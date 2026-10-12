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
          <el-button v-if="canEdit" type="success" :icon="Plus" @click="handleAdd">新增</el-button>
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
        <el-table-column prop="configValue" label="配置值" min-width="120" show-overflow-tooltip />
        <!-- 内置 key 的业务说明与生效范围来自前端常量 BUILTIN_CONFIG_META，自定义 key 显示 - -->
        <el-table-column label="作用" min-width="300">
          <template #default="{ row }">
            <template v-if="metaOf(row as SystemConfig)">
              <div class="config-usage">{{ metaOf(row as SystemConfig)!.description }}</div>
              <div class="config-usage-tags">
                <el-tag size="small" type="info">生效范围：{{ metaOf(row as SystemConfig)!.scope }}</el-tag>
                <el-tag size="small">取值：{{ metaOf(row as SystemConfig)!.format }}</el-tag>
              </div>
            </template>
            <span v-else class="config-usage-empty">-</span>
          </template>
        </el-table-column>
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
        <!-- ponytail: 后端 /configs 写接口只放给超管（sys_config 全系统共享），校管理员只读，按钮直接不渲染 -->
        <el-table-column v-if="canEdit" label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as SystemConfig)">
              编辑
            </el-button>
            <el-button
              link
              type="danger"
              :icon="Delete"
              :disabled="(row as SystemConfig).configType === 1"
              @click="handleDelete(row as SystemConfig)"
            >
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
            :placeholder="currentMeta ? `请输入配置值（${currentMeta.format}）` : '请输入配置值'"
          />
        </el-form-item>
        <el-alert
          v-if="currentMeta"
          type="info"
          :closable="false"
          show-icon
          class="config-meta-alert"
        >
          <template #title>{{ currentMeta.description }}</template>
          生效范围：{{ currentMeta.scope }}；保存后立即生效。
        </el-alert>
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
  BUILTIN_CONFIG_META,
  type SystemConfig
} from '@/api/config'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
/** 超级管理员 userType=1；与后端 ConfigController 的 hasRole('SUPER_ADMIN') 对齐 */
const canEdit = computed(() => userStore.userInfo?.userType === 1)

/** 内置 key 的业务说明；自定义 key 返回 undefined */
const metaOf = (row: SystemConfig) => BUILTIN_CONFIG_META[row.configKey]

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

/** 正在编辑的 key 若是内置项，在弹窗里给出说明与取值提示 */
const currentMeta = computed(() => (form.configKey ? BUILTIN_CONFIG_META[form.configKey] : undefined))

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

  .config-usage {
    line-height: 1.5;
    margin-bottom: 4px;
    white-space: normal;
  }

  .config-usage-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .config-usage-empty {
    color: #909399;
  }
}

.config-meta-alert {
  margin: 0 0 12px 100px;
  width: auto;
}
</style>
