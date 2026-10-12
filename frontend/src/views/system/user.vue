<template>
  <div class="user-management">
    <el-card>
      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="用户名">
          <el-input
            v-model="searchForm.username"
            placeholder="请输入用户名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input
            v-model="searchForm.realName"
            placeholder="请输入姓名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="searchForm.role" placeholder="请选择角色" clearable>
            <el-option label="全部" value="" />
            <el-option label="管理员" value="admin" />
            <el-option label="教师" value="teacher" />
            <el-option label="学生" value="student" />
            <el-option label="家长" value="parent" />
          </el-select>
        </el-form-item>
        <el-form-item label="学校">
          <el-select
            v-model="searchForm.schoolId"
            placeholder="请选择学校"
            clearable
            filterable
          >
            <el-option
              v-for="school in schoolList"
              :key="school.id"
              :label="school.schoolName"
              :value="school.id!"
            />
          </el-select>
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
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="getRoleType(row.role)">
              {{ getRoleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="schoolName" label="学校" min-width="150" show-overflow-tooltip />
        <el-table-column prop="phone" label="联系方式" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row as User)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="handleEdit(row as User)">
              编辑
            </el-button>
            <!-- ponytail: 家长行多一个「绑定学生」，走 /parents/{id}/children；其他角色不显示 -->
            <el-button
              v-if="row.role === 'parent'"
              link
              type="success"
              :icon="Link"
              @click="handleBindChildren(row as User)"
            >
              绑定学生
            </el-button>
            <el-button link type="warning" :icon="Key" @click="handleResetPassword(row as User)">
              重置密码
            </el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row as User)">
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
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :disabled="!!form.id"
          />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio value="admin">管理员</el-radio>
            <el-radio value="teacher">教师</el-radio>
            <el-radio value="student">学生</el-radio>
            <el-radio value="parent">家长</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="学校" prop="schoolId">
          <el-select
            v-model="form.schoolId"
            placeholder="请选择学校"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="school in schoolList"
              :key="school.id"
              :label="school.schoolName"
              :value="school.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
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

    <!-- 重置密码对话框 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="重置密码"
      width="400px"
      @close="handlePasswordDialogClose"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="100px"
      >
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="passwordForm.password"
            type="password"
            placeholder="请输入新密码（至少6位）"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handlePasswordSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 家长绑定学生对话框 -->
    <!-- ponytail: 按学号搜索 /students?studentNo=（后端 LIKE 匹配），选中后 POST /parents/{id}/children；关系取字典 relationship -->
    <el-dialog
      v-model="bindDialogVisible"
      :title="`绑定学生 - ${bindParent?.realName ?? ''}`"
      width="680px"
    >
      <el-form :inline="true" class="bind-form">
        <el-form-item label="学号">
          <el-input
            v-model="bindSearchNo"
            placeholder="输入学号搜索"
            clearable
            style="width: 180px"
            @keyup.enter="handleBindSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="bindSearching" @click="handleBindSearch">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-form :inline="true" class="bind-form">
        <el-form-item label="学生">
          <el-select
            v-model="bindStudentId"
            placeholder="请先搜索学号"
            style="width: 260px"
            :disabled="bindCandidates.length === 0"
          >
            <el-option
              v-for="s in bindCandidates"
              :key="s.id"
              :label="`${s.studentNo} - ${s.name}`"
              :value="s.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关系">
          <el-select v-model="bindRelationship" style="width: 120px">
            <el-option
              v-for="o in relationshipOptions"
              :key="String(o.value)"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="success" :loading="bindSubmitting" :disabled="!bindStudentId" @click="handleBindSubmit">
            绑定
          </el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="bindLoading" :data="bindChildren" border stripe size="small">
        <el-table-column prop="studentNo" label="学号" width="140" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="className" label="班级" min-width="120">
          <template #default="{ row }">{{ row.className || '未分班' }}</template>
        </el-table-column>
        <el-table-column prop="relationshipName" label="关系" width="90" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleUnbind(row as ChildBrief)">解绑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该家长尚未绑定学生" :image-size="60" />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="bindDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Edit, Delete, Key, Link } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getUserList,
  createUser,
  updateUser,
  deleteUser,
  updateUserStatus,
  resetPassword,
  type User,
  type UserQuery
} from '@/api/user'
import { getSchoolList, type School } from '@/api/school'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'
import { getParentChildren, bindChild, unbindChild, type ChildBrief } from '@/api/parent'
import { getStudentList, type Student } from '@/api/student'
import { useDict } from '@/composables/useDict'

const loading = ref(false)
const submitLoading = ref(false)
const passwordLoading = ref(false)

const searchForm = reactive<Partial<UserQuery>>({
  username: '',
  realName: '',
  role: '',
  schoolId: undefined,
  status: undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const tableData = ref<User[]>([])
const schoolList = ref<School[]>([])
const dialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()

const defaultForm = (): User => ({
  id: undefined,
  username: '',
  realName: '',
  role: 'student',
  schoolId: undefined,
  phone: '',
  email: '',
  password: '',
  status: 1
})

const form = reactive<User>(defaultForm())

const passwordForm = reactive({
  userId: 0,
  password: '',
  confirmPassword: ''
})

const validatePhone = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && !/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

const validateEmail = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    callback(new Error('请输入正确的邮箱'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== passwordForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  phone: [
    { validator: validatePhone, trigger: 'blur' }
  ],
  email: [
    { validator: validateEmail, trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为6位', trigger: 'blur' }
  ]
}

const passwordRules: FormRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const getRoleType = (role: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const typeMap: Record<string, string> = {
    admin: 'danger',
    teacher: 'warning',
    student: 'success',
    parent: 'info'
  }
  return (typeMap[role] as 'success' | 'warning' | 'info' | 'danger') || undefined
}

const getRoleLabel = (role: string) => {
  const labelMap: Record<string, string> = {
    admin: '管理员',
    teacher: '教师',
    student: '学生',
    parent: '家长'
  }
  return labelMap[role] || role
}

const loadSchoolList = async () => {
  try {
    const res = await getSchoolList({ pageNum: 1, pageSize: 1000 }) as PageResponse<School>
    schoolList.value = res.records || []
  } catch (error) {
    handleError(error, '加载学校列表失败')
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const params: UserQuery = {
      ...searchForm,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    const res = await getUserList(params) as PageResponse<User>
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
  searchForm.username = ''
  searchForm.realName = ''
  searchForm.role = ''
  searchForm.schoolId = undefined
  searchForm.status = undefined
  handleSearch()
}

const handleAdd = () => {
  dialogTitle.value = '新增用户'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row: User) => {
  dialogTitle.value = '编辑用户'
  Object.assign(form, JSON.parse(JSON.stringify(row)))
  dialogVisible.value = true
}

const handleDelete = (row: User) => {
  ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    try {
      await deleteUser(row.id!)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      handleError(error, '删除失败')
    }
  })
}

const handleStatusChange = async (row: User) => {
  const previousStatus = row.status
  try {
    await updateUserStatus(row.id!, row.status)
    ElMessage.success('状态更新成功')
  } catch (error) {
    handleError(error, '状态更新失败')
    row.status = previousStatus
  }
}

const handleResetPassword = (row: User) => {
  passwordForm.userId = row.id!
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (form.id) {
          await updateUser(form.id, form)
          ElMessage.success('更新成功')
        } else {
          await createUser(form)
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

const handlePasswordSubmit = async () => {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (valid) {
      passwordLoading.value = true
      try {
        await resetPassword(passwordForm.userId, passwordForm.password)
        ElMessage.success('密码重置成功')
        passwordDialogVisible.value = false
      } catch (error) {
        handleError(error, '密码重置失败')
      } finally {
        passwordLoading.value = false
      }
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  resetForm()
}

const handlePasswordDialogClose = () => {
  passwordFormRef.value?.resetFields()
  passwordForm.userId = 0
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
}

const resetForm = () => {
  Object.assign(form, defaultForm())
}

// ---------------------------------------------------------------- 家长绑定学生

// ponytail: 关系取字典 relationship（V17 种子 father/mother/other），字典缺失时回落同样三项
const { options: relationshipOptions } = useDict('relationship', [
  { label: '父亲', value: 'father' },
  { label: '母亲', value: 'mother' },
  { label: '其他', value: 'other' }
])

const bindDialogVisible = ref(false)
const bindParent = ref<User | null>(null)
const bindChildren = ref<ChildBrief[]>([])
const bindLoading = ref(false)
const bindSearchNo = ref('')
const bindSearching = ref(false)
const bindCandidates = ref<Student[]>([])
const bindStudentId = ref<number>()
const bindRelationship = ref<string | number>('other')
const bindSubmitting = ref(false)

const loadBindChildren = async () => {
  if (!bindParent.value?.id) return
  bindLoading.value = true
  try {
    bindChildren.value = await getParentChildren(bindParent.value.id)
  } catch (error) {
    handleError(error, '加载已绑定学生失败')
  } finally {
    bindLoading.value = false
  }
}

const handleBindChildren = (row: User) => {
  bindParent.value = row
  bindSearchNo.value = ''
  bindCandidates.value = []
  bindStudentId.value = undefined
  bindRelationship.value = 'other'
  bindDialogVisible.value = true
  loadBindChildren()
}

const handleBindSearch = async () => {
  const studentNo = bindSearchNo.value.trim()
  if (!studentNo) {
    ElMessage.warning('请输入学号')
    return
  }
  bindSearching.value = true
  try {
    // ponytail: 超管建的家长归属某校，按家长 schoolId 限定搜索；校管理员由租户插件自动限本校，后端再做同校校验
    const res = await getStudentList({
      studentNo,
      schoolId: bindParent.value?.schoolId,
      pageNum: 1,
      pageSize: 20
    })
    bindCandidates.value = res.records || []
    bindStudentId.value = bindCandidates.value[0]?.id
    if (bindCandidates.value.length === 0) {
      ElMessage.info('未找到匹配学号的学生')
    }
  } catch (error) {
    handleError(error, '搜索学生失败')
  } finally {
    bindSearching.value = false
  }
}

const handleBindSubmit = async () => {
  if (!bindParent.value?.id || !bindStudentId.value) return
  bindSubmitting.value = true
  try {
    await bindChild(bindParent.value.id, bindStudentId.value, String(bindRelationship.value))
    ElMessage.success('绑定成功')
    bindStudentId.value = undefined
    bindCandidates.value = []
    bindSearchNo.value = ''
    loadBindChildren()
  } catch (error) {
    handleError(error, '绑定失败')
  } finally {
    bindSubmitting.value = false
  }
}

const handleUnbind = (row: ChildBrief) => {
  ElMessageBox.confirm(`确定解除与 ${row.name ?? row.studentNo ?? row.studentId} 的绑定吗？`, '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  }).then(async () => {
    if (!bindParent.value?.id) return
    try {
      await unbindChild(bindParent.value.id, row.studentId)
      ElMessage.success('已解绑')
      loadBindChildren()
    } catch (error) {
      handleError(error, '解绑失败')
    }
  })
}

onMounted(() => {
  loadSchoolList()
  loadData()
})
</script>

<style scoped lang="scss">
.user-management {
  .search-form {
    margin-bottom: 20px;
  }
}

.bind-form {
  margin-bottom: 8px;
}
</style>

