package sec.siis.jdbc.exception;

public class SystemException extends ApiException {

    /**
     * General system-level error (E500).
     */
    public SystemException(Throwable cause) {
        super(
            new ApiError(
                "E500",
                "System error",
                "SYSTEM",
                ExceptionMessageUtils.collectMessages(cause)
            ),
            cause
        );
    }

    /**
     * System error with a custom descriptive message.
     * Used for DataSource configuration errors, JNDI lookup failures, etc.
     */
    public SystemException(String message, Throwable cause) {
        super(
            new ApiError(
                "E500",
                message,
                "SYSTEM",
                ExceptionMessageUtils.collectMessages(cause)
            ),
            cause
        );
    }
}