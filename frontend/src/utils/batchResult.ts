import { h } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BatchInputResult } from '@/api/score'

/**
 * 统一提示批量录入 / Excel 导入的结果（契约 1：BatchInputResult）。
 * 全部成功：轻提示并带条数；存在失败项：弹窗逐条列出学生与失败原因。
 * ponytail: 后端逐条校验（学生不在班、分数超满分…），部分成功是常态，不能只报一句「成功」
 *
 * @param result  后端返回的 BatchInputResult；旧版后端返回 void 时为 undefined/null
 * @param action  动作名，用于拼提示文案，如「批量保存」「导入」
 * @param labelOf 把失败项的 studentId 转成可读标签（如学生姓名），默认「学生#id」
 */
export const notifyBatchInputResult = (
  result: BatchInputResult | null | undefined,
  action: string,
  labelOf: (studentId: number) => string = id => `学生#${id}`
): void => {
  const failures = result?.failures ?? []
  const successCount = result?.successCount ?? 0

  if (failures.length === 0) {
    ElMessage.success(result ? `${action}成功，共 ${successCount} 条` : `${action}成功`)
    return
  }

  const failCount = result?.failCount ?? failures.length
  ElMessageBox.alert(
    h('div', [
      h('p', `成功 ${successCount} 条，失败 ${failCount} 条：`),
      h(
        'ul',
        { style: 'margin: 8px 0 0; padding-left: 20px; max-height: 240px; overflow: auto' },
        failures.map(f =>
          h('li', { key: `${f.studentId}-${f.subjectId}` }, `${labelOf(f.studentId)}：${f.reason}`)
        )
      )
    ]),
    `${action}部分失败`,
    { type: 'warning', confirmButtonText: '知道了' }
  ).catch(() => undefined)
}
