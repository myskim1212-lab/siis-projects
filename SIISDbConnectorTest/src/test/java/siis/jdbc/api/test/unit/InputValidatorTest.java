package siis.jdbc.api.test.unit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import siis.jdbc.api.test.exception.BadRequestException;
import siis.jdbc.api.test.validation.InputValidator;

import static org.assertj.core.api.Assertions.*;

/**
 * InputValidator unit tests.
 *
 * <p>API request JSON body validation logic verification.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("InputValidator Unit Tests")
class InputValidatorTest {

    private InputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InputValidator();
    }

    // ─── validateAndParse - Failure Cases ────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("TC-IV-01: null request body throws BadRequestException")
    void testNullBodyThrows() {
        assertThatThrownBy(() -> validator.validateAndParse(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("null");
    }

    @Test
    @Order(2)
    @DisplayName("TC-IV-02: empty string throws BadRequestException")
    void testEmptyStringThrows() {
        assertThatThrownBy(() -> validator.validateAndParse(""))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @Order(3)
    @DisplayName("TC-IV-03: blank (whitespace-only) string throws BadRequestException")
    void testBlankStringThrows() {
        assertThatThrownBy(() -> validator.validateAndParse("   \t\n  "))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @Order(4)
    @DisplayName("TC-IV-04: invalid JSON format throws BadRequestException")
    void testInvalidJsonThrows() {
        assertThatThrownBy(() -> validator.validateAndParse("{not valid json"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    @Order(5)
    @DisplayName("TC-IV-05: empty JSON object {} throws BadRequestException")
    void testEmptyJsonObjectThrows() {
        assertThatThrownBy(() -> validator.validateAndParse("{}"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @Order(6)
    @DisplayName("TC-IV-06: empty JSON array [] throws BadRequestException")
    void testEmptyJsonArrayThrows() {
        assertThatThrownBy(() -> validator.validateAndParse("[]"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @Order(7)
    @DisplayName("TC-IV-07: JSON literal null throws BadRequestException")
    void testJsonNullThrows() {
        assertThatThrownBy(() -> validator.validateAndParse("null"))
                .isInstanceOf(BadRequestException.class);
    }

    // ─── validateAndParse - Success Cases ────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("TC-IV-08: valid JSON object parses successfully and returns root node")
    void testValidJsonObjectParses() {
        String json = "{\"operations\": {\"insert_user\": {\"data\": []}}}";

        JsonNode result = validator.validateAndParse(json);

        assertThat(result).isNotNull();
        assertThat(result.isObject()).isTrue();
        assertThat(result.has("operations")).isTrue();
    }

    @Test
    @Order(9)
    @DisplayName("TC-IV-09: valid JSON with nested structure parses correctly")
    void testNestedJsonParses() {
        String json = "{\"a\": {\"b\": {\"c\": 42}}}";

        JsonNode result = validator.validateAndParse(json);

        assertThat(result.at("/a/b/c").asInt()).isEqualTo(42);
    }

    @Test
    @Order(10)
    @DisplayName("TC-IV-10: non-empty JSON array parses successfully")
    void testNonEmptyArrayParses() {
        String json = "[{\"key\": \"value\"}]";

        JsonNode result = validator.validateAndParse(json);

        assertThat(result).isNotNull();
        assertThat(result.isArray()).isTrue();
        assertThat(result).hasSize(1);
    }

    // ─── resolveDataRecordPath ────────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("TC-IV-11: resolveDataRecordPath('/operations') returns operations node")
    void testResolvePathOperations() {
        String json = "{\"operations\": {\"insert_user\": null}}";
        JsonNode root = validator.validateAndParse(json);

        JsonNode result = validator.resolveDataRecordPath(root, "/operations");

        assertThat(result).isNotNull();
        assertThat(result.isObject()).isTrue();
        assertThat(result.has("insert_user")).isTrue();
    }

    @Test
    @Order(12)
    @DisplayName("TC-IV-12: resolveDataRecordPath with nested '/a/b' returns correct node")
    void testResolveNestedPath() {
        String json = "{\"a\": {\"b\": {\"value\": 99}}}";
        JsonNode root = validator.validateAndParse(json);

        JsonNode result = validator.resolveDataRecordPath(root, "/a/b");

        assertThat(result).isNotNull();
        assertThat(result.get("value").asInt()).isEqualTo(99);
    }

    @Test
    @Order(13)
    @DisplayName("TC-IV-13: resolveDataRecordPath with non-existent path returns null")
    void testResolveNonExistentPath() {
        String json = "{\"operations\": {}}";
        JsonNode root = validator.validateAndParse(json);

        JsonNode result = validator.resolveDataRecordPath(root, "/missing/path");

        assertThat(result).isNull();
    }

    @Test
    @Order(14)
    @DisplayName("TC-IV-14: resolveDataRecordPath with '/' returns root node")
    void testResolveRootPath() {
        String json = "{\"key\": \"val\"}";
        JsonNode root = validator.validateAndParse(json);

        JsonNode result = validator.resolveDataRecordPath(root, "/");

        assertThat(result).isNotNull();
        assertThat(result.has("key")).isTrue();
    }

    @Test
    @Order(15)
    @DisplayName("TC-IV-15: resolveDataRecordPath with empty string returns root node")
    void testResolveEmptyPath() {
        String json = "{\"key\": \"val\"}";
        JsonNode root = validator.validateAndParse(json);

        JsonNode result = validator.resolveDataRecordPath(root, "");

        assertThat(result).isNotNull();
        assertThat(result).isSameAs(root);
    }

    @Test
    @Order(16)
    @DisplayName("TC-IV-16: resolveDataRecordPath with null root returns null")
    void testResolveNullRoot() {
        JsonNode result = validator.resolveDataRecordPath(null, "/operations");

        assertThat(result).isNull();
    }

    @Test
    @Order(17)
    @DisplayName("TC-IV-17: resolveDataRecordPath without leading slash still resolves")
    void testResolvePathWithoutLeadingSlash() {
        String json = "{\"operations\": {\"op1\": null}}";
        JsonNode root = validator.validateAndParse(json);

        JsonNode result = validator.resolveDataRecordPath(root, "operations");

        assertThat(result).isNotNull();
        assertThat(result.has("op1")).isTrue();
    }

    // ─── validateWithRequiredKey ──────────────────────────────────────────────

    @Test
    @Order(18)
    @DisplayName("TC-IV-18: validateWithRequiredKey succeeds when key exists")
    void testValidateWithRequiredKeySuccess() {
        String json = "{\"operations\": {\"insert_user\": null}, \"meta\": {}}";

        JsonNode result = validator.validateWithRequiredKey(json, "operations");

        assertThat(result).isNotNull();
        assertThat(result.has("operations")).isTrue();
    }

    @Test
    @Order(19)
    @DisplayName("TC-IV-19: validateWithRequiredKey throws when key is missing")
    void testValidateWithRequiredKeyMissing() {
        String json = "{\"data\": []}";

        assertThatThrownBy(() -> validator.validateWithRequiredKey(json, "operations"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("operations");
    }

    @Test
    @Order(20)
    @DisplayName("TC-IV-20: validateWithRequiredKey with null body throws BadRequestException")
    void testValidateWithRequiredKeyNullBody() {
        assertThatThrownBy(() -> validator.validateWithRequiredKey(null, "operations"))
                .isInstanceOf(BadRequestException.class);
    }

    // ─── Real-world Request Validation ───────────────────────────────────────

    @Test
    @Order(21)
    @DisplayName("TC-IV-21: typical TC001-style request body parses correctly")
    void testTypicalRequestBodyParses() {
        String json = "{\n"
                + "  \"operations\": {\n"
                + "    \"delete_tb_user_v2\": null,\n"
                + "    \"insert_tb_user_v2\": {\n"
                + "      \"data\": [\n"
                + "        {\"USER_ID\": \"ID_1\", \"USER_NAME\": \"Tester_1\"}\n"
                + "      ]\n"
                + "    }\n"
                + "  }\n"
                + "}";

        JsonNode root = validator.validateAndParse(json);
        JsonNode ops  = validator.resolveDataRecordPath(root, "/operations");

        assertThat(root).isNotNull();
        assertThat(ops).isNotNull();
        assertThat(ops.has("delete_tb_user_v2")).isTrue();
        assertThat(ops.get("delete_tb_user_v2").isNull()).isTrue();
        assertThat(ops.has("insert_tb_user_v2")).isTrue();

        JsonNode insertData = ops.get("insert_tb_user_v2").get("data");
        assertThat(insertData.isArray()).isTrue();
        assertThat(insertData).hasSize(1);
        assertThat(insertData.get(0).get("USER_ID").asText()).isEqualTo("ID_1");
    }

    @Test
    @Order(22)
    @DisplayName("TC-IV-22: request with Unicode values parses correctly")
    void testUnicodeValuesInRequest() {
        String json = "{\"operations\": {\"insert_user\": {\"data\": [{\"name\": \"\ud55c\uad6d\uc5b4\"}]}}}";

        JsonNode root = validator.validateAndParse(json);
        JsonNode data = root.at("/operations/insert_user/data/0/name");

        assertThat(data.asText()).isEqualTo("\ud55c\uad6d\uc5b4");
    }

    @Test
    @Order(23)
    @DisplayName("TC-IV-23: data_dump default-value-style check - plain string value parses")
    void testSimpleStringValueParses() {
        String json = "{\"flag\": false}";

        JsonNode result = validator.validateAndParse(json);

        assertThat(result.get("flag").asBoolean()).isFalse();
    }
}
