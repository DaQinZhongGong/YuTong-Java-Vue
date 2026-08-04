import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * Lowcode Store
 * 设计来源: 66-前端组件API与状态管理详设 line 107-115
 *
 * 仅保存当前编辑态引用 ID, 不保存完整实体对象 (大对象由后端 / 接口返回时直接使用)。
 * 包括: 选中实体 ID / 页面草稿引用 / 选中组件 ID。
 */

export const useLowcodeStore = defineStore('lowcode', () => {
  /** 当前选中的实体 ID (引用, 不保存完整 entity 对象) */
  const selectedEntityId = ref<string | null>(null)
  /** 当前页面草稿 (结构轻量, 仅布局引用, 不含完整字段定义) */
  const pageDraft = ref<object | null>(null)
  /** 当前选中的组件 ID (页面设计器中) */
  const selectedComponentId = ref<string | null>(null)

  /** 选中实体 (仅保存 ID 引用) */
  function selectEntity(id: string | null): void {
    selectedEntityId.value = id
  }

  /** 保存页面草稿 (轻量结构, 不含完整字段定义) */
  function saveDraft(draft: object | null): void {
    pageDraft.value = draft
  }

  /**
   * 发布实体 (调用方负责调用 API, 本 store 仅清除草稿)。
   * 实际发布 API 调用由页面组件完成, 避免在 store 中耦合 API 客户端。
   */
  function publish(_entityId: string): void {
    pageDraft.value = null
    selectedComponentId.value = null
  }

  /** 选中组件 */
  function selectComponent(id: string | null): void {
    selectedComponentId.value = id
  }

  return {
    selectedEntityId,
    pageDraft,
    selectedComponentId,
    selectEntity,
    saveDraft,
    publish,
    selectComponent,
  }
})
