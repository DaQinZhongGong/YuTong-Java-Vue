import service from './request'
import { getToken, getMockUser } from '@/utils/auth'

/**
 * Coding Harness API。设计来源: docs/compose/spec/ai-depth-parity.md S2.1。
 * 后端前缀 /api/v1/coding/harness, baseURL 已含 /api/v1, 此处用相对路径。
 * 权限: ai:assistant:use。
 */

// ==================== 类型 ====================

/** 权限模式: fail-closed */
export type PermissionMode = 'READ_ONLY' | 'WORKSPACE_WRITE' | 'FULL_ACCESS'
/** 审批策略 */
export type ApprovalPolicy = 'ON_REQUEST' | 'NEVER'
/** Run 状态机 */
export type RunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING_FOR_APPROVAL'
  | 'WAITING_FOR_INPUT'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED'

export interface HarnessBudget {
  maxToolCalls?: number
  maxInputTokens?: number
  maxOutputTokens?: number
}

export interface HarnessSession {
  id: string
  title: string
  workspacePath?: string
  model?: string
  permissionMode?: PermissionMode
  approvalPolicy?: ApprovalPolicy
  activeRunId?: string
  revision?: number
  pinnedAt?: string | null
  deleted?: boolean
  createdTime?: string
  updatedTime?: string
}

export interface HarnessRun {
  id: string
  sessionId: string
  status: RunStatus
  requirement?: string
  permissionMode?: PermissionMode
  permissionRevision?: number
  budget?: HarnessBudget
  usage?: {
    toolCallCount?: number
    inputTokens?: number
    outputTokens?: number
    iteration?: number
  }
  iteration?: number
  toolCallCount?: number
  cancelRequested?: boolean
  errorMessage?: string
  revision?: number
  createdTime?: string
  updatedTime?: string
}

export interface HarnessEvent {
  id?: string
  eventId?: string
  sessionId: string
  runId: string
  sequence: number
  type: string
  payloadJson?: string
  payload?: unknown
  createdTime?: string
}

/** 后端 REST/SSE 返回 sequenceNo/eventType，统一映射为前端 sequence/type */
export function normalizeHarnessEvent(raw: Record<string, unknown>): HarnessEvent {
  const sequence =
    typeof raw.sequence === 'number'
      ? raw.sequence
      : typeof raw.sequenceNo === 'number'
        ? raw.sequenceNo
        : Number(raw.sequenceNo ?? raw.sequence ?? 0)
  const type = String(raw.type ?? raw.eventType ?? '')
  let payloadJson: string | undefined
  let payload: unknown = raw.payload
  const rawPayloadJson = raw.payloadJson
  if (typeof rawPayloadJson === 'string') {
    payloadJson = rawPayloadJson
    try {
      payload = JSON.parse(rawPayloadJson)
    } catch {
      // keep string
    }
  } else if (raw.payload != null && typeof raw.payload !== 'string') {
    payloadJson = JSON.stringify(raw.payload)
  }
  return {
    id: raw.id != null ? String(raw.id) : undefined,
    eventId: raw.eventId != null ? String(raw.eventId) : undefined,
    sessionId: String(raw.sessionId ?? ''),
    runId: String(raw.runId ?? ''),
    sequence: Number.isFinite(sequence) ? sequence : 0,
    type,
    payloadJson,
    payload,
    createdTime: raw.createdTime != null ? String(raw.createdTime) : undefined,
  }
}

export interface HarnessApproval {
  id: string
  sessionId?: string
  runId?: string
  toolName: string
  argumentsSha256?: string
  argumentsJson?: string
  state?: string
  expectedRevision?: number
  decisionId?: string
  decidedBy?: string
  decidedAt?: string
  createdTime?: string
}

export interface HarnessPlan {
  id?: string
  runId?: string
  taskId: string
  revision?: number
  mode?: string
  reviewState?: string
  planMd?: string
  canonicalHash?: string
  feedback?: string
}

export interface CreateSessionRequest {
  workspacePath?: string
  title: string
  model?: string
  permissionMode?: PermissionMode
  approvalPolicy?: ApprovalPolicy
  idempotencyKey?: string
}

export interface CreateRunRequest {
  requirement: string
  budget?: HarnessBudget
  idempotencyKey?: string
}

export interface ResolveApprovalRequest {
  decisionId: string
  decision: 'APPROVE' | 'DENY'
  expectedRevision: number
  argumentsSha256?: string
  note?: string
}

export interface ApprovePlanRequest {
  planId: string
  expectedRevision: number
  expectedHash: string
  idempotencyKey?: string
}

export const HARNESS_PERMISSION_MODE_OPTIONS: Array<{ value: PermissionMode; label: string }> = [
  { value: 'READ_ONLY', label: '只读 READ_ONLY' },
  { value: 'WORKSPACE_WRITE', label: '工作区写 WORKSPACE_WRITE' },
  { value: 'FULL_ACCESS', label: '完全访问 FULL_ACCESS' },
]

export const HARNESS_APPROVAL_POLICY_OPTIONS: Array<{ value: ApprovalPolicy; label: string }> = [
  { value: 'ON_REQUEST', label: '按需审批 ON_REQUEST' },
  { value: 'NEVER', label: '从不审批 NEVER' },
]

export function harnessRunStatusTag(status?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'RUNNING': return 'primary'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'CANCELLED': return 'info'
    case 'WAITING_FOR_APPROVAL': return 'warning'
    case 'WAITING_FOR_INPUT': return 'warning'
    case 'QUEUED': return 'info'
    default: return 'info'
  }
}

export function isTerminalRunStatus(status?: string): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

// ==================== 会话 ====================

export function createHarnessSession(data: CreateSessionRequest): Promise<HarnessSession> {
  return service.post('/coding/harness/sessions', data) as unknown as Promise<HarnessSession>
}

export function listHarnessSessions(params?: {
  includeDeleted?: boolean
  keyword?: string
}): Promise<HarnessSession[]> {
  return service.get('/coding/harness/sessions', { params }) as unknown as Promise<HarnessSession[]>
}

export function getHarnessSession(id: string): Promise<HarnessSession> {
  return service.get(`/coding/harness/sessions/${encodeURIComponent(id)}`) as unknown as Promise<HarnessSession>
}

export function pinHarnessSession(id: string, pinned: boolean): Promise<HarnessSession> {
  return service.put(`/coding/harness/sessions/${encodeURIComponent(id)}/pin`, { pinned }) as unknown as Promise<HarnessSession>
}

export function deleteHarnessSession(id: string): Promise<void> {
  return service.delete(`/coding/harness/sessions/${encodeURIComponent(id)}`) as unknown as Promise<void>
}

export function restoreHarnessSession(id: string): Promise<HarnessSession> {
  return service.post(`/coding/harness/sessions/${encodeURIComponent(id)}/restore`) as unknown as Promise<HarnessSession>
}

// ==================== Run ====================

export function createHarnessRun(sessionId: string, data: CreateRunRequest): Promise<HarnessRun> {
  return service.post(`/coding/harness/sessions/${encodeURIComponent(sessionId)}/runs`, data) as unknown as Promise<HarnessRun>
}

export function listHarnessRuns(sessionId: string): Promise<HarnessRun[]> {
  return service.get(`/coding/harness/sessions/${encodeURIComponent(sessionId)}/runs`) as unknown as Promise<HarnessRun[]>
}

export function getHarnessRun(sessionId: string, runId: string): Promise<HarnessRun> {
  return service.get(
    `/coding/harness/sessions/${encodeURIComponent(sessionId)}/runs/${encodeURIComponent(runId)}`
  ) as unknown as Promise<HarnessRun>
}

export function cancelHarnessRun(sessionId: string, runId: string): Promise<HarnessRun> {
  return service.post(
    `/coding/harness/sessions/${encodeURIComponent(sessionId)}/runs/${encodeURIComponent(runId)}/cancel`
  ) as unknown as Promise<HarnessRun>
}

// ==================== 事件 ====================

export function listHarnessRunEvents(
  sessionId: string,
  runId: string,
  params?: { afterSequence?: number }
): Promise<HarnessEvent[]> {
  return service
    .get(
      `/coding/harness/sessions/${encodeURIComponent(sessionId)}/runs/${encodeURIComponent(runId)}/events`,
      { params }
    )
    .then((rows: unknown) => {
      const arr = Array.isArray(rows) ? rows : ((rows as { records?: unknown[] })?.records ?? [])
      return arr.map((r) => normalizeHarnessEvent(r as Record<string, unknown>))
    }) as unknown as Promise<HarnessEvent[]>
}

/** 从事件流中提取待审批卡片 (approval.requested / approval.pending) */
export function extractPendingApprovals(events: HarnessEvent[]): HarnessApproval[] {
  const map = new Map<string, HarnessApproval>()
  for (const ev of events) {
    if (!ev.type || !/approval/i.test(ev.type)) continue
    let payload: Record<string, unknown> | null = null
    try {
      const raw = ev.payloadJson ?? (typeof ev.payload === 'string' ? ev.payload : JSON.stringify(ev.payload ?? null))
      payload = raw ? (JSON.parse(raw) as Record<string, unknown>) : null
    } catch {
      payload = null
    }
    if (!payload) continue
    const state = String(payload.state ?? payload.approvalState ?? '')
    const id = String(payload.id ?? payload.approvalId ?? ev.eventId ?? ev.id ?? `${ev.runId}:${ev.sequence}`)
    if (/resolved|consumed|decided|done|closed/i.test(state)) {
      map.delete(id)
      continue
    }
    if (/pending|awaiting|requested/i.test(ev.type) || /pending|awaiting|open/i.test(state)) {
      map.set(id, {
        id,
        sessionId: ev.sessionId,
        runId: ev.runId,
        toolName: String(payload.toolName ?? payload.tool ?? 'unknown'),
        argumentsSha256: payload.argumentsSha256 ? String(payload.argumentsSha256) : undefined,
        argumentsJson: payload.argumentsJson
          ? String(payload.argumentsJson)
          : payload.arguments
            ? JSON.stringify(payload.arguments)
            : undefined,
        state: state || 'PENDING',
        expectedRevision: typeof payload.expectedRevision === 'number' ? payload.expectedRevision : undefined,
        createdTime: ev.createdTime,
      })
    }
  }
  return Array.from(map.values())
}

/** 从事件流中提取待审批计划 (plan.awaiting_approval) */
export function extractPendingPlan(events: HarnessEvent[]): HarnessPlan | null {
  for (let i = events.length - 1; i >= 0; i--) {
    const ev = events[i]
    if (!ev.type || !/plan/i.test(ev.type)) continue
    if (!/awaiting|pending|approval/i.test(ev.type)) continue
    let payload: Record<string, unknown> | null = null
    try {
      const raw = ev.payloadJson ?? (typeof ev.payload === 'string' ? ev.payload : JSON.stringify(ev.payload ?? null))
      payload = raw ? (JSON.parse(raw) as Record<string, unknown>) : null
    } catch {
      payload = null
    }
    if (!payload) continue
    const reviewState = String(payload.reviewState ?? payload.state ?? '')
    if (/approved|rejected|done/i.test(reviewState)) continue
    return {
      id: payload.planId ? String(payload.planId) : undefined,
      runId: ev.runId,
      taskId: String(payload.taskId ?? payload.id ?? ''),
      revision: typeof payload.expectedRevision === 'number' ? payload.expectedRevision : undefined,
      mode: payload.mode ? String(payload.mode) : undefined,
      reviewState: reviewState || 'AWAITING_APPROVAL',
      planMd: payload.planMd ? String(payload.planMd) : payload.planMarkdown ? String(payload.planMarkdown) : undefined,
      canonicalHash: payload.canonicalHash ? String(payload.canonicalHash) : undefined,
    }
  }
  return null
}

// ==================== 审批 / 计划 ====================

export function resolveHarnessApproval(
  approvalId: string,
  data: ResolveApprovalRequest
): Promise<HarnessApproval> {
  return service.post(
    `/coding/harness/approvals/${encodeURIComponent(approvalId)}/resolve`,
    data
  ) as unknown as Promise<HarnessApproval>
}

export function approveHarnessPlan(data: ApprovePlanRequest): Promise<HarnessPlan> {
  return service.post('/coding/harness/plan/approve', data) as unknown as Promise<HarnessPlan>
}

// ==================== SSE (fetch + ReadableStream) ====================

function buildSseHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    ...(extra || {}),
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  try {
    const tenantId = localStorage.getItem('yutong_tenant_id')
    if (tenantId) {
      headers['X-Tenant-Id'] = tenantId
    }
  } catch {
    // ignore
  }
  if (import.meta.env.DEV) {
    const mockUser = getMockUser()
    if (mockUser) {
      headers['X-Mock-User'] = mockUser
    }
  }
  return headers
}

export interface StreamHarnessEventsOptions {
  afterSequence?: number
  onEvent?: (event: HarnessEvent) => void
  onError?: (error: Error) => void
  onComplete?: () => void
}

/**
 * SSE 订阅 Run 事件: GET /coding/harness/sessions/{id}/runs/{runId}/events/stream
 * 使用原生 fetch + ReadableStream (axios 不支持流式)。
 * 可通过 AbortSignal 取消订阅。
 */
export async function streamHarnessRunEvents(
  sessionId: string,
  runId: string,
  options: StreamHarnessEventsOptions = {},
  signal?: AbortSignal
): Promise<void> {
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  const qs = options.afterSequence != null ? `?afterSequence=${options.afterSequence}` : ''
  const url = `${baseURL}/coding/harness/sessions/${encodeURIComponent(sessionId)}/runs/${encodeURIComponent(runId)}/events/stream${qs}`

  let response: Response
  try {
    response = await fetch(url, {
      method: 'GET',
      headers: buildSseHeaders(),
      signal,
    })
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      options.onComplete?.()
      return
    }
    options.onError?.(err as Error)
    throw err
  }

  if (!response.ok) {
    let errorMsg = `HTTP ${response.status}`
    try {
      const errBody = await response.json()
      errorMsg = (errBody as { message?: string })?.message || errorMsg
    } catch {
      // non-JSON
    }
    const error = new Error(errorMsg)
    options.onError?.(error)
    throw error
  }

  if (!response.body) {
    const error = new Error('SSE 流式响应不支持: response.body 为空')
    options.onError?.(error)
    throw error
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const dispatch = (rawEvent: string) => {
    const lines = rawEvent.split('\n')
    const dataLines: string[] = []
    for (const line of lines) {
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      }
    }
    if (dataLines.length === 0) return
    const dataJson = dataLines.join('\n')
    try {
      const parsed = JSON.parse(dataJson) as Record<string, unknown>
      // 兼容信封 { type, data } 或直接事件体
      if (parsed && typeof parsed === 'object' && 'type' in parsed && !('sequence' in parsed) && !('sequenceNo' in parsed) && !('eventType' in parsed)) {
        const envelope = parsed as unknown as { type: string; data?: unknown }
        options.onEvent?.({
          sessionId,
          runId,
          sequence: 0,
          type: envelope.type,
          payload: envelope.data,
          payloadJson: envelope.data != null ? JSON.stringify(envelope.data) : undefined,
        })
      } else {
        options.onEvent?.(normalizeHarnessEvent(parsed))
      }
    } catch {
      // ignore unparseable (heartbeat comment)
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let sep: number
      while ((sep = buffer.indexOf('\n\n')) !== -1) {
        const raw = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        dispatch(raw)
      }
    }
    if (buffer.trim()) {
      dispatch(buffer)
    }
    options.onComplete?.()
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      options.onComplete?.()
      return
    }
    options.onError?.(err as Error)
    throw err
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // ignore
    }
  }
}
