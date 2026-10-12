/** 页面路径集中声明，与 pages.json 保持一致 */
export const PAGES = {
  login: '/pages/login/index',
  home: '/pages/index/index',
  scoreList: '/pages/score/list',
  scoreDetail: '/pages/score/detail',
  trend: '/pages/trend/index',
  notification: '/pages/notification/index',
  mine: '/pages/mine/index'
} as const

export const TAB_PAGES: readonly string[] = [PAGES.home, PAGES.scoreList, PAGES.notification, PAGES.mine]

export const toScoreDetail = (examId: number): void => {
  uni.navigateTo({ url: `${PAGES.scoreDetail}?examId=${examId}` })
}

export const toTrend = (): void => {
  uni.navigateTo({ url: PAGES.trend })
}

export const toHome = (): void => {
  uni.switchTab({ url: PAGES.home })
}

export const toLogin = (): void => {
  uni.reLaunch({ url: PAGES.login })
}

export const toast = (title: string, icon: 'none' | 'success' | 'error' = 'none'): void => {
  uni.showToast({ title, icon, duration: 2200 })
}

export const errorMessage = (err: unknown, fallback = '操作失败'): string =>
  err instanceof Error && err.message ? err.message : fallback
