<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsCoreOption } from 'echarts/core'
import type { ExamTrend, SubjectRadar } from '@/types'

/**
 * H5 专用：echarts 折线（历次总分 + 班级名次）与雷达（各科得分率）。
 * ponytail: 只在 pages/trend 里以 `#ifdef H5` 引入，小程序构建不会打进 echarts；
 * 按需注册图表/组件（echarts/core）而不是 import 'echarts'，H5 包体小一半以上
 */
echarts.use([LineChart, RadarChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ trends: ExamTrend[]; radars: SubjectRadar[] }>()

const lineEl = ref<HTMLDivElement | null>(null)
const radarEl = ref<HTMLDivElement | null>(null)
let lineChart: echarts.ECharts | null = null
let radarChart: echarts.ECharts | null = null

const PRIMARY = '#2f6fed'
const ACCENT = '#f59e0b'
const GREEN = '#1f9d55'

/** 'yyyy-MM-dd' → 'yy.MM'；无日期则退回考试名前 6 字 */
const shortLabel = (t: ExamTrend): string => {
  const d = t.examDate ?? ''
  if (/^\d{4}-\d{2}/.test(d)) return `${d.slice(2, 4)}.${d.slice(5, 7)}`
  return (t.examName ?? '').slice(0, 6)
}

interface AxisTooltipItem {
  dataIndex: number
  seriesName?: string
  value?: unknown
  marker?: string
}

const lineOption = (trends: ExamTrend[]): EChartsCoreOption => ({
  tooltip: {
    trigger: 'axis',
    // ponytail: 考试名太长，x 轴只放"年.月"，完整考试名放到 tooltip 标题里
    formatter: (params: AxisTooltipItem[] | AxisTooltipItem) => {
      const items = Array.isArray(params) ? params : [params]
      const first = items[0]
      if (!first) return ''
      const title = trends[first.dataIndex]?.examName ?? ''
      const lines = items.map((p) => `${p.marker ?? ''}${p.seriesName ?? ''}：${p.value ?? '—'}`)
      return [title, ...lines].join('<br/>')
    }
  },
  legend: { data: ['总分', '班级名次'], bottom: 0, textStyle: { fontSize: 11 } },
  grid: { left: 36, right: 36, top: 24, bottom: 48 },
  xAxis: {
    type: 'category',
    data: trends.map(shortLabel),
    axisLabel: { fontSize: 10, interval: 0 }
  },
  yAxis: [
    { type: 'value', name: '分', axisLabel: { fontSize: 10 } },
    // 名次越小越好：反转坐标轴让"上升"在视觉上也向上
    { type: 'value', name: '名', inverse: true, minInterval: 1, axisLabel: { fontSize: 10 }, splitLine: { show: false } }
  ],
  series: [
    {
      name: '总分',
      type: 'line',
      smooth: true,
      data: trends.map((t) => t.totalScore ?? null),
      itemStyle: { color: PRIMARY },
      areaStyle: { color: 'rgba(47,111,237,0.08)' }
    },
    {
      name: '班级名次',
      type: 'line',
      yAxisIndex: 1,
      smooth: true,
      data: trends.map((t) => t.rankInClass ?? null),
      itemStyle: { color: ACCENT },
      lineStyle: { type: 'dashed' }
    }
  ]
})

const percentOf = (r: SubjectRadar): number => {
  if (typeof r.percentage === 'number') return Math.round(r.percentage * 10) / 10
  if (typeof r.score === 'number' && typeof r.fullScore === 'number' && r.fullScore > 0) {
    return Math.round((r.score / r.fullScore) * 1000) / 10
  }
  return 0
}

const radarOption = (radars: SubjectRadar[]): EChartsCoreOption => ({
  tooltip: {},
  radar: {
    indicator: radars.map((r) => ({ name: r.subjectName ?? '', max: 100 })),
    radius: '62%',
    axisName: { fontSize: 11, color: '#4b5160' }
  },
  series: [
    {
      type: 'radar',
      data: [{ value: radars.map(percentOf), name: '得分率 %' }],
      itemStyle: { color: GREEN },
      areaStyle: { color: 'rgba(31,157,85,0.18)' }
    }
  ]
})

const render = (): void => {
  if (lineEl.value) {
    lineChart = lineChart ?? echarts.init(lineEl.value)
    lineChart.setOption(lineOption(props.trends), true)
  }
  if (radarEl.value) {
    radarChart = radarChart ?? echarts.init(radarEl.value)
    radarChart.setOption(radarOption(props.radars), true)
  }
}

const resize = (): void => {
  lineChart?.resize()
  radarChart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})

watch(() => [props.trends, props.radars], render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  lineChart?.dispose()
  radarChart?.dispose()
  lineChart = null
  radarChart = null
})
</script>

<template>
  <view>
    <view class="card">
      <view class="card-title">总分趋势</view>
      <view v-if="trends.length === 0" class="muted">暂无数据</view>
      <div v-show="trends.length > 0" ref="lineEl" class="chart"></div>
    </view>
    <view class="card">
      <view class="card-title">各科得分率雷达（最近一场）</view>
      <view v-if="radars.length < 3" class="muted">科目不足 3 门，雷达图不展示，请看下方明细</view>
      <div v-show="radars.length >= 3" ref="radarEl" class="chart chart-radar"></div>
    </view>
  </view>
</template>

<style scoped lang="scss">
.chart {
  width: 100%;
  height: 460rpx;
}

.chart-radar {
  height: 520rpx;
}
</style>
