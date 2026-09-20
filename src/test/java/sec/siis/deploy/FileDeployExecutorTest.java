package sec.siis.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import sec.siis.deploy.FileDeployExecutor.ArtifactType;

class FileDeployExecutorTest {

    @TempDir
    Path root;

    private Path libDir;
    private Path sequenceDir;
    private FileDeployExecutor executor;

    @BeforeEach
    void setUp() {
        libDir = root.resolve("lib");
        sequenceDir = root.resolve("sequences");
        executor = new FileDeployExecutor(libDir, sequenceDir);
    }

    @Test
    void listFiles_onMissingDirectory_returnsEmptyListInsteadOfThrowing() throws IOException {
        assertTrue(executor.listFiles(ArtifactType.JAR).isEmpty());
    }

    @Test
    void deploy_jar_writesFileUnderLibDir() throws IOException {
        byte[] content = "fake-jar-bytes".getBytes(StandardCharsets.UTF_8);

        Map<String, Object> result = executor.deploy(ArtifactType.JAR, "foo.jar", content);

        assertEquals("foo.jar", result.get("name"));
        assertEquals(content.length, result.get("size"));
        Path written = libDir.resolve("foo.jar");
        assertTrue(Files.isRegularFile(written));
        assertEquals("fake-jar-bytes", new String(Files.readAllBytes(written), StandardCharsets.UTF_8));
    }

    @Test
    void deploy_jar_rejectsWrongExtension() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> executor.deploy(ArtifactType.JAR, "foo.txt", content));
    }

    @Test
    void deploy_sequence_withValidRootElement_succeeds() throws IOException {
        String xml = "<sequence xmlns=\"http://ws.apache.org/ns/synapse\" name=\"MySeq\"><log/></sequence>";
        byte[] content = xml.getBytes(StandardCharsets.UTF_8);

        Map<String, Object> result = executor.deploy(ArtifactType.SEQUENCE, "MySeq.xml", content);

        assertEquals("MySeq.xml", result.get("name"));
        assertTrue(Files.isRegularFile(sequenceDir.resolve("MySeq.xml")));
    }

    @Test
    void deploy_sequence_rejectsNonSequenceRootElement() {
        String xml = "<proxy xmlns=\"http://ws.apache.org/ns/synapse\" name=\"NotASequence\"/>";
        byte[] content = xml.getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> executor.deploy(ArtifactType.SEQUENCE, "bad.xml", content));
        assertTrue(ex.getMessage().contains("proxy"));
    }

    @Test
    void deploy_sequence_rejectsMalformedXml() {
        byte[] content = "not xml at all".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> executor.deploy(ArtifactType.SEQUENCE, "bad.xml", content));
    }

    @Test
    void deploy_rejectsPathTraversalOutsideTargetDirectory() {
        byte[] content = "x".getBytes(StandardCharsets.UTF_8);
        assertThrows(SecurityException.class,
                () -> executor.deploy(ArtifactType.JAR, "../escape.jar", content));
    }

    @Test
    void deploy_rejectsEmptyContent() {
        assertThrows(IllegalArgumentException.class,
                () -> executor.deploy(ArtifactType.JAR, "empty.jar", new byte[0]));
    }

    @Test
    void listFiles_returnsOnlyMatchingExtension_sortedByNewestFirst() throws IOException, InterruptedException {
        executor.deploy(ArtifactType.JAR, "old.jar", "1".getBytes(StandardCharsets.UTF_8));
        Thread.sleep(10);
        executor.deploy(ArtifactType.JAR, "new.jar", "2".getBytes(StandardCharsets.UTF_8));
        Files.write(libDir.resolve("readme.txt"), "not a jar".getBytes(StandardCharsets.UTF_8));

        List<Map<String, Object>> files = executor.listFiles(ArtifactType.JAR);

        assertEquals(2, files.size());
        assertEquals("new.jar", files.get(0).get("name"));
        assertEquals("old.jar", files.get(1).get("name"));
    }

    @Test
    void download_existingFile_returnsBase64Content() throws IOException {
        executor.deploy(ArtifactType.JAR, "foo.jar", "hello".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = executor.download(ArtifactType.JAR, "foo.jar");

        assertEquals(true, result.get("exists"));
        byte[] decoded = java.util.Base64.getDecoder().decode((String) result.get("content"));
        assertEquals("hello", new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    void download_missingFile_reportsNotExistsInsteadOfThrowing() throws IOException {
        Map<String, Object> result = executor.download(ArtifactType.JAR, "no-such.jar");
        assertEquals(false, result.get("exists"));
    }

    @Test
    void delete_existingFile_removesItAndReportsExisted() throws IOException {
        executor.deploy(ArtifactType.SEQUENCE, "MySeq.xml",
                "<sequence xmlns=\"http://ws.apache.org/ns/synapse\"/>".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = executor.delete(ArtifactType.SEQUENCE, "MySeq.xml");

        assertEquals(true, result.get("existed"));
        assertFalse(Files.exists(sequenceDir.resolve("MySeq.xml")));
    }

    @Test
    void delete_missingFile_reportsNotExistedInsteadOfThrowing() throws IOException {
        Map<String, Object> result = executor.delete(ArtifactType.JAR, "no-such.jar");
        assertEquals(false, result.get("existed"));
    }

    @Test
    void deploy_overwritesExistingFile() throws IOException {
        executor.deploy(ArtifactType.JAR, "foo.jar", "v1".getBytes(StandardCharsets.UTF_8));
        executor.deploy(ArtifactType.JAR, "foo.jar", "v2".getBytes(StandardCharsets.UTF_8));

        byte[] finalContent = Files.readAllBytes(libDir.resolve("foo.jar"));
        assertEquals("v2", new String(finalContent, StandardCharsets.UTF_8));
    }
}
