package com.yutong.sample.payment.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.payment.domain.PayCallbackLog;
import com.yutong.sample.payment.domain.PayOrder;
import com.yutong.sample.payment.domain.PayReconciliation;
import com.yutong.sample.payment.domain.PayRefundOrder;
import com.yutong.sample.payment.dto.CallbackRequest;
import com.yutong.sample.payment.dto.CreateOrderRequest;
import com.yutong.sample.payment.dto.PaymentStatsVO;
import com.yutong.sample.payment.dto.ReconciliationImportRequest;
import com.yutong.sample.payment.dto.RefundRequest;
import com.yutong.sample.payment.service.PaymentOpsApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 支付订单 Controller。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 *
 * <p>核心 6 项能力 API 映射:
 * <ul>
 *   <li>支付单状态机: POST /orders (create, PENDING) + POST /orders/{id}/cancel (CANCELLED) + POST /orders/{id}/close (CLOSED)</li>
 *   <li>第三方回调验签: POST /callbacks (handleCallback, HMAC-SHA256)</li>
 *   <li>回调幂等: POST /callbacks (idempotency_key 唯一索引兜底)</li>
 *   <li>对账文件导入: POST /reconciliations/import</li>
 *   <li>金额精度: 全程 Long (分), 1 元 = 100 分</li>
 *   <li>安全审计: @Auditable AOP 写入 sys_operation_log</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/payment")
@Tag(name = "PaymentOps", description = "支付订单-状态机/回调验签/幂等/对账/退款")
public class PaymentOpsController {

    private final PaymentOpsApplicationService service;

    public PaymentOpsController(PaymentOpsApplicationService service) {
        this.service = service;
    }

    // ==================== 1. 支付单 (状态机) ====================

    @GetMapping("/orders")
    @Operation(summary = "分页查询支付单", operationId = "pagePayOrders")
    @RequiresPermission("biz:payment:list")
    public Result<Page<PayOrder>> pageOrders(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType) {
        Page<PayOrder> page = service.pageOrders(pageNo, pageSize, orderNo, channel, status, bizType);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "查询支付单详情", operationId = "getPayOrder")
    @RequiresPermission("biz:payment:detail")
    public Result<PayOrder> getOrder(@PathVariable String id) {
        return Result.ok(service.getOrder(id), TraceContext.getTraceId());
    }

    @PostMapping("/orders")
    @Operation(summary = "创建支付单 (状态机 PENDING, 幂等键防重复)", operationId = "createPayOrder")
    @RequiresPermission("biz:payment:create")
    @Auditable(bizType = "payment_order", module = "sample", bizIdExpr = "#result.data.id",
            operationType = "CREATE", content = "创建支付单", recordResult = true)
    public Result<PayOrder> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return Result.ok(service.createOrder(request), TraceContext.getTraceId());
    }

    @PostMapping("/orders/{id}/cancel")
    @Operation(summary = "取消支付 (PENDING → CANCELLED)", operationId = "cancelPayOrder")
    @RequiresPermission("biz:payment:create")
    @Auditable(bizType = "payment_order", module = "sample", bizIdExpr = "#id",
            operationType = "CANCEL", content = "取消支付单")
    public Result<PayOrder> cancelOrder(@PathVariable String id) {
        return Result.ok(service.cancelOrder(id), TraceContext.getTraceId());
    }

    @PostMapping("/orders/{id}/close")
    @Operation(summary = "关闭订单 (PAID/REFUNDED → CLOSED, 归档)", operationId = "closePayOrder")
    @RequiresPermission("biz:payment:create")
    @Auditable(bizType = "payment_order", module = "sample", bizIdExpr = "#id",
            operationType = "CLOSE", content = "关闭支付单")
    public Result<PayOrder> closeOrder(@PathVariable String id) {
        return Result.ok(service.closeOrder(id), TraceContext.getTraceId());
    }

    // ==================== 2. 第三方回调 (验签 + 幂等) ====================

    @PostMapping("/callbacks")
    @Operation(summary = "处理第三方支付回调 (HMAC-SHA256 验签 + 幂等)", operationId = "handlePayCallback")
    @RequiresPermission("biz:payment:callback")
    @Auditable(bizType = "payment_callback", module = "sample", bizIdExpr = "#result.data.id",
            operationType = "CALLBACK", content = "处理支付回调", recordResult = true)
    public Result<PayCallbackLog> handleCallback(@Valid @RequestBody CallbackRequest request) {
        return Result.ok(service.handleCallback(request), TraceContext.getTraceId());
    }

    @GetMapping("/callbacks")
    @Operation(summary = "分页查询回调日志", operationId = "pagePayCallbacks")
    @RequiresPermission("biz:payment:list")
    public Result<Page<PayCallbackLog>> pageCallbacks(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String verifyResult,
            @RequestParam(required = false) String processResult) {
        Page<PayCallbackLog> page = service.pageCallbacks(pageNo, pageSize, orderNo, channel, verifyResult, processResult);
        return Result.ok(page, TraceContext.getTraceId());
    }

    // ==================== 3. 退款 ====================

    @PostMapping("/refunds")
    @Operation(summary = "发起退款 (PAID → REFUNDING/REFUNDED, 累计不超过原支付金额)",
            operationId = "refundPayOrder")
    @RequiresPermission("biz:payment:refund")
    @Auditable(bizType = "payment_refund", module = "sample", bizIdExpr = "#result.data.id",
            operationType = "REFUND", content = "发起退款", recordResult = true)
    public Result<PayRefundOrder> refund(@Valid @RequestBody RefundRequest request) {
        return Result.ok(service.refund(request), TraceContext.getTraceId());
    }

    @GetMapping("/refunds")
    @Operation(summary = "分页查询退款单", operationId = "pagePayRefunds")
    @RequiresPermission("biz:payment:list")
    public Result<Page<PayRefundOrder>> pageRefunds(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String refundNo,
            @RequestParam(required = false) String originalOrderId,
            @RequestParam(required = false) String status) {
        Page<PayRefundOrder> page = service.pageRefunds(pageNo, pageSize, refundNo, originalOrderId, status);
        return Result.ok(page, TraceContext.getTraceId());
    }

    // ==================== 4. 对账文件导入 ====================

    @PostMapping("/reconciliations/import")
    @Operation(summary = "对账文件导入 (PENDING → MATCHED/MISMATCHED)", operationId = "importPayReconciliation")
    @RequiresPermission("biz:payment:create")
    @Auditable(bizType = "payment_reconciliation", module = "sample", bizIdExpr = "#result.data.id",
            operationType = "IMPORT", content = "对账文件导入", recordResult = true)
    public Result<PayReconciliation> importReconciliation(@Valid @RequestBody ReconciliationImportRequest request) {
        return Result.ok(service.importReconciliation(request), TraceContext.getTraceId());
    }

    @GetMapping("/reconciliations")
    @Operation(summary = "分页查询对账记录", operationId = "pagePayReconciliations")
    @RequiresPermission("biz:payment:list")
    public Result<Page<PayReconciliation>> pageReconciliations(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        Page<PayReconciliation> page = service.pageReconciliations(pageNo, pageSize, channel, status, startDate, endDate);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @GetMapping("/reconciliations/{id}")
    @Operation(summary = "查询对账记录详情", operationId = "getPayReconciliation")
    @RequiresPermission("biz:payment:detail")
    public Result<PayReconciliation> getReconciliation(@PathVariable String id) {
        return Result.ok(service.getReconciliation(id), TraceContext.getTraceId());
    }

    // ==================== 5. 监控统计 ====================

    @GetMapping("/stats")
    @Operation(summary = "支付监控统计 (订单/状态/渠道/回调/对账)", operationId = "getPayStats")
    @RequiresPermission("biz:payment:list")
    public Result<PaymentStatsVO> getStats() {
        return Result.ok(service.getStats(), TraceContext.getTraceId());
    }
}
