import service from './request'
import type { LoginRequest, LoginResponse, UserInfo, Menu } from './types'

/** 登录: POST /auth/login */
export function login(data: LoginRequest): Promise<LoginResponse> {
  return service.post('/auth/login', data) as unknown as Promise<LoginResponse>
}

/** 获取当前登录用户信息: GET /auth/me */
export function getMe(): Promise<UserInfo> {
  return service.get('/auth/me') as unknown as Promise<UserInfo>
}

/** 退出登录: POST /auth/logout */
export function logout(): Promise<void> {
  return service.post('/auth/logout') as unknown as Promise<void>
}

/** 刷新访问令牌: POST /auth/refresh，校验并轮换 refresh token，返回新的访问令牌和刷新令牌 */
export function refreshToken(
  refreshToken: string
): Promise<{ token: string; refreshToken: string }> {
  return service.post('/auth/refresh', { refreshToken }) as unknown as Promise<{
    token: string
    refreshToken: string
  }>
}

/** 获取当前用户菜单树: GET /auth/menus */
export function getMenus(): Promise<Menu[]> {
  return service.get('/auth/menus') as unknown as Promise<Menu[]>
}

/** 获取当前用户权限码集合: GET /auth/permissions */
export function getPermissions(): Promise<string[]> {
  return service.get('/auth/permissions') as unknown as Promise<string[]>
}
