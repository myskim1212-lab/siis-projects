package sec.siis.log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
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

/**
 * MI 자신의 로그 파일을 효율적으로 조회하는 순수 로직 — Synapse에 전혀 의존하지 않아
 * 단위 테스트가 쉽다 (mediate() 쪽 배선은 LogViewerMediator가 담당).
 *
 * WSO2 MI 기본 제공 Management API({@code GET /logs?file=})는 부분 읽기를 지원하지
 * 않아 매 조회마다 파일 전체를 서버 메모리에 올려 응답한다 — 로그 뷰어가 몇 초 간격으로
 * 폴링하는 용도로 쓰기엔 대용량 로그 파일에서 서버/네트워크 부담이 크다. 이 클래스는
 * 그 문제를 아래 두 가지 방식으로 해결한다.
 *
 * 1. {@link #tail}      : 파일 끝에서부터 블록 단위로 역방향 탐색해 최근 N줄만 읽는다
 *                          (파일 전체를 읽지 않음 — 처음 화면을 열 때 사용).
 * 2. {@link #readFrom}  : 이전 응답이 알려준 바이트 오프셋부터 이어서 읽는다 — 폴링마다
 *                          "새로 추가된 만큼만" 읽으므로 파일 크기와 무관하게 가볍다.
 */
public class LogFileExecutor {

    private static final int BLOCK_SIZE = 8192;
    // tail() 역방향 탐색 시 최악의 경우(예: 개행이 거의 없는 파일)에도 무한정 읽지
    // 않도록 두는 안전판 — 이 한도에 걸리면 truncated=true로 표시하고 그 지점까지만 본다.
    private static final long MAX_TAIL_SCAN_BYTES = 5L * 1024 * 1024;

    private static final int DEFAULT_MAX_BYTES = 65536;
    private static final int MIN_MAX_BYTES = 1024;
    private static final int MAX_MAX_BYTES = 1024 * 1024;

    private final Path logDir;

    public LogFileExecutor() {
        this(resolveDefaultLogDir());
    }

    public LogFileExecutor(Path logDir) {
        this.logDir = logDir.toAbsolutePath().normalize();
    }

    private static Path resolveDefaultLogDir() {
        String carbonHome = System.getProperty("carbon.home");
        if (carbonHome != null && !carbonHome.trim().isEmpty()) {
            return Paths.get(carbonHome, "repository", "logs");
        }
        return Paths.get("repository", "logs");
    }

    /** 로그 디렉터리 아래 파일 목록(이름/크기/최종수정시각) — 최근 수정순 정렬. */
    public List<Map<String, Object>> listFiles() throws IOException {
        if (!Files.isDirectory(logDir)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(logDir)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(this::lastModifiedSafe).reversed())
                    .collect(Collectors.toList());
            for (Path p : files) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fileName", p.getFileName().toString());
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
     * 파일 끝에서 최근 {@code maxLines}줄만 읽는다. 파일 전체를 읽지 않고, 끝에서부터
     * {@link #BLOCK_SIZE} 단위로 역방향으로 개행 문자만 세어 시작 위치를 찾은 뒤, 그
     * 지점부터 끝까지만 한 번에 읽는다.
     */
    public Map<String, Object> tail(String fileName, int maxLines) throws IOException {
        int limit = maxLines > 0 ? maxLines : 200;
        Path path = resolveFile(fileName);
        long fileLength = Files.size(path);
        boolean truncated = false;
        long startPos;

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long scanFloor = Math.max(fileLength - MAX_TAIL_SCAN_BYTES, 0);
            long pos = fileLength;
            int newlinesFound = 0;
            long foundStart = -1;
            byte[] buffer = new byte[BLOCK_SIZE];

            scan:
            while (pos > scanFloor) {
                int toRead = (int) Math.min(BLOCK_SIZE, pos - scanFloor);
                pos -= toRead;
                raf.seek(pos);
                raf.readFully(buffer, 0, toRead);
                for (int i = toRead - 1; i >= 0; i--) {
                    if (buffer[i] == '\n') {
                        newlinesFound++;
                        if (newlinesFound > limit) {
                            foundStart = pos + i + 1;
                            break scan;
                        }
                    }
                }
            }

            if (foundStart >= 0) {
                startPos = foundStart;
            } else {
                startPos = scanFloor;
                truncated = scanFloor > 0;
            }

            raf.seek(startPos);
            byte[] rest = new byte[(int) (fileLength - startPos)];
            raf.readFully(rest);
            List<String> lines = splitLines(new String(rest, StandardCharsets.UTF_8));
            if (lines.size() > limit) {
                lines = lines.subList(lines.size() - limit, lines.size());
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", fileName);
            result.put("size", fileLength);
            result.put("lines", lines);
            result.put("truncated", truncated);
            result.put("nextOffset", fileLength);
            return result;
        }
    }

    /**
     * 이전 조회 응답의 {@code nextOffset}부터 이어서 최대 {@code maxBytes} 바이트만
     * 새로 읽는다 — 폴링 방식 로그 뷰어(tail -f 유사)를 위한 핵심 메서드. 파일 크기가
     * 아무리 커도 "새로 추가된 만큼"만 읽으므로 비용이 거의 일정하다.
     *
     * 파일이 로테이션/절단되어 offset이 현재 크기보다 커지면(rotated=true로 표시)
     * 처음부터(0) 다시 읽도록 안내한다.
     */
    public Map<String, Object> readFrom(String fileName, long offset, Integer maxBytes) throws IOException {
        int cappedMaxBytes = Math.max(MIN_MAX_BYTES,
                Math.min(maxBytes != null ? maxBytes : DEFAULT_MAX_BYTES, MAX_MAX_BYTES));
        Path path = resolveFile(fileName);
        long fileLength = Files.size(path);

        boolean rotated = false;
        long fromOffset = offset < 0 ? 0 : offset;
        if (fromOffset > fileLength) {
            rotated = true;
            fromOffset = 0;
        }

        long available = fileLength - fromOffset;
        int toRead = (int) Math.min(available, cappedMaxBytes);

        List<String> lines = new ArrayList<>();
        long nextOffset = fromOffset;

        if (toRead > 0) {
            try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
                raf.seek(fromOffset);
                byte[] buffer = new byte[toRead];
                raf.readFully(buffer);

                int lastNewline = lastIndexOf(buffer, (byte) '\n');
                if (lastNewline >= 0) {
                    String chunk = new String(buffer, 0, lastNewline + 1, StandardCharsets.UTF_8);
                    lines = splitLines(chunk);
                    nextOffset = fromOffset + lastNewline + 1;
                } else {
                    // 한 줄이 maxBytes보다 긴 예외적인 경우 — 계속 멈춰있지 않도록 그대로 흘려보낸다.
                    lines.add(new String(buffer, StandardCharsets.UTF_8));
                    nextOffset = fromOffset + toRead;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("size", fileLength);
        result.put("fromOffset", fromOffset);
        result.put("nextOffset", nextOffset);
        result.put("lines", lines);
        result.put("hasMore", fileLength - nextOffset > 0);
        result.put("rotated", rotated);
        return result;
    }

    /**
     * 파일을 처음부터 한 줄씩(BufferedReader) 스트리밍으로 훑으며 키워드를 포함한 줄만
     * 모은다 — 파일 전체를 메모리에 한 번에 올리지 않는다. 매치/스캔 개수에 상한을 두어
     * 큰 파일에서도 처리 시간이 발산하지 않게 한다.
     */
    public Map<String, Object> search(String fileName, String keyword, int maxMatches, int maxLinesScanned)
            throws IOException {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("keyword is required");
        }
        Path path = resolveFile(fileName);
        String needle = keyword.toLowerCase();
        int matchLimit = maxMatches > 0 ? maxMatches : 100;
        int scanLimit = maxLinesScanned > 0 ? maxLinesScanned : 200_000;

        List<Map<String, Object>> matches = new ArrayList<>();
        boolean truncated = false;
        long lineNo = 0;

        try (java.io.BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.toLowerCase().contains(needle)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("lineNumber", lineNo);
                    m.put("text", line);
                    matches.add(m);
                    if (matches.size() >= matchLimit) {
                        truncated = true;
                        break;
                    }
                }
                if (lineNo >= scanLimit) {
                    truncated = true;
                    break;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("keyword", keyword);
        result.put("matches", matches);
        result.put("linesScanned", lineNo);
        result.put("truncated", truncated);
        return result;
    }

    private int lastIndexOf(byte[] buffer, byte target) {
        for (int i = buffer.length - 1; i >= 0; i--) {
            if (buffer[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private List<String> splitLines(String text) {
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        // 마지막이 개행으로 끝나면 split이 만드는 빈 문자열 꼬리를 제거한다.
        String normalized = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> lines = new ArrayList<>();
        for (String l : normalized.split("\n", -1)) {
            lines.add(l.endsWith("\r") ? l.substring(0, l.length() - 1) : l);
        }
        return lines;
    }

    /**
     * 요청된 파일명을 로그 디렉터리 기준으로 해석하고 검증한다 — 상위 경로 이동
     * ("..") 등으로 로그 디렉터리 밖의 임의 파일을 읽을 수 없도록 정규화 후 접두사를
     * 확인한다 (path traversal 방지).
     */
    private Path resolveFile(String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("file name is required");
        }
        Path candidate = logDir.resolve(fileName).normalize();
        if (!candidate.startsWith(logDir)) {
            throw new SecurityException("Invalid file path: " + fileName);
        }
        if (!Files.isRegularFile(candidate)) {
            throw new FileNotFoundException("Log file not found: " + fileName);
        }
        return candidate;
    }
}
