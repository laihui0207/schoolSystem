import type { LoginResponse, Result } from '@/types'
import { userStore } from '@/store/user'

/**
 * uni.request 封装：
 * - baseURL 来自 VITE_API_BASE（缺省 http://localhost:8080/api/v1，与后端 context-path 对齐）
 * - 自动带 Authorization: Bearer
 * - 解包 Result 信封；非 200 业务码 toast message 并 reject
 * - HTTP 401 → 用 refreshToken 刷一次（POST /auth/refresh?refreshToken=）再重放；失败则清会话跳登录
 */

const DEFAULT_BASE = 'http://localhost:8080/api/v1'
const TIMEOUT_MS = 30000
const LOGIN_PAGE = '/pages/login/index'

export const API_BASE: string = (import.meta.env.VITE_API_BASE ?? DEFAULT_BASE).replace(/\/+$/, '')

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
type QueryValue = string | number | boolean | null | undefined
export type Query = Record<string, QueryValue>

export interface RequestOptions {
  url: string
  method?: HttpMethod
  params?: Query
  data?: unknown
  /** 不带 Authorization（登录/验证码等匿名接口） */
  anonymous?: boolean
  /** 业务码非 200 时不自动 toast（调用方自行提示） */
  silent?: boolean
  /** 内部标记：已重放过一次，不再 refresh */
  _retry?: boolean
}

export class ApiError extends Error {
  readonly code: number
  readonly httpStatus: number
  constructor(message: string, code: number, httpStatus: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

export const buildQuery = (params?: Query): string => {
  if (!params) return ''
  const pairs = Object.entries(params)
    .filter((entry): entry is [string, string | number | boolean] => entry[1] !== undefined && entry[1] !== null && entry[1] !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}

export const buildUrl = (url: string, params?: Query): string => {
  const path = url.startsWith('/') ? url : `/${url}`
  return `${API_BASE}${path}${buildQuery(params)}`
}

export const authHeader = (): Record<string, string> => {
  const token = userStore.accessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

const toast = (title: string): void => {
  uni.showToast({ title, icon: 'none', duration: 2500 })
}

let redirecting = false
export const goLogin = (): void => {
  userStore.clearSession()
  // ponytail: 多个并发 401 只跳一次登录页；reLaunch 清栈避免返回键回到需要登录的页面
  if (redirecting) return
  redirecting = true
  uni.reLaunch({
    url: LOGIN_PAGE,
    complete: () => {
      redirecting = false
    }
  })
}

interface RawResponse {
  statusCode: number
  data: unknown
}

const rawRequest = (options: RequestOptions): Promise<RawResponse> =>
  new Promise((resolve, reject) => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json;charset=utf-8' }
    if (!options.anonymous) Object.assign(headers, authHeader())
    // ponytail: @dcloudio/types 的 method 联合类型没有 PATCH，但 H5(XHR)/App 运行时都支持；
    // 通知"标记已读"只有 PATCH 端点，这里放宽类型。微信小程序 wx.request 不支持 PATCH，见 README 风险说明
    const method = (options.method ?? 'GET') as UniNamespace.RequestOptions['method']
    uni.request({
      url: buildUrl(options.url, options.params),
      method,
      data: options.data === undefined ? undefined : (options.data as UniNamespace.RequestOptions['data']),
      header: headers,
      timeout: TIMEOUT_MS,
      success: (res) => resolve({ statusCode: res.statusCode, data: res.data }),
      fail: (err) => reject(new ApiError(err.errMsg || '网络错误，请检查网络连接', -1, 0))
    })
  })

/** 同一时刻只允许一次刷新在途；后端刷新会轮换 refreshToken，并发第二次必失败 */
let refreshing: Promise<string | null> | null = null

const tryRefreshToken = (): Promise<string | null> => {
  if (refreshing) return refreshing
  const current = userStore.refreshToken()
  if (!current) return Promise.resolve(null)
  refreshing = rawRequest({
    url: '/auth/refresh',
    method: 'POST',
    params: { refreshToken: current },
    anonymous: true
  })
    .then((res) => {
      const body = res.data as Partial<Result<LoginResponse>> | undefined
      const data = body?.data
      if (res.statusCode === 200 && body?.code === 200 && data?.accessToken) {
        userStore.setSession(data)
        return data.accessToken
      }
      return null
    })
    .catch(() => null)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

const isAuthEndpoint = (url: string, name: string): boolean => url.includes(`/auth/${name}`)

const isResult = (body: unknown): body is Result<unknown> =>
  typeof body === 'object' && body !== null && 'code' in body

/**
 * 发起请求并返回 Result.data。
 */
export const request = async <T>(options: RequestOptions): Promise<T> => {
  const res = await rawRequest(options)

  if (res.statusCode === 401) {
    const canRefresh =
      !options._retry &&
      !options.anonymous &&
      !isAuthEndpoint(options.url, 'login') &&
      !isAuthEndpoint(options.url, 'refresh') &&
      !isAuthEndpoint(options.url, 'logout')
    if (canRefresh) {
      const newToken = await tryRefreshToken()
      if (newToken) return request<T>({ ...options, _retry: true })
    }
    if (!isAuthEndpoint(options.url, 'logout') && !isAuthEndpoint(options.url, 'login')) {
      toast('登录已过期，请重新登录')
      goLogin()
    }
    throw new ApiError('未授权', 1001, 401)
  }

  if (res.statusCode === 403) {
    if (!options.silent) toast('拒绝访问')
    throw new ApiError('拒绝访问', 1101, 403)
  }

  if (res.statusCode === 404) {
    if (!options.silent) toast('请求地址不存在')
    throw new ApiError('请求地址不存在', 404, 404)
  }

  if (res.statusCode >= 500) {
    if (!options.silent) toast('服务器内部错误')
    throw new ApiError('服务器内部错误', res.statusCode, res.statusCode)
  }

  if (!isResult(res.data)) {
    const msg = res.statusCode === 200 ? '响应格式错误' : `请求失败（${res.statusCode}）`
    if (!options.silent) toast(msg)
    throw new ApiError(msg, res.statusCode, res.statusCode)
  }

  const body = res.data
  // ponytail: BusinessException 走 HTTP 200 + 业务 code；Bean 校验失败走 HTTP 400 + message，两者都在这里统一成 ApiError
  if (body.code === 200) return body.data as T
  const message = body.message || '请求失败'
  if (!options.silent) toast(message)
  throw new ApiError(message, body.code, res.statusCode)
}

export const get = <T>(url: string, params?: Query, extra?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...extra, url, method: 'GET', params })

export const post = <T>(url: string, data?: unknown, params?: Query, extra?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...extra, url, method: 'POST', data, params })

export const patch = <T>(url: string, data?: unknown, params?: Query, extra?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...extra, url, method: 'PATCH', data, params })

export const del = <T>(url: string, params?: Query, extra?: Partial<RequestOptions>): Promise<T> =>
  request<T>({ ...extra, url, method: 'DELETE', params })

export default request
