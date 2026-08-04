package com.yutong.system.log.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.domain.SysOperationLog;
import com.yutong.system.log.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志接口。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 */
@Tag(name = "操作日志管理")
@RestController
@RequestMapping("/api/v1/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Operation(summary = "分页查询操作日志", operationId = "listOperationLogs")
    @RequiresPermission("system:operation-log:list")
    @GetMapping
    public Result<PageResult<SysOperationLog>> list(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) String operatorId,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String result,
                                                    @RequestParam(required = false) String bizType,
                                                    @RequestParam(required = false) String bizId) {
        return Result.ok(operationLogService.pageLogs(
                PageRequest.of(page, size), operatorId, keyword, result, bizType, bizId), TraceContext.getTraceId());
    }

    @Operation(summary = "查询操作日志详情", operationId = "getOperationLog")
    @RequiresPermission("system:operation-log:list")
    @GetMapping("/{id}")
    public Result<SysOperationLog> get(@PathVariable String id) {
        return Result.ok(operationLogService.getLog(id), TraceContext.getTraceId());
    }
}
