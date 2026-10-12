import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import type { AxiosRequestConfig } from 'axios'

/**
 * 导入导出 / PDF 下载。
 *
 * 走统一的 request 实例：baseURL 与 Authorization 由拦截器处理，
 * responseType 为 blob 时拦截器原样返回 Blob，不做 Result 解包。
 * 模板下载与 Excel 导入见 api/score.ts 的 downloadTemplate / importScores。
 */

export interface ExamScopedQuery {
  examId: number
  classId?: number
}

/**
 * 成绩明细 Excel 导出（GET /export/scores）
 */
export async function exportScores(params: ExamScopedQuery): Promise<void> {
  await downloadFile({ url: '/export/scores', params }, `成绩导出_${Date.now()}.xlsx`)
}

/**
 * 统计报表 Excel 导出（GET /export/stat-report；classId 为空时全校各班）
 */
export async function exportStatReport(params: ExamScopedQuery): Promise<void> {
  await downloadFile({ url: '/export/stat-report', params }, `统计报表_${Date.now()}.xlsx`)
}

/**
 * 单个学生成绩单 PDF（GET /pdf/score-report）；label 用作文件名，缺省用 studentId
 */
export async function downloadScoreReportPdf(
  examId: number,
  studentId: number,
  label?: string | number
): Promise<void> {
  await downloadFile(
    { url: '/pdf/score-report', params: { examId, studentId } },
    `成绩单_${label ?? studentId}.pdf`
  )
}

/**
 * 全班成绩单 PDF（GET /pdf/class-reports，每人一页）；label 用作文件名，缺省用 classId
 */
export async function downloadClassReportsPdf(
  examId: number,
  classId: number,
  label?: string | number
): Promise<void> {
  await downloadFile(
    { url: '/pdf/class-reports', params: { examId, classId } },
    `成绩单_${label ?? classId}.pdf`
  )
}

async function downloadFile(config: AxiosRequestConfig, filename: string): Promise<void> {
  const blob = await request<Blob>({ ...config, method: 'get', responseType: 'blob' })
  const binary = await ensureBinary(blob)
  saveBlob(binary, filename)
}

/**
 * ponytail: blob 请求下后端抛 BusinessException 时返回的是 HTTP 200 + JSON 信封，拦截器不解包，
 * 直接存盘会得到一个内容是 JSON 的「.pdf/.xlsx」；这里识别出来、弹后端原文并中断
 */
async function ensureBinary(blob: Blob): Promise<Blob> {
  if (!blob.type || !blob.type.includes('application/json')) {
    return blob
  }
  let message = '下载失败'
  try {
    const body = JSON.parse(await blob.text()) as { message?: string }
    message = body.message || message
  } catch {
    // 非 JSON 文本，保留默认提示
  }
  ElMessage.error(message)
  throw new Error(message)
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
