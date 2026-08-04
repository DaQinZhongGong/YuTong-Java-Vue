<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { renderFullscreenDashboard } from '@/api/report-dashboard'
import { previewDataset } from '@/api/report'
import type { RptDashboard } from '@/api/report-dashboard'
import type { DatasetResultVO } from '@/api/types'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  BarChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  CanvasRenderer,
])

/**
 * 大屏全屏展示页面。设计来源: 42-报表与大屏可视化设计 R3。
 * 功能: 全屏 + 自动轮播(10s) + 实时刷新(30s) + ESC退出 + ECharts渲染。
 */

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const dashboardCode = route.params.id as string

const loading = ref(false)
const dashboard = ref<RptDashboard | null>(null)
const layoutData = ref<any>({
  canvas: { width: 1920, height: 1080, theme: 'dark', backgroundImage: '' },
  components: [],
})

// 自动轮播
const currentComponentIndex = ref(0)
const isAutoPlay = ref(true)
let autoPlayTimer: ReturnType<typeof setInterval> | null = null

// 刷新间隔
let refreshTimer: ReturnType<typeof setInterval> | null = null

// ECharts 实例映射: compId -> ECharts
const chartInstances = ref<Record<string, echarts.ECharts>>({})
const chartRefs = ref<Record<string, HTMLDivElement>>({})

// 组件数据集缓存
const compDataMap = ref<Record<string, DatasetResultVO>>({})

// 加载大屏数据
async function loadDashboard() {
  loading.value = true
  try {
    const data = await renderFullscreenDashboard(dashboardCode)
    dashboard.value = data
    if (data.layoutJson) {
      try {
        layoutData.value = JSON.parse(data.layoutJson)
      } catch {
        layoutData.value = {
          canvas: { width: data.canvasWidth, height: data.canvasHeight, theme: data.theme, backgroundImage: data.backgroundImage || '' },
          components: [],
        }
      }
    }
    // 查询各组件数据集
    await loadComponentData()
    // 渲染图表
    await nextTickRenderCharts()
  } catch {
    ElMessage.error(t('dashboard.msg.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 加载各组件数据集
async function loadComponentData() {
  const comps = layoutData.value.components || []
  for (const comp of comps) {
    if (comp.datasetCode) {
      try {
        const res = await previewDataset(comp.datasetCode)
        compDataMap.value[comp.id] = res
      } catch {
        // 静默失败，保留旧数据
      }
    }
  }
}

// 渲染图表
async function nextTickRenderCharts() {
  await new Promise((resolve) => setTimeout(resolve, 100))
  renderCharts()
}

function renderCharts() {
  const comps = layoutData.value.components || []
  for (const comp of comps) {
    const el = chartRefs.value[comp.id]
    if (!el) continue
    if (!chartInstances.value[comp.id]) {
      chartInstances.value[comp.id] = echarts.init(el)
    }
    const chart = chartInstances.value[comp.id]
    const data = compDataMap.value[comp.id]
    const option = buildChartOption(comp, data)
    chart.setOption(option, true)
  }
}

function buildChartOption(comp: any, data?: DatasetResultVO): echarts.EChartsCoreOption {
  const isDark = layoutData.value.canvas.theme === 'dark'
  const textColor = isDark ? '#e0e0e0' : '#333'
  const title = comp.props?.title || comp.type

  if (comp.type === 'kpi-card' || comp.type === 'text') {
    return {
      title: { text: title, left: 'center', textStyle: { color: textColor, fontSize: 14 } },
      series: [],
    }
  }

  if (!data || !data.rows || data.rows.length === 0) {
    return {
      title: { text: title + t('dashboard.fullscreen.chartNoDataSuffix'), left: 'center', textStyle: { color: textColor, fontSize: 14 } },
    }
  }

  const rows = data.rows
  const columns = data.columns

  // 通用图表配置
  const baseOption: echarts.EChartsCoreOption = {
    backgroundColor: 'transparent',
    title: { text: title, left: 'center', textStyle: { color: textColor, fontSize: 16 } },
    tooltip: { trigger: comp.type === 'pie-chart' ? 'item' : 'axis' },
    textStyle: { color: textColor },
  }

  if (comp.type === 'line-chart') {
    const xField = comp.props?.xField || columns[0]
    const yField = comp.props?.yField || columns[1]
    return {
      ...baseOption,
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: rows.map((r: any) => r[xField]), axisLabel: { color: textColor } },
      yAxis: { type: 'value', axisLabel: { color: textColor } },
      series: [{
        type: 'line',
        smooth: true,
        data: rows.map((r: any) => Number(r[yField]) || 0),
        itemStyle: { color: '#409eff' },
        areaStyle: { opacity: 0.2, color: '#409eff' },
      }],
    }
  }

  if (comp.type === 'bar-chart') {
    const xField = comp.props?.xField || columns[0]
    const yField = comp.props?.yField || columns[1]
    return {
      ...baseOption,
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: rows.map((r: any) => r[xField]), axisLabel: { color: textColor, rotate: 30 } },
      yAxis: { type: 'value', axisLabel: { color: textColor } },
      series: [{
        type: 'bar',
        data: rows.map((r: any) => Number(r[yField]) || 0),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: '#67c23a' },
          ]),
        },
      }],
    }
  }

  if (comp.type === 'pie-chart') {
    const nameField = comp.props?.nameField || columns[0]
    const valueField = comp.props?.valueField || columns[1]
    return {
      ...baseOption,
      legend: { bottom: 0, left: 'center', textStyle: { color: textColor } },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false, position: 'center' },
        emphasis: { label: { show: true, fontSize: 18, fontWeight: 'bold', color: textColor } },
        labelLine: { show: false },
        data: rows.map((r: any) => ({ value: Number(r[valueField]) || 0, name: String(r[nameField]) })),
      }],
    }
  }

  return baseOption
}

// 自动轮播
function startAutoPlay() {
  stopAutoPlay()
  if (!isAutoPlay.value) return
  const components = layoutData.value.components || []
  if (components.length === 0) return

  autoPlayTimer = setInterval(() => {
    currentComponentIndex.value = (currentComponentIndex.value + 1) % components.length
  }, 10000)
}

function stopAutoPlay() {
  if (autoPlayTimer) {
    clearInterval(autoPlayTimer)
    autoPlayTimer = null
  }
}

// 刷新间隔
function startRefresh() {
  stopRefresh()
  const interval = dashboard.value?.refreshInterval || 30
  refreshTimer = setInterval(() => {
    loadDashboard()
  }, interval * 1000)
}

function stopRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

// 退出全屏
function handleExit() {
  router.push('/dashboards')
}

// 切换自动轮播
function toggleAutoPlay() {
  isAutoPlay.value = !isAutoPlay.value
  if (isAutoPlay.value) {
    startAutoPlay()
  } else {
    stopAutoPlay()
  }
}

// 计算属性
const canvasStyle = computed(() => {
  const theme = layoutData.value.canvas.theme || 'dark'
  return {
    width: '100vw',
    height: '100vh',
    background: theme === 'dark' ? '#1a1a2e' : '#f5f7fa',
    backgroundImage: layoutData.value.canvas.backgroundImage
      ? `url(${layoutData.value.canvas.backgroundImage})`
      : 'none',
    backgroundSize: 'cover',
    backgroundPosition: 'center',
  }
})

const isDarkTheme = computed(() => {
  return layoutData.value.canvas.theme === 'dark'
})

// KPI 卡片取值
function getKpiValue(comp: any): string | number {
  const data = compDataMap.value[comp.id]
  if (!data || !data.rows || data.rows.length === 0) return '-'
  const valueField = comp.props?.valueField
  if (valueField) {
    return (data.rows[0][valueField] as string | number) ?? '-'
  }
  const firstKey = Object.keys(data.rows[0])[0]
  return (data.rows[0][firstKey] as string | number) ?? '-'
}

// 键盘事件
function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    handleExit()
  } else if (e.key === 'F11') {
    e.preventDefault()
    if (document.fullscreenElement) {
      document.exitFullscreen?.()
    } else {
      document.documentElement.requestFullscreen?.()
    }
  } else if (e.key === ' ') {
    e.preventDefault()
    toggleAutoPlay()
  }
}

// 窗口 resize
function onWindowResize() {
  Object.values(chartInstances.value).forEach((chart) => chart.resize())
}

async function enterFullscreen() {
  const el = document.documentElement
  if (el.requestFullscreen) {
    await el.requestFullscreen()
  } else if ((el as any).webkitRequestFullscreen) {
    (el as any).webkitRequestFullscreen()
  }
}

function exitFullscreen() {
  if (document.fullscreenElement && document.exitFullscreen) {
    document.exitFullscreen()
  }
}

onMounted(() => {
  loadDashboard()
  startAutoPlay()
  startRefresh()
  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('resize', onWindowResize)
  enterFullscreen().catch(() => { /* 用户可能禁止自动全屏，忽略 */ })
})

onUnmounted(() => {
  stopAutoPlay()
  stopRefresh()
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('resize', onWindowResize)
  Object.values(chartInstances.value).forEach((chart) => chart.dispose())
  chartInstances.value = {}
  exitFullscreen()
})
</script>

<template>
  <div class="dashboard-fullscreen" v-loading="loading" :style="canvasStyle">
    <!-- 顶部控制栏 -->
    <div class="fullscreen-toolbar" :class="{ dark: isDarkTheme }">
      <div class="toolbar-left">
        <span class="dashboard-title">{{ dashboard?.dashboardName || $t('dashboard.fullscreen.title') }}</span>
        <el-tag :type="isDarkTheme ? 'info' : 'success'" size="small">
          {{ layoutData.canvas?.theme === 'dark' ? $t('dashboard.fullscreen.dark') : $t('dashboard.fullscreen.light') }}
        </el-tag>
      </div>
      <div class="toolbar-center">
        <el-button
          :type="isAutoPlay ? 'primary' : 'default'"
          size="small"
          @click="toggleAutoPlay"
        >
          {{ isAutoPlay ? $t('dashboard.fullscreen.pauseCarousel') : $t('dashboard.fullscreen.autoCarousel') }}
        </el-button>
        <span class="refresh-hint">
          {{ $t('dashboard.fullscreen.autoRefresh') }}: {{ dashboard?.refreshInterval || 30 }}s
        </span>
      </div>
      <div class="toolbar-right">
        <el-button @click="handleExit" size="small">{{ $t('dashboard.fullscreen.exit') }}</el-button>
      </div>
    </div>

    <!-- 画布内容 -->
    <div class="fullscreen-canvas">
      <div
        v-for="(comp, idx) in layoutData.components"
        :key="comp.id"
        class="fullscreen-component"
        :class="{ active: idx === currentComponentIndex }"
        :style="{
          left: (comp.layout.x / 24) * 100 + '%',
          top: (comp.layout.y / 12) * 100 + '%',
          width: (comp.layout.w / 24) * 100 + '%',
          height: (comp.layout.h / 12) * 100 + '%',
        }"
      >
        <!-- KPI 卡片特殊渲染 -->
        <div v-if="comp.type === 'kpi-card'" class="component-content kpi-card">
          <div class="kpi-title">{{ comp.props?.title || comp.type }}</div>
          <div class="kpi-value">{{ getKpiValue(comp) }}</div>
        </div>
        <!-- 文本特殊渲染 -->
        <div v-else-if="comp.type === 'text'" class="component-content text-card">
          <div class="text-title">{{ comp.props?.title || comp.type }}</div>
        </div>
        <!-- 数据表格 -->
        <div v-else-if="comp.type === 'data-table'" class="component-content table-card">
          <div class="table-title">{{ comp.props?.title || comp.type }}</div>
          <div class="table-body">
            <table v-if="compDataMap[comp.id]?.rows?.length">
              <thead>
                <tr>
                  <th v-for="col in compDataMap[comp.id].columns" :key="col">{{ col }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rIdx) in compDataMap[comp.id].rows.slice(0, 5)" :key="rIdx">
                  <td v-for="col in compDataMap[comp.id].columns" :key="col">{{ row[col] }}</td>
                </tr>
              </tbody>
            </table>
            <div v-else class="no-data">{{ $t('dashboard.fullscreen.noData') }}</div>
          </div>
        </div>
        <!-- 图表组件 -->
        <div v-else class="component-content chart-card">
          <div :ref="(el: any) => { if (el) chartRefs[comp.id] = el }" class="chart-container"></div>
        </div>
      </div>

      <div v-if="layoutData.components.length === 0" class="canvas-empty">
        <p>{{ $t('dashboard.fullscreen.emptyCanvas') }}</p>
      </div>
    </div>

    <!-- 底部指示器 -->
    <div class="fullscreen-indicators" v-if="layoutData.components.length > 1">
      <span
        v-for="(comp, idx) in layoutData.components"
        :key="'ind-' + comp.id"
        class="indicator-dot"
        :class="{ active: idx === currentComponentIndex }"
        @click="currentComponentIndex = idx"
      ></span>
    </div>
  </div>
</template>

<style scoped>
.dashboard-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  z-index: 9999;
}

.fullscreen-toolbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(10px);
  z-index: 10;
  color: #fff;
}

.fullscreen-toolbar.dark {
  background: rgba(0, 0, 0, 0.7);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dashboard-title {
  font-size: 20px;
  font-weight: 600;
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 16px;
}

.refresh-hint {
  font-size: 12px;
  color: #909399;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.fullscreen-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  padding-top: 60px;
}

.fullscreen-component {
  position: absolute;
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(5px);
  transition: all 0.5s ease;
  overflow: hidden;
}

.fullscreen-component.active {
  border-color: #409eff;
  box-shadow: 0 0 30px rgba(64, 158, 255, 0.5);
  transform: scale(1.02);
}

.component-content {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.kpi-card {
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.kpi-title {
  font-size: 16px;
  color: #909399;
}

.kpi-value {
  font-size: 48px;
  font-weight: 700;
  color: #409eff;
}

.text-card {
  align-items: center;
  justify-content: center;
}

.text-title {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
}

.table-card {
  padding: 8px;
}

.table-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 8px;
}

.table-body {
  flex: 1;
  overflow: auto;
}

.table-body table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  color: #e0e0e0;
}

.table-body th,
.table-body td {
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 4px 8px;
  text-align: left;
}

.table-body th {
  background: rgba(64, 158, 255, 0.2);
}

.no-data {
  color: #909399;
  text-align: center;
  padding: 20px;
}

.chart-card {
  padding: 8px;
}

.chart-container {
  width: 100%;
  height: 100%;
}

.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #909399;
  font-size: 18px;
}

.fullscreen-indicators {
  position: absolute;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  z-index: 10;
}

.indicator-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  cursor: pointer;
  transition: all 0.3s;
}

.indicator-dot.active {
  background: #409eff;
  transform: scale(1.3);
}
</style>
