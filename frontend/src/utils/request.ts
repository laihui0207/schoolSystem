import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AnyData = any

/** 原请求配置 + 已重放标记，防止 401 → refresh → 重放再 401 时无限循环 */
type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

const BASE_URL = '/api/v1'
const TIMEOUT = 30000

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// ponytail: 契约 T — 刷新 token 用独立实例，不挂 service 的拦截器，避免 refresh 自身 401 时再触发 refresh 递归
const refreshClient: AxiosInstance = axios.create({ baseURL: BASE_URL, timeout: TIMEOUT })

/** 同一时刻只允许一次刷新在途；并发 401 共用同一个 Promise，否则后端轮换后第二次刷新必失败 */
let refreshing: Promise<string | null> | null = null

const tryRefreshToken = (): Promise<string | null> => {
  if (refreshing) return refreshing
  const userStore = useUserStore()
  const currentRefreshToken = userStore.refreshToken
  if (!currentRefreshToken) return Promise.resolve(null)

  refreshing = refreshClient
    .post('/auth/refresh', null, { params: { refreshToken: currentRefreshToken } })
    .then((resp) => {
      const body = resp.data
      const data = body?.data
      if ((body?.code === 200 || body?.code === 0) && data?.accessToken) {
        userStore.setTokens(data.accessToken, data.refreshToken, data.userInfo)
        return data.accessToken as string
      }
      return null
    })
    .catch(() => null)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

const isAuthEndpoint = (url: string | undefined, name: string) => !!url && url.includes('/auth/' + name)

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data

    // 文件流（Excel/PDF 下载）没有统一响应包装，直接返回 Blob
    if (response.config.responseType === 'blob') {
      return res
    }

    // 根据后端统一响应格式处理
    if (res.code === 200 || res.code === 0) {
      return res.data
    } else {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  async (error) => {
    if (error.response) {
      const config = error.config as RetryableConfig | undefined
      switch (error.response.status) {
        case 401: {
          // ponytail: 契约 T — 先用 refreshToken 静默续期一次并重放原请求；login/refresh/logout 自身的 401 不续期
          const canRefresh = !!config && !config._retry
            && !isAuthEndpoint(config.url, 'login')
            && !isAuthEndpoint(config.url, 'refresh')
            && !isAuthEndpoint(config.url, 'logout')
          if (canRefresh) {
            const newToken = await tryRefreshToken()
            if (newToken) {
              config._retry = true
              config.headers.Authorization = 'Bearer ' + newToken
              return service.request(config)
            }
          }
          // ponytail: logout 接口自身 401 时不要再调 logout()，否则 logout → 401 → logout 死循环
          if (!isAuthEndpoint(config?.url, 'logout')) {
            ElMessage.error('未授权，请重新登录')
            useUserStore().logout()
          }
          break
        }
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          ElMessage.error('请求地址不存在')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(error.response.data.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

/**
 * 类型安全的请求封装
 *
 * 响应拦截器已在运行时解包并返回后端的 `data` 字段，
 * 因此这里将返回类型声明为 `Promise<T>`，与运行时行为保持一致，
 * 避免各业务模块使用 `as` 强制转换。
 */
const request = <T = AnyData>(config: AxiosRequestConfig): Promise<T> => {
  return service.request<AnyData, T>(config) as unknown as Promise<T>
}

export default request
export { service, refreshClient }
