<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getReport,
  updateReport,
  publishReport,
  pageWidgets,
} from '@/api/report-dashboard'
import type { RptReportDashboard, RptWidget } from '@/api/report-dashboard'
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
 * 报表设计器页面。设计来源: 42-报表与大屏可视化设计 R2。
 * 布局: 左侧组件库 + 中间画布(栅格 24 列) + 右侧属性面板 + 底部数据预览。
 */

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const reportCode = route.params.id as string

const loading = ref(false)
const report = ref<RptReportDashboard | null>(null)
const widgets = ref<RptWidget[]>([])
const selectedComponent = ref<any>(null)

// 布局数据
const layoutData = ref<any>({
  canvas: { title: '', theme: 'light' },
  components: [],
})

// 组件库
const COMPONENT_PALETTE = [
  { type: 'table', label: t('dashboard.designer.reportTable'), icon: 'Grid' },
  { type: 'line-chart', label: t('dashboard.designer.chartLine'), icon: 'TrendCharts' },
  { type: 'bar-chart', label: t('dashboard.designer.chartBar'), icon: 'Histogram' },
  { type: 'pie-chart', label: t('dashboard.designer.chartPie'), icon: 'PieChart' },
  { type: 'kpi-card', label: t('dashboard.designer.kpiCard'), icon: 'DataLine' },
  { type: 'text', label: t('dashboard.designer.text'), icon: 'Document' },
]

// 加载报表数据
async function loadReport() {
  loading.value = true
  try {
    const data = await getReport(reportCode)
    report.value = data
    if (data.layoutJson) {
      try {
        layoutData.value = JSON.parse(data.layoutJson)
      } catch {
        layoutData.value = { canvas: { title: data.reportName, theme: 'light' }, components: [] }
      }
    }
  } catch {
    ElMessage.error(t('dashboard.msg.loadReportFailed'))
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

// 添加组件到画布
function addComponent(paletteItem: { type: string; label: string }) {
  const newId = 'c' + (layoutData.value.components.length + 1)
  const component = {
    id: newId,
    type: paletteItem.type,
    datasetCode: '',
    props: { title: paletteItem.label },
    layout: { x: 0, y: 0, w: 12, h: 6 },
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

// 保存报表
async function handleSave() {
  if (!report.value) return
  try {
    await updateReport(reportCode, {
      reportCode: report.value.reportCode,
      reportName: report.value.reportName,
      reportType: report.value.reportType,
      layoutJson: JSON.stringify(layoutData.value),
      datasetBindings: JSON.stringify(
        layoutData.value.components.map((c: any) => ({
          componentId: c.id,
          datasetCode: c.datasetCode,
        }))
      ),
      permissionCode: report.value.permissionCode,
      description: report.value.description,
    })
    ElMessage.success(t('dashboard.msg.saveSuccess'))
  } catch {
    ElMessage.error(t('dashboard.msg.saveFailed'))
  }
}

// 发布报表
async function handlePublish() {
  try {
    await ElMessageBox.confirm(t('dashboard.msg.confirmPublishReport'), t('dashboard.msg.confirmPublish'), {
      type: 'warning',
    })
    await publishReport(reportCode)
    ElMessage.success(t('dashboard.msg.publishSuccess'))
    loadReport()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(t('dashboard.msg.publishFailed'))
    }
  }
}

// 预览
function handlePreview() {
  ElMessage.info(t('dashboard.msg.previewDeveloping'))
}

// 返回
function handleBack() {
  router.push('/reports')
}

// 计算属性
const statusLabel = computed(() => {
  return report.value?.status === 'PUBLISHED' ? t('common.publishStatus.published') : t('common.publishStatus.draft')
})

const statusType = computed(() => {
  return report.value?.status === 'PUBLISHED' ? 'success' : 'info'
})

onMounted(() => {
  loadReport()
  loadWidgets()
})
</script>

<template>
  <div class="report-designer" v-loading="loading">
    <!-- 顶部工具栏 -->
    <div class="designer-toolbar">
      <div class="toolbar-left">
        <el-button @click="handleBack" :icon="'ArrowLeft'">{{ $t('dashboard.report.back') }}</el-button>
        <span class="report-title">{{ report?.reportName || $t('dashboard.report.title') }}</span>
        <el-tag :type="statusType" size="small">{{ statusLabel }}</el-tag>
        <span class="version-tag">v{{ report?.versionNo || 1 }}</span>
      </div>
      <div class="toolbar-right">
        <el-button @click="handlePreview">{{ $t('dashboard.report.preview') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ $t('dashboard.report.save') }}</el-button>
        <el-button type="success" @click="handlePublish" :disabled="report?.status === 'PUBLISHED'">
          {{ $t('dashboard.report.publish') }}
        </el-button>
      </div>
    </div>

    <!-- 主体区域 -->
    <div class="designer-body">
      <!-- 左侧: 组件库 -->
      <div class="designer-sidebar left-sidebar">
        <div class="sidebar-section">
          <h4 class="section-title">{{ $t('dashboard.report.componentLibrary') }}</h4>
          <div class="component-palette">
            <div
              v-for="item in COMPONENT_PALETTE"
              :key="item.type"
              class="palette-item"
              @click="addComponent(item)"
            >
              <el-icon size="20"><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>
        <div class="sidebar-section">
          <h4 class="section-title">{{ $t('dashboard.report.widgetComponents') }}</h4>
          <div class="widget-list">
            <div
              v-for="w in widgets"
              :key="w.widgetCode"
              class="widget-item"
              :title="w.widgetName"
            >
              <span class="widget-type-tag">{{ w.widgetType }}</span>
              <span class="widget-name">{{ w.widgetName }}</span>
            </div>
            <div v-if="widgets.length === 0" class="empty-hint">{{ $t('dashboard.report.noWidget') }}</div>
          </div>
        </div>
      </div>

      <!-- 中间: 画布 -->
      <div class="designer-canvas">
        <div class="canvas-header">
          <span class="canvas-title">{{ layoutData.canvas?.title || $t('dashboard.report.canvas') }}</span>
          <span class="canvas-hint">{{ $t('dashboard.report.canvasHint') }}</span>
        </div>
        <div class="canvas-grid">
          <div
            v-for="comp in layoutData.components"
            :key="comp.id"
            class="canvas-component"
            :class="{ selected: selectedComponent?.id === comp.id }"
            :style="{
              width: (comp.layout.w / 24) * 100 + '%',
              height: comp.layout.h * 40 + 'px',
            }"
            @click.stop="selectComponent(comp)"
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
                {{ $t('dashboard.report.delete') }}
              </el-button>
            </div>
            <div class="component-body">
              <span class="component-title">{{ comp.props?.title || comp.type }}</span>
              <span v-if="comp.datasetCode" class="dataset-bind">
                {{ $t('dashboard.report.dataset') }}: {{ comp.datasetCode }}
              </span>
              <span v-else class="no-dataset">{{ $t('dashboard.report.noDataset') }}</span>
            </div>
          </div>
          <div v-if="layoutData.components.length === 0" class="canvas-empty">
            <p>{{ $t('dashboard.report.canvasEmpty') }}</p>
          </div>
        </div>
      </div>

      <!-- 右侧: 属性面板 -->
      <div class="designer-sidebar right-sidebar">
        <div class="sidebar-section">
          <h4 class="section-title">{{ $t('dashboard.report.propertyPanel') }}</h4>
          <div v-if="selectedComponent" class="property-panel">
            <el-form label-width="80px" size="small">
              <el-form-item :label="$t('dashboard.report.componentId')">
                <el-input :model-value="selectedComponent.id" disabled />
              </el-form-item>
              <el-form-item :label="$t('dashboard.report.type')">
                <el-input :model-value="selectedComponent.type" disabled />
              </el-form-item>
              <el-form-item :label="$t('dashboard.report.titleLabel')">
                <el-input v-model="selectedComponent.props.title" />
              </el-form-item>
              <el-form-item :label="$t('dashboard.report.dataset')">
                <el-input v-model="selectedComponent.datasetCode" :placeholder="$t('dashboard.report.datasetCodePlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('dashboard.report.widthGrid')">
                <el-input-number v-model="selectedComponent.layout.w" :min="1" :max="24" />
              </el-form-item>
              <el-form-item :label="$t('dashboard.report.heightRow')">
                <el-input-number v-model="selectedComponent.layout.h" :min="1" :max="20" />
              </el-form-item>
            </el-form>
          </div>
          <div v-else class="empty-hint">{{ $t('dashboard.report.selectComponent') }}</div>
        </div>
        <div class="sidebar-section">
          <h4 class="section-title">{{ $t('dashboard.report.datasetBinding') }}</h4>
          <div class="dataset-bindings">
            <div
              v-for="comp in layoutData.components"
              :key="'bind-' + comp.id"
              class="binding-row"
            >
              <span class="binding-id">{{ comp.id }}</span>
              <el-input
                v-model="comp.datasetCode"
                :placeholder="$t('dashboard.report.datasetCodePlaceholder')"
                size="small"
              />
            </div>
            <div v-if="layoutData.components.length === 0" class="empty-hint">
              {{ $t('dashboard.report.noComponentBinding') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部: 数据预览 -->
    <div class="designer-footer">
      <div class="footer-header">
        <span class="footer-title">{{ $t('dashboard.report.dataPreview') }}</span>
      </div>
      <div class="footer-body">
        <el-table :data="[]" border size="small" style="width: 100%">
          <el-table-column prop="col1" :label="$t('dashboard.report.column1')" />
          <el-table-column prop="col2" :label="$t('dashboard.report.column2')" />
          <el-table-column prop="col3" :label="$t('dashboard.report.column3')" />
          <template #empty>
            <div class="preview-empty">{{ $t('dashboard.report.previewEmpty') }}</div>
          </template>
        </el-table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  background: #f5f7fa;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.report-title {
  font-size: 16px;
  font-weight: 600;
}

.version-tag {
  color: #909399;
  font-size: 12px;
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
  width: 240px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  flex-shrink: 0;
}

.right-sidebar {
  border-right: none;
  border-left: 1px solid #e4e7ed;
}

.sidebar-section {
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.section-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
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
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 12px;
}

.palette-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
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
  background: #f5f7fa;
}

.widget-type-tag {
  padding: 1px 4px;
  background: #e4e7ed;
  border-radius: 2px;
  font-size: 10px;
  color: #606266;
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

.designer-canvas {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.canvas-header {
  padding: 8px 16px;
  background: #fafafa;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.canvas-title {
  font-weight: 600;
  font-size: 14px;
}

.canvas-hint {
  color: #909399;
  font-size: 12px;
}

.canvas-grid {
  flex: 1;
  padding: 16px;
  overflow: auto;
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 8px;
}

.canvas-component {
  border: 2px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
  min-height: 80px;
}

.canvas-component:hover {
  border-color: #409eff;
}

.canvas-component.selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.component-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-size: 11px;
}

.component-type {
  color: #409eff;
  font-weight: 600;
}

.component-id {
  color: #909399;
}

.component-body {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.component-title {
  font-size: 13px;
  font-weight: 500;
}

.dataset-bind {
  font-size: 11px;
  color: #67c23a;
}

.no-dataset {
  font-size: 11px;
  color: #e6a23c;
}

.canvas-empty {
  width: 100%;
  text-align: center;
  padding: 60px 0;
  color: #909399;
}

.property-panel {
  padding: 4px 0;
}

.dataset-bindings {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.binding-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.binding-id {
  font-size: 11px;
  color: #909399;
  width: 30px;
  flex-shrink: 0;
}

.designer-footer {
  height: 200px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.footer-header {
  padding: 8px 16px;
  border-bottom: 1px solid #e4e7ed;
}

.footer-title {
  font-weight: 600;
  font-size: 13px;
}

.footer-body {
  flex: 1;
  overflow: auto;
  padding: 8px 16px;
}

.preview-empty {
  padding: 20px;
  color: #909399;
  text-align: center;
}
</style>
