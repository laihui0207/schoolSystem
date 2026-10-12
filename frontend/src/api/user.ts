import request from '@/utils/request'
import type { PageQuery } from '@/types/common'

export interface User {
  id?: number
  username: string
  realName: string
  role: 'admin' | 'teacher' | 'student'
  schoolId?: number
  schoolName?: string
  phone?: string
  email?: string
  password?: string
  status: 0 | 1  // 0-禁用 1-启用
  createdAt?: string
}

export interface UserQuery extends PageQuery {
  username?: string
  realName?: string
  role?: string
  schoolId?: number
  status?: number
}

/**
 * 分页查询用户列表
 */
export const getUserList = (params: UserQuery) => {
  return request({
    url: '/users',
    method: 'get',
    params
  })
}

/**
 * 获取用户详情
 */
export const getUserDetail = (id: number) => {
  return request({
    url: `/users/${id}`,
    method: 'get'
  })
}

/**
 * 创建用户
 */
export const createUser = (data: User) => {
  return request({
    url: '/users',
    method: 'post',
    data
  })
}

/**
 * 更新用户
 */
export const updateUser = (id: number, data: User) => {
  return request({
    url: `/users/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除用户
 */
export const deleteUser = (id: number) => {
  return request({
    url: `/users/${id}`,
    method: 'delete'
  })
}

/**
 * 更新用户状态
 */
export const updateUserStatus = (id: number, status: number) => {
  return request({
    url: `/users/${id}/status`,
    method: 'patch',
    params: { status }
  })
}

/**
 * 重置用户密码
 */
export const resetPassword = (id: number, password: string) => {
  return request({
    url: `/users/${id}/password`,
    method: 'patch',
    data: { password }
  })
}

