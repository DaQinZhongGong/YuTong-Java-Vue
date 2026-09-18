import service from './request'
import type { PageResult } from './types'

/**
 * MCP 市场 API。设计来源: backend McpMarketController
 * 路径与后端保持一致: /api/v1/mcp/market (VITE_API_BASE_URL 已含 /api/v1)
 *   GET    /mcp/market            分页查询
 *   GET    /mcp/market/{id}       详情
 *   GET    /mcp/market/{id}/tools 工具列表
 *   POST   /mcp/market/{id}/install 安装
 *   POST   /mcp/market/sync       同步
 */

// ==================== 类型定义 ====================

export interface McpMarket {
  id: string
  name: string
  code: string
  description?: string | null
  iconUrl?: string | null
  provider?: string | null
  category?: string | null
  rating?: number | string | null
  installCount?: number | null
  status?: string | null
  configJson?: string | null
  tenantId?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  /** 前端本地状态：是否已安装（安装后更新，不持久化） */
  _installed?: boolean
}

export interface McpMarketTool {
  id: string
  marketId: string
  toolName: string
  toolDesc?: string | null
  inputSchema?: string | null
  tenantId?: string
  createdTime?: string
  updatedTime?: string
}

export interface PageMcpMarketParams {
  page?: number
  size?: number
  keyword?: string
  category?: string
  status?: string
}

const BASE = '/mcp/market'

/** 分页查询 MCP 市场 */
export function listMarkets(params: PageMcpMarketParams): Promise<PageResult<McpMarket>> {
  return service.get(BASE, { params }) as unknown as Promise<PageResult<McpMarket>>
}

/** 查询市场详情 */
export function getMarket(id: string): Promise<McpMarket> {
  return service.get(`${BASE}/${encodeURIComponent(id)}`) as unknown as Promise<McpMarket>
}

/** 查询市场工具列表 */
export function listMarketTools(id: string): Promise<McpMarketTool[]> {
  return service.get(`${BASE}/${encodeURIComponent(id)}/tools`) as unknown as Promise<McpMarketTool[]>
}

/** 安装市场条目（幂等 install_count+1） */
export function installMarket(id: string): Promise<McpMarket> {
  return service.post(`${BASE}/${encodeURIComponent(id)}/install`) as unknown as Promise<McpMarket>
}

/** 同步市场（占位） */
export function syncMarket(): Promise<{ synced: boolean }> {
  return service.post(`${BASE}/sync`) as unknown as Promise<{ synced: boolean }>
}
