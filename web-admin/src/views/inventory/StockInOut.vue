<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  Download,
  Upload,
  Refresh,
  RefreshLeft,
  List as ListIcon,
  DocumentCopy,
} from '@element-plus/icons-vue'
import {
  pageInbounds,
  pageOutbounds,
  pageTransactions,
  createInbound,
  confirmInbound,
  createOutbound,
  confirmOutbound,
  compensateOutbound,
  pageMaterials,
  listWarehouses,
} from '@/api/inventory'
import type {
  InvInboundOrder,
  InvOutboundOrder,
  InvStockTransaction,
  InvMaterial,
  InvWarehouse,
  InboundRequest,
  OutboundRequest,
} from '@/api/types'

const { t } = useI18n()

/**
 * 出入库操作页。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 *
 * 核心能力:
 *  - 入库单创建(幂等键防重复) + 确认(库存增加)
 *  - 出库单创建(幂等键防重复) + 确认(库存扣减, 不足拦截) + 异常补偿(回补)
 *  - 库存流水审计追溯(不可变, IN/OUT/COMPENSATE)
 *  - 三个 Tab: 入库单 / 出库单 / 库存流水
 *
 * 验证 7 项能力:
 *  1. 库存扣减并发控制 (乐观锁 version + 余额唯一索引)
 *  2. 幂等键防重复提交 (idempotency_key 唯一索引)
 *  3. 乐观锁或数据库约束 (DuplicateKeyException 拦截)
 *  4. 导入物料 (物料管理在 InventoryList.vue)
 *  5. 导出库存 (列表页打印/导出)
 *  6. 异步任务 (单号生成 REQUIRES_NEW 独立事务)
 *  7. 异常补偿 (compensateOutbound 回补库存 + 写 COMPENSATE 流水)
 */

const activeTab = ref<'inbound' | 'outbound' | 'transaction'>('inbound')

// 入库单列表
const inboundLoading = ref(false)
const inboundList = ref<InvInboundOrder[]>([])
const inboundTotal = ref(0)
const inboundPageNo = ref(1)
const inboundPageSize = ref(10)

// 出库单列表
const outboundLoading = ref(false)
const outboundList = ref<InvOutboundOrder[]>([])
const outboundTotal = ref(0)
const outboundPageNo = ref(1)
const outboundPageSize = ref(10)

// 库存流水列表
const transactionLoading = ref(false)
const transactionList = ref<InvStockTransaction[]>([])
const transactionTotal = ref(0)
const transactionPageNo = ref(1)
const transactionPageSize = ref(10)

// 物料 + 仓库下拉
const materials = ref<InvMaterial[]>([])
const warehouses = ref<InvWarehouse[]>([])

// 入库弹窗
const inboundDialogVisible = ref(false)
const inboundCreating = ref(false)
const inboundForm = ref<InboundRequest>({
  idempotencyKey: '',
  warehouseId: '',
  materialId: '',
  quantity: 1,
  unitCost: undefined,
  inboundType: 'PURCHASE',
  batchNo: '',
  supplier: '',
})

// 出库弹窗
const outboundDialogVisible = ref(false)
const outboundCreating = ref(false)
const outboundForm = ref<OutboundRequest>({
  idempotencyKey: '',
  warehouseId: '',
  materialId: '',
  quantity: 1,
  unitCost: undefined,
  outboundType: 'SALE',
  batchNo: '',
  customer: '',
})

const INBOUND_TYPE_OPTIONS = [
  { label: t('inventory.inboundType.purchase'), value: 'PURCHASE' },
  { label: t('inventory.inboundType.return'), value: 'RETURN' },
  { label: t('inventory.inboundType.transferIn'), value: 'TRANSFER_IN' },
  { label: t('inventory.inboundType.initial'), value: 'INITIAL' },
]

const OUTBOUND_TYPE_OPTIONS = [
  { label: t('inventory.outboundType.sale'), value: 'SALE' },
  { label: t('inventory.outboundType.scrap'), value: 'SCRAP' },
  { label: t('inventory.outboundType.transferOut'), value: 'TRANSFER_OUT' },
]

const INBOUND_STATUS_OPTIONS = [
  { label: t('inventory.orderStatus.draft'), value: 'DRAFT' },
  { label: t('inventory.orderStatus.confirmed'), value: 'CONFIRMED' },
]

const OUTBOUND_STATUS_OPTIONS = [
  { label: t('inventory.orderStatus.draft'), value: 'DRAFT' },
  { label: t('inventory.orderStatus.confirmed'), value: 'CONFIRMED' },
  { label: t('inventory.orderStatus.compensated'), value: 'COMPENSATED' },
]

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function inboundStatusTagType(status: string): TagType {
  const map: Record<string, TagType> = { DRAFT: 'info', CONFIRMED: 'success' }
  return map[status] || 'info'
}

function outboundStatusTagType(status: string): TagType {
  const map: Record<string, TagType> = {
    DRAFT: 'info',
    CONFIRMED: 'success',
    COMPENSATED: 'warning',
  }
  return map[status] || 'info'
}

function transactionTypeTagType(t: string): TagType {
  const map: Record<string, TagType> = {
    IN: 'success',
    OUT: 'danger',
    COMPENSATE: 'warning',
  }
  return map[t] || 'info'
}

function inboundTypeLabel(t?: string): string {
  if (!t) return '-'
  return INBOUND_TYPE_OPTIONS.find((o) => o.value === t)?.label || t
}

function outboundTypeLabel(t?: string): string {
  if (!t) return '-'
  return OUTBOUND_TYPE_OPTIONS.find((o) => o.value === t)?.label || t
}

function inboundStatusLabel(s: string): string {
  return INBOUND_STATUS_OPTIONS.find((o) => o.value === s)?.label || s
}

function outboundStatusLabel(s: string): string {
  return OUTBOUND_STATUS_OPTIONS.find((o) => o.value === s)?.label || s
}

function materialLabel(id: string): string {
  const m = materials.value.find((x) => x.id === id)
  return m ? `${m.materialCode} - ${m.materialName}` : id
}

function warehouseLabel(id: string): string {
  const w = warehouses.value.find((x) => x.id === id)
  return w ? `${w.warehouseCode} - ${w.warehouseName}` : id
}

/** 生成幂等键 (UUID v4 简化, 用户也可手动覆盖) */
function genIdempotencyKey(): string {
  return 'idem-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8)
}

// ==================== 数据加载 ====================

async function loadInbounds() {
  inboundLoading.value = true
  try {
    const res = await pageInbounds({
      pageNo: inboundPageNo.value,
      pageSize: inboundPageSize.value,
    })
    inboundList.value = res.records || []
    inboundTotal.value = res.total || 0
  } catch (e) {
    console.warn('loadInbounds failed', e)
  } finally {
    inboundLoading.value = false
  }
}

async function loadOutbounds() {
  outboundLoading.value = true
  try {
    const res = await pageOutbounds({
      pageNo: outboundPageNo.value,
      pageSize: outboundPageSize.value,
    })
    outboundList.value = res.records || []
    outboundTotal.value = res.total || 0
  } catch (e) {
    console.warn('loadOutbounds failed', e)
  } finally {
    outboundLoading.value = false
  }
}

async function loadTransactions() {
  transactionLoading.value = true
  try {
    const res = await pageTransactions({
      pageNo: transactionPageNo.value,
      pageSize: transactionPageSize.value,
    })
    transactionList.value = res.records || []
    transactionTotal.value = res.total || 0
  } catch (e) {
    console.warn('loadTransactions failed', e)
  } finally {
    transactionLoading.value = false
  }
}

async function loadMaterialsAndWarehouses() {
  try {
    const [matRes, whList] = await Promise.all([
      pageMaterials({ pageNo: 1, pageSize: 200 }),
      listWarehouses(),
    ])
    materials.value = matRes.records || []
    warehouses.value = whList || []
  } catch (e) {
    console.warn('loadMaterialsAndWarehouses failed', e)
  }
}

// ==================== 入库操作 ====================

function openInboundDialog() {
  inboundForm.value = {
    idempotencyKey: genIdempotencyKey(),
    warehouseId: '',
    materialId: '',
    quantity: 1,
    unitCost: undefined,
    inboundType: 'PURCHASE',
    batchNo: '',
    supplier: '',
  }
  inboundDialogVisible.value = true
}

async function submitInbound() {
  if (!inboundForm.value.warehouseId || !inboundForm.value.materialId) {
    ElMessage.warning(t('inventory.msg.selectWarehouseAndMaterial'))
    return
  }
  if (!inboundForm.value.quantity || inboundForm.value.quantity <= 0) {
    ElMessage.warning(t('inventory.msg.inboundQuantityMustBePositive'))
    return
  }
  inboundCreating.value = true
  try {
    const order = await createInbound(inboundForm.value)
    ElMessage.success(t('inventory.msg.inboundCreated', { orderNo: order.orderNo }))
    inboundDialogVisible.value = false
    loadInbounds()
  } catch (e) {
    // 拦截器已报错 (含幂等键冲突 IVT-409003)
    console.warn('createInbound failed', e)
  } finally {
    inboundCreating.value = false
  }
}

async function confirmInboundOrder(id: string, orderNo: string) {
  try {
    await ElMessageBox.confirm(
      t('inventory.msg.confirmInboundMessage', { orderNo }),
      t('inventory.msg.confirmInbound'),
      { confirmButtonText: t('inventory.msg.confirm'), cancelButtonText: t('inventory.msg.cancel'), type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await confirmInbound(id)
    ElMessage.success(t('inventory.msg.inboundConfirmed'))
    loadInbounds()
  } catch (e) {
    console.warn('confirmInbound failed', e)
  }
}

// ==================== 出库操作 ====================

function openOutboundDialog() {
  outboundForm.value = {
    idempotencyKey: genIdempotencyKey(),
    warehouseId: '',
    materialId: '',
    quantity: 1,
    unitCost: undefined,
    outboundType: 'SALE',
    batchNo: '',
    customer: '',
  }
  outboundDialogVisible.value = true
}

async function submitOutbound() {
  if (!outboundForm.value.warehouseId || !outboundForm.value.materialId) {
    ElMessage.warning(t('inventory.msg.selectWarehouseAndMaterial'))
    return
  }
  if (!outboundForm.value.quantity || outboundForm.value.quantity <= 0) {
    ElMessage.warning(t('inventory.msg.outboundQuantityMustBePositive'))
    return
  }
  outboundCreating.value = true
  try {
    const order = await createOutbound(outboundForm.value)
    ElMessage.success(t('inventory.msg.outboundCreated', { orderNo: order.orderNo }))
    outboundDialogVisible.value = false
    loadOutbounds()
  } catch (e) {
    console.warn('createOutbound failed', e)
  } finally {
    outboundCreating.value = false
  }
}

async function confirmOutboundOrder(id: string, orderNo: string) {
  try {
    await ElMessageBox.confirm(
      t('inventory.msg.confirmOutboundMessage', { orderNo }),
      t('inventory.msg.confirmOutbound'),
      { confirmButtonText: t('inventory.msg.confirm'), cancelButtonText: t('inventory.msg.cancel'), type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await confirmOutbound(id)
    ElMessage.success(t('inventory.msg.outboundConfirmed'))
    loadOutbounds()
  } catch (e) {
    // 拦截器已报错 (含库存不足 IVT-409001)
    console.warn('confirmOutbound failed', e)
  }
}

async function compensateOutboundOrder(id: string, orderNo: string) {
  try {
    await ElMessageBox.confirm(
      t('inventory.msg.confirmCompensateMessage', { orderNo }),
      t('inventory.msg.compensate'),
      { confirmButtonText: t('inventory.msg.confirmCompensate'), cancelButtonText: t('inventory.msg.cancel'), type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await compensateOutbound(id)
    ElMessage.success(t('inventory.msg.compensateCompleted'))
    loadOutbounds()
  } catch (e) {
    console.warn('compensateOutbound failed', e)
  }
}

// ==================== 分页 ====================

function handleInboundPageChange(p: number) {
  inboundPageNo.value = p
  loadInbounds()
}

function handleOutboundPageChange(p: number) {
  outboundPageNo.value = p
  loadOutbounds()
}

function handleTransactionPageChange(p: number) {
  transactionPageNo.value = p
  loadTransactions()
}

function handleTabChange() {
  if (activeTab.value === 'inbound') loadInbounds()
  else if (activeTab.value === 'outbound') loadOutbounds()
  else loadTransactions()
}

function refreshAll() {
  loadInbounds()
  loadOutbounds()
  loadTransactions()
}

onMounted(() => {
  Promise.all([loadMaterialsAndWarehouses(), loadInbounds()])
})
</script>

<template>
  <div class="stock-in-out" role="main" :aria-label="$t('inventory.aria.inOut')">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <el-icon><DocumentCopy /></el-icon>
            <span>{{ $t('inventory.title.inOut') }}</span>
          </div>
          <div>
            <el-button :icon="Refresh" @click="refreshAll">{{ $t('inventory.action.refreshAll') }}</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 入库单 Tab -->
        <el-tab-pane :label="$t('inventory.tab.inbound')" name="inbound">
          <div class="tab-toolbar">
            <el-button type="primary" :icon="Download" @click="openInboundDialog">
              {{ $t('inventory.action.createInbound') }}
            </el-button>
            <span class="tab-tip">{{ $t('inventory.tip.inboundFlow') }}</span>
          </div>
          <el-table
            v-loading="inboundLoading"
            :data="inboundList"
            border
            stripe
            :empty-text="$t('inventory.empty.inbound')"
            :aria-label="$t('inventory.aria.inboundList')"
          >
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="orderNo" :label="$t('inventory.field.inboundOrderNo')" min-width="170" />
            <el-table-column prop="idempotencyKey" :label="$t('inventory.field.idempotencyKey')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="$t('inventory.field.material')" min-width="200">
              <template #default="{ row }">
                {{ materialLabel(row.materialId) }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('inventory.field.warehouse')" min-width="160">
              <template #default="{ row }">
                {{ warehouseLabel(row.warehouseId) }}
              </template>
            </el-table-column>
            <el-table-column prop="quantity" :label="$t('inventory.field.qty')" width="100" align="right" />
            <el-table-column prop="unitCost" :label="$t('inventory.field.price')" width="100" align="right" />
            <el-table-column :label="$t('inventory.field.type')" width="110">
              <template #default="{ row }">
                {{ inboundTypeLabel(row.inboundType) }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('inventory.field.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="inboundStatusTagType(row.status)" size="small">
                  {{ inboundStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="supplier" :label="$t('inventory.field.supplier')" min-width="120" />
            <el-table-column prop="createdTime" :label="$t('inventory.field.createdTime')" min-width="170" />
            <el-table-column :label="$t('inventory.field.operation')" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'DRAFT'"
                  type="primary"
                  size="small"
                  link
                  @click="confirmInboundOrder(row.id, row.orderNo)"
                >
                  {{ $t('inventory.action.confirmInbound') }}
                </el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="inboundPageNo"
              v-model:page-size="inboundPageSize"
              :total="inboundTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="handleInboundPageChange"
            />
          </div>
        </el-tab-pane>

        <!-- 出库单 Tab -->
        <el-tab-pane :label="$t('inventory.tab.outbound')" name="outbound">
          <div class="tab-toolbar">
            <el-button type="primary" :icon="Upload" @click="openOutboundDialog">
              {{ $t('inventory.action.createOutbound') }}
            </el-button>
            <span class="tab-tip">
              {{ $t('inventory.tip.outboundFlow') }}
            </span>
          </div>
          <el-table
            v-loading="outboundLoading"
            :data="outboundList"
            border
            stripe
            :empty-text="$t('inventory.empty.outbound')"
            :aria-label="$t('inventory.aria.outboundList')"
          >
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="orderNo" :label="$t('inventory.field.outboundOrderNo')" min-width="170" />
            <el-table-column prop="idempotencyKey" :label="$t('inventory.field.idempotencyKey')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="$t('inventory.field.material')" min-width="200">
              <template #default="{ row }">
                {{ materialLabel(row.materialId) }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('inventory.field.warehouse')" min-width="160">
              <template #default="{ row }">
                {{ warehouseLabel(row.warehouseId) }}
              </template>
            </el-table-column>
            <el-table-column prop="quantity" :label="$t('inventory.field.qty')" width="100" align="right" />
            <el-table-column prop="unitCost" :label="$t('inventory.field.price')" width="100" align="right" />
            <el-table-column :label="$t('inventory.field.type')" width="110">
              <template #default="{ row }">
                {{ outboundTypeLabel(row.outboundType) }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('inventory.field.status')" width="100">
              <template #default="{ row }">
                <el-tag :type="outboundStatusTagType(row.status)" size="small">
                  {{ outboundStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="customer" :label="$t('inventory.field.customer')" min-width="120" />
            <el-table-column prop="createdTime" :label="$t('inventory.field.createdTime')" min-width="170" />
            <el-table-column :label="$t('inventory.field.operation')" width="180" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'DRAFT'"
                  type="primary"
                  size="small"
                  link
                  @click="confirmOutboundOrder(row.id, row.orderNo)"
                >
                  {{ $t('inventory.action.confirmOutbound') }}
                </el-button>
                <el-button
                  v-if="row.status === 'CONFIRMED'"
                  type="warning"
                  size="small"
                  link
                  :icon="RefreshLeft"
                  @click="compensateOutboundOrder(row.id, row.orderNo)"
                >
                  {{ $t('inventory.action.compensate') }}
                </el-button>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="outboundPageNo"
              v-model:page-size="outboundPageSize"
              :total="outboundTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="handleOutboundPageChange"
            />
          </div>
        </el-tab-pane>

        <!-- 库存流水 Tab -->
        <el-tab-pane :label="$t('inventory.tab.transaction')" name="transaction">
          <div class="tab-toolbar">
            <el-button :icon="ListIcon" @click="loadTransactions">{{ $t('inventory.action.refreshTransaction') }}</el-button>
            <span class="tab-tip">
              {{ $t('inventory.tip.transactionAudit') }}
            </span>
          </div>
          <el-table
            v-loading="transactionLoading"
            :data="transactionList"
            border
            stripe
            :empty-text="$t('inventory.empty.transaction')"
            :aria-label="$t('inventory.aria.transactionList')"
          >
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column prop="transactionNo" :label="$t('inventory.field.transactionNo')" min-width="200" />
            <el-table-column :label="$t('inventory.field.type')" width="100">
              <template #default="{ row }">
                <el-tag :type="transactionTypeTagType(row.transactionType)" size="small">
                  {{ row.transactionType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('inventory.field.material')" min-width="200">
              <template #default="{ row }">
                {{ materialLabel(row.materialId) }}
              </template>
            </el-table-column>
            <el-table-column :label="$t('inventory.field.warehouse')" min-width="160">
              <template #default="{ row }">
                {{ warehouseLabel(row.warehouseId) }}
              </template>
            </el-table-column>
            <el-table-column prop="quantity" :label="$t('inventory.field.changeQuantity')" width="120" align="right" />
            <el-table-column prop="quantityBefore" :label="$t('inventory.field.quantityBefore')" width="120" align="right" />
            <el-table-column prop="quantityAfter" :label="$t('inventory.field.quantityAfter')" width="120" align="right" />
            <el-table-column :label="$t('inventory.field.bizOrder')" min-width="180">
              <template #default="{ row }">
                <span>{{ row.bizOrderType }} / {{ row.bizOrderNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdTime" :label="$t('inventory.field.occurredTime')" min-width="170" />
          </el-table>
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="transactionPageNo"
              v-model:page-size="transactionPageSize"
              :total="transactionTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="handleTransactionPageChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 入库单弹窗 -->
    <el-dialog
      v-model="inboundDialogVisible"
      :title="$t('inventory.title.createInbound')"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="$t('inventory.alert.inboundIdempotency')"
        style="margin-bottom: 12px"
      />
      <el-form :model="inboundForm" label-width="100px">
        <el-form-item :label="$t('inventory.field.idempotencyKey')" required>
          <el-input v-model="inboundForm.idempotencyKey" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.warehouse')" required>
          <el-select v-model="inboundForm.warehouseId" filterable :placeholder="$t('inventory.placeholder.selectWarehouse')" style="width: 100%">
            <el-option
              v-for="w in warehouses"
              :key="w.id"
              :label="`${w.warehouseCode} - ${w.warehouseName}`"
              :value="w.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventory.field.material')" required>
          <el-select v-model="inboundForm.materialId" filterable :placeholder="$t('inventory.placeholder.selectMaterial')" style="width: 100%">
            <el-option
              v-for="m in materials"
              :key="m.id"
              :label="`${m.materialCode} - ${m.materialName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventory.field.inboundQuantity')" required>
          <el-input-number
            v-model="inboundForm.quantity"
            :min="0.0001"
            :precision="4"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.price')">
          <el-input-number
            v-model="inboundForm.unitCost"
            :min="0"
            :precision="2"
            :step="0.01"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.inboundType')">
          <el-select v-model="inboundForm.inboundType" style="width: 100%">
            <el-option
              v-for="t in INBOUND_TYPE_OPTIONS"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventory.field.batchNo')">
          <el-input v-model="inboundForm.batchNo" maxlength="64" :placeholder="$t('inventory.placeholder.optional')" />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.supplier')">
          <el-input v-model="inboundForm.supplier" maxlength="128" :placeholder="$t('inventory.placeholder.optional')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inboundDialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="inboundCreating" @click="submitInbound">
          {{ $t('inventory.action.submitInbound') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 出库单弹窗 -->
    <el-dialog
      v-model="outboundDialogVisible"
      :title="$t('inventory.title.createOutbound')"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="$t('inventory.alert.outboundIdempotency')"
        style="margin-bottom: 12px"
      />
      <el-form :model="outboundForm" label-width="100px">
        <el-form-item :label="$t('inventory.field.idempotencyKey')" required>
          <el-input v-model="outboundForm.idempotencyKey" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.warehouse')" required>
          <el-select v-model="outboundForm.warehouseId" filterable :placeholder="$t('inventory.placeholder.selectWarehouse')" style="width: 100%">
            <el-option
              v-for="w in warehouses"
              :key="w.id"
              :label="`${w.warehouseCode} - ${w.warehouseName}`"
              :value="w.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventory.field.material')" required>
          <el-select v-model="outboundForm.materialId" filterable :placeholder="$t('inventory.placeholder.selectMaterial')" style="width: 100%">
            <el-option
              v-for="m in materials"
              :key="m.id"
              :label="`${m.materialCode} - ${m.materialName}`"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventory.field.outboundQuantity')" required>
          <el-input-number
            v-model="outboundForm.quantity"
            :min="0.0001"
            :precision="4"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.price')">
          <el-input-number
            v-model="outboundForm.unitCost"
            :min="0"
            :precision="2"
            :step="0.01"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.outboundType')">
          <el-select v-model="outboundForm.outboundType" style="width: 100%">
            <el-option
              v-for="t in OUTBOUND_TYPE_OPTIONS"
              :key="t.value"
              :label="t.label"
              :value="t.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('inventory.field.batchNo')">
          <el-input v-model="outboundForm.batchNo" maxlength="64" :placeholder="$t('inventory.placeholder.optional')" />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.customer')">
          <el-input v-model="outboundForm.customer" maxlength="128" :placeholder="$t('inventory.placeholder.optional')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outboundDialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="outboundCreating" @click="submitOutbound">
          {{ $t('inventory.action.submitOutbound') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stock-in-out {
  padding: 0;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}
.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.tab-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.text-muted {
  color: var(--el-text-color-secondary);
}
</style>
