import service from './request'

/**
 * 报表大屏可视化 API。设计来源: 42-报表与大屏可视化设计 R2/R3。
 * 覆盖报表设计器、大屏设计器、Widget 组件库三组端点。
 */

// ============ 类型定义 ============

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface RptReportDashboard {
  id: string
  tenantId: string
  reportCode: string
  reportName: string
  reportType: 'TABLE' | 'CHART' | 'MIX' | 'DASHBOARD'
  layoutJson: string
  datasetBindings?: string
  versionNo: number
  status: 'DRAFT' | 'PUBLISHED'
  ownerUserId?: string
  permissionCode?: string
  description?: string
  createdTime?: string
  updatedTime?: string
}

export interface RptDashboard {
  id: string
  tenantId: string
  dashboardCode: string
  dashboardName: string
  canvasWidth: number
  canvasHeight: number
  theme: 'dark' | 'light'
  backgroundImage?: string
  layoutJson?: string
  componentBindings?: string
  refreshInterval: number
  status: 'DRAFT' | 'PUBLISHED'
  ownerUserId?: string
  permissionCode?: string
  description?: string
  createdTime?: string
  updatedTime?: string
}

export interface RptWidget {
  id: string
  tenantId: string
  widgetCode: string
  widgetName: string
  widgetType: 'line-chart' | 'bar-chart' | 'pie-chart' | 'kpi-card' | 'data-table' | 'text'
  datasetCode?: string
  propsJson?: string
  styleJson?: string
  description?: string
  createdTime?: string
  updatedTime?: string
}

export interface SaveReportRequest {
  reportCode: string
  reportName: string
  reportType?: string
  layoutJson: string
  datasetBindings?: string
  permissionCode?: string
  description?: string
}

export interface SaveDashboardRequest {
  dashboardCode: string
  dashboardName: string
  canvasWidth?: number
  canvasHeight?: number
  theme?: string
  backgroundImage?: string
  layoutJson?: string
  componentBindings?: string
  refreshInterval?: number
  permissionCode?: string
  description?: string
}

export interface SaveWidgetRequest {
  widgetCode: string
  widgetName: string
  widgetType: string
  datasetCode?: string
  propsJson?: string
  styleJson?: string
  description?: string
}

// ============ 报表设计器 API ============

const REPORT_BASE = '/report/reports'

export function pageReportDashboard(params: {
  page?: number
  size?: number
  keyword?: string
  status?: string
  reportType?: string
}): Promise<PageResult<RptReportDashboard>> {
  return service.get(REPORT_BASE, { params }) as unknown as Promise<PageResult<RptReportDashboard>>
}

export function getReport(code: string): Promise<RptReportDashboard> {
  return service.get(`${REPORT_BASE}/${code}`) as unknown as Promise<RptReportDashboard>
}

export function createReport(data: SaveReportRequest): Promise<RptReportDashboard> {
  return service.post(REPORT_BASE, data) as unknown as Promise<RptReportDashboard>
}

export function updateReport(code: string, data: SaveReportRequest): Promise<RptReportDashboard> {
  return service.put(`${REPORT_BASE}/${code}`, data) as unknown as Promise<RptReportDashboard>
}

export function deleteReport(code: string): Promise<void> {
  return service.delete(`${REPORT_BASE}/${code}`) as unknown as Promise<void>
}

export function publishReport(code: string): Promise<RptReportDashboard> {
  return service.post(`${REPORT_BASE}/${code}/publish`) as unknown as Promise<RptReportDashboard>
}

export function renderReport(code: string): Promise<RptReportDashboard> {
  return service.get(`${REPORT_BASE}/${code}/render`) as unknown as Promise<RptReportDashboard>
}

// ============ 大屏设计器 API ============

const DASHBOARD_BASE = '/dashboards'

export function pageDashboards(params: {
  page?: number
  size?: number
  keyword?: string
  status?: string
  theme?: string
}): Promise<PageResult<RptDashboard>> {
  return service.get(DASHBOARD_BASE, { params }) as unknown as Promise<PageResult<RptDashboard>>
}

export function getDashboard(code: string): Promise<RptDashboard> {
  return service.get(`${DASHBOARD_BASE}/${code}`) as unknown as Promise<RptDashboard>
}

export function createDashboard(data: SaveDashboardRequest): Promise<RptDashboard> {
  return service.post(DASHBOARD_BASE, data) as unknown as Promise<RptDashboard>
}

export function updateDashboard(code: string, data: SaveDashboardRequest): Promise<RptDashboard> {
  return service.put(`${DASHBOARD_BASE}/${code}`, data) as unknown as Promise<RptDashboard>
}

export function deleteDashboard(code: string): Promise<void> {
  return service.delete(`${DASHBOARD_BASE}/${code}`) as unknown as Promise<void>
}

export function publishDashboard(code: string): Promise<RptDashboard> {
  return service.post(`${DASHBOARD_BASE}/${code}/publish`) as unknown as Promise<RptDashboard>
}

export function renderFullscreenDashboard(code: string): Promise<RptDashboard> {
  return service.get(`${DASHBOARD_BASE}/${code}/fullscreen`) as unknown as Promise<RptDashboard>
}

// ============ Widget 组件 API ============

const WIDGET_BASE = '/widgets'

export function pageWidgets(params: {
  page?: number
  size?: number
  keyword?: string
  widgetType?: string
}): Promise<PageResult<RptWidget>> {
  return service.get(WIDGET_BASE, { params }) as unknown as Promise<PageResult<RptWidget>>
}

export function getWidget(code: string): Promise<RptWidget> {
  return service.get(`${WIDGET_BASE}/${code}`) as unknown as Promise<RptWidget>
}

export function createWidget(data: SaveWidgetRequest): Promise<RptWidget> {
  return service.post(WIDGET_BASE, data) as unknown as Promise<RptWidget>
}

export function updateWidget(code: string, data: SaveWidgetRequest): Promise<RptWidget> {
  return service.put(`${WIDGET_BASE}/${code}`, data) as unknown as Promise<RptWidget>
}

export function deleteWidget(code: string): Promise<void> {
  return service.delete(`${WIDGET_BASE}/${code}`) as unknown as Promise<void>
}
