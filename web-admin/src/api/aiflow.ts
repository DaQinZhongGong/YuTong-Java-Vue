import service from './request'
import type { PageResult } from './types'

export interface AiflowDefinition {
  id: string
  flowCode: string
  flowName: string
  versionNo?: number
  dagJson?: string | null
  status?: string
  version?: number
}

export function pageAiflowDefinitions(params: {
  page?: number
  size?: number
  flowCode?: string
  status?: string
}): Promise<PageResult<AiflowDefinition>> {
  return service.get('/aiflow/definitions', { params }) as unknown as Promise<PageResult<AiflowDefinition>>
}

export function getAiflowDefinition(id: string): Promise<AiflowDefinition> {
  return service.get(`/aiflow/definitions/${encodeURIComponent(id)}`) as unknown as Promise<AiflowDefinition>
}

export function createAiflowDefinition(body: Partial<AiflowDefinition>): Promise<AiflowDefinition> {
  return service.post('/aiflow/definitions', body) as unknown as Promise<AiflowDefinition>
}

export function updateAiflowDefinition(id: string, body: Partial<AiflowDefinition>): Promise<AiflowDefinition> {
  return service.put(`/aiflow/definitions/${encodeURIComponent(id)}`, body) as unknown as Promise<AiflowDefinition>
}

export function publishAiflowDefinition(id: string, version: number): Promise<AiflowDefinition> {
  return service.post(`/aiflow/definitions/${encodeURIComponent(id)}/publish`, null, {
    params: { version },
  }) as unknown as Promise<AiflowDefinition>
}
