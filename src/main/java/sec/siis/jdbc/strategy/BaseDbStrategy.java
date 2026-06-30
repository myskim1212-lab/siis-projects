package sec.siis.jdbc.strategy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonGenerator;

import sec.siis.jdbc.execution.ObjectMapperHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sec.siis.jdbc.config.EffectiveOperationConfig;
import sec.siis.jdbc.config.FieldConfig;
import sec.siis.jdbc.config.JdbcConfig;
import sec.siis.jdbc.logging.LogMessageManager;
import sec.siis.jdbc.util.CommonUtil;

public abstract class BaseDbStrategy implements DbStrategy {
    private static final Logger log = LoggerFactory.getLogger(BaseDbStrategy.class);
    private static final int STREAM_BUFFER_SIZE = 8192;
    private static final int CHAR_BUFFER_SIZE = 4096;
    // '...' 내부의 :xx (날짜 포맷 등)를 파라미터로 오인하지 않도록 문자열 리터럴을 먼저 소비한다.
    // group(1) == null  → 문자열 리터럴 매칭 → 건너뜀
    // group(1) != null  → 실제 named parameter
    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile("'(?:[^']|'')*'|(?<!:):(\\w+)");
    
    private String msgID;
    
    BaseDbStrategy(String msdID){
    	this.msgID=msdID;
    }
    // ======================================================================
    // 1. Core Execution Methods (Update, Batch, Select)
    // ======================================================================

    @Override
    public int executeUpdate(Connection conn, String txUnitId, EffectiveOperationConfig eop, List<Map<String, Object>> rows) throws Exception {
    	
    	int totalAffectedCount = 0;
        if (rows == null || rows.isEmpty()) {
            LogMessageManager.debugNoRowsToProcess(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name());
            return totalAffectedCount;
        }
     
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Map<String, Object> row = rows.get(rowIndex);
            // 행마다 데이터(특히 Collection)에 맞춰 SQL 동적 생성
            ParsedSql parsedSql = parseNamedParameters(eop.getSql(), row, eop.getFields());
            //LogMessageManager.debugExecSql(log, msgID, eop.getApi_name(), eop.getOperation_name(), parsedSql.sql);

            // SQL 중복 로깅 방지: EOP 객체에 상태 저장
            if (!eop.isSqlLogged()) {
                LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), parsedSql.sql);
                eop.setSqlLogged(true); // 한 번 찍으면 true로 변경
            }

            try (PreparedStatement pstmt = conn.prepareStatement(parsedSql.sql)) {
                bindParameters(pstmt, parsedSql, eop.getFieldMap(), eop, row);
                if (eop.isDataDump()) {
                    LogMessageManager.infoDumpSqlBindings(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), rowIndex, buildBindingDump(parsedSql, eop.getFieldMap(), row));
                }
                int affectedCount=pstmt.executeUpdate();
                totalAffectedCount += affectedCount;
            }
        }
        //LogMessageManager.debugUpdatedRows(log, msgID, eop.getApi_name(), eop.getOperation_name(), rows.size());
        return totalAffectedCount;
    }

    @Override
    public int executeBatch(Connection conn, String txUnitId, EffectiveOperationConfig eop, int batchSize, List<Map<String, Object>> rows) throws Exception {
    	
    	int totalAffectedCount=0; //실제 DB 행 변화 수 합계
    	 
        if (rows == null || rows.isEmpty()) return totalAffectedCount;

        // 1. 가변 IN 절(Collection) 여부 확인
        boolean hasCollection = rows.stream()
                .anyMatch(row -> row.values().stream().anyMatch(v -> v instanceof Collection));

        // 데이터가 가변적이면 Batch 대신 개별 Update 실행
        if (hasCollection) {
            //log.debug("Dynamic Collection detected. Diverting to executeUpdate.");
            // executeUpdate가 영향을 준 총 행수를 반환하도록 설계되어 있다면 그 값을 결과 객체에 담아야 함
            totalAffectedCount = executeUpdate(conn, txUnitId, eop, rows); 
            return totalAffectedCount;
        }

        //LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), eop.getOperation_name(), eop.getSql());
        // SQL 중복 로깅 방지: EOP 객체에 상태 저장
        if (!eop.isSqlLogged()) {
            LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), eop.getSql());
            eop.setSqlLogged(true); // 한 번 찍으면 true로 변경
        }
        ParsedSql parsedSql = parseNamedParameters(eop.getSql(), rows.get(0), eop.getFields());
        
        int processedCount = 0;
        
        try (PreparedStatement pstmt = conn.prepareStatement(parsedSql.sql)) {
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Map<String, Object> row = rows.get(rowIndex);
                bindParameters(pstmt, parsedSql, eop.getFieldMap(), eop, row);
                if (eop.isDataDump()) {
                    LogMessageManager.infoDumpSqlBindings(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), rowIndex, buildBindingDump(parsedSql, eop.getFieldMap(), row));
                }
                pstmt.addBatch();
                processedCount++;

                if (processedCount % batchSize == 0) {
                    int[] counts = pstmt.executeBatch();
                    totalAffectedCount += sumUpdateCounts(counts); // [추가] 합계 계산
                    pstmt.clearBatch();
                }
            }
            
            if (processedCount % batchSize != 0) {
                int[] counts = pstmt.executeBatch();
                totalAffectedCount += sumUpdateCounts(counts); // [추가] 합계 계산
            }
        }

        //LogMessageManager.debugOperationProcessed(log, msgID, eop.getApi_name(), eop.getOperation_name(), processedCount);
        return totalAffectedCount;
    }

    @Override
    public int executeSql(Connection conn, String txUnitId, EffectiveOperationConfig eop) throws Exception {
    	
        //LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), eop.getOperation_name(), eop.getSql());
        if (!eop.isSqlLogged()) {
            LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), eop.getSql());
            eop.setSqlLogged(true); // 한 번 찍으면 true로 변경
        }    	
        ParsedSql parsedSql = parseNamedParameters(eop.getSql(), new HashMap<>(), eop.getFields());
        try (PreparedStatement pstmt = conn.prepareStatement(parsedSql.sql)) {
            int affected = pstmt.executeUpdate();
            LogMessageManager.debugExecutedOperation(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), affected);
            return affected;
        }
    }

    @Override
    public List<Map<String, Object>> executeProcedure(Connection conn, String txUnitId, EffectiveOperationConfig eop, List<Map<String, Object>> rows) throws Exception {
        String callSql = String.format("{CALL %s}", eop.getSql());
        if (!eop.isSqlLogged()) {
            LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), callSql);
            eop.setSqlLogged(true);
        }

        List<FieldConfig> outFields = eop.getResultMappingFields(); // OUT/INOUT 필드 목록
        boolean hasOut = !outFields.isEmpty();

        if (rows == null || rows.isEmpty()) {
            ParsedSql parsedSql = parseNamedParameters(callSql, new HashMap<>(), eop.getFields());
            try (CallableStatement cstmt = conn.prepareCall(parsedSql.sql)) {
                if (hasOut) {
                    registerOutParametersByIndex(cstmt, parsedSql, eop.getFieldMap());
                }
                cstmt.execute();
                if (hasOut) {
                    return List.of(collectOutValuesByIndex(cstmt, parsedSql, eop.getFieldMap(), outFields));
                }
            }
            return null;
        }

        // Collection 여부 판단: Collection이 있으면 행마다 SQL이 달라지므로 행별 처리
        boolean hasCollection = rows.stream()
                .anyMatch(row -> row.values().stream().anyMatch(v -> v instanceof Collection));

        List<Map<String, Object>> outResults = hasOut ? new ArrayList<>() : null;

        if (hasCollection) {
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Map<String, Object> row = rows.get(rowIndex);
                ParsedSql parsedSql = parseNamedParameters(callSql, row, eop.getFields());
                try (CallableStatement cstmt = conn.prepareCall(parsedSql.sql)) {
                    bindProcParameters(cstmt, parsedSql, eop.getFieldMap(), eop, row);
                    if (hasOut) {
                        registerOutParametersByIndex(cstmt, parsedSql, eop.getFieldMap());
                    }
                    if (eop.isDataDump()) {
                        LogMessageManager.infoDumpSqlBindings(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), rowIndex, buildBindingDump(parsedSql, eop.getFieldMap(), row));
                    }
                    cstmt.execute();
                    if (hasOut) {
                        outResults.add(collectOutValuesByIndex(cstmt, parsedSql, eop.getFieldMap(), outFields));
                    }
                }
            }
        } else {
            ParsedSql parsedSql = parseNamedParameters(callSql, rows.get(0), eop.getFields());
            try (CallableStatement cstmt = conn.prepareCall(parsedSql.sql)) {
                if (hasOut) {
                    registerOutParametersByIndex(cstmt, parsedSql, eop.getFieldMap());
                }
                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    Map<String, Object> row = rows.get(rowIndex);
                    bindProcParameters(cstmt, parsedSql, eop.getFieldMap(), eop, row);
                    if (eop.isDataDump()) {
                        LogMessageManager.infoDumpSqlBindings(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), rowIndex, buildBindingDump(parsedSql, eop.getFieldMap(), row));
                    }
                    cstmt.execute();
                    if (hasOut) {
                        outResults.add(collectOutValuesByIndex(cstmt, parsedSql, eop.getFieldMap(), outFields));
                    }
                }
            }
        }
        return outResults;
    }

    /**
     * OUT/INOUT 파라메터를 인덱스(1-based) 기반으로 CallableStatement에 등록한다.
     * Oracle JDBC는 named binding과 ordinal binding을 혼용할 수 없으므로 반드시 인덱스를 사용한다.
     */
    private void registerOutParametersByIndex(CallableStatement cstmt, ParsedSql parsedSql,
                                              Map<String, FieldConfig> fieldMap) throws SQLException {
        List<String> paramNames = parsedSql.paramNames;
        for (int i = 0; i < paramNames.size(); i++) {
            FieldConfig fc = fieldMap.get(paramNames.get(i));
            if (fc == null) continue;
            sec.siis.jdbc.config.Direction dir = fc.getDirection();
            if (dir == sec.siis.jdbc.config.Direction.OUT || dir == sec.siis.jdbc.config.Direction.INOUT) {
                cstmt.registerOutParameter(i + 1, getSqlType(FieldType.fromString(fc.getType())));
            }
        }
    }

    /**
     * 프로시저 실행 후 OUT/INOUT 파라메터 값을 인덱스 기반으로 수집한다.
     * data_field를 키로, OUT 값을 값으로 반환한다.
     */
    private Map<String, Object> collectOutValuesByIndex(CallableStatement cstmt, ParsedSql parsedSql,
                                                        Map<String, FieldConfig> fieldMap,
                                                        List<FieldConfig> outFields) throws SQLException {
        List<String> paramNames = parsedSql.paramNames;
        // param → 1-based index 매핑 (첫 번째 등장 위치 사용)
        Map<String, Integer> paramIndex = new HashMap<>();
        for (int i = 0; i < paramNames.size(); i++) {
            paramIndex.putIfAbsent(paramNames.get(i), i + 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (FieldConfig fc : outFields) {
            Integer idx = paramIndex.get(fc.getParam());
            if (idx == null) continue;
            Object value = cstmt.getObject(idx);
            result.put(fc.getData_field(), value);
        }
        return result;
    }

    /**
     * 프로시저 전용 파라메터 바인딩: IN/INOUT 파라메터만 바인딩한다.
     * OUT 파라메터는 registerOutParameter로 별도 처리되므로 바인딩에서 제외.
     */
    protected void bindProcParameters(PreparedStatement pstmt, ParsedSql parsedSql, Map<String, FieldConfig> fieldMap,
                                      EffectiveOperationConfig eop, Map<String, Object> row) throws SQLException {
        if (fieldMap == null) return;

        Map<String, Integer> listIndexTracker = new HashMap<>();
        int jdbcIndex = 1;

        for (String paramName : parsedSql.paramNames) {
            FieldConfig fc = fieldMap.get(paramName);
            if (fc == null) {
                pstmt.setObject(jdbcIndex++, null);
                continue;
            }

            // OUT 전용 파라메터는 바인딩하지 않고 인덱스만 진행
            if (fc.getDirection() == sec.siis.jdbc.config.Direction.OUT) {
                jdbcIndex++;
                continue;
            }

            Object value = row.get(fc.getData_field());
            int sqlType = getSqlType(FieldType.fromString(fc.getType()));

            if (value instanceof List<?> list) {
                int currentIdx = listIndexTracker.getOrDefault(paramName, 0);
                Object singleValue = (currentIdx < list.size()) ? list.get(currentIdx) : null;
                bindParameter(pstmt, jdbcIndex++, eop, singleValue, sqlType);
                listIndexTracker.put(paramName, currentIdx + 1);
            } else {
                bindParameter(pstmt, jdbcIndex++, eop, value, sqlType);
            }
        }
    }
    @Override
    public List<Map<String, Object>> executeSelect(Connection conn, String txUnitId, EffectiveOperationConfig eop, List<Map<String, Object>> params) throws Exception {
        Map<String, String> columnToJsonMap = buildColumnToJsonMap(eop);
        
        // Select의 경우 첫번째 배열만 참조
        Map<String, Object> whereParams = (params != null && !params.isEmpty()) ? params.get(0) : new HashMap<>();

        // Select 파라미터에 맞게 SQL 동적 생성
        // ParsedSql parsedSql = parseNamedParameters(eop.getSql(), whereParams, eop.getParamFields());
        ParsedSql parsedSql = parseNamedParameters(eop.getSql(), whereParams, eop.getWhereParamFields());

        //LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), eop.getOperation_name(), parsedSql.sql);
        
        if (!eop.isSqlLogged()) {
            LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), parsedSql.sql);
            eop.setSqlLogged(true); // 한 번 찍으면 true로 변경
        }    

        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(parsedSql.sql)) {
            int selectMaxRows = eop.getEffectiveMaxRowLimit();
            if (selectMaxRows > 0) pstmt.setMaxRows(selectMaxRows);
            int fetchSize = eop.getFetchSize();
            if (fetchSize > 0) pstmt.setFetchSize(fetchSize);

            // whereParams 비어있음과 무관하게 SQL에 ? 가 있으면 반드시 바인딩
            if (!parsedSql.paramNames.isEmpty()) {
            	bindParameters(pstmt, parsedSql, eop.getWhereParamFieldMap(), eop, whereParams);
                if (eop.isDataDump()) {
                    LogMessageManager.infoDumpSqlBindings(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), 0, buildBindingDump(parsedSql, eop.getWhereParamFieldMap(), whereParams));
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(extractRow(rs, columnToJsonMap,eop));
                }
            }
        }
        LogMessageManager.debugSelectReturned(log, msgID, eop.getApi_name(), txUnitId,  eop.getOperation_name(), result.size());
        return result;
    }

    // ======================================================================
    // 2. Dynamic SQL Parsing & Parameter Binding
    // ======================================================================

    protected ParsedSql parseNamedParameters(String sql, Map<String, Object> row, List<FieldConfig> fieldConfigs) {
        List<String> paramNames = new ArrayList<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;

        // FieldConfig의 param과 data_field 매핑 준비
        Map<String, String> paramToDataField = (fieldConfigs == null) ? new HashMap<>() :
                fieldConfigs.stream().collect(Collectors.toMap(FieldConfig::getParam, FieldConfig::getData_field, (a, b) -> a));

        while (matcher.find()) {
            sb.append(sql, lastEnd, matcher.start());
            String paramName = matcher.group(1);
            // group(1) == null → 문자열 리터럴('...')에 매칭된 경우, 그대로 복사하고 건너뜀
            if (paramName == null) {
                sb.append(matcher.group(0));
                lastEnd = matcher.end();
                continue;
            }
            String dataField = paramToDataField.get(paramName);
            Object value = (dataField != null) ? row.get(dataField) : null;

            // Collection인 경우 "IN (?, ?, ?)" 형태로 확장
            // Config에서 IN 절은 반드시 ( ) 붙여서 정의해야 한다.
            if (value instanceof Collection<?> col) {
                int size = col.size();
                if (size == 0) {
                    // 빈 리스트일 때 IN () 에러 방지를 위해 NULL 문자열 직접 삽입
                    sb.append("NULL"); 
                } else {
                    for (int i = 0; i < size; i++) {
                        sb.append("?");
                        paramNames.add(paramName);
                        if (i < size - 1) sb.append(", ");
                    }
                }
            } else {
                // value가 null인 경우 포함 (기존 로직 유지)
                sb.append("?");
                paramNames.add(paramName);
            }
            lastEnd = matcher.end();
        }
        sb.append(sql.substring(lastEnd));
        return new ParsedSql(sb.toString(), paramNames);
    }

    protected void bindParameters(PreparedStatement pstmt, ParsedSql parsedSql, Map<String, FieldConfig> fieldMap,
                                  EffectiveOperationConfig eop, Map<String, Object> row) throws SQLException {
        if (fieldMap == null) return;

        Map<String, Integer> listIndexTracker = new HashMap<>(); // 리스트 파라미터 내 현재 위치 추적
        int jdbcIndex = 1;

        for (String paramName : parsedSql.paramNames) {
            FieldConfig fc = fieldMap.get(paramName);
            if (fc == null) {
                pstmt.setObject(jdbcIndex++, null);
                continue;
            }

            Object value = row.get(fc.getData_field());
            int sqlType = getSqlType(FieldType.fromString(fc.getType()));

            if (value instanceof List<?> list) {
                int currentIdx = listIndexTracker.getOrDefault(paramName, 0);
                Object singleValue = (currentIdx < list.size()) ? list.get(currentIdx) : null;
                bindParameter(pstmt, jdbcIndex++, eop, singleValue, sqlType);
                listIndexTracker.put(paramName, currentIdx + 1);
            } else {
                bindParameter(pstmt, jdbcIndex++, eop, value, sqlType);
            }
        }
    }

    protected void bindParameter(PreparedStatement pstmt, int index, EffectiveOperationConfig config, Object value, int sqlType) throws SQLException {
        if (value == null) {
            pstmt.setNull(index, sqlType);
            return;
        }

        if (sqlType == Types.DATE && value instanceof String str) {
            pstmt.setDate(index, (java.sql.Date) CommonUtil.TemporalParse(str, Types.DATE, config.getDate_formats()));
        } else if (sqlType == Types.TIMESTAMP && value instanceof String str) {
            pstmt.setTimestamp(index, (Timestamp) CommonUtil.TemporalParse(str, Types.TIMESTAMP, config.getTimestamp_ntz_formats()));
        } else if (sqlType == Types.TIMESTAMP_WITH_TIMEZONE && value instanceof String str) {
            pstmt.setObject(index, (Timestamp) CommonUtil.TemporalParse(str, Types.TIMESTAMP_WITH_TIMEZONE, config.getTimestamp_formats()));
        } else if (sqlType == Types.DECIMAL || sqlType == Types.NUMERIC) {
            pstmt.setBigDecimal(index, toBigDecimal(value));
        } else {
            pstmt.setObject(index, value, sqlType);
        }
    }

    /**
     * Converts a value to BigDecimal using its string representation to avoid
     * floating-point precision loss that occurs with {@code new BigDecimal(double)}.
     *
     * <p>When Jackson parses an unquoted JSON number (e.g. {@code 123.45}), it produces
     * a {@code Double}. Passing that Double directly to {@code setObject(..., Types.DECIMAL)}
     * causes the JDBC driver to call {@code new BigDecimal(double)} internally, which yields
     * {@code 123.45000000000000284...} — a value that does not match the stored DB value and
     * therefore causes WHERE clauses to find 0 rows.
     *
     * <p>Using {@code value.toString()} first produces the exact decimal string {@code "123.45"},
     * and {@code new BigDecimal("123.45")} gives the mathematically correct value.
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        return new BigDecimal(value.toString());
    }

    // ======================================================================
    // 3. ResultSet & Helper Methods
    // ======================================================================

//    protected Map<String, Object> extractRow(ResultSet rs, Map<String, String> columnToJsonMap,JdbcConfig jcfg) throws SQLException {
//        ResultSetMetaData meta = rs.getMetaData();
//        int count = meta.getColumnCount();
//        Map<String, Object> row = new LinkedHashMap<>(count);
//
//        for (int i = 1; i <= count; i++) {
//            String label = meta.getColumnLabel(i);
//            Object value = extractValueByType(rs, i, meta.getColumnType(i),jcfg);
//            // DB 컬럼명을 Json 필드명으로 변환하여 설정
//            row.put(columnToJsonMap.getOrDefault(label, label), value);
//        }
//        return row;
//    }

    protected Map<String, Object> extractRow(ResultSet rs, Map<String, String> columnToJsonMap, EffectiveOperationConfig eop) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>(count);

        // 1. 결과 매핑용 param → FieldConfig Map (캐싱된 값 사용)
        Map<String, FieldConfig> resultFieldMap = eop.getResultMappingFieldMap();
        
        for (int i = 1; i <= count; i++) {
            String label = meta.getColumnLabel(i).toUpperCase();

            // 2. 우선순위 결정: 설정된 타입이 있는가?
            FieldConfig fc = resultFieldMap.get(label);
            
            // YAML의 fields에 정의되지 않았거나, Direction이 IN인 필드는 결과 JSON에서 제외
            if (fc == null) {
                continue; 
            }
                        
            int targetType = (fc.getType() != null) 
                    ? getSqlType(FieldType.fromString(fc.getType())) 
                    : meta.getColumnType(i);

            Object value = extractValueByType(rs, i, targetType, eop.getJdbcConfig());
            
            // DB 컬럼명을 Json 필드명으로 변환하여 설정
            row.put(columnToJsonMap.getOrDefault(label, label), value);
        }
        return row;
    }
  
    protected Object extractValueByType(ResultSet rs, int index, int type, JdbcConfig jcfg) throws SQLException {
        return switch (type) {
	        case Types.CHAR -> {
	            String val = rs.getString(index);
	            yield (val != null) ? val.trim() : ""; // null 보단 빈 문자열이 안전할 수 있음
	        }
	        
	        case Types.CLOB, Types.NCLOB -> extractClob(rs, index);
            case Types.BLOB ->extractBlob(rs, index);
            
//            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
//                Timestamp ts = rs.getTimestamp(index);
//                if (ts == null) yield null;
//                
//                // 핵심 수정: toLocalDateTime() 대신 ZonedDateTime을 사용하여 타임존(XXX) 대응
//                yield ts.toInstant()
//                        .atZone(ZoneId.systemDefault()) // 시스템 기본 시간대 적용
//                        .format(jcfg.getPrimaryTimestampFormatter()); // JDBCConfig 로딩 시점에 Formatter 설정
//            } 
            case Types.TIMESTAMP -> {
                Timestamp ts = rs.getTimestamp(index);
                if (ts == null) yield null;
                // NTZ formatter 사용 → zone 정보 없이 LocalDateTime으로 처리
                yield ts.toLocalDateTime()
                        .format(jcfg.getPrimaryTimestampNtzFormatter());
            }
            case Types.TIMESTAMP_WITH_TIMEZONE -> {
                if (rs.getObject(index) == null) yield null;
                yield extractZonedTimestamp(rs, index, jcfg);  // 별도 메서드로 위임
            }         
            case Types.DATE -> {
                Date date = rs.getDate(index);
                if (date == null) yield null;
                yield date.toLocalDate().format(jcfg.getPrimaryDateFormatter());  // JDBCConfig 로딩 시점에 Formatter 설정
            }            
            
            default -> rs.getObject(index);
        };
    }

    private String extractZonedTimestamp(ResultSet rs, int index, JdbcConfig jcfg) 
            throws SQLException {
        
        // 1단계: OffsetDateTime 직접 획득
        try {
            OffsetDateTime odt = rs.getObject(index, OffsetDateTime.class);
            if (odt != null) {
                return odt.format(jcfg.getPrimaryTimestampFormatter());
            }
        } catch (SQLException | UnsupportedOperationException ignored) {}

        // 2단계: ZonedDateTime 시도
        try {
            ZonedDateTime zdt = rs.getObject(index, ZonedDateTime.class);
            if (zdt != null) {
                return zdt.format(jcfg.getPrimaryTimestampFormatter());
            }
        } catch (SQLException | UnsupportedOperationException ignored) {}

        // 3단계: fallback
        Timestamp ts = rs.getTimestamp(index);
        if (ts == null) return null;

        return ts.toInstant()
                 .atZone(jcfg.getResolvedZoneId())
                 .format(jcfg.getPrimaryTimestampFormatter());
    }
 // LOB 추출 및 해제를 담당하는 헬퍼 메서드들
    private String extractClob(ResultSet rs, int index) throws SQLException {
        Clob clob = rs.getClob(index);
        if (clob == null) return null;
        try {
            return clobToString(clob);
        } finally {
            try { clob.free(); } catch (SQLException ignore) {}
        }
    }

    private String extractBlob(ResultSet rs, int index) throws SQLException {
        Blob blob = rs.getBlob(index);
        if (blob == null) return null;
        try {
            return blobToBase64(blob);
        } finally {
            try { blob.free(); } catch (SQLException ignore) {}
        }
    }
    
    private Map<String, String> buildColumnToJsonMap(EffectiveOperationConfig eop) {
        return eop.getColumnToJsonMap();
    }

    private LinkedHashMap<String, Object> buildBindingDump(ParsedSql parsedSql, Map<String, FieldConfig> fieldMap, Map<String, Object> row) {
        LinkedHashMap<String, Object> dump = new LinkedHashMap<>();
        if (fieldMap == null) return dump;
        for (String paramName : parsedSql.paramNames) {
            FieldConfig fc = fieldMap.get(paramName);
            if (fc != null) {
                String key = ":" + paramName + " (" + fc.getData_field() + ")";
                dump.putIfAbsent(key, row.get(fc.getData_field()));
            }
        }
        return dump;
    }

    protected static class ParsedSql {
        final String sql;
        final List<String> paramNames;
        ParsedSql(String sql, List<String> paramNames) { this.sql = sql; this.paramNames = paramNames; }
    }
    
    private static String blobToBase64(Blob blob) {
        if (blob == null) {
            return null;
        }

        try (InputStream inputStream = blob.getBinaryStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int length;

            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (Exception e) {
            //LogMessageManager.errorFailedConvertBlob(log, msgID, e);
            throw new RuntimeException("Failed to convert BLOB to Base64", e);
        }
    }    
    
    private static String clobToString(Clob clob) {
        if (clob == null) {
            return null;
        }

        try (Reader reader = clob.getCharacterStream();
             StringWriter writer = new StringWriter()) {

            char[] buffer = new char[CHAR_BUFFER_SIZE];
            int length;

            while ((length = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, length);
            }

            return writer.toString();

        } catch (Exception e) {
            //LogMessageManager.errorFailedReadClob(log, msgID, e);
            throw new RuntimeException("Failed to read CLOB", e);
        }
    }   

    // ======================================================================
    // 4. Bulk DML & Streaming SELECT
    // ======================================================================

    @Override
    public int executeBulkBatch(Connection conn, String txUnitId, EffectiveOperationConfig eop,
            int batchSize, int chunkCommitSize, Iterator<Map<String, Object>> rowIter) throws Exception {

        if (rowIter == null || !rowIter.hasNext()) return 0;

        // 첫 행으로 SQL 파싱 (Collection 파라미터는 Bulk 모드에서 지원하지 않음)
        Map<String, Object> firstRow = rowIter.next();
        ParsedSql parsedSql = parseNamedParameters(eop.getSql(), firstRow, eop.getFields());

        if (!eop.isSqlLogged()) {
            LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), parsedSql.sql);
            eop.setSqlLogged(true);
        }
        LogMessageManager.debugBulkStarted(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), chunkCommitSize);

        int totalProcessed = 0;
        int pendingBatch   = 0;

        try (PreparedStatement pstmt = conn.prepareStatement(parsedSql.sql)) {

            // 첫 행 처리
            bindParameters(pstmt, parsedSql, eop.getFieldMap(), eop, firstRow);
            pstmt.addBatch();
            pendingBatch++;
            totalProcessed++;

            while (rowIter.hasNext()) {
                Map<String, Object> row = rowIter.next();
                bindParameters(pstmt, parsedSql, eop.getFieldMap(), eop, row);
                pstmt.addBatch();
                pendingBatch++;
                totalProcessed++;

                // batchSize마다 JDBC 배치 플러시
                if (pendingBatch == batchSize) {
                    pstmt.executeBatch();
                    pstmt.clearBatch();
                    pendingBatch = 0;
                }

                // chunkCommitSize마다 청크 커밋
                if (totalProcessed % chunkCommitSize == 0) {
                    if (pendingBatch > 0) {
                        pstmt.executeBatch();
                        pstmt.clearBatch();
                        pendingBatch = 0;
                    }
                    conn.commit();
                    int chunkNum = totalProcessed / chunkCommitSize;
                    LogMessageManager.debugBulkChunkCommitted(log, msgID, eop.getApi_name(), txUnitId,
                            eop.getOperation_name(), chunkNum, chunkCommitSize, totalProcessed);
                }
            }

            // 잔여 배치 플러시
            if (pendingBatch > 0) {
                pstmt.executeBatch();
            }
        }

        // 최종 커밋: 마지막 청크 커밋 이후 남은 행이 있을 때만 수행
        // (totalProcessed가 chunkCommitSize의 정배수이면 이미 루프 내에서 커밋됨)
        if (totalProcessed % chunkCommitSize != 0) {
            conn.commit();
        }
        LogMessageManager.debugBulkCompleted(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), totalProcessed);
        return totalProcessed;
    }

    @Override
    public String executeSelectStreaming(Connection conn, String txUnitId, EffectiveOperationConfig eop,
            List<Map<String, Object>> params) throws Exception {

        Map<String, String> columnToJsonMap = buildColumnToJsonMap(eop);
        Map<String, Object> whereParams = (params != null && !params.isEmpty()) ? params.get(0) : new HashMap<>();
        ParsedSql parsedSql = parseNamedParameters(eop.getSql(), whereParams, eop.getWhereParamFields());

        if (!eop.isSqlLogged()) {
            LogMessageManager.debugSqlStatement(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), parsedSql.sql);
            eop.setSqlLogged(true);
        }
        LogMessageManager.debugStreamingSelectStarted(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), eop.getFetchSize());

        StringWriter sw = new StringWriter();
        int resultCount = 0;
        Map<String, FieldConfig> resultFieldMap = eop.getResultMappingFieldMap();

        try (PreparedStatement pstmt = conn.prepareStatement(parsedSql.sql)) {
            int selectMaxRows = eop.getEffectiveMaxRowLimit();
            if (selectMaxRows > 0) pstmt.setMaxRows(selectMaxRows);
            int fetchSize = eop.getFetchSize();
            if (fetchSize > 0) pstmt.setFetchSize(fetchSize);

            if (!parsedSql.paramNames.isEmpty()) {
                bindParameters(pstmt, parsedSql, eop.getWhereParamFieldMap(), eop, whereParams);
                if (eop.isDataDump()) {
                    LogMessageManager.infoDumpSqlBindings(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), 0,
                            buildBindingDump(parsedSql, eop.getWhereParamFieldMap(), whereParams));
                }
            }

            try (JsonGenerator gen = ObjectMapperHolder.INSTANCE.mapper.getFactory().createGenerator(sw);
                 ResultSet rs = pstmt.executeQuery()) {

                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                // 컬럼별 메타 정보 사전 계산 (루프 내 반복 호출 제거)
                String[] labels   = new String[columnCount + 1];
                String[] jsonKeys = new String[columnCount + 1];
                int[]    colTypes = new int[columnCount + 1];
                boolean[] include = new boolean[columnCount + 1];

                for (int i = 1; i <= columnCount; i++) {
                    labels[i]  = meta.getColumnLabel(i).toUpperCase();
                    FieldConfig fc = resultFieldMap.get(labels[i]);
                    include[i] = (fc != null);
                    if (include[i]) {
                        jsonKeys[i] = columnToJsonMap.getOrDefault(labels[i], labels[i]);
                        colTypes[i] = (fc.getType() != null)
                                ? getSqlType(FieldType.fromString(fc.getType()))
                                : meta.getColumnType(i);
                    }
                }

                gen.writeStartArray();
                while (rs.next()) {
                    gen.writeStartObject();
                    for (int i = 1; i <= columnCount; i++) {
                        if (!include[i]) continue;
                        Object value = extractValueByType(rs, i, colTypes[i], eop.getJdbcConfig());
                        gen.writeFieldName(jsonKeys[i]);
                        ObjectMapperHolder.INSTANCE.mapper.writeValue(gen, value);
                    }
                    gen.writeEndObject();
                    resultCount++;
                }
                gen.writeEndArray();
            }
        }

        LogMessageManager.debugStreamingSelectReturned(log, msgID, eop.getApi_name(), txUnitId, eop.getOperation_name(), resultCount, eop.getFetchSize());
        return sw.toString();
    }

    @Override
    public int getSqlType(FieldType fieldType) {
        if (fieldType == null) {
            LogMessageManager.warnSqlTypeNull(log);
            return Types.VARCHAR;
        }
        
        switch (fieldType) {
            case INT:
                return Types.INTEGER;
            case LONG:
                return Types.BIGINT;
            case DOUBLE:
                return Types.DOUBLE;
            case FLOAT:
                return Types.FLOAT;
            case STRING:
                return Types.VARCHAR;
            case CHAR:
                return Types.CHAR;                
            case DATE:
                return Types.DATE;
            case TIMESTAMP:
                return Types.TIMESTAMP;
            case TIMESTAMP_WITH_TIMEZONE:
                return Types.TIMESTAMP_WITH_TIMEZONE;                
            case BLOB:
                return Types.BLOB;
            case CLOB:
                return Types.CLOB;
            case BOOLEAN:
                return Types.BOOLEAN;
            case DECIMAL:
                return Types.DECIMAL;
            default:
                LogMessageManager.warnUnknownSqlType(log, msgID, fieldType.name());
                return Types.VARCHAR;
        }
    }

    private int sumUpdateCounts(int[] counts) {
        int sum = 0;
        for (int count : counts) {
            if (count >= 0) {
                sum += count;
            } else if (count == PreparedStatement.SUCCESS_NO_INFO) {
                // 영향을 받은 행 수를 알 수 없는 경우, 최소 1건으로 간주하거나 로그를 남김
                sum += 1; 
            }
        }
        return sum;
    }
}
