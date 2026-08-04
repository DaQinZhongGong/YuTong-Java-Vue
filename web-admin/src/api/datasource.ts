/**
 * 数据源管理 API。设计来源: 46-多数据源与数据集设计 (GA2-46 v1.5)
 *
 * 8 个端点覆盖 4 大能力域:
 *   1. 数据源管理 CRUD (5 端点: 分页/详情/创建/更新/删除)
 *   2. 连接测试 (1 端点)
 *   3. 元数据浏览 (2 端点: 表列表/列列表)
 *
 * 安全约束:
 *   - VO 脱敏: jdbcUrlMasked 只返回 host:port, 不泄露 user/password 参数
 *   - 连接测试失败不泄露数据库地址/用户名/密码/完整驱动异常
 *   - 元数据浏览按 ACL 过滤, 敏感列隐藏
 */
import service from './request'
import type { PageResult } from './types'

// ==================== 类型定义 ====================

export interface DatasourceVO {
  id?: string
  datasourceCode: string
  datasourceName: string
  dbType: 'POSTGRESQL' | 'MYSQL' | 'ORACLE' | 'SQLSERVER'
  /** 脱敏后的 jdbc_url: 只保留 host:port/db, 隐藏 user/password 参数 */
  jdbcUrlMasked?: string
  /** 密钥引用 (不返回解析后的真实值) */
  usernameRef?: string
  passwordRef?: string
  poolConfig?: string
  readOnly?: boolean
  enabled?: boolean
  /** 运行时健康状态: UP/DOWN/UNKNOWN */
  healthStatus?: 'UP' | 'DOWN' | 'UNKNOWN'
  lastCheckTime?: string
  /** 脱敏后的错误信息 */
  lastErrorMessage?: string
  configVersion?: number
  enabledTime?: string
  lagThresholdMs?: number
  description?: string
  createdTime?: string
  updatedTime?: string
}

export interface SaveDatasourceRequest {
  datasourceCode: string
  datasourceName: string
  dbType: 'POSTGRESQL' | 'MYSQL' | 'ORACLE' | 'SQLSERVER'
  jdbcUrl: string
  usernameRef?: string
  passwordRef?: string
  poolConfig?: string
  readOnly?: boolean
  enabled?: boolean
  lagThresholdMs?: number
  description?: string
}

export interface ConnectionTestResultVO {
  connected: boolean
  healthStatus: 'UP' | 'DOWN'
  databaseProductName?: string
  databaseProductVersion?: string
  latencyMs?: number
  testTime?: string
  /** 脱敏错误摘要 */
  errorMessage?: string
}

export interface TableMetadataVO {
  tableName: string
  tableSchema: string
  tableType: 'BASE TABLE' | 'VIEW' | string
  tableComment?: string
  estimatedRows?: number
  queryAllowed?: boolean
  maxRows?: number
}

export interface ColumnMetadataVO {
  columnName: string
  dataType: string
  nullable?: boolean
  columnSize?: number
  columnDefault?: string
  ordinalPosition?: number
  columnComment?: string
  /** 敏感级别 (仅管理员可见) */
  sensitivityLevel?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  maskingStrategy?: 'MASK' | 'HIDE' | 'HASH' | 'NONE'
}

// ==================== API 函数 ====================

const BASE = '/admin/datasources'

/** 分页查询数据源列表 (脱敏) */
export function pageDatasources(params: {
  page?: number
  size?: number
  keyword?: string
}): Promise<PageResult<DatasourceVO>> {
  return service.get(`${BASE}`, { params }) as unknown as Promise<PageResult<DatasourceVO>>
}

/** 查询数据源详情 (脱敏) */
export function getDatasource(code: string): Promise<DatasourceVO> {
  return service.get(`${BASE}/${code}`) as unknown as Promise<DatasourceVO>
}

/** 创建数据源 */
export function createDatasource(data: SaveDatasourceRequest): Promise<DatasourceVO> {
  return service.post(`${BASE}`, data) as unknown as Promise<DatasourceVO>
}

/** 更新数据源 */
export function updateDatasource(code: string, data: SaveDatasourceRequest): Promise<DatasourceVO> {
  return service.put(`${BASE}/${code}`, data) as unknown as Promise<DatasourceVO>
}

/** 删除数据源 (primary 不允许删除) */
export function deleteDatasource(code: string): Promise<void> {
  return service.delete(`${BASE}/${code}`) as unknown as Promise<void>
}

/** 数据源连接测试 (只返回连通性和脱敏摘要) */
export function testDatasourceConnection(code: string): Promise<ConnectionTestResultVO> {
  return service.post(`${BASE}/${code}/test`) as unknown as Promise<ConnectionTestResultVO>
}

/** 表元数据浏览 (按 ACL 过滤) */
export function listDatasourceTables(code: string): Promise<TableMetadataVO[]> {
  return service.get(`${BASE}/${code}/tables`) as unknown as Promise<TableMetadataVO[]>
}

/** 列元数据浏览 (按 ACL 过滤, 敏感列隐藏) */
export function listDatasourceColumns(code: string, table: string): Promise<ColumnMetadataVO[]> {
  return service.get(`${BASE}/${code}/tables/${table}/columns`) as unknown as Promise<ColumnMetadataVO[]>
}
