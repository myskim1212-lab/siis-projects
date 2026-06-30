package sec.siis.connection;

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
}
