<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  Plus,
  Edit,
  Refresh,
  Document,
  Check,
  View,
  Back,
  Delete,
  Sort,
} from '@element-plus/icons-vue'
import {
  getPages,
  getPage,
  createPage,
  updatePage,
  publishPage,
  rollbackPage,
  previewPage,
  LC_PAGE_STATUS,
  LC_PAGE_STATUS_OPTIONS,
} from '@/api/lowcode'
import {
  LC_ACTION_TYPE,
  LC_ACTION_TYPE_OPTIONS,
  LC_COMPONENT_TYPE_OPTIONS,
  LC_PAGE_TYPE,
  LC_PAGE_TYPE_OPTIONS,
} from '@/api/types'
import type {
  LcAction,
  LcComponent,
  LcPage,
  LcPageDetailVO,
  PageResult,
  SaveLcPageRequest,
} from '@/api/types'
import { track } from '@/utils/tracker'

const { t } = useI18n()

/**
 * 低代码页面设计器。设计来源: 14-低代码平台设计、16-原型与交互体验设计 低代码设计器
 *
 * GA2-28 功能落地:
 *  - 页面列表: 分页查询 + 编码/名称/状态/类型过滤 + 新建/编辑/预览/发布/回滚操作
 *  - 页面设计器: el-dialog 三栏式 (左侧组件区 + 中间画布 + 右侧属性面板)
 *      左侧: 可拖入组件清单 (INPUT/SELECT/TABLE/FORM/BUTTON/CUSTOM)
 *      中间: 画布展示已选组件树 + 动作列表 (SUBMIT/RESET/API_CALL/NAVIGATE/EXPORT)
 *      右侧: 属性面板编辑选中组件/动作的 JSON 配置
 *  - 草稿保存: id 为空新建, 否则更新 (需 version 乐观锁)
 *  - 预览: POST /{id}/preview 返回 LcPageDetailVO (不发布即可预览)
 *  - 发布版本: POST /{id}/publish?version={n} (生成不可变快照 + config_hash)
 *  - 回滚版本: POST /{id}/rollback?version={n} (历史版本复制为新草稿)
 *  - 版本差异: 通过详情对话框展示 versionNo + 配置摘要 (供 diff 预览)
 *  - GA2-18 track() 埋点: create/update/preview/publish/rollback 关键事件
 *  - WCAG: aria-label 标签 + el-form 校验提示
 *
 * 设计文档对应:
 *  - 14 号文档 line 62-72: lc_page / lc_component / lc_action 表结构
 *  - 14 号文档 line 154-160: 版本、发布与回滚
 *  - 14 号文档 line 204-208: 组件联动 rules_json triggers + 事件命名规范
 *  - 14 号文档 line 272-279: schema_version 迁移
 *  - 16 号文档 line 62-68: 左侧组件区 + 中间画布 + 右侧属性面板 + 顶部保存/预览/发布 + 版本差异
 */

interface ComponentRow extends LcComponent {
  /** 前端临时 ID, 用于 el-table :key */
  _rowKey: string
}
interface ActionRow extends LcAction {
  _rowKey: string
}

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const previewing = ref(false)
const rollingBack = ref(false)
const tableData = ref<LcPage[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const filters = reactive({
  pageCode: '',
  pageName: '',
  status: '',
  pageType: '',
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const detailLoading = ref(false)
const form = reactive<SaveLcPageRequest>({
  id: undefined,
  version: undefined,
  pageCode: '',
  pageName: '',
  entityId: '',
  pageType: LC_PAGE_TYPE.LIST,
  layoutJson: '',
  layoutSchemaVersion: '1.0.0',
  components: [],
  actions: [],
})
const componentRows = ref<ComponentRow[]>([])
const actionRows = ref<ActionRow[]>([])

/** 当前选中的组件/动作 (右侧属性面板编辑目标) */
const selectedComponentKey = ref<string | null>(null)
const selectedActionKey = ref<string | null>(null)

/** 详情/版本差异对话框 */
const detailDialogVisible = ref(false)
const detailData = ref<LcPageDetailVO | null>(null)

/** 预览对话框 */
const previewDialogVisible = ref(false)
const previewData = ref<LcPageDetailVO | null>(null)

let _rowKeySeq = 0
function genRowKey(): string {
  _rowKeySeq += 1
  return `row-${Date.now()}-${_rowKeySeq}`
}

const statusLabelMap: Record<string, string> = LC_PAGE_STATUS_OPTIONS.reduce<
  Record<string, string>
>(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {}
)
const statusTagTypeMap: Record<
  string,
  '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'
> = LC_PAGE_STATUS_OPTIONS.reduce<
  Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'>
>(
  (m, o) => ({ ...m, [o.value]: o.tagType }),
  {}
)

const pageTypeLabelMap: Record<string, string> = LC_PAGE_TYPE_OPTIONS.reduce<
  Record<string, string>
>(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {}
)

const dialogModeIsEdit = computed(() => !!form.id)

/** 选中组件的引用, 用于右侧属性面板双向编辑 */
const selectedComponent = computed<ComponentRow | null>(() => {
  if (!selectedComponentKey.value) return null
  return (
    componentRows.value.find((c) => c._rowKey === selectedComponentKey.value) ||
    null
  )
})

const selectedAction = computed<ActionRow | null>(() => {
  if (!selectedActionKey.value) return null
  return (
    actionRows.value.find((a) => a._rowKey === selectedActionKey.value) || null
  )
})

async function loadData() {
  loading.value = true
  try {
    track('web.lowcode.page.list.view', {
      payload: { page: currentPage.value, size: pageSize.value },
    })
    const res: PageResult<LcPage> = await getPages({
      page: currentPage.value,
      size: pageSize.value,
      pageCode: filters.pageCode || undefined,
      pageName: filters.pageName || undefined,
      status: filters.status || undefined,
      pageType: filters.pageType || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadPageListFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filters.pageCode = ''
  filters.pageName = ''
  filters.status = ''
  filters.pageType = ''
  handleSearch()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function resetForm() {
  form.id = undefined
  form.version = undefined
  form.pageCode = ''
  form.pageName = ''
  form.entityId = ''
  form.pageType = LC_PAGE_TYPE.LIST
  form.layoutJson = ''
  form.layoutSchemaVersion = '1.0.0'
  form.components = []
  form.actions = []
  componentRows.value = []
  actionRows.value = []
  selectedComponentKey.value = null
  selectedActionKey.value = null
}

async function handleAdd() {
  resetForm()
  dialogTitle.value = t('lowcode.page.dialog.create')
  dialogVisible.value = true
  track('web.lowcode.page.create.click', {})
}

async function handleEdit(row: LcPage) {
  resetForm()
  dialogTitle.value = t('lowcode.page.dialog.edit')
  dialogVisible.value = true
  detailLoading.value = true
  track('web.lowcode.page.update.click', { bizId: row.id })
  try {
    const detail: LcPageDetailVO = await getPage(row.id)
    form.id = detail.id
    form.version = detail.version
    form.pageCode = detail.pageCode
    form.pageName = detail.pageName
    form.entityId = detail.entityId || ''
    form.pageType = detail.pageType
    form.layoutJson = detail.layoutJson || ''
    form.layoutSchemaVersion = detail.layoutSchemaVersion || '1.0.0'
    componentRows.value = (detail.components || []).map((c) => ({
      ...c,
      _rowKey: genRowKey(),
    }))
    actionRows.value = (detail.actions || []).map((a) => ({
      ...a,
      _rowKey: genRowKey(),
    }))
    if (componentRows.value.length > 0) {
      selectedComponentKey.value = componentRows.value[0]._rowKey
    }
    if (actionRows.value.length > 0) {
      selectedActionKey.value = actionRows.value[0]._rowKey
    }
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadPageDetailFailed'))
    dialogVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function handleViewDetail(row: LcPage) {
  detailLoading.value = true
  detailDialogVisible.value = true
  try {
    detailData.value = await getPage(row.id)
    track('web.lowcode.page.detail.view', { bizId: row.id })
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadDetailFailed'))
  } finally {
    detailLoading.value = false
  }
}

/* =================== 组件区: 添加/选中/移除 =================== */

function handleAddComponent(type: string) {
  const row: ComponentRow = {
    _rowKey: genRowKey(),
    componentCode: `comp_${componentRows.value.length + 1}`,
    componentType: type,
    propsJson: '{}',
    rulesJson: '',
    eventsJson: '',
    propsSchemaVersion: '1.0.0',
    parentComponentId: '',
    sortNo: componentRows.value.length + 1,
  }
  componentRows.value.push(row)
  selectedComponentKey.value = row._rowKey
  selectedActionKey.value = null
}

function handleSelectComponent(row: ComponentRow) {
  selectedComponentKey.value = row._rowKey
  selectedActionKey.value = null
}

function handleRemoveComponent(row: ComponentRow) {
  const idx = componentRows.value.findIndex((r) => r._rowKey === row._rowKey)
  if (idx >= 0) {
    componentRows.value.splice(idx, 1)
    if (selectedComponentKey.value === row._rowKey) {
      selectedComponentKey.value =
        componentRows.value[0]?._rowKey || null
    }
  }
}

function handleMoveComponent(row: ComponentRow, delta: -1 | 1) {
  const idx = componentRows.value.findIndex((r) => r._rowKey === row._rowKey)
  const target = idx + delta
  if (idx < 0 || target < 0 || target >= componentRows.value.length) return
  const arr = componentRows.value
  ;[arr[idx], arr[target]] = [arr[target], arr[idx]]
  arr.forEach((c, i) => (c.sortNo = i + 1))
}

/* =================== 动作区: 添加/选中/移除 =================== */

function handleAddAction(type: string) {
  const row: ActionRow = {
    _rowKey: genRowKey(),
    actionCode: `action_${actionRows.value.length + 1}`,
    actionName: '',
    actionType: type,
    permissionCode: '',
    confirmRequired: false,
    apiMethod: type === LC_ACTION_TYPE.API_CALL ? 'GET' : '',
    apiPath: '',
    payloadMapping: '',
  }
  actionRows.value.push(row)
  selectedActionKey.value = row._rowKey
  selectedComponentKey.value = null
}

function handleSelectAction(row: ActionRow) {
  selectedActionKey.value = row._rowKey
  selectedComponentKey.value = null
}

function handleRemoveAction(row: ActionRow) {
  const idx = actionRows.value.findIndex((r) => r._rowKey === row._rowKey)
  if (idx >= 0) {
    actionRows.value.splice(idx, 1)
    if (selectedActionKey.value === row._rowKey) {
      selectedActionKey.value = actionRows.value[0]?._rowKey || null
    }
  }
}

/* =================== 校验与保存 =================== */

/**
 * GA2-28: 校验 JSON 字段合法性 (防止 PostgreSQL jsonb 解析失败)。
 * 空字符串允许 (后端 jsonOrNull 会转 null), 非空必须是合法 JSON。
 * @param label 用于错误提示的组件/动作编码
 * @param jsonFields 待校验的 JSON 字符串数组
 * @returns 错误消息或 null
 */
function checkJsonFields(label: string, ...jsonFields: Array<string | undefined>): string | null {
  for (const field of jsonFields) {
    if (field && field.trim()) {
      try {
        JSON.parse(field)
      } catch {
        return t('lowcode.page.validate.jsonInvalid', { label, value: field.slice(0, 50) })
      }
    }
  }
  return null
}

function validateForm(): string | null {
  if (!form.pageCode.trim()) return t('lowcode.page.validate.codeRequired')
  if (!/^[a-z][a-z0-9_]*$/i.test(form.pageCode)) {
    return t('lowcode.page.validate.codeFormat')
  }
  if (!form.pageName.trim()) return t('lowcode.page.validate.nameRequired')
  if (!form.pageType) return t('lowcode.page.validate.typeRequired')
  if (!form.layoutSchemaVersion?.trim()) {
    return t('lowcode.page.validate.layoutSchemaVersionRequired')
  }

  // 校验组件编码唯一
  const compCodes = new Set<string>()
  for (const c of componentRows.value) {
    if (!c.componentCode.trim()) return t('lowcode.page.validate.componentCodeMissing')
    if (compCodes.has(c.componentCode)) return t('lowcode.page.validate.componentCodeDuplicate', { code: c.componentCode })
    compCodes.add(c.componentCode)
    if (!c.componentType) return t('lowcode.page.validate.componentTypeMissing', { code: c.componentCode })
    if (!c.propsSchemaVersion?.trim()) {
      return t('lowcode.page.validate.propsSchemaVersionMissing', { code: c.componentCode })
    }
    // GA2-28: propsJson/rulesJson/eventsJson 必须是合法 JSON (若填), 防止 PostgreSQL jsonb 解析失败
    const jsonErr = checkJsonFields(c.componentCode, c.propsJson, c.rulesJson, c.eventsJson)
    if (jsonErr) return jsonErr
  }

  // 校验动作编码唯一
  const actCodes = new Set<string>()
  for (const a of actionRows.value) {
    if (!a.actionCode.trim()) return t('lowcode.page.validate.actionCodeMissing')
    if (actCodes.has(a.actionCode)) return t('lowcode.page.validate.actionCodeDuplicate', { code: a.actionCode })
    actCodes.add(a.actionCode)
    if (!a.actionName.trim()) return t('lowcode.page.validate.actionNameMissing', { code: a.actionCode })
    if (!a.actionType) return t('lowcode.page.validate.actionTypeMissing', { code: a.actionCode })
    if (a.actionType === LC_ACTION_TYPE.API_CALL) {
      if (!a.apiMethod?.trim()) return t('lowcode.page.validate.apiMethodMissing', { code: a.actionCode })
      if (!a.apiPath?.trim()) return t('lowcode.page.validate.apiPathMissing', { code: a.actionCode })
    }
  }

  // layoutJson 必须是合法 JSON (若填)
  if (form.layoutJson && form.layoutJson.trim()) {
    try {
      JSON.parse(form.layoutJson)
    } catch {
      return t('lowcode.page.validate.layoutJsonInvalid')
    }
  }
  return null
}

async function handleSave() {
  const err = validateForm()
  if (err) {
    ElMessage.warning(err)
    return
  }
  saving.value = true
  // 组装请求 (剥离前端临时 _rowKey)
  const payload: SaveLcPageRequest = {
    ...form,
    components: componentRows.value.map(({ _rowKey, id, pageId, ...rest }) => ({
      ...rest,
      ...(id ? { id } : {}),
    })),
    actions: actionRows.value.map(({ _rowKey, id, pageId, ...rest }) => ({
      ...rest,
      ...(id ? { id } : {}),
    })),
  }
  track(
    dialogModeIsEdit.value
      ? 'web.lowcode.page.update.submit'
      : 'web.lowcode.page.create.submit',
    {
      bizType: 'lc_page',
      payload: {
        componentCount: payload.components.length,
        actionCount: payload.actions.length,
      },
    }
  )
  try {
    const saved: LcPage = await (dialogModeIsEdit.value
      ? updatePage(form.id!, payload)
      : createPage(payload))
    ElMessage.success(
      dialogModeIsEdit.value
        ? t('lowcode.msg.draftUpdateSuccess')
        : t('lowcode.msg.draftCreateSuccess')
    )
    track(
      dialogModeIsEdit.value
        ? 'web.lowcode.page.update.success'
        : 'web.lowcode.page.create.success',
      { bizType: 'lc_page', bizId: saved.id }
    )
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    track(
      dialogModeIsEdit.value
        ? 'web.lowcode.page.update.failed'
        : 'web.lowcode.page.create.failed',
      { bizType: 'lc_page', result: 'FAILED', errorCode: 'LC_PAGE_SAVE_ERROR' }
    )
    ElMessage.error(t('lowcode.msg.savePageFailed'))
  } finally {
    saving.value = false
  }
}

/* =================== 预览 =================== */

async function handlePreview(row: LcPage) {
  previewing.value = true
  track('web.lowcode.page.preview.click', { bizId: row.id })
  try {
    previewData.value = await previewPage(row.id)
    previewDialogVisible.value = true
    track('web.lowcode.page.preview.success', {
      bizId: row.id,
      payload: {
        componentCount: previewData.value.components?.length || 0,
        actionCount: previewData.value.actions?.length || 0,
      },
    })
  } catch (e) {
    track('web.lowcode.page.preview.failed', {
      bizId: row.id,
      result: 'FAILED',
      errorCode: 'LC_PAGE_PREVIEW_ERROR',
    })
    ElMessage.error(t('lowcode.msg.previewFailed'))
  } finally {
    previewing.value = false
  }
}

/* =================== 发布 =================== */

async function handlePublish(row: LcPage) {
  if (row.status !== LC_PAGE_STATUS.DRAFT) {
    ElMessage.warning(t('lowcode.msg.onlyDraftCanPublish'))
    return
  }
  const version = row.versionNo ?? row.version ?? 0
  try {
    await ElMessageBox.confirm(
      t('lowcode.msg.confirmPublishPage', { name: row.pageName, version }),
      t('lowcode.msg.publishConfirmTitle'),
      { type: 'warning', confirmButtonText: t('lowcode.msg.confirmPublishButton'), cancelButtonText: t('lowcode.msg.cancel') }
    )
  } catch {
    return
  }
  publishing.value = true
  track('web.lowcode.page.publish.click', { bizType: 'lc_page', bizId: row.id })
  try {
    await publishPage(row.id, version)
    ElMessage.success(t('lowcode.msg.pagePublishSuccess'))
    track('web.lowcode.page.publish.success', {
      bizType: 'lc_page',
      bizId: row.id,
      payload: { version },
    })
    await loadData()
  } catch (e) {
    track('web.lowcode.page.publish.failed', {
      bizType: 'lc_page',
      bizId: row.id,
      result: 'FAILED',
      errorCode: 'LC_PAGE_PUBLISH_ERROR',
    })
    ElMessage.error(t('lowcode.msg.publishDraftFailed'))
  } finally {
    publishing.value = false
  }
}

/* =================== 回滚 =================== */

async function handleRollback(row: LcPage) {
  if (row.status !== LC_PAGE_STATUS.PUBLISHED) {
    ElMessage.warning(t('lowcode.msg.onlyPublishedCanRollback'))
    return
  }
  const version = row.versionNo ?? row.version ?? 0
  let targetVersion = 0
  try {
    const { value } = await ElMessageBox.prompt(
      t('lowcode.msg.confirmRollbackPage', { name: row.pageName, version }),
      t('lowcode.msg.rollbackConfirmTitle'),
      {
        type: 'warning',
        confirmButtonText: t('lowcode.msg.confirmRollbackButton'),
        cancelButtonText: t('lowcode.msg.cancel'),
        inputPattern: /^\d+$/,
        inputErrorMessage: t('lowcode.msg.pleaseInputNumericVersion'),
        inputValue: String(Math.max(0, version - 1)),
      }
    )
    targetVersion = Number(value)
  } catch {
    return
  }
  if (targetVersion >= version) {
    ElMessage.warning(t('lowcode.msg.targetVersionMustBeSmaller'))
    return
  }
  rollingBack.value = true
  track('web.lowcode.page.rollback.click', {
    bizType: 'lc_page',
    bizId: row.id,
    payload: { from: version, to: targetVersion },
  })
  try {
    const rolled: LcPage = await rollbackPage(row.id, targetVersion)
    ElMessage.success(
      t('lowcode.msg.rollbackSuccess', {
        targetVersion,
        newVersion: rolled.versionNo,
      })
    )
    track('web.lowcode.page.rollback.success', {
      bizType: 'lc_page',
      bizId: row.id,
      payload: { from: version, to: targetVersion, newVersion: rolled.versionNo },
    })
    await loadData()
  } catch (e) {
    track('web.lowcode.page.rollback.failed', {
      bizType: 'lc_page',
      bizId: row.id,
      result: 'FAILED',
      errorCode: 'LC_PAGE_ROLLBACK_ERROR',
    })
    ElMessage.error(t('lowcode.msg.rollbackFailed'))
  } finally {
    rollingBack.value = false
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

function formatComponentTypes(types: Array<{ componentType: string }>): string {
  if (!types || types.length === 0) return '-'
  const counts: Record<string, number> = {}
  for (const c of types) {
    counts[c.componentType] = (counts[c.componentType] || 0) + 1
  }
  return Object.entries(counts)
    .map(([t, n]) => `${t}${n > 1 ? `×${n}` : ''}`)
    .join(', ')
}

onMounted(loadData)
</script>

<template>
  <div class="lc-page-list">
    <!-- 顶部过滤栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('lowcode.page.field.code')">
          <el-input
            v-model="filters.pageCode"
            :placeholder="$t('lowcode.page.placeholder.codeExample')"
            clearable
            style="width: 180px"
            :aria-label="$t('lowcode.page.aria.filterCode')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.page.field.name')">
          <el-input
            v-model="filters.pageName"
            :placeholder="$t('lowcode.page.placeholder.nameExample')"
            clearable
            style="width: 180px"
            :aria-label="$t('lowcode.page.aria.filterName')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.page.field.type')">
          <el-select
            v-model="filters.pageType"
            :placeholder="$t('lowcode.page.placeholder.all')"
            clearable
            style="width: 140px"
            :aria-label="$t('lowcode.page.aria.filterType')"
          >
            <el-option
              v-for="o in LC_PAGE_TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('lowcode.page.field.status')">
          <el-select
            v-model="filters.status"
            :placeholder="$t('lowcode.page.placeholder.all')"
            clearable
            style="width: 120px"
            :aria-label="$t('lowcode.page.aria.filterStatus')"
          >
            <el-option
              v-for="o in LC_PAGE_STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Refresh" @click="handleSearch">
            {{ $t('lowcode.page.action.search') }}
          </el-button>
          <el-button @click="handleReset">{{ $t('lowcode.page.action.reset') }}</el-button>
          <el-button
            v-permission="'lc:page:add'"
            type="success"
            :icon="Plus"
            @click="handleAdd"
            :aria-label="$t('lowcode.page.aria.createDraft')"
          >
            {{ $t('lowcode.page.action.createDraft') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 页面列表 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="tableData" border stripe :aria-label="$t('lowcode.page.aria.list')">
        <el-table-column prop="pageCode" :label="$t('lowcode.page.field.code')" width="200" fixed="left" />
        <el-table-column prop="pageName" :label="$t('lowcode.page.field.name')" min-width="160" />
        <el-table-column :label="$t('lowcode.page.field.type')" width="120">
          <template #default="{ row }">
            <el-tag size="small" type="primary">
              {{ pageTypeLabelMap[row.pageType] || row.pageType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('lowcode.page.field.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagTypeMap[row.status] || 'info'" size="small">
              {{ statusLabelMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionNo" :label="$t('lowcode.page.field.version')" width="80" align="center" />
        <el-table-column prop="entityId" :label="$t('lowcode.page.field.relatedEntity')" width="180">
          <template #default="{ row }">
            <span class="hash-cell" :title="row.entityId">
              {{ row.entityId ? row.entityId.slice(0, 16) + '...' : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('lowcode.page.field.createdTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
        <el-table-column
          v-if="false"
          prop="publishedTime"
          :label="$t('lowcode.page.field.publishedTime')"
          width="180"
        >
          <template #default="{ row }">{{ formatTime(row.publishedTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.page.field.operation')" width="360" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'lc:page:detail'"
              link
              size="small"
              :icon="Document"
              @click="handleViewDetail(row as LcPage)"
              :aria-label="$t('lowcode.page.aria.viewDetail')"
            >
              {{ $t('lowcode.page.action.detail') }}
            </el-button>
            <el-button
              v-permission="'lc:page:edit'"
              link
              type="primary"
              size="small"
              :icon="Edit"
              @click="handleEdit(row as LcPage)"
              :aria-label="$t('lowcode.page.aria.edit')"
            >
              {{ $t('lowcode.page.action.edit') }}
            </el-button>
            <el-button
              v-permission="'lc:page:detail'"
              link
              type="info"
              size="small"
              :icon="View"
              :loading="previewing"
              @click="handlePreview(row as LcPage)"
              :aria-label="$t('lowcode.page.aria.preview')"
            >
              {{ $t('lowcode.page.action.preview') }}
            </el-button>
            <el-button
              v-permission="'lc:page:publish'"
              link
              type="warning"
              size="small"
              :icon="Check"
              :disabled="row.status !== 'DRAFT'"
              :loading="publishing"
              @click="handlePublish(row as LcPage)"
              :aria-label="$t('lowcode.page.aria.publish')"
            >
              {{ $t('lowcode.page.action.publish') }}
            </el-button>
            <el-button
              v-permission="'lc:page:rollback'"
              link
              type="danger"
              size="small"
              :icon="Back"
              :disabled="row.status !== 'PUBLISHED'"
              :loading="rollingBack"
              @click="handleRollback(row as LcPage)"
              :aria-label="$t('lowcode.page.aria.rollback')"
            >
              {{ $t('lowcode.page.action.rollback') }}
            </el-button>
          </template>
        </el-table-column>
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

    <!-- 页面设计器对话框: 左侧组件区 + 中间画布 + 右侧属性面板 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="1280px"
      :close-on-click-modal="false"
      :aria-label="$t('lowcode.page.aria.designerDialog')"
      class="designer-dialog"
    >
      <div v-loading="detailLoading" class="designer-body">
        <el-alert
          type="info"
          :closable="false"
          :title="$t('lowcode.page.alert.draftArea.title')"
          :description="$t('lowcode.page.alert.draftArea.description')"
          show-icon
          style="margin-bottom: 12px"
        />

        <!-- 基本信息 -->
        <div class="section-title">{{ $t('lowcode.page.section.basicInfo') }}</div>
        <el-form :model="form" label-width="120px" size="default">
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item :label="$t('lowcode.page.field.code')" required>
                <el-input
                  v-model="form.pageCode"
                  :placeholder="$t('lowcode.page.placeholder.codeExample2')"
                  :disabled="dialogModeIsEdit"
                  :aria-label="$t('lowcode.page.field.code')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="$t('lowcode.page.field.name')" required>
                <el-input
                  v-model="form.pageName"
                  :placeholder="$t('lowcode.page.placeholder.nameExample2')"
                  :aria-label="$t('lowcode.page.field.name')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="$t('lowcode.page.field.type')" required>
                <el-select
                  v-model="form.pageType"
                  :aria-label="$t('lowcode.page.field.type')"
                  style="width: 100%"
                >
                  <el-option
                    v-for="o in LC_PAGE_TYPE_OPTIONS"
                    :key="o.value"
                    :label="o.label"
                    :value="o.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="$t('lowcode.page.field.relatedEntityId')">
                <el-input
                  v-model="form.entityId"
                  :placeholder="$t('lowcode.page.placeholder.relatedEntityId')"
                  :aria-label="$t('lowcode.page.field.relatedEntityId')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="$t('lowcode.page.field.layoutSchemaVersion')" required>
                <el-input
                  v-model="form.layoutSchemaVersion"
                  :placeholder="$t('lowcode.page.placeholder.schemaVersion')"
                  :aria-label="$t('lowcode.page.aria.layoutSchemaVersion')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6" v-if="dialogModeIsEdit">
              <el-form-item :label="$t('lowcode.page.field.optimisticVersion')">
                <el-input-number
                  v-model="form.version"
                  :min="0"
                  disabled
                  :aria-label="$t('lowcode.page.field.optimisticVersion')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('lowcode.page.field.layoutJson')">
                <el-input
                  v-model="form.layoutJson"
                  type="textarea"
                  :rows="2"
                  :placeholder="$t('lowcode.page.placeholder.layoutJson')"
                  :aria-label="$t('lowcode.page.field.layoutJson')"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 三栏设计器: 组件区 + 画布 + 属性面板 -->
        <div class="designer-layout">
          <!-- 左侧: 组件区 -->
          <div class="designer-left">
            <div class="panel-title">
              <span>{{ $t('lowcode.page.section.componentArea') }}</span>
              <small>{{ $t('lowcode.page.tip.clickToAdd') }}</small>
            </div>
            <div class="component-palette">
              <div
                v-for="opt in LC_COMPONENT_TYPE_OPTIONS"
                :key="opt.value"
                class="palette-item"
                @click="handleAddComponent(opt.value)"
                :aria-label="$t('lowcode.page.aria.addComponent', { label: opt.label })"
              >
                <el-icon><Plus /></el-icon>
                <span>{{ opt.label }}</span>
              </div>
            </div>

            <div class="panel-title" style="margin-top: 12px">
              <span>{{ $t('lowcode.page.section.actionArea') }}</span>
              <small>{{ $t('lowcode.page.tip.clickToAdd') }}</small>
            </div>
            <div class="component-palette">
              <div
                v-for="opt in LC_ACTION_TYPE_OPTIONS"
                :key="opt.value"
                class="palette-item palette-action"
                @click="handleAddAction(opt.value)"
                :aria-label="$t('lowcode.page.aria.addAction', { label: opt.label })"
              >
                <el-icon><Plus /></el-icon>
                <span>{{ opt.label }}</span>
              </div>
            </div>
          </div>

          <!-- 中间: 画布 -->
          <div class="designer-center">
            <div class="panel-title">
              <span>{{ $t('lowcode.page.section.canvasComponentList') }}</span>
              <small>{{ $t('lowcode.page.tip.componentCount', { count: componentRows.length }) }}</small>
            </div>
            <el-table
              :data="componentRows"
              border
              size="small"
              max-height="280"
              highlight-current-row
              row-key="_rowKey"
              @current-change="
                (row) => row && handleSelectComponent(row as ComponentRow)
              "
              :aria-label="$t('lowcode.page.aria.componentCanvas')"
            >
              <el-table-column :label="$t('lowcode.page.field.index')" type="index" width="50" align="center" />
              <el-table-column prop="componentCode" :label="$t('lowcode.page.field.componentCode')" width="160" />
              <el-table-column :label="$t('lowcode.page.field.typeLabel')" width="120">
                <template #default="{ row }">
                  <el-tag size="small" type="info">
                    {{ row.componentType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="propsSchemaVersion" :label="$t('lowcode.page.field.schemaVersion')" width="100" />
              <el-table-column prop="sortNo" :label="$t('lowcode.page.field.sortNo')" width="80" align="center" />
              <el-table-column :label="$t('lowcode.page.field.operation')" width="160" align="center">
                <template #default="{ row }">
                  <el-button
                    link
                    size="small"
                    :icon="Sort"
                    @click.stop="handleMoveComponent(row as ComponentRow, -1)"
                    :aria-label="$t('lowcode.page.aria.moveUp')"
                  >
                    {{ $t('lowcode.page.action.moveUp') }}
                  </el-button>
                  <el-button
                    link
                    size="small"
                    :icon="Sort"
                    @click.stop="handleMoveComponent(row as ComponentRow, 1)"
                    :aria-label="$t('lowcode.page.aria.moveDown')"
                  >
                    {{ $t('lowcode.page.action.moveDown') }}
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :icon="Delete"
                    @click.stop="handleRemoveComponent(row as ComponentRow)"
                    :aria-label="$t('lowcode.page.aria.removeComponent')"
                  />
                </template>
              </el-table-column>
              <template #empty>
                <div class="empty-tip">{{ $t('lowcode.page.tip.addComponentFromLeft') }}</div>
              </template>
            </el-table>

            <div class="panel-title" style="margin-top: 12px">
              <span>{{ $t('lowcode.page.section.canvasActionList') }}</span>
              <small>{{ $t('lowcode.page.tip.actionCount', { count: actionRows.length }) }}</small>
            </div>
            <el-table
              :data="actionRows"
              border
              size="small"
              max-height="200"
              highlight-current-row
              row-key="_rowKey"
              @current-change="
                (row) => row && handleSelectAction(row as ActionRow)
              "
              :aria-label="$t('lowcode.page.aria.actionCanvas')"
            >
              <el-table-column :label="$t('lowcode.page.field.index')" type="index" width="50" align="center" />
              <el-table-column prop="actionCode" :label="$t('lowcode.page.field.actionCode')" width="160" />
              <el-table-column prop="actionName" :label="$t('lowcode.page.field.actionName')" width="160" />
              <el-table-column :label="$t('lowcode.page.field.typeLabel')" width="120">
                <template #default="{ row }">
                  <el-tag size="small" type="warning">
                    {{ row.actionType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                prop="permissionCode"
                :label="$t('lowcode.page.field.permissionCode')"
                width="180"
              />
              <el-table-column
                :label="$t('lowcode.page.field.operation')"
                width="100"
                align="center"
              >
                <template #default="{ row }">
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :icon="Delete"
                    @click.stop="handleRemoveAction(row as ActionRow)"
                    :aria-label="$t('lowcode.page.aria.removeAction')"
                  />
                </template>
              </el-table-column>
              <template #empty>
                <div class="empty-tip">{{ $t('lowcode.page.tip.addActionFromLeft') }}</div>
              </template>
            </el-table>
          </div>

          <!-- 右侧: 属性面板 -->
          <div class="designer-right">
            <div class="panel-title">
              <span>{{ $t('lowcode.page.section.propertyPanel') }}</span>
              <small v-if="selectedComponent">{{ $t('lowcode.page.tip.componentSelected', { code: selectedComponent.componentCode }) }}</small>
              <small v-else-if="selectedAction">{{ $t('lowcode.page.tip.actionSelected', { code: selectedAction.actionCode }) }}</small>
              <small v-else>{{ $t('lowcode.page.tip.selectComponentOrAction') }}</small>
            </div>

            <!-- 组件属性 -->
            <el-form
              v-if="selectedComponent"
              :model="selectedComponent"
              label-width="100px"
              size="small"
            >
              <el-form-item :label="$t('lowcode.page.field.componentCode')" required>
                <el-input v-model="selectedComponent.componentCode" />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.componentTypeLabel')" required>
                <el-select v-model="selectedComponent.componentType" style="width: 100%">
                  <el-option
                    v-for="o in LC_COMPONENT_TYPE_OPTIONS"
                    :key="o.value"
                    :label="o.label"
                    :value="o.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.propsVersion')" required>
                <el-input v-model="selectedComponent.propsSchemaVersion" />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.parentComponentId')">
                <el-input
                  v-model="selectedComponent.parentComponentId"
                  :placeholder="$t('lowcode.page.placeholder.parentComponentId')"
                />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.sortNo')">
                <el-input-number
                  v-model="selectedComponent.sortNo"
                  :min="1"
                  controls-position="right"
                />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.propsJson')">
                <el-input
                  v-model="selectedComponent.propsJson"
                  type="textarea"
                  :rows="4"
                  :placeholder="$t('lowcode.page.placeholder.propsJson')"
                />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.rulesJson')">
                <el-input
                  v-model="selectedComponent.rulesJson"
                  type="textarea"
                  :rows="3"
                  :placeholder="$t('lowcode.page.placeholder.rulesJson')"
                />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.eventsJson')">
                <el-input
                  v-model="selectedComponent.eventsJson"
                  type="textarea"
                  :rows="3"
                  :placeholder="$t('lowcode.page.placeholder.eventsJson')"
                />
              </el-form-item>
            </el-form>

            <!-- 动作属性 -->
            <el-form
              v-else-if="selectedAction"
              :model="selectedAction"
              label-width="100px"
              size="small"
            >
              <el-form-item :label="$t('lowcode.page.field.actionCode')" required>
                <el-input v-model="selectedAction.actionCode" />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.actionName')" required>
                <el-input v-model="selectedAction.actionName" />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.actionType')" required>
                <el-select v-model="selectedAction.actionType" style="width: 100%">
                  <el-option
                    v-for="o in LC_ACTION_TYPE_OPTIONS"
                    :key="o.value"
                    :label="o.label"
                    :value="o.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.permissionCode')">
                <el-input
                  v-model="selectedAction.permissionCode"
                  :placeholder="$t('lowcode.page.placeholder.permissionCode')"
                />
              </el-form-item>
              <el-form-item :label="$t('lowcode.page.field.confirmRequired')">
                <el-switch v-model="selectedAction.confirmRequired" />
              </el-form-item>
              <template v-if="selectedAction.actionType === 'API_CALL'">
                <el-form-item :label="$t('lowcode.page.field.httpMethod')" required>
                  <el-select v-model="selectedAction.apiMethod" style="width: 100%">
                    <el-option label="GET" value="GET" />
                    <el-option label="POST" value="POST" />
                    <el-option label="PUT" value="PUT" />
                    <el-option label="DELETE" value="DELETE" />
                  </el-select>
                </el-form-item>
                <el-form-item :label="$t('lowcode.page.field.apiPath')" required>
                  <el-input
                    v-model="selectedAction.apiPath"
                    :placeholder="$t('lowcode.page.placeholder.apiPath')"
                  />
                </el-form-item>
                <el-form-item :label="$t('lowcode.page.field.payloadMapping')">
                  <el-input
                    v-model="selectedAction.payloadMapping"
                    type="textarea"
                    :rows="2"
                    :placeholder="$t('lowcode.page.placeholder.payloadMapping')"
                  />
                </el-form-item>
              </template>
            </el-form>

            <div v-else class="empty-tip" style="padding: 24px">
              {{ $t('lowcode.page.tip.selectToEdit') }}
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false" :disabled="saving">{{ $t('lowcode.page.action.cancel') }}</el-button>
        <el-button
          v-permission="dialogModeIsEdit ? 'lc:page:edit' : 'lc:page:add'"
          type="primary"
          :loading="saving"
          @click="handleSave"
          :aria-label="$t('lowcode.page.aria.saveDraft')"
        >
          {{ $t('lowcode.page.action.saveDraft') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情/版本差异查看对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="$t('lowcode.page.dialog.detailTitle')"
      width="880px"
      :aria-label="$t('lowcode.page.aria.detailDialog')"
    >
      <div v-loading="detailLoading">
        <template v-if="detailData">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item :label="$t('lowcode.page.field.code')">{{ detailData.pageCode }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.page.field.name')">{{ detailData.pageName }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.page.field.type')">
              <el-tag size="small" type="primary">
                {{ pageTypeLabelMap[detailData.pageType] || detailData.pageType }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.page.field.status')">
              <el-tag :type="statusTagTypeMap[detailData.status] || 'info'" size="small">
                {{ statusLabelMap[detailData.status] || detailData.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.page.field.version')">{{ detailData.versionNo }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.page.field.layoutSchemaVersion')">
              {{ detailData.layoutSchemaVersion || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.page.field.relatedEntityId')">
              <code class="hash-full">{{ detailData.entityId || '-' }}</code>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.page.field.createdTime')">
              {{ formatTime(detailData.createdTime) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.page.field.publishedTime')" v-if="detailData.publishedTime">
              {{ formatTime(detailData.publishedTime) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title" style="margin-top: 16px">
            {{ $t('lowcode.page.section.componentList', { count: detailData.components?.length || 0 }) }}
            <small style="margin-left: 8px; color: #909399">
              {{ formatComponentTypes(detailData.components || []) }}
            </small>
          </div>
          <el-table :data="detailData.components" border size="small" max-height="240">
            <el-table-column prop="componentCode" :label="$t('lowcode.page.field.componentCode')" width="160" />
            <el-table-column prop="componentType" :label="$t('lowcode.page.field.typeLabel')" width="120" />
            <el-table-column prop="propsSchemaVersion" :label="$t('lowcode.page.field.schemaVersion')" width="120" />
            <el-table-column prop="parentComponentId" :label="$t('lowcode.page.field.parentComponent')" width="180">
              <template #default="{ row }">
                <span class="hash-cell">{{ row.parentComponentId || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="sortNo" :label="$t('lowcode.page.field.sortNo')" width="80" align="center" />
            <el-table-column :label="$t('lowcode.page.field.props')" min-width="200">
              <template #default="{ row }">
                <code class="hash-cell">{{ row.propsJson || '-' }}</code>
              </template>
            </el-table-column>
          </el-table>

          <div class="section-title" style="margin-top: 16px">
            {{ $t('lowcode.page.section.actionList', { count: detailData.actions?.length || 0 }) }}
          </div>
          <el-table :data="detailData.actions" border size="small" max-height="200">
            <el-table-column prop="actionCode" :label="$t('lowcode.page.field.actionCode')" width="160" />
            <el-table-column prop="actionName" :label="$t('lowcode.page.field.actionName')" width="160" />
            <el-table-column prop="actionType" :label="$t('lowcode.page.field.typeLabel')" width="120" />
            <el-table-column prop="permissionCode" :label="$t('lowcode.page.field.permissionCode')" width="160" />
            <el-table-column :label="$t('lowcode.page.field.api')" min-width="240">
              <template #default="{ row }">
                <span v-if="row.apiMethod && row.apiPath">
                  <el-tag size="small" type="info">{{ row.apiMethod }}</el-tag>
                  <code class="hash-cell" style="margin-left: 8px">{{ row.apiPath }}</code>
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('lowcode.page.field.confirmRequiredLabel')" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.confirmRequired" size="small" type="warning">{{ $t('lowcode.page.status.yes') }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>

    <!-- 预览对话框 -->
    <el-dialog
      v-model="previewDialogVisible"
      :title="$t('lowcode.page.dialog.previewTitle')"
      width="880px"
      :aria-label="$t('lowcode.page.aria.previewDialog')"
    >
      <template v-if="previewData">
        <el-alert
          type="success"
          :closable="false"
          :title="$t('lowcode.page.alert.previewMode.title')"
          :description="$t('lowcode.page.alert.previewMode.description')"
          show-icon
          style="margin-bottom: 12px"
        />
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('lowcode.page.field.code')">{{ previewData.pageCode }}</el-descriptions-item>
          <el-descriptions-item :label="$t('lowcode.page.field.name')">{{ previewData.pageName }}</el-descriptions-item>
          <el-descriptions-item :label="$t('lowcode.page.field.typeLabel')">
            <el-tag size="small" type="primary">
              {{ pageTypeLabelMap[previewData.pageType] || previewData.pageType }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('lowcode.page.field.status')">
            <el-tag :type="statusTagTypeMap[previewData.status] || 'info'" size="small">
              {{ statusLabelMap[previewData.status] || previewData.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('lowcode.page.field.componentCount')">
            {{ previewData.components?.length || 0 }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('lowcode.page.field.actionCount')">
            {{ previewData.actions?.length || 0 }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title" style="margin-top: 16px">{{ $t('lowcode.page.section.componentPreview') }}</div>
        <el-table :data="previewData.components" border size="small" max-height="280">
          <el-table-column prop="componentCode" :label="$t('lowcode.page.field.componentCode')" width="160" />
          <el-table-column prop="componentType" :label="$t('lowcode.page.field.typeLabel')" width="120" />
          <el-table-column prop="propsSchemaVersion" :label="$t('lowcode.page.field.schema')" width="100" />
          <el-table-column :label="$t('lowcode.page.field.props')" min-width="240">
            <template #default="{ row }">
              <code class="hash-cell">{{ row.propsJson || '-' }}</code>
            </template>
          </el-table-column>
        </el-table>

        <div class="section-title" style="margin-top: 16px">{{ $t('lowcode.page.section.actionPreview') }}</div>
        <el-table :data="previewData.actions" border size="small" max-height="200">
          <el-table-column prop="actionCode" :label="$t('lowcode.page.field.actionCode')" width="160" />
          <el-table-column prop="actionName" :label="$t('lowcode.page.field.actionName')" width="160" />
          <el-table-column prop="actionType" :label="$t('lowcode.page.field.typeLabel')" width="120" />
          <el-table-column :label="$t('lowcode.page.field.api')" min-width="240">
            <template #default="{ row }">
              <span v-if="row.apiMethod && row.apiPath">
                <el-tag size="small" type="info">{{ row.apiMethod }}</el-tag>
                <code class="hash-cell" style="margin-left: 8px">{{ row.apiPath }}</code>
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.lc-page-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-card :deep(.el-card__body) {
  padding: 16px 20px 0 20px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
}

.empty-tip {
  text-align: center;
  color: #c0c4cc;
  padding: 12px;
  font-size: 12px;
}

.hash-cell {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #909399;
}

.hash-full {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}

/* 设计器三栏布局 */
.designer-body {
  min-height: 540px;
}

.designer-layout {
  display: grid;
  grid-template-columns: 200px 1fr 320px;
  gap: 12px;
  margin-top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

.designer-left,
.designer-center,
.designer-right {
  background: #ffffff;
  border-radius: 4px;
  padding: 8px;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 4px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
}

.panel-title small {
  font-weight: normal;
  color: #909399;
  font-size: 11px;
}

.component-palette {
  display: grid;
  grid-template-columns: 1fr;
  gap: 6px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  color: #303133;
  transition: all 0.15s;
}

.palette-item:hover {
  border-color: #409eff;
  color: #409eff;
  background: #ecf5ff;
}

.palette-action {
  background: #fdf6ec;
}

.palette-action:hover {
  border-color: #e6a23c;
  color: #e6a23c;
  background: #fdf6ec;
}

.designer-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}
</style>
