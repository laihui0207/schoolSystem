import type { RouteRecordRaw } from 'vue-router'

/**
 * 路由扩展点。
 *
 * 新功能模块在 `router/modules/<feature>.routes.ts` 默认导出 RouteRecordRaw[]
 * （相对于 MainLayout 的子路由，path 不带前导斜杠，meta.allowedTypes 与内置路由同义），
 * 这里用 import.meta.glob 自动收集并挂到主布局下。并行开发时各自只新增文件。
 */
const modules = import.meta.glob<{ default: RouteRecordRaw[] }>('./*.routes.ts', { eager: true })

export const featureRoutes: RouteRecordRaw[] = Object.keys(modules)
  .sort()
  .flatMap(key => modules[key].default ?? [])
