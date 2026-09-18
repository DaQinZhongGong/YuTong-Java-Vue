<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Paperclip, CircleClose } from '@element-plus/icons-vue'
import type { ChatAttachment } from './types'

/**
 * 附件选择器 — 文件选择 + 已选列表 + 移除 + 数量上限。
 * 设计来源: ADR 0004 P2-G (ChatView 内联附件逻辑抽取)。
 * 上传仍由父组件在发送时执行 (uploadPendingFiles)，本组件只管选择态。
 */

const props = withDefaults(
  defineProps<{
    modelValue: ChatAttachment[]
    maxCount?: number
    disabled?: boolean
  }>(),
  { maxCount: 5, disabled: false },
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: ChatAttachment[]): void
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)

function open() {
  if (!props.disabled) fileInputRef.value?.click()
}

function onFilesSelected(e: Event) {
  const inputEl = e.target as HTMLInputElement
  const files = inputEl.files
  if (!files) return
  let next = [...props.modelValue]
  for (const f of Array.from(files)) {
    next.push({ id: `${Date.now()}-${Math.random().toString(36).slice(2, 6)}`, name: f.name, size: f.size, raw: f })
    if (next.length > props.maxCount) {
      next = next.slice(-props.maxCount)
      ElMessage.warning(`最多保留 ${props.maxCount} 个附件`)
    }
  }
  emit('update:modelValue', next)
  if (inputEl) inputEl.value = ''
}

function removeAttachment(id: string) {
  emit(
    'update:modelValue',
    props.modelValue.filter((a) => a.id !== id),
  )
}

function fmtSize(n: number) {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

defineExpose({ open })
</script>

<template>
  <div class="yt-files">
    <input ref="fileInputRef" type="file" multiple style="display: none" @change="onFilesSelected" />
    <div v-if="modelValue.length" class="yt-senderAttachments">
      <div v-for="a in modelValue" :key="a.id" class="yt-attachCard">
        <el-icon><Paperclip /></el-icon>
        <span class="yt-attachCard__name" :title="a.name">{{ a.name }}</span>
        <span class="yt-attachCard__size">{{ fmtSize(a.size) }}</span>
        <el-button text size="small" :icon="CircleClose" :disabled="disabled" aria-label="移除附件" @click="removeAttachment(a.id)" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.yt-senderAttachments {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 4px 0 8px;
}
.yt-attachCard {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px 4px 10px;
  border: 1px solid var(--yt-border-default);
  border-radius: var(--yt-radius-sm);
  background: var(--yt-bg-page);
  max-width: 240px;
}
.yt-attachCard__name {
  font-size: 12px;
  color: var(--yt-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.yt-attachCard__size {
  font-size: 11px;
  color: var(--yt-text-secondary);
  flex-shrink: 0;
}
</style>
