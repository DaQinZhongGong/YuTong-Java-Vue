<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDashboard,
  updateDashboard,
  publishDashboard,
  pageWidgets,
} from '@/api/report-dashboard'
import type { RptDashboard, RptWidget } from '@/api/report-dashboard'
import { pageDatasets } from '@/api/report'
import type { RptDataset } from '@/api/types'
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
 * 大屏设计器页面。设计来源: 42-报表与大屏可视化设计 R3。
 * 布局: 1920x1080 画布 + 拖拽组件 + 属性配置 + 全屏预览。
 * 增强: 支持从组件库拖拽到画布、画布内拖拽调整位置/大小、数据集下拉选择。
 */

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const dashboardCode = route.params.id as string

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const dashboard = ref<RptDashboard | null>(null)
const widgets = ref<RptWidget[]>([])
const datasets = ref<RptDataset[]>([])
const selectedComponent = ref<any>(null)

// 画布数据
const layoutData = ref<any>({
  canvas: { width: 1920, height: 1080, theme: 'dark', backgroundImage: '' },
  components: [],
})

// 缩放比例
const canvasScale = ref(0.5)

// 组件库
const COMPONENT_PALETTE = [
  { type: 'line-chart', label: t('dashboard.designer.chartLine'), icon: 'TrendCharts' },
  { type: 'bar-chart', label: t('dashboard.designer.chartBar'), icon: 'Histogram' },
  { type: 'pie-chart', label: t('dashboard.designer.chartPie'), icon: 'PieChart' },
  { type: 'kpi-card', label: t('dashboard.designer.kpiCard'), icon: 'DataLine' },
  { type: 'data-table', label: t('dashboard.designer.dataTable'), icon: 'Grid' },
  { type: 'text', label: t('dashboard.designer.text'), icon: 'Document' },
]

// 拖拽状态
const dragState = ref<{
  dragging: boolean
  resizing: boolean
  compId: string | null
  startX: number
  startY: number
  origX: number
  origY: number
  origW: number
  origH: number
}>({
  dragging: false,
  resizing: false,
  compId: null,
  startX: 0,
  startY: 0,
  origX: 0,
  origY: 0,
  origW: 0,
  origH: 0,
})

// 加载大屏数据
async function loadDashboard() {
  loading.value = true
  try {
    const data = await getDashboard(dashboardCode)
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
  } catch {
    ElMessage.error(t('dashboard.msg.loadFailed'))
  } finally {
    loading.value = false
  }
}

// 加载 Widget 列表
async function loadWidgets() {
  try {
    const res = await pageWidgets({ page: 1, size: 100 })
    widgets.value = res.records || []
  } catch {
    // 静默失败
  }
}

// 加载数据集列表
async function loadDatasets() {
  try {
    const res = await pageDatasets({ page: 1, size: 100 })
    datasets.value = res.records || []
  } catch {
    // 静默失败
  }
}

// 添加组件到画布
function addComponent(paletteItem: { type: string; label: string }) {
  const newId = 'd' + (Date.now())
  const component = {
    id: newId,
    type: paletteItem.type,
    widgetCode: '',
    datasetCode: '',
    props: { title: paletteItem.label },
    layout: { x: 0, y: 0, w: 6, h: 4 },
  }
  layoutData.value.components.push(component)
  selectedComponent.value = component
}

// 选择组件
function selectComponent(comp: any) {
  selectedComponent.value = comp
}

// 删除组件
function removeComponent(comp: any) {
  const idx = layoutData.value.components.findIndex((c: any) => c.id === comp.id)
  if (idx >= 0) {
    layoutData.value.components.splice(idx, 1)
    if (selectedComponent.value?.id === comp.id) {
      selectedComponent.value = null
    }
  }
}

// 保存大屏
async function handleSave() {
  if (!dashboard.value) return
  if (saving.value) return
  saving.value = true
  try {
    await updateDashboard(dashboardCode, {
      dashboardCode: dashboard.value.dashboardCode,
      dashboardName: dashboard.value.dashboardName,
      canvasWidth: layoutData.value.canvas.width,
      canvasHeight: layoutData.value.canvas.height,
      theme: layoutData.value.canvas.theme,
      backgroundImage: layoutData.value.canvas.backgroundImage,
      layoutJson: JSON.stringify(layoutData.value),
      componentBindings: JSON.stringify(
        layoutData.value.components.map((c: any) => ({
          componentId: c.id,
          widgetCode: c.widgetCode,
        }))
      ),
      refreshInterval: dashboard.value.refreshInterval,
      permissionCode: dashboard.value.permissionCode,
      description: dashboard.value.description,
    })
    ElMessage.success(t('dashboard.msg.saveSuccess'))
  } catch {
    ElMessage.error(t('dashboard.msg.saveFailed'))
  } finally {
    saving.value = false
  }
}

// 发布大屏
async function handlePublish() {
  if (publishing.value) return
  try {
    await ElMessageBox.confirm(t('dashboard.msg.confirmPublishDesigner'), t('dashboard.msg.confirmPublish'), {
      type: 'warning',
    })
    publishing.value = true
    await publishDashboard(dashboardCode)
    ElMessage.success(t('dashboard.msg.publishSuccess'))
    loadDashboard()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(t('dashboard.msg.publishFailed'))
    }
  } finally {
    publishing.value = false
  }
}

// 全屏预览
function handleFullscreen() {
  router.push(`/dashboard-fullscreen/${dashboardCode}`)
}

// 返回
function handleBack() {
  router.push('/dashboards')
}

// 计算属性
const statusLabel = computed(() => {
  return dashboard.value?.status === 'PUBLISHED' ? t('common.publishStatus.published') : t('common.publishStatus.draft')
})

const statusType = computed(() => {
  return dashboard.value?.status === 'PUBLISHED' ? 'success' : 'info'
})

const canvasStyle = computed(() => {
  const w = layoutData.value.canvas.width || 1920
  const h = layoutData.value.canvas.height || 1080
  return {
    width: w * canvasScale.value + 'px',
    height: h * canvasScale.value + 'px',
    background: layoutData.value.canvas.theme === 'dark' ? '#1a1a2e' : '#f5f7fa',
  }
})

// 栅格计算
const COLS = 24
const ROWS = 12

function getCellW() {
  const w = layoutData.value.canvas.width || 1920
  return (w * canvasScale.value) / COLS
}

function getCellH() {
  const h = layoutData.value.canvas.height || 1080
  return (h * canvasScale.value) / ROWS
}

function getCompStyle(comp: any) {
  return {
    left: comp.layout.x * getCellW() + 'px',
    top: comp.layout.y * getCellH() + 'px',
    width: comp.layout.w * getCellW() + 'px',
    height: comp.layout.h * getCellH() + 'px',
  }
}

// 画布内拖拽移动
function onCompMouseDown(e: MouseEvent, comp: any) {
  e.stopPropagation()
  selectComponent(comp)
  // const cellW = getCellW()
  // const cellH = getCellH()
  dragState.value = {
    dragging: true,
    resizing: false,
    compId: comp.id,
    startX: e.clientX,
    startY: e.clientY,
    origX: comp.layout.x,
    origY: comp.layout.y,
    origW: comp.layout.w,
    origH: comp.layout.h,
  }
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

// 调整大小
function onResizeMouseDown(e: MouseEvent, comp: any) {
  e.stopPropagation()
  e.preventDefault()
  selectComponent(comp)
  dragState.value = {
    dragging: false,
    resizing: true,
    compId: comp.id,
    startX: e.clientX,
    startY: e.clientY,
    origX: comp.layout.x,
    origY: comp.layout.y,
    origW: comp.layout.w,
    origH: comp.layout.h,
  }
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  if (!dragState.value.compId) return
  const comp = layoutData.value.components.find((c: any) => c.id === dragState.value.compId)
  if (!comp) return

  const cellW = getCellW()
  const cellH = getCellH()
  const dx = Math.round((e.clientX - dragState.value.startX) / cellW)
  const dy = Math.round((e.clientY - dragState.value.startY) / cellH)

  if (dragState.value.dragging) {
    const newX = Math.max(0, Math.min(COLS - comp.layout.w, dragState.value.origX + dx))
    const newY = Math.max(0, Math.min(ROWS - comp.layout.h, dragState.value.origY + dy))
    comp.layout.x = newX
    comp.layout.y = newY
  } else if (dragState.value.resizing) {
    const newW = Math.max(1, Math.min(COLS - comp.layout.x, dragState.value.origW + dx))
    const newH = Math.max(1, Math.min(ROWS - comp.layout.y, dragState.value.origH + dy))
    comp.layout.w = newW
    comp.layout.h = newH
  }
}

function onMouseUp() {
  dragState.value.dragging = false
  dragState.value.resizing = false
  dragState.value.compId = null
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

// 从组件库拖拽到画布
function onPaletteDragStart(e: DragEvent, item: { type: string; label: string; widgetCode?: string; datasetCode?: string }) {
  if (e.dataTransfer) {
    e.dataTransfer.setData('application/json', JSON.stringify(item))
    e.dataTransfer.effectAllowed = 'copy'
  }
}

function onCanvasDragOver(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) {
    e.dataTransfer.dropEffect = 'copy'
  }
}

function onCanvasDrop(e: DragEvent) {
  e.preventDefault()
  const data = e.dataTransfer?.getData('application/json')
  if (!data) return
  try {
    const item = JSON.parse(data)
    const canvasRect = (e.currentTarget as HTMLElement).getBoundingClientRect()
    const cellW = getCellW()
    const cellH = getCellH()
    const x = Math.max(0, Math.min(COLS - 6, Math.floor((e.clientX - canvasRect.left) / cellW)))
    const y = Math.max(0, Math.min(ROWS - 4, Math.floor((e.clientY - canvasRect.top) / cellH)))
    const newId = 'd' + Date.now()
    const component = {
      id: newId,
      type: item.type,
      widgetCode: '',
      datasetCode: '',
      props: { title: item.label },
      layout: { x, y, w: 6, h: 4 },
    }
    layoutData.value.components.push(component)
    selectedComponent.value = component
  } catch {
    // ignore
  }
}

// 数据集选项
const datasetOptions = computed(() => {
  return datasets.value.map((d) => ({
    label: d.datasetName + ' (' + d.datasetCode + ')',
    value: d.datasetCode,
  }))
})

onMounted(() => {
  loadDashboard()
  loadWidgets()
  loadDatasets()
})
</script>

<template>
  <div class="dashboard-designer" v-loading="loading">
    <!-- 顶部工具栏 -->
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <el-button @click="handleBack" :icon="'ArrowLeft'">{{ $t('dashboard.designer.back') }}</el-button>
        <span class="dashboard-title">{{ dashboard?.dashboardName || $t('dashboard.designer.title') }}</span>
        <el-tag :type="statusType" size="small">{{ statusLabel }}</el-tag>
        <span class="canvas-size">
          {{ layoutData.canvas?.width || 1920 }} × {{ layoutData.canvas?.height || 1080 }}
        </span>
      </div>
      <div class="toolbar-center">
        <el-slider
          v-model="canvasScale"
          :min="0.25"
          :max="1"
          :step="0.05"
          :format-tooltip="(v: number) => Math.round(v * 100) + '%'"
          style="width: 200px"
        />
      </div>
      <div class="toolbar-right">
        <el-button @click="handleFullscreen">{{ $t('dashboard.designer.fullscreenPreview') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ $t('dashboard.designer.save') }}</el-button>
        <el-button type="success" @click="handlePublish" :disabled="dashboard?.status === 'PUBLISHED'" :loading="publishing">
          {{ $t('dashboard.designer.publish') }}
        </el-button>
      </div>
    </div>

    <!-- 主体区域 -->
    <div class="designer-body">
      <!-- 左侧: 组件库 -->
      <div class="designer-sidebar left-sidebar">
        <div class="sidebar-section">
          <h4 class="section-title">{{ $t('dashboard.designer.componentLibrary') }}</h4>
          <div class="component-palette">
            <div
              v-for="item in COMPONENT_PALETTE"
              :key="item.type"
              class="palette-item"
              draggable="true"
              @dragstart="onPaletteDragStart($event, item)"
              @click="addComponent(item)"
            >
              <el-icon size="20"><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>
        <div class="sidebar-section">
          <h4 class="section-title">{{ $t('dashboard.designer.widgetComponents') }}</h4>
          <div class="widget-list">
            <div
              v-for="w in widgets"
              :key="w.widgetCode"
              class="widget-item"
              :title="w.widgetName"
              draggable="true"
              @dragstart="onPaletteDragStart($event, { type: w.widgetType, label: w.widgetName, widgetCode: w.widgetCode, datasetCode: w.datasetCode })"
            >
              <span class="widget-type-tag">{{ w.widgetType }}</span>
              <span class="widget-name">{{ w.widgetName }}</span>
            </div>
            <div v-if="widgets.length === 0" class="empty-hint">{{ $t('dashboard.designer.noWidget') }}</div>
          </div>
        </div>
        <div class="sidebar-section">
          <h4 class="section-title">{{ $t('dashboard.designer.canvasSettings') }}</h4>
          <el-form label-width="70px" size="small">
            <el-form-item :label="$t('dashboard.designer.theme')">
              <el-select v-model="layoutData.canvas.theme" style="width: 100%">
                <el-option :label="$t('dashboard.designer.themeDark')" value="dark" />
                <el-option :label="$t('dashboard.designer.themeLight')" value="light" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('dashboard.designer.width')">
              <el-input-number v-model="layoutData.canvas.width" :min="800" :max="3840" />
            </el-form-item>
            <el-form-item :label="$t('dashboard.designer.height')">
              <el-input-number v-model="layoutData.canvas.height" :min="600" :max="2160" />
            </el-form-item>
          </el-form>
        </div>
      </div>

      <!-- 中间: 画布 -->
      <div class="designer-canvas-area">
        <div
          class="canvas-wrapper"
          :style="canvasStyle"
          @dragover="onCanvasDragOver"
          @drop="onCanvasDrop"
        >
          <div
            v-for="comp in layoutData.components"
            :key="comp.id"
            class="canvas-component"
            :class="{ selected: selectedComponent?.id === comp.id }"
            :style="getCompStyle(comp)"
            @mousedown="onCompMouseDown($event, comp)"
          >
            <div class="component-header">
              <span class="component-type">{{ comp.type }}</span>
              <span class="component-id">#{{ comp.id }}</span>
              <el-button
                type="danger"
                link
                size="small"
                @click.stop="removeComponent(comp)"
              >
                ×
              </el-button>
            </div>
            <div class="component-body">
              <span class="component-title">{{ comp.props?.title || comp.type }}</span>
              <span v-if="comp.datasetCode" class="component-dataset">{{ comp.datasetCode }}</span>
            </div>
            <!-- 调整大小手柄 -->
            <div
              v-if="selectedComponent?.id === comp.id"
              class="resize-handle"
              @mousedown="onResizeMouseDown($event, comp)"
            ></div>
          </div>
          <div v-if="layoutData.components.length === 0" class="canvas-empty">
            <p>{{ $t('dashboard.designer.canvasEmpty') }}</p>
          </div>
        </div>
      </div>

      <!-- 右侧: 属性面板 -->
      <div class="designer-sidebar right-sidebar">
        <div class="sidebar-section">
          <h4 class="section-title">{{ $t('dashboard.designer.propertyPanel') }}</h4>
          <div v-if="selectedComponent" class="property-panel">
            <el-form label-width="80px" size="small">
              <el-form-item :label="$t('dashboard.designer.componentId')">
                <el-input :model-value="selectedComponent.id" disabled />
              </el-form-item>
              <el-form-item :label="$t('dashboard.designer.type')">
                <el-input :model-value="selectedComponent.type" disabled />
              </el-form-item>
              <el-form-item :label="$t('dashboard.designer.titleLabel')">
                <el-input v-model="selectedComponent.props.title" />
              </el-form-item>
              <el-form-item label="Widget">
                <el-input v-model="selectedComponent.widgetCode" :placeholder="$t('dashboard.designer.widgetCode')" />
              </el-form-item>
              <el-form-item :label="$t('dashboard.designer.dataset')">
                <el-select
                  v-model="selectedComponent.datasetCode"
                  :placeholder="$t('dashboard.designer.selectDataset')"
                  clearable
                  filterable
                  style="width: 100%"
                >
                  <el-option
                    v-for="opt in datasetOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('dashboard.designer.xGrid')">
                <el-input-number v-model="selectedComponent.layout.x" :min="0" :max="23" />
              </el-form-item>
              <el-form-item :label="$t('dashboard.designer.yGrid')">
                <el-input-number v-model="selectedComponent.layout.y" :min="0" :max="11" />
              </el-form-item>
              <el-form-item :label="$t('dashboard.designer.widthGrid')">
                <el-input-number v-model="selectedComponent.layout.w" :min="1" :max="24" />
              </el-form-item>
              <el-form-item :label="$t('dashboard.designer.heightGrid')">
                <el-input-number v-model="selectedComponent.layout.h" :min="1" :max="12" />
              </el-form-item>
            </el-form>
          </div>
          <div v-else class="empty-hint">{{ $t('dashboard.designer.selectComponent') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  background: #2c2c54;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #1a1a2e;
  border-bottom: 1px solid #3d3d5c;
  flex-shrink: 0;
  color: #fff;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dashboard-title {
  font-size: 16px;
  font-weight: 600;
}

.canvas-size {
  color: #909399;
  font-size: 12px;
}

.toolbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.designer-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.designer-sidebar {
  width: 260px;
  background: #16213e;
  border-right: 1px solid #3d3d5c;
  overflow-y: auto;
  flex-shrink: 0;
  color: #e0e0e0;
}

.right-sidebar {
  border-right: none;
  border-left: 1px solid #3d3d5c;
}

.sidebar-section {
  padding: 12px;
  border-bottom: 1px solid #3d3d5c;
}

.section-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #e0e0e0;
}

.component-palette {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.palette-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  border: 1px solid #3d3d5c;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 12px;
  background: #1a1a2e;
}

.palette-item:hover {
  border-color: #409eff;
  background: #0f3460;
}

.widget-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.widget-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.widget-item:hover {
  background: #0f3460;
}

.widget-type-tag {
  padding: 1px 4px;
  background: #3d3d5c;
  border-radius: 2px;
  font-size: 10px;
  color: #e0e0e0;
}

.widget-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-hint {
  color: #909399;
  font-size: 12px;
  text-align: center;
  padding: 12px;
}

.designer-canvas-area {
  flex: 1;
  overflow: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.canvas-wrapper {
  position: relative;
  border: 2px solid #3d3d5c;
  border-radius: 4px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
}

.canvas-component {
  position: absolute;
  border: 2px solid #3d3d5c;
  border-radius: 4px;
  background: rgba(26, 26, 46, 0.8);
  cursor: move;
  transition: border-color 0.2s, box-shadow 0.2s;
  overflow: hidden;
  user-select: none;
}

.canvas-component:hover {
  border-color: #409eff;
}

.canvas-component.selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.4);
}

.component-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid #3d3d5c;
  font-size: 10px;
  color: #e0e0e0;
}

.component-type {
  color: #409eff;
  font-weight: 600;
}

.component-id {
  color: #909399;
}

.component-body {
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.component-title {
  font-size: 12px;
  font-weight: 500;
  color: #e0e0e0;
}

.component-dataset {
  font-size: 10px;
  color: #67c23a;
}

.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
  color: #909399;
}

.property-panel {
  padding: 4px 0;
}

.resize-handle {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 12px;
  height: 12px;
  background: #409eff;
  cursor: se-resize;
  border-top-left-radius: 4px;
  opacity: 0.8;
}

.resize-handle:hover {
  opacity: 1;
}
</style>
