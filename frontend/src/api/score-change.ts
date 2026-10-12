import request from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export interface ScoreChangeApplyRequest {
  scoreId: number
  newScore: number
  changeReason: string
}

/**
 * 审批请求（契约 P：与后端 ScoreChangeApproveRequest 一致）
 * ponytail: 后端按 changeLogIds 列表 + result 1/2 接收，审批人取 SecurityContext，不再从 query 传 approverId
 */
export interface ScoreChangeApproveRequest {
  changeLogIds: number[]
  /** 1-通过 2-驳回 */
  result: 1 | 2
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

export const approveScoreChange = (data: ScoreChangeApproveRequest) => {
  return request<void>({
    url: '/score-changes/approve',
    method: 'post',
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
