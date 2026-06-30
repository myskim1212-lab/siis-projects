package sec.siis.jdbc.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

import sec.siis.jdbc.config.ActionType;
import sec.siis.jdbc.config.EffectiveOperationConfig;
import sec.siis.jdbc.exception.ApiException;
import sec.siis.jdbc.exception.DbErrorClassifier;
import sec.siis.jdbc.util.DateTimeFormatUtil;

@JsonPropertyOrder({
    "order", "operationName", "type", "success", "skipped","committed",
    "startTime", "endTime","elapsedMs",
    "requestRecordCount", "responseRecordCount", "successCount", "failCount","affectedRows", 
    "resultData",
    "errorMessage", "errorCode", "errorType", "errorDetail", "rowErrors",
})

// null 인 항목들은 json 변환 시 제외
@JsonInclude(JsonInclude.Include.NON_NULL)

public class JdbcExecutionOperationResult {

	private int order;
	@JsonIgnore
	private String operationName;
	private ActionType actionType;

	private boolean success;

	@JsonIgnore
	private boolean skipped;

	@JsonIgnore
	private long startTime = 0;

	@JsonIgnore
	private long endTime = 0;

	private int requestRecordCount=0;
	private int responseRecordCount=0;
	private int successCount=0;
	private int affectedRows; // DB 영향을 받은 행 수
	private int failCount=0;

	@JsonIgnore
	private List<Map<String, Object>> resultData; // select 결과 or procedure OUT

	/** SELECT Streaming 경로: pre-serialized JSON 배열 (null이면 일반 경로) */
	@JsonIgnore
	private JsonNode streamedResultNode;

	/** Bulk DML 경로로 실행되었는지 여부 (Summary 로그에서 BLK 표시용) */
	@JsonIgnore
	private boolean bulkMode = false;

	/** Streaming SELECT 경로로 실행되었는지 여부 (Summary 로그에서 STR 표시용) */
	@JsonIgnore
	private boolean streamingMode = false;

	private String dataRecord;
	private String errorMessage;
	private String errorCode;    
	private String errorType;
	private String errorDetail;
	
	private boolean committed;
    @JsonIgnore
	private String sql;
    
	@JsonIgnore
    private String sqlState;
    
	private List<RowError> rowErrors;

	/* factory */
	public static JdbcExecutionOperationResult init(int order, EffectiveOperationConfig eop) {

		JdbcExecutionOperationResult r = new JdbcExecutionOperationResult();
		r.order = order;
		r.operationName = eop.getOperation_name();
		r.actionType = eop.getAction_type();
		r.sql = eop.getSql();
		r.dataRecord = eop.getData_record();
		return r;
	}


	public void start() {
		this.startTime = System.currentTimeMillis();
	}

	public void end() {
		this.endTime = System.currentTimeMillis();
	}

	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public String getOperationName() {
		return operationName;
	}

	public void setOperationName(String operationName) {
		this.operationName = operationName;
	}

	public ActionType getActionType() {
		return this.actionType;
	}

	public void setActionType(ActionType actionType) {
		this.actionType = actionType;
	}

	public boolean isSkipped() {
		return skipped;
	}

	public void setSkipped(boolean skipped) {
		this.skipped = skipped;
	}
	@JsonIgnore
	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}
	@JsonIgnore
	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public int getRequestRecordCount() {
		return requestRecordCount;
	}

	public void setRequestRecordCount(int requestRecordCount) {
		this.requestRecordCount = requestRecordCount;
	}

	public int getResponseRecordCount() {
		return responseRecordCount;
	}

	public void setResponseRecordCount(int responseRecordCount) {
		this.responseRecordCount = responseRecordCount;
	}
	
	public int getSuccessCount() {
		return successCount;
	}

	public void setSuccessCount(int successCount) {
		this.successCount = successCount;
	}

	public long getAffectedRows() {
		return affectedRows;
	}

	public void setAffectedRows(int affectedRows) {
		this.affectedRows = affectedRows;
	}
	
	public int getFailCount() {
		return failCount;
	}

	public void setFailCount(int failCount) {
		this.failCount = failCount;
	}

	public Object getResultData() {
		return resultData;
	}

	public void setResultData(List<Map<String, Object>> resultData) {
		this.resultData = resultData;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getErrorType() {
		return errorType;
	}

	public void setErrorType(String errorType) {
		this.errorType = errorType;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public void success(int successCount) {
		this.success = true;
		this.successCount = this.successCount + successCount;
	}

	public void fail(Exception e) {
	    this.success = false;
	    this.endTime = System.currentTimeMillis();

	    if (e instanceof ApiException apiEx) {
	        this.errorCode = apiEx.getError().getCode();
	        this.errorType = apiEx.getError().getType();
	        this.errorMessage = apiEx.getError().getMessage();
	        this.errorDetail = apiEx.getError().getDetail();

	        Throwable cause = apiEx.getCause();
	        while (cause != null) {
	            if (cause instanceof java.sql.SQLException se) {
	                this.sqlState = se.getSQLState();
	                this.errorCode = Integer.toString(se.getErrorCode());
	                // 분류기를 통해 가독성 높은 메시지로 교체
	                String classified = DbErrorClassifier.classify(
	                    this.sqlState, String.valueOf(se.getErrorCode()), se.getMessage());

	                this.errorMessage = classified;
             
	                // 상세 내용은 원본 DB 메시지 유지
	                this.errorDetail = String.format("(SqlState:%s) %s", 
	                                   this.sqlState , se.getMessage());	                
	                break;
	            }
	            cause = cause.getCause();
	        }
	    } else {
	        this.errorCode = "E500";
	        this.errorMessage = "Internal Server Error";
	        this.errorDetail = e.getClass().getSimpleName() + ": " + e.getMessage();
	    }
	}

    public String getSqlState() { return sqlState; }

	public void skip(String reason) {
		this.skipped = true;
		this.success = true;
		this.committed = false;
		this.errorMessage = reason;
		this.endTime = System.currentTimeMillis();
	}

	public boolean isSuccess() {
		return success;
	}

	@JsonIgnore
	public boolean isSelect() {
		return (ActionType.SELECT==this.actionType);
	}

	@JsonIgnore
	public boolean isFail() {
		return !this.success;
	}

	public boolean isFailCount() {
		return this.failCount > 0;
	}

	public String getErrorDetail() {
		return errorDetail;
	}

	public void setErrorDetail(String errorDetail) {
		this.errorDetail = errorDetail;
	}

	public boolean isCommitted() {
		return committed;
	}

	public void setCommitted(boolean committed) {
		this.committed = committed;
	}
	
	@JsonIgnore
	public String getStartTimeFormatted() {
		return DateTimeFormatUtil.formatTimestamp(startTime);
	}

	@JsonIgnore
	public String getEndTimeFormatted() {
		return DateTimeFormatUtil.formatTimestampOrNull(endTime);
	}

	@JsonProperty("elapsedMs")
	public long getElapsedMs() {
		return endTime == 0 ? 0 : endTime - startTime;
	}

	public List<RowError> getRowErrors() {
		return rowErrors;
	}

	public void setRowErrors(List<RowError> rowErrors) {
		this.rowErrors = rowErrors;
	}

	public void addRowError(RowError error) {
		if (rowErrors == null) {
			rowErrors = new ArrayList<>();
		}
		rowErrors.add(error);
	}

	public boolean hasRowErrors() {
		return rowErrors != null && !rowErrors.isEmpty();
	}

	public String getSql() {
		return sql;
	}

	public void incrementFail(int fail) {
		this.failCount += fail;
	}

	public void incrementSuccessCount(int success) {
		this.successCount += success;
	}

	public void incrementRequestRecordCount(int reqRecCnt) {
		this.requestRecordCount += reqRecCnt;
	}
	
	public void incrementAffectedRows(int affectedRows) {
		this.affectedRows += affectedRows;
	}
	
	public JsonNode getStreamedResultNode() {
		return streamedResultNode;
	}

	public void setStreamedResultNode(JsonNode streamedResultNode) {
		this.streamedResultNode = streamedResultNode;
	}

	public boolean isBulkMode() {
		return bulkMode;
	}

	public void setBulkMode(boolean bulkMode) {
		this.bulkMode = bulkMode;
	}

	public boolean isStreamingMode() {
		return streamingMode;
	}

	public void setStreamingMode(boolean streamingMode) {
		this.streamingMode = streamingMode;
	}

	// 클래스에 정의된 필드명이 아닌 Dynamic한 필드명을 정의하고 싶은 경우
	// dynamicMap의 Key가 필드명이 됨.
	@JsonAnyGetter
	public Map<String, Object> getDynamicResultData() {
	    // 데이터가 없으면 null을 반환하여 JSON 생성을 방지
	    if (this.streamedResultNode == null && this.resultData == null) {
	        return null;
	    }

	    Map<String, Object> dynamicMap = new HashMap<>();
	    String key = (this.dataRecord != null) ? this.dataRecord : "resultData";
	    if (this.streamedResultNode != null) {
	        dynamicMap.put(key, this.streamedResultNode);
	    } else {
	        dynamicMap.put(key, resultData);
	    }
	    return dynamicMap;
	}
}