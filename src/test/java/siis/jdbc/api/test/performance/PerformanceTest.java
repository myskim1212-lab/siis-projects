package siis.jdbc.api.test.performance;

import siis.jdbc.api.test.core.ApiResponse;
import siis.jdbc.api.test.core.JdbcApiClient;
import siis.jdbc.api.test.core.TestConfig;
import siis.jdbc.api.test.data.TestDataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * JDBC API 성능 / 안정성 테스트
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │ 성능 테스트 (PT)                                                   │
 * │  PT001 - 단건 INSERT 응답시간                                       │
 * │  PT002 - 대량 INSERT 처리량 (Throughput)                            │
 * │  PT003 - 단건 SELECT 응답시간                                       │
 * │  PT004 - 대량 SELECT 처리량                                         │
 * │  PT005 - 동시 INSERT (멀티스레드)                                    │
 * │  PT006 - 동시 SELECT (멀티스레드)                                    │
 * │  PT007 - INSERT/SELECT 혼합 동시 부하                               │
 * │  PT008 - 연속 호출 응답시간 분포 (Percentile)                        │
 * ├──────────────────────────────────────────────────────────────────┤
 * │ 안정성 테스트 (ST)                                                   │
 * │  ST001 - 장시간 반복 INSERT/SELECT (Soak)                           │
 * │  ST002 - 메모리 누수 감지 (반복 대용량 CLOB/BLOB)                    │
 * │  ST003 - Connection 고갈 후 복구                                    │
 * │  ST004 - 오류 연속 발생 후 정상 복구                                  │
 * │  ST005 - 순간 대량 요청 (Spike)                                     │
 * └──────────────────────────────────────────────────────────────────┘
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(PerformanceTest.class);

    // ── 성능 기준 임계값 ──────────────────────────────────────────────
    private static final long SINGLE_INSERT_MAX_MS   = 500;   // 단건 INSERT 최대 허용 ms
    private static final long SINGLE_SELECT_MAX_MS   = 300;   // 단건 SELECT 최대 허용 ms
    private static final long BULK_1000_MAX_MS        = 5000; // 1000건 INSERT 최대 허용 ms
    private static final double P95_MAX_MS            = 800;   // 95th percentile 최대 ms
    private static final double SUCCESS_RATE_MIN      = 0.99;  // 동시 부하 최소 성공률 (99%)
    private static final int    SOAK_ITERATIONS       = 100000;   // 안정성 반복 횟수
    private static final long   SOAK_MAX_ELAPSED_MS   = 1000; // 안정성 반복당 최대 ms

    private static JdbcApiClient apiClient;
    private static String endpoint;

    // ── 공통 엔드포인트 상수 ──────────────────────────────────────────
    private static final String EP_DELETE_INSERT = "delete_insert_tb_user_v2/dbconnector";
    private static final String EP_SELECT_PARAM1 = "select_tb_user_v2_param1/dbconnector";
    private static final String EP_SELECT_ALL    = "select_tb_user_v2_no_param/dbconnector";

    @BeforeAll
    static void setUp() {
        apiClient = new JdbcApiClient(TestConfig.getBaseUrl());
        log.info("PerformanceTest setup completed - Base URL: {}", TestConfig.getBaseUrl());
    }

    @AfterAll
    static void tearDown() {
        if (apiClient != null) apiClient.close();
        log.info("PerformanceTest teardown completed");
    }

    // =================================================================
    // 성능 테스트 (PT)
    // =================================================================

    @Test
    @Order(1)
    @DisplayName("PT001 - 단건 INSERT 응답시간 검증")
    void testSingleInsertResponseTime() {
        log.info("Running PT001 - Single INSERT Response Time");

        List<Map<String, Object>> data = buildUsers(1, 9001, "PT001");
        Map<String, Object> req = buildDeleteInsertRequest(null, data);

        long start = System.currentTimeMillis();
        ApiResponse response = callApi(EP_DELETE_INSERT, req);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(1);

        log.info("PT001 - elapsed={}ms (threshold={}ms)", elapsed, SINGLE_INSERT_MAX_MS);
        assertThat(elapsed)
            .as("단건 INSERT 응답시간이 %dms를 초과했습니다: %dms", SINGLE_INSERT_MAX_MS, elapsed)
            .isLessThanOrEqualTo(SINGLE_INSERT_MAX_MS);

        log.info("PT001 - PASSED");
    }

    @Test
    @Order(2)
    @DisplayName("PT002 - 대량 INSERT 처리량 (100건)")
    void testBulkInsertThroughput() {
        log.info("Running PT002 - Bulk INSERT Throughput (200 rows)");

        List<Map<String, Object>> data = buildUsers(200, 1, "PT002");
        Map<String, Object> req = buildDeleteInsertRequest(null, data);

        long start = System.currentTimeMillis();
        ApiResponse response = callApi(EP_DELETE_INSERT, req);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getAffectedRows("insert_tb_user_v2")).isEqualTo(200);

        double tps = 1000.0 / (elapsed / 1000.0);
        log.info("PT002 - elapsed={}ms, TPS={:.1f} (threshold={}ms)", elapsed, tps, BULK_1000_MAX_MS);

        assertThat(elapsed)
            .as("1000건 INSERT가 %dms를 초과했습니다: %dms", BULK_1000_MAX_MS, elapsed)
            .isLessThanOrEqualTo(BULK_1000_MAX_MS);

        log.info("PT002 - PASSED (TPS: {:.1f})", tps);
    }

    @Test
    @Order(3)
    @DisplayName("PT003 - 단건 SELECT 응답시간 검증")
    void testSingleSelectResponseTime() {
        log.info("Running PT003 - Single SELECT Response Time");

        // 사전 데이터 보장 (PT002 이후 데이터 존재 가정)
        Map<String, Object> req = buildSelectParam1Request("ID_1");

        long start = System.currentTimeMillis();
        ApiResponse response = callApi(EP_SELECT_PARAM1, req);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.isSuccess()).isTrue();

        log.info("PT003 - elapsed={}ms (threshold={}ms)", elapsed, SINGLE_SELECT_MAX_MS);
        assertThat(elapsed)
            .as("단건 SELECT 응답시간이 %dms를 초과했습니다: %dms", SINGLE_SELECT_MAX_MS, elapsed)
            .isLessThanOrEqualTo(SINGLE_SELECT_MAX_MS);

        log.info("PT003 - PASSED");
    }

    @Test
    @Order(4)
    @DisplayName("PT004 - 전체 SELECT 처리량 (1000건 결과)")
    void testBulkSelectThroughput() {
        log.info("Running PT004 - Bulk SELECT Throughput");

        long start = System.currentTimeMillis();
        ApiResponse response = callApi(EP_SELECT_ALL, buildSelectAllRequest());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(response.isSuccess()).isTrue();
        int rowCount = response.getResultRowCount("select_tb_user_v2");

        double tps = rowCount > 0 ? rowCount / (elapsed / 1000.0) : 0;
        log.info("PT004 - rows={}, elapsed={}ms, rows/sec={:.1f}", rowCount, elapsed, tps);

        // 조회 자체가 성공해야 함
        assertThat(response.isOperationSuccess("select_tb_user_v2")).isTrue();

        log.info("PT004 - PASSED (rows: {}, elapsed: {}ms)", rowCount, elapsed);
    }

    @Test
    @Order(5)
    @DisplayName("PT005 - 동시 INSERT (10 스레드 × 10건)")
    void testConcurrentInsert() throws Exception {
        log.info("Running PT005 - Concurrent INSERT (10 threads × 10 rows)");

        int threads   = 10;
        int rowsEach  = 10;
        ConcurrentStats stats = runConcurrent(threads, threadIdx -> {
            int startId = 5000 + threadIdx * rowsEach;
            List<Map<String, Object>> data =
                buildUsers(rowsEach, startId, "PT005_T" + threadIdx);
            Map<String, Object> req = buildDeleteInsertRequest(null, data);
            ApiResponse res = callApi(EP_DELETE_INSERT, req);
            return res.isSuccess() && res.getAffectedRows("insert_tb_user_v2") == rowsEach;
        });

        log.info("PT005 - threads={}, success={}, fail={}, totalMs={}, avgMs={:.1f}",
            threads, stats.successCount, stats.failCount, stats.totalElapsedMs, stats.avgMs());

        double successRate = stats.successRate();
        assertThat(successRate)
            .as("동시 INSERT 성공률이 %.0f%% 미만입니다: %.1f%%", SUCCESS_RATE_MIN * 100, successRate * 100)
            .isGreaterThanOrEqualTo(SUCCESS_RATE_MIN);

        log.info("PT005 - PASSED (success rate: {:.1f}%)", successRate * 100);
    }

    @Test
    @Order(6)
    @DisplayName("PT006 - 동시 SELECT (20 스레드)")
    void testConcurrentSelect() throws Exception {
        log.info("Running PT006 - Concurrent SELECT (20 threads)");

        int threads = 20;
        ConcurrentStats stats = runConcurrent(threads, threadIdx -> {
            String userId = "ID_" + ((threadIdx % 100) + 1); // ID_1 ~ ID_100 순환
            Map<String, Object> req = buildSelectParam1Request(userId);
            ApiResponse res = callApi(EP_SELECT_PARAM1, req);
            return res.isSuccess();
        });

        log.info("PT006 - threads={}, success={}, fail={}, avgMs={:.1f}",
            threads, stats.successCount, stats.failCount, stats.avgMs());

        assertThat(stats.successRate())
            .as("동시 SELECT 성공률 %.1f%%", stats.successRate() * 100)
            .isGreaterThanOrEqualTo(SUCCESS_RATE_MIN);

        log.info("PT006 - PASSED (success rate: {:.1f}%)", stats.successRate() * 100);
    }

    @Test
    @Order(7)
    @DisplayName("PT007 - INSERT/SELECT 혼합 동시 부하 (15 스레드)")
    void testMixedConcurrentLoad() throws Exception {
        log.info("Running PT007 - Mixed INSERT/SELECT Concurrent Load (15 threads)");

        int threads = 15;
        ConcurrentStats stats = runConcurrent(threads, threadIdx -> {
            if (threadIdx % 3 == 0) {
                // 1/3은 INSERT
                int startId = 6000 + threadIdx * 5;
                List<Map<String, Object>> data =
                    buildUsers(5, startId, "PT007_T" + threadIdx);
                Map<String, Object> req = buildDeleteInsertRequest(null, data);
                ApiResponse res = callApi(EP_DELETE_INSERT, req);
                return res.isSuccess();
            } else {
                // 2/3은 SELECT
                String userId = "ID_" + ((threadIdx % 50) + 1);
                Map<String, Object> req = buildSelectParam1Request(userId);
                ApiResponse res = callApi(EP_SELECT_PARAM1, req);
                return res.isSuccess();
            }
        });

        log.info("PT007 - threads={}, success={}, fail={}, avgMs={:.1f}",
            threads, stats.successCount, stats.failCount, stats.avgMs());

        assertThat(stats.successRate())
            .as("혼합 부하 성공률 %.1f%%", stats.successRate() * 100)
            .isGreaterThanOrEqualTo(SUCCESS_RATE_MIN);

        log.info("PT007 - PASSED (success rate: {:.1f}%)", stats.successRate() * 100);
    }

    @Test
    @Order(8)
    @DisplayName("PT008 - 연속 50회 호출 응답시간 분포 (Percentile)")
    void testResponseTimePercentile() {
        log.info("Running PT008 - Response Time Percentile (50 iterations)");

        int iterations = 50;
        List<Long> elapsedList = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            Map<String, Object> req = buildSelectParam1Request("ID_" + ((i % 100) + 1));
            long start = System.currentTimeMillis();
            ApiResponse res = callApi(EP_SELECT_PARAM1, req);
            long elapsed = System.currentTimeMillis() - start;
            if (res.isSuccess()) elapsedList.add(elapsed);
        }

        Collections.sort(elapsedList);
        double p50 = percentile(elapsedList, 50);
        double p90 = percentile(elapsedList, 90);
        double p95 = percentile(elapsedList, 95);
        double p99 = percentile(elapsedList, 99);
        double avg = elapsedList.stream().mapToLong(Long::longValue).average().orElse(0);
        long  max  = elapsedList.stream().mapToLong(Long::longValue).max().orElse(0);

        log.info("PT008 - avg={:.1f}ms, p50={:.1f}ms, p90={:.1f}ms, p95={:.1f}ms, p99={:.1f}ms, max={}ms",
            avg, p50, p90, p95, p99, max);

        assertThat(p95)
            .as("P95 응답시간이 %.0fms를 초과했습니다: %.1fms", P95_MAX_MS, p95)
            .isLessThanOrEqualTo(P95_MAX_MS);

        log.info("PT008 - PASSED");
    }

    // =================================================================
    // 안정성 테스트 (ST)
    // =================================================================

    @Test
    @Order(10)
    @DisplayName("ST001 - 장시간 반복 INSERT/SELECT Soak 테스트 (100회)")
    void testSoakInsertSelect() {
        log.info("Running ST001 - Soak Test (INSERT/SELECT × {}회)", SOAK_ITERATIONS);

        int    failCount    = 0;
        long   totalElapsed = 0;
        long   maxElapsed   = 0;
        List<Long> elapsedList = new ArrayList<>();

        for (int i = 0; i < SOAK_ITERATIONS; i++) {
            // INSERT
            List<Map<String, Object>> data =
                buildUsers(1, 7000 + i, "ST001_" + i);
            Map<String, Object> insertReq = buildDeleteInsertRequest(null, data);

            long start = System.currentTimeMillis();
            ApiResponse insertRes = callApi(EP_DELETE_INSERT, insertReq);
            long elapsed = System.currentTimeMillis() - start;

            elapsedList.add(elapsed);
            totalElapsed += elapsed;
            maxElapsed    = Math.max(maxElapsed, elapsed);

            if (!insertRes.isSuccess()) {
                failCount++;
                log.warn("ST001 - iteration {} INSERT 실패", i);
                continue;
            }

            // SELECT
            Map<String, Object> selectReq = buildSelectParam1Request("ID_" + ((i % 100) + 1));
            ApiResponse selectRes = callApi(EP_SELECT_PARAM1, selectReq);
            if (!selectRes.isSuccess()) {
                failCount++;
                log.warn("ST001 - iteration {} SELECT 실패", i);
            }

            if (i % 20 == 0) {
                log.info("ST001 - [{}/{}] elapsed={}ms, fail={}", i + 1, SOAK_ITERATIONS, elapsed, failCount);
            }
        }

        Collections.sort(elapsedList);
        double p95 = percentile(elapsedList, 95);
        log.info("ST001 - 완료: total={}ms, max={}ms, p95={:.1f}ms, fail={}",
            totalElapsed, maxElapsed, p95, failCount);

        assertThat(failCount)
            .as("Soak 테스트 중 %d회 실패 발생", failCount)
            .isEqualTo(0);

        assertThat(maxElapsed)
            .as("Soak 최대 응답시간 %dms 초과: %dms", SOAK_MAX_ELAPSED_MS, maxElapsed)
            .isLessThanOrEqualTo(SOAK_MAX_ELAPSED_MS);

        log.info("ST001 - PASSED");
    }

    @Test
    @Order(11)
    @DisplayName("ST002 - 메모리 누수 감지 (대용량 CLOB/BLOB 반복 50회)")
    void testMemoryLeakLargeClobBlob() {
        log.info("Running ST002 - Memory Leak Detection (Large CLOB/BLOB × 50회)");

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        int failCount = 0;
        for (int i = 0; i < 50; i++) {
            List<Map<String, Object>> data =
                buildUsersWithLargeData(8000 + i, "ST002_" + i);
            Map<String, Object> req = buildDeleteInsertRequest(null, data);
            ApiResponse res = callApi(EP_DELETE_INSERT, req);
            if (!res.isSuccess()) failCount++;

            if (i % 10 == 0) {
                runtime.gc();
                long memCurrent = runtime.totalMemory() - runtime.freeMemory();
                log.info("ST002 - [{}/50] heapUsed={}MB", i + 1, memCurrent / 1024 / 1024);
            }
        }

        runtime.gc();
        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        long memDiff  = memAfter - memBefore;
        long memDiffMB = memDiff / 1024 / 1024;

        log.info("ST002 - memBefore={}MB, memAfter={}MB, diff={}MB, fail={}",
            memBefore / 1024 / 1024, memAfter / 1024 / 1024, memDiffMB, failCount);

        assertThat(failCount).as("대용량 반복 중 %d회 실패", failCount).isEqualTo(0);

        // 메모리 증가가 200MB 미만이어야 함 (누수 기준)
        assertThat(memDiffMB)
            .as("메모리 증가량이 200MB를 초과했습니다: %dMB (누수 의심)", memDiffMB)
            .isLessThan(200L);

        log.info("ST002 - PASSED (memory diff: {}MB)", memDiffMB);
    }

    @Test
    @Order(12)
    @DisplayName("ST003 - 오류 연속 발생 후 정상 복구")
    void testErrorRecovery() {
        log.info("Running ST003 - Error Recovery after Consecutive Failures");

        // 1단계: 의도적 오류 10회 연속 발생 (NULL 필드 주입)
        int errorCount = 0;
        for (int i = 0; i < 10; i++) {
            List<Map<String, Object>> data =
                buildUsersWithNulls("ST003_ERR_" + i, "ST003_ERR");
            Map<String, Object> req = buildDeleteInsertRequest(null, data);
            ApiResponse res = callApi(EP_DELETE_INSERT, req);
            if (!res.isSuccess()) errorCount++;
        }
        log.info("ST003 - 오류 발생: {}회", errorCount);
        assertThat(errorCount).as("의도적 오류가 발생해야 합니다").isGreaterThan(0);

        // 2단계: 정상 요청으로 복구 확인 (5회 연속 성공해야 함)
        int successCount = 0;
        for (int i = 0; i < 5; i++) {
            List<Map<String, Object>> data =
                buildUsers(1, 9100 + i, "ST003_OK");
            Map<String, Object> req = buildDeleteInsertRequest(null, data);
            ApiResponse res = callApi(EP_DELETE_INSERT, req);
            if (res.isSuccess()) successCount++;
            else log.warn("ST003 - 복구 단계 {}회 실패", i + 1);
        }

        log.info("ST003 - 복구 성공: {}/5회", successCount);
        assertThat(successCount)
            .as("오류 후 정상 복구에 실패했습니다 (%d/5 성공)", successCount)
            .isEqualTo(5);

        log.info("ST003 - PASSED");
    }

    @Test
    @Order(13)
    @DisplayName("ST004 - Connection 고갈 시뮬레이션 후 복구 (30 동시 요청)")
    void testConnectionExhaustionRecovery() throws Exception {
        log.info("Running ST004 - Connection Exhaustion Simulation (30 concurrent)");

        // 1단계: 30개 동시 요청으로 Connection Pool 압박
        int threads = 30;
        ConcurrentStats spikeStats = runConcurrent(threads, threadIdx -> {
            List<Map<String, Object>> data =
                buildUsers(10, 9200 + threadIdx * 10, "ST004_SPIKE");
            Map<String, Object> req = buildDeleteInsertRequest(null, data);
            ApiResponse res = callApi(EP_DELETE_INSERT, req);
            return res.isSuccess();
        });

        log.info("ST004 - Spike: success={}, fail={}, avgMs={:.1f}",
            spikeStats.successCount, spikeStats.failCount, spikeStats.avgMs());

        // 2단계: 잠시 대기 후 정상 복구 확인
        Thread.sleep(2000);

        int recoverySuccess = 0;
        for (int i = 0; i < 5; i++) {
            List<Map<String, Object>> data =
                buildUsers(1, 9500 + i, "ST004_RECOVERY");
            Map<String, Object> req = buildDeleteInsertRequest(null, data);
            ApiResponse res = callApi(EP_DELETE_INSERT, req);
            if (res.isSuccess()) recoverySuccess++;
        }

        log.info("ST004 - 복구 성공: {}/5회", recoverySuccess);
        assertThat(recoverySuccess)
            .as("Connection 고갈 후 복구 실패 (%d/5)", recoverySuccess)
            .isEqualTo(5);

        log.info("ST004 - PASSED");
    }

    @Test
    @Order(14)
    @DisplayName("ST005 - 순간 대량 요청 Spike 테스트 (50 스레드)")
    void testSpikeLoad() throws Exception {
        log.info("Running ST005 - Spike Load Test (50 threads)");

        int threads = 50;

        // 모든 스레드를 동시에 출발시키기 위한 CyclicBarrier
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        AtomicLong    totalElapsed = new AtomicLong(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            futures.add(executor.submit(() -> {
                try {
                    barrier.await(); // 모든 스레드 동시 출발
                    Map<String, Object> req = buildSelectParam1Request("ID_" + ((threadIdx % 100) + 1));
                    long start = System.currentTimeMillis();
                    ApiResponse res = callApi(EP_SELECT_PARAM1, req);
                    long elapsed = System.currentTimeMillis() - start;
                    totalElapsed.addAndGet(elapsed);
                    if (res.isSuccess()) successCount.incrementAndGet();
                    else failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.warn("ST005 - thread {} 예외: {}", threadIdx, e.getMessage());
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.SECONDS);

        double successRate = successCount.get() / (double) threads;
        double avgMs       = totalElapsed.get() / (double) threads;
        log.info("ST005 - threads={}, success={}, fail={}, successRate={:.1f}%, avgMs={:.1f}",
            threads, successCount.get(), failCount.get(), successRate * 100, avgMs);

        assertThat(successRate)
            .as("Spike 테스트 성공률 %.1f%% (최소 %.0f%% 필요)", successRate * 100, SUCCESS_RATE_MIN * 100)
            .isGreaterThanOrEqualTo(SUCCESS_RATE_MIN);

        log.info("ST005 - PASSED (success rate: {:.1f}%)", successRate * 100);
    }

    // =================================================================
    // 유틸리티
    // =================================================================

    /** 멀티스레드 동시 실행 공통 헬퍼 */
    private ConcurrentStats runConcurrent(int threads, ConcurrentTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail    = new AtomicInteger(0);
        AtomicLong    total   = new AtomicLong(0);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int idx = t;
            futures.add(executor.submit(() -> {
                long start = System.currentTimeMillis();
                try {
                    boolean ok = task.run(idx);
                    if (ok) success.incrementAndGet();
                    else    fail.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                    log.warn("ConcurrentTask thread {} 예외: {}", idx, e.getMessage());
                } finally {
                    total.addAndGet(System.currentTimeMillis() - start);
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(120, TimeUnit.SECONDS);
        return new ConcurrentStats(success.get(), fail.get(), total.get(), threads);
    }

    /** percentile 계산 (정렬된 리스트 기준) */
    private double percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    /** DELETE + INSERT 요청 바디 생성 */
    private Map<String, Object> buildDeleteInsertRequest(
            List<Map<String, Object>> deleteData,
            List<Map<String, Object>> insertData) {

        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> operations  = new HashMap<>();

        Map<String, Object> deleteOp = new HashMap<>();
        deleteOp.put("data", deleteData);
        operations.put("delete_tb_user_v2", deleteOp);

        Map<String, Object> insertOp = new HashMap<>();
        insertOp.put("data", insertData);
        operations.put("insert_tb_user_v2", insertOp);

        requestBody.put("operations", operations);
        return requestBody;
    }

    /** SELECT 단건 요청 바디 생성 */
    private Map<String, Object> buildSelectParam1Request(String userId) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> operations  = new HashMap<>();
        Map<String, Object> selectOp    = new HashMap<>();
        List<Map<String, Object>> data  = new ArrayList<>();

        Map<String, Object> row = new HashMap<>();
        row.put("USER_ID", userId);
        data.add(row);

        selectOp.put("data", data);
        operations.put("select_tb_user_v2", selectOp);
        requestBody.put("operations", operations);
        return requestBody;
    }

    /** SELECT ALL (no-param) 요청 바디 생성 */
    private Map<String, Object> buildSelectAllRequest() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> operations  = new HashMap<>();
        Map<String, Object> selectOp    = new HashMap<>();
        selectOp.put("data", new ArrayList<>());
        operations.put("select_tb_user_v2", selectOp);
        requestBody.put("operations", operations);
        return requestBody;
    }

    /** API 호출 헬퍼 (엔드포인트 설정 포함) */
    private ApiResponse callApi(String endpointKey, Map<String, Object> body) {
        TestConfig.setEndpoint(endpointKey);
        String url = TestConfig.getEndpoint(null);
        return apiClient.callApi(url, body, null);
    }

    // =================================================================
    // 데이터 생성 헬퍼
    // =================================================================

    /** count건의 사용자 데이터 리스트를 생성 (startId부터 순번) */
    private List<Map<String, Object>> buildUsers(int count, int startId, String tag) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(buildSingleUser("ID_" + (startId + i), tag));
        }
        return list;
    }

    /** 단건 사용자 데이터 생성 (대용량 CLOB 포함) */
    private List<Map<String, Object>> buildUsersWithLargeData(int id, String tag) {
        Map<String, Object> u = buildSingleUser("ID_" + id, tag);
        u.put("USER_DESC", "LARGE_DATA_START: " + "AaBbCcDdEeFf0123456789 ".repeat(150) + " END. tag=" + tag);
        return Collections.singletonList(u);
    }

    /** 단건 필수 컬럼 누락 데이터 생성 (DB 오류 유발용) */
    private List<Map<String, Object>> buildUsersWithNulls(String userId, String tag) {
        Map<String, Object> u = new HashMap<>();
        u.put("USER_ID",      userId);
        // USER_NAME 누락 → NOT NULL 제약 위반 → DB 오류 유발
        u.put("OPTIONAL_COL", tag);
        return Collections.singletonList(u);
    }

    /** 단건 사용자 Map 생성 (표준 필드 세트) */
    private Map<String, Object> buildSingleUser(String userId, String tag) {
        Map<String, Object> u = new HashMap<>();
        u.put("USER_ID",      userId);
        u.put("USER_NAME",    "Perf_" + userId);
        u.put("USER_NICK",    "NK_" + tag);
        u.put("USER_CODE",    "CODE_" + tag);
        u.put("USER_DESC",    "Performance test data. tag=" + tag);
        u.put("USER_AGE",     30);
        u.put("USER_COUNT",   100);
        u.put("USER_BIGINT",  922337203685477580L);
        u.put("USER_SCORE",   88.1234);
        u.put("USER_RATE",    0.12345678);
        u.put("USER_RATIO",   1.2345);
        u.put("USER_WEIGHT",  75.4321);
        u.put("CREATED_DATE", "2026-01-01");
        u.put("UPDATED_TS",   "2026-01-01T00:00:00.000");
        u.put("UPDATED_TZ",   "2026-01-01T00:00:00.000+09:00");
        u.put("UPDATED_LTZ",  "2026-01-01T00:00:00.000+09:00");
        u.put("IS_ACTIVE",    "Y");
        u.put("IS_DELETED",   "N");
        u.put("USER_PROFILE", "QklOQVJZXzE=");
        u.put("META_JSON",    "{\"tag\":\"" + tag + "\"}");
        u.put("TAGS",         "perf,test");
        u.put("USER_XML",     "<user><tag>" + tag + "</tag></user>");
        u.put("OPTIONAL_COL", tag);
        return u;
    }

    /** JSON 리소스 로드 헬퍼 */
    private String readJson(String fileName) {
        return TestDataLoader.loadJson(fileName);
    }

    // =================================================================
    // 내부 클래스
    // =================================================================

    @FunctionalInterface
    interface ConcurrentTask {
        boolean run(int threadIndex) throws Exception;
    }

    static class ConcurrentStats {
        final int  successCount;
        final int  failCount;
        final long totalElapsedMs;
        final int  threadCount;

        ConcurrentStats(int successCount, int failCount, long totalElapsedMs, int threadCount) {
            this.successCount   = successCount;
            this.failCount      = failCount;
            this.totalElapsedMs = totalElapsedMs;
            this.threadCount    = threadCount;
        }

        double successRate() {
            return threadCount == 0 ? 0 : successCount / (double) threadCount;
        }

        double avgMs() {
            return threadCount == 0 ? 0 : totalElapsedMs / (double) threadCount;
        }
    }
}