package com.yutong.system.message.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.message.domain.SysTodoTask;
import com.yutong.system.message.service.TodoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 轻量待办接口。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 * 查询范围限定当前用户(assignee_id=当前用户)。
 */
@io.swagger.v3.oas.annotations.Hidden
@Tag(name = "待办管理")
@RestController
@RequestMapping("/api/v1/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @Operation(summary = "分页查询当前用户待办", operationId = "pageTodos")
    @GetMapping
    public Result<PageResult<SysTodoTask>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(required = false) String todoStatus) {
        return Result.ok(todoService.pageTodos(PageRequest.of(pageNo, pageSize), todoStatus), TraceContext.getTraceId());
    }

    @Operation(summary = "当前用户待办数", operationId = "getPendingTodoCount")
    @GetMapping("/pending-count")
    public Result<Long> getPendingCount() {
        return Result.ok(todoService.getPendingCount(), TraceContext.getTraceId());
    }

    @Operation(summary = "完成待办", operationId = "completeTodo")
    @RequiresPermission("system:todo:complete")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable String id) {
        todoService.completeTodo(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
