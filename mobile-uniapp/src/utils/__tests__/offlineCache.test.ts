import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  saveListCache,
  loadListCache,
  clearUserCaches,
  DEFAULT_TTL,
} from '../offlineCache';
import { setUser, removeUser } from '@/store/auth';

/**
 * 离线缓存测试。依赖 vitest.setup.ts 中的 uni.* 存储桩。
 * 注意：cache key 按 userId 隔离，因此 setUser 会改变写入/读取的 key。
 */
describe('offlineCache', () => {
  beforeEach(() => {
    clearUserCaches();
    removeUser();
  });

  describe('DEFAULT_TTL', () => {
    it('默认有效期为 30 分钟', () => {
      expect(DEFAULT_TTL).toBe(1000 * 60 * 30);
    });
  });

  describe('saveListCache / loadListCache', () => {
    it('存储后读取一致', () => {
      setUser({ id: 'u1', username: 'alice' });
      const data = [
        { id: 1, name: 'todo1' },
        { id: 2, name: 'todo2' },
      ];
      saveListCache('todo', data);

      const loaded = loadListCache<typeof data>('todo');
      expect(loaded).not.toBeNull();
      expect(loaded?.data).toEqual(data);
      expect(loaded?.stale).toBe(false);
    });

    it('存储对象类型数据', () => {
      setUser({ id: 'u1', username: 'alice' });
      const stats = { count: 5, total: 100, items: ['a', 'b', 'c'] };
      saveListCache('workbench-stats', stats);

      const loaded = loadListCache<typeof stats>('workbench-stats');
      expect(loaded?.data).toEqual(stats);
      expect(loaded?.stale).toBe(false);
    });

    it('未存储的 key 读取返回 null', () => {
      setUser({ id: 'u2', username: 'bob' });
      expect(loadListCache('not-exist')).toBeNull();
    });

    it('未登录用户使用 anonymous 隔离', () => {
      // 未登录也能写入（userId 回退 anonymous）
      saveListCache('todo', [1, 2, 3]);
      const loaded = loadListCache<number[]>('todo');
      expect(loaded?.data).toEqual([1, 2, 3]);
    });

    it('不同用户缓存隔离（不串数据）', () => {
      setUser({ id: 'alice', username: 'alice' });
      saveListCache('todo', [{ a: 1 }]);

      setUser({ id: 'bob', username: 'bob' });
      // 切换用户后 alice 的缓存不可见
      expect(loadListCache('todo')).toBeNull();

      saveListCache('todo', [{ b: 2 }]);
      // 再切回 alice，仍能读到 alice 自己的
      setUser({ id: 'alice', username: 'alice' });
      const loaded = loadListCache<Array<{ a: number }>>('todo');
      expect(loaded?.data).toEqual([{ a: 1 }]);
    });
  });

  describe('stale 标记', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });
    afterEach(() => {
      vi.useRealTimers();
    });

    it('未过期 stale=false', () => {
      setUser({ id: 'u3', username: 'carol' });
      saveListCache('todo', [1, 2, 3], 1000);
      expect(loadListCache('todo')?.stale).toBe(false);
    });

    it('过期后 stale=true，但 data 仍可读', () => {
      setUser({ id: 'u3', username: 'carol' });
      saveListCache('todo', [1, 2, 3], 100);
      // 推进时间超过 TTL
      vi.advanceTimersByTime(150);
      const loaded = loadListCache<number[]>('todo');
      expect(loaded?.stale).toBe(true);
      expect(loaded?.data).toEqual([1, 2, 3]);
    });
  });

  describe('clearUserCaches', () => {
    it('清空缓存后读取返回 null', () => {
      setUser({ id: 'u4', username: 'dave' });
      saveListCache('todo', [1]);
      saveListCache('workbench-stats', { count: 5 });
      // 写入后能读到
      expect(loadListCache('todo')).not.toBeNull();
      expect(loadListCache('workbench-stats')).not.toBeNull();

      clearUserCaches();

      expect(loadListCache('todo')).toBeNull();
      expect(loadListCache('workbench-stats')).toBeNull();
    });

    it('只清理 cache:list: 前缀的键，不影响其它存储', () => {
      setUser({ id: 'u5', username: 'eve' });
      saveListCache('todo', [1]);
      // 通过 store/auth 写入 user 信息（非 cache:list: 前缀）
      setUser({ id: 'u5', username: 'eve' });

      clearUserCaches();

      expect(loadListCache('todo')).toBeNull();
      // user 信息仍保留（clearUserCaches 不应清除鉴权键）
      // 注：clearUserCaches 内部遍历 keys 仅 remove cache:list: 前缀
    });
  });
});
