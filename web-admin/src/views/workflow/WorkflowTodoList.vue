<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  pageTasks,
  completeTask,
  rejectTask,
  delegateTask,
  transferTask,
  getWorkflowStats,
  type WfTaskExt,
  type WfTaskStatus,
  type WorkflowStats,
  type CompleteTaskRequest,
  type RejectTaskRequest,
  type DelegateTaskRequest,
} from '@/api/workflow'

const { t } = useI18n()

/**
 * 任务待办列表页。设计来源: 41-工作流与BPMN引擎设计。
 * GA2-44: 我的待办/已办 + 办理通过 + 驳回 + 委派 + 转办 + 运营监控统计。
 */
const loading = ref(false)
const tableData = ref<WfTaskExt[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 监控统计
const stats = ref<WorkflowStats | null>(null)

// 筛选条件
const filterProcessKey = ref('')
const filterBizType = ref('')
const filterBizNo = ref('')
const filterStatus = ref<WfTaskStatus | ''>('')
const filterMyTodoOnly = ref(false)
const filterMyDoneOnly = ref(false)

// 办理任务弹窗
const completeDialogVisible = ref(false)
const completeForm = ref<CompleteTaskRequest & { taskId?: string; taskName?: string }>({
  opinion: '',
  formData: {},
  variableUpdates: {},
})
const formDataJson = ref('{}')
const variableUpdatesJson = ref('{}')

// 驳回任务弹窗
const rejectDialogVisible = ref(false)
const rejectForm = ref<RejectTaskRequest & { taskId?: string; taskName?: string }>({
  opinion: '',
})

// 委派/转办弹窗
const delegateDialogVisible = ref(false)
const delegateForm = ref<DelegateTaskRequest & { taskId?: string; taskName?: string; delegateType?: 'DELEGATE' | 'TRANSFER' }>({
  delegateToUserId: '',
  opinion: '',
  delegateType: 'DELEGATE',
})
const delegateDialogTitle = ref(t('workflow.todo.msg.delegateTitle'))

const STATUS_OPTIONS = [
  { label: t('workflow.todo.status.pending'), value: 'PENDING', tagType: 'warning' as const },
  { label: t('workflow.todo.status.completed'), value: 'COMPLETED', tagType: 'success' as const },
  { label: t('workflow.todo.status.rejected'), value: 'REJECTED', tagType: 'danger' as const },
  { label: t('workflow.todo.status.delegated'), value: 'DELEGATED', tagType: 'info' as const },
  { label: t('workflow.todo.status.transferred'), value: 'TRANSFERRED', tagType: 'info' as const },
  { label: t('workflow.todo.status.cancelled'), value: 'CANCELLED', tagType: 'info' as const },
]

function statusLabel(status: string): string {
  return STATUS_OPTIONS.find((s) => s.value === status)?.label || status
}

function statusTagType(status: string) {
  return STATUS_OPTIONS.find((s) => s.value === status)?.tagType || 'info'
}

function isOverdue(row: WfTaskExt): boolean {
  if (!row.dueTime || row.taskStatus !== 'PENDING') return false
  return new Date(row.dueTime) < new Date()
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageTasks({
      page: currentPage.value,
      size: pageSize.value,
      processKey: filterProcessKey.value || undefined,
      bizType: filterBizType.value || undefined,
      bizNo: filterBizNo.value || undefined,
      taskStatus: filterStatus.value || undefined,
      myTodoOnly: filterMyTodoOnly.value || undefined,
      myDoneOnly: filterMyDoneOnly.value || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    stats.value = await getWorkflowStats()
  } catch {
    // 忽略
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filterProcessKey.value = ''
  filterBizType.value = ''
  filterBizNo.value = ''
  filterStatus.value = ''
  filterMyTodoOnly.value = false
  filterMyDoneOnly.value = false
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function openCompleteDialog(row: WfTaskExt) {
  completeForm.value = {
    taskId: row.id,
    taskName: row.taskName,
    opinion: '',
    formData: {},
    variableUpdates: {},
  }
  formDataJson.value = '{}'
  variableUpdatesJson.value = '{}'
  completeDialogVisible.value = true
}

async function handleComplete() {
  try {
    completeForm.value.formData = JSON.parse(formDataJson.value || '{}')
  } catch {
    ElMessage.error(t('workflow.todo.msg.formDataJsonError'))
    return
  }
  try {
    completeForm.value.variableUpdates = JSON.parse(variableUpdatesJson.value || '{}')
  } catch {
    ElMessage.error(t('workflow.todo.msg.variableUpdatesJsonError'))
    return
  }
  try {
    await completeTask(completeForm.value.taskId!, {
      opinion: completeForm.value.opinion,
      formData: completeForm.value.formData,
      variableUpdates: completeForm.value.variableUpdates,
    })
    ElMessage.success(t('workflow.todo.msg.completeSuccess'))
    completeDialogVisible.value = false
    loadData()
    loadStats()
  } catch {
    // 错误由拦截器处理
  }
}

function openRejectDialog(row: WfTaskExt) {
  rejectForm.value = {
    taskId: row.id,
    taskName: row.taskName,
    opinion: '',
  }
  rejectDialogVisible.value = true
}

async function handleReject() {
  if (!rejectForm.value.opinion.trim()) {
    ElMessage.warning(t('workflow.todo.msg.rejectReasonRequired'))
    return
  }
  try {
    await rejectTask(rejectForm.value.taskId!, { opinion: rejectForm.value.opinion })
    ElMessage.success(t('workflow.todo.msg.rejectSuccess'))
    rejectDialogVisible.value = false
    loadData()
    loadStats()
  } catch {
    // 错误由拦截器处理
  }
}

function openDelegateDialog(row: WfTaskExt, type: 'DELEGATE' | 'TRANSFER') {
  delegateForm.value = {
    taskId: row.id,
    taskName: row.taskName,
    delegateToUserId: '',
    opinion: '',
    delegateType: type,
  }
  delegateDialogTitle.value = type === 'TRANSFER' ? t('workflow.todo.msg.transferTitle') : t('workflow.todo.msg.delegateTitle')
  delegateDialogVisible.value = true
}

async function handleDelegate() {
  if (!delegateForm.value.delegateToUserId.trim()) {
    ElMessage.warning(t('workflow.todo.msg.delegateUserIdRequired'))
    return
  }
  try {
    const req: DelegateTaskRequest = {
      delegateToUserId: delegateForm.value.delegateToUserId,
      opinion: delegateForm.value.opinion,
      delegateType: delegateForm.value.delegateType,
    }
    if (delegateForm.value.delegateType === 'TRANSFER') {
      await transferTask(delegateForm.value.taskId!, req)
      ElMessage.success(t('workflow.todo.msg.transferSuccess'))
    } else {
      await delegateTask(delegateForm.value.taskId!, req)
      ElMessage.success(t('workflow.todo.msg.delegateSuccess'))
    }
    delegateDialogVisible.value = false
    loadData()
    loadStats()
  } catch {
    // 错误由拦截器处理
  }
}

onMounted(() => {
  loadData()
  loadStats()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('workflow.todo.page.title') }}</h2>

    <!-- 运营监控统计卡片 -->
    <el-row :gutter="16" class="stats-row" v-if="stats">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-card-warning">
          <div class="stat-label">{{ $t('workflow.todo.stat.myTodo') }}</div>
          <div class="stat-value">{{ stats.myPendingTaskCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-card-success">
          <div class="stat-label">{{ $t('workflow.todo.stat.myDone') }}</div>
          <div class="stat-value">{{ stats.myCompletedTaskCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-card-info">
          <div class="stat-label">{{ $t('workflow.todo.stat.allTodo') }}</div>
          <div class="stat-value">{{ stats.pendingTaskCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card stat-card-primary">
          <div class="stat-label">{{ $t('workflow.todo.stat.runningInstance') }}</div>
          <div class="stat-value">{{ stats.runningInstanceCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('workflow.common.field.processKey')">
          <el-input v-model="filterProcessKey" :placeholder="$t('workflow.todo.placeholder.processKey')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.bizType')">
          <el-input v-model="filterBizType" :placeholder="$t('workflow.todo.placeholder.bizType')" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.bizNo')">
          <el-input v-model="filterBizNo" :placeholder="$t('workflow.todo.placeholder.bizNo')" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.status')">
          <el-select v-model="filterStatus" :placeholder="$t('workflow.common.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="filterMyTodoOnly">{{ $t('workflow.todo.filter.myTodoOnly') }}</el-checkbox>
          <el-checkbox v-model="filterMyDoneOnly">{{ $t('workflow.todo.filter.myDoneOnly') }}</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('workflow.common.action.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('workflow.common.action.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="taskName" :label="$t('workflow.instance.field.taskName')" min-width="160" show-overflow-tooltip />
      <el-table-column prop="processKey" :label="$t('workflow.common.field.processKey')" width="180" show-overflow-tooltip />
      <el-table-column prop="bizType" :label="$t('workflow.common.field.bizType')" width="120" />
      <el-table-column prop="bizNo" :label="$t('workflow.common.field.bizNo')" width="140" show-overflow-tooltip />
      <el-table-column prop="assigneeId" :label="$t('workflow.common.field.assignee')" width="140" show-overflow-tooltip />
      <el-table-column prop="taskStatus" :label="$t('workflow.common.field.status')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.taskStatus)" size="small">{{ statusLabel(row.taskStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="dueTime" :label="$t('workflow.todo.field.dueTime')" width="170">
        <template #default="{ row }">
          <span :style="{ color: isOverdue(row as WfTaskExt) ? '#f56c6c' : '' }">
            {{ row.dueTime || '-' }}
          </span>
          <el-tag v-if="isOverdue(row as WfTaskExt)" type="danger" size="small" style="margin-left: 4px">{{ $t('workflow.todo.tag.overdue') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="actualHandlerId" :label="$t('workflow.common.field.actualHandler')" width="140" show-overflow-tooltip />
      <el-table-column prop="opinion" :label="$t('workflow.common.field.opinion')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="$t('workflow.common.field.action')" width="280" fixed="right">
        <template #default="{ row }">
          <template v-if="row.taskStatus === 'PENDING'">
            <el-button size="small" link type="success" @click="openCompleteDialog(row as WfTaskExt)">{{ $t('workflow.todo.action.handle') }}</el-button>
            <el-button size="small" link type="danger" @click="openRejectDialog(row as WfTaskExt)">{{ $t('workflow.todo.action.reject') }}</el-button>
            <el-button size="small" link @click="openDelegateDialog(row as WfTaskExt, 'DELEGATE')">{{ $t('workflow.todo.action.delegate') }}</el-button>
            <el-button size="small" link @click="openDelegateDialog(row as WfTaskExt, 'TRANSFER')">{{ $t('workflow.todo.action.transfer') }}</el-button>
          </template>
          <span v-else class="text-muted">{{ $t('workflow.todo.text.handled') }}</span>
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

    <!-- 办理任务弹窗 -->
    <el-dialog v-model="completeDialogVisible" :title="$t('workflow.todo.dialog.completeTitle')" width="640px">
      <el-form label-width="120px">
        <el-form-item :label="$t('workflow.instance.field.taskName')">
          <span>{{ completeForm.taskName }}</span>
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.opinion')">
          <el-input v-model="completeForm.opinion" type="textarea" :rows="3" :placeholder="$t('workflow.todo.placeholder.approvalOpinion')" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('workflow.todo.field.formData')">
          <el-input v-model="formDataJson" type="textarea" :rows="4" :placeholder="$t('workflow.todo.placeholder.formData')" />
        </el-form-item>
        <el-form-item :label="$t('workflow.todo.field.variableUpdates')">
          <el-input v-model="variableUpdatesJson" type="textarea" :rows="4" :placeholder="$t('workflow.todo.placeholder.variableUpdates')" />
          <div class="form-tip">{{ $t('workflow.todo.tip.variableUpdates') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialogVisible = false">{{ $t('workflow.common.action.cancel') }}</el-button>
        <el-button type="success" @click="handleComplete">{{ $t('workflow.todo.action.approve') }}</el-button>
      </template>
    </el-dialog>

    <!-- 驳回任务弹窗 -->
    <el-dialog v-model="rejectDialogVisible" :title="$t('workflow.todo.dialog.rejectTitle')" width="540px">
      <el-form label-width="120px">
        <el-form-item :label="$t('workflow.instance.field.taskName')">
          <span>{{ rejectForm.taskName }}</span>
        </el-form-item>
        <el-form-item :label="$t('workflow.todo.field.rejectReason')" required>
          <el-input v-model="rejectForm.opinion" type="textarea" :rows="4" :placeholder="$t('workflow.todo.placeholder.rejectReason')" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">{{ $t('workflow.common.action.cancel') }}</el-button>
        <el-button type="danger" @click="handleReject">{{ $t('workflow.todo.action.confirmReject') }}</el-button>
      </template>
    </el-dialog>

    <!-- 委派/转办任务弹窗 -->
    <el-dialog v-model="delegateDialogVisible" :title="delegateDialogTitle" width="540px">
      <el-form label-width="120px">
        <el-form-item :label="$t('workflow.instance.field.taskName')">
          <span>{{ delegateForm.taskName }}</span>
        </el-form-item>
        <el-form-item :label="$t('workflow.todo.field.targetUserId')" required>
          <el-input v-model="delegateForm.delegateToUserId" :placeholder="$t('workflow.todo.placeholder.targetUserId')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('workflow.todo.field.remark')">
          <el-input v-model="delegateForm.opinion" type="textarea" :rows="3" :placeholder="$t('workflow.todo.placeholder.delegateRemark')" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="delegateDialogVisible = false">{{ $t('workflow.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleDelegate">{{ delegateForm.delegateType === 'TRANSFER' ? $t('workflow.todo.action.confirmTransfer') : $t('workflow.todo.action.confirmDelegate') }}</el-button>
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
.stats-row {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}
.stat-card-warning .stat-value { color: #e6a23c; }
.stat-card-success .stat-value { color: #67c23a; }
.stat-card-info .stat-value { color: #909399; }
.stat-card-primary .stat-value { color: #409eff; }
.filter-card {
  margin-bottom: 16px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
.text-muted {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
