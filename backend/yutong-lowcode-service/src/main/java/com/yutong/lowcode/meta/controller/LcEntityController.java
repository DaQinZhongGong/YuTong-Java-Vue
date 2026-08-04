package com.yutong.lowcode.meta.controller;

import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.idempotency.Idempotent;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.lowcode.meta.domain.LcEntity;
import com.yutong.lowcode.meta.dto.LcEntityDetailVO;
import com.yutong.lowcode.meta.dto.SaveLcEntityRequest;
import com.yutong.lowcode.meta.service.LcEntityApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 低代码实体接口。设计来源: 08-API契约设计、58-后端API逐接口任务清单
 */
@Tag(name = "低代码-实体")
@RestController
@RequestMapping("/api/v1/lowcode/entities")
public class LcEntityController {

    private final LcEntityApplicationService service;

    public LcEntityController(LcEntityApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询实体", operationId = "listLowcodeEntities")
    @RequiresPermission("lc:entity:list")
    @GetMapping
    public Result<PageResult<LcEntity>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String entityCode,
                                             @RequestParam(required = false) String entityName,
                                             @RequestParam(required = false) String status) {
        return Result.ok(service.pageEntities(PageRequest.of(page, size), entityCode, entityName, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "实体详情", operationId = "getLowcodeEntity")
    @RequiresPermission("lc:entity:detail")
    @GetMapping("/{id}")
    public Result<LcEntityDetailVO> detail(@PathVariable String id) {
        return Result.ok(service.getEntityDetail(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建实体草稿", operationId = "createLowcodeEntityDraft")
    @RequiresPermission("lc:entity:add")
    @Auditable(operationType = "CREATE", module = "lowcode", bizType = "lc_entity",
            bizIdExpr = "#result.data.id", content = "创建实体草稿")
    @Idempotent(resourceType = "lc-draft-create", action = "CREATE", ttlSeconds = 10)
    @PostMapping
    public Result<LcEntity> create(@RequestBody SaveLcEntityRequest request) {
        return Result.ok(service.saveDraft(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新实体", operationId = "updateLowcodeEntity")
    @RequiresPermission("lc:entity:edit")
    @Auditable(operationType = "UPDATE", module = "lowcode", bizType = "lc_entity",
            bizIdExpr = "#id", content = "更新实体")
    @Idempotent(resourceType = "lc-draft-update", resourceIdExpr = "#id",
            action = "UPDATE", ttlSeconds = 10)
    @PutMapping("/{id}")
    public Result<LcEntity> update(@PathVariable String id, @RequestBody SaveLcEntityRequest request) {
        request.setId(id);
        return Result.ok(service.saveDraft(request), TraceContext.getTraceId());
    }

    @Operation(summary = "发布实体版本", operationId = "publishLowcodeEntity")
    @RequiresPermission("lc:entity:publish")
    @Auditable(operationType = "PUBLISH", module = "lowcode", bizType = "lc_entity",
            bizIdExpr = "#id", content = "发布实体版本")
    @Idempotent(resourceType = "lc-publish", resourceIdExpr = "#id",
            action = "PUBLISH", ttlSeconds = 10)
    @PostMapping("/{id}/publish")
    public Result<LcEntity> publish(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.publish(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "禁用实体", operationId = "disableLowcodeEntity")
    @RequiresPermission("lc:entity:delete")
    @Auditable(operationType = "DISABLE", module = "lowcode", bizType = "lc_entity",
            bizIdExpr = "#id", content = "禁用实体")
    @PostMapping("/{id}/disable")
    public Result<LcEntity> disable(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.disable(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "删除实体", operationId = "deleteLowcodeEntity")
    @RequiresPermission("lc:entity:delete")
    @Auditable(operationType = "DELETE", module = "lowcode", bizType = "lc_entity",
            bizIdExpr = "#id", content = "删除实体")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
