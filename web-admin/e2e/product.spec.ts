/**
 * GA2-L183: 商品域 (Product) 核心 GA 路由 E2E 测试。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节: /biz/products 路由覆盖)
 *  - web-admin/src/views/product/ProductList.vue (商品列表: el-form inline 搜索 + el-table + 分页)
 *
 * 覆盖场景 (1 条路由):
 *  - /biz/products: 商品列表表格可见 + 搜索框可见
 *
 * 前置条件:
 *  - admin_mock 用户具备 biz:product:list 权限
 *  - dev/local profile 下 MockAuthAdapter 注入 X-Mock-User: admin_demo
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

test.describe('GA2-L183 商品域核心路由', () => {
  test.describe.configure({ mode: 'serial' })

  test('商品列表页 /biz/products 表格与搜索框可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/biz/products')
    await page.waitForLoadState('networkidle')

    // 页面可达 (未跳 /403 或 /login)
    await expect(page).toHaveURL(/\/biz\/products/)
    // 商品列表表格可见 (ProductList.vue el-table, 数据为空时仍渲染表头)
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 10_000 })
    // 关键元素: 搜索框 (el-form-item label="商品名称" 内 input, placeholder="搜索商品名称")
    await expect(page.getByPlaceholder('搜索商品名称')).toBeVisible()
    cap.assert()
  })
})
