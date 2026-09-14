import { describe, it, expect, vi } from 'vitest'

// term.ts 经 @/utils/request 间接依赖 element-plus
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
}))

import { termLabel, TERM_NAMES, TERM_STATUS, type Term } from '@/api/term'

const term = (over: Partial<Term> = {}): Term => ({
  year: '2024-2025',
  term: 1,
  startDate: '2024-09-01',
  endDate: '2025-01-31',
  ...over
})

describe('termLabel', () => {
  it('拼出 学年 + 学期名', () => {
    expect(termLabel(term())).toBe('2024-2025 第一学期')
    expect(termLabel(term({ term: 2 }))).toBe('2024-2025 第二学期')
  })

  it('term 缺失时只显示学年，不出现 undefined', () => {
    expect(termLabel(term({ term: undefined }))).toBe('2024-2025')
  })

  it('空值返回占位符', () => {
    expect(termLabel(null)).toBe('-')
    expect(termLabel(undefined)).toBe('-')
    expect(termLabel(term({ year: '', term: undefined }))).toBe('-')
  })
})

describe('后端契约常量', () => {
  it('状态语义与后端一致：1-进行中，0-已结束', () => {
    expect(TERM_STATUS.ACTIVE).toBe(1)
    expect(TERM_STATUS.ENDED).toBe(0)
  })

  it('学期只有 1、2 两个取值', () => {
    expect(Object.keys(TERM_NAMES)).toEqual(['1', '2'])
  })
})
