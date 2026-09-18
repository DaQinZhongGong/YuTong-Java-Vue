<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search, Refresh, View, Clock, DataAnalysis } from '@element-plus/icons-vue'
import { pageTraceRuns, getTraceRun, listTraceNodes } from '@/api/trace'
import type { AiTraceRun, AiTraceNode } from '@/api/trace'

/**
 * AI 链路追踪-时间线视图。
 * 设计来源: V043 ai_trace_run/ai_trace_node — 前端时间线视图
 * 后端 6 接口封装于 @/api/trace.ts
 * 路由: /trace 权限 ai:assistant:use + license ai
 */

// ============ 筛选状态 ============
const loading = ref(false)
const errorMsg = ref('')
const tableData = ref<AiTraceRun[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const filterTraceType = ref('')
const filterStatus = ref('')
const filterKeyword = ref('')
const filterDateRange = ref<[string, string] | null>(null)

const TRACE_TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: 'AGENT', value: 'AGENT' },
  { label: 'FLOW', value: 'FLOW' },
  { label: 'RAG', value: 'RAG' },
  { label: 'MCP', value: 'MCP' },
  { label: 'SKILL', value: 'SKILL' },
  { label: 'TOOL', value: 'TOOL' },
  { label: 'LLM', value: 'LLM' },
  { label: 'MEDIA', value: 'MEDIA' },
  { label: 'CUSTOM', value: 'CUSTOM' },
] as const

const STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: 'RUNNING', value: 'RUNNING' },
  { label: 'SUCCESS', value: 'SUCCESS' },
  { label: 'FAILED', value: 'FAILED' },
  { label: 'CANCELLED', value: 'CANCELLED' },
] as const

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function traceTypeTagType(t: string): TagType {
  const map: Record<string, TagType> = {
    AGENT: 'primary',
    FLOW: undefined,
    RAG: 'success',
    MCP: 'warning',
    SKILL: 'info',
    TOOL: 'info',
    LLM: 'primary',
    MEDIA: 'warning',
    CUSTOM: 'info',
  }
  return map[t] ?? 'info'
}

function statusTagType(s: string): TagType {
  const map: Record<string, TagType> = {
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    CANCELLED: 'info',
    PENDING: 'info',
    SKIPPED: undefined,
  }
  return map[s] ?? 'info'
}

function nodeStatusType(s: string): '' | 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    PENDING: 'info',
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    SKIPPED: 'info',
    CANCELLED: 'info',
  }
  return map[s] ?? 'info'
}

function formatLatency(ms?: number | null): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

function formatTime(t?: string | null): string {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (Number.isNaN(d.getTime())) return t
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return t
  }
}

function prettyJson(raw?: string | null): string {
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

// 前端本地过滤（时间范围 / 租户关键字）- 后端暂未支持该参数，本地过滤
const filteredData = computed(() => {
  let list = tableData.value
  // 租户关键字：匹配 tenantId / id / traceType
  const kw = filterKeyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter((r) =>
      [r.id, r.tenantId, r.traceType, r.errorMessage].some((v) => (v || '').toLowerCase().includes(kw)),
    )
  }
  // 时间范围：匹配 createdTime
  if (filterDateRange.value && filterDateRange.value[0] && filterDateRange.value[1]) {
    const start = new Date(filterDateRange.value[0]).getTime()
    const end = new Date(filterDateRange.value[1]).getTime() + 24 * 60 * 60 * 1000 - 1
    list = list.filter((r) => {
      if (!r.createdTime) return false
      const t = new Date(r.createdTime).getTime()
      return t >= start && t <= end
    })
  }
  return list
})

async function loadData() {
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await pageTraceRuns({
      page: currentPage.value,
      size: pageSize.value,
      traceType: filterTraceType.value || undefined,
      status: filterStatus.value || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    errorMsg.value = msg || '加载失败'
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filterTraceType.value = ''
  filterStatus.value = ''
  filterKeyword.value = ''
  filterDateRange.value = null
  currentPage.value = 1
  loadData()
}

function handlePageChange(p: number) {
  currentPage.value = p
  loadData()
}

function handleSizeChange(s: number) {
  pageSize.value = s
  currentPage.value = 1
  loadData()
}

function handleRefresh() {
  loadData()
}

// ============ 详情抽屉 + 时间线 ============
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const currentRun = ref<AiTraceRun | null>(null)
const nodes = ref<AiTraceNode[]>([])
const nodesLoading = ref(false)
const nodesError = ref('')

async function openDetail(row: AiTraceRun) {
  drawerVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  nodes.value = []
  nodesError.value = ''
  try {
    const detail = await getTraceRun(row.id)
    currentRun.value = detail
  } catch (e) {
    detailError.value = e instanceof Error ? e.message : String(e)
    currentRun.value = row
  } finally {
    detailLoading.value = false
  }
  // 加载节点时间线
  nodesLoading.value = true
  try {
    const list = await listTraceNodes(row.id)
    nodes.value = Array.isArray(list) ? list : []
  } catch (e) {
    nodesError.value = e instanceof Error ? e.message : String(e)
  } finally {
    nodesLoading.value = false
  }
}

function closeDrawer() {
  drawerVisible.value = false
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="trace-list" role="main" aria-label="AI 链路追踪">
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <el-icon><DataAnalysis /></el-icon>
            <span>AI 链路追踪</span>
          </div>
          <el-button :icon="Refresh" @click="handleRefresh">刷新</el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="链路类型">
          <el-select
            v-model="filterTraceType"
            placeholder="全部类型"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="o in TRACE_TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 150px">
            <el-option
              v-for="o in STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filterDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 280px"
          />
        </el-form-item>

        <el-form-item label="租户/关键字">
          <el-input
            v-model="filterKeyword"
            placeholder="租户 / ID / 类型 / 错误信息"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 错误态 -->
    <el-alert
      v-if="errorMsg && !loading"
      type="error"
      :title="errorMsg"
      show-icon
      closable
      style="margin-top: 12px"
      @close="errorMsg = ''"
    />

    <!-- 列表 -->
    <el-card shadow="never" style="margin-top: 12px">
      <el-table
        v-loading="loading"
        :data="filteredData"
        border
        stripe
        row-key="id"
        :empty-text="loading ? '加载中...' : '暂无链路追踪数据'"
        aria-label="链路追踪列表"
      >
        <el-table-column prop="id" label="追踪ID" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mono-id">{{ row.id }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="traceType" label="链路类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="traceTypeTagType(row.traceType)" size="small" effect="plain">
              {{ row.traceType }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="耗时" width="120" align="right">
          <template #default="{ row }">
            <span class="latency-cell">
              <el-icon style="margin-right: 4px"><Clock /></el-icon>
              {{ formatLatency(row.latencyMs) }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="createdTime" label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" :icon="View" @click="openDetail(row as AiTraceRun)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空态补充 -->
      <el-empty
        v-if="!loading && !errorMsg && filteredData.length === 0"
        description="暂无匹配的链路数据，调整筛选条件或稍后刷新"
        :image-size="120"
      />

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="链路详情 — 时间线"
      size="560px"
      :close-on-click-modal="true"
      direction="rtl"
      @close="closeDrawer"
    >
      <div v-loading="detailLoading" class="drawer-body">
        <!-- 错误态 -->
        <el-alert
          v-if="detailError"
          type="error"
          :title="detailError"
          show-icon
          style="margin-bottom: 12px"
        />

        <!-- 运行概览 -->
        <el-descriptions
          v-if="currentRun"
          :column="2"
          border
          size="small"
          class="run-descriptions"
        >
          <el-descriptions-item label="追踪ID">
            <span class="mono-id">{{ currentRun.id }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="链路类型">
            <el-tag :type="traceTypeTagType(currentRun.traceType)" size="small">
              {{ currentRun.traceType }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(currentRun.status)" size="small">
              {{ currentRun.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总耗时">
            {{ formatLatency(currentRun.latencyMs) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatTime(currentRun.createdTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="更新时间">
            {{ formatTime(currentRun.updatedTime) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="currentRun.errorMessage" label="错误信息" :span="2">
            <span class="error-text">{{ currentRun.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- Input / Output 可折叠 -->
        <el-collapse v-if="currentRun" style="margin-top: 12px">
          <el-collapse-item title="输入 inputJson" name="input">
            <el-empty v-if="!currentRun.inputJson" description="无输入数据" :image-size="60" />
            <pre v-else class="json-block">{{ prettyJson(currentRun.inputJson) }}</pre>
          </el-collapse-item>
          <el-collapse-item title="输出 outputJson" name="output">
            <el-empty v-if="!currentRun.outputJson" description="无输出数据" :image-size="60" />
            <pre v-else class="json-block">{{ prettyJson(currentRun.outputJson) }}</pre>
          </el-collapse-item>
        </el-collapse>

        <el-divider content-position="left">
          <span class="timeline-title">节点时间线 · {{ nodes.length }} 个节点</span>
        </el-divider>

        <!-- 节点加载 / 错误 / 空态 -->
        <div v-if="nodesLoading" class="timeline-state">
          <el-skeleton :rows="4" animated />
        </div>
        <el-alert
          v-else-if="nodesError"
          type="error"
          :title="nodesError"
          show-icon
          style="margin-bottom: 12px"
        />
        <el-empty
          v-else-if="nodes.length === 0"
          description="该链路暂无节点数据"
          :image-size="80"
        />

        <!-- 时间线 -->
        <el-timeline v-else>
          <el-timeline-item
            v-for="node in nodes"
            :key="node.id"
            :type="nodeStatusType(node.status) === 'success' ? 'success' : nodeStatusType(node.status) === 'danger' ? 'danger' : nodeStatusType(node.status) === 'warning' ? 'warning' : 'primary'"
            :hollow="node.status === 'PENDING' || node.status === 'SKIPPED'"
            :timestamp="formatTime(node.createdTime)"
            placement="top"
          >
            <el-card shadow="never" class="node-card">
              <div class="node-header">
                <span class="node-type">{{ node.nodeType }}</span>
                <el-tag :type="statusTagType(node.status)" size="small" effect="plain">
                  {{ node.status }}
                </el-tag>
                <span class="node-latency">{{ formatLatency(node.latencyMs) }}</span>
              </div>

              <div v-if="node.errorMessage" class="node-error">
                {{ node.errorMessage }}
              </div>

              <el-collapse class="node-collapse">
                <el-collapse-item title="输入" :name="`input-${node.id}`">
                  <el-empty v-if="!node.inputJson" description="无输入" :image-size="50" />
                  <pre v-else class="json-block small">{{ prettyJson(node.inputJson) }}</pre>
                </el-collapse-item>
                <el-collapse-item title="输出" :name="`output-${node.id}`">
                  <el-empty v-if="!node.outputJson" description="无输出" :image-size="50" />
                  <pre v-else class="json-block small">{{ prettyJson(node.outputJson) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>

      <template #footer>
        <el-button @click="closeDrawer">关闭</el-button>
        <el-button
          type="primary"
          :loading="nodesLoading || detailLoading"
          @click="currentRun && openDetail(currentRun)"
        >
          刷新
        </el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.trace-list {
  padding: 0;
}

.filter-card :deep(.el-card__header) {
  padding: var(--yt-space-md, 16px);
  border-bottom: 1px solid var(--yt-border-default, #e5e7eb);
}

.filter-card :deep(.el-card__body) {
  padding: var(--yt-space-md, 16px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  display: flex;
  align-items: center;
  gap: var(--yt-space-sm, 8px);
  font-size: var(--yt-font-size-section, 16px);
  font-weight: var(--yt-font-weight-bold, 600);
  color: var(--yt-text-primary, #111827);
}

.filter-form {
  margin: 0;
}

.mono-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--yt-font-size-caption, 12px);
  color: var(--yt-text-secondary, #4b5563);
  word-break: break-all;
}

.latency-cell {
  display: inline-flex;
  align-items: center;
  color: var(--yt-text-secondary, #4b5563);
  font-size: var(--yt-font-size-caption, 12px);
}

.pagination-wrapper {
  margin-top: var(--yt-space-md, 16px);
  display: flex;
  justify-content: flex-end;
}

.drawer-body {
  padding: 0;
}

.run-descriptions :deep(.el-descriptions__label) {
  background: var(--yt-bg-page, #f6f8fb);
  color: var(--yt-text-secondary, #4b5563);
  font-size: var(--yt-font-size-caption, 12px);
}

.error-text {
  color: var(--yt-color-danger, #dc2626);
  word-break: break-all;
}

.json-block {
  background: var(--yt-bg-page, #f6f8fb);
  border: 1px solid var(--yt-border-light, #f1f5f9);
  border-radius: var(--yt-radius-sm, 4px);
  padding: var(--yt-space-sm, 8px) var(--yt-space-md, 16px);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--yt-font-size-caption, 12px);
  line-height: var(--yt-font-line-height-caption, 18px);
  color: var(--yt-text-regular, #374151);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
  margin: 0;
}

.json-block.small {
  max-height: 220px;
}

.timeline-title {
  font-size: var(--yt-font-size-caption, 12px);
  color: var(--yt-text-secondary, #4b5563);
}

.timeline-state {
  padding: var(--yt-space-md, 16px) 0;
}

.node-card {
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: var(--yt-radius-md, 8px);
}

.node-card :deep(.el-card__body) {
  padding: var(--yt-space-sm, 8px) var(--yt-space-md, 16px);
}

.node-header {
  display: flex;
  align-items: center;
  gap: var(--yt-space-sm, 8px);
  flex-wrap: wrap;
}

.node-type {
  font-weight: var(--yt-font-weight-bold, 600);
  color: var(--yt-text-primary, #111827);
  font-size: var(--yt-font-size-body, 14px);
}

.node-latency {
  margin-left: auto;
  font-size: var(--yt-font-size-caption, 12px);
  color: var(--yt-text-secondary, #4b5563);
}

.node-error {
  margin-top: var(--yt-space-sm, 8px);
  color: var(--yt-color-danger, #dc2626);
  font-size: var(--yt-font-size-caption, 12px);
  word-break: break-all;
}

.node-collapse {
  margin-top: var(--yt-space-sm, 8px);
  border: none;
}

.node-collapse :deep(.el-collapse-item__header) {
  font-size: var(--yt-font-size-caption, 12px);
  color: var(--yt-text-secondary, #4b5563);
}
</style>
