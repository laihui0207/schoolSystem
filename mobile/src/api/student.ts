import type { StudentResponse } from '@/types'
import { get } from '@/utils/request'

/** GET /students/{id} —— 用于家长端显示孩子姓名/班级；失败不致命，调用方 silent 处理 */
export const getStudent = (id: number): Promise<StudentResponse> =>
  get<StudentResponse>(`/students/${id}`, undefined, { silent: true })
