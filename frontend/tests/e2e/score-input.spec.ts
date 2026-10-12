import { test, expect } from '@playwright/test'

/**
 * Score Input E2E Tests
 *
 * NOTE: The `/score-input` route referenced in the original test does not exist.
 * The actual route is `/score`, but it's currently a placeholder page showing
 * "成绩管理 - 开发中". These tests are skipped until the score input feature is
 * fully implemented.
 */

test.describe.skip('Score Input Flow (Not Yet Implemented)', () => {
  test('should navigate to score management page', async ({ page }) => {
    await page.goto('/login')
    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', 'admin123')
    await page.getByRole('button', { name: '登录' }).click()
    await page.waitForURL('/', { timeout: 5000 })

    // Navigate to the score page (currently just shows "开发中").
    await page.goto('/score')
    await expect(page).toHaveURL('/score')
  })

  test('TODO: add tests for score input form once implemented', async () => {
    // Placeholder for future implementation.
  })
})
