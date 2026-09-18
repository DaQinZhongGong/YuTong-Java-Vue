<script setup lang="ts">
import { ref, onMounted, watch, onBeforeUnmount, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Share, Refresh, VideoPlay, Plus, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { GraphChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  listKnowledgeGraphs,
  getKnowledgeGraph,
  queryKnowledgeGraph,
  createKnowledgeGraph,
  buildKnowledgeGraph,
  listKnowledgeGraphSegments,
  type KnowledgeGraph,
  type KnowledgeGraphSegment,
  type GraphData,
} from '@/api/knowledge'

echarts.use([GraphChart, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  kbId: string
}>()

const loading = ref(false)
const graphList = ref<KnowledgeGraph[]>([])
const selectedId = ref('')
const currentGraph = ref<KnowledgeGraph | null>(null)
const segments = ref<KnowledgeGraphSegment[]>([])
const segmentsLoading = ref(false)
const building = ref(false)
const createDialogVisible = ref(false)
const createName = ref('')
const createLoading = ref(false)

let pollTimer: ReturnType<typeof setInterval> | null = null
const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const entityCount = computed(() => currentGraph.value?.entityCount ?? 0)
const relationCount = computed(() => currentGraph.value?.relationCount ?? 0)

const graphData = computed<GraphData>(() => {
  if (!currentGraph.value?.graphJson) return { nodes: [], edges: [] }
  try {
    const parsed = JSON.parse(currentGraph.value.graphJson)
    return {
      nodes: Array.isArray(parsed.nodes) ? parsed.nodes : [],
      edges: Array.isArray(parsed.edges) ? parsed.edges : [],
    }
  } catch {
    return { nodes: [], edges: [] }
  }
})

function parseJsonSafe(str?: string): unknown {
  if (!str) return null
  try { return JSON.parse(str) } catch { return str }
}

function segmentEntities(seg: KnowledgeGraphSegment): unknown[] {
  const v = parseJsonSafe(seg.entityJson)
  return Array.isArray(v) ? v : v ? [v] : []
}
function segmentRelations(seg: KnowledgeGraphSegment): unknown[] {
  const v = parseJsonSafe(seg.relationJson)
  return Array.isArray(v) ? v : v ? [v] : []
}

function statusTagType(status?: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' | undefined {
  switch (status) {
    case 'READY': return 'success'
    case 'BUILDING': return 'warning'
    case 'FAILED': return 'danger'
    case 'DRAFT': return 'info'
    default: return undefined
  }
}

async function loadList() {
  loading.value = true
  try {
    const page = await listKnowledgeGraphs({ page: 1, size: 20, kbId: props.kbId || undefined })
    const records = (page as unknown as { records?: KnowledgeGraph[] })?.records ?? (page as unknown as KnowledgeGraph[]) ?? []
    graphList.value = Array.isArray(records) ? records : []
    if (graphList.value.length && !selectedId.value) {
      selectedId.value = graphList.value[0].id
      await loadDetail(selectedId.value)
    } else if (selectedId.value) {
      await loadDetail(selectedId.value)
    } else {
      currentGraph.value = null
      segments.value = []
    }
  } catch (e) {
    console.error('listKnowledgeGraphs failed', e)
  } finally {
    loading.value = false
  }
}

async function loadDetail(id: string) {
  if (!id) return
  try {
    const g = await queryKnowledgeGraph(id).catch(() => getKnowledgeGraph(id))
    currentGraph.value = g
    await nextTick()
    renderChart()
    await loadSegments(id)
  } catch (e) {
    console.error('loadDetail failed', e)
  }
}

async function loadSegments(id: string) {
  segmentsLoading.value = true
  try {
    const list = await listKnowledgeGraphSegments(id)
    segments.value = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('list segments failed', e)
    segments.value = []
  } finally {
    segmentsLoading.value = false
  }
}

async function handleCreate() {
  if (!createName.value.trim()) {
    ElMessage.warning('请输入图谱名称')
    return
  }
  createLoading.value = true
  try {
    const g = await createKnowledgeGraph({ kbId: props.kbId || undefined, name: createName.value.trim() } as Partial<KnowledgeGraph>)
    ElMessage.success('图谱已创建')
    createDialogVisible.value = false
    createName.value = ''
    await loadList()
    if (g?.id) {
      selectedId.value = g.id
      await loadDetail(g.id)
    }
  } catch (e) {
    console.error('create failed', e)
    ElMessage.error('创建失败')
  } finally {
    createLoading.value = false
  }
}

function startPoll(id: string) {
  clearPoll()
  let tries = 0
  pollTimer = setInterval(async () => {
    tries++
    if (tries > 60) {
      clearPoll()
      building.value = false
      return
    }
    try {
      const g = await getKnowledgeGraph(id)
      currentGraph.value = g
      renderChart()
      if (g.status === 'READY' || g.status === 'FAILED') {
        clearPoll()
        building.value = false
        if (g.status === 'READY') {
          ElMessage.success('构建完成')
          await loadSegments(id)
        } else {
          ElMessage.error('构建失败: ' + (g.remark || g.status || 'FAILED'))
        }
      }
    } catch {
      // ignore polling errors
    }
  }, 2000)
}

function clearPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

async function handleBuild() {
  if (!currentGraph.value?.id) {
    ElMessage.warning('请先选择或创建知识图谱')
    return
  }
  building.value = true
  try {
    const g = await buildKnowledgeGraph(currentGraph.value.id)
    currentGraph.value = g
    ElMessage.success('构建已触发，轮询状态中...')
    startPoll(currentGraph.value.id)
  } catch (e) {
    console.error('build failed', e)
    ElMessage.error('构建触发失败')
    building.value = false
  }
}

function renderChart() {
  if (!chartRef.value) return
  const data = graphData.value
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  if (!data.nodes.length) {
    chartInstance.clear()
    chartInstance.setOption({
      title: { text: '暂无图谱数据', left: 'center', top: 'center', textStyle: { color: '#94a3b8', fontSize: 13, fontWeight: 400 } },
    } as echarts.EChartsCoreOption)
    return
  }
  const categories = Array.from(new Set(data.nodes.map(n => n.type || 'ENTITY')))
  const catList = categories.map(c => ({ name: c }))
  const option: echarts.EChartsCoreOption = {
    tooltip: {
      trigger: 'item',
      formatter: (p: Record<string, unknown>) => {
        const d = p.data as Record<string, unknown> | undefined
        if (!d) return ''
        if (p.dataType === 'edge') {
          return `${String(d.source ?? '')} —[${String(d.relation ?? (d as { label?: string }).label ?? '')}]→ ${String(d.target ?? '')}`
        }
        return `<b>${String(d.label ?? d.name ?? d.id ?? '')}</b><br/>类型: ${String((d as { type?: string }).type ?? 'ENTITY')}`
      },
    },
    legend: { data: categories, bottom: 4, textStyle: { fontSize: 11, color: '#64748b' } },
    series: [
      {
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        label: { show: true, position: 'right', fontSize: 11, color: '#334155' },
        force: { repulsion: 220, edgeLength: 90, gravity: 0.12 },
        categories: catList,
        data: data.nodes.map(n => ({
          id: n.id,
          name: n.label,
          label: n.label,
          type: n.type,
          category: categories.indexOf(n.type || 'ENTITY'),
          symbolSize: 28,
          itemStyle: { color: n.type === 'PERSON' ? '#2563eb' : n.type === 'ORG' ? '#16a34a' : n.type === 'CONCEPT' ? '#d97706' : '#64748b' },
        })),
        edges: data.edges.map(e => ({
          source: e.source,
          target: e.target,
          relation: e.relation,
          label: { show: true, formatter: e.relation, fontSize: 10, color: '#64748b' },
          lineStyle: { color: '#cbd5e1', width: 1.2, curveness: 0.08 },
        })),
        emphasis: { focus: 'adjacency', lineStyle: { width: 2 } },
      },
    ],
  }
  chartInstance.setOption(option as echarts.EChartsCoreOption, true)
  chartInstance.resize()
}

function handleResize() {
  chartInstance?.resize()
}

watch(() => props.kbId, () => {
  selectedId.value = ''
  loadList()
})

watch(selectedId, (id) => {
  if (id) loadDetail(id)
})

onMounted(() => {
  loadList()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  clearPoll()
  window.removeEventListener('resize', handleResize)
  if (chartInstance) { chartInstance.dispose(); chartInstance = null }
})

// 暴露刷新供父组件触发
defineExpose({ reload: loadList })
</script>

<template>
  <div v-loading="loading" class="kg-tab">
    <!-- 顶部操作区 -->
    <div class="kg-toolbar">
      <div class="kg-toolbar__left">
        <el-select v-model="selectedId" placeholder="选择知识图谱" size="small" style="min-width: 260px" clearable>
          <el-option v-for="g in graphList" :key="g.id" :label="`${g.name} (${g.status})`" :value="g.id" />
        </el-select>
        <el-button size="small" :icon="Refresh" @click="loadList">刷新</el-button>
        <el-button size="small" type="primary" plain :icon="Plus" @click="createDialogVisible = true">新建图谱</el-button>
      </div>
      <div class="kg-toolbar__right">
        <el-button type="primary" size="small" :icon="VideoPlay" :loading="building" :disabled="!currentGraph || currentGraph.status === 'BUILDING' || currentGraph.status === 'READY'" @click="handleBuild">
          {{ building ? '构建中...' : (currentGraph?.status === 'FAILED' ? '重新构建' : '构建图谱') }}
        </el-button>
      </div>
    </div>

    <!-- 统计 -->
    <div class="kg-stats">
      <el-card class="kg-stat-card" shadow="never">
        <div class="kg-stat-card__label"><el-icon><TrendCharts /></el-icon> 实体数量</div>
        <div class="kg-stat-card__value">{{ entityCount }}</div>
        <div class="kg-stat-card__sub">entity_count</div>
      </el-card>
      <el-card class="kg-stat-card" shadow="never">
        <div class="kg-stat-card__label"><el-icon><Share /></el-icon> 关系数量</div>
        <div class="kg-stat-card__value">{{ relationCount }}</div>
        <div class="kg-stat-card__sub">relation_count</div>
      </el-card>
      <el-card v-if="currentGraph" class="kg-stat-card kg-stat-card--status" shadow="never">
        <div class="kg-stat-card__label">构建状态</div>
        <div class="kg-stat-card__value">
          <el-tag :type="statusTagType(currentGraph.status)" effect="plain" size="large">{{ currentGraph.status }}</el-tag>
        </div>
        <div class="kg-stat-card__sub">{{ currentGraph.remark || currentGraph.name }}</div>
      </el-card>
    </div>

    <!-- 无图谱空态 -->
    <div v-if="!currentGraph && !loading" class="yt-empty">
      <el-icon :size="48" style="color: var(--yt-color-primary-light-5)"><Share /></el-icon>
      <div class="yt-empty__text">暂无知识图谱</div>
      <div class="yt-empty__sub">为当前知识库新建一个图谱后，点击 Build 抽取实体-关系并构建图谱</div>
      <el-button type="primary" size="small" :icon="Plus" style="margin-top: var(--yt-space-sm)" @click="createDialogVisible = true">新建图谱</el-button>
    </div>

    <!-- 图谱可视化 -->
    <el-card v-if="currentGraph" class="kg-chart-card" shadow="never">
      <template #header>
        <div class="kg-chart-header">
          <span class="kg-chart-title"><el-icon><Share /></el-icon> 知识图谱可视化</span>
          <span class="kg-chart-sub">{{ graphData.nodes.length }} 个实体 · {{ graphData.edges.length }} 条关系 · echarts graph</span>
        </div>
      </template>
      <div ref="chartRef" class="kg-chart" />
      <!-- 节点 el-tag 补充（满足“节点为 el-tag，边为 line”兜底展示） -->
      <div v-if="graphData.nodes.length" class="kg-tags">
        <el-tag v-for="n in graphData.nodes.slice(0, 48)" :key="n.id" size="small" effect="plain" class="kg-tag" :type="n.type === 'PERSON' ? undefined : n.type === 'ORG' ? 'success' : n.type === 'CONCEPT' ? 'warning' : 'info'">
          {{ n.label }}
        </el-tag>
        <span v-if="graphData.nodes.length > 48" class="kg-tags__more">+{{ graphData.nodes.length - 48 }} 更多</span>
      </div>
      <div v-if="graphData.edges.length" class="kg-edges">
        <span v-for="(e, i) in graphData.edges.slice(0, 12)" :key="i" class="kg-edge">
          <el-tag size="small" type="info" effect="plain">{{ e.source }}</el-tag>
          <span class="kg-edge__line" />
          <el-tag size="small" type="warning" effect="plain">{{ e.relation }}</el-tag>
          <span class="kg-edge__line" />
          <el-tag size="small" type="info" effect="plain">{{ e.target }}</el-tag>
        </span>
        <span v-if="graphData.edges.length > 12" class="kg-tags__more">+{{ graphData.edges.length - 12 }} 条更多</span>
      </div>
    </el-card>

    <!-- Segments 列表 -->
    <el-card v-if="currentGraph" class="kg-segments-card" shadow="never">
      <template #header>
        <div class="kg-chart-header">
          <span class="kg-chart-title">分段抽取结果</span>
          <span class="kg-chart-sub">segments：entity_json / relation_json · 共 {{ segments.length }} 段</span>
        </div>
      </template>
      <div v-loading="segmentsLoading">
        <el-table v-if="segments.length" :data="segments" border stripe size="small" max-height="420">
          <el-table-column label="#" width="56" type="index" />
          <el-table-column label="来源分块" width="220">
            <template #default="{ row }">
              <span class="kg-mono">{{ (row as KnowledgeGraphSegment).sourceChunkId || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="entity_json" min-width="260">
            <template #default="{ row }">
              <div class="kg-json-cell">
                <el-tag v-for="(ent, idx) in (segmentEntities(row as KnowledgeGraphSegment) as unknown[]).slice(0, 6)" :key="idx" size="small" class="kg-json-tag" effect="plain">
                  {{ (ent as Record<string, unknown>).label ?? (ent as Record<string, unknown>).id ?? JSON.stringify(ent).slice(0, 40) }}
                </el-tag>
                <span v-if="(segmentEntities(row as KnowledgeGraphSegment) as unknown[]).length === 0" class="kg-empty-inline">—</span>
                <span v-if="(segmentEntities(row as KnowledgeGraphSegment) as unknown[]).length > 6" class="kg-tags__more">+{{ (segmentEntities(row as KnowledgeGraphSegment) as unknown[]).length - 6 }}</span>
              </div>
              <el-collapse v-if="(segmentEntities(row as KnowledgeGraphSegment) as unknown[]).length" class="kg-json-collapse">
                <el-collapse-item title="查看完整 entity_json">
                  <pre class="kg-json-pre">{{ row.entityJson }}</pre>
                </el-collapse-item>
              </el-collapse>
            </template>
          </el-table-column>
          <el-table-column label="relation_json" min-width="260">
            <template #default="{ row }">
              <div class="kg-json-cell">
                <el-tag v-for="(rel, idx) in (segmentRelations(row as KnowledgeGraphSegment) as unknown[]).slice(0, 6)" :key="idx" size="small" type="warning" effect="plain" class="kg-json-tag">
                  {{ (rel as Record<string, unknown>).relation ?? JSON.stringify(rel).slice(0, 32) }}
                </el-tag>
                <span v-if="(segmentRelations(row as KnowledgeGraphSegment) as unknown[]).length === 0" class="kg-empty-inline">—</span>
                <span v-if="(segmentRelations(row as KnowledgeGraphSegment) as unknown[]).length > 6" class="kg-tags__more">+{{ (segmentRelations(row as KnowledgeGraphSegment) as unknown[]).length - 6 }}</span>
              </div>
              <el-collapse v-if="(segmentRelations(row as KnowledgeGraphSegment) as unknown[]).length" class="kg-json-collapse">
                <el-collapse-item title="查看完整 relation_json">
                  <pre class="kg-json-pre">{{ row.relationJson }}</pre>
                </el-collapse-item>
              </el-collapse>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="yt-empty" style="padding: var(--yt-space-lg)">
          <div class="yt-empty__text">暂无分段抽取结果</div>
          <div class="yt-empty__sub">构建完成后，segments 将展示各分块的 entity_json / relation_json</div>
        </div>
      </div>
    </el-card>

    <!-- 新建对话框 -->
    <el-dialog v-model="createDialogVisible" title="新建知识图谱" width="480px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="图谱名称" required>
          <el-input v-model="createName" placeholder="如：YuTong 平台知识图谱" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="关联知识库">
          <el-input :model-value="props.kbId" disabled placeholder="当前 KB ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.kg-tab {
  display: flex;
  flex-direction: column;
  gap: var(--yt-space-md);
}
.kg-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--yt-space-sm);
}
.kg-toolbar__left {
  display: flex;
  gap: var(--yt-space-sm);
  align-items: center;
  flex-wrap: wrap;
}
.kg-toolbar__right {
  display: flex;
  gap: var(--yt-space-sm);
}
.kg-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--yt-space-md);
}
.kg-stat-card {
  text-align: center;
  border: 1px solid var(--yt-border-light);
  border-radius: var(--yt-radius-md);
}
.kg-stat-card__label {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--yt-space-xs);
  margin-bottom: var(--yt-space-xs);
}
.kg-stat-card__value {
  font-size: 28px;
  font-weight: var(--yt-font-weight-bold);
  color: var(--yt-text-primary);
  line-height: 36px;
}
.kg-stat-card__sub {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-disabled);
  margin-top: var(--yt-space-xs);
}
.kg-stat-card--status {
  background: var(--yt-bg-page);
}
.kg-chart-card, .kg-segments-card {
  border: 1px solid var(--yt-border-light);
  border-radius: var(--yt-radius-md);
}
.kg-chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--yt-space-sm);
}
.kg-chart-title {
  font-weight: var(--yt-font-weight-bold);
  color: var(--yt-text-primary);
  display: flex;
  align-items: center;
  gap: var(--yt-space-xs);
  font-size: var(--yt-font-size-section);
}
.kg-chart-sub {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
}
.kg-chart {
  width: 100%;
  height: 420px;
  background: var(--yt-bg-card);
  border: 1px solid var(--yt-border-light);
  border-radius: var(--yt-radius-md);
}
.kg-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yt-space-xs);
  margin-top: var(--yt-space-md);
}
.kg-tag {
  border-radius: var(--yt-radius-sm);
}
.kg-tags__more {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
  align-self: center;
}
.kg-edges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yt-space-sm);
  margin-top: var(--yt-space-sm);
}
.kg-edge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  background: var(--yt-bg-page);
  border: 1px solid var(--yt-border-light);
  border-radius: var(--yt-radius-sm);
}
.kg-edge__line {
  width: 18px;
  height: 1px;
  background: var(--yt-border-default);
  display: inline-block;
}
.kg-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: var(--yt-text-secondary);
  word-break: break-all;
}
.kg-json-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
.kg-json-tag {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.kg-json-collapse {
  margin-top: 6px;
  border: none;
}
.kg-json-collapse :deep(.el-collapse-item__header) {
  font-size: 12px;
  color: var(--yt-color-primary);
  height: 28px;
}
.kg-json-pre {
  max-height: 220px;
  overflow: auto;
  background: var(--yt-bg-page);
  border: 1px solid var(--yt-border-light);
  border-radius: var(--yt-radius-sm);
  padding: var(--yt-space-sm);
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
.kg-empty-inline {
  color: var(--yt-text-disabled);
  font-size: var(--yt-font-size-caption);
}
.yt-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--yt-space-xl) var(--yt-space-md);
  background: var(--yt-bg-card);
  border: 1px dashed var(--yt-border-default);
  border-radius: var(--yt-radius-md);
  gap: var(--yt-space-sm);
  text-align: center;
}
.yt-empty__text { font-size: var(--yt-font-size-body); color: var(--yt-text-primary); font-weight: var(--yt-font-weight-bold); }
.yt-empty__sub { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
</style>
