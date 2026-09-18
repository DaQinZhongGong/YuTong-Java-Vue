package com.yutong.ai.trace.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.ai.trace.domain.AiTraceNode;
import com.yutong.ai.trace.domain.AiTraceRun;
import com.yutong.ai.trace.dto.AiTraceDashboardVO;
import com.yutong.ai.trace.service.AiTraceService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 链路追踪接口。设计来源: V043 ai_trace_run/ai_trace_node — P0 平价能力
 */
@Tag(name = "AI-链路追踪")
@RestController
@RequestMapping("/api/v1/ai/trace")
public class AiTraceController {

    private final AiTraceService traceService;

    public AiTraceController(AiTraceService traceService) {
        this.traceService = traceService;
    }

    @Operation(summary = "分页查询追踪主表", operationId = "pageTraceRuns")
    @RequiresPermission("ai:trace:list")
    @GetMapping("/runs")
    public Result<PageResult<AiTraceRun>> pageRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String traceType,
            @RequestParam(required = false) String status) {
        return Result.ok(traceService.queryRuns(PageRequest.of(page, size), traceType, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询追踪主表详情", operationId = "getTraceRun")
    @RequiresPermission("ai:trace:detail")
    @GetMapping("/runs/{id}")
    public Result<AiTraceRun> getRun(@PathVariable String id) {
        return Result.ok(traceService.getRun(id), TraceContext.getTraceId());
    }

    @Operation(summary = "查询追踪节点列表", operationId = "listTraceNodes")
    @RequiresPermission("ai:trace:detail")
    @GetMapping("/runs/{runId}/nodes")
    public Result<List<AiTraceNode>> listNodes(@PathVariable String runId) {
        return Result.ok(traceService.listNodes(runId), TraceContext.getTraceId());
    }

    @Operation(summary = "监控大盘聚合 (成功率/耗时/分类型/近期失败)", operationId = "getTraceDashboard")
    @RequiresPermission("ai:trace:list")
    @GetMapping("/dashboard")
    public Result<AiTraceDashboardVO> getDashboard(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String traceType) {
        return Result.ok(traceService.getDashboard(days, traceType), TraceContext.getTraceId());
    }

    @Operation(summary = "开始追踪（创建主表）", operationId = "startTraceRun")
    @RequiresPermission("ai:trace:add")
    @Auditable(operationType = "CREATE", module = "ai", bizType = "ai_trace_run", content = "开始链路追踪")
    @PostMapping("/runs")
    public Result<AiTraceRun> startRun(@Valid @RequestBody AiTraceRun run) {
        return Result.ok(traceService.startRun(run), TraceContext.getTraceId());
    }

    @Operation(summary = "追加追踪节点", operationId = "addTraceNode")
    @RequiresPermission("ai:trace:add")
    @Auditable(operationType = "CREATE", module = "ai", bizType = "ai_trace_node", bizIdExpr = "#runId", content = "追加追踪节点")
    @PostMapping("/runs/{runId}/nodes")
    public Result<AiTraceNode> addNode(@PathVariable String runId,
                                       @Valid @RequestBody AiTraceNode node) {
        node.setRunId(runId);
        return Result.ok(traceService.addNode(node), TraceContext.getTraceId());
    }

    @Operation(summary = "完成追踪（结束主表）", operationId = "finishTraceRun")
    @RequiresPermission("ai:trace:edit")
    @Auditable(operationType = "UPDATE", module = "ai", bizType = "ai_trace_run", bizIdExpr = "#runId", content = "完成链路追踪")
    @PostMapping("/runs/{runId}/finish")
    public Result<AiTraceRun> finishRun(@PathVariable String runId,
                                        @RequestBody(required = false) Map<String, Object> body) {
        String status = body == null ? null : (String) body.get("status");
        String outputJson = body == null ? null : (String) body.get("outputJson");
        if (outputJson == null && body != null && body.get("output_json") != null) {
            outputJson = String.valueOf(body.get("output_json"));
        }
        Integer latencyMs = null;
        if (body != null && body.get("latencyMs") != null) {
            try { latencyMs = Integer.parseInt(String.valueOf(body.get("latencyMs"))); } catch (Exception ignored) {}
        }
        String errorMessage = body == null ? null : (String) body.get("errorMessage");
        return Result.ok(traceService.finishRun(runId, status, outputJson, latencyMs, errorMessage),
                TraceContext.getTraceId());
    }
}
