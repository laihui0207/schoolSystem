import request from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

/**
 * 操作日志项（对应后端 OperationLogResponse）
 * ponytail: 日志由后端切面自动写入，前端只读；params/errorMsg 是原文，详情抽屉里整段展示
 */
export interface OperationLogItem {
  id: number
  userId?: number
  username?: string
  /** 操作描述，如 "[score] 审核成绩" 或 "ScoreController#reviewScores" */
  operation?: string
  /** "HTTP方法 URI"，如 "POST /api/v1/scores/review" */
  method?: string
  /** 请求参数 JSON 原文（敏感字段已打码） */
  params?: string
  ip?: string
  location?: string
  /** 1-成功 0-失败 */
  status?: number
  statusName?: string
  errorMsg?: string
  /** 耗时毫秒 */
  executeTime?: number
  createdAt?: string
}

/**
 * 操作日志查询参数（对应后端 OperationLogQueryRequest）
 */
export interface OperationLogQuery extends PageQuery {
  username?: string
  operation?: string
  /** 1-成功 0-失败 */
  status?: number
  /** yyyy-MM-dd HH:mm:ss */
  startTime?: string
  /** yyyy-MM-dd HH:mm:ss */
  endTime?: string
}

/**
 * 分页查询操作日志（GET /operation-logs，超管全量 / 校管理员仅本校用户）
 */
export const getOperationLogList = (params: OperationLogQuery) => {
  return request<PageResponse<OperationLogItem>>({
    url: '/operation-logs',
    method: 'get',
    params
  })
}

/**
 * 查询单条操作日志（GET /operation-logs/{id}）
 */
export const getOperationLogDetail = (id: number) => {
  return request<OperationLogItem>({
    url: `/operation-logs/${id}`,
    method: 'get'
  })
}
