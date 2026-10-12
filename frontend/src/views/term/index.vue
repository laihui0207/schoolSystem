<template>
  <div class="term-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="学校">
          <el-select v-model="searchForm.schoolId" placeholder="请选择学校" clearable>
            <el-option
              v-for="item in schoolList"
              :key="item.id"
              :label="item.schoolName"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学年">
          <el-input
            v-model="searchForm.year"
            placeholder="例如：2024-2025"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="searchForm.term" placeholder="请选择学期" clearable>
            <el-option
              v-for="item in termOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option
              v-for="item in termStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>
      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="tableData"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="学校" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ schoolName(row.schoolId) }}</template>
        </el-table-column>
        <el-table-column prop="year" label="学年" width="120" />
        <!-- 学年已单独成列，这里只显示学期名，不用 termLabel 重复拼学年 -->
        <el-table-column label="学期" width="120">
          <template #default="{ row }">{{ TERM_NAMES[row.term] || '-' }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === TERM_STATUS.ACTIVE ? 'success' : 'info'">
              {{ row.status === TERM_STATUS.ACTIVE ? '进行中' : '已结束' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as Term)">
              编辑
            </el-button>
            <el-button
              v-if="row.status === TERM_STATUS.ACTIVE"
              link
              type="warning"
              :icon="CircleCheck"
              @click="handleEndTerm(row as Term)"
            >
              结束学期
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as Term)">
              删除
            </el-button>
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
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="所属学校" prop="schoolId">
          <el-select
            v-model="form.schoolId"
            placeholder="请选择学校"
            style="width: 100%"
            :disabled="isEdit"
          >
            <el-option
              v-for="item in schoolList"
              :key="item.id"
              :label="item.schoolName"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学年" prop="year">
          <el-input v-model="form.year" placeholder="例如：2024-2025" />
        </el-form-item>
        <el-form-item label="学期" prop="term">
          <el-select v-model="form.term" placeholder="请选择学期" style="width: 100%">
            <el-option
              v-for="item in termOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="form.startDate"
            type="date"
            placeholder="请选择开始日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            placeholder="请选择结束日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, CircleCheck } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getTermList,
  createTerm,
  updateTerm,
  endTerm,
  deleteTerm,
  termLabel,
  termOptions,
  termStatusOptions,
  TERM_NAMES,
  TERM_STATUS,
  type Term
} from '@/api/term'
import { getSchoolList, type School } from '@/api/school'
import { handleError } from '@/utils/errorHandler'

type TableData = Term

const loading = ref(false)
const submitLoading = ref(false)

const searchForm = reactive({
  schoolId: undefined as number | undefined,
  year: '',
  term: undefined as number | undefined,
  status: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref<TableData[]>([])
const schoolList = ref<School[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

// id/status 必须显式列出：resetForm 走 Object.assign，缺 key 就清不掉
// 否则「编辑」后再点「新增」会带着上次的 id，把旧学期改掉而不是新建
const defaultForm = (): Term => ({
  id: undefined,
  schoolId: undefined,
  year: '',
  term: undefined,
  startDate: '',
  endDate: '',
  status: undefined
})

const form = reactive<Term>(defaultForm())

const isEdit = computed(() => !!form.id)

// AcademicTermResponse 不回 schoolName，用已加载的 schoolList 映射
const schoolName = (id?: number) =>
  schoolList.value.find((s) => s.id === id)?.schoolName || '-'

const validateDateRange = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
  if (form.startDate && form.endDate) {
    if (form.startDate >= form.endDate) {
      callback(new Error('结束日期必须大于开始日期'))
    } else {
      callback()
    }
  } else {
    callback()
  }
}

const rules: FormRules = {
  schoolId: [
    { required: true, message: '请选择学校', trigger: 'change' }
  ],
  year: [
    { required: true, message: '请输入学年', trigger: 'blur' },
    // 与后端 AcademicTermUpdateRequest 的 @Pattern 一致，格式不对先在前端拦掉
    { pattern: /^\d{4}-\d{4}$/, message: '学年格式不正确，应为：2024-2025', trigger: 'blur' }
  ],
  term: [
    { required: true, message: '请选择学期', trigger: 'change' }
  ],
  startDate: [
    { required: true, message: '请选择开始日期', trigger: 'change' },
    { validator: validateDateRange, trigger: 'change' }
  ],
  endDate: [
    { required: true, message: '请选择结束日期', trigger: 'change' },
    { validator: validateDateRange, trigger: 'change' }
  ]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getTermList({
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
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
    const res = await getSchoolList({ pageNum: 1, pageSize: 1000 })
    schoolList.value = (res as { records?: School[] }).records || []
  } catch (error) {
    handleError(error, '加载学校列表失败')
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, {
    schoolId: undefined,
    year: '',
    term: undefined,
    status: undefined
  })
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增学期'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Term) => {
  dialogTitle.value = '编辑学期'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const handleEndTerm = (row: Term) => {
  ElMessageBox.confirm(
    `确定要结束学期「${termLabel(row)}」吗？结束后将不能再录入该学期的成绩。`,
    '提示',
    {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }
  ).then(async () => {
    try {
      await endTerm(row.id!)
      ElMessage.success('学期已结束')
      loadData()
    } catch (error) {
      handleError(error, '结束学期失败')
    }
  }).catch(() => {
    // 用户取消
  })
}

const handleDelete = (row: Term) => {
  ElMessageBox.confirm(
    `确定要删除学期「${termLabel(row)}」吗？该学期被班级/考试引用时后端会拒绝删除。`,
    '提示',
    {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }
  ).then(async () => {
    try {
      await deleteTerm(row.id!)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      handleError(error, '删除失败')
    }
  }).catch(() => {
    // 用户取消删除
  })
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
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
        // ponytail: 拦截器已弹出后端的具体校验信息，这里不再覆盖成「提交失败」
        // 失败时保持弹窗打开，用户可直接改表单重试
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

const resetForm = () => {
  Object.assign(form, defaultForm())
}

onMounted(() => {
  loadSchoolList()
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
