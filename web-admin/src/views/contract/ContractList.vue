<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { DocumentChecked } from '@element-plus/icons-vue'
import { pageContracts, searchContracts, createContract } from '@/api/contract'
import type { Contract, SaveContractRequest, PageResult } from '@/api/types'

const { t } = useI18n()

/**
 * 合同列表页。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 *
 * 核心能力:
 *  - 分页查询 + 状态/类型/合同号/标题/乙方筛选
 *  - PG 全文检索（query 参数走 /contracts/search 端点）
 *  - 新建合同草稿（自动生成合同号 CTyyyyMMddNNNN）
 *  - 行点击进入详情
 */
const router = useRouter()
const loading = ref(false)
const tableData = ref<Contract[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选条件
const filterContractNo = ref('')
const filterTitle = ref('')
const filterStatus = ref('')
const filterContractType = ref('')
const filterPartyB = ref('')

// 全文检索（独立于筛选）
const searchQuery = ref('')
const searchMode = ref(false)
const searchResults = ref<Contract[]>([])

// 新建合同弹窗
const createDialogVisible = ref(false)
const createForm = ref<SaveContractRequest>({
  title: '',
  contractType: 'GENERAL',
  partyA: '',
  partyB: '',
  amount: undefined,
  currency: 'CNY',
  contentSummary: '',
})

const STATUS_OPTIONS = [
  { label: t('contract.status.draft'), value: 'DRAFT' },
  { label: t('contract.status.submitted'), value: 'SUBMITTED' },
  { label: t('contract.status.approved'), value: 'APPROVED' },
  { label: t('contract.status.rejected'), value: 'REJECTED' },
  { label: t('contract.status.signed'), value: 'SIGNED' },
  { label: t('contract.status.archived'), value: 'ARCHIVED' },
  { label: t('contract.status.cancelled'), value: 'CANCELLED' },
]

const TYPE_OPTIONS = [
  { label: t('contract.type.general'), value: 'GENERAL' },
  { label: t('contract.type.service'), value: 'SERVICE' },
  { label: t('contract.type.purchase'), value: 'PURCHASE' },
  { label: t('contract.type.sale'), value: 'SALE' },
  { label: t('contract.type.lease'), value: 'LEASE' },
]

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
  return STATUS_OPTIONS.find((s) => s.value === status)?.label || status
}

function typeLabel(type?: string): string {
  if (!type) return '-'
  return TYPE_OPTIONS.find((t) => t.value === type)?.label || type
}

function formatAmount(amount?: number | null, currency?: string): string {
  if (amount === null || amount === undefined) return '***'
  const symbol = currency === 'CNY' ? '¥' : currency ? `${currency} ` : ''
  return `${symbol}${Number(amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

async function loadData() {
  loading.value = true
  try {
    if (searchMode.value && searchQuery.value.trim()) {
      // 全文检索模式
      searchResults.value = await searchContracts(searchQuery.value.trim(), 50)
      tableData.value = searchResults.value
      total.value = searchResults.value.length
    } else {
      // 分页查询模式
      const res: PageResult<Contract> = await pageContracts({
        page: currentPage.value,
        size: pageSize.value,
        contractNo: filterContractNo.value || undefined,
        title: filterTitle.value || undefined,
        status: filterStatus.value || undefined,
        contractType: filterContractType.value || undefined,
        partyB: filterPartyB.value || undefined,
      })
      tableData.value = res.records || []
      total.value = res.total || 0
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  searchMode.value = false
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filterContractNo.value = ''
  filterTitle.value = ''
  filterStatus.value = ''
  filterContractType.value = ''
  filterPartyB.value = ''
  searchQuery.value = ''
  searchMode.value = false
  currentPage.value = 1
  loadData()
}

function handleFullTextSearch() {
  if (!searchQuery.value.trim()) {
    ElMessage.warning(t('contract.msg.searchKeywordRequired'))
    return
  }
  searchMode.value = true
  loadData()
}

function clearFullTextSearch() {
  searchQuery.value = ''
  searchMode.value = false
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function goDetail(row: Contract) {
  router.push(`/contracts/${row.id}`)
}

function openCreateDialog() {
  createForm.value = {
    title: '',
    contractType: 'GENERAL',
    partyA: '',
    partyB: '',
    amount: undefined,
    currency: 'CNY',
    contentSummary: '',
  }
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.title.trim()) {
    ElMessage.warning(t('contract.msg.titleRequired'))
    return
  }
  if (!createForm.value.partyA?.trim()) {
    ElMessage.warning(t('contract.msg.partyARequired'))
    return
  }
  if (!createForm.value.partyB?.trim()) {
    ElMessage.warning(t('contract.msg.partyBRequired'))
    return
  }
  const created = await createContract(createForm.value)
  ElMessage.success(t('contract.msg.createSuccess', { contractNo: created.contractNo }))
  createDialogVisible.value = false
  // 跳转到详情页
  router.push(`/contracts/${created.id}`)
}

onMounted(loadData)
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('contract.list.pageTitle') }}</h2>

    <!-- 全文检索区（独立卡片，凸显 PG 全文检索能力） -->
    <el-card class="filter-card search-card" shadow="never">
      <div class="search-bar">
        <el-icon class="search-icon"><DocumentChecked /></el-icon>
        <el-input
          v-model="searchQuery"
          :placeholder="$t('contract.list.placeholder.search')"
          clearable
          style="flex: 1"
          @keyup.enter="handleFullTextSearch"
        />
        <el-button type="primary" @click="handleFullTextSearch">{{ $t('contract.list.action.search') }}</el-button>
        <el-button v-if="searchMode" @click="clearFullTextSearch">{{ $t('contract.list.action.backToList') }}</el-button>
      </div>
      <div v-if="searchMode" class="search-hint">
        <el-tag type="success" size="small">{{ $t('contract.list.tag.searchMode') }}</el-tag>
        <span>{{ $t('contract.list.searchHint', { total: total, query: searchQuery }) }}</span>
      </div>
    </el-card>

    <!-- 筛选区 -->
    <el-card v-if="!searchMode" class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('contract.field.contractNo')">
          <el-input v-model="filterContractNo" :placeholder="$t('contract.field.contractNo')" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item :label="$t('contract.field.title')">
          <el-input v-model="filterTitle" :placeholder="$t('contract.field.title')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('contract.field.status')">
          <el-select v-model="filterStatus" :placeholder="$t('contract.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('contract.field.type')">
          <el-select v-model="filterContractType" :placeholder="$t('contract.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('contract.field.partyB')">
          <el-input v-model="filterPartyB" :placeholder="$t('contract.field.partyB')" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('contract.action.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('contract.action.reset') }}</el-button>
          <el-button type="success" @click="openCreateDialog">{{ $t('contract.list.action.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 合同列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe @row-click="goDetail" style="cursor: pointer">
      <el-table-column prop="contractNo" :label="$t('contract.field.contractNo')" width="160" />
      <el-table-column prop="title" :label="$t('contract.field.title')" min-width="220" show-overflow-tooltip />
      <el-table-column prop="contractType" :label="$t('contract.field.type')" width="100">
        <template #default="{ row }">{{ typeLabel(row.contractType) }}</template>
      </el-table-column>
      <el-table-column prop="partyA" :label="$t('contract.field.partyA')" width="180" show-overflow-tooltip />
      <el-table-column prop="partyB" :label="$t('contract.field.partyB')" width="180" show-overflow-tooltip />
      <el-table-column prop="amount" :label="$t('contract.field.amount')" width="140" align="right">
        <template #default="{ row }">
          <span :class="{ 'amount-masked': row.amount === null }">
            {{ formatAmount(row.amount, row.currency) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('contract.field.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status as string)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentVersionNo" :label="$t('contract.field.version')" width="70" align="center" />
      <el-table-column prop="createdTime" :label="$t('contract.field.createdTime')" width="170" />
    </el-table>

    <el-pagination
      v-if="!searchMode"
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />

    <!-- 新建合同弹窗 -->
    <el-dialog v-model="createDialogVisible" :title="$t('contract.list.dialog.createTitle')" width="600px">
      <el-form label-width="90px">
        <el-form-item :label="$t('contract.field.title')" required>
          <el-input v-model="createForm.title" :placeholder="$t('contract.list.placeholder.titleInput')" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('contract.list.field.contractType')">
          <el-select v-model="createForm.contractType" style="width: 100%">
            <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('contract.field.partyA')" required>
          <el-input v-model="createForm.partyA" :placeholder="$t('contract.list.placeholder.partyAName')" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('contract.field.partyB')" required>
          <el-input v-model="createForm.partyB" :placeholder="$t('contract.list.placeholder.partyBName')" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('contract.list.field.signedDate')">
          <el-date-picker v-model="createForm.signedDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('contract.list.placeholder.signedDate')" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('contract.list.field.effectiveDate')">
          <el-date-picker v-model="createForm.effectiveDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('contract.list.placeholder.effectiveDate')" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('contract.list.field.expireDate')">
          <el-date-picker v-model="createForm.expireDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('contract.list.placeholder.expireDate')" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('contract.field.amount')">
          <el-input-number v-model="createForm.amount" :min="0" :precision="2" :placeholder="$t('contract.list.placeholder.amount')" style="width: 200px" />
          <el-select v-model="createForm.currency" style="width: 100px; margin-left: 8px">
            <el-option label="CNY" value="CNY" />
            <el-option label="USD" value="USD" />
            <el-option label="EUR" value="EUR" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('contract.list.field.contentSummary')">
          <el-input v-model="createForm.contentSummary" type="textarea" :rows="4" :placeholder="$t('contract.list.placeholder.contentSummary')" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ $t('contract.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ $t('contract.list.action.confirm') }}</el-button>
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
.search-card {
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border: 1px solid #bae6fd;
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.search-icon {
  font-size: 22px;
  color: var(--el-color-primary);
}
.search-hint {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 8px;
}
.amount-masked {
  color: var(--el-text-color-placeholder);
  font-style: italic;
}
</style>
