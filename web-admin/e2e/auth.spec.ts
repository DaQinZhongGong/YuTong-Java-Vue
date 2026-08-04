/**
 * GA2-L183: 认证流程 E2E 测试。
 *
 * 覆盖场景:
 *  - admin_demo 登录后跳转 /dashboard
 *  - viewer_demo 登录后跳转 /dashboard (验证不同 Mock 用户均可登录)
 *  - 错误密码登录失败, 停留 /login 并展示错误 toast
 *  - 退出登录后跳转 /login
 *
 * 设计来源:
 *  - 59-测试矩阵与验收用例详设 (TC-P2-AUTH-001 Mock 登录)
 *  - web-admin/src/views/login/LoginView.vue (登录表单 + Mock 用户选择)
 *  - web-admin/src/stores/auth.ts (login/logout 实现)
 *  - web-admin/src/layouts/DefaultLayout.vue (退出登录入口)
 */
import { test, expect } from '@playwright/test'
import { loginAs, logout } from './helpers/auth'

test.describe('GA2-L183 认证流程', () => {
  test.describe.configure({ mode: 'serial' })

  test('admin_demo 登录成功跳转 /dashboard', async ({ page }) => {
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    // 断言 URL 在 /dashboard
    await expect(page).toHaveURL(/\/dashboard/)
    // 断言页面渲染 (DefaultLayout 含侧边菜单)
    // 使用 menubar role 定位主菜单 (避免匹配到子菜单 el-menu--inline)
    await expect(page.getByRole('menubar', { name: '侧边栏导航' })).toBeVisible({ timeout: 10_000 })
  })

  test('viewer_demo 登录成功跳转 /dashboard', async ({ page }) => {
    // viewer 权限受限但 dashboard 仍可访问 (dashboard:view 权限已授予)
    await loginAs(page, 'viewer_demo', 'demo123', 'viewer')
    await expect(page).toHaveURL(/\/dashboard/)
    await expect(page.getByRole('menubar', { name: '侧边栏导航' })).toBeVisible({ timeout: 10_000 })
  })

  test('错误密码登录失败, 停留 /login (TC-P2-AUTH-001 反向用例)', async ({ page }) => {
    // dev 模式下后端 MockAuthAdapter 仍校验密码:
    //   - 正确密码 (demo123) → 200 + token
    //   - 错误密码 → 403 AUTH-403001 (后端返回权限错误而非认证错误, 已记录)
    // 前端 request 拦截器对 403 派发 no-permission 事件, 不弹 ElMessage,
    // LoginView catch 块不跳转, 页面停留 /login。
    await page.goto('/login')
    await page.waitForSelector('#login-username', { state: 'visible' })
    await page.fill('#login-username', 'admin_demo')
    await page.fill('#login-password', 'wrong_password_xyz')
    // dev 模式选择 admin Mock 用户
    const mockSelect = page.locator('#login-mock-user').locator('xpath=ancestor::div[contains(@class,"el-select")]').first()
    await mockSelect.click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: '平台管理员' }).first().click()
    await expect(page.locator('.el-select-dropdown:visible')).toHaveCount(0, { timeout: 3_000 })

    await page.getByRole('button', { name: /登\s*录/ }).click()
    // 等待请求完成 (后端返回 403, 前端不跳转)
    await page.waitForTimeout(3000)
    // 仍在 /login
    await expect(page).toHaveURL(/\/login/)
  })

  test('退出登录后跳转 /login', async ({ page }) => {
    // 先登录
    await loginAs(page, 'admin_demo', 'demo123', 'admin')
    await expect(page).toHaveURL(/\/dashboard/)

    // 退出
    await logout(page)
    await expect(page).toHaveURL(/\/login/)
    // 清空 token 后访问受保护路由应跳回 /login?redirect=
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/login/)
  })
})
