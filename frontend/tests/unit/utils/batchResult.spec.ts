import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  ElMessageBox: { alert: vi.fn(() => Promise.resolve()) }
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import { notifyBatchInputResult } from '@/utils/batchResult'

/** 递归抽取 h() 生成的 VNode 文本，便于断言弹窗内容 */
const textOf = (node: unknown): string => {
  if (node == null) return ''
  if (typeof node === 'string') return node
  if (Array.isArray(node)) return node.map(textOf).join('')
  return textOf((node as { children?: unknown }).children)
}

describe('notifyBatchInputResult', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('全部成功时轻提示并带成功条数', () => {
    notifyBatchInputResult({ successCount: 3, failCount: 0, failures: [] }, '批量保存')

    expect(ElMessage.success).toHaveBeenCalledWith('批量保存成功，共 3 条')
    expect(ElMessageBox.alert).not.toHaveBeenCalled()
  })

  it('后端未返回结果体时退化为无条数的成功提示', () => {
    notifyBatchInputResult(undefined, '导入')

    expect(ElMessage.success).toHaveBeenCalledWith('导入成功')
    expect(ElMessageBox.alert).not.toHaveBeenCalled()
  })

  it('存在失败项时弹窗逐条列出学生与原因', () => {
    notifyBatchInputResult(
      {
        successCount: 1,
        failCount: 1,
        failures: [{ studentId: 7, subjectId: 2, reason: '分数超过满分' }]
      },
      '批量保存',
      id => `张三(${id})`
    )

    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(ElMessageBox.alert).toHaveBeenCalledTimes(1)

    const [vnode, title] = vi.mocked(ElMessageBox.alert).mock.calls[0]
    expect(title).toBe('批量保存部分失败')
    const text = textOf(vnode)
    expect(text).toContain('成功 1 条，失败 1 条')
    expect(text).toContain('张三(7)：分数超过满分')
  })
})
