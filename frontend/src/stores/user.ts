import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, logout as logoutApi } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<any>(JSON.parse(localStorage.getItem('userInfo') || 'null'))

  // 登录
  const login = async (username: string, password: string, captcha: string, captchaKey: string) => {
    try {
      const data = await loginApi({ username, password, captcha, captchaKey })
      token.value = data.accessToken
      userInfo.value = data.userInfo
      localStorage.setItem('token', data.accessToken)
      localStorage.setItem('userInfo', JSON.stringify(data.userInfo))
      return data
    } catch (error) {
      throw error
    }
  }

  // 登出
  const logout = async () => {
    try {
      await logoutApi()
    } catch (error) {
      console.error('登出失败', error)
    } finally {
      token.value = ''
      userInfo.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      router.push('/login')
    }
  }

  return {
    token,
    userInfo,
    login,
    logout
  }
})
