import service from './request'
import type { InvPage } from './types'

/**
 * 通知运营 API。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知 + 44-实时通信与消息推送设计
 * 后端: NotificationOpsController @RequestMapping("/api/v1/notifications")
 *
 * 核心能力 (6 项):
 *  1. 站内信 (POST /send 写 sys_message)
 *  2. 未读数 (GET /stats 返回 currentUserUnreadCount)
 *  3. 实时推送 (PushService.push 复用 GA2-32 WebSocket 通道)
 *  4. 移动端订阅消息 (sys_notification_subscription + MockMobilePushAdapter)
 *  5. 消息模板 (sys_message_template + 变量渲染)
 *  6. 消息重试 (sys_notification_dispatch_log + retryDispatch/resolveDispatch)
 */

// ==================== 类型定义 ====================

export interface SendNotificationRequest {
  templateCode: string
  receiverId: string
  variables: Record<string, string>
  bizType?: string
  bizId?: string
}

export interface SendNotificationVO {
  messageId: string
  title: string
  content: string
  msgType: string
  receiverId: string
  createdTime: string
  dispatchLogIds: string[]
  realtimePushed: boolean
  mobilePushTriggered: boolean
}

export interface NotificationStatsVO {
  templateCount?: number
  enabledTemplateCount?: number
  todayMessageCount?: number
  todayReadCount?: number
  todayUnreadCount?: number
  todayDispatchCount?: number
  todayFailedDispatchCount?: number
  pendingRetryCount?: number
  deadLetterCount?: number
  currentUserUnreadCount?: number
  subscriptionCount?: number
}

export interface MessageTemplate {
  id: string
  templateCode: string
  templateName: string
  channel?: string
  msgType: string
  titleTemplate: string
  contentTemplate: string
  targetRouteId?: string
  priority: string
  deliveryMode: string
  // V002 列: ENABLED / DISABLED (后端 domain 使用 String status, 不再使用 enabled Boolean)
  status?: string
  remark?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

export interface SaveTemplateRequest {
  templateCode: string
  templateName: string
  msgType: string
  titleTemplate: string
  contentTemplate: string
  targetRouteId?: string
  priority?: string
  deliveryMode?: string
  enabled?: boolean
  remark?: string
}

export interface DispatchLog {
  id: string
  messageId: string
  receiverId: string
  channel: string
  dispatchStatus: string
  retryCount: number
  maxRetryCount: number
  retryBackoffMs: number
  nextRetryTime?: string
  lastError?: string
  sentTime?: string
  payloadSnapshot?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

export interface Subscription {
  id: string
  userId: string
  topic: string
  channel: string
  enabled: boolean
  deviceToken?: string
  remark?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
}

export interface SaveSubscriptionRequest {
  topic: string
  channel: string
  enabled?: boolean
  deviceToken?: string
  remark?: string
}

export interface MockMobilePushResult {
  deviceToken: string
  title: string
  pushed: boolean
}

// ==================== API 函数 ====================

/** 发送通知 (6 项核心能力编排: 模板渲染→站内信→分发→推送→移动订阅) */
export function sendNotification(data: SendNotificationRequest): Promise<SendNotificationVO> {
  return service.post('/notifications/send', data) as unknown as Promise<SendNotificationVO>
}

/** 通知运营统计 (8 项指标) */
export function getNotificationStats(): Promise<NotificationStatsVO> {
  return service.get('/notifications/stats') as unknown as Promise<NotificationStatsVO>
}

/** 分页查询消息模板 */
export function pageMessageTemplates(params: {
  pageNo?: number
  pageSize?: number
  msgType?: string
}): Promise<InvPage<MessageTemplate>> {
  return service.get('/notifications/templates', { params }) as unknown as Promise<InvPage<MessageTemplate>>
}

/** 创建消息模板 */
export function createMessageTemplate(data: SaveTemplateRequest): Promise<MessageTemplate> {
  return service.post('/notifications/templates', data) as unknown as Promise<MessageTemplate>
}

/** 删除消息模板 */
export function deleteMessageTemplate(id: string): Promise<void> {
  return service.delete(`/notifications/templates/${id}`) as unknown as Promise<void>
}

/** 分页查询分发日志 */
export function pageDispatchLogs(params: {
  pageNo?: number
  pageSize?: number
  dispatchStatus?: string
  channel?: string
  receiverId?: string
}): Promise<InvPage<DispatchLog>> {
  return service.get('/notifications/dispatches', { params }) as unknown as Promise<InvPage<DispatchLog>>
}

/** 手动重试分发 (验证消息重试能力) */
export function retryDispatch(id: string): Promise<DispatchLog> {
  return service.post(`/notifications/dispatches/${id}/retry`) as unknown as Promise<DispatchLog>
}

/** 解决死信分发 */
export function resolveDispatch(id: string): Promise<DispatchLog> {
  return service.post(`/notifications/dispatches/${id}/resolve`) as unknown as Promise<DispatchLog>
}

/** 分页查询当前用户移动端订阅 */
export function pageSubscriptions(params: {
  pageNo?: number
  pageSize?: number
  topic?: string
}): Promise<InvPage<Subscription>> {
  return service.get('/notifications/subscriptions', { params }) as unknown as Promise<InvPage<Subscription>>
}

/** 创建/更新移动端订阅 */
export function saveSubscription(data: SaveSubscriptionRequest): Promise<Subscription> {
  return service.post('/notifications/subscriptions', data) as unknown as Promise<Subscription>
}

/** 删除移动端订阅 */
export function deleteSubscription(id: string): Promise<void> {
  return service.delete(`/notifications/subscriptions/${id}`) as unknown as Promise<void>
}

/** Mock 移动推送 (冒烟测试用) */
export function mockMobilePush(params: {
  deviceToken: string
  title: string
  content?: string
}): Promise<MockMobilePushResult> {
  return service.post('/notifications/mock/mobile-push', null, { params }) as unknown as Promise<MockMobilePushResult>
}
