import { ExamStatus, ExamType } from '@/api/exam'

/** Element Plus 标签颜色类型 */
export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/** 状态下拉/展示选项 */
export const statusOptions: ReadonlyArray<{ label: string; value: ExamStatus }> = [
  { label: '待录入', value: ExamStatus.PENDING_INPUT },
  { label: '录入中', value: ExamStatus.RECORDING },
  { label: '待审核', value: ExamStatus.PENDING_REVIEW },
  { label: '已审核', value: ExamStatus.PUBLISHED }
]

/** 考试类型下拉/展示选项 */
export const typeOptions: ReadonlyArray<{ label: string; value: ExamType }> = [
  { label: '月考', value: ExamType.MONTHLY },
  { label: '期中考试', value: ExamType.MIDTERM },
  { label: '期末考试', value: ExamType.FINAL },
  { label: '模拟考试', value: ExamType.MOCK },
  { label: '其他', value: ExamType.OTHER }
]

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

/** 根据类型获取文案 */
export const getTypeText = (type: number): string => typeTextMap[type] ?? '未知'

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
