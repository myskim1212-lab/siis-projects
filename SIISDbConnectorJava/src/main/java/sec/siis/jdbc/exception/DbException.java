package sec.siis.jdbc.exception;


public class DbException extends ApiException {

    /**
     * General DB operation failure (E102).
     * Used for query execution errors, constraint violations, etc.
     */
    public DbException(Throwable cause) {
        super(
            new ApiError(
                "E102",
                "Database operation failed",
                "DB_ERROR",
                ExceptionMessageUtils.collectMessages(cause)
            ),
            cause
        );
    }

    /**
     * Classified DB error with custom code, message, and error type.
     * Used for connection failures (E101), authentication errors, and type resolution errors.
     */
    public DbException(String code, String message, String type, Throwable cause) {
        super(
            new ApiError(
                code,
                message,
                type,
                ExceptionMessageUtils.collectMessages(cause)
            ),
            cause
        );
    }
}