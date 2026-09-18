package siis.jdbc.api.test.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 테스트 설정
 */
public class TestConfig {
    
    private static final Properties properties = new Properties();
    
    private static String endPoint;
    
    static {
        try (InputStream input = TestConfig.class
                .getClassLoader()
                .getResourceAsStream("test.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static String getBaseUrl() {
        return properties.getProperty("api.base.url", "http://localhost:8290/mi/jdbc/v1/");
    }
    
    public static String getEndpoint(String endpoint) {
        return endPoint;
    }
    
    public static void setEndpoint(String endpoint) {
    	endPoint=endpoint;
    }
    
    public static int getRequestTimeout() {
        return Integer.parseInt(properties.getProperty("api.request.timeout", "30000"));
    }
    
    public static int getConcurrentUsers() {
        return Integer.parseInt(properties.getProperty("performance.concurrent.users", "10"));
    }
    
    public static int getTotalRequests() {
        return Integer.parseInt(properties.getProperty("performance.total.requests", "100"));
    }
    
    public static int getTestDuration() {
        return Integer.parseInt(properties.getProperty("performance.duration.seconds", "60"));
    }
    
    public static String getReportOutputDir() {
        return properties.getProperty("report.output.dir", "target/test-reports");
    }
}
