<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageAiPrompts,
  getAiPrompt,
  saveAiPromptDraft,
  publishAiPrompt,
  disableAiPrompt,
  type AiPromptTemplate,
} from '@/api/ai-governance'
import type { PageResult } from '@/api/types'

const { t } = useI18n()

/**
 * Prompt 治理列表页。设计来源: 37-AI治理与评测设计 (GA2-45 v1.0) 能力域 1。
 * 三态管理: DRAFT → PUBLISHED → DISABLED。
 * 草稿可编辑; 已发布仅可禁用; 已禁用不可再编辑或发布。
 */
const loading = ref(false)
const tableData = ref<AiPromptTemplate[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选条件
const filterTemplateCode = ref('')
const filterScenario = ref('')
const filterStatus = ref('')

const STATUS_OPTIONS = [
  { label: t('aiGovernance.prompt.status.draft'), value: 'DRAFT' },
  { label: t('aiGovernance.prompt.status.published'), value: 'PUBLISHED' },
  { label: t('aiGovernance.prompt.status.disabled'), value: 'DISABLED' },
]

const SCENARIO_OPTIONS = [
  { label: t('aiGovernance.prompt.scenario.ragQa'), value: 'RAG_QA' },
  { label: t('aiGovernance.prompt.scenario.summary'), value: 'SUMMARY' },
  { label: t('aiGovernance.prompt.scenario.draftGeneration'), value: 'DRAFT_GENERATION' },
  { label: t('aiGovernance.prompt.scenario.sqlDraft'), value: 'SQL_DRAFT' },
  { label: t('aiGovernance.prompt.scenario.toolOrchestration'), value: 'TOOL_ORCHESTRATION' },
]

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function statusTagType(status: string): TagType {
  const map: Record<string, TagType> = {
    DRAFT: 'info',
    PUBLISHED: 'success',
    DISABLED: 'danger',
  }
  return map[status] || 'info'
}

function statusLabel(status: string): string {
  return STATUS_OPTIONS.find((s) => s.value === status)?.label || status
}

function scenarioLabel(scenario: string): string {
  return SCENARIO_OPTIONS.find((s) => s.value === scenario)?.label || scenario
}

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<AiPromptTemplate> = await pageAiPrompts({
      page: currentPage.value,
      size: pageSize.value,
      templateCode: filterTemplateCode.value || undefined,
      scenario: filterScenario.value || undefined,
      status: filterStatus.value || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filterTemplateCode.value = ''
  filterScenario.value = ''
  filterStatus.value = ''
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

// ============ 草稿编辑弹窗 ============
const editDialogVisible = ref(false)
const editForm = ref<AiPromptTemplate>(emptyDraft())

function emptyDraft(): AiPromptTemplate {
  return {
    templateCode: '',
    scenario: 'RAG_QA',
    promptText: '',
    safetyRules: '',
    evaluationSetCode: '',
    status: 'DRAFT',
    remark: '',
    inputSchema: '',
    outputSchema: '',
  }
}

function openCreateDialog() {
  editForm.value = emptyDraft()
  editDialogVisible.value = true
}

async function openEditDialog(row: AiPromptTemplate) {
  if (row.status !== 'DRAFT') {
    ElMessage.warning(t('aiGovernance.msg.draftOnlyEditable'))
    return
  }
  loading.value = true
  try {
    const detail = await getAiPrompt(row.id as string)
    editForm.value = { ...detail }
    editDialogVisible.value = true
  } finally {
    loading.value = false
  }
}

async function handleSaveDraft() {
  if (!editForm.value.templateCode.trim()) {
    ElMessage.warning(t('aiGovernance.msg.templateCodeRequired'))
    return
  }
  if (!editForm.value.promptText.trim()) {
    ElMessage.warning(t('aiGovernance.msg.promptContentRequired'))
    return
  }
  await saveAiPromptDraft(editForm.value)
  ElMessage.success(t('aiGovernance.msg.draftSaved'))
  editDialogVisible.value = false
  loadData()
}

async function handlePublish(row: AiPromptTemplate) {
  if (row.status !== 'DRAFT') {
    ElMessage.warning(t('aiGovernance.msg.draftOnlyPublishable'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('aiGovernance.msg.confirmPublishPrompt', { templateCode: row.templateCode, version: row.versionNo || 1 }),
      t('aiGovernance.msg.publishConfirmTitle'),
      { confirmButtonText: t('aiGovernance.msg.publishButton'), cancelButtonText: t('aiGovernance.common.action.cancel'), type: 'warning' }
    )
    await publishAiPrompt(row.id as string, row.version || 1)
    ElMessage.success(t('aiGovernance.msg.publishSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

async function handleDisable(row: AiPromptTemplate) {
  if (row.status !== 'PUBLISHED') {
    ElMessage.warning(t('aiGovernance.msg.publishedOnlyDisableable'))
    return
  }
  try {
    const { value: reason } = await ElMessageBox.prompt(t('aiGovernance.msg.disablePromptReason'), t('aiGovernance.msg.disableConfirmTitle'), {
      confirmButtonText: t('aiGovernance.msg.disableButton'),
      cancelButtonText: t('common.action.cancel'),
      inputType: 'textarea',
      inputPlaceholder: t('aiGovernance.msg.disablePlaceholder'),
      inputValidator: (v: string) => !v || v.length <= 200 || t('aiGovernance.msg.disableReasonTooLong'),
    })
    await disableAiPrompt(row.id as string, row.version || 1, reason || undefined)
    ElMessage.success(t('aiGovernance.msg.disableSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('aiGovernance.prompt.page.title') }}</h2>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('aiGovernance.prompt.field.templateCode')">
          <el-input v-model="filterTemplateCode" :placeholder="$t('aiGovernance.prompt.placeholder.templateCode')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.prompt.field.scenario')">
          <el-select v-model="filterScenario" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 160px">
            <el-option v-for="s in SCENARIO_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.common.field.status')">
          <el-select v-model="filterStatus" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('aiGovernance.common.action.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('aiGovernance.common.action.reset') }}</el-button>
          <el-button type="success" @click="openCreateDialog">{{ $t('aiGovernance.prompt.action.createDraft') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="templateCode" :label="$t('aiGovernance.prompt.field.templateCode')" width="180" />
      <el-table-column prop="scenario" :label="$t('aiGovernance.prompt.field.scenario')" width="140">
        <template #default="{ row }">
          {{ scenarioLabel(row.scenario) }}
        </template>
      </el-table-column>
      <el-table-column prop="versionNo" :label="$t('aiGovernance.common.field.version')" width="80" align="center" />
      <el-table-column prop="status" :label="$t('aiGovernance.common.field.status')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status as string)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="evaluationSetCode" :label="$t('aiGovernance.prompt.field.evalSet')" width="140" show-overflow-tooltip />
      <el-table-column prop="publishedTime" :label="$t('aiGovernance.prompt.field.publishedTime')" width="170">
        <template #default="{ row }">
          {{ row.publishedTime || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="publishedBy" :label="$t('aiGovernance.prompt.field.publishedBy')" width="120" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.publishedBy || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="remark" :label="$t('aiGovernance.common.field.remark')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="$t('aiGovernance.common.field.action')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'DRAFT'"
            type="primary"
            link
            size="small"
            @click="openEditDialog(row as AiPromptTemplate)"
          >{{ $t('aiGovernance.common.action.edit') }}</el-button>
          <el-button
            v-if="row.status === 'DRAFT'"
            type="success"
            link
            size="small"
            @click="handlePublish(row as AiPromptTemplate)"
          >{{ $t('aiGovernance.prompt.action.publish') }}</el-button>
          <el-button
            v-if="row.status === 'PUBLISHED'"
            type="danger"
            link
            size="small"
            @click="handleDisable(row as AiPromptTemplate)"
          >{{ $t('aiGovernance.prompt.action.disable') }}</el-button>
          <span v-if="row.status === 'DISABLED'" class="text-muted">{{ $t('aiGovernance.prompt.text.disabled') }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />

    <!-- 草稿编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="editForm.id ? $t('aiGovernance.prompt.dialog.editTitle') : $t('aiGovernance.prompt.dialog.createTitle')" width="720px">
      <el-form label-width="120px" size="small">
        <el-form-item :label="$t('aiGovernance.prompt.field.templateCode')" required>
          <el-input
            v-model="editForm.templateCode"
            :placeholder="$t('aiGovernance.prompt.placeholder.templateCodeExample')"
            maxlength="64"
            show-word-limit
            :disabled="!!editForm.id"
          />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.prompt.field.scenario')" required>
          <el-select v-model="editForm.scenario" style="width: 100%">
            <el-option v-for="s in SCENARIO_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.prompt.field.promptContent')" required>
          <el-input
            v-model="editForm.promptText"
            type="textarea"
            :rows="8"
            :placeholder="$t('aiGovernance.prompt.placeholder.promptContent')"
            maxlength="8000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.prompt.field.safetyRules')">
          <el-input
            v-model="editForm.safetyRules"
            type="textarea"
            :rows="3"
            :placeholder="$t('aiGovernance.prompt.placeholder.safetyRules')"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.prompt.field.evalSetCode')">
          <el-input v-model="editForm.evaluationSetCode" :placeholder="$t('aiGovernance.prompt.placeholder.evalSetCode')" maxlength="64" />
        </el-form-item>
        <el-form-item label="Input Schema">
          <el-input v-model="editForm.inputSchema" type="textarea" :rows="2" :placeholder="$t('aiGovernance.prompt.placeholder.jsonSchemaOptional')" />
        </el-form-item>
        <el-form-item label="Output Schema">
          <el-input v-model="editForm.outputSchema" type="textarea" :rows="2" :placeholder="$t('aiGovernance.prompt.placeholder.jsonSchemaOptional')" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.common.field.remark')">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ $t('aiGovernance.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveDraft">{{ $t('aiGovernance.prompt.action.saveDraft') }}</el-button>
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
.filter-card {
  margin-bottom: 16px;
}
.text-muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
