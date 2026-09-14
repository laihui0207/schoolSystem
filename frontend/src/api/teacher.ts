import request from '@/utils/request'

/**
 * 对应后端 TeacherResponse
 * 后端无 teacherName / subjectIds[] / phone / email / hireDate
 * status: 0=离职, 1=在职
 */
export interface Teacher {
  id?: number
  userId?: number
  teacherNo: string
  schoolId: number
  schoolName?: string
  name: string
  gender?: number
  genderName?: string
  subjectId?: number
  subjectName?: string
  status?: number
  statusName?: string
  createdAt?: string
  updatedAt?: string
}

export interface TeacherQuery {
  name?: string
  teacherNo?: string
  schoolId?: number
  subjectId?: number
  status?: number
  pageNum: number
  pageSize: number
}

export interface TeacherPage {
  records: Teacher[]
  total: number
}

export const getTeacherList = (params: TeacherQuery) => {
  return request<TeacherPage>({
    url: '/teachers',
    method: 'get',
    params
  })
}

export const getTeacherDetail = (id: number) => {
  return request<Teacher>({
    url: `/teachers/${id}`,
    method: 'get'
  })
}

/**
 * ponytail: 只发 TeacherCreateRequest 认的字段
 * teacherNo / schoolId / name 必填
 */
export const createTeacher = (data: Teacher) => {
  return request<Teacher>({
    url: '/teachers',
    method: 'post',
    data: {
      teacherNo: data.teacherNo,
      schoolId: data.schoolId,
      name: data.name,
      gender: data.gender,
      subjectId: data.subjectId,
      userId: data.userId
    }
  })
}

/**
 * ponytail: 只发 TeacherUpdateRequest 认的字段
 * status: 0=离职, 1=在职
 */
export const updateTeacher = (id: number, data: Teacher) => {
  return request<Teacher>({
    url: `/teachers/${id}`,
    method: 'put',
    data: {
      name: data.name,
      gender: data.gender,
      subjectId: data.subjectId,
      status: data.status
    }
  })
}

export const deleteTeacher = (id: number) => {
  return request({
    url: `/teachers/${id}`,
    method: 'delete'
  })
}
