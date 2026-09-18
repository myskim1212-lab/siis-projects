package sec.siis.jvm;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import sec.siis.connection.DataSourceManager;

/**
 * 이 MI 인스턴스 자신의 JVM/데이터소스 풀 상태를 수집하는 순수 로직 — Synapse에 전혀
 * 의존하지 않아 단위 테스트가 쉽다 (mediate() 쪽 배선은 JvmInfoMediator가 담당).
 *
 * 원격 JMX 포트를 열 필요 없이 이 코드 자체가 대상 MI JVM 안에서 실행되므로,
 * java.lang.management의 로컬 MXBean만으로 필요한 지표를 전부 얻는다.
 */
public class JvmInfoExecutor {

    // 힙/파일디스크립터/커넥션 풀 사용률에 대한 종합 헬스 판정 임계값(%).
    private static final double WARN_PCT = 75.0;
    private static final double CRITICAL_PCT = 90.0;

    // "장시간 실행 스레드" 판정용 — JVM 표준 API(ThreadMXBean)는 스레드별 실제 생성
    // 시각을 제공하지 않으므로, 이 클래스가 최초로 관측한 시각을 스레드 ID별로 기억해
    // 두었다가 "관측 시작 이후 경과 시간"으로 근사한다. MI 인스턴스별로 별도 JVM(별도
    // 클래스로더)에서 이 코드가 실행되므로 static 상태가 인스턴스 간에 섞이지 않는다.
    // 스레드가 종료되면 맵에서 제거해 ID가 재사용돼도 나이가 잘못 누적되지 않게 한다.
    private static final ConcurrentHashMap<Long, Long> THREAD_FIRST_SEEN_MS = new ConcurrentHashMap<>();
    private static final int LONG_RUNNING_THREAD_LIMIT = 10;

    public Map<String, Object> collect() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        ThreadMXBean th = ManagementFactory.getThreadMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

        Map<String, Object> heap = memoryUsageToMap(mem.getHeapMemoryUsage());
        Map<String, Object> nonHeap = memoryUsageToMap(mem.getNonHeapMemoryUsage());
        Map<String, Object> fileDescriptors = collectFileDescriptors(os);
        List<Map<String, Object>> memoryPools = collectMemoryPools();
        List<Map<String, Object>> datasourcePools = collectDatasourcePools();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("timestamp", Instant.now().toString());
        result.put("uptime_ms", rt.getUptime());
        result.put("start_time", Instant.ofEpochMilli(rt.getStartTime()).toString());
        result.put("heap", heap);
        result.put("non_heap", nonHeap);
        result.put("threads", collectThreads(th));
        result.put("long_running_threads", collectLongRunningThreads(th));
        result.put("gc", collectGc());
        result.put("os", collectOs(os));
        result.put("file_descriptors", fileDescriptors);
        result.put("memory_pools", memoryPools);
        result.put("datasource_pools", datasourcePools);
        result.put("health", computeHealth(heap, fileDescriptors, memoryPools, datasourcePools));
        return result;
    }

    private Map<String, Object> collectThreads(ThreadMXBean th) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("current", th.getThreadCount());
        m.put("peak", th.getPeakThreadCount());
        m.put("daemon", th.getDaemonThreadCount());
        m.put("total_started", th.getTotalStartedThreadCount());
        return m;
    }

    /**
     * 관측된 시각 기준으로 가장 오래 살아있는 스레드 상위 N개를 돌려준다(응답 크기를
     * 위해 제한). 정렬/집계 로직 자체는 {@link #collectAllObservedThreads}에 있고,
     * 여기서는 응답에 실을 개수만 자른다.
     */
    private List<Map<String, Object>> collectLongRunningThreads(ThreadMXBean th) {
        List<Map<String, Object>> all = collectAllObservedThreads(th);
        if (all.size() > LONG_RUNNING_THREAD_LIMIT) {
            return new ArrayList<>(all.subList(0, LONG_RUNNING_THREAD_LIMIT));
        }
        return all;
    }

    /**
     * "실행 시간"이 아니라 "이 수집기가 그 스레드를 처음 본 이후 경과 시간"으로 나이를
     * 근사한다 — JVM 표준 API(ThreadMXBean)는 스레드별 실제 생성 시각을 제공하지 않기
     * 때문이다. MI 프로세스를 재시작하거나 모니터링을 이제 막 시작한 직후에는 모든
     * 스레드가 나이 0에서 다시 쌓이기 시작하며, 주기적으로 계속 조회할수록 정확해진다.
     * 개수 제한 없이 관측 경과 시간 내림차순으로 정렬된 전체 목록을 돌려준다(테스트에서
     * 특정 스레드가 상위 N에서 잘려나가는 것에 흔들리지 않도록 패키지 접근으로 노출).
     */
    List<Map<String, Object>> collectAllObservedThreads(ThreadMXBean th) {
        long now = System.currentTimeMillis();
        long[] ids = th.getAllThreadIds();

        Set<Long> liveIds = new HashSet<>();
        for (long id : ids) {
            liveIds.add(id);
            THREAD_FIRST_SEEN_MS.putIfAbsent(id, now);
        }
        THREAD_FIRST_SEEN_MS.keySet().retainAll(liveIds);

        boolean cpuTimeSupported = th.isThreadCpuTimeSupported() && th.isThreadCpuTimeEnabled();
        ThreadInfo[] infos = th.getThreadInfo(ids);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ThreadInfo info : infos) {
            if (info == null) continue; // 조회 사이에 이미 종료된 스레드
            long id = info.getThreadId();
            Long firstSeen = THREAD_FIRST_SEEN_MS.get(id);
            if (firstSeen == null) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", info.getThreadName());
            m.put("state", info.getThreadState().toString());
            m.put("observed_age_ms", now - firstSeen);
            m.put("cpu_time_ms", cpuTimeSupported ? th.getThreadCpuTime(id) / 1_000_000 : null);
            m.put("blocked_count", info.getBlockedCount());
            m.put("waited_count", info.getWaitedCount());
            list.add(m);
        }

        list.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("observed_age_ms")).reversed());
        return list;
    }

    private List<Map<String, Object>> collectGc() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", gc.getName());
            m.put("collection_count", gc.getCollectionCount());
            m.put("collection_time_ms", gc.getCollectionTime());
            list.add(m);
        }
        return list;
    }

    private Map<String, Object> collectOs(OperatingSystemMXBean os) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available_processors", os.getAvailableProcessors());
        m.put("system_load_average", os.getSystemLoadAverage()); // 미지원 OS면 -1.0
        // com.sun.management 확장 인터페이스는 HotSpot 전용이라 안전하게 캐스팅 시도만 한다.
        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOs = (com.sun.management.OperatingSystemMXBean) os;
            m.put("process_cpu_load", sunOs.getProcessCpuLoad());
            m.put("free_physical_memory", sunOs.getFreePhysicalMemorySize());
            m.put("total_physical_memory", sunOs.getTotalPhysicalMemorySize());
        }
        return m;
    }

    /** 유닉스 계열 JVM에서만 지원되는 값이라, 아니면(예: Windows) open/max가 비어있다. */
    private Map<String, Object> collectFileDescriptors(OperatingSystemMXBean os) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (os instanceof com.sun.management.UnixOperatingSystemMXBean) {
            com.sun.management.UnixOperatingSystemMXBean unixOs = (com.sun.management.UnixOperatingSystemMXBean) os;
            long open = unixOs.getOpenFileDescriptorCount();
            long max = unixOs.getMaxFileDescriptorCount();
            m.put("open", open);
            m.put("max", max);
            if (max > 0) {
                m.put("usage_pct", roundPct(open * 100.0 / max));
            }
        }
        return m;
    }

    private List<Map<String, Object>> collectMemoryPools() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", pool.getName());
            m.put("type", pool.getType().toString()); // HEAP | NON_HEAP
            m.put("usage", memoryUsageToMap(pool.getUsage()));
            MemoryUsage peak = pool.getPeakUsage();
            if (peak != null) {
                m.put("peak_usage", memoryUsageToMap(peak));
            }
            list.add(m);
        }
        return list;
    }

    /**
     * DataSourceManager 캐시에 올라와 있는(=한 번이라도 조회된) 데이터소스마다 커넥션 풀
     * 상태를 수집한다. 풀 구현체(Tomcat JDBC Pool, HikariCP 등)를 컴파일 시점에 알 수
     * 없으므로 — MI 배포판마다 번들 버전이 다를 수 있어 직접 의존하면 깨지기 쉽다 —
     * 리플렉션으로 알려진 구현체의 getter만 안전하게 시도한다.
     */
    private List<Map<String, Object>> collectDatasourcePools() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, DataSource> entry : DataSourceManager.snapshot().entrySet()) {
            list.add(collectOneDatasourcePool(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    private Map<String, Object> collectOneDatasourcePool(String jndiName, DataSource ds) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jndi_name", jndiName);
        m.put("pool_class", ds.getClass().getName());

        // Tomcat JDBC Connection Pool — WSO2 MI가 기본으로 사용하는 구현체.
        if (hasMethod(ds, "getActive") && hasMethod(ds, "getMaxActive")) {
            m.put("pool_type", "tomcat-jdbc");
            m.put("active", invokeInt(ds, "getActive"));
            m.put("idle", invokeInt(ds, "getIdle"));
            m.put("size", invokeInt(ds, "getSize"));
            m.put("max_active", invokeInt(ds, "getMaxActive"));
            m.put("wait_count", invokeInt(ds, "getWaitCount"));
            return m;
        }

        // HikariCP — 커스텀 데이터소스 설정 등으로 쓰였을 경우 대비.
        Object hikariMXBean = invokeObject(ds, "getHikariPoolMXBean");
        if (hikariMXBean != null) {
            m.put("pool_type", "hikaricp");
            m.put("active", invokeInt(hikariMXBean, "getActiveConnections"));
            m.put("idle", invokeInt(hikariMXBean, "getIdleConnections"));
            m.put("size", invokeInt(hikariMXBean, "getTotalConnections"));
            m.put("wait_count", invokeInt(hikariMXBean, "getThreadsAwaitingConnection"));
            return m;
        }

        m.put("pool_type", "unknown");
        m.put("note", "지원하지 않는 커넥션 풀 구현체라 통계를 제공할 수 없습니다.");
        return m;
    }

    /**
     * heap / 파일디스크립터 / (Old Gen류) 메모리 풀 / 데이터소스 풀 사용률을 임계값과
     * 비교해 OK / WARNING / CRITICAL 하나로 요약한다. 커넥션 대기(wait_count > 0)는
     * 사용률과 무관하게 그 자체로 CRITICAL — 이미 커넥션을 못 받아 대기 중인 요청이
     * 있다는 뜻이기 때문이다.
     */
    private Map<String, Object> computeHealth(Map<String, Object> heap, Map<String, Object> fileDescriptors,
            List<Map<String, Object>> memoryPools, List<Map<String, Object>> datasourcePools) {
        List<String> reasons = new ArrayList<>();
        String[] status = { "OK" };

        checkPct(usagePct(heap), "힙 메모리", status, reasons);
        checkPct((Double) fileDescriptors.get("usage_pct"), "파일 디스크립터", status, reasons);

        for (Map<String, Object> pool : memoryPools) {
            String name = String.valueOf(pool.get("name"));
            if (isOldGenPool(name)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> usage = (Map<String, Object>) pool.get("usage");
                checkPct(usagePct(usage), name, status, reasons);
            }
        }

        for (Map<String, Object> ds : datasourcePools) {
            Object active = ds.get("active");
            Object maxActive = ds.get("max_active");
            String jndiName = String.valueOf(ds.get("jndi_name"));
            if (active instanceof Integer && maxActive instanceof Integer) {
                int a = (Integer) active;
                int max = (Integer) maxActive;
                if (max > 0) {
                    checkPct(a * 100.0 / max, jndiName + " 커넥션 풀", status, reasons);
                }
            }
            Object waitCount = ds.get("wait_count");
            if (waitCount instanceof Integer && (Integer) waitCount > 0) {
                int w = (Integer) waitCount;
                status[0] = "CRITICAL";
                reasons.add(jndiName + " 커넥션 대기 발생 (" + w + "건)");
            }
        }

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", status[0]);
        health.put("reasons", reasons);
        return health;
    }

    private boolean isOldGenPool(String name) {
        String n = name.toLowerCase();
        return n.contains("old") || n.contains("tenured");
    }

    private void checkPct(Double pct, String label, String[] status, List<String> reasons) {
        if (pct == null) return;
        if (pct >= CRITICAL_PCT) {
            status[0] = "CRITICAL";
            reasons.add(label + " 사용률 " + roundPct(pct) + "%");
        } else if (pct >= WARN_PCT && !"CRITICAL".equals(status[0])) {
            status[0] = "WARNING";
            reasons.add(label + " 사용률 " + roundPct(pct) + "%");
        }
    }

    private Double usagePct(Map<String, Object> usage) {
        if (usage == null) return null;
        Object usedObj = usage.get("used");
        Object maxObj = usage.get("max");
        if (!(usedObj instanceof Long) || !(maxObj instanceof Long)) return null;
        long used = (Long) usedObj;
        long max = (Long) maxObj;
        if (max <= 0) return null; // max == -1 : 이 풀은 상한이 정의되어 있지 않음
        return roundPct(used * 100.0 / max);
    }

    private double roundPct(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private Map<String, Object> memoryUsageToMap(MemoryUsage u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("init", u.getInit());
        m.put("used", u.getUsed());
        m.put("committed", u.getCommitted());
        m.put("max", u.getMax());
        return m;
    }

    // ── 리플렉션 헬퍼 — 풀 구현체를 컴파일 시점에 몰라도 되도록 ──────────────

    private boolean hasMethod(Object target, String method) {
        try {
            target.getClass().getMethod(method);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private Integer invokeInt(Object target, String method) {
        if (target == null) return null;
        try {
            Object v = target.getClass().getMethod(method).invoke(target);
            return v == null ? null : ((Number) v).intValue();
        } catch (Exception e) {
            return null;
        }
    }

    private Object invokeObject(Object target, String method) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }
}
