import type { RouteRecordRaw } from 'vue-router'

/**
 * 家长端路由，挂在 MainLayout 下；由 router/modules/index.ts 自动收集。
 * allowedTypes [5]：只有家长（UserRole.PARENT）可见；成绩查询 / 成绩趋势复用内置页面并带 ?studentId= 跳转。
 */
const routes: RouteRecordRaw[] = [
  {
    path: 'parent/children',
    name: 'ParentChildren',
    component: () => import('@/views/parent/children.vue'),
    meta: { title: '我的孩子', allowedTypes: [5] }
  }
]

export default routes
