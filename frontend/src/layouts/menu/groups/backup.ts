import { FolderChecked } from '@element-plus/icons-vue'
import type { MenuGroupDef } from '@/layouts/menu'

/**
 * 数据备份菜单项
 * ponytail: key 'system' 会被 MainLayout 并入内置「系统管理」子菜单，不单独成组；仅超管可见
 */
const group: MenuGroupDef = {
  key: 'system',
  title: '系统管理',
  icon: FolderChecked,
  allowedTypes: [1],
  items: [{ title: '数据备份', path: '/system/backup', allowedTypes: [1] }]
}

export default group
