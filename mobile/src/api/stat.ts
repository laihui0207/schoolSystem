import type { PersonalStatResponse, StudentTrendResponse } from '@/types'
import { get } from '@/utils/request'

// ponytail: 统计接口在"该生暂无成绩"时抛业务异常（如"未找到学生成绩"），页面要以空态呈现而不是 toast，故 silent

/** GET /stats/personal?examId&studentId —— 总分/总分名次/各科名次 */
export const getPersonalStat = (examId: number, studentId: number): Promise<PersonalStatResponse> =>
  get<PersonalStatResponse>('/stats/personal', { examId, studentId }, { silent: true })

/** GET /stats/student/{studentId}/trend —— 历次总分趋势 + 各科雷达（取最新一场） */
export const getStudentTrend = (studentId: number, termId?: number): Promise<StudentTrendResponse> =>
  get<StudentTrendResponse>(`/stats/student/${studentId}/trend`, { termId }, { silent: true })
