package sec.siis.jdbc.config;

import java.util.ArrayList;
import java.util.List;

import sec.siis.jdbc.execution.CommitAction;
import sec.siis.jdbc.execution.CommitScope;

/**
 * Operation 설정 (Master-Detail 지원)
 * DetailOperationConfig 없이 OperationConfig를 재사용
 */
public class OperationConfig {
    // 기존 필드들...
    private String operation_name;
    private ActionType action_type;
    private String data_record;
    private String data_record_path;
    private CommitAction commit_action=CommitAction.CONTINUE;
    private CommitScope commit_scope=CommitScope.ALL;
    private String sql;
    private List<FieldConfig> fields;

    // Master-Detail 지원
    private boolean has_detail = false;              // Detail Operation 존재 여부
    private List<OperationConfig> detail_operations; // OperationConfig 재사용
    //private List<String> inherit_fields;             // Detail이 Master에서 상속받을 필드명 리스트
    private List<FieldConfig> inherit_fields;
    
    /** Operation 단위 행 수 제한 (null = 미설정 → 전역 max_row_limit 사용) */
    private Integer max_row_limit;

    /**
     * 입력 데이터 노드가 없어도(existNode=false) Operation을 강제 실행한다.
     * 기본값 false (하위 호환).
     * 표준 경로에서 특정 Op 키를 보내지 않더라도 DELETE 전체 삭제,
     * PROCEDURE 강제 호출 등이 필요한 경우 true로 설정한다.
     */
    private boolean execute_if_no_data = false;

    /**
     * 대량 DML Bulk 처리 여부.
     * true이면 입력 데이터를 Iterator 기반으로 순차 처리하고
     * chunk_commit_size 단위로 커밋한다. INSERT/UPSERT에 적합.
     * 기본값 false (하위 호환).
     */
    private boolean bulk = false;

    /**
     * Bulk 처리 시 커밋 단위 행 수.
     * bulk=true 일 때만 유효하며, null이면 JdbcConfigDefaults.DEFAULT_CHUNK_COMMIT_SIZE 사용.
     */
    private Integer chunk_commit_size;

    /**
     * SELECT ResultSet fetch size.
     * JDBC 드라이버가 서버 커서에서 한 번에 가져올 행 수를 지정한다.
     * null 또는 0이면 드라이버 기본값 사용.
     */
    private Integer fetch_size;
    
	public OperationConfig() {
    	
    }

    public OperationConfig(OperationConfig src) {
        this.operation_name = src.operation_name;
        this.action_type = src.action_type;
        this.data_record = src.data_record;
        this.data_record_path = src.data_record_path;
        this.sql = src.sql;

        this.commit_scope = src.commit_scope;
        this.commit_action = src.commit_action;

        this.fields = src.fields != null
                ? new ArrayList<>(src.fields)
                : null;

        this.inherit_fields = src.inherit_fields != null
                ? new ArrayList<>(src.inherit_fields)
                : null;
        this.max_row_limit = src.max_row_limit;
        this.execute_if_no_data = src.execute_if_no_data;
        this.bulk = src.bulk;
        this.chunk_commit_size = src.chunk_commit_size;
        this.fetch_size = src.fetch_size;
    }
    
    public OperationConfig copy() {
        return new OperationConfig(this);
    }
    
    public boolean isHas_detail() {
        return has_detail;
    }

    public void setHas_detail(boolean has_detail) {
        this.has_detail = has_detail;
    }
    
    public String getOperation_name() {
		return operation_name;
	}

	public void setOperation_name(String operation_name) {
		this.operation_name = operation_name;
	}

	public ActionType getAction_type() {
		return action_type;
	}

	public void setAction_type(ActionType action_type) {
		this.action_type = action_type;
	}

	public String getData_record() {
		return data_record;
	}

	public void setData_record(String data_record) {
		this.data_record = data_record;
	}

	public String getData_record_path() {
		return data_record_path;
	}

	public void setData_record_path(String data_record_path) {
		this.data_record_path = data_record_path;
	}

	public CommitAction getCommit_action() {
		return commit_action;
	}

	public void setCommit_action(CommitAction commit_action) {
		this.commit_action = commit_action;
	}

	public CommitScope getCommit_scope() {
		return commit_scope;
	}

	public void setCommit_scope(CommitScope commit_scope) {
		this.commit_scope = commit_scope;
	}

	public String getSql() {
		return sql;
	}

	public void setSql(String sql) {
		this.sql = sql;
	}

	public List<FieldConfig> getFields() {
		return fields;
	}

	public void setFields(List<FieldConfig> fields) {
		this.fields = fields;
	}

	public List<OperationConfig> getDetail_operations() {
        return detail_operations;
    }

    public void setDetail_operations(List<OperationConfig> detail_operations) {
        this.detail_operations = detail_operations;
    }    
    
    public List<FieldConfig> getInherit_fields() {
		return inherit_fields;
	}

	public void setInherit_fields(List<FieldConfig> inherit_fields) {
		this.inherit_fields = inherit_fields;
	}
	
    /**
     * Detail Operation이 있는지 확인
     */
    public boolean hasDetailOperations() {
        return has_detail && detail_operations != null && !detail_operations.isEmpty();
    }
    


	public Integer getMax_row_limit() {
		return max_row_limit;
	}

	public void setMax_row_limit(Integer max_row_limit) {
		this.max_row_limit = max_row_limit;
	}

	public boolean isExecute_if_no_data() {
		return execute_if_no_data;
	}

	public void setExecute_if_no_data(boolean execute_if_no_data) {
		this.execute_if_no_data = execute_if_no_data;
	}

	public boolean isBulk() {
		return bulk;
	}

	public void setBulk(boolean bulk) {
		this.bulk = bulk;
	}

	public Integer getChunk_commit_size() {
		return chunk_commit_size;
	}

	public void setChunk_commit_size(Integer chunk_commit_size) {
		this.chunk_commit_size = chunk_commit_size;
	}

	public Integer getFetch_size() {
		return fetch_size;
	}

	public void setFetch_size(Integer fetch_size) {
		this.fetch_size = fetch_size;
	}

}