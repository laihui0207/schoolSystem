import request from '@/utils/request'

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
  headTeacher?: boolean
  classes?: ClassAssignment[]
  createdAt?: string
  updatedAt?: string
}

export interface ClassAssignment {
  classId: number
  className: string
  subjectId: number
  subjectName: string
  isHeadTeacher: boolean
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

export const getTeacherList = (params: TeacherQuery) =>
  request<TeacherPage>({ url: '/teachers', method: 'get', params })

export const getTeacherDetail = (id: number) =>
  request<Teacher>({ url: `/teachers/${id}`, method: 'get' })

export const createTeacher = (data: Teacher) =>
  request<Teacher>({
    url: '/teachers',
    method: 'post',
    data: { teacherNo: data.teacherNo, schoolId: data.schoolId, name: data.name, gender: data.gender, subjectId: data.subjectId, userId: data.userId }
  })

export const updateTeacher = (id: number, data: Teacher) =>
  request<Teacher>({
    url: `/teachers/${id}`,
    method: 'put',
    data: { name: data.name, gender: data.gender, subjectId: data.subjectId, status: data.status }
  })

export const deleteTeacher = (id: number) =>
  request({ url: `/teachers/${id}`, method: 'delete' })

export const getClassAssignments = (teacherId: number) =>
  request<ClassAssignment[]>({ url: `/teachers/${teacherId}/classes`, method: 'get' })

export const assignClass = (teacherId: number, classId: number, subjectId: number) =>
  request({ url: `/teachers/${teacherId}/classes`, method: 'post', params: { classId, subjectId } })

export const removeClassAssignment = (teacherId: number, classId: number, subjectId: number) =>
  request({ url: `/teachers/${teacherId}/classes`, method: 'delete', params: { classId, subjectId } })
