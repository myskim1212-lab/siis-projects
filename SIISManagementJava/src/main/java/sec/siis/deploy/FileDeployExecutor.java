package sec.siis.deploy;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

/**
 * SSH 접속이 안 되는 인스턴스에서 JAR/시퀀스 파일을 배포/조회/삭제하는 순수 로직 —
 * Synapse에 전혀 의존하지 않아 단위 테스트가 쉽다 (mediate() 쪽 배선은
 * FileDeployMediator가 담당).
 *
 * wso2-mgmt-tool은 지금까지 JAR/시퀀스 배포를 SSH/SFTP로만 했는데(Management API에
 * 대응 엔드포인트가 없어서), SSH를 켤 수 없는 인스턴스는 배포 자체가 불가능했다.
 * 이 클래스는 대상 MI/EI 서버 안에서 직접 실행되므로, 클라이언트가 파일 내용을
 * base64로 실어 보내면 이 서버 프로세스가 자기 자신의 lib/시퀀스 디렉터리에
 * 파일을 쓰는 방식으로 SSH 없이도 같은 결과를 얻는다.
 */
public class FileDeployExecutor {

    public enum ArtifactType { JAR, SEQUENCE }

    private final Path libDir;
    private final Path sequenceDir;

    public FileDeployExecutor() {
        this(resolveDefaultLibDir(), resolveDefaultSequenceDir());
    }

    public FileDeployExecutor(Path libDir, Path sequenceDir) {
        this.libDir = libDir.toAbsolutePath().normalize();
        this.sequenceDir = sequenceDir.toAbsolutePath().normalize();
    }

    private static Path resolveDefaultLibDir() {
        return carbonHomeBase().resolve("lib");
    }

    private static Path resolveDefaultSequenceDir() {
        return carbonHomeBase().resolve("repository")
                .resolve("deployment").resolve("server")
                .resolve("synapse-configs").resolve("default").resolve("sequences");
    }

    private static Path carbonHomeBase() {
        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome != null && !carbonHome.trim().isEmpty()) {
            return Paths.get(carbonHome);
        }
        return Paths.get(".");
    }

    private Path dirFor(ArtifactType type) {
        return type == ArtifactType.JAR ? libDir : sequenceDir;
    }

    private String extensionFor(ArtifactType type) {
        return type == ArtifactType.JAR ? ".jar" : ".xml";
    }

    /** 대상 디렉터리 아래 파일 목록(이름/크기/최종수정시각) — 최근 수정순 정렬. */
    public List<Map<String, Object>> listFiles(ArtifactType type) throws IOException {
        Path dir = dirFor(type);
        if (!Files.isDirectory(dir)) {
            return new ArrayList<>();
        }
        String ext = extensionFor(type);
        List<Map<String, Object>> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(ext))
                    .sorted(Comparator.comparingLong(this::lastModifiedSafe).reversed())
                    .collect(Collectors.toList());
            for (Path p : files) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", p.getFileName().toString());
                m.put("size", Files.size(p));
                m.put("lastModified", Files.getLastModifiedTime(p).toMillis());
                result.add(m);
            }
        }
        return result;
    }

    private long lastModifiedSafe(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 기존 파일 내용을 base64로 돌려준다 — 클라이언트가 덮어쓰기/삭제 전에 백업하는
     * 용도(SSH 방식에서 하던 것과 동일한 흐름). 파일이 없으면 exists=false만 반환한다
     * (에러 아님 — 최초 배포 시 당연히 없을 수 있다).
     */
    public Map<String, Object> download(ArtifactType type, String fileName) throws IOException {
        Path target = resolveTarget(type, fileName, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", fileName);
        if (target == null || !Files.isRegularFile(target)) {
            result.put("exists", false);
            return result;
        }
        byte[] content = Files.readAllBytes(target);
        result.put("exists", true);
        result.put("size", content.length);
        result.put("content", java.util.Base64.getEncoder().encodeToString(content));
        return result;
    }

    /**
     * 파일을 배포(신규 등록 또는 덮어쓰기)한다. SEQUENCE 타입은 루트 엘리먼트가
     * &lt;sequence&gt;인지까지 확인한다 — Proxy Service/Endpoint/API 등 다른 Synapse
     * 아티팩트를 실수로 시퀀스 hot-deploy 경로에 올리는 사고를 막기 위함
     * (wso2-mgmt-tool의 SSH 배포 경로가 이미 하는 검증과 동일).
     */
    public Map<String, Object> deploy(ArtifactType type, String fileName, byte[] content) throws IOException {
        validateFileName(type, fileName);
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File content is empty");
        }
        if (type == ArtifactType.SEQUENCE) {
            validateSequenceXml(content);
        }

        Path dir = dirFor(type);
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName).normalize();
        if (!target.startsWith(dir)) {
            throw new SecurityException("Invalid file name: " + fileName);
        }

        Files.write(target, content);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", fileName);
        result.put("path", target.toString());
        result.put("size", content.length);
        return result;
    }

    /** 파일을 삭제한다. 없었으면 existed=false(에러 아님). */
    public Map<String, Object> delete(ArtifactType type, String fileName) throws IOException {
        Path target = resolveTarget(type, fileName, false);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", fileName);
        if (target == null || !Files.isRegularFile(target)) {
            result.put("existed", false);
            return result;
        }
        Files.delete(target);
        result.put("existed", true);
        return result;
    }

    private void validateFileName(ArtifactType type, String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("file name is required");
        }
        String ext = extensionFor(type);
        if (!fileName.toLowerCase().endsWith(ext)) {
            throw new IllegalArgumentException(
                    type + " 파일은 " + ext + " 확장자여야 합니다: " + fileName);
        }
    }

    /**
     * 요청된 파일명을 대상 디렉터리 기준으로 해석하고 검증한다 — 상위 경로 이동("..")
     * 등으로 디렉터리 밖의 임의 파일에 접근할 수 없도록 정규화 후 접두사를 확인한다.
     * requireExists=false면 존재하지 않아도 null 대신 경로만 돌려주지 않고(호출부가
     * Files.isRegularFile로 직접 존재 확인), requireExists=true면 없을 때 예외를 던진다.
     */
    private Path resolveTarget(ArtifactType type, String fileName, boolean requireExists) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("file name is required");
        }
        Path dir = dirFor(type);
        Path candidate = dir.resolve(fileName).normalize();
        if (!candidate.startsWith(dir)) {
            throw new SecurityException("Invalid file name: " + fileName);
        }
        if (requireExists && !Files.isRegularFile(candidate)) {
            throw new FileNotFoundException("File not found: " + fileName);
        }
        return candidate;
    }

    /**
     * XML 루트 엘리먼트가 &lt;sequence&gt;(네임스페이스 무관, 로컬 이름만 비교)인지 확인한다.
     * 외부에서 올라온 파일이라 DOCTYPE 선언을 금지해 XXE(External Entity) 주입을 차단한다.
     */
    private void validateSequenceXml(byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 기본 에러 핸들러는 파싱 실패 시 stderr에 원시 SAX 에러를 그대로 찍는다 —
            // 외부에서 올라온 파일이라 잘못된 XML은 흔한 입력이므로 조용히 예외만 던지게 한다.
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            Element root = builder.parse(new ByteArrayInputStream(content)).getDocumentElement();
            String localName = root.getLocalName() != null ? root.getLocalName() : root.getTagName();
            if (!"sequence".equals(localName)) {
                throw new IllegalArgumentException(
                        "이 XML은 루트 엘리먼트가 <" + localName + ">라 시퀀스가 아닙니다. "
                        + "시퀀스 배포는 <sequence> 루트 엘리먼트를 가진 파일만 지원합니다.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("유효한 시퀀스 XML이 아닙니다: " + e.getMessage(), e);
        }
    }
}
