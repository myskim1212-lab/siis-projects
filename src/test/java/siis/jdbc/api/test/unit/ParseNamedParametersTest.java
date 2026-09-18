package siis.jdbc.api.test.unit;

import org.junit.jupiter.api.*;
import siis.jdbc.api.test.sql.NamedParameterParser;
import siis.jdbc.api.test.sql.NamedParameterParser.ParsedSql;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * NamedParameterParser unit tests.
 *
 * <p>SQL Named Parameter(:param) to JDBC '?' substitution core logic verification.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("NamedParameterParser Unit Tests")
class ParseNamedParametersTest {

    // ─── Basic Substitution ──────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("TC-SQL-01: single :param is replaced with '?'")
    void testSingleParamReplacement() {
        String sql = "SELECT * FROM users WHERE user_id = :userId";
        Map<String, Object> params = Map.of("userId", "U001");

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql()).isEqualTo("SELECT * FROM users WHERE user_id = ?");
        assertThat(result.getBoundValues()).containsExactly("U001");
        assertThat(result.getParamCount()).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("TC-SQL-02: multiple different :params are all replaced")
    void testMultipleParamReplacement() {
        String sql = "SELECT * FROM users WHERE name = :name AND age = :age";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "Alice");
        params.put("age", 30);

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql())
                .isEqualTo("SELECT * FROM users WHERE name = ? AND age = ?");
        assertThat(result.getBoundValues()).hasSize(2);
        assertThat(result.getBoundValues()).contains("Alice", 30);
    }

    @Test
    @Order(3)
    @DisplayName("TC-SQL-03: same :param appearing multiple times expands each occurrence")
    void testDuplicateParamOccurrences() {
        String sql = "SELECT * FROM t WHERE a = :id OR b = :id OR c = :id";
        Map<String, Object> params = Map.of("id", "X99");

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql())
                .isEqualTo("SELECT * FROM t WHERE a = ? OR b = ? OR c = ?");
        assertThat(result.getBoundValues()).containsExactly("X99", "X99", "X99");
        assertThat(result.getParamCount()).isEqualTo(3);
    }

    @Test
    @Order(4)
    @DisplayName("TC-SQL-04: param key matching is case-insensitive")
    void testCaseInsensitiveParamMatching() {
        String sql = "INSERT INTO t (a, b) VALUES (:userId, :userName)";
        Map<String, Object> params = new HashMap<>();
        params.put("USERID", "U001");   // uppercase key
        params.put("username", "Bob");  // lowercase key

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql()).isEqualTo("INSERT INTO t (a, b) VALUES (?, ?)");
        assertThat(result.getBoundValues()).hasSize(2);
    }

    // ─── Collection Handling ─────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("TC-SQL-05: Collection value expands to IN (?, ?, ?)")
    void testCollectionExpansion() {
        String sql = "SELECT * FROM users WHERE user_id IN :ids";
        Map<String, Object> params = Map.of("ids", List.of("A", "B", "C"));

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql())
                .isEqualTo("SELECT * FROM users WHERE user_id IN (?, ?, ?)");
        assertThat(result.getBoundValues()).containsExactly("A", "B", "C");
        assertThat(result.getParamCount()).isEqualTo(3);
    }

    @Test
    @Order(6)
    @DisplayName("TC-SQL-06: single-element Collection expands to IN (?)")
    void testSingleElementCollectionExpansion() {
        String sql = "SELECT * FROM t WHERE id IN :ids";
        Map<String, Object> params = Map.of("ids", List.of("ONLY_ONE"));

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql()).isEqualTo("SELECT * FROM t WHERE id IN (?)");
        assertThat(result.getBoundValues()).containsExactly("ONLY_ONE");
    }

    @Test
    @Order(7)
    @DisplayName("TC-SQL-07: empty Collection inserts (NULL) to guarantee 0 rows")
    void testEmptyCollectionInsertsNull() {
        String sql = "SELECT * FROM users WHERE user_id IN :ids";
        Map<String, Object> params = Map.of("ids", Collections.emptyList());

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql())
                .isEqualTo("SELECT * FROM users WHERE user_id IN (NULL)");
        assertThat(result.getBoundValues()).isEmpty();
        assertThat(result.getParamCount()).isEqualTo(0);
    }

    @Test
    @Order(8)
    @DisplayName("TC-SQL-08: Set Collection expands to IN (?, ?, ?) with all elements")
    void testSetCollectionExpansion() {
        String sql = "DELETE FROM t WHERE id IN :ids";
        Set<Integer> ids = new LinkedHashSet<>(List.of(1, 2, 3));
        Map<String, Object> params = Map.of("ids", ids);

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql()).isEqualTo("DELETE FROM t WHERE id IN (?, ?, ?)");
        assertThat(result.getBoundValues()).hasSize(3);
    }

    // ─── No-Param SQL ────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("TC-SQL-09: SQL with no :params passes through unchanged")
    void testNoParamSqlPassThrough() {
        String sql = "SELECT COUNT(*) FROM users";

        ParsedSql result = NamedParameterParser.parse(sql, Map.of("unused", "val"));

        assertThat(result.getSql()).isEqualTo("SELECT COUNT(*) FROM users");
        assertThat(result.getBoundValues()).isEmpty();
    }

    @Test
    @Order(10)
    @DisplayName("TC-SQL-10: null param map binds null for unresolved params")
    void testNullParamMap() {
        String sql = "SELECT * FROM t WHERE id = :id";

        ParsedSql result = NamedParameterParser.parse(sql, null);

        assertThat(result.getSql()).isEqualTo("SELECT * FROM t WHERE id = ?");
        assertThat(result.getBoundValues()).containsExactly((Object) null);
    }

    @Test
    @Order(11)
    @DisplayName("TC-SQL-11: null SQL returns ParsedSql with null sql and empty values")
    void testNullSql() {
        ParsedSql result = NamedParameterParser.parse(null, Map.of("id", "X"));

        assertThat(result.getSql()).isNull();
        assertThat(result.getBoundValues()).isEmpty();
    }

    @Test
    @Order(12)
    @DisplayName("TC-SQL-12: blank SQL returns ParsedSql with original sql and empty values")
    void testBlankSql() {
        ParsedSql result = NamedParameterParser.parse("   ", Map.of("id", "X"));

        assertThat(result.getSql()).isEqualTo("   ");
        assertThat(result.getBoundValues()).isEmpty();
    }

    // ─── String Literal Skipping ─────────────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("TC-SQL-13: :param inside single-quoted string literal is ignored")
    void testParamInsideSingleQuotedStringIgnored() {
        String sql = "SELECT ':notAParam' AS label, col FROM t WHERE col = :realParam";
        Map<String, Object> params = Map.of("realParam", "value123");

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql())
                .isEqualTo("SELECT ':notAParam' AS label, col FROM t WHERE col = ?");
        assertThat(result.getBoundValues()).containsExactly("value123");
        assertThat(result.getParamCount()).isEqualTo(1);
    }

    @Test
    @Order(14)
    @DisplayName("TC-SQL-14: :param inside double-quoted identifier is ignored")
    void testParamInsideDoubleQuotedIdentifierIgnored() {
        String sql = "SELECT \"col:name\" FROM t WHERE id = :id";
        Map<String, Object> params = Map.of("id", 42);

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql()).isEqualTo("SELECT \"col:name\" FROM t WHERE id = ?");
        assertThat(result.getBoundValues()).containsExactly(42);
    }

    // ─── extractParamNames ───────────────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("TC-SQL-15: extractParamNames returns names in appearance order")
    void testExtractParamNamesOrder() {
        String sql = "INSERT INTO t (a,b,c) VALUES (:alpha, :beta, :gamma)";

        List<String> names = NamedParameterParser.extractParamNames(sql);

        assertThat(names).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    @Order(16)
    @DisplayName("TC-SQL-16: extractParamNames includes duplicates in order")
    void testExtractParamNamesDuplicates() {
        String sql = "UPDATE t SET a=:val, b=:val WHERE c=:val";

        List<String> names = NamedParameterParser.extractParamNames(sql);

        assertThat(names).containsExactly("val", "val", "val");
    }

    @Test
    @Order(17)
    @DisplayName("TC-SQL-17: extractParamNames with no params returns empty list")
    void testExtractParamNamesEmpty() {
        List<String> names = NamedParameterParser.extractParamNames("SELECT 1 FROM dual");

        assertThat(names).isEmpty();
    }

    @Test
    @Order(18)
    @DisplayName("TC-SQL-18: extractParamNames excludes params in quoted strings")
    void testExtractParamNamesExcludesQuoted() {
        String sql = "SELECT ':skip' AS s, col FROM t WHERE col = :keep";

        List<String> names = NamedParameterParser.extractParamNames(sql);

        assertThat(names).containsExactly("keep");
    }

    // ─── Complex Cases ───────────────────────────────────────────────────────

    @Test
    @Order(19)
    @DisplayName("TC-SQL-19: mixed Collection and scalar params in same SQL")
    void testMixedCollectionAndScalarParams() {
        String sql = "SELECT * FROM t WHERE status = :status AND id IN :ids";
        Map<String, Object> params = new HashMap<>();
        params.put("status", "ACTIVE");
        params.put("ids", List.of(1, 2, 3));

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql())
                .isEqualTo("SELECT * FROM t WHERE status = ? AND id IN (?, ?, ?)");
        assertThat(result.getParamCount()).isEqualTo(4);
        assertThat(result.getBoundValues().get(0)).isEqualTo("ACTIVE");
        assertThat(result.getBoundValues().subList(1, 4)).containsExactly(1, 2, 3);
    }

    @Test
    @Order(20)
    @DisplayName("TC-SQL-20: INSERT with 4 fields produces correct ? count and order")
    void testInsertManyFields() {
        String sql = "INSERT INTO users (id, name, age, score) VALUES (:id, :name, :age, :score)";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", "U001");
        params.put("name", "Alice");
        params.put("age", 30);
        params.put("score", 99.5);

        ParsedSql result = NamedParameterParser.parse(sql, params);

        assertThat(result.getSql())
                .isEqualTo("INSERT INTO users (id, name, age, score) VALUES (?, ?, ?, ?)");
        assertThat(result.getParamCount()).isEqualTo(4);
    }
}
