import client from './client'

/** CMS 公开内容 (CmsPublicController, 无权限码, 仅 PUBLISHED 可见) */
export interface CmsContent {
  id: string
  title: string
  slug: string
  contentMd: string
  contentHtml: string
  summary?: string
  category?: string
  tags?: string
  status: string
  viewCount?: number
  publishedAt?: string
}

export const CMS_CATEGORIES = ['announcement', 'help', 'terms', 'blog'] as const

/** 按 slug 获取已发布内容 (服务端浏览 +1) */
export async function getPublishedBySlug(slug: string): Promise<CmsContent> {
  return (await client.get(`/cms/published/${encodeURIComponent(slug)}`)) as unknown as CmsContent
}

/** 按分类列出已发布内容 */
export async function listPublishedByCategory(category: string, limit = 20): Promise<CmsContent[]> {
  const data = (await client.get('/cms/published', { params: { category, limit } })) as unknown as
    | CmsContent[]
    | { records?: CmsContent[] }
  if (Array.isArray(data)) return data
  return data.records || []
}

/** 聚合全部分类的已发布内容 (失败关闭: 任一分类失败即抛错, 不填演示数据) */
export async function listAllPublished(limitPerCategory = 20): Promise<CmsContent[]> {
  const settled = await Promise.allSettled(CMS_CATEGORIES.map((c) => listPublishedByCategory(c, limitPerCategory)))
  const failed = settled.filter((r) => r.status === 'rejected')
  if (failed.length > 0) {
    const reason = (failed[0] as PromiseRejectedResult).reason as { message?: string }
    throw new Error(reason?.message || '内容加载失败')
  }
  return (settled as PromiseFulfilledResult<CmsContent[]>[])
    .flatMap((r) => r.value)
    .sort((a, b) => String(b.publishedAt || '').localeCompare(String(a.publishedAt || '')))
}
