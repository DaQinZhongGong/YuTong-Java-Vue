import { describe, it, expect } from 'vitest';
import {
  resolveActualPath,
  toActualPath,
  toCanonicalPath,
  ROUTE_ALIAS_MAP,
} from '../route-alias';

/**
 * 路由别名映射测试。
 *
 * 规范路径 ↔ 实际路径 5 条偏移：
 *   pages/workbench/index  ↔ pages/workbench/workbench
 *   pages/login/index      ↔ pages/login/login
 *   pages/todo/list        ↔ pages/todo/todo
 *   pages/mine/index       ↔ pages/mine/mine
 *   pages/upload/index     ↔ pages/mine/uploadQueue
 */
describe('route-alias', () => {
  describe('resolveActualPath', () => {
    it('规范 workbench → 实际', () => {
      expect(resolveActualPath('pages/workbench/index')).toBe(
        '/pages/workbench/workbench',
      );
    });

    it('规范 login → 实际', () => {
      expect(resolveActualPath('pages/login/index')).toBe('/pages/login/login');
    });

    it('规范 todo/list → 实际', () => {
      expect(resolveActualPath('pages/todo/list')).toBe('/pages/todo/todo');
    });

    it('规范 mine/index → 实际', () => {
      expect(resolveActualPath('pages/mine/index')).toBe('/pages/mine/mine');
    });

    it('规范 upload/index → 实际', () => {
      expect(resolveActualPath('pages/upload/index')).toBe(
        '/pages/mine/uploadQueue',
      );
    });

    it('带前导 / 也能正确映射', () => {
      expect(resolveActualPath('/pages/workbench/index')).toBe(
        '/pages/workbench/workbench',
      );
    });

    it('保留 query 字符串', () => {
      expect(resolveActualPath('pages/workbench/index?foo=bar&x=1')).toBe(
        '/pages/workbench/workbench?foo=bar&x=1',
      );
    });

    it('保留 hash', () => {
      expect(resolveActualPath('pages/workbench/index#section')).toBe(
        '/pages/workbench/workbench#section',
      );
    });

    it('query + hash 同时保留', () => {
      expect(resolveActualPath('pages/login/index?a=1#anchor')).toBe(
        '/pages/login/login?a=1#anchor',
      );
    });

    it('未知路径返回带前导 / 的原值（按实际路径处理）', () => {
      expect(resolveActualPath('pages/unknown/foo')).toBe('/pages/unknown/foo');
    });

    it('已是实际路径时原样返回（带前导 /）', () => {
      expect(resolveActualPath('pages/workbench/workbench')).toBe(
        '/pages/workbench/workbench',
      );
    });

    it('空字符串原样返回', () => {
      expect(resolveActualPath('')).toBe('');
    });
  });

  describe('toActualPath', () => {
    it('规范路径 → 实际路径（无前导 /）', () => {
      expect(toActualPath('pages/workbench/index')).toBe(
        'pages/workbench/workbench',
      );
      expect(toActualPath('pages/login/index')).toBe('pages/login/login');
      expect(toActualPath('pages/todo/list')).toBe('pages/todo/todo');
      expect(toActualPath('pages/mine/index')).toBe('pages/mine/mine');
      expect(toActualPath('pages/upload/index')).toBe(
        'pages/mine/uploadQueue',
      );
    });

    it('未知路径返回入参原值', () => {
      expect(toActualPath('pages/unknown/foo')).toBe('pages/unknown/foo');
    });

    it('带前导 / 会先剥离再查表', () => {
      expect(toActualPath('/pages/login/index')).toBe('pages/login/login');
    });
  });

  describe('toCanonicalPath', () => {
    it('实际路径 → 规范路径', () => {
      expect(toCanonicalPath('pages/workbench/workbench')).toBe(
        'pages/workbench/index',
      );
      expect(toCanonicalPath('pages/login/login')).toBe('pages/login/index');
      expect(toCanonicalPath('pages/todo/todo')).toBe('pages/todo/list');
      expect(toCanonicalPath('pages/mine/mine')).toBe('pages/mine/index');
      expect(toCanonicalPath('pages/mine/uploadQueue')).toBe(
        'pages/upload/index',
      );
    });

    it('未知实际路径返回入参原值', () => {
      expect(toCanonicalPath('pages/unknown/foo')).toBe('pages/unknown/foo');
    });
  });

  describe('ROUTE_ALIAS_MAP', () => {
    it('包含 5 条映射', () => {
      expect(Object.keys(ROUTE_ALIAS_MAP)).toHaveLength(5);
    });

    it('被 Object.freeze 冻结（只读）', () => {
      expect(Object.isFrozen(ROUTE_ALIAS_MAP)).toBe(true);
    });

    it('包含 workbench 映射', () => {
      expect(ROUTE_ALIAS_MAP['pages/workbench/index']).toBe(
        'pages/workbench/workbench',
      );
    });

    it('包含 upload → uploadQueue 映射', () => {
      expect(ROUTE_ALIAS_MAP['pages/upload/index']).toBe(
        'pages/mine/uploadQueue',
      );
    });
  });

  describe('双向映射一致性', () => {
    it('toActualPath(toCanonicalPath(actual)) === actual', () => {
      const actuals = [
        'pages/workbench/workbench',
        'pages/login/login',
        'pages/todo/todo',
        'pages/mine/mine',
        'pages/mine/uploadQueue',
      ];
      for (const actual of actuals) {
        expect(toActualPath(toCanonicalPath(actual))).toBe(actual);
      }
    });
  });
});
