package siis.jdbc.api.test.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * registry/config/jdbc/{apiName}.yaml 최상위 모델.
 *
 * <pre>
 * api_name: select_tb_user_v2_mssql_no_param
 * target_system_key: SST0002_MSSQL
 * target_jndi_name: jdbc/SST0002
 * stop_on_operation_error: true
 * stop_on_row_error: true
 * max_row_limit: 100000
 * data_record_path: /operations
 * timestamp_zone_strategy: UTC
 * ...
 * operations:
 *   - operation_name: ...
 * </pre>
 */
public class JdbcApiConfig {

    @JsonProperty("api_name")
    private String apiName;

    @JsonProperty("target_system_key")
    private String targetSystemKey;

    @JsonProperty("target_system_name")
    private String targetSystemName;

    @JsonProperty("target_jndi_name")
    private String targetJndiName;

    @JsonProperty("stop_on_operation_error")
    private Boolean stopOnOperationError = true;

    @JsonProperty("stop_on_row_error")
    private Boolean stopOnRowError = true;

    @JsonProperty("max_row_limit")
    private int maxRowLimit = 100000;

    @JsonProperty("data_record_path")
    private String dataRecordPath = "/operations";

    /** 결과를 JSON 덤프 모드로 반환할지 여부. 기본값 false */
    @JsonProperty("data_dump")
    private boolean dataDump = false;

    /** 배치 INSERT 시 묶음 크기. 기본값 100 */
    @JsonProperty("batch_size")
    private int batchSize = 100;

    @JsonProperty("date_formats")
    private List<String> dateFormats;

    /** UTC / SYSTEM / FIXED */
    @JsonProperty("timestamp_zone_strategy")
    private String timestampZoneStrategy = "UTC";

    /** timestampZoneStrategy=FIXED 일 때만 유효 */
    @JsonProperty("fixed_zone_id")
    private String fixedZoneId;

    @JsonProperty("timestamp_formats")
    private List<String> timestampFormats;

    @JsonProperty("timestamp_ntz_formats")
    private List<String> timestampNtzFormats;

    @JsonProperty("operations")
    private List<OperationConfig> operations;

    // ─── getters & setters ───────────────────────────────────────────────────

    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }

    public String getTargetSystemKey() { return targetSystemKey; }
    public void setTargetSystemKey(String targetSystemKey) { this.targetSystemKey = targetSystemKey; }

    public String getTargetSystemName() { return targetSystemName; }
    public void setTargetSystemName(String targetSystemName) { this.targetSystemName = targetSystemName; }

    public String getTargetJndiName() { return targetJndiName; }
    public void setTargetJndiName(String targetJndiName) { this.targetJndiName = targetJndiName; }

    public Boolean getStopOnOperationError() { return stopOnOperationError; }
    public void setStopOnOperationError(Boolean stopOnOperationError) { this.stopOnOperationError = stopOnOperationError; }

    public Boolean getStopOnRowError() { return stopOnRowError; }
    public void setStopOnRowError(Boolean stopOnRowError) { this.stopOnRowError = stopOnRowError; }

    public int getMaxRowLimit() { return maxRowLimit; }
    public void setMaxRowLimit(int maxRowLimit) { this.maxRowLimit = maxRowLimit; }

    public String getDataRecordPath() { return dataRecordPath; }
    public void setDataRecordPath(String dataRecordPath) { this.dataRecordPath = dataRecordPath; }

    public boolean isDataDump() { return dataDump; }
    public void setDataDump(boolean dataDump) { this.dataDump = dataDump; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public List<String> getDateFormats() { return dateFormats; }
    public void setDateFormats(List<String> dateFormats) { this.dateFormats = dateFormats; }

    public String getTimestampZoneStrategy() { return timestampZoneStrategy; }
    public void setTimestampZoneStrategy(String timestampZoneStrategy) { this.timestampZoneStrategy = timestampZoneStrategy; }

    public String getFixedZoneId() { return fixedZoneId; }
    public void setFixedZoneId(String fixedZoneId) { this.fixedZoneId = fixedZoneId; }

    public List<String> getTimestampFormats() { return timestampFormats; }
    public void setTimestampFormats(List<String> timestampFormats) { this.timestampFormats = timestampFormats; }

    public List<String> getTimestampNtzFormats() { return timestampNtzFormats; }
    public void setTimestampNtzFormats(List<String> timestampNtzFormats) { this.timestampNtzFormats = timestampNtzFormats; }

    public List<OperationConfig> getOperations() { return operations; }
    public void setOperations(List<OperationConfig> operations) { this.operations = operations; }

    @Override
    public String toString() {
        return "JdbcApiConfig{apiName='" + apiName + "', targetJndiName='" + targetJndiName + "'}";
    }
}
