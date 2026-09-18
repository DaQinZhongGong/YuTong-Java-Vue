import service from './request'
import type { SysOAuthClient, PageResult, PageRequest } from './types'

/**
 * OAuth2 客户端管理 API。设计来源: 08-API 契约设计、ADR 0004 P2-F 批次 6-C。
 *   - SysClientController @RequestMapping("/api/v1/oauth/clients")
 * baseURL 已含 /api/v1, 此处用相对路径。
 * 约定: 保存创建/更新共用 POST /oauth/clients (id 为空=创建, 自动生成 clientId/密钥);
 * 更新时不允许改 clientId/clientSecret, 密钥轮换走 reset-secret (仅本次响应可见)。
 */

export interface OAuthClientPageQuery extends PageRequest {
  status?: string
}

/** 分页查询客户端: GET /oauth/clients */
export function getOAuthClients(
  params: OAuthClientPageQuery
): Promise<PageResult<SysOAuthClient>> {
  return service.get('/oauth/clients', { params }) as unknown as Promise<
    PageResult<SysOAuthClient>
  >
}

/** 查询客户端详情: GET /oauth/clients/{id} */
export function getOAuthClient(id: string): Promise<SysOAuthClient> {
  return service.get(`/oauth/clients/${id}`) as unknown as Promise<SysOAuthClient>
}

/** 保存客户端 (创建/更新): POST /oauth/clients (需 auth:client:save) */
export function saveOAuthClient(data: Partial<SysOAuthClient>): Promise<string> {
  return service.post('/oauth/clients', data) as unknown as Promise<string>
}

/** 启用客户端: POST /oauth/clients/{id}/enable (需 auth:client:save) */
export function enableOAuthClient(id: string): Promise<void> {
  return service.post(`/oauth/clients/${id}/enable`) as unknown as Promise<void>
}

/** 禁用客户端: POST /oauth/clients/{id}/disable (需 auth:client:save) */
export function disableOAuthClient(id: string): Promise<void> {
  return service.post(`/oauth/clients/${id}/disable`) as unknown as Promise<void>
}

/** 重置密钥 (仅本次响应可见): POST /oauth/clients/{id}/reset-secret (需 auth:client:save) */
export function resetOAuthClientSecret(id: string): Promise<{ clientSecret: string }> {
  return service.post(`/oauth/clients/${id}/reset-secret`) as unknown as Promise<{
    clientSecret: string
  }>
}
