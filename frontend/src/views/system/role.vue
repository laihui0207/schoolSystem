<template>
  <div class="role-management">
    <el-card>
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="角色编码">
          <el-input v-model="searchForm.roleCode" placeholder="如 head_teacher" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="searchForm.roleName" placeholder="请输入角色名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="searchForm.isBuiltin" placeholder="全部" clearable style="width: 120px">
            <el-option label="内置" :value="1" />
            <el-option label="自定义" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="superAdmin" label="学校">
          <el-select v-model="searchForm.schoolId" placeholder="全部" clearable filterable style="width: 200px">
            <el-option v-for="s in schoolList" :key="s.id" :label="s.schoolName" :value="s.id!" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          <el-button v-permission="'sys:role:add'" type="success" :icon="Plus" @click="handleAdd">新增</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleCode" label="角色编码" min-width="170" show-overflow-tooltip />
        <el-table-column prop="roleName" label="角色名称" min-width="130" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
        <el-table-column label="归属" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.schoolId ? (row.schoolName || `学校 ${row.schoolId}`) : '平台级' }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isBuiltin === 1 ? 'warning' : 'info'">{{ row.isBuiltin === 1 ? '内置' : '自定义' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="用户数" width="90" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleAssign(row as Role)">分配权限</el-button>
            <el-button v-if="canManage(row as Role)" link type="primary" @click="handleEdit(row as Role)">编辑</el-button>
            <el-button v-if="canManage(row as Role) && row.isBuiltin !== 1" link type="danger" @click="handleDelete(row as Role)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px"
        @size-change="handleSearch"
        @current-change="loadData"
      />
    </el-card>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" :disabled="editingBuiltin" placeholder="字母开头，如 head_teacher；ROLE_ 前缀保留给内置角色" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item v-if="superAdmin && !form.id" label="归属学校">
          <el-select v-model="form.schoolId" placeholder="不选为平台级角色" clearable filterable style="width: 100%">
            <el-option v-for="s in schoolList" :key="s.id" :label="s.schoolName" :value="s.id!" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限抽屉 -->
    <el-drawer v-model="drawerVisible" :title="`分配权限 - ${currentRole?.roleName ?? ''}`" size="480px">
      <div class="drawer-tip">
        勾选父节点会联动全部子节点。
        <template v-if="!superAdmin">灰色节点是你未持有的权限，无法增删；该角色已有的会原样保留。</template>
      </div>
      <el-tree
        ref="treeRef"
        v-loading="treeLoading"
        :data="permissionTree"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="treeProps"
      >
        <template #default="{ data }">
          <span class="tree-node">
            <span>{{ data.permissionName }}</span>
            <span class="tree-code">{{ data.permissionCode }}</span>
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button v-if="currentRole && canManage(currentRole)" type="primary" :loading="assignLoading" @click="handleAssignSubmit">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree, FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import {
  getRoleList, createRole, updateRole, deleteRole, getRolePermissions, assignRolePermissions,
  getPermissionTree, ensurePermissionsLoaded, hasPermission, isSuperAdmin, clearPermissionCache,
  type Role, type RoleSaveRequest, type PermissionNode
} from '@/api/rbac'
import { getSchoolList, type School } from '@/api/school'
import { useUserStore } from '@/stores/user'
import { handleError } from '@/utils/errorHandler'
import type { PageResponse } from '@/types/common'

/** 树节点：在后端节点上加 disabled（校管理员未持有的权限置灰） */
type TreeNode = Omit<PermissionNode, 'children'> & { disabled?: boolean; children?: TreeNode[] }

interface RoleForm extends RoleSaveRequest { id?: number; isBuiltin?: number }

const userStore = useUserStore()
const superAdmin = computed(() => isSuperAdmin())
const mySchoolId = computed(() => userStore.userInfo?.schoolId)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<Role[]>([])
const schoolList = ref<School[]>([])
const searchForm = reactive<{ roleCode: string; roleName: string; isBuiltin?: number; schoolId?: number }>({
  roleCode: '', roleName: '', isBuiltin: undefined, schoolId: undefined
})
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const defaultForm = (): RoleForm => ({ id: undefined, roleCode: '', roleName: '', description: '', schoolId: undefined, isBuiltin: 0 })
const form = reactive<RoleForm>(defaultForm())
const editingBuiltin = computed(() => !!form.id && form.isBuiltin === 1)
const rules: FormRules = {
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_:-]*$/, message: '字母开头，只能包含字母、数字、下划线、冒号、中划线', trigger: 'blur' }
  ],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const drawerVisible = ref(false)
const treeLoading = ref(false)
const assignLoading = ref(false)
const treeRef = ref<InstanceType<typeof ElTree>>()
const permissionTree = ref<TreeNode[]>([])
const currentRole = ref<Role | null>(null)
const treeProps = { label: 'permissionName', children: 'children', disabled: 'disabled' }

/** 与后端 RoleServiceImpl.assertManageable 一致：超管全部；校管理员只能改本校自建角色 */
const canManage = (row: Role): boolean => superAdmin.value || (!!row.schoolId && row.schoolId === mySchoolId.value)

const loadData = async () => {
  loading.value = true
  try {
    const page = await getRoleList({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      roleCode: searchForm.roleCode || undefined,
      roleName: searchForm.roleName || undefined,
      isBuiltin: searchForm.isBuiltin,
      schoolId: superAdmin.value ? searchForm.schoolId : undefined
    })
    tableData.value = page.records ?? []
    pagination.total = page.total ?? 0
  } catch (error) {
    handleError(error, '加载角色列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.pageNum = 1; loadData() }
const handleReset = () => {
  searchForm.roleCode = ''; searchForm.roleName = ''; searchForm.isBuiltin = undefined; searchForm.schoolId = undefined
  handleSearch()
}

const handleAdd = () => { dialogTitle.value = '新增角色'; Object.assign(form, defaultForm()); dialogVisible.value = true }
const handleEdit = (row: Role) => {
  dialogTitle.value = '编辑角色'
  Object.assign(form, { id: row.id, roleCode: row.roleCode, roleName: row.roleName, description: row.description ?? '', schoolId: row.schoolId ?? undefined, isBuiltin: row.isBuiltin })
  dialogVisible.value = true
}
const handleDialogClose = () => { formRef.value?.resetFields(); Object.assign(form, defaultForm()) }

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload: RoleSaveRequest = { roleName: form.roleName, description: form.description || undefined }
      if (form.id) {
        // 内置角色编码不可改，不传即不改
        if (!editingBuiltin.value) payload.roleCode = form.roleCode
        await updateRole(form.id, payload)
      } else {
        payload.roleCode = form.roleCode
        payload.schoolId = superAdmin.value ? form.schoolId ?? null : undefined
        await createRole(payload)
      }
      ElMessage.success(form.id ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadData()
    } catch (error) {
      handleError(error)
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = async (row: Role) => {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？仍有用户绑定时会被拒绝。`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  }
}

const decorate = (nodes: PermissionNode[]): TreeNode[] =>
  nodes.map(n => ({ ...n, disabled: !hasPermission(n.permissionCode), children: n.children ? decorate(n.children) : [] }))

/**
 * 回显勾选集合：只勾叶子，父节点由 el-tree 从子节点推导半选/全选。
 * ponytail: 绝不直接勾选有子节点的父节点——el-tree 非 check-strictly 模式会级联勾上全部子节点，
 * 一个只分配了 score:manage 菜单、没分配任何子权限的角色，保存时就会被悄悄放大成全部成绩权限。
 * 代价是「只有菜单、没有子权限」的孤立父节点保存后会被丢弃，这类节点本身没有鉴权意义，可接受。
 */
const collectChecked = (nodes: TreeNode[], assigned: Set<number>): number[] =>
  nodes.flatMap(n => (n.children?.length ? collectChecked(n.children, assigned) : (assigned.has(n.id) ? [n.id] : [])))

const handleAssign = async (row: Role) => {
  currentRole.value = row
  drawerVisible.value = true
  treeLoading.value = true
  try {
    await ensurePermissionsLoaded()
    const [tree, assigned] = await Promise.all([getPermissionTree(), getRolePermissions(row.id)])
    permissionTree.value = decorate(tree ?? [])
    const assignedIds = new Set((assigned ?? []).map(p => p.id))
    await nextTick()
    treeRef.value?.setCheckedKeys(collectChecked(permissionTree.value, assignedIds))
  } catch (error) {
    handleError(error, '加载权限失败')
  } finally {
    treeLoading.value = false
  }
}

const handleAssignSubmit = async () => {
  if (!currentRole.value || !treeRef.value) return
  assignLoading.value = true
  try {
    // 全选 + 半选（父菜单）一起提交，保证菜单节点随子权限一并分配
    const ids = [...(treeRef.value.getCheckedKeys(false) as number[]), ...(treeRef.value.getHalfCheckedKeys() as number[])]
    await assignRolePermissions(currentRole.value.id, ids)
    ElMessage.success('权限已更新')
    clearPermissionCache()
    drawerVisible.value = false
  } catch (error) {
    handleError(error)
  } finally {
    assignLoading.value = false
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

onMounted(() => { loadData(); loadSchools() })
</script>

<style scoped lang="scss">
.role-management { padding: 20px; }
.search-form { margin-bottom: 16px; }
.drawer-tip { margin-bottom: 12px; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
.tree-node { display: flex; gap: 12px; align-items: baseline; }
.tree-code { color: var(--el-text-color-placeholder); font-size: 12px; font-family: monospace; }
</style>
