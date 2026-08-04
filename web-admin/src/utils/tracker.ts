/**
 * 端侧埋点 SDK。设计来源: 94-端侧埋点与体验监控详设
 *
 * 封装 track / trackPageView / trackApi / trackError / trackPerformance 五个核心 API。
 * 具备采样率、批量上报、失败重试、脱敏管道、禁用开关和 debug 模式。
 *
 * 隐私约束（94 号文档第 96-98 行）：
 *  - 不得采集明文密码、token、完整手机号、完整身份证、文件原文、AI Prompt 敏感字段
 *  - userIdHash / tenantIdHash / bizIdHash 由 SDK hash 后上报
 *  - 业务标题、申请原因、审核意见不得进入埋点
 *
 * 上报接口：POST /api/v1/client-events/batch（后端 ClientEventController）
 */

// ==================== 类型定义 ====================

/** 端侧埋点事件载荷。对齐后端 ClientEvent domain 字段。 */
export interface ClientEventPayload {
  eventId: string
  eventName: string
  occurredTime: string
  traceId?: string
  sessionId: string
  userIdHash?: string
  tenantIdHash?: string
  route: string
  pageTitle?: string
  platform: string
  appVersion: string
  bizType?: string
  bizIdHash?: string
  result?: string
  errorCode?: string
  durationMs?: number
  /** 附加业务字段 JSON 字符串（已脱敏） */
  payload?: string
}

/** SDK 配置 */
export interface TrackerConfig {
  /** 采样率 0~1，默认 1.0（100%） */
  sampleRate?: number
  /** 批量上报大小，默认 20 */
  batchSize?: number
  /** 定时刷新间隔（毫秒），默认 5000 */
  flushInterval?: number
  /** 是否启用，默认 true */
  enabled?: boolean
  /** debug 模式（控制台打印），默认 import.meta.env.DEV */
  debug?: boolean
  /** 上报端点 */
  endpoint?: string
}

// ==================== 内部状态 ====================

const DEFAULT_CONFIG: Required<TrackerConfig> = {
  sampleRate: 1.0,
  batchSize: 20,
  flushInterval: 5000,
  enabled: true,
  debug: import.meta.env.DEV,
  endpoint: '/api/v1/client-events/batch',
}

let config: Required<TrackerConfig> = { ...DEFAULT_CONFIG }
let eventQueue: ClientEventPayload[] = []
let flushTimer: ReturnType<typeof setInterval> | null = null
let sessionId = generateSessionId()
let appVersion = '1.0.0'

// ==================== 工具函数 ====================

/** 生成简易唯一 eventId（时间戳 + 随机数，不严格 ULID 但满足端侧唯一性） */
function generateEventId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 12)
}

/** 生成会话 ID（运行期生成，页面刷新后变更） */
function generateSessionId(): string {
  return 'sess_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

/**
 * 简易 hash 函数（djb2 变体），用于 userIdHash / tenantIdHash / bizIdHash。
 * 注意：这不是加密安全的 hash，仅用于隐私脱敏（避免上报明文 ID）。
 */
function hash(value: string | undefined | null): string | undefined {
  if (value == null || value === '') return undefined
  let h = 5381
  for (let i = 0; i < value.length; i++) {
    h = ((h << 5) + h) + value.charCodeAt(i)
    h = h & 0xffffffff
  }
  return 'h_' + (h >>> 0).toString(36)
}

/**
 * 脱敏管道：对 payload 对象中的敏感字段进行脱敏。
 * 设计来源: 94 号文档第 96-98 行隐私约束。
 *
 * 内置脱敏规则：
 *  - password / pwd / token / secret / apiKey → '***'
 *  - phone / mobile / tel → 后四位 + '****'
 *  - idCard / idNumber → 前 4 + '****' + 后 4
 *  - prompt / content / reason / opinion → 不上报（删除字段）
 */
function sanitizePayload(payload: Record<string, unknown> | undefined): string | undefined {
  if (payload == null) return undefined

  const sanitized: Record<string, unknown> = {}
  const SENSITIVE_KEYS = ['password', 'pwd', 'token', 'secret', 'apikey', 'authorization']
  const PHONE_KEYS = ['phone', 'mobile', 'tel', 'telephone']
  const IDCARD_KEYS = ['idcard', 'idnumber', 'id_card', 'id_number']
  const FORBIDDEN_KEYS = ['prompt', 'content', 'reason', 'opinion', 'title', 'applyreason', 'auditopinion']

  for (const [key, value] of Object.entries(payload)) {
    const lowerKey = key.toLowerCase()

    // 元数据白名单：这些字段名虽包含 forbidden 子串（如 opinionLength 包含 opinion），
    // 但它们是统计元数据而非敏感正文，94 号文档明确要求保留 opinionLength
    const META_WHITELIST = ['opinionlength', 'hasopinion', 'hasreason', 'titlelength', 'contentlength', 'promptlength', 'reasoncode']
    if (META_WHITELIST.includes(lowerKey)) { sanitized[key] = value; continue }

    if (FORBIDDEN_KEYS.some(k => lowerKey.includes(k))) {
      continue // 不得进入埋点
    }
    if (SENSITIVE_KEYS.some(k => lowerKey.includes(k))) {
      sanitized[key] = '***'
      continue
    }
    if (PHONE_KEYS.some(k => lowerKey.includes(k)) && typeof value === 'string') {
      sanitized[key] = value.length > 4 ? '****' + value.slice(-4) : '****'
      continue
    }
    if (IDCARD_KEYS.some(k => lowerKey.includes(k)) && typeof value === 'string' && value.length >= 8) {
      sanitized[key] = value.slice(0, 4) + '****' + value.slice(-4)
      continue
    }
    sanitized[key] = value
  }

  try {
    return JSON.stringify(sanitized)
  } catch {
    return undefined
  }
}

/** 获取当前路由 */
function getCurrentRoute(): string {
  return window.location.pathname + window.location.search
}

/** 获取页面标题 */
function getPageTitle(): string {
  return document.title || ''
}

// ==================== 核心API ====================

/**
 * 初始化 SDK 配置。可在 main.ts 中调用。
 */
export function initTracker(overrides?: TrackerConfig): void {
  config = { ...DEFAULT_CONFIG, ...overrides }
  if (config.debug) {
    console.debug('[tracker] initialized', { config, sessionId })
  }
  startFlushTimer()
}

/** 设置应用版本（用于 appVersion 字段） */
export function setAppVersion(version: string): void {
  appVersion = version
}

/**
 * 通用事件追踪。
 *
 * @param eventName 事件名，小写点分格式 端.模块.对象.动作
 * @param options 事件选项（业务字段、结果、耗时等）
 */
export function track(
  eventName: string,
  options: {
    traceId?: string
    bizType?: string
    bizId?: string
    result?: string
    errorCode?: string
    durationMs?: number
    pageTitle?: string
    route?: string
    /** 附加业务字段（SDK 自动脱敏） */
    payload?: Record<string, unknown>
  } = {}
): void {
  if (!config.enabled) return
  if (!eventName) return

  // 采样率检查
  if (Math.random() > config.sampleRate) return

  const event: ClientEventPayload = {
    eventId: generateEventId(),
    eventName,
    occurredTime: new Date().toISOString(),
    traceId: options.traceId,
    sessionId,
    route: options.route || getCurrentRoute(),
    pageTitle: options.pageTitle || getPageTitle(),
    platform: 'web',
    appVersion,
    bizType: options.bizType,
    bizIdHash: hash(options.bizId),
    result: options.result,
    errorCode: options.errorCode,
    durationMs: options.durationMs,
    payload: sanitizePayload(options.payload),
  }

  eventQueue.push(event)

  if (config.debug) {
    console.debug('[tracker] track', eventName, event)
  }

  // 队列满则立即刷新
  if (eventQueue.length >= config.batchSize) {
    void flush()
  }
}

/**
 * 页面访问追踪。在 router.afterEach 中调用。
 */
export function trackPageView(route: string, pageTitle?: string): void {
  track('web.page.view', {
    route,
    pageTitle,
    payload: { referrer: document.referrer || undefined },
  })
}

/**
 * 接口耗时追踪。在 request.ts 响应拦截器中调用。
 *
 * @param url 接口 URL
 * @param method HTTP 方法
 * @param status HTTP 状态码
 * @param durationMs 耗时（毫秒）
 * @param traceId 链路 ID
 * @param errorCode 错误码（失败时）
 */
export function trackApi(
  url: string,
  method: string,
  status: number,
  durationMs: number,
  traceId?: string,
  errorCode?: string
): void {
  // 不追踪埋点上报自身，避免循环
  if (url.includes('/client-events/batch')) return

  const isSuccess = status >= 200 && status < 400
  track('web.api.request', {
    traceId,
    result: isSuccess ? 'success' : 'failed',
    errorCode,
    durationMs,
    payload: { url, method, status },
  })

  // 慢接口告警（94 号文档：接口超过模块阈值记录 web.api.slow）
  if (isSuccess && durationMs > 2000) {
    track('web.api.slow', {
      traceId,
      durationMs,
      payload: { url, method, status },
    })
  }
}

/**
 * JS 错误追踪。在 window.onerror / unhandledrejection 中调用。
 */
export function trackError(error: Error | string, context?: { route?: string; component?: string }): void {
  const message = typeof error === 'string' ? error : error.message
  const stack = typeof error === 'object' && error.stack ? error.stack.split('\n')[0] : undefined

  track('web.js.error', {
    result: 'failed',
    errorCode: 'JS_ERROR',
    route: context?.route || getCurrentRoute(),
    payload: {
      message,
      component: context?.component,
      stackSummary: stack,
    },
  })
}

/**
 * 性能指标追踪。Web RUM 指标 FCP/LCP/CLS/INP/TTFB。
 */
export function trackPerformance(metric: string, value: number, rating?: string): void {
  track('web.rum.metric', {
    durationMs: Math.round(value),
    payload: { metric, rating, value },
  })

  // 94 号文档：首屏超过 3 秒记录 web.rum.lcp.slow
  if (metric === 'LCP' && value > 3000) {
    track('web.rum.lcp.slow', {
      durationMs: Math.round(value),
      payload: { metric, value },
    })
  }
}

// ==================== 批量上报 ====================

/** 启动定时刷新 */
function startFlushTimer(): void {
  if (flushTimer) clearInterval(flushTimer)
  flushTimer = setInterval(() => {
    if (eventQueue.length > 0) {
      void flush()
    }
  }, config.flushInterval)
}

/**
 * 刷新队列：批量上报到后端。
 * 失败时重试（最多 3 次，指数退避）。
 */
export async function flush(retryCount = 0): Promise<void> {
  if (eventQueue.length === 0) return

  const batch = eventQueue.splice(0, config.batchSize)
  const body = JSON.stringify({ events: batch })

  try {
    const response = await fetch(config.endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      credentials: 'include',
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    if (config.debug) {
      console.debug('[tracker] flush success', batch.length, 'events')
    }
  } catch (err) {
    // 失败重试：放回队列头部，最多 3 次
    if (retryCount < 3) {
      eventQueue.unshift(...batch)
      const delay = Math.min(1000 * Math.pow(2, retryCount), 8000)
      if (config.debug) {
        console.warn(`[tracker] flush failed, retry in ${delay}ms`, err)
      }
      setTimeout(() => void flush(retryCount + 1), delay)
    } else {
      if (config.debug) {
        console.warn('[tracker] flush giving up after 3 retries', batch.length, 'events')
      }
    }
  }
}

/**
 * 立即刷新队列（页面卸载时使用 sendBeacon 尽力上报）。
 */
export function flushBeacon(): void {
  if (eventQueue.length === 0) return

  const batch = eventQueue.splice(0, config.batchSize)
  const body = JSON.stringify({ events: batch })

  // 使用 sendBeacon 在页面卸载时尽力上报
  if (navigator.sendBeacon) {
    const blob = new Blob([body], { type: 'application/json' })
    navigator.sendBeacon(config.endpoint, blob)
  }
}

// ==================== 全局错误监听（自动初始化） ====================

let globalErrorInitialized = false

/**
 * 初始化全局错误监听。
 * 在 main.ts 中调用一次即可自动捕获 window.onerror 和 unhandledrejection。
 */
export function initGlobalErrorListener(): void {
  if (globalErrorInitialized) return
  globalErrorInitialized = true

  window.addEventListener('error', (event) => {
    trackError(event.error || event.message, {
      route: getCurrentRoute(),
      component: event.filename,
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    const error = reason instanceof Error ? reason : new Error(String(reason))
    trackError(error, { route: getCurrentRoute() })
  })

  // 页面卸载时尽力上报剩余事件
  window.addEventListener('beforeunload', () => {
    flushBeacon()
  })
}
