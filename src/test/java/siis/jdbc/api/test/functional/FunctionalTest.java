package siis.jdbc.api.test.functional;

import siis.jdbc.api.test.core.ApiResponse;
import siis.jdbc.api.test.core.JdbcApiClient;
import siis.jdbc.api.test.core.TestConfig;
import siis.jdbc.api.test.data.TestDataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.*;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * JDBC API 기능 테스트
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FunctionalTest {
    
    private static final Logger log = LoggerFactory.getLogger(FunctionalTest.class);
    
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
    @DisplayName("TC001 - 기본 단일 레코드 INSERT")
    void testDeleteInsert() {
        log.info("Running TC001 - Basic Insert Test");
               
        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        
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
    @DisplayName("TC002 - 기본 단일 레코드 INSERT 후 Procedure")
    void testDeleteInsertProcedure() {
        log.info("Running TC002 - Delete-Insert-Procedure");
        

        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        
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
    @DisplayName("TC003 - 대량 INSERT (10건)")
    void testBulkInsert100() {
        log.info("Running TC003 - Bulk Insert 10 records");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
    @DisplayName("TC004 - DELETE 후 INSERT 트랜잭션")
    void testDeleteInsertTransaction() {
        log.info("Running TC004 - Delete-Insert Transaction");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC004.json"));

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        // TC003이 10건 삽입 → TC004 DELETE ALL 로 10건 삭제
        assertThat(response.getAffectedRows("delete_tb_user_v2")).isEqualTo(10);
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        log.info("TC004 - PASSED");
    }
 
    @Test
    @Order(5)
    @DisplayName("TC005 - NULL 값 처리")
    void testNullValueHandling() {
        log.info("Running TC005 - NULL Value Handling");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
    @DisplayName("TC006 - CLOB/BLOB 대용량 데이터")
    void testLargeClobBlob() {
        log.info("Running TC006 - Large CLOB/BLOB Data");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
    @DisplayName("TC007 - 특수문자 및 이모지 처리")
    void testSpecialCharacters() {
        log.info("Running TC007 - Special Characters and Emoji");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
    @DisplayName("TC008 - 최소값")
    void testMinimumValues() {
        log.info("Running TC008 - Minimum Values");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC008.json"));

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        // SELECT: 0값이 NULL이 아닌 실제 0으로 저장되었는지 검증
        ApiResponse sr008 = selectByUserId("ID_1");
        assertThat(sr008.isSuccess()).isTrue();
        assertThat(sr008.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(sr008.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("Min_1");
        assertThat(new BigDecimal(sr008.getResultValue("select_tb_user_v2", 0, "USER_SCORE").toString()).compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(new BigDecimal(sr008.getResultValue("select_tb_user_v2", 0, "USER_RATE").toString()).compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(new BigDecimal(sr008.getResultValue("select_tb_user_v2", 0, "USER_RATIO").toString()).compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(new BigDecimal(sr008.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT").toString()).compareTo(BigDecimal.ZERO)).isEqualTo(0);

        log.info("TC008 - PASSED");
    }
    
    @Test
    @Order(9)
    @DisplayName("TC009 - 최대값")
    void testMaximumValues() {
        log.info("Running TC009 - Maximum Values");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC009.json"));

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        // SELECT: 최대값이 손상 없이 저장되었는지 검증 (USER_ID=1 은 숫자이지만 VARCHAR2에 "1"로 저장)
        ApiResponse sr009 = selectByUserId("1");
        assertThat(sr009.isSuccess()).isTrue();
        assertThat(sr009.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        // 한글 최대 길이 USER_NAME 검증
        assertThat(sr009.getResultStringValue("select_tb_user_v2", 0, "USER_NAME")).startsWith("가가가가가");
        // 정수 최대값 검증
        assertThat(new BigDecimal(sr009.getResultValue("select_tb_user_v2", 0, "USER_AGE").toString()).intValue()).isEqualTo(999);
        assertThat(new BigDecimal(sr009.getResultValue("select_tb_user_v2", 0, "USER_COUNT").toString()).longValue()).isEqualTo(2147483647L);
        // 대형 소수값: 99999999999 초과인지 (Oracle NUMBER 정밀도 범위 내)
        BigDecimal score09 = new BigDecimal(sr009.getResultValue("select_tb_user_v2", 0, "USER_SCORE").toString());
        assertThat(score09.compareTo(new BigDecimal("99999999999"))).isGreaterThan(0);
        // 초대형 값 (1e+37 이상): non-null 및 양수 확인
        assertThat(new BigDecimal(sr009.getResultValue("select_tb_user_v2", 0, "USER_RATE").toString()).signum()).isEqualTo(1);
        assertThat(new BigDecimal(sr009.getResultValue("select_tb_user_v2", 0, "USER_RATIO").toString()).signum()).isEqualTo(1);
        assertThat(new BigDecimal(sr009.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT").toString()).signum()).isEqualTo(1);

        log.info("TC009 - PASSED");
    }
    
    @Test
    @Order(10)
    @DisplayName("TC010 - ZERO값")
    void testZeroValues() {
        log.info("Running TC010 - Zero Values");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC010.json"));

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        // SELECT: ZERO값이 NULL이 아닌 실제 0으로 저장되었는지 검증
        ApiResponse sr010 = selectByUserId("ID_1");
        assertThat(sr010.isSuccess()).isTrue();
        assertThat(sr010.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(sr010.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("Zero_1");
        assertThat(new BigDecimal(sr010.getResultValue("select_tb_user_v2", 0, "USER_SCORE").toString()).compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(new BigDecimal(sr010.getResultValue("select_tb_user_v2", 0, "USER_RATE").toString()).compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(new BigDecimal(sr010.getResultValue("select_tb_user_v2", 0, "USER_RATIO").toString()).compareTo(BigDecimal.ZERO)).isEqualTo(0);
        assertThat(new BigDecimal(sr010.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT").toString()).compareTo(BigDecimal.ZERO)).isEqualTo(0);

        log.info("TC010 - PASSED");
    }    
    
    @Test
    @Order(11)
    @DisplayName("TC011 - UNICODE TEST")
    void testUnicodeExtreme() {
        log.info("Running TC011 - Unicode Values");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC012.json"));
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");
        assertThat(response.getErrorSummary()).contains("Overflow");
        log.info("TC012 - PASSED");
    }   
    
    @Test
    @Order(13)
    @DisplayName("TC013 - UNDERFLOW TEST")
    void testUnderflowTest() {
        log.info("Running TC013 - UNDERFLOW TEST");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC013.json"));
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");
        assertThat(response.getErrorSummary()).contains("Overflow");
        log.info("TC013 - PASSED");
    }     
    
    
    
    @Test
    @Order(14)
    @DisplayName("TC014 - 응답메시지구조검증")
    void testResponseStructure() {
        log.info("Running TC014 - 응답메시지구조검증");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
    @DisplayName("TC015 - 멀티테이블 INSERT (2)")
    void testMultiDataInsert2() {
        log.info("Running TC015 - 멀티테이블 INSERT");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_multi/dbconnector");
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
    @DisplayName("TC016 - 멀티테이블 INSERT (3)")
    void testMultiDataInsert3() {
        log.info("Running TC016 - 멀티테이블 INSERT");
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_multi/dbconnector");
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_multi/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC017.json"));
        
        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m3")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m1")).isTrue();
        // insert_tb_user_v2_m2 에서 오류 발생
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
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_multi_stop_on_error_false/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC018.json"));

        // Then
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m1")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m2")).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2_m3")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m1")).isTrue();
        // insert_tb_user_v2_m2 에서 오류 발생
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m2")).isFalse();
        // insert_tb_user_v2_m3 는 정상 수행 (continue on error)
        assertThat(response.isOperationSuccess("insert_tb_user_v2_m3")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_m1")).isEqualTo(3);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m2")).isEqualTo(0);
        assertThat(response.getAffectedRows("insert_tb_user_v2_m3")).isEqualTo(3);
        // E206 에러 : Partially committed.
        assertThat(response.getCode()).isEqualTo("E206");

        log.info("TC018 - PASSED");
    }      
    

    @Test
    @Order(19)
    @DisplayName("TC019 - Master-Detail INSERT")
    void testMasterDetail() {
        log.info("Running TC019 - Master-Detail INSERT Test");
                
        TestConfig.setEndpoint("delete_insert_tb_user_v2_master-detail/dbconnector");
        
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
    // TC020 ~ TC027 : JSON 파일 기반 추가 테스트 케이스
    // =========================================================

    @Test
    @Order(20)
    @DisplayName("TC020 - 빈 배열 INSERT (Empty Array)")
    void testEmptyArrayInsert() {
        log.info("Running TC020 - Empty Array Insert");
        
        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC020.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // 빈 배열 전송 시 성공 응답이지만 affected rows = 0 이어야 함
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC020 - PASSED");
    }

    @Test
    @Order(21)
    @DisplayName("TC021 - 중복 키 INSERT (Duplicate Key Error)")
    void testDuplicateKeyInsert() {
        log.info("Running TC021 - Duplicate Key Insert");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC021.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // 중복 키 발생 시 전체 실패, 롤백
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);
        assertThat(response.getCode()).isEqualTo("E500");

        log.info("TC021 - PASSED");
    }

    @Test
    @Order(22)
    @DisplayName("TC022 - 대량 INSERT (1000건)")
    void testBulkInsert1000() {
        log.info("Running TC022 - Bulk Insert 1000 records");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
    @DisplayName("TC023 - Master-Detail에서 Detail NULL 오류 시 롤백")
    void testMasterDetailRollbackOnDetailError() {
        log.info("Running TC023 - Master-Detail Rollback on Detail Error");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC023.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // tb_user_v2_d2의 NOT NULL 컬럼에 null 입력 → 전체 롤백
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E500");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        // master insert는 성공했으나 detail 오류로 롤백
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC023 - PASSED");
    }

    @Test
    @Order(24)
    @DisplayName("TC024 - Master 2건 + 각 Detail 다건 INSERT")
    void testMasterMultiRecordsWithDetail() {
        log.info("Running TC024 - Master 2 records with Detail");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_master-detail/dbconnector");
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
        // d1: M1(3건) + M2(1건) = 4건
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d1")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d1")).isEqualTo(4);
        // d2: M1(1건) + M2(2건) = 3건
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d2")).isEqualTo(3);
        // d3: M1(2건) + M2(1건) = 3건
        assertThat(response.isOperationSuccess("insert_tb_user_v2_d3")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2_d3")).isEqualTo(3);

        log.info("TC024 - PASSED");
    }

    @Test
    @Order(25)
    @DisplayName("TC025 - 날짜/시간 경계값 (최소/최대/윤년)")
    void testDateTimeBoundaryValues() {
        log.info("Running TC025 - DateTime Boundary Values");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
    @DisplayName("TC026 - 컬럼 길이 초과 (String Overflow)")
    void testColumnLengthOverflow() {
        log.info("Running TC026 - Column Length Overflow");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
    @DisplayName("TC027 - SQL Injection 방어")
    void testSqlInjectionDefense() {
        log.info("Running TC027 - SQL Injection Defense");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC027.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // SQL Injection 패턴이 그대로 문자열로 저장되거나, 컬럼 제약으로 실패해야 함
        // 어떤 경우든 DB 구조가 변경/파괴되어서는 안 됨
        // IS_ACTIVE, IS_DELETED 컬럼 제약 위반으로 실패 예상
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E500");
        // 테이블 구조가 유지됨을 간접 확인: 이후 정상 쿼리가 가능해야 함
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC027 - PASSED");
    }    
    
    @Test
    @Order(28)
    @DisplayName("TC028 - Row Commit Insert")
    void testRowCommitInsert() {
        log.info("Running TC028 - Row Commit Insert");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_rowcommit_stop_on_error_true/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC028.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // SQL Injection 패턴이 그대로 문자열로 저장되거나, 컬럼 제약으로 실패해야 함
        // 어떤 경우든 DB 구조가 변경/파괴되어서는 안 됨
        // IS_ACTIVE, IS_DELETED 컬럼 제약 위반으로 실패 예상
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        // 테이블 구조가 유지됨을 간접 확인: 이후 정상 쿼리가 가능해야 함
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(10);

        log.info("TC028 - PASSED");
    }  
    
    @Test
    @Order(29)
    @DisplayName("TC029 - Row Commit Insert stop_on_error_true")
    void testRowCommitInsertErrorStop() {
        log.info("Running TC029 - Row Commit Insert stop_on_error_true");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_rowcommit_stop_on_error_true/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC029.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // SQL Injection 패턴이 그대로 문자열로 저장되거나, 컬럼 제약으로 실패해야 함
        // 어떤 경우든 DB 구조가 변경/파괴되어서는 안 됨
        // IS_ACTIVE, IS_DELETED 컬럼 제약 위반으로 실패 예상
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E206");
        // 테이블 구조가 유지됨을 간접 확인: 이후 정상 쿼리가 가능해야 함
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);

        log.info("TC029 - PASSED");
    }      
    
    @Test
    @Order(30)
    @DisplayName("TC030 - Row Commit Insert stop_on_error_false")
    void testRowCommitInsertErrorContinue() {
        log.info("Running TC030 - Row Commit Insert stop_on_error_false");

        dbinit();
        
        TestConfig.setEndpoint("delete_insert_tb_user_v2_rowcommit_stop_on_error_false/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = readJson("TC030.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // SQL Injection 패턴이 그대로 문자열로 저장되거나, 컬럼 제약으로 실패해야 함
        // 어떤 경우든 DB 구조가 변경/파괴되어서는 안 됨
        // IS_ACTIVE, IS_DELETED 컬럼 제약 위반으로 실패 예상
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("E206");
        // 테이블 구조가 유지됨을 간접 확인: 이후 정상 쿼리가 가능해야 함
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(8);

        assertThat(response.getRowErrors("insert_tb_user_v2")).isTrue();
        
        log.info("TC030 - PASSED");
    }      
    
    @Test
    @Order(31)
    @DisplayName("TC031 - 단건 조회 (정상 USER_ID)")
    void testSelectParam1() {
        log.info("Running TC031 - Select Single Row by USER_ID");

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC031.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // 정확히 1건 조회
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_ID 값 일치 확인
        Object userId = response.getResultValue("select_tb_user_v2", 0, "USER_ID");
        
        
        assertThat(userId).isNotNull();
        assertThat(userId.toString()).isEqualTo("TC027_SQLI_001");

        log.info("TC031 - PASSED");
    }
    
    @Test
    @Order(32)
    @DisplayName("TC032 - 단건 조회 (존재하지 않는 USER_ID → 0건)")
    void testSelectNoRow() {
        log.info("Running TC032 - Select No Row (non-existent USER_ID)");

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC032.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // 데이터 없음은 에러가 아닌 성공(빈 결과)이어야 함
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC032 - PASSED");
    }

    @Test
    @Order(33)
    @DisplayName("TC033 - CLOB/BLOB 컬럼 조회 및 값 무결성 검증")
    void testSelectClobBlobIntegrity() {
        log.info("Running TC033 - Select CLOB/BLOB Column Integrity");

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC033.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_PROFILE (BLOB) : Base64 인코딩된 문자열로 반환되어야 함
        Object profile = response.getResultValue("select_tb_user_v2", 0, "USER_PROFILE");
        assertThat(profile).isNotNull();
        assertThat(profile.toString()).isNotEmpty();

        // USER_DESC (CLOB) : 텍스트 내용이 보존되어야 함
        Object desc = response.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).isNotEmpty();

        // META_JSON (CLOB/JSON) : JSON 형식의 문자열로 반환되어야 함
        Object metaJson = response.getResultValue("select_tb_user_v2", 0, "META_JSON");
        assertThat(metaJson).isNotNull();
        assertThat(metaJson.toString()).contains("{");

        // USER_XML (CLOB/XMLTYPE) : XML 형식의 문자열로 반환되어야 함
        Object userXml = response.getResultValue("select_tb_user_v2", 0, "USER_XML");
        assertThat(userXml).isNotNull();
        assertThat(userXml.toString()).contains("<user>");

        log.info("TC033 - PASSED");
    }

    @Test
    @Order(34)
    @DisplayName("TC034 - 특수문자/이모지 데이터 조회 및 값 무결성 검증")
    void testSelectSpecialCharsIntegrity() {
        log.info("Running TC034 - Select Special Characters and Emoji Integrity");

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC034.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_DESC : 줄바꿈, 탭, 이모지 보존 확인
        Object desc = response.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).contains("🚀");
        assertThat(desc.toString()).contains("\n");

        log.info("TC034 - PASSED");
    }

    @Test
    @Order(35)
    @DisplayName("TC035 - 유니코드 데이터 조회 및 값 무결성 검증")
    void testSelectUnicodeIntegrity() {
        log.info("Running TC035 - Select Unicode Data Integrity");

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC035.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_NAME : 다국어 문자가 깨지지 않아야 함
        Object name = response.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(name).isNotNull();
        assertThat(name.toString()).isNotEmpty();

        // USER_CODE에 유니코드 특수문자 포함 여부 확인 (TC011 기준값)
        Object code = response.getResultValue("select_tb_user_v2", 0, "USER_CODE");
        assertThat(code).isNotNull();
        assertThat(code.toString()).isNotEmpty();

        log.info("TC035 - PASSED");
    }

    @Test
    @Order(36)
    @DisplayName("TC036 - 날짜 값 조회 및 타입 검증")
    void testSelectDateTypeCheck() {
        log.info("Running TC036 - Select Date Value");

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC036.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // CREATED_DATE : 날짜가 보존되어야 함
        Object createdDate = response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE");
        assertThat(createdDate).isNotNull();
        assertThat(createdDate.toString()).isEqualToIgnoringCase("2026-01-01");

        // UPDATED_TS : 타임스탬프 형식 보존
        Object updatedTs = response.getResultValue("select_tb_user_v2", 0, "UPDATED_TS");
        assertThat(updatedTs).isNotNull();
        assertThat(updatedTs.toString()).isEqualToIgnoringCase("2026-01-01T00:00:00.000");

        // UPDATED_TZ : 타임존 포함 타임스탬프 형식 보존
        Object updatedTz = response.getResultValue("select_tb_user_v2", 0, "UPDATED_TZ");
        assertThat(updatedTz).isNotNull();
        assertThat(updatedTz.toString()).isEqualToIgnoringCase("2026-01-01T00:00:00.000+09:00");

        log.info("TC036 - PASSED");

    }

    @Test
    @Order(37)
    @DisplayName("TC037 - NULL 허용 컬럼 조회 (OPTIONAL_COL = null)")
    void testSelectNullableColumn() {
        log.info("Running TC037 - Select Nullable Column (OPTIONAL_COL)");

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC037.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // OPTIONAL_COL : null이 그대로 반환되어야 함 (누락 또는 null)
        Object optCol = response.getResultValue("select_tb_user_v2", 0, "OPTIONAL_COL");
        assertThat(optCol).isNull();

        // 다른 필수 컬럼은 정상 반환
        Object userId = response.getResultValue("select_tb_user_v2", 0, "USER_ID");
        assertThat(userId).isNotNull();
        assertThat(userId.toString()).isEqualTo("TC027_SQLI_001");

        log.info("TC037 - PASSED");
    }

    @Test
    @Order(38)
    @DisplayName("TC038 - 전체 조회 응답구조 검증 및 다건 결과 확인")
    void testSelectAllRows() {
        log.info("Running TC038 - Select All Rows (no param) - Response Structure & Multi-row");

        TestConfig.setEndpoint("select_tb_user_v2_no_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC038.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // 다건 조회 : 1건 이상 반환 확인 (사전 데이터 존재 전제)
        int rowCount = response.getResultRowCount("select_tb_user_v2");
        assertThat(rowCount).isGreaterThan(0);
        log.info("TC038 - Total rows returned: {}", rowCount);

        // 각 row에 필수 필드가 모두 존재하는지 확인 (첫 번째 row 기준)
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isNotNull();

        // 응답 메시지 구조 검증 (SELECT 전용 필드 포함)
        assertThat(response.getJsonResponse().has("operations")).isTrue();

        // SELECT 결과는 operations 하위에 rows 또는 data 배열로 존재해야 함
//        assertThat(response.getJsonResponse()
//            .getJSONObject("operations")
//            .has("select_tb_user_v2")).isTrue();

        log.info("TC038 - PASSED");
    }    

    @Test
    @Order(39)
    @DisplayName("TC039 - IN절 정상 조회 (3건, 모두 존재)")
    void testSelectInNormal() {
        log.info("Running TC039 - Select IN clause, 3 rows all exist");

        TestConfig.setEndpoint("select_tb_user_v2_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC039.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // 3건 모두 반환
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(3);

        // 반환된 USER_ID 목록 검증
        List<String> returnedIds = getResultFieldValues(response, "select_tb_user_v2", "USER_ID");
        assertThat(returnedIds).containsExactlyInAnyOrder(
            "TC027_SQLI_001", "TC027_SQLI_002", "TC027_SQLI_003"
        );

        // 각 row에 필수 컬럼이 모두 포함되어야 함
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE")).isNotNull();

        log.info("TC039 - PASSED");
    }

    @Test
    @Order(40)
    @DisplayName("TC040 - IN절 일부 존재 (3개 중 2개만 DB에 있음)")
    void testSelectInPartialMatch() {
        log.info("Running TC040 - Select IN clause, 3 rows but only 2 exist");

        TestConfig.setEndpoint("select_tb_user_v2_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC040.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // 없는 ID는 조용히 무시되고 존재하는 2건만 반환되어야 함
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
    @DisplayName("TC041 - IN절 1건 (단건 조회와 컬럼 구성 동일)")
    void testSelectInSingleRow() {
        log.info("Running TC041 - Select IN clause with single row");

        TestConfig.setEndpoint("select_tb_user_v2_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC041.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        // 정확히 1건
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // USER_ID 값 일치
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID"))
            .isEqualTo("TC027_SQLI_001");

        // param1 방식(TC031)과 동일하게 모든 컬럼 반환 확인
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_NICK")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_AGE")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "CREATED_DATE")).isNotNull();
        assertThat(response.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isNotNull();

        log.info("TC041 - PASSED");
    }

    @Test
    @Order(42)
    @DisplayName("TC042 - IN절 대량 100건 조회")
    void testSelectInBulk100() {
        log.info("Running TC042 - Select IN clause with 100 rows");

        dbinit();
        testBulkInsert100();
        
        TestConfig.setEndpoint("select_tb_user_v2_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC042.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // TC003에서 insert한 10건 반환 (IN절에 100건 지정, DB에 매칭 10건)
        int rowCount = response.getResultRowCount("select_tb_user_v2");
        assertThat(rowCount).isEqualTo(10);

        // 첫 번째, 중간, 마지막 ID 포함 여부 확인
        List<String> returnedIds = getResultFieldValues(response, "select_tb_user_v2", "USER_ID");
        assertThat(returnedIds).contains("ID_1", "ID_5", "ID_10");
        assertThat(returnedIds).hasSize(10);

        log.info("TC042 - PASSED (rows: {})", rowCount);
    }

    @Test
    @Order(43)
    @DisplayName("TC043 - IN절 빈 data 배열 (Empty List) → 0건 성공 반환")
    void testSelectInEmptyData() {
        log.info("Running TC043 - Select IN clause with empty data array");

        TestConfig.setEndpoint("select_tb_user_v2_in_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC043.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // 빈 data 배열: 바인딩 값이 null로 처리되어 IN(null) 조건 → 0건 정상 반환
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC043 - PASSED (empty IN clause returns 0 rows)");
    }
    
    @Test
    @Order(44)
    @DisplayName("TC044 - 파라메터가 있는 프로시저 호출")
    void testProcedure_param() {
        log.info("Running TC044 - Exec Procedure with params");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_procedure_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC044.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        log.info("TC044 - PASSED");
    }    

    // =========================================================
    // TC045 ~ TC086 : 추가 Oracle 기능 테스트 케이스
    // =========================================================

    @Test
    @Order(45)
    @DisplayName("TC045 - 다국어 (아랍어/히브리어/러시아어/일본어/중국어) INSERT")
    void testMultilingualInsert() {
        log.info("Running TC045 - Multilingual INSERT Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC045.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC045 - PASSED");
    }

    @Test
    @Order(46)
    @DisplayName("TC046 - 4바이트 이모지 및 복합 유니코드 INSERT")
    void testEmojiDataInsert() {
        log.info("Running TC046 - 4-byte Emoji INSERT Test");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC046.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC046 - PASSED");
    }

    @Test
    @Order(47)
    @DisplayName("TC047 - HTML/XML 특수문자 INSERT")
    void testHtmlXmlSpecialCharsInsert() {
        log.info("Running TC047 - HTML/XML Special Characters INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC047.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC047 - PASSED");
    }

    @Test
    @Order(48)
    @DisplayName("TC048 - SQL 예약어 값으로 INSERT (SQL Injection 방어 재검증)")
    void testSqlKeywordDataInsert() {
        log.info("Running TC048 - SQL Keyword Data INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC048.json"));

        // SQL 예약어가 값으로 삽입될 때 DB 구조가 영향받지 않아야 함
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC048 - PASSED");
    }

    @Test
    @Order(49)
    @DisplayName("TC049 - 숫자 정밀도 경계값 INSERT")
    void testNumericPrecisionInsert() {
        log.info("Running TC049 - Numeric Precision INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC049.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        // SELECT: 정밀도 경계값이 Oracle 컬럼 범위 내에서 정확히 저장되었는지 범위 검증
        ApiResponse sr049 = selectByUserId("NUM_1");
        assertThat(sr049.isSuccess()).isTrue();
        assertThat(sr049.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        // π ≈ 3.14159... → [3.14, 3.15)
        BigDecimal pi = new BigDecimal(sr049.getResultValue("select_tb_user_v2", 0, "USER_SCORE").toString());
        assertThat(pi.compareTo(new BigDecimal("3.14"))).isGreaterThanOrEqualTo(0);
        assertThat(pi.compareTo(new BigDecimal("3.15"))).isLessThan(0);
        // √2 ≈ 1.4142... → [1.41, 1.42)
        BigDecimal sqrt2 = new BigDecimal(sr049.getResultValue("select_tb_user_v2", 0, "USER_RATE").toString());
        assertThat(sqrt2.compareTo(new BigDecimal("1.41"))).isGreaterThanOrEqualTo(0);
        assertThat(sqrt2.compareTo(new BigDecimal("1.42"))).isLessThan(0);
        // e ≈ 2.7182... → [2.71, 2.72)
        BigDecimal eNum = new BigDecimal(sr049.getResultValue("select_tb_user_v2", 0, "USER_RATIO").toString());
        assertThat(eNum.compareTo(new BigDecimal("2.71"))).isGreaterThanOrEqualTo(0);
        assertThat(eNum.compareTo(new BigDecimal("2.72"))).isLessThan(0);
        // √3 ≈ 1.7320... → [1.73, 1.74)
        BigDecimal sqrt3 = new BigDecimal(sr049.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT").toString());
        assertThat(sqrt3.compareTo(new BigDecimal("1.73"))).isGreaterThanOrEqualTo(0);
        assertThat(sqrt3.compareTo(new BigDecimal("1.74"))).isLessThan(0);

        log.info("TC049 - PASSED");
    }

    @Test
    @Order(50)
    @DisplayName("TC050 - 전체 음수값 INSERT")
    void testNegativeValuesInsert() {
        log.info("Running TC050 - Negative Values INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC050.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        // SELECT: 음수 부호가 손실되지 않고 정확히 저장되었는지 검증
        ApiResponse sr050 = selectByUserId("NEG_1");
        assertThat(sr050.isSuccess()).isTrue();
        assertThat(sr050.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(new BigDecimal(sr050.getResultValue("select_tb_user_v2", 0, "USER_SCORE").toString()).compareTo(new BigDecimal("-88.1234"))).isEqualTo(0);
        assertThat(new BigDecimal(sr050.getResultValue("select_tb_user_v2", 0, "USER_RATE").toString()).compareTo(new BigDecimal("-0.12345678"))).isEqualTo(0);
        assertThat(new BigDecimal(sr050.getResultValue("select_tb_user_v2", 0, "USER_RATIO").toString()).compareTo(new BigDecimal("-1.2345"))).isEqualTo(0);
        assertThat(new BigDecimal(sr050.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT").toString()).compareTo(new BigDecimal("-75.4321"))).isEqualTo(0);
        // 음수 정수값 검증
        assertThat(new BigDecimal(sr050.getResultValue("select_tb_user_v2", 0, "USER_AGE").toString()).intValue()).isEqualTo(-1);
        assertThat(new BigDecimal(sr050.getResultValue("select_tb_user_v2", 0, "USER_COUNT").toString()).intValue()).isEqualTo(-100);

        log.info("TC050 - PASSED");
    }

    @Test
    @Order(51)
    @DisplayName("TC051 - 빈 문자열 CLOB INSERT")
    void testEmptyClobInsert() {
        log.info("Running TC051 - Empty CLOB INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC051.json"));

        // Oracle에서 빈 문자열은 NULL로 처리될 수 있음 (정상 처리)
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC051 - PASSED");
    }

    @Test
    @Order(52)
    @DisplayName("TC052 - 대용량 CLOB (4000자) INSERT")
    void testLargeClob4000Insert() {
        log.info("Running TC052 - Large CLOB 4000 chars INSERT");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC052.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);
        assertThat(response.getCode()).isEqualTo("S000");

        log.info("TC052 - PASSED");
    }

    @Test
    @Order(53)
    @DisplayName("TC053 - 다국어 데이터 INSERT 후 SELECT 무결성 검증")
    void testMultilingualRoundTrip() {
        log.info("Running TC053 - Multilingual Round-Trip Test");

        dbinit();

        // INSERT using JSON resource (USER_ID = "ML_99")
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("TC053.json"));
        assertThat(insertResponse.isSuccess()).isTrue();

        // Then SELECT
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // Verify multilingual data integrity
        Object userName = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(userName).isNotNull();
        assertThat(userName.toString()).contains("محمد");

        log.info("TC053 - PASSED");
    }

    @Test
    @Order(54)
    @DisplayName("TC054 - 특수문자 데이터 INSERT 후 SELECT 무결성 검증")
    void testSpecialCharsRoundTrip() {
        log.info("Running TC054 - Special Characters Round-Trip Test");

        // INSERT using JSON resource (USER_ID = "HX_99")
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("TC054.json"));
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNotNull();
        assertThat(desc.toString()).contains("<root>");

        log.info("TC054 - PASSED");
    }

    @Test
    @Order(55)
    @DisplayName("TC055 - 음수 숫자값 INSERT 후 SELECT 무결성 검증")
    void testNegativeValuesRoundTrip() {
        log.info("Running TC055 - Negative Values Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("TC055.json"));
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object age = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_AGE");
        assertThat(age).isNotNull();
        // Verify negative value preserved
        assertThat(Integer.parseInt(age.toString())).isNegative();

        log.info("TC055 - PASSED");
    }

    @Test
    @Order(56)
    @DisplayName("TC056 - 이모지 데이터 INSERT 후 SELECT 무결성 검증")
    void testEmojiRoundTrip() {
        log.info("Running TC056 - Emoji Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("TC056.json"));
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object name = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME");
        assertThat(name).isNotNull();
        assertThat(name.toString()).contains("😀");

        log.info("TC056 - PASSED");
    }

    @Test
    @Order(57)
    @DisplayName("TC057 - 대용량 CLOB (4000자) INSERT 후 SELECT 무결성 검증")
    void testLargeClob4000RoundTrip() {
        log.info("Running TC057 - Large CLOB 4000 chars Round-Trip Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("TC057.json"));
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        // Oracle: empty string = NULL, but 4000 'A' chars should be preserved
        assertThat(desc).isNotNull();
        assertThat(desc.toString().length()).isGreaterThanOrEqualTo(4000);

        log.info("TC057 - PASSED");
    }

    @Test
    @Order(58)
    @DisplayName("TC058 - 빈 CLOB INSERT 후 SELECT (Oracle 빈문자열=NULL 검증)")
    void testEmptyClobRoundTrip() {
        log.info("Running TC058 - Empty CLOB Round-Trip (Oracle empty=NULL) Test");

        // INSERT using JSON resource
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("TC058.json"));
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectRequestBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        // Oracle에서 빈 문자열은 NULL로 저장됨
        Object desc = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_DESC");
        assertThat(desc).isNull(); // Oracle: '' = NULL

        log.info("TC058 - PASSED");
    }

    // ==========================================================
    // TC059 ~ TC068 : UPDATE / UPSERT / DELETE(조건) / SELECT(다중파라미터)
    // ==========================================================

    @Test
    @Order(59)
    @DisplayName("TC059 - UPDATE 단건 성공 및 값 검증")
    void testUpdateSingle() {
        log.info("Running TC059 - UPDATE Single Row");

        // DELETE(초기화) + INSERT(사전 데이터) + UPDATE(테스트 대상)
        TestConfig.setEndpoint("update_tb_user_v2/dbconnector");
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("Updated_Name_1");
        // Oracle NUMBER → BigDecimal 반환 가능하므로 toString 비교
        assertThat(Integer.parseInt(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_AGE").toString())).isEqualTo(99);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("N");

        log.info("TC059 - PASSED");
    }

    @Test
    @Order(60)
    @DisplayName("TC060 - UPDATE 3건 배치")
    void testUpdateBatch() {
        log.info("Running TC060 - UPDATE Batch (3 rows)");

        TestConfig.setEndpoint("update_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC060.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("update_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);
        assertThat(response.getAffectedRows("update_tb_user_v2")).isEqualTo(3);

        log.info("TC060 - PASSED");
    }

    @Test
    @Order(61)
    @DisplayName("TC061 - UPDATE 미존재 행 (affected rows = 0)")
    void testUpdateNoMatch() {
        log.info("Running TC061 - UPDATE Non-existent Row");

        TestConfig.setEndpoint("update_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC061.json"));

        // 존재하지 않는 행 UPDATE는 SQL 오류가 아닌 success + affected rows = 0
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("update_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("update_tb_user_v2")).isEqualTo(0);

        log.info("TC061 - PASSED");
    }

    @Test
    @Order(62)
    @DisplayName("TC062 - UPSERT INSERT 분기 (신규 행 삽입)")
    void testUpsertInsertBranch() {
        log.info("Running TC062 - UPSERT INSERT Branch (new row)");

        // ID_1 사전 삽입 후 ID_2 UPSERT → WHEN NOT MATCHED → INSERT
        TestConfig.setEndpoint("upsert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC062.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("upsert_tb_user_v2")).isEqualTo(1);

        log.info("TC062 - PASSED");
    }

    @Test
    @Order(63)
    @DisplayName("TC063 - UPSERT UPDATE 분기 (기존 행 갱신)")
    void testUpsertUpdateBranch() {
        log.info("Running TC063 - UPSERT UPDATE Branch (existing row)");

        // ID_1 사전 삽입 후 ID_1 UPSERT → WHEN MATCHED → UPDATE
        TestConfig.setEndpoint("upsert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC063.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("After_Upsert_1");
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("N");

        log.info("TC063 - PASSED");
    }

    @Test
    @Order(64)
    @DisplayName("TC064 - UPSERT 혼합 (UPDATE + INSERT 동시)")
    void testUpsertMixed() {
        log.info("Running TC064 - UPSERT Mixed (UPDATE existing + INSERT new)");

        // ID_1, ID_2 사전 삽입 후 UPSERT: ID_1(UPDATE) + ID_3(INSERT)
        TestConfig.setEndpoint("upsert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC064.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("upsert_tb_user_v2")).isTrue();
        // ID_1 UPDATE + ID_3 INSERT = 총 2행 처리
        assertThat(response.getAffectedRows("upsert_tb_user_v2")).isEqualTo(2);

        log.info("TC064 - PASSED");
    }

    @Test
    @Order(65)
    @DisplayName("TC065 - DELETE WHERE IN (단건 조건부 삭제)")
    void testDeleteWithParam() {
        log.info("Running TC065 - DELETE WHERE IN (single row)");

        // DELETE(초기화) + INSERT(3건) + DELETE WHERE IN(ID_1만 삭제)
        TestConfig.setEndpoint("delete_tb_user_v2_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC065.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2_param")).isTrue();
        assertThat(response.getAffectedRows("delete_tb_user_v2_param")).isEqualTo(1);

        // SELECT ALL로 남은 행 수 검증 (ID_2, ID_3 두 행 남아야 함)
        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        selectOp.put("data", new ArrayList<>());
        ops.put("select_tb_user_v2", selectOp);
        selectBody.put("operations", ops);

        TestConfig.setEndpoint("select_tb_user_v2_no_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(2);
        List<String> remainingIds = getResultFieldValues(selectResponse, "select_tb_user_v2", "USER_ID");
        assertThat(remainingIds).containsExactlyInAnyOrder("ID_2", "ID_3");

        log.info("TC065 - PASSED");
    }

    @Test
    @Order(66)
    @DisplayName("TC066 - DELETE WHERE IN (다건 + 미존재 포함)")
    void testDeleteWithParamMulti() {
        log.info("Running TC066 - DELETE WHERE IN (multi + non-existent)");

        // INSERT 3건 후 IN(ID_1, ID_2, ID_99) 삭제 → 실제 삭제 2건 (ID_99 미존재)
        TestConfig.setEndpoint("delete_tb_user_v2_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC066.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2_param")).isTrue();
        assertThat(response.getAffectedRows("delete_tb_user_v2_param")).isEqualTo(2);

        log.info("TC066 - PASSED");
    }

    @Test
    @Order(67)
    @DisplayName("TC067 - SELECT 다중 파라미터 (IS_ACTIVE + USER_AGE, 결과 있음)")
    void testSelectMultiParam() {
        log.info("Running TC067 - SELECT Multi-Parameter (results found)");

        // 사전 데이터 삽입: IS_ACTIVE 혼합, USER_AGE 혼합
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
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
        TestConfig.setEndpoint("select_tb_user_v2_multi_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC067.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(response.getResultValue("select_tb_user_v2", 0, "USER_ID")).isEqualTo("ID_1");
        assertThat(response.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("Y");

        log.info("TC067 - PASSED");
    }

    @Test
    @Order(68)
    @DisplayName("TC068 - SELECT 다중 파라미터 (USER_AGE > 200, 결과 없음)")
    void testSelectMultiParamNoResult() {
        log.info("Running TC068 - SELECT Multi-Parameter (no results)");

        // TC067 이후 데이터 존재 상태에서 조건이 맞지 않는 파라미터로 조회
        TestConfig.setEndpoint("select_tb_user_v2_multi_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC068.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(0);

        log.info("TC068 - PASSED");
    }

    @Test
    @Order(69)
    @DisplayName("TC069 - UPDATE DECIMAL WHERE (숫자 타입 JSON 전송 → 1 row 갱신)")
    void testUpdateDecimalWhere() {
        log.info("Running TC069 - UPDATE with DECIMAL WHERE (numeric JSON value, no quotes)");

        // DELETE(초기화) + INSERT(USER_SCORE=123.45 숫자 타입) + UPDATE WHERE USER_SCORE=123.45(숫자 타입)
        // 수정 전: setObject(Double, Types.DECIMAL) → new BigDecimal(double) → 부동소수점 오차 → 0 rows
        // 수정 후: setBigDecimal(new BigDecimal("123.45"))               → 정확한 소수 → 1 row
        TestConfig.setEndpoint("update_tb_user_v2_decimal_where/dbconnector");
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

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "USER_NAME")).isEqualTo("Decimal_Updated");
        assertThat(selectResponse.getResultValue("select_tb_user_v2", 0, "IS_ACTIVE")).isEqualTo("U");

        log.info("TC069 - PASSED");
    }

    @Test
    @Order(70)
    @DisplayName("TC070 - INSERT DECIMAL → SELECT 값 정확도 검증 (BigDecimal 비교)")
    void testDecimalSelectAccuracy() {
        log.info("Running TC070 - DECIMAL value accuracy after INSERT → SELECT");

        // INSERT: USER_SCORE=123.45, USER_RATE=0.12345678, USER_RATIO=1.2345, USER_WEIGHT=75.4321
        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse insertResponse = apiClient.callApi(endpoint, null, readJson("TC070.json"));

        assertThat(insertResponse.isSuccess()).isTrue();
        assertThat(insertResponse.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        // SELECT: USER_ID = "DEC_70" 으로 조회
        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops        = new HashMap<>();
        Map<String, Object> selectOp   = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param      = new HashMap<>();
        param.put("USER_ID", "DEC_70");
        params.add(param);
        selectOp.put("data", params);
        ops.put("select_tb_user_v2", selectOp);
        selectBody.put("operations", ops);

        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse selectResponse = apiClient.callApi(endpoint, selectBody, null);

        assertThat(selectResponse.isSuccess()).isTrue();
        assertThat(selectResponse.getResultRowCount("select_tb_user_v2")).isEqualTo(1);

        // Oracle NUMBER → BigDecimal 반환; compareTo로 정밀도 비교
        Object score  = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_SCORE");
        Object rate   = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_RATE");
        Object ratio  = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_RATIO");
        Object weight = selectResponse.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT");

        assertThat(new BigDecimal(score.toString()).compareTo(new BigDecimal("123.45"))).isEqualTo(0);
        assertThat(new BigDecimal(rate.toString()).compareTo(new BigDecimal("0.12345678"))).isEqualTo(0);
        assertThat(new BigDecimal(ratio.toString()).compareTo(new BigDecimal("1.2345"))).isEqualTo(0);
        assertThat(new BigDecimal(weight.toString()).compareTo(new BigDecimal("75.4321"))).isEqualTo(0);

        log.info("TC070 - PASSED (score={}, rate={}, ratio={}, weight={})",
                score, rate, ratio, weight);
    }

    @Test
    @Order(71)
    @DisplayName("TC071 - NULL DECIMAL INSERT → SELECT NULL 검증")
    void testNullDecimalInsertSelect() {
        log.info("Running TC071 - NULL DECIMAL INSERT and SELECT NULL verification");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC071.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        // SELECT: decimal 필드가 NULL로 저장되었는지 검증
        ApiResponse sr071 = selectByUserId("NULL_DEC_71");
        assertThat(sr071.isSuccess()).isTrue();
        assertThat(sr071.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(sr071.getResultValue("select_tb_user_v2", 0, "USER_SCORE")).isNull();
        assertThat(sr071.getResultValue("select_tb_user_v2", 0, "USER_RATE")).isNull();
        assertThat(sr071.getResultValue("select_tb_user_v2", 0, "USER_RATIO")).isNull();
        assertThat(sr071.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT")).isNull();

        log.info("TC071 - PASSED");
    }

    @Test
    @Order(72)
    @DisplayName("TC072 - 문자열 DECIMAL INSERT (\"123.45\") → SELECT BigDecimal 정밀도 검증")
    void testStringDecimalInsertSelect() {
        log.info("Running TC072 - String-typed DECIMAL INSERT and SELECT precision verification");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC072.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        // SELECT: 문자열로 입력된 decimal 값이 정밀도 손실 없이 저장되었는지 검증
        ApiResponse sr072 = selectByUserId("STR_DEC_72");
        assertThat(sr072.isSuccess()).isTrue();
        assertThat(sr072.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
        assertThat(new BigDecimal(sr072.getResultValue("select_tb_user_v2", 0, "USER_SCORE").toString())
                .compareTo(new BigDecimal("123.45"))).isEqualTo(0);
        assertThat(new BigDecimal(sr072.getResultValue("select_tb_user_v2", 0, "USER_RATE").toString())
                .compareTo(new BigDecimal("0.12345678"))).isEqualTo(0);
        assertThat(new BigDecimal(sr072.getResultValue("select_tb_user_v2", 0, "USER_RATIO").toString())
                .compareTo(new BigDecimal("1.2345"))).isEqualTo(0);
        assertThat(new BigDecimal(sr072.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT").toString())
                .compareTo(new BigDecimal("75.4321"))).isEqualTo(0);

        log.info("TC072 - PASSED");
    }

    @Test
    @Order(73)
    @DisplayName("TC073 - DECIMAL 배치 INSERT (3건) → SELECT 전수 정밀도 검증")
    void testBatchDecimalInsertSelect() {
        log.info("Running TC073 - Batch DECIMAL INSERT (3 rows) and full precision verification");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC073.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(3);

        // 각 행별 decimal 값 정밀도 개별 검증
        String[][] expected = {
            {"BDEC_73_1", "111.11", "0.11111111", "1.1111", "11.1111"},
            {"BDEC_73_2", "222.22", "0.22222222", "2.2222", "22.2222"},
            {"BDEC_73_3", "333.33", "0.33333333", "3.3333", "33.3333"}
        };

        for (String[] row : expected) {
            ApiResponse sr = selectByUserId(row[0]);
            assertThat(sr.isSuccess()).isTrue();
            assertThat(sr.getResultRowCount("select_tb_user_v2")).isEqualTo(1);
            assertThat(new BigDecimal(sr.getResultValue("select_tb_user_v2", 0, "USER_SCORE").toString())
                    .compareTo(new BigDecimal(row[1]))).as("USER_SCORE for " + row[0]).isEqualTo(0);
            assertThat(new BigDecimal(sr.getResultValue("select_tb_user_v2", 0, "USER_RATE").toString())
                    .compareTo(new BigDecimal(row[2]))).as("USER_RATE for " + row[0]).isEqualTo(0);
            assertThat(new BigDecimal(sr.getResultValue("select_tb_user_v2", 0, "USER_RATIO").toString())
                    .compareTo(new BigDecimal(row[3]))).as("USER_RATIO for " + row[0]).isEqualTo(0);
            assertThat(new BigDecimal(sr.getResultValue("select_tb_user_v2", 0, "USER_WEIGHT").toString())
                    .compareTo(new BigDecimal(row[4]))).as("USER_WEIGHT for " + row[0]).isEqualTo(0);
        }

        log.info("TC073 - PASSED");
    }

    @Test
    @Order(74)
    @DisplayName("TC074 - 2 테이블 JOIN SELECT (TB_USER_V2 INNER JOIN TB_USER_V2_D1)")
    void testSelectJoinTwoTables() {
        log.info("Running TC074 - SELECT with INNER JOIN between TB_USER_V2 and TB_USER_V2_D1");

        // Given: 마스터(TB_USER_V2) 1건 + 디테일(TB_USER_V2_D1) 3건 INSERT
        TestConfig.setEndpoint("delete_insert_tb_user_v2_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse setupResponse = apiClient.callApi(endpoint, null, readJson("TC074_setup.json"));
        assertThat(setupResponse.isSuccess()).isTrue();

        // When: JOIN SELECT 수행
        TestConfig.setEndpoint("select_tb_user_v2_join_d1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC074.json"));

        // Then: 기본 응답 검증
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_user_join_history")).isTrue();

        // INNER JOIN 결과: 마스터 1건 × 디테일 3건 = 3행
        int rowCount = response.getResultRowCount("select_user_join_history");
        assertThat(rowCount).isEqualTo(3);

        // 모든 행에 TB_USER_V2 컬럼값이 동일하게 채워져 있는지 확인
        for (int i = 0; i < rowCount; i++) {
            assertThat(response.getResultValue("select_user_join_history", i, "USER_ID")).isEqualTo("JN74_1");
            assertThat(response.getResultValue("select_user_join_history", i, "USER_NAME")).isEqualTo("Join_Test_74");
            assertThat(response.getResultValue("select_user_join_history", i, "IS_ACTIVE")).isEqualTo("Y");
        }

        // TB_USER_V2_D1 컬럼: HIST_ID 순서대로 1, 2, 3 정렬 확인
        assertThat(Integer.parseInt(response.getResultValue("select_user_join_history", 0, "HIST_ID").toString())).isEqualTo(1);
        assertThat(response.getResultValue("select_user_join_history", 0, "CHANGE_TYPE")).isEqualTo("INSERT");
        assertThat(response.getResultValue("select_user_join_history", 0, "CHANGE_DESC")).isEqualTo("최초 등록");
        assertThat(response.getResultValue("select_user_join_history", 0, "CREATED_BY")).isEqualTo("tc074");

        assertThat(Integer.parseInt(response.getResultValue("select_user_join_history", 1, "HIST_ID").toString())).isEqualTo(2);
        assertThat(response.getResultValue("select_user_join_history", 1, "CHANGE_TYPE")).isEqualTo("UPDATE");
        assertThat(response.getResultValue("select_user_join_history", 1, "CHANGE_DESC")).isEqualTo("닉네임 변경");

        assertThat(Integer.parseInt(response.getResultValue("select_user_join_history", 2, "HIST_ID").toString())).isEqualTo(3);
        assertThat(response.getResultValue("select_user_join_history", 2, "CHANGE_TYPE")).isEqualTo("UPDATE");
        assertThat(response.getResultValue("select_user_join_history", 2, "CHANGE_DESC")).isEqualTo("상태 변경");

        // CHANGE_DTTM 필드가 null이 아닌지 확인 (TIMESTAMP 매핑 검증)
        assertThat(response.getResultValue("select_user_join_history", 0, "CHANGE_DTTM")).isNotNull();

        log.info("TC074 - PASSED (JOIN rows: {})", rowCount);
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

    // =========================================================
    // IN절 테스트 전용 헬퍼
    // =========================================================

    /**
     * SELECT 결과 rows에서 특정 컬럼 값만 추출하여 List<String>으로 반환
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
    
    private Map<String, Object> createInsertRequest(
            String operationName, List<Map<String, Object>> data) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        Map<String, Object> operation = new HashMap<>();
        operation.put("data", data);
        operations.put(operationName, operation);
        requestBody.put("operations", operations);
        return requestBody;
    }
    
    private Map<String, Object> createDeleteInsertRequest(
    		String deleteOperationName,
    		List<Map<String, Object>> deleteData,
            String insertOperationName,
            List<Map<String, Object>> insertData) {
    	
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> operations = new HashMap<>();
        
        Map<String, Object> deleteOperation = new HashMap<>();
        deleteOperation.put("data", deleteData);
        
        operations.put(deleteOperationName, deleteOperation);
        
        Map<String, Object> insertOperation = new HashMap<>();
        insertOperation.put("data", insertData);
        operations.put(insertOperationName, insertOperation);

        requestBody.put("operations", operations);
        return requestBody;
    }    

    private void createMultiDeleteInsertRequest(
    		Map<String, Object> requestBody,
    		Map<String, Object> operations,
    		String operationName,
    		List<Map<String, Object>> Data) {    	
        
        if(operationName!=null) {
            Map<String, Object> operation = new HashMap<>();
            operation.put("data", Data);        
            operations.put(operationName, operation);
        }
        
        requestBody.put("operations", operations);
        
    } 
    
    /**
     * classpath 리소스에서 JSON 파일을 읽어 반환 (TestDataLoader 위임)
     */
    private String readJson(String fileName) {
        return TestDataLoader.loadJson(fileName);
    }

    /**
     * USER_ID 단건 SELECT 헬퍼 — 경계값/정밀도 검증용
     */
    private ApiResponse selectByUserId(String userId) {
        Map<String, Object> body  = new HashMap<>();
        Map<String, Object> ops   = new HashMap<>();
        Map<String, Object> op    = new HashMap<>();
        List<Map<String, Object>> params = new ArrayList<>();
        Map<String, Object> param = new HashMap<>();
        param.put("USER_ID", userId);
        params.add(param);
        op.put("data", params);
        ops.put("select_tb_user_v2", op);
        body.put("operations", ops);
        TestConfig.setEndpoint("select_tb_user_v2_param1/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        return apiClient.callApi(endpoint, body, null);
    }

    // ==========================================================
    // TC075 ~ TC077 : max_row_limit 검증
    // ==========================================================

    @Test
    @Order(75)
    @DisplayName("TC075 - 전역 max_row_limit 초과 시 E400(실패) 반환")
    void testGlobalMaxRowLimitExceeded() {
        log.info("Running TC075 - Global max_row_limit exceeded (4 rows > limit 3)");

        // FK 자식 레코드 먼저 삭제 후 테스트
        dbinit();

        // delete_insert_tb_user_v2_global_mrl : global max_row_limit=3
        TestConfig.setEndpoint("delete_insert_tb_user_v2_global_mrl/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // 4건 전송 → 전역 한도(3) 초과 → INSERT 실패
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC075.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC075 - PASSED (insert rejected with 4 rows > global limit 3)");
    }

    @Test
    @Order(76)
    @DisplayName("TC076 - 전역 max_row_limit 이내 INSERT 성공")
    void testGlobalMaxRowLimitWithinLimit() {
        log.info("Running TC076 - Within global max_row_limit (2 rows <= limit 3)");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_global_mrl/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // 2건 전송 → 전역 한도(3) 이내 → INSERT 성공
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC076.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);

        log.info("TC076 - PASSED (2 rows inserted within global limit 3)");
    }

    @Test
    @Order(77)
    @DisplayName("TC077 - Operation 단위 max_row_limit 초과 시 E400(실패) 반환 (전역 한도 이내라도 op 한도 우선)")
    void testOperationMaxRowLimitExceeded() {
        log.info("Running TC077 - Operation-level max_row_limit exceeded (3 rows > op limit 2, global limit 10)");

        dbinit();

        // delete_insert_tb_user_v2_op_mrl : global=10, insert op max_row_limit=2
        TestConfig.setEndpoint("delete_insert_tb_user_v2_op_mrl/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // 3건 전송 → 전역(10) 이내지만 op 한도(2) 초과 → INSERT 실패
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC077.json"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isFalse();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC077 - PASSED (insert rejected with 3 rows > op limit 2)");
    }

    // ==========================================================
    // TC078 ~ TC079 : Operation 단위 data_record_path 검증
    // ==========================================================

    @Test
    @Order(78)
    @DisplayName("TC078 - op-level data_record_path(/batch) 지정 시 해당 경로에서 데이터 파싱하여 INSERT 성공")
    void testOpDataRecordPathSuccess() {
        log.info("Running TC078 - op-level data_record_path (/batch) INSERT success");

        dbinit();

        // delete_insert_tb_user_v2_op_drp:
        //   delete op  - 표준 경로 (/operations/delete_tb_user_v2/data)
        //   insert op  - 커스텀 경로 (/batch/data)
        TestConfig.setEndpoint("delete_insert_tb_user_v2_op_drp/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // 입력: operations.delete_tb_user_v2=null, batch.data=[2건]
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC078.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);

        log.info("TC078 - PASSED (2 rows inserted via op-level data_record_path /batch)");
    }

    @Test
    @Order(79)
    @DisplayName("TC079 - op-level data_record_path(/batch) 지정 시 표준 경로 데이터는 무시 (INSERT 0건)")
    void testOpDataRecordPathMismatch() {
        log.info("Running TC079 - op-level data_record_path mismatch: data at /operations, config expects /batch");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_op_drp/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // 입력: 표준 경로(operations.insert_tb_user_v2.data)로 1건 전송
        //       → insert op은 /batch/data를 탐색하므로 데이터를 찾지 못해 0건
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC079.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC079 - PASSED (insert 0 rows: data at /operations ignored, /batch not found)");
    }

    // ==========================================================
    // TC080 : op-level data_record_path — 단일 경로로 DELETE/INSERT/PROC 처리
    // ==========================================================

    @Test
    @Order(80)
    @DisplayName("TC080 - payload 단일 경로에서 DELETE(rows 키 없음)/INSERT(rows 파싱)/PROC 모두 실행")
    void testPayloadPathDeleteInsertProc() {
        log.info("Running TC080 - payload path: DELETE+INSERT+PROC via single /payload path, no 'operations' key");

        dbinit();

        // delete_insert_tb_user_v2_payload:
        //   - delete op : data_record_path=/payload, data_record=rows → /payload 존재하면 실행(rows 키 없어도 무방)
        //   - insert op : data_record_path=/payload, data_record=rows → /payload/rows 파싱
        //   - proc   op : data_record_path=/payload, data_record=rows → /payload 존재하면 실행
        //
        // 입력 JSON에 "operations" 키 없음 — "payload" 키만 존재
        TestConfig.setEndpoint("delete_insert_tb_user_v2_payload/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC080.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        // DELETE — 파라미터 없는 전체 삭제 실행 (rows 키 없어도 skip 없이 실행)
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();

        // INSERT — /payload/rows 2건 파싱하여 삽입
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);

        // PROCEDURE — 파라미터 없이 실행 (rows 키 없어도 skip 없이 실행)
        assertThat(response.isOperationSuccess("proc_tb_user_v2")).isTrue();

        log.info("TC080 - PASSED (DELETE+INSERT 2rows+PROC via /payload, no 'operations' key)");
    }

    // ==========================================================
    // TC081 : execute_if_no_data — 표준 경로에서 op 키 없이 강제 실행
    // ==========================================================

    @Test
    @Order(81)
    @DisplayName("TC081 - execute_if_no_data=true인 DELETE/PROC은 op 키 없이도 강제 실행")
    void testExecuteIfNoData() {
        log.info("Running TC081 - execute_if_no_data: DELETE+PROC forced execution without op key");

        dbinit();

        // delete_insert_tb_user_v2_eind:
        //   - delete_tb_user_v2  : execute_if_no_data=true  → operations 아래 키 없어도 강제 실행
        //   - insert_tb_user_v2  : 표준 경로 (operations.insert_tb_user_v2.data)
        //   - proc_tb_user_v2    : execute_if_no_data=true  → operations 아래 키 없어도 강제 실행
        //
        // 입력 JSON: "operations.insert_tb_user_v2.data=[2건]"만 포함
        //           "delete_tb_user_v2" / "proc_tb_user_v2" 키 없음
        TestConfig.setEndpoint("delete_insert_tb_user_v2_eind/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC081.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");

        // DELETE — op 키 없음에도 execute_if_no_data=true로 강제 실행
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();

        // INSERT — 표준 경로 데이터 2건 파싱
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(2);

        // PROCEDURE — op 키 없음에도 execute_if_no_data=true로 강제 실행
        assertThat(response.isOperationSuccess("proc_tb_user_v2")).isTrue();

        log.info("TC081 - PASSED (DELETE+INSERT 2rows+PROC: delete/proc forced via execute_if_no_data, no op key)");
    }

    // ==========================================================
    // TC082 ~ TC083 : Bulk DML (bulk=true, chunk_commit_size 청크 커밋)
    // ==========================================================

    @Test
    @Order(82)
    @DisplayName("TC082 - bulk=true INSERT 10건: Iterator 기반 처리 + chunk_commit_size=5 청크 커밋")
    void testBulkInsert() {
        log.info("Running TC082 - bulk=true INSERT 10 rows with chunk_commit_size=5");

        dbinit();

        // delete_insert_tb_user_v2_bulk: bulk=true, chunk_commit_size=5
        TestConfig.setEndpoint("delete_insert_tb_user_v2_bulk/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC082.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        // DELETE: 전체 삭제
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        // INSERT: bulk 경로로 10건 처리 (chunk_commit_size=5 → 2회 커밋)
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(10);

        log.info("TC082 - PASSED (bulk INSERT 10 rows, 2 chunk commits of 5 each)");
    }

    @Test
    @Order(83)
    @DisplayName("TC083 - bulk=true INSERT: insert 키 없는 경우 skip")
    void testBulkInsertSkipNoData() {
        log.info("Running TC083 - bulk=true INSERT skipped when insert key absent");

        dbinit();

        // 입력: delete 키만 포함, insert_tb_user_v2 키 없음 → bulk op existNode=false → skip
        TestConfig.setEndpoint("delete_insert_tb_user_v2_bulk/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC083.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("delete_tb_user_v2")).isTrue();
        // insert op이 skip 되었으므로 affectedRows=0
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(0);

        log.info("TC083 - PASSED (bulk INSERT skipped: insert key absent → existNode=false → skip)");
    }

    // ==========================================================
    // TC084 ~ TC085 : SELECT Streaming (fetch_size + JsonGenerator)
    // ==========================================================

    @Test
    @Order(84)
    @DisplayName("TC084 - fetch_size 지정 SELECT: JsonGenerator 스트리밍 경로로 결과 반환")
    void testSelectFetchSize() {
        log.info("Running TC084 - SELECT with fetch_size=10 via JsonGenerator streaming path");

        dbinit();

        // TC082에서 insert한 10건이 남아있지 않으므로 TC082와 동일한 데이터를 bulk로 먼저 insert
        TestConfig.setEndpoint("delete_insert_tb_user_v2_bulk/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        apiClient.callApi(endpoint, null, readJson("TC082.json"));

        // SELECT with fetch_size: JsonGenerator 스트리밍 경로
        TestConfig.setEndpoint("select_tb_user_v2_fetch/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC084.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();
        // 10건이 SELECT 결과로 반환되어야 함
        assertThat(response.getResultRowCount("select_tb_user_v2")).isEqualTo(10);

        log.info("TC084 - PASSED (fetch_size SELECT returned 10 rows via JsonGenerator streaming)");
    }

    @Test
    @Order(85)
    @DisplayName("TC085 - fetch_size SELECT 결과 필드값 검증")
    void testSelectFetchSizeFieldValues() {
        log.info("Running TC085 - SELECT fetch_size field value verification");

        // TC084에서 insert된 데이터 그대로 활용 (dbinit 없음)
        TestConfig.setEndpoint("select_tb_user_v2_fetch/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC085.json"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        // 첫 번째 행(ORDER BY USER_ID → BULK82_01) 필드값 검증
        assertThat(response.getResultRowCount("select_tb_user_v2")).isGreaterThan(0);
        assertThat(response.getResultStringValue("select_tb_user_v2", 0, "USER_ID"))
                .isEqualTo("BULK82_01");
        assertThat(response.getResultStringValue("select_tb_user_v2", 0, "OPTIONAL_COL"))
                .isEqualTo("TC082");

        log.info("TC085 - PASSED (fetch_size SELECT field values verified: USER_ID=BULK82_01)");
    }

    // ==========================================================
    // TC086 ~ TC087 : PROCEDURE OUT 파라메터 지원 테스트
    // ==========================================================

    @Test
    @Order(86)
    @DisplayName("TC086 - OUT 파라메터 프로시저 호출 (정상 복사 후 COPY_CNT=1, RESULT_MSG 반환)")
    void testProcedureOutParam_normal() {
        log.info("Running TC086 - Procedure with OUT params (normal case)");

        dbinit();

        TestConfig.setEndpoint("delete_insert_tb_user_v2_procedure_param_out/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC086.json"));

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

        log.info("TC086 - PASSED (COPY_CNT={}, RESULT_MSG={})", copyCnt, resultMsg);
    }

    @Test
    @Order(87)
    @DisplayName("TC087 - OUT 파라메터 프로시저 호출 (존재하지 않는 ID → COPY_CNT=0)")
    void testProcedureOutParam_noData() {
        log.info("Running TC087 - Procedure with OUT params (no matching row)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_procedure_param_out/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC087.json"));

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

        log.info("TC087 - PASSED (COPY_CNT={}, RESULT_MSG={})", copyCnt, resultMsg);
    }

    // ==========================================================
    // TC088 : [BUG FIX] TO_CHAR 날짜 포맷 내 :MI :SS 오탐 방지
    // ==========================================================

    @Test
    @Order(88)
    @DisplayName("TC088 - [BUG FIX] Oracle TO_CHAR 날짜 포맷 내 :MI :SS 가 파라미터로 오인되지 않는다")
    void testOracleDateFormatParamNotMisread() {
        log.info("Running TC088 - Oracle TO_CHAR date format colon fix verification");

        TestConfig.setEndpoint("test_oracle_date_format/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        // Step 1: DELETE + INSERT (TO_CHAR(:UPDATED_TS, 'YYYY-MM-DD HH24:MI:SS') 포함)
        //   버그 수정 전: 'YYYY-MM-DD HH24:MI:SS' 내 :MI :SS 오탐 → ConfigValidationException
        //   버그 수정 후: 리터럴 내 콜론 무시 → INSERT 1건 정상 실행
        ApiResponse insertResp = apiClient.callApi(endpoint, null, readJson("TC_DATE_FORMAT_ORACLE.json"));
        assertThat(insertResp.getJsonResponse()).isNotNull();
        assertThat(insertResp.getCode())
            .as("TO_CHAR 포맷 내 :MI :SS 오탐 시 YAML 로딩 단계에서 S000 이 아닌 오류 코드 반환")
            .isEqualTo("S000");
        assertThat(insertResp.isOperationSuccess("insert_date_format_test")).isTrue();
        assertThat(insertResp.getAffectedRows("insert_date_format_test")).isEqualTo(1);

        // Step 2: SELECT (TO_CHAR(컬럼, 'YYYY-MM-DD HH24:MI:SS') 포함)
        //   버그 수정 전: SELECT 컬럼 포맷 리터럴 내 :MI :SS 오탐 → YAML 로딩 실패
        //   버그 수정 후: FORMATTED_TS = "YYYY-MM-DD HH24:MI:SS" 형식 문자열 반환
        //              OPTIONAL_COL = INSERT 시 TO_CHAR 로 저장된 동일 형식 문자열
        ApiResponse selectResp = apiClient.callApi(endpoint, null, readJson("TC_DATE_FORMAT_ORACLE_SELECT.json"));
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
            .as("INSERT 시 TO_CHAR(:UPDATED_TS, 'YYYY-MM-DD HH24:MI:SS') 로 저장된 OPTIONAL_COL 검증")
            .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");

        log.info("TC088 - PASSED (FORMATTED_TS={}, OPTIONAL_COL={})", formattedTs, optionalCol);
    }

    // ==========================================================
    // TC089 ~ TC094 : CUSTOM-LOG-recordcount 응답 헤더 검증
    //
    // 규칙:
    //   - 최상위 오퍼레이션 중 DELETE/PROCEDURE는 대표 후보에서 스킵하고,
    //     남은 첫 번째 오퍼레이션을 대표로 사용한다 (master/detail이면 master).
    //   - INSERT/UPDATE/UPSERT : 수신 건수(requestRecordCount)
    //   - SELECT                : 응답 건수(responseRecordCount)
    //   - 모든 최상위 오퍼레이션이 DELETE/PROCEDURE뿐이면 : 빈 문자열("")
    //
    // 배포된 registry YAML(각 apiName.yaml)의 정확한 오퍼레이션 순서는 이 테스트
    // 프로젝트에서 직접 확인할 수 없으므로(서버 registry에만 존재), 응답 바디의
    // "operations" 필드(LinkedHashMap 기반 → 실행 순서 보존)에서 DELETE/PROCEDURE를
    // 건너뛰고 첫 번째 대표 오퍼레이션을 스스로 찾아 기대값을 계산하는
    // 자기일관성(self-consistency) 방식으로 검증한다.
    // ==========================================================

    private static final String RECORD_COUNT_HEADER = "CUSTOM-LOG-recordcount";

    /**
     * 응답 바디의 "operations" 중 DELETE/PROCEDURE를 건너뛰고 남은 첫 번째(=대표) 오퍼레이션을 반환한다.
     * 전부 DELETE/PROCEDURE뿐이면 null을 반환한다.
     */
    private Map.Entry<String, JsonNode> representativeOperationEntry(ApiResponse response) {
        JsonNode operations = response.getJsonResponse().path("operations");
        Iterator<Map.Entry<String, JsonNode>> fields = operations.fields();
        assertThat(fields.hasNext())
            .as("응답에 operations가 최소 1건 있어야 함")
            .isTrue();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String actionType = entry.getValue().path("actionType").asText();
            if (!"DELETE".equals(actionType) && !"PROCEDURE".equals(actionType)) {
                return entry;
            }
        }
        return null; // 전부 DELETE/PROCEDURE
    }

    private String expectedRecordCountHeaderFor(Map.Entry<String, JsonNode> representative) {
        if (representative == null) {
            return "";
        }
        JsonNode op = representative.getValue();
        String actionType = op.path("actionType").asText();
        if ("SELECT".equals(actionType)) {
            return String.valueOf(op.path("responseRecordCount").asInt());
        }
        return String.valueOf(op.path("requestRecordCount").asInt()); // INSERT/UPDATE/UPSERT
    }

    /**
     * 응답 바디에서 DELETE/PROCEDURE를 건너뛴 대표 오퍼레이션을 찾아
     * CUSTOM-LOG-recordcount 헤더의 기대값을 계산한다.
     */
    private String computeExpectedRecordCountHeader(ApiResponse response) {
        return expectedRecordCountHeaderFor(representativeOperationEntry(response));
    }

    @Test
    @Order(89)
    @DisplayName("TC089 - CUSTOM-LOG-recordcount 헤더 : SELECT 대표 오퍼레이션 → 응답 건수")
    void testRecordCountHeader_selectRepresentative() {
        log.info("Running TC089 - Record count header for SELECT representative operation");

        TestConfig.setEndpoint("select_tb_user_v2_no_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        Map<String, Object> selectBody = new HashMap<>();
        Map<String, Object> ops = new HashMap<>();
        Map<String, Object> selectOp = new HashMap<>();
        selectOp.put("data", new ArrayList<>());
        ops.put("select_tb_user_v2", selectOp);
        selectBody.put("operations", ops);

        ApiResponse response = apiClient.callApi(endpoint, selectBody, null);

        assertThat(response.isSuccess()).isTrue();
        String header = response.getHeader(RECORD_COUNT_HEADER);
        assertThat(header).as("SELECT 대표 오퍼레이션은 헤더가 항상 세팅되어야 함").isNotNull();
        assertThat(header).isEqualTo(computeExpectedRecordCountHeader(response));
        assertThat(header).isEqualTo(String.valueOf(response.getResultRowCount("select_tb_user_v2")));

        log.info("TC089 - PASSED (recordcount header={})", header);
    }

    @Test
    @Order(90)
    @DisplayName("TC090 - CUSTOM-LOG-recordcount 헤더 : 선행 DELETE는 스킵되고 다음 오퍼레이션 건수 반영")
    void testRecordCountHeader_leadingDeleteSkippedUsesNextOperation() {
        log.info("Running TC090 - Record count header skips leading DELETE, uses next operation");

        TestConfig.setEndpoint("delete_insert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC001.json"));

        assertThat(response.isSuccess()).isTrue();
        String header = response.getHeader(RECORD_COUNT_HEADER);
        assertThat(header).as("선행 DELETE는 스킵되고 다음 오퍼레이션(INSERT) 건수가 반영되어야 함").isNotNull();
        assertThat(header).isEqualTo(computeExpectedRecordCountHeader(response));
        // delete_tb_user_v2(order=1, DELETE)는 스킵되고, insert_tb_user_v2(order=2)의 수신 건수가 반영되어야 함
        assertThat(header).isEqualTo(String.valueOf(response.getJsonResponse()
                .path("operations").path("insert_tb_user_v2").path("requestRecordCount").asInt()));

        log.info("TC090 - PASSED (recordcount header='{}')", header);
    }

    @Test
    @Order(91)
    @DisplayName("TC091 - CUSTOM-LOG-recordcount 헤더 : Master/Detail → Master 건수만 반영 (DELETE 스킵 후)")
    void testRecordCountHeader_masterDetailUsesMasterCountOnly() {
        log.info("Running TC091 - Record count header for master/detail (master count only)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_master-detail/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC019.json"));

        assertThat(response.isSuccess()).isTrue();
        String header = response.getHeader(RECORD_COUNT_HEADER);
        assertThat(header).isNotNull();

        Map.Entry<String, JsonNode> representative = representativeOperationEntry(response);
        assertThat(header).isEqualTo(expectedRecordCountHeaderFor(representative));

        // 대표 오퍼레이션이 INSERT/UPDATE/UPSERT/SELECT 타입일 때만 "master 건수만 반영,
        // detail(insert_tb_user_v2_d1 등 각 2건)은 무시"를 직접 대조 검증할 수 있다.
        if (representative != null) {
            assertThat(header).isEqualTo(String.valueOf(response.getAffectedRows("insert_tb_user_v2")));
            assertThat(header).isNotEqualTo(String.valueOf(response.getAffectedRows("insert_tb_user_v2_d1")));
        }

        log.info("TC091 - PASSED (recordcount header={}, representative={})",
                header, representative == null ? null : representative.getKey());

        // detail 테이블에 행이 남아있으면 이후 단일 테이블 DELETE(FK 제약)가 실패하므로 정리한다.
        dbinit();
    }

    @Test
    @Order(92)
    @DisplayName("TC092 - CUSTOM-LOG-recordcount 헤더 : 멀티마스터 → DELETE 스킵 후 첫 INSERT 건수만 반영")
    void testRecordCountHeader_multiMasterUsesFirstOperationOnly() {
        log.info("Running TC092 - Record count header for multi-master (first non-delete operation only)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_multi/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);

        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC015.json"));

        assertThat(response.isSuccess()).isTrue();
        String header = response.getHeader(RECORD_COUNT_HEADER);
        assertThat(header).isNotNull();
        assertThat(header).isEqualTo(computeExpectedRecordCountHeader(response));

        log.info("TC092 - PASSED (recordcount header='{}')", header);
    }

    @Test
    @Order(93)
    @DisplayName("TC093 - CUSTOM-LOG-recordcount 헤더 : UPDATE/UPSERT 플로우 자기일관성 검증")
    void testRecordCountHeader_updateAndUpsertFlows() {
        log.info("Running TC093 - Record count header for UPDATE/UPSERT flows");

        dbinit();

        TestConfig.setEndpoint("update_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse updateResponse = apiClient.callApi(endpoint, null, readJson("TC059.json"));
        assertThat(updateResponse.isSuccess()).isTrue();
        String updateHeader = updateResponse.getHeader(RECORD_COUNT_HEADER);
        assertThat(updateHeader).isNotNull();
        assertThat(updateHeader).isEqualTo(computeExpectedRecordCountHeader(updateResponse));

        TestConfig.setEndpoint("upsert_tb_user_v2/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse upsertResponse = apiClient.callApi(endpoint, null, readJson("TC062.json"));
        assertThat(upsertResponse.isSuccess()).isTrue();
        String upsertHeader = upsertResponse.getHeader(RECORD_COUNT_HEADER);
        assertThat(upsertHeader).isNotNull();
        assertThat(upsertHeader).isEqualTo(computeExpectedRecordCountHeader(upsertResponse));

        log.info("TC093 - PASSED (update header='{}', upsert header='{}')", updateHeader, upsertHeader);
    }

    @Test
    @Order(94)
    @DisplayName("TC094 - CUSTOM-LOG-recordcount 헤더 : DELETE WHERE IN 플로우 자기일관성 검증")
    void testRecordCountHeader_deleteWithParamFlow() {
        log.info("Running TC094 - Record count header for DELETE WHERE IN flow");

        dbinit();

        TestConfig.setEndpoint("delete_tb_user_v2_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        ApiResponse response = apiClient.callApi(endpoint, null, readJson("TC065.json"));

        assertThat(response.isSuccess()).isTrue();
        String header = response.getHeader(RECORD_COUNT_HEADER);
        assertThat(header).isNotNull();
        assertThat(header).isEqualTo(computeExpectedRecordCountHeader(response));

        log.info("TC094 - PASSED (recordcount header='{}')", header);
    }

    @Test
    @Order(95)
    @DisplayName("TC095 - 프로시저 addBatch 플러시 경계 검증 (파라메터 전부 IN, batch_size 초과 150건)")
    void testProcedure_addBatchFlushBoundary() {
        log.info("Running TC095 - Procedure addBatch flush boundary (150 rows, all-IN params)");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_procedure_param/dbconnector");
        endpoint = TestConfig.getEndpoint(endpoint);
        String inputJson = readJson("TC095.json");

        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

        // Then
        // 기본 batch_size(100)보다 많은 150건을 addBatch로 묶어 실행 → 중간 flush(100건) +
        // 잔여 flush(50건)가 모두 정상적으로 반영되어야 함
        // 참고: PROCEDURE 오퍼레이션은 affectedRows를 채우지 않으므로(항상 0, 기존 동작과 동일)
        // 실제 처리 건수는 successCount로 검증한다.
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("S000");
        assertThat(response.isOperationSuccess("insert_tb_user_v2")).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(150);
        assertThat(response.isOperationSuccess("proc_tb_user_v2")).isTrue();
        assertThat(response.getOperation("proc_tb_user_v2").get("successCount").asInt()).isEqualTo(150);

        log.info("TC095 - PASSED");
    }

    private void dbinit() {
        log.info("DB Initialize");

        TestConfig.setEndpoint("delete_insert_tb_user_v2_master-detail/dbconnector");

        endpoint = TestConfig.getEndpoint(endpoint);

        String inputJson = TestDataLoader.loadJson("DBInit.json");
        // When
        ApiResponse response = apiClient.callApi(endpoint, null, inputJson);

    }
}
