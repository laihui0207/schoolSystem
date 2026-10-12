import type { RouteRecordRaw } from 'vue-router'

/**
 * 定时任务路由（挂在 MainLayout 下，由 router/modules/index.ts 自动收集）
 * ponytail: allowedTypes [1] 与后端 /jobs 全部 hasRole('SUPER_ADMIN') 对齐——任务以系统身份跨校执行，校管理员不可见
 */
const routes: RouteRecordRaw[] = [
  {
    path: 'system/job',
    name: 'SysJob',
    component: () => import('@/views/system/job.vue'),
    meta: { title: '定时任务', allowedTypes: [1] }
  }
]

export default routes
