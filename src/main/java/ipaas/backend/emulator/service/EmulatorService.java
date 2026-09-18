package ipaas.backend.emulator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ipaas.backend.emulator.model.EmulatorRequest;
import ipaas.backend.emulator.model.RequestLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;

@Service
public class EmulatorService {

    private static final Logger log = LoggerFactory.getLogger(EmulatorService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Random random = new Random();

    private final RequestHistoryService historyService;

    public EmulatorService(RequestHistoryService historyService) {
        this.historyService = historyService;
    }

    public ResponseEntity<StreamingResponseBody> handle(String method, String uri,
                                                        Map<String, String> reqHeaders, String reqBody) {
        int no = historyService.nextCount();

        RequestLog requestLog = new RequestLog();
        requestLog.setNo(no);
        requestLog.setTimestamp(LocalDateTime.now().format(FMT));
        requestLog.setMethod(method);
        requestLog.setUri(uri);
        requestLog.setRequestHeaders(reqHeaders);
        requestLog.setRequestBody(reqBody);

        logRequest(no, method, uri, reqHeaders, reqBody);

        EmulatorRequest emulatorReq = tryParse(reqBody);
        ResponseEntity<StreamingResponseBody> response;

        if (emulatorReq != null) {
            response = buildControlledResponse(emulatorReq, requestLog);
        } else {
            response = buildEchoResponse(reqHeaders, reqBody, requestLog);
        }

        historyService.add(requestLog);
        return response;
    }

    private EmulatorRequest tryParse(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            EmulatorRequest req = objectMapper.readValue(body, EmulatorRequest.class);
            if (req.getResponseBody() != null) return req;
        } catch (Exception ignored) {}
        return null;
    }

    private ResponseEntity<StreamingResponseBody> buildControlledResponse(
            EmulatorRequest req, final RequestLog requestLog) {

        // 1. error_rate 체크
        if (req.getErrorRate() != null && req.getErrorRate() > 0) {
            if (random.nextInt(100) < req.getErrorRate()) {
                final String errBody = "{\"error\":\"Simulated error (error_rate=" + req.getErrorRate() + "%)\"}";
                requestLog.setResponseCode(500);
                requestLog.setMode("ERROR");
                requestLog.setResponseBody(errBody);
                logResponse(requestLog.getNo(), "ERROR (error_rate=" + req.getErrorRate() + "%)",
                        500, new HttpHeaders(), errBody, false, 0, 0);
                return ResponseEntity.status(500)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .body(new StreamingResponseBody() {
                            public void writeTo(java.io.OutputStream os) throws java.io.IOException {
                                os.write(errBody.getBytes(StandardCharsets.UTF_8));
                                os.flush();
                            }
                        });
            }
        }

        // 2. 응답 헤더 구성
        HttpHeaders responseHeaders = new HttpHeaders();
        if (req.getResponseContentType() != null && !req.getResponseContentType().trim().isEmpty()) {
            responseHeaders.set(HttpHeaders.CONTENT_TYPE, req.getResponseContentType());
        }
        if (req.getResponseHeader() != null && !req.getResponseHeader().trim().isEmpty()) {
            for (String part : req.getResponseHeader().split(",")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2) {
                    responseHeaders.add(kv[0].trim(), kv[1].trim());
                }
            }
        }

        // 3. 응답 설정
        final int    responseCode = req.getResponseCode() != null ? req.getResponseCode() : 200;
        final String responseBody = req.getResponseBody() != null ? req.getResponseBody() : "";
        final boolean stream      = Boolean.TRUE.equals(req.getStream());
        final int chunkSize       = (req.getChunkSize() != null && req.getChunkSize() > 0)
                                    ? req.getChunkSize() : Math.max(1, responseBody.length());
        final int delayTime       = req.getDelayTime() != null ? req.getDelayTime() : 0;
        final String mode         = stream ? "CONTROLLED-STREAM" : "CONTROLLED";
        final int no              = requestLog.getNo();

        requestLog.setResponseCode(responseCode);
        requestLog.setMode(mode);
        requestLog.setResponseBody(responseBody);

        logResponse(no, mode, responseCode, responseHeaders, responseBody, stream, chunkSize, delayTime);

        final byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);

        StreamingResponseBody streamBody = new StreamingResponseBody() {
            public void writeTo(java.io.OutputStream outputStream) throws java.io.IOException {
                if (stream) {
                    for (int i = 0; i < bytes.length; i += chunkSize) {
                        int end = Math.min(i + chunkSize, bytes.length);
                        outputStream.write(bytes, i, end - i);
                        outputStream.flush();
                        log.info("[STREAM #{}] chunk sent ({} ~ {} bytes)", no, i, end - 1);
                        if (delayTime > 0 && end < bytes.length) {
                            try { Thread.sleep(delayTime); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                    }
                } else {
                    if (delayTime > 0) {
                        try { Thread.sleep(delayTime); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        log.info("[DELAY #{}] {}ms applied", no, delayTime);
                    }
                    outputStream.write(bytes);
                    outputStream.flush();
                }
            }
        };

        return ResponseEntity.status(responseCode).headers(responseHeaders).body(streamBody);
    }

    private ResponseEntity<StreamingResponseBody> buildEchoResponse(
            Map<String, String> reqHeaders, String reqBody, RequestLog requestLog) {

        HttpHeaders responseHeaders = new HttpHeaders();
        for (Map.Entry<String, String> entry : reqHeaders.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("content-length")) {
                responseHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        final String body = reqBody != null ? reqBody : "";
        requestLog.setResponseCode(200);
        requestLog.setMode("ECHO");
        requestLog.setResponseBody(body);

        logResponse(requestLog.getNo(), "ECHO", 200, responseHeaders, body, false, 0, 0);

        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().headers(responseHeaders).body(new StreamingResponseBody() {
            public void writeTo(java.io.OutputStream outputStream) throws java.io.IOException {
                outputStream.write(bytes);
                outputStream.flush();
            }
        });
    }

    private void logRequest(int no, String method, String uri,
                            Map<String, String> headers, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("=".repeat(70)).append("\n");
        sb.append(String.format(" [REQUEST #%d]\n", no));
        sb.append("=".repeat(70)).append("\n");
        sb.append(String.format("  %-16s: %s%n", "Time",   LocalDateTime.now().format(FMT)));
        sb.append(String.format("  %-16s: %s%n", "Method", method));
        sb.append(String.format("  %-16s: %s%n", "URI",    uri));
        sb.append("  -- Headers --\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(String.format("  %-30s: %s%n", entry.getKey(), entry.getValue()));
        }
        sb.append("  -- Body --\n");
        sb.append("  ").append(body != null && !body.trim().isEmpty() ? body : "(empty)").append("\n");
        sb.append("=".repeat(70)).append("\n");
        log.info(sb.toString());
    }

    private void logResponse(int no, String mode, int responseCode, HttpHeaders headers,
                              String body, boolean stream, int chunkSize, int delayTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("-".repeat(70)).append("\n");
        sb.append(String.format(" [RESPONSE #%d]\n", no));
        sb.append("-".repeat(70)).append("\n");
        sb.append(String.format("  %-16s: %d%n", "Status", responseCode));
        if (stream) {
            sb.append(String.format("  %-16s: STREAM (chunk_size=%d, delay_time=%dms)%n", "Mode", chunkSize, delayTime));
        } else {
            sb.append(String.format("  %-16s: %s%s%n", "Mode", mode,
                    delayTime > 0 ? String.format(" (delay_time=%dms)", delayTime) : ""));
        }
        sb.append("  -- Headers --\n");
        headers.forEach((k, v) -> sb.append(String.format("  %-30s: %s%n", k, v)));
        sb.append("  -- Body --\n");
        sb.append("  ").append(!body.trim().isEmpty() ? body : "(empty)").append("\n");
        sb.append("-".repeat(70)).append("\n");
        log.info(sb.toString());
    }
}
