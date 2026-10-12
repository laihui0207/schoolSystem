<template>
  <div class="subject-compare">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">科目成绩对比</span>
        </div>
      </template>

      <!-- 选择区 -->
      <div class="filter-section">
        <el-select
          v-model="selectedSchoolId"
          placeholder="请选择学校"
          style="width: 200px; margin-right: 10px"
          @change="handleSchoolChange"
          :loading="schoolsLoading"
        >
          <el-option
            v-for="school in schoolList"
            :key="school.id"
            :label="school.schoolName"
            :value="school.id!"
          />
        </el-select>
        <el-select
          v-model="selectedExamId"
          placeholder="请选择考试"
          style="width: 250px; margin-right: 10px"
          @change="handleExamChange"
          :loading="examsLoading"
          :disabled="!selectedSchoolId"
        >
          <el-option
            v-for="exam in examList"
            :key="exam.id"
            :label="exam.examName"
            :value="exam.id!"
          />
        </el-select>
        <el-select
          v-model="selectedSubjectId"
          placeholder="请选择科目"
          style="width: 250px"
          @change="handleSubjectChange"
          :loading="subjectsLoading"
          :disabled="!selectedExamId"
        >
          <el-option
            v-for="subject in subjectList"
            :key="subject.id"
            :label="subject.subjectName"
            :value="subject.id!"
          />
        </el-select>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" v-loading="loading" style="height: 400px"></div>

      <!-- 空数据状态 -->
      <el-empty
        v-else-if="!selectedSubjectId"
        description="请选择考试和科目查看数据"
      />

      <!-- 数据展示 -->
      <div v-else class="content-section">
        <!-- 统计信息卡片 -->
        <el-row :gutter="20" class="stat-cards">
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-label">参考总人数</div>
              <div class="stat-value primary">{{ compare.totalStudents }}</div>
            </div>
          </el-col>
          <el-col :span="3">
            <div class="stat-card">
              <div class="stat-label">平均分</div>
              <div class="stat-value">{{ compare.averageScore.toFixed(2) }}</div>
            </div>
          </el-col>
          <el-col :span="3">
            <div class="stat-card">
              <div class="stat-label">最高分</div>
              <div class="stat-value success">{{ compare.maxScore.toFixed(2) }}</div>
            </div>
          </el-col>
          <el-col :span="3">
            <div class="stat-card">
              <div class="stat-label">最低分</div>
              <div class="stat-value danger">{{ compare.minScore.toFixed(2) }}</div>
            </div>
          </el-col>
          <el-col :span="3">
            <div class="stat-card">
              <div class="stat-label">及格率</div>
              <div class="stat-value warning">{{ compare.passRate.toFixed(2) }}%</div>
            </div>
          </el-col>
          <el-col :span="3">
            <div class="stat-card">
              <div class="stat-label">优秀率</div>
              <div class="stat-value info">{{ compare.excellentRate.toFixed(2) }}%</div>
            </div>
          </el-col>
          <el-col :span="5">
            <div class="stat-card">
              <div class="stat-label">标准差</div>
              <div class="stat-value">{{ compare.standardDeviation.toFixed(2) }}</div>
            </div>
          </el-col>
        </el-row>

        <!-- 图表区域 -->
        <el-row :gutter="20" class="chart-section">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">年级分数分布箱线图</span>
              </template>
              <div ref="boxChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">班级对比</span>
              </template>
              <div ref="classChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="20" class="chart-section">
          <el-col :span="24">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">分数段分布</span>
              </template>
              <div ref="distributionChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import { getSubjectStat, type SubjectStat } from '@/api/stat'
import { getExamList, type Exam } from '@/api/exam'
import { getSubjectList, type Subject } from '@/api/subject'
import { getSchoolList, type School } from '@/api/school'
import { handleError } from '@/utils/errorHandler'

const loading = ref(false)
const schoolsLoading = ref(false)
const examsLoading = ref(false)
const subjectsLoading = ref(false)
const selectedSchoolId = ref<number>()
const selectedExamId = ref<number>()
const selectedSubjectId = ref<number>()
const schoolList = ref<School[]>([])
const examList = ref<Exam[]>([])
const subjectList = ref<Subject[]>([])
const boxChartRef = ref<HTMLElement>()
const classChartRef = ref<HTMLElement>()
const distributionChartRef = ref<HTMLElement>()
let boxChartInstance: ECharts | null = null
let classChartInstance: ECharts | null = null
let distributionChartInstance: ECharts | null = null

// 字段名与后端 SubjectStatResponse 一致：classAverages / gradeBoxes / scoreRanges
const compare = reactive<SubjectStat>({
  subjectId: 0,
  subjectName: '',
  examId: 0,
  totalStudents: 0,
  actualStudents: 0,
  averageScore: 0,
  maxScore: 0,
  minScore: 0,
  passRate: 0,
  excellentRate: 0,
  standardDeviation: 0,
  classAverages: [],
  gradeBoxes: [],
  scoreRanges: []
})

const colors = {
  primary: '#409EFF',
  success: '#67C23A',
  warning: '#E6A23C',
  danger: '#F56C6C',
  info: '#909399'
}

const loadSchoolList = async () => {
  schoolsLoading.value = true
  try {
    const data = await getSchoolList({ pageNum: 1, pageSize: 100 })
    schoolList.value = data.records || []
  } catch (error) {
    handleError(error, '加载学校列表失败')
  } finally {
    schoolsLoading.value = false
  }
}

const loadExamList = async () => {
  if (!selectedSchoolId.value) return
  examsLoading.value = true
  try {
    const data = await getExamList({ pageNum: 1, pageSize: 100, schoolId: selectedSchoolId.value })
    examList.value = data.records || []
  } catch (error) {
    handleError(error, '加载考试列表失败')
  } finally {
    examsLoading.value = false
  }
}

const loadSubjectList = async () => {
  subjectsLoading.value = true
  try {
    const data = await getSubjectList({ pageNum: 1, pageSize: 100 })
    subjectList.value = data.records || []
  } catch (error) {
    handleError(error, '加载科目列表失败')
  } finally {
    subjectsLoading.value = false
  }
}

const loadCompare = async () => {
  if (!selectedExamId.value || !selectedSubjectId.value) return

  loading.value = true
  try {
    const data = await getSubjectStat({
      examId: selectedExamId.value,
      subjectId: selectedSubjectId.value
    })
    Object.assign(compare, data)

    // 图表容器在 v-if="loading" 分支下未挂载，必须先关闭 loading 再等 DOM
    loading.value = false
    await nextTick()
    initBoxChart()
    initClassChart()
    initDistributionChart()
  } catch (error) {
    handleError(error, '加载科目对比失败')
  } finally {
    loading.value = false
  }
}

const initBoxChart = () => {
  if (!boxChartRef.value || compare.gradeBoxes.length === 0) return

  if (boxChartInstance) {
    boxChartInstance.dispose()
  }

  boxChartInstance = echarts.init(boxChartRef.value)

  const option = {
    tooltip: {
      trigger: 'item',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '10%',
      right: '10%',
      bottom: '15%'
    },
    xAxis: {
      type: 'category',
      data: compare.gradeBoxes.map(item => item.gradeName),
      boundaryGap: true,
      nameGap: 30,
      splitArea: {
        show: false
      },
      axisLabel: {
        formatter: '{value}'
      },
      splitLine: {
        show: false
      }
    },
    yAxis: {
      type: 'value',
      name: '分数',
      splitArea: {
        show: true
      }
    },
    series: [
      {
        name: 'boxplot',
        type: 'boxplot',
        data: compare.gradeBoxes.map(item => [
          item.min,
          item.q1,
          item.median,
          item.q3,
          item.max
        ]),
        itemStyle: {
          color: colors.primary,
          borderColor: colors.primary
        },
        tooltip: {
          formatter: (param: { name: string, data: number[] }) => {
            return `${param.name}<br/>
              最高分: ${param.data[4]}<br/>
              上四分位数: ${param.data[3]}<br/>
              中位数: ${param.data[2]}<br/>
              下四分位数: ${param.data[1]}<br/>
              最低分: ${param.data[0]}`
          }
        }
      }
    ]
  }

  boxChartInstance.setOption(option)
}

const initClassChart = () => {
  if (!classChartRef.value || compare.classAverages.length === 0) return

  if (classChartInstance) {
    classChartInstance.dispose()
  }

  classChartInstance = echarts.init(classChartRef.value)

  const sortedData = [...compare.classAverages].sort((a, b) => b.averageScore - a.averageScore)
  const maxScore = Math.max(...sortedData.map(item => item.averageScore))
  const minScore = Math.min(...sortedData.map(item => item.averageScore))

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
      data: sortedData.map(item => item.className),
      axisLabel: {
        rotate: 30,
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      name: '平均分'
    },
    series: [
      {
        data: sortedData.map(item => {
          let color = colors.primary
          if (item.averageScore === maxScore) {
            color = colors.success
          } else if (item.averageScore === minScore) {
            color = colors.danger
          }
          return {
            value: item.averageScore,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: color },
                { offset: 1, color: color + '80' }
              ])
            }
          }
        }),
        type: 'bar',
        barWidth: '60%',
        label: {
          show: true,
          position: 'top',
          formatter: (params: { value: number }) => params.value.toFixed(2)
        }
      }
    ]
  }

  classChartInstance.setOption(option)
}

const initDistributionChart = () => {
  if (!distributionChartRef.value || compare.scoreRanges.length === 0) return

  if (distributionChartInstance) {
    distributionChartInstance.dispose()
  }

  distributionChartInstance = echarts.init(distributionChartRef.value)

  const rangeColors: Record<string, string> = {
    '0-59': colors.danger,
    '60-69': colors.warning,
    '70-79': colors.success,
    '80-89': colors.primary,
    '90-100': colors.primary
  }

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
      data: compare.scoreRanges.map(item => item.range),
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
        data: compare.scoreRanges.map(item => ({
          value: item.count,
          itemStyle: {
            color: rangeColors[item.range] || colors.info
          }
        })),
        type: 'bar',
        barWidth: '60%',
        label: {
          show: true,
          position: 'top'
        }
      }
    ]
  }

  distributionChartInstance.setOption(option)
}

const handleResize = () => {
  boxChartInstance?.resize()
  classChartInstance?.resize()
  distributionChartInstance?.resize()
}

const handleSchoolChange = () => {
  selectedExamId.value = undefined
  selectedSubjectId.value = undefined
  examList.value = []
  subjectList.value = []
  loadExamList()
}

const handleExamChange = () => {
  selectedSubjectId.value = undefined
  subjectList.value = []
  loadSubjectList()
}

const handleSubjectChange = () => {
  loadCompare()
}

onMounted(() => {
  loadSchoolList()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (boxChartInstance) {
    boxChartInstance.dispose()
    boxChartInstance = null
  }
  if (classChartInstance) {
    classChartInstance.dispose()
    classChartInstance = null
  }
  if (distributionChartInstance) {
    distributionChartInstance.dispose()
    distributionChartInstance = null
  }
})
</script>

<style scoped lang="scss">
.subject-compare {
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
        padding: 15px;
        border-radius: 8px;
        background: #f5f7fa;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);

        .stat-label {
          font-size: 13px;
          color: #606266;
          margin-bottom: 8px;
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
  }
}
</style>

