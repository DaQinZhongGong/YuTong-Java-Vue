package com.yutong.system.notification.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.system.notification.domain.SysMessageTemplate;
import com.yutong.system.notification.domain.SysNotificationDispatchLog;
import com.yutong.system.notification.domain.SysNotificationSubscription;
import com.yutong.system.notification.dto.*;
import com.yutong.system.notification.service.MockMobilePushAdapter;
import com.yutong.system.notification.service.NotificationOpsApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 通知运营接口。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知 + 44-实时通信与消息推送设计
 * <p>
 * GA2-40 落地: 12 个 REST 端点，覆盖 6 项核心能力（站内信+未读数+实时推送+移动订阅+模板+重试）。
 * <p>
 * 复用已有 MessageController (/api/v1/messages) 的站内信查询/已读能力，本 Controller 聚焦运营层。
 */
@Tag(name = "通知运营")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationOpsController {

    private final NotificationOpsApplicationService opsService;
    private final MockMobilePushAdapter mobilePushAdapter;

    public NotificationOpsController(NotificationOpsApplicationService opsService,
                                     MockMobilePushAdapter mobilePushAdapter) {
        this.opsService = opsService;
        this.mobilePushAdapter = mobilePushAdapter;
    }

    // ==================== 发送通知 + 统计 ====================

    @Operation(summary = "发送通知 (模板渲染 + 多渠道分发)", operationId = "sendNotification")
    @RequiresPermission("system:notification:send")
    @Auditable(operationType = "SEND", module = "system", bizType = "notification",
            content = "发送通知")
    @PostMapping("/send")
    public Result<SendNotificationVO> sendNotification(@Valid @RequestBody SendNotificationRequest request) {
        return Result.ok(opsService.sendNotification(request), TraceContext.getTraceId());
    }

    @Operation(summary = "通知运营统计 (8 项指标)", operationId = "getNotificationStats")
    @RequiresPermission("system:notification:list")
    @GetMapping("/stats")
    public Result<NotificationStatsVO> getStats() {
        return Result.ok(opsService.getStats(), TraceContext.getTraceId());
    }

    // ==================== 模板管理 ====================

    @Operation(summary = "分页查询消息模板", operationId = "pageMessageTemplates")
    @RequiresPermission("system:notification:list")
    @GetMapping("/templates")
    public Result<PageResult<SysMessageTemplate>> pageTemplates(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String msgType) {
        return Result.ok(opsService.pageTemplates(pageNo, pageSize, msgType), TraceContext.getTraceId());
    }

    @Operation(summary = "创建消息模板", operationId = "createMessageTemplate")
    @RequiresPermission("system:notification:template")
    @Auditable(operationType = "CREATE", module = "system", bizType = "notification_template",
            content = "创建消息模板")
    @PostMapping("/templates")
    public Result<SysMessageTemplate> createTemplate(@Valid @RequestBody SaveTemplateRequest request) {
        return Result.ok(opsService.createTemplate(request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除消息模板", operationId = "deleteMessageTemplate")
    @RequiresPermission("system:notification:template")
    @Auditable(operationType = "DELETE", module = "system", bizType = "notification_template",
            bizIdExpr = "#id", content = "删除消息模板")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable String id) {
        opsService.deleteTemplate(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ==================== 分发日志管理 ====================

    @Operation(summary = "分页查询分发日志", operationId = "pageDispatchLogs")
    @RequiresPermission("system:notification:list")
    @GetMapping("/dispatches")
    public Result<PageResult<SysNotificationDispatchLog>> pageDispatchLogs(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String dispatchStatus,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String receiverId) {
        DispatchLogPageQuery query = new DispatchLogPageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setDispatchStatus(dispatchStatus);
        query.setChannel(channel);
        query.setReceiverId(receiverId);
        return Result.ok(opsService.pageDispatchLogs(query), TraceContext.getTraceId());
    }

    @Operation(summary = "手动重试分发", operationId = "retryDispatch")
    @RequiresPermission("system:notification:send")
    @Auditable(operationType = "RETRY", module = "system", bizType = "notification_dispatch",
            bizIdExpr = "#id", content = "重试通知分发")
    @PostMapping("/dispatches/{id}/retry")
    public Result<SysNotificationDispatchLog> retryDispatch(@PathVariable String id) {
        return Result.ok(opsService.retryDispatch(id), TraceContext.getTraceId());
    }

    @Operation(summary = "解决死信分发 (标记为人工已处理)", operationId = "resolveDispatch")
    @RequiresPermission("system:notification:send")
    @Auditable(operationType = "RESOLVE", module = "system", bizType = "notification_dispatch",
            bizIdExpr = "#id", content = "解决死信分发")
    @PostMapping("/dispatches/{id}/resolve")
    public Result<SysNotificationDispatchLog> resolveDispatch(@PathVariable String id) {
        return Result.ok(opsService.resolveDispatch(id), TraceContext.getTraceId());
    }

    // ==================== 订阅管理 ====================

    @Operation(summary = "分页查询当前用户移动端订阅", operationId = "pageSubscriptions")
    @RequiresPermission("system:notification:list")
    @GetMapping("/subscriptions")
    public Result<PageResult<SysNotificationSubscription>> pageSubscriptions(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String topic) {
        return Result.ok(opsService.pageSubscriptions(pageNo, pageSize, topic), TraceContext.getTraceId());
    }

    @Operation(summary = "创建/更新移动端订阅", operationId = "saveSubscription")
    @RequiresPermission("system:notification:send")
    @Auditable(operationType = "SAVE", module = "system", bizType = "notification_subscription",
            content = "保存移动端订阅")
    @PostMapping("/subscriptions")
    public Result<SysNotificationSubscription> saveSubscription(@Valid @RequestBody SaveSubscriptionRequest request) {
        return Result.ok(opsService.saveSubscription(request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除移动端订阅", operationId = "deleteSubscription")
    @RequiresPermission("system:notification:send")
    @Auditable(operationType = "DELETE", module = "system", bizType = "notification_subscription",
            bizIdExpr = "#id", content = "删除移动端订阅")
    @DeleteMapping("/subscriptions/{id}")
    public Result<Void> deleteSubscription(@PathVariable String id) {
        opsService.deleteSubscription(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ==================== Mock 移动推送端点 ====================

    /**
     * Mock 移动推送端点。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知。
     * <p>
     * 用于冒烟测试验证 MockMobilePushAdapter 的推送行为：
     * - deviceToken="mock-token-fail-xxx" → 模拟失败
     * - 其他 deviceToken → 模拟成功
     * <p>
     * 注意：真实移动推送由 NotificationOpsApplicationService.sendNotification 自动触发，
     * 本端点仅供冒烟测试单独验证 MockMobilePushAdapter 的行为。
     */
    @Operation(summary = "Mock 移动推送 (冒烟测试用)", operationId = "mockMobilePush")
    @RequiresPermission("system:notification:send")
    @PostMapping("/mock/mobile-push")
    public Result<Map<String, Object>> mockMobilePush(@RequestParam String deviceToken,
                                                       @RequestParam String title,
                                                       @RequestParam(required = false) String content) {
        boolean ok = mobilePushAdapter.push(deviceToken, title, content, "mock", null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceToken", deviceToken);
        result.put("title", title);
        result.put("pushed", ok);
        return Result.ok(result, TraceContext.getTraceId());
    }
}
