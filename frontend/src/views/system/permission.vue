<template>
  <div class="permission-management">
    <el-card>
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="按编码 / 名称过滤" clearable style="width: 260px" />
        <div>
          <el-button :icon="Refresh" @click="loadTree">刷新</el-button>
          <el-button v-if="superAdmin" type="success" :icon="Plus" @click="handleAdd(null)">新增一级菜单</el-button>
        </div>
      </div>
      <div v-if="!superAdmin" class="tip">权限树只读；增删改仅超级管理员可操作。</div>

      <el-table
        v-loading="loading"
        :data="filteredTree"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
      >
        <el-table-column prop="permissionName" label="名称" min-width="200" />
        <el-table-column prop="permissionCode" label="编码" min-width="220" show-overflow-tooltip>
          <template #default="{ row }"><code class="code">{{ row.permissionCode }}</code></template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.resourceType)" size="small">{{ typeLabel(row.resourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="url" label="URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80" />
        <el-table-column v-if="superAdmin" label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleAdd(row as PermissionNode)">添加子项</el-button>
            <el-button link type="primary" @click="handleEdit(row as PermissionNode)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row as PermissionNode)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="父权限">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            node-key="id"
            :props="{ label: 'permissionName', children: 'children' }"
            check-strictly
            default-expand-all
            clearable
            placeholder="不选为一级菜单"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="编码" prop="permissionCode">
          <el-input v-model="form.permissionCode" :disabled="!!form.id" placeholder="module:action，小写字母/数字/中划线" />
        </el-form-item>
        <el-form-item label="名称" prop="permissionName">
          <el-input v-model="form.permissionName" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="类型" prop="resourceType">
          <el-radio-group v-model="form.resourceType">
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
            <el-radio :value="3">接口</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="URL">
          <el-input v-model="form.url" placeholder="后端路径，仅作展示，如 /roles/{id}" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Refresh, Plus } from '@element-plus/icons-vue'
import {
  getPermissionTree, createPermission, updatePermission, deletePermission, isSuperAdmin, clearPermissionCache,
  type PermissionNode, type PermissionSaveRequest
} from '@/api/rbac'
import { handleError } from '@/utils/errorHandler'

interface PermissionForm extends PermissionSaveRequest { id?: number; originalParentId?: number | null }

const superAdmin = computed(() => isSuperAdmin())
const loading = ref(false)
const submitLoading = ref(false)
const tree = ref<PermissionNode[]>([])
const keyword = ref('')

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const defaultForm = (): PermissionForm => ({
  id: undefined, permissionCode: '', permissionName: '', resourceType: 3, url: '', parentId: undefined, sortOrder: 0, originalParentId: null
})
const form = reactive<PermissionForm>(defaultForm())
const rules: FormRules = {
  permissionCode: [
    { required: true, message: '请输入权限编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)*$/, message: '格式应为 module:action，小写字母/数字/中划线', trigger: 'blur' }
  ],
  permissionName: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  resourceType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const TYPE_LABELS: Record<number, string> = { 1: '菜单', 2: '按钮', 3: '接口' }
const TYPE_TAGS: Record<number, 'primary' | 'success' | 'info'> = { 1: 'primary', 2: 'success', 3: 'info' }
const typeLabel = (t: number) => TYPE_LABELS[t] ?? String(t)
const typeTag = (t: number) => TYPE_TAGS[t] ?? 'info'

/** 关键字过滤：命中节点或有命中后代的节点保留 */
const filterNodes = (nodes: PermissionNode[], kw: string): PermissionNode[] =>
  nodes.flatMap(n => {
    const children = n.children ? filterNodes(n.children, kw) : []
    const hit = n.permissionCode.toLowerCase().includes(kw) || n.permissionName.toLowerCase().includes(kw)
    return hit || children.length ? [{ ...n, children }] : []
  })
const filteredTree = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return kw ? filterNodes(tree.value, kw) : tree.value
})

/** 父权限候选：编辑时排除自身及其子树（后端也会拒绝成环） */
const excludeSubtree = (nodes: PermissionNode[], id: number): PermissionNode[] =>
  nodes.filter(n => n.id !== id).map(n => ({ ...n, children: n.children ? excludeSubtree(n.children, id) : [] }))
const parentOptions = computed(() => (form.id ? excludeSubtree(tree.value, form.id) : tree.value))

const loadTree = async () => {
  loading.value = true
  try {
    tree.value = (await getPermissionTree()) ?? []
  } catch (error) {
    handleError(error, '加载权限树失败')
  } finally {
    loading.value = false
  }
}

const handleAdd = (parent: PermissionNode | null) => {
  dialogTitle.value = parent ? `添加子权限 - ${parent.permissionName}` : '新增一级菜单'
  Object.assign(form, defaultForm(), { parentId: parent?.id, resourceType: parent ? 3 : 1 })
  dialogVisible.value = true
}
const handleEdit = (row: PermissionNode) => {
  dialogTitle.value = '编辑权限'
  Object.assign(form, {
    id: row.id, permissionCode: row.permissionCode, permissionName: row.permissionName, resourceType: row.resourceType,
    url: row.url ?? '', parentId: row.parentId ?? undefined, sortOrder: row.sortOrder ?? 0, originalParentId: row.parentId ?? null
  })
  dialogVisible.value = true
}
const handleDialogClose = () => { formRef.value?.resetFields(); Object.assign(form, defaultForm()) }

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload: PermissionSaveRequest = {
        permissionName: form.permissionName, resourceType: form.resourceType, url: form.url || undefined, sortOrder: form.sortOrder
      }
      if (form.id) {
        // 后端约定：parentId 传 0 表示移到一级；未变化时不传
        const cleared = !form.parentId && form.originalParentId != null
        payload.parentId = cleared ? 0 : (form.parentId !== form.originalParentId ? form.parentId : undefined)
        await updatePermission(form.id, payload)
      } else {
        payload.permissionCode = form.permissionCode
        payload.parentId = form.parentId ?? undefined
        await createPermission(payload)
      }
      ElMessage.success(form.id ? '更新成功' : '创建成功')
      clearPermissionCache()
      dialogVisible.value = false
      loadTree()
    } catch (error) {
      handleError(error)
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = async (row: PermissionNode) => {
  try {
    await ElMessageBox.confirm(
      `确定删除权限「${row.permissionName}」(${row.permissionCode}) 吗？所有角色上的该权限绑定会一并移除。`,
      '提示', { type: 'warning' }
    )
    await deletePermission(row.id)
    ElMessage.success('删除成功')
    clearPermissionCache()
    loadTree()
  } catch (error) {
    if (error !== 'cancel') handleError(error)
  }
}

onMounted(loadTree)
</script>

<style scoped lang="scss">
.permission-management { padding: 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tip { margin-bottom: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.code { font-family: monospace; font-size: 12px; color: var(--el-text-color-regular); }
</style>
