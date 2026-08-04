package com.yutong.system.datasource.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.datasource.dto.*;
import com.yutong.system.datasource.service.DataSourceApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理接口。设计来源: 46-多数据源与数据集设计 line 119-128。
 * <p>
 * GA2-46 v1.5 落地 4 个 REST 端点:
 * <ul>
 *   <li>GET    /api/v1/admin/datasources          - 分页查询 (datasource:manage)</li>
 *   <li>POST   /api/v1/admin/datasources          - 创建 (datasource:manage)</li>
 *   <li>PUT    /api/v1/admin/datasources/{code}   - 更新 (datasource:manage)</li>
 *   <li>DELETE /api/v1/admin/datasources/{code}   - 删除 (datasource:manage)</li>
 *   <li>GET    /api/v1/admin/datasources/{code}   - 详情 (datasource:manage)</li>
 *   <li>POST   /api/v1/admin/datasources/{code}/test - 连接测试 (datasource:test)</li>
 *   <li>GET    /api/v1/admin/datasources/{code}/tables - 表元数据浏览 (datasource:metadata:view)</li>
 *   <li>GET    /api/v1/admin/datasources/{code}/tables/{table}/columns - 列元数据浏览 (datasource:metadata:view)</li>
 * </ul>
 * <p>
 * 所有接口 @Auditable 审计 (46 号文档 line 128: 数据源变更记审计日志)。
 * <p>
 * 安全约束:
 * <ul>
 *   <li>VO 脱敏: jdbc_url 只返回 host:port, 隐藏 user/password 参数</li>
 *   <li>连接测试失败不泄露数据库地址/用户名/密码/完整驱动异常</li>
 *   <li>元数据浏览按 ACL 过滤, 不返回未授权表/列</li>
 * </ul>
 */
@Tag(name = "数据源管理")
@RestController
@RequestMapping("/api/v1/admin/datasources")
public class DataSourceController {

    private final DataSourceApplicationService service;

    public DataSourceController(DataSourceApplicationService service) {
        this.service = service;
    }

    // ==================== 1. 数据源管理 (CRUD) ====================

    @Operation(summary = "分页查询数据源列表", operationId = "listDatasources")
    @RequiresPermission("datasource:manage")
    @GetMapping
    public Result<PageResult<DatasourceVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.pageDatasources(page, size, keyword), TraceContext.getTraceId());
    }

    @Operation(summary = "查询数据源详情", operationId = "getDatasource")
    @RequiresPermission("datasource:manage")
    @GetMapping("/{code}")
    public Result<DatasourceVO> get(@PathVariable String code) {
        return Result.ok(service.getDatasource(code), TraceContext.getTraceId());
    }

    @Operation(summary = "创建数据源", operationId = "createDatasource")
    @RequiresPermission("datasource:manage")
    @Auditable(operationType = "CREATE", module = "system", bizType = "sys_datasource",
            bizIdExpr = "#request.datasourceCode", content = "创建数据源", recordResult = true)
    @PostMapping
    public Result<DatasourceVO> create(@Valid @RequestBody SaveDatasourceRequest request) {
        return Result.ok(service.saveDatasource(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新数据源", operationId = "updateDatasource")
    @RequiresPermission("datasource:manage")
    @Auditable(operationType = "UPDATE", module = "system", bizType = "sys_datasource",
            bizIdExpr = "#code", content = "更新数据源", recordResult = true)
    @PutMapping("/{code}")
    public Result<DatasourceVO> update(@PathVariable String code,
                                        @Valid @RequestBody SaveDatasourceRequest request) {
        // 保证 path 和 body 中的 code 一致
        request.setDatasourceCode(code);
        return Result.ok(service.saveDatasource(request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除数据源", operationId = "deleteDatasource")
    @RequiresPermission("datasource:manage")
    @Auditable(operationType = "DELETE", module = "system", bizType = "sys_datasource",
            bizIdExpr = "#code", content = "删除数据源")
    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        service.deleteDatasource(code);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ==================== 2. 连接测试 ====================

    @Operation(summary = "数据源连接测试", operationId = "testDatasourceConnection")
    @RequiresPermission("datasource:test")
    @Auditable(operationType = "TEST", module = "system", bizType = "sys_datasource",
            bizIdExpr = "#code", content = "数据源连接测试", recordResult = true)
    @PostMapping("/{code}/test")
    public Result<ConnectionTestResultVO> test(@PathVariable String code) {
        return Result.ok(service.testConnection(code), TraceContext.getTraceId());
    }

    // ==================== 3. 元数据浏览 ====================

    @Operation(summary = "表元数据浏览 (按 ACL 过滤)", operationId = "listDatasourceTables")
    @RequiresPermission("datasource:metadata:view")
    @Auditable(operationType = "VIEW", module = "system", bizType = "sys_datasource_metadata",
            bizIdExpr = "#code", content = "表元数据浏览")
    @GetMapping("/{code}/tables")
    public Result<List<TableMetadataVO>> listTables(@PathVariable String code) {
        return Result.ok(service.listTables(code), TraceContext.getTraceId());
    }

    @Operation(summary = "列元数据浏览 (按 ACL 过滤, 敏感列隐藏)", operationId = "listDatasourceColumns")
    @RequiresPermission("datasource:metadata:view")
    @Auditable(operationType = "VIEW", module = "system", bizType = "sys_datasource_metadata",
            bizIdExpr = "#code", content = "列元数据浏览")
    @GetMapping("/{code}/tables/{table}/columns")
    public Result<List<ColumnMetadataVO>> listColumns(@PathVariable String code,
                                                        @PathVariable String table) {
        return Result.ok(service.listColumns(code, table), TraceContext.getTraceId());
    }
}
