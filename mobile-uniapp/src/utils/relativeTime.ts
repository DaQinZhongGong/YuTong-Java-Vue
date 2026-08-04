/**
 * 相对时间格式化。
 *
 * 传入 ISO 时间字符串、时间戳或 Date，返回 "刚刚 / X分钟前 / X小时前 / X天前"。
 * 超过 30 天回退为 YYYY-MM-DD，避免展示 "365 天前" 这类无意义文案。
 *
 * 设计来源: P1-2 详情页审批记录时间线相对时间展示。
 */

export function relativeTime(input: string | number | Date | null | undefined): string {
  if (input == null || input === '') return '';
  const date = input instanceof Date ? input : new Date(input);
  const ts = date.getTime();
  if (Number.isNaN(ts)) return '';

  const diff = Date.now() - ts;
  // 未来时间直接显示 "刚刚"
  if (diff < 0) return '刚刚';

  const sec = Math.floor(diff / 1000);
  if (sec < 60) return '刚刚';
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}分钟前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}小时前`;
  const day = Math.floor(hour / 24);
  if (day < 30) return `${day}天前`;

  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}
