<template>
  <div class="parent-children">
    <el-card>
      <template #header>
        <div class="card-header">
          <span class="title">我的孩子</span>
          <el-button link type="primary" :icon="Refresh" :loading="loading" @click="loadChildren">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="children" border stripe>
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="studentNo" label="学号" width="150" />
        <el-table-column prop="gradeName" label="年级" width="120">
          <template #default="{ row }">{{ row.gradeName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="className" label="班级" width="150">
          <template #default="{ row }">{{ row.className || '未分班' }}</template>
        </el-table-column>
        <el-table-column prop="relationshipName" label="关系" width="100">
          <template #default="{ row }">{{ row.relationshipName || row.relationship || '-' }}</template>
        </el-table-column>
        <!-- ponytail: 三个入口都带 studentId（student.id）跳转/下载；后端按 canViewStudent 复核，越权会被拒 -->
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="goScores(row as ChildBrief)">查看成绩</el-button>
            <el-button link type="primary" @click="goTrend(row as ChildBrief)">成绩趋势</el-button>
            <el-button link type="success" @click="openReportDialog(row as ChildBrief)">下载成绩单</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="尚未绑定孩子，请联系学校管理员在「用户管理」中绑定" />
        </template>
      </el-table>
    </el-card>

    <!-- 成绩单 PDF 需要指定考试：只列已发布（status=3）的考试 -->
    <el-dialog
      v-model="reportVisible"
      :title="`下载成绩单 - ${reportChild?.name ?? ''}`"
      width="440px"
    >
      <el-form label-width="80px">
        <el-form-item label="考试">
          <el-select
            v-model="reportExamId"
            placeholder="请选择已发布的考试"
            style="width: 100%"
            :loading="examsLoading"
            filterable
          >
            <el-option v-for="e in exams" :key="e.id" :label="e.examName" :value="e.id!" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="downloading" :disabled="!reportExamId" @click="downloadReport">
          下载
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { getMyChildren, type ChildBrief } from '@/api/parent'
import { getExamList, type Exam } from '@/api/exam'
import { downloadScoreReportPdf } from '@/api/export'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'

const router = useRouter()

const loading = ref(false)
const children = ref<ChildBrief[]>([])

const loadChildren = async () => {
  loading.value = true
  try {
    children.value = await getMyChildren()
  } catch (error) {
    handleError(error, '加载孩子列表失败')
  } finally {
    loading.value = false
  }
}

const goScores = (child: ChildBrief) => {
  router.push({ path: '/score/query', query: { studentId: String(child.studentId) } })
}

const goTrend = (child: ChildBrief) => {
  router.push({ path: '/stat/student-trend', query: { studentId: String(child.studentId) } })
}

// ---------------------------------------------------------------- 成绩单

const reportVisible = ref(false)
const reportChild = ref<ChildBrief | null>(null)
const reportExamId = ref<number>()
const exams = ref<Exam[]>([])
const examsLoading = ref(false)
const downloading = ref(false)

const loadPublishedExams = async () => {
  examsLoading.value = true
  try {
    // ponytail: /pdf/score-report 只接受已发布考试，下拉直接按 status=3 过滤，避免选了再被后端拒
    const res = (await getExamList({ status: 3, pageNum: 1, pageSize: 200 })) as PageResponse<Exam>
    exams.value = res?.records ?? []
  } catch (error) {
    handleError(error, '加载考试列表失败')
  } finally {
    examsLoading.value = false
  }
}

const openReportDialog = async (child: ChildBrief) => {
  reportChild.value = child
  reportExamId.value = undefined
  reportVisible.value = true
  if (exams.value.length === 0) {
    await loadPublishedExams()
  }
}

const downloadReport = async () => {
  if (!reportChild.value || !reportExamId.value) return
  downloading.value = true
  try {
    await downloadScoreReportPdf(reportExamId.value, reportChild.value.studentId, reportChild.value.studentNo)
    reportVisible.value = false
  } catch {
    // 后端业务错误（成绩未发布 / 越权）已在 api/export.ts 的 ensureBinary 中提示
  } finally {
    downloading.value = false
  }
}

onMounted(() => {
  loadChildren()
})
</script>

<style scoped lang="scss">
.parent-children {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .title {
      font-size: 18px;
      font-weight: bold;
    }
  }
}
</style>
