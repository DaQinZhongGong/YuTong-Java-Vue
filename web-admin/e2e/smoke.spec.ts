import { test, expect } from '@playwright/test'

test('smoke', async ({ page }) => {
  await page.goto('/login')
  await expect(page).toHaveURL(/\/login/)
})
