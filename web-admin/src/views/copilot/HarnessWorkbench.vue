<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Refresh,
  Delete,
  Top,
  VideoPlay,
  CircleClose,
  Connection,
  Check,
  Close,
  Star,
} from '@element-plus/icons-vue'
import {
  HARNESS_APPROVAL_POLICY_OPTIONS,
  HARNESS_PERMISSION_MODE_OPTIONS,
  approveHarnessPlan,
  cancelHarnessRun,
  createHarnessRun,
  createHarnessSession,
  deleteHarnessSession,
  extractPendingApprovals,
  extractPendingPlan,
  getHarnessRun,
  harnessRunStatusTag,
  isTerminalRunStatus,
  listHarnessRunEvents,
  listHarnessRuns,
  listHarnessSessions,
  pinHarnessSession,
  resolveHarnessApproval,
  restoreHarnessSession,
  streamHarnessRunEvents,
  type CreateSessionRequest,
  type HarnessApproval,
  type HarnessEvent,
  type HarnessPlan,
  type HarnessRun,
  type HarnessSession,
} from '@/api/harness'

/**
 * Coding Harness 工作台。设计来源: ai-depth-parity.md S2.1。
 * 左: 会话列表(新建/置顶/删除) | 右: Run 列表 + 创建 Run + 事件时间线 + 计划审批 + 待审批卡片 + 取消 + SSE。
 */

// ==================== 会话列表 ====================
const sessions = ref<HarnessSession[]>([])
const sessionsLoading = ref(false)
const includeDeleted = ref(false)
const currentSessionId = ref('')
const currentSession = computed(() => sessions.value.find((s) => s.id === currentSessionId.value) || null)

async function loadSessions() {
  sessionsLoading.value = true
  try {
    sessions.value = (await listHarnessSessions({ includeDeleted: includeDeleted.value })) || []
    if (currentSessionId.value && !sessions.value.some((s) => s.id === currentSessionId.value)) {
      currentSessionId.value = ''
      clearRunState()
    }
  } catch {
    sessions.value = []
  } finally {
    sessionsLoading.value = false
  }
}

function selectSession(id: string) {
  if (currentSessionId.value === id) return
  stopSse()
  currentSessionId.value = id
  clearRunState()
  void loadRuns()
}

function clearRunState() {
  runs.value = []
  selectedRunId.value = ''
  events.value = []
  pendingApprovals.value = []
  pendingPlan.value = null
  lastSequence.value = -1
}

// 新建会话
const createSessionVisible = ref(false)
const createSessionSaving = ref(false)
const sessionForm = reactive<CreateSessionRequest>({
  title: '',
  workspacePath: '',
  model: '',
  permissionMode: 'WORKSPACE_WRITE',
  approvalPolicy: 'ON_REQUEST',
})

function openCreateSession() {
  sessionForm.title = ''
  sessionForm.workspacePath = ''
  sessionForm.model = ''
  sessionForm.permissionMode = 'WORKSPACE_WRITE'
  sessionForm.approvalPolicy = 'ON_REQUEST'
  createSessionVisible.value = true
}

function newIdempotencyKey(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

async function submitCreateSession() {
  if (!sessionForm.title.trim()) {
    ElMessage.warning('请输入会话标题')
    return
  }
  createSessionSaving.value = true
  try {
    const s = await createHarnessSession({
      title: sessionForm.title.trim(),
      workspacePath: sessionForm.workspacePath?.trim() || '/workspace/harness',
      model: sessionForm.model?.trim() || undefined,
      permissionMode: sessionForm.permissionMode,
      approvalPolicy: sessionForm.approvalPolicy,
      idempotencyKey: newIdempotencyKey('harness-session'),
    })
    ElMessage.success('会话已创建')
    createSessionVisible.value = false
    await loadSessions()
    if (s?.id) selectSession(s.id)
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '创建会话失败')
  } finally {
    createSessionSaving.value = false
  }
}

async function togglePin(row: HarnessSession) {
  try {
    await pinHarnessSession(row.id, !row.pinnedAt)
    await loadSessions()
    ElMessage.success(row.pinnedAt ? '已取消置顶' : '已置顶')
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '置顶失败')
  }
}

async function removeSession(row: HarnessSession) {
  try {
    await ElMessageBox.confirm(`确认删除会话「${row.title}」？可稍后恢复。`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteHarnessSession(row.id)
    ElMessage.success('已删除')
    if (currentSessionId.value === row.id) {
      currentSessionId.value = ''
      clearRunState()
    }
    await loadSessions()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '删除失败')
  }
}

async function restoreSession(row: HarnessSession) {
  try {
    await restoreHarnessSession(row.id)
    ElMessage.success('已恢复')
    await loadSessions()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '恢复失败')
  }
}

// ==================== Run ====================
const runs = ref<HarnessRun[]>([])
const runsLoading = ref(false)
const selectedRunId = ref('')
const selectedRun = ref<HarnessRun | null>(null)

const runForm = reactive({
  requirement: '',
  maxToolCalls: 20,
  maxInputTokens: 100000,
  maxOutputTokens: 16000,
})
const creatingRun = ref(false)
const cancelling = ref(false)

async function loadRuns() {
  if (!currentSessionId.value) return
  runsLoading.value = true
  try {
    runs.value = (await listHarnessRuns(currentSessionId.value)) || []
  } catch {
    runs.value = []
  } finally {
    runsLoading.value = false
  }
}

async function submitCreateRun() {
  if (!currentSessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }
  if (!runForm.requirement.trim()) {
    ElMessage.warning('请输入 Run 需求')
    return
  }
  creatingRun.value = true
  try {
    const run = await createHarnessRun(currentSessionId.value, {
      requirement: runForm.requirement.trim(),
      budget: {
        maxToolCalls: runForm.maxToolCalls || undefined,
        maxInputTokens: runForm.maxInputTokens || undefined,
        maxOutputTokens: runForm.maxOutputTokens || undefined,
      },
      idempotencyKey: newIdempotencyKey('harness-run'),
    })
    ElMessage.success('Run 已创建')
    runForm.requirement = ''
    await loadRuns()
    if (run?.id) {
      await selectRun(run.id)
    }
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '创建 Run 失败')
  } finally {
    creatingRun.value = false
  }
}

async function selectRun(runId: string) {
  stopSse()
  selectedRunId.value = runId
  events.value = []
  pendingApprovals.value = []
  pendingPlan.value = null
  lastSequence.value = -1
  try {
    selectedRun.value = await getHarnessRun(currentSessionId.value, runId)
  } catch {
    selectedRun.value = runs.value.find((r) => r.id === runId) || null
  }
  await loadEvents()
  refreshDerived()
  if (selectedRun.value && !isTerminalRunStatus(selectedRun.value.status)) {
    startSse()
  }
}

function onRunRowChange(row: HarnessRun | undefined | null) {
  if (row?.id) {
    void selectRun(row.id)
  }
}

async function loadEvents() {
  if (!currentSessionId.value || !selectedRunId.value) return
  try {
    const list = (await listHarnessRunEvents(currentSessionId.value, selectedRunId.value, {
      afterSequence: lastSequence.value >= 0 ? lastSequence.value : undefined,
    })) || []
    mergeEvents(list)
  } catch {
    // ignore
  }
}

function mergeEvents(list: HarnessEvent[]) {
  for (const ev of list) {
    if (ev.sequence != null && ev.sequence <= lastSequence.value) continue
    events.value.push(ev)
    if (ev.sequence != null && ev.sequence > lastSequence.value) {
      lastSequence.value = ev.sequence
    }
  }
  events.value.sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0))
}

function refreshDerived() {
  pendingApprovals.value = extractPendingApprovals(events.value)
  pendingPlan.value = extractPendingPlan(events.value)
}

async function doCancelRun() {
  if (!currentSessionId.value || !selectedRunId.value) return
  try {
    await ElMessageBox.confirm('确认取消当前 Run？已执行的工具调用不会回滚。', '取消 Run', {
      type: 'warning',
      confirmButtonText: '取消 Run',
      cancelButtonText: '返回',
    })
  } catch {
    return
  }
  cancelling.value = true
  try {
    selectedRun.value = await cancelHarnessRun(currentSessionId.value, selectedRunId.value)
    ElMessage.success('已请求取消')
    await loadRuns()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '取消失败')
  } finally {
    cancelling.value = false
  }
}

// ==================== SSE ====================
const events = ref<HarnessEvent[]>([])
const lastSequence = ref(-1)
const sseActive = ref(false)
let sseAbort: AbortController | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null

function stopSse() {
  if (sseAbort) {
    sseAbort.abort()
    sseAbort = null
  }
  sseActive.value = false
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startSse() {
  stopSse()
  if (!currentSessionId.value || !selectedRunId.value) return
  sseActive.value = true
  sseAbort = new AbortController()
  const sessionId = currentSessionId.value
  const runId = selectedRunId.value
  void streamHarnessRunEvents(
    sessionId,
    runId,
    {
      afterSequence: lastSequence.value >= 0 ? lastSequence.value : undefined,
      onEvent: (ev) => {
        if (selectedRunId.value !== runId) return
        mergeEvents([ev])
        refreshDerived()
      },
      onError: () => {
        // SSE 失败时降级为 5s 轮询事件 + Run 状态
        sseActive.value = false
        armFallbackPolling(sessionId, runId)
      },
      onComplete: () => {
        sseActive.value = false
        void refreshRunStatus(sessionId, runId)
      },
    },
    sseAbort.signal
  ).catch(() => {
    sseActive.value = false
    armFallbackPolling(sessionId, runId)
  })
}

function armFallbackPolling(sessionId: string, runId: string) {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(() => {
    void (async () => {
      if (selectedRunId.value !== runId) {
        stopSse()
        return
      }
      try {
        const evs = await listHarnessRunEvents(sessionId, runId, {
          afterSequence: lastSequence.value >= 0 ? lastSequence.value : undefined,
        })
        mergeEvents(evs || [])
        refreshDerived()
        await refreshRunStatus(sessionId, runId)
        if (selectedRun.value && isTerminalRunStatus(selectedRun.value.status)) {
          stopSse()
        }
      } catch {
        stopSse()
      }
    })()
  }, 5000)
}

async function refreshRunStatus(sessionId: string, runId: string) {
  try {
    const run = await getHarnessRun(sessionId, runId)
    if (selectedRunId.value === runId) {
      selectedRun.value = run
      const idx = runs.value.findIndex((r) => r.id === runId)
      if (idx >= 0) runs.value[idx] = run
    }
  } catch {
    // ignore
  }
}

// ==================== 审批 ====================
const pendingApprovals = ref<HarnessApproval[]>([])
const pendingPlan = ref<HarnessPlan | null>(null)
const approvalBusy = ref<Record<string, boolean>>({})
const planBusy = ref(false)

function newDecisionId(): string {
  return `dec-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

async function resolveApproval(item: HarnessApproval, decision: 'APPROVE' | 'DENY') {
  approvalBusy.value = { ...approvalBusy.value, [item.id]: true }
  try {
    await resolveHarnessApproval(item.id, {
      decisionId: item.decisionId || newDecisionId(),
      decision,
      expectedRevision: item.expectedRevision ?? 0,
      argumentsSha256: item.argumentsSha256,
      note: decision === 'DENY' ? '用户在工作台拒绝' : '用户在工作台批准',
    })
    ElMessage.success(decision === 'APPROVE' ? '已批准工具调用' : '已拒绝工具调用')
    await loadEvents()
    refreshDerived()
    await refreshRunStatus(currentSessionId.value, selectedRunId.value)
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '审批失败')
  } finally {
    approvalBusy.value = { ...approvalBusy.value, [item.id]: false }
  }
}

async function approvePlan() {
  const plan = pendingPlan.value
  if (!plan || !plan.taskId) {
    ElMessage.warning('没有待审批的计划')
    return
  }
  planBusy.value = true
  try {
    await approveHarnessPlan({
      planId: plan.id || '',
      expectedRevision: plan.revision ?? 0,
      expectedHash: plan.canonicalHash || '',
      idempotencyKey: newIdempotencyKey('harness-plan'),
    })
    ElMessage.success('计划已批准')
    await loadEvents()
    refreshDerived()
    await refreshRunStatus(currentSessionId.value, selectedRunId.value)
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '计划审批失败')
  } finally {
    planBusy.value = false
  }
}

// ==================== 展示辅助 ====================
function eventTagType(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  if (/error|failed/i.test(type)) return 'danger'
  if (/approval|awaiting/i.test(type)) return 'warning'
  if (/completed|done|success/i.test(type)) return 'success'
  if (/plan/i.test(type)) return 'primary'
  return 'info'
}

function formatPayload(ev: HarnessEvent): string {
  const raw = ev.payloadJson ?? (ev.payload != null ? JSON.stringify(ev.payload) : '')
  if (!raw) return ''
  try {
    const o = JSON.parse(raw) as unknown
    const s = JSON.stringify(o, null, 0)
    return s.length > 280 ? s.slice(0, 280) + '…' : s
  } catch {
    return raw.length > 280 ? raw.slice(0, 280) + '…' : raw
  }
}

const sortedSessions = computed(() => {
  const list = [...sessions.value]
  list.sort((a, b) => {
    const pa = a.pinnedAt ? 1 : 0
    const pb = b.pinnedAt ? 1 : 0
    if (pa !== pb) return pb - pa
    return (b.updatedTime || b.createdTime || '').localeCompare(a.updatedTime || a.createdTime || '')
  })
  return list
})

const canCancel = computed(() => {
  const s = selectedRun.value?.status
  return !!s && !isTerminalRunStatus(s)
})

onMounted(() => {
  void loadSessions()
})

onBeforeUnmount(() => {
  stopSse()
})
</script>

<template>
  <div class="harness-page">
    <div class="page-head">
      <div>
        <h2 class="head-title">Coding Harness 工作台</h2>
        <p class="head-sub">会话 / Run / 预算 / 工具审批 / 计划审批 / SSE 事件时间线。权限码 ai:assistant:use。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadSessions">刷新会话</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateSession">新建会话</el-button>
      </div>
    </div>

    <div class="harness-layout">
      <!-- 左: 会话列表 -->
      <el-card shadow="never" class="session-panel">
        <template #header>
          <div class="panel-head">
            <span>会话</span>
            <el-checkbox v-model="includeDeleted" @change="loadSessions">含已删除</el-checkbox>
          </div>
        </template>
        <div v-loading="sessionsLoading" class="session-list">
          <div
            v-for="s in sortedSessions"
            :key="s.id"
            class="session-item"
            :class="{ active: s.id === currentSessionId, deleted: s.deleted }"
            @click="selectSession(s.id)"
          >
            <div class="session-title">
              <el-icon v-if="s.pinnedAt" class="pin-icon"><Star /></el-icon>
              <span class="title-text">{{ s.title }}</span>
            </div>
            <div class="session-meta">
              <el-tag size="small" effect="plain">{{ s.permissionMode || 'WORKSPACE_WRITE' }}</el-tag>
              <el-tag v-if="s.deleted" size="small" type="info">已删除</el-tag>
              <span v-else class="meta-model">{{ s.model || '默认模型' }}</span>
            </div>
            <div class="session-actions" @click.stop>
              <el-button
                v-if="!s.deleted"
                size="small"
                text
                :icon="Top"
                :title="s.pinnedAt ? '取消置顶' : '置顶'"
                @click="togglePin(s)"
              />
              <el-button
                v-if="!s.deleted"
                size="small"
                text
                type="danger"
                :icon="Delete"
                title="删除"
                @click="removeSession(s)"
              />
              <el-button
                v-else
                size="small"
                text
                type="primary"
                title="恢复"
                @click="restoreSession(s)"
              >
                恢复
              </el-button>
            </div>
          </div>
          <el-empty v-if="!sessionsLoading && sessions.length === 0" description="暂无会话" :image-size="64" />
        </div>
      </el-card>

      <!-- 右: Run 区 -->
      <div class="main-panel">
        <el-empty v-if="!currentSessionId" description="请选择左侧会话，或新建一个 Coding 会话" />

        <template v-else>
          <!-- 会话信息 + 创建 Run -->
          <el-card shadow="never" class="run-create-card">
            <template #header>
              <div class="panel-head">
                <span>{{ currentSession?.title || '会话' }}</span>
                <el-tag size="small" effect="plain">{{ currentSession?.permissionMode }}</el-tag>
                <el-tag size="small" type="info">{{ currentSession?.approvalPolicy }}</el-tag>
              </div>
            </template>
            <el-form label-width="96px" size="default">
              <el-form-item label="需求" required>
                <el-input
                  v-model="runForm.requirement"
                  type="textarea"
                  :rows="3"
                  placeholder="描述本次 Run 要完成的任务，例如：扫描 workspace 并生成模块骨架"
                />
              </el-form-item>
              <el-form-item label="工具上限">
                <el-input-number v-model="runForm.maxToolCalls" :min="1" :max="200" />
              </el-form-item>
              <el-form-item label="输入 Token">
                <el-input-number v-model="runForm.maxInputTokens" :min="1000" :step="1000" :max="2000000" />
              </el-form-item>
              <el-form-item label="输出 Token">
                <el-input-number v-model="runForm.maxOutputTokens" :min="500" :step="500" :max="200000" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :icon="VideoPlay" :loading="creatingRun" @click="submitCreateRun">
                  创建 Run
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- Run 列表 -->
          <el-card shadow="never" class="run-list-card">
            <template #header>
              <div class="panel-head">
                <span>Run 列表</span>
                <el-button size="small" :icon="Refresh" @click="loadRuns">刷新</el-button>
              </div>
            </template>
            <el-table
              v-loading="runsLoading"
              :data="runs"
              size="small"
              highlight-current-row
              @current-change="onRunRowChange"
            >
              <el-table-column prop="id" label="Run ID" min-width="140" show-overflow-tooltip />
              <el-table-column prop="requirement" label="需求" min-width="180" show-overflow-tooltip />
              <el-table-column label="状态" width="160">
                <template #default="{ row }">
                  <el-tag :type="harnessRunStatusTag(row.status)" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="工具调用" width="90" align="center">
                <template #default="{ row }">
                  {{ row.toolCallCount ?? row.usage?.toolCallCount ?? 0 }}
                </template>
              </el-table-column>
              <el-table-column prop="createdTime" label="创建时间" width="170" />
            </el-table>
          </el-card>

          <!-- 当前 Run 详情 -->
          <el-card v-if="selectedRun" shadow="never" class="run-detail-card">
            <template #header>
              <div class="panel-head">
                <span>
                  <el-icon><Connection /></el-icon>
                  Run 事件时间线
                  <el-tag size="small" class="ml-8">{{ selectedRun.status }}</el-tag>
                  <el-tag v-if="sseActive" size="small" type="success" class="ml-8">SSE 已连接</el-tag>
                  <el-tag v-else size="small" type="info" class="ml-8">SSE 未连接</el-tag>
                </span>
                <div>
                  <el-button
                    v-if="canCancel"
                    size="small"
                    type="danger"
                    plain
                    :icon="CircleClose"
                    :loading="cancelling"
                    @click="doCancelRun"
                  >
                    取消 Run
                  </el-button>
                  <el-button size="small" :icon="Refresh" @click="selectRun(selectedRun.id)">重载</el-button>
                </div>
              </div>
            </template>

            <!-- 待审批计划 -->
            <el-alert
              v-if="pendingPlan"
              type="warning"
              :closable="false"
              class="plan-alert"
            >
              <template #title>
                计划待审批 · taskId={{ pendingPlan.taskId }} · revision={{ pendingPlan.revision ?? 0 }}
              </template>
              <div class="plan-body">
                <pre class="plan-md">{{ pendingPlan.planMd || '(无计划正文)' }}</pre>
                <div class="plan-actions">
                  <el-button
                    type="primary"
                    :icon="Check"
                    :loading="planBusy"
                    :disabled="!pendingPlan.canonicalHash && !pendingPlan.taskId"
                    @click="approvePlan"
                  >
                    批准计划
                  </el-button>
                  <span v-if="pendingPlan.canonicalHash" class="hash-tip">
                    hash: {{ pendingPlan.canonicalHash.slice(0, 16) }}…
                  </span>
                </div>
              </div>
            </el-alert>

            <!-- 待审批工具 -->
            <div v-if="pendingApprovals.length" class="approval-list">
              <el-card
                v-for="a in pendingApprovals"
                :key="a.id"
                shadow="hover"
                class="approval-card"
              >
                <div class="approval-head">
                  <el-tag type="warning" size="small">待审批</el-tag>
                  <strong>{{ a.toolName }}</strong>
                </div>
                <div class="approval-args">
                  <code v-if="a.argumentsJson">{{ a.argumentsJson.slice(0, 200) }}</code>
                  <span v-else class="muted">无参数快照</span>
                </div>
                <div v-if="a.argumentsSha256" class="approval-sha muted">
                  sha256: {{ a.argumentsSha256.slice(0, 16) }}…
                </div>
                <div class="approval-actions">
                  <el-button
                    type="primary"
                    size="small"
                    :icon="Check"
                    :loading="approvalBusy[a.id]"
                    @click="resolveApproval(a, 'APPROVE')"
                  >
                    批准
                  </el-button>
                  <el-button
                    type="danger"
                    size="small"
                    plain
                    :icon="Close"
                    :loading="approvalBusy[a.id]"
                    @click="resolveApproval(a, 'DENY')"
                  >
                    拒绝
                  </el-button>
                </div>
              </el-card>
            </div>

            <!-- 事件时间线 -->
            <el-timeline v-if="events.length" class="event-timeline">
              <el-timeline-item
                v-for="ev in events"
                :key="`${ev.sequence}-${ev.eventId || ev.id || ''}`"
                :timestamp="`#${ev.sequence} ${ev.createdTime || ''}`"
                :type="eventTagType(ev.type)"
                placement="top"
              >
                <div class="event-type">
                  <el-tag size="small" :type="eventTagType(ev.type)">{{ ev.type }}</el-tag>
                </div>
                <div v-if="formatPayload(ev)" class="event-payload">
                  <code>{{ formatPayload(ev) }}</code>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无事件，创建 Run 后将实时推送" :image-size="72" />
          </el-card>
        </template>
      </div>
    </div>

    <!-- 新建会话对话框 -->
    <el-dialog v-model="createSessionVisible" title="新建 Coding 会话" width="520px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="标题" required>
          <el-input v-model="sessionForm.title" placeholder="例如：低代码模块重构" maxlength="120" />
        </el-form-item>
        <el-form-item label="工作区路径">
          <el-input v-model="sessionForm.workspacePath" placeholder="可选，例如 D:/workspaces/demo" />
        </el-form-item>
        <el-form-item label="模型">
          <el-input v-model="sessionForm.model" placeholder="可选，留空使用默认" />
        </el-form-item>
        <el-form-item label="权限模式">
          <el-select v-model="sessionForm.permissionMode" style="width: 100%">
            <el-option
              v-for="opt in HARNESS_PERMISSION_MODE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审批策略">
          <el-select v-model="sessionForm.approvalPolicy" style="width: 100%">
            <el-option
              v-for="opt in HARNESS_APPROVAL_POLICY_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createSessionVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSessionSaving" @click="submitCreateSession">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.harness-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.head-title {
  margin: 0 0 4px;
  font-size: 18px;
}
.head-sub {
  margin: 0;
  color: var(--yt-text-secondary, #909399);
  font-size: 12px;
}
.head-actions {
  display: flex;
  gap: 8px;
}
.harness-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
  align-items: start;
}
.session-panel {
  min-height: 420px;
}
.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 640px;
  overflow: auto;
}
.session-item {
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  padding: 10px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.session-item:hover {
  border-color: var(--el-color-primary-light-5);
}
.session-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.session-item.deleted {
  opacity: 0.55;
}
.session-title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;
  margin-bottom: 6px;
}
.pin-icon {
  color: var(--el-color-warning);
}
.title-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--yt-text-secondary, #909399);
}
.meta-model {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-actions {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
}
.main-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ml-8 {
  margin-left: 8px;
}
.plan-alert {
  margin-bottom: 12px;
}
.plan-body {
  margin-top: 8px;
}
.plan-md {
  margin: 0 0 8px;
  padding: 8px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 6px;
  max-height: 180px;
  overflow: auto;
  white-space: pre-wrap;
  font-size: 12px;
}
.plan-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hash-tip {
  font-size: 12px;
  color: var(--yt-text-secondary, #909399);
}
.approval-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}
.approval-card {
  border-left: 3px solid var(--el-color-warning);
}
.approval-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.approval-args {
  font-size: 12px;
  margin-bottom: 4px;
  word-break: break-all;
}
.approval-sha {
  font-size: 11px;
  margin-bottom: 8px;
}
.approval-actions {
  display: flex;
  gap: 8px;
}
.muted {
  color: var(--yt-text-secondary, #909399);
}
.event-timeline {
  padding-left: 4px;
}
.event-type {
  margin-bottom: 4px;
}
.event-payload code {
  display: block;
  font-size: 12px;
  background: var(--el-fill-color-light, #f5f7fa);
  padding: 6px 8px;
  border-radius: 4px;
  word-break: break-all;
  white-space: pre-wrap;
}
@media (max-width: 1100px) {
  .harness-layout {
    grid-template-columns: 1fr;
  }
}
</style>
