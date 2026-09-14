import request from '@/utils/request'

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  userInfo: any
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
