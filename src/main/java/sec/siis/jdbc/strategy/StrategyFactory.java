package sec.siis.jdbc.strategy;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sec.siis.jdbc.exception.DbException;
import sec.siis.jdbc.exception.ExceptionMessageUtils;
import sec.siis.jdbc.exception.SystemException;
import sec.siis.jdbc.logging.LogMessageManager;

public class StrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(StrategyFactory.class);

    // DbType cache — resolved once per jndiName, reused on subsequent calls
    private static final ConcurrentHashMap<String, DbType> typeCache
        = new ConcurrentHashMap<>();

    private StrategyFactory() {}

    public static DbStrategy create(DataSource ds, String jndiName, String msgID) throws Exception {

        // 1. Resolve DbType — first call connects to DB; subsequent calls use cache
        DbType type = typeCache.get(jndiName);
        if (type != null) {
            LogMessageManager.debugDbTypeCacheHit(log, msgID, jndiName, type.toString());
        } else {
            LogMessageManager.debugDbTypeCacheMiss(log, msgID, jndiName);
            try {
                type = DbTypeResolver.resolve(ds, jndiName);
            } catch (Exception e) {
                throw classifyResolutionError(jndiName, e);
            }
            DbType existing = typeCache.putIfAbsent(jndiName, type);
            if (existing != null) {
                type = existing;
            }
        }

        // 2. Return DB-specific strategy
        switch (type) {
            case ORACLE:   return new OracleStrategy(msgID);
            case TIBERO:   return new OracleStrategy(msgID); // Tibero는 Oracle 고호환 → OracleStrategy 재사용
            case MSSQL:    return new MsSqlStrategy(msgID);
            case POSTGRES: return new PostgreStrategy(msgID);
            case MYSQL:    return new MySqlStrategy(msgID);
            case MARIADB:  return new MariaDbStrategy(msgID);
            default:
                throw new UnsupportedOperationException("Unsupported DB type: " + type);
        }
    }

    /**
     * Classifies a DbTypeResolver failure into a meaningful exception.
     *
     * <ul>
     *   <li>SQLState 08xxx — DB server unreachable / connection refused / network error</li>
     *   <li>SQLState 28xxx — Authentication failure (invalid credentials)</li>
     *   <li>Other SQLException — Type resolution failed due to unexpected SQL error</li>
     *   <li>Non-SQL exception — DataSource / JNDI configuration error</li>
     * </ul>
     */
    private static Exception classifyResolutionError(String jndiName, Throwable cause) {

        if (cause instanceof SQLException) {
            String state   = ((SQLException) cause).getSQLState();
            String rootMsg = rootMessage(cause);

            // Connection-level errors: server unreachable, socket timeout, network issue
            if (state != null && state.startsWith("08")) {
                return new DbException("E101",
                    "Database connection failed [" + jndiName + "]: " + rootMsg,
                    "DB_CONNECTION_ERROR", cause);
            }

            // Authentication errors: invalid username/password, login denied
            if (state != null && state.startsWith("28")) {
                return new DbException("E101",
                    "Database authentication failed [" + jndiName + "]: " + rootMsg,
                    "DB_AUTH_ERROR", cause);
            }

            // Other SQL errors encountered during type resolution
            return new DbException("E101",
                "Failed to resolve database type [" + jndiName + "]: " + rootMsg,
                "DB_TYPE_RESOLUTION_ERROR", cause);
        }

        // Non-SQL errors: JNDI name not found, DataSource null, class not found, etc.
        return new SystemException(
            "DataSource configuration error [" + jndiName + "]: " + rootMessage(cause),
            cause);
    }

    /**
     * Walks the exception chain and returns the message of the deepest (root) cause.
     */
    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return (cur.getMessage() != null) ? cur.getMessage() : cur.getClass().getSimpleName();
    }

    /** Clears the entire type cache — for testing or DataSource reconfiguration. */
    public static void clearCache() {
        typeCache.clear();
    }

    /** Removes a single entry from the type cache — for DataSource replacement. */
    public static void evict(String jndiName) {
        typeCache.remove(jndiName);
    }
}
