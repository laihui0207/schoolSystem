import { test, expect } from '@playwright/test'

test.describe('Login Flow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('should redirect to login page and display title', async ({ page }) => {
    // Unauthenticated users visiting / should land on /login.
    await expect(page).toHaveURL('/login')
    await expect(page.locator('.title')).toContainText('学生成绩管理系统')
  })

  test('should show validation error for empty username', async ({ page }) => {
    await page.goto('/login')

    // Clear the default values (username: 'admin', password: 'admin123').
    await page.fill('input[placeholder="请输入用户名"]', '')
    await page.fill('input[placeholder="请输入密码"]', '')

    // Click the login button.
    await page.getByRole('button', { name: '登录' }).click()

    // Element Plus form validation should show an error without hitting the backend.
    await expect(page.locator('.el-form-item__error').first()).toBeVisible()
  })

  test('should navigate to login when accessing protected route without auth', async ({ page }) => {
    await page.goto('/dashboard')
    await expect(page).toHaveURL('/login')
  })

  // The tests below require a live backend at :8080 and are skipped for now.
  test.describe('Backend-dependent tests (skipped)', () => {
    test.skip('should successfully login with valid credentials', async ({ page }) => {
      await page.goto('/login')
      await page.fill('input[placeholder="请输入用户名"]', 'admin')
      await page.fill('input[placeholder="请输入密码"]', 'admin123')
      await page.getByRole('button', { name: '登录' }).click()

      await expect(page).toHaveURL('/', { timeout: 5000 })
      await expect(page.locator('.layout-header')).toBeVisible()
    })

    test.skip('should show error message for invalid credentials', async ({ page }) => {
      await page.goto('/login')
      await page.fill('input[placeholder="请输入用户名"]', 'wronguser')
      await page.fill('input[placeholder="请输入密码"]', 'wrongpass')
      await page.getByRole('button', { name: '登录' }).click()

      await expect(page.locator('.el-message--error')).toBeVisible()
    })
  })
})
