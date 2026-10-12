import request from '@/utils/request'

export interface Notification {
  id?: number
  title: string
  content: string
  /** 1-成绩发布 2-成绩预警 3-系统通知（后端 Notification.type 为 Integer） */
  // ponytail: 后端实体与 NotificationResponse.type 都是 Integer，string 会让 typeTagMap 命中失败
  type: number
  userId?: number
  /** 关联业务 id（如考试 id），对应 NotificationResponse.relatedId */
  relatedId?: number
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
  return request<number>({
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
