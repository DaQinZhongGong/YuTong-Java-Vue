import { request, get, post } from '@/utils/request';
import type { LoginRequest, LoginResponse, UserInfo } from './types';

/**
 * Login with username/password.
 * POST /api/v1/auth/login
 */
export function login(data: LoginRequest): Promise<LoginResponse> {
  return request<LoginResponse>({
    url: '/auth/login',
    method: 'POST',
    data,
    needAuth: false,
  });
}

/**
 * Fetch the current user profile.
 * GET /api/v1/auth/me
 */
export function getMe(): Promise<UserInfo> {
  return get<UserInfo>('/auth/me');
}

/**
 * Logout the current session.
 * POST /api/v1/auth/logout
 */
export function logout(): Promise<void> {
  return post<void>('/auth/logout');
}

/**
 * 21-安全合规: 使用 refresh token 换取新的 access token + refresh token (轮换)。
 * POST /api/v1/auth/refresh
 *
 * <p>refresh token 每次使用必须轮换 (设计文档 21 号要求)：
 * 后端删除旧 refresh token 并签发新 token pair，前端需持久化返回的新 refresh token。
 */
export function refreshToken(refreshToken: string): Promise<{
  token: string;
  refreshToken: string;
}> {
  return request<{ token: string; refreshToken: string }>({
    url: '/auth/refresh',
    method: 'POST',
    data: { refreshToken },
    needAuth: false,
  });
}
