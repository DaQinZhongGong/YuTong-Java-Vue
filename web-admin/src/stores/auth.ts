import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginRequest, LoginResponse, UserInfo } from '@/api/types'
import {
  getToken,
  setToken,
  removeToken,
  setRefreshToken,
  removeRefreshToken,
  getMockUser,
  setMockUser,
  removeMockUser,
  type MockUserType,
} from '@/utils/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getToken() || '')
  const userInfo = ref<UserInfo | null>(null)
  /** GA2-16: 当前 Mock 用户类型 (admin/biz/approver/viewer), 仅 dev 模式可用 */
  const mockUser = ref<MockUserType | null>(getMockUser())
  /** userInfo 是否已拉取 (避免路由守卫重复拉取) */
  const userInfoLoaded = ref(false)

  /**
   * 登录: 调用接口并保存 token。
   * GA2-16: 增加 mockUserType 参数, 仅 dev/local profile 用于切换 Mock 用户。
   */
  async function login(
    payload: LoginRequest,
    mockUserType?: MockUserType
  ): Promise<LoginResponse> {
    // GA2-16: 在请求前持久化 Mock 用户类型, 让 request 拦截器注入 X-Mock-User 头
    if (mockUserType) {
      setMockUser(mockUserType)
      mockUser.value = mockUserType
    } else {
      // 未指定时清除历史 Mock 用户, 默认走 admin (后端 MockAuthAdapter 默认)
      removeMockUser()
      mockUser.value = null
    }
    const data = await authApi.login(payload)
    token.value = data.token
    setToken(data.token)
    // 21-安全合规: 持久化 refresh token (14d, 轮换)，供 axios 拦截器在 access token 401 时自动换新
    if (data.refreshToken) {
      setRefreshToken(data.refreshToken)
    }
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      tenantId: data.tenantId,
    }
    userInfoLoaded.value = false
    // 立即拉取完整用户信息 (含 permissions/roles/dataScopeType)
    try {
      await fetchUserInfo()
    } catch {
      // 拉取失败不阻断登录流程, 后续路由守卫会重试
    }
    return data
  }

  /** 拉取当前用户信息 (含 permissions/roles/dataScopeType)。 */
  async function fetchUserInfo(): Promise<UserInfo> {
    const data = await authApi.getMe()
    userInfo.value = data
    userInfoLoaded.value = true
    return data
  }

  /** 切换 Mock 用户类型 (dev 模式); 会清除 token 并要求重新登录。 */
  function switchMockUser(type: MockUserType): void {
    setMockUser(type)
    mockUser.value = type
    // 切换用户需要清除 token, 触发重新登录拉取新权限集
    token.value = ''
    userInfo.value = null
    userInfoLoaded.value = false
    removeToken()
    removeRefreshToken()
  }

  /** 退出登录: 清空本地状态。 */
  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      token.value = ''
      userInfo.value = null
      userInfoLoaded.value = false
      mockUser.value = null
      removeToken()
      removeRefreshToken()
      removeMockUser()
      // GA2-L171: 清空 License 模块状态缓存 (避免下个用户看到上个用户的模块矩阵)
      const { useLicenseStore } = await import('./license')
      useLicenseStore().reset()
    }
  }

  return {
    token,
    userInfo,
    mockUser,
    userInfoLoaded,
    login,
    logout,
    fetchUserInfo,
    switchMockUser,
  }
})
