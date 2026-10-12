<template>
  <div class="exam-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="考试名称">
          <el-input
            v-model="searchForm.examName"
            placeholder="请输入考试名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="searchForm.termId" placeholder="全部" clearable style="width: 160px">
            <el-option
              v-for="item in termList"
              :key="item.id"
              :label="termLabel(item)"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学校">
          <el-select v-model="searchForm.schoolId" placeholder="全部" clearable style="width: 160px">
            <el-option
              v-for="item in schoolList"
              :key="item.id"
              :label="item.schoolName"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考试状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 130px">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考试类型">
          <el-select v-model="searchForm.examType" placeholder="全部" clearable style="width: 130px">
            <el-option
              v-for="item in typeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增考试</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="examName" label="考试名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="考试类型" width="110">
          <template #default="{ row }">{{ getTypeText(row.examType) }}</template>
        </el-table-column>
        <el-table-column prop="termName" label="学期" width="140" show-overflow-tooltip />
        <el-table-column prop="schoolName" label="学校" min-width="140" show-overflow-tooltip />
        <el-table-column label="年级" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ (row.gradeNames || []).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column prop="examDate" label="考试日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" @click="handleDetail(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="getStatusAction(row.status)"
              link
              :type="getStatusAction(row.status)!.buttonType"
              @click="handleStatusAction(row)"
            >
              {{ getStatusAction(row.status)!.label }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="loadData"
        style="margin-top: 20px"
      />
    </el-card>

    <ExamFormDialog v-model:visible="dialogVisible" :edit-data="editData" @saved="loadData" />
  </div>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getExamList,
  deleteExam,
  startExamInput,
  submitExamReview,
  publishExam,
  type Exam,
  type ExamQuery
} from '@/api/exam'
import { getSchoolList, type School } from '@/api/school'
import { getAllTerms, termLabel, type Term } from '@/api/term'
import {
  statusOptions,
  useExamTypeOptions,
  getStatusType,
  getStatusText,
  getTypeText,
  getStatusAction
} from './examOptions'
import ExamFormDialog from './ExamFormDialog.vue'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'

const router = useRouter()

/** 考试类型筛选：字典 exam_type，失败回落内置表 */
const { options: typeOptions } = useExamTypeOptions()

const loading = ref(false)
const tableData = ref<Exam[]>([])
const schoolList = ref<School[]>([])
const termList = ref<Term[]>([])

const dialogVisible = ref(false)
const editData = ref<Exam | null>(null)

const searchForm = reactive({
  examName: '',
  termId: undefined as number | undefined,
  schoolId: undefined as number | undefined,
  status: undefined as number | undefined,
  examType: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const loadData = async () => {
  loading.value = true
  try {
    const params: ExamQuery = {
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    const res = (await getExamList(params)) as PageResponse<Exam>
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    handleError(error, '加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadSchoolList = async () => {
  try {
    const res = (await getSchoolList({ pageNum: 1, pageSize: 1000 })) as { records: School[] }
    schoolList.value = res.records || []
  } catch (error) {
    handleError(error, '加载学校列表失败')
  }
}

const loadTermList = async () => {
  try {
    termList.value = ((await getAllTerms()) as Term[]) || []
  } catch (error) {
    handleError(error, '加载学期列表失败')
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.examName = ''
  searchForm.termId = undefined
  searchForm.schoolId = undefined
  searchForm.status = undefined
  searchForm.examType = undefined
  handleSearch()
}

const handleAdd = () => {
  editData.value = null
  dialogVisible.value = true
}

const handleEdit = (row: Exam) => {
  // 深拷贝，避免直接修改表格数据
  editData.value = JSON.parse(JSON.stringify(row)) as Exam
  dialogVisible.value = true
}

const handleDetail = (row: Exam) => {
  router.push(`/exam/detail/${row.id}`)
}

const handleDelete = (row: Exam) => {
  ElMessageBox.confirm(`确定要删除考试「${row.examName}」吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        await deleteExam(row.id!)
        ElMessage.success('删除成功')
        loadData()
      } catch (error) {
        handleError(error, '删除失败')
      }
    })
    .catch(() => undefined)
}

const handleStatusAction = (row: Exam) => {
  const action = getStatusAction(row.status)
  if (!action) return
  ElMessageBox.confirm(`确定要「${action.label}」吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        if (action.action === 'start-input') {
          await startExamInput(row.id!)
        } else if (action.action === 'submit-review') {
          await submitExamReview(row.id!)
        } else {
          await publishExam(row.id!)
        }
        ElMessage.success(`${action.label}成功`)
        loadData()
      } catch (error) {
        handleError(error, `${action.label}失败`)
      }
    })
    .catch(() => undefined)
}

onMounted(() => {
  loadData()
  loadSchoolList()
  loadTermList()
})
</script>

<style scoped lang="scss">
.exam-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
