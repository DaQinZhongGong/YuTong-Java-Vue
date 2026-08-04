package com.yutong.sample.extsync.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.extsync.domain.ExtSyncError;
import com.yutong.sample.extsync.domain.ExtSyncRecord;
import com.yutong.sample.extsync.domain.ExtSyncTask;
import com.yutong.sample.extsync.dto.ExtSyncStatsVO;
import com.yutong.sample.extsync.dto.SaveExtSyncTaskRequest;
import com.yutong.sample.extsync.dto.TriggerSyncRequest;
import com.yutong.sample.extsync.service.ExtSyncTaskApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 同步任务 Controller。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>提供同步任务 CRUD + 手动触发同步 + 同步记录查询 + 错误队列管理 + 同步监控统计 API。
 *
 * <p>核心 6 项能力 API 映射:
 * <ul>
 *   <li>HTTP Client + 签名: POST /tasks/{id}/trigger → ExtSyncExecutor.execute()</li>
 *   <li>失败重试 + 死信: POST /errors/{id}/retry + POST /errors/{id}/resolve</li>
 *   <li>幂等写入: ExtSyncExecutor.writeBusinessRecord + (task_id, business_key) 唯一索引</li>
 *   <li>同步监控: GET /stats + GET /records + GET /errors</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ext")
@Tag(name = "ExtSync", description = "外部接口同步-任务/记录/错误队列")
public class ExtSyncTaskController {

    private final ExtSyncTaskApplicationService taskService;

    public ExtSyncTaskController(ExtSyncTaskApplicationService taskService) {
        this.taskService = taskService;
    }

    // ==================== 同步任务 ====================

    @GetMapping("/tasks")
    @Operation(summary = "分页查询同步任务", operationId = "pageExtSyncTasks")
    @RequiresPermission("biz:ext-sync:task:list")
    public Result<Page<ExtSyncTask>> pageTasks(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String taskCode,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String systemId,
            @RequestParam(required = false) String status) {
        Page<ExtSyncTask> page = taskService.pageTasks(pageNo, pageSize, taskCode, taskName, systemId, status);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "查询同步任务详情", operationId = "getExtSyncTask")
    @RequiresPermission("biz:ext-sync:task:detail")
    public Result<ExtSyncTask> getTask(@PathVariable String id) {
        return Result.ok(taskService.getTask(id), TraceContext.getTraceId());
    }

    @PostMapping("/tasks")
    @Operation(summary = "创建同步任务", operationId = "createExtSyncTask")
    @RequiresPermission("biz:ext-sync:task:create")
    @Auditable(bizType = "ext-task", module = "sample", bizIdExpr = "#result.data.id", operationType = "CREATE")
    public Result<ExtSyncTask> createTask(@Valid @RequestBody SaveExtSyncTaskRequest request) {
        return Result.ok(taskService.createTask(request), TraceContext.getTraceId());
    }

    @PutMapping("/tasks/{id}")
    @Operation(summary = "更新同步任务", operationId = "updateExtSyncTask")
    @RequiresPermission("biz:ext-sync:task:create")
    @Auditable(bizType = "ext-task", module = "sample", bizIdExpr = "#id", operationType = "UPDATE")
    public Result<ExtSyncTask> updateTask(@PathVariable String id,
                                          @Valid @RequestBody SaveExtSyncTaskRequest request) {
        return Result.ok(taskService.updateTask(id, request), TraceContext.getTraceId());
    }

    @DeleteMapping("/tasks/{id}")
    @Operation(summary = "删除同步任务", operationId = "deleteExtSyncTask")
    @RequiresPermission("biz:ext-sync:task:list")
    @Auditable(bizType = "ext-task", module = "sample", bizIdExpr = "#id", operationType = "DELETE")
    public Result<Void> deleteTask(@PathVariable String id) {
        taskService.deleteTask(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @PostMapping("/tasks/{id}/trigger")
    @Operation(summary = "手动触发同步任务 (HTTP Client + 签名 + 重试 + 幂等 + 错误队列)",
            operationId = "triggerExtSyncTask")
    @RequiresPermission("biz:ext-sync:task:retry")
    @Auditable(bizType = "ext-sync", module = "sample", bizIdExpr = "#result.data.id", operationType = "TRIGGER")
    public Result<ExtSyncRecord> triggerSync(@PathVariable String id,
                                             @RequestBody(required = false) TriggerSyncRequest request) {
        return Result.ok(taskService.triggerSync(id, request), TraceContext.getTraceId());
    }

    // ==================== 同步记录 ====================

    @GetMapping("/records")
    @Operation(summary = "分页查询同步记录 (同步监控)", operationId = "pageExtSyncRecords")
    @RequiresPermission("biz:ext-sync:task:list")
    public Result<Page<ExtSyncRecord>> pageRecords(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String recordNo) {
        Page<ExtSyncRecord> page = taskService.pageRecords(pageNo, pageSize, taskId, status, recordNo);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @GetMapping("/records/{id}")
    @Operation(summary = "查询同步记录详情", operationId = "getExtSyncRecord")
    @RequiresPermission("biz:ext-sync:task:detail")
    public Result<ExtSyncRecord> getRecord(@PathVariable String id) {
        return Result.ok(taskService.getRecord(id), TraceContext.getTraceId());
    }

    // ==================== 错误队列 (死信) ====================

    @GetMapping("/errors")
    @Operation(summary = "分页查询错误明细 (死信队列)", operationId = "pageExtSyncErrors")
    @RequiresPermission("biz:ext-sync:task:list")
    public Result<Page<ExtSyncError>> pageErrors(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessKey) {
        Page<ExtSyncError> page = taskService.pageErrors(pageNo, pageSize, taskId, status, businessKey);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @PostMapping("/errors/{id}/retry")
    @Operation(summary = "重试单条错误记录 (PENDING/DEAD_LETTER → RETRYING → RESOLVED/DEAD_LETTER)",
            operationId = "retryExtSyncError")
    @RequiresPermission("biz:ext-sync:task:retry")
    @Auditable(bizType = "ext-error", module = "sample", bizIdExpr = "#id", operationType = "RETRY")
    public Result<ExtSyncError> retryError(@PathVariable String id) {
        return Result.ok(taskService.retryError(id), TraceContext.getTraceId());
    }

    @PostMapping("/errors/{id}/resolve")
    @Operation(summary = "手动解决错误记录 (归档)", operationId = "resolveExtSyncError")
    @RequiresPermission("biz:ext-sync:task:retry")
    @Auditable(bizType = "ext-error", module = "sample", bizIdExpr = "#id", operationType = "RESOLVE")
    public Result<ExtSyncError> resolveError(@PathVariable String id) {
        return Result.ok(taskService.resolveError(id), TraceContext.getTraceId());
    }

    // ==================== 同步监控统计 ====================

    @GetMapping("/stats")
    @Operation(summary = "同步监控统计 (系统数/任务数/近期记录/错误队列)",
            operationId = "getExtSyncStats")
    @RequiresPermission("biz:ext-sync:task:list")
    public Result<ExtSyncStatsVO> getStats() {
        return Result.ok(taskService.getStats(), TraceContext.getTraceId());
    }
}
