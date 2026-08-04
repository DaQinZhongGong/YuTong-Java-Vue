package com.yutong.lowcode.meta.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.lowcode.meta.domain.LcComponentRegistry;
import com.yutong.lowcode.meta.dto.ComponentRegistryPageQuery;
import com.yutong.lowcode.meta.dto.SaveComponentRegistryRequest;
import com.yutong.lowcode.meta.service.ComponentRegistryApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 低代码组件协议元数据接口。设计来源: 36-低代码高级能力设计 组件协议（GA2-L191 子任务 B）
 * 端点: 分页查询、详情、按分类、已发布列表、新建、更新、删除、发布、禁用。
 * 设计器组件库通过 /published 拉取物料区组件清单。
 */
@Tag(name = "低代码-组件协议")
@RestController
@RequestMapping("/api/v1/lowcode/components/registry")
public class ComponentRegistryController {

    private final ComponentRegistryApplicationService service;

    public ComponentRegistryController(ComponentRegistryApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询组件协议", operationId = "listLowcodeComponentRegistry")
    @RequiresPermission("lc:component:view")
    @GetMapping
    public Result<PageResult<LcComponentRegistry>> page(@ModelAttribute ComponentRegistryPageQuery query) {
        return Result.ok(service.page(query), TraceContext.getTraceId());
    }

    @Operation(summary = "已发布组件列表", operationId = "listPublishedLowcodeComponentRegistry")
    @RequiresPermission("lc:component:view")
    @GetMapping("/published")
    public Result<List<LcComponentRegistry>> published() {
        return Result.ok(service.listPublished(), TraceContext.getTraceId());
    }

    @Operation(summary = "按分类查询组件", operationId = "listLowcodeComponentRegistryByCategory")
    @RequiresPermission("lc:component:view")
    @GetMapping("/categories/{category}")
    public Result<List<LcComponentRegistry>> listByCategory(@PathVariable String category) {
        return Result.ok(service.listByCategory(category), TraceContext.getTraceId());
    }

    @Operation(summary = "组件协议详情", operationId = "getLowcodeComponentRegistry")
    @RequiresPermission("lc:component:view")
    @GetMapping("/{id}")
    public Result<LcComponentRegistry> detail(@PathVariable String id) {
        return Result.ok(service.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "新建组件协议", operationId = "createLowcodeComponentRegistry")
    @RequiresPermission("lc:component:create")
    @Auditable(operationType = "CREATE", module = "lowcode", bizType = "component_registry",
            bizIdExpr = "#result.data.id", content = "新建组件协议", recordResult = true)
    @PostMapping
    public Result<LcComponentRegistry> create(@Valid @RequestBody SaveComponentRegistryRequest request) {
        request.setId(null);
        return Result.ok(service.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新组件协议", operationId = "updateLowcodeComponentRegistry")
    @RequiresPermission("lc:component:edit")
    @Auditable(operationType = "UPDATE", module = "lowcode", bizType = "component_registry",
            bizIdExpr = "#id", content = "更新组件协议")
    @PutMapping("/{id}")
    public Result<LcComponentRegistry> update(@PathVariable String id,
                                              @Valid @RequestBody SaveComponentRegistryRequest request) {
        request.setId(id);
        return Result.ok(service.update(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除组件协议", operationId = "deleteLowcodeComponentRegistry")
    @RequiresPermission("lc:component:delete")
    @Auditable(operationType = "DELETE", module = "lowcode", bizType = "component_registry",
            bizIdExpr = "#id", content = "删除组件协议")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "发布组件协议", operationId = "publishLowcodeComponentRegistry")
    @RequiresPermission("lc:component:edit")
    @Auditable(operationType = "PUBLISH", module = "lowcode", bizType = "component_registry",
            bizIdExpr = "#id", content = "发布组件协议")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable String id) {
        service.publish(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "禁用组件协议", operationId = "disableLowcodeComponentRegistry")
    @RequiresPermission("lc:component:edit")
    @Auditable(operationType = "DISABLE", module = "lowcode", bizType = "component_registry",
            bizIdExpr = "#id", content = "禁用组件协议")
    @PostMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable String id) {
        service.disable(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
