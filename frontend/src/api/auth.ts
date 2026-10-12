import request from '@/utils/request'
import type { UserInfo } from '@/stores/user'

export interface LoginParams {
  username: string
  password: string
  captcha: string
  captchaKey: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfo
}

export interface CaptchaResponse {
  captchaKey: string
  captchaImage: string
  expiresIn: number
}

/**
 * 获取验证码
 */
export const getCaptcha = () => {
  return request<CaptchaResponse>({
    url: '/auth/captcha',
    method: 'get'
  })
}

/**
 * 登录
 */
export const login = (data: LoginParams) => {
  return request<LoginResponse>({
    url: '/auth/login',
    method: 'post',
    data
  })
}

/**
 * 登出
 */
// ponytail: 契约 T — 登出把 refreshToken 一并带给后端，让其删除 Redis 里的 refresh 登记（参数可选，后端兼容不传）
export const logout = (refreshToken?: string) => {
  return request({
    url: '/auth/logout',
    method: 'post',
    params: refreshToken ? { refreshToken } : undefined
  })
}

/**
 * 刷新token
 */
export const refreshToken = (token: string) => {
  return request<LoginResponse>({
    url: '/auth/refresh',
    method: 'post',
    params: { refreshToken: token }
  })
}
