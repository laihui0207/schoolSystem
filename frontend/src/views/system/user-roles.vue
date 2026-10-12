<template>
  <div class="user-roles">
    <el-row :gutter="16">
      <el-col :span="13">
        <el-card>
          <template #header><span>选择用户</span></template>
          <el-form :inline="true" :model="searchForm" class="search-form">
            <el-form-item label="用户名">
              <el-input v-model="searchForm.username" clearable placeholder="请输入用户名" style="width: 150px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item label="姓名">
              <el-input v-model="searchForm.realName" clearable placeholder="请输入姓名" style="width: 130px" @keyup.enter="handleSearch" />
            </el-form-item>
            <el-form-item v-if="superAdmin" label="学校">
              <el-select v-model="searchForm.schoolId" clearable filterable placeholder="全部" style="width: 180px">
                <el-option v-for="s in schoolList" :key="s.id" :label="s.schoolName" :value="s.id!" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loading" :data="users" highlight-current-row stripe border @current-change="handleSelectUser">
            <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="realName" label="姓名" min-width="100" show-overflow-tooltip />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ userTypeLabel(row.userType) }}</template>
            </el-table-column>
            <el-table-column prop="schoolName" label="学校" min-width="140" show-overflow-tooltip />
          </el-table>

          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 16px"
            @size-change="handleSearch"
            @current-change="loadUsers"
          />
        </el-card>
      </el-col>

      <el-col :span="11">
        <el-card>
          <template #header><span>附加角色</span></template>
          <el-empty v-if="!selectedUser" description="请在左侧选择用户" />
          <template v-else>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="用户名">{{ selectedUser.username }}</el-descriptions-item>
              <el-descriptions-item label="姓名">{{ selectedUser.realName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="基础角色" :span="2">
                <el-tag v-for="r in builtinRoles" :key="r.id" type="warning" style="margin-right: 6px">
                  {{ r.roleName }}（{{ r.roleCode }}）
                </el-tag>
                <span v-if="!builtinRoles.length" class="muted">-</span>
              </el-descriptions-item>
            </el-descriptions>
            <div class="tip">基础角色由用户类型决定，在「用户管理」修改；这里只能叠加自定义角色，权限点取并集。</div>

            <el-checkbox-group v-model="selectedCodes" v-loading="rolesLoading" class="role-list">
              <el-checkbox v-for="r in assignableRoles" :key="r.id" :value="r.roleCode" border>
                {{ r.roleName }}
                <span class="muted">{{ r.roleCode }}</span>
                <span v-if="!r.schoolId" class="muted">（平台级）</span>
              </el-checkbox>
            </el-checkbox-group>
            <el-empty
              v-if="!assignableRoles.length && !rolesLoading"
              description="没有可分配的自定义角色，请先在「角色管理」中创建"
              :image-size="60"
            />

            <div class="actions">
              <el-button v-permission="'sys:user:assign-role'" type="primary" :loading="saving" @click="handleSave">保存</el-button>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getUserList } from '@/api/user'
import { getSchoolList, type School } from '@/api/school'
import { getRoleList, getUserRoles, updateUserRoles, isSuperAdmin, type Role } from '@/api/rbac'
import { useUserStore } from '@/stores/user'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'

/** 用户列表行（后端 UserResponse 子集；api/user.ts 的 User 缺 userType，这里单独声明） */
interface UserRow {
  id: number
  username: string
  realName?: string
  userType?: number
  schoolId?: number
  schoolName?: string
}

const USER_TYPE_LABELS: Record<number, string> = { 1: '超级管理员', 2: '学校管理员', 3: '教师', 4: '学生', 5: '家长' }
const userTypeLabel = (t?: number) => (t && USER_TYPE_LABELS[t]) || '-'

const userStore = useUserStore()
const superAdmin = computed(() => isSuperAdmin())
const mySchoolId = computed(() => userStore.userInfo?.schoolId)

const loading = ref(false)
const rolesLoading = ref(false)
const saving = ref(false)
const users = ref<UserRow[]>([])
const schoolList = ref<School[]>([])
const allCustomRoles = ref<Role[]>([])
const selectedUser = ref<UserRow | null>(null)
const userRoles = ref<Role[]>([])
const selectedCodes = ref<string[]>([])

const searchForm = reactive<{ username: string; realName: string; schoolId?: number }>({ username: '', realName: '', schoolId: undefined })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const builtinRoles = computed(() => userRoles.value.filter(r => r.isBuiltin === 1))

/** 与后端 UserRoleServiceImpl.assertAssignable 一致：超管=平台级或同校；校管理员=仅本校自建 */
const assignableRoles = computed(() => {
  const user = selectedUser.value
  if (!user) return []
  return allCustomRoles.value.filter(r =>
    superAdmin.value ? (!r.schoolId || r.schoolId === user.schoolId) : (!!r.schoolId && r.schoolId === mySchoolId.value)
  )
})

const loadUsers = async () => {
  loading.value = true
  try {
    const page = await getUserList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      username: searchForm.username || undefined,
      realName: searchForm.realName || undefined,
      schoolId: superAdmin.value ? searchForm.schoolId : undefined
    }) as PageResponse<UserRow>
    users.value = page.records ?? []
    pagination.total = page.total ?? 0
  } catch (error) {
    handleError(error, '加载用户列表失败')
  } finally {
    loading.value = false
  }
}
const handleSearch = () => { pagination.pageNum = 1; loadUsers() }

const loadCustomRoles = async () => {
  try {
    // ponytail: 自定义角色数量很少，一次取满避免分页；内置角色由后端按 user_type 保留，这里不展示为可选项
    const page = await getRoleList({ pageNum: 1, pageSize: 500, isBuiltin: 0 })
    allCustomRoles.value = page.records ?? []
  } catch (error) {
    handleError(error, '加载角色列表失败')
  }
}

const handleSelectUser = async (row: UserRow | null) => {
  selectedUser.value = row
  userRoles.value = []
  selectedCodes.value = []
  if (!row) return
  rolesLoading.value = true
  try {
    userRoles.value = (await getUserRoles(row.id)) ?? []
    selectedCodes.value = userRoles.value.filter(r => r.isBuiltin !== 1).map(r => r.roleCode)
  } catch (error) {
    handleError(error, '加载用户角色失败')
  } finally {
    rolesLoading.value = false
  }
}

const handleSave = async () => {
  if (!selectedUser.value) return
  saving.value = true
  try {
    userRoles.value = (await updateUserRoles(selectedUser.value.id, selectedCodes.value)) ?? []
    selectedCodes.value = userRoles.value.filter(r => r.isBuiltin !== 1).map(r => r.roleCode)
    ElMessage.success('用户角色已更新')
  } catch (error) {
    handleError(error)
  } finally {
    saving.value = false
  }
}

const loadSchools = async () => {
  if (!superAdmin.value) return
  try {
    const res = await getSchoolList({ pageNum: 1, pageSize: 1000 }) as PageResponse<School>
    schoolList.value = res.records || []
  } catch (error) {
    handleError(error, '加载学校列表失败')
  }
}

onMounted(() => { loadUsers(); loadCustomRoles(); loadSchools() })
</script>

<style scoped lang="scss">
.user-roles { padding: 20px; }
.search-form { margin-bottom: 12px; }
.tip { margin: 12px 0; color: var(--el-text-color-secondary); font-size: 13px; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; margin-left: 4px; }
.role-list { display: flex; flex-direction: column; gap: 10px; min-height: 60px; }
.actions { margin-top: 16px; text-align: right; }
</style>
