/**
 * web 聊天组件共享类型。设计来源: ADR 0004 P2-G 前端 web 增强。
 * ChatView 与 chat/* 独立组件共用，避免循环引用。
 */

/** 附件 (FilesSelect v-model 元素) */
export interface ChatAttachment {
  id: string
  name: string
  size: number
  raw?: File
}

/** 工具调用 (ToolCallTimeline 输入) */
export interface ChatToolCall {
  id: string
  name: string
  status: 'pending' | 'running' | 'success' | 'failed'
  summary?: string
  elapsedMs?: number
}

/** 已发布 Agent 选项 (AgentSelect) */
export interface ChatAgentOption {
  id: string
  agentCode?: string
  agentName?: string
}

/** 模型选项 (ModelSelect): 空 providerCode/modelCode = 自动路由 */
export interface ChatModelSelection {
  providerCode: string
  providerName: string
  modelCode: string
}
