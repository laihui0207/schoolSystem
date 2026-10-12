<template>
  <div class="grade-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="年级名称">
          <el-input
            v-model="searchForm.gradeName"
            placeholder="请输入年级名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
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
        <el-table-column prop="gradeName" label="年级名称" min-width="120" />
        <!-- ponytail: GradeResponse 无 schoolName，前端用已加载的 schoolList 映射 -->
        <el-table-column label="所属学校" min-width="150">
          <template #default="{ row }">{{ schoolNameOf(row.schoolId) }}</template>
        </el-table-column>
        <el-table-column prop="gradeCode" label="年级编码" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as Grade)">
              编辑
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as Grade)">
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
        <el-form-item label="年级名称" prop="gradeName">
          <el-input v-model="form.gradeName" placeholder="请输入年级名称" />
        </el-form-item>
        <!-- 后端 GradeUpdateRequest 不接受 schoolId，编辑时禁用 -->
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
        <el-form-item label="年级编码" prop="gradeCode">
          <el-input-number
            v-model="form.gradeCode"
            :min="1"
            :max="20"
            placeholder="请输入年级编码"
            style="width: 100%"
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
import { Search, Refresh, Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getGradeList,
  createGrade,
  updateGrade,
  deleteGrade,
  type Grade
} from '@/api/grade'
import { getSchoolList, type School } from '@/api/school'
import { handleError } from '@/utils/errorHandler'

type TableData = Grade

const loading = ref(false)
const submitLoading = ref(false)

const searchForm = reactive({
  gradeName: '',
  schoolId: undefined as number | undefined
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

// id 必须显式列出：resetForm 走 Object.assign，缺 key 就清不掉，
// 「编辑」后再点「新增」会带着上次的 id 去改旧记录
const defaultForm = (): Grade => ({
  id: undefined,
  gradeName: '',
  schoolId: undefined,
  gradeCode: undefined
})

const form = reactive<Grade>(defaultForm())

const isEdit = computed(() => !!form.id)

const schoolNameOf = (id?: number) =>
  schoolList.value.find((s) => s.id === id)?.schoolName || '-'

const rules: FormRules = {
  gradeName: [
    { required: true, message: '请输入年级名称', trigger: 'blur' }
  ],
  schoolId: [
    { required: true, message: '请选择学校', trigger: 'change' }
  ],
  // 后端 GradeCreateRequest @NotNull，漏发直接 400
  gradeCode: [
    { required: true, message: '请输入年级编码', trigger: 'blur' }
  ]
}

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    const res = await getGradeList(params)
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
    const res = await getSchoolList({
      pageNum: 1,
      pageSize: 1000
    }) as { records: School[] }
    schoolList.value = res.records || []
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
    gradeName: '',
    schoolId: undefined
  })
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增年级'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Grade) => {
  dialogTitle.value = '编辑年级'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const handleDelete = (row: Grade) => {
  ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteGrade(row.id!)
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
          await updateGrade(form.id, form)
          ElMessage.success('更新成功')
        } else {
          await createGrade(form)
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
  loadData()
  loadSchoolList()
})
</script>

<style scoped lang="scss">
.grade-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
