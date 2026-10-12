import type { Directive, DirectiveBinding } from 'vue'
import { ensurePermissionsLoaded, hasPermission } from '@/api/rbac'

type PermissionValue = string | string[]

/**
 * v-permission="'score:review'" / v-permission="['a', 'b']"（任一命中即可）
 *
 * 无权限则从 DOM 移除元素。权限码来自 GET /me/permissions（api/rbac.ts 模块级缓存，登录后首次调用拉取），
 * 请求在途期间先隐藏元素，避免按钮闪现后消失。超管恒放行，与后端 PermissionEvaluatorBean 一致。
 * ponytail: 用 remove 而非 display:none——被隐藏的按钮仍可被脚本点击，移除才是真正的「不可用」。
 */
const apply = async (el: HTMLElement, binding: DirectiveBinding<PermissionValue>): Promise<void> => {
  const value = binding.value
  if (!value || (Array.isArray(value) && value.length === 0)) return

  const originalDisplay = el.style.display
  el.style.display = 'none'
  await ensurePermissionsLoaded()
  if (hasPermission(value)) {
    el.style.display = originalDisplay
  } else {
    el.parentNode?.removeChild(el)
  }
}

const permission: Directive<HTMLElement, PermissionValue> = {
  mounted(el, binding) {
    void apply(el, binding)
  }
}

// Vue 3.5 的 GlobalDirectives 增强：让模板里的 v-permission 在 vue-tsc 下有类型，无需每个页面本地声明 vPermission
declare module 'vue' {
  interface GlobalDirectives {
    vPermission: typeof permission
  }
}

export default permission
