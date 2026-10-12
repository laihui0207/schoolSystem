import request from '@/utils/request'
import type { PageQuery } from '@/types/common'

/**
 * 考试状态枚举
 * 与后端 ExamStatusConstants 对齐
 */
export enum ExamStatus {
  /** 待录入（未开始） */
  PENDING_INPUT = 0,
  /** 录入中 */
  RECORDING = 1,
  /** 待审核（审核中） */
  PENDING_REVIEW = 2,
  /** 已发布 */
  PUBLISHED = 3
}

/**
 * 考试类型枚举
 * 与后端 ExamTypeConstants 对齐
 */
export enum ExamType {
  /** 月考 */
  MONTHLY = 1,
  /** 期中考试 */
  MIDTERM = 2,
  /** 期末考试 */
  FINAL = 3,
  /** 模拟考试 */
  MOCK = 4,
  /** 其他 */
  OTHER = 5
}

/**
 * 考试科目信息
 */
export interface ExamSubject {
  subjectId: number
  subjectName?: string
  /** 满分 */
  fullScore: number
  /** 排序 */
  sortOrder?: number
}

/**
 * 考试实体（读取模型，对应后端 ExamResponse）
 */
export interface Exam {
  id?: number
  schoolId: number
  schoolName?: string
  termId: number
  termName?: string
  examName: string
  examType: number
  examTypeName?: string
  examDate?: string
  gradeIds: number[]
  gradeNames?: string[]
  status: number
  statusName?: string
  subjects?: ExamSubject[]
  createdAt?: string
  updatedAt?: string
}

/**
 * 考试创建/更新提交数据
 */
export interface ExamSaveData {
  schoolId: number
  termId: number
  examName: string
  examType: number
  examDate?: string
  gradeIds: number[]
  subjects: ExamSubject[]
}

/**
 * 考试查询参数
 */
export interface ExamQuery extends PageQuery {
  examName?: string
  examType?: number
  status?: number
  schoolId?: number
  termId?: number
  gradeId?: number
  startDate?: string
  endDate?: string
}

/**
 * 单个录入进度项
 */
export interface ExamProgressItem {
  gradeId: number
  gradeName: string
  classId: number
  className: string
  subjectId: number
  subjectName: string
  /** 已录入人数 */
  enteredCount: number
  /** 应录入总人数 */
  totalCount: number
}

/**
 * 考试录入进度
 */
export interface ExamProgress {
  examId: number
  items: ExamProgressItem[]
}

/**
 * 分页查询考试列表
 */
export const getExamList = (params: ExamQuery) => {
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
  return request<Exam>({
    url: `/exams/${id}`,
    method: 'get'
  })
}

/**
 * 创建考试
 */
export const createExam = (data: ExamSaveData) => {
  return request({
    url: '/exams',
    method: 'post',
    data
  })
}

/**
 * 更新考试
 */
export const updateExam = (id: number, data: ExamSaveData) => {
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
 * 开始录入（待录入 -> 录入中）
 */
export const startExamInput = (examId: number) => {
  return request({
    url: `/exams/${examId}/start-input`,
    method: 'post'
  })
}

/**
 * 提交审核（录入中 -> 待审核）
 */
export const submitExamReview = (examId: number) => {
  return request({
    url: `/exams/${examId}/submit-review`,
    method: 'post'
  })
}

/**
 * 发布成绩（待审核 -> 已发布）
 */
export const publishExam = (examId: number) => {
  return request({
    url: `/exams/${examId}/publish`,
    method: 'post'
  })
}

/**
 * 获取考试录入进度
 */
export const getExamProgress = (examId: number) => {
  return request<ExamProgress>({
    url: `/exams/${examId}/progress`,
    method: 'get'
  })
}
