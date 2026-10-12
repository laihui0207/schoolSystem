import request from '@/utils/request'

/**
 * 对应后端 ClassResponse
 *
 * 注意：schoolName / gradeName / termName / headTeacherName 后端声明了但
 * ClassServiceImpl.convertToResponse 从未赋值，恒为 null。
 * 页面用已加载的下拉列表在前端映射（见 views/class/index.vue），
 * 不额外为展示名加一次后端 join。
 */
export interface Class {
  id?: number
  schoolId?: number
  schoolName?: string
  gradeId?: number
  gradeName?: string
  className: string
  classCode?: number
  termId?: number
  termName?: string
  headTeacherId?: number
  headTeacherName?: string
  createdAt?: string
  updatedAt?: string
}

export interface ClassQuery {
  className?: string
  gradeId?: number
  schoolId?: number
  termId?: number
  pageNum: number
  pageSize: number
}

export interface ClassPage {
  records: Class[]
  total: number
}

/**
 * 分页查询班级列表
 */
export const getClassList = (params: ClassQuery) => {
  return request<ClassPage>({
    url: '/classes',
    method: 'get',
    params
  })
}

/**
 * 获取班级详情
 */
export const getClassDetail = (id: number) => {
  return request<Class>({
    url: `/classes/${id}`,
    method: 'get'
  })
}

/**
 * 创建班级
 *
 * ponytail: 只发后端 ClassCreateRequest 认的字段。classCode / termId 是
 * @NotNull，漏发直接 400「学期ID不能为空, 班级编码不能为空」
 */
export const createClass = (data: Class) => {
  return request<Class>({
    url: '/classes',
    method: 'post',
    data: {
      schoolId: data.schoolId,
      gradeId: data.gradeId,
      className: data.className,
      classCode: data.classCode,
      termId: data.termId,
      headTeacherId: data.headTeacherId
    }
  })
}

/**
 * 更新班级
 *
 * 后端 ClassUpdateRequest 只接受这三个字段，
 * schoolId / gradeId / termId 一律沿用原值，不可改
 */
export const updateClass = (id: number, data: Class) => {
  return request<Class>({
    url: `/classes/${id}`,
    method: 'put',
    data: {
      className: data.className,
      classCode: data.classCode,
      // ponytail: G-9 — 清空班主任时 el-select 给的是 undefined，JSON 序列化会直接丢字段，后端无法区分「没改」和「置空」；显式发 null
      headTeacherId: data.headTeacherId ?? null
    }
  })
}

/**
 * 删除班级
 */
export const deleteClass = (id: number) => {
  return request({
    url: `/classes/${id}`,
    method: 'delete'
  })
}

/**
 * 设置班主任
 */
export const setHeadTeacher = (id: number, headTeacherId: number) => {
  return request({
    url: `/classes/${id}/head-teacher`,
    method: 'patch',
    params: { headTeacherId }
  })
}

/**
 * 根据年级ID获取班级列表（不分页）
 */
export const getClassesByGrade = async (gradeId: number): Promise<Class[]> => {
  const page = await request<ClassPage>({
    url: '/classes',
    method: 'get',
    params: { gradeId, pageNum: 1, pageSize: 1000 }
  })
  return page?.records ?? []
}
