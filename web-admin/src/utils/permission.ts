/**
 * 前端权限判断工具。设计来源: 96-端侧权限可见性矩阵详设
 *
 * 权限判断分五层: 菜单/路由、按钮、字段、数据范围、行级状态。
 * 前端只做体验层控制,最终权限必须以后端校验为准。
 *
 * 通配符 `*` 表示管理员全量权限,任何 hasPermission 检查都会通过。
 */
import { useAuthStore } from '@/stores/auth'

/** 从 store 取出当前用户权限码集合;未登录时返回空集。 */
function currentPermissions(): Set<string> {
  const authStore = useAuthStore()
  const list = authStore.userInfo?.permissions
  if (!list || list.length === 0) {
    return new Set<string>()
  }
  return new Set(list)
}

/** 判断当前用户是否拥有指定权限码 (支持 `*` 通配)。 */
export function hasPermission(code: string): boolean {
  const perms = currentPermissions()
  if (perms.size === 0) {
    return false
  }
  if (perms.has('*')) {
    return true
  }
  return perms.has(code)
}

/** 判断当前用户是否拥有给定权限码中的任意一个。 */
export function hasAnyPermission(codes: string[]): boolean {
  if (!codes || codes.length === 0) {
    return false
  }
  const perms = currentPermissions()
  if (perms.size === 0) {
    return false
  }
  if (perms.has('*')) {
    return true
  }
  return codes.some((c) => perms.has(c))
}

/** 判断当前用户是否同时拥有全部给定权限码。 */
export function hasAllPermissions(codes: string[]): boolean {
  if (!codes || codes.length === 0) {
    return false
  }
  const perms = currentPermissions()
  if (perms.size === 0) {
    return false
  }
  if (perms.has('*')) {
    return true
  }
  return codes.every((c) => perms.has(c))
}

/** 判断当前用户是否属于指定角色 (支持 `*` 通配 ADMIN)。 */
export function hasRole(role: string): boolean {
  const authStore = useAuthStore()
  const roles = authStore.userInfo?.roles
  if (!roles || roles.length === 0) {
    return false
  }
  if (roles.includes('ADMIN')) {
    return true
  }
  return roles.includes(role)
}

/** 返回当前用户数据范围类型 (ALL/TENANT/DEPT_AND_CHILD/DEPT/SELF/CUSTOM/NONE)。 */
export function currentDataScopeType(): string {
  const authStore = useAuthStore()
  return authStore.userInfo?.dataScopeType || 'NONE'
}

/** 判断当前用户是否可见敏感字段 (admin/biz/approver 可见, viewer 不可见)。 */
export function canViewSensitiveFields(): boolean {
  const scope = currentDataScopeType()
  // viewer (TENANT + sensitiveFieldsVisible=false) 不可见, 其他默认可见
  // 实际是否可见应由后端 DataScope.isSensitiveFieldsVisible() 决定
  // 前端基于 dataScopeType 近似判断, 仅供 UI 展示, 不能作为安全边界
  const authStore = useAuthStore()
  const permissions = authStore.userInfo?.permissions || []
  if (permissions.includes('*')) {
    return true
  }
  return scope !== 'TENANT' || permissions.some((p) => p.startsWith('biz:'))
}
