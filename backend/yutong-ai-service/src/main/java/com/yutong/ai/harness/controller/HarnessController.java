package com.yutong.ai.harness.controller;

import com.yutong.ai.harness.domain.HarnessApproval;
import com.yutong.ai.harness.domain.HarnessEvent;
import com.yutong.ai.harness.domain.HarnessPlan;
import com.yutong.ai.harness.domain.HarnessRun;
import com.yutong.ai.harness.domain.HarnessSession;
import com.yutong.ai.harness.dto.ApprovePlanRequest;
import com.yutong.ai.harness.dto.CreateRunRequest;
import com.yutong.ai.harness.dto.CreateSessionRequest;
import com.yutong.ai.harness.dto.PinSessionRequest;
import com.yutong.ai.harness.dto.QueueInputRequest;
import com.yutong.ai.harness.dto.ResolveApprovalRequest;
import com.yutong.ai.harness.enums.RunStatus;
import com.yutong.ai.harness.service.HarnessService;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coding Harness REST + SSE。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.1
 */
@Tag(name = "AI-CodingHarness")
@RestController
@RequestMapping("/api/v1/coding/harness")
public class HarnessController {

    private static final Logger log = LoggerFactory.getLogger(HarnessController.class);
    private static final long SSE_TIMEOUT_MS = 300_000L;
    private static final long SSE_POLL_MS = 400L;

    private final HarnessService harnessService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "harness-sse");
        t.setDaemon(true);
        return t;
    });

    public HarnessController(HarnessService harnessService) {
        this.harnessService = harnessService;
    }

    // ===== Sessions =====

    @Operation(summary = "创建会话（幂等）", operationId = "createHarnessSession")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/sessions")
    public Result<HarnessSession> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return Result.ok(harnessService.createSession(request), TraceContext.getTraceId());
    }

    @Operation(summary = "会话列表", operationId = "listHarnessSessions")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/sessions")
    public Result<List<HarnessSession>> listSessions(
            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return Result.ok(harnessService.listSessions(includeDeleted), TraceContext.getTraceId());
    }

    @Operation(summary = "会话详情", operationId = "getHarnessSession")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/sessions/{id}")
    public Result<HarnessSession> getSession(@PathVariable String id) {
        return Result.ok(harnessService.getSession(id), TraceContext.getTraceId());
    }

    @Operation(summary = "置顶/取消置顶", operationId = "pinHarnessSession")
    @RequiresPermission("ai:assistant:use")
    @PutMapping("/sessions/{id}/pin")
    public Result<HarnessSession> pinSession(@PathVariable String id,
                                             @RequestBody(required = false) PinSessionRequest request) {
        boolean pinned = request == null || request.isPinned();
        return Result.ok(harnessService.pin(id, pinned), TraceContext.getTraceId());
    }

    @Operation(summary = "软删会话", operationId = "deleteHarnessSession")
    @RequiresPermission("ai:assistant:use")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable String id) {
        harnessService.softDelete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "恢复会话", operationId = "restoreHarnessSession")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/sessions/{id}/restore")
    public Result<HarnessSession> restoreSession(@PathVariable String id) {
        return Result.ok(harnessService.restore(id), TraceContext.getTraceId());
    }

    // ===== Runs =====

    @Operation(summary = "创建 Run（幂等）", operationId = "createHarnessRun")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/sessions/{id}/runs")
    public Result<HarnessRun> createRun(@PathVariable String id,
                                        @Valid @RequestBody CreateRunRequest request) {
        return Result.ok(harnessService.createRun(id, request), TraceContext.getTraceId());
    }

    @Operation(summary = "Run 列表", operationId = "listHarnessRuns")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/sessions/{id}/runs")
    public Result<List<HarnessRun>> listRuns(@PathVariable String id) {
        return Result.ok(harnessService.listRuns(id), TraceContext.getTraceId());
    }

    @Operation(summary = "Run 详情", operationId = "getHarnessRun")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/sessions/{id}/runs/{runId}")
    public Result<HarnessRun> getRun(@PathVariable String id, @PathVariable String runId) {
        return Result.ok(harnessService.getRun(id, runId), TraceContext.getTraceId());
    }

    @Operation(summary = "历史事件（afterSequence 游标）", operationId = "listHarnessRunEvents")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/sessions/{id}/runs/{runId}/events")
    public Result<List<HarnessEvent>> listEvents(@PathVariable String id,
                                                 @PathVariable String runId,
                                                 @RequestParam(defaultValue = "0") long afterSequence,
                                                 @RequestParam(defaultValue = "200") int limit) {
        return Result.ok(harnessService.listEvents(id, runId, afterSequence, limit),
                TraceContext.getTraceId());
    }

    @Operation(summary = "事件 SSE 流（Last-Event-ID / afterSequence）",
            operationId = "streamHarnessRunEvents")
    @RequiresPermission("ai:assistant:use")
    @GetMapping(value = "/sessions/{id}/runs/{runId}/events/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable String id,
                                   @PathVariable String runId,
                                   @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                                   @RequestParam(required = false) Long afterSequence) {
        // 校验归属
        harnessService.getRun(id, runId);

        long cursor = 0L;
        if (afterSequence != null && afterSequence > 0) {
            cursor = afterSequence;
        } else if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                cursor = Long.parseLong(lastEventId.trim());
            } catch (NumberFormatException ignored) {
                // Last-Event-ID 可能是 event id 字符串，忽略并从 0 开始
            }
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        final long startCursor = cursor;
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        String username = CurrentUserContext.getUsername();
        final long finalCursor = cursor;
        final java.util.concurrent.atomic.AtomicBoolean clientClosed =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        emitter.onTimeout(() -> {
            clientClosed.set(true);
            emitter.complete();
        });
        emitter.onCompletion(() -> clientClosed.set(true));
        emitter.onError(e -> clientClosed.set(true));
        sseExecutor.execute(() -> {
            try {
                CurrentUserContext.set(userId, tenantId, username);
                long seq = finalCursor;
                long deadline = System.currentTimeMillis() + SSE_TIMEOUT_MS - 2_000L;
                while (!clientClosed.get() && System.currentTimeMillis() < deadline) {
                    HarnessRun run = harnessService.getRun(id, runId);
                    List<HarnessEvent> batch = harnessService.listEvents(id, runId, seq, 100);
                    for (HarnessEvent event : batch) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("id", event.getId());
                        data.put("sequenceNo", event.getSequenceNo());
                        data.put("type", event.getEventType());
                        data.put("payload", event.getPayloadJson());
                        data.put("stepId", event.getStepId());
                        data.put("toolCallId", event.getToolCallId());
                        data.put("approvalId", event.getApprovalId());
                        data.put("createdTime", event.getCreatedTime() == null
                                ? null : event.getCreatedTime().toString());
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(event.getSequenceNo()))
                                .name(event.getEventType())
                                .data(data));
                        seq = event.getSequenceNo();
                    }
                    RunStatus status = run.statusEnum();
                    if (status != null && status.isTerminal() && batch.isEmpty()) {
                        emitter.send(SseEmitter.event().name("done")
                                .data(Map.of("status", status.name(), "lastSequence", seq)));
                        emitter.complete();
                        return;
                    }
                    if (clientClosed.get()) {
                        return;
                    }
                    Thread.sleep(SSE_POLL_MS);
                }
                emitter.send(SseEmitter.event().name("done")
                        .data(Map.of("status", "TIMEOUT", "lastSequence", seq)));
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (Exception e) {
                log.warn("harness sse stream error runId={} from={}", runId, startCursor, e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("error", e.getMessage() == null ? "stream error" : e.getMessage())));
                } catch (Exception ignored) {
                    // ignore
                }
                emitter.completeWithError(e);
            } finally {
                CurrentUserContext.clear();
            }
        });
        return emitter;
    }

    // ===== Inputs / Cancel =====

    @Operation(summary = "注入 STEER/FOLLOW_UP 输入", operationId = "queueHarnessRunInput")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/sessions/{id}/runs/{runId}/inputs")
    public Result<HarnessRun> queueInput(@PathVariable String id,
                                         @PathVariable String runId,
                                         @Valid @RequestBody QueueInputRequest request) {
        return Result.ok(harnessService.queueInput(id, runId, request), TraceContext.getTraceId());
    }

    @Operation(summary = "请求取消 Run", operationId = "cancelHarnessRun")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/sessions/{id}/runs/{runId}/cancel")
    public Result<HarnessRun> cancelRun(@PathVariable String id, @PathVariable String runId) {
        return Result.ok(harnessService.requestCancel(id, runId), TraceContext.getTraceId());
    }

    // ===== Approvals / Plan =====

    @Operation(summary = "审批工具调用", operationId = "resolveHarnessApproval")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/approvals/{id}/resolve")
    public Result<HarnessApproval> resolveApproval(@PathVariable String id,
                                                   @Valid @RequestBody ResolveApprovalRequest request) {
        return Result.ok(harnessService.resolveApproval(
                id, request.getDecision(), request.getExpectedRevision(),
                request.getArgumentsSha256(), request.getDecisionId(), request.getNote()),
                TraceContext.getTraceId());
    }

    @Operation(summary = "claim 审批（一次性 CONSUMED）并继续执行",
            operationId = "claimHarnessApproval")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/approvals/{id}/claim")
    public Result<HarnessApproval> claimApproval(@PathVariable String id,
                                                 @RequestParam String sessionId,
                                                 @RequestParam String runId) {
        return Result.ok(harnessService.claimApproval(sessionId, runId, id), TraceContext.getTraceId());
    }

    @Operation(summary = "审批计划", operationId = "approveHarnessPlan")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/plan/approve")
    public Result<HarnessPlan> approvePlan(@Valid @RequestBody ApprovePlanRequest request) {
        return Result.ok(harnessService.approvePlan(
                request.getPlanId(), request.getExpectedRevision(),
                request.getExpectedHash(), request.getIdempotencyKey()),
                TraceContext.getTraceId());
    }
}
