<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { listNotifications, markAllAsRead, markAsRead } from '@/api/notification'
import { useUnreadBadge } from '@/composables/useUnreadBadge'
import { userStore } from '@/store/user'
import type { NotificationResponse } from '@/types'
import { fmtDate } from '@/utils/format'
import { errorMessage, toLogin, toScoreDetail, toast } from '@/utils/navigate'

type Filter = 'all' | 'unread'

const PAGE_SIZE = 20
const TYPE_SCORE_PUBLISHED = 1
const TYPE_SCORE_WARNING = 2

const { unreadCount, refreshUnreadBadge, setUnreadCount } = useUnreadBadge()

const filter = ref<Filter>('all')
const list = ref<NotificationResponse[]>([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')

const finished = computed(() => list.value.length >= total.value)

const typeClass = (type: number | undefined): string => {
  if (type === TYPE_SCORE_PUBLISHED) return 'tag'
  if (type === TYPE_SCORE_WARNING) return 'tag tag-orange'
  return 'tag tag-gray'
}

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
    const page = await listNotifications({
      isRead: filter.value === 'unread' ? 0 : undefined,
      pageNum: target,
      pageSize: PAGE_SIZE
    })
    list.value = reset ? page.records : [...list.value, ...page.records]
    total.value = page.total
    pageNum.value = target + 1
  } catch (err) {
    error.value = errorMessage(err, '加载失败')
  } finally {
    loading.value = false
  }
}

const switchFilter = (next: Filter): void => {
  if (filter.value === next) return
  filter.value = next
  void load(true)
}

const open = async (item: NotificationResponse): Promise<void> => {
  if (item.isRead === 0) {
    try {
      await markAsRead(item.id)
      list.value = list.value.map((n) => (n.id === item.id ? { ...n, isRead: 1 } : n))
      setUnreadCount(unreadCount.value - 1)
    } catch {
      // 标记失败不影响阅读
    }
  }
  // ponytail: 成绩发布通知的 relatedId 是 examId，可直接跳成绩详情；其他类型只展示正文
  const canJump = item.type === TYPE_SCORE_PUBLISHED && typeof item.relatedId === 'number'
  uni.showModal({
    title: item.title ?? '通知',
    content: item.content ?? '',
    showCancel: canJump,
    cancelText: '关闭',
    confirmText: canJump ? '查看成绩' : '知道了',
    success: (res) => {
      if (canJump && res.confirm && typeof item.relatedId === 'number') toScoreDetail(item.relatedId)
    }
  })
}

const readAll = async (): Promise<void> => {
  if (unreadCount.value === 0) return
  try {
    await markAllAsRead()
    list.value = list.value.map((n) => ({ ...n, isRead: 1 }))
    setUnreadCount(0)
    toast('已全部标记为已读', 'success')
    if (filter.value === 'unread') void load(true)
  } catch (err) {
    toast(errorMessage(err, '操作失败'))
  }
}

onShow(() => {
  void refreshUnreadBadge()
  void load(true)
})

onPullDownRefresh(async () => {
  await Promise.all([refreshUnreadBadge(), load(true)])
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (!finished.value) void load(false)
})
</script>

<template>
  <view class="page">
    <view class="toolbar">
      <view class="chip-row">
        <view class="chip" :class="{ active: filter === 'all' }" @tap="switchFilter('all')">全部</view>
        <view class="chip" :class="{ active: filter === 'unread' }" @tap="switchFilter('unread')">
          未读<text v-if="unreadCount > 0"> {{ unreadCount }}</text>
        </view>
      </view>
      <text class="card-link" :class="{ disabled: unreadCount === 0 }" @tap="readAll">全部已读</text>
    </view>

    <view v-if="error" class="card muted">{{ error }}</view>
    <EmptyState v-else-if="!loading && list.length === 0" :text="filter === 'unread' ? '没有未读消息' : '暂无消息'" />

    <view v-else class="card">
      <view v-for="item in list" :key="item.id" class="list-item notice" @tap="open(item)">
        <view class="dot" :class="{ unread: item.isRead === 0 }" />
        <view class="body">
          <view class="row-between">
            <text class="title" :class="{ read: item.isRead === 1 }">{{ item.title }}</text>
            <text class="muted time">{{ fmtDate(item.createdAt, true) }}</text>
          </view>
          <view class="row meta">
            <text :class="typeClass(item.type)">{{ item.typeName || '通知' }}</text>
            <text class="muted preview">{{ item.content }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="loading" class="loading">加载中…</view>
    <view v-else-if="list.length > 0 && finished" class="loading">— 没有更多了 —</view>
  </view>
</template>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
  padding: 0 4rpx;

  .disabled {
    color: #b8bcc5;
  }
}

.notice {
  align-items: flex-start;
  gap: 16rpx;
}

.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  margin-top: 16rpx;
  background: transparent;
  flex-shrink: 0;

  &.unread {
    background: #e5484d;
  }
}

.body {
  flex: 1;
  min-width: 0;
}

.title {
  font-size: 30rpx;
  font-weight: 600;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.read {
    font-weight: 400;
    color: #4b5160;
  }
}

.time {
  font-size: 22rpx;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.meta {
  margin-top: 10rpx;
  gap: 12rpx;
  min-width: 0;
}

.preview {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 24rpx;
}
</style>
