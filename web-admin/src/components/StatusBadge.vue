<script setup lang="ts">
/**
 * StatusBadge 状态展示组件
 * 设计来源: 66-前端组件API与状态管理详设 line 54-56 + base.css line 80-134
 *
 * 统一状态展示, 文案来自字典或 i18n key, 颜色来自 50 号 token。
 * 历史 StatusTag 可作为兼容别名保留一个小版本, 但新增代码一律使用 StatusBadge。
 */
interface Props {
  type: 'success' | 'warning' | 'danger' | 'info' | 'primary'
  label: string
}

defineProps<Props>()
</script>

<template>
  <span class="yt-status-badge" :class="`yt-status-badge--${type}`">{{ label }}</span>
</template>
