import { computed, ref } from 'vue'
import type { StudentResponse } from '@/types'
import { getStudent } from '@/api/student'
import { userStore } from '@/store/user'

export interface ChildOption {
  studentId: number
  label: string
  student: StudentResponse | null
}

/** 已加载的学生档案缓存（student.id → StudentResponse|null），跨页面共享 */
const cache = ref<Record<number, StudentResponse | null>>({})

const classLabel = (s: StudentResponse | null): string => {
  const c = s?.classes?.[0]
  if (!c) return ''
  return [c.gradeName, c.className].filter(Boolean).join(' ')
}

/** 加载一个学生档案；失败缓存 null，避免重复请求 */
export const loadStudent = async (studentId: number): Promise<StudentResponse | null> => {
  const hit = cache.value[studentId]
  if (hit !== undefined) return hit
  try {
    const s = await getStudent(studentId)
    cache.value = { ...cache.value, [studentId]: s }
    return s
  } catch {
    cache.value = { ...cache.value, [studentId]: null }
    return null
  }
}

/**
 * 家长的孩子列表（含姓名/班级），学生身份则只有本人一项。
 */
export const useChildren = () => {
  const ids = computed<number[]>(() => {
    if (userStore.isParent.value) return userStore.childStudentIds.value
    const self = userStore.userInfo.value?.studentId
    return self ? [self] : []
  })

  const options = computed<ChildOption[]>(() =>
    ids.value.map((studentId) => {
      const student = cache.value[studentId] ?? null
      const name = student?.name ?? `学生 #${studentId}`
      const cls = classLabel(student)
      return { studentId, label: cls ? `${name} · ${cls}` : name, student }
    })
  )

  const loadAll = async (): Promise<void> => {
    await Promise.all(ids.value.map((id) => loadStudent(id)))
  }

  return { ids, options, loadAll, loadStudent, classLabel }
}
