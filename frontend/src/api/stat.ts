import request from '@/utils/request'

/**
 * 获取个人成绩统计
 */
export const getPersonalStat = (params: any) => {
  return request({
    url: '/stats/personal',
    method: 'get',
    params
  })
}

/**
 * 获取学生成绩趋势
 */
export const getStudentTrend = (studentId: number, subjectId?: number) => {
  return request({
    url: `/stats/student/${studentId}/trend`,
    method: 'get',
    params: { subjectId }
  })
}

/**
 * 获取班级成绩统计
 */
export const getClassStat = (params: any) => {
  return request({
    url: '/stats/class',
    method: 'get',
    params
  })
}

/**
 * 获取年级成绩统计
 */
export const getGradeStat = (params: any) => {
  return request({
    url: '/stats/grade',
    method: 'get',
    params
  })
}
