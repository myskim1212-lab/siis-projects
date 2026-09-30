package sec.siis.jdbc.execution;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sec.siis.jdbc.config.ActionType;
import sec.siis.jdbc.config.EffectiveOperationConfig;
import sec.siis.jdbc.config.FieldConfig;
import sec.siis.jdbc.config.JdbcConfig;
import sec.siis.jdbc.config.JdbcConfigDefaults;
import sec.siis.jdbc.config.OperationConfig;
import sec.siis.jdbc.exception.ApiException;
import sec.siis.jdbc.exception.DbException;
import sec.siis.jdbc.exception.ExceptionMessageUtils;
import sec.siis.jdbc.exception.SystemException;
import sec.siis.jdbc.logging.LogMessageManager;
import sec.siis.jdbc.strategy.DbStrategy;
import sec.siis.jdbc.strategy.StrategyFactory;
import sec.siis.jdbc.util.CommonJsonUtil;

public class JdbcExecutor {

	private static final Logger log = LoggerFactory.getLogger(JdbcExecutor.class);
	private static final int DEFAULT_PROCEDURE_AFFECTED_ROWS = 1;

	/** Pool connection acquisitions slower than this threshold (ms) trigger a WARN log. */
	private static final long CONN_SLOW_THRESHOLD_MS = 1_000;

	/** Connection.setNetworkTimeout()가 요구하는 Executor — 커넥션마다 새로 만들지 않고 공유한다. */
	private static final Executor NETWORK_TIMEOUT_EXECUTOR = Executors.newCachedThreadPool();

	private final DataSource dataSource;
	private final DbStrategy strategy;
	private final String jndiName;
	private String msgID;
	private JdbcConfig jcfg;

	public JdbcExecutor(DataSource dataSource, String jndiName, String msgID) throws Exception {
		this.dataSource = dataSource;
		this.jndiName   = jndiName;
		this.strategy   = StrategyFactory.create(dataSource, jndiName, msgID);
		this.msgID      = msgID;
	}

	/**
	 * Acquires a JDBC connection from the pool, retrying on failure per jcfg.connection_retry_count.
	 * 재시도는 getConnection()이 예외를 던질 때만 의미가 있다 — datasource의
	 * oracle.net.CONNECT_TIMEOUT/READ_TIMEOUT이 설정돼 있지 않으면 getConnection() 자체가
	 * 무제한 대기할 수 있고, 그 상태에서는 재시도 루프에 진입할 기회조차 없다.
	 * 성공하면 jcfg.connection_read_timeout_ms를 커넥션에 적용해, 이후 실행할 SQL의
	 * 응답 대기 시간을 제한한다(획득 자체의 지연은 이 설정으로 막지 못함).
	 */
	private Connection acquireConnection() throws SQLException {
		int maxRetries = (jcfg != null && jcfg.getConnection_retry_count() != null)
				? jcfg.getConnection_retry_count() : JdbcConfigDefaults.DEFAULT_CONNECTION_RETRY_COUNT;
		long retryIntervalMs = (jcfg != null && jcfg.getConnection_retry_interval_ms() != null)
				? jcfg.getConnection_retry_interval_ms() : JdbcConfigDefaults.DEFAULT_CONNECTION_RETRY_INTERVAL_MS;
		long readTimeoutMs = (jcfg != null && jcfg.getConnection_read_timeout_ms() != null)
				? jcfg.getConnection_read_timeout_ms() : JdbcConfigDefaults.DEFAULT_CONNECTION_READ_TIMEOUT_MS;

		int totalAttempts = maxRetries + 1;
		SQLException lastFailure = null;

		for (int attempt = 1; attempt <= totalAttempts; attempt++) {
			long start = System.currentTimeMillis();
			try {
				Connection conn = dataSource.getConnection();
				long elapsed = System.currentTimeMillis() - start;
				LogMessageManager.debugConnectionAcquired(log, msgID, jndiName, elapsed);
				if (elapsed > CONN_SLOW_THRESHOLD_MS) {
					LogMessageManager.warnSlowPoolConnection(log, msgID, jndiName, elapsed, CONN_SLOW_THRESHOLD_MS);
				}
				if (readTimeoutMs > 0) {
					// setNetworkTimeout() 실패(드라이버 미지원 등)는 커넥션 자체는 정상이므로
					// 재시도 대상으로 삼지 않고, 타임아웃 없이 그대로 반환한다.
					try {
						conn.setNetworkTimeout(NETWORK_TIMEOUT_EXECUTOR, (int) readTimeoutMs);
					} catch (SQLException nte) {
						LogMessageManager.warnNetworkTimeoutUnsupported(log, msgID, jndiName, readTimeoutMs, nte.getMessage());
					}
				}
				return conn;
			} catch (SQLException e) {
				lastFailure = e;
				if (attempt < totalAttempts) {
					LogMessageManager.warnConnectionRetry(log, msgID, jndiName, attempt, totalAttempts,
							retryIntervalMs, e.getMessage());
					if (retryIntervalMs > 0) {
						try {
							Thread.sleep(retryIntervalMs);
						} catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							throw e;
						}
					}
				}
			}
		}
		LogMessageManager.errorConnectionRetryExhausted(log, msgID, jndiName, totalAttempts, lastFailure.getMessage());
		throw lastFailure;
	}

	/*
	 * ========================================================= 
	 * Entry 
	 * =========================================================
	 */
	public JdbcExecutionResult executeJdbc(JdbcConfig jcfg, JsonNode parsedInput, JdbcExecutionResult result)
			throws Exception {

		this.jcfg = jcfg;

		try {
			ObjectMapper mapper = ObjectMapperHolder.INSTANCE.mapper;
			Map<String, JsonNode> pathCache = new HashMap<>();

			List<CollectOperation> txUnit = new ArrayList<>();
			boolean hasError = false;
			int[] txCounter = {0}; // TxUnit 번호 카운터 (로컬 변수, 스레드 안전)

			
			int order = 0;
			for (OperationConfig op : jcfg.getOperations()) {
				order++;
				EffectiveOperationConfig eop = new EffectiveOperationConfig(jcfg, op);

				// ── Bulk DML 경로 ──────────────────────────────────────────────────────
				// bulk=true이면 rows를 List로 구체화하지 않고 Iterator로 처리한다.
				// 기존 txUnit 흐름에서 독립 실행되며, 청크 커밋은 strategy 내부에서 수행한다.
				if (eop.isBulk() && eop.getAction_type() != null && eop.getAction_type().isDml()
						&& eop.getAction_type() != ActionType.SELECT) {

					boolean bulkExistNode;
					Iterator<Map<String, Object>> bulkIter;
					if (eop.hasOperationDataRecordPath()) {
						bulkExistNode = CommonJsonUtil.existRecordNode(parsedInput, eop.getData_record_path(),
								eop.getData_record(), pathCache);
						bulkIter = CommonJsonUtil.streamRecord(parsedInput, eop.getData_record_path(),
								eop.getData_record(), pathCache);
					} else {
						bulkExistNode = CommonJsonUtil.existRecordNode(parsedInput, JdbcConfigDefaults.DEFAULT_DATA_RECORD_PATH,
								eop.getOperation_name(), eop.getData_record(), pathCache);
						bulkIter = CommonJsonUtil.streamRecord(parsedInput, JdbcConfigDefaults.DEFAULT_DATA_RECORD_PATH,
								eop.getOperation_name(), eop.getData_record(), pathCache);
					}
					if (!bulkExistNode && eop.isExecuteIfNoData()) bulkExistNode = true;

					// 이전 txUnit 먼저 처리
					if (!txUnit.isEmpty()) {
						hasError = executeTxUnit(txUnit, jcfg, result, txCounter);
						if (hasError && jcfg.getStop_on_operation_error()) break;
					}

					hasError = executeBulkOperation(order, eop, bulkIter, bulkExistNode, jcfg, result, txCounter);
					if (hasError && jcfg.getStop_on_operation_error()) break;
					continue;
				}
				// ─────────────────────────────────────────────────────────────────────

				// Operation 단위 data_record_path가 정의된 경우:
				//   data_record_path + data_record 직접 파싱 (opName 경로 제외)
				// 미정의 경우:
				//   전역 고정 경로(/operations) + opName + data_record 표준 파싱
				boolean existNode;
				List<Map<String, Object>> rows;
				if (eop.hasOperationDataRecordPath()) {
					existNode = CommonJsonUtil.existRecordNode(parsedInput, eop.getData_record_path(),
							eop.getData_record(), pathCache);
					rows = CommonJsonUtil.extractRecord(parsedInput, eop.getData_record_path(),
							eop.getData_record(), pathCache);
				} else {
					existNode = CommonJsonUtil.existRecordNode(parsedInput, JdbcConfigDefaults.DEFAULT_DATA_RECORD_PATH,
							eop.getOperation_name(), eop.getData_record(), pathCache);
					rows = CommonJsonUtil.extractRecord(parsedInput, JdbcConfigDefaults.DEFAULT_DATA_RECORD_PATH,
							eop.getOperation_name(), eop.getData_record(), pathCache);
				}

				// execute_if_no_data=true 이면 existNode=false여도 강제 실행
				if (!existNode && eop.isExecuteIfNoData()) {
					existNode = true;
				}

				CollectOperation collectOp = new CollectOperation(order, eop, rows, existNode);

				// DML이면서 ROW 스코프인 경우만 전환 트리거 발생
				// 데이터 건수가 0건일 경우 ROW 처리는 의미 없음.
				boolean isRowDml = eop.getCommit_scope() == CommitScope.ROW
			               && eop.getAction_type().isDml()
			               && rows != null && !rows.isEmpty();
			   
			    if (isRowDml) {
			        // 1. 이전 ALL scope 유닛 처리
			        if (!txUnit.isEmpty()) {
			            hasError = executeTxUnit(txUnit, jcfg, result, txCounter);
			            if (hasError && jcfg.getStop_on_operation_error()) break;
			        }

			        // 2. 현재 ROW 단위 개별 처리
			        hasError = (op.hasDetailOperations())
			                   ? executeMasterDetailRowCommit(collectOp, jcfg, result, txCounter)
			                   : executeRowCommit(collectOp, jcfg, result, txCounter);

			        if (hasError && jcfg.getStop_on_operation_error()) break;
			        continue;
			    }

			    // SELECT, PROCEDURE, ALL-DML은 여기에 누적
			    txUnit.add(collectOp);

			    // 명시적 커밋 지점
			    if (eop.getCommit_Action() == CommitAction.COMMIT) {
			        hasError = executeTxUnit(txUnit, jcfg, result, txCounter);
			        if (hasError && jcfg.getStop_on_operation_error()) break;
			    }
			}
			// 잔여 작업 처리 (hasError 체크가 핵심)
			if (!txUnit.isEmpty()) {
			    executeTxUnit(txUnit, jcfg, result, txCounter);
			}

		} catch (Exception e) {
			LogMessageManager.errorExecuteJdbcFailed(log, msgID, e);
			throw new SystemException(e);
		} finally {
			result.finish();
		}
		return result;
	}

	private boolean executeTxUnit(List<CollectOperation> txUnit, JdbcConfig jcfg, JdbcExecutionResult result, int[] txCounter) {
		if (txUnit.isEmpty()) {
			return false;
		}
		String txUnitId = "txUnit" + (++txCounter[0]);
		boolean hasError = executeInTx(txUnit, jcfg, result, txUnitId);
		txUnit.clear();
		return hasError;
	}

	private boolean executeInTx(List<CollectOperation> txUnit, JdbcConfig jcfg, JdbcExecutionResult result, String txUnitId) {
		if (txUnit.isEmpty()) {
			return false;
		}

		// 1. TxUnit 시작 로깅 및 시간 측정 시작
		String ops = LogMessageManager.formatOperations(txUnit, op -> op.getEop().getOperation_name());
		LogMessageManager.infoTxUnitOperations(log, msgID, jcfg.getApi_name(), txUnitId, ops);
		
		long txStartTime = System.currentTimeMillis();

		List<JdbcExecutionOperationResult> opResults = new ArrayList<>();
		Connection conn = null;
		boolean rollback = false;

		try {
			conn = acquireConnection();
			conn.setAutoCommit(false);

			for (CollectOperation op : txUnit) {

				if (op.getEop().isMasterDetail()) {
					List<JdbcExecutionOperationResult> md = executeMasterDetailInTx(op, jcfg, conn, txUnitId);
					opResults.addAll(md);
					if (md.stream().anyMatch(JdbcExecutionOperationResult::isFail))
						rollback = true;
				} else {
					JdbcExecutionOperationResult r = executeGeneralInTx(op, jcfg, conn, txUnitId);
					opResults.add(r);
					if (r.isFail())
						rollback = true;
				}

				if (rollback)
					break;
			}

			if (rollback) {
				conn.rollback();
			} else {
				conn.commit();
			}

		} catch (Exception e) {
			rollback = true;
			if (conn != null) {
				try {
					conn.rollback();
				} catch (SQLException ex) {
					LogMessageManager.errorExecuteJdbcFailed(log, msgID, ex);
				}
			}
			throw new SystemException(e);
		} finally {
			if (conn != null) {
				try {
					if (!conn.isClosed()) {
		                conn.setAutoCommit(true); // 커넥션 반납 전 상태 복구
		                conn.close();
		            }
				} catch (SQLException e) {
					LogMessageManager.errorExecuteJdbcFailed(log, msgID, e);
				}
			}

			// 4. TxUnit 최종 결과 및 전체 소요시간 로깅
			if (!rollback) {
				LogMessageManager.infoTxUnitCommitted(log, msgID, jcfg.getApi_name(), txUnitId, ops);
			} else {
				LogMessageManager.infoTxUnitRolledBack(log, msgID, jcfg.getApi_name(), txUnitId, ops);
			}

			// 커넥션/커밋 실패 시에도 실행된 operation 결과를 result에 반영
			// (SystemException이 throw되더라도 진단 정보를 응답에 포함)
			boolean isCommit = !rollback;
			result.addAll(opResults, isCommit);
		}

		return rollback;
	}

	private JdbcExecutionOperationResult executeGeneralInTx(CollectOperation collectOp, JdbcConfig jcfg,
			Connection conn, String txUnitId) {

		JdbcExecutionOperationResult r = JdbcExecutionOperationResult.init(collectOp.getOrder(), collectOp.getEop());
		List<Map<String, Object>> rows = collectOp.getRows();
		r.start();
		int rowSize = (rows == null ? 0 : rows.size());
		r.setRequestRecordCount(rowSize);

		if (!collectOp.isExistNode()) {
			String skipReason = String.format("Record node not present: path=%s, record=%s",
					collectOp.getEop().getData_record_path(), collectOp.getEop().getData_record());
			r.skip(skipReason);
			LogMessageManager.debugOperationSkipped(log, msgID, collectOp.getEop().getApi_name(), txUnitId,
					collectOp.getEop().getOperation_name(), skipReason);
			r.end();
			return r;
		}

		try {
			OperationOutput out = runOperation(jcfg, collectOp.getEop(), rows, conn, txUnitId);

			ActionType actionType = collectOp.getEop().getAction_type();
			if (actionType == ActionType.SELECT) {
				if (out.isStreamed()) {
					// SELECT Streaming 경로: JSON 문자열 → JsonNode로 변환하여 보관
					try {
						JsonNode node = ObjectMapperHolder.INSTANCE.mapper.readTree(out.getStreamedJson());
						int rowCount = node.isArray() ? node.size() : 0;
						r.setResponseRecordCount(rowCount);
						r.setStreamedResultNode(node);
						r.setStreamingMode(true);   // Summary 로그에서 STR 표시
					} catch (Exception parseEx) {
						throw new SystemException(parseEx);
					}
				} else if (out.getData() != null) {
					r.setResponseRecordCount(out.getData().size());
					r.setResultData(out.getData());
				}
			} else if (actionType == ActionType.PROCEDURE && out.getData() != null) {
				// PROCEDURE OUT 파라메터 결과 저장
				r.setResponseRecordCount(out.getData().size());
				r.setResultData(out.getData());
			}
			r.setSuccessCount(out.getSuccessCount());
			r.setAffectedRows(out.getAffectedCount());
			r.setSuccess(true);
		} catch (ApiException e) {
			r.setFailCount(rowSize);
			r.setAffectedRows(0);
			r.fail(e);
			LogMessageManager.errorOperationExecutionFailed(log, msgID, collectOp.getEop().getApi_name(), txUnitId,
					collectOp.getEop().getOperation_name(), e);
		} catch (Exception e) {
			r.setFailCount(rowSize);
			r.setAffectedRows(0);
			r.fail(new SystemException(e));
			LogMessageManager.errorOperationExecutionFailed(log, msgID, collectOp.getEop().getApi_name(), txUnitId,
					collectOp.getEop().getOperation_name(), e);
		} finally {
			r.end();
			LogMessageManager.debugOperationComplete(log, msgID, collectOp.getEop().getApi_name(), txUnitId,
					collectOp.getEop().getOperation_name(), r.getElapsedMs());
		}
		return r;
	}

	/*
	 * ========================================================= 
	 * Master Detail ALL
	 * =========================================================
	 */
	private List<JdbcExecutionOperationResult> executeMasterDetailInTx(CollectOperation collectOp, JdbcConfig jcfg,
			Connection conn, String txUnitId) {

		List<JdbcExecutionOperationResult> results = new ArrayList<>();

		OperationConfig masterOp = collectOp.getOriginalConfig();
		EffectiveOperationConfig masterEop = collectOp.getEop();
		List<Map<String, Object>> masterRows = collectOp.getRows();

		JdbcExecutionOperationResult master = JdbcExecutionOperationResult.init(collectOp.getOrder(), masterEop);
		master.start();
		int masterRowSize = (masterRows == null ? 0 : masterRows.size());
		master.setRequestRecordCount(masterRowSize);

		LogMessageManager.debugExecutingMasterDetail(log, msgID, masterEop.getApi_name(), txUnitId, masterEop.getOperation_name());

		if (!collectOp.isExistNode()) {
			String skipReason = String.format("Record node not present: path=%s, record=%s",
					masterEop.getData_record_path(), masterEop.getData_record());
			master.skip(skipReason);
			LogMessageManager.debugOperationSkipped(log, msgID, masterEop.getApi_name(), txUnitId, masterEop.getOperation_name(),
					skipReason);
			master.end();
			results.add(master);
			return results;
		}

		Map<String, JdbcExecutionOperationResult> detailMap = new LinkedHashMap<>();
		for (OperationConfig d : masterOp.getDetail_operations()) {
			EffectiveOperationConfig e = EffectiveOperationConfig.forDetail(jcfg, masterOp, d);
			JdbcExecutionOperationResult r = JdbcExecutionOperationResult.init(collectOp.getOrder(), e);
			detailMap.put(e.getOperation_name(), r);
		}

		// detail 별 row 수 체크
		for (Map<String, Object> masterRow : masterRows) {
			for (OperationConfig d : masterOp.getDetail_operations()) {

				List<Map<String, Object>> detailRows = extractDetailRecords(masterRow, d.getData_record());
				if (detailRows == null || detailRows.isEmpty()) {
					continue;
				}

				EffectiveOperationConfig detailEop = EffectiveOperationConfig.forDetail(jcfg, masterOp, d);
				JdbcExecutionOperationResult dr = detailMap.get(detailEop.getOperation_name());

				dr.incrementRequestRecordCount(detailRows.size());
			}
		}
		try {

			int masterSuccessCount = 0;
			int masterAffectedCount = 0;

			for (Map<String, Object> masterRow : masterRows) {

				OperationOutput masterOut = runOperation(jcfg, masterEop, List.of(masterRow), conn, txUnitId);

				for (OperationConfig d : masterOp.getDetail_operations()) {
					List<Map<String, Object>> detailRows = extractDetailRecords(masterRow, d.getData_record());
					if (detailRows == null)
						continue;

					EffectiveOperationConfig e = EffectiveOperationConfig.forDetail(jcfg, masterOp, d);
					JdbcExecutionOperationResult dr = detailMap.get(e.getOperation_name());

					if (dr.getStartTime() == 0) {
						dr.start();
					}
					try {
						inheritMasterFields(detailRows, masterRow, masterOp, d.getInherit_fields());
						OperationOutput out = runOperation(jcfg, e, detailRows, conn, txUnitId);
						dr.incrementSuccessCount(out.getSuccessCount());
						dr.incrementAffectedRows(out.getAffectedCount());
					} catch (Exception de) {
						dr.setSuccessCount(0);
						dr.setFailCount(detailRows.size());
						dr.fail(de instanceof ApiException ? (ApiException) de : new SystemException(de));
						throw (de instanceof RuntimeException) ? (RuntimeException) de : new SystemException(de);
					}
				}
				masterSuccessCount += masterOut.getSuccessCount();
				masterAffectedCount += masterOut.getAffectedCount();
			}
			master.setSuccess(true);
			master.setSuccessCount(masterSuccessCount);
			master.setAffectedRows(masterAffectedCount);

		} catch (Exception e) {
			master.setSuccessCount(0);
			master.setFailCount(masterRowSize);
			master.fail(e instanceof ApiException ? (ApiException) e : new SystemException(e));
			LogMessageManager.errorOperationExecutionFailed(log, msgID, masterEop.getApi_name(), txUnitId, masterEop.getOperation_name(),
					e);
		}

		master.end();
		results.add(master);

		for (JdbcExecutionOperationResult dr : detailMap.values()) {
			if (master.isSuccess()) { // master가 success 이면 detail도 모두 성공으로 간주
				dr.setSuccess(true);
				dr.setSuccessCount(dr.getRequestRecordCount());
			} else {
				dr.setSuccess(false);
				dr.setFailCount(dr.getRequestRecordCount());
			}
			dr.end();
			results.add(dr);
		}

		return results;
	}

	/*
	 * ========================================================= 
	 * ROW commit
	 * =========================================================
	 */
	private boolean executeMasterDetailRowCommit(CollectOperation collectOp, JdbcConfig jcfg,
			JdbcExecutionResult result, int[] txCounter) {

		String txUnitId = "txUnit" + (++txCounter[0]);
		
		OperationConfig masterOp = collectOp.getOriginalConfig();
		EffectiveOperationConfig masterEop = collectOp.getEop();
		List<Map<String, Object>> masterRows = collectOp.getRows();

		JdbcExecutionOperationResult master = JdbcExecutionOperationResult.init(collectOp.getOrder(), masterEop);
		master.start();

		int masterRowSize = (masterRows == null ? 0 : masterRows.size());
		master.setRequestRecordCount(masterRowSize);

		if (!collectOp.isExistNode()) {
			String skipReason = String.format("Record node not present: path=%s, record=%s",
					masterEop.getData_record_path(), masterEop.getData_record());
			master.skip(skipReason);
			LogMessageManager.debugOperationSkipped(log, msgID, masterEop.getApi_name(), txUnitId, masterEop.getOperation_name(),
					skipReason);
			master.end();
			result.add(master);
			return false;		
		}

		LogMessageManager.debugRowCommitStarted(log, msgID, masterEop.getApi_name(), txUnitId, masterEop.getOperation_name(),
				masterRowSize);

		// Operation별 JdbcExecutionOperationResult 생성 및 초기화
		Map<String, JdbcExecutionOperationResult> detailMap = new LinkedHashMap<>();
		for (OperationConfig d : masterOp.getDetail_operations()) {
			EffectiveOperationConfig e = EffectiveOperationConfig.forDetail(jcfg, masterOp, d);
			JdbcExecutionOperationResult r = JdbcExecutionOperationResult.init(collectOp.getOrder(), e);
			detailMap.put(e.getOperation_name(), r);
		}

		// detail 별 row 수 체크
		for (Map<String, Object> masterRow : masterRows) {
			for (OperationConfig d : masterOp.getDetail_operations()) {

				List<Map<String, Object>> detailRows = extractDetailRecords(masterRow, d.getData_record());
				if (detailRows == null || detailRows.isEmpty()) {
					continue;
				}

				EffectiveOperationConfig detailEop = EffectiveOperationConfig.forDetail(jcfg, masterOp, d);
				JdbcExecutionOperationResult dr = detailMap.get(detailEop.getOperation_name());

				dr.incrementRequestRecordCount(detailRows.size());
			}
		}

		for (int i = 0; i < masterRowSize; i++) {
			Map<String, Object> masterRow = masterRows.get(i);
			Connection conn = null;

			try {
				conn = acquireConnection();
				conn.setAutoCommit(false);

				// Master 실행
				OperationOutput masterOut = runOperation(jcfg, collectOp.getEop(), List.of(masterRow), conn, txUnitId);


				int j=0;
				// Detail 실행 (executeMasterDetailInTx 스타일)
				for (OperationConfig d : masterOp.getDetail_operations()) {
					j++;
					List<Map<String, Object>> detailRows = extractDetailRecords(masterRow, d.getData_record());
					if (detailRows == null || detailRows.isEmpty()) {
						continue;
					}

					EffectiveOperationConfig detailEop = EffectiveOperationConfig.forDetail(jcfg, masterOp, d);
					JdbcExecutionOperationResult dr = detailMap.get(detailEop.getOperation_name());

					// 첫 실행 시 start
					if (dr.getStartTime() == 0) {
						dr.start();
					}

					try {
						inheritMasterFields(detailRows, masterRow, masterOp, d.getInherit_fields());
						OperationOutput out = runOperation(jcfg, detailEop, detailRows, conn, txUnitId);
						dr.incrementSuccessCount(out.getSuccessCount());
						dr.incrementAffectedRows(out.getAffectedCount());

					} catch (Exception de) {
						// dr.setSuccessCount(0);
						dr.incrementFail(detailRows.size());
						// dr.setFailCount(detailRows.size());
						dr.fail(de instanceof ApiException ? (ApiException) de : new SystemException(de));
						LogMessageManager.debugRowCommitFailed(log, msgID, detailEop.getApi_name(), txUnitId, detailEop.getOperation_name(), j,
								dr.getErrorDetail());
						throw (de instanceof RuntimeException) ? (RuntimeException) de : new SystemException(de);
					}
				}

				conn.commit();

				master.incrementSuccessCount(masterOut.getSuccessCount());
				master.incrementAffectedRows(masterOut.getAffectedCount());

			} catch (Exception e) {
				if (conn != null) {
					try {
						conn.rollback();
					} catch (SQLException ex) {
						LogMessageManager.errorExecuteJdbcFailed(log, msgID, ex);
					}
				}

				master.incrementFail(1);
				master.fail(e);
				master.addRowError(RowError.fromException(i + 1, masterRow,
						e instanceof ApiException ? (ApiException) e : new SystemException(e)));

				LogMessageManager.debugRowCommitFailed(log, msgID, masterEop.getApi_name(), txUnitId, masterEop.getOperation_name(), i,
						master.getErrorDetail());

				// 치명적 에러 여부 판단
				boolean isFatal = ExceptionMessageUtils.isFatalError(e);

				if (isFatal) {
					LogMessageManager.errorOperationExecutionFailed(log, msgID, masterEop.getApi_name(), txUnitId,
							masterEop.getOperation_name(), e);
				}
				// 중단 조건 체크: 치명적 에러이거나, 설정에 의해 중단해야 하거나
				if (isFatal || jcfg.getStop_on_row_error()) {
					break;
				}

			} finally {
				if (conn != null) {
					try {
						if (!conn.isClosed()) {
			                conn.setAutoCommit(true); // 커넥션 반납 전 상태 복구
			                conn.close();
			            }
					} catch (SQLException e) {
						LogMessageManager.errorExecuteJdbcFailed(log, msgID, e);
					}
				}
			}

		}

		// Master 결과 마무리
		master.setSuccess(master.getFailCount() == 0 && !master.isSkipped());
		master.setCommitted(master.isSuccess());
		master.end();

		// Detail 결과 마무리 및 추가 (executeMasterDetailInTx 스타일)
		result.add(master, master.isSuccess());

		for (JdbcExecutionOperationResult r : detailMap.values()) {
			// master 처리 시 에러가 발생하여 detail이 한번도 처리되지 않았다면 skip 되었다고 간주
			if (r.getStartTime() == 0) {
				r.setSkipped(true);
				r.setCommitted(false);
				r.setSuccess(true);
			}else {
				r.setSuccess(r.getFailCount() == 0);
				r.end();				
			}
			result.add(r, r.isSuccess());
		}

		return master.isFail();
	}

	private boolean executeRowCommit(CollectOperation collectOp, JdbcConfig jcfg, JdbcExecutionResult result, int[] txCounter) {

		String txUnitId = "txUnit" + (++txCounter[0]);
		
		JdbcExecutionOperationResult r = JdbcExecutionOperationResult.init(collectOp.getOrder(), collectOp.getEop());

		List<Map<String, Object>> rows = collectOp.getRows();
		r.start();
		int rowSize = (rows == null ? 0 : rows.size());
		r.setRequestRecordCount(rowSize);

		LogMessageManager.debugRowCommitStarted(log, msgID, collectOp.getEop().getApi_name(), txUnitId,
				collectOp.getEop().getOperation_name(), rowSize);

		for (int i = 0; i < rowSize; i++) {
			Map<String, Object> row = rows.get(i);
			Connection conn = null;

			try {
				conn = acquireConnection();
				conn.setAutoCommit(false);

				OperationOutput out = runOperation(jcfg, collectOp.getEop(), List.of(row), conn, txUnitId);

				conn.commit();

				r.incrementSuccessCount(out.getSuccessCount());
				r.incrementAffectedRows(out.getAffectedCount());

			} catch (Exception e) {
				if (conn != null) {
					try {
						conn.rollback();
					} catch (SQLException ex) {
						LogMessageManager.errorExecuteJdbcFailed(log, msgID, ex);
					}
				}

				r.incrementFail(1);
				// 첫번째 에러일 때
				if (r.getFailCount() == 1) {
					r.fail(e);
				}

				r.addRowError(RowError.fromException(i+1, row,
						e instanceof ApiException ? (ApiException) e : new SystemException(e)));

				LogMessageManager.debugRowCommitFailed(log, msgID, collectOp.getEop().getApi_name(), txUnitId,
						collectOp.getEop().getOperation_name(), i, e.getMessage());

				// 치명적 에러 여부 판단
				boolean isFatal = ExceptionMessageUtils.isFatalError(e);

				if (isFatal) {
					LogMessageManager.errorOperationExecutionFailed(log, msgID, collectOp.getEop().getApi_name(), txUnitId,
							collectOp.getEop().getOperation_name(), e);
				}
				// 중단 조건 체크: 치명적 에러이거나, 설정에 의해 중단해야 하거나
				if (isFatal || jcfg.getStop_on_row_error()) {
					break;
				}
			} finally {
				if (conn != null) {
					try {
						if (!conn.isClosed()) {
			                conn.setAutoCommit(true); // 커넥션 반납 전 상태 복구
			                conn.close();
			            }
					} catch (SQLException e) {
						LogMessageManager.errorExecuteJdbcFailed(log, msgID, e);
					}
				}
			}

		}

		r.setSuccess(r.getFailCount() == 0);
		r.setCommitted(r.isSuccess());
		r.end();

		result.add(r, r.isSuccess());
		return r.isFail();
	}

	/*
	 * =========================================================
	 * Bulk DML (Iterator 기반 청크 커밋)
	 * =========================================================
	 */
	private boolean executeBulkOperation(int order, EffectiveOperationConfig eop,
			Iterator<Map<String, Object>> rowIter, boolean existNode,
			JdbcConfig jcfg, JdbcExecutionResult result, int[] txCounter) {

		String txUnitId = "txUnit" + (++txCounter[0]);
		JdbcExecutionOperationResult r = JdbcExecutionOperationResult.init(order, eop);
		r.start();

		if (!existNode) {
			String skipReason = String.format("Record node not present: path=%s, record=%s",
					eop.getData_record_path(), eop.getData_record());
			r.skip(skipReason);
			LogMessageManager.debugOperationSkipped(log, msgID, eop.getApi_name(), txUnitId,
					eop.getOperation_name(), skipReason);
			r.end();
			result.add(r, false);
			return false;
		}

		r.setBulkMode(true);   // Summary 로그에서 BLK 표시

		LogMessageManager.infoTxUnitOperations(log, msgID, eop.getApi_name(), txUnitId,
				"[bulk] " + eop.getOperation_name());

		Connection conn = null;
		try {
			conn = acquireConnection();
			conn.setAutoCommit(false);
			int processed = strategy.executeBulkBatch(conn, txUnitId, eop,
					jcfg.getBatch_size(), eop.getChunkCommitSize(), rowIter);
			r.setRequestRecordCount(processed);
			r.setSuccessCount(processed);
			r.setAffectedRows(processed);
			r.setSuccess(true);
			r.setCommitted(true);
			LogMessageManager.infoTxUnitCommitted(log, msgID, eop.getApi_name(), txUnitId,
					"[bulk] " + eop.getOperation_name());
		} catch (Exception e) {
			if (conn != null) {
				try { conn.rollback(); } catch (SQLException ex) {
					LogMessageManager.errorExecuteJdbcFailed(log, msgID, ex);
				}
			}
			r.fail(e instanceof ApiException ? (ApiException) e : new SystemException(e));
			LogMessageManager.errorOperationExecutionFailed(log, msgID, eop.getApi_name(), txUnitId,
					eop.getOperation_name(), e);
		} finally {
			if (conn != null) {
				try {
					if (!conn.isClosed()) {
						conn.setAutoCommit(true);
						conn.close();
					}
				} catch (SQLException e) {
					LogMessageManager.errorExecuteJdbcFailed(log, msgID, e);
				}
			}
			r.end();
		}

		result.add(r, r.isSuccess());
		return r.isFail();
	}

	public OperationOutput runOperation(JdbcConfig jcfg, EffectiveOperationConfig eop, List<Map<String, Object>> rows,
			Connection conn, String txUnitId) throws Exception {

		// ── 입력 row 수 제한 체크 (DML 전용) ──────────────────────────────────────
		// SELECT 는 pstmt.setMaxRows() 로 처리하므로 여기서 제외.
		// PROCEDURE 는 row 단위 반복 처리가 없으므로 제외.
		if (eop.getAction_type().isDml() && eop.getAction_type() != ActionType.SELECT) {
			int limit = eop.getEffectiveMaxRowLimit();
			if (limit > 0 && rows != null && rows.size() > limit) {
				throw new sec.siis.jdbc.exception.BadRequestException(String.format(
					"[%s] 입력 행 수(%d)가 max_row_limit(%d)을 초과했습니다.",
					eop.getOperation_name(), rows.size(), limit));
			}
		}
		// ─────────────────────────────────────────────────────────────────────────

		int affectedCount = 0;

		try {
			switch (eop.getAction_type()) {
			case INSERT:
				affectedCount = strategy.executeBatch(conn, txUnitId, eop, jcfg.getBatch_size(), rows);
				return new OperationOutput((rows == null ? 0 : rows.size()), affectedCount, null);
			case UPSERT:
				affectedCount = strategy.executeBatch(conn, txUnitId, eop, jcfg.getBatch_size(), rows);
				return new OperationOutput((rows == null ? 0 : rows.size()), affectedCount, null);
			case UPDATE:
				if (rows == null || rows.isEmpty()) {
					affectedCount = strategy.executeSql(conn, txUnitId, eop);
					return new OperationOutput(0, affectedCount, null);
				} else {
					affectedCount = strategy.executeBatch(conn, txUnitId, eop, jcfg.getBatch_size(), rows);
					return new OperationOutput(rows.size(), affectedCount, null);
				}
			case DELETE:
				if (rows == null || rows.isEmpty()) {
					affectedCount = strategy.executeSql(conn, txUnitId, eop);
					return new OperationOutput(0, affectedCount, null);
				} else {
					affectedCount = strategy.executeBatch(conn, txUnitId, eop, jcfg.getBatch_size(), rows);
					return new OperationOutput(rows.size(), affectedCount, null);
				}
			case SELECT:
				if (eop.getFetchSize() > 0) {
					String streamedJson = strategy.executeSelectStreaming(conn, txUnitId, eop, rows);
					return new OperationOutput(0, streamedJson);
				}
				List<Map<String, Object>> resultData = strategy.executeSelect(conn, txUnitId, eop, rows);
				return new OperationOutput(resultData.size(), 0, resultData);
			case PROCEDURE:
				if (rows == null || rows.isEmpty()) {
					List<Map<String, Object>> procOut = strategy.executeProcedure(conn, txUnitId, eop, null, jcfg.getBatch_size());
					return new OperationOutput(DEFAULT_PROCEDURE_AFFECTED_ROWS, 0, procOut);
				} else {
					List<Map<String, Object>> procOut = strategy.executeProcedure(conn, txUnitId, eop, rows, jcfg.getBatch_size());
					return new OperationOutput(rows.size(), 0, procOut);
				}
			default:
				throw new IllegalArgumentException("Unsupported type");
			}
		} catch (SQLException e) {
			throw new DbException(e);
		}
	}

	private List<Map<String, Object>> extractDetailRecords(Map<String, Object> masterRow, String key) {
		Object v = masterRow.get(key);
		if (v instanceof List)
			return (List<Map<String, Object>>) v;
		if (v instanceof Map)
			return List.of((Map<String, Object>) v);
		return null;
	}

	private void inheritMasterFields(List<Map<String, Object>> detailRows, Map<String, Object> masterRow,
			OperationConfig masterOp, List<FieldConfig> inheritFields) {

		if (inheritFields == null)
			return;
		for (Map<String, Object> d : detailRows) {
			for (FieldConfig f : inheritFields) {
				d.put(f.getData_field(), masterRow.get(f.getData_field()));
			}
		}
	}
}