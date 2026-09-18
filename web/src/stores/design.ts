import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/**
 * YuTong 主题模式存储 (用户端)。
 * 设计来源: 50-设计系统 §6 + web-admin stores/modules/design.ts 对等移植。
 * 入口: <ThemeToggle /> (三态 light / dark / auto)。
 * DOM 副作用: isDark 变化 → `<html>.classList.toggle('dark')`，命中共享 dark.css 覆盖。
 * 持久化: localStorage 键 `yutong:design-mode` (与管理端共享键值，同源多端一致)。
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
    // localStorage 不可用时降级 light
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

  const isDark = computed<boolean>(() => {
    if (mode.value === 'auto') {
      return systemPrefersDark()
    }
    return mode.value === 'dark'
  })

  const modeLabel = computed<string>(() => {
    if (mode.value === 'light') return '浅色'
    if (mode.value === 'dark') return '深色'
    return '跟随系统'
  })

  function cycleMode(): void {
    mode.value = mode.value === 'light' ? 'dark' : mode.value === 'dark' ? 'auto' : 'light'
  }

  // 初始化即应用，避免首屏闪烁由默认浅色开始
  applyToDom(
    mode.value === 'auto' ? systemPrefersDark() : mode.value === 'dark',
  )

  watch(isDark, (v) => applyToDom(v))

  // auto 模式跟随系统变化
  if (typeof window !== 'undefined' && window.matchMedia) {
    try {
      const mql = window.matchMedia('(prefers-color-scheme: dark)')
      const onChange = () => {
        if (mode.value === 'auto') applyToDom(mql.matches)
      }
      if (typeof mql.addEventListener === 'function') {
        mql.addEventListener('change', onChange)
      } else {
        ;(mql as unknown as { addListener: (cb: () => void) => void }).addListener(onChange)
      }
    } catch {
      // 旧浏览器忽略
    }
  }

  return { mode, isDark, modeLabel, cycleMode }
})
