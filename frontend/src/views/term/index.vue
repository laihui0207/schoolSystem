<template>
  <div class="term-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="所属学校">
          <el-select v-model="searchForm.schoolId" placeholder="请选择学校" clearable style="width: 180px">
            <el-option
              v-for="s in schoolOptions"
              :key="s.id"
              :label="s.schoolName"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option v-for="o in termStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="所属学校" min-width="150">
          <template #default="{ row }">
            {{ schoolMap[row.schoolId] ?? row.schoolId ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="year" label="学年" width="120" />
        <el-table-column label="学期" width="110">
          <template #default="{ row }">{{ termLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === TERM_STATUS.ACTIVE ? 'success' : 'info'">
              {{ row.status === TERM_STATUS.ACTIVE ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === TERM_STATUS.ACTIVE" link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === TERM_STATUS.ACTIVE" link type="warning" @click="handleEnd(row)">结束</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
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
        style="margin-top: 20px"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="学校" prop="schoolId">
          <el-select v-model="form.schoolId" placeholder="请选择学校" style="width: 100%">
            <el-option
              v-for="s in schoolOptions"
              :key="s.id"
              :label="s.schoolName"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学年" prop="year">
          <el-input v-model="form.year" placeholder="格式：2024-2025" />
        </el-form-item>
        <el-form-item label="学期" prop="term">
          <el-select v-model="form.term" placeholder="请选择学期" style="width: 100%">
            <el-option v-for="o in termOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="选择结束日期" style="width: 100%" />
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getTermList, createTerm, updateTerm, endTerm, deleteTerm,
  termLabel, termOptions, termStatusOptions, TERM_STATUS,
  type Term
} from '@/api/term'
import { getSchoolList, type School } from '@/api/school'

const loading = ref(false)
const submitLoading = ref(false)

const searchForm = reactive({
  schoolId: undefined as number | undefined,
  status: undefined as number | undefined
})

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref<Term[]>([])
const schoolOptions = ref<School[]>([])

// ponytail: flat map for schoolId→name lookup, rebuilt when schoolOptions loads
const schoolMap = computed(() =>
  Object.fromEntries(schoolOptions.value.map(s => [s.id!, s.schoolName]))
)

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const form = reactive<Term>({
  id: undefined,
  schoolId: undefined,
  year: '',
  term: undefined,
  startDate: '',
  endDate: ''
})

const yearPattern = /^\d{4}-\d{4}$/

const rules: FormRules = {
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }],
  year: [
    { required: true, message: '请输入学年', trigger: 'blur' },
    { pattern: yearPattern, message: '格式：2024-2025', trigger: 'blur' }
  ],
  term: [{ required: true, message: '请选择学期', trigger: 'change' }]
}

const loadSchools = async () => {
  try {
    const res: any = await getSchoolList({ pageNum: 1, pageSize: 1000 })
    schoolOptions.value = res.records || []
  } catch {
    // non-blocking
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getTermList({ ...searchForm, pageNum: pagination.pageNum, pageSize: pagination.pageSize })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } catch (error) {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.schoolId = undefined
  searchForm.status = undefined
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增学期'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Term) => {
  dialogTitle.value = '编辑学期'
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleEnd = (row: Term) => {
  ElMessageBox.confirm(`确定要结束「${termLabel(row)}」吗？结束后不可恢复。`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await endTerm(row.id!)
      ElMessage.success('学期已结束')
      loadData()
    } catch {
      ElMessage.error('操作失败')
    }
  })
}

const handleDelete = (row: Term) => {
  ElMessageBox.confirm(`确定要删除「${termLabel(row)}」吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteTerm(row.id!)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      ElMessage.error('删除失败')
    }
  })
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateTerm(form.id, form)
        ElMessage.success('更新成功')
      } else {
        await createTerm(form)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch {
      ElMessage.error('提交失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

const resetForm = () => {
  form.id = undefined
  form.schoolId = undefined
  form.year = ''
  form.term = undefined
  form.startDate = ''
  form.endDate = ''
}

onMounted(() => {
  loadSchools()
  loadData()
})
</script>

<style scoped lang="scss">
.term-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
