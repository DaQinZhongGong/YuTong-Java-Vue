import service from './request'
import type { PageResult } from './types'

export interface MediaJob {
  id: string
  mediaType: string
  prompt: string
  providerCode?: string
  modelCode?: string
  status: string
  outputUrl?: string | null
  outputJson?: string | null
  createdTime?: string
}

export function createMediaJob(type: string, body: {
  prompt: string
  providerCode?: string
  modelCode?: string
  inputJson?: string
}): Promise<MediaJob> {
  return service.post(`/media/${encodeURIComponent(type)}`, body) as unknown as Promise<MediaJob>
}

export function getMediaJob(type: string, id: string): Promise<MediaJob> {
  return service.get(`/media/${encodeURIComponent(type)}/${encodeURIComponent(id)}`) as unknown as Promise<MediaJob>
}

export function pageMediaJobs(params: {
  page?: number
  size?: number
  mediaType?: string
  status?: string
}): Promise<PageResult<MediaJob>> {
  return service.get('/media/jobs', { params }) as unknown as Promise<PageResult<MediaJob>>
}
