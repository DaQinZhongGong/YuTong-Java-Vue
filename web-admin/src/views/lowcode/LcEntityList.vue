<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Edit, Refresh, Document, Check, Close, Clock } from '@element-plus/icons-vue'
import {
  disableEntity,
  getEntities,
  getEntity,
  publishEntity,
  saveEntityDraft,
  LC_ENTITY_STATUS,
  LC_ENTITY_STATUS_OPTIONS,
} from '@/api/lowcode'
import {
  LC_CASCADE_POLICY,
  LC_FIELD_TYPE,
  LC_FIELD_TYPE_OPTIONS,
  LC_RELATION_TYPE,
} from '@/api/types'
import type {
  LcEntity,
  LcEntityDetailVO,
  LcField,
  LcRelation,
  PageResult,
  SaveLcEntityRequest,
} from '@/api/types'
import { track } from '@/utils/tracker'

const { t } = useI18n()

/**
 * 低代码实体设计器。设计来源: 14-低代码平台设计、16-原型与交互体验设计 低代码设计器
 *
 * GA2-27 功能落地:
 *  - 实体列表: 分页查询 + 编码/名称/状态过滤 + 新建/编辑/发布/禁用操作
 *  - 实体编辑器: el-dialog 三段式 (基本信息 + 字段表 + 关系表)
 *  - 字段配置: 字段编码/名称/列名/数据类型/长度/精度/小数位/可空/默认值/字典类型/主键/唯一/索引/排序
 *    主键约束: primaryFlag=true 时只允许 fieldCode=id, dataType=STRING, dbColumn=varchar(32) (14 号文档第 152 行)
 *  - 关系配置: 目标实体/关系类型 ONE_TO_ONE/ONE_TO_MANY/MANY_TO_ONE/源字段/目标字段/级联策略/必填
 *  - 草稿保存: id 为空新建, 否则更新 (需 version 乐观锁)
 *  - 发布版本: POST /{id}/publish?version={n} (生成不可变快照 + config_hash)
 *  - 禁用: POST /{id}/disable?version={n}
 *  - configHash 展示: 列表行点击查看详情, 展示配置摘要 (用于 diff 预览)
 *  - GA2-18 track() 埋点: create/update/publish/disable 关键事件
 *  - WCAG: aria-label 标签 + el-form 校验提示
 */

interface FieldRow extends LcField {
  /** 前端临时 ID, 用于 el-table :key */
  _rowKey: string
}
interface RelationRow extends LcRelation {
  _rowKey: string
}

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const tableData = ref<LcEntity[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const filters = reactive({
  entityCode: '',
  entityName: '',
  status: '',
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const detailLoading = ref(false)
const form = reactive<SaveLcEntityRequest>({
  id: undefined,
  version: undefined,
  entityCode: '',
  entityName: '',
  tableName: '',
  moduleCode: '',
  ownerUserId: undefined,
  fields: [],
  relations: [],
})
const fieldRows = ref<FieldRow[]>([])
const relationRows = ref<RelationRow[]>([])

/** 详情对话框 (查看 configHash + 字段/关系快照) */
const detailDialogVisible = ref(false)
const detailData = ref<LcEntityDetailVO | null>(null)

/** GA2-47: 版本历史抽屉 (展示 versionNo + configHash + publishedTime 等版本信息) */
const versionDrawerVisible = ref(false)
const versionData = ref<LcEntityDetailVO | null>(null)
const versionLoading = ref(false)

let _rowKeySeq = 0
function genRowKey(): string {
  _rowKeySeq += 1
  return `row-${Date.now()}-${_rowKeySeq}`
}

const statusLabelMap: Record<string, string> = LC_ENTITY_STATUS_OPTIONS.reduce(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {} as Record<string, string>
)
const statusTagTypeMap: Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'> =
  LC_ENTITY_STATUS_OPTIONS.reduce(
    (m, o) => ({ ...m, [o.value]: o.tagType }),
    {} as Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'>
  )

const dialogModeIsEdit = computed(() => !!form.id)

async function loadData() {
  loading.value = true
  try {
    track('web.lowcode.entity.list.view', {
      payload: { page: currentPage.value, size: pageSize.value },
    })
    const res: PageResult<LcEntity> = await getEntities({
      page: currentPage.value,
      size: pageSize.value,
      entityCode: filters.entityCode || undefined,
      entityName: filters.entityName || undefined,
      status: filters.status || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadEntityListFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filters.entityCode = ''
  filters.entityName = ''
  filters.status = ''
  handleSearch()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

function resetForm() {
  form.id = undefined
  form.version = undefined
  form.entityCode = ''
  form.entityName = ''
  form.tableName = ''
  form.moduleCode = ''
  form.ownerUserId = undefined
  form.fields = []
  form.relations = []
  fieldRows.value = []
  relationRows.value = []
}

async function handleAdd() {
  resetForm()
  dialogTitle.value = t('lowcode.entity.dialog.create')
  dialogVisible.value = true
  track('web.lowcode.entity.create.click', {})
}

async function handleEdit(row: LcEntity) {
  resetForm()
  dialogTitle.value = t('lowcode.entity.dialog.edit')
  dialogVisible.value = true
  detailLoading.value = true
  track('web.lowcode.entity.update.click', { bizId: row.id })
  try {
    const detail: LcEntityDetailVO = await getEntity(row.id)
    form.id = detail.id
    form.version = detail.version
    form.entityCode = detail.entityCode
    form.entityName = detail.entityName
    form.tableName = detail.tableName
    form.moduleCode = detail.moduleCode
    form.ownerUserId = detail.ownerUserId
    fieldRows.value = (detail.fields || []).map((f) => ({
      ...f,
      _rowKey: genRowKey(),
    }))
    relationRows.value = (detail.relations || []).map((r) => ({
      ...r,
      _rowKey: genRowKey(),
    }))
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadEntityDetailFailed'))
    dialogVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function handleViewDetail(row: LcEntity) {
  detailLoading.value = true
  detailDialogVisible.value = true
  try {
    detailData.value = await getEntity(row.id)
    track('web.lowcode.entity.detail.view', { bizId: row.id })
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadDetailFailed'))
  } finally {
    detailLoading.value = false
  }
}

/**
 * GA2-47: 打开版本历史抽屉。
 * 47 号文档验收标准: 实体设计器可完成字段 CRUD、发布、版本历史。
 * v1.0: 展示当前实体的版本信息 (versionNo + configHash + publishedTime + schemaVersion)
 *      完整版本列表 API (listEntityVersions) 留 v1.1+
 */
async function handleViewVersionHistory(row: LcEntity) {
  versionLoading.value = true
  versionDrawerVisible.value = true
  try {
    versionData.value = await getEntity(row.id)
    track('web.lowcode.entity.version.view', { bizId: row.id })
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadVersionHistoryFailed'))
    versionDrawerVisible.value = false
  } finally {
    versionLoading.value = false
  }
}

function handleAddField() {
  fieldRows.value.push({
    _rowKey: genRowKey(),
    fieldCode: '',
    fieldName: '',
    dbColumn: '',
    dataType: LC_FIELD_TYPE.STRING,
    lengthValue: 255,
    nullable: true,
    primaryFlag: false,
    uniqueFlag: false,
    indexFlag: false,
    sortNo: fieldRows.value.length + 1,
  })
}

function handleRemoveField(row: FieldRow) {
  const idx = fieldRows.value.findIndex((r) => r._rowKey === row._rowKey)
  if (idx >= 0) fieldRows.value.splice(idx, 1)
}

function handleAddRelation() {
  relationRows.value.push({
    _rowKey: genRowKey(),
    targetEntityId: '',
    relationType: LC_RELATION_TYPE.MANY_TO_ONE,
    cascadePolicy: LC_CASCADE_POLICY.RESTRICT,
    required: false,
  })
}

function handleRemoveRelation(row: RelationRow) {
  const idx = relationRows.value.findIndex((r) => r._rowKey === row._rowKey)
  if (idx >= 0) relationRows.value.splice(idx, 1)
}

/** 主键字段切换约束: primaryFlag=true 时强制 fieldCode=id, dataType=STRING, dbColumn=varchar(32) */
function handlePrimaryFlagChange(row: FieldRow) {
  if (row.primaryFlag) {
    row.fieldCode = 'id'
    row.dataType = LC_FIELD_TYPE.STRING
    row.dbColumn = 'varchar(32)'
    row.nullable = false
    row.lengthValue = 32
    row.uniqueFlag = true
    row.indexFlag = true
    ElMessage.info(t('lowcode.msg.primaryKeyConstrained'))
  }
}

/** 数据类型变更: STRING 默认 255, DECIMAL 默认 precision=18 scale=2, DICT 显示字典类型输入框 */
function handleDataTypeChange(row: FieldRow) {
  if (row.dataType === LC_FIELD_TYPE.STRING && !row.lengthValue) {
    row.lengthValue = 255
  } else if (row.dataType === LC_FIELD_TYPE.DECIMAL) {
    row.precisionValue = row.precisionValue || 18
    row.scaleValue = row.scaleValue ?? 2
  } else if (row.dataType === LC_FIELD_TYPE.DICT) {
    row.dictType = row.dictType || ''
  }
}

function validateForm(): string | null {
  if (!form.entityCode.trim()) return t('lowcode.entity.validate.codeRequired')
  if (!/^[a-z][a-z0-9_]*$/i.test(form.entityCode)) {
    return t('lowcode.entity.validate.codeFormat')
  }
  if (!form.entityName.trim()) return t('lowcode.entity.validate.nameRequired')
  if (!form.tableName.trim()) return t('lowcode.entity.validate.tableNameRequired')
  if (!/^[a-z][a-z0-9_]*$/i.test(form.tableName)) {
    return t('lowcode.entity.validate.tableNameFormat')
  }
  if (!form.moduleCode.trim()) return t('lowcode.entity.validate.moduleCodeRequired')

  // 校验字段
  const codes = new Set<string>()
  const cols = new Set<string>()
  let hasPrimary = false
  for (const f of fieldRows.value) {
    if (!f.fieldCode.trim()) return t('lowcode.entity.validate.fieldCodeMissing')
    if (!/^[a-z][a-z0-9]*$/i.test(f.fieldCode)) {
      return t('lowcode.entity.validate.fieldCodeCamelCase', { code: f.fieldCode })
    }
    if (codes.has(f.fieldCode)) return t('lowcode.entity.validate.fieldCodeDuplicate', { code: f.fieldCode })
    codes.add(f.fieldCode)
    if (!f.dbColumn.trim()) return t('lowcode.entity.validate.fieldColumnMissing', { code: f.fieldCode })
    if (!/^[a-z][a-z0-9_]*$/.test(f.dbColumn)) {
      return t('lowcode.entity.validate.columnSnakeCase', { column: f.dbColumn })
    }
    if (cols.has(f.dbColumn)) return t('lowcode.entity.validate.columnDuplicate', { column: f.dbColumn })
    cols.add(f.dbColumn)
    if (!f.fieldName.trim()) return t('lowcode.entity.validate.fieldNameMissing', { code: f.fieldCode })
    if (f.primaryFlag) {
      hasPrimary = true
      if (f.fieldCode !== 'id' || f.dataType !== LC_FIELD_TYPE.STRING) {
        return t('lowcode.entity.validate.primaryKeyConstraint')
      }
    }
    if (f.dataType === LC_FIELD_TYPE.DICT && !f.dictType) {
      return t('lowcode.entity.validate.dictTypeRequired', { code: f.fieldCode })
    }
  }
  // 实体必须有主键 (设计约束)
  if (fieldRows.value.length > 0 && !hasPrimary) {
    return t('lowcode.entity.validate.primaryKeyMissing')
  }
  // 校验关系
  for (const r of relationRows.value) {
    if (!r.targetEntityId.trim()) return t('lowcode.entity.validate.relationTargetMissing')
    if (!r.relationType) return t('lowcode.entity.validate.relationTypeMissing')
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
  const payload: SaveLcEntityRequest = {
    ...form,
    fields: fieldRows.value.map(({ _rowKey, id, entityId, ...rest }) => ({
      ...rest,
      ...(id ? { id } : {}),
    })),
    relations: relationRows.value.map(({ _rowKey, id, sourceEntityId, ...rest }) => ({
      ...rest,
      ...(id ? { id } : {}),
    })),
  }
  track(
    dialogModeIsEdit.value
      ? 'web.lowcode.entity.update.submit'
      : 'web.lowcode.entity.create.submit',
    { bizType: 'lc_entity', payload: { fieldCount: payload.fields.length, relationCount: payload.relations.length } }
  )
  try {
    const saved: LcEntity = await saveEntityDraft(payload)
    ElMessage.success(
      dialogModeIsEdit.value
        ? t('lowcode.msg.draftUpdateSuccess')
        : t('lowcode.msg.draftCreateSuccess')
    )
    track(
      dialogModeIsEdit.value
        ? 'web.lowcode.entity.update.success'
        : 'web.lowcode.entity.create.success',
      { bizType: 'lc_entity', bizId: saved.id }
    )
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    track(
      dialogModeIsEdit.value
        ? 'web.lowcode.entity.update.failed'
        : 'web.lowcode.entity.create.failed',
      { bizType: 'lc_entity', result: 'FAILED', errorCode: 'LC_SAVE_ERROR' }
    )
    ElMessage.error(t('lowcode.msg.saveEntityFailed'))
  } finally {
    saving.value = false
  }
}

async function handlePublish(row: LcEntity) {
  if (row.status !== LC_ENTITY_STATUS.DRAFT) {
    ElMessage.warning(t('lowcode.msg.onlyDraftCanPublish'))
    return
  }
  const version = row.versionNo ?? row.version ?? 0
  try {
    await ElMessageBox.confirm(
      t('lowcode.msg.confirmPublishEntity', { name: row.entityName, version }),
      t('lowcode.msg.publishConfirmTitle'),
      { type: 'warning', confirmButtonText: t('lowcode.msg.confirmPublishButton'), cancelButtonText: t('lowcode.msg.cancel') }
    )
  } catch {
    return
  }
  publishing.value = true
  track('web.lowcode.entity.publish.click', { bizType: 'lc_entity', bizId: row.id })
  try {
    await publishEntity(row.id, version)
    ElMessage.success(t('lowcode.msg.entityPublishSuccess'))
    track('web.lowcode.entity.publish.success', {
      bizType: 'lc_entity',
      bizId: row.id,
      payload: { version },
    })
    await loadData()
  } catch (e) {
    track('web.lowcode.entity.publish.failed', {
      bizType: 'lc_entity',
      bizId: row.id,
      result: 'FAILED',
      errorCode: 'LC_PUBLISH_ERROR',
    })
    ElMessage.error(t('lowcode.msg.publishDraftFailed'))
  } finally {
    publishing.value = false
  }
}

async function handleDisable(row: LcEntity) {
  if (row.status === LC_ENTITY_STATUS.DISABLED) {
    ElMessage.warning(t('lowcode.msg.entityAlreadyDisabled'))
    return
  }
  const version = row.versionNo ?? row.version ?? 0
  try {
    await ElMessageBox.confirm(
      t('lowcode.msg.confirmDisableEntity', { name: row.entityName }),
      t('lowcode.msg.disableConfirmTitle'),
      { type: 'error', confirmButtonText: t('lowcode.msg.confirmDisableButton'), cancelButtonText: t('lowcode.msg.cancel') }
    )
  } catch {
    return
  }
  track('web.lowcode.entity.disable.click', { bizType: 'lc_entity', bizId: row.id })
  try {
    await disableEntity(row.id, version)
    ElMessage.success(t('lowcode.msg.entityDisabledSuccess'))
    track('web.lowcode.entity.disable.success', {
      bizType: 'lc_entity',
      bizId: row.id,
      payload: { version },
    })
    await loadData()
  } catch (e) {
    track('web.lowcode.entity.disable.failed', {
      bizType: 'lc_entity',
      bizId: row.id,
      result: 'FAILED',
      errorCode: 'LC_DISABLE_ERROR',
    })
    ElMessage.error(t('lowcode.msg.disableEntityFailed'))
  }
}

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

function shortHash(h?: string): string {
  if (!h) return '-'
  return h.length > 16 ? `${h.slice(0, 16)}...` : h
}

onMounted(loadData)
</script>

<template>
  <div class="lc-entity-page">
    <!-- 顶部过滤栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('lowcode.entity.field.code')">
          <el-input
            v-model="filters.entityCode"
            :placeholder="$t('lowcode.entity.placeholder.codeLike')"
            clearable
            style="width: 180px"
            :aria-label="$t('lowcode.entity.aria.filterCode')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.entity.field.name')">
          <el-input
            v-model="filters.entityName"
            :placeholder="$t('lowcode.entity.placeholder.nameLike')"
            clearable
            style="width: 180px"
            :aria-label="$t('lowcode.entity.aria.filterName')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.entity.field.status')">
          <el-select
            v-model="filters.status"
            :placeholder="$t('lowcode.entity.placeholder.all')"
            clearable
            style="width: 120px"
            :aria-label="$t('lowcode.entity.aria.filterStatus')"
          >
            <el-option
              v-for="o in LC_ENTITY_STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Refresh" @click="handleSearch">{{ $t('lowcode.entity.action.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('lowcode.entity.action.reset') }}</el-button>
          <el-button
            v-permission="'lc:entity:add'"
            type="success"
            :icon="Plus"
            @click="handleAdd"
            :aria-label="$t('lowcode.entity.aria.createDraft')"
          >
            {{ $t('lowcode.entity.action.createDraft') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 实体列表 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="tableData" border stripe :aria-label="$t('lowcode.entity.aria.list')">
        <el-table-column prop="entityCode" :label="$t('lowcode.entity.field.code')" width="180" fixed="left" />
        <el-table-column prop="entityName" :label="$t('lowcode.entity.field.name')" min-width="160" />
        <el-table-column prop="tableName" :label="$t('lowcode.entity.field.tableName')" width="200" />
        <el-table-column prop="moduleCode" :label="$t('lowcode.entity.field.module')" width="120" />
        <el-table-column prop="status" :label="$t('lowcode.entity.field.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagTypeMap[row.status] || 'info'" size="small">
              {{ statusLabelMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="versionNo" :label="$t('lowcode.entity.field.version')" width="80" align="center" />
        <el-table-column :label="$t('lowcode.entity.field.configHash')" width="180">
          <template #default="{ row }">
            <span class="hash-cell" :title="row.configHash">{{ shortHash(row.configHash) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('lowcode.entity.field.createdTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.createdTime || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.entity.field.operation')" width="360" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'lc:entity:detail'"
              link
              size="small"
              :icon="Document"
              @click="handleViewDetail(row as LcEntity)"
              :aria-label="$t('lowcode.entity.aria.viewDetail')"
            >
              {{ $t('lowcode.entity.action.detail') }}
            </el-button>
            <!-- GA2-47: 版本历史按钮。47 号文档验收标准: 实体设计器可完成版本历史查看 -->
            <el-button
              v-permission="'lc:entity:detail'"
              link
              type="info"
              size="small"
              :icon="Clock"
              @click="handleViewVersionHistory(row as LcEntity)"
              :aria-label="$t('lowcode.entity.aria.viewHistory')"
            >
              {{ $t('lowcode.entity.action.versionHistory') }}
            </el-button>
            <el-button
              v-permission="'lc:entity:edit'"
              link
              type="primary"
              size="small"
              :icon="Edit"
              @click="handleEdit(row as LcEntity)"
              :aria-label="$t('lowcode.entity.aria.edit')"
            >
              {{ $t('lowcode.entity.action.edit') }}
            </el-button>
            <el-button
              v-permission="'lc:entity:publish'"
              link
              type="warning"
              size="small"
              :icon="Check"
              :disabled="row.status !== 'DRAFT'"
              :loading="publishing"
              @click="handlePublish(row as LcEntity)"
              :aria-label="$t('lowcode.entity.aria.publish')"
            >
              {{ $t('lowcode.entity.action.publish') }}
            </el-button>
            <el-button
              v-permission="'lc:entity:edit'"
              link
              type="danger"
              size="small"
              :icon="Close"
              :disabled="row.status === 'DISABLED'"
              @click="handleDisable(row as LcEntity)"
              :aria-label="$t('lowcode.entity.aria.disable')"
            >
              {{ $t('lowcode.entity.action.disable') }}
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

    <!-- 实体编辑器对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="1080px"
      :close-on-click-modal="false"
      :aria-label="$t('lowcode.entity.aria.editorDialog')"
    >
      <div v-loading="detailLoading">
        <el-alert
          type="info"
          :closable="false"
          :title="$t('lowcode.entity.alert.draftTitle')"
          :description="$t('lowcode.entity.alert.draftDesc')"
          show-icon
          style="margin-bottom: 12px"
        />

        <!-- 基本信息 -->
        <div class="section-title">{{ $t('lowcode.entity.section.basic') }}</div>
        <el-form :model="form" label-width="120px" size="default">
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item :label="$t('lowcode.entity.field.code')" required>
                <el-input
                  v-model="form.entityCode"
                  :placeholder="$t('lowcode.entity.placeholder.codeExample')"
                  :disabled="dialogModeIsEdit"
                  :aria-label="$t('lowcode.entity.aria.entityCode')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item :label="$t('lowcode.entity.field.name')" required>
                <el-input v-model="form.entityName" :placeholder="$t('lowcode.entity.placeholder.nameExample')" :aria-label="$t('lowcode.entity.aria.entityName')" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item :label="$t('lowcode.entity.field.tableName')" required>
                <el-input
                  v-model="form.tableName"
                  :placeholder="$t('lowcode.entity.placeholder.tableNameExample')"
                  :disabled="dialogModeIsEdit"
                  :aria-label="$t('lowcode.entity.aria.tableName')"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item :label="$t('lowcode.entity.field.moduleCode')" required>
                <el-input v-model="form.moduleCode" :placeholder="$t('lowcode.entity.placeholder.moduleExample')" :aria-label="$t('lowcode.entity.aria.moduleCode')" />
              </el-form-item>
            </el-col>
            <el-col :span="8" v-if="dialogModeIsEdit">
              <el-form-item :label="$t('lowcode.entity.field.lockVersion')">
                <el-input-number v-model="form.version" :min="0" disabled :aria-label="$t('lowcode.entity.aria.lockVersion')" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <!-- 字段配置 -->
        <div class="section-title">
          <span>{{ $t('lowcode.entity.section.fields') }}</span>
          <el-button size="small" type="primary" plain :icon="Plus" @click="handleAddField">
            {{ $t('lowcode.entity.action.addField') }}
          </el-button>
        </div>
        <el-table :data="fieldRows" border size="small" :aria-label="$t('lowcode.entity.aria.fieldTable')" max-height="320">
          <el-table-column :label="$t('lowcode.entity.field.index')" type="index" width="50" align="center" />
          <el-table-column :label="$t('lowcode.entity.field.fieldCode')" width="140">
            <template #default="{ row }">
              <el-input v-model="row.fieldCode" size="small" :placeholder="$t('lowcode.entity.placeholder.fieldCodeExample')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.fieldName')" width="140">
            <template #default="{ row }">
              <el-input v-model="row.fieldName" size="small" :placeholder="$t('lowcode.entity.placeholder.fieldNameExample')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.columnName')" width="140">
            <template #default="{ row }">
              <el-input v-model="row.dbColumn" size="small" :placeholder="$t('lowcode.entity.placeholder.columnNameExample')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.dataType')" width="140">
            <template #default="{ row }">
              <el-select
                v-model="row.dataType"
                size="small"
                @change="handleDataTypeChange(row as FieldRow)"
                :aria-label="$t('lowcode.entity.aria.dataType')"
              >
                <el-option
                  v-for="o in LC_FIELD_TYPE_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.length')" width="80">
            <template #default="{ row }">
              <el-input-number
                v-model="row.lengthValue"
                size="small"
                :min="1"
                :max="9999"
                controls-position="right"
                :aria-label="$t('lowcode.entity.aria.length')"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.precision')" width="80" v-if="fieldRows.some(r => r.dataType === 'DECIMAL')">
            <template #default="{ row }">
              <el-input-number
                v-model="row.precisionValue"
                size="small"
                :min="1"
                :max="38"
                controls-position="right"
                :aria-label="$t('lowcode.entity.aria.precision')"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.scale')" width="80" v-if="fieldRows.some(r => r.dataType === 'DECIMAL')">
            <template #default="{ row }">
              <el-input-number
                v-model="row.scaleValue"
                size="small"
                :min="0"
                :max="row.precisionValue || 38"
                controls-position="right"
                :aria-label="$t('lowcode.entity.aria.scale')"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.dictType')" width="140" v-if="fieldRows.some(r => r.dataType === 'DICT')">
            <template #default="{ row }">
              <el-input
                v-model="row.dictType"
                size="small"
                :placeholder="$t('lowcode.entity.placeholder.dictTypeRequired')"
                :disabled="row.dataType !== 'DICT'"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.defaultValue')" width="120">
            <template #default="{ row }">
              <el-input v-model="row.defaultValue" size="small" :placeholder="$t('lowcode.entity.placeholder.optional')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.nullable')" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.nullable" :disabled="row.primaryFlag" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.primaryKey')" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox
                v-model="row.primaryFlag"
                @change="handlePrimaryFlagChange(row as FieldRow)"
                :aria-label="$t('lowcode.entity.aria.primaryKey')"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.unique')" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.uniqueFlag" :disabled="row.primaryFlag" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.index')" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.indexFlag" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.sortNo')" width="70" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.sortNo"
                size="small"
                :min="1"
                controls-position="right"
                :aria-label="$t('lowcode.entity.aria.sortNo')"
              />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.operation')" width="80" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="danger"
                size="small"
                :icon="Close"
                @click="handleRemoveField(row as FieldRow)"
                :disabled="row.primaryFlag"
                :aria-label="$t('lowcode.entity.aria.removeField')"
              />
            </template>
          </el-table-column>
        </el-table>
        <div v-if="fieldRows.length === 0" class="empty-tip">{{ $t('lowcode.entity.tip.addPrimaryKey') }}</div>

        <!-- 关系配置 -->
        <div class="section-title" style="margin-top: 16px">
          <span>{{ $t('lowcode.entity.section.relations') }}</span>
          <el-button size="small" type="primary" plain :icon="Plus" @click="handleAddRelation">
            {{ $t('lowcode.entity.action.addRelation') }}
          </el-button>
        </div>
        <el-table :data="relationRows" border size="small" :aria-label="$t('lowcode.entity.aria.relationTable')" max-height="200">
          <el-table-column :label="$t('lowcode.entity.field.index')" type="index" width="50" align="center" />
          <el-table-column :label="$t('lowcode.entity.field.targetEntityId')" width="200">
            <template #default="{ row }">
              <el-input v-model="row.targetEntityId" size="small" :placeholder="$t('lowcode.entity.placeholder.targetEntityIdExample')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.relationType')" width="160">
            <template #default="{ row }">
              <el-select v-model="row.relationType" size="small" :aria-label="$t('lowcode.entity.aria.relationType')">
                <el-option :label="$t('lowcode.entity.option.oneToOne')" :value="LC_RELATION_TYPE.ONE_TO_ONE" />
                <el-option :label="$t('lowcode.entity.option.oneToMany')" :value="LC_RELATION_TYPE.ONE_TO_MANY" />
                <el-option :label="$t('lowcode.entity.option.manyToOne')" :value="LC_RELATION_TYPE.MANY_TO_ONE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.sourceFieldCode')" width="160">
            <template #default="{ row }">
              <el-input v-model="row.sourceFieldCode" size="small" :placeholder="$t('lowcode.entity.placeholder.sourceFieldExample')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.targetFieldCode')" width="160">
            <template #default="{ row }">
              <el-input v-model="row.targetFieldCode" size="small" :placeholder="$t('lowcode.entity.placeholder.targetFieldExample')" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.cascadePolicy')" width="140">
            <template #default="{ row }">
              <el-select v-model="row.cascadePolicy" size="small" :aria-label="$t('lowcode.entity.aria.cascadePolicy')">
                <el-option label="CASCADE" :value="LC_CASCADE_POLICY.CASCADE" />
                <el-option label="SET_NULL" :value="LC_CASCADE_POLICY.SET_NULL" />
                <el-option label="RESTRICT" :value="LC_CASCADE_POLICY.RESTRICT" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.required')" width="60" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.required" />
            </template>
          </el-table-column>
          <el-table-column :label="$t('lowcode.entity.field.operation')" width="80" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="danger"
                size="small"
                :icon="Close"
                @click="handleRemoveRelation(row as RelationRow)"
                :aria-label="$t('lowcode.entity.aria.removeRelation')"
              />
            </template>
          </el-table-column>
        </el-table>
        <div v-if="relationRows.length === 0" class="empty-tip">{{ $t('lowcode.entity.tip.noRelations') }}</div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false" :disabled="saving">{{ $t('lowcode.entity.action.cancel') }}</el-button>
        <el-button
          v-permission="dialogModeIsEdit ? 'lc:entity:edit' : 'lc:entity:add'"
          type="primary"
          :loading="saving"
          @click="handleSave"
          :aria-label="$t('lowcode.entity.aria.saveDraft')"
        >
          {{ $t('lowcode.entity.action.saveDraft') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情查看对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="$t('lowcode.entity.dialog.detailTitle')"
      width="800px"
      :aria-label="$t('lowcode.entity.aria.detailDialog')"
    >
      <div v-loading="detailLoading">
        <template v-if="detailData">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item :label="$t('lowcode.entity.field.code')">{{ detailData.entityCode }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.name')">{{ detailData.entityName }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.tableName')">{{ detailData.tableName }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.module')">{{ detailData.moduleCode }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.status')">
              <el-tag :type="statusTagTypeMap[detailData.status] || 'info'" size="small">
                {{ statusLabelMap[detailData.status] || detailData.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.version')">{{ detailData.versionNo }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.schemaVersion')">
              {{ detailData.schemaVersion || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="config_hash">
              <code class="hash-full">{{ detailData.configHash || '-' }}</code>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.createdTime')">
              {{ formatTime(detailData.createdTime) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.publishedTime')" v-if="detailData.publishedTime">
              {{ formatTime(detailData.publishedTime) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title" style="margin-top: 16px">
            {{ $t('lowcode.entity.section.fieldList') }} ({{ detailData.fields?.length || 0 }})
          </div>
          <el-table :data="detailData.fields" border size="small" max-height="240">
            <el-table-column prop="fieldCode" :label="$t('lowcode.entity.field.fieldCode')" width="140" />
            <el-table-column prop="fieldName" :label="$t('lowcode.entity.field.fieldName')" width="140" />
            <el-table-column prop="dbColumn" :label="$t('lowcode.entity.field.columnName')" width="140" />
            <el-table-column prop="dataType" :label="$t('lowcode.entity.field.type')" width="100" />
            <el-table-column :label="$t('lowcode.entity.field.primaryKey')" width="60" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.primaryFlag" type="success" size="small">{{ $t('lowcode.entity.option.yes') }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('lowcode.entity.field.nullable')" width="60" align="center">
              <template #default="{ row }">{{ row.nullable ? $t('lowcode.entity.option.yes') : $t('lowcode.entity.option.no') }}</template>
            </el-table-column>
            <el-table-column prop="sortNo" :label="$t('lowcode.entity.field.sortNo')" width="60" align="center" />
          </el-table>

          <div class="section-title" style="margin-top: 16px">
            {{ $t('lowcode.entity.section.relationList') }} ({{ detailData.relations?.length || 0 }})
          </div>
          <el-table :data="detailData.relations" border size="small" max-height="200">
            <el-table-column prop="targetEntityId" :label="$t('lowcode.entity.field.targetEntity')" width="200" />
            <el-table-column prop="relationType" :label="$t('lowcode.entity.field.relationType')" width="140" />
            <el-table-column prop="sourceFieldCode" :label="$t('lowcode.entity.field.sourceField')" width="140" />
            <el-table-column prop="targetFieldCode" :label="$t('lowcode.entity.field.targetField')" width="140" />
            <el-table-column prop="cascadePolicy" :label="$t('lowcode.entity.field.cascadePolicy')" width="120" />
          </el-table>
        </template>
      </div>
    </el-dialog>

    <!--
      GA2-47: 版本历史抽屉。
      47 号文档验收标准: 实体设计器可完成字段 CRUD、发布、版本历史。
      v1.0: 展示当前实体已发布版本的快照信息 (versionNo + configHash + publishedTime + schemaVersion + 字段/关系快照)
      v1.1+: 后端补 listEntityVersions API 后, 改为完整版本时间线 (含 DRAFT/PUBLISHED/DISABLED 流转记录)
    -->
    <el-drawer
      v-model="versionDrawerVisible"
      :title="$t('lowcode.entity.dialog.versionHistory')"
      direction="rtl"
      size="640px"
      :aria-label="$t('lowcode.entity.aria.versionDrawer')"
    >
      <div v-loading="versionLoading">
        <template v-if="versionData">
          <!--
            v1.0 占位说明: 当前 API 仅返回最新版本快照。
            待后端补 listEntityVersions 后, 这里将替换为 el-timeline 列出全部历史版本。
          -->
          <el-alert
            type="info"
            :closable="false"
            :title="$t('lowcode.entity.alert.currentSnapshotTitle')"
            :description="$t('lowcode.entity.alert.currentSnapshotDesc')"
            show-icon
            style="margin-bottom: 12px"
          />

          <el-descriptions :column="1" border size="small">
            <el-descriptions-item :label="$t('lowcode.entity.field.code')">{{ versionData.entityCode }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.name')">{{ versionData.entityName }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.tableName')">{{ versionData.tableName }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.module')">{{ versionData.moduleCode }}</el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.status')">
              <el-tag :type="statusTagTypeMap[versionData.status] || 'info'" size="small">
                {{ statusLabelMap[versionData.status] || versionData.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.versionNoLabel')">
              <el-tag type="success" size="small">
                v{{ versionData.versionNo ?? 0 }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.schemaVersion')">
              <code class="hash-full">{{ versionData.schemaVersion || '-' }}</code>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.configHashLabel')">
              <code class="hash-full">{{ versionData.configHash || '-' }}</code>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.lockVersionLabel')">
              {{ versionData.version ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.createdTime')">
              {{ formatTime(versionData.createdTime) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.publishedTime')" v-if="versionData.publishedTime">
              <el-tag type="warning" size="small">
                {{ formatTime(versionData.publishedTime) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('lowcode.entity.field.updatedTime')" v-if="versionData.updatedTime">
              {{ formatTime(versionData.updatedTime) }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 字段快照 -->
          <div class="section-title" style="margin-top: 16px">
            {{ $t('lowcode.entity.section.fieldSnapshot') }} ({{ versionData.fields?.length || 0 }})
          </div>
          <el-table :data="versionData.fields" border size="small" max-height="240">
            <el-table-column prop="fieldCode" :label="$t('lowcode.entity.field.fieldCode')" width="140" />
            <el-table-column prop="fieldName" :label="$t('lowcode.entity.field.fieldName')" width="140" />
            <el-table-column prop="dbColumn" :label="$t('lowcode.entity.field.columnName')" width="140" />
            <el-table-column prop="dataType" :label="$t('lowcode.entity.field.type')" width="100" />
            <el-table-column :label="$t('lowcode.entity.field.primaryKey')" width="60" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.primaryFlag" type="success" size="small">{{ $t('lowcode.entity.option.yes') }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('lowcode.entity.field.nullable')" width="60" align="center">
              <template #default="{ row }">{{ row.nullable ? $t('lowcode.entity.option.yes') : $t('lowcode.entity.option.no') }}</template>
            </el-table-column>
            <el-table-column prop="sortNo" :label="$t('lowcode.entity.field.sortNo')" width="60" align="center" />
          </el-table>

          <!-- 关系快照 -->
          <div class="section-title" style="margin-top: 16px">
            {{ $t('lowcode.entity.section.relationSnapshot') }} ({{ versionData.relations?.length || 0 }})
          </div>
          <el-table :data="versionData.relations" border size="small" max-height="200">
            <el-table-column prop="targetEntityId" :label="$t('lowcode.entity.field.targetEntity')" width="200" />
            <el-table-column prop="relationType" :label="$t('lowcode.entity.field.relationType')" width="140" />
            <el-table-column prop="sourceFieldCode" :label="$t('lowcode.entity.field.sourceField')" width="140" />
            <el-table-column prop="targetFieldCode" :label="$t('lowcode.entity.field.targetField')" width="140" />
            <el-table-column prop="cascadePolicy" :label="$t('lowcode.entity.field.cascadePolicy')" width="120" />
          </el-table>

          <!-- v1.1+ 路线图 -->
          <div class="version-roadmap">
            <div class="section-title">{{ $t('lowcode.entity.section.roadmap') }}</div>
            <ul class="roadmap-list">
              <li>{{ $t('lowcode.entity.roadmap.item1') }}</li>
              <li>{{ $t('lowcode.entity.roadmap.item2') }}</li>
              <li>{{ $t('lowcode.entity.roadmap.item3') }}</li>
            </ul>
          </div>
        </template>

        <!-- 空状态 -->
        <el-empty v-else-if="!versionLoading" :description="$t('lowcode.entity.tip.noVersions')" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.lc-entity-page {
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

/* GA2-47: 版本历史抽屉 v1.1+ 路线图样式 */
.version-roadmap {
  margin-top: 24px;
  padding: 12px 16px;
  background-color: #f5f7fa;
  border-left: 3px solid var(--el-color-info);
  border-radius: 4px;
}

.roadmap-list {
  margin: 8px 0 0 0;
  padding-left: 20px;
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}

.roadmap-list li {
  list-style: disc;
}

.roadmap-list code {
  font-family: 'Courier New', monospace;
  font-size: 11px;
  background-color: #ebeef5;
  padding: 1px 4px;
  border-radius: 2px;
}
</style>
