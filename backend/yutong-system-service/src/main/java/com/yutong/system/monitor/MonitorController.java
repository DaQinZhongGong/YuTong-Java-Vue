package com.yutong.system.monitor;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import java.util.Collection;
import java.util.Comparator;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * 平台监控：服务健康 + 缓存概览。设计来源: 91-Web基础后台逐页交互详设「服务健康页/缓存概览页」。
 *
 * GA2-L177: 在原极简实现基础上扩展为组件级健康检查与缓存白名单清理。
 * 关键约束（91 号文档 line 75-81）:
 *  - 健康卡片展示 App/PostgreSQL/Redis/MinIO/Flyway/RabbitMQ/Nacos/AI Provider 状态、耗时、检查时间、错误摘要、traceId
 *  - boot profile 下 RabbitMQ/Nacos 显示 SKIPPED；cloud profile 必须 UP，否则整体 DEGRADED/DOWN
 *  - 缓存清理接口为 DELETE /cache/{cacheName}，禁止任意 key 删除，仅允许白名单
 *  - 二次确认由前端 ElMessageBox 实现；后端按白名单强制校验
 */
@Tag(name = "监控")
@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    /** 平台版本号 — 与设计文档基线对齐，GA2-L177 阶段同步至 v1.0 GA2。 */
    private static final String PLATFORM_VERSION = "1.0.0-GA2";

    /** 组件状态枚举值。 */
    private static final String STATUS_UP = "UP";
    private static final String STATUS_DOWN = "DOWN";
    private static final String STATUS_SKIPPED = "SKIPPED";

    /** 整体状态枚举值（91 号文档：cloud profile 下 SKIPPED 不得作为异常）。 */
    private static final String OVERALL_UP = "UP";
    private static final String OVERALL_DEGRADED = "DEGRADED";
    private static final String OVERALL_DOWN = "DOWN";

    /** cloud profile 标识：在该 profile 下 RabbitMQ/Nacos 必须 UP，否则降级。 */
    private static final String PROFILE_CLOUD = "cloud";

    /** 缓存清理白名单（91 号文档 line 81：禁止任意 key 删除入口）。
     *  - config: SysConfig 缓存，可安全清理
     *  - dict: 字典缓存，可安全清理
     *  - report-dataset: 报表数据集缓存，可安全清理
     *  - idem 幂等锁：不在白名单（清理可能造成重复处理）
     *  - realtime: 为 PubSub 通道，无 key 需清理
     */
    private static final Set<String> CACHE_CLEAR_WHITELIST = Set.of("config", "dict", "report-dataset");

    /** 缓存名称 → Redis key 前缀映射（用于概览与清理）。 */
    private static final Map<String, String> CACHE_PREFIX_MAP = new LinkedHashMap<>();

    static {
        CACHE_PREFIX_MAP.put("config", "yutong:config:");
        CACHE_PREFIX_MAP.put("dict", "yutong:dict:");
        CACHE_PREFIX_MAP.put("idem", "yutong:idem:");
        CACHE_PREFIX_MAP.put("report-dataset", "yutong:report:dataset:");
    }

    private final StringRedisTemplate redis;
    private final DataSource dataSource;
    private final MinioClient minioClient;
    private final String minioBucket;

    /** Flyway 可选注入（关闭迁移时可能不存在）。 */
    @Autowired(required = false)
    private Flyway flyway;

    /** Micrometer MeterRegistry（由 yutong-boot actuator 运行时提供）。 */
    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /** AI Provider 查询用 JdbcTemplate（避免跨模块依赖 yutong-ai-service）。 */
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    public MonitorController(
            StringRedisTemplate redis,
            DataSource dataSource,
            MinioClient minioClient,
            @Qualifier("minioBucketName") String minioBucket) {
        this.redis = redis;
        this.dataSource = dataSource;
        this.minioClient = minioClient;
        this.minioBucket = minioBucket;
    }

    @PostConstruct
    void init() {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        log.info("[Monitor] 初始化完成: profile={}, minioBucket={}, flyway={}",
                activeProfile, minioBucket, flyway != null ? "present" : "absent");
    }

    /**
     * 服务健康检查：组件级状态聚合。
     * 设计来源: 91 号文档 line 75-77。权限码 monitor:health:view。
     */
    @Operation(summary = "服务健康检查（组件级）", operationId = "getPlatformHealth")
    @RequiresPermission("monitor:health:view")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        String traceId = TraceContext.getTraceId();
        OffsetDateTime checkedAt = OffsetDateTime.now();
        boolean isCloud = PROFILE_CLOUD.equalsIgnoreCase(activeProfile);

        List<Map<String, Object>> components = new ArrayList<>();
        boolean anyDown = false;
        boolean anyDegraded = false;

        // 1. App
        Map<String, Object> app = checkComponent("App", traceId, () -> {
            if (flyway != null && flyway.info() != null) {
                return "migrations=" + flyway.info().applied().length;
            }
            return "no-flyway";
        });
        components.add(app);

        // 2. PostgreSQL
        Map<String, Object> pg = checkComponent("PostgreSQL", traceId, () -> {
            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement("SELECT 1");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return "pg-version=" + conn.getMetaData().getDatabaseProductVersion();
                }
                return "pg-empty-result";
            }
        });
        components.add(pg);

        // 3. Redis
        Map<String, Object> redisComp = checkComponent("Redis", traceId, () -> {
            String pong = redis.execute((RedisCallback<String>) conn -> conn.ping());
            return "ping=" + pong;
        });
        components.add(redisComp);

        // 4. MinIO
        Map<String, Object> minio = checkComponent("MinIO", traceId, () -> {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioBucket).build());
            return "bucket=" + minioBucket + ",exists=" + exists;
        });
        components.add(minio);

        // 5. Flyway（迁移状态）
        Map<String, Object> flywayComp = checkComponent("Flyway", traceId, () -> {
            if (flyway == null) {
                throw new IllegalStateException("Flyway bean not configured");
            }
            int applied = flyway.info().applied().length;
            int pending = flyway.info().pending() != null ? flyway.info().pending().length : 0;
            return "applied=" + applied + ",pending=" + pending;
        });
        components.add(flywayComp);

        // 6. RabbitMQ — boot profile 下 SKIPPED；cloud profile 必须 UP
        Map<String, Object> rabbit = checkOptionalComponent("RabbitMQ", traceId, isCloud, () -> {
            // cloud profile 下应注入 RabbitAdmin / AmqpTemplate；当前 boot 模块未引入依赖
            // 留作 cloud 切换时扩展点（设计文档 03-总体架构 line 45-50）
            throw new IllegalStateException("RabbitMQ probe not implemented in current profile: " + activeProfile);
        });
        components.add(rabbit);

        // 7. Nacos — boot profile 下 SKIPPED；cloud profile 必须 UP
        Map<String, Object> nacos = checkOptionalComponent("Nacos", traceId, isCloud, () -> {
            throw new IllegalStateException("Nacos probe not implemented in current profile: " + activeProfile);
        });
        components.add(nacos);

        // 8. AI Provider — 查询 ai_provider 表 enabled 计数
        Map<String, Object> aiProvider = checkComponent("AI Provider", traceId, () -> {
            Integer enabledCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ai_provider WHERE enabled = true AND deleted = false",
                    Integer.class);
            return "enabled-providers=" + (enabledCount == null ? 0 : enabledCount);
        });
        components.add(aiProvider);

        // 聚合整体状态
        for (Map<String, Object> c : components) {
            String s = String.valueOf(c.get("status"));
            if (STATUS_DOWN.equals(s)) {
                anyDown = true;
            } else if (OVERALL_DEGRADED.equals(s)) {
                anyDegraded = true;
            }
        }
        String overall = anyDown ? OVERALL_DOWN : (anyDegraded ? OVERALL_DEGRADED : OVERALL_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", overall);
        result.put("platform", "YuTong");
        result.put("version", PLATFORM_VERSION);
        result.put("profile", activeProfile);
        result.put("runMode", isCloud ? "cloud" : "boot");
        result.put("checkedAt", checkedAt.toString());
        result.put("components", components);
        return Result.ok(result, traceId);
    }

    /**
     * 缓存概览：返回缓存名称/key 数估算/命中率/过期策略/最后刷新时间。
     * 设计来源: 91 号文档 line 79-80。权限码 monitor:cache:view。
     *
     * 命中率说明：Redis INFO stats 的 keyspace_hits/keyspace_misses 是全局聚合，无法按缓存名拆分；
     * 因此命中率字段仅返回全局值，单缓存仅返回 keyCount + ttl 策略 + 最后刷新时间。
     */
    @Operation(summary = "缓存概览", operationId = "getCacheOverview")
    @RequiresPermission("monitor:cache:view")
    @GetMapping("/cache")
    public Result<Map<String, Object>> getCacheOverview() {
        String traceId = TraceContext.getTraceId();
        OffsetDateTime checkedAt = OffsetDateTime.now();

        // 全局 Redis 命中率（来自 INFO stats）
        double globalHitRate = redis.execute((RedisCallback<Double>) conn -> {
            Properties info = conn.info("stats");
            String hits = info.getProperty("keyspace_hits", "0");
            String misses = info.getProperty("keyspace_misses", "0");
            long h = Long.parseLong(hits.trim());
            long m = Long.parseLong(misses.trim());
            long total = h + m;
            return total == 0 ? 0.0 : (double) h / total;
        });

        // Redis run_id（实例标识）
        String runId = redis.execute((RedisCallback<String>) conn -> {
            Properties server = conn.info("server");
            return server.getProperty("run_id", "unknown");
        });

        List<Map<String, Object>> caches = new ArrayList<>();
        for (Map.Entry<String, String> e : CACHE_PREFIX_MAP.entrySet()) {
            String name = e.getKey();
            String prefix = e.getValue();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("cacheName", name);
            c.put("keyPrefix", prefix);
            c.put("clearable", CACHE_CLEAR_WHITELIST.contains(name));

            // SCAN 估算 key 数（限制 10000 次避免阻塞）
            Long keyCount = redis.execute((RedisCallback<Long>) conn -> {
                long count = 0;
                Cursor<byte[]> cursor = conn.scan(
                        ScanOptions.scanOptions().match(prefix + "*").count(500).build());
                while (cursor.hasNext() && count < 10000) {
                    cursor.next();
                    count++;
                }
                return count;
            });
            c.put("keyCount", keyCount);

            // 采样第一个 key 的 TTL 推断过期策略
            String ttlPolicy = redis.execute((RedisCallback<String>) conn -> {
                Cursor<byte[]> cursor = conn.scan(
                        ScanOptions.scanOptions().match(prefix + "*").count(1).build());
                if (cursor.hasNext()) {
                    byte[] key = cursor.next();
                    Long ttl = conn.keyCommands().ttl(key);
                    if (ttl == null || ttl < 0) {
                        return "no-expiry";
                    }
                    return ttl + "s";
                }
                return "empty";
            });
            c.put("ttlPolicy", ttlPolicy);

            c.put("lastRefreshTime", null); // Redis 不记录 SET 时间，无法回溯
            caches.add(c);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", STATUS_UP);
        result.put("provider", "REDIS");
        result.put("endpoint", runId);
        result.put("globalHitRate", globalHitRate);
        result.put("checkedAt", checkedAt.toString());
        result.put("caches", caches);
        return Result.ok(result, traceId);
    }

    /**
     * 清理指定缓存（白名单强校验）。
     * 设计来源: 91 号文档 line 81。权限码 monitor:cache:clear。
     * 接口: DELETE /cache/{cacheName}
     * 约束: 二次确认由前端 ElMessageBox 实现；后端按 CACHE_CLEAR_WHITELIST 强制校验。
     */
    @Operation(summary = "清理指定缓存（白名单）", operationId = "clearCache")
    @RequiresPermission("monitor:cache:clear")
    @Auditable(operationType = "CLEAR", module = "system", bizType = "monitor",
            bizIdExpr = "#cacheName", content = "清理缓存")
    @DeleteMapping("/cache/{cacheName}")
    public Result<Map<String, Object>> clearCache(@PathVariable String cacheName) {
        String traceId = TraceContext.getTraceId();
        if (cacheName == null || !CACHE_CLEAR_WHITELIST.contains(cacheName)) {
            log.warn("[Monitor] 拒绝清理非白名单缓存: cacheName={}, traceId={}", cacheName, traceId);
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "缓存 [" + cacheName + "] 不在白名单内，禁止清理。允许: " + new TreeSet<>(CACHE_CLEAR_WHITELIST));
        }
        String prefix = CACHE_PREFIX_MAP.get(cacheName);
        Set<String> keys = new HashSet<>();
        redis.execute((RedisCallback<Void>) conn -> {
            Cursor<byte[]> cursor = conn.scan(
                    ScanOptions.scanOptions().match(prefix + "*").count(1000).build());
            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
            return null;
        });
        long deleted = 0;
        if (!keys.isEmpty()) {
            deleted = redis.delete(keys);
        }
        log.info("[Monitor] 清理缓存: cacheName={}, prefix={}, deletedKeys={}, traceId={}",
                cacheName, prefix, deleted, traceId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cacheName", cacheName);
        data.put("deletedKeys", deleted);
        data.put("clearedAt", OffsetDateTime.now().toString());
        return Result.ok(data, traceId);
    }

    /**
     * 聚合 Metrics 指标：JVM 内存 / 线程 / 连接池 / HTTP P95 P99。
     * 设计来源: 62-可观测性指标日志链路详设。权限码 monitor:metrics:view。
     */
    @Operation(summary = "聚合 Metrics 指标", operationId = "getMetrics")
    @RequiresPermission("monitor:metrics:view")
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        String traceId = TraceContext.getTraceId();
        Map<String, Object> data = new LinkedHashMap<>();

        // JVM 内存 (heap)
        double jvmHeapUsed = getGaugeValue("jvm.memory.used", "area", "heap");
        double jvmHeapMax = getGaugeValue("jvm.memory.max", "area", "heap");
        double jvmHeapCommitted = getGaugeValue("jvm.memory.committed", "area", "heap");
        data.put("jvmHeapUsed", jvmHeapUsed);
        data.put("jvmHeapMax", jvmHeapMax);
        data.put("jvmHeapCommitted", jvmHeapCommitted);

        // JVM 线程
        double jvmThreadLive = getGaugeValue("jvm.threads.live");
        double jvmThreadPeak = getGaugeValue("jvm.threads.peak");
        data.put("jvmThreadLive", (int) jvmThreadLive);
        data.put("jvmThreadPeak", (int) jvmThreadPeak);

        // 数据库连接池 (HikariCP)
        int dbPoolActive = 0;
        int dbPoolIdle = 0;
        int dbPoolMax = 0;
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean mxBean = hikari.getHikariPoolMXBean();
            if (mxBean != null) {
                dbPoolActive = mxBean.getActiveConnections();
                dbPoolIdle = mxBean.getIdleConnections();
            }
            dbPoolMax = hikari.getMaximumPoolSize();
        }
        data.put("dbPoolActive", dbPoolActive);
        data.put("dbPoolIdle", dbPoolIdle);
        data.put("dbPoolMax", dbPoolMax);

        // HTTP 响应时间 P95 / P99 (毫秒)
        double httpP95 = 0.0;
        double httpP99 = 0.0;
        if (meterRegistry != null) {
            Collection<Timer> httpTimers = meterRegistry.find("http.server.requests").timers();
            Timer representative = httpTimers.stream()
                    .max(Comparator.comparingDouble(Timer::count))
                    .orElse(null);
            if (representative != null) {
                HistogramSnapshot snapshot = representative.takeSnapshot();
                for (ValueAtPercentile vp : snapshot.percentileValues()) {
                    if (Math.abs(vp.percentile() - 0.95) < 0.001) {
                        httpP95 = Math.round(vp.value() * 1000.0 * 100.0) / 100.0;
                    }
                    if (Math.abs(vp.percentile() - 0.99) < 0.001) {
                        httpP99 = Math.round(vp.value() * 1000.0 * 100.0) / 100.0;
                    }
                }
            }
        }
        data.put("httpP95", httpP95);
        data.put("httpP99", httpP99);

        return Result.ok(data, traceId);
    }

    /** 从 MeterRegistry 读取指定 Gauge 值；找不到时返回 0。 */
    private double getGaugeValue(String name, String... tags) {
        if (meterRegistry == null) {
            return 0.0;
        }
        var finder = meterRegistry.find(name);
        if (finder == null) {
            return 0.0;
        }
        Gauge gauge = finder.tags(tags).gauge();
        return gauge != null ? gauge.value() : 0.0;
    }

    // ===== 内部辅助方法 =====

    /** 必需组件检查：抛异常 → DOWN，正常 → UP。 */
    private Map<String, Object> checkComponent(String name, String traceId, ComponentProbe probe) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("checkedAt", OffsetDateTime.now().toString());
        c.put("traceId", traceId);
        long start = System.nanoTime();
        try {
            String detail = probe.probe();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            c.put("status", STATUS_UP);
            c.put("durationMs", durationMs);
            c.put("detail", detail);
            c.put("errorSummary", null);
        } catch (Exception ex) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            c.put("status", STATUS_DOWN);
            c.put("durationMs", durationMs);
            c.put("detail", null);
            c.put("errorSummary", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            log.warn("[Monitor] 组件 [{}] 检查失败 ({}ms): {}", name, durationMs, ex.getMessage());
        }
        return c;
    }

    /** 可选组件检查：boot profile → SKIPPED；cloud profile → 必需。 */
    private Map<String, Object> checkOptionalComponent(String name, String traceId, boolean requiredInCloud,
                                                       ComponentProbe probe) {
        if (!requiredInCloud) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", name);
            c.put("status", STATUS_SKIPPED);
            c.put("durationMs", 0);
            c.put("checkedAt", OffsetDateTime.now().toString());
            c.put("detail", "boot profile 下未启用");
            c.put("errorSummary", null);
            c.put("traceId", traceId);
            return c;
        }
        return checkComponent(name, traceId, probe);
    }

    @FunctionalInterface
    private interface ComponentProbe {
        String probe() throws Exception;
    }
}
