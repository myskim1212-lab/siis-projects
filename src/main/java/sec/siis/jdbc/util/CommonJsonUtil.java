package sec.siis.jdbc.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sec.siis.jdbc.config.JdbcConfig;
import sec.siis.jdbc.config.OperationConfig;
import sec.siis.jdbc.execution.JdbcExecutionOperationResult;
import sec.siis.jdbc.execution.JdbcExecutionResult;
import sec.siis.jdbc.execution.ObjectMapperHolder;


public class CommonJsonUtil {

    /** extractRecord 변환에 재사용되는 TypeReference (매 호출마다 익명 클래스 생성 방지). */
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new TypeReference<List<Map<String, Object>>>() {};

    private static ObjectMapper mapper() {
        return ObjectMapperHolder.INSTANCE.mapper;
    }

    public static String toJson(
            Map<String, List<Map<String, Object>>> data
    ) throws Exception {

        return mapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data);
    }
 
    public static String toJson(
    		Object data
    ) throws Exception {

        return mapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data);
    }
    
 
    public static String toJson(
    		JdbcExecutionResult data
    ) throws Exception {

        return mapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data);
    }

  
    public static String toJsonResult(
    	    List<JdbcExecutionOperationResult>  data
    ) throws Exception {

        return mapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data);
    }

    public static String toJson(
            List<Map<String, Object>> data
    ) throws Exception {

        return mapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(data);
    }    
    
   
    private static JsonNode resolvePath(JsonNode root, String path) {
        if (path == null || "/".equals(path)) return root;

        JsonNode cur = root;
        for (String p : path.substring(1).split("/")) {
            cur = cur.path(p);
            if (cur.isMissingNode()) return null;
        }
        return cur;
    }
    
    public static boolean existRecordNode(
            JsonNode root,
            String recordBasePath,
            String opName,
            String recordName,
            Map<String, JsonNode> pathCache
    ) {
        // 1. Base 노드(예: /operations) 확보
        JsonNode base = getBaseNode(root, recordBasePath, pathCache);
        if (base == null) return false;

        // 2. Base가 배열인 경우 (예: "operations": [ {...} ])
        if (base.isArray()) {
            for (JsonNode node : base) {
                // 해당 노드가 오퍼레이션 명(opName)을 가지고 있는지 확인
                if (node.has(opName)) {
                    // 오퍼레이션 노드 내부에서 recordName(예: data)이 있는지 확인
                    return true;
                }
            }
            return false;
        }

        // 3. Base가 일반 객체인 경우 (하위 호환성)
        if (base.has(opName)) {
            return true;
        }

        return false;
    }

    public static List<Map<String, Object>> extractRecord(
            JsonNode root,
            String recordBasePath,
            String opName,      // 추가: delete_tb_user 등
            String recordName,  // 실제 데이터 필드: data
            Map<String, JsonNode> pathCache
    ) {
        // 1. Base 노드(예: /operations) 확보
        JsonNode base = getBaseNode(root, recordBasePath, pathCache);
        if (base == null) return null;

        JsonNode targetOpNode = null;

        // 2. Base가 배열인 경우 순회하며 opName 노드 찾기
        if (base.isArray()) {
            for (JsonNode node : base) {
                if (node.has(opName)) {
                    targetOpNode = node.get(opName);
                    break;
                }
            }
        } else {
            targetOpNode = base.get(opName);
        }

        // 3. Operation 노드가 없거나, 그 안에 recordName(data) 필드가 없는 경우
        if (targetOpNode == null || !targetOpNode.has(recordName)) {
            return null; 
        }

        JsonNode recordNode = targetOpNode.get(recordName);

        // 4. recordNode(data)가 null인 경우 (delete-only 등)
        if (recordNode.isNull() || recordNode.isMissingNode()) {
            return Collections.emptyList();
        }

        // 5. recordNode가 배열이 아닌 경우 예외 처리
        if (!recordNode.isArray()) {
            throw new IllegalArgumentException(
                    "Record '" + recordName + "' under operation '" + opName + "' must be an array or null"
            );
        }

        // 6. List<Map>으로 변환하여 반환
        return ObjectMapperHolder.INSTANCE.mapper.convertValue(
                recordNode,
                LIST_MAP_TYPE
        );
    }

    /**
     * Operation 단위 data_record_path 사용 시 호출되는 오버로드.
     * recordBasePath 경로 자체의 존재 여부만 확인한다 (recordName 키 존재 여부 불필요).
     *
     * 표준 경로에서는 opName 키 존재 여부가 "클라이언트가 이 Operation을 호출하겠다"는
     * 신호였지만, 커스텀 경로에서는 data_record_path 경로에 도달할 수 있는 것 자체가
     * 실행 의사 표현이다.
     * 따라서 data_record(recordName) 키가 없어도 (DELETE 전체, PROCEDURE 등)
     * 경로가 존재하면 실행하고, rows=null/empty 로 처리한다.
     */
    public static boolean existRecordNode(
            JsonNode root,
            String recordBasePath,
            String recordName,
            Map<String, JsonNode> pathCache
    ) {
        JsonNode base = getBaseNode(root, recordBasePath, pathCache);
        return base != null;
    }

    /**
     * Operation 단위 data_record_path 사용 시 호출되는 오버로드.
     * recordBasePath → recordName 직접 탐색 (opName 없음).
     */
    public static List<Map<String, Object>> extractRecord(
            JsonNode root,
            String recordBasePath,
            String recordName,
            Map<String, JsonNode> pathCache
    ) {
        JsonNode base = getBaseNode(root, recordBasePath, pathCache);
        if (base == null) return null;

        if (!base.has(recordName)) return null;

        JsonNode recordNode = base.get(recordName);

        if (recordNode.isNull() || recordNode.isMissingNode()) {
            return Collections.emptyList();
        }

        if (!recordNode.isArray()) {
            throw new IllegalArgumentException(
                    "Record '" + recordName + "' at path '" + recordBasePath + "' must be an array or null"
            );
        }

        return ObjectMapperHolder.INSTANCE.mapper.convertValue(
                recordNode,
                LIST_MAP_TYPE
        );
    }

    /**
     * Bulk DML 전용 — 표준 경로(opName 포함) 기준으로 행 Iterator를 반환한다.
     * extractRecord()와 달리 전체 배열을 List로 구체화하지 않고 JsonNode 배열을 직접 순회한다.
     *
     * @return null  : 노드 자체가 없음 (skip 대상)
     *         empty : 노드는 있으나 data가 null/missing (rows=없는 실행 불필요 시 사용자가 판단)
     *         iterator : 행 데이터를 하나씩 반환하는 지연 이터레이터
     */
    public static Iterator<Map<String, Object>> streamRecord(
            JsonNode root,
            String recordBasePath,
            String opName,
            String recordName,
            Map<String, JsonNode> pathCache
    ) {
        JsonNode base = getBaseNode(root, recordBasePath, pathCache);
        if (base == null) return null;

        JsonNode targetOpNode = null;
        if (base.isArray()) {
            for (JsonNode node : base) {
                if (node.has(opName)) { targetOpNode = node.get(opName); break; }
            }
        } else {
            targetOpNode = base.get(opName);
        }

        if (targetOpNode == null || !targetOpNode.has(recordName)) return null;

        JsonNode recordNode = targetOpNode.get(recordName);
        if (recordNode.isNull() || recordNode.isMissingNode()) return Collections.emptyIterator();
        if (!recordNode.isArray()) throw new IllegalArgumentException(
                "Record '" + recordName + "' under operation '" + opName + "' must be an array or null");

        return toRowIterator(recordNode);
    }

    /**
     * Bulk DML 전용 — Operation 단위 data_record_path 기준으로 행 Iterator를 반환한다.
     */
    public static Iterator<Map<String, Object>> streamRecord(
            JsonNode root,
            String recordBasePath,
            String recordName,
            Map<String, JsonNode> pathCache
    ) {
        JsonNode base = getBaseNode(root, recordBasePath, pathCache);
        if (base == null) return null;
        if (!base.has(recordName)) return null;

        JsonNode recordNode = base.get(recordName);
        if (recordNode.isNull() || recordNode.isMissingNode()) return Collections.emptyIterator();
        if (!recordNode.isArray()) throw new IllegalArgumentException(
                "Record '" + recordName + "' at path '" + recordBasePath + "' must be an array or null");

        return toRowIterator(recordNode);
    }

    /** Row 단위 TypeReference (toRowIterator 재사용) */
    private static final TypeReference<Map<String, Object>> ROW_MAP_TYPE =
            new TypeReference<Map<String, Object>>() {};

    /**
     * JsonNode 배열을 Map<String,Object> Iterator로 변환한다.
     * 각 요소는 필요 시점에 변환되어 메모리 사용을 최소화한다.
     */
    private static Iterator<Map<String, Object>> toRowIterator(JsonNode arrayNode) {
        Iterator<JsonNode> nodeIter = arrayNode.elements();
        return new Iterator<Map<String, Object>>() {
            @Override
            public boolean hasNext() { return nodeIter.hasNext(); }
            @Override
            public Map<String, Object> next() {
                return ObjectMapperHolder.INSTANCE.mapper.convertValue(nodeIter.next(), ROW_MAP_TYPE);
            }
        };
    }

    // 중복되는 base 노드 추출 로직을 별도 메서드로 분리
    private static JsonNode getBaseNode(JsonNode root, String recordPath, Map<String, JsonNode> pathCache) {
        String pathKey = (recordPath == null || recordPath.isBlank()) ? "/" : recordPath;
        return pathCache.computeIfAbsent(pathKey, p -> resolvePath(root, p));
    }

    public static void filterOperationData(Map<String, Object> response, JdbcConfig jdbcCfg) {
        // 1. 기본 null 체크
        if (response == null || jdbcCfg == null) return;

        // 2. 안전하게 Object로 꺼내기
        Object opsObj = response.get("operations");
        
        // 3. Map 타입일 때만 로직 수행 (List인 경우 자동 패스)
        if (opsObj instanceof Map) {
            Map<String, Object> ops = (Map<String, Object>) opsObj;
            
            ops.forEach((opName, opResult) -> {
                // 4. JdbcConfig의 오퍼레이션 리스트 안전하게 가져오기
                if (jdbcCfg.getOperations() == null) return;

                String recordName = jdbcCfg.getOperations().stream()
                        .filter(op -> op != null && opName.equals(op.getOperation_name()))
                        .map(OperationConfig::getData_record)
                        .findFirst()
                        .orElse("data");

                // 5. 결과 데이터 제거 로직
                if (opResult instanceof JdbcExecutionOperationResult) {
                    ((JdbcExecutionOperationResult) opResult).setResultData(null);
                } else if (opResult instanceof Map) {
                    Map<String, Object> resultMap = (Map<String, Object>) opResult;
                    resultMap.remove(recordName);
                    resultMap.remove("data"); 
                }
            });
        }
    }
}
