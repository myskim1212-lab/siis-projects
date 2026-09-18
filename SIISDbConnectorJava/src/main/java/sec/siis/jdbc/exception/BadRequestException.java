package sec.siis.jdbc.exception;

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(
            new ApiError(
                "E400",
                message,
                "BAD_REQUEST",
                message
            ),
            null
        );
    }
}