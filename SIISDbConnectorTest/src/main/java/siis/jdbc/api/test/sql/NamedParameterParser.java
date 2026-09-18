package siis.jdbc.api.test.sql;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL 의 Named Parameter(:param) 를 JDBC PreparedStatement 용 '?' 로 치환하는 파서.
 *
 * <h3>지원 기능</h3>
 * <ul>
 *   <li>{@code :param} → {@code ?} 단순 치환 (대소문자 구분 없음)</li>
 *   <li>동일 파라미터가 여러 번 등장 → 각 위치마다 바인드 값 삽입</li>
 *   <li>Collection 값 → {@code IN (?, ?, ?)} 동적 확장</li>
 *   <li>빈 Collection → {@code (NULL)} 삽입 (결과 0건 보장)</li>
 *   <li>파라미터 없는 SQL → 원본 그대로 반환, 빈 바인드 목록</li>
 * </ul>
 *
 * <h3>제약</h3>
 * <ul>
 *   <li>SQL 문자열 리터럴 내부의 {@code :word} 는 파라미터로 처리하지 않는다.
 *       (따옴표 문자열 스킵 지원)</li>
 * </ul>
 */
public class NamedParameterParser {

    /** SQL 토큰: 문자열 리터럴 or Named Parameter or 그 외 문자 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "'(?:[^'\\\\]|\\\\.)*'"      // 작은따옴표 문자열 리터럴
            + "|\"(?:[^\"\\\\]|\\\\.)*\"" // 큰따옴표 식별자/문자열
            + "|:([a-zA-Z_][a-zA-Z0-9_]*)" // Named Parameter
    );

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * SQL 을 파싱하여 '?' 로 치환된 SQL 과 바인드 값 목록을 반환한다.
     *
     * @param sql      Named Parameter 가 포함된 원본 SQL
     * @param paramMap 파라미터 이름 → 값 맵 (Collection 허용)
     * @return {@link ParsedSql} 치환된 SQL + 순서대로 정렬된 바인드 값
     */
    public static ParsedSql parse(String sql, Map<String, Object> paramMap) {
        if (sql == null || sql.isBlank()) {
            return new ParsedSql(sql, Collections.emptyList());
        }

        Map<String, Object> normalizedMap = normalizeKeys(paramMap);
        StringBuilder result = new StringBuilder();
        List<Object> boundValues = new ArrayList<>();
        int lastEnd = 0;

        Matcher matcher = TOKEN_PATTERN.matcher(sql);
        while (matcher.find()) {
            // 매칭 전 구간을 결과에 추가
            result.append(sql, lastEnd, matcher.start());
            lastEnd = matcher.end();

            String paramName = matcher.group(1); // Named Param 캡처 그룹

            if (paramName == null) {
                // 문자열 리터럴 → 그대로 유지
                result.append(matcher.group(0));
            } else {
                // Named Parameter → 치환
                String key = paramName.toUpperCase();
                Object value = normalizedMap.get(key);
                expandParam(result, boundValues, value);
            }
        }

        // 마지막 구간 추가
        result.append(sql, lastEnd, sql.length());

        return new ParsedSql(result.toString(), boundValues);
    }

    /**
     * SQL 에 등장하는 Named Parameter 이름 목록을 순서대로 추출한다.
     * 문자열 리터럴 내부는 무시한다.
     *
     * @param sql 원본 SQL
     * @return 등장 순서대로 정렬된 파라미터 이름 리스트 (중복 포함)
     */
    public static List<String> extractParamNames(String sql) {
        List<String> names = new ArrayList<>();
        if (sql == null) return names;

        Matcher matcher = TOKEN_PATTERN.matcher(sql);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (paramName != null) {
                names.add(paramName);
            }
        }
        return names;
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    /** 파라미터 맵의 키를 모두 대문자로 정규화 */
    private static Map<String, Object> normalizeKeys(Map<String, Object> original) {
        if (original == null) return Collections.emptyMap();
        Map<String, Object> normalized = new LinkedHashMap<>();
        original.forEach((k, v) -> normalized.put(k.toUpperCase(), v));
        return normalized;
    }

    /**
     * 단일 값 → {@code ?} 한 개 / Collection → {@code (?, ?, ...)} 확장.
     * 빈 Collection 은 {@code (NULL)} 로 처리한다.
     */
    @SuppressWarnings("unchecked")
    private static void expandParam(StringBuilder sql,
                                    List<Object> boundValues,
                                    Object value) {
        if (value instanceof Collection) {
            Collection<Object> col = (Collection<Object>) value;
            if (col.isEmpty()) {
                // 빈 컬렉션: IN (NULL) → 항상 0건
                sql.append("(NULL)");
            } else {
                sql.append("(");
                sql.append(col.stream()
                        .map(v -> "?")
                        .collect(Collectors.joining(", ")));
                sql.append(")");
                boundValues.addAll(col);
            }
        } else {
            // 단일 값
            sql.append("?");
            boundValues.add(value);
        }
    }

    // ─── Result Container ────────────────────────────────────────────────────

    /**
     * 파싱 결과: '?' 로 치환된 SQL 과 순서대로 정렬된 바인드 값 목록.
     */
    public static class ParsedSql {

        private final String sql;
        private final List<Object> boundValues;

        public ParsedSql(String sql, List<Object> boundValues) {
            this.sql = sql;
            this.boundValues = Collections.unmodifiableList(boundValues);
        }

        /** '?' 로 치환된 SQL */
        public String getSql() { return sql; }

        /** PreparedStatement.setXxx() 에 사용할 바인드 값 목록 */
        public List<Object> getBoundValues() { return boundValues; }

        /** 바인드 값의 개수 */
        public int getParamCount() { return boundValues.size(); }

        @Override
        public String toString() {
            return "ParsedSql{sql='" + sql + "', boundValues=" + boundValues + "}";
        }
    }
}
