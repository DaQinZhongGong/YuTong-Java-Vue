/**
 * GA2-L183: 登录辅助函数。
 *
 * 设计来源: web-admin/src/views/login/LoginView.vue、web-admin/src/stores/auth.ts。
 *
 * 关键决策:
 *  1. dev 模式登录页有 Mock 用户类型下拉 (默认 admin), 仅 dev 模式可见;
 *     生产构建不显示选择器, mockUserType 入参被忽略。
 *  2. authStore.login(payload, mockUserType) 会先把 mockUserType 写入 localStorage,
 *     request 拦截器再从 localStorage 读出注入 X-Mock-User 头; 故选择下拉后必须等
 *     选项文本就绪再点击登录。
 *  3. 后端 MockAuthAdapter 在 dev/local profile 下以 X-Mock-User 决定身份,
 *     username/password 仅占位; E2E 仍填写 admin_demo/demo123 以贴近真实使用。
 *  4. 登录成功后路由跳 /dashboard (LoginView.vue line 57: redirect || '/dashboard')。
 */
import type { Page } from '@playwright/test'
import { expect } from '@playwright/test'
import { loginSelectors, mockUserOptionText } from './selectors'

/** 4 类 Mock 用户类型, 对应 web-admin/src/utils/auth.ts MockUserType。 */
export type MockUserType = 'admin' | 'biz' | 'approver' | 'viewer'

/**
 * 在 dev 登录页选择 Mock 用户类型。
 *
 * Element Plus el-select 下拉面板会渲染到 body 末尾 (.el-select-dropdown),
 * 选项文本格式为 `${label} — ${desc}` (例如 "平台管理员 — 全部权限 + ALL 数据范围")。
 * 这里用 label 文本片段做包含匹配, 避免硬编码 desc 文本。
 */
async function selectMockUser(page: Page, type: MockUserType): Promise<void> {
  // 找到 Mock 用户类型选择器的 el-select 容器 (Element Plus el-select 触发器是 .el-select,
  // 内部 input 被 .el-select__placeholder 覆盖, 直接点击 input 会被拦截, 故点击 wrapper)
  const mockSelectWrapper = page.locator('#login-mock-user').locator('xpath=ancestor::div[contains(@class,"el-select")]').first()
  if (!(await mockSelectWrapper.isVisible().catch(() => false))) {
    // dev 模式必定可见; 不可见说明非 dev 构建, 直接跳过
    return
  }
  await mockSelectWrapper.click()
  // 下拉项渲染在 body 末尾; 用文本片段匹配 (label 是完整文本的前缀)
  const optionText = mockUserOptionText[type]
  const option = page.locator('.el-select-dropdown__item:visible', {
    hasText: optionText,
  })
  await option.first().click()
  // 等下拉关闭
  await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0, {
    timeout: 3_000,
  })
}

/**
 * 以指定 Mock 用户类型登录。
 *
 * @param page Playwright Page
 * @param username 用户名 (dev 模式仅占位; 默认 admin_demo)
 * @param password 密码 (dev 模式仅占位; 默认 demo123)
 * @param mockUserType Mock 用户类型 (默认 admin); 仅 dev 模式生效
 * @param expectPath 登录成功后预期跳转路径 (默认 /dashboard)
 */
export async function loginAs(
  page: Page,
  username = 'admin_demo',
  password = 'demo123',
  mockUserType: MockUserType = 'admin',
  expectPath: string = loginSelectors.dashboardPath,
): Promise<void> {
  await page.goto(loginSelectors.loginPath)
  // 等登录卡片渲染
  await page.waitForSelector(loginSelectors.usernameInput, { state: 'visible' })

  await page.fill(loginSelectors.usernameInput, username)
  await page.fill(loginSelectors.passwordInput, password)

  // dev 模式选择 Mock 用户类型 (生产构建 selectMockUser 会自动跳过)
  await selectMockUser(page, mockUserType)

  await page.getByRole('button', { name: loginSelectors.loginButton }).click()

  // 等待跳转; 路由守卫先校验 token 再校验 userInfo, 可能触发一次 /auth/me
  await page.waitForURL(`**${expectPath}**`, { timeout: 15_000 })
  // 等待 dashboard 页面主内容渲染, 避免后续操作撞上 loading
  await page.waitForLoadState('networkidle')
}

/**
 * 在 dev 登录页提交错误密码并断言停留在登录页。
 *
 * 后端 MockAuthAdapter 在 dev 模式下仍会以 X-Mock-User 决定身份,
 * 但错误密码会触发 AUTH-401001; 前端 request 拦截器弹 ElMessage 并 reject,
 * LoginView 不跳转。这里断言 URL 仍在 /login。
 */
export async function loginWithWrongPassword(
  page: Page,
  username = 'admin_demo',
  wrongPassword = 'wrong_password_xyz',
): Promise<void> {
  await page.goto(loginSelectors.loginPath)
  await page.waitForSelector(loginSelectors.usernameInput, { state: 'visible' })

  await page.fill(loginSelectors.usernameInput, username)
  await page.fill(loginSelectors.passwordInput, wrongPassword)
  await page.getByRole('button', { name: loginSelectors.loginButton }).click()

  // 等待错误提示出现 (ElMessage)
  await expect(page.locator('.el-message--error')).toBeVisible({ timeout: 10_000 })
  // 仍在 /login
  await expect(page).toHaveURL(/\/login/)
}

/**
 * 退出登录。
 *
 * DefaultLayout.vue 用户头像下拉菜单含 "退出登录", 点击后弹 ElMessageBox.confirm,
 * 确认后调用 authStore.logout() 清空状态并跳 /login。
 */
export async function logout(page: Page): Promise<void> {
  // 找到顶部用户头像下拉触发器 (DefaultLayout 含 avatar + dropdown)
  // 用 aria-label 或文本兜底定位; Element Plus dropdown 触发器常见为 .el-dropdown
  const dropdownTrigger = page
    .locator('.el-dropdown', { has: page.locator('.el-avatar, .el-icon') })
    .first()
  await dropdownTrigger.click()

  // 点击下拉项中的 "退出登录"
  const logoutItem = page.locator('.el-dropdown-menu__item:visible', {
    hasText: '退出登录',
  })
  await logoutItem.first().click()

  // ElMessageBox.confirm 弹窗的确定按钮
  const confirmBtn = page.locator('.el-message-box__btns .el-button--primary', {
    hasText: '确定',
  })
  await confirmBtn.click()

  // 等待跳转到 /login
  await page.waitForURL('**/login**', { timeout: 10_000 })
}
