<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ChildSwitcher from '@/components/ChildSwitcher.vue'
import EmptyState from '@/components/EmptyState.vue'
import TrendBars from '@/components/TrendBars.vue'
// #ifdef H5
import TrendCharts from '@/components/TrendCharts.vue'
// #endif
import { getStudentTrend } from '@/api/stat'
import { useCurrentStudent } from '@/composables/useCurrentStudent'
import type { ExamTrend, StudentTrendResponse, SubjectRadar } from '@/types'
import { errorMessage } from '@/utils/navigate'

const { studentId, emptyHint } = useCurrentStudent()

const trend = ref<StudentTrendResponse | null>(null)
const loading = ref(false)
const error = ref('')

/** 按考试日期升序，折线从左到右即时间顺序 */
const trends = computed<ExamTrend[]>(() =>
  [...(trend.value?.examTrends ?? [])].sort((a, b) => (a.examDate ?? '').localeCompare(b.examDate ?? ''))
)
const radars = computed<SubjectRadar[]>(() => trend.value?.subjectRadars ?? [])

const load = async (): Promise<void> => {
  const id = studentId.value
  if (id === null) return
  loading.value = true
  error.value = ''
  try {
    trend.value = await getStudentTrend(id)
  } catch (err) {
    trend.value = null
    error.value = errorMessage(err, '加载失败')
  } finally {
    loading.value = false
  }
}

onShow(() => {
  void load()
})
</script>

<template>
  <view class="page">
    <ChildSwitcher @change="load" />
    <EmptyState v-if="emptyHint" :text="emptyHint" />
    <view v-else-if="loading" class="loading">加载中…</view>
    <EmptyState v-else-if="error" :text="error" />
    <EmptyState v-else-if="trends.length === 0" text="暂无已发布的考试成绩" />
    <template v-else>
      <view v-if="trend?.studentName" class="muted who">{{ trend.studentName }} · 共 {{ trends.length }} 场考试</view>
      <!-- #ifdef H5 -->
      <TrendCharts :trends="trends" :radars="radars" />
      <!-- #endif -->
      <TrendBars :trends="trends" :radars="radars" />
    </template>
  </view>
</template>

<style scoped lang="scss">
.who {
  margin: 0 4rpx 20rpx;
}
</style>
