<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  checkProvidersHealth,
  getProvidersHealth,
  listProviders,
} from '@/api/aiProviders'
import type { AiProvider, AiProviderHealth } from '@/api/types'
import { track } from '@/utils/tracker'

/**
 * AI 供应商管理页面。
 *
 * 设计来源: 13-AI能力设计 provider registry / 37-AI治理与评测设计 供应商健康检查
 *
 * 能力:
 *  - 展示供应商列表 (providerCode/providerName/endpoint/enabled/priority/模型数量)
 *  - 展示最近一次健康状态 (reachable/latencyMs/defaultModelOk/errorMessage)
 *  - 顶部操作栏: 「刷新健康状态」触发 POST /ai/providers/health-check; 「查询最近状态」拉取 GET /ai/providers/health
 *  - 后端列表端点 (GET /ai/providers) 不存在时降级为只展示健康状态返回的供应商
 *  - GA2-18 track() 埋点: 健康检查/查询/降级等关键事件
 *  - WCAG: 表格行/列可读, 状态 tag 含 aria-label
 *  - GA2-? 前端工具条: 关键字搜索 + 启用状态筛选 + 本地/云端类型筛选 + 客户端分页
 */

/** 表格行数据: 供应商基础信息 + 健康状态 (合并后) */
interface ProviderRow extends AiProvider {
  /** 关联的最近健康状态, 没有则为 undefined */
  health?: AiProviderHealth
}

const { t } = useI18n()
const loading = ref(false)
/** 触发健康检查中 (区别于查询, 按钮独立 loading) */
const checking = ref(false)

const providers = ref<AiProvider[]>([])
const healthMap = ref<Map<string, AiProviderHealth>>(new Map())

/** 最近一次健康检查完成时间 (ISO-8601, 用于顶部提示) */
const lastCheckedAt = ref<string>('')

// ============================================================================
// 工具条筛选状态
// ============================================================================

/** 关键字搜索 (providerCode / providerName 模糊匹配, 前端过滤) */
const keyword = ref('')
/** 启用状态筛选: 全部 / 已启用 / 未启用 */
const statusFilter = ref<'all' | 'enabled' | 'disabled'>('all')
/** 类型筛选: 全部 / 本地服务 (endpoint 含 localhost 或 host.docker.internal) / 云端 */
const typeFilter = ref<'all' | 'local' | 'cloud'>('all')

/** 分页状态: 默认 20/页, 可选 10/20/50/100 */
const currentPage = ref(1)
const pageSize = ref(20)

/**
 * 合并供应商列表与健康状态。
 * 优先以 providers 列表为基线, 用 providerCode 关联 healthMap;
 * 若 providers 为空 (后端列表端点不存在), 降级用 healthMap 中的数据构造行 (providerName 等字段可能缺失)。
 */
const rows = computed<ProviderRow[]>(() => {
  if (providers.value.length > 0) {
    return providers.value.map((p) => ({
      ...p,
      health: healthMap.value.get(p.providerCode),
    }))
  }
  // 降级: 仅展示健康状态返回的供应商
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

/** 是否处于降级模式 (后端列表端点不存在, 仅展示健康状态) */
const degraded = computed(() => providers.value.length === 0 && healthMap.value.size > 0)

/**
 * 前端组合筛选 (关键字 + 启用状态 + 类型) 后的行。
 * 三者可任意组合; 统计与分页均以此为基线。
 */
const filteredRows = computed<ProviderRow[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  const status = statusFilter.value
  const type = typeFilter.value
  return rows.value.filter((r) => {
    if (kw) {
      const hit =
        (r.providerCode || '').toLowerCase().includes(kw) ||
        (r.providerName || '').toLowerCase().includes(kw)
      if (!hit) return false
    }
    if (status === 'enabled' && !r.enabled) return false
    if (status === 'disabled' && r.enabled) return false
    if (type === 'local' && !isLocalType(r.endpoint)) return false
    if (type === 'cloud' && isLocalType(r.endpoint)) return false
    return true
  })
})

/** 筛选后的总数统计: 共 N 家, 已启用 M 家 (与筛选联动) */
const filteredTotal = computed(() => filteredRows.value.length)
const filteredEnabled = computed(
  () => filteredRows.value.filter((r) => r.enabled).length
)

/** 当前页要渲染的行 (filteredRows 切片) */
const pagedRows = computed<ProviderRow[]>(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredRows.value.slice(start, start + pageSize.value)
})

/** 判断端点是否为本地服务 (localhost / host.docker.internal, 大小写不敏感) */
function isLocalType(endpoint?: string): boolean {
  if (!endpoint) return false
  const e = endpoint.toLowerCase()
  return e.includes('localhost') || e.includes('host.docker.internal')
}

/** 任一筛选条件变化时回到第 1 页, 避免停留在越界页 */
watch([keyword, statusFilter, typeFilter], () => {
  currentPage.value = 1
})

/** 分页: 每页大小变化 */
function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
}

/** 分页: 当前页变化 */
function handleCurrentChange(page: number) {
  currentPage.value = page
}

/** 重置全部筛选条件 */
function resetFilters() {
  keyword.value = ''
  statusFilter.value = 'all'
  typeFilter.value = 'all'
  currentPage.value = 1
}

/** 安全解析 modelListJson 并返回模型数量 */
function countModels(modelListJson?: string): number | undefined {
  if (!modelListJson) return undefined
  try {
    const parsed = JSON.parse(modelListJson)
    return Array.isArray(parsed) ? parsed.length : undefined
  } catch {
    return undefined
  }
}

/** 加载供应商列表 (失败不阻断, 触发降级模式) */
async function loadProviders() {
  try {
    const res = await listProviders()
    const list = res?.records || []
    // 后端未返回 modelCount 时, 从 modelListJson 计算
    providers.value = list.map((p) => ({
      ...p,
      modelCount: p.modelCount ?? countModels(p.modelListJson),
    }))
  } catch (e) {
    // 后端列表端点不存在时静默降级, 仅展示健康状态
    providers.value = []
    track('web.ai.providers.list.failed', {
      bizType: 'ai_provider',
      result: 'FAILED',
      errorCode: 'AI_PROVIDERS_LIST_ERROR',
      payload: { reason: (e as Error)?.message || 'unknown' },
    })
  }
}

/** 查询最近一次健康状态: GET /ai/providers/health */
async function loadHealth() {
  loading.value = true
  const startedAt = Date.now()
  try {
    const list = await getProvidersHealth()
    const arr = Array.isArray(list) ? list : []
    const map = new Map<string, AiProviderHealth>()
    for (const h of arr) {
      if (h && h.providerCode) {
        map.set(h.providerCode, h)
      }
    }
    healthMap.value = map
    // 取最新 checkedAt 作为顶部提示
    lastCheckedAt.value = pickLatestCheckedAt(arr)
    track('web.ai.providers.health.query.success', {
      bizType: 'ai_provider',
      result: 'SUCCESS',
      durationMs: Date.now() - startedAt,
      payload: { count: arr.length },
    })
  } catch (e) {
    ElMessage.error(t('ai.msg.healthQueryFailed'))
    track('web.ai.providers.health.query.failed', {
      bizType: 'ai_provider',
      result: 'FAILED',
      errorCode: 'AI_PROVIDERS_HEALTH_QUERY_ERROR',
      durationMs: Date.now() - startedAt,
      payload: { reason: (e as Error)?.message || 'unknown' },
    })
  } finally {
    loading.value = false
  }
}

/** 触发健康检查: POST /ai/providers/health-check (后端并发化中, 最长可能约 60 秒) */
async function handleCheckHealth() {
  checking.value = true
  const startedAt = Date.now()
  try {
    const list = await checkProvidersHealth()
    const arr = Array.isArray(list) ? list : []
    const map = new Map<string, AiProviderHealth>()
    for (const h of arr) {
      if (h && h.providerCode) {
        map.set(h.providerCode, h)
      }
    }
    healthMap.value = map
    lastCheckedAt.value = pickLatestCheckedAt(arr)
    ElMessage.success(t('ai.msg.healthCheckDone', { count: arr.length }))
    track('web.ai.providers.health.check.success', {
      bizType: 'ai_provider',
      result: 'SUCCESS',
      durationMs: Date.now() - startedAt,
      payload: { count: arr.length },
    })
  } catch (e) {
    ElMessage.error(t('ai.msg.healthCheckFailed'))
    track('web.ai.providers.health.check.failed', {
      bizType: 'ai_provider',
      result: 'FAILED',
      errorCode: 'AI_PROVIDERS_HEALTH_CHECK_ERROR',
      durationMs: Date.now() - startedAt,
      payload: { reason: (e as Error)?.message || 'unknown' },
    })
  } finally {
    checking.value = false
  }
}

/** 从健康状态数组中取最新的 checkedAt */
function pickLatestCheckedAt(list: AiProviderHealth[]): string {
  let latest = ''
  for (const h of list) {
    if (h.checkedAt && h.checkedAt > latest) {
      latest = h.checkedAt
    }
  }
  return latest
}

/** 格式化时间为本地可读格式 */
function formatTime(t?: string): string {
  if (!t) return ''
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return t
  }
}

/** endpoint 截断显示 (超长时 tooltip 展示完整) */
function endpointDisplay(endpoint?: string): string {
  if (!endpoint) return '-'
  return endpoint.length > 40 ? endpoint.slice(0, 37) + '...' : endpoint
}

onMounted(async () => {
  // 并行加载供应商列表和最近健康状态; 列表失败时降级
  await Promise.all([loadProviders(), loadHealth()])
})
</script>

<template>
  <div class="ai-providers-page">
    <!-- 顶部操作栏 -->
    <el-card class="toolbar-card" shadow="never" :aria-label="$t('ai.provider.aria.toolbar')">
      <div class="toolbar">
        <div class="toolbar-left">
          <h2 class="page-title">{{ $t('ai.provider.page.title') }}</h2>
          <el-tag v-if="degraded" size="small" type="warning">
            {{ $t('ai.provider.tag.degraded') }}
          </el-tag>
          <el-tag v-if="lastCheckedAt" size="small" type="info">
            {{ $t('ai.provider.tag.lastCheck') }}{{ formatTime(lastCheckedAt) }}
          </el-tag>
        </div>
        <div class="toolbar-right">
          <el-button
            type="primary"
            :loading="checking"
            @click="handleCheckHealth"
            :aria-label="checking ? $t('ai.provider.aria.checking') : $t('ai.provider.aria.triggerCheck')"
          >
            {{ checking ? $t('ai.provider.action.checking') : $t('ai.provider.action.refreshHealth') }}
          </el-button>
          <el-button
            :loading="loading"
            @click="loadHealth"
            :aria-label="$t('ai.provider.aria.queryLatest')"
          >
            {{ $t('ai.provider.action.queryLatest') }}
          </el-button>
        </div>
      </div>

      <!-- 筛选工具条: 关键字 / {{ $t('ai.provider.tag.enabled') }}状态 / 类型, 三者可组合 -->
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          class="filter-item filter-keyword"
          :placeholder="$t('ai.provider.placeholder.search')"
          clearable
          :prefix-icon="Search"
          :aria-label="$t('ai.provider.aria.searchKeyword')"
        />
        <el-select
          v-model="statusFilter"
          class="filter-item filter-status"
          :placeholder="$t('ai.provider.placeholder.status')"
          :aria-label="$t('ai.provider.aria.filterStatus')"
        >
          <el-option :label="$t('ai.provider.option.allStatus')" value="all" />
          <el-option :label="$t('ai.provider.option.enabled')" value="enabled" />
          <el-option :label="$t('ai.provider.option.disabled')" value="disabled" />
        </el-select>
        <el-select
          v-model="typeFilter"
          class="filter-item filter-type"
          :placeholder="$t('ai.provider.placeholder.type')"
          :aria-label="$t('ai.provider.aria.filterType')"
        >
          <el-option :label="$t('ai.provider.option.allType')" value="all" />
          <el-option :label="$t('ai.provider.option.local')" value="local" />
          <el-option :label="$t('ai.provider.option.cloud')" value="cloud" />
        </el-select>
        <el-button @click="resetFilters" :aria-label="$t('ai.provider.aria.resetFilters')">{{ $t('ai.provider.action.reset') }}</el-button>
      </div>
    </el-card>

    <!-- 供应商表格 -->
    <el-card class="table-card" shadow="never" :aria-label="$t('ai.provider.aria.table')">
      <div class="table-summary">
        {{ $t('ai.provider.text.totalPrefix') }}<strong>{{ filteredTotal }}</strong>{{ $t('ai.provider.text.totalMiddle') }}
        <strong>{{ filteredEnabled }}</strong>{{ $t('ai.provider.text.totalSuffix') }}
      </div>
      <el-table
        :data="pagedRows"
        v-loading="loading"
        border
        stripe
        :empty-text="$t('ai.provider.text.empty')"
        style="width: 100%"
      >
        <el-table-column
          prop="providerCode"
          :label="$t('ai.provider.field.code')"
          min-width="140"
          fixed="left"
        />
        <el-table-column
          prop="providerName"
          :label="$t('ai.provider.field.name')"
          min-width="160"
        />
        <el-table-column :label="$t('ai.provider.field.endpoint')" min-width="240">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.endpoint"
              :content="row.endpoint"
              placement="top"
              :disabled="!row.endpoint || row.endpoint.length <= 40"
            >
              <span class="endpoint-cell">{{ endpointDisplay(row.endpoint) }}</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('ai.provider.field.type')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="isLocalType(row.endpoint)"
              type="warning"
              size="small"
              :aria-label="`${row.providerName} ${$t('ai.provider.tag.local')}`"
            >
              {{ $t('ai.provider.tag.local') }}
            </el-tag>
            <el-tag
              v-else
              type="info"
              size="small"
              :aria-label="`${row.providerName} ${$t('ai.provider.tag.cloud')}`"
            >
              {{ $t('ai.provider.tag.cloud') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('ai.provider.field.enableStatus')" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.enabled"
              type="success"
              size="small"
              :aria-label="`${row.providerName} ${$t('ai.provider.tag.enabled')}`"
            >
              {{ $t('ai.provider.tag.enabled') }}
            </el-tag>
            <el-tag
              v-else
              type="info"
              size="small"
              :aria-label="`${row.providerName} ${$t('ai.provider.tag.disabled')}`"
            >
              {{ $t('ai.provider.tag.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="priority"
          :label="$t('ai.provider.field.priority')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span v-if="row.priority != null">{{ row.priority }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="modelCount"
          :label="$t('ai.provider.field.modelCount')"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <span v-if="row.modelCount != null">{{ row.modelCount }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('ai.provider.field.healthStatus')" min-width="220">
          <template #default="{ row }">
            <template v-if="row.health">
              <el-tooltip
                v-if="!row.health.reachable && row.health.errorMessage"
                :content="row.health.errorMessage"
                placement="top"
              >
                <el-tag
                  type="danger"
                  size="small"
                  :aria-label="`${row.providerName} ${$t('ai.provider.tag.unreachable')}: ${row.health.errorMessage}`"
                >
                  {{ $t('ai.provider.tag.unreachable') }}
                </el-tag>
              </el-tooltip>
              <el-tag
                v-else-if="row.health.reachable"
                type="success"
                size="small"
                :aria-label="`${row.providerName} ${$t('ai.provider.tag.reachable')}`"
              >
                {{ $t('ai.provider.tag.reachable') }}
              </el-tag>
              <el-tag
                v-else
                type="danger"
                size="small"
                :aria-label="`${row.providerName} ${$t('ai.provider.tag.unreachable')}`"
              >
                {{ $t('ai.provider.tag.unreachable') }}
              </el-tag>
              <span class="health-meta">
                <span v-if="row.health.latencyMs != null" class="health-latency">
                  {{ row.health.latencyMs }}ms
                </span>
                <el-tag
                  v-if="row.health.defaultModelOk === true"
                  type="success"
                  size="small"
                  effect="plain"
                >
                  {{ $t('ai.provider.tag.defaultModelOk') }}
                </el-tag>
                <el-tag
                  v-else-if="row.health.defaultModelOk === false"
                  type="warning"
                  size="small"
                  effect="plain"
                >
                  {{ $t('ai.provider.tag.defaultModelAbnormal') }}
                </el-tag>
              </span>
            </template>
            <span v-else class="health-pending">{{ $t('ai.provider.text.notChecked') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('ai.provider.field.lastCheckTime')" min-width="180">
          <template #default="{ row }">
            <span v-if="row.health?.checkedAt">
              {{ formatTime(row.health.checkedAt) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 客户端分页 -->
      <div class="pager-wrap">
        <el-pagination
          class="pager"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="filteredTotal"
          :page-size="pageSize"
          :current-page="currentPage"
          :page-sizes="[10, 20, 50, 100]"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :aria-label="$t('ai.provider.aria.pagination')"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.ai-providers-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar-card :deep(.el-card__body) {
  padding: 12px 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 筛选工具条 */
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter, #ebeef5);
}

.filter-item {
  max-width: 240px;
}

.filter-keyword {
  width: 240px;
}

.filter-status,
.filter-type {
  width: 140px;
}

.table-summary {
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}

.table-summary strong {
  color: #303133;
  font-weight: 600;
}

.pager-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.endpoint-cell {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #606266;
  cursor: help;
}

.health-meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.health-latency {
  color: #67c23a;
  font-weight: 500;
}

.health-pending {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
