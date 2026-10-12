import type { NotificationResponse, Page } from '@/types'
import { get, patch } from '@/utils/request'

export interface NotificationQuery {
  isRead?: number
  type?: number
  pageNum?: number
  pageSize?: number
}

/** GET /notifications —— 后端强制为当前用户的收件箱 */
export const listNotifications = (query: NotificationQuery = {}): Promise<Page<NotificationResponse>> =>
  get<Page<NotificationResponse>>('/notifications', {
    isRead: query.isRead,
    type: query.type,
    pageNum: query.pageNum ?? 1,
    pageSize: query.pageSize ?? 20
  })

/** GET /notifications/unread-count */
export const getUnreadCount = (): Promise<number> => get<number>('/notifications/unread-count', undefined, { silent: true })

/** PATCH /notifications/{id}/read */
export const markAsRead = (id: number): Promise<void> => patch<void>(`/notifications/${id}/read`)

/** PATCH /notifications/read-all */
export const markAllAsRead = (): Promise<void> => patch<void>('/notifications/read-all')
