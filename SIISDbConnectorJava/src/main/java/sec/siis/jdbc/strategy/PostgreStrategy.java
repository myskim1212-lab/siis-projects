package sec.siis.jdbc.strategy;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;

import sec.siis.jdbc.config.EffectiveOperationConfig;
import sec.siis.jdbc.config.JdbcConfig;
import sec.siis.jdbc.util.CommonUtil;

public class PostgreStrategy extends BaseDbStrategy {

	public PostgreStrategy(String msgID) {
        super(msgID);
    }
	
	@Override
	public DbType getDbType() {
		return DbType.POSTGRES;
	}

	@Override
	protected void bindParameter(
	        PreparedStatement pstmt,
	        int index,
	        EffectiveOperationConfig config,
	        Object value,
	        int sqlType) throws SQLException {

	    if (value == null) {
	        pstmt.setNull(index, sqlType);
	        return;
	    }

	    // 1. DATE 처리
	    if (sqlType == Types.DATE && value instanceof String) {
	        Object convertedValue = CommonUtil.TemporalParse((String) value, Types.DATE, config.getDate_formats());
	        pstmt.setDate(index, (java.sql.Date) convertedValue);
	        return;
	    }

	    // 2. TIMESTAMP 처리 (PostgreSQL 밀리초 정밀도 유지 전략)
	    if (sqlType == Types.TIMESTAMP && value instanceof String) {
	        Object convertedValue = CommonUtil.TemporalParse((String) value, Types.TIMESTAMP, config.getTimestamp_ntz_formats());
	        if (convertedValue instanceof java.sql.Timestamp) {
	            // PostgreSQL 드라이버가 밀리초(.SSS)를 누락하지 않도록 setObject에 타입을 명시
	            pstmt.setObject(index, convertedValue, Types.TIMESTAMP);
	        }
	        return;
	    }

	    // TIMESTAMPTZ (WITH TIME ZONE)
	    if (sqlType == Types.TIMESTAMP_WITH_TIMEZONE && value instanceof String) {
	        Object convertedValue = CommonUtil.TemporalParse(
	            (String) value,
	            Types.TIMESTAMP_WITH_TIMEZONE,
	            config.getTimestamp_formats()
	        );

	        if (convertedValue instanceof OffsetDateTime odt) {
	            // TZ 보존 경로 - PostgreSQL 42.x+ setObject(OffsetDateTime) 지원
	            pstmt.setObject(index, odt);
	        } else if (convertedValue instanceof LocalDateTime ldt) {
	            // TZ 없는 문자열 입력 → NTZ 폴백
	            pstmt.setObject(index, ldt, Types.TIMESTAMP);
	        } else if (convertedValue instanceof java.sql.Timestamp ts) {
	            // Timestamp 폴백
	            pstmt.setObject(index, ts, Types.TIMESTAMP_WITH_TIMEZONE);
	        }
	        return;
	    }

	    // 3. BLOB 처리 (PostgreSQL은 주로 bytea 타입을 사용)
	    if (sqlType == Types.BLOB) {
	        byte[] bytes;
	        if (value instanceof byte[]) {
	            bytes = (byte[]) value;
	        } else if (value instanceof String) {
	            bytes = Base64.getDecoder().decode((String) value);
	        } else if (value instanceof InputStream) {
	            try {
	                bytes = ((InputStream) value).readAllBytes();
	            } catch (IOException e) {
	                throw new SQLException("Failed to read InputStream for BLOB", e);
	            }
	        } else {
	            throw new IllegalArgumentException("Unsupported BLOB value type: " + value.getClass());
	        }
	        // PostgreSQL bytea 컬럼은 setBytes가 가장 안정적
	        pstmt.setBytes(index, bytes);
	        return;
	    }

	    // 4. CLOB 처리 (PostgreSQL은 주로 text 타입을 사용)
	    if (sqlType == Types.CLOB) {
	        if (value instanceof String) {
	            pstmt.setString(index, (String) value);
	        } else if (value instanceof Reader) {
	            pstmt.setCharacterStream(index, (Reader) value);
	        } else {
	            throw new IllegalArgumentException("Unsupported CLOB value type: " + value.getClass());
	        }
	        return;
	    }

	    // 5. 그 외 기본 타입은 부모 클래스(BaseDbStrategy)에 위임
	    super.bindParameter(pstmt, index, config, value, sqlType);
	}

	// ======================================================================
	// BLOB 읽기 — bytea 컬럼은 getBlob() 대신 getBytes() 사용
	// pgjdbc의 getBlob()은 내부적으로 OID(Long)로 파싱을 시도하여
	// bytea 컬럼에서 PSQLException이 발생하므로 override 필요
	// ======================================================================
	@Override
	protected Object extractValueByType(ResultSet rs, int index, int type, JdbcConfig jcfg) throws SQLException {
	    if (type == Types.BLOB) {
	        byte[] bytes = rs.getBytes(index);
	        return (bytes != null) ? Base64.getEncoder().encodeToString(bytes) : null;
	    }
	    // PostgreSQL getClob() internally calls getLong() to resolve OID,
	    // which fails on text/varchar columns with "Bad value for type long".
	    // Use getString() instead — PostgreSQL text columns are natively UTF-8 strings.
	    if (type == Types.CLOB || type == Types.NCLOB) {
	        return rs.getString(index);
	    }
	    // PostgreSQL normalizes TIMESTAMPTZ to UTC internally and returns UTC OffsetDateTime.
	    // Convert to the configured zone (FIXED → fixed_zone_id) before formatting.
	    if (type == Types.TIMESTAMP_WITH_TIMEZONE) {
	        if (rs.getObject(index) == null) return null;
	        try {
	            OffsetDateTime odt = rs.getObject(index, OffsetDateTime.class);
	            if (odt != null) {
	                return odt.atZoneSameInstant(jcfg.getResolvedZoneId())
	                          .format(jcfg.getPrimaryTimestampFormatter());
	            }
	        } catch (Exception ignored) {}
	        Timestamp ts = rs.getTimestamp(index);
	        if (ts == null) return null;
	        return ts.toInstant()
	                 .atZone(jcfg.getResolvedZoneId())
	                 .format(jcfg.getPrimaryTimestampFormatter());
	    }
	    return super.extractValueByType(rs, index, type, jcfg);
	}

}
