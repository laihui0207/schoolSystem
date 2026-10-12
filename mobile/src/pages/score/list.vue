<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { listExams } from '@/api/exam'
import { useCurrentStudent } from '@/composables/useCurrentStudent'
import { userStore } from '@/store/user'
import type { ExamResponse } from '@/types'
import { fmtDate } from '@/utils/format'
import { errorMessage, toLogin, toScoreDetail } from '@/utils/navigate'

const PAGE_SIZE = 20

const { emptyHint } = useCurrentStudent()

const exams = ref<ExamResponse[]>([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')

const finished = computed(() => exams.value.length >= total.value)

const load = async (reset: boolean): Promise<void> => {
  if (!userStore.isLoggedIn.value) {
    toLogin()
    return
  }
  if (loading.value) return
  loading.value = true
  error.value = ''
  const target = reset ? 1 : pageNum.value
  try {
    const page = await listExams({
      schoolId: userStore.userInfo.value?.schoolId,
      pageNum: target,
      pageSize: PAGE_SIZE
    })
    exams.value = reset ? page.records : [...exams.value, ...page.records]
    total.value = page.total
    pageNum.value = target + 1
  } catch (err) {
    error.value = errorMessage(err, '加载失败')
  } finally {
    loading.value = false
  }
}

onShow(() => {
  // ponytail: 每次回到 tab 都刷新首页数据量很小（已发布考试列表），保证新发布的考试及时出现
  void load(true)
})

onPullDownRefresh(async () => {
  await load(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (!finished.value) void load(false)
})
</script>

<template>
  <view class="page">
    <EmptyState v-if="emptyHint" :text="emptyHint" />

    <template v-else>
      <view v-if="error" class="card muted">{{ error }}</view>
      <EmptyState v-else-if="!loading && exams.length === 0" text="暂无已发布的考试" />

      <view v-for="exam in exams" :key="exam.id" class="card exam" @tap="toScoreDetail(exam.id)">
        <view class="row-between">
          <text class="exam-name">{{ exam.examName }}</text>
          <text class="tag tag-green">{{ exam.statusName || '已发布' }}</text>
        </view>
        <view class="row meta">
          <text v-if="exam.examTypeName" class="tag tag-gray">{{ exam.examTypeName }}</text>
          <text class="muted">{{ fmtDate(exam.examDate) }}</text>
          <text v-if="exam.termName" class="muted">· {{ exam.termName }}</text>
        </view>
        <view v-if="exam.subjects && exam.subjects.length" class="subjects muted">
          {{ exam.subjects.map((s) => s.subjectName).join(' / ') }}
        </view>
      </view>

      <view v-if="loading" class="loading">加载中…</view>
      <view v-else-if="exams.length > 0 && finished" class="loading">— 没有更多了 —</view>
    </template>
  </view>
</template>

<style scoped lang="scss">
.exam {
  padding: 28rpx;
}

.exam-name {
  font-size: 32rpx;
  font-weight: 600;
}

.meta {
  margin-top: 14rpx;
  gap: 12rpx;
}

.subjects {
  margin-top: 12rpx;
  font-size: 24rpx;
}
</style>
