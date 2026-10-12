<template>
  <div class="exam-overview">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">考试成绩总览</span>
        </div>
      </template>

      <!-- 选择区 -->
      <div class="filter-section">
        <el-select
          v-model="selectedExamId"
          placeholder="请选择考试"
          style="width: 300px"
          @change="handleExamChange"
          :loading="examsLoading"
        >
          <el-option
            v-for="exam in examList"
            :key="exam.id"
            :label="exam.name || exam.examName"
            :value="exam.id!"
          />
        </el-select>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" v-loading="loading" style="height: 400px"></div>

      <!-- 空数据状态 -->
      <el-empty
        v-else-if="!selectedExamId"
        description="请选择考试查看数据"
      />

      <!-- 数据展示 -->
      <div v-else class="content-section">
        <!-- 关键指标卡片 -->
        <el-row :gutter="20" class="stat-cards">
          <el-col :span="6">
            <div class="stat-card primary">
              <div class="stat-icon">👥</div>
              <div class="stat-info">
                <div class="stat-label">参考总人数</div>
                <div class="stat-value">{{ overview.totalStudents }}</div>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card success">
              <div class="stat-icon">📊</div>
              <div class="stat-info">
                <div class="stat-label">平均总分</div>
                <div class="stat-value">{{ overview.averageScore.toFixed(2) }}</div>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card warning">
              <div class="stat-icon">✓</div>
              <div class="stat-info">
                <div class="stat-label">总及格率</div>
                <div class="stat-value">{{ overview.passRate.toFixed(2) }}%</div>
              </div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card danger">
              <div class="stat-icon">★</div>
              <div class="stat-info">
                <div class="stat-label">总优秀率</div>
                <div class="stat-value">{{ overview.excellentRate.toFixed(2) }}%</div>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 图表区域 -->
        <el-row :gutter="20" class="chart-section">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">年级对比</span>
              </template>
              <div ref="gradeChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">科目平均分</span>
              </template>
              <div ref="radarChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 班级排名表格 -->
        <el-card shadow="never" class="table-section">
          <template #header>
            <span class="section-title">班级排名</span>
          </template>
          <el-table :data="overview.classComparisons" stripe border>
            <el-table-column prop="rank" label="排名" width="80" align="center" />
            <el-table-column prop="className" label="班级名称" width="150" />
            <el-table-column prop="averageScore" label="平均分" width="120" align="center">
              <template #default="{ row }">
                {{ row.averageScore.toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="maxScore" label="最高分" width="120" align="center">
              <template #default="{ row }">
                <span class="score-high">{{ row.maxScore.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="minScore" label="最低分" width="120" align="center">
              <template #default="{ row }">
                <span class="score-low">{{ row.minScore.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="passRate" label="及格率" width="120" align="center">
              <template #default="{ row }">
                {{ row.passRate.toFixed(2) }}%
              </template>
            </el-table-column>
            <el-table-column prop="excellentRate" label="优秀率" width="120" align="center">
              <template #default="{ row }">
                {{ row.excellentRate.toFixed(2) }}%
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
import { getGradeStat, type GradeStat } from '@/api/stat'
import { getExamList, type Exam } from '@/api/exam'
import { handleError } from '@/utils/errorHandler'

const loading = ref(false)
const examsLoading = ref(false)
const selectedExamId = ref<number>()
const examList = ref<Exam[]>([])
const gradeChartRef = ref<HTMLElement>()
const radarChartRef = ref<HTMLElement>()
let gradeChartInstance: ECharts | null = null
let radarChartInstance: ECharts | null = null

// 不传 gradeId 的年级统计即整场考试总览，复用后端 /stats/grade
const overview = reactive<GradeStat>({
  examId: 0,
  totalStudents: 0,
  averageScore: 0,
  maxScore: 0,
  minScore: 0,
  passRate: 0,
  excellentRate: 0,
  gradeStats: [],
  subjectStats: [],
  classComparisons: []
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

const loadOverview = async () => {
  if (!selectedExamId.value) return

  loading.value = true
  try {
    const data = await getGradeStat({ examId: selectedExamId.value })
    Object.assign(overview, data)

    // 图表容器在 v-if="loading" 分支下未挂载，必须先关闭 loading 再等 DOM
    loading.value = false
    await nextTick()
    initGradeChart()
    initRadarChart()
  } catch (error) {
    handleError(error, '加载考试总览失败')
  } finally {
    loading.value = false
  }
}

const initGradeChart = () => {
  if (!gradeChartRef.value || overview.gradeStats.length === 0) return

  if (gradeChartInstance) {
    gradeChartInstance.dispose()
  }

  gradeChartInstance = echarts.init(gradeChartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: overview.gradeStats.map(item => item.gradeName),
      axisLabel: {
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      name: '平均分'
    },
    series: [
      {
        data: overview.gradeStats.map(item => ({
          value: item.averageScore,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: colors.primary },
              { offset: 1, color: colors.success }
            ])
          }
        })),
        type: 'bar',
        barWidth: '50%',
        label: {
          show: true,
          position: 'top',
          formatter: (params: { value: number }) => params.value.toFixed(2)
        }
      }
    ]
  }

  gradeChartInstance.setOption(option)
}

const initRadarChart = () => {
  if (!radarChartRef.value || overview.subjectStats.length === 0) return

  if (radarChartInstance) {
    radarChartInstance.dispose()
  }

  radarChartInstance = echarts.init(radarChartRef.value)

  const maxScore = Math.max(...overview.subjectStats.map(item => item.averageScore))
  const radarMax = Math.ceil(maxScore / 10) * 10 + 10

  const option = {
    tooltip: {
      trigger: 'item'
    },
    radar: {
      indicator: overview.subjectStats.map(item => ({
        name: item.subjectName,
        max: radarMax
      }))
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: overview.subjectStats.map(item => item.averageScore),
            name: '平均分',
            areaStyle: {
              color: new echarts.graphic.RadialGradient(0.5, 0.5, 1, [
                { offset: 0, color: 'rgba(64, 158, 255, 0.5)' },
                { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
              ])
            },
            itemStyle: {
              color: colors.primary
            },
            lineStyle: {
              color: colors.primary
            }
          }
        ]
      }
    ]
  }

  radarChartInstance.setOption(option)
}

const handleResize = () => {
  gradeChartInstance?.resize()
  radarChartInstance?.resize()
}

const handleExamChange = () => {
  loadOverview()
}

onMounted(() => {
  loadExamList()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (gradeChartInstance) {
    gradeChartInstance.dispose()
    gradeChartInstance = null
  }
  if (radarChartInstance) {
    radarChartInstance.dispose()
    radarChartInstance = null
  }
})
</script>

<style scoped lang="scss">
.exam-overview {
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
        display: flex;
        align-items: center;
        padding: 20px;
        border-radius: 8px;
        background: linear-gradient(135deg, rgba(255, 255, 255, 0.1) 0%, rgba(255, 255, 255, 0.05) 100%);
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);

        .stat-icon {
          font-size: 40px;
          margin-right: 15px;
        }

        .stat-info {
          flex: 1;

          .stat-label {
            font-size: 14px;
            color: #606266;
            margin-bottom: 8px;
          }

          .stat-value {
            font-size: 24px;
            font-weight: bold;
            color: #303133;
          }
        }

        &.primary {
          border-left: 4px solid #409eff;
        }

        &.success {
          border-left: 4px solid #67c23a;
        }

        &.warning {
          border-left: 4px solid #e6a23c;
        }

        &.danger {
          border-left: 4px solid #f56c6c;
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

      .score-low {
        color: #f56c6c;
        font-weight: bold;
      }
    }
  }
}
</style>

