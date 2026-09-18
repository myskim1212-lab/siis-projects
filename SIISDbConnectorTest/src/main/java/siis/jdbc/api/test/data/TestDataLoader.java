package siis.jdbc.api.test.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * ?뚯뒪??由ъ냼??classpath)?먯꽌 JSON ?뚯씪???쎌뼱?ㅻ뒗 ?좏떥由ы떚.
 *
 * <p>紐⑤뱺 湲곕뒫 ?뚯뒪?몃뒗 ???대옒?ㅻ? ?듯빐 JSON ?곗씠?곕? 濡쒕뱶?섏뿬
 * ?뚯뒪???곗씠???앹꽦 諛⑹떇???쇨??섍쾶 ?좎??쒕떎.
 *
 * <pre>
 * // 湲곕낯 ?ъ슜踰?
 * String json = TestDataLoader.loadJson("TC001.json");
 * JsonNode node = TestDataLoader.loadJsonNode("TC001.json");
 * </pre>
 */
public class TestDataLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestDataLoader() {}

    /**
     * ?대옒?ㅽ뙣??由ъ냼?ㅼ뿉??JSON 臾몄옄?댁쓣 諛섑솚?쒕떎.
     *
     * @param classpathResource ?대옒?ㅽ뙣??湲곗? 寃쎈줈 (?? "TC001.json", "mssql/TC051.json")
     * @return JSON 臾몄옄??
     * @throws IllegalStateException ?뚯씪???녾굅???쎄린 ?ㅽ뙣 ??
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
     * ?대옒?ㅽ뙣??由ъ냼?ㅼ뿉??Jackson {@link JsonNode}瑜?諛섑솚?쒕떎.
     *
     * @param classpathResource ?대옒?ㅽ뙣??湲곗? 寃쎈줈
     * @return ?뚯떛??{@link JsonNode}
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
     * ?대옒?ㅽ뙣??由ъ냼??議댁옱 ?щ?瑜??뺤씤?쒕떎.
     *
     * @param classpathResource ?대옒?ㅽ뙣??湲곗? 寃쎈줈
     * @return 議댁옱?섎㈃ {@code true}
     */
    public static boolean exists(String classpathResource) {
        return TestDataLoader.class.getClassLoader()
                .getResourceAsStream(classpathResource) != null;
    }
}