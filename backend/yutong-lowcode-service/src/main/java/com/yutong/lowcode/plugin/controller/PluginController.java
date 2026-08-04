package com.yutong.lowcode.plugin.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.lowcode.plugin.domain.PluginAuditLog;
import com.yutong.lowcode.plugin.domain.PluginInstallation;
import com.yutong.lowcode.plugin.domain.PluginPackage;
import com.yutong.lowcode.plugin.dto.InstallPluginRequest;
import com.yutong.lowcode.plugin.dto.MarketPluginVO;
import com.yutong.lowcode.plugin.dto.PluginDependencyCheckResult;
import com.yutong.lowcode.plugin.dto.PluginPackagePageQuery;
import com.yutong.lowcode.plugin.dto.SavePluginPackageRequest;
import com.yutong.lowcode.plugin.service.PluginApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 插件管理接口（45 号文档「插件与模板生态设计」）。
 * 端点: 分页查询、详情、新建、更新、安装、卸载、启用、禁用、已安装列表、审计日志、插件市场列表、依赖校验。
 * 权限码: plugin:view/install/uninstall/manage
 */
@Tag(name = "插件管理")
@RestController
@RequestMapping("/api/v1/plugins")
public class PluginController {

    private final PluginApplicationService service;

    public PluginController(PluginApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询插件包", operationId = "listPlugins")
    @RequiresPermission("plugin:view")
    @GetMapping
    public Result<PageResult<PluginPackage>> page(@ModelAttribute PluginPackagePageQuery query) {
        return Result.ok(service.page(query), TraceContext.getTraceId());
    }

    @Operation(summary = "插件详情", operationId = "getPlugin")
    @RequiresPermission("plugin:view")
    @GetMapping("/{id}")
    public Result<PluginPackage> detail(@PathVariable String id) {
        return Result.ok(service.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "新建插件包", operationId = "createPlugin")
    @RequiresPermission("plugin:manage")
    @Auditable(operationType = "CREATE", module = "plugin", bizType = "plugin_package",
            bizIdExpr = "#result.data.id", content = "新建插件包", recordResult = true)
    @PostMapping
    public Result<PluginPackage> create(@Valid @RequestBody SavePluginPackageRequest request) {
        request.setId(null);
        return Result.ok(service.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新插件包", operationId = "updatePlugin")
    @RequiresPermission("plugin:manage")
    @Auditable(operationType = "UPDATE", module = "plugin", bizType = "plugin_package",
            bizIdExpr = "#id", content = "更新插件包")
    @PutMapping("/{id}")
    public Result<PluginPackage> update(@PathVariable String id,
                                        @Valid @RequestBody SavePluginPackageRequest request) {
        request.setId(id);
        return Result.ok(service.update(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "安装插件", operationId = "installPlugin")
    @RequiresPermission("plugin:install")
    @Auditable(operationType = "INSTALL", module = "plugin", bizType = "plugin_installation",
            bizIdExpr = "#result.data.id", content = "安装插件", recordResult = true)
    @PostMapping("/{id}/install")
    public Result<PluginInstallation> install(@PathVariable String id,
                                              @RequestBody(required = false) InstallPluginRequest request) {
        return Result.ok(service.install(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "卸载插件", operationId = "uninstallPlugin")
    @RequiresPermission("plugin:uninstall")
    @Auditable(operationType = "UNINSTALL", module = "plugin", bizType = "plugin_installation",
            bizIdExpr = "#id", content = "卸载插件")
    @PostMapping("/{id}/uninstall")
    public Result<Void> uninstall(@PathVariable String id) {
        service.uninstall(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "启用插件", operationId = "enablePlugin")
    @RequiresPermission("plugin:install")
    @Auditable(operationType = "ENABLE", module = "plugin", bizType = "plugin_installation",
            bizIdExpr = "#id", content = "启用插件")
    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable String id) {
        service.enable(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "禁用插件", operationId = "disablePlugin")
    @RequiresPermission("plugin:uninstall")
    @Auditable(operationType = "DISABLE", module = "plugin", bizType = "plugin_installation",
            bizIdExpr = "#id", content = "禁用插件")
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable String id) {
        service.disable(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "已安装插件列表", operationId = "listInstalledPlugins")
    @RequiresPermission("plugin:view")
    @GetMapping("/installed")
    public Result<List<PluginInstallation>> listInstalled() {
        return Result.ok(service.listInstalled(), TraceContext.getTraceId());
    }

    @Operation(summary = "插件市场列表", operationId = "listMarketPlugins")
    @RequiresPermission("plugin:view")
    @GetMapping("/market")
    public Result<List<MarketPluginVO>> listMarketPlugins() {
        return Result.ok(service.listMarketPlugins(), TraceContext.getTraceId());
    }

    @Operation(summary = "插件依赖校验", operationId = "checkPluginDependencies")
    @RequiresPermission("plugin:view")
    @GetMapping("/{id}/dependency-check")
    public Result<PluginDependencyCheckResult> checkDependencies(@PathVariable String id) {
        PluginPackage plugin = service.get(id);
        return Result.ok(service.checkDependencies(plugin), TraceContext.getTraceId());
    }

    @Operation(summary = "分页查询插件审计日志", operationId = "listPluginAuditLogs")
    @RequiresPermission("plugin:view")
    @GetMapping("/audit-logs")
    public Result<PageResult<PluginAuditLog>> pageAuditLogs(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String pluginCode) {
        return Result.ok(service.pageAuditLogs(pageNo, pageSize, pluginCode), TraceContext.getTraceId());
    }
}
