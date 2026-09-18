<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getTraceDashboard, type TraceDashboard } from '@/api/trace'
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
 * AI 调用监控大盘。设计来源: P2-F 链路追踪监控大盘 (M-3)。
 * 数据源 GET /ai/trace/dashboard (SQL 侧聚合): 总量卡片 + 日趋势 (成功/失败堆叠 + 平均耗时线)
 * + 分类型出错率 + 近期失败表。文案沿用 AiTraceList 硬编码中文惯例。
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

const TRACE_TYPES = ['AGENT', 'FLOW', 'RAG', 'MCP', 'SKILL', 'TOOL', 'LLM', 'MEDIA', 'CUSTOM']

const loading = ref(false)
const loadError = ref('')
const data = ref<TraceDashboard | null>(null)

const filters = reactive({
  days: 7,
  traceType: '',
})

function formatRate(v?: number | null): string {
  if (v === undefined || v === null) return '-'
  return `${Number(v).toFixed(1)}%`
}

function formatLatency(v?: number | null): string {
  if (v === undefined || v === null) return '-'
  if (v >= 1000) return `${(v / 1000).toFixed(2)}s`
  return `${v}ms`
}

function formatTime(s?: string): string {
  if (!s) return '-'
  return String(s).replace('T', ' ').slice(0, 19)
}

async function fetchData() {
  loading.value = true
  loadError.value = ''
  try {
    const params: Record<string, number | string> = { days: filters.days }
    if (filters.traceType) params.traceType = filters.traceType
    data.value = await getTraceDashboard(params)
    await nextTick()
    renderTrend()
    renderType()
  } catch (e: unknown) {
    loadError.value = (e as { message?: string })?.message || '大盘加载失败'
    data.value = null
    ElMessage.error(loadError.value)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  fetchData()
}

function handleReset() {
  filters.days = 7
  filters.traceType = ''
  fetchData()
}

// ===== ECharts =====
const trendRef = ref<HTMLDivElement | null>(null)
const typeRef = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null
let typeChart: echarts.ECharts | null = null

function renderTrend() {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  const daily = data.value?.daily || []
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功', '失败', '平均耗时'] },
    grid: { left: 48, right: 56, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: daily.map((d) => String(d.day).slice(5)) },
    yAxis: [
      { type: 'value', name: '次数' },
      { type: 'value', name: '耗时(ms)' },
    ],
    series: [
      {
        name: '成功',
        type: 'bar',
        stack: 'runs',
        data: daily.map((d) => d.success),
        itemStyle: { color: '#16a34a' },
      },
      {
        name: '失败',
        type: 'bar',
        stack: 'runs',
        data: daily.map((d) => d.failed),
        itemStyle: { color: '#dc2626', borderRadius: [4, 4, 0, 0] },
      },
      {
        name: '平均耗时',
        type: 'line',
        yAxisIndex: 1,
        data: daily.map((d) => d.avgLatencyMs ?? null),
        smooth: true,
        connectNulls: true,
        lineStyle: { color: '#2563eb', width: 2 },
        itemStyle: { color: '#2563eb' },
      },
    ],
  })
}

function renderType() {
  if (!typeRef.value) return
  if (!typeChart) typeChart = echarts.init(typeRef.value)
  const byType = data.value?.byType || []
  typeChart.setOption({
    tooltip: { trigger: 'axis', valueFormatter: (v: unknown) => `${v}%` },
    grid: { left: 80, right: 48, top: 32, bottom: 30 },
    xAxis: { type: 'value', name: '出错率(%)' },
    yAxis: { type: 'category', data: byType.map((t) => t.traceType) },
    series: [
      {
        type: 'bar',
        data: byType.map((t) => Number(t.errorRate)),
        itemStyle: {
          color: (p: { value: number }) => (p.value >= 20 ? '#dc2626' : p.value >= 5 ? '#d97706' : '#16a34a'),
          borderRadius: [0, 4, 4, 0],
        },
        label: { show: true, position: 'right', formatter: '{c}%' },
      },
    ],
  })
}

function handleResize() {
  trendChart?.resize()
  typeChart?.resize()
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  typeChart?.dispose()
  trendChart = null
  typeChart = null
})
</script>

<template>
  <div class="trace-dashboard">
    <h2 class="page-title" id="page-title">调用监控</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" aria-label="大盘筛选">
        <el-button-group>
          <el-button :type="filters.days === 7 ? 'primary' : 'default'" size="small" @click="filters.days = 7; fetchData()">近7天</el-button>
          <el-button :type="filters.days === 30 ? 'primary' : 'default'" size="small" @click="filters.days = 30; fetchData()">近30天</el-button>
        </el-button-group>
        <el-select
          v-model="filters.traceType"
          placeholder="链路类型"
          clearable
          style="width: 150px"
          aria-label="链路类型"
          @change="fetchData"
        >
          <el-option v-for="tt in TRACE_TYPES" :key="tt" :label="tt" :value="tt" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>

      <el-alert v-if="loadError" :title="loadError" type="error" show-icon :closable="false" class="trace-dashboard__alert" />

      <div v-loading="loading">
        <div class="trace-dashboard__cards">
          <el-card shadow="never" class="trace-dashboard__stat">
            <div class="trace-dashboard__statLabel">总调用</div>
            <div class="trace-dashboard__statValue">{{ data?.summary.totalRuns ?? '-' }}</div>
          </el-card>
          <el-card shadow="never" class="trace-dashboard__stat">
            <div class="trace-dashboard__statLabel">成功率</div>
            <div class="trace-dashboard__statValue trace-dashboard__statValue--ok">{{ formatRate(data?.summary.successRate) }}</div>
          </el-card>
          <el-card shadow="never" class="trace-dashboard__stat">
            <div class="trace-dashboard__statLabel">平均耗时</div>
            <div class="trace-dashboard__statValue">{{ formatLatency(data?.summary.avgLatencyMs) }}</div>
          </el-card>
          <el-card shadow="never" class="trace-dashboard__stat">
            <div class="trace-dashboard__statLabel">失败数</div>
            <div class="trace-dashboard__statValue trace-dashboard__statValue--bad">{{ data?.summary.failedRuns ?? '-' }}</div>
          </el-card>
          <el-card shadow="never" class="trace-dashboard__stat">
            <div class="trace-dashboard__statLabel">最大耗时</div>
            <div class="trace-dashboard__statValue">{{ formatLatency(data?.summary.maxLatencyMs) }}</div>
          </el-card>
        </div>

        <el-card shadow="never" class="trace-dashboard__block">
          <template #header>
            <span class="trace-dashboard__blockTitle">日趋势 (成功 / 失败 / 平均耗时)</span>
          </template>
          <div ref="trendRef" class="trace-dashboard__chart" role="img" aria-label="日趋势图" />
          <el-empty v-if="!data?.daily.length && !loading" description="该区间暂无调用" :image-size="64" />
        </el-card>

        <el-card shadow="never" class="trace-dashboard__block">
          <template #header>
            <span class="trace-dashboard__blockTitle">分类型出错率</span>
          </template>
          <div ref="typeRef" class="trace-dashboard__chart trace-dashboard__chart--short" role="img" aria-label="分类型出错率图" />
          <el-empty v-if="!data?.byType.length && !loading" description="该区间暂无调用" :image-size="64" />
        </el-card>

        <el-card shadow="never" class="trace-dashboard__block">
          <template #header>
            <span class="trace-dashboard__blockTitle">近期失败 (最多 10 条)</span>
          </template>
          <el-table :data="data?.recentErrors || []" border stripe empty-text="暂无失败">
            <el-table-column prop="traceType" label="链路类型" width="120" align="center" />
            <el-table-column prop="errorMessage" label="失败信息" min-width="300" show-overflow-tooltip />
            <el-table-column label="时间" width="180" align="center">
              <template #default="{ row }">
                {{ formatTime(row.createdTime) }}
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
.trace-dashboard__alert {
  margin-bottom: 12px;
}
.trace-dashboard__cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: var(--yt-space-md, 16px);
  margin-bottom: var(--yt-space-md, 16px);
}
.trace-dashboard__stat :deep(.el-card__body) {
  padding: var(--yt-space-md, 16px);
}
.trace-dashboard__statLabel {
  font-size: var(--yt-font-size-caption, 12px);
  color: var(--yt-text-secondary, #4b5563);
}
.trace-dashboard__statValue {
  font-size: 24px;
  font-weight: var(--yt-font-weight-bold, 600);
  color: var(--yt-color-primary, #2563eb);
  margin-top: 4px;
}
.trace-dashboard__statValue--ok {
  color: var(--yt-color-success, #16a34a);
}
.trace-dashboard__statValue--bad {
  color: var(--yt-color-danger, #dc2626);
}
.trace-dashboard__block {
  margin-bottom: var(--yt-space-md, 16px);
}
.trace-dashboard__blockTitle {
  font-weight: var(--yt-font-weight-bold, 600);
  font-size: var(--yt-font-size-section, 16px);
}
.trace-dashboard__chart {
  width: 100%;
  height: 320px;
}
.trace-dashboard__chart--short {
  height: 260px;
}
</style>
