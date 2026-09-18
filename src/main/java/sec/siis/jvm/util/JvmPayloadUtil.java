package sec.siis.jvm.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;

/**
 * JVM 모니터링 기능 전용 Synapse JSON payload 유틸 — sec.siis.jdbc.util.MiPayloadUtil과
 * 로직은 같지만(둘 다 Axis2/Synapse 표준 방식), dbinfo 쪽 코드에 전혀 의존하지 않도록
 * 이 패키지 안에 독립적으로 둔다.
 */
public class JvmPayloadUtil {
    private JvmPayloadUtil() {}

    public static void setJsonPayload(MessageContext mc, String jsonString, boolean isSuccess) {
        org.apache.axis2.context.MessageContext axis2MC =
                ((Axis2MessageContext) mc).getAxis2MessageContext();

        JsonUtil.removeJsonPayload(axis2MC);

        try {
            org.apache.axiom.om.OMElement body = axis2MC.getEnvelope().getBody();
            if (body != null) {
                body.build();
                while (body.getFirstOMChild() != null) {
                    body.getFirstOMChild().detach();
                }
            }
        } catch (Exception e) {
            // 무시 — 바디가 비어있는 상태였으면 애초에 지울 것도 없음
        }

        try {
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
            InputStream is = new java.io.ByteArrayInputStream(bytes);
            JsonUtil.getNewJsonPayload(axis2MC, is, true, true);

            axis2MC.setProperty("messageType", "application/json");
            axis2MC.setProperty("ContentType", "application/json");
            axis2MC.setProperty("HTTP_SC", isSuccess ? "200" : "500");
        } catch (Exception e) {
            throw new SynapseException("JSON Payload setting failed", e);
        }
    }

    public static String readJsonPayload(MessageContext synCtx) throws Exception {
        org.apache.axis2.context.MessageContext axis2Ctx =
                ((Axis2MessageContext) synCtx).getAxis2MessageContext();

        InputStream jsonStream = JsonUtil.getJsonPayload(axis2Ctx);
        if (jsonStream == null) {
            return "{}";
        }
        String payload = new String(readAllBytes(jsonStream), StandardCharsets.UTF_8);
        return (payload == null || payload.trim().isEmpty()) ? "{}" : payload;
    }

    // InputStream.readAllBytes()는 JDK9+ — Java 8에서도 돌아야 해서 직접 구현.
    private static byte[] readAllBytes(InputStream is) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }
}
