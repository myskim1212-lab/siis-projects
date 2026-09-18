package siis.jdbc.api.test.functional;

import siis.jdbc.api.test.core.ApiResponse;
import siis.jdbc.api.test.core.JdbcApiClient;
import siis.jdbc.api.test.core.TestConfig;
import siis.jdbc.api.test.data.TestDataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * JDBC API 기능 테스트 (PostgreSQL)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FunctionalTestForPostgre {

    private static final Logger log = LoggerFactory.getLogger(FunctionalTestForPostgre.class);

    private static JdbcApiClient apiClient;
    private static String endpoint;

    @BeforeAll
    static void setUp() {
        apiClient = new JdbcApiClient(TestConfig.getBaseUrl());
        log.info("Test setup completed - Base URL: {}", TestConfig.getBaseUrl());
    }

    @AfterAll
    static void tearDown() {
        if (apiClient != null) {
            apiClient.close();
        }
        log.info("Test teardown completed");
    }

    @Test
    @Order(1)
    @DisplayName("TC001 - Single Row INSERT")
    void testDeleteInsert() {
        log.info("Running TC001 - Basic Insert Test");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC001.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        log.info("TC001 - PASSED");
    }

    @Test
    @Order(2)
    @DisplayName("TC002 - Single Row INSERT via Procedure")
    void testDeleteInsertProcedure() {
        log.info("Running TC002 - Delete-Insert-Procedure");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC002.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("delete_tb_user_v2")).isEqualTo(1);
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.isOperationSuccess("proc_tb_user_v2")).isTrue();

        log.info("TC002 - PASSED");
    }

    @Test
    @Order(3)
    @DisplayName("TC003 - Bulk INSERT 100 rows")
    void testBulkInsert100() {
        log.info("Running TC003 - Bulk Insert 100 records");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC003.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(10);

        log.info("TC003 - PASSED");
    }

    @Test
    @Order(4)
    @DisplayName("TC004 - DELETE and INSERT Transaction")
    void testDeleteInsertTransaction() {
        log.info("Running TC004 - Delete-Insert Transaction");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC004.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("delete_tb_user_v2")).isGreaterThanOrEqualTo(0);
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        log.info("TC004 - PASSED");
    }

    @Test
    @Order(5)
    @DisplayName("TC005 - NULL Column Handling")
    void testNullValueHandling() {
        log.info("Running TC005 - NULL Value Handling");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC005.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC005 - PASSED");
    }

    @Test
    @Order(6)
    @DisplayName("TC006 - CLOB/BLOB Large Data")
    void testLargeClobBlob() {
        log.info("Running TC006 - Large CLOB/BLOB Data");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC006.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC006 - PASSED");
    }

    @Test
    @Order(7)
    @DisplayName("TC007 - Special Chars and Emoji")
    void testSpecialCharacters() {
        log.info("Running TC007 - Special Characters and Emoji");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC007.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC007 - PASSED");
    }

    @Test
    @Order(8)
    @DisplayName("TC008 - Minimum Values")
    void testMinimumValues() {
        log.info("Running TC008 - Minimum Values");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC008.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        log.info("TC008 - PASSED");
    }

    @Test
    @Order(9)
    @DisplayName("TC009 - Maximum Values")
    void testMaximumValues() {
        log.info("Running TC009 - Maximum Values");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC009.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        log.info("TC009 - PASSED");
    }

    @Test
    @Order(10)
    @DisplayName("TC010 - Zero Values")
    void testZeroValues() {
        log.info("Running TC010 - Zero Values");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC010.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        log.info("TC010 - PASSED");
    }

    @Test
    @Order(11)
    @DisplayName("TC011 - UNICODE TEST")
    void testUnicodeExtreme() {
        log.info("Running TC011 - Unicode Values");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC011.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        log.info("TC011 - PASSED");
    }

    @Test
    @Order(12)
    @DisplayName("TC012 - OVERFLOW TEST")
    void testOverflowTest() {
        log.info("Running TC012 - OVERFLOW TEST");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC012.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");

        log.info("TC012 - PASSED");
    }

    @Test
    @Order(13)
    @DisplayName("TC013 - UNDERFLOW TEST")
    void testUnderflowTest() {
        log.info("Running TC013 - UNDERFLOW TEST");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC013.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");
        assertThat(response.getErrorSummary()).isNotBlank();

        log.info("TC013 - PASSED");
    }

    @Test
    @Order(14)
    @DisplayName("TC014 - Response Structure Validation")
    void testResponseStructure() {
        log.info("Running TC014 - Response Structure Validation");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC014.json"));

        assertThat(response.getJsonResponse()).isNotNull();
        assertThat(response.getJsonResponse().has("apiName")).isTrue();
        assertThat(response.getJsonResponse().has("targetSystemName")).isTrue();
        assertThat(response.getJsonResponse().has("targetSystemKey")).isTrue();
        assertThat(response.getJsonResponse().has("global_transaction_id")).isTrue();
        assertThat(response.getJsonResponse().has("startTime")).isTrue();
        assertThat(response.getJsonResponse().has("endTime")).isTrue();
        assertThat(response.getJsonResponse().has("elapsedMs")).isTrue();
        assertThat(response.getJsonResponse().has("success")).isTrue();
        assertThat(response.getJsonResponse().has("code")).isTrue();
        assertThat(response.getJsonResponse().has("message")).isTrue();
        assertThat(response.getJsonResponse().has("operations")).isTrue();

        log.info("TC014 - PASSED");
    }

    @Test
    @Order(15)
    @DisplayName("TC015 - Multi-table INSERT (2 tables)")
    void testMultiDataInsert2() {
        log.info("Running TC015 - 멀티테이블 INSERT (2 tables, PostgreSQL)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_multi/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC015.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_m1")).isEqualTo(3);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m2")).isEqualTo(3);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC015 - PASSED");
    }

    @Test
    @Order(16)
    @DisplayName("TC016 - Multi-table INSERT (3 tables)")
    void testMultiDataInsert3() {
        log.info("Running TC016 - 멀티테이블 INSERT (3 tables, PostgreSQL)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_multi/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC016.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m3")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m3")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_m1")).isEqualTo(3);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m2")).isEqualTo(3);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m3")).isEqualTo(3);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC016 - PASSED");
    }

    @Test
    @Order(17)
    @DisplayName("TC017 - Stop On Error")
    void testMultiDataInsertRollback() {
        log.info("Running TC017 - Stop On Error");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_multi/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC017.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m3")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2_m1")).isEqualTo(0);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");

        log.info("TC017 - PASSED");
    }

    @Test
    @Order(18)
    @DisplayName("TC018 - Continue On Error")
    void testMultiDataInsertContinueOnError() {
        log.info("Running TC018 - Continue On Error");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_multi_stop_on_error_false/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC018.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m3")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m2")).isFalse();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m3")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_m1")).isEqualTo(3);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m2")).isEqualTo(0);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m3")).isEqualTo(3);
        assertThat(response.getCode()).isEqualTo("E206");

        log.info("TC018 - PASSED");
    }

    @Test
    @Order(19)
    @DisplayName("TC019 - Master-Detail INSERT")
    void testMasterDetail() {
        log.info("Running TC019 - Master-Detail INSERT Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC019.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d1")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d1")).isEqualTo(2);
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d2")).isEqualTo(2);
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d3")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d3")).isEqualTo(2);

        log.info("TC019 - PASSED");
    }

    // =========================================================
    // TC020 ~ TC030 : 추가 테스트 케이스
    // =========================================================

    @Test
    @Order(20)
    @DisplayName("TC020 - Empty Array INSERT")
    void testEmptyArrayInsert() {
        log.info("Running TC020 - Empty Array Insert");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC020.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC020 - PASSED");
    }

    @Test
    @Order(21)
    @DisplayName("TC021 - Duplicate Key INSERT")
    void testDuplicateKeyInsert() {
        log.info("Running TC021 - Duplicate Key Insert");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC021.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");

        log.info("TC021 - PASSED");
    }

    @Test
    @Order(22)
    @DisplayName("TC022 - Bulk INSERT 1000 rows")
    void testBulkInsert1000() {
        log.info("Running TC022 - Bulk Insert 1000 records");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC022.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1000);

        log.info("TC022 - PASSED");
    }

    @Test
    @Order(23)
    @DisplayName("TC023 - Master-Detail Rollback on Detail Error")
    void testMasterDetailRollbackOnDetailError() {
        log.info("Running TC023 - Master-Detail Rollback on Detail Error");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC023.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E500");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC023 - PASSED");
    }

    @Test
    @Order(24)
    @DisplayName("TC024 - Master 2 rows + Detail multi INSERT")
    void testMasterMultiRecordsWithDetail() {
        log.info("Running TC024 - Master 2 records with Detail");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC024.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d1")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d1")).isEqualTo(4);
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d2")).isEqualTo(3);
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d3")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d3")).isEqualTo(3);

        log.info("TC024 - PASSED");
    }

    @Test
    @Order(25)
    @DisplayName("TC025 - Date/Time Boundary Values")
    void testDateTimeBoundaryValues() {
        log.info("Running TC025 - DateTime Boundary Values");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC025.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);

        log.info("TC025 - PASSED");
    }

    @Test
    @Order(26)
    @DisplayName("TC026 - Large Number INSERT")
    void testColumnLengthOverflow() {
        log.info("Running TC026 - Column Length Overflow");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC026.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");

        log.info("TC026 - PASSED");
    }

    @Test
    @Order(27)
    @DisplayName("TC027 - SQL Injection Literal Storage")
    void testSqlInjectionDefense() {
        log.info("Running TC027 - SQL Injection Defense");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC027.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E500");
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC027 - PASSED");
    }

    @Test
    @Order(28)
    @DisplayName("TC028 - RowCommit StopOnError True")
    void testRowCommitInsert() {
        log.info("Running TC028 - Row Commit Insert");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_rowcommit_stop_on_error_true/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC028.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(10);

        log.info("TC028 - PASSED");
    }

    @Test
    @Order(29)
    @DisplayName("TC029 - RowCommit StopOnError False")
    void testRowCommitInsertErrorStop() {
        log.info("Running TC029 - Row Commit Insert stop_on_error_true");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_rowcommit_stop_on_error_true/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC029.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E206");
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);

        log.info("TC029 - PASSED");
    }

    @Test
    @Order(30)
    @DisplayName("TC030 - Row-level Error Record")
    void testRowCommitInsertErrorContinue() {
        log.info("Running TC030 - Row Commit Insert stop_on_error_false");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_rowcommit_stop_on_error_false/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC030.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E206");
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(8);
        assertThat(response.getRowErrors("insert_tb_user_v2")).isTrue();

        log.info("TC030 - PASSED");
    }

    // =========================================================
    // TC031 ~ TC043 : SELECT 테스트
    // =========================================================

    @Test
    @Order(31)
    @DisplayName("TC031 - Select Single Row by USER_ID")
    void testSelectParam1() {
        log.info("Running TC031 - Select Single Row by USER_ID");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC031.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object userId = response.getResultValue("select_tb_user_v2", 0, "USER_ID");
        assertThat(userId).isNotNull();
        assertThat(userId.toString()).isEqualTo("TC027_SQLI_001");

        log.info("TC031 - PASSED");
    }

    @Test
    @Order(32)
    @DisplayName("TC032 - Select No Row (non-existent ID)")
    void testSelectNoRow() {
        log.info("Running TC032 - Select No Row");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC032.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC032 - PASSED");
    }

    @Test
    @Order(33)
    @DisplayName("TC033 - Select CLOB/BLOB Column Integrity")
    void testSelectClobBlobIntegrity() {
        log.info("Running TC033 - Select CLOB/BLOB Column Integrity");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC033.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object profile = response.getResultValue("select_tb_user_v2", 0, "USER_PROFILE");
        assertThat(profile).isNotNull();
        assertThat(profile.toString()).isNotEmpty();

        Object desc = response.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).isNotEmpty();

        Object metaJson = response.getResultValue("select_tb_user_v2", 0, "META_JSON");
        assertThat(metaJson).isNotNull();
        assertThat(metaJson.toString()).contains("{");

        Object userXml = response.getResultValue("select_tb_user_v2", 0, "USER_XML");
        assertThat(userXml).isNotNull();
        assertThat(userXml.toString()).contains("<user>");

        log.info("TC033 - PASSED");
    }

    @Test
    @Order(34)
    @DisplayName("TC034 - Select Special Chars and Emoji Integrity")
    void testSelectSpecialCharsIntegrity() {
        log.info("Running TC034 - Select Special Characters and Emoji Integrity");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC034.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object desc = response.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).contains("\n");

        log.info("TC034 - PASSED");
    }

    @Test
    @Order(35)
    @DisplayName("TC035 - Select Unicode Data Integrity")
    void testSelectUnicodeIntegrity() {
        log.info("Running TC035 - Select Unicode Data Integrity");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC035.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object name = response.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(name).isNotNull();
        assertThat(name.toString()).isNotEmpty();

        Object code = response.getResultValue("select_tb_user_v2", 0, "USER_CODE");
        assertThat(code).isNotNull();
        assertThat(code.toString()).isNotEmpty();

        log.info("TC035 - PASSED");
    }

    @Test
    @Order(36)
    @DisplayName("TC036 - Select Date Value and Type Check")
    void testSelectDateTypeCheck() {
        log.info("Running TC036 - Select Date Value");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC036.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object createdDate = response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE");
        assertThat(createdDate).isNotNull();
        assertThat(createdDate.toString()).isEqualToIgnoringCase("2026-01-01");

        Object updatedTs = response.getResultValue("select_tb_user_v2", 0, "UPDATED_TS");
        assertThat(updatedTs).isNotNull();
        assertThat(updatedTs.toString()).isEqualToIgnoringCase("2026-01-01T00:00:00.000");

        Object updatedTz = response.getResultValue("select_tb_user_v2", 0, "UPDATED_TZ");
        assertThat(updatedTz).isNotNull();
        assertThat(updatedTz.toString()).isEqualToIgnoringCase("2026-01-01T00:00:00.000+09:00");

        log.info("TC036 - PASSED");
    }

    @Test
    @Order(37)
    @DisplayName("TC037 - Select Nullable Column")
    void testSelectNullableColumn() {
        log.info("Running TC037 - Select Nullable Column (OPTIONAL_COL)");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC037.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object optCol = response.getResultValue("select_tb_user_v2", 0, "OPTIONAL_COL");
        assertThat(optCol).isNull();

        Object userId = response.getResultValue("select_tb_user_v2", 0, "USER_ID");
        assertThat(userId).isNotNull();
        assertThat(userId.toString()).isEqualTo("TC027_SQLI_001");

        log.info("TC037 - PASSED");
    }

    @Test
    @Order(38)
    @DisplayName("TC038 - Select All Rows - Response Structure")
    void testSelectAllRows() {
        log.info("Running TC038 - Select All Rows (no param)");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_no_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC038.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        int rowCount = response.getResultRowCount("select_tb_user_v2");
        assertThat(rowCount).isGreaterThan(0);
        log.info("TC038 - Total rows returned: {}", rowCount);

        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isNotNull();
        assertThat(response.getJsonResponse().has("operations")).isTrue();

        log.info("TC038 - PASSED");
    }

    @Test
    @Order(39)
    @DisplayName("TC039 - Select IN clause, 3 rows all exist")
    void testSelectInNormal() {
        log.info("Running TC039 - Select IN clause, 3 rows all exist");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC039.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(3);

        List<String> returnedIds = getResultFieldValues(response, "select_tb_user_v2", "USER_ID");
        assertThat(returnedIds).containsExactlyInAnyOrder(
            "TC027_SQLI_001", "TC027_SQLI_002", "TC027_SQLI_003"
        );

        log.info("TC039 - PASSED");
    }

    @Test
    @Order(40)
    @DisplayName("TC040 - Select IN clause, partial match")
    void testSelectInPartialMatch() {
        log.info("Running TC040 - Select IN clause, partial match");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC040.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(2);

        List<String> returnedIds = getResultFieldValues(response, "select_tb_user_v2", "USER_ID");
        assertThat(returnedIds).containsExactlyInAnyOrder("TC027_SQLI_001", "TC027_SQLI_002");

        log.info("TC040 - PASSED");
    }

    @Test
    @Order(41)
    @DisplayName("TC041 - Select IN clause, single row")
    void testSelectInSingleRow() {
        log.info("Running TC041 - Select IN clause with single row");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC041.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID"))
            .isEqualTo("TC027_SQLI_001");

        log.info("TC041 - PASSED");
    }

    @Test
    @Order(42)
    @DisplayName("TC042 - Select IN clause, 100 rows")
    void testSelectInBulk100() {
        log.info("Running TC042 - Select IN clause with 100 rows");

        dbinit();
        testBulkInsert100();

        TestConfig.setEndpoint("select_tb_user_v2_postgre_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC042.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        int rowCount = response.getResultRowCount("select_tb_user_v2");
        assertThat(rowCount).isEqualTo(10);

        List<String> returnedIds = getResultFieldValues(response, "select_tb_user_v2", "USER_ID");
        assertThat(returnedIds).contains("ID_1", "ID_5", "ID_10");

        log.info("TC042 - PASSED (rows: {})", rowCount);
    }

    @Test
    @Order(43)
    @DisplayName("TC043 - Select IN clause, empty data")
    void testSelectInEmptyData() {
        log.info("Running TC043 - Select IN clause with empty data array");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC043.json"));

        // PostgreSQL: empty data array returns success with 0 rows
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC043 - PASSED");
    }

    // =========================================================
    // TC045 ~ TC056 : 문자열/데이터 무결성 테스트 (PostgreSQL 전용)
    // =========================================================

    @Test
    @Order(45)
    @DisplayName("TC045 (PostgreSQL) - Multilingual Data INSERT")
    void testMultilingualInsert() {
        log.info("Running TC045 PostgreSQL - Multilingual INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("postgre/TC045.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC045 PostgreSQL - PASSED");
    }

    @Test
    @Order(46)
    @DisplayName("TC046 (PostgreSQL) - HTML/XML Special Chars INSERT")
    void testHtmlXmlSpecialCharsInsert() {
        log.info("Running TC046 PostgreSQL - HTML/XML Special Chars INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("postgre/TC046.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC046 PostgreSQL - PASSED");
    }

    @Test
    @Order(47)
    @DisplayName("TC047 (PostgreSQL) - Negative Values INSERT")
    void testNegativeValuesInsert() {
        log.info("Running TC047 PostgreSQL - Negative Values INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("postgre/TC047.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC047 PostgreSQL - PASSED");
    }

    @Test
    @Order(48)
    @DisplayName("TC048 (PostgreSQL) - Emoji 4-byte Unicode INSERT")
    void testEmojiDataInsert() {
        log.info("Running TC048 PostgreSQL - Emoji 4-byte Unicode INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // PostgreSQL은 기본 UTF-8이므로 이모지 저장 가능
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("postgre/TC048.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC048 PostgreSQL - PASSED");
    }

    @Test
    @Order(49)
    @DisplayName("TC049 (PostgreSQL) - Numeric Precision INSERT")
    void testNumericPrecisionInsert() {
        log.info("Running TC049 PostgreSQL - Numeric Precision INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("postgre/TC049.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC049 PostgreSQL - PASSED");
    }

    @Test
    @Order(50)
    @DisplayName("TC050 (PostgreSQL) - Large TEXT 4000 chars INSERT")
    void testLargeClob4000Insert() {
        log.info("Running TC050 PostgreSQL - Large TEXT 4000 chars INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("postgre/TC050.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC050 PostgreSQL - PASSED");
    }

    // =========================================================
    // TC051 ~ TC056 : Round-Trip (INSERT + SELECT) 무결성 검증
    // =========================================================

    @Test
    @Order(51)
    @DisplayName("TC051 (PostgreSQL) - Multilingual Round-Trip")
    void testMultilingualRoundTrip() {
        log.info("Running TC051 PostgreSQL - Multilingual Round-Trip Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("postgre/TC051.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "ML_99");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // PostgreSQL은 기본 UTF-8이므로 다국어 문자 그대로 저장
        Object userName = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(userName).isNotNull();
        assertThat(userName.toString()).contains("_99");

        log.info("TC051 PostgreSQL - PASSED");
    }

    @Test
    @Order(52)
    @DisplayName("TC052 (PostgreSQL) - Special Chars Round-Trip")
    void testSpecialCharsRoundTrip() {
        log.info("Running TC052 PostgreSQL - Special Characters Round-Trip Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("postgre/TC052.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "HX_99");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).contains("<root>");

        log.info("TC052 PostgreSQL - PASSED");
    }

    @Test
    @Order(53)
    @DisplayName("TC053 (PostgreSQL) - Negative Values Round-Trip")
    void testNegativeValuesRoundTrip() {
        log.info("Running TC053 PostgreSQL - Negative Values Round-Trip Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("postgre/TC053.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "NEG_99");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object age = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_AGE");
        assertThat(age).isNotNull();
        assertThat(Integer.parseInt(age.toString())).isNegative();

        log.info("TC053 PostgreSQL - PASSED");
    }

    @Test
    @Order(54)
    @DisplayName("TC054 (PostgreSQL) - Emoji Round-Trip")
    void testEmojiRoundTrip() {
        log.info("Running TC054 PostgreSQL - Emoji Round-Trip Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("postgre/TC054.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "EM_99");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // PostgreSQL UTF-8: 이모지 원본 그대로 저장/조회 가능
        Object name = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(name).isNotNull();
        assertThat(name.toString()).isNotEmpty();

        log.info("TC054 PostgreSQL - PASSED");
    }

    @Test
    @Order(55)
    @DisplayName("TC055 (PostgreSQL) - Large TEXT 4000 chars Round-Trip")
    void testLargeText4000RoundTrip() {
        log.info("Running TC055 PostgreSQL - Large TEXT 4000 chars Round-Trip Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("postgre/TC055.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "ID_99");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString().length()).isGreaterThanOrEqualTo(4000);

        log.info("TC055 PostgreSQL - PASSED");
    }

    @Test
    @Order(56)
    @DisplayName("TC056 (PostgreSQL) - NULL TEXT Round-Trip")
    void testNullTextRoundTrip() {
        log.info("Running TC056 PostgreSQL - NULL TEXT Round-Trip Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("postgre/TC056.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "ID_1");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNull();

        log.info("TC056 PostgreSQL - PASSED");
    }

    // =========================================================
    // TC059 ~ TC069 : UPDATE / UPSERT / DELETE(조건) / SELECT(다중파라미터)
    // =========================================================

    @Test
    @Order(59)
    @DisplayName("TC059 (PostgreSQL) - UPDATE 단건 성공 및 값 검증")
    void testUpdateSingle() {
        log.info("Running TC059 PostgreSQL - UPDATE Single Row");

        TestConfig.setEndpoint("update_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC059.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("update_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getAffectedRows("update_tb_user_v2")).isEqualTo(1);

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "ID_1");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("Updated_Name_1");
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_AGE")).isEqualTo(99);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("N");

        log.info("TC059 PostgreSQL - PASSED");
    }

    @Test
    @Order(60)
    @DisplayName("TC060 (PostgreSQL) - UPDATE 3건 배치")
    void testUpdateBatch() {
        log.info("Running TC060 PostgreSQL - UPDATE Batch (3 rows)");

        TestConfig.setEndpoint("update_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC060.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("update_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);
        assertThat(response.getAffectedRows("update_tb_user_v2")).isEqualTo(3);

        log.info("TC060 PostgreSQL - PASSED");
    }

    @Test
    @Order(61)
    @DisplayName("TC061 (PostgreSQL) - UPDATE 미존재 행 (affected rows = 0)")
    void testUpdateNoMatch() {
        log.info("Running TC061 PostgreSQL - UPDATE Non-existent Row");

        TestConfig.setEndpoint("update_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC061.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("update_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("update_tb_user_v2")).isEqualTo(0);

        log.info("TC061 PostgreSQL - PASSED");
    }

    @Test
    @Order(62)
    @DisplayName("TC062 (PostgreSQL) - UPSERT INSERT 분기 (신규 행 삽입)")
    void testUpsertInsertBranch() {
        log.info("Running TC062 PostgreSQL - UPSERT INSERT Branch (new row)");

        TestConfig.setEndpoint("upsert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC062.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("upsert_tb_user_v2")).isEqualTo(1);

        log.info("TC062 PostgreSQL - PASSED");
    }

    @Test
    @Order(63)
    @DisplayName("TC063 (PostgreSQL) - UPSERT UPDATE 분기 (기존 행 갱신)")
    void testUpsertUpdateBranch() {
        log.info("Running TC063 PostgreSQL - UPSERT UPDATE Branch (existing row)");

        TestConfig.setEndpoint("upsert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC063.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("upsert_tb_user_v2")).isEqualTo(1);

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "ID_1");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("After_Upsert_1");
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("N");

        log.info("TC063 PostgreSQL - PASSED");
    }

    @Test
    @Order(64)
    @DisplayName("TC064 (PostgreSQL) - UPSERT 혼합 (UPDATE + INSERT 동시)")
    void testUpsertMixed() {
        log.info("Running TC064 PostgreSQL - UPSERT Mixed (UPDATE existing + INSERT new)");

        TestConfig.setEndpoint("upsert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC064.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("upsert_tb_user_v2")).isEqualTo(2);

        log.info("TC064 PostgreSQL - PASSED");
    }

    @Test
    @Order(65)
    @DisplayName("TC065 (PostgreSQL) - DELETE WHERE IN (단건 조건부 삭제)")
    void testDeleteWithParam() {
        log.info("Running TC065 PostgreSQL - DELETE WHERE IN (single row)");

        TestConfig.setEndpoint("delete_tb_user_v2_postgre_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC065.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2_param")).isTrue();
        assertThat(response.getAffectedRows("delete_tb_user_v2_param")).isEqualTo(1);

        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        selectOp.put("data", new ArrayList<>());
        ops.put("select_tb_user_v2", selectOp);
        selectBody.put("operations", ops);

        TestConfig.setEndpoint("select_tb_user_v2_postgre_no_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(2);
        List<String> remainingIds = getResultFieldValues(selectResponse, "select_tb_user_v2", "USER_ID");
        assertThat(remainingIds).containsExactlyInAnyOrder("ID_2", "ID_3");

        log.info("TC065 PostgreSQL - PASSED");
    }

    @Test
    @Order(66)
    @DisplayName("TC066 (PostgreSQL) - DELETE WHERE IN (다건 + 미존재 포함)")
    void testDeleteWithParamMulti() {
        log.info("Running TC066 PostgreSQL - DELETE WHERE IN (multi + non-existent)");

        TestConfig.setEndpoint("delete_tb_user_v2_postgre_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC066.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2_param")).isTrue();
        assertThat(response.getAffectedRows("delete_tb_user_v2_param")).isEqualTo(2);

        log.info("TC066 PostgreSQL - PASSED");
    }

    @Test
    @Order(67)
    @DisplayName("TC067 (PostgreSQL) - SELECT 다중 파라미터 (IS_ACTIVE + USER_AGE, 결과 있음)")
    void testSelectMultiParam() {
        log.info("Running TC067 PostgreSQL - SELECT Multi-Parameter (results found)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        Map<String, Object> setupBody = new HashMap<>();
        Map<String, Object> setupOps = new HashMap<>();
        setupOps.put("delete_tb_user_v2", null);
        Map<String, Object> insertOp = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(buildUserRow("ID_1", "Y", 30, "TC067"));
        rows.add(buildUserRow("ID_2", "Y", 20, "TC067"));
        rows.add(buildUserRow("ID_3", "N", 30, "TC067"));
        insertOp.put("data", rows);
        setupOps.put("insert_tb_user_v2", insertOp);
        setupBody.put("operations", setupOps);
        apiClient.callApi(endpoint, setupBody, null);

        TestConfig.setEndpoint("select_tb_user_v2_postgre_multi_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC067.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID")).isEqualTo("ID_1");
        assertThat(response.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("Y");

        log.info("TC067 PostgreSQL - PASSED");
    }

    @Test
    @Order(68)
    @DisplayName("TC068 (PostgreSQL) - SELECT 다중 파라미터 (USER_AGE > 200, 결과 없음)")
    void testSelectMultiParamNoResult() {
        log.info("Running TC068 PostgreSQL - SELECT Multi-Parameter (no results)");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_multi_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC068.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC068 PostgreSQL - PASSED");
    }

    @Test
    @Order(69)
    @DisplayName("TC069 (PostgreSQL) - UPDATE DECIMAL WHERE (숫자 타입 JSON 전송 → 1 row 갱신)")
    void testUpdateDecimalWhere() {
        log.info("Running TC069 PostgreSQL - UPDATE with DECIMAL WHERE");

        TestConfig.setEndpoint("update_tb_user_v2_postgre_decimal_where/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC069.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("update_tb_user_v2_decimal_where")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getAffectedRows("update_tb_user_v2_decimal_where")).isEqualTo(1);

        Map<String, Object> selectBody = buildSelectBody("select_tb_user_v2", "USER_ID", "DEC_01");

        TestConfig.setEndpoint("select_tb_user_v2_postgre_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("Decimal_Updated");
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("U");

        log.info("TC069 PostgreSQL - PASSED");
    }

    // =========================================================
    // TC070 ~ TC071 : PROCEDURE OUT 파라메터 지원 테스트 (PostgreSQL)
    // =========================================================

    @Test
    @Order(70)
    @DisplayName("TC070 - PostgreSQL OUT 파라메터 프로시저 호출 (정상 복사 후 COPY_CNT=1, RESULT_MSG 반환)")
    void testProcedureOutParam_normal() {
        log.info("Running TC070 - PostgreSQL Procedure with OUT params (normal case)");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_procedure_param_out/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC_POSTGRE086.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.isOperationSuccess("proc_tb_user_v2_out")).isTrue();

        Object copyCnt = response.getResultValue("proc_tb_user_v2_out", 0, "COPY_CNT");
        assertThat(copyCnt).isNotNull();
        assertThat(new java.math.BigDecimal(copyCnt.toString()).intValue()).isEqualTo(1);

        Object resultMsg = response.getResultValue("proc_tb_user_v2_out", 0, "RESULT_MSG");
        assertThat(resultMsg).isNotNull();
        assertThat(resultMsg.toString()).contains("복사 완료");

        log.info("TC070 - PASSED (COPY_CNT={}, RESULT_MSG={})", copyCnt, resultMsg);
    }

    @Test
    @Order(71)
    @DisplayName("TC071 - PostgreSQL OUT 파라메터 프로시저 호출 (존재하지 않는 ID → COPY_CNT=0)")
    void testProcedureOutParam_noData() {
        log.info("Running TC071 - PostgreSQL Procedure with OUT params (no matching row)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_procedure_param_out/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC_POSTGRE087.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("proc_tb_user_v2_out")).isTrue();

        Object copyCnt = response.getResultValue("proc_tb_user_v2_out", 0, "COPY_CNT");
        assertThat(copyCnt).isNotNull();
        assertThat(new java.math.BigDecimal(copyCnt.toString()).intValue()).isEqualTo(0);

        Object resultMsg = response.getResultValue("proc_tb_user_v2_out", 0, "RESULT_MSG");
        assertThat(resultMsg).isNotNull();
        assertThat(resultMsg.toString()).contains("복사 완료");

        log.info("TC071 - PASSED (COPY_CNT={}, RESULT_MSG={})", copyCnt, resultMsg);
    }

    // =========================================================
    // 헬퍼 메서드
    // =========================================================

    private Map<String, Object> buildSelectBody(String operationName, String paramKey, String paramValue) {
        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put(paramKey, paramValue);
        params.add(param);
        selectOp.put("data", params);
        ops.put(operationName, selectOp);
        selectBody.put("operations", ops);
        return selectBody;
    }

    private Map<String, Object> buildUserRow(String userId, String isActive, int age, String tag) {
        Map<String, Object> row = new HashMap<>();
        row.put("USER_ID",      userId);
        row.put("USER_NAME",    "Name_" + userId);
        row.put("USER_NICK",    "NK_" + userId);
        row.put("USER_CODE",    "CD_" + tag);
        row.put("USER_DESC",    "Setup row for " + tag + " userId=" + userId);
        row.put("USER_AGE",     age);
        row.put("USER_COUNT",   100);
        row.put("USER_BIGINT",  922337203685477580L);
        row.put("USER_SCORE",   88.12);
        row.put("USER_RATE",    0.123);
        row.put("USER_RATIO",   1.23);
        row.put("USER_WEIGHT",  75.43);
        row.put("CREATED_DATE", "2026-01-01");
        row.put("UPDATED_TS",   "2026-01-01T00:00:00.000");
        row.put("UPDATED_TZ",   "2026-01-01T00:00:00.000+09:00");
        row.put("UPDATED_LTZ",  "2026-01-01T00:00:00.000+09:00");
        row.put("IS_ACTIVE",    isActive);
        row.put("IS_DELETED",   "N");
        row.put("USER_PROFILE", "QklOQVJZXzE=");
        row.put("META_JSON",    "{\"tag\":\"" + tag + "\"}");
        row.put("TAGS",         tag + ",test");
        row.put("USER_XML",     "<u><tag>" + tag + "</tag></u>");
        row.put("OPTIONAL_COL", tag);
        return row;
    }

    private List<String> getResultFieldValues(ApiResponse response,
                                               String operationName,
                                               String fieldName) {
        List<Map<String, Object>> rows = response.getResultRows(operationName);
        List<String> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object val = row.get(fieldName);
            if (val != null) {
                values.add(val.toString());
            }
        }
        return values;
    }

    private String readJson(String fileName) {
        return TestDataLoader.loadJson(fileName);
    }

    // ==========================================================
    // TC088 : [BUG FIX] TO_CHAR + :: 캐스트 날짜 포맷 내 :MI :SS 오탐 방지
    // ==========================================================

    @Test
    @Order(88)
    @DisplayName("TC088 (PostgreSQL) - [BUG FIX] TO_CHAR 날짜 포맷 내 :MI :SS 및 :: 캐스트가 파라미터로 오인되지 않는다")
    void testPostgreDateFormatParamNotMisread() {
        log.info("Running TC088 PostgreSQL - TO_CHAR date format + :: cast colon fix verification");

        TestConfig.setEndpoint("test_postgresql_date_format/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // Step 1: DELETE + INSERT (TO_CHAR(:UPDATED_TS::timestamp, 'YYYY-MM-DD HH24:MI:SS') 포함)
        //   버그 수정 전1: 'YYYY-MM-DD HH24:MI:SS' 내 :MI :SS 오탐 → ConfigValidationException
        //   버그 수정 전2: :UPDATED_TS::timestamp 의 :: 뒤를 파라미터로 오탐
        //   버그 수정 후: 리터럴 내 콜론 및 :: 캐스트 정상 처리 → INSERT 1건 정상 실행
        ApiResponse insertResp = apiClient.callApi(endpoint, null, readJson("postgre/TC_DATE_FORMAT_POSTGRE.json"));
        assertThat(insertResp.getJsonResponse()).isNotNull();
        assertThat(insertResp.getCode())
            .as("TO_CHAR + :: 오탐 시 YAML 로딩 단계에서 S000 이 아닌 오류 코드 반환")
            .isEqualTo("S000");
        assertThat(insertResp.isOperationSuccess("insert_date_format_test")).isTrue();
        assertThat(insertResp.getAffectedRows("insert_date_format_test")).isEqualTo(1);

        // Step 2: SELECT (TO_CHAR(컬럼, 'YYYY-MM-DD HH24:MI:SS') 포함)
        //   버그 수정 전: SELECT 컬럼 포맷 리터럴 내 :MI :SS 오탐 → YAML 로딩 실패
        //   버그 수정 후: FORMATTED_TS = "YYYY-MM-DD HH24:MI:SS" 형식 문자열 반환
        //              OPTIONAL_COL = INSERT 시 TO_CHAR(::timestamp) 로 저장된 동일 형식 문자열
        ApiResponse selectResp = apiClient.callApi(endpoint, null, readJson("postgre/TC_DATE_FORMAT_POSTGRE_SELECT.json"));
        assertThat(selectResp.getJsonResponse()).isNotNull();
        assertThat(selectResp.getCode()).isEqualTo("S000");
        assertThat(selectResp.isOperationSuccess("select_date_format_test")).isTrue();

        Object formattedTs = selectResp.getResultValue("select_date_format_test", 0, "FORMATTED_TS");
        assertThat(formattedTs).isNotNull();
        assertThat(formattedTs.toString())
            .as("TO_CHAR(UPDATED_TS, 'YYYY-MM-DD HH24:MI:SS') 결과가 올바른 형식이어야 한다")
            .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

        Object optionalCol = selectResp.getResultValue("select_date_format_test", 0, "OPTIONAL_COL");
        assertThat(optionalCol).isNotNull();
        assertThat(optionalCol.toString())
            .as("INSERT 시 TO_CHAR(:UPDATED_TS::timestamp, 'YYYY-MM-DD HH24:MI:SS') 로 저장된 OPTIONAL_COL 검증")
            .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

        log.info("TC088 PostgreSQL - PASSED (FORMATTED_TS={}, OPTIONAL_COL={})", formattedTs, optionalCol);
    }

    private void dbinit() {
        log.info("DB Initialize");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_postgre_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = TestDataLoader.loadJson("DBInit.json");
        apiClient.callApi(endpoint, null, inputJson);
    }
}
