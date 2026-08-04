import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDictItemsByType } from '@/api/dict'
import type { DictItem } from '@/api/types'

/**
 * Dict Store
 * 设计来源: 66-前端组件API与状态管理详设 line 107-115
 *
 * 字典数据缓存, 避免业务页面重复请求。
 * 仅缓存 dictType → DictItem[] 映射, 不保存大对象或敏感数据。
 */

export const useDictStore = defineStore('dict', () => {
  /** dictType → DictItem[] 缓存映射 */
  const dictMap = ref<Record<string, DictItem[]>>({})
  /** dictType → loading 状态映射, 避免并发重复请求 */
  const loadingMap = ref<Record<string, boolean>>({})

  /**
   * 加载字典数据 (已缓存则直接返回, 不重复请求)。
   * @param dictType 字典类型编码
   */
  async function loadDict(dictType: string): Promise<DictItem[]> {
    if (!dictType) return []
    if (dictMap.value[dictType]) {
      return dictMap.value[dictType]
    }
    if (loadingMap.value[dictType]) {
      // 已有请求进行中, 等待结果 (轮询简单实现)
      await new Promise((resolve) => setTimeout(resolve, 100))
      return dictMap.value[dictType] || []
    }
    loadingMap.value[dictType] = true
    try {
      const items = await getDictItemsByType(dictType)
      dictMap.value[dictType] = items || []
      return items || []
    } finally {
      loadingMap.value[dictType] = false
    }
  }

  /**
   * 强制刷新字典数据 (绕过缓存)。
   * @param dictType 字典类型编码
   */
  async function refreshDict(dictType: string): Promise<DictItem[]> {
    if (!dictType) return []
    loadingMap.value[dictType] = true
    try {
      const items = await getDictItemsByType(dictType)
      dictMap.value[dictType] = items || []
      return items || []
    } finally {
      loadingMap.value[dictType] = false
    }
  }

  return {
    dictMap,
    loadingMap,
    loadDict,
    refreshDict,
  }
})
