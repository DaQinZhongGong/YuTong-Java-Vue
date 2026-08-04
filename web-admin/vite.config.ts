import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // GA2-L179: 端口从 5173 改为 5183，避免与其他项目 5173 端口冲突
    port: 20050,
    // host: '0.0.0.0' 等价于 host: true，支持外部 IP 访问（不仅是 localhost）
    host: '0.0.0.0',
    proxy: {
      // GA2-L179: Docker 后端映射到宿主机 8092 端口（deploy/.env BACKEND_PORT=8092）
      '/api': {
        target: 'http://localhost:20010',
        changeOrigin: true,
      },
    },
  },
})
