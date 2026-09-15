export interface PageQuery {
  pageNum: number
  pageSize: number
}

export interface PageResponse<T> {
  records: T[]
  total: number
}
