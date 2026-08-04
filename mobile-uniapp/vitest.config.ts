import { defineConfig } from 'vitest/config';
import { fileURLToPath, URL } from 'node:url';

/**
 * 独立的 vitest 配置。
 *
 * 注意：不加载 @dcloudio/vite-plugin-uni，避免 uniapp 运行时在测试环境产生副作用。
 * 仅提供 utils 单测所需：@ 路径别名、node 环境、uni.* 存储桩（见 vitest.setup.ts）。
 */
export default defineConfig({
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'node',
    globals: true,
    include: ['src/**/__tests__/**/*.test.ts'],
    setupFiles: ['./vitest.setup.ts'],
  },
});
