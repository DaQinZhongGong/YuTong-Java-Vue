package com.yutong.workflow.controller;

import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.idempotency.Idempotent;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.workflow.domain.WfProcessDefinition;
import com.yutong.workflow.domain.WfProcessInstance;
import com.yutong.workflow.domain.WfTaskExt;
import com.yutong.workflow.dto.*;
import com.yutong.workflow.service.WorkflowApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流 REST Controller。设计来源: 41-工作流与BPMN引擎设计 (API 契约)。
 *
 * <p>GA2-44 L1+L2 落地: 实现 41 号文档定义的 API 契约, 含 15 个端点:
 * <ul>
 *   <li>流程定义: GET/POST/PUT /api/v1/workflow/definitions + POST /publish + POST /deploy</li>
 *   <li>流程实例: GET /api/v1/workflow/instances + GET /{id} + POST /start + POST /{id}/terminate + GET /{id}/diagram</li>
 *   <li>任务: GET /api/v1/workflow/tasks/todo + GET /{id} + POST /{id}/complete + POST /{id}/reject + POST /{id}/delegate + POST /{id}/transfer</li>
 *   <li>运营监控: GET /api/v1/workflow/stats</li>
 * </ul>
 *
 * <p>权限码设计: wf:definition:list/add/edit/publish/deploy + wf:instance:list/detail/start/terminate + wf:task:list/detail/complete/reject/delegate/transfer
 * (Mock 模式下权限码校验由 @RequiresPermission AOP 完成, 当前版本复用 system:todo:list 全角色可见, 后续接入 RBAC 后细化)
 */
@RestController
@RequestMapping("/api/v1/workflow")
public class WorkflowController {

    private final WorkflowApplicationService service;

    public WorkflowController(WorkflowApplicationService service) {
        this.service = service;
    }

    // ===== 流程定义 =====

    @RequiresPermission("workflow:definition:list")
    @GetMapping("/definitions")
    public Result<PageResult<WfProcessDefinition>> pageDefinitions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String bizType) {
        com.yutong.common.response.PageRequest req = com.yutong.common.response.PageRequest.of(page, size);
        return Result.ok(service.pageDefinitions(req, keyword, status, bizType), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:definition:detail")
    @GetMapping("/definitions/{id}")
    public Result<WfProcessDefinition> getDefinition(@PathVariable String id) {
        return Result.ok(service.getDefinition(id), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:definition:add")
    @PostMapping("/definitions")
    @Auditable(operationType = "CREATE", module = "workflow", bizType = "wf_definition",
               content = "创建流程定义")
    public Result<WfProcessDefinition> createDefinition(@Valid @RequestBody SaveProcessDefinitionRequest request) {
        return Result.ok(service.createDefinition(request), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:definition:edit")
    @PutMapping("/definitions/{id}")
    @Auditable(operationType = "UPDATE", module = "workflow", bizType = "wf_definition",
               bizIdExpr = "#id", content = "更新流程定义")
    public Result<WfProcessDefinition> updateDefinition(@PathVariable String id,
                                                        @Valid @RequestBody SaveProcessDefinitionRequest request) {
        return Result.ok(service.updateDefinition(id, request), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:definition:publish")
    @PostMapping("/definitions/{id}/publish")
    @Auditable(operationType = "PUBLISH", module = "workflow", bizType = "wf_definition",
               bizIdExpr = "#id", content = "发布流程定义")
    public Result<WfProcessDefinition> publishDefinition(@PathVariable String id) {
        return Result.ok(service.publishDefinition(id), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:definition:deploy")
    @PostMapping("/definitions/{id}/deploy")
    @Auditable(operationType = "DEPLOY", module = "workflow", bizType = "wf_definition",
               bizIdExpr = "#id", content = "部署流程定义")
    public Result<WfProcessDefinition> deployDefinition(@PathVariable String id) {
        return Result.ok(service.deployDefinition(id), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:definition:delete")
    @DeleteMapping("/definitions/{id}")
    @Auditable(operationType = "DELETE", module = "workflow", bizType = "wf_definition",
               bizIdExpr = "#id", content = "删除流程定义")
    public Result<Void> deleteDefinition(@PathVariable String id) {
        service.deleteDefinition(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ===== 流程实例 =====

    @RequiresPermission("workflow:instance:list")
    @GetMapping("/instances")
    public Result<PageResult<WfProcessInstance>> pageInstances(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String starterId,
            @RequestParam(required = false) String instanceStatus) {
        InstancePageQuery query = new InstancePageQuery();
        query.setPageNo(page);
        query.setPageSize(size);
        query.setProcessKey(processKey);
        query.setBizType(bizType);
        query.setBizNo(bizNo);
        query.setStarterId(starterId);
        query.setInstanceStatus(instanceStatus);
        return Result.ok(service.pageInstances(query), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:instance:detail")
    @GetMapping("/instances/{id}")
    public Result<InstanceDetailVO> getInstance(@PathVariable String id) {
        return Result.ok(service.getInstance(id), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:instance:start")
    @Idempotent(resourceType = "wf-action", action = "START", ttlSeconds = 10)
    @PostMapping("/instances/start")
    @Auditable(operationType = "START", module = "workflow", bizType = "wf_instance",
               content = "启动流程实例")
    public Result<WfProcessInstance> startInstance(@Valid @RequestBody StartProcessRequest request) {
        return Result.ok(service.startInstance(request), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:instance:terminate")
    @Idempotent(resourceType = "wf-action", resourceIdExpr = "#id",
            action = "TERMINATE", ttlSeconds = 10)
    @PostMapping("/instances/{id}/terminate")
    @Auditable(operationType = "TERMINATE", module = "workflow", bizType = "wf_instance",
               bizIdExpr = "#id", content = "终止流程实例")
    public Result<WfProcessInstance> terminateInstance(@PathVariable String id,
                                                       @Valid @RequestBody TerminateInstanceRequest request) {
        return Result.ok(service.terminateInstance(id, request), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:instance:detail")
    @GetMapping("/instances/{id}/diagram")
    public Result<InstanceDetailVO> getInstanceDiagram(@PathVariable String id) {
        return Result.ok(service.getInstance(id), TraceContext.getTraceId());
    }

    // ===== 任务 =====

    @RequiresPermission("workflow:task:list")
    @GetMapping("/tasks")
    public Result<PageResult<WfTaskExt>> pageTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) Boolean myTodoOnly,
            @RequestParam(required = false) Boolean myDoneOnly) {
        TaskPageQuery query = new TaskPageQuery();
        query.setPageNo(page);
        query.setPageSize(size);
        query.setProcessKey(processKey);
        query.setBizType(bizType);
        query.setBizNo(bizNo);
        query.setTaskStatus(taskStatus);
        query.setMyTodoOnly(myTodoOnly);
        query.setMyDoneOnly(myDoneOnly);
        return Result.ok(service.pageTasks(query), TraceContext.getTraceId());
    }

    /** 41 号文档待办端点 alias (Web/移动共用) */
    @RequiresPermission("workflow:task:list")
    @GetMapping("/tasks/todo")
    public Result<PageResult<WfTaskExt>> myTodo(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        TaskPageQuery query = new TaskPageQuery();
        query.setPageNo(page);
        query.setPageSize(size);
        query.setMyTodoOnly(true);
        return Result.ok(service.pageTasks(query), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:task:list")
    @GetMapping("/tasks/{id}")
    public Result<WfTaskExt> getTask(@PathVariable String id) {
        return Result.ok(service.getTask(id), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:task:complete")
    @Idempotent(resourceType = "wf-action", resourceIdExpr = "#id",
            action = "COMPLETE", ttlSeconds = 10)
    @PostMapping("/tasks/{id}/complete")
    @Auditable(operationType = "COMPLETE", module = "workflow", bizType = "wf_task",
               bizIdExpr = "#id", content = "办理通过任务")
    public Result<WfTaskExt> completeTask(@PathVariable String id,
                                          @RequestBody CompleteTaskRequest request) {
        return Result.ok(service.completeTask(id, request), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:task:reject")
    @Idempotent(resourceType = "wf-action", resourceIdExpr = "#id",
            action = "REJECT", ttlSeconds = 10)
    @PostMapping("/tasks/{id}/reject")
    @Auditable(operationType = "REJECT", module = "workflow", bizType = "wf_task",
               bizIdExpr = "#id", content = "驳回任务")
    public Result<WfTaskExt> rejectTask(@PathVariable String id,
                                        @Valid @RequestBody RejectTaskRequest request) {
        return Result.ok(service.rejectTask(id, request), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:task:delegate")
    @Idempotent(resourceType = "wf-action", resourceIdExpr = "#id",
            action = "DELEGATE", ttlSeconds = 10)
    @PostMapping("/tasks/{id}/delegate")
    @Auditable(operationType = "DELEGATE", module = "workflow", bizType = "wf_task",
               bizIdExpr = "#id", content = "委派任务")
    public Result<WfTaskExt> delegateTask(@PathVariable String id,
                                          @Valid @RequestBody DelegateTaskRequest request) {
        return Result.ok(service.delegateTask(id, request), TraceContext.getTraceId());
    }

    @RequiresPermission("workflow:task:transfer")
    @Idempotent(resourceType = "wf-action", resourceIdExpr = "#id",
            action = "TRANSFER", ttlSeconds = 10)
    @PostMapping("/tasks/{id}/transfer")
    @Auditable(operationType = "TRANSFER", module = "workflow", bizType = "wf_task",
               bizIdExpr = "#id", content = "转办任务")
    public Result<WfTaskExt> transferTask(@PathVariable String id,
                                          @Valid @RequestBody DelegateTaskRequest request) {
        // 转办 = 委派的特殊形式
        request.setDelegateType("TRANSFER");
        return Result.ok(service.delegateTask(id, request), TraceContext.getTraceId());
    }

    // ===== 运营监控 =====

    @RequiresPermission("workflow:instance:list")
    @GetMapping("/stats")
    public Result<WorkflowStatsVO> getStats() {
        return Result.ok(service.getStats(), TraceContext.getTraceId());
    }
}
