/**
 * uni storage 的 JSON 封装：所有键集中在这里，避免散落的魔法字符串。
 */
export const STORAGE_KEYS = {
  accessToken: 'sm_access_token',
  refreshToken: 'sm_refresh_token',
  userInfo: 'sm_user_info',
  selectedChildId: 'sm_selected_child_id'
} as const

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS]

export const storageGet = <T>(key: StorageKey): T | null => {
  try {
    const raw: unknown = uni.getStorageSync(key)
    if (raw === '' || raw === null || raw === undefined) return null
    // ponytail: 统一以 JSON 字符串落盘；若历史值不是 JSON（例如裸 token），原样返回而不是抛错清空
    if (typeof raw !== 'string') return raw as T
    try {
      return JSON.parse(raw) as T
    } catch {
      return raw as unknown as T
    }
  } catch {
    return null
  }
}

export const storageSet = (key: StorageKey, value: unknown): void => {
  try {
    uni.setStorageSync(key, JSON.stringify(value))
  } catch {
    // 存储满/隐私模式下静默失败，调用方的内存态仍然有效
  }
}

export const storageRemove = (key: StorageKey): void => {
  try {
    uni.removeStorageSync(key)
  } catch {
    // 忽略
  }
}
