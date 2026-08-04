import { defineStore } from 'pinia'
import { ref } from 'vue'
import i18n from '@/locales'

/**
 * App Store
 * 设计来源: 66-前端组件API与状态管理详设 line 107-115
 *
 * 全局应用状态: 主题 / 侧边栏 / 语言 / 标签页。
 * 仅持久化 theme/locale 到 localStorage, 不保存大对象或敏感数据。
 */

export type Theme = 'light' | 'dark'
export type Locale = 'zh-CN' | 'en-US'

export interface Tab {
  /** 路由 path, 唯一标识 */
  path: string
  /** 标签页标题 */
  title: string
  /** 路由 name */
  name?: string
  /** 是否可关闭 */
  closable?: boolean
}

const THEME_KEY = 'yutong_theme'
const LOCALE_KEY = 'yutong_locale'
const SIDEBAR_KEY = 'yutong_sidebar_collapsed'

function readTheme(): Theme {
  const v = localStorage.getItem(THEME_KEY)
  return v === 'dark' ? 'dark' : 'light'
}

function readLocale(): Locale {
  const v = localStorage.getItem(LOCALE_KEY)
  return v === 'en-US' ? 'en-US' : 'zh-CN'
}

function readSidebarCollapsed(): boolean {
  return localStorage.getItem(SIDEBAR_KEY) === 'true'
}

function applyThemeToDom(theme: Theme) {
  const el = document.documentElement
  if (theme === 'dark') {
    el.classList.add('dark')
  } else {
    el.classList.remove('dark')
  }
}

export const useAppStore = defineStore('app', () => {
  const theme = ref<Theme>(readTheme())
  const sidebarCollapsed = ref<boolean>(readSidebarCollapsed())
  const locale = ref<Locale>(readLocale())
  const tabs = ref<Tab[]>([])

  // 初始化时同步 DOM 主题类
  applyThemeToDom(theme.value)
  // 同步 i18n locale
  i18n.global.locale.value = locale.value

  /** 切换主题 (light/dark), 持久化并应用 DOM class */
  function toggleTheme(): void {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    localStorage.setItem(THEME_KEY, theme.value)
    applyThemeToDom(theme.value)
  }

  /** 设置语言, 持久化并同步 i18n */
  function setLocale(loc: Locale): void {
    locale.value = loc
    localStorage.setItem(LOCALE_KEY, loc)
    i18n.global.locale.value = loc
  }

  /** 切换侧边栏折叠状态, 持久化 */
  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem(SIDEBAR_KEY, String(sidebarCollapsed.value))
  }

  /** 新增标签页 (path 重复则不重复添加) */
  function addTab(tab: Tab): void {
    if (!tab || !tab.path) return
    if (tabs.value.some((t) => t.path === tab.path)) return
    tabs.value.push(tab)
  }

  /** 移除标签页 (按 path) */
  function removeTab(path: string): void {
    tabs.value = tabs.value.filter((t) => t.path !== path)
  }

  return {
    theme,
    sidebarCollapsed,
    locale,
    tabs,
    toggleTheme,
    setLocale,
    toggleSidebar,
    addTab,
    removeTab,
  }
})
