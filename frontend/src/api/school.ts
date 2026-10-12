import request from '@/utils/request'

export interface School {
  id?: number
  schoolName: string
  schoolCode: string
  /** 区域编码；同一编码的学校可在「区域分析」里跨校对比 */
  regionCode?: string
  address?: string
  contactPhone?: string
  contactPerson?: string
  status: number
}

export interface SchoolQuery {
  schoolName?: string
  status?: number
  pageNum: number
  pageSize: number
}

/**
 * 分页查询学校列表
 */
export const getSchoolList = (params: SchoolQuery) => {
  return request({
    url: '/schools',
    method: 'get',
    params
  })
}

/**
 * 获取学校详情
 */
export const getSchoolDetail = (id: number) => {
  return request({
    url: `/schools/${id}`,
    method: 'get'
  })
}

/**
 * 创建学校
 */
export const createSchool = (data: School) => {
  return request({
    url: '/schools',
    method: 'post',
    data
  })
}

/**
 * 更新学校
 */
export const updateSchool = (id: number, data: School) => {
  return request({
    url: `/schools/${id}`,
    method: 'put',
    data
  })
}

/**
 * 更新学校状态
 *
 * ponytail: 学校被年级/班级/学生/成绩大量外键引用，后端刻意不提供删除，
 * 停用（status=0）即为业务上的「移除」
 */
export const updateSchoolStatus = (id: number, status: number) => {
  return request({
    url: `/schools/${id}/status`,
    method: 'patch',
    params: { status }
  })
}
