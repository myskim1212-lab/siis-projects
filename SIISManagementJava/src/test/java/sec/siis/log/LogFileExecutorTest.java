package sec.siis.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogFileExecutorTest {

    @TempDir
    Path logDir;

    private LogFileExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new LogFileExecutor(logDir);
    }

    private Path writeFile(String name, String content) throws IOException {
        Path p = logDir.resolve(name);
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    @Test
    void listFiles_returnsNameSizeAndLastModified() throws IOException {
        writeFile("wso2carbon.log", "line1\nline2\n");

        List<Map<String, Object>> files = executor.listFiles();

        assertEquals(1, files.size());
        assertEquals("wso2carbon.log", files.get(0).get("fileName"));
        assertEquals(12L, files.get(0).get("size"));
        assertTrue(files.get(0).containsKey("lastModified"));
    }

    @Test
    void listFiles_onMissingDirectory_returnsEmptyListInsteadOfThrowing() {
        LogFileExecutor missing = new LogFileExecutor(logDir.resolve("does-not-exist"));
        assertTrue(assertDoesNotThrowList(missing).isEmpty());
    }

    private List<Map<String, Object>> assertDoesNotThrowList(LogFileExecutor exec) {
        try {
            return exec.listFiles();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void tail_returnsOnlyLastNLines_withoutReadingWholeFileConceptually() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 1000; i++) {
            sb.append("line-").append(i).append('\n');
        }
        writeFile("big.log", sb.toString());

        Map<String, Object> result = executor.tail("big.log", 10);

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) result.get("lines");
        assertEquals(10, lines.size());
        assertEquals("line-991", lines.get(0));
        assertEquals("line-1000", lines.get(9));
        assertFalse((Boolean) result.get("truncated"));
    }

    @Test
    void tail_whenFileHasFewerLinesThanRequested_returnsAllOfThem() throws IOException {
        writeFile("small.log", "a\nb\nc\n");

        Map<String, Object> result = executor.tail("small.log", 100);

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) result.get("lines");
        assertEquals(Arrays.asList("a", "b", "c"), lines);
    }

    @Test
    void tail_fileWithoutTrailingNewline_stillReturnsLastLine() throws IOException {
        writeFile("noeof.log", "a\nb\nc");

        Map<String, Object> result = executor.tail("noeof.log", 2);

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) result.get("lines");
        assertEquals(Arrays.asList("b", "c"), lines);
    }

    @Test
    void tail_crossesBlockBoundary_stillCountsLinesCorrectly() throws IOException {
        // 8192바이트 블록 경계를 여러 번 넘나들도록 충분히 많은 줄을 만든다.
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5000; i++) {
            sb.append("row").append(i).append("-0123456789\n"); // 줄마다 약 18바이트
        }
        writeFile("boundary.log", sb.toString());

        Map<String, Object> result = executor.tail("boundary.log", 3);

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) result.get("lines");
        assertEquals(Arrays.asList("row4998-0123456789", "row4999-0123456789", "row5000-0123456789"), lines);
    }

    @Test
    void readFrom_onlyReturnsBytesAppendedSinceLastOffset() throws IOException {
        Path p = writeFile("poll.log", "first\nsecond\n");

        Map<String, Object> first = executor.readFrom("poll.log", 0, null);
        @SuppressWarnings("unchecked")
        List<String> firstLines = (List<String>) first.get("lines");
        assertEquals(Arrays.asList("first", "second"), firstLines);
        long nextOffset = (Long) first.get("nextOffset");
        assertFalse((Boolean) first.get("hasMore"));

        Files.write(p, "third\n".getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);

        Map<String, Object> second = executor.readFrom("poll.log", nextOffset, null);
        @SuppressWarnings("unchecked")
        List<String> secondLines = (List<String>) second.get("lines");
        assertEquals(Arrays.asList("third"), secondLines);
        assertFalse((Boolean) second.get("rotated"));
    }

    @Test
    void readFrom_whenOffsetPastCurrentSize_reportsRotatedAndRestartsFromZero() throws IOException {
        writeFile("rotate.log", "hello\n");

        Map<String, Object> result = executor.readFrom("rotate.log", 999_999L, null);

        assertTrue((Boolean) result.get("rotated"));
        assertEquals(0L, result.get("fromOffset"));
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) result.get("lines");
        assertEquals(Arrays.asList("hello"), lines);
    }

    @Test
    void readFrom_withNoNewlineWithinMaxBytes_stillMakesProgressInstead_ofStalling() throws IOException {
        StringBuilder longLineBuilder = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            longLineBuilder.append('x');
        }
        String longLine = longLineBuilder.toString(); // 개행 없음
        writeFile("longline.log", longLine);

        Map<String, Object> result = executor.readFrom("longline.log", 0, 1024);

        long nextOffset = (Long) result.get("nextOffset");
        assertEquals(1024L, nextOffset);
        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) result.get("lines");
        assertEquals(1, lines.size());
    }

    @Test
    void search_findsMatchingLinesCaseInsensitively() throws IOException {
        writeFile("app.log", "INFO starting\nERROR boom\ninfo done\nerror again\n");

        Map<String, Object> result = executor.search("app.log", "error", 100, 100_000);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertEquals(2, matches.size());
        assertEquals(2L, matches.get(0).get("lineNumber"));
        assertEquals(4L, matches.get(1).get("lineNumber"));
        assertFalse((Boolean) result.get("truncated"));
    }

    @Test
    void search_stopsAtMaxMatches_andMarksTruncated() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("ERROR ").append(i).append('\n');
        }
        writeFile("many.log", sb.toString());

        Map<String, Object> result = executor.search("many.log", "ERROR", 5, 100_000);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertEquals(5, matches.size());
        assertTrue((Boolean) result.get("truncated"));
    }

    @Test
    void resolveFile_rejectsPathTraversalOutsideLogDirectory() throws IOException {
        writeFile("safe.log", "ok\n");

        assertThrows(SecurityException.class, () -> executor.tail("../secret.txt", 10));
    }

    @Test
    void resolveFile_missingFile_throwsFileNotFound() {
        assertThrows(FileNotFoundException.class, () -> executor.tail("no-such-file.log", 10));
    }
}
