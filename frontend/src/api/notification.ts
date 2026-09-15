import request from '@/utils/request'

export interface Notification {
  id?: number
  title: string
  content: string
  type: string
  userId?: number
}

export interface NotificationQuery {
  pageNum: number
  pageSize: number
  userId?: number
  type?: string
  isRead?: number
}

/**
 * 分页查询通知列表
 */
export const getNotificationList = (params: NotificationQuery) => {
  return request({
    url: '/notifications',
    method: 'get',
    params
  })
}

/**
 * 获取通知详情
 */
export const getNotificationDetail = (id: number) => {
  return request({
    url: `/notifications/${id}`,
    method: 'get'
  })
}

/**
 * 创建通知
 */
export const createNotification = (data: Notification) => {
  return request({
    url: '/notifications',
    method: 'post',
    data
  })
}

/**
 * 标记已读
 */
export const markAsRead = (id: number) => {
  return request({
    url: `/notifications/${id}/read`,
    method: 'patch'
  })
}

/**
 * 全部标记已读
 */
export const markAllAsRead = () => {
  return request({
    url: '/notifications/read-all',
    method: 'patch'
  })
}

/**
 * 获取未读数量
 */
export const getUnreadCount = () => {
  return request({
    url: '/notifications/unread-count',
    method: 'get'
  })
}

/**
 * 删除通知
 */
export const deleteNotification = (id: number) => {
  return request({
    url: `/notifications/${id}`,
    method: 'delete'
  })
}
