import { computed, reactive, readonly } from 'vue'
import type { LoginResponse, UserInfo } from '@/types'
import { USER_TYPE_PARENT, USER_TYPE_STUDENT } from '@/types'
import { STORAGE_KEYS, storageGet, storageRemove, storageSet } from '@/utils/storage'

/**
 * 会话状态（不引入 pinia：移动端只有这一份全局状态，reactive 单例足够且少一个依赖）。
 * ponytail: 本模块只依赖 types/storage，request.ts 依赖本模块；不要在这里 import api/request，否则循环引用
 */
interface SessionState {
  accessToken: string
  refreshToken: string
  userInfo: UserInfo | null
  /** 家长当前选中的孩子 student.id；null 表示未选（取 childStudentIds[0]） */
  selectedChildId: number | null
}

const state = reactive<SessionState>({
  accessToken: storageGet<string>(STORAGE_KEYS.accessToken) ?? '',
  refreshToken: storageGet<string>(STORAGE_KEYS.refreshToken) ?? '',
  userInfo: storageGet<UserInfo>(STORAGE_KEYS.userInfo),
  selectedChildId: storageGet<number>(STORAGE_KEYS.selectedChildId)
})

const userInfo = computed(() => state.userInfo)
const isLoggedIn = computed(() => state.accessToken !== '' && state.userInfo !== null)
const isParent = computed(() => state.userInfo?.userType === USER_TYPE_PARENT)
const isStudent = computed(() => state.userInfo?.userType === USER_TYPE_STUDENT)
const childStudentIds = computed<number[]>(() => state.userInfo?.childStudentIds ?? [])

/** 家长当前生效的孩子：显式选择优先，否则第一个；选择值已不在列表中时回退 */
const selectedChildId = computed<number | null>(() => {
  const ids = childStudentIds.value
  if (ids.length === 0) return null
  const chosen = state.selectedChildId
  if (chosen !== null && ids.includes(chosen)) return chosen
  return ids[0] ?? null
})

const setTokens = (accessToken: string, refreshToken: string): void => {
  state.accessToken = accessToken
  state.refreshToken = refreshToken
  storageSet(STORAGE_KEYS.accessToken, accessToken)
  storageSet(STORAGE_KEYS.refreshToken, refreshToken)
}

const setSession = (login: LoginResponse): void => {
  setTokens(login.accessToken, login.refreshToken)
  state.userInfo = { ...login.userInfo }
  storageSet(STORAGE_KEYS.userInfo, state.userInfo)
}

const selectChild = (studentId: number): void => {
  state.selectedChildId = studentId
  storageSet(STORAGE_KEYS.selectedChildId, studentId)
}

const clearSession = (): void => {
  state.accessToken = ''
  state.refreshToken = ''
  state.userInfo = null
  state.selectedChildId = null
  storageRemove(STORAGE_KEYS.accessToken)
  storageRemove(STORAGE_KEYS.refreshToken)
  storageRemove(STORAGE_KEYS.userInfo)
  storageRemove(STORAGE_KEYS.selectedChildId)
}

export const userStore = {
  state: readonly(state),
  userInfo,
  isLoggedIn,
  isParent,
  isStudent,
  childStudentIds,
  selectedChildId,
  accessToken: (): string => state.accessToken,
  refreshToken: (): string => state.refreshToken,
  setTokens,
  setSession,
  selectChild,
  clearSession
}

export const useUserStore = () => userStore
