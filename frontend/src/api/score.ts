import request from '@/utils/request'

export const inputScore = (data: any) => request({ url: '/scores/input', method: 'post', data })

export const batchInputScore = (data: any) => request({ url: '/scores/batch-input', method: 'post', data })

export const getScoreList = (params: any) => request({ url: '/scores', method: 'get', params })

export const reviewScore = (data: any) => request({ url: '/scores/review', method: 'post', data })

export const calculateRankings = (examId: number, subjectId?: number) =>
  request({ url: '/scores/calculate-rankings', method: 'post', params: { examId, subjectId } })

export const deleteScore = (id: number) => request({ url: `/scores/${id}`, method: 'delete' })

export const getClassScoreList = (examId: number, classId: number, subjectId?: number) =>
  request({ url: '/scores/class-list', method: 'get', params: { examId, classId, subjectId } })

export const getScoreStatistics = (examId: number, classId: number, subjectId?: number) =>
  request({ url: '/scores/statistics', method: 'get', params: { examId, classId, subjectId } })

export const getReviewList = (params: { examId?: number; classId?: number; subjectId?: number; schoolId?: number }) =>
  request({ url: '/scores/review-list', method: 'get', params })

export const getReviewDetail = (examId: number, classId: number, subjectId?: number) =>
  request({ url: '/scores/review-detail', method: 'get', params: { examId, classId, subjectId } })

export const getReviewHistory = (examId: number, classId: number, subjectId?: number) =>
  request({ url: '/scores/review-history', method: 'get', params: { examId, classId, subjectId } })

export const getScoreDistribution = (examId: number, classId: number, subjectId?: number) =>
  request({ url: '/scores/distribution', method: 'get', params: { examId, classId, subjectId } })
