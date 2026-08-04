import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * AI Store
 * 设计来源: 66-前端组件API与状态管理详设 line 107-115
 *
 * 仅保存当前会话 ID 引用和 sources 引用列表, 不保存完整对话内容。
 * 对话内容由 AiChat 组件本地状态管理, 避免大对象占用 Pinia 内存。
 */

/** RAG 引用来源 (轻量引用, 仅 docTitle + score + 文档 ID) */
export interface Source {
  documentId: string
  docTitle: string
  sectionPath?: string
  score: number
}

export const useAiStore = defineStore('ai', () => {
  /** 当前会话 ID (引用, 完整对话内容不进入 store) */
  const currentConversationId = ref<string | null>(null)
  /** 当前回复的引用来源列表 (轻量引用) */
  const sources = ref<Source[]>([])
  /** 累计 token 成本 (仅统计数字, 不含 prompt 内容) */
  const cost = ref<{ inputTokens: number; outputTokens: number }>({
    inputTokens: 0,
    outputTokens: 0,
  })

  /**
   * 发送消息 (仅更新 conversationId 引用 + 累加 cost)。
   * 实际 API 调用由 AiChat 组件完成, 避免在 store 中耦合 API 客户端。
   */
  function sendMessage(text: string): void {
    // 占位: 实际 API 调用在组件中, 这里仅记录引用
    // text 参数仅为接口契约, 不保存到 store (避免 prompt 内容进入状态)
    void text
  }

  /** 累加 token 成本 */
  function addCost(input: number, output: number): void {
    cost.value.inputTokens += input
    cost.value.outputTokens += output
  }

  /** 更新当前会话 ID */
  function setConversationId(id: string | null): void {
    currentConversationId.value = id
  }

  /** 更新引用来源列表 */
  function setSources(list: Source[]): void {
    sources.value = list || []
  }

  /**
   * 应用 AI 建议 (调用方负责调用 API)。
   * 仅清除当前 sources, 实际 applySuggestion API 调用由组件完成。
   */
  function applySuggestion(_suggestionId: string): void {
    sources.value = []
  }

  return {
    currentConversationId,
    sources,
    cost,
    sendMessage,
    addCost,
    setConversationId,
    setSources,
    applySuggestion,
  }
})
