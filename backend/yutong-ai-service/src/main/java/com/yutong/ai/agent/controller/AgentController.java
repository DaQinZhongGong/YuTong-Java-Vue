package com.yutong.ai.agent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.agent.domain.AiAgent;
import com.yutong.ai.agent.domain.AiAgentRun;
import com.yutong.ai.agent.mapper.AiAgentRunMapper;
import com.yutong.ai.agent.service.AgentMemoryService;
import com.yutong.ai.agent.service.AgentService;
import com.yutong.ai.agent.service.ReActEngine;
import com.yutong.ai.agent.service.SupervisorAgent;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent 管理接口。设计来源: Phase 4 Agent + Memory
 * permission: ai:agent:*
 */
@Tag(name = "AI-Agent管理")
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final ReActEngine reActEngine;
    private final SupervisorAgent supervisorAgent;
    private final AgentMemoryService memoryService;
    private final AiAgentRunMapper runMapper;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public AgentController(AgentService agentService, ReActEngine reActEngine,
                           SupervisorAgent supervisorAgent, AgentMemoryService memoryService,
                           AiAgentRunMapper runMapper, ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.reActEngine = reActEngine;
        this.supervisorAgent = supervisorAgent;
        this.memoryService = memoryService;
        this.runMapper = runMapper;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "分页查询 Agent", operationId = "pageAgents")
    @RequiresPermission("ai:agent:list")
    @GetMapping
    public Result<PageResult<AiAgent>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String agentCode,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String status) {
        return Result.ok(agentService.pageAgents(PageRequest.of(page, size), agentCode, agentType, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询 Agent 详情", operationId = "getAgent")
    @RequiresPermission("ai:agent:detail")
    @GetMapping("/{id}")
    public Result<AiAgent> get(@PathVariable String id) {
        return Result.ok(agentService.getAgent(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建 Agent 草稿", operationId = "createAgent")
    @RequiresPermission("ai:agent:add")
    @PostMapping
    public Result<AiAgent> create(@RequestBody AiAgent agent) {
        return Result.ok(agentService.saveAgent(agent), TraceContext.getTraceId());
    }

    @Operation(summary = "更新 Agent 草稿", operationId = "updateAgent")
    @RequiresPermission("ai:agent:edit")
    @PutMapping("/{id}")
    public Result<AiAgent> update(@PathVariable String id, @RequestBody AiAgent agent) {
        agent.setId(id);
        return Result.ok(agentService.saveAgent(agent), TraceContext.getTraceId());
    }

    @Operation(summary = "发布 Agent (DRAFT→PUBLISHED)", operationId = "publishAgent")
    @RequiresPermission("ai:agent:publish")
    @PostMapping("/{id}/publish")
    public Result<AiAgent> publish(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(agentService.publish(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "删除 Agent", operationId = "deleteAgent")
    @RequiresPermission("ai:agent:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id, @RequestParam Integer version) {
        agentService.delete(id, version);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "执行 Agent (SSE 流式返回 ReAct 步骤)", operationId = "runAgent")
    @RequiresPermission("ai:agent:run")
    @PostMapping(value = "/{id}/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String query = body != null && body.get("query") != null ? String.valueOf(body.get("query")) : "";
        String conversationId = body != null && body.get("conversationId") != null ? String.valueOf(body.get("conversationId")) : null;
        @SuppressWarnings("unchecked")
        List<String> subAgentIds = body != null && body.get("subAgentIds") instanceof List
                ? (List<String>) body.get("subAgentIds") : null;

        AiAgent agent = agentService.getAgent(id);
        // 捕获当前线程的 tenant/user 上下文，透传到异步线程
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        String username = CurrentUserContext.getUsername();

        SseEmitter emitter = new SseEmitter(120_000L);
        executor.execute(() -> {
            // 透传上下文
            try {
                CurrentUserContext.set(userId, tenantId, username);
                if (AiAgent.TYPE_SUPERVISOR.equals(agent.getAgentType()) && subAgentIds != null && !subAgentIds.isEmpty()) {
                    supervisorAgent.orchestrateSequential(agent, subAgentIds, query, conversationId, step -> {
                        sendStep(emitter, step);
                    });
                    emitter.send(SseEmitter.event().name("done").data("{\"status\":\"SUCCESS\"}"));
                } else {
                    reActEngine.execute(agent, query, conversationId, step -> sendStep(emitter, step));
                    emitter.send(SseEmitter.event().name("done").data("{\"status\":\"SUCCESS\"}"));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("agent run SSE failed: agentId={}", id, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + escape(e.getMessage()) + "\"}"));
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

    private void sendStep(SseEmitter emitter, ReActEngine.ReActStep step) {
        try {
            String data = objectMapper.writeValueAsString(Map.of(
                    "step", step.step(),
                    "type", step.type(),
                    "content", step.content() != null ? step.content() : "",
                    "tool", step.tool() != null ? step.tool() : "",
                    "observation", step.observation() != null ? step.observation() : ""
            ));
            emitter.send(SseEmitter.event().name("step").data(data));
        } catch (Exception e) {
            log.warn("SSE send step failed", e);
        }
    }

    @Operation(summary = "查询 Agent 执行记录", operationId = "listAgentRuns")
    @RequiresPermission("ai:agent:list")
    @GetMapping("/{id}/runs")
    public Result<PageResult<AiAgentRun>> runs(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String tenantId = CurrentUserContext.getTenantId();
        LambdaQueryWrapper<AiAgentRun> wrapper = new LambdaQueryWrapper<AiAgentRun>()
                .eq(AiAgentRun::getTenantId, tenantId)
                .eq(AiAgentRun::getAgentId, id)
                .orderByDesc(AiAgentRun::getCreatedTime);
        var pg = runMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
        return Result.ok(PageResult.of(pg.getRecords(), pg.getTotal(), page, size), TraceContext.getTraceId());
    }

    @Operation(summary = "获取记忆上下文 (摘要+窗口)", operationId = "getAgentMemoryContext")
    @RequiresPermission("ai:agent:detail")
    @GetMapping("/memory/{conversationId}/context")
    public Result<Map<String, Object>> memoryContext(@PathVariable String conversationId) {
        String ctx = memoryService.buildContext(conversationId);
        String summary = memoryService.maybeSummarize(conversationId);
        return Result.ok(Map.of("context", ctx, "summary", summary != null ? summary : ""), TraceContext.getTraceId());
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'").replace("\n", " ");
    }
}
