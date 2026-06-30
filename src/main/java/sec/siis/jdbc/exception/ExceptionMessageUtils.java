package sec.siis.jdbc.exception;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExceptionMessageUtils {

    private ExceptionMessageUtils() {}

    public static String collectMessages(Throwable t) {
        if (t == null) return "";

        List<String> messages = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Throwable cur = t;
        while (cur != null) {
        	
            String msg = cur.getMessage();

            if (msg != null && !msg.isBlank() && seen.add(msg)) {
                messages.add(
                    cur.getClass().getSimpleName() + ": " + msg
                );
            }
            cur = cur.getCause();
        }

        return String.join(" | caused by: ", messages);
    }
    
    public static boolean isFatalError(Exception e) {
        if (e instanceof DbException || e instanceof SQLException) {
            String sqlState = "";
            if (e instanceof SQLException) {
                sqlState = ((SQLException) e).getSQLState();
            } else if (e.getCause() instanceof SQLException) {
                sqlState = ((SQLException) e.getCause()).getSQLState();
            }

            if (sqlState != null) {
                // 08: connection error, 42: syntax/object not found, 28: authorization failure
                return sqlState.startsWith("08") || sqlState.startsWith("42") || sqlState.startsWith("28");
            }
        }
        // Unknown errors (e.g. SystemException) are treated as fatal to halt processing
        return !(e instanceof ApiException || e instanceof NoDataException);
    }    
}