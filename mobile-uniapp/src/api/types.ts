/**
 * Unified API response wrapper.
 * The backend returns { code, message, data, success }.
 */
export interface Result<T = unknown> {
  code: number;
  message: string;
  data: T;
  success: boolean;
}

/**
 * Paginated result wrapper.
 */
export interface PageResult<T = unknown> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/**
 * Login request payload.
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Login response payload (the `data` field of Result).
 * GA2-22: userId/tenantId 按 ADR-006 改为 string ULID，对齐后端 AuthController。
 */
export interface LoginResponse {
  token: string;
  userId: string;
  username: string;
  tenantId: string;
  /** 21-安全合规: refresh token (14d, 轮换)，登录成功后由后端签发 */
  refreshToken?: string;
}

/**
 * Current user info.
 * GA2-22: id/tenantId 按 ADR-006 改为 string ULID。
 */
export interface UserInfo {
  id: string;
  username: string;
  nickname?: string;
  avatar?: string;
  email?: string;
  phone?: string;
  tenantId?: string;
  roles?: string[];
  permissions?: string[];
}
