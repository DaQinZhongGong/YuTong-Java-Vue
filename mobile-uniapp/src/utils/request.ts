import type { Result } from '@/api/types';
import { trackApi } from './tracker';
import i18n from '@/locales';

/**
 * 请求错误类型，携带 HTTP statusCode 与后端业务 code，
 * 供调用方按状态码/错误码分支处理（如 P1-2 审核 409 冲突）。
 */
export interface RequestError extends Error {
  /** HTTP 状态码（网络错误时为 0）。 */
  statusCode?: number;
  /** 后端 Result.code（字符串错误码，如 "BIZ-409002"）。 */
  code?: string;
  /** 原始响应体，供需要时读取 messageKey 等字段。 */
  body?: unknown;
}

function buildError(message: string, statusCode?: number, body?: unknown): RequestError {
  const err = new Error(message) as RequestError;
  err.statusCode = statusCode;
  err.body = body;
  if (body && typeof body === 'object' && typeof (body as Record<string, unknown>).code === 'string') {
    err.code = (body as Record<string, unknown>).code as string;
  }
  return err;
}

/**
 * 解析后端错误响应的展示文案。
 *
 * 优先级：body.messageKey（经 vue-i18n 翻译）> body.message > fallback。
 *
 * messageKey 通过 i18n.global.t(messageKey) 翻译，与后端 MessageSource 共享命名空间；
 * vue-i18n 找不到 key 时返回 key 本身，此时回退到 body.message，再回退到 messageKey。
 */
function resolveErrorMessage(body: unknown, fallback: string): string {
  if (body && typeof body === 'object') {
    const record = body as Record<string, unknown>;
    const messageKey = typeof record.messageKey === 'string' ? record.messageKey : '';
    const message = typeof record.message === 'string' ? record.message : '';
    if (messageKey) {
      // i18n 已接入：优先用 vue-i18n 翻译 messageKey（与后端 MessageSource 共享命名空间）。
      // vue-i18n 找不到 key 时返回 key 本身，此时回退到后端 message，再回退到 messageKey 兜底。
      const translated = i18n.global.t(messageKey);
      if (translated && translated !== messageKey) {
        return translated;
      }
      return message || messageKey;
    }
    if (message) {
      return message;
    }
  }
  return fallback;
}

/**
 * Base URL for the backend API.
 * 优先读取构建期注入的环境变量 VITE_API_BASE_URL（.env.development / .env.production），
 * 缺省时回退到本地后端服务地址，保证未配置环境时仍可运行。
 *
 * 注意：必须直接以 `import.meta.env.VITE_API_BASE_URL` 形式访问（不可先存入中间变量），
 * 否则 Vite 无法在构建期静态替换该成员表达式，导致生产构建拿不到环境变量。
 */
// GA2-L192: 后端 Docker 映射到宿主机 20010 端口（与 deploy/.env BACKEND_PORT=20010 对齐）
// H5 dev 走相对路径 /api/v1 由 vite proxy 转发 (manifest.json h5.devServer.proxy -> localhost:20010)，避免浏览器 CORS；
// 小程序/Node(vitest) 无 window，默认直连绝对地址；两者均可被 VITE_API_BASE_URL 覆盖。
// 注: 不用 uni 条件编译注释 (#ifdef)，vitest 直接编译 TS 不识别该语法。
const isH5Runtime = typeof window !== 'undefined';
export const BASE_URL =
  (import.meta.env as unknown as Record<string, string | undefined>).VITE_API_BASE_URL ||
  (isH5Runtime ? '/api/v1' : 'http://localhost:20010/api/v1');

/** Storage keys for auth persistence. */
export const TOKEN_STORAGE_KEY = 'yutong_token';
export const USER_STORAGE_KEY = 'yutong_user';
/** 21-安全合规: refresh token 存储键 (14d, 轮换) */
export const REFRESH_TOKEN_STORAGE_KEY = 'yutong_refresh_token';
/** Storage key for the route to return to after re-login (token expired). */
export const REDIRECT_STORAGE_KEY = 'yutong_redirect';

export interface RequestOptions {
  url: string;
  method?: UniApp.RequestOptions['method'];
  data?: unknown;
  header?: Record<string, string>;
  /** Whether to attach the Authorization header. Defaults to true. */
  needAuth?: boolean;
}

/**
 * Generate a simple UUID v4 string for request tracing.
 * Falls back to a timestamp-based id when crypto is unavailable.
 */
function generateTraceId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
  } catch {
    // ignore and fall through
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// ============================================================================
// 21-安全合规: refresh token 轮换 - access token 401 时自动用 refresh token 换新
// 设计来源: 21-安全合规详设「access=15min + refresh=14d，refresh token 每次使用必须轮换」
// 策略:
//   1. 收到 401 时，若本地存在 refresh token 且当前请求不是 /auth/refresh 本身，
//      则调用 POST /auth/refresh 换取新 token pair，重试原请求。
//   2. 并发 401 请求复用同一次 refresh 调用 (isRefreshing 锁 + 等待队列)。
//   3. refresh 失败 → 清除本地凭据并跳转登录页。
//   4. refresh 请求使用裸 uni.request (绕过 request 拦截)，避免 401 递归。
// ============================================================================

let isRefreshing = false;
let refreshWaiters: Array<{
  resolve: (token: string) => void;
  reject: (err: unknown) => void;
}> = [];

/** 清除本地凭据并跳转登录页 (refresh 失败或无 refresh token 时调用)。 */
function clearAuthAndRedirectLogin(): void {
  uni.removeStorageSync(TOKEN_STORAGE_KEY);
  uni.removeStorageSync(REFRESH_TOKEN_STORAGE_KEY);
  uni.removeStorageSync(USER_STORAGE_KEY);
  isRefreshing = false;
  refreshWaiters.forEach((w) => w.reject(new Error('refresh failed')));
  refreshWaiters = [];
  uni.reLaunch({ url: '/pages/login/login' });
}

/**
 * 触发 refresh token 轮换。并发调用复用同一个 Promise。
 *
 * @returns 新的 access token；失败时 reject 并触发跳转登录。
 */
function triggerTokenRefresh(): Promise<string> {
  const oldRefreshToken = uni.getStorageSync(REFRESH_TOKEN_STORAGE_KEY);
  if (!oldRefreshToken) {
    return Promise.reject(new Error('no refresh token'));
  }
  if (isRefreshing) {
    return new Promise<string>((resolve, reject) => {
      refreshWaiters.push({ resolve, reject });
    });
  }
  isRefreshing = true;
  return new Promise<string>((resolve, reject) => {
    uni.request({
      url: BASE_URL + '/auth/refresh',
      method: 'POST',
      data: { refreshToken: oldRefreshToken },
      header: { 'Content-Type': 'application/json' },
      success: (res) => {
        const body = res.data as Result<{ token: string; refreshToken: string }> | undefined;
        const data = body?.data;
        if (res.statusCode !== 200 || !data || !data.token) {
          clearAuthAndRedirectLogin();
          reject(new Error('refresh failed'));
          return;
        }
        uni.setStorageSync(TOKEN_STORAGE_KEY, data.token);
        if (data.refreshToken) {
          uni.setStorageSync(REFRESH_TOKEN_STORAGE_KEY, data.refreshToken);
        }
        const newToken = data.token;
        refreshWaiters.forEach((w) => w.resolve(newToken));
        refreshWaiters = [];
        isRefreshing = false;
        resolve(newToken);
      },
      fail: (err) => {
        clearAuthAndRedirectLogin();
        reject(new Error(err.errMsg || 'refresh network error'));
      },
    });
  });
}

/**
 * 处理 401: 尝试 refresh token 换新后重试原请求。
 *
 * @param options 原请求选项 (用于重试)
 * @returns 重试请求的 Promise；无法刷新时 reject。
 */
function handle401AndRetry<T>(options: RequestOptions): Promise<T> {
  // /auth/refresh 自身的 401 不再重试，直接跳登录
  if (options.url.includes('/auth/refresh')) {
    clearAuthAndRedirectLogin();
    return Promise.reject(new Error('refresh token invalid'));
  }
  if (!uni.getStorageSync(REFRESH_TOKEN_STORAGE_KEY)) {
    clearAuthAndRedirectLogin();
    return Promise.reject(new Error('no refresh token'));
  }
  return triggerTokenRefresh().then(() => request<T>(options));
}

/**
 * Core request wrapper around uni.request.
 *
 * - Prepends BASE_URL to the given url.
 * - Attaches a `Bearer <token>` Authorization header when needAuth is true.
 * - Adds `Accept-Language: zh-CN` and `X-Trace-Id` headers for tracing.
 * - Unwraps the Result<T> envelope and resolves with its `data` field.
 * - On HTTP 401 it clears auth and redirects to the login page.
 */
export function request<T = unknown>(options: RequestOptions): Promise<T> {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    needAuth = true,
  } = options;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept-Language': 'zh-CN',
    'X-Trace-Id': generateTraceId(),
    ...header,
  };

  if (needAuth) {
    const token = uni.getStorageSync(TOKEN_STORAGE_KEY);
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }

  // P1-5: 请求耗时埋点（成功/失败均记录）
  const startTime = Date.now();
  const traceId = headers['X-Trace-Id'];

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data: data as UniApp.RequestOptions['data'],
      header: headers,
      success: (res) => {
        const statusCode = res.statusCode;
        const body = res.data as Result<T>;
        // P1-5: 记录 API 请求埋点（含非 2xx 错误响应）
        trackApi(url, method, statusCode, Date.now() - startTime, traceId);

        if (statusCode === 401) {
          // 当前页面栈顶路由（用于判断是否在登录页 & 捕获回跳目标）
          let cur: any = null;
          try {
            const pages = getCurrentPages();
            cur = pages[pages.length - 1] as any;
          } catch {
            // ignore
          }
          const onLoginPage = !!cur && cur.route === 'pages/login/login';
          // 登录页自身 401（账号/密码错误等）不触发 refresh/跳转，
          // 避免重载登录页丢失表单与提示，交由调用方按 statusCode 分类提示。
          if (onLoginPage) {
            reject(buildError('登录已过期，请重新登录', statusCode, body));
            return;
          }
          // 捕获回跳目标，refresh 失败跳登录后用于回跳
          try {
            if (cur && cur.route) {
              const opts = cur.options || cur.$page?.options || {};
              const query = Object.entries(opts as Record<string, string>)
                .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
                .join('&');
              const redirect = '/' + cur.route + (query ? '?' + query : '');
              uni.setStorageSync(REDIRECT_STORAGE_KEY, redirect);
            }
          } catch {
            // ignore redirect capture errors
          }
          // 21-安全合规: 尝试 refresh token 换新后重试原请求，失败则跳登录
          handle401AndRetry<T>(options).then(resolve).catch((err: any) => {
            reject(buildError(err?.message || '登录已过期，请重新登录', statusCode, body));
          });
          return;
        }

        if (statusCode < 200 || statusCode >= 300) {
          // P1-2: 携带 statusCode / code 供调用方按状态码分支处理
          const msg = resolveErrorMessage(body, `请求失败：HTTP ${statusCode}`);
          reject(buildError(msg, statusCode, body));
          return;
        }

        // Unwrap the Result<T> envelope if present.
        if (body && typeof body === 'object' && 'data' in body) {
          const code = (body as Result<T>).code;
          const ok =
            (typeof code === 'number' && (code === 200 || code === 0)) ||
            (body as Result<T>).success === true ||
            typeof (body as Result<T>).success === 'undefined';

          if (!ok) {
            reject(buildError(resolveErrorMessage(body, '请求失败'), statusCode, body));
            return;
          }
          resolve((body as Result<T>).data);
          return;
        }

        // Response is not wrapped, return as-is.
        resolve(body as unknown as T);
      },
      fail: (err) => {
        // P1-5: 网络错误埋点（statusCode 记 0）
        trackApi(url, method, 0, Date.now() - startTime, traceId, err.errMsg || 'network_error');
        reject(buildError(err.errMsg || '网络请求失败', 0));
      },
    });
  });
}

export function get<T = unknown>(
  url: string,
  data?: unknown,
  header?: Record<string, string>,
): Promise<T> {
  return request<T>({ url, method: 'GET', data, header });
}

export function post<T = unknown>(
  url: string,
  data?: unknown,
  header?: Record<string, string>,
): Promise<T> {
  return request<T>({ url, method: 'POST', data, header });
}

export function put<T = unknown>(
  url: string,
  data?: unknown,
  header?: Record<string, string>,
): Promise<T> {
  return request<T>({ url, method: 'PUT', data, header });
}

export function del<T = unknown>(
  url: string,
  data?: unknown,
  header?: Record<string, string>,
): Promise<T> {
  return request<T>({ url, method: 'DELETE', data, header });
}
