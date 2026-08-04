import type { LoginResponse, UserInfo } from '@/api/types';
import { TOKEN_STORAGE_KEY, USER_STORAGE_KEY, REFRESH_TOKEN_STORAGE_KEY } from '@/utils/request';

/**
 * Simple auth store backed by uni storage.
 * Persists token and user info across app restarts.
 */

export function getToken(): string {
  return uni.getStorageSync(TOKEN_STORAGE_KEY) || '';
}

export function setToken(token: string): void {
  uni.setStorageSync(TOKEN_STORAGE_KEY, token);
}

export function removeToken(): void {
  uni.removeStorageSync(TOKEN_STORAGE_KEY);
}

/** 21-安全合规: refresh token 存取 (14d, 轮换) */
export function getRefreshToken(): string {
  return uni.getStorageSync(REFRESH_TOKEN_STORAGE_KEY) || '';
}

export function setRefreshToken(token: string): void {
  uni.setStorageSync(REFRESH_TOKEN_STORAGE_KEY, token);
}

export function removeRefreshToken(): void {
  uni.removeStorageSync(REFRESH_TOKEN_STORAGE_KEY);
}

export function getUser(): UserInfo | null {
  const raw = uni.getStorageSync(USER_STORAGE_KEY);
  if (!raw) return null;
  try {
    return typeof raw === 'string' ? (JSON.parse(raw) as UserInfo) : (raw as UserInfo);
  } catch {
    return null;
  }
}

export function setUser(user: UserInfo): void {
  uni.setStorageSync(USER_STORAGE_KEY, JSON.stringify(user));
}

export function removeUser(): void {
  uni.removeStorageSync(USER_STORAGE_KEY);
}

/**
 * Persist the login response as both token and a basic UserInfo.
 */
export function saveLogin(resp: LoginResponse): void {
  setToken(resp.token);
  // 21-安全合规: 持久化 refresh token (14d, 轮换)，供 request 在 access token 401 时自动换新
  if (resp.refreshToken) {
    setRefreshToken(resp.refreshToken);
  }
  setUser({
    id: resp.userId,
    username: resp.username,
    tenantId: resp.tenantId,
  });
}

export function isLoggedIn(): boolean {
  return !!getToken();
}

/**
 * 权限校验（P0-6 基础接入）。
 *
 * 基于登录后存储的 permissions 数组判断；admin 的 `*` 通配放行。
 *
 * 策略说明：当前登录响应未携带 permissions，且 saveLogin 未持久化该字段，
 * 故 permissions 未加载（undefined）时按放行处理，避免阻塞既有可用功能
 * （如 mock 账号 admin/admin）。当后续通过 getMe 拉取并 setUser 写入
 * permissions 后，过滤将自动生效。完整权限矩阵作为 P0 单独工作。
 *
 * @param code 权限码，如 mobile:scan:use
 */
export function hasPermission(code: string): boolean {
  const permissions = getUser()?.permissions;
  if (!permissions) return true; // 未加载权限时放行（基础接入策略）
  if (permissions.includes('*')) return true; // admin 通配放行
  return permissions.includes(code);
}

/**
 * Clear all persisted auth state.
 */
export function clearAuth(): void {
  removeToken();
  removeRefreshToken();
  removeUser();
}
