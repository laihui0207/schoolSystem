import type { CaptchaResponse, LoginParams, LoginResponse, PhoneLoginParams } from '@/types'
import { ApiError, get, post } from '@/utils/request'

/** GET /auth/captcha —— 匿名 */
export const getCaptcha = (): Promise<CaptchaResponse> =>
  get<CaptchaResponse>('/auth/captcha', undefined, { anonymous: true, silent: true })

/** POST /auth/login —— 后端关闭验证码时 captcha/captchaKey 可缺省 */
export const login = (data: LoginParams): Promise<LoginResponse> =>
  post<LoginResponse>('/auth/login', data, undefined, { anonymous: true, silent: true })

/**
 * POST /auth/login/phone —— E 组可能提供；不存在时 404 → ApiError(httpStatus 404)。
 * 调用方用 try/catch 判断是否隐藏手机登录 Tab。
 */
export const loginByPhone = (data: PhoneLoginParams): Promise<LoginResponse> =>
  post<LoginResponse>('/auth/login/phone', data, undefined, { anonymous: true, silent: true })

/** POST /auth/sms/send?phone= —— 与 loginByPhone 配套；端点名若与 E 组不符，在此处集中修改 */
export const sendSmsCode = (phone: string): Promise<void> =>
  post<void>('/auth/sms/send', undefined, { phone }, { anonymous: true, silent: true })

/**
 * 探测手机登录端点是否存在：匿名发一个空体请求。
 * ponytail: 实测不存在的 /auth/login/phone 返回的是 401 而不是 404 —— 该路径不在 SecurityConfig.WHITE_LIST，
 * 匿名请求先被 JWT 入口拒绝，根本到不了 DispatcherServlet。E 组落地时必然把它加进白名单，
 * 届时空体请求会得到 400（Bean 校验）或 200+业务码。因此只有 400/200 才算"存在"，401/404/网络错误都算不存在
 */
export const probePhoneLogin = async (): Promise<boolean> => {
  try {
    await post<unknown>('/auth/login/phone', {}, undefined, { anonymous: true, silent: true })
    return true
  } catch (err) {
    if (err instanceof ApiError) return err.httpStatus === 400 || err.httpStatus === 200
    return false
  }
}

/** POST /auth/logout —— 需要带当前 Bearer；后端会删除 Redis token:<jwt>，之后所有请求 401 */
export const logout = (): Promise<void> => post<void>('/auth/logout', undefined, undefined, { silent: true })

export const refreshToken = (token: string): Promise<LoginResponse> =>
  post<LoginResponse>('/auth/refresh', undefined, { refreshToken: token }, { anonymous: true, silent: true })
