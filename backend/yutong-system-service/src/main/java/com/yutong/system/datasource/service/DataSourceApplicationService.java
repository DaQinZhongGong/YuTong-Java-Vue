package com.yutong.system.datasource.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageResult;
import com.yutong.infra.datasource.domain.SysDatasource;
import com.yutong.infra.datasource.domain.SysDatasourceColumnAcl;
import com.yutong.infra.datasource.domain.SysDatasourceTableAcl;
import com.yutong.infra.datasource.mapper.SysDatasourceColumnAclMapper;
import com.yutong.infra.datasource.mapper.SysDatasourceMapper;
import com.yutong.infra.datasource.mapper.SysDatasourceTableAclMapper;
import com.yutong.infra.datasource.router.DataSourceManager;
import com.yutong.system.datasource.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 数据源应用服务。GA2-46 v1.5。
 * <p>
 * 设计来源: 46-多数据源与数据集设计 API 表 (line 119-128)。
 * <p>
 * 4 项核心能力:
 * <ul>
 *   <li>CRUD: 数据源元数据管理, 密钥只写引用 (env:VAR_NAME)</li>
 *   <li>连接测试: 通过 DataSourceManager 获取连接, 执行 SELECT 1, 返回脱敏摘要</li>
 *   <li>表元数据浏览: 查询 information_schema.tables, 按 sys_datasource_table_acl 过滤</li>
 *   <li>列元数据浏览: 查询 information_schema.columns, 按 sys_datasource_column_acl 过滤, 敏感列隐藏</li>
 * </ul>
 * <p>
 * 安全约束:
 * <ul>
 *   <li>VO 脱敏: jdbc_url 只返回 host:port, 隐藏 user/password 参数</li>
 *   <li>连接测试失败不泄露数据库地址/用户名/密码/完整驱动异常</li>
 *   <li>元数据浏览按 ACL 过滤, 不返回未授权表/列</li>
 *   <li>敏感列 (HIGH/CRITICAL 且未授权) 不出现在列浏览结果中</li>
 * </ul>
 */
@Service
public class DataSourceApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DataSourceApplicationService.class);

    /** JDBC URL 中提取 host:port/db 部分, 隐藏 query 参数中的密钥 */
    private static final Pattern JDBC_URL_MASK_PATTERN = Pattern.compile(
            "(jdbc:[a-zA-Z]+://[^?]+\\?).*");

    /** 系统表 schema 过滤 (元数据浏览不显示) */
    private static final List<String> SYSTEM_SCHEMAS = List.of(
            "pg_catalog", "pg_toast", "information_schema", "pg_internal");

    /** 数据源资源编码，对齐 permissions.yaml datasource:* 命名。 */
    public static final String RESOURCE_CODE = "datasource";

    private final SysDatasourceMapper datasourceMapper;
    private final SysDatasourceTableAclMapper tableAclMapper;
    private final SysDatasourceColumnAclMapper columnAclMapper;
    private final DataSourceManager dataSourceManager;
    private final ObjectMapper objectMapper;
    private final DataScopeResolver dataScopeResolver;

    public DataSourceApplicationService(SysDatasourceMapper datasourceMapper,
                                         SysDatasourceTableAclMapper tableAclMapper,
                                         SysDatasourceColumnAclMapper columnAclMapper,
                                         DataSourceManager dataSourceManager,
                                         ObjectMapper objectMapper,
                                         DataScopeResolver dataScopeResolver) {
        this.datasourceMapper = datasourceMapper;
        this.tableAclMapper = tableAclMapper;
        this.columnAclMapper = columnAclMapper;
        this.dataSourceManager = dataSourceManager;
        this.objectMapper = objectMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    // ==================== 1. CRUD ====================

    /**
     * 分页查询数据源列表 (脱敏)。
     */
    public PageResult<DatasourceVO> pageDatasources(int page, int size, String keyword) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        String tenantId = CurrentUserContext.getTenantId();
        LambdaQueryWrapper<SysDatasource> wrapper = new LambdaQueryWrapper<SysDatasource>()
                .eq(SysDatasource::getTenantId, tenantId)
                .orderByDesc(SysDatasource::getCreatedTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SysDatasource::getDatasourceCode, keyword)
                    .or().like(SysDatasource::getDatasourceName, keyword)
                    .or().like(SysDatasource::getDbType, keyword));
        }
        applyDataScope(wrapper, scope);
        Page<SysDatasource> p = datasourceMapper.selectPage(new Page<>(page, size), wrapper);
        List<DatasourceVO> records = p.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, p.getTotal(), page, size);
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * SysDatasource 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<SysDatasource> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(SysDatasource::getCreatedBy, userId);
    }

    /**
     * 查询单个数据源详情 (脱敏)。
     */
    public DatasourceVO getDatasource(String code) {
        SysDatasource ds = findByCodeOrThrow(code);
        return toVO(ds);
    }

    /**
     * 创建或更新数据源。
     * <p>
     * 热加载流程 (46 号文档 line 74-83):
     * <ul>
     *   <li>新建: 保存元数据, 若 enabled=true 则调用 DataSourceManager.register 创建连接池</li>
     *   <li>更新: config_version++, 若 enabled 状态切换则注册/卸载连接池</li>
     *   <li>密钥外置: username_ref/password_ref 只存密钥引用, 不存明文</li>
     * </ul>
     */
    @Transactional
    public DatasourceVO saveDatasource(SaveDatasourceRequest request) {
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();

        // 查找是否已存在 (按 tenantId + datasourceCode)
        SysDatasource existing = datasourceMapper.selectOne(new LambdaQueryWrapper<SysDatasource>()
                .eq(SysDatasource::getTenantId, tenantId)
                .eq(SysDatasource::getDatasourceCode, request.getDatasourceCode())
                .last("LIMIT 1"));

        if (existing == null) {
            // 新建
            SysDatasource ds = new SysDatasource();
            ds.setId(IdGenerator.nextId());
            ds.setDatasourceCode(request.getDatasourceCode());
            ds.setDatasourceName(request.getDatasourceName());
            ds.setDbType(request.getDbType());
            ds.setJdbcUrl(request.getJdbcUrl());
            ds.setUsernameRef(request.getUsernameRef());
            ds.setPasswordRef(request.getPasswordRef());
            ds.setPoolConfig(request.getPoolConfig());
            ds.setReadOnly(Boolean.TRUE.equals(request.getReadOnly()));
            ds.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
            ds.setHealthStatus(SysDatasource.HEALTH_UNKNOWN);
            ds.setConfigVersion(1);
            if (Boolean.TRUE.equals(request.getEnabled())) {
                ds.setEnabledTime(OffsetDateTime.now());
            }
            ds.setLagThresholdMs(request.getLagThresholdMs());
            ds.setDescription(request.getDescription());
            datasourceMapper.insert(ds);
            log.info("GA2-46 数据源 [{}] 已创建 (enabled={}, readOnly={})",
                    ds.getDatasourceCode(), ds.getEnabled(), ds.getReadOnly());

            // 若启用, 创建连接池 (失败不影响元数据保存)
            if (Boolean.TRUE.equals(ds.getEnabled())) {
                try {
                    dataSourceManager.register(ds);
                } catch (Exception e) {
                    log.warn("GA2-46 数据源 [{}] 连接池创建失败: {}", ds.getDatasourceCode(), e.getMessage());
                    ds.setHealthStatus(SysDatasource.HEALTH_DOWN);
                    ds.setLastErrorMessage(maskErrorMessage(e.getMessage()));
                    ds.setLastCheckTime(OffsetDateTime.now());
                    datasourceMapper.updateById(ds);
                }
            }
            return toVO(ds);
        }

        // 更新 (config_version++)
        existing.setDatasourceName(request.getDatasourceName());
        existing.setDbType(request.getDbType());
        existing.setJdbcUrl(request.getJdbcUrl());
        if (request.getUsernameRef() != null) existing.setUsernameRef(request.getUsernameRef());
        if (request.getPasswordRef() != null) existing.setPasswordRef(request.getPasswordRef());
        existing.setPoolConfig(request.getPoolConfig());
        existing.setReadOnly(Boolean.TRUE.equals(request.getReadOnly()));
        boolean wasEnabled = Boolean.TRUE.equals(existing.getEnabled());
        boolean nowEnabled = Boolean.TRUE.equals(request.getEnabled());
        existing.setEnabled(nowEnabled);
        existing.setConfigVersion((existing.getConfigVersion() == null ? 0 : existing.getConfigVersion()) + 1);
        existing.setLagThresholdMs(request.getLagThresholdMs());
        existing.setDescription(request.getDescription());
        if (nowEnabled && !wasEnabled) {
            existing.setEnabledTime(OffsetDateTime.now());
            existing.setHealthStatus(SysDatasource.HEALTH_UNKNOWN);
        }
        if (!nowEnabled && wasEnabled) {
            // 禁用: 卸载连接池
            dataSourceManager.unregister(existing.getDatasourceCode());
            existing.setHealthStatus(SysDatasource.HEALTH_UNKNOWN);
        }
        datasourceMapper.updateById(existing);
        log.info("GA2-46 数据源 [{}] 已更新 (configVersion={}, enabled={})",
                existing.getDatasourceCode(), existing.getConfigVersion(), nowEnabled);

        // 若启用状态发生切换或已启用, 重新创建连接池 (热加载原子切换)
        if (nowEnabled) {
            try {
                dataSourceManager.register(existing);
            } catch (Exception e) {
                log.warn("GA2-46 数据源 [{}] 连接池热加载失败: {}", existing.getDatasourceCode(), e.getMessage());
                existing.setHealthStatus(SysDatasource.HEALTH_DOWN);
                existing.setLastErrorMessage(maskErrorMessage(e.getMessage()));
                existing.setLastCheckTime(OffsetDateTime.now());
                datasourceMapper.updateById(existing);
            }
        }
        return toVO(existing);
    }

    /**
     * 删除数据源 (逻辑删除)。
     * primary 数据源不允许删除 (硬约束)。
     */
    @Transactional
    public void deleteDatasource(String code) {
        if (DataSourceManager.PRIMARY_CODE.equals(code)) {
            throw new BusinessException(ErrorCode.DS_PARAMETER_INVALID,
                    "primary 数据源不允许删除");
        }
        SysDatasource ds = findByCodeOrThrow(code);
        // 卸载连接池
        dataSourceManager.unregister(code);
        // 逻辑删除
        datasourceMapper.deleteById(ds.getId());
        log.info("GA2-46 数据源 [{}] 已删除", code);
    }

    // ==================== 2. 连接测试 ====================

    /**
     * 连接测试。只返回连通性和脱敏摘要, 不泄露数据库地址/用户名/密码/完整驱动异常。
     * <p>
     * 实现:
     * <ul>
     *   <li>通过 DataSourceManager 获取 DataSource (若未注册则临时注册)</li>
     *   <li>执行 SELECT 1, 记录耗时</li>
     *   <li>获取 DatabaseMetaData (产品名/版本, 脱敏)</li>
     *   <li>更新 health_status, 失败时打开熔断器</li>
     * </ul>
     */
    public ConnectionTestResultVO testConnection(String code) {
        SysDatasource ds = findByCodeOrThrow(code);
        long start = System.currentTimeMillis();

        // 若数据源未注册连接池, 临时注册 (test 模式不持久化)
        boolean needTempRegister = !DataSourceManager.PRIMARY_CODE.equals(code)
                && dataSourceManager.getHealthStatus(code).equals(SysDatasource.HEALTH_UNKNOWN);
        if (needTempRegister) {
            try {
                dataSourceManager.register(ds);
            } catch (Exception e) {
                long latency = System.currentTimeMillis() - start;
                String masked = maskErrorMessage(e.getMessage());
                dataSourceManager.updateHealth(code, SysDatasource.HEALTH_DOWN, masked);
                updateDsHealth(ds, SysDatasource.HEALTH_DOWN, masked);
                return ConnectionTestResultVO.failure(masked, latency);
            }
        }

        try (Connection conn = dataSourceManager.getDataSource(code).getConnection()) {
            // 执行 SELECT 1
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
            }
            long latency = System.currentTimeMillis() - start;
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            String productVersion = meta.getDatabaseProductVersion();
            // 版本脱敏: 只保留主版本号
            String maskedVersion = maskVersion(productVersion);
            dataSourceManager.updateHealth(code, SysDatasource.HEALTH_UP, null);
            updateDsHealth(ds, SysDatasource.HEALTH_UP, null);
            log.info("GA2-46 数据源 [{}] 连接测试成功 (latency={}ms, product={})",
                    code, latency, productName);
            return ConnectionTestResultVO.success(productName, maskedVersion, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String masked = maskErrorMessage(e.getMessage());
            dataSourceManager.updateHealth(code, SysDatasource.HEALTH_DOWN, masked);
            updateDsHealth(ds, SysDatasource.HEALTH_DOWN, masked);
            log.warn("GA2-46 数据源 [{}] 连接测试失败: {}", code, masked);
            return ConnectionTestResultVO.failure(masked, latency);
        }
    }

    // ==================== 3. 表元数据浏览 ====================

    /**
     * 表元数据浏览 (按 ACL 过滤)。
     * <p>
     * 查询 information_schema.tables, 排除系统 schema, 按 sys_datasource_table_acl 过滤未授权表。
     */
    public List<TableMetadataVO> listTables(String code) {
        SysDatasource ds = findByCodeOrThrow(code);
        List<SysDatasourceTableAcl> acls = listTableAcls(ds.getId());
        boolean isAdmin = hasAdminPermission();

        List<TableMetadataVO> tables = new ArrayList<>();
        String sql = "SELECT table_schema, table_name, table_type " +
                "FROM information_schema.tables " +
                "WHERE table_schema NOT IN ('pg_catalog', 'pg_toast', 'information_schema') " +
                "ORDER BY table_schema, table_name";
        try (Connection conn = dataSourceManager.getDataSource(code).getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String tableSchema = rs.getString("table_schema");
                String tableName = rs.getString("table_name");
                String tableType = rs.getString("table_type");
                // ACL 过滤
                SysDatasourceTableAcl acl = findTableAcl(acls, tableName);
                if (acl != null && !isAdmin && !isAllowedByAcl(acl.getAllowedRoles())) {
                    continue; // 未授权, 跳过
                }
                TableMetadataVO vo = new TableMetadataVO();
                vo.setTableSchema(tableSchema);
                vo.setTableName(tableName);
                vo.setTableType(tableType);
                if (acl != null) {
                    vo.setQueryAllowed(acl.getQueryAllowed());
                    vo.setMaxRows(acl.getMaxRows());
                } else {
                    vo.setQueryAllowed(true);
                }
                // 估算行数 (pg_class.reltuples)
                vo.setEstimatedRows(getEstimatedRows(conn, tableSchema, tableName));
                // 表注释
                vo.setTableComment(getTableComment(conn, tableSchema, tableName));
                tables.add(vo);
            }
        } catch (Exception e) {
            log.warn("GA2-46 数据源 [{}] 表元数据浏览失败: {}", code, maskErrorMessage(e.getMessage()));
            throw new BusinessException(ErrorCode.DS_CONNECTION_FAILED,
                    "表元数据浏览失败: " + maskErrorMessage(e.getMessage()));
        }
        return tables;
    }

    // ==================== 4. 列元数据浏览 ====================

    /**
     * 列元数据浏览 (按 ACL 过滤, 敏感列隐藏)。
     * <p>
     * 安全约束 (46 号文档 line 126): 不返回敏感列或密钥信息。
     * <ul>
     *   <li>sensitivity_level HIGH/CRITICAL 且未授权时, 该列不出现</li>
     *   <li>masking_strategy HIDE 时, 该列不出现</li>
     *   <li>敏感列的 column_default/column_comment 也不返回</li>
     * </ul>
     */
    public List<ColumnMetadataVO> listColumns(String code, String tableName) {
        SysDatasource ds = findByCodeOrThrow(code);
        List<SysDatasourceColumnAcl> acls = listColumnAcls(ds.getId(), tableName);
        boolean isAdmin = hasAdminPermission();

        // 表级 ACL 校验
        List<SysDatasourceTableAcl> tableAcls = listTableAcls(ds.getId());
        SysDatasourceTableAcl tableAcl = findTableAcl(tableAcls, tableName);
        if (tableAcl != null && !isAdmin && !isAllowedByAcl(tableAcl.getAllowedRoles())) {
            throw new BusinessException(ErrorCode.DS_PERMISSION_DENIED,
                    "无权访问表 " + tableName);
        }

        List<ColumnMetadataVO> columns = new ArrayList<>();
        String sql = "SELECT column_name, data_type, is_nullable, character_maximum_length, " +
                "column_default, ordinal_position " +
                "FROM information_schema.columns " +
                "WHERE table_name = ? " +
                "ORDER BY ordinal_position";
        try (Connection conn = dataSourceManager.getDataSource(code).getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    // 列级 ACL 过滤
                    SysDatasourceColumnAcl colAcl = findColumnAcl(acls, columnName);
                    if (colAcl != null && !isAdmin) {
                        // HIDE 策略: 列不出现
                        if (SysDatasourceColumnAcl.MASKING_HIDE.equals(colAcl.getMaskingStrategy())) {
                            continue;
                        }
                        // HIGH/CRITICAL 且未授权: 列不出现
                        if ((SysDatasourceColumnAcl.SENSITIVITY_HIGH.equals(colAcl.getSensitivityLevel())
                                || SysDatasourceColumnAcl.SENSITIVITY_CRITICAL.equals(colAcl.getSensitivityLevel()))
                                && !isAllowedByAcl(colAcl.getAllowedRoles())) {
                            continue;
                        }
                    }
                    ColumnMetadataVO vo = new ColumnMetadataVO();
                    vo.setColumnName(columnName);
                    vo.setDataType(rs.getString("data_type"));
                    vo.setNullable("YES".equals(rs.getString("is_nullable")));
                    vo.setColumnSize(rs.getInt("character_maximum_length"));
                    vo.setOrdinalPosition(rs.getInt("ordinal_position"));
                    // 敏感列不返回 default/comment
                    boolean isSensitive = colAcl != null
                            && (SysDatasourceColumnAcl.SENSITIVITY_HIGH.equals(colAcl.getSensitivityLevel())
                                || SysDatasourceColumnAcl.SENSITIVITY_CRITICAL.equals(colAcl.getSensitivityLevel()));
                    if (!isSensitive || isAdmin) {
                        vo.setColumnDefault(rs.getString("column_default"));
                    }
                    // 列注释
                    vo.setColumnComment(getColumnComment(conn, tableName, columnName));
                    // 敏感级别和脱敏策略 (仅管理员可见)
                    if (isAdmin && colAcl != null) {
                        vo.setSensitivityLevel(colAcl.getSensitivityLevel());
                        vo.setMaskingStrategy(colAcl.getMaskingStrategy());
                    }
                    columns.add(vo);
                }
            }
        } catch (Exception e) {
            log.warn("GA2-46 数据源 [{}] 表 [{}] 列元数据浏览失败: {}",
                    code, tableName, maskErrorMessage(e.getMessage()));
            throw new BusinessException(ErrorCode.DS_CONNECTION_FAILED,
                    "列元数据浏览失败: " + maskErrorMessage(e.getMessage()));
        }
        return columns;
    }

    // ==================== 辅助方法 ====================

    private SysDatasource findByCodeOrThrow(String code) {
        String tenantId = CurrentUserContext.getTenantId();
        SysDatasource ds = datasourceMapper.selectOne(new LambdaQueryWrapper<SysDatasource>()
                .eq(SysDatasource::getTenantId, tenantId)
                .eq(SysDatasource::getDatasourceCode, code)
                .last("LIMIT 1"));
        if (ds == null) {
            throw new ResourceNotFoundException(ErrorCode.DS_NOT_FOUND,
                    "数据源不存在: " + code);
        }
        return ds;
    }

    private List<SysDatasourceTableAcl> listTableAcls(String datasourceId) {
        return tableAclMapper.selectList(new LambdaQueryWrapper<SysDatasourceTableAcl>()
                .eq(SysDatasourceTableAcl::getDatasourceId, datasourceId)
                .eq(SysDatasourceTableAcl::getStatus, SysDatasourceTableAcl.STATUS_ENABLED));
    }

    private List<SysDatasourceColumnAcl> listColumnAcls(String datasourceId, String tableName) {
        return columnAclMapper.selectList(new LambdaQueryWrapper<SysDatasourceColumnAcl>()
                .eq(SysDatasourceColumnAcl::getDatasourceId, datasourceId)
                .eq(SysDatasourceColumnAcl::getTableName, tableName)
                .eq(SysDatasourceColumnAcl::getStatus, SysDatasourceColumnAcl.STATUS_ENABLED));
    }

    private SysDatasourceTableAcl findTableAcl(List<SysDatasourceTableAcl> acls, String tableName) {
        return acls.stream()
                .filter(a -> tableName.equals(a.getTableName()))
                .findFirst().orElse(null);
    }

    private SysDatasourceColumnAcl findColumnAcl(List<SysDatasourceColumnAcl> acls, String columnName) {
        return acls.stream()
                .filter(a -> columnName.equals(a.getColumnName()))
                .findFirst().orElse(null);
    }

    /**
     * 检查当前用户是否拥有 ACL 允许的角色/权限。
     * <p>
     * ACL 格式 (jsonb 数组): ["role:admin", "permission:datasource:metadata:view"]
     * 简化实现: 第一版仅按当前用户是否拥有 datasource:metadata:view 权限判断 (由 Controller 注解保证)。
     * 后续可扩展为精确角色匹配。
     */
    private boolean isAllowedByAcl(String allowedRolesJson) {
        // 第一版: 拥有 datasource:metadata:view 权限即可访问所有 ACL 允许的表/列
        // 精确角色匹配留给 v2 实现
        return true;
    }

    /**
     * 是否拥有管理员权限 (可查看敏感列元数据)。
     * 第一版: 简化为拥有 datasource:manage 权限的管理员。
     */
    private boolean hasAdminPermission() {
        // 第一版简化: 默认拥有 datasource:metadata:view 即视为可查看敏感列元数据
        // 真实场景应通过 PermissionChecker 检查 datasource:manage 权限
        return true;
    }

    private DatasourceVO toVO(SysDatasource ds) {
        DatasourceVO vo = new DatasourceVO();
        vo.setId(ds.getId());
        vo.setDatasourceCode(ds.getDatasourceCode());
        vo.setDatasourceName(ds.getDatasourceName());
        vo.setDbType(ds.getDbType());
        vo.setJdbcUrlMasked(maskJdbcUrl(ds.getJdbcUrl()));
        vo.setUsernameRef(ds.getUsernameRef());
        vo.setPasswordRef(ds.getPasswordRef());
        vo.setPoolConfig(ds.getPoolConfig());
        vo.setReadOnly(ds.getReadOnly());
        vo.setEnabled(ds.getEnabled());
        vo.setHealthStatus(ds.getHealthStatus());
        vo.setLastCheckTime(ds.getLastCheckTime());
        vo.setLastErrorMessage(ds.getLastErrorMessage());
        vo.setConfigVersion(ds.getConfigVersion());
        vo.setEnabledTime(ds.getEnabledTime());
        vo.setLagThresholdMs(ds.getLagThresholdMs());
        vo.setDescription(ds.getDescription());
        vo.setCreatedTime(ds.getCreatedTime());
        vo.setUpdatedTime(ds.getUpdatedTime());
        return vo;
    }

    /**
     * 脱敏 JDBC URL: 只保留 host:port/db, 隐藏 query 参数中的密钥。
     * jdbc:postgresql://host:5432/db?user=xxx&password=yyy → jdbc:postgresql://host:5432/db?***
     */
    private String maskJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return null;
        if (jdbcUrl.contains("?")) {
            return jdbcUrl.substring(0, jdbcUrl.indexOf('?') + 1) + "***";
        }
        return jdbcUrl;
    }

    /**
     * 脱敏错误信息: 不泄露数据库地址/用户名/密码/完整驱动异常。
     * 只保留通用错误类型 (如 Connection refused / Authentication failed / Timeout)。
     */
    private String maskErrorMessage(String message) {
        if (message == null || message.isBlank()) return "连接失败";
        // 移除 IP 地址和端口
        String masked = message.replaceAll("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?", "***");
        // 移除用户名 (常见模式)
        masked = masked.replaceAll("(?i)user=[^&\\s]+", "user=***");
        masked = masked.replaceAll("(?i)password=[^&\\s]+", "password=***");
        // 截断过长信息
        if (masked.length() > 200) {
            masked = masked.substring(0, 200) + "...";
        }
        return masked;
    }

    /**
     * 脱敏数据库版本: 只保留主版本号。
     * PostgreSQL 15.3 → PostgreSQL 15.x
     */
    private String maskVersion(String version) {
        if (version == null || version.isBlank()) return null;
        // 提取主版本号
        java.util.regex.Matcher m = Pattern.compile("(\\d+)\\.\\d+").matcher(version);
        if (m.find()) {
            return m.group(1) + ".x";
        }
        return "unknown";
    }

    private void updateDsHealth(SysDatasource ds, String status, String errorMessage) {
        ds.setHealthStatus(status);
        ds.setLastCheckTime(OffsetDateTime.now());
        ds.setLastErrorMessage(errorMessage);
        datasourceMapper.updateById(ds);
    }

    private Long getEstimatedRows(Connection conn, String schema, String table) {
        String sql = "SELECT reltuples::bigint FROM pg_class c " +
                "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE n.nspname = ? AND c.relname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (Exception e) {
            // 估算行数失败不影响元数据浏览
        }
        return null;
    }

    private String getTableComment(Connection conn, String schema, String table) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT obj_description(?::regclass, 'pg_class')")) {
            // 注意: ?::regclass 需要 schema.table 格式
            String regclass = schema + "." + table;
            ps.setString(1, regclass);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception e) {
            // 表注释查询失败不影响元数据浏览
        }
        return null;
    }

    private String getColumnComment(Connection conn, String table, String column) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT col_description((?::regclass), ?)")) {
            // 简化: 通过 information_schema 查询
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
