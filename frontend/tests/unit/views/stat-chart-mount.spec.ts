import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

/**
 * 回归：统计页图表容器在 v-if="loading" 分支下不挂载，
 * 若在 nextTick 之后才关闭 loading，echarts.init 拿到的 ref 是 undefined，
 * 图表区域永久空白。要求 loading.value = false 出现在 await nextTick() 之前。
 */
const VIEWS = [
  'exam-overview.vue',
  'class-analysis.vue',
  'student-trend.vue',
  'subject-compare.vue'
]

const read = (name: string) =>
  readFileSync(resolve(__dirname, '../../../src/views/stat', name), 'utf-8')

describe('统计页图表挂载时序', () => {
  it.each(VIEWS)('%s 先关闭 loading 再等 DOM', name => {
    const src = read(name)

    // 图表容器确实被 loading 分支包住，前提成立才有这条约束
    expect(src).toContain('v-if="loading"')
    expect(src).toContain('chart-container')

    const closeAt = src.indexOf('loading.value = false')
    const tickAt = src.indexOf('await nextTick()')

    expect(closeAt).toBeGreaterThan(-1)
    expect(tickAt).toBeGreaterThan(-1)
    expect(closeAt).toBeLessThan(tickAt)
  })

  it.each(VIEWS)('%s 的 init*Chart 调用在 nextTick 之后', name => {
    const src = read(name)
    const tickAt = src.indexOf('await nextTick()')
    const initAt = src.search(/\n\s+init[A-Za-z]*Chart\(\)/)

    expect(initAt).toBeGreaterThan(tickAt)
  })
})
