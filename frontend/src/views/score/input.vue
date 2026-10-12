<template>
  <div class="score-input">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>成绩录入</span>
        </div>
      </template>

      <!-- 选择区 -->
      <el-form :model="selectForm" label-width="100px" class="select-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="选择考试" required>
              <el-select
                v-model="selectForm.examId"
                placeholder="请选择考试"
                style="width: 100%"
                clearable
                @change="handleExamChange"
              >
                <el-option
                  v-for="item in examList"
                  :key="item.id"
                  :label="item.examName"
                  :value="item.id!"
                >
                  <span>{{ item.examName }}</span>
                  <span style="float: right; color: #8492a6; font-size: 13px">
                    {{ item.examDate }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="选择班级" required>
              <el-select
                v-model="selectForm.classId"
                placeholder="请先选择考试"
                style="width: 100%"
                clearable
                :disabled="!selectForm.examId"
                @change="handleClassChange"
              >
                <el-option
                  v-for="item in classList"
                  :key="item.id"
                  :label="item.className"
                  :value="item.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="选择科目" required>
              <el-select
                v-model="selectForm.subjectId"
                placeholder="请先选择考试"
                style="width: 100%"
                clearable
                :disabled="!selectForm.examId"
                @change="handleSubjectChange"
              >
                <el-option
                  v-for="item in subjectList"
                  :key="item.subjectId"
                  :label="item.subjectName"
                  :value="item.subjectId"
                >
                  <span>{{ item.subjectName }}</span>
                  <span style="float: right; color: #8492a6; font-size: 13px">
                    满分: {{ item.fullScore }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="录入进度">
              <div class="progress-info">
                <span v-if="progressInfo">
                  已录入: <el-tag type="success">{{ progressInfo.enteredCount }}</el-tag> /
                  总人数: <el-tag>{{ progressInfo.totalCount }}</el-tag>
                  <el-progress
                    :percentage="progressPercentage"
                    :color="progressColor"
                    style="margin-left: 10px; flex: 1"
                  />
                </span>
                <span v-else class="text-muted">请选择考试、班级和科目</span>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 操作区 -->
      <div class="action-area">
        <el-button
          type="primary"
          size="large"
          :disabled="!canStartInput"
          @click="handleStartInput"
        >
          开始录入
        </el-button>
        <el-button
          type="success"
          size="large"
          :disabled="!canStartInput"
          @click="handleImport"
        >
          导入Excel
        </el-button>
        <el-button size="large" @click="handleDownloadTemplate">
          下载模板
        </el-button>
      </div>
    </el-card>

    <!-- Excel导入对话框 -->
    <ScoreImport
      v-model:visible="importDialogVisible"
      :exam-id="selectForm.examId"
      :class-id="selectForm.classId"
      :subject-id="selectForm.subjectId"
      :full-score="selectedSubject?.fullScore"
      @success="handleImportSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamList, type Exam, ExamStatus, type ExamSubject } from '@/api/exam'
import { getClassesByGrade, type Class } from '@/api/class'
import { getScoreStatistics, downloadTemplate, type ScoreStatistics } from '@/api/score'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'
import ScoreImport from '@/components/ScoreImport.vue'

const router = useRouter()

const examList = ref<Exam[]>([])
const classList = ref<Class[]>([])
const subjectList = ref<ExamSubject[]>([])
const progressInfo = ref<ScoreStatistics | null>(null)
const importDialogVisible = ref(false)

const selectForm = reactive({
  examId: undefined as number | undefined,
  classId: undefined as number | undefined,
  subjectId: undefined as number | undefined
})

const selectedExam = computed(() => {
  return examList.value.find(e => e.id === selectForm.examId)
})

// ponytail: 导入校验用的满分来自当前考试科目配置（exam.subjects[].fullScore），不再固定 100
const selectedSubject = computed(() => {
  return subjectList.value.find(s => s.subjectId === selectForm.subjectId)
})

const progressPercentage = computed(() => {
  if (!progressInfo.value) return 0
  const { enteredCount, totalCount } = progressInfo.value
  return totalCount > 0 ? Math.round((enteredCount / totalCount) * 100) : 0
})

const progressColor = computed(() => {
  const percentage = progressPercentage.value
  if (percentage === 100) return '#67c23a'
  if (percentage >= 50) return '#409eff'
  return '#e6a23c'
})

const canStartInput = computed(() => {
  return !!(selectForm.examId && selectForm.classId && selectForm.subjectId)
})

const loadExamList = async () => {
  try {
    const res = (await getExamList({
      status: ExamStatus.RECORDING,
      pageNum: 1,
      pageSize: 100
    })) as PageResponse<Exam>
    examList.value = res.records || []
  } catch (error) {
    handleError(error, '加载考试列表失败')
  }
}

const handleExamChange = async () => {
  selectForm.classId = undefined
  selectForm.subjectId = undefined
  classList.value = []
  subjectList.value = []
  progressInfo.value = null

  if (!selectForm.examId || !selectedExam.value) return

  subjectList.value = selectedExam.value.subjects || []

  await loadClassList()
}

const loadClassList = async () => {
  if (!selectedExam.value || !selectedExam.value.gradeIds.length) return

  try {
    const allClasses: Class[] = []
    for (const gradeId of selectedExam.value.gradeIds) {
      const classes = (await getClassesByGrade(gradeId)) as Class[]
      allClasses.push(...classes)
    }
    classList.value = allClasses
  } catch (error) {
    handleError(error, '加载班级列表失败')
  }
}

const handleClassChange = () => {
  loadProgress()
}

const handleSubjectChange = () => {
  loadProgress()
}

const loadProgress = async () => {
  if (!selectForm.examId || !selectForm.classId || !selectForm.subjectId) {
    progressInfo.value = null
    return
  }

  try {
    progressInfo.value = (await getScoreStatistics(
      selectForm.examId,
      selectForm.classId,
      selectForm.subjectId
    )) as ScoreStatistics
  } catch (error) {
    handleError(error, '加载录入进度失败')
  }
}

const handleStartInput = () => {
  if (!canStartInput.value) return

  router.push({
    name: 'ScoreInputTable',
    query: {
      examId: selectForm.examId,
      classId: selectForm.classId,
      subjectId: selectForm.subjectId
    }
  })
}

const handleImport = () => {
  if (!canStartInput.value) return
  importDialogVisible.value = true
}

const handleDownloadTemplate = async () => {
  try {
    const blob = (await downloadTemplate()) as Blob
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '成绩导入模板.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('模板下载成功')
  } catch (error) {
    handleError(error, '模板下载失败')
  }
}

// ponytail: 成功/失败明细已由 ScoreImport 按 BatchInputResult 提示，这里只刷新录入进度，避免重复弹「导入成功」
const handleImportSuccess = () => {
  loadProgress()
}

onMounted(() => {
  loadExamList()
})
</script>

<style scoped lang="scss">
.score-input {
  .card-header {
    font-size: 18px;
    font-weight: bold;
  }

  .select-form {
    margin-bottom: 30px;
  }

  .progress-info {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;

    .text-muted {
      color: #909399;
    }
  }

  .action-area {
    display: flex;
    justify-content: center;
    gap: 20px;
    padding: 20px 0;
    border-top: 1px solid #ebeef5;
  }
}
</style>
