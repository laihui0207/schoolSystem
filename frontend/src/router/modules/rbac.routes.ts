import type { RouteRecordRaw } from 'vue-router'

/**
 * RBAC 角色/权限路由（挂在 MainLayout 下，由 router/modules/index.ts 自动收集）
 * ponytail: allowedTypes [1,2] 与后端 V19 给 ROLE_SCHOOL_ADMIN 预置的 sys:role:* / sys:permission:list 对齐；
 * 权限页对校管理员只读，增删改按钮由页面内 isSuperAdmin 控制
 */
const routes: RouteRecordRaw[] = [
  {
    path: 'system/role',
    name: 'RoleManage',
    component: () => import('@/views/system/role.vue'),
    meta: { title: '角色管理', allowedTypes: [1, 2] }
  },
  {
    path: 'system/permission',
    name: 'PermissionManage',
    component: () => import('@/views/system/permission.vue'),
    meta: { title: '权限管理', allowedTypes: [1, 2] }
  },
  {
    path: 'system/user-roles',
    name: 'UserRoles',
    component: () => import('@/views/system/user-roles.vue'),
    meta: { title: '用户角色', allowedTypes: [1, 2] }
  }
]

export default routes
