import service from './request'
import type { InvPage } from './types'

/**
 * 支付订单 API。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)
 * 后端: PaymentOpsController @RequestMapping("/api/v1/payment")
 *
 * 核心 6 项能力:
 *  1. 支付单状态机 (PENDING → PAID/FAILED/CANCELLED → REFUNDING → REFUNDED → CLOSED)
 *  2. 第三方回调验签 (HMAC-SHA256, 签名串 = orderNo + channel + amount + timestamp)
 *  3. 回调幂等 (idempotency_key 唯一索引兜底)
 *  4. 对账文件导入 (matched/mismatched/missing/extra 四维比对)
 *  5. 金额精度 (全程 Long 分, 1 元 = 100 分)
 *  6. 安全审计 (@Auditable AOP 写入 sys_operation_log)
 */

// ==================== 类型定义 ====================

/** 支付单 (对应后端 PayOrder domain) */
export interface PayOrder {
  id: string
  orderNo: string
  bizType: string
  bizId?: string
  /** MOCK_ALIPAY / MOCK_WECHAT / MOCK_UNIONPAY */
  channel: string
  /** 金额 (单位: 分, 1 元 = 100 分) */
  amount: number
  currency: string
  subject: string
  payerId?: string
  /** PENDING / PAID / FAILED / CANCELLED / REFUNDING / REFUNDED / CLOSED */
  status: string
  channelTradeNo?: string
  paidTime?: string
  expiredTime?: string
  closedTime?: string
  failReason?: string
  idempotencyKey?: string
  /** 渠道凭证 JSON 字符串, 例如 {"accessKey":"...","secretKey":"..."} */
  channelConfig?: string
  extraParams?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 支付回调日志 (对应后端 PayCallbackLog domain) */
export interface PayCallbackLog {
  id: string
  orderId?: string
  orderNo: string
  channel: string
  channelTradeNo?: string
  /** PAY_SUCCESS / PAY_FAIL / REFUND_SUCCESS / REFUND_FAIL */
  callbackAction: string
  signature?: string
  callbackTimestamp: string
  rawPayload?: string
  parsedPayload?: string
  /** SUCCESS / FAILED */
  verifyResult: string
  /** PROCESSED / IGNORED / ERROR */
  processResult: string
  processMessage?: string
  idempotencyKey?: string
  receivedTime: string
  createdBy?: string
  createdTime?: string
  version?: number
  remark?: string
}

/** 退款单 (对应后端 PayRefundOrder domain) */
export interface PayRefundOrder {
  id: string
  refundNo: string
  originalOrderId: string
  originalOrderNo: string
  /** 退款金额 (单位: 分) */
  refundAmount: number
  reason: string
  /** PENDING / SUCCESS / FAILED */
  status: string
  operatorId: string
  channelRefundNo?: string
  refundedTime?: string
  failReason?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 对账记录 (对应后端 PayReconciliation domain) */
export interface PayReconciliation {
  id: string
  reconDate: string
  channel: string
  fileName?: string
  totalCount: number
  totalAmount: number
  matchedCount: number
  matchedAmount: number
  mismatchedCount: number
  mismatchedAmount: number
  missingCount: number
  missingAmount: number
  extraCount: number
  extraAmount: number
  /** PENDING / MATCHED / MISMATCHED / IMPORTED / FAILED */
  status: string
  /** JSON 字符串数组, 每条含 orderNo/channelTradeNo/localAmount/remoteAmount/diff/diffType */
  details?: string
  importedBy?: string
  importedTime?: string
  processMessage?: string
  createdBy?: string
  createdTime?: string
  version?: number
  remark?: string
}

/** 支付监控统计 VO (对应后端 PaymentStatsVO) */
export interface PaymentStatsVO {
  totalOrders?: number
  statusCounts?: Record<string, number>
  channelCounts?: Record<string, number>
  totalAmountPaid?: number
  totalAmountRefunded?: number
  totalRefunds?: number
  callbackTotal?: number
  callbackVerifyFailed?: number
  reconTotal?: number
  reconMatchedCount?: number
  reconMismatchedCount?: number
}

/** 创建支付单请求 */
export interface CreateOrderRequest {
  bizType: string
  bizId?: string
  channel: string
  amount: number
  currency?: string
  subject: string
  payerId?: string
  idempotencyKey?: string
  channelConfig?: string
  extraParams?: string
  remark?: string
}

/** 退款请求 */
export interface RefundRequest {
  originalOrderId: string
  refundAmount: number
  reason: string
  remark?: string
}

/** 第三方回调请求 */
export interface CallbackRequest {
  orderNo: string
  channel: string
  channelTradeNo?: string
  callbackAction: string
  amount?: number
  callbackTimestamp: string
  signature?: string
  rawPayload?: string
  failReason?: string
}

/** 对账文件导入请求 */
export interface ReconciliationImportRequest {
  reconDate: string
  channel: string
  fileName?: string
  items: ReconciliationItem[]
}

export interface ReconciliationItem {
  orderNo: string
  channelTradeNo?: string
  amount?: number
  status?: string
}

// ==================== 支付单 (状态机) ====================

/** 分页查询支付单 */
export function pagePayOrders(params: {
  pageNo?: number
  pageSize?: number
  orderNo?: string
  channel?: string
  status?: string
  bizType?: string
}): Promise<InvPage<PayOrder>> {
  return service.get('/payment/orders', { params }) as unknown as Promise<InvPage<PayOrder>>
}

/** 查询支付单详情 */
export function getPayOrder(id: string): Promise<PayOrder> {
  return service.get(`/payment/orders/${id}`) as unknown as Promise<PayOrder>
}

/** 创建支付单 (状态机 PENDING, 幂等键防重复) */
export function createPayOrder(data: CreateOrderRequest): Promise<PayOrder> {
  return service.post('/payment/orders', data) as unknown as Promise<PayOrder>
}

/** 取消支付 (PENDING → CANCELLED) */
export function cancelPayOrder(id: string): Promise<PayOrder> {
  return service.post(`/payment/orders/${id}/cancel`) as unknown as Promise<PayOrder>
}

/** 关闭订单 (PAID/REFUNDED → CLOSED, 归档) */
export function closePayOrder(id: string): Promise<PayOrder> {
  return service.post(`/payment/orders/${id}/close`) as unknown as Promise<PayOrder>
}

// ==================== 第三方回调 (验签 + 幂等) ====================

/** 处理第三方支付回调 (HMAC-SHA256 验签 + 幂等) */
export function handlePayCallback(data: CallbackRequest): Promise<PayCallbackLog> {
  return service.post('/payment/callbacks', data) as unknown as Promise<PayCallbackLog>
}

/** 分页查询回调日志 */
export function pagePayCallbacks(params: {
  pageNo?: number
  pageSize?: number
  orderNo?: string
  channel?: string
  verifyResult?: string
  processResult?: string
}): Promise<InvPage<PayCallbackLog>> {
  return service.get('/payment/callbacks', { params }) as unknown as Promise<InvPage<PayCallbackLog>>
}

// ==================== 退款 ====================

/** 发起退款 (PAID → REFUNDING/REFUNDED) */
export function refundPayOrder(data: RefundRequest): Promise<PayRefundOrder> {
  return service.post('/payment/refunds', data) as unknown as Promise<PayRefundOrder>
}

/** 分页查询退款单 */
export function pagePayRefunds(params: {
  pageNo?: number
  pageSize?: number
  refundNo?: string
  originalOrderId?: string
  status?: string
}): Promise<InvPage<PayRefundOrder>> {
  return service.get('/payment/refunds', { params }) as unknown as Promise<InvPage<PayRefundOrder>>
}

// ==================== 对账文件导入 ====================

/** 对账文件导入 (PENDING → MATCHED/MISMATCHED) */
export function importPayReconciliation(data: ReconciliationImportRequest): Promise<PayReconciliation> {
  return service.post('/payment/reconciliations/import', data) as unknown as Promise<PayReconciliation>
}

/** 分页查询对账记录 */
export function pagePayReconciliations(params: {
  pageNo?: number
  pageSize?: number
  channel?: string
  status?: string
  startDate?: string
  endDate?: string
}): Promise<InvPage<PayReconciliation>> {
  return service.get('/payment/reconciliations', { params }) as unknown as Promise<InvPage<PayReconciliation>>
}

/** 查询对账记录详情 */
export function getPayReconciliation(id: string): Promise<PayReconciliation> {
  return service.get(`/payment/reconciliations/${id}`) as unknown as Promise<PayReconciliation>
}

// ==================== 监控统计 ====================

/** 支付监控统计 (订单/状态/渠道/回调/对账) */
export function getPaymentStats(): Promise<PaymentStatsVO> {
  return service.get('/payment/stats') as unknown as Promise<PaymentStatsVO>
}
