<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRequest,
  submitRequest,
  approveRequest,
  rejectRequest,
  withdrawRequest,
  archiveRequest,
} from '@/api/biz-request'
import type { BizRequestDetail } from '@/api/types'
import { hasAnyPermission } from '@/utils/permission'
import { track } from '@/utils/tracker'
import DetailDrawer from '@/components/DetailDrawer.vue'
import AuditTimeline, { type AuditItem } from '@/components/AuditTimeline.vue'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<BizRequestDetail | null>(null)
const actionLoading = ref(false)

const statusLabel: Record<string, string> = {
  DRAFT: t('biz.request.status.draft'),
  SUBMITTED: t('biz.request.status.submitted'),
  APPROVED: t('biz.request.status.approved'),
  REJECTED: t('biz.request.status.rejected'),
  ARCHIVED: t('biz.request.status.archived'),
}
const statusTagType: Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  ARCHIVED: '',
}
const actionLabel: Record<string, string> = {
  SUBMIT: t('biz.request.action.submit'),
  APPROVE: t('biz.request.action.approve'),
  REJECT: t('biz.request.action.reject'),
  WITHDRAW: t('biz.request.action.withdraw'),
  ARCHIVE: t('biz.request.action.archive'),
}

const isDraft = computed(() => detail.value?.requestStatus === 'DRAFT')
const isRejected = computed(() => detail.value?.requestStatus === 'REJECTED')
const isSubmitted = computed(() => detail.value?.requestStatus === 'SUBMITTED')
const isApproved = computed(() => detail.value?.requestStatus === 'APPROVED')
// 草稿/已驳回状态可编辑 (设计来源 18-样例业务详细设计 状态机 DRAFT→SUBMITTED, REJECTED→DRAFT(edit))
const canEdit = computed(() => isDraft.value || isRejected.value)
const canSubmit = computed(() => canEdit.value && hasAnyPermission(['biz:request:submit']))
const canApprove = computed(() => isSubmitted.value && hasAnyPermission(['biz:request:approve']))
const canReject = computed(() => isSubmitted.value && hasAnyPermission(['biz:request:reject']))
const canWithdraw = computed(() => isSubmitted.value && hasAnyPermission(['biz:request:withdraw']))
const canArchive = computed(() => isApproved.value && hasAnyPermission(['biz:request:archive']))

function genIdempotencyKey() {
  // 简单 UUID v4 生成, 用于幂等键 (防重复提交)
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return 'idem-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)
}

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getRequest(route.params.id as string)
  } finally {
    loading.value = false
  }
}

function handleEdit() {
  track('web.biz_request.edit.click', { payload: { id: detail.value?.id } })
  router.push(`/biz/requests/${detail.value?.id}/edit`)
}

async function refreshAfterAction() {
  await loadDetail()
}

async function handleSubmit() {
  if (!detail.value) return
  await ElMessageBox.confirm(t('biz.request.msg.submitConfirm'), t('biz.request.msg.submitConfirmTitle'), {
    type: 'warning',
  })
  actionLoading.value = true
  track('web.biz_request.submit.click', { payload: { id: detail.value.id } })
  try {
    await submitRequest(detail.value.id, {
      version: detail.value.version,
      idempotencyKey: genIdempotencyKey(),
    })
    ElMessage.success(t('biz.request.msg.submitSuccess'))
    track('web.biz_request.submit.success', { payload: { id: detail.value?.id } })
    await refreshAfterAction()
  } catch (err) {
    track('web.biz_request.submit.failed', {
      errorCode: (err as Error)?.message || 'SUBMIT_FAILED',
    })
    throw err
  } finally {
    actionLoading.value = false
  }
}

async function handleApprove() {
  if (!detail.value) return
  const { value: opinion } = await ElMessageBox.prompt(t('biz.request.msg.approvePrompt'), t('biz.request.msg.approveTitle'), {
    inputType: 'textarea',
    inputPlaceholder: t('biz.request.msg.opinionPlaceholder'),
    confirmButtonText: t('biz.request.msg.approveConfirmButton'),
  })
  actionLoading.value = true
  track('web.biz_request.approve.click', { payload: { id: detail.value.id } })
  try {
    await approveRequest(detail.value.id, {
      opinion: opinion || t('biz.request.msg.agreeDefault'),
      version: detail.value.version,
      idempotencyKey: genIdempotencyKey(),
    })
    ElMessage.success(t('biz.request.msg.approveSuccess'))
    track('web.biz_request.approve.success', { payload: { id: detail.value?.id } })
    await refreshAfterAction()
  } catch (err) {
    track('web.biz_request.approve.failed', {
      errorCode: (err as Error)?.message || 'APPROVE_FAILED',
    })
    throw err
  } finally {
    actionLoading.value = false
  }
}

async function handleReject() {
  if (!detail.value) return
  const { value: opinion } = await ElMessageBox.prompt(t('biz.request.msg.rejectPrompt'), t('biz.request.msg.rejectTitle'), {
    inputType: 'textarea',
    inputPlaceholder: t('biz.request.msg.rejectOpinionPlaceholder'),
    inputValidator: (v: string) => (v && v.trim().length > 0) || t('biz.request.msg.rejectOpinionRequired'),
    confirmButtonText: t('biz.request.msg.rejectConfirmButton'),
    confirmButtonClass: 'el-button--danger',
  })
  actionLoading.value = true
  track('web.biz_request.reject.click', { payload: { id: detail.value.id } })
  try {
    await rejectRequest(detail.value.id, opinion.trim(), detail.value.version, genIdempotencyKey())
    ElMessage.success(t('biz.request.msg.rejectSuccess'))
    track('web.biz_request.reject.success', { payload: { id: detail.value?.id } })
    await refreshAfterAction()
  } catch (err) {
    track('web.biz_request.reject.failed', {
      errorCode: (err as Error)?.message || 'REJECT_FAILED',
    })
    throw err
  } finally {
    actionLoading.value = false
  }
}

async function handleWithdraw() {
  if (!detail.value) return
  const { value: reason } = await ElMessageBox.prompt(t('biz.request.msg.withdrawPrompt'), t('biz.request.msg.withdrawTitle'), {
    inputType: 'textarea',
    inputPlaceholder: t('biz.request.msg.withdrawReasonPlaceholder'),
    inputValidator: (v: string) => (v && v.trim().length > 0) || t('biz.request.msg.withdrawReasonRequired'),
    confirmButtonText: t('biz.request.msg.withdrawConfirmButton'),
  })
  actionLoading.value = true
  track('web.biz_request.withdraw.click', { payload: { id: detail.value.id } })
  try {
    await withdrawRequest(detail.value.id, reason.trim(), detail.value.version, genIdempotencyKey())
    ElMessage.success(t('biz.request.msg.withdrawSuccess'))
    track('web.biz_request.withdraw.success', { payload: { id: detail.value?.id } })
    await refreshAfterAction()
  } catch (err) {
    track('web.biz_request.withdraw.failed', {
      errorCode: (err as Error)?.message || 'WITHDRAW_FAILED',
    })
    throw err
  } finally {
    actionLoading.value = false
  }
}

async function handleArchive() {
  if (!detail.value) return
  await ElMessageBox.confirm(t('biz.request.msg.archiveConfirm'), t('biz.request.msg.archiveConfirmTitle'), {
    type: 'warning',
  })
  actionLoading.value = true
  track('web.biz_request.archive.click', { payload: { id: detail.value.id } })
  try {
    await archiveRequest(detail.value.id, detail.value.version, genIdempotencyKey())
    ElMessage.success(t('biz.request.msg.archiveSuccess'))
    track('web.biz_request.archive.success', { payload: { id: detail.value?.id } })
    await refreshAfterAction()
  } catch (err) {
    track('web.biz_request.archive.failed', {
      errorCode: (err as Error)?.message || 'ARCHIVE_FAILED',
    })
    throw err
  } finally {
    actionLoading.value = false
  }
}

function formatAmount(v?: number) {
  return v != null ? '¥' + Number(v).toFixed(2) : '-'
}

// --- 通用组件验证: DetailDrawer + AuditTimeline ---
const approvalDetailVisible = ref(false)
const currentApproval = ref<Record<string, any>>({})

// 审批结果到 AuditItem.status 的映射
const approvalStatusMap: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  APPROVED: 'success',
  REJECTED: 'danger',
  SUBMITTED: 'warning',
}

// 将后端 ApprovalRecord 映射为 AuditTimeline 所需的 AuditItem (倒序由组件内部处理)
const auditItems = computed<AuditItem[]>(() => {
  if (!detail.value?.approvals) return []
  return detail.value.approvals.map((a, idx) => ({
    id: a.id || `approval-${idx}`,
    action: actionLabel[a.action || ''] || a.action || '-',
    operator: a.operatorId || '-',
    status: approvalStatusMap[a.result || ''] || 'info',
    comment: a.opinion,
    timestamp: a.operatedTime || '',
  }))
})

function handleViewApproval(item: any) {
  currentApproval.value = item || {}
  approvalDetailVisible.value = true
}

onMounted(loadDetail)
</script>

<template>
  <div v-loading="loading">
    <el-page-header @back="router.push('/biz/requests')" :title="$t('common.action.back')">
      <template #content>{{ $t('biz.request.page.detail') }}</template>
      <template #extra>
        <el-button v-if="canEdit" type="primary" @click="handleEdit">{{ $t('common.action.edit') }}</el-button>
        <el-button v-if="canSubmit" type="warning" :loading="actionLoading" @click="handleSubmit">{{ $t('biz.request.action.submit') }}</el-button>
        <el-button v-if="canApprove" type="success" :loading="actionLoading" @click="handleApprove">{{ $t('biz.request.action.approve') }}</el-button>
        <el-button v-if="canReject" type="danger" :loading="actionLoading" @click="handleReject">{{ $t('biz.request.action.reject') }}</el-button>
        <el-button v-if="canWithdraw" :loading="actionLoading" @click="handleWithdraw">{{ $t('biz.request.action.withdraw') }}</el-button>
        <el-button v-if="canArchive" type="info" :loading="actionLoading" @click="handleArchive">{{ $t('biz.request.action.archive') }}</el-button>
      </template>
    </el-page-header>

    <el-descriptions v-if="detail" :column="2" border style="margin-top: 20px">
      <el-descriptions-item :label="$t('biz.request.field.requestNo')">{{ detail.requestNo }}</el-descriptions-item>
      <el-descriptions-item :label="$t('common.field.status')">
        <el-tag :type="statusTagType[detail.requestStatus] || 'info'">
          {{ statusLabel[detail.requestStatus] || detail.requestStatus }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.title')" :span="2">{{ detail.title }}</el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.customerId')">{{ detail.customerNameSnapshot }}</el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.amount')">{{ formatAmount(detail.totalAmount) }}</el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.applicant')">{{ detail.applicantNameSnapshot || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.versionNo')">v{{ detail.version ?? 0 }}</el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.submittedTime')">{{ detail.submittedTime || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.approvedTime')">{{ detail.approvedTime || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="$t('common.field.createdTime')">{{ detail.createdTime || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.archivedTime')">{{ detail.archivedTime || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="$t('biz.request.field.applyReason')" :span="2">{{ detail.applyReason || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-card v-if="detail && detail.items && detail.items.length > 0" shadow="never" style="margin-top: 20px">
      <template #header>{{ $t('biz.request.detail.itemsTitle') }}</template>
      <el-table :data="detail.items" border stripe size="small">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="productCodeSnapshot" :label="$t('biz.product.field.code')" width="140" />
        <el-table-column prop="productNameSnapshot" :label="$t('biz.product.field.name')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="unit" :label="$t('biz.product.field.unit')" width="80" />
        <el-table-column prop="quantity" :label="$t('biz.request.field.quantity')" width="100" align="right" />
        <el-table-column prop="unitPrice" :label="$t('biz.product.field.price')" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="lineAmount" :label="$t('biz.request.field.lineAmount')" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.lineAmount) }}</template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; text-align: right; font-size: 16px; font-weight: 600">
        {{ $t('biz.request.field.totalLabel') }} {{ formatAmount(detail.totalAmount) }}
      </div>
    </el-card>

    <el-card v-if="detail && detail.approvals && detail.approvals.length > 0" shadow="never" style="margin-top: 20px">
      <template #header>{{ $t('biz.request.detail.approvalTitle') }}</template>
      <el-timeline>
        <el-timeline-item
          v-for="(item, idx) in detail.approvals"
          :key="idx"
          :timestamp="item.operatedTime"
          placement="top"
        >
          <el-card shadow="hover">
            <h4>{{ actionLabel[item.action || ''] || item.action }}</h4>
            <p>{{ $t('biz.request.field.operator') }}: {{ item.operatorId || '-' }}</p>
            <p v-if="item.result">{{ $t('common.field.result') }}: {{ item.result === 'APPROVED' ? $t('biz.request.result.approved') : $t('biz.request.result.rejected') }}</p>
            <p v-if="item.opinion">{{ $t('biz.request.field.opinion') }}: {{ item.opinion }}</p>
            <div style="margin-top: 8px; text-align: right">
              <el-button size="small" link type="primary" @click="handleViewApproval(item)">{{ $t('common.action.view') }}</el-button>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-card v-if="detail && detail.approvals && detail.approvals.length > 0" shadow="never" style="margin-top: 20px">
      <template #header>{{ $t('biz.request.detail.auditTrail') }}</template>
      <AuditTimeline :items="auditItems" />
    </el-card>

    <DetailDrawer
      v-model:visible="approvalDetailVisible"
      :title="$t('biz.request.detail.approvalDetail')"
      :data="currentApproval"
      :column="1"
    />
  </div>
</template>
