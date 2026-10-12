import { Document } from '@element-plus/icons-vue'
import type { MenuGroupDef } from '@/layouts/menu'

/**
 * 操作日志菜单项
 * ponytail: 契约 K4——key 'system' 会被 MainLayout 并入内置「系统管理」子菜单，不单独成组
 */
const group: MenuGroupDef = {
  key: 'system',
  title: '系统管理',
  icon: Document,
  allowedTypes: [1, 2],
  items: [{ title: '操作日志', path: '/system/log', allowedTypes: [1, 2] }]
}

export default group
