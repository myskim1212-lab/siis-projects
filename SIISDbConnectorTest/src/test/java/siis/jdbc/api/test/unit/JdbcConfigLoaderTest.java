package siis.jdbc.api.test.unit;

import org.junit.jupiter.api.*;
import siis.jdbc.api.test.config.FieldConfig;
import siis.jdbc.api.test.config.JdbcApiConfig;
import siis.jdbc.api.test.config.JdbcConfigLoader;
import siis.jdbc.api.test.config.OperationConfig;
import siis.jdbc.api.test.exception.ConfigValidationException;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * JdbcConfigLoader 단위 테스트.
 *
 * <p>테스트 픽스처 YAML 파일은 src/test/resources/jdbc/ 에 위치한다.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("JdbcConfigLoader Unit Tests")
class JdbcConfigLoaderTest {

    private JdbcConfigLoader loader;

    @BeforeEach
    void setUp() {
        loader = new JdbcConfigLoader();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 정상 파싱 케이스
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("TC-CL-01: valid SELECT config YAML parses successfully")
    void testValidSelectConfig() {
        JdbcApiConfig config = load("jdbc/valid_select_config.yaml");

        assertThat(config).isNotNull();
        assertThat(config.getApiName()).isEqualTo("test_select_users");
        assertThat(config.getTargetJndiName()).isEqualTo("jdbc/TEST");
        assertThat(config.getOperations()).hasSize(1);

        OperationConfig op = config.getOperations().get(0);
        assertThat(op.getOperationName()).isEqualTo("test_select_op");
        assertThat(op.getActionType()).isEqualTo("SELECT");
        assertThat(op.getFields()).hasSize(3);
    }

    @Test
    @Order(2)
    @DisplayName("TC-CL-02: valid INSERT config YAML parses successfully")
    void testValidInsertConfig() {
        JdbcApiConfig config = load("jdbc/valid_insert_config.yaml");

        assertThat(config.getApiName()).isEqualTo("test_insert_user");
        OperationConfig op = config.getOperations().get(0);
        assertThat(op.getActionType()).isEqualTo("INSERT");
        assertThat(op.getFields()).extracting(FieldConfig::getType)
                .containsExactlyInAnyOrder("STRING", "STRING", "INT");
    }

    @Test
    @Order(3)
    @DisplayName("TC-CL-03: multi-operation config loads all operations")
    void testMultiOperationConfig() {
        JdbcApiConfig config = load("jdbc/multi_operation_config.yaml");

        assertThat(config.getOperations()).hasSize(3);
        assertThat(config.getOperations())
                .extracting(OperationConfig::getOperationName)
                .containsExactly("delete_op", "insert_op", "select_op");
        assertThat(config.getOperations())
                .extracting(OperationConfig::getActionType)
                .containsExactly("DELETE", "INSERT", "SELECT");
    }

    @Test
    @Order(4)
    @DisplayName("TC-CL-04: fields types parsed correctly for all supported types")
    void testFieldTypeParsing() {
        JdbcApiConfig config = load("jdbc/valid_select_config.yaml");
        List<FieldConfig> fields = config.getOperations().get(0).getFields();

        assertThat(fields).extracting(FieldConfig::getDataField)
                .containsExactly("USER_ID", "USER_NAME", "USER_AGE");
        assertThat(fields).extracting(FieldConfig::getParam)
                .containsExactly("USER_ID", "USER_NAME", "USER_AGE");
    }

    @Test
    @Order(5)
    @DisplayName("TC-CL-05: date/timestamp format lists are loaded correctly")
    void testDateTimestampFormats() {
        JdbcApiConfig config = load("jdbc/valid_select_config.yaml");

        assertThat(config.getDateFormats()).containsExactly("yyyy-MM-dd", "yyyyMMdd");
        assertThat(config.getTimestampFormats())
                .containsExactly("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        assertThat(config.getTimestampNtzFormats())
                .containsExactly("yyyy-MM-dd'T'HH:mm:ss.SSS",
                        "yyyy-MM-dd'T'HH:mm:ss",
                        "yyyy-MM-dd HH:mm:ss");
    }

    @Test
    @Order(6)
    @DisplayName("TC-CL-06: PROCEDURE with OUT direction passes validation")
    void testProcedureWithOutDirection() {
        JdbcApiConfig config = load("jdbc/procedure_with_out_config.yaml");

        OperationConfig op = config.getOperations().get(0);
        assertThat(op.getActionType()).isEqualTo("PROCEDURE");
        assertThat(op.getFields())
                .filteredOn(f -> "OUT".equals(f.getDirection()))
                .hasSize(2)
                .extracting(FieldConfig::getParam)
                .containsExactlyInAnyOrder("OUT_CODE", "OUT_MSG");
    }

    @Test
    @Order(7)
    @DisplayName("TC-CL-07: FIXED zone strategy with fixed_zone_id loads correctly")
    void testFixedZoneStrategy() {
        JdbcApiConfig config = load("jdbc/fixed_zone_config.yaml");

        assertThat(config.getTimestampZoneStrategy()).isEqualTo("FIXED");
        assertThat(config.getFixedZoneId()).isEqualTo("Asia/Seoul");
    }

    @Test
    @Order(8)
    @DisplayName("TC-CL-08: row commit config loaded correctly")
    void testRowCommitConfig() {
        JdbcApiConfig config = load("jdbc/row_commit_config.yaml");

        OperationConfig op = config.getOperations().get(0);
        assertThat(op.getCommitScope()).isEqualTo("ROW");
        assertThat(op.getCommitAction()).isEqualTo("COMMIT");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 기본값(default) 검증 케이스
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("TC-CL-09: data_dump defaults to false when omitted")
    void testDataDumpDefault() {
        JdbcApiConfig config = load("jdbc/defaults_omitted_config.yaml");

        assertThat(config.isDataDump()).isFalse();
    }

    @Test
    @Order(10)
    @DisplayName("TC-CL-10: batch_size defaults to 100 when omitted")
    void testBatchSizeDefault() {
        JdbcApiConfig config = load("jdbc/defaults_omitted_config.yaml");

        assertThat(config.getBatchSize()).isEqualTo(100);
    }

    @Test
    @Order(11)
    @DisplayName("TC-CL-11: commit_action defaults to COMMIT when omitted")
    void testCommitActionDefault() {
        JdbcApiConfig config = load("jdbc/defaults_omitted_config.yaml");

        OperationConfig op = config.getOperations().get(0);
        assertThat(op.getCommitAction()).isEqualTo("COMMIT");
    }

    @Test
    @Order(12)
    @DisplayName("TC-CL-12: commit_scope defaults to ALL when omitted")
    void testCommitScopeDefault() {
        JdbcApiConfig config = load("jdbc/defaults_omitted_config.yaml");

        OperationConfig op = config.getOperations().get(0);
        assertThat(op.getCommitScope()).isEqualTo("ALL");
    }

    @Test
    @Order(13)
    @DisplayName("TC-CL-13: stop_on_operation_error defaults to true when omitted")
    void testStopOnOperationErrorDefault() {
        JdbcApiConfig config = load("jdbc/defaults_omitted_config.yaml");

        assertThat(config.getStopOnOperationError()).isTrue();
    }

    @Test
    @Order(14)
    @DisplayName("TC-CL-14: max_row_limit defaults to 100000 when omitted")
    void testMaxRowLimitDefault() {
        JdbcApiConfig config = load("jdbc/defaults_omitted_config.yaml");

        assertThat(config.getMaxRowLimit()).isEqualTo(100000);
    }

    @Test
    @Order(15)
    @DisplayName("TC-CL-15: explicitly set batch_size=50 is respected")
    void testExplicitBatchSize() {
        JdbcApiConfig config = load("jdbc/multi_operation_config.yaml");

        assertThat(config.getBatchSize()).isEqualTo(50);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 검증 실패(ConfigValidationException) 케이스
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(16)
    @DisplayName("TC-CL-16: missing api_name throws ConfigValidationException")
    void testMissingApiName() {
        assertThatThrownBy(() -> load("jdbc/missing_api_name.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("api_name");
    }

    @Test
    @Order(17)
    @DisplayName("TC-CL-17: missing operations field throws ConfigValidationException")
    void testMissingOperations() {
        assertThatThrownBy(() -> load("jdbc/missing_operations.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("operations");
    }

    @Test
    @Order(18)
    @DisplayName("TC-CL-18: empty operations list throws ConfigValidationException")
    void testEmptyOperations() {
        assertThatThrownBy(() -> load("jdbc/empty_operations.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("operations");
    }

    @Test
    @Order(19)
    @DisplayName("TC-CL-19: missing operation_name throws ConfigValidationException")
    void testMissingOperationName() {
        assertThatThrownBy(() -> load("jdbc/missing_operation_name.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("operation_name");
    }

    @Test
    @Order(20)
    @DisplayName("TC-CL-20: SQL :param not defined in fields throws ConfigValidationException")
    void testSqlParamNotInFields() {
        assertThatThrownBy(() -> load("jdbc/sql_param_not_in_fields.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("STATUS")
                .hasMessageContaining("fields");
    }

    @Test
    @Order(21)
    @DisplayName("TC-CL-21: SELECT with direction=OUT throws ConfigValidationException")
    void testSelectWithOutDirection() {
        assertThatThrownBy(() -> load("jdbc/select_with_out_direction.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("OUT")
                .hasMessageContaining("SELECT");
    }

    @Test
    @Order(22)
    @DisplayName("TC-CL-22: null InputStream throws ConfigValidationException")
    void testNullInputStream() {
        assertThatThrownBy(() -> loader.load((InputStream) null))
                .isInstanceOf(Exception.class);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // max_row_limit 검증 케이스
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(24)
    @DisplayName("TC-CL-24: 전역 max_row_limit 명시 설정값(5)이 올바르게 파싱된다")
    void testExplicitGlobalMaxRowLimit() {
        JdbcApiConfig config = load("jdbc/max_row_limit_global.yaml");

        assertThat(config.getMaxRowLimit()).isEqualTo(5);
    }

    @Test
    @Order(25)
    @DisplayName("TC-CL-25: operation 단위 max_row_limit 설정값이 YAML에 포함되어 파싱된다")
    void testOperationMaxRowLimitParsed() {
        JdbcApiConfig config = load("jdbc/max_row_limit_operation.yaml");

        // 전역 한도 확인
        assertThat(config.getMaxRowLimit()).isEqualTo(10);
        // operation이 2개 정상 로드되었는지 확인
        assertThat(config.getOperations()).hasSize(2);
        assertThat(config.getOperations())
                .extracting(OperationConfig::getOperationName)
                .containsExactly("insert_op_with_limit", "insert_op_no_limit");
    }

    @Test
    @Order(26)
    @DisplayName("TC-CL-26: max_row_limit=0 설정 시 파싱 정상 동작 (0 = 제한 없음 의미)")
    void testZeroMaxRowLimit() {
        String yaml =
            "api_name: test_zero_mrl\n" +
            "target_system_key: TEST\n" +
            "target_jndi_name: jdbc/TEST\n" +
            "max_row_limit: 0\n" +
            "operations:\n" +
            "  - operation_name: op1\n" +
            "    action_type: INSERT\n" +
            "    data_record: data\n" +
            "    sql: |\n" +
            "      INSERT INTO T(ID) VALUES (:ID)\n" +
            "    fields:\n" +
            "      - { data_field: ID, param: ID, type: STRING }\n";

        java.io.InputStream is = new java.io.ByteArrayInputStream(
                yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        JdbcApiConfig config = loader.load(is);
        assertThat(config.getMaxRowLimit()).isEqualTo(0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // bulk / chunk_commit_size / fetch_size / commit_scope / has_detail 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(27)
    @DisplayName("TC-CL-27: bulk=true on SELECT throws ConfigValidationException")
    void testBulkOnSelectThrows() {
        assertThatThrownBy(() -> load("jdbc/bulk_select_invalid.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("bulk")
                .hasMessageContaining("SELECT");
    }

    @Test
    @Order(28)
    @DisplayName("TC-CL-28: bulk=true on INSERT with chunk_commit_size passes validation")
    void testBulkInsertValid() {
        JdbcApiConfig config = load("jdbc/bulk_insert_valid.yaml");

        OperationConfig op = config.getOperations().get(0);
        assertThat(op.isBulk()).isTrue();
        assertThat(op.getChunkCommitSize()).isEqualTo(500);
        assertThat(op.getActionType()).isEqualTo("INSERT");
    }

    @Test
    @Order(29)
    @DisplayName("TC-CL-29: chunk_commit_size=-1 throws ConfigValidationException")
    void testChunkCommitSizeNegativeThrows() {
        assertThatThrownBy(() -> load("jdbc/chunk_commit_size_negative.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("chunk_commit_size");
    }

    @Test
    @Order(30)
    @DisplayName("TC-CL-30: fetch_size=-100 throws ConfigValidationException")
    void testFetchSizeNegativeThrows() {
        assertThatThrownBy(() -> load("jdbc/fetch_size_negative.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("fetch_size");
    }

    @Test
    @Order(31)
    @DisplayName("TC-CL-31: commit_scope=ROW on SELECT throws ConfigValidationException")
    void testRowScopeOnSelectThrows() {
        assertThatThrownBy(() -> load("jdbc/row_scope_delete_invalid.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("ROW")
                .hasMessageContaining("SELECT");
    }

    @Test
    @Order(34)
    @DisplayName("TC-CL-34: commit_scope=ROW on PROCEDURE throws ConfigValidationException")
    void testRowScopeOnProcedureThrows() {
        assertThatThrownBy(() -> load("jdbc/row_scope_procedure_invalid.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("ROW")
                .hasMessageContaining("PROCEDURE");
    }

    @Test
    @Order(35)
    @DisplayName("TC-CL-35: commit_scope=ROW on DELETE passes validation after fix")
    void testRowScopeOnDeleteValid() {
        JdbcApiConfig config = load("jdbc/row_scope_delete_valid.yaml");

        OperationConfig op = config.getOperations().get(0);
        assertThat(op.getActionType()).isEqualTo("DELETE");
        assertThat(op.getCommitScope()).isEqualTo("ROW");
    }

    @Test
    @Order(36)
    @DisplayName("TC-CL-36: commit_scope=ROW on UPDATE passes validation after fix")
    void testRowScopeOnUpdateValid() {
        JdbcApiConfig config = load("jdbc/row_scope_update_valid.yaml");

        OperationConfig op = config.getOperations().get(0);
        assertThat(op.getActionType()).isEqualTo("UPDATE");
        assertThat(op.getCommitScope()).isEqualTo("ROW");
    }

    @Test
    @Order(32)
    @DisplayName("TC-CL-32: has_detail=true without detail_operations throws ConfigValidationException")
    void testHasDetailMissingOperationsThrows() {
        assertThatThrownBy(() -> load("jdbc/has_detail_missing_operations.yaml"))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("has_detail")
                .hasMessageContaining("detail_operations");
    }

    @Test
    @Order(33)
    @DisplayName("TC-CL-33: PostgreSQL :: cast operator in SQL does not cause false :param detection")
    void testPostgresqlCastSqlNoBogusParam() {
        // SQL 내 value::text 형태의 캐스트 연산자가 :text 로 오탐되지 않아야 함
        JdbcApiConfig config = load("jdbc/postgresql_cast_sql.yaml");

        assertThat(config).isNotNull();
        assertThat(config.getOperations()).hasSize(1);
        OperationConfig op = config.getOperations().get(0);
        // 캐스트 타입명(text, date, integer)이 미정의 param으로 오류를 내지 않고 파싱 성공
        assertThat(op.getOperationName()).isEqualTo("select_with_cast");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 실제 레지스트리 YAML 호환성 검증
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @Order(23)
    @DisplayName("TC-CL-23: real registry YAML for MSSQL no-param select parses correctly")
    void testRealRegistryYamlMssql() {
        // 실제 레지스트리 YAML 파일 직접 로드 (통합 확인)
        java.nio.file.Path registryYaml = java.nio.file.Path.of(
                "D:/wso2/WSO2-Integration-Studio-8.5.0-win32-x86_64/IntegrationStudio"
                + "/runtime/microesb/registry/config/jdbc"
                + "/select_tb_user_v2_mssql_no_param.yaml");

        // 파일이 존재하는 경우에만 실행 (CI 환경 무관)
        assumeFileExists(registryYaml);

        JdbcApiConfig config = loader.load(registryYaml);
        assertThat(config.getApiName()).isEqualTo("select_tb_user_v2_mssql_no_param");
        assertThat(config.getTargetJndiName()).isEqualTo("jdbc/SST0002");
        assertThat(config.getOperations()).isNotEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────────────────────────────────

    private JdbcApiConfig load(String classpathResource) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalStateException("Test fixture not found: " + classpathResource);
        }
        return loader.load(is);
    }

    /** 파일이 없으면 테스트를 건너뜀 (JUnit 5 assumeTrue 활용) */
    private void assumeFileExists(java.nio.file.Path path) {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.exists(path),
                "Registry YAML not found, skipping: " + path);
    }
}
