package sec.siis.jdbc.strategy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sec.siis.jdbc.logging.LogMessageManager;

public final class DbTypeResolver {

    private static final Logger log = LoggerFactory.getLogger(DbTypeResolver.class);

    /** Connections taking longer than this (ms) trigger a WARN log. */
    private static final long SLOW_CONN_THRESHOLD_MS = 3_000;

    private DbTypeResolver() {}

    /**
     * Resolves the database type by connecting to the DataSource and inspecting metadata.
     *
     * <p>Logs the following events:
     * <ul>
     *   <li>DEBUG — before the connection attempt</li>
     *   <li>INFO  — on success: product name/version, JDBC URL (password masked), user name, elapsed ms</li>
     *   <li>WARN  — if the connection takes longer than {@value #SLOW_CONN_THRESHOLD_MS} ms</li>
     * </ul>
     *
     * @param ds       DataSource to connect to
     * @param jndiName JNDI name of the DataSource (used in log messages)
     * @throws SQLException propagated directly so callers can inspect SQLState
     */
    public static DbType resolve(DataSource ds, String jndiName) throws SQLException {
        Connection conn = null;
        try {
            LogMessageManager.debugConnectionAcquiring(log, jndiName);

            long start = System.currentTimeMillis();
            conn = ds.getConnection();
            long elapsed = System.currentTimeMillis() - start;

            DatabaseMetaData meta    = conn.getMetaData();
            String productName       = meta.getDatabaseProductName();
            String productVersion    = meta.getDatabaseProductVersion();
            String url               = maskPassword(meta.getURL());
            String user              = meta.getUserName();

            DbType dbType = detectType(productName);

            LogMessageManager.infoConnectionResolved(
                    log, jndiName, productName, productVersion, url, user, dbType, elapsed);

            if (elapsed > SLOW_CONN_THRESHOLD_MS) {
                LogMessageManager.warnSlowConnection(log, jndiName, elapsed, SLOW_CONN_THRESHOLD_MS);
            }

            return dbType;

        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------

    /** Determines DbType from the database product name string. */
    private static DbType detectType(String productName) {
        if (productName == null) return DbType.UNKNOWN;
        String name = productName.toLowerCase();
        if (name.contains("oracle"))     return DbType.ORACLE;
        if (name.contains("tibero"))     return DbType.TIBERO;
        if (name.contains("postgres"))   return DbType.POSTGRES;
        if (name.contains("mysql"))      return DbType.MYSQL;
        if (name.contains("maria"))      return DbType.MARIADB;
        if (name.contains("sql server")) return DbType.MSSQL;
        return DbType.UNKNOWN;
    }

    /**
     * Masks the {@code password} query parameter in a JDBC URL so it is safe to log.
     * Example: {@code jdbc:sqlserver://host;password=secret} → {@code jdbc:sqlserver://host;password=***}
     */
    static String maskPassword(String url) {
        if (url == null) return "";
        return url.replaceAll("(?i)(password=)[^;&]+", "$1***");
    }
}
