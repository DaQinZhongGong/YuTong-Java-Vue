package com.yutong.lowcode.meta.controller;

import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.idempotency.Idempotent;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.lowcode.meta.domain.LcPage;
import com.yutong.lowcode.meta.dto.LcPageDetailVO;
import com.yutong.lowcode.meta.dto.SaveLcPageRequest;
import com.yutong.lowcode.meta.service.LcPageApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 低代码页面接口。设计来源: 08-API契约设计、58-后端API逐接口任务清单
 */
@Tag(name = "低代码-页面")
@RestController
@RequestMapping("/api/v1/lowcode/pages")
public class LcPageController {

    private final LcPageApplicationService service;

    public LcPageController(LcPageApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询页面", operationId = "listLowcodePages")
    @RequiresPermission("lc:page:list")
    @GetMapping
    public Result<PageResult<LcPage>> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String pageCode,
                                           @RequestParam(required = false) String pageName,
                                           @RequestParam(required = false) String status) {
        return Result.ok(service.pagePages(PageRequest.of(page, size), pageCode, pageName, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "页面详情", operationId = "getLowcodePage")
    @RequiresPermission("lc:page:detail")
    @GetMapping("/{id}")
    public Result<LcPageDetailVO> detail(@PathVariable String id) {
        return Result.ok(service.getPageDetail(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建页面草稿", operationId = "createLowcodePageDraft")
    @RequiresPermission("lc:page:add")
    @Auditable(operationType = "CREATE", module = "lowcode", bizType = "lc_page",
            bizIdExpr = "#result.data.id", content = "创建页面草稿")
    @Idempotent(resourceType = "lc-draft-create", action = "CREATE", ttlSeconds = 10)
    @PostMapping
    public Result<LcPage> create(@RequestBody SaveLcPageRequest request) {
        return Result.ok(service.saveDraft(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新页面", operationId = "updateLowcodePage")
    @RequiresPermission("lc:page:edit")
    @Auditable(operationType = "UPDATE", module = "lowcode", bizType = "lc_page",
            bizIdExpr = "#id", content = "更新页面")
    @Idempotent(resourceType = "lc-draft-update", resourceIdExpr = "#id",
            action = "UPDATE", ttlSeconds = 10)
    @PutMapping("/{id}")
    public Result<LcPage> update(@PathVariable String id, @RequestBody SaveLcPageRequest request) {
        request.setId(id);
        return Result.ok(service.saveDraft(request), TraceContext.getTraceId());
    }

    @Operation(summary = "预览页面配置", operationId = "previewLowcodePage")
    @RequiresPermission("lc:page:preview")
    @PostMapping("/{id}/preview")
    public Result<LcPageDetailVO> preview(@PathVariable String id) {
        return Result.ok(service.getPageDetail(id), TraceContext.getTraceId());
    }

    @Operation(summary = "发布页面版本", operationId = "publishLowcodePage")
    @RequiresPermission("lc:page:publish")
    @Auditable(operationType = "PUBLISH", module = "lowcode", bizType = "lc_page",
            bizIdExpr = "#id", content = "发布页面版本")
    @Idempotent(resourceType = "lc-publish", resourceIdExpr = "#id",
            action = "PUBLISH", ttlSeconds = 10)
    @PostMapping("/{id}/publish")
    public Result<LcPage> publish(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.publish(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "回滚页面到草稿", operationId = "rollbackLowcodePage")
    @RequiresPermission("lc:page:rollback")
    @Auditable(operationType = "ROLLBACK", module = "lowcode", bizType = "lc_page",
            bizIdExpr = "#id", content = "回滚页面到草稿")
    @Idempotent(resourceType = "lc-publish", resourceIdExpr = "#id",
            action = "ROLLBACK", ttlSeconds = 10)
    @PostMapping("/{id}/rollback")
    public Result<LcPage> rollback(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.rollback(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "删除页面", operationId = "deleteLowcodePage")
    @RequiresPermission("lc:page:delete")
    @Auditable(operationType = "DELETE", module = "lowcode", bizType = "lc_page",
            bizIdExpr = "#id", content = "删除页面")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
