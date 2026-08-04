import service from './request'
import type { PageResult } from './types'

/**
 * 插件管理 API（45 号文档「插件与模板生态设计」）
 * 对齐后端 PluginController @RequestMapping("/api/v1/plugins")
 */

/** 插件包 */
export interface PluginPackage {
  id: string
  pluginCode: string
  pluginName: string
  pluginVersion: string
  packageHash?: string
  signatureStatus: string
  licenseType?: string
  riskLevel: string
  minPlatformVersion?: string
  maxPlatformVersion?: string
  status: string
  installCount: number
  author?: string
  description?: string
  dependenciesJson?: string
  createdTime?: string
  updatedTime?: string
}

/** 插件安装记录 */
export interface PluginInstallation {
  id: string
  pluginId: string
  installedBy: string
  installedTime: string
  uninstalledTime?: string
  status: string
  configJson?: string
  createdTime?: string
}

/** 插件审计日志 */
export interface PluginAuditLog {
  id: string
  pluginCode: string
  pluginVersion: string
  operatorId: string
  permissionCode?: string
  bizType: string
  bizId: string
  traceId?: string
  result: string
  errorCode?: string
  createdTime: string
}

/** 市场插件 VO */
export interface MarketPluginVO {
  id: string
  pluginCode: string
  pluginName: string
  pluginVersion: string
  description?: string
  author?: string
  licenseType?: string
  riskLevel: string
  installCount: number
  minPlatformVersion?: string
  maxPlatformVersion?: string
  installed: boolean
  installationStatus?: string
  dependenciesJson?: string
}

/** 依赖校验结果 */
export interface PluginDependencyCheckResult {
  passed: boolean
  missingDependencies: string[]
  versionCompatible: boolean
  versionConflictMessage?: string
}

/** 插件分页查询参数 */
export interface PluginPackagePageQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
  riskLevel?: string
}

/** 安装插件请求 */
export interface InstallPluginRequest {
  configJson?: string
}

/** 分页查询插件包 */
export function getPlugins(params: PluginPackagePageQuery): Promise<PageResult<PluginPackage>> {
  return service.get('/plugins', { params }) as unknown as Promise<PageResult<PluginPackage>>
}

/** 查询插件详情 */
export function getPlugin(id: string): Promise<PluginPackage> {
  return service.get(`/plugins/${id}`) as unknown as Promise<PluginPackage>
}

/** 安装插件 */
export function installPlugin(id: string, data?: InstallPluginRequest): Promise<PluginInstallation> {
  return service.post(`/plugins/${id}/install`, data) as unknown as Promise<PluginInstallation>
}

/** 卸载插件 */
export function uninstallPlugin(id: string): Promise<void> {
  return service.post(`/plugins/${id}/uninstall`) as unknown as Promise<void>
}

/** 启用插件 */
export function enablePlugin(id: string): Promise<void> {
  return service.post(`/plugins/${id}/enable`) as unknown as Promise<void>
}

/** 禁用插件 */
export function disablePlugin(id: string): Promise<void> {
  return service.post(`/plugins/${id}/disable`) as unknown as Promise<void>
}

/** 查询插件市场列表 */
export function getMarketPlugins(): Promise<MarketPluginVO[]> {
  return service.get('/plugins/market') as unknown as Promise<MarketPluginVO[]>
}

/** 插件依赖校验 */
export function checkPluginDependencies(id: string): Promise<PluginDependencyCheckResult> {
  return service.get(`/plugins/${id}/dependency-check`) as unknown as Promise<PluginDependencyCheckResult>
}

/** 查询已安装插件列表 */
export function getInstalledPlugins(): Promise<PluginInstallation[]> {
  return service.get('/plugins/installed') as unknown as Promise<PluginInstallation[]>
}

/** 分页查询插件审计日志 */
export function getPluginAuditLogs(
  pageNo: number,
  pageSize: number,
  pluginCode?: string
): Promise<PageResult<PluginAuditLog>> {
  return service.get('/plugins/audit-logs', {
    params: { pageNo, pageSize, pluginCode },
  }) as unknown as Promise<PageResult<PluginAuditLog>>
}

/**
 * 模板市场 API（45 号文档「插件与模板生态设计」）
 * 对齐后端 TemplateController @RequestMapping("/api/v1/templates")
 */

/** 模板 */
export interface MktTemplate {
  id: string
  templateCode: string
  templateName: string
  category: string
  previewImages?: string
  packageUrl?: string
  minVersion?: string
  installCount: number
  status: string
  createdTime?: string
  updatedTime?: string
}

/** 模板分页查询参数 */
export interface TemplatePageQuery {
  pageNo: number
  pageSize: number
  keyword?: string
  category?: string
  status?: string
}

/** 分页查询模板 */
export function getTemplates(params: TemplatePageQuery): Promise<PageResult<MktTemplate>> {
  return service.get('/templates', { params }) as unknown as Promise<PageResult<MktTemplate>>
}

/** 查询模板详情 */
export function getTemplate(id: string): Promise<MktTemplate> {
  return service.get(`/templates/${id}`) as unknown as Promise<MktTemplate>
}
