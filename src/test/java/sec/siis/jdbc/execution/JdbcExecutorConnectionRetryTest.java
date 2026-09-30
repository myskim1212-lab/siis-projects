package sec.siis.jdbc.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import sec.siis.jdbc.config.JdbcConfig;

/**
 * JdbcExecutor.acquireConnection()의 재시도(connection_retry_count/interval)와
 * SQL 응답 타임아웃(connection_read_timeout_ms) 적용을 검증한다.
 *
 * 실제 DB/WSO2 없이 순수 로직만 검증하기 위해, DataSource/Connection/DatabaseMetaData는
 * java.lang.reflect.Proxy로 최소 구현한 가짜를 쓴다. acquireConnection()과 jcfg 필드는
 * private이라 리플렉션으로 접근한다 — 프로덕션 코드의 가시성을 테스트 때문에 낮추지 않기 위함.
 *
 * JdbcExecutor 생성자가 내부적으로 StrategyFactory.create()를 통해 DB 타입 판별용
 * getConnection()을 1회 호출하므로(성공해야 생성자가 끝남), 각 테스트는 매번 새로운
 * jndiName을 써서 StrategyFactory의 static 캐시가 테스트 간 간섭하지 않게 한다.
 */
class JdbcExecutorConnectionRetryTest {

    private static int seq = 0;

    private static String uniqueJndi() {
        return "test/retry/jndi/" + System.nanoTime() + "/" + (seq++);
    }

    // ── 가짜 DataSource — 생성 직후에는 항상 성공하고(타입 판별용),
    //    이후 setRemainingFailures()로 지정한 횟수만큼만 실패한다 ──────────────

    private static class FakeDataSource implements DataSource {
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final Connection successConnection;
        private volatile int remainingFailures = 0;

        FakeDataSource(Connection successConnection) {
            this.successConnection = successConnection;
        }

        void setRemainingFailures(int n) {
            this.remainingFailures = n;
        }

        int getCallCount() {
            return callCount.get();
        }

        @Override
        public Connection getConnection() throws SQLException {
            callCount.incrementAndGet();
            synchronized (this) {
                if (remainingFailures > 0) {
                    remainingFailures--;
                    throw new SQLException("simulated connection failure", "08006");
                }
            }
            return successConnection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override public PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() {
            throw new UnsupportedOperationException();
        }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    // ── 가짜 Connection/DatabaseMetaData — 필요한 메서드만 동작, 나머지는 기본값 반환 ──

    private static Object defaultFor(Class<?> returnType) {
        if (returnType == void.class) return null;
        if (returnType == boolean.class) return Boolean.FALSE;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        if (returnType == short.class) return (short) 0;
        if (returnType == byte.class) return (byte) 0;
        if (returnType == char.class) return (char) 0;
        if (returnType == float.class) return 0f;
        if (returnType == double.class) return 0d;
        return null;
    }

    /**
     * @param networkTimeoutSetCount setNetworkTimeout() 호출 횟수를 기록할 카운터 (null이면 무시)
     * @param lastNetworkTimeoutMs   마지막으로 전달된 timeout 값(ms)을 기록 (null이면 무시)
     */
    private static Connection fakeConnection(AtomicInteger networkTimeoutSetCount, AtomicInteger lastNetworkTimeoutMs) {
        InvocationHandler metaHandler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDatabaseProductName": return "Oracle";
                case "getDatabaseProductVersion": return "19c";
                case "getURL": return "jdbc:oracle:thin:@fake";
                case "getUserName": return "faketest";
                case "toString": return "FakeDatabaseMetaData";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                default: return defaultFor(method.getReturnType());
            }
        };
        DatabaseMetaData meta = (DatabaseMetaData) Proxy.newProxyInstance(
                JdbcExecutorConnectionRetryTest.class.getClassLoader(),
                new Class<?>[] { DatabaseMetaData.class }, metaHandler);

        InvocationHandler connHandler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getMetaData": return meta;
                case "close": return null;
                case "isClosed": return false;
                case "setAutoCommit": return null;
                case "setNetworkTimeout":
                    if (networkTimeoutSetCount != null) networkTimeoutSetCount.incrementAndGet();
                    if (lastNetworkTimeoutMs != null) lastNetworkTimeoutMs.set((Integer) args[1]);
                    return null;
                case "toString": return "FakeConnection";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                default: return defaultFor(method.getReturnType());
            }
        };
        return (Connection) Proxy.newProxyInstance(
                JdbcExecutorConnectionRetryTest.class.getClassLoader(),
                new Class<?>[] { Connection.class }, connHandler);
    }

    // ── 리플렉션 헬퍼 ──────────────────────────────────────────────────────

    private static JdbcExecutor newExecutor(DataSource ds, String jndiName) throws Exception {
        return new JdbcExecutor(ds, jndiName, "TEST-MSG-ID");
    }

    private static void setJcfg(JdbcExecutor executor, JdbcConfig jcfg) throws Exception {
        Field f = JdbcExecutor.class.getDeclaredField("jcfg");
        f.setAccessible(true);
        f.set(executor, jcfg);
    }

    private static Connection invokeAcquireConnection(JdbcExecutor executor) throws Throwable {
        Method m = JdbcExecutor.class.getDeclaredMethod("acquireConnection");
        m.setAccessible(true);
        try {
            return (Connection) m.invoke(executor);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static JdbcConfig jcfgWith(Integer retryCount, Long retryIntervalMs, Long readTimeoutMs) {
        JdbcConfig jcfg = new JdbcConfig();
        jcfg.setConnection_retry_count(retryCount);
        jcfg.setConnection_retry_interval_ms(retryIntervalMs);
        jcfg.setConnection_read_timeout_ms(readTimeoutMs);
        return jcfg;
    }

    // ── 테스트 ────────────────────────────────────────────────────────────

    @Test
    void succeedsOnFirstAttempt_whenNoFailure() throws Throwable {
        Connection okConn = fakeConnection(null, null);
        FakeDataSource ds = new FakeDataSource(okConn);
        JdbcExecutor executor = newExecutor(ds, uniqueJndi());
        int callsAfterConstruction = ds.getCallCount();

        setJcfg(executor, jcfgWith(0, 0L, 0L));

        Connection result = invokeAcquireConnection(executor);

        assertSame(okConn, result);
        assertEquals(callsAfterConstruction + 1, ds.getCallCount());
    }

    @Test
    void retriesAndEventuallySucceeds_whenFailuresWithinRetryCount() throws Throwable {
        Connection okConn = fakeConnection(null, null);
        FakeDataSource ds = new FakeDataSource(okConn);
        JdbcExecutor executor = newExecutor(ds, uniqueJndi());
        int callsAfterConstruction = ds.getCallCount();
        ds.setRemainingFailures(2); // 처음 2번 실패, 3번째 성공

        setJcfg(executor, jcfgWith(3, 0L, 0L)); // 재시도 3회 허용(총 최대 4번 시도)

        Connection result = invokeAcquireConnection(executor);

        assertSame(okConn, result);
        // 실패 2번 + 성공 1번 = 3번 호출로 끝나야 함 (불필요한 추가 시도 없음)
        assertEquals(callsAfterConstruction + 3, ds.getCallCount());
    }

    @Test
    void throwsLastFailure_whenAllRetriesExhausted() throws Throwable {
        Connection okConn = fakeConnection(null, null);
        FakeDataSource ds = new FakeDataSource(okConn);
        JdbcExecutor executor = newExecutor(ds, uniqueJndi());
        int callsAfterConstruction = ds.getCallCount();
        ds.setRemainingFailures(100); // 계속 실패

        setJcfg(executor, jcfgWith(2, 0L, 0L)); // 총 시도 = 1 + 2 = 3번

        try {
            invokeAcquireConnection(executor);
            fail("SQLException이 발생해야 한다");
        } catch (SQLException e) {
            assertTrue(e.getMessage().contains("simulated connection failure"));
        }
        // 정확히 (1 + retry_count)번만 시도하고 멈춰야 함 — 그 이상 재시도하지 않음
        assertEquals(callsAfterConstruction + 3, ds.getCallCount());
    }

    @Test
    void appliesNetworkTimeout_whenConnectionReadTimeoutConfigured() throws Throwable {
        AtomicInteger setCount = new AtomicInteger();
        AtomicInteger lastTimeout = new AtomicInteger();
        Connection okConn = fakeConnection(setCount, lastTimeout);
        FakeDataSource ds = new FakeDataSource(okConn);
        JdbcExecutor executor = newExecutor(ds, uniqueJndi());

        setJcfg(executor, jcfgWith(0, 0L, 60000L));

        invokeAcquireConnection(executor);

        assertEquals(1, setCount.get());
        assertEquals(60000, lastTimeout.get());
    }

    @Test
    void doesNotApplyNetworkTimeout_whenReadTimeoutIsZero() throws Throwable {
        AtomicInteger setCount = new AtomicInteger();
        Connection okConn = fakeConnection(setCount, new AtomicInteger());
        FakeDataSource ds = new FakeDataSource(okConn);
        JdbcExecutor executor = newExecutor(ds, uniqueJndi());

        setJcfg(executor, jcfgWith(0, 0L, 0L));

        invokeAcquireConnection(executor);

        assertEquals(0, setCount.get());
    }

    @Test
    void usesSafeDefaults_whenJcfgNeverSet() throws Throwable {
        // executeJdbc()를 아직 안 거쳐서 jcfg가 null인 상태 — 기존 동작(재시도 없음, 타임아웃 없음)과
        // 동일해야 한다 (JdbcConfigDefaults 기본값 경로 검증).
        Connection okConn = fakeConnection(null, null);
        FakeDataSource ds = new FakeDataSource(okConn);
        JdbcExecutor executor = newExecutor(ds, uniqueJndi());
        int callsAfterConstruction = ds.getCallCount();

        Connection result = invokeAcquireConnection(executor);

        assertSame(okConn, result);
        assertEquals(callsAfterConstruction + 1, ds.getCallCount());
    }

    @Test
    void waitsRetryInterval_betweenAttempts() throws Throwable {
        Connection okConn = fakeConnection(null, null);
        FakeDataSource ds = new FakeDataSource(okConn);
        JdbcExecutor executor = newExecutor(ds, uniqueJndi());
        ds.setRemainingFailures(2);

        setJcfg(executor, jcfgWith(3, 50L, 0L)); // 2번 재시도 대기(50ms) 발생 예상

        long start = System.currentTimeMillis();
        invokeAcquireConnection(executor);
        long elapsed = System.currentTimeMillis() - start;

        // 실패가 2번이므로 재시도 대기도 최소 2번(약 100ms) 발생해야 함
        assertTrue(elapsed >= 100, "재시도 간격이 적용되지 않은 것으로 보임 (elapsed=" + elapsed + "ms)");
    }
}
