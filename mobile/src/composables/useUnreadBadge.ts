import { ref } from 'vue'
import { getUnreadCount } from '@/api/notification'

/** tabBar 中"消息"的索引，与 pages.json tabBar.list 顺序一致 */
const MESSAGE_TAB_INDEX = 2

const unreadCount = ref(0)

const applyBadge = (count: number): void => {
  // ponytail: 非 tab 页（登录页）调用 setTabBarBadge 会 fail，吞掉即可；角标只是辅助提示
  if (count > 0) {
    uni.setTabBarBadge({ index: MESSAGE_TAB_INDEX, text: count > 99 ? '99+' : String(count), fail: () => undefined })
  } else {
    uni.removeTabBarBadge({ index: MESSAGE_TAB_INDEX, fail: () => undefined })
  }
}

/** 拉取未读数并同步 tabBar 角标；失败时保持原值 */
export const refreshUnreadBadge = async (): Promise<number> => {
  try {
    const count = await getUnreadCount()
    unreadCount.value = count ?? 0
  } catch {
    // 未登录/网络失败：保持上次值
  }
  applyBadge(unreadCount.value)
  return unreadCount.value
}

export const setUnreadCount = (count: number): void => {
  unreadCount.value = Math.max(0, count)
  applyBadge(unreadCount.value)
}

export const useUnreadBadge = () => ({ unreadCount, refreshUnreadBadge, setUnreadCount })
