<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Refresh, Plus, Close, Lock, Coin, Upload } from '@element-plus/icons-vue'
import {
  pagePayOrders,
  createPayOrder,
  cancelPayOrder,
  closePayOrder,
  handlePayCallback,
  pagePayCallbacks,
  refundPayOrder,
  pagePayRefunds,
  importPayReconciliation,
  pagePayReconciliations,
  getPaymentStats,
  type PayOrder,
  type PayCallbackLog,
  type PayRefundOrder,
  type PayReconciliation,
  type PaymentStatsVO,
  type CreateOrderRequest,
  type CallbackRequest,
  type RefundRequest,
  type ReconciliationImportRequest,
} from '@/api/payment'

const { t } = useI18n()

/**
 * 支付订单运营页。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)
 *
 * 4 个 Tab:
 *  1. 运营监控: 6 项核心能力指标 (订单/状态/渠道/金额/回调/对账)
 *  2. 支付单管理: 状态机 (创建 PENDING → 取消 CANCELLED → 关闭 CLOSED) + 分页查询
 *  3. 回调与退款: 模拟第三方回调 (HMAC-SHA256 验签 + 幂等) + 发起退款 (累计金额校验)
 *  4. 对账导入: 文件明细输入 + 比对结果 (matched/mismatched/missing/extra)
 *
 * 核心能力验证 (6 项):
 *  - 支付单状态机: 支付单管理 Tab 创建/取消/关闭
 *  - 第三方回调验签: 回调与退款 Tab 模拟回调 (Mock 渠道 secretKey)
 *  - 回调幂等: 重复回调返回已有记录 (idempotencyKey 命中)
 *  - 对账文件导入: 对账导入 Tab 文件明细 + matched/mismatched/missing/extra
 *  - 金额精度: 全程 Long 分单位 (1 元 = 100 分), 表单输入元 → 后端分
 *  - 安全审计: @Auditable AOP 自动写入 sys_operation_log (Controller 层)
 */

const activeTab = ref<'stats' | 'orders' | 'callbacks' | 'reconciliation'>('stats')

// 金额单位换算: 1 元 = 100 分
const YUAN_TO_FEN = 100

// 渠道默认 secretKey 映射 (与 V017 种子数据 channelConfig 对齐)
const CHANNEL_DEFAULTS: Record<string, { accessKey: string; secretKey: string }> = {
  MOCK_ALIPAY: { accessKey: 'mock-alipay-ak', secretKey: 'mock-alipay-sk-32bytes-xxxxx' },
  MOCK_WECHAT: { accessKey: 'mock-wechat-ak', secretKey: 'mock-wechat-sk-32bytes-xxxxx' },
  MOCK_UNIONPAY: { accessKey: 'mock-unionpay-ak', secretKey: 'mock-unionpay-sk-32bytes-x' },
}

// ==================== 运营监控 ====================
const stats = ref<PaymentStatsVO>({})
const statsLoading = ref(false)

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await getPaymentStats()
  } catch (e) {
    console.error('loadStats failed', e)
  } finally {
    statsLoading.value = false
  }
}

function formatAmount(fen?: number): string {
  if (fen == null) return '0.00'
  return (fen / YUAN_TO_FEN).toFixed(2)
}

// ==================== 支付单管理 ====================
const orderList = ref<PayOrder[]>([])
const orderTotal = ref(0)
const orderLoading = ref(false)
const orderQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  orderNo: '',
  channel: '',
  status: '',
  bizType: '',
})

const orderDialogVisible = ref(false)
const orderForm = reactive<CreateOrderRequest>({
  bizType: 'biz_request',
  bizId: '',
  channel: 'MOCK_ALIPAY',
  amount: 100, // 单位: 分
  currency: 'CNY',
  subject: '',
  payerId: '',
  idempotencyKey: '',
  channelConfig: '',
  extraParams: '',
  remark: '',
})
// 元输入中转 (避免直接操作分单位)
const orderAmountYuan = ref(1)
const orderDialogOpen = () => {
  orderForm.bizType = 'biz_request'
  orderForm.bizId = ''
  orderForm.channel = 'MOCK_ALIPAY'
  orderForm.amount = 100
  orderAmountYuan.value = 1
  orderForm.currency = 'CNY'
  orderForm.subject = ''
  orderForm.payerId = ''
  orderForm.idempotencyKey = 'idem-' + Date.now()
  orderForm.channelConfig = JSON.stringify(CHANNEL_DEFAULTS['MOCK_ALIPAY'])
  orderForm.extraParams = ''
  orderForm.remark = ''
  orderDialogVisible.value = true
}

async function loadOrders() {
  orderLoading.value = true
  try {
    const params: any = { ...orderQuery }
    if (!params.orderNo) delete params.orderNo
    if (!params.channel) delete params.channel
    if (!params.status) delete params.status
    if (!params.bizType) delete params.bizType
    const res = await pagePayOrders(params)
    orderList.value = res.records as PayOrder[]
    orderTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadOrders failed', e)
  } finally {
    orderLoading.value = false
  }
}

function handleOrderPageChange(page: number) {
  orderQuery.pageNo = page
  loadOrders()
}

async function handleCreateOrder() {
  if (!orderForm.subject.trim()) {
    ElMessage.warning(t('payment.msg.orderSubjectRequired'))
    return
  }
  if (orderAmountYuan.value <= 0) {
    ElMessage.warning(t('payment.msg.amountMustBePositive'))
    return
  }
  // 元 → 分
  orderForm.amount = Math.round(orderAmountYuan.value * YUAN_TO_FEN)
  // 渠道凭证自动填充 (若用户未改)
  if (!orderForm.channelConfig || !orderForm.channelConfig.trim()) {
    orderForm.channelConfig = JSON.stringify(CHANNEL_DEFAULTS[orderForm.channel] || {})
  }
  try {
    const order = await createPayOrder(orderForm)
    ElMessage.success(t('payment.msg.orderCreated', { orderNo: order.orderNo }))
    orderDialogVisible.value = false
    loadOrders()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('payment.msg.createFailed', { message: e?.message || t('payment.msg.unknownError') }))
  }
}

async function handleCancelOrder(row: PayOrder) {
  try {
    await ElMessageBox.confirm(t('payment.msg.confirmCancelOrder', { orderNo: row.orderNo }), t('payment.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const updated = await cancelPayOrder(row.id)
    ElMessage.success(t('payment.msg.cancelled', { status: updated.status }))
    loadOrders()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('payment.msg.cancelFailed', { message: e?.message || t('payment.msg.unknownError') }))
  }
}

async function handleCloseOrder(row: PayOrder) {
  try {
    await ElMessageBox.confirm(t('payment.msg.confirmCloseOrder', { orderNo: row.orderNo }), t('payment.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const updated = await closePayOrder(row.id)
    ElMessage.success(t('payment.msg.closed', { status: updated.status }))
    loadOrders()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('payment.msg.closeFailed', { message: e?.message || t('payment.msg.unknownError') }))
  }
}

function orderStatusType(status: string): 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined {
  switch (status) {
    case 'PAID': return 'success'
    case 'PENDING': return 'warning'
    case 'FAILED':
    case 'CANCELLED': return 'info'
    case 'REFUNDING':
    case 'REFUNDED': return 'primary'
    case 'CLOSED': return 'info'
    default: return undefined
  }
}

// ==================== 回调与退款 ====================
const callbackList = ref<PayCallbackLog[]>([])
const callbackTotal = ref(0)
const callbackLoading = ref(false)
const callbackQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  orderNo: '',
  channel: '',
  verifyResult: '',
  processResult: '',
})

// 模拟第三方回调表单
const callbackForm = reactive<CallbackRequest>({
  orderNo: '',
  channel: 'MOCK_ALIPAY',
  channelTradeNo: '',
  callbackAction: 'PAY_SUCCESS',
  amount: 0, // 单位: 分
  callbackTimestamp: '',
  signature: '',
  rawPayload: '',
  failReason: '',
})
const callbackAmountYuan = ref(0)
const callbackDialogVisible = ref(false)
// 手动签名输入 (前端不计算 HMAC, 由用户从 Mock 第三方系统获取; 或留空触发后端 FAILED)
const callbackSignMode = ref<'auto' | 'manual' | 'wrong'>('auto')

// 退款表单
const refundDialogVisible = ref(false)
const refundForm = reactive<RefundRequest>({
  originalOrderId: '',
  refundAmount: 0,
  reason: '',
  remark: '',
})
const refundAmountYuan = ref(0)

// 退款单列表
const refundList = ref<PayRefundOrder[]>([])
const refundTotal = ref(0)
const refundLoading = ref(false)
const refundQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  refundNo: '',
  originalOrderId: '',
  status: '',
})

async function loadCallbacks() {
  callbackLoading.value = true
  try {
    const params: any = { ...callbackQuery }
    if (!params.orderNo) delete params.orderNo
    if (!params.channel) delete params.channel
    if (!params.verifyResult) delete params.verifyResult
    if (!params.processResult) delete params.processResult
    const res = await pagePayCallbacks(params)
    callbackList.value = res.records as PayCallbackLog[]
    callbackTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadCallbacks failed', e)
  } finally {
    callbackLoading.value = false
  }
}

async function loadRefunds() {
  refundLoading.value = true
  try {
    const params: any = { ...refundQuery }
    if (!params.refundNo) delete params.refundNo
    if (!params.originalOrderId) delete params.originalOrderId
    if (!params.status) delete params.status
    const res = await pagePayRefunds(params)
    refundList.value = res.records as PayRefundOrder[]
    refundTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadRefunds failed', e)
  } finally {
    refundLoading.value = false
  }
}

function handleCallbackPageChange(page: number) {
  callbackQuery.pageNo = page
  loadCallbacks()
}

function handleRefundPageChange(page: number) {
  refundQuery.pageNo = page
  loadRefunds()
}

/**
 * 打开模拟回调对话框: 自动填充订单号 + 金额 + 时间戳
 */
function openCallbackDialog(row?: PayOrder) {
  if (row) {
    callbackForm.orderNo = row.orderNo
    callbackForm.channel = row.channel
    callbackForm.amount = row.amount
    callbackAmountYuan.value = row.amount / YUAN_TO_FEN
    callbackForm.channelTradeNo = row.channelTradeNo || ('MOCK-TRADE-' + Date.now())
  } else {
    callbackForm.orderNo = ''
    callbackForm.channel = 'MOCK_ALIPAY'
    callbackForm.amount = 0
    callbackAmountYuan.value = 0
    callbackForm.channelTradeNo = ''
  }
  callbackForm.callbackAction = 'PAY_SUCCESS'
  callbackForm.callbackTimestamp = formatTimestamp(new Date())
  callbackForm.signature = ''
  callbackForm.rawPayload = ''
  callbackForm.failReason = ''
  callbackSignMode.value = 'auto'
  callbackDialogVisible.value = true
}

function formatTimestamp(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

async function handleSendCallback() {
  if (!callbackForm.orderNo.trim()) {
    ElMessage.warning(t('payment.msg.orderNoRequired'))
    return
  }
  // 元 → 分
  callbackForm.amount = Math.round(callbackAmountYuan.value * YUAN_TO_FEN)
  // 根据签名模式构造 signature
  // - auto: 实际项目中由第三方系统用 secretKey HMAC-SHA256 计算后传入; 此处用占位字符串
  //   后端会用真实 secretKey 重算并比对, 验签会失败 (FAILED), 用于演示验签能力
  // - manual: 用户手填, 同上, 通常也会 FAILED
  // - wrong: 故意填错, 验签 FAILED (用于冒烟测试)
  if (callbackSignMode.value === 'auto') {
    callbackForm.signature = 'mock-signature-' + Date.now()
  } else if (callbackSignMode.value === 'wrong') {
    callbackForm.signature = 'intentionally-wrong-signature'
  }
  // rawPayload
  callbackForm.rawPayload = JSON.stringify({
    orderNo: callbackForm.orderNo,
    channelTradeNo: callbackForm.channelTradeNo,
    amount: callbackForm.amount,
    callbackAction: callbackForm.callbackAction,
    callbackTimestamp: callbackForm.callbackTimestamp,
  })
  try {
    const result = await handlePayCallback(callbackForm)
    ElMessage.success(t('payment.msg.callbackProcessed', { verifyResult: result.verifyResult, processResult: result.processResult }))
    callbackDialogVisible.value = false
    loadCallbacks()
    loadOrders()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('payment.msg.callbackFailed', { message: e?.message || t('payment.msg.unknownError') }))
  }
}

function openRefundDialog(row: PayOrder) {
  refundForm.originalOrderId = row.id
  refundForm.refundAmount = 0
  refundAmountYuan.value = 0
  refundForm.reason = ''
  refundForm.remark = ''
  refundDialogVisible.value = true
}

async function handleRefund() {
  if (refundAmountYuan.value <= 0) {
    ElMessage.warning(t('payment.msg.refundAmountMustBePositive'))
    return
  }
  if (!refundForm.reason.trim()) {
    ElMessage.warning(t('payment.msg.refundReasonRequired'))
    return
  }
  refundForm.refundAmount = Math.round(refundAmountYuan.value * YUAN_TO_FEN)
  try {
    const refund = await refundPayOrder(refundForm)
    ElMessage.success(t('payment.msg.refundCreated', { refundNo: refund.refundNo, status: refund.status }))
    refundDialogVisible.value = false
    loadRefunds()
    loadOrders()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('payment.msg.refundFailed', { message: e?.message || t('payment.msg.unknownError') }))
  }
}

function verifyResultType(v: string): 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined {
  return v === 'SUCCESS' ? 'success' : 'danger'
}

function processResultType(p: string): 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined {
  switch (p) {
    case 'PROCESSED': return 'success'
    case 'IGNORED': return 'info'
    case 'ERROR': return 'danger'
    default: return undefined
  }
}

// ==================== 对账导入 ====================
const reconList = ref<PayReconciliation[]>([])
const reconTotal = ref(0)
const reconLoading = ref(false)
const reconQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  channel: '',
  status: '',
  startDate: '',
  endDate: '',
})

const reconDialogVisible = ref(false)
const reconForm = reactive<ReconciliationImportRequest>({
  reconDate: new Date().toISOString().slice(0, 10),
  channel: 'MOCK_ALIPAY',
  fileName: 'mock-recon.csv',
  items: [],
})
// 明细输入: 多行 CSV 文本 (orderNo,channelTradeNo,amount,status)
const reconItemsText = ref('PO20260719000001,ALIPAY-TRADE-001,25000,PAID')

async function loadReconciliations() {
  reconLoading.value = true
  try {
    const params: any = { ...reconQuery }
    if (!params.channel) delete params.channel
    if (!params.status) delete params.status
    if (!params.startDate) delete params.startDate
    if (!params.endDate) delete params.endDate
    const res = await pagePayReconciliations(params)
    reconList.value = res.records as PayReconciliation[]
    reconTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadReconciliations failed', e)
  } finally {
    reconLoading.value = false
  }
}

function handleReconPageChange(page: number) {
  reconQuery.pageNo = page
  loadReconciliations()
}

function openReconDialog() {
  reconForm.reconDate = new Date().toISOString().slice(0, 10)
  reconForm.channel = 'MOCK_ALIPAY'
  reconForm.fileName = 'mock-recon.csv'
  reconItemsText.value = 'PO20260719000001,ALIPAY-TRADE-001,25000,PAID'
  reconForm.items = []
  reconDialogVisible.value = true
}

function parseReconItems() {
  const items = reconItemsText.value
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const [orderNo, channelTradeNo, amount, status] = line.split(',').map((s) => s?.trim() || '')
      return {
        orderNo,
        channelTradeNo: channelTradeNo || undefined,
        amount: amount ? Number(amount) : undefined,
        status: status || undefined,
      }
    })
  return items
}

async function handleImportRecon() {
  const items = parseReconItems()
  if (items.length === 0) {
    ElMessage.warning(t('payment.msg.reconDetailRequired'))
    return
  }
  reconForm.items = items
  try {
    const result = await importPayReconciliation(reconForm)
    ElMessage.success(t('payment.msg.reconImportCompleted', { status: result.status, matchedCount: result.matchedCount, mismatchedCount: result.mismatchedCount, missingCount: result.missingCount, extraCount: result.extraCount }))
    reconDialogVisible.value = false
    loadReconciliations()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('payment.msg.reconImportFailed', { message: e?.message || t('payment.msg.unknownError') }))
  }
}

function reconStatusType(status: string): 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined {
  switch (status) {
    case 'MATCHED': return 'success'
    case 'MISMATCHED': return 'danger'
    case 'IMPORTED': return 'primary'
    case 'PENDING': return 'warning'
    case 'FAILED': return 'info'
    default: return undefined
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadStats()
  loadOrders()
  loadCallbacks()
  loadRefunds()
  loadReconciliations()
})
</script>

<template>
  <div class="payment-ops">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ===== Tab 1: 运营监控 ===== -->
      <el-tab-pane :label="$t('payment.tab.stats')" name="stats">
        <div class="tab-header">
          <el-button :icon="Refresh" :loading="statsLoading" @click="loadStats">{{ $t('payment.action.refreshStats') }}</el-button>
        </div>
        <el-row :gutter="16" class="stats-row">
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('payment.stats.totalOrders') }}</div>
                <div class="stat-value">{{ stats.totalOrders ?? 0 }}</div>
                <div class="stat-sub">PENDING {{ stats.statusCounts?.PENDING ?? 0 }} / PAID {{ stats.statusCounts?.PAID ?? 0 }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('payment.stats.totalPaidAmount') }}</div>
                <div class="stat-value">{{ formatAmount(stats.totalAmountPaid) }}</div>
                <div class="stat-sub">FAILED {{ stats.statusCounts?.FAILED ?? 0 }} / CANCELLED {{ stats.statusCounts?.CANCELLED ?? 0 }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('payment.stats.totalRefundedAmount') }}</div>
                <div class="stat-value warning">{{ formatAmount(stats.totalAmountRefunded) }}</div>
                <div class="stat-sub">{{ $t('payment.stats.refundCount', { count: stats.totalRefunds ?? 0 }) }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('payment.stats.callbackVerifyFailed') }}</div>
                <div class="stat-value danger">{{ stats.callbackVerifyFailed ?? 0 }}</div>
                <div class="stat-sub">{{ $t('payment.stats.callbackTotal', { count: stats.callbackTotal ?? 0 }) }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="16" class="stats-row">
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('payment.stats.channelDistribution') }}</div>
                <div class="stat-value small">
                  {{ $t('payment.channel.alipay') }} {{ stats.channelCounts?.MOCK_ALIPAY ?? 0 }} /
                  {{ $t('payment.channel.wechat') }} {{ stats.channelCounts?.MOCK_WECHAT ?? 0 }} /
                  {{ $t('payment.channel.unionpay') }} {{ stats.channelCounts?.MOCK_UNIONPAY ?? 0 }}
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('payment.stats.reconRecords') }}</div>
                <div class="stat-value">
                  {{ $t('payment.stats.matchedMismatched', { matched: stats.reconMatchedCount ?? 0, mismatched: stats.reconMismatchedCount ?? 0 }) }}
                </div>
                <div class="stat-sub">{{ $t('payment.stats.reconTotal', { count: stats.reconTotal ?? 0 }) }}</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="hover">
              <div class="stat-card">
                <div class="stat-label">{{ $t('payment.stats.coreCapabilities') }}</div>
                <div class="stat-value small">{{ $t('payment.stats.coreCapabilitiesDesc') }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-alert type="info" :closable="false" class="info-alert">
          <template #title>
            {{ $t('payment.stats.alertTitle') }}
          </template>
        </el-alert>
      </el-tab-pane>

      <!-- ===== Tab 2: 支付单管理 ===== -->
      <el-tab-pane :label="$t('payment.tab.orders')" name="orders">
        <div class="tab-header">
          <el-button type="primary" :icon="Plus" @click="orderDialogOpen">{{ $t('payment.order.action.createPending') }}</el-button>
          <el-button :icon="Refresh" :loading="orderLoading" @click="loadOrders">{{ $t('payment.action.refresh') }}</el-button>
        </div>
        <el-form :inline="true" :model="orderQuery" class="filter-form">
          <el-form-item :label="$t('payment.order.field.orderNo')">
            <el-input v-model="orderQuery.orderNo" :placeholder="$t('payment.order.placeholder.exactMatch')" clearable style="width: 200px" />
          </el-form-item>
          <el-form-item :label="$t('payment.field.channel')">
            <el-select v-model="orderQuery.channel" :placeholder="$t('payment.placeholder.all')" clearable style="width: 160px">
              <el-option :label="$t('payment.channel.mockAlipay')" value="MOCK_ALIPAY" />
              <el-option :label="$t('payment.channel.mockWechat')" value="MOCK_WECHAT" />
              <el-option :label="$t('payment.channel.mockUnionpay')" value="MOCK_UNIONPAY" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('payment.field.status')">
            <el-select v-model="orderQuery.status" :placeholder="$t('payment.placeholder.all')" clearable style="width: 140px">
              <el-option :label="$t('payment.order.status.pending')" value="PENDING" />
              <el-option :label="$t('payment.order.status.paid')" value="PAID" />
              <el-option :label="$t('payment.order.status.failed')" value="FAILED" />
              <el-option :label="$t('payment.order.status.cancelled')" value="CANCELLED" />
              <el-option :label="$t('payment.order.status.refunding')" value="REFUNDING" />
              <el-option :label="$t('payment.order.status.refunded')" value="REFUNDED" />
              <el-option :label="$t('payment.order.status.closed')" value="CLOSED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="orderQuery.pageNo = 1; loadOrders()">{{ $t('payment.action.query') }}</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="orderList" v-loading="orderLoading" border stripe>
          <el-table-column prop="orderNo" :label="$t('payment.order.field.orderNo')" width="200" />
          <el-table-column prop="channel" :label="$t('payment.field.channel')" width="130" />
          <el-table-column :label="$t('payment.order.field.amount')" width="120">
            <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="subject" :label="$t('payment.order.field.subject')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="status" :label="$t('payment.field.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="orderStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="channelTradeNo" :label="$t('payment.order.field.channelTradeNo')" width="170" show-overflow-tooltip />
          <el-table-column prop="createdTime" :label="$t('payment.field.createdTime')" width="170" />
          <el-table-column :label="$t('payment.field.action')" width="240" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 'PENDING'"
                type="warning"
                size="small"
                :icon="Close"
                @click="handleCancelOrder(row as PayOrder)"
              >{{ $t('payment.action.cancel') }}</el-button>
              <el-button
                v-if="row.status === 'PAID' || row.status === 'REFUNDED'"
                type="info"
                size="small"
                :icon="Lock"
                @click="handleCloseOrder(row as PayOrder)"
              >{{ $t('payment.order.action.close') }}</el-button>
              <el-button
                v-if="row.status === 'PAID' || row.status === 'REFUNDING'"
                type="primary"
                size="small"
                :icon="Coin"
                @click="openRefundDialog(row as PayOrder)"
              >{{ $t('payment.order.action.refund') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="pagination"
          v-model:current-page="orderQuery.pageNo"
          :page-size="orderQuery.pageSize"
          :total="orderTotal"
          layout="total, prev, pager, next, jumper"
          @current-change="handleOrderPageChange"
        />

        <!-- 创建支付单对话框 -->
        <el-dialog v-model="orderDialogVisible" :title="$t('payment.order.dialog.create')" width="600px">
          <el-form :model="orderForm" label-width="120px">
            <el-form-item :label="$t('payment.order.field.bizType')">
              <el-select v-model="orderForm.bizType" style="width: 100%">
                <el-option :label="$t('payment.order.bizType.bizRequest')" value="biz_request" />
                <el-option :label="$t('payment.order.bizType.contract')" value="contract" />
                <el-option :label="$t('payment.order.bizType.workTicket')" value="work_ticket" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.bizId')">
              <el-input v-model="orderForm.bizId" :placeholder="$t('payment.placeholder.optional')" />
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.payChannel')">
              <el-select v-model="orderForm.channel" style="width: 100%">
                <el-option :label="$t('payment.channel.mockAlipay')" value="MOCK_ALIPAY" />
                <el-option :label="$t('payment.channel.mockWechat')" value="MOCK_WECHAT" />
                <el-option :label="$t('payment.channel.mockUnionpay')" value="MOCK_UNIONPAY" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.amount')">
              <el-input-number v-model="orderAmountYuan" :min="0.01" :step="1" :precision="2" />
              <span class="form-tip">{{ $t('payment.order.tip.amountUnit') }}</span>
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.orderTitle')">
              <el-input v-model="orderForm.subject" :placeholder="$t('payment.order.placeholder.subject')" />
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.payerId')">
              <el-input v-model="orderForm.payerId" :placeholder="$t('payment.placeholder.optional')" />
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.idempotencyKey')">
              <el-input v-model="orderForm.idempotencyKey" :placeholder="$t('payment.order.placeholder.idempotencyKey')" />
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.channelConfig')">
              <el-input v-model="orderForm.channelConfig" type="textarea" :rows="2" />
              <div class="form-tip">{{ $t('payment.order.tip.channelConfig') }}</div>
            </el-form-item>
            <el-form-item :label="$t('payment.field.remark')">
              <el-input v-model="orderForm.remark" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="orderDialogVisible = false">{{ $t('payment.action.cancel') }}</el-button>
            <el-button type="primary" @click="handleCreateOrder">{{ $t('payment.order.action.create') }}</el-button>
          </template>
        </el-dialog>

        <!-- 退款对话框 -->
        <el-dialog v-model="refundDialogVisible" :title="$t('payment.refund.dialog.title')" width="500px">
          <el-form :model="refundForm" label-width="120px">
            <el-form-item :label="$t('payment.refund.field.originalOrderId')">
              <el-input v-model="refundForm.originalOrderId" readonly />
            </el-form-item>
            <el-form-item :label="$t('payment.refund.field.amount')">
              <el-input-number v-model="refundAmountYuan" :min="0.01" :step="1" :precision="2" />
              <span class="form-tip">{{ $t('payment.refund.tip.amountLimit') }}</span>
            </el-form-item>
            <el-form-item :label="$t('payment.refund.field.reason')">
              <el-input v-model="refundForm.reason" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item :label="$t('payment.field.remark')">
              <el-input v-model="refundForm.remark" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="refundDialogVisible = false">{{ $t('payment.action.cancel') }}</el-button>
            <el-button type="primary" @click="handleRefund">{{ $t('payment.refund.action.refund') }}</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- ===== Tab 3: 回调与退款 ===== -->
      <el-tab-pane :label="$t('payment.tab.callbacks')" name="callbacks">
        <el-alert type="info" :closable="false" class="info-alert">
          <template #title>
            {{ $t('payment.callback.alertTitle') }}
          </template>
        </el-alert>
        <div class="tab-header">
          <el-button type="primary" :icon="Plus" @click="openCallbackDialog()">{{ $t('payment.callback.action.mock') }}</el-button>
          <el-button :icon="Refresh" :loading="callbackLoading" @click="loadCallbacks">{{ $t('payment.callback.action.refresh') }}</el-button>
          <el-button :icon="Refresh" :loading="refundLoading" @click="loadRefunds">{{ $t('payment.callback.action.refreshRefunds') }}</el-button>
        </div>

        <h4 class="section-title">{{ $t('payment.callback.title') }}</h4>
        <el-table :data="callbackList" v-loading="callbackLoading" border stripe size="small">
          <el-table-column prop="orderNo" :label="$t('payment.order.field.orderNo')" width="180" />
          <el-table-column prop="channel" :label="$t('payment.field.channel')" width="120" />
          <el-table-column prop="callbackAction" :label="$t('payment.callback.field.callbackAction')" width="130" />
          <el-table-column prop="verifyResult" :label="$t('payment.callback.field.verifyResult')" width="100">
            <template #default="{ row }">
              <el-tag :type="verifyResultType(row.verifyResult)" size="small">{{ row.verifyResult }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="processResult" :label="$t('payment.callback.field.processResult')" width="110">
            <template #default="{ row }">
              <el-tag :type="processResultType(row.processResult)" size="small">{{ row.processResult }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="processMessage" :label="$t('payment.callback.field.processMessage')" min-width="240" show-overflow-tooltip />
          <el-table-column prop="receivedTime" :label="$t('payment.callback.field.receivedTime')" width="170" />
        </el-table>
        <el-pagination
          class="pagination"
          v-model:current-page="callbackQuery.pageNo"
          :page-size="callbackQuery.pageSize"
          :total="callbackTotal"
          layout="total, prev, pager, next"
          @current-change="handleCallbackPageChange"
        />

        <h4 class="section-title">{{ $t('payment.refund.title') }}</h4>
        <el-table :data="refundList" v-loading="refundLoading" border stripe size="small">
          <el-table-column prop="refundNo" :label="$t('payment.refund.field.refundNo')" width="200" />
          <el-table-column prop="originalOrderNo" :label="$t('payment.refund.field.originalOrderNo')" width="200" />
          <el-table-column :label="$t('payment.refund.field.amount')" width="120">
            <template #default="{ row }">{{ formatAmount(row.refundAmount) }}</template>
          </el-table-column>
          <el-table-column prop="reason" :label="$t('payment.refund.field.reason')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="status" :label="$t('payment.field.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="channelRefundNo" :label="$t('payment.refund.field.channelRefundNo')" width="170" show-overflow-tooltip />
          <el-table-column prop="refundedTime" :label="$t('payment.refund.field.refundedTime')" width="170" />
        </el-table>
        <el-pagination
          class="pagination"
          v-model:current-page="refundQuery.pageNo"
          :page-size="refundQuery.pageSize"
          :total="refundTotal"
          layout="total, prev, pager, next"
          @current-change="handleRefundPageChange"
        />

        <!-- 模拟回调对话框 -->
        <el-dialog v-model="callbackDialogVisible" :title="$t('payment.callback.dialog.title')" width="600px">
          <el-form :model="callbackForm" label-width="120px">
            <el-form-item :label="$t('payment.order.field.orderNo')">
              <el-input v-model="callbackForm.orderNo" placeholder="PO20260719000001" />
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.payChannel')">
              <el-select v-model="callbackForm.channel" style="width: 100%">
                <el-option :label="$t('payment.channel.mockAlipay')" value="MOCK_ALIPAY" />
                <el-option :label="$t('payment.channel.mockWechat')" value="MOCK_WECHAT" />
                <el-option :label="$t('payment.channel.mockUnionpay')" value="MOCK_UNIONPAY" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.channelTradeNo')">
              <el-input v-model="callbackForm.channelTradeNo" :placeholder="$t('payment.callback.placeholder.channelTradeNo')" />
            </el-form-item>
            <el-form-item :label="$t('payment.callback.field.callbackAction')">
              <el-select v-model="callbackForm.callbackAction" style="width: 100%">
                <el-option :label="$t('payment.callback.action.paySuccess')" value="PAY_SUCCESS" />
                <el-option :label="$t('payment.callback.action.payFail')" value="PAY_FAIL" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('payment.order.field.amount')">
              <el-input-number v-model="callbackAmountYuan" :min="0.01" :step="1" :precision="2" />
              <span class="form-tip">{{ $t('payment.callback.tip.amountMatch') }}</span>
            </el-form-item>
            <el-form-item :label="$t('payment.callback.field.timestamp')">
              <el-input v-model="callbackForm.callbackTimestamp" :placeholder="$t('payment.callback.placeholder.timestampFormat')" />
            </el-form-item>
            <el-form-item :label="$t('payment.callback.field.signMode')">
              <el-radio-group v-model="callbackSignMode">
                <el-radio value="auto">{{ $t('payment.callback.signMode.auto') }}</el-radio>
                <el-radio value="wrong">{{ $t('payment.callback.signMode.wrong') }}</el-radio>
                <el-radio value="manual">{{ $t('payment.callback.signMode.manual') }}</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="callbackSignMode === 'manual'" :label="$t('payment.callback.field.correctSign')">
              <el-input v-model="callbackForm.signature" :placeholder="$t('payment.callback.placeholder.signature')" />
              <div class="form-tip">
                {{ $t('payment.callback.tip.signManual') }}
              </div>
            </el-form-item>
            <el-form-item v-if="callbackForm.callbackAction === 'PAY_FAIL'" :label="$t('payment.callback.field.failReason')">
              <el-input v-model="callbackForm.failReason" :placeholder="$t('payment.callback.placeholder.failReason')" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="callbackDialogVisible = false">{{ $t('payment.action.cancel') }}</el-button>
            <el-button type="primary" @click="handleSendCallback">{{ $t('payment.callback.action.send') }}</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- ===== Tab 4: 对账导入 ===== -->
      <el-tab-pane :label="$t('payment.tab.reconciliation')" name="reconciliation">
        <div class="tab-header">
          <el-button type="primary" :icon="Upload" @click="openReconDialog">{{ $t('payment.recon.action.import') }}</el-button>
          <el-button :icon="Refresh" :loading="reconLoading" @click="loadReconciliations">{{ $t('payment.action.refresh') }}</el-button>
        </div>
        <el-form :inline="true" :model="reconQuery" class="filter-form">
          <el-form-item :label="$t('payment.field.channel')">
            <el-select v-model="reconQuery.channel" :placeholder="$t('payment.placeholder.all')" clearable style="width: 160px">
              <el-option :label="$t('payment.channel.mockAlipay')" value="MOCK_ALIPAY" />
              <el-option :label="$t('payment.channel.mockWechat')" value="MOCK_WECHAT" />
              <el-option :label="$t('payment.channel.mockUnionpay')" value="MOCK_UNIONPAY" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('payment.field.status')">
            <el-select v-model="reconQuery.status" :placeholder="$t('payment.placeholder.all')" clearable style="width: 140px">
              <el-option :label="$t('payment.recon.status.matched')" value="MATCHED" />
              <el-option :label="$t('payment.recon.status.mismatched')" value="MISMATCHED" />
              <el-option :label="$t('payment.recon.status.imported')" value="IMPORTED" />
              <el-option :label="$t('payment.recon.status.pending')" value="PENDING" />
              <el-option :label="$t('payment.recon.status.failed')" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="reconQuery.pageNo = 1; loadReconciliations()">{{ $t('payment.action.query') }}</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="reconList" v-loading="reconLoading" border stripe>
          <el-table-column prop="reconDate" :label="$t('payment.recon.field.reconDate')" width="120" />
          <el-table-column prop="channel" :label="$t('payment.field.channel')" width="130" />
          <el-table-column prop="fileName" :label="$t('payment.recon.field.fileName')" min-width="180" show-overflow-tooltip />
          <el-table-column :label="$t('payment.recon.field.summary')" width="160">
            <template #default="{ row }">
              {{ $t('payment.recon.summary', { count: row.totalCount, amount: formatAmount(row.totalAmount) }) }}
            </template>
          </el-table-column>
          <el-table-column :label="$t('payment.recon.field.resultDetail')" width="220">
            <template #default="{ row }">
              <el-tag type="success" size="small">{{ $t('payment.recon.status.matched') }} {{ row.matchedCount }}</el-tag>
              <el-tag type="danger" size="small" v-if="row.mismatchedCount > 0">{{ $t('payment.recon.status.mismatched') }} {{ row.mismatchedCount }}</el-tag>
              <el-tag type="warning" size="small" v-if="row.missingCount > 0">{{ $t('payment.recon.status.missing') }} {{ row.missingCount }}</el-tag>
              <el-tag type="info" size="small" v-if="row.extraCount > 0">{{ $t('payment.recon.status.extra') }} {{ row.extraCount }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="$t('payment.field.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="reconStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="processMessage" :label="$t('payment.recon.field.processMessage')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="importedTime" :label="$t('payment.recon.field.importedTime')" width="170" />
        </el-table>
        <el-pagination
          class="pagination"
          v-model:current-page="reconQuery.pageNo"
          :page-size="reconQuery.pageSize"
          :total="reconTotal"
          layout="total, prev, pager, next"
          @current-change="handleReconPageChange"
        />

        <!-- 对账导入对话框 -->
        <el-dialog v-model="reconDialogVisible" :title="$t('payment.recon.dialog.title')" width="700px">
          <el-form :model="reconForm" label-width="120px">
            <el-form-item :label="$t('payment.recon.field.reconDate')">
              <el-input v-model="reconForm.reconDate" placeholder="yyyy-MM-dd" />
            </el-form-item>
            <el-form-item :label="$t('payment.recon.field.channel')">
              <el-select v-model="reconForm.channel" style="width: 100%">
                <el-option :label="$t('payment.channel.mockAlipay')" value="MOCK_ALIPAY" />
                <el-option :label="$t('payment.channel.mockWechat')" value="MOCK_WECHAT" />
                <el-option :label="$t('payment.channel.mockUnionpay')" value="MOCK_UNIONPAY" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('payment.recon.field.fileName')">
              <el-input v-model="reconForm.fileName" placeholder="mock-recon-20260719.csv" />
            </el-form-item>
            <el-form-item :label="$t('payment.recon.field.detail')">
              <el-input
                v-model="reconItemsText"
                type="textarea"
                :rows="8"
                :placeholder="$t('payment.recon.placeholder.detail')"
              />
              <div class="form-tip">
                {{ $t('payment.recon.tip.detail') }}
              </div>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="reconDialogVisible = false">{{ $t('payment.action.cancel') }}</el-button>
            <el-button type="primary" @click="handleImportRecon">{{ $t('payment.recon.action.importBtn') }}</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.payment-ops {
  padding: 16px;
}
.tab-header {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
}
.stats-row {
  margin-bottom: 16px;
}
.stat-card {
  padding: 12px;
  text-align: center;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
.stat-value.small {
  font-size: 13px;
  font-weight: normal;
}
.stat-value.danger {
  color: #f56c6c;
}
.stat-value.warning {
  color: #e6a23c;
}
.stat-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.info-alert {
  margin-top: 16px;
}
.filter-form {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.section-title {
  margin: 16px 0 8px;
  font-size: 15px;
  color: #303133;
  border-left: 4px solid #409eff;
  padding-left: 8px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.send-result {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
}
</style>
