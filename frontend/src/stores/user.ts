import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import router from '@/router'
import { handleError } from '@/utils/errorHandler'

export interface UserInfo {
  id: number
  username: string
  realName?: string
  email?: string
  phone?: string
  /** UserRole.code：super_admin/admin/teacher/student/parent */
  role?: string
  avatar?: string
  schoolId?: number
  /** 1 超管 2 校管 3 教师 4 学生 5 家长 */
  userType?: number
  // ponytail: 契约 2 — 用户 id ≠ 学生/教师档案 id，学生自查成绩一律用 studentId，非学生为 null
  studentId?: number | null
  teacherId?: number | null
  headTeacher?: boolean
  // ponytail: 契约 L — 家长绑定的学生 id（student.id）列表，非家长为空数组；孩子下拉以 /parents/me/children 实时为准
  childStudentIds?: number[]
}

const REFRESH_TOKEN_KEY = 'refreshToken'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  // ponytail: 契约 T — refreshToken 落 localStorage，供 401 时静默续期与登出时通知后端删除登记
  const refreshToken = ref<string>(localStorage.getItem(REFRESH_TOKEN_KEY) || '')
  const userInfo = ref<UserInfo | null>(
    JSON.parse(localStorage.getItem('userInfo') || 'null')
  )

  /**
   * 写入一对新 token（登录 / 刷新共用）；userInfo 传了才覆盖
   */
  const setTokens = (accessToken: string, newRefreshToken?: string, newUserInfo?: UserInfo) => {
    token.value = accessToken
    localStorage.setItem('token', accessToken)
    if (newRefreshToken) {
      refreshToken.value = newRefreshToken
      localStorage.setItem(REFRESH_TOKEN_KEY, newRefreshToken)
    } else {
      refreshToken.value = ''
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
    if (newUserInfo) {
      userInfo.value = newUserInfo
      localStorage.setItem('userInfo', JSON.stringify(newUserInfo))
    }
  }

  /**
   * 只清本地登录态，不调接口也不跳转（路由守卫、拦截器复用）
   */
  const clearSession = () => {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem('userInfo')
  }

  // 登录
  const login = async (username: string, password: string, captcha: string, captchaKey: string) => {
    try {
      const data = await loginApi({ username, password, captcha, captchaKey })
      setTokens(data.accessToken, data.refreshToken, data.userInfo)
      return data
    } catch (error) {
      throw error
    }
  }

  // 登出
  const logout = async () => {
    try {
      await logoutApi(refreshToken.value || undefined)
    } catch (error) {
      handleError(error, '登出失败')
    } finally {
      clearSession()
      router.push('/login')
    }
  }

  return {
    token,
    refreshToken,
    userInfo,
    login,
    logout,
    setTokens,
    clearSession
  }
})
