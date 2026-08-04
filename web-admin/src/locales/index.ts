import { createI18n } from 'vue-i18n'

/**
 * i18n 配置。设计来源: 43-国际化与无障碍设计、contracts/registries/i18n-catalog.yaml
 * 从 zh-CN/ 和 en-US/ 目录自动加载所有域 JSON 文件并合并。
 * 295 key 中英双语覆盖，与后端 MessageSource 共享同一 key 命名空间。
 */

const zhCNModules = import.meta.glob('./zh-CN/*.json', { eager: true })
const enUSModules = import.meta.glob('./en-US/*.json', { eager: true })

function mergeMessages(modules: Record<string, any>): Record<string, string> {
  const merged: Record<string, string> = {}
  for (const [, module] of Object.entries(modules)) {
    Object.assign(merged, (module as any).default || module)
  }
  return merged
}

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': mergeMessages(zhCNModules),
    'en-US': mergeMessages(enUSModules),
  },
})

export default i18n
