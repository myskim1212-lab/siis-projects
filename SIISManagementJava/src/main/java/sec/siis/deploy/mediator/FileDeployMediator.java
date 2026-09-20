package sec.siis.deploy.mediator;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sec.siis.deploy.FileDeployExecutor;
import sec.siis.deploy.FileDeployExecutor.ArtifactType;
import sec.siis.deploy.util.DeployPayloadUtil;

/**
 * SSH 접속이 불가능한 인스턴스에서 JAR/시퀀스 파일을 배포/조회/삭제하는 전용
 * 엔드포인트 — wso2-mgmt-tool은 지금까지 이 작업을 SSH/SFTP로만 했는데
 * (WSO2 Management API에 lib/시퀀스 디렉터리용 엔드포인트가 없어서), SSH를 켤 수
 * 없는 인스턴스는 배포 자체가 불가능했다. 이 API는 대상 서버 안에서 직접 실행되므로
 * 파일 내용을 base64로 실어 보내면 SSH 없이도 같은 결과를 얻는다
 * (자세한 배경은 {@link FileDeployExecutor} 참고).
 *
 * 지원 action:
 *  - LIST_FILES : 대상 디렉터리(JAR: lib, SEQUENCE: sequences)의 파일 목록
 *  - DOWNLOAD   : 기존 파일 내용을 base64로 조회 (덮어쓰기/삭제 전 클라이언트 백업용)
 *  - DEPLOY     : 파일 배포(base64 content) — SEQUENCE는 루트 엘리먼트 검증 포함
 *  - DELETE     : 파일 삭제
 */
public class FileDeployMediator extends AbstractMediator {

    private static final Logger log = LoggerFactory.getLogger(FileDeployMediator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean mediate(MessageContext context) {
        boolean success = true;
        Map<String, Object> response;
        String action = null;

        try {
            String payload = DeployPayloadUtil.readJsonPayload(context);
            JsonNode req = MAPPER.readTree(payload);
            action = req.path("action").asText(null);
            if (action == null || action.trim().isEmpty()) {
                throw new IllegalArgumentException("action is required");
            }

            ArtifactType type = parseType(req.path("type").asText(null));
            FileDeployExecutor executor = new FileDeployExecutor();

            switch (action.toUpperCase()) {
                case "LIST_FILES": {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("success", true);
                    m.put("type", type.name());
                    m.put("files", executor.listFiles(type));
                    response = m;
                    break;
                }
                case "DOWNLOAD": {
                    String fileName = req.path("file").asText(null);
                    Map<String, Object> m = new LinkedHashMap<>(executor.download(type, fileName));
                    m.put("success", true);
                    response = m;
                    break;
                }
                case "DEPLOY": {
                    String fileName = req.path("file").asText(null);
                    String contentB64 = req.path("content").asText(null);
                    byte[] content = contentB64 != null
                            ? java.util.Base64.getDecoder().decode(contentB64)
                            : null;
                    Map<String, Object> m = new LinkedHashMap<>(executor.deploy(type, fileName, content));
                    m.put("success", true);
                    response = m;
                    break;
                }
                case "DELETE": {
                    String fileName = req.path("file").asText(null);
                    Map<String, Object> m = new LinkedHashMap<>(executor.delete(type, fileName));
                    m.put("success", true);
                    response = m;
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown action: " + action);
            }
        } catch (Exception e) {
            success = false;
            log.error("[FileDeployMediator] action={} failed: {}", action, e.getMessage(), e);
            response = buildErrorResponse(action, e);
        }

        try {
            String responseJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            DeployPayloadUtil.setJsonPayload(context, responseJson, success);
        } catch (Exception e) {
            log.error("[FileDeployMediator] Failed to set response payload", e);
        }

        return true;
    }

    private ArtifactType parseType(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("type is required (JAR or SEQUENCE)");
        }
        try {
            return ArtifactType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown type: " + type + " (expected JAR or SEQUENCE)");
        }
    }

    private Map<String, Object> buildErrorResponse(String action, Exception e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("action", action != null ? action : "UNKNOWN");
        error.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        return error;
    }
}
