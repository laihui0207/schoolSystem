<template>
  <div class="region-compare">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">区域成绩对比</span>
          <span class="subtitle">同一区域编码下的学校，按学年 / 学期 / 考试类型各取最新一场已发布考试</span>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-form" @submit.prevent="handleQuery">
        <el-form-item label="区域">
          <el-select
            v-model="query.regionCode"
            placeholder="请选择区域"
            style="width: 220px"
            :loading="regionsLoading"
            filterable
          >
            <el-option
              v-for="region in regions"
              :key="region.regionCode"
              :label="`${region.regionCode}（${region.schoolCount} 所）`"
              :value="region.regionCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学年">
          <el-input
            v-model="query.year"
            placeholder="如 2024-2025"
            style="width: 160px"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="query.term" style="width: 120px">
            <el-option label="上学期" :value="1" />
            <el-option label="下学期" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试类型">
          <el-select v-model="query.examType" style="width: 140px">
            <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>

      <!-- 加载状态 -->
      <div v-if="loading" v-loading="loading" style="height: 300px"></div>

      <!-- 未查询 -->
      <el-empty v-else-if="!result" description="请选择区域、学年、学期和考试类型后查询" />

      <!-- 区域下没有学校 -->
      <el-empty v-else-if="result.schools.length === 0" description="该区域下没有启用的学校" />

      <!-- 数据展示 -->
      <div v-else class="content-section">
        <el-alert
          v-if="unrankedCount > 0"
          type="info"
          :closable="false"
          show-icon
          class="hint"
          :title="`${unrankedCount} 所学校在该学年学期没有已发布的此类考试，不参与排名`"
        />

        <el-table :data="result.schools" stripe border class="table-section">
          <el-table-column label="排名" width="80" align="center">
            <template #default="{ row }">{{ row.rank ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="schoolName" label="学校" min-width="160" />
          <el-table-column label="考试" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.examName ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="studentCount" label="参考人数" width="100" align="center" />
          <el-table-column label="总分均分" width="110" align="center">
            <template #default="{ row }">{{ formatMetric(row as RegionSchoolStat, row.averageScore) }}</template>
          </el-table-column>
          <el-table-column label="及格率" width="100" align="center">
            <template #default="{ row }">{{ formatMetric(row as RegionSchoolStat, row.passRate, '%') }}</template>
          </el-table-column>
          <el-table-column label="优秀率" width="100" align="center">
            <template #default="{ row }">{{ formatMetric(row as RegionSchoolStat, row.excellentRate, '%') }}</template>
          </el-table-column>
        </el-table>

        <el-row :gutter="20" class="chart-section">
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">各校总分均分</span>
              </template>
              <div ref="schoolChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never">
              <template #header>
                <span class="section-title">各科跨校对比</span>
              </template>
              <div ref="subjectChartRef" class="chart-container"></div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import {
  getRegionOptions,
  getRegionStat,
  type RegionOption,
  type RegionSchoolStat,
  type RegionStat
} from '@/api/stat'
import { typeOptions } from '@/views/exam/examOptions'
import { handleError } from '@/utils/errorHandler'

const loading = ref(false)
const regionsLoading = ref(false)
const regions = ref<RegionOption[]>([])
const result = ref<RegionStat | null>(null)
const schoolChartRef = ref<HTMLElement>()
const subjectChartRef = ref<HTMLElement>()
let schoolChart: ECharts | null = null
let subjectChart: ECharts | null = null

// ponytail: 默认期末(3)/上学期(1)——跨校对比最常用的是期末；学年留空强制用户填，避免误查成空结果
const query = reactive({
  regionCode: '',
  year: '',
  term: 1,
  examType: 3
})

const unrankedCount = computed(
  () => (result.value?.schools ?? []).filter(school => school.rank == null).length
)

/** 没有可比成绩的学校（rank 缺省）指标显示为 —，避免把 0.00 当成真实均分 */
const formatMetric = (row: RegionSchoolStat, value: number, suffix = ''): string =>
  row.rank == null ? '—' : `${value.toFixed(2)}${suffix}`

const rankedSchools = (data: RegionStat): RegionSchoolStat[] =>
  data.schools.filter(school => school.rank != null)

const loadRegions = async () => {
  regionsLoading.value = true
  try {
    regions.value = (await getRegionOptions()) ?? []
  } catch (error) {
    handleError(error, '加载区域列表失败')
  } finally {
    regionsLoading.value = false
  }
}

const handleQuery = async () => {
  const year = query.year.trim()
  if (!query.regionCode) {
    ElMessage.warning('请选择区域')
    return
  }
  if (!year) {
    ElMessage.warning('请输入学年，如 2024-2025')
    return
  }
  loading.value = true
  try {
    const data = await getRegionStat({
      regionCode: query.regionCode,
      year,
      term: query.term,
      examType: query.examType
    })
    result.value = data
    // 图表容器在 v-if="loading" 分支下未挂载，必须先关闭 loading 再等 DOM
    loading.value = false
    await nextTick()
    renderSchoolChart(data)
    renderSubjectChart(data)
  } catch (error) {
    handleError(error, '加载区域对比失败')
  } finally {
    loading.value = false
  }
}

const renderSchoolChart = (data: RegionStat) => {
  if (!schoolChartRef.value) return
  schoolChart?.dispose()
  schoolChart = echarts.init(schoolChartRef.value)
  const rows = rankedSchools(data)
  const option: EChartsOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map(school => school.schoolName),
      axisLabel: { interval: 0, rotate: rows.length > 6 ? 30 : 0 }
    },
    yAxis: { type: 'value', name: '总分均分' },
    series: [
      {
        type: 'bar',
        barWidth: '50%',
        data: rows.map(school => school.averageScore),
        label: { show: true, position: 'top', formatter: '{c}' },
        itemStyle: { color: '#409EFF' }
      }
    ]
  }
  schoolChart.setOption(option)
}

const renderSubjectChart = (data: RegionStat) => {
  if (!subjectChartRef.value) return
  subjectChart?.dispose()
  subjectChart = echarts.init(subjectChartRef.value)
  const rows = rankedSchools(data)
  // ponytail: 分类轴是科目、每校一个系列；某校没考该科时用 '-' 让 ECharts 留空，而不是画成 0
  const option: EChartsOption = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: rows.map(school => school.schoolName), top: 0, type: 'scroll' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 50, containLabel: true },
    xAxis: { type: 'category', data: data.subjects.map(subject => subject.subjectName) },
    yAxis: { type: 'value', name: '均分' },
    series: rows.map(school => ({
      name: school.schoolName,
      type: 'bar' as const,
      data: data.subjects.map(
        subject => subject.schools.find(item => item.schoolId === school.schoolId)?.averageScore ?? '-'
      )
    }))
  }
  subjectChart.setOption(option)
}

const handleResize = () => {
  schoolChart?.resize()
  subjectChart?.resize()
}

onMounted(() => {
  loadRegions()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  schoolChart?.dispose()
  schoolChart = null
  subjectChart?.dispose()
  subjectChart = null
})
</script>

<style scoped lang="scss">
.region-compare {
  .card-header {
    display: flex;
    align-items: baseline;
    gap: 12px;

    .title {
      font-size: 18px;
      font-weight: bold;
    }

    .subtitle {
      font-size: 13px;
      color: #909399;
    }
  }

  .filter-form {
    margin-bottom: 20px;
  }

  .content-section {
    .hint {
      margin-bottom: 16px;
    }

    .table-section {
      margin-bottom: 20px;
    }

    .chart-section {
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
