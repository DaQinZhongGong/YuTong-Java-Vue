<script setup lang="ts">
/**
 * AiAssistantPanel AI 助手侧边栏
 * 设计来源: 53-Web管理端页面级分工详设 line 62 (通用组件任务表: AiAssistantPanel context)
 *           + 13-AI能力设计 chatWithAssistant
 *
 * 上下文 AI 对话浮层:
 *   - el-drawer 从右侧滑入, 宽度 420
 *   - 顶部展示上下文标签 (type + label, 如有)
 *   - 中间对话区: 用户消息右侧蓝色气泡, AI 消息左侧灰色气泡
 *   - 底部输入框 + 发送按钮
 *   - 调用 src/api/ai.ts 的 chatWithAssistant; 若该 API 不存在则使用 mock 回复
 *   - 支持清空对话
 */
import { ref, nextTick, watch } from 'vue'
import { Delete, Promotion, RefreshRight } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { chatWithAssistant, resetAiConversation } from '@/api/ai'

/** 上下文对象 */
export interface AiContext {
  /** 上下文类型 (如 page / record) */
  type: string
  /** 上下文对象 id */
  id: string
  /** 上下文展示文案 */
  label: string
}

interface Props {
  /** 是否显示 (v-model) */
  visible: boolean
  /** 上下文对象, 如当前页面或当前选中记录 */
  context?: AiContext
}

const props = defineProps<Props>()
const { t } = useI18n()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  /** 是否错误消息 (true 时展示红色气泡 + 重试按钮) */
  error?: boolean
}

const messages = ref<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const scrollRef = ref<HTMLDivElement | null>(null)

/** 生成简单消息 id */
function genId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'msg-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
}

/** 滚动到底部 */
function scrollToBottom() {
  nextTick(() => {
    const el = scrollRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/** 最近一次失败的用户输入, 用于“重试”按钮重新发送 */
const lastFailedText = ref('')

/**
 * 调用 AI 接口发送消息, 接入真实后端 POST /ai/chat (operationId: chatWithAssistant)。
 * chatWithAssistant 内部已捕获网络错误并返回 { reply, error: true }, 这里直接透传。
 */
async function callAi(userText: string) {
  return chatWithAssistant(userText, props.context)
}

/**
 * 发送一条用户消息并追加 AI 回复气泡。
 * @param text 用户输入文本
 * @param isRetry 是否为重试 (true 时不重复追加 user 气泡)
 */
async function sendUserMessage(text: string, isRetry = false) {
  if (!isRetry) {
    messages.value.push({ id: genId(), role: 'user', content: text })
  }
  input.value = ''
  sending.value = true
  scrollToBottom()
  try {
    const resp = await callAi(text)
    messages.value.push({
      id: genId(),
      role: 'assistant',
      content: resp.reply,
      error: resp.error,
    })
    // 标记/清除最近失败文本, 用于重试按钮可见性
    if (resp.error) {
      lastFailedText.value = text
    } else {
      lastFailedText.value = ''
    }
  } catch {
    // chatWithAssistant 已吞掉异常, 此分支理论上不会进入, 保留兜底
    messages.value.push({
      id: genId(),
      role: 'assistant',
      content: t('ai.assistant.msg.unavailable'),
      error: true,
    })
    lastFailedText.value = text
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

async function handleSend() {
  const text = input.value.trim()
  if (!text || sending.value) return
  await sendUserMessage(text)
}

/** 重试最近一次失败的提问: 移除错误气泡后重新发送 */
async function handleRetry() {
  if (!lastFailedText.value || sending.value) return
  const lastIdx = messages.value.length - 1
  if (lastIdx >= 0 && messages.value[lastIdx]?.error) {
    messages.value.splice(lastIdx, 1)
  }
  await sendUserMessage(lastFailedText.value, true)
}

function handleClear() {
  messages.value = []
  lastFailedText.value = ''
  // 清空后重置会话 ID, 下次提问走新会话
  resetAiConversation()
}

function handleVisibleChange(v: boolean) {
  emit('update:visible', v)
}

// 首次打开时塞入一条欢迎语, 避免空对话区
watch(
  () => props.visible,
  (v) => {
    if (v && messages.value.length === 0) {
      messages.value.push({
        id: genId(),
        role: 'assistant',
        content: t('ai.assistant.msg.welcome'),
      })
      scrollToBottom()
    }
  }
)
</script>

<template>
  <el-drawer
    :model-value="visible"
    :size="420"
    :close-on-click-modal="true"
    @update:model-value="handleVisibleChange"
  >
    <template #header>
      <div class="yt-ai-panel__header">
        <span class="yt-ai-panel__title">{{ $t('ai.assistant.title') }}</span>
        <el-button
          text
          type="primary"
          :icon="Delete"
          :disabled="messages.length === 0"
          @click="handleClear"
        >
          {{ $t('ai.assistant.action.clear') }}
        </el-button>
      </div>
    </template>

    <!-- 上下文标签 -->
    <div v-if="context" class="yt-ai-panel__context">
      <el-tag type="info" size="small" effect="plain">
        {{ context.type }}
      </el-tag>
      <span class="yt-ai-panel__context-label">{{ context.label }}</span>
    </div>

    <!-- 对话区 -->
    <div ref="scrollRef" class="yt-ai-panel__messages">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="yt-ai-panel__bubble"
        :class="[
          `yt-ai-panel__bubble--${msg.role}`,
          { 'yt-ai-panel__bubble--error': msg.error },
        ]"
      >
        <div class="yt-ai-panel__bubble-content">{{ msg.content }}</div>
        <el-button
          v-if="msg.error"
          class="yt-ai-panel__retry"
          text
          type="danger"
          size="small"
          :icon="RefreshRight"
          :loading="sending"
          @click="handleRetry"
        >
          {{ $t('ai.assistant.action.retry') }}
        </el-button>
      </div>
    </div>

    <!-- 输入区 -->
    <template #footer>
      <div class="yt-ai-panel__input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          :placeholder="$t('ai.assistant.placeholder.input')"
          :disabled="sending"
          @keydown.enter.prevent="handleSend"
        />
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="sending"
          :disabled="!input.trim()"
          @click="handleSend"
        >
          {{ $t('ai.assistant.action.chat') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.yt-ai-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.yt-ai-panel__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.yt-ai-panel__context {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.yt-ai-panel__context-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yt-ai-panel__messages {
  height: calc(100vh - 220px);
  overflow-y: auto;
  padding: 4px 0;
}

.yt-ai-panel__bubble {
  max-width: 80%;
  padding: 8px 12px;
  margin-bottom: 10px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  border-radius: 8px;
}

.yt-ai-panel__bubble--user {
  margin-left: auto;
  background: var(--el-color-primary);
  color: #fff;
  border-bottom-right-radius: 2px;
}

.yt-ai-panel__bubble--assistant {
  margin-right: auto;
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
  border-bottom-left-radius: 2px;
}

.yt-ai-panel__bubble--error {
  margin-right: auto;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  border: 1px solid var(--el-color-danger-light-5);
  border-bottom-left-radius: 2px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.yt-ai-panel__bubble-content {
  word-break: break-word;
}

.yt-ai-panel__retry {
  padding: 0;
  height: auto;
  font-size: 12px;
}

.yt-ai-panel__input {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  width: 100%;
}

.yt-ai-panel__input .el-button {
  flex-shrink: 0;
}
</style>