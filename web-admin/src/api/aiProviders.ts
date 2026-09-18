import service from './request'
import type { AiProvider, AiProviderHealth, PageResult } from './types'

/**
 * AI 供应商管理 API — 平价能力 扩展。
 * <p>
 * 设计来源: 13-AI能力设计 provider registry / 37-AI治理与评测设计 供应商健康检查
 * / V036 ai_provider parity (provider_type/model_type/platform/multimodal)
 * <p>
 * 路径对齐后端 AiProviderController @RequestMapping("/api/v1/ai"), baseURL 已含 /api/v1。
 *   - GET  /ai/providers               → listProviders          (分页查询供应商列表)
 *   - GET  /ai/providers/{id}          → getProvider            (详情)
 *   - POST /ai/providers               → saveProvider           (新建/更新草稿)
 *   - POST /ai/providers/{id}/enable   → enableProvider
 *   - POST /ai/providers/{id}/disable  → disableProvider
 *   - POST /ai/providers/health-check  → checkProvidersHealth   (触发健康检查)
 *   - GET  /ai/providers/health        → getProvidersHealth     (查询最近健康状态)
 *   - GET  /ai/providers/models        → listModelOptions       (可用模型下拉)
 */

// ============================================================
// 平价能力 枚举 — 与后端 V036 DDL CHECK / Java enum 完全一致
// ============================================================

/** 供应商类型 11 枚举 — 对齐 backend AiProviderType / V036 chk_ai_provider_provider_type */
export const AI_PROVIDER_TYPE = {
  OPENAI: 'openai',
  DEEPSEEK: 'deepseek',
  QIANWEN: 'qianwen',
  ZHIPU: 'zhipu',
  OLLAMA: 'ollama',
  MINIMAX: 'minimax',
  ATLAS: 'atlas',
  XIAOMI: 'xiaomi',
  DIFY: 'dify',
  COZE: 'coze',
  CUSTOM_API: 'custom_api',
} as const
export type AiProviderType = (typeof AI_PROVIDER_TYPE)[keyof typeof AI_PROVIDER_TYPE]

export const AI_PROVIDER_TYPE_OPTIONS: Array<{ value: string; label: string; color: string; icon: string }> = [
  { value: 'openai', label: 'OpenAI', color: '#10a37f', icon: '🤖' },
  { value: 'deepseek', label: 'DeepSeek', color: '#4d6bfe', icon: '🧠' },
  { value: 'qianwen', label: '通义千问', color: '#ff6a00', icon: '☁️' },
  { value: 'zhipu', label: '智谱 GLM', color: '#7c3aed', icon: '⚡' },
  { value: 'ollama', label: 'Ollama', color: '#111827', icon: '🦙' },
  { value: 'minimax', label: 'MiniMax', color: '#e11d48', icon: '✨' },
  { value: 'atlas', label: 'Atlas', color: '#0e7490', icon: '🗺️' },
  { value: 'xiaomi', label: '小米', color: '#ff6900', icon: '📱' },
  { value: 'dify', label: 'Dify', color: '#2970ff', icon: '🔧' },
  { value: 'coze', label: 'Coze', color: '#8b5cf6', icon: '🧩' },
  { value: 'custom_api', label: '自定义', color: '#64748b', icon: '🔌' },
]

/** 供应商类型 -> 展示元数据 Map (供卡片快速查找) */
export const AI_PROVIDER_TYPE_META: Record<string, { label: string; color: string; icon: string }> =
  Object.fromEntries(AI_PROVIDER_TYPE_OPTIONS.map((o) => [o.value, o])) as Record<string, { label: string; color: string; icon: string }>

/** 模型类型 9 枚举 — 对齐 backend AiModelType / V036 chk_ai_provider_model_type */
export const AI_MODEL_TYPE = {
  CHAT: 'chat',
  IMAGE: 'image',
  VECTOR: 'vector',
  RERANKER: 'reranker',
  AUDIO: 'audio',
  TEXT: 'text',
  VIDEO: 'video',
  PPT: 'ppt',
  MUSIC: 'music',
} as const
export type AiModelType = (typeof AI_MODEL_TYPE)[keyof typeof AI_MODEL_TYPE]

export const AI_MODEL_TYPE_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'chat', label: '对话 CHAT' },
  { value: 'image', label: '图像 IMAGE' },
  { value: 'vector', label: '向量 VECTOR' },
  { value: 'reranker', label: '重排 RERANKER' },
  { value: 'audio', label: '音频 AUDIO' },
  { value: 'text', label: '文本 TEXT' },
  { value: 'video', label: '视频 VIDEO' },
  { value: 'ppt', label: '演示 PPT' },
  { value: 'music', label: '音乐 MUSIC' },
]

/** 平台枚举 — 对齐 backend AiPlatform / V036 platform (null=直连) */
export const AI_PLATFORM = {
  DIFY: 'dify',
  COZE: 'coze',
  FASTGPT: 'fastgpt',
  NONE: '',
} as const
export type AiPlatform = (typeof AI_PLATFORM)[keyof typeof AI_PLATFORM]

export const AI_PLATFORM_OPTIONS: Array<{ value: string; label: string }> = [
  { value: '', label: '直连' },
  { value: 'dify', label: 'Dify' },
  { value: 'coze', label: 'Coze' },
  { value: 'fastgpt', label: 'FastGPT' },
]

/** 平台 tab 值 — 前端矩阵筛选专用，空字符串代表直连 */
export const AI_PLATFORM_TABS: Array<{ value: string; label: string }> = [
  { value: 'all', label: '全部平台' },
  { value: 'dify', label: 'Dify' },
  { value: 'coze', label: 'Coze' },
  { value: 'fastgpt', label: 'FastGPT' },
  { value: '', label: '直连' },
]

/** 多模态能力标签 — 映射 multimodal_capabilities JSON keys -> /media/* */
export const AI_MULTIMODAL_OPTIONS: Array<{ value: string; label: string; mediaPath: string }> = [
  { value: 'image', label: '图像', mediaPath: '/media/image' },
  { value: 'video', label: '视频', mediaPath: '/media/video' },
  { value: 'audio', label: '音频', mediaPath: '/media/audio' },
  { value: 'ppt', label: '演示', mediaPath: '/media/ppt' },
]

// ============================================================
// API 函数
// ============================================================

/** 查询供应商列表: GET /ai/providers (返回分页结构, 默认取 1000 条) */
export function listProviders(params?: {
  page?: number
  size?: number
  providerCode?: string
  status?: string
  keyword?: string
  enabled?: boolean
  providerType?: string
  modelType?: string
  platform?: string
}): Promise<PageResult<AiProvider>> {
  return service.get('/ai/providers', { params: { page: 1, size: 1000, ...params } }) as unknown as Promise<PageResult<AiProvider>>
}

/** 查询供应商详情: GET /ai/providers/{id} */
export function getProvider(id: string): Promise<AiProvider> {
  return service.get(`/ai/providers/${id}`) as unknown as Promise<AiProvider>
}

/** 保存供应商草稿: POST /ai/providers (新建) / PUT /ai/providers/{id} (更新) — 兼容后端 POST 幂等 */
export function saveProvider(data: Partial<AiProvider>): Promise<AiProvider> {
  if (data.id) {
    return service.put(`/ai/providers/${data.id}`, data) as unknown as Promise<AiProvider>
  }
  return service.post('/ai/providers', data) as unknown as Promise<AiProvider>
}

/** 启用供应商: POST /ai/providers/{id}/enable?version= */
export function enableProvider(id: string, version: number): Promise<AiProvider> {
  return service.post(`/ai/providers/${id}/enable`, null, { params: { version } }) as unknown as Promise<AiProvider>
}

/** 禁用供应商: POST /ai/providers/{id}/disable?version= */
export function disableProvider(id: string, version: number): Promise<AiProvider> {
  return service.post(`/ai/providers/${id}/disable`, null, { params: { version } }) as unknown as Promise<AiProvider>
}

/** 触发供应商健康检查: POST /ai/providers/health-check */
export function checkProvidersHealth(): Promise<AiProviderHealth[]> {
  return service.post('/ai/providers/health-check') as unknown as Promise<AiProviderHealth[]>
}

/** 查询最近一次供应商健康状态: GET /ai/providers/health */
export function getProvidersHealth(): Promise<AiProviderHealth[]> {
  return service.get('/ai/providers/health') as unknown as Promise<AiProviderHealth[]>
}

/** 可用模型下拉: GET /ai/providers/models */
export function listModelOptions(): Promise<Array<{ providerCode: string; modelCode: string; modelName: string }>> {
  return service.get('/ai/providers/models') as unknown as Promise<Array<{ providerCode: string; modelCode: string; modelName: string }>>
}
