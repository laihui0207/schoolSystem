<template>
  <div class="exam-detail" v-loading="loading">
    <el-page-header class="page-header" @back="handleBack">
      <template #content>
        <span>考试详情</span>
      </template>
    </el-page-header>

    <template v-if="exam">
      <!-- 顶部：基本信息 -->
      <el-card class="section">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <div class="header-actions">
              <el-tag :type="getStatusType(exam.status)" size="large" effect="dark">
                {{ getStatusText(exam.status) }}
              </el-tag>
              <el-button
                v-if="statusAction"
                :type="statusAction.buttonType"
                @click="handleStatusAction"
              >
                {{ statusAction.label }}
              </el-button>
            </div>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="考试名称">{{ exam.examName }}</el-descriptions-item>
          <el-descriptions-item label="考试类型">
            {{ getTypeText(exam.examType) }}
          </el-descriptions-item>
          <!-- 后端 ExamResponse.termName 声明了但从未赋值，这里恒为 '-'，需后端补 join -->
          <el-descriptions-item label="学期">{{ exam.termName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="考试日期">{{ exam.examDate || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 中部：参考范围 -->
      <el-card class="section">
        <template #header><span>参考范围</span></template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="学校">{{ exam.schoolName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="参考年级">
            <el-tag
              v-for="name in exam.gradeNames || []"
              :key="name"
              class="range-tag"
              type="info"
            >
              {{ name }}
            </el-tag>
            <span v-if="!(exam.gradeNames && exam.gradeNames.length)">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="考试科目">
            <el-tag
              v-for="subject in exam.subjects || []"
              :key="subject.subjectId"
              class="range-tag"
              type="success"
            >
              {{ subject.subjectName }}（满分 {{ subject.fullScore }}）
            </el-tag>
            <span v-if="!(exam.subjects && exam.subjects.length)">-</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 底部：录入进度 -->
      <el-card class="section">
        <template #header><span>录入进度</span></template>
        <el-table v-loading="progressLoading" :data="progressList" border stripe>
          <el-table-column prop="gradeName" label="年级" width="120" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column prop="subjectName" label="科目" width="120" />
          <el-table-column label="录入人数" width="140">
            <template #default="{ row }">
              {{ row.enteredCount }} / {{ row.totalCount }}
            </template>
          </el-table-column>
          <el-table-column label="进度" min-width="200">
            <template #default="{ row }">
              <el-progress
                :percentage="calcPercent(row)"
                :status="progressStatus(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="goInput(row)">去录入</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <span>暂无录入进度数据</span>
          </template>
        </el-table>
      </el-card>
    </template>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getExamDetail,
  getExamProgress,
  startExamInput,
  submitExamReview,
  publishExam,
  type Exam,
  type ExamProgressItem
} from '@/api/exam'
import {
  getStatusType,
  getStatusText,
  getTypeText,
  getStatusAction
} from './examOptions'
import { handleError } from '@/utils/errorHandler'

const route = useRoute()
const router = useRouter()

const examId = Number(route.params.id)

const loading = ref(false)
const progressLoading = ref(false)
const exam = ref<Exam | null>(null)
const progressList = ref<ExamProgressItem[]>([])

const statusAction = computed(() =>
  exam.value ? getStatusAction(exam.value.status) : null
)

const calcPercent = (row: ExamProgressItem): number => {
  if (!row.totalCount) return 0
  return Math.round((row.enteredCount / row.totalCount) * 100)
}

const progressStatus = (row: ExamProgressItem): '' | 'success' => {
  const percent = calcPercent(row)
  // 已完成显示绿色，其余（未开始灰 / 进行中蓝）用默认色
  return percent >= 100 ? 'success' : ''
}

const loadDetail = async () => {
  loading.value = true
  try {
    exam.value = await getExamDetail(examId)
  } catch (error) {
    handleError(error, '加载考试详情失败')
  } finally {
    loading.value = false
  }
}

const loadProgress = async () => {
  progressLoading.value = true
  try {
    const res = await getExamProgress(examId)
    progressList.value = res?.items || []
  } catch (error) {
    // 进度接口可能尚未就绪，静默降级为空列表而非阻塞页面
    progressList.value = []
  } finally {
    progressLoading.value = false
  }
}

const handleStatusAction = () => {
  const action = statusAction.value
  if (!action || !exam.value) return
  ElMessageBox.confirm(`确定要「${action.label}」吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        if (action.action === 'start-input') {
          await startExamInput(examId)
        } else if (action.action === 'submit-review') {
          await submitExamReview(examId)
        } else {
          await publishExam(examId)
        }
        ElMessage.success(`${action.label}成功`)
        await loadDetail()
        await loadProgress()
      } catch (error) {
        handleError(error, `${action.label}失败`)
      }
    })
    .catch(() => undefined)
}

const goInput = (row: ExamProgressItem) => {
  // ponytail: /score 是成绩管理入口页不读 query，原跳转等于丢参；直接进录入表格路由并带上三元组
  router.push({
    name: 'ScoreInputTable',
    query: {
      examId: String(examId),
      classId: String(row.classId),
      subjectId: String(row.subjectId)
    }
  })
}

const handleBack = () => {
  router.push('/exam')
}

onMounted(() => {
  loadDetail()
  loadProgress()
})
</script>

<style scoped lang="scss">
.exam-detail {
  .page-header {
    margin-bottom: 16px;
  }

  .section {
    margin-bottom: 16px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .header-actions {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .range-tag {
    margin-right: 8px;
    margin-bottom: 4px;
  }
}
</style>
