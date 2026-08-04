import service from './request'
import type {
  Contract,
  ContractDetail,
  ContractTag,
  SaveContractRequest,
  ContractActionRequest,
  PageResult,
} from './types'

/**
 * 合同档案 API。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 * 后端 ContractController @RequestMapping("/api/v1/contracts")
 *
 * 核心能力: 分页查询 + 全文检索 + 详情聚合 + 状态流转 + 标签管理
 */

/** 分页查询合同列表 */
export function pageContracts(params: {
  page?: number
  size?: number
  contractNo?: string
  title?: string
  status?: string
  contractType?: string
  partyB?: string
}): Promise<PageResult<Contract>> {
  return service.get('/contracts', { params }) as unknown as Promise<PageResult<Contract>>
}

/** 全文检索合同（PG tsvector + GIN 索引 + ILIKE 兜底） */
export function searchContracts(query: string, limit = 20): Promise<Contract[]> {
  return service.get('/contracts/search', { params: { query, limit } }) as unknown as Promise<Contract[]>
}

/** 查询合同详情（含版本/审批/标签聚合 + DataScope 脱敏） */
export function getContractDetail(id: string): Promise<ContractDetail> {
  return service.get(`/contracts/${id}`) as unknown as Promise<ContractDetail>
}

/** 创建合同草稿（自动生成合同号 CTyyyyMMddNNNN） */
export function createContract(data: SaveContractRequest): Promise<Contract> {
  return service.post('/contracts', data) as unknown as Promise<Contract>
}

/** 更新合同草稿（已归档/已取消不可编辑，后端返回 CTR-409002） */
export function updateContract(id: string, data: SaveContractRequest): Promise<Contract> {
  return service.put(`/contracts/${id}`, data) as unknown as Promise<Contract>
}

/** 合同状态流转操作 (SUBMIT/APPROVE/REJECT/RESUBMIT/SIGN/ARCHIVE/CANCEL) */
export function contractAction(id: string, data: ContractActionRequest): Promise<Contract> {
  return service.post(`/contracts/${id}/actions`, data) as unknown as Promise<Contract>
}

/** 添加合同标签 */
export function addContractTag(id: string, tagName: string): Promise<ContractTag> {
  return service.post(`/contracts/${id}/tags`, null, { params: { tagName } }) as unknown as Promise<ContractTag>
}

/** 删除合同标签 */
export function removeContractTag(id: string, tagName: string): Promise<void> {
  return service.delete(`/contracts/${id}/tags/${encodeURIComponent(tagName)}`) as unknown as Promise<void>
}
