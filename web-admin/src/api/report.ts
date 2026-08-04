import service from './request'
import type {
  RptDataset,
  RptReport,
  DatasetResultVO,
  ReportRenderVO,
  ReportExplainVO,
  SaveDatasetRequest,
  SaveReportRequest,
  ImportExportTask,
  PageResult,
} from './types'

/**
 * 报表分析 API。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 R1。
 * 后端 ReportController @RequestMapping("/api/v1/report")
 *
 * GA2-36 验证 5 项能力:
 *  - 物化视图刷新: refreshMaterializedView / queryMaterializedView
 *  - ECharts 图表: renderReport (前端按 layout 渲染)
 *  - 大数据量导出异步化: exportReport (返回 PENDING, 轮询 import-export-tasks)
 *  - AI 指标解释: explainReport (降级模式 degraded=true)
 *  - 数据权限下的报表过滤: 复用后端 DatasetEngine DataScope 注入 + 列级脱敏
 */

const BASE = '/report'

// ==================== 数据集 ====================

export interface DatasetPageQuery {
  page?: number
  size?: number
  datasetCode?: string
  datasetName?: string
  status?: string
}

export function pageDatasets(params?: DatasetPageQuery): Promise<PageResult<RptDataset>> {
  return service.get(`${BASE}/datasets`, { params }) as unknown as Promise<PageResult<RptDataset>>
}

export function getDataset(code: string): Promise<RptDataset> {
  return service.get(`${BASE}/datasets/${code}`) as unknown as Promise<RptDataset>
}

export function createDataset(data: SaveDatasetRequest): Promise<RptDataset> {
  return service.post(`${BASE}/datasets`, data) as unknown as Promise<RptDataset>
}

export function updateDataset(code: string, data: SaveDatasetRequest): Promise<RptDataset> {
  return service.put(`${BASE}/datasets/${code}`, data) as unknown as Promise<RptDataset>
}

export function deleteDataset(code: string): Promise<void> {
  return service.delete(`${BASE}/datasets/${code}`) as unknown as Promise<void>
}

/** 数据集预览（100 行限制，不缓存，不走 DataScope） */
export function previewDataset(code: string, params?: Record<string, unknown>): Promise<DatasetResultVO> {
  return service.post(`${BASE}/datasets/${code}/preview`, params ?? {}) as unknown as Promise<DatasetResultVO>
}

// ==================== 报表 ====================

export interface ReportPageQuery {
  page?: number
  size?: number
  reportCode?: string
  reportName?: string
  reportType?: string
  status?: string
}

export function pageReports(params?: ReportPageQuery): Promise<PageResult<RptReport>> {
  return service.get(`${BASE}/reports`, { params }) as unknown as Promise<PageResult<RptReport>>
}

export function getReport(code: string): Promise<RptReport> {
  return service.get(`${BASE}/reports/${code}`) as unknown as Promise<RptReport>
}

export function createReport(data: SaveReportRequest): Promise<RptReport> {
  return service.post(`${BASE}/reports`, data) as unknown as Promise<RptReport>
}

export function updateReport(code: string, data: SaveReportRequest): Promise<RptReport> {
  return service.put(`${BASE}/reports/${code}`, data) as unknown as Promise<RptReport>
}

export function deleteReport(code: string): Promise<void> {
  return service.delete(`${BASE}/reports/${code}`) as unknown as Promise<void>
}

/** 渲染报表（返回 layoutJson + 各组件数据集执行结果，前端 ECharts 渲染） */
export function renderReport(
  code: string,
  params?: Record<string, unknown>,
): Promise<ReportRenderVO> {
  return service.get(`${BASE}/reports/${code}/render`, { params }) as unknown as Promise<ReportRenderVO>
}

/** 异步导出报表（PENDING → 异步生成 → SUCCESS/FAILED） */
export function exportReport(
  code: string,
  params?: Record<string, unknown>,
): Promise<ImportExportTask> {
  return service.post(`${BASE}/reports/${code}/export`, params ?? {}) as unknown as Promise<ImportExportTask>
}

/** AI 指标解释（降级模式：返回结构化数据摘要） */
export function explainReport(code: string): Promise<ReportExplainVO> {
  return service.post(`${BASE}/reports/${code}/explain`) as unknown as Promise<ReportExplainVO>
}

// ==================== 物化视图 ====================

export function refreshMaterializedView(name: string): Promise<void> {
  return service.post(`${BASE}/materialized-views/${name}/refresh`) as unknown as Promise<void>
}

export function queryMaterializedView(
  name: string,
): Promise<Record<string, unknown>[]> {
  return service.get(`${BASE}/materialized-views/${name}/data`) as unknown as Promise<Record<string, unknown>[]>
}

// ==================== 异步导出任务轮询 (复用系统导入导出任务接口) ====================

export function getImportExportTask(id: string): Promise<ImportExportTask> {
  return service.get(`/api/v1/import-export-tasks/${id}`) as unknown as Promise<ImportExportTask>
}
