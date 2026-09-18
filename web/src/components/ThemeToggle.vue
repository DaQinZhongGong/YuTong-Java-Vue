<script setup lang="ts">
import { computed } from 'vue'
import { Moon, Sunny, Platform } from '@element-plus/icons-vue'
import { useDesignStore, type DesignMode } from '@/stores/design'

/**
 * 主题切换按钮 — 三态循环 light → dark → auto (用户端)。
 * 设计来源: 50-设计系统 §6 + web-admin ThemeToggle 对等移植 (plain CSS 版)。
 * WCAG: 原生 button 键盘可达 + aria-label。
 */

const designStore = useDesignStore()

const ICONS: Record<DesignMode, typeof Moon> = {
  light: Sunny,
  dark: Moon,
  auto: Platform,
}

const currentIcon = computed(() => ICONS[designStore.mode])
const ariaLabel = computed(() => `当前主题:${designStore.modeLabel},点击切换`)
const tooltipText = computed(() => `主题:${designStore.modeLabel}`)

function handleClick(): void {
  designStore.cycleMode()
}
</script>

<template>
  <el-tooltip :content="tooltipText" placement="bottom" :show-after="300">
    <button
      type="button"
      class="theme-toggle"
      :aria-label="ariaLabel"
      :title="ariaLabel"
      @click="handleClick"
    >
      <el-icon class="theme-toggle__icon" aria-hidden="true">
        <component :is="currentIcon" />
      </el-icon>
    </button>
  </el-tooltip>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--yt-border-default);
  border-radius: 8px;
  color: var(--yt-text-secondary);
  cursor: pointer;
}
.theme-toggle:hover {
  border-color: var(--yt-color-primary);
  color: var(--yt-color-primary);
}
.theme-toggle:focus-visible {
  outline: 2px solid var(--yt-color-primary);
  outline-offset: 2px;
}
.theme-toggle__icon {
  font-size: 16px;
}
</style>
