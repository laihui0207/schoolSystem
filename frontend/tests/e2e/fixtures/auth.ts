import { test as base } from '@playwright/test'

/**
 * Test fixtures for authenticated user sessions
 */

interface TestFixtures {
  authenticatedPage: any
}

export const test = base.extend<TestFixtures>({
  authenticatedPage: async ({ page }, use) => {
    // Navigate to login page
    await page.goto('/login')

    // Perform login (Element Plus inputs don't have type="text", use placeholders)
    await page.fill('input[placeholder="请输入用户名"]', 'admin')
    await page.fill('input[placeholder="请输入密码"]', 'admin123')
    await page.getByRole('button', { name: '登录' }).click()

    // Wait for navigation to complete
    await page.waitForURL('/', { timeout: 5000 })

    // Use the authenticated page
    await use(page)
  }
})

export { expect } from '@playwright/test'
