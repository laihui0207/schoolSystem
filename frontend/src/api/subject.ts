import request from '@/utils/request'

/**
 * 对应后端 SubjectResponse
 * 后端无 subjectType / totalScore / passingScore / sortOrder / status
 */
export interface Subject {
  id?: number
  schoolId?: number
  subjectName: string
  subjectCode?: string
  fullScore?: number
  passScore?: number
  createdAt?: string
  updatedAt?: string
}

export interface SubjectQuery {
  subjectName?: string
  subjectCode?: string
  schoolId?: number
  pageNum: number
  pageSize: number
}

export interface SubjectPage {
  records: Subject[]
  total: number
}

export const getSubjectList = (params: SubjectQuery) => {
  return request<SubjectPage>({
    url: '/subjects',
    method: 'get',
    params
  })
}

export const getSubjectDetail = (id: number) => {
  return request<Subject>({
    url: `/subjects/${id}`,
    method: 'get'
  })
}

/**
 * ponytail: 只发 SubjectCreateRequest 认的字段
 * subjectCode 须 @Pattern(^[A-Z0-9]+$)，schoolId 必填
 */
export const createSubject = (data: Subject) => {
  return request<Subject>({
    url: '/subjects',
    method: 'post',
    data: {
      subjectName: data.subjectName,
      subjectCode: data.subjectCode,
      fullScore: data.fullScore,
      passScore: data.passScore,
      schoolId: data.schoolId
    }
  })
}

/**
 * ponytail: 只发 SubjectUpdateRequest 认的字段（不含 subjectCode/schoolId）
 */
export const updateSubject = (id: number, data: Subject) => {
  return request<Subject>({
    url: `/subjects/${id}`,
    method: 'put',
    data: {
      subjectName: data.subjectName,
      fullScore: data.fullScore,
      passScore: data.passScore
    }
  })
}

export const deleteSubject = (id: number) => {
  return request({
    url: `/subjects/${id}`,
    method: 'delete'
  })
}

/** ponytail: 后端无 /all 端点，借分页取大页 */
export const getAllSubjects = async (schoolId?: number): Promise<Subject[]> => {
  const page = await request<SubjectPage>({
    url: '/subjects',
    method: 'get',
    params: { pageNum: 1, pageSize: 1000, ...(schoolId ? { schoolId } : {}) }
  })
  return page?.records ?? []
}
