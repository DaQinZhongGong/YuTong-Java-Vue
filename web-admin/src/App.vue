<script setup lang="ts">
/**
 * 根组件入口
 * - 主题模式初始化(在 mount 前同步 DOM,避免暗色页面闪白)
 * - 设计来源:50-设计系统与视觉规范详设 §6 主题切换
 */
import { onBeforeMount } from 'vue'
import { useDesignStore } from '@/stores/modules/design'

const designStore = useDesignStore()

onBeforeMount(() => {
  designStore.init()
})
</script>

<template>
  <router-view />
</template>
