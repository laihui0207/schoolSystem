import { authHeader, buildUrl } from '@/utils/request'

/**
 * GET /pdf/score-report?examId&studentId —— 二进制 PDF，不走 Result 信封。
 * H5：fetch + blob + <a download>；App/小程序：uni.downloadFile → uni.openDocument。
 * ponytail: 后端只认 Authorization 头（?token= 仅 /chat/stream 接受），所以 H5 不能用 <a href> 直链，必须 fetch 带头
 */
export const downloadScoreReport = async (examId: number, studentId: number): Promise<void> => {
  const url = buildUrl('/pdf/score-report', { examId, studentId })
  const fileName = `成绩单-${examId}-${studentId}.pdf`

  // #ifdef H5
  const resp = await fetch(url, { headers: authHeader() })
  if (!resp.ok) {
    throw new Error(resp.status === 401 ? '登录已过期，请重新登录' : `下载失败（${resp.status}）`)
  }
  const contentType = resp.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    // 后端以 Result 信封返回了业务错误（如"该考试成绩尚未发布"）
    const body = (await resp.json()) as { message?: string }
    throw new Error(body.message || '下载失败')
  }
  const blob = await resp.blob()
  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(objectUrl)
  return
  // #endif

  // #ifndef H5
  await new Promise<void>((resolve, reject) => {
    uni.downloadFile({
      url,
      header: authHeader(),
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error(res.statusCode === 401 ? '登录已过期，请重新登录' : `下载失败（${res.statusCode}）`))
          return
        }
        uni.openDocument({
          filePath: res.tempFilePath,
          fileType: 'pdf',
          success: () => resolve(),
          fail: (err: UniNamespace.GeneralCallbackResult) => reject(new Error(err.errMsg || '打开文件失败'))
        })
      },
      fail: (err: UniNamespace.GeneralCallbackResult) => reject(new Error(err.errMsg || '下载失败'))
    })
  })
  // #endif
}
