package siis.jdbc.api.test.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

/**
 * JDBC API 클라이언트
 * HTTP 요청을 처리하고 응답을 파싱합니다.
 */
public class JdbcApiClient {
    
    private static final Logger log = LoggerFactory.getLogger(JdbcApiClient.class);
    
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final CloseableHttpClient httpClient;
    
    public JdbcApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        Timeout timeout = Timeout.ofMilliseconds(TestConfig.getRequestTimeout());

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(timeout)
                .setSocketTimeout(timeout)
                .build())
            .build();

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(timeout)
            .setResponseTimeout(timeout)
            .build();

        this.httpClient = HttpClientBuilder.create()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();
    }
    
    public ApiResponse callApi(String endpoint, Map<String, Object> requestBody , String requestBodyStr) {
        String url = baseUrl + endpoint;
        log.info("Calling API: {}", url);
        
        try {
        	String jsonBody="";
        	if(requestBodyStr==null || requestBodyStr.isEmpty() ) {
        		jsonBody = objectMapper.writeValueAsString(requestBody);
        	}else {
        		jsonBody=requestBodyStr;
        	}
            
            log.debug("Request Body: {}", jsonBody);
            
            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
            httpPost.setHeader("Content-Type", "application/json");
            
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();
                String responseBody = new String(
                    response.getEntity().getContent().readAllBytes(),
                    StandardCharsets.UTF_8
                );
                
                log.info("Response Status: {}", statusCode);
                log.debug("Response Body: {}", responseBody);

                JsonNode jsonResponse = objectMapper.readTree(responseBody);

                Map<String, String> responseHeaders = new LinkedHashMap<>();
                for (Header header : response.getHeaders()) {
                    responseHeaders.put(header.getName(), header.getValue());
                }

                return new ApiResponse(
                    statusCode == 200,
                    statusCode,
                    responseBody,
                    jsonResponse,
                    null,
                    responseHeaders
                );
            }
        } catch (IOException e) {
            log.error("API call failed", e);
            return new ApiResponse(false, -1, null, null, e.getMessage());
        }
    }
    
    public void close() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.error("Failed to close HTTP client", e);
        }
    }
}
