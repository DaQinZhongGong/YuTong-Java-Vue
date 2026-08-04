import service from './request'

/** 模块授权状态 (对齐后端 LicenseController.ModuleStatus record) */
export interface ModuleStatus {
  moduleCode: string
  licensed: boolean
  switchOn: boolean
  active: boolean
}

/** 当前授权信息 (对齐后端 LicenseInfo record) */
export interface LicenseInfo {
  licenseId: string | null
  edition: string
  subject: string
  deploymentId: string | null
  licenseSchemaVersion: string
  expireTime: string | null
  status: string
  modules: string[]
  limitsJson: string
  signature: string | null
  fallbackCommunity: boolean
}

/** 查询当前授权信息: GET /license/current */
export function getCurrentLicense(): Promise<LicenseInfo> {
  return service.get('/license/current') as unknown as Promise<LicenseInfo>
}

/** 查询指定模块授权状态: GET /license/modules/{moduleCode} */
export function getModuleStatus(moduleCode: string): Promise<ModuleStatus> {
  return service.get(`/license/modules/${moduleCode}`) as unknown as Promise<ModuleStatus>
}

/** 查询所有模块授权状态: GET /license/modules */
export function listModuleStatuses(): Promise<ModuleStatus[]> {
  return service.get('/license/modules') as unknown as Promise<ModuleStatus[]>
}

/** 额度使用情况 (对齐后端 LicenseController.QuotaUsage record) */
export interface QuotaUsage {
  quotaCode: string
  used: number
  limit: number | null
  period: string
  lastUsedTime: string | null
}

/** 查询额度使用情况: GET /license/usage */
export function getLicenseUsage(): Promise<QuotaUsage[]> {
  return service.get('/license/usage') as unknown as Promise<QuotaUsage[]>
}

// ===== GA2-L174: License 上传 / 刷新 / 审计日志 =====

/** License 上传结果 (对齐后端 LicenseUploadResult record) */
export interface LicenseUploadResult {
  licenseId: string
  edition: string
  subject: string
  expireTime: string
  modules: string[]
  previousRevoked: boolean
}

/** 授权审计日志 (对齐后端 LicenseAuditLogVo record) */
export interface LicenseAuditLog {
  action: string
  licenseId: string | null
  moduleCode: string | null
  quotaCode: string | null
  result: string
  errorCode: string | null
  userIdHash: string | null
  traceId: string | null
  detailJson: string | null
  operatedTime: string
}

/** 分页结果 (对齐 MyBatis-Plus Page) */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

/** 审计日志查询参数 */
export interface AuditLogQuery {
  page?: number
  size?: number
  action?: string
  licenseId?: string
  moduleCode?: string
}

/** 上传新 License 文件: POST /license/upload */
export function uploadLicense(licenseJson: string): Promise<LicenseUploadResult> {
  return service.post('/license/upload', licenseJson, {
    headers: { 'Content-Type': 'application/json' },
  }) as unknown as Promise<LicenseUploadResult>
}

/** 手动刷新 License: POST /license/refresh */
export function refreshLicense(): Promise<void> {
  return service.post('/license/refresh') as unknown as Promise<void>
}

/** 查询授权审计日志: GET /license/audit-logs */
export function listAuditLogs(query: AuditLogQuery): Promise<PageResult<LicenseAuditLog>> {
  return service.get('/license/audit-logs', { params: query }) as unknown as Promise<PageResult<LicenseAuditLog>>
}
