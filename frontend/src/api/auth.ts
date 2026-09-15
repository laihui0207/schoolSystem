import request from '@/utils/request'

export interface LoginParams {
  username: string
  password: string
  captcha: string
  captchaKey: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  userInfo: any
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
export const logout = () => {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

/**
 * 刷新token
 */
export const refreshToken = () => {
  return request({
    url: '/auth/refresh',
    method: 'post'
  })
}
