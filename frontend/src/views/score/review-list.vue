<template>
  <div class="review-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>成绩审核列表</span>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :model="queryForm" label-width="100px" class="filter-form">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="考试">
              <el-select
                v-model="queryForm.examId"
                placeholder="请选择考试"
                clearable
                style="width: 100%"
                @change="handleExamChange"
              >
                <el-option
                  v-for="item in examList"
                  :key="item.id"
                  :label="item.examName"
                  :value="item.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="班级">
              <el-select
                v-model="queryForm.classId"
                placeholder="请选择班级"
                clearable
                style="width: 100%"
                :disabled="!queryForm.examId"
              >
                <el-option
                  v-for="item in classList"
                  :key="item.id"
                  :label="item.className"
                  :value="item.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="科目">
              <el-select
                v-model="queryForm.subjectId"
                placeholder="请选择科目"
                clearable
                style="width: 100%"
                :disabled="!queryForm.examId"
              >
                <el-option
                  v-for="item in subjectList"
                  :key="item.subjectId"
                  :label="item.subjectName"
                  :value="item.subjectId"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="审核状态">
              <el-select
                v-model="queryForm.status"
                placeholder="请选择状态"
                clearable
                style="width: 100%"
              >
                <el-option label="全部" value="" />
                <el-option label="待审核" value="pending" />
                <el-option label="已通过" value="approved" />
                <el-option label="已驳回" value="rejected" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- ponytail: F-04 — 后端 review-list 不支持时间范围筛选，去掉无效的日期控件 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label=" " label-width="20px">
              <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
              <el-button :icon="Refresh" @click="handleReset">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 批量操作区 -->
      <div class="batch-actions">
        <el-button
          type="success"
          :icon="Select"
          :disabled="selectedItems.length === 0"
          @click="handleBatchApprove"
        >
          批量通过
        </el-button>
        <el-button
          type="danger"
          :icon="CloseBold"
          :disabled="selectedItems.length === 0"
          @click="handleBatchReject"
        >
          批量驳回
        </el-button>
        <span v-if="selectedItems.length > 0" class="selected-count">
          已选择 {{ selectedItems.length }} 项
        </span>
      </div>

      <!-- 列表表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" :selectable="isSelectable" />
        <el-table-column prop="examName" label="考试名称" min-width="150" />
        <el-table-column prop="className" label="班级名称" width="120" />
        <el-table-column prop="subjectName" label="科目名称" width="100" />
        <el-table-column prop="inputPerson" label="录入人" width="100" />
        <el-table-column prop="inputTime" label="录入时间" width="160" />
        <el-table-column label="审核状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewPerson" label="审核人" width="100" />
        <el-table-column prop="reviewTime" label="审核时间" width="160" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewDetail(row)">
              查看详情
            </el-button>
            <el-button
              v-if="row.status === 'pending'"
              link
              type="success"
              @click="handleReview(row)"
            >
              审核
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 批量驳回对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="批量驳回" width="500px">
      <el-form :model="rejectForm" label-width="100px">
        <el-form-item label="驳回原因" required>
          <el-input
            v-model="rejectForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请输入驳回原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmBatchReject">确定驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Select, CloseBold } from '@element-plus/icons-vue'
import {
  getReviewList,
  batchApprove,
  batchReject,
  type ReviewListItem,
  type ReviewQuery,
  type BatchReviewResult
} from '@/api/score'
import { getExamList, type Exam, type ExamSubject } from '@/api/exam'
import { getClassesByGrade, type Class } from '@/api/class'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'

const router = useRouter()

const loading = ref(false)
const tableData = ref<ReviewListItem[]>([])
const examList = ref<Exam[]>([])
const classList = ref<Class[]>([])
const subjectList = ref<ExamSubject[]>([])
const selectedItems = ref<ReviewListItem[]>([])
const rejectDialogVisible = ref(false)

const defaultQueryForm: ReviewQuery = {
  examId: undefined,
  classId: undefined,
  subjectId: undefined,
  status: undefined,
  pageNum: 1,
  pageSize: 20
}

const queryForm = reactive<ReviewQuery>({ ...defaultQueryForm })

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const rejectForm = reactive({
  reason: ''
})

const getStatusType = (status: string) => {
  const typeMap: Record<string, 'warning' | 'success' | 'danger'> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'danger'
  }
  return typeMap[status] || 'info'
}

const getStatusText = (status: string) => {
  const textMap: Record<string, string> = {
    pending: '待审核',
    approved: '已通过',
    rejected: '已驳回'
  }
  return textMap[status] || status
}

const isSelectable = (row: ReviewListItem) => {
  return row.status === 'pending'
}

const loadExamList = async () => {
  try {
    const res = await getExamList({ pageNum: 1, pageSize: 100 }) as PageResponse<Exam>
    examList.value = res.records || []
  } catch (error) {
    handleError(error, '加载考试列表失败')
  }
}

const handleExamChange = async () => {
  queryForm.classId = undefined
  queryForm.subjectId = undefined
  classList.value = []
  subjectList.value = []

  const exam = examList.value.find(e => e.id === queryForm.examId)
  if (!exam) return

  subjectList.value = exam.subjects || []

  if (exam.gradeIds && exam.gradeIds.length > 0) {
    try {
      const allClasses: Class[] = []
      for (const gradeId of exam.gradeIds) {
        const classes = await getClassesByGrade(gradeId) as Class[]
        allClasses.push(...classes)
      }
      classList.value = allClasses
    } catch (error) {
      handleError(error, '加载班级列表失败')
    }
  }
}

const loadList = async () => {
  loading.value = true
  try {
    const params: ReviewQuery = {
      ...queryForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }

    const res = await getReviewList(params) as PageResponse<ReviewListItem>
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    handleError(error, '加载审核列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadList()
}

const handleReset = () => {
  Object.assign(queryForm, { ...defaultQueryForm })
  pagination.pageNum = 1
  loadList()
}

const handleSizeChange = () => {
  loadList()
}

const handleCurrentChange = () => {
  loadList()
}

const handleSelectionChange = (selection: ReviewListItem[]) => {
  selectedItems.value = selection
}

const handleViewDetail = (row: ReviewListItem) => {
  router.push({
    name: 'ScoreReviewDetail',
    query: {
      examId: row.examId,
      classId: row.classId,
      subjectId: row.subjectId
    }
  })
}

const handleReview = (row: ReviewListItem) => {
  router.push({
    name: 'ScoreReviewDetail',
    query: {
      examId: row.examId,
      classId: row.classId,
      subjectId: row.subjectId
    }
  })
}

/**
 * 把失败三元组换成可读文案：考试名 / 班级名 / 科目名
 */
const describeTriplet = (examId: number, classId: number, subjectId: number) => {
  const row = tableData.value.find(r => r.examId === examId && r.classId === classId && r.subjectId === subjectId)
  return row ? `${row.examName} / ${row.className} / ${row.subjectName}` : `考试#${examId} / 班级#${classId} / 科目#${subjectId}`
}

// ponytail: F-07 — 逐项汇总成功/失败数并列出失败三元组；无论结果如何都刷新列表，已通过项不再停留在「待审核」
const notifyBatchReviewResult = (result: BatchReviewResult, action: string) => {
  if (result.failCount === 0) {
    ElMessage.success(`${action}成功，共 ${result.successCount} 项`)
    return
  }
  ElMessageBox.alert(
    h('div', [
      h('p', `成功 ${result.successCount} 项，失败 ${result.failCount} 项：`),
      h(
        'ul',
        { style: 'margin: 8px 0 0; padding-left: 20px; max-height: 240px; overflow: auto' },
        result.failures.map(f =>
          h('li', { key: `${f.examId}-${f.classId}-${f.subjectId}` }, `${describeTriplet(f.examId, f.classId, f.subjectId)}：${f.reason}`)
        )
      )
    ]),
    `${action}部分失败`,
    { type: 'warning', confirmButtonText: '知道了' }
  ).catch(() => undefined)
}

const handleBatchApprove = async () => {
  const confirmed = await ElMessageBox.confirm(
    `确认通过选中的 ${selectedItems.value.length} 项成绩审核？`,
    '批量通过确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => true).catch(() => false)
  if (!confirmed) return

  loading.value = true
  try {
    const data = selectedItems.value.map(item => ({
      examId: item.examId,
      classId: item.classId,
      subjectId: item.subjectId
    }))

    const result = await batchApprove(data)
    notifyBatchReviewResult(result, '批量审核通过')
  } finally {
    loading.value = false
    loadList()
  }
}

const handleBatchReject = () => {
  rejectForm.reason = ''
  rejectDialogVisible.value = true
}

const confirmBatchReject = async () => {
  if (!rejectForm.reason.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }

  loading.value = true
  try {
    const items = selectedItems.value.map(item => ({
      examId: item.examId,
      classId: item.classId,
      subjectId: item.subjectId
    }))

    const result = await batchReject(items, rejectForm.reason)
    notifyBatchReviewResult(result, '批量驳回')
    rejectDialogVisible.value = false
  } finally {
    loading.value = false
    loadList()
  }
}

onMounted(() => {
  loadExamList()
  loadList()
})
</script>

<style scoped lang="scss">
.review-list {
  .card-header {
    font-size: 18px;
    font-weight: bold;
  }

  .filter-form {
    margin-bottom: 20px;
  }

  .batch-actions {
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 10px;

    .selected-count {
      color: #409eff;
      font-size: 14px;
    }
  }

  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
