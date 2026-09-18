import service from './request'
import type { SysTenantPackage, SaveTenantPackage, PageResult, PageRequest } from './types'

/**
 * 租户套餐管理 API。设计来源: 70-商业授权与版本能力裁剪、ADR 0004 P2-F 批次 6-B。
 *   - TenantPackageController @RequestMapping("/api/v1/tenant/packages")
 * baseURL 已含 /api/v1, 此处用相对路径。
 * 约定: 保存创建/更新共用 POST /tenant/packages (id 为空=创建), 与后端 SaveTenantPackageRequest 对齐。
 */

export interface TenantPackagePageQuery extends PageRequest {
  status?: string
}

/** 分页查询租户套餐: GET /tenant/packages */
export function getTenantPackages(
  params: TenantPackagePageQuery
): Promise<PageResult<SysTenantPackage>> {
  return service.get('/tenant/packages', { params }) as unknown as Promise<
    PageResult<SysTenantPackage>
  >
}

/** 查询套餐详情: GET /tenant/packages/{id} */
export function getTenantPackage(id: string): Promise<SysTenantPackage> {
  return service.get(`/tenant/packages/${id}`) as unknown as Promise<SysTenantPackage>
}

/** 列出已发布套餐 (租户订阅选择, 无权限码): GET /tenant/packages/active */
export function listActiveTenantPackages(): Promise<SysTenantPackage[]> {
  return service.get('/tenant/packages/active') as unknown as Promise<SysTenantPackage[]>
}

/** 保存套餐 (创建/更新): POST /tenant/packages (需 tenant:package:save) */
export function saveTenantPackage(data: SaveTenantPackage): Promise<string> {
  return service.post('/tenant/packages', data) as unknown as Promise<string>
}

/** 发布套餐: POST /tenant/packages/{id}/publish (需 tenant:package:publish) */
export function publishTenantPackage(id: string): Promise<void> {
  return service.post(`/tenant/packages/${id}/publish`) as unknown as Promise<void>
}

/** 归档套餐: POST /tenant/packages/{id}/archive (需 tenant:package:archive) */
export function archiveTenantPackage(id: string): Promise<void> {
  return service.post(`/tenant/packages/${id}/archive`) as unknown as Promise<void>
}
