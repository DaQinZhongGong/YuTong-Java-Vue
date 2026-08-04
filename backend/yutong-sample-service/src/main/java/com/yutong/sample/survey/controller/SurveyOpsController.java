package com.yutong.sample.survey.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.survey.domain.SurAnswer;
import com.yutong.sample.survey.domain.SurQuestion;
import com.yutong.sample.survey.domain.SurResponse;
import com.yutong.sample.survey.domain.SurSurvey;
import com.yutong.sample.survey.dto.AiDraftRequest;
import com.yutong.sample.survey.dto.SaveQuestionRequest;
import com.yutong.sample.survey.dto.SaveSurveyRequest;
import com.yutong.sample.survey.dto.SubmitResponseRequest;
import com.yutong.sample.survey.dto.SurveyStatsVO;
import com.yutong.sample.survey.service.SurveyOpsApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 问卷表单 Controller。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)。
 *
 * <p>核心 6 项能力 API 映射:
 * <ul>
 *   <li>动态表单渲染: GET /surveys (列表) + GET /surveys/{id}/detail (详情聚合, 含题目列表)</li>
 *   <li>条件显隐: 服务端 submitResponse 时执行 logicJson 校验 (前端按 logicJson 实时渲染)</li>
 *   <li>字段校验: 服务端 submitResponse 时执行 validationJson 校验</li>
 *   <li>移动端填写: POST /surveys/responses/start + POST /surveys/responses/submit (同一接口同时服务 web 和移动, source 字段标识来源)</li>
 *   <li>统计报表: GET /surveys/stats (仪表盘多维聚合)</li>
 *   <li>AI 生成题目草稿: POST /surveys/ai-draft (返回草稿 JSON, 不直接写库)</li>
 * </ul>
 *
 * <p>状态机 API:
 * <ul>
 *   <li>POST /surveys (创建, DRAFT)</li>
 *   <li>POST /surveys/{id}/publish (DRAFT → PUBLISHED)</li>
 *   <li>POST /surveys/{id}/start (PUBLISHED/CLOSED → COLLECTING)</li>
 *   <li>POST /surveys/{id}/close (COLLECTING → CLOSED)</li>
 *   <li>POST /surveys/{id}/archive (DRAFT/CLOSED → ARCHIVED)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/surveys")
@Tag(name = "SurveyOps", description = "问卷表单-动态渲染/条件显隐/字段校验/移动填写/统计报表/AI 草稿")
public class SurveyOpsController {

    private final SurveyOpsApplicationService service;

    public SurveyOpsController(SurveyOpsApplicationService service) {
        this.service = service;
    }

    // ==================== 1. 问卷 (状态机) ====================

    @GetMapping
    @Operation(summary = "分页查询问卷", operationId = "pageSurveys")
    @RequiresPermission("biz:survey:list")
    public Result<Page<SurSurvey>> pageSurveys(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String surveyNo,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category) {
        Page<SurSurvey> page = service.pageSurveys(pageNo, pageSize, surveyNo, title, status, category);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询问卷详情", operationId = "getSurvey")
    @RequiresPermission("biz:survey:detail")
    public Result<SurSurvey> getSurvey(@PathVariable String id) {
        return Result.ok(service.getSurvey(id), TraceContext.getTraceId());
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "问卷详情聚合 (问卷 + 题目列表, 用于问卷设计器/填写页)", operationId = "getSurveyDetail")
    @RequiresPermission("biz:survey:detail")
    public Result<Map<String, Object>> getSurveyDetail(@PathVariable String id) {
        return Result.ok(service.getSurveyDetail(id), TraceContext.getTraceId());
    }

    @PostMapping
    @Operation(summary = "创建问卷 (初始 DRAFT 状态)", operationId = "createSurvey")
    @RequiresPermission("biz:survey:create")
    @Auditable(bizType = "survey", module = "sample", bizIdExpr = "#result.data.id",
            operationType = "CREATE", content = "创建问卷", recordResult = true)
    public Result<SurSurvey> createSurvey(@Valid @RequestBody SaveSurveyRequest request) {
        return Result.ok(service.createSurvey(request), TraceContext.getTraceId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新问卷 (DRAFT 可全字段修改; COLLECTING/CLOSED 仅描述/主题/备注; ARCHIVED 只读)",
            operationId = "updateSurvey")
    @RequiresPermission("biz:survey:update")
    @Auditable(bizType = "survey", module = "sample", bizIdExpr = "#id",
            operationType = "UPDATE", content = "更新问卷")
    public Result<SurSurvey> updateSurvey(@PathVariable String id,
                                          @Valid @RequestBody SaveSurveyRequest request) {
        return Result.ok(service.updateSurvey(id, request), TraceContext.getTraceId());
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布问卷 (DRAFT → PUBLISHED, 至少 1 道题目)", operationId = "publishSurvey")
    @RequiresPermission("biz:survey:submit")
    @Auditable(bizType = "survey", module = "sample", bizIdExpr = "#id",
            operationType = "PUBLISH", content = "发布问卷")
    public Result<SurSurvey> publishSurvey(@PathVariable String id) {
        return Result.ok(service.publishSurvey(id), TraceContext.getTraceId());
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "开始收集答卷 (PUBLISHED/CLOSED → COLLECTING)", operationId = "startCollecting")
    @RequiresPermission("biz:survey:submit")
    @Auditable(bizType = "survey", module = "sample", bizIdExpr = "#id",
            operationType = "START_COLLECTING", content = "开始收集答卷")
    public Result<SurSurvey> startCollecting(@PathVariable String id) {
        return Result.ok(service.startCollecting(id), TraceContext.getTraceId());
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "关闭收集 (COLLECTING → CLOSED, 提前结束)", operationId = "closeSurvey")
    @RequiresPermission("biz:survey:submit")
    @Auditable(bizType = "survey", module = "sample", bizIdExpr = "#id",
            operationType = "CLOSE", content = "关闭问卷收集")
    public Result<SurSurvey> closeSurvey(@PathVariable String id) {
        return Result.ok(service.closeSurvey(id), TraceContext.getTraceId());
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档问卷 (DRAFT/CLOSED → ARCHIVED, 只读)", operationId = "archiveSurvey")
    @RequiresPermission("biz:survey:submit")
    @Auditable(bizType = "survey", module = "sample", bizIdExpr = "#id",
            operationType = "ARCHIVE", content = "归档问卷")
    public Result<SurSurvey> archiveSurvey(@PathVariable String id) {
        return Result.ok(service.archiveSurvey(id), TraceContext.getTraceId());
    }

    // ==================== 2. 题目 CRUD ====================

    @GetMapping("/{surveyId}/questions")
    @Operation(summary = "查询问卷下所有题目 (按 sortNo 升序)", operationId = "listSurveyQuestions")
    @RequiresPermission("biz:survey:list")
    public Result<List<SurQuestion>> listQuestions(@PathVariable String surveyId) {
        return Result.ok(service.listQuestions(surveyId), TraceContext.getTraceId());
    }

    @PostMapping("/{surveyId}/questions")
    @Operation(summary = "新增题目 (仅 DRAFT 状态可新增)", operationId = "saveSurveyQuestion")
    @RequiresPermission("biz:survey:create")
    @Auditable(bizType = "survey_question", module = "sample", bizIdExpr = "#result.data.id",
            operationType = "CREATE", content = "新增题目", recordResult = true)
    public Result<SurQuestion> saveQuestion(@PathVariable String surveyId,
                                            @Valid @RequestBody SaveQuestionRequest request) {
        return Result.ok(service.saveQuestion(surveyId, request), TraceContext.getTraceId());
    }

    @PutMapping("/{surveyId}/questions/{questionId}")
    @Operation(summary = "更新题目 (仅 DRAFT 状态可修改)", operationId = "updateSurveyQuestion")
    @RequiresPermission("biz:survey:update")
    @Auditable(bizType = "survey_question", module = "sample", bizIdExpr = "#questionId",
            operationType = "UPDATE", content = "更新题目")
    public Result<SurQuestion> updateQuestion(@PathVariable String surveyId,
                                              @PathVariable String questionId,
                                              @Valid @RequestBody SaveQuestionRequest request) {
        return Result.ok(service.updateQuestion(surveyId, questionId, request), TraceContext.getTraceId());
    }

    @DeleteMapping("/{surveyId}/questions/{questionId}")
    @Operation(summary = "删除题目 (仅 DRAFT 状态可删除)", operationId = "deleteSurveyQuestion")
    @RequiresPermission("biz:survey:delete")
    @Auditable(bizType = "survey_question", module = "sample", bizIdExpr = "#questionId",
            operationType = "DELETE", content = "删除题目")
    public Result<Void> deleteQuestion(@PathVariable String surveyId,
                                       @PathVariable String questionId) {
        service.deleteQuestion(surveyId, questionId);
        return Result.ok(null, TraceContext.getTraceId());
    }

    // ==================== 3. 答卷 (移动端 + Web 同一接口) ====================

    @PostMapping("/responses/start")
    @Operation(summary = "启动答卷 (创建 IN_PROGRESS 记录, 校验填写次数限制)", operationId = "startSurveyResponse")
    @RequiresPermission("biz:survey:submit")
    @Auditable(bizType = "survey_response", module = "sample", bizIdExpr = "#result.data.id",
            operationType = "START", content = "启动答卷", recordResult = true)
    public Result<SurResponse> startResponse(@RequestParam String surveyId,
                                             @RequestParam(required = false, defaultValue = "WEB_ADMIN") String source) {
        return Result.ok(service.startResponse(surveyId, source), TraceContext.getTraceId());
    }

    @PostMapping("/responses/submit")
    @Operation(summary = "提交答卷 (字段校验 + 条件显隐验证, 兼容未启动直接提交)", operationId = "submitSurveyResponse")
    @RequiresPermission("biz:survey:submit")
    @Auditable(bizType = "survey_response", module = "sample", bizIdExpr = "#result.data.id",
            operationType = "SUBMIT", content = "提交答卷", recordResult = true)
    public Result<SurResponse> submitResponse(@Valid @RequestBody SubmitResponseRequest request) {
        return Result.ok(service.submitResponse(request), TraceContext.getTraceId());
    }

    @GetMapping("/responses/{id}")
    @Operation(summary = "查询答卷详情", operationId = "getSurveyResponse")
    @RequiresPermission("biz:survey:detail")
    public Result<SurResponse> getResponse(@PathVariable String id) {
        return Result.ok(service.getResponse(id), TraceContext.getTraceId());
    }

    @GetMapping("/responses/{id}/answers")
    @Operation(summary = "查询答卷下所有答题 (按 questionCode 升序)", operationId = "listSurveyAnswers")
    @RequiresPermission("biz:survey:list")
    public Result<List<SurAnswer>> listAnswers(@PathVariable String id) {
        return Result.ok(service.listAnswers(id), TraceContext.getTraceId());
    }

    @GetMapping("/responses")
    @Operation(summary = "分页查询答卷", operationId = "pageSurveyResponses")
    @RequiresPermission("biz:survey:list")
    public Result<Page<SurResponse>> pageResponses(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String surveyId,
            @RequestParam(required = false) String respondentId,
            @RequestParam(required = false) String status) {
        Page<SurResponse> page = service.pageResponses(pageNo, pageSize, surveyId, respondentId, status);
        return Result.ok(page, TraceContext.getTraceId());
    }

    // ==================== 4. 统计报表 ====================

    @GetMapping("/stats")
    @Operation(summary = "问卷监控统计 (问卷/状态/答卷/来源/分类/趋势多维聚合)", operationId = "getSurveyStats")
    @RequiresPermission("biz:survey:list")
    public Result<SurveyStatsVO> getStats() {
        return Result.ok(service.getStats(), TraceContext.getTraceId());
    }

    // ==================== 5. AI 生成题目草稿 ====================

    @PostMapping("/ai-draft")
    @Operation(summary = "AI 生成问卷题目草稿 (mock-model, 不直接写库, 返回草稿 JSON)",
            operationId = "generateSurveyAiDraft")
    @RequiresPermission("biz:survey:create")
    @Auditable(bizType = "survey_ai_draft", module = "sample",
            operationType = "AI_GENERATE", content = "AI 生成问卷题目草稿")
    public Result<String> generateAiDraft(@Valid @RequestBody AiDraftRequest request) {
        return Result.ok(service.generateAiDraft(request), TraceContext.getTraceId());
    }
}
