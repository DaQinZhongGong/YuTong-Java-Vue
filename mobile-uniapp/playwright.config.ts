import { defineConfig, devices } from '@playwright/test'

/**
 * Mobile UniApp H5 层冒烟 E2E 套件配置。
 *
 * 设计来源:
 *  - deploy/port-mapping.md (20060 e2e test backend 端口规划)
 *  - mobile-uniapp/src/pages.json (tabBar 路由: 工作台/待办/我的)
 *  - mobile-uniapp/src/pages/login/login.vue (登录表单)
 *
 * 关键决策:
 *  1. dev:h5 以 --port 20060 启动, 对齐 deploy/port-mapping.md 测试端口段。
 *  2. 仅启用 chromium 项目, 与 web-admin 策略一致, 避免额外浏览器下载开销。
 *  3. webServer reuseExistingServer=true, 便于本地调试时复用已启动的 dev server。
 *  4. 降级策略: 若 dev:h5 无法绑定 20060 端口, 可不实际运行 E2E,
 *     仅保证测试文件类型检查通过 (与 web-admin 上轮策略一致)。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 30_000,
  expect: { timeout: 5_000 },

  use: {
    baseURL: 'http://localhost:20060',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 10_000,
    navigationTimeout: 15_000,
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  webServer: {
    command: 'npm run dev:h5 -- --port 20060',
    url: 'http://localhost:20060',
    reuseExistingServer: true,
    timeout: 60_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
})
