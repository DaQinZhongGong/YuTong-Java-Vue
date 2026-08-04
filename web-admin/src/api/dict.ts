import service from './request'
import type { DictType, DictItem, PageResult, PageRequest } from './types'

/**
 * 字典管理 API。设计来源: 08-API契约设计、98-后端实现蓝图系统基础接口补齐规则
 * GA2-25: 路径对齐后端 controller, 补全 dict-type / dict-item CRUD。
 *   - DictTypeController @RequestMapping("/api/v1/dict-types")
 *   - DictItemController @RequestMapping("/api/v1/dict-items")
 * baseURL 已含 /api/v1, 此处用相对路径。
 */

// ==================== 字典类型 ====================

/** 分页查询字典类型: GET /dict-types */
export function getDictTypes(
  params?: PageRequest & { keyword?: string }
): Promise<PageResult<DictType>> {
  return service.get('/dict-types', { params }) as unknown as Promise<
    PageResult<DictType>
  >
}

/** 查询字典类型详情: GET /dict-types/{id} */
export function getDictType(id: string): Promise<DictType> {
  return service.get(`/dict-types/${id}`) as unknown as Promise<DictType>
}

/** 创建字典类型: POST /dict-types (需 system:dict:add) */
export function createDictType(data: Partial<DictType>): Promise<DictType> {
  return service.post('/dict-types', data) as unknown as Promise<DictType>
}

/** 更新字典类型: PUT /dict-types/{id} (需 system:dict:edit) */
export function updateDictType(
  id: string,
  data: Partial<DictType>
): Promise<DictType> {
  return service.put(`/dict-types/${id}`, data) as unknown as Promise<DictType>
}

/** 删除字典类型: DELETE /dict-types/{id} (需 system:dict:delete) */
export function deleteDictType(id: string): Promise<void> {
  return service.delete(`/dict-types/${id}`) as unknown as Promise<void>
}

// ==================== 字典项 ====================

/** 分页查询字典项: GET /dict-items?dictType=xxx */
export function getDictItems(
  params?: PageRequest & { dictType?: string }
): Promise<PageResult<DictItem>> {
  return service.get('/dict-items', { params }) as unknown as Promise<
    PageResult<DictItem>
  >
}

/** 按字典类型查询启用字典项: GET /dict-items/by-type/{dictType} */
export function getDictItemsByType(dictType: string): Promise<DictItem[]> {
  return service.get(`/dict-items/by-type/${dictType}`) as unknown as Promise<
    DictItem[]
  >
}

/** 创建字典项: POST /dict-items (需 system:dict:add) */
export function createDictItem(data: Partial<DictItem>): Promise<DictItem> {
  return service.post('/dict-items', data) as unknown as Promise<DictItem>
}

/** 更新字典项: PUT /dict-items/{id} (需 system:dict:edit) */
export function updateDictItem(
  id: string,
  data: Partial<DictItem>
): Promise<DictItem> {
  return service.put(`/dict-items/${id}`, data) as unknown as Promise<DictItem>
}

/** 删除字典项: DELETE /dict-items/{id} (需 system:dict:delete) */
export function deleteDictItem(id: string): Promise<void> {
  return service.delete(`/dict-items/${id}`) as unknown as Promise<void>
}
