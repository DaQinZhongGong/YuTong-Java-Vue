<script setup lang="ts">
/**
 * 主题切换按钮 — 三态循环 light → dark → auto
 * 落点:50-设计系统与视觉规范详设 §6 主题切换
 * 复用 vue-element-plus-x 风格的 Sun/Moon/Platform 图标
 *
 * WCAG 2.1.1: 键盘可达,role=button + aria-label
 * WCAG 1.4.11: focus 对比度沿用全局 token
 */
import { Moon, Sunny, Platform } from '@element-plus/icons-vue'
import { useDesignStore, type DesignMode } from '@/stores/modules/design'

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

<style scoped lang="scss">
.theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  background: transparent;
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 8px;
  color: var(--yt-text-secondary, #4b5563);
  cursor: pointer;
  transition:
    color 0.18s ease-out,
    background-color 0.18s ease-out,
    border-color 0.18s ease-out,
    transform 0.18s ease-out;

  &:hover {
    color: var(--yt-color-primary, #2563eb);
    background-color: var(--yt-color-primary-light-9, #e2ecfe);
    border-color: var(--yt-color-primary-light-5, #93b1f7);
  }

  &:active {
    transform: scale(0.96);
  }

  &:focus-visible {
    outline: 2px solid var(--yt-color-primary, #2563eb);
    outline-offset: 2px;
  }

  &__icon {
    font-size: 18px;
  }
}
</style>
