<script setup lang="ts">
/**
 * 申请单列表页 (GA2-55 重构)
 * 设计来源: 66-前端组件API与状态管理详设 line 11-21 + 137 (验收标准 1)
 *
 * 重构要点:
 *   - 使用 BaseTable 通用组件替代 el-table + el-pagination 直接组合
 *   - 使用 SearchForm 通用组件声明查询字段
 *   - 使用 StatusBadge 替代 el-tag 展示状态
 *   - 保留既有权限码控制 (canCreate/handleDetail) 和埋点逻辑
 */
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getRequests } from '@/api/biz-request'
import type { BizRequest, PageResult } from '@/api/types'
import { track } from '@/utils/tracker'
import { hasAnyPermission } from '@/utils/permission'
import BaseTable from '@/components/BaseTable.vue'
import type { Column } from '@/components/BaseTable.vue'
import SearchForm from '@/components/SearchForm.vue'
import type { SearchField } from '@/components/SearchForm.vue'
import StatusBadge from '@/components/StatusBadge.vue'

const router = useRouter()
const { t } = useI18n()
const loading = ref(false)
const tableData = ref<BizRequest[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 查询模型 (SearchForm v-model 绑定的对象)
const searchModel = reactive({
  requestNo: '',
  title: '',
  status: '',
})

const statusOptions = [
  { label: t('biz.request.status.all'), value: '' },
  { label: t('biz.request.status.draft'), value: 'DRAFT' },
  { label: t('biz.request.status.submitted'), value: 'SUBMITTED' },
  { label: t('biz.request.status.approved'), value: 'APPROVED' },
  { label: t('biz.request.status.rejected'), value: 'REJECTED' },
  { label: t('biz.request.status.archived'), value: 'ARCHIVED' },
]

/** StatusBadge type 映射 (66 号文档 + base.css yt-status-badge--{type}) */
const statusBadgeType: Record<
  string,
  'success' | 'warning' | 'danger' | 'info' | 'primary'
> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  ARCHIVED: 'primary',
}

const statusLabel: Record<string, string> = {
  DRAFT: t('biz.request.status.draft'),
  SUBMITTED: t('biz.request.status.submitted'),
  APPROVED: t('biz.request.status.approved'),
  REJECTED: t('biz.request.status.rejected'),
  ARCHIVED: t('biz.request.status.archived'),
}

/** 查询表单 schema (SearchForm 声明式字段定义) */
const searchSchema: SearchField[] = [
  { key: 'requestNo', label: t('biz.request.field.requestNo'), type: 'input', placeholder: t('biz.request.list.placeholder.requestNo') },
  { key: 'title', label: t('biz.request.field.title'), type: 'input', placeholder: t('biz.request.list.placeholder.titleKeyword') },
  {
    key: 'status',
    label: t('common.field.status'),
    type: 'select',
    placeholder: t('biz.request.list.placeholder.allStatus'),
    options: statusOptions.filter((o) => o.value !== ''),
  },
]

/** 表格列定义 (66 号文档 line 23-35 Column 结构) */
const columns: Column[] = [
  { key: 'requestNo', title: t('biz.request.field.requestNo'), width: 180 },
  { key: 'title', title: t('biz.request.field.title'), width: 220, ellipsis: true },
  { key: 'customerNameSnapshot', title: t('biz.request.field.customerId'), width: 150, ellipsis: true },
  { key: 'requestStatus', title: t('common.field.status'), width: 100, align: 'center' },
  {
    key: 'totalAmount',
    title: t('biz.request.field.amount'),
    width: 120,
    align: 'right',
    formatter: (_row, _col, value) =>
      value != null ? '¥' + Number(value).toFixed(2) : '-',
  },
  { key: 'createdTime', title: t('common.field.createdTime'), width: 180 },
  { key: '__actions', title: t('biz.request.field.operation'), width: 100, fixed: 'right', align: 'center' },
]

/** 分页对象 (PageResult 结构, 传给 BaseTable) */
const pagination = computed<PageResult<BizRequest>>(() => ({
  records: tableData.value,
  total: total.value,
  page: currentPage.value,
  size: pageSize.value,
}))

// GA2-24: 新建按钮可见性按权限码 biz:request:add 控制 (96-端侧权限可见性矩阵详设)
const canCreate = () => hasAnyPermission(['biz:request:add'])

async function loadData() {
  loading.value = true
  // GA2-18: 申请单搜索埋点 (94 号文档业务事件字典 web.biz_request.search)
  const startTime = Date.now()
  const filterCount =
    (searchModel.status ? 1 : 0) +
    (searchModel.title ? 1 : 0) +
    (searchModel.requestNo ? 1 : 0)
  track('web.biz_request.search.click', { payload: { filterCount } })
  try {
    const res: PageResult<BizRequest> = await getRequests({
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      status: searchModel.status || undefined,
      title: searchModel.title || undefined,
      requestNo: searchModel.requestNo || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
    // GA2-18: 搜索成功埋点 (不采集标题等业务字段)
    track('web.biz_request.search.success', {
      durationMs: Date.now() - startTime,
      payload: { filterCount, total: total.value },
    })
  } catch (err) {
    // GA2-18: 搜索失败埋点
    track('web.biz_request.search.failed', {
      result: 'failed',
      errorCode: (err as Error)?.message || 'SEARCH_FAILED',
      durationMs: Date.now() - startTime,
    })
    throw err
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  searchModel.requestNo = ''
  searchModel.title = ''
  searchModel.status = ''
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function handleDetail(row: BizRequest) {
  router.push(`/biz/requests/${row.id}`)
}

function handleCreate() {
  // GA2-18: 新建申请单点击埋点
  track('web.biz_request.create.click', { payload: { source: 'list' } })
  router.push('/biz/requests/create')
}

onMounted(loadData)
</script>

<template>
  <div>
    <!-- GA2-55: 使用 SearchForm 通用组件声明查询字段 -->
    <SearchForm
      :schema="searchSchema"
      :model="searchModel"
      :loading="loading"
      @search="handleSearch"
      @reset="handleReset"
    >
      <template #actions>
        <el-button v-if="canCreate()" type="success" @click="handleCreate">
          {{ $t('biz.request.action.create') }}
        </el-button>
      </template>
    </SearchForm>

    <!-- GA2-55: 使用 BaseTable 通用组件, 业务页面不再直接用 el-table+el-pagination -->
    <BaseTable
      :columns="columns"
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @page-change="handlePageChange"
    >
      <!-- 状态列: 使用 StatusBadge 替代 el-tag -->
      <template #col-requestStatus="{ row }">
        <StatusBadge
          :type="statusBadgeType[row.requestStatus] || 'info'"
          :label="statusLabel[row.requestStatus] || row.requestStatus"
        />
      </template>

      <!-- 操作列 -->
      <template #col-__actions="{ row }">
        <el-button size="small" @click="handleDetail(row as BizRequest)">{{ $t('common.action.view') }}</el-button>
      </template>
    </BaseTable>
  </div>
</template>
