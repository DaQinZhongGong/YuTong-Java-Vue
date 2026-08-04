import service from './request'
import type { InvPage } from './types'

/**
 * 外部接口同步 API。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 * 后端:
 *   - ExtSystemController @RequestMapping("/api/v1/ext/systems")
 *   - ExtSyncTaskController @RequestMapping("/api/v1/ext")
 *
 * 核心能力 (6 项):
 *  1. HTTP Client 适配 (Spring RestClient + 超时配置)
 *  2. 签名鉴权 (HMAC-SHA256 + 4 个请求头)
 *  3. 失败重试 (max_retry_count + 指数退避)
 *  4. 幂等写入 ((task_id, business_key) 唯一索引兜底)
 *  5. 死信/错误队列 (PENDING → RETRYING → RESOLVED / DEAD_LETTER)
 *  6. 同步监控 (统计 + 记录 + 错误队列分页查询)
 */

// ==================== 类型定义 ====================

/** 外部系统 (对应后端 ExtSystem domain) */
export interface ExtSystem {
  id: string
  systemCode: string
  systemName: string
  description?: string
  endpoint: string
  /** NONE / HMAC_SHA256 / API_KEY / BEARER_TOKEN */
  authType: string
  /** JSON 字符串, 例如 {"accessKey":"...","secretKey":"..."} */
  credentials?: string
  connectTimeout?: number
  readTimeout?: number
  maxRetryCount?: number
  retryBackoffMs?: number
  /** ACTIVE / DISABLED */
  status: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 同步任务 (对应后端 ExtSyncTask domain) */
export interface ExtSyncTask {
  id: string
  taskCode: string
  taskName: string
  systemId: string
  description?: string
  sourceApi: string
  /** GET / POST */
  httpMethod: string
  requestTemplate?: string
  businessKeyField?: string
  /** FULL / INCREMENTAL */
  syncMode: string
  targetTable?: string
  cronExpression?: string
  lastSyncTime?: string
  /** ACTIVE / DISABLED */
  status: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 同步记录 (对应后端 ExtSyncRecord domain) */
export interface ExtSyncRecord {
  id: string
  recordNo: string
  taskId: string
  systemId: string
  batchNo: string
  /** PENDING / RUNNING / SUCCESS / FAILED / PARTIAL */
  status: string
  /** MANUAL / SCHEDULED */
  triggerType: string
  totalCount?: number
  successCount?: number
  failedCount?: number
  requestSnapshot?: string
  responseSnapshot?: string
  httpStatus?: number
  errorMessage?: string
  startedTime?: string
  finishedTime?: string
  durationMs?: number
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 错误明细 / 死信队列 (对应后端 ExtSyncError domain) */
export interface ExtSyncError {
  id: string
  recordId: string
  taskId: string
  businessKey: string
  payload?: string
  errorCode?: string
  errorMessage?: string
  httpStatus?: number
  /** PENDING / RETRYING / RESOLVED / DEAD_LETTER */
  status: string
  retryCount?: number
  lastRetryTime?: string
  resolvedTime?: string
  createdBy?: string
  createdTime?: string
  updatedBy?: string
  updatedTime?: string
  version?: number
  remark?: string
}

/** 同步监控统计 VO (对应后端 ExtSyncStatsVO) */
export interface ExtSyncStatsVO {
  systemCount?: number
  taskCount?: number
  activeTaskCount?: number
  recordCount?: number
  recentRecordCount?: number
  recentSuccessCount?: number
  recentFailedCount?: number
  pendingErrorCount?: number
  deadLetterCount?: number
}

/** 创建/更新外部系统请求 */
export interface SaveExtSystemRequest {
  systemCode: string
  systemName: string
  description?: string
  endpoint: string
  authType: string
  credentials?: string
  connectTimeout?: number
  readTimeout?: number
  maxRetryCount?: number
  retryBackoffMs?: number
  status?: string
}

/** 创建/更新同步任务请求 */
export interface SaveExtSyncTaskRequest {
  taskCode: string
  taskName: string
  systemId: string
  description?: string
  sourceApi: string
  httpMethod?: string
  requestTemplate?: string
  businessKeyField?: string
  syncMode?: string
  targetTable?: string
  cronExpression?: string
  status?: string
}

/** 触发同步请求 */
export interface TriggerSyncRequest {
  triggerType?: string
  businessKeyFilter?: string
  customPayload?: string
}

// ==================== 外部系统 ====================

/** 分页查询外部系统 */
export function pageExtSystems(params: {
  pageNo?: number
  pageSize?: number
  systemCode?: string
  systemName?: string
  status?: string
}): Promise<InvPage<ExtSystem>> {
  return service.get('/ext/systems', { params }) as unknown as Promise<InvPage<ExtSystem>>
}

/** 查询全部活跃外部系统 (下拉用) */
export function listAllExtSystems(): Promise<ExtSystem[]> {
  return service.get('/ext/systems/all') as unknown as Promise<ExtSystem[]>
}

/** 查询外部系统详情 */
export function getExtSystem(id: string): Promise<ExtSystem> {
  return service.get(`/ext/systems/${id}`) as unknown as Promise<ExtSystem>
}

/** 创建外部系统 */
export function createExtSystem(data: SaveExtSystemRequest): Promise<ExtSystem> {
  return service.post('/ext/systems', data) as unknown as Promise<ExtSystem>
}

/** 更新外部系统 */
export function updateExtSystem(id: string, data: SaveExtSystemRequest): Promise<ExtSystem> {
  return service.put(`/ext/systems/${id}`, data) as unknown as Promise<ExtSystem>
}

/** 删除外部系统 */
export function deleteExtSystem(id: string): Promise<void> {
  return service.delete(`/ext/systems/${id}`) as unknown as Promise<void>
}

// ==================== 同步任务 ====================

/** 分页查询同步任务 */
export function pageExtSyncTasks(params: {
  pageNo?: number
  pageSize?: number
  taskCode?: string
  taskName?: string
  systemId?: string
  status?: string
}): Promise<InvPage<ExtSyncTask>> {
  return service.get('/ext/tasks', { params }) as unknown as Promise<InvPage<ExtSyncTask>>
}

/** 查询同步任务详情 */
export function getExtSyncTask(id: string): Promise<ExtSyncTask> {
  return service.get(`/ext/tasks/${id}`) as unknown as Promise<ExtSyncTask>
}

/** 创建同步任务 */
export function createExtSyncTask(data: SaveExtSyncTaskRequest): Promise<ExtSyncTask> {
  return service.post('/ext/tasks', data) as unknown as Promise<ExtSyncTask>
}

/** 更新同步任务 */
export function updateExtSyncTask(id: string, data: SaveExtSyncTaskRequest): Promise<ExtSyncTask> {
  return service.put(`/ext/tasks/${id}`, data) as unknown as Promise<ExtSyncTask>
}

/** 删除同步任务 */
export function deleteExtSyncTask(id: string): Promise<void> {
  return service.delete(`/ext/tasks/${id}`) as unknown as Promise<void>
}

/** 手动触发同步任务 (HTTP Client + 签名 + 重试 + 幂等 + 错误队列) */
export function triggerExtSyncTask(id: string, data?: TriggerSyncRequest): Promise<ExtSyncRecord> {
  return service.post(`/ext/tasks/${id}/trigger`, data || {}) as unknown as Promise<ExtSyncRecord>
}

// ==================== 同步记录 ====================

/** 分页查询同步记录 (同步监控) */
export function pageExtSyncRecords(params: {
  pageNo?: number
  pageSize?: number
  taskId?: string
  status?: string
  recordNo?: string
}): Promise<InvPage<ExtSyncRecord>> {
  return service.get('/ext/records', { params }) as unknown as Promise<InvPage<ExtSyncRecord>>
}

/** 查询同步记录详情 */
export function getExtSyncRecord(id: string): Promise<ExtSyncRecord> {
  return service.get(`/ext/records/${id}`) as unknown as Promise<ExtSyncRecord>
}

// ==================== 错误队列 (死信) ====================

/** 分页查询错误明细 (死信队列) */
export function pageExtSyncErrors(params: {
  pageNo?: number
  pageSize?: number
  taskId?: string
  status?: string
  businessKey?: string
}): Promise<InvPage<ExtSyncError>> {
  return service.get('/ext/errors', { params }) as unknown as Promise<InvPage<ExtSyncError>>
}

/** 重试单条错误记录 (PENDING/DEAD_LETTER → RETRYING → RESOLVED/DEAD_LETTER) */
export function retryExtSyncError(id: string): Promise<ExtSyncError> {
  return service.post(`/ext/errors/${id}/retry`) as unknown as Promise<ExtSyncError>
}

/** 手动解决错误记录 (归档) */
export function resolveExtSyncError(id: string): Promise<ExtSyncError> {
  return service.post(`/ext/errors/${id}/resolve`) as unknown as Promise<ExtSyncError>
}

// ==================== 同步监控统计 ====================

/** 同步监控统计 (系统数/任务数/近期记录/错误队列) */
export function getExtSyncStats(): Promise<ExtSyncStatsVO> {
  return service.get('/ext/stats') as unknown as Promise<ExtSyncStatsVO>
}
