package sec.siis.jdbc.mediator.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import sec.siis.jdbc.config.ActionType;
import sec.siis.jdbc.config.JdbcConfig;
import sec.siis.jdbc.config.OperationConfig;
import sec.siis.jdbc.execution.JdbcExecutionOperationResult;
import sec.siis.jdbc.execution.JdbcExecutionResult;

/**
 * CUSTOM-LOG-recordcount 헤더 값 산정 로직(ApiResponseFactory.resolveRecordCountHeader) 검증.
 *
 * 규칙:
 *  - 최상위 오퍼레이션 중 DELETE/PROCEDURE는 대표 후보에서 스킵하고, 남은 첫 번째 오퍼레이션을
 *    대표로 사용한다 (master/detail이면 master, 멀티마스터면 스킵 후 남은 것 중 첫 번째).
 *  - INSERT/UPDATE/UPSERT : 수신 건수(requestRecordCount)
 *  - SELECT                : 응답 건수(responseRecordCount)
 *  - 모든 최상위 오퍼레이션이 DELETE/PROCEDURE뿐이면 : 빈 문자열("")
 *  - 대표 오퍼레이션 실패/스킵/실행결과 없음 : "0"
 */
class ApiResponseFactoryRecordCountHeaderTest {

    /**
     * JdbcExecutionOperationResult 목록으로부터 이름/타입이 일치하는 JdbcConfig(최상위 오퍼레이션 목록)를
     * 함께 생성한다. config와 execution 결과의 이름/타입이 어긋나는 테스트 버그를 방지하기 위해
     * 하나의 소스(results)에서 둘 다 파생시킨다.
     */
    private static JdbcConfig jcfgFrom(JdbcExecutionOperationResult... results) {
        JdbcConfig jcfg = new JdbcConfig();
        List<OperationConfig> ops = Arrays.stream(results).map(r -> {
            OperationConfig op = new OperationConfig();
            op.setOperation_name(r.getOperationName());
            op.setAction_type(r.getActionType());
            return op;
        }).collect(Collectors.toList());
        jcfg.setOperations(ops);
        return jcfg;
    }

    private static JdbcExecutionOperationResult opResult(String name, ActionType type,
            boolean success, boolean skipped, int requestRecordCount, int responseRecordCount) {
        JdbcExecutionOperationResult r = new JdbcExecutionOperationResult();
        r.setOperationName(name);
        r.setActionType(type);
        r.setSuccess(success);
        r.setSkipped(skipped);
        r.setRequestRecordCount(requestRecordCount);
        r.setResponseRecordCount(responseRecordCount);
        return r;
    }

    private static JdbcExecutionResult execResult(JdbcExecutionOperationResult... results) {
        JdbcExecutionResult result = new JdbcExecutionResult();
        for (JdbcExecutionOperationResult r : results) {
            result.add(r);
        }
        return result;
    }

    @Test
    void insertRepresentative_returnsRequestRecordCount() {
        JdbcExecutionOperationResult insert = opResult("insert_tb_user_v2", ActionType.INSERT, true, false, 5, 0);
        JdbcConfig jcfg = jcfgFrom(insert);
        JdbcExecutionResult exec = execResult(insert);

        assertEquals("5", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void updateRepresentative_returnsRequestRecordCount() {
        JdbcExecutionOperationResult update = opResult("update_tb_user_v2", ActionType.UPDATE, true, false, 3, 0);
        JdbcConfig jcfg = jcfgFrom(update);
        JdbcExecutionResult exec = execResult(update);

        assertEquals("3", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void upsertRepresentative_returnsRequestRecordCount() {
        JdbcExecutionOperationResult upsert = opResult("upsert_tb_user_v2", ActionType.UPSERT, true, false, 2, 0);
        JdbcConfig jcfg = jcfgFrom(upsert);
        JdbcExecutionResult exec = execResult(upsert);

        assertEquals("2", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void selectRepresentative_returnsResponseRecordCount() {
        JdbcExecutionOperationResult select = opResult("select_tb_user_v2", ActionType.SELECT, true, false, 0, 7);
        JdbcConfig jcfg = jcfgFrom(select);
        JdbcExecutionResult exec = execResult(select);

        assertEquals("7", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void onlyDeleteOperationWithoutData_returnsEmptyString() {
        // 최상위 오퍼레이션이 DELETE 하나뿐이고 전달된 데이터가 없는 경우(예: DELETE ALL)
        JdbcExecutionOperationResult delete = opResult("delete_tb_user_v2", ActionType.DELETE, true, false, 0, 0);
        JdbcConfig jcfg = jcfgFrom(delete);
        JdbcExecutionResult exec = execResult(delete);

        assertEquals("", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void onlyDeleteOperationWithData_returnsRequestRecordCount() {
        // 최상위 오퍼레이션이 DELETE 하나뿐이고 DELETE를 위한 데이터(WHERE IN 등)가 전달된 경우
        JdbcExecutionOperationResult delete = opResult("delete_tb_user_v2_param", ActionType.DELETE, true, false, 4, 0);
        JdbcConfig jcfg = jcfgFrom(delete);
        JdbcExecutionResult exec = execResult(delete);

        assertEquals("4", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void onlyProcedureOperationWithoutData_returnsEmptyString() {
        // 최상위 오퍼레이션이 PROCEDURE 하나뿐이고 파라메터로 전달된 데이터가 없는 경우
        JdbcExecutionOperationResult proc = opResult("proc_tb_user_v2", ActionType.PROCEDURE, true, false, 0, 1);
        JdbcConfig jcfg = jcfgFrom(proc);
        JdbcExecutionResult exec = execResult(proc);

        assertEquals("", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void onlyProcedureOperationWithData_returnsRequestRecordCount() {
        // 최상위 오퍼레이션이 PROCEDURE 하나뿐이고 파라메터로 전달된 데이터가 있는 경우
        JdbcExecutionOperationResult proc = opResult("proc_tb_user_v2_param", ActionType.PROCEDURE, true, false, 2, 1);
        JdbcConfig jcfg = jcfgFrom(proc);
        JdbcExecutionResult exec = execResult(proc);

        assertEquals("2", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void masterDetail_usesMasterCountOnly_ignoresLargerDetailCount() {
        // 최상위 오퍼레이션은 master(insert_tb_user_v2) 하나뿐이고,
        // detail(insert_tb_user_v2_d1)은 별도 이름으로 실행 결과에만 존재한다(최상위 목록에는 없음).
        JdbcExecutionOperationResult master = opResult("insert_tb_user_v2", ActionType.INSERT, true, false, 1, 0);
        JdbcExecutionOperationResult detail = opResult("insert_tb_user_v2_d1", ActionType.INSERT, true, false, 4, 0);
        JdbcConfig jcfg = jcfgFrom(master); // detail은 최상위 오퍼레이션 목록에 포함되지 않음
        JdbcExecutionResult exec = execResult(master, detail);

        assertEquals("1", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void multiMaster_usesFirstOperationCountOnly_ignoresSecondOperationCount() {
        // 멀티마스터: 최상위 오퍼레이션이 여러 개일 때 첫 번째만 대표로 사용.
        JdbcExecutionOperationResult m1 = opResult("insert_tb_user_v2_m1", ActionType.INSERT, true, false, 2, 0);
        JdbcExecutionOperationResult m2 = opResult("insert_tb_user_v2_m2", ActionType.INSERT, true, false, 9, 0);
        JdbcConfig jcfg = jcfgFrom(m1, m2);
        JdbcExecutionResult exec = execResult(m1, m2);

        assertEquals("2", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void leadingDeleteIsSkipped_usesNextNonDeleteOperationCount() {
        // DELETE/PROCEDURE는 대표 후보에서 제외되고, 그 다음 오퍼레이션(INSERT)이 대표가 된다.
        JdbcExecutionOperationResult delete = opResult("delete_tb_user_v2", ActionType.DELETE, true, false, 10, 0);
        JdbcExecutionOperationResult insert = opResult("insert_tb_user_v2", ActionType.INSERT, true, false, 1, 0);
        JdbcConfig jcfg = jcfgFrom(delete, insert);
        JdbcExecutionResult exec = execResult(delete, insert);

        assertEquals("1", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void multipleLeadingDeletesAreSkipped_usesFirstNonDeleteOperationCount() {
        JdbcExecutionOperationResult d1 = opResult("delete_tb_user_v2_d1", ActionType.DELETE, true, false, 3, 0);
        JdbcExecutionOperationResult d2 = opResult("delete_tb_user_v2_d2", ActionType.DELETE, true, false, 5, 0);
        JdbcExecutionOperationResult update = opResult("update_tb_user_v2", ActionType.UPDATE, true, false, 4, 0);
        JdbcConfig jcfg = jcfgFrom(d1, d2, update);
        JdbcExecutionResult exec = execResult(d1, d2, update);

        assertEquals("4", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void allTopLevelOperationsAreDeleteOrProcedure_returnsEmptyString() {
        JdbcExecutionOperationResult delete = opResult("delete_tb_user_v2", ActionType.DELETE, true, false, 3, 0);
        JdbcExecutionOperationResult proc = opResult("proc_tb_user_v2", ActionType.PROCEDURE, true, false, 0, 1);
        JdbcConfig jcfg = jcfgFrom(delete, proc);
        JdbcExecutionResult exec = execResult(delete, proc);

        assertEquals("", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void representativeFailed_returnsZero() {
        JdbcExecutionOperationResult insert = opResult("insert_tb_user_v2", ActionType.INSERT, false, false, 5, 0);
        JdbcConfig jcfg = jcfgFrom(insert);
        JdbcExecutionResult exec = execResult(insert);

        assertEquals("0", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void representativeSkipped_returnsZero() {
        JdbcExecutionOperationResult insert = opResult("insert_tb_user_v2", ActionType.INSERT, true, true, 5, 0);
        JdbcConfig jcfg = jcfgFrom(insert);
        JdbcExecutionResult exec = execResult(insert);

        assertEquals("0", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void representativeNotFoundInExecutionResult_returnsZero() {
        JdbcExecutionOperationResult configuredInsert =
                opResult("insert_tb_user_v2", ActionType.INSERT, true, false, 5, 0);
        JdbcConfig jcfg = jcfgFrom(configuredInsert);
        JdbcExecutionResult exec = execResult(
                opResult("some_other_op", ActionType.INSERT, true, false, 5, 0));

        assertEquals("0", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void nullConfig_returnsZero() {
        JdbcExecutionResult exec = execResult(
                opResult("insert_tb_user_v2", ActionType.INSERT, true, false, 5, 0));

        assertEquals("0", ApiResponseFactory.resolveRecordCountHeader(null, exec));
    }

    @Test
    void emptyOperationsInConfig_returnsZero() {
        JdbcConfig jcfg = new JdbcConfig();
        jcfg.setOperations(Collections.emptyList());
        JdbcExecutionResult exec = execResult(
                opResult("insert_tb_user_v2", ActionType.INSERT, true, false, 5, 0));

        assertEquals("0", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void emptyExecutionResult_returnsZero() {
        JdbcExecutionOperationResult insert = opResult("insert_tb_user_v2", ActionType.INSERT, true, false, 5, 0);
        JdbcConfig jcfg = jcfgFrom(insert);
        JdbcExecutionResult exec = execResult();

        assertEquals("0", ApiResponseFactory.resolveRecordCountHeader(jcfg, exec));
    }

    @Test
    void nullExecutionResult_returnsZero() {
        JdbcExecutionOperationResult insert = opResult("insert_tb_user_v2", ActionType.INSERT, true, false, 5, 0);
        JdbcConfig jcfg = jcfgFrom(insert);

        assertEquals("0", ApiResponseFactory.resolveRecordCountHeader(jcfg, null));
    }
}
