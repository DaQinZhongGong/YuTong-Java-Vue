import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/**
 * YuTong 主题模式存储
 *
 * 落点:50-设计系统与视觉规范详设 §6 主题切换 + DESIGN.md
 * 入口:<ThemeToggle /> 组件(三态:light / dark / auto)
 * DOM 副作用:mode 变化 → 立即 `<html>.classList.toggle('dark', isDark)`
 * 持久化:localStorage 键 `yutong:design-mode`
 *
 * auto 模式:跟系统 `prefers-color-scheme` 媒体查询,变化时实时跟随
 */
export type DesignMode = 'light' | 'dark' | 'auto'

const STORAGE_KEY = 'yutong:design-mode'

function loadInitialMode(): DesignMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'auto') {
      return stored
    }
  } catch {
    // localStorage 不可用(SSR/隐私模式),降级 light
  }
  return 'light'
}

function systemPrefersDark(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) {
    return false
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyToDom(isDark: boolean): void {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  if (isDark) {
    root.classList.add('dark')
    root.setAttribute('data-theme', 'dark')
  } else {
    root.classList.remove('dark')
    root.setAttribute('data-theme', 'light')
  }
}

export const useDesignStore = defineStore('design', () => {
  const mode = ref<DesignMode>(loadInitialMode())

  // 实际是否暗色 = mode=auto 时跟随系统,否则按 mode
  const isDark = computed<boolean>(() => {
    if (mode.value === 'auto') {
      return systemPrefersDark()
    }
    return mode.value === 'dark'
  })

  // 当前主题描述(用于 aria-label / tooltip)
  const modeLabel = computed<string>(() => {
    if (mode.value === 'light') return '浅色'
    if (mode.value === 'dark') return '深色'
    return '跟随系统'
  })

  function setMode(next: DesignMode): void {
    if (next !== 'light' && next !== 'dark' && next !== 'auto') return
    mode.value = next
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // ignore — 持久化失败不影响主题应用
    }
  }

  /** 三态循环:light → dark → auto → light */
  function cycleMode(): void {
    const order: DesignMode[] = ['light', 'dark', 'auto']
    const idx = order.indexOf(mode.value)
    setMode(order[(idx + 1) % order.length])
  }

  /**
   * 初始化:挂载到 Pinia 时立刻同步 DOM,并订阅系统主题变化
   * 应在 App.vue `onBeforeMount` 之前调用,避免页面闪烁
   */
  function init(): void {
    applyToDom(isDark.value)
    if (typeof window === 'undefined' || !window.matchMedia) return
    const mql = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = (e: MediaQueryListEvent) => {
      if (mode.value === 'auto') {
        applyToDom(e.matches)
      }
    }
    // 现代浏览器用 addEventListener,旧 Safari(<14)用 addListener 兜底
    if (mql.addEventListener) {
      mql.addEventListener('change', handler)
    } else {
      // 旧 API 兜底 (Safari < 14),使用类型断言绕过 DOM 类型差异
      (mql as unknown as {
        addListener: (cb: (e: MediaQueryListEvent) => void) => void
      }).addListener(handler)
    }
  }

  // isDark 变化 → 立即同步 DOM (auto 模式也会响应 mode 切换)
  watch(isDark, (v) => applyToDom(v), { immediate: false })

  return {
    mode,
    isDark,
    modeLabel,
    setMode,
    cycleMode,
    init,
  }
})
