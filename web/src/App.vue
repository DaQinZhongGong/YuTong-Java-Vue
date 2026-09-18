<script setup lang="ts">
import { useRoute } from 'vue-router'
import ThemeColorPicker from '@/components/ThemeColorPicker.vue'
const route = useRoute()
</script>

<template>
  <el-container style="height: 100vh">
    <el-header style="border-bottom: 1px solid var(--yt-border-default, #e5e7eb); display:flex; align-items:center; justify-content:space-between; background: var(--yt-bg-card, #fff)">
      <div style="display:flex; align-items:center; gap: 20px">
        <strong style="color: var(--yt-color-primary, #2563eb); font-size: 18px">YuTong</strong>
        <el-menu :default-active="route.path" mode="horizontal" router :ellipsis="false" style="border: none">
          <el-menu-item index="/chat">对话</el-menu-item>
          <el-menu-item index="/knowledge">知识问答</el-menu-item>
          <el-menu-item index="/media">媒体</el-menu-item>
          <el-menu-item index="/store">商店</el-menu-item>
          <el-menu-item index="/cms">内容</el-menu-item>
        </el-menu>
      </div>
      <div style="display:flex; align-items:center; gap: 12px">
        <ThemeColorPicker />
        <el-tag type="info" size="small">C端用户版 · 20051</el-tag>
      </div>
    </el-header>
    <el-main style="background: var(--yt-bg-page, #f6f8fb); padding: 20px; overflow:auto">
      <router-view v-slot="{ Component }">
        <transition name="yt-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </el-main>
  </el-container>
</template>

<style>
html, body, #app { height: 100%; margin: 0; }

/* 路由切换淡入淡出 (P2-K 微动效, 时长走 --yt-motion-* 令牌, 降级 150ms) */
.yt-fade-enter-active { transition: opacity var(--yt-motion-fade, 150ms) var(--yt-ease-out, ease-out); }
.yt-fade-leave-active { transition: opacity var(--yt-motion-fade, 150ms) var(--yt-ease-out, ease-out); }
.yt-fade-enter-from, .yt-fade-leave-to { opacity: 0; }
</style>
