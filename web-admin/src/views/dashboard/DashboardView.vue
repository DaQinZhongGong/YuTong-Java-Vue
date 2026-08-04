<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  User,
  Goods,
  Document,
  Bell,
  Message,
  FolderOpened,
} from '@element-plus/icons-vue'
import { track } from '@/utils/tracker'
import { getWorkbenchStats, getWorkbenchTrend, getWorkbenchTopAmount } from '@/api/workbench'
import type { WorkbenchStats, WorkbenchTrend, WorkbenchTopItem } from '@/api/types'
// ECharts 按需引入，减小打包体积。设计来源: 42-报表与大屏可视化设计 R0。
import * as echarts from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  PieChart,
  BarChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  DataZoomComponent,
  CanvasRenderer,
])

const { t } = useI18n()

/**
 * 工作台。设计来源: 10-Vue3管理端设计、16-原型与交互体验设计、18-样例业务详细设计、42-报表与大屏可视化设计 R0。
 *
 * GA2-33: 接入 ECharts 图表，对齐 42 号文档 R0 验收标准"基础 ECharts 图表"。
 *  - 折线图: 近 7 日申请单提交趋势（对齐 biz_request_trend_7d 数据集）
 *  - 饼图:   申请单状态分布
 *  - 柱状图: 金额 Top10（对齐 biz_request_amount_top10 数据集）
 */

const router = useRouter()
const stats = ref<WorkbenchStats | null>(null)
const trend = ref<WorkbenchTrend | null>(null)
const topItems = ref<WorkbenchTopItem[]>([])
const loading = ref(false)

// ECharts 实例引用
const trendChartRef = ref<HTMLDivElement>()
const statusChartRef = ref<HTMLDivElement>()
const topAmountChartRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let statusChart: echarts.ECharts | null = null
let topAmountChart: echarts.ECharts | null = null

async function loadAll() {
  loading.value = true
  try {
    const [s, t, top] = await Promise.all([
      getWorkbenchStats(),
      getWorkbenchTrend(7),
      getWorkbenchTopAmount(10),
    ])
    stats.value = s
    trend.value = t
    topItems.value = top
    // 数据加载完成后渲染图表
    await nextTick()
    renderTrendChart()
    renderStatusChart()
    renderTopAmountChart()
  } catch {
    stats.value = null
    trend.value = null
    topItems.value = []
  } finally {
    loading.value = false
  }
}

/** 渲染折线图: 近 7 日提交趋势（双 Y 轴: 数量 + 金额） */
function renderTrendChart() {
  if (!trendChartRef.value || !trend.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  const points = trend.value.points
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        let html = `${params[0].axisValue}<br/>`
        params.forEach((p: any) => {
          const val = p.seriesName === t('dashboard.chart.legend.amount') ? `¥${Number(p.value).toFixed(2)}` : p.value
          html += `${p.marker} ${p.seriesName}: ${val}<br/>`
        })
        return html
      },
    },
    legend: { data: [t('dashboard.chart.legend.submitCount'), t('dashboard.chart.legend.amount')], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: points.map((p) => p.date.slice(5)), boundaryGap: false },
    yAxis: [
      { type: 'value', name: t('dashboard.chart.legend.submitCount'), position: 'left' },
      { type: 'value', name: t('dashboard.chart.legend.amount'), position: 'right', axisLabel: { formatter: '¥{value}' } },
    ],
    series: [
      {
        name: t('dashboard.chart.legend.submitCount'),
        type: 'line',
        smooth: true,
        data: points.map((p) => p.count),
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.1 },
      },
      {
        name: t('dashboard.chart.legend.amount'),
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: points.map((p) => p.amount),
        itemStyle: { color: '#67c23a' },
        areaStyle: { opacity: 0.1 },
      },
    ],
  })
}

/** 渲染饼图: 申请单状态分布 */
function renderStatusChart() {
  if (!statusChartRef.value || !stats.value) return
  if (!statusChart) {
    statusChart = echarts.init(statusChartRef.value)
  }
  const s = stats.value
  statusChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, left: 'center' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
        labelLine: { show: false },
        data: [
          { value: s.draftRequests, name: t('dashboard.chart.status.draft'), itemStyle: { color: '#909399' } },
          { value: s.submittedRequests, name: t('dashboard.chart.status.submitted'), itemStyle: { color: '#409eff' } },
          { value: s.approvedRequests, name: t('dashboard.chart.status.approved'), itemStyle: { color: '#67c23a' } },
          { value: s.rejectedRequests, name: t('dashboard.chart.status.rejected'), itemStyle: { color: '#f56c6c' } },
          { value: s.archivedRequests, name: t('dashboard.chart.status.archived'), itemStyle: { color: '#9c27b0' } },
        ],
      },
    ],
  })
}

/** 渲染柱状图: 金额 Top10 */
function renderTopAmountChart() {
  if (!topAmountChartRef.value || topItems.value.length === 0) return
  if (!topAmountChart) {
    topAmountChart = echarts.init(topAmountChartRef.value)
  }
  const items = topItems.value
  topAmountChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const idx = params[0].dataIndex
        const item = items[idx]
        return `${item.requestNo}<br/>${item.title}<br/>${t('dashboard.chart.tooltip.customer')}: ${item.customerNameSnapshot}<br/>${t('dashboard.chart.tooltip.amount')}: ¥${Number(item.totalAmount).toFixed(2)}<br/>${t('dashboard.chart.tooltip.status')}: ${item.requestStatus}`
      },
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { formatter: '¥{value}' } },
    yAxis: {
      type: 'category',
      data: items.map((i) => i.requestNo).reverse(),
      axisLabel: { width: 120, overflow: 'truncate' },
    },
    series: [
      {
        type: 'bar',
        data: items.map((i) => i.totalAmount).reverse(),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#e6a23c' },
            { offset: 1, color: '#f56c6c' },
          ]),
        },
        label: { show: true, position: 'right', formatter: (p: any) => `¥${Number(p.value).toFixed(0)}` },
      },
    ],
  })
}

/** 窗口 resize 时重绘图表 */
function handleResize() {
  trendChart?.resize()
  statusChart?.resize()
  topAmountChart?.resize()
}

// 快捷入口
const shortcuts = [
  { title: t('dashboard.shortcut.customerManage'), icon: User, path: '/biz/customers', color: '#409eff' },
  { title: t('dashboard.shortcut.productManage'), icon: Goods, path: '/biz/products', color: '#67c23a' },
  { title: t('dashboard.shortcut.request'), icon: Document, path: '/biz/requests', color: '#e6a23c' },
  { title: t('dashboard.shortcut.todo'), icon: Bell, path: '/todos', color: '#f56c6c' },
  { title: t('dashboard.shortcut.message'), icon: Message, path: '/system/messages', color: '#909399' },
  { title: t('dashboard.shortcut.file'), icon: FolderOpened, path: '/system/files', color: '#9c27b0' },
]

function goShortcut(path: string, title: string) {
  track('web.dashboard.shortcut.click', { payload: { path, shortcut: title } })
  router.push(path)
}

function statCard(title: string, value: number | undefined, icon: any, color: string) {
  return { title, value, icon, color }
}

const cardsRow1 = computed(() => {
  const s = stats.value
  return [
    statCard(t('dashboard.statCard.totalCustomers'), s?.totalCustomers, User, '#409eff'),
    statCard(t('dashboard.statCard.totalProducts'), s?.totalProducts, Goods, '#67c23a'),
    statCard(t('dashboard.statCard.totalRequests'), s?.totalRequests, Document, '#e6a23c'),
    statCard(t('dashboard.statCard.pendingTodos'), s?.pendingTodos, Bell, '#f56c6c'),
  ]
})

const cardsRow2 = computed(() => {
  const s = stats.value
  return [
    statCard(t('dashboard.statCard.draft'), s?.draftRequests, Document, '#909399'),
    statCard(t('dashboard.statCard.submitted'), s?.submittedRequests, Document, '#409eff'),
    statCard(t('dashboard.statCard.approved'), s?.approvedRequests, Document, '#67c23a'),
    statCard(t('dashboard.statCard.unreadMessages'), s?.unreadMessages, Message, '#9c27b0'),
  ]
})

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  statusChart?.dispose()
  topAmountChart?.dispose()
  trendChart = null
  statusChart = null
  topAmountChart = null
})
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <h2 class="page-title">{{ $t('dashboard.title') }}</h2>

    <!-- 统计卡片 第一行 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in cardsRow1" :key="card.title" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card-body">
            <el-icon class="stat-icon" :style="{ color: card.color }">
              <component :is="card.icon" />
            </el-icon>
            <div class="stat-info">
              <div class="stat-title">{{ card.title }}</div>
              <div class="stat-value" :style="{ color: card.color }">
                {{ card.value ?? '-' }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 统计卡片 第二行 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in cardsRow2" :key="card.title" :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card-body">
            <el-icon class="stat-icon" :style="{ color: card.color }">
              <component :is="card.icon" />
            </el-icon>
            <div class="stat-info">
              <div class="stat-title">{{ card.title }}</div>
              <div class="stat-value" :style="{ color: card.color }">
                {{ card.value ?? '-' }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- GA2-33: ECharts 图表行 — 近 7 日提交趋势（折线图）+ 状态分布（饼图） -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <span class="section-title">{{ $t('dashboard.view.trend7d') }}</span>
            <span v-if="trend" class="section-sub">
              {{ $t('dashboard.view.total') }} {{ trend.totalCount }} {{ $t('dashboard.view.transactions') }} / ¥{{ Number(trend.totalAmount).toFixed(2) }}
            </span>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <span class="section-title">{{ $t('dashboard.view.statusDistribution') }}</span>
          </template>
          <div ref="statusChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- GA2-33: ECharts 图表行 — 金额 Top10（柱状图）+ 快捷入口 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <span class="section-title">{{ $t('dashboard.view.top10Amount') }}</span>
          </template>
          <div v-if="topItems.length === 0" class="empty-tip">{{ $t('dashboard.view.noData') }}</div>
          <div v-else ref="topAmountChartRef" class="chart-container chart-tall"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span class="section-title">{{ $t('dashboard.view.shortcut') }}</span>
          </template>
          <div class="shortcut-grid">
            <div
              v-for="sc in shortcuts"
              :key="sc.path"
              class="shortcut-item"
              @click="goShortcut(sc.path, sc.title)"
            >
              <el-icon class="shortcut-icon" :style="{ color: sc.color }">
                <component :is="sc.icon" />
              </el-icon>
              <div class="shortcut-title">{{ sc.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 600;
}
.stat-row {
  margin-bottom: 16px;
}
.stat-card-body {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  font-size: 40px;
}
.stat-info {
  flex: 1;
}
.stat-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
}
.section-title {
  font-weight: 600;
}
.section-sub {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.chart-container {
  width: 100%;
  height: 280px;
}
.chart-tall {
  height: 320px;
}
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}
.shortcut-item:hover {
  background: var(--el-fill-color-light);
}
.shortcut-icon {
  font-size: 32px;
  margin-bottom: 8px;
}
.shortcut-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.empty-tip {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: 40px 0;
}
</style>
