<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { pageTickets, createTicket, listTicketCategories } from '@/api/ticket'
import type { WorkTicket, WorkTicketCategory, PageResult } from '@/api/types'

/**
 * 工单列表页。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * 支持状态/分类/优先级筛选 + 搜索 + 新建工单。
 */
const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const tableData = ref<WorkTicket[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const categories = ref<WorkTicketCategory[]>([])

// 筛选条件
const filterTicketNo = ref('')
const filterTitle = ref('')
const filterStatus = ref('')
const filterCategoryId = ref('')
const filterPriority = ref('')

// 新建工单弹窗
const createDialogVisible = ref(false)
const createForm = ref({
  title: '',
  description: '',
  categoryId: '',
  priority: 'MEDIUM',
})

const STATUS_OPTIONS = [
  { label: t('ticket.status.new'), value: 'NEW' },
  { label: t('ticket.status.assigned'), value: 'ASSIGNED' },
  { label: t('ticket.status.processing'), value: 'PROCESSING' },
  { label: t('ticket.status.suspended'), value: 'SUSPENDED' },
  { label: t('ticket.status.completed'), value: 'COMPLETED' },
  { label: t('ticket.status.closed'), value: 'CLOSED' },
]

const PRIORITY_OPTIONS = [
  { label: t('ticket.priority.low'), value: 'LOW' },
  { label: t('ticket.priority.medium'), value: 'MEDIUM' },
  { label: t('ticket.priority.high'), value: 'HIGH' },
  { label: t('ticket.priority.urgent'), value: 'URGENT' },
]

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function statusTagType(status: string): TagType {
  const map: Record<string, TagType> = {
    NEW: 'info',
    ASSIGNED: 'warning',
    PROCESSING: undefined,
    SUSPENDED: 'danger',
    COMPLETED: 'success',
    CLOSED: 'info',
  }
  return map[status] || 'info'
}

function statusLabel(status: string): string {
  return STATUS_OPTIONS.find((s) => s.value === status)?.label || status
}

function priorityTagType(priority: string): TagType {
  const map: Record<string, TagType> = { LOW: 'info', MEDIUM: undefined, HIGH: 'warning', URGENT: 'danger' }
  return map[priority] || undefined
}

function priorityLabel(priority: string): string {
  return PRIORITY_OPTIONS.find((p) => p.value === priority)?.label || priority
}

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<WorkTicket> = await pageTickets({
      page: currentPage.value,
      size: pageSize.value,
      ticketNo: filterTicketNo.value || undefined,
      title: filterTitle.value || undefined,
      status: filterStatus.value || undefined,
      categoryId: filterCategoryId.value || undefined,
      priority: filterPriority.value || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  categories.value = await listTicketCategories()
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filterTicketNo.value = ''
  filterTitle.value = ''
  filterStatus.value = ''
  filterCategoryId.value = ''
  filterPriority.value = ''
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function goDetail(row: WorkTicket) {
  router.push(`/tickets/${row.id}`)
}

function openCreateDialog() {
  createForm.value = { title: '', description: '', categoryId: '', priority: 'MEDIUM' }
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.title.trim()) {
    ElMessage.warning(t('ticket.msg.titleRequired'))
    return
  }
  if (!createForm.value.categoryId) {
    ElMessage.warning(t('ticket.msg.categoryRequired'))
    return
  }
  await createTicket(createForm.value)
  ElMessage.success(t('ticket.msg.createSuccess'))
  createDialogVisible.value = false
  loadData()
}

function isOverdue(row: WorkTicket): boolean {
  if (!row.slaDeadline) return false
  return ['NEW', 'ASSIGNED', 'PROCESSING', 'SUSPENDED'].includes(row.status)
    && new Date(row.slaDeadline) < new Date()
}

onMounted(() => {
  loadCategories()
  loadData()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('ticket.page.list') }}</h2>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('ticket.filter.ticketNo')">
          <el-input v-model="filterTicketNo" :placeholder="$t('ticket.placeholder.ticketNo')" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item :label="$t('ticket.filter.title')">
          <el-input v-model="filterTitle" :placeholder="$t('ticket.placeholder.title')" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item :label="$t('ticket.filter.status')">
          <el-select v-model="filterStatus" :placeholder="$t('ticket.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ticket.filter.category')">
          <el-select v-model="filterCategoryId" :placeholder="$t('ticket.placeholder.all')" clearable style="width: 140px">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ticket.filter.priority')">
          <el-select v-model="filterPriority" :placeholder="$t('ticket.placeholder.all')" clearable style="width: 100px">
            <el-option v-for="p in PRIORITY_OPTIONS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('ticket.action.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('ticket.action.reset') }}</el-button>
          <el-button type="success" @click="openCreateDialog">{{ $t('ticket.action.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工单列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe @row-click="goDetail" style="cursor: pointer">
      <el-table-column prop="ticketNo" :label="$t('ticket.field.ticketNo')" width="160" />
      <el-table-column prop="title" :label="$t('ticket.field.title')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="categoryNameSnapshot" :label="$t('ticket.field.category')" width="120" />
      <el-table-column prop="priority" :label="$t('ticket.field.priority')" width="90">
        <template #default="{ row }">
          <el-tag :type="priorityTagType(row.priority as string)" size="small">{{ priorityLabel(row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('ticket.field.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status as string)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="handlerNameSnapshot" :label="$t('ticket.field.handler')" width="120" show-overflow-tooltip />
      <el-table-column prop="slaDeadline" :label="$t('ticket.field.slaDeadline')" width="170">
        <template #default="{ row }">
          <span :style="{ color: isOverdue(row as WorkTicket) ? '#f56c6c' : '' }">
            {{ row.slaDeadline || '-' }}
          </span>
          <el-tag v-if="isOverdue(row as WorkTicket)" type="danger" size="small" style="margin-left: 4px">{{ $t('ticket.tag.overdue') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdTime" :label="$t('ticket.field.createdTime')" width="170" />
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />

    <!-- 新建工单弹窗 -->
    <el-dialog v-model="createDialogVisible" :title="$t('ticket.dialog.titleCreate')" width="500px">
      <el-form label-width="80px">
        <el-form-item :label="$t('ticket.field.title')" required>
          <el-input v-model="createForm.title" :placeholder="$t('ticket.placeholder.titleInput')" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('ticket.field.category')" required>
          <el-select v-model="createForm.categoryId" :placeholder="$t('ticket.placeholder.categorySelect')" style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="`${c.categoryName} (SLA ${c.slaHours}h)`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ticket.field.priority')">
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option v-for="p in PRIORITY_OPTIONS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('ticket.field.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="4" :placeholder="$t('ticket.placeholder.description')" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ $t('ticket.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreate">{{ $t('ticket.action.confirm') }}</el-button>
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
