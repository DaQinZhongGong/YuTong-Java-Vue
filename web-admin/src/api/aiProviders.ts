import service from './request'
import type { AiProvider, AiProviderHealth, PageResult } from './types'

/**
 * AI 供应商管理 API。
 * <p>
 * 设计来源: 13-AI能力设计 provider registry / 37-AI治理与评测设计 供应商健康检查
 * <p>
 * 路径对齐后端 AiProviderController @RequestMapping("/api/v1/ai"), baseURL 已含 /api/v1, 此处用相对路径。
 *   - GET  /ai/providers             → listProviders        (分页查询供应商列表)
 *   - POST /ai/providers/health-check → checkProvidersHealth (触发健康检查)
 *   - GET  /ai/providers/health       → getProvidersHealth   (查询最近健康状态)
 *
 * 注: 后端 GET /ai/providers 当前返回 PageResult<AiProvider>，前端取 records 渲染。
 */

/** 查询供应商列表: GET /ai/providers (返回分页结构, 默认取 1000 条) */
export function listProviders(params?: { page?: number; size?: number; providerCode?: string; status?: string }): Promise<PageResult<AiProvider>> {
  return service.get('/ai/providers', { params: { page: 1, size: 1000, ...params } }) as unknown as Promise<PageResult<AiProvider>>
}

/** 触发供应商健康检查: POST /ai/providers/health-check */
export function checkProvidersHealth(): Promise<AiProviderHealth[]> {
  return service.post(
    '/ai/providers/health-check'
  ) as unknown as Promise<AiProviderHealth[]>
}

/** 查询最近一次供应商健康状态: GET /ai/providers/health */
export function getProvidersHealth(): Promise<AiProviderHealth[]> {
  return service.get('/ai/providers/health') as unknown as Promise<AiProviderHealth[]>
}
