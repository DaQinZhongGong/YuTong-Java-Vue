import { defineConfig, devices } from '@playwright/test'

/**
 * GA2-L183: Web Playwright E2E 套件配置。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节 TC-E2E-WEB-001~004)
 *  - 58-后端API逐接口任务清单 (CT-createCustomer 验收标准)
 *
 * 关键决策:
 *  1. dev server 以 `--mode e2e` 启动, 加载 `.env.e2e` 覆盖 VITE_API_BASE_URL 为 `/api/v1`,
 *     走 vite proxy (vite.config.ts: /api -> http://localhost:20010) 与 Docker 后端同源通信。
 *     端口 20050 对齐 deploy/port-mapping.md 规划 (20000-20099 段)。
 *  2. 仅启用 chromium 项目, 避免额外浏览器下载开销; firefox 可按需放开。
 *  3. webServer reuseExistingServer=true, 便于本地调试时复用已启动的 dev server。
 */
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 30_000,
  expect: { timeout: 5_000 },

  use: {
    baseURL: 'http://localhost:20051',
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
    command: 'npx vite --mode e2e --force',
    url: 'http://localhost:20051',
    reuseExistingServer: true,
    timeout: 60_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
})