import request from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'
import { saveBlobAsFile } from '@/api/student'

/**
 * 备份记录（对应后端 BackupRecordResponse）
 * ponytail: restorable 由后端算（成功 + .sql.gz + 文件仍在），前端不要再按扩展名猜
 */
export interface BackupRecord {
  id: number
  fileName?: string
  fileSize?: number
  /** 如 "12.3 MB" */
  fileSizeText?: string
  /** 1-手动 2-定时 */
  backupType?: number
  backupTypeName?: string
  /** pg_dump / docker / sql / upload */
  backupMode?: string
  /** 是否可在线恢复（仅本系统生成的 .sql.gz） */
  restorable?: boolean
  /** 0-进行中 1-成功 2-失败 */
  status?: number
  statusName?: string
  message?: string
  triggeredBy?: string
  startedAt?: string
  finishedAt?: string
  createdAt?: string
}

/**
 * 备份记录查询参数（对应后端 BackupQueryRequest）
 */
export interface BackupQuery extends PageQuery {
  status?: number
  backupType?: number
}

/**
 * 恢复结果（对应后端 BackupRestoreResult）
 */
export interface BackupRestoreResult {
  backupId: number
  preRestoreBackupId?: number
  restoredTables: string[]
  skippedTables: string[]
  executedStatements: number
  elapsedMs: number
}

/** 恢复接口要求的确认词 */
export const RESTORE_CONFIRM_WORD = 'RESTORE'

/**
 * ponytail: request 实例默认 30s 超时；恢复要先自动备份再重放几十万行、上传/下载是整库文件，
 * 都远超 30s，这几个调用单独放宽到 15 分钟，否则前端先报超时而后端还在跑
 */
const LONG_TIMEOUT_MS = 15 * 60 * 1000

/**
 * 立即备份（POST /backups）：后端立即返回 status=0 的记录，实际导出异步进行
 */
export const triggerBackup = () =>
  request<BackupRecord>({ url: '/backups', method: 'post' })

/**
 * 分页查询备份记录（GET /backups）
 */
export const getBackupList = (params: BackupQuery) =>
  request<PageResponse<BackupRecord>>({ url: '/backups', method: 'get', params })

/**
 * 单条详情（GET /backups/{id}）
 */
export const getBackupDetail = (id: number) =>
  request<BackupRecord>({ url: `/backups/${id}`, method: 'get' })

/**
 * 下载备份文件（GET /backups/{id}/download，流式）
 */
export const downloadBackup = async (id: number, fileName?: string): Promise<void> => {
  const blob = await request<Blob>({
    url: `/backups/${id}/download`,
    method: 'get',
    responseType: 'blob',
    timeout: LONG_TIMEOUT_MS
  })
  await saveBlobAsFile(blob, fileName || `backup_${id}`)
}

/**
 * 删除备份（DELETE /backups/{id}，文件 + 记录）
 */
export const deleteBackup = (id: number) =>
  request<void>({ url: `/backups/${id}`, method: 'delete' })

/**
 * 在线恢复（POST /backups/{id}/restore）；body 必须带 confirm:"RESTORE"
 */
export const restoreBackup = (id: number) =>
  request<BackupRestoreResult>({
    url: `/backups/${id}/restore`,
    method: 'post',
    data: { confirm: RESTORE_CONFIRM_WORD },
    timeout: LONG_TIMEOUT_MS
  })

/**
 * 上传 .sql.gz 作为可恢复备份（POST /backups/upload，multipart）
 */
export const uploadBackup = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request<BackupRecord>({
    url: '/backups/upload',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data: formData,
    timeout: LONG_TIMEOUT_MS
  })
}
