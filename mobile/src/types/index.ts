/**
 * 与后端 DTO 一一对应的类型（字段名以 Java 类为准，不要猜）。
 * 来源：LoginResponse / ExamResponse / ScoreResponse / PersonalStatResponse /
 *       StudentTrendResponse / NotificationResponse / StudentResponse
 */

/** 统一响应信封 Result<T>{code,message,data,timestamp}，成功 code=200 */
export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp?: number
}

/** MyBatis-Plus Page<T>：只读 records/total，没有 list 字段 */
export interface Page<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages?: number
}

export const USER_TYPE_SUPER_ADMIN = 1
export const USER_TYPE_SCHOOL_ADMIN = 2
export const USER_TYPE_TEACHER = 3
export const USER_TYPE_STUDENT = 4
export const USER_TYPE_PARENT = 5

/** LoginResponse.UserInfo（childStudentIds 由 A 组本轮加入，老后端可能缺省） */
export interface UserInfo {
  id: number
  username: string
  realName?: string
  avatar?: string
  userType: number
  schoolId?: number
  /** student.id，非学生账号为 null */
  studentId?: number
  teacherId?: number
  headTeacher?: boolean
  role?: string
  /** 家长绑定的学生 ID 列表，非家长为空 */
  childStudentIds?: number[]
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType?: string
  expiresIn?: number
  userInfo: UserInfo
}

export interface CaptchaResponse {
  captchaKey: string
  /** data:image/png;base64,... */
  captchaImage: string
  expiresIn?: number
}

export interface LoginParams {
  username: string
  password: string
  captcha?: string
  captchaKey?: string
}

export interface PhoneLoginParams {
  phone: string
  code: string
}

export interface ExamSubjectInfo {
  subjectId: number
  subjectName: string
  fullScore?: number
  sortOrder?: number
}

/** 考试状态：0 未开始 1 录入中 2 审核中 3 已发布 */
export const EXAM_STATUS_PUBLISHED = 3

export interface ExamResponse {
  id: number
  schoolId?: number
  schoolName?: string
  termId?: number
  termName?: string
  examName: string
  examType?: number
  examTypeName?: string
  /** yyyy-MM-dd */
  examDate?: string
  gradeIds?: number[]
  gradeNames?: string[]
  status: number
  statusName?: string
  subjects?: ExamSubjectInfo[]
  createdAt?: string
  updatedAt?: string
}

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
  /** 0 否 1 是 */
  isAbsent?: number
  rankInClass?: number
  rankInGrade?: number
  rankInSchool?: number
  totalRankInClass?: number
  totalRankInGrade?: number
  totalRankInSchool?: number
  /** 0 待审核 1 已通过 2 已驳回 */
  status?: number
  statusName?: string
  recordedAt?: string
  remark?: string
}

export interface PersonalSubjectScore {
  subjectId: number
  subjectName?: string
  score?: number
  fullScore?: number
  rankInClass?: number
  rankInGrade?: number
  isAbsent?: boolean
  /**
   * 班级均分 —— 当前后端 PersonalStatResponse.SubjectScore 没有此字段（/stats/class 仅管理员/教师可见）。
   * ponytail: 前向兼容：后端补了字段 UI 自动显示"班级均分"列，缺省时隐藏，见 README 跨组说明
   */
  classAverage?: number
}

export interface PersonalStatResponse {
  studentId: number
  studentName?: string
  studentNo?: string
  examId: number
  examName?: string
  totalScore?: number
  totalFullScore?: number
  /** 该生各科平均分（不是班级均分） */
  averageScore?: number
  rankInClass?: number
  rankInGrade?: number
  subjectScores?: PersonalSubjectScore[]
}

export interface ExamTrend {
  examId: number
  examName?: string
  examDate?: string
  totalScore?: number
  averageScore?: number
  rankInClass?: number
  rankInGrade?: number
  subjectScores?: Record<string, number>
}

export interface SubjectRadar {
  subjectId: number
  subjectName?: string
  score?: number
  fullScore?: number
  /** 得分率 0-100 */
  percentage?: number
}

export interface StudentTrendResponse {
  studentId: number
  studentName?: string
  examTrends?: ExamTrend[]
  subjectRadars?: SubjectRadar[]
}

/** 通知类型：1 成绩发布 2 成绩预警 3 系统通知 */
export interface NotificationResponse {
  id: number
  userId?: number
  title?: string
  content?: string
  type?: number
  typeName?: string
  /** 0 未读 1 已读 */
  isRead?: number
  relatedId?: number
  createdAt?: string
}

export interface StudentClassInfo {
  classId: number
  className?: string
  gradeName?: string
}

export interface StudentResponse {
  id: number
  userId?: number
  studentNo?: string
  schoolId?: number
  schoolName?: string
  name: string
  gender?: number
  genderName?: string
  status?: number
  statusName?: string
  classes?: StudentClassInfo[]
}
