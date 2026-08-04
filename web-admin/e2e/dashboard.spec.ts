/**
 * GA2-L183: 工作台 (Dashboard) E2E 测试。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节 TC-E2E-WEB-001 核心路由可达性)
 *  - web-admin/src/views/dashboard/DashboardView.vue (工作台页面: 统计卡片 + ECharts 图表 + 快捷入口)
 *
 * 覆盖场景:
 *  - 登录后访问 /dashboard, 验证页面标题 "工作台" 可见
 *  - 统计卡片可见 (客户总数/商品总数/申请单总数/待办任务 — DashboardView.vue cardsRow1)
 *  - 快捷入口区块可见 (DashboardView.vue shortcuts: 客户管理/申请单等)
 *  - 控制台无持续性错误 (排除 dev 模式预期的网络/API 错误, 仅校验未捕获的 JS 异常)
 */
import { test, expect } from '@playwright/test'
import { loginAs } from './helpers/auth'

test.describe('GA2-L183 工作台 (Dashboard) - TC-E2E-WEB-001', () => {
  test.describe.configure({ mode: 'serial' })

  test('登录后访问 /dashboard 验证页面元素与控制台无错误', async ({ page }) => {
    // 收集控制台错误和未捕获的页面异常
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

    await loginAs(page, 'admin_demo', 'demo123', 'admin')

    // 登录后默认跳转 /dashboard (loginAs expectPath='/dashboard')
    await expect(page).toHaveURL(/\/dashboard/)
    // 等待 dashboard 数据加载 + ECharts 渲染完成
    await page.waitForLoadState('networkidle')

    // 验证页面标题包含 "工作台" (DashboardView.vue h2.page-title)
    await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible()

    // 验证统计卡片可见 (cardsRow1: 客户总数/商品总数/申请单总数/待办任务)
    // 这些文本仅在 dashboard 统计卡片中出现, 不会与侧边栏菜单冲突
    await expect(page.getByText('客户总数')).toBeVisible()
    await expect(page.getByText('商品总数')).toBeVisible()
    await expect(page.getByText('申请单总数')).toBeVisible()

    // 验证快捷入口区块可见 (DashboardView.vue section-title "快捷入口")
    await expect(page.getByText('快捷入口', { exact: true })).toBeVisible()

    // 验证 ECharts 图表区块标题可见 (证明图表区域渲染)
    await expect(page.getByText('近 7 日申请单提交趋势')).toBeVisible()
    await expect(page.getByText('申请单状态分布')).toBeVisible()

    // 验证控制台无持续性错误:
    // 1. pageerror (未捕获的 JS 异常) — 不允许出现
    expect(pageErrors, `未捕获的页面异常: ${pageErrors.join('; ')}`).toEqual([])

    // 2. console.error — 排除 dev 模式预期的网络/API 错误 (后端未启动或 mock 401/403/404/500)
    const criticalConsoleErrors = consoleErrors.filter(
      (e) =>
        !e.includes('Failed to load resource') &&
        !e.includes('Network Error') &&
        !e.includes('ERR_') &&
        !e.includes('Failed to fetch') &&
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
  })
})
