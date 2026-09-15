<template>
  <div class="grade-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="所属学校">
          <el-select
            v-model="searchForm.schoolId"
            placeholder="请选择学校"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="s in schoolOptions"
              :key="s.id"
              :label="s.schoolName"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="年级名称">
          <el-input
            v-model="searchForm.gradeName"
            placeholder="请输入年级名称"
            clearable
            @keyup.enter="handleSearch"
          />
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
            {{ schoolMap[row.schoolId] ?? row.schoolId }}
          </template>
        </el-table-column>
        <el-table-column prop="gradeName" label="年级名称" min-width="120" />
        <el-table-column prop="gradeCode" label="年级编码" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属学校" prop="schoolId">
          <el-select v-model="form.schoolId" placeholder="请选择学校" :disabled="!!form.id" style="width: 100%">
            <el-option
              v-for="s in schoolOptions"
              :key="s.id"
              :label="s.schoolName"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="年级名称" prop="gradeName">
          <el-input v-model="form.gradeName" placeholder="如：高一" />
        </el-form-item>
        <el-form-item label="年级编码" prop="gradeCode">
          <el-input-number v-model="form.gradeCode" :min="1" placeholder="排序用数字" style="width: 100%" />
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
import { getGradeList, createGrade, updateGrade, deleteGrade, type Grade } from '@/api/grade'
import { getSchoolList, type School } from '@/api/school'

const loading = ref(false)
const submitLoading = ref(false)

const searchForm = reactive({
  gradeName: '',
  schoolId: undefined as number | undefined
})

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const tableData = ref<Grade[]>([])
const schoolOptions = ref<School[]>([])
// ponytail: flat map for display, rebuilt when schoolOptions loads
const schoolMap = computed(() =>
  Object.fromEntries(schoolOptions.value.map(s => [s.id, s.schoolName]))
)

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const form = reactive<Grade & { id?: number }>({
  id: undefined,
  schoolId: undefined,
  gradeName: '',
  gradeCode: undefined
})

const rules: FormRules = {
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }],
  gradeName: [{ required: true, message: '请输入年级名称', trigger: 'blur' }],
  gradeCode: [{ required: true, message: '请输入年级编码', trigger: 'blur' }]
}

const loadSchools = async () => {
  try {
    const res: any = await getSchoolList({ pageNum: 1, pageSize: 1000 })
    schoolOptions.value = res.records || []
  } catch {
    // non-fatal, dropdown will be empty
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getGradeList({
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
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
  searchForm.gradeName = ''
  searchForm.schoolId = undefined
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增年级'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Grade) => {
  dialogTitle.value = '编辑年级'
  Object.assign(form, row)
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
        await updateGrade(form.id, form)
        ElMessage.success('更新成功')
      } else {
        await createGrade(form)
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
  form.gradeName = ''
  form.gradeCode = undefined
}

onMounted(() => {
  loadSchools()
  loadData()
})
</script>

<style scoped lang="scss">
.grade-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
