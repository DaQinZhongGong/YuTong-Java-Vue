<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getTicketDetail, ticketAction } from '@/api/ticket'
import type { TicketDetail, TicketActionRequest } from '@/api/types'

/**
 * 工单详情页。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * 展示工单基本信息 + 处理记录时间线 + 状态操作按钮 + 评价表单。
 */
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<TicketDetail | null>(null)

// 操作弹窗
const actionDialogVisible = ref(false)
const currentAction = ref('')
const actionForm = ref<TicketActionRequest>({ action: '', handlerId: '', handlerName: '', comment: '' })

const STATUS_LABELS: Record<string, string> = {
  NEW: t('ticket.status.new'), ASSIGNED: t('ticket.status.assigned'), PROCESSING: t('ticket.status.processing'),
  SUSPENDED: t('ticket.status.suspended'), COMPLETED: t('ticket.status.completed'), CLOSED: t('ticket.status.closed'),
}

const ACTION_LABELS: Record<string, string> = {
  ASSIGN: t('ticket.actionLabel.assign'), ACCEPT: t('ticket.actionLabel.accept'), TRANSFER: t('ticket.actionLabel.transfer'), SUSPEND: t('ticket.actionLabel.suspend'),
  RESUME: t('ticket.actionLabel.resume'), RESOLVE: t('ticket.actionLabel.resolve'), REOPEN: t('ticket.actionLabel.reopen'), CLOSE: t('ticket.actionLabel.close'), EVALUATE: t('ticket.actionLabel.evaluate'),
}

const ACTION_LOG_LABELS: Record<string, string> = {
  CREATE: t('ticket.actionLogLabel.create'), ASSIGN: t('ticket.actionLogLabel.assign'), ACCEPT: t('ticket.actionLogLabel.accept'), TRANSFER: t('ticket.actionLogLabel.transfer'), SUSPEND: t('ticket.actionLogLabel.suspend'),
  RESUME: t('ticket.actionLogLabel.resume'), RESOLVE: t('ticket.actionLogLabel.resolve'), REOPEN: t('ticket.actionLogLabel.reopen'), CLOSE: t('ticket.actionLogLabel.close'), EVALUATE: t('ticket.actionLogLabel.evaluate'),
}

/** 根据当前状态计算可用操作 */
const availableActions = computed<{ action: string; label: string }[]>(() => {
  if (!detail.value) return []
  const status = detail.value.status
  const map: Record<string, string[]> = {
    NEW: ['ASSIGN', 'CLOSE'],
    ASSIGNED: ['ACCEPT', 'TRANSFER'],
    PROCESSING: ['SUSPEND', 'RESOLVE'],
    SUSPENDED: ['RESUME'],
    COMPLETED: ['REOPEN', 'EVALUATE', 'CLOSE'],
    CLOSED: [],
  }
  return (map[status] || []).map((a) => ({ action: a, label: ACTION_LABELS[a] || a }))
})

/** 操作是否需要处理人输入 */
function needsHandler(action: string): boolean {
  return action === 'ASSIGN' || action === 'TRANSFER'
}

function statusLabel(status: string): string {
  return STATUS_LABELS[status] || status
}

function actionLogLabel(action: string): string {
  return ACTION_LOG_LABELS[action] || action
}

async function loadData() {
  loading.value = true
  try {
    detail.value = await getTicketDetail(route.params.id as string)
  } finally {
    loading.value = false
  }
}

function openActionDialog(action: string) {
  currentAction.value = action
  actionForm.value = {
    action,
    handlerId: '',
    handlerName: '',
    comment: '',
    satisfactionScore: action === 'EVALUATE' ? 5 : undefined,
    satisfactionComment: '',
  }
  actionDialogVisible.value = true
}

async function handleAction() {
  if (!detail.value) return
  if (needsHandler(currentAction.value) && !actionForm.value.handlerId?.trim()) {
    ElMessage.warning(t('ticket.msg.handlerIdRequired'))
    return
  }
  await ticketAction(detail.value.id, actionForm.value)
  ElMessage.success(t('ticket.msg.actionSuccess', { action: ACTION_LABELS[currentAction.value] }))
  actionDialogVisible.value = false
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div v-loading="loading">
    <div v-if="detail" class="ticket-detail">
      <!-- 顶部信息 -->
      <el-page-header @back="router.push('/tickets')" :title="$t('ticket.action.back')">
        <template #content>
          <span class="header-title">{{ detail.ticketNo }}</span>
          <el-tag size="small" style="margin-left: 12px">{{ statusLabel(detail.status) }}</el-tag>
        </template>
      </el-page-header>

      <el-row :gutter="16" style="margin-top: 16px">
        <!-- 左侧: 工单信息 + 处理记录 -->
        <el-col :span="16">
          <el-card shadow="never">
            <template #header><span class="section-title">{{ $t('ticket.section.basicInfo') }}</span></template>
            <el-descriptions :column="2" border>
              <el-descriptions-item :label="$t('ticket.field.ticketNo')">{{ detail.ticketNo }}</el-descriptions-item>
              <el-descriptions-item :label="$t('ticket.field.title')">{{ detail.title }}</el-descriptions-item>
              <el-descriptions-item :label="$t('ticket.field.category')">{{ detail.categoryNameSnapshot }}</el-descriptions-item>
              <el-descriptions-item :label="$t('ticket.field.priority')">{{ detail.priority }}</el-descriptions-item>
              <el-descriptions-item :label="$t('ticket.field.reporter')">{{ detail.reporterNameSnapshot || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('ticket.field.handler')">{{ detail.handlerNameSnapshot || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('ticket.field.slaDeadline')">{{ detail.slaDeadline || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('ticket.field.createdTime')">{{ detail.createdTime }}</el-descriptions-item>
              <el-descriptions-item :label="$t('ticket.field.description')" :span="2">{{ detail.description || '-' }}</el-descriptions-item>
              <el-descriptions-item v-if="detail.satisfactionScore" :label="$t('ticket.field.evaluate')" :span="2">
                <el-rate :model-value="detail.satisfactionScore" disabled />
                <span v-if="detail.satisfactionComment" style="margin-left: 12px">{{ detail.satisfactionComment }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 处理记录时间线 -->
          <el-card shadow="never" style="margin-top: 16px">
            <template #header><span class="section-title">{{ $t('ticket.section.logs') }}</span></template>
            <el-timeline v-if="detail.logs && detail.logs.length > 0">
              <el-timeline-item
                v-for="log in detail.logs"
                :key="log.id"
                :timestamp="log.createdTime"
                placement="top"
              >
                <div class="log-item">
                  <el-tag size="small" type="info">{{ actionLogLabel(log.action) }}</el-tag>
                  <span v-if="log.fromStatus && log.toStatus" class="log-status">
                    {{ statusLabel(log.fromStatus) }} → {{ statusLabel(log.toStatus) }}
                  </span>
                  <span class="log-operator">{{ log.operatorName || log.operatorId }}</span>
                  <div v-if="log.comment" class="log-comment">{{ log.comment }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else :description="$t('ticket.empty.noLogs')" />
          </el-card>
        </el-col>

        <!-- 右侧: 操作面板 -->
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span class="section-title">{{ $t('ticket.section.operation') }}</span></template>
            <div class="action-buttons">
              <el-button
                v-for="act in availableActions"
                :key="act.action"
                :type="act.action === 'CLOSE' ? 'danger' : act.action === 'RESOLVE' || act.action === 'ACCEPT' ? 'success' : 'primary'"
                @click="openActionDialog(act.action)"
                style="margin: 4px; width: 100%"
              >
                {{ act.label }}
              </el-button>
              <el-empty v-if="availableActions.length === 0" :description="$t('ticket.empty.noActions')" :image-size="60" />
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 操作弹窗 -->
      <el-dialog v-model="actionDialogVisible" :title="ACTION_LABELS[currentAction] || $t('ticket.dialog.titleAction')" width="450px">
        <el-form label-width="80px">
          <el-form-item v-if="needsHandler(currentAction)" :label="$t('ticket.field.handlerId')" required>
            <el-input v-model="actionForm.handlerId" :placeholder="$t('ticket.placeholder.handlerId')" />
          </el-form-item>
          <el-form-item v-if="needsHandler(currentAction)" :label="$t('ticket.field.handlerName')">
            <el-input v-model="actionForm.handlerName" :placeholder="$t('ticket.placeholder.handlerName')" />
          </el-form-item>
          <el-form-item v-if="currentAction === 'EVALUATE'" :label="$t('ticket.field.score')" required>
            <el-rate v-model="actionForm.satisfactionScore" />
          </el-form-item>
          <el-form-item v-if="currentAction === 'EVALUATE'" :label="$t('ticket.field.evaluateContent')">
            <el-input v-model="actionForm.satisfactionComment" type="textarea" :rows="2" :placeholder="$t('ticket.placeholder.evaluateContent')" />
          </el-form-item>
          <el-form-item :label="$t('ticket.field.comment')">
            <el-input v-model="actionForm.comment" type="textarea" :rows="3" :placeholder="$t('ticket.placeholder.comment')" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="actionDialogVisible = false">{{ $t('ticket.action.cancel') }}</el-button>
          <el-button type="primary" @click="handleAction">{{ $t('ticket.action.confirmAction') }}</el-button>
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
.action-buttons {
  display: flex;
  flex-direction: column;
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
</style>
