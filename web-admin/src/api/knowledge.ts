import service from './request'
import type { InvPage } from './types'

/**
 * 知识库运营 API。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 * 后端: KnowledgeOpsController @RequestMapping("/api/v1/kb")
 *
 * 核心能力 (6 项):
 *  1. 文档导入 (POST /documents/{id}/reindex)
 *  2. 分块和向量化 (后端 ingest 时自动)
 *  3. 权限过滤 (后端 RagAclService)
 *  4. 问答引用 (POST /ask 返回 citations)
 *  5. 命中率统计 (GET /stats 返回 todayHitRate)
 *  6. 低置信度拒答 (maxScore < 0.30 时 refused=true)
 */

// ==================== 类型定义 ====================

export interface AskQuestionRequest {
  kbId: string
  question: string
  topK?: number
}

export interface Citation {
  docId: string
  docTitle: string
  chunkId: string
  sectionPath?: string
  sourceType?: string
  score: number
  chunkTextPreview?: string
}

export interface AskQuestionVO {
  conversationNo: string
  question: string
  answer: string
  refused: boolean
  refuseReason?: string  // NO_HITS / LOW_CONFIDENCE / KB_DISABLED / ACL_DENIED
  hitChunkCount: number
  maxScore: number
  minScore: number
  avgScore: number
  latencyMs: number
  citations: Citation[]
}

export interface KbStatsVO {
  kbId?: string
  kbName?: string
  kbStatus?: string
  documentCount?: number
  chunkCount?: number
  embeddingCount?: number
  todayConversationCount?: number
  todayHitCount?: number
  todayRefusedCount?: number
  todayHitRate?: number
  todayAvgMaxScore?: number
  todayAvgLatencyMs?: number
  totalConversationCount?: number
  totalHitCount?: number
  totalRefusedCount?: number
}

export interface KbConversationLog {
  id: string
  conversationNo?: string
  kbId?: string
  userId?: string
  question?: string
  answer?: string
  hitChunkCount?: number
  maxScore?: number
  minScore?: number
  avgScore?: number
  isRefused?: boolean
  refuseReason?: string
  citedDocuments?: string
  citedChunkIds?: string
  latencyMs?: number
  aiConversationId?: string
  createdBy?: string
  createdTime?: string
  version?: number
}

// ==================== API 函数 ====================

/** 知识库问答 (6 项核心能力验证: 文档导入/分块向量化/权限过滤/问答引用/命中率统计/低置信度拒答) */
export function askQuestion(data: AskQuestionRequest): Promise<AskQuestionVO> {
  return service.post('/kb/ask', data) as unknown as Promise<AskQuestionVO>
}

/** 知识库运营统计 (5 项核心指标: 文档数/分块数/今日问答/命中率/平均分) */
export function getKbStats(kbId: string): Promise<KbStatsVO> {
  return service.get('/kb/stats', { params: { kbId } }) as unknown as Promise<KbStatsVO>
}

/** 分页查询问答历史 */
export function pageKbConversations(params: {
  page?: number
  size?: number
  kbId?: string
  userId?: string
  refused?: boolean
}): Promise<InvPage<KbConversationLog>> {
  return service.get('/kb/conversations', { params }) as unknown as Promise<InvPage<KbConversationLog>>
}

/** 查询问答详情 */
export function getKbConversation(id: string): Promise<KbConversationLog> {
  return service.get(`/kb/conversations/${id}`) as unknown as Promise<KbConversationLog>
}

/** 重新索引文档 (验证文档导入能力) */
export function reindexKbDocument(id: string): Promise<void> {
  return service.post(`/kb/documents/${id}/reindex`) as unknown as Promise<void>
}
