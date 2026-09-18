<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'

/**
 * 深度思考开关 — enableThinking 状态 + localStorage 持久化。
 * 设计来源: ADR 0004 P2-G (ChatView 内联 DeepThinking 开关抽取)。
 * 开启后父组件在 streamChat body 透传 enableThinking=true。
 */

const STORAGE_KEY = 'yt_enableThinking'

const props = defineProps<{ modelValue: boolean }>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const inner = ref(props.modelValue)

onMounted(() => {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v !== null && (v === '1') !== props.modelValue) {
      inner.value = v === '1'
      emit('update:modelValue', inner.value)
    }
  } catch {
    // 无痕模式等场景忽略持久化
  }
})

watch(inner, (v) => {
  emit('update:modelValue', v)
  try {
    localStorage.setItem(STORAGE_KEY, v ? '1' : '0')
  } catch {
    // 忽略持久化失败
  }
})

watch(
  () => props.modelValue,
  (v) => {
    if (v !== inner.value) inner.value = v
  },
)
</script>

<template>
  <div class="yt-thinkingToggle">
    <el-switch
      v-model="inner"
      inline-prompt
      active-text="深度思考"
      inactive-text="普通"
      style="--el-switch-on-color: var(--yt-color-primary)"
      aria-label="深度思考开关"
    />
    <el-tooltip
      content="开启后，服务端启用更深度的推理与工具链（enableThinking=true 透传），响应更详细但稍慢"
      placement="top"
    >
      <span class="yt-chat__controlHint">DeepThinking</span>
    </el-tooltip>
  </div>
</template>

<style scoped>
.yt-thinkingToggle {
  display: flex;
  align-items: center;
  gap: 8px;
}
.yt-chat__controlHint {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
  border: 1px dashed var(--yt-border-default);
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--yt-bg-card);
}
</style>
