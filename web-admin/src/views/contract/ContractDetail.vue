<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getContractDetail,
  contractAction,
  addContractTag,
  removeContractTag,
  updateContract,
} from '@/api/contract'
import type { ContractDetail, ContractActionRequest, SaveContractRequest } from '@/api/types'

const { t } = useI18n()

/**
 * 合同详情页。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 *
 * 核心能力:
 *  - 主表信息 + 版本列表 + 审批时间线 + 标签列表 聚合展示
 *  - 状态机操作面板: SUBMIT/APPROVE/REJECT/RESUBMIT/SIGN/ARCHIVE/CANCEL
 *  - 标签管理: 添加/删除
 *  - 编辑表单: 仅 DRAFT/SUBMITTED/REJECTED 状态可编辑
 *  - DataScope 脱敏展示: amount=null 显示 ***，partyB=*** 显示掩码
 *  - 归档只读提示: ARCHIVED/CANCELLED 显示警告条
 */
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<ContractDetail | null>(null)

// 操作弹窗（SUBMIT/APPROVE/REJECT/RESUBMIT/SIGN/ARCHIVE/CANCEL）
const actionDialogVisible = ref(false)
const currentAction = ref('')
const actionForm = ref<ContractActionRequest>({ action: '', opinion: '' })

// 编辑弹窗
const editDialogVisible = ref(false)
const editForm = ref<SaveContractRequest>({
  title: '',
  contractType: 'GENERAL',
  partyA: '',
  partyB: '',
  amount: undefined,
  currency: 'CNY',
  contentSummary: '',
})

// 标签输入
const newTagInput = ref('')

const STATUS_LABELS: Record<string, string> = {
  DRAFT: t('contract.status.draft'),
  SUBMITTED: t('contract.status.submitted'),
  APPROVED: t('contract.status.approved'),
  REJECTED: t('contract.status.rejected'),
  SIGNED: t('contract.status.signed'),
  ARCHIVED: t('contract.status.archived'),
  CANCELLED: t('contract.status.cancelled'),
}

const ACTION_LABELS: Record<string, string> = {
  SUBMIT: t('contract.action.submit'),
  APPROVE: t('contract.action.approve'),
  REJECT: t('contract.action.reject'),
  RESUBMIT: t('contract.action.resubmit'),
  SIGN: t('contract.action.sign'),
  ARCHIVE: t('contract.action.archive'),
  CANCEL: t('contract.action.cancel'),
}

const ACTION_LOG_LABELS: Record<string, string> = {
  CREATE: t('contract.actionLog.create'),
  SUBMIT: t('contract.action.submit'),
  APPROVE: t('contract.action.approve'),
  REJECT: t('contract.action.reject'),
  RESUBMIT: t('contract.action.resubmit'),
  SIGN: t('contract.action.sign'),
  ARCHIVE: t('contract.action.archive'),
  CANCEL: t('contract.action.cancel'),
}

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function statusTagType(status: string): TagType {
  const map: Record<string, TagType> = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'primary',
    REJECTED: 'danger',
    SIGNED: 'success',
    ARCHIVED: undefined,
    CANCELLED: 'info',
  }
  return map[status] || 'info'
}

function statusLabel(status: string): string {
  return STATUS_LABELS[status] || status
}

function actionLogLabel(action: string): string {
  return ACTION_LOG_LABELS[action] || action
}

/** 根据当前状态计算可用操作 */
const availableActions = computed<{ action: string; label: string; type: string }[]>(() => {
  if (!detail.value) return []
  const status = detail.value.status
  const map: Record<string, { action: string; label: string; type: string }[]> = {
    DRAFT: [
      { action: 'SUBMIT', label: t('contract.action.submit'), type: 'primary' },
      { action: 'CANCEL', label: t('contract.action.cancel'), type: 'info' },
    ],
    SUBMITTED: [
      { action: 'APPROVE', label: t('contract.action.approve'), type: 'success' },
      { action: 'REJECT', label: t('contract.action.reject'), type: 'danger' },
      { action: 'CANCEL', label: t('contract.action.cancel'), type: 'info' },
    ],
    APPROVED: [
      { action: 'SIGN', label: t('contract.action.sign'), type: 'success' },
      { action: 'CANCEL', label: t('contract.action.cancel'), type: 'info' },
    ],
    REJECTED: [
      { action: 'RESUBMIT', label: t('contract.action.resubmit'), type: 'primary' },
      { action: 'CANCEL', label: t('contract.action.cancel'), type: 'info' },
    ],
    SIGNED: [
      { action: 'ARCHIVE', label: t('contract.action.archive'), type: 'warning' },
      { action: 'CANCEL', label: t('contract.action.cancel'), type: 'info' },
    ],
    ARCHIVED: [],
    CANCELLED: [],
  }
  return map[status] || []
})

/** 是否需要审批意见（APPROVE/REJECT 必填） */
function needsOpinion(action: string): boolean {
  return action === 'APPROVE' || action === 'REJECT'
}

/** 操作是否需要二次确认（CANCEL/ARCHIVE/SIGN 等关键操作） */
function needsConfirm(action: string): boolean {
  return ['CANCEL', 'ARCHIVE', 'SIGN'].includes(action)
}

function formatAmount(amount?: number | null, currency?: string): string {
  if (amount === null || amount === undefined) return t('contract.detail.amountNoPermission')
  const symbol = currency === 'CNY' ? '¥' : currency ? `${currency} ` : ''
  return `${symbol}${Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function isMasked(value?: string): boolean {
  return value === '***'
}

async function loadData() {
  loading.value = true
  try {
    detail.value = await getContractDetail(route.params.id as string)
  } finally {
    loading.value = false
  }
}

function openActionDialog(action: string) {
  currentAction.value = action
  actionForm.value = { action, opinion: '' }
  actionDialogVisible.value = true
}

async function handleAction() {
  if (!detail.value) return
  if (needsOpinion(currentAction.value) && !actionForm.value.opinion?.trim()) {
    ElMessage.warning(t('contract.msg.opinionRequired', { action: ACTION_LABELS[currentAction.value] }))
    return
  }
  try {
    if (needsConfirm(currentAction.value)) {
      await ElMessageBox.confirm(
        t('contract.msg.actionConfirm', { action: ACTION_LABELS[currentAction.value] }),
        t('contract.msg.actionConfirmTitle'),
        { confirmButtonText: t('contract.msg.confirmButton'), cancelButtonText: t('contract.msg.cancelButton'), type: 'warning' },
      )
    }
  } catch {
    return // 用户取消
  }
  await contractAction(detail.value.id, actionForm.value)
  ElMessage.success(t('contract.msg.actionSuccess', { action: ACTION_LABELS[currentAction.value] }))
  actionDialogVisible.value = false
  loadData()
}

function openEditDialog() {
  if (!detail.value) return
  editForm.value = {
    title: detail.value.title,
    contractType: detail.value.contractType || 'GENERAL',
    partyA: detail.value.partyA,
    partyB: isMasked(detail.value.partyB) ? '' : detail.value.partyB,
    signedDate: detail.value.signedDate,
    effectiveDate: detail.value.effectiveDate,
    expireDate: detail.value.expireDate,
    amount: detail.value.amount ?? undefined,
    currency: detail.value.currency || 'CNY',
    contentSummary: isMasked(detail.value.contentSummary) ? '' : detail.value.contentSummary || '',
  }
  editDialogVisible.value = true
}

async function handleEdit() {
  if (!detail.value) return
  if (!editForm.value.title.trim()) {
    ElMessage.warning(t('contract.msg.titleRequired'))
    return
  }
  if (!editForm.value.partyA.trim() || !editForm.value.partyB.trim()) {
    ElMessage.warning(t('contract.msg.partyRequired'))
    return
  }
  await updateContract(detail.value.id, editForm.value)
  ElMessage.success(t('contract.msg.updateSuccess'))
  editDialogVisible.value = false
  loadData()
}

async function handleAddTag() {
  if (!detail.value) return
  const tagName = newTagInput.value.trim()
  if (!tagName) {
    ElMessage.warning(t('contract.msg.tagNameRequired'))
    return
  }
  try {
    await addContractTag(detail.value.id, tagName)
    ElMessage.success(t('contract.msg.tagAdded', { tagName }))
    newTagInput.value = ''
    loadData()
  } catch (e: unknown) {
    // 重复标签等业务冲突由全局拦截器处理
    console.warn('add tag failed', e)
  }
}

async function handleRemoveTag(tagName: string) {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(t('contract.msg.tagDeleteConfirm', { tagName }), t('contract.msg.tagDeleteConfirmTitle'), {
      confirmButtonText: t('contract.msg.confirmButton'),
      cancelButtonText: t('contract.msg.cancelButton'),
      type: 'warning',
    })
  } catch {
    return
  }
  await removeContractTag(detail.value.id, tagName)
  ElMessage.success(t('contract.msg.tagDeleted'))
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading">
    <div v-if="detail" class="contract-detail">
      <!-- 顶部信息 -->
      <el-page-header @back="router.push('/contracts')" :title="$t('contract.detail.back')">
        <template #content>
          <span class="header-title">{{ detail.contractNo }}</span>
          <el-tag :type="statusTagType(detail.status)" size="small" style="margin-left: 12px">
            {{ statusLabel(detail.status) }}
          </el-tag>
          <el-tag v-if="!detail.editable" type="info" size="small" style="margin-left: 8px">{{ $t('contract.detail.readonly') }}</el-tag>
        </template>
      </el-page-header>

      <!-- 归档/取消提示条 -->
      <el-alert
        v-if="detail.status === 'ARCHIVED' || detail.status === 'CANCELLED'"
        :title="$t('contract.detail.archivedAlert', { status: statusLabel(detail.status) })"
        type="warning"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      />

      <el-row :gutter="16" style="margin-top: 16px">
        <!-- 左侧: 合同信息 + 版本 + 审批 -->
        <el-col :span="17">
          <!-- 合同基本信息 -->
          <el-card shadow="never">
            <template #header>
              <div class="card-header">
                <span class="section-title">{{ $t('contract.detail.section.info') }}</span>
                <el-button
                  v-if="detail.editable"
                  type="primary"
                  size="small"
                  @click="openEditDialog"
                >
                  {{ $t('contract.detail.action.edit') }}
                </el-button>
              </div>
            </template>
            <el-descriptions :column="2" border>
              <el-descriptions-item :label="$t('contract.field.contractNo')">{{ detail.contractNo }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.detail.field.versionNo')">v{{ detail.currentVersionNo }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.field.title')" :span="2">{{ detail.title }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.list.field.contractType')">{{ detail.contractType || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.field.status')">
                <el-tag :type="statusTagType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('contract.field.partyA')">{{ detail.partyA }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.field.partyB')">
                <span :class="{ 'masked-value': isMasked(detail.partyB) }">{{ detail.partyB }}</span>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('contract.list.field.signedDate')">{{ detail.signedDate || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.list.field.effectiveDate')">{{ detail.effectiveDate || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.list.field.expireDate')">{{ detail.expireDate || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.detail.field.contractAmount')">
                <span :class="{ 'masked-value': detail.amount === null }">
                  {{ formatAmount(detail.amount, detail.currency) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('contract.list.field.contentSummary')" :span="2">
                <span :class="{ 'masked-value': isMasked(detail.contentSummary) }">
                  {{ detail.contentSummary || '-' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('contract.detail.field.submittedTime')">{{ detail.submittedTime || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.detail.field.approvedTime')">{{ detail.approvedTime || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.detail.field.signedTime')">{{ detail.signedTime || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.detail.field.archivedTime')">{{ detail.archivedTime || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.detail.field.createdBy')">{{ detail.createdBy || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('contract.field.createdTime')">{{ detail.createdTime || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 版本列表 -->
          <el-card shadow="never" style="margin-top: 16px">
            <template #header><span class="section-title">{{ $t('contract.detail.section.versions') }}</span></template>
            <el-table :data="detail.versions" border size="small">
              <el-table-column prop="versionNo" :label="$t('contract.field.version')" width="70" align="center">
                <template #default="{ row }">
                  <span>v{{ row.versionNo }}</span>
                  <el-tag v-if="row.isCurrent" type="success" size="small" style="margin-left: 4px">{{ $t('contract.detail.version.current') }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="fileNameSnapshot" :label="$t('contract.detail.field.fileName')" min-width="200" show-overflow-tooltip />
              <el-table-column prop="fileSize" :label="$t('contract.detail.field.size')" width="100">
                <template #default="{ row }">{{ row.fileSize ? (row.fileSize / 1024).toFixed(1) + ' KB' : '-' }}</template>
              </el-table-column>
              <el-table-column prop="fileChecksum" :label="$t('contract.detail.field.checksum')" width="180" show-overflow-tooltip />
              <el-table-column prop="changeLog" :label="$t('contract.detail.field.changeLog')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="createdTime" :label="$t('contract.field.createdTime')" width="170" />
            </el-table>
          </el-card>

          <!-- 审批时间线 -->
          <el-card shadow="never" style="margin-top: 16px">
            <template #header><span class="section-title">{{ $t('contract.detail.section.approvals') }}</span></template>
            <el-timeline v-if="detail.approvals && detail.approvals.length > 0">
              <el-timeline-item
                v-for="apr in detail.approvals"
                :key="apr.id"
                :timestamp="apr.createdTime"
                placement="top"
              >
                <div class="log-item">
                  <el-tag size="small" :type="apr.action === 'REJECT' ? 'danger' : apr.action === 'CREATE' ? 'info' : 'primary'">
                    {{ actionLogLabel(apr.action) }}
                  </el-tag>
                  <span v-if="apr.fromStatus && apr.toStatus" class="log-status">
                    {{ statusLabel(apr.fromStatus) }} → {{ statusLabel(apr.toStatus) }}
                  </span>
                  <span class="log-operator">{{ apr.approverName || apr.approverId }}</span>
                  <div v-if="apr.opinion" class="log-comment">{{ apr.opinion }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else :description="$t('contract.detail.empty.approvals')" />
          </el-card>
        </el-col>

        <!-- 右侧: 操作面板 + 标签 -->
        <el-col :span="7">
          <!-- 操作面板 -->
          <el-card shadow="never">
            <template #header><span class="section-title">{{ $t('contract.detail.section.action') }}</span></template>
            <div class="action-buttons">
              <el-button
                v-for="act in availableActions"
                :key="act.action"
                :type="(act.type as 'primary' | 'success' | 'warning' | 'danger' | 'info')"
                @click="openActionDialog(act.action)"
                style="margin: 4px 0; width: 100%"
              >
                {{ act.label }}
              </el-button>
              <el-empty v-if="availableActions.length === 0" :description="$t('contract.detail.empty.actions')" :image-size="60" />
            </div>
          </el-card>

          <!-- 标签管理 -->
          <el-card shadow="never" style="margin-top: 16px">
            <template #header><span class="section-title">{{ $t('contract.detail.section.tags') }}</span></template>
            <div class="tag-input-bar">
              <el-input
                v-model="newTagInput"
                :placeholder="$t('contract.detail.placeholder.tagName')"
                size="small"
                @keyup.enter="handleAddTag"
              />
              <el-button type="primary" size="small" @click="handleAddTag">{{ $t('contract.detail.action.add') }}</el-button>
            </div>
            <div class="tag-list">
              <el-tag
                v-for="tag in detail.tags"
                :key="tag.id"
                closable
                @close="handleRemoveTag(tag.tagName)"
                style="margin: 4px"
              >
                {{ tag.tagName }}
              </el-tag>
              <el-empty v-if="!detail.tags || detail.tags.length === 0" :description="$t('contract.detail.empty.tags')" :image-size="40" />
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 操作弹窗 -->
      <el-dialog v-model="actionDialogVisible" :title="ACTION_LABELS[currentAction] || $t('contract.detail.operation')" width="450px">
        <el-form label-width="80px">
          <el-form-item v-if="needsOpinion(currentAction)" :label="$t('contract.detail.field.opinion')" required>
            <el-input
              v-model="actionForm.opinion"
              type="textarea"
              :rows="4"
              :placeholder="currentAction === 'REJECT' ? $t('contract.detail.placeholder.rejectReason') : $t('contract.detail.placeholder.opinion')"
            />
          </el-form-item>
          <el-form-item v-else :label="$t('contract.field.remark')">
            <el-input
              v-model="actionForm.opinion"
              type="textarea"
              :rows="3"
              :placeholder="$t('contract.detail.placeholder.actionRemark')"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="actionDialogVisible = false">{{ $t('contract.action.cancel') }}</el-button>
          <el-button type="primary" @click="handleAction">{{ $t('contract.detail.action.confirm') }}</el-button>
        </template>
      </el-dialog>

      <!-- 编辑弹窗 -->
      <el-dialog v-model="editDialogVisible" :title="$t('contract.detail.dialog.editTitle')" width="600px">
        <el-form label-width="90px">
          <el-form-item :label="$t('contract.field.title')" required>
            <el-input v-model="editForm.title" maxlength="256" show-word-limit />
          </el-form-item>
          <el-form-item :label="$t('contract.list.field.contractType')">
            <el-select v-model="editForm.contractType" style="width: 100%">
              <el-option :label="$t('contract.type.general')" value="GENERAL" />
              <el-option :label="$t('contract.type.service')" value="SERVICE" />
              <el-option :label="$t('contract.type.purchase')" value="PURCHASE" />
              <el-option :label="$t('contract.type.sale')" value="SALE" />
              <el-option :label="$t('contract.type.lease')" value="LEASE" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('contract.field.partyA')" required>
            <el-input v-model="editForm.partyA" maxlength="128" show-word-limit />
          </el-form-item>
          <el-form-item :label="$t('contract.field.partyB')" required>
            <el-input v-model="editForm.partyB" maxlength="128" show-word-limit />
          </el-form-item>
          <el-form-item :label="$t('contract.field.amount')">
            <el-input-number v-model="editForm.amount" :min="0" :precision="2" style="width: 200px" />
            <el-select v-model="editForm.currency" style="width: 100px; margin-left: 8px">
              <el-option label="CNY" value="CNY" />
              <el-option label="USD" value="USD" />
              <el-option label="EUR" value="EUR" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('contract.list.field.contentSummary')">
            <el-input v-model="editForm.contentSummary" type="textarea" :rows="4" maxlength="2000" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editDialogVisible = false">{{ $t('contract.action.cancel') }}</el-button>
          <el-button type="primary" @click="handleEdit">{{ $t('contract.detail.action.save') }}</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<style scoped>
.header-title {
  font-size: 16px;
  font-weight: 600;
}
.section-title {
  font-weight: 600;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.action-buttons {
  display: flex;
  flex-direction: column;
}
.tag-input-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.tag-list {
  min-height: 40px;
}
.log-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.log-status {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.log-operator {
  font-size: 13px;
  color: var(--el-text-color-primary);
  font-weight: 500;
}
.log-comment {
  width: 100%;
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  padding: 4px 8px;
  border-radius: 4px;
}
.masked-value {
  color: var(--el-text-color-placeholder);
  font-style: italic;
}
</style>
