package com.yutong.sample.mobile.controller;

import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.idempotency.Idempotent;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.mobile.dto.MobileRequestDetailVO;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.sample.mobile.dto.MobileTodoVO;
import com.yutong.sample.mobile.service.MobileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 移动端聚合接口。设计来源: 18-样例业务详细设计、92-Uniapp移动端逐页交互详设
 * 聚合待办列表、申请单详情、移动审核能力。
 */
@Tag(name = "移动端聚合")
@RestController
@RequestMapping("/api/v1/mobile")
public class MobileController {

    private final MobileService mobileService;

    public MobileController(MobileService mobileService) {
        this.mobileService = mobileService;
    }

    @Operation(summary = "移动端待办列表", operationId = "listMobileTodos")
    @RequiresPermission("mobile:todo:list")
    @GetMapping("/todos")
    public Result<PageResult<MobileTodoVO>> listTodos(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String todoStatus) {
        PageRequest request = PageRequest.of(pageNo, pageSize);
        return Result.ok(mobileService.pageTodos(request, keyword, todoStatus), TraceContext.getTraceId());
    }

    @Operation(summary = "移动端申请单详情", operationId = "getMobileBizRequest")
    @RequiresPermission("mobile:biz-request:detail")
    @GetMapping("/biz-requests/{id}")
    public Result<MobileRequestDetailVO> getRequestDetail(@PathVariable String id) {
        return Result.ok(mobileService.getRequestDetail(id), TraceContext.getTraceId());
    }

    @Operation(summary = "移动端审核通过", operationId = "approveMobileBizRequest")
    @RequiresPermission("mobile:biz-request:approve")
    @Auditable(operationType = "APPROVE", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "移动端审核通过")
    @Idempotent(resourceType = "wf-action", resourceIdExpr = "#id",
            action = "APPROVE", ttlSeconds = 10)
    @PostMapping("/biz-requests/{id}/approve")
    public Result<Void> approve(@PathVariable String id,
                                @RequestParam(required = false) String opinion,
                                @RequestParam(required = false) Integer version,
                                @RequestParam(required = false) String idempotencyKey) {
        mobileService.approve(id, opinion, version, idempotencyKey);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "移动端驳回", operationId = "rejectMobileBizRequest")
    @RequiresPermission("mobile:biz-request:reject")
    @Auditable(operationType = "REJECT", module = "sample", bizType = "biz_request",
            bizIdExpr = "#id", content = "移动端驳回")
    @Idempotent(resourceType = "wf-action", resourceIdExpr = "#id",
            action = "REJECT", ttlSeconds = 10)
    @PostMapping("/biz-requests/{id}/reject")
    public Result<Void> reject(@PathVariable String id,
                               @RequestParam String opinion,
                               @RequestParam(required = false) Integer version,
                               @RequestParam(required = false) String idempotencyKey) {
        mobileService.reject(id, opinion, version, idempotencyKey);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "移动端工作台", operationId = "getMobileWorkbench")
    @RequiresPermission("mobile:workbench:view")
    @GetMapping("/workbench")
    public Result<Map<String, Object>> getWorkbench() {
        return Result.ok(mobileService.getWorkbenchStats(), TraceContext.getTraceId());
    }

    @Operation(summary = "移动端扫码解析", operationId = "resolveMobileScan")
    @RequiresPermission("mobile:scan:use")
    @PostMapping("/scan/resolve")
    public Result<Map<String, Object>> resolveScan(@RequestParam String code) {
        // MockMvc 环境下 @RequestParam 可能收到未解码的 URL 编码值，
        // 此处安全解码以回显原始 code (生产环境容器已解码，解码为 no-op)
        String resolved = code;
        try {
            resolved = URLDecoder.decode(code, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 解码失败保留原值
        }
        return Result.ok(Map.of("routeId", "mobile.workbench", "params", Map.of("code", resolved)),
                TraceContext.getTraceId());
    }
}
