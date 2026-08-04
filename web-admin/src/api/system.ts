import service from './request'
import type {
  SysConfig,
  OperationLog,
  JobLog,
  ImportExportTask,
  PageResult,
} from './types'

/**
 * 系统管理 API 模块 (GA2-15 落地)
 *
 * 设计来源:
 * - 17-平台基础能力详细设计 (参数配置/操作日志/任务日志)
 * - 91-Web基础后台逐页交互详设
 * - 98-后端实现蓝图系统基础接口补齐规则
 *
 * 覆盖端点:
 * - 参数配置: GET/POST /configs, GET/PUT/DELETE /configs/{id}, PUT /configs/refresh-cache
 * - 操作日志: GET /operation-logs, GET /operation-logs/{id}
 * - 任务日志: GET /job-logs, GET /job-logs/{id}, POST /job-logs/{id}/retry
 * - 导入导出任务: GET /import-export-tasks, GET /import-export-tasks/{id}, POST /import-export-tasks/{id}/retry
 */

// ===== 参数配置 =====

export interface ConfigPageQuery {
  page: number
  size: number
  keyword?: string
  configGroup?: string
}

/** 分页查询参数配置: GET /configs */
export function getConfigs(
  params: ConfigPageQuery
): Promise<PageResult<SysConfig>> {
  return service.get('/configs', { params }) as unknown as Promise<
    PageResult<SysConfig>
  >
}

/** 查询参数配置详情: GET /configs/{id} */
export function getConfig(id: string): Promise<SysConfig> {
  return service.get(`/configs/${id}`) as unknown as Promise<SysConfig>
}

/** 创建参数配置: POST /configs */
export function createConfig(data: Partial<SysConfig>): Promise<SysConfig> {
  return service.post('/configs', data) as unknown as Promise<SysConfig>
}

/** 更新参数配置: PUT /configs/{id} */
export function updateConfig(
  id: string,
  data: Partial<SysConfig>
): Promise<SysConfig> {
  return service.put(`/configs/${id}`, data) as unknown as Promise<SysConfig>
}

/** 删除参数配置: DELETE /configs/{id} */
export function deleteConfig(id: string): Promise<void> {
  return service.delete(`/configs/${id}`) as unknown as Promise<void>
}

/** 刷新参数配置缓存: PUT /configs/refresh-cache */
export function refreshConfigCache(): Promise<void> {
  return service.put('/configs/refresh-cache') as unknown as Promise<void>
}

// ===== 操作日志 =====

export interface OperationLogPageQuery {
  page: number
  size: number
  operatorId?: string
  keyword?: string
  result?: string
}

/** 分页查询操作日志: GET /operation-logs */
export function getOperationLogs(
  params: OperationLogPageQuery
): Promise<PageResult<OperationLog>> {
  return service.get('/operation-logs', { params }) as unknown as Promise<
    PageResult<OperationLog>
  >
}

/** 查询操作日志详情: GET /operation-logs/{id} */
export function getOperationLog(id: string): Promise<OperationLog> {
  return service.get(`/operation-logs/${id}`) as unknown as Promise<OperationLog>
}

// ===== 任务日志 =====

export interface JobLogPageQuery {
  page: number
  size: number
  jobName?: string
  status?: string
}

/** 分页查询任务日志: GET /job-logs */
export function getJobLogs(
  params: JobLogPageQuery
): Promise<PageResult<JobLog>> {
  return service.get('/job-logs', { params }) as unknown as Promise<
    PageResult<JobLog>
  >
}

/** 查询任务日志详情: GET /job-logs/{id} */
export function getJobLog(id: string): Promise<JobLog> {
  return service.get(`/job-logs/${id}`) as unknown as Promise<JobLog>
}

/** 重试任务: POST /job-logs/{id}/retry */
export function retryJob(id: string): Promise<JobLog> {
  return service.post(`/job-logs/${id}/retry`) as unknown as Promise<JobLog>
}

// ===== 导入导出任务 =====

export interface ImportExportTaskPageQuery {
  page: number
  size: number
  taskType?: string
  status?: string
}

/** 分页查询导入导出任务: GET /import-export-tasks */
export function getImportExportTasks(
  params: ImportExportTaskPageQuery
): Promise<PageResult<ImportExportTask>> {
  return service.get('/import-export-tasks', { params }) as unknown as Promise<
    PageResult<ImportExportTask>
  >
}

/** 查询导入导出任务详情: GET /import-export-tasks/{id} */
export function getImportExportTask(id: string): Promise<ImportExportTask> {
  return service.get(
    `/import-export-tasks/${id}`
  ) as unknown as Promise<ImportExportTask>
}

/** 重试导入导出任务: POST /import-export-tasks/{id}/retry */
export function retryImportExportTask(id: string): Promise<ImportExportTask> {
  return service.post(
    `/import-export-tasks/${id}/retry`
  ) as unknown as Promise<ImportExportTask>
}
