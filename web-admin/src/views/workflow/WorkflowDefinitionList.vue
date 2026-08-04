<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  pageDefinitions,
  createDefinition,
  updateDefinition,
  publishDefinition,
  deleteDefinition,
  type WfProcessDefinition,
  type WfDefinitionStatus,
  type SaveProcessDefinitionRequest,
} from '@/api/workflow'

const { t } = useI18n()

/**
 * 流程定义列表页。设计来源: 41-工作流与BPMN引擎设计。
 * GA2-44: 流程定义 CRUD + 发布 + BPMN XML 解析校验。
 */
const loading = ref(false)
const tableData = ref<WfProcessDefinition[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 筛选条件
const filterKeyword = ref('')
const filterStatus = ref<WfDefinitionStatus | ''>('')
const filterBizType = ref('')

// 编辑/新建弹窗
const editDialogVisible = ref(false)
const editForm = ref<SaveProcessDefinitionRequest & { id?: string }>({
  processKey: '',
  processName: '',
  categoryCode: '',
  bizType: '',
  bpmnXml: '',
  formPageCode: '',
  description: '',
  serviceTaskWhitelist: [],
})
const isEdit = ref(false)

// BPMN XML 预览弹窗
const bpmnDialogVisible = ref(false)
const bpmnPreviewXml = ref('')

const STATUS_OPTIONS = [
  { label: t('workflow.definition.status.draft'), value: 'DRAFT', tagType: 'info' as const },
  { label: t('workflow.definition.status.published'), value: 'PUBLISHED', tagType: 'success' as const },
  { label: t('workflow.definition.status.disabled'), value: 'DISABLED', tagType: 'danger' as const },
]

function statusLabel(status: string): string {
  return STATUS_OPTIONS.find((s) => s.value === status)?.label || status
}

function statusTagType(status: string) {
  return STATUS_OPTIONS.find((s) => s.value === status)?.tagType || 'info'
}

// 默认 BPMN 模板 (申请单审批: StartEvent -> 部门经理审批 -> 金额判断 -> 总经理审批/直接通过 -> EndEvent)
const DEFAULT_BPMN_TEMPLATE = computed(() => `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <bpmn:process id="process_key_here" name="${t('workflow.definition.bpmn.processName')}">
    <bpmn:startEvent id="start" name="${t('workflow.definition.bpmn.start')}" />
    <bpmn:userTask id="task_manager" name="${t('workflow.definition.bpmn.managerApproval')}" assignee="\${starter_manager}" />
    <bpmn:exclusiveGateway id="gw_amount" name="${t('workflow.definition.bpmn.amountCheck')}" />
    <bpmn:userTask id="task_boss" name="${t('workflow.definition.bpmn.bossApproval')}" assignee="\${boss}" />
    <bpmn:endEvent id="end" name="${t('workflow.definition.bpmn.end')}" />
    <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="task_manager" />
    <bpmn:sequenceFlow id="flow2" sourceRef="task_manager" targetRef="gw_amount" />
    <bpmn:sequenceFlow id="flow3" sourceRef="gw_amount" targetRef="task_boss">
      <bpmn:conditionExpression>\${totalAmount > 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="flow4" sourceRef="gw_amount" targetRef="end">
      <bpmn:conditionExpression>\${totalAmount &lt;= 10000}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="flow5" sourceRef="task_boss" targetRef="end" />
  </bpmn:process>
</bpmn:definitions>`)

async function loadData() {
  loading.value = true
  try {
    const res = await pageDefinitions({
      page: currentPage.value,
      size: pageSize.value,
      keyword: filterKeyword.value || undefined,
      status: filterStatus.value || undefined,
      bizType: filterBizType.value || undefined,
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
  filterBizType.value = ''
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function openCreateDialog() {
  isEdit.value = false
  editForm.value = {
    processKey: '',
    processName: '',
    categoryCode: '',
    bizType: '',
    bpmnXml: DEFAULT_BPMN_TEMPLATE.value,
    formPageCode: '',
    description: '',
    serviceTaskWhitelist: [],
  }
  editDialogVisible.value = true
}

function openEditDialog(row: WfProcessDefinition) {
  isEdit.value = true
  editForm.value = {
    id: row.id,
    processKey: row.processKey,
    processName: row.processName,
    categoryCode: row.categoryCode || '',
    bizType: row.bizType || '',
    bpmnXml: row.bpmnXml || '',
    formPageCode: row.formPageCode || '',
    description: row.description || '',
    serviceTaskWhitelist: row.serviceTaskWhitelist
      ? (() => { try { return JSON.parse(row.serviceTaskWhitelist) } catch { return [] } })()
      : [],
  }
  editDialogVisible.value = true
}

async function handleSave() {
  if (!editForm.value.processKey.trim()) {
    ElMessage.warning(t('workflow.definition.msg.processKeyRequired'))
    return
  }
  if (!editForm.value.processName.trim()) {
    ElMessage.warning(t('workflow.definition.msg.processNameRequired'))
    return
  }
  if (!editForm.value.bpmnXml.trim()) {
    ElMessage.warning(t('workflow.definition.msg.bpmnXmlRequired'))
    return
  }
  try {
    if (isEdit.value && editForm.value.id) {
      await updateDefinition(editForm.value.id, editForm.value)
      ElMessage.success(t('workflow.definition.msg.updateSuccess'))
    } else {
      await createDefinition(editForm.value)
      ElMessage.success(t('workflow.definition.msg.createSuccess'))
    }
    editDialogVisible.value = false
    loadData()
  } catch (e) {
    // 错误由 request 拦截器统一处理
  }
}

async function handlePublish(row: WfProcessDefinition) {
  try {
    await ElMessageBox.confirm(
      t('workflow.definition.msg.publishConfirm', { name: row.processName }),
      t('workflow.definition.msg.publishConfirmTitle'),
      { confirmButtonText: t('workflow.definition.msg.publishConfirmButton'), cancelButtonText: t('workflow.definition.msg.cancelButton'), type: 'warning' },
    )
    await publishDefinition(row.id)
    ElMessage.success(t('workflow.definition.msg.publishSuccess', { name: row.processName }))
    loadData()
  } catch {
    // 用户取消
  }
}

async function handleDelete(row: WfProcessDefinition) {
  try {
    await ElMessageBox.confirm(
      t('workflow.definition.msg.deleteConfirm', { name: row.processName }),
      t('workflow.definition.msg.deleteConfirmTitle'),
      { confirmButtonText: t('workflow.definition.msg.deleteConfirmButton'), cancelButtonText: t('workflow.definition.msg.cancelButton'), type: 'error' },
    )
    await deleteDefinition(row.id)
    ElMessage.success(t('workflow.definition.msg.deleteSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

function viewBpmnXml(row: WfProcessDefinition) {
  bpmnPreviewXml.value = row.bpmnXml || t('workflow.definition.msg.noBpmnXml')
  bpmnDialogVisible.value = true
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('workflow.definition.page.title') }}</h2>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('workflow.definition.field.keyword')">
          <el-input v-model="filterKeyword" :placeholder="$t('workflow.definition.placeholder.processName')" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.status')">
          <el-select v-model="filterStatus" :placeholder="$t('workflow.common.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.bizType')">
          <el-input v-model="filterBizType" :placeholder="$t('workflow.definition.placeholder.bizType')" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('workflow.common.action.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('workflow.common.action.reset') }}</el-button>
          <el-button type="success" @click="openCreateDialog">{{ $t('workflow.definition.action.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 流程定义列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="processKey" :label="$t('workflow.common.field.processKey')" width="180" show-overflow-tooltip />
      <el-table-column prop="processName" :label="$t('workflow.definition.field.processName')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="bizType" :label="$t('workflow.common.field.bizType')" width="140" />
      <el-table-column prop="versionNo" :label="$t('workflow.common.field.version')" width="80" align="center">
        <template #default="{ row }">v{{ row.versionNo }}</template>
      </el-table-column>
      <el-table-column prop="status" :label="$t('workflow.common.field.status')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="$t('workflow.common.field.description')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createdTime" :label="$t('workflow.common.field.createdTime')" width="170" />
      <el-table-column :label="$t('workflow.common.field.action')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" link @click="viewBpmnXml(row as WfProcessDefinition)">{{ $t('workflow.definition.action.viewXml') }}</el-button>
          <el-button size="small" link type="primary" :disabled="row.status === 'DISABLED'" @click="openEditDialog(row as WfProcessDefinition)">{{ $t('workflow.common.action.edit') }}</el-button>
          <el-button size="small" link type="success" :disabled="row.status !== 'DRAFT'" @click="handlePublish(row as WfProcessDefinition)">{{ $t('workflow.definition.action.publish') }}</el-button>
          <el-button size="small" link type="danger" :disabled="row.status === 'PUBLISHED'" @click="handleDelete(row as WfProcessDefinition)">{{ $t('workflow.common.action.delete') }}</el-button>
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

    <!-- 编辑/新建弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="isEdit ? $t('workflow.definition.dialog.editTitle') : $t('workflow.definition.dialog.createTitle')" width="800px">
      <el-form label-width="120px">
        <el-form-item :label="$t('workflow.common.field.processKey')" required>
          <el-input v-model="editForm.processKey" :placeholder="$t('workflow.definition.placeholder.processKey')" :disabled="isEdit" maxlength="64" show-word-limit />
          <div class="form-tip">{{ $t('workflow.definition.tip.processKey') }}</div>
        </el-form-item>
        <el-form-item :label="$t('workflow.definition.field.processName')" required>
          <el-input v-model="editForm.processName" :placeholder="$t('workflow.definition.placeholder.processNameExample')" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('workflow.definition.field.category')">
          <el-input v-model="editForm.categoryCode" :placeholder="$t('workflow.definition.placeholder.category')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.bizType')">
          <el-input v-model="editForm.bizType" :placeholder="$t('workflow.definition.placeholder.bizType')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('workflow.definition.field.formPageCode')">
          <el-input v-model="editForm.formPageCode" :placeholder="$t('workflow.definition.placeholder.formPageCode')" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('workflow.common.field.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="512" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('workflow.definition.field.serviceTaskWhitelist')">
          <el-select v-model="editForm.serviceTaskWhitelist" multiple filterable allow-create :placeholder="$t('workflow.definition.placeholder.serviceTask')" style="width: 100%">
            <el-option label="message/send" value="message/send" />
            <el-option label="todo/sync" value="todo/sync" />
            <el-option label="business-callback" value="business-callback" />
          </el-select>
        </el-form-item>
        <el-form-item label="BPMN XML" required>
          <el-input v-model="editForm.bpmnXml" type="textarea" :rows="12" :placeholder="$t('workflow.definition.placeholder.bpmnXml')" />
          <div class="form-tip">{{ $t('workflow.definition.tip.bpmnElements') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ $t('workflow.common.action.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ isEdit ? $t('workflow.common.action.save') : $t('workflow.common.action.create') }}</el-button>
      </template>
    </el-dialog>

    <!-- BPMN XML 预览弹窗 -->
    <el-dialog v-model="bpmnDialogVisible" :title="$t('workflow.definition.dialog.bpmnPreviewTitle')" width="800px">
      <pre class="bpmn-preview">{{ bpmnPreviewXml }}</pre>
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
.bpmn-preview {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 500px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
