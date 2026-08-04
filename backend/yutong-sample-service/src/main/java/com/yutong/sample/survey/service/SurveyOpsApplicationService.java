package com.yutong.sample.survey.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.sample.survey.domain.SurAnswer;
import com.yutong.sample.survey.domain.SurQuestion;
import com.yutong.sample.survey.domain.SurResponse;
import com.yutong.sample.survey.domain.SurSurvey;
import com.yutong.sample.survey.dto.AiDraftRequest;
import com.yutong.sample.survey.dto.SaveQuestionRequest;
import com.yutong.sample.survey.dto.SaveSurveyRequest;
import com.yutong.sample.survey.dto.SubmitResponseRequest;
import com.yutong.sample.survey.dto.SurveyStatsVO;
import com.yutong.sample.survey.mapper.SurAnswerMapper;
import com.yutong.sample.survey.mapper.SurQuestionMapper;
import com.yutong.sample.survey.mapper.SurResponseMapper;
import com.yutong.sample.survey.mapper.SurSurveyMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 问卷表单应用服务。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)。
 *
 * <p>核心 6 项能力实现:
 * <ol>
 *   <li>动态表单渲染: sur_survey + sur_question 元数据驱动前端渲染 7 种题型</li>
 *   <li>条件显隐: sur_question.logic_json 数组, 满足任一条件即触发 action (SHOW/HIDE/REQUIRE)</li>
 *   <li>字段校验: sur_question.validation_json 校验 minLength/maxLength/minValue/maxValue/regex/minSelect/maxSelect</li>
 *   <li>移动端填写: sur_response.source 标识来源 (WEB_ADMIN/MOBILE_UNIAPP/API), 同一接口同时服务 web 和移动</li>
 *   <li>统计报表: getStats 输出问卷/答卷/题目/来源/分类/趋势多维聚合</li>
 *   <li>AI 生成题目草稿: generateAiDraft 返回符合规范的题目 JSON 草稿, AI 不直接写库</li>
 * </ol>
 *
 * <p>状态机:
 * <ul>
 *   <li>问卷: DRAFT → PUBLISHED → COLLECTING → CLOSED → ARCHIVED</li>
 *   <li>答卷: IN_PROGRESS → SUBMITTED / ABANDONED</li>
 * </ul>
 *
 * <p>设计约束:
 * <ul>
 *   <li>AI 不直接写入数据库, 只返回草稿 JSON, 用户必须在前端预览并确认后通过 saveQuestion 接口写入</li>
 *   <li>调用 AI 服务时不在事务内</li>
 *   <li>答卷提交时同时校验字段规则和条件显隐规则</li>
 *   <li>重复提交抛 SUR-409004 已提交不可修改</li>
 *   <li>超过单用户填写次数限制抛 SUR-409005</li>
 * </ul>
 */
@Service
public class SurveyOpsApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SurveyOpsApplicationService.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 默认每用户填写次数 */
    private static final int DEFAULT_MAX_RESPONSES_PER_USER = 1;

    /** 问卷资源编码，对齐 permissions.yaml biz:survey:* 命名。 */
    public static final String RESOURCE_CODE = "biz:survey";

    private final SurSurveyMapper surveyMapper;
    private final SurQuestionMapper questionMapper;
    private final SurResponseMapper responseMapper;
    private final SurAnswerMapper answerMapper;
    private final ObjectMapper objectMapper;
    private final DataScopeResolver dataScopeResolver;

    public SurveyOpsApplicationService(SurSurveyMapper surveyMapper,
                                       SurQuestionMapper questionMapper,
                                       SurResponseMapper responseMapper,
                                       SurAnswerMapper answerMapper,
                                       ObjectMapper objectMapper,
                                       DataScopeResolver dataScopeResolver) {
        this.surveyMapper = surveyMapper;
        this.questionMapper = questionMapper;
        this.responseMapper = responseMapper;
        this.answerMapper = answerMapper;
        this.objectMapper = objectMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    // ==================== 1. 问卷 (状态机) ====================

    /**
     * 创建问卷 (初始 DRAFT 状态)。
     */
    @Transactional
    public SurSurvey createSurvey(SaveSurveyRequest request) {
        SurSurvey survey = new SurSurvey();
        survey.setId(IdGenerator.nextId());
        survey.setSurveyNo(generateSurveyNo());
        survey.setTitle(request.getTitle());
        survey.setDescription(request.getDescription());
        survey.setStatus(SurSurvey.STATUS_DRAFT);
        survey.setCategory(request.getCategory());
        survey.setAnonymous(request.getAnonymous() == null ? false : request.getAnonymous());
        survey.setMaxResponsesPerUser(
                request.getMaxResponsesPerUser() == null ? DEFAULT_MAX_RESPONSES_PER_USER : request.getMaxResponsesPerUser());
        survey.setStartTime(request.getStartTime());
        survey.setEndTime(request.getEndTime());
        survey.setResponseCount(0);
        survey.setThemeJson(request.getThemeJson());
        survey.setRemark(request.getRemark());
        try {
            surveyMapper.insert(survey);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_NO_DUPLICATE, "问卷编号已存在: " + survey.getSurveyNo());
        }
        log.info("createSurvey: surveyNo={} title={}", survey.getSurveyNo(), survey.getTitle());
        return survey;
    }

    /**
     * 更新问卷 (DRAFT/ARCHIVED 状态不允许修改关键字段)。
     */
    @Transactional
    public SurSurvey updateSurvey(String id, SaveSurveyRequest request) {
        SurSurvey survey = getSurvey(id);
        // 已发布/收集中的问卷仅允许修改描述/主题/备注, 避免破坏已有答卷
        if (SurSurvey.STATUS_COLLECTING.equals(survey.getStatus())
                || SurSurvey.STATUS_CLOSED.equals(survey.getStatus())) {
            survey.setDescription(request.getDescription());
            survey.setThemeJson(request.getThemeJson());
            survey.setRemark(request.getRemark());
            surveyMapper.updateById(survey);
            return survey;
        }
        // ARCHIVED 归档只读
        if (SurSurvey.STATUS_ARCHIVED.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "归档问卷只读, 不允许修改: " + survey.getStatus());
        }
        survey.setTitle(request.getTitle());
        survey.setDescription(request.getDescription());
        survey.setCategory(request.getCategory());
        if (request.getAnonymous() != null) {
            survey.setAnonymous(request.getAnonymous());
        }
        if (request.getMaxResponsesPerUser() != null) {
            survey.setMaxResponsesPerUser(request.getMaxResponsesPerUser());
        }
        survey.setStartTime(request.getStartTime());
        survey.setEndTime(request.getEndTime());
        survey.setThemeJson(request.getThemeJson());
        survey.setRemark(request.getRemark());
        surveyMapper.updateById(survey);
        log.info("updateSurvey: id={} title={}", id, survey.getTitle());
        return survey;
    }

    /**
     * 发布问卷 (DRAFT → PUBLISHED)。
     */
    @Transactional
    public SurSurvey publishSurvey(String id) {
        SurSurvey survey = getSurvey(id);
        if (!SurSurvey.STATUS_DRAFT.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 DRAFT 状态可发布, 当前: " + survey.getStatus());
        }
        // 校验问卷至少有 1 道题目
        Long questionCount = questionMapper.selectCount(new LambdaQueryWrapper<SurQuestion>()
                .eq(SurQuestion::getSurveyId, id));
        if (questionCount == null || questionCount == 0) {
            throw new BusinessException(ErrorCode.SUR_REQUEST_INVALID, "问卷至少需要 1 道题目才能发布");
        }
        survey.setStatus(SurSurvey.STATUS_PUBLISHED);
        survey.setPublishedTime(OffsetDateTime.now());
        surveyMapper.updateById(survey);
        log.info("publishSurvey: id={} -> PUBLISHED", id);
        return survey;
    }

    /**
     * 开始收集答卷 (PUBLISHED → COLLECTING)。
     */
    @Transactional
    public SurSurvey startCollecting(String id) {
        SurSurvey survey = getSurvey(id);
        if (!SurSurvey.STATUS_PUBLISHED.equals(survey.getStatus())
                && !SurSurvey.STATUS_CLOSED.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 PUBLISHED/CLOSED 状态可开始收集, 当前: " + survey.getStatus());
        }
        survey.setStatus(SurSurvey.STATUS_COLLECTING);
        if (survey.getStartTime() == null) {
            survey.setStartTime(OffsetDateTime.now());
        }
        surveyMapper.updateById(survey);
        log.info("startCollecting: id={} -> COLLECTING", id);
        return survey;
    }

    /**
     * 关闭收集 (COLLECTING → CLOSED, 提前结束)。
     */
    @Transactional
    public SurSurvey closeSurvey(String id) {
        SurSurvey survey = getSurvey(id);
        if (!SurSurvey.STATUS_COLLECTING.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 COLLECTING 状态可关闭, 当前: " + survey.getStatus());
        }
        survey.setStatus(SurSurvey.STATUS_CLOSED);
        survey.setClosedTime(OffsetDateTime.now());
        surveyMapper.updateById(survey);
        log.info("closeSurvey: id={} -> CLOSED", id);
        return survey;
    }

    /**
     * 归档问卷 (DRAFT/CLOSED → ARCHIVED)。
     */
    @Transactional
    public SurSurvey archiveSurvey(String id) {
        SurSurvey survey = getSurvey(id);
        if (!SurSurvey.STATUS_DRAFT.equals(survey.getStatus())
                && !SurSurvey.STATUS_CLOSED.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 DRAFT/CLOSED 状态可归档, 当前: " + survey.getStatus());
        }
        survey.setStatus(SurSurvey.STATUS_ARCHIVED);
        surveyMapper.updateById(survey);
        log.info("archiveSurvey: id={} -> ARCHIVED", id);
        return survey;
    }

    public SurSurvey getSurvey(String id) {
        SurSurvey survey = surveyMapper.selectById(id);
        if (survey == null) {
            throw new ResourceNotFoundException(ErrorCode.SUR_SURVEY_NOT_FOUND);
        }
        return survey;
    }

    public Page<SurSurvey> pageSurveys(int pageNo, int pageSize, String surveyNo, String title,
                                       String status, String category) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        Page<SurSurvey> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SurSurvey> wrapper = new LambdaQueryWrapper<SurSurvey>()
                .eq(SurSurvey::getTenantId, CurrentUserContext.getTenantId())
                .orderByDesc(SurSurvey::getCreatedTime);
        if (surveyNo != null && !surveyNo.isBlank()) {
            wrapper.eq(SurSurvey::getSurveyNo, surveyNo);
        }
        if (title != null && !title.isBlank()) {
            wrapper.like(SurSurvey::getTitle, title);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SurSurvey::getStatus, status);
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(SurSurvey::getCategory, category);
        }
        applyDataScope(wrapper, scope);
        return surveyMapper.selectPage(page, wrapper);
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * SurSurvey 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<SurSurvey> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(SurSurvey::getCreatedBy, userId);
    }

    // ==================== 2. 题目 CRUD ====================

    /**
     * 新增题目 (仅 DRAFT 状态可新增)。
     */
    @Transactional
    public SurQuestion saveQuestion(String surveyId, SaveQuestionRequest request) {
        SurSurvey survey = getSurvey(surveyId);
        if (!SurSurvey.STATUS_DRAFT.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 DRAFT 状态可新增题目, 当前: " + survey.getStatus());
        }
        validateQuestionType(request.getQuestionType());
        validateQuestionOptions(request.getQuestionType(), request.getOptionsJson(), request.getMatrixJson());
        validateLogicRule(request.getLogicJson());

        SurQuestion question = new SurQuestion();
        question.setId(IdGenerator.nextId());
        question.setSurveyId(surveyId);
        question.setQuestionCode(request.getQuestionCode());
        question.setQuestionType(request.getQuestionType());
        question.setTitle(request.getTitle());
        question.setDescription(request.getDescription());
        question.setRequired(request.getRequired() == null ? true : request.getRequired());
        question.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        question.setOptionsJson(request.getOptionsJson());
        question.setValidationJson(request.getValidationJson());
        question.setLogicJson(request.getLogicJson());
        question.setMatrixJson(request.getMatrixJson());
        question.setAiGenerated(false);
        question.setRemark(request.getRemark());
        try {
            questionMapper.insert(question);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SUR_QUESTION_CODE_DUPLICATE,
                    "题目编号已存在: " + request.getQuestionCode());
        }
        log.info("saveQuestion: surveyId={} questionCode={}", surveyId, request.getQuestionCode());
        return question;
    }

    /**
     * 更新题目 (仅 DRAFT 状态可修改)。
     */
    @Transactional
    public SurQuestion updateQuestion(String surveyId, String questionId, SaveQuestionRequest request) {
        SurSurvey survey = getSurvey(surveyId);
        if (!SurSurvey.STATUS_DRAFT.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 DRAFT 状态可修改题目, 当前: " + survey.getStatus());
        }
        SurQuestion question = questionMapper.selectById(questionId);
        if (question == null || !surveyId.equals(question.getSurveyId())) {
            throw new ResourceNotFoundException(ErrorCode.SUR_QUESTION_NOT_FOUND);
        }
        validateQuestionType(request.getQuestionType());
        validateQuestionOptions(request.getQuestionType(), request.getOptionsJson(), request.getMatrixJson());
        validateLogicRule(request.getLogicJson());

        // 如果 questionCode 变更, 校验唯一性
        if (!request.getQuestionCode().equals(question.getQuestionCode())) {
            Long count = questionMapper.selectCount(new LambdaQueryWrapper<SurQuestion>()
                    .eq(SurQuestion::getSurveyId, surveyId)
                    .eq(SurQuestion::getQuestionCode, request.getQuestionCode()));
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.SUR_QUESTION_CODE_DUPLICATE,
                        "题目编号已存在: " + request.getQuestionCode());
            }
            question.setQuestionCode(request.getQuestionCode());
        }
        question.setQuestionType(request.getQuestionType());
        question.setTitle(request.getTitle());
        question.setDescription(request.getDescription());
        question.setRequired(request.getRequired() == null ? true : request.getRequired());
        question.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        question.setOptionsJson(request.getOptionsJson());
        question.setValidationJson(request.getValidationJson());
        question.setLogicJson(request.getLogicJson());
        question.setMatrixJson(request.getMatrixJson());
        question.setRemark(request.getRemark());
        questionMapper.updateById(question);
        log.info("updateQuestion: surveyId={} questionId={}", surveyId, questionId);
        return question;
    }

    /**
     * 删除题目 (仅 DRAFT 状态可删除)。
     */
    @Transactional
    public void deleteQuestion(String surveyId, String questionId) {
        SurSurvey survey = getSurvey(surveyId);
        if (!SurSurvey.STATUS_DRAFT.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 DRAFT 状态可删除题目, 当前: " + survey.getStatus());
        }
        SurQuestion question = questionMapper.selectById(questionId);
        if (question == null || !surveyId.equals(question.getSurveyId())) {
            throw new ResourceNotFoundException(ErrorCode.SUR_QUESTION_NOT_FOUND);
        }
        questionMapper.deleteById(questionId);
        log.info("deleteQuestion: surveyId={} questionId={}", surveyId, questionId);
    }

    /**
     * 查询问卷下所有题目 (按 sortNo 升序)。
     */
    public List<SurQuestion> listQuestions(String surveyId) {
        return questionMapper.selectList(new LambdaQueryWrapper<SurQuestion>()
                .eq(SurQuestion::getSurveyId, surveyId)
                .orderByAsc(SurQuestion::getSortNo));
    }

    // ==================== 3. 答卷提交 (字段校验 + 条件显隐) ====================

    /**
     * 启动一份答卷 (创建 IN_PROGRESS 答卷记录, 校验填写次数限制)。
     */
    @Transactional
    public SurResponse startResponse(String surveyId, String source) {
        SurSurvey survey = getSurvey(surveyId);
        if (!SurSurvey.STATUS_COLLECTING.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 COLLECTING 状态可填写答卷, 当前: " + survey.getStatus());
        }
        String respondentId = CurrentUserContext.getUserId();
        // 校验填写次数限制 (maxResponsesPerUser=0 表示不限制)
        if (survey.getMaxResponsesPerUser() != null && survey.getMaxResponsesPerUser() > 0
                && respondentId != null && !respondentId.isBlank() && !Boolean.TRUE.equals(survey.getAnonymous())) {
            Long existingCount = responseMapper.selectCount(new LambdaQueryWrapper<SurResponse>()
                    .eq(SurResponse::getSurveyId, surveyId)
                    .eq(SurResponse::getRespondentId, respondentId)
                    .eq(SurResponse::getStatus, SurResponse.STATUS_SUBMITTED));
            if (existingCount != null && existingCount >= survey.getMaxResponsesPerUser()) {
                throw new BusinessException(ErrorCode.SUR_RESPONSE_LIMIT_EXCEEDED,
                        "已达最大填写次数: " + survey.getMaxResponsesPerUser());
            }
        }
        SurResponse response = new SurResponse();
        response.setId(IdGenerator.nextId());
        response.setSurveyId(surveyId);
        response.setResponseNo(generateResponseNo());
        response.setRespondentId(Boolean.TRUE.equals(survey.getAnonymous()) ? null : respondentId);
        response.setRespondentName(CurrentUserContext.getUsername());
        response.setStatus(SurResponse.STATUS_IN_PROGRESS);
        response.setSource(source == null ? SurSurvey.SOURCE_WEB_ADMIN : source);
        response.setStartTime(OffsetDateTime.now());
        // 填充 clientIp/userAgent (从 HttpServletRequest)
        fillClientInfo(response);
        try {
            responseMapper.insert(response);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_NO_DUPLICATE, "答卷编号已存在: " + response.getResponseNo());
        }
        log.info("startResponse: surveyId={} responseNo={}", surveyId, response.getResponseNo());
        return response;
    }

    /**
     * 提交答卷 (字段校验 + 条件显隐验证)。
     *
     * <p>核心流程:
     * <ol>
     *   <li>校验答卷存在 + 状态为 IN_PROGRESS (已提交抛 SUR-409004)</li>
     *   <li>查询问卷下所有题目 (按 sortNo 升序)</li>
     *   <li>构建 questionCode → answer 映射</li>
     *   <li>遍历题目, 对每道题执行: 条件显隐判断 + 必答校验 + 字段校验规则</li>
     *   <li>持久化 sur_answer 记录, 更新答卷状态 SUBMITTED + submittedTime + durationMs + totalScore</li>
     *   <li>更新问卷 response_count 冗余字段</li>
     * </ol>
     */
    @Transactional
    public SurResponse submitResponse(SubmitResponseRequest request) {
        SurSurvey survey = getSurvey(request.getSurveyId());
        if (!SurSurvey.STATUS_COLLECTING.equals(survey.getStatus())) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_STATUS_NOT_ALLOWED,
                    "仅 COLLECTING 状态可提交答卷, 当前: " + survey.getStatus());
        }
        // 由于 startResponse 是可选调用, 这里如果未启动则自动创建一份 IN_PROGRESS 答卷
        // (兼容前端直接调用 submitResponse 的场景)
        SurResponse response = new SurResponse();
        response.setId(IdGenerator.nextId());
        response.setSurveyId(request.getSurveyId());
        response.setResponseNo(generateResponseNo());
        response.setRespondentId(Boolean.TRUE.equals(survey.getAnonymous()) ? null : CurrentUserContext.getUserId());
        response.setRespondentName(CurrentUserContext.getUsername());
        response.setStatus(SurResponse.STATUS_IN_PROGRESS);
        response.setSource(request.getSource() == null ? SurSurvey.SOURCE_WEB_ADMIN : request.getSource());
        response.setStartTime(OffsetDateTime.now());
        fillClientInfo(response);
        response.setRemark(request.getRemark());

        // 加载问卷题目
        List<SurQuestion> questions = listQuestions(request.getSurveyId());
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCode.SUR_REQUEST_INVALID, "问卷未配置题目, 无法提交");
        }

        // 构建 questionCode → answer 映射
        Map<String, SubmitResponseRequest.AnswerItem> answerMap = new HashMap<>();
        for (SubmitResponseRequest.AnswerItem item : request.getAnswers()) {
            answerMap.put(item.getQuestionCode(), item);
        }

        // 遍历题目执行校验
        int totalScore = 0;
        List<SurAnswer> answers = new ArrayList<>();
        for (SurQuestion question : questions) {
            // 1. 条件显隐判断: 如果 logicJson 不为空, 判断是否被隐藏 (HIDE 命中即隐藏; SHOW 不命中即隐藏)
            boolean hidden = isQuestionHidden(question, answerMap);
            if (hidden) {
                // 隐藏的题目不参与校验和保存
                continue;
            }
            // 2. 判断是否必答 (logicJson 的 REQUIRE 可强制必答)
            boolean required = Boolean.TRUE.equals(question.getRequired()) || isQuestionRequired(question, answerMap);
            SubmitResponseRequest.AnswerItem answer = answerMap.get(question.getQuestionCode());
            if (required && (answer == null || isAnswerEmpty(answer))) {
                throw new BusinessException(ErrorCode.SUR_ANSWER_VALIDATION_FAILED,
                        "必答题未填写: " + question.getQuestionCode());
            }
            if (answer == null || isAnswerEmpty(answer)) {
                continue;
            }
            // 3. 字段校验规则
            validateAnswer(question, answer);
            // 4. 构建 SurAnswer 实体
            SurAnswer surAnswer = buildAnswer(response.getId(), survey.getId(), question, answer);
            if (surAnswer.getRatingScore() != null) {
                totalScore += surAnswer.getRatingScore();
            }
            answers.add(surAnswer);
        }

        // 5. 持久化答卷和答题
        response.setStatus(SurResponse.STATUS_SUBMITTED);
        OffsetDateTime now = OffsetDateTime.now();
        response.setSubmittedTime(now);
        response.setDurationMs(java.time.Duration.between(response.getStartTime(), now).toMillis());
        response.setTotalScore(totalScore);
        try {
            responseMapper.insert(response);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SUR_SURVEY_NO_DUPLICATE, "答卷编号已存在: " + response.getResponseNo());
        }
        for (SurAnswer answer : answers) {
            answerMapper.insert(answer);
        }

        // 6. 更新问卷 response_count 冗余字段
        survey.setResponseCount((survey.getResponseCount() == null ? 0 : survey.getResponseCount()) + 1);
        surveyMapper.updateById(survey);

        log.info("submitResponse: surveyId={} responseNo={} answerCount={} totalScore={}",
                survey.getId(), response.getResponseNo(), answers.size(), totalScore);
        return response;
    }

    public SurResponse getResponse(String id) {
        SurResponse response = responseMapper.selectById(id);
        if (response == null) {
            throw new ResourceNotFoundException(ErrorCode.SUR_RESPONSE_NOT_FOUND);
        }
        return response;
    }

    public List<SurAnswer> listAnswers(String responseId) {
        return answerMapper.selectList(new LambdaQueryWrapper<SurAnswer>()
                .eq(SurAnswer::getResponseId, responseId)
                .orderByAsc(SurAnswer::getQuestionCode));
    }

    public Page<SurResponse> pageResponses(int pageNo, int pageSize, String surveyId,
                                           String respondentId, String status) {
        Page<SurResponse> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SurResponse> wrapper = new LambdaQueryWrapper<SurResponse>()
                .orderByDesc(SurResponse::getSubmittedTime);
        if (surveyId != null && !surveyId.isBlank()) {
            wrapper.eq(SurResponse::getSurveyId, surveyId);
        }
        if (respondentId != null && !respondentId.isBlank()) {
            wrapper.eq(SurResponse::getRespondentId, respondentId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SurResponse::getStatus, status);
        }
        return responseMapper.selectPage(page, wrapper);
    }

    // ==================== 4. 问卷详情聚合 ====================

    /**
     * 问卷详情聚合 (问卷主表 + 题目列表)。
     * 前端"问卷设计器"和"问卷填写页"均使用此接口。
     */
    public Map<String, Object> getSurveyDetail(String id) {
        SurSurvey survey = getSurvey(id);
        List<SurQuestion> questions = listQuestions(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("survey", survey);
        detail.put("questions", questions);
        return detail;
    }

    // ==================== 5. 统计报表 ====================

    /**
     * 问卷监控统计 (仪表盘)。
     */
    public SurveyStatsVO getStats() {
        SurveyStatsVO vo = new SurveyStatsVO();
        // 1. 问卷总数 + 状态分布 + 分类分布
        List<SurSurvey> allSurveys = surveyMapper.selectList(null);
        vo.setTotalSurveys(allSurveys.size());
        Map<String, Long> statusCounts = new HashMap<>();
        Map<String, Long> categoryCounts = new HashMap<>();
        long collectingCount = 0;
        for (SurSurvey s : allSurveys) {
            statusCounts.merge(s.getStatus(), 1L, Long::sum);
            if (SurSurvey.STATUS_COLLECTING.equals(s.getStatus())) {
                collectingCount++;
            }
            if (s.getCategory() != null && !s.getCategory().isBlank()) {
                categoryCounts.merge(s.getCategory(), 1L, Long::sum);
            }
        }
        vo.setStatusCounts(statusCounts);
        vo.setCollectingCount(collectingCount);
        vo.setCategoryCounts(categoryCounts);

        // 2. 题目总数
        vo.setQuestionCount(questionMapper.selectCount(null));

        // 3. 答卷总数 + 状态分布 + 来源分布 + 平均时长 + 平均评分
        List<SurResponse> allResponses = responseMapper.selectList(null);
        vo.setTotalResponses(allResponses.size());
        Map<String, Long> sourceCounts = new HashMap<>();
        long submittedCount = 0;
        long inProgressCount = 0;
        long totalDurationMs = 0;
        long durationCount = 0;
        long totalScore = 0;
        long scoreCount = 0;
        Map<String, Long> dailySubmitCounts = new HashMap<>();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (SurResponse r : allResponses) {
            if (SurResponse.STATUS_SUBMITTED.equals(r.getStatus())) {
                submittedCount++;
                if (r.getSubmittedTime() != null) {
                    String day = r.getSubmittedTime().format(dayFmt);
                    dailySubmitCounts.merge(day, 1L, Long::sum);
                }
            } else if (SurResponse.STATUS_IN_PROGRESS.equals(r.getStatus())) {
                inProgressCount++;
            }
            if (r.getSource() != null) {
                sourceCounts.merge(r.getSource(), 1L, Long::sum);
            }
            if (r.getDurationMs() != null && r.getDurationMs() > 0) {
                totalDurationMs += r.getDurationMs();
                durationCount++;
            }
            if (r.getTotalScore() != null && r.getTotalScore() > 0) {
                totalScore += r.getTotalScore();
                scoreCount++;
            }
        }
        vo.setSubmittedCount(submittedCount);
        vo.setInProgressCount(inProgressCount);
        vo.setSourceCounts(sourceCounts);
        vo.setAvgDurationMs(durationCount == 0 ? 0 : totalDurationMs / durationCount);
        vo.setAvgScore(scoreCount == 0 ? 0.0 : (double) totalScore / scoreCount);

        // 4. 最近 7 日提交趋势
        List<SurveyStatsVO.DailyCount> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            String day = today.minusDays(i).toString();
            trend.add(new SurveyStatsVO.DailyCount(day, dailySubmitCounts.getOrDefault(day, 0L)));
        }
        vo.setRecentTrend(trend);
        return vo;
    }

    // ==================== 6. AI 生成题目草稿 ====================

    /**
     * AI 生成问卷题目草稿。
     *
     * <p>设计约束:
     * <ul>
     *   <li>AI 不直接写入数据库, 只返回草稿 JSON</li>
     *   <li>用户必须在前端预览并确认后, 通过 saveQuestion 接口写入</li>
     *   <li>调用 AI 服务时不在事务内 (本方法不加 @Transactional)</li>
     *   <li>第一版采用本地 mock 实现, 与 AiChatApplicationService 第一版 mock-model 风格一致</li>
     * </ul>
     *
     * <p>返回格式 (JSON 字符串):
     * <pre>
     * {
     *   "prompt": "用户提示词",
     *   "generatedAt": "2026-07-19T14:00:00+08:00",
     *   "questions": [
     *     {"questionCode":"Q001","questionType":"SINGLE_CHOICE","title":"...","optionsJson":"[...]"},
     *     ...
     *   ]
     * }
     * </pre>
     */
    public String generateAiDraft(AiDraftRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new BusinessException(ErrorCode.SUR_REQUEST_INVALID, "AI 草稿提示词不能为空");
        }
        int expectedCount = request.getExpectedCount() == null || request.getExpectedCount() <= 0
                ? 5 : Math.min(request.getExpectedCount(), 20);
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("prompt", request.getPrompt());
            root.put("generatedAt", OffsetDateTime.now().toString());
            root.put("modelCode", "mock-model");
            root.put("scenario", "SURVEY_QUESTION_GENERATE");
            ArrayNode questionsArray = objectMapper.createArrayNode();
            // mock: 根据提示词生成 expectedCount 道题, 覆盖 5 种核心题型
            String[] types = {SurQuestion.TYPE_SINGLE_CHOICE, SurQuestion.TYPE_MULTI_CHOICE,
                    SurQuestion.TYPE_RATING, SurQuestion.TYPE_TEXT, SurQuestion.TYPE_TEXTAREA};
            for (int i = 0; i < expectedCount; i++) {
                String qType = types[i % types.length];
                ObjectNode q = objectMapper.createObjectNode();
                q.put("questionCode", String.format("Q%03d", i + 1));
                q.put("questionType", qType);
                q.put("title", buildMockTitle(request.getPrompt(), qType, i + 1));
                q.put("description", "AI 生成 (基于提示词: " + truncate(request.getPrompt(), 80) + ")");
                q.put("required", i != expectedCount - 1); // 最后一题非必答, 演示 required 校验
                q.put("sortNo", i + 1);
                q.put("aiGenerated", true);
                // 选项/校验规则按题型填充
                if (SurQuestion.TYPE_SINGLE_CHOICE.equals(qType) || SurQuestion.TYPE_MULTI_CHOICE.equals(qType)) {
                    q.put("optionsJson", buildMockOptionsJson(qType));
                    if (SurQuestion.TYPE_MULTI_CHOICE.equals(qType)) {
                        q.put("validationJson", "{\"minSelect\":1,\"maxSelect\":3}");
                    }
                } else if (SurQuestion.TYPE_RATING.equals(qType)) {
                    q.put("optionsJson", buildMockRatingOptionsJson());
                    q.put("validationJson", "{\"minValue\":1,\"maxValue\":5}");
                } else if (SurQuestion.TYPE_TEXT.equals(qType)) {
                    q.put("validationJson", "{\"maxLength\":200}");
                } else if (SurQuestion.TYPE_TEXTAREA.equals(qType)) {
                    q.put("validationJson", "{\"maxLength\":500}");
                }
                questionsArray.add(q);
            }
            root.set("questions", questionsArray);
            // 简单模拟 AI 生成延迟 (50-150ms)
            try {
                Thread.sleep(50 + ThreadLocalRandom.current().nextInt(100));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            log.info("generateAiDraft: prompt={} expectedCount={} generated={}",
                    truncate(request.getPrompt(), 60), expectedCount, questionsArray.size());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.error("generateAiDraft failed: prompt={}", request.getPrompt(), e);
            throw new BusinessException(ErrorCode.SUR_AI_DRAFT_FAILED, "AI 草稿生成失败: " + e.getMessage());
        }
    }

    // ==================== 内部辅助方法 ====================

    private String generateSurveyNo() {
        return "SUR" + OffsetDateTime.now().format(NO_FMT)
                + String.format("%06d", System.nanoTime() % 1000000);
    }

    private String generateResponseNo() {
        return "RSP" + OffsetDateTime.now().format(NO_FMT)
                + String.format("%06d", System.nanoTime() % 1000000);
    }

    /** 从 HttpServletRequest 填充 clientIp/userAgent */
    private void fillClientInfo(SurResponse response) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                response.setClientIp(resolveClientIp(request));
                String ua = request.getHeader("User-Agent");
                response.setUserAgent(ua == null ? null : (ua.length() > 512 ? ua.substring(0, 512) : ua));
            }
        } catch (Exception e) {
            log.debug("fillClientInfo skipped (non-web context): {}", e.getMessage());
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /** 校验题目类型合法 */
    private void validateQuestionType(String type) {
        if (type == null) {
            throw new BusinessException(ErrorCode.SUR_QUESTION_OPTION_INVALID, "题目类型不能为空");
        }
        switch (type) {
            case SurQuestion.TYPE_SINGLE_CHOICE:
            case SurQuestion.TYPE_MULTI_CHOICE:
            case SurQuestion.TYPE_TEXT:
            case SurQuestion.TYPE_TEXTAREA:
            case SurQuestion.TYPE_RATING:
            case SurQuestion.TYPE_DATE:
            case SurQuestion.TYPE_MATRIX:
                break;
            default:
                throw new BusinessException(ErrorCode.SUR_QUESTION_OPTION_INVALID, "不支持的题目类型: " + type);
        }
    }

    /** 校验选项/矩阵配置 (选项题必须有 optionsJson) */
    private void validateQuestionOptions(String type, String optionsJson, String matrixJson) {
        if (SurQuestion.TYPE_SINGLE_CHOICE.equals(type) || SurQuestion.TYPE_MULTI_CHOICE.equals(type)
                || SurQuestion.TYPE_RATING.equals(type)) {
            if (optionsJson == null || optionsJson.isBlank()) {
                throw new BusinessException(ErrorCode.SUR_QUESTION_OPTION_INVALID,
                        type + " 类型题目必须配置 optionsJson");
            }
            try {
                JsonNode opts = objectMapper.readTree(optionsJson);
                if (!opts.isArray() || opts.isEmpty()) {
                    throw new BusinessException(ErrorCode.SUR_QUESTION_OPTION_INVALID,
                            type + " 类型题目 optionsJson 必须是非空数组");
                }
            } catch (BusinessException be) {
                throw be;
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.SUR_QUESTION_OPTION_INVALID,
                        "optionsJson 不是合法 JSON: " + e.getMessage());
            }
        }
        if (SurQuestion.TYPE_MATRIX.equals(type)) {
            if (matrixJson == null || matrixJson.isBlank()) {
                throw new BusinessException(ErrorCode.SUR_QUESTION_OPTION_INVALID,
                        "MATRIX 类型题目必须配置 matrixJson");
            }
        }
    }

    /** 校验条件显隐规则格式 */
    private void validateLogicRule(String logicJson) {
        if (logicJson == null || logicJson.isBlank()) {
            return;
        }
        try {
            JsonNode logic = objectMapper.readTree(logicJson);
            if (!logic.isArray()) {
                throw new BusinessException(ErrorCode.SUR_LOGIC_RULE_INVALID,
                        "logicJson 必须是数组");
            }
            for (JsonNode rule : logic) {
                String action = rule.path("action").asText("");
                if (!SurQuestion.LOGIC_ACTION_SHOW.equals(action)
                        && !SurQuestion.LOGIC_ACTION_HIDE.equals(action)
                        && !SurQuestion.LOGIC_ACTION_REQUIRE.equals(action)) {
                    throw new BusinessException(ErrorCode.SUR_LOGIC_RULE_INVALID,
                            "不支持的 action: " + action);
                }
                if (rule.path("questionCode").asText("").isBlank()) {
                    throw new BusinessException(ErrorCode.SUR_LOGIC_RULE_INVALID,
                            "logicJson 中 questionCode 不能为空");
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SUR_LOGIC_RULE_INVALID,
                    "logicJson 不是合法 JSON: " + e.getMessage());
        }
    }

    /**
     * 判断题目是否被隐藏。
     *
     * <p>规则语义 (与前端 isQuestionHiddenByLogic 对齐):
     * <ul>
     *   <li>HIDE 规则: 条件满足 → 隐藏 (任一 HIDE 命中即隐藏)</li>
     *   <li>SHOW 规则: 条件不满足 → 隐藏 (存在 SHOW 规则但无一命中即隐藏)</li>
     *   <li>HIDE 优先级高于 SHOW (同时存在时, HIDE 命中即隐藏, 不再判断 SHOW)</li>
     *   <li>无任何规则 → 不隐藏</li>
     * </ul>
     */
    private boolean isQuestionHidden(SurQuestion question,
                                     Map<String, SubmitResponseRequest.AnswerItem> answerMap) {
        if (question.getLogicJson() == null || question.getLogicJson().isBlank()) {
            return false;
        }
        try {
            JsonNode logic = objectMapper.readTree(question.getLogicJson());
            boolean hasShowRule = false;
            boolean anyShowMatched = false;
            for (JsonNode rule : logic) {
                String action = rule.path("action").asText("");
                if (SurQuestion.LOGIC_ACTION_HIDE.equals(action)) {
                    // HIDE 命中即隐藏 (优先级最高)
                    if (evaluateLogicRule(rule, answerMap)) {
                        return true;
                    }
                } else if (SurQuestion.LOGIC_ACTION_SHOW.equals(action)) {
                    hasShowRule = true;
                    if (evaluateLogicRule(rule, answerMap)) {
                        anyShowMatched = true;
                    }
                }
            }
            // 存在 SHOW 规则但无一命中 → 隐藏 (SHOW 条件未满足)
            return hasShowRule && !anyShowMatched;
        } catch (Exception e) {
            log.warn("isQuestionHidden parse logic failed: questionCode={}", question.getQuestionCode(), e);
        }
        return false;
    }

    /** 判断题目是否被强制必答 (logicJson 中存在 action=REQUIRE 且条件满足) */
    private boolean isQuestionRequired(SurQuestion question,
                                       Map<String, SubmitResponseRequest.AnswerItem> answerMap) {
        if (question.getLogicJson() == null || question.getLogicJson().isBlank()) {
            return false;
        }
        try {
            JsonNode logic = objectMapper.readTree(question.getLogicJson());
            for (JsonNode rule : logic) {
                String action = rule.path("action").asText("");
                if (!SurQuestion.LOGIC_ACTION_REQUIRE.equals(action)) {
                    continue;
                }
                if (evaluateLogicRule(rule, answerMap)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("isQuestionRequired parse logic failed: questionCode={}", question.getQuestionCode(), e);
        }
        return false;
    }

    /**
     * 计算单条 logic 规则是否满足。
     * 支持的 operator: EQ/NE/IN/NOT_IN/CONTAINS/GT/GTE/LT/LTE
     */
    private boolean evaluateLogicRule(JsonNode rule,
                                      Map<String, SubmitResponseRequest.AnswerItem> answerMap) {
        String questionCode = rule.path("questionCode").asText("");
        String operator = rule.path("operator").asText("");
        JsonNode expectedValue = rule.path("value");
        SubmitResponseRequest.AnswerItem depAnswer = answerMap.get(questionCode);
        if (depAnswer == null || isAnswerEmpty(depAnswer)) {
            return false;
        }
        try {
            // 取依赖题目的实际值 (优先 ratingScore, 其次 selectedOptions, 最后 answerValue)
            JsonNode actualValue = null;
            if (depAnswer.getRatingScore() != null) {
                actualValue = objectMapper.valueToTree(depAnswer.getRatingScore());
            } else if (depAnswer.getSelectedOptions() != null && !depAnswer.getSelectedOptions().isBlank()) {
                actualValue = objectMapper.readTree(depAnswer.getSelectedOptions());
            } else if (depAnswer.getAnswerValue() != null && !depAnswer.getAnswerValue().isBlank()) {
                actualValue = objectMapper.readTree(depAnswer.getAnswerValue());
            }
            return compareValues(operator, actualValue, expectedValue);
        } catch (Exception e) {
            log.warn("evaluateLogicRule failed: questionCode={} operator={}", questionCode, operator, e);
            return false;
        }
    }

    /** 比较实际值与期望值 (按 operator) */
    private boolean compareValues(String operator, JsonNode actual, JsonNode expected) {
        if (actual == null) {
            return false;
        }
        switch (operator) {
            case SurQuestion.LOGIC_OP_EQ:
                return jsonEquals(actual, expected);
            case SurQuestion.LOGIC_OP_NE:
                return !jsonEquals(actual, expected);
            case SurQuestion.LOGIC_OP_IN:
                if (expected == null || !expected.isArray()) {
                    return false;
                }
                for (JsonNode item : expected) {
                    if (jsonEquals(actual, item)) {
                        return true;
                    }
                }
                return false;
            case SurQuestion.LOGIC_OP_NOT_IN:
                if (expected == null || !expected.isArray()) {
                    return true;
                }
                for (JsonNode item : expected) {
                    if (jsonEquals(actual, item)) {
                        return false;
                    }
                }
                return true;
            case SurQuestion.LOGIC_OP_CONTAINS:
                if (actual.isArray()) {
                    for (JsonNode item : actual) {
                        if (jsonEquals(item, expected)) {
                            return true;
                        }
                    }
                }
                return false;
            case SurQuestion.LOGIC_OP_GT:
                return numericCompare(actual, expected) > 0;
            case SurQuestion.LOGIC_OP_GTE:
                return numericCompare(actual, expected) >= 0;
            case SurQuestion.LOGIC_OP_LT:
                return numericCompare(actual, expected) < 0;
            case SurQuestion.LOGIC_OP_LTE:
                return numericCompare(actual, expected) <= 0;
            default:
                log.warn("unsupported operator: {}", operator);
                return false;
        }
    }

    private boolean jsonEquals(JsonNode a, JsonNode b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        // 数组 vs 标量: 检查数组是否包含该标量 (用于 SINGLE_CHOICE/MULTI_CHOICE 的 selectedOptions 比较)
        if (a.isArray() && !b.isArray()) {
            for (JsonNode elem : a) {
                if (elem.equals(b)) {
                    return true;
                }
            }
            return false;
        }
        if (b.isArray() && !a.isArray()) {
            for (JsonNode elem : b) {
                if (elem.equals(a)) {
                    return true;
                }
            }
            return false;
        }
        return a.equals(b);
    }

    /** 数值比较, 返回 -1/0/1, 无法比较时返回 Integer.MIN_VALUE */
    private int numericCompare(JsonNode actual, JsonNode expected) {
        if (actual == null || expected == null) {
            return Integer.MIN_VALUE;
        }
        try {
            double a = actual.asDouble();
            double b = expected.asDouble();
            return Double.compare(a, b);
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    /** 判断答案是否为空 */
    private boolean isAnswerEmpty(SubmitResponseRequest.AnswerItem answer) {
        if (answer == null) {
            return true;
        }
        if (answer.getRatingScore() != null) {
            return false;
        }
        if (answer.getSelectedOptions() != null && !answer.getSelectedOptions().isBlank()
                && !"[]".equals(answer.getSelectedOptions())) {
            return false;
        }
        if (answer.getAnswerValue() != null && !answer.getAnswerValue().isBlank()
                && !"\"\"".equals(answer.getAnswerValue()) && !"null".equals(answer.getAnswerValue())) {
            return false;
        }
        if (answer.getAnswerText() != null && !answer.getAnswerText().isBlank()) {
            return false;
        }
        return true;
    }

    /**
     * 字段校验规则。设计来源: 35 号文档 validation_json 格式。
     * 支持: minLength/maxLength/minValue/maxValue/regex/minSelect/maxSelect
     */
    private void validateAnswer(SurQuestion question, SubmitResponseRequest.AnswerItem answer) {
        if (question.getValidationJson() == null || question.getValidationJson().isBlank()) {
            return;
        }
        try {
            JsonNode validation = objectMapper.readTree(question.getValidationJson());
            String qType = question.getQuestionType();
            // 文本类题型: 校验长度/正则
            if (SurQuestion.TYPE_TEXT.equals(qType) || SurQuestion.TYPE_TEXTAREA.equals(qType)) {
                String text = answer.getAnswerText();
                if (text == null && answer.getAnswerValue() != null) {
                    // 兼容 answerValue 存文本的场景
                    try {
                        JsonNode v = objectMapper.readTree(answer.getAnswerValue());
                        if (v.isTextual()) {
                            text = v.asText();
                        }
                    } catch (Exception ignore) {
                        text = answer.getAnswerValue();
                    }
                }
                if (text != null) {
                    int len = text.length();
                    if (validation.has("minLength") && len < validation.get("minLength").asInt()) {
                        throw new BusinessException(ErrorCode.SUR_ANSWER_VALIDATION_FAILED,
                                question.getQuestionCode() + " 答案长度不能少于 " + validation.get("minLength").asInt());
                    }
                    if (validation.has("maxLength") && len > validation.get("maxLength").asInt()) {
                        throw new BusinessException(ErrorCode.SUR_ANSWER_VALIDATION_FAILED,
                                question.getQuestionCode() + " 答案长度不能超过 " + validation.get("maxLength").asInt());
                    }
                    if (validation.has("regex")) {
                        String regex = validation.get("regex").asText();
                        if (!text.matches(regex)) {
                            throw new BusinessException(ErrorCode.SUR_ANSWER_VALIDATION_FAILED,
                                    question.getQuestionCode() + " 答案格式不符合要求: " + regex);
                        }
                    }
                }
            }
            // 评分题: 校验 minValue/maxValue
            if (SurQuestion.TYPE_RATING.equals(qType) && answer.getRatingScore() != null) {
                int score = answer.getRatingScore();
                if (validation.has("minValue") && score < validation.get("minValue").asInt()) {
                    throw new BusinessException(ErrorCode.SUR_ANSWER_VALIDATION_FAILED,
                            question.getQuestionCode() + " 评分不能少于 " + validation.get("minValue").asInt());
                }
                if (validation.has("maxValue") && score > validation.get("maxValue").asInt()) {
                    throw new BusinessException(ErrorCode.SUR_ANSWER_VALIDATION_FAILED,
                            question.getQuestionCode() + " 评分不能超过 " + validation.get("maxValue").asInt());
                }
            }
            // 多选题: 校验 minSelect/maxSelect
            if (SurQuestion.TYPE_MULTI_CHOICE.equals(qType) && answer.getSelectedOptions() != null) {
                try {
                    JsonNode opts = objectMapper.readTree(answer.getSelectedOptions());
                    if (opts.isArray()) {
                        int size = opts.size();
                        if (validation.has("minSelect") && size < validation.get("minSelect").asInt()) {
                            throw new BusinessException(ErrorCode.SUR_ANSWER_VALIDATION_FAILED,
                                    question.getQuestionCode() + " 至少选择 " + validation.get("minSelect").asInt() + " 项");
                        }
                        if (validation.has("maxSelect") && size > validation.get("maxSelect").asInt()) {
                            throw new BusinessException(ErrorCode.SUR_ANSWER_VALIDATION_FAILED,
                                    question.getQuestionCode() + " 最多选择 " + validation.get("maxSelect").asInt() + " 项");
                        }
                    }
                } catch (BusinessException be) {
                    throw be;
                } catch (Exception e) {
                    log.warn("validateAnswer multi_choice parse selectedOptions failed: questionCode={}",
                            question.getQuestionCode(), e);
                }
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.warn("validateAnswer parse validation failed: questionCode={}", question.getQuestionCode(), e);
        }
    }

    /** 构建 SurAnswer 实体 */
    private SurAnswer buildAnswer(String responseId, String surveyId, SurQuestion question,
                                  SubmitResponseRequest.AnswerItem answer) {
        SurAnswer surAnswer = new SurAnswer();
        surAnswer.setId(IdGenerator.nextId());
        surAnswer.setResponseId(responseId);
        surAnswer.setSurveyId(surveyId);
        surAnswer.setQuestionId(question.getId());
        surAnswer.setQuestionCode(question.getQuestionCode());
        surAnswer.setQuestionType(question.getQuestionType());
        surAnswer.setAnswerValue(answer.getAnswerValue());
        surAnswer.setAnswerText(answer.getAnswerText());
        surAnswer.setSelectedOptions(answer.getSelectedOptions());
        surAnswer.setRatingScore(answer.getRatingScore());
        surAnswer.setDurationMs(answer.getDurationMs());
        return surAnswer;
    }

    // ==================== AI 草稿 mock 辅助 ====================

    private String buildMockTitle(String prompt, String qType, int idx) {
        String topic = truncate(prompt, 30);
        switch (qType) {
            case SurQuestion.TYPE_SINGLE_CHOICE:
                return "Q" + idx + " 您对" + topic + "的整体评价是?";
            case SurQuestion.TYPE_MULTI_CHOICE:
                return "Q" + idx + " 您认为" + topic + "哪些方面需要改进? (可多选)";
            case SurQuestion.TYPE_RATING:
                return "Q" + idx + " 请为" + topic + "评分 (1-5 分)";
            case SurQuestion.TYPE_TEXT:
                return "Q" + idx + " 您对" + topic + "有什么具体建议? (200 字以内)";
            case SurQuestion.TYPE_TEXTAREA:
                return "Q" + idx + " 请描述您在使用" + topic + "过程中遇到的问题";
            default:
                return "Q" + idx + " 题目";
        }
    }

    private String buildMockOptionsJson(String qType) {
        if (SurQuestion.TYPE_SINGLE_CHOICE.equals(qType)) {
            return "[{\"code\":\"OPT_A\",\"label\":\"非常满意\",\"value\":\"A\",\"sortNo\":1},"
                    + "{\"code\":\"OPT_B\",\"label\":\"满意\",\"value\":\"B\",\"sortNo\":2},"
                    + "{\"code\":\"OPT_C\",\"label\":\"一般\",\"value\":\"C\",\"sortNo\":3},"
                    + "{\"code\":\"OPT_D\",\"label\":\"不满意\",\"value\":\"D\",\"sortNo\":4}]";
        }
        // MULTI_CHOICE
        return "[{\"code\":\"OPT_A\",\"label\":\"响应速度\",\"value\":\"A\",\"sortNo\":1},"
                + "{\"code\":\"OPT_B\",\"label\":\"技术专业\",\"value\":\"B\",\"sortNo\":2},"
                + "{\"code\":\"OPT_C\",\"label\":\"态度友好\",\"value\":\"C\",\"sortNo\":3},"
                + "{\"code\":\"OPT_D\",\"label\":\"问题解决\",\"value\":\"D\",\"sortNo\":4},"
                + "{\"code\":\"OPT_E\",\"label\":\"文档完善\",\"value\":\"E\",\"sortNo\":5}]";
    }

    private String buildMockRatingOptionsJson() {
        return "[{\"code\":\"OPT_1\",\"label\":\"1 分\",\"value\":1,\"sortNo\":1},"
                + "{\"code\":\"OPT_2\",\"label\":\"2 分\",\"value\":2,\"sortNo\":2},"
                + "{\"code\":\"OPT_3\",\"label\":\"3 分\",\"value\":3,\"sortNo\":3},"
                + "{\"code\":\"OPT_4\",\"label\":\"4 分\",\"value\":4,\"sortNo\":4},"
                + "{\"code\":\"OPT_5\",\"label\":\"5 分\",\"value\":5,\"sortNo\":5}]";
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
