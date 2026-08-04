<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Box, Plus, Search, Refresh } from '@element-plus/icons-vue'
import { pageBalances, pageMaterials, createMaterial, listWarehouses } from '@/api/inventory'
import type { StockBalanceVO, InvMaterial, InvWarehouse, SaveMaterialRequest } from '@/api/types'

/**
 * 库存余额列表页。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 *
 * 核心能力:
 *  - 库存余额分页查询（含物料/仓库冗余字段）
 *  - 物料/仓库/物料编码/物料名称筛选
 *  - 可用数量 = quantity - lockedQuantity（后端计算后返回）
 *  - 物料管理弹窗（新建物料, 物料编码租户内唯一）
 *
 * 验证能力: 导入物料、库存余额唯一约束（物料+仓库维度）、乐观锁 version 字段。
 */

const { t } = useI18n()
// 库存余额列表
const loading = ref(false)
const balanceList = ref<StockBalanceVO[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(10)

// 筛选条件
const filterMaterialCode = ref('')
const filterMaterialName = ref('')
const filterWarehouseId = ref('')

// 仓库下拉
const warehouses = ref<InvWarehouse[]>([])

// 物料管理弹窗
const materialDialogVisible = ref(false)
const materialForm = ref<SaveMaterialRequest>({
  materialCode: '',
  materialName: '',
  materialType: 'GENERAL',
  spec: '',
  unit: 'PCS',
  category: '',
  barcode: '',
  referencePrice: undefined,
})
const materialCreating = ref(false)

// 物料列表（弹窗中显示已有物料）
const materialList = ref<InvMaterial[]>([])
const materialTotal = ref(0)

const MATERIAL_TYPE_OPTIONS = [
  { label: t('inventory.materialType.electronic'), value: 'ELECTRONIC' },
  { label: t('inventory.materialType.mechanical'), value: 'MECHANICAL' },
  { label: t('inventory.materialType.accessory'), value: 'ACCESSORY' },
  { label: t('inventory.materialType.general'), value: 'GENERAL' },
]

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function availabilityTagType(available: number): TagType {
  if (available <= 0) return 'danger'
  if (available < 10) return 'warning'
  return 'success'
}

function materialTypeLabel(type?: string): string {
  if (!type) return '-'
  return MATERIAL_TYPE_OPTIONS.find((t) => t.value === type)?.label || type
}

async function loadBalances() {
  loading.value = true
  try {
    const res = await pageBalances({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      materialCode: filterMaterialCode.value || undefined,
      materialName: filterMaterialName.value || undefined,
      warehouseId: filterWarehouseId.value || undefined,
    })
    balanceList.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    // 拦截器已报错
    console.warn('loadBalances failed', e)
  } finally {
    loading.value = false
  }
}

async function loadWarehouses() {
  try {
    warehouses.value = await listWarehouses()
  } catch (e) {
    console.warn('loadWarehouses failed', e)
  }
}

async function loadMaterials() {
  try {
    const res = await pageMaterials({ pageNo: 1, pageSize: 50 })
    materialList.value = res.records || []
    materialTotal.value = res.total || 0
  } catch (e) {
    console.warn('loadMaterials failed', e)
  }
}

function handleSearch() {
  pageNo.value = 1
  loadBalances()
}

function handleReset() {
  filterMaterialCode.value = ''
  filterMaterialName.value = ''
  filterWarehouseId.value = ''
  pageNo.value = 1
  loadBalances()
}

function handlePageChange(p: number) {
  pageNo.value = p
  loadBalances()
}

function handleSizeChange(s: number) {
  pageSize.value = s
  pageNo.value = 1
  loadBalances()
}

function openMaterialDialog() {
  materialForm.value = {
    materialCode: '',
    materialName: '',
    materialType: 'GENERAL',
    spec: '',
    unit: 'PCS',
    category: '',
    barcode: '',
    referencePrice: undefined,
  }
  materialDialogVisible.value = true
  loadMaterials()
}

async function submitMaterial() {
  if (!materialForm.value.materialCode || !materialForm.value.materialName) {
    ElMessage.warning(t('inventory.msg.materialRequired'))
    return
  }
  materialCreating.value = true
  try {
    await createMaterial(materialForm.value)
    ElMessage.success(t('inventory.msg.materialCreated'))
    materialDialogVisible.value = false
    loadBalances()
  } catch (e) {
    // 拦截器已报错（如物料编码冲突）
    console.warn('createMaterial failed', e)
  } finally {
    materialCreating.value = false
  }
}

async function refreshData() {
  await Promise.all([loadWarehouses(), loadBalances()])
}

onMounted(() => {
  refreshData()
})
</script>

<template>
  <div class="inventory-list" role="main" :aria-label="$t('inventory.aria.balanceManage')">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-title">
            <el-icon><Box /></el-icon>
            <span>{{ $t('inventory.title.balance') }}</span>
          </div>
          <el-button type="primary" :icon="Plus" @click="openMaterialDialog">{{ $t('inventory.action.createMaterial') }}</el-button>
        </div>
      </template>

      <!-- 筛选区 -->
      <el-form :inline="true" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item :label="$t('inventory.field.materialCode')">
          <el-input
            v-model="filterMaterialCode"
            :placeholder="$t('inventory.placeholder.fuzzyMatch')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.materialName')">
          <el-input
            v-model="filterMaterialName"
            :placeholder="$t('inventory.placeholder.fuzzyMatch')"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('inventory.field.warehouse')">
          <el-select
            v-model="filterWarehouseId"
            :placeholder="$t('inventory.placeholder.allWarehouses')"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="w in warehouses"
              :key="w.id"
              :label="`${w.warehouseCode} - ${w.warehouseName}`"
              :value="w.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('inventory.action.query') }}</el-button>
          <el-button :icon="Refresh" @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <!-- 库存余额表格 -->
      <el-table
        v-loading="loading"
        :data="balanceList"
        border
        stripe
        :empty-text="$t('inventory.empty.balance')"
        :aria-label="$t('inventory.aria.balanceList')"
      >
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column prop="materialCode" :label="$t('inventory.field.materialCode')" min-width="140" />
        <el-table-column prop="materialName" :label="$t('inventory.field.materialName')" min-width="180" />
        <el-table-column prop="warehouseCode" :label="$t('inventory.field.warehouseCode')" width="120" />
        <el-table-column prop="warehouseName" :label="$t('inventory.field.warehouseName')" min-width="140" />
        <el-table-column prop="quantity" :label="$t('inventory.field.quantity')" width="110" align="right" />
        <el-table-column prop="lockedQuantity" :label="$t('inventory.field.lockedQuantity')" width="110" align="right" />
        <el-table-column :label="$t('inventory.field.availableQuantity')" width="120" align="right">
          <template #default="{ row }">
            <el-tag :type="availabilityTagType(row.availableQuantity)" effect="plain">
              {{ row.availableQuantity ?? 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastInTime" :label="$t('inventory.field.lastInTime')" min-width="170">
          <template #default="{ row }">
            <span v-if="row.lastInTime">{{ row.lastInTime }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastOutTime" :label="$t('inventory.field.lastOutTime')" min-width="170">
          <template #default="{ row }">
            <span v-if="row.lastOutTime">{{ row.lastOutTime }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 物料管理弹窗 -->
    <el-dialog
      v-model="materialDialogVisible"
      :title="$t('inventory.title.materialManage')"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="$t('inventory.alert.materialUnique')"
        style="margin-bottom: 12px"
      />
      <el-form :model="materialForm" label-width="100px" label-position="right">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('inventory.field.materialCode')" required>
              <el-input v-model="materialForm.materialCode" :placeholder="$t('inventory.placeholder.materialCodeExample')" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('inventory.field.materialName')" required>
              <el-input v-model="materialForm.materialName" :placeholder="$t('inventory.placeholder.materialNameExample')" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('inventory.field.materialType')">
              <el-select v-model="materialForm.materialType" style="width: 100%">
                <el-option
                  v-for="t in MATERIAL_TYPE_OPTIONS"
                  :key="t.value"
                  :label="t.label"
                  :value="t.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('inventory.field.unit')">
              <el-input v-model="materialForm.unit" placeholder="PCS / BOX / KG / M" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('inventory.field.spec')">
              <el-input v-model="materialForm.spec" :placeholder="$t('inventory.placeholder.specExample')" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('inventory.field.category')">
              <el-input v-model="materialForm.category" :placeholder="$t('inventory.placeholder.categoryExample')" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('inventory.field.barcode')">
              <el-input v-model="materialForm.barcode" :placeholder="$t('inventory.placeholder.optional')" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('inventory.field.referencePrice')">
              <el-input-number
                v-model="materialForm.referencePrice"
                :min="0"
                :precision="2"
                :step="0.01"
                style="width: 100%"
                placeholder="0.00"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 已有物料列表 -->
      <el-divider content-position="left">{{ $t('inventory.material.existing', { total: materialTotal }) }}</el-divider>
      <el-table :data="materialList" max-height="240" border size="small">
        <el-table-column prop="materialCode" :label="$t('inventory.field.code')" min-width="120" />
        <el-table-column prop="materialName" :label="$t('inventory.field.name')" min-width="160" />
        <el-table-column :label="$t('inventory.field.type')" width="100">
          <template #default="{ row }">
            {{ materialTypeLabel(row.materialType) }}
          </template>
        </el-table-column>
        <el-table-column prop="unit" :label="$t('inventory.field.unit')" width="80" />
        <el-table-column prop="category" :label="$t('inventory.field.category')" min-width="100" />
        <el-table-column prop="referencePrice" :label="$t('inventory.field.referencePrice')" width="110" align="right" />
        <el-table-column :label="$t('inventory.field.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? $t('inventory.status.enabled') : $t('inventory.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="materialDialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="materialCreating" @click="submitMaterial">
          {{ $t('inventory.action.submitMaterial') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.inventory-list {
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
.filter-form {
  margin-bottom: 12px;
}
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
