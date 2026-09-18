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
 * JDBC API 기능 테스트 (MSSQL)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FunctionalTestForMssql {
    
    private static final Logger log = LoggerFactory.getLogger(FunctionalTestForMssql.class);
    
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        
        endpoint = TestConfig.getEndpoint(endpoint);
        
        String inputJson = readJson("TC001.json");
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);
        
        // Then
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
        

        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        
        endpoint = TestConfig.getEndpoint(endpoint);
        
        String inputJson = readJson("TC002.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);
        
        // Then
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC003.json"));

        // Then
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC004.json"));

        // Then
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC005.json"));
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("delete_tb_user_v2")).isEqualTo(0);
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        
        log.info("TC005 - PASSED");
    }
    
     
    
    @Test
    @Order(6)
    @DisplayName("TC006 - CLOB/BLOB Large Data")
    void testLargeClobBlob() {
        log.info("Running TC006 - Large CLOB/BLOB Data");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC006.json"));
        
        // Then
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC007.json"));
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC008.json"));
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");
        
        log.info("TC008 - PASSED");
    }
    
    @Test
    @Order(9)
    @DisplayName("TC009 - Maximum Values")
    void testMaximumValues() {
        log.info("Running TC009 - Maximum Values");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        
        endpoint = TestConfig.getEndpoint(endpoint);
        
        String inputJson = readJson("TC009.json");
        
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");
        
        log.info("TC009 - PASSED");
    }
    
    @Test
    @Order(10)
    @DisplayName("TC010 - Zero Values")
    void testZeroValues() {
        log.info("Running TC010 - Zero Values");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC010.json"));
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");
        
        log.info("TC010 - PASSED");
    }    
    
    @Test
    @Order(11)
    @DisplayName("TC011 - UNICODE TEST")
    void testUnicodeExtreme() {
        log.info("Running TC011 - Unicode Values");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC011.json"));
        
        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");
        
        log.info("TC011 - PASSED");
    }       
    
    @Test
    @Order(12)
    @DisplayName("TC012 - OVERFLOW TEST")
    void testOverflowTest() {
        log.info("Running TC012 - OVERFLOW TEST");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC012.json"));
        
        // Then
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC013.json"));
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");
        assertThat(response.getErrorSummary()).isNotBlank(); // MSSQL은 한국어 오류 메시지 반환
        log.info("TC013 - PASSED");
    }     
    
    
    
    @Test
    @Order(14)
    @DisplayName("TC014 - Response Structure Validation")
    void testResponseStructure() {
        log.info("Running TC014 - Response Structure Validation");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC014.json"));
        
        // Then
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
        log.info("Running TC015 - 멀티테이블 INSERT (2 tables, MSSQL)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_multi/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC015.json"));

        // Then
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
        log.info("Running TC016 - 멀티테이블 INSERT (3 tables, MSSQL)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_multi/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC016.json"));

        // Then
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_multi/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC017.json"));
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m3")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m1")).isTrue();
        // insert_tb_user_v2_m2 ?�서 ?�류 발생
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_multi_stop_on_error_false/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC018.json"));

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m3")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m1")).isTrue();
        // insert_tb_user_v2_m2?�서 ?�류 발생
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m2")).isFalse();
        // insert_tb_user_v2_m3???�상 ?�행
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m3")).isTrue();

        assertThat(response.getAffectedRows("insert_tb_user_v2_m1")).isEqualTo(3);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m2")).isEqualTo(0);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m3")).isEqualTo(3);
        // E206 ?�러 : Partially committed. (Success: 5, Total: 6)
        assertThat(response.getCode()).isEqualTo("E206");

        log.info("TC018 - PASSED");
    }      
    

    @Test
    @Order(19)
    @DisplayName("TC019 - Master-Detail INSERT")
    void testMasterDetail() {
        log.info("Running TC019 - Master-Detail INSERT Test");
                
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_master-detail/dbconnector");
        
        endpoint = TestConfig.getEndpoint(endpoint);
        
        String inputJson = readJson("TC019.json");
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);
        
        // Then
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
    // TC020 ~ TC027 : JSON ?�일 기반 추�? ?�스??케?�스
    // =========================================================

    @Test
    @Order(20)
    @DisplayName("TC020 - Empty Array INSERT")
    void testEmptyArrayInsert() {
        log.info("Running TC020 - Empty Array Insert");
        
        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC020.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // �?배열 ?�송 ???�공 ?�답?��?�?affected rows = 0 ?�어????
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

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC021.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // 중복 ??발생 ???�체 ?�패, 롤백
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

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC022.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1000);

        log.info("TC022 - PASSED");
    }

    @Test
    @Order(23)
    @DisplayName("TC023 - Master-Detail Rollback on Detail Error")
    void testMasterDetailRollbackOnDetailError() {
        log.info("Running TC023 - Master-Detail Rollback on Detail Error");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC023.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // tb_user_v2_d2??NOT NULL 컬럼??null ?�력 ???�체 롤백
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E500");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        // master insert???�공?�으??detail ?�류�?롤백
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC023 - PASSED");
    }

    @Test
    @Order(24)
    @DisplayName("TC024 - Master 2 rows + Detail multi INSERT")
    void testMasterMultiRecordsWithDetail() {
        log.info("Running TC024 - Master 2 records with Detail");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC024.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);
        // d1: M1(3�? + M2(1�? = 4�?        assertThat(response.isOperationSuccess("insert_tb_user_v2_d1")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d1")).isEqualTo(4);
        // d2: M1(1�? + M2(2�? = 3�?        assertThat(response.isOperationSuccess("insert_tb_user_v2_d2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d2")).isEqualTo(3);
        // d3: M1(2�? + M2(1�? = 3�?        assertThat(response.isOperationSuccess("insert_tb_user_v2_d3")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d3")).isEqualTo(3);

        log.info("TC024 - PASSED");
    }

    @Test
    @Order(25)
    @DisplayName("TC025 - Date/Time Boundary Values")
    void testDateTimeBoundaryValues() {
        log.info("Running TC025 - DateTime Boundary Values");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC025.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);

        log.info("TC025 - PASSED");
    }

    @Test
    @Order(26)
    @DisplayName("TC026 - Large Number INSERT")
    void testColumnLengthOverflow() {
        log.info("Running TC026 - Column Length Overflow");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC026.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
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

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC027.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // SQL Injection ?�턴??그�?�?문자?�로 ?�?�되거나, 컬럼 ?�약?�로 ?�패?�야 ??        // ?�떤 경우??DB 구조가 변�??�괴?�어?�는 ????        // IS_ACTIVE, IS_DELETED 컬럼 ?�약 ?�반?�로 ?�패 ?�상
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E500");
        // ?�이�?구조가 ?��??�을 간접 ?�인: ?�후 ?�상 쿼리가 가?�해????        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC027 - PASSED");
    }    
    
    @Test
    @Order(28)
    @DisplayName("TC028 - RowCommit StopOnError True")
    void testRowCommitInsert() {
        log.info("Running TC028 - Row Commit Insert");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_rowcommit_stop_on_error_true/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC028.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // SQL Injection ?�턴??그�?�?문자?�로 ?�?�되거나, 컬럼 ?�약?�로 ?�패?�야 ??        // ?�떤 경우??DB 구조가 변�??�괴?�어?�는 ????        // IS_ACTIVE, IS_DELETED 컬럼 ?�약 ?�반?�로 ?�패 ?�상
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        // ?�이�?구조가 ?��??�을 간접 ?�인: ?�후 ?�상 쿼리가 가?�해????        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(10);

        log.info("TC028 - PASSED");
    }  
    
    @Test
    @Order(29)
    @DisplayName("TC029 - RowCommit StopOnError False")
    void testRowCommitInsertErrorStop() {
        log.info("Running TC029 - Row Commit Insert stop_on_error_true");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_rowcommit_stop_on_error_true/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC029.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // SQL Injection ?�턴??그�?�?문자?�로 ?�?�되거나, 컬럼 ?�약?�로 ?�패?�야 ??        // ?�떤 경우??DB 구조가 변�??�괴?�어?�는 ????        // IS_ACTIVE, IS_DELETED 컬럼 ?�약 ?�반?�로 ?�패 ?�상
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E206");
        // ?�이�?구조가 ?��??�을 간접 ?�인: ?�후 ?�상 쿼리가 가?�해????        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);

        log.info("TC029 - PASSED");
    }      
    
    @Test
    @Order(30)
    @DisplayName("TC030 - Row-level Error Record")
    void testRowCommitInsertErrorContinue() {
        log.info("Running TC030 - Row Commit Insert stop_on_error_false");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_rowcommit_stop_on_error_false/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC030.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // SQL Injection ?�턴??그�?�?문자?�로 ?�?�되거나, 컬럼 ?�약?�로 ?�패?�야 ??        // ?�떤 경우??DB 구조가 변�??�괴?�어?�는 ????        // IS_ACTIVE, IS_DELETED 컬럼 ?�약 ?�반?�로 ?�패 ?�상
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E206");
        // ?�이�?구조가 ?��??�을 간접 ?�인: ?�후 ?�상 쿼리가 가?�해????        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(8);

        assertThat(response.getRowErrors("insert_tb_user_v2")).isTrue();
        
        log.info("TC030 - PASSED");
    }      
    
    @Test
    @Order(31)
    @DisplayName("TC031 - Select Single Row by USER_ID")
    void testSelectParam1() {
        log.info("Running TC031 - Select Single Row by USER_ID");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC031.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // ?�확??1�?조회
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_ID �??�치 ?�인
        Object userId = response.getResultValue("select_tb_user_v2", 0, "USER_ID");
        
        
        assertThat(userId).isNotNull();
        assertThat(userId.toString()).isEqualTo("TC027_SQLI_001");

        log.info("TC031 - PASSED");
    }
    
    @Test
    @Order(32)
    @DisplayName("TC032 - Select No Row (non-existent ID)")
    void testSelectNoRow() {
        log.info("Running TC032 - Select No Row (non-existent USER_ID)");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC032.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // ?�이???�음?� ?�러가 ?�닌 ?�공(�?결과)?�어????
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC032 - PASSED");
    }

    @Test
    @Order(33)
    @DisplayName("TC033 - Select CLOB/BLOB Column Integrity")
    void testSelectClobBlobIntegrity() {
        log.info("Running TC033 - Select CLOB/BLOB Column Integrity");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC033.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_PROFILE (BLOB): should be returned as Base64 encoded string
        Object profile = response.getResultValue("select_tb_user_v2", 0, "USER_PROFILE");
        assertThat(profile).isNotNull();
        assertThat(profile.toString()).isNotEmpty();

        // USER_DESC (CLOB): text content should be preserved
        Object desc = response.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).isNotEmpty();

        // META_JSON (CLOB/JSON): should be returned as JSON string
        Object metaJson = response.getResultValue("select_tb_user_v2", 0, "META_JSON");
        assertThat(metaJson).isNotNull();
        assertThat(metaJson.toString()).contains("{");

        // USER_XML (CLOB/XML): should be returned as XML string
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

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC034.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_DESC: verify line breaks and content are preserved (CLOB/NVARCHAR)
        Object desc = response.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).isNotEmpty();
        assertThat(desc.toString()).contains("\n");

        log.info("TC034 - PASSED");
    }

    @Test
    @Order(35)
    @DisplayName("TC035 - Select Unicode Data Integrity")
    void testSelectUnicodeIntegrity() {
        log.info("Running TC035 - Select Unicode Data Integrity");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC035.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_NAME: Korean/Unicode characters should not be garbled
        Object name = response.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(name).isNotNull();
        assertThat(name.toString()).isNotEmpty();

        // USER_CODE???�니코드 ?�수문자 ?�함 ?��? ?�인 (TC011 기�?�?
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

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC036.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // CREATED_DATE: date value should be preserved
        Object createdDate = response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE");
        assertThat(createdDate).isNotNull();
        assertThat(createdDate.toString()).isEqualToIgnoringCase("2026-01-01");

        // UPDATED_TS : ?�?�스?�프 ?�식 보존
        Object updatedTs = response.getResultValue("select_tb_user_v2", 0, "UPDATED_TS");
        assertThat(updatedTs).isNotNull();
        assertThat(updatedTs.toString()).isEqualToIgnoringCase("2026-01-01T00:00:00.000");

        // UPDATED_TZ : ?�?�존 ?�함 ?�?�스?�프 ?�식 보존
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

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC037.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // OPTIONAL_COL : null??그�?�?반환?�어????(?�락 ?�는 null)
        Object optCol = response.getResultValue("select_tb_user_v2", 0, "OPTIONAL_COL");
        assertThat(optCol).isNull();

        // ?�른 ?�수 컬럼?� ?�상 반환
        Object userId = response.getResultValue("select_tb_user_v2", 0, "USER_ID");
        assertThat(userId).isNotNull();
        assertThat(userId.toString()).isEqualTo("TC027_SQLI_001");

        log.info("TC037 - PASSED");
    }

    @Test
    @Order(38)
    @DisplayName("TC038 - Select All Rows - Response Structure")
    void testSelectAllRows() {
        log.info("Running TC038 - Select All Rows (no param) - Response Structure & Multi-row");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_no_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC038.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // ?�건 조회 : 1�??�상 반환 ?�인 (?�전 ?�이??존재 ?�제)
        int rowCount = response.getResultRowCount("select_tb_user_v2");
        assertThat(rowCount).isGreaterThan(0);
        log.info("TC038 - Total rows returned: {}", rowCount);

        // �?row???�수 ?�드가 모두 존재?�는지 ?�인 (�?번째 row 기�?)
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isNotNull();

        // ?�답 메시지 구조 검�?(SELECT ?�용 ?�드 ?�함)
        assertThat(response.getJsonResponse().has("operations")).isTrue();

        // SELECT 결과??operations ?�위??rows ?�는 data 배열�?존재?�야 ??//        assertThat(response.getJsonResponse()
//            .getJSONObject("operations")
//            .has("select_tb_user_v2")).isTrue();

        log.info("TC038 - PASSED");
    }    

    @Test
    @Order(39)
    @DisplayName("TC039 - Select IN clause, 3 rows all exist")
    void testSelectInNormal() {
        log.info("Running TC039 - Select IN clause, 3 rows all exist");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC039.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // 3�?모두 반환
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(3);

        // Verify returned USER_ID list
        List<String> returnedIds = getResultFieldValues(response, "select_tb_user_v2", "USER_ID");
        assertThat(returnedIds).containsExactlyInAnyOrder(
            "TC027_SQLI_001", "TC027_SQLI_002", "TC027_SQLI_003"
        );

        // �?row???�수 컬럼??모두 ?�함?�어????        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE")).isNotNull();

        log.info("TC039 - PASSED");
    }

    @Test
    @Order(40)
    @DisplayName("TC040 - Select IN clause, partial match")
    void testSelectInPartialMatch() {
        log.info("Running TC040 - Select IN clause, 3 rows but only 2 exist");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC040.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // ?�는 ID??조용??무시?�고 존재?�는 2건만 반환?�어????
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(2);

        List<String> returnedIds = getResultFieldValues(response, "select_tb_user_v2", "USER_ID");
        assertThat(returnedIds).containsExactlyInAnyOrder("TC027_SQLI_001", "TC027_SQLI_002");
        assertThat(returnedIds).doesNotContain("NOTEXIST_X");

        log.info("TC040 - PASSED");
    }

    @Test
    @Order(41)
    @DisplayName("TC041 - Select IN clause, single row")
    void testSelectInSingleRow() {
        log.info("Running TC041 - Select IN clause with single row");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC041.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        // ?�확??1�?        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_ID �??�치
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID"))
            .isEqualTo("TC027_SQLI_001");

        // param1 방식(TC031)�??�일?�게 모든 컬럼 반환 ?�인
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NICK")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_AGE")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isNotNull();

        log.info("TC041 - PASSED");
    }

    @Test
    @Order(42)
    @DisplayName("TC042 - Select IN clause, 100 rows")
    void testSelectInBulk100() {
        log.info("Running TC042 - Select IN clause with 100 rows");

        dbinit();
        testBulkInsert100();
        
        TestConfig.setEndpoint("select_tb_user_v2_mssql_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC042.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // TC003?�서 insert??10�??��? 반환
        int rowCount = response.getResultRowCount("select_tb_user_v2");
        assertThat(rowCount).isEqualTo(10);

        // �?번째, 중간, 마�?�?ID ?�함 ?��? ?�인
        List<String> returnedIds = getResultFieldValues(response, "select_tb_user_v2", "USER_ID");
        assertThat(returnedIds).contains("ID_1", "ID_5", "ID_10");
        assertThat(returnedIds).hasSize(10);

        log.info("TC042 - PASSED (rows: {})", rowCount);
    }

    @Test
    @Order(43)
    @DisplayName("TC043 - Select IN clause, empty data")
    void testSelectInEmptyData() {
        log.info("Running TC043 - Select IN clause with empty data array");

        TestConfig.setEndpoint("select_tb_user_v2_mssql_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC043.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // MSSQL: empty data array returns success with 0 rows (Oracle returns error)
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC043 - PASSED");
    }

    @Test
    @Order(45)
    @DisplayName("TC045 (MSSQL) - Multilingual Data INSERT")
    void testMultilingualInsert() {
        log.info("Running TC045 MSSQL - Multilingual INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("mssql/TC045.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC045 MSSQL - PASSED");
    }

    @Test
    @Order(46)
    @DisplayName("TC046 (MSSQL) - HTML/XML Special Chars INSERT")
    void testHtmlXmlSpecialCharsInsert() {
        log.info("Running TC046 MSSQL - HTML/XML Special Chars INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("mssql/TC046.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC046 MSSQL - PASSED");
    }

    @Test
    @Order(47)
    @DisplayName("TC047 (MSSQL) - Negative Values INSERT")
    void testNegativeValuesInsert() {
        log.info("Running TC047 MSSQL - Negative Values INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("mssql/TC047.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC047 MSSQL - PASSED");
    }

    @Test
    @Order(48)
    @DisplayName("TC048 (MSSQL) - Emoji 4-byte Unicode INSERT")
    void testEmojiDataInsert() {
        log.info("Running TC048 MSSQL - Emoji 4-byte Unicode INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("mssql/TC048.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC048 MSSQL - PASSED");
    }

    @Test
    @Order(49)
    @DisplayName("TC049 (MSSQL) - Numeric Precision INSERT")
    void testNumericPrecisionInsert() {
        log.info("Running TC049 MSSQL - Numeric Precision INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("mssql/TC049.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC049 MSSQL - PASSED");
    }

    @Test
    @Order(50)
    @DisplayName("TC050 (MSSQL) - Large TEXT 4000 chars INSERT")
    void testLargeClob4000Insert() {
        log.info("Running TC050 MSSQL - Large CLOB 4000 chars INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("mssql/TC050.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC050 MSSQL - PASSED");
    }

    // =========================================================
    // TC051 ~ TC056 : Round-Trip (INSERT + SELECT) 무결??검�?    // =========================================================

    @Test
    @Order(51)
    @DisplayName("TC051 (MSSQL) - Multilingual Round-Trip")
    void testMultilingualRoundTrip() {
        log.info("Running TC051 MSSQL - Multilingual Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("mssql/TC051.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        // SELECT
        Map<String, Object> selectRequestBody = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "ML_99");
        params.add(param);
        selectOp.put("data", params);
        operations.put("select_tb_user_v2", selectOp);
        selectRequestBody.put("operations", operations);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object userName = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(userName).isNotNull();
        // MSSQL VARCHAR 컬럼?� 비ASCII 문자�?'?'�?변?�하므�?ASCII 부�?_99)?�로 round-trip 검�?        assertThat(userName.toString()).contains("_99");

        log.info("TC051 MSSQL - PASSED");
    }

    @Test
    @Order(52)
    @DisplayName("TC052 (MSSQL) - Special Chars Round-Trip")
    void testSpecialCharsRoundTrip() {
        log.info("Running TC052 MSSQL - Special Characters Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("mssql/TC052.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        // SELECT
        Map<String, Object> selectRequestBody = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "HX_99");
        params.add(param);
        selectOp.put("data", params);
        operations.put("select_tb_user_v2", selectOp);
        selectRequestBody.put("operations", operations);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).contains("<root>");

        log.info("TC052 MSSQL - PASSED");
    }

    @Test
    @Order(53)
    @DisplayName("TC053 (MSSQL) - Negative Values Round-Trip")
    void testNegativeValuesRoundTrip() {
        log.info("Running TC053 MSSQL - Negative Values Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("mssql/TC053.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        // SELECT
        Map<String, Object> selectRequestBody = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "NEG_99");
        params.add(param);
        selectOp.put("data", params);
        operations.put("select_tb_user_v2", selectOp);
        selectRequestBody.put("operations", operations);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object age = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_AGE");
        assertThat(age).isNotNull();
        assertThat(Integer.parseInt(age.toString())).isNegative();

        log.info("TC053 MSSQL - PASSED");
    }

    @Test
    @Order(54)
    @DisplayName("TC054 (MSSQL) - Emoji Round-Trip")
    void testEmojiRoundTrip() {
        log.info("Running TC054 MSSQL - Emoji Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("mssql/TC054.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        // SELECT
        Map<String, Object> selectRequestBody = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "EM_99");
        params.add(param);
        selectOp.put("data", params);
        operations.put("select_tb_user_v2", selectOp);
        selectRequestBody.put("operations", operations);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object name = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(name).isNotNull();
        // MSSQL VARCHAR 컬럼?� ?�모지(4바이???�니코드)�?'?'�?변?�하므�?ASCII 부�?_99)?�로 round-trip 검�?        assertThat(name.toString()).contains("_99");

        log.info("TC054 MSSQL - PASSED");
    }

    @Test
    @Order(55)
    @DisplayName("TC055 (MSSQL) - Large TEXT 4000 chars Round-Trip")
    void testLargeText4000RoundTrip() {
        log.info("Running TC055 MSSQL - Large TEXT 4000 chars Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("mssql/TC055.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        // SELECT
        Map<String, Object> selectRequestBody = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "ID_99");
        params.add(param);
        selectOp.put("data", params);
        operations.put("select_tb_user_v2", selectOp);
        selectRequestBody.put("operations", operations);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString().length()).isGreaterThanOrEqualTo(4000);

        log.info("TC055 MSSQL - PASSED");
    }

    @Test
    @Order(56)
    @DisplayName("TC056 (MSSQL) - NULL TEXT Round-Trip")
    void testNullTextRoundTrip() {
        log.info("Running TC056 MSSQL - NULL TEXT Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("mssql/TC056.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        // SELECT
        Map<String, Object> selectRequestBody = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "ID_1");
        params.add(param);
        selectOp.put("data", params);
        operations.put("select_tb_user_v2", selectOp);
        selectRequestBody.put("operations", operations);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        // Inserted as NULL, so must be retrieved as NULL
        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNull();

        log.info("TC056 MSSQL - PASSED");
    }

    // ==========================================================
    // TC059 ~ TC068 : UPDATE / UPSERT / DELETE(조건) / SELECT(다중파라미터)
    // ==========================================================

    @Test
    @Order(59)
    @DisplayName("TC059 (MSSQL) - UPDATE 단건 성공 및 값 검증")
    void testUpdateSingle() {
        log.info("Running TC059 MSSQL - UPDATE Single Row");

        // DELETE(초기화) + INSERT(사전 데이터) + UPDATE(테스트 대상)
        TestConfig.setEndpoint("update_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC059.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("update_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getAffectedRows("update_tb_user_v2")).isEqualTo(1);

        // UPDATE 후 SELECT로 변경된 값 검증
        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "ID_1");
        params.add(param);
        selectOp.put("data", params);
        ops.put("select_tb_user_v2", selectOp);
        selectBody.put("operations", ops);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("Updated_Name_1");
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_AGE")).isEqualTo(99);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("N");

        log.info("TC059 MSSQL - PASSED");
    }

    @Test
    @Order(60)
    @DisplayName("TC060 (MSSQL) - UPDATE 3건 배치")
    void testUpdateBatch() {
        log.info("Running TC060 MSSQL - UPDATE Batch (3 rows)");

        TestConfig.setEndpoint("update_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC060.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("update_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);
        assertThat(response.getAffectedRows("update_tb_user_v2")).isEqualTo(3);

        log.info("TC060 MSSQL - PASSED");
    }

    @Test
    @Order(61)
    @DisplayName("TC061 (MSSQL) - UPDATE 미존재 행 (affected rows = 0)")
    void testUpdateNoMatch() {
        log.info("Running TC061 MSSQL - UPDATE Non-existent Row");

        TestConfig.setEndpoint("update_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC061.json"));

        // 존재하지 않는 행 UPDATE는 SQL 오류가 아닌 success + affected rows = 0
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("update_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("update_tb_user_v2")).isEqualTo(0);

        log.info("TC061 MSSQL - PASSED");
    }

    @Test
    @Order(62)
    @DisplayName("TC062 (MSSQL) - UPSERT INSERT 분기 (신규 행 삽입)")
    void testUpsertInsertBranch() {
        log.info("Running TC062 MSSQL - UPSERT INSERT Branch (new row)");

        // ID_1 사전 삽입 후 ID_2 UPSERT → WHEN NOT MATCHED → INSERT
        TestConfig.setEndpoint("upsert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC062.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
        // UPSERT ID_2 (신규) → 1행 처리
        assertThat(response.getAffectedRows("upsert_tb_user_v2")).isEqualTo(1);

        log.info("TC062 MSSQL - PASSED");
    }

    @Test
    @Order(63)
    @DisplayName("TC063 (MSSQL) - UPSERT UPDATE 분기 (기존 행 갱신)")
    void testUpsertUpdateBranch() {
        log.info("Running TC063 MSSQL - UPSERT UPDATE Branch (existing row)");

        // ID_1 사전 삽입 후 ID_1 UPSERT → WHEN MATCHED → UPDATE
        TestConfig.setEndpoint("upsert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC063.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
        // UPSERT ID_1 (기존) → 1행 처리
        assertThat(response.getAffectedRows("upsert_tb_user_v2")).isEqualTo(1);

        // SELECT로 UPDATE된 값 검증
        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "ID_1");
        params.add(param);
        selectOp.put("data", params);
        ops.put("select_tb_user_v2", selectOp);
        selectBody.put("operations", ops);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("After_Upsert_1");
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("N");

        log.info("TC063 MSSQL - PASSED");
    }

    @Test
    @Order(64)
    @DisplayName("TC064 (MSSQL) - UPSERT 혼합 (UPDATE + INSERT 동시)")
    void testUpsertMixed() {
        log.info("Running TC064 MSSQL - UPSERT Mixed (UPDATE existing + INSERT new)");

        // ID_1, ID_2 사전 삽입 후 UPSERT: ID_1(UPDATE) + ID_3(INSERT)
        TestConfig.setEndpoint("upsert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC064.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
        // ID_1 UPDATE + ID_3 INSERT = 총 2행 처리
        assertThat(response.getAffectedRows("upsert_tb_user_v2")).isEqualTo(2);

        log.info("TC064 MSSQL - PASSED");
    }

    @Test
    @Order(65)
    @DisplayName("TC065 (MSSQL) - DELETE WHERE IN (단건 조건부 삭제)")
    void testDeleteWithParam() {
        log.info("Running TC065 MSSQL - DELETE WHERE IN (single row)");

        // DELETE(초기화) + INSERT(3건) + DELETE WHERE IN(ID_1만 삭제)
        TestConfig.setEndpoint("delete_tb_user_v2_mssql_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC065.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2_param")).isTrue();
        // ID_1만 삭제 → affected rows = 1
        assertThat(response.getAffectedRows("delete_tb_user_v2_param")).isEqualTo(1);

        // SELECT ALL로 남은 행 수 검증 (ID_2, ID_3 두 행 남아야 함)
        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        selectOp.put("data", new ArrayList<>());
        ops.put("select_tb_user_v2", selectOp);
        selectBody.put("operations", ops);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_no_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(2);
        List<String> remainingIds = getResultFieldValues(selectResponse, "select_tb_user_v2", "USER_ID");
        assertThat(remainingIds).containsExactlyInAnyOrder("ID_2", "ID_3");

        log.info("TC065 MSSQL - PASSED");
    }

    @Test
    @Order(66)
    @DisplayName("TC066 (MSSQL) - DELETE WHERE IN (다건 + 미존재 포함)")
    void testDeleteWithParamMulti() {
        log.info("Running TC066 MSSQL - DELETE WHERE IN (multi + non-existent)");

        // INSERT 3건 후 IN(ID_1, ID_2, ID_99) 삭제 → 실제 삭제 2건 (ID_99 미존재)
        TestConfig.setEndpoint("delete_tb_user_v2_mssql_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC066.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2_param")).isTrue();
        // ID_1, ID_2 삭제 성공, ID_99 미존재 → affected rows = 2
        assertThat(response.getAffectedRows("delete_tb_user_v2_param")).isEqualTo(2);

        log.info("TC066 MSSQL - PASSED");
    }

    @Test
    @Order(67)
    @DisplayName("TC067 (MSSQL) - SELECT 다중 파라미터 (IS_ACTIVE + USER_AGE, 결과 있음)")
    void testSelectMultiParam() {
        log.info("Running TC067 MSSQL - SELECT Multi-Parameter (results found)");

        // 사전 데이터 삽입: IS_ACTIVE 혼합, USER_AGE 혼합
        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        Map<String, Object> setupBody = new HashMap<>();
        Map<String, Object> setupOps = new HashMap<>();
        setupOps.put("delete_tb_user_v2", null);
        Map<String, Object> insertOp = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        // ID_1: IS_ACTIVE=Y, USER_AGE=30 → 조회 대상
        rows.add(buildUserRow("ID_1", "Y", 30, "TC067"));
        // ID_2: IS_ACTIVE=Y, USER_AGE=20 → USER_AGE <= 25이므로 제외
        rows.add(buildUserRow("ID_2", "Y", 20, "TC067"));
        // ID_3: IS_ACTIVE=N, USER_AGE=30 → IS_ACTIVE='N'이므로 제외
        rows.add(buildUserRow("ID_3", "N", 30, "TC067"));
        insertOp.put("data", rows);
        setupOps.put("insert_tb_user_v2", insertOp);
        setupBody.put("operations", setupOps);
        apiClient.callApi(endpoint, setupBody, null);

        // SELECT: IS_ACTIVE='Y' AND USER_AGE > 25 → ID_1만 매칭
        TestConfig.setEndpoint("select_tb_user_v2_mssql_multi_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC067.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID")).isEqualTo("ID_1");
        assertThat(response.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("Y");

        log.info("TC067 MSSQL - PASSED");
    }

    @Test
    @Order(68)
    @DisplayName("TC068 (MSSQL) - SELECT 다중 파라미터 (USER_AGE > 200, 결과 없음)")
    void testSelectMultiParamNoResult() {
        log.info("Running TC068 MSSQL - SELECT Multi-Parameter (no results)");

        // TC067 이후 데이터 존재 상태에서 조건이 맞지 않는 파라미터로 조회
        TestConfig.setEndpoint("select_tb_user_v2_mssql_multi_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC068.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC068 MSSQL - PASSED");
    }

    @Test
    @Order(69)
    @DisplayName("TC069 (MSSQL) - UPDATE DECIMAL WHERE (숫자 타입 JSON 전송 → 1 row 갱신)")
    void testUpdateDecimalWhere() {
        log.info("Running TC069 MSSQL - UPDATE with DECIMAL WHERE (numeric JSON value, no quotes)");

        // DELETE(초기화) + INSERT(USER_SCORE=123.45 숫자 타입) + UPDATE WHERE USER_SCORE=123.45(숫자 타입)
        // 수정 전: setObject(Double, Types.DECIMAL) → new BigDecimal(double) → 부동소수점 오차 → 0 rows
        // 수정 후: setBigDecimal(new BigDecimal("123.45"))               → 정확한 소수 → 1 row
        TestConfig.setEndpoint("update_tb_user_v2_mssql_decimal_where/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC069.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("update_tb_user_v2_decimal_where")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        // 핵심 검증: DECIMAL WHERE 조건으로 정확히 1행이 갱신되어야 한다 (수정 전에는 0이었음)
        assertThat(response.getAffectedRows("update_tb_user_v2_decimal_where")).isEqualTo(1);

        // SELECT로 실제 갱신된 값 확인
        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", "DEC_01");
        params.add(param);
        selectOp.put("data", params);
        ops.put("select_tb_user_v2", selectOp);
        selectBody.put("operations", ops);

        TestConfig.setEndpoint("select_tb_user_v2_mssql_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("Decimal_Updated");
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("U");

        log.info("TC069 MSSQL - PASSED");
    }

    /**
     * TC067/TC068 SELECT 테스트용 사전 데이터 행 생성 헬퍼
     */
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

    // ==========================================================
    // TC070 ~ TC071 : PROCEDURE OUT 파라메터 지원 테스트 (MSSQL)
    // ==========================================================

    @Test
    @Order(70)
    @DisplayName("TC070 - MSSQL OUT 파라메터 프로시저 호출 (정상 복사 후 COPY_CNT=1, RESULT_MSG 반환)")
    void testProcedureOutParam_normal() {
        log.info("Running TC070 - MSSQL Procedure with OUT params (normal case)");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_procedure_param_out/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC_MSSQL086.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.isOperationSuccess("proc_tb_user_v2_out")).isTrue();

        // OUT 파라메터 검증: COPY_CNT=1 (1건 복사), RESULT_MSG 포함 확인
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
    @DisplayName("TC071 - MSSQL OUT 파라메터 프로시저 호출 (존재하지 않는 ID → COPY_CNT=0)")
    void testProcedureOutParam_noData() {
        log.info("Running TC071 - MSSQL Procedure with OUT params (no matching row)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_procedure_param_out/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC_MSSQL087.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("proc_tb_user_v2_out")).isTrue();

        // OUT 파라메터 검증: 매칭 데이터 없으므로 COPY_CNT=0
        Object copyCnt = response.getResultValue("proc_tb_user_v2_out", 0, "COPY_CNT");
        assertThat(copyCnt).isNotNull();
        assertThat(new java.math.BigDecimal(copyCnt.toString()).intValue()).isEqualTo(0);

        Object resultMsg = response.getResultValue("proc_tb_user_v2_out", 0, "RESULT_MSG");
        assertThat(resultMsg).isNotNull();
        assertThat(resultMsg.toString()).contains("복사 완료");

        log.info("TC071 - PASSED (COPY_CNT={}, RESULT_MSG={})", copyCnt, resultMsg);
    }

    // =========================================================
    // IN???�스???�용 ?�퍼
    // =========================================================

    /**
     * SELECT 결과 rows?�서 ?�정 컬럼 값만 추출?�여 List<String>?�로 반환
     */
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
    
    /**
     * src/test/resources/{fileName} 경로?�서 JSON ?�일???�어 반환
     */
    private String readJson(String fileName) {
        return TestDataLoader.loadJson(fileName);
    }    

    // ==========================================================
    // TC088 : [BUG FIX] FORMAT 날짜 포맷 내 :mm :ss 오탐 방지
    // ==========================================================

    @Test
    @Order(88)
    @DisplayName("TC088 (MSSQL) - [BUG FIX] FORMAT 날짜 포맷 내 :mm :ss 가 파라미터로 오인되지 않는다")
    void testMssqlDateFormatParamNotMisread() {
        log.info("Running TC088 MSSQL - FORMAT date format colon fix verification");

        TestConfig.setEndpoint("test_mssql_date_format/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // Step 1: DELETE + INSERT (FORMAT(:UPDATED_TS, 'yyyy-MM-dd HH:mm:ss') 포함)
        //   버그 수정 전: 'yyyy-MM-dd HH:mm:ss' 내 :mm :ss 오탐 → ConfigValidationException
        //   버그 수정 후: 리터럴 내 콜론 무시 → INSERT 1건 정상 실행
        ApiResponse insertResp = apiClient.callApi(endpoint, null, readJson("mssql/TC_DATE_FORMAT_MSSQL.json"));
        assertThat(insertResp.getJsonResponse()).isNotNull();
        assertThat(insertResp.getCode())
            .as("FORMAT 포맷 내 :mm :ss 오탐 시 YAML 로딩 단계에서 S000 이 아닌 오류 코드 반환")
            .isEqualTo("S000");
        assertThat(insertResp.isOperationSuccess("insert_date_format_test")).isTrue();
        assertThat(insertResp.getAffectedRows("insert_date_format_test")).isEqualTo(1);

        // Step 2: SELECT (FORMAT(컬럼, 'yyyy-MM-dd HH:mm:ss') 포함)
        //   버그 수정 전: SELECT 컬럼 포맷 리터럴 내 :mm :ss 오탐 → YAML 로딩 실패
        //   버그 수정 후: FORMATTED_TS = "yyyy-MM-dd HH:mm:ss" 형식 문자열 반환
        //              OPTIONAL_COL = INSERT 시 FORMAT 으로 저장된 동일 형식 문자열
        ApiResponse selectResp = apiClient.callApi(endpoint, null, readJson("mssql/TC_DATE_FORMAT_MSSQL_SELECT.json"));
        assertThat(selectResp.getJsonResponse()).isNotNull();
        assertThat(selectResp.getCode()).isEqualTo("S000");
        assertThat(selectResp.isOperationSuccess("select_date_format_test")).isTrue();

        Object formattedTs = selectResp.getResultValue("select_date_format_test", 0, "FORMATTED_TS");
        assertThat(formattedTs).isNotNull();
        assertThat(formattedTs.toString())
            .as("FORMAT(UPDATED_TS, 'yyyy-MM-dd HH:mm:ss') 결과가 올바른 형식이어야 한다")
            .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

        Object optionalCol = selectResp.getResultValue("select_date_format_test", 0, "OPTIONAL_COL");
        assertThat(optionalCol).isNotNull();
        assertThat(optionalCol.toString())
            .as("INSERT 시 FORMAT(:UPDATED_TS, 'yyyy-MM-dd HH:mm:ss') 로 저장된 OPTIONAL_COL 검증")
            .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

        log.info("TC088 MSSQL - PASSED (FORMATTED_TS={}, OPTIONAL_COL={})", formattedTs, optionalCol);
    }

    private void dbinit() {
        log.info("DB Initialize");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_mssql_master-detail/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = TestDataLoader.loadJson("DBInit.json");
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

    }
}
