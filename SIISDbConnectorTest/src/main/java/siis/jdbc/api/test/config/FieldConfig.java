package siis.jdbc.api.test.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * YAML operation 내 fields 항목 하나를 나타내는 모델.
 *
 * <pre>
 * fields:
 *   - { data_field: USER_ID, param: USER_ID, type: STRING }
 *   - { data_field: OUT_CODE, param: OUT_CODE, type: STRING, direction: OUT }
 * </pre>
 */
public class FieldConfig {

    /** DB 컬럼명 (결과셋에서 읽을 때 사용) */
    @JsonProperty("data_field")
    private String dataField;

    /** SQL 바인드 파라미터명 (:param 에서 'param' 부분) */
    @JsonProperty("param")
    private String param;

    /** 데이터 타입: STRING, INT, LONG, DECIMAL, FLOAT, DOUBLE,
     *  DATE, TIMESTAMP, TIMESTAMP_WITH_TIMEZONE, CLOB, BLOB */
    @JsonProperty("type")
    private String type;

    /** 파라미터 방향: IN(기본값) / OUT (프로시저용) */
    @JsonProperty("direction")
    private String direction = "IN";

    // ─── getters & setters ───────────────────────────────────────────────────

    public String getDataField() { return dataField; }
    public void setDataField(String dataField) { this.dataField = dataField; }

    public String getParam() { return param; }
    public void setParam(String param) { this.param = param; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    @Override
    public String toString() {
        return "FieldConfig{dataField='" + dataField + "', param='" + param
                + "', type='" + type + "', direction='" + direction + "'}";
    }
}
