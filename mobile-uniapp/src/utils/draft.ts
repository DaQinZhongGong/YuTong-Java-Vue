/**
 * 表单草稿工具。
 *
 * 设计来源: 11-弱网与离线策略详设（表单草稿能力）。
 *
 * 维度隔离: 按 页面(page) + 业务类型(bizType) + 用户(userId) 三维隔离。
 * key 规范: draft:<page>:<bizType>:<userId>
 *
 * 典型用法:
 *  - 用户输入时（防抖）调用 saveDraft 自动保存
 *  - 进入页面时调用 hasDraft/loadDraft 检测并提示"是否恢复"
 *  - 提交成功后调用 clearDraft 清草稿
 *  - 退出登录时调用 clearAllDraftsForUser 清理
 */

import { USER_STORAGE_KEY } from './request';

interface DraftPayload<T> {
  data: T;
  savedAt: number;
}

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

function buildKey(page: string, bizType: string): string {
  return `draft:${page}:${bizType}:${getUserId()}`;
}

/** 保存草稿。 */
export function saveDraft<T>(page: string, bizType: string, data: T): void {
  const payload: DraftPayload<T> = { data, savedAt: Date.now() };
  try {
    uni.setStorageSync(buildKey(page, bizType), JSON.stringify(payload));
  } catch (e) {
    console.error('[draft] save failed', e);
  }
}

/** 读取草稿，不存在返回 null。 */
export function loadDraft<T>(page: string, bizType: string): { data: T; savedAt: number } | null {
  try {
    const raw = uni.getStorageSync(buildKey(page, bizType));
    if (!raw) return null;
    const payload = JSON.parse(raw) as DraftPayload<T>;
    return { data: payload.data, savedAt: payload.savedAt };
  } catch {
    return null;
  }
}

/** 是否存在草稿。 */
export function hasDraft(page: string, bizType: string): boolean {
  return !!loadDraft(page, bizType);
}

/** 清除指定草稿。 */
export function clearDraft(page: string, bizType: string): void {
  try {
    uni.removeStorageSync(buildKey(page, bizType));
  } catch (e) {
    console.error('[draft] clear failed', e);
  }
}

/** 退出登录时清理当前用户全部草稿。 */
export function clearAllDraftsForUser(): void {
  try {
    const info = uni.getStorageInfoSync();
    info.keys.forEach((key) => {
      if (key.startsWith('draft:')) {
        uni.removeStorageSync(key);
      }
    });
  } catch (e) {
    console.error('[draft] clearAll failed', e);
  }
}
