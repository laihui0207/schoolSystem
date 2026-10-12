<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ChildSwitcher from '@/components/ChildSwitcher.vue'
import { logout as logoutApi } from '@/api/auth'
import { listExams } from '@/api/exam'
import { downloadScoreReport } from '@/api/pdf'
import { useChildren } from '@/composables/useChildren'
import { useCurrentStudent } from '@/composables/useCurrentStudent'
import { userStore } from '@/store/user'
import type { ExamResponse } from '@/types'
import { USER_TYPE_PARENT, USER_TYPE_STUDENT } from '@/types'
import { fmtDate } from '@/utils/format'
import { errorMessage, toLogin, toTrend, toast } from '@/utils/navigate'
import { API_BASE } from '@/utils/request'

/** 下载成绩单时可选的最近考试数量（uni.showActionSheet 项数上限 6） */
const RECENT_EXAM_LIMIT = 6

const { studentId, emptyHint } = useCurrentStudent()
const { options, loadAll, classLabel } = useChildren()

const info = userStore.userInfo
const downloading = ref(false)

const displayName = computed(() => info.value?.realName || info.value?.username || '未登录')
const avatarChar = computed(() => displayName.value.slice(0, 1))
const roleLabel = computed(() => {
  const type = info.value?.userType
  if (type === USER_TYPE_STUDENT) return '学生'
  if (type === USER_TYPE_PARENT) return '家长'
  return info.value?.role ?? '其他'
})

const current = computed(() => options.value.find((o) => o.studentId === studentId.value) ?? null)
const currentStudent = computed(() => current.value?.student ?? null)

const pickExamAndDownload = async (): Promise<void> => {
  const sid = studentId.value
  if (sid === null || downloading.value) return
  downloading.value = true
  try {
    const page = await listExams({ schoolId: info.value?.schoolId, pageNum: 1, pageSize: RECENT_EXAM_LIMIT })
    const exams: ExamResponse[] = page.records
    if (exams.length === 0) {
      toast('暂无已发布的考试')
      return
    }
    const chosen = await new Promise<ExamResponse | null>((resolve) => {
      uni.showActionSheet({
        title: '选择考试',
        itemList: exams.map((e) => `${e.examName} ${fmtDate(e.examDate)}`.trim()),
        success: (res) => resolve(exams[res.tapIndex] ?? null),
        fail: () => resolve(null)
      })
    })
    if (!chosen) return
    await downloadScoreReport(chosen.id, sid)
    toast('成绩单已生成', 'success')
  } catch (err) {
    toast(errorMessage(err, '下载失败'))
  } finally {
    downloading.value = false
  }
}

const confirmLogout = (): void => {
  uni.showModal({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    success: async (res) => {
      if (!res.confirm) return
      // ponytail: 先调后端 logout 让 Redis 的 token:<jwt> 失效，失败也照样清本地会话——用户意图是"退出"
      try {
        await logoutApi()
      } catch {
        // 忽略
      }
      userStore.clearSession()
      toLogin()
    }
  })
}

onShow(() => {
  if (!userStore.isLoggedIn.value) {
    toLogin()
    return
  }
  void loadAll()
})
</script>

<template>
  <view class="page">
    <view class="card profile">
      <view class="avatar">{{ avatarChar }}</view>
      <view class="profile-body">
        <view class="row">
          <text class="name">{{ displayName }}</text>
          <text class="tag">{{ roleLabel }}</text>
        </view>
        <text class="muted">账号 {{ info?.username }}</text>
      </view>
    </view>

    <ChildSwitcher />

    <view v-if="emptyHint" class="card muted">{{ emptyHint }}</view>

    <view v-else class="card">
      <view class="card-title">{{ userStore.isParent.value ? '当前查看的孩子' : '学生档案' }}</view>
      <view class="list-item">
        <text class="muted">姓名</text>
        <text>{{ currentStudent?.name ?? current?.label ?? '—' }}</text>
      </view>
      <view class="list-item">
        <text class="muted">学号</text>
        <text>{{ currentStudent?.studentNo ?? '—' }}</text>
      </view>
      <view class="list-item">
        <text class="muted">班级</text>
        <text>{{ classLabel(currentStudent) || '—' }}</text>
      </view>
      <view class="list-item">
        <text class="muted">学校</text>
        <text>{{ currentStudent?.schoolName ?? '—' }}</text>
      </view>
    </view>

    <view v-if="!emptyHint" class="card">
      <view class="list-item action" @tap="toTrend">
        <text>成绩趋势</text>
        <text class="muted">›</text>
      </view>
      <view class="list-item action" @tap="pickExamAndDownload">
        <text>下载成绩单 PDF</text>
        <text class="muted">{{ downloading ? '处理中…' : '›' }}</text>
      </view>
    </view>

    <button class="btn btn-danger" @tap="confirmLogout">退出登录</button>

    <view class="muted footer">服务地址 {{ API_BASE }}</view>
  </view>
</template>

<style scoped lang="scss">
.profile {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f6fed, #5b8dff);
  color: #fff;
  font-size: 48rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.profile-body {
  display: flex;
  flex-direction: column;
  gap: 8rpx;

  .row {
    gap: 14rpx;
  }
}

.name {
  font-size: 36rpx;
  font-weight: 700;
}

.action {
  font-size: 30rpx;
}

.footer {
  margin-top: 40rpx;
  text-align: center;
  font-size: 22rpx;
  word-break: break-all;
}
</style>
