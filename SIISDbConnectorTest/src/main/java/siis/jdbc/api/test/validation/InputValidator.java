package siis.jdbc.api.test.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import siis.jdbc.api.test.exception.BadRequestException;

/**
 * API 요청 JSON 본문의 유효성을 검증하는 클래스.
 *
 * <h3>검증 규칙</h3>
 * <ul>
 *   <li>null 또는 공백 → {@link BadRequestException}</li>
 *   <li>JSON 파싱 실패 → {@link BadRequestException}</li>
 *   <li>비어 있는 JSON 객체/배열 → {@link BadRequestException}</li>
 *   <li>data_record_path 경로 탐색 지원</li>
 * </ul>
 */
public class InputValidator {

    private final ObjectMapper mapper;

    public InputValidator() {
        this.mapper = new ObjectMapper();
    }

    public InputValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * 요청 본문을 검증하고 파싱된 {@link JsonNode} 를 반환한다.
     *
     * @param requestBody JSON 요청 본문 문자열
     * @return 파싱된 루트 {@link JsonNode}
     * @throws BadRequestException 본문이 null / 공백 / 유효하지 않은 JSON 인 경우
     */
    public JsonNode validateAndParse(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            throw new BadRequestException("Request body must not be null or empty");
        }

        JsonNode root;
        try {
            root = mapper.readTree(requestBody);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid JSON format: " + e.getOriginalMessage());
        }

        if (root == null || root.isNull()) {
            throw new BadRequestException("Request body parsed to null JSON node");
        }

        if (root.isEmpty()) {
            throw new BadRequestException("Request body is an empty JSON object/array");
        }

        return root;
    }

    /**
     * JSON 트리에서 data_record_path 에 해당하는 노드를 탐색한다.
     *
     * <p>예: path="/operations" → root.get("operations")
     * <br>path="/a/b/c"  → root.get("a").get("b").get("c")
     *
     * @param root 루트 {@link JsonNode}
     * @param path "/"로 구분된 탐색 경로 (예: "/operations")
     * @return 대상 노드, 경로가 존재하지 않으면 {@code null}
     */
    public JsonNode resolveDataRecordPath(JsonNode root, String path) {
        if (root == null) return null;
        if (path == null || path.isEmpty() || "/".equals(path)) return root;

        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = normalized.split("/");

        JsonNode current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!current.isObject() || !current.has(part)) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    /**
     * 요청 본문이 특정 최상위 키를 포함하는지 검증한다.
     *
     * @param requestBody JSON 요청 본문
     * @param requiredKey 존재해야 하는 키 이름
     * @return 파싱된 루트 {@link JsonNode}
     * @throws BadRequestException 키가 없거나 본문이 유효하지 않은 경우
     */
    public JsonNode validateWithRequiredKey(String requestBody, String requiredKey) {
        JsonNode root = validateAndParse(requestBody);
        if (!root.has(requiredKey)) {
            throw new BadRequestException(
                    "Request body must contain the key '" + requiredKey + "'");
        }
        return root;
    }
}
