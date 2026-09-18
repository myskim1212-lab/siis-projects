package sec.siis.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import sec.siis.connection.DataSourceManager;

class JvmInfoExecutorTest {

    @AfterEach
    void clearDataSourceCache() throws Exception {
        getCacheField().clear();
        getThreadFirstSeenField().clear();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, DataSource> getCacheField() throws Exception {
        Field f = DataSourceManager.class.getDeclaredField("cache");
        f.setAccessible(true);
        return (ConcurrentHashMap<String, DataSource>) f.get(null);
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Long, Long> getThreadFirstSeenField() throws Exception {
        Field f = JvmInfoExecutor.class.getDeclaredField("THREAD_FIRST_SEEN_MS");
        f.setAccessible(true);
        return (ConcurrentHashMap<Long, Long>) f.get(null);
    }

    @Test
    void collect_returnsFullShapeWithoutAnyDatasource() {
        Map<String, Object> result = new JvmInfoExecutor().collect();

        assertEquals(true, result.get("success"));
        assertNotNull(result.get("timestamp"));
        assertTrue((Long) result.get("uptime_ms") >= 0);

        Map<String, Object> heap = asMap(result.get("heap"));
        assertTrue((Long) heap.get("used") >= 0);
        assertNotNull(heap.get("max"));

        Map<String, Object> threads = asMap(result.get("threads"));
        assertTrue((Integer) threads.get("current") > 0);

        assertTrue(result.get("gc") instanceof List);
        assertTrue(result.get("memory_pools") instanceof List);
        assertFalse(((List<?>) result.get("memory_pools")).isEmpty());

        assertTrue(result.get("datasource_pools") instanceof List);
        assertTrue(((List<?>) result.get("datasource_pools")).isEmpty());

        Map<String, Object> health = asMap(result.get("health"));
        assertTrue(Arrays.asList("OK", "WARNING", "CRITICAL").contains(health.get("status")));
        assertTrue(health.get("reasons") instanceof List);

        assertTrue(result.get("long_running_threads") instanceof List);
        assertFalse(((List<?>) result.get("long_running_threads")).isEmpty());
    }

    @Test
    void collectLongRunningThreads_ageGrowsAcrossRepeatedCallsForTheSameThread() throws InterruptedException {
        JvmInfoExecutor executor = new JvmInfoExecutor();
        java.lang.management.ThreadMXBean th = java.lang.management.ManagementFactory.getThreadMXBean();
        long myThreadId = Thread.currentThread().getId();

        List<Map<String, Object>> first = executor.collectAllObservedThreads(th);
        Map<String, Object> firstEntry = findThreadEntry(first, myThreadId);
        assertNotNull(firstEntry, "현재 테스트 스레드가 결과에 포함되어야 한다");
        assertEquals(0L, firstEntry.get("observed_age_ms"), "처음 관측하는 순간의 나이는 0이어야 한다");

        Thread.sleep(20);

        List<Map<String, Object>> second = executor.collectAllObservedThreads(th);
        Map<String, Object> secondEntry = findThreadEntry(second, myThreadId);
        assertNotNull(secondEntry);
        assertTrue((Long) secondEntry.get("observed_age_ms") >= 20,
                "재조회 시 같은 스레드의 관측 경과 시간이 늘어나 있어야 한다");
    }

    @Test
    void collectLongRunningThreads_sortedByObservedAgeDescending() {
        Map<String, Object> result = new JvmInfoExecutor().collect();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> threads = (List<Map<String, Object>>) result.get("long_running_threads");

        assertTrue(threads.size() <= 10, "응답에 포함되는 개수는 상한선 이내여야 한다");
        for (int i = 1; i < threads.size(); i++) {
            long prevAge = (Long) threads.get(i - 1).get("observed_age_ms");
            long curAge = (Long) threads.get(i).get("observed_age_ms");
            assertTrue(prevAge >= curAge, "관측 경과 시간 내림차순으로 정렬되어야 한다");
        }
    }

    private Map<String, Object> findThreadEntry(List<Map<String, Object>> threads, long threadId) {
        return threads.stream().filter(t -> ((Long) t.get("id")) == threadId).findFirst().orElse(null);
    }

    @Test
    void collect_detectsTomcatJdbcStylePoolViaReflection() throws Exception {
        getCacheField().put("jdbc/testDS", new FakeTomcatPoolDataSource(8, 2, 10, 10, 0));

        Map<String, Object> result = new JvmInfoExecutor().collect();
        List<?> pools = (List<?>) result.get("datasource_pools");
        assertEquals(1, pools.size());

        Map<String, Object> pool = asMap(pools.get(0));
        assertEquals("jdbc/testDS", pool.get("jndi_name"));
        assertEquals("tomcat-jdbc", pool.get("pool_type"));
        assertEquals(8, pool.get("active"));
        assertEquals(2, pool.get("idle"));
        assertEquals(10, pool.get("max_active"));
        assertEquals(0, pool.get("wait_count"));
    }

    @Test
    void collect_flagsCriticalWhenConnectionPoolIsExhaustedOrWaiting() throws Exception {
        getCacheField().put("jdbc/busyDS", new FakeTomcatPoolDataSource(10, 0, 10, 10, 3));

        Map<String, Object> result = new JvmInfoExecutor().collect();
        Map<String, Object> health = asMap(result.get("health"));
        assertEquals("CRITICAL", health.get("status"));

        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) health.get("reasons");
        assertTrue(reasons.stream().anyMatch(r -> r.contains("busyDS")));
    }

    @Test
    void collect_reportsUnknownPoolTypeGracefully() throws Exception {
        getCacheField().put("jdbc/plainDS", new DataSource() {
            public java.sql.Connection getConnection() { throw new UnsupportedOperationException(); }
            public java.sql.Connection getConnection(String u, String p) { throw new UnsupportedOperationException(); }
            public java.io.PrintWriter getLogWriter() { throw new UnsupportedOperationException(); }
            public void setLogWriter(java.io.PrintWriter out) { }
            public void setLoginTimeout(int seconds) { }
            public int getLoginTimeout() { return 0; }
            public java.util.logging.Logger getParentLogger() { throw new UnsupportedOperationException(); }
            public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
            public boolean isWrapperFor(Class<?> iface) { return false; }
        });

        Map<String, Object> result = new JvmInfoExecutor().collect();
        List<?> pools = (List<?>) result.get("datasource_pools");
        assertEquals(1, pools.size());
        assertEquals("unknown", asMap(pools.get(0)).get("pool_type"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    /** Tomcat JDBC Connection Pool의 통계 getter만 흉내 낸 가짜 DataSource — 실제
     * tomcat-jdbc 라이브러리 의존 없이 JvmInfoExecutor의 리플렉션 감지 로직만 검증한다. */
    static class FakeTomcatPoolDataSource implements DataSource {
        private final int active, idle, size, maxActive, waitCount;

        FakeTomcatPoolDataSource(int active, int idle, int size, int maxActive, int waitCount) {
            this.active = active;
            this.idle = idle;
            this.size = size;
            this.maxActive = maxActive;
            this.waitCount = waitCount;
        }

        public int getActive() { return active; }
        public int getIdle() { return idle; }
        public int getSize() { return size; }
        public int getMaxActive() { return maxActive; }
        public int getWaitCount() { return waitCount; }

        public java.sql.Connection getConnection() { throw new UnsupportedOperationException(); }
        public java.sql.Connection getConnection(String u, String p) { throw new UnsupportedOperationException(); }
        public java.io.PrintWriter getLogWriter() { throw new UnsupportedOperationException(); }
        public void setLogWriter(java.io.PrintWriter out) { }
        public void setLoginTimeout(int seconds) { }
        public int getLoginTimeout() { return 0; }
        public java.util.logging.Logger getParentLogger() { throw new UnsupportedOperationException(); }
        public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
