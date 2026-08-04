import axios from 'axios'
import type {
  AxiosInstance,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios'
import { ElMessage } from 'element-plus'
import {
  getToken,
  setToken,
  removeToken,
  getRefreshToken,
  setRefreshToken,
  removeRefreshToken,
  getMockUser,
} from '@/utils/auth'
import i18n from '@/locales'
import { trackApi } from '@/utils/tracker'
import type { Result } from './types'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// GA2-18: 扩展 InternalAxiosRequestConfig 存储请求开始时间 (用于 trackApi 耗时计算)
declare module 'axios' {
  interface InternalAxiosRequestConfig {
    metadata?: { startTime?: number }
  }
}

/** 优先使用 messageKey 从前端 i18n 解析本地化消息，兜底使用后端返回的 message。 */
function resolveErrorMessage(res: Result<unknown>): string {
  if (res.messageKey) {
    const local = i18n.global.t(res.messageKey)
    // t() 在 key 未找到时返回 key 本身，此时回退到后端 message
    if (local && local !== res.messageKey) {
      return local
    }
  }
  return res.message || i18n.global.t('common.error.internal')
}

/**
 * GA2-55: 生成 32 位 hex traceId (同一请求复用)。
 * 优先使用 crypto.getRandomValues, 退化到 Math.random 保证兼容性。
 */
function generateTraceId(): string {
  const bytes = new Uint8Array(16)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < 16; i++) {
      bytes[i] = Math.floor(Math.random() * 256)
    }
  }
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * GA2-55: 从 localStorage 读取 tenantId (X-Tenant-Id 请求头注入)。
 * 后端通过 X-Tenant-Id 头识别租户上下文, 不依赖 URL 路径。
 */
function getTenantId(): string | null {
  try {
    return localStorage.getItem('yutong_tenant_id')
  } catch {
    return null
  }
}

/**
 * GA2-55: 403 无权限事件派发。
 * 业务页面可监听 'yutong:no-permission' 事件展示 PageState(no-permission)。
 * 不在此处重复弹 ElMessage, 避免与页面级状态展示冲突。
 */
function dispatchNoPermission(permissionCode?: string): void {
  window.dispatchEvent(
    new CustomEvent('yutong:no-permission', {
      detail: { permissionCode: permissionCode || '' },
    })
  )
}

/**
 * GA2-55: 409 业务冲突事件派发。
 * 业务页面可监听 'yutong:business-conflict' 事件刷新详情或提示用户。
 */
function dispatchBusinessConflict(detail: {
  code?: string
  message?: string
  messageKey?: string
}): void {
  window.dispatchEvent(
    new CustomEvent('yutong:business-conflict', { detail })
  )
}

// ============================================================================
// 21-安全合规: refresh token 轮换 - access token 401 时自动用 refresh token 换新
// 设计来源: 21-安全合规详设「access=15min + refresh=14d，refresh token 每次使用必须轮换」
// 策略:
//   1. 收到 401 时，若本地存在 refresh token 且当前请求不是 /auth/refresh 本身，
//      则调用 POST /auth/refresh 换取新 token pair (access + refresh)，重试原请求。
//   2. 并发 401 请求复用同一次 refresh 调用 (isRefreshing 锁 + 等待队列)。
//   3. refresh 失败 (refresh token 过期/被轮换/被撤销) → 清除本地凭据并跳转登录页。
//   4. refresh 请求使用裸 axios (绕过 service 拦截器)，避免 401 递归。
// ============================================================================

let isRefreshing = false
let refreshWaiters: Array<{
  resolve: (token: string) => void
  reject: (err: unknown) => void
}> = []

/** 清除本地凭据并跳转登录页 (refresh 失败或无 refresh token 时调用)。 */
function clearAuthAndRedirect(): void {
  removeToken()
  removeRefreshToken()
  isRefreshing = false
  refreshWaiters.forEach((w) => w.reject(new Error('refresh failed')))
  refreshWaiters = []
  ElMessage.error(i18n.global.t('auth.error.tokenExpired'))
  window.location.href = '/login'
}

/**
 * 触发 refresh token 轮换。并发调用复用同一个 Promise。
 *
 * @returns 新的 access token；失败时 reject 并触发跳转登录。
 */
function triggerTokenRefresh(): Promise<string> {
  const oldRefreshToken = getRefreshToken()
  if (!oldRefreshToken) {
    return Promise.reject(new Error('no refresh token'))
  }
  if (isRefreshing) {
    // 已有 refresh 进行中，排队等待结果
    return new Promise<string>((resolve, reject) => {
      refreshWaiters.push({ resolve, reject })
    })
  }
  isRefreshing = true
  // 使用裸 axios 绕过 service 拦截器，避免 /auth/refresh 的 401 触发递归 refresh
  return axios
    .post<Result<{ token: string; refreshToken: string }>>(
      `${import.meta.env.VITE_API_BASE_URL}/auth/refresh`,
      { refreshToken: oldRefreshToken },
      { headers: { 'Content-Type': 'application/json' }, timeout: 15000 }
    )
    .then((resp) => {
      const data = resp.data?.data
      if (!data || !data.token) {
        throw new Error('refresh response missing token')
      }
      setToken(data.token)
      if (data.refreshToken) {
        setRefreshToken(data.refreshToken)
      }
      // 通知所有等待中的请求使用新 token 重试
      const newToken = data.token
      refreshWaiters.forEach((w) => w.resolve(newToken))
      refreshWaiters = []
      isRefreshing = false
      return newToken
    })
    .catch((err) => {
      clearAuthAndRedirect()
      throw err
    })
}

/**
 * 处理 401: 尝试 refresh token 换新后重试原请求。
 *
 * @param originalConfig 原请求配置 (用于重试)
 * @returns 重试请求的 Promise；无法刷新时 reject。
 */
function handle401AndRetry(originalConfig: InternalAxiosRequestConfig | undefined): Promise<unknown> {
  // /auth/refresh 自身的 401 不再重试，直接跳登录
  const url = originalConfig?.url || ''
  if (url.includes('/auth/refresh')) {
    clearAuthAndRedirect()
    return Promise.reject(new Error('refresh token invalid'))
  }
  if (!getRefreshToken()) {
    clearAuthAndRedirect()
    return Promise.reject(new Error('no refresh token'))
  }
  return triggerTokenRefresh().then((newToken) => {
    if (!originalConfig) {
      return null
    }
    originalConfig.headers = originalConfig.headers || {}
    originalConfig.headers.Authorization = `Bearer ${newToken}`
    return service.request(originalConfig)
  })
}

// 请求拦截器: 自动携带 Authorization、Accept-Language 和 X-Mock-User (dev/local)
// GA2-16: X-Mock-User 由 localStorage 注入, 用于切换 4 类 Mock 用户验证权限可见性矩阵。
// 生产构建不读取本字段, 后端 MockAuthGuardConfig 会拒绝 dev 外 profile 启用 Mock。
// GA2-18: 记录请求开始时间, 用于 trackApi 耗时计算 (设计来源 94-端侧埋点与体验监控详设)
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    config.metadata = { startTime: Date.now() }
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    config.headers['Accept-Language'] = i18n.global.locale.value
    // GA2-55: 注入 X-Tenant-Id (从 localStorage yutong_tenant_id 读取)
    const tenantId = getTenantId()
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId
    }
    // GA2-55: 注入 X-Trace-Id (32 位 hex, 同一请求复用, 便于后端日志关联)
    config.headers['X-Trace-Id'] = generateTraceId()
    // GA2-16: dev 模式注入 Mock 用户头
    if (import.meta.env.DEV) {
      const mockUser = getMockUser()
      if (mockUser) {
        config.headers['X-Mock-User'] = mockUser
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器: 解包 Result<T>, 失败时统一报错
// GA2-18: 自动上报接口耗时事件 trackApi (设计来源 94-端侧埋点与体验监控详设)
service.interceptors.response.use(
  (response: AxiosResponse<Result<unknown>>) => {
    const startTime = response.config?.metadata?.startTime
    if (startTime) {
      trackApi(response.config.url || '', response.config.method || 'GET',
        response.status, Date.now() - startTime, response.data?.traceId,
        response.data?.code != null ? String(response.data.code) : undefined)
    }
    const res = response.data
    // 兼容非标准 Result 结构(如直接返回数据)
    if (res === null || typeof res !== 'object' || !('code' in res)) {
      return res as unknown as AxiosResponse
    }
    // 后端 Result.code="0" 表示成功；兼容历史 code=200/"200"
    const code = String(res.code)
    const isSuccess = code === '0' || code === '200'
    if (!isSuccess) {
      // 21-安全合规: 401 先尝试 refresh token 换新后重试，失败再跳登录 (不弹 ElMessage)
      if (code === '401') {
        return handle401AndRetry(response.config) as unknown as AxiosResponse
      }
      // GA2-55: 403 触发 no-permission 事件, 不重复弹 ElMessage
      if (code === '403') {
        const permissionCode =
          (res.data as { permissionCode?: string } | undefined)?.permissionCode ||
          res.messageKey ||
          ''
        dispatchNoPermission(permissionCode)
        return Promise.reject(new Error(res.message || 'No Permission'))
      }
      // GA2-55: 409 触发 business-conflict 事件, 业务页面可监听刷新详情
      if (code === '409') {
        dispatchBusinessConflict({
          code,
          message: res.message,
          messageKey: res.messageKey,
        })
        ElMessage.error(resolveErrorMessage(res))
        return Promise.reject(new Error(res.message || 'Business Conflict'))
      }
      ElMessage.error(resolveErrorMessage(res))
      return Promise.reject(new Error(res.message || 'Error'))
    }
    return res.data as unknown as AxiosResponse
  },
  (error) => {
    const status = error?.response?.status
    const res = error?.response?.data as Result<unknown> | undefined
    const startTime = error?.config?.metadata?.startTime
    if (startTime) {
      trackApi(error.config?.url || '', error.config?.method || 'GET',
        status || 0, Date.now() - startTime, res?.traceId,
        res?.code != null ? String(res.code) : undefined)
    }
    const message = res ? resolveErrorMessage(res) : (error?.message || i18n.global.t('common.error.internal'))
    if (status === 401) {
      // 21-安全合规: 尝试 refresh token 换新后重试原请求，失败则跳登录 (clearAuthAndRedirect 内弹提示)
      return handle401AndRetry(error.config)
    } else if (status === 403) {
      // GA2-55: HTTP 403 触发 no-permission 事件, 不重复弹 ElMessage
      const permissionCode =
        (res?.data as { permissionCode?: string } | undefined)?.permissionCode ||
        res?.messageKey ||
        ''
      dispatchNoPermission(permissionCode)
    } else if (status === 409) {
      // GA2-55: HTTP 409 触发 business-conflict 事件, 业务页面可监听刷新详情
      dispatchBusinessConflict({
        code: res?.code != null ? String(res.code) : '409',
        message: res?.message,
        messageKey: res?.messageKey,
      })
      ElMessage.error(message)
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default service
