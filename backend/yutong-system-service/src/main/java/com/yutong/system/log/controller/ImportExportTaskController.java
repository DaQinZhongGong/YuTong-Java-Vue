package com.yutong.system.log.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.system.log.domain.SysImportExportTask;
import com.yutong.system.log.service.ImportExportTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 导入导出任务接口。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 */
@Tag(name = "导入导出任务管理")
@RestController
@RequestMapping("/api/v1/import-export-tasks")
public class ImportExportTaskController {

    private final ImportExportTaskService importExportTaskService;

    public ImportExportTaskController(ImportExportTaskService importExportTaskService) {
        this.importExportTaskService = importExportTaskService;
    }

    @Operation(summary = "分页查询导入导出任务", operationId = "listImportExportTasks")
    @GetMapping
    public Result<PageResult<SysImportExportTask>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) String taskType,
                                                        @RequestParam(required = false) String status) {
        return Result.ok(importExportTaskService.pageTasks(PageRequest.of(page, size), taskType, status), TraceContext.getTraceId());
    }

    @Operation(summary = "查询导入导出任务详情", operationId = "getImportExportTask")
    @GetMapping("/{id}")
    public Result<SysImportExportTask> get(@PathVariable String id) {
        return Result.ok(importExportTaskService.getTask(id), TraceContext.getTraceId());
    }

    @Operation(summary = "重试导入导出任务", operationId = "retryImportExportTask")
    @RequiresPermission("system:import-export-task:retry")
    @Auditable(operationType = "RETRY", module = "system", bizType = "import_export_task",
            bizIdExpr = "#id", content = "重试导入导出任务")
    @PostMapping("/{id}/retry")
    public Result<SysImportExportTask> retry(@PathVariable String id) {
        return Result.ok(importExportTaskService.retryTask(id), TraceContext.getTraceId());
    }
}
