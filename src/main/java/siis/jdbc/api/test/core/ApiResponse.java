package siis.jdbc.api.test.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * API 응답 모델
 */
public class ApiResponse {
    private final boolean success;
    private final int statusCode;
    private final String responseBody;
    private final JsonNode jsonResponse;
    private final String errorMessage;
    private final Map<String, String> headers;

    public ApiResponse(boolean success, int statusCode, String responseBody,
                      JsonNode jsonResponse, String errorMessage) {
        this(success, statusCode, responseBody, jsonResponse, errorMessage, null);
    }

    public ApiResponse(boolean success, int statusCode, String responseBody,
                      JsonNode jsonResponse, String errorMessage, Map<String, String> headers) {
        this.success = success;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.jsonResponse = jsonResponse;
        this.errorMessage = errorMessage;
        // 헤더 이름은 대소문자를 구분하지 않으므로 조회 시 케이스에 상관없이 찾을 수 있도록 한다.
        this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            this.headers.putAll(headers);
        }
    }

    /**
     * HTTP 응답 헤더 값을 조회한다 (대소문자 구분 없음).
     *
     * @param name 헤더 이름
     * @return 헤더 값 (없으면 null)
     */
    public String getHeader(String name) {
        return headers.get(name);
    }
    
    public boolean isSuccess() {
        return success && jsonResponse != null && 
               jsonResponse.has("success") && 
               jsonResponse.get("success").asBoolean();
    }
    
    public String getCode() {
        if (jsonResponse != null && jsonResponse.has("code")) {
            return jsonResponse.get("code").asText();
        }
        return null;
    }
    
    public String getMessage() {
        if (jsonResponse != null && jsonResponse.has("message")) {
            return jsonResponse.get("message").asText();
        }
        return null;
    }
    
    public String getErrorSummary() {
        if (jsonResponse != null && jsonResponse.has("message")) {
            return jsonResponse.get("errorSummary").asText();
        }
        return null;
    }    
    
    public JsonNode getOperation(String operationName) {
        if (jsonResponse != null && jsonResponse.has("operations")) {
            JsonNode operations = jsonResponse.get("operations");
            if (operations.has(operationName)) {
                return operations.get(operationName);
            }
        }
        return null;
    }
    
    public boolean isOperationSuccess(String operationName) {
        JsonNode operation = getOperation(operationName);
        return operation != null && 
               operation.has("success") && 
               operation.get("success").asBoolean();
    }
    
    public int getAffectedRows(String operationName) {
        JsonNode operation = getOperation(operationName);
        if (operation != null && operation.has("affectedRows")) {
            return operation.get("affectedRows").asInt();
        }
        return 0;
    }
    
    public boolean getRowErrors(String operationName) {
        JsonNode operation = getOperation(operationName);
        if (operation != null && operation.has("rowErrors")) {
            return operation.get("rowErrors").isArray();
        }
        return false;
    }      
    // Getters
    public int getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }
    public JsonNode getJsonResponse() { return jsonResponse; }
    public String getErrorMessage() { return errorMessage; }
    
    /**
     * SELECT 결과 rows 전체를 반환한다.
     *
     * @param operationName operations 키 이름 (ex: "select_tb_user_v2")
     * @return 결과 rows (없으면 빈 리스트)
     */
    public List<Map<String, Object>> getResultRows(String operationName) {
        try {
            JsonNode operations = jsonResponse.path("operations");
            if (operations.isMissingNode()) {
                return Collections.emptyList();
            }

            JsonNode op = operations.path(operationName);
            if (op.isMissingNode()) {
                return Collections.emptyList();
            }

            JsonNode rows = op.path("data");
            if (rows.isMissingNode() || !rows.isArray()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode row : rows) {
                Map<String, Object> rowMap = new LinkedHashMap<>();
                row.fields().forEachRemaining(entry -> {
                    rowMap.put(entry.getKey(), jsonNodeToObject(entry.getValue()));
                });
                result.add(rowMap);
            }
            return result;

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * SELECT 결과 row 건수를 반환한다.
     *
     * @param operationName operations 키 이름
     * @return row 건수
     */
    public int getResultRowCount(String operationName) {
        return getResultRows(operationName).size();
    }

    /**
     * SELECT 결과 특정 row의 특정 컬럼 값을 반환한다.
     *
     * @param operationName operations 키 이름
     * @param rowIndex      0-based row 인덱스
     * @param fieldName     컬럼명
     * @return 컬럼 값 (없거나 null이면 null 반환)
     */
    public Object getResultValue(String operationName, int rowIndex, String fieldName) {
        List<Map<String, Object>> rows = getResultRows(operationName);
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return null;
        }
        return rows.get(rowIndex).get(fieldName);
    }

    /**
     * SELECT 결과 특정 row의 특정 컬럼 값을 String으로 반환한다.
     * null이면 null 반환.
     */
    public String getResultStringValue(String operationName, int rowIndex, String fieldName) {
        Object val = getResultValue(operationName, rowIndex, fieldName);
        return val == null ? null : val.toString();
    }

    // =========================================================
    // Private 헬퍼
    // =========================================================

    /**
     * JsonNode → Java Object 변환
     *
     * JsonNode 타입별로 적절한 Java 타입으로 변환한다.
     * NullNode / MissingNode → null
     * BooleanNode            → Boolean
     * IntNode / ShortNode    → Integer
     * LongNode               → Long
     * FloatNode              → Float
     * DoubleNode             → Double
     * DecimalNode            → BigDecimal
     * TextNode               → String
     * ArrayNode              → List<Object>
     * ObjectNode             → Map<String, Object>
     */
    private Object jsonNodeToObject(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isInt() || node.isShort()) {
            return node.intValue();
        }
        if (node.isLong()) {
            return node.longValue();
        }
        if (node.isFloat()) {
            return node.floatValue();
        }
        if (node.isDouble()) {
            return node.doubleValue();
        }
        if (node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isBigInteger()) {
            return node.bigIntegerValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(child -> list.add(jsonNodeToObject(child)));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry ->
                map.put(entry.getKey(), jsonNodeToObject(entry.getValue()))
            );
            return map;
        }
        // 그 외 (binary 등) → toString 으로 폴백
        return node.toString();
    }    
}
