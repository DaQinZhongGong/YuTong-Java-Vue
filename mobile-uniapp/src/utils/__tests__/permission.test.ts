import { describe, it, expect, beforeEach } from 'vitest';
import { checkPermission } from '../permission';
import { hasPermission, setUser, removeUser, getUser } from '@/store/auth';

/**
 * 权限判定测试。
 *
 * - checkPermission 是 permission.ts 暴露的工具，内部委托 store/auth 的 hasPermission。
 * - hasPermission 基于当前登录用户的 permissions 数组：
 *     permissions 未加载 → 放行（基础接入策略）
 *     permissions 含 "*" → admin 通配放行
 *     否则精确 includes 匹配
 *
 * 用户状态通过 setUser / removeUser 公共 API 设置，依赖 vitest.setup.ts 中的 uni.* 桩。
 */
describe('permission', () => {
  beforeEach(() => {
    removeUser();
  });

  describe('checkPermission', () => {
    it('未加载 permissions（未登录）→ 放行 true（基础接入策略）', () => {
      expect(getUser()).toBeNull();
      expect(checkPermission('mobile:scan:use')).toBe(true);
    });

    it('admin 的 "*" 通配放行', () => {
      setUser({ id: 'u-admin', username: 'admin', permissions: ['*'] });
      expect(checkPermission('mobile:scan:use')).toBe(true);
      expect(checkPermission('any:other:code')).toBe(true);
      expect(checkPermission('admin:system:shutdown')).toBe(true);
    });

    it('普通用户精确匹配', () => {
      setUser({
        id: 'u-user',
        username: 'alice',
        permissions: ['mobile:scan:use', 'mobile:todo:view'],
      });
      expect(checkPermission('mobile:scan:use')).toBe(true);
      expect(checkPermission('mobile:todo:view')).toBe(true);
    });

    it('无权限返回 false', () => {
      setUser({
        id: 'u-user',
        username: 'bob',
        permissions: ['mobile:todo:view'],
      });
      expect(checkPermission('mobile:scan:use')).toBe(false);
      expect(checkPermission('admin:delete')).toBe(false);
    });

    it('空 permissions 数组：非空 code 全部拒绝', () => {
      setUser({ id: 'u-empty', username: 'empty', permissions: [] });
      expect(checkPermission('mobile:scan:use')).toBe(false);
    });
  });

  describe('hasPermission（直接测试 store/auth）', () => {
    it('未加载 permissions → 放行', () => {
      expect(hasPermission('any:code')).toBe(true);
    });

    it('"*" 通配', () => {
      setUser({ id: 'a', username: 'admin', permissions: ['*'] });
      expect(hasPermission('x:y:z')).toBe(true);
    });

    it('精确匹配命中', () => {
      setUser({ id: 'b', username: 'u', permissions: ['mobile:detail:view'] });
      expect(hasPermission('mobile:detail:view')).toBe(true);
    });

    it('精确匹配未命中返回 false', () => {
      setUser({ id: 'c', username: 'u', permissions: ['mobile:detail:view'] });
      expect(hasPermission('mobile:detail:approve')).toBe(false);
    });

    it('无 "*" 且无匹配 → false', () => {
      setUser({ id: 'd', username: 'u', permissions: ['a:b', 'c:d'] });
      expect(hasPermission('a:b:c')).toBe(false);
    });
  });
});
