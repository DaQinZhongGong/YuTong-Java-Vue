import service from './request'

export interface CopilotDraft {
  targetType: string
  suggestedCode: string
  suggestedName: string
  fields: Array<{ fieldCode: string; fieldName: string; component: string; required: boolean }>
  layoutJson: string
  message: string
  providerCode?: string
  modelCode?: string
  mock: boolean
  runId?: string
  status?: string
}

export interface CopilotRun {
  id: string
  targetType: string
  prompt: string
  status: string
  providerCode?: string
  modelCode?: string
  createdTime?: string
  /** 草稿载荷 JSON {targetType, code, name, fields[]} (P2-J 落库用) */
  outputJson?: string
  /** 生成计划 JSON (展示用) */
  planJson?: string
}

export function generateCopilotDraft(body: {
  prompt: string
  targetType?: string
  entityCode?: string
}): Promise<CopilotDraft> {
  return service.post('/ai/copilot/generate', body) as unknown as Promise<CopilotDraft>
}

export function pageCopilotRuns(params: { page?: number; size?: number; status?: string }) {
  return service.get('/ai/copilot/runs', { params }) as unknown as Promise<{ records: CopilotRun[]; total: number }>
}

export function approveCopilotRun(id: string): Promise<CopilotRun> {
  return service.post(`/ai/copilot/runs/${encodeURIComponent(id)}/approve`) as unknown as Promise<CopilotRun>
}

export function rejectCopilotRun(id: string): Promise<CopilotRun> {
  return service.post(`/ai/copilot/runs/${encodeURIComponent(id)}/reject`) as unknown as Promise<CopilotRun>
}

export function listCopilotOutbox(): Promise<CopilotRun[]> {
  return service.get('/ai/copilot/outbox') as unknown as Promise<CopilotRun[]>
}

export function ackCopilotOutbox(id: string): Promise<CopilotRun> {
  return service.post(`/ai/copilot/outbox/${encodeURIComponent(id)}/ack`) as unknown as Promise<CopilotRun>
}
