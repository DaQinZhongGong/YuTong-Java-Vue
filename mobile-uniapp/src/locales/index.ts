import { createI18n } from 'vue-i18n';
import zhCN from './zh-CN';
import enUS from './en-US';

/**
 * 移动端 i18n 配置。设计来源: web-admin/src/locales/index.ts。
 *
 * - 默认语言 zh-CN，可切换 en-US。
 * - 语言来源优先级: uni.getStorageSync('lang') > VITE_I18N_LOCALE 环境变量 > zh-CN。
 * - legacy: false 使用 Composition API 模式，兼容 Vue 3 + uniapp createSSRApp。
 * - key 命名空间与后端 MessageSource 对齐，request.ts 中 i18n.global.t(messageKey) 可直接解析。
 */

export type AppLocale = 'zh-CN' | 'en-US';

export const SUPPORTED_LOCALES: AppLocale[] = ['zh-CN', 'en-US'];

function detectLocale(): AppLocale {
  // 1) 用户在 storage 中显式选择的语言
  try {
    const stored = uni.getStorageSync('lang');
    if (typeof stored === 'string' && SUPPORTED_LOCALES.indexOf(stored as AppLocale) !== -1) {
      return stored as AppLocale;
    }
  } catch {
    // storage 不可用时忽略，回退到环境变量
  }
  // 2) 构建期注入的环境变量（.env.development / .env.production）
  const envLang = (import.meta.env as unknown as Record<string, string | undefined>).VITE_I18N_LOCALE;
  if (typeof envLang === 'string' && SUPPORTED_LOCALES.indexOf(envLang as AppLocale) !== -1) {
    return envLang as AppLocale;
  }
  // 3) 默认中文
  return 'zh-CN';
}

const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
});

export default i18n;