package com.yutong.ai.aiflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.aiflow.domain.AiflowDefinition;
import com.yutong.ai.aiflow.domain.AiflowInstance;
import com.yutong.ai.aiflow.service.AiflowEngine;
import com.yutong.ai.aiflow.service.AiflowService;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AIFlow 管理接口。设计来源: Phase 5 AIFlow
 * 权限: ai:aiflow:*
 * 路径: /api/v1/aiflow/definitions + instances
 *
 * <p>融合路径说明: 本 Controller 仅编排 AIFlow DAG；
 * yutong-workflow-service 的 LightWorkflowEngine 保持 untouched，
 * 未来 human 节点可委托 workflow 实现审批闭环，当前 mock 自动通过。</p>
 */
@Tag(name = "AI-Flow编排")
@RestController
@RequestMapping("/api/v1/aiflow")
public class AiflowController {

    private static final Logger log = LoggerFactory.getLogger(AiflowController.class);

    private final AiflowService aiflowService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AiflowController(AiflowService aiflowService, ObjectMapper objectMapper) {
        this.aiflowService = aiflowService;
        this.objectMapper = objectMapper;
    }

    // ===== Definitions CRUD =====

    @Operation(summary = "分页查询 AIFlow 定义", operationId = "pageAiflowDefinitions")
    @RequiresPermission("ai:aiflow:list")
    @GetMapping("/definitions")
    public Result<PageResult<AiflowDefinition>> pageDefinitions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) String status) {
        return Result.ok(aiflowService.pageDefinitions(PageRequest.of(page, size), flowCode, status), TraceContext.getTraceId());
    }

    @Operation(summary = "查询 AIFlow 定义详情", operationId = "getAiflowDefinition")
    @RequiresPermission("ai:aiflow:detail")
    @GetMapping("/definitions/{id}")
    public Result<AiflowDefinition> getDefinition(@PathVariable String id) {
        return Result.ok(aiflowService.getDefinition(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建 AIFlow 定义 (DRAFT)", operationId = "createAiflowDefinition")
    @RequiresPermission("ai:aiflow:add")
    @PostMapping("/definitions")
    public Result<AiflowDefinition> create(@RequestBody AiflowDefinition def) {
        return Result.ok(aiflowService.saveDefinition(def), TraceContext.getTraceId());
    }

    @Operation(summary = "更新 AIFlow 定义 (仅 DRAFT)", operationId = "updateAiflowDefinition")
    @RequiresPermission("ai:aiflow:edit")
    @PutMapping("/definitions/{id}")
    public Result<AiflowDefinition> update(@PathVariable String id, @RequestBody AiflowDefinition def) {
        def.setId(id);
        return Result.ok(aiflowService.saveDefinition(def), TraceContext.getTraceId());
    }

    @Operation(summary = "发布 AIFlow 定义 (DRAFT→PUBLISHED)", operationId = "publishAiflowDefinition")
    @RequiresPermission("ai:aiflow:publish")
    @PostMapping("/definitions/{id}/publish")
    public Result<AiflowDefinition> publish(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(aiflowService.publish(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "归档 AIFlow 定义 (→ARCHIVED)", operationId = "archiveAiflowDefinition")
    @RequiresPermission("ai:aiflow:publish")
    @PostMapping("/definitions/{id}/archive")
    public Result<AiflowDefinition> archive(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(aiflowService.archive(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "删除 AIFlow 定义", operationId = "deleteAiflowDefinition")
    @RequiresPermission("ai:aiflow:delete")
    @DeleteMapping("/definitions/{id}")
    public Result<Void> delete(@PathVariable String id, @RequestParam Integer version) {
        aiflowService.deleteDefinition(id, version);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ===== Run (SSE) =====

    @Operation(summary = "执行 AIFlow (SSE 流式返回节点状态)", operationId = "runAiflow")
    @RequiresPermission("ai:aiflow:run")
    @PostMapping(value = "/definitions/{id}/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String inputJson;
        try {
            inputJson = body == null ? "{}" : objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            inputJson = "{}";
        }
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        String username = CurrentUserContext.getUsername();
        final String finalInputJson = inputJson;

        SseEmitter emitter = new SseEmitter(120_000L);
        executor.execute(() -> {
            try {
                CurrentUserContext.set(userId, tenantId, username);
                aiflowService.startInstance(id, finalInputJson, event -> {
                    try {
                        String data = objectMapper.writeValueAsString(Map.of(
                                "nodeId", event.nodeId(),
                                "type", event.type(),
                                "status", event.status(),
                                "output", event.output() != null ? event.output() : "",
                                "error", event.error() != null ? event.error() : ""
                        ));
                        emitter.send(SseEmitter.event().name("node_state").data(data));
                    } catch (Exception ex) {
                        log.warn("SSE send node_state failed", ex);
                    }
                });
                emitter.send(SseEmitter.event().name("done").data("{\"status\":\"SUCCESS\"}"));
                emitter.complete();
            } catch (Exception e) {
                log.error("aiflow run SSE failed: definitionId={}", id, e);
                try {
                    String msg = e.getMessage() == null ? "unknown" : e.getMessage().replace("\"", "'").replace("\n", " ");
                    emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + msg + "\"}"));
                } catch (Exception ex) {
                    log.warn("SSE error send failed", ex);
                }
                emitter.completeWithError(e);
            } finally {
                CurrentUserContext.clear();
            }
        });
        return emitter;
    }

    // ===== Instances =====

    @Operation(summary = "分页查询 AIFlow 实例", operationId = "pageAiflowInstances")
    @RequiresPermission("ai:aiflow:list")
    @GetMapping("/definitions/{id}/instances")
    public Result<PageResult<AiflowInstance>> pageInstances(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(aiflowService.pageInstances(id, PageRequest.of(page, size)), TraceContext.getTraceId());
    }

    @Operation(summary = "查询全部实例 (按租户)", operationId = "pageAllAiflowInstances")
    @RequiresPermission("ai:aiflow:list")
    @GetMapping("/instances")
    public Result<PageResult<AiflowInstance>> pageAllInstances(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String flowId) {
        return Result.ok(aiflowService.pageInstances(flowId, PageRequest.of(page, size)), TraceContext.getTraceId());
    }

    @Operation(summary = "查询 AIFlow 实例详情", operationId = "getAiflowInstance")
    @RequiresPermission("ai:aiflow:detail")
    @GetMapping("/instances/{instanceId}")
    public Result<AiflowInstance> getInstance(@PathVariable String instanceId) {
        return Result.ok(aiflowService.getInstance(instanceId), TraceContext.getTraceId());
    }
}
