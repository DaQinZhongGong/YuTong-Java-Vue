/**
 * GA2-L183: 申请单管理 (BizRequest) 核心 GA 路由 E2E 测试。
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (Web E2E 章节: /biz/requests 列表/新建/详情路由覆盖)
 *  - web-admin/src/views/request/RequestList.vue (列表页: BaseTable + SearchForm)
 *  - web-admin/src/views/request/RequestForm.vue (新建/编辑表单: el-form + 明细表)
 *  - web-admin/src/views/request/RequestDetail.vue (详情页: el-descriptions + 审批记录 + AuditTimeline)
 *  - web-admin/src/components/AuditTimeline.vue (审核轨迹时间线通用组件)
 *
 * 覆盖场景:
 *  - 列表页 /biz/requests: 表格可见、搜索框可见
 *  - 新建页 /biz/requests/create: 点击 "新建申请单" 跳转、表单可见
 *  - 详情页 /biz/requests/{ULID}: 详情页可见、审批记录区块可见、AuditTimeline 时间线视图可见
 *
 * 前置数据假设:
 *  - ULID 01KYK8A3XPC0D4AVBK5MFWVB4R 为种子数据中真实存在的申请单, 且有 2 条审批记录
 *    (RequestDetail.vue 中审批记录卡片和 AuditTimeline 卡片的 v-if 条件:
 *     detail.approvals && detail.approvals.length > 0)
 */
import { test, expect } from '@playwright/test'
import { loginAs } from './helpers/auth'

/** 种子数据中的真实申请单 ULID (dev/local profile 种子脚本写入)。 */
const SEED_REQUEST_ULID = '01KYK8A3XPC0D4AVBK5MFWVB4R'

test.describe('GA2-L183 申请单管理核心路由', () => {
  test.describe.configure({ mode: 'serial' })

  test('列表页 /biz/requests 表格与搜索框可见', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/biz/requests')
    await page.waitForLoadState('networkidle')

    // 验证表格可见 (BaseTable 渲染 el-table, GA2-55 重构后使用通用组件)
    await expect(page.locator('.el-table').first()).toBeVisible({ timeout: 10_000 })

    // 验证搜索框可见 (SearchForm 字段 requestNo placeholder="申请单号")
    await expect(page.getByPlaceholder('申请单号')).toBeVisible({ timeout: 10_000 })
  })

  test('点击新建申请单跳转 /biz/requests/create 表单可见', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto('/biz/requests')
    await page.waitForLoadState('networkidle')

    // 等待列表渲染 (SearchForm 含 "新建申请单" 按钮, 权限码 biz:request:add)
    const createButton = page.getByRole('button', { name: '新建申请单' })
    await expect(createButton).toBeVisible({ timeout: 10_000 })
    await createButton.click()

    // 验证跳转到新建页 (RequestList.vue handleCreate → router.push('/biz/requests/create'))
    await page.waitForURL('**/biz/requests/create**', { timeout: 10_000 })

    // 验证表单可见 (RequestForm.vue el-page-header content="新建申请单" + el-form-item label="申请单标题")
    await expect(page.getByText('新建申请单', { exact: true })).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('申请单标题')).toBeVisible()
  })

  test('详情页 /biz/requests/{ULID} 详情/审批记录/AuditTimeline 可见', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await page.goto(`/biz/requests/${SEED_REQUEST_ULID}`)
    await page.waitForLoadState('networkidle')

    // 验证详情页 page-header 可见 (RequestDetail.vue el-page-header content="申请单详情")
    // 该元素在 v-if="detail" 之外, 始终渲染
    await expect(page.getByText('申请单详情', { exact: true })).toBeVisible({ timeout: 10_000 })

    // 验证审批记录区块可见 (RequestDetail.vue el-card header="审批记录")
    // 前置: detail.approvals.length > 0 (种子数据需包含审批记录)
    await expect(page.getByText('审批记录', { exact: true })).toBeVisible({ timeout: 10_000 })

    // 验证审核轨迹时间线视图卡片可见 (RequestDetail.vue el-card header="审核轨迹（时间线视图）")
    await expect(page.getByText('审核轨迹（时间线视图）')).toBeVisible()

    // 验证 AuditTimeline 组件渲染 (AuditTimeline.vue 根节点 .yt-audit-timeline)
    await expect(page.locator('.yt-audit-timeline')).toBeVisible()
  })
})
