<template>
  <div class="review-detail">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button :icon="ArrowLeft" @click="handleBack">返回</el-button>
            <span class="title">成绩审核详情</span>
          </div>
          <div class="header-status">
            <el-tag :type="getStatusType(detail.status)" size="large">
              {{ getStatusText(detail.status) }}
            </el-tag>
          </div>
        </div>
      </template>

      <el-row :gutter="20">
        <!-- 左侧主要内容 -->
        <el-col :span="18">
          <!-- 基本信息卡片 -->
          <el-card class="info-card" shadow="never">
            <template #header>
              <span class="section-title">基本信息</span>
            </template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="考试名称">
                {{ detail.examInfo.examName }}
              </el-descriptions-item>
              <el-descriptions-item label="考试类型">
                {{ detail.examInfo.examType }}
              </el-descriptions-item>
              <el-descriptions-item label="考试日期">
                {{ detail.examInfo.examDate }}
              </el-descriptions-item>
              <el-descriptions-item label="班级">
                {{ detail.classInfo.className }}
              </el-descriptions-item>
              <el-descriptions-item label="科目">
                {{ detail.subjectInfo.subjectName }}
              </el-descriptions-item>
              <el-descriptions-item label="满分">
                {{ detail.subjectInfo.fullScore }}
              </el-descriptions-item>
              <el-descriptions-item label="录入人">
                {{ detail.inputInfo.inputPerson }}
              </el-descriptions-item>
              <el-descriptions-item label="录入时间">
                {{ detail.inputInfo.inputTime }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 异常检测提示 -->
          <el-alert
            v-if="anomalies.length > 0"
            type="warning"
            :closable="false"
            class="anomaly-alert"
          >
            <template #title>
              <div class="anomaly-title">
                <el-icon><Warning /></el-icon>
                <span>发现 {{ anomalies.length }} 项异常数据</span>
              </div>
            </template>
            <ul class="anomaly-list">
              <li v-for="(item, index) in anomalies" :key="index">{{ item }}</li>
            </ul>
          </el-alert>

          <!-- 成绩列表 -->
          <el-card class="score-card" shadow="never">
            <template #header>
              <span class="section-title">成绩列表</span>
            </template>
            <el-table
              :data="detail.scores"
              stripe
              border
              :height="400"
              style="width: 100%"
            >
              <el-table-column prop="studentNo" label="学号" width="120" fixed />
              <el-table-column prop="studentName" label="姓名" width="100" fixed />
              <el-table-column label="分数" width="100" align="center">
                <template #default="{ row }">
                  <span v-if="row.isAbsent" class="absent-text">缺考</span>
                  <span
                    v-else
                    :class="{
                      'score-high': row.score >= 85,
                      'score-low': row.score < 60,
                      'score-anomaly': row.score > detail.subjectInfo.fullScore
                    }"
                  >
                    {{ row.score }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="缺考" width="80" align="center">
                <template #default="{ row }">
                  <el-icon v-if="row.isAbsent" color="#f56c6c"><Close /></el-icon>
                  <el-icon v-else color="#67c23a"><Check /></el-icon>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="200" />
            </el-table>
          </el-card>

          <!-- 分数分布图表 -->
          <el-card class="chart-card" shadow="never">
            <template #header>
              <span class="section-title">分数分布</span>
            </template>
            <div ref="chartRef" class="chart-container"></div>
          </el-card>
        </el-col>

        <!-- 右侧统计信息 -->
        <el-col :span="6">
          <el-card class="stat-card" shadow="never">
            <template #header>
              <span class="section-title">统计信息</span>
            </template>
            <div class="stat-list">
              <div class="stat-item">
                <div class="stat-label">总人数</div>
                <div class="stat-value">{{ detail.statistics.totalCount }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">已录入人数</div>
                <div class="stat-value primary">{{ detail.statistics.enteredCount }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">平均分</div>
                <div class="stat-value">{{ detail.statistics.averageScore.toFixed(2) }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">最高分</div>
                <div class="stat-value success">{{ detail.statistics.highestScore }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">最低分</div>
                <div class="stat-value danger">{{ detail.statistics.lowestScore }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">及格率</div>
                <div class="stat-value">{{ detail.statistics.passRate.toFixed(2) }}%</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">优秀率(≥85)</div>
                <div class="stat-value warning">{{ detail.statistics.excellentRate.toFixed(2) }}%</div>
              </div>
            </div>
          </el-card>

          <!-- 审核历史 -->
          <el-card v-if="historyList.length > 0" class="history-card" shadow="never">
            <template #header>
              <span class="section-title">审核历史</span>
            </template>
            <el-timeline>
              <el-timeline-item
                v-for="item in historyList"
                :key="item.id"
                :timestamp="item.operateTime"
                placement="top"
                :type="item.action === 'approve' ? 'success' : 'danger'"
              >
                <div class="history-item">
                  <div class="history-action">
                    {{ item.action === 'approve' ? '审核通过' : '审核驳回' }}
                  </div>
                  <div class="history-operator">操作人: {{ item.operator }}</div>
                  <div v-if="item.reason" class="history-reason">
                    驳回原因: {{ item.reason }}
                  </div>
                  <div v-if="item.remark" class="history-remark">
                    备注: {{ item.remark }}
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>

      <!-- 底部审核操作区 -->
      <div v-if="detail.status === 'pending'" class="review-actions">
        <el-button
          type="success"
          size="large"
          :icon="Select"
          :loading="approving"
          @click="handleApprove"
        >
          审核通过
        </el-button>
        <el-button
          type="danger"
          size="large"
          :icon="CloseBold"
          :loading="rejecting"
          @click="handleReject"
        >
          审核驳回
        </el-button>
      </div>
    </el-card>

    <!-- 驳回对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="审核驳回" width="500px">
      <el-form :model="rejectForm" label-width="100px">
        <el-form-item label="驳回原因" required>
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="5"
            placeholder="请输入驳回原因，将通知录入人重新录入"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="confirmReject">
          确定驳回
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  Select,
  CloseBold,
  Warning,
  Check,
  Close
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  getReviewDetail,
  getReviewHistory,
  approveScore,
  rejectScore,
  getScoreDistribution,
  type ReviewDetail,
  type ReviewHistory,
  type ScoreDistribution
} from '@/api/score'
import { handleError } from '@/utils/errorHandler'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const approving = ref(false)
const rejecting = ref(false)
const rejectDialogVisible = ref(false)
const chartRef = ref<HTMLElement>()
let chartInstance: ECharts | null = null

const defaultDetail: ReviewDetail = {
  examInfo: {
    examId: 0,
    examName: '',
    examType: '',
    examDate: ''
  },
  classInfo: {
    classId: 0,
    className: ''
  },
  subjectInfo: {
    subjectId: 0,
    subjectName: '',
    fullScore: 100
  },
  inputInfo: {
    inputPerson: '',
    inputTime: ''
  },
  status: 'pending',
  scores: [],
  statistics: {
    totalCount: 0,
    enteredCount: 0,
    averageScore: 0,
    highestScore: 0,
    lowestScore: 0,
    passRate: 0,
    excellentRate: 0
  }
}

const detail = reactive<ReviewDetail>({ ...defaultDetail })
const historyList = ref<ReviewHistory[]>([])
const distributionData = ref<ScoreDistribution[]>([])

const rejectForm = reactive({
  reason: ''
})

const examId = computed(() => Number(route.query.examId))
const classId = computed(() => Number(route.query.classId))
const subjectId = computed(() => Number(route.query.subjectId))

const anomalies = computed(() => {
  const result: string[] = []

  detail.scores.forEach(score => {
    // 超出满分
    if (score.score !== undefined && score.score > detail.subjectInfo.fullScore) {
      result.push(`${score.studentName}(${score.studentNo}): 分数${score.score}超出满分${detail.subjectInfo.fullScore}`)
    }

    // 0分但未标记缺考
    if (score.score === 0 && !score.isAbsent) {
      result.push(`${score.studentName}(${score.studentNo}): 分数为0但未标记缺考`)
    }

    // 满分可疑（如果满分人数过多且没有备注）
    if (score.score === detail.subjectInfo.fullScore && !score.remark) {
      const fullScoreCount = detail.scores.filter(
        s => s.score === detail.subjectInfo.fullScore
      ).length
      if (fullScoreCount > detail.statistics.totalCount * 0.3) {
        result.push(`${score.studentName}(${score.studentNo}): 满分记录无备注说明`)
      }
    }
  })

  // 未录入学生
  const notEnteredCount = detail.statistics.totalCount - detail.statistics.enteredCount
  if (notEnteredCount > 0) {
    result.push(`有${notEnteredCount}名学生成绩未录入`)
  }

  return result
})

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'warning' | 'success' | 'danger'> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回'
  }
  return textMap[status] || status
}

const loadDetail = async () => {
  if (!examId.value || !classId.value || !subjectId.value) {
    ElMessage.error('参数错误')
    router.back()
    return
  }

  loading.value = true
  try {
    const [detailData, history, distribution] = await Promise.all([
      getReviewDetail(examId.value, classId.value, subjectId.value) as Promise<ReviewDetail>,
      getReviewHistory(examId.value, classId.value, subjectId.value) as Promise<ReviewHistory[]>,
      getScoreDistribution(examId.value, classId.value, subjectId.value) as Promise<ScoreDistribution[]>
    ])

    Object.assign(detail, detailData)
    historyList.value = history
    distributionData.value = distribution

    await nextTick()
    initChart()
  } catch (error) {
    handleError(error, '加载审核详情失败')
  } finally {
    loading.value = false
  }
}

const initChart = () => {
  if (!chartRef.value) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(chartRef.value)

  const ranges = ['0-59', '60-69', '70-79', '80-89', '90-100']
  const counts = ranges.map(range => {
    const item = distributionData.value.find(d => d.range === range)
    return item ? item.count : 0
  })

  const option = {
    title: {
      text: '分数分布',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: ranges,
      axisLabel: {
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      name: '人数',
      minInterval: 1
    },
    series: [
      {
        data: counts.map((value, index) => {
          const colors = ['#f56c6c', '#e6a23c', '#67c23a', '#409eff', '#409eff']
          return {
            value,
            itemStyle: {
              color: colors[index]
            }
          }
        }),
        type: 'bar',
        barWidth: '60%',
        label: {
          show: true,
          position: 'top'
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

const handleResize = () => {
  chartInstance?.resize()
}

const handleBack = () => {
  router.back()
}

const handleApprove = async () => {
  try {
    await ElMessageBox.confirm(
      '确认通过该班级该科目的成绩？发布考试时统一计算排名。',
      '审核通过确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    approving.value = true
    await approveScore({
      examId: examId.value,
      classId: classId.value,
      subjectId: subjectId.value
    })

    ElMessage.success('审核通过成功')
    router.back()
  } catch (error) {
    if (error !== 'cancel') {
      handleError(error, '审核通过失败')
    }
  } finally {
    approving.value = false
  }
}

const handleReject = () => {
  rejectForm.reason = ''
  rejectDialogVisible.value = true
}

const confirmReject = async () => {
  if (!rejectForm.reason.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }

  try {
    rejecting.value = true
    await rejectScore({
      examId: examId.value,
      classId: classId.value,
      subjectId: subjectId.value,
      reason: rejectForm.reason
    })

    ElMessage.success('审核驳回成功，已通知录入人')
    rejectDialogVisible.value = false
    router.back()
  } catch (error) {
    handleError(error, '审核驳回失败')
  } finally {
    rejecting.value = false
  }
}

onMounted(() => {
  loadDetail()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped lang="scss">
.review-detail {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .title {
        font-size: 18px;
        font-weight: bold;
      }
    }
  }

  .section-title {
    font-weight: bold;
    font-size: 16px;
  }

  .info-card,
  .score-card,
  .chart-card,
  .stat-card,
  .history-card {
    margin-bottom: 20px;
  }

  .anomaly-alert {
    margin-bottom: 20px;

    .anomaly-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-weight: bold;
    }

    .anomaly-list {
      margin-top: 10px;
      padding-left: 20px;

      li {
        margin-bottom: 5px;
        color: #e6a23c;
      }
    }
  }

  .absent-text {
    color: #909399;
  }

  .score-high {
    color: #67c23a;
    font-weight: bold;
  }

  .score-low {
    color: #f56c6c;
    font-weight: bold;
  }

  .score-anomaly {
    color: #f56c6c;
    font-weight: bold;
    background-color: #fef0f0;
    padding: 2px 6px;
    border-radius: 3px;
  }

  .chart-container {
    height: 350px;
    width: 100%;
  }

  .stat-list {
    .stat-item {
      padding: 15px 0;
      border-bottom: 1px solid #ebeef5;
      display: flex;
      justify-content: space-between;
      align-items: center;

      &:last-child {
        border-bottom: none;
      }

      .stat-label {
        color: #606266;
        font-size: 14px;
      }

      .stat-value {
        font-size: 20px;
        font-weight: bold;
        color: #303133;

        &.primary {
          color: #409eff;
        }

        &.success {
          color: #67c23a;
        }

        &.danger {
          color: #f56c6c;
        }

        &.warning {
          color: #e6a23c;
        }
      }
    }
  }

  .history-item {
    .history-action {
      font-weight: bold;
      margin-bottom: 5px;
    }

    .history-operator {
      color: #606266;
      font-size: 13px;
      margin-bottom: 3px;
    }

    .history-reason,
    .history-remark {
      color: #909399;
      font-size: 13px;
      margin-top: 5px;
    }
  }

  .review-actions {
    margin-top: 30px;
    padding-top: 30px;
    border-top: 2px solid #ebeef5;
    display: flex;
    justify-content: center;
    gap: 20px;
  }
}
</style>
