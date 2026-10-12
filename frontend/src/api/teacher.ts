import request from '@/utils/request'
import { saveBlobAsFile, type RosterImportResult } from '@/api/student'

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

// ---------------------------------------------------------------- 名册导入导出（结果结构见 api/student.ts）

export const downloadTeacherTemplate = () =>
  request<Blob>({ url: '/teachers/template', method: 'get', responseType: 'blob' })

export const importTeachers = (file: File, schoolId?: number) => {
  const formData = new FormData()
  formData.append('file', file)
  if (schoolId) formData.append('schoolId', String(schoolId))
  return request<RosterImportResult>({
    url: '/teachers/import',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data: formData
  })
}

export type TeacherExportQuery = Omit<TeacherQuery, 'pageNum' | 'pageSize'>

export const exportTeachers = async (params: TeacherExportQuery): Promise<void> => {
  const blob = await request<Blob>({ url: '/teachers/export', method: 'get', params, responseType: 'blob' })
  await saveBlobAsFile(blob, `教师档案导出_${Date.now()}.xlsx`)
}
