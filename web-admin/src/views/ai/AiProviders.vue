<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Lightning, VideoCamera, Microphone, Picture, Document, Star, StarFilled } from '@element-plus/icons-vue'
import {
  AI_MODEL_TYPE_OPTIONS,
  AI_MULTIMODAL_OPTIONS,
  AI_PLATFORM_TABS,
  AI_PROVIDER_TYPE_META,
  AI_PROVIDER_TYPE_OPTIONS,
  checkProvidersHealth,
  getProvidersHealth,
  listProviders,
} from '@/api/aiProviders'
import type { AiProvider, AiProviderHealth } from '@/api/types'
import { track } from '@/utils/tracker'

/**
 * AI 供应商厂商矩阵 — Phase 1 精美重构。
 * 设计来源: 13-AI能力设计 provider registry / V036 平价能力 (11厂商/9模型类型/平台)
 * 视觉: --yt-* tokens, 卡片矩阵, 11 厂商主题色 + 平台 Tabs + /media 能力标签
 * 能力: 关联健康探测、优先级、启用态、多模态、搜索/筛选(厂商类型/模型类型/平台/启用态/关键字)
 */

interface ProviderRow extends AiProvider {
  health?: AiProviderHealth
}

const { t } = useI18n()
const loading = ref(false)
const checking = ref(false)
const providers = ref<AiProvider[]>([])
const healthMap = ref<Map<string, AiProviderHealth>>(new Map())
const lastCheckedAt = ref('')

// 筛选态
const keyword = ref('')
const providerTypeFilter = ref<string>('all')
const modelTypeFilter = ref<string>('all')
const enabledFilter = ref<'all' | 'enabled' | 'disabled'>('all')
const platformTab = ref<string>('all') // all | dify | coze | fastgpt | ''(直连)

const currentPage = ref(1)
const pageSize = ref(12)

// 合并行
const rows = computed<ProviderRow[]>(() => {
  if (providers.value.length > 0) {
    return providers.value.map((p) => ({ ...p, health: healthMap.value.get(p.providerCode) }))
  }
  return Array.from(healthMap.value.values()).map((h) => ({
    providerCode: h.providerCode,
    providerName: h.providerName || h.providerCode,
    endpoint: undefined,
    enabled: true,
    priority: undefined,
    modelCount: undefined,
    health: h,
  }))
})

const degraded = computed(() => providers.value.length === 0 && healthMap.value.size > 0)

// 平台 tab 过滤 + 其他筛选
const filteredRows = computed<ProviderRow[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter((r) => {
    if (kw) {
      const hit =
        (r.providerCode || '').toLowerCase().includes(kw) ||
        (r.providerName || '').toLowerCase().includes(kw) ||
        (r.endpoint || '').toLowerCase().includes(kw)
      if (!hit) return false
    }
    if (providerTypeFilter.value !== 'all' && (r.providerType || 'custom_api') !== providerTypeFilter.value) return false
    if (modelTypeFilter.value !== 'all' && (r.modelType || 'chat') !== modelTypeFilter.value) return false
    if (enabledFilter.value === 'enabled' && !r.enabled) return false
    if (enabledFilter.value === 'disabled' && r.enabled) return false
    if (platformTab.value !== 'all') {
      const plat = r.platform || ''
      if (plat !== platformTab.value) return false
    }
    return true
  })
})

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})

const stats = computed(() => {
  const total = filteredRows.value.length
  const enabled = filteredRows.value.filter((r) => r.enabled).length
  const healthy = filteredRows.value.filter((r) => r.health?.reachable).length
  const directCount = rows.value.filter((r) => !r.platform).length
  return { total, enabled, healthy, directCount, rawTotal: rows.value.length }
})

watch([keyword, providerTypeFilter, modelTypeFilter, enabledFilter, platformTab], () => {
  currentPage.value = 1
})

function handleSizeChange(v: number) {
  pageSize.value = v
  currentPage.value = 1
}
function handleCurrentChange(v: number) {
  currentPage.value = v
}
function resetFilters() {
  keyword.value = ''
  providerTypeFilter.value = 'all'
  modelTypeFilter.value = 'all'
  enabledFilter.value = 'all'
  platformTab.value = 'all'
  currentPage.value = 1
}

// 工具
function metaFor(type?: string | null) {
  const key = (type || 'custom_api').toLowerCase()
  return AI_PROVIDER_TYPE_META[key] || AI_PROVIDER_TYPE_META['custom_api']
}
function platformLabel(p?: string | null): string {
  if (!p) return '直连'
  const hit = AI_PLATFORM_TABS.find((x) => x.value === p)
  return hit?.label || p
}
function modelTypeLabel(m?: string | null): string {
  if (!m) return '-'
  return AI_MODEL_TYPE_OPTIONS.find((x) => x.value === m)?.label || m
}
function countModels(modelListJson?: string): number | undefined {
  if (!modelListJson) return undefined
  try {
    const parsed = JSON.parse(modelListJson)
    return Array.isArray(parsed) ? parsed.length : undefined
  } catch {
    return undefined
  }
}
function parseMultimodal(json?: string): string[] {
  if (!json) return []
  try {
    const obj = JSON.parse(json)
    if (typeof obj !== 'object' || obj === null) return []
    return Object.entries(obj)
      .filter(([, v]) => v === true || v === 'true' || v === 1)
      .map(([k]) => k.toLowerCase())
  } catch {
    return []
  }
}
function mediaTagColor(cap: string): string {
  const map: Record<string, string> = { image: 'success', video: 'primary', audio: 'warning', ppt: '' }
  return map[cap] || 'info'
}
function healthStatusOf(row: ProviderRow): { label: string; type: '' | 'success' | 'warning' | 'danger' | 'info' } {
  // 优先 provider 持久化 healthStatus, 否则用探测 reachable
  const s = (row.healthStatus || '').toUpperCase()
  if (s === 'HEALTHY') return { label: '健康', type: 'success' }
  if (s === 'UNHEALTHY') return { label: '异常', type: 'danger' }
  if (s === 'DEGRADED') return { label: '降级', type: 'warning' }
  if (row.health) return row.health.reachable ? { label: '可达', type: 'success' } : { label: '不可达', type: 'danger' }
  return { label: '未知', type: 'info' }
}
function priorityTone(p?: number): string {
  if (p == null) return 'var(--yt-text-disabled)'
  if (p <= 1) return 'var(--yt-color-danger)'
  if (p <= 3) return 'var(--yt-color-warning)'
  if (p <= 6) return 'var(--yt-color-primary)'
  return 'var(--yt-text-secondary)'
}
function formatTime(ts?: string): string {
  if (!ts) return '-'
  try {
    const d = new Date(ts)
    if (isNaN(d.getTime())) return ts
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return ts
  }
}
function endpointDisplay(e?: string): string {
  if (!e) return '-'
  try {
    const u = new URL(e)
    return u.host + (u.pathname !== '/' ? u.pathname : '')
  } catch {
    return e.length > 36 ? e.slice(0, 33) + '...' : e
  }
}

async function loadProviders() {
  try {
    const res = await listProviders()
    const list = (res as unknown as { records?: AiProvider[] })?.records || (Array.isArray(res) ? (res as unknown as AiProvider[]) : [])
    const arr = Array.isArray(list) ? list : []
    providers.value = arr.map((p) => ({ ...p, modelCount: p.modelCount ?? countModels(p.modelListJson) }))
  } catch (e) {
    providers.value = []
    track('web.ai.providers.list.failed', {
      bizType: 'ai_provider',
      result: 'FAILED',
      errorCode: 'AI_PROVIDERS_LIST_ERROR',
      payload: { reason: (e as Error)?.message || 'unknown' },
    })
  }
}
async function loadHealth() {
  loading.value = true
  const startedAt = Date.now()
  try {
    const list = await getProvidersHealth()
    const arr = Array.isArray(list) ? list : []
    const map = new Map<string, AiProviderHealth>()
    for (const h of arr) if (h?.providerCode) map.set(h.providerCode, h)
    healthMap.value = map
    lastCheckedAt.value = pickLatest(arr)
    track('web.ai.providers.health.query.success', { bizType: 'ai_provider', result: 'SUCCESS', durationMs: Date.now() - startedAt, payload: { count: arr.length } })
  } catch (e) {
    ElMessage.error(t('ai.msg.healthQueryFailed'))
    track('web.ai.providers.health.query.failed', { bizType: 'ai_provider', result: 'FAILED', errorCode: 'AI_PROVIDERS_HEALTH_QUERY_ERROR', durationMs: Date.now() - startedAt, payload: { reason: (e as Error)?.message || 'unknown' } })
  } finally {
    loading.value = false
  }
}
async function handleCheckHealth() {
  checking.value = true
  const startedAt = Date.now()
  try {
    const list = await checkProvidersHealth()
    const arr = Array.isArray(list) ? list : []
    const map = new Map<string, AiProviderHealth>()
    for (const h of arr) if (h?.providerCode) map.set(h.providerCode, h)
    healthMap.value = map
    lastCheckedAt.value = pickLatest(arr)
    ElMessage.success(t('ai.msg.healthCheckDone', { count: arr.length }))
    track('web.ai.providers.health.check.success', { bizType: 'ai_provider', result: 'SUCCESS', durationMs: Date.now() - startedAt, payload: { count: arr.length } })
  } catch (e) {
    ElMessage.error(t('ai.msg.healthCheckFailed'))
    track('web.ai.providers.health.check.failed', { bizType: 'ai_provider', result: 'FAILED', errorCode: 'AI_PROVIDERS_HEALTH_CHECK_ERROR', durationMs: Date.now() - startedAt, payload: { reason: (e as Error)?.message || 'unknown' } })
  } finally {
    checking.value = false
  }
}
function pickLatest(list: AiProviderHealth[]): string {
  let latest = ''
  for (const h of list) if (h.checkedAt && h.checkedAt > latest) latest = h.checkedAt
  return latest
}

onMounted(async () => {
  await Promise.all([loadProviders(), loadHealth()])
})
</script>

<template>
  <div class="ai-matrix-page">
    <!-- 顶部标题 + 统计 + 操作 -->
    <div class="matrix-header">
      <div class="header-left">
        <h2 class="matrix-title">厂商矩阵</h2>
        <span class="matrix-subtitle">11 厂商 · 9 模型类型 · Dify / Coze / FastGPT 直连全覆盖</span>
        <div class="stats-row">
          <span class="stat"><strong>{{ stats.rawTotal }}</strong> 厂商已接入</span>
          <span class="stat-dot">·</span>
          <span class="stat"><strong>{{ stats.enabled }}</strong> 已启用</span>
          <span class="stat-dot">·</span>
          <span class="stat"><strong>{{ stats.healthy }}</strong> 健康可达</span>
          <span class="stat-dot">·</span>
          <span class="stat"><strong>{{ stats.directCount }}</strong> 直连</span>
          <el-tag v-if="degraded" size="small" type="warning" class="degraded-tag">降级展示</el-tag>
          <el-tag v-if="lastCheckedAt" size="small" type="info" class="time-tag">最近检测 {{ formatTime(lastCheckedAt) }}</el-tag>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" :loading="checking" :icon="Lightning" @click="handleCheckHealth">
          {{ checking ? '检测中' : '刷新健康状态' }}
        </el-button>
        <el-button :loading="loading" :icon="Refresh" @click="loadHealth">查询最近状态</el-button>
      </div>
    </div>

    <!-- 平台 Tabs -->
    <div class="platform-tabs">
      <button
        v-for="tab in AI_PLATFORM_TABS"
        :key="String(tab.value)"
        class="plat-tab"
        :class="{ active: platformTab === tab.value }"
        @click="platformTab = tab.value"
      >
        <span class="plat-dot" :class="'plat-' + (tab.value || 'direct')" />
        {{ tab.label }}
        <span v-if="tab.value === 'all'" class="plat-count">{{ rows.length }}</span>
      </button>
    </div>

    <!-- 筛选栏 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-grid">
        <el-input
          v-model="keyword"
          class="filter-kw"
          :placeholder="'搜索厂商编码 / 名称 / 端点'"
          clearable
          :prefix-icon="Search"
        />
        <el-select v-model="providerTypeFilter" placeholder="厂商类型" class="filter-sel">
          <el-option label="全部厂商" value="all" />
          <el-option v-for="o in AI_PROVIDER_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="modelTypeFilter" placeholder="模型类型" class="filter-sel">
          <el-option label="全部模型" value="all" />
          <el-option v-for="o in AI_MODEL_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="enabledFilter" class="filter-sel filter-sm">
          <el-option label="全部状态" value="all" />
          <el-option label="已启用" value="enabled" />
          <el-option label="未启用" value="disabled" />
        </el-select>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="active-filters" v-if="keyword || providerTypeFilter !== 'all' || modelTypeFilter !== 'all' || enabledFilter !== 'all' || platformTab !== 'all'">
        <span class="active-label">已筛选 {{ filteredRows.length }} / {{ rows.length }}</span>
      </div>
    </el-card>

    <!-- 卡片矩阵 -->
    <div v-loading="loading" class="matrix-grid">
      <div
        v-for="(row, idx) in pagedRows"
        :key="row.providerCode + idx"
        class="vendor-card"
        :style="{ '--accent': metaFor(row.providerType).color } as unknown as Record<string, string>"
      >
        <div class="card-accent" />
        <div class="card-head">
          <div class="vendor-icon" :style="{ background: metaFor(row.providerType).color }">
            <span class="vendor-emoji">{{ metaFor(row.providerType).icon }}</span>
          </div>
          <div class="vendor-meta">
            <div class="vendor-name" :title="row.providerName">{{ row.providerName }}</div>
            <div class="vendor-code">{{ row.providerCode }}</div>
          </div>
          <div class="card-badges">
            <el-tag :type="(healthStatusOf(row).type as any) || 'info'" size="small" effect="plain" class="health-tag">
              <span class="health-dot" :class="'dot-' + healthStatusOf(row).type" />
              {{ healthStatusOf(row).label }}
            </el-tag>
          </div>
        </div>

        <div class="card-tags">
          <el-tag size="small" class="type-tag" :style="{ borderColor: metaFor(row.providerType).color, color: metaFor(row.providerType).color }">
            {{ metaFor(row.providerType).label }}
          </el-tag>
          <el-tag size="small" type="info" effect="plain">{{ modelTypeLabel(row.modelType) }}</el-tag>
          <el-tag size="small" :type="(row.platform ? 'primary' : 'info') as any" effect="plain" :class="{ 'plat-direct': !row.platform }">
            {{ platformLabel(row.platform) }}
          </el-tag>
          <el-tag v-if="row.enabled" size="small" type="success">已启用</el-tag>
          <el-tag v-else size="small" type="info">未启用</el-tag>
        </div>

        <div class="card-body">
          <div class="info-row">
            <span class="info-label">端点</span>
            <el-tooltip :content="row.endpoint || '-'" :disabled="!row.endpoint || row.endpoint.length < 30" placement="top">
              <span class="info-value endpoint">{{ endpointDisplay(row.endpoint) }}</span>
            </el-tooltip>
          </div>
          <div class="info-row">
            <span class="info-label">优先级</span>
            <span class="priority-pill" :style="{ color: priorityTone(row.priority), borderColor: priorityTone(row.priority) }">
              <el-icon v-if="row.priority != null && row.priority <= 2"><StarFilled /></el-icon>
              <el-icon v-else><Star /></el-icon>
              {{ row.priority != null ? 'P' + row.priority : '-' }}
            </span>
            <span class="info-sep">·</span>
            <span class="info-label">模型</span>
            <span class="info-value">{{ row.modelCount != null ? row.modelCount + ' 个' : (row.modelListJson ? '已配置' : '-') }}</span>
            <span v-if="row.health?.latencyMs != null" class="latency">{{ row.health.latencyMs }}ms</span>
          </div>
        </div>

        <!-- 多模态 /media 能力 -->
        <div class="media-row">
          <span class="media-label">/media</span>
          <template v-if="parseMultimodal(row.multimodalCapabilities).length">
            <el-tag
              v-for="cap in parseMultimodal(row.multimodalCapabilities)"
              :key="cap"
              size="small"
              :type="(mediaTagColor(cap) as any)"
              effect="plain"
              class="media-tag"
            >
              <el-icon v-if="cap === 'image'"><Picture /></el-icon>
              <el-icon v-else-if="cap === 'video'"><VideoCamera /></el-icon>
              <el-icon v-else-if="cap === 'audio'"><Microphone /></el-icon>
              <el-icon v-else-if="cap === 'ppt'"><Document /></el-icon>
              {{ cap }}
            </el-tag>
          </template>
          <span v-else class="media-empty">— 无多模态</span>
          <!-- 额外按 /media 路径展示固定 4 能力占位 -->
          <span class="media-hint">
            <span v-for="o in AI_MULTIMODAL_OPTIONS" :key="o.value" class="hint-item" :class="{ on: parseMultimodal(row.multimodalCapabilities).includes(o.value) }" :title="o.mediaPath">{{ o.label }}</span>
          </span>
        </div>

        <div class="card-foot">
          <span class="foot-time">{{ row.health?.checkedAt ? formatTime(row.health.checkedAt) : (row.healthCheckedTime ? formatTime(row.healthCheckedTime) : '未检测') }}</span>
          <el-tooltip v-if="row.health?.errorMessage" :content="row.health.errorMessage" placement="top">
            <span class="foot-error">查看错误</span>
          </el-tooltip>
          <span v-else-if="row.health?.defaultModelOk === false" class="foot-warn">模型异常</span>
          <span v-else-if="row.health?.defaultModelOk === true" class="foot-ok">模型正常</span>
        </div>
      </div>

      <!-- 空态 -->
      <div v-if="!loading && pagedRows.length === 0" class="empty-state">
        <div class="empty-illus">◈</div>
        <div class="empty-title">暂无匹配的厂商</div>
        <div class="empty-desc">尝试调整筛选条件或切换平台 Tab</div>
        <el-button type="primary" plain @click="resetFilters">清除筛选</el-button>
        <!-- 11 厂商图例 -->
        <div class="legend-grid">
          <span v-for="o in AI_PROVIDER_TYPE_OPTIONS" :key="o.value" class="legend-item" :style="{ borderColor: o.color }">
            <span class="legend-dot" :style="{ background: o.color }">{{ o.icon }}</span>{{ o.label }}
          </span>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pager-wrap" v-if="filteredRows.length > pageSize">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="filteredRows.length"
        :page-size="pageSize"
        :current-page="currentPage"
        :page-sizes="[12, 24, 48]"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 底栏图例 (有数据时也展示 11 厂商色板) -->
    <div class="matrix-legend">
      <span class="legend-title">厂商色板</span>
      <span v-for="o in AI_PROVIDER_TYPE_OPTIONS" :key="o.value" class="legend-chip" :style="{ '--c': o.color } as unknown as Record<string,string>">
        <span class="chip-dot" />{{ o.label }}
      </span>
      <span class="legend-divider">|</span>
      <span v-for="o in AI_MULTIMODAL_OPTIONS" :key="o.value" class="legend-chip media-chip">
        {{ o.label }}<small>{{ o.mediaPath }}</small>
      </span>
    </div>
  </div>
</template>

<style scoped>
.ai-matrix-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 2px 24px;
  background: var(--yt-bg-page, #f6f8fb);
  min-height: 100%;
}

/* Header */
.matrix-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}
.matrix-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--yt-text-primary, #111827);
  letter-spacing: -0.02em;
}
.matrix-subtitle {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--yt-text-secondary, #64748b);
}
.stats-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
  font-size: 12px;
  color: var(--yt-text-secondary, #4b5563);
}
.stats-row strong { color: var(--yt-text-primary, #111827); font-weight: 700; }
.stat-dot { color: var(--yt-border-default, #e5e7eb); }
.degraded-tag, .time-tag { margin-left: 4px; }
.header-actions { display: flex; gap: 8px; align-items: center; }

/* Platform tabs */
.platform-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 4px;
  background: var(--yt-bg-card, #fff);
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 10px;
  width: fit-content;
}
.plat-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--yt-text-secondary, #4b5563);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms ease-out;
}
.plat-tab:hover { background: var(--yt-bg-page, #f6f8fb); color: var(--yt-text-primary, #111827); }
.plat-tab.active {
  background: var(--yt-color-primary, #2563eb);
  color: #fff;
  border-color: var(--yt-color-primary, #2563eb);
  box-shadow: 0 2px 8px rgba(37,99,235,0.25);
}
.plat-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: currentColor; opacity: 0.9;
}
.plat-dot.plat-direct { background: var(--yt-text-disabled, #9ca3af); }
.plat-tab.active .plat-dot { background: #fff; }
.plat-count {
  min-width: 18px; height: 18px; padding: 0 5px;
  border-radius: 99px;
  background: rgba(255,255,255,0.22);
  font-size: 11px; line-height: 18px; text-align: center;
}
.plat-tab:not(.active) .plat-count { background: var(--yt-border-light, #f1f5f9); color: var(--yt-text-secondary); }

/* Filter card */
.filter-card { border-radius: 12px; border: 1px solid var(--yt-border-default, #e5e7eb); }
.filter-card :deep(.el-card__body) { padding: 12px 14px; }
.filter-grid {
  display: flex; gap: 8px; flex-wrap: wrap; align-items: center;
}
.filter-kw { width: 260px; flex: 1 1 220px; max-width: 360px; }
.filter-sel { width: 150px; }
.filter-sm { width: 130px; }
.active-filters { margin-top: 8px; font-size: 12px; color: var(--yt-text-secondary); }
.active-label { background: var(--yt-bg-page); padding: 2px 8px; border-radius: 99px; }

/* Grid */
.matrix-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
@media (max-width: 1280px) { .matrix-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px)  { .matrix-grid { grid-template-columns: 1fr; } }

.vendor-card {
  position: relative;
  background: var(--yt-bg-card, #fff);
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 12px;
  padding: 14px 14px 10px;
  overflow: hidden;
  transition: transform 160ms ease-out, box-shadow 160ms ease-out, border-color 160ms ease-out;
}
.vendor-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--yt-shadow-popover, 0 12px 32px rgba(15,23,42,0.12));
  border-color: var(--accent, var(--yt-border-default));
}
.card-accent {
  position: absolute; left: 0; top: 0; bottom: 0;
  width: 3px; background: var(--accent, var(--yt-color-primary)); opacity: 0.9;
}
.card-head {
  display: flex; align-items: center; gap: 10px;
}
.vendor-icon {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; flex-shrink: 0;
  box-shadow: 0 4px 10px rgba(0,0,0,0.12);
}
.vendor-emoji { font-size: 18px; line-height: 1; }
.vendor-meta { flex: 1; min-width: 0; }
.vendor-name { font-size: 14px; font-weight: 600; color: var(--yt-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.vendor-code { font-size: 11px; color: var(--yt-text-disabled); font-family: 'JetBrains Mono', monospace; }
.health-tag { display: inline-flex; align-items: center; gap: 4px; }
.health-dot { width: 6px; height: 6px; border-radius: 50%; display: inline-block; }
.dot-success { background: var(--yt-color-success); }
.dot-danger  { background: var(--yt-color-danger); }
.dot-warning { background: var(--yt-color-warning); }
.dot-info    { background: var(--yt-color-info); }

.card-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.type-tag { background: #fff; font-weight: 600; }
.plat-direct { background: var(--yt-bg-page); }

.card-body { margin-top: 10px; display: flex; flex-direction: column; gap: 6px; }
.info-row { display: flex; align-items: center; gap: 6px; font-size: 12px; flex-wrap: wrap; }
.info-label { color: var(--yt-text-disabled); font-size: 11px; letter-spacing: 0.02em; }
.info-value { color: var(--yt-text-secondary); }
.info-value.endpoint { font-family: 'JetBrains Mono', monospace; font-size: 11px; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.info-sep { color: var(--yt-border-default); }
.priority-pill {
  display: inline-flex; align-items: center; gap: 3px;
  padding: 1px 7px; border-radius: 99px;
  border: 1px solid currentColor; font-weight: 600; font-size: 11px; background: #fff;
}
.latency { margin-left: auto; color: var(--yt-color-success); font-weight: 600; font-size: 11px; background: #f0fdf4; padding: 1px 6px; border-radius: 99px; border: 1px solid #dcfce7; }

.media-row {
  display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
  margin-top: 10px; padding-top: 8px; border-top: 1px dashed var(--yt-border-light, #f1f5f9);
  font-size: 12px;
}
.media-label { font-size: 11px; color: var(--yt-text-disabled); font-family: monospace; }
.media-tag { font-weight: 500; }
.media-empty { color: var(--yt-text-disabled); font-size: 11px; }
.media-hint { display: inline-flex; gap: 4px; margin-left: 6px; }
.hint-item { font-size: 10px; padding: 1px 5px; border-radius: 99px; background: var(--yt-bg-page); color: var(--yt-text-disabled); border: 1px solid var(--yt-border-light); }
.hint-item.on { background: var(--yt-color-primary-light-9, #e2ecfe); color: var(--yt-color-primary); border-color: var(--yt-color-primary-light-7); font-weight: 600; }

.card-foot {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 8px; font-size: 11px; color: var(--yt-text-disabled);
}
.foot-error { color: var(--yt-color-danger); cursor: help; text-decoration: underline dotted; }
.foot-warn { color: var(--yt-color-warning); font-weight: 600; }
.foot-ok { color: var(--yt-color-success); }

.pager-wrap { display: flex; justify-content: flex-end; }

/* Empty */
.empty-state {
  grid-column: 1 / -1;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 36px 16px;
  background: var(--yt-bg-card); border: 1px dashed var(--yt-border-default); border-radius: 12px;
}
.empty-illus { width: 48px; height: 48px; border-radius: 12px; background: var(--yt-bg-page); display: flex; align-items: center; justify-content: center; font-size: 20px; color: var(--yt-text-disabled); }
.empty-title { font-weight: 600; color: var(--yt-text-primary); }
.empty-desc { font-size: 12px; color: var(--yt-text-secondary); }
.legend-grid { display: flex; flex-wrap: wrap; gap: 6px; justify-content: center; margin-top: 12px; max-width: 560px; }
.legend-item { display: inline-flex; align-items: center; gap: 5px; padding: 3px 8px; border-radius: 99px; border: 1px solid var(--yt-border-default); background: #fff; font-size: 11px; color: var(--yt-text-secondary); }
.legend-dot { width: 18px; height: 18px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; color: #fff; font-size: 10px; }

/* Bottom legend */
.matrix-legend {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 10px 12px; background: var(--yt-bg-card); border: 1px solid var(--yt-border-default); border-radius: 10px;
  font-size: 12px; color: var(--yt-text-secondary);
}
.legend-title { font-weight: 600; color: var(--yt-text-primary); margin-right: 4px; }
.legend-chip { display: inline-flex; align-items: center; gap: 5px; padding: 2px 8px; border-radius: 99px; background: var(--yt-bg-page); border: 1px solid var(--yt-border-light); font-size: 11px; }
.chip-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--c, var(--yt-color-primary)); }
.legend-divider { color: var(--yt-border-default); margin: 0 2px; }
.media-chip small { margin-left: 3px; color: var(--yt-text-disabled); font-family: monospace; font-size: 10px; }
</style>
