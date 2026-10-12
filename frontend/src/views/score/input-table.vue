<template>
  <div class="score-input-table">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
            <span class="title">成绩录入</span>
          </div>
          <div class="header-info">
            <el-tag>{{ examName }}</el-tag>
            <el-tag type="success">{{ className }}</el-tag>
            <el-tag type="warning">{{ subjectName }}</el-tag>
          </div>
        </div>
      </template>

      <!-- 统计信息 -->
      <div class="statistics-area">
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-label">录入进度</div>
              <div class="stat-value">
                {{ statistics.enteredCount }} / {{ statistics.totalCount }}
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-label">平均分</div>
              <div class="stat-value">{{ statistics.averageScore.toFixed(2) }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-label">最高分 / 最低分</div>
              <div class="stat-value">
                {{ statistics.highestScore }} / {{ statistics.lowestScore }}
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-label">及格率</div>
              <div class="stat-value">{{ statistics.passRate.toFixed(2) }}%</div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 操作按钮 -->
      <div class="toolbar">
        <el-button type="primary" :icon="Select" @click="handleBatchSave">
          批量保存
        </el-button>
        <el-button type="success" :icon="Refresh" @click="loadData">
          刷新数据
        </el-button>
        <div class="auto-save-info">
          <el-icon v-if="autoSaving" class="is-loading"><Loading /></el-icon>
          <span v-if="lastSaveTime">最后保存: {{ lastSaveTime }}</span>
        </div>
      </div>

      <!-- 成绩录入表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        :height="tableHeight"
        style="width: 100%"
      >
        <el-table-column prop="studentNo" label="学号" width="120" fixed />
        <el-table-column prop="studentName" label="姓名" width="100" fixed />
        <el-table-column label="分数" width="150">
          <template #default="{ row }">
            <el-input-number
              v-model="row.score"
              :min="0"
              :max="fullScore"
              :precision="1"
              :disabled="row.isAbsent"
              :controls="false"
              size="small"
              style="width: 100%"
              @change="handleScoreChange(row as TableRow)"
              @blur="handleScoreBlur(row as TableRow)"
              @keyup.enter="handleScoreEnter(row as TableRow)"
            />
          </template>
        </el-table-column>
        <el-table-column label="缺考" width="80" align="center">
          <template #default="{ row }">
            <el-checkbox
              v-model="row.isAbsent"
              @change="handleAbsentChange(row as TableRow)"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">
            <el-input
              v-model="row.remark"
              placeholder="请输入备注"
              size="small"
              @input="handleRemarkInput(row as TableRow)"
              @blur="handleRemarkBlur(row as TableRow)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="row.saving"
              @click="handleSave(row as TableRow)"
            >
              保存
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Select, Refresh, Loading } from '@element-plus/icons-vue'
import {
  getClassScoreList,
  inputScore,
  batchInputScore,
  getScoreStatistics,
  type ClassScoreItem,
  type ScoreStatistics,
  type ScoreInput,
  type BatchScoreInput
} from '@/api/score'
import { getExamDetail, type Exam } from '@/api/exam'
import { getClassDetail, type Class } from '@/api/class'
import { handleError } from '@/utils/errorHandler'
import { notifyBatchInputResult } from '@/utils/batchResult'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const autoSaving = ref(false)
const lastSaveTime = ref('')
const tableHeight = ref(600)
const schoolId = ref(0)

interface TableRow {
  id?: number
  studentId: number
  studentNo: string
  studentName: string
  score?: number
  isAbsent: boolean
  remark?: string
  saving?: boolean
  changed?: boolean
  /** 上次保存被后端拒绝；自动保存跳过该行，用户再次改动后清除 */
  saveError?: boolean
}

// ponytail: F-05 — 未缺考且分数为空的行不能发给后端（会被「未缺考时必须填写分数」拒绝）；handleSave / 自动保存共用这一守卫
const isRowSubmittable = (row: TableRow) => row.isAbsent || (row.score !== undefined && row.score !== null)

// ponytail: F-05 — 自动保存只重发「有改动、未在保存中、可提交、且上次没失败」的行，避免每 5 秒重发坏数据并弹一次错误
const isAutoSavable = (row: TableRow) =>
  !!row.changed && !row.saving && !row.saveError && isRowSubmittable(row)

const tableData = ref<TableRow[]>([])
const examName = ref('')
const className = ref('')
const subjectName = ref('')
const fullScore = ref(100)

const statistics = reactive<ScoreStatistics>({
  totalCount: 0,
  enteredCount: 0,
  averageScore: 0,
  highestScore: 0,
  lowestScore: 0,
  passRate: 0
})

const examId = computed(() => Number(route.query.examId))
const classId = computed(() => Number(route.query.classId))
const subjectId = computed(() => Number(route.query.subjectId))

let autoSaveTimer: number | null = null

const loadData = async () => {
  if (!examId.value || !classId.value || !subjectId.value) {
    ElMessage.error('参数错误')
    router.back()
    return
  }

  loading.value = true
  try {
    const [scoreList, exam, classInfo, stats] = await Promise.all([
      getClassScoreList(examId.value, classId.value, subjectId.value) as Promise<ClassScoreItem[]>,
      getExamDetail(examId.value) as Promise<Exam>,
      getClassDetail(classId.value) as Promise<Class>,
      getScoreStatistics(examId.value, classId.value, subjectId.value) as Promise<ScoreStatistics>
    ])

    tableData.value = scoreList.map(item => ({
      ...item,
      saving: false,
      changed: false,
      saveError: false
    }))

    examName.value = exam.examName
    className.value = classInfo.className || ''
    schoolId.value = classInfo.schoolId ?? 0
    const subject = exam.subjects?.find(s => s.subjectId === subjectId.value)
    subjectName.value = subject?.subjectName || ''
    fullScore.value = subject?.fullScore || 100

    Object.assign(statistics, stats)
  } catch (error) {
    handleError(error, '加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleAbsentChange = (row: TableRow) => {
  if (row.isAbsent) {
    row.score = 0
  }
  row.changed = true
  row.saveError = false
  handleSave(row)
}

// ponytail: 之前只有勾缺考才置 changed，手工改分/备注后 blur 不触发保存、批量保存与自动保存也漏掉这些行
const handleScoreChange = (row: TableRow) => {
  row.changed = true
  // ponytail: F-05 — 用户重新改分即视为要重试，解除自动保存排除
  row.saveError = false
}

const handleRemarkInput = (row: TableRow) => {
  row.changed = true
  row.saveError = false
}

const handleScoreBlur = (row: TableRow) => {
  if (row.changed) {
    handleSave(row)
  }
}

const handleScoreEnter = (row: TableRow) => {
  handleSave(row)
}

const handleRemarkBlur = (row: TableRow) => {
  if (row.changed) {
    handleSave(row)
  }
}

const handleSave = async (row: TableRow) => {
  if (row.saving) return

  if (!isRowSubmittable(row)) {
    return
  }

  row.saving = true
  try {
    const data: ScoreInput = {
      examId: examId.value,
      classId: classId.value,
      schoolId: schoolId.value,
      studentId: row.studentId,
      subjectId: subjectId.value,
      score: row.isAbsent ? 0 : row.score,
      isAbsent: row.isAbsent ? 1 : 0,
      remark: row.remark
    }
    await inputScore(data)
    row.changed = false
    row.saveError = false
    lastSaveTime.value = new Date().toLocaleTimeString()
    await loadStatistics()
  } catch (error) {
    // ponytail: F-05 — 手动保存失败同样打 saveError，否则 5 秒后自动保存会把同一条坏数据原样重发
    row.saveError = true
    handleError(error, '保存失败')
  } finally {
    row.saving = false
  }
}

const handleBatchSave = async () => {
  const changedRows = tableData.value.filter(row => row.changed)
  if (changedRows.length === 0) {
    ElMessage.info('没有需要保存的数据')
    return
  }

  loading.value = true
  try {
    // ponytail: 契约 1 — isAbsent 传 0/1 而非 boolean，不传 schoolId；按返回的 successCount/failures 提示，失败行保留 changed 以便重试
    const payload: BatchScoreInput = {
      examId: examId.value,
      classId: classId.value,
      subjectId: subjectId.value,
      scores: changedRows.map(row => ({
        studentId: row.studentId,
        score: row.isAbsent ? 0 : row.score,
        isAbsent: row.isAbsent ? 1 : 0,
        remark: row.remark
      }))
    }
    const result = await batchInputScore(payload)
    const failedStudentIds = new Set((result?.failures ?? []).map(f => f.studentId))
    // ponytail: F-05 — 被后端拒绝的行保留 changed 以便手动重试，但打 saveError 让自动保存跳过
    changedRows.forEach(row => {
      const failed = failedStudentIds.has(row.studentId)
      row.changed = failed
      row.saveError = failed
    })
    lastSaveTime.value = new Date().toLocaleTimeString()
    notifyBatchInputResult(result, '批量保存', id =>
      tableData.value.find(r => r.studentId === id)?.studentName ?? `学生#${id}`
    )
    await loadStatistics()
  } catch (error) {
    handleError(error, '批量保存失败')
  } finally {
    loading.value = false
  }
}

const loadStatistics = async () => {
  try {
    const stats = (await getScoreStatistics(
      examId.value,
      classId.value,
      subjectId.value
    )) as ScoreStatistics
    Object.assign(statistics, stats)
  } catch (error) {
    console.error('加载统计信息失败', error)
  }
}

const startAutoSave = () => {
  autoSaveTimer = window.setInterval(async () => {
    const changedRows = tableData.value.filter(isAutoSavable)
    if (changedRows.length === 0) return

    autoSaving.value = true
    try {
      // ponytail: F-05 — 逐行 allSettled：失败行打 saveError 退出自动保存，成功行正常清 changed，互不影响
      const settled = await Promise.allSettled(changedRows.map(row => {
        const data: ScoreInput = {
          examId: examId.value,
          classId: classId.value,
          schoolId: schoolId.value,
          studentId: row.studentId,
          subjectId: subjectId.value,
          score: row.isAbsent ? 0 : row.score,
          isAbsent: row.isAbsent ? 1 : 0,
          remark: row.remark
        }
        return inputScore(data)
      }))
      settled.forEach((result, index) => {
        const row = changedRows[index]
        if (result.status === 'fulfilled') {
          row.changed = false
          row.saveError = false
        } else {
          row.saveError = true
        }
      })
      if (settled.some(r => r.status === 'fulfilled')) {
        lastSaveTime.value = new Date().toLocaleTimeString()
        await loadStatistics()
      }
    } finally {
      autoSaving.value = false
    }
  }, 5000)
}

const stopAutoSave = () => {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
}

const handleBack = () => {
  router.back()
}

const calculateTableHeight = () => {
  const windowHeight = window.innerHeight
  tableHeight.value = windowHeight - 400
}

onMounted(() => {
  loadData()
  startAutoSave()
  calculateTableHeight()
  window.addEventListener('resize', calculateTableHeight)
})

onUnmounted(() => {
  stopAutoSave()
  window.removeEventListener('resize', calculateTableHeight)
})
</script>

<style scoped lang="scss">
.score-input-table {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-left {
      display: flex;
      align-items: center;
      gap: 15px;

      .title {
        font-size: 18px;
        font-weight: bold;
      }
    }

    .header-info {
      display: flex;
      gap: 10px;
    }
  }

  .statistics-area {
    margin-bottom: 20px;
    padding: 20px;
    background-color: #f5f7fa;
    border-radius: 4px;

    .stat-item {
      text-align: center;

      .stat-label {
        font-size: 14px;
        color: #909399;
        margin-bottom: 8px;
      }

      .stat-value {
        font-size: 24px;
        font-weight: bold;
        color: #303133;
      }
    }
  }

  .toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 15px;

    .auto-save-info {
      margin-left: auto;
      display: flex;
      align-items: center;
      gap: 8px;
      color: #909399;
      font-size: 14px;

      .is-loading {
        animation: rotating 2s linear infinite;
      }
    }
  }

  :deep(.el-input-number) {
    .el-input__inner {
      text-align: left;
    }
  }
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>

