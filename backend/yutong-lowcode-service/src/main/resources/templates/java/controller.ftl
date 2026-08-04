<#-- Java Controller 模板 | 设计来源: 65-低代码代码生成模板详设
  约束:
    - 只接收 Request DTO
    - 返回 Result<T>
    - OpenAPI 注解完整
    - 权限注解使用权限码
-->
<#-- @ftlvariable name="entity" type="com.yutong.lowcode.generator.service.CodeTemplateService.TemplateEntity" -->
<#-- @ftlvariable name="className" type="String" -->
<#-- @ftlvariable name="apiPrefix" type="String" -->
<#-- @ftlvariable name="permissions" type="java.util.List<String>" -->
// 由低代码生成器生成，可二开但需保留生成标记
package com.yutong.generated.${entity.moduleCode!'lowcode'}.controller;

import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.generated.${entity.moduleCode!'lowcode'}.dto.${className}Request;
import com.yutong.generated.${entity.moduleCode!'lowcode'}.dto.${className}Response;
import com.yutong.generated.${entity.moduleCode!'lowcode'}.service.${className}ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "${entity.entityName}")
@RestController
@RequestMapping("${apiPrefix!'/api/v1'}/${entity.entityCode?replace('_', '-')}")
public class ${className}Controller {

    private final ${className}ApplicationService service;

    public ${className}Controller(${className}ApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "创建${entity.entityName}", operationId = "create${className}")
    @PostMapping
    public Result<${className}Response> create(@Valid @RequestBody ${className}Request request) {
        return Result.ok(service.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新${entity.entityName}", operationId = "update${className}")
    @PutMapping("/{id}")
    public Result<${className}Response> update(@PathVariable String id,
                                                @Valid @RequestBody ${className}Request request) {
        return Result.ok(service.update(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "查询${entity.entityName}详情", operationId = "get${className}")
    @GetMapping("/{id}")
    public Result<${className}Response> get(@PathVariable String id) {
        return Result.ok(service.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "删除${entity.entityName}", operationId = "delete${className}")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
