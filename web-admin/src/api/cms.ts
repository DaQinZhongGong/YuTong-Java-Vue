import service from './request'
import type { CmsContent, SaveCmsContent, PageResult, PageRequest } from './types'

/**
 * CMS 内容管理 API。设计来源: 84-CMS 运营详设、ADR 0004 P2-F 批次 6-A。
 *   - CmsContentController @RequestMapping("/api/v1/cms/contents")
 *   - CmsPublicController @RequestMapping("/api/v1/cms/published") (无权限码)
 * baseURL 已含 /api/v1, 此处用相对路径。
 * 约定: 保存创建/更新共用 POST /cms/contents (id 为空=创建), 与后端 SaveCmsContentRequest 对齐。
 */

export interface CmsPageQuery extends PageRequest {
  status?: string
  category?: string
}

/** 分页查询 CMS 内容: GET /cms/contents */
export function getCmsContents(
  params: CmsPageQuery
): Promise<PageResult<CmsContent>> {
  return service.get('/cms/contents', { params }) as unknown as Promise<
    PageResult<CmsContent>
  >
}

/** 查询 CMS 内容详情: GET /cms/contents/{id} */
export function getCmsContent(id: string): Promise<CmsContent> {
  return service.get(`/cms/contents/${id}`) as unknown as Promise<CmsContent>
}

/** 保存 CMS 内容 (创建/更新): POST /cms/contents (需 cms:content:save) */
export function saveCmsContent(data: SaveCmsContent): Promise<string> {
  return service.post('/cms/contents', data) as unknown as Promise<string>
}

/** 发布内容: POST /cms/contents/{id}/publish (需 cms:content:publish) */
export function publishCmsContent(id: string): Promise<void> {
  return service.post(`/cms/contents/${id}/publish`) as unknown as Promise<void>
}

/** 归档内容: POST /cms/contents/{id}/archive (需 cms:content:archive) */
export function archiveCmsContent(id: string): Promise<void> {
  return service.post(`/cms/contents/${id}/archive`) as unknown as Promise<void>
}

/** 物理删除内容 (仅 DRAFT): DELETE /cms/contents/{id} (需 cms:content:delete) */
export function deleteCmsContent(id: string): Promise<void> {
  return service.delete(`/cms/contents/${id}`) as unknown as Promise<void>
}

/** 按 slug 获取已发布内容 (公开, 浏览 +1): GET /cms/published/{slug} */
export function getPublishedCmsBySlug(slug: string): Promise<CmsContent> {
  return service.get(`/cms/published/${slug}`) as unknown as Promise<CmsContent>
}
