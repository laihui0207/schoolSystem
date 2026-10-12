import request from '@/utils/request'

/**
 * 家长绑定的孩子（对应后端 ChildResponse）
 * ponytail: studentId 是 student.id（学生档案 id），查成绩 / 趋势 / 成绩单一律用它，不要用家长的 userInfo.id
 */
export interface ChildBrief {
  /** parent_student.id */
  id: number
  parentUserId: number
  studentId: number
  studentNo?: string
  name?: string
  className?: string
  gradeName?: string
  /** father / mother / other */
  relationship?: string
  /** 字典 relationship 的 dict_label */
  relationshipName?: string
}

/**
 * 家长本人查看绑定的孩子（GET /parents/me/children，后端要求 userType=5）
 */
export const getMyChildren = () => {
  return request<ChildBrief[]>({
    url: '/parents/me/children',
    method: 'get'
  })
}

/**
 * 管理员查看某家长绑定的孩子（GET /parents/{parentUserId}/children）
 */
export const getParentChildren = (parentUserId: number) => {
  return request<ChildBrief[]>({
    url: `/parents/${parentUserId}/children`,
    method: 'get'
  })
}

/**
 * 管理员为家长绑定学生（POST /parents/{parentUserId}/children?studentId&relationship）
 * ponytail: 后端是 @RequestParam，必须用 params 不能用 data；relationship 取字典 relationship 的 value
 */
export const bindChild = (parentUserId: number, studentId: number, relationship?: string) => {
  return request<ChildBrief>({
    url: `/parents/${parentUserId}/children`,
    method: 'post',
    params: { studentId, relationship }
  })
}

/**
 * 管理员解除绑定（DELETE /parents/{parentUserId}/children/{studentId}）
 */
export const unbindChild = (parentUserId: number, studentId: number) => {
  return request<void>({
    url: `/parents/${parentUserId}/children/${studentId}`,
    method: 'delete'
  })
}
