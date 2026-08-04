import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import i18n from './locales'
import permissionDirective from './directives/permission'
import { initTracker, initGlobalErrorListener } from './utils/tracker'
// GA2-29: 设计系统样式引入顺序: variables(token) → dark(主题覆盖) → base(组件规范)
// 设计来源: 50-设计系统与视觉规范详设
import './styles/variables.css'
import './styles/dark.css'
import './styles/base.css'

const app = createApp(App)

app.use(createPinia())
app.use(i18n)
app.use(router)
app.use(ElementPlus)

// GA2-16: 注册 v-permission 指令 (设计来源 96-端侧权限可见性矩阵详设)
// 用法: v-permission="'biz:request:add'" / v-permission.any="[...]" / v-permission.all="[...]"
app.directive('permission', permissionDirective)

// GA2-18: 初始化端侧埋点 SDK (设计来源 94-端侧埋点与体验监控详设)
// 初始化配置 + 全局错误监听 (window.onerror + unhandledrejection + beforeunload sendBeacon)
initTracker()
initGlobalErrorListener()

app.mount('#app')
