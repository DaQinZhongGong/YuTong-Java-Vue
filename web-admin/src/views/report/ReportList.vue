<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageReports, deleteReport, refreshMaterializedView } from '@/api/report'
import type { RptReport, PageResult } from '@/api/types'

/**
 * 报表列表页。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 R1。
 * GA2-36 验证能力:
 *  - 报表 CRUD (列表/筛选/删除)
 *  - 物化视图刷新入口 (验证物化视图刷新能力)
 *  - 跳转详情页渲染 ECharts 图表 + 异步导出 + AI 指标解释
 */
const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const tableData = ref<RptReport[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选条件
const filterReportCode = ref('')
const filterReportName = ref('')
const filterReportType = ref('')
const filterStatus = ref('')

// 物化视图刷新 loading
const mvRefreshing = ref(false)

const REPORT_TYPE_OPTIONS = computed(() => [
  { label: t('report.list.type.chart'), value: 'CHART' },
  { label: t('report.list.type.mix'), value: 'MIX' },
  { label: t('report.list.type.table'), value: 'TABLE' },
  { label: t('report.list.type.metric'), value: 'METRIC' },
])

const STATUS_OPTIONS = computed(() => [
  { label: t('report.list.status.draft'), value: 'DRAFT' },
  { label: t('report.list.status.published'), value: 'PUBLISHED' },
  { label: t('report.list.status.archived'), value: 'ARCHIVED' },
])

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function reportTypeTagType(t: string): TagType {
  const map: Record<string, TagType> = { CHART: 'primary', MIX: 'warning', TABLE: 'info', METRIC: undefined }
  return map[t] || 'info'
}

function reportTypeLabel(type: string): string {
  return REPORT_TYPE_OPTIONS.value.find((o) => o.value === type)?.label || type
}

function statusTagType(s: string): TagType {
  const map: Record<string, TagType> = { DRAFT: 'info', PUBLISHED: 'success', ARCHIVED: 'warning' }
  return map[s] || 'info'
}

function statusLabel(s: string): string {
  return STATUS_OPTIONS.value.find((o: { value: string }) => o.value === s)?.label || s
}

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<RptReport> = await pageReports({
      page: currentPage.value,
      size: pageSize.value,
      reportCode: filterReportCode.value || undefined,
      reportName: filterReportName.value || undefined,
      reportType: filterReportType.value || undefined,
      status: filterStatus.value || undefined,
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
  filterReportCode.value = ''
  filterReportName.value = ''
  filterReportType.value = ''
  filterStatus.value = ''
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function goDetail(row: RptReport) {
  router.push(`/reports/${row.reportCode}`)
}

async function handleDelete(row: RptReport) {
  try {
    await ElMessageBox.confirm(t('report.list.msg.confirmDelete', { name: row.reportName }), t('report.list.msg.confirm'), { type: 'warning' })
    await deleteReport(row.reportCode)
    ElMessage.success(t('report.list.msg.deleteSuccess'))
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器统一提示
    }
  }
}

// GA2-36 验证物化视图刷新能力
const MATERIALIZED_VIEWS = [
  { name: 'rpt_mv_request_status_stat', label: t('report.list.mv.requestStatusStat') },
  { name: 'rpt_mv_request_trend_7d', label: t('report.list.mv.requestTrend7d') },
]

async function handleRefreshMv(mvName: string) {
  mvRefreshing.value = true
  try {
    await refreshMaterializedView(mvName)
    ElMessage.success(t('report.list.msg.mvRefreshSuccess', { name: mvName }))
  } finally {
    mvRefreshing.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('report.list.title') }}</h2>

    <!-- 物化视图刷新入口 (GA2-36 验证物化视图刷新能力) -->
    <el-card class="mv-card" shadow="never">
      <template #header>
        <span>{{ $t('report.list.mvRefresh') }}</span>
        <span class="mv-hint">{{ $t('report.list.mvHint') }}</span>
      </template>
      <el-button
        v-for="mv in MATERIALIZED_VIEWS"
        :key="mv.name"
        type="primary"
        plain
        :loading="mvRefreshing"
        @click="handleRefreshMv(mv.name)"
      >
        {{ $t('report.list.refresh') }} {{ mv.label }}
      </el-button>
    </el-card>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('report.list.reportCode')">
          <el-input v-model="filterReportCode" :placeholder="$t('report.list.reportCode')" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item :label="$t('report.list.reportName')">
          <el-input v-model="filterReportName" :placeholder="$t('report.list.reportName')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('report.list.type')">
          <el-select v-model="filterReportType" :placeholder="$t('report.list.all')" clearable style="width: 120px">
            <el-option v-for="o in REPORT_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('report.list.status')">
          <el-select v-model="filterStatus" :placeholder="$t('report.list.all')" clearable style="width: 120px">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('report.list.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('report.list.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 报表列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe @row-click="goDetail" style="cursor: pointer">
      <el-table-column prop="reportCode" :label="$t('report.list.reportCode')" width="220" />
      <el-table-column prop="reportName" :label="$t('report.list.reportName')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="reportType" :label="$t('report.list.type')" width="90">
        <template #default="{ row }">
          <el-tag :type="reportTypeTagType(row.reportType)" size="small">{{ reportTypeLabel(row.reportType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="versionNo" :label="$t('report.list.version')" width="70" />
      <el-table-column prop="status" :label="$t('report.list.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="$t('report.list.description')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdTime" :label="$t('report.list.createdTime')" width="170" />
      <el-table-column :label="$t('report.list.action')" width="160" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click.stop="goDetail(row as RptReport)">{{ $t('report.list.view') }}</el-button>
          <el-button type="danger" link size="small" @click.stop="handleDelete(row as RptReport)">{{ $t('report.list.delete') }}</el-button>
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
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 600;
}
.mv-card {
  margin-bottom: 16px;
}
.mv-hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
.filter-card {
  margin-bottom: 16px;
}
</style>
