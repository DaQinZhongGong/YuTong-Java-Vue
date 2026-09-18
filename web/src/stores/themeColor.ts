import { defineStore } from 'pinia'
import { ref, watch, computed } from 'vue'

/**
 * YuTong 主题色存储 (用户端)。
 * 落点: 业界同类实现 19 主题色 + ADR 0005 P2。
 * 持久化: localStorage 键 `yutong:theme-color`。
 * DOM 副作用: 设置 CSS 变量 `--yt-color-primary` 等。
 */

export interface ThemeColor {
  name: string
  value: string
  light: string
}

/** 19 种预设主题色 (参考 业界同类实现 + Element Plus 调色板) */
export const THEME_COLORS: ThemeColor[] = [
  { name: '经典蓝', value: '#2563eb', light: '#3b82f6' },
  { name: '深海蓝', value: '#1e40af', light: '#3b82f6' },
  { name: '天空蓝', value: '#0ea5e9', light: '#38bdf8' },
  { name: '翡翠绿', value: '#059669', light: '#10b981' },
  { name: '松石绿', value: '#0d9488', light: '#14b8a6' },
  { name: '琥珀橙', value: '#d97706', light: '#f59e0b' },
  { name: '珊瑚红', value: '#dc2626', light: '#ef4444' },
  { name: '玫瑰粉', value: '#e11d48', light: '#f43f5e' },
  { name: '紫罗兰', value: '#7c3aed', light: '#8b5cf6' },
  { name: '靛蓝', value: '#4f46e5', light: '#6366f1' },
  { name: '石墨黑', value: '#1f2937', light: '#4b5563' },
  { name: '暖灰', value: '#6b7280', light: '#9ca3af' },
  { name: '柠檬黄', value: '#ca8a04', light: '#eab308' },
  { name: '青柠绿', value: '#65a30d', light: '#84cc16' },
  { name: '天际青', value: '#0891b2', light: '#06b6d4' },
  { name: '品红', value: '#c026d3', light: '#d946ef' },
  { name: '砖红', value: '#b91c1c', light: '#dc2626' },
  { name: '橄榄绿', value: '#4d7c0f', light: '#65a30d' },
  { name: '午夜蓝', value: '#1e3a5f', light: '#2563eb' },
]

const STORAGE_KEY = 'yutong:theme-color'

function loadInitialColor(): string {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored && THEME_COLORS.some(c => c.value === stored)) {
      return stored
    }
  } catch {
    // localStorage 不可用
  }
  return '#2563eb' // 默认经典蓝
}

function applyColorToDom(color: string): void {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.style.setProperty('--yt-color-primary', color)
  // 派生色: 浅色变体 (hover) 和深色变体 (active)
  const light = adjustBrightness(color, 1.2)
  const dark = adjustBrightness(color, 0.8)
  root.style.setProperty('--yt-color-primary-light', light)
  root.style.setProperty('--yt-color-primary-dark', dark)
  // Element Plus 主题桥接
  root.style.setProperty('--el-color-primary', color)
  root.style.setProperty('--el-color-primary-light-3', light)
  root.style.setProperty('--el-color-primary-light-5', adjustBrightness(color, 1.4))
  root.style.setProperty('--el-color-primary-light-7', adjustBrightness(color, 1.6))
  root.style.setProperty('--el-color-primary-light-8', adjustBrightness(color, 1.7))
  root.style.setProperty('--el-color-primary-light-9', adjustBrightness(color, 1.8))
  root.style.setProperty('--el-color-primary-dark-2', dark)
}

/** 调整颜色亮度 (简单实现, 正数变亮, 负数变暗) */
function adjustBrightness(hex: string, factor: number): string {
  try {
    const r = parseInt(hex.slice(1, 3), 16)
    const g = parseInt(hex.slice(3, 5), 16)
    const b = parseInt(hex.slice(5, 7), 16)
    const nr = Math.min(255, Math.max(0, Math.round(r * factor)))
    const ng = Math.min(255, Math.max(0, Math.round(g * factor)))
    const nb = Math.min(255, Math.max(0, Math.round(b * factor)))
    return `#${nr.toString(16).padStart(2, '0')}${ng.toString(16).padStart(2, '0')}${nb.toString(16).padStart(2, '0')}`
  } catch {
    return hex
  }
}

export const useThemeColorStore = defineStore('themeColor', () => {
  const primaryColor = ref<string>(loadInitialColor())

  const currentColor = computed(() =>
    THEME_COLORS.find(c => c.value === primaryColor.value) || THEME_COLORS[0]
  )

  function setColor(color: string): void {
    if (!THEME_COLORS.some(c => c.value === color)) return
    primaryColor.value = color
    try {
      localStorage.setItem(STORAGE_KEY, color)
    } catch {
      // ignore
    }
    applyColorToDom(color)
  }

  // 初始化即应用
  applyColorToDom(primaryColor.value)

  watch(primaryColor, (v) => applyColorToDom(v))

  return { primaryColor, currentColor, setColor, THEME_COLORS }
})
