<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageDashboards, deleteDashboard, createDashboard } from '@/api/report-dashboard'
import type { RptDashboard, PageResult } from '@/api/report-dashboard'

/**
 * 大屏列表页。设计来源: 42-报表与大屏可视化设计 R3。
 * 提供大屏的列表、筛选、新建、删除、进入设计器入口。
 */
const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const tableData = ref<RptDashboard[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选条件
const filterKeyword = ref('')
const filterStatus = ref('')
const filterTheme = ref('')

const STATUS_OPTIONS = [
  { label: t('common.publishStatus.draft'), value: 'DRAFT' },
  { label: t('common.publishStatus.published'), value: 'PUBLISHED' },
]

const THEME_OPTIONS = [
  { label: t('dashboard.list.themeDark'), value: 'dark' },
  { label: t('dashboard.list.themeLight'), value: 'light' },
]

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function statusTagType(s: string): TagType {
  const map: Record<string, TagType> = { DRAFT: 'info', PUBLISHED: 'success' }
  return map[s] || 'info'
}

function statusLabel(s: string): string {
  return STATUS_OPTIONS.find((o) => o.value === s)?.label || s
}

function themeLabel(t: string): string {
  return THEME_OPTIONS.find((o) => o.value === t)?.label || t
}

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<RptDashboard> = await pageDashboards({
      page: currentPage.value,
      size: pageSize.value,
      keyword: filterKeyword.value || undefined,
      status: filterStatus.value || undefined,
      theme: filterTheme.value || undefined,
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
  filterKeyword.value = ''
  filterStatus.value = ''
  filterTheme.value = ''
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function goDesigner(row: RptDashboard) {
  router.push(`/dashboard-designer/${row.dashboardCode}`)
}

function goFullscreen(row: RptDashboard) {
  router.push(`/dashboard-fullscreen/${row.dashboardCode}`)
}

async function handleDelete(row: RptDashboard) {
  try {
    await ElMessageBox.confirm(t('dashboard.msg.deleteConfirm', { name: row.dashboardName }), t('dashboard.msg.deleteConfirmTitle'), { type: 'warning' })
    await deleteDashboard(row.dashboardCode)
    ElMessage.success(t('dashboard.msg.deleteSuccess'))
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      // 错误已由拦截器统一提示
    }
  }
}

// 新建大屏
const createDialogVisible = ref(false)
const createForm = ref({
  dashboardCode: '',
  dashboardName: '',
  theme: 'dark',
})

async function handleCreate() {
  if (!createForm.value.dashboardCode || !createForm.value.dashboardName) {
    ElMessage.warning(t('dashboard.msg.formIncomplete'))
    return
  }
  try {
    await createDashboard({
      dashboardCode: createForm.value.dashboardCode,
      dashboardName: createForm.value.dashboardName,
      theme: createForm.value.theme,
      layoutJson: JSON.stringify({
        canvas: { width: 1920, height: 1080, theme: createForm.value.theme, backgroundImage: '' },
        components: [],
      }),
    })
    ElMessage.success(t('dashboard.msg.createSuccess'))
    createDialogVisible.value = false
    createForm.value = { dashboardCode: '', dashboardName: '', theme: 'dark' }
    loadData()
  } catch {
    ElMessage.error(t('dashboard.msg.createFailed'))
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('dashboard.list.title') }}</h2>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('dashboard.list.keyword')">
          <el-input v-model="filterKeyword" :placeholder="$t('dashboard.list.keywordPlaceholder')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('dashboard.list.status')">
          <el-select v-model="filterStatus" :placeholder="$t('dashboard.list.all')" clearable style="width: 120px">
            <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('dashboard.list.theme')">
          <el-select v-model="filterTheme" :placeholder="$t('dashboard.list.all')" clearable style="width: 120px">
            <el-option v-for="o in THEME_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('dashboard.list.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('dashboard.list.reset') }}</el-button>
          <el-button type="success" @click="createDialogVisible = true">{{ $t('dashboard.list.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 大屏列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe style="cursor: pointer">
      <el-table-column prop="dashboardCode" :label="$t('dashboard.list.code')" width="220" />
      <el-table-column prop="dashboardName" :label="$t('dashboard.list.name')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="theme" :label="$t('dashboard.list.theme')" width="120">
        <template #default="{ row }">
          {{ themeLabel(row.theme) }}
        </template>
      </el-table-column>
      <el-table-column prop="canvasWidth" :label="$t('dashboard.list.width')" width="90" />
      <el-table-column prop="canvasHeight" :label="$t('dashboard.list.height')" width="90" />
      <el-table-column prop="status" :label="$t('dashboard.list.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="refreshInterval" :label="$t('dashboard.list.refreshInterval')" width="110" />
      <el-table-column prop="description" :label="$t('dashboard.list.description')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdTime" :label="$t('dashboard.list.createdTime')" width="170" />
      <el-table-column :label="$t('dashboard.list.action')" width="220" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click.stop="goDesigner(row as RptDashboard)">{{ $t('dashboard.list.design') }}</el-button>
          <el-button type="success" link size="small" @click.stop="goFullscreen(row as RptDashboard)">{{ $t('dashboard.list.fullscreen') }}</el-button>
          <el-button type="danger" link size="small" @click.stop="handleDelete(row as RptDashboard)">{{ $t('dashboard.list.delete') }}</el-button>
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

    <!-- 新建大屏弹窗 -->
    <el-dialog v-model="createDialogVisible" :title="$t('dashboard.list.create')" width="400px">
      <el-form label-width="80px">
        <el-form-item :label="$t('dashboard.list.codeLabel')">
          <el-input v-model="createForm.dashboardCode" :placeholder="$t('dashboard.list.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('dashboard.list.nameLabel')">
          <el-input v-model="createForm.dashboardName" :placeholder="$t('dashboard.list.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('dashboard.list.theme')">
          <el-select v-model="createForm.theme" style="width: 100%">
            <el-option :label="$t('dashboard.list.themeDark')" value="dark" />
            <el-option :label="$t('dashboard.list.themeLight')" value="light" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ $t('dashboard.list.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ $t('dashboard.list.confirm') }}</el-button>
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
</style>
