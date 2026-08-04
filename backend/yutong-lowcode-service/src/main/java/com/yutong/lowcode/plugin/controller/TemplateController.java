package com.yutong.lowcode.plugin.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.lowcode.plugin.domain.MktTemplate;
import com.yutong.lowcode.plugin.dto.TemplatePageQuery;
import com.yutong.lowcode.plugin.service.TemplateApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 模板市场接口（45 号文档「插件与模板生态设计」）。
 * 端点: 分页查询、详情、创建、更新、删除、发布。
 * 权限码: template:view/create/edit/delete
 */
@Tag(name = "模板市场")
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateApplicationService service;

    public TemplateController(TemplateApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询模板", operationId = "listTemplates")
    @RequiresPermission("template:view")
    @GetMapping
    public Result<PageResult<MktTemplate>> page(@ModelAttribute TemplatePageQuery query) {
        return Result.ok(service.page(query), TraceContext.getTraceId());
    }

    @Operation(summary = "模板详情", operationId = "getTemplate")
    @RequiresPermission("template:view")
    @GetMapping("/{id}")
    public Result<MktTemplate> detail(@PathVariable String id) {
        return Result.ok(service.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建模板", operationId = "createTemplate")
    @RequiresPermission("template:create")
    @Auditable(operationType = "CREATE", module = "template", bizType = "mkt_template",
            bizIdExpr = "#result.data.id", content = "创建模板", recordResult = true)
    @PostMapping
    public Result<MktTemplate> create(@RequestBody MktTemplate request) {
        return Result.ok(service.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新模板", operationId = "updateTemplate")
    @RequiresPermission("template:edit")
    @Auditable(operationType = "UPDATE", module = "template", bizType = "mkt_template",
            bizIdExpr = "#id", content = "更新模板")
    @PutMapping("/{id}")
    public Result<MktTemplate> update(@PathVariable String id, @RequestBody MktTemplate request) {
        return Result.ok(service.update(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除模板", operationId = "deleteTemplate")
    @RequiresPermission("template:delete")
    @Auditable(operationType = "DELETE", module = "template", bizType = "mkt_template",
            bizIdExpr = "#id", content = "删除模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "发布模板", operationId = "publishTemplate")
    @RequiresPermission("template:edit")
    @Auditable(operationType = "PUBLISH", module = "template", bizType = "mkt_template",
            bizIdExpr = "#id", content = "发布模板")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable String id) {
        service.publish(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
