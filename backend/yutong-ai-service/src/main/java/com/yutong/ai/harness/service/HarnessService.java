package com.yutong.ai.harness.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.harness.approval.ApprovalService;
import com.yutong.ai.harness.budget.BudgetPolicy;
import com.yutong.ai.harness.budget.HarnessBudget;
import com.yutong.ai.harness.budget.HarnessUsage;
import com.yutong.ai.harness.domain.HarnessApproval;
import com.yutong.ai.harness.domain.HarnessEvent;
import com.yutong.ai.harness.domain.HarnessPlan;
import com.yutong.ai.harness.domain.HarnessRun;
import com.yutong.ai.harness.domain.HarnessSession;
import com.yutong.ai.harness.dto.CreateRunRequest;
import com.yutong.ai.harness.dto.CreateSessionRequest;
import com.yutong.ai.harness.dto.QueueInputRequest;
import com.yutong.ai.harness.enums.ApprovalPolicy;
import com.yutong.ai.harness.enums.HarnessEventType;
import com.yutong.ai.harness.enums.PermissionMode;
import com.yutong.ai.harness.enums.RunStatus;
import com.yutong.ai.harness.enums.ToolCapability;
import com.yutong.ai.harness.plan.PlanService;
import com.yutong.ai.harness.policy.ToolDecision;
import com.yutong.ai.harness.policy.ToolPolicyEngine;
import com.yutong.ai.harness.store.HarnessStore;
import com.yutong.ai.harness.store.HarnessStoreService;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Coding Harness 编排服务。Phase 1：会话/Run 生命周期 + 计划审批 + dry-run 工具循环。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.1
 */
@Service
public class HarnessService {

    private static final Logger log = LoggerFactory.getLogger(HarnessService.class);

    /** dry-run 阶段默认工具能力（只读回显） */
    private static final Set<ToolCapability> DRY_RUN_CAPABILITIES = Set.of(ToolCapability.READ);

    private final HarnessStore store;
    private final ApprovalService approvalService;
    private final PlanService planService;
    private final HarnessToolExecutor toolExecutor;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "harness-run");
        t.setDaemon(true);
        return t;
    });

    public HarnessService(HarnessStore store,
                          ApprovalService approvalService,
                          PlanService planService,
                          HarnessToolExecutor toolExecutor,
                          ObjectMapper objectMapper) {
        this.store = store;
        this.approvalService = approvalService;
        this.planService = planService;
        this.toolExecutor = toolExecutor;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ===== Session =====

    @Transactional
    public HarnessSession createSession(CreateSessionRequest request) {
        String tenantId = requireTenant();
        String userId = requireUser();
        if (request.getWorkspacePath() == null || request.getWorkspacePath().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "workspacePath 不能为空");
        }
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            var existing = store.findSessionByIdempotencyKey(tenantId, userId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        PermissionMode mode = PermissionMode.parse(request.getPermissionMode());
        ApprovalPolicy policy = ApprovalPolicy.parse(request.getApprovalPolicy());

        HarnessSession session = new HarnessSession();
        session.setId(IdGenerator.nextId());
        session.setTenantId(tenantId);
        session.setCreatedBy(userId);
        session.setUserId(userId);
        session.setTitle(request.getTitle() == null || request.getTitle().isBlank()
                ? "未命名会话" : request.getTitle());
        session.setWorkspacePath(request.getWorkspacePath());
        session.setModel(request.getModel());
        session.setPermissionMode(mode.name());
        session.setApprovalPolicy(policy.name());
        session.setThinkingLevel(normalizeThinking(request.getThinkingLevel()));
        session.setVerificationMode(normalizeVerification(request.getVerificationMode()));
        session.setIdempotencyKey(request.getIdempotencyKey());
        session.setRevision(0L);
        session.setVersion(0);
        try {
            return store.insertSession(session);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            var existing = store.findSessionByIdempotencyKey(tenantId, userId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
            throw new BusinessException(ErrorCode.SYS_IDEMPOTENCY_CONFLICT, "会话创建幂等键冲突");
        }
    }

    public List<HarnessSession> listSessions(boolean includeDeleted) {
        return store.listSessions(requireTenant(), requireUser(), includeDeleted);
    }

    public HarnessSession getSession(String id) {
        HarnessSession session = HarnessStoreService.requirePresent(
                store.findSession(requireTenant(), id), "会话", id);
        // 同租户内仅所有者可读写（list 已按 user 过滤；详情/操作补齐）
        String userId = requireUser();
        if (session.getUserId() != null && !session.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("会话不存在: " + id);
        }
        return session;
    }

    @Transactional
    public HarnessSession pin(String id, boolean pinned) {
        String tenantId = requireTenant();
        HarnessSession session = getSession(id);
        long rev = session.getRevision() == null ? 0L : session.getRevision();
        HarnessStoreService.requireUpdated(
                store.pinSession(tenantId, id, pinned ? OffsetDateTime.now() : null, rev),
                "会话", id);
        session.setPinnedAt(pinned ? OffsetDateTime.now() : null);
        session.setRevision(rev + 1);
        return session;
    }

    @Transactional
    public void softDelete(String id) {
        String tenantId = requireTenant();
        getSession(id);
        if (!store.softDeleteSession(tenantId, id)) {
            throw new ResourceNotFoundException("会话不存在: " + id);
        }
    }

    @Transactional
    public HarnessSession restore(String id) {
        String tenantId = requireTenant();
        String userId = requireUser();
        // 先做 owner 校验（含已删除会话），再 restore
        var existing = store.findSession(tenantId, id, true)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在: " + id));
        if (existing.getUserId() != null && !existing.getUserId().equals(userId)
                && !Objects.equals(existing.getCreatedBy(), userId)) {
            throw new BusinessException(ErrorCode.SYS_UNAUTHORIZED, "无权恢复该会话");
        }
        if (!store.restoreSession(tenantId, id, userId)) {
            // 未删除时 restore 可能 no-op，返回当前态
            return store.findSession(tenantId, id, true)
                    .orElseThrow(() -> new ResourceNotFoundException("会话不存在: " + id));
        }
        return store.findSession(tenantId, id, true)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在: " + id));
    }

    // ===== Run =====

    @Transactional
    public HarnessRun createRun(String sessionId, CreateRunRequest request) {
        String tenantId = requireTenant();
        String userId = requireUser();
        HarnessSession session = getSession(sessionId);
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            var existing = store.findRunByIdempotencyKey(sessionId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        // Run 权限不得突破会话天花板（fail-closed）
        PermissionMode sessionMode = PermissionMode.parse(session.getPermissionMode());
        PermissionMode requested = request.getPermissionMode() != null && !request.getPermissionMode().isBlank()
                ? PermissionMode.parse(request.getPermissionMode())
                : sessionMode;
        PermissionMode mode = clampPermissionMode(sessionMode, requested);

        HarnessBudget budget = new HarnessBudget(
                request.getMaxToolCalls(),
                request.getMaxInputTokens(),
                request.getMaxOutputTokens(),
                request.getMaxIterations(),
                request.getMaxWallTimeMs());

        HarnessRun run = new HarnessRun();
        run.setId(IdGenerator.nextId());
        run.setTenantId(tenantId);
        run.setCreatedBy(userId);
        run.setSessionId(sessionId);
        run.setUserId(userId);
        run.setStatus(RunStatus.QUEUED.name());
        run.setRequirement(request.getRequirement());
        run.setPermissionMode(mode.name());
        run.setPermissionRevision(session.getRevision() == null ? 0L : session.getRevision());
        run.setBudgetJson(toJson(budget));
        run.setUsageJson(toJson(HarnessUsage.empty()));
        run.setIteration(0);
        run.setToolCallCount(0);
        run.setCancelRequested(false);
        run.setIdempotencyKey(request.getIdempotencyKey());
        run.setRevision(0L);
        run.setVersion(0);
        try {
            store.insertRun(run);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            var existing = store.findRunByIdempotencyKey(sessionId, request.getIdempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
            throw new BusinessException(ErrorCode.SYS_IDEMPOTENCY_CONFLICT, "Run 创建幂等键冲突");
        }

        // 更新会话 activeRunId（乐观锁失败必须上抛）
        long sRev = session.getRevision() == null ? 0L : session.getRevision();
        session.setActiveRunId(run.getId());
        HarnessStoreService.requireUpdated(store.updateSession(session, sRev), "会话", sessionId);

        appendEvent(sessionId, run.getId(), HarnessEventType.RUN_QUEUED, null, null, null,
                Map.of("requirement", truncate(request.getRequirement(), 500),
                        "permissionMode", mode.name()));

        // 事务提交后再异步推进，避免读不到未提交的 Run
        final String fSessionId = sessionId;
        final String fRunId = run.getId();
        final String fTenant = tenantId;
        final String fUser = userId;
        final String fUsername = CurrentUserContext.getUsername();
        runAfterCommit(() -> startRunAsync(fTenant, fUser, fUsername, fSessionId, fRunId));
        return run;
    }

    public List<HarnessRun> listRuns(String sessionId) {
        getSession(sessionId);
        return store.listRuns(requireTenant(), sessionId);
    }

    public HarnessRun getRun(String sessionId, String runId) {
        HarnessRun run = HarnessStoreService.requirePresent(
                store.findRun(requireTenant(), runId), "Run", runId);
        if (!run.getSessionId().equals(sessionId)) {
            throw new ResourceNotFoundException("Run 不属于该会话: " + runId);
        }
        return run;
    }

    // ===== Events =====

    public List<HarnessEvent> listEvents(String sessionId, String runId, long afterSequence, int limit) {
        getRun(sessionId, runId);
        return store.listEvents(runId, afterSequence, limit);
    }

    public HarnessEvent appendEvent(String sessionId, String runId, HarnessEventType type,
                                    String stepId, String toolCallId, String approvalId,
                                    Map<String, Object> payload) {
        HarnessEvent event = new HarnessEvent();
        event.setId(IdGenerator.nextId());
        event.setTenantId(CurrentUserContext.getTenantId());
        event.setSessionId(sessionId);
        event.setRunId(runId);
        event.setSequenceNo(store.nextEventSequence(runId));
        event.setEventType(type.code());
        event.setStepId(stepId);
        event.setToolCallId(toolCallId);
        event.setApprovalId(approvalId);
        event.setPayloadJson(payload == null ? "{}" : toJson(payload));
        event.setCreatedTime(OffsetDateTime.now());
        return store.insertEvent(event);
    }

    // ===== Cancel / Input =====

    @Transactional
    public HarnessRun requestCancel(String sessionId, String runId) {
        HarnessRun run = getRun(sessionId, runId);
        if (run.statusEnum().isTerminal()) {
            return run;
        }
        long rev = run.getRevision() == null ? 0L : run.getRevision();
        run.setCancelRequested(true);
        // 非 RUNNING 时直接进入 CANCELLED；RUNNING 由异步循环观察 cancelRequested
        if (run.statusEnum() != RunStatus.RUNNING && run.statusEnum() != RunStatus.QUEUED) {
            requireTransition(run, RunStatus.CANCELLED);
            run.setStatus(RunStatus.CANCELLED.name());
        }
        HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", runId);
        appendEvent(sessionId, runId, HarnessEventType.RUN_CANCEL_REQUESTED, null, null, null,
                Map.of("status", run.getStatus()));
        return run;
    }

    @Transactional
    public HarnessRun queueInput(String sessionId, String runId, QueueInputRequest request) {
        HarnessRun run = getRun(sessionId, runId);
        String type = request.getType() == null ? "" : request.getType().trim().toUpperCase();
        if (!"STEER".equals(type) && !"FOLLOW_UP".equals(type)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "type 仅允许 STEER/FOLLOW_UP");
        }
        if (run.statusEnum().isTerminal()) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "Run 已终态，无法注入输入: " + run.getStatus());
        }
        appendEvent(sessionId, runId, HarnessEventType.USER_INPUT, null, null, null,
                Map.of("type", type, "content", truncate(request.getContent(), 2000)));

        // WAITING_FOR_INPUT → QUEUED 并重新异步推进
        if (run.statusEnum() == RunStatus.WAITING_FOR_INPUT) {
            long rev = run.getRevision() == null ? 0L : run.getRevision();
            requireTransition(run, RunStatus.QUEUED);
            run.setStatus(RunStatus.QUEUED.name());
            HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", runId);
            final String fTenant = run.getTenantId();
            final String fUser = run.getUserId();
            final String fUsername = CurrentUserContext.getUsername();
            runAfterCommit(() -> startRunAsync(fTenant, fUser, fUsername, sessionId, runId));
        }
        return run;
    }

    // ===== Async execution =====

    /**
     * 异步：QUEUED → RUNNING → 生成计划 → WAITING_FOR_INPUT + plan AWAITING_APPROVAL。
     * 计划批准后由 {@link #onPlanApproved} 继续执行。
     */
    public void startRunAsync(String tenantId, String userId, String username,
                              String sessionId, String runId) {
        executor.execute(() -> {
            try {
                CurrentUserContext.set(userId, tenantId, username);
                executeQueuedRun(sessionId, runId);
            } catch (Exception e) {
                log.error("harness run failed runId={}", runId, e);
                try {
                    failRun(sessionId, runId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                } catch (Exception ex) {
                    log.error("harness failRun also failed runId={}", runId, ex);
                }
            } finally {
                CurrentUserContext.clear();
            }
        });
    }

    private void executeQueuedRun(String sessionId, String runId) {
        HarnessRun run = store.findRun(requireTenant(), runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run 不存在: " + runId));
        if (run.getCancelRequested() != null && run.getCancelRequested()) {
            transitionRun(run, RunStatus.CANCELLED, null);
            appendEvent(sessionId, runId, HarnessEventType.RUN_CANCELLED, null, null, null, Map.of());
            return;
        }
        long rev = run.getRevision() == null ? 0L : run.getRevision();
        requireTransition(run, RunStatus.RUNNING);
        run.setStatus(RunStatus.RUNNING.name());
        HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", runId);
        appendEvent(sessionId, runId, HarnessEventType.RUN_STARTED, null, null, null, Map.of());

        // 生成计划（Phase 1 规则模板，不依赖外部 LLM）
        String taskId = "task-" + runId;
        String planMd = buildPlanMarkdown(run.getRequirement());
        String stepsJson = buildStepsJson(run.getRequirement());
        HarnessPlan plan = planService.createPlan(sessionId, runId, taskId, planMd, stepsJson);
        appendEvent(sessionId, runId, HarnessEventType.PLAN_CREATED,
                plan.getTaskId(), null, plan.getId(),
                Map.of("planId", plan.getId(), "taskId", plan.getTaskId()));
        appendEvent(sessionId, runId, HarnessEventType.PLAN_AWAITING_APPROVAL,
                plan.getTaskId(), null, plan.getId(),
                Map.of("planId", plan.getId(),
                        "taskId", plan.getTaskId(),
                        "planMd", plan.getPlanMd() == null ? "" : plan.getPlanMd(),
                        "canonicalHash", plan.getCanonicalHash(),
                        "expectedRevision", 0));

        // 进入等待输入（计划审批）
        HarnessRun latest = store.findRun(requireTenant(), runId).orElse(run);
        long rev2 = latest.getRevision() == null ? 0L : latest.getRevision();
        requireTransition(latest, RunStatus.WAITING_FOR_INPUT);
        latest.setStatus(RunStatus.WAITING_FOR_INPUT.name());
        latest.setPlanJson(toJson(Map.of("planId", plan.getId(),
                "canonicalHash", plan.getCanonicalHash(),
                "reviewState", plan.getReviewState())));
        HarnessStoreService.requireUpdated(store.updateRun(latest, rev2), "Run", runId);
    }

    /**
     * 计划批准后回调：dry-run 工具循环 → COMPLETED（或预算/取消/审批中断）。
     * 由 PlanService.approve 的调用方（HarnessService.approvePlan）触发。
     */
    public void onPlanApproved(String tenantId, String userId, String username,
                               String sessionId, String runId) {
        executor.execute(() -> {
            try {
                CurrentUserContext.set(userId, tenantId, username);
                executeToolLoop(sessionId, runId);
            } catch (Exception e) {
                log.error("harness tool loop failed runId={}", runId, e);
                try {
                    failRun(sessionId, runId, e.getMessage());
                } catch (Exception ex) {
                    log.error("harness failRun failed runId={}", runId, ex);
                }
            } finally {
                CurrentUserContext.clear();
            }
        });
    }

    /** 计划批准入口（Controller 调用）：批准后触发执行循环。 */
    @Transactional
    public HarnessPlan approvePlan(String planId, long expectedRevision, String expectedHash,
                                   String idempotencyKey) {
        HarnessPlan plan = planService.approve(planId, expectedRevision, expectedHash, idempotencyKey);
        appendEvent(plan.getSessionId(), plan.getRunId(), HarnessEventType.PLAN_APPROVED,
                plan.getTaskId(), null, plan.getId(),
                Map.of("approvedBy", plan.getApprovedBy() == null ? "" : plan.getApprovedBy(),
                        "idempotencyKey", idempotencyKey == null ? "" : idempotencyKey));

        HarnessRun run = store.findRun(requireTenant(), plan.getRunId())
                .orElseThrow(() -> new ResourceNotFoundException("Run 不存在: " + plan.getRunId()));
        if (run.statusEnum() == RunStatus.WAITING_FOR_INPUT) {
            // 状态机: WAITING_* → QUEUED（禁止直跳 RUNNING）
            long rev = run.getRevision() == null ? 0L : run.getRevision();
            requireTransition(run, RunStatus.QUEUED);
            run.setStatus(RunStatus.QUEUED.name());
            HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", run.getId());
            final String fTenant = run.getTenantId();
            final String fUser = run.getUserId();
            final String fUsername = CurrentUserContext.getUsername();
            final String fSessionId = run.getSessionId();
            final String fRunId = run.getId();
            runAfterCommit(() -> onPlanApproved(fTenant, fUser, fUsername, fSessionId, fRunId));
        }
        return plan;
    }

    private void executeToolLoop(String sessionId, String runId) {
        HarnessRun run = store.findRun(requireTenant(), runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run 不存在: " + runId));
        // 允许从 QUEUED 恢复执行（WAITING 已先迁 QUEUED）
        if (run.statusEnum() == RunStatus.QUEUED) {
            long rev0 = run.getRevision() == null ? 0L : run.getRevision();
            requireTransition(run, RunStatus.RUNNING);
            run.setStatus(RunStatus.RUNNING.name());
            HarnessStoreService.requireUpdated(store.updateRun(run, rev0), "Run", runId);
            appendEvent(sessionId, runId, HarnessEventType.RUN_STARTED, null, null, null,
                    Map.of("resumed", true));
        } else if (run.statusEnum() != RunStatus.RUNNING) {
            return;
        }
        HarnessBudget budget = fromJson(run.getBudgetJson(), HarnessBudget.class);
        if (budget == null) {
            budget = HarnessBudget.unlimited();
        }
        PermissionMode mode = PermissionMode.parse(run.getPermissionMode());
        ApprovalPolicy policy = ApprovalPolicy.parse(
                sessionApprovalPolicy(run.getSessionId()));
        HarnessUsage usage = fromJson(run.getUsageJson(), HarnessUsage.class);
        if (usage == null) {
            usage = HarnessUsage.empty();
        }
        long startedAt = System.currentTimeMillis();

        // Phase 1：对 requirement 做 1-3 次 dry-run 只读工具调用
        int plannedCalls = 1;
        for (int i = 0; i < plannedCalls; i++) {
            run = store.findRun(requireTenant(), runId).orElse(run);
            if (run.getCancelRequested() != null && run.getCancelRequested()) {
                transitionRun(run, RunStatus.CANCELLED, null);
                appendEvent(sessionId, runId, HarnessEventType.RUN_CANCELLED, null, null, null, Map.of());
                return;
            }
            usage = usage.withToolCall().withIteration()
                    .withTokens(0, 0);
            usage = new HarnessUsage(usage.toolCalls(), usage.inputTokens(), usage.outputTokens(),
                    usage.iterations(), System.currentTimeMillis() - startedAt);

            if (BudgetPolicy.isExceeded(usage, budget)) {
                failBudget(sessionId, runId, usage, budget);
                return;
            }

            String toolName = "echo";
            String args = toJson(Map.of("requirement", truncate(run.getRequirement(), 200), "iteration", i));
            ToolDecision decision = ToolPolicyEngine.evaluate(mode, policy, DRY_RUN_CAPABILITIES);
            if (decision == ToolDecision.DENY) {
                appendEvent(sessionId, runId, HarnessEventType.TOOL_DENIED, "step-" + i, "call-" + i, null,
                        Map.of("tool", toolName, "reason", "policy_deny"));
                failRun(sessionId, runId, "工具被策略拒绝: " + toolName);
                return;
            }
            if (decision == ToolDecision.ASK) {
                long permRev = run.getPermissionRevision() == null ? 0L : run.getPermissionRevision();
                long rev = run.getRevision() == null ? 0L : run.getRevision();
                HarnessApproval approval = approvalService.createApproval(
                        sessionId, runId, toolName, "call-" + i, args, permRev, rev);
                appendEvent(sessionId, runId, HarnessEventType.APPROVAL_REQUESTED,
                        "step-" + i, "call-" + i, approval.getId(),
                        Map.of("tool", toolName,
                                "approvalId", approval.getId(),
                                "argumentsSha256", approval.getArgumentsSha256(),
                                "argumentsJson", approval.getArgumentsJson() == null ? "{}" : approval.getArgumentsJson(),
                                "expectedRevision", approval.getVersion() == null ? 0 : approval.getVersion()));
                requireTransition(run, RunStatus.WAITING_FOR_APPROVAL);
                run.setStatus(RunStatus.WAITING_FOR_APPROVAL.name());
                run.setUsageJson(toJson(usage));
                run.setToolCallCount(usage.toolCalls());
                run.setIteration(usage.iterations());
                HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", runId);
                return;
            }

            appendEvent(sessionId, runId, HarnessEventType.TOOL_CALL, "step-" + i, "call-" + i, null,
                    Map.of("tool", toolName));
            HarnessToolExecutor.ToolExecutionResult result = toolExecutor.execute(toolName, args);
            appendEvent(sessionId, runId, HarnessEventType.TOOL_RESULT, "step-" + i, "call-" + i, null,
                    Map.of("tool", toolName, "success", result.success(),
                            "output", truncate(result.output(), 1000)));
            if (!result.success()) {
                failRun(sessionId, runId, "工具执行失败: " + result.output());
                return;
            }
        }

        run = store.findRun(requireTenant(), runId).orElse(run);
        long rev = run.getRevision() == null ? 0L : run.getRevision();
        usage = new HarnessUsage(usage.toolCalls(), usage.inputTokens(), usage.outputTokens(),
                usage.iterations(), System.currentTimeMillis() - startedAt);
        run.setUsageJson(toJson(usage));
        run.setToolCallCount(usage.toolCalls());
        run.setIteration(usage.iterations());
        requireTransition(run, RunStatus.COMPLETED);
        run.setStatus(RunStatus.COMPLETED.name());
        HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", runId);
        appendEvent(sessionId, runId, HarnessEventType.RUN_COMPLETED, null, null, null,
                Map.of("toolCalls", usage.toolCalls(), "iterations", usage.iterations()));
    }

    /** 审批 resolve；APPROVE 成功后自动 claim 并恢复执行（避免前端漏调 claim 卡死）。 */
    @Transactional
    public HarnessApproval resolveApproval(String approvalId, String decision, long expectedRevision,
                                           String argumentsSha256, String decisionId) {
        return resolveApproval(approvalId, decision, expectedRevision, argumentsSha256, decisionId, null);
    }

    /** 审批 resolve（可带 note）；APPROVE 成功后自动 claim 并恢复执行。 */
    @Transactional
    public HarnessApproval resolveApproval(String approvalId, String decision, long expectedRevision,
                                           String argumentsSha256, String decisionId, String note) {
        HarnessApproval approval = approvalService.resolve(
                approvalId, decision, expectedRevision, argumentsSha256, decisionId, note);
        appendEvent(approval.getSessionId(), approval.getRunId(), HarnessEventType.APPROVAL_RESOLVED,
                null, approval.getToolCallId(), approval.getId(),
                Map.of("decision", decision, "state", approval.getState()));
        if (ApprovalService.DECISION_APPROVE.equals(decision)) {
            // 控制面 resolve 后立即 claim；claim 失败必须 fail-closed 推进 Run
            try {
                claimApproval(approval.getSessionId(), approval.getRunId(), approval.getId());
            } catch (BusinessException e) {
                log.warn("auto-claim after resolve failed approvalId={} err={}", approvalId, e.getMessage());
                failRun(approval.getSessionId(), approval.getRunId(),
                        "审批 claim 失败: " + e.getMessage());
            }
        } else {
            // DENY: Run 失败关闭
            failRun(approval.getSessionId(), approval.getRunId(), "工具调用被拒绝: " + approval.getToolName());
        }
        return store.findApproval(requireTenant(), approvalId).orElse(approval);
    }

    /** claim 审批并推进 Run（工具循环继续）。 */
    @Transactional
    public HarnessApproval claimApproval(String sessionId, String runId, String approvalId) {
        HarnessApproval approval = approvalService.claim(runId, approvalId);
        if (sessionId != null && !sessionId.isBlank()
                && approval.getSessionId() != null
                && !approval.getSessionId().equals(sessionId)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "审批不属于该会话: approval.sessionId=" + approval.getSessionId());
        }
        appendEvent(approval.getSessionId(), runId, HarnessEventType.APPROVAL_CONSUMED,
                null, approval.getToolCallId(), approval.getId(),
                Map.of("tool", approval.getToolName()));
        HarnessRun run = store.findRun(requireTenant(), runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run 不存在: " + runId));
        if (run.statusEnum() == RunStatus.WAITING_FOR_APPROVAL) {
            // 状态机: WAITING_* → QUEUED，再异步推进
            long rev = run.getRevision() == null ? 0L : run.getRevision();
            requireTransition(run, RunStatus.QUEUED);
            run.setStatus(RunStatus.QUEUED.name());
            HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", runId);
            final String fTenant = run.getTenantId();
            final String fUser = run.getUserId();
            final String fUsername = CurrentUserContext.getUsername();
            final String fSessionId = sessionId != null && !sessionId.isBlank()
                    ? sessionId : approval.getSessionId();
            final String fRunId = runId;
            runAfterCommit(() -> onPlanApproved(fTenant, fUser, fUsername, fSessionId, fRunId));
        }
        return approval;
    }

    // ===== helpers =====

    private void failRun(String sessionId, String runId, String message) {
        HarnessRun run = store.findRun(CurrentUserContext.getTenantId(), runId).orElse(null);
        if (run == null || run.statusEnum().isTerminal()) {
            return;
        }
        long rev = run.getRevision() == null ? 0L : run.getRevision();
        requireTransition(run, RunStatus.FAILED);
        run.setStatus(RunStatus.FAILED.name());
        run.setErrorMessage(truncate(message, 1000));
        HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", runId);
        appendEvent(sessionId, runId, HarnessEventType.RUN_FAILED, null, null, null,
                Map.of("error", truncate(message, 500)));
    }

    private void failBudget(String sessionId, String runId, HarnessUsage usage, HarnessBudget budget) {
        String reason = BudgetPolicy.firstBreach(usage, budget).orElse("budget exceeded");
        HarnessRun run = store.findRun(CurrentUserContext.getTenantId(), runId).orElse(null);
        if (run != null && !run.statusEnum().isTerminal()) {
            appendEvent(sessionId, runId, HarnessEventType.BUDGET_EXCEEDED, null, null, null,
                    Map.of("reason", reason, "usage", toJson(usage)));
        }
        failRun(sessionId, runId, "预算超限: " + reason);
    }

    private void transitionRun(HarnessRun run, RunStatus to, String error) {
        long rev = run.getRevision() == null ? 0L : run.getRevision();
        requireTransition(run, to);
        run.setStatus(to.name());
        if (error != null) {
            run.setErrorMessage(truncate(error, 1000));
        }
        HarnessStoreService.requireUpdated(store.updateRun(run, rev), "Run", run.getId());
    }

    private static void requireTransition(HarnessRun run, RunStatus to) {
        RunStatus from = run.statusEnum();
        if (!RunStatus.canTransitionTo(from, to)) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "非法状态迁移: " + from + " → " + to);
        }
    }

    private String sessionApprovalPolicy(String sessionId) {
        return store.findSession(requireTenant(), sessionId)
                .map(HarnessSession::getApprovalPolicy)
                .orElse(ApprovalPolicy.ON_REQUEST.name());
    }

    private static String normalizeVerification(String raw) {
        if (raw == null || raw.isBlank()) {
            return "OFF";
        }
        String v = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (v) {
            case "OFF", "LIGHT", "STRICT" -> v;
            default -> "OFF";
        };
    }

    private static String normalizeThinking(String raw) {
        if (raw == null || raw.isBlank()) {
            return "MEDIUM";
        }
        String v = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (v) {
            case "NONE", "LOW", "MEDIUM", "HIGH" -> v;
            default -> "MEDIUM";
        };
    }

    private static String buildPlanMarkdown(String requirement) {
        return """
                # 执行计划

                ## 需求
                %s

                ## 步骤
                1. 解析需求与约束（只读）
                2. dry-run 回显关键操作（Phase 1 不落真实资源）
                3. 汇总结果并结束

                > Phase 1 内核：真实 MCP/沙箱执行见 Phase 2。
                """.formatted(truncate(requirement, 800));
    }

    private static String buildStepsJson(String requirement) {
        // 手写 JSON，避免测试/异步线程依赖 ObjectMapper
        return "[{\"step\":1,\"name\":\"parse_requirement\",\"capability\":\"READ\"},"
                + "{\"step\":2,\"name\":\"dry_run_echo\",\"capability\":\"READ\"},"
                + "{\"step\":3,\"name\":\"summarize\",\"capability\":\"READ\"}]";
    }

    private String toJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Run 权限天花板钳制：不得超过会话 permissionMode。 */
    static PermissionMode clampPermissionMode(PermissionMode sessionMode, PermissionMode requested) {
        if (sessionMode == null) {
            return PermissionMode.READ_ONLY;
        }
        if (requested == null) {
            return sessionMode;
        }
        return requested.rank() <= sessionMode.rank() ? requested : sessionMode;
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static String requireTenant() {
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_UNAUTHORIZED, "缺失租户上下文");
        }
        return tenantId;
    }

    private static String requireUser() {
        String userId = CurrentUserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_UNAUTHORIZED, "缺失用户上下文");
        }
        return userId;
    }
}
