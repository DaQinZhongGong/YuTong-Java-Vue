<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  pageInstances,
  getInstance,
  startInstance,
  terminateInstance,
  pageDefinitions,
  type WfProcessInstance,
  type WfInstanceStatus,
  type WfInstanceDetail,
  type WfProcessDefinition,
  type StartProcessRequest,
  type TerminateInstanceRequest,
} from '@/api/workflow'

const { t } = useI18n()

/**
 * 流程实例列表页。设计来源: 41-工作流与BPMN引擎设计。
 * GA2-44: 流程实例启动 + 详情查看 + 终止。
 */
const loading = ref(false)
const tableData = ref<WfProcessInstance[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选条件
const filterProcessKey = ref('')
const filterBizType = ref('')
const filterBizNo = ref('')
const filterStatus = ref<WfInstanceStatus | ''>('')

// 启动流程弹窗
const startDialogVisible = ref(false)
const publishedDefinitions = ref<WfProcessDefinition[]>([])
const startForm = ref<StartProcessRequest>({
  processKey: '',
  bizType: '',
  bizId: '',
  bizNo: '',
  variables: {},
  businessCallbackUrl: '',
})
const variablesJson = ref('{}')

// 详情弹窗
const detailDialogVisible = ref(false)
const detailData = ref<WfInstanceDetail | null>(null)

const STATUS_OPTIONS = [
  { label: t('workflow.instance.status.running'), value: 'RUNNING', tagType: undefined as 'success' | 'warning' | 'danger' | 'info' | undefined },
  { label: t('workflow.instance.status.completed'), value: 'COMPLETED', tagType: 'success' as const },
  { label: t('workflow.instance.status.terminated'), value: 'TERMINATED', tagType: 'danger' as const },
  { label: t('workflow.instance.status.suspended'), value: 'SUSPENDED', tagType: 'warning' as const },
]

function statusLabel(status: string): string {
  return STATUS_OPTIONS.find((s) => s.value === status)?.label || status
}

function statusTagType(status: string) {
  return STATUS_OPTIONS.find((s) => s.value === status)?.tagType || 'info'
}

function taskStatusTagType(status: string) {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info' | undefined> = {
    PENDING: 'warning',
    COMPLETED: 'success',
    REJECTED: 'danger',
    DELEGATED: 'info',
    TRANSFERRED: 'info',
    CANCELLED: 'info',
  }
  return map[status] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageInstances({
      page: currentPage.value,
      size: pageSize.value,
      processKey: filterProcessKey.value || undefined,
      bizType: filterBizType.value || undefined,
      bizNo: filterBizNo.value || undefined,
      instanceStatus: filterStatus.value || undefined,
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
  filterProcessKey.value = ''
  filterBizType.value = ''
  filterBizNo.value = ''
  filterStatus.value = ''
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

async function openStartDialog() {
  // 加载已发布的流程定义列表
  try {
    const res = await pageDefinitions({ page: 1, size: 100, status: 'PUBLISHED' })
    publishedDefinitions.value = res.records || []
  } catch {
    publishedDefinitions.value = []
  }
  startForm.value = {
    processKey: '',
    bizType: '',
    bizId: '',
    bizNo: '',
    variables: {},
    businessCallbackUrl: '',
  }
  variablesJson.value = '{}'
  startDialogVisible.value = true
}

async function handleStart() {
  if (!startForm.value.processKey) {
    ElMessage.warning(t('workflow.instance.msg.processRequired'))
    return
  }
  if (!startForm.value.bizType.trim()) {
    ElMessage.warning(t('workflow.instance.msg.bizTypeRequired'))
    return
  }
  if (!startForm.value.bizId.trim()) {
    ElMessage.warning(t('workflow.instance.msg.bizIdRequired'))
    return
  }
  // 解析 variables JSON
  try {
    startForm.value.variables = JSON.parse(variablesJson.value || '{}')
  } catch {
    ElMessage.error(t('workflow.instance.msg.variablesJsonError'))
    return
  }
  try {
    const instance = await startInstance(startForm.value)
    ElMessage.success(t('workflow.instance.msg.startSuccess', { id: instance.id }))
    startDialogVisible.value = false
    loadData()
  } catch {
    // 错误由拦截器处理
  }
}

async function viewDetail(row: WfProcessInstance) {
  try {
    detailData.value = await getInstance(row.id)
    detailDialogVisible.value = true
  } catch {
    // 错误由拦截器处理
  }
}

async function handleTerminate(row: WfProcessInstance) {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      t('workflow.instance.msg.terminateConfirm', { id: row.id }),
      t('workflow.instance.msg.terminateConfirmTitle'),
      {
        confirmButtonText: t('workflow.instance.msg.terminateConfirmButton'),
        cancelButtonText: t('workflow.instance.msg.cancelButton'),
        type: 'error',
        inputPlaceholder: t('workflow.instance.msg.terminateReasonPlaceholder'),
        inputValidator: (v: string) => v && v.trim().length > 0 ? true : t('workflow.instance.msg.terminateReasonRequired'),
      },
    )
    const req: TerminateInstanceRequest = { reason }
    await terminateInstance(row.id, req)
    ElMessage.success(t('workflow.instance.msg.terminateSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('workflow.instance.page.title') }}</h2>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('workflow.common.field.processKey')">
          <el-input v-model="filterProcessKey" :placeholder="$t('workflow.instance.placeholder.processKey')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.bizType')">
          <el-input v-model="filterBizType" :placeholder="$t('workflow.instance.placeholder.bizType')" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.bizNo')">
          <el-input v-model="filterBizNo" :placeholder="$t('workflow.instance.placeholder.bizNo')" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.status')">
          <el-select v-model="filterStatus" :placeholder="$t('workflow.common.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('workflow.common.action.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('workflow.common.action.reset') }}</el-button>
          <el-button type="success" @click="openStartDialog">{{ $t('workflow.instance.action.start') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 流程实例列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="id" :label="$t('workflow.instance.field.instanceId')" width="220" show-overflow-tooltip />
      <el-table-column prop="processKey" :label="$t('workflow.common.field.processKey')" width="180" show-overflow-tooltip />
      <el-table-column prop="bizType" :label="$t('workflow.common.field.bizType')" width="120" />
      <el-table-column prop="bizNo" :label="$t('workflow.common.field.bizNo')" width="140" show-overflow-tooltip />
      <el-table-column prop="starterId" :label="$t('workflow.common.field.starter')" width="140" show-overflow-tooltip />
      <el-table-column prop="currentNodeNames" :label="$t('workflow.common.field.currentNode')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="instanceStatus" :label="$t('workflow.common.field.status')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.instanceStatus)" size="small">{{ statusLabel(row.instanceStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" :label="$t('workflow.instance.field.startTime')" width="170" />
      <el-table-column prop="endTime" :label="$t('workflow.instance.field.endTime')" width="170" />
      <el-table-column :label="$t('workflow.common.field.action')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link type="primary" @click="viewDetail(row as WfProcessInstance)">{{ $t('workflow.common.action.detail') }}</el-button>
          <el-button size="small" link type="danger" :disabled="row.instanceStatus !== 'RUNNING'" @click="handleTerminate(row as WfProcessInstance)">{{ $t('workflow.common.action.terminate') }}</el-button>
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

    <!-- 启动流程弹窗 -->
    <el-dialog v-model="startDialogVisible" :title="$t('workflow.instance.dialog.startTitle')" width="640px">
      <el-form label-width="120px">
        <el-form-item :label="$t('workflow.instance.field.selectProcess')" required>
          <el-select v-model="startForm.processKey" :placeholder="$t('workflow.instance.placeholder.selectProcess')" filterable style="width: 100%">
            <el-option
              v-for="d in publishedDefinitions"
              :key="d.id"
              :label="`${d.processName} (v${d.versionNo})`"
              :value="d.processKey"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.bizType')" required>
          <el-input v-model="startForm.bizType" :placeholder="$t('workflow.instance.placeholder.bizType')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('workflow.instance.field.bizId')" required>
          <el-input v-model="startForm.bizId" :placeholder="$t('workflow.instance.placeholder.bizId')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.bizNo')">
          <el-input v-model="startForm.bizNo" :placeholder="$t('workflow.instance.placeholder.bizNoOptional')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('workflow.instance.field.variables')">
          <el-input v-model="variablesJson" type="textarea" :rows="6" :placeholder="$t('workflow.instance.placeholder.variables')" />
          <div class="form-tip">{{ $t('workflow.instance.tip.variables') }}</div>
        </el-form-item>
        <el-form-item :label="$t('workflow.instance.field.callbackUrl')">
          <el-input v-model="startForm.businessCallbackUrl" :placeholder="$t('workflow.instance.placeholder.callbackUrl')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">{{ $t('workflow.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleStart">{{ $t('workflow.instance.action.launch') }}</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" :title="$t('workflow.instance.dialog.detailTitle')" width="900px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('workflow.instance.field.instanceId')">{{ detailData.id }}</el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.instance.field.process')">{{ detailData.processDefinitionName || detailData.processKey }}</el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.common.field.bizType')">{{ detailData.bizType }}</el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.common.field.bizNo')">{{ detailData.bizNo || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.common.field.starter')">{{ detailData.starterId }}</el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.common.field.status')">
            <el-tag :type="statusTagType(detailData.instanceStatus)" size="small">{{ statusLabel(detailData.instanceStatus) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.common.field.currentNode')">{{ detailData.currentNodeNames || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.instance.field.startTime')">{{ detailData.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.instance.field.endTime')">{{ detailData.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('workflow.instance.field.terminateReason')" v-if="detailData.terminateReason">{{ detailData.terminateReason }}</el-descriptions-item>
        </el-descriptions>

        <h3 class="section-title">{{ $t('workflow.instance.section.taskHistory') }}</h3>
        <el-table :data="detailData.tasks" border size="small">
          <el-table-column prop="taskName" :label="$t('workflow.instance.field.taskName')" min-width="140" show-overflow-tooltip />
          <el-table-column prop="assigneeId" :label="$t('workflow.common.field.assignee')" width="140" show-overflow-tooltip />
          <el-table-column prop="taskStatus" :label="$t('workflow.common.field.status')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="taskStatusTagType(row.taskStatus)" size="small">{{ row.taskStatus }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="actualHandlerId" :label="$t('workflow.common.field.actualHandler')" width="140" show-overflow-tooltip />
          <el-table-column prop="opinion" :label="$t('workflow.common.field.opinion')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createTime" :label="$t('workflow.common.field.createdTime')" width="170" />
          <el-table-column prop="completeTime" :label="$t('workflow.instance.field.completeTime')" width="170" />
        </el-table>
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
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
.section-title {
  margin: 20px 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
</style>
