<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { renderReport, exportReport, explainReport, getImportExportTask } from '@/api/report'
import type { ReportRenderVO, DatasetResultVO, ReportComponent, ImportExportTask } from '@/api/types'

/**
 * 报表详情页。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 R1。
 * GA2-36 验证能力:
 *  - ECharts 图表: 按 layoutJson 解析 components，渲染饼图/柱状图/折线图
 *  - 大数据量导出异步化: 点击导出 → PENDING → 轮询 → SUCCESS/FAILED
 *  - AI 指标解释: 调用 explain 接口展示降级模式解释文本
 *  - 数据权限下的报表过滤: viewer 角色脱敏列展示 ***
 */
echarts.use([PieChart, BarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const { t } = useI18n()
const route = useRoute()
const reportCode = computed(() => route.params.code as string)

const loading = ref(false)
const render = ref<ReportRenderVO | null>(null)
const components = ref<ReportComponent[]>([])
const explainText = ref('')
const explainLoading = ref(false)
const explainDegraded = ref(false)
const exportLoading = ref(false)
const exportTask = ref<ImportExportTask | null>(null)
const exportDialogVisible = ref(false)
let exportPollTimer: ReturnType<typeof setInterval> | null = null

// 图表实例缓存 (key = componentId)
const chartInstances = new Map<string, echarts.ECharts>()

/** 解析 layoutJson，提取组件定义 */
function parseLayout(layoutJson: string): ReportComponent[] {
  try {
    const layout = JSON.parse(layoutJson) as { components?: ReportComponent[] }
    return layout.components || []
  } catch {
    return []
  }
}

/** 加载报表渲染数据 */
async function loadRender() {
  loading.value = true
  try {
    const data = await renderReport(reportCode.value)
    render.value = data
    components.value = parseLayout(data.layoutJson)
    // 等待 DOM 渲染完成后初始化图表
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

/** 根据组件类型渲染 ECharts 图表 */
function renderCharts() {
  if (!render.value) return
  for (const comp of components.value) {
    const data = render.value.componentData[comp.id]
    if (!data) continue
    const el = document.getElementById(`chart-${comp.id}`)
    if (!el) continue

    // 复用或创建图表实例
    let chart = chartInstances.get(comp.id)
    if (!chart) {
      chart = echarts.init(el)
      chartInstances.set(comp.id, chart)
    }

    const option = buildChartOption(comp, data)
    chart.setOption(option, true)
  }
}

/** 根据组件类型构建 ECharts option */
function buildChartOption(comp: ReportComponent, data: DatasetResultVO): echarts.EChartsCoreOption {
  const title = comp.props.title || comp.id
  const rows = data.rows

  if (comp.type === 'pie-chart') {
    const nameField = comp.props.nameField || 'name'
    const valueField = comp.props.valueField || 'value'
    const pieData = rows.map((r) => ({
      name: String(r[nameField] ?? ''),
      value: Number(r[valueField] ?? 0),
    }))
    return {
      title: { text: title, left: 'center' },
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', left: 'left' },
      series: [{ type: 'pie', radius: '60%', data: pieData }],
    }
  }

  if (comp.type === 'bar-chart') {
    const xField = comp.props.xField || 'name'
    const yField = comp.props.yField || 'value'
    const xData = rows.map((r) => String(r[xField] ?? ''))
    const yData = rows.map((r) => Number(r[yField] ?? 0))
    return {
      title: { text: title, left: 'center' },
      tooltip: { trigger: 'axis' },
      grid: { left: '10%', right: '10%', bottom: '15%' },
      xAxis: { type: 'category', data: xData, axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: yData, itemStyle: { color: '#409eff' } }],
    }
  }

  if (comp.type === 'line-chart') {
    const xField = comp.props.xField || 'name'
    const yField = comp.props.yField || 'value'
    const xData = rows.map((r) => String(r[xField] ?? ''))
    const yData = rows.map((r) => Number(r[yField] ?? 0))
    return {
      title: { text: title, left: 'center' },
      tooltip: { trigger: 'axis' },
      grid: { left: '10%', right: '10%', bottom: '15%' },
      xAxis: { type: 'category', data: xData },
      yAxis: { type: 'value' },
      series: [{ type: 'line', data: yData, smooth: true, itemStyle: { color: '#67c23a' } }],
    }
  }

  // table / metric-card 等由模板直接渲染，返回空 option
  return {}
}

/** GA2-36 异步导出报表 */
async function handleExport() {
  exportLoading.value = true
  exportDialogVisible.value = true
  try {
    const task = await exportReport(reportCode.value)
    exportTask.value = task
    // 启动轮询
    startExportPolling(task.id)
  } catch {
    exportLoading.value = false
  }
}

function startExportPolling(taskId: string) {
  if (exportPollTimer) clearInterval(exportPollTimer)
  exportPollTimer = setInterval(async () => {
    try {
      const task = await getImportExportTask(taskId)
      exportTask.value = task
      if (task.status === 'SUCCESS' || task.status === 'FAILED') {
        stopExportPolling()
        exportLoading.value = false
        if (task.status === 'SUCCESS') {
          ElMessage.success(t('report.detail.msg.exportSuccess', { rows: task.totalRows ?? 0 }))
        } else {
          const errorMsg = task.errorMessage || t('report.detail.msg.unknownError')
          ElMessage.error(t('report.detail.msg.exportFailed', { error: errorMsg }))
        }
      }
    } catch {
      stopExportPolling()
      exportLoading.value = false
    }
  }, 1500)
}

function stopExportPolling() {
  if (exportPollTimer) {
    clearInterval(exportPollTimer)
    exportPollTimer = null
  }
}

/** GA2-36 AI 指标解释 */
async function handleExplain() {
  explainLoading.value = true
  explainText.value = ''
  try {
    const res = await explainReport(reportCode.value)
    explainText.value = res.explanation
    explainDegraded.value = res.degraded
  } finally {
    explainLoading.value = false
  }
}

/** 判断组件是否为表格类型 (用 el-table 渲染) */
function isTableComponent(comp: ReportComponent): boolean {
  return comp.type === 'table'
}

/** 获取组件数据集结果 */
function getComponentData(compId: string): DatasetResultVO | undefined {
  return render.value?.componentData[compId]
}

watch(() => route.params.code, () => {
  if (reportCode.value) {
    loadRender()
  }
})

onMounted(() => {
  if (reportCode.value) {
    loadRender()
  }
})

onBeforeUnmount(() => {
  stopExportPolling()
  // 销毁所有图表实例
  chartInstances.forEach((chart) => chart.dispose())
  chartInstances.clear()
})
</script>

<template>
  <div v-loading="loading">
    <!-- 顶部信息 + 操作按钮 -->
    <el-card class="header-card" shadow="never">
      <div class="header-row">
        <div>
          <h2 class="page-title">{{ render?.reportName || $t('report.detail.defaultTitle') }}</h2>
          <div class="meta">
            <el-tag size="small">{{ $t('report.detail.codeLabel') }} {{ render?.reportCode }}</el-tag>
            <el-tag size="small" type="info">{{ $t('report.detail.typeLabel') }} {{ render?.reportType }}</el-tag>
            <el-tag v-if="render?.traceId" size="small" type="warning">traceId: {{ render.traceId }}</el-tag>
          </div>
        </div>
        <div class="actions">
          <el-button type="primary" @click="loadRender">{{ $t('report.detail.refreshData') }}</el-button>
          <el-button type="success" :loading="exportLoading" @click="handleExport">{{ $t('report.detail.asyncExport') }}</el-button>
          <el-button type="warning" :loading="explainLoading" @click="handleExplain">{{ $t('report.detail.aiExplain') }}</el-button>
        </div>
      </div>
    </el-card>

    <!-- 图表区 (GA2-36 验证 ECharts 图表能力) -->
    <el-row :gutter="16" class="chart-row">
      <el-col
        v-for="comp in components"
        :key="comp.id"
        :span="comp.layout.w"
      >
        <el-card class="chart-card" shadow="never">
          <template #header>
            <span>{{ comp.props.title || comp.id }}</span>
            <span class="comp-meta">({{ comp.type }} / dataset: {{ comp.datasetCode }})</span>
          </template>

          <!-- 表格类型组件 -->
          <el-table
            v-if="isTableComponent(comp)"
            :data="getComponentData(comp.id)?.rows || []"
            border
            size="small"
            max-height="400"
          >
            <el-table-column
              v-for="col in (getComponentData(comp.id)?.columns || [])"
              :key="col"
              :prop="col"
              :label="col"
              min-width="120"
              show-overflow-tooltip
            />
          </el-table>

          <!-- ECharts 图表容器 -->
          <div
            v-else
            :id="`chart-${comp.id}`"
            class="chart-container"
            :style="{ height: (comp.layout.h * 60) + 'px' }"
          />

          <!-- 数据集元信息 -->
          <div v-if="getComponentData(comp.id)" class="data-meta">
            <el-tag size="small" :type="getComponentData(comp.id)?.fromCache ? 'success' : 'info'">
              {{ getComponentData(comp.id)?.fromCache ? $t('report.detail.cacheHit') : $t('report.detail.realtimeQuery') }}
            </el-tag>
            <el-tag size="small" :type="getComponentData(comp.id)?.dataScopeApplied ? 'warning' : 'info'">
              {{ getComponentData(comp.id)?.dataScopeApplied ? $t('report.detail.dataScopeApplied') : $t('report.detail.fullData') }}
            </el-tag>
            <el-tag v-if="getComponentData(comp.id)?.maskedColumns.length" size="small" type="danger">
              {{ $t('report.detail.maskedColumns') }} {{ getComponentData(comp.id)?.maskedColumns.join(', ') }}
            </el-tag>
            <span class="row-count">{{ $t('report.detail.rowCount') }} {{ getComponentData(comp.id)?.rowCount }}</span>
            <span class="gen-time">{{ $t('report.detail.generatedTime') }} {{ getComponentData(comp.id)?.generatedTime }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- AI 指标解释区 (GA2-36 验证 AI 指标解释能力) -->
    <el-card v-if="explainText" class="explain-card" shadow="never">
      <template #header>
        <span>{{ $t('report.detail.aiExplainTitle') }}</span>
        <el-tag v-if="explainDegraded" size="small" type="warning" style="margin-left: 8px">{{ $t('report.detail.degradedMode') }}</el-tag>
      </template>
      <div class="explain-content" v-html="renderMarkdown(explainText)"></div>
    </el-card>

    <!-- 异步导出任务状态弹窗 (GA2-36 验证大数据量导出异步化能力) -->
    <el-dialog v-model="exportDialogVisible" :title="$t('report.detail.exportTaskTitle')" width="500px">
      <div v-if="exportTask" class="export-task-info">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="$t('report.detail.taskId')">{{ exportTask.id }}</el-descriptions-item>
          <el-descriptions-item :label="$t('report.detail.taskType')">{{ exportTask.taskType }}</el-descriptions-item>
          <el-descriptions-item :label="$t('report.detail.bizType')">{{ exportTask.bizType }}</el-descriptions-item>
          <el-descriptions-item :label="$t('report.detail.status')">
            <el-tag :type="exportTask.status === 'SUCCESS' ? 'success' : (exportTask.status === 'FAILED' ? 'danger' : 'warning')">
              {{ exportTask.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('report.detail.totalRows')">{{ exportTask.totalRows ?? '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('report.detail.successRows')">{{ exportTask.successRows ?? '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('report.detail.failRows')">{{ exportTask.failRows ?? '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="exportTask.errorMessage" :label="$t('report.detail.errorMessage')">
            {{ exportTask.errorMessage }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <div v-else class="export-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        {{ $t('report.detail.creatingTask') }}
      </div>
      <template #footer>
        <el-button @click="exportDialogVisible = false">{{ $t('report.detail.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts">
import { Loading } from '@element-plus/icons-vue'

/** 简单 Markdown 渲染（仅支持 # 标题、**加粗**、- 列表） */
function renderMarkdown(md: string): string {
  const escapeHtml = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const lines = md.split('\n')
  let html = ''
  let inList = false
  for (const line of lines) {
    const trimmed = line.trim()
    if (trimmed.startsWith('### ')) {
      if (inList) { html += '</ul>'; inList = false }
      html += `<h4>${escapeHtml(trimmed.slice(4))}</h4>`
    } else if (trimmed.startsWith('## ')) {
      if (inList) { html += '</ul>'; inList = false }
      html += `<h3>${escapeHtml(trimmed.slice(3))}</h3>`
    } else if (trimmed.startsWith('# ')) {
      if (inList) { html += '</ul>'; inList = false }
      html += `<h2>${escapeHtml(trimmed.slice(2))}</h2>`
    } else if (trimmed.startsWith('- ')) {
      if (!inList) { html += '<ul>'; inList = true }
      html += `<li>${escapeHtml(trimmed.slice(2))}</li>`
    } else if (trimmed) {
      if (inList) { html += '</ul>'; inList = false }
      // 加粗
      html += `<p>${escapeHtml(trimmed).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')}</p>`
    }
  }
  if (inList) html += '</ul>'
  return html
}

export default { components: { Loading } }
</script>

<style scoped>
.header-card {
  margin-bottom: 16px;
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.page-title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 600;
}
.meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.actions {
  display: flex;
  gap: 8px;
}
.chart-row {
  margin-bottom: 16px;
}
.chart-card {
  margin-bottom: 16px;
}
.comp-meta {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.chart-container {
  width: 100%;
  min-height: 300px;
}
.data-meta {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 12px;
  color: #606266;
}
.row-count, .gen-time {
  margin-left: 8px;
}
.explain-card {
  margin-top: 16px;
}
.explain-content {
  line-height: 1.6;
  color: #303133;
}
.explain-content :deep(h2),
.explain-content :deep(h3),
.explain-content :deep(h4) {
  margin: 12px 0 8px;
}
.explain-content :deep(ul) {
  padding-left: 20px;
}
.explain-content :deep(li) {
  margin: 4px 0;
}
.export-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px;
}
</style>
