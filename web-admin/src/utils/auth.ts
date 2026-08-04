const TOKEN_KEY = 'yutong_admin_token'
const REFRESH_TOKEN_KEY = 'yutong_admin_refresh_token'
const MOCK_USER_KEY = 'yutong_admin_mock_user'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 21-安全合规: refresh token 存取 (14d, 轮换) */
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

export function setRefreshToken(token: string): void {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

export function removeRefreshToken(): void {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

/**
 * GA2-16: Mock 用户类型管理。
 * 设计来源: 96-端侧权限可见性矩阵详设、MockAuthAdapter
 *
 * local/test profile 后端通过 X-Mock-User 请求头切换 4 类 Mock 用户:
 *   - admin    → ALL 数据范围 + 全部权限
 *   - biz      → SELF 数据范围 + 业务权限
 *   - approver → CUSTOM 数据范围 + 审批权限
 *   - viewer   → TENANT 数据范围 + 只读权限 + 敏感字段脱敏
 *
 * 生产 profile 不读取本字段, 后端由真实 AuthAdapter 决定用户身份。
 */
export type MockUserType = 'admin' | 'biz' | 'approver' | 'viewer'

export function getMockUser(): MockUserType | null {
  const v = localStorage.getItem(MOCK_USER_KEY)
  if (v === 'admin' || v === 'biz' || v === 'approver' || v === 'viewer') {
    return v
  }
  return null
}

export function setMockUser(type: MockUserType): void {
  localStorage.setItem(MOCK_USER_KEY, type)
}

export function removeMockUser(): void {
  localStorage.removeItem(MOCK_USER_KEY)
}

/** Mock 用户类型 → 中文显示名 */
export const MOCK_USER_LABELS: Record<MockUserType, string> = {
  admin: '平台管理员',
  biz: '业务人员',
  approver: '审核人员',
  viewer: '只读观察者',
}
