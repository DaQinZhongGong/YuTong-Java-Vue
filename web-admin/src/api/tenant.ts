import service from './request'
import type { SysTenant, SaveTenant, PageResult, PageRequest } from './types'

/**
 * 租户注册表管理 API。设计来源: 业界同类实现 SysTenant + ADR 0004 P2-F。
 *   - SysTenantController @RequestMapping("/api/v1/tenants")
 * baseURL 已含 /api/v1, 此处用相对路径。
 * 约定: 保存创建/更新共用 POST /tenants (id 为空=创建, 编码创建后不可改)。
 */

export interface TenantPageQuery extends PageRequest {
  status?: string
  keyword?: string
}

/** 分页查询租户: GET /tenants (需 tenant:tenant:list) */
export function getTenants(params: TenantPageQuery): Promise<PageResult<SysTenant>> {
  return service.get('/tenants', { params }) as unknown as Promise<PageResult<SysTenant>>
}

/** 查询租户详情: GET /tenants/{id} */
export function getTenant(id: string): Promise<SysTenant> {
  return service.get(`/tenants/${id}`) as unknown as Promise<SysTenant>
}

/** 保存租户 (创建/更新): POST /tenants (需 tenant:tenant:save) */
export function saveTenant(data: SaveTenant): Promise<string> {
  return service.post('/tenants', data) as unknown as Promise<string>
}

/** 启用租户: POST /tenants/{id}/enable (需 tenant:tenant:enable) */
export function enableTenant(id: string): Promise<void> {
  return service.post(`/tenants/${id}/enable`) as unknown as Promise<void>
}

/** 停用租户: POST /tenants/{id}/disable (需 tenant:tenant:disable, default 禁止) */
export function disableTenant(id: string): Promise<void> {
  return service.post(`/tenants/${id}/disable`) as unknown as Promise<void>
}

/** 分配套餐: POST /tenants/{id}/assign-package?packageId=&expireTime= (需 tenant:tenant:assign) */
export function assignTenantPackage(
  id: string,
  packageId: string,
  expireTime?: string,
): Promise<void> {
  return service.post(`/tenants/${id}/assign-package`, null, {
    params: { packageId, expireTime },
  }) as unknown as Promise<void>
}

/** 删除租户: DELETE /tenants/{id} (需 tenant:tenant:delete, 仅已停用) */
export function deleteTenant(id: string): Promise<void> {
  return service.delete(`/tenants/${id}`) as unknown as Promise<void>
}
