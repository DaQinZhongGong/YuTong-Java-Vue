/**
 * 列表离线缓存工具。
 *
 * 设计来源: 11-弱网与离线策略详设（列表缓存能力）。
 *
 * 特性:
 *  - 按 userId 隔离（不同用户不串缓存）
 *  - 带 TTL（默认 30 分钟），过期视为 stale
 *  - key 规范: cache:list:<baseKey>:<userId>
 *  - 退出登录时调用 clearUserCaches 清理当前用户全部列表缓存
 */

import { USER_STORAGE_KEY } from './request';

/** 默认缓存有效期：30 分钟。 */
export const DEFAULT_TTL = 1000 * 60 * 30;

interface CachedList<T> {
  data: T;
  savedAt: number;
  expireAt: number;
}

/** 读取当前登录用户 ID，未登录回退 anonymous。 */
function getUserId(): string {
  try {
    const raw = uni.getStorageSync(USER_STORAGE_KEY);
    if (!raw) return 'anonymous';
    const user = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return user?.id || 'anonymous';
  } catch {
    return 'anonymous';
  }
}

function buildKey(baseKey: string): string {
  return `cache:list:${baseKey}:${getUserId()}`;
}

/**
 * 写入列表缓存。
 * @param baseKey 业务键（如 'todo' / 'workbench-stats'）
 * @param data 列表数据
 * @param ttl 有效期（毫秒），默认 DEFAULT_TTL
 */
export function saveListCache<T>(baseKey: string, data: T, ttl: number = DEFAULT_TTL): void {
  const now = Date.now();
  const payload: CachedList<T> = { data, savedAt: now, expireAt: now + ttl };
  try {
    uni.setStorageSync(buildKey(baseKey), JSON.stringify(payload));
  } catch (e) {
    console.error('[offlineCache] save failed', e);
  }
}

/**
 * 读取列表缓存。
 * @returns 成功返回 { data, stale }；不存在或解析失败返回 null。
 *          stale 为 true 表示已过期（仍可展示，但应提示数据为历史快照）。
 */
export function loadListCache<T>(baseKey: string): { data: T; stale: boolean } | null {
  try {
    const raw = uni.getStorageSync(buildKey(baseKey));
    if (!raw) return null;
    const payload = JSON.parse(raw) as CachedList<T>;
    const stale = Date.now() > payload.expireAt;
    return { data: payload.data, stale };
  } catch {
    return null;
  }
}

/** 退出登录时清理当前用户的所有列表缓存（保留鉴权键）。 */
export function clearUserCaches(): void {
  try {
    const info = uni.getStorageInfoSync();
    info.keys.forEach((key) => {
      if (key.startsWith('cache:list:')) {
        uni.removeStorageSync(key);
      }
    });
  } catch (e) {
    console.error('[offlineCache] clear failed', e);
  }
}
