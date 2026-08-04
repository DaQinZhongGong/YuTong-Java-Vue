<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, View } from '@element-plus/icons-vue'
import { getOperationLogs, type OperationLogPageQuery } from '@/api/system'
import type { OperationLog } from '@/api/types'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<OperationLog[]>([])
const total = ref(0)

const query = reactive<OperationLogPageQuery>({
  page: 1,
  size: 10,
  operatorId: '',
  keyword: '',
  result: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getOperationLogs(query)
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
  query.operatorId = ''
  query.keyword = ''
  query.result = ''
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

// 结果标签颜色映射: SUCCESS 绿 / FAILED 红 / DENIED 橙
function resultTagType(result: string): 'success' | 'danger' | 'warning' | 'info' {
  if (result === 'SUCCESS') return 'success'
  if (result === 'FAILED') return 'danger'
  if (result === 'DENIED') return 'warning'
  return 'info'
}

function resultLabel(result: string): string {
  if (result === 'SUCCESS') return t('system.operationLog.status.success')
  if (result === 'FAILED') return t('system.operationLog.status.failed')
  if (result === 'DENIED') return t('system.operationLog.status.denied')
  return result || '-'
}

// ===== 详情对话框 =====
const detailVisible = ref(false)
const detailData = ref<OperationLog | null>(null)

function openDetail(row: OperationLog) {
  detailData.value = row
  detailVisible.value = true
}

function formatJson(jsonStr?: string): string {
  if (!jsonStr) return '-'
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    return jsonStr
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="operation-log-view">
    <h2 class="page-title">{{ $t('system.operationLog.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('system.operationLog.aria.search')">
        <el-input
          v-model="query.operatorId"
          :placeholder="$t('system.operationLog.placeholder.operatorId')"
          clearable
          style="width: 180px"
          :aria-label="$t('system.operationLog.placeholder.operatorId')"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="query.keyword"
          :placeholder="$t('system.operationLog.placeholder.contentKeyword')"
          clearable
          style="width: 220px"
          :aria-label="$t('system.operationLog.placeholder.contentKeyword')"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.result"
          :placeholder="$t('common.field.result')"
          clearable
          style="width: 140px"
          :aria-label="$t('system.operationLog.aria.resultFilter')"
        >
          <el-option :label="$t('system.operationLog.status.success')" value="SUCCESS" />
          <el-option :label="$t('system.operationLog.status.failed')" value="FAILED" />
          <el-option :label="$t('system.operationLog.status.denied')" value="DENIED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('system.operationLog.empty.noData')"
        :aria-label="$t('system.operationLog.aria.list')"
      >
        <el-table-column prop="operationType" :label="$t('system.operationLog.field.type')" width="120" align="center" />
        <el-table-column prop="module" :label="$t('system.operationLog.field.module')" width="100" align="center" />
        <el-table-column prop="bizType" :label="$t('system.operationLog.field.bizType')" width="140" />
        <el-table-column prop="content" :label="$t('system.operationLog.field.content')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="operatorName" :label="$t('system.operationLog.field.operator')" width="120" />
        <el-table-column :label="$t('common.field.result')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="resultTagType(row.result)" size="small">
              {{ resultLabel(row.result) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatedTime" :label="$t('system.operationLog.field.operatedTime')" width="180" />
        <el-table-column :label="$t('system.operationLog.field.operation')" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="View"
              size="small"
              :aria-label="$t('common.action.detail')"
              @click="openDetail(row as OperationLog)"
            >{{ $t('common.action.view') }}</el-button>
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
      :title="$t('system.operationLog.title.detail')"
      width="720px"
      :aria-label="$t('system.operationLog.aria.detailDialog')"
    >
      <el-descriptions v-if="detailData" :column="2" border>
        <el-descriptions-item :label="$t('system.operationLog.field.type')">{{ detailData.operationType }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.module')">{{ detailData.module || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.bizType')">{{ detailData.bizType || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.bizId')">{{ detailData.bizId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.operatorId')">{{ detailData.operatorId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.operatorName')">{{ detailData.operatorName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.field.result')">
          <el-tag :type="resultTagType(detailData.result)" size="small">
            {{ resultLabel(detailData.result) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.errorCode')">{{ detailData.errorCode || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.field.traceId')">{{ detailData.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.ip')">{{ detailData.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.operatedTime')">{{ detailData.operatedTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="User-Agent" :span="2">{{ detailData.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.content')" :span="2">{{ detailData.content || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.beforeSnapshot')" :span="2">
          <pre class="json-block">{{ formatJson(detailData.beforeJson) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.operationLog.field.afterSnapshot')" :span="2">
          <pre class="json-block">{{ formatJson(detailData.afterJson) }}</pre>
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
  max-height: 240px;
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
