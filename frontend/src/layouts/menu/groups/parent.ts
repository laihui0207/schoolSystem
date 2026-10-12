import { User } from '@element-plus/icons-vue'
import type { MenuGroupDef } from '@/layouts/menu'

/**
 * 侧栏「我的孩子」分组（key 'parent'，order 5，仅家长 userType=5 可见）。
 * ponytail: 内置的成绩查询 / 成绩趋势菜单按管理员、教师、学生分支渲染，家长看不到，
 * 这里把两个页面也列进来，家长不必只能从孩子列表的按钮跳转。
 * 只用 import type 引用 layouts/menu，避免与其 import.meta.glob 形成运行时循环依赖。
 */
const group: MenuGroupDef = {
  key: 'parent',
  title: '我的孩子',
  icon: User,
  order: 5,
  allowedTypes: [5],
  items: [
    { title: '我的孩子', path: '/parent/children', allowedTypes: [5] },
    { title: '孩子成绩查询', path: '/score/query', allowedTypes: [5] },
    { title: '孩子成绩趋势', path: '/stat/student-trend', allowedTypes: [5] }
  ]
}

export default group
