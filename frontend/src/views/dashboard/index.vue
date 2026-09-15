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
            <el-table-column label="操作">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewExam(row)">
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { School, UserFilled, Reading, Document, EditPen, DataAnalysis, User } from '@element-plus/icons-vue'
import { getSchoolList } from '@/api/school'
import { getStudentList } from '@/api/student'
import { getTeacherList } from '@/api/teacher'

const router = useRouter()

const stats = ref({
  schoolCount: 0,
  studentCount: 0,
  examCount: 0,
  scoreCount: 0
})

const recentExams = ref<any[]>([])

const goTo = (path: string) => {
  router.push(path)
}

const getStatusType = (status: number) => {
  const types: any = {
    0: 'info',
    1: 'warning',
    2: '',
    3: 'success'
  }
  return types[status] || 'info'
}

const getStatusText = (status: number) => {
  const texts: any = {
    0: '未开始',
    1: '录入中',
    2: '审核中',
    3: '已发布'
  }
  return texts[status] || '未知'
}

const viewExam = (exam: any) => {
  router.push(`/exam/${exam.id}`)
}

onMounted(async () => {
  const results = await Promise.allSettled([
    getSchoolList({ pageNum: 1, pageSize: 1 }),
    getStudentList({ pageNum: 1, pageSize: 1 }),
    getTeacherList({ pageNum: 1, pageSize: 1 })
  ])
  if (results[0].status === 'fulfilled') stats.value.schoolCount = (results[0].value as any)?.total ?? 0
  if (results[1].status === 'fulfilled') stats.value.studentCount = (results[1].value as any)?.total ?? 0
  if (results[2].status === 'fulfilled') stats.value.examCount = (results[2].value as any)?.total ?? 0
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
