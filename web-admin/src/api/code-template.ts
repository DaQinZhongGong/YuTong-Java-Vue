import service from './request'
import type { PageResult } from './types'

/**
 * 代码生成模板 API（45 号文档「插件与模板生态设计」E0）
 * 对齐后端 CodeTemplateController @RequestMapping("/api/v1/template/code-templates")
 */

/** 代码生成模板 */
export interface CodeTemplate {
  id: string
  templateCode: string
  templateName: string
  category: string
  tags?: string
  engineType: string
  content: string
  description?: string
  status: string
  createdTime?: string
  updatedTime?: string
  version?: number
}

/** 代码模板分页查询参数 */
export interface CodeTemplatePageQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  category?: string
  engineType?: string
  status?: string
}

/** 分页查询代码模板 */
export function getCodeTemplates(params: CodeTemplatePageQuery): Promise<PageResult<CodeTemplate>> {
  return service.get('/template/code-templates', { params }) as unknown as Promise<PageResult<CodeTemplate>>
}

/** 查询代码模板详情 */
export function getCodeTemplate(id: string): Promise<CodeTemplate> {
  return service.get(`/template/code-templates/${id}`) as unknown as Promise<CodeTemplate>
}

/** 创建代码模板 */
export function createCodeTemplate(data: Omit<CodeTemplate, 'id' | 'createdTime' | 'updatedTime'>): Promise<CodeTemplate> {
  return service.post('/template/code-templates', data) as unknown as Promise<CodeTemplate>
}

/** 更新代码模板 */
export function updateCodeTemplate(id: string, data: Omit<CodeTemplate, 'id' | 'createdTime' | 'updatedTime'>): Promise<CodeTemplate> {
  return service.put(`/template/code-templates/${id}`, data) as unknown as Promise<CodeTemplate>
}

/** 删除代码模板 */
export function deleteCodeTemplate(id: string): Promise<void> {
  return service.delete(`/template/code-templates/${id}`) as unknown as Promise<void>
}
