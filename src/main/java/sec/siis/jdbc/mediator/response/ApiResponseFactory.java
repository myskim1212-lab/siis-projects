package sec.siis.jdbc.mediator.response;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sec.siis.jdbc.config.ActionType;
import sec.siis.jdbc.config.ConfigValidationException;
import sec.siis.jdbc.config.JdbcConfig;
import sec.siis.jdbc.config.OperationConfig;
import sec.siis.jdbc.exception.ExceptionMessageUtils;
import sec.siis.jdbc.execution.JdbcExecutionOperationResult;
import sec.siis.jdbc.execution.JdbcExecutionResult;
import sec.siis.jdbc.util.DateTimeFormatUtil;

public class ApiResponseFactory {

    private static final String CODE_SUCCESS = "S000";
    private static final String CODE_PARTIAL_FAIL = "E206"; // 부분 실패
    private static final String CODE_SYSTEM_ERROR = "E500"; // 시스템/전체 에러
    private static final String CODE_CONFIG_VALIDATION_ERROR = "E400"; // 설정/검증 에러

    /** 처리 건수를 응답 HTTP 헤더로 노출할 때 사용하는 헤더 이름 */
    public static final String RECORD_COUNT_HEADER_NAME = "CUSTOM-LOG-recordcount";

    /**
     * 대표 오퍼레이션(최상위 오퍼레이션 중 DELETE/PROCEDURE를 스킵하고 남은 첫 번째 오퍼레이션,
     * master/detail이면 master)의 처리 건수를 HTTP 응답 헤더용 문자열로 산정한다.
     *
     * 규칙:
     *  - DELETE/PROCEDURE      : 대표 후보에서 제외(스킵)하고 다음 오퍼레이션을 확인
     *  - INSERT/UPDATE/UPSERT : 수신 건수(requestRecordCount)
     *  - SELECT                : 응답 건수(responseRecordCount)
     *  - 최상위 오퍼레이션이 단 1개이고 그게 DELETE/PROCEDURE인 경우(다른 대표 후보가 없음):
     *      전달된 데이터가 있으면(requestRecordCount > 0) 그 수신 건수를 반영,
     *      데이터가 없으면 빈 문자열("")
     *  - 최상위 오퍼레이션이 여러 개인데 전부 DELETE/PROCEDURE뿐이라 대표를 찾을 수 없는 경우: 빈 문자열("")
     *  - 대표 오퍼레이션 실패/스킵 또는 실행 결과를 찾을 수 없는 경우: "0"
     */
    public static String resolveRecordCountHeader(JdbcConfig jcfg, JdbcExecutionResult execResult) {
        if (jcfg == null || jcfg.getOperations() == null || jcfg.getOperations().isEmpty()
                || execResult == null || execResult.getOperations().isEmpty()) {
            return "0";
        }

        List<OperationConfig> topLevelOps = jcfg.getOperations();

        OperationConfig representative = topLevelOps.stream()
                .filter(op -> op.getAction_type() != ActionType.DELETE
                        && op.getAction_type() != ActionType.PROCEDURE)
                .findFirst()
                .orElse(null);

        if (representative == null) {
            if (topLevelOps.size() == 1) {
                // 최상위 오퍼레이션이 단 1개뿐이고 그게 DELETE/PROCEDURE인 경우:
                // 데이터가 전달됐는지에 따라 그 오퍼레이션 자체를 대표로 삼는다.
                representative = topLevelOps.get(0);
            } else {
                // 여러 최상위 오퍼레이션이 전부 DELETE/PROCEDURE뿐인 경우
                return "";
            }
        }

        String representativeName = representative.getOperation_name();

        JdbcExecutionOperationResult rep = execResult.getOperations().stream()
                .filter(r -> representativeName.equals(r.getOperationName()))
                .findFirst()
                .orElse(null);

        if (rep == null || rep.isSkipped() || !rep.isSuccess()) {
            return "0";
        }

        if (rep.getActionType() == ActionType.SELECT) {
            return String.valueOf(rep.getResponseRecordCount());
        }

        if ((rep.getActionType() == ActionType.DELETE || rep.getActionType() == ActionType.PROCEDURE)
                && rep.getRequestRecordCount() <= 0) {
            // 단일 DELETE/PROCEDURE 오퍼레이션인데 전달된 데이터가 없는 경우
            return "";
        }

        // INSERT / UPSERT / UPDATE / (데이터가 존재하는 단일 DELETE·PROCEDURE)
        return String.valueOf(rep.getRequestRecordCount());
    }

    /**
     * [CASE 1] 시스템 예외 발생 시 (DB 연결 전이나 실행 중 치명적 예외)
     */
    public static Map<String, Object> error(JdbcConfig jcfg, Exception e, String msgID, long startTime, long endTime, JdbcExecutionResult jdbcExecResult) {
        Map<String, Object> result = createHeader(jcfg, msgID, startTime, endTime);
        result.put("success", false);

        String collectedMessages = (e != null) ? ExceptionMessageUtils.collectMessages(e) : "Unknown error occurred";
        String code = (e instanceof ConfigValidationException) ? CODE_CONFIG_VALIDATION_ERROR : CODE_SYSTEM_ERROR;

        boolean isAnyCommitted = false;

        if (jdbcExecResult != null && !jdbcExecResult.getOperations().isEmpty()) {

            // skipped 제외
            List<JdbcExecutionOperationResult> executedOps = jdbcExecResult.getOperations().stream()
                    .filter(op -> !op.isSkipped())
                    .collect(Collectors.toList());

            isAnyCommitted = executedOps.stream().anyMatch(JdbcExecutionOperationResult::isCommitted);

            if (isAnyCommitted && !(e instanceof ConfigValidationException)) {
                code = CODE_PARTIAL_FAIL;
            }

            String opErrors = buildErrorDetails(executedOps);
            result.put("errorSummary", String.format("ERROR: %s \n[Operation Errors]:\n%s", collectedMessages, opErrors));
            result.put("operations", transformOperations(jdbcExecResult, isAnyCommitted));
        } else {
            result.put("errorSummary", collectedMessages);
            result.put("operations", Collections.emptyMap());
        }

        result.put("code", code);
        result.put("message", isAnyCommitted ? "Error occurred but some changes were committed." : collectedMessages);

        return result;
    }
    /**
     * [CASE 2] 실행 완료 후 결과를 분석하여 응답 생성 (전체성공/부분실패/전체실패)
     */
    public static Map<String, Object> createResponse(JdbcConfig jcfg, String msgID, long startTime, long endTime, JdbcExecutionResult execResult) {
        Map<String, Object> result = createHeader(jcfg, msgID, startTime, endTime);
        List<JdbcExecutionOperationResult> ops = execResult.getOperations();

        // skipped 제외한 실제 실행된 operation만 카운트
        List<JdbcExecutionOperationResult> executedOps = ops.stream()
                .filter(op -> !op.isSkipped())
                .collect(Collectors.toList());

        long totalOps   = executedOps.size();
        long successOps = executedOps.stream().filter(JdbcExecutionOperationResult::isSuccess).count();

        boolean isAnyCommitted = executedOps.stream().anyMatch(JdbcExecutionOperationResult::isCommitted);

        if (successOps == totalOps) {
            result.put("success", true);
            result.put("code", CODE_SUCCESS);
            result.put("message", "Request processed successfully.");
        } else {
            result.put("success", false);

            if (isAnyCommitted) {
                result.put("code", CODE_PARTIAL_FAIL);
                result.put("message", String.format("Partially committed. (Success: %d, Total: %d)", successOps, totalOps));
            } else {
                result.put("code", CODE_SYSTEM_ERROR);
                result.put("message", successOps > 0
                    ? "Transaction rolled back due to error."
                    : "All operations failed to execute.");
            }

            result.put("errorSummary", buildErrorDetails(executedOps));
        }

        result.put("operations", transformOperations(execResult, isAnyCommitted));
        return result;
    }
    
    
    /**
     * 에러 메시지를 가독성 있게 조합
     */
    private static String buildErrorDetails(List<JdbcExecutionOperationResult> ops) {
        return ops.stream()
                .filter(op -> !op.isSkipped())   // 방어적으로 한번 더
                .filter(op -> !op.isSuccess())
                .map(op -> {
                    String opName    = op.getOperationName();
                    String errDetail = (op.getErrorDetail() != null) ? op.getErrorDetail().trim() : "";

                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(opName).append("] ");
                    if (!errDetail.isEmpty()) {
                        sb.append(errDetail.replace("[", "(").replace("]", ")"));
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }    
    /**
     * Select 오퍼레이션의 결과만 추출
     */
    private static Map<String, Object> extractSelectData(JdbcConfig jcfg, JdbcExecutionResult result) {
        return result.getOperations().stream()
                .filter(r -> ActionType.SELECT==r.getActionType() && r.getResultData() != null)
                .collect(Collectors.toMap(
                    r -> jcfg.findOperation(r.getOperationName())
                             .map(OperationConfig::getOperation_name)
                             .orElse(r.getOperationName()),
                    JdbcExecutionOperationResult::getResultData,
                    (oldVal, newVal) -> newVal,
                    LinkedHashMap::new
                ));
    }

    private static Map<String, Object> createHeader(JdbcConfig jcfg,String msgID, long startTime, long endTime) {
        Map<String, Object> header = new LinkedHashMap<>();
        
        // jcfg가 null이면 기본값 적용
        header.put("apiName", jcfg != null ? jcfg.getApi_name() : "UNKNOWN_API");
        header.put("targetSystemName", jcfg != null ? jcfg.getTarget_system_name() : "");
        header.put("targetSystemKey", jcfg != null ? jcfg.getTarget_system_key() : "");
        header.put("global_transaction_id", msgID != null ? msgID : "");
        header.put("startTime", DateTimeFormatUtil.formatTimestamp(startTime));
        header.put("endTime", DateTimeFormatUtil.formatTimestampOrNull(endTime));
        header.put("elapsedMs", (startTime > 0 && endTime >= startTime) ? (endTime - startTime) : 0);
        return header;
    }

    /**
     * 트랜잭션 상태에 따른 오퍼레이션 변환
     */
    private static Map<String, Object> transformOperations(JdbcExecutionResult execution, boolean isAnyCommitted) {
        if (execution == null || execution.getOperations() == null) {
            return Collections.emptyMap();
        }

        return execution.getOperations().stream()
                .filter(op -> !op.isSkipped())   // skipped 응답에서 제외
                .collect(Collectors.toMap(
                    op -> {
                        if (!isAnyCommitted && op.isSuccess()) {
                            op.setErrorMessage("Rolled back due to transaction failure");
                        }
                        return op.getOperationName();
                    },
                    op -> op,
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
                ));
    }    
        
}