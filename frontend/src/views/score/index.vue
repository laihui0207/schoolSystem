<template>
  <div class="score-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>成绩管理</span>
        </div>
      </template>

      <div class="management-cards">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-card class="action-card" shadow="hover" @click="handleGoToInput">
              <div class="card-icon">
                <el-icon :size="48" color="#409eff"><Edit /></el-icon>
              </div>
              <div class="card-title">成绩录入</div>
              <div class="card-desc">录入和管理学生考试成绩</div>
            </el-card>
          </el-col>

          <el-col :span="6">
            <el-card class="action-card" shadow="hover" @click="handleGoToReview">
              <div class="card-icon">
                <el-icon :size="48" color="#f56c6c"><DocumentChecked /></el-icon>
              </div>
              <div class="card-title">成绩审核</div>
              <div class="card-desc">审核已录入的成绩数据</div>
            </el-card>
          </el-col>

          <el-col :span="6">
            <el-card class="action-card" shadow="hover" @click="handleGoToQuery">
              <div class="card-icon">
                <el-icon :size="48" color="#67c23a"><Search /></el-icon>
              </div>
              <div class="card-title">成绩查询</div>
              <div class="card-desc">查询学生各科成绩信息</div>
            </el-card>
          </el-col>

          <el-col :span="6">
            <el-card class="action-card" shadow="hover" @click="handleGoToAnalysis">
              <div class="card-icon">
                <el-icon :size="48" color="#e6a23c"><DataAnalysis /></el-icon>
              </div>
              <div class="card-title">成绩分析</div>
              <div class="card-desc">统计分析成绩数据</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="action-card" shadow="hover" @click="handleGoToScoreChange">
              <div class="card-icon">
                <el-icon :size="48" color="#9c27b0"><EditPen /></el-icon>
              </div>
              <div class="card-title">成绩变更</div>
              <div class="card-desc">申请或审批成绩修改</div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <!-- 快速入口 -->
      <div class="recent-section">
        <h3>快速入口</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="成绩录入">
            <el-link type="primary" @click="handleGoToInput">进入成绩录入</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="成绩审核">
            <el-link type="danger" @click="handleGoToReview">进入成绩审核</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="成绩查询">
            <el-link type="success" @click="handleGoToQuery">查询成绩</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="Excel导入">
            <el-link type="warning" @click="handleGoToInput">批量导入成绩</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="统计分析">
            <el-link type="info" @click="handleGoToAnalysis">查看统计报表</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="排名计算">
            <el-link type="primary" @click="handleGoToExam">发布考试后自动计算排名</el-link>
          </el-descriptions-item>
          <el-descriptions-item label="成绩变更">
            <el-link type="primary" @click="handleGoToScoreChange">申请或审批变更</el-link>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { Edit, Search, DataAnalysis, DocumentChecked, EditPen } from '@element-plus/icons-vue'

const router = useRouter()

const handleGoToInput = () => {
  router.push('/score/input')
}

const handleGoToScoreChange = () => {
  router.push('/score/score-change')
}

const handleGoToReview = () => {
  router.push('/score/review-list')
}

const handleGoToQuery = () => {
  router.push('/score/query')
}

const handleGoToAnalysis = () => {
  router.push('/score/analysis')
}

// ponytail: 排名在「发布考试」时由后端统一计算，没有独立的排名页，这里导去考试管理
const handleGoToExam = () => {
  router.push('/exam')
}
</script>

<style scoped lang="scss">
.score-management {
  .card-header {
    font-size: 18px;
    font-weight: bold;
  }

  .management-cards {
    margin-bottom: 30px;

    .action-card {
      cursor: pointer;
      text-align: center;
      padding: 30px 20px;
      transition: all 0.3s;

      &:hover {
        transform: translateY(-5px);
      }

      .card-icon {
        margin-bottom: 15px;
      }

      .card-title {
        font-size: 18px;
        font-weight: bold;
        margin-bottom: 10px;
        color: #303133;
      }

      .card-desc {
        font-size: 14px;
        color: #909399;
      }
    }
  }

  .recent-section {
    h3 {
      font-size: 16px;
      font-weight: bold;
      margin-bottom: 15px;
      color: #303133;
    }
  }
}
</style>
