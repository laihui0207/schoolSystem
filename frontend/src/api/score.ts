import axios from 'axios'
import request from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

export interface ScoreInput {
  examId: number
  classId: number
  schoolId: number
  studentId: number
  subjectId: number
  score?: number
  isAbsent?: number
  remark?: string
}

/**
 * 批量录入单项（契约 1：ScoreBatchInputRequest.ScoreItem）
 * ponytail: isAbsent 与后端 Integer 0/1 对齐而不是 boolean；classId/subjectId 可覆盖顶层默认；
 *           不再传 schoolId，学校归属由服务端按学生记录解析
 */
export interface BatchScoreItem {
  studentId: number
  score?: number
  /** 0-正常 1-缺考，默认 0 */
  isAbsent?: number
  remark?: string
  classId?: number
  subjectId?: number
}

/**
 * POST /scores/batch-input 请求体（契约 1：ScoreBatchInputRequest）
 */
export interface BatchScoreInput {
  examId: number
  /** 各项默认班级，可被 scores[i].classId 覆盖 */
  classId?: number
  /** 各项默认科目，可被 scores[i].subjectId 覆盖 */
  subjectId?: number
  scores: BatchScoreItem[]
}

/**
 * 批量录入 / Excel 导入的单条失败项
 */
export interface BatchInputFailure {
  studentId: number
  subjectId: number
  reason: string
}

/**
 * 批量录入 / Excel 导入结果（契约 1：BatchInputResult）
 */
export interface BatchInputResult {
  successCount: number
  failCount: number
  failures: BatchInputFailure[]
}

export interface ScoreQuery extends PageQuery {
  examId?: number
  studentId?: number
  subjectId?: number
  classId?: number
}

/**
 * 成绩查询返回项（对应后端 ScoreResponse）
 * ponytail: 契约 R — rankIn* 永远是「该行所属科目」的单科名次；totalRankIn* 是该生本场考试总分名次（同一学生所有行相同）
 */
export interface ScoreResponse {
  id: number
  examId: number
  examName?: string
  studentId: number
  studentName?: string
  studentNo?: string
  subjectId: number
  subjectName?: string
  classId?: number
  className?: string
  schoolId?: number
  schoolName?: string
  score?: number
  fullScore?: number
  /** 0-正常 1-缺考 */
  isAbsent?: number
  /** 单科班级名次 */
  rankInClass?: number
  /** 单科年级名次 */
  rankInGrade?: number
  /** 单科全校名次 */
  rankInSchool?: number
  /** 总分班级名次 */
  totalRankInClass?: number
  /** 总分年级名次 */
  totalRankInGrade?: number
  /** 总分全校名次 */
  totalRankInSchool?: number
  recorderId?: number
  status?: number
  statusName?: string
  recordedAt?: string
  reviewedAt?: string
  remark?: string
}

/**
 * 审核结果：1-通过，2-驳回（与后端 ScoreReviewRequest.result 一致）
 */
export const REVIEW_RESULT = {
  APPROVE: 1,
  REJECT: 2
} as const

export type ReviewResult = (typeof REVIEW_RESULT)[keyof typeof REVIEW_RESULT]

/**
 * POST /scores/review 请求体（对应后端 ScoreReviewRequest）
 * scoreIds 与 (examId, classId, subjectId) 三元组二选一，后端优先 scoreIds
 */
export interface ScoreReview {
  scoreIds?: number[]
  examId?: number
  classId?: number
  subjectId?: number
  result: ReviewResult
  rejectReason?: string
}

/**
 * 班级学生成绩项
 */
export interface ClassScoreItem {
  id?: number
  studentId: number
  studentNo: string
  studentName: string
  score?: number
  isAbsent: boolean
  remark?: string
}

/**
 * 成绩统计信息（GET /scores/statistics，对应后端 ScoreStatisticsDto）
 */
export interface ScoreStatistics {
  totalCount: number
  enteredCount: number
  averageScore: number
  highestScore: number
  lowestScore: number
  /** 及格率，后端已换算为百分数 0-100，前端直接 toFixed 加 % 即可，不要再 ×100 */
  passRate: number
}

/**
 * 录入成绩
 */
export const inputScore = (data: ScoreInput) => {
  return request({
    url: '/scores/input',
    method: 'post',
    data
  })
}

/**
 * 批量录入成绩
 */
export const batchInputScore = (data: BatchScoreInput) => {
  return request<BatchInputResult>({
    url: '/scores/batch-input',
    method: 'post',
    data
  })
}

/**
 * 分页查询成绩列表
 */
export const getScoreList = (params: ScoreQuery) => {
  return request<PageResponse<ScoreResponse>>({
    url: '/scores',
    method: 'get',
    params
  })
}

/**
 * 查询班级学生成绩列表（用于成绩录入）
 */
export const getClassScoreList = (examId: number, classId: number, subjectId: number) => {
  return request<ClassScoreItem[]>({
    url: '/scores/class-list',
    method: 'get',
    params: { examId, classId, subjectId }
  })
}

/**
 * 获取成绩统计信息
 */
export const getScoreStatistics = (examId: number, classId: number, subjectId: number) => {
  return request<ScoreStatistics>({
    url: '/scores/statistics',
    method: 'get',
    params: { examId, classId, subjectId }
  })
}

/**
 * 审核成绩（后端 POST /scores/review，返回 Result<Void>）
 */
export const reviewScore = (data: ScoreReview) => {
  return request<void>({
    url: '/scores/review',
    method: 'post',
    data
  })
}

/**
 * 计算排名
 */
export const calculateRankings = (examId: number, subjectId?: number) => {
  return request({
    url: '/scores/calculate-rankings',
    method: 'post',
    params: { examId, subjectId }
  })
}

/**
 * 删除成绩
 */
export const deleteScore = (id: number) => {
  return request({
    url: `/scores/${id}`,
    method: 'delete'
  })
}

/**
 * Excel导入成绩（后端 ExportController：POST /export/import-scores，返回 BatchInputResult）
 * 模板列顺序须与 ScoreImport.vue 的校验一致：学号 | 姓名 | 分数 | 缺考 | 备注
 * ponytail: 契约 1 — 导入与批量录入共用 BatchInputResult，前端据此逐条展示失败学生与原因
 */
export const importScores = (file: File, examId: number, classId: number, subjectId: number) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('examId', examId.toString())
  formData.append('classId', classId.toString())
  formData.append('subjectId', subjectId.toString())

  return request<BatchInputResult>({
    url: '/export/import-scores',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data: formData
  })
}

/**
 * 下载成绩导入模板（后端 ExportController：GET /export/score-template）
 */
export const downloadTemplate = () => {
  return request<Blob>({
    url: '/export/score-template',
    method: 'get',
    responseType: 'blob'
  })
}

/**
 * 审核列表查询参数
 */
export interface ReviewQuery extends PageQuery {
  examId?: number
  classId?: number
  subjectId?: number
  status?: 'pending' | 'approved' | 'rejected'
  // ponytail: F-04 — 后端 GET /scores/review-list 不绑定 startDate/endDate，去掉以免暴露无效筛选
}

/**
 * 审核列表项
 */
export interface ReviewListItem {
  id: number
  examId: number
  examName: string
  classId: number
  className: string
  subjectId: number
  subjectName: string
  fullScore: number
  inputPerson: string
  inputTime: string
  status: 'pending' | 'approved' | 'rejected'
  reviewPerson?: string
  reviewTime?: string
}

/**
 * 审核通过参数
 */
export interface ReviewApprove {
  examId: number
  classId: number
  subjectId: number
  remark?: string
}

/**
 * 审核驳回参数
 */
export interface ReviewReject {
  examId: number
  classId: number
  subjectId: number
  reason: string
}

/**
 * 审核历史记录
 */
export interface ReviewHistory {
  id: number
  operator: string
  operateTime: string
  action: 'approve' | 'reject'
  reason?: string
  remark?: string
}

/**
 * 审核详情
 */
export interface ReviewDetail {
  examInfo: {
    examId: number
    examName: string
    examType: string
    examDate: string
  }
  classInfo: {
    classId: number
    className: string
  }
  subjectInfo: {
    subjectId: number
    subjectName: string
    fullScore: number
  }
  inputInfo: {
    inputPerson: string
    inputTime: string
  }
  status: 'pending' | 'approved' | 'rejected'
  scores: ClassScoreItem[]
  statistics: ScoreStatistics & {
    excellentRate: number
  }
}

/**
 * 分数分布数据
 */
export interface ScoreDistribution {
  range: string
  count: number
}

/**
 * 获取待审核列表
 */
export const getReviewList = (params: ReviewQuery) => {
  return request<PageResponse<ReviewListItem>>({
    url: '/scores/review-list',
    method: 'get',
    params
  })
}

/**
 * 获取审核详情
 */
export const getReviewDetail = (examId: number, classId: number, subjectId: number) => {
  return request<ReviewDetail>({
    url: '/scores/review-detail',
    method: 'get',
    params: { examId, classId, subjectId }
  })
}

/**
 * 审核通过（按 考试/班级/科目 三元组，通过该组合下全部待审核成绩）
 * 后端只有 POST /scores/review，这里组装其 payload
 */
export const approveScore = (data: ReviewApprove) => {
  return reviewScore({
    examId: data.examId,
    classId: data.classId,
    subjectId: data.subjectId,
    result: REVIEW_RESULT.APPROVE
  })
}

/**
 * 审核驳回（按 考试/班级/科目 三元组，驳回该组合下全部待审核成绩）
 */
export const rejectScore = (data: ReviewReject) => {
  return reviewScore({
    examId: data.examId,
    classId: data.classId,
    subjectId: data.subjectId,
    result: REVIEW_RESULT.REJECT,
    rejectReason: data.reason
  })
}

/**
 * 获取审核历史
 */
export const getReviewHistory = (examId: number, classId: number, subjectId: number) => {
  return request<ReviewHistory[]>({
    url: '/scores/review-history',
    method: 'get',
    params: { examId, classId, subjectId }
  })
}

/**
 * 获取分数分布
 */
export const getScoreDistribution = (examId: number, classId: number, subjectId: number) => {
  return request<ScoreDistribution[]>({
    url: '/scores/distribution',
    method: 'get',
    params: { examId, classId, subjectId }
  })
}

/**
 * 审核三元组（考试 / 班级 / 科目）
 */
export interface ReviewTriplet {
  examId: number
  classId: number
  subjectId: number
}

export interface BatchReviewFailure extends ReviewTriplet {
  reason: string
}

/**
 * 批量审核结果：逐三元组汇总成功 / 失败
 */
export interface BatchReviewResult {
  successCount: number
  failCount: number
  failures: BatchReviewFailure[]
}

const reviewFailureReason = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || error.message || '请求失败'
  }
  return error instanceof Error ? error.message : '未知错误'
}

// ponytail: F-07 — 每个三元组各自一个事务，部分失败是常态；用 allSettled 把成功/失败逐项汇总，而不是第一个失败就整体 reject
const settleReviews = async (
  items: ReviewTriplet[],
  run: (item: ReviewTriplet) => Promise<void>
): Promise<BatchReviewResult> => {
  const settled = await Promise.allSettled(items.map(run))
  const failures: BatchReviewFailure[] = settled.flatMap((r, i) =>
    r.status === 'rejected' ? [{ ...items[i], reason: reviewFailureReason(r.reason) }] : []
  )
  return {
    successCount: items.length - failures.length,
    failCount: failures.length,
    failures
  }
}

/**
 * 批量审核通过：每个三元组各调一次 /scores/review
 * ponytail: 后端没有多三元组的批量接口，且每个三元组各自一个事务；审核列表一次勾选也就几条到几十条，
 *           量大或需要整体原子性时再给后端加 List<三元组> 的批量端点
 */
export const batchApprove = (data: ReviewApprove[]) => {
  return settleReviews(data, item => approveScore(item))
}

/**
 * 批量审核驳回：同一原因，每个三元组各调一次 /scores/review
 */
export const batchReject = (items: ReviewTriplet[], reason: string) => {
  return settleReviews(items, item => rejectScore({ ...item, reason }))
}
