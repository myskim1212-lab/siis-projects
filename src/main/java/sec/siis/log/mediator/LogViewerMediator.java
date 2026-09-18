package sec.siis.log.mediator;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sec.siis.log.LogFileExecutor;
import sec.siis.log.util.LogPayloadUtil;

/**
 * MI 자신의 로그 파일을 조회하는 전용 엔드포인트 — WSO2 MI 기본 제공 로그 API가 매
 * 조회마다 파일 전체를 서버 메모리에 올리는 문제를 우회하기 위해 별도로 둔다
 * (자세한 배경은 {@link LogFileExecutor} 참고).
 *
 * action 필드로 분기하는 구조는 JvmInfoMediator와 동일한 컨벤션을 따른다 — 나중에
 * 액션이 늘어나도(예: DOWNLOAD, DELETE_OLD) URL/클래스를 새로 만들 필요가 없다.
 *
 * 지원 action:
 *  - LIST_FILES : 로그 디렉터리의 파일 목록(이름/크기/수정시각)
 *  - TAIL       : 파일 끝에서 최근 N줄만 읽기 (화면을 처음 열 때)
 *  - READ       : 이전 응답의 nextOffset부터 이어서 읽기 (폴링용, tail -f 유사)
 *  - SEARCH     : 파일을 스트리밍으로 훑으며 키워드 매치 라인 검색
 */
public class LogViewerMediator extends AbstractMediator {

    private static final Logger log = LoggerFactory.getLogger(LogViewerMediator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean mediate(MessageContext context) {
        boolean success = true;
        Map<String, Object> response;
        String action = null;

        try {
            String payload = LogPayloadUtil.readJsonPayload(context);
            JsonNode req = MAPPER.readTree(payload);
            action = req.path("action").asText("LIST_FILES");

            LogFileExecutor executor = new LogFileExecutor();
            switch (action.toUpperCase()) {
                case "LIST_FILES": {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("success", true);
                    m.put("files", executor.listFiles());
                    response = m;
                    break;
                }
                case "TAIL": {
                    String fileName = req.path("file").asText(null);
                    int lines = req.path("lines").asInt(200);
                    Map<String, Object> m = new LinkedHashMap<>(executor.tail(fileName, lines));
                    m.put("success", true);
                    response = m;
                    break;
                }
                case "READ": {
                    String fileName = req.path("file").asText(null);
                    long offset = req.path("offset").asLong(0);
                    Integer maxBytes = req.has("maxBytes") ? req.path("maxBytes").asInt() : null;
                    Map<String, Object> m = new LinkedHashMap<>(executor.readFrom(fileName, offset, maxBytes));
                    m.put("success", true);
                    response = m;
                    break;
                }
                case "SEARCH": {
                    String fileName = req.path("file").asText(null);
                    String keyword = req.path("keyword").asText(null);
                    int maxMatches = req.path("maxMatches").asInt(100);
                    int maxLinesScanned = req.path("maxLinesScanned").asInt(200_000);
                    Map<String, Object> m = new LinkedHashMap<>(
                            executor.search(fileName, keyword, maxMatches, maxLinesScanned));
                    m.put("success", true);
                    response = m;
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown action: " + action);
            }
        } catch (Exception e) {
            success = false;
            log.error("[LogViewerMediator] action={} failed: {}", action, e.getMessage(), e);
            response = buildErrorResponse(action, e);
        }

        try {
            String responseJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            LogPayloadUtil.setJsonPayload(context, responseJson, success);
        } catch (Exception e) {
            log.error("[LogViewerMediator] Failed to set response payload", e);
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
