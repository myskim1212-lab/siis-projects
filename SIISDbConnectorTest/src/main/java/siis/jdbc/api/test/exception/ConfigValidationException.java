package siis.jdbc.api.test.exception;

/**
 * JDBC API 설정 YAML 파싱/검증 시 발생하는 예외.
 */
public class ConfigValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigValidationException(String message) {
        super(message);
    }

    public ConfigValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
