<script setup lang="ts">
import { ref } from 'vue'
import { useThemeColorStore, THEME_COLORS } from '@/stores/themeColor'

const themeStore = useThemeColorStore()
const showPicker = ref(false)

function handleSelect(color: string) {
  themeStore.setColor(color)
  showPicker.value = false
}
</script>

<template>
  <div class="theme-color-picker">
    <button class="trigger-btn" title="主题色" @click="showPicker = !showPicker">
      <span class="color-dot" :style="{ background: themeStore.primaryColor }" />
    </button>
    <div v-if="showPicker" class="color-panel">
      <div class="panel-header">主题色</div>
      <div class="color-grid">
        <button
          v-for="color in THEME_COLORS"
          :key="color.value"
          class="color-item"
          :class="{ active: themeStore.primaryColor === color.value }"
          :title="color.name"
          @click="handleSelect(color.value)"
        >
          <span class="color-swatch" :style="{ background: color.value }" />
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.theme-color-picker {
  position: relative;
}
.trigger-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid var(--yt-border-default, #e5e7eb);
  background: var(--yt-bg-card, #fff);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}
.color-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: block;
}
.color-panel {
  position: absolute;
  top: 40px;
  right: 0;
  background: var(--yt-bg-card, #fff);
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 10px;
  padding: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  z-index: 100;
  width: 200px;
}
.panel-header {
  font-size: 13px;
  font-weight: 600;
  color: var(--yt-text-primary, #111827);
  margin-bottom: 10px;
}
.color-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}
.color-item {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 2px;
  background: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
.color-item:hover {
  border-color: var(--yt-border-default, #e5e7eb);
}
.color-item.active {
  border-color: var(--yt-color-primary, #2563eb);
}
.color-swatch {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: block;
}
</style>
