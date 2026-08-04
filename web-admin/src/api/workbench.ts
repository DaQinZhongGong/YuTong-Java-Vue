import service from './request'
import type { WorkbenchStats, WorkbenchTrend, WorkbenchTopItem } from './types'

/**
 * 工作台 API。设计来源: 18-样例业务详细设计 workbench 聚合统计、42-报表与大屏可视化设计 R0。
 * GA2-25: 修正路径 bug (原 /api/v1/workbench/stats 与 baseURL /api/v1 拼接产生双 /api/v1)。
 * 后端 WorkbenchController @RequestMapping("/api/v1/workbench")
 * GA2-33: 新增趋势 + 金额 Top N 端点，支撑前端 ECharts 图表。
 */
export function getWorkbenchStats(): Promise<WorkbenchStats> {
  return service.get('/workbench/stats') as unknown as Promise<WorkbenchStats>
}

/** GA2-33: 近 N 日申请单提交趋势。对齐 42 号文档 biz_request_trend_7d 数据集。 */
export function getWorkbenchTrend(days = 7): Promise<WorkbenchTrend> {
  return service.get('/workbench/trend', { params: { days } }) as unknown as Promise<WorkbenchTrend>
}

/** GA2-33: 金额 Top N 申请单。对齐 42 号文档 biz_request_amount_top10 数据集。 */
export function getWorkbenchTopAmount(limit = 10): Promise<WorkbenchTopItem[]> {
  return service.get('/workbench/top-amount', { params: { limit } }) as unknown as Promise<WorkbenchTopItem[]>
}
