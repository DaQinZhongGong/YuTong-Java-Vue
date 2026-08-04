package com.yutong.system.message.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.system.message.domain.SysMessage;
import com.yutong.system.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 站内信接口。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 * 查询范围限定当前用户(receiver_id=当前用户)。
 */
@Tag(name = "站内信管理")
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "分页查询当前用户站内信", operationId = "listMessages")
    @GetMapping
    public Result<PageResult<SysMessage>> page(@RequestParam(defaultValue = "1") int pageNo,
                                               @RequestParam(defaultValue = "20") int pageSize,
                                               @RequestParam(required = false) String readStatus) {
        return Result.ok(messageService.pageMessages(PageRequest.of(pageNo, pageSize), readStatus), TraceContext.getTraceId());
    }

    @Operation(summary = "当前用户未读消息数", operationId = "countUnreadMessages")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        return Result.ok(messageService.getUnreadCount(), TraceContext.getTraceId());
    }

    @Operation(summary = "标记消息已读", operationId = "markMessageRead")
    @RequiresPermission("system:message:read")
    @Auditable(operationType = "MARK_READ", module = "system", bizType = "message",
            bizIdExpr = "#id", content = "标记消息已读")
    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable String id) {
        messageService.markAsRead(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "当前用户全部已读", operationId = "markAllMessagesRead")
    @RequiresPermission("system:message:read")
    @Auditable(operationType = "MARK_ALL_READ", module = "system", bizType = "message",
            content = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        messageService.markAllRead();
        return Result.ok(null, TraceContext.getTraceId());
    }
}
