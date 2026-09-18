<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Connection, Tools, Star, Download, RefreshLeft, View } from '@element-plus/icons-vue'
import { listMarkets, listMarketTools, installMarket, syncMarket } from '@/api/mcpMarket'
import type { McpMarket, McpMarketTool } from '@/api/mcpMarket'

const loading = ref(false)
const syncing = ref(false)
const installingId = ref<string | null>(null)
const list = ref<McpMarket[]>([])
const keyword = ref('')
const categoryFilter = ref<string>('all')
const statusFilter = ref<string>('all')
const currentPage = ref(1)
const pageSize = ref(9)

// drawer / detail
const drawer = ref(false)
const current = ref<McpMarket | null>(null)

// tools dialog
const toolsDialog = ref(false)
const toolsLoading = ref(false)
const toolsList = ref<McpMarketTool[]>([])
const toolsMarketName = ref('')

async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: 1, size: 100 }
    const kw = keyword.value.trim()
    if (kw) params.keyword = kw
    if (categoryFilter.value !== 'all') params.category = categoryFilter.value
    if (statusFilter.value !== 'all') params.status = statusFilter.value
    const data = (await listMarkets(params as never)) as unknown as { records?: McpMarket[] } | McpMarket[]
    list.value = Array.isArray(data) ? data : (data.records || [])
    currentPage.value = 1
  } catch (e) {
    list.value = []
    ElMessage.error((e as Error)?.message || '加载 MCP 市场失败')
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter(s => {
    if (kw && !(`${s.name || ''} ${s.code || ''} ${s.description || ''} ${s.provider || ''}`.toLowerCase().includes(kw))) return false
    if (categoryFilter.value !== 'all' && (s.category || '') !== categoryFilter.value) return false
    if (statusFilter.value !== 'all' && (s.status || '') !== statusFilter.value) return false
    return true
  })
})

const paged = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filtered.value.slice(start, start + pageSize.value)
})

const stats = computed(() => ({
  total: list.value.length,
  published: list.value.filter(s => s.status === 'PUBLISHED').length,
  categories: new Set(list.value.map(s => s.category).filter(Boolean)).size,
}))

const categoryOptions = computed(() => {
  const set = new Set<string>()
  for (const m of list.value) if (m.category) set.add(m.category)
  return Array.from(set)
})

function statusType(s?: string | null): 'success' | 'warning' | 'danger' | 'info' | 'primary' | undefined {
  if (s === 'PUBLISHED') return 'success'
  if (s === 'DRAFT') return 'info'
  if (s === 'OFFLINE') return 'warning'
  if (s === 'DISABLED') return 'danger'
  return undefined
}

function statusLabel(s?: string | null): string {
  if (s === 'PUBLISHED') return '已发布'
  if (s === 'DRAFT') return '草稿'
  if (s === 'OFFLINE') return '已下线'
  if (s === 'DISABLED') return '已禁用'
  return s || '未知'
}

function categoryColor(c?: string | null): string {
  if (c === '数据源') return '#2563eb'
  if (c === '协作') return '#d97706'
  if (c === '搜索') return '#16a34a'
  return 'var(--yt-color-primary, #2563eb)'
}

function openDetail(item: McpMarket) {
  current.value = item
  drawer.value = true
}

async function handleInstall(item: McpMarket) {
  installingId.value = item.id
  try {
    const updated = await installMarket(item.id)
    const nextCount = (updated?.installCount ?? (item.installCount || 0) + 1) as number
    item.installCount = nextCount
    item._installed = true
    if (updated?.status) item.status = updated.status
    ElMessage.success(`已安装 ${item.name}，已注册 MCP 服务 · 安装量 ${nextCount}`)
  } catch (e) {
    ElMessage.error((e as Error)?.message || '安装失败')
  } finally {
    installingId.value = null
  }
}

async function handleSync() {
  syncing.value = true
  try {
    await syncMarket()
    ElMessage.success('已同步内置 MCP 目录')
    await load()
  } catch (e) {
    ElMessage.error((e as Error)?.message || '同步失败')
  } finally {
    syncing.value = false
  }
}

async function handleViewTools(item: McpMarket) {
  toolsMarketName.value = item.name
  toolsDialog.value = true
  toolsLoading.value = true
  toolsList.value = []
  try {
    const tools = await listMarketTools(item.id)
    toolsList.value = Array.isArray(tools) ? tools : []
  } finally {
    toolsLoading.value = false
  }
}

function prettyJson(v?: string | null): string {
  if (!v) return '—'
  try {
    return JSON.stringify(JSON.parse(v), null, 2)
  } catch {
    return v
  }
}

onMounted(load)
</script>

<template>
  <div class="mcp-page">
    <div class="page-head">
      <div class="head-left">
        <h2 class="head-title"><el-icon><Connection /></el-icon> MCP 广场</h2>
        <div class="head-sub">
          <span class="sub-stat"><strong>{{ stats.total }}</strong> 市场条目</span><span class="dot">·</span>
          <span class="sub-stat"><strong>{{ stats.published }}</strong> 已发布</span><span class="dot">·</span>
          <span class="sub-stat">{{ stats.categories }} 分类</span>
        </div>
      </div>
      <div class="head-actions">
        <el-button size="small" :icon="RefreshLeft" :loading="syncing" @click="handleSync">同步市场</el-button>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索名称 / 编码 / 描述 / 提供方" clearable style="max-width:360px" @input="currentPage=1" @clear="currentPage=1" />
        <el-select v-model="categoryFilter" style="width:160px" @change="currentPage=1">
          <el-option label="全部分类" value="all" />
          <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="statusFilter" style="width:160px" @change="currentPage=1">
          <el-option label="全部状态" value="all" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已下线" value="OFFLINE" />
          <el-option label="已禁用" value="DISABLED" />
        </el-select>
        <span class="filter-count">已筛选 {{ filtered.length }} / {{ list.length }}</span>
      </div>
    </el-card>

    <div v-loading="loading" class="mcp-grid">
      <div v-for="s in paged" :key="s.id" class="mcp-card" :class="{ installed: !!s._installed }">
        <div class="card-head">
          <div class="mcp-icon" :style="{ background: categoryColor(s.category) }"><el-icon><Connection /></el-icon></div>
          <div class="mcp-meta">
            <div class="mcp-name" :title="s.name">{{ s.name }} <span class="mcp-code">{{ s.code }}</span></div>
            <div class="mcp-provider" :title="s.provider || ''">{{ s.provider || '—' }} · {{ s.category || '未分类' }}</div>
          </div>
          <el-tag :type="statusType(s.status)" size="small" effect="plain">{{ statusLabel(s.status) }}</el-tag>
        </div>
        <div class="mcp-desc">{{ s.description || '—' }}</div>
        <div class="mcp-tags">
          <el-tag size="small" effect="plain" type="info">{{ s.category || '未分类' }}</el-tag>
          <el-tag size="small" effect="plain" type="info"><el-icon><Star /></el-icon> {{ s.rating ?? '—' }}</el-tag>
          <el-tag size="small" effect="plain" type="warning"><el-icon><Download /></el-icon> {{ s.installCount ?? 0 }} 安装</el-tag>
          <el-tag v-if="s._installed" size="small" type="success" effect="plain">已安装</el-tag>
        </div>
        <div class="card-actions">
          <el-button size="small" type="primary" :icon="Download" :loading="installingId===s.id" @click="handleInstall(s)">{{ s._installed ? '再次安装' : '安装' }}</el-button>
          <el-button size="small" :icon="Tools" @click="handleViewTools(s)">工具</el-button>
          <el-button size="small" :icon="View" @click="openDetail(s)">详情</el-button>
        </div>
      </div>

      <div v-if="!loading && !paged.length" class="empty-state">
        <div class="empty-illus">◎</div>
        <div class="empty-title">暂无匹配的市场条目</div>
        <div class="empty-desc">尝试调整关键字或筛选条件，或点击同步拉取</div>
        <el-button size="small" @click="keyword=''; categoryFilter='all'; statusFilter='all'">清除筛选</el-button>
        <div class="empty-legend">
          <span class="legend-chip"><i class="dot" style="background:#2563eb" />数据源</span>
          <span class="legend-chip"><i class="dot" style="background:#d97706" />协作</span>
          <span class="legend-chip"><i class="dot" style="background:#16a34a" />搜索</span>
        </div>
      </div>
    </div>

    <div v-if="filtered.length > pageSize" class="pager-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="filtered.length"
        :page-size="pageSize"
        :current-page="currentPage"
        :page-sizes="[9,12,24]"
        @size-change="(v:number)=>{ pageSize=v; currentPage=1 }"
        @current-change="(v:number)=> currentPage=v"
      />
    </div>

    <div class="foot-hint">后端 GET /api/v1/mcp/market · 支持 keyword/category/status · 安装 POST /{id}/install · 同步 POST /sync · 工具 GET /{id}/tools</div>

    <el-drawer v-model="drawer" :title="current ? `详情 · ${current.name}` : '详情'" size="520px" direction="rtl" destroy-on-close>
      <div v-if="current" style="display:flex; flex-direction:column; gap:16px">
        <el-alert type="info" :closable="false" :title="`编码 ${current.code} · 提供方 ${current.provider || '—'}`" />
        <div class="drawer-meta">
          <div><strong>{{ current.name }}</strong> <el-tag size="small" :type="statusType(current.status)" style="margin-left:6px">{{ statusLabel(current.status) }}</el-tag></div>
          <div style="font-size:12px; color: var(--yt-text-secondary); margin-top:6px">{{ current.description || '—' }}</div>
          <div style="margin-top:10px; display:flex; gap:6px; flex-wrap:wrap">
            <el-tag size="small" effect="plain">{{ current.category || '未分类' }}</el-tag>
            <el-tag size="small" effect="plain" type="warning">安装 {{ current.installCount ?? 0 }}</el-tag>
            <el-tag size="small" effect="plain">评分 {{ current.rating ?? '—' }}</el-tag>
            <el-tag v-if="current._installed" size="small" type="success">已安装</el-tag>
          </div>
          <div style="margin-top:12px">
            <div style="font-size:12px; font-weight:600; color: var(--yt-text-primary)">configJson</div>
            <pre class="json-pre">{{ prettyJson(current.configJson) }}</pre>
          </div>
          <div style="display:flex; gap:8px; margin-top:12px">
            <el-button size="small" type="primary" :loading="installingId===current.id" :icon="Download" @click="handleInstall(current!)">安装</el-button>
            <el-button size="small" :icon="Tools" @click="handleViewTools(current!)">查看工具</el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="toolsDialog" :title="`工具 · ${toolsMarketName}`" width="640px" destroy-on-close>
      <div v-loading="toolsLoading">
        <el-alert v-if="!toolsLoading && !toolsList.length" type="info" :closable="false" title="暂无工具" />
        <div v-for="t in toolsList" :key="t.id" class="tool-row">
          <div class="tool-name">{{ t.toolName }}</div>
          <div class="tool-desc">{{ t.toolDesc || '—' }}</div>
          <pre v-if="t.inputSchema" class="json-pre small">{{ prettyJson(t.inputSchema) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button size="small" @click="toolsDialog=false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mcp-page { display:flex; flex-direction:column; gap:14px; padding:4px 2px 20px; background: var(--yt-bg-page, #f6f8fb); min-height:100% }
.page-head { display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:16px; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)) }
.head-title { margin:0; font-size:18px; font-weight:600; display:flex; gap:8px; align-items:center }
.head-sub { display:flex; align-items:center; gap:8px; font-size:12px; color: var(--yt-text-secondary); margin-top:6px }
.head-sub strong{ color: var(--yt-text-primary) }
.dot { color: var(--yt-text-disabled) }
.head-actions { display:flex; gap:8px; align-items:center; flex-wrap:wrap }
.filter-card :deep(.el-card__body){ padding:12px 16px }
.filter-row { display:flex; align-items:center; gap:12px; flex-wrap:wrap }
.filter-count { font-size:12px; color: var(--yt-text-secondary) }
.mcp-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(320px,1fr)); gap:16px }
.mcp-card { background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:14px; display:flex; flex-direction:column; gap:10px; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)); transition: all 140ms ease }
.mcp-card:hover{ border-color: var(--yt-color-primary-light-5); box-shadow: 0 4px 16px rgba(37,99,235,.08); transform: translateY(-1px) }
.mcp-card.installed{ border-color: var(--yt-color-success-light-5, #a7f3d0) }
.card-head{ display:flex; gap:10px; align-items:center }
.mcp-icon{ width:36px; height:36px; border-radius:10px; display:flex; align-items:center; justify-content:center; color:#fff; flex-shrink:0 }
.mcp-meta{ flex:1; min-width:0 }
.mcp-name{ font-size:14px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.mcp-code{ font-size:11px; font-weight:400; color: var(--yt-text-secondary); margin-left:6px; font-family: ui-monospace, monospace }
.mcp-provider{ font-size:11px; color: var(--yt-text-secondary); white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.mcp-desc{ font-size:12px; color: var(--yt-text-secondary); min-height:28px; line-height:1.5 }
.mcp-tags{ display:flex; gap:6px; flex-wrap:wrap; align-items:center }
.card-actions{ display:flex; gap:8px; align-items:center; margin-top:2px }
.card-actions .el-button{ flex:1 }
.empty-state{ grid-column: 1 / -1; text-align:center; padding:36px 0; background: var(--yt-bg-card); border:1px dashed var(--yt-border-default); border-radius:12px }
.empty-illus{ font-size:32px; color: var(--yt-text-disabled) }
.empty-title{ font-weight:600; margin-top:8px }
.empty-desc{ font-size:12px; color: var(--yt-text-secondary); margin:6px 0 12px }
.empty-legend{ display:flex; gap:12px; justify-content:center; font-size:11px; color: var(--yt-text-secondary); margin-top:12px }
.legend-chip .dot{ display:inline-block; width:8px; height:8px; border-radius:50%; margin-right:4px }
.pager-wrap{ display:flex; justify-content:center; margin-top:4px }
.foot-hint{ font-size:11px; color: var(--yt-text-secondary); text-align:center }
.drawer-meta{ background: var(--yt-bg-subtle, #f8fafc); border:1px solid var(--yt-border-light); border-radius:8px; padding:12px; font-size:13px }
.json-pre{ background: var(--yt-bg-page, #f6f8fb); border:1px solid var(--yt-border-light); border-radius:6px; padding:8px 10px; font-family: ui-monospace, monospace; font-size:11px; white-space:pre-wrap; word-break:break-all; margin-top:6px; color: var(--yt-text-primary) }
.json-pre.small{ font-size:11px; padding:6px 8px }
.tool-row{ border:1px solid var(--yt-border-light); border-radius:8px; padding:10px 12px; margin-bottom:8px; background: var(--yt-bg-card) }
.tool-name{ font-weight:600; font-size:13px }
.tool-desc{ font-size:12px; color: var(--yt-text-secondary); margin-top:4px }
</style>
