import request from '@/utils/request'

/**
 * 对应后端 StudentResponse
 * 后端无 studentName / idCard / phone / email
 * status: 0=离校, 1=在读, 2=休学
 * classes 是数组 [{classId, className, gradeName}]
 */
export interface StudentClassInfo {
  classId: number
  className: string
  gradeName: string
}

export interface Student {
  id?: number
  userId?: number
  studentNo: string
  schoolId: number
  schoolName?: string
  name: string
  gender?: number
  genderName?: string
  birthDate?: string
  enrollmentDate?: string
  status?: number
  statusName?: string
  classes?: StudentClassInfo[]
  createdAt?: string
  updatedAt?: string
}

export interface StudentQuery {
  studentNo?: string
  name?: string
  schoolId?: number
  classId?: number
  status?: number
  pageNum: number
  pageSize: number
}

export interface StudentPage {
  records: Student[]
  total: number
}

export const getStudentList = (params: StudentQuery) => {
  return request<StudentPage>({
    url: '/students',
    method: 'get',
    params
  })
}

export const getStudentDetail = (id: number) => {
  return request<Student>({
    url: `/students/${id}`,
    method: 'get'
  })
}

/**
 * ponytail: 只发 StudentCreateRequest 认的字段
 * studentNo / schoolId / name 必填
 */
export const createStudent = (data: Student) => {
  return request<Student>({
    url: '/students',
    method: 'post',
    data: {
      studentNo: data.studentNo,
      schoolId: data.schoolId,
      name: data.name,
      gender: data.gender,
      birthDate: data.birthDate,
      enrollmentDate: data.enrollmentDate,
      userId: data.userId
    }
  })
}

/**
 * ponytail: 只发 StudentUpdateRequest 认的字段
 * status: 0=离校, 1=在读, 2=休学
 */
export const updateStudent = (id: number, data: Student) => {
  return request<Student>({
    url: `/students/${id}`,
    method: 'put',
    data: {
      name: data.name,
      gender: data.gender,
      birthDate: data.birthDate,
      enrollmentDate: data.enrollmentDate,
      status: data.status
    }
  })
}

export const deleteStudent = (id: number) => {
  return request({
    url: `/students/${id}`,
    method: 'delete'
  })
}

/**
 * ponytail: 后端 @RequestParam Long classId，必须用 params 不能用 data
 */
export const assignClass = (studentId: number, classId: number) => {
  return request({
    url: `/students/${studentId}/assign-class`,
    method: 'post',
    params: { classId }
  })
}

export const transferClass = (studentId: number, fromClassId: number, toClassId: number) => {
  return request({
    url: `/students/${studentId}/transfer-class`,
    method: 'post',
    params: { fromClassId, toClassId }
  })
}

// ---------------------------------------------------------------- 名册导入导出

/**
 * 对应后端 RosterImportResult（学生/教师导入共用，teacher.ts 从这里复用）
 * 任一行失败则整批不落库：failCount > 0 时 successCount 恒为 0
 */
export interface RosterImportFailure {
  row: number
  studentNo?: string
  teacherNo?: string
  reason: string
}

export interface RosterCreatedAccount {
  row: number
  username: string
  realName: string
  initialPassword: string
}

export interface RosterImportResult {
  successCount: number
  failCount: number
  failures: RosterImportFailure[]
  createdAccounts: RosterCreatedAccount[]
  warnings: string[]
}

/**
 * 保存 blob 为文件。
 * ponytail: 后端业务异常（如「没有数据」）在 blob 通道里是一段 JSON，拦截器对 blob 不做解包也不弹提示；
 * 这里识别 application/json 并抛出 message，由调用方 handleError 展示，避免把一段 JSON 存成 .xlsx
 */
export const saveBlobAsFile = async (blob: Blob, filename: string): Promise<void> => {
  if (blob.type.includes('application/json')) {
    const body = JSON.parse(await blob.text()) as { message?: string }
    throw new Error(body.message || '下载失败')
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export const downloadStudentTemplate = () =>
  request<Blob>({ url: '/students/template', method: 'get', responseType: 'blob' })

/**
 * ponytail: 后端是 @RequestParam，file 与 schoolId 都走 multipart 表单；校管理员不传 schoolId
 */
export const importStudents = (file: File, schoolId?: number) => {
  const formData = new FormData()
  formData.append('file', file)
  if (schoolId) formData.append('schoolId', String(schoolId))
  return request<RosterImportResult>({
    url: '/students/import',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data: formData
  })
}

export type StudentExportQuery = Omit<StudentQuery, 'pageNum' | 'pageSize'>

export const exportStudents = async (params: StudentExportQuery): Promise<void> => {
  const blob = await request<Blob>({ url: '/students/export', method: 'get', params, responseType: 'blob' })
  await saveBlobAsFile(blob, `学生档案导出_${Date.now()}.xlsx`)
}
