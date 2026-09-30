package sec.siis.jdbc.config;

import java.util.List;
import java.util.Set;

import sec.siis.jdbc.execution.CommitAction;
import sec.siis.jdbc.execution.CommitScope;

/**
 * JDBC Config 기본값 정의
 */
public class JdbcConfigDefaults {
    
    // ========== 전역 기본값 ==========
    
    /**
     * 기본 Batch 크기
     */
    public static final int DEFAULT_BATCH_SIZE = 100;
    
    /**
     * 커넥션 획득(getConnection) 실패 시 기본 재시도 횟수.
     * 0이면 재시도 없음(기존 동작과 동일) — datasource의 oracle.net.CONNECT_TIMEOUT/
     * READ_TIMEOUT이 설정돼 있어야 getConnection()이 적당한 시간 안에 실패로 끝나서
     * 이 재시도가 실제로 의미가 있다. 그 설정 없이 재시도 횟수만 늘려봐야, 매 시도가
     * 여전히 무제한 대기할 수 있어 효과가 없다.
     */
    public static final int DEFAULT_CONNECTION_RETRY_COUNT = 0;

    /**
     * 커넥션 획득 재시도 사이 고정 대기 시간(ms)
     */
    public static final long DEFAULT_CONNECTION_RETRY_INTERVAL_MS = 500L;

    /**
     * 커넥션 획득 후 Connection.setNetworkTimeout()으로 적용할 기본값(ms).
     * 0이면 제한 없음(기존 동작과 동일). 이건 "커넥션을 얻은 뒤 실행하는 SQL"에만
     * 적용되고, getConnection() 자체가 멈추는 것은 막지 못한다(그건 datasource의
     * oracle.net.READ_TIMEOUT의 역할).
     */
    public static final long DEFAULT_CONNECTION_READ_TIMEOUT_MS = 0L;

    /**
     * 기본 최대 조회 건수 (SELECT)
     */
    public static final int DEFAULT_MAX_ROW_LIMIT = 100000;

    /**
     * Bulk DML 기본 청크 커밋 크기 (행 수)
     */
    public static final int DEFAULT_CHUNK_COMMIT_SIZE = 1000;
    
    /**
     * 기본 데이터 레코드 경로 (전역 고정값 — Config에서 변경 불가)
     */
    public static final String DEFAULT_DATA_RECORD_PATH = "/operations";
    
    /**
     * 기본 Operation 실패 시 중단 여부
     */
    public static final boolean DEFAULT_STOP_ON_OPERATION_ERROR = true;
    
    /**
     * 기본 Row 실패 시 중단 여부
     */
    public static final boolean DEFAULT_STOP_ON_ROW_ERROR = true;
    
    /**
     * 기본 날짜 포맷 목록
     */
    public static final List<String> DEFAULT_DATE_FORMATS = List.of(
        "yyyy-MM-dd",
        "yyyyMMdd",
        "yyyy/MM/dd"
    );
    
    
    // ========== 타임스탬프 타임존 전략 ==========

    public enum TimestampZoneStrategy {
        UTC, SYSTEM, FIXED
    }

    /**
     * 기본 Timestamp Timezone 전략
     */
    public static final TimestampZoneStrategy DEFAULT_TIMESTAMP_ZONE_STRATEGY = 
        TimestampZoneStrategy.SYSTEM;

    /**
     * 기본 고정 Zone ID (FIXED 전략 사용 시)
     */
    public static final String DEFAULT_FIXED_ZONE_ID = "UTC";

    /**
     * 기본 NTZ(No Time Zone) 타임스탬프 포맷 목록
     * TIMESTAMP (without timezone) 전용
     */
    public static final List<String> DEFAULT_TIMESTAMP_NTZ_FORMATS = List.of(
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss.SSS"
    );

    /**
     * 기존 DEFAULT_TIMESTAMP_FORMATS는 WITH TIME ZONE 용으로 재정의
     * (기존 코드 호환성 유지를 위해 기존 목록 유지)
     */
    public static final List<String> DEFAULT_TIMESTAMP_TZ_FORMATS = List.of(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"  // ISO-8601 with offset
    );
    
    // ========== Operation 기본값 ==========
    
    /**
     * 기본 Commit Action
     */
    public static final CommitAction DEFAULT_COMMIT_ACTION = CommitAction.CONTINUE;
    
    /**
     * 기본 Commit Scope
     */
    public static final CommitScope DEFAULT_COMMIT_SCOPE = CommitScope.ALL;
    
    /**
     * 기본 has_detail 값
     */
    public static final boolean DEFAULT_HAS_DETAIL = false;
    
    // ========== 유효성 검증 상수 ==========
    
    /**
     * 유효한 Operation 타입
     */
    public static final Set<String> VALID_OPERATION_TYPES = Set.of(
        "insert",
        "update",
        "delete",
        "select",
        "procedure",
        "upsert"
    );
    
    /**
     * ROW Scope 허용 타입
     */
    public static final Set<String> ROW_SCOPE_ALLOWED_TYPES = Set.of(
        "insert",
        "upsert",
        "update",
        "delete"
    );
    
    /**
     * SQL이 필수가 아닌 타입
     */
    public static final Set<String> SQL_OPTIONAL_TYPES = Set.of(
        "procedure"
    );
    
    // ========== 헬퍼 메서드 ==========
    
    /**
     * Operation 타입이 유효한지 검증
     */
    public static boolean isValidOperationType(String type) {
        if (type == null) return false;
        return VALID_OPERATION_TYPES.contains(type.toLowerCase());
    }
    
    /**
     * ROW Scope 사용 가능한 타입인지 검증
     */
    public static boolean isRowScopeAllowed(String type) {
        if (type == null) return false;
        return ROW_SCOPE_ALLOWED_TYPES.contains(type.toLowerCase());
    }
    
    /**
     * SQL이 필수인 타입인지 검증
     */
    public static boolean isSqlRequired(String type) {
        if (type == null) return true;
        return !SQL_OPTIONAL_TYPES.contains(type.toLowerCase());
    }
    
    /**
     * 지원되는 Operation 타입 목록 문자열 반환
     */
    public static String getSupportedOperationTypes() {
        return String.join(", ", VALID_OPERATION_TYPES);
    }
    
    // 인스턴스 생성 방지
    private JdbcConfigDefaults() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}