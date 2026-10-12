<template>
  <div class="student-trend">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">学生成绩趋势</span>
        </div>
      </template>

      <!-- 选择区 -->
      <div class="filter-section">
        <!-- ponytail: 家长端——孩子下拉来自 /parents/me/children，不开放学生搜索；后端 getStudentTrend 按 canViewStudent 复核 -->
        <el-select
          v-if="isParent"
          v-model="selectedStudentId"
          placeholder="请选择孩子"
          style="width: 300px; margin-right: 10px"
          @change="handleStudentChange"
        >
          <el-option
            v-for="child in children"
            :key="child.studentId"
            :label="`${child.studentNo ?? ''} - ${child.name ?? ''}`"
            :value="child.studentId"
          />
        </el-select>
        <el-select
          v-if="!isStudent && !isParent"
          v-model="selectedStudentId"
          placeholder="请输入学号或姓名搜索学生"
          style="width: 300px; margin-right: 10px"
          filterable
          remote
          :remote-method="handleStudentSearch"
          :loading="studentsLoading"
          @change="handleStudentChange"
        >
          <el-option
            v-for="student in studentList"
            :key="student.id"
            :label="`${student.studentNo} - ${student.name}`"
            :value="student.id!"
          />
        </el-select>
        <el-select
          v-model="selectedTermId"
          placeholder="请选择学期（可选）"
          style="width: 250px"
          clearable
          @change="handleTermChange"
          :loading="termsLoading"
          :disabled="!selectedStudentId"
        >
          <el-option
            v-for="term in termList"
            :key="term.id"
            :label="termLabel(term)"
            :value="term.id!"
          />
        </el-select>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" v-loading="loading" style="height: 400px"></div>

      <!-- 空数据状态 -->
      <el-empty
        v-else-if="!selectedStudentId"
        :description="emptyText"
      />

      <!-- 数据展示 -->
      <div v-else class="content-section">
        <!-- 图表区域 -->
        <el-row :gutter="20" class="chart-section">
          <el-col :span="24">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">总分趋势</span>
              </template>
              <div ref="totalChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="20" class="chart-section">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">科目成绩对比</span>
              </template>
              <div ref="subjectChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">排名变化</span>
              </template>
              <div ref="rankChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 个人科目雷达图（最近一次考试，各科按真实满分归一） -->
        <el-row :gutter="20" class="chart-section">
          <el-col :span="24">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">最近一次考试各科得分雷达图</span>
              </template>
              <el-empty
                v-if="trend.subjectRadars.length === 0"
                description="暂无科目得分数据"
                :image-size="80"
              />
              <div v-show="trend.subjectRadars.length > 0" ref="radarChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 历次考试详细成绩表格 -->
        <el-card shadow="never" class="table-section">
          <template #header>
            <span class="section-title">历次考试详细成绩</span>
          </template>
          <el-table :data="trend.examDetails" stripe border max-height="400">
            <el-table-column prop="examName" label="考试名称" width="200" fixed />
            <el-table-column prop="examDate" label="考试日期" width="120" />
            <el-table-column prop="totalScore" label="总分" width="100" align="center">
              <template #default="{ row }">
                <span class="score-highlight">{{ row.totalScore.toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="各科分数">
              <template #default="{ row }">
                <el-tag
                  v-for="(score, subject) in row.subjects"
                  :key="subject"
                  style="margin-right: 5px"
                  :type="score >= 85 ? 'success' : score >= 60 ? 'info' : 'danger'"
                >
                  {{ subject }}: {{ score }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="classRank" label="班级排名" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.classRank === 1" type="danger" effect="dark">🥇 {{ row.classRank }}</el-tag>
                <el-tag v-else-if="row.classRank === 2" type="warning" effect="dark">🥈 {{ row.classRank }}</el-tag>
                <el-tag v-else-if="row.classRank === 3" type="success" effect="dark">🥉 {{ row.classRank }}</el-tag>
                <span v-else>{{ row.classRank }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="gradeRank" label="年级排名" width="100" align="center" />
          </el-table>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import { getStudentTrend, type ExamTrend, type SubjectRadar } from '@/api/stat'
import { getStudentList, type Student } from '@/api/student'
import { getTermList, termLabel, type Term } from '@/api/term'
import { handleError } from '@/utils/errorHandler'
import { useUserStore } from '@/stores/user'
import { useRoute } from 'vue-router'
import { getMyChildren, type ChildBrief } from '@/api/parent'

interface SubjectTrendItem {
  subjectName: string
  scores: number[]
}

interface ExamDetailRow {
  examName: string
  examDate?: string
  totalScore: number
  subjects: Record<string, number>
  classRank?: number
  gradeRank?: number
}

interface TrendView {
  examNames: string[]
  totalScores: number[]
  classRanks: number[]
  subjectTrends: SubjectTrendItem[]
  examDetails: ExamDetailRow[]
  /** 最近一次考试各科得分（契约 3 subjectRadars） */
  subjectRadars: SubjectRadar[]
}

const loading = ref(false)
const studentsLoading = ref(false)
const termsLoading = ref(false)
const selectedStudentId = ref<number>()
const selectedTermId = ref<number>()
const studentList = ref<Student[]>([])
const termList = ref<Term[]>([])

const userStore = useUserStore()
// ponytail: userType 4 = STUDENT，与后端 UserRole.STUDENT 一致
const isStudent = computed(() => userStore.userInfo?.userType === 4)
// ponytail: userType 5 = PARENT，与后端 UserRole.PARENT 一致；孩子列表实时取自 /parents/me/children
const isParent = computed(() => userStore.userInfo?.userType === 5)
const children = ref<ChildBrief[]>([])
const route = useRoute()
// ponytail: 契约 2 — 学生账号可能尚未关联学生档案（studentId 为 null），此时给出明确提示而不是一直「加载中」
const noStudentProfile = ref(false)
const emptyText = computed(() => {
  if (isParent.value) return children.value.length ? '请选择孩子查看成绩趋势' : '当前账号尚未绑定孩子，请联系学校管理员'
  if (!isStudent.value) return '请选择学生查看成绩趋势'
  return noStudentProfile.value ? '当前账号未关联学生档案' : '加载中...'
})
const totalChartRef = ref<HTMLElement>()
const subjectChartRef = ref<HTMLElement>()
const rankChartRef = ref<HTMLElement>()
const radarChartRef = ref<HTMLElement>()
let totalChartInstance: ECharts | null = null
let subjectChartInstance: ECharts | null = null
let rankChartInstance: ECharts | null = null
let radarChartInstance: ECharts | null = null

const trend = reactive<TrendView>({
  examNames: [],
  totalScores: [],
  classRanks: [],
  subjectTrends: [],
  examDetails: [],
  subjectRadars: []
})

function buildTrendView(examTrends: ExamTrend[], subjectRadars: SubjectRadar[] = []): TrendView {
  const examNames = examTrends.map(t => t.examName)
  const totalScores = examTrends.map(t => t.totalScore)
  const classRanks = examTrends.map(t => t.rankInClass ?? 0)

  // transpose subjectScores: gather unique subject names then build score arrays
  const subjectNames = Array.from(
    new Set(examTrends.flatMap(t => Object.keys(t.subjectScores ?? {})))
  )
  const subjectTrends: SubjectTrendItem[] = subjectNames.map(name => ({
    subjectName: name,
    scores: examTrends.map(t => t.subjectScores?.[name] ?? 0)
  }))

  const examDetails: ExamDetailRow[] = examTrends.map(t => ({
    examName: t.examName,
    examDate: t.examDate,
    totalScore: t.totalScore,
    subjects: t.subjectScores ?? {},
    classRank: t.rankInClass,
    gradeRank: t.rankInGrade
  }))

  return { examNames, totalScores, classRanks, subjectTrends, examDetails, subjectRadars }
}

const colors = {
  primary: '#409EFF',
  success: '#67C23A',
  warning: '#E6A23C',
  danger: '#F56C6C',
  info: '#909399'
}

const subjectColors = [
  '#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399',
  '#5470C6', '#91CC75', '#FAC858', '#EE6666', '#73C0DE'
]

const handleStudentSearch = async (query: string) => {
  if (!query) {
    studentList.value = []
    return
  }

  studentsLoading.value = true
  try {
    const data = await getStudentList({
      pageNum: 1,
      pageSize: 20,
      name: query
    })
    studentList.value = data.records || []
  } catch (error) {
    handleError(error, '搜索学生失败')
  } finally {
    studentsLoading.value = false
  }
}

const loadTermList = async () => {
  termsLoading.value = true
  try {
    const data = await getTermList({ pageNum: 1, pageSize: 100 })
    termList.value = data.records || []
  } catch (error) {
    handleError(error, '加载学期列表失败')
  } finally {
    termsLoading.value = false
  }
}

const loadTrend = async () => {
  if (!selectedStudentId.value) return

  loading.value = true
  try {
    const data = await getStudentTrend(selectedStudentId.value, { termId: selectedTermId.value })
    const view = buildTrendView(data.examTrends || [], data.subjectRadars || [])
    Object.assign(trend, view)

    // 图表容器在 v-if="loading" 分支下未挂载，必须先关闭 loading 再等 DOM
    loading.value = false
    await nextTick()
    initTotalChart()
    initSubjectChart()
    initRankChart()
    initRadarChart()
  } catch (error) {
    handleError(error, '加载成绩趋势失败')
  } finally {
    loading.value = false
  }
}

// ponytail: 家长默认选中路由 query.studentId 对应的孩子（来自「我的孩子」页跳转），否则选第一个
const loadChildrenAndTrend = async () => {
  try {
    children.value = await getMyChildren()
  } catch (error) {
    handleError(error, '加载孩子列表失败')
    return
  }
  const fromRoute = Number(route.query.studentId)
  const preferred = children.value.find(c => c.studentId === fromRoute) ?? children.value[0]
  if (!preferred) return
  selectedStudentId.value = preferred.studentId
  await loadTrend()
}

const initTotalChart = () => {
  if (!totalChartRef.value || trend.totalScores.length === 0) return

  if (totalChartInstance) {
    totalChartInstance.dispose()
  }

  totalChartInstance = echarts.init(totalChartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: trend.examNames,
      axisLabel: {
        rotate: 30,
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      name: '总分'
    },
    series: [
      {
        data: trend.totalScores,
        type: 'line',
        smooth: true,
        symbolSize: 8,
        itemStyle: {
          color: colors.primary
        },
        lineStyle: {
          width: 3,
          color: colors.primary
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        },
        label: {
          show: true,
          position: 'top',
          formatter: (params: { value: number }) => params.value.toFixed(2)
        }
      }
    ]
  }

  totalChartInstance.setOption(option)
}

const initSubjectChart = () => {
  if (!subjectChartRef.value || trend.subjectTrends.length === 0) return

  if (subjectChartInstance) {
    subjectChartInstance.dispose()
  }

  subjectChartInstance = echarts.init(subjectChartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: trend.subjectTrends.map(item => item.subjectName),
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: trend.examNames,
      axisLabel: {
        rotate: 30,
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      name: '分数'
    },
    series: trend.subjectTrends.map((item, index) => ({
      name: item.subjectName,
      type: 'line',
      data: item.scores,
      smooth: true,
      itemStyle: {
        color: subjectColors[index % subjectColors.length]
      },
      lineStyle: {
        width: 2
      }
    }))
  }

  subjectChartInstance.setOption(option)
}

const initRankChart = () => {
  if (!rankChartRef.value || trend.classRanks.length === 0) return

  if (rankChartInstance) {
    rankChartInstance.dispose()
  }

  rankChartInstance = echarts.init(rankChartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params: Array<{ value: number, name: string }>) => {
        const item = params[0]
        return `${item.name}<br/>班级排名: 第${item.value}名`
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
      data: trend.examNames,
      axisLabel: {
        rotate: 30,
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value',
      name: '排名',
      inverse: true,
      minInterval: 1
    },
    series: [
      {
        data: trend.classRanks,
        type: 'line',
        smooth: true,
        symbolSize: 8,
        itemStyle: {
          color: colors.warning
        },
        lineStyle: {
          width: 3,
          color: colors.warning
        },
        label: {
          show: true,
          position: 'top',
          formatter: (params: { value: number }) => `第${params.value}名`
        }
      }
    ]
  }

  rankChartInstance.setOption(option)
}

// ponytail: 契约 3 — 计划书要求的个人雷达图；指示器上限用各科真实满分，等价于按得分率归一
const initRadarChart = () => {
  if (!radarChartRef.value || trend.subjectRadars.length === 0) return

  if (radarChartInstance) {
    radarChartInstance.dispose()
  }

  radarChartInstance = echarts.init(radarChartRef.value)

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: () =>
        trend.subjectRadars
          .map(r => `${r.subjectName}: ${r.score} / ${r.fullScore}（${Number(r.percentage).toFixed(1)}%）`)
          .join('<br/>')
    },
    radar: {
      indicator: trend.subjectRadars.map(r => ({ name: r.subjectName, max: r.fullScore || 100 })),
      radius: '65%'
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            name: '最近一次考试得分',
            value: trend.subjectRadars.map(r => r.score),
            itemStyle: { color: colors.primary },
            lineStyle: { width: 2, color: colors.primary },
            areaStyle: { color: 'rgba(64, 158, 255, 0.25)' }
          }
        ]
      }
    ]
  }

  radarChartInstance.setOption(option)
}

const handleResize = () => {
  totalChartInstance?.resize()
  subjectChartInstance?.resize()
  rankChartInstance?.resize()
  radarChartInstance?.resize()
}

const handleStudentChange = () => {
  loadTrend()
}

const handleTermChange = () => {
  loadTrend()
}

onMounted(() => {
  loadTermList()
  window.addEventListener('resize', handleResize)
  if (isParent.value) {
    void loadChildrenAndTrend()
    return
  }
  // 学生角色直接加载自身趋势，无需搜索
  // ponytail: 契约 2 — 用 userInfo.studentId（学生档案 id）而非 userInfo.id（用户 id）；未关联档案时提示而不发请求
  if (isStudent.value) {
    const studentId = userStore.userInfo?.studentId
    if (!studentId) {
      noStudentProfile.value = true
      ElMessage.warning('当前账号未关联学生档案')
      return
    }
    selectedStudentId.value = studentId
    loadTrend()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (totalChartInstance) {
    totalChartInstance.dispose()
    totalChartInstance = null
  }
  if (subjectChartInstance) {
    subjectChartInstance.dispose()
    subjectChartInstance = null
  }
  if (rankChartInstance) {
    rankChartInstance.dispose()
    rankChartInstance = null
  }
  if (radarChartInstance) {
    radarChartInstance.dispose()
    radarChartInstance = null
  }
})
</script>

<style scoped lang="scss">
.student-trend {
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

      .score-highlight {
        color: #409eff;
        font-weight: bold;
        font-size: 16px;
      }
    }
  }
}
</style>

