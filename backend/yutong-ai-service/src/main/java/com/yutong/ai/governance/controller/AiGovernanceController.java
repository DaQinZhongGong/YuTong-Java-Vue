package com.yutong.ai.governance.controller;

import com.yutong.ai.gateway.domain.AiPromptTemplate;
import com.yutong.ai.governance.domain.*;
import com.yutong.ai.governance.dto.*;
import com.yutong.ai.governance.service.AiGovernanceApplicationService;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * AI 治理接口。设计来源: 37-AI治理与评测设计 (GA2-45 v1.0)
 * <p>
 * 21 个 REST 端点覆盖 5 大能力域:
 * <ol>
 *   <li>Prompt 治理 (5 端点)</li>
 *   <li>AI 工具注册 (5 端点)</li>
 *   <li>成本治理 (2 端点)</li>
 *   <li>反馈闭环 (3 端点)</li>
 *   <li>RAG 评测 (5 端点)</li>
 *   <li>监控统计 (1 端点)</li>
 * </ol>
 *
 * <p>审计: 11 个关键写操作接入 @Auditable AOP, module=ai, bizType=ai_prompt/ai_tool/ai_quota/ai_feedback/ai_eval。
 */
@Tag(name = "AI-治理与评测")
@RestController
@RequestMapping("/api/v1/ai-governance")
public class AiGovernanceController {

    private final AiGovernanceApplicationService service;

    public AiGovernanceController(AiGovernanceApplicationService service) {
        this.service = service;
    }

    // ==================== 1. Prompt 治理 ====================

    @Operation(summary = "分页查询 Prompt 模板", operationId = "pageAiGovernancePrompts")
    @RequiresPermission("ai:prompt:list")
    @GetMapping("/prompts")
    public Result<PageResult<AiPromptTemplate>> pagePrompts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String status) {
        return Result.ok(service.pagePrompts(PageRequest.of(page, size), templateCode, scenario, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询 Prompt 模板详情", operationId = "getAiGovernancePrompt")
    @RequiresPermission("ai:prompt:detail")
    @GetMapping("/prompts/{id}")
    public Result<AiPromptTemplate> getPrompt(@PathVariable String id) {
        return Result.ok(service.getPrompt(id), TraceContext.getTraceId());
    }

    @Operation(summary = "保存 Prompt 草稿", operationId = "saveAiGovernancePromptDraft")
    @Auditable(operationType = "CREATE", module = "ai", bizType = "ai_prompt", bizIdExpr = "#result.data.id", content = "保存 Prompt 草稿")
    @RequiresPermission("ai:prompt:add")
    @PostMapping("/prompts")
    public Result<AiPromptTemplate> savePromptDraft(@RequestBody AiPromptTemplate template) {
        return Result.ok(service.savePromptDraft(template), TraceContext.getTraceId());
    }

    @Operation(summary = "发布 Prompt 模板", operationId = "publishAiGovernancePrompt")
    @Auditable(operationType = "PUBLISH", module = "ai", bizType = "ai_prompt", bizIdExpr = "#id", content = "发布 Prompt 模板")
    @RequiresPermission("ai:prompt:publish")
    @PostMapping("/prompts/{id}/publish")
    public Result<AiPromptTemplate> publishPrompt(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.publishPrompt(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "禁用 Prompt 模板", operationId = "disableAiGovernancePrompt")
    @Auditable(operationType = "DISABLE", module = "ai", bizType = "ai_prompt", bizIdExpr = "#id", content = "禁用 Prompt 模板")
    @RequiresPermission("ai:prompt:disable")
    @PostMapping("/prompts/{id}/disable")
    public Result<AiPromptTemplate> disablePrompt(@PathVariable String id,
                                                  @RequestParam Integer version,
                                                  @RequestParam(required = false) String reason) {
        return Result.ok(service.disablePrompt(id, reason, version), TraceContext.getTraceId());
    }

    // ==================== 2. AI 工具注册 ====================

    @Operation(summary = "分页查询 AI 工具注册表", operationId = "pageAiGovernanceTools")
    @RequiresPermission("ai:tool:list")
    @GetMapping("/tools")
    public Result<PageResult<AiToolRegistry>> pageTools(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String aiLevel,
            @RequestParam(required = false) Boolean enabledOnly,
            @RequestParam(required = false) Boolean forbiddenOnly) {
        return Result.ok(service.pageTools(PageRequest.of(page, size), toolName, riskLevel, aiLevel, enabledOnly, forbiddenOnly),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询 AI 工具详情", operationId = "getAiGovernanceTool")
    @RequiresPermission("ai:tool:list")
    @GetMapping("/tools/{id}")
    public Result<AiToolRegistry> getTool(@PathVariable String id) {
        return Result.ok(service.getTool(id), TraceContext.getTraceId());
    }

    @Operation(summary = "保存 AI 工具", operationId = "saveAiGovernanceTool")
    @Auditable(operationType = "CREATE", module = "ai", bizType = "ai_tool", bizIdExpr = "#result.data.id", content = "保存 AI 工具注册")
    @RequiresPermission("ai:tool:add")
    @PostMapping("/tools")
    public Result<AiToolRegistry> saveTool(@Valid @RequestBody SaveToolRequest request) {
        return Result.ok(service.saveTool(request), TraceContext.getTraceId());
    }

    @Operation(summary = "启停 AI 工具", operationId = "toggleAiGovernanceTool")
    @Auditable(operationType = "UPDATE", module = "ai", bizType = "ai_tool", bizIdExpr = "#id", content = "启停 AI 工具")
    @RequiresPermission("ai:tool:edit")
    @PostMapping("/tools/{id}/toggle")
    public Result<AiToolRegistry> toggleTool(@PathVariable String id,
                                             @RequestParam boolean enabled,
                                             @RequestParam Integer version) {
        return Result.ok(service.toggleTool(id, enabled, version), TraceContext.getTraceId());
    }

    @Operation(summary = "校验 AI 工具合法性", operationId = "validateAiGovernanceTool")
    @RequiresPermission("ai:tool:edit")
    @PostMapping("/tools/validate")
    public Result<AiToolRegistry> validateTool(@RequestParam String toolName) {
        return Result.ok(service.validateTool(toolName), TraceContext.getTraceId());
    }

    // ==================== 3. 成本治理 ====================

    @Operation(summary = "分页查询成本额度", operationId = "pageAiGovernanceQuotas")
    @RequiresPermission("ai:cost:save")
    @GetMapping("/quotas")
    public Result<PageResult<AiCostQuota>> pageQuotas(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String quotaScope,
            @RequestParam(required = false) String scopeKey) {
        return Result.ok(service.pageQuotas(PageRequest.of(page, size), quotaScope, scopeKey),
                TraceContext.getTraceId());
    }

    @Operation(summary = "保存成本额度配置", operationId = "saveAiGovernanceQuota")
    @Auditable(operationType = "CREATE", module = "ai", bizType = "ai_quota", bizIdExpr = "#result.data.id", content = "保存 AI 成本额度")
    @RequiresPermission("ai:cost:save")
    @PostMapping("/quotas")
    public Result<AiCostQuota> saveQuota(@Valid @RequestBody SaveQuotaRequest request) {
        return Result.ok(service.saveQuota(request), TraceContext.getTraceId());
    }

    // ==================== 4. 反馈闭环 ====================

    @Operation(summary = "分页查询用户反馈", operationId = "pageAiGovernanceFeedbacks")
    @RequiresPermission("ai:feedback:list")
    @GetMapping("/feedbacks")
    public Result<PageResult<AiFeedback>> pageFeedbacks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) Boolean unhandledOnly,
            @RequestParam(required = false) String userId) {
        return Result.ok(service.pageFeedbacks(PageRequest.of(page, size), feedbackType, unhandledOnly, userId),
                TraceContext.getTraceId());
    }

    @Operation(summary = "提交用户反馈", operationId = "submitAiGovernanceFeedback")
    @Auditable(operationType = "CREATE", module = "ai", bizType = "ai_feedback", bizIdExpr = "#result.data.id", content = "提交 AI 用户反馈")
    @RequiresPermission("ai:feedback:add")
    @PostMapping("/feedbacks")
    public Result<AiFeedback> submitFeedback(@Valid @RequestBody SubmitFeedbackRequest request) {
        return Result.ok(service.submitFeedback(request), TraceContext.getTraceId());
    }

    @Operation(summary = "处理用户反馈", operationId = "handleAiGovernanceFeedback")
    @Auditable(operationType = "HANDLE", module = "ai", bizType = "ai_feedback", bizIdExpr = "#id", content = "处理 AI 用户反馈")
    @RequiresPermission("ai:feedback:process")
    @PostMapping("/feedbacks/{id}/handle")
    public Result<AiFeedback> handleFeedback(@PathVariable String id,
                                             @RequestParam String handleResult) {
        return Result.ok(service.handleFeedback(id, handleResult), TraceContext.getTraceId());
    }

    // ==================== 5. RAG 评测 ====================

    @Operation(summary = "分页查询评测样本集", operationId = "pageAiGovernanceEvalDatasets")
    @RequiresPermission("ai:eval:trigger")
    @GetMapping("/eval/datasets")
    public Result<PageResult<AiEvalDataset>> pageDatasets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String caseId) {
        return Result.ok(service.pageDatasets(PageRequest.of(page, size), scenario, difficulty, caseId),
                TraceContext.getTraceId());
    }

    @Operation(summary = "分页查询评测运行批次", operationId = "pageAiGovernanceEvalRuns")
    @RequiresPermission("ai:eval:trigger")
    @GetMapping("/eval/runs")
    public Result<PageResult<AiEvalRun>> pageRuns(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String releaseDecision) {
        return Result.ok(service.pageRuns(PageRequest.of(page, size), releaseDecision),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询评测运行详情", operationId = "getAiGovernanceEvalRun")
    @RequiresPermission("ai:eval:trigger")
    @GetMapping("/eval/runs/{id}")
    public Result<AiEvalRun> getRun(@PathVariable String id) {
        return Result.ok(service.getRun(id), TraceContext.getTraceId());
    }

    @Operation(summary = "触发评测运行", operationId = "triggerAiGovernanceEvalRun")
    @Auditable(operationType = "TRIGGER", module = "ai", bizType = "ai_eval", bizIdExpr = "#result.data.id", content = "触发 AI 评测运行")
    @RequiresPermission("ai:eval:trigger")
    @PostMapping("/eval/runs")
    public Result<AiEvalRun> triggerRun(@Valid @RequestBody TriggerEvalRunRequest request) {
        return Result.ok(service.triggerEvalRun(request), TraceContext.getTraceId());
    }

    @Operation(summary = "查询评测运行样本结果", operationId = "pageAiGovernanceEvalResults")
    @RequiresPermission("ai:eval:trigger")
    @GetMapping("/eval/runs/{id}/results")
    public Result<PageResult<AiEvalResult>> pageResults(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) Boolean failedOnly) {
        return Result.ok(service.pageResults(PageRequest.of(page, size), id, scenario, failedOnly),
                TraceContext.getTraceId());
    }

    // ==================== 6. 监控统计 ====================

    @Operation(summary = "AI 治理监控统计", operationId = "getAiGovernanceStats")
    @RequiresPermission("ai:prompt:list")
    @GetMapping("/stats")
    public Result<AiGovernanceStatsVO> getStats() {
        return Result.ok(service.getStats(), TraceContext.getTraceId());
    }
}
