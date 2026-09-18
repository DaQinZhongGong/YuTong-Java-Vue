import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
// P2-K: 复用 web-admin 权威令牌源 (variables.css 浅色 + dark.css 暗色覆盖),
// web 此前仅引 design-systems 令牌, --yt-color-*/--yt-bg-* 等全未定义致自定义样式静默降级。
// 单源真理: 色彩/字体/间距/圆角/阴影/动效 + Element Plus 主题桥接只在 web-admin 维护, web 直接引用不复制。
import '../../web-admin/src/styles/variables.css'
import '../../web-admin/src/styles/dark.css'
import './styles/tokens.css'
import './styles/chat.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
