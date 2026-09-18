package siis.jdbc.api.test.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import siis.jdbc.api.test.exception.ConfigValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * registry/config/jdbc/{apiName}.yaml 파일을 로드하고 유효성을 검증하는 로더.
 *
 * <h3>검증 규칙</h3>
 * <ul>
 *   <li>api_name 필수</li>
 *   <li>operations 배열 필수 (비어 있으면 안 됨)</li>
 *   <li>각 operation 의 operation_name 필수</li>
 *   <li>SQL 에 등장하는 :param 이 fields 목록에 존재해야 함</li>
 *   <li>direction=OUT 은 SELECT 가 아닌 action_type 에서만 허용
 *       (프로시저/함수 컨텍스트)</li>
 *   <li>기본값 자동 적용: data_dump=false, batch_size=100,
 *       commit_action=COMMIT, commit_scope=ALL</li>
 * </ul>
 */
public class JdbcConfigLoader {

    // (?<!:) : PostgreSQL :: 타입 캐스트 연산자(예: value::text)의 오탐을 방지하는 negative lookbehind
    private static final Pattern NAMED_PARAM_PATTERN =
            Pattern.compile("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)");

    private static final Set<String> VALID_ZONE_STRATEGIES =
            Set.of("UTC", "SYSTEM", "FIXED");

    private final ObjectMapper yamlMapper;

    public JdbcConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * 파일 경로에서 YAML 을 로드하고 검증한다.
     *
     * @param yamlPath YAML 파일 경로
     * @return 검증된 {@link JdbcApiConfig}
     * @throws ConfigValidationException 검증 실패 시
     */
    public JdbcApiConfig load(Path yamlPath) {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            return loadAndValidate(is);
        } catch (IOException e) {
            throw new ConfigValidationException(
                    "Failed to read YAML file: " + yamlPath, e);
        }
    }

    /**
     * InputStream 에서 YAML 을 로드하고 검증한다 (테스트용 classpath 리소스에 유용).
     *
     * @param is YAML InputStream
     * @return 검증된 {@link JdbcApiConfig}
     * @throws ConfigValidationException 검증 실패 시
     */
    public JdbcApiConfig load(InputStream is) {
        return loadAndValidate(is);
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private JdbcApiConfig loadAndValidate(InputStream is) {
        JdbcApiConfig config;
        try {
            config = yamlMapper.readValue(is, JdbcApiConfig.class);
        } catch (IOException e) {
            throw new ConfigValidationException("Failed to parse YAML: " + e.getMessage(), e);
        }

        applyDefaults(config);
        validate(config);
        return config;
    }

    /** 명시적으로 설정되지 않은 필드에 기본값을 적용한다. */
    private void applyDefaults(JdbcApiConfig config) {
        if (config.getDataRecordPath() == null) {
            config.setDataRecordPath("/operations");
        }
        if (config.getTimestampZoneStrategy() == null) {
            config.setTimestampZoneStrategy("UTC");
        }
        if (config.getStopOnOperationError() == null) {
            config.setStopOnOperationError(true);
        }
        if (config.getStopOnRowError() == null) {
            config.setStopOnRowError(true);
        }

        List<OperationConfig> ops = config.getOperations();
        if (ops != null) {
            for (OperationConfig op : ops) {
                if (op.getCommitAction() == null) {
                    op.setCommitAction("COMMIT");
                }
                if (op.getCommitScope() == null) {
                    op.setCommitScope("ALL");
                }
            }
        }
    }

    /** 설정 값의 필수 항목 및 논리적 일관성을 검증한다. */
    private void validate(JdbcApiConfig config) {
        // 1. api_name 필수
        if (config.getApiName() == null || config.getApiName().isBlank()) {
            throw new ConfigValidationException("api_name is required but was missing or empty");
        }

        // 2. operations 배열 필수
        List<OperationConfig> ops = config.getOperations();
        if (ops == null) {
            throw new ConfigValidationException(
                    "[" + config.getApiName() + "] operations is required but was null");
        }
        if (ops.isEmpty()) {
            throw new ConfigValidationException(
                    "[" + config.getApiName() + "] operations must not be empty");
        }

        // 3. timestampZoneStrategy 값 검증
        String zoneStrategy = config.getTimestampZoneStrategy();
        if (zoneStrategy != null && !VALID_ZONE_STRATEGIES.contains(zoneStrategy.toUpperCase())) {
            throw new ConfigValidationException(
                    "[" + config.getApiName() + "] Invalid timestamp_zone_strategy: " + zoneStrategy
                            + " (expected: UTC / SYSTEM / FIXED)");
        }

        // 4. 각 operation 검증
        for (OperationConfig op : ops) {
            validateOperation(config.getApiName(), op);
        }
    }

    private void validateOperation(String apiName, OperationConfig op) {
        validateOperationInternal(apiName, op, null);
    }

    private void validateOperationInternal(String apiName, OperationConfig op, String parentCtx) {
        String ctx = "[" + apiName + "]";

        // 4-1. operation_name 필수
        if (op.getOperationName() == null || op.getOperationName().isBlank()) {
            String where = (parentCtx != null) ? parentCtx + " > Detail" : ctx;
            throw new ConfigValidationException(where + " operation_name is required");
        }

        // [P3b] Detail 검증 시 에러 메시지에 부모 Operation 이름 포함
        String opLabel = (parentCtx != null)
                ? parentCtx + " > Detail[" + op.getOperationName() + "]"
                : ctx + "[" + op.getOperationName() + "]";

        // 4-2. SQL :param 이 fields 에 존재해야 함
        if (op.getSql() != null && op.getFields() != null) {
            Set<String> fieldParams = op.getFields().stream()
                    .map(FieldConfig::getParam)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            Matcher matcher = NAMED_PARAM_PATTERN.matcher(op.getSql());
            while (matcher.find()) {
                String sqlParam = matcher.group(1).toUpperCase();
                if (!fieldParams.contains(sqlParam)) {
                    throw new ConfigValidationException(
                            opLabel + " SQL references :param '" + matcher.group(1)
                                    + "' but it is not defined in fields or inherit_fields");
                }
            }
        }

        // 4-3. direction=OUT 은 SELECT 에서 허용되지 않음
        if ("SELECT".equalsIgnoreCase(op.getActionType()) && op.getFields() != null) {
            for (FieldConfig field : op.getFields()) {
                if ("OUT".equalsIgnoreCase(field.getDirection())) {
                    throw new ConfigValidationException(
                            opLabel + " field '" + field.getParam()
                                    + "' has direction=OUT, which is not allowed for SELECT operations");
                }
            }
        }

        // [P2a] bulk=true 는 INSERT/UPDATE/DELETE/UPSERT 만 허용 (SELECT/PROCEDURE 불가)
        if (op.isBulk() && op.getActionType() != null) {
            String at = op.getActionType().toUpperCase();
            if ("SELECT".equals(at) || "PROCEDURE".equals(at)) {
                throw new ConfigValidationException(
                        opLabel + " bulk=true is not allowed for " + op.getActionType()
                                + " (only INSERT/UPDATE/DELETE/UPSERT)");
            }
        }

        // [P2b] chunk_commit_size 는 0 이상이어야 함
        if (op.getChunkCommitSize() != null && op.getChunkCommitSize() < 0) {
            throw new ConfigValidationException(
                    opLabel + " chunk_commit_size must be 0 or greater, got " + op.getChunkCommitSize());
        }

        // [P2c] fetch_size 는 0 이상이어야 함
        if (op.getFetchSize() != null && op.getFetchSize() < 0) {
            throw new ConfigValidationException(
                    opLabel + " fetch_size must be 0 or greater, got " + op.getFetchSize());
        }

        // [P2e] commit_scope=ROW 는 DML(INSERT/UPSERT/UPDATE/DELETE) 만 허용
        if ("ROW".equalsIgnoreCase(op.getCommitScope()) && op.getActionType() != null) {
            String at = op.getActionType().toUpperCase();
            if (!"INSERT".equals(at) && !"UPSERT".equals(at)
                    && !"UPDATE".equals(at) && !"DELETE".equals(at)) {
                throw new ConfigValidationException(
                        opLabel + " commit_scope=ROW is only allowed for INSERT/UPSERT/UPDATE/DELETE, not "
                                + op.getActionType());
            }
        }

        // [P3a] has_detail=true 인데 detail_operations 가 없으면 설정 오류
        if (op.isHasDetail() && (op.getDetailOperations() == null || op.getDetailOperations().isEmpty())) {
            throw new ConfigValidationException(
                    opLabel + " has_detail=true but detail_operations is missing or empty");
        }

        // Detail Operations 재귀 검증 [P3b: 부모 컨텍스트 전달]
        if (op.getDetailOperations() != null) {
            for (OperationConfig detailOp : op.getDetailOperations()) {
                validateOperationInternal(apiName, detailOp, opLabel);
            }
        }
    }
}
