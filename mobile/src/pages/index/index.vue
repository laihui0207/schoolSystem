<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import ChildSwitcher from '@/components/ChildSwitcher.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getStudentTrend } from '@/api/stat'
import { useCurrentStudent } from '@/composables/useCurrentStudent'
import { useUnreadBadge } from '@/composables/useUnreadBadge'
import { userStore } from '@/store/user'
import type { ExamTrend } from '@/types'
import { USER_TYPE_PARENT, USER_TYPE_STUDENT } from '@/types'
import { fmtDate, fmtRank, fmtScore } from '@/utils/format'
import { PAGES, errorMessage, toLogin, toScoreDetail, toTrend } from '@/utils/navigate'

const { studentId, emptyHint } = useCurrentStudent()
const { unreadCount, refreshUnreadBadge } = useUnreadBadge()

const latest = ref<ExamTrend | null>(null)
const examCount = ref(0)
const loading = ref(false)
const error = ref('')

const displayName = computed(() => {
  const info = userStore.userInfo.value
  return info?.realName || info?.username || ''
})

const roleLabel = computed(() => {
  const type = userStore.userInfo.value?.userType
  if (type === USER_TYPE_STUDENT) return '学生'
  if (type === USER_TYPE_PARENT) return '家长'
  return '访客'
})

/** 最近一场：按 examDate 倒序取首个；无日期的排后 */
const pickLatest = (trends: ExamTrend[]): ExamTrend | null => {
  const sorted = [...trends].sort((a, b) => (b.examDate ?? '').localeCompare(a.examDate ?? ''))
  return sorted[0] ?? null
}

const load = async (): Promise<void> => {
  if (!userStore.isLoggedIn.value) {
    toLogin()
    return
  }
  void refreshUnreadBadge()
  const id = studentId.value
  if (id === null) {
    latest.value = null
    examCount.value = 0
    return
  }
  loading.value = true
  error.value = ''
  try {
    const trend = await getStudentTrend(id)
    const trends = trend.examTrends ?? []
    latest.value = pickLatest(trends)
    examCount.value = trends.length
  } catch (err) {
    latest.value = null
    examCount.value = 0
    error.value = errorMessage(err, '加载失败')
  } finally {
    loading.value = false
  }
}

const goNotifications = (): void => {
  uni.switchTab({ url: PAGES.notification })
}

const goScores = (): void => {
  uni.switchTab({ url: PAGES.scoreList })
}

onShow(() => {
  void load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="page">
    <view class="greeting">
      <view>
        <text class="hello">你好，{{ displayName }}</text>
        <text class="tag role">{{ roleLabel }}</text>
      </view>
      <text class="muted">查看最近成绩与通知</text>
    </view>

    <ChildSwitcher @change="load" />

    <EmptyState v-if="emptyHint" :text="emptyHint" />

    <template v-else>
      <view class="card latest" @tap="latest && toScoreDetail(latest.examId)">
        <view class="card-title">
          <text>最近一次考试</text>
          <text v-if="latest" class="card-link">查看详情 ›</text>
        </view>
        <view v-if="loading" class="loading">加载中…</view>
        <view v-else-if="error" class="muted">{{ error }}</view>
        <view v-else-if="!latest" class="muted">暂无已发布的考试成绩</view>
        <view v-else>
          <view class="exam-name">{{ latest.examName }}</view>
          <view class="muted">{{ fmtDate(latest.examDate) }}</view>
          <view class="score-row">
            <view class="score-main">
              <text class="score-value">{{ fmtScore(latest.totalScore) }}</text>
              <text class="score-label">总分</text>
            </view>
            <view class="rank">
              <view class="rank-item">
                <text class="rank-value">{{ fmtRank(latest.rankInClass) }}</text>
                <text class="score-label">班级排名</text>
              </view>
              <view class="rank-item">
                <text class="rank-value">{{ fmtRank(latest.rankInGrade) }}</text>
                <text class="score-label">年级排名</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <view class="grid">
        <view class="card grid-item" @tap="goNotifications">
          <text class="grid-value">{{ unreadCount }}</text>
          <text class="score-label">未读通知</text>
        </view>
        <view class="card grid-item" @tap="goScores">
          <text class="grid-value">{{ examCount }}</text>
          <text class="score-label">已发布考试</text>
        </view>
      </view>

      <view class="card">
        <view class="card-title">快捷入口</view>
        <view class="entries">
          <view class="entry" @tap="toTrend">
            <text class="entry-title">成绩趋势</text>
            <text class="muted">历次总分 · 各科雷达</text>
          </view>
          <view class="entry" @tap="goScores">
            <text class="entry-title">全部考试</text>
            <text class="muted">按考试查看各科成绩</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped lang="scss">
.greeting {
  padding: 12rpx 4rpx 28rpx;
}

.hello {
  font-size: 40rpx;
  font-weight: 700;
  margin-right: 16rpx;
}

.role {
  vertical-align: middle;
}

.latest {
  background: linear-gradient(135deg, #2f6fed, #5b8dff);
  color: #fff;

  .card-title,
  .card-link,
  .muted,
  .score-label {
    color: rgba(255, 255, 255, 0.85);
  }
}

.exam-name {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 6rpx;
}

.score-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: 28rpx;
}

.score-main {
  display: flex;
  flex-direction: column;
}

.score-value {
  font-size: 76rpx;
  font-weight: 700;
  line-height: 1;
}

.score-label {
  font-size: 24rpx;
  margin-top: 8rpx;
}

.rank {
  display: flex;
  gap: 36rpx;
}

.rank-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.rank-value {
  font-size: 32rpx;
  font-weight: 600;
}

.grid {
  display: flex;
  gap: 24rpx;

  .grid-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 32rpx 0;
  }
}

.grid-value {
  font-size: 48rpx;
  font-weight: 700;
  color: #2f6fed;
  line-height: 1;
}

.entries {
  display: flex;
  gap: 24rpx;
}

.entry {
  flex: 1;
  background: #f5f6f8;
  border-radius: 16rpx;
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.entry-title {
  font-size: 30rpx;
  font-weight: 600;
}
</style>
