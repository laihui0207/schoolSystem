<template>
  <div class="stat-page">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Tab 1: 班级统计 -->
      <el-tab-pane label="班级统计" name="class">
        <el-form :model="classFilter" inline>
          <el-form-item label="考试">
            <el-select v-model="classFilter.examId" placeholder="请选择考试" clearable style="width:200px">
              <el-option v-for="e in exams" :key="e.id" :label="e.examName" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select v-model="classFilter.classId" placeholder="请选择班级" clearable style="width:160px">
              <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="classLoading" :disabled="!classFilter.examId || !classFilter.classId" @click="loadClassStat">查询</el-button>
            <el-button @click="resetClass">重置</el-button>
          </el-form-item>
        </el-form>

        <template v-if="classStat">
          <el-row :gutter="16" class="stat-cards">
            <el-col :span="4" v-for="item in classStatItems" :key="item.label">
              <el-card shadow="never" class="stat-card">
                <div class="stat-value">{{ item.value }}</div>
                <div class="stat-label">{{ item.label }}</div>
              </el-card>
            </el-col>
          </el-row>
          <div ref="classChartEl" style="height:300px;margin-top:16px" />
        </template>
        <el-empty v-else-if="!classLoading" description="请选择考试和班级后查询" />
      </el-tab-pane>

      <!-- Tab 2: 年级统计 -->
      <el-tab-pane label="年级统计" name="grade">
        <el-form :model="gradeFilter" inline>
          <el-form-item label="考试">
            <el-select v-model="gradeFilter.examId" placeholder="请选择考试" clearable style="width:200px">
              <el-option v-for="e in exams" :key="e.id" :label="e.examName" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="年级">
            <el-select v-model="gradeFilter.gradeId" placeholder="请选择年级" clearable style="width:160px">
              <el-option v-for="g in grades" :key="g.id" :label="g.gradeName" :value="g.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="gradeLoading" :disabled="!gradeFilter.examId || !gradeFilter.gradeId" @click="loadGradeStat">查询</el-button>
            <el-button @click="resetGrade">重置</el-button>
          </el-form-item>
        </el-form>

        <div v-if="gradeStat" ref="gradeChartEl" style="height:300px;margin-top:16px" />
        <el-empty v-else-if="!gradeLoading" description="请选择考试和年级后查询" />
      </el-tab-pane>

      <!-- Tab 3: 个人成绩趋势 -->
      <el-tab-pane label="个人趋势" name="personal">
        <el-form :model="personalFilter" inline>
          <el-form-item label="学生">
            <el-select
              v-model="personalFilter.studentId"
              placeholder="输入学号或姓名搜索"
              filterable
              remote
              :remote-method="searchStudents"
              :loading="studentSearching"
              clearable
              style="width:220px"
            >
              <el-option v-for="s in studentOptions" :key="s.id" :label="`${s.name}(${s.studentNo})`" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="考试">
            <el-select v-model="personalFilter.examId" placeholder="请选择考试" clearable style="width:200px">
              <el-option v-for="e in exams" :key="e.id" :label="e.examName" :value="e.id" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="personalLoading" :disabled="!personalFilter.studentId || !personalFilter.examId" @click="loadPersonalStat">查询</el-button>
            <el-button @click="resetPersonal">重置</el-button>
          </el-form-item>
        </el-form>

        <template v-if="personalStat">
          <div ref="personalChartEl" style="height:300px;margin-top:16px" />
          <div ref="trendChartEl" style="height:280px;margin-top:16px" />
        </template>
        <el-empty v-else-if="!personalLoading" description="请选择学生和考试后查询" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getClassStat, getGradeStat, getPersonalStat, getStudentTrend } from '@/api/stat'
import { getExamList } from '@/api/exam'
import { getClassList } from '@/api/class'
import { getGradeList } from '@/api/grade'
import { getStudentList } from '@/api/student'

const activeTab = ref('class')

// --- shared data ---
const exams = ref<any[]>([])
const classes = ref<any[]>([])
const grades = ref<any[]>([])

// --- Tab 1: 班级统计 ---
const classLoading = ref(false)
const classStat = ref<any>(null)
const classChartEl = ref<HTMLElement>()
let classChart: echarts.ECharts | null = null

const classFilter = reactive({ examId: undefined as number | undefined, classId: undefined as number | undefined })

const classStatItems = computed(() => {
  const s = classStat.value
  if (!s) return []
  return [
    { label: '平均分', value: s.averageScore != null ? Number(s.averageScore).toFixed(1) : '-' },
    { label: '最高分', value: s.highestScore ?? '-' },
    { label: '最低分', value: s.lowestScore ?? '-' },
    { label: '及格率', value: s.passRate != null ? (s.passRate * 100).toFixed(1) + '%' : '-' },
    { label: '优秀率', value: s.excellentRate != null ? (s.excellentRate * 100).toFixed(1) + '%' : '-' },
  ]
})

const loadClassStat = async () => {
  classLoading.value = true
  classStat.value = null
  try {
    const res = await getClassStat({ examId: classFilter.examId, classId: classFilter.classId }) as any
    classStat.value = res
    await nextTick()
    renderClassChart(res)
  } catch {
    ElMessage.error('班级统计加载失败')
  } finally {
    classLoading.value = false
  }
}

const renderClassChart = (data: any) => {
  if (!classChartEl.value) return
  classChart?.dispose()
  classChart = echarts.init(classChartEl.value)
  // ponytail: use scoreDistribution if present, else fall back to score buckets from stat fields
  const dist: any[] = data.scoreDistribution ?? data.distribution ?? []
  if (!dist.length) {
    classChart.setOption({
      title: { text: '暂无分布数据', left: 'center', top: 'center', textStyle: { color: '#909399', fontSize: 14 } },
      xAxis: { show: false }, yAxis: { show: false }, series: []
    })
    return
  }
  classChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dist.map((d: any) => d.range ?? d.label) },
    yAxis: { type: 'value', name: '人数' },
    series: [{ name: '人数', type: 'bar', data: dist.map((d: any) => d.count), itemStyle: { color: '#409eff' } }]
  })
}

const resetClass = () => {
  classFilter.examId = undefined
  classFilter.classId = undefined
  classStat.value = null
}

// load classes when exam changes
watch(() => classFilter.examId, async (examId) => {
  classFilter.classId = undefined
  classes.value = []
  if (!examId) return
  const res = await getClassList({ pageNum: 1, pageSize: 1000 }) as any
  classes.value = res?.records ?? []
})

// --- Tab 2: 年级统计 ---
const gradeLoading = ref(false)
const gradeStat = ref<any>(null)
const gradeChartEl = ref<HTMLElement>()
let gradeChart: echarts.ECharts | null = null

const gradeFilter = reactive({ examId: undefined as number | undefined, gradeId: undefined as number | undefined })

const loadGradeStat = async () => {
  gradeLoading.value = true
  gradeStat.value = null
  try {
    const res = await getGradeStat({ examId: gradeFilter.examId, gradeId: gradeFilter.gradeId }) as any
    gradeStat.value = res
    await nextTick()
    renderGradeChart(res)
  } catch {
    ElMessage.error('年级统计加载失败')
  } finally {
    gradeLoading.value = false
  }
}

const renderGradeChart = (data: any) => {
  if (!gradeChartEl.value) return
  gradeChart?.dispose()
  gradeChart = echarts.init(gradeChartEl.value)
  // expect data.classSummaries = [{className, averageScore}] or similar
  const items: any[] = data.classSummaries ?? data.classes ?? data.classStats ?? []
  if (!items.length) {
    gradeChart.setOption({
      title: { text: '暂无班级数据', left: 'center', top: 'center', textStyle: { color: '#909399', fontSize: 14 } },
      xAxis: { show: false }, yAxis: { show: false }, series: []
    })
    return
  }
  gradeChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: items.map((c: any) => c.className ?? c.name) },
    yAxis: { type: 'value', name: '平均分', min: 0, max: 150 },
    series: [{ name: '平均分', type: 'bar', data: items.map((c: any) => Number(c.averageScore ?? 0).toFixed(1)), itemStyle: { color: '#67c23a' } }]
  })
}

const resetGrade = () => {
  gradeFilter.examId = undefined
  gradeFilter.gradeId = undefined
  gradeStat.value = null
}

// --- Tab 3: 个人趋势 ---
const personalLoading = ref(false)
const studentSearching = ref(false)
const personalStat = ref<any>(null)
const studentOptions = ref<any[]>([])
const personalChartEl = ref<HTMLElement>()
const trendChartEl = ref<HTMLElement>()
let personalChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

const personalFilter = reactive({ studentId: undefined as number | undefined, examId: undefined as number | undefined })

const searchStudents = async (query: string) => {
  if (!query) return
  studentSearching.value = true
  try {
    const res = await getStudentList({ name: query.match(/\D/) ? query : undefined, studentNo: query.match(/^\d+$/) ? query : undefined, pageNum: 1, pageSize: 20 }) as any
    studentOptions.value = res?.records ?? []
  } catch {
    // silent
  } finally {
    studentSearching.value = false
  }
}

const loadPersonalStat = async () => {
  personalLoading.value = true
  personalStat.value = null
  try {
    const [stat, trend] = await Promise.all([
      getPersonalStat({ examId: personalFilter.examId, studentId: personalFilter.studentId }) as any,
      getStudentTrend(personalFilter.studentId!) as any
    ])
    personalStat.value = stat
    await nextTick()
    renderPersonalChart(stat)
    renderTrendChart(trend)
  } catch {
    ElMessage.error('个人统计加载失败')
  } finally {
    personalLoading.value = false
  }
}

const renderPersonalChart = (data: any) => {
  if (!personalChartEl.value) return
  personalChart?.dispose()
  personalChart = echarts.init(personalChartEl.value)
  // expect data.subjectScores = [{subjectName, score}]
  const items: any[] = data.subjectScores ?? data.scores ?? data.subjects ?? []
  if (!items.length) {
    personalChart.setOption({
      title: { text: '暂无科目数据', left: 'center', top: 'center', textStyle: { color: '#909399', fontSize: 14 } },
      xAxis: { show: false }, yAxis: { show: false }, series: []
    })
    return
  }
  personalChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: items.map((s: any) => s.subjectName ?? s.name) },
    yAxis: { type: 'value', name: '分数' },
    series: [{ name: '分数', type: 'bar', data: items.map((s: any) => s.score), itemStyle: { color: '#e6a23c' } }]
  })
}

const renderTrendChart = (data: any) => {
  if (!trendChartEl.value) return
  trendChart?.dispose()
  trendChart = echarts.init(trendChartEl.value)
  // expect data = [{examName, totalScore}] or {trends:[...]}
  const items: any[] = Array.isArray(data) ? data : (data?.trends ?? data?.records ?? [])
  if (!items.length) {
    trendChart.setOption({
      title: { text: '暂无历史趋势', left: 'center', top: 'center', textStyle: { color: '#909399', fontSize: 14 } },
      xAxis: { show: false }, yAxis: { show: false }, series: []
    })
    return
  }
  trendChart.setOption({
    title: { text: '历次考试总分趋势', left: 'center', textStyle: { fontSize: 13 } },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: items.map((t: any) => t.examName ?? t.name) },
    yAxis: { type: 'value', name: '总分' },
    series: [{ name: '总分', type: 'line', smooth: true, data: items.map((t: any) => t.totalScore ?? t.score), itemStyle: { color: '#f56c6c' } }]
  })
}

const resetPersonal = () => {
  personalFilter.studentId = undefined
  personalFilter.examId = undefined
  personalStat.value = null
  studentOptions.value = []
}

// --- init ---
onMounted(async () => {
  const [examRes, gradeRes] = await Promise.all([
    getExamList({ pageNum: 1, pageSize: 1000 }) as any,
    getGradeList({ pageNum: 1, pageSize: 1000 }) as any
  ])
  exams.value = examRes?.records ?? []
  grades.value = gradeRes?.records ?? []
})
</script>

<style scoped lang="scss">
.stat-page {
  .stat-cards {
    margin-top: 20px;
    .stat-card {
      text-align: center;
      padding: 8px 0;
      .stat-value {
        font-size: 24px;
        font-weight: bold;
        color: #409eff;
      }
      .stat-label {
        font-size: 13px;
        color: #909399;
        margin-top: 4px;
      }
    }
  }
}
</style>
