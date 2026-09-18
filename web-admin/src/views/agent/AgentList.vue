<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Search, Refresh, Plus, Cpu, Share, Tools, DataAnalysis,
  VideoPlay, View, Setting, Clock, ChatDotRound, Histogram,
  List, Delete
} from '@element-plus/icons-vue'
import client from '@/api/request'

interface AgentItem {
  id: string
  name: string
  description?: string
  status?: string
  model?: string
  tools?: string[]
  supervisor?: string
  memory?: string
  createdTime?: string
  agentType?: string
  version?: number
  memoryConfigJson?: string
}
interface TraceStep { step: number; thought: string; action: string; observation: string; latencyMs?: number; tool?: string }
interface MemoryConfig { shortTerm: string; longTerm: string; vectorKbId?: string; windowSize: number; summaryPrompt?: string }

const loading = ref(false)
const list = ref<AgentItem[]>([])
const keyword = ref('')
const statusFilter = ref<string>('all')
const currentPage = ref(1)
const pageSize = ref(9)

// ReAct
const running = ref(false)
const input = ref('帮我查询上月订单统计')
const trace = ref<TraceStep[]>([])
const finalAnswer = ref('')
const activeAgentId = ref<string>('')

// Supervisor 协作示意图（不冒充真实编排结果）
const supervisorMode = ref(false)
const supervisorGraph = ref<{ nodes: {id:string; label:string; role:string; x:number; y:number}[]; edges: {from:string;to:string; label?:string}[] }>({
  nodes: [
    { id:'sup', label:'Supervisor', role:'协调者', x: 160, y: 40 },
    { id:'a1', label:'数据分析', role:'Analyst', x: 40, y: 140 },
    { id:'a2', label:'客服助手', role:'Support', x: 160, y: 140 },
    { id:'a3', label:'运维助手', role:'Ops', x: 280, y: 140 },
  ],
  edges: [
    { from:'sup', to:'a1', label:'委派' },
    { from:'sup', to:'a2' },
    { from:'sup', to:'a3' },
    { from:'a1', to:'sup', label:'汇总' },
  ]
})

// Memory drawer
const memoryDrawer = ref(false)
const memoryAgent = ref<AgentItem|null>(null)
const memoryForm = ref<MemoryConfig>({ shortTerm:'buffer', longTerm:'vector', windowSize: 10, vectorKbId:'', summaryPrompt:'总结最近对话要点' })

// Run audit drawer
const traceDrawer = ref(false)
const traceDetail = ref<TraceStep[]|null>(null)
const runHistory = ref<{ id:string; agentName:string; task:string; status:string; durationMs:number; createdTime:string; steps: TraceStep[] }[]>([])

const filtered = computed(()=>{
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter(a=>{
    if(kw && !(a.name.toLowerCase().includes(kw) || (a.description||'').toLowerCase().includes(kw) || (a.model||'').toLowerCase().includes(kw))) return false
    if(statusFilter.value!=='all' && a.status !== statusFilter.value) return false
    return true
  })
})
const paged = computed(()=>{
  const start=(currentPage.value-1)*pageSize.value
  return filtered.value.slice(start, start+pageSize.value)
})
const stats = computed(()=>({
  total: list.value.length,
  active: list.value.filter(a=>a.status==='PUBLISHED').length,
  tools: new Set(list.value.flatMap(a=>a.tools||[])).size
}))

function parseJsonArray(raw?: string): string[] {
  if (!raw) return []
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v.map(String) : []
  } catch {
    return raw.split(',').map((s) => s.trim()).filter(Boolean)
  }
}

function mapAgent(raw: Record<string, unknown>): AgentItem {
  return {
    id: String(raw.id || ''),
    name: String(raw.agentName || raw.agentCode || ''),
    description: String(raw.systemPrompt || ''),
    status: String(raw.status || 'DRAFT'),
    model: String(raw.agentType || 'react'),
    tools: parseJsonArray(typeof raw.toolIds === 'string' ? raw.toolIds : ''),
    supervisor: String(raw.agentType || '') === 'supervisor' ? 'supervisor' : '',
    memory: typeof raw.memoryConfigJson === 'string' ? raw.memoryConfigJson : '',
    createdTime: String(raw.createdTime || ''),
    agentType: String(raw.agentType || 'react'),
    version: typeof raw.version === 'number' ? raw.version : 0,
    memoryConfigJson: typeof raw.memoryConfigJson === 'string' ? raw.memoryConfigJson : '',
  }
}

async function load() {
  loading.value = true
  try {
    const data = await client.get('/agents', { params: { page: 1, size: 50 } }) as { records?: Record<string, unknown>[] }
    const arr = Array.isArray(data) ? data : (data?.records || [])
    list.value = (arr as Record<string, unknown>[]).map(mapAgent)
    currentPage.value = 1
  } catch (e: unknown) {
    list.value = []
    ElMessage.error((e as { message?: string })?.message || '加载 Agent 失败')
  } finally {
    loading.value = false
  }
}

function openMemory(a: AgentItem){
  memoryAgent.value=a
  let windowSize = 10
  try {
    const cfg = a.memoryConfigJson ? JSON.parse(a.memoryConfigJson) as { windowSize?: number } : {}
    if (cfg.windowSize) windowSize = cfg.windowSize
  } catch { /* ignore */ }
  memoryForm.value = { shortTerm: 'buffer', longTerm: 'none', windowSize, vectorKbId: '', summaryPrompt: '总结最近对话要点，保留关键实体' }
  memoryDrawer.value = true
}
async function saveMemory() {
  if (!memoryAgent.value?.id) return
  try {
    await client.put(`/agents/${memoryAgent.value.id}`, {
      id: memoryAgent.value.id,
      agentName: memoryAgent.value.name,
      agentType: memoryAgent.value.agentType || 'react',
      version: memoryAgent.value.version,
      memoryConfigJson: JSON.stringify({
        windowSize: memoryForm.value.windowSize,
        summarizeThreshold: 20,
        summaryPrompt: memoryForm.value.summaryPrompt,
      }),
    })
    ElMessage.success(`已保存 ${memoryAgent.value.name} 的 Memory 配置`)
    memoryDrawer.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '保存失败')
  }
}

async function runReAct(agent: AgentItem) {
  if (!input.value.trim()) { ElMessage.warning('请输入任务'); return }
  running.value = true
  trace.value = []
  finalAnswer.value = ''
  activeAgentId.value = agent.id
  const q = input.value.trim()
  try {
    const base = (import.meta.env.VITE_API_BASE_URL as string) || '/api/v1'
    const token = localStorage.getItem('yutong_token') ? `Bearer ${localStorage.getItem('yutong_token')}` : ''
    const url = `${base}/agents/${agent.id}/run`
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        ...(token ? { Authorization: token } : {}),
      },
      body: JSON.stringify({ query: q }),
    })
    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)
    const reader = res.body.getReader()
    const dec = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      let idx: number
      while ((idx = buf.indexOf('\n\n')) !== -1) {
        const raw = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        const lines = raw.split('\n')
        let eventName = 'message'
        const dataLines: string[] = []
        for (const l of lines) {
          if (l.startsWith('event:')) eventName = l.slice(6).trim()
          if (l.startsWith('data:')) dataLines.push(l.slice(5).trimStart())
        }
        const dl = dataLines.join('\n')
        if (!dl) continue
        try {
          const ev = JSON.parse(dl) as { step?: number; type?: string; content?: string; observation?: string; tool?: string; error?: string; status?: string }
          if (eventName === 'error' || ev.error) {
            ElMessage.error(ev.error || 'Agent 执行失败')
            continue
          }
          if (eventName === 'done') {
            finalAnswer.value = ev.status === 'SUCCESS' ? (finalAnswer.value || '执行完成') : (ev.error || finalAnswer.value)
            continue
          }
          const type = ev.type || 'Observation'
          trace.value.push({
            step: ev.step || trace.value.length + 1,
            thought: type === 'Thought' ? (ev.content || '') : '',
            action: type,
            observation: ev.observation || (type !== 'Thought' ? (ev.content || '') : ''),
            tool: ev.tool,
          })
          if (type === 'Observation' && (ev.observation || ev.content || '').includes('Final Answer')) {
            finalAnswer.value = ev.observation || ev.content || ''
          }
        } catch { /* ignore malformed chunk */ }
      }
    }
    await loadRuns(agent)
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || 'Agent 执行失败')
  } finally { running.value = false }
}

async function loadRuns(agent: AgentItem) {
  try {
    const data = await client.get(`/agents/${agent.id}/runs`, { params: { page: 1, size: 10 } }) as { records?: Record<string, unknown>[] }
    const arr = data?.records || []
    runHistory.value = arr.map((r) => ({
      id: String(r.id || ''),
      agentName: agent.name,
      task: String(r.inputJson || ''),
      status: String(r.status || ''),
      durationMs: 0,
      createdTime: String(r.createdTime || ''),
      steps: [],
    }))
  } catch {
    /* keep previous */
  }
}

function viewTrace(h: typeof runHistory.value[number]){
  traceDetail.value = h.steps.length? h.steps : trace.value
  traceDrawer.value = true
}

async function createDraft() {
  const code = `agent-${Date.now().toString(36)}`
  try {
    await client.post('/agents', {
      agentCode: code,
      agentName: '新智能体',
      agentType: 'react',
      systemPrompt: '你是 YuTong ReAct 智能体。',
      toolIds: '[]',
      skillIds: '[]',
      mcpServerIds: '[]',
    })
    ElMessage.success('已创建草稿，可执行 ReAct')
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '创建失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="agent-page">
    <!-- Header -->
    <div class="page-head">
      <div class="head-left">
        <h2 class="head-title"><el-icon><Cpu /></el-icon> Agent 列表 · ReAct · Supervisor</h2>
        <div class="head-sub">
          <span class="sub-stat"><strong>{{ stats.total }}</strong> Agent</span><span class="dot">·</span>
          <span class="sub-stat"><strong>{{ stats.active }}</strong> 已发布</span><span class="dot">·</span>
          <span class="sub-stat">工具 {{ stats.tools }} 个</span>
          <el-tag v-if="supervisorMode" size="small" type="warning" effect="plain">Supervisor 协作图</el-tag>
        </div>
      </div>
      <div class="head-actions">
        <el-button size="small" :type="supervisorMode?'warning':'default'" :icon="Share" @click="supervisorMode=!supervisorMode">{{ supervisorMode?'隐藏协作图':'Supervisor 协作图' }}</el-button>
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- Filters -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索 Agent / 模型 / 描述" clearable style="max-width:360px" @input="currentPage=1" />
        <el-select v-model="statusFilter" style="width:140px" @change="currentPage=1">
          <el-option label="全部状态" value="all" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="草稿" value="DRAFT" />
        </el-select>
        <span class="filter-count">已筛选 {{ filtered.length }} / {{ list.length }}</span>
        <div style="margin-left:auto; display:flex; gap:8px">
          <el-button size="small" :icon="Plus" type="primary" plain @click="createDraft">新建草稿</el-button>
        </div>
      </div>
    </el-card>

    <!-- Supervisor graph -->
    <el-card v-if="supervisorMode" shadow="never" class="graph-card">
      <template #header><div style="display:flex; justify-content:space-between; align-items:center"><span style="font-weight:600"><el-icon><Share /></el-icon> Supervisor 协作图</span><span style="font-size:12px; color: var(--yt-text-secondary)">中心协调 · 多 Agent 委派 · 汇总</span></div></template>
      <div class="graph-wrap">
        <svg width="520" height="200" style="background:#f8fafc; border:1px dashed var(--yt-border-default); border-radius:12px">
          <line v-for="e in supervisorGraph.edges" :key="e.from+e.to"
            :x1="supervisorGraph.nodes.find(n=>n.id===e.from)!.x + 50" :y1="supervisorGraph.nodes.find(n=>n.id===e.from)!.y + 22"
            :x2="supervisorGraph.nodes.find(n=>n.id===e.to)!.x + 50" :y2="supervisorGraph.nodes.find(n=>n.id===e.to)!.y + 22"
            stroke="#2563eb" stroke-width="1.8" opacity="0.55" marker-end="url(#agt-arrow)" />
          <defs><marker id="agt-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M 0 0 L 10 5 L 0 10 z" fill="#2563eb" /></marker></defs>
        </svg>
        <div class="graph-nodes">
          <div v-for="n in supervisorGraph.nodes" :key="n.id" class="g-node" :class="{ sup: n.id==='sup' }" :style="{ left: n.x+'px', top: n.y+'px' }">
            <div class="g-label">{{ n.label }}</div><div class="g-role">{{ n.role }}</div>
          </div>
        </div>
        <div class="graph-legend">
          <span><i class="dot-sup" /> Supervisor 统一调度</span>
          <span><i class="dot-agt" /> 子 Agent 并行执行</span>
          <span>支持 ReAct 循环 · 工具调用 · 汇总</span>
        </div>
      </div>
    </el-card>

    <!-- Cards -->
    <div v-loading="loading" class="agent-grid">
      <div v-for="a in paged" :key="a.id" class="agent-card" :class="{ active: activeAgentId===a.id }">
        <div class="card-top">
          <div class="agent-icon"><el-icon><Cpu /></el-icon></div>
          <div class="agent-meta">
            <div class="agent-name" :title="a.name">{{ a.name }}</div>
            <div class="agent-model">{{ a.model }} · {{ a.memory || 'buffer' }}</div>
          </div>
          <el-tag :type="a.status==='PUBLISHED'?'success': 'info'" size="small" effect="plain">{{ a.status }}</el-tag>
        </div>
        <div class="agent-desc">{{ a.description }}</div>
        <div class="agent-tools">
          <el-tag v-for="t in (a.tools||[])" :key="t" size="small" effect="plain" class="tool-chip"><el-icon><Tools /></el-icon>{{ t }}</el-tag>
        </div>
        <div class="agent-foot">
          <span class="foot-time"><el-icon><Clock /></el-icon>{{ a.createdTime || '-' }}</span>
          <span v-if="a.supervisor" class="foot-sup"><el-icon><Share /></el-icon>{{ a.supervisor }}</span>
        </div>
        <div class="card-actions">
          <el-button size="small" type="primary" :icon="VideoPlay" :loading="running && activeAgentId===a.id" @click="runReAct(a)">ReAct 执行</el-button>
          <el-button size="small" :icon="Setting" @click="openMemory(a)">Memory</el-button>
          <el-button size="small" :icon="View" @click="loadRuns(a)">运行记录</el-button>
        </div>
      </div>

      <div v-if="!loading && !paged.length" class="empty-state">
        <div class="empty-illus">◈</div>
        <div class="empty-title">暂无匹配的 Agent</div>
        <div class="empty-desc">尝试调整关键字或状态筛选</div>
        <el-button size="small" @click="keyword=''; statusFilter='all'">清除筛选</el-button>
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

    <!-- Run input + ReAct trace -->
    <el-card shadow="never" style="margin-top:16px">
      <template #header><div style="display:flex; justify-content:space-between; align-items:center"><span style="font-weight:600"><el-icon><ChatDotRound /></el-icon> ReAct 执行</span><span style="font-size:12px; color: var(--yt-text-secondary)">POST /agents/{id}/run · 真实 LLM SSE · 失败不模拟</span></div></template>
      <div style="display:flex; gap:8px">
        <el-input v-model="input" placeholder="输入任务，例如：统计上月订单 / 查询工单 KB-301" clearable @keyup.enter="() => list[0] && runReAct(list[0])" />
        <el-button type="primary" :loading="running" :disabled="!list.length" :icon="VideoPlay" @click="() => list[0] && runReAct(list[0])">触发首个 Agent</el-button>
        <el-button :icon="Delete" @click="trace=[]; finalAnswer=''">清空轨迹</el-button>
      </div>

      <div v-if="trace.length || finalAnswer" style="margin-top:16px">
        <div style="font-weight:600; margin-bottom:10px; display:flex; align-items:center; gap:8px"><el-icon><Histogram /></el-icon> ReAct 轨迹 · {{ trace.length }} 步 <el-tag v-if="running" size="small" type="warning" effect="plain">执行中</el-tag></div>
        <el-timeline>
          <el-timeline-item v-for="t in trace" :key="t.step" :hollow="true" :type="t.step===trace.length && !finalAnswer?'primary': t.action==='Answer'?'success':'info'">
            <div class="trace-card">
              <div class="trace-head">
                <span class="trace-step">Step {{ t.step }}</span>
                <el-tag size="small" :type="t.tool?'primary':'info'" effect="plain">{{ t.action }}</el-tag>
                <span v-if="t.latencyMs" class="trace-lat">{{ t.latencyMs }}ms</span>
                <span v-if="t.tool" class="trace-tool"><el-icon><Tools /></el-icon>{{ t.tool }}</span>
              </div>
              <div class="trace-thought"><span class="lbl">Thought:</span> {{ t.thought }}</div>
              <div class="trace-obs"><span class="lbl">Observation:</span> {{ t.observation }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-alert v-if="finalAnswer" :title="finalAnswer" type="success" :closable="false" show-icon style="margin-top:8px" />
      </div>
      <div v-else class="yt-empty">
        <el-icon :size="36" style="color: var(--yt-text-disabled)"><DataAnalysis /></el-icon>
        <div style="margin-top:8px; font-size:13px; color: var(--yt-text-secondary)">执行后在此查看 Thought → Action → Observation 轨迹</div>
      </div>
      <div style="font-size:11px; color: var(--yt-text-secondary); text-align:center; margin-top:10px">后端 GET /api/v1/agents · POST /api/v1/agents/{id}/run (SSE)</div>
    </el-card>

    <!-- Run audit history -->
    <el-card shadow="never" style="margin-top:12px">
      <template #header><div style="display:flex; justify-content:space-between; align-items:center"><span style="font-weight:600"><el-icon><List /></el-icon> Run 审计 · 历史执行</span><el-button size="small" :icon="Refresh" :disabled="!list[0]" @click="list[0] && loadRuns(list[0])">刷新</el-button></div></template>
      <el-table :data="runHistory" size="small" border stripe>
        <el-table-column prop="id" label="Run ID" width="100" />
        <el-table-column prop="agentName" label="Agent" width="140" />
        <el-table-column prop="task" label="任务" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100"><template #default="{row}"><el-tag :type="row.status==='success'?'success':'danger'" size="small">{{ row.status }}</el-tag></template></el-table-column>
        <el-table-column prop="durationMs" label="耗时" width="100"><template #default="{row}">{{ row.durationMs }}ms</template></el-table-column>
        <el-table-column prop="createdTime" label="时间" width="160" />
        <el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button size="small" link :icon="View" @click="viewTrace(row as any)">Trace</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <!-- Memory drawer -->
    <el-drawer v-model="memoryDrawer" :title="memoryAgent ? `Memory 配置 · ${memoryAgent.name}` : 'Memory 配置'" size="520px" direction="rtl">
      <div style="display:flex; flex-direction:column; gap:16px">
        <el-alert type="info" :closable="false" title="Memory：短期缓冲 + 长期向量，支持窗口与摘要策略" />
        <el-form label-width="96px" size="small">
          <el-form-item label="短期记忆">
            <el-select v-model="memoryForm.shortTerm" style="width:100%">
              <el-option label="Buffer 窗口" value="buffer" />
              <el-option label="Summary 摘要" value="summary" />
            </el-select>
          </el-form-item>
          <el-form-item label="长期记忆">
            <el-select v-model="memoryForm.longTerm" style="width:100%">
              <el-option label="无" value="none" />
              <el-option label="Vector 向量" value="vector" />
              <el-option label="Vector + KG" value="vector_kg" />
            </el-select>
          </el-form-item>
          <el-form-item label="窗口大小"><el-input-number v-model="memoryForm.windowSize" :min="2" :max="50" style="width:100%" /></el-form-item>
          <el-form-item label="向量库"><el-input v-model="memoryForm.vectorKbId" placeholder="可选 kbId" /></el-form-item>
          <el-form-item label="摘要提示词"><el-input v-model="memoryForm.summaryPrompt" type="textarea" :autosize="{minRows:3, maxRows:5}" /></el-form-item>
        </el-form>
        <div style="display:flex; justify-content:flex-end; gap:8px">
          <el-button @click="memoryDrawer=false">取消</el-button>
          <el-button type="primary" @click="saveMemory">保存</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- Trace drawer -->
    <el-drawer v-model="traceDrawer" title="Run Trace 详情" size="560px" direction="rtl">
      <el-timeline v-if="traceDetail && traceDetail.length">
        <el-timeline-item v-for="t in traceDetail" :key="t.step" :hollow="true" type="primary">
          <div style="font-weight:600; font-size:13px">Step {{ t.step }} · {{ t.action }}</div>
          <div style="font-size:12px; color: var(--yt-text-secondary); margin-top:4px">Thought: {{ t.thought }}</div>
          <div style="font-size:12px; color: var(--yt-text-secondary)">Observation: {{ t.observation }}</div>
          <div v-if="t.latencyMs" style="font-size:11px; color: var(--yt-text-disabled); margin-top:4px">{{ t.latencyMs }}ms</div>
        </el-timeline-item>
      </el-timeline>
      <div v-else style="font-size:13px; color: var(--yt-text-secondary); text-align:center; padding:40px 0">暂无 Trace 数据，先执行一次 ReAct</div>
    </el-drawer>
  </div>
</template>

<style scoped>
.agent-page { display:flex; flex-direction:column; gap:14px; padding:4px 2px 20px; background: var(--yt-bg-page, #f6f8fb); min-height:100% }
.page-head { display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:16px; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)) }
.head-title { margin:0; font-size:18px; font-weight:600; display:flex; gap:8px; align-items:center }
.head-sub { display:flex; align-items:center; gap:8px; font-size:12px; color: var(--yt-text-secondary); margin-top:6px; flex-wrap:wrap }
.head-sub strong { color: var(--yt-text-primary) }
.dot { color: var(--yt-text-disabled) }
.head-actions { display:flex; align-items:center; gap:8px }
.filter-card :deep(.el-card__body) { padding:12px 16px }
.filter-row { display:flex; align-items:center; gap:12px; flex-wrap:wrap }
.filter-count { font-size:12px; color: var(--yt-text-secondary) }

.graph-card :deep(.el-card__body) { padding:12px 16px }
.graph-wrap { position:relative; display:flex; gap:16px; align-items:flex-start; flex-wrap:wrap }
.graph-nodes { position:absolute; left:0; top:0; width:520px; height:200px; pointer-events:none }
.g-node { position:absolute; width:100px; text-align:center; background:#fff; border:1px solid var(--yt-border-default); border-radius:10px; padding:8px; box-shadow: 0 1px 6px rgba(0,0,0,.06) }
.g-node.sup { border-color: var(--yt-color-warning); background:#fffbeb; }
.g-label { font-size:13px; font-weight:600 }
.g-role { font-size:11px; color: var(--yt-text-secondary) }
.graph-legend { display:flex; gap:12px; font-size:11px; color: var(--yt-text-secondary); align-items:center; flex-wrap:wrap }
.dot-sup { display:inline-block; width:8px; height:8px; border-radius:50%; background: var(--yt-color-warning); margin-right:4px }
.dot-agt { display:inline-block; width:8px; height:8px; border-radius:50%; background: var(--yt-color-primary); margin-right:4px }

.agent-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(300px,1fr)); gap:16px }
.agent-card { background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:14px; display:flex; flex-direction:column; gap:10px; transition: all 140ms ease; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)) }
.agent-card:hover { border-color: var(--yt-color-primary-light-5); box-shadow: 0 4px 16px rgba(37,99,235,.08); transform: translateY(-1px) }
.agent-card.active { border-color: var(--yt-color-primary); box-shadow: 0 0 0 3px rgba(37,99,235,.12) }
.card-top { display:flex; gap:10px; align-items:center }
.agent-icon { width:36px; height:36px; border-radius:10px; background: var(--yt-color-primary); color:#fff; display:flex; align-items:center; justify-content:center; font-size:16px; flex-shrink:0 }
.agent-meta { flex:1; min-width:0 }
.agent-name { font-size:14px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.agent-model { font-size:11px; color: var(--yt-text-secondary); font-family: ui-monospace, monospace }
.agent-desc { font-size:12px; color: var(--yt-text-secondary); min-height:28px; line-height:1.5 }
.agent-tools { display:flex; gap:6px; flex-wrap:wrap }
.tool-chip { font-size:11px }
.agent-foot { display:flex; justify-content:space-between; font-size:11px; color: var(--yt-text-secondary); border-top:1px dashed var(--yt-border-light); padding-top:8px }
.foot-sup { display:flex; gap:4px; align-items:center; color: var(--yt-color-warning) }
.card-actions { display:flex; gap:8px; margin-top:2px }
.card-actions .el-button { flex:1 }

.empty-state { grid-column: 1 / -1; text-align:center; padding:36px 0; background: var(--yt-bg-card); border:1px dashed var(--yt-border-default); border-radius:12px }
.empty-illus { font-size:32px; color: var(--yt-text-disabled) }
.empty-title { font-weight:600; margin-top:8px }
.empty-desc { font-size:12px; color: var(--yt-text-secondary); margin:6px 0 12px }

.pager-wrap { display:flex; justify-content:center; margin-top:4px }
.trace-card { background:#f8fafc; border:1px solid var(--yt-border-light); border-radius:8px; padding:10px 12px }
.trace-head { display:flex; align-items:center; gap:8px; flex-wrap:wrap }
.trace-step { font-weight:600; font-size:13px }
.trace-lat { font-size:11px; color: var(--yt-text-disabled); font-family: ui-monospace, monospace }
.trace-tool { font-size:11px; color: var(--yt-text-secondary); display:flex; gap:4px; align-items:center }
.trace-thought, .trace-obs { font-size:12px; color: var(--yt-text-secondary); margin-top:6px; line-height:1.6 }
.lbl { font-weight:600; color: var(--yt-text-primary); margin-right:4px }
.yt-empty { text-align:center; padding:20px 0; color: var(--yt-text-secondary); font-size:12px }
</style>
