/**
 * 规范路径 ↔ 实际路径映射（P0-5）。
 *
 * 背景：GA 路由基线（docs/contracts/registries/routes.yaml）登记的规范路径
 * 与 mobile-uniapp/src/pages.json 中实际文件路径存在系统性偏移（5 条）：
 *   规范 pages/workbench/index  ↔ 实际 pages/workbench/workbench
 *   规范 pages/login/index      ↔ 实际 pages/login/login
 *   规范 pages/todo/list        ↔ 实际 pages/todo/todo
 *   规范 pages/mine/index       ↔ 实际 pages/mine/mine
 *   规范 pages/upload/index     ↔ 实际 pages/mine/uploadQueue
 *
 * 约束：不重命名文件（避免破坏既有引用）。因此内部 uni.navigateTo / switchTab /
 * reLaunch 调用统一使用“实际路径”；外部跳转来源（如推送通知、深度链接、扫码解析
 * 返回的规范路径）必须先经本工具映射为实际路径后再跳转，否则会 404。
 *
 * 说明：uniapp 的 pages.json 不原生支持按 aliasPath 路由（pages.json 中新增的
 * aliasPath 字段仅作映射登记，运行时不参与路由匹配），故外部跳转必须显式映射。
 */

/** 规范路径（无前导 /）→ 实际页面路径（无前导 /）。 */
const CANONICAL_TO_ACTUAL: Record<string, string> = {
  'pages/workbench/index': 'pages/workbench/workbench',
  'pages/login/index': 'pages/login/login',
  'pages/todo/list': 'pages/todo/todo',
  'pages/mine/index': 'pages/mine/mine',
  'pages/upload/index': 'pages/mine/uploadQueue',
};

/** 反向映射：实际路径 → 规范路径。 */
const ACTUAL_TO_CANONICAL: Record<string, string> = Object.entries(
  CANONICAL_TO_ACTUAL,
).reduce<Record<string, string>>((acc, [canonical, actual]) => {
  acc[actual] = canonical;
  return acc;
}, {});

/**
 * 将规范路径解析为可用于 uni.navigateTo / switchTab 的实际路径。
 * 输入可带或不带前导 `/`，可带 query（query 原样保留）。
 * 若输入已是实际路径或未登记的路径，则原样返回（按实际路径处理）。
 */
export function resolveActualPath(canonical: string): string {
  if (!canonical) return canonical;
  // 拆分 query 与 hash
  const queryIdx = canonical.search(/[?#]/);
  const base = queryIdx >= 0 ? canonical.slice(0, queryIdx) : canonical;
  const suffix = queryIdx >= 0 ? canonical.slice(queryIdx) : '';
  const normalized = base.replace(/^\/+/, '');
  const actual = CANONICAL_TO_ACTUAL[normalized] || normalized;
  return '/' + actual + suffix;
}

/** 查询某规范路径对应的实际路径（无前导 /，无 query）。未登记返回入参原值。 */
export function toActualPath(canonical: string): string {
  return CANONICAL_TO_ACTUAL[canonical.replace(/^\/+/, '')] || canonical;
}

/** 查询某实际路径对应的规范路径（无前导 /）。未登记返回入参原值。 */
export function toCanonicalPath(actual: string): string {
  return ACTUAL_TO_CANONICAL[actual.replace(/^\/+/, '')] || actual;
}

/** 完整映射表（只读，便于排查或上报）。 */
export const ROUTE_ALIAS_MAP: Readonly<Record<string, string>> =
  Object.freeze({ ...CANONICAL_TO_ACTUAL });
