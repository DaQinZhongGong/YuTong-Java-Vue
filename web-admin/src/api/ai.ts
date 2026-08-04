import service from './request'
import { getToken, getMockUser } from '@/utils/auth'
import i18n from '@/locales'
import type {
  AiChatRequest,
  AiChatVO,
  AiConversation,
  AiMessage,
  AiCitation,
  AiStreamDeltaData,
  AiStreamDoneData,
  AiStreamErrorData,
  AiStreamEvent,
  AiModelOption,
  AiStreamMetaData,
  ApplySuggestionRequest,
  PageRequest,
  PageResult,
} from './types'

/**
 * AI 对话 API。设计来源: 13-AI能力设计 chatWithAssistant / applyAiSuggestion、08-API契约设计
 * GA2-26: 路径对齐后端 AiChatController @RequestMapping("/api/v1/ai"), baseURL 已含 /api/v1, 此处用相对路径。
 *   - POST /ai/chat           → chatWithAssistant  (Accept: application/json 返回完整 AiChatVO; SSE 留 v0.5+)
 *   - GET  /ai/conversations  → listAiConversations
 *   - GET  /ai/conversations/{id}        → getAiConversation
 *   - GET  /ai/conversations/{id}/messages → listAiConversationMessages (@Hidden, 不在 OpenAPI 94 基线中)
 *   - POST /ai/suggestions/apply → applyAiSuggestion
 *
 * GA2-31: 新增 SSE 流式对话 streamChat(), Accept: text/event-stream, 事件序列 meta→citation→delta→done|error
 */

/** AI 场景枚举。对齐后端 AiChatRequest.scenario 注释。 */
export const AI_SCENARIO = {
  /** 平台问答: 通用平台知识问答 (默认场景, 走 query_meta_model 工具) */
  PLATFORM_QA: 'PLATFORM_QA',
  /** 字段建议: 业务字段智能建议 */
  FIELD_SUGGEST: 'FIELD_SUGGEST',
  /** 页面生成: 低代码页面草稿生成 (走 generate_page_draft 工具) */
  PAGE_GENERATE: 'PAGE_GENERATE',
  /** SQL 解释: SQL 草稿生成与解释 (走 generate_sql_draft 工具) */
  SQL_EXPLAIN: 'SQL_EXPLAIN',
  /** 运维诊断: 平台运维问题诊断 */
  OPS_DIAGNOSE: 'OPS_DIAGNOSE',
} as const

export type AiScenario = (typeof AI_SCENARIO)[keyof typeof AI_SCENARIO]

/** 场景下拉选项 (供视图层 el-select 渲染) */
export const AI_SCENARIO_OPTIONS: Array<{
  value: AiScenario
  label: string
  /** 是否产生草稿建议 (决定是否展示"应用建议"按钮) */
  producesDraft: boolean
}> = [
  { value: AI_SCENARIO.PLATFORM_QA, label: '平台问答', producesDraft: false },
  { value: AI_SCENARIO.FIELD_SUGGEST, label: '字段建议', producesDraft: true },
  { value: AI_SCENARIO.PAGE_GENERATE, label: '页面生成', producesDraft: true },
  { value: AI_SCENARIO.SQL_EXPLAIN, label: 'SQL 解释', producesDraft: true },
  { value: AI_SCENARIO.OPS_DIAGNOSE, label: '运维诊断', producesDraft: false },
]

/**
 * 发起 AI 对话: POST /ai/chat
 * 第一版返回完整 AiChatVO (含 citations); SSE 流式请使用 streamChat()。
 * 幂等键由调用方生成 (推荐 crypto.randomUUID())。
 */
export function sendMessage(data: AiChatRequest): Promise<AiChatVO> {
  return service.post('/ai/chat', data, {
    headers: { Accept: 'application/json' },
  }) as unknown as Promise<AiChatVO>
}

/**
 * 模块级缓存的当前会话 ID。
 * 后端 POST /ai/chat 在 conversationId 为空时自动创建新会话并返回 AiChatVO.conversationId;
 * 后续调用复用该 ID 保持上下文连贯。无需单独调用 POST /ai/conversations 创建会话
 * (后端 AiChatController 未暴露该端点, 隐式创建即可)。
 */
let currentConversationId: string | undefined

/**
 * 重置 AI 助手当前会话 ID。
 * 调用方在“清空对话”或关闭面板时调用, 使下一次 chatWithAssistant 发起新会话。
 */
export function resetAiConversation(): void {
  currentConversationId = undefined
}

/**
 * chatWithAssistant 返回结构。
 * - reply: AI 回复内容 (失败时为友好提示文案)
 * - conversationId: 后端返回的会话 ID (新会话首次回复后填充, 失败时不填充)
 * - error: 是否发生错误, 组件层据此展示错误气泡 + 重试按钮
 */
export interface ChatAssistantResult {
  reply: string
  conversationId?: string
  /** 网络错误/服务异常时为 true, 此时 reply 为友好提示文案 */
  error?: boolean
}

/**
 * AI 助手上下文对话封装 (供 AiAssistantPanel 组件调用)。
 * <p>
 * 调用后端 POST /ai/chat (operationId: chatWithAssistant, Accept: application/json),
 * 后端在 conversationId 为空时自动创建新会话, 并在 AiChatVO.conversationId 中返回;
 * 后续调用复用缓存的 conversationId 保持上下文连贯。
 * <p>
 * 设计来源: 13-AI能力设计 chatWithAssistant、AiChatController#chat。
 * <p>
 * 错误处理: 网络错误/服务异常时不抛异常, 返回 { reply: 友好提示, error: true }。
 *
 * @param message 用户问题
 * @param context 上下文对象 (type/id/label), 当前版本仅用于视图层展示, 不影响后端调用
 */
export async function chatWithAssistant(
  message: string,
  context?: { type: string; id: string; label: string }
): Promise<ChatAssistantResult> {
  const idempotencyKey =
    typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : 'ai-chat-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)

  const request: AiChatRequest = {
    message,
    conversationId: currentConversationId,
    scenario: AI_SCENARIO.PLATFORM_QA,
    idempotencyKey,
  }
  // context 当前版本无对应后端字段, 仅用于视图层展示, 此处预留扩展点
  void context

  try {
    const resp = await sendMessage(request)
    if (resp?.conversationId) {
      currentConversationId = resp.conversationId
    }
    return {
      reply: resp?.content ?? '',
      conversationId: resp?.conversationId,
    }
  } catch (err) {
    const detail = err instanceof Error ? err.message : String(err)
    return {
      reply: `AI 助手暂时不可用, 请稍后重试。${detail ? `(${detail})` : ''}`,
      error: true,
    }
  }
}

/**
 * GA2-31: SSE 流式 AI 对话回调集合。
 * 每个事件类型对应一个可选回调，客户端按需实现。
 * 设计来源: 13-AI能力设计 line 137-143/252、contracts/openapi/openapi.yaml AiStreamEvent
 */
export interface StreamChatCallbacks {
  /** meta 事件: 流开始，携带 modelCode/scenario，客户端可锁定 messageId */
  onMeta?: (data: AiStreamMetaData, event: AiStreamEvent) => void
  /** delta 事件: 文本增量，客户端按 sequence 顺序拼接得到完整回复 */
  onDelta?: (data: AiStreamDeltaData, event: AiStreamEvent) => void
  /** citation 事件: 引用来源（RAG 检索结果，可多次推送） */
  onCitation?: (data: AiCitation, event: AiStreamEvent) => void
  /** done 事件: 流正常结束，携带 token 用量和是否需要人工确认 */
  onDone?: (data: AiStreamDoneData, event: AiStreamEvent) => void
  /** error 事件: 流异常终止，客户端展示错误提示 */
  onError?: (data: AiStreamErrorData, event: AiStreamEvent) => void
}

/**
 * GA2-31: 发起 SSE 流式 AI 对话: POST /ai/chat (Accept: text/event-stream)
 * <p>
 * 使用原生 fetch + ReadableStream 消费 SSE 流（axios 不支持流式响应）。
 * 事件序列: meta → (citation)* → (delta)+ → done | error
 * <p>
 * 设计来源: 13-AI能力设计 line 137-143/252、contracts/openapi/openapi.yaml AiChatResponse (line 1289-1295)
 * <p>
 * 关键约束:
 * - 用户可随时通过 AbortController.abort() 取消请求（13 号文档 line 142）
 * - 鉴权头与 axios 请求拦截器保持一致: Authorization / X-Mock-User / Accept-Language
 * - SSE 事件 data 字段为完整 AiStreamEvent JSON（后端 SseEmitter.event().data(event) 发送）
 *
 * @param data 请求体（与 sendMessage 相同的 AiChatRequest）
 * @param callbacks 事件回调集合
 * @param signal AbortSignal，用于"停止生成"功能
 * @returns Promise<void>，流结束（done 或 error）时 resolve，网络错误时 reject
 */
export async function streamChat(
  data: AiChatRequest,
  callbacks: StreamChatCallbacks,
  signal?: AbortSignal
): Promise<void> {
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  const url = `${baseURL}/ai/chat`

  // 构造请求头，对齐 axios 请求拦截器的鉴权和 i18n 头
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    'Accept-Language': i18n.global.locale.value,
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  // dev 模式注入 Mock 用户头（与 axios 拦截器一致）
  if (import.meta.env.DEV) {
    const mockUser = getMockUser()
    if (mockUser) {
      headers['X-Mock-User'] = mockUser
    }
  }

  let response: Response
  try {
    response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
      signal,
    })
  } catch (err) {
    // AbortError 是用户主动取消，不算错误
    if (err instanceof DOMException && err.name === 'AbortError') {
      return
    }
    throw err
  }

  if (!response.ok) {
    // HTTP 错误（如 401/403/500），尝试解析 JSON 错误响应
    let errorMsg = `HTTP ${response.status}`
    try {
      const errBody = await response.json()
      errorMsg = errBody?.message || errorMsg
    } catch {
      // 响应不是 JSON，使用默认错误消息
    }
    throw new Error(errorMsg)
  }

  if (!response.body) {
    throw new Error('SSE 流式响应不支持: response.body 为空')
  }

  // 解析 SSE 流: 按 \n\n 分隔事件，每个事件包含 id:/event:/data: 行
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })

      // 按 \n\n 分隔事件（SSE 协议规定空行分隔事件）
      let separatorIndex: number
      while ((separatorIndex = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, separatorIndex)
        buffer = buffer.slice(separatorIndex + 2)
        parseAndDispatchSseEvent(rawEvent, callbacks)
      }
    }
    // 处理 buffer 中剩余的未分发事件
    if (buffer.trim()) {
      parseAndDispatchSseEvent(buffer, callbacks)
    }
  } finally {
    reader.releaseLock()
  }
}

/**
 * 解析单个 SSE 事件文本并分发到对应回调。
 * SSE 事件格式:
 *   id: {eventId}
 *   event: {eventType}
 *   data: {json}
 */
function parseAndDispatchSseEvent(rawEvent: string, callbacks: StreamChatCallbacks): void {
  const lines = rawEvent.split('\n')
  let dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('data:')) {
      // data 行可能有多个，按 SSE 协议用 \n 拼接
      dataLines.push(line.slice(5).trimStart())
    }
    // id: 和 event: 行不单独解析，因为后端把完整信封放在 data 里
  }

  if (dataLines.length === 0) {
    return
  }

  const dataJson = dataLines.join('\n')
  let event: AiStreamEvent
  try {
    event = JSON.parse(dataJson)
  } catch {
    // 忽略无法解析的事件（如心跳注释）
    return
  }

  // 按 eventType 分发到对应回调
  switch (event.eventType) {
    case 'meta':
      callbacks.onMeta?.(event.data as AiStreamMetaData, event)
      break
    case 'delta':
      callbacks.onDelta?.(event.data as AiStreamDeltaData, event)
      break
    case 'citation':
      callbacks.onCitation?.(event.data as AiCitation, event)
      break
    case 'done':
      callbacks.onDone?.(event.data as AiStreamDoneData, event)
      break
    case 'error':
      callbacks.onError?.(event.data as AiStreamErrorData, event)
      break
    default:
      // tool 事件和其他未知类型暂不处理（v0.5+ 工具调用流式落地后再扩展）
      break
  }
}

/** 分页查询会话列表: GET /ai/conversations */
export function getConversations(
  params?: PageRequest & { scenario?: string }
): Promise<PageResult<AiConversation>> {
  return service.get('/ai/conversations', { params }) as unknown as Promise<
    PageResult<AiConversation>
  >
}

/** 查询会话详情: GET /ai/conversations/{id} */
export function getConversation(id: string): Promise<AiConversation> {
  return service.get(`/ai/conversations/${id}`) as unknown as Promise<AiConversation>
}

/** 查询会话消息列表: GET /ai/conversations/{id}/messages (后端 @Hidden, 不在 OpenAPI 94 基线) */
export function getMessages(conversationId: string): Promise<AiMessage[]> {
  return service.get(
    `/ai/conversations/${conversationId}/messages`
  ) as unknown as Promise<AiMessage[]>
}

/**
 * 应用 AI 建议: POST /ai/suggestions/apply (需 ai:tool:generate 权限)
 * 关键约束: 必须回传 schemaVersion / configHash / expectedVersion / idempotencyKey, 任一不匹配后端拒绝。
 * 第一版后端仅做校验和审计, 实际草稿写入由低代码服务处理 (留 v0.5+)。
 * 返回值为后端确认信息字符串。
 */
export function applySuggestion(
  data: ApplySuggestionRequest
): Promise<string> {
  return service.post('/ai/suggestions/apply', data) as unknown as Promise<string>
}

/** 查询可用模型列表: GET /ai/providers/models */
export function getAiModels(): Promise<AiModelOption[]> {
  return service.get('/ai/providers/models') as unknown as Promise<AiModelOption[]>
}
