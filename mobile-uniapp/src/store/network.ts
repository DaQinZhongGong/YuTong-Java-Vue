/**
 * 全局网络状态 store（函数式，风格与 auth.ts 一致）。
 *
 * 设计来源: 11-弱网与离线策略详设 / 54-移动端离线能力设计。
 *
 * 职责:
 *  - 通过 uni.getNetworkType / uni.onNetworkStatusChange 监听端侧网络状态
 *  - 维护全局网络类型、是否离线、是否弱网三个响应式状态
 *  - 提供 onNetworkOnline 订阅，供上传队列在网络恢复后自动重试
 *
 * 说明: 采用 Vue 的 ref 作为响应式单一数据源；由于并非 Pinia，
 * 本 store 以模块级单例暴露状态与操作方法，跨页面共享同一实例。
 */

import { ref } from 'vue';
import { track } from '@/utils/tracker';

/** 端侧网络类型枚举（与 uni.getNetworkType 返回值对齐）。 */
export type NetworkType =
  | 'wifi'
  | '2g'
  | '3g'
  | '4g'
  | '5g'
  | 'ethernet'
  | 'unknown'
  | 'none';

/** 当前网络类型。 */
export const networkType = ref<NetworkType>('unknown');
/** 是否离线（无网络，networkType === 'none'）。 */
export const isOffline = ref(false);
/** 是否弱网（2g/3g 视为弱网）。none 已归入离线，不重复计入弱网。 */
export const isWeakNetwork = ref(false);

/** 网络恢复（从离线恢复为可用）订阅集合。 */
const onlineListeners = new Set<() => void>();

/**
 * 注册网络恢复回调，返回取消订阅函数。
 * 当网络从离线恢复为可用时触发（弱网切换不视为"恢复"，仅离线->在线触发）。
 */
export function onNetworkOnline(cb: () => void): () => void {
  onlineListeners.add(cb);
  return () => {
    onlineListeners.delete(cb);
  };
}

/**
 * 应用一次网络类型变更，更新全局状态并在"离线恢复"时通知订阅者。
 * @param type 最新网络类型
 */
export function applyNetworkType(type: NetworkType): void {
  const wasOffline = isOffline.value;
  networkType.value = type;
  const offline = type === 'none';
  isOffline.value = offline;
  // 弱网: 2g/3g（若业务需把 none 也算弱网，此处已归离线，不再叠加）
  isWeakNetwork.value = type === '2g' || type === '3g';

  // P1-5: 网络切换埋点（仅状态变化时上报）
  if (offline && !wasOffline) {
    track('mobile.network.offline');
  }
  if (!offline && wasOffline) {
    track('mobile.network.recovered');
  }

  // 仅在"从离线恢复为可用"时触发上传队列重试等逻辑
  if (!offline) {
    onlineListeners.forEach((cb) => {
      try {
        cb();
      } catch (e) {
        console.error('[network] online listener error', e);
      }
    });
  }
}

let initialized = false;

/**
 * 初始化全局网络监听。幂等，重复调用只生效一次。
 * 在 App.vue onLaunch 中调用。
 */
export function initNetworkMonitor(): void {
  if (initialized) return;
  initialized = true;
  try {
    uni.getNetworkType({
      success: (res) => {
        applyNetworkType((res.networkType as NetworkType) || 'unknown');
      },
      fail: () => {
        applyNetworkType('unknown');
      },
    });
    uni.onNetworkStatusChange((res) => {
      applyNetworkType((res.networkType as NetworkType) || 'unknown');
    });
  } catch (e) {
    console.error('[network] init network monitor failed', e);
  }
}
