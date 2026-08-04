import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { relativeTime } from '../relativeTime';

/**
 * 使用 fake timer 固定 Date.now()，使各分支用例稳定可复现。
 * 基准时间：2026-01-15T12:00:00.000Z（UTC）。
 */
const NOW_MS = Date.parse('2026-01-15T12:00:00.000Z');

describe('relativeTime', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(NOW_MS));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('空值与非法输入', () => {
    it('null → ""', () => {
      expect(relativeTime(null)).toBe('');
    });

    it('undefined → ""', () => {
      expect(relativeTime(undefined)).toBe('');
    });

    it('空字符串 → ""', () => {
      expect(relativeTime('')).toBe('');
    });

    it('非法日期字符串 → ""', () => {
      expect(relativeTime('not-a-date')).toBe('');
    });
  });

  describe('任务要求分支', () => {
    it('刚刚（<60s）', () => {
      expect(relativeTime(NOW_MS - 30 * 1000)).toBe('刚刚');
    });

    it('X分钟前（<60min）', () => {
      expect(relativeTime(NOW_MS - 5 * 60 * 1000)).toBe('5分钟前');
    });

    it('X小时前（<24h）', () => {
      expect(relativeTime(NOW_MS - 3 * 60 * 60 * 1000)).toBe('3小时前');
    });

    it('X天前（<30d）', () => {
      expect(relativeTime(NOW_MS - 5 * 24 * 60 * 60 * 1000)).toBe('5天前');
    });

    it('超过30天回退 YYYY-MM-DD', () => {
      // 36 天前：2025-12-10 (UTC)
      const past = NOW_MS - 36 * 24 * 60 * 60 * 1000;
      const d = new Date(past);
      const expected =
        `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      expect(relativeTime(past)).toBe(expected);
    });
  });

  describe('边界值', () => {
    it('刚好 0 秒前 → 刚刚', () => {
      expect(relativeTime(NOW_MS)).toBe('刚刚');
    });

    it('59 秒前 → 刚刚', () => {
      expect(relativeTime(NOW_MS - 59 * 1000)).toBe('刚刚');
    });

    it('60 秒前 → 1分钟前（边界）', () => {
      expect(relativeTime(NOW_MS - 60 * 1000)).toBe('1分钟前');
    });

    it('59 分钟前 → 59分钟前', () => {
      expect(relativeTime(NOW_MS - 59 * 60 * 1000)).toBe('59分钟前');
    });

    it('60 分钟前 → 1小时前（边界）', () => {
      expect(relativeTime(NOW_MS - 60 * 60 * 1000)).toBe('1小时前');
    });

    it('23 小时前 → 23小时前', () => {
      expect(relativeTime(NOW_MS - 23 * 60 * 60 * 1000)).toBe('23小时前');
    });

    it('24 小时前 → 1天前（边界）', () => {
      expect(relativeTime(NOW_MS - 24 * 60 * 60 * 1000)).toBe('1天前');
    });

    it('29 天前 → 29天前', () => {
      expect(relativeTime(NOW_MS - 29 * 24 * 60 * 60 * 1000)).toBe('29天前');
    });

    it('30 天前 → 回退 YYYY-MM-DD（边界）', () => {
      const past = NOW_MS - 30 * 24 * 60 * 60 * 1000;
      const d = new Date(past);
      const expected =
        `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      expect(relativeTime(past)).toBe(expected);
    });

    it('未来时间 → 刚刚', () => {
      expect(relativeTime(NOW_MS + 60 * 1000)).toBe('刚刚');
    });
  });

  describe('不同入参类型', () => {
    it('接受 ISO 字符串', () => {
      const iso = new Date(NOW_MS - 10 * 60 * 1000).toISOString();
      expect(relativeTime(iso)).toBe('10分钟前');
    });

    it('接受 Date 对象', () => {
      const d = new Date(NOW_MS - 2 * 60 * 60 * 1000);
      expect(relativeTime(d)).toBe('2小时前');
    });

    it('接受时间戳 (number)', () => {
      expect(relativeTime(NOW_MS - 4 * 60 * 60 * 1000)).toBe('4小时前');
    });
  });
});
