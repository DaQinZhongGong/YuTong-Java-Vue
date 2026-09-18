import service from './request'

/**
 * 分镜视频生成 API。设计来源: docs/compose/spec/ai-depth-parity.md S2.2。
 * 后端前缀 /api/v1/ai/drama, baseURL 已含 /api/v1。
 * 与既有短剧合成 /ai/drama/compose 互补: 本模块管项目+分镜+单镜/批量视频。
 */

// ==================== 类型 ====================

/** 分镜视频状态机: PENDING → GENERATING → SUCCEEDED | FAILED */
export type StoryboardVideoStatus = 'PENDING' | 'GENERATING' | 'SUCCEEDED' | 'FAILED'
/** 项目合成状态: NONE 未合成 / PENDING / RUNNING / SUCCESS / FAILED */
export type ComposeStatus = 'NONE' | 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface StoryboardItem {
  id: string
  projectId?: string
  sceneNo: number
  shotType?: string
  locationName?: string
  imagePrompt?: string
  videoPrompt?: string
  durationSeconds?: number
  videoStatus?: StoryboardVideoStatus
  videoId?: string
  videoUrl?: string
  lastFrameUrl?: string
  mediaJobId?: string
  errorMessage?: string
  createdTime?: string
  updatedTime?: string
}

export interface DramaProject {
  id: string
  title: string
  synopsis?: string
  artStyle?: string
  styleRef?: string
  aspectRatio?: string
  composeStatus?: ComposeStatus
  composeJobId?: string
  composedPath?: string
  storyboards?: StoryboardItem[]
  createdTime?: string
  updatedTime?: string
}

export interface StoryboardInput {
  sceneNo: number
  shotType?: string
  locationName?: string
  imagePrompt?: string
  videoPrompt?: string
  durationSeconds?: number
}

export interface CreateDramaProjectRequest {
  title: string
  synopsis?: string
  artStyle?: string
  aspectRatio?: string
  storyboards: StoryboardInput[]
}

export interface StoryboardVideoResult {
  storyboardId: string
  videoStatus: StoryboardVideoStatus
  videoId?: string
  videoUrl?: string
  lastFrameUrl?: string
  mediaJobId?: string
  errorMessage?: string
}

export interface BatchVideoResult {
  projectId: string
  total: number
  succeeded: number
  failed: number
  storyboards?: StoryboardItem[]
  message?: string
}

export interface ComposeResult {
  projectId: string
  composeStatus?: ComposeStatus
  composeJobId?: string
  composedPath?: string
  message?: string
}

export const STORYBOARD_SHOT_TYPE_OPTIONS = [
  '远景',
  '全景',
  '中景',
  '近景',
  '特写',
  '俯拍',
  '仰拍',
  '跟拍',
] as const

export function storyboardVideoStatusTag(status?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'SUCCEEDED': return 'success'
    case 'FAILED': return 'danger'
    case 'GENERATING': return 'primary'
    case 'PENDING': return 'warning'
    default: return 'info'
  }
}

export function composeStatusTag(status?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    case 'RUNNING': return 'primary'
    case 'PENDING': return 'warning'
    default: return 'info'
  }
}

// ==================== API ====================

/** 创建/落库项目+分镜: POST /ai/drama/projects */
export function createDramaProject(data: CreateDramaProjectRequest): Promise<DramaProject> {
  return service.post('/ai/drama/projects', data) as unknown as Promise<DramaProject>
}

/** 项目列表: GET /ai/drama/projects */
export function listDramaProjects(params?: {
  page?: number
  size?: number
  keyword?: string
}): Promise<DramaProject[] | { records: DramaProject[]; total: number }> {
  return service.get('/ai/drama/projects', { params }) as unknown as Promise<
    DramaProject[] | { records: DramaProject[]; total: number }
  >
}

/** 项目详情(含 storyboards): GET /ai/drama/projects/{id} */
export function getDramaProject(id: string): Promise<DramaProject> {
  return service.get(`/ai/drama/projects/${encodeURIComponent(id)}`) as unknown as Promise<DramaProject>
}

/** 追加分镜: POST /ai/drama/project/{id}/storyboards */
export function appendStoryboards(
  projectId: string,
  storyboards: StoryboardInput[]
): Promise<DramaProject> {
  return service.post(
    `/ai/drama/project/${encodeURIComponent(projectId)}/storyboards`,
    { storyboards }
  ) as unknown as Promise<DramaProject>
}

/** 单镜视频生成: POST /ai/drama/storyboard/{id}/video */
export function generateStoryboardVideo(
  storyboardId: string,
  body?: { idempotencyKey?: string }
): Promise<StoryboardVideoResult> {
  return service.post(
    `/ai/drama/storyboard/${encodeURIComponent(storyboardId)}/video`,
    body || {}
  ) as unknown as Promise<StoryboardVideoResult>
}

/** 单镜视频轮询: GET /ai/drama/storyboard/{id}/video */
export function getStoryboardVideo(storyboardId: string): Promise<StoryboardVideoResult> {
  return service.get(
    `/ai/drama/storyboard/${encodeURIComponent(storyboardId)}/video`
  ) as unknown as Promise<StoryboardVideoResult>
}

/** 批量视频 (同 location 串行 lastFrame, 跨组并发): POST /ai/drama/project/{id}/videos/batch */
export function batchGenerateProjectVideos(
  projectId: string,
  body?: { storyboardIds?: string[]; idempotencyKey?: string }
): Promise<BatchVideoResult> {
  return service.post(
    `/ai/drama/project/${encodeURIComponent(projectId)}/videos/batch`,
    body || {}
  ) as unknown as Promise<BatchVideoResult>
}

/** 串联合成: POST /ai/drama/project/{id}/compose */
export function composeDramaProject(
  projectId: string,
  body?: { idempotencyKey?: string }
): Promise<ComposeResult> {
  return service.post(
    `/ai/drama/project/${encodeURIComponent(projectId)}/compose`,
    body || {}
  ) as unknown as Promise<ComposeResult>
}
