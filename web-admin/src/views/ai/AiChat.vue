<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Sender, Bubble } from 'vue-element-plus-x'
import {
  AI_SCENARIO,
  AI_SCENARIO_OPTIONS,
  applySuggestion,
  getAiModels,
  getConversations,
  getMessages,
  streamChat,
} from '@/api/ai'
import type {
  AiConversation,
  AiCitation,
  AiMessage,
  AiStreamDeltaData,
  AiStreamDoneData,
  AiStreamErrorData,
  AiStreamMetaData,
  AiModelOption,
  ApplySuggestionRequest,
  PageResult,
} from '@/api/types'
import { track } from '@/utils/tracker'
import { estimateTokenCostCNY, formatCNY } from '@/utils/tokenCost'

const { t } = useI18n()

/**
 * AI 助手页面。设计来源: 13-AI能力设计 chatWithAssistant / applyAiSuggestion、16-原型与交互体验设计 AI 助手原型
 *
 * GA2-31 SSE 流式实现 (基于后端内容协商):
 *  - 对话通过 POST /ai/chat Accept: text/event-stream 流式响应 (meta/delta/citation/done/error 事件)
 *  - 前端使用 fetch + ReadableStream 消费 SSE, 逐字渲染 delta 事件
 *  - 引用来源 (citations) 通过 citation 事件逐条推送, 流式追加展示
 *  - 应用建议流程: 场景 producesDraft=true 时展示"应用建议"按钮 → 差异预览对话框 → POST /ai/suggestions/apply
 *  - 停止生成: AbortController 中止 fetch 请求 (13 号文档 line 142)
 *  - 场景切换: el-select 渲染 AI_SCENARIO_OPTIONS (5 种场景)
 *  - GA2-18 track() 埋点: 发送/应用建议/停止生成等关键事件
 *  - WCAG 4.1.3: aria-live 播报新消息
 */
interface DisplayMessage extends AiMessage {
  /** 引用来源列表 (RAG 检索结果, citation 事件流式追加) */
  citations?: AiCitation[]
  /** 模型元信息 (providerCode/modelCode/tokenInput/tokenOutput/latencyMs), meta/done 事件携带, 仅 ASSISTANT 消息 */
  meta?: {
    providerCode?: string
    modelCode?: string
    tokenInput?: number
    tokenOutput?: number
    latencyMs?: number
    scenario?: string
  }
  /** 是否正在流式输出 (用于显示打字光标) */
  streaming?: boolean
}

const loading = ref(false)
const sending = ref(false)
const applying = ref(false)
const conversations = ref<AiConversation[]>([])
const currentConversationId = ref('')
const messages = ref<DisplayMessage[]>([])
const inputMessage = ref('')
const scenario = ref<string>(AI_SCENARIO.PLATFORM_QA)
const chatContainer = ref<HTMLElement | null>(null)

/** 本会话 token 用量累计 (来源: SSE done 事件 usage 字段) */
const sessionTokenInput = ref(0)
const sessionTokenOutput = ref(0)
/** 会话 token 总数 (input + output) 用于 toolbar 角标展示 */
const sessionTokenTotal = computed(() => sessionTokenInput.value + sessionTokenOutput.value)
/** 会话累计成本 (CNY, 来源 tokenCost 静态价格表) */
const sessionTokenCost = ref(0)
/** 切换会话时重置累计 */
function resetSessionTokenUsage() {
  sessionTokenInput.value = 0
  sessionTokenOutput.value = 0
  sessionTokenCost.value = 0
}

/** 可用模型列表 */
const models = ref<AiModelOption[]>([])
/** 模型选择器 loading */
const modelLoading = ref(false)
/** 当前选中的模型键 (格式: providerCode:modelCode) */
const selectedModelKey = ref('')

/** 当前请求的 AbortController, 用于"停止生成" */
let currentAbortController: AbortController | null = null

/** 差异预览对话框状态 */
const diffDialogVisible = ref(false)
const diffDraftContent = ref('')
const diffScenarioLabel = ref('')
const applyResult = ref('')
const applyResultDialogVisible = ref(false)

/** 当前场景是否产生草稿建议 (决定是否展示"应用建议"按钮) */
const currentScenarioProducesDraft = computed(() => {
  const opt = AI_SCENARIO_OPTIONS.find((o) => o.value === scenario.value)
  return !!opt?.producesDraft
})

/** 模型下拉选项: 无可用模型时展示禁用项 Mock Chat */
const modelSelectOptions = computed(() => {
  if (models.value.length === 0) {
    return [{
      key: 'mock',
      label: 'Mock Chat',
      providerCode: '',
      modelCode: '',
      disabled: true,
    }]
  }
  return models.value.map((m) => ({
    key: `${m.providerCode}:${m.modelCode}`,
    label: `${m.providerCode} / ${m.modelName || m.modelCode}`,
    providerCode: m.providerCode,
    modelCode: m.modelCode,
    disabled: false,
  }))
})

/** 当前选中的模型 */
const selectedModel = computed(() => {
  if (!selectedModelKey.value) return undefined
  return models.value.find((m) => `${m.providerCode}:${m.modelCode}` === selectedModelKey.value)
})

async function loadConversations() {
  loading.value = true
  try {
    const res: PageResult<AiConversation> = await getConversations({
      page: 1,
      size: 50,
    })
    conversations.value = res.records || []
  } finally {
    loading.value = false
  }
}

async function loadModels() {
  modelLoading.value = true
  try {
    const list = await getAiModels()
    models.value = Array.isArray(list) ? list : []
    if (models.value.length > 0) {
      selectedModelKey.value = `${models.value[0].providerCode}:${models.value[0].modelCode}`
    } else {
      selectedModelKey.value = 'mock'
    }
  } catch {
    models.value = []
    selectedModelKey.value = 'mock'
  } finally {
    modelLoading.value = false
  }
}

async function selectConversation(id: string) {
  currentConversationId.value = id
  messages.value = []
  resetSessionTokenUsage()
  try {
    const list = await getMessages(id)
    // 解析每条消息的 citationJson 字段为 citations 数组, 并将 contentSummary 映射为 content (前端统一展示字段)
    messages.value = list.map((m) => ({
      ...m,
      content: m.contentSummary || m.contentEncrypted || m.content || '',
      citations: parseCitations(m.citationJson),
    }))
    await nextTick()
    scrollToBottom()
  } catch (e) {
    ElMessage.error(t('ai.msg.loadConversationFailed'))
  }
}

/** 安全解析 citationJson (后端可能为 null 或非 JSON 字符串) */
function parseCitations(raw?: string): AiCitation[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as AiCitation[]) : []
  } catch {
    return []
  }
}

/**
 * GA2-31: 发送消息 (SSE 流式)
 * 设计来源: 13-AI能力设计 line 137-143/252
 * 事件序列: meta → (citation)* → (delta)+ → done | error
 */
async function handleSend(val?: string) {
  const msg = String(val ?? inputMessage.value ?? '').trim()
  if (!msg || sending.value) return

  sending.value = true
  inputMessage.value = ''

  // 立即追加 USER 消息到界面 (乐观更新, role 对齐后端常量小写 "user")
  const userDisplayMsg: DisplayMessage = {
    id: `local-user-${Date.now()}`,
    conversationId: currentConversationId.value,
    role: 'user',
    content: msg,
    createdAt: new Date().toISOString(),
  }
  messages.value.push(userDisplayMsg)
  await nextTick()
  scrollToBottom()

  // 创建 AbortController 支持"停止生成" (13 号文档 line 142)
  currentAbortController = new AbortController()

  track('web.ai.chat.send.click', {
    bizType: 'ai_conversation',
    payload: { scenario: scenario.value, messageLength: msg.length },
  })

  // 创建 ASSISTANT 占位消息, 流式 delta 逐步追加内容
  const assistantMsg: DisplayMessage = {
    id: `streaming-${Date.now()}`,
    conversationId: currentConversationId.value,
    role: 'assistant',
    content: '',
    citations: [],
    streaming: true,
    createdAt: new Date().toISOString(),
  }
  messages.value.push(assistantMsg)
  await nextTick()
  scrollToBottom()

  // 记录流式过程中的状态 (用于 done 事件埋点)
  let streamMessageId = ''
  let streamConversationId = ''
  let citationCount = 0
  let tokenInput = 0
  let tokenOutput = 0
  let latencyMs = 0
  let newConversationCreated = false

  try {
    await streamChat(
      {
        conversationId: currentConversationId.value || undefined,
        message: msg,
        scenario: scenario.value,
        idempotencyKey: `chat-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        providerCode: selectedModel.value?.providerCode,
        modelCode: selectedModel.value?.modelCode,
      },
      {
        // meta 事件: 流开始, 锁定 messageId/conversationId/modelCode/providerCode
        onMeta: (data: AiStreamMetaData, event) => {
          assistantMsg.meta = {
            providerCode: selectedModel.value?.providerCode,
            modelCode: data.modelCode,
            scenario: data.scenario,
          }
          // 从事件信封捕获 conversationId/messageId (后续事件复用同一 messageId)
          streamConversationId = event.conversationId
          streamMessageId = event.messageId
          assistantMsg.id = event.messageId
          assistantMsg.conversationId = event.conversationId
        },
        // citation 事件: 引用来源逐条追加
        onCitation: (data: AiCitation) => {
          if (!assistantMsg.citations) {
            assistantMsg.citations = []
          }
          assistantMsg.citations.push(data)
          citationCount++
          nextTick(scrollToBottom)
        },
        // delta 事件: 文本增量逐字拼接, 自动滚动到底部
        onDelta: (data: AiStreamDeltaData) => {
          assistantMsg.content += data.text
          nextTick(scrollToBottom)
        },
        // done 事件: 流正常结束, 更新 token 用量和耗时, 标记 streaming=false
        onDone: (data: AiStreamDoneData) => {
          assistantMsg.streaming = false
          assistantMsg.meta = {
            ...assistantMsg.meta,
            tokenInput: data.usage.inputTokens,
            tokenOutput: data.usage.outputTokens,
            latencyMs: data.usage.latencyMs,
          }
          tokenInput = data.usage.inputTokens
          tokenOutput = data.usage.outputTokens
          latencyMs = data.usage.latencyMs
          // 累计本会话 token 用量 (toolbar 角标展示)
          sessionTokenInput.value += data.usage.inputTokens
          sessionTokenOutput.value += data.usage.outputTokens
          // 累计本会话成本 (优先用后端 8-C 推的 estimatedCost, 兜底用前端静态价格表)
          const backendCost = parseBackendCost(data.usage.estimatedCost)
          if (backendCost !== null) {
            sessionTokenCost.value += backendCost
          } else {
            sessionTokenCost.value += estimateTokenCostCNY(
              selectedModel.value?.modelCode,
              data.usage.inputTokens,
              data.usage.outputTokens
            )
          }
        },
        // error 事件: 流异常终止, 展示错误提示
        onError: (data: AiStreamErrorData) => {
          assistantMsg.streaming = false
          assistantMsg.content += '\n\n' + t('ai.chat.error.streamError', { code: data.code, messageKey: data.messageKey })
          ElMessage.error(t('ai.msg.streamError', { code: data.code }))
          track('web.ai.chat.stream.error', {
            bizType: 'ai_conversation',
            result: 'FAILED',
            errorCode: data.code,
            payload: { retryable: data.retryable, traceId: data.traceId },
          })
        },
      },
      currentAbortController.signal
    )

    // 新会话: 记录 conversationId 并刷新会话列表
    if (streamConversationId && !currentConversationId.value) {
      currentConversationId.value = streamConversationId
      newConversationCreated = true
    }

    if (newConversationCreated) {
      await loadConversations()
    }

    await nextTick()
    scrollToBottom()

    track('web.ai.chat.reply.success', {
      bizType: 'ai_conversation',
      bizId: streamConversationId,
      result: 'SUCCESS',
      durationMs: latencyMs,
      payload: {
        scenario: scenario.value,
        messageId: streamMessageId,
        citationsCount: citationCount,
        tokenTotal: tokenInput + tokenOutput,
      },
    })
  } catch (e: unknown) {
    // AbortController 中止时不显示错误 (用户主动停止)
    assistantMsg.streaming = false
    if (e instanceof DOMException && e.name === 'AbortError') {
      ElMessage.info(t('ai.msg.generationStopped'))
      track('web.ai.chat.abort.success', { result: 'ABORTED' })
      return
    }
    if (e instanceof Error && e.name === 'AbortError') {
      ElMessage.info(t('ai.msg.generationStopped'))
      track('web.ai.chat.abort.success', { result: 'ABORTED' })
      return
    }
    const errMsg = (e as { message?: string })?.message || ''
    if (errMsg.includes('abort') || errMsg.includes('cancel')) {
      ElMessage.info(t('ai.msg.generationStopped'))
      track('web.ai.chat.abort.success', { result: 'ABORTED' })
      return
    }
    ElMessage.error(t('ai.msg.sendFailed'))
    assistantMsg.content += '\n\n' + t('ai.chat.error.sendFailed', { message: errMsg })
    track('web.ai.chat.send.failed', { result: 'FAILED', errorCode: 'AI_SEND_ERROR' })
  } finally {
    sending.value = false
    currentAbortController = null
  }
}

/** 停止生成: 中止当前 fetch 请求 (13 号文档 line 142 AbortController) */
function handleStop() {
  if (currentAbortController) {
    currentAbortController.abort()
  }
}

/** 打开"应用建议"差异预览对话框 */
function openApplySuggestionDialog(message: DisplayMessage) {
  // 草稿内容: 第一版使用 AI 回复原文作为草稿 JSON (实际 v0.5+ 由后端返回结构化草稿)
  const draftContent = JSON.stringify(
    {
      type: 'ai_draft',
      scenario: message.meta?.scenario || scenario.value,
      content: message.content,
      generatedAt: message.createdAt,
    },
    null,
    2
  )
  diffDraftContent.value = draftContent
  diffScenarioLabel.value =
    AI_SCENARIO_OPTIONS.find((o) => o.value === message.meta?.scenario)?.label ||
    t('ai.chat.text.aiSuggestion')
  applyResult.value = ''
  diffDialogVisible.value = true
}

/** 确认应用建议: 调用 POST /ai/suggestions/apply */
async function confirmApplySuggestion() {
  applying.value = true
  track('web.ai.suggestion.apply.click', {
    bizType: 'ai_suggestion',
    payload: { scenario: scenario.value },
  })
  try {
    // 第一版前端构造草稿元信息 (v0.5+ 应由后端在 AiChatVO 中返回 draftId/schemaVersion/configHash)
    const draftId = `draft-${Date.now()}`
    const schemaVersion = '1.0.0'
    // 简单 djb2 风格 hash (前端预览用, 后端实际以收到的 configHash 字符串做匹配校验)
    const configHash = simpleHash(diffDraftContent.value)
    const expectedVersion = 1
    const idempotencyKey = `apply-${Date.now()}-${Math.random()
      .toString(36)
      .slice(2, 8)}`

    const request: ApplySuggestionRequest = {
      draftId,
      draftType: mapScenarioToDraftType(scenario.value),
      draftContent: diffDraftContent.value,
      schemaVersion,
      configHash,
      expectedVersion,
      idempotencyKey,
    }

    const result = await applySuggestion(request)

    applyResult.value =
      typeof result === 'string'
        ? result
        : t('ai.chat.msg.applySubmitted')
    diffDialogVisible.value = false
    applyResultDialogVisible.value = true

    track('web.ai.suggestion.apply.success', {
      bizType: 'ai_suggestion',
      bizId: draftId,
      result: 'SUCCESS',
      payload: {
        draftType: request.draftType,
        schemaVersion,
      },
    })
    ElMessage.success(t('ai.msg.applySuccess'))
  } catch (e) {
    track('web.ai.suggestion.apply.failed', {
      bizType: 'ai_suggestion',
      result: 'FAILED',
      errorCode: 'AI_APPLY_ERROR',
    })
    ElMessage.error(t('ai.msg.applyFailed'))
  } finally {
    applying.value = false
  }
}

/** 场景 → 草稿类型映射 (PAGE_GENERATE→PAGE, FIELD_SUGGEST→ENTITY, SQL_EXPLAIN→SQL) */
function mapScenarioToDraftType(
  s: string
): ApplySuggestionRequest['draftType'] {
  switch (s) {
    case AI_SCENARIO.PAGE_GENERATE:
      return 'PAGE'
    case AI_SCENARIO.SQL_EXPLAIN:
      return 'SQL'
    case AI_SCENARIO.FIELD_SUGGEST:
      return 'ENTITY'
    default:
      return 'PAGE'
  }
}

/** 简单字符串哈希 (前端预览用, 不做安全用途) */
function simpleHash(input: string): string {
  let h = 5381
  for (let i = 0; i < input.length; i++) {
    h = ((h << 5) + h + input.charCodeAt(i)) >>> 0
  }
  return `h_${h.toString(16)}`
}

/** Token 数格式化: 1234 -> "1.2K", 1500000 -> "1.5M" */
function formatTokenCount(n: number): string {
  if (n < 1000) return String(n)
  if (n < 1_000_000) return (n / 1000).toFixed(1) + 'K'
  return (n / 1_000_000).toFixed(2) + 'M'
}

/** 解析后端 8-C 推的 estimatedCost 字符串 -> 元 (number)
 * 失败/空/0 兜底: 返回 null (由调用方决定是否走前端静态表)
 */
function parseBackendCost(cost: string | null | undefined): number | null {
  if (!cost || cost === '0' || cost === '0.0') return null
  const n = Number(cost)
  if (!isFinite(n) || n <= 0) return null
  return n
}

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

function formatTime(t?: string): string {
  if (!t) return ''
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return t
  }
}

/** 保留换行和空白渲染 (无 markdown 库依赖) */
function renderContent(content?: string): string {
  if (!content) return ''
  // 转义 HTML 防注入
  return content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\n/g, '<br/>')
}

onMounted(() => {
  loadConversations()
  loadModels()
})

onUnmounted(() => {
  // 离开页面时中止未完成请求
  if (currentAbortController) {
    currentAbortController.abort()
  }
})
</script>

<template>
  <div class="ai-chat-page">
    <!-- 会话列表 -->
    <el-card class="conv-list" v-loading="loading" :aria-label="$t('ai.chat.aria.conversationList')">
      <template #header>
        <div class="conv-header">
          <span>{{ $t('ai.chat.title.conversationList') }}</span>
          <el-button size="small" @click="loadConversations" :loading="loading">
            {{ $t('ai.chat.action.refresh') }}
          </el-button>
        </div>
      </template>
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conv-item"
        :class="{ active: currentConversationId === conv.id }"
        role="button"
        tabindex="0"
        :aria-label="`${$t('ai.chat.aria.selectConversation')}: ${conv.conversationTitle || $t('ai.chat.text.newConversation')}`"
        @click="selectConversation(conv.id)"
        @keyup.enter="selectConversation(conv.id)"
      >
        <div class="conv-title">{{ conv.conversationTitle || $t('ai.chat.text.newConversation') }}</div>
        <div class="conv-meta">
          <el-tag size="small" type="info">{{ conv.scenario || $t('ai.chat.text.generalScenario') }}</el-tag>
          <span class="conv-time">{{ formatTime(conv.lastMessageTime || conv.createdAt) }}</span>
        </div>
      </div>
      <div v-if="conversations.length === 0" class="empty-tip">
        {{ $t('ai.chat.text.emptyConversations') }}
      </div>
    </el-card>

    <!-- 对话区域 -->
    <el-card class="chat-area" :aria-label="$t('ai.chat.aria.chatArea')">
      <!-- 顶部场景选择 + 元信息 -->
      <div class="chat-toolbar">
        <div class="toolbar-left">
          <span class="toolbar-label">{{ $t('ai.chat.label.scenario') }}</span>
          <el-select v-model="scenario" size="small" style="width: 140px" :aria-label="$t('ai.chat.aria.selectScenario')">
            <el-option
              v-for="opt in AI_SCENARIO_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-tag v-if="currentScenarioProducesDraft" size="small" type="warning">
            {{ $t('ai.chat.tag.willProduceDraft') }}
          </el-tag>
        </div>
        <div class="toolbar-right">
          <!-- P2-C: 本会话 Token 用量累计 (来源 SSE done 事件 usage 字段) -->
          <el-tooltip
            v-if="sessionTokenTotal > 0"
            :content="`本会话累计 Token: 输入 ${sessionTokenInput} + 输出 ${sessionTokenOutput} = ${sessionTokenTotal}`"
            placement="bottom"
          >
            <el-tag size="small" type="info" effect="plain" class="token-usage-badge" role="status" :aria-label="`本会话 Token 总量 ${sessionTokenTotal}`">
              🪙 {{ formatTokenCount(sessionTokenTotal) }} tokens
            </el-tag>
          </el-tooltip>
          <!-- P8-A: 本会话累计成本估算 (静态价格表) -->
          <el-tooltip
            v-if="sessionTokenCost > 0"
            :content="`基于当前模型静态价格表估算 (生产建议改用后端 AiModelPriceResolver 动态价格)`"
            placement="bottom"
          >
            <el-tag size="small" type="warning" effect="plain" class="token-cost-badge" role="status" :aria-label="`本会话累计成本 ${formatCNY(sessionTokenCost)}`">
              💰 {{ formatCNY(sessionTokenCost) }}
            </el-tag>
          </el-tooltip>
          <el-button
            v-if="sending"
            type="danger"
            size="small"
            @click="handleStop"
            :aria-label="$t('ai.chat.aria.stopGeneration')"
          >
            {{ $t('ai.chat.action.stopGeneration') }}
          </el-button>
        </div>
      </div>

      <!-- 消息列表 (WCAG 4.1.3: aria-live 播报新消息) -->
      <div
        ref="chatContainer"
        class="chat-container"
        aria-live="polite"
        :aria-label="$t('ai.chat.aria.messageList')"
      >
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="msg-row"
          :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
        >
          <Bubble
            :content="msg.content"
            :placement="msg.role === 'user' ? 'end' : 'start'"
            :shape="msg.role === 'user' ? 'corner' : 'round'"
            :variant="msg.role === 'user' ? 'filled' : 'outlined'"
            :is-markdown="msg.role !== 'user'"
            :typing="msg.streaming"
            :max-width="'720'"
            :aria-label="msg.role === 'user' ? $t('ai.chat.aria.myMessage') : $t('ai.chat.aria.aiReply')"
          >
            <template #content>
              <div class="msg-content" v-html="renderContent(msg.content)" />
              <span v-if="msg.streaming" class="streaming-cursor" aria-hidden="true">▋</span>
            </template>
          </Bubble>
          <div class="msg-bubble-extra">

            <!-- 引用来源 (citations) -->
            <div v-if="msg.citations && msg.citations.length > 0" class="citations">
              <div class="citations-title">{{ $t('ai.chat.title.citations') }} ({{ msg.citations.length }})</div>
              <div
                v-for="(cite, idx) in msg.citations"
                :key="idx"
                class="citation-item"
              >
                <div class="cite-header">
                  <el-tag size="small" type="success">{{ cite.sourceType || 'DOC' }}</el-tag>
                  <span class="cite-doc">{{ cite.docTitle }}</span>
                  <span class="cite-score">{{ $t('ai.chat.label.relevance') }} {{ (cite.score * 100).toFixed(1) }}%</span>
                </div>
                <div class="cite-path">{{ cite.sectionPath }}</div>
              </div>
            </div>

            <!-- 模型元信息 (审计可见性) -->
            <div v-if="msg.meta" class="msg-meta">
              <span v-if="msg.meta.modelCode">
                {{ $t('ai.chat.label.model') }}
                <span v-if="msg.meta.providerCode" class="model-provider">{{ msg.meta.providerCode }}</span>
                {{ msg.meta.providerCode ? ' / ' : '' }}{{ msg.meta.modelCode }}
              </span>
              <span v-if="msg.meta.tokenInput || msg.meta.tokenOutput">
                tokens: {{ msg.meta.tokenInput || 0 }} → {{ msg.meta.tokenOutput || 0 }}
              </span>
              <span v-if="msg.meta.latencyMs">{{ $t('ai.chat.label.latency') }} {{ msg.meta.latencyMs }}ms</span>
              <span class="msg-time">{{ formatTime(msg.createdAt) }}</span>
            </div>

            <!-- 应用建议按钮 (仅产生草稿的场景 + assistant 消息) -->
            <div
              v-if="
                msg.role === 'assistant' &&
                currentScenarioProducesDraft &&
                !sending
              "
              class="msg-actions"
            >
              <el-button
                v-permission="'ai:tool:generate'"
                size="small"
                type="primary"
                plain
                @click="openApplySuggestionDialog(msg)"
                :aria-label="$t('ai.chat.aria.applySuggestion')"
              >
                {{ $t('ai.assistant.action.applySuggestion') }}
              </el-button>
            </div>
          </div>
        </div>
        <div v-if="messages.length === 0" class="empty-chat" role="status">
          {{ $t('ai.chat.text.emptyChat') }}
        </div>
      </div>

      <!-- 模型选择器 -->
      <div class="model-selector-bar">
        <span class="toolbar-label">{{ $t('ai.chat.label.model') }}</span>
        <el-select
          v-model="selectedModelKey"
          size="small"
          style="width: 260px"
          :loading="modelLoading"
          :disabled="sending || models.length === 0"
          :aria-label="$t('ai.chat.aria.selectModel')"
        >
          <el-option
            v-for="opt in modelSelectOptions"
            :key="opt.key"
            :label="opt.label"
            :value="opt.key"
            :disabled="opt.disabled"
          />
        </el-select>
      </div>

      <!-- 输入区：vue-element-plus-x Sender，保留场景/模型/应用建议 -->
      <div class="chat-input">
        <Sender
          v-model="inputMessage"
          variant="updown"
          :auto-size="{ minRows: 1, maxRows: 5 }"
          allow-speech
          clearable
          :loading="sending"
          :submitBtnDisabled="!inputMessage.trim()"
          :placeholder="$t('ai.chat.placeholder.inputMessage')"
          submitType="enter"
          :style="{ '--el-color-primary': 'var(--yt-color-primary)' }"
          @submit="handleSend"
          @cancel="handleStop"
        />
      </div>
    </el-card>

    <!-- 差异预览对话框 (应用建议前必展示) -->
    <el-dialog
      v-model="diffDialogVisible"
      :title="$t('ai.chat.dialog.applyDiffTitle')"
      width="720px"
      :aria-label="$t('ai.chat.aria.diffPreviewDialog')"
    >
      <el-alert
        type="warning"
        :closable="false"
        :title="$t('ai.chat.alert.diffTitle')"
        :description="$t('ai.chat.alert.diffDescription')"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item :label="$t('ai.chat.field.scenario')">{{ diffScenarioLabel }}</el-descriptions-item>
        <el-descriptions-item :label="$t('ai.chat.field.draftType')">
          {{ mapScenarioToDraftType(scenario) }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('ai.chat.field.schemaVersion')">1.0.0</el-descriptions-item>
        <el-descriptions-item :label="$t('ai.chat.field.configHash')">
          {{ simpleHash(diffDraftContent) }}
        </el-descriptions-item>
        <el-descriptions-item :label="$t('ai.chat.field.expectedVersion')">1</el-descriptions-item>
        <el-descriptions-item :label="$t('ai.chat.field.idempotencyKey')">
          apply-{{ Date.now() }}
        </el-descriptions-item>
      </el-descriptions>
      <div class="diff-label">{{ $t('ai.chat.label.draftPreview') }}</div>
      <pre class="diff-preview">{{ diffDraftContent }}</pre>
      <template #footer>
        <el-button @click="diffDialogVisible = false" :disabled="applying">
          {{ $t('ai.chat.action.cancel') }}
        </el-button>
        <el-button
          v-permission="'ai:tool:generate'"
          type="primary"
          :loading="applying"
          @click="confirmApplySuggestion"
        >
          {{ $t('ai.chat.action.confirmApply') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 应用结果对话框 -->
    <el-dialog
      v-model="applyResultDialogVisible"
      :title="$t('ai.chat.dialog.applyResultTitle')"
      width="520px"
      :aria-label="$t('ai.chat.aria.applyResultDialog')"
    >
      <el-alert type="success" :closable="false" show-icon :title="$t('ai.chat.alert.applySuccessTitle')">
        <template #default>
          <div style="white-space: pre-wrap; word-break: break-all">{{ applyResult }}</div>
        </template>
      </el-alert>
      <template #footer>
        <el-button type="primary" @click="applyResultDialogVisible = false">{{ $t('ai.chat.action.gotIt') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-chat-page {
  display: flex;
  height: calc(100vh - 140px);
  gap: 12px;
}

.conv-list {
  width: 280px;
  overflow-y: auto;
  flex-shrink: 0;
}

.conv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-item {
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 4px;
  margin-bottom: 4px;
  border: 1px solid transparent;
}

.conv-item:hover {
  background: #f5f7fa;
}

.conv-item.active {
  background: #ecf5ff;
  border-color: #b3d8ff;
}

.conv-title {
  font-weight: 500;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  color: #999;
}

.conv-time {
  margin-left: auto;
}

.empty-tip {
  text-align: center;
  color: #ccc;
  padding: 20px 0;
  font-size: 13px;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-area :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.chat-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-label {
  font-size: 13px;
  color: #606266;
}

.chat-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.msg-row {
  margin-bottom: 16px;
  display: flex;
}

.msg-user {
  justify-content: flex-end;
}

.msg-ai {
  justify-content: flex-start;
}

.msg-bubble {
  display: inline-block;
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 10px;
  background: #f4f4f5;
  color: #333;
  word-break: break-word;
}

.msg-user .msg-bubble {
  background: #409eff;
  color: #fff;
}

.msg-content {
  line-height: 1.6;
  font-size: 14px;
}

/* GA2-31: SSE 流式输出时的打字光标动画 */
.streaming-cursor {
  display: inline-block;
  margin-left: 2px;
  color: #409eff;
  font-weight: bold;
  animation: blink 0.8s steps(2) infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.citations {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #dcdfe6;
}

.citations-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
  font-weight: 500;
}

.citation-item {
  background: #fff;
  padding: 6px 8px;
  border-radius: 4px;
  margin-bottom: 4px;
  font-size: 12px;
}

.cite-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.cite-doc {
  font-weight: 500;
  color: #303133;
}

.cite-score {
  margin-left: auto;
  color: #67c23a;
  font-size: 11px;
}

.cite-path {
  color: #909399;
  font-size: 11px;
  margin-left: 4px;
}

.msg-meta {
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed #dcdfe6;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  color: #909399;
}

.msg-user .msg-meta {
  border-top-color: rgba(255, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.85);
}

.model-provider {
  color: #a8abb2;
}

.msg-user .model-provider {
  color: rgba(255, 255, 255, 0.75);
}

.msg-time {
  margin-left: auto;
}

.msg-actions {
  margin-top: 8px;
  text-align: right;
}

.empty-chat {
  text-align: center;
  color: #ccc;
  margin-top: 40px;
}

.model-selector-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}

.msg-bubble-extra {
  margin-top: 8px;
  max-width: 720px;
}

.chat-input {
  border-top: 1px solid var(--yt-border-light);
  padding: 12px;
  background: var(--yt-bg-card);
}

.chat-input :deep(.el-textarea) {
  flex: 1;
}

.diff-label {
  margin: 12px 0 6px;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.diff-preview {
  max-height: 320px;
  overflow: auto;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  border: 1px solid #ebeef5;
}
</style>
