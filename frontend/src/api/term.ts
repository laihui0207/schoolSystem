import request from '@/utils/request'

/**
 * 学期编号 → 名称（后端 AcademicTerm.term 只有 1、2 两个取值）
 */
export const TERM_NAMES: Record<number, string> = {
  1: '第一学期',
  2: '第二学期'
}

export const termOptions = [
  { label: TERM_NAMES[1], value: 1 },
  { label: TERM_NAMES[2], value: 2 }
]

/**
 * 状态语义与后端 AcademicTerm.status 一致：0-已结束，1-进行中
 */
export const TERM_STATUS = {
  ENDED: 0,
  ACTIVE: 1
} as const

export const termStatusOptions = [
  { label: '进行中', value: TERM_STATUS.ACTIVE },
  { label: '已结束', value: TERM_STATUS.ENDED }
]

/**
 * 对应后端 AcademicTermResponse
 */
export interface Term {
  id?: number
  schoolId?: number
  year: string
  term?: number
  startDate: string
  endDate: string
  status?: number
  createdAt?: string
  updatedAt?: string
}

export interface TermQuery {
  schoolId?: number
  year?: string
  term?: number
  status?: number
  pageNum: number
  pageSize: number
}

export interface TermPage {
  records: Term[]
  total: number
}

/**
 * 学期展示名，例如「2024-2025 第一学期」
 *
 * 后端没有 termName 字段，学期名由 year + term 拼出，各页面统一走这里
 */
export const termLabel = (t?: Term | null): string => {
  if (!t) return '-'
  const name = t.term ? TERM_NAMES[t.term] : ''
  return [t.year, name].filter(Boolean).join(' ') || '-'
}

/**
 * 分页查询学期列表
 */
export const getTermList = (params: TermQuery) => {
  return request<TermPage>({
    url: '/terms',
    method: 'get',
    params
  })
}

/**
 * 获取学期详情
 */
export const getTermDetail = (id: number) => {
  return request<Term>({
    url: `/terms/${id}`,
    method: 'get'
  })
}

/**
 * 创建学期
 *
 * ponytail: 只发后端 AcademicTermCreateRequest 认的字段，表单里的 status 等不外传
 */
export const createTerm = (data: Term) => {
  return request<Term>({
    url: '/terms',
    method: 'post',
    data: {
      schoolId: data.schoolId,
      year: data.year,
      term: data.term,
      startDate: data.startDate,
      endDate: data.endDate
    }
  })
}

/**
 * 更新学期（后端不允许改 schoolId 和 status）
 */
export const updateTerm = (id: number, data: Term) => {
  return request<Term>({
    url: `/terms/${id}`,
    method: 'put',
    data: {
      year: data.year,
      term: data.term,
      startDate: data.startDate,
      endDate: data.endDate
    }
  })
}

/**
 * 结束学期
 *
 * ponytail: 学期被班级/考试引用，后端刻意不提供删除，结束学期即为业务上的「归档」
 */
export const endTerm = (id: number) => {
  return request({
    url: `/terms/${id}/end`,
    method: 'patch'
  })
}

/**
 * 删除学期
 */
export const deleteTerm = (id: number) => {
  return request({
    url: `/terms/${id}`,
    method: 'delete'
  })
}

/**
 * 获取所有学期列表（用于下拉选择）
 *
 * ponytail: 后端没有 /all 端点，借分页接口取大页；学期量级到千级再让后端加不分页端点
 */
export const getAllTerms = async (): Promise<Term[]> => {
  const page = await request<TermPage>({
    url: '/terms',
    method: 'get',
    params: { pageNum: 1, pageSize: 1000 }
  })
  return page?.records ?? []
}
