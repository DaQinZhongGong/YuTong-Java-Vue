package com.yutong.infra.datasource.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.infra.datasource.domain.SysDatasource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源管理器: 连接池生命周期 + env 占位符解析 + 热加载原子切换。
 * <p>
 * GA2-46 v1.5 设计来源: 46-多数据源与数据集设计 line 72-93。
 * <p>
 * 核心能力:
 * <ul>
 *   <li>env: 前缀占位符解析: env:SPRING_DATASOURCE_URL → Environment.getProperty("spring.datasource.url")</li>
 *   <li>primary 数据源复用 Spring 主 DataSource (避免重复连接池)</li>
 *   <li>其他数据源独立创建 HikariDataSource (46 号文档 line 69 不与主库共用池)</li>
 *   <li>热加载原子切换: 新池验证 → 原子替换 → 旧池延迟 30s 关闭 (46 号文档 line 74-83)</li>
 *   <li>健康状态维护: UP/DOWN/UNKNOWN, 连接测试时更新</li>
 *   <li>熔断器: 连接池耗尽 60s 熔断 (46 号文档 line 90)</li>
 * </ul>
 */
@Component
public class DataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);

    /** primary 数据源编码 (复用 Spring 主 DataSource) */
    public static final String PRIMARY_CODE = "primary";

    /** 熔断器冷却时间 (毫秒) */
    private static final long BREAKER_COOLDOWN_MS = 60_000L;

    /** 旧连接池延迟关闭时间 (毫秒) */
    private static final long OLD_POOL_DRAIN_MS = 30_000L;

    private final DataSource primaryDataSource;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    /** 数据源编码 → DataSource (primary 不在此 Map 中, 直接使用 primaryDataSource) */
    private final Map<String, HikariDataSource> datasourcePool = new ConcurrentHashMap<>();

    /** 数据源编码 → 健康状态 (UP/DOWN/UNKNOWN) */
    private final Map<String, String> healthStatus = new ConcurrentHashMap<>();

    /** 数据源编码 → 熔断器打开时间戳 (0=未熔断) */
    private final Map<String, Long> breakerOpenTime = new ConcurrentHashMap<>();

    public DataSourceManager(DataSource primaryDataSource,
                             Environment environment,
                             ObjectMapper objectMapper) {
        this.primaryDataSource = primaryDataSource;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.healthStatus.put(PRIMARY_CODE, SysDatasource.HEALTH_UP);
    }

    /**
     * 获取指定数据源编码对应的 DataSource。
     * primary 直接返回 Spring 主 DataSource, 其他从 datasourcePool 获取。
     *
     * @param datasourceCode 数据源编码
     * @return DataSource
     * @throws BusinessException 数据源不存在 (DS-404001) / 熔断中 (DS-503001)
     */
    public DataSource getDataSource(String datasourceCode) {
        if (datasourceCode == null || datasourceCode.isBlank() || PRIMARY_CODE.equals(datasourceCode)) {
            return primaryDataSource;
        }
        // 熔断器检查
        Long openTime = breakerOpenTime.get(datasourceCode);
        if (openTime != null) {
            long elapsed = System.currentTimeMillis() - openTime;
            if (elapsed < BREAKER_COOLDOWN_MS) {
                log.warn("数据源 [{}] 熔断中, 剩余 {}ms", datasourceCode, BREAKER_COOLDOWN_MS - elapsed);
                throw new BusinessException(ErrorCode.DS_UNAVAILABLE,
                        "数据源 [" + datasourceCode + "] 熔断中, 请稍后重试");
            }
            // 熔断器冷却完成, 清除状态
            breakerOpenTime.remove(datasourceCode);
        }
        HikariDataSource ds = datasourcePool.get(datasourceCode);
        if (ds == null) {
            throw new BusinessException(ErrorCode.DS_NOT_FOUND,
                    "数据源不存在或未加载: " + datasourceCode);
        }
        if (ds.isClosed()) {
            throw new BusinessException(ErrorCode.DS_UNAVAILABLE,
                    "数据源 [" + datasourceCode + "] 已关闭");
        }
        return ds;
    }

    /**
     * 注册 (创建) 一个数据源的连接池。
     * env: 前缀占位符会被解析为真实值。
     *
     * @param ds 元数据
     */
    public synchronized void register(SysDatasource ds) {
        if (PRIMARY_CODE.equals(ds.getDatasourceCode())) {
            // primary 复用 Spring 主 DataSource, 不创建独立连接池
            healthStatus.put(PRIMARY_CODE, SysDatasource.HEALTH_UP);
            log.info("数据源 [primary] 复用 Spring 主 DataSource, 跳过独立连接池创建");
            return;
        }
        if (!Boolean.TRUE.equals(ds.getEnabled())) {
            log.info("数据源 [{}] 未启用, 跳过连接池创建", ds.getDatasourceCode());
            return;
        }
        HikariConfig config = buildHikariConfig(ds);
        HikariDataSource newPool = new HikariDataSource(config);
        HikariDataSource oldPool = datasourcePool.put(ds.getDatasourceCode(), newPool);
        healthStatus.put(ds.getDatasourceCode(), SysDatasource.HEALTH_UP);
        log.info("数据源 [{}] 连接池已创建 (poolSize={}, minIdle={}, readOnly={})",
                ds.getDatasourceCode(), config.getMaximumPoolSize(), config.getMinimumIdle(), config.isReadOnly());
        if (oldPool != null) {
            // 热加载原子切换: 旧池延迟 30s 关闭 (46 号文档 line 83)
            schedulePoolClose(oldPool, ds.getDatasourceCode());
        }
    }

    /**
     * 卸载指定数据源的连接池 (用于禁用或删除)。
     */
    public synchronized void unregister(String datasourceCode) {
        HikariDataSource pool = datasourcePool.remove(datasourceCode);
        healthStatus.remove(datasourceCode);
        breakerOpenTime.remove(datasourceCode);
        if (pool != null) {
            schedulePoolClose(pool, datasourceCode);
            log.info("数据源 [{}] 连接池已卸载, 延迟 30s 关闭", datasourceCode);
        }
    }

    /**
     * 更新健康状态 (连接测试时调用)。
     */
    public void updateHealth(String datasourceCode, String status, String errorMessage) {
        healthStatus.put(datasourceCode, status);
        if (SysDatasource.HEALTH_DOWN.equals(status)) {
            breakerOpenTime.put(datasourceCode, System.currentTimeMillis());
            log.warn("数据源 [{}] 健康状态 DOWN, 熔断器打开 60s: {}", datasourceCode, errorMessage);
        } else if (SysDatasource.HEALTH_UP.equals(status)) {
            breakerOpenTime.remove(datasourceCode);
        }
    }

    /**
     * 获取健康状态。
     */
    public String getHealthStatus(String datasourceCode) {
        return healthStatus.getOrDefault(datasourceCode, SysDatasource.HEALTH_UNKNOWN);
    }

    /**
     * 解析 env: 前缀占位符。
     * env:SPRING_DATASOURCE_URL → Environment.getProperty("spring.datasource.url")
     * 其他字符串原样返回。
     */
    private String resolveEnvRef(String ref) {
        if (ref == null || ref.isBlank()) return null;
        if (ref.startsWith("env:")) {
            String key = ref.substring(4);
            String value = environment.getProperty(key);
            if (value == null) {
                log.warn("env: 占位符 [{}] 未找到对应环境变量, 返回 null", key);
            }
            return value;
        }
        if (ref.startsWith("ENV:")) {
            String key = ref.substring(4);
            String value = environment.getProperty(key);
            if (value == null) {
                log.warn("ENV: 占位符 [{}] 未找到对应环境变量, 返回 null", key);
            }
            return value;
        }
        return ref;
    }

    /**
     * 根据 SysDatasource 元数据构建 HikariConfig。
     */
    private HikariConfig buildHikariConfig(SysDatasource ds) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("yutong-ds-" + ds.getDatasourceCode());
        // 解析 env: 占位符
        String jdbcUrl = resolveEnvRef(ds.getJdbcUrl());
        String username = resolveEnvRef(ds.getUsernameRef());
        String password = resolveEnvRef(ds.getPasswordRef());
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new BusinessException(ErrorCode.DS_PARAMETER_INVALID,
                    "数据源 [" + ds.getDatasourceCode() + "] jdbc_url 解析为空");
        }
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(Boolean.TRUE.equals(ds.getReadOnly()));
        // 解析 pool_config jsonb
        applyPoolConfig(config, ds.getPoolConfig());
        // 默认值 (如果 pool_config 未指定)
        if (config.getMaximumPoolSize() <= 0) config.setMaximumPoolSize(10);
        if (config.getMinimumIdle() < 0) config.setMinimumIdle(2);
        if (config.getConnectionTimeout() <= 0) config.setConnectionTimeout(30_000);
        if (config.getIdleTimeout() <= 0) config.setIdleTimeout(600_000);
        if (config.getMaxLifetime() <= 0) config.setMaxLifetime(1_800_000);
        // GA2-46 安全约束: statement timeout 通过连接初始化 SQL 设置
        config.setConnectionInitSql("SET statement_timeout = 30000"); // 30s statement timeout
        return config;
    }

    /**
     * 应用 pool_config jsonb 配置到 HikariConfig。
     */
    private void applyPoolConfig(HikariConfig config, String poolConfigJson) {
        if (poolConfigJson == null || poolConfigJson.isBlank()) return;
        try {
            Map<String, Object> props = objectMapper.readValue(poolConfigJson, Map.class);
            if (props.containsKey("maximumPoolSize")) {
                config.setMaximumPoolSize(((Number) props.get("maximumPoolSize")).intValue());
            }
            if (props.containsKey("minimumIdle")) {
                config.setMinimumIdle(((Number) props.get("minimumIdle")).intValue());
            }
            if (props.containsKey("connectionTimeout")) {
                config.setConnectionTimeout(((Number) props.get("connectionTimeout")).longValue());
            }
            if (props.containsKey("idleTimeout")) {
                config.setIdleTimeout(((Number) props.get("idleTimeout")).longValue());
            }
            if (props.containsKey("maxLifetime")) {
                config.setMaxLifetime(((Number) props.get("maxLifetime")).longValue());
            }
        } catch (Exception e) {
            log.warn("数据源 pool_config 解析失败, 使用默认值: {}", e.getMessage());
        }
    }

    /**
     * 延迟 30s 关闭旧连接池 (46 号文档 line 83)。
     */
    private void schedulePoolClose(HikariDataSource pool, String code) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(OLD_POOL_DRAIN_MS);
                pool.close();
                log.info("数据源 [{}] 旧连接池已关闭", code);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("数据源 [{}] 旧连接池关闭失败: {}", code, e.getMessage());
            }
        }, "pool-drain-" + code);
        t.setDaemon(true);
        t.start();
    }

    /**
     * 获取所有已注册数据源的健康状态快照。
     */
    public Map<String, String> getHealthSnapshot() {
        return new HashMap<>(healthStatus);
    }
}
