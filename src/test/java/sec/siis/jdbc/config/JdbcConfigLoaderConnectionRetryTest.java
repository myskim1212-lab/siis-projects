package sec.siis.jdbc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * connection_retry_count / connection_retry_interval_ms / connection_read_timeout_ms
 * 파싱 및 기본값 적용(JdbcConfigLoader.applyDefaults) 검증.
 *
 * 세 필드 모두 미설정 시 기본값이 "기존 동작과 동일"(재시도 없음, 타임아웃 없음)이어야
 * 기존 API config가 영향받지 않는다 — 이걸 직접 확인한다.
 */
class JdbcConfigLoaderConnectionRetryTest {

    private static final String MINIMAL_OPERATION = """
            operations:
              - operation_name: select_dummy
                action_type: select
                data_record: dummy
                sql: "SELECT 1 FROM DUAL"
            """;

    private static String baseYaml(String extra) {
        return """
                api_name: TestApi
                target_system_key: TESTKEY
                target_jndi_name: jdbc/TEST
                """ + extra + MINIMAL_OPERATION;
    }

    @Test
    void appliesDefaults_whenFieldsOmitted() {
        JdbcConfig cfg = JdbcConfigLoader.loadFromString(baseYaml(""));

        assertEquals(JdbcConfigDefaults.DEFAULT_CONNECTION_RETRY_COUNT, cfg.getConnection_retry_count());
        assertEquals(JdbcConfigDefaults.DEFAULT_CONNECTION_RETRY_INTERVAL_MS, cfg.getConnection_retry_interval_ms());
        assertEquals(JdbcConfigDefaults.DEFAULT_CONNECTION_READ_TIMEOUT_MS, cfg.getConnection_read_timeout_ms());
    }

    @Test
    void parsesExplicitValues() {
        String yaml = baseYaml("""
                connection_retry_count: 8
                connection_retry_interval_ms: 500
                connection_read_timeout_ms: 60000
                """);

        JdbcConfig cfg = JdbcConfigLoader.loadFromString(yaml);

        assertEquals(8, cfg.getConnection_retry_count());
        assertEquals(500L, cfg.getConnection_retry_interval_ms());
        assertEquals(60000L, cfg.getConnection_read_timeout_ms());
    }

    @Test
    void fallsBackToDefault_whenValueIsNegative() {
        // 음수는 의미가 없으므로(재시도 횟수/시간이 음수일 수 없음) 기본값으로 대체되어야 한다.
        String yaml = baseYaml("""
                connection_retry_count: -1
                connection_retry_interval_ms: -100
                connection_read_timeout_ms: -1
                """);

        JdbcConfig cfg = JdbcConfigLoader.loadFromString(yaml);

        assertEquals(JdbcConfigDefaults.DEFAULT_CONNECTION_RETRY_COUNT, cfg.getConnection_retry_count());
        assertEquals(JdbcConfigDefaults.DEFAULT_CONNECTION_RETRY_INTERVAL_MS, cfg.getConnection_retry_interval_ms());
        assertEquals(JdbcConfigDefaults.DEFAULT_CONNECTION_READ_TIMEOUT_MS, cfg.getConnection_read_timeout_ms());
    }

    @Test
    void zeroIsValid_meansDisabled() {
        // 0은 "비활성화"라는 유효한 명시적 값이라 기본값으로 덮어써지면 안 된다.
        String yaml = baseYaml("""
                connection_retry_count: 0
                connection_retry_interval_ms: 0
                connection_read_timeout_ms: 0
                """);

        JdbcConfig cfg = JdbcConfigLoader.loadFromString(yaml);

        assertEquals(0, cfg.getConnection_retry_count());
        assertEquals(0L, cfg.getConnection_retry_interval_ms());
        assertEquals(0L, cfg.getConnection_read_timeout_ms());
    }
}
