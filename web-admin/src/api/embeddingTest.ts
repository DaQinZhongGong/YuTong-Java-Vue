import service from './request'

/**
 * 多模态 Embedding 测试 API。设计来源: docs/compose/spec/ai-depth-parity.md S2.3。
 * 后端: POST /api/v1/ai/rag/embeddings/multimodal (管理端调参用)
 * baseURL 已含 /api/v1。
 */

export type EmbeddingModality = 'text' | 'image' | 'video'

export interface MultimodalEmbeddingRequest {
  /** text | image | video */
  modality: EmbeddingModality
  /**
   * text: 纯文本
   * image: URL 或 data:image/...;base64,...
   * video: URL
   */
  payload: string
  knowledgeBaseId?: string
}

export interface MultimodalEmbeddingResult {
  /** 模态 */
  modality?: string
  /** 向量维度 */
  dimension: number
  /** 是否远程成功 (false = 失败关闭/哈希回退) */
  remoteSuccess?: boolean
  /** 前 N 维预览 */
  preview?: number[]
  /** 说明信息 */
  message?: string
  /** 兼容旧字段 */
  vector?: number[]
  vectorPreview?: number[]
  errorMessage?: string
  degraded?: boolean
  traceId?: string
}

export const EMBEDDING_MODALITY_OPTIONS: Array<{ value: EmbeddingModality; label: string }> = [
  { value: 'text', label: '文本 text' },
  { value: 'image', label: '图片 image (URL / data URL)' },
  { value: 'video', label: '视频 video (URL)' },
]

/** 多模态 Embedding 测试: POST /ai/rag/embeddings/multimodal */
export function testMultimodalEmbedding(
  data: MultimodalEmbeddingRequest
): Promise<MultimodalEmbeddingResult> {
  return service.post('/ai/rag/embeddings/multimodal', data) as unknown as Promise<MultimodalEmbeddingResult>
}
