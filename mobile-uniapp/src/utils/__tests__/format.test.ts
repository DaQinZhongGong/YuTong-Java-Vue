import { describe, it, expect } from 'vitest';
import { formatMoney } from '../format';

describe('formatMoney', () => {
  describe('任务要求用例', () => {
    it('formatMoney("1234567.8") → "1,234,567.80"', () => {
      expect(formatMoney('1234567.8')).toBe('1,234,567.80');
    });

    it('formatMoney(1234567.8) → "1,234,567.80"', () => {
      expect(formatMoney(1234567.8)).toBe('1,234,567.80');
    });

    it('formatMoney("0") → "0.00"', () => {
      expect(formatMoney('0')).toBe('0.00');
    });

    it('formatMoney("") → "0.00"', () => {
      expect(formatMoney('')).toBe('0.00');
    });

    it('formatMoney("1000") → "1,000.00"', () => {
      expect(formatMoney('1000')).toBe('1,000.00');
    });

    it('formatMoney("999999999.99") → "999,999,999.99"（大数精度）', () => {
      expect(formatMoney('999999999.99')).toBe('999,999,999.99');
    });
  });

  describe('空值与边界', () => {
    it('null → "0.00"', () => {
      expect(formatMoney(null)).toBe('0.00');
    });

    it('undefined → "0.00"', () => {
      expect(formatMoney(undefined)).toBe('0.00');
    });

    it('"-" → "0.00"', () => {
      expect(formatMoney('-')).toBe('0.00');
    });

    it('"." → "0.00"', () => {
      expect(formatMoney('.')).toBe('0.00');
    });

    it('带空白的字符串会被 trim', () => {
      expect(formatMoney('  1234  ')).toBe('1,234.00');
    });
  });

  describe('整数与小数处理', () => {
    it('整数补两位小数 (number)', () => {
      expect(formatMoney(1234567)).toBe('1,234,567.00');
    });

    it('整数补两位小数 (string)', () => {
      expect(formatMoney('1000000')).toBe('1,000,000.00');
    });

    it('一位小数补齐到两位', () => {
      expect(formatMoney('1.5')).toBe('1.50');
    });

    it('三位以上小数截断（不四舍五入，避免大数精度问题）', () => {
      expect(formatMoney('1.999')).toBe('1.99');
      expect(formatMoney('1.009')).toBe('1.00');
    });

    it('去除整数部分前导 0（保留单个 0）', () => {
      expect(formatMoney('001234')).toBe('1,234.00');
      expect(formatMoney('000')).toBe('0.00');
    });
  });

  describe('负数与千分位', () => {
    it('负数金额保留负号', () => {
      expect(formatMoney('-1234.5')).toBe('-1,234.50');
    });

    it('负数整数补两位小数', () => {
      expect(formatMoney('-1000')).toBe('-1,000.00');
    });

    it('多位千分位分隔', () => {
      expect(formatMoney('1000000000')).toBe('1,000,000,000.00');
    });

    it('恰好 3 位整数不加多余逗号', () => {
      expect(formatMoney('123')).toBe('123.00');
    });

    it('恰好 4 位整数加一个逗号', () => {
      expect(formatMoney('1234')).toBe('1,234.00');
    });
  });
});
