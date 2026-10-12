import { Key } from '@element-plus/icons-vue'
import type { MenuGroupDef } from '@/layouts/menu'

/**
 * RBAC 菜单项：key 'system' 会被 MainLayout 并入内置「系统管理」子菜单
 */
const group: MenuGroupDef = {
  key: 'system',
  title: '系统管理',
  icon: Key,
  allowedTypes: [1, 2],
  items: [
    { title: '角色管理', path: '/system/role', allowedTypes: [1, 2] },
    { title: '权限管理', path: '/system/permission', allowedTypes: [1, 2] },
    { title: '用户角色', path: '/system/user-roles', allowedTypes: [1, 2] }
  ]
}

export default group
