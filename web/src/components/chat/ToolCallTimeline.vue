<script setup lang="ts">
import { Finished, Loading, WarningFilled } from '@element-plus/icons-vue'
import type { ChatToolCall } from './types'

/**
 * 工具调用时间线 — 展示一次 assistant 回复中的 MCP/Skill/Function 调用序列。
 * 设计来源: ADR 0004 P2-G (ChatView 内联时间线抽取, 视觉语言与 web-admin ToolCallCard 对齐:
 * 运行中琥珀脉冲 / 成功绿 / 失败红)。
 */

defineProps<{ calls: ChatToolCall[] }>()

function dotClass(status: ChatToolCall['status']) {
  return `is-${status}`
}
</script>

<template>
  <div v-if="calls && calls.length" class="yt-toolCalls" role="status" aria-label="工具调用时间线">
    <div class="yt-toolCalls__head"><el-icon><Finished /></el-icon> 工具调用 · {{ calls.length }} 步</div>
    <div class="yt-toolCalls__timeline">
      <div v-for="tc in calls" :key="tc.id" class="yt-toolCallCard" :class="dotClass(tc.status)">
        <span class="yt-toolCallCard__dot" />
        <span class="yt-toolCallCard__name">{{ tc.name }}</span>
        <span class="yt-toolCallCard__summary">{{ tc.summary || '' }}</span>
        <span v-if="tc.elapsedMs" class="yt-toolCallCard__time">{{ tc.elapsedMs }}ms</span>
        <el-icon v-if="tc.status === 'running'" class="is-loading"><Loading /></el-icon>
        <el-icon v-else-if="tc.status === 'success'" class="is-success"><Finished /></el-icon>
        <el-icon v-else-if="tc.status === 'failed'" class="is-failed"><WarningFilled /></el-icon>
      </div>
    </div>
  </div>
</template>

<style scoped>
.yt-toolCalls {
  margin: 4px 0 4px 42px;
  border: 1px solid var(--yt-border-light);
  border-radius: var(--yt-radius-sm);
  background: var(--yt-bg-page);
  padding: 8px 12px;
}
.yt-toolCalls__head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--yt-text-secondary);
  margin-bottom: 6px;
}
.yt-toolCalls__timeline {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.yt-toolCallCard {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
.yt-toolCallCard__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--yt-border-default);
  flex-shrink: 0;
}
.yt-toolCallCard.is-running .yt-toolCallCard__dot {
  background: var(--yt-color-warning);
  animation: yt-tc-pulse 1.2s infinite;
}
.yt-toolCallCard.is-success .yt-toolCallCard__dot {
  background: var(--yt-color-success);
}
.yt-toolCallCard.is-failed .yt-toolCallCard__dot {
  background: var(--yt-color-danger);
}
.yt-toolCallCard__name {
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  color: var(--yt-text-primary);
  white-space: nowrap;
}
.yt-toolCallCard__summary {
  color: var(--yt-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.yt-toolCallCard__time {
  color: var(--yt-text-secondary);
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.yt-toolCallCard .is-loading {
  color: var(--yt-color-warning);
  animation: yt-tc-spin 1s linear infinite;
}
.yt-toolCallCard .is-success {
  color: var(--yt-color-success);
}
.yt-toolCallCard .is-failed {
  color: var(--yt-color-danger);
}
@keyframes yt-tc-pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}
@keyframes yt-tc-spin {
  to { transform: rotate(360deg); }
}
</style>
