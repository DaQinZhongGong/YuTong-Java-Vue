/**
 * GA2-L183: 平台监控域 (Monitor) 核心 GA 路由 E2E 测试。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节: /monitor/health + /monitor/cache 路由覆盖)
 *  - 91-Web基础后台逐页交互详设「服务健康页/缓存概览页」
 *  - web-admin/src/views/system/MonitorHealth.vue (服务健康页: banner-card 总体状态 + 组件卡片网格 + 刷新按钮)
 *  - web-admin/src/views/system/MonitorCache.vue (缓存概览页: overview-card 全局命中率 + el-table + 刷新按钮)
 *
 * 覆盖场景 (2 条路由):
 *  - /monitor/health: 健康检查页面可见 + 健康状态卡片可见
 *  - /monitor/cache: 缓存监控页面可见 + 缓存信息可见
 *
 * 前置条件:
 *  - admin_mock 用户具备 monitor:health:view / monitor:cache:view 权限
 *  - dev/local profile 下 MockAuthAdapter 注入 X-Mock-User: admin_demo
 *  - 后端 /api/v1/monitor/health 与 /api/v1/monitor/cache 在 dev 模式下返回 mock 概览数据;
 *    若后端未启动, 页面渲染 el-alert 错误提示, 但 banner/overview-card 与刷新按钮始终渲染。
 */
import { test, expect, type Page } from '@playwright/test'
import { loginAs } from './helpers/auth'

/**
 * 附加控制台错误与页面异常捕获。
 *
 * 模仿 dashboard.spec.ts 的过滤策略, 排除 dev 模式下后端未启动或 mock 接口返回
 * 401/403/404/500/502/503 以及 WebSocket 连接失败等预期网络噪声, 仅保留未捕获的
 * JS 异常与持续性逻辑错误。
 */
function attachErrorCapture(page: Page): { assert: () => void } {
  const consoleErrors: string[] = []
  const pageErrors: string[] = []
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text())
    }
  })
  page.on('pageerror', (err) => {
    pageErrors.push(err.message)
  })
  return {
    assert: () => {
      expect(pageErrors, `未捕获的页面异常: ${pageErrors.join('; ')}`).toEqual([])
      const criticalConsoleErrors = consoleErrors.filter(
        (e) =>
          !e.includes('Failed to load resource') &&
          !e.includes('Network Error') &&
          !e.includes('ERR_') &&
          !e.includes('Failed to fetch') &&
          !e.includes('WebSocket') &&
          !e.includes('401') &&
          !e.includes('403') &&
          !e.includes('404') &&
          !e.includes('500') &&
          !e.includes('502') &&
          !e.includes('503'),
      )
      expect(
        criticalConsoleErrors,
        `控制台持续性错误: ${criticalConsoleErrors.join('; ')}`,
      ).toEqual([])
    },
  }
}

test.describe('GA2-L183 平台监控域核心路由', () => {
  test.describe.configure({ mode: 'serial' })

  test('服务健康页 /monitor/health 页面与健康状态卡片可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/monitor/health')
    await page.waitForLoadState('networkidle')

    // 页面可达 (未跳 /403 或 /login)
    await expect(page).toHaveURL(/\/monitor\/health/)
    // 健康状态卡片可见 (MonitorHealth.vue banner-card .banner-label "总体状态")
    // 该卡片在 v-if 之外, 始终渲染, 是页面可达性的稳定锚点
    await expect(page.getByText('总体状态', { exact: true })).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: 刷新按钮 (el-button text="刷新", 始终渲染)
    await expect(page.getByRole('button', { name: '刷新' })).toBeVisible()
    cap.assert()
  })

  test('缓存监控页 /monitor/cache 页面与缓存信息可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/monitor/cache')
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/monitor\/cache/)
    // 缓存概览卡片可见 (MonitorCache.vue overview-meta <strong>全局命中率:</strong>, 带冒号)
    // 该卡片在 v-if 之外, 始终渲染; 实际 DOM 文本含冒号, 故用子串匹配而非 exact
    await expect(page.getByText('全局命中率')).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: 缓存列表表格 (MonitorCache.vue el-table, 始终渲染, 数据为空时显示 empty-text)
    await expect(page.locator('.el-table').first()).toBeVisible()
    // 刷新按钮 (el-button text="刷新")
    await expect(page.getByRole('button', { name: '刷新' })).toBeVisible()
    cap.assert()
  })
})
