package sec.siis.jdbc.config;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlParamExtractor {

    // '...' 내부의 :xx (날짜 포맷 등)를 파라미터로 오인하지 않도록 문자열 리터럴을 먼저 소비한다.
    // (?<!:) : PostgreSQL :: 타입 캐스트 연산자(예: value::text)의 오탐을 방지하는 negative lookbehind
    // group(1) == null → 문자열 리터럴 매칭 → 건너뜀
    // group(1) != null → 실제 named parameter
    private static final Pattern PARAM_PATTERN = Pattern.compile("'(?:[^']|'')*'|(?<!:):(\\w+)");

    public static Set<String> extractNamedParams(String sql) {
        Set<String> params = new HashSet<>();
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (paramName != null) {
                params.add(paramName);
            }
        }
        return params;
    }
}