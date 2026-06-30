package sec.siis.jdbc.mediator.dbinfo;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import sec.siis.jdbc.strategy.DbType;

public class DbInfoExecutor {

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
}
