import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listModuleStatuses, type ModuleStatus } from '@/api/license'

/**
 * GA2-L171: 商业授权模块状态 Store。
 * 设计来源: 70-商业授权与版本能力裁剪详设「模块授权行为矩阵」、
 * contracts/governance/commercial-license-trimming.yaml#moduleAuthBehaviorMatrix。
 *
 * 启动时 (登录后首次进入) 调用 GET /license/modules 加载模块状态矩阵，
 * 菜单过滤器和路由守卫据此隐藏未授权模块的入口。
 *
 * backendBoundary (70 号文档): 后端 LicenseInterceptor 是强制边界，
 * 前端菜单隐藏只是体验优化 (防止用户点击后看到 403 错误)。
 */
export const useLicenseStore = defineStore('license', () => {
  /** 模块状态列表 (从后端 /license/modules 获取) */
  const moduleStatuses = ref<ModuleStatus[]>([])
  /** 是否已加载 (避免路由守卫重复拉取) */
  const loaded = ref(false)

  /** 模块编码 → 状态的 Map (便于 O(1) 查询) */
  const moduleMap = computed<Map<string, ModuleStatus>>(() => {
    const map = new Map<string, ModuleStatus>()
    for (const m of moduleStatuses.value) {
      map.set(m.moduleCode, m)
    }
    return map
  })

  /**
   * 拉取所有模块的授权状态。
   * 设计来源: 70 号文档「授权校验流程 - 启动校验」。
   */
  async function fetchModuleStatuses(): Promise<void> {
    moduleStatuses.value = await listModuleStatuses()
    loaded.value = true
  }

  /**
   * 判断模块是否激活 (licensed && switchOn)。
   * 未加载时默认 true (不阻断基础功能，后端拦截器是最终防线)。
   *
   * @param moduleCode 模块编码 (lowcode/ai/report/workflow/datasource/plugin)
   */
  function isModuleActive(moduleCode: string): boolean {
    if (!loaded.value) {
      // 未加载时不拦截 (后端 LicenseInterceptor 是强制边界)
      return true
    }
    const status = moduleMap.value.get(moduleCode)
    return status ? status.active : true
  }

  /** 重置状态 (登出时调用) */
  function reset(): void {
    moduleStatuses.value = []
    loaded.value = false
  }

  return {
    moduleStatuses,
    loaded,
    moduleMap,
    fetchModuleStatuses,
    isModuleActive,
    reset,
  }
})
