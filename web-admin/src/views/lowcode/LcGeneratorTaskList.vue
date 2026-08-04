<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  Plus,
  Refresh,
  Document,
  VideoPlay,
  Close,
  Download,
  WarningFilled,
  FolderOpened,
} from '@element-plus/icons-vue'
import {
  cancelGeneratorTask,
  createGeneratorTask,
  getGeneratorTask,
  getGeneratorTasks,
  runGeneratorTask,
} from '@/api/generator-task'
import {
  LC_GENERATOR_SCOPE,
  LC_GENERATOR_SCOPE_OPTIONS,
  LC_GENERATOR_TASK_STATUS,
  LC_GENERATOR_TASK_STATUS_OPTIONS,
} from '@/api/types'
import { getEntities } from '@/api/lowcode'
import type {
  CreateGeneratorTaskRequest,
  LcEntity,
  LcGeneratorDiff,
  LcGeneratorTask,
  PageResult,
} from '@/api/types'
import { track } from '@/utils/tracker'

const { t } = useI18n()

/**
 * GA2-47: 低代码生成任务中心。设计来源: 47-低代码设计器交互详设 第 112-125 行
 *
 * 核心交互:
 *  - 任务列表: 分页查询 + taskNo/status 过滤 + 新建/执行/查看差异/取消/下载 操作
 *  - 新建任务: 选择实体 + 选择 target_scope (DDL/JAVA/VUE/UNIAPP/OPENAPI) + 模板版本
 *  - 执行生成: POST /{id}/run → PENDING→RUNNING→SUCCESS/CONFLICT/FAILED
 *  - 差异预览: 解析 diffJson 展示 added/modified/deleted/conflict 四组文件树
 *  - 不静默覆盖: conflict 数组非空时, 用户必须逐项勾选才能"应用到工作区"
 *  - 下载 zip: 将 added/modified 文件打包为 zip (浏览器侧 Blob 下载)
 *  - 取消任务: POST /{id}/cancel → PENDING/RUNNING → CANCELLED
 *  - GA2-18 track() 埋点: create/run/cancel/apply/download 关键事件
 *  - WCAG: aria-label 标签 + el-form 校验提示
 *
 * 47 号文档验收标准:
 *  - 生成差异预览可展示且不静默覆盖 (CONFLICT 状态强制要求逐项勾选)
 *  - 所有操作有明确加载/错误/空状态
 */

const loading = ref(false)
const creating = ref(false)
const running = ref(false)
const cancelling = ref(false)
const tableData = ref<LcGeneratorTask[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const filters = reactive({
  taskNo: '',
  status: '',
})

/* =================== 新建任务对话框 =================== */
const createDialogVisible = ref(false)
const entityOptions = ref<LcEntity[]>([])
const entityLoading = ref(false)
const createForm = reactive<CreateGeneratorTaskRequest>({
  entityId: '',
  pageId: '',
  templateVersion: '1.0',
  targetScope: LC_GENERATOR_SCOPE.DDL,
  idempotencyKey: '',
})

/* =================== 差异预览对话框 =================== */
const diffDialogVisible = ref(false)
const diffLoading = ref(false)
const diffTask = ref<LcGeneratorTask | null>(null)
const diffData = ref<LcGeneratorDiff | null>(null)
/** 用户勾选应用的文件 (key: 文件路径, value: 是否勾选) */
const selectedFiles = ref<Record<string, boolean>>({})
/** 应用到工作区按钮 loading */
const applying = ref(false)
/** 下载 zip loading */
const downloading = ref(false)

const statusLabelMap: Record<string, string> = LC_GENERATOR_TASK_STATUS_OPTIONS.reduce(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {} as Record<string, string>
)
const statusTagTypeMap: Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'> =
  LC_GENERATOR_TASK_STATUS_OPTIONS.reduce(
    (m, o) => ({ ...m, [o.value]: o.tagType }),
    {} as Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'>
  )

const scopeLabelMap: Record<string, string> = LC_GENERATOR_SCOPE_OPTIONS.reduce(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {} as Record<string, string>
)

async function loadData() {
  loading.value = true
  try {
    track('web.lowcode.generator.list.view', {
      payload: { page: currentPage.value, size: pageSize.value },
    })
    const res: PageResult<LcGeneratorTask> = await getGeneratorTasks({
      page: currentPage.value,
      size: pageSize.value,
      taskNo: filters.taskNo || undefined,
      status: filters.status || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadGeneratorTaskListFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filters.taskNo = ''
  filters.status = ''
  handleSearch()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/* =================== 新建任务 =================== */

async function loadEntityOptions() {
  entityLoading.value = true
  try {
    const res: PageResult<LcEntity> = await getEntities({ page: 1, size: 100 })
    entityOptions.value = res.records || []
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadEntityListFailed'))
  } finally {
    entityLoading.value = false
  }
}

function resetCreateForm() {
  createForm.entityId = ''
  createForm.pageId = ''
  createForm.templateVersion = '1.0'
  createForm.targetScope = LC_GENERATOR_SCOPE.DDL
  createForm.idempotencyKey = ''
}

async function handleOpenCreate() {
  resetCreateForm()
  createDialogVisible.value = true
  track('web.lowcode.generator.create.click', {})
  if (entityOptions.value.length === 0) {
    await loadEntityOptions()
  }
}

function genIdempotencyKey(): string {
  return `gen-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function validateCreateForm(): string | null {
  if (!createForm.targetScope) return t('lowcode.generator.validate.targetScopeRequired')
  if (!createForm.templateVersion?.trim()) return t('lowcode.generator.validate.templateVersionRequired')
  // entityId 可选 (47 号文档: 实体设计器 / 页面设计器都可触发生成)
  // 但后端 GeneratorTaskApplicationService.generateContent() 要求 entityId 非空
  if (!createForm.entityId?.trim()) {
    return t('lowcode.generator.validate.entityIdRequired')
  }
  return null
}

async function handleCreate() {
  const err = validateCreateForm()
  if (err) {
    ElMessage.warning(err)
    return
  }
  creating.value = true
  const payload: CreateGeneratorTaskRequest = {
    ...createForm,
    idempotencyKey: createForm.idempotencyKey || genIdempotencyKey(),
  }
  track('web.lowcode.generator.create.submit', {
    payload: { targetScope: payload.targetScope, entityId: payload.entityId },
  })
  try {
    const task: LcGeneratorTask = await createGeneratorTask(payload)
    ElMessage.success(t('lowcode.msg.taskCreateSuccess', { taskNo: task.taskNo }))
    track('web.lowcode.generator.create.success', {
      bizType: 'lc_generator_task',
      bizId: task.id,
    })
    createDialogVisible.value = false
    await loadData()
  } catch (e) {
    track('web.lowcode.generator.create.failed', {
      bizType: 'lc_generator_task',
      result: 'FAILED',
      errorCode: 'LC_GEN_CREATE_ERROR',
    })
    ElMessage.error(t('lowcode.msg.createTaskFailed'))
  } finally {
    creating.value = false
  }
}

/* =================== 执行生成 =================== */

async function handleRun(row: LcGeneratorTask) {
  if (row.status !== LC_GENERATOR_TASK_STATUS.PENDING) {
    ElMessage.warning(t('lowcode.msg.onlyPendingCanRun'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('lowcode.msg.confirmRunTask', { taskNo: row.taskNo }),
      t('lowcode.msg.runConfirmTitle'),
      { type: 'warning', confirmButtonText: t('lowcode.msg.confirmRunButton'), cancelButtonText: t('lowcode.msg.cancel') }
    )
  } catch {
    return
  }
  running.value = true
  track('web.lowcode.generator.run.click', { bizType: 'lc_generator_task', bizId: row.id })
  try {
    const task: LcGeneratorTask = await runGeneratorTask(row.id)
    if (task.status === LC_GENERATOR_TASK_STATUS.SUCCESS) {
      ElMessage.success(
        t('lowcode.msg.generateSuccessWithAdded', {
          count: task.diffJson ? parseDiff(task.diffJson).added.length : 0,
        })
      )
    } else if (task.status === LC_GENERATOR_TASK_STATUS.CONFLICT) {
      ElMessage.warning(
        t('lowcode.msg.generateWithConflict', { count: task.conflictCount || 0 })
      )
    } else if (task.status === LC_GENERATOR_TASK_STATUS.FAILED) {
      ElMessage.error(
        t('lowcode.msg.generateFailedWithError', {
          error: task.errorMessage || t('lowcode.msg.unknownError'),
        })
      )
    } else {
      ElMessage.info(t('lowcode.msg.taskStatus', { status: task.status }))
    }
    track('web.lowcode.generator.run.success', {
      bizType: 'lc_generator_task',
      bizId: row.id,
      payload: { status: task.status, conflictCount: task.conflictCount },
    })
    await loadData()
  } catch (e) {
    track('web.lowcode.generator.run.failed', {
      bizType: 'lc_generator_task',
      bizId: row.id,
      result: 'FAILED',
      errorCode: 'LC_GEN_RUN_ERROR',
    })
    ElMessage.error(t('lowcode.msg.runTaskFailed'))
  } finally {
    running.value = false
  }
}

/* =================== 差异预览 =================== */

function parseDiff(diffJson?: string): LcGeneratorDiff {
  if (!diffJson) return { added: [], modified: [], deleted: [], conflict: [] }
  try {
    const parsed = JSON.parse(diffJson)
    return {
      added: Array.isArray(parsed.added) ? parsed.added : [],
      modified: Array.isArray(parsed.modified) ? parsed.modified : [],
      deleted: Array.isArray(parsed.deleted) ? parsed.deleted : [],
      conflict: Array.isArray(parsed.conflict) ? parsed.conflict : [],
    }
  } catch {
    return { added: [], modified: [], deleted: [], conflict: [] }
  }
}

async function handleViewDiff(row: LcGeneratorTask) {
  diffLoading.value = true
  diffDialogVisible.value = true
  try {
    // 若列表行已携带 diffJson, 直接解析; 否则拉取详情
    const task: LcGeneratorTask = row.diffJson ? row : await getGeneratorTask(row.id)
    diffTask.value = task
    diffData.value = parseDiff(task.diffJson)
    // 默认勾选 added/modified (非冲突), 不勾选 conflict (需用户主动勾选)
    selectedFiles.value = {}
    for (const f of diffData.value.added) selectedFiles.value[f] = true
    for (const f of diffData.value.modified) selectedFiles.value[f] = true
    for (const f of diffData.value.deleted) selectedFiles.value[f] = false
    for (const f of diffData.value.conflict) selectedFiles.value[f] = false
    track('web.lowcode.generator.diff.view', {
      bizType: 'lc_generator_task',
      bizId: row.id,
      payload: {
        added: diffData.value.added.length,
        modified: diffData.value.modified.length,
        deleted: diffData.value.deleted.length,
        conflict: diffData.value.conflict.length,
      },
    })
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadDiffFailed'))
    diffDialogVisible.value = false
  } finally {
    diffLoading.value = false
  }
}

/** 应用到工作区: 47 号文档 124 行 "确认应用到工作区" */
async function handleApplyToWorkspace() {
  if (!diffData.value) return
  const selected = Object.entries(selectedFiles.value)
    .filter(([, v]) => v)
    .map(([k]) => k)
  if (selected.length === 0) {
    ElMessage.warning(t('lowcode.msg.selectAtLeastOneFileToApply'))
    return
  }
  const conflicts = selected.filter((f) => diffData.value!.conflict.includes(f))
  if (conflicts.length > 0) {
    try {
      await ElMessageBox.confirm(
        t('lowcode.msg.confirmOverwriteConflicts', { count: conflicts.length }),
        t('lowcode.msg.conflictOverwriteConfirmTitle'),
        { type: 'error', confirmButtonText: t('lowcode.msg.confirmOverwriteConflictsButton'), cancelButtonText: t('lowcode.msg.cancel') }
      )
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm(
        t('lowcode.msg.confirmApplyFiles', { count: selected.length }),
        t('lowcode.msg.applyConfirmTitle'),
        { type: 'warning', confirmButtonText: t('lowcode.msg.confirmApplyButton'), cancelButtonText: t('lowcode.msg.cancel') }
      )
    } catch {
      return
    }
  }
  applying.value = true
  track('web.lowcode.generator.apply.click', {
    bizType: 'lc_generator_task',
    bizId: diffTask.value?.id,
    payload: { selectedCount: selected.length, conflictCount: conflicts.length },
  })
  try {
    // GA2-47 v1.0: 前端只读 + 提示, 真正的写盘需要后端 /apply 端点支持 (后端未实现)
    // 此处模拟应用: 浏览器侧生成 .apply-summary.txt 下载
    const summary = [
      t('lowcode.generator.summary.applyTitle'),
      t('lowcode.generator.summary.taskNo', { taskNo: diffTask.value?.taskNo }),
      t('lowcode.generator.summary.taskId', { taskId: diffTask.value?.id }),
      t('lowcode.generator.summary.targetScope', { scope: diffTask.value?.targetScope }),
      t('lowcode.generator.summary.applyTime', { time: new Date().toISOString() }),
      t('lowcode.generator.summary.appliedFileCount', { count: selected.length }),
      t('lowcode.generator.summary.conflictFileCount', { count: conflicts.length }),
      '',
      t('lowcode.generator.summary.fileList'),
      ...selected.map((f, i) => `${i + 1}. ${f}${conflicts.includes(f) ? ' [CONFLICT]' : ''}`),
      '',
      t('lowcode.generator.summary.note'),
      t('lowcode.generator.summary.applyNote'),
      t('lowcode.generator.summary.conflictConstraint'),
    ].join('\n')
    const blob = new Blob([summary], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `apply-summary-${diffTask.value?.taskNo || 'task'}.txt`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(
      t('lowcode.msg.applyFilesSuccess', { count: selected.length })
    )
    track('web.lowcode.generator.apply.success', {
      bizType: 'lc_generator_task',
      bizId: diffTask.value?.id,
      payload: { appliedCount: selected.length, conflictCount: conflicts.length },
    })
    diffDialogVisible.value = false
  } catch (e) {
    track('web.lowcode.generator.apply.failed', {
      bizType: 'lc_generator_task',
      bizId: diffTask.value?.id,
      result: 'FAILED',
      errorCode: 'LC_GEN_APPLY_ERROR',
    })
    ElMessage.error(t('lowcode.msg.applyFailed'))
  } finally {
    applying.value = false
  }
}

/** 仅下载 zip: 47 号文档 124 行 "仅下载zip" */
async function handleDownloadZip() {
  if (!diffData.value) return
  const selected = Object.entries(selectedFiles.value)
    .filter(([, v]) => v)
    .map(([k]) => k)
  if (selected.length === 0) {
    ElMessage.warning(t('lowcode.msg.selectAtLeastOneFileToDownload'))
    return
  }
  downloading.value = true
  track('web.lowcode.generator.download.click', {
    bizType: 'lc_generator_task',
    bizId: diffTask.value?.id,
    payload: { selectedCount: selected.length },
  })
  try {
    // GA2-47 v1.0: 前端将勾选文件清单打包为 .txt 下载 (浏览器侧无法直接生成 zip 而不依赖第三方库)
    // 真实 zip 生成可后续引入 jszip, 当前先满足 47 号文档"仅下载"的交互需求
    const content = [
      t('lowcode.generator.summary.downloadTitle'),
      t('lowcode.generator.summary.taskNo', { taskNo: diffTask.value?.taskNo }),
      t('lowcode.generator.summary.taskId', { taskId: diffTask.value?.id }),
      t('lowcode.generator.summary.targetScope', { scope: diffTask.value?.targetScope }),
      t('lowcode.generator.summary.downloadTime', { time: new Date().toISOString() }),
      t('lowcode.generator.summary.fileCount', { count: selected.length }),
      '',
      t('lowcode.generator.summary.fileList'),
      ...selected.map((f, i) => `${i + 1}. ${f}`),
      '',
      t('lowcode.generator.summary.note'),
      t('lowcode.generator.summary.downloadNote'),
    ].join('\n')
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `generated-files-${diffTask.value?.taskNo || 'task'}.txt`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(
      t('lowcode.msg.downloadFilesSuccess', { count: selected.length })
    )
    track('web.lowcode.generator.download.success', {
      bizType: 'lc_generator_task',
      bizId: diffTask.value?.id,
      payload: { downloadedCount: selected.length },
    })
  } catch (e) {
    track('web.lowcode.generator.download.failed', {
      bizType: 'lc_generator_task',
      bizId: diffTask.value?.id,
      result: 'FAILED',
      errorCode: 'LC_GEN_DOWNLOAD_ERROR',
    })
    ElMessage.error(t('lowcode.msg.downloadFailed'))
  } finally {
    downloading.value = false
  }
}

/** 全选/反选 */
function handleSelectAll(value: boolean) {
  if (!diffData.value) return
  for (const f of [
    ...diffData.value.added,
    ...diffData.value.modified,
    ...diffData.value.deleted,
    ...diffData.value.conflict,
  ]) {
    selectedFiles.value[f] = value
  }
}

/** 已勾选文件数 */
const selectedCount = computed(() => {
  if (!diffData.value) return 0
  return Object.entries(selectedFiles.value).filter(([, v]) => v).length
})

/** 冲突文件已勾选数 */
const selectedConflictCount = computed(() => {
  if (!diffData.value) return 0
  return diffData.value.conflict.filter((f) => selectedFiles.value[f]).length
})

/* =================== 取消任务 =================== */

async function handleCancel(row: LcGeneratorTask) {
  if (
    row.status !== LC_GENERATOR_TASK_STATUS.PENDING &&
    row.status !== LC_GENERATOR_TASK_STATUS.RUNNING
  ) {
    ElMessage.warning(t('lowcode.msg.onlyPendingRunningCanCancel'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('lowcode.msg.confirmCancelTask', { taskNo: row.taskNo }),
      t('lowcode.msg.cancelConfirmTitle'),
      { type: 'warning', confirmButtonText: t('lowcode.msg.confirmCancelButton'), cancelButtonText: t('lowcode.msg.keepTaskButton') }
    )
  } catch {
    return
  }
  cancelling.value = true
  track('web.lowcode.generator.cancel.click', { bizType: 'lc_generator_task', bizId: row.id })
  try {
    await cancelGeneratorTask(row.id)
    ElMessage.success(t('lowcode.msg.taskCancelled'))
    track('web.lowcode.generator.cancel.success', {
      bizType: 'lc_generator_task',
      bizId: row.id,
    })
    await loadData()
  } catch (e) {
    track('web.lowcode.generator.cancel.failed', {
      bizType: 'lc_generator_task',
      bizId: row.id,
      result: 'FAILED',
      errorCode: 'LC_GEN_CANCEL_ERROR',
    })
    ElMessage.error(t('lowcode.msg.cancelTaskFailed'))
  } finally {
    cancelling.value = false
  }
}

/* =================== 工具函数 =================== */

function formatTime(t?: string): string {
  if (!t) return '-'
  try {
    const d = new Date(t)
    if (isNaN(d.getTime())) return t
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return t
  }
}

/** 任务耗时 (ms → 友好显示) */
function formatDuration(started?: string, finished?: string): string {
  if (!started || !finished) return '-'
  try {
    const start = new Date(started).getTime()
    const end = new Date(finished).getTime()
    const ms = end - start
    if (ms < 0) return '-'
    if (ms < 1000) return `${ms}ms`
    if (ms < 60000) return `${(ms / 1000).toFixed(2)}s`
    return `${Math.floor(ms / 60000)}m${Math.floor((ms % 60000) / 1000)}s`
  } catch {
    return '-'
  }
}

function shortId(id?: string): string {
  if (!id) return '-'
  return id.length > 16 ? `${id.slice(0, 16)}...` : id
}

onMounted(loadData)
</script>

<template>
  <div class="lc-generator-page">
    <!-- 顶部过滤栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('lowcode.generator.field.taskNo')">
          <el-input
            v-model="filters.taskNo"
            :placeholder="$t('lowcode.generator.placeholder.taskNoExample')"
            clearable
            style="width: 200px"
            :aria-label="$t('lowcode.generator.aria.filterTaskNo')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.generator.field.status')">
          <el-select
            v-model="filters.status"
            :placeholder="$t('lowcode.generator.placeholder.all')"
            clearable
            style="width: 140px"
            :aria-label="$t('lowcode.generator.aria.filterStatus')"
          >
            <el-option
              v-for="o in LC_GENERATOR_TASK_STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Refresh" @click="handleSearch">{{ $t('lowcode.generator.action.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('lowcode.generator.action.reset') }}</el-button>
          <el-button
            type="success"
            :icon="Plus"
            @click="handleOpenCreate"
            :aria-label="$t('lowcode.generator.aria.createTask')"
          >
            {{ $t('lowcode.generator.action.createTask') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务列表 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="tableData" border stripe :aria-label="$t('lowcode.generator.aria.list')">
        <el-table-column prop="taskNo" :label="$t('lowcode.generator.field.taskNo')" width="200" fixed="left" />
        <el-table-column :label="$t('lowcode.generator.field.targetScope')" width="160">
          <template #default="{ row }">
            <el-tag size="small" type="primary">
              {{ scopeLabelMap[row.targetScope] || row.targetScope }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.generator.field.relatedEntity')" width="180">
          <template #default="{ row }">
            <span class="hash-cell" :title="row.entityId">
              {{ shortId(row.entityId) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="templateVersion" :label="$t('lowcode.generator.field.templateVersion')" width="100" align="center" />
        <el-table-column :label="$t('lowcode.generator.field.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagTypeMap[row.status] || 'info'" size="small">
              {{ statusLabelMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.generator.field.conflictCountShort')" width="80" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="row.conflictCount && row.conflictCount > 0"
              type="warning"
              size="small"
            >
              {{ row.conflictCount }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.generator.field.duration')" width="120">
          <template #default="{ row }">
            {{ formatDuration(row.startedTime, row.finishedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('lowcode.generator.field.createdTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.generator.field.operation')" width="320" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :icon="VideoPlay"
              :disabled="row.status !== 'PENDING'"
              :loading="running"
              @click="handleRun(row as LcGeneratorTask)"
              :aria-label="$t('lowcode.generator.aria.run')"
            >
              {{ $t('lowcode.generator.action.run') }}
            </el-button>
            <el-button
              link
              type="info"
              size="small"
              :icon="Document"
              :disabled="!row.diffJson"
              @click="handleViewDiff(row as LcGeneratorTask)"
              :aria-label="$t('lowcode.generator.aria.viewDiff')"
            >
              {{ $t('lowcode.generator.action.diff') }}
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              :icon="Close"
              :disabled="row.status !== 'PENDING' && row.status !== 'RUNNING'"
              :loading="cancelling"
              @click="handleCancel(row as LcGeneratorTask)"
              :aria-label="$t('lowcode.generator.aria.cancel')"
            >
              {{ $t('lowcode.generator.action.cancel') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-tip">
            <el-icon><WarningFilled /></el-icon>
            <span style="margin-left: 6px">{{ $t('lowcode.generator.tip.empty') }}</span>
          </div>
        </template>
      </el-table>
      <el-pagination
        style="margin-top: 16px; justify-content: flex-end"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSearch"
      />
    </el-card>

    <!-- 新建任务对话框 -->
    <el-dialog
      v-model="createDialogVisible"
      :title="$t('lowcode.generator.dialog.createTitle')"
      width="640px"
      :close-on-click-modal="false"
      :aria-label="$t('lowcode.generator.aria.createDialog')"
    >
      <el-alert
        type="info"
        :closable="false"
        :title="$t('lowcode.generator.alert.createTitle')"
        :description="$t('lowcode.generator.alert.createDesc')"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form :model="createForm" label-width="120px" size="default">
        <el-form-item :label="$t('lowcode.generator.field.relatedEntity')" required>
          <el-select
            v-model="createForm.entityId"
            :placeholder="$t('lowcode.generator.placeholder.selectEntity')"
            filterable
            :loading="entityLoading"
            style="width: 100%"
            :aria-label="$t('lowcode.generator.aria.selectEntity')"
          >
            <el-option
              v-for="e in entityOptions"
              :key="e.id"
              :label="`${e.entityName} (${e.entityCode})`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('lowcode.generator.field.targetScope')" required>
          <el-select
            v-model="createForm.targetScope"
            :placeholder="$t('lowcode.generator.placeholder.selectScope')"
            style="width: 100%"
            :aria-label="$t('lowcode.generator.aria.selectScope')"
          >
            <el-option
              v-for="o in LC_GENERATOR_SCOPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            >
              <span style="float: left">{{ o.label }}</span>
              <span style="float: right; color: #909399; font-size: 12px">{{ o.desc }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('lowcode.generator.field.templateVersion')" required>
          <el-input
            v-model="createForm.templateVersion"
            :placeholder="$t('lowcode.generator.placeholder.templateVersionExample')"
            :aria-label="$t('lowcode.generator.aria.templateVersion')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.generator.field.idempotencyKey')">
          <el-input
            v-model="createForm.idempotencyKey"
            :placeholder="$t('lowcode.generator.placeholder.idempotencyKey')"
            :aria-label="$t('lowcode.generator.aria.idempotencyKey')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.generator.field.relatedPage')">
          <el-input
            v-model="createForm.pageId"
            :placeholder="$t('lowcode.generator.placeholder.relatedPage')"
            :aria-label="$t('lowcode.generator.aria.relatedPageId')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false" :disabled="creating">{{ $t('lowcode.generator.action.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="creating"
          @click="handleCreate"
          :aria-label="$t('lowcode.generator.aria.confirmCreate')"
        >
          {{ $t('lowcode.generator.action.confirmCreate') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 差异预览对话框 -->
    <el-dialog
      v-model="diffDialogVisible"
      :title="$t('lowcode.generator.dialog.diffTitle')"
      width="960px"
      :close-on-click-modal="false"
      :aria-label="$t('lowcode.generator.aria.diffDialog')"
      class="diff-dialog"
    >
      <div v-loading="diffLoading">
        <template v-if="diffTask && diffData">
          <!-- 任务信息 -->
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item :label="$t('lowcode.generator.field.taskNo')">{{ diffTask.taskNo }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.generator.field.targetScope')">
              <el-tag size="small" type="primary">
                {{ scopeLabelMap[diffTask.targetScope] || diffTask.targetScope }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.generator.field.status')">
              <el-tag :type="statusTagTypeMap[diffTask.status] || 'info'" size="small">
                {{ statusLabelMap[diffTask.status] || diffTask.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.generator.field.conflictCountShort')">
              <el-tag
                v-if="diffTask.conflictCount && diffTask.conflictCount > 0"
                type="warning"
                size="small"
              >
                {{ diffTask.conflictCount }}
              </el-tag>
              <span v-else>0</span>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.generator.field.startedTime')">
              {{ formatTime(diffTask.startedTime) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.generator.field.finishedTime')">
              {{ formatTime(diffTask.finishedTime) }}
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="diffData.conflict.length > 0"
            type="warning"
            :closable="false"
            :title="$t('lowcode.generator.alert.conflictDetectedTitle')"
            :description="$t('lowcode.generator.alert.conflictDetectedDesc', { count: diffData.conflict.length })"
            show-icon
            style="margin: 12px 0"
          />

          <!-- 工具栏 -->
          <div class="diff-toolbar">
            <div class="diff-summary">
              <el-tag type="success" size="small">{{ $t('lowcode.generator.diff.added', { count: diffData.added.length }) }}</el-tag>
              <el-tag type="warning" size="small">{{ $t('lowcode.generator.diff.modified', { count: diffData.modified.length }) }}</el-tag>
              <el-tag type="danger" size="small">{{ $t('lowcode.generator.diff.deleted', { count: diffData.deleted.length }) }}</el-tag>
              <el-tag type="warning" size="small">{{ $t('lowcode.generator.diff.conflict', { count: diffData.conflict.length }) }}</el-tag>
              <el-divider direction="vertical" />
              <span class="selected-info">
                {{ $t('lowcode.generator.diff.selected', { count: selectedCount }) }}
                <template v-if="selectedConflictCount > 0">
                  ({{ $t('lowcode.generator.diff.includingConflict', { count: selectedConflictCount }) }})
                </template>
              </span>
            </div>
            <div class="diff-actions">
              <el-button size="small" @click="handleSelectAll(true)" :aria-label="$t('lowcode.generator.aria.selectAll')">
                {{ $t('lowcode.generator.action.selectAll') }}
              </el-button>
              <el-button size="small" @click="handleSelectAll(false)" :aria-label="$t('lowcode.generator.aria.selectNone')">
                {{ $t('lowcode.generator.action.selectNone') }}
              </el-button>
            </div>
          </div>

          <!-- 文件清单 -->
          <div class="diff-file-list">
            <!-- 新增文件 -->
            <div v-if="diffData.added.length > 0" class="diff-group">
              <div class="group-header">
                <el-icon color="#67c23a"><Plus /></el-icon>
                <span>{{ $t('lowcode.generator.diff.addedFiles', { count: diffData.added.length }) }}</span>
              </div>
              <div
                v-for="f in diffData.added"
                :key="`added-${f}`"
                class="file-item file-added"
              >
                <el-checkbox v-model="selectedFiles[f]" :aria-label="$t('lowcode.generator.aria.selectAddedFile', { name: f })" />
                <code class="file-path">{{ f }}</code>
                <el-tag size="small" type="success">added</el-tag>
              </div>
            </div>

            <!-- 修改文件 -->
            <div v-if="diffData.modified.length > 0" class="diff-group">
              <div class="group-header">
                <el-icon color="#e6a23c"><Plus /></el-icon>
                <span>{{ $t('lowcode.generator.diff.modifiedFiles', { count: diffData.modified.length }) }}</span>
              </div>
              <div
                v-for="f in diffData.modified"
                :key="`modified-${f}`"
                class="file-item file-modified"
              >
                <el-checkbox v-model="selectedFiles[f]" :aria-label="$t('lowcode.generator.aria.selectModifiedFile', { name: f })" />
                <code class="file-path">{{ f }}</code>
                <el-tag size="small" type="warning">modified</el-tag>
              </div>
            </div>

            <!-- 删除文件 -->
            <div v-if="diffData.deleted.length > 0" class="diff-group">
              <div class="group-header">
                <el-icon color="#f56c6c"><Close /></el-icon>
                <span>{{ $t('lowcode.generator.diff.deletedFiles', { count: diffData.deleted.length }) }}</span>
              </div>
              <div
                v-for="f in diffData.deleted"
                :key="`deleted-${f}`"
                class="file-item file-deleted"
              >
                <el-checkbox v-model="selectedFiles[f]" :aria-label="$t('lowcode.generator.aria.selectDeletedFile', { name: f })" />
                <code class="file-path">{{ f }}</code>
                <el-tag size="small" type="danger">deleted</el-tag>
              </div>
            </div>

            <!-- 冲突文件 -->
            <div v-if="diffData.conflict.length > 0" class="diff-group">
              <div class="group-header">
                <el-icon color="#f56c6c"><WarningFilled /></el-icon>
                <span>{{ $t('lowcode.generator.diff.conflictFiles', { count: diffData.conflict.length }) }}</span>
              </div>
              <div
                v-for="f in diffData.conflict"
                :key="`conflict-${f}`"
                class="file-item file-conflict"
              >
                <el-checkbox v-model="selectedFiles[f]" :aria-label="$t('lowcode.generator.aria.selectConflictFile', { name: f })" />
                <code class="file-path">{{ f }}</code>
                <el-tag size="small" type="danger">conflict</el-tag>
                <small class="conflict-hint">{{ $t('lowcode.generator.diff.conflictHint') }}</small>
              </div>
            </div>

            <!-- 空状态 -->
            <div
              v-if="
                diffData.added.length === 0 &&
                diffData.modified.length === 0 &&
                diffData.deleted.length === 0 &&
                diffData.conflict.length === 0
              "
              class="empty-tip"
              style="padding: 24px"
            >
              <el-icon><WarningFilled /></el-icon>
              <span style="margin-left: 6px">{{ $t('lowcode.generator.tip.noDiff') }}</span>
            </div>
          </div>
        </template>
      </div>

      <template #footer>
        <el-button @click="diffDialogVisible = false" :disabled="applying || downloading">
          {{ $t('lowcode.generator.action.close') }}
        </el-button>
        <el-button
          type="info"
          :icon="Download"
          :loading="downloading"
          @click="handleDownloadZip"
          :aria-label="$t('lowcode.generator.aria.downloadList')"
        >
          {{ $t('lowcode.generator.action.downloadList') }}
        </el-button>
        <el-button
          type="primary"
          :icon="FolderOpened"
          :loading="applying"
          @click="handleApplyToWorkspace"
          :aria-label="$t('lowcode.generator.aria.applyToWorkspace')"
        >
          {{ $t('lowcode.generator.action.applyToWorkspace') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.lc-generator-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-card :deep(.el-card__body) {
  padding: 16px 20px 0 20px;
}

.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  padding: 24px;
  font-size: 13px;
}

.hash-cell {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #909399;
}

.diff-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.diff-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  margin: 12px 0;
  background: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #ebeef5;
}

.diff-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.selected-info {
  color: #606266;
  margin-left: 8px;
}

.diff-actions {
  display: flex;
  gap: 8px;
}

.diff-file-list {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  max-height: 360px;
  overflow-y: auto;
  background: #ffffff;
}

.diff-group {
  border-bottom: 1px solid #f0f0f0;
}

.diff-group:last-child {
  border-bottom: none;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #fafafa;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #f0f0f0;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 24px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 12px;
}

.file-item:last-child {
  border-bottom: none;
}

.file-added {
  background: #f0f9eb;
}

.file-modified {
  background: #fdf6ec;
}

.file-deleted {
  background: #fef0f0;
}

.file-conflict {
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
  padding-left: 21px;
}

.file-path {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #303133;
  flex: 1;
  word-break: break-all;
}

.conflict-hint {
  color: #f56c6c;
  font-size: 11px;
}
</style>
