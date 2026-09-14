import request from '@/utils/request'

/**
 * 对应后端 GradeResponse
 *
 * 后端只有 gradeCode（年级序号，Integer），没有 gradeOrder / description /
 * schoolName —— 那三个字段在任何 Grade DTO 和实体里都不存在
 */
export interface Grade {
  id?: number
  schoolId?: number
  gradeName: string
  gradeCode?: number
  createdAt?: string
  updatedAt?: string
}

export interface GradeQuery {
  gradeName?: string
  schoolId?: number
  pageNum: number
  pageSize: number
}

export interface GradePage {
  records: Grade[]
  total: number
}

/**
 * 分页查询年级列表
 */
export const getGradeList = (params: GradeQuery) => {
  return request<GradePage>({
    url: '/grades',
    method: 'get',
    params
  })
}

/**
 * 获取年级详情
 */
export const getGradeDetail = (id: number) => {
  return request<Grade>({
    url: `/grades/${id}`,
    method: 'get'
  })
}

/**
 * 创建年级
 *
 * ponytail: 只发后端 GradeCreateRequest 认的字段。
 * schoolId / gradeName / gradeCode 都是必填，漏 gradeCode 直接 400
 */
export const createGrade = (data: Grade) => {
  return request<Grade>({
    url: '/grades',
    method: 'post',
    data: {
      schoolId: data.schoolId,
      gradeName: data.gradeName,
      gradeCode: data.gradeCode
    }
  })
}

/**
 * 更新年级（后端不允许改 schoolId）
 */
export const updateGrade = (id: number, data: Grade) => {
  return request<Grade>({
    url: `/grades/${id}`,
    method: 'put',
    data: {
      gradeName: data.gradeName,
      gradeCode: data.gradeCode
    }
  })
}

/**
 * 删除年级
 */
export const deleteGrade = (id: number) => {
  return request({
    url: `/grades/${id}`,
    method: 'delete'
  })
}

/**
 * 根据学校ID获取年级列表（不分页，后端直接返回数组）
 */
export const getGradesBySchool = (schoolId: number) => {
  return request<Grade[]>({
    url: `/grades/school/${schoolId}`,
    method: 'get'
  })
}
