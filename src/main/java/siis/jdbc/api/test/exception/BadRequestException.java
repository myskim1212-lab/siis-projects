package siis.jdbc.api.test.exception;

/**
 * API 요청 데이터가 유효하지 않을 때 발생하는 예외.
 */
public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
