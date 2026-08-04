import service from './request'

/**
 * 监控 API 模块 (GA2-L177 落地)
 *
 * 设计来源:
 * - 91-Web基础后台逐页交互详设「服务健康页/缓存概览页」
 * - 62-可观测性指标日志链路详设
 *
 * 覆盖端点:
 * - GET /monitor/health        服务健康检查（组件级）
 * - GET /monitor/cache         缓存概览
 * - DELETE /monitor/cache/{cacheName}  清理指定缓存（白名单）
 */

// ===== 服务健康 =====

export interface HealthComponent {
  name: string
  status: 'UP' | 'DOWN' | 'SKIPPED' | 'DEGRADED'
  durationMs: number
  checkedAt: string
  detail: string | null
  errorSummary: string | null
  traceId: string
}

export interface HealthOverview {
  status: 'UP' | 'DEGRADED' | 'DOWN'
  platform: string
  version: string
  profile: string
  runMode: 'boot' | 'cloud'
  checkedAt: string
  components: HealthComponent[]
}

/** 服务健康检查: GET /monitor/health */
export function getHealth(): Promise<HealthOverview> {
  return service.get('/monitor/health') as unknown as Promise<HealthOverview>
}

// ===== 缓存概览 =====

export interface CacheItem {
  cacheName: string
  keyPrefix: string
  clearable: boolean
  keyCount: number
  ttlPolicy: string
  lastRefreshTime: string | null
}

export interface CacheOverview {
  status: string
  provider: string
  endpoint: string
  globalHitRate: number
  checkedAt: string
  caches: CacheItem[]
}

/** 缓存概览: GET /monitor/cache */
export function getCacheOverview(): Promise<CacheOverview> {
  return service.get('/monitor/cache') as unknown as Promise<CacheOverview>
}

/** 清理指定缓存（白名单）: DELETE /monitor/cache/{cacheName} */
export function clearCache(cacheName: string): Promise<{
  cacheName: string
  deletedKeys: number
  clearedAt: string
}> {
  return service.delete(
    `/monitor/cache/${encodeURIComponent(cacheName)}`
  ) as unknown as Promise<{
    cacheName: string
    deletedKeys: number
    clearedAt: string
  }>
}

// ===== 聚合 Metrics =====

export interface MetricsSnapshot {
  jvmHeapUsed: number
  jvmHeapMax: number
  jvmHeapCommitted: number
  jvmThreadLive: number
  jvmThreadPeak: number
  dbPoolActive: number
  dbPoolIdle: number
  dbPoolMax: number
  httpP95: number
  httpP99: number
}

/** 聚合 Metrics 指标: GET /monitor/metrics */
export function getMetrics(): Promise<MetricsSnapshot> {
  return service.get('/monitor/metrics') as unknown as Promise<MetricsSnapshot>
}
