<template>
  <div class="school-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="学校名称">
          <el-input
            v-model="searchForm.schoolName"
            placeholder="请输入学校名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
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
        <el-table-column prop="schoolName" label="学校名称" min-width="150" />
        <el-table-column prop="schoolCode" label="学校代码" width="120" />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row as School)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as School)">
              编辑
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
        <el-form-item label="学校名称" prop="schoolName">
          <el-input v-model="form.schoolName" placeholder="请输入学校名称" />
        </el-form-item>
        <el-form-item label="学校代码" prop="schoolCode">
          <el-input v-model="form.schoolCode" placeholder="请输入学校代码" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入地址" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="form.contactPerson" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus, Edit } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getSchoolList,
  createSchool,
  updateSchool,
  updateSchoolStatus,
  type School
} from '@/api/school'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'

const loading = ref(false)
const submitLoading = ref(false)

const searchForm = reactive({
  schoolName: '',
  status: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref<School[]>([])
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const defaultForm = (): School => ({
  id: undefined,
  schoolName: '',
  schoolCode: '',
  address: '',
  contactPhone: '',
  contactPerson: '',
  status: 1
})

const form = reactive<School>(defaultForm())

const rules: FormRules = {
  schoolName: [
    { required: true, message: '请输入学校名称', trigger: 'blur' }
  ],
  schoolCode: [
    { required: true, message: '请输入学校代码', trigger: 'blur' }
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
    const res = await getSchoolList(params) as PageResponse<School>
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch (error) {
    handleError(error, '加载数据失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.schoolName = ''
  searchForm.status = undefined
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增学校'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: School) => {
  dialogTitle.value = '编辑学校'
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  dialogVisible.value = true
}



const handleStatusChange = async (row: School) => {
  const previousStatus = row.status
  try {
    await updateSchoolStatus(row.id!, row.status)
    ElMessage.success('状态更新成功')
  } catch (error) {
    handleError(error, '状态更新失败')
    row.status = previousStatus
  }
}

/**
 * 可选字段的空字符串转 undefined（JSON 序列化时省略），不改原表单对象
 */
const normalizeSchoolForm = (source: School): School => {
  const blankToUndefined = (v?: string) => (v && v.trim() ? v.trim() : undefined)
  return {
    ...source,
    address: blankToUndefined(source.address),
    contactPhone: blankToUndefined(source.contactPhone),
    contactPerson: blankToUndefined(source.contactPerson)
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        // ponytail: G-6 — 表单默认值是空字符串，直接提交会让后端 @Pattern(contactPhone) 等校验把 '' 判为格式错误；空串字段转 undefined 不发
        const payload = normalizeSchoolForm(form)
        if (form.id) {
          await updateSchool(form.id, payload)
          ElMessage.success('更新成功')
        } else {
          await createSchool(payload)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadData()
      } catch (error) {
        handleError(error, '提交失败')
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
})
</script>

<style scoped lang="scss">
.school-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
