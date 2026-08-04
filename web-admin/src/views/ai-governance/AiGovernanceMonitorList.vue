<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { TabPaneName } from 'element-plus'
import {
  pageAiQuotas,
  saveAiQuota,
  pageAiFeedbacks,
  handleAiFeedback,
  submitAiFeedback,
  pageAiEvalDatasets,
  pageAiEvalRuns,
  triggerAiEvalRun,
  pageAiEvalResults,
  getAiGovernanceStats,
  type AiCostQuota,
  type AiFeedback,
  type AiEvalDataset,
  type AiEvalRun,
  type AiEvalResult,
  type AiGovernanceStats,
  type SaveQuotaRequest,
  type SubmitFeedbackRequest,
  type TriggerEvalRunRequest,
} from '@/api/ai-governance'
import type { PageResult } from '@/api/types'

const { t } = useI18n()

/**
 * AI 治理监控中心。设计来源: 37-AI治理与评测设计 (GA2-45 v1.0) 能力域 3-5 + 监控统计。
 * 顶部统计卡片 + 5 个 Tab: 成本额度 / 用户反馈 / 评测样本 / 评测运行 / 评测结果。
 */
const activeTab = ref('quotas')
const stats = ref<AiGovernanceStats>({})

async function loadStats() {
  stats.value = await getAiGovernanceStats()
}

// ============ 1. 成本额度 ============
const quotaLoading = ref(false)
const quotaData = ref<AiCostQuota[]>([])
const quotaTotal = ref(0)
const quotaPage = ref(1)
const quotaSize = ref(10)
const filterQuotaScope = ref('')
const filterScopeKey = ref('')
const quotaDialogVisible = ref(false)
const quotaForm = ref<SaveQuotaRequest>(emptyQuota())

const QUOTA_SCOPE_OPTIONS = [
  { label: t('aiGovernance.monitor.scope.tenant'), value: 'TENANT' },
  { label: t('aiGovernance.monitor.scope.user'), value: 'USER' },
  { label: t('aiGovernance.monitor.scope.scenario'), value: 'SCENARIO' },
]

function emptyQuota(): SaveQuotaRequest {
  return {
    quotaScope: 'TENANT',
    scopeKey: 'default',
    modelCode: '',
    dailyTokenLimit: 1000000,
    dailyCostLimit: 100,
    singleCallTokenLimit: 8000,
    currency: 'CNY',
    enabled: true,
  }
}

async function loadQuotas() {
  quotaLoading.value = true
  try {
    const res: PageResult<AiCostQuota> = await pageAiQuotas({
      page: quotaPage.value,
      size: quotaSize.value,
      quotaScope: filterQuotaScope.value || undefined,
      scopeKey: filterScopeKey.value || undefined,
    })
    quotaData.value = res.records || []
    quotaTotal.value = res.total || 0
  } finally {
    quotaLoading.value = false
  }
}

function openQuotaDialog() {
  quotaForm.value = emptyQuota()
  quotaDialogVisible.value = true
}

async function handleSaveQuota() {
  if (!quotaForm.value.quotaScope) {
    ElMessage.warning(t('aiGovernance.msg.quotaScopeRequired'))
    return
  }
  if (!quotaForm.value.scopeKey?.trim()) {
    ElMessage.warning(t('aiGovernance.msg.scopeKeyRequired'))
    return
  }
  await saveAiQuota(quotaForm.value)
  ElMessage.success(t('aiGovernance.msg.saveSuccess'))
  quotaDialogVisible.value = false
  loadQuotas()
  loadStats()
}

function handleQuotaPageChange(p: number) {
  quotaPage.value = p
  loadQuotas()
}

// ============ 2. 用户反馈 ============
const feedbackLoading = ref(false)
const feedbackData = ref<AiFeedback[]>([])
const feedbackTotal = ref(0)
const feedbackPage = ref(1)
const feedbackSize = ref(10)
const filterFeedbackType = ref('')
const filterUnhandledOnly = ref(false)
const handleDialogVisible = ref(false)
const handleForm = ref<{ id: string; handleResult: string }>({ id: '', handleResult: '' })
const feedbackDialogVisible = ref(false)
const feedbackForm = ref<SubmitFeedbackRequest>(emptyFeedback())

const FEEDBACK_TYPE_OPTIONS = [
  { label: t('aiGovernance.monitor.feedbackType.helpful'), value: 'HELPFUL' },
  { label: t('aiGovernance.monitor.feedbackType.notHelpful'), value: 'NOT_HELPFUL' },
  { label: t('aiGovernance.monitor.feedbackType.inaccurate'), value: 'INACCURATE' },
  { label: t('aiGovernance.monitor.feedbackType.citationError'), value: 'CITATION_ERROR' },
  { label: t('aiGovernance.monitor.feedbackType.formatError'), value: 'FORMAT_ERROR' },
  { label: t('aiGovernance.monitor.feedbackType.risky'), value: 'RISKY' },
]

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function feedbackTagType(type: string): TagType {
  const map: Record<string, TagType> = {
    HELPFUL: 'success',
    NOT_HELPFUL: 'info',
    INACCURATE: 'warning',
    CITATION_ERROR: 'warning',
    FORMAT_ERROR: 'warning',
    RISKY: 'danger',
  }
  return map[type] || 'info'
}

function feedbackLabel(type: string): string {
  return FEEDBACK_TYPE_OPTIONS.find((f) => f.value === type)?.label || type
}

function emptyFeedback(): SubmitFeedbackRequest {
  return {
    feedbackType: 'HELPFUL',
    targetType: 'ANSWER',
    targetId: '',
    conversationId: '',
    messageId: '',
    scenario: '',
    modelCode: '',
    rating: 5,
    tagsJson: '',
    commentText: '',
  }
}

async function loadFeedbacks() {
  feedbackLoading.value = true
  try {
    const res: PageResult<AiFeedback> = await pageAiFeedbacks({
      page: feedbackPage.value,
      size: feedbackSize.value,
      feedbackType: filterFeedbackType.value || undefined,
      unhandledOnly: filterUnhandledOnly.value || undefined,
    })
    feedbackData.value = res.records || []
    feedbackTotal.value = res.total || 0
  } finally {
    feedbackLoading.value = false
  }
}

function openHandleDialog(row: AiFeedback) {
  handleForm.value = { id: row.id as string, handleResult: '' }
  handleDialogVisible.value = true
}

async function handleFeedbackSubmit() {
  if (!handleForm.value.handleResult.trim()) {
    ElMessage.warning(t('aiGovernance.msg.handleResultRequired'))
    return
  }
  await handleAiFeedback(handleForm.value.id, handleForm.value.handleResult)
  ElMessage.success(t('aiGovernance.msg.handleComplete'))
  handleDialogVisible.value = false
  loadFeedbacks()
  loadStats()
}

function openFeedbackDialog() {
  feedbackForm.value = emptyFeedback()
  feedbackDialogVisible.value = true
}

async function handleFeedbackCreate() {
  if (!feedbackForm.value.feedbackType) {
    ElMessage.warning(t('aiGovernance.msg.feedbackTypeRequired'))
    return
  }
  await submitAiFeedback(feedbackForm.value)
  ElMessage.success(t('aiGovernance.msg.feedbackSubmitted'))
  feedbackDialogVisible.value = false
  loadFeedbacks()
  loadStats()
}

function handleFeedbackPageChange(p: number) {
  feedbackPage.value = p
  loadFeedbacks()
}

// ============ 3. 评测样本集 ============
const datasetLoading = ref(false)
const datasetData = ref<AiEvalDataset[]>([])
const datasetTotal = ref(0)
const datasetPage = ref(1)
const datasetSize = ref(10)
const filterScenario = ref('')
const filterDifficulty = ref('')

const DATASET_SCENARIO_OPTIONS = [
  { label: t('aiGovernance.monitor.scenario.ragCore'), value: 'RAG_CORE' },
  { label: t('aiGovernance.monitor.scenario.generationLowcode'), value: 'GENERATION_LOWCODE' },
  { label: t('aiGovernance.monitor.scenario.sqlDraft'), value: 'SQL_DRAFT' },
  { label: t('aiGovernance.monitor.scenario.securityBoundary'), value: 'SECURITY_BOUNDARY' },
]

const DIFFICULTY_OPTIONS = [
  { label: t('aiGovernance.monitor.difficulty.easy'), value: 'EASY' },
  { label: t('aiGovernance.monitor.difficulty.medium'), value: 'MEDIUM' },
  { label: t('aiGovernance.monitor.difficulty.hard'), value: 'HARD' },
]

function scenarioLabel(s: string): string {
  return DATASET_SCENARIO_OPTIONS.find((o) => o.value === s)?.label || s
}

function difficultyLabel(d: string): string {
  return DIFFICULTY_OPTIONS.find((o) => o.value === d)?.label || d
}

async function loadDatasets() {
  datasetLoading.value = true
  try {
    const res: PageResult<AiEvalDataset> = await pageAiEvalDatasets({
      page: datasetPage.value,
      size: datasetSize.value,
      scenario: filterScenario.value || undefined,
      difficulty: filterDifficulty.value || undefined,
    })
    datasetData.value = res.records || []
    datasetTotal.value = res.total || 0
  } finally {
    datasetLoading.value = false
  }
}

function handleDatasetPageChange(p: number) {
  datasetPage.value = p
  loadDatasets()
}

// ============ 4. 评测运行 ============
const runLoading = ref(false)
const runData = ref<AiEvalRun[]>([])
const runTotal = ref(0)
const runPage = ref(1)
const runSize = ref(10)
const filterReleaseDecision = ref('')
const runDialogVisible = ref(false)
const runForm = ref<TriggerEvalRunRequest>(emptyRunForm())

const DECISION_OPTIONS = [
  { label: t('aiGovernance.monitor.decision.pending'), value: 'PENDING' },
  { label: t('aiGovernance.monitor.decision.passed'), value: 'PASSED' },
  { label: t('aiGovernance.monitor.decision.conditional'), value: 'CONDITIONAL' },
  { label: t('aiGovernance.monitor.decision.rejected'), value: 'REJECTED' },
]

function decisionLabel(d: string): string {
  return DECISION_OPTIONS.find((o) => o.value === d)?.label || d
}

function decisionTagType(d: string): TagType {
  const map: Record<string, TagType> = {
    PENDING: 'info',
    PASSED: 'success',
    CONDITIONAL: 'warning',
    REJECTED: 'danger',
  }
  return map[d] || 'info'
}

function emptyRunForm(): TriggerEvalRunRequest {
  return {
    appVersion: 'v1.0.0',
    promptVersion: 'v1',
    modelRouteVersion: 'default',
    kbVersion: 'v1',
    datasetFilter: '',
  }
}

async function loadRuns() {
  runLoading.value = true
  try {
    const res: PageResult<AiEvalRun> = await pageAiEvalRuns({
      page: runPage.value,
      size: runSize.value,
      releaseDecision: filterReleaseDecision.value || undefined,
    })
    runData.value = res.records || []
    runTotal.value = res.total || 0
  } finally {
    runLoading.value = false
  }
}

function openRunDialog() {
  runForm.value = emptyRunForm()
  runDialogVisible.value = true
}

async function handleTriggerRun() {
  await triggerAiEvalRun(runForm.value)
  ElMessage.success(t('aiGovernance.msg.evalRunTriggered'))
  runDialogVisible.value = false
  loadRuns()
  loadStats()
}

function handleRunPageChange(p: number) {
  runPage.value = p
  loadRuns()
}

// ============ 5. 评测结果 ============
const resultLoading = ref(false)
const resultData = ref<AiEvalResult[]>([])
const resultTotal = ref(0)
const resultPage = ref(1)
const resultSize = ref(10)
const selectedRunId = ref('')
const filterResultScenario = ref('')
const filterFailedOnly = ref(false)

async function loadResults() {
  if (!selectedRunId.value) {
    ElMessage.warning(t('aiGovernance.msg.selectRunBatchRequired'))
    return
  }
  resultLoading.value = true
  try {
    const res: PageResult<AiEvalResult> = await pageAiEvalResults(selectedRunId.value, {
      page: resultPage.value,
      size: resultSize.value,
      scenario: filterResultScenario.value || undefined,
      failedOnly: filterFailedOnly.value || undefined,
    })
    resultData.value = res.records || []
    resultTotal.value = res.total || 0
  } finally {
    resultLoading.value = false
  }
}

async function selectRun(row: AiEvalRun) {
  selectedRunId.value = row.id as string
  activeTab.value = 'results'
  resultPage.value = 1
  await loadResults()
}

function handleResultPageChange(p: number) {
  resultPage.value = p
  loadResults()
}

// ============ Tab 切换加载 ============
function handleTabChange(tab: TabPaneName) {
  const tabName = String(tab)
  if (tabName === 'quotas' && quotaData.value.length === 0) loadQuotas()
  if (tabName === 'feedbacks' && feedbackData.value.length === 0) loadFeedbacks()
  if (tabName === 'datasets' && datasetData.value.length === 0) loadDatasets()
  if (tabName === 'runs' && runData.value.length === 0) loadRuns()
  if (tabName === 'results' && selectedRunId.value) loadResults()
}

onMounted(() => {
  loadStats()
  loadQuotas()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('aiGovernance.monitor.page.title') }}</h2>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ $t('aiGovernance.monitor.stat.totalPrompts') }}</div>
          <div class="stat-value">{{ stats.totalPrompts ?? '-' }}</div>
          <div class="stat-sub">{{ $t('aiGovernance.monitor.stat.publishedPrefix') }}{{ stats.publishedPrompts ?? 0 }}{{ $t('aiGovernance.monitor.stat.draftPrefix') }}{{ stats.draftPrompts ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ $t('aiGovernance.monitor.stat.totalTools') }}</div>
          <div class="stat-value">{{ stats.totalTools ?? '-' }}</div>
          <div class="stat-sub">{{ $t('aiGovernance.monitor.stat.enabledPrefix') }}{{ stats.enabledTools ?? 0 }}{{ $t('aiGovernance.monitor.stat.forbiddenPrefix') }}{{ stats.forbiddenTools ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ $t('aiGovernance.monitor.stat.todayCallCount') }}</div>
          <div class="stat-value">{{ stats.todayCallCount ?? '-' }}</div>
          <div class="stat-sub">{{ stats.todayTotalTokens ?? 0 }} tokens / ¥{{ stats.todayTotalCost ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ $t('aiGovernance.monitor.stat.feedback') }}</div>
          <div class="stat-value">{{ stats.totalFeedbacks ?? '-' }}</div>
          <div class="stat-sub">{{ $t('aiGovernance.monitor.stat.riskPrefix') }}{{ stats.riskyFeedbacks ?? 0 }}{{ $t('aiGovernance.monitor.stat.unhandledPrefix') }}{{ stats.unhandledFeedbacks ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ $t('aiGovernance.monitor.stat.evalCases') }}</div>
          <div class="stat-value">{{ stats.totalEvalCases ?? '-' }}</div>
          <div class="stat-sub">{{ $t('aiGovernance.monitor.stat.enabledPrefix') }}{{ stats.enabledEvalCases ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ $t('aiGovernance.monitor.stat.evalRuns') }}</div>
          <div class="stat-value">{{ stats.totalEvalRuns ?? '-' }}</div>
          <div class="stat-sub">{{ $t('aiGovernance.monitor.stat.passedPrefix') }}{{ stats.passedEvalRuns ?? 0 }}{{ $t('aiGovernance.monitor.stat.rejectedPrefix') }}{{ stats.failedEvalRuns ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 5 大能力域 Tab -->
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 1. 成本额度 -->
      <el-tab-pane :label="$t('aiGovernance.monitor.tab.quotas')" name="quotas">
        <el-card shadow="never" class="filter-card">
          <el-form :inline="true" size="small">
            <el-form-item :label="$t('aiGovernance.monitor.field.scope')">
              <el-select v-model="filterQuotaScope" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 120px">
                <el-option v-for="s in QUOTA_SCOPE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="Key">
              <el-input v-model="filterScopeKey" :placeholder="$t('aiGovernance.monitor.placeholder.scopeKey')" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="quotaPage = 1; loadQuotas()">{{ $t('aiGovernance.common.action.query') }}</el-button>
              <el-button type="success" @click="openQuotaDialog">{{ $t('aiGovernance.monitor.action.createQuota') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-table v-loading="quotaLoading" :data="quotaData" border stripe>
          <el-table-column prop="quotaScope" :label="$t('aiGovernance.monitor.field.scope')" width="100">
            <template #default="{ row }">
              {{ QUOTA_SCOPE_OPTIONS.find((s) => s.value === row.quotaScope)?.label || row.quotaScope }}
            </template>
          </el-table-column>
          <el-table-column prop="scopeKey" label="Key" width="140" />
          <el-table-column prop="modelCode" :label="$t('aiGovernance.monitor.field.model')" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ row.modelCode || $t('aiGovernance.monitor.text.all') }}</template>
          </el-table-column>
          <el-table-column prop="dailyTokenLimit" :label="$t('aiGovernance.monitor.field.dailyTokenLimit')" width="130" align="right" />
          <el-table-column prop="dailyCostLimit" :label="$t('aiGovernance.monitor.field.dailyCostLimit')" width="120" align="right">
            <template #default="{ row }">¥{{ row.dailyCostLimit ?? 0 }} {{ row.currency || '' }}</template>
          </el-table-column>
          <el-table-column prop="singleCallTokenLimit" :label="$t('aiGovernance.monitor.field.singleCallToken')" width="120" align="right" />
          <el-table-column prop="enabled" :label="$t('aiGovernance.common.field.enabled')" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? $t('aiGovernance.common.text.yes') : $t('aiGovernance.common.text.no') }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 16px; justify-content: flex-end"
          v-model:current-page="quotaPage"
          v-model:page-size="quotaSize"
          :total="quotaTotal"
          layout="total, prev, pager, next"
          @current-change="handleQuotaPageChange"
        />
      </el-tab-pane>

      <!-- 2. 用户反馈 -->
      <el-tab-pane :label="$t('aiGovernance.monitor.tab.feedbacks')" name="feedbacks">
        <el-card shadow="never" class="filter-card">
          <el-form :inline="true" size="small">
            <el-form-item :label="$t('aiGovernance.monitor.field.feedbackType')">
              <el-select v-model="filterFeedbackType" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 140px">
                <el-option v-for="t in FEEDBACK_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="">
              <el-checkbox v-model="filterUnhandledOnly">{{ $t('aiGovernance.monitor.filter.unhandledOnly') }}</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="feedbackPage = 1; loadFeedbacks()">{{ $t('aiGovernance.common.action.query') }}</el-button>
              <el-button type="success" @click="openFeedbackDialog">{{ $t('aiGovernance.monitor.action.submitFeedback') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-table v-loading="feedbackLoading" :data="feedbackData" border stripe>
          <el-table-column prop="feedbackType" :label="$t('aiGovernance.monitor.field.type')" width="100">
            <template #default="{ row }">
              <el-tag :type="feedbackTagType(row.feedbackType)" size="small">{{ feedbackLabel(row.feedbackType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="targetType" :label="$t('aiGovernance.monitor.field.target')" width="100" />
          <el-table-column prop="scenario" :label="$t('aiGovernance.monitor.field.scenario')" width="120" show-overflow-tooltip />
          <el-table-column prop="rating" :label="$t('aiGovernance.monitor.field.rating')" width="80" align="center" />
          <el-table-column prop="commentText" :label="$t('aiGovernance.monitor.field.comment')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="traceId" label="TraceId" width="160" show-overflow-tooltip />
          <el-table-column prop="handled" :label="$t('aiGovernance.common.field.status')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.handled ? 'success' : 'warning'" size="small">
                {{ row.handled ? $t('aiGovernance.monitor.text.handled') : $t('aiGovernance.monitor.text.pending') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" :label="$t('aiGovernance.common.field.createdTime')" width="170" />
          <el-table-column :label="$t('aiGovernance.common.field.action')" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="!row.handled"
                type="primary"
                link
                size="small"
                @click="openHandleDialog(row as AiFeedback)"
              >{{ $t('aiGovernance.monitor.action.handle') }}</el-button>
              <span v-else class="text-muted">{{ row.handledBy }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 16px; justify-content: flex-end"
          v-model:current-page="feedbackPage"
          v-model:page-size="feedbackSize"
          :total="feedbackTotal"
          layout="total, prev, pager, next"
          @current-change="handleFeedbackPageChange"
        />
      </el-tab-pane>

      <!-- 3. 评测样本集 -->
      <el-tab-pane :label="$t('aiGovernance.monitor.tab.datasets')" name="datasets">
        <el-card shadow="never" class="filter-card">
          <el-form :inline="true" size="small">
            <el-form-item :label="$t('aiGovernance.monitor.field.scenario')">
              <el-select v-model="filterScenario" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 160px">
                <el-option v-for="s in DATASET_SCENARIO_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('aiGovernance.monitor.field.difficulty')">
              <el-select v-model="filterDifficulty" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 120px">
                <el-option v-for="d in DIFFICULTY_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="datasetPage = 1; loadDatasets()">{{ $t('aiGovernance.common.action.query') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-table v-loading="datasetLoading" :data="datasetData" border stripe>
          <el-table-column prop="caseId" label="Case ID" width="160" show-overflow-tooltip />
          <el-table-column prop="scenario" :label="$t('aiGovernance.monitor.field.scenario')" width="140">
            <template #default="{ row }">{{ scenarioLabel(row.scenario) }}</template>
          </el-table-column>
          <el-table-column prop="difficulty" :label="$t('aiGovernance.monitor.field.difficulty')" width="80" align="center">
            <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column prop="question" :label="$t('aiGovernance.monitor.field.question')" min-width="280" show-overflow-tooltip />
          <el-table-column prop="expectedRefusal" :label="$t('aiGovernance.monitor.field.expectedRefusal')" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.expectedRefusal" type="danger" size="small">{{ $t('aiGovernance.monitor.text.refuse') }}</el-tag>
              <el-tag v-else type="success" size="small">{{ $t('aiGovernance.monitor.text.answer') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="expectedErrorCode" :label="$t('aiGovernance.monitor.field.expectedErrorCode')" width="120" />
          <el-table-column prop="enabled" :label="$t('aiGovernance.common.field.enabled')" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? $t('aiGovernance.common.text.yes') : $t('aiGovernance.common.text.no') }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 16px; justify-content: flex-end"
          v-model:current-page="datasetPage"
          v-model:page-size="datasetSize"
          :total="datasetTotal"
          layout="total, prev, pager, next"
          @current-change="handleDatasetPageChange"
        />
      </el-tab-pane>

      <!-- 4. 评测运行 -->
      <el-tab-pane :label="$t('aiGovernance.monitor.tab.runs')" name="runs">
        <el-card shadow="never" class="filter-card">
          <el-form :inline="true" size="small">
            <el-form-item :label="$t('aiGovernance.monitor.field.decision')">
              <el-select v-model="filterReleaseDecision" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 140px">
                <el-option v-for="d in DECISION_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="runPage = 1; loadRuns()">{{ $t('aiGovernance.common.action.query') }}</el-button>
              <el-button type="success" @click="openRunDialog">{{ $t('aiGovernance.monitor.action.triggerEval') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-table v-loading="runLoading" :data="runData" border stripe>
          <el-table-column prop="runNo" :label="$t('aiGovernance.monitor.field.runNo')" width="180" show-overflow-tooltip />
          <el-table-column prop="appVersion" :label="$t('aiGovernance.monitor.field.appVersion')" width="120" />
          <el-table-column prop="promptVersion" :label="$t('aiGovernance.monitor.field.promptVersion')" width="120" />
          <el-table-column prop="totalCases" :label="$t('aiGovernance.monitor.field.totalCases')" width="80" align="center" />
          <el-table-column prop="passedCases" :label="$t('aiGovernance.monitor.field.passed')" width="80" align="center" />
          <el-table-column prop="failedCases" :label="$t('aiGovernance.monitor.field.failed')" width="80" align="center" />
          <el-table-column prop="recallAtK" label="Recall@K" width="100" align="center">
            <template #default="{ row }">{{ row.recallAtK != null ? (Number(row.recallAtK) * 100).toFixed(1) + '%' : '-' }}</template>
          </el-table-column>
          <el-table-column prop="citationAccuracy" :label="$t('aiGovernance.monitor.field.citationAccuracy')" width="110" align="center">
            <template #default="{ row }">{{ row.citationAccuracy != null ? (Number(row.citationAccuracy) * 100).toFixed(1) + '%' : '-' }}</template>
          </el-table-column>
          <el-table-column prop="refusalAccuracy" :label="$t('aiGovernance.monitor.field.refusalAccuracy')" width="110" align="center">
            <template #default="{ row }">{{ row.refusalAccuracy != null ? (Number(row.refusalAccuracy) * 100).toFixed(1) + '%' : '-' }}</template>
          </el-table-column>
          <el-table-column prop="releaseDecision" :label="$t('aiGovernance.monitor.field.decision')" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="decisionTagType(row.releaseDecision)" size="small">{{ decisionLabel(row.releaseDecision) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="triggeredBy" :label="$t('aiGovernance.monitor.field.triggeredBy')" width="120" show-overflow-tooltip />
          <el-table-column prop="finishedTime" :label="$t('aiGovernance.monitor.field.finishedTime')" width="170" />
          <el-table-column :label="$t('aiGovernance.common.field.action')" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="selectRun(row as AiEvalRun)">{{ $t('aiGovernance.monitor.action.viewResult') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 16px; justify-content: flex-end"
          v-model:current-page="runPage"
          v-model:page-size="runSize"
          :total="runTotal"
          layout="total, prev, pager, next"
          @current-change="handleRunPageChange"
        />
      </el-tab-pane>

      <!-- 5. 评测结果 -->
      <el-tab-pane :label="$t('aiGovernance.monitor.tab.results')" name="results">
        <el-card shadow="never" class="filter-card">
          <el-form :inline="true" size="small">
            <el-form-item :label="$t('aiGovernance.monitor.field.runBatch')">
              <el-input v-model="selectedRunId" :placeholder="$t('aiGovernance.monitor.placeholder.runBatch')" disabled style="width: 280px" />
            </el-form-item>
            <el-form-item :label="$t('aiGovernance.monitor.field.scenario')">
              <el-select v-model="filterResultScenario" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 160px">
                <el-option v-for="s in DATASET_SCENARIO_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="">
              <el-checkbox v-model="filterFailedOnly">{{ $t('aiGovernance.monitor.filter.failedOnly') }}</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="resultPage = 1; loadResults()">{{ $t('aiGovernance.common.action.query') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <el-table v-loading="resultLoading" :data="resultData" border stripe>
          <el-table-column prop="caseId" label="Case ID" width="160" show-overflow-tooltip />
          <el-table-column prop="scenario" :label="$t('aiGovernance.monitor.field.scenario')" width="140">
            <template #default="{ row }">{{ scenarioLabel(row.scenario) }}</template>
          </el-table-column>
          <el-table-column prop="isRefused" :label="$t('aiGovernance.monitor.field.isRefused')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isRefused ? 'warning' : 'success'" size="small">{{ row.isRefused ? $t('aiGovernance.monitor.text.refuse') : $t('aiGovernance.monitor.text.answer') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isPassed" :label="$t('aiGovernance.monitor.field.isPassed')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.isPassed ? 'success' : 'danger'" size="small">{{ row.isPassed ? $t('aiGovernance.monitor.text.pass') : $t('aiGovernance.monitor.text.fail') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="latencyMs" :label="$t('aiGovernance.monitor.field.latency')" width="100" align="right" />
          <el-table-column prop="costAmount" :label="$t('aiGovernance.monitor.field.cost')" width="100" align="right">
            <template #default="{ row }">¥{{ row.costAmount ?? 0 }}</template>
          </el-table-column>
          <el-table-column prop="tokenInput" :label="$t('aiGovernance.monitor.field.tokenInput')" width="100" align="right" />
          <el-table-column prop="tokenOutput" :label="$t('aiGovernance.monitor.field.tokenOutput')" width="100" align="right" />
          <el-table-column prop="errorCode" :label="$t('aiGovernance.monitor.field.errorCode')" width="120" />
          <el-table-column prop="failureReason" :label="$t('aiGovernance.monitor.field.failureReason')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="traceId" label="TraceId" width="160" show-overflow-tooltip />
        </el-table>
        <el-pagination
          style="margin-top: 16px; justify-content: flex-end"
          v-model:current-page="resultPage"
          v-model:page-size="resultSize"
          :total="resultTotal"
          layout="total, prev, pager, next"
          @current-change="handleResultPageChange"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- 成本额度编辑弹窗 -->
    <el-dialog v-model="quotaDialogVisible" :title="quotaForm.id ? $t('aiGovernance.monitor.dialog.editQuotaTitle') : $t('aiGovernance.monitor.dialog.createQuotaTitle')" width="560px">
      <el-form label-width="130px" size="small">
        <el-form-item :label="$t('aiGovernance.monitor.field.scope')" required>
          <el-select v-model="quotaForm.quotaScope" style="width: 100%">
            <el-option v-for="s in QUOTA_SCOPE_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Key" required>
          <el-input v-model="quotaForm.scopeKey" :placeholder="$t('aiGovernance.monitor.placeholder.scopeKeyValue')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.modelCode')">
          <el-input v-model="quotaForm.modelCode" :placeholder="$t('aiGovernance.monitor.placeholder.modelCode')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.dailyTokenLimit')">
          <el-input-number v-model="quotaForm.dailyTokenLimit" :min="0" :max="1000000000" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.dailyCostLimit')">
          <el-input-number v-model="quotaForm.dailyCostLimit" :min="0" :max="1000000" :precision="2" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.singleCallToken')">
          <el-input-number v-model="quotaForm.singleCallTokenLimit" :min="0" :max="1000000" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.currency')">
          <el-input v-model="quotaForm.currency" maxlength="8" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.common.field.enabled')">
          <el-switch v-model="quotaForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaDialogVisible = false">{{ $t('aiGovernance.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveQuota">{{ $t('aiGovernance.common.action.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 反馈处理弹窗 -->
    <el-dialog v-model="handleDialogVisible" :title="$t('aiGovernance.monitor.dialog.handleFeedbackTitle')" width="560px">
      <el-form label-width="100px" size="small">
        <el-form-item :label="$t('aiGovernance.monitor.field.handleResult')" required>
          <el-input
            v-model="handleForm.handleResult"
            type="textarea"
            :rows="4"
            :placeholder="$t('aiGovernance.monitor.placeholder.handleResult')"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">{{ $t('aiGovernance.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleFeedbackSubmit">{{ $t('aiGovernance.common.action.submit') }}</el-button>
      </template>
    </el-dialog>

    <!-- 提交反馈弹窗 -->
    <el-dialog v-model="feedbackDialogVisible" :title="$t('aiGovernance.monitor.dialog.submitFeedbackTitle')" width="560px">
      <el-form label-width="100px" size="small">
        <el-form-item :label="$t('aiGovernance.monitor.field.feedbackType')" required>
          <el-select v-model="feedbackForm.feedbackType" style="width: 100%">
            <el-option v-for="t in FEEDBACK_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.targetType')">
          <el-input v-model="feedbackForm.targetType" placeholder="ANSWER / TOOL / GENERATION" maxlength="32" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.targetId')">
          <el-input v-model="feedbackForm.targetId" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.scenario')">
          <el-input v-model="feedbackForm.scenario" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.rating')">
          <el-input-number v-model="feedbackForm.rating" :min="1" :max="5" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.comment')">
          <el-input v-model="feedbackForm.commentText" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackDialogVisible = false">{{ $t('aiGovernance.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleFeedbackCreate">{{ $t('aiGovernance.common.action.submit') }}</el-button>
      </template>
    </el-dialog>

    <!-- 触发评测弹窗 -->
    <el-dialog v-model="runDialogVisible" :title="$t('aiGovernance.monitor.dialog.triggerEvalTitle')" width="560px">
      <el-form label-width="130px" size="small">
        <el-form-item :label="$t('aiGovernance.monitor.field.appVersion')">
          <el-input v-model="runForm.appVersion" maxlength="32" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.promptVersion')">
          <el-input v-model="runForm.promptVersion" maxlength="32" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.modelRouteVersion')">
          <el-input v-model="runForm.modelRouteVersion" maxlength="32" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.kbVersion')">
          <el-input v-model="runForm.kbVersion" maxlength="32" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.monitor.field.datasetFilter')">
          <el-input v-model="runForm.datasetFilter" type="textarea" :rows="2" :placeholder="$t('aiGovernance.monitor.placeholder.datasetFilter')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialogVisible = false">{{ $t('aiGovernance.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleTriggerRun">{{ $t('aiGovernance.monitor.action.triggerRun') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 600;
}
.stats-row {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--el-color-primary);
  margin: 4px 0;
}
.stat-sub {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}
.filter-card {
  margin-bottom: 16px;
}
.text-muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
