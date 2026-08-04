/**
 * 金额格式化（按 string 处理，避免大数转 Number 精度丢失）。
 *
 * 规则：每 3 位逗号分隔，保留 2 位小数。
 * 若入参为 number，先 toString 再格式化。
 *
 * 示例：
 *   formatMoney("1234567.8")  → "1,234,567.80"
 *   formatMoney(1234567)       → "1,234,567.00"
 *   formatMoney("-1234.5")     → "-1,234.50"
 *
 * 设计来源: P1-3 详情页金额展示（后端可能以 string 返回）。
 */

export function formatMoney(amount: string | number | null | undefined): string {
  if (amount == null || amount === '') return '0.00';
  const str = typeof amount === 'number' ? String(amount) : String(amount).trim();
  if (str === '' || str === '-' || str === '.') return '0.00';

  const negative = str.startsWith('-');
  const positive = negative ? str.slice(1) : str;

  let [intPart, decPart] = positive.split('.');
  if (intPart === undefined) intPart = '';
  if (decPart === undefined) decPart = '';

  // 去除整数部分前导 0（保留单个 0）
  intPart = intPart.replace(/^0+(?=\d)/, '');
  if (intPart === '') intPart = '0';

  // 小数补齐 / 截断到 2 位（不做四舍五入，避免引入 Number 精度问题）
  decPart = (decPart + '00').slice(0, 2);

  // 每 3 位加逗号
  intPart = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');

  return (negative ? '-' : '') + intPart + '.' + decPart;
}
