<template>
  <div class="exam-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="学期">
          <el-select v-model="searchForm.termId" placeholder="请选择学期" clearable style="width: 200px">
            <el-option
              v-for="t in termList"
              :key="t.id"
              :label="termLabel(t)"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考试类型">
          <el-select v-model="searchForm.examType" placeholder="请选择类型" clearable>
            <el-option v-for="o in examTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
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
        <el-table-column prop="examName" label="考试名称" min-width="150" />
        <el-table-column prop="termName" label="学期" width="160" />
        <el-table-column prop="examType" label="考试类型" width="100">
          <template #default="{ row }">
            {{ examTypeLabel(row.examType) }}
          </template>
        </el-table-column>
        <el-table-column prop="examDate" label="考试日期" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0" link type="success" @click="handleStartInput(row)">开始录入</el-button>
            <el-button v-if="row.status === 2" link type="warning" @click="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 0" link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="考试名称" prop="examName">
          <el-input v-model="form.examName" placeholder="请输入考试名称" />
        </el-form-item>
        <el-form-item label="学期" prop="termId">
          <el-select v-model="form.termId" placeholder="请选择学期" style="width: 100%">
            <el-option
              v-for="t in termList"
              :key="t.id"
              :label="termLabel(t)"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="考试类型" prop="examType">
          <el-select v-model="form.examType" placeholder="请选择类型" style="width: 100%">
            <el-option v-for="o in examTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="考试日期" prop="examDate">
          <el-date-picker
            v-model="form.examDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { getExamList, createExam, updateExam, deleteExam, startInput, publishExam } from '@/api/exam'
import { getAllTerms, termLabel, type Term } from '@/api/term'

const examTypeOptions = [
  { label: '月考', value: 1 },
  { label: '期中', value: 2 },
  { label: '期末', value: 3 },
  { label: '模拟', value: 4 },
  { label: '其他', value: 5 }
]

const statusOptions = [
  { label: '未开始', value: 0 },
  { label: '录入中', value: 1 },
  { label: '审核中', value: 2 },
  { label: '已发布', value: 3 }
]

const examTypeMap: Record<number, string> = { 1: '月考', 2: '期中', 3: '期末', 4: '模拟', 5: '其他' }
const statusTagMap: Record<number, string> = { 0: 'info', 1: 'warning', 2: '', 3: 'success' }
const statusLabelMap: Record<number, string> = { 0: '未开始', 1: '录入中', 2: '审核中', 3: '已发布' }

const examTypeLabel = (v: number) => examTypeMap[v] ?? '-'
const statusTagType = (v: number) => statusTagMap[v] ?? 'info'
const statusLabel = (v: number) => statusLabelMap[v] ?? '未知'

const loading = ref(false)
const submitLoading = ref(false)
const termList = ref<Term[]>([])
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const searchForm = reactive({
  termId: undefined as number | undefined,
  examType: undefined as number | undefined,
  status: undefined as number | undefined
})

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const form = reactive({
  id: undefined as number | undefined,
  examName: '',
  termId: undefined as number | undefined,
  examType: undefined as number | undefined,
  examDate: ''
})

const rules: FormRules = {
  examName: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
  termId: [{ required: true, message: '请选择学期', trigger: 'change' }],
  examType: [{ required: true, message: '请选择考试类型', trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getExamList({
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tableData.value = res.records ?? []
    pagination.total = res.total ?? 0
  } catch {
    // request interceptor handles error messages
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.termId = undefined
  searchForm.examType = undefined
  searchForm.status = undefined
  handleSearch()
}

const resetForm = () => {
  form.id = undefined
  form.examName = ''
  form.termId = undefined
  form.examType = undefined
  form.examDate = ''
}

const handleAdd = () => {
  dialogTitle.value = '新增考试'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑考试'
  Object.assign(form, {
    id: row.id,
    examName: row.examName,
    termId: row.termId,
    examType: row.examType,
    examDate: row.examDate
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateExam(form.id, form)
        ElMessage.success('更新成功')
      } else {
        await createExam(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch {
      // handled by interceptor
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

const handleStartInput = async (row: any) => {
  try {
    await startInput(row.id)
    ElMessage.success('已开始录入')
    loadData()
  } catch {
    // handled by interceptor
  }
}

const handlePublish = async (row: any) => {
  try {
    await publishExam(row.id)
    ElMessage.success('发布成功')
    loadData()
  } catch {
    // handled by interceptor
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm('确定要删除该考试吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteExam(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      // handled by interceptor
    }
  })
}

onMounted(async () => {
  termList.value = await getAllTerms()
  loadData()
})
</script>

<style scoped lang="scss">
.exam-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
