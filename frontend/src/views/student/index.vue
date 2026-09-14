<template>
  <div class="student-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="学号">
          <el-input
            v-model="searchForm.studentNo"
            placeholder="请输入学号"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input
            v-model="searchForm.name"
            placeholder="请输入姓名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="searchForm.classId" placeholder="请选择班级" clearable>
            <el-option
              v-for="item in classList"
              :key="item.id"
              :label="item.className"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
            <el-option label="在读" :value="1" />
            <el-option label="离校" :value="0" />
            <el-option label="休学" :value="2" />
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
      >
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="gender" label="性别" width="80">
          <template #default="{ row }">
            {{ (row as Student).gender === 1 ? '男' : (row as Student).gender === 2 ? '女' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="schoolName" label="学校" min-width="150" />
        <!-- ponytail: classes 是数组，取第一个班级名显示 -->
        <el-table-column label="班级" width="120">
          <template #default="{ row }">
            {{ (row as Student).classes?.[0]?.className || '-' }}
          </template>
        </el-table-column>
        <!-- ponytail: status 0=离校, 1=在读, 2=休学 -->
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusType((row as Student).status)">
              {{ statusLabel((row as Student).status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as Student)">
              编辑
            </el-button>
            <el-button link type="warning" :icon="Promotion" @click="handleAssignClass(row as Student)">
              分班
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as Student)">
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
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="form.studentNo" placeholder="请输入学号" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="学校" prop="schoolId">
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
        <el-form-item label="出生日期" prop="birthDate">
          <el-date-picker
            v-model="form.birthDate"
            type="date"
            placeholder="请选择出生日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item label="入学日期" prop="enrollmentDate">
          <el-date-picker
            v-model="form.enrollmentDate"
            type="date"
            placeholder="请选择入学日期"
            style="width: 100%"
            value-format="YYYY-MM-DD"
          />
        </el-form-item>
        <el-form-item v-if="isEdit" label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="在读" :value="1" />
            <el-option label="离校" :value="0" />
            <el-option label="休学" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 分班对话框 -->
    <el-dialog
      v-model="assignDialogVisible"
      title="分配班级"
      width="400px"
    >
      <el-form label-width="80px">
        <el-form-item label="学生">
          <el-input :value="currentStudent?.name" disabled />
        </el-form-item>
        <el-form-item label="选择班级">
          <el-select v-model="assignClassId" placeholder="请选择班级" style="width: 100%">
            <el-option
              v-for="item in classList"
              :key="item.id"
              :label="item.className"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignClassSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Promotion } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getStudentList,
  createStudent,
  updateStudent,
  deleteStudent,
  assignClass,
  type Student
} from '@/api/student'
import { getSchoolList, type School } from '@/api/school'
import { getClassList, type Class } from '@/api/class'

type TableData = Student

const STATUS_LABEL: Record<number, string> = { 0: '离校', 1: '在读', 2: '休学' }
const STATUS_TYPE: Record<number, string> = { 0: 'info', 1: 'success', 2: 'warning' }

const statusLabel = (s?: number) => s !== undefined ? (STATUS_LABEL[s] ?? '-') : '-'
const statusType = (s?: number): '' | 'success' | 'warning' | 'info' | 'danger' =>
  (s !== undefined ? STATUS_TYPE[s] : 'info') as '' | 'success' | 'warning' | 'info' | 'danger'

const loading = ref(false)
const submitLoading = ref(false)

const searchForm = reactive({
  studentNo: '',
  name: '',
  classId: undefined as number | undefined,
  status: undefined as number | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref<TableData[]>([])
const schoolList = ref<School[]>([])
const classList = ref<Class[]>([])
const dialogVisible = ref(false)
const assignDialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const currentStudent = ref<Student | null>(null)
const assignClassId = ref<number>()

const defaultForm = (): Student => ({
  id: undefined,
  studentNo: '',
  schoolId: 0,
  name: '',
  gender: 1,
  birthDate: undefined,
  enrollmentDate: undefined,
  status: 1
})

const form = reactive<Student>(defaultForm())

const isEdit = computed(() => !!form.id)

const rules: FormRules = {
  studentNo: [{ required: true, message: '请输入学号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  schoolId: [{ required: true, message: '请选择学校', trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getStudentList({
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

const loadClassList = async () => {
  try {
    const res = await getClassList({ pageNum: 1, pageSize: 1000 }) as { records: Class[] }
    classList.value = res.records || []
  } catch {
    ElMessage.error('加载班级列表失败')
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  Object.assign(searchForm, { studentNo: '', name: '', classId: undefined, status: undefined })
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增学生'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: Student) => {
  dialogTitle.value = '编辑学生'
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const handleDelete = (row: Student) => {
  ElMessageBox.confirm('确定要删除这条记录吗？', '提示', { type: 'warning' }).then(async () => {
    try {
      await deleteStudent(row.id!)
      ElMessage.success('删除成功')
      loadData()
    } catch {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

const handleAssignClass = (row: Student) => {
  currentStudent.value = row
  assignClassId.value = row.classes?.[0]?.classId
  assignDialogVisible.value = true
}

const handleAssignClassSubmit = async () => {
  if (!assignClassId.value) {
    ElMessage.warning('请选择班级')
    return
  }
  try {
    await assignClass(currentStudent.value!.id!, assignClassId.value)
    ElMessage.success('分班成功')
    assignDialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('分班失败')
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (form.id) {
          await updateStudent(form.id, form)
          ElMessage.success('更新成功')
        } else {
          await createStudent(form)
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
  loadClassList()
})
</script>

<style scoped lang="scss">
.student-management {
  .search-form {
    margin-bottom: 20px;
  }
}
</style>
