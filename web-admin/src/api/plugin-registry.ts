import service from './request'
import type { PageResult } from './types'

/**
 * 插件注册表 API（45 号文档「插件与模板生态设计」E0）
 * 对齐后端 PluginRegistryController @RequestMapping("/api/v1/plugin/registries")
 */

/** 插件注册表 */
export interface PluginRegistry {
  id: string
  pluginCode: string
  pluginName: string
  pluginVersion: string
  pluginType: string
  status: string
  description?: string
  entryClass?: string
  iconUrl?: string
  tags?: string
  createdTime?: string
  updatedTime?: string
  version?: number
}

/** 插件注册表分页查询参数 */
export interface PluginRegistryPageQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  pluginType?: string
  status?: string
}

/** 分页查询插件注册表 */
export function getPluginRegistries(params: PluginRegistryPageQuery): Promise<PageResult<PluginRegistry>> {
  return service.get('/plugin/registries', { params }) as unknown as Promise<PageResult<PluginRegistry>>
}

/** 查询插件注册表详情 */
export function getPluginRegistry(id: string): Promise<PluginRegistry> {
  return service.get(`/plugin/registries/${id}`) as unknown as Promise<PluginRegistry>
}

/** 创建插件注册表 */
export function createPluginRegistry(data: Omit<PluginRegistry, 'id' | 'createdTime' | 'updatedTime'>): Promise<PluginRegistry> {
  return service.post('/plugin/registries', data) as unknown as Promise<PluginRegistry>
}

/** 更新插件注册表 */
export function updatePluginRegistry(id: string, data: Omit<PluginRegistry, 'id' | 'createdTime' | 'updatedTime'>): Promise<PluginRegistry> {
  return service.put(`/plugin/registries/${id}`, data) as unknown as Promise<PluginRegistry>
}

/** 删除插件注册表 */
export function deletePluginRegistry(id: string): Promise<void> {
  return service.delete(`/plugin/registries/${id}`) as unknown as Promise<void>
}
