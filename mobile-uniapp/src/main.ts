import { createSSRApp } from 'vue';
import App from './App.vue';
import { initTracker, initGlobalErrorListener } from './utils/tracker';
import i18n from './locales';

export function createApp() {
  const app = createSSRApp(App);

  // 注册 i18n（vue-i18n@9，Composition 模式），供 request.ts 的 messageKey 解析与页面文案使用
  app.use(i18n);

  // GA2-18: 初始化端侧埋点 SDK (设计来源 94-端侧埋点与体验监控详设)
  // 初始化配置 + 全局错误监听 (uni.onError + uni.onUnhandledRejection)
  initTracker();
  initGlobalErrorListener();

  return {
    app,
  };
}