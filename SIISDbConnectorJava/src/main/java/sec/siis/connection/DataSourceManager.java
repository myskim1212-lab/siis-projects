package sec.siis.connection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sec.siis.jdbc.logging.LogMessageManager;

public class DataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);

    // DataSource cache keyed by JNDI name — looked up only once per name
    private static final ConcurrentHashMap<String, DataSource> cache
        = new ConcurrentHashMap<>();

    private DataSourceManager() {}

    public static DataSource get(String jndiName) {
        return cache.computeIfAbsent(jndiName, name -> {
            LogMessageManager.debugJndiLookup(log, name);
            try {
                Context ctx = new InitialContext();
                DataSource ds = (DataSource) ctx.lookup(name);
                LogMessageManager.infoJndiResolved(log, name);
                return ds;
            } catch (NamingException e) {
                log.error("[{}] JNDI lookup failed: {}", name, e.getMessage());
                throw new RuntimeException("JNDI lookup failed: " + name, e);
            }
        });
    }

    /**
     * 지금까지 최소 한 번 이상 조회되어 캐시에 올라온 데이터소스 목록의 스냅샷.
     * JVM 모니터링(커넥션 풀 상태 조회)에서 쓴다 — 한 번도 쓰인 적 없는 데이터소스는
     * 애초에 풀이 초기화되지 않았을 수 있어 대상에서 자연히 제외된다.
     */
    public static Map<String, DataSource> snapshot() {
        return new LinkedHashMap<>(cache);
    }
}
