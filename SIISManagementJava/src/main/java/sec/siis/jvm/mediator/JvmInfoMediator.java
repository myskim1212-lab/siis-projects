package sec.siis.jvm.mediator;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sec.siis.jvm.JvmInfoExecutor;
import sec.siis.jvm.util.JvmPayloadUtil;

/**
 * MI 자신의 JVM/커넥션 풀 상태를 조회하는 전용 엔드포인트 — dbinfo(JDBC 데이터 조회/테스트)와는
 * 완전히 별개의 URL(/mi/monitor/v1/jvminfo)·클래스로 배선된다. dbinfo 쪽 코드(sec.siis.jdbc.*)를
 * 전혀 참조하지 않는다.
 *
 * action 필드로 분기하는 구조는 유지해서(현재는 JVM_INFO 하나뿐) 나중에 THREAD_DUMP,
 * FORCE_GC 같은 추가 액션이 생겨도 URL/클래스를 새로 안 만들고 여기에 얹을 수 있게 한다.
 */
public class JvmInfoMediator extends AbstractMediator {

    private static final Logger log = LoggerFactory.getLogger(JvmInfoMediator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean mediate(MessageContext context) {
        boolean success = true;
        Map<String, Object> response;
        String action = null;

        try {
            String payload = JvmPayloadUtil.readJsonPayload(context);
            JsonNode req = MAPPER.readTree(payload);
            action = req.path("action").asText("JVM_INFO");

            JvmInfoExecutor executor = new JvmInfoExecutor();
            switch (action.toUpperCase()) {
                case "JVM_INFO": {
                    response = executor.collect();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown action: " + action);
            }
        } catch (Exception e) {
            success = false;
            log.error("[JvmInfoMediator] action={} failed: {}", action, e.getMessage(), e);
            response = buildErrorResponse(action, e);
        }

        try {
            String responseJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            JvmPayloadUtil.setJsonPayload(context, responseJson, success);
        } catch (Exception e) {
            log.error("[JvmInfoMediator] Failed to set response payload", e);
        }

        return true;
    }

    private Map<String, Object> buildErrorResponse(String action, Exception e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("action", action != null ? action : "UNKNOWN");
        error.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        return error;
    }
}
