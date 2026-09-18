package sec.siis.jdbc.strategy;

import java.sql.Connection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import sec.siis.jdbc.config.EffectiveOperationConfig;


public interface DbStrategy {

    DbType getDbType();

    int executeUpdate(
            Connection conn,
            String txUnitId,
            EffectiveOperationConfig eop,
            List<Map<String, Object>> rows) throws Exception;

    int executeSql(
            Connection conn,
            String txUnitId,
            EffectiveOperationConfig eop) throws Exception;

    int executeBatch(
            Connection conn,
            String txUnitId,
            EffectiveOperationConfig eop,
            int batchSize,
            List<Map<String, Object>> rows) throws Exception;

    List<Map<String, Object>> executeSelect(
            Connection conn,
            String txUnitId,
            EffectiveOperationConfig eop,
            List<Map<String, Object>> params) throws Exception;

    /**
     * @param batchSize 파라메터가 전부 IN(OUT/INOUT 없음)인 다중 행 호출을 addBatch()로
     *                  묶어 실행할 때 executeBatch()를 몇 행마다 플러시할지. OUT/INOUT이
     *                  하나라도 있으면 지금까지처럼 행마다 즉시 execute()하므로 사용되지 않는다.
     */
    List<Map<String, Object>> executeProcedure(
            Connection conn,
            String txUnitId,
            EffectiveOperationConfig eop,
            List<Map<String, Object>> rows,
            int batchSize) throws Exception;

    /**
     * Bulk DML 처리: Iterator 기반으로 행을 순차 처리하고 chunkCommitSize 단위로 커밋한다.
     * 커밋은 이 메서드 내부에서 직접 수행하므로 호출자는 별도로 커밋하지 않는다.
     *
     * @return 전체 처리 행 수 (processedCount)
     */
    int executeBulkBatch(
            Connection conn,
            String txUnitId,
            EffectiveOperationConfig eop,
            int batchSize,
            int chunkCommitSize,
            Iterator<Map<String, Object>> rowIter) throws Exception;

    /**
     * SELECT Streaming 처리: fetch_size를 적용하고 JsonGenerator로 ResultSet을 직접
     * JSON 배열 문자열로 직렬화한다. ArrayList<Map> 구체화를 피해 메모리를 절약한다.
     *
     * @return JSON 배열 문자열 (예: "[{...},{...}]")
     */
    String executeSelectStreaming(
            Connection conn,
            String txUnitId,
            EffectiveOperationConfig eop,
            List<Map<String, Object>> params) throws Exception;

    int getSqlType(FieldType fieldType);

}