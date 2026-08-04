import { vi, beforeEach } from 'vitest';

/**
 * 全局 uni.* 存储桩。
 *
 * 用内存 Map 模拟 uni.getStorageSync / setStorageSync / removeStorageSync /
 * getStorageInfoSync，使依赖 uni 存储的 utils（permission / offlineCache 等）
 * 可在 node 环境运行，无需引入 uniapp 运行时。
 *
 * 每个 case 之间清空，保证测试隔离。
 */
const storage = new Map<string, unknown>();

const uniMock = {
  getStorageSync(key: string): unknown {
    return storage.has(key) ? storage.get(key) : '';
  },
  setStorageSync(key: string, data: unknown): void {
    storage.set(key, data);
  },
  removeStorageSync(key: string): void {
    storage.delete(key);
  },
  getStorageInfoSync(): { keys: string[] } {
    return { keys: Array.from(storage.keys()) };
  },
};

vi.stubGlobal('uni', uniMock);

beforeEach(() => {
  storage.clear();
});
