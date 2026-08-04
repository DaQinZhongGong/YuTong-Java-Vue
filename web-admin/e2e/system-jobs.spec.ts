/**
 * GA2-L183: 任务日志域 (System Jobs) 核心 GA 路由 E2E 测试。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节: /system/job-logs + /system/import-export-tasks 路由覆盖)
 *  - 15-平台基础能力补齐 — GA2-15 任务日志/导入导出任务清单
 *  - web-admin/src/views/system/JobLogList.vue (任务日志列表: h2.page-title + el-table[aria-label] + 任务名称搜索)
 *  - web-admin/src/views/system/ImportExportTaskList.vue (导入导出任务列表: h2.page-title + el-table[aria-label] + 类型/状态筛选)
 *
 * 覆盖场景 (2 条路由):
 *  - /system/job-logs: 任务日志列表可见
 *  - /system/import-export-tasks: 导入导出任务列表可见
 *
 * 前置条件:
 *  - admin_mock 用户具备 system:job-log:list / system:import-export-task:list 权限
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

test.describe('GA2-L183 任务日志域核心路由', () => {
  test.describe.configure({ mode: 'serial' })

  test('任务日志页 /system/job-logs 列表可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/system/job-logs')
    await page.waitForLoadState('networkidle')

    // 页面可达 (未跳 /403 或 /login)
    await expect(page).toHaveURL(/\/system\/job-logs/)
    // 页面标题 (JobLogList.vue h2.page-title "任务日志")
    await expect(page.getByRole('heading', { name: '任务日志' })).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: el-table (aria-label="任务日志列表") + 任务名称搜索框 (placeholder="任务名称")
    await expect(page.locator('.el-table').first()).toBeVisible()
    await expect(page.getByPlaceholder('任务名称')).toBeVisible()
    cap.assert()
  })

  test('导入导出任务页 /system/import-export-tasks 列表可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/system/import-export-tasks')
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/system\/import-export-tasks/)
    // 页面标题 (ImportExportTaskList.vue h2.page-title "导入导出任务")
    await expect(page.getByRole('heading', { name: '导入导出任务' })).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: el-table (aria-label="导入导出任务列表") + 任务类型筛选下拉
    // el-select 不渲染原生 placeholder 属性, getByPlaceholder 无效;
    // ImportExportTaskList.vue 已设 aria-label="任务类型筛选", el-select 渲染为 role=combobox
    await expect(page.locator('.el-table').first()).toBeVisible()
    await expect(page.getByRole('combobox', { name: '任务类型筛选' })).toBeVisible()
    cap.assert()
  })
})
