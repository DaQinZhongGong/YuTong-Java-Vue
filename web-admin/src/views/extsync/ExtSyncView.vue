<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  Plus,
  Delete,
  Edit,
  VideoPlay,
  RefreshRight,
  Check,
  DataAnalysis,
  Connection,
  List as ListIcon,
  Document,
  Warning,
} from '@element-plus/icons-vue'
import {
  pageExtSystems,
  listAllExtSystems,
  createExtSystem,
  updateExtSystem,
  deleteExtSystem,
  pageExtSyncTasks,
  createExtSyncTask,
  updateExtSyncTask,
  deleteExtSyncTask,
  triggerExtSyncTask,
  pageExtSyncRecords,
  getExtSyncRecord,
  pageExtSyncErrors,
  retryExtSyncError,
  resolveExtSyncError,
  getExtSyncStats,
  type ExtSystem,
  type ExtSyncTask,
  type ExtSyncRecord,
  type ExtSyncError,
  type ExtSyncStatsVO,
  type SaveExtSystemRequest,
  type SaveExtSyncTaskRequest,
} from '@/api/ext-sync'

/**
 * 外部接口同步页。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * 5 个 Tab:
 *  1. 同步监控: 9 项指标统计看板 (系统数/任务数/活跃任务/记录数/近24h同步/成功/失败/{{ $t('extsync.stat.pendingError') }}/死信数)
 *  2. 外部系统: CRUD + 启用/停用 (HMAC_SHA256/API_KEY/BEARER_TOKEN/NONE)
 *  3. 同步任务: CRUD + 手动触发 (HTTP Client + 签名 + 重试 + 幂等 + 错误队列)
 *  4. 同步记录: 同步执行历史 (PENDING/RUNNING/SUCCESS/FAILED/PARTIAL)
 *  5. 错误队列: {{ $t('extsync.stat.deadLetter') }}管理 (PENDING/RETRYING/RESOLVED/DEAD_LETTER)
 *
 * 核心能力验证:
 *  - HTTP Client 适配: 触发同步时通过 RestClient 调用外部 API
 *  - 签名鉴权: HMAC-SHA256 + 4 个请求头 (X-Ext-Access-Key/X-Ext-Timestamp/X-Ext-Nonce/X-Ext-Signature)
 *  - 失败重试: 指数退避 (retry_backoff_ms * 2^attempt)
 *  - 幂等写入: (task_id, business_key) 唯一索引兜底
 *  - {{ $t('extsync.stat.deadLetter') }}: retry_count >= max_retry_count 时进入 DEAD_LETTER
 *  - 同步监控: 实时统计 + 记录追溯 + 错误队列
 */

const activeTab = ref<'stats' | 'systems' | 'tasks' | 'records' | 'errors'>('stats')

// ==================== 同步监控统计 ====================
const stats = ref<ExtSyncStatsVO>({})
const statsLoading = ref(false)

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await getExtSyncStats()
  } catch (e) {
    console.error('loadStats failed', e)
  } finally {
    statsLoading.value = false
  }
}

// ==================== 外部系统 ====================
const systemLoading = ref(false)
const systemList = ref<ExtSystem[]>([])
const systemTotal = ref(0)
const systemPageNo = ref(1)
const systemPageSize = ref(10)
const systemFilter = ref({ systemCode: '', systemName: '', status: '' })

const systemDialogVisible = ref(false)
const systemDialogMode = ref<'create' | 'edit'>('create')
const systemEditing = ref(false)
const systemForm = ref<SaveExtSystemRequest>({
  systemCode: '',
  systemName: '',
  description: '',
  endpoint: '',
  authType: 'HMAC_SHA256',
  credentials: '',
  connectTimeout: 5,
  readTimeout: 15,
  maxRetryCount: 3,
  retryBackoffMs: 1000,
  status: 'ACTIVE',
})

async function loadSystems() {
  systemLoading.value = true
  try {
    const page = await pageExtSystems({
      pageNo: systemPageNo.value,
      pageSize: systemPageSize.value,
      systemCode: systemFilter.value.systemCode || undefined,
      systemName: systemFilter.value.systemName || undefined,
      status: systemFilter.value.status || undefined,
    })
    systemList.value = page.records || []
    systemTotal.value = page.total || 0
  } catch (e) {
    console.error('loadSystems failed', e)
  } finally {
    systemLoading.value = false
  }
}

function openSystemDialog(mode: 'create' | 'edit', system?: ExtSystem) {
  systemDialogMode.value = mode
  if (mode === 'create') {
    systemForm.value = {
      systemCode: '',
      systemName: '',
      description: '',
      endpoint: '',
      authType: 'HMAC_SHA256',
      credentials: '',
      connectTimeout: 5,
      readTimeout: 15,
      maxRetryCount: 3,
      retryBackoffMs: 1000,
      status: 'ACTIVE',
    }
  } else if (system) {
    systemForm.value = {
      systemCode: system.systemCode,
      systemName: system.systemName,
      description: system.description || '',
      endpoint: system.endpoint,
      authType: system.authType,
      credentials: system.credentials || '',
      connectTimeout: system.connectTimeout,
      readTimeout: system.readTimeout,
      maxRetryCount: system.maxRetryCount,
      retryBackoffMs: system.retryBackoffMs,
      status: system.status,
    }
  }
  systemDialogVisible.value = true
}

async function submitSystem() {
  systemEditing.value = true
  try {
    if (systemDialogMode.value === 'create') {
      await createExtSystem(systemForm.value)
      ElMessage.success('外部系统创建成功')
    } else {
      const editing = systemList.value.find((s) => s.systemCode === systemForm.value.systemCode)
      if (editing) {
        await updateExtSystem(editing.id, systemForm.value)
        ElMessage.success('外部系统更新成功')
      }
    }
    systemDialogVisible.value = false
    await loadSystems()
    await loadStats()
  } catch (e) {
    console.error('submitSystem failed', e)
  } finally {
    systemEditing.value = false
  }
}

async function removeSystem(system: ExtSystem) {
  try {
    await ElMessageBox.confirm(`确定要删除外部系统「${system.systemName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteExtSystem(system.id)
    ElMessage.success('删除成功')
    await loadSystems()
    await loadStats()
  } catch {
    // 用户取消
  }
}

// ==================== 同步任务 ====================
const taskLoading = ref(false)
const taskList = ref<ExtSyncTask[]>([])
const taskTotal = ref(0)
const taskPageNo = ref(1)
const taskPageSize = ref(10)
const taskFilter = ref({ taskCode: '', taskName: '', systemId: '', status: '' })
const allSystems = ref<ExtSystem[]>([])

const taskDialogVisible = ref(false)
const taskDialogMode = ref<'create' | 'edit'>('create')
const taskEditing = ref(false)
const taskForm = ref<SaveExtSyncTaskRequest>({
  taskCode: '',
  taskName: '',
  systemId: '',
  description: '',
  sourceApi: '',
  httpMethod: 'GET',
  requestTemplate: '',
  businessKeyField: 'id',
  syncMode: 'FULL',
  targetTable: '',
  cronExpression: '',
  status: 'ACTIVE',
})

const triggerLoading = ref<Record<string, boolean>>({})

async function loadTasks() {
  taskLoading.value = true
  try {
    const page = await pageExtSyncTasks({
      pageNo: taskPageNo.value,
      pageSize: taskPageSize.value,
      taskCode: taskFilter.value.taskCode || undefined,
      taskName: taskFilter.value.taskName || undefined,
      systemId: taskFilter.value.systemId || undefined,
      status: taskFilter.value.status || undefined,
    })
    taskList.value = page.records || []
    taskTotal.value = page.total || 0
  } catch (e) {
    console.error('loadTasks failed', e)
  } finally {
    taskLoading.value = false
  }
}

async function loadAllSystems() {
  try {
    allSystems.value = await listAllExtSystems()
  } catch (e) {
    console.error('loadAllSystems failed', e)
  }
}

function getSystemName(systemId: string): string {
  const sys = allSystems.value.find((s) => s.id === systemId)
  return sys ? sys.systemName : systemId
}

function openTaskDialog(mode: 'create' | 'edit', task?: ExtSyncTask) {
  taskDialogMode.value = mode
  if (mode === 'create') {
    taskForm.value = {
      taskCode: '',
      taskName: '',
      systemId: allSystems.value[0]?.id || '',
      description: '',
      sourceApi: '/orders',
      httpMethod: 'GET',
      requestTemplate: '',
      businessKeyField: 'id',
      syncMode: 'FULL',
      targetTable: '',
      cronExpression: '',
      status: 'ACTIVE',
    }
  } else if (task) {
    taskForm.value = {
      taskCode: task.taskCode,
      taskName: task.taskName,
      systemId: task.systemId,
      description: task.description || '',
      sourceApi: task.sourceApi,
      httpMethod: task.httpMethod,
      requestTemplate: task.requestTemplate || '',
      businessKeyField: task.businessKeyField || 'id',
      syncMode: task.syncMode,
      targetTable: task.targetTable || '',
      cronExpression: task.cronExpression || '',
      status: task.status,
    }
  }
  taskDialogVisible.value = true
}

async function submitTask() {
  taskEditing.value = true
  try {
    if (taskDialogMode.value === 'create') {
      await createExtSyncTask(taskForm.value)
      ElMessage.success('同步任务创建成功')
    } else {
      const editing = taskList.value.find((t) => t.taskCode === taskForm.value.taskCode)
      if (editing) {
        await updateExtSyncTask(editing.id, taskForm.value)
        ElMessage.success('同步任务更新成功')
      }
    }
    taskDialogVisible.value = false
    await loadTasks()
    await loadStats()
  } catch (e) {
    console.error('submitTask failed', e)
  } finally {
    taskEditing.value = false
  }
}

async function removeTask(task: ExtSyncTask) {
  try {
    await ElMessageBox.confirm(`确定要删除同步任务「${task.taskName}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteExtSyncTask(task.id)
    ElMessage.success('删除成功')
    await loadTasks()
    await loadStats()
  } catch {
    // 用户取消
  }
}

async function triggerTask(task: ExtSyncTask) {
  triggerLoading.value[task.id] = true
  try {
    const record = await triggerExtSyncTask(task.id)
    ElMessage.success(`同步已触发, 状态: ${record.status}, 成功 ${record.successCount || 0}/${record.totalCount || 0}`)
    await loadRecords()
    await loadErrors()
    await loadStats()
  } catch (e) {
    console.error('triggerTask failed', e)
  } finally {
    triggerLoading.value[task.id] = false
  }
}

// ==================== 同步记录 ====================
const recordLoading = ref(false)
const recordList = ref<ExtSyncRecord[]>([])
const recordTotal = ref(0)
const recordPageNo = ref(1)
const recordPageSize = ref(10)
const recordFilter = ref({ taskId: '', status: '', recordNo: '' })

const recordDetailVisible = ref(false)
const recordDetail = ref<ExtSyncRecord | null>(null)

async function loadRecords() {
  recordLoading.value = true
  try {
    const page = await pageExtSyncRecords({
      pageNo: recordPageNo.value,
      pageSize: recordPageSize.value,
      taskId: recordFilter.value.taskId || undefined,
      status: recordFilter.value.status || undefined,
      recordNo: recordFilter.value.recordNo || undefined,
    })
    recordList.value = page.records || []
    recordTotal.value = page.total || 0
  } catch (e) {
    console.error('loadRecords failed', e)
  } finally {
    recordLoading.value = false
  }
}

async function viewRecord(record: ExtSyncRecord) {
  try {
    recordDetail.value = await getExtSyncRecord(record.id)
    recordDetailVisible.value = true
  } catch (e) {
    console.error('viewRecord failed', e)
  }
}

function getTaskName(taskId: string): string {
  const task = taskList.value.find((t) => t.id === taskId)
  return task ? task.taskName : taskId
}

// ==================== 错误队列 ====================
const errorLoading = ref(false)
const errorList = ref<ExtSyncError[]>([])
const errorTotal = ref(0)
const errorPageNo = ref(1)
const errorPageSize = ref(10)
const errorFilter = ref({ taskId: '', status: '', businessKey: '' })

const errorActionLoading = ref<Record<string, boolean>>({})

async function loadErrors() {
  errorLoading.value = true
  try {
    const page = await pageExtSyncErrors({
      pageNo: errorPageNo.value,
      pageSize: errorPageSize.value,
      taskId: errorFilter.value.taskId || undefined,
      status: errorFilter.value.status || undefined,
      businessKey: errorFilter.value.businessKey || undefined,
    })
    errorList.value = page.records || []
    errorTotal.value = page.total || 0
  } catch (e) {
    console.error('loadErrors failed', e)
  } finally {
    errorLoading.value = false
  }
}

async function retryError(error: ExtSyncError) {
  errorActionLoading.value[error.id] = true
  try {
    const updated = await retryExtSyncError(error.id)
    ElMessage.success(`重试完成, 状态: ${updated.status}, 重试次数: ${updated.retryCount}`)
    await loadErrors()
    await loadStats()
  } catch (e) {
    console.error('retryError failed', e)
  } finally {
    errorActionLoading.value[error.id] = false
  }
}

async function resolveError(error: ExtSyncError) {
  try {
    await ElMessageBox.confirm(`确定要手动解决错误「${error.businessKey}」吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    errorActionLoading.value[error.id] = true
    await resolveExtSyncError(error.id)
    ElMessage.success('错误已标记为已解决')
    await loadErrors()
    await loadStats()
  } catch {
    // 用户取消
  } finally {
    errorActionLoading.value[error.id] = false
  }
}

// ==================== 状态颜色映射 ====================
function statusTagType(status: string): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  switch (status) {
    case 'SUCCESS':
    case 'RESOLVED':
    case 'ACTIVE':
      return 'success'
    case 'FAILED':
    case 'DEAD_LETTER':
    case 'DISABLED':
      return 'danger'
    case 'PARTIAL':
    case 'RETRYING':
    case 'PENDING':
      return 'warning'
    case 'RUNNING':
      return 'primary'
    default:
      return 'info'
  }
}

// ==================== 初始化 ====================
async function refreshAll() {
  await Promise.all([loadStats(), loadSystems(), loadTasks(), loadRecords(), loadErrors(), loadAllSystems()])
}

onMounted(() => {
  refreshAll()
})
</script>

<template>
  <div class="ext-sync-page">
    <div class="page-header">
      <h2 class="page-title">{{ $t('extsync.title') }}</h2>
      <p class="page-desc">
        {{ $t('extsync.pageDesc') }}
      </p>
      <el-button :icon="Refresh" size="small" @click="refreshAll" :loading="statsLoading">{{ $t('extsync.refreshAll') }}</el-button>
    </div>

    <el-tabs v-model="activeTab" class="content-tabs">
      <!-- ==================== 同步监控 ==================== -->
      <el-tab-pane name="stats">
        <template #label>
          <el-icon><DataAnalysis /></el-icon>
          <span>{{ $t('extsync.tabStats') }}</span>
        </template>

        <el-row :gutter="16" v-loading="statsLoading">
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-label">{{ $t('extsync.stat.systemCount') }}</div>
              <div class="stat-value">{{ stats.systemCount ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-label">{{ $t('extsync.stat.taskCount') }}</div>
              <div class="stat-value">{{ stats.taskCount ?? 0 }}</div>
              <div class="stat-sub">{{ $t('extsync.stat.active') }} {{ stats.activeTaskCount ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-label">{{ $t('extsync.stat.recordCount') }}</div>
              <div class="stat-value">{{ stats.recordCount ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-label">{{ $t('extsync.stat.recentSync') }}</div>
              <div class="stat-value">{{ stats.recentRecordCount ?? 0 }}</div>
              <div class="stat-sub">
                {{ $t('extsync.stat.success') }} <span class="text-success">{{ stats.recentSuccessCount ?? 0 }}</span>
                / {{ $t('extsync.stat.failed') }} <span class="text-danger">{{ stats.recentFailedCount ?? 0 }}</span>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" style="margin-top: 16px">
          <el-col :span="12">
            <el-card class="stat-card alert-card">
              <div class="stat-label">
                <el-icon><Warning /></el-icon>
                {{ $t('extsync.stat.pendingError') }}
              </div>
              <div class="stat-value text-warning">{{ stats.pendingErrorCount ?? 0 }}</div>
              <div class="stat-sub">PENDING + RETRYING</div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card class="stat-card danger-card">
              <div class="stat-label">
                <el-icon><Warning /></el-icon>
                {{ $t('extsync.stat.deadLetter') }}
              </div>
              <div class="stat-value text-danger">{{ stats.deadLetterCount ?? 0 }}</div>
              <div class="stat-sub">{{ $t('extsync.stat.maxRetryReached') }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-alert
          :title="$t('extsync.alert.title')"
          type="info"
          :closable="false"
          style="margin-top: 16px"
        >
          <template #default>
            <ol class="capability-list">
              <li>{{ $t('extsync.alert.cap1') }}</li>
              <li>{{ $t('extsync.alert.cap2') }}</li>
              <li>{{ $t('extsync.alert.cap3') }}</li>
              <li>{{ $t('extsync.alert.cap4') }}</li>
              <li>{{ $t('extsync.alert.cap5') }}</li>
              <li>{{ $t('extsync.alert.cap6') }}</li>
            </ol>
          </template>
        </el-alert>
      </el-tab-pane>

      <!-- ==================== 外部系统 ==================== -->
      <el-tab-pane name="systems">
        <template #label>
          <el-icon><Connection /></el-icon>
          <span>{{ $t('extsync.tabSystems') }}</span>
        </template>

        <div class="filter-bar">
          <el-input v-model="systemFilter.systemCode" :placeholder="$t('extsync.systemCode')" clearable style="width: 160px" />
          <el-input v-model="systemFilter.systemName" :placeholder="$t('extsync.systemName')" clearable style="width: 200px" />
          <el-select v-model="systemFilter.status" :placeholder="$t('extsync.status')" clearable style="width: 120px">
            <el-option :label="$t('extsync.active')" value="ACTIVE" />
            <el-option :label="$t('extsync.disabled')" value="DISABLED" />
          </el-select>
          <el-button :icon="Refresh" @click="loadSystems">{{ $t('extsync.query') }}</el-button>
          <el-button type="primary" :icon="Plus" @click="openSystemDialog('create')">{{ $t('extsync.createSystem') }}</el-button>
        </div>

        <el-table v-loading="systemLoading" :data="systemList" border style="width: 100%">
          <el-table-column prop="systemCode" :label="$t('extsync.systemCode')" width="140" />
          <el-table-column prop="systemName" :label="$t('extsync.systemName')" width="180" />
          <el-table-column prop="endpoint" :label="$t('extsync.endpoint')" min-width="280" show-overflow-tooltip />
          <el-table-column prop="authType" :label="$t('extsync.authType')" width="140" />
          <el-table-column :label="$t('extsync.timeout')" width="100">
            <template #default="{ row }">
              {{ $t('extsync.timeoutConnect') }} {{ row.connectTimeout }} / {{ $t('extsync.timeoutRead') }} {{ row.readTimeout }}
            </template>
          </el-table-column>
          <el-table-column :label="$t('extsync.retryPolicy')" width="140">
            <template #default="{ row }">
              {{ row.maxRetryCount }} {{ $t('extsync.retryTimes') }} / {{ $t('extsync.retryBackoff') }} {{ row.retryBackoffMs }}ms
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="$t('extsync.status')" width="80">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('extsync.action')" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :icon="Edit" @click="openSystemDialog('edit', row as ExtSystem)">{{ $t('extsync.edit') }}</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="removeSystem(row as ExtSystem)">{{ $t('extsync.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="systemPageNo"
          v-model:page-size="systemPageSize"
          :total="systemTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px"
          @current-change="loadSystems"
          @size-change="loadSystems"
        />
      </el-tab-pane>

      <!-- ==================== 同步任务 ==================== -->
      <el-tab-pane name="tasks">
        <template #label>
          <el-icon><ListIcon /></el-icon>
          <span>{{ $t('extsync.tabTasks') }}</span>
        </template>

        <div class="filter-bar">
          <el-input v-model="taskFilter.taskCode" :placeholder="$t('extsync.taskCode')" clearable style="width: 160px" />
          <el-input v-model="taskFilter.taskName" :placeholder="$t('extsync.taskName')" clearable style="width: 200px" />
          <el-select v-model="taskFilter.systemId" :placeholder="$t('extsync.extSystem')" clearable style="width: 180px">
            <el-option
              v-for="sys in allSystems"
              :key="sys.id"
              :label="sys.systemName"
              :value="sys.id"
            />
          </el-select>
          <el-select v-model="taskFilter.status" :placeholder="$t('extsync.status')" clearable style="width: 120px">
            <el-option :label="$t('extsync.active')" value="ACTIVE" />
            <el-option :label="$t('extsync.disabled')" value="DISABLED" />
          </el-select>
          <el-button :icon="Refresh" @click="loadTasks">{{ $t('extsync.query') }}</el-button>
          <el-button type="primary" :icon="Plus" @click="openTaskDialog('create')">{{ $t('extsync.createTask') }}</el-button>
        </div>

        <el-table v-loading="taskLoading" :data="taskList" border style="width: 100%">
          <el-table-column prop="taskCode" :label="$t('extsync.taskCode')" width="160" />
          <el-table-column prop="taskName" :label="$t('extsync.taskName')" width="180" />
          <el-table-column :label="$t('extsync.extSystem')" width="160">
            <template #default="{ row }">{{ getSystemName(row.systemId) }}</template>
          </el-table-column>
          <el-table-column prop="sourceApi" :label="$t('extsync.sourceApi')" width="140" />
          <el-table-column prop="httpMethod" :label="$t('extsync.method')" width="80" />
          <el-table-column prop="businessKeyField" :label="$t('extsync.businessKeyField')" width="120" />
          <el-table-column prop="syncMode" :label="$t('extsync.syncMode')" width="100" />
          <el-table-column prop="cronExpression" :label="$t('extsync.cronExpression')" width="160">
            <template #default="{ row }">{{ row.cronExpression || $t('extsync.manualOnly') }}</template>
          </el-table-column>
          <el-table-column prop="status" :label="$t('extsync.status')" width="80">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('extsync.action')" width="280" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                type="primary"
                :icon="VideoPlay"
                :loading="triggerLoading[row.id]"
                @click="triggerTask(row as ExtSyncTask)"
              >{{ $t('extsync.trigger') }}</el-button>
              <el-button size="small" :icon="Edit" @click="openTaskDialog('edit', row as ExtSyncTask)">{{ $t('extsync.edit') }}</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="removeTask(row as ExtSyncTask)">{{ $t('extsync.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="taskPageNo"
          v-model:page-size="taskPageSize"
          :total="taskTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px"
          @current-change="loadTasks"
          @size-change="loadTasks"
        />
      </el-tab-pane>

      <!-- ==================== 同步记录 ==================== -->
      <el-tab-pane name="records">
        <template #label>
          <el-icon><Document /></el-icon>
          <span>{{ $t('extsync.tabRecords') }}</span>
        </template>

        <div class="filter-bar">
          <el-input v-model="recordFilter.recordNo" :placeholder="$t('extsync.recordNo')" clearable style="width: 220px" />
          <el-input v-model="recordFilter.taskId" :placeholder="$t('extsync.taskId')" clearable style="width: 220px" />
          <el-select v-model="recordFilter.status" :placeholder="$t('extsync.status')" clearable style="width: 140px">
            <el-option label="PENDING" value="PENDING" />
            <el-option label="RUNNING" value="RUNNING" />
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="FAILED" value="FAILED" />
            <el-option label="PARTIAL" value="PARTIAL" />
          </el-select>
          <el-button :icon="Refresh" @click="loadRecords">{{ $t('extsync.query') }}</el-button>
        </div>

        <el-table v-loading="recordLoading" :data="recordList" border style="width: 100%">
          <el-table-column prop="recordNo" :label="$t('extsync.recordNo')" width="240" />
          <el-table-column :label="$t('extsync.task')" width="180">
            <template #default="{ row }">{{ getTaskName(row.taskId) }}</template>
          </el-table-column>
          <el-table-column prop="batchNo" :label="$t('extsync.batchNo')" width="180" />
          <el-table-column prop="status" :label="$t('extsync.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="triggerType" :label="$t('extsync.triggerType')" width="100" />
          <el-table-column :label="$t('extsync.statistics')" width="160">
            <template #default="{ row }">
              {{ $t('extsync.statTotal') }} {{ row.totalCount ?? 0 }} / {{ $t('extsync.stat.success') }} {{ row.successCount ?? 0 }} / {{ $t('extsync.stat.failed') }} {{ row.failedCount ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column prop="httpStatus" label="HTTP" width="80" />
          <el-table-column prop="durationMs" :label="$t('extsync.latencyMs')" width="100" />
          <el-table-column prop="startedTime" :label="$t('extsync.startedTime')" width="180" />
          <el-table-column :label="$t('extsync.action')" width="100" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :icon="Document" @click="viewRecord(row as ExtSyncRecord)">{{ $t('extsync.detail') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="recordPageNo"
          v-model:page-size="recordPageSize"
          :total="recordTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px"
          @current-change="loadRecords"
          @size-change="loadRecords"
        />
      </el-tab-pane>

      <!-- ==================== 错误队列 ==================== -->
      <el-tab-pane name="errors">
        <template #label>
          <el-icon><Warning /></el-icon>
          <span>{{ $t('extsync.tabErrors') }}</span>
        </template>

        <div class="filter-bar">
          <el-input v-model="errorFilter.businessKey" :placeholder="$t('extsync.businessKey')" clearable style="width: 200px" />
          <el-input v-model="errorFilter.taskId" :placeholder="$t('extsync.taskId')" clearable style="width: 220px" />
          <el-select v-model="errorFilter.status" :placeholder="$t('extsync.status')" clearable style="width: 160px">
            <el-option label="PENDING" value="PENDING" />
            <el-option label="RETRYING" value="RETRYING" />
            <el-option label="RESOLVED" value="RESOLVED" />
            <el-option label="DEAD_LETTER" value="DEAD_LETTER" />
          </el-select>
          <el-button :icon="Refresh" @click="loadErrors">{{ $t('extsync.query') }}</el-button>
        </div>

        <el-table v-loading="errorLoading" :data="errorList" border style="width: 100%">
          <el-table-column prop="businessKey" :label="$t('extsync.businessKey')" width="200" />
          <el-table-column :label="$t('extsync.task')" width="180">
            <template #default="{ row }">{{ getTaskName(row.taskId) }}</template>
          </el-table-column>
          <el-table-column prop="errorCode" :label="$t('extsync.errorCode')" width="160" />
          <el-table-column prop="errorMessage" :label="$t('extsync.errorMessage')" min-width="240" show-overflow-tooltip />
          <el-table-column prop="httpStatus" label="HTTP" width="80" />
          <el-table-column prop="retryCount" :label="$t('extsync.retryCount')" width="100" />
          <el-table-column prop="status" :label="$t('extsync.status')" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdTime" :label="$t('extsync.createdTime')" width="180" />
          <el-table-column :label="$t('extsync.action')" width="200" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                type="warning"
                :icon="RefreshRight"
                :loading="errorActionLoading[row.id]"
                :disabled="row.status === 'RESOLVED'"
                @click="retryError(row as ExtSyncError)"
              >{{ $t('extsync.retry') }}</el-button>
              <el-button
                size="small"
                type="success"
                :icon="Check"
                :loading="errorActionLoading[row.id]"
                :disabled="row.status === 'RESOLVED'"
                @click="resolveError(row as ExtSyncError)"
              >{{ $t('extsync.resolve') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="errorPageNo"
          v-model:page-size="errorPageSize"
          :total="errorTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          style="margin-top: 12px"
          @current-change="loadErrors"
          @size-change="loadErrors"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 外部系统弹窗 ==================== -->
    <el-dialog
      v-model="systemDialogVisible"
      :title="systemDialogMode === 'create' ? $t('extsync.createSystemTitle') : $t('extsync.editSystemTitle')"
      width="640px"
    >
      <el-form :model="systemForm" label-width="120px">
        <el-form-item :label="$t('extsync.systemCode')">
          <el-input
            v-model="systemForm.systemCode"
            :placeholder="$t('extsync.placeholder.systemCodeExample')"
            :disabled="systemDialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item :label="$t('extsync.systemName')">
          <el-input v-model="systemForm.systemName" :placeholder="$t('extsync.placeholder.systemNameExample')" />
        </el-form-item>
        <el-form-item :label="$t('extsync.endpoint')">
          <el-input v-model="systemForm.endpoint" :placeholder="$t('extsync.placeholder.endpointExample')" />
        </el-form-item>
        <el-form-item :label="$t('extsync.authType')">
          <el-select v-model="systemForm.authType" style="width: 100%">
            <el-option label="HMAC-SHA256" value="HMAC_SHA256" />
            <el-option label="API Key" value="API_KEY" />
            <el-option label="Bearer Token" value="BEARER_TOKEN" />
            <el-option :label="$t('extsync.noAuth')" value="NONE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('extsync.credentialsJson')">
          <el-input
            v-model="systemForm.credentials"
            type="textarea"
            :rows="3"
            :placeholder="$t('extsync.placeholder.credentialsExample')"
          />
        </el-form-item>
        <el-form-item :label="$t('extsync.connectTimeout')">
          <el-input-number v-model="systemForm.connectTimeout" :min="1" :max="60" />
        </el-form-item>
        <el-form-item :label="$t('extsync.readTimeout')">
          <el-input-number v-model="systemForm.readTimeout" :min="1" :max="120" />
        </el-form-item>
        <el-form-item :label="$t('extsync.maxRetryCount')">
          <el-input-number v-model="systemForm.maxRetryCount" :min="0" :max="10" />
        </el-form-item>
        <el-form-item :label="$t('extsync.retryBackoffMs')">
          <el-input-number v-model="systemForm.retryBackoffMs" :min="100" :max="60000" :step="500" />
        </el-form-item>
        <el-form-item :label="$t('extsync.status')">
          <el-select v-model="systemForm.status" style="width: 100%">
            <el-option :label="$t('extsync.active')" value="ACTIVE" />
            <el-option :label="$t('extsync.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('extsync.description')">
          <el-input v-model="systemForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="systemDialogVisible = false">{{ $t('extsync.cancel') }}</el-button>
        <el-button type="primary" :loading="systemEditing" @click="submitSystem">{{ $t('extsync.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 同步任务弹窗 ==================== -->
    <el-dialog
      v-model="taskDialogVisible"
      :title="taskDialogMode === 'create' ? $t('extsync.createTaskTitle') : $t('extsync.editTaskTitle')"
      width="640px"
    >
      <el-form :model="taskForm" label-width="120px">
        <el-form-item :label="$t('extsync.taskCode')">
          <el-input
            v-model="taskForm.taskCode"
            :placeholder="$t('extsync.placeholder.taskCodeExample')"
            :disabled="taskDialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item :label="$t('extsync.taskName')">
          <el-input v-model="taskForm.taskName" :placeholder="$t('extsync.placeholder.taskNameExample')" />
        </el-form-item>
        <el-form-item :label="$t('extsync.extSystem')">
          <el-select v-model="taskForm.systemId" style="width: 100%">
            <el-option
              v-for="sys in allSystems"
              :key="sys.id"
              :label="sys.systemName"
              :value="sys.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('extsync.sourceApi')">
          <el-input v-model="taskForm.sourceApi" :placeholder="$t('extsync.placeholder.sourceApiExample')" />
        </el-form-item>
        <el-form-item :label="$t('extsync.httpMethod')">
          <el-select v-model="taskForm.httpMethod" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('extsync.requestTemplate')">
          <el-input
            v-model="taskForm.requestTemplate"
            type="textarea"
            :rows="3"
            :placeholder="$t('extsync.placeholder.requestTemplate')"
          />
        </el-form-item>
        <el-form-item :label="$t('extsync.businessKeyField')">
          <el-input v-model="taskForm.businessKeyField" :placeholder="$t('extsync.placeholder.businessKeyExample')" />
        </el-form-item>
        <el-form-item :label="$t('extsync.syncMode')">
          <el-select v-model="taskForm.syncMode" style="width: 100%">
            <el-option :label="$t('extsync.fullSync')" value="FULL" />
            <el-option :label="$t('extsync.incrementalSync')" value="INCREMENTAL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('extsync.targetTable')">
          <el-input v-model="taskForm.targetTable" :placeholder="$t('extsync.placeholder.targetTable')" />
        </el-form-item>
        <el-form-item :label="$t('extsync.cronExpression')">
          <el-input v-model="taskForm.cronExpression" :placeholder="$t('extsync.placeholder.cronExpression')" />
        </el-form-item>
        <el-form-item :label="$t('extsync.status')">
          <el-select v-model="taskForm.status" style="width: 100%">
            <el-option :label="$t('extsync.active')" value="ACTIVE" />
            <el-option :label="$t('extsync.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('extsync.description')">
          <el-input v-model="taskForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">{{ $t('extsync.cancel') }}</el-button>
        <el-button type="primary" :loading="taskEditing" @click="submitTask">{{ $t('extsync.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 同步记录详情弹窗 ==================== -->
    <el-dialog v-model="recordDetailVisible" :title="$t('extsync.recordDetailTitle')" width="800px">
      <el-descriptions v-if="recordDetail" :column="2" border>
        <el-descriptions-item :label="$t('extsync.recordNo')">{{ recordDetail.recordNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.batchNo')">{{ recordDetail.batchNo }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.status')">
          <el-tag :type="statusTagType(recordDetail.status)" size="small">{{ recordDetail.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.triggerType')">{{ recordDetail.triggerType }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.totalCount')">{{ recordDetail.totalCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.successCount')">{{ recordDetail.successCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.failedCount')">{{ recordDetail.failedCount }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.httpStatus')">{{ recordDetail.httpStatus }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.latencyMs')">{{ recordDetail.durationMs }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.startedTime')">{{ recordDetail.startedTime }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.finishedTime')">{{ recordDetail.finishedTime }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.errorMessage')" :span="2">{{ recordDetail.errorMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.requestSnapshot')" :span="2">
          <pre class="snapshot-pre">{{ recordDetail.requestSnapshot }}</pre>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('extsync.responseSnapshot')" :span="2">
          <pre class="snapshot-pre">{{ recordDetail.responseSnapshot }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.ext-sync-page {
  padding: 0;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
}
.page-desc {
  margin: 0 0 8px 0;
  color: var(--yutong-text-secondary, #909399);
  font-size: 13px;
}
.content-tabs {
  margin-top: 8px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  font-size: 13px;
  color: var(--yutong-text-secondary, #909399);
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--yutong-text-primary, #303133);
}
.stat-sub {
  font-size: 12px;
  color: var(--yutong-text-secondary, #909399);
  margin-top: 4px;
}
.text-success {
  color: var(--el-color-success, #67c23a);
}
.text-danger {
  color: var(--el-color-danger, #f56c6c);
}
.text-warning {
  color: var(--el-color-warning, #e6a23c);
}
.alert-card {
  border-left: 4px solid var(--el-color-warning, #e6a23c);
}
.danger-card {
  border-left: 4px solid var(--el-color-danger, #f56c6c);
}
.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.capability-list {
  margin: 8px 0 0 0;
  padding-left: 20px;
  line-height: 1.8;
  font-size: 13px;
}
.snapshot-pre {
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
