import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一错误处理工具
 *
 * 规则：
 * 1. HTTP 层失败（带 response 的 AxiosError）由 request.ts 拦截器统一弹中文提示，这里直接返回，不再弹一遍英文 message；
 * 2. 调用方传入的上下文文案优先；error.message 只在未传文案时兜底。
 */
export function handleError(error: unknown, customMessage?: string): void {
  // ponytail: F-08 — 拦截器已对 401/403/404/500 弹过提示，再弹「Request failed with status code 500」属重复且是英文
  if (axios.isAxiosError(error) && error.response) {
    return
  }

  let message = customMessage || '操作失败'

  // ponytail: F-08 — 恢复「调用方文案优先」，避免中文上下文被 error.message 覆盖
  if (!customMessage) {
    if (error instanceof Error && error.message) {
      message = error.message
    } else if (typeof error === 'string' && error) {
      message = error
    }
  }

  ElMessage.error(message)
}

/**
 * 处理异步操作错误
 */
export async function handleAsyncError<T>(
  asyncFn: () => Promise<T>,
  errorMessage?: string
): Promise<T | null> {
  try {
    return await asyncFn()
  } catch (error) {
    handleError(error, errorMessage)
    return null
  }
}
