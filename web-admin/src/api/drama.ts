import service from './request'
import type { PageResult, PageRequest } from './types'

/**
 * 短剧合成 API。设计来源: ADR 0004 P2-D 批次 5-C。
 *   - DramaComposeController @RequestMapping("/api/v1/ai/drama/compose")
 * baseURL 已含 /api/v1, 此处用相对路径。
 * 约定: 提交为异步任务, 前端提交后轮询任务状态; capabilities 用于页面横幅 (ffmpeg 缺失时失败关闭)。
 */

/** 合成任务 (后端 DramaComposeJob 实体) */
export interface DramaComposeJob {
  id: string
  title: string
  shotVideosJson?: string
  audioTracksJson?: string
  subtitleFile?: string
  resolution?: string
  outputPath?: string
  outputSize?: number
  durationSec?: number
  /** 状态机 PENDING → RUNNING → SUCCESS / FAILED */
  status?: string
  errorMessage?: string
  logTail?: string
  exitCode?: number
  startedAt?: string
  finishedAt?: string
  createdTime?: string
}

/** 提交合成请求 (后端 SubmitComposeRequest, 路径均为 workDir 下相对路径) */
export interface SubmitComposeRequest {
  title: string
  shotVideos: string[]
  audioTracks?: string[]
  subtitleFile?: string
  resolution?: string
}

/** 合成运行能力 (后端 capabilities) */
export interface ComposeCapabilities {
  ffmpegPath?: string
  ffmpegAvailable?: boolean
  ffmpegVersion?: string
  ffprobeAvailable?: boolean
  workDir?: string
  timeoutSec?: number
  maxDurationSec?: number
  maxShots?: number
}

export interface ComposeJobPageQuery extends PageRequest {
  status?: string
}

/** 提交合成: POST /ai/drama/compose (需 ai:assistant:use) */
export function submitCompose(data: SubmitComposeRequest): Promise<DramaComposeJob> {
  return service.post('/ai/drama/compose', data) as unknown as Promise<DramaComposeJob>
}

/** 查询任务状态: GET /ai/drama/compose/{id} */
export function getComposeJob(id: string): Promise<DramaComposeJob> {
  return service.get(`/ai/drama/compose/${id}`) as unknown as Promise<DramaComposeJob>
}

/** 分页查询任务: GET /ai/drama/compose */
export function getComposeJobs(
  params: ComposeJobPageQuery
): Promise<PageResult<DramaComposeJob>> {
  return service.get('/ai/drama/compose', { params }) as unknown as Promise<
    PageResult<DramaComposeJob>
  >
}

/** 运行能力: GET /ai/drama/compose/capabilities */
export function getComposeCapabilities(): Promise<ComposeCapabilities> {
  return service.get('/ai/drama/compose/capabilities') as unknown as Promise<ComposeCapabilities>
}
