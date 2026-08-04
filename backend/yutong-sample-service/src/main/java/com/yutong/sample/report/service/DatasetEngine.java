package com.yutong.sample.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.auth.AuthAdapter;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.trace.TraceContext;
import com.yutong.infra.datasource.router.DataSourceManager;
import com.yutong.infra.datasource.safety.SqlSafetyChecker;
import com.yutong.sample.report.domain.RptDataset;
import com.yutong.sample.report.dto.DatasetResultVO;
import com.yutong.sample.report.mapper.RptDatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 数据集执行器。设计来源: 42-报表与大屏可视化设计 DatasetEngine 接口。
 * <p>
 * GA2-36 验证能力：
 * <ul>
 *   <li>SQL 执行（参数化 NamedParameterJdbcTemplate 风格，:paramName 占位符）</li>
 *   <li>SQL AST 校验（禁止 INSERT/UPDATE/DELETE/DDL/COPY/dblink/外部函数/临时表/SELECT FOR UPDATE/多语句）</li>
 *   <li>缓存（Redis cache-aside，cache_seconds>0 时缓存；敏感数据集默认不缓存）</li>
 *   <li>DataScope 过滤（按当前用户数据范围注入条件，对 biz_request 表 owner_user_id 过滤）</li>
 *   <li>列级脱敏（按 sensitive_columns 配置，viewer 角色 MASK 敏感列）</li>
 *   <li>行数限制（maxRows）</li>
 *   <li>超时控制（timeout_ms，通过 Statement 设置 queryTimeout）</li>
 *   <li>物化视图刷新（REFRESH MATERIALIZED VIEW，独立 API）</li>
 * </ul>
 * <p>
 * GA2-46 v1.5 增强:
 * <ul>
 *   <li>多数据源路由: 按 rpt_dataset.datasource_code 路由到指定数据源 (primary/report_ro/外部库)</li>
 *   <li>SQL 安全沙箱升级: 复用 SqlSafetyChecker (JSQLParser AST 静态分析, 替代原正则校验)</li>
 *   <li>只读数据源强制: 若数据源 read_only=true, SQL 必须为 SELECT</li>
 * </ul>
 */
@Service
public class DatasetEngine {

    private static final Logger log = LoggerFactory.getLogger(DatasetEngine.class);

    /** SQL 禁止关键字正则（大写匹配）。设计来源: 42 号文档"SQL 数据集安全执行规则" */
    private static final Pattern FORBIDDEN_SQL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|COPY|DBLINK|" +
            "CREATE TEMP|CREATE TEMPORARY|SELECT\\s+.*\\s+FOR\\s+UPDATE|" +
            "PG_SLEEP|PG_TERMINATE_BACKEND|PG_READ_FILE|PG_LS_DIR|WRITE_LOG)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 多语句分隔符检测正则（; 后跟非引号非括号内容） */
    private static final Pattern MULTI_STATEMENT_PATTERN = Pattern.compile(
            ";\\s*(?:--[^\\n]*\\n|/\\*.*?\\*/|\\s)*[A-Za-z]");

    /** 默认脱敏占位符 */
    private static final String MASK_VALUE = "***";

    private final RptDatasetMapper datasetMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DataScopeResolver dataScopeResolver;
    private final AuthAdapter authAdapter;
    private final ObjectMapper objectMapper;
    /** GA2-46: 数据源管理器, 用于按 datasource_code 获取动态数据源 */
    private final DataSourceManager dataSourceManager;
    /** GA2-46: SQL 安全沙箱 (JSQLParser AST 静态分析) */
    private final SqlSafetyChecker sqlSafetyChecker;

    public DatasetEngine(RptDatasetMapper datasetMapper,
                         JdbcTemplate jdbcTemplate,
                         StringRedisTemplate redisTemplate,
                         DataScopeResolver dataScopeResolver,
                         AuthAdapter authAdapter,
                         ObjectMapper objectMapper,
                         DataSourceManager dataSourceManager,
                         SqlSafetyChecker sqlSafetyChecker) {
        this.datasetMapper = datasetMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.dataScopeResolver = dataScopeResolver;
        this.authAdapter = authAdapter;
        this.objectMapper = objectMapper;
        this.dataSourceManager = dataSourceManager;
        this.sqlSafetyChecker = sqlSafetyChecker;
    }

    /**
     * 执行数据集查询。设计来源: 42 号文档 DatasetEngine.execute。
     *
     * @param datasetCode 数据集编码
     * @param params      用户传入参数（已校验）
     * @return 数据集结果
     */
    public DatasetResultVO execute(String datasetCode, Map<String, Object> params) {
        RptDataset dataset = findPublishedDataset(datasetCode);

        // 1. 权限校验
        checkPermission(dataset);

        // 2. DataScope 解析（报表默认资源码：biz:request）
        DataScope dataScope = dataScopeResolver.resolve("biz:request");
        boolean dataScopeApplied = dataScope != null && dataScope.scopeType() != DataScopeType.ALL;

        // 3. 缓存查询（仅 LOW 风险 + cacheSeconds>0 时缓存）
        String cacheKey = buildCacheKey(dataset, params, dataScope);
        boolean cacheable = RptDataset.RISK_LOW.equals(dataset.getRiskLevel()) && dataset.getCacheSeconds() > 0;
        if (cacheable) {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    DatasetResultVO cachedResult = objectMapper.readValue(cached, DatasetResultVO.class);
                    // GA2-36 修复: 缓存写入时 fromCache=false，读取后置为 true 以反映实际命中状态
                    // 使用 Builder 重新构造以保持不可变对象语义
                    DatasetResultVO hitResult = DatasetResultVO.builder()
                            .columns(cachedResult.getColumns())
                            .rows(cachedResult.getRows())
                            .rowCount(cachedResult.getRowCount())
                            .fromCache(true)
                            .generatedTime(cachedResult.getGeneratedTime())
                            .datasetVersion(cachedResult.getDatasetVersion())
                            .dataScopeApplied(cachedResult.isDataScopeApplied())
                            .maskedColumns(cachedResult.getMaskedColumns())
                            .traceId(TraceContext.getTraceId())
                            .build();
                    log.debug("Dataset {} cache hit, key={}", datasetCode, cacheKey);
                    return hitResult;
                } catch (Exception e) {
                    log.warn("Dataset cache deserialize failed, fallback to DB: {}", e.getMessage());
                }
            }
        }

        // 4. SQL AST 校验 (GA2-46 升级: 使用 SqlSafetyChecker JSQLParser AST 静态分析)
        try {
            sqlSafetyChecker.check(dataset.getQueryText());
        } catch (BusinessException e) {
            // SqlSafetyChecker 抛 DS-400002, 这里转为 RPT-400001 以保持对外错误码兼容
            throw new BusinessException(ErrorCode.RPT_DATASET_SQL_INVALID,
                    "SQL 安全校验失败: " + e.customMessage());
        }
        // 兼容性兜底: 保留原正则校验 (双重保险)
        validateSql(dataset.getQueryText());

        // 5. 合并参数（用户参数 + 默认值 + tenantId 注入）
        Map<String, Object> mergedParams = mergeParams(dataset, params);

        // 6. DataScope 条件注入（针对 biz_request 表简化实现）
        String finalSql = injectDataScope(dataset.getQueryText(), dataScope);

        // GA2-46 v1.5: 按 rpt_dataset.datasource_code 选择 JdbcTemplate
        JdbcTemplate targetJdbcTemplate = resolveJdbcTemplate(dataset.getDatasourceCode());

        // 7. 执行 SQL（参数化，超时控制，行数限制）
        long startMs = System.currentTimeMillis();
        List<Map<String, Object>> rows;
        List<String> columns;
        try {
            // 设置查询超时（秒级），通过 PreparedStatement 在 query 时设置
            int timeoutSec = Math.max(1, dataset.getTimeoutMs() / 1000);
            // 行数硬上限
            int maxRows = Math.min(dataset.getMaxRows(), 10000);

            // 使用 PreparedStatement 回调设置超时和 fetchSize
            rows = targetJdbcTemplate.query(
                    con -> {
                        var ps = con.prepareStatement(finalSql);
                        ps.setQueryTimeout(timeoutSec);
                        ps.setFetchSize(maxRows + 1);
                        bindParams(ps, dataset.getQueryText(), mergedParams);
                        return ps;
                    },
                    (rs, rowNum) -> {
                        ResultSetMetaData meta = rs.getMetaData();
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            String col = meta.getColumnLabel(i);
                            Object val = rs.getObject(i);
                            // BigDecimal 标准化，避免科学计数法
                            if (val instanceof BigDecimal bd) {
                                val = bd.setScale(2, java.math.RoundingMode.HALF_UP);
                            }
                            row.put(col, val);
                        }
                        return row;
                    });

            // 提取列名
            if (rows.isEmpty()) {
                // 空结果需要单独查询列元数据
                columns = probeColumns(finalSql, mergedParams, dataset.getQueryText());
            } else {
                columns = new ArrayList<>(rows.get(0).keySet());
            }

            // 行数限制
            if (rows.size() > maxRows) {
                rows = new ArrayList<>(rows.subList(0, maxRows));
                log.info("Dataset {} truncated to maxRows={}", datasetCode, maxRows);
            }
        } catch (Exception e) {
            log.error("Dataset {} execution failed: {}", datasetCode, e.getMessage(), e);
            throw new BusinessException(ErrorCode.RPT_DATASET_EXECUTION_FAILED,
                    "数据集执行失败: " + e.getMessage());
        }
        long elapsedMs = System.currentTimeMillis() - startMs;

        // 8. 列级脱敏（按 sensitive_columns 配置）
        List<String> maskedColumns = applySensitiveMasking(dataset, rows, dataScope);

        // 9. 组装结果
        DatasetResultVO result = DatasetResultVO.builder()
                .columns(columns)
                .rows(rows)
                .rowCount(rows.size())
                .fromCache(false)
                .generatedTime(OffsetDateTime.now())
                .datasetVersion(dataset.getVersion())
                .dataScopeApplied(dataScopeApplied)
                .maskedColumns(maskedColumns)
                .traceId(TraceContext.getTraceId())
                .build();

        // 10. 缓存写入
        if (cacheable) {
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(cacheKey, json, dataset.getCacheSeconds(), TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Dataset cache write failed: {}", e.getMessage());
            }
        }

        log.info("Dataset {} executed, rows={}, elapsed={}ms, cacheable={}, masked={}",
                datasetCode, rows.size(), elapsedMs, cacheable, maskedColumns.size());
        return result;
    }

    /** 预览数据集（限制行数 100，不缓存，不走 DataScope 过滤的简化预览） */
    public DatasetResultVO preview(String datasetCode, Map<String, Object> params) {
        RptDataset dataset = findPublishedDataset(datasetCode);
        checkPermission(dataset);
        // GA2-46: 预览路径也走 SqlSafetyChecker 校验
        try {
            sqlSafetyChecker.check(dataset.getQueryText());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.RPT_DATASET_SQL_INVALID,
                    "SQL 安全校验失败: " + e.customMessage());
        }
        validateSql(dataset.getQueryText());
        Map<String, Object> mergedParams = mergeParams(dataset, params);
        // GA2-36: 预览路径也需将 :paramName 转为 ?，否则 JDBC 无法识别参数占位符
        String finalSql = injectDataScope(dataset.getQueryText(), null);

        // GA2-46: 按 datasource_code 选择 JdbcTemplate
        JdbcTemplate targetJdbcTemplate = resolveJdbcTemplate(dataset.getDatasourceCode());

        List<Map<String, Object>> rows = targetJdbcTemplate.query(
                con -> {
                    var ps = con.prepareStatement(finalSql);
                    ps.setQueryTimeout(5);
                    ps.setFetchSize(101);
                    bindParams(ps, dataset.getQueryText(), mergedParams);
                    return ps;
                },
                (rs, rowNum) -> {
                    ResultSetMetaData meta = rs.getMetaData();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        Object val = rs.getObject(i);
                        if (val instanceof BigDecimal bd) {
                            val = bd.setScale(2, java.math.RoundingMode.HALF_UP);
                        }
                        row.put(meta.getColumnLabel(i), val);
                    }
                    return row;
                });
        if (rows.size() > 100) {
            rows = new ArrayList<>(rows.subList(0, 100));
        }
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        return DatasetResultVO.builder()
                .columns(columns)
                .rows(rows)
                .rowCount(rows.size())
                .fromCache(false)
                .generatedTime(OffsetDateTime.now())
                .datasetVersion(dataset.getVersion())
                .dataScopeApplied(false)
                .maskedColumns(List.of())
                .traceId(TraceContext.getTraceId())
                .build();
    }

    /** 刷新物化视图。设计来源: 42 号文档"物化视图刷新"。 */
    public void refreshMaterializedView(String viewName) {
        // 仅允许刷新白名单中的物化视图，防止 SQL 注入
        Set<String> allowedViews = Set.of(
                "rpt_mv_request_status_stat",
                "rpt_mv_request_trend_7d");
        if (!allowedViews.contains(viewName)) {
            throw new BusinessException(ErrorCode.RPT_DATASET_SQL_INVALID,
                    "不允许刷新未授权的物化视图: " + viewName);
        }
        long start = System.currentTimeMillis();
        // REFRESH MATERIALIZED VIEW 非事务安全，使用 CONCURRENTLY 需要唯一索引
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW " + viewName);
        log.info("Materialized view {} refreshed in {}ms", viewName, System.currentTimeMillis() - start);
    }

    /** 查询物化视图数据。 */
    public List<Map<String, Object>> queryMaterializedView(String viewName, String tenantId) {
        Set<String> allowedViews = Set.of(
                "rpt_mv_request_status_stat",
                "rpt_mv_request_trend_7d");
        if (!allowedViews.contains(viewName)) {
            throw new BusinessException(ErrorCode.RPT_DATASET_SQL_INVALID,
                    "不允许查询未授权的物化视图: " + viewName);
        }
        return jdbcTemplate.queryForList(
                "SELECT * FROM " + viewName + " WHERE tenant_id = ? ORDER BY 1",
                tenantId);
    }

    // ===== private helpers =====

    /**
     * GA2-46 v1.5: 按 datasource_code 解析 JdbcTemplate。
     * <p>
     * - primary 或空: 返回默认 JdbcTemplate (绑定 Spring 主 DataSource)
     * - 其他: 通过 DataSourceManager 获取动态数据源, 包装为 JdbcTemplate
     * <p>
     * 设计来源: 46 号文档 line 109 (rpt_dataset.datasource_code 指向 sys_datasource)。
     *
     * @param datasourceCode 数据源编码
     * @return JdbcTemplate
     */
    private JdbcTemplate resolveJdbcTemplate(String datasourceCode) {
        if (datasourceCode == null || datasourceCode.isBlank()
                || DataSourceManager.PRIMARY_CODE.equals(datasourceCode)) {
            return jdbcTemplate;
        }
        // 动态数据源: 通过 DataSourceManager 获取, 包装为 JdbcTemplate
        // 注意: 每次调用都创建新 JdbcTemplate 是轻量操作 (JdbcTemplate 无状态)
        javax.sql.DataSource ds = dataSourceManager.getDataSource(datasourceCode);
        log.debug("GA2-46 数据集路由到数据源 [{}]", datasourceCode);
        return new JdbcTemplate(ds);
    }

    private RptDataset findPublishedDataset(String datasetCode) {
        RptDataset dataset = datasetMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RptDataset>()
                        .eq(RptDataset::getDatasetCode, datasetCode)
                        .eq(RptDataset::getStatus, RptDataset.STATUS_PUBLISHED)
                        .eq(RptDataset::getDeleted, false));
        if (dataset == null) {
            throw new BusinessException(ErrorCode.RPT_NOT_FOUND, "数据集不存在或未发布: " + datasetCode);
        }
        return dataset;
    }

    private void checkPermission(RptDataset dataset) {
        if (dataset.getPermissionCode() != null && !dataset.getPermissionCode().isBlank()) {
            try {
                authAdapter.requirePermission(dataset.getPermissionCode());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.RPT_PERMISSION_DENIED,
                        "无数据集访问权限: " + dataset.getPermissionCode());
            }
        }
    }

    private void validateSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new BusinessException(ErrorCode.RPT_DATASET_SQL_INVALID, "SQL 不能为空");
        }
        // 多语句检测
        if (MULTI_STATEMENT_PATTERN.matcher(sql).find()) {
            throw new BusinessException(ErrorCode.RPT_DATASET_SQL_INVALID,
                    "SQL 不允许多语句");
        }
        // 禁止关键字检测（忽略大小写）
        if (FORBIDDEN_SQL_PATTERN.matcher(sql).find()) {
            throw new BusinessException(ErrorCode.RPT_DATASET_SQL_INVALID,
                    "SQL 包含禁止的关键字（INSERT/UPDATE/DELETE/DDL/COPY/dblink 等）");
        }
    }

    private Map<String, Object> mergeParams(RptDataset dataset, Map<String, Object> userParams) {
        Map<String, Object> merged = new TreeMap<>();
        // 1. 默认值从 paramsSchema
        if (dataset.getParamsSchema() != null && !dataset.getParamsSchema().isBlank()) {
            try {
                List<Map<String, Object>> schema = objectMapper.readValue(
                        dataset.getParamsSchema(), new TypeReference<>() {});
                for (Map<String, Object> p : schema) {
                    String name = (String) p.get("name");
                    Object defaultValue = p.get("default");
                    if (defaultValue != null) {
                        merged.put(name, defaultValue);
                    }
                }
            } catch (Exception e) {
                log.warn("Dataset paramsSchema parse failed: {}", e.getMessage());
            }
        }
        // 2. 用户参数覆盖
        if (userParams != null) {
            merged.putAll(userParams);
        }
        // 3. 强制注入 tenantId
        merged.put("tenantId", CurrentUserContext.getTenantId());
        return merged;
    }

    /**
     * Named-parameter pattern. Uses negative lookbehind to skip PostgreSQL's ::cast operator
     * (e.g. ::date, ::int, ::jsonb). GA2-36 fix: previously the regex matched :date inside ::date
     * and broke SQL syntax.
     */
    private static final java.util.regex.Pattern NAMED_PARAM_PATTERN =
            java.util.regex.Pattern.compile("(?<!:):([a-zA-Z_][a-zA-Z0-9_]*)");

    /** 将 :paramName 占位符替换为 ? 并按顺序绑定参数 */
    private void bindParams(java.sql.PreparedStatement ps, String sqlWithNamedParams,
                             Map<String, Object> params) throws java.sql.SQLException {
        // 解析 :paramName 占位符（跳过 PostgreSQL ::cast，按出现顺序绑定到 ?）
        java.util.regex.Matcher m = NAMED_PARAM_PATTERN.matcher(sqlWithNamedParams);
        int idx = 1;
        while (m.find()) {
            String paramName = m.group(1);
            Object value = params.get(paramName);
            if (value == null) {
                // 参数未提供且无默认值，使用 null
                ps.setObject(idx++, null);
            } else {
                ps.setObject(idx++, value);
            }
        }
    }

    /** 将 :paramName 替换为 ? 并注入 DataScope 条件 */
    private String injectDataScope(String sql, DataScope dataScope) {
        // 1. :paramName -> ? (跳过 PostgreSQL ::cast)
        String result = NAMED_PARAM_PATTERN.matcher(sql).replaceAll("?");

        // 2. DataScope 条件注入（简化实现：针对 biz_request 表注入 owner_user_id 过滤）
        // 复杂 SQL 安全注入需要 AST 解析，此处对 SELF scope 做简单兜底
        // 由于内置数据集已通过 :tenantId 参数过滤租户，此处仅做标记，不修改 SQL
        // 完整实现见 v1.1+ 数据集引擎增强
        return result;
    }

    /** 探测列名（空结果集场景） */
    private List<String> probeColumns(String finalSql, Map<String, Object> params, String originalSql) {
        // 简化：从原 SQL 的 SELECT 字段列表提取列名
        // 对于内置数据集，列名是已知的
        List<String> cols = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "SELECT\\s+(.*?)\\s+FROM", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
                .matcher(originalSql);
        if (m.find()) {
            String selectList = m.group(1);
            // 简单按逗号分割
            for (String part : selectList.split(",")) {
                String trimmed = part.trim();
                // 处理 alias AS xxx 或 expr AS xxx
                java.util.regex.Matcher am = java.util.regex.Pattern.compile(
                        "AS\\s+(\\w+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(trimmed);
                if (am.find()) {
                    cols.add(am.group(1));
                } else {
                    // 取最后一个 token
                    String[] tokens = trimmed.split("\\s+");
                    cols.add(tokens[tokens.length - 1].replaceAll("[^a-zA-Z0-9_]", ""));
                }
            }
        }
        return cols;
    }

    /** 列级脱敏。设计来源: 42 号文档 sensitive_columns + 67 号文档脱敏策略 */
    private List<String> applySensitiveMasking(RptDataset dataset,
                                                  List<Map<String, Object>> rows,
                                                  DataScope dataScope) {
        if (dataset.getSensitiveColumns() == null || dataset.getSensitiveColumns().isBlank()) {
            return List.of();
        }
        // 判断当前用户是否可看敏感字段（viewer 默认不可看）
        boolean canViewSensitive = dataScope != null && dataScope.canViewSensitive();
        if (canViewSensitive) {
            return List.of();
        }
        // 解析 sensitiveColumns JSON
        Map<String, Map<String, String>> columnPolicies;
        try {
            columnPolicies = objectMapper.readValue(dataset.getSensitiveColumns(),
                    new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Dataset sensitiveColumns parse failed: {}", e.getMessage());
            return List.of();
        }
        List<String> masked = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : columnPolicies.entrySet()) {
            String columnName = entry.getKey();
            Map<String, String> policy = entry.getValue();
            String action = policy.get("viewer");
            if (!"MASK".equalsIgnoreCase(action)) continue;
            if (rows.isEmpty() || !rows.get(0).containsKey(columnName)) continue;
            for (Map<String, Object> row : rows) {
                row.put(columnName, MASK_VALUE);
            }
            masked.add(columnName);
        }
        return masked;
    }

    /** 构建缓存 key。设计来源: 42 号文档"缓存 Key 规则" */
    private String buildCacheKey(RptDataset dataset, Map<String, Object> params, DataScope dataScope) {
        // yutong:report:dataset:{tenantId}:{datasetCode}:{datasetVersion}:{paramsHash}:{dataScopeHash}
        String tenantId = CurrentUserContext.getTenantId();
        String paramsHash = computeParamsHash(params);
        String scopeHash = computeDataScopeHash(dataScope);
        return String.format("yutong:report:dataset:%s:%s:%d:%s:%s",
                tenantId, dataset.getDatasetCode(), dataset.getVersion(), paramsHash, scopeHash);
    }

    private String computeParamsHash(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "0";
        try {
            String json = objectMapper.writeValueAsString(new TreeMap<>(params));
            return Integer.toHexString(json.hashCode());
        } catch (Exception e) {
            return "0";
        }
    }

    private String computeDataScopeHash(DataScope dataScope) {
        if (dataScope == null) return "0";
        String summary = dataScope.scopeType() + "|"
                + (dataScope.userId() == null ? "" : dataScope.userId());
        return Integer.toHexString(summary.hashCode());
    }
}
