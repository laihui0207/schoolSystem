<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <!-- 统计卡片 -->
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <el-icon class="icon" color="#409eff"><School /></el-icon>
            <div class="content">
              <div class="value">{{ stats.schoolCount }}</div>
              <div class="label">学校数量</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <el-icon class="icon" color="#67c23a"><UserFilled /></el-icon>
            <div class="content">
              <div class="value">{{ stats.studentCount }}</div>
              <div class="label">学生总数</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <el-icon class="icon" color="#e6a23c"><Reading /></el-icon>
            <div class="content">
              <div class="value">{{ stats.examCount }}</div>
              <div class="label">考试场次</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-item">
            <el-icon class="icon" color="#f56c6c"><Document /></el-icon>
            <div class="content">
              <div class="value">{{ stats.scoreCount }}</div>
              <div class="label">成绩记录</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <span>快捷入口</span>
          </template>
          <div class="quick-links">
            <el-button type="primary" @click="goTo('/exam')">
              <el-icon><Reading /></el-icon>
              创建考试
            </el-button>
            <el-button type="success" @click="goTo('/score')">
              <el-icon><EditPen /></el-icon>
              录入成绩
            </el-button>
            <el-button type="warning" @click="goTo('/stat')">
              <el-icon><DataAnalysis /></el-icon>
              查看统计
            </el-button>
            <el-button type="info" @click="goTo('/student')">
              <el-icon><User /></el-icon>
              学生管理
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近考试 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <span>最近考试</span>
          </template>
          <el-empty v-if="recentExams.length === 0" description="暂无考试数据" />
          <el-table v-else :data="recentExams" stripe>
            <el-table-column prop="examName" label="考试名称" />
            <el-table-column prop="examDate" label="考试日期" />
            <el-table-column prop="examType" label="考试类型" />
            <el-table-column prop="status" label="状态">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <!-- ponytail: F-11 — exam/detail 路由 allowedTypes 为 [1,2,3]，学生点「查看」会被守卫静默弹回首页，直接不渲染该列 -->
            <el-table-column v-if="!isStudent" label="操作">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewExam(row as Exam)">
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { School, UserFilled, Reading, Document, EditPen, DataAnalysis, User } from '@element-plus/icons-vue'
import { getSchoolList } from '@/api/school'
import { getStudentList } from '@/api/student'
import { getExamList, type Exam } from '@/api/exam'
import { getScoreList } from '@/api/score'
import type { PageResponse } from '@/types/common'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
// userType: 4 学生 5 家长，两者都没有考试详情页权限
const isStudent = computed(() => [4, 5].includes(userStore.userInfo?.userType ?? 0))

const stats = ref({
  schoolCount: 0,
  studentCount: 0,
  examCount: 0,
  scoreCount: 0
})

const recentExams = ref<Exam[]>([])

const goTo = (path: string) => {
  router.push(path)
}

type StatusType = 'success' | 'warning' | 'danger' | 'info'

const getStatusType = (status: number): StatusType => {
  const types: Record<number, StatusType> = {
    0: 'info',
    1: 'warning',
    2: 'info',
    3: 'success'
  }
  return types[status] || 'info'
}

const getStatusText = (status: number): string => {
  const texts: Record<number, string> = {
    0: '未开始',
    1: '录入中',
    2: '审核中',
    3: '已发布'
  }
  return texts[status] || '未知'
}

const viewExam = (exam: Exam) => {
  // ponytail: 考试详情路由是 /exam/detail/:id，原 /exam/:id 会被兜底路由重定向回首页
  router.push(`/exam/detail/${exam.id}`)
}

onMounted(async () => {
  // ponytail: 「考试场次」原来误取 /teachers 的 total；改取 /exams 的 total 并把前 5 条按日期倒序作「最近考试」；「成绩记录」取 /scores 的 total
  const results = await Promise.allSettled([
    getSchoolList({ pageNum: 1, pageSize: 1 }),
    getStudentList({ pageNum: 1, pageSize: 1 }),
    getExamList({ pageNum: 1, pageSize: 5 }) as Promise<PageResponse<Exam>>,
    getScoreList({ pageNum: 1, pageSize: 1 })
  ])
  if (results[0].status === 'fulfilled') stats.value.schoolCount = (results[0].value as any)?.total ?? 0
  if (results[1].status === 'fulfilled') stats.value.studentCount = (results[1].value as any)?.total ?? 0
  if (results[2].status === 'fulfilled') {
    const examPage = results[2].value
    stats.value.examCount = examPage?.total ?? 0
    recentExams.value = [...(examPage?.records ?? [])].sort((a, b) =>
      (b.examDate ?? '').localeCompare(a.examDate ?? '')
    )
  }
  if (results[3].status === 'fulfilled') stats.value.scoreCount = (results[3].value as any)?.total ?? 0
})
</script>

<style scoped lang="scss">
.dashboard {
  .stat-card {
    .stat-item {
      display: flex;
      align-items: center;
      gap: 20px;

      .icon {
        font-size: 48px;
      }

      .content {
        .value {
          font-size: 28px;
          font-weight: bold;
          color: #303133;
        }

        .label {
          font-size: 14px;
          color: #909399;
          margin-top: 5px;
        }
      }
    }
  }

  .quick-links {
    display: flex;
    gap: 15px;
  }
}
</style>
