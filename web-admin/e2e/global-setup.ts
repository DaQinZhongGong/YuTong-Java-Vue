import { chromium, type FullConfig } from '@playwright/test'

/**
 * GA2-L183: E2E 全局预热。
 *
 * Vite 在 e2e 模式下首次访问页面时会按需预构建依赖并触发页面 reload，
 * 导致登录请求被中断。globalSetup 会反复尝试登录直到成功进入 dashboard，
 * 借此一次性完成所有 Element Plus 组件的依赖优化，保证后续测试稳定运行。
 */
async function tryLoginAndWaitForDashboard(
  page: Awaited<ReturnType<typeof browser.newPage>>,
  url: string,
): Promise<boolean> {
  await page.goto(`${url}/login`)
  await page.waitForSelector('#login-username', { state: 'visible', timeout: 10_000 })
  await page.fill('#login-username', 'admin_demo')
  await page.fill('#login-password', 'demo123')

  const mockSelectWrapper = page.locator('#login-mock-user').locator('xpath=ancestor::div[contains(@class,"el-select")]').first()
  if (await mockSelectWrapper.isVisible().catch(() => false)) {
    await mockSelectWrapper.click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: '平台管理员' }).first().click()
    await page.waitForSelector('.el-select-dropdown:visible', { state: 'hidden' }).catch(() => {})
  }

  await page.getByRole('button', { name: /登\s*录/ }).click()
  try {
    await page.waitForURL('**/dashboard**', { timeout: 15_000 })
    await page.waitForLoadState('networkidle')
    return true
  } catch {
    return false
  }
}

export default async function globalSetup(config: FullConfig) {
  const { baseURL } = config.projects[0].use
  const url = baseURL || 'http://localhost:5183'
  const browser = await chromium.launch()
  const page = await browser.newPage()

  let ok = false
  for (let i = 0; i < 5; i++) {
    ok = await tryLoginAndWaitForDashboard(page, url)
    if (ok) break
    // Vite 可能还在按需预构建，页面 reload 会打断登录；刷新后重试
    await page.waitForTimeout(1_000)
  }

  await browser.close()
  if (!ok) {
    throw new Error('Global setup failed: unable to login and reach /dashboard after retries')
  }
}
