package sec.siis.jdbc.util;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CommonUtil {

    /** 포맷 패턴별 DateTimeFormatter 캐시 (불변 객체이므로 스레드 안전). */
    private static final ConcurrentHashMap<String, DateTimeFormatter> FORMATTER_CACHE =
            new ConcurrentHashMap<>();

    /* ================= 주요 메서드 ================= */

	public static Object TemporalParse(String value, int sqlType, List<String> formats) {
	    if (value == null || value.isBlank()) return null;
	    String v = value.trim();

	    // 1. ISO-8601 표준 포맷 우선 시도 (가변 소수점 6자리 등 완벽 대응)
	    // T가 포함되어 있고 + 혹은 Z가 있는 경우 표준 ISO 포맷터 사용
	    if (v.contains("T")) {
	        try {
	            if (v.contains("+") || v.endsWith("Z")) {
	                OffsetDateTime odt = OffsetDateTime.parse(v, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	                return switch (sqlType) {
	                    case Types.DATE                    -> java.sql.Date.valueOf(odt.toLocalDate());
	                    case Types.TIMESTAMP_WITH_TIMEZONE -> odt;          // ← OffsetDateTime 그대로 반환
	                    default                            -> java.sql.Timestamp.from(odt.toInstant());               
	                };	                
	            } else {
	                LocalDateTime ldt = LocalDateTime.parse(v, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	                return (sqlType == Types.DATE) 
	                    ? java.sql.Date.valueOf(ldt.toLocalDate()) 
	                    : java.sql.Timestamp.valueOf(ldt);
	            }
	        } catch (DateTimeParseException e) {
	            // 표준 포맷 실패 시 정의된 사용자 포맷(YAML)으로 넘어감
	        }
	    }

	    // 2. 사용자 정의 포맷 루프 (기존 로직 최적화)
	    List<String> effectiveFormats = resolveFormats(sqlType, formats);
	    for (String fmt : effectiveFormats) {
	        try {
	            DateTimeFormatter f = FORMATTER_CACHE.computeIfAbsent(fmt, pattern ->
	                    new DateTimeFormatterBuilder()
	                            .appendPattern(pattern)
	                            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
	                            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
	                            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
	                            .toFormatter());

	            TemporalAccessor ta = f.parseBest(v, OffsetDateTime::from, LocalDateTime::from, LocalDate::from);

	            if (sqlType == Types.DATE) {
	                if (ta instanceof OffsetDateTime) return java.sql.Date.valueOf(((OffsetDateTime) ta).toLocalDate());
	                if (ta instanceof LocalDateTime) return java.sql.Date.valueOf(((LocalDateTime) ta).toLocalDate());
	                if (ta instanceof LocalDate) return java.sql.Date.valueOf((LocalDate) ta);
	            } else if (sqlType == Types.TIMESTAMP) {
	                if (ta instanceof OffsetDateTime) return java.sql.Timestamp.from(((OffsetDateTime) ta).toInstant());
	                if (ta instanceof LocalDateTime) return java.sql.Timestamp.valueOf((LocalDateTime) ta);
	                if (ta instanceof LocalDate) return java.sql.Timestamp.valueOf(((LocalDate) ta).atStartOfDay());
	            } else if (sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
	                if (ta instanceof OffsetDateTime o) return o;  // ← OffsetDateTime 그대로 반환
	                if (ta instanceof LocalDateTime l)  return l;  // ← TZ 없으면 LocalDateTime 반환
	            }
	        } catch (DateTimeParseException ignore) { }
	    }

	    throw new IllegalArgumentException("지원하지 않는 날짜/시간 형식입니다: " + value);
	}

    /* ================= 헬퍼 메서드 ================= */

    private static List<String> resolveFormats(
            int sqlType,
            List<String> formats
    ) {
        if (formats != null && !formats.isEmpty()) {
            return formats;
        }
        return List.of(); // 안전한 기본값 반환
    }
    
    public static String decodeYamlConfig(String jdbcConfig) throws Exception{
    	
        if (jdbcConfig == null || jdbcConfig.isEmpty()) {
        	throw new Exception("ifConfig property is missing");
        }

        boolean isBase64 = jdbcConfig.matches("^[A-Za-z0-9+/]+={0,2}$") && (jdbcConfig.length() % 4 == 0);
        if(isBase64)
        {
	        // 1. 앞뒤 공백 제거 및 URL-Safe 대응 (언더바(_)를 슬래시(/)로, 대시(-)를 플러스(+)로 복구)
	        // 5f(_) 에러의 직접적인 해결책입니다.
	        String normalized = jdbcConfig.trim()
	                                      .replace('_', '/')
	                                      .replace('-', '+')
	        							  .replace(" ", "+");
	
	        try {
	            // 2. MIME 디코더 사용 (중간에 섞인 줄바꿈(\n, \r)을 알아서 무시함)
	            byte[] decodedBytes = Base64.getMimeDecoder().decode(normalized);
	            return new String(decodedBytes, StandardCharsets.UTF_8);
	        } catch (IllegalArgumentException e) {
	        	throw new RuntimeException("Base64 디코딩 실패. 데이터 길이를 확인하세요: " + jdbcConfig.length());
	        }
        }
        // 2. Base64가 아니면 평문 YAML로 바로 반환
        return jdbcConfig;
    }    
 
}