import { afterEach, vi } from 'vitest'

/**
 * Global test setup for Vitest.
 *
 * Note: We intentionally do NOT globally mock `vue-router` here. Doing so
 * without providing `createRouter`/`createWebHistory` would break `@/router`
 * (and any module that imports it, such as the user store). Tests that need to
 * avoid the real router mock `@/router` locally instead.
 */

// Mock Element Plus ElMessage so unit tests don't create real DOM notifications.
// We keep every other Element Plus export intact via importActual.
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn()
    }
  }
})

// Reset mock state between tests to keep them isolated.
afterEach(() => {
  vi.clearAllMocks()
})
