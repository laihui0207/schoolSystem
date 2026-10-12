import { MapLocation } from '@element-plus/icons-vue'
import type { MenuGroupDef } from '@/layouts/menu'

/**
 * 侧栏「区域分析」分组（契约 K4：key 'stat-region'，order 30，仅超管可见）。
 * 只用 import type 引用 layouts/menu，避免与其 import.meta.glob 形成运行时循环依赖。
 */
const group: MenuGroupDef = {
  key: 'stat-region',
  title: '区域分析',
  icon: MapLocation,
  order: 30,
  allowedTypes: [1],
  items: [{ title: '区域成绩对比', path: '/stat/region-compare', allowedTypes: [1] }]
}

export default group
