package sec.siis.jdbc.strategy;

/**
 * MariaDB 전용 Strategy.
 *
 * <p>MariaDB는 MySQL과 대부분 호환되므로 {@link MySqlStrategy}를 상속하여 재사용한다.
 * 향후 MariaDB 전용 동작(RETURNING 절, UUID 타입 등)이 필요한 경우 이 클래스에서 override한다.
 *
 * <p>MySQL과의 주요 차이점 (버전별):
 * <ul>
 *   <li>10.5+ : DML RETURNING 절 지원 (MySQL 미지원)</li>
 *   <li>10.7+ : UUID 네이티브 타입 지원</li>
 *   <li>JDBC  : org.mariadb.jdbc.Driver (MySQL Connector/J와 별도)</li>
 * </ul>
 */
public class MariaDbStrategy extends MySqlStrategy {

    public MariaDbStrategy(String msgID) {
        super(msgID);
    }

    @Override
    public DbType getDbType() {
        return DbType.MARIADB;
    }

    // MySQL과 대부분 호환 → MySqlStrategy 그대로 사용
    // MariaDB 전용 동작이 필요한 경우 이 클래스에서 override할 것
}
