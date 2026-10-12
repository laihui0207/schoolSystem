<template>
  <div class="dict-management">
    <el-card>
      <el-row :gutter="20">
        <!-- 左侧：字典类型 -->
        <el-col :span="8">
          <div class="dict-type-section">
            <div class="section-header">
              <h3>字典类型</h3>
              <el-button type="primary" size="small" :icon="Plus" @click="handleAddType">
                新增类型
              </el-button>
            </div>

            <el-input
              v-model="typeSearchText"
              placeholder="搜索字典类型"
              :prefix-icon="Search"
              clearable
              style="margin-bottom: 10px"
            />

            <el-table
              v-loading="typeLoading"
              :data="filteredTypeList"
              highlight-current-row
              @current-change="handleTypeSelect"
              style="width: 100%"
              max-height="600px"
            >
              <el-table-column prop="dictName" label="字典名称" min-width="120" />
              <el-table-column prop="dictType" label="类型编码" min-width="100" />
              <el-table-column prop="status" label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.status === 0 ? 'success' : 'info'">
                    {{ row.status === 0 ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <el-button link type="primary" :icon="Edit" @click.stop="handleEditType(row as DictType)">
                    编辑
                  </el-button>
                  <el-button link type="danger" :icon="Delete" @click.stop="handleDeleteType(row as DictType)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>

        <!-- 右侧：字典数据 -->
        <el-col :span="16">
          <div class="dict-data-section">
            <div class="section-header">
              <h3>字典数据{{ currentType ? `（${currentType.dictName}）` : '' }}</h3>
              <el-button
                type="primary"
                size="small"
                :icon="Plus"
                :disabled="!currentType"
                @click="handleAddData"
              >
                新增数据
              </el-button>
            </div>

            <el-empty v-if="!currentType" description="请先选择字典类型" />

            <el-table
              v-else
              v-loading="dataLoading"
              :data="dataList"
              stripe
              border
              style="width: 100%"
            >
              <el-table-column prop="dictLabel" label="字典标签" min-width="120" />
              <el-table-column prop="dictValue" label="字典值" min-width="120" />
              <el-table-column prop="dictSort" label="排序" width="80" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 0 ? 'success' : 'info'">
                    {{ row.status === 0 ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" :icon="Edit" @click="handleEditData(row as DictData)">
                    编辑
                  </el-button>
                  <el-button link type="danger" :icon="Delete" @click="handleDeleteData(row as DictData)">
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 字典类型对话框 -->
    <el-dialog
      v-model="typeDialogVisible"
      :title="typeDialogTitle"
      width="500px"
      @close="handleTypeDialogClose"
    >
      <el-form
        ref="typeFormRef"
        :model="typeForm"
        :rules="typeRules"
        label-width="100px"
      >
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="类型编码" prop="dictType">
          <el-input
            v-model="typeForm.dictType"
            placeholder="请输入类型编码（英文）"
            :disabled="!!typeForm.id"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="typeForm.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="typeForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="typeSubmitLoading" @click="handleTypeSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 字典数据对话框 -->
    <el-dialog
      v-model="dataDialogVisible"
      :title="dataDialogTitle"
      width="500px"
      @close="handleDataDialogClose"
    >
      <el-form
        ref="dataFormRef"
        :model="dataForm"
        :rules="dataRules"
        label-width="100px"
      >
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" placeholder="请输入字典标签" />
        </el-form-item>
        <el-form-item label="字典值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" placeholder="请输入字典值" />
        </el-form-item>
        <el-form-item label="排序" prop="dictSort">
          <el-input-number v-model="dataForm.dictSort" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="dataForm.status">
            <el-radio :value="0">正常</el-radio>
            <el-radio :value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="dataForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dataSubmitLoading" @click="handleDataSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getDictTypeList,
  createDictType,
  updateDictType,
  deleteDictType,
  getDictDataList,
  createDictData,
  updateDictData,
  deleteDictData,
  type DictType,
  type DictData
} from '@/api/dict'
import { handleError } from '@/utils/errorHandler'

const typeLoading = ref(false)
const dataLoading = ref(false)
const typeSubmitLoading = ref(false)
const dataSubmitLoading = ref(false)

const typeList = ref<DictType[]>([])
const dataList = ref<DictData[]>([])
const currentType = ref<DictType | null>(null)
const typeSearchText = ref('')

const typeDialogVisible = ref(false)
const dataDialogVisible = ref(false)
const typeDialogTitle = ref('')
const dataDialogTitle = ref('')

const typeFormRef = ref<FormInstance>()
const dataFormRef = ref<FormInstance>()

const defaultTypeForm = (): DictType => ({
  id: undefined,
  dictName: '',
  dictType: '',
  status: 0,
  remark: ''
})

const defaultDataForm = (): DictData => ({
  id: undefined,
  dictType: '',
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  status: 0,
  remark: ''
})

const typeForm = reactive<DictType>(defaultTypeForm())
const dataForm = reactive<DictData>(defaultDataForm())

const validateDictType = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && !/^[a-zA-Z_]+$/.test(value)) {
    callback(new Error('类型编码只能包含英文字母和下划线'))
  } else {
    callback()
  }
}

const typeRules: FormRules = {
  dictName: [
    { required: true, message: '请输入字典名称', trigger: 'blur' }
  ],
  dictType: [
    { required: true, message: '请输入类型编码', trigger: 'blur' },
    { validator: validateDictType, trigger: 'blur' }
  ]
}

const dataRules: FormRules = {
  dictLabel: [
    { required: true, message: '请输入字典标签', trigger: 'blur' }
  ],
  dictValue: [
    { required: true, message: '请输入字典值', trigger: 'blur' }
  ]
}

const filteredTypeList = computed(() => {
  if (!typeSearchText.value) {
    return typeList.value
  }
  const searchLower = typeSearchText.value.toLowerCase()
  return typeList.value.filter(
    item =>
      item.dictName.toLowerCase().includes(searchLower) ||
      item.dictType.toLowerCase().includes(searchLower)
  )
})

const loadTypeList = async () => {
  typeLoading.value = true
  try {
    const res = await getDictTypeList()
    typeList.value = (res as DictType[]) || []
  } catch (error) {
    handleError(error, '加载字典类型失败')
  } finally {
    typeLoading.value = false
  }
}

const loadDataList = async (dictType: string) => {
  dataLoading.value = true
  try {
    const res = await getDictDataList(dictType)
    dataList.value = (res as DictData[]) || []
  } catch (error) {
    handleError(error, '加载字典数据失败')
  } finally {
    dataLoading.value = false
  }
}

const handleTypeSelect = (row: DictType | null) => {
  currentType.value = row
  if (row) {
    loadDataList(row.dictType)
  } else {
    dataList.value = []
  }
}

const handleAddType = () => {
  typeDialogTitle.value = '新增字典类型'
  resetTypeForm()
  typeDialogVisible.value = true
}

const handleEditType = (row: DictType) => {
  typeDialogTitle.value = '编辑字典类型'
  Object.assign(typeForm, JSON.parse(JSON.stringify(row)))
  typeDialogVisible.value = true
}

const handleDeleteType = (row: DictType) => {
  ElMessageBox.confirm('确定要删除这个字典类型吗？删除后相关数据也会被删除！', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteDictType(row.id!)
      ElMessage.success('删除成功')
      if (currentType.value?.id === row.id) {
        currentType.value = null
        dataList.value = []
      }
      loadTypeList()
    } catch (error) {
      handleError(error, '删除失败')
    }
  })
}

const handleTypeSubmit = async () => {
  if (!typeFormRef.value) return

  await typeFormRef.value.validate(async (valid) => {
    if (valid) {
      typeSubmitLoading.value = true
      try {
        if (typeForm.id) {
          await updateDictType(typeForm.id, typeForm)
          ElMessage.success('更新成功')
        } else {
          await createDictType(typeForm)
          ElMessage.success('创建成功')
        }
        typeDialogVisible.value = false
        loadTypeList()
      } catch (error) {
        handleError(error, '提交失败')
      } finally {
        typeSubmitLoading.value = false
      }
    }
  })
}

const handleAddData = () => {
  if (!currentType.value) return
  dataDialogTitle.value = '新增字典数据'
  resetDataForm()
  dataForm.dictType = currentType.value.dictType
  dataDialogVisible.value = true
}

const handleEditData = (row: DictData) => {
  dataDialogTitle.value = '编辑字典数据'
  Object.assign(dataForm, JSON.parse(JSON.stringify(row)))
  dataDialogVisible.value = true
}

const handleDeleteData = (row: DictData) => {
  ElMessageBox.confirm('确定要删除这条字典数据吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteDictData(row.id!)
      ElMessage.success('删除成功')
      if (currentType.value) {
        loadDataList(currentType.value.dictType)
      }
    } catch (error) {
      handleError(error, '删除失败')
    }
  })
}

const handleDataSubmit = async () => {
  if (!dataFormRef.value) return

  await dataFormRef.value.validate(async (valid) => {
    if (valid) {
      dataSubmitLoading.value = true
      try {
        if (dataForm.id) {
          await updateDictData(dataForm.id, dataForm)
          ElMessage.success('更新成功')
        } else {
          await createDictData(dataForm)
          ElMessage.success('创建成功')
        }
        dataDialogVisible.value = false
        if (currentType.value) {
          loadDataList(currentType.value.dictType)
        }
      } catch (error) {
        handleError(error, '提交失败')
      } finally {
        dataSubmitLoading.value = false
      }
    }
  })
}

const handleTypeDialogClose = () => {
  typeFormRef.value?.resetFields()
  resetTypeForm()
}

const handleDataDialogClose = () => {
  dataFormRef.value?.resetFields()
  resetDataForm()
}

const resetTypeForm = () => {
  Object.assign(typeForm, defaultTypeForm())
}

const resetDataForm = () => {
  Object.assign(dataForm, defaultDataForm())
}

onMounted(() => {
  loadTypeList()
})
</script>

<style scoped lang="scss">
.dict-management {
  .dict-type-section,
  .dict-data-section {
    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 15px;

      h3 {
        margin: 0;
        font-size: 16px;
        font-weight: 600;
      }
    }
  }

  .dict-type-section {
    border-right: 1px solid #ebeef5;
    padding-right: 20px;
  }

  .dict-data-section {
    padding-left: 20px;
  }
}
</style>

