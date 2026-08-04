/**
 * 上传重试队列。
 *
 * 设计来源: 11-弱网与离线策略详设（上传重试队列能力）。
 *
 * 队列项字段（严格按设计文档）:
 *   localId        本地唯一标识
 *   filePath       本地文件临时路径（不持久化 token / 请求头）
 *   fileName       文件名
 *   fileSize       文件大小（字节）
 *   bizType        业务类型（如 'biz_request'）
 *   bizId          业务 ID（为空则上传后不绑定）
 *   status         状态枚举（见 UploadStatus）
 *   retryCount     已重试次数（单文件最多 3 次）
 *   errorMessage   最近一次失败原因
 *   createdTime    创建时间（ISO）
 *   lastRetryTime  最近一次重试时间（ISO）
 * 扩展字段（非敏感）:
 *   fileId         上传成功后回填，用于 BIND_FAILED 时重新绑定
 *   relType        绑定关系类型，默认 ATTACHMENT
 *
 * 规则:
 *  - 网络恢复（onNetworkOnline）或手动 retryTask/retryAll 触发重试
 *  - 单文件最多重试 3 次，超限置 FAILED
 *  - 上传成功且带 bizType+bizId 时自动调用绑定接口
 *  - BIND_FAILED = 文件上传成功但业务绑定失败，UI 必须展示"重新绑定"
 *  - 队列不保存 token / 完整请求头（鉴权在 api/file.ts 运行时读取）
 *  - 退出登录调用 clearUploadQueueForUser 清理敏感缓存
 */

import { uploadFile, bindFile } from '@/api/file';
import { isOffline, onNetworkOnline } from '@/store/network';
import { USER_STORAGE_KEY } from './request';

export type UploadStatus = 'PENDING' | 'UPLOADING' | 'SUCCESS' | 'FAILED' | 'BIND_FAILED';

export interface UploadTask {
  localId: string;
  filePath: string;
  fileName: string;
  fileSize: number;
  bizType: string;
  bizId: string;
  status: UploadStatus;
  retryCount: number;
  errorMessage?: string;
  createdTime: string;
  lastRetryTime?: string;
  /** 上传成功后回填的文件 ID，BIND_FAILED 时用于重新绑定。 */
  fileId?: string;
  /** 绑定关系类型，默认 ATTACHMENT。 */
  relType?: string;
}

/** 单文件最大重试次数。 */
export const MAX_RETRY = 3;

const STORAGE_KEY_PREFIX = 'upload:queue:';

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

function storageKey(): string {
  return STORAGE_KEY_PREFIX + getUserId();
}

function genLocalId(): string {
  return 'up_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}

// 模块级单例队列（从本地存储恢复，跨页面/重启共享）
let queue: UploadTask[] = loadQueue();
const listeners = new Set<() => void>();
let processing = false;
let networkUnsub: (() => void) | null = null;

function loadQueue(): UploadTask[] {
  try {
    const raw = uni.getStorageSync(storageKey());
    return raw ? (JSON.parse(raw) as UploadTask[]) : [];
  } catch {
    return [];
  }
}

function persist(): void {
  try {
    uni.setStorageSync(storageKey(), JSON.stringify(queue));
  } catch (e) {
    console.error('[uploadQueue] persist failed', e);
  }
}

function notify(): void {
  persist();
  listeners.forEach((cb) => {
    try {
      cb();
    } catch (e) {
      console.error('[uploadQueue] listener error', e);
    }
  });
}

/** 订阅队列变更，返回取消订阅函数（供 UI 列表刷新）。 */
export function onQueueChange(cb: () => void): () => void {
  listeners.add(cb);
  return () => {
    listeners.delete(cb);
  };
}

export interface EnqueueInput {
  filePath: string;
  fileName: string;
  fileSize: number;
  bizType?: string;
  bizId?: string;
  relType?: string;
}

/** 加入上传队列，返回新建任务。 */
export function enqueue(input: EnqueueInput): UploadTask {
  const item: UploadTask = {
    localId: genLocalId(),
    filePath: input.filePath,
    fileName: input.fileName,
    fileSize: input.fileSize,
    bizType: input.bizType || '',
    bizId: input.bizId || '',
    status: 'PENDING',
    retryCount: 0,
    createdTime: new Date().toISOString(),
    relType: input.relType || 'ATTACHMENT',
  };
  queue.push(item);
  notify();
  return item;
}

/** 读取当前队列快照（不可变副本）。 */
export function getQueue(): UploadTask[] {
  return queue.slice();
}

/** 删除指定任务。 */
export function removeTask(localId: string): void {
  queue = queue.filter((t) => t.localId !== localId);
  notify();
}

/** 清空队列（保留鉴权状态）。 */
export function clearQueue(): void {
  queue = [];
  notify();
}

/** 退出登录时清理当前用户的上传队列（敏感缓存清理）。 */
export function clearUploadQueueForUser(): void {
  queue = [];
  persist();
}

async function processTask(item: UploadTask): Promise<void> {
  item.status = 'UPLOADING';
  item.lastRetryTime = new Date().toISOString();
  notify();
  try {
    const sysFile = await uploadFile(item.filePath);
    item.fileId = sysFile.id;
    // 上传成功，若带 bizType+bizId 则自动绑定
    if (item.bizType && item.bizId) {
      try {
        await bindFile({
          bizType: item.bizType,
          bizId: item.bizId,
          fileId: sysFile.id,
          relType: item.relType,
        });
        item.status = 'SUCCESS';
        item.errorMessage = undefined;
      } catch (e) {
        // 文件已上传成功，但业务绑定失败
        item.status = 'BIND_FAILED';
        item.errorMessage = e instanceof Error ? e.message : '绑定失败';
      }
    } else {
      item.status = 'SUCCESS';
      item.errorMessage = undefined;
    }
  } catch (e) {
    item.retryCount += 1;
    if (item.retryCount >= MAX_RETRY) {
      item.status = 'FAILED';
    } else {
      item.status = 'PENDING';
    }
    item.errorMessage = e instanceof Error ? e.message : '上传失败';
  }
  notify();
}

/** 手动重试单个任务（网络恢复或用户点击）。 */
export async function retryTask(localId: string): Promise<void> {
  const item = queue.find((t) => t.localId === localId);
  if (!item || item.status === 'UPLOADING') return;

  // BIND_FAILED 且有 fileId：仅重新绑定，不重新上传
  if (item.status === 'BIND_FAILED' && item.fileId) {
    item.status = 'UPLOADING';
    item.lastRetryTime = new Date().toISOString();
    notify();
    try {
      await bindFile({
        bizType: item.bizType,
        bizId: item.bizId,
        fileId: item.fileId,
        relType: item.relType,
      });
      item.status = 'SUCCESS';
      item.errorMessage = undefined;
    } catch (e) {
      item.status = 'BIND_FAILED';
      item.errorMessage = e instanceof Error ? e.message : '绑定失败';
    }
    notify();
    return;
  }

  await processTask(item);
}

/** 重试所有可重试任务（PENDING / FAILED / BIND_FAILED）。 */
export async function retryAll(): Promise<void> {
  if (processing) return;
  // 离线时不重试，等待网络恢复
  if (isOffline.value) return;
  const pending = queue.filter(
    (t) => t.status === 'PENDING' || t.status === 'FAILED' || t.status === 'BIND_FAILED',
  );
  if (pending.length === 0) return;
  processing = true;
  try {
    for (const item of pending) {
      await retryTask(item.localId);
    }
  } finally {
    processing = false;
  }
}

/**
 * 初始化上传队列：注册网络恢复自动重试。
 * 在 App.vue onLaunch 中调用（幂等）。
 */
export function initUploadQueue(): void {
  if (networkUnsub) return;
  networkUnsub = onNetworkOnline(() => {
    // 仅当非离线时触发重试
    void retryAll();
  });
}
