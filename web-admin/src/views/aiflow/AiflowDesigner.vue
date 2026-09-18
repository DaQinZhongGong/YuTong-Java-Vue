<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch, markRaw } from 'vue'
import { ElMessage } from 'element-plus'
import { VueFlow, Handle, Position, type Node, type Connection } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import {
  createAiflowDefinition,
  publishAiflowDefinition,
  updateAiflowDefinition,
  type AiflowDefinition,
} from '@/api/aiflow'
import {
  Connection as ConnectionIcon, Cpu, Tools, Share, Document, Reading, User, Finished, Refresh, Plus,
  Delete, View, VideoPlay, Download, Upload, CopyDocument, Setting, DataAnalysis, Cellphone, Monitor, List, Operation, Edit,
} from '@element-plus/icons-vue'

type NodeStatus = 'idle' | 'running' | 'success' | 'failed' | 'queued'
type NodeKind = 'input' | 'llm' | 'tool' | 'condition' | 'code' | 'http' | 'knowledge' | 'human' | 'output' | 'loop' | 'subflow'

const KIND_TO_BACKEND: Record<NodeKind, string> = {
  input: 'input',
  llm: 'model',
  tool: 'mcp',
  condition: 'condition',
  code: 'code',
  http: 'http',
  knowledge: 'rag',
  human: 'human',
  output: 'output',
  loop: 'loop',
  subflow: 'subflow',
}

interface NodeData {
  kind: NodeKind
  label: string
  status: NodeStatus
  config: Record<string, string>
}

interface PaletteItem { kind: NodeKind; label: string; icon: unknown; color: string; desc: string }

const PALETTE: PaletteItem[] = [
  { kind: 'input', label: '输入', icon: markRaw(ConnectionIcon), color: 'var(--yt-color-primary)', desc: '触发/变量输入' },
  { kind: 'llm', label: 'LLM', icon: markRaw(Cpu), color: '#7c3aed', desc: '大模型推理' },
  { kind: 'tool', label: '工具', icon: markRaw(Tools), color: '#0e7490', desc: '函数/MCP 工具' },
  { kind: 'condition', label: '分支', icon: markRaw(Share), color: '#d97706', desc: '条件分支' },
  { kind: 'code', label: '代码', icon: markRaw(Document), color: '#1e293b', desc: '尚未接入运行时' },
  { kind: 'http', label: 'HTTP', icon: markRaw(Cellphone), color: '#16a34a', desc: 'HTTP 请求' },
  { kind: 'knowledge', label: '知识库', icon: markRaw(Reading), color: '#4f46e5', desc: 'RAG 检索' },
  { kind: 'human', label: '人工', icon: markRaw(User), color: '#e11d48', desc: '需工作流审批' },
  { kind: 'output', label: '输出', icon: markRaw(Finished), color: '#059669', desc: '结果输出' },
  { kind: 'loop', label: '循环', icon: markRaw(Refresh), color: '#16a34a', desc: '循环遍历 + 模板输出 (支持 {{loop_index}}/{{loop_item}})' },
  { kind: 'subflow', label: '子流程', icon: markRaw(CopyDocument), color: '#7c3aed', desc: '内嵌子 DAG / 引用已发布流程 (definitionId)' },
]

function kindMeta(k: NodeKind): PaletteItem {
  return PALETTE.find((p) => p.kind === k) ?? PALETTE[0]
}

function defaultConfig(kind: NodeKind): Record<string, string> {
  switch (kind) {
    case 'llm': return { model: 'deepseek-chat', systemPrompt: '你是 YuTong AI 助手', temperature: '0.7' }
    case 'tool': return { serverCode: '', toolName: '', params: '{}' }
    case 'condition': return { condition: 'status==approved' }
    case 'code': return { language: 'javascript', code: 'return input;' }
    case 'http': return { method: 'GET', url: 'https://example.com' }
    case 'knowledge': return { kbId: '', topK: '5' }
    case 'human': return { assignee: 'admin' }
    case 'loop': return { items: '{{list}}', maxIter: '10', body: 'idx={{loop_index}} item={{loop_item}}', join: '|' }
    case 'subflow': return { definitionId: '', outputKey: '', input: '{}' }
    case 'input': return { variables: 'query' }
    case 'output': return { template: '{{n3}}' }
    default: return {}
  }
}

function toVfNode(id: string, kind: NodeKind, label: string, x: number, y: number, config?: Record<string, string>, status: NodeStatus = 'idle'): Node<NodeData> {
  return {
    id,
    type: 'yt',
    position: { x, y },
    data: { kind, label, status, config: config || defaultConfig(kind) },
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
  }
}

// 不用 ref<Node<NodeData>[]> 显式 generic，避免 Node<T>.data 在 strict mode 下嵌套展开
// （@vue-flow/core 1.45 + TS 5.6 会触发 TS2589 嵌套过深）
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const nodes = ref<any[]>([
  toVfNode('n1', 'input', '输入', 40, 140, { variables: 'query' }),
  toVfNode('n2', 'knowledge', '知识检索', 280, 140, { kbId: '', topK: '5' }),
  toVfNode('n3', 'llm', 'LLM 推理', 520, 140, { model: 'deepseek-chat', temperature: '0.7' }),
  toVfNode('n4', 'output', '输出', 760, 140, { template: '{{n3}}' }),
])
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const edges = ref<any[]>([
  { id: 'e1', source: 'n1', target: 'n2', type: 'default' },
  { id: 'e2', source: 'n2', target: 'n3', type: 'default' },
  { id: 'e3', source: 'n3', target: 'n4', type: 'default' },
])

const selectedId = ref('')
// vue-flow Node<T> generic 在 strict mode 下嵌套推断过深，绕开：nodes 已是 any，
// 实际 n.data 一定存在（toVfNode 创建时强制带 NodeData），template 用 v-if 守卫 .data
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const selectedNode = computed<any>(() =>
  nodes.value.find((n: { id: string }) => n.id === selectedId.value) ?? null
)
const dagJson = ref('')
const showJson = ref(false)
const running = ref(false)
const sseStatus = ref<'idle' | 'running' | 'success' | 'failed'>('idle')
const logs = ref<string[]>([])
const logCollapsed = ref(false)
const searchNode = ref('')
const flowName = ref('未命名 Flow')
const flowStatus = ref<'draft' | 'published'>('draft')
const idCounter = ref(10)
const persisted = ref<AiflowDefinition | null>(null)
const saving = ref(false)
const canvasEl = ref<HTMLElement | null>(null)

function syncDag() {
  dagJson.value = JSON.stringify({
    name: flowName.value,
    status: flowStatus.value,
    nodes: nodes.value.map((n) => ({
      id: n.id,
      kind: n.data!.kind,
      label: n.data!.label,
      x: n.position.x,
      y: n.position.y,
      status: n.data!.status,
      config: n.data!.config,
    })),
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    edges: edges.value.map((e: any) => ({ id: e.id, source: e.source, target: e.target, condition: e.data?.condition || '' })),
  }, null, 2)
}
watch([nodes, edges, flowName, flowStatus], syncDag, { deep: true })

function uid(): string {
  idCounter.value += 1
  return `n${idCounter.value}`
}

function addNode(kind: NodeKind, position?: { x: number; y: number }) {
  const meta = kindMeta(kind)
  const id = uid()
  const pos = position || { x: 80 + (nodes.value.length % 4) * 220, y: 80 + Math.floor(nodes.value.length / 4) * 140 }
  nodes.value = [...nodes.value, toVfNode(id, kind, meta.label, pos.x, pos.y)]
  selectedId.value = id
  ElMessage.success(`已添加 ${meta.label}`)
}

function removeNode(id: string) {
  nodes.value = nodes.value.filter((n) => n.id !== id)
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  if (selectedId.value === id) selectedId.value = ''
}

function duplicateNode(id: string) {
  const src = nodes.value.find((n) => n.id === id)
  if (!src) return
  const nid = uid()
  nodes.value = [...nodes.value, toVfNode(nid, src.data!.kind, `${src.data!.label} 副本`, src.position.x + 24, src.position.y + 24, { ...src.data!.config })]
  selectedId.value = nid
}

function wouldCreateCycle(src: string, tgt: string): boolean {
  if (src === tgt) return true
  const adj = new Map<string, string[]>()
  for (const e of edges.value) {
    if (!adj.has(e.source)) adj.set(e.source, [])
    adj.get(e.source)!.push(e.target)
  }
  const stack = [tgt]
  const seen = new Set<string>()
  while (stack.length) {
    const cur = stack.pop()!
    if (cur === src) return true
    if (seen.has(cur)) continue
    seen.add(cur)
    for (const nb of adj.get(cur) || []) {
      if (!seen.has(nb)) stack.push(nb)
    }
  }
  return false
}

function onConnect(connection: Connection) {
  if (!connection.source || !connection.target) return
  if (wouldCreateCycle(connection.source, connection.target)) {
    ElMessage.error('该连线会形成环路，已阻止')
    return
  }
  edges.value = [...edges.value, { ...connection, id: `e${Date.now().toString().slice(-6)}`, type: 'default' }]
}

function onPaletteDragStart(e: DragEvent, kind: NodeKind) {
  if (e.dataTransfer) {
    e.dataTransfer.setData('application/vueflow', kind)
    e.dataTransfer.effectAllowed = 'move'
  }
}

function onCanvasDrop(e: DragEvent) {
  const kind = (e.dataTransfer?.getData('application/vueflow') || e.dataTransfer?.getData('text/plain')) as NodeKind
  if (!kind || !PALETTE.some((p) => p.kind === kind)) return
  e.preventDefault()
  const bounds = canvasEl.value?.getBoundingClientRect()
  addNode(kind, { x: e.clientX - (bounds?.left || 0) - 80, y: e.clientY - (bounds?.top || 0) - 24 })
}

const filteredPalette = computed(() => {
  const kw = searchNode.value.trim().toLowerCase()
  if (!kw) return PALETTE
  return PALETTE.filter((p) => p.label.toLowerCase().includes(kw) || p.kind.includes(kw) || p.desc.includes(kw))
})

const stats = computed(() => ({
  nodes: nodes.value.length,
  edges: edges.value.length,
  llm: nodes.value.filter((n) => n.data!.kind === 'llm').length,
  issues: validate().length,
}))

function validate(): string[] {
  const errs: string[] = []
  if (!nodes.value.length) {
    errs.push('画布为空，请添加节点')
    return errs
  }
  if (!nodes.value.some((n) => n.data!.kind === 'input')) errs.push('缺少输入节点')
  if (!nodes.value.some((n) => n.data!.kind === 'output')) errs.push('缺少输出节点')
  const indeg = new Map<string, number>()
  const outdeg = new Map<string, number>()
  for (const n of nodes.value) {
    indeg.set(n.id, 0)
    outdeg.set(n.id, 0)
  }
  for (const e of edges.value) {
    outdeg.set(e.source, (outdeg.get(e.source) || 0) + 1)
    indeg.set(e.target, (indeg.get(e.target) || 0) + 1)
  }
  for (const n of nodes.value) {
    const ind = indeg.get(n.id) || 0
    const outd = outdeg.get(n.id) || 0
    if (ind === 0 && outd === 0 && nodes.value.length > 1) errs.push(`孤立节点：${n.data!.label}`)
    else {
      if (ind === 0 && n.data!.kind !== 'input') errs.push(`无输入：${n.data!.label}`)
      if (outd === 0 && n.data!.kind !== 'output') errs.push(`无输出：${n.data!.label}`)
    }
  }
  return errs
}

function backendDag(): string {
  return JSON.stringify({
    nodes: nodes.value.map((n) => ({
      id: n.id,
      type: KIND_TO_BACKEND[n.data!.kind as NodeKind] || n.data!.kind,
      config: n.data!.config,
      x: n.position.x,
      y: n.position.y,
      label: n.data!.label,
    })),
    edges: edges.value.map((e) => ({
      source: e.source,
      target: e.target,
      condition: (e.data as { condition?: string } | undefined)?.condition || '',
    })),
  })
}

function slugCode(name: string): string {
  const raw = name.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
  return raw || `flow-${Date.now().toString().slice(-6)}`
}

async function handleSave() {
  const errs = validate()
  if (errs.length) {
    ElMessage.error('保存已阻断 · 校验失败：' + errs.join('；'))
    return
  }
  syncDag()
  saving.value = true
  try {
    const dag = backendDag()
    if (!persisted.value) {
      persisted.value = await createAiflowDefinition({
        flowCode: slugCode(flowName.value),
        flowName: flowName.value,
        dagJson: dag,
      })
    } else {
      persisted.value = await updateAiflowDefinition(persisted.value.id, {
        ...persisted.value,
        flowName: flowName.value,
        dagJson: dag,
        version: persisted.value.version,
      })
    }
    flowStatus.value = 'draft'
    ElMessage.success('已保存草稿到 aiflow_definition')
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handlePublish() {
  const errs = validate()
  if (errs.length) {
    ElMessage.error('发布前校验失败：' + errs.join('；'))
    return
  }
  await handleSave()
  if (!persisted.value) return
  try {
    persisted.value = await publishAiflowDefinition(persisted.value.id, persisted.value.version ?? 0)
    flowStatus.value = 'published'
    ElMessage.success('已发布 Flow')
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '发布失败')
  }
}

async function runFlow() {
  running.value = true
  sseStatus.value = 'running'
  logs.value = ['[开始] 提交执行...']
  nodes.value.forEach((n) => { n.data!.status = 'queued' })
  const pushLog = (s: string) => {
    logs.value.push(`[${new Date().toLocaleTimeString()}] ${s}`)
    nextTick(() => {
      const logEl = document.querySelector('.sse-log')
      if (logEl) logEl.scrollTop = logEl.scrollHeight
    })
  }
  try {
    if (!persisted.value?.id || flowStatus.value !== 'published') {
      throw new Error('请先保存并发布 Flow 后再执行')
    }
    const base = (import.meta.env.VITE_API_BASE_URL as string) || '/api/v1'
    const token = localStorage.getItem('yutong_token') ? `Bearer ${localStorage.getItem('yutong_token')}` : ''
    const res = await fetch(`${base}/aiflow/definitions/${persisted.value.id}/run`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(token ? { Authorization: token } : {}) },
      body: JSON.stringify({ query: 'hello from designer' }),
    })
    if (!res.ok || !res.body) throw new Error(`执行失败 HTTP ${res.status}`)
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
        const dl = raw.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trimStart()).join('\n')
        if (!dl) continue
        try {
          const ev = JSON.parse(dl) as { nodeId?: string; status?: string; output?: string; error?: string }
          if (ev.nodeId) {
            const n = nodes.value.find((x) => x.id === ev.nodeId)
            if (n && ev.status) n.data!.status = (ev.status.toLowerCase() as NodeStatus)
          }
          pushLog(ev.output || ev.error || JSON.stringify(ev))
        } catch {
          pushLog(dl)
        }
      }
    }
    sseStatus.value = 'success'
    nodes.value.forEach((n) => { if (n.data!.status !== 'failed') n.data!.status = 'success' })
    pushLog('SSE 结束 · 执行成功')
  } catch (e: unknown) {
    sseStatus.value = 'failed'
    nodes.value.forEach((n) => { n.data!.status = 'failed' })
    pushLog(`失败 ${(e as { message?: string })?.message || 'error'}`)
  } finally {
    running.value = false
  }
}

function copyJson() {
  try {
    window.navigator.clipboard.writeText(dagJson.value)
    ElMessage.success('已复制')
  } catch { /* ignore */ }
}

function onNodeClick(id: string) {
  selectedId.value = id
}

// Vue Flow @node-click 给的 event 内联类型注解在 vue-tsc 下不被允许（template 表达式不能含 : type），
// 抽出 wrapper 让 vue-tsc 接受并保留类型推断
function onNodeClickEvent(e: { node: { id: string } }): void {
  onNodeClick(e.node.id)
}

onMounted(() => syncDag())
</script>

<template>
  <div class="aiflow-page">
    <div class="flow-header">
      <div class="fh-left">
        <div class="fh-title">
          <el-icon><Operation /></el-icon>
          <el-input v-model="flowName" size="small" style="width:220px; font-weight:600" placeholder="Flow 名称" />
          <el-tag :type="flowStatus==='published'?'success':'info'" size="small" effect="plain">{{ flowStatus==='published'?'已发布':'草稿' }}</el-tag>
          <el-tag :type="sseStatus==='running'?'warning': sseStatus==='success'?'success': sseStatus==='failed'?'danger':'info'" size="small">{{ sseStatus==='idle'?'就绪': sseStatus==='running'?'执行中': sseStatus==='success'?'成功':'失败' }}</el-tag>
        </div>
        <div class="fh-sub">
          <span class="sub-item"><strong>{{ stats.nodes }}</strong> 节点</span><span class="dot">·</span>
          <span class="sub-item"><strong>{{ stats.edges }}</strong> 连线</span><span class="dot">·</span>
          <span class="sub-item">LLM×{{ stats.llm }}</span>
          <span v-if="stats.issues" class="sub-warn">· {{ stats.issues }} 校验提示</span>
          <span class="sub-hint">Vue Flow 贝塞尔连线 · 拖拽节点 · 端口连线自动环路检测</span>
        </div>
      </div>
      <div class="fh-actions">
        <el-button size="small" :icon="View" @click="showJson=!showJson">{{ showJson?'隐藏 JSON':'查看 JSON' }}</el-button>
        <el-button size="small" :icon="Download" :loading="saving" @click="handleSave">保存草稿</el-button>
        <el-button size="small" type="primary" :icon="Upload" @click="handlePublish">发布</el-button>
        <el-button size="small" type="primary" :icon="VideoPlay" :loading="running" @click="runFlow">执行</el-button>
      </div>
    </div>

    <el-alert v-if="validate().length" type="error" :closable="false" show-icon style="border-radius: 8px">
      <template #title>校验未通过 · {{ validate().length }} 项</template>
      <ul style="margin:4px 0 0 18px; padding:0; line-height:1.7; font-size:12px">
        <li v-for="(m,i) in validate()" :key="i">{{ m }}</li>
      </ul>
    </el-alert>

    <div class="flow-body">
      <div class="palette">
        <div class="palette-head">
          <span class="palette-title">节点 Palette</span>
          <el-tag size="small" type="info" effect="plain">{{ PALETTE.length }} 类型</el-tag>
        </div>
        <el-input v-model="searchNode" size="small" placeholder="搜索节点" clearable style="margin-bottom:10px" />
        <div class="palette-grid">
          <div
            v-for="p in filteredPalette"
            :key="p.kind"
            class="palette-item"
            draggable="true"
            @dragstart="onPaletteDragStart($event, p.kind)"
            @click="addNode(p.kind)"
          >
            <div class="pi-icon" :style="{ background: p.color, color:'#fff' }"><el-icon><component :is="p.icon" /></el-icon></div>
            <div class="pi-meta">
              <div class="pi-label">{{ p.label }}<span class="pi-kind">{{ p.kind }}</span></div>
              <div class="pi-desc">{{ p.desc }}</div>
            </div>
            <el-icon class="pi-add"><Plus /></el-icon>
          </div>
        </div>
        <el-divider style="margin:12px 0" />
        <div class="palette-tips">
          <div class="tip-title"><el-icon><DataAnalysis /></el-icon> 使用提示</div>
          <ul>
            <li>从左侧拖到画布，或点击添加</li>
            <li>从节点右侧端口拖到目标左侧端口连线</li>
            <li>code / loop / human 运行时会明确失败，避免假成功</li>
          </ul>
        </div>
      </div>

      <div ref="canvasEl" class="canvas-wrap" @drop="onCanvasDrop" @dragover.prevent>
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :min-zoom="0.5"
          :max-zoom="1.6"
          :default-edge-options="{ type: 'default', animated: false }"
          fit-view-on-init
          @connect="onConnect"
          @node-click="onNodeClickEvent"
          @pane-click="selectedId = ''"
        >
          <Background pattern-color="#e2e8f0" :gap="20" />
          <Controls />
          <template #node-yt="{ id, data }">
            <div class="vf-node" :class="[data.kind, data.status, { selected: selectedId===id }]" @click.stop="onNodeClick(id)">
              <Handle type="target" :position="Position.Left" />
              <div class="node-head">
                <span class="node-icon" :style="{ background: kindMeta(data.kind).color }"><el-icon><component :is="kindMeta(data.kind).icon" /></el-icon></span>
                <span class="node-label">{{ data.label }}</span>
                <el-tag size="small" effect="plain">{{ data.status }}</el-tag>
              </div>
              <div class="node-meta"><span>{{ data.kind }}</span><span>{{ id }}</span></div>
              <div class="node-foot">
                <el-button size="small" link :icon="CopyDocument" @click.stop="duplicateNode(id)" />
                <el-button size="small" link type="danger" :icon="Delete" @click.stop="removeNode(id)" />
              </div>
              <Handle type="source" :position="Position.Right" />
            </div>
          </template>
        </VueFlow>
        <div v-if="!nodes.length" class="canvas-empty">
          <el-icon :size="40"><List /></el-icon>
          <div>拖拽左侧节点到此处开始编排</div>
        </div>
      </div>

      <div class="props-panel" :class="{ empty: !selectedNode }">
        <div v-if="selectedNode" class="props-inner">
          <div class="props-head">
            <span class="props-title"><el-icon><Setting /></el-icon> 属性 · {{ selectedNode.data.label }}</span>
          </div>
          <el-form label-width="88px" size="small" label-position="top" style="margin-top:12px">
            <el-form-item label="节点名称">
              <el-input v-model="selectedNode.data.label" />
            </el-form-item>
            <el-form-item v-for="(_v,k) in selectedNode.data.config" :key="String(k)" :label="String(k)">
              <el-input v-model="selectedNode.data.config[String(k)]" />
            </el-form-item>
            <div class="props-actions">
              <el-button size="small" :icon="CopyDocument" @click="duplicateNode(selectedNode.id)">复制</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="removeNode(selectedNode.id)">删除</el-button>
            </div>
          </el-form>
        </div>
        <div v-else class="props-empty">
          <el-icon :size="36"><Edit /></el-icon>
          <div class="pe-title">未选中节点</div>
          <div class="pe-sub">点击画布节点编辑属性；从右侧端口拖出贝塞尔连线</div>
        </div>
      </div>
    </div>

    <el-card v-if="showJson" shadow="never" style="margin-top:12px">
      <el-input v-model="dagJson" type="textarea" :autosize="{minRows:8, maxRows:16}" readonly />
      <el-button size="small" style="margin-top:8px" @click="copyJson">复制 JSON</el-button>
    </el-card>

    <el-card shadow="never" style="margin-top:12px">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center">
          <span style="font-weight:600"><el-icon><Monitor /></el-icon> SSE 执行日志</span>
          <div style="display:flex; gap:8px">
            <el-tag size="small">{{ sseStatus }}</el-tag>
            <el-button size="small" @click="logCollapsed=!logCollapsed">{{ logCollapsed?'展开':'折叠' }}</el-button>
            <el-button size="small" @click="logs=[]">清空</el-button>
          </div>
        </div>
      </template>
      <div v-show="!logCollapsed" class="sse-log">
        <div v-for="(l,i) in logs" :key="i" class="log-line">{{ l }}</div>
        <div v-if="!logs.length" class="log-empty">等待执行… POST /api/v1/aiflow/definitions/{id}/run</div>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.aiflow-page { display:flex; flex-direction:column; gap:12px; padding:4px 2px 20px; background: var(--yt-bg-page, #f6f8fb); min-height:100% }
.flow-header { display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:14px 16px }
.fh-title { display:flex; align-items:center; gap:10px; font-weight:600 }
.fh-sub { display:flex; align-items:center; gap:8px; font-size:12px; color: var(--yt-text-secondary); flex-wrap:wrap; margin-top:6px }
.fh-actions { display:flex; align-items:center; gap:8px; flex-wrap:wrap }
.dot { color: var(--yt-text-disabled) }
.sub-warn { color: var(--yt-color-warning) }
.sub-hint { margin-left:8px; font-size:11px }
.flow-body { display:flex; gap:12px; min-height:560px }
.palette { width:270px; min-width:250px; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:12px }
.palette-head { display:flex; justify-content:space-between; margin-bottom:10px }
.palette-title { font-weight:600; font-size:13px }
.palette-grid { display:flex; flex-direction:column; gap:8px; max-height:420px; overflow:auto }
.palette-item { display:flex; align-items:center; gap:10px; border:1px solid var(--yt-border-light); border-radius:8px; padding:8px 10px; cursor:grab }
.pi-icon { width:32px; height:32px; border-radius:8px; display:flex; align-items:center; justify-content:center }
.pi-label { font-size:13px; font-weight:600 }
.pi-kind { font-size:10px; color: var(--yt-text-disabled); margin-left:6px }
.pi-desc { font-size:11px; color: var(--yt-text-secondary) }
.palette-tips { font-size:12px; color: var(--yt-text-secondary) }
.canvas-wrap { flex:1; min-width:420px; height:560px; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); position:relative; overflow:hidden }
.canvas-empty { position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; color: var(--yt-text-secondary); pointer-events:none }
.vf-node { width:168px; background:#fff; border:1.5px solid var(--yt-border-default); border-radius:10px; padding:10px }
.vf-node.selected { border-color: var(--yt-color-primary) }
.node-head { display:flex; align-items:center; gap:6px }
.node-icon { width:22px; height:22px; border-radius:6px; color:#fff; display:flex; align-items:center; justify-content:center }
.node-label { font-weight:600; font-size:13px; flex:1 }
.node-meta { display:flex; justify-content:space-between; font-size:11px; color: var(--yt-text-secondary); margin-top:6px }
.node-foot { display:flex; justify-content:flex-end }
.props-panel { width:280px; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:12px }
.props-empty, .props-inner { min-height:200px }
.props-empty { display:flex; flex-direction:column; align-items:center; justify-content:center; color: var(--yt-text-secondary); gap:8px; text-align:center }
.sse-log { max-height:180px; overflow:auto; font-family: ui-monospace, monospace; font-size:12px; background: var(--yt-bg-page, #f8fafc); padding:8px; border-radius:8px }
.log-line { line-height:1.6 }
.log-empty { color: var(--yt-text-secondary) }
</style>