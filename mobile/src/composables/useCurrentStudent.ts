import { computed } from 'vue'
import { userStore } from '@/store/user'

/**
 * 当前"被查看的学生" student.id：
 * - 学生(userType=4)：userInfo.studentId（student 表主键，不是 userInfo.id）
 * - 家长(userType=5)：childStudentIds[选中项]；没有孩子 → null，页面提示联系学校绑定
 * - 其他身份：null（本端只服务学生/家长）
 */
export const useCurrentStudent = () => {
  const studentId = computed<number | null>(() => {
    const info = userStore.userInfo.value
    if (!info) return null
    if (userStore.isStudent.value) return info.studentId ?? null
    if (userStore.isParent.value) return userStore.selectedChildId.value
    return null
  })

  const noChildBound = computed(() => userStore.isParent.value && userStore.childStudentIds.value.length === 0)

  const emptyHint = computed(() => {
    if (!userStore.userInfo.value) return '请先登录'
    if (noChildBound.value) return '尚未绑定孩子，请联系学校完成绑定'
    if (userStore.isStudent.value && studentId.value === null) return '账号未关联学生档案，请联系学校'
    if (!userStore.isStudent.value && !userStore.isParent.value) return '移动端仅面向学生与家长'
    return ''
  })

  return {
    studentId,
    noChildBound,
    emptyHint,
    isParent: userStore.isParent,
    childStudentIds: userStore.childStudentIds,
    selectChild: userStore.selectChild
  }
}
