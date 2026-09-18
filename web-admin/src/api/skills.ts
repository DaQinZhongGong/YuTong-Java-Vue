import service from './request'
import type { PageResult } from './types'

export interface AiSkill {
  id: string
  skillCode: string
  skillName: string
  skillType: string
  sourcePath?: string | null
  skillMd?: string | null
  versionNo?: number
  status?: string
  configJson?: string | null
  version?: number
}

export interface ExecuteSkillResponse {
  runId: string
  skillCode: string
  skillType: string
  action: string
  status: string
  summary: string
  outputFileId?: string | null
  downloadUrl?: string | null
  latencyMs?: number
}

export function pageSkills(params: {
  page?: number
  size?: number
  skillCode?: string
  skillType?: string
  status?: string
}): Promise<PageResult<AiSkill>> {
  return service.get('/skills', { params }) as unknown as Promise<PageResult<AiSkill>>
}

export function getSkill(id: string): Promise<AiSkill> {
  return service.get(`/skills/${encodeURIComponent(id)}`) as unknown as Promise<AiSkill>
}

export function publishSkill(id: string, version: number): Promise<AiSkill> {
  return service.post(`/skills/${encodeURIComponent(id)}/publish`, null, {
    params: { version },
  }) as unknown as Promise<AiSkill>
}

export function executeSkill(
  id: string,
  body: { action?: string; content: string; fileName?: string; title?: string },
): Promise<ExecuteSkillResponse> {
  return service.post(`/skills/${encodeURIComponent(id)}/execute`, body) as unknown as Promise<ExecuteSkillResponse>
}
