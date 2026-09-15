<template>
  <div class="system-management">
    <el-tabs v-model="activeTab">
      <!-- ===== Tab 1: 系统配置 ===== -->
      <el-tab-pane label="系统配置" name="config">
        <el-card>
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
              <el-button :icon="Refresh" @click="handleConfigReset">重置</el-button>
              <el-button type="success" :icon="Plus" @click="handleConfigAdd">新增</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="configLoading" :data="filteredConfigs" stripe border style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="configName" label="配置名称" min-width="150" />
            <el-table-column prop="configKey" label="配置键" min-width="180" />
            <el-table-column prop="configValue" label="配置值" min-width="200" show-overflow-tooltip />
            <el-table-column prop="configType" label="系统内置" width="100">
              <template #default="{ row }">
                <el-tag :type="row.configType === 1 ? 'warning' : ''">
                  {{ row.configType === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="Edit" @click="handleConfigEdit(row)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="handleConfigDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-dialog v-model="configDialogVisible" :title="configDialogTitle" width="600px" @close="handleConfigDialogClose">
          <el-form ref="configFormRef" :model="configForm" :rules="configRules" label-width="100px">
            <el-form-item label="配置名称" prop="configName">
              <el-input v-model="configForm.configName" placeholder="请输入配置名称" />
            </el-form-item>
            <el-form-item label="配置键" prop="configKey">
              <el-input v-model="configForm.configKey" placeholder="请输入配置键（如：system.title）" :disabled="!!configForm.id" />
            </el-form-item>
            <el-form-item label="配置值" prop="configValue">
              <el-input v-model="configForm.configValue" type="textarea" :rows="3" placeholder="请输入配置值" />
            </el-form-item>
            <el-form-item label="备注" prop="remark">
              <el-input v-model="configForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="configDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="configSubmitLoading" @click="handleConfigSubmit">确定</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- ===== Tab 2: 数据字典 ===== -->
      <el-tab-pane label="数据字典" name="dict">
        <el-row :gutter="16">
          <!-- 左侧：字典类型 -->
          <el-col :span="6">
            <el-card>
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center">
                  <span>字典类型</span>
                  <el-button type="primary" :icon="Plus" size="small" @click="handleDictTypeAdd">新增</el-button>
                </div>
              </template>
              <div v-loading="dictTypeLoading">
                <div
                  v-for="item in dictTypeList"
                  :key="item.id"
                  class="dict-type-item"
                  :class="{ active: selectedDictType === item.dictType }"
                  @click="handleDictTypeSelect(item)"
                >
                  <div class="dict-type-info">
                    <div class="dict-type-name">{{ item.dictName }}</div>
                    <div class="dict-type-code">{{ item.dictType }}</div>
                  </div>
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    size="small"
                    @click.stop="handleDictTypeDelete(item)"
                  />
                </div>
                <el-empty v-if="!dictTypeList.length" description="暂无数据" :image-size="60" />
              </div>
            </el-card>
          </el-col>

          <!-- 右侧：字典数据 -->
          <el-col :span="18">
            <el-card>
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center">
                  <span>字典数据{{ selectedDictType ? `：${selectedDictType}` : '' }}</span>
                  <el-button
                    type="primary"
                    :icon="Plus"
                    size="small"
                    :disabled="!selectedDictType"
                    @click="handleDictDataAdd"
                  >新增</el-button>
                </div>
              </template>
              <el-table v-loading="dictDataLoading" :data="dictDataList" stripe border style="width: 100%">
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="dictLabel" label="字典标签" min-width="120" />
                <el-table-column prop="dictValue" label="字典值" min-width="120" />
                <el-table-column prop="sort" label="排序" width="80" />
                <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
                <el-table-column label="操作" width="150" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" :icon="Edit" @click="handleDictDataEdit(row)">编辑</el-button>
                    <el-button link type="danger" :icon="Delete" @click="handleDictDataDelete(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-if="!selectedDictType" description="请先选择左侧字典类型" />
            </el-card>
          </el-col>
        </el-row>

        <!-- 字典类型弹窗 -->
        <el-dialog v-model="dictTypeDialogVisible" title="新增字典类型" width="500px" @close="dictTypeFormRef?.resetFields()">
          <el-form ref="dictTypeFormRef" :model="dictTypeForm" :rules="dictTypeRules" label-width="100px">
            <el-form-item label="字典名称" prop="dictName">
              <el-input v-model="dictTypeForm.dictName" placeholder="请输入字典名称" />
            </el-form-item>
            <el-form-item label="字典类型" prop="dictType">
              <el-input v-model="dictTypeForm.dictType" placeholder="请输入字典类型（如：sys_user_sex）" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="dictTypeForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dictTypeDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="dictTypeSubmitLoading" @click="handleDictTypeSubmit">确定</el-button>
          </template>
        </el-dialog>

        <!-- 字典数据弹窗 -->
        <el-dialog v-model="dictDataDialogVisible" :title="dictDataDialogTitle" width="500px" @close="dictDataFormRef?.resetFields()">
          <el-form ref="dictDataFormRef" :model="dictDataForm" :rules="dictDataRules" label-width="100px">
            <el-form-item label="字典标签" prop="dictLabel">
              <el-input v-model="dictDataForm.dictLabel" placeholder="请输入字典标签" />
            </el-form-item>
            <el-form-item label="字典值" prop="dictValue">
              <el-input v-model="dictDataForm.dictValue" placeholder="请输入字典值" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="dictDataForm.sort" :min="0" :max="999" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="dictDataForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dictDataDialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="dictDataSubmitLoading" @click="handleDictDataSubmit">确定</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
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
import {
  getDictTypeList,
  getDictDataByType,
  createDictType,
  createDictData,
  updateDictData,
  deleteDictType,
  deleteDictData,
  type DictType,
  type DictData
} from '@/api/dict'

const activeTab = ref('config')

// ===== 系统配置 =====
const configLoading = ref(false)
const configSubmitLoading = ref(false)
const filterName = ref('')
const allConfigs = ref<SystemConfig[]>([])
const configDialogVisible = ref(false)
const configDialogTitle = ref('')
const configFormRef = ref<FormInstance>()

const filteredConfigs = computed(() => {
  if (!filterName.value) return allConfigs.value
  const kw = filterName.value.toLowerCase()
  return allConfigs.value.filter(
    (c) => c.configName.toLowerCase().includes(kw) || c.configKey.toLowerCase().includes(kw)
  )
})

const defaultConfigForm = (): SystemConfig => ({
  id: undefined, configName: '', configKey: '', configValue: '', configType: 0, remark: ''
})
const configForm = reactive<SystemConfig>(defaultConfigForm())

const configRules: FormRules = {
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configKey:  [{ required: true, message: '请输入配置键', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }]
}

const loadConfigs = async () => {
  configLoading.value = true
  try {
    allConfigs.value = (await getConfigList()) || []
  } catch {
    ElMessage.error('加载配置失败')
  } finally {
    configLoading.value = false
  }
}

const applyFilter = () => { /* computed is reactive */ }

const handleConfigReset = () => { filterName.value = ''; loadConfigs() }
const handleConfigAdd = () => {
  configDialogTitle.value = '新增配置'
  Object.assign(configForm, defaultConfigForm())
  configDialogVisible.value = true
}
const handleConfigEdit = (row: SystemConfig) => {
  configDialogTitle.value = '编辑配置'
  Object.assign(configForm, { ...row })
  configDialogVisible.value = true
}
const handleConfigDelete = (row: SystemConfig) => {
  ElMessageBox.confirm('确定要删除这条配置吗？', '提示', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteConfig(row.id!)
      ElMessage.success('删除成功')
      loadConfigs()
    } catch {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}
const handleConfigSubmit = async () => {
  if (!configFormRef.value) return
  await configFormRef.value.validate(async (valid) => {
    if (!valid) return
    configSubmitLoading.value = true
    try {
      if (configForm.id) {
        await updateConfig(configForm.id, configForm)
        ElMessage.success('更新成功')
      } else {
        await createConfig(configForm)
        ElMessage.success('创建成功')
      }
      configDialogVisible.value = false
      loadConfigs()
    } catch {
      ElMessage.error('提交失败')
    } finally {
      configSubmitLoading.value = false
    }
  })
}
const handleConfigDialogClose = () => {
  configFormRef.value?.resetFields()
  Object.assign(configForm, defaultConfigForm())
}

// ===== 数据字典 =====
const dictTypeLoading = ref(false)
const dictDataLoading = ref(false)
const dictTypeSubmitLoading = ref(false)
const dictDataSubmitLoading = ref(false)

const dictTypeList = ref<DictType[]>([])
const dictDataList = ref<DictData[]>([])
const selectedDictType = ref('')

const dictTypeDialogVisible = ref(false)
const dictTypeFormRef = ref<FormInstance>()
const dictTypeForm = reactive<DictType>({ dictType: '', dictName: '', remark: '' })
const dictTypeRules: FormRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }]
}

const dictDataDialogVisible = ref(false)
const dictDataDialogTitle = ref('')
const dictDataFormRef = ref<FormInstance>()
const defaultDictDataForm = (): DictData => ({
  id: undefined, dictType: '', dictLabel: '', dictValue: '', sort: 0, remark: ''
})
const dictDataForm = reactive<DictData>(defaultDictDataForm())
const dictDataRules: FormRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典值', trigger: 'blur' }]
}

const loadDictTypes = async () => {
  dictTypeLoading.value = true
  try {
    dictTypeList.value = (await getDictTypeList()) || []
  } catch {
    ElMessage.error('加载字典类型失败')
  } finally {
    dictTypeLoading.value = false
  }
}

const loadDictData = async (type: string) => {
  dictDataLoading.value = true
  try {
    dictDataList.value = (await getDictDataByType(type)) || []
  } catch {
    ElMessage.error('加载字典数据失败')
  } finally {
    dictDataLoading.value = false
  }
}

const handleDictTypeSelect = (item: DictType) => {
  selectedDictType.value = item.dictType
  loadDictData(item.dictType)
}

const handleDictTypeAdd = () => {
  Object.assign(dictTypeForm, { dictType: '', dictName: '', remark: '' })
  dictTypeDialogVisible.value = true
}
const handleDictTypeDelete = (item: DictType) => {
  ElMessageBox.confirm(`确定要删除字典类型「${item.dictName}」吗？`, '提示', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteDictType(item.id!)
      ElMessage.success('删除成功')
      if (selectedDictType.value === item.dictType) {
        selectedDictType.value = ''
        dictDataList.value = []
      }
      loadDictTypes()
    } catch {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}
const handleDictTypeSubmit = async () => {
  if (!dictTypeFormRef.value) return
  await dictTypeFormRef.value.validate(async (valid) => {
    if (!valid) return
    dictTypeSubmitLoading.value = true
    try {
      await createDictType(dictTypeForm)
      ElMessage.success('创建成功')
      dictTypeDialogVisible.value = false
      loadDictTypes()
    } catch {
      ElMessage.error('创建失败')
    } finally {
      dictTypeSubmitLoading.value = false
    }
  })
}

const handleDictDataAdd = () => {
  dictDataDialogTitle.value = '新增字典数据'
  Object.assign(dictDataForm, { ...defaultDictDataForm(), dictType: selectedDictType.value })
  dictDataDialogVisible.value = true
}
const handleDictDataEdit = (row: DictData) => {
  dictDataDialogTitle.value = '编辑字典数据'
  Object.assign(dictDataForm, { ...row })
  dictDataDialogVisible.value = true
}
const handleDictDataDelete = (row: DictData) => {
  ElMessageBox.confirm('确定要删除这条字典数据吗？', '提示', {
    type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteDictData(row.id!)
      ElMessage.success('删除成功')
      loadDictData(selectedDictType.value)
    } catch {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}
const handleDictDataSubmit = async () => {
  if (!dictDataFormRef.value) return
  await dictDataFormRef.value.validate(async (valid) => {
    if (!valid) return
    dictDataSubmitLoading.value = true
    try {
      if (dictDataForm.id) {
        await updateDictData(dictDataForm.id, dictDataForm)
        ElMessage.success('更新成功')
      } else {
        await createDictData(dictDataForm)
        ElMessage.success('创建成功')
      }
      dictDataDialogVisible.value = false
      loadDictData(selectedDictType.value)
    } catch {
      ElMessage.error('提交失败')
    } finally {
      dictDataSubmitLoading.value = false
    }
  })
}

// 切换到字典 tab 时才加载
watch(activeTab, (tab) => {
  if (tab === 'dict' && !dictTypeList.value.length) loadDictTypes()
})

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped lang="scss">
.system-management {
  .search-form {
    margin-bottom: 20px;
  }

  .dict-type-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 8px;
    border-radius: 4px;
    cursor: pointer;
    transition: background-color 0.2s;

    &:hover {
      background-color: var(--el-fill-color-light);
    }

    &.active {
      background-color: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
    }

    .dict-type-info {
      flex: 1;
      overflow: hidden;
    }

    .dict-type-name {
      font-size: 14px;
      font-weight: 500;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .dict-type-code {
      font-size: 12px;
      color: var(--el-text-color-secondary);
      margin-top: 2px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }
}
</style>
