<script setup lang="ts">
/**
 * StatusTag 状态展示组件 (兼容别名)
 * 设计来源: 66-前端组件API与状态管理详设 line 54-56
 *
 * 历史 StatusTag 别名, 新增代码请使用 StatusBadge。
 * 本组件仅为兼容保留, 内部直接复用 StatusBadge 实现。
 */
import StatusBadge from './StatusBadge.vue'

interface Props {
  type: 'success' | 'warning' | 'danger' | 'info' | 'primary'
  label: string
}

defineProps<Props>()
</script>

<template>
  <StatusBadge :type="type" :label="label" />
</template>
