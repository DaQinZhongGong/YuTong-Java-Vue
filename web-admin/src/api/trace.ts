import service from './request'
import type { PageResult } from './types'

/**
 * AI 链路追踪 API 封装。
 * 设计来源: backend/yutong-ai-service AiTraceController (V043 ai_trace_run/ai_trace_node)
 *
 * 后端 6 接口:
 * - POST   /ai/trace/runs
 * - POST   /ai/trace/runs/{runId}/nodes
 * - POST   /ai/trace/runs/{runId}/finish
 * - GET    /ai/trace/runs
 * - GET    /ai/trace/runs/{id}
 * - GET    /ai/trace/runs/{runId}/nodes
 * - GET    /ai/trace/dashboard (M-3 大盘聚合)
 */

export interface AiTraceRun {
  id: string
  tenantId?: string
  traceType: string
  inputJson?: string | null
  outputJson?: string | null
  status: string
  latencyMs?: number | null
  errorMessage?: string | null
  createdBy?: string
  createdTime?: string
  updatedTime?: string
  version?: number
}

export interface AiTraceNode {
  id: string
  runId: string
  nodeType: string
  inputJson?: string | null
  outputJson?: string | null
  status: string
  latencyMs?: number | null
  errorMessage?: string | null
  createdTime?: string
  updatedTime?: string
}

export interface PageTraceRunsParams {
  page?: number
  size?: number
  traceType?: string
  status?: string
}

export interface FinishTraceRunPayload {
  status?: string
  outputJson?: string
  output_json?: string
  latencyMs?: number
  errorMessage?: string
}

const BASE = '/ai/trace'

/** 分页查询追踪主表: GET /ai/trace/runs */
export function pageTraceRuns(params: PageTraceRunsParams): Promise<PageResult<AiTraceRun>> {
  return service.get(`${BASE}/runs`, { params }) as unknown as Promise<PageResult<AiTraceRun>>
}

/** 查询追踪主表详情: GET /ai/trace/runs/{id} */
export function getTraceRun(id: string): Promise<AiTraceRun> {
  return service.get(`${BASE}/runs/${encodeURIComponent(id)}`) as unknown as Promise<AiTraceRun>
}

/** 查询追踪节点列表: GET /ai/trace/runs/{runId}/nodes */
export function listTraceNodes(runId: string): Promise<AiTraceNode[]> {
  return service.get(`${BASE}/runs/${encodeURIComponent(runId)}/nodes`) as unknown as Promise<AiTraceNode[]>
}

/** 开始追踪（创建主表）: POST /ai/trace/runs */
export function startTraceRun(data: Partial<AiTraceRun>): Promise<AiTraceRun> {
  return service.post(`${BASE}/runs`, data) as unknown as Promise<AiTraceRun>
}

/** 追加追踪节点: POST /ai/trace/runs/{runId}/nodes */
export function addTraceNode(runId: string, data: Partial<AiTraceNode>): Promise<AiTraceNode> {
  return service.post(`${BASE}/runs/${encodeURIComponent(runId)}/nodes`, data) as unknown as Promise<AiTraceNode>
}

/** 完成追踪（结束主表）: POST /ai/trace/runs/{runId}/finish */
export function finishTraceRun(runId: string, data?: FinishTraceRunPayload): Promise<AiTraceRun> {
  return service.post(`${BASE}/runs/${encodeURIComponent(runId)}/finish`, data || {}) as unknown as Promise<AiTraceRun>
}

// ==================== M-3 监控大盘 ====================

export interface TraceDashboardSummary {
  totalRuns: number
  successRuns: number
  failedRuns: number
  runningRuns: number
  successRate?: number | null
  avgLatencyMs?: number | null
  maxLatencyMs?: number | null
}

export interface TraceDailyPoint {
  day: string
  total: number
  success: number
  failed: number
  avgLatencyMs?: number | null
}

export interface TraceTypeStat {
  traceType: string
  total: number
  failed: number
  errorRate: number
  avgLatencyMs?: number | null
}

export interface TraceErrorItem {
  id: string
  traceType: string
  errorMessage?: string | null
  createdTime?: string
}

export interface TraceDashboard {
  summary: TraceDashboardSummary
  daily: TraceDailyPoint[]
  byType: TraceTypeStat[]
  recentErrors: TraceErrorItem[]
  startDate: string
  endDate: string
}

/** 监控大盘聚合: GET /ai/trace/dashboard (需 ai:trace:list) */
export function getTraceDashboard(params: {
  days?: number
  traceType?: string
}): Promise<TraceDashboard> {
  return service.get(`${BASE}/dashboard`, { params }) as unknown as Promise<TraceDashboard>
}
