import service from './request'
import type {
  BizRequest,
  BizRequestDetail,
  PageResult,
  SaveBizRequestPayload,
} from './types'

/**
 * 申请单 API 客户端。设计来源: 18-样例业务详细设计 API 契约、08-API契约设计。
 *
 * GA2-24 修复:
 *  - 路径修正: /api/v1/requests → /api/v1/biz-requests (后端 BizRequestController @RequestMapping)
 *  - 查询参数对齐后端: pageNo/pageSize/status (替代旧 page size/requestStatus/requestType)
 *  - 字段类型对齐后端 BizRequest domain (title/customerNameSnapshot/submittedTime/createdTime 等)
 *  - 新增 update/submit/approve/reject/withdraw/archive 完整接口
 *  - 详情接口返回 BizRequestDetail (含 items + approvals)
 */
export interface BizRequestPageQuery {
  pageNo?: number
  pageSize?: number
  requestNo?: string
  title?: string
  status?: string
  customerId?: string
  /** keyset 分页模式 (PERF-002 调优, 深翻页性能稳定) */
  mode?: 'keyset'
  cursor?: string
}

export function getRequests(
  params?: BizRequestPageQuery,
): Promise<PageResult<BizRequest>> {
  // baseURL 已为 /api/v1，路径仅写 /biz-requests 即可，避免拼成 /api/v1/api/v1/biz-requests 导致 404
  return service.get('/biz-requests', {
    params,
  }) as unknown as Promise<PageResult<BizRequest>>
}

export function getRequest(id: string): Promise<BizRequestDetail> {
  return service.get(
    `/biz-requests/${id}`,
  ) as unknown as Promise<BizRequestDetail>
}

/** 保存草稿 (新建): POST /biz-requests */
export function createRequest(
  data: SaveBizRequestPayload,
): Promise<BizRequest> {
  return service.post('/biz-requests', data) as unknown as Promise<BizRequest>
}

/** 修改草稿: PUT /biz-requests/{id} (仅 DRAFT/REJECTED 状态可改) */
export function updateRequest(
  id: string,
  data: SaveBizRequestPayload,
): Promise<BizRequest> {
  return service.put(
    `/biz-requests/${id}`,
    data,
  ) as unknown as Promise<BizRequest>
}

export interface RequestActionPayload {
  /** 审核意见 (reject 必填) */
  opinion?: string
  /** 撤回原因 (withdraw 必填) */
  reason?: string
  /** 乐观锁版本号 */
  version?: number
  /** 幂等键 (前端生成的 UUID, 防重复提交) */
  idempotencyKey?: string
}

/** 提交申请单: POST /biz-requests/{id}/submit */
export function submitRequest(
  id: string,
  data: RequestActionPayload = {},
): Promise<BizRequest> {
  return service.post(
    `/biz-requests/${id}/submit`,
    data,
  ) as unknown as Promise<BizRequest>
}

/** 审核通过: POST /biz-requests/{id}/approve */
export function approveRequest(
  id: string,
  data: RequestActionPayload = {},
): Promise<BizRequest> {
  return service.post(
    `/biz-requests/${id}/approve`,
    data,
  ) as unknown as Promise<BizRequest>
}

/** 驳回申请单: POST /biz-requests/{id}/reject (opinion 必填) */
export function rejectRequest(
  id: string,
  opinion: string,
  version?: number,
  idempotencyKey?: string,
): Promise<BizRequest> {
  return service.post(
    `/biz-requests/${id}/reject`,
    { opinion, version, idempotencyKey },
  ) as unknown as Promise<BizRequest>
}

/** 撤回申请单: POST /biz-requests/{id}/withdraw (reason 必填) */
export function withdrawRequest(
  id: string,
  reason: string,
  version?: number,
  idempotencyKey?: string,
): Promise<BizRequest> {
  return service.post(
    `/biz-requests/${id}/withdraw`,
    { reason, version, idempotencyKey },
  ) as unknown as Promise<BizRequest>
}

/** 归档申请单: POST /biz-requests/{id}/archive */
export function archiveRequest(
  id: string,
  version?: number,
  idempotencyKey?: string,
): Promise<BizRequest> {
  return service.post(
    `/biz-requests/${id}/archive`,
    { version, idempotencyKey },
  ) as unknown as Promise<BizRequest>
}
