package siis.jdbc.api.test.data;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 테스트 데이터 생성기
 */
public class TestDataGenerator {
    
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    
    /**
     * 사용자 데이터 생성
     * 
     * @param count 생성할 데이터 수
     * @param startId 시작 ID
     * @return 사용자 데이터 리스트
     */
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter ISO_TZ = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public static List<Map<String, Object>> generateUsers(int count, int startId , String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    // 1) 식별자
                    user.put("USER_ID", "ID_" + userId);

                    // 2) 문자열 계열 (특수문자 집중 테스트)
                    user.put("USER_NAME", "테스터_" + userId);
                    user.put("USER_NICK", "NICK_" + (userId % 100)); 
                    
                    // NVARCHAR2: 다국어, 이모지, 특수기호 테스트
                    // 🚩 포인트: 유니코드 보충 평면 문자(이모지) 및 복잡한 한자
                    user.put("USER_CODE", "🔑_韓_" + userId + "_∑_π_Ω_★_㏘"); 

                    // CLOB: 대용량 + 개행 + 특수문자 조합
                    StringBuilder descBuilder = new StringBuilder();
                    descBuilder.append("--- 복합 특수문자 리포트 ---\n");
                    descBuilder.append("줄바꿈(LF) 및 탭(TAB)\t체크\n");
                    descBuilder.append("이모지: 🚀, 💡, 🛠️, 📉\n");
                    descBuilder.append("수학/기술: √, ∞, ±, ≠, ⊆, ⊗\n");
                    descBuilder.append("괄호/인용: 「」, 『』, 〈〉, “”, ‘’\n");
                    descBuilder.append("데이터 번호: ").append(userId);
                    user.put("USER_DESC", descBuilder.toString());

                    // 3) 숫자 계열 (이전과 동일)
                    user.put("USER_AGE", 20 + (i % 80));
                    user.put("USER_COUNT", 1000 + i);
                    user.put("USER_BIGINT", 922337203685477580L + i);
                    user.put("USER_SCORE", 88.1234);
                    user.put("USER_RATE", 0.12345678f);
                    user.put("USER_RATIO", 1.2345f);
                    user.put("USER_WEIGHT", 75.4321d);

                    // 4) 날짜/시간 계열 (이전과 동일)
                    user.put("CREATED_DATE", LocalDateTime.now().format(ISO_DATE));
                    user.put("UPDATED_TS", LocalDateTime.now().format(ISO_TIMESTAMP));
                    user.put("UPDATED_TZ", OffsetDateTime.now().format(ISO_TZ));
                    user.put("UPDATED_LTZ", OffsetDateTime.now().format(ISO_TZ));

                    // 5) 상태값
                    user.put("IS_ACTIVE", i % 2 == 0 ? "Y" : "N");
                    user.put("IS_DELETED", "N");

                    // 6) 바이너리
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("BINARY_" + userId).getBytes()));

                    // 7) 구조형/특수 타입 (JSON 내부 특수문자)
                    user.put("META_JSON", String.format("{\"desc\": \"특수문자 텍스트: ★\", \"val\": %d}", i));
                    user.put("TAGS", "java,∑,Ω,test");
                    user.put("USER_XML", String.format("<user><msg>안녕 🚀</msg><id>%d</id></user>", userId));

                    // 8) 옵션
                    user.put("OPTIONAL_COL", testCase);

                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
    

    /**
     * DELETE용 ID 데이터 생성
     */
    public static List<Map<String, Object>> generateDeleteIds(int startId, int endId) {
        return IntStream.range(startId, endId + 1)
            .mapToObj(id -> {
                Map<String, Object> data = new HashMap<>();
                data.put("USER_ID", String.valueOf(id));
                return data;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * NULL 테스트 데이터 생성
     */
    public static List<Map<String, Object>> generateUserWithNulls(String userId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, 1)
                .mapToObj(i -> {
                    Map<String, Object> user = new LinkedHashMap<>();

                    // 1) 식별자
                    user.put("USER_ID", null);

                    // 2) 문자열 계열 (특수문자 집중 테스트)
                    user.put("USER_NAME", null);
                    user.put("USER_NICK", "NICK_1"); 
                    
                    // NVARCHAR2: 다국어, 이모지, 특수기호 테스트
                    // 🚩 포인트: 유니코드 보충 평면 문자(이모지) 및 복잡한 한자
                    user.put("USER_CODE", "🔑_韓_" + userId + "_∑_π_Ω_★_㏘"); 

                    // CLOB: 대용량 + 개행 + 특수문자 조합
                    StringBuilder descBuilder = new StringBuilder();
                    descBuilder.append("--- 복합 특수문자 리포트 ---\n");
                    descBuilder.append("줄바꿈(LF) 및 탭(TAB)\t체크\n");
                    descBuilder.append("이모지: 🚀, 💡, 🛠️, 📉\n");
                    descBuilder.append("수학/기술: √, ∞, ±, ≠, ⊆, ⊗\n");
                    descBuilder.append("괄호/인용: 「」, 『』, 〈〉, “”, ‘’\n");
                    descBuilder.append("데이터 번호: ").append(userId);
                    user.put("USER_DESC", descBuilder.toString());

                    // 3) 숫자 계열 (이전과 동일)
                    user.put("USER_AGE", 20 + (i % 80));
                    user.put("USER_COUNT", 1000 + i);
                    user.put("USER_BIGINT", 922337203685477580L + i);
                    user.put("USER_SCORE", 88.1234);
                    user.put("USER_RATE", 0.12345678f);
                    user.put("USER_RATIO", 1.2345f);
                    user.put("USER_WEIGHT", 75.4321d);

                    // 4) 날짜/시간 계열 (이전과 동일)
                    user.put("CREATED_DATE", LocalDateTime.now().format(ISO_DATE));
                    user.put("UPDATED_TS", LocalDateTime.now().format(ISO_TIMESTAMP));
                    user.put("UPDATED_TZ", OffsetDateTime.now().format(ISO_TZ));
                    user.put("UPDATED_LTZ", OffsetDateTime.now().format(ISO_TZ));

                    // 5) 상태값
                    user.put("IS_ACTIVE", i % 2 == 0 ? "Y" : "N");
                    user.put("IS_DELETED", "N");

                    // 6) 바이너리
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("BINARY_" + userId).getBytes()));

                    // 7) 구조형/특수 타입 (JSON 내부 특수문자)
                    user.put("META_JSON", String.format("{\"desc\": \"특수문자 텍스트: ★\", \"val\": %d}", i));
                    user.put("TAGS", "java,∑,Ω,test");
                    user.put("USER_XML", String.format("<user><msg>안녕 🚀</msg><id>%d</id></user>", 1));

                    // 8) 옵션
                    user.put("OPTIONAL_COL", testCase);

                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
    
    /**
     * 대용량 CLOB/BLOB 데이터 생성
     */
    
    public static List<Map<String, Object>> generateUserWithLargeData(int count, int startId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    // 1) 식별자
                    user.put("USER_ID", "ID_" + userId);

                    // 2) 문자열 계열 (특수문자 집중 테스트)
                    user.put("USER_NAME", "테스터_" + userId);
                    user.put("USER_NICK", "NICK_" + (userId % 100)); 
                    
                    // NVARCHAR2: 다국어, 이모지, 특수기호 테스트
                    // 🚩 포인트: 유니코드 보충 평면 문자(이모지) 및 복잡한 한자
                    user.put("USER_CODE", "🔑_韓_" + userId + "_∑_π_Ω_★_㏘"); 
                    //대용량
                    user.put("USER_DESC", "대용량 CLOB 테스트 데이터\n".repeat(1000));

                    // 3) 숫자 계열 (이전과 동일)
                    user.put("USER_AGE", 20 + (i % 80));
                    user.put("USER_COUNT", 1000 + i);
                    user.put("USER_BIGINT", 922337203685477580L + i);
                    user.put("USER_SCORE", 88.1234);
                    user.put("USER_RATE", 0.12345678f);
                    user.put("USER_RATIO", 1.2345f);
                    user.put("USER_WEIGHT", 75.4321d);

                    // 4) 날짜/시간 계열 (이전과 동일)
                    user.put("CREATED_DATE", LocalDateTime.now().format(ISO_DATE));
                    user.put("UPDATED_TS", LocalDateTime.now().format(ISO_TIMESTAMP));
                    user.put("UPDATED_TZ", OffsetDateTime.now().format(ISO_TZ));
                    user.put("UPDATED_LTZ", OffsetDateTime.now().format(ISO_TZ));

                    // 5) 상태값
                    user.put("IS_ACTIVE", i % 2 == 0 ? "Y" : "N");
                    user.put("IS_DELETED", "N");

                    // 6) 바이너리
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("BINARY_" + userId).getBytes()));

                    // 7) 구조형/특수 타입 (JSON 내부 특수문자)
                    user.put("META_JSON", String.format("{\"desc\": \"특수문자 텍스트: ★\", \"val\": %d}", i));
                    user.put("TAGS", "java,∑,Ω,test");
                    user.put("USER_XML", String.format("<user><msg>안녕 🚀</msg><id>%d</id></user>", userId));

                    // 8) 옵션
                    user.put("OPTIONAL_COL", testCase);

                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static List<Map<String, Object>> generateUserWithSpecialChars(int count, int startId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    // 1) 식별자
                    user.put("USER_ID", "ID_" + userId);

                    // 2) 문자열 계열 (특수문자 집중 테스트)
                    user.put("USER_NAME", "!@#$%^&*()_+-={}[]|\\\\:;\\\"'<>,.?/~`");
                    user.put("USER_NICK", "이모😀🎉SQL'--"); 
                    
                    // NVARCHAR2: 다국어, 이모지, 특수기호 테스트
                    // 🚩 포인트: 유니코드 보충 평면 문자(이모지) 및 복잡한 한자
                    user.put("USER_CODE", "🔑_韓_" + userId + "_∑_π_Ω_★_㏘"); 
                    // CLOB: 대용량 + 개행 + 특수문자 조합
                    StringBuilder descBuilder = new StringBuilder();
                    descBuilder.append("--- 복합 특수문자 리포트 ---\n");
                    descBuilder.append("줄바꿈(LF) 및 탭(TAB)\t체크\n");
                    descBuilder.append("이모지: 🚀, 💡, 🛠️, 📉\n");
                    descBuilder.append("수학/기술: √, ∞, ±, ≠, ⊆, ⊗\n");
                    descBuilder.append("괄호/인용: 「」, 『』, 〈〉, “”, ‘’\n");
                    descBuilder.append("데이터 번호: ").append(userId);
                    user.put("USER_DESC", descBuilder.toString());

                    // 3) 숫자 계열 (이전과 동일)
                    user.put("USER_AGE", 20 + (i % 80));
                    user.put("USER_COUNT", 1000 + i);
                    user.put("USER_BIGINT", 922337203685477580L + i);
                    user.put("USER_SCORE", 88.1234);
                    user.put("USER_RATE", 0.12345678f);
                    user.put("USER_RATIO", 1.2345f);
                    user.put("USER_WEIGHT", 75.4321d);

                    // 4) 날짜/시간 계열 (이전과 동일)
                    user.put("CREATED_DATE", LocalDateTime.now().format(ISO_DATE));
                    user.put("UPDATED_TS", LocalDateTime.now().format(ISO_TIMESTAMP));
                    user.put("UPDATED_TZ", OffsetDateTime.now().format(ISO_TZ));
                    user.put("UPDATED_LTZ", OffsetDateTime.now().format(ISO_TZ));

                    // 5) 상태값
                    user.put("IS_ACTIVE", i % 2 == 0 ? "Y" : "N");
                    user.put("IS_DELETED", "N");

                    // 6) 바이너리
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("BINARY_" + userId).getBytes()));

                    // 7) 구조형/특수 타입 (JSON 내부 특수문자)
                    user.put("META_JSON", String.format("{\"desc\": \"특수문자 텍스트: ★\", \"val\": %d}", i));
                    user.put("TAGS", "java,∑,Ω,test");
                    user.put("USER_XML", String.format("<user><msg>안녕 🚀</msg><id>%d</id></user>", userId));

                    // 8) 옵션
                    user.put("OPTIONAL_COL", testCase);

                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
       
    /*
     * 최소값 테스트
     */
    public static List<Map<String, Object>> generateMinimumValues(int count, int startId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    // 식별자
                    user.put("USER_ID", userId);
                    
                    // 문자열 계열 - 최소 길이
                    user.put("USER_NAME", "A");                    // 1글자
                    user.put("USER_NICK", "B");                    // CHAR(20) 최소
                    user.put("USER_CODE", "C");                    // NVARCHAR2 최소
                    user.put("USER_DESC", "D");                    // CLOB 최소
                    
                    user.put("USER_AGE", 0);
                    user.put("USER_COUNT", 0);
                    user.put("USER_BIGINT", 0L);
                    
                    // 실수형 - 매우 작은 양수 (Oracle 호환)
                    user.put("USER_SCORE", 0.0001);      // NUMBER(15,4)의 최소 단위
                    user.put("USER_RATE", 0.000001f);    // 충분히 작은 값
                    user.put("USER_RATIO", 0.000001f);    // 충분히 작은 값
                    user.put("USER_WEIGHT", 0.000001d);  // 충분히 작은 값
                    
                    // 날짜/시간 - 최소 (오라클 최소 날짜: 4712 BC 1월 1일)
                    user.put("CREATED_DATE", "1900-01-01");        // 실용적 최소
                    user.put("UPDATED_TS", "1900-01-01T00:00:00");
                    user.put("UPDATED_TZ", "1900-01-01T00:00:00+00:00");
                    user.put("UPDATED_LTZ", "1900-01-01T00:00:00+00:00");
                    
                    // 상태값
                    user.put("IS_ACTIVE", "N");
                    user.put("IS_DELETED", "N");
                    
                    // 바이너리 - 최소
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString("A".getBytes()));
                    
                    // 구조형 - 최소
                    user.put("META_JSON", "{}");                   // 빈 JSON
                    user.put("TAGS", "");                          // 빈 태그
                    user.put("USER_XML", "<r/>");                  // 최소 XML
                    
                    // NULL 컬럼
                    user.put("OPTIONAL_COL", testCase);
                    
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }    
    
    /*
     * 최대값 테스트
     */
    public static List<Map<String, Object>> generateMaximumValues(int count, int startId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    // 식별자
                    user.put("USER_ID", userId);
                    
                    // 문자열 계열 - 최대 길이
                    user.put("USER_NAME", "가".repeat(30));       // VARCHAR2(100) 최대
                    user.put("USER_NICK", "나".repeat(6));        // CHAR(20) 최대
                    user.put("USER_CODE", "다".repeat(50));        // NVARCHAR2(50) 최대
                    user.put("USER_DESC", "라".repeat(10000));     // CLOB 대용량
                    
                    // 숫자 계열 - 최대값
                 // 1) 숫자 계열 - 현실적인 최대값으로 수정
                    user.put("USER_AGE", 999); 
                    user.put("USER_COUNT", 9999999999L);
                    user.put("USER_BIGINT", 9223372036854775807L);

                    // 2) 부동 소수점 - Oracle NUMBER 지수 한계(10^125)를 고려한 안전한 값
                    // Float.MAX_VALUE나 Double.MAX_VALUE 대신 아래처럼 테스트용 큰 값을 사용하세요.
                    user.put("USER_SCORE", 99999999999.9999);
                    user.put("USER_RATE", 1.0E37f);        // FLOAT 안전 범위
                    user.put("USER_RATIO", 3.4E38f);       // BINARY_FLOAT 최대치 근접 (Float.MAX_VALUE와 유사)
                    user.put("USER_WEIGHT", 1.0E125d);     // BINARY_DOUBLE 안전 범위 (NUMBER 호환성 고려)
                    
                    // 날짜/시간 - 최대 (오라클 최대: 9999년 12월 31일)
                    user.put("CREATED_DATE", "9999-12-31");
                    user.put("UPDATED_TS", "9999-12-31T23:59:59");
                    user.put("UPDATED_TZ", "9999-12-31T23:59:59+14:00");  // 최대 타임존
                    user.put("UPDATED_LTZ", "9999-12-31T23:59:59+14:00");
                    
                    // 상태값
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "Y");
                    
                    // 바이너리 - 대용량
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(
                        "X".repeat(10000).getBytes()));
                    
                    // 구조형 - 복잡한 구조
                    StringBuilder json = new StringBuilder("{");
                    for (int j = 0; j < 100; j++) {
                        json.append("\"key").append(i).append("\":\"value").append(i).append("\"");
                        if (i < 99) json.append(",");
                    }
                    json.append("}");
                    user.put("META_JSON", json.toString());
                    
                    user.put("TAGS", String.join(",", Collections.nCopies(100, "tag")));
                    user.put("USER_XML", "<root>" + "<item>data</item>".repeat(100) + "</root>");
                    
                    // 옵션 - 최대 길이
                    user.put("OPTIONAL_COL", testCase);
                    
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }     
    
    /*
     * 제로값 테스트
     */
    public static List<Map<String, Object>> generateZeroValues(int count, int startId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    user.put("USER_ID", userId);
                    user.put("USER_NAME", "ZERO테스트");
                    user.put("USER_NICK", "ZERO");
                    user.put("USER_CODE", "0");
                    user.put("USER_DESC", "0");
                    
                    // 모든 숫자를 0으로
                    user.put("USER_AGE", 0);
                    user.put("USER_COUNT", 0);
                    user.put("USER_BIGINT", 0L);
                    user.put("USER_SCORE", 0.0);
                    user.put("USER_RATE", 0.0f);
                    user.put("USER_RATIO", 0.0f);
                    user.put("USER_WEIGHT", 0.0d);
                    
                    user.put("CREATED_DATE", LocalDateTime.now().format(ISO_DATE));
                    user.put("UPDATED_TS", LocalDateTime.now().format(ISO_TIMESTAMP));
                    user.put("UPDATED_TZ", OffsetDateTime.now().format(ISO_TZ));
                    user.put("UPDATED_LTZ", OffsetDateTime.now().format(ISO_TZ));
                    
                    user.put("IS_ACTIVE", "N");
                    user.put("IS_DELETED", "N");
                    // BLOB은 0Byte 일때 에러 발생
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString("1".getBytes()));
                    user.put("META_JSON", "{\"val\":0}");
                    user.put("TAGS", "");
                    user.put("USER_XML", "<zero/>");
                    user.put("OPTIONAL_COL", testCase);
                    
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }         
        
    /*
     * 유니코드 테스트
     */
    public static List<Map<String, Object>> generateUnicodeExtreme(int count, int startId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    user.put("USER_ID", userId);
                    user.put("USER_NAME", "가나다라마바사아자차카타파하");
                    user.put("USER_NICK", "😀😃😄😁😆");
                    user.put("USER_CODE", "🔑韓國中國日本🚀💡🛠️");
                    user.put("USER_DESC", 
                        "한글: 가나다\n" +
                        "한자: 韓中日\n" +
                        "이모지: 🚀💡🎉\n" +
                        "수학: ∑∫∂√∞±≠\n" +
                        "기호: ★☆♠♣♥♦\n" +
                        "괄호: 「」『』〈〉");
                    
                    // 모든 숫자를 0으로
                    user.put("USER_AGE", 0);
                    user.put("USER_COUNT", 0);
                    user.put("USER_BIGINT", 0L);
                    user.put("USER_SCORE", 0.0);
                    user.put("USER_RATE", 0.0f);
                    user.put("USER_RATIO", 0.0f);
                    user.put("USER_WEIGHT", 0.0d);
                    
                    user.put("CREATED_DATE", LocalDateTime.now().format(ISO_DATE));
                    user.put("UPDATED_TS", LocalDateTime.now().format(ISO_TIMESTAMP));
                    user.put("UPDATED_TZ", OffsetDateTime.now().format(ISO_TZ));
                    user.put("UPDATED_LTZ", OffsetDateTime.now().format(ISO_TZ));
                    
                    user.put("IS_ACTIVE", "");
                    user.put("IS_DELETED", "");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(
                            "🎯🎨🎭".getBytes()));
                    user.put("META_JSON", "{}");
                    user.put("TAGS", "");
                    user.put("USER_XML", "<zero/>");
                    user.put("OPTIONAL_COL", testCase);
                    
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }  
    
    /*
     * 오버플로우 테스트 (최대값 초과 시도)
     */
    public static List<Map<String, Object>> generateOverflowTest(int count, int startId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    user.put("USER_ID", userId);
                    user.put("USER_NAME", "오버플로우테스트");
                    user.put("USER_NICK", "가".repeat(30));        // CHAR(20) 초과
                    user.put("USER_CODE", "나".repeat(60));        // NVARCHAR2(50) 초과
                    user.put("USER_DESC", "다".repeat(100000));    // 매우 큰 CLOB
                    
                    // 숫자 오버플로우 시도
                    user.put("USER_AGE", 1000);                    // NUMBER(3) 초과
                    user.put("USER_COUNT", 99999999999L);          // NUMBER(10) 초과
                    user.put("USER_BIGINT", Long.MAX_VALUE);
                    user.put("USER_SCORE", 999999999999.9999);     // NUMBER(15,4) 초과
                    user.put("USER_RATE", Float.MAX_VALUE * 2);
                    user.put("USER_RATIO", Float.MAX_VALUE);
                    user.put("USER_WEIGHT", Double.MAX_VALUE);
                    
                    user.put("CREATED_DATE", LocalDateTime.now().format(ISO_DATE));
                    user.put("UPDATED_TS", LocalDateTime.now().format(ISO_TIMESTAMP));
                    user.put("UPDATED_TZ", OffsetDateTime.now().format(ISO_TZ));
                    user.put("UPDATED_LTZ", OffsetDateTime.now().format(ISO_TZ));
                    
                    user.put("IS_ACTIVE", "YES");                  // CHAR(1) 초과
                    user.put("IS_DELETED", "NO");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(
                        "X".repeat(100000).getBytes()));
                    user.put("META_JSON", "{\"overflow\":true}");
                    user.put("TAGS", "tag,".repeat(1000));
                    user.put("USER_XML", "<data>" + "item".repeat(10000) + "</data>");
                    user.put("OPTIONAL_COL", testCase);
                    
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }    
    
    /*
     * 언더플로우 테스트 (음수 등)
     */
    public static List<Map<String, Object>> generateUnderflowTest(int count, int startId,String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int userId = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();

                    user.put("USER_ID", userId);
                    user.put("USER_NAME", "언더플로우테스트");
                    user.put("USER_NICK", "NEG");
                    user.put("USER_CODE", "NEGATIVE");
                    user.put("USER_DESC", "음수 테스트");
                    
                    // 음수 테스트
                    user.put("USER_AGE", -1);
                    user.put("USER_COUNT", -999999);
                    user.put("USER_BIGINT", Long.MIN_VALUE);
                    user.put("USER_SCORE", -99999.9999);
                    user.put("USER_RATE", -Float.MAX_VALUE);
                    user.put("USER_RATIO", Float.MIN_VALUE);
                    user.put("USER_WEIGHT", -Double.MAX_VALUE);
                    
                    user.put("CREATED_DATE", "0001-01-01");        // 매우 옛날
                    user.put("UPDATED_TS", "0001-01-01T00:00:00");
                    user.put("UPDATED_TZ", "0001-01-01T00:00:00+12:00");
                    user.put("UPDATED_LTZ", "0001-01-01T00:00:00+12:00");
                    
                    
                    user.put("IS_ACTIVE", "N");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString("1".getBytes()));
                    user.put("META_JSON", "{\"negative\":-1}");
                    user.put("TAGS", "negative");
                    user.put("USER_XML", "<negative>-1</negative>");
                    user.put("OPTIONAL_COL", testCase);
                    
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 다국어 데이터 생성 (아랍어/히브리어/러시아어/일본어/중국어)
     */
    public static List<Map<String, Object>> generateMultilingual(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "ML_" + id);
                    user.put("USER_NAME", "محمد_" + id + "، أحمد");
                    user.put("USER_NICK", "שלום_" + id);
                    user.put("USER_CODE", "Привет_" + id + "мир");
                    user.put("USER_DESC", "こんにちは_" + id + "世界" + " Chinese: 你好世界_" + id);
                    user.put("USER_AGE", 25);
                    user.put("USER_COUNT", 1);
                    user.put("USER_BIGINT", 100L + id);
                    user.put("USER_SCORE", 1.5);
                    user.put("USER_RATE", 0.5f);
                    user.put("USER_RATIO", 1.5f);
                    user.put("USER_WEIGHT", 50.0d);
                    user.put("CREATED_DATE", "2026-01-01");
                    user.put("UPDATED_TS", "2026-01-01T00:00:00.000");
                    user.put("UPDATED_TZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("ML_" + id).getBytes()));
                    user.put("META_JSON", "{\"lang\": \"multilingual\", \"id\": " + id + "}");
                    user.put("TAGS", "arabic,hebrew,russian,japanese,chinese");
                    user.put("USER_XML", "<user><id>ML_" + id + "</id><lang>multilingual</lang></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 4바이트 이모지 및 복합 유니코드 데이터 생성
     */
    public static List<Map<String, Object>> generateEmojiData(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "EMJ_" + id);
                    user.put("USER_NAME", "😀😂🎉🔥💯_" + id);
                    user.put("USER_NICK", "EMOJI_" + id);
                    user.put("USER_CODE", "🌍🌎🌏🚀💡_" + id);
                    user.put("USER_DESC", "이모지 테스트 🎊🎋🎌🎍🎎🎏\n4바이트 이모지: 𝄞𝄠𝀣\n복합: 👨‍👩‍👧‍👦");
                    user.put("USER_AGE", 30);
                    user.put("USER_COUNT", 1);
                    user.put("USER_BIGINT", 200L);
                    user.put("USER_SCORE", 2.5);
                    user.put("USER_RATE", 0.75f);
                    user.put("USER_RATIO", 2.0f);
                    user.put("USER_WEIGHT", 60.0d);
                    user.put("CREATED_DATE", "2026-01-15");
                    user.put("UPDATED_TS", "2026-01-15T12:00:00.000");
                    user.put("UPDATED_TZ", "2026-01-15T12:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-01-15T12:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("EMOJI_" + id).getBytes()));
                    user.put("META_JSON", "{\"type\": \"emoji\", \"count\": 4}");
                    user.put("TAGS", "emoji,4byte,unicode");
                    user.put("USER_XML", "<user><id>EMJ_" + id + "</id></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * HTML/XML 특수문자 데이터 생성
     */
    public static List<Map<String, Object>> generateHtmlXmlSpecialChars(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "HTML_" + id);
                    String rawName = "<b>Bold</b>_&amp;_<script>alert(1)</script>_" + id;
                    user.put("USER_NAME", rawName.length() > 100 ? rawName.substring(0, 100) : rawName);
                    user.put("USER_NICK", "&lt;nick&gt;_" + id);
                    user.put("USER_CODE", "<code>&lt;/&gt;&amp;\"'</code>_" + id);
                    user.put("USER_DESC", "HTML tags: <div class=\"test\">content</div>\nXML: <root><child attr='val'>text</child></root>\n&lt;&gt;&amp;&quot;&apos;");
                    user.put("USER_AGE", 35);
                    user.put("USER_COUNT", 1);
                    user.put("USER_BIGINT", 300L);
                    user.put("USER_SCORE", 3.5);
                    user.put("USER_RATE", 0.25f);
                    user.put("USER_RATIO", 1.0f);
                    user.put("USER_WEIGHT", 70.0d);
                    user.put("CREATED_DATE", "2026-02-01");
                    user.put("UPDATED_TS", "2026-02-01T00:00:00.000");
                    user.put("UPDATED_TZ", "2026-02-01T00:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-02-01T00:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("<html>_" + id + "</html>").getBytes()));
                    user.put("META_JSON", "{\"html\": \"<div>test</div>\"}");
                    user.put("TAGS", "html,xml,special");
                    user.put("USER_XML", "<user><id>" + id + "</id><html>&lt;b&gt;test&lt;/b&gt;</html></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * SQL 예약어를 값으로 갖는 데이터 생성
     */
    public static List<Map<String, Object>> generateSqlKeywordData(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "SQL_KW_" + id);
                    user.put("USER_NAME", "SELECT FROM WHERE_" + id);
                    user.put("USER_NICK", "DROP_TABLE_" + id);
                    user.put("USER_CODE", "INSERT UPDATE DELETE_" + id);
                    user.put("USER_DESC", "SQL keywords: SELECT * FROM dual WHERE 1=1;\nDROP TABLE test;\n-- comment\n/* block comment */");
                    user.put("USER_AGE", 40);
                    user.put("USER_COUNT", 1);
                    user.put("USER_BIGINT", 400L);
                    user.put("USER_SCORE", 4.0);
                    user.put("USER_RATE", 0.1f);
                    user.put("USER_RATIO", 0.5f);
                    user.put("USER_WEIGHT", 80.0d);
                    user.put("CREATED_DATE", "2026-03-01");
                    user.put("UPDATED_TS", "2026-03-01T00:00:00.000");
                    user.put("UPDATED_TZ", "2026-03-01T00:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-03-01T00:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("SQL_" + id).getBytes()));
                    user.put("META_JSON", "{\"sql\": \"SELECT 1 FROM dual\"}");
                    user.put("TAGS", "sql,keyword,injection");
                    user.put("USER_XML", "<user><id>SQL_KW_" + id + "</id></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 숫자 정밀도 경계값 데이터 생성
     */
    public static List<Map<String, Object>> generateNumericPrecision(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "NUM_" + id);
                    user.put("USER_NAME", "숫자정밀도_" + id);
                    user.put("USER_NICK", "NUMPREC_" + id);
                    user.put("USER_CODE", "NUM_PREC_" + id);
                    user.put("USER_DESC", "Numeric precision test");
                    user.put("USER_AGE", 99);
                    user.put("USER_COUNT", 2147483647L); // INT max (MSSQL INT / Oracle NUMBER(10) compatible)
                    user.put("USER_BIGINT", 999999999999999999L);
                    user.put("USER_SCORE", 9999.9999);
                    user.put("USER_RATE", 0.99999999f);
                    user.put("USER_RATIO", 9.9999f);
                    user.put("USER_WEIGHT", 9999.999d);
                    user.put("CREATED_DATE", "2026-01-01");
                    user.put("UPDATED_TS", "2026-01-01T00:00:00.000");
                    user.put("UPDATED_TZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("NUM_" + id).getBytes()));
                    user.put("META_JSON", "{\"type\": \"numeric_precision\"}");
                    user.put("TAGS", "numeric,precision,boundary");
                    user.put("USER_XML", "<user><id>NUM_" + id + "</id></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 빈 문자열 CLOB 데이터 생성
     */
    public static List<Map<String, Object>> generateEmptyClobData(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "ECLOB_" + id);
                    user.put("USER_NAME", "빈CLOB_" + id);
                    user.put("USER_NICK", "EMPTYCLOB_" + id);
                    user.put("USER_CODE", "EC_" + id);
                    user.put("USER_DESC", null); // Oracle: empty string = NULL for CLOB
                    user.put("USER_AGE", 20);
                    user.put("USER_COUNT", 1);
                    user.put("USER_BIGINT", 100L);
                    user.put("USER_SCORE", 1.0);
                    user.put("USER_RATE", 0.1f);
                    user.put("USER_RATIO", 1.0f);
                    user.put("USER_WEIGHT", 50.0d);
                    user.put("CREATED_DATE", "2026-01-01");
                    user.put("UPDATED_TS", "2026-01-01T00:00:00.000");
                    user.put("UPDATED_TZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("EC_" + id).getBytes()));
                    user.put("META_JSON", "{\"type\": \"empty_clob\"}");
                    user.put("TAGS", "clob,empty");
                    user.put("USER_XML", "<user><id>ECLOB_" + id + "</id></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 대용량 CLOB (4000자) 데이터 생성
     */
    public static List<Map<String, Object>> generateLargeClob4000(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "L4K_" + id);
                    user.put("USER_NAME", "대용량CLOB_" + id);
                    user.put("USER_NICK", "LARGE4K_" + id);
                    user.put("USER_CODE", "L4K_" + id);
                    user.put("USER_DESC", "A".repeat(4000));
                    user.put("USER_AGE", 20);
                    user.put("USER_COUNT", 1);
                    user.put("USER_BIGINT", 100L);
                    user.put("USER_SCORE", 1.0);
                    user.put("USER_RATE", 0.1f);
                    user.put("USER_RATIO", 1.0f);
                    user.put("USER_WEIGHT", 50.0d);
                    user.put("CREATED_DATE", "2026-01-01");
                    user.put("UPDATED_TS", "2026-01-01T00:00:00.000");
                    user.put("UPDATED_TZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("L4K_" + id).getBytes()));
                    user.put("META_JSON", "{\"type\": \"large_clob_4k\", \"size\": 4000}");
                    user.put("TAGS", "clob,large,4000");
                    user.put("USER_XML", "<user><id>L4K_" + id + "</id></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 전체 음수값 데이터 생성
     */
    public static List<Map<String, Object>> generateNegativeValues(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "NEG_" + id);
                    user.put("USER_NAME", "음수값_" + id);
                    user.put("USER_NICK", "NEGATIVE_" + id);
                    user.put("USER_CODE", "NEG_" + id);
                    user.put("USER_DESC", "All negative numeric values test");
                    user.put("USER_AGE", -99);
                    user.put("USER_COUNT", -9999999);
                    user.put("USER_BIGINT", -9999999999999999L);
                    user.put("USER_SCORE", -9999.9999);
                    user.put("USER_RATE", -0.99999999f);
                    user.put("USER_RATIO", -9.9999f);
                    user.put("USER_WEIGHT", -9999.999d);
                    user.put("CREATED_DATE", "2026-01-01");
                    user.put("UPDATED_TS", "2026-01-01T00:00:00.000");
                    user.put("UPDATED_TZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("NEG_" + id).getBytes()));
                    user.put("META_JSON", "{\"type\": \"negative\"}");
                    user.put("TAGS", "negative,numeric");
                    user.put("USER_XML", "<user><id>NEG_" + id + "</id></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * MSSQL BIT/boolean 필드 테스트 데이터 생성
     */
    public static List<Map<String, Object>> generateMssqlBitBooleanData(int count, int startId, String testCase) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            result = IntStream.range(0, count)
                .mapToObj(i -> {
                    int id = startId + i;
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("USER_ID", "BIT_" + id);
                    user.put("USER_NAME", "BIT_TEST_" + id);
                    user.put("USER_NICK", "BIT_" + id);
                    user.put("USER_CODE", "BIT_CODE_" + id);
                    user.put("USER_DESC", "MSSQL BIT boolean field test");
                    user.put("USER_AGE", 30);
                    user.put("USER_COUNT", 1);
                    user.put("USER_BIGINT", 100L);
                    user.put("USER_SCORE", 1.0);
                    user.put("USER_RATE", 0.5f);
                    user.put("USER_RATIO", 1.0f);
                    user.put("USER_WEIGHT", 50.0d);
                    user.put("CREATED_DATE", "2026-01-01");
                    user.put("UPDATED_TS", "2026-01-01T00:00:00.000");
                    user.put("UPDATED_TZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("UPDATED_LTZ", "2026-01-01T00:00:00.000000+09:00");
                    user.put("IS_ACTIVE", "Y");
                    user.put("IS_DELETED", "N");
                    user.put("USER_PROFILE", Base64.getEncoder().encodeToString(("BIT_" + id).getBytes()));
                    user.put("META_JSON", "{\"type\": \"bit_boolean\"}");
                    user.put("TAGS", "mssql,bit,boolean");
                    user.put("USER_XML", "<user><id>BIT_" + id + "</id></user>");
                    user.put("OPTIONAL_COL", testCase);
                    return user;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }
}
