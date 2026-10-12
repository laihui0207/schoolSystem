import type { RouteRecordRaw } from 'vue-router'

/**
 * 操作日志路由（挂在 MainLayout 下，由 router/modules/index.ts 自动收集）
 * ponytail: allowedTypes [1,2] 与后端 @PreAuthorize SUPER_ADMIN/SCHOOL_ADMIN 对齐
 */
const routes: RouteRecordRaw[] = [
  {
    path: 'system/log',
    name: 'OperationLog',
    component: () => import('@/views/system/log.vue'),
    meta: { title: '操作日志', allowedTypes: [1, 2] }
  }
]

export default routes
