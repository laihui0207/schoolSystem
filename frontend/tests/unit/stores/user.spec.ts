import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock element-plus to avoid resolution issues (used by @/utils/errorHandler)
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  }
}))

// Mock the auth API so the store never performs real HTTP calls.
vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  logout: vi.fn()
}))

// Mock the router so importing the store does not pull in the real route table
// (which triggers dynamic view imports). We only need `push` for logout.
vi.mock('@/router', () => ({
  default: {
    push: vi.fn()
  }
}))

import { useUserStore } from '@/stores/user'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import router from '@/router'

describe('User Store', () => {
  beforeEach(() => {
    // Fresh pinia + clean localStorage before each test.
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('should initialize with default state', () => {
    const store = useUserStore()
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
  })

  it('should initialize token from localStorage when present', () => {
    localStorage.setItem('token', 'persisted-token')
    // Re-create pinia so the store setup runs after localStorage is populated.
    setActivePinia(createPinia())

    const store = useUserStore()
    expect(store.token).toBe('persisted-token')
  })

  // 契约 T：refreshToken 同样从 localStorage 恢复，刷新页面后 401 续期才有料可用
  it('should initialize refreshToken from localStorage when present', () => {
    localStorage.setItem('refreshToken', 'persisted-refresh')
    setActivePinia(createPinia())

    const store = useUserStore()
    expect(store.refreshToken).toBe('persisted-refresh')
  })

  it('should set token and userInfo on successful login', async () => {
    const mockResponse = {
      accessToken: 'login-token-123',
      refreshToken: 'refresh-token-456',
      tokenType: 'Bearer',
      expiresIn: 7200,
      // 契约 2：登录响应携带 userType / studentId / teacherId / headTeacher / role
      userInfo: {
        id: 1,
        username: 'admin',
        role: 'admin',
        userType: 2,
        studentId: null,
        teacherId: null,
        headTeacher: false
      }
    }
    vi.mocked(loginApi).mockResolvedValue(mockResponse as any)

    const store = useUserStore()
    const result = await store.login('admin', 'admin123', '1234', 'captcha-key-1')

    // 验证码参数必须透传，否则后端直接拒登
    expect(loginApi).toHaveBeenCalledWith({
      username: 'admin',
      password: 'admin123',
      captcha: '1234',
      captchaKey: 'captcha-key-1'
    })
    expect(store.token).toBe('login-token-123')
    expect(store.userInfo).toEqual(mockResponse.userInfo)
    expect(localStorage.getItem('token')).toBe('login-token-123')
    // 契约 T：refreshToken 必须落 localStorage（key = refreshToken）
    expect(store.refreshToken).toBe('refresh-token-456')
    expect(localStorage.getItem('refreshToken')).toBe('refresh-token-456')
    expect(result).toEqual(mockResponse)
  })

  it('should propagate error and not set token on failed login', async () => {
    vi.mocked(loginApi).mockRejectedValue(new Error('Invalid credentials'))

    const store = useUserStore()
    await expect(store.login('bad', 'creds', '0000', 'captcha-key-2')).rejects.toThrow(
      'Invalid credentials'
    )

    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('should clear token, userInfo and localStorage on logout', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      accessToken: 'login-token-123',
      refreshToken: 'refresh-token-456',
      userInfo: { id: 1, username: 'admin' }
    } as any)
    vi.mocked(logoutApi).mockResolvedValue(undefined as any)

    const store = useUserStore()
    await store.login('admin', 'admin123', '1234', 'captcha-key-1')
    expect(store.token).toBe('login-token-123')

    await store.logout()

    // 契约 T：登出把 refreshToken 带给后端，让其删除 Redis 登记
    expect(logoutApi).toHaveBeenCalledWith('refresh-token-456')
    expect(store.token).toBe('')
    expect(store.refreshToken).toBe('')
    expect(store.userInfo).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
    expect(router.push).toHaveBeenCalledWith('/login')
  })

  it('should pass undefined to logout API when no refreshToken is stored', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      accessToken: 'login-token-123',
      userInfo: { id: 1, username: 'admin' }
    } as any)
    vi.mocked(logoutApi).mockResolvedValue(undefined as any)

    const store = useUserStore()
    await store.login('admin', 'admin123', '1234', 'captcha-key-1')
    await store.logout()

    expect(logoutApi).toHaveBeenCalledWith(undefined)
  })

  // F-10：路由守卫对旧会话只清本地态，不调接口、不跳转（跳转由守卫自己 next('/login')）
  it('clearSession should wipe local state without calling API or router', () => {
    localStorage.setItem('token', 't')
    localStorage.setItem('refreshToken', 'r')
    localStorage.setItem('userInfo', JSON.stringify({ id: 1, username: 'old' }))
    setActivePinia(createPinia())

    const store = useUserStore()
    expect(store.userInfo).not.toBeNull()

    store.clearSession()

    expect(store.token).toBe('')
    expect(store.refreshToken).toBe('')
    expect(store.userInfo).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
    expect(logoutApi).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
  })

  it('setTokens should rotate both tokens and optionally replace userInfo', () => {
    const store = useUserStore()
    store.setTokens('access-1', 'refresh-1', { id: 9, username: 'u9' })
    expect(store.token).toBe('access-1')
    expect(store.refreshToken).toBe('refresh-1')
    expect(store.userInfo?.id).toBe(9)

    // 不传 userInfo 时保留原值；不传 refreshToken 时清掉旧的，避免拿着已轮换失效的 refresh 再去刷新
    store.setTokens('access-2')
    expect(store.token).toBe('access-2')
    expect(store.refreshToken).toBe('')
    expect(localStorage.getItem('refreshToken')).toBeNull()
    expect(store.userInfo?.id).toBe(9)
  })

  it('should still clear state and redirect even if logout API fails', async () => {
    vi.mocked(loginApi).mockResolvedValue({
      accessToken: 'login-token-123',
      userInfo: { id: 1, username: 'admin' }
    } as any)
    vi.mocked(logoutApi).mockRejectedValue(new Error('network down'))

    const store = useUserStore()
    await store.login('admin', 'admin123', '1234', 'captcha-key-1')

    await store.logout()

    // finally-block guarantees cleanup regardless of API failure.
    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
    expect(router.push).toHaveBeenCalledWith('/login')
  })
})
