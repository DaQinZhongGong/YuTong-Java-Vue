<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getAiUsageDaily, type AiUsageDaily, type AiUsageDailyRow } from '@/api/ai-governance'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

/**
 * AI 用量统计页。设计来源: P2-C Token 用量可视化 (M-2)。
 * 数据源 GET /ai-governance/usage/daily (调用后实时累计日行, 无需定时任务):
 * 趋势图 (Token 柱 + 成本线双轴) + 分模型汇总表 + 总量卡片。
 */

echarts.use([
  BarChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  CanvasRenderer,
])

const { t } = useI18n()

const loading = ref(false)
const loadError = ref('')
const data = ref<AiUsageDaily | null>(null)

const filters = reactive({
  days: 30,
  range: [] as string[],
  scope: 'TENANT',
  scopeKey: '',
  modelCode: '',
})

function fmtDate(d: Date): string {
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function applyQuickDays(n: number) {
  filters.days = n
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - (n - 1))
  filters.range = [fmtDate(start), fmtDate(end)]
}

function formatTokens(n: number): string {
  if (n >= 1000000) return `${(n / 1000000).toFixed(2)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(n)
}

function formatCost(n: number | string): string {
  return `¥${Number(n || 0).toFixed(4)}`
}

async function fetchData() {
  loading.value = true
  loadError.value = ''
  try {
    const params: Record<string, string> = { scope: filters.scope }
    if (filters.range.length === 2) {
      params.startDate = filters.range[0]
      params.endDate = filters.range[1]
    }
    if (filters.scopeKey.trim()) params.scopeKey = filters.scopeKey.trim()
    if (filters.modelCode.trim()) params.modelCode = filters.modelCode.trim()
    data.value = await getAiUsageDaily(params)
    await nextTick()
    renderChart()
  } catch (e: unknown) {
    loadError.value = (e as { message?: string })?.message || t('aiGovernance.usage.message.loadFailed')
    data.value = null
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  filters.days = 0
  fetchData()
}

function handleReset() {
  filters.scopeKey = ''
  filters.modelCode = ''
  applyQuickDays(30)
  fetchData()
}

// ===== 趋势序列 (按日补零, 保证连续) =====
const trend = computed(() => {
  const rows: AiUsageDailyRow[] = data.value?.rows || []
  const start = data.value?.startDate
  const end = data.value?.endDate
  if (!start || !end) return { dates: [] as string[], tokens: [] as number[], costs: [] as number[] }
  const byDate = new Map<string, { tokens: number; cost: number }>()
  for (const r of rows) {
    const k = String(r.usageDate).slice(0, 10)
    const cur = byDate.get(k) || { tokens: 0, cost: 0 }
    cur.tokens += Number(r.tokenUsed || 0)
    cur.cost += Number(r.costUsed || 0)
    byDate.set(k, cur)
  }
  const dates: string[] = []
  const tokens: number[] = []
  const costs: number[] = []
  const cur = new Date(`${start}T00:00:00`)
  const stop = new Date(`${end}T00:00:00`)
  while (cur <= stop) {
    const k = fmtDate(cur)
    dates.push(k.slice(5))
    const v = byDate.get(k)
    tokens.push(v ? v.tokens : 0)
    costs.push(v ? Number(v.cost.toFixed(4)) : 0)
    cur.setDate(cur.getDate() + 1)
  }
  return { dates, tokens, costs }
})

const modelAgg = computed(() => {
  const rows: AiUsageDailyRow[] = data.value?.rows || []
  const map = new Map<string, { tokens: number; cost: number }>()
  for (const r of rows) {
    const k = r.modelCode || '-'
    const cur = map.get(k) || { tokens: 0, cost: 0 }
    cur.tokens += Number(r.tokenUsed || 0)
    cur.cost += Number(r.costUsed || 0)
    map.set(k, cur)
  }
  const total = data.value?.totalTokens || 0
  return [...map.entries()]
    .map(([model, v]) => ({
      model,
      tokens: v.tokens,
      cost: v.cost,
      share: total > 0 ? (v.tokens / total) * 100 : 0,
    }))
    .sort((a, b) => b.tokens - a.tokens)
})

const distinctModels = computed(() => new Set((data.value?.rows || []).map((r) => r.modelCode)).size)

// ===== ECharts =====
const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function renderChart() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const { dates, tokens, costs } = trend.value
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: [t('aiGovernance.usage.chart.tokens'), t('aiGovernance.usage.chart.cost')] },
    grid: { left: 56, right: 56, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: t('aiGovernance.usage.chart.tokens') },
      { type: 'value', name: t('aiGovernance.usage.chart.costYuan') },
    ],
    series: [
      {
        name: t('aiGovernance.usage.chart.tokens'),
        type: 'bar',
        data: tokens,
        itemStyle: { color: '#2563eb', borderRadius: [4, 4, 0, 0] },
      },
      {
        name: t('aiGovernance.usage.chart.cost'),
        type: 'line',
        yAxisIndex: 1,
        data: costs,
        smooth: true,
        lineStyle: { color: '#16a34a', width: 2 },
        itemStyle: { color: '#16a34a' },
      },
    ],
  })
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  applyQuickDays(30)
  fetchData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="ai-usage">
    <h2 class="page-title" id="page-title">{{ $t('aiGovernance.usage.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('aiGovernance.usage.aria.search')">
        <el-button-group>
          <el-button :type="filters.days === 7 ? 'primary' : 'default'" size="small" @click="applyQuickDays(7); fetchData()">{{ $t('aiGovernance.usage.range.d7') }}</el-button>
          <el-button :type="filters.days === 30 ? 'primary' : 'default'" size="small" @click="applyQuickDays(30); fetchData()">{{ $t('aiGovernance.usage.range.d30') }}</el-button>
          <el-button :type="filters.days === 90 ? 'primary' : 'default'" size="small" @click="applyQuickDays(90); fetchData()">{{ $t('aiGovernance.usage.range.d90') }}</el-button>
        </el-button-group>
        <el-date-picker
          v-model="filters.range"
          type="daterange"
          value-format="YYYY-MM-DD"
          :start-placeholder="$t('aiGovernance.usage.placeholder.startDate')"
          :end-placeholder="$t('aiGovernance.usage.placeholder.endDate')"
          style="width: 260px"
          :aria-label="$t('aiGovernance.usage.placeholder.range')"
          @change="handleSearch"
        />
        <el-select
          v-model="filters.scope"
          style="width: 130px"
          :aria-label="$t('aiGovernance.usage.placeholder.scope')"
          @change="fetchData"
        >
          <el-option :label="$t('aiGovernance.usage.scope.tenant')" value="TENANT" />
          <el-option :label="$t('aiGovernance.usage.scope.user')" value="USER" />
          <el-option :label="$t('aiGovernance.usage.scope.scenario')" value="SCENARIO" />
        </el-select>
        <el-input
          v-model="filters.modelCode"
          :placeholder="$t('aiGovernance.usage.placeholder.model')"
          clearable
          style="width: 180px"
          :aria-label="$t('aiGovernance.usage.placeholder.model')"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
      </div>

      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="ai-usage__alert" />

      <div v-loading="loading">
        <div class="ai-usage__cards">
          <el-card shadow="never" class="ai-usage__stat">
            <div class="ai-usage__statLabel">{{ $t('aiGovernance.usage.stat.totalTokens') }}</div>
            <div class="ai-usage__statValue">{{ formatTokens(data?.totalTokens || 0) }}</div>
          </el-card>
          <el-card shadow="never" class="ai-usage__stat">
            <div class="ai-usage__statLabel">{{ $t('aiGovernance.usage.stat.totalCost') }}</div>
            <div class="ai-usage__statValue ai-usage__statValue--cost">{{ formatCost(data?.totalCost || 0) }}</div>
          </el-card>
          <el-card shadow="never" class="ai-usage__stat">
            <div class="ai-usage__statLabel">{{ $t('aiGovernance.usage.stat.days') }}</div>
            <div class="ai-usage__statValue">{{ trend.dates.length }}</div>
          </el-card>
          <el-card shadow="never" class="ai-usage__stat">
            <div class="ai-usage__statLabel">{{ $t('aiGovernance.usage.stat.models') }}</div>
            <div class="ai-usage__statValue">{{ distinctModels }}</div>
          </el-card>
        </div>

        <el-card shadow="never" class="ai-usage__chartCard">
          <template #header>
            <span class="ai-usage__chartTitle">{{ $t('aiGovernance.usage.chart.title') }}</span>
          </template>
          <div ref="chartRef" class="ai-usage__chart" role="img" :aria-label="$t('aiGovernance.usage.aria.chart')" />
          <el-empty v-if="!trend.dates.length && !loading" :description="$t('aiGovernance.usage.empty.noData')" :image-size="64" />
        </el-card>

        <el-card shadow="never" class="ai-usage__tableCard">
          <template #header>
            <span class="ai-usage__chartTitle">{{ $t('aiGovernance.usage.table.byModel') }}</span>
          </template>
          <el-table :data="modelAgg" border stripe :empty-text="$t('aiGovernance.usage.empty.noData')">
            <el-table-column prop="model" :label="$t('aiGovernance.usage.field.model')" min-width="180" />
            <el-table-column :label="$t('aiGovernance.usage.field.tokens')" min-width="140" align="right">
              <template #default="{ row }">
                {{ formatTokens(row.tokens) }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('aiGovernance.usage.field.cost')" min-width="140" align="right">
              <template #default="{ row }">
                {{ formatCost(row.cost) }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('aiGovernance.usage.field.share')" min-width="160">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.share.toFixed(1))" :stroke-width="8" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 600;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.ai-usage__alert {
  margin-bottom: 12px;
}
.ai-usage__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--yt-space-md, 16px);
  margin-bottom: var(--yt-space-md, 16px);
}
.ai-usage__stat :deep(.el-card__body) {
  padding: var(--yt-space-md, 16px);
}
.ai-usage__statLabel {
  font-size: var(--yt-font-size-caption, 12px);
  color: var(--yt-text-secondary, #4b5563);
}
.ai-usage__statValue {
  font-size: 24px;
  font-weight: var(--yt-font-weight-bold, 600);
  color: var(--yt-color-primary, #2563eb);
  margin-top: 4px;
}
.ai-usage__statValue--cost {
  color: var(--yt-color-success, #16a34a);
}
.ai-usage__chartCard,
.ai-usage__tableCard {
  margin-bottom: var(--yt-space-md, 16px);
}
.ai-usage__chartTitle {
  font-weight: var(--yt-font-weight-bold, 600);
  font-size: var(--yt-font-size-section, 16px);
}
.ai-usage__chart {
  width: 100%;
  height: 320px;
}
</style>
