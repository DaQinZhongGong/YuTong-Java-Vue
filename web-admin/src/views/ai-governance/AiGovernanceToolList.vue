<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageAiTools,
  getAiTool,
  saveAiTool,
  toggleAiTool,
  validateAiTool,
  type AiToolRegistry,
  type SaveToolRequest,
} from '@/api/ai-governance'
import type { PageResult } from '@/api/types'

const { t } = useI18n()

/**
 * AI 工具注册列表页。设计来源: 37-AI治理与评测设计 (GA2-45 v1.0) 能力域 2。
 * 维护白名单 + 禁止清单; 6 个禁止工具 (execute_sql/delete_data/update_config/auto_approve/publish_lowcode_page/overwrite_source_code) 永远不可启用。
 */
const loading = ref(false)
const tableData = ref<AiToolRegistry[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选
const filterToolName = ref('')
const filterRiskLevel = ref('')
const filterAiLevel = ref('')
const filterEnabledOnly = ref(false)
const filterForbiddenOnly = ref(false)

const RISK_OPTIONS = [
  { label: t('aiGovernance.tool.riskLevel.low'), value: 'LOW' },
  { label: t('aiGovernance.tool.riskLevel.medium'), value: 'MEDIUM' },
  { label: t('aiGovernance.tool.riskLevel.high'), value: 'HIGH' },
  { label: t('aiGovernance.tool.riskLevel.critical'), value: 'CRITICAL' },
]

const AI_LEVEL_OPTIONS = [
  { label: t('aiGovernance.tool.aiLevel.a0'), value: 'A0' },
  { label: t('aiGovernance.tool.aiLevel.a1'), value: 'A1' },
  { label: t('aiGovernance.tool.aiLevel.a2'), value: 'A2' },
  { label: t('aiGovernance.tool.aiLevel.a3'), value: 'A3' },
  { label: t('aiGovernance.tool.aiLevel.a4'), value: 'A4' },
]

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function riskTagType(risk: string): TagType {
  const map: Record<string, TagType> = { LOW: 'info', MEDIUM: undefined, HIGH: 'warning', CRITICAL: 'danger' }
  return map[risk] || 'info'
}

function riskLabel(risk: string): string {
  return RISK_OPTIONS.find((r) => r.value === risk)?.label || risk
}

function aiLevelLabel(level: string): string {
  return AI_LEVEL_OPTIONS.find((l) => l.value === level)?.label || level
}

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<AiToolRegistry> = await pageAiTools({
      page: currentPage.value,
      size: pageSize.value,
      toolName: filterToolName.value || undefined,
      riskLevel: filterRiskLevel.value || undefined,
      aiLevel: filterAiLevel.value || undefined,
      enabledOnly: filterEnabledOnly.value || undefined,
      forbiddenOnly: filterForbiddenOnly.value || undefined,
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
  filterToolName.value = ''
  filterRiskLevel.value = ''
  filterAiLevel.value = ''
  filterEnabledOnly.value = false
  filterForbiddenOnly.value = false
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

// ============ 编辑弹窗 ============
const editDialogVisible = ref(false)
const editForm = ref<SaveToolRequest>(emptyTool())

function emptyTool(): SaveToolRequest {
  return {
    toolName: '',
    toolVersion: '1.0.0',
    riskLevel: 'MEDIUM',
    aiCapabilityLevel: 'A2',
    permissionCode: '',
    description: '',
    inputSchema: '',
    outputSchema: '',
    isReadonly: true,
    needsHumanReview: false,
    accessBusinessData: false,
    dataScopeStrategy: 'SELF',
    fieldMaskingStrategy: '',
    maxResults: 50,
    timeoutMs: 30000,
    rateLimitPerMin: 60,
    isForbidden: false,
    forbiddenReason: '',
    enabled: true,
    ownerUserId: '',
  }
}

function openCreateDialog() {
  editForm.value = emptyTool()
  editDialogVisible.value = true
}

async function openEditDialog(row: AiToolRegistry) {
  loading.value = true
  try {
    const detail = await getAiTool(row.id as string)
    editForm.value = { ...detail } as SaveToolRequest
    editDialogVisible.value = true
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!editForm.value.toolName.trim()) {
    ElMessage.warning(t('aiGovernance.msg.toolNameRequired'))
    return
  }
  if (!editForm.value.riskLevel) {
    ElMessage.warning(t('aiGovernance.msg.riskLevelRequired'))
    return
  }
  if (!editForm.value.aiCapabilityLevel) {
    ElMessage.warning(t('aiGovernance.msg.aiLevelRequired'))
    return
  }
  await saveAiTool(editForm.value)
  ElMessage.success(t('aiGovernance.msg.saveSuccess'))
  editDialogVisible.value = false
  loadData()
}

async function handleToggle(row: AiToolRegistry) {
  if (row.isForbidden && !row.enabled) {
    ElMessage.warning(t('aiGovernance.msg.forbiddenToolCannotEnable', { reason: row.forbiddenReason || t('aiGovernance.msg.governancePolicyForbidden') }))
    return
  }
  const target = !row.enabled
  const action = target ? t('aiGovernance.msg.enable') : t('aiGovernance.msg.disable')
  try {
    await ElMessageBox.confirm(
      t('aiGovernance.msg.confirmToggleTool', { action, toolName: row.toolName }),
      t('aiGovernance.msg.confirmToggleTitle', { action }),
      { confirmButtonText: action, cancelButtonText: t('aiGovernance.common.action.cancel'), type: 'warning' }
    )
    await toggleAiTool(row.id as string, target, row.version || 1)
    ElMessage.success(t('aiGovernance.msg.toggleSuccess', { action }))
    loadData()
  } catch {
    // 用户取消
  }
}

async function handleValidate() {
  if (!filterToolName.value.trim()) {
    ElMessage.warning(t('aiGovernance.msg.validateToolNameRequired'))
    return
  }
  loading.value = true
  try {
    const tool = await validateAiTool(filterToolName.value.trim())
    if (tool.isForbidden) {
      ElMessage.warning(t('aiGovernance.msg.toolForbiddenByPolicy', { toolName: tool.toolName, reason: tool.forbiddenReason || '-' }))
    } else if (tool.enabled) {
      ElMessage.success(t('aiGovernance.msg.toolValidatePassed', { toolName: tool.toolName }))
    } else {
      ElMessage.info(t('aiGovernance.msg.toolNotEnabled', { toolName: tool.toolName }))
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('aiGovernance.tool.page.title') }}</h2>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('aiGovernance.tool.field.toolName')">
          <el-input v-model="filterToolName" :placeholder="$t('aiGovernance.tool.placeholder.toolName')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.riskLevel')">
          <el-select v-model="filterRiskLevel" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="r in RISK_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.aiLevel')">
          <el-select v-model="filterAiLevel" :placeholder="$t('aiGovernance.common.placeholder.all')" clearable style="width: 160px">
            <el-option v-for="l in AI_LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="">
          <el-checkbox v-model="filterEnabledOnly">{{ $t('aiGovernance.tool.filter.enabledOnly') }}</el-checkbox>
          <el-checkbox v-model="filterForbiddenOnly">{{ $t('aiGovernance.tool.filter.forbiddenOnly') }}</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('aiGovernance.common.action.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('aiGovernance.common.action.reset') }}</el-button>
          <el-button type="success" @click="openCreateDialog">{{ $t('aiGovernance.tool.action.create') }}</el-button>
          <el-button type="warning" @click="handleValidate">{{ $t('aiGovernance.tool.action.validate') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="toolName" :label="$t('aiGovernance.tool.field.toolName')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="riskLevel" :label="$t('aiGovernance.tool.field.risk')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="riskTagType(row.riskLevel as string)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="aiCapabilityLevel" :label="$t('aiGovernance.tool.field.aiLevel')" width="180">
        <template #default="{ row }">
          {{ aiLevelLabel(row.aiCapabilityLevel) }}
        </template>
      </el-table-column>
      <el-table-column prop="permissionCode" :label="$t('aiGovernance.tool.field.permissionCode')" width="160" show-overflow-tooltip />
      <el-table-column prop="isReadonly" :label="$t('aiGovernance.tool.field.readonly')" width="70" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isReadonly ? 'success' : 'warning'" size="small">
            {{ row.isReadonly ? $t('aiGovernance.common.text.yes') : $t('aiGovernance.common.text.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="isForbidden" :label="$t('aiGovernance.tool.field.forbidden')" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isForbidden" type="danger" size="small">{{ $t('aiGovernance.tool.text.forbidden') }}</el-tag>
          <el-tag v-else type="info" size="small">{{ $t('aiGovernance.tool.text.allowed') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" :label="$t('aiGovernance.common.field.enabled')" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? $t('aiGovernance.common.text.enabled') : $t('aiGovernance.common.text.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="forbiddenReason" :label="$t('aiGovernance.tool.field.forbiddenReason')" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.forbiddenReason || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('aiGovernance.common.field.action')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openEditDialog(row as AiToolRegistry)">{{ $t('aiGovernance.common.action.edit') }}</el-button>
          <el-button
            v-if="!row.isForbidden"
            :type="row.enabled ? 'danger' : 'success'"
            link
            size="small"
            @click="handleToggle(row as AiToolRegistry)"
          >{{ row.enabled ? $t('aiGovernance.common.text.disabled') : $t('aiGovernance.common.text.enabled') }}</el-button>
          <span v-else class="text-muted">{{ $t('aiGovernance.tool.text.forbiddenCannotEnable') }}</span>
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

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="editForm.id ? $t('aiGovernance.tool.dialog.editTitle') : $t('aiGovernance.tool.dialog.createTitle')" width="720px">
      <el-form label-width="130px" size="small">
        <el-form-item :label="$t('aiGovernance.tool.field.toolName')" required>
          <el-input
            v-model="editForm.toolName"
            :placeholder="$t('aiGovernance.tool.placeholder.toolNameExample')"
            maxlength="64"
            show-word-limit
            :disabled="!!editForm.id"
          />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.common.field.version')">
          <el-input v-model="editForm.toolVersion" :placeholder="$t('aiGovernance.tool.placeholder.version')" maxlength="32" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.riskLevel')" required>
          <el-select v-model="editForm.riskLevel" style="width: 100%">
            <el-option v-for="r in RISK_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.aiCapabilityLevel')" required>
          <el-select v-model="editForm.aiCapabilityLevel" style="width: 100%">
            <el-option v-for="l in AI_LEVEL_OPTIONS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.permissionCode')">
          <el-input v-model="editForm.permissionCode" :placeholder="$t('aiGovernance.tool.placeholder.permissionCode')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.common.field.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.readonlyTool')">
          <el-switch v-model="editForm.isReadonly" />
          <span class="form-hint">{{ $t('aiGovernance.tool.tip.readonly') }}</span>
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.needsReview')">
          <el-switch v-model="editForm.needsHumanReview" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.accessBusinessData')">
          <el-switch v-model="editForm.accessBusinessData" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.dataScope')">
          <el-input v-model="editForm.dataScopeStrategy" :placeholder="$t('aiGovernance.tool.placeholder.dataScopeStrategy')" maxlength="32" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.fieldMasking')">
          <el-input v-model="editForm.fieldMaskingStrategy" :placeholder="$t('aiGovernance.tool.placeholder.fieldMaskingStrategy')" maxlength="200" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.maxResults')">
          <el-input-number v-model="editForm.maxResults" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.timeout')">
          <el-input-number v-model="editForm.timeoutMs" :min="1000" :max="300000" :step="1000" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.rateLimit')">
          <el-input-number v-model="editForm.rateLimitPerMin" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.tool.field.forbiddenTool')">
          <el-switch v-model="editForm.isForbidden" />
          <span class="form-hint">{{ $t('aiGovernance.tool.tip.forbidden') }}</span>
        </el-form-item>
        <el-form-item v-if="editForm.isForbidden" :label="$t('aiGovernance.tool.field.forbiddenReason')">
          <el-input v-model="editForm.forbiddenReason" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('aiGovernance.common.field.enabled')">
          <el-switch v-model="editForm.enabled" :disabled="editForm.isForbidden" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ $t('aiGovernance.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ $t('aiGovernance.common.action.save') }}</el-button>
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
.form-hint {
  margin-left: 12px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
