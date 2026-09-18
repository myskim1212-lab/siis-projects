package siis.jdbc.api.test.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * YAML operations[] 배열의 항목 하나를 나타내는 모델.
 *
 * <pre>
 * operations:
 *   - operation_name: select_tb_user_v2
 *     action_type: SELECT
 *     data_record: data
 *     commit_action: COMMIT
 *     commit_scope: ALL
 *     sql: |
 *       SELECT USER_ID FROM TB_USER_V2 WHERE USER_ID = :userId
 *     fields:
 *       - { data_field: USER_ID, param: userId, type: STRING }
 * </pre>
 */
public class OperationConfig {

    @JsonProperty("operation_name")
    private String operationName;

    /** SELECT / INSERT / UPDATE / DELETE / PROCEDURE */
    @JsonProperty("action_type")
    private String actionType;

    /** 결과 배열을 담을 JSON 키 이름 */
    @JsonProperty("data_record")
    private String dataRecord;

    /** COMMIT / CONTINUE */
    @JsonProperty("commit_action")
    private String commitAction = "COMMIT";

    /** ALL / ROW  (INSERT 계열에서만 ROW 허용) */
    @JsonProperty("commit_scope")
    private String commitScope = "ALL";

    @JsonProperty("sql")
    private String sql;

    @JsonProperty("fields")
    private List<FieldConfig> fields;

    /** 대량 DML Bulk 처리 여부. true이면 chunk_commit_size 단위로 커밋. INSERT/UPSERT 전용. */
    @JsonProperty("bulk")
    private boolean bulk = false;

    /** Bulk 처리 시 커밋 단위 행 수. bulk=true일 때만 유효. */
    @JsonProperty("chunk_commit_size")
    private Integer chunkCommitSize;

    /** SELECT ResultSet fetch size. null 또는 0이면 드라이버 기본값. */
    @JsonProperty("fetch_size")
    private Integer fetchSize;

    /** Detail Operation 존재 여부. */
    @JsonProperty("has_detail")
    private boolean hasDetail = false;

    /** Detail Operation 목록. has_detail=true일 때 필수. */
    @JsonProperty("detail_operations")
    private List<OperationConfig> detailOperations;

    // ─── getters & setters ───────────────────────────────────────────────────

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getDataRecord() { return dataRecord; }
    public void setDataRecord(String dataRecord) { this.dataRecord = dataRecord; }

    public String getCommitAction() { return commitAction; }
    public void setCommitAction(String commitAction) { this.commitAction = commitAction; }

    public String getCommitScope() { return commitScope; }
    public void setCommitScope(String commitScope) { this.commitScope = commitScope; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public List<FieldConfig> getFields() { return fields; }
    public void setFields(List<FieldConfig> fields) { this.fields = fields; }

    public boolean isBulk() { return bulk; }
    public void setBulk(boolean bulk) { this.bulk = bulk; }

    public Integer getChunkCommitSize() { return chunkCommitSize; }
    public void setChunkCommitSize(Integer chunkCommitSize) { this.chunkCommitSize = chunkCommitSize; }

    public Integer getFetchSize() { return fetchSize; }
    public void setFetchSize(Integer fetchSize) { this.fetchSize = fetchSize; }

    public boolean isHasDetail() { return hasDetail; }
    public void setHasDetail(boolean hasDetail) { this.hasDetail = hasDetail; }

    public List<OperationConfig> getDetailOperations() { return detailOperations; }
    public void setDetailOperations(List<OperationConfig> detailOperations) { this.detailOperations = detailOperations; }

    @Override
    public String toString() {
        return "OperationConfig{operationName='" + operationName + "', actionType='" + actionType + "'}";
    }
}
