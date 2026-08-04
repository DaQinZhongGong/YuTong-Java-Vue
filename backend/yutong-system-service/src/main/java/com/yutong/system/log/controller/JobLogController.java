package com.yutong.system.log.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.system.log.domain.SysJobLog;
import com.yutong.system.log.service.JobLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 定时任务日志接口。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 */
@Tag(name = "任务日志管理")
@RestController
@RequestMapping("/api/v1/job-logs")
public class JobLogController {

    private final JobLogService jobLogService;

    public JobLogController(JobLogService jobLogService) {
        this.jobLogService = jobLogService;
    }

    @Operation(summary = "分页查询任务日志", operationId = "listJobLogs")
    @GetMapping
    public Result<PageResult<SysJobLog>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String jobName,
                                              @RequestParam(required = false) String status) {
        return Result.ok(jobLogService.pageLogs(PageRequest.of(page, size), jobName, status), TraceContext.getTraceId());
    }

    @Operation(summary = "查询任务日志详情", operationId = "getJobLog")
    @GetMapping("/{id}")
    public Result<SysJobLog> get(@PathVariable String id) {
        return Result.ok(jobLogService.getLog(id), TraceContext.getTraceId());
    }

    @Operation(summary = "重试任务", operationId = "retryJob")
    @RequiresPermission("system:job-log:retry")
    @Auditable(operationType = "RETRY", module = "system", bizType = "job_log",
            bizIdExpr = "#id", content = "重试任务")
    @PostMapping("/{id}/retry")
    public Result<SysJobLog> retry(@PathVariable String id) {
        return Result.ok(jobLogService.retryJob(id), TraceContext.getTraceId());
    }
}
