<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, EditPen, ChatDotRound, Close, Refresh, Brush, User, Service, Star, StarFilled, RefreshRight, Top, Bottom } from '@element-plus/icons-vue'
import { Sender, Bubble, Thinking } from 'vue-element-plus-x'
import client, { streamChat } from '@/api/client'
import FilesSelect from '@/components/chat/FilesSelect.vue'
import DeepThinkingToggle from '@/components/chat/DeepThinkingToggle.vue'
import ToolCallTimeline from '@/components/chat/ToolCallTimeline.vue'
import AgentSelect from '@/components/chat/AgentSelect.vue'
import ModelSelect from '@/components/chat/ModelSelect.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import type { ChatAttachment, ChatModelSelection } from '@/components/chat/types'

type ThinkingStatus = 'start' | 'thinking' | 'end' | 'error'

interface Conv {
  id: string
  title?: string
  scenario?: string
  lastMessageTime?: string
  createdTime?: string
  status?: string
  summary?: string
  pinned?: boolean
}
interface PageResult<T> { records: T[]; total: number; page: number; size: number }
interface ToolCall { id: string; name: string; status: 'pending'|'running'|'success'|'failed'; summary?: string; elapsedMs?: number }
interface Msg {
  id?: string
  /** 父消息 ID (V052 分支链, 后端 parentMessageId) */
  parentMessageId?: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  citations?: { title?: string; source?: string; score?: number; chunkTextPreview?: string; docTitle?: string; [k: string]: unknown }[]
  tokens?: number
  latencyMs?: number
  /** 消息反馈 LIKE/DISLIKE (P2-C, 后端 ai_message.feedback) */
  feedback?: string
  toolCalls?: ToolCall[]
  reasoning?: string
  thinkingStatus?: ThinkingStatus
}

const suggestions = ['如何配置数据源？', '低代码页面如何绑定模型？', '怎样创建审批流程？', '如何接入 MCP 工具？']

const conversations = ref<Conv[]>([])
const convTotal = ref(0)
const convPage = ref(1)
const convSize = 20
const convLoading = ref(false)
const activeId = ref<string | undefined>(undefined)
const drawerVisible = ref(false)
const isMobile = ref(false)

function checkMobile() { isMobile.value = window.innerWidth < 860 }
if (typeof window !== 'undefined') {
  checkMobile()
  window.addEventListener('resize', checkMobile)
}

const input = ref('')
const sending = ref(false)
// P2-G: 深度思考开关状态由 DeepThinkingToggle 组件持有 (localStorage 持久化在其内部)
const enableThinking = ref<boolean>(false)
// P2-G: 附件列表由 FilesSelect 组件 v-model 持有
const attachments = ref<ChatAttachment[]>([])
const filesSelectRef = ref<InstanceType<typeof FilesSelect> | null>(null)
// P2-G: Agent/模型选择由 AgentSelect/ModelSelect 组件持有, 透传到 /ai/chat
const selectedAgentId = ref('')
const selectedModel = ref<ChatModelSelection>({ providerCode: '', providerName: '', modelCode: '' })
const senderRef = ref<InstanceType<typeof Sender> | null>(null)
function triggerFileSelect() { filesSelectRef.value?.open() }
const messages = ref<Msg[]>([{ role: 'assistant', content: '你好，我是 YuTong 助手。\n\n我可以帮你解答平台配置、低代码、流程与 AI 能力的各类问题。左侧可管理会话历史，试试下方快捷问题，或直接输入你的需求。' }])

// V052 P2-C 分支导航: 按 parentMessageId 组装树, 显示 active path (线性历史与原来逐条显示完全一致)。
// branchSel[parentKey] = 选中的子消息 key, 缺省取最新子; 流式消息恒置末尾。
// 键模型: 有服务端 id 用 id; 无 id (欢迎语/实时消息) 用本地序号键并按到达顺序挂链,
// 与后端语义一致 (新消息默认续尾)。
const branchSel = ref<Record<string, string>>({})

interface BranchNode {
  msg: Msg
  key: string
  parentKey: string
}

const branchNodes = computed<BranchNode[]>(() => {
  const list = messages.value
  return list.map((m, i) => {
    const key = m.id || `__local_${i}`
    const parentKey =
      (m as Msg & { parentMessageId?: string }).parentMessageId ||
      (i > 0 ? list[i - 1].id || `__local_${i - 1}` : '__root__')
    return { msg: m, key, parentKey }
  })
})

const childrenOf = computed(() => {
  const map = new Map<string, BranchNode[]>()
  for (const n of branchNodes.value) {
    if (n.msg.streaming) continue
    if (!map.has(n.parentKey)) map.set(n.parentKey, [])
    map.get(n.parentKey)!.push(n)
  }
  return map
})

const visibleMessages = computed<Msg[]>(() => {
  const children = childrenOf.value
  const out: Msg[] = []
  let key = '__root__'
  const seen = new Set<string>()
  for (;;) {
    const kids = children.get(key) || []
    if (!kids.length) break
    const sel = branchSel.value[key]
    const pick = kids.find((k) => k.key === sel) || kids[kids.length - 1]
    if (seen.has(pick.key)) break
    seen.add(pick.key)
    out.push(pick.msg)
    key = pick.key
  }
  // 流式消息恒置末尾 (尚未落库, 无 id)
  for (const m of messages.value) {
    if (m.streaming) out.push(m)
  }
  return out
})

/** 某条消息的兄弟分支信息 (无兄弟返回 null) */
function branchOf(m: Msg): { index: number; total: number; parentKey: string } | null {
  if (m.streaming) return null
  const node = branchNodes.value.find((n) => n.msg === m)
  if (!node) return null
  const siblings = childrenOf.value.get(node.parentKey) || []
  if (siblings.length < 2) return null
  const index = siblings.findIndex((s) => s.key === node.key)
  return { index: index + 1, total: siblings.length, parentKey: node.parentKey }
}

function switchBranch(parentKey: string, delta: number) {
  const siblings = childrenOf.value.get(parentKey) || []
  if (siblings.length < 2) return
  const cur = siblings.findIndex((s) => s.key === branchSel.value[parentKey])
  const base = cur < 0 ? siblings.length - 1 : cur
  const next = (base + delta + siblings.length) % siblings.length
  branchSel.value[parentKey] = siblings[next].key
}

function resetBranches() {
  branchSel.value = {}
}
const container = ref<HTMLElement | null>(null)
let abort: AbortController | null = null
let titleRefreshTimer: ReturnType<typeof setTimeout> | null = null

const activeTitle = computed(() => {
  if (!activeId.value) return '新对话'
  const c = conversations.value.find(v => v.id === activeId.value)
  return c?.title || '未命名对话'
})

function scrollBottom() { nextTick(() => { if (container.value) container.value.scrollTop = container.value.scrollHeight }) }

async function loadConversations() {
  convLoading.value = true
  try {
    const data = await client.get('/ai/conversations', { params: { page: convPage.value, size: convSize } }) as unknown as PageResult<Conv> | Conv[]
    if (Array.isArray(data)) { conversations.value = data; convTotal.value = data.length }
    else { conversations.value = (data.records || []) as Conv[]; convTotal.value = Number((data as PageResult<Conv>).total || 0) }
  } catch {
    // 失败时保持空态不阻断 UI；不填充任何演示 / Mock 数据，避免把临时故障伪装成历史会话
  } finally { convLoading.value = false }
}

function fmtTime(v?: string) {
  if (!v) return '—'
  try { const d = new Date(v); return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}` } catch { return v }
}

async function handleSelectConversation(id: string) {
  activeId.value = id
  drawerVisible.value = false
  resetBranches()
  await loadMessages(id)
}

async function loadMessages(conversationId: string) {
  try {
    const list = await client.get(`/ai/conversations/${conversationId}/messages`) as unknown as { contentSummary?: string; contentEncrypted?: string; role: string; citationJson?: string; tokenOutput?: number; latencyMs?: number }[] | { content?: string; role: string }[]
    const arr = Array.isArray(list) ? list : []
    if (!arr.length) { messages.value = [{ role: 'assistant', content: '会话已切换，继续提问吧。' }]; return }
    messages.value = (arr as Record<string, unknown>[]).map((m) => {
      const role = (m.role as string) === 'user' ? 'user' as const : 'assistant' as const
      const content = (m.contentSummary as string) || (m.content as string) || (m.contentEncrypted as string) || ''
      let citations: Record<string, unknown>[] = []
      try { const j = (m as { citationJson?: string }).citationJson; if (j) citations = JSON.parse(j) } catch {}
      const tokens = (m as { tokenOutput?: number }).tokenOutput as number | undefined
      const latencyMs = (m as { latencyMs?: number }).latencyMs as number | undefined
      const id = m.id as string | undefined
      const feedback = (m as { feedback?: string }).feedback as string | undefined
      const parentMessageId = (m as { parentMessageId?: string }).parentMessageId as string | undefined
      return { id, role, content, citations, tokens, latencyMs, feedback, parentMessageId }
    })
    nextTick(scrollBottom)
  } catch {
    messages.value = [{ role: 'assistant', content: '历史消息加载失败，已为你开启新上下文。' }]
  }
}

function handleNewConversation() {
  activeId.value = undefined
  resetBranches()
  messages.value = [{ role: 'assistant', content: '已开启新对话，试试输入一个问题。' }]
  drawerVisible.value = false
  nextTick(scrollBottom)
  loadConversations()
}

async function handleDelete(id: string) {
  try { await ElMessageBox.confirm('删除后仅在本地隐藏会话（后端暂未开放删除接口），是否继续？', '删除会话') } catch { return }
  try { await client.delete(`/ai/conversations/${id}`) } catch { /* ignore */ }
  conversations.value = conversations.value.filter(c => c.id !== id)
  if (activeId.value === id) handleNewConversation()
  ElMessage.success('已移除')
}

async function handleRename(c: Conv) {
  try {
    const { value } = await ElMessageBox.prompt('输入新的会话标题', '重命名', { confirmButtonText: '保存', cancelButtonText: '取消', inputValue: c.title || '' })
    const name = String(value || '').trim()
    if (!name) return
    try { await client.put(`/ai/conversations/${c.id}`, { title: name }) } catch { /* local only */ }
    c.title = name
    ElMessage.success('已重命名')
  } catch { /* cancel */ }
}

// P2-C: 会话置顶/取消置顶 (后端 ai_conversation.pinned, 列表服务端置顶优先)
async function handleTogglePin(c: Conv) {
  const action = c.pinned ? 'unpin' : 'pin'
  try {
    await client.post(`/ai/conversations/${c.id}/${action}`)
    c.pinned = !c.pinned
    ElMessage.success(c.pinned ? '已置顶' : '已取消置顶')
    loadConversations()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '操作失败')
  }
}

// P2-C: 消息反馈 (后端 ai_message.feedback, 仅 assistant 消息有 id 才可评价)
async function handleFeedback(m: Msg, value: 'LIKE' | 'DISLIKE') {
  if (!m.id) return
  const next = m.feedback === value ? undefined : value
  try {
    await client.post(`/ai/messages/${m.id}/feedback`, { feedback: next ?? null })
    m.feedback = next
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '评价失败')
  }
}

// P2-C: 重新生成 (重发最后一条用户消息, 新消息与原文同父 = 兄弟分支)
function handleRegenerate() {
  if (sending.value) return
  const visible = visibleMessages.value
  for (let i = visible.length - 1; i >= 0; i--) {
    const m = visible[i]
    if (m.role === 'user' && m.content && !m.content.startsWith('[附件')) {
      const text = m.content.replace(/\n\[附件:.*\]$/, '')
      // 与原文同父; 老数据无 parent 记录时退化为续尾
      const parent = m.parentMessageId ?? visible.slice(0, i).reverse().find((x) => x.id)?.id
      handleSend(text || '请重新回答', parent)
      return
    }
  }
  ElMessage.info('没有可重新生成的内容')
}

function scheduleTitleRefresh() {
  if (titleRefreshTimer) clearTimeout(titleRefreshTimer)
  titleRefreshTimer = setTimeout(() => { loadConversations(); titleRefreshTimer = setTimeout(loadConversations, 2500) }, 1400)
}

function scoreColor(s: number) {
  if (s >= 0.7) return 'var(--yt-chat-score-high)'
  if (s >= 0.45) return 'var(--yt-chat-score-mid)'
  return 'var(--yt-chat-score-low)'
}

async function uploadPendingFiles(files: ChatAttachment[]): Promise<string[]> {
  const urls: string[] = []
  for (const a of files) {
    if (!a.raw) continue
    const form = new FormData()
    form.append('file', a.raw, a.name)
    const uploaded = await client.post('/files/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } }) as { id?: string }
    if (!uploaded?.id) throw new Error(`附件上传失败: ${a.name}`)
    const url = await client.get(`/files/${uploaded.id}/download-url`) as string
    urls.push(url)
  }
  return urls
}

async function handleSend(prefill?: string, parentId?: string) {
  const q = (prefill ?? input.value).trim()
  if ((!q && !attachments.value.length) || sending.value) return
  input.value = ''
  // V052 P2-C 分支链: 新消息挂到当前可见路径末条 (显式重试可指定挂靠点, 缺省续尾)
  const parentMessageId =
    parentId ?? [...visibleMessages.value].reverse().find((m) => m.id)?.id
  const pendingAttachments = [...attachments.value]
  attachments.value = []
  const userToolSnapshot = pendingAttachments.length ? `\n[附件: ${pendingAttachments.map(a=>a.name).join(', ')}]` : ''
  messages.value.push({ role: 'user', content: (q || '请分析附件') + userToolSnapshot })
  const ai: Msg = { role: 'assistant', content: '', streaming: true, citations: [], reasoning: '', thinkingStatus: enableThinking.value ? 'thinking' : 'start' }
  messages.value.push(ai)
  sending.value = true
  abort = new AbortController()
  scrollBottom()
  let conversationIdForNext = activeId.value
  let isThinking = false
  try {
    const fileUrls = await uploadPendingFiles(pendingAttachments)
    let message = q || '请分析附件'
    if (fileUrls.length) {
      message += '\n\n' + fileUrls.map((u, i) => `[附件${i + 1}](${u})`).join('\n')
    }
    // 多模态 attachments：把已上传文件 URL 透传到后端，构造 OpenAI 多模态 content（P1-7）
    // 仅 image 类型参与多模态 vision；其他类型后端折叠为文本
    const attachments = fileUrls.map((u, i) => {
      const orig = pendingAttachments[i]
      const mime = orig?.raw?.type || ''
      const type = mime.startsWith('image/') ? 'image' : (mime.startsWith('audio/') ? 'audio' : 'image')
      return { type, url: u, name: orig?.name, format: type === 'audio' ? (mime.split('/')[1] || 'wav') : undefined }
    })
    await streamChat(
      {
        message,
        scenario: 'PLATFORM_QA',
        idempotencyKey: `web-${Date.now()}-${Math.random().toString(36).slice(2,7)}`,
        conversationId: activeId.value,
        enableThinking: enableThinking.value,
        attachments,
        // P2-G: Agent/模型选择透传 (空值=自动路由, 后端 AiChatRequest.agentId/providerCode/modelCode)
        // V052 P2-C: parentMessageId 分支挂靠 (空=链首/默认续尾)
        ...(selectedAgentId.value ? { agentId: selectedAgentId.value } : {}),
        ...(selectedModel.value.providerCode ? { providerCode: selectedModel.value.providerCode } : {}),
        ...(selectedModel.value.modelCode ? { modelCode: selectedModel.value.modelCode } : {}),
        ...(parentMessageId ? { parentMessageId } : {}),
      },
      {
        onDelta: (d) => {
          const raw = d as unknown as Record<string, unknown>
          const chunkText = (raw.text as string) ?? (raw.content as string) ?? ''
          const reasonChunk = (raw.reasoning_content as string) ?? (raw.reasoning as string) ?? ''
          if (reasonChunk) {
            ai.reasoning = (ai.reasoning || '') + reasonChunk
            ai.thinkingStatus = 'thinking'
          }
          if (chunkText) {
            let cur = chunkText
            // 处理内嵌 <think> / </think> 标签的三轨渲染
            if (!isThinking && cur.includes('<think')) {
              const idx = cur.indexOf('<think')
              const gt = cur.indexOf('>', idx)
              const before = idx > 0 ? cur.slice(0, idx) : ''
              if (before) ai.content += before
              cur = gt !== -1 ? cur.slice(gt + 1) : cur.slice(idx + 6)
              isThinking = true
              ai.thinkingStatus = 'thinking'
            }
            if (isThinking && cur.includes('</think')) {
              const idx = cur.indexOf('</think')
              const before = cur.slice(0, idx)
              if (before) ai.reasoning = (ai.reasoning || '') + before
              cur = cur.slice(idx + 8)
              isThinking = false
              ai.thinkingStatus = 'end'
            }
            if (cur) {
              if (isThinking) ai.reasoning = (ai.reasoning || '') + cur
              else ai.content += cur
            }
          }
          scrollBottom()
        },
        onCitation: (d) => { ai.citations?.push(d); scrollBottom() },
        onTool: (d) => {
          const name = String((d as { name?: string }).name || (d as { id?: string }).id || 'tool')
          const status = ((d as { status?: ToolCall['status'] }).status || 'success')
          ai.toolCalls = [...(ai.toolCalls || []), {
            id: String((d as { id?: string }).id || name),
            name,
            status,
            summary: String((d as { summary?: string }).summary || ''),
          }]
          scrollBottom()
        },
        onMeta: (d) => {
          const dd = d as { conversationId?: string }
          if (dd.conversationId && !activeId.value) { activeId.value = dd.conversationId; conversationIdForNext = dd.conversationId }
        },
        onDone: (d) => {
          ai.streaming = false
          if (ai.thinkingStatus === 'thinking') ai.thinkingStatus = 'end'
          isThinking = false
          const dd = d as Record<string, unknown>
          if (typeof dd.tokens === 'number') ai.tokens = dd.tokens as number
          if (typeof dd.tokenOutput === 'number') ai.tokens = dd.tokenOutput as number
          if (typeof dd.latencyMs === 'number') ai.latencyMs = dd.latencyMs as number
          // P2-C: done 事件携带 assistant 消息 id, 据此挂载点赞/点踩
          if (typeof dd.messageId === 'string' && dd.messageId) ai.id = dd.messageId as string
          const convId = (dd.conversationId as string) || conversationIdForNext
          if (convId) activeId.value = convId
          scheduleTitleRefresh()
        },
        onError: (d) => { ai.streaming = false; if (ai.thinkingStatus === 'thinking') ai.thinkingStatus = 'error'; ai.content += `\n[错误:${(d as { code?: string }).code ?? ''}]`; ElMessage.error('流式异常') },
      },
      abort.signal,
    )
  } catch (e: unknown) {
    const name = (e as { name?: string })?.name
    const msg = (e as { message?: string })?.message || ''
    if (name === 'AbortError' || msg.includes('abort')) { ElMessage.info('已停止生成') } else { ai.content += `\n[发送失败:${msg}]`; ElMessage.error('发送失败') }
    ai.streaming = false
    if (ai.thinkingStatus === 'thinking') ai.thinkingStatus = 'error'
  } finally { sending.value = false; abort = null; scrollBottom(); if (!activeId.value) scheduleTitleRefresh(); else loadConversations() }
}

function onSenderSubmit(val: string) {
  const v = (val ?? input.value ?? '').trim()
  if (!v) {
    if (!attachments.value.length) return
    // 仅附件也允许发送
    handleSend(v || '请分析附件')
    return
  }
  handleSend(v)
}
function stop() { abort?.abort() }
function clearCurrent() { resetBranches(); messages.value = [{ role: 'assistant', content: '对话已清空，继续提问吧。' }]; scrollBottom() }

watch(activeId, () => nextTick(scrollBottom))

onMounted(() => { loadConversations() })
onUnmounted(() => { abort?.abort(); if (titleRefreshTimer) clearTimeout(titleRefreshTimer); window.removeEventListener('resize', checkMobile) })
</script>

<template>
  <div class="yt-chat">
    <!-- 侧栏 -->
    <aside class="yt-chat__sidebar" :class="{ 'is-mobile-hidden': isMobile }">
      <div class="yt-chat__sidebar-head">
        <el-button type="primary" :icon="Plus" class="yt-chat__newBtn" @click="handleNewConversation">新建对话</el-button>
        <el-button :icon="Refresh" circle size="small" :loading="convLoading" style="flex-shrink:0" @click="loadConversations" aria-label="刷新会话" />
      </div>
      <div class="yt-chat__sidebar-sub">GET /api/v1/ai/conversations · 分页 {{ convTotal }} 条 · 自动标题</div>

      <el-scrollbar class="yt-chat__convScroll">
        <div v-if="convLoading && !conversations.length" class="yt-chat__skeleton">
          <el-skeleton :rows="3" animated v-for="i in 3" :key="i" style="padding: 12px 0" />
        </div>
        <div v-else-if="!conversations.length" class="yt-chat__emptySide">
          <el-icon :size="28" style="color: var(--yt-color-primary-light-5)"><ChatDotRound /></el-icon>
          <div class="yt-chat__emptySide-title">暂无会话</div>
          <div class="yt-chat__emptySide-desc">发送一条消息后自动创建</div>
        </div>
        <div v-else class="yt-chat__convList">
          <div
            v-for="c in conversations"
            :key="c.id"
            class="yt-chat__convItem"
            :class="{ 'is-active': c.id === activeId }"
            @click="handleSelectConversation(c.id)"
          >
            <div class="yt-chat__convTitle" :title="c.title || '未命名对话'">{{ c.title || '未命名对话' }}</div>
            <div class="yt-chat__convMeta">
              <span v-if="c.pinned" class="yt-chat__convPinned">置顶</span>
              <span class="yt-chat__convTime">{{ fmtTime(c.lastMessageTime || c.createdTime) }}</span>
              <span v-if="c.scenario" class="yt-chat__convScenario">{{ c.scenario }}</span>
            </div>
            <div class="yt-chat__convActions" @click.stop>
              <!-- P2-C: 置顶切换 -->
              <el-button text size="small" :icon="c.pinned ? StarFilled : Star" :type="c.pinned ? 'warning' : ''" :aria-label="c.pinned ? '取消置顶' : '置顶'" @click="handleTogglePin(c)" />
              <el-button text size="small" :icon="EditPen" aria-label="重命名" @click="handleRename(c)" />
              <el-button text size="small" :icon="Delete" type="danger" aria-label="删除" @click="handleDelete(c.id)" />
            </div>
          </div>
        </div>
      </el-scrollbar>

      <div class="yt-chat__sidebar-foot">
        <span class="yt-chat__footText">共 {{ convTotal }} 会话 · 第 {{ convPage }} 页</span>
        <div class="yt-chat__footBtns">
          <el-button size="small" :disabled="convPage<=1" @click="convPage=Math.max(1,convPage-1); loadConversations()">上一页</el-button>
          <el-button size="small" :disabled="conversations.length < convSize" @click="convPage+=1; loadConversations()">下一页</el-button>
        </div>
      </div>
    </aside>

    <!-- 移动端抽屉 -->
    <el-drawer v-if="isMobile" v-model="drawerVisible" direction="ltr" size="82%" :with-header="false" append-to-body>
      <div class="yt-chat__drawerHead">
        <strong style="color: var(--yt-text-primary)">会话历史</strong>
        <el-button :icon="Close" circle size="small" @click="drawerVisible=false" />
      </div>
      <div class="yt-chat__drawerNew"><el-button type="primary" :icon="Plus" style="width:100%" @click="handleNewConversation">新建对话</el-button></div>
      <div class="yt-chat__convList">
        <div v-for="c in conversations" :key="c.id" class="yt-chat__convItem" :class="{ 'is-active': c.id===activeId }" @click="handleSelectConversation(c.id)">
          <div class="yt-chat__convTitle">{{ c.title || '未命名对话' }}</div>
          <div class="yt-chat__convMeta"><span>{{ fmtTime(c.lastMessageTime || c.createdTime) }}</span><span v-if="c.scenario">{{ c.scenario }}</span></div>
        </div>
      </div>
    </el-drawer>

    <!-- 主对话区 -->
    <section class="yt-chat__main">
      <div class="yt-chat__mainHead">
        <div class="yt-chat__mainHead-left">
          <el-button v-if="isMobile" :icon="ChatDotRound" circle size="small" @click="drawerVisible=true" aria-label="会话列表" />
          <span class="yt-chat__mainTitle">{{ activeTitle }}</span>
          <el-tag size="small" effect="plain" style="border-color: var(--yt-border-default); color: var(--yt-text-secondary)">PLATFORM_QA</el-tag>
          <el-tag v-if="activeId" size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary); max-width: 160px; overflow:hidden; text-overflow:ellipsis">{{ activeId.slice(-8) }}</el-tag>
        </div>
        <div class="yt-chat__mainHead-right">
          <ThemeToggle />
          <el-button size="small" :icon="Brush" @click="clearCurrent">清空</el-button>
        </div>
      </div>

      <div ref="container" class="yt-chat__messages" aria-live="polite">
        <!-- 空态插画 + 示例问题 -->
        <div v-if="visibleMessages.length===1 && visibleMessages[0].role==='assistant' && !sending" class="yt-chat__welcome">
          <div class="yt-chat__welcome-illus">
            <div class="yt-chat__welcome-ring" />
            <el-icon :size="48" style="color: var(--yt-color-primary)"><ChatDotRound /></el-icon>
          </div>
          <div class="yt-chat__welcome-title">与 YuTong 助手对话</div>
          <div class="yt-chat__welcome-desc">基于 RAG 混合检索与重排，回答将附带引用溯源与用量信息</div>
          <div class="yt-chat__chips">
            <el-tag
              v-for="s in suggestions"
              :key="s"
              effect="plain"
              class="yt-chat__chip"
              @click="handleSend(s)"
            >{{ s }}</el-tag>
          </div>
          <div class="yt-chat__welcome-cards">
            <div class="yt-chat__welcome-card"><strong>可信</strong><span>引用来源可追溯</span></div>
            <div class="yt-chat__welcome-card"><strong>克制</strong><span>无多余打扰</span></div>
            <div class="yt-chat__welcome-card"><strong>精密</strong><span>混合检索 + 重排</span></div>
          </div>
        </div>

        <template v-for="(m,i) in visibleMessages" :key="m.id || i">
          <!-- 思考链：Thinking 折叠展示，自动收起由 status 驱动 -->
          <Thinking
            v-if="m.role==='assistant' && m.reasoning"
            :content="m.reasoning"
            :status="m.thinkingStatus || (m.streaming ? 'thinking' : 'end')"
            :autoCollapse="false"
            :maxWidth="'720px'"
            class="yt-thinking"
            style="margin-left: 42px;"
          />
          <!-- 气泡：vue-element-plus-x Bubble，typing 流式打字，--yt-* 令牌驱动 -->
          <Bubble
            :content="m.content"
            :loading="!!m.streaming"
            :typing="m.streaming ? { step: 2, interval: 30 } : false"
            :placement="m.role==='user' ? 'end' : 'start'"
            :variant="m.role==='user' ? 'filled' : 'outlined'"
            :isMarkdown="m.role==='assistant'"
            maxWidth="720px"
            shape="corner"
            avatarSize="32px"
          >
            <template #avatar>
              <el-icon v-if="m.role==='user'" style="background: var(--yt-color-primary); color: var(--yt-bg-card); border-radius: 50%; width:32px; height:32px; display:flex; align-items:center; justify-content:center; border: 1px solid var(--yt-color-primary-dark-2);"><User /></el-icon>
              <el-icon v-else style="background: var(--yt-bg-page); color: var(--yt-text-secondary); border-radius: 50%; width:32px; height:32px; display:flex; align-items:center; justify-content:center; border:1px solid var(--yt-border-light);"><Service /></el-icon>
            </template>
          </Bubble>
          <!-- 引用 / token 尾脚（保留在 Bubble 外，保持与原 StreamingMessage 一致的 yt 令牌） -->
          <div v-if="m.role==='assistant' && m.citations?.length" class="yt-msgCitations">
            <el-tag
              v-for="(c, ci) in m.citations"
              :key="ci"
              size="small"
              effect="plain"
              class="yt-msgChip"
              :title="String(c.chunkTextPreview || c.title || c.docTitle || '')"
            >
              <span class="yt-msgChip__index">[{{ ci + 1 }}]</span>
              {{ String(c.docTitle || c.title || c.source || '引用') }}
              <span v-if="c.score != null" class="yt-msgChip__score" :style="{ color: scoreColor(Number(c.score)) }"> {{ (Number(c.score) * 100).toFixed(1) }}%</span>
            </el-tag>
          </div>
          <div v-if="m.role==='assistant' && (m.tokens != null || m.latencyMs != null || m.id)" class="yt-msgFooter">
            <span v-if="m.tokens != null">{{ m.tokens }} tokens</span>
            <span v-if="m.tokens != null && m.latencyMs != null"> · </span>
            <span v-if="m.latencyMs != null">{{ m.latencyMs }}ms</span>
            <!-- P2-C: 反馈 (仅有服务端 id 的消息可评价) -->
            <span v-if="m.id && !m.streaming" class="yt-msgFeedback">
              <el-button
                text
                size="small"
                :icon="Top"
                :type="m.feedback === 'LIKE' ? 'success' : ''"
                aria-label="赞同"
                @click="handleFeedback(m, 'LIKE')"
              />
              <el-button
                text
                size="small"
                :icon="Bottom"
                :type="m.feedback === 'DISLIKE' ? 'danger' : ''"
                aria-label="反对"
                @click="handleFeedback(m, 'DISLIKE')"
              />
            </span>
            <!-- P2-C: 重新生成 (最后一条可见回复) + 分支切换 -->
            <el-button
              v-if="!m.streaming && i === visibleMessages.length - 1"
              text
              size="small"
              :icon="RefreshRight"
              aria-label="重新生成"
              :disabled="sending"
              @click="handleRegenerate"
            >重新生成</el-button>
            <span v-if="!m.streaming && branchOf(m)" class="yt-branchPager">
              <el-button text size="small" aria-label="上个分支" @click="switchBranch(branchOf(m)!.parentKey, -1)">‹</el-button>
              <span class="yt-branchPager__text">{{ branchOf(m)!.index }} / {{ branchOf(m)!.total }}</span>
              <el-button text size="small" aria-label="下个分支" @click="switchBranch(branchOf(m)!.parentKey, 1)">›</el-button>
            </span>
          </div>
          <!-- P2-G: 工具调用时间线独立组件 -->
          <ToolCallTimeline
            v-if="m.role==='assistant' && m.toolCalls && m.toolCalls.length"
            :calls="m.toolCalls"
          />
        </template>

        <div v-if="sending" class="yt-chat__typing"><span class="yt-chat__dot" /><span class="yt-chat__dot" /><span class="yt-chat__dot" /> 正在生成…</div>
      </div>

      <!-- 快捷建议 chips（有消息时展示于输入框上方） -->
      <div v-if="visibleMessages.length>1" class="yt-chat__suggestRow">
        <el-tag v-for="s in suggestions" :key="s" size="small" effect="plain" class="yt-chat__chip yt-chat__chip--sm" @click="handleSend(s)">{{ s }}</el-tag>
      </div>

      <!-- P2-G: DeepThinking 独立开关组件 -->
      <div class="yt-chat__controls">
        <div class="yt-chat__controlLeft">
          <DeepThinkingToggle v-model="enableThinking" />
        </div>
        <div class="yt-chat__controlRight">
          <span class="yt-chat__controlHint" style="border-style: solid;">Shift+Enter 换行 · Enter 发送</span>
        </div>
      </div>

      <!-- 输入栏：vue-element-plus-x Sender -->
      <div class="yt-chat__senderWrap">
        <Sender
          ref="senderRef"
          v-model="input"
          variant="updown"
          :auto-size="{ minRows: 1, maxRows: 5 }"
          allow-speech
          clearable
          :loading="sending"
          :submitBtnDisabled="!input.trim() && !attachments.length"
          placeholder="输入问题，回车发送 · Shift+Enter 换行"
          submitType="enter"
          :style="{ '--el-color-primary': 'var(--yt-color-primary)' }"
          @submit="onSenderSubmit"
          @cancel="stop"
        >
          <template #header>
            <!-- P2-G: 附件选择独立组件 (v-model 持有列表) -->
            <FilesSelect ref="filesSelectRef" v-model="attachments" :disabled="sending" />
          </template>
          <template #prefix>
            <div class="yt-senderPrefix">
              <div class="yt-senderPrefix__left">
                <!-- P2-G: 真接口 Agent/模型选择 (透传 agentId/providerCode/modelCode) -->
                <AgentSelect v-model="selectedAgentId" />
                <ModelSelect v-model="selectedModel" />
              </div>
              <el-button size="small" class="yt-senderPrefix__attach" style="margin-left:auto; border-color: var(--yt-border-default); color: var(--yt-text-secondary);" @click="triggerFileSelect">附件</el-button>
            </div>
          </template>
        </Sender>
      </div>
      <div class="yt-chat__footnote">后端 POST /api/v1/ai/chat (Accept: text/event-stream) · 标题异步生成，发送后 2s 内自动刷新</div>
    </section>
  </div>
</template>

<style scoped>
.yt-chat { display:flex; gap: var(--yt-space-md); height: calc(100vh - 96px); min-height: 560px; max-width: 1280px; margin: 0 auto; }
/* sidebar */
.yt-chat__sidebar { width: 300px; flex-shrink:0; display:flex; flex-direction:column; gap: var(--yt-space-sm); background: var(--yt-bg-card); border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); box-shadow: var(--yt-shadow-card); padding: var(--yt-space-md); overflow:hidden; }
.yt-chat__sidebar.is-mobile-hidden { display:flex; }
@media (max-width: 860px) { .yt-chat__sidebar.is-mobile-hidden { display:none; } .yt-chat { height: calc(100vh - 88px); } }
.yt-chat__sidebar-head { display:flex; gap: var(--yt-space-sm); align-items:center; }
.yt-chat__newBtn { flex:1; border-radius: var(--yt-radius-md); font-weight: var(--yt-font-weight-bold); }
.yt-chat__sidebar-sub { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); line-height: var(--yt-font-line-height-caption); }
.yt-chat__convScroll { flex:1; min-height:0; }
.yt-chat__skeleton { padding: var(--yt-space-sm) 0; }
.yt-chat__emptySide { display:flex; flex-direction:column; align-items:center; justify-content:center; padding: var(--yt-space-xl) var(--yt-space-md); gap: var(--yt-space-sm); text-align:center; }
.yt-chat__emptySide-title { font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); }
.yt-chat__emptySide-desc { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-chat__convList { display:flex; flex-direction:column; gap: var(--yt-space-xs); padding: 2px 0; }
.yt-chat__convItem { position:relative; padding: var(--yt-space-sm) var(--yt-space-sm); border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); background: var(--yt-bg-card); cursor:pointer; transition: border-color var(--yt-transition-hover), background var(--yt-transition-hover), box-shadow var(--yt-transition-hover); }
.yt-chat__convItem:hover { border-color: var(--yt-color-primary-light-7); background: var(--yt-color-primary-light-9); box-shadow: var(--yt-shadow-card); }
.yt-chat__convItem.is-active { border-color: var(--yt-color-primary-light-5); background: var(--yt-color-primary-light-9); box-shadow: var(--yt-shadow-card); }
.yt-chat__convTitle { font-size: 13px; font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); white-space: nowrap; overflow:hidden; text-overflow:ellipsis; padding-right: 56px; }
.yt-chat__convMeta { display:flex; gap: var(--yt-space-sm); font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); margin-top: 2px; }
.yt-chat__convScenario { color: var(--yt-color-primary); }
.yt-chat__convActions { position:absolute; right: 4px; top: 6px; display:flex; gap: 2px; opacity:0; transition: opacity var(--yt-transition-hover); }
.yt-chat__convItem:hover .yt-chat__convActions, .yt-chat__convItem.is-active .yt-chat__convActions { opacity:1; }
.yt-chat__sidebar-foot { display:flex; flex-direction:column; gap: var(--yt-space-sm); border-top: 1px solid var(--yt-border-light); padding-top: var(--yt-space-sm); }
.yt-chat__footText { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-chat__footBtns { display:flex; gap: var(--yt-space-sm); }
/* drawer */
.yt-chat__drawerHead { display:flex; justify-content:space-between; align-items:center; margin-bottom: var(--yt-space-sm); }
.yt-chat__drawerNew { margin-bottom: var(--yt-space-md); }
/* main */
.yt-chat__main { flex:1; min-width:0; display:flex; flex-direction:column; background: var(--yt-bg-card); border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); box-shadow: var(--yt-shadow-card); overflow:hidden; }
.yt-chat__mainHead { display:flex; justify-content:space-between; align-items:center; gap: var(--yt-space-sm); padding: var(--yt-space-sm) var(--yt-space-md); border-bottom: 1px solid var(--yt-border-light); background: var(--yt-bg-card); flex-wrap: wrap; }
.yt-chat__mainHead-left { display:flex; align-items:center; gap: var(--yt-space-sm); flex-wrap: wrap; min-width:0; }
.yt-chat__mainTitle { font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); font-size: var(--yt-font-size-body); white-space: nowrap; overflow:hidden; text-overflow:ellipsis; max-width: 220px; }
.yt-chat__mainHead-right { display:flex; gap: var(--yt-space-sm); }
.yt-chat__messages { flex:1; min-height:0; overflow-y:auto; display:flex; flex-direction:column; gap: var(--yt-space-md); padding: var(--yt-space-md); background: linear-gradient(180deg, var(--yt-bg-card) 0%, var(--yt-bg-page) 100%); }
.yt-chat__messages::-webkit-scrollbar { width: 6px; } .yt-chat__messages::-webkit-scrollbar-thumb { background: var(--yt-border-default); border-radius: var(--yt-radius-sm); }
/* welcome */
.yt-chat__welcome { display:flex; flex-direction:column; align-items:center; text-align:center; gap: var(--yt-space-sm); padding: var(--yt-space-lg) var(--yt-space-md); border: 1px dashed var(--yt-border-default); border-radius: var(--yt-radius-md); background: var(--yt-bg-card); }
.yt-chat__welcome-illus { position:relative; width: 88px; height: 88px; display:flex; align-items:center; justify-content:center; }
.yt-chat__welcome-ring { position:absolute; inset:0; border-radius: 50%; border: 2px dashed var(--yt-color-primary-light-7); opacity:.7; }
.yt-chat__welcome-title { font-size: 18px; font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); }
.yt-chat__welcome-desc { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); max-width: 520px; line-height: var(--yt-font-line-height-caption); }
.yt-chat__chips { display:flex; gap: var(--yt-space-sm); flex-wrap: wrap; justify-content:center; margin-top: var(--yt-space-sm); }
.yt-chat__chip { cursor:pointer; border-color: var(--yt-border-default) !important; color: var(--yt-text-secondary) !important; background: var(--yt-bg-card) !important; border-radius: 999px !important; transition: all var(--yt-transition-hover); }
.yt-chat__chip:hover { border-color: var(--yt-color-primary-light-5) !important; color: var(--yt-color-primary) !important; background: var(--yt-color-primary-light-9) !important; }
.yt-chat__chip--sm { font-size: var(--yt-font-size-caption); }
.yt-chat__welcome-cards { display:flex; gap: var(--yt-space-sm); flex-wrap: wrap; justify-content:center; margin-top: var(--yt-space-sm); }
.yt-chat__welcome-card { display:flex; flex-direction:column; align-items:center; gap: 2px; padding: var(--yt-space-sm) var(--yt-space-md); border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); background: var(--yt-bg-page); min-width: 120px; }
.yt-chat__welcome-card strong { font-size: 13px; color: var(--yt-text-primary); } .yt-chat__welcome-card span { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-chat__typing { display:flex; align-items:center; gap: 6px; font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-chat__dot { width:6px; height:6px; border-radius:50%; background: var(--yt-color-primary-light-5); animation: yt-dot 1.2s infinite; } .yt-chat__dot:nth-child(2){ animation-delay:.2s } .yt-chat__dot:nth-child(3){ animation-delay:.4s }
@keyframes yt-dot { 0%,80%,100%{ opacity:.3; transform: scale(.8)} 40%{ opacity:1; transform: scale(1)} }
.yt-chat__suggestRow { display:flex; gap: var(--yt-space-xs); flex-wrap: wrap; padding: var(--yt-space-sm) var(--yt-space-md); border-top: 1px solid var(--yt-border-light); background: var(--yt-bg-card); }
.yt-chat__controls { display:flex; justify-content:space-between; align-items:center; gap: var(--yt-space-sm); padding: var(--yt-space-sm) var(--yt-space-md); border-top: 1px solid var(--yt-border-light); background: var(--yt-bg-page); }
.yt-chat__controlLeft { display:flex; align-items:center; gap: var(--yt-space-sm); }
.yt-chat__controlHint { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); border: 1px dashed var(--yt-border-default); padding: 2px 8px; border-radius: 999px; background: var(--yt-bg-card); }
.yt-chat__controlRight { display:flex; gap: var(--yt-space-sm); align-items:center; }
/* Sender 定制：全部用 --yt-* 覆盖，禁止硬编码 */
.yt-chat__senderWrap { padding: var(--yt-space-sm) var(--yt-space-md); border-top: 1px solid var(--yt-border-light); background: var(--yt-bg-card); }
.yt-chat__senderWrap :deep(.el-sender) { --el-color-primary: var(--yt-color-primary); border-color: var(--yt-border-light); border-radius: var(--yt-radius-md); box-shadow: var(--yt-shadow-card); }
.yt-chat__senderWrap :deep(.el-sender__input) { font-size: var(--yt-font-size-body); color: var(--yt-text-primary); }
.yt-chat__senderWrap :deep(.el-sender__prefix) { width: 100%; }
.yt-senderPrefix { display:flex; align-items:center; gap: var(--yt-space-sm); width: 100%; }
.yt-senderPrefix__left { display:flex; gap: var(--yt-space-sm); align-items:center; flex-wrap: wrap; }
.yt-thinking { --el-color-primary: var(--yt-color-primary); }
.yt-thinking :deep(.el-thinking) { border-color: var(--yt-border-light); background: var(--yt-bg-page); border-radius: var(--yt-radius-md); }
.yt-msgCitations { display:flex; flex-wrap: wrap; gap: var(--yt-space-xs); margin: 6px 0 0 42px; max-width: 78%; }
.yt-msgChip { background: var(--yt-chat-citation-bg) !important; border-color: var(--yt-chat-citation-border) !important; color: var(--yt-chat-citation-color) !important; border-radius: var(--yt-radius-sm) !important; }
.yt-msgChip__index { font-weight: var(--yt-font-weight-bold); margin-right: 2px; }
.yt-msgChip__score { font-size: var(--yt-font-size-caption); margin-left: 4px; }
.yt-msgFooter { font-size: var(--yt-chat-footer-size); color: var(--yt-chat-footer-color); line-height: var(--yt-font-line-height-caption); margin: 2px 0 0 42px; display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.yt-msgFeedback { display: inline-flex; align-items: center; gap: 0; margin-left: 4px; }
.yt-branchPager { display: inline-flex; align-items: center; gap: 2px; margin-left: 4px; }
.yt-branchPager__text { font-size: 11px; color: var(--yt-text-secondary); min-width: 32px; text-align: center; }
.yt-chat__convPinned { color: var(--yt-color-warning, #d97706); font-weight: 600; }
.yt-chat__footnote { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); text-align:center; padding: 6px var(--yt-space-md) var(--yt-space-sm); background: var(--yt-bg-card); }
/* Bubble 主题令牌对齐：filled/outlined 均用 yt 变量 */
:deep(.el-bubble) { --el-bubble-filled-bg: var(--yt-chat-user-bg); --el-bubble-filled-color: var(--yt-chat-user-color); }
:deep(.el-bubble__content) { font-size: var(--yt-font-size-body); line-height: var(--yt-font-line-height-body); }
</style>
