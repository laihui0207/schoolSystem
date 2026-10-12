import type { RouteRecordRaw } from 'vue-router'

/**
 * 数据备份路由（挂在 MainLayout 下，由 router/modules/index.ts 自动收集）
 * ponytail: allowedTypes [1] 与后端 @PreAuthorize hasRole('SUPER_ADMIN') 对齐——备份文件是整库明文，校管理员无入口
 */
const routes: RouteRecordRaw[] = [
  {
    path: 'system/backup',
    name: 'DataBackup',
    component: () => import('@/views/system/backup.vue'),
    meta: { title: '数据备份', allowedTypes: [1] }
  }
]

export default routes
