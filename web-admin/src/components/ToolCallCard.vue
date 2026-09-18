<script setup lang="ts">
/**
 * 工具调用卡片 — 展示 MCP / Skill / Function Calling 的调用名/参数/结果/耗时
 * 设计来源: rouoyi-web ToolCallCard + DESIGN.md
 *
 * 落点:50-设计系统 + 49-AI 工具调用可观测性
 * 复用:AiChat.vue / AgentList / CopilotDraft 等需要展示工具调用的页面
 *
 * 状态:RUNNING(脉冲蓝)/ SUCCESS(松绿)/ FAILED(红)/ SKIPPED(灰)
 * WCAG:aria-label / role=status / focus 环 token
 */
import { CaretRight, CircleCheck, CircleClose, Loading, VideoPlay } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'

type ToolStatus = 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED'

const props = withDefaults(
  defineProps<{
    /** 工具/MCP/Skill 名(如 mcp__playwright__click / Skill.docx.parse) */
    name: string
    /** 调用参数(对象,自动 JSON 序列化) */
    params?: Record<string, unknown>
    /** 调用结果(对象 / 字符串) */
    result?: unknown
    /** 状态 */
    status?: ToolStatus
    /** 耗时(ms),用于展示 */
    durationMs?: number
    /** 失败原因 */
    error?: string
  }>(),
  {
    params: () => ({}),
    status: 'SUCCESS',
  },
)

const expanded = ref(false)

const statusType = computed<'success' | 'warning' | 'danger' | 'info'>(() => {
  switch (props.status) {
    case 'SUCCESS':
      return 'success'
    case 'RUNNING':
      return 'warning'
    case 'FAILED':
      return 'danger'
    default:
      return 'info'
  }
})

const statusLabel = computed<string>(() => {
  switch (props.status) {
    case 'SUCCESS':
      return '成功'
    case 'RUNNING':
      return '执行中'
    case 'FAILED':
      return '失败'
    default:
      return '已跳过'
  }
})

const statusIcon = computed(() => {
  switch (props.status) {
    case 'SUCCESS':
      return CircleCheck
    case 'RUNNING':
      return Loading
    case 'FAILED':
      return CircleClose
    default:
      return VideoPlay
  }
})

const durationText = computed<string>(() => {
  if (props.durationMs == null) return ''
  if (props.durationMs < 1000) return `${props.durationMs}ms`
  return `${(props.durationMs / 1000).toFixed(2)}s`
})

const paramsJson = computed<string>(() => {
  try {
    return JSON.stringify(props.params, null, 2)
  } catch {
    return String(props.params)
  }
})

const resultJson = computed<string>(() => {
  if (props.result == null) return ''
  if (typeof props.result === 'string') return props.result
  try {
    return JSON.stringify(props.result, null, 2)
  } catch {
    return String(props.result)
  }
})

const hasDetail = computed<boolean>(() => {
  return Object.keys(props.params).length > 0 || props.result != null || !!props.error
})

const isError = computed<boolean>(() => props.status === 'FAILED')

function toggle(): void {
  if (hasDetail.value) {
    expanded.value = !expanded.value
  }
}
</script>

<template>
  <div
    class="tool-call-card"
    :class="{
      'tool-call-card--running': status === 'RUNNING',
      'tool-call-card--failed': isError,
    }"
    role="status"
    :aria-label="`工具调用 ${name} ${statusLabel}`"
  >
    <button
      type="button"
      class="tool-call-card__header"
      :aria-expanded="hasDetail ? expanded : undefined"
      :aria-controls="hasDetail ? `tcc-body-${name}` : undefined"
      :disabled="!hasDetail"
      @click="toggle"
    >
      <el-icon class="tool-call-card__caret" :class="{ 'is-expanded': expanded }" aria-hidden="true">
        <CaretRight />
      </el-icon>
      <el-icon class="tool-call-card__status-icon" :class="`is-${status.toLowerCase()}`" aria-hidden="true">
        <component :is="statusIcon" :class="{ 'is-spin': status === 'RUNNING' }" />
      </el-icon>
      <span class="tool-call-card__name">{{ name }}</span>
      <el-tag v-if="status" :type="statusType" size="small" effect="plain" round class="tool-call-card__tag">
        {{ statusLabel }}
      </el-tag>
      <span v-if="durationText" class="tool-call-card__duration">{{ durationText }}</span>
    </button>
    <div v-if="hasDetail && expanded" :id="`tcc-body-${name}`" class="tool-call-card__body">
      <div v-if="Object.keys(params).length > 0" class="tool-call-card__section">
        <div class="tool-call-card__section-title">入参</div>
        <pre class="tool-call-card__pre"><code>{{ paramsJson }}</code></pre>
      </div>
      <div v-if="result != null" class="tool-call-card__section">
        <div class="tool-call-card__section-title">出参</div>
        <pre class="tool-call-card__pre"><code>{{ resultJson }}</code></pre>
      </div>
      <div v-if="error" class="tool-call-card__section">
        <div class="tool-call-card__section-title tool-call-card__section-title--danger">错误</div>
        <pre class="tool-call-card__pre tool-call-card__pre--danger"><code>{{ error }}</code></pre>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.tool-call-card {
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 8px;
  background: var(--yt-bg-card, #ffffff);
  margin: 8px 0;
  transition: border-color 0.18s ease-out, box-shadow 0.18s ease-out;

  &--running {
    border-color: var(--yt-color-warning, #d97706);
  }
  &--failed {
    border-color: var(--yt-color-danger, #dc2626);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    padding: 8px 12px;
    background: transparent;
    border: none;
    cursor: pointer;
    text-align: left;
    color: var(--yt-text-primary, #111827);
    font-size: 13px;

    &:disabled {
      cursor: default;
    }

    &:focus-visible {
      outline: 2px solid var(--yt-color-primary, #2563eb);
      outline-offset: -2px;
    }
  }

  &__caret {
    font-size: 12px;
    color: var(--yt-text-secondary, #4b5563);
    transition: transform 0.18s ease-out;
    &.is-expanded {
      transform: rotate(90deg);
    }
  }

  &__status-icon {
    font-size: 16px;
    &.is-running {
      color: var(--yt-color-warning, #d97706);
    }
    &.is-success {
      color: var(--yt-color-success, #16a34a);
    }
    &.is-failed {
      color: var(--yt-color-danger, #dc2626);
    }
    &.is-skipped {
      color: var(--yt-text-disabled, #9ca3af);
    }
    .is-spin {
      animation: tcc-spin 1s linear infinite;
    }
  }

  &__name {
    flex: 1;
    font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
    font-size: 13px;
    color: var(--yt-text-primary, #111827);
  }

  &__tag {
    flex-shrink: 0;
  }

  &__duration {
    font-size: 12px;
    color: var(--yt-text-secondary, #4b5563);
    font-variant-numeric: tabular-nums;
  }

  &__body {
    padding: 0 12px 12px 12px;
    border-top: 1px dashed var(--yt-border-light, #f1f5f9);
    margin-top: 4px;
  }

  &__section {
    margin-top: 8px;

    &-title {
      font-size: 11px;
      font-weight: 600;
      color: var(--yt-text-secondary, #4b5563);
      margin-bottom: 4px;
      text-transform: uppercase;
      letter-spacing: 0.04em;

      &--danger {
        color: var(--yt-color-danger, #dc2626);
      }
    }
  }

  &__pre {
    margin: 0;
    padding: 8px 10px;
    background: var(--yt-color-primary-light-9, #e2ecfe);
    border-radius: 4px;
    font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
    color: var(--yt-text-primary, #111827);
    overflow-x: auto;
    max-height: 240px;
    overflow-y: auto;

    &--danger {
      background: rgba(220, 38, 38, 0.06);
      color: var(--yt-color-danger, #dc2626);
    }

    code {
      font-family: inherit;
      background: transparent;
    }
  }
}

@keyframes tcc-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
