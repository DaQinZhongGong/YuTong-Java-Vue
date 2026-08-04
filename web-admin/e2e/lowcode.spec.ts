/**
 * GA2-L183: 低代码 (LowCode) 核心 GA 路由 E2E 测试。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节: /lowcode/entities + /lowcode/pages 路由覆盖)
 *  - web-admin/src/views/lowcode/LcEntityList.vue (实体列表: 过滤栏 + el-table)
 *  - web-admin/src/views/lowcode/LcPageList.vue (页面列表: 过滤栏 + el-table)
 *
 * 覆盖场景:
 *  - 实体列表页 /lowcode/entities: 实体列表可见
 *  - 页面列表页 /lowcode/pages: 页面列表可见
 *
 * 前置条件:
 *  - 路由 meta.licenseRequired='lowcode', 需 licenseStore.isModuleActive('lowcode') 通过
 *  - dev/local profile 下 License 模块默认全部激活
 *  - 路由 meta.permissions: 实体列表需 'lc:entity:list', 页面列表需 'lc:page:list'
 *  - admin_mock 用户具备全部权限 (dev MockAuthAdapter)
 */
import { test, expect } from '@playwright/test'
import { loginAs } from './helpers/auth'

test.describe('GA2-L183 低代码核心路由', () => {
  test.describe.configure({ mode: 'serial' })

  test('实体列表页 /lowcode/entities 实体列表可见', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/lowcode/entities')
    await page.waitForLoadState('networkidle')

    // 验证实体列表页表格可见 (LcEntityList.vue el-table aria-label="低代码实体列表")
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 10_000 })

    // 验证过滤栏可见 (el-form-item label="实体编码" — 过滤条件, 始终渲染)
    await expect(page.getByText('实体编码', { exact: true }).first()).toBeVisible()
  })

  test('页面列表页 /lowcode/pages 页面列表可见', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/lowcode/pages')
    await page.waitForLoadState('networkidle')

    // 验证页面列表页表格可见 (LcPageList.vue el-table aria-label="低代码页面列表")
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 10_000 })

    // 验证过滤栏可见 (el-form-item label="页面编码" — 过滤条件, 始终渲染)
    await expect(page.getByText('页面编码', { exact: true }).first()).toBeVisible()
  })
})
