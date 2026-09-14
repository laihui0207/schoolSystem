<template>
  <div class="subject-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="科目名称">
          <el-input
            v-model="searchForm.subjectName"
            placeholder="请输入科目名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="科目编码">
          <el-input
            v-model="searchForm.subjectCode"
            placeholder="请输入科目编码"
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
              :value="item.id"
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
        <el-table-column prop="subjectName" label="科目名称" min-width="120" />
        <el-table-column prop="subjectCode" label="科目编码" width="120" />
        <!-- ponytail: SubjectResponse 无 schoolName，前端用已加载的 schoolList 映射 -->
        <el-table-column label="所属学校" min-width="150">
          <template #default="{ row }">{{ schoolNameOf((row as Subject).schoolId) }}</template>
        </el-table-column>
        <el-table-column prop="fullScore" label="满分" width="100" />
        <el-table-column prop="passScore" label="及格分" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as Subject)">
              编辑
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as Subject)">
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
        @current-change="handleSearch"
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
        <el-form-item label="科目名称" prop="subjectName">
          <el-input v-model="form.subjectName" placeholder="请输入科目名称" />
        </el-form-item>
        <el-form-item label="科目编码" prop="subjectCode">
          <el-input
            v-model="form.subjectCode"
            placeholder="大写字母和数字，如 MATH01"
            :disabled="isEdit"
          />
        </el-form-item>
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
        <el-form-item label="满分" prop="fullScore">
          <el-input-number
            v-model="form.fullScore"
            :min="1"
            :max="1000"
            :precision="1"
            placeholder="请输入满分"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="及格分" prop="passScore">
          <el-input-number
            v-model="form.passScore"
            :min="0"
            :max="form.fullScore || 1000"
            :precision="1"
            placeholder="请输入及格分"
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
  getSubjectList,
  createSubject,
  updateSubject,
  deleteSubject,
  type Subject
} from '@/api/subject'
import { getSchoolList, type School } from '@/api/school'

type TableData = Subject

const loading = ref(false)
const submitLoading = ref(false)

const searchForm = reactive({
  subjectName: '',
  subjectCode: '',
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

const defaultForm = (): Subject => ({
  id: undefined,
  subjectName: '',
  subjectCode: '',
  fullScore: undefined,
  passScore: undefined,
  schoolId: undefined
})

const form = reactive<Subject>(defaultForm())

const isEdit = computed(() => !!form.id)

const schoolNameOf = (id?: number) =>
  schoolList.value.find((s) => s.id === id)?.schoolName || '-'

const validatePassScore = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
  if (form.passScore !== undefined && form.fullScore !== undefined && form.passScore > form.fullScore) {
    callback(new Error('及格分不能大于满分'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  subjectName: [{ required: true, message: '请输入科目名称', trigger: 'blur' }],
  subjectCode: [
    { required: true, message: '请输入科目编码', trigger: 'blur' },
    { pattern: /^[A-Z0-9]+$/, message: '科目编码只能包含大写字母和数字', trigger: 'blur' }
  ],
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }],
  fullScore: [{ required: true, message: '请输入满分', trigger: 'blur' }],
  passScore: [
    { required: true, message: '请输入及格分', trigger: 'blur' },
    { validator: validatePassScore, trigger: 'blur' }
  ]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getSubjectList({
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    tableData.value = res.records || []
    pagination.total = res.total || 0
  } catch {
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const loadSchoolList = async () => {
  try {
    const res = await getSchoolList({ pageNum: 1, pageSize: 1000 }) as { records: School[] }
    schoolList.value = res.records || []
  } catch {
    ElMessage.error('加载学校列表失败')
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { subjectName: '', subjectCode: '', schoolId: undefined })
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增科目'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Subject) => {
  dialogTitle.value = '编辑科目'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const handleDelete = (row: Subject) => {
  ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteSubject(row.id!)
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
          await updateSubject(form.id, form)
          ElMessage.success('更新成功')
        } else {
          await createSubject(form)
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
.subject-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
