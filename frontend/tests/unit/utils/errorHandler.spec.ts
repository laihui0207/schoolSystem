import { describe, it, expect, beforeEach, vi } from 'vitest'
import { AxiosError } from 'axios'

// errorHandler 只用到 ElMessage；按其它 spec 的做法本地 mock，避免 element-plus 解析问题
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  }
}))

import { ElMessage } from 'element-plus'
import { handleError, handleAsyncError } from '@/utils/errorHandler'

/**
 * F-08 回归保护：
 * 1. 调用方传入的上下文文案优先，error.message 只在未传文案时兜底；
 * 2. HTTP 层失败（带 response 的 AxiosError）已由 request.ts 拦截器弹过，这里不再重复弹。
 */
describe('handleError', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('caller message wins over error.message', () => {
    handleError(new Error('分数不能为空'), '加载数据失败')
    expect(ElMessage.error).toHaveBeenCalledTimes(1)
    expect(ElMessage.error).toHaveBeenCalledWith('加载数据失败')
  })

  it('falls back to error.message when no caller message', () => {
    handleError(new Error('分数不能为空'))
    expect(ElMessage.error).toHaveBeenCalledWith('分数不能为空')
  })

  it('uses string error when no caller message', () => {
    handleError('直接是字符串')
    expect(ElMessage.error).toHaveBeenCalledWith('直接是字符串')
  })

  it('uses default text when nothing usable is available', () => {
    handleError(undefined)
    expect(ElMessage.error).toHaveBeenCalledWith('操作失败')
    vi.clearAllMocks()
    handleError(new Error(''))
    expect(ElMessage.error).toHaveBeenCalledWith('操作失败')
  })

  it('does NOT toast again for an AxiosError that has a response (interceptor already did)', () => {
    const err = new AxiosError(
      'Request failed with status code 500',
      'ERR_BAD_RESPONSE',
      undefined,
      undefined,
      { status: 500, data: {}, statusText: '', headers: {}, config: {} as any }
    )
    handleError(err, '加载数据失败')
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('still toasts the caller message for an AxiosError without response (network error)', () => {
    const err = new AxiosError('Network Error', 'ERR_NETWORK')
    handleError(err, '加载数据失败')
    expect(ElMessage.error).toHaveBeenCalledWith('加载数据失败')
  })
})

describe('handleAsyncError', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns the value on success without toasting', async () => {
    const value = await handleAsyncError(async () => 42, '不该出现')
    expect(value).toBe(42)
    expect(ElMessage.error).not.toHaveBeenCalled()
  })

  it('returns null and toasts the caller message on failure', async () => {
    const value = await handleAsyncError(async () => {
      throw new Error('boom')
    }, '保存失败')
    expect(value).toBeNull()
    expect(ElMessage.error).toHaveBeenCalledWith('保存失败')
  })
})
