package sec.siis.jdbc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sec.siis.jdbc.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * inputJson의 operations 구조를 JdbcConfig 기준으로 검증한다.
 *
 * <pre>
 * 검증 규칙:
 *  1. config의 모든 operation이 data_record_path를 지정한 경우(커스텀 경로 전용):
 *     "operations" 키 없어도 허용. 존재할 경우에만 아래 규칙 적용.
 *  2. 하나라도 표준 경로를 사용하는 operation이 있으면 "operations" 키 필수.
 *  3. 요청의 "operations"에 config에 없는 operation명이 있으면 에러.
 *  4. config에 있지만 요청에 없는 operation은 허용 (optional).
 * </pre>
 */
public class InputValidator {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * inputJson을 파싱 및 검증하고, 재사용 가능한 JsonNode를 반환한다.
     * 호출자는 반환된 JsonNode를 그대로 JdbcExecutor에 전달하여 재파싱을 방지한다.
     *
     * @return 파싱된 root JsonNode (재사용용)
     * @throws BadRequestException 구조 불일치 또는 미정의 operation 존재 시
     */
    public static JsonNode validateAndParse(String inputJson, JdbcConfig ifCfg)
            throws BadRequestException {

        // 1. JSON 파싱 (1회만)
        JsonNode root;
        try {
            root = mapper.readTree(inputJson);
        } catch (Exception e) {
            throw new BadRequestException("Request body is not valid JSON: " + e.getMessage());
        }

        // 2. config의 모든 operation이 data_record_path를 지정했는지 확인
        //    모두 커스텀 경로인 경우 "operations" 키 없이도 처리 가능
        List<OperationConfig> configuredOps = ifCfg.getOperations();
        boolean allOpsHaveCustomPath = configuredOps.stream()
                .allMatch(op -> op.getData_record_path() != null
                             && !op.getData_record_path().isBlank());

        boolean hasOperations = root.has("operations") && !root.get("operations").isNull();

        // 3. "operations" 키 존재 및 타입 확인
        //    표준 경로를 사용하는 operation이 하나라도 있으면 "operations" 키 필수
        if (!allOpsHaveCustomPath && !hasOperations) {
            throw new BadRequestException("Request body must contain 'operations' field");
        }

        // 4. "operations" 키가 있는 경우에만 내부 구조 검증
        if (hasOperations) {
            JsonNode operationsNode = root.get("operations");

            if (!operationsNode.isObject()) {
                throw new BadRequestException(
                    "'operations' must be a JSON object, but got: " + operationsNode.getNodeType());
            }

            // config 기준 operation명 Set 구성 (표준 경로 op만 대상)
            Set<String> configuredOpNames = configuredOps.stream()
                    .map(OperationConfig::getOperation_name)
                    .collect(Collectors.toSet());

            // 요청에 있는 operation 순회 검증
            List<String> errors = new ArrayList<>();

            operationsNode.fields().forEachRemaining(entry -> {
                String opName = entry.getKey();
                if (!configuredOpNames.contains(opName)) {
                    errors.add(String.format(
                        "[%s] is not defined in the interface configuration", opName));
                }
            });

            if (!errors.isEmpty()) {
                throw new BadRequestException(
                    "Invalid request structure:\n  - " + String.join("\n  - ", errors));
            }
        }

        // 5. 파싱된 JsonNode 반환 (재사용)
        return root;
    }

}