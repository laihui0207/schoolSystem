<script setup lang="ts">
import { computed } from 'vue'
import type { ExamTrend, SubjectRadar } from '@/types'
import { fmtDate, fmtScore } from '@/utils/format'

/**
 * 全端通用的"条形"趋势视图：H5 作为图表下方的明细，小程序端作为主视图（不依赖 echarts）。
 */
const props = defineProps<{ trends: ExamTrend[]; radars: SubjectRadar[] }>()

const maxTotal = computed(() => Math.max(1, ...props.trends.map((t) => t.totalScore ?? 0)))

const barWidth = (value: number | undefined, max: number): string => `${Math.round(((value ?? 0) / max) * 100)}%`

const percentOf = (r: SubjectRadar): number => {
  if (typeof r.percentage === 'number') return Math.round(r.percentage)
  if (typeof r.score === 'number' && typeof r.fullScore === 'number' && r.fullScore > 0) {
    return Math.round((r.score / r.fullScore) * 100)
  }
  return 0
}
</script>

<template>
  <view>
    <view class="card">
      <view class="card-title">历次总分</view>
      <view v-if="trends.length === 0" class="muted">暂无数据</view>
      <view v-for="t in trends" :key="t.examId" class="bar-item">
        <view class="row-between bar-head">
          <text class="bar-name">{{ t.examName }}</text>
          <text class="muted">{{ fmtDate(t.examDate) }}</text>
        </view>
        <view class="bar-track">
          <view class="bar-fill" :style="{ width: barWidth(t.totalScore, maxTotal) }" />
        </view>
        <view class="row-between bar-foot">
          <text class="bar-value">{{ fmtScore(t.totalScore) }} 分</text>
          <text class="muted">班名 {{ t.rankInClass ?? '—' }} · 级名 {{ t.rankInGrade ?? '—' }}</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="card-title">各科得分率（最近一场）</view>
      <view v-if="radars.length === 0" class="muted">暂无数据</view>
      <view v-for="r in radars" :key="r.subjectId" class="bar-item">
        <view class="row-between bar-head">
          <text class="bar-name">{{ r.subjectName }}</text>
          <text class="muted">{{ fmtScore(r.score) }} / {{ fmtScore(r.fullScore) }}</text>
        </view>
        <view class="bar-track">
          <view class="bar-fill radar" :style="{ width: `${percentOf(r)}%` }" />
        </view>
        <view class="bar-foot">
          <text class="bar-value">{{ percentOf(r) }}%</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.bar-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f1f3;

  &:last-child {
    border-bottom: none;
  }
}

.bar-head {
  margin-bottom: 10rpx;
}

.bar-name {
  font-size: 28rpx;
  font-weight: 500;
}

.bar-track {
  height: 16rpx;
  border-radius: 8rpx;
  background: #f0f1f3;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 8rpx;
  background: linear-gradient(90deg, #2f6fed, #5b8dff);
  transition: width 0.3s ease;

  &.radar {
    background: linear-gradient(90deg, #1f9d55, #4cd98a);
  }
}

.bar-foot {
  margin-top: 8rpx;
  font-size: 24rpx;
}

.bar-value {
  font-weight: 600;
  color: #1f2329;
}
</style>
