<template>
  <div class="config-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="配置名称">
          <el-input
            v-model="filterName"
            placeholder="请输入配置名称或键名"
            clearable
            @input="applyFilter"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="applyFilter">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Plus" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table
        v-loading="loading"
        :data="filteredData"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="configName" label="配置名称" min-width="150" />
        <el-table-column prop="configKey" label="配置键" min-width="180" />
        <el-table-column prop="configValue" label="配置值" min-width="200" show-overflow-tooltip />
        <!-- ponytail: configType 0=非内置, 1=系统内置 -->
        <el-table-column prop="configType" label="系统内置" width="100">
          <template #default="{ row }">
            <el-tag :type="(row as SystemConfig).configType === 1 ? 'warning' : ''">
              {{ (row as SystemConfig).configType === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as SystemConfig)">
              编辑
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as SystemConfig)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入配置名称" />
        </el-form-item>
        <el-form-item label="配置键" prop="configKey">
          <el-input
            v-model="form.configKey"
            placeholder="请输入配置键（如：system.title）"
            :disabled="!!form.id"
          />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input
            v-model="form.configValue"
            type="textarea"
            :rows="3"
            placeholder="请输入配置值"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
            placeholder="请输入备注"
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
  getConfigList,
  createConfig,
  updateConfig,
  deleteConfig,
  type SystemConfig
} from '@/api/config'

const loading = ref(false)
const submitLoading = ref(false)
const filterName = ref('')

const allData = ref<SystemConfig[]>([])
const filteredData = computed(() => {
  if (!filterName.value) return allData.value
  const kw = filterName.value.toLowerCase()
  return allData.value.filter(
    (c) => c.configName.toLowerCase().includes(kw) || c.configKey.toLowerCase().includes(kw)
  )
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()

const defaultForm = (): SystemConfig => ({
  id: undefined,
  configName: '',
  configKey: '',
  configValue: '',
  configType: 0,
  remark: ''
})

const form = reactive<SystemConfig>(defaultForm())

const rules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configKey: [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    // ponytail: GET /configs 直接返回 List，不分页
    allData.value = await getConfigList() || []
  } catch {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const applyFilter = () => { /* computed already reactive */ }

const handleReset = () => {
  filterName.value = ''
  loadData()
}

const handleAdd = () => {
  dialogTitle.value = '新增配置'
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

const handleEdit = (row: SystemConfig) => {
  dialogTitle.value = '编辑配置'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const handleDelete = (row: SystemConfig) => {
  ElMessageBox.confirm('确定要删除这条配置吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteConfig(row.id!)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (form.id) {
          await updateConfig(form.id, form)
          ElMessage.success('更新成功')
        } else {
          await createConfig(form)
          ElMessage.success('创建成功')
        }
        dialogVisible.value = false
        loadData()
      } catch {
        ElMessage.error('提交失败')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  Object.assign(form, defaultForm())
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.config-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
