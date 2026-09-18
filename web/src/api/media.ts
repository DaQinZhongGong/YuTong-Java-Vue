import client from './client'

/**
 * 媒体生成 API。设计来源: ADR 0005 P1-F + 业界同类实现 /media/*。
 */

export interface MediaJobInfo {
  id: string
  mediaType: string
  prompt: string
  status: string
  outputUrl?: string
  providerCode?: string
  modelCode?: string
  createdTime?: string
  outputJson?: string
}

/** 提交媒体生成: POST /ai/media/generate */
export function generateMedia(data: {
  mediaType: string
  prompt: string
  providerCode?: string
  modelCode?: string
}): Promise<MediaJobInfo> {
  return client.post('/ai/media/generate', data) as unknown as Promise<MediaJobInfo>
}

/** 查询媒体任务列表: GET /ai/media/jobs */
export function getMediaJobs(params?: {
  page?: number
  size?: number
  mediaType?: string
  status?: string
}): Promise<{ records: MediaJobInfo[]; total: number }> {
  return client.get('/ai/media/jobs', { params }) as unknown as Promise<{
    records: MediaJobInfo[]
    total: number
  }>
}

/** 查询单个任务: GET /ai/media/jobs/{id} */
export function getMediaJob(id: string): Promise<MediaJobInfo> {
  return client.get(`/ai/media/jobs/${id}`) as unknown as Promise<MediaJobInfo>
}
