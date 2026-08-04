/**
 * 简体中文翻译（移动端）。
 * 设计来源: web-admin/src/locales/zh-CN，与后端 MessageSource 共享同一 key 命名空间。
 * 采用嵌套对象结构，vue-i18n 按点号路径解析，如 t('auth.error.tokenExpired')。
 */
export default {
  auth: {
    error: {
      tokenExpired: '登录已过期，请重新登录',
      disabled: '账号已被禁用',
      tooFrequent: '登录尝试过于频繁',
      loginFailed: '账号或凭证无效',
      permissionDenied: '缺少所需权限',
      unauthenticated: '请先登录',
    },
  },
  common: {
    error: {
      internal: '请求失败',
      network: '网络请求失败',
      notFound: '请求的资源不存在',
      rateLimited: '请求过于频繁，请稍后重试',
    },
  },
};