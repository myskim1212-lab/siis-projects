package sec.siis.jdbc.logging;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import sec.siis.jdbc.config.ActionType;
import sec.siis.jdbc.config.JdbcConfig;
import sec.siis.jdbc.execution.JdbcExecutionOperationResult;

/**
 * 데이터베이스 작업 관련 로그 메시지를 중앙에서 관리하는 클래스
 * 개선된 로그 포맷 적용
 */
public class LogMessageManager {

    private LogMessageManager() {
        // 인스턴스화 방지
    }

    // ========== 로그 포맷 스타일 설정 ==========
    
    /**
     * 로그 포맷 스타일
     * COMPACT: 간결한 형식 (기본)
     * DETAILED: 상세한 형식
     * STRUCTURED: 구조화된 형식 (JSON-like)
     */
    public enum LogStyle {
        COMPACT,    // [API:xxx | OP:yyy] 메시지
        DETAILED,   // apiName=xxx, operationName=yyy : 메시지
        DEFAULT
    }
    
    private static LogStyle currentStyle = LogStyle.COMPACT;
    
    public static void setLogStyle(LogStyle style) {
        currentStyle = style;
    }
    
    // ========== 메시지 포맷팅 헬퍼 ==========
    
    private static String formatPrefix(String msgID, String apiName, String txUnitId, String operationName) {
        switch (currentStyle) {
            case COMPACT:
                return String.format("%s [%s,%s,%s]", 
                	msgID,
                    truncate(apiName, 50),
                    txUnitId,
                    truncate(operationName, 50));
            case DETAILED:
            default:
            	return String.format("%s [%s][%s][%s]", msgID, apiName, txUnitId, operationName);
        }
    }
    
    private static String formatPrefix(String msgID , String apiName) {
        switch (currentStyle) {
            case COMPACT:
                return String.format("%s [%s]", 
                	msgID,
                    truncate(apiName, 50));
            case DETAILED:
            default:
            	return String.format("%s [%s]", msgID, apiName);
        }
    }    
    
    private static String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength - 2) + ".." : str;
    }
    
    // ========== DATA DUMP - INFO 레벨 로그 메서드 ==========

    public static void infoDumpRequest(Logger log, String msgID, String apiName, String json) {
        if (!log.isInfoEnabled()) return;
        log.info("{} >> REQUEST DATA DUMP", formatPrefix(msgID, apiName));
        log.info("{} {}", formatPrefix(msgID, apiName), json);
    }

    public static void infoDumpSqlBindings(Logger log, String msgID, String apiName, String txUnitId,
            String operationName, int rowIndex, Map<String, Object> bindings) {
        if (!log.isInfoEnabled() || bindings == null || bindings.isEmpty()) return;
        String prefix = formatPrefix(msgID, apiName, txUnitId, operationName);
        log.info("{} >> SQL BINDING DUMP | row[{}]", prefix, rowIndex);
        int maxKeyLen = bindings.keySet().stream().mapToInt(String::length).max().orElse(0);
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            log.info("{}   {}  = {}", prefix,
                String.format("%-" + maxKeyLen + "s", entry.getKey()),
                entry.getValue());
        }
    }

    public static void infoDumpResponse(Logger log, String msgID, String apiName, String json) {
        if (!log.isInfoEnabled()) return;
        log.info("{} << RESPONSE DATA DUMP", formatPrefix(msgID, apiName));
        log.info("{} {}", formatPrefix(msgID, apiName), json);
    }

    // ========== JdbcExecutor - INFO 레벨 로그 메서드 ==========
  
    public static void infoStartIf(Logger log, String msgID, String apiName, String ops) {
        if (log.isInfoEnabled()) {
            log.info("{} >> BEGIN | Operations : {}",formatPrefix(msgID, apiName),ops);
        }
    }
    public static void infoEndIf(Logger log, String msgID, String apiName) {
        if (log.isInfoEnabled()) {
            log.info("{} << END ",formatPrefix(msgID, apiName));
        }
    }    
    public static void infoTxUnitOperations(Logger log, String msgID, String apiName, String txUnitId, String operations) {
        if (log.isInfoEnabled()) {
            log.info("{} TxUnit Started",formatPrefix(msgID, apiName, txUnitId, operations));
        }
    }
    
    public static void infoTxUnitCommitted(Logger log, String msgID, String apiName, String txUnitId, String operations) {
        if (log.isInfoEnabled()) {
            log.info("{} TxUnit Committed",formatPrefix(msgID, apiName, txUnitId, operations));
        }
    }
    
    public static void infoTxUnitRolledBack(Logger log, String msgID, String apiName, String txUnitId, String operations) {
        if (log.isInfoEnabled()) {
            log.info("{} TxUnit Rolled Back", formatPrefix(msgID, apiName, txUnitId, operations));
        }
    }

    // ========== JdbcExecutor - DEBUG 레벨 로그 메서드 ==========
    
    public static void debugExecutingMasterDetail(Logger log, String msgID, String apiName, String txUnitId, String operationName) {
        if (log.isDebugEnabled()) {
            log.debug("{} [ Master-Detail ] execution started", formatPrefix(msgID, apiName, txUnitId, operationName));
        }
    }

    public static void debugRowCommitStarted(Logger log, String msgID, String apiName, String txUnitId, String operationName, int totalRows) {
        if (log.isDebugEnabled()) {
            log.debug("{} Row-by-row commit: {} rows", 
                formatPrefix(msgID, apiName, txUnitId, operationName), totalRows);
        }
    }
    
    public static void debugRowCommitProgress(Logger log, String msgID, String apiName, String txUnitId, String operationName, 
                                             int completed, int total) {
        if (log.isDebugEnabled()) {
            int percentage = (completed * 100) / total;
            log.debug("{} Progress: {}/{} ({}%)", 
                formatPrefix(msgID, apiName, txUnitId, operationName), completed, total, percentage);
        }
    }
    
    public static void debugRowCommitSuccess(Logger log, String msgID, String apiName, String txUnitId, String operationName, int rowIndex) {
        if (log.isDebugEnabled()) {
            log.debug("{} Row[{}] committed", formatPrefix(msgID, apiName, txUnitId, operationName), rowIndex);
        }
    }
    
    public static void debugRowCommitFailed(Logger log, String msgID, String apiName, String txUnitId, String operationName, 
                                           int rowIndex, String reason) {
        if (log.isDebugEnabled()) {
            log.debug("{} Row[{}] failed: {}", 
                formatPrefix(msgID, apiName, txUnitId, operationName), rowIndex, reason);
        }
    }
    
    public static void debugOperationSkipped(Logger log, String msgID, String apiName, String txUnitId, String operationName, String reason) {
        if (log.isDebugEnabled()) {
            log.debug("{} Skipped: {}", formatPrefix(msgID, apiName, txUnitId, operationName), reason);
        }
    }
    
    public static void debugTransactionStarted(Logger log, String msgID, String apiName, String txUnitId, String operationName) {
        if (log.isDebugEnabled()) {
            log.debug("{} Transaction started", formatPrefix(msgID, apiName, txUnitId, operationName));
        }
    }
    
    public static void debugTransactionCommitted(Logger log, String msgID, String apiName, String txUnitId, String operationName) {
        if (log.isDebugEnabled()) {
            log.debug("{} Transaction committed", formatPrefix(msgID, apiName, txUnitId, operationName));
        }
    }
    
    public static void debugTransactionRolledBack(Logger log, String msgID, String apiName, String txUnitId, String operationName, String reason) {
        if (log.isDebugEnabled()) {
            log.debug("{} Transaction rolled back: {}",
                formatPrefix(msgID, apiName, txUnitId, operationName), reason);
        }
    }

    // ========== AbstractDatabaseStrategy - DEBUG 레벨 로그 메서드 ==========
    
    public static void debugExecSql(Logger log, String msgID, String apiName, String txUnitId, String operationName , String sql) {
        if (log.isDebugEnabled()) {
            log.debug("{} SQL = {}", formatPrefix(msgID, apiName, txUnitId, operationName),sql);
        }
    }
    
    public static void debugNoRowsToProcess(Logger log, String msgID, String apiName, String txUnitId, String operationName) {
        if (log.isDebugEnabled()) {
            log.debug("{} No rows to process", formatPrefix(msgID, apiName, txUnitId, operationName));
        }
    }
    
    public static void debugUpdatedRows(Logger log, String msgID, String apiName, String txUnitId, String operationName, int rowCount) {
        if (log.isDebugEnabled()) {
            log.debug("{} Updated {} rows", formatPrefix(msgID, apiName, txUnitId, operationName), rowCount);
        }
    }
    
    public static void debugExecutedOperation(Logger log, String msgID, String apiName, String txUnitId, String operationName, int affectedRows) {
        if (log.isDebugEnabled()) {
            log.debug("{} Executed, affected {} rows", 
                formatPrefix(msgID, apiName, txUnitId, operationName), affectedRows);
        }
    }
    
    public static void debugOperationProcessed(Logger log, String msgID, String apiName, String txUnitId, String operationName, int totalRows) {
        if (log.isDebugEnabled()) {
            log.debug("{} Operation processed {} rows", 
                formatPrefix(msgID, apiName, txUnitId, operationName), totalRows);
        }
    }
    
    public static void debugSelectReturned(Logger log, String msgID, String apiName, String txUnitId, String operationName, int rowCount) {
        if (log.isDebugEnabled()) {
            log.debug("{} Select returned {} rows", 
                formatPrefix(msgID, apiName, txUnitId, operationName), rowCount);
        }
    }
    
    public static void debugProcedureNoParams(Logger log, String msgID, String apiName, String txUnitId, String operationName) {
        if (log.isDebugEnabled()) {
            log.debug("{} Procedure executed (no params)", 
                formatPrefix(msgID, apiName, txUnitId, operationName));
        }
    }
    
    public static void debugProcedureExecuted(Logger log, String msgID, String apiName, String txUnitId, String operationName, int executeCount) {
        if (log.isDebugEnabled()) {
            log.debug("{} Procedure executed {} times", 
                formatPrefix(msgID, apiName, txUnitId, operationName), executeCount);
        }
    }
    
    public static void debugBindingParameter(Logger log, String msgID, String apiName, String txUnitId, String operationName, 
                                            String field, Object value) {
        if (log.isDebugEnabled()) {
            log.debug("{} Param: {}={}", 
                formatPrefix(msgID, apiName, txUnitId, operationName), field, value);
        }
    }
    
    public static void debugSqlStatement(Logger log, String msgID, String apiName, String txUnitId, String operationName, String sql) {
        if (log.isDebugEnabled()) {
            // 여러 줄 공백을 한 줄로 정리하여 로그 가독성 확보 (길이 제한 없이 전체 출력)
            String compactSql = sql.replaceAll("\\s+", " ").trim();
            log.debug("{} SQL: {}", formatPrefix(msgID, apiName, txUnitId, operationName), compactSql);
        }
    }

    // ========== WARN 레벨 로그 메서드 ==========
    
    public static void warnSqlTypeNull(Logger log) {
        if (log.isWarnEnabled()) {
            log.warn("SQL type is null, defaulting to VARCHAR");
        }
    }
    
    public static void warnUnknownSqlType(Logger log, String msgID, String sqlType) {
        if (log.isWarnEnabled()) {
            log.warn("Unknown SQL type '{}', defaulting to VARCHAR", sqlType);
        }
    }

    // ========== ERROR 레벨 로그 메서드 ==========
    
    public static void errorExecuteJdbcFailed(Logger log, String msgID, Exception e) {
        log.error("JDBC execution failed", e);
    }
    
    public static void errorOperationExecutionFailed(Logger log, String msgID, String apiName, String txUnitId, String operationName, Exception e) {
        log.error("{} Operation failed", formatPrefix(msgID, apiName, txUnitId, operationName), e);
    }
    
    public static void errorOperationExecutionCriticalFailed(Logger log, String msgID, String apiName, String txUnitId, String operationName,Exception e) {
        log.error("{} Operation failed ( Critical DB error detected. Stopping all subsequent rows ) , {}", formatPrefix(msgID, apiName, txUnitId, operationName), e);
    }    
    
    public static void errorFailedReadClob(Logger log, Exception e) {
        log.error("Failed to read CLOB", e);
    }
    
    public static void errorFailedConvertBlob(Logger log, Exception e) {
        log.error("Failed to convert BLOB to Base64", e);
    }
    
    // ========== 유틸리티 메서드 ==========
    
    public static <T> String formatOperations(List<T> operations, 
                                             java.util.function.Function<T, String> formatter) {
        return operations.stream()
                        .map(formatter)
                        .reduce((a, b) -> a + " -> " + b)
                        .orElse("(none)");
    }
    
    public static void debugOperationStart(Logger log, String msgID, String apiName, String txUnitId, String operationName, 
                                          ActionType actionType) {
        if (log.isDebugEnabled()) {
            log.debug("{} Starting {} operation", 
                formatPrefix(msgID, apiName, txUnitId, operationName), actionType);
        }
    }
    
    public static void debugOperationComplete(Logger log, String msgID, String apiName, String txUnitId, String operationName, 
                                             long elapsedMs) {
        if (log.isDebugEnabled()) {
            log.debug("{} Completed in {}ms", 
                formatPrefix(msgID, apiName, txUnitId, operationName), elapsedMs);
        }
    }
    
    public static void debugBatchProgress(Logger log, String msgID, String apiName, String txUnitId, String operationName, 
                                         int processed, int total) {
        if (log.isDebugEnabled()) {
            int percentage = (processed * 100) / total;
            log.debug("{} Batch progress: {}/{} ({}%)", 
                formatPrefix(msgID, apiName, txUnitId, operationName), processed, total, percentage);
        }
    }
    
    public static void infoExecutionSummary(
            Logger log,
            String msgID,
            String apiName,
            boolean success,
            long elapsedMs,
            List<JdbcExecutionOperationResult> operations) {

        if (!log.isInfoEnabled() || operations == null || operations.isEmpty()) return;

        // 1. 통계 및 각 필드의 최대 길이 산정
        boolean isAllCommitted = operations.stream().allMatch(JdbcExecutionOperationResult::isCommitted);

        int maxOrderLen = 1;
        int maxNameLen = 10; // 최소 헤더 길이
        int maxActionLen = 3;
        int maxSuccessLen = 1;
        int maxFailLen = 1;
        int maxAffectedLen = 1;
        int maxElapsedLen = 1;

        for (JdbcExecutionOperationResult r : operations) {
            maxOrderLen = Math.max(maxOrderLen, String.valueOf(r.getOrder()).length());
            maxNameLen = Math.max(maxNameLen, r.getOperationName() == null ? 0 : r.getOperationName().length());
            maxActionLen = Math.max(maxActionLen, simpleOpType(r.getActionType(), r.isBulkMode(), r.isStreamingMode()).length());
            maxSuccessLen = Math.max(maxSuccessLen, String.valueOf(r.getSuccessCount()).length());
            maxFailLen = Math.max(maxFailLen, String.valueOf(r.getFailCount()).length());
            maxAffectedLen = Math.max(maxAffectedLen, String.valueOf(r.getAffectedRows()).length());
            maxElapsedLen = Math.max(maxElapsedLen, String.valueOf(r.getElapsedMs()).length());
        }

        // 2. 동적 포맷 문자열 생성: %-[길이]s 형태로 실제 데이터 길이에 맞춤
        String formatStr = String.format(
            "  [%%%dd] %%-%ds | %%-%ds | %%-3s | Committed: %%-1s | Success: %%%dd | Fail: %%%dd | Affected: %%%dd | Elapsed(ms): %%%dd",
            maxOrderLen, maxNameLen, maxActionLen, maxSuccessLen, maxFailLen, maxAffectedLen, maxElapsedLen
        );

        // 헤더 출력
        log.info("{} [Summary] | Result: {} | Elapsed: {}ms",
        		formatPrefix(msgID,apiName),
                 isAllCommitted ? "COMMITTED" : (success ? "SUCCESS" : "ROLLBACK"), 
                 elapsedMs);
        log.info("{} Operations:", formatPrefix(msgID,apiName) );

        // 바디 출력
        for (JdbcExecutionOperationResult r : operations) {
            String statusMark = r.isSuccess() ? "OK " : "ERR";
            String commitMark = r.isCommitted() ? "Y" : "N";
            String actionType = simpleOpType(r.getActionType(), r.isBulkMode(), r.isStreamingMode());

            String formattedLine = String.format(formatStr,
                r.getOrder(),
                r.getOperationName(),
                actionType,
                statusMark,
                commitMark,
                r.getSuccessCount(),
                r.getFailCount(),
                r.getAffectedRows(),
                r.getElapsedMs()
            );

            log.info("{} {}", formatPrefix(msgID,apiName), formattedLine);

            if (r.isFail()) {
                String errDetail = r.getErrorDetail() != null ? r.getErrorDetail().trim() : "Unknown Error";
                log.info("{} [{}]          >> [ERROR] {}", msgID, apiName, errDetail);
            }
        }
    }
    
    private static String simpleOpType(ActionType actionType) {
        if (actionType == null) return "UNK";
        switch (actionType) {
            case DELETE:    return "DEL";
            case INSERT:    return "INS";
            case UPSERT:    return "INU";
            case UPDATE:    return "UPD";
            case SELECT:    return "SEL";
            case PROCEDURE: return "PRO";
            default:        return "UNK";
        }
    }

    /**
     * Bulk/Streaming 모드 여부를 반영한 opType 표시.
     * Bulk DML → "BLK", Streaming SELECT → "STR", 일반 → 기존 3자리 코드
     */
    private static String simpleOpType(ActionType actionType, boolean bulkMode, boolean streamingMode) {
        if (bulkMode)      return "BLK";
        if (streamingMode) return "STR";
        return simpleOpType(actionType);
    }
    
    // ========== Bulk DML / Streaming SELECT 전용 로그 메서드 ==========

    /**
     * DEBUG: Bulk DML 시작 — chunkCommitSize 표시
     */
    public static void debugBulkStarted(Logger log, String msgID, String apiName,
            String txUnitId, String operationName, int chunkCommitSize) {
        if (!log.isDebugEnabled()) return;
        log.debug("{} [Bulk] DML started (chunkCommitSize={})",
                formatPrefix(msgID, apiName, txUnitId, operationName), chunkCommitSize);
    }

    /**
     * DEBUG: Bulk DML 청크 커밋 — 몇 번째 청크가 몇 행 커밋됐는지
     */
    public static void debugBulkChunkCommitted(Logger log, String msgID, String apiName,
            String txUnitId, String operationName, int chunkNum, int chunkSize, int totalProcessed) {
        if (!log.isDebugEnabled()) return;
        log.debug("{} [Bulk] Chunk #{} committed ({} rows, total: {})",
                formatPrefix(msgID, apiName, txUnitId, operationName), chunkNum, chunkSize, totalProcessed);
    }

    /**
     * DEBUG: Bulk DML 전체 완료
     */
    public static void debugBulkCompleted(Logger log, String msgID, String apiName,
            String txUnitId, String operationName, int totalProcessed) {
        if (!log.isDebugEnabled()) return;
        log.debug("{} [Bulk] Completed: {} rows processed",
                formatPrefix(msgID, apiName, txUnitId, operationName), totalProcessed);
    }

    /**
     * DEBUG: Streaming SELECT 시작 — fetchSize 표시
     */
    public static void debugStreamingSelectStarted(Logger log, String msgID, String apiName,
            String txUnitId, String operationName, int fetchSize) {
        if (!log.isDebugEnabled()) return;
        log.debug("{} [Streaming] SELECT started (fetchSize={})",
                formatPrefix(msgID, apiName, txUnitId, operationName), fetchSize);
    }

    /**
     * DEBUG: Streaming SELECT 완료 — 행 수 + fetchSize 표시
     */
    public static void debugStreamingSelectReturned(Logger log, String msgID, String apiName,
            String txUnitId, String operationName, int rowCount, int fetchSize) {
        if (!log.isDebugEnabled()) return;
        log.debug("{} [Streaming] Select returned {} rows (fetchSize={})",
                formatPrefix(msgID, apiName, txUnitId, operationName), rowCount, fetchSize);
    }

    // ========== DATASOURCE / STRATEGY 로그 메서드 ==========

    public static void debugDataSourceResolved(Logger log, String msgID, String apiName, String jndiName) {
        if (log.isDebugEnabled()) {
            log.debug("{} DataSource resolved: {}", formatPrefix(msgID, apiName), jndiName);
        }
    }

    public static void debugDbTypeCacheHit(Logger log, String msgID, String jndiName, String dbType) {
        if (log.isDebugEnabled()) {
            log.debug("{} DB type cache hit: {}", formatPrefix(msgID, jndiName), dbType);
        }
    }

    public static void debugDbTypeCacheMiss(Logger log, String msgID, String jndiName) {
        if (log.isDebugEnabled()) {
            log.debug("{} DB type cache miss, resolving...", formatPrefix(msgID, jndiName));
        }
    }

    // ========== CONNECTION LIFECYCLE 로그 메서드 ==========

    /**
     * DEBUG: Logged before attempting to acquire the initial (type-resolution) connection.
     */
    public static void debugConnectionAcquiring(Logger log, String jndiName) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Acquiring initial connection for DB type resolution...", jndiName);
        }
    }

    /**
     * INFO: Logged once per JNDI name after the DB type is successfully resolved.
     * Includes product name, version, JDBC URL (password masked), user name, and elapsed ms.
     */
    public static void infoConnectionResolved(Logger log, String jndiName,
            String product, String version, String url, String user,
            Object dbType, long elapsedMs) {
        if (log.isInfoEnabled()) {
            log.info("[{}] Connected | product={} {} | url={} | user={} | dbType={} | elapsed={}ms",
                    jndiName, product, version, url, user, dbType, elapsedMs);
        }
    }

    /**
     * WARN: Logged when the initial connection takes longer than the defined threshold.
     */
    public static void warnSlowConnection(Logger log, String jndiName,
            long elapsedMs, long thresholdMs) {
        log.warn("[{}] Slow initial connection detected: {}ms (threshold: {}ms)",
                jndiName, elapsedMs, thresholdMs);
    }

    /**
     * DEBUG: Logged every time a connection is acquired from the pool during request processing.
     */
    public static void debugConnectionAcquired(Logger log, String msgID,
            String jndiName, long elapsedMs) {
        if (log.isDebugEnabled()) {
            log.debug("{} Connection acquired from pool: elapsed={}ms", formatPrefix(msgID, jndiName), elapsedMs);
        }
    }

    /**
     * WARN: Logged when pool connection acquisition takes longer than the defined threshold.
     */
    public static void warnSlowPoolConnection(Logger log, String msgID,
            String jndiName, long elapsedMs, long thresholdMs) {
        log.warn("{} Slow connection pool response: {}ms (threshold: {}ms)",
                formatPrefix(msgID, jndiName), elapsedMs, thresholdMs);
    }

    /**
     * WARN: Logged when a getConnection() attempt fails and a retry will follow.
     */
    public static void warnConnectionRetry(Logger log, String msgID, String jndiName,
            int attempt, int maxAttempts, long retryIntervalMs, String errorMessage) {
        log.warn("{} Connection attempt {}/{} failed, retrying in {}ms: {}",
                formatPrefix(msgID, jndiName), attempt, maxAttempts, retryIntervalMs, errorMessage);
    }

    /**
     * ERROR: Logged when all connection retry attempts have been exhausted.
     */
    public static void errorConnectionRetryExhausted(Logger log, String msgID, String jndiName,
            int totalAttempts, String errorMessage) {
        log.error("{} Connection failed after {} attempt(s), giving up: {}",
                formatPrefix(msgID, jndiName), totalAttempts, errorMessage);
    }

    /**
     * WARN: Logged when Connection.setNetworkTimeout() is not supported by the driver/connection,
     * so the connection is returned without the configured read-timeout protection.
     */
    public static void warnNetworkTimeoutUnsupported(Logger log, String msgID, String jndiName,
            long readTimeoutMs, String errorMessage) {
        log.warn("{} setNetworkTimeout({}ms) not supported, proceeding without it: {}",
                formatPrefix(msgID, jndiName), readTimeoutMs, errorMessage);
    }

    /**
     * DEBUG: Logged before JNDI DataSource lookup.
     */
    public static void debugJndiLookup(Logger log, String jndiName) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] JNDI lookup initiated...", jndiName);
        }
    }

    /**
     * INFO: Logged once when DataSource is successfully obtained from JNDI.
     */
    public static void infoJndiResolved(Logger log, String jndiName) {
        if (log.isInfoEnabled()) {
            log.info("[{}] DataSource obtained from JNDI", jndiName);
        }
    }

    private static String extractShortError(String detail) {
        if (detail == null) return "";

        // ORA-xxxxx 에러 코드 추출
        int ora = detail.indexOf("ORA-");
        if (ora >= 0) {
            int end = detail.indexOf("\n", ora);
            return end > ora
                    ? detail.substring(ora, end)
                    : detail.substring(ora);
        }

        // 폴백: 앞 80자
        return detail.length() > 80
                ? detail.substring(0, 77) + "..."
                : detail;
    }    
}