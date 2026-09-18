import service from './request'
import type { PageResult } from './types'

export interface AiMemory {
  id: string
  ownerType: string
  ownerId: string
  memoryKind: string
  content: string
  source?: string
  confidence?: number
  expiresAt?: string | null
  createdTime?: string
}

export function pageMemories(params: {
  page?: number
  size?: number
  ownerType?: string
  ownerId?: string
  memoryKind?: string
}): Promise<PageResult<AiMemory>> {
  return service.get('/ai/memories', { params }) as unknown as Promise<PageResult<AiMemory>>
}

export function saveMemory(body: {
  ownerType?: string
  ownerId?: string
  memoryKind?: string
  content: string
  source?: string
}): Promise<AiMemory> {
  return service.post('/ai/memories', body) as unknown as Promise<AiMemory>
}

export function deleteMemory(id: string): Promise<void> {
  return service.delete(`/ai/memories/${encodeURIComponent(id)}`) as unknown as Promise<void>
}
