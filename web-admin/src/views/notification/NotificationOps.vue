<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Refresh, Promotion, Bell, Document, Delete } from '@element-plus/icons-vue'
import {
  sendNotification,
  getNotificationStats,
  pageMessageTemplates,
  createMessageTemplate,
  deleteMessageTemplate,
  pageDispatchLogs,
  retryDispatch,
  resolveDispatch,
  pageSubscriptions,
  saveSubscription,
  deleteSubscription,
  type SendNotificationRequest,
  type SendNotificationVO,
  type NotificationStatsVO,
  type MessageTemplate,
  type SaveTemplateRequest,
  type DispatchLog,
  type Subscription,
  type SaveSubscriptionRequest,
} from '@/api/notification'

const { t } = useI18n()

/**
 * 通知运营页。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知 + 44-实时通信与消息推送设计
 *
 * 4 个 Tab:
 *  1. 运营监控: 8 项指标统计看板 (模板数/今日发送/失败/待重试/死信/未读/订阅数)
 *  2. 发送通知: 模板代码 + 接收人 + 变量 JSON + 发送按钮 + 结果展示
 *  3. 分发日志: 分页查询 + 状态/渠道筛选 + 重试/解决按钮
 *  4. 模板与订阅: 模板列表 + 创建弹窗 + 当前用户订阅管理
 *
 * 核心能力验证 (6 项):
 *  - 站内信: 发送通知 Tab 创建 sys_message
 *  - 未读数: 运营监控 Tab currentUserUnreadCount
 *  - 实时推送: 发送通知结果 realtimePushed=true
 *  - 移动端订阅消息: 发送通知结果 mobilePushTriggered=true
 *  - 消息模板: 模板与订阅 Tab 模板列表 + 创建
 *  - 消息重试: 分发日志 Tab retry/resolve 按钮
 */

const activeTab = ref<'stats' | 'send' | 'dispatches' | 'templates'>('stats')

// ==================== 运营监控 ====================
const stats = ref<NotificationStatsVO>({})
const statsLoading = ref(false)

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await getNotificationStats()
  } catch (e) {
    console.error('loadStats failed', e)
  } finally {
    statsLoading.value = false
  }
}

// ==================== 发送通知 ====================
const sendForm = reactive<SendNotificationRequest>({
  templateCode: 'BIZ_REQUEST_SUBMITTED',
  receiverId: 'mock-user-admin',
  variables: {
    requestNo: 'REQ-DEMO-001',
    applicantName: t('notification.send.sample.applicantName'),
    amount: '1000',
  },
  bizType: 'notification_ops',
  bizId: '',
})
// el-input v-model 需要 string 类型, 但 sendForm.variables 是 Record<string, string>, 用 variablesJson 中转
const variablesJson = ref(JSON.stringify(sendForm.variables, null, 2))
const sendLoading = ref(false)
const sendResult = ref<SendNotificationVO | null>(null)

async function handleSend() {
  if (!sendForm.templateCode.trim() || !sendForm.receiverId.trim()) {
    ElMessage.warning(t('notification.msg.templateCodeAndReceiverRequired'))
    return
  }
  // 解析 variablesJson 字符串为对象
  try {
    sendForm.variables = JSON.parse(variablesJson.value)
  } catch {
    ElMessage.error(t('notification.msg.variablesJsonParseFailed'))
    return
  }
  sendLoading.value = true
  sendResult.value = null
  try {
    sendResult.value = await sendNotification(sendForm)
    ElMessage.success(t('notification.msg.sendSuccess'))
    loadStats()
    loadDispatches()
  } catch (e: any) {
    ElMessage.error(t('notification.msg.sendFailed', { message: e?.message || t('notification.msg.unknownError') }))
  } finally {
    sendLoading.value = false
  }
}

// ==================== 分发日志 ====================
const dispatchList = ref<DispatchLog[]>([])
const dispatchTotal = ref(0)
const dispatchLoading = ref(false)
const dispatchQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  dispatchStatus: '',
  channel: '',
  receiverId: '',
})

async function loadDispatches() {
  dispatchLoading.value = true
  try {
    const params: any = { ...dispatchQuery }
    if (!params.dispatchStatus) delete params.dispatchStatus
    if (!params.channel) delete params.channel
    if (!params.receiverId) delete params.receiverId
    const res = await pageDispatchLogs(params)
    dispatchList.value = res.records as DispatchLog[]
    dispatchTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadDispatches failed', e)
  } finally {
    dispatchLoading.value = false
  }
}

function handleDispatchPageChange(page: number) {
  dispatchQuery.pageNo = page
  loadDispatches()
}

function dispatchStatusTagType(status: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'SENT': return 'success'
    case 'PENDING': return 'info'
    case 'FAILED': return 'warning'
    case 'RETRYING': return 'warning'
    case 'DEAD_LETTER': return 'danger'
    default: return 'primary'
  }
}

async function handleRetry(row: DispatchLog) {
  try {
    await ElMessageBox.confirm(t('notification.msg.confirmRetryDispatch', { id: row.id }), t('notification.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const updated = await retryDispatch(row.id)
    ElMessage.success(t('notification.msg.retryCompleted', { status: updated.dispatchStatus, retryCount: updated.retryCount }))
    loadDispatches()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('notification.msg.retryFailed', { message: e?.message || t('notification.msg.unknownError') }))
  }
}

async function handleResolve(row: DispatchLog) {
  try {
    await ElMessageBox.confirm(t('notification.msg.confirmResolveDeadLetter', { id: row.id }), t('notification.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await resolveDispatch(row.id)
    ElMessage.success(t('notification.msg.resolved'))
    loadDispatches()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('notification.msg.resolveFailed', { message: e?.message || t('notification.msg.unknownError') }))
  }
}

// ==================== 模板管理 ====================
const templateList = ref<MessageTemplate[]>([])
const templateTotal = ref(0)
const templateLoading = ref(false)
const templateQuery = reactive({ pageNo: 1, pageSize: 10, msgType: '' })
const templateDialogVisible = ref(false)
const templateForm = reactive<SaveTemplateRequest>({
  templateCode: '',
  templateName: '',
  msgType: 'BIZ',
  titleTemplate: '',
  contentTemplate: '',
  targetRouteId: '',
  priority: 'NORMAL',
  deliveryMode: 'PERSIST_THEN_PUSH',
  enabled: true,
  remark: '',
})

async function loadTemplates() {
  templateLoading.value = true
  try {
    const params: any = { ...templateQuery }
    if (!params.msgType) delete params.msgType
    const res = await pageMessageTemplates(params)
    templateList.value = res.records as MessageTemplate[]
    templateTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadTemplates failed', e)
  } finally {
    templateLoading.value = false
  }
}

function handleTemplatePageChange(page: number) {
  templateQuery.pageNo = page
  loadTemplates()
}

function openTemplateDialog() {
  templateForm.templateCode = ''
  templateForm.templateName = ''
  templateForm.msgType = 'BIZ'
  templateForm.titleTemplate = ''
  templateForm.contentTemplate = ''
  templateForm.targetRouteId = ''
  templateForm.priority = 'NORMAL'
  templateForm.deliveryMode = 'PERSIST_THEN_PUSH'
  templateForm.enabled = true
  templateForm.remark = ''
  templateDialogVisible.value = true
}

async function handleCreateTemplate() {
  if (!templateForm.templateCode || !templateForm.templateName || !templateForm.titleTemplate) {
    ElMessage.warning(t('notification.msg.templateRequiredFields'))
    return
  }
  try {
    await createMessageTemplate(templateForm)
    ElMessage.success(t('notification.msg.templateCreated'))
    templateDialogVisible.value = false
    loadTemplates()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('notification.msg.createFailed', { message: e?.message || t('notification.msg.unknownError') }))
  }
}

async function handleDeleteTemplate(row: MessageTemplate) {
  try {
    await ElMessageBox.confirm(t('notification.msg.confirmDeleteTemplate', { templateCode: row.templateCode }), t('notification.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteMessageTemplate(row.id)
    ElMessage.success(t('notification.msg.deleted'))
    loadTemplates()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('notification.msg.deleteFailed', { message: e?.message || t('notification.msg.unknownError') }))
  }
}

// ==================== 我的订阅 ====================
const subscriptionList = ref<Subscription[]>([])
const subscriptionTotal = ref(0)
const subscriptionLoading = ref(false)
const subscriptionQuery = reactive({ pageNo: 1, pageSize: 10, topic: '' })
const subscriptionDialogVisible = ref(false)
const subscriptionForm = reactive<SaveSubscriptionRequest>({
  topic: 'BIZ',
  channel: 'MOBILE_PUSH',
  enabled: true,
  deviceToken: 'mock-token-admin-001',
  remark: '',
})

async function loadSubscriptions() {
  subscriptionLoading.value = true
  try {
    const params: any = { ...subscriptionQuery }
    if (!params.topic) delete params.topic
    const res = await pageSubscriptions(params)
    subscriptionList.value = res.records as Subscription[]
    subscriptionTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadSubscriptions failed', e)
  } finally {
    subscriptionLoading.value = false
  }
}

function openSubscriptionDialog() {
  subscriptionForm.topic = 'BIZ'
  subscriptionForm.channel = 'MOBILE_PUSH'
  subscriptionForm.enabled = true
  subscriptionForm.deviceToken = 'mock-token-admin-001'
  subscriptionForm.remark = ''
  subscriptionDialogVisible.value = true
}

async function handleSaveSubscription() {
  if (!subscriptionForm.topic || !subscriptionForm.channel) {
    ElMessage.warning(t('notification.msg.topicAndChannelRequired'))
    return
  }
  try {
    await saveSubscription(subscriptionForm)
    ElMessage.success(t('notification.msg.subscriptionSaved'))
    subscriptionDialogVisible.value = false
    loadSubscriptions()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('notification.msg.saveFailed', { message: e?.message || t('notification.msg.unknownError') }))
  }
}

async function handleDeleteSubscription(row: Subscription) {
  try {
    await ElMessageBox.confirm(t('notification.msg.confirmDeleteSubscription', { topic: row.topic, channel: row.channel }), t('notification.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteSubscription(row.id)
    ElMessage.success(t('notification.msg.deleted'))
    loadSubscriptions()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('notification.msg.deleteFailed', { message: e?.message || t('notification.msg.unknownError') }))
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadStats()
  loadDispatches()
  loadTemplates()
  loadSubscriptions()
})
</script>

<template>
  <div class="notification-ops">
    <el-tabs v-model="activeTab" class="ops-tabs">
      <!-- ===== Tab 1: 运营监控 ===== -->
      <el-tab-pane :label="$t('notification.tab.stats')" name="stats">
        <div class="tab-header">
          <el-button :icon="Refresh" :loading="statsLoading" @click="loadStats">{{ $t('notification.action.refreshStats') }}</el-button>
        </div>
        <el-row :gutter="16" class="stats-row">
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('notification.stats.templateCount') }}</div>
                <div class="stat-value">{{ stats.templateCount ?? 0 }}</div>
                <div class="stat-sub">{{ $t('notification.stats.enabled', { count: stats.enabledTemplateCount ?? 0 }) }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('notification.stats.todaySend') }}</div>
                <div class="stat-value">{{ stats.todayMessageCount ?? 0 }}</div>
                <div class="stat-sub">{{ $t('notification.stats.readUnread', { read: stats.todayReadCount ?? 0, unread: stats.todayUnreadCount ?? 0 }) }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('notification.stats.todayDispatch') }}</div>
                <div class="stat-value">{{ stats.todayDispatchCount ?? 0 }}</div>
                <div class="stat-sub">{{ $t('notification.stats.failed', { count: stats.todayFailedDispatchCount ?? 0 }) }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('notification.stats.deadLetter') }}</div>
                <div class="stat-value danger">{{ stats.deadLetterCount ?? 0 }}</div>
                <div class="stat-sub">{{ $t('notification.stats.pendingRetry', { count: stats.pendingRetryCount ?? 0 }) }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" class="stats-row">
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('notification.stats.currentUserUnread') }}</div>
                <div class="stat-value warning">{{ stats.currentUserUnreadCount ?? 0 }}</div>
                <div class="stat-sub">{{ $t('notification.stats.realtimePushTip') }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('notification.stats.mySubscription') }}</div>
                <div class="stat-value">{{ stats.subscriptionCount ?? 0 }}</div>
                <div class="stat-sub">{{ $t('notification.stats.mobilePushTip') }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('notification.stats.coreCapabilities') }}</div>
                <div class="stat-value small">{{ $t('notification.stats.coreCapabilitiesDesc') }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-alert type="info" :closable="false" class="info-alert">
          <template #title>
            {{ $t('notification.stats.alertTitle') }}
          </template>
        </el-alert>
      </el-tab-pane>

      <!-- ===== Tab 2: 发送通知 ===== -->
      <el-tab-pane :label="$t('notification.tab.send')" name="send">
        <el-form :model="sendForm" label-width="120px" class="send-form">
          <el-form-item :label="$t('notification.send.field.templateCode')">
            <el-select v-model="sendForm.templateCode" :placeholder="$t('notification.send.placeholder.selectTemplate')" style="width: 100%">
              <el-option :label="$t('notification.send.template.bizRequest')" value="BIZ_REQUEST_SUBMITTED" />
              <el-option :label="$t('notification.send.template.approval')" value="APPROVAL_RESULT" />
              <el-option :label="$t('notification.send.template.export')" value="EXPORT_COMPLETE" />
              <el-option :label="$t('notification.send.template.system')" value="SYSTEM_ANNOUNCE" />
              <el-option :label="$t('notification.send.template.workflow')" value="WORKFLOW_NOTIFY" />
              <el-option :label="$t('notification.send.template.alert')" value="ALERT_THRESHOLD" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('notification.send.field.receiverId')">
            <el-input v-model="sendForm.receiverId" :placeholder="$t('notification.send.placeholder.receiverId')" />
          </el-form-item>
          <el-form-item :label="$t('notification.send.field.variablesJson')">
            <el-input
              v-model="variablesJson"
              type="textarea"
              :rows="6"
              :placeholder="'{&quot;requestNo&quot;:&quot;REQ-001&quot;,&quot;applicantName&quot;:&quot;' + $t('notification.send.sample.applicantName') + '&quot;,&quot;amount&quot;:&quot;1000&quot;}'"
            />
            <div class="form-tip">
              {{ $t('notification.send.tip.variablesPrefix') }}${'$'}{var}{{ $t('notification.send.tip.variablesSuffix') }}
            </div>
          </el-form-item>
          <el-form-item :label="$t('notification.send.field.bizType')">
            <el-input v-model="sendForm.bizType" :placeholder="$t('notification.send.placeholder.bizType')" />
          </el-form-item>
          <el-form-item :label="$t('notification.send.field.bizId')">
            <el-input v-model="sendForm.bizId" :placeholder="$t('notification.placeholder.optional')" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Promotion" :loading="sendLoading" @click="handleSend">
              {{ $t('notification.send.action.send') }}
            </el-button>
          </el-form-item>
        </el-form>

        <el-divider />

        <div v-if="sendResult" class="send-result">
          <h3>{{ $t('notification.send.result.title') }}</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item :label="$t('notification.field.messageId')">{{ sendResult.messageId }}</el-descriptions-item>
            <el-descriptions-item :label="$t('notification.field.msgType')">{{ sendResult.msgType }}</el-descriptions-item>
            <el-descriptions-item :label="$t('notification.field.receiver')">{{ sendResult.receiverId }}</el-descriptions-item>
            <el-descriptions-item :label="$t('notification.field.createdTime')">{{ sendResult.createdTime }}</el-descriptions-item>
            <el-descriptions-item :label="$t('notification.send.result.realtimePush')">
              <el-tag :type="sendResult.realtimePushed ? 'success' : 'danger'">
                {{ sendResult.realtimePushed ? $t('notification.tag.pushed') : $t('notification.tag.notPushed') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('notification.send.result.mobilePush')">
              <el-tag :type="sendResult.mobilePushTriggered ? 'success' : 'info'">
                {{ sendResult.mobilePushTriggered ? $t('notification.tag.triggered') : $t('notification.tag.notTriggered') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('notification.field.title')" :span="2">{{ sendResult.title }}</el-descriptions-item>
            <el-descriptions-item :label="$t('notification.send.result.content')" :span="2">{{ sendResult.content }}</el-descriptions-item>
            <el-descriptions-item :label="$t('notification.send.result.dispatchLogIds')" :span="2">
              <div v-for="id in sendResult.dispatchLogIds" :key="id" class="dispatch-id">{{ id }}</div>
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 3: 分发日志 ===== -->
      <el-tab-pane :label="$t('notification.tab.dispatches')" name="dispatches">
        <div class="tab-header">
          <el-select v-model="dispatchQuery.dispatchStatus" :placeholder="$t('notification.dispatch.placeholder.statusFilter')" clearable style="width: 160px">
            <el-option label="PENDING" value="PENDING" />
            <el-option label="SENT" value="SENT" />
            <el-option label="FAILED" value="FAILED" />
            <el-option label="RETRYING" value="RETRYING" />
            <el-option label="DEAD_LETTER" value="DEAD_LETTER" />
          </el-select>
          <el-select v-model="dispatchQuery.channel" :placeholder="$t('notification.dispatch.placeholder.channelFilter')" clearable style="width: 160px">
            <el-option label="IN_APP" value="IN_APP" />
            <el-option label="MOBILE_PUSH" value="MOBILE_PUSH" />
            <el-option label="SMS" value="SMS" />
            <el-option label="EMAIL" value="EMAIL" />
          </el-select>
          <el-input v-model="dispatchQuery.receiverId" :placeholder="$t('notification.dispatch.placeholder.receiverId')" clearable style="width: 200px" />
          <el-button :icon="Refresh" :loading="dispatchLoading" @click="loadDispatches">{{ $t('notification.action.query') }}</el-button>
        </div>
        <el-table :data="dispatchList" v-loading="dispatchLoading" border style="width: 100%">
          <el-table-column prop="id" label="ID" width="180" />
          <el-table-column prop="messageId" :label="$t('notification.field.messageId')" width="180" />
          <el-table-column prop="receiverId" :label="$t('notification.field.receiver')" width="160" />
          <el-table-column prop="channel" :label="$t('notification.field.channel')" width="120" />
          <el-table-column :label="$t('notification.field.status')" width="120">
            <template #default="{ row }">
              <el-tag :type="dispatchStatusTagType(row.dispatchStatus)">{{ row.dispatchStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('notification.dispatch.field.retry')" width="100">
            <template #default="{ row }">
              <span>{{ row.retryCount }} / {{ row.maxRetryCount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastError" :label="$t('notification.dispatch.field.error')" show-overflow-tooltip />
          <el-table-column prop="sentTime" :label="$t('notification.dispatch.field.sentTime')" width="180" />
          <el-table-column :label="$t('notification.field.action')" width="200" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.dispatchStatus === 'FAILED' || row.dispatchStatus === 'RETRYING'"
                type="warning"
                size="small"
                @click="handleRetry(row as DispatchLog)"
              >{{ $t('notification.dispatch.action.retry') }}</el-button>
              <el-button
                v-if="row.dispatchStatus === 'DEAD_LETTER' || row.dispatchStatus === 'FAILED' || row.dispatchStatus === 'RETRYING'"
                type="success"
                size="small"
                @click="handleResolve(row as DispatchLog)"
              >{{ $t('notification.dispatch.action.resolve') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pagination"
          v-model:current-page="dispatchQuery.pageNo"
          :page-size="dispatchQuery.pageSize"
          :total="dispatchTotal"
          layout="total, prev, pager, next"
          @current-change="handleDispatchPageChange"
        />
      </el-tab-pane>

      <!-- ===== Tab 4: 模板与订阅 ===== -->
      <el-tab-pane :label="$t('notification.tab.templates')" name="templates">
        <el-divider content-position="left">{{ $t('notification.template.divider.title') }}</el-divider>
        <div class="tab-header">
          <el-select v-model="templateQuery.msgType" :placeholder="$t('notification.template.placeholder.typeFilter')" clearable style="width: 160px">
            <el-option label="BIZ" value="BIZ" />
            <el-option label="APPROVAL" value="APPROVAL" />
            <el-option label="EXPORT" value="EXPORT" />
            <el-option label="SYSTEM" value="SYSTEM" />
            <el-option label="WORKFLOW" value="WORKFLOW" />
            <el-option label="ALERT" value="ALERT" />
          </el-select>
          <el-button :icon="Refresh" :loading="templateLoading" @click="loadTemplates">{{ $t('notification.action.query') }}</el-button>
          <el-button type="primary" :icon="Document" @click="openTemplateDialog">{{ $t('notification.template.action.create') }}</el-button>
        </div>
        <el-table :data="templateList" v-loading="templateLoading" border style="width: 100%">
          <el-table-column prop="templateCode" :label="$t('notification.template.field.code')" width="220" />
          <el-table-column prop="templateName" :label="$t('notification.template.field.name')" width="180" />
          <el-table-column prop="msgType" :label="$t('notification.template.field.type')" width="100" />
          <el-table-column prop="titleTemplate" :label="$t('notification.template.field.title')" show-overflow-tooltip />
          <el-table-column prop="priority" :label="$t('notification.template.field.priority')" width="100" />
          <el-table-column :label="$t('notification.template.field.enabled')" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? $t('notification.common.yes') : $t('notification.common.no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('notification.field.action')" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" size="small" :icon="Delete" @click="handleDeleteTemplate(row as MessageTemplate)" />
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pagination"
          v-model:current-page="templateQuery.pageNo"
          :page-size="templateQuery.pageSize"
          :total="templateTotal"
          layout="total, prev, pager, next"
          @current-change="handleTemplatePageChange"
        />

        <el-divider content-position="left">{{ $t('notification.subscription.divider.title') }}</el-divider>
        <div class="tab-header">
          <el-button :icon="Refresh" :loading="subscriptionLoading" @click="loadSubscriptions">{{ $t('notification.action.query') }}</el-button>
          <el-button type="primary" :icon="Bell" @click="openSubscriptionDialog">{{ $t('notification.subscription.action.create') }}</el-button>
        </div>
        <el-table :data="subscriptionList" v-loading="subscriptionLoading" border style="width: 100%">
          <el-table-column prop="id" label="ID" width="180" />
          <el-table-column prop="topic" :label="$t('notification.subscription.field.topic')" width="120" />
          <el-table-column prop="channel" :label="$t('notification.subscription.field.channel')" width="120" />
          <el-table-column :label="$t('notification.subscription.field.enabled')" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? $t('notification.common.yes') : $t('notification.common.no') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deviceToken" :label="$t('notification.subscription.field.deviceToken')" show-overflow-tooltip />
          <el-table-column prop="remark" :label="$t('notification.field.remark')" show-overflow-tooltip />
          <el-table-column :label="$t('notification.field.action')" width="100" fixed="right">
            <template #default="{ row }">
              <el-button type="danger" size="small" :icon="Delete" @click="handleDeleteSubscription(row as Subscription)" />
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pagination"
          v-model:current-page="subscriptionQuery.pageNo"
          :page-size="subscriptionQuery.pageSize"
          :total="subscriptionTotal"
          layout="total, prev, pager, next"
          @current-change="(p: number) => { subscriptionQuery.pageNo = p; loadSubscriptions() }"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- ===== 模板创建弹窗 ===== -->
    <el-dialog v-model="templateDialogVisible" :title="$t('notification.template.dialog.createTitle')" width="700px">
      <el-form :model="templateForm" label-width="120px">
        <el-form-item :label="$t('notification.template.field.code')">
          <el-input v-model="templateForm.templateCode" :placeholder="$t('notification.template.placeholder.codeExample')" />
        </el-form-item>
        <el-form-item :label="$t('notification.template.field.name')">
          <el-input v-model="templateForm.templateName" />
        </el-form-item>
        <el-form-item :label="$t('notification.template.field.msgType')">
          <el-select v-model="templateForm.msgType" style="width: 100%">
            <el-option :label="$t('notification.template.msgType.biz')" value="BIZ" />
            <el-option :label="$t('notification.template.msgType.approval')" value="APPROVAL" />
            <el-option :label="$t('notification.template.msgType.export')" value="EXPORT" />
            <el-option :label="$t('notification.template.msgType.system')" value="SYSTEM" />
            <el-option :label="$t('notification.template.msgType.workflow')" value="WORKFLOW" />
            <el-option :label="$t('notification.template.msgType.alert')" value="ALERT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('notification.template.field.title')">
          <el-input v-model="templateForm.titleTemplate" :placeholder="$t('notification.template.placeholder.titleExample')" />
        </el-form-item>
        <el-form-item :label="$t('notification.template.field.content')">
          <el-input v-model="templateForm.contentTemplate" type="textarea" :rows="4"
                    :placeholder="$t('notification.template.placeholder.contentExample')" />
        </el-form-item>
        <el-form-item :label="$t('notification.template.field.route')">
          <el-input v-model="templateForm.targetRouteId" :placeholder="$t('notification.template.placeholder.routeExample')" />
        </el-form-item>
        <el-form-item :label="$t('notification.template.field.priority')">
          <el-select v-model="templateForm.priority" style="width: 100%">
            <el-option label="LOW" value="LOW" />
            <el-option label="NORMAL" value="NORMAL" />
            <el-option label="HIGH" value="HIGH" />
            <el-option label="URGENT" value="URGENT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('notification.template.field.deliveryMode')">
          <el-select v-model="templateForm.deliveryMode" style="width: 100%">
            <el-option :label="$t('notification.template.deliveryMode.persistThenPush')" value="PERSIST_THEN_PUSH" />
            <el-option :label="$t('notification.template.deliveryMode.pushOnly')" value="PUSH_ONLY" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('notification.template.field.enabled')">
          <el-switch v-model="templateForm.enabled" />
        </el-form-item>
        <el-form-item :label="$t('notification.field.remark')">
          <el-input v-model="templateForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">{{ $t('notification.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreateTemplate">{{ $t('notification.template.action.confirmCreate') }}</el-button>
      </template>
    </el-dialog>

    <!-- ===== 订阅创建弹窗 ===== -->
    <el-dialog v-model="subscriptionDialogVisible" :title="$t('notification.subscription.dialog.createTitle')" width="500px">
      <el-form :model="subscriptionForm" label-width="120px">
        <el-form-item :label="$t('notification.subscription.field.topic')">
          <el-select v-model="subscriptionForm.topic" style="width: 100%">
            <el-option :label="$t('notification.template.msgType.biz')" value="BIZ" />
            <el-option :label="$t('notification.template.msgType.approval')" value="APPROVAL" />
            <el-option :label="$t('notification.template.msgType.export')" value="EXPORT" />
            <el-option :label="$t('notification.template.msgType.system')" value="SYSTEM" />
            <el-option :label="$t('notification.template.msgType.workflow')" value="WORKFLOW" />
            <el-option :label="$t('notification.template.msgType.alert')" value="ALERT" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('notification.subscription.field.channel')">
          <el-select v-model="subscriptionForm.channel" style="width: 100%">
            <el-option :label="$t('notification.subscription.channel.mobilePush')" value="MOBILE_PUSH" />
            <el-option :label="$t('notification.subscription.channel.sms')" value="SMS" />
            <el-option :label="$t('notification.subscription.channel.email')" value="EMAIL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('notification.subscription.field.deviceToken')">
          <el-input v-model="subscriptionForm.deviceToken" :placeholder="$t('notification.subscription.placeholder.deviceTokenExample')" />
        </el-form-item>
        <el-form-item :label="$t('notification.subscription.field.enabled')">
          <el-switch v-model="subscriptionForm.enabled" />
        </el-form-item>
        <el-form-item :label="$t('notification.field.remark')">
          <el-input v-model="subscriptionForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subscriptionDialogVisible = false">{{ $t('notification.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleSaveSubscription">{{ $t('notification.subscription.action.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.notification-ops {
  padding: 16px;
}
.ops-tabs {
  margin-top: 8px;
}
.tab-header {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
}
.stats-row {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
  padding: 8px 0;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}
.stat-value.danger {
  color: #f56c6c;
}
.stat-value.warning {
  color: #e6a23c;
}
.stat-value.small {
  font-size: 14px;
  font-weight: 400;
  line-height: 1.6;
}
.stat-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
.info-alert {
  margin-top: 16px;
}
.send-form {
  max-width: 800px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.send-result {
  margin-top: 16px;
}
.send-result h3 {
  margin: 0 0 12px 0;
  font-size: 16px;
}
.dispatch-id {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
