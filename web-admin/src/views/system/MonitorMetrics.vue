<script setup lang="ts">
/**
 * GA2-L177: 性能指标监控页。
 * 展示 JVM 内存趋势、线程数、数据库连接池、API 响应时间 P95/P99。
 * 前端每 10 秒轮询并维护历史趋势数组。
 */
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { getMetrics, type MetricsSnapshot } from '@/api/monitor'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  CanvasRenderer,
])

const { t } = useI18n()

const loading = ref(false)
const lastError = ref<string | null>(null)
const snapshot = ref<MetricsSnapshot | null>(null)

// 历史数据（最多保留 30 个点 = 5 分钟）
const MAX_POINTS = 30
const history = ref({
  timestamps: [] as string[],
  jvmHeapUsed: [] as number[],
  jvmHeapCommitted: [] as number[],
  httpP95: [] as number[],
  httpP99: [] as number[],
})

let pollTimer: ReturnType<typeof setInterval> | null = null

const memoryChartRef = ref<HTMLDivElement>()
const latencyChartRef = ref<HTMLDivElement>()
let memoryChart: echarts.ECharts | null = null
let latencyChart: echarts.ECharts | null = null

function formatTime(date: Date): string {
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  const s = String(date.getSeconds()).padStart(2, '0')
  return `${h}:${m}:${s}`
}

function bytesToMB(bytes: number): number {
  return Math.round((bytes / 1024 / 1024) * 100) / 100
}

async function fetchMetrics() {
  loading.value = true
  lastError.value = null
  try {
    const data = await getMetrics()
    snapshot.value = data

    const now = new Date()
    const ts = formatTime(now)
    history.value.timestamps.push(ts)
    history.value.jvmHeapUsed.push(bytesToMB(data.jvmHeapUsed))
    history.value.jvmHeapCommitted.push(bytesToMB(data.jvmHeapCommitted))
    history.value.httpP95.push(data.httpP95)
    history.value.httpP99.push(data.httpP99)

    if (history.value.timestamps.length > MAX_POINTS) {
      history.value.timestamps.shift()
      history.value.jvmHeapUsed.shift()
      history.value.jvmHeapCommitted.shift()
      history.value.httpP95.shift()
      history.value.httpP99.shift()
    }

    await nextTick()
    renderMemoryChart()
    renderLatencyChart()
  } catch (e: any) {
    lastError.value = e?.message || t('system.monitorMetrics.message.loadFailed')
  } finally {
    loading.value = false
  }
}

function renderMemoryChart() {
  if (!memoryChartRef.value) return
  if (!memoryChart) {
    memoryChart = echarts.init(memoryChartRef.value)
  }
  memoryChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: [t('system.monitorMetrics.legend.used'), t('system.monitorMetrics.legend.committed')], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: history.value.timestamps,
    },
    yAxis: { type: 'value', name: 'MB' },
    series: [
      {
        name: t('system.monitorMetrics.legend.used'),
        type: 'line',
        smooth: true,
        data: history.value.jvmHeapUsed,
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.15 },
        showSymbol: false,
      },
      {
        name: t('system.monitorMetrics.legend.committed'),
        type: 'line',
        smooth: true,
        data: history.value.jvmHeapCommitted,
        itemStyle: { color: '#67c23a' },
        areaStyle: { opacity: 0.1 },
        showSymbol: false,
      },
    ],
  })
}

function renderLatencyChart() {
  if (!latencyChartRef.value) return
  if (!latencyChart) {
    latencyChart = echarts.init(latencyChartRef.value)
  }
  latencyChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        let html = `${params[0].axisValue}<br/>`
        params.forEach((p: any) => {
          html += `${p.marker} ${p.seriesName}: ${p.value} ms<br/>`
        })
        return html
      },
    },
    legend: { data: ['P95', 'P99'], top: 0 },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: history.value.timestamps,
    },
    yAxis: { type: 'value', name: 'ms' },
    series: [
      {
        name: 'P95',
        type: 'line',
        smooth: true,
        data: history.value.httpP95,
        itemStyle: { color: '#e6a23c' },
        areaStyle: { opacity: 0.1 },
        showSymbol: false,
      },
      {
        name: 'P99',
        type: 'line',
        smooth: true,
        data: history.value.httpP99,
        itemStyle: { color: '#f56c6c' },
        areaStyle: { opacity: 0.1 },
        showSymbol: false,
      },
    ],
  })
}

function handleResize() {
  memoryChart?.resize()
  latencyChart?.resize()
}

onMounted(() => {
  fetchMetrics()
  pollTimer = setInterval(fetchMetrics, 10_000)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  window.removeEventListener('resize', handleResize)
  memoryChart?.dispose()
  latencyChart?.dispose()
  memoryChart = null
  latencyChart = null
})

const jvmHeapPercent = computed(() => {
  const s = snapshot.value
  if (!s || s.jvmHeapMax <= 0) return 0
  return Math.min(100, Math.round((s.jvmHeapUsed / s.jvmHeapMax) * 100))
})
</script>

<template>
  <div class="monitor-metrics-page">
    <!-- 顶部操作栏 -->
    <el-card class="banner-card" shadow="never">
      <div class="banner">
        <div class="banner-left">
          <span class="banner-label">{{ $t('system.monitorMetrics.page.title') }}</span>
          <el-tag v-if="snapshot" type="info" size="small" effect="plain">
            {{ $t('system.monitorMetrics.field.lastUpdate') }}: {{ history.timestamps[history.timestamps.length - 1] || '-' }}
          </el-tag>
        </div>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="fetchMetrics">
          {{ $t('common.action.refresh') }}
        </el-button>
      </div>
    </el-card>

    <el-alert v-if="lastError" :title="lastError" type="error" :closable="false" show-icon class="error-alert" />

    <!-- 指标卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="metric-card">
            <div class="metric-title">{{ $t('system.monitorMetrics.field.jvmHeap') }}</div>
            <div class="metric-value" :style="{ color: jvmHeapPercent > 80 ? '#f56c6c' : '#409eff' }">
              {{ snapshot ? bytesToMB(snapshot.jvmHeapUsed) : '-' }} MB
            </div>
            <el-progress
              :percentage="jvmHeapPercent"
              :status="jvmHeapPercent > 80 ? 'exception' : undefined"
              :stroke-width="8"
              class="metric-progress"
            />
            <div class="metric-sub">
              {{ $t('system.monitorMetrics.field.committed') }} {{ snapshot ? bytesToMB(snapshot.jvmHeapCommitted) : '-' }} MB / {{ $t('system.monitorMetrics.field.max') }} {{ snapshot ? bytesToMB(snapshot.jvmHeapMax) : '-' }} MB
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="metric-card">
            <div class="metric-title">{{ $t('system.monitorMetrics.field.jvmThreads') }}</div>
            <div class="metric-value" style="color: #67c23a">
              {{ snapshot?.jvmThreadLive ?? '-' }}
            </div>
            <div class="metric-sub">
              {{ $t('system.monitorMetrics.field.peak') }} {{ snapshot?.jvmThreadPeak ?? '-' }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="metric-card">
            <div class="metric-title">{{ $t('system.monitorMetrics.field.poolActive') }}</div>
            <div class="metric-value" style="color: #e6a23c">
              {{ snapshot?.dbPoolActive ?? '-' }}
            </div>
            <div class="metric-sub">
              {{ $t('system.monitorMetrics.field.max') }} {{ snapshot?.dbPoolMax ?? '-' }}
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="metric-card">
            <div class="metric-title">{{ $t('system.monitorMetrics.field.poolIdle') }}</div>
            <div class="metric-value" style="color: #909399">
              {{ snapshot?.dbPoolIdle ?? '-' }}
            </div>
            <div class="metric-sub">
              {{ $t('system.monitorMetrics.field.active') }} {{ snapshot?.dbPoolActive ?? '-' }}
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表行 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <span class="section-title">{{ $t('system.monitorMetrics.title.heapTrend') }}</span>
          </template>
          <div ref="memoryChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <span class="section-title">{{ $t('system.monitorMetrics.title.latencyTrend') }}</span>
          </template>
          <div ref="latencyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.monitor-metrics-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.banner-card {
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
}
.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
.banner-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.banner-label {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.error-alert {
  margin-bottom: 0;
}
.stat-row {
  margin: 0;
}
.metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.metric-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
}
.metric-sub {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.metric-progress {
  margin-top: 4px;
}
.chart-row {
  margin: 0;
}
.chart-container {
  width: 100%;
  height: 320px;
}
.section-title {
  font-weight: 600;
}
</style>
