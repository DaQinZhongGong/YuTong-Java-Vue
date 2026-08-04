package com.yutong.lowcode.plugin.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.lowcode.plugin.domain.CodeTemplate;
import com.yutong.lowcode.plugin.dto.CodeTemplatePageQuery;
import com.yutong.lowcode.plugin.dto.SaveCodeTemplateRequest;
import com.yutong.lowcode.plugin.service.CodeTemplateApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 代码生成模板接口（45 号文档「插件与模板生态设计」E0）。
 * 端点: 分页查询、详情、新建、更新、删除。
 * 权限码: template:view/template:create/template:edit/template:delete
 */
@Tag(name = "代码生成模板")
@RestController
@RequestMapping("/api/v1/template/code-templates")
public class CodeTemplateController {

    private final CodeTemplateApplicationService service;

    public CodeTemplateController(CodeTemplateApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询代码模板", operationId = "listCodeTemplates")
    @RequiresPermission("template:view")
    @GetMapping
    public Result<PageResult<CodeTemplate>> page(@ModelAttribute CodeTemplatePageQuery query) {
        return Result.ok(service.page(query), TraceContext.getTraceId());
    }

    @Operation(summary = "代码模板详情", operationId = "getCodeTemplate")
    @RequiresPermission("template:view")
    @GetMapping("/{id}")
    public Result<CodeTemplate> detail(@PathVariable String id) {
        return Result.ok(service.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建代码模板", operationId = "createCodeTemplate")
    @RequiresPermission("template:create")
    @Auditable(operationType = "CREATE", module = "template", bizType = "code_template",
            bizIdExpr = "#result.data.id", content = "创建代码模板", recordResult = true)
    @PostMapping
    public Result<CodeTemplate> create(@Valid @RequestBody SaveCodeTemplateRequest request) {
        request.setId(null);
        return Result.ok(service.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新代码模板", operationId = "updateCodeTemplate")
    @RequiresPermission("template:edit")
    @Auditable(operationType = "UPDATE", module = "template", bizType = "code_template",
            bizIdExpr = "#id", content = "更新代码模板")
    @PutMapping("/{id}")
    public Result<CodeTemplate> update(@PathVariable String id,
                                       @Valid @RequestBody SaveCodeTemplateRequest request) {
        request.setId(id);
        return Result.ok(service.update(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除代码模板", operationId = "deleteCodeTemplate")
    @RequiresPermission("template:delete")
    @Auditable(operationType = "DELETE", module = "template", bizType = "code_template",
            bizIdExpr = "#id", content = "删除代码模板")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
