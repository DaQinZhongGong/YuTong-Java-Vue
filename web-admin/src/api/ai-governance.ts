/**
 * AI 治理 API。设计来源: 37-AI治理与评测设计 (GA2-45 v1.0)
 *
 * 21 个端点覆盖 5 大能力域:
 *   1. Prompt 治理 (5 端点: 分页/详情/保存草稿/发布/禁用)
 *   2. AI 工具注册 (5 端点: 分页/详情/保存/启停/校验)
 *   3. 成本治理 (2 端点: 分页/保存)
 *   4. 反馈闭环 (3 端点: 分页/提交/处理)
 *   5. RAG 评测 (5 端点: 样本集分页/运行分页/运行详情/触发运行/结果分页)
 *   6. 监控统计 (2 端点: 治理统计 + 用量日报 M-2)
 */
import service from './request'
import type { PageResult } from './types'

// ==================== 类型定义 ====================

export interface AiPromptTemplate {
  id?: string
  templateCode: string
  versionNo?: number
  scenario: string
  inputSchema?: string
  outputSchema?: string
  promptText: string
  safetyRules?: string
  evaluationSetCode?: string
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED'
  publishedTime?: string
  publishedBy?: string
  version?: number
  remark?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
}

export interface AiToolRegistry {
  id?: string
  toolName: string
  toolVersion?: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  aiCapabilityLevel: 'A0' | 'A1' | 'A2' | 'A3' | 'A4'
  permissionCode?: string
  description?: string
  inputSchema?: string
  outputSchema?: string
  isReadonly?: boolean
  needsHumanReview?: boolean
  accessBusinessData?: boolean
  dataScopeStrategy?: string
  fieldMaskingStrategy?: string
  maxResults?: number
  timeoutMs?: number
  rateLimitPerMin?: number
  isForbidden?: boolean
  forbiddenReason?: string
  enabled?: boolean
  ownerUserId?: string
  version?: number
}

export interface AiCostQuota {
  id?: string
  quotaScope: 'TENANT' | 'USER' | 'SCENARIO'
  scopeKey: string
  modelCode?: string
  dailyTokenLimit?: number
  dailyCostLimit?: number
  singleCallTokenLimit?: number
  currency?: string
  enabled?: boolean
  effectiveFrom?: string
  effectiveTo?: string
  version?: number
}

export interface AiFeedback {
  id?: string
  feedbackType: 'HELPFUL' | 'NOT_HELPFUL' | 'INACCURATE' | 'CITATION_ERROR' | 'FORMAT_ERROR' | 'RISKY'
  targetType?: string
  targetId?: string
  conversationId?: string
  messageId?: string
  userId?: string
  scenario?: string
  modelCode?: string
  rating?: number
  tagsJson?: string
  commentText?: string
  traceId?: string
  handled?: boolean
  handledBy?: string
  handledTime?: string
  handleResult?: string
  createdTime?: string
}

export interface AiEvalDataset {
  id?: string
  caseId: string
  scenario: string
  locale?: string
  question: string
  expectedAnswerPointsJson?: string
  expectedSourcesJson?: string
  forbiddenSourcesJson?: string
  forbiddenToolsJson?: string
  permissionContextJson?: string
  expectedSchemaJson?: string
  forbiddenFieldsJson?: string
  riskTagsJson?: string
  expectedRefusal?: boolean
  expectedRefusalReason?: string
  expectedErrorCode?: string
  assertionsJson?: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  enabled?: boolean
}

export interface AiEvalRun {
  id?: string
  runNo: string
  appVersion?: string
  promptVersion?: string
  modelRouteVersion?: string
  kbVersion?: string
  datasetFilter?: string
  totalCases?: number
  passedCases?: number
  failedCases?: number
  recallAtK?: number
  answerAccuracy?: number
  citationAccuracy?: number
  refusalAccuracy?: number
  aclPrecision?: number
  citationLeakageRate?: number
  avgLatencyMs?: number
  avgCostAmount?: number
  forbiddenToolBlockRate?: number
  dangerousSqlBlockRate?: number
  releaseDecision: 'PENDING' | 'PASSED' | 'CONDITIONAL' | 'REJECTED'
  releaseNote?: string
  triggeredBy?: string
  startedTime?: string
  finishedTime?: string
}

export interface AiEvalResult {
  id?: string
  runId: string
  caseId: string
  scenario: string
  actualAnswer?: string
  actualSourcesJson?: string
  actualToolsJson?: string
  isRefused?: boolean
  refusalReason?: string
  isPassed?: boolean
  failureReason?: string
  latencyMs?: number
  costAmount?: number
  tokenInput?: number
  tokenOutput?: number
  errorCode?: string
  traceId?: string
}

export interface AiGovernanceStats {
  totalPrompts?: number
  publishedPrompts?: number
  draftPrompts?: number
  totalTools?: number
  enabledTools?: number
  forbiddenTools?: number
  todayTotalTokens?: number
  todayTotalCost?: number
  todayCallCount?: number
  totalFeedbacks?: number
  helpfulFeedbacks?: number
  riskyFeedbacks?: number
  unhandledFeedbacks?: number
  totalEvalCases?: number
  enabledEvalCases?: number
  totalEvalRuns?: number
  passedEvalRuns?: number
  failedEvalRuns?: number
  latestRunDecision?: string
  latestRunNo?: string
}

export interface SaveToolRequest {
  id?: string
  toolName: string
  toolVersion?: string
  riskLevel: string
  aiCapabilityLevel: string
  permissionCode?: string
  description?: string
  inputSchema?: string
  outputSchema?: string
  isReadonly?: boolean
  needsHumanReview?: boolean
  accessBusinessData?: boolean
  dataScopeStrategy?: string
  fieldMaskingStrategy?: string
  maxResults?: number
  timeoutMs?: number
  rateLimitPerMin?: number
  isForbidden?: boolean
  forbiddenReason?: string
  enabled?: boolean
  ownerUserId?: string
  version?: number
}

export interface SaveQuotaRequest {
  id?: string
  quotaScope: string
  scopeKey: string
  modelCode?: string
  dailyTokenLimit?: number
  dailyCostLimit?: number
  singleCallTokenLimit?: number
  currency?: string
  enabled?: boolean
  version?: number
}

export interface SubmitFeedbackRequest {
  feedbackType: string
  targetType?: string
  targetId?: string
  conversationId?: string
  messageId?: string
  scenario?: string
  modelCode?: string
  rating?: number
  tagsJson?: string
  commentText?: string
}

export interface TriggerEvalRunRequest {
  appVersion?: string
  promptVersion?: string
  modelRouteVersion?: string
  kbVersion?: string
  datasetFilter?: string
}

// ==================== API 函数 ====================

const BASE = '/ai-governance'

// 1. Prompt 治理
export function pageAiPrompts(params: {
  page?: number
  size?: number
  templateCode?: string
  scenario?: string
  status?: string
}): Promise<PageResult<AiPromptTemplate>> {
  return service.get(`${BASE}/prompts`, { params }) as unknown as Promise<PageResult<AiPromptTemplate>>
}

export function getAiPrompt(id: string): Promise<AiPromptTemplate> {
  return service.get(`${BASE}/prompts/${id}`) as unknown as Promise<AiPromptTemplate>
}

export function saveAiPromptDraft(data: AiPromptTemplate): Promise<AiPromptTemplate> {
  return service.post(`${BASE}/prompts`, data) as unknown as Promise<AiPromptTemplate>
}

export function publishAiPrompt(id: string, version: number): Promise<AiPromptTemplate> {
  return service.post(`${BASE}/prompts/${id}/publish`, undefined, { params: { version } }) as unknown as Promise<AiPromptTemplate>
}

export function disableAiPrompt(id: string, version: number, reason?: string): Promise<AiPromptTemplate> {
  return service.post(`${BASE}/prompts/${id}/disable`, undefined, { params: { version, reason } }) as unknown as Promise<AiPromptTemplate>
}

// 2. AI 工具注册
export function pageAiTools(params: {
  page?: number
  size?: number
  toolName?: string
  riskLevel?: string
  aiLevel?: string
  enabledOnly?: boolean
  forbiddenOnly?: boolean
}): Promise<PageResult<AiToolRegistry>> {
  return service.get(`${BASE}/tools`, { params }) as unknown as Promise<PageResult<AiToolRegistry>>
}

export function getAiTool(id: string): Promise<AiToolRegistry> {
  return service.get(`${BASE}/tools/${id}`) as unknown as Promise<AiToolRegistry>
}

export function saveAiTool(data: SaveToolRequest): Promise<AiToolRegistry> {
  return service.post(`${BASE}/tools`, data) as unknown as Promise<AiToolRegistry>
}

export function toggleAiTool(id: string, enabled: boolean, version: number): Promise<AiToolRegistry> {
  return service.post(`${BASE}/tools/${id}/toggle`, undefined, { params: { enabled, version } }) as unknown as Promise<AiToolRegistry>
}

export function validateAiTool(toolName: string): Promise<AiToolRegistry> {
  return service.post(`${BASE}/tools/validate`, undefined, { params: { toolName } }) as unknown as Promise<AiToolRegistry>
}

// 3. 成本治理
export function pageAiQuotas(params: {
  page?: number
  size?: number
  quotaScope?: string
  scopeKey?: string
}): Promise<PageResult<AiCostQuota>> {
  return service.get(`${BASE}/quotas`, { params }) as unknown as Promise<PageResult<AiCostQuota>>
}

export function saveAiQuota(data: SaveQuotaRequest): Promise<AiCostQuota> {
  return service.post(`${BASE}/quotas`, data) as unknown as Promise<AiCostQuota>
}

// 4. 反馈闭环
export function pageAiFeedbacks(params: {
  page?: number
  size?: number
  feedbackType?: string
  unhandledOnly?: boolean
  userId?: string
}): Promise<PageResult<AiFeedback>> {
  return service.get(`${BASE}/feedbacks`, { params }) as unknown as Promise<PageResult<AiFeedback>>
}

export function submitAiFeedback(data: SubmitFeedbackRequest): Promise<AiFeedback> {
  return service.post(`${BASE}/feedbacks`, data) as unknown as Promise<AiFeedback>
}

export function handleAiFeedback(id: string, handleResult: string): Promise<AiFeedback> {
  return service.post(`${BASE}/feedbacks/${id}/handle`, undefined, { params: { handleResult } }) as unknown as Promise<AiFeedback>
}

// 5. RAG 评测
export function pageAiEvalDatasets(params: {
  page?: number
  size?: number
  scenario?: string
  difficulty?: string
  caseId?: string
}): Promise<PageResult<AiEvalDataset>> {
  return service.get(`${BASE}/eval/datasets`, { params }) as unknown as Promise<PageResult<AiEvalDataset>>
}

export function pageAiEvalRuns(params: {
  page?: number
  size?: number
  releaseDecision?: string
}): Promise<PageResult<AiEvalRun>> {
  return service.get(`${BASE}/eval/runs`, { params }) as unknown as Promise<PageResult<AiEvalRun>>
}

export function getAiEvalRun(id: string): Promise<AiEvalRun> {
  return service.get(`${BASE}/eval/runs/${id}`) as unknown as Promise<AiEvalRun>
}

export function triggerAiEvalRun(data: TriggerEvalRunRequest): Promise<AiEvalRun> {
  return service.post(`${BASE}/eval/runs`, data) as unknown as Promise<AiEvalRun>
}

export function pageAiEvalResults(
  runId: string,
  params: { page?: number; size?: number; scenario?: string; failedOnly?: boolean }
): Promise<PageResult<AiEvalResult>> {
  return service.get(`${BASE}/eval/runs/${runId}/results`, { params }) as unknown as Promise<PageResult<AiEvalResult>>
}

// 6. 监控统计
export function getAiGovernanceStats(): Promise<AiGovernanceStats> {
  return service.get(`${BASE}/stats`) as unknown as Promise<AiGovernanceStats>
}

// 6.1 用量日报 (M-2: P2-C Token 用量可视化, 数据源为调用后实时累计日行)
export interface AiUsageDailyRow {
  usageDate: string
  quotaScope: string
  scopeKey: string
  modelCode: string
  tokenUsed: number
  costUsed: number
}

export interface AiUsageDaily {
  rows: AiUsageDailyRow[]
  totalTokens: number
  totalCost: number
  startDate: string
  endDate: string
}

/** 用量日报查询: GET /ai-governance/usage/daily (需 ai:usage:list) */
export function getAiUsageDaily(params: {
  startDate?: string
  endDate?: string
  scope?: string
  scopeKey?: string
  modelCode?: string
}): Promise<AiUsageDaily> {
  return service.get(`${BASE}/usage/daily`, { params }) as unknown as Promise<AiUsageDaily>
}
