/**
 * GA2-L183: 系统管理域 (System) 核心 GA 路由 E2E 测试。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节: /system/* 路由覆盖)
 *  - web-admin/src/views/dict/DictTypeView.vue (字典类型列表: h2.page-title + el-table + 关键字搜索)
 *  - web-admin/src/views/dict/DictItemView.vue (字典项列表: h2.page-title + el-table + 类型筛选)
 *  - web-admin/src/views/system/ConfigList.vue (参数配置列表: h2#page-title + el-table[aria-label] + 关键字搜索)
 *  - web-admin/src/views/file/FileList.vue (文件管理列表: h2.page-title + el-table + 上传按钮)
 *  - web-admin/src/views/message/MessageList.vue (站内消息列表: el-table + el-pagination)
 *  - web-admin/src/views/system/OperationLogList.vue (操作日志列表: h2.page-title + el-table[aria-label] + 操作人搜索)
 *
 * 覆盖场景 (6 条路由):
 *  - /system/dict-types: 字典类型列表表格可见
 *  - /system/dict-items: 字典项列表表格可见
 *  - /system/configs: 系统配置列表表格可见
 *  - /system/files: 文件列表可见 + 上传按钮可见
 *  - /system/messages: 消息列表可见
 *  - /system/operation-logs: 操作日志列表可见
 *
 * 前置条件:
 *  - admin_mock 用户具备 system:dict:list / system:dict-item:list / system:config:list /
 *    system:file:list / system:message:list / system:operation-log:list 全部权限
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
      // 1. pageerror (未捕获的 JS 异常) — 不允许出现
      expect(pageErrors, `未捕获的页面异常: ${pageErrors.join('; ')}`).toEqual([])
      // 2. console.error — 排除 dev 模式预期的网络/API/WebSocket 噪声
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

test.describe('GA2-L183 系统管理域核心路由', () => {
  test.describe.configure({ mode: 'serial' })

  test('字典类型页 /system/dict-types 表格可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/system/dict-types')
    await page.waitForLoadState('networkidle')

    // 页面可达 (未跳 /403 或 /login)
    await expect(page).toHaveURL(/\/system\/dict-types/)
    // 页面标题 (DictTypeView.vue h2.page-title "字典类型")
    await expect(page.getByRole('heading', { name: '字典类型' })).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: el-table + 关键字搜索框 (placeholder="请输入字典名称")
    await expect(page.locator('.el-table').first()).toBeVisible()
    await expect(page.getByPlaceholder('请输入字典名称')).toBeVisible()
    cap.assert()
  })

  test('字典项页 /system/dict-items 表格可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/system/dict-items')
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/system\/dict-items/)
    // 页面标题 (DictItemView.vue h2.page-title "字典项")
    await expect(page.getByRole('heading', { name: '字典项' })).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: el-table + 类型筛选下拉 (DictItemView.vue el-select placeholder="选择字典类型筛选")
    // el-select 不渲染原生 placeholder 属性, getByPlaceholder 无效;
    // 该 select 未设 aria-label, 但 placeholder 文本作为可见文本节点渲染且页面唯一, 用 getByText 定位
    await expect(page.locator('.el-table').first()).toBeVisible()
    await expect(page.getByText('选择字典类型筛选', { exact: true })).toBeVisible()
    cap.assert()
  })

  test('参数配置页 /system/configs 表格可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/system/configs')
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/system\/configs/)
    // 页面标题 (ConfigList.vue h2#page-title "参数配置")
    await expect(page.getByRole('heading', { name: '参数配置' })).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: el-table (aria-label="参数配置列表") + 参数键搜索框 (placeholder="参数键关键字")
    await expect(page.locator('.el-table').first()).toBeVisible()
    await expect(page.getByPlaceholder('参数键关键字')).toBeVisible()
    cap.assert()
  })

  test('文件管理页 /system/files 列表与上传按钮可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/system/files')
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/system\/files/)
    // 页面标题 (FileList.vue h2.page-title "文件管理")
    await expect(page.getByRole('heading', { name: '文件管理' })).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: el-table + 上传按钮 (el-upload 内 el-button text="上传文件", admin 具 system:file:upload 权限)
    // el-upload 包装 div 也带 role="button" 且含相同文本, getByRole 会命中 2 个 (strict 违规);
    // 改用 button 标签 + hasText 仅匹配内部真实 <button>, 排除 el-upload div 包装器
    await expect(page.locator('.el-table').first()).toBeVisible()
    await expect(page.locator('button', { hasText: '上传文件' })).toBeVisible()
    cap.assert()
  })

  test('站内消息页 /system/messages 列表可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/system/messages')
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/system\/messages/)
    // MessageList.vue 无 page-title, 直接以 el-table 为页面锚点
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 10_000 })
    // 关键元素: el-pagination (MessageList.vue 始终渲染分页条)
    await expect(page.locator('.el-pagination').first()).toBeVisible()
    cap.assert()
  })

  test('操作日志页 /system/operation-logs 列表可见', async ({ page }) => {
    const cap = attachErrorCapture(page)
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/system/operation-logs')
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/system\/operation-logs/)
    // 页面标题 (OperationLogList.vue h2.page-title "操作日志")
    await expect(page.getByRole('heading', { name: '操作日志' })).toBeVisible({
      timeout: 10_000,
    })
    // 关键元素: el-table (aria-label="操作日志列表") + 操作人搜索框 (placeholder="操作人 ID")
    await expect(page.locator('.el-table').first()).toBeVisible()
    await expect(page.getByPlaceholder('操作人 ID')).toBeVisible()
    cap.assert()
  })
})
