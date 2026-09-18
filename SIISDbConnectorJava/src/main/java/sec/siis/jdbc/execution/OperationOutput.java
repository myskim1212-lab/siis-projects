package sec.siis.jdbc.execution;

import java.util.List;
import java.util.Map;

public class OperationOutput {
    private int successCount;
    private int affectedCount;
    private List<Map<String, Object>> data;
    /** SELECT Streaming 경로: JSON 배열 문자열 (null이면 일반 경로) */
    private String streamedJson;

    public OperationOutput(int successCount, int affectedCount, List<Map<String, Object>> data) {
        this.successCount = successCount;
        this.affectedCount = affectedCount;
        this.data = data;
    }

    /** SELECT Streaming 전용 생성자 */
    public OperationOutput(int successCount, String streamedJson) {
        this.successCount = successCount;
        this.affectedCount = 0;
        this.streamedJson = streamedJson;
    }

	public int getSuccessCount() {
        return successCount;
    }

    public int getAffectedCount() {
		return affectedCount;
	}

	public void setAffectedCount(int affectedCount) {
		this.affectedCount = affectedCount;
	}

    public List<Map<String, Object>> getData() {
        return data;
    }

    public String getStreamedJson() {
        return streamedJson;
    }

    public boolean isStreamed() {
        return streamedJson != null;
    }
}
