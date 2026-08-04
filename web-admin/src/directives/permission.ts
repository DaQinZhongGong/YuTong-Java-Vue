/**
 * v-permission 自定义指令。设计来源: 96-端侧权限可见性矩阵详设
 *
 * 用法:
 *   - 单权限:        v-permission="'biz:request:add'"
 *   - 任一权限 (any): v-permission.any="['biz:request:approve', 'biz:request:reject']"
 *   - 全部权限 (all): v-permission.all="['biz:request:edit', 'biz:request:delete']"
 *
 * 无权限时元素从 DOM 中移除 (display:none 不够安全, 仍可被开发者工具操作)。
 * 高风险动作 (删除/审核/归档/缓存清理) 必须使用本指令, 不得仅靠 UI 隐藏。
 */
import type { Directive, DirectiveBinding } from 'vue'
import { hasPermission, hasAnyPermission, hasAllPermissions } from '@/utils/permission'

type PermissionMode = 'single' | 'any' | 'all'

function evaluate(binding: DirectiveBinding): boolean {
  const value = binding.value
  const mode: PermissionMode = (binding.modifiers.all && 'all')
    || (binding.modifiers.any && 'any')
    || 'single'

  if (mode === 'single') {
    if (typeof value !== 'string') {
      // eslint-disable-next-line no-console
      console.warn('[v-permission] 单权限模式需要 string 值, 收到:', value)
      return false
    }
    return hasPermission(value)
  }

  if (!Array.isArray(value)) {
    // eslint-disable-next-line no-console
    console.warn(`[v-permission] ${mode} 模式需要 string[] 值, 收到:`, value)
    return false
  }

  return mode === 'any'
    ? hasAnyPermission(value)
    : hasAllPermissions(value)
}

/**
 * 注释占位符, 用于无权限时替换元素, 避免空文本节点占位干扰布局。
 * 当权限恢复时通过占位符定位元素重新插入。
 */
const PLACEHOLDER_COMMENT = '__v_permission_placeholder__'

export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding) {
    if (!evaluate(binding)) {
      // 用注释节点占位, 便于在权限变化时回插 (但当前实现不做动态回插, 仅 unmount)
      const placeholder = document.createComment(PLACEHOLDER_COMMENT)
      ;(placeholder as Comment & { __permission_el?: HTMLElement }).__permission_el = el
      ;(el as HTMLElement & { __permission_placeholder?: Comment }).__permission_placeholder = placeholder
      el.parentNode?.replaceChild(placeholder, el)
    }
  },
}

export default permissionDirective
