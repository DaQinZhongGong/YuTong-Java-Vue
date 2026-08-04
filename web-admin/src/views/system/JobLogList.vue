<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, View, RefreshRight } from '@element-plus/icons-vue'
import { getJobLogs, retryJob, type JobLogPageQuery } from '@/api/system'
import type { JobLog } from '@/api/types'
import i18n from '@/locales'

const loading = ref(false)
const tableData = ref<JobLog[]>([])
const total = ref(0)

const query = reactive<JobLogPageQuery>({
  page: 1,
  size: 10,
  jobName: '',
  status: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getJobLogs(query)
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
  query.jobName = ''
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

// 状态标签颜色映射: SUCCESS 绿 / FAILED 红 / PENDING 蓝 / RUNNING 橙
function statusTagType(status: string): 'success' | 'danger' | 'warning' | 'info' | 'primary' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    SUCCESS: i18n.global.t('system.jobLog.status.success'),
    FAILED: i18n.global.t('system.jobLog.status.failed'),
    PENDING: i18n.global.t('system.jobLog.status.pending'),
    RUNNING: i18n.global.t('system.jobLog.status.running'),
  }
  return map[status] || status || '-'
}

function formatDuration(ms?: number): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms} ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(2)} s`
  return `${(ms / 60000).toFixed(2)} min`
}

// ===== 详情对话框 =====
const detailVisible = ref(false)
const detailData = ref<JobLog | null>(null)

function openDetail(row: any) {
  detailData.value = row
  detailVisible.value = true
}

async function handleRetry(row: JobLog) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.jobLog.message.retryConfirm', { name: row.jobName || row.jobCode }),
      i18n.global.t('common.message.tip'),
      { confirmButtonText: i18n.global.t('common.action.confirm'), cancelButtonText: i18n.global.t('common.action.cancel'), type: 'warning' }
    )
    await retryJob(row.id)
    ElMessage.success(i18n.global.t('system.jobLog.message.retrySuccess'))
    fetchData()
  } catch {
    // 用户取消
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="job-log-view">
    <h2 class="page-title">{{ $t('system.jobLog.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('system.jobLog.aria.search')">
        <el-input
          v-model="query.jobName"
          :placeholder="$t('system.jobLog.field.name')"
          clearable
          style="width: 220px"
          :aria-label="$t('system.jobLog.field.name')"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.status"
          :placeholder="$t('common.field.status')"
          clearable
          style="width: 140px"
          :aria-label="$t('system.jobLog.aria.statusFilter')"
        >
          <el-option :label="$t('system.jobLog.status.success')" value="SUCCESS" />
          <el-option :label="$t('system.jobLog.status.failed')" value="FAILED" />
          <el-option :label="$t('system.jobLog.status.pending')" value="PENDING" />
          <el-option :label="$t('system.jobLog.status.running')" value="RUNNING" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.list') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('system.jobLog.empty.noData')"
        :aria-label="$t('system.jobLog.aria.list')"
      >
        <el-table-column prop="jobCode" :label="$t('system.jobLog.field.code')" width="160" />
        <el-table-column prop="jobName" :label="$t('system.jobLog.field.name')" min-width="180" />
        <el-table-column prop="bizType" :label="$t('system.jobLog.field.bizType')" width="120" align="center" />
        <el-table-column prop="triggerType" :label="$t('system.jobLog.field.triggerType')" width="100" align="center" />
        <el-table-column :label="$t('common.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.jobLog.field.duration')" width="120" align="right">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column prop="startTime" :label="$t('system.jobLog.field.startedTime')" width="180" />
        <el-table-column :label="$t('system.jobLog.field.operation')" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="View"
              size="small"
              :aria-label="$t('common.action.detail')"
              @click="openDetail(row)"
            >{{ $t('common.action.view') }}</el-button>
            <el-button
              v-if="row.status === 'FAILED'"
              type="warning"
              link
              :icon="RefreshRight"
              size="small"
              :aria-label="$t('system.jobLog.aria.retry')"
              @click="handleRetry(row as JobLog)"
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
      :title="$t('system.jobLog.title.detail')"
      width="640px"
      :aria-label="$t('system.jobLog.aria.detailDialog')"
    >
      <el-descriptions v-if="detailData" :column="2" border>
        <el-descriptions-item :label="$t('system.jobLog.field.code')">{{ detailData.jobCode || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.jobLog.field.name')">{{ detailData.jobName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.jobLog.field.bizType')">{{ detailData.bizType || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.jobLog.field.bizId')">{{ detailData.bizId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.jobLog.field.triggerType')">{{ detailData.triggerType || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.field.status')">
          <el-tag :type="statusTagType(detailData.status)" size="small">
            {{ statusLabel(detailData.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.jobLog.field.startedTime')">{{ detailData.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.jobLog.field.endTime')">{{ detailData.endTime || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.jobLog.field.duration')">{{ formatDuration(detailData.durationMs) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.field.traceId')">{{ detailData.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.jobLog.field.errorMessage')" :span="2">
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
