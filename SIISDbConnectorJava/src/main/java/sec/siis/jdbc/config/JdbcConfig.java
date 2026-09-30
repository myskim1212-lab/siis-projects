package sec.siis.jdbc.config;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sec.siis.jdbc.config.JdbcConfigDefaults.TimestampZoneStrategy;

public class JdbcConfig {

	private String api_name;
	private String target_system_key;
	private String target_system_name;
	private String target_jndi_name;
    private Integer batch_size;
	private Integer max_row_limit;

    private String data_record_path;
    private List<String> date_formats;

	private List<String> timestamp_formats;
    
	private Boolean stop_on_operation_error;
	private Boolean stop_on_row_error;
	private Boolean data_dump;

	/** getConnection() 실패 시 추가로 시도할 횟수. 0=재시도 없음(기존 동작과 동일).
	 *  datasource의 oracle.net.CONNECT_TIMEOUT/READ_TIMEOUT이 설정돼 있어야 의미가 있다
	 *  (그게 없으면 매 시도가 여전히 무제한 대기할 수 있음). */
	private Integer connection_retry_count;

	/** 커넥션 획득 재시도마다 동일하게 적용되는 고정 대기 시간(ms) */
	private Long connection_retry_interval_ms;

	/** 커넥션을 얻은 직후 Connection.setNetworkTimeout()으로 적용할 값(ms).
	 *  0=제한 없음(기존 동작과 동일). 커넥션 획득 자체가 멈추는 것은 못 막고,
	 *  획득 이후 실행하는 SQL의 응답 대기만 제한한다. */
	private Long connection_read_timeout_ms;
	
	@JsonIgnore
	private DateTimeFormatter primaryDateFormatter;
	@JsonIgnore
	private DateTimeFormatter primaryTimestampFormatter;

    /** TIMESTAMP (타임존 없음) 전용 포맷 목록 */
    private List<String> timestamp_ntz_formats;

    /** Timestamp 타임존 처리 전략: UTC / SYSTEM / FIXED */
    private TimestampZoneStrategy timestamp_zone_strategy;

    /** timestamp_zone_strategy = FIXED 일 때 사용할 ZoneId 문자열 */
    private String fixed_zone_id;

    @JsonIgnore
    private DateTimeFormatter primaryTimestampNtzFormatter; // NTZ 전용 포맷터

    @JsonIgnore
    private ZoneId resolvedZoneId; // 폴백 시 사용할 ZoneId (캐싱)

    private List<OperationConfig> operations;

	public JdbcConfig() {
    }
    
	public String getApi_name() {
		return api_name;
	}

	public void setApi_name(String api_name) {
		this.api_name = api_name;
	}	

	public String getTarget_system_key() {
		return target_system_key;
	}

	public void setTarget_system_key(String target_system_key) {
		this.target_system_key = target_system_key;
	}

	public String getTarget_system_name() {
		return target_system_name;
	}

	public void setTarget_system_name(String target_system_name) {
		this.target_system_name = target_system_name;
	}

	public String getTarget_jndi_name() {
		return target_jndi_name;
	}

	public void setTarget_jndi_name(String target_jndi_name) {
		this.target_jndi_name = target_jndi_name;
	}


	public Integer getMax_row_limit() {
		return max_row_limit;
	}

	public void setMax_row_limit(Integer max_row_limit) {
		this.max_row_limit = max_row_limit;
	}

    public Integer getBatch_size() {
        return batch_size;
    }

    public void setBatch_size(Integer batch_size) {
        this.batch_size = batch_size;
    }

    public String getData_record_path() {
		return data_record_path;
	}

	public void setData_record_path(String data_record_path) {
		this.data_record_path = data_record_path;
	}

    public List<String> getDate_formats() {
		return date_formats;
	}

	public void setDate_formats(List<String> date_formats) {
	    this.date_formats = date_formats;
	    if (date_formats != null && !date_formats.isEmpty()) {
	        this.primaryDateFormatter = DateTimeFormatter.ofPattern(date_formats.get(0));
	    }
	}
	
	public void setTimestamp_formats(List<String> timestamp_formats) {
	    this.timestamp_formats = timestamp_formats;
	    if (timestamp_formats != null && !timestamp_formats.isEmpty()) {
	        this.primaryTimestampFormatter = DateTimeFormatter.ofPattern(timestamp_formats.get(0));
	    }
	}

    public List<String> getTimestamp_ntz_formats() {
        return timestamp_ntz_formats;
    }

    /**
     * NTZ 포맷 설정 시 primaryTimestampNtzFormatter 자동 생성
     */
    public void setTimestamp_ntz_formats(List<String> timestamp_ntz_formats) {
        this.timestamp_ntz_formats = timestamp_ntz_formats;
        if (timestamp_ntz_formats != null && !timestamp_ntz_formats.isEmpty()) {
            this.primaryTimestampNtzFormatter = 
                DateTimeFormatter.ofPattern(timestamp_ntz_formats.get(0));
        }
    }

    public TimestampZoneStrategy getTimestamp_zone_strategy() {
        return timestamp_zone_strategy;
    }

    public void setTimestamp_zone_strategy(TimestampZoneStrategy timestamp_zone_strategy) {
        this.timestamp_zone_strategy = timestamp_zone_strategy;
        this.resolvedZoneId = null; // 전략 변경 시 캐시 초기화
    }

    public String getFixed_zone_id() {
        return fixed_zone_id;
    }

    public void setFixed_zone_id(String fixed_zone_id) {
        this.fixed_zone_id = fixed_zone_id;
        this.resolvedZoneId = null; // zone 변경 시 캐시 초기화
    }

    /**
     * NTZ(타임존 없음) 전용 포맷터 반환
     * null이면 기존 primaryTimestampFormatter로 폴백 (하위 호환)
     */
    public DateTimeFormatter getPrimaryTimestampNtzFormatter() {
        return primaryTimestampNtzFormatter != null
            ? primaryTimestampNtzFormatter
            : primaryTimestampFormatter; // 설정 안 했을 때 기존 동작 유지
    }

    /**
     * 타임존 전략에 따른 ZoneId 반환 (캐싱)
     * timestamp_zone_strategy 미설정 시 SYSTEM으로 동작
     */
    public ZoneId getResolvedZoneId() {
        if (resolvedZoneId != null) return resolvedZoneId;

        TimestampZoneStrategy strategy = timestamp_zone_strategy != null
            ? timestamp_zone_strategy
            : JdbcConfigDefaults.DEFAULT_TIMESTAMP_ZONE_STRATEGY; // SYSTEM

        resolvedZoneId = switch (strategy) {
            case UTC   -> ZoneId.of("UTC");
            case FIXED -> ZoneId.of(
                fixed_zone_id != null 
                    ? fixed_zone_id 
                    : JdbcConfigDefaults.DEFAULT_FIXED_ZONE_ID
            );
            case SYSTEM -> ZoneId.systemDefault();
        };
        return resolvedZoneId;
    }


    public DateTimeFormatter getPrimaryDateFormatter() {
		return primaryDateFormatter;
	}

	public DateTimeFormatter getPrimaryTimestampFormatter() {
		return primaryTimestampFormatter;
	}
	
    public List<String> getTimestamp_formats() {
		return timestamp_formats;
	}

	public Boolean getStop_on_operation_error() {
		return stop_on_operation_error;
	}

	public void setStop_on_operation_error(Boolean stop_on_operation_error) {
		this.stop_on_operation_error = stop_on_operation_error;
	}

	public Boolean getStop_on_row_error() {
		return stop_on_row_error;
	}

	public void setStop_on_row_error(Boolean stop_on_row_error) {
		this.stop_on_row_error = stop_on_row_error;
	}

	public Boolean getData_dump() {
		return data_dump;
	}

	public void setData_dump(Boolean data_dump) {
		this.data_dump = data_dump;
	}

	public Integer getConnection_retry_count() {
		return connection_retry_count;
	}

	public void setConnection_retry_count(Integer connection_retry_count) {
		this.connection_retry_count = connection_retry_count;
	}

	public Long getConnection_retry_interval_ms() {
		return connection_retry_interval_ms;
	}

	public void setConnection_retry_interval_ms(Long connection_retry_interval_ms) {
		this.connection_retry_interval_ms = connection_retry_interval_ms;
	}

	public Long getConnection_read_timeout_ms() {
		return connection_read_timeout_ms;
	}

	public void setConnection_read_timeout_ms(Long connection_read_timeout_ms) {
		this.connection_read_timeout_ms = connection_read_timeout_ms;
	}

    public List<OperationConfig> getOperations() {
        return operations;
    }

    public void setOperations(List<OperationConfig> operations) {
        this.operations = operations;
    }

	public Optional<OperationConfig> findOperation(String operationName) {
        if (operationName == null || operations == null) {
            return Optional.empty();
        }

        return operations.stream()
                .filter(op -> operationName.equalsIgnoreCase(op.getOperation_name()))
                .findFirst();
    }
}
