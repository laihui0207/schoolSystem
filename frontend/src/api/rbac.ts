import request from '@/utils/request'
import type { PageQuery, PageResponse } from '@/types/common'
import { useUserStore } from '@/stores/user'

/** 角色（对应后端 RoleResponse） */
export interface Role {
  id: number
  roleCode: string
  roleName: string
  description?: string
  /** null/undefined = 平台级 */
  schoolId?: number | null
  schoolName?: string
  /** 1 内置（不可删、编码不可改） */
  isBuiltin: number
  /** 在用用户数 */
  userCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface RoleQuery extends PageQuery {
  roleCode?: string
  roleName?: string
  schoolId?: number
  isBuiltin?: number
}

export interface RoleSaveRequest {
  roleCode?: string
  roleName: string
  description?: string
  /** 仅超管生效；校管理员固定为本校 */
  schoolId?: number | null
}

/** 权限树节点（对应后端 PermissionTreeNode） */
export interface PermissionNode {
  id: number
  permissionCode: string
  permissionName: string
  /** 1 菜单 2 按钮 3 接口 */
  resourceType: number
  url?: string
  parentId?: number | null
  sortOrder?: number
  children?: PermissionNode[]
}

export interface PermissionSaveRequest {
  permissionCode?: string
  permissionName: string
  resourceType: number
  url?: string
  /** 更新时传 0 表示移到一级 */
  parentId?: number | null
  sortOrder?: number
}

export const getRoleList = (params: RoleQuery) =>
  request<PageResponse<Role>>({ url: '/roles', method: 'get', params })

export const getRoleDetail = (id: number) =>
  request<Role>({ url: `/roles/${id}`, method: 'get' })

export const createRole = (data: RoleSaveRequest) =>
  request<Role>({ url: '/roles', method: 'post', data })

export const updateRole = (id: number, data: RoleSaveRequest) =>
  request<Role>({ url: `/roles/${id}`, method: 'put', data })

export const deleteRole = (id: number) =>
  request<void>({ url: `/roles/${id}`, method: 'delete' })

/** 角色已分配的权限点（平铺） */
export const getRolePermissions = (id: number) =>
  request<PermissionNode[]>({ url: `/roles/${id}/permissions`, method: 'get' })

/** 全量替换角色权限 */
export const assignRolePermissions = (id: number, permissionIds: number[]) =>
  request<void>({ url: `/roles/${id}/permissions`, method: 'put', data: { permissionIds } })

export const getPermissionTree = () =>
  request<PermissionNode[]>({ url: '/permissions/tree', method: 'get' })

export const createPermission = (data: PermissionSaveRequest) =>
  request<PermissionNode>({ url: '/permissions', method: 'post', data })

export const updatePermission = (id: number, data: PermissionSaveRequest) =>
  request<PermissionNode>({ url: `/permissions/${id}`, method: 'put', data })

export const deletePermission = (id: number) =>
  request<void>({ url: `/permissions/${id}`, method: 'delete' })

export const getUserRoles = (userId: number) =>
  request<Role[]>({ url: `/users/${userId}/roles`, method: 'get' })

/** 全量设置用户附加角色；user_type 对应的内置角色由后端始终保留 */
export const updateUserRoles = (userId: number, roleCodes: string[]) =>
  request<Role[]>({ url: `/users/${userId}/roles`, method: 'put', data: { roleCodes } })

export const getMyPermissions = () =>
  request<string[]>({ url: '/me/permissions', method: 'get' })

// ---------------------------------------------------------------------------
// 当前用户权限码缓存（供 v-permission 指令与页面按钮判定）
// ponytail: 模块级缓存按 token 失效——不能改 stores/user.ts 在登出时清缓存，
// 所以记录加载时的 token，换账号登录后 token 不同即重新拉取；拉取失败不缓存，下次再试。
// ---------------------------------------------------------------------------

let cachedCodes: Set<string> | null = null
let cachedToken = ''
let pending: Promise<Set<string>> | null = null

export const isSuperAdmin = (): boolean => useUserStore().userInfo?.userType === 1

/** 确保权限码已加载（登录后首次调用触发请求，之后命中缓存） */
export const ensurePermissionsLoaded = (): Promise<Set<string>> => {
  const token = useUserStore().token
  if (!token) return Promise.resolve(new Set<string>())
  if (cachedCodes && cachedToken === token) return Promise.resolve(cachedCodes)
  if (pending) return pending
  pending = getMyPermissions()
    .then((codes) => {
      cachedCodes = new Set(codes ?? [])
      cachedToken = token
      return cachedCodes
    })
    .catch(() => new Set<string>())
    .finally(() => {
      pending = null
    })
  return pending
}

/** 同步判定（需先 ensurePermissionsLoaded）；超管恒 true，与后端 PermissionEvaluatorBean 一致 */
export const hasPermission = (code: string | string[]): boolean => {
  if (isSuperAdmin()) return true
  if (!cachedCodes || cachedToken !== useUserStore().token) return false
  const codes = Array.isArray(code) ? code : [code]
  return codes.some((c) => cachedCodes!.has(c))
}

/** 角色权限变更后手动失效（下次 ensurePermissionsLoaded 重新拉取） */
export const clearPermissionCache = (): void => {
  cachedCodes = null
  cachedToken = ''
}
