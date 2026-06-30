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

public class MySqlStrategy extends BaseDbStrategy {

    public MySqlStrategy(String msgID) {
        super(msgID);
    }

    @Override
    public DbType getDbType() {
        return DbType.MYSQL;
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

        // 2. TIMESTAMP 처리 (MySQL DATETIME 컬럼 대응)
        if (sqlType == Types.TIMESTAMP && value instanceof String) {
            Object convertedValue = CommonUtil.TemporalParse((String) value, Types.TIMESTAMP, config.getTimestamp_ntz_formats());
            if (convertedValue instanceof java.sql.Timestamp) {
                pstmt.setTimestamp(index, (java.sql.Timestamp) convertedValue);
            }
            return;
        }

        // 3. TIMESTAMP_WITH_TIMEZONE 처리
        // MySQL/MariaDB는 TIMESTAMP WITH TIME ZONE 미지원
        // → OffsetDateTime을 UTC Instant 기준 Timestamp로 변환하여 저장
        if (sqlType == Types.TIMESTAMP_WITH_TIMEZONE && value instanceof String) {
            Object converted = CommonUtil.TemporalParse(
                (String) value,
                Types.TIMESTAMP_WITH_TIMEZONE,
                config.getTimestamp_formats()
            );
            if (converted instanceof OffsetDateTime odt) {
                pstmt.setTimestamp(index, Timestamp.from(odt.toInstant())); // UTC 기준 변환
            } else if (converted instanceof LocalDateTime ldt) {
                pstmt.setTimestamp(index, Timestamp.valueOf(ldt));
            }
            return;
        }

        // 4. BLOB 처리 (MySQL BLOB/MEDIUMBLOB/LONGBLOB)
        // setBytes()가 MySQL/MariaDB BLOB 컬럼에 가장 안정적
        if (sqlType == Types.BLOB) {
            if (value instanceof byte[]) {
                pstmt.setBytes(index, (byte[]) value);
            } else if (value instanceof String) {
                pstmt.setBytes(index, Base64.getDecoder().decode((String) value));
            } else if (value instanceof InputStream) {
                try {
                    pstmt.setBytes(index, ((InputStream) value).readAllBytes());
                } catch (IOException e) {
                    throw new SQLException("Failed to read InputStream for BLOB", e);
                }
            } else {
                throw new IllegalArgumentException("Unsupported BLOB value type: " + value.getClass());
            }
            return;
        }

        // 5. CLOB 처리 (MySQL TEXT/MEDIUMTEXT/LONGTEXT)
        // setString()이 TEXT 계열 컬럼에 가장 안정적
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

        // 6. 그 외 기본 타입은 부모 클래스에 위임
        super.bindParameter(pstmt, index, config, value, sqlType);
    }

    // ======================================================================
    // BLOB/CLOB 읽기
    // MySQL/MariaDB BLOB(LONGBLOB)은 getBytes(), TEXT(LONGTEXT)는 getString()
    // ======================================================================
    @Override
    protected Object extractValueByType(ResultSet rs, int index, int type, JdbcConfig jcfg) throws SQLException {
        if (type == Types.BLOB) {
            byte[] bytes = rs.getBytes(index);
            return (bytes != null) ? Base64.getEncoder().encodeToString(bytes) : null;
        }
        if (type == Types.CLOB || type == Types.NCLOB) {
            return rs.getString(index); // TEXT 계열은 getString()이 안전
        }
        return super.extractValueByType(rs, index, type, jcfg);
    }
}
