$utf8NoBom = New-Object System.Text.UTF8Encoding($False)
$base = "D:\wso2\WSO2-Integration-Studio-8.5.0-win32-x86_64\workspace\SIISDbConnectorTest"

# Ensure directories exist
New-Item -ItemType Directory -Force -Path "$base\src\main\java\siis\jdbc\api\test\data" | Out-Null
New-Item -ItemType Directory -Force -Path "$base\src\test\resources" | Out-Null
New-Item -ItemType Directory -Force -Path "$base\src\test\resources\mssql" | Out-Null

# FILE 1: TestDataLoader.java
$content = @'
package siis.jdbc.api.test.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 테스트 리소스(classpath)에서 JSON 파일을 읽어오는 유틸리티.
 *
 * <p>모든 기능 테스트는 이 클래스를 통해 JSON 데이터를 로드하여
 * 테스트 데이터 생성 방식을 일관되게 유지한다.
 *
 * <pre>
 * // 기본 사용법
 * String json = TestDataLoader.loadJson("TC001.json");
 * JsonNode node = TestDataLoader.loadJsonNode("TC001.json");
 * </pre>
 */
public class TestDataLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestDataLoader() {}

    /**
     * 클래스패스 리소스에서 JSON 문자열을 반환한다.
     *
     * @param classpathResource 클래스패스 기준 경로 (예: "TC001.json", "mssql/TC051.json")
     * @return JSON 문자열
     * @throws IllegalStateException 파일이 없거나 읽기 실패 시
     */
    public static String loadJson(String classpathResource) {
        InputStream is = TestDataLoader.class.getClassLoader()
                .getResourceAsStream(classpathResource);
        if (is == null) {
            throw new IllegalStateException(
                    "Test resource not found on classpath: " + classpathResource);
        }
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read test resource: " + classpathResource, e);
        }
    }

    /**
     * 클래스패스 리소스에서 Jackson {@link JsonNode}를 반환한다.
     *
     * @param classpathResource 클래스패스 기준 경로
     * @return 파싱된 {@link JsonNode}
     */
    public static JsonNode loadJsonNode(String classpathResource) {
        try {
            return MAPPER.readTree(loadJson(classpathResource));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to parse JSON resource: " + classpathResource, e);
        }
    }

    /**
     * 클래스패스 리소스 존재 여부를 확인한다.
     *
     * @param classpathResource 클래스패스 기준 경로
     * @return 존재하면 {@code true}
     */
    public static boolean exists(String classpathResource) {
        return TestDataLoader.class.getClassLoader()
                .getResourceAsStream(classpathResource) != null;
    }
}
'@
[System.IO.File]::WriteAllText("$base\src\main\java\siis\jdbc\api\test\data\TestDataLoader.java", $content, $utf8NoBom)
Write-Host "FILE 1: TestDataLoader.java - OK"
