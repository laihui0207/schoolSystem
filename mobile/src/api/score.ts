import type { Page, ScoreResponse } from '@/types'
import { get } from '@/utils/request'

/**
 * GET /scores?examId&studentId —— 分页；学生身份后端强制 publishedOnly 与本人；家长由 canViewStudent 放行孩子
 * pageSize 给大一些：一场考试一个学生最多十几科
 */
export const listStudentExamScores = (examId: number, studentId: number): Promise<Page<ScoreResponse>> =>
  get<Page<ScoreResponse>>('/scores', { examId, studentId, pageNum: 1, pageSize: 50 }, { silent: true })

/** GET /scores/student/{studentId}/exam/{examId} —— 非分页版本 */
export const getStudentExamScores = (examId: number, studentId: number): Promise<ScoreResponse[]> =>
  get<ScoreResponse[]>(`/scores/student/${studentId}/exam/${examId}`, undefined, { silent: true })
