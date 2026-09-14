import request from '@/utils/request'

export interface School {
  id?: number
  schoolName: string
  schoolCode: string
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
 * 删除学校
 */
export const deleteSchool = (id: number) => {
  return request({
    url: `/schools/${id}`,
    method: 'delete'
  })
}

/**
 * 更新学校状态
 */
export const updateSchoolStatus = (id: number, status: number) => {
  return request({
    url: `/schools/${id}/status`,
    method: 'patch',
    params: { status }
  })
}
