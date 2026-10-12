<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑考试' : '新增考试'"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="handleVisibleChange"
    @close="handleClose"
  >
    <el-steps :active="activeStep" finish-status="success" align-center class="steps">
      <el-step title="基本信息" />
      <el-step title="年级选择" />
      <el-step title="科目配置" />
    </el-steps>

    <!-- 步骤1：基本信息 -->
    <el-form
      v-show="activeStep === 0"
      ref="baseFormRef"
      :model="form"
      :rules="baseRules"
      label-width="90px"
      class="step-body"
    >
      <el-form-item label="考试名称" prop="examName">
        <el-input v-model="form.examName" placeholder="请输入考试名称" clearable />
      </el-form-item>
      <el-form-item label="考试类型" prop="examType">
        <el-select v-model="form.examType" placeholder="请选择考试类型" style="width: 100%">
          <el-option
            v-for="item in typeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="学期" prop="termId">
        <el-select v-model="form.termId" placeholder="请选择学期" style="width: 100%">
          <el-option
            v-for="item in termList"
            :key="item.id"
            :label="termLabel(item)"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="考试日期" prop="examDate">
        <el-date-picker
          v-model="form.examDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择考试日期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="学校" prop="schoolId">
        <el-select
          v-model="form.schoolId"
          placeholder="请选择学校"
          style="width: 100%"
          @change="handleSchoolChange"
        >
          <el-option
            v-for="item in schoolList"
            :key="item.id"
            :label="item.schoolName"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <!-- 步骤2：年级选择 -->
    <div v-show="activeStep === 1" class="step-body">
      <el-alert
        v-if="!form.schoolId"
        title="请先在上一步选择学校"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-checkbox-group v-else v-model="form.gradeIds" class="grade-group">
        <el-checkbox
          v-for="grade in gradeOptions"
          :key="grade.id"
          :value="grade.id!"
          border
          class="grade-item"
        >
          {{ grade.gradeName }}
          <el-tag size="small" type="info" class="class-count">
            {{ grade.classCount }} 个班级
          </el-tag>
        </el-checkbox>
      </el-checkbox-group>
      <div v-if="form.schoolId && gradeOptions.length === 0" class="empty-tip">
        该学校暂无年级数据
      </div>
    </div>

    <!-- 步骤3：科目配置 -->
    <div v-show="activeStep === 2" class="step-body">
      <el-transfer
        v-model="selectedSubjectIds"
        :data="subjectTransferData"
        :titles="['可选科目', '已选科目']"
        filterable
        class="subject-transfer"
        @change="handleSubjectChange"
      />
      <el-table
        v-if="form.subjects.length > 0"
        :data="form.subjects"
        border
        size="small"
        class="subject-table"
      >
        <el-table-column prop="subjectName" label="科目" />
        <el-table-column label="满分" width="200">
          <template #default="{ row }">
            <el-input-number
              v-model="row.fullScore"
              :min="1"
              :max="750"
              :step="10"
              controls-position="right"
              style="width: 140px"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button v-if="activeStep > 0" @click="activeStep--">上一步</el-button>
      <el-button v-if="activeStep < 2" type="primary" @click="handleNext">下一步</el-button>
      <el-button
        v-else
        type="primary"
        :loading="submitLoading"
        @click="handleSubmit"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createExam,
  updateExam,
  ExamType,
  type Exam,
  type ExamSaveData,
  type ExamSubject
} from '@/api/exam'
import { useExamTypeOptions } from './examOptions'
import { getSchoolList, type School } from '@/api/school'
import { getAllTerms, termLabel, type Term } from '@/api/term'
import { getGradesBySchool, type Grade } from '@/api/grade'
import { getAllSubjects, type Subject } from '@/api/subject'
import { getClassesByGrade } from '@/api/class'
import { handleError } from '@/utils/errorHandler'

const props = defineProps<{
  visible: boolean
  /** 编辑时传入的考试数据，新增时为 null */
  editData: Exam | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: []
}>()

const DEFAULT_FULL_SCORE = 100

interface GradeOption extends Grade {
  classCount: number
}

const isEdit = computed(() => !!props.editData?.id)

/** 考试类型下拉：字典 exam_type，失败回落内置表 */
const { options: typeOptions } = useExamTypeOptions()

const activeStep = ref(0)
const submitLoading = ref(false)
const baseFormRef = ref<FormInstance>()

const schoolList = ref<School[]>([])
const termList = ref<Term[]>([])
const gradeOptions = ref<GradeOption[]>([])
const subjectList = ref<Subject[]>([])
const selectedSubjectIds = ref<number[]>([])

const defaultForm = (): ExamSaveData => ({
  schoolId: 0,
  termId: 0,
  examName: '',
  examType: ExamType.MONTHLY,
  examDate: '',
  gradeIds: [],
  subjects: []
})

const form = reactive<ExamSaveData>(defaultForm())

const baseRules: FormRules = {
  examName: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  examType: [{ required: true, message: '请选择考试类型', trigger: 'change' }],
  termId: [{ required: true, message: '请选择学期', trigger: 'change' }],
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }]
}

const subjectTransferData = computed(() =>
  subjectList.value.map((s) => ({ key: s.id!, label: s.subjectName }))
)

const loadSchoolList = async () => {
  try {
    const res = (await getSchoolList({ pageNum: 1, pageSize: 1000 })) as { records: School[] }
    schoolList.value = res.records || []
  } catch (error) {
    handleError(error, '加载学校列表失败')
  }
}

const loadTermList = async () => {
  try {
    termList.value = ((await getAllTerms()) as Term[]) || []
  } catch (error) {
    handleError(error, '加载学期列表失败')
  }
}

const loadSubjectList = async (schoolId?: number) => {
  try {
    subjectList.value = ((await getAllSubjects(schoolId)) as Subject[]) || []
  } catch (error) {
    handleError(error, '加载科目列表失败')
  }
}

/** 加载年级并附带班级数 */
const loadGradeOptions = async (schoolId: number) => {
  try {
    const grades = ((await getGradesBySchool(schoolId)) as Grade[]) || []
    const withCount = await Promise.all(
      grades.map(async (grade) => {
        let classCount = 0
        try {
          const classes = (await getClassesByGrade(grade.id!)) as unknown[]
          classCount = Array.isArray(classes) ? classes.length : 0
        } catch {
          classCount = 0
        }
        return { ...grade, classCount }
      })
    )
    gradeOptions.value = withCount
  } catch (error) {
    handleError(error, '加载年级列表失败')
    gradeOptions.value = []
  }
}

const handleSchoolChange = async (schoolId: number) => {
  form.gradeIds = []
  gradeOptions.value = []
  form.subjects = []
  subjectList.value = []
  if (schoolId) {
    await Promise.all([loadGradeOptions(schoolId), loadSubjectList(schoolId)])
  }
}

const handleSubjectChange = (value: (number | string)[]) => {
  const nextSubjects: ExamSubject[] = value.map((raw) => {
    const id = Number(raw)
    const existing = form.subjects.find((s) => s.subjectId === id)
    if (existing) return existing
    const subject = subjectList.value.find((s) => s.id === id)
    return {
      subjectId: id,
      subjectName: subject?.subjectName ?? '',
      fullScore: DEFAULT_FULL_SCORE
    }
  })
  form.subjects = nextSubjects
}
const handleNext = async () => {
  if (activeStep.value === 0) {
    if (!baseFormRef.value) return
    const valid = await baseFormRef.value.validate().catch(() => false)
    if (!valid) return
  } else if (activeStep.value === 1) {
    if (form.gradeIds.length === 0) {
      ElMessage.warning('请至少选择一个年级')
      return
    }
  }
  activeStep.value++
}

const validateBeforeSubmit = (): boolean => {
  if (!form.examName.trim()) {
    ElMessage.warning('考试名称不能为空')
    activeStep.value = 0
    return false
  }
  if (form.gradeIds.length === 0) {
    ElMessage.warning('请至少选择一个年级')
    activeStep.value = 1
    return false
  }
  if (form.subjects.length === 0) {
    ElMessage.warning('请至少选择一个科目')
    return false
  }
  const invalidScore = form.subjects.some((s) => !s.fullScore || s.fullScore <= 0)
  if (invalidScore) {
    ElMessage.warning('科目满分必须大于0')
    return false
  }
  return true
}

const handleSubmit = async () => {
  if (!validateBeforeSubmit()) return

  submitLoading.value = true
  try {
    const payload: ExamSaveData = {
      schoolId: form.schoolId,
      termId: form.termId,
      examName: form.examName.trim(),
      examType: form.examType,
      examDate: form.examDate || undefined,
      gradeIds: [...form.gradeIds],
      subjects: form.subjects.map((s, index) => ({
        subjectId: s.subjectId,
        fullScore: s.fullScore,
        sortOrder: index + 1
      }))
    }
    if (isEdit.value && props.editData?.id) {
      await updateExam(props.editData.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createExam(payload)
      ElMessage.success('创建成功')
    }
    emit('saved')
    closeDialog()
  } catch (error) {
    handleError(error, '提交失败')
  } finally {
    submitLoading.value = false
  }
}

/** 用编辑数据回填表单 */
const fillForm = async (data: Exam) => {
  form.schoolId = data.schoolId
  form.termId = data.termId
  form.examName = data.examName
  form.examType = data.examType
  form.examDate = data.examDate ?? ''
  form.gradeIds = [...(data.gradeIds ?? [])]
  form.subjects = (data.subjects ?? []).map((s) => ({
    subjectId: s.subjectId,
    subjectName: s.subjectName ?? '',
    fullScore: Number(s.fullScore) || DEFAULT_FULL_SCORE,
    sortOrder: s.sortOrder
  }))
  selectedSubjectIds.value = form.subjects.map((s) => s.subjectId)
  if (data.schoolId) {
    await Promise.all([loadGradeOptions(data.schoolId), loadSubjectList(data.schoolId)])
  }
}

const resetForm = () => {
  Object.assign(form, defaultForm())
  selectedSubjectIds.value = []
  gradeOptions.value = []
  activeStep.value = 0
  baseFormRef.value?.clearValidate()
}

const closeDialog = () => {
  emit('update:visible', false)
}

const handleVisibleChange = (value: boolean) => {
  emit('update:visible', value)
}

const handleClose = () => {
  resetForm()
  closeDialog()
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return
    // 打开时懒加载下拉数据
    if (schoolList.value.length === 0) await loadSchoolList()
    if (termList.value.length === 0) await loadTermList()
    resetForm()
    if (props.editData) {
      await fillForm(props.editData)
    }
  }
)
</script>

<style scoped lang="scss">
.steps {
  margin-bottom: 24px;
}

.step-body {
  min-height: 260px;
}

.grade-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;

  .grade-item {
    margin-right: 0;

    .class-count {
      margin-left: 8px;
    }
  }
}

.empty-tip {
  color: #909399;
  text-align: center;
  padding: 40px 0;
}

.subject-transfer {
  margin-bottom: 16px;
}

.subject-table {
  margin-top: 8px;
}
</style>

