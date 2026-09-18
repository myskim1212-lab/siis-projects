package sec.siis.jdbc.mediator.dbinfo;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sec.siis.jdbc.strategy.DbType;

public class DbInfoExecutor {

    private static final Logger log = LoggerFactory.getLogger(DbInfoExecutor.class);

    /** jdbc:{scheme}:... 에서 scheme을 뽑아 드라이버 클래스로 매핑 (CONNECTION_TEST_DIRECT 전용 사전 로드용). */
    private static final Pattern JDBC_SCHEME_PATTERN = Pattern.compile("^jdbc:([a-zA-Z0-9]+):");

    private static final Map<String, String> DRIVER_CLASS_BY_SCHEME = Map.of(
        "oracle",     "oracle.jdbc.OracleDriver",
        "postgresql", "org.postgresql.Driver",
        "sqlserver",  "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "mariadb",    "org.mariadb.jdbc.Driver",
        "mysql",      "com.mysql.cj.jdbc.Driver",
        "tibero",     "com.tmax.tibero.jdbc.TbDriver"
    );

    // -------------------------------------------------------------------------
    // CONNECTION_TEST
    // -------------------------------------------------------------------------

    public Map<String, Object> testConnection(DataSource ds, String jndiName) throws SQLException {
        long start = System.currentTimeMillis();
        try (Connection conn = ds.getConnection()) {
            long elapsed = System.currentTimeMillis() - start;
            DatabaseMetaData meta = conn.getMetaData();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("action", "CONNECTION_TEST");
            result.put("jndi_name", jndiName);
            result.put("db_type", detectDbType(meta.getDatabaseProductName()).toString());
            result.put("product", meta.getDatabaseProductName());
            result.put("version", meta.getDatabaseProductVersion());
            result.put("url", maskPassword(meta.getURL()));
            result.put("user", meta.getUserName());
            result.put("elapsed_ms", elapsed);
            return result;
        }
    }

    // -------------------------------------------------------------------------
    // CONNECTION_TEST_DIRECT
    // -------------------------------------------------------------------------

    public Map<String, Object> testConnectionDirect(String url, String user, String password) throws SQLException {
        warmUpDriver(url);
        long start = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            long elapsed = System.currentTimeMillis() - start;
            DatabaseMetaData meta = conn.getMetaData();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("action", "CONNECTION_TEST_DIRECT");
            result.put("db_type", detectDbType(meta.getDatabaseProductName()).toString());
            result.put("product", meta.getDatabaseProductName());
            result.put("version", meta.getDatabaseProductVersion());
            result.put("url", maskPassword(url));
            result.put("user", user);
            result.put("elapsed_ms", elapsed);
            return result;
        }
    }

    // -------------------------------------------------------------------------
    // TABLE_LAYOUT
    // -------------------------------------------------------------------------

    public Map<String, Object> getTableLayout(DataSource ds, String jndiName,
                                               String requestedSchema, String tableName) throws SQLException {
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            DbType dbType = detectDbType(meta.getDatabaseProductName());

            String catalog       = resolveCatalog(dbType, conn);
            String schema        = resolveSchema(dbType, conn, meta, requestedSchema);
            String resolvedTable = (dbType == DbType.ORACLE || dbType == DbType.TIBERO) ? tableName.toUpperCase() : tableName;

            // PK 조회 (KEY_SEQ 순서 유지)
            Map<Short, String> pkSeqMap = new TreeMap<>();
            try (ResultSet pkRs = meta.getPrimaryKeys(catalog, schema, resolvedTable)) {
                while (pkRs.next()) {
                    pkSeqMap.put(pkRs.getShort("KEY_SEQ"), pkRs.getString("COLUMN_NAME"));
                }
            }
            Set<String> pkSet = new HashSet<>(pkSeqMap.values());
            List<String> primaryKeys = new ArrayList<>(pkSeqMap.values());

            // 컬럼 조회
            List<Map<String, Object>> columns = new ArrayList<>();
            try (ResultSet colRs = meta.getColumns(catalog, schema, resolvedTable, null)) {
                while (colRs.next()) {
                    String colName = colRs.getString("COLUMN_NAME");
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("order",    colRs.getInt("ORDINAL_POSITION"));
                    col.put("name",     colName);
                    col.put("type",     colRs.getString("TYPE_NAME"));
                    col.put("size",     colRs.getInt("COLUMN_SIZE"));
                    col.put("scale",    colRs.getInt("DECIMAL_DIGITS"));
                    col.put("nullable", "YES".equalsIgnoreCase(colRs.getString("IS_NULLABLE")));
                    col.put("pk",       pkSet.contains(colName));
                    col.put("default",  colRs.getString("COLUMN_DEF"));
                    col.put("remarks",  colRs.getString("REMARKS") != null ? colRs.getString("REMARKS") : "");
                    columns.add(col);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success",      true);
            result.put("action",       "TABLE_LAYOUT");
            result.put("jndi_name",    jndiName);
            result.put("db_type",      dbType.toString());
            result.put("schema",       schema);
            result.put("table_name",   resolvedTable);
            result.put("column_count", columns.size());
            result.put("columns",      columns);
            result.put("primary_keys", primaryKeys);
            return result;
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private DbType detectDbType(String productName) {
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

    private String resolveCatalog(DbType dbType, Connection conn) throws SQLException {
        // Oracle/Tibero는 catalog 개념 없음
        return (dbType == DbType.ORACLE || dbType == DbType.TIBERO) ? null : conn.getCatalog();
    }

    private String resolveSchema(DbType dbType, Connection conn,
                                  DatabaseMetaData meta, String requested) throws SQLException {
        if (requested != null && !requested.isBlank()) {
            // Oracle/Tibero는 스키마명을 대문자로 저장
            return (dbType == DbType.ORACLE || dbType == DbType.TIBERO) ? requested.toUpperCase() : requested;
        }
        return switch (dbType) {
            case ORACLE, TIBERO -> meta.getUserName().toUpperCase(); // 기본 스키마 = 접속 계정명
            case POSTGRES       -> "public";
            case MSSQL          -> "dbo";
            case MYSQL, MARIADB -> null;
            default             -> null;
        };
    }

    private String maskPassword(String url) {
        if (url == null) return "";
        return url.replaceAll("(?i)(password=)[^;&]+", "$1***");
    }

    /**
     * DriverManager.getConnection() 호출 전에 URL로 추정한 드라이버 클래스를 미리 로드해둔다.
     *
     * <p>JDBC 4.0+ 드라이버는 클래스가 처음 로드되는 시점에 정적 초기화 블록에서
     * {@code DriverManager.registerDriver()}로 자기 자신을 등록한다. 이 등록은 그 클래스가
     * "이 JVM에서 최초로 로드되는 순간" 딱 한 번만 일어나므로, 서버가 막 재시작돼 아직 아무도
     * 그 드라이버를 로드한 적이 없으면(JNDI 데이터소스 접속 등으로도) DriverManager 레지스트리가
     * 비어있어 곧바로 DriverManager.getConnection()을 호출하면 "No suitable driver found"가 난다.
     * CONNECTION_TEST(JNDI 방식)는 커넥션풀이 드라이버를 직접 로드해서 이 문제가 없지만,
     * CONNECTION_TEST_DIRECT는 사전 로드 없이 DriverManager를 바로 쓰기 때문에 이 문제가 있었다.
     *
     * <p>드라이버 클래스명은 URL의 jdbc:{scheme}: 부분으로 추정한다 — best-effort이므로 클래스를
     * 못 찾아도(추정이 틀렸거나 해당 드라이버가 서버에 없는 경우) 예외를 삼키고 그냥 진행한다.
     * 어차피 뒤이은 DriverManager.getConnection()이 다시 시도하고, 이미 다른 경로로 로드돼
     * 있었다면 정상 동작하므로 기존 동작보다 나빠지는 경우는 없다.
     */
    private void warmUpDriver(String url) {
        String scheme = extractJdbcScheme(url);
        if (scheme == null) return;
        String driverClass = DRIVER_CLASS_BY_SCHEME.get(scheme.toLowerCase());
        if (driverClass == null) return;
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            log.warn("[DbInfoExecutor] CONNECTION_TEST_DIRECT 드라이버 사전 로드 실패 (계속 진행): {}", driverClass, e);
        }
    }

    private String extractJdbcScheme(String url) {
        if (url == null) return null;
        Matcher m = JDBC_SCHEME_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
