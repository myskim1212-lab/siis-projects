package sec.siis.jdbc.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import sec.siis.jdbc.execution.CommitAction;
import sec.siis.jdbc.execution.CommitScope;

public class EffectiveOperationConfig {

	private final OperationConfig originalConfig;
    private final OperationConfig op;
    private final JdbcConfig ifcfg;

    private boolean sqlLogged = false;

    // ── 캐싱 필드 (per-request 인스턴스, 스레드 안전) ─────────────────────────
    private List<FieldConfig> cachedWhereParamFields;
    private List<FieldConfig> cachedResultMappingFields;
    private Map<String, FieldConfig> cachedFieldMap;
    private Map<String, FieldConfig> cachedWhereParamFieldMap;
    private Map<String, FieldConfig> cachedResultMappingFieldMap;
    private Map<String, String>      cachedColumnToJsonMap;


	/* =========================
     * 기본 생성자 (Master / 일반)
     * ========================= */
    public EffectiveOperationConfig(
            JdbcConfig ifcfg,
            OperationConfig op
    ) {
    	this.originalConfig = op;  
        this.ifcfg = ifcfg;
        this.op = op;
    }

    /* =========================
     * Detail 전용 Factory
     * ========================= */
    public static EffectiveOperationConfig forDetail(
            JdbcConfig ifcfg,
            OperationConfig masterOp,
            OperationConfig detailOp
    ) {
        OperationConfig merged = mergeDetailOperation(masterOp, detailOp);
        return new EffectiveOperationConfig(ifcfg, merged);
    }

    /* =========================
     * Detail 병합 로직 (캡슐화)
     * ========================= */
    private static OperationConfig mergeDetailOperation(
            OperationConfig masterOp,
            OperationConfig detailOp
    ) {
        OperationConfig merged = new OperationConfig(detailOp); // copy constructor 권장

        /* fields 병합 */
        List<FieldConfig> combinedFields = new ArrayList<>();

        if (detailOp.getFields() != null) {
            combinedFields.addAll(detailOp.getFields());
        }

        if (detailOp.getInherit_fields() != null && !detailOp.getInherit_fields().isEmpty()) {

            Map<String, FieldConfig> masterFieldMap = new HashMap<>();
            if (masterOp.getFields() != null) {
                for (FieldConfig f : masterOp.getFields()) {
                    masterFieldMap.put(f.getData_field(), f);
                }
            }

            for (FieldConfig inheritField : detailOp.getInherit_fields()) {
                FieldConfig masterField = masterFieldMap.get(inheritField.getData_field());
                if (masterField != null) {
                    combinedFields.add(masterField);
                } else {
                    // YAML 설정 오류(오타 등)를 런타임에 조용히 무시하지 않고 경고 로그로 노출
                    org.slf4j.LoggerFactory.getLogger(EffectiveOperationConfig.class)
                        .warn("[{}] inherit_fields '{}' not found in master operation '{}' fields — parameter will be missing at runtime",
                            detailOp.getOperation_name(),
                            inheritField.getData_field(),
                            masterOp.getOperation_name());
                }
            }
        }

        merged.setFields(combinedFields);
        return merged;
    }

    public boolean isMasterDetail() {
        return originalConfig != null && originalConfig.hasDetailOperations();
    }
    
    public OperationConfig getOriginalConfig() {
        return originalConfig;
    }

    
    public String getData_record() {
        return op.getData_record();
    }

	public ActionType getAction_type() {
		return op.getAction_type();
	}

    public String getOperation_name() {
        return op.getOperation_name();
    }

    public String getSql() {
        return op.getSql();
    }

    public List<FieldConfig> getFields() {
        return op.getFields();
    }

    /**
     * Operation 단위 data_record_path 정의 여부.
     * true이면 JdbcExecutor에서 opName을 경로에 포함하지 않고
     * data_record_path + data_record 조합으로 직접 파싱한다.
     */
    public boolean hasOperationDataRecordPath() {
        String p = op.getData_record_path();
        return p != null && !p.isBlank();
    }

    /**
     * 실효 data_record_path 반환.
     * Operation에 data_record_path가 정의된 경우 해당 값을 반환하고,
     * 그렇지 않으면 전역 고정값 /operations를 반환한다.
     * (전역 JdbcConfig.data_record_path는 무시됨)
     */
    public String getData_record_path() {
        return hasOperationDataRecordPath()
                ? op.getData_record_path()
                : JdbcConfigDefaults.DEFAULT_DATA_RECORD_PATH;
    }
    
    public List<String> getDate_formats() {
        return ifcfg.getDate_formats();
    }

    public List<String> getTimestamp_formats() {
        return ifcfg.getTimestamp_formats();
    }    
    
    public List<String> getTimestamp_ntz_formats() {
        return ifcfg.getTimestamp_ntz_formats();
    }    

    public List<FieldConfig> getWhereParamFields() {
        if (cachedWhereParamFields == null) {
            if (op.getFields() == null) {
                cachedWhereParamFields = Collections.emptyList();
            } else {
                cachedWhereParamFields = op.getFields().stream()
                        .filter(f -> f.getDirection() == Direction.IN
                                  || f.getDirection() == Direction.INOUT)
                        .collect(Collectors.toList());
            }
        }
        return cachedWhereParamFields;
    }

    public List<FieldConfig> getResultMappingFields() {
        if (cachedResultMappingFields == null) {
            if (op.getFields() == null) {
                cachedResultMappingFields = Collections.emptyList();
            } else {
                cachedResultMappingFields = op.getFields().stream()
                        .filter(f -> f.getDirection() == Direction.OUT
                                  || f.getDirection() == Direction.INOUT)
                        .collect(Collectors.toList());
            }
        }
        return cachedResultMappingFields;
    }

    /** 전체 fields의 param → FieldConfig Map (DML/PROC 바인딩용). */
    public Map<String, FieldConfig> getFieldMap() {
        if (cachedFieldMap == null) {
            List<FieldConfig> fields = op.getFields();
            cachedFieldMap = (fields == null) ? Collections.emptyMap()
                : fields.stream().collect(
                    Collectors.toMap(FieldConfig::getParam, fc -> fc, (a, b) -> a));
        }
        return cachedFieldMap;
    }

    /** IN/INOUT fields의 param → FieldConfig Map (SELECT 바인딩용). */
    public Map<String, FieldConfig> getWhereParamFieldMap() {
        if (cachedWhereParamFieldMap == null) {
            cachedWhereParamFieldMap = getWhereParamFields().stream()
                .collect(Collectors.toMap(FieldConfig::getParam, fc -> fc, (a, b) -> a));
        }
        return cachedWhereParamFieldMap;
    }

    /** OUT/INOUT fields의 param → FieldConfig Map (SELECT 결과 매핑용). */
    public Map<String, FieldConfig> getResultMappingFieldMap() {
        if (cachedResultMappingFieldMap == null) {
            cachedResultMappingFieldMap = getResultMappingFields().stream()
                .collect(Collectors.toMap(FieldConfig::getParam, fc -> fc, (a, b) -> a));
        }
        return cachedResultMappingFieldMap;
    }

    /** SELECT 컬럼 라벨 → JSON 필드명 Map (buildColumnToJsonMap용). */
    public Map<String, String> getColumnToJsonMap() {
        if (cachedColumnToJsonMap == null) {
            cachedColumnToJsonMap = getResultMappingFields().stream()
                .collect(Collectors.toMap(
                    FieldConfig::getParam, FieldConfig::getData_field, (a, b) -> a));
        }
        return cachedColumnToJsonMap;
    }
    
    public CommitScope getCommit_scope() {
        return op.getCommit_scope();
    }

    public CommitAction getCommit_Action() {
        return op.getCommit_action();
    }
    
    public String getApi_name() {
        return ifcfg.getApi_name();
    }
    
    /**
     * execute_if_no_data 설정 반환.
     * true이면 입력 JSON에 해당 노드가 없더라도(existNode=false) Operation을 강제 실행한다.
     * 기본값 false.
     */
    public boolean isExecuteIfNoData() {
        return op.isExecute_if_no_data();
    }

    /**
     * 실효 max_row_limit 반환.
     * Operation 단위 설정값(max_row_limit > 0)이 있으면 우선 적용하고,
     * 없으면 전역 JdbcConfig.max_row_limit 로 fallback 한다.
     * 최종 값이 0 이하이면 제한 없음을 의미한다.
     *
     * 적용 대상
     *  - SELECT  : pstmt.setMaxRows() 로 결과 행 수를 DB 수준에서 제한
     *  - DML     : 입력 rows 수가 한도를 초과하면 E400 BadRequest 반환
     */
    public int getEffectiveMaxRowLimit() {
        Integer opLimit = op.getMax_row_limit();
        if (opLimit != null && opLimit > 0) return opLimit;
        Integer globalLimit = ifcfg.getMax_row_limit();
        return (globalLimit != null && globalLimit > 0) ? globalLimit : 0;
    }
    
    public JdbcConfig getJdbcConfig(){
    	return this.ifcfg;
    }

    public boolean isSqlLogged() {
		return sqlLogged;
	}

	public void setSqlLogged(boolean sqlLogged) {
		this.sqlLogged = sqlLogged;
	}

	public boolean isDataDump() {
		return Boolean.TRUE.equals(ifcfg.getData_dump());
	}

    /**
     * Bulk DML 처리 여부.
     * true이면 Iterator 기반 청크 커밋 경로로 실행된다.
     */
    public boolean isBulk() {
        return op.isBulk();
    }

    /**
     * Bulk DML 청크 커밋 크기.
     * Operation 단위 설정값이 있으면 해당 값, 없으면 기본값(DEFAULT_CHUNK_COMMIT_SIZE) 반환.
     */
    public int getChunkCommitSize() {
        Integer v = op.getChunk_commit_size();
        return (v != null && v > 0) ? v : JdbcConfigDefaults.DEFAULT_CHUNK_COMMIT_SIZE;
    }

    /**
     * SELECT ResultSet fetch size.
     * null 또는 0이면 드라이버 기본값 사용.
     */
    public int getFetchSize() {
        Integer v = op.getFetch_size();
        return (v != null && v > 0) ? v : 0;
    }
}