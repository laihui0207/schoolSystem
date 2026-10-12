import request from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'

/**
 * 定时任务（对应后端 JobResponse）
 * ponytail: cron 是 Spring 6 位格式（秒 分 时 日 月 周），不是 crontab 的 5 位；模板与校验都按 6 段处理
 */
export interface JobItem {
  id: number
  jobName: string
  jobCode: string
  /** 执行器 Spring Bean 名 */
  handlerBean: string
  /** 容器里是否存在该 handler（预置 backupJobHandler 在备份模块未就绪时为 false） */
  handlerAvailable?: boolean
  cron: string
  /** JSON 对象字符串 */
  params?: string
  /** 0-停用 1-启用 */
  status: 0 | 1
  lastRunAt?: string
  /** 1-成功 0-失败 */
  lastRunStatus?: number
  nextRunAt?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

/** 新建/编辑请求（对应后端 JobSaveRequest） */
export interface JobSaveRequest {
  jobName: string
  jobCode: string
  handlerBean: string
  cron: string
  params?: string
  status?: number
  remark?: string
}

export interface JobQuery extends PageQuery {
  jobName?: string
  jobCode?: string
  status?: number
}

/** 执行日志（对应后端 JobLogResponse） */
export interface JobLogItem {
  id: number
  jobId: number
  jobCode: string
  startedAt?: string
  finishedAt?: string
  /** 1-成功 0-失败 2-执行中 */
  status: number
  statusName?: string
  message?: string
  durationMs?: number
  /** "cron" 或触发者用户名 */
  triggeredBy?: string
}

export interface JobLogQuery extends PageQuery {
  status?: number
}

export const JOB_LOG_STATUS = {
  FAIL: 0,
  SUCCESS: 1,
  RUNNING: 2
} as const

export interface CronTemplate {
  label: string
  cron: string
}

/** 常用 cron 模板（Spring 6 位） */
export const CRON_TEMPLATES: readonly CronTemplate[] = [
  { label: '每分钟', cron: '0 * * * * ?' },
  { label: '每小时整点', cron: '0 0 * * * ?' },
  { label: '每天 02:00', cron: '0 0 2 * * ?' },
  { label: '每天 03:00', cron: '0 0 3 * * ?' },
  { label: '每天 06:00', cron: '0 0 6 * * ?' },
  { label: '工作日 07:30', cron: '0 30 7 ? * MON-FRI' },
  { label: '每周一 01:00', cron: '0 0 1 ? * MON' },
  { label: '每月 1 日 01:00', cron: '0 0 1 1 * ?' }
]

/** 分页查询任务（GET /jobs，仅超管） */
export const getJobList = (params: JobQuery) => {
  return request<PageResponse<JobItem>>({
    url: '/jobs',
    method: 'get',
    params
  })
}

/** 容器内全部 JobHandler Bean 名（GET /jobs/handlers） */
export const getJobHandlers = () => {
  return request<string[]>({
    url: '/jobs/handlers',
    method: 'get'
  })
}

/** 新建任务（POST /jobs），返回新任务 ID */
export const createJob = (data: JobSaveRequest) => {
  return request<number>({
    url: '/jobs',
    method: 'post',
    data
  })
}

/** 编辑任务（PUT /jobs/{id}） */
export const updateJob = (id: number, data: JobSaveRequest) => {
  return request<void>({
    url: `/jobs/${id}`,
    method: 'put',
    data
  })
}

/** 删除任务（DELETE /jobs/{id}） */
export const deleteJob = (id: number) => {
  return request<void>({
    url: `/jobs/${id}`,
    method: 'delete'
  })
}

/** 启停任务（PATCH /jobs/{id}/status?status=） */
export const updateJobStatus = (id: number, status: number) => {
  return request<void>({
    url: `/jobs/${id}/status`,
    method: 'patch',
    params: { status }
  })
}

/** 立即异步执行一次（POST /jobs/{id}/run），返回 jobLogId */
export const runJob = (id: number) => {
  return request<number>({
    url: `/jobs/${id}/run`,
    method: 'post'
  })
}

/** 任务执行日志分页（GET /jobs/{id}/logs） */
export const getJobLogs = (id: number, params: JobLogQuery) => {
  return request<PageResponse<JobLogItem>>({
    url: `/jobs/${id}/logs`,
    method: 'get',
    params
  })
}
