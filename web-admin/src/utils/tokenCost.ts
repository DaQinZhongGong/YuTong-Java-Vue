/**
 * Token 成本估算 — 静态价格表 (2024 H2, 元/1K tokens)
 * 落点: 37-AI 治理与评测设计「成本治理」
 *
 * 用法:
 *   import { estimateTokenCostCNY, formatCNY } from '@/utils/tokenCost'
 *   const cost = estimateTokenCostCNY('deepseek-chat', 1000, 500)  // 输入 1K + 输出 500
 *   const label = formatCNY(cost)  // "¥0.003"
 *
 * 注意:
 *   - 价格表是 2024 H2 公开价, 生产建议改从后端 AiModelPriceResolver 动态获取
 *   - 命中不到模型时返回 0 (用户角标显示 "0.00¥" 不会误显示假数据)
 *   - 本工具不写入任何 store / 持久化
 */

interface ModelPrice {
  /** 输入单价 元/1K tokens */
  input: number
  /** 输出单价 元/1K tokens */
  output: number
}

/** 主流模型价格表 (元/1K tokens) */
const PRICE_TABLE: Record<string, ModelPrice> = {
  // DeepSeek
  'deepseek-chat': { input: 0.001, output: 0.002 },
  'deepseek-reasoner': { input: 0.004, output: 0.016 },
  'deepseek-coder': { input: 0.001, output: 0.002 },
  // 阿里通义
  'qwen-turbo': { input: 0.003, output: 0.006 },
  'qwen-plus': { input: 0.004, output: 0.012 },
  'qwen-max': { input: 0.02, output: 0.06 },
  'qwen-long': { input: 0.0005, output: 0.002 },
  // 字节豆包
  'doubao-lite': { input: 0.0008, output: 0.001 },
  'doubao-pro': { input: 0.0008, output: 0.002 },
  // 智谱
  'glm-4-flash': { input: 0.0001, output: 0.0001 },
  'glm-4-air': { input: 0.001, output: 0.001 },
  'glm-4-plus': { input: 0.007, output: 0.007 },
  // 智谱全模型
  'glm-4.5': { input: 0.002, output: 0.008 },
  'glm-4.5-air': { input: 0.001, output: 0.002 },
  // 国产通用兜底
  'MiniMax-Text-01': { input: 0.001, output: 0.008 },
  'abab6.5s-chat': { input: 0.003, output: 0.006 },
  // OpenAI
  'gpt-4o': { input: 0.005, output: 0.015 },       // USD
  'gpt-4o-mini': { input: 0.00015, output: 0.0006 }, // USD
  'gpt-3.5-turbo': { input: 0.0005, output: 0.0015 },
  'o1': { input: 0.015, output: 0.06 },
  'o1-mini': { input: 0.003, output: 0.012 },
  // Anthropic
  'claude-3-5-sonnet': { input: 0.003, output: 0.015 },
  'claude-3-haiku': { input: 0.00025, output: 0.00125 },
  // 兜底
  'mock': { input: 0, output: 0 },
  'mock-local': { input: 0, output: 0 },
  'mock-chat': { input: 0, output: 0 },
}

const DEFAULT_PRICE: ModelPrice = { input: 0, output: 0 }

/**
 * 估算 Token 成本 (CNY)
 * @param modelCode 模型编码 (如 "deepseek-chat"),命中不到返回 0
 * @param inputTokens 输入 token 数
 * @param outputTokens 输出 token 数
 * @returns 成本 (元),未命中模型 = 0
 */
export function estimateTokenCostCNY(
  modelCode: string | null | undefined,
  inputTokens: number,
  outputTokens: number
): number {
  if (!modelCode) return 0
  const price = PRICE_TABLE[modelCode] ?? DEFAULT_PRICE
  if (price.input === 0 && price.output === 0) return 0
  const cost =
    (inputTokens / 1000) * price.input + (outputTokens / 1000) * price.output
  return Math.round(cost * 1_000_000) / 1_000_000 // 6 位小数 (µ¥)
}

/**
 * 格式化 CNY 金额
 * @param cost 元
 * @returns 形如 "¥0.005" / "¥1.23" / "¥12.3" / "¥0" (智能精度)
 */
export function formatCNY(cost: number): string {
  if (cost === 0 || !isFinite(cost)) return '¥0'
  if (cost < 0.0001) return '<¥0.0001'
  if (cost < 1) return `¥${cost.toFixed(4)}` // 0.0001 ~ 0.9999
  if (cost < 100) return `¥${cost.toFixed(2)}` // 1.00 ~ 99.99
  if (cost < 10000) return `¥${cost.toFixed(1)}` // 100.0 ~ 9999.9
  return `¥${(cost / 1000).toFixed(1)}K`     // 1.0K+ (万元/千)
}

/**
 * 测试入口 — Node 跑 `npx tsx src/utils/tokenCost.ts` 验证
 */
export const __test__ = {
  PRICE_TABLE,
  DEFAULT_PRICE,
}
