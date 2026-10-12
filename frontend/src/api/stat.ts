import request from '@/utils/request'

// 类型与后端 com.school.module.stat.dto 一一对应；后端配置 non_null，缺省字段不会出现在响应里

export interface PersonalStatQuery {
  examId: number
  studentId: number
}

export interface ClassStatQuery {
  examId: number
  classId: number
}

export interface GradeStatQuery {
  examId: number
  /** 不传即整场考试总览 */
  gradeId?: number
}

export interface SubjectStatQuery {
  examId: number
  subjectId: number
}

export interface ScoreRange {
  range: string
  count: number
  percentage: number
}

export interface SubjectStatItem {
  subjectId: number
  subjectName: string
  averageScore: number
  maxScore: number
  minScore: number
  passRate?: number
  excellentRate?: number
}

export interface TopStudent {
  rank: number
  studentId: number
  studentNo?: string
  studentName: string
  totalScore: number
  /** 科目名称 -> 得分 */
  subjectScores: Record<string, number>
}

export interface ClassStat {
  classId: number
  examId: number
  totalStudents: number
  actualStudents: number
  absentStudents: number
  totalScore: number
  averageScore: number
  maxScore: number
  minScore: number
  passCount: number
  passRate: number
  excellentCount: number
  excellentRate: number
  subjectStats: SubjectStatItem[]
  scoreRanges: ScoreRange[]
  topStudents: TopStudent[]
}

export interface ClassComparison {
  classId: number
  className: string
  averageScore: number
  maxScore: number
  minScore: number
  passRate: number
  excellentRate: number
  rank: number
}

export interface GradeStatItem {
  gradeId: number
  gradeName: string
  averageScore: number
}

export interface GradeStat {
  gradeId?: number
  gradeName?: string
  examId: number
  totalStudents: number
  averageScore: number
  maxScore: number
  minScore: number
  passRate: number
  excellentRate: number
  classComparisons: ClassComparison[]
  gradeStats: GradeStatItem[]
  subjectStats: SubjectStatItem[]
}

export interface ExamTrend {
  examId: number
  examName: string
  examDate?: string
  totalScore: number
  averageScore: number
  rankInClass?: number
  rankInGrade?: number
  /** 科目名称 -> 得分 */
  subjectScores: Record<string, number>
}

/**
 * 个人科目雷达图单项（契约 3：StudentTrendResponse.SubjectRadar，fullScore 为真实满分）
 */
export interface SubjectRadar {
  subjectId: number
  subjectName: string
  score: number
  fullScore: number
  /** 得分率（%） */
  percentage: number
}

export interface StudentTrend {
  studentId: number
  studentName?: string
  /** 按考试日期升序 */
  examTrends: ExamTrend[]
  /** 最近一次考试各科得分，用于个人雷达图 */
  subjectRadars: SubjectRadar[]
}

/**
 * GET /stats/student/{id}/trend 查询参数（契约 3：subjectId、termId 均可选）
 */
export interface StudentTrendQuery {
  subjectId?: number
  termId?: number
}

export interface ClassAverage {
  classId: number
  className: string
  averageScore: number
}

export interface GradeBox {
  gradeId: number
  gradeName: string
  min: number
  q1: number
  median: number
  q3: number
  max: number
}

export interface SubjectStat {
  subjectId: number
  subjectName: string
  examId: number
  totalStudents: number
  actualStudents: number
  averageScore: number
  maxScore: number
  minScore: number
  passRate: number
  excellentRate: number
  standardDeviation: number
  classAverages: ClassAverage[]
  gradeBoxes: GradeBox[]
  scoreRanges: ScoreRange[]
}

/**
 * 获取个人成绩统计
 */
export const getPersonalStat = (params: PersonalStatQuery) => {
  return request({
    url: '/stats/personal',
    method: 'get',
    params
  })
}

/**
 * 获取学生成绩趋势
 */
// ponytail: 契约 3 — 第二参改为 StudentTrendQuery 对象，termId/subjectId 都可选且可同时传
export const getStudentTrend = (studentId: number, params: StudentTrendQuery = {}) => {
  return request<StudentTrend>({
    url: `/stats/student/${studentId}/trend`,
    method: 'get',
    params
  })
}

/**
 * 获取班级成绩统计
 */
export const getClassStat = (params: ClassStatQuery) => {
  return request<ClassStat>({
    url: '/stats/class',
    method: 'get',
    params
  })
}

/**
 * 获取年级成绩统计；不传 gradeId 即考试总览
 */
export const getGradeStat = (params: GradeStatQuery) => {
  return request<GradeStat>({
    url: '/stats/grade',
    method: 'get',
    params
  })
}

/**
 * 获取某次考试的单科成绩统计
 */
export const getSubjectStat = (params: SubjectStatQuery) => {
  return request<SubjectStat>({
    url: '/stats/subject',
    method: 'get',
    params
  })
}

// ---------------------------------------------------------------- 区域（跨校）对比
// 对应后端 RegionStatQuery / RegionStatResponse / RegionOptionResponse，仅超管可调用

/** GET /stats/regions 单项 */
export interface RegionOption {
  regionCode: string
  /** 该区域内启用状态的学校数 */
  schoolCount: number
}

/** GET /stats/region 查询参数：考试按校建没有统一 ID，用 (year, term, examType) 对齐各校考试 */
export interface RegionStatQuery {
  regionCode: string
  /** 学年，如 2024-2025（academic_term.year） */
  year: string
  /** 1 上学期 2 下学期 */
  term: number
  /** 1 月考 2 期中 3 期末 4 模拟 5 其他 */
  examType: number
}

export interface RegionSchoolStat {
  schoolId: number
  schoolName: string
  /** 该校该学期该类型最新一场已发布考试；没有时缺省 */
  examId?: number
  examName?: string
  /** 至少有一科有效成绩的学生数 */
  studentCount: number
  averageScore: number
  passRate: number
  excellentRate: number
  /** 均分名次，并列同名次；没有可比成绩的学校缺省（排在最后） */
  rank?: number
}

export interface RegionSchoolSubjectAverage {
  schoolId: number
  averageScore: number
}

export interface RegionSubjectCompare {
  /** 科目按名称跨校对齐，这里只是首个遇到的学校的科目 ID */
  subjectId: number
  subjectName: string
  schools: RegionSchoolSubjectAverage[]
}

export interface RegionStat {
  regionCode: string
  examType: number
  year: string
  term: number
  schools: RegionSchoolStat[]
  subjects: RegionSubjectCompare[]
}

/**
 * 区域（跨校）成绩对比
 */
export const getRegionStat = (params: RegionStatQuery) => {
  return request<RegionStat>({
    url: '/stats/region',
    method: 'get',
    params
  })
}

/**
 * 已配置区域编码的区域列表（下拉用）
 */
export const getRegionOptions = () => {
  return request<RegionOption[]>({
    url: '/stats/regions',
    method: 'get'
  })
}
