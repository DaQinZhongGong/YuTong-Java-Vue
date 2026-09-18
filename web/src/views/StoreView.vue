<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Plus, Goods, Box, Shop, Tools, Connection, Star, View, Promotion, MagicStick, Reading, Cpu, VideoPlay } from '@element-plus/icons-vue'
import client from '@/api/client'

interface PluginItem { id: string; name: string; description?: string; version?: string; author?: string; price?: number; rating?: number; category?: string; installs?: number; icon?: string }
interface TemplateItem { id: string; name: string; description?: string; tags?: string[]; category?: string; cover?: string; installs?: number }
interface SkillItem { id: string; name: string; description?: string; category?: string; version?: string; author?: string; rating?: number; type: 'skill' }
interface McpItem { id: string; name: string; description?: string; transport?: string; enabled?: boolean; category?: string }
interface StartInput { field: string; label: string; type: number; required?: boolean; defaultValue?: string; options?: string[]; placeholder?: string }
interface AppItem { id: string; name: string; description?: string; category?: string; type?: 'agent'|'workflow'; version?: string; startInputs?: StartInput[]; installs?: number }

const router = useRouter()
const active = ref('apps')
// 短剧卡片跳转：与 drama 工坊联动
function goDrama(){ router.push('/drama') }
const query = ref('')
const category = ref('all')
const loading = ref(false)
const plugins = ref<PluginItem[]>([])
const templates = ref<TemplateItem[]>([])
const skills = ref<SkillItem[]>([])
const mcpServers = ref<McpItem[]>([])
const apps = ref<AppItem[]>([])

const installDialogVisible = ref(false)
const installTarget = ref<{ name: string; version?: string } | null>(null)
const installing = ref(false)

// AppMarket Start schema dialog
const appDialogVisible = ref(false)
const appDialogTarget = ref<AppItem | null>(null)
const appForm = reactive<Record<string, unknown>>({})
const appFormErrors = ref<string[]>([])

const pluginCategories = computed(() => {
  const set = new Set<string>(['all'])
  plugins.value.forEach(p => p.category && set.add(p.category))
  return Array.from(set)
})
const templateCategories = computed(() => {
  const set = new Set<string>(['all'])
  templates.value.forEach(t => t.category && set.add(t.category))
  return Array.from(set)
})
const skillCategories = computed(() => {
  const set = new Set<string>(['all'])
  skills.value.forEach(s => s.category && set.add(s.category))
  return Array.from(set)
})
const mcpCategories = computed(() => {
  const set = new Set<string>(['all'])
  mcpServers.value.forEach(m => m.category && set.add(m.category))
  return Array.from(set)
})
const appCategories = computed(() => {
  const set = new Set<string>(['all'])
  apps.value.forEach(a => a.category && set.add(a.category))
  return Array.from(set)
})
const currentCategories = computed(() => {
  if (active.value === 'apps') return appCategories.value
  if (active.value === 'plugins') return pluginCategories.value
  if (active.value === 'templates') return templateCategories.value
  if (active.value === 'skills') return skillCategories.value
  return mcpCategories.value
})

const filteredPlugins = computed(() => {
  const q = query.value.trim().toLowerCase()
  return plugins.value.filter(p => {
    if (category.value !== 'all' && p.category !== category.value) return false
    if (!q) return true
    return [p.name, p.description, p.author].join(' ').toLowerCase().includes(q)
  })
})
const filteredTemplates = computed(() => {
  const q = query.value.trim().toLowerCase()
  return templates.value.filter(t => {
    if (category.value !== 'all' && t.category !== category.value) return false
    if (!q) return true
    return [t.name, t.description, (t.tags||[]).join(' ')].join(' ').toLowerCase().includes(q)
  })
})
const filteredSkills = computed(() => {
  const q = query.value.trim().toLowerCase()
  return skills.value.filter(s => {
    if (category.value !== 'all' && s.category !== category.value) return false
    if (!q) return true
    return [s.name, s.description, s.author].join(' ').toLowerCase().includes(q)
  })
})
const filteredMcp = computed(() => {
  const q = query.value.trim().toLowerCase()
  return mcpServers.value.filter(m => {
    if (category.value !== 'all' && m.category !== category.value) return false
    if (!q) return true
    return [m.name, m.description, m.transport].join(' ').toLowerCase().includes(q)
  })
})
const filteredApps = computed(() => {
  const q = query.value.trim().toLowerCase()
  return apps.value.filter(a => {
    if (category.value !== 'all' && a.category !== category.value) return false
    if (!q) return true
    return [a.name, a.description, a.category].join(' ').toLowerCase().includes(q)
  })
})
const currentCount = computed(() => {
  if (active.value === 'apps') return filteredApps.value.length
  if (active.value === 'plugins') return filteredPlugins.value.length
  if (active.value === 'templates') return filteredTemplates.value.length
  if (active.value === 'skills') return filteredSkills.value.length
  return filteredMcp.value.length
})

function unwrapList<T>(raw: unknown): T[] {
  if (Array.isArray(raw)) return raw as T[]
  if (raw && typeof raw === 'object' && Array.isArray((raw as { records?: T[] }).records)) {
    return (raw as { records: T[] }).records
  }
  return []
}

async function load() {
  loading.value = true
  try {
    const [pluginRaw, templateRaw, agentRaw, flowRaw, skillRaw, mcpRaw, marketRaw] = await Promise.allSettled([
      client.get('/plugins', { params: { page: 1, size: 48 } }),
      client.get('/code-templates', { params: { page: 1, size: 48 } }),
      client.get('/agents', { params: { page: 1, size: 48, status: 'PUBLISHED' } }),
      client.get('/aiflow/definitions', { params: { page: 1, size: 48, status: 'PUBLISHED' } }),
      client.get('/skills', { params: { page: 1, size: 48, status: 'PUBLISHED' } }),
      client.get('/mcp/servers', { params: { page: 1, size: 48 } }),
      client.get('/mcp/market', { params: { page: 1, size: 48, status: 'PUBLISHED' } }),
    ])
    plugins.value = pluginRaw.status === 'fulfilled' ? unwrapList<PluginItem>(pluginRaw.value) : []
    templates.value = templateRaw.status === 'fulfilled' ? unwrapList<TemplateItem>(templateRaw.value) : []
    const agents = agentRaw.status === 'fulfilled' ? unwrapList<Record<string, unknown>>(agentRaw.value) : []
    const flows = flowRaw.status === 'fulfilled' ? unwrapList<Record<string, unknown>>(flowRaw.value) : []
    apps.value = [
      ...agents.map((a) => ({
        id: String(a.id || ''),
        name: String(a.agentName || a.name || '未命名智能体'),
        description: String(a.systemPrompt || a.remark || ''),
        category: String(a.agentType || 'agent'),
        type: 'agent' as const,
        version: String(a.versionNo || a.version || ''),
        startInputs: [{ field: 'query', label: '问题', type: 1, required: true, placeholder: '输入要交给智能体的问题' }],
      })),
      ...flows.map((f) => ({
        id: String(f.id || ''),
        name: String(f.flowName || f.name || '未命名流程'),
        description: String(f.remark || ''),
        category: 'workflow',
        type: 'workflow' as const,
        version: String(f.versionNo || f.version || ''),
        startInputs: [{ field: 'query', label: '输入', type: 1, required: true, placeholder: '流程启动参数' }],
      })),
    ]
    const skillRows = skillRaw.status === 'fulfilled' ? unwrapList<Record<string, unknown>>(skillRaw.value) : []
    skills.value = skillRows.map((s) => ({
      id: String(s.id || ''),
      name: String(s.skillName || s.name || ''),
      description: String(s.skillMd || s.remark || s.skillCode || ''),
      category: String(s.skillType || 'skill'),
      version: String(s.versionNo || '1'),
      author: String(s.createdBy || ''),
      type: 'skill' as const,
    }))
    const servers = mcpRaw.status === 'fulfilled' ? unwrapList<Record<string, unknown>>(mcpRaw.value) : []
    const markets = marketRaw.status === 'fulfilled' ? unwrapList<Record<string, unknown>>(marketRaw.value) : []
    mcpServers.value = [
      ...servers.map((m) => ({
        id: String(m.id || ''),
        name: String(m.serverName || m.name || ''),
        description: String(m.remark || m.endpoint || ''),
        transport: String(m.transport || ''),
        enabled: Boolean(m.enabled),
        category: String(m.transport || 'server'),
      })),
      ...markets.map((m) => ({
        id: String(m.id || ''),
        name: String(m.name || m.code || ''),
        description: String(m.description || ''),
        transport: 'market',
        enabled: String(m.status || '') === 'PUBLISHED',
        category: String(m.category || 'market'),
      })),
    ]
  } finally { loading.value = false }
}

function openInstall(name: string, version?: string) {
  installTarget.value = { name, version }
  installDialogVisible.value = true
}
async function confirmInstall() {
  if (!installTarget.value) return
  installing.value = true
  try {
    const target = mcpServers.value.find((m) => m.name === installTarget.value?.name)
    if (target?.category && target.transport === 'market') {
      await client.post(`/mcp/market/${encodeURIComponent(target.id)}/install`)
      ElMessage.success(`已安装市场条目：${installTarget.value.name}`)
    } else {
      ElMessage.info(`${installTarget.value.name} 已在租户内注册，无需重复安装`)
    }
    installDialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '安装失败')
  } finally {
    installing.value = false
  }
}

// wf-card App handlers
function handleAppClick(app: AppItem) {
  const inputs = app.startInputs || []
  if (!inputs.length) {
    router.push({ path: '/chat', query: { appId: app.id } })
    ElMessage.success(`已进入 ${app.name}`)
    return
  }
  appDialogTarget.value = app
  // init form with defaults
  Object.keys(appForm).forEach(k => delete appForm[k])
  for (const f of inputs) {
    const def = f.defaultValue ?? ''
    if (f.type === 2) appForm[f.field] = def !== '' ? Number(def) : undefined
    else if (f.type === 5) appForm[f.field] = def === 'true' || def === '1' || def === true as unknown as string
    else appForm[f.field] = def
  }
  appFormErrors.value = []
  appDialogVisible.value = true
}
function submitAppStart() {
  const app = appDialogTarget.value
  if (!app) return
  const errs: string[] = []
  for (const f of (app.startInputs || [])) {
    const v = appForm[f.field]
    if (f.required && (v === undefined || v === '' || v === null)) errs.push(`${f.label} 为必填`)
    if (f.type === 2 && v !== undefined && v !== '' && isNaN(Number(v))) errs.push(`${f.label} 需为数字`)
  }
  if (errs.length) { appFormErrors.value = errs; return }
  appDialogVisible.value = false
  // persist start payload in sessionStorage for chat view to pick up if needed
  try { sessionStorage.setItem(`yt_app_${app.id}_inputs`, JSON.stringify(appForm)) } catch {}
  router.push({ path: '/chat', query: { appId: app.id } })
  ElMessage.success(`已启动 ${app.name}`)
}

onMounted(load)
</script>

<template>
  <div class="yt-store">
    <!-- 顶部Banner -->
    <div class="yt-store__banner">
      <div class="yt-store__banner-left">
        <div class="yt-store__banner-title"><el-icon style="color: var(--yt-bg-card)"><Shop /></el-icon> 商店 / 市场</div>
        <div class="yt-store__banner-desc">已发布智能体 / AIFlow / 技能 / MCP 市场，空列表表示租户尚未发布，不会用演示数据填充</div>
        <div class="yt-store__banner-tags">
          <el-tag size="small" effect="plain" style="background: rgba(255,255,255,0.16); border-color: rgba(255,255,255,0.28); color: #fff">production</el-tag>
          <el-tag size="small" effect="plain" style="background: rgba(255,255,255,0.16); border-color: rgba(255,255,255,0.28); color: #fff">MCP 已就绪</el-tag>
          <el-tag size="small" effect="plain" style="background: rgba(255,255,255,0.16); border-color: rgba(255,255,255,0.28); color: #fff">Skills 4 项</el-tag>
          <el-tag size="small" effect="plain" style="background: rgba(255,255,255,0.16); border-color: rgba(255,255,255,0.28); color: #fff; cursor:pointer" @click="goDrama"><el-icon><VideoPlay /></el-icon> 短剧工坊</el-tag>
        </div>
      </div>
      <div class="yt-store__banner-right">
        <div class="yt-store__kpi"><strong>{{ plugins.length + templates.length + skills.length + mcpServers.length }}</strong><span>总条目</span></div>
        <div class="yt-store__kpi"><strong>{{ skills.length }}</strong><span>技能</span></div>
        <div class="yt-store__kpi"><strong>{{ mcpServers.length }}</strong><span>MCP 服务</span></div>
      </div>
    </div>

    <el-card shadow="never" class="yt-store__header-card">
      <template #header>
        <div class="yt-store__header">
          <el-radio-group v-model="active" size="small" @change="category='all'">
            <el-radio-button value="apps"><el-icon><MagicStick /></el-icon> AI应用</el-radio-button>
            <el-radio-button value="plugins"><el-icon><Box /></el-icon> 插件</el-radio-button>
            <el-radio-button value="templates"><el-icon><Goods /></el-icon> 模板</el-radio-button>
            <el-radio-button value="skills"><el-icon><Tools /></el-icon> 技能</el-radio-button>
            <el-radio-button value="mcp"><el-icon><Connection /></el-icon> MCP</el-radio-button>
          </el-radio-group>
          <span class="yt-store__count">共 {{ currentCount }} 项</span>
        </div>
      </template>

      <div class="yt-store__toolbar">
        <el-input v-model="query" :prefix-icon="Search" placeholder="搜索名称 / 描述 / 作者 / 标签" clearable style="max-width: 360px" size="default" />
        <div class="yt-store__tabs">
          <el-tag
            v-for="c in currentCategories"
            :key="c"
            :effect="category===c ? 'dark' : 'plain'"
            :type="category===c ? 'primary' : 'info'"
            class="yt-store__cat"
            style="cursor: pointer"
            @click="category=c"
          >{{ c==='all' ? '全部' : c }}</el-tag>
        </div>
      </div>
    </el-card>

    <!-- skeleton -->
    <div v-if="loading" class="yt-store__grid">
      <el-card v-for="i in 6" :key="i" shadow="never" class="yt-store__card yt-store__card--skeleton">
        <el-skeleton :rows="3" animated />
      </el-card>
    </div>

    <template v-else>
      <!-- apps : wf-card Grid -->
      <div v-if="active==='apps'" class="yt-store__grid yt-store__grid--apps">
        <!-- 短剧独立入口卡片，跳转 /drama -->
        <div class="wf-card wf-card--drama" @click="goDrama">
          <div class="wf-card__icon" style="background: linear-gradient(135deg, #7c3aed, #ec4899)"><el-icon><VideoPlay /></el-icon></div>
          <div class="wf-card__title">短剧工坊</div>
          <div class="wf-card__desc">4步创作 01创意→02剧本→03资产→04分镜 · 真实 LLM SSE · 复用 drama 7表</div>
          <div class="wf-card__tags">
            <el-tag size="small" effect="plain" type="warning" style="border-color: var(--yt-border-light)">短剧</el-tag>
            <el-tag size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">V043</el-tag>
          </div>
          <el-button size="small" type="primary" class="wf-card__cta" :icon="VideoPlay" @click.stop="goDrama">进入工坊</el-button>
        </div>
        <div
          v-for="app in filteredApps"
          :key="app.id"
          class="wf-card"
          @click="handleAppClick(app)"
        >
          <div class="wf-card__icon">
            <el-icon v-if="app.type==='workflow'"><Connection /></el-icon>
            <el-icon v-else-if="app.category==='workflow'"><Promotion /></el-icon>
            <el-icon v-else><Cpu /></el-icon>
          </div>
          <div class="wf-card__title" :title="app.name">{{ app.name }}</div>
          <div class="wf-card__desc">{{ app.description || '—' }}</div>
          <div class="wf-card__tags">
            <el-tag size="small" effect="plain" :type="app.type==='workflow' ? 'warning' : 'success'" style="border-color: var(--yt-border-light)">{{ app.type==='workflow' ? '工作流' : '智能体' }}</el-tag>
            <el-tag v-if="app.category" size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">{{ app.category }}</el-tag>
            <span v-if="app.startInputs && app.startInputs.length" class="wf-card__inputsTag">{{ app.startInputs.length }} 个输入</span>
          </div>
          <el-button size="small" type="primary" class="wf-card__cta" :icon="VideoPlay" @click.stop="handleAppClick(app)">开始使用</el-button>
        </div>
      </div>
      <div v-else-if="active==='plugins'" class="yt-store__grid">
        <el-card v-for="p in filteredPlugins" :key="p.id" shadow="never" class="yt-store__card">
          <div class="yt-store__card-head">
            <div class="yt-store__card-icon"><el-icon><Box /></el-icon></div>
            <strong class="yt-store__card-title">{{ p.name }}</strong>
            <el-tag size="small" effect="plain" style="margin-left:auto; border-color: var(--yt-border-default); color: var(--yt-text-secondary)">{{ p.version || '—' }}</el-tag>
          </div>
          <div class="yt-store__card-desc">{{ p.description || '—' }}</div>
          <div class="yt-store__card-meta">
            <span class="yt-store__author"><el-icon><Star /></el-icon> {{ p.author || '—' }}</span>
            <span v-if="p.rating" class="yt-store__rating">★ {{ p.rating }}</span>
            <span v-if="p.installs" class="yt-store__installs"><el-icon><View /></el-icon> {{ p.installs }}</span>
            <el-tag v-if="p.category" size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">{{ p.category }}</el-tag>
          </div>
          <el-button size="small" type="primary" class="yt-store__cta" :icon="Plus" @click="openInstall(p.name, p.version)">安装</el-button>
        </el-card>
      </div>

      <div v-else-if="active==='templates'" class="yt-store__grid">
        <el-card v-for="t in filteredTemplates" :key="t.id" shadow="never" class="yt-store__card">
          <div class="yt-store__card-head"><strong class="yt-store__card-title">{{ t.name }}</strong><span v-if="t.installs" class="yt-store__installs"><el-icon><View /></el-icon> {{ t.installs }}</span></div>
          <div class="yt-store__card-desc">{{ t.description || '—' }}</div>
          <div class="yt-store__tags">
            <el-tag v-for="tag in (t.tags||[])" :key="tag" size="small" effect="plain" style="border-color: var(--yt-border-default); color: var(--yt-text-secondary)">{{ tag }}</el-tag>
            <el-tag v-if="t.category" size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">{{ t.category }}</el-tag>
          </div>
          <el-button size="small" type="primary" plain class="yt-store__cta" :icon="Promotion" @click="openInstall(t.name)">使用模板</el-button>
        </el-card>
      </div>

      <div v-else-if="active==='skills'" class="yt-store__grid">
        <el-card v-for="s in filteredSkills" :key="s.id" shadow="never" class="yt-store__card">
          <div class="yt-store__card-head">
            <div class="yt-store__card-icon yt-store__card-icon--skill"><el-icon><Tools /></el-icon></div>
            <strong class="yt-store__card-title">{{ s.name }}</strong>
            <el-tag size="small" effect="plain" style="margin-left:auto; border-color: var(--yt-border-default); color: var(--yt-text-secondary)">{{ s.version || '—' }}</el-tag>
          </div>
          <div class="yt-store__card-desc">{{ s.description || '—' }}</div>
          <div class="yt-store__card-meta">
            <span class="yt-store__author">{{ s.author || '—' }}</span>
            <span v-if="s.rating" class="yt-store__rating">★ {{ s.rating }}</span>
            <el-tag v-if="s.category" size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">{{ s.category }}</el-tag>
          </div>
          <el-button size="small" type="primary" class="yt-store__cta" :icon="Plus" @click="openInstall(s.name, s.version)">接入技能</el-button>
        </el-card>
      </div>

      <div v-else class="yt-store__grid">
        <el-card v-for="m in filteredMcp" :key="m.id" shadow="never" class="yt-store__card">
          <div class="yt-store__card-head">
            <div class="yt-store__card-icon yt-store__card-icon--mcp"><el-icon><Connection /></el-icon></div>
            <strong class="yt-store__card-title">{{ m.name }}</strong>
            <el-tag size="small" :type="m.enabled ? 'success' : 'info'" effect="plain" style="margin-left:auto">{{ m.enabled ? '已启用' : '未启用' }}</el-tag>
          </div>
          <div class="yt-store__card-desc">{{ m.description || '—' }}</div>
          <div class="yt-store__card-meta">
            <el-tag size="small" effect="plain" style="border-color: var(--yt-border-default); color: var(--yt-text-secondary)">{{ m.transport || '—' }}</el-tag>
            <el-tag v-if="m.category" size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">{{ m.category }}</el-tag>
          </div>
          <el-button size="small" :type="m.enabled ? 'primary' : 'info'" plain class="yt-store__cta" @click="openInstall(m.name)">{{ m.enabled ? '配置' : '启用 MCP' }}</el-button>
        </el-card>
      </div>

      <!-- App Start dynamic form dialog -->
      <el-dialog v-model="appDialogVisible" :title="appDialogTarget ? `启动 — ${appDialogTarget.name}` : '启动'" width="520px" destroy-on-close>
        <el-alert v-if="appFormErrors.length" type="error" :closable="false" show-icon :title="appFormErrors.join('；')" style="margin-bottom: var(--yt-space-md)" />
        <el-form label-width="108px" size="default">
          <template v-for="fld in (appDialogTarget?.startInputs || [])" :key="fld.field">
            <el-form-item :label="fld.label" :required="!!fld.required">
              <el-input
                v-if="fld.type===1"
                v-model="appForm[fld.field] as string"
                :placeholder="fld.placeholder || `请输入${fld.label}`"
                type="textarea"
                :autosize="{minRows:2,maxRows:4}"
              />
              <el-input-number
                v-else-if="fld.type===2"
                v-model="appForm[fld.field] as number"
                :placeholder="fld.placeholder || ''"
                style="width: 100%"
                controls-position="right"
              />
              <el-switch
                v-else-if="fld.type===5"
                v-model="appForm[fld.field] as boolean"
                :active-text="fld.label"
              />
              <el-input
                v-else
                v-model="appForm[fld.field] as string"
                :placeholder="fld.placeholder || ''"
              />
            </el-form-item>
          </template>
        </el-form>
        <template #footer>
          <el-button @click="appDialogVisible=false">取消</el-button>
          <el-button type="primary" @click="submitAppStart">开始使用</el-button>
        </template>
      </el-dialog>

      <!-- empty with illustration -->
      <div v-if="currentCount===0" class="yt-store__empty">
        <div class="yt-store__empty-illustration">
          <el-icon :size="64" style="color: var(--yt-color-primary-light-5)"><Goods /></el-icon>
          <div class="yt-store__empty-ring" />
        </div>
        <div class="yt-store__empty-title">暂无匹配结果</div>
        <div class="yt-store__empty-desc">试试更换关键词或切换分类</div>
        <el-button size="small" @click="query=''; category='all'">清空筛选</el-button>
      </div>
    </template>

    <div class="yt-store__footnote">后端 GET /api/v1/plugins · /templates · /ai/skills · /ai/mcp/servers · 无需刷新 · 租户隔离</div>

    <el-dialog v-model="installDialogVisible" :title="installTarget ? `安装确认 — ${installTarget.name}` : '安装确认'" width="480px" destroy-on-close>
      <div class="yt-store__dialog-body">
        <el-alert type="info" :closable="false" show-icon title="演示环境：安装仅为模拟，不会变更生产数据" style="margin-bottom: var(--yt-space-md); background: var(--yt-color-primary-light-9); border-color: var(--yt-color-primary-light-7)" />
        <el-descriptions :column="1" border>
          <el-descriptions-item label="名称">{{ installTarget?.name }}</el-descriptions-item>
          <el-descriptions-item v-if="installTarget?.version" label="版本">{{ installTarget?.version }}</el-descriptions-item>
          <el-descriptions-item label="生效">实时生效（演示）</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="installDialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="installing" @click="confirmInstall">确认安装</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.yt-store { max-width: 1160px; margin: 0 auto; display:flex; flex-direction:column; gap: var(--yt-space-md); }
.yt-store__banner { display:flex; justify-content:space-between; gap: var(--yt-space-md); padding: var(--yt-space-lg) var(--yt-space-lg); background: linear-gradient(135deg, var(--yt-color-primary) 0%, var(--yt-color-primary-light-3) 55%, var(--yt-color-primary-light-3) 100%); border-radius: var(--yt-radius-md); color: var(--yt-bg-card); box-shadow: var(--yt-shadow-popover); flex-wrap: wrap; }
.yt-store__banner-title { display:flex; align-items:center; gap: var(--yt-space-sm); font-size: 20px; font-weight: var(--yt-font-weight-bold); }
.yt-store__banner-desc { margin-top: var(--yt-space-sm); font-size: var(--yt-font-size-caption); opacity:.92; }
.yt-store__banner-tags { display:flex; gap: var(--yt-space-sm); margin-top: var(--yt-space-sm); flex-wrap: wrap; }
.yt-store__banner-right { display:flex; gap: var(--yt-space-md); align-items:center; flex-wrap: wrap; }
.yt-store__kpi { display:flex; flex-direction:column; align-items:center; gap: 2px; min-width: 72px; padding: var(--yt-space-sm) var(--yt-space-md); background: rgba(255,255,255,0.14); border: 1px solid rgba(255,255,255,0.22); border-radius: var(--yt-radius-md); backdrop-filter: blur(6px); }
.yt-store__kpi strong { font-size: 22px; line-height: 1; } .yt-store__kpi span { font-size: var(--yt-font-size-caption); opacity:.9; }
.yt-store__header { display:flex; justify-content:space-between; align-items:center; gap: var(--yt-space-md); flex-wrap: wrap; }
.yt-store__toolbar { display:flex; align-items:center; gap: var(--yt-space-md); flex-wrap: wrap; }
.yt-store__tabs { display:flex; gap: var(--yt-space-sm); flex-wrap: wrap; }
.yt-store__count { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-store__grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(280px,1fr)); gap: var(--yt-space-md); }
.yt-store__grid--apps { grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.wf-card { display:flex; flex-direction:column; gap: var(--yt-space-sm); padding: var(--yt-space-md); border:1px solid var(--yt-border-light); border-radius: 8px; background: var(--yt-bg-card); box-shadow: var(--yt-shadow-card); cursor:pointer; transition: transform var(--yt-transition-hover), box-shadow var(--yt-transition-hover), border-color var(--yt-transition-hover); }
.wf-card:hover { transform: translateY(-4px); box-shadow: var(--yt-shadow-popover); border-color: var(--yt-color-primary-light-7); }
.wf-card__icon { width: 44px; height:44px; border-radius: 8px; display:flex; align-items:center; justify-content:center; background: linear-gradient(135deg, var(--yt-color-primary) 0%, var(--yt-color-primary-light-3) 100%); color: var(--yt-bg-card); font-size:18px; box-shadow: 0 4px 12px rgba(37,99,235,.2); }
.wf-card__title { font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); font-size: var(--yt-font-size-body); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.wf-card__desc { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); line-height: var(--yt-font-line-height-caption); min-height: 32px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow:hidden; }
.wf-card__tags { display:flex; gap: var(--yt-space-xs); align-items:center; flex-wrap:wrap; margin-top:2px; }
.wf-card__inputsTag { font-size: 11px; color: var(--yt-text-secondary); background: var(--yt-bg-page); border:1px solid var(--yt-border-light); padding: 2px 8px; border-radius: 999px; }
.wf-card__cta { margin-top: auto; border-radius: 8px; width:100%; }
.yt-store__card {
  border: 1px solid var(--yt-border-light);
  border-radius: var(--yt-radius-md);
  transition: transform var(--yt-transition-hover), box-shadow var(--yt-transition-hover), border-color var(--yt-transition-hover);
  background: var(--yt-bg-card);
}
.yt-store__card:hover {
  transform: translateY(-4px);
  box-shadow: var(--yt-shadow-popover);
  border-color: var(--yt-color-primary-light-7);
}
.yt-store__card--skeleton:hover { transform: none; box-shadow: none; }
.yt-store__card-head { display:flex; align-items:center; gap: var(--yt-space-sm); }
.yt-store__card-icon { width: 32px; height: 32px; border-radius: var(--yt-radius-sm); display:flex; align-items:center; justify-content:center; background: var(--yt-color-primary-light-9); color: var(--yt-color-primary); border: 1px solid var(--yt-color-primary-light-7); flex-shrink:0; }
.yt-store__card-icon--skill { background: var(--yt-bg-page); color: var(--yt-color-success); border-color: var(--yt-border-default); }
.yt-store__card-icon--mcp { background: var(--yt-color-primary); color: var(--yt-bg-card); border-color: var(--yt-color-primary-dark-2); }
.yt-store__card-title { font-size: var(--yt-font-size-body); color: var(--yt-text-primary); }
.yt-store__card-desc { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); margin-top: var(--yt-space-sm); min-height: 32px; line-height: var(--yt-font-line-height-caption); }
.yt-store__card-meta { display:flex; align-items:center; margin-top: var(--yt-space-sm); font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); gap: var(--yt-space-sm); flex-wrap: wrap; }
.yt-store__author { color: var(--yt-text-secondary); display:flex; align-items:center; gap: 4px; }
.yt-store__rating { color: var(--yt-color-warning); font-weight: var(--yt-font-weight-bold); }
.yt-store__installs { display:flex; align-items:center; gap: 4px; color: var(--yt-text-secondary); }
.yt-store__tags { margin-top: var(--yt-space-sm); display:flex; gap: var(--yt-space-xs); flex-wrap: wrap; }
.yt-store__cta { margin-top: var(--yt-space-sm); width: 100%; border-radius: var(--yt-radius-md); }
.yt-store__empty {
  display:flex; flex-direction:column; align-items:center; justify-content:center;
  padding: var(--yt-space-xl) var(--yt-space-md);
  background: var(--yt-bg-card);
  border: 1px dashed var(--yt-border-default);
  border-radius: var(--yt-radius-md);
  gap: var(--yt-space-sm);
}
.yt-store__empty-illustration { position: relative; width: 96px; height: 96px; display:flex; align-items:center; justify-content:center; }
.yt-store__empty-ring { position:absolute; inset:0; border-radius: 50%; border: 2px dashed var(--yt-color-primary-light-7); opacity: 0.6; }
.yt-store__empty-title { font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); }
.yt-store__empty-desc { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-store__footnote { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); text-align:center; }
.yt-store__header-card :deep(.el-card__header) { background: var(--yt-bg-card); }
</style>

