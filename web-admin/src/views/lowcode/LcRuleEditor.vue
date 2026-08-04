<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Delete, Check, VideoPlay } from '@element-plus/icons-vue'
import {
  evaluateRules,
  validateExpression,
} from '@/api/lowcode'
import { LC_RULE_TYPE, LC_RULE_TYPE_OPTIONS } from '@/api/types'
import type {
  LcExpressionValidationResult,
  LcRule,
  LcRuleEvalResult,
} from '@/api/types'
import { track } from '@/utils/tracker'

const { t } = useI18n()

/**
 * 低代码规则与表达式引擎。设计来源: 36-低代码高级能力设计
 *
 * GA2-L191 功能落地:
 *  - 表达式校验器: 输入表达式 → POST /lowcode/rules/validate-expression → 展示 valid/error
 *  - 规则求值器: 动态规则表 + 上下文 JSON → POST /lowcode/rules/evaluate → 按结果类型分组展示
 *  - 内置函数与语法帮助: abs/round/ceil/floor/min/max/upper/lower/length/concat/contains/
 *    substring/now/formatDate/dateAdd/dateDiff/isNull/notNull/isEmpty/isNotEmpty/size/dict/if
 *  - GA2-18 track() 埋点: validate/evaluate 关键事件
 */

interface RuleRow extends LcRule {
  /** 前端临时 ID, 用于 el-table :key */
  _rowKey: string
}

let _rowKeySeq = 0
function genRowKey(): string {
  _rowKeySeq += 1
  return `rule-${Date.now()}-${_rowKeySeq}`
}

/* ============================ 表达式校验器 ============================ */

const exprText = ref('amount > 100 && status == "ACTIVE"')
const exprContextText = ref('{\n  "amount": 200,\n  "status": "ACTIVE"\n}')
const validating = ref(false)
const exprResult = ref<LcExpressionValidationResult | null>(null)

/** 内置函数清单 (供帮助面板展示) */
const BUILTIN_FUNCTIONS: Array<{ name: string; desc: string }> = [
  { name: 'abs(x)', desc: t('lowcode.rule.fn.abs') },
  { name: 'round(x, n)', desc: t('lowcode.rule.fn.round') },
  { name: 'ceil(x)', desc: t('lowcode.rule.fn.ceil') },
  { name: 'floor(x)', desc: t('lowcode.rule.fn.floor') },
  { name: 'min(a, b, ...)', desc: t('lowcode.rule.fn.min') },
  { name: 'max(a, b, ...)', desc: t('lowcode.rule.fn.max') },
  { name: 'upper(s)', desc: t('lowcode.rule.fn.upper') },
  { name: 'lower(s)', desc: t('lowcode.rule.fn.lower') },
  { name: 'length(s)', desc: t('lowcode.rule.fn.length') },
  { name: 'concat(a, b, ...)', desc: t('lowcode.rule.fn.concat') },
  { name: 'contains(s, sub)', desc: t('lowcode.rule.fn.contains') },
  { name: 'substring(s, start, end)', desc: t('lowcode.rule.fn.substring') },
  { name: 'now()', desc: t('lowcode.rule.fn.now') },
  { name: 'formatDate(date, pattern)', desc: t('lowcode.rule.fn.formatDate') },
  { name: 'dateAdd(date, n, unit)', desc: t('lowcode.rule.fn.dateAdd') },
  { name: 'dateDiff(a, b, unit)', desc: t('lowcode.rule.fn.dateDiff') },
  { name: 'isNull(x)', desc: t('lowcode.rule.fn.isNull') },
  { name: 'notNull(x)', desc: t('lowcode.rule.fn.notNull') },
  { name: 'isEmpty(x)', desc: t('lowcode.rule.fn.isEmpty') },
  { name: 'isNotEmpty(x)', desc: t('lowcode.rule.fn.isNotEmpty') },
  { name: 'size(x)', desc: t('lowcode.rule.fn.size') },
  { name: 'dict(code, key)', desc: t('lowcode.rule.fn.dict') },
  { name: 'if(cond, a, b)', desc: t('lowcode.rule.fn.if') },
]

/** 语法示例 */
const SYNTAX_EXAMPLES: Array<{ expr: string; desc: string }> = [
  { expr: 'amount > 100', desc: t('lowcode.rule.syntax.numericComparison') },
  { expr: 'status == "ACTIVE"', desc: t('lowcode.rule.syntax.stringEquality') },
  { expr: 'amount > 100 && status == "ACTIVE"', desc: t('lowcode.rule.syntax.logicalAnd') },
  { expr: 'age >= 18 || hasGuardian', desc: t('lowcode.rule.syntax.logicalOr') },
  { expr: '!isDeleted', desc: t('lowcode.rule.syntax.logicalNot') },
  { expr: 'contains(name, "admin")', desc: t('lowcode.rule.syntax.functionCall') },
  { expr: 'if(amount > 1000, "VIP", "NORMAL")', desc: t('lowcode.rule.syntax.conditionalExpression') },
  { expr: 'items != null && size(items) > 0', desc: t('lowcode.rule.syntax.nullAndLengthCheck') },
]

function parseContext(text: string): Record<string, unknown> | null {
  if (!text || !text.trim()) return {}
  try {
    const obj = JSON.parse(text)
    if (typeof obj !== 'object' || obj === null || Array.isArray(obj)) {
      ElMessage.warning(t('lowcode.rule.msg.contextMustBeObject'))
      return null
    }
    return obj as Record<string, unknown>
  } catch (e) {
    ElMessage.error(t('lowcode.rule.msg.jsonParseFailed'))
    return null
  }
}

async function handleValidate() {
  if (!exprText.value.trim()) {
    ElMessage.warning(t('lowcode.rule.msg.expressionRequired'))
    return
  }
  const ctx = parseContext(exprContextText.value)
  if (ctx === null) return
  validating.value = true
  track('web.lowcode.rule.validate.click', {})
  try {
    const res = await validateExpression(exprText.value, ctx)
    exprResult.value = res
    if (res.valid) {
      ElMessage.success(t('lowcode.rule.msg.validationPassed'))
      track('web.lowcode.rule.validate.success', { payload: { valid: true } })
    } else {
      ElMessage.warning(t('lowcode.rule.msg.validationFailed'))
      track('web.lowcode.rule.validate.failed', {
        result: 'FAILED',
        errorCode: 'LC_EXPR_INVALID',
      })
    }
  } catch (e) {
    ElMessage.error(t('lowcode.rule.msg.validationRequestFailed'))
    exprResult.value = { valid: false, error: t('lowcode.rule.msg.requestError') }
  } finally {
    validating.value = false
  }
}

/* ============================ 规则求值器 ============================ */

const ruleRows = ref<RuleRow[]>([
  {
    _rowKey: genRowKey(),
    ruleId: 'rule-001',
    ruleName: t('lowcode.rule.mock.ruleName1'),
    ruleType: LC_RULE_TYPE.VALIDATION,
    expression: 'amount > 100',
    message: t('lowcode.rule.mock.message1'),
    targetField: 'amount',
    priority: 10,
  },
  {
    _rowKey: genRowKey(),
    ruleId: 'rule-002',
    ruleName: t('lowcode.rule.mock.ruleName2'),
    ruleType: LC_RULE_TYPE.VISIBILITY,
    expression: 'status == "ACTIVE"',
    message: '',
    targetField: 'extraPanel',
    priority: 5,
  },
])
const evalContextText = ref('{\n  "amount": 200,\n  "status": "ACTIVE"\n}')
const evaluating = ref(false)
const evalResult = ref<LcRuleEvalResult | null>(null)

function addRuleRow() {
  ruleRows.value.push({
    _rowKey: genRowKey(),
    ruleId: `rule-${Date.now()}`,
    ruleName: '',
    ruleType: LC_RULE_TYPE.VALIDATION,
    expression: '',
    message: '',
    targetField: '',
    priority: 0,
  })
}

function removeRuleRow(row: RuleRow) {
  const idx = ruleRows.value.findIndex((r) => r._rowKey === row._rowKey)
  if (idx >= 0) {
    ruleRows.value.splice(idx, 1)
  }
}

/** 结果分组定义 (按结果类型展示) */
const resultGroups: Array<{
  key: keyof LcRuleEvalResult
  title: string
  tagType: 'primary' | 'success' | 'warning' | 'info' | 'danger'
}> = [
  { key: 'validations', title: t('lowcode.rule.result.validation'), tagType: 'danger' },
  { key: 'visibility', title: t('lowcode.rule.result.visibility'), tagType: 'primary' },
  { key: 'computations', title: t('lowcode.rule.result.computation'), tagType: 'success' },
  { key: 'defaults', title: t('lowcode.rule.result.defaultValue'), tagType: 'warning' },
  { key: 'readonly', title: t('lowcode.rule.result.readonly'), tagType: 'info' },
  { key: 'linkage', title: t('lowcode.rule.result.linkage'), tagType: 'primary' },
]

/** 将一个 map-like 结果转成键值对数组便于渲染 */
function toEntries(val: unknown): Array<{ key: string; value: string }> {
  if (val == null) return []
  if (Array.isArray(val)) {
    return val.map((v, i) => ({ key: `[${i}]`, value: formatValue(v) }))
  }
  if (typeof val === 'object') {
    return Object.entries(val as Record<string, unknown>).map(([k, v]) => ({
      key: k,
      value: formatValue(v),
    }))
  }
  return [{ key: '-', value: String(val) }]
}

function formatValue(v: unknown): string {
  if (v == null) return 'null'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function isGroupEmpty(key: keyof LcRuleEvalResult): boolean {
  const val = evalResult.value?.[key]
  if (val == null) return true
  if (Array.isArray(val)) return val.length === 0
  if (typeof val === 'object') return Object.keys(val as Record<string, unknown>).length === 0
  return false
}

async function handleEvaluate() {
  if (ruleRows.value.length === 0) {
    ElMessage.warning(t('lowcode.rule.msg.atLeastOneRule'))
    return
  }
  for (const r of ruleRows.value) {
    if (!r.expression.trim()) {
      ElMessage.warning(t('lowcode.rule.msg.expressionMissing', { name: r.ruleName || r.ruleId }))
      return
    }
  }
  const ctx = parseContext(evalContextText.value)
  if (ctx === null) return
  evaluating.value = true
  track('web.lowcode.rule.evaluate.click', { payload: { count: ruleRows.value.length } })
  try {
    const rules: LcRule[] = ruleRows.value.map((r) => ({
      ruleId: r.ruleId,
      ruleName: r.ruleName,
      ruleType: r.ruleType,
      expression: r.expression,
      message: r.message,
      targetField: r.targetField,
      priority: r.priority,
    }))
    const res = await evaluateRules(rules, ctx)
    evalResult.value = res
    ElMessage.success(t('lowcode.rule.msg.evaluateSuccess'))
    track('web.lowcode.rule.evaluate.success', { payload: { count: ruleRows.value.length } })
  } catch (e) {
    ElMessage.error(t('lowcode.rule.msg.evaluateRequestFailed'))
  } finally {
    evaluating.value = false
  }
}
</script>

<template>
  <div class="lc-rule-page">
    <div class="page-title">{{ $t('lowcode.rule.page.title') }}</div>

    <!-- 表达式校验器 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('lowcode.rule.section.exprValidator') }}</span>
          <el-tag size="small" type="info">POST /lowcode/rules/validate-expression</el-tag>
        </div>
      </template>

      <el-form label-position="top">
        <el-form-item :label="$t('lowcode.rule.field.expression')">
          <el-input
            v-model="exprText"
            type="textarea"
            :rows="3"
            :placeholder="$t('lowcode.rule.placeholder.expression')"
            :aria-label="$t('lowcode.rule.aria.expressionInput')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.rule.field.contextOptional')">
          <el-input
            v-model="exprContextText"
            type="textarea"
            :rows="5"
            placeholder='{ "amount": 200, "status": "ACTIVE" }'
            :aria-label="$t('lowcode.rule.aria.contextJson')"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :icon="Check"
            :loading="validating"
            @click="handleValidate"
            :aria-label="$t('lowcode.rule.aria.validate')"
          >
            {{ $t('lowcode.rule.action.validate') }}
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 校验结果 -->
      <el-alert
        v-if="exprResult"
        :type="exprResult.valid ? 'success' : 'error'"
        :closable="false"
        show-icon
        style="margin-top: 8px"
      >
        <template #title>
          <strong>{{ exprResult.valid ? $t('lowcode.rule.status.valid') : $t('lowcode.rule.status.invalid') }}</strong>
        </template>
        <div v-if="!exprResult.valid && exprResult.error" class="expr-error">
          {{ exprResult.error }}
        </div>
      </el-alert>

      <!-- 帮助: 内置函数 -->
      <el-divider content-position="left">{{ $t('lowcode.rule.section.builtinFunctions') }}</el-divider>
      <div class="func-grid">
        <div v-for="fn in BUILTIN_FUNCTIONS" :key="fn.name" class="func-item">
          <code class="func-name">{{ fn.name }}</code>
          <span class="func-desc">{{ fn.desc }}</span>
        </div>
      </div>

      <!-- 帮助: 语法示例 -->
      <el-divider content-position="left">{{ $t('lowcode.rule.section.syntaxExamples') }}</el-divider>
      <el-table :data="SYNTAX_EXAMPLES" border size="small" :aria-label="$t('lowcode.rule.aria.syntaxExamples')">
        <el-table-column :label="$t('lowcode.rule.field.expression')" min-width="320">
          <template #default="{ row }">
            <code class="expr-code">{{ row.expr }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="desc" :label="$t('lowcode.rule.field.description')" width="200" />
      </el-table>
    </el-card>

    <!-- 规则求值器 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('lowcode.rule.section.ruleEvaluator') }}</span>
          <el-tag size="small" type="info">POST /lowcode/rules/evaluate</el-tag>
        </div>
      </template>

      <div class="rule-toolbar">
        <el-button type="success" :icon="Plus" @click="addRuleRow" :aria-label="$t('lowcode.rule.aria.addRule')">
          {{ $t('lowcode.rule.action.addRule') }}
        </el-button>
        <el-button
          type="primary"
          :icon="VideoPlay"
          :loading="evaluating"
          @click="handleEvaluate"
          :aria-label="$t('lowcode.rule.aria.evaluate')"
        >
          {{ $t('lowcode.rule.action.evaluate') }}
        </el-button>
      </div>

      <el-table :data="ruleRows" border stripe :aria-label="$t('lowcode.rule.aria.ruleList')" style="margin-top: 12px">
        <el-table-column label="ruleId" width="180">
          <template #default="{ row }">
            <el-input v-model="row.ruleId" size="small" :aria-label="$t('lowcode.rule.aria.ruleId')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.rule.field.ruleName')" width="160">
          <template #default="{ row }">
            <el-input v-model="row.ruleName" size="small" :aria-label="$t('lowcode.rule.aria.ruleName')" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.rule.field.ruleType')" width="140">
          <template #default="{ row }">
            <el-select v-model="row.ruleType" size="small" :aria-label="$t('lowcode.rule.aria.ruleType')">
              <el-option
                v-for="o in LC_RULE_TYPE_OPTIONS"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.rule.field.expression')" min-width="280">
          <template #default="{ row }">
            <el-input v-model="row.expression" size="small" :aria-label="$t('lowcode.rule.aria.expression')" />
          </template>
        </el-table-column>
        <el-table-column label="message" width="180">
          <template #default="{ row }">
            <el-input v-model="row.message" size="small" :aria-label="$t('lowcode.rule.aria.message')" />
          </template>
        </el-table-column>
        <el-table-column label="targetField" width="140">
          <template #default="{ row }">
            <el-input v-model="row.targetField" size="small" :aria-label="$t('lowcode.rule.aria.targetField')" />
          </template>
        </el-table-column>
        <el-table-column label="priority" width="100" align="center">
          <template #default="{ row }">
            <el-input-number
              v-model="row.priority"
              size="small"
              :controls="false"
              :aria-label="$t('lowcode.rule.aria.priority')"
              style="width: 80px"
            />
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.rule.field.operation')" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              size="small"
              :icon="Delete"
              @click="removeRuleRow(row as RuleRow)"
              :aria-label="$t('lowcode.rule.aria.deleteRule')"
            >
              {{ $t('lowcode.rule.action.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-form label-position="top" style="margin-top: 16px">
        <el-form-item :label="$t('lowcode.rule.field.context')">
          <el-input
            v-model="evalContextText"
            type="textarea"
            :rows="6"
            placeholder='{ "amount": 200, "status": "ACTIVE" }'
            :aria-label="$t('lowcode.rule.aria.evaluateContext')"
          />
        </el-form-item>
      </el-form>

      <!-- 求值结果 -->
      <template v-if="evalResult">
        <el-divider content-position="left">{{ $t('lowcode.rule.section.evaluateResult') }}</el-divider>
        <div class="result-grid">
          <el-card
            v-for="g in resultGroups"
            :key="g.key"
            shadow="never"
            class="result-item"
            :class="{ 'result-empty': isGroupEmpty(g.key) }"
          >
            <template #header>
              <div class="result-header">
                <el-tag :type="g.tagType" size="small">{{ g.title }}</el-tag>
                <span class="result-count">{{ toEntries(evalResult[g.key]).length }}</span>
              </div>
            </template>
            <template v-if="!isGroupEmpty(g.key)">
              <div
                v-for="item in toEntries(evalResult[g.key])"
                :key="item.key"
                class="result-line"
              >
                <span class="result-key">{{ item.key }}</span>
                <span class="result-value">{{ item.value }}</span>
              </div>
            </template>
            <el-empty v-else :description="$t('lowcode.rule.status.none')" :image-size="40" />
          </el-card>
        </div>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.lc-rule-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.section-card {
  margin-bottom: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
}

.expr-error {
  margin-top: 4px;
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  word-break: break-all;
}

.func-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 8px 12px;
}

.func-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.func-name {
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 3px;
  color: var(--el-color-primary);
  font-family: 'Monaco', 'Consolas', monospace;
  white-space: nowrap;
}

.func-desc {
  color: var(--el-text-color-secondary);
}

.expr-code {
  font-family: 'Monaco', 'Consolas', monospace;
  color: var(--el-color-primary);
}

.rule-toolbar {
  display: flex;
  gap: 8px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.result-item {
  background: var(--el-fill-color-blank);
}

.result-empty {
  opacity: 0.6;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.result-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result-line {
  display: flex;
  gap: 8px;
  padding: 2px 0;
  font-size: 13px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.result-line:last-child {
  border-bottom: none;
}

.result-key {
  flex: 0 0 auto;
  min-width: 80px;
  color: var(--el-text-color-secondary);
  font-family: 'Monaco', 'Consolas', monospace;
}

.result-value {
  flex: 1;
  word-break: break-all;
  color: var(--el-text-color-primary);
}
</style>
