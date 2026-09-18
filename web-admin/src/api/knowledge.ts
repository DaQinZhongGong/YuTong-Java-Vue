import service from './request'
import type { InvPage, PageResult } from './types'

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

// ==================== 知识图谱 ====================

export interface KnowledgeGraph {
  id: string
  kbId?: string
  name: string
  status: string // DRAFT / BUILDING / READY / FAILED
  graphJson?: string
  entityCount?: number
  relationCount?: number
  remark?: string
  createdTime?: string
  updatedTime?: string
  version?: number
}

export interface KnowledgeGraphSegment {
  id: string
  graphId: string
  sourceChunkId?: string
  entityJson?: string
  relationJson?: string
  createdTime?: string
}

export interface GraphNode {
  id: string
  label: string
  type?: string
  props?: Record<string, unknown>
}

export interface GraphEdge {
  source: string
  target: string
  relation: string
  props?: Record<string, unknown>
}

export interface GraphData {
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export function listKnowledgeGraphs(params: {
  page?: number
  size?: number
  kbId?: string
  status?: string
  keyword?: string
}): Promise<PageResult<KnowledgeGraph>> {
  return service.get('/knowledge-graph', { params }) as unknown as Promise<PageResult<KnowledgeGraph>>
}

export function getKnowledgeGraph(id: string): Promise<KnowledgeGraph> {
  return service.get(`/knowledge-graph/${id}`) as unknown as Promise<KnowledgeGraph>
}

export function queryKnowledgeGraph(id: string): Promise<KnowledgeGraph> {
  return service.get(`/knowledge-graph/${id}/query`) as unknown as Promise<KnowledgeGraph>
}

export function createKnowledgeGraph(data: Partial<KnowledgeGraph>): Promise<KnowledgeGraph> {
  return service.post('/knowledge-graph', data) as unknown as Promise<KnowledgeGraph>
}

export function buildKnowledgeGraph(id: string): Promise<KnowledgeGraph> {
  return service.post(`/knowledge-graph/${id}/build`) as unknown as Promise<KnowledgeGraph>
}

export function listKnowledgeGraphSegments(id: string): Promise<KnowledgeGraphSegment[]> {
  return service.get(`/knowledge-graph/${id}/segments`) as unknown as Promise<KnowledgeGraphSegment[]>
}

/** 兼容任务描述的别名 */
export const listGraph = listKnowledgeGraphs
export const createGraph = createKnowledgeGraph
export const buildGraph = buildKnowledgeGraph
export const getSegments = listKnowledgeGraphSegments

// ==================== 知识库检索配置 (V050 P2-E 混合检索可配) ====================

/** 知识库检索配置 (后端 AiKnowledgeBase 混合检索字段子集) */
export interface KbRetrievalConfig {
  id?: string
  kbCode?: string
  kbName?: string
  status?: string
  hybridEnabled?: boolean
  hybridVectorWeight?: number
  hybridTopK?: number
  hybridMinScore?: number
}

/** 查询知识库详情: GET /ai/knowledge-bases/{id} */
export function getKnowledgeBase(id: string): Promise<KbRetrievalConfig> {
  return service.get(`/ai/knowledge-bases/${id}`) as unknown as Promise<KbRetrievalConfig>
}

/** 更新检索配置 (ACTIVE 可直接调参实时生效): PATCH /ai/knowledge-bases/{id}/retrieval-config */
export function updateRetrievalConfig(
  id: string,
  data: Partial<KbRetrievalConfig>,
): Promise<KbRetrievalConfig> {
  return service.patch(`/ai/knowledge-bases/${id}/retrieval-config`, data) as unknown as Promise<KbRetrievalConfig>
}
