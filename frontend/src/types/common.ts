/**
 * 分页响应接口
 */
export interface PageResponse<T> {
  records: T[]
  total: number
  /** 当前页（MyBatis-Plus Page.current） */
  // ponytail: 后端直接序列化 MyBatis-Plus Page，字段是 current/size 而非 pageNum/pageSize
  current: number
  /** 每页条数（MyBatis-Plus Page.size） */
  size: number
}

/**
 * API统一响应接口
 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/**
 * 分页查询参数基础接口
 */
export interface PageQuery {
  pageNum: number
  pageSize: number
}
