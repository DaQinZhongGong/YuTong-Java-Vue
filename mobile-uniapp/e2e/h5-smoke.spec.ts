/**
 * Mobile UniApp H5 层冒烟 E2E 测试。
 *
 * 设计来源:
 *  - mobile-uniapp/src/pages/login/login.vue (登录表单: 标题/用户名/密码/登录按钮)
 *  - mobile-uniapp/src/pages/workbench/workbench.vue (工作台: 问候语/统计/快捷入口/最近待办)
 *  - mobile-uniapp/src/pages/todo/todo.vue (待办: 搜索框/筛选 tabs/列表)
 *  - mobile-uniapp/src/pages/message/list.vue (消息: 全部已读/消息列表)
 *  - mobile-uniapp/src/pages/mine/mine.vue (我的: 头像/用户名/菜单/退出登录)
 *  - mobile-uniapp/src/pages.json (tabBar: 工作台/待办/我的; message 为子包页面)
 *
 * 编写规范:
 *  - 选择器使用 uniapp H5 渲染后的实际 DOM: uni-input 外层元素保留 aria-label, placeholder 会被剥离为独立元素,
 *    因此定位用 uni-input[aria-label=...] input 而非 input[placeholder=...]
 *  - 登录凭据 admin_demo/demo123 (GA2-L178 Demo 账号, 对齐 login.vue 开发账号)
 *  - 不硬编码 waitForTimeout, 使用 waitForLoadState / expect 断言等待
 *  - 降级策略: 若 E2E 环境未就绪, 仅保证类型检查通过即可
 */
import { test, expect, type Page } from '@playwright/test'

/**
 * 使用 admin_demo/demo123 登录并等待跳转至工作台。
 * 登录成功后 login.vue 通过 uni.switchTab 跳转 /pages/workbench/workbench。
 */
async function loginAsAdmin(page: Page): Promise<void> {
  await page.goto('/#/pages/login/login')
  await page.waitForLoadState('domcontentloaded')
  await page.fill('uni-input[aria-label="用户名"] input', 'admin_demo')
  await page.fill('uni-input[aria-label="密码"] input', 'demo123')
  // uni-button 渲染为自定义元素无 role, 用 aria-label 定位 (login.vue 已声明 aria-label="登录")
  await page.locator('uni-button[aria-label="登录"]').click()
  // login.vue 在 showToast 后 setTimeout 500ms 再 switchTab; tabBar 首页 URL 为 #/ , 用工作台内容断言
  await expect(page.locator('.workbench__greeting')).toBeVisible({ timeout: 20_000 })
}

test.describe('H5 冒烟测试', () => {
  test('登录页可见 (标题/账号/密码/登录按钮)', async ({ page }) => {
    await page.goto('/#/pages/login/login')
    await page.waitForLoadState('domcontentloaded')

    // 标题
    await expect(page.locator('.login-title')).toHaveText('YuTong')
    // 副标题
    await expect(page.locator('.login-subtitle')).toHaveText('欢迎登录')
    // 账号输入框 (uni-input 外层保留 aria-label)
    await expect(page.locator('uni-input[aria-label="用户名"]')).toBeVisible()
    // 密码输入框
    await expect(page.locator('uni-input[aria-label="密码"]')).toBeVisible()
    // 登录按钮 (uni-button 自定义元素无 role, 用 aria-label 定位)
    await expect(page.locator('uni-button[aria-label="登录"]')).toBeVisible()
  })

  test('登录成功跳转工作台 (admin_demo/demo123)', async ({ page }) => {
    await loginAsAdmin(page)
    // 断言工作台已渲染 (tabBar 首页 URL 为 #/)
    await expect(page.locator('.workbench__greeting')).toBeVisible()
  })

  test('工作台标题可见 + 快捷入口可见', async ({ page }) => {
    await loginAsAdmin(page)

    // 工作台问候语 (greeting + userName)
    await expect(page.locator('.workbench__greeting')).toBeVisible()
    // 快捷入口区域 (aria-label="快捷入口")
    await expect(page.getByRole('region', { name: '快捷入口' })).toBeVisible()
    // 至少一个快捷入口可见 (待办 shortcut 受 mobile:todo:list 权限控制, admin 应可见)
    await expect(page.locator('.shortcut').first()).toBeVisible()
  })

  test('待办列表可见', async ({ page }) => {
    await loginAsAdmin(page)

    // 跳转至待办页 (tabBar 页面)
    await page.goto('/#/pages/todo/todo')
    await page.waitForLoadState('domcontentloaded')

    // 待办搜索框可见 (uni-input 外层 aria-label)
    await expect(page.locator('uni-input[aria-label="搜索待办"]')).toBeVisible()
    // 筛选 tabs 可见 (待办筛选 tablist: 全部/待处理/已处理; 时间范围筛选 tablist 也有"全部", 需限定作用域)
    const statusTabs = page.getByRole('tablist', { name: '待办筛选' })
    await expect(statusTabs.getByRole('tab', { name: '全部' })).toBeVisible()
    await expect(statusTabs.getByRole('tab', { name: '待处理' })).toBeVisible()
    await expect(statusTabs.getByRole('tab', { name: '已处理' })).toBeVisible()
  })

  test('消息列表可见', async ({ page }) => {
    await loginAsAdmin(page)

    // 跳转至消息页 (子包页面 pages/message/list)
    await page.goto('/#/pages/message/list')
    await page.waitForLoadState('domcontentloaded')

    // "全部已读" 操作按钮可见 (aria-label=全部标记为已读)
    await expect(page.getByRole('button', { name: '全部标记为已读' })).toBeVisible()
    // 消息列表区域可见 (消息项或空态至少存在其一)
    const messageList = page.locator('.message-list')
    await expect(messageList).toBeVisible()
  })

  test('我的页可见', async ({ page }) => {
    await loginAsAdmin(page)

    // 跳转至我的页 (tabBar 页面)
    await page.goto('/#/pages/mine/mine')
    await page.waitForLoadState('domcontentloaded')

    // 用户资料卡片可见
    await expect(page.locator('.profile-card')).toBeVisible()
    // 用户名可见 (登录后应显示 admin_demo)
    await expect(page.locator('.profile-name')).toBeVisible()
    // 菜单项可见 (个人资料/设置/关于)
    await expect(page.getByRole('menuitem', { name: '个人资料' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: '设置' })).toBeVisible()
    await expect(page.getByRole('menuitem', { name: '关于' })).toBeVisible()
    // 退出登录可见
    await expect(page.getByRole('menuitem', { name: '退出登录' })).toBeVisible()
  })
})
