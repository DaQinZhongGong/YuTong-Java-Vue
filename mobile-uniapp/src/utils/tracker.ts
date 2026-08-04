/**
 * 端侧埋点 SDK (移动端)。设计来源: 94-端侧埋点与体验监控详设
 *
 * 适配 uniapp 环境，封装 track / trackPageView / trackApi / trackError 四个核心 API。
 * 具备采样率、批量上报、失败重试、脱敏管道、禁用开关和 debug 模式。
 *
 * 隐私约束（94 号文档第 96-98 行）：
 *  - 不得采集明文密码、token、完整手机号、完整身份证、文件原文、AI Prompt 敏感字段
 *  - 审核意见正文不得进入埋点，只上报 opinionLength
 *
 * 上报接口：POST /api/v1/client-events/batch（后端 ClientEventController）
 * 上报方式：uni.request，与移动端请求基础设施一致
 */

import { BASE_URL } from './request'

// ==================== 类型定义 ====================

export interface ClientEventPayload {
  eventId: string
  eventName: string
  occurredTime: string
  traceId?: string
  sessionId: string
  route: string
  pageTitle?: string
  platform: string
  appVersion: string
  bizType?: string
  bizIdHash?: string
  result?: string
  errorCode?: string
  durationMs?: number
  payload?: string
}

export interface TrackerConfig {
  sampleRate?: number
  batchSize?: number
  flushInterval?: number
  enabled?: boolean
  debug?: boolean
}

// ==================== 内部状态 ====================

const DEFAULT_CONFIG: Required<TrackerConfig> = {
  sampleRate: 1.0,
  batchSize: 20,
  flushInterval: 5000,
  enabled: true,
  debug: false,
}

let config: Required<TrackerConfig> = { ...DEFAULT_CONFIG }
let eventQueue: ClientEventPayload[] = []
let flushTimer: ReturnType<typeof setInterval> | null = null
let sessionId = generateSessionId()
let appVersion = '1.0.0'

// ==================== 工具函数 ====================

function generateEventId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 12)
}

function generateSessionId(): string {
  return 'sess_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

function hash(value: string | undefined | null): string | undefined {
  if (value == null || value === '') return undefined
  let h = 5381
  for (let i = 0; i < value.length; i++) {
    h = ((h << 5) + h) + value.charCodeAt(i)
    h = h & 0xffffffff
  }
  return 'h_' + (h >>> 0).toString(36)
}

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
    if (FORBIDDEN_KEYS.some(k => lowerKey.includes(k))) continue
    if (SENSITIVE_KEYS.some(k => lowerKey.includes(k))) { sanitized[key] = '***'; continue }
    if (PHONE_KEYS.some(k => lowerKey.includes(k)) && typeof value === 'string') {
      sanitized[key] = value.length > 4 ? '****' + value.slice(-4) : '****'; continue
    }
    if (IDCARD_KEYS.some(k => lowerKey.includes(k)) && typeof value === 'string' && value.length >= 8) {
      sanitized[key] = value.slice(0, 4) + '****' + value.slice(-4); continue
    }
    sanitized[key] = value
  }
  try { return JSON.stringify(sanitized) } catch { return undefined }
}

function getCurrentRoute(): string {
  try {
    const pages = getCurrentPages()
    const page = pages[pages.length - 1] as any
    if (page) {
      return '/' + page.route + (page.options ? '?' + Object.entries(page.options as Record<string, string>).map(([k, v]) => `${k}=${v}`).join('&') : '')
    }
  } catch { /* ignore */ }
  return ''
}

// ==================== 核心API ====================

export function initTracker(overrides?: TrackerConfig): void {
  config = { ...DEFAULT_CONFIG, ...overrides }
  if (config.debug) console.debug('[tracker] initialized', { config, sessionId })
  startFlushTimer()
}

export function setAppVersion(version: string): void {
  appVersion = version
}

export function track(
  eventName: string,
  options: {
    traceId?: string
    bizType?: string
    bizId?: string
    result?: string
    errorCode?: string
    durationMs?: number
    route?: string
    payload?: Record<string, unknown>
  } = {}
): void {
  if (!config.enabled || !eventName) return
  if (Math.random() > config.sampleRate) return

  const event: ClientEventPayload = {
    eventId: generateEventId(),
    eventName,
    occurredTime: new Date().toISOString(),
    traceId: options.traceId,
    sessionId,
    route: options.route || getCurrentRoute(),
    platform: 'h5',
    appVersion,
    bizType: options.bizType,
    bizIdHash: hash(options.bizId),
    result: options.result,
    errorCode: options.errorCode,
    durationMs: options.durationMs,
    payload: sanitizePayload(options.payload),
  }

  eventQueue.push(event)
  if (config.debug) console.debug('[tracker] track', eventName, event)
  if (eventQueue.length >= config.batchSize) void flush()
}

export function trackPageView(route: string, pageTitle?: string): void {
  track('mobile.page.view', { route, payload: { pageTitle } })
}

export function trackApi(
  url: string,
  method: string,
  status: number,
  durationMs: number,
  traceId?: string,
  errorCode?: string
): void {
  if (url.includes('/client-events/batch')) return
  const isSuccess = status >= 200 && status < 400
  track('mobile.api.request', {
    traceId, result: isSuccess ? 'success' : 'failed', errorCode, durationMs,
    payload: { url, method, status },
  })
}

export function trackError(error: Error | string, context?: { route?: string }): void {
  const message = typeof error === 'string' ? error : error.message
  track('mobile.js.error', {
    result: 'failed', errorCode: 'JS_ERROR',
    route: context?.route || getCurrentRoute(),
    payload: { message },
  })
}

// ==================== 批量上报 ====================

function startFlushTimer(): void {
  if (flushTimer) clearInterval(flushTimer)
  flushTimer = setInterval(() => {
    if (eventQueue.length > 0) void flush()
  }, config.flushInterval)
}

export function flush(retryCount = 0): Promise<void> {
  if (eventQueue.length === 0) return Promise.resolve()
  const batch = eventQueue.splice(0, config.batchSize)
  const body = JSON.stringify({ events: batch })

  return new Promise<void>((resolve) => {
    uni.request({
      url: BASE_URL + '/client-events/batch',
      method: 'POST',
      data: body,
      header: { 'Content-Type': 'application/json' },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          if (config.debug) console.debug('[tracker] flush success', batch.length)
        } else {
          handleFlushError(batch, retryCount, new Error(`HTTP ${res.statusCode}`))
        }
        resolve()
      },
      fail: (err) => {
        handleFlushError(batch, retryCount, new Error(err.errMsg || 'flush failed'))
        resolve()
      },
    })
  })
}

function handleFlushError(batch: ClientEventPayload[], retryCount: number, err: Error): void {
  if (retryCount < 3) {
    eventQueue.unshift(...batch)
    const delay = Math.min(1000 * Math.pow(2, retryCount), 8000)
    if (config.debug) console.warn(`[tracker] flush failed, retry in ${delay}ms`, err)
    setTimeout(() => void flush(retryCount + 1), delay)
  } else {
    if (config.debug) console.warn('[tracker] flush giving up after 3 retries', batch.length)
  }
}

// ==================== 全局错误监听 ====================

let globalErrorInitialized = false

export function initGlobalErrorListener(): void {
  if (globalErrorInitialized) return
  globalErrorInitialized = true

  // uniapp 全局错误监听
  if (typeof uni !== 'undefined' && uni.onError) {
    uni.onError((errMsg) => {
      trackError(errMsg, { route: getCurrentRoute() })
    })
  }
  if (typeof uni !== 'undefined' && uni.onUnhandledRejection) {
    uni.onUnhandledRejection((res) => {
      const reason = res.reason as unknown
      const error = typeof reason === 'object' && reason instanceof Error
        ? reason
        : new Error(String(reason))
      trackError(error, { route: getCurrentRoute() })
    })
  }
}
