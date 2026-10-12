import type { ExamResponse, Page } from '@/types'
import { EXAM_STATUS_PUBLISHED } from '@/types'
import { get } from '@/utils/request'

export interface ExamListQuery {
  schoolId?: number
  status?: number
  pageNum?: number
  pageSize?: number
}

/**
 * GET /exams —— 分页；学生/家长只关心 status=3 已发布。
 * ponytail: 不传 orderBy，走 ExamMapper 默认 `ORDER BY e.exam_date DESC, e.created_at DESC`，首条即最近一场
 */
export const listExams = (query: ExamListQuery): Promise<Page<ExamResponse>> =>
  get<Page<ExamResponse>>(
    '/exams',
    {
      schoolId: query.schoolId,
      status: query.status ?? EXAM_STATUS_PUBLISHED,
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20
    },
    { silent: true }
  )

/** GET /exams/{id} */
export const getExam = (id: number): Promise<ExamResponse> => get<ExamResponse>(`/exams/${id}`, undefined, { silent: true })
