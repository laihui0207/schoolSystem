import { ExamStatus, ExamType } from '@/api/exam'
import { useDict, type DictHandle } from '@/composables/useDict'

/** Element Plus 标签颜色类型 */
export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/** 状态下拉/展示选项 */
export const statusOptions: ReadonlyArray<{ label: string; value: ExamStatus }> = [
  { label: '待录入', value: ExamStatus.PENDING_INPUT },
  { label: '录入中', value: ExamStatus.RECORDING },
  { label: '待审核', value: ExamStatus.PENDING_REVIEW },
  { label: '已审核', value: ExamStatus.PUBLISHED }
]

/**
 * 考试类型默认选项：字典 exam_type 不可用时的回落表（与 V7 种子一致）。
 * 页面下拉请用 useExamTypeOptions()，不要直接渲染这张表。
 */
export const typeOptions: ReadonlyArray<{ label: string; value: ExamType }> = [
  { label: '月考', value: ExamType.MONTHLY },
  { label: '期中考试', value: ExamType.MIDTERM },
  { label: '期末考试', value: ExamType.FINAL },
  { label: '模拟考试', value: ExamType.MOCK },
  { label: '其他', value: ExamType.OTHER }
]

/** 考试类型的字典编码（sys_dict_type.dict_type） */
export const EXAM_TYPE_DICT = 'exam_type'

let examTypeDict: DictHandle | null = null

/**
 * 考试类型下拉：优先 sys_dict_data(exam_type)，请求失败或字典为空时回落 typeOptions
 *
 * ponytail: 模块级单例——列表页、弹窗、详情页共用一份 options，getTypeText 也从这里取 label，
 * 保证下拉文案与表格展示文案同源；dict_value 是字符串，这里按 numeric 换算成与 ExamType 一致的数字。
 */
export const useExamTypeOptions = (): DictHandle => {
  if (!examTypeDict) {
    examTypeDict = useDict(EXAM_TYPE_DICT, typeOptions, { numeric: true })
  }
  return examTypeDict
}

const statusColorMap: Record<number, TagType> = {
  [ExamStatus.PENDING_INPUT]: 'info',
  [ExamStatus.RECORDING]: 'warning',
  [ExamStatus.PENDING_REVIEW]: 'primary',
  [ExamStatus.PUBLISHED]: 'success'
}

const statusTextMap: Record<number, string> = {
  [ExamStatus.PENDING_INPUT]: '待录入',
  [ExamStatus.RECORDING]: '录入中',
  [ExamStatus.PENDING_REVIEW]: '待审核',
  [ExamStatus.PUBLISHED]: '已发布'
}

const typeTextMap: Record<number, string> = {
  [ExamType.MONTHLY]: '月考',
  [ExamType.MIDTERM]: '期中考试',
  [ExamType.FINAL]: '期末考试',
  [ExamType.MOCK]: '模拟考试',
  [ExamType.OTHER]: '其他'
}

/** 根据状态获取标签颜色 */
export const getStatusType = (status: number): TagType => statusColorMap[status] ?? 'info'

/** 根据状态获取文案 */
export const getStatusText = (status: number): string => statusTextMap[status] ?? '未知'

/** 根据类型获取文案：字典已加载时用字典标签，否则用内置表 */
export const getTypeText = (type: number): string =>
  useExamTypeOptions().label(type, typeTextMap[type] ?? '未知')

/**
 * 状态操作按钮配置
 * 按当前状态返回下一步动作，null 表示无可用操作
 */
export interface StatusAction {
  label: string
  /** 目标接口动作 */
  action: 'start-input' | 'submit-review' | 'publish'
  buttonType: TagType
}

export const getStatusAction = (status: number): StatusAction | null => {
  switch (status) {
    case ExamStatus.PENDING_INPUT:
      return { label: '开始录入', action: 'start-input', buttonType: 'success' }
    case ExamStatus.RECORDING:
      return { label: '提交审核', action: 'submit-review', buttonType: 'warning' }
    case ExamStatus.PENDING_REVIEW:
      return { label: '发布成绩', action: 'publish', buttonType: 'primary' }
    default:
      return null
  }
}
