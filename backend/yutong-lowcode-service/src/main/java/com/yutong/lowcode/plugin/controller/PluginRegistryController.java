package com.yutong.lowcode.plugin.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.lowcode.plugin.domain.PluginRegistry;
import com.yutong.lowcode.plugin.dto.PluginRegistryPageQuery;
import com.yutong.lowcode.plugin.dto.SavePluginRegistryRequest;
import com.yutong.lowcode.plugin.service.PluginRegistryApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 插件注册表接口（45 号文档「插件与模板生态设计」E0）。
 * 端点: 分页查询、详情、新建、更新、删除。
 * 权限码: plugin:view/plugin:create/plugin:edit/plugin:delete
 */
@Tag(name = "插件注册表")
@RestController
@RequestMapping("/api/v1/plugin/registries")
public class PluginRegistryController {

    private final PluginRegistryApplicationService service;

    public PluginRegistryController(PluginRegistryApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询插件注册表", operationId = "listPluginRegistries")
    @RequiresPermission("plugin:view")
    @GetMapping
    public Result<PageResult<PluginRegistry>> page(@ModelAttribute PluginRegistryPageQuery query) {
        return Result.ok(service.page(query), TraceContext.getTraceId());
    }

    @Operation(summary = "插件注册表详情", operationId = "getPluginRegistry")
    @RequiresPermission("plugin:view")
    @GetMapping("/{id}")
    public Result<PluginRegistry> detail(@PathVariable String id) {
        return Result.ok(service.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建插件注册表", operationId = "createPluginRegistry")
    @RequiresPermission("plugin:create")
    @Auditable(operationType = "CREATE", module = "plugin", bizType = "plugin_registry",
            bizIdExpr = "#result.data.id", content = "创建插件注册表", recordResult = true)
    @PostMapping
    public Result<PluginRegistry> create(@Valid @RequestBody SavePluginRegistryRequest request) {
        request.setId(null);
        return Result.ok(service.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新插件注册表", operationId = "updatePluginRegistry")
    @RequiresPermission("plugin:edit")
    @Auditable(operationType = "UPDATE", module = "plugin", bizType = "plugin_registry",
            bizIdExpr = "#id", content = "更新插件注册表")
    @PutMapping("/{id}")
    public Result<PluginRegistry> update(@PathVariable String id,
                                         @Valid @RequestBody SavePluginRegistryRequest request) {
        request.setId(id);
        return Result.ok(service.update(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除插件注册表", operationId = "deletePluginRegistry")
    @RequiresPermission("plugin:delete")
    @Auditable(operationType = "DELETE", module = "plugin", bizType = "plugin_registry",
            bizIdExpr = "#id", content = "删除插件注册表")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
