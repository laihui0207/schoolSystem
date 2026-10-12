import type { RouteRecordRaw } from 'vue-router'

/**
 * 区域（跨校）统计路由，挂在 MainLayout 下；由 router/modules/index.ts 自动收集。
 * allowedTypes [1]：只有超管能跨校，与后端 GET /stats/region 的 hasRole('SUPER_ADMIN') 一致。
 */
const routes: RouteRecordRaw[] = [
  {
    path: 'stat/region-compare',
    name: 'RegionCompare',
    component: () => import('@/views/stat/region-compare.vue'),
    meta: { title: '区域成绩对比', allowedTypes: [1] }
  }
]

export default routes
