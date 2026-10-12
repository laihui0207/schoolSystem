import type { Component } from 'vue'

/**
 * 侧栏菜单扩展点。
 *
 * 新功能模块不要直接改 MainLayout.vue，而是在 `layouts/menu/groups/<feature>.ts`
 * 默认导出一个 MenuGroupDef；这里用 import.meta.glob 自动收集并按 order 排序。
 * 多个功能并行开发时各自只新增文件，互不冲突。
 */
export interface MenuItemDef {
  title: string
  path: string
  /** 允许看到该菜单项的 userType（1 超管 2 校管 3 教师 4 学生 5 家长）；省略表示全部 */
  allowedTypes?: number[]
}

export interface MenuGroupDef {
  /** el-sub-menu 的 index，全局唯一 */
  key: string
  title: string
  icon: Component
  /** 排序，越小越靠前；内置分组之后、系统管理之前渲染 */
  order?: number
  /** 整组可见性；省略表示全部 */
  allowedTypes?: number[]
  items: MenuItemDef[]
}

const modules = import.meta.glob<{ default: MenuGroupDef }>('./groups/*.ts', { eager: true })

export const featureMenuGroups: MenuGroupDef[] = Object.values(modules)
  .map(m => m.default)
  .filter((g): g is MenuGroupDef => !!g && Array.isArray(g.items))
  .sort((a, b) => (a.order ?? 100) - (b.order ?? 100))

const allowed = (types: number[] | undefined, userType: number): boolean =>
  !types || types.includes(userType)

/** 按当前用户类型过滤后的菜单分组（空组会被整体隐藏） */
export function visibleMenuGroups(userType: number): MenuGroupDef[] {
  return featureMenuGroups
    .filter(g => allowed(g.allowedTypes, userType))
    .map(g => ({ ...g, items: g.items.filter(i => allowed(i.allowedTypes, userType)) }))
    .filter(g => g.items.length > 0)
}
