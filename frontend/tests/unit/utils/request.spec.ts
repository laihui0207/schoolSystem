import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// Mock element-plus before any imports that use it
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  }
}))

// 401 续期失败会走 userStore.logout() → router.push，这里不需要真路由表
vi.mock('@/router', () => ({
  default: {
    push: vi.fn()
  }
}))

import { ElMessage } from 'element-plus'
import { service, refreshClient } from '@/utils/request'
import { useUserStore } from '@/stores/user'

/**
 * Request utility tests.
 *
 * Instead of pulling in the (uninstalled) `axios-mock-adapter`, we inject a
 * custom axios adapter onto the real `service` instance. This keeps the real
 * request/response interceptor logic under test while stubbing the transport
 * layer. An adapter returns a Promise<AxiosResponse> (resolve = 2xx path) or
 * rejects with an axios-style error (`{ response }` for HTTP errors, no
 * `response` for network errors).
 */

type AdapterHandler = (config: any) =>
  | { status: number; data: any }
  | { networkError: true }
  | { httpError: number; data?: any }

let handler: AdapterHandler
let refreshHandler: AdapterHandler
let originalAdapter: any
let originalRefreshAdapter: any

// Register a single custom adapter that delegates to the given handler getter.
const makeAdapter = (getHandler: () => AdapterHandler) => async (config: any) => {
  const result = getHandler()(config)

  if ('networkError' in result) {
    // No `response` field -> triggers the "network error" branch.
    return Promise.reject(new Error('Network Error'))
  }

  if ('httpError' in result) {
    const error: any = new Error(`Request failed with status code ${result.httpError}`)
    // 真实 AxiosError 同时带 config 与 response；401 续期逻辑要从 error.config 取原请求重放
    error.config = config
    error.response = {
      status: result.httpError,
      data: result.data ?? {},
      config
    }
    return Promise.reject(error)
  }

  return {
    data: result.data,
    status: result.status,
    statusText: 'OK',
    headers: {},
    config
  }
}

const customAdapter = makeAdapter(() => handler)
const customRefreshAdapter = makeAdapter(() => refreshHandler)

describe('Request Utility', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    handler = () => ({ status: 200, data: { code: 200, data: null } })
    refreshHandler = () => ({ httpError: 401, data: { code: 401, message: 'refresh token 无效' } })

    // Save original adapters and install custom ones (service + refreshClient)
    originalAdapter = (service as any).defaults?.adapter
    if ((service as any).defaults) {
      (service as any).defaults.adapter = customAdapter
    }
    originalRefreshAdapter = (refreshClient as any).defaults?.adapter
    if ((refreshClient as any).defaults) {
      (refreshClient as any).defaults.adapter = customRefreshAdapter
    }
  })

  afterEach(() => {
    vi.clearAllMocks()
    // Restore original adapters
    if ((service as any).defaults && originalAdapter !== undefined) {
      (service as any).defaults.adapter = originalAdapter
    }
    if ((refreshClient as any).defaults && originalRefreshAdapter !== undefined) {
      (refreshClient as any).defaults.adapter = originalRefreshAdapter
    }
  })

  it('should unwrap data on a successful GET (code 200)', async () => {
    const mockData = { id: 1, name: 'Test' }
    handler = () => ({ status: 200, data: { code: 200, data: mockData, message: 'success' } })

    const result = await service.get('/test')
    expect(result).toEqual(mockData)
  })

  it('should unwrap data on a successful POST (code 0)', async () => {
    const mockResponse = { token: 'mock-token' }
    handler = (config) => {
      expect(config.method).toBe('post')
      expect(config.url).toBe('/login')
      return { status: 200, data: { code: 0, data: mockResponse } }
    }

    const result = await service.post('/login', { username: 'test', password: '123456' })
    expect(result).toEqual(mockResponse)
  })

  it('should reject and surface message when business code !== 200', async () => {
    handler = () => ({ status: 200, data: { code: 400, message: 'Bad Request' } })

    await expect(service.get('/error')).rejects.toThrow('Bad Request')
    expect(ElMessage.error).toHaveBeenCalledWith('Bad Request')
  })

  it('should handle 404 HTTP error', async () => {
    handler = () => ({ httpError: 404, data: { message: 'Not Found' } })

    await expect(service.get('/notfound')).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith('请求地址不存在')
  })

  it('should handle 500 HTTP error', async () => {
    handler = () => ({ httpError: 500, data: { message: 'Internal Server Error' } })

    await expect(service.get('/servererror')).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith('服务器内部错误')
  })

  it('should handle 403 HTTP error', async () => {
    handler = () => ({ httpError: 403, data: {} })

    await expect(service.get('/forbidden')).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith('拒绝访问')
  })

  it('should handle a network error (no response)', async () => {
    handler = () => ({ networkError: true })

    await expect(service.get('/network-error')).rejects.toBeTruthy()
    expect(ElMessage.error).toHaveBeenCalledWith('网络错误，请检查网络连接')
  })

  it('should attach Authorization header when a token exists', async () => {
    const store = useUserStore()
    store.token = 'test-token'

    handler = (config) => {
      expect(config.headers.Authorization).toBe('Bearer test-token')
      return { status: 200, data: { code: 200, data: { success: true } } }
    }

    const result = await service.get('/protected')
    expect(result).toEqual({ success: true })
  })

  it('should not attach Authorization header when no token', async () => {
    handler = (config) => {
      expect(config.headers.Authorization).toBeUndefined()
      return { status: 200, data: { code: 200, data: {} } }
    }

    await service.get('/public')
  })

  // ---- 契约 T：401 静默续期 ----

  it('should refresh once and replay the original request on 401', async () => {
    const store = useUserStore()
    store.setTokens('expired-access', 'valid-refresh')

    const seenAuth: string[] = []
    handler = (config) => {
      seenAuth.push(config.headers.Authorization)
      // 第一次拿旧 token → 401；重放时拿新 token → 200
      if (config.headers.Authorization === 'Bearer expired-access') {
        return { httpError: 401, data: { code: 401, message: 'token 过期' } }
      }
      return { status: 200, data: { code: 200, data: { ok: true } } }
    }

    let refreshCalls = 0
    refreshHandler = (config) => {
      refreshCalls += 1
      expect(config.url).toBe('/auth/refresh')
      expect(config.params).toEqual({ refreshToken: 'valid-refresh' })
      return {
        status: 200,
        data: { code: 200, data: { accessToken: 'new-access', refreshToken: 'new-refresh', userInfo: { id: 1, username: 'u' } } }
      }
    }

    const result = await service.get('/protected')

    expect(result).toEqual({ ok: true })
    expect(refreshCalls).toBe(1)
    expect(seenAuth).toEqual(['Bearer expired-access', 'Bearer new-access'])
    // 新 token 对已轮换进 store / localStorage
    expect(store.token).toBe('new-access')
    expect(store.refreshToken).toBe('new-refresh')
    expect(localStorage.getItem('refreshToken')).toBe('new-refresh')
    // 续期成功时不应弹「未授权」
    expect(ElMessage.error).not.toHaveBeenCalledWith('未授权，请重新登录')
  })

  it('should logout when refresh fails after 401', async () => {
    const store = useUserStore()
    store.setTokens('expired-access', 'stale-refresh')

    handler = (config) => {
      // logout 本身返回 200，避免再进 401 分支
      if (String(config.url).includes('/auth/logout')) {
        return { status: 200, data: { code: 200, data: null } }
      }
      return { httpError: 401, data: { code: 401, message: 'token 过期' } }
    }
    refreshHandler = () => ({ httpError: 401, data: { code: 401, message: 'refresh 已失效' } })

    await expect(service.get('/protected')).rejects.toBeTruthy()

    expect(ElMessage.error).toHaveBeenCalledWith('未授权，请重新登录')
    // logout 清掉本地登录态
    expect(store.token).toBe('')
    expect(store.refreshToken).toBe('')
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('should logout directly on 401 when no refreshToken is stored', async () => {
    const store = useUserStore()
    store.token = 'expired-access'

    let refreshCalls = 0
    refreshHandler = () => {
      refreshCalls += 1
      return { status: 200, data: { code: 200, data: { accessToken: 'x' } } }
    }
    handler = (config) => {
      if (String(config.url).includes('/auth/logout')) {
        return { status: 200, data: { code: 200, data: null } }
      }
      return { httpError: 401, data: {} }
    }

    await expect(service.get('/protected')).rejects.toBeTruthy()

    expect(refreshCalls).toBe(0)
    expect(ElMessage.error).toHaveBeenCalledWith('未授权，请重新登录')
    expect(store.token).toBe('')
  })

  it('should not try to refresh when the 401 comes from /auth/login itself', async () => {
    const store = useUserStore()
    store.setTokens('', 'some-refresh')

    let refreshCalls = 0
    refreshHandler = () => {
      refreshCalls += 1
      return { status: 200, data: { code: 200, data: { accessToken: 'x' } } }
    }
    handler = (config) => {
      if (String(config.url).includes('/auth/logout')) {
        return { status: 200, data: { code: 200, data: null } }
      }
      return { httpError: 401, data: { code: 401, message: '用户名或密码错误' } }
    }

    await expect(service.post('/auth/login', {})).rejects.toBeTruthy()
    expect(refreshCalls).toBe(0)
  })
})
