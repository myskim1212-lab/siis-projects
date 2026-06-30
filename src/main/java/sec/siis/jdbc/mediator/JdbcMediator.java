package sec.siis.jdbc.mediator;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.axiom.om.OMElement;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.mediators.AbstractMediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import sec.siis.connection.DataSourceManager;
import sec.siis.jdbc.config.ConfigValidationException;
import sec.siis.jdbc.config.InputValidator;
import sec.siis.jdbc.config.JdbcConfig;
import sec.siis.jdbc.config.JdbcConfigLoader;
import sec.siis.jdbc.exception.ApiException;
import sec.siis.jdbc.exception.BadRequestException;
import sec.siis.jdbc.execution.JdbcExecutionResult;
import sec.siis.jdbc.execution.JdbcExecutor;
import sec.siis.jdbc.logging.LogMessageManager;
import sec.siis.jdbc.mediator.response.ApiResponseFactory;
import sec.siis.jdbc.util.CommonJsonUtil;
import sec.siis.jdbc.util.CommonUtil;
import sec.siis.jdbc.util.MiPayloadUtil;

public class JdbcMediator extends AbstractMediator {

	private static final Logger log = LoggerFactory.getLogger(JdbcMediator.class);

	public boolean mediate(MessageContext context) {
	    JdbcConfig jcfg = null;
	    JdbcExecutionResult jdbcExecResult = new JdbcExecutionResult();
	    Map<String, Object> response = new LinkedHashMap<>();

	    boolean if_success = true;
	    long startTime = System.currentTimeMillis();
	    long endTime = 0;
	    String msgID ="";
	    
	    try {
   
	        String inputIfConfig = CommonUtil.decodeYamlConfig(getStringProperty(context, "ifConfig"));
	        
	        jcfg = JdbcConfigLoader.loadFromString(inputIfConfig);
	        
	        // msgID 읽어오기
	        msgID = getStringProperty(context, "msgID");
	        
			String ops = LogMessageManager.formatOperations(jcfg.getOperations(), op -> op.getOperation_name());
	        LogMessageManager.infoStartIf(log, msgID, jcfg.getApi_name(), ops);
	        
	        // 입력 데이터 확보
	        String inputJson = MiPayloadUtil.readJsonPayload(context);
	        
	        // POST 요청에서 body가 비어있는 경우 즉시 에러
	        if (inputJson == null || inputJson.isBlank() || "{}".equals(inputJson.trim())) {
	        	throw new BadRequestException("Request body is missing or empty");
	        }
	        
	        JsonNode parsedInput = InputValidator.validateAndParse(inputJson, jcfg);

	        if (Boolean.TRUE.equals(jcfg.getData_dump())) {
	            LogMessageManager.infoDumpRequest(log, msgID, jcfg.getApi_name(), inputJson);
	        }

	        DataSource ds = DataSourceManager.get(jcfg.getTarget_jndi_name());
	        LogMessageManager.debugDataSourceResolved(log, msgID, jcfg.getApi_name(), jcfg.getTarget_jndi_name());

	        // 실행
	        JdbcExecutor executor = new JdbcExecutor(ds,jcfg.getTarget_jndi_name(),msgID);
	        jdbcExecResult = executor.executeJdbc(jcfg, parsedInput, jdbcExecResult);
	        
	        endTime = System.currentTimeMillis();
	        
	        if (jdbcExecResult.hasFailure()) {
	        	 if_success = false;
	        }
	        
            response = ApiResponseFactory.createResponse(jcfg,msgID, startTime, endTime, jdbcExecResult);

	        if (Boolean.TRUE.equals(jcfg.getData_dump())) {
	            try {
	                LogMessageManager.infoDumpResponse(log, msgID, jcfg.getApi_name(), CommonJsonUtil.toJson(response));
	            } catch (Exception dumpEx) {
	                log.warn("Failed to dump response", dumpEx);
	            }
	        }

	    } catch (ConfigValidationException e) {
	        // 설정 검증 실패 전용 처리
	        log.error("JDBC Configuration Error: {}", e.getMessage());
	        if_success = false;
	        endTime = System.currentTimeMillis();
	        response = ApiResponseFactory.error(null, e, msgID, startTime, endTime, jdbcExecResult);

	    } catch (ApiException e) {
	        log.error("API error", e);
	        if_success = false;
	        endTime = System.currentTimeMillis();
	        response = ApiResponseFactory.error(jcfg, e, msgID, startTime, endTime, jdbcExecResult);
	    } catch (Exception e) {
	        log.error("System error", e);
	        if_success = false;
	        endTime = System.currentTimeMillis();
	        response = ApiResponseFactory.error(jcfg, e, msgID, startTime, endTime, jdbcExecResult);
	    } finally {
	        if (endTime <= 0) endTime = System.currentTimeMillis();
	    }

	    
	    
	    // 5. 최종 응답 설정
	    try {
	        MiPayloadUtil.setJsonPayload(context, CommonJsonUtil.toJson(response), if_success);
	    } catch (Exception e) {
	        log.error("Critical: Failed to set JSON payload", e);
	        
	        // JSON 직렬화 실패 시 폴백 에러 응답 생성
	        Map<String, Object> fallbackResponse = ApiResponseFactory.error(
	            jcfg,
	            new RuntimeException("Response serialization failed: " + e.getMessage()),
	            msgID,
	            startTime,
	            System.currentTimeMillis(),
	            null
	        );

	        try {
	            MiPayloadUtil.setJsonPayload(context, CommonJsonUtil.toJson(fallbackResponse), if_success);
	            CommonJsonUtil.filterOperationData(response, jcfg);
	        } catch (Exception fatal) {
	            // 메시지 엔진 수준의 에러 처리 필요
	            throw new SynapseException("Fatal error during response delivery", fatal);
	        } finally {
	            CommonJsonUtil.filterOperationData(fallbackResponse, jcfg);
	            try {
	                // synapse-handler로 모니터링 데이터 설정 (data 필드 제거 후)
	                context.setProperty("eventDetail", CommonJsonUtil.toJson(fallbackResponse));
	            } catch (Exception ex) {
	            }
	        }
	    } finally {
	        // 로그는 응답 설정 성공 여부와 상관없이 실행
	        logExecutionSummarySafely(msgID, jcfg, jdbcExecResult, if_success, startTime, endTime);
	        // jcfg가 null일 수 있으므로 방어 코드 적용
	        if (jcfg != null) {
	            LogMessageManager.infoEndIf(log, msgID, jcfg.getApi_name());
	        } else {
	            LogMessageManager.infoEndIf(log, msgID, null);
	        }

	        // synapse-handler로 모니터링 데이터 설정 (data 필드 제거 후)
	        CommonJsonUtil.filterOperationData(response, jcfg);
	        try {
	            context.setProperty("eventDetail", CommonJsonUtil.toJson(response));
	        } catch (Exception e) {
	        }
	        
	    }
	    
	    return true;
	}
	
    public static String getStringProperty(
            MessageContext ctx, String key) {

        Object v = ctx.getProperty(key);

        if (v == null) return null;

        if (v instanceof OMElement) {
            return ((OMElement) v).getText().trim();
        }

        return v.toString().trim();
    }
    
    private void logExecutionSummarySafely(String msgID,
            JdbcConfig ifcfg,
            JdbcExecutionResult result,
            boolean success,
            long start,
            long end) {

        try {
            LogMessageManager.infoExecutionSummary(
                log,
                msgID,
                ifcfg != null ? ifcfg.getApi_name() : "UNKNOWN",
                success,
                end - start,
                result != null ? result.getOperations() : null
            );
        } catch (Exception e) {
            log.warn("Failed to log execution summary", e);
        }
    }    
}
