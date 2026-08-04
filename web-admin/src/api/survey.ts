import service from './request'
import type { InvPage } from './types'

/**
 * 问卷表单 API。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)
 * 后端: SurveyOpsController @RequestMapping("/api/v1/surveys")
 *
 * 核心 6 项能力:
 *  1. 动态表单渲染 (GET /surveys + GET /surveys/{id}/detail 含题目列表)
 *  2. 条件显隐 (logic_json, 9 种 operator + 3 种 action)
 *  3. 字段校验 (validation_json, minLength/maxLength/regex/minValue/maxValue/minSelect/maxSelect)
 *  4. 移动端填写 (POST /surveys/responses/start + submit, source 字段标识来源)
 *  5. 统计报表 (GET /surveys/stats 多维聚合 + 7 日趋势)
 *  6. AI 生成题目草稿 (POST /surveys/ai-draft mock-model, 不直接写库)
 */

// ==================== 类型定义 ====================

/** 问卷主表 (对应后端 SurSurvey domain) */
export interface SurSurvey {
  id: string
  surveyNo: string
  title: string
  description?: string
  /** DRAFT / PUBLISHED / COLLECTING / CLOSED / ARCHIVED */
  status: string
  category?: string
  anonymous?: boolean
  maxResponsesPerUser?: number
  startTime?: string
  endTime?: string
  publishedTime?: string
  closedTime?: string
  responseCount?: number
  themeJson?: string
  aiDraftPrompt?: string
  tenantId?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 题目 (对应后端 SurQuestion domain) */
export interface SurQuestion {
  id: string
  surveyId: string
  /** Q001 / Q002 / ... */
  questionCode: string
  /** SINGLE_CHOICE / MULTI_CHOICE / TEXT / TEXTAREA / RATING / DATE / MATRIX */
  questionType: string
  title: string
  description?: string
  required?: boolean
  sortNo?: number
  /** 选项 JSON 字符串, 例如 [{"code":"A","label":"选项 A"}] */
  optionsJson?: string
  /** 校验规则 JSON, 例如 {"minLength":10,"maxLength":500,"regex":"^1\\d{10}$"} */
  validationJson?: string
  /** 条件显隐规则 JSON, 例如 [{"questionCode":"Q001","operator":"EQ","value":"A","action":"SHOW"}] */
  logicJson?: string
  /** 矩阵题行列配置 JSON */
  matrixJson?: string
  aiGenerated?: boolean
  tenantId?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 答卷 (对应后端 SurResponse domain) */
export interface SurResponse {
  id: string
  surveyId: string
  surveyNo?: string
  responseNo: string
  /** IN_PROGRESS / SUBMITTED / ABANDONED */
  status: string
  respondentId?: string
  /** WEB_ADMIN / MOBILE_UNIAPP / API */
  source?: string
  clientIp?: string
  userAgent?: string
  startedTime?: string
  submittedTime?: string
  durationMs?: number
  totalScore?: number
  tenantId?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 答题 (对应后端 SurAnswer domain) */
export interface SurAnswer {
  id: string
  responseId: string
  surveyId: string
  questionId: string
  questionCode: string
  /** 答案值 JSON 字符串 */
  answerValue?: string
  answerText?: string
  selectedOptions?: string
  ratingScore?: number
  durationMs?: number
  tenantId?: string
  createdBy?: string
  createdTime?: string
  version?: number
  remark?: string
}

/** 问卷监控统计 VO (对应后端 SurveyStatsVO) */
export interface SurveyStatsVO {
  totalSurveys?: number
  statusCounts?: Record<string, number>
  collectingCount?: number
  totalResponses?: number
  submittedCount?: number
  inProgressCount?: number
  questionCount?: number
  avgDurationMs?: number
  avgScore?: number
  sourceCounts?: Record<string, number>
  categoryCounts?: Record<string, number>
  recentTrend?: DailyCount[]
}

export interface DailyCount {
  date: string
  count: number
}

/** 问卷详情聚合 (GET /surveys/{id}/detail 返回) */
export interface SurveyDetailVO {
  survey: SurSurvey
  questions: SurQuestion[]
}

/** 创建/更新问卷请求 */
export interface SaveSurveyRequest {
  title: string
  description?: string
  category?: string
  anonymous?: boolean
  maxResponsesPerUser?: number
  startTime?: string
  endTime?: string
  themeJson?: string
  remark?: string
}

/** 创建/更新题目请求 */
export interface SaveQuestionRequest {
  questionCode: string
  questionType: string
  title: string
  description?: string
  required?: boolean
  sortNo?: number
  optionsJson?: string
  validationJson?: string
  logicJson?: string
  matrixJson?: string
  remark?: string
}

/** 提交答卷请求 */
export interface SubmitResponseRequest {
  surveyId: string
  source?: string
  answers: AnswerItem[]
  remark?: string
}

export interface AnswerItem {
  questionId: string
  questionCode: string
  answerValue?: string
  answerText?: string
  selectedOptions?: string
  ratingScore?: number
  durationMs?: number
}

/** AI 草稿生成请求 */
export interface AiDraftRequest {
  prompt: string
  expectedCount?: number
}

// ==================== 问卷 (状态机) ====================

/** 分页查询问卷 */
export function pageSurveys(params: {
  pageNo?: number
  pageSize?: number
  surveyNo?: string
  title?: string
  status?: string
  category?: string
}): Promise<InvPage<SurSurvey>> {
  return service.get('/surveys', { params }) as unknown as Promise<InvPage<SurSurvey>>
}

/** 查询问卷详情 */
export function getSurvey(id: string): Promise<SurSurvey> {
  return service.get(`/surveys/${id}`) as unknown as Promise<SurSurvey>
}

/** 问卷详情聚合 (问卷 + 题目列表, 用于问卷设计器/填写页) */
export function getSurveyDetail(id: string): Promise<SurveyDetailVO> {
  return service.get(`/surveys/${id}/detail`) as unknown as Promise<SurveyDetailVO>
}

/** 创建问卷 (初始 DRAFT 状态) */
export function createSurvey(data: SaveSurveyRequest): Promise<SurSurvey> {
  return service.post('/surveys', data) as unknown as Promise<SurSurvey>
}

/** 更新问卷 (DRAFT 可全字段修改; COLLECTING/CLOSED 仅描述/主题/备注; ARCHIVED 只读) */
export function updateSurvey(id: string, data: SaveSurveyRequest): Promise<SurSurvey> {
  return service.put(`/surveys/${id}`, data) as unknown as Promise<SurSurvey>
}

/** 发布问卷 (DRAFT → PUBLISHED, 至少 1 道题目) */
export function publishSurvey(id: string): Promise<SurSurvey> {
  return service.post(`/surveys/${id}/publish`) as unknown as Promise<SurSurvey>
}

/** 开始收集答卷 (PUBLISHED/CLOSED → COLLECTING) */
export function startCollecting(id: string): Promise<SurSurvey> {
  return service.post(`/surveys/${id}/start`) as unknown as Promise<SurSurvey>
}

/** 关闭收集 (COLLECTING → CLOSED) */
export function closeSurvey(id: string): Promise<SurSurvey> {
  return service.post(`/surveys/${id}/close`) as unknown as Promise<SurSurvey>
}

/** 归档问卷 (DRAFT/CLOSED → ARCHIVED, 只读) */
export function archiveSurvey(id: string): Promise<SurSurvey> {
  return service.post(`/surveys/${id}/archive`) as unknown as Promise<SurSurvey>
}

// ==================== 题目 CRUD ====================

/** 查询问卷下所有题目 (按 sortNo 升序) */
export function listQuestions(surveyId: string): Promise<SurQuestion[]> {
  return service.get(`/surveys/${surveyId}/questions`) as unknown as Promise<SurQuestion[]>
}

/** 新增题目 (仅 DRAFT 状态可新增) */
export function saveQuestion(surveyId: string, data: SaveQuestionRequest): Promise<SurQuestion> {
  return service.post(`/surveys/${surveyId}/questions`, data) as unknown as Promise<SurQuestion>
}

/** 更新题目 (仅 DRAFT 状态可修改) */
export function updateQuestion(
  surveyId: string,
  questionId: string,
  data: SaveQuestionRequest,
): Promise<SurQuestion> {
  return service.put(`/surveys/${surveyId}/questions/${questionId}`, data) as unknown as Promise<SurQuestion>
}

/** 删除题目 (仅 DRAFT 状态可删除) */
export function deleteQuestion(surveyId: string, questionId: string): Promise<void> {
  return service.delete(`/surveys/${surveyId}/questions/${questionId}`) as unknown as Promise<void>
}

// ==================== 答卷 (移动端 + Web 同一接口) ====================

/** 启动答卷 (创建 IN_PROGRESS 记录, 校验填写次数限制) */
export function startResponse(surveyId: string, source?: string): Promise<SurResponse> {
  return service.post('/surveys/responses/start', null, {
    params: { surveyId, source: source || 'WEB_ADMIN' },
  }) as unknown as Promise<SurResponse>
}

/** 提交答卷 (字段校验 + 条件显隐验证, 兼容未启动直接提交) */
export function submitResponse(data: SubmitResponseRequest): Promise<SurResponse> {
  return service.post('/surveys/responses/submit', data) as unknown as Promise<SurResponse>
}

/** 查询答卷详情 */
export function getResponse(id: string): Promise<SurResponse> {
  return service.get(`/surveys/responses/${id}`) as unknown as Promise<SurResponse>
}

/** 查询答卷下所有答题 (按 questionCode 升序) */
export function listAnswers(responseId: string): Promise<SurAnswer[]> {
  return service.get(`/surveys/responses/${responseId}/answers`) as unknown as Promise<SurAnswer[]>
}

/** 分页查询答卷 */
export function pageResponses(params: {
  pageNo?: number
  pageSize?: number
  surveyId?: string
  respondentId?: string
  status?: string
}): Promise<InvPage<SurResponse>> {
  return service.get('/surveys/responses', { params }) as unknown as Promise<InvPage<SurResponse>>
}

// ==================== 统计报表 ====================

/** 问卷监控统计 (问卷/状态/答卷/来源/分类/趋势多维聚合) */
export function getSurveyStats(): Promise<SurveyStatsVO> {
  return service.get('/surveys/stats') as unknown as Promise<SurveyStatsVO>
}

// ==================== AI 生成题目草稿 ====================

/**
 * AI 生成问卷题目草稿 (mock-model, 不直接写库, 返回草稿 JSON 字符串)
 * 返回值: JSON 字符串, 解析后为 SaveQuestionRequest[] 数组
 */
export function generateAiDraft(data: AiDraftRequest): Promise<string> {
  return service.post('/surveys/ai-draft', data) as unknown as Promise<string>
}
