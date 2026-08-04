package com.yutong.lowcode.generator.controller;

import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.idempotency.Idempotent;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.lowcode.meta.domain.LcGeneratorTask;
import com.yutong.lowcode.meta.dto.CreateGeneratorTaskRequest;
import com.yutong.lowcode.generator.service.GeneratorTaskApplicationService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 低代码代码生成任务接口。设计来源: 08-API契约设计、58-后端API逐接口任务清单
 */
@Tag(name = "低代码-生成任务")
@RestController
@RequestMapping("/api/v1/lowcode/generator-tasks")
public class GeneratorTaskController {

    private final GeneratorTaskApplicationService service;

    public GeneratorTaskController(GeneratorTaskApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询生成任务", operationId = "listLowcodeGeneratorTasks")
    @RequiresPermission("lc:generator-task:list")
    @GetMapping
    public Result<PageResult<LcGeneratorTask>> page(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) String taskNo,
                                                    @RequestParam(required = false) String status) {
        return Result.ok(service.pageTasks(PageRequest.of(page, size), taskNo, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "生成任务详情", operationId = "getLowcodeGeneratorTask")
    @RequiresPermission("lc:generator-task:detail")
    @GetMapping("/{id}")
    public Result<LcGeneratorTask> detail(@PathVariable String id) {
        return Result.ok(service.getTask(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建生成任务", operationId = "createLowcodeGeneratorTask")
    @RequiresPermission("lc:generator-task:add")
    @Auditable(operationType = "CREATE", module = "lowcode", bizType = "lc_generator_task",
            bizIdExpr = "#result.data.id", content = "创建生成任务")
    @Idempotent(resourceType = "async-task", action = "CREATE", ttlSeconds = 10)
    @PostMapping
    public Result<LcGeneratorTask> create(@RequestBody CreateGeneratorTaskRequest request) {
        return Result.ok(service.createTask(request.getEntityId(), request.getPageId(),
                request.getTemplateVersion(), request.getTargetScope()), TraceContext.getTraceId());
    }

    @Hidden
    @Operation(summary = "执行生成任务", operationId = "runLowcodeGeneratorTask")
    @RequiresPermission("lc:generator-task:run")
    @Auditable(operationType = "RUN", module = "lowcode", bizType = "lc_generator_task",
            bizIdExpr = "#id", content = "执行生成任务")
    @Idempotent(resourceType = "async-task", resourceIdExpr = "#id",
            action = "RUN", ttlSeconds = 10)
    @PostMapping("/{id}/run")
    public Result<LcGeneratorTask> run(@PathVariable String id) {
        return Result.ok(service.runTask(id), TraceContext.getTraceId());
    }

    @Hidden
    @Operation(summary = "取消生成任务", operationId = "cancelLowcodeGeneratorTask")
    @RequiresPermission("lc:generator-task:delete")
    @Auditable(operationType = "CANCEL", module = "lowcode", bizType = "lc_generator_task",
            bizIdExpr = "#id", content = "取消生成任务")
    @PostMapping("/{id}/cancel")
    public Result<LcGeneratorTask> cancel(@PathVariable String id) {
        return Result.ok(service.cancel(id), TraceContext.getTraceId());
    }
}
