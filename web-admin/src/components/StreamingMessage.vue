<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, User, Service } from '@element-plus/icons-vue'

export interface CitationChip {
  title?: string
  source?: string
  score?: number
  chunkTextPreview?: string
  docTitle?: string
  sectionPath?: string
}

interface Props {
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  citations?: CitationChip[]
  tokens?: number
  latencyMs?: number
  showCopy?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  streaming: false,
  citations: () => [],
  showCopy: true,
})

const copied = ref(false)

const scoreColor = (s: number) => {
  if (s >= 0.7) return 'var(--yt-color-success)'
  if (s >= 0.45) return 'var(--yt-color-primary)'
  return 'var(--yt-color-warning)'
}

const footerText = computed(() => {
  const parts: string[] = []
  if (props.tokens != null) parts.push(`${props.tokens} tokens`)
  if (props.latencyMs != null) parts.push(`${props.latencyMs}ms`)
  return parts.join(' · ')
})

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.content || '')
    copied.value = true
    ElMessage.success('已复制')
    setTimeout(() => (copied.value = false), 1200)
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<template>
  <div class="yt-streaming-msg" :class="`yt-streaming-msg--${role}`">
    <div class="yt-streaming-msg__avatar" :class="`yt-streaming-msg__avatar--${role}`">
      <el-icon v-if="role === 'user'"><User /></el-icon>
      <el-icon v-else><Service /></el-icon>
    </div>
    <div class="yt-streaming-msg__body">
      <div class="yt-streaming-msg__bubble" :class="{ 'is-streaming': streaming }">
        <div class="yt-streaming-msg__content" style="white-space: pre-wrap; word-break: break-word">
          {{ content }}<span v-if="streaming" class="yt-streaming-msg__cursor">▋</span>
        </div>
        <el-button
          v-if="showCopy && role === 'assistant' && content"
          class="yt-streaming-msg__copy"
          text
          size="small"
          :icon="CopyDocument"
          @click="handleCopy"
        >
          {{ copied ? '已复制' : '复制' }}
        </el-button>
      </div>

      <div v-if="citations && citations.length" class="yt-streaming-msg__citations">
        <el-tag
          v-for="(c, i) in citations"
          :key="i"
          size="small"
          effect="plain"
          class="yt-streaming-msg__chip"
          :title="c.chunkTextPreview || c.title || c.docTitle || ''"
        >
          <span class="yt-streaming-msg__chip-index">[{{ i + 1 }}]</span>
          {{ c.docTitle || c.title || c.source || '引用' }}
          <span v-if="c.sectionPath" class="yt-streaming-msg__chip-section"> · {{ c.sectionPath }}</span>
          <span v-if="c.score != null" class="yt-streaming-msg__chip-score" :style="{ color: scoreColor(c.score as number) }"> {{ (Number(c.score) * 100).toFixed(1) }}%</span>
        </el-tag>
      </div>

      <div v-if="footerText" class="yt-streaming-msg__footer">{{ footerText }}</div>
    </div>
  </div>
</template>

<style scoped>
.yt-streaming-msg {
  display: flex;
  gap: var(--yt-space-sm);
  align-items: flex-start;
}
.yt-streaming-msg--user { flex-direction: row-reverse; }
.yt-streaming-msg__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: var(--yt-font-size-body);
  border: 1px solid var(--yt-border-default);
}
.yt-streaming-msg__avatar--user {
  background: var(--yt-color-primary);
  color: var(--yt-bg-card);
  border-color: var(--yt-color-primary-dark-2);
}
.yt-streaming-msg__avatar--assistant {
  background: var(--yt-bg-card);
  color: var(--yt-text-secondary);
  border-color: var(--yt-border-light);
}
.yt-streaming-msg__body {
  max-width: 78%;
  display: flex;
  flex-direction: column;
  gap: var(--yt-space-xs);
}
.yt-streaming-msg--user .yt-streaming-msg__body { align-items: flex-end; }
.yt-streaming-msg__bubble {
  position: relative;
  padding: var(--yt-space-sm) var(--yt-space-md);
  border-radius: var(--yt-radius-md);
  font-size: var(--yt-font-size-body);
  line-height: var(--yt-font-line-height-body);
  box-shadow: var(--yt-shadow-card);
}
.yt-streaming-msg--user .yt-streaming-msg__bubble {
  background: var(--yt-color-primary);
  color: var(--yt-bg-card);
  border-bottom-right-radius: var(--yt-radius-sm);
}
.yt-streaming-msg--assistant .yt-streaming-msg__bubble {
  background: var(--yt-bg-card);
  color: var(--yt-text-primary);
  border: 1px solid var(--yt-border-default);
  border-bottom-left-radius: var(--yt-radius-sm);
}
.yt-streaming-msg__cursor {
  animation: yt-blink 1s step-end infinite;
  margin-left: 2px;
}
@keyframes yt-blink { 50% { opacity: 0; } }
.yt-streaming-msg__copy {
  position: absolute;
  right: var(--yt-space-xs);
  bottom: var(--yt-space-xs);
  opacity: 0;
  transition: opacity var(--yt-transition-hover);
  color: var(--yt-text-secondary);
}
.yt-streaming-msg__bubble:hover .yt-streaming-msg__copy { opacity: 1; }
.yt-streaming-msg__citations {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yt-space-xs);
}
.yt-streaming-msg__chip {
  background: var(--yt-color-primary-light-9);
  border-color: var(--yt-color-primary-light-7);
  color: var(--yt-color-primary-dark-2);
  border-radius: var(--yt-radius-sm);
}
.yt-streaming-msg__chip-index { font-weight: var(--yt-font-weight-bold); margin-right: 2px; }
.yt-streaming-msg__chip-section { color: var(--yt-text-secondary); font-size: var(--yt-font-size-caption); }
.yt-streaming-msg__chip-score { font-size: var(--yt-font-size-caption); margin-left: 4px; }
.yt-streaming-msg__footer {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
  line-height: var(--yt-font-line-height-caption);
}
</style>
