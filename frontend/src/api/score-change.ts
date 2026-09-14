import request from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export interface ScoreChangeApplyRequest {
  scoreId: number
  newScore: number
  changeReason: string
}

export interface ScoreChangeApproveRequest {
  id: number
  approved: boolean
  rejectReason?: string
}

export interface ScoreChangeLog {
  id: number
  scoreId: number
  examId: number
  examName: string
  studentId: number
  studentName: string
  studentNo: string
  subjectId: number
  subjectName: string
  oldScore: number
  newScore: number
  changeReason: string
  applicantId: number
  applicantName: string
  approverId?: number
  approverName?: string
  status: number
  statusName: string
  applyTime: string
  approveTime?: string
  rejectReason?: string
}

export interface ScoreChangeQuery extends PageQuery {
  examId?: number
  studentId?: number
  subjectId?: number
  status?: number
}

export const applyScoreChange = (data: ScoreChangeApplyRequest, applicantId: number) => {
  return request<ScoreChangeLog>({
    url: '/score-changes/apply',
    method: 'post',
    params: { applicantId },
    data
  })
}

export const approveScoreChange = (data: ScoreChangeApproveRequest, approverId: number) => {
  return request<void>({
    url: '/score-changes/approve',
    method: 'post',
    params: { approverId },
    data
  })
}

export const cancelScoreChange = (id: number, applicantId: number) => {
  return request<void>({
    url: `/score-changes/${id}/cancel`,
    method: 'delete',
    params: { applicantId }
  })
}

export const getScoreChangeList = (params: ScoreChangeQuery) => {
  return request<PageResponse<ScoreChangeLog>>({
    url: '/score-changes',
    method: 'get',
    params
  })
}

export const getScoreChangeById = (id: number) => {
  return request<ScoreChangeLog>({
    url: `/score-changes/${id}`,
    method: 'get'
  })
}
