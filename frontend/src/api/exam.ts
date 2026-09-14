import request from '@/utils/request'

/**
 * 分页查询考试列表
 */
export const getExamList = (params: any) => {
  return request({
    url: '/exams',
    method: 'get',
    params
  })
}

/**
 * 获取考试详情
 */
export const getExamDetail = (id: number) => {
  return request({
    url: `/exams/${id}`,
    method: 'get'
  })
}

/**
 * 创建考试
 */
export const createExam = (data: any) => {
  return request({
    url: '/exams',
    method: 'post',
    data
  })
}

/**
 * 更新考试
 */
export const updateExam = (id: number, data: any) => {
  return request({
    url: `/exams/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除考试
 */
export const deleteExam = (id: number) => {
  return request({
    url: `/exams/${id}`,
    method: 'delete'
  })
}

/**
 * 开始录入
 */
export const startInput = (id: number) => {
  return request({
    url: `/exams/${id}/start-input`,
    method: 'post'
  })
}

/**
 * 提交审核
 */
export const submitReview = (id: number) => {
  return request({
    url: `/exams/${id}/submit-review`,
    method: 'post'
  })
}

/**
 * 发布成绩
 */
export const publishExam = (id: number) => {
  return request({
    url: `/exams/${id}/publish`,
    method: 'post'
  })
}
