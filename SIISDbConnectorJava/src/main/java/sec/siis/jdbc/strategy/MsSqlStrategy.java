package sec.siis.jdbc.strategy;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;

import sec.siis.jdbc.config.EffectiveOperationConfig;
import sec.siis.jdbc.util.CommonUtil;

public class MsSqlStrategy extends BaseDbStrategy {

	public MsSqlStrategy(String msgID) {
        super(msgID);
    }
	
	@Override
	public DbType getDbType() {
		return DbType.MSSQL;
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

	    // 2. TIMESTAMP 처리 (MSSQL DATETIME2 정밀도 유지)
	    // MSSQL DATETIME은 3.33ms 단위로 반올림되므로, 밀리초 정밀도를 유지하려면 DATETIME2 컬럼 필요
	    if (sqlType == Types.TIMESTAMP && value instanceof String) {
	        Object convertedValue = CommonUtil.TemporalParse((String) value, Types.TIMESTAMP, config.getTimestamp_ntz_formats());
	        if (convertedValue instanceof java.sql.Timestamp) {
	            pstmt.setTimestamp(index, (java.sql.Timestamp) convertedValue);
	        }
	        return;
	    }

	    // TIMESTAMP WITH TIME ZONE 처리
	    if (sqlType == Types.TIMESTAMP_WITH_TIMEZONE && value instanceof String) {
	        Object converted = CommonUtil.TemporalParse(
	            (String) value,
	            Types.TIMESTAMP_WITH_TIMEZONE,
	            config.getTimestamp_formats()
	        );

	        try {
	            pstmt.setObject(index, converted); // OffsetDateTime → DATETIMEOFFSET 보존
	            return;
	        } catch (SQLException ignored) {}

	        // 폴백
	        if (converted instanceof OffsetDateTime odt) {
	            pstmt.setTimestamp(index, Timestamp.from(odt.toInstant()));
	        } else if (converted instanceof LocalDateTime ldt) {
	            pstmt.setTimestamp(index, Timestamp.valueOf(ldt));
	        }
	        return;
	    }

	    // 3. BLOB 처리 (MSSQL VARBINARY(MAX) 대응)
	    if (sqlType == Types.BLOB) {
	        if (value instanceof byte[]) {
	            pstmt.setBytes(index, (byte[]) value);
	        } else if (value instanceof String) {
	            pstmt.setBytes(index, Base64.getDecoder().decode((String) value));
	        } else if (value instanceof InputStream) {
	            try {
	                byte[] bytes = ((InputStream) value).readAllBytes();
	                pstmt.setBytes(index, bytes);
	            } catch (IOException e) {
	                throw new SQLException("Failed to read InputStream for MSSQL BLOB", e);
	            }
	        }
	        return;
	    }

	    // 4. CLOB 처리 (MSSQL VARCHAR(MAX) 또는 NVARCHAR(MAX) 대응)
	    if (sqlType == Types.CLOB) {
	        if (value instanceof String) {
	            pstmt.setString(index, (String) value);
	        } else if (value instanceof Reader) {
	            pstmt.setCharacterStream(index, (Reader) value);
	        }
	        return;
	    }

	    // 5. 기본 처리는 부모 클래스에 위임
	    super.bindParameter(pstmt, index, config, value, sqlType);
	}
}
