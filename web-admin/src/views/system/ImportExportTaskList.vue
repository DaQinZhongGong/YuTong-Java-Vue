<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, View, RefreshRight } from '@element-plus/icons-vue'
import {
  getImportExportTasks,
  retryImportExportTask,
  type ImportExportTaskPageQuery,
} from '@/api/system'
import type { ImportExportTask } from '@/api/types'
import i18n from '@/locales'

const loading = ref(false)
const tableData = ref<ImportExportTask[]>([])
const total = ref(0)

const query = reactive<ImportExportTaskPageQuery>({
  page: 1,
  size: 10,
  taskType: '',
  status: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getImportExportTasks(query)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchData()
}

function handleReset() {
  query.taskType = ''
  query.status = ''
  query.page = 1
  fetchData()
}

function handlePageChange(page: number) {
  query.page = page
  fetchData()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchData()
}

function statusTagType(status: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING' || status === 'RUNNING') return 'warning'
  return 'info'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    SUCCESS: i18n.global.t('system.task.status.success'),
    FAILED: i18n.global.t('system.task.status.failed'),
    PENDING: i18n.global.t('system.task.status.pending'),
    RUNNING: i18n.global.t('system.task.status.running'),
  }
  return map[status] || status || '-'
}

function taskTypeLabel(taskType: string): string {
  const map: Record<string, string> = {
    IMPORT: i18n.global.t('common.action.import'),
    EXPORT: i18n.global.t('common.action.export'),
  }
  return map[taskType] || taskType || '-'
}

// ===== 详情对话框 =====
const detailVisible = ref(false)
const detailData = ref<ImportExportTask | null>(null)

function openDetail(row: ImportExportTask) {
  detailData.value = row
  detailVisible.value = true
}

async function handleRetry(row: ImportExportTask) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.task.message.retryConfirm', { type: taskTypeLabel(row.taskType) }),
      i18n.global.t('common.message.tip'),
      { confirmButtonText: i18n.global.t('common.action.confirm'), cancelButtonText: i18n.global.t('common.action.cancel'), type: 'warning' }
    )
    await retryImportExportTask(row.id)
    ElMessage.success(i18n.global.t('system.task.message.retrySuccess'))
    fetchData()
  } catch {
    // 用户取消
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="import-export-view">
    <h2 class="page-title">{{ $t('system.task.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('system.task.aria.search')">
        <el-select
          v-model="query.taskType"
          :placeholder="$t('system.task.placeholder.type')"
          clearable
          style="width: 140px"
          :aria-label="$t('system.task.aria.typeFilter')"
        >
          <el-option :label="$t('common.action.import')" value="IMPORT" />
          <el-option :label="$t('common.action.export')" value="EXPORT" />
        </el-select>
        <el-select
          v-model="query.status"
          :placeholder="$t('common.field.status')"
          clearable
          style="width: 140px"
          :aria-label="$t('system.task.aria.statusFilter')"
        >
          <el-option :label="$t('system.task.status.success')" value="SUCCESS" />
          <el-option :label="$t('system.task.status.failed')" value="FAILED" />
          <el-option :label="$t('system.task.status.pending')" value="PENDING" />
          <el-option :label="$t('system.task.status.running')" value="RUNNING" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.list') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('system.task.empty.noData')"
        :aria-label="$t('system.task.aria.list')"
      >
        <el-table-column :label="$t('system.task.field.type')" width="100" align="center">
          <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
        </el-table-column>
        <el-table-column prop="bizType" :label="$t('system.task.field.bizType')" width="140" />
        <el-table-column :label="$t('common.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.task.field.totalRows')" width="100" align="right">
          <template #default="{ row }">{{ row.totalRows ?? '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('system.task.status.success')" width="100" align="right">
          <template #default="{ row }">{{ row.successRows ?? '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('system.task.status.failed')" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'fail-cell': row.failRows && row.failRows > 0 }">
              {{ row.failRows ?? '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="startedTime" :label="$t('system.task.field.startedTime')" width="180" />
        <el-table-column prop="finishedTime" :label="$t('system.task.field.finishedTime')" width="180" />
        <el-table-column :label="$t('system.task.field.operation')" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="View"
              size="small"
              :aria-label="$t('common.action.detail')"
              @click="openDetail(row as ImportExportTask)"
            >{{ $t('common.action.view') }}</el-button>
            <el-button
              v-if="row.status === 'FAILED'"
              type="warning"
              link
              :icon="RefreshRight"
              size="small"
              :aria-label="$t('system.task.aria.retry')"
              @click="handleRetry(row as ImportExportTask)"
            >{{ $t('common.action.retry') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      :title="$t('system.task.title.detail')"
      width="640px"
      :aria-label="$t('system.task.aria.detailDialog')"
    >
      <el-descriptions v-if="detailData" :column="2" border>
        <el-descriptions-item :label="$t('system.task.field.type')">{{ taskTypeLabel(detailData.taskType) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.bizType')">{{ detailData.bizType || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.field.status')">
          <el-tag :type="statusTagType(detailData.status)" size="small">
            {{ statusLabel(detailData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.fileId')">{{ detailData.fileId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.totalRows')">{{ detailData.totalRows ?? '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.successRows')">{{ detailData.successRows ?? '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.failRows')">{{ detailData.failRows ?? '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.errorFileId')">{{ detailData.errorFileId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.startedTime')">{{ detailData.startedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.finishedTime')">{{ detailData.finishedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.task.field.errorMessage')" :span="2">
          <pre class="json-block">{{ detailData.errorMessage || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('common.action.close') }}</el-button>
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
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.fail-cell {
  color: var(--el-color-danger);
  font-weight: 600;
}
.json-block {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
