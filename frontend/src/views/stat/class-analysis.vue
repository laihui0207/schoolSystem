<template>
  <div class="class-analysis">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">班级成绩分析</span>
        </div>
      </template>

      <!-- 选择区 -->
      <div class="filter-section">
        <el-select
          v-if="isSuperAdmin"
          v-model="selectedSchoolId"
          placeholder="请选择学校"
          style="width: 200px; margin-right: 10px"
          clearable
          @change="handleSchoolChange"
        >
          <el-option v-for="s in schoolList" :key="s.id" :label="s.schoolName" :value="s.id" />
        </el-select>
        <el-select
          v-model="selectedExamId"
          placeholder="请选择考试"
          style="width: 250px; margin-right: 10px"
          @change="handleExamChange"
          :loading="examsLoading"
          :disabled="isSuperAdmin && !selectedSchoolId"
        >
          <el-option
            v-for="exam in examList"
            :key="exam.id"
            :label="exam.name || exam.examName"
            :value="exam.id!"
          />
        </el-select>
        <el-select
          v-model="selectedClassId"
          placeholder="请选择班级"
          style="width: 250px"
          @change="handleClassChange"
          :loading="classesLoading"
          :disabled="!selectedExamId"
        >
          <el-option
            v-for="cls in classList"
            :key="cls.id"
            :label="cls.className"
            :value="cls.id!"
          />
        </el-select>
        <!-- ponytail: 两个导出都要求考试已发布（后端校验），未选齐考试+班级时禁用 -->
        <el-button
          type="success"
          style="margin-left: 10px"
          :disabled="!selectedExamId || !selectedClassId"
          :loading="exportingReport"
          @click="handleExportReport"
        >导出统计报表</el-button>
        <el-button
          type="primary"
          :disabled="!selectedExamId || !selectedClassId"
          :loading="exportingPdf"
          @click="handleClassPdf"
        >全班成绩单 PDF</el-button>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" v-loading="loading" style="height: 400px"></div>

      <!-- 空数据状态 -->
      <el-empty
        v-else-if="!selectedClassId"
        description="请选择考试和班级查看数据"
      />

      <!-- 数据展示 -->
      <div v-else class="content-section">
        <!-- 统计信息卡片 -->
        <el-row :gutter="20" class="stat-cards">
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-label">班级总人数</div>
              <div class="stat-value primary">{{ analysis.totalStudents }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-label">平均总分</div>
              <div class="stat-value">{{ analysis.averageScore.toFixed(2) }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-label">最高分</div>
              <div class="stat-value success">{{ analysis.maxScore.toFixed(2) }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-label">最低分</div>
              <div class="stat-value danger">{{ analysis.minScore.toFixed(2) }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-label">及格率</div>
              <div class="stat-value warning">{{ analysis.passRate.toFixed(2) }}%</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-label">优秀率</div>
              <div class="stat-value info">{{ analysis.excellentRate.toFixed(2) }}%</div>
            </div>
          </el-col>
        </el-row>

        <!-- 图表区域 -->
        <el-row :gutter="20" class="chart-section">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">分数段分布</span>
              </template>
              <div ref="pieChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">各科目成绩对比</span>
              </template>
              <div ref="subjectChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 学生排名表格 -->
        <el-card shadow="never" class="table-section">
          <template #header>
            <span class="section-title">学生排名 Top 10</span>
          </template>
          <el-table :data="analysis.topStudents" stripe border>
            <el-table-column prop="rank" label="排名" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.rank === 1" type="danger" effect="dark">🥇 {{ row.rank }}</el-tag>
                <el-tag v-else-if="row.rank === 2" type="warning" effect="dark">🥈 {{ row.rank }}</el-tag>
                <el-tag v-else-if="row.rank === 3" type="success" effect="dark">🥉 {{ row.rank }}</el-tag>
                <span v-else>{{ row.rank }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="studentName" label="姓名" width="120" />
            <el-table-column prop="totalScore" label="总分" width="100" align="center">
              <template #default="{ row }">
                <span class="score-high">{{ row.totalScore.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="各科分数">
              <template #default="{ row }">
                <el-tag
                  v-for="(score, subject) in row.subjectScores"
                  :key="subject"
                  style="margin-right: 5px"
                  :type="score >= 85 ? 'success' : score >= 60 ? 'info' : 'danger'"
                >
                  {{ subject }}: {{ score }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import { getClassStat, type ClassStat } from '@/api/stat'
import { getExamList, type Exam } from '@/api/exam'
import { getClassList, type Class } from '@/api/class'
import { getSchoolList } from '@/api/school'
import { exportStatReport, downloadClassReportsPdf } from '@/api/export'
import { handleError } from '@/utils/errorHandler'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const isSuperAdmin = userStore.userInfo?.userType === 1

const loading = ref(false)
const examsLoading = ref(false)
const classesLoading = ref(false)
const selectedSchoolId = ref<number | undefined>(isSuperAdmin ? undefined : userStore.userInfo?.schoolId)
const selectedExamId = ref<number>()
const selectedClassId = ref<number>()
const schoolList = ref<any[]>([])
const examList = ref<Exam[]>([])
const classList = ref<Class[]>([])
const pieChartRef = ref<HTMLElement>()
const subjectChartRef = ref<HTMLElement>()
let pieChartInstance: ECharts | null = null
let subjectChartInstance: ECharts | null = null

const analysis = reactive<ClassStat>({
  classId: 0,
  examId: 0,
  totalStudents: 0,
  actualStudents: 0,
  absentStudents: 0,
  totalScore: 0,
  averageScore: 0,
  maxScore: 0,
  minScore: 0,
  passCount: 0,
  passRate: 0,
  excellentCount: 0,
  excellentRate: 0,
  subjectStats: [],
  scoreRanges: [],
  topStudents: []
})

const colors = {
  primary: '#409EFF',
  success: '#67C23A',
  warning: '#E6A23C',
  danger: '#F56C6C',
  info: '#909399'
}

const loadExamList = async () => {
  examsLoading.value = true
  try {
    const data = await getExamList({ pageNum: 1, pageSize: 100 })
    examList.value = data.records || []
  } catch (error) {
    handleError(error, '加载考试列表失败')
  } finally {
    examsLoading.value = false
  }
}

const loadClassList = async (schoolId: number) => {
  classesLoading.value = true
  try {
    const data = await getClassList({ schoolId, pageNum: 1, pageSize: 1000 })
    classList.value = data.records || []
  } catch (error) {
    handleError(error, '加载班级列表失败')
  } finally {
    classesLoading.value = false
  }
}

const loadAnalysis = async () => {
  if (!selectedExamId.value || !selectedClassId.value) return

  loading.value = true
  try {
    const data = await getClassStat({ examId: selectedExamId.value, classId: selectedClassId.value })
    Object.assign(analysis, data)

    // 图表容器在 v-if="loading" 分支下未挂载，必须先关闭 loading 再等 DOM
    loading.value = false
    await nextTick()
    initPieChart()
    initSubjectChart()
  } catch (error) {
    handleError(error, '加载班级分析失败')
  } finally {
    loading.value = false
  }
}

const initPieChart = () => {
  if (!pieChartRef.value || analysis.scoreRanges.length === 0) return

  if (pieChartInstance) {
    pieChartInstance.dispose()
  }

  pieChartInstance = echarts.init(pieChartRef.value)

  const segmentColors: Record<string, string> = {
    '优秀': '#409EFF',
    '良好': '#67C23A',
    '及格': '#E6A23C',
    '不及格': '#F56C6C'
  }

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: '分数段分布',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}: {c}人 ({d}%)'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: analysis.scoreRanges.map(item => ({
          value: item.count,
          name: item.range,
          itemStyle: {
            color: segmentColors[item.range] || colors.info
          }
        }))
      }
    ]
  }

  pieChartInstance.setOption(option)
}

const initSubjectChart = () => {
  if (!subjectChartRef.value || analysis.subjectStats.length === 0) return

  if (subjectChartInstance) {
    subjectChartInstance.dispose()
  }

  subjectChartInstance = echarts.init(subjectChartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        crossStyle: {
          color: '#999'
        }
      }
    },
    legend: {
      data: ['平均分', '及格率']
    },
    xAxis: {
      type: 'category',
      data: analysis.subjectStats.map(item => item.subjectName),
      axisPointer: {
        type: 'shadow'
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '平均分',
        min: 0,
        max: 100,
        axisLabel: {
          formatter: '{value}'
        }
      },
      {
        type: 'value',
        name: '及格率',
        min: 0,
        max: 100,
        axisLabel: {
          formatter: '{value}%'
        }
      }
    ],
    series: [
      {
        name: '平均分',
        type: 'bar',
        data: analysis.subjectStats.map(item => item.averageScore),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colors.primary },
            { offset: 1, color: colors.success }
          ])
        },
        label: {
          show: true,
          position: 'top',
          formatter: (params: { value: number }) => params.value.toFixed(2)
        }
      },
      {
        name: '及格率',
        type: 'line',
        yAxisIndex: 1,
        data: analysis.subjectStats.map(item => item.passRate ?? 0),
        itemStyle: {
          color: colors.warning
        },
        label: {
          show: true,
          formatter: (params: { value: number }) => params.value.toFixed(2) + '%'
        }
      }
    ]
  }

  subjectChartInstance.setOption(option)
}

const handleResize = () => {
  pieChartInstance?.resize()
  subjectChartInstance?.resize()
}

const handleSchoolChange = (schoolId: number) => {
  selectedExamId.value = undefined
  selectedClassId.value = undefined
  classList.value = []
  if (schoolId) loadClassList(schoolId)
}

const handleExamChange = () => {
  selectedClassId.value = undefined
}

const handleClassChange = () => {
  loadAnalysis()
}

const exportingReport = ref(false)
const exportingPdf = ref(false)

const selectedClassName = () =>
  classList.value.find(cls => cls.id === selectedClassId.value)?.className ?? selectedClassId.value

const handleExportReport = async () => {
  if (!selectedExamId.value || !selectedClassId.value) return
  exportingReport.value = true
  try {
    await exportStatReport({ examId: selectedExamId.value, classId: selectedClassId.value })
  } catch {
    // 后端业务错误（未发布 / 越权）已在 api/export.ts 的 ensureBinary 中提示，HTTP 错误由拦截器提示
  } finally {
    exportingReport.value = false
  }
}

const handleClassPdf = async () => {
  if (!selectedExamId.value || !selectedClassId.value) return
  exportingPdf.value = true
  try {
    await downloadClassReportsPdf(selectedExamId.value, selectedClassId.value, selectedClassName())
  } catch {
    // 同上
  } finally {
    exportingPdf.value = false
  }
}

onMounted(async () => {
  if (isSuperAdmin) {
    const res = await getSchoolList({ pageNum: 1, pageSize: 1000 }) as any
    schoolList.value = res?.records ?? []
  } else if (selectedSchoolId.value) {
    await Promise.all([
      loadExamList(),
      loadClassList(selectedSchoolId.value)
    ])
  }
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (pieChartInstance) {
    pieChartInstance.dispose()
    pieChartInstance = null
  }
  if (subjectChartInstance) {
    subjectChartInstance.dispose()
    subjectChartInstance = null
  }
})
</script>

<style scoped lang="scss">
.class-analysis {
  .card-header {
    .title {
      font-size: 18px;
      font-weight: bold;
    }
  }

  .filter-section {
    margin-bottom: 20px;
  }

  .content-section {
    .stat-cards {
      margin-bottom: 20px;

      .stat-card {
        text-align: center;
        padding: 20px;
        border-radius: 8px;
        background: #f5f7fa;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);

        .stat-label {
          font-size: 14px;
          color: #606266;
          margin-bottom: 10px;
        }

        .stat-value {
          font-size: 24px;
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

          &.info {
            color: #909399;
          }
        }
      }
    }

    .chart-section {
      margin-bottom: 20px;

      .section-title {
        font-weight: bold;
        font-size: 16px;
      }

      .chart-container {
        height: 350px;
        width: 100%;
      }
    }

    .table-section {
      .section-title {
        font-weight: bold;
        font-size: 16px;
      }

      .score-high {
        color: #67c23a;
        font-weight: bold;
      }
    }
  }
}
</style>

