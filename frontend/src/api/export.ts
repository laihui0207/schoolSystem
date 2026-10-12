import request from '@/utils/request'

/**
 * 成绩明细 Excel 导出。
 *
 * 走统一的 request 实例：baseURL 与 Authorization 由拦截器处理，
 * responseType 为 blob 时拦截器原样返回 Blob，不做 Result 解包。
 * 模板下载与 Excel 导入见 api/score.ts 的 downloadTemplate / importScores。
 */
export async function exportScores(params: { examId: number; classId?: number }): Promise<void> {
  const blob = await request<Blob>({
    url: '/export/scores',
    method: 'get',
    params,
    responseType: 'blob'
  })
  downloadBlob(blob, `成绩导出_${Date.now()}.xlsx`)
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}
