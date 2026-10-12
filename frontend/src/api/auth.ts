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

/** 短信验证码场景，与后端 SmsScene 一致 */
export type SmsScene = 'login' | 'register' | 'reset'

export interface SmsSendParams {
  phone: string
  scene: SmsScene
}

export interface SmsSendResponse {
  /** 验证码有效期（秒） */
  expiresIn: number
  /** 下次可发送的间隔（秒），前端倒计时用 */
  sendInterval: number
}

export interface PhoneLoginParams {
  phone: string
  code: string
}

export type RegisterRole = 'student' | 'parent'

export interface RegisterParams {
  phone: string
  code: string
  password: string
  realName: string
  role: RegisterRole
  schoolId: number
  /** 学生可选：匹配本校学号且姓名一致时自动绑定学生档案 */
  studentNo?: string
}

/** 注册页学校下拉项（匿名可读，仅 id + 名称） */
export interface PublicSchool {
  id: number
  schoolName: string
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
 * 发送短信验证码
 */
export const sendSmsCode = (data: SmsSendParams) => {
  return request<SmsSendResponse>({
    url: '/auth/sms/send',
    method: 'post',
    data
  })
}

/**
 * 手机号 + 短信验证码登录（返回结构与账号密码登录完全一致）
 */
export const loginByPhone = (data: PhoneLoginParams) => {
  return request<LoginResponse>({
    url: '/auth/login/phone',
    method: 'post',
    data
  })
}

/**
 * 学生 / 家长自助注册，成功即返回登录态
 */
export const register = (data: RegisterParams) => {
  return request<LoginResponse>({
    url: '/auth/register',
    method: 'post',
    data
  })
}

/**
 * 注册页学校下拉（无需登录）
 */
export const getPublicSchools = () => {
  return request<PublicSchool[]>({
    url: '/auth/schools',
    method: 'get'
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
