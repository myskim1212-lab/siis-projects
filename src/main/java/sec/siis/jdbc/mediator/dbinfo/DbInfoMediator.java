package sec.siis.jdbc.mediator.dbinfo;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import sec.siis.connection.DataSourceManager;
import sec.siis.jdbc.execution.ObjectMapperHolder;
import sec.siis.jdbc.util.CommonJsonUtil;
import sec.siis.jdbc.util.MiPayloadUtil;

public class DbInfoMediator extends AbstractMediator {

    private static final Logger log = LoggerFactory.getLogger(DbInfoMediator.class);

    @Override
    public boolean mediate(MessageContext context) {
        boolean success = true;
        Map<String, Object> response = new LinkedHashMap<>();

        String action   = null;
        String jndiName = null;

        try {
            String payload = MiPayloadUtil.readJsonPayload(context);
            ObjectMapper mapper = ObjectMapperHolder.INSTANCE.mapper;
            JsonNode req = mapper.readTree(payload);

            action   = getRequiredText(req, "action");
            jndiName = getRequiredText(req, "jndi_name");

            log.debug("[DbInfoMediator] Started Dbinfo Service");
            
            DataSource ds = DataSourceManager.get(jndiName);
            DbInfoExecutor executor = new DbInfoExecutor();

            switch (action.toUpperCase()) {
                case "CONNECTION_TEST":
                    response = executor.testConnection(ds, jndiName);
                    break;
                case "TABLE_LAYOUT":
                    String tableName  = getRequiredText(req, "table_name");
                    String schemaName = textOrNull(req, "schema_name");
                    response = executor.getTableLayout(ds, jndiName, schemaName, tableName);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown action: " + action);
            }

        } catch (Exception e) {
            success = false;
            log.error("[DbInfoMediator] action={}, jndi_name={} failed: {}", action, jndiName, e.getMessage(), e);
            response = buildErrorResponse(action, jndiName, e);
        }

        try {
            String responseJson = CommonJsonUtil.toJson(response);
            MiPayloadUtil.setJsonPayload(context, responseJson, success);
            // MIHandler(synapse-handler)가 eventDetail 프로퍼티를 읽어 모니터링에 사용
            context.setProperty("eventDetail", responseJson);
        } catch (Exception e) {
            log.error("[DbInfoMediator] Failed to set response payload", e);
        }

        return true;
    }

    private String getRequiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Required field missing: " + field);
        }
        return value.asText().trim();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private Map<String, Object> buildErrorResponse(String action, String jndiName, Exception e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success",  false);
        error.put("action",   action != null ? action : "UNKNOWN");
        if (jndiName != null) {
            error.put("jndi_name", jndiName);
        }
        error.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        return error;
    }
}
