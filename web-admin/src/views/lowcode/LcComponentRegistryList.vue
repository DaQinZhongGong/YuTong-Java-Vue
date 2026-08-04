<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Edit, Refresh, Delete, Check, Close } from '@element-plus/icons-vue'
import {
  createComponentRegistry,
  deleteComponentRegistry,
  disableComponentRegistry,
  getComponentRegistries,
  publishComponentRegistry,
  updateComponentRegistry,
} from '@/api/lowcode'
import {
  LC_COMPONENT_CATEGORY,
  LC_COMPONENT_CATEGORY_OPTIONS,
  LC_COMPONENT_GRADE,
  LC_COMPONENT_GRADE_OPTIONS,
  LC_COMPONENT_PLATFORM,
  LC_COMPONENT_PLATFORM_OPTIONS,
  LC_COMPONENT_STATUS,
  LC_COMPONENT_STATUS_OPTIONS,
} from '@/api/types'
import type {
  LcComponentRegistry,
  LcComponentRegistryPageQuery,
  PageResult,
  SaveLcComponentRegistryRequest,
} from '@/api/types'
import { track } from '@/utils/tracker'

const { t } = useI18n()

/**
 * 低代码组件协议注册表。设计来源: 36-低代码高级能力设计
 *
 * GA2-L191 功能落地:
 *  - 分页查询: keyword/category/platform/status 过滤
 *  - 新建/编辑: el-dialog 表单, 覆盖 SaveLcComponentRegistryRequest 全部字段
 *  - 发布: POST /{id}/publish (草稿 → 已发布)
 *  - 禁用: POST /{id}/disable (→ 已禁用)
 *  - 删除: DELETE /{id}
 *  - GA2-18 track() 埋点关键事件
 */

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const tableData = ref<LcComponentRegistry[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const filters = reactive({
  keyword: '',
  category: '',
  platform: '',
  status: '',
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const form = reactive<SaveLcComponentRegistryRequest>({
  id: undefined,
  version: undefined,
  componentCode: '',
  componentName: '',
  componentType: '',
  displayName: '',
  platform: LC_COMPONENT_PLATFORM.WEB,
  category: LC_COMPONENT_CATEGORY.INPUT,
  compatibilityGrade: LC_COMPONENT_GRADE.STABLE,
  propsSchema: '',
  eventSchema: '',
  dataBinding: '',
  permissionSupport: false,
  validationSupport: false,
  permissionCode: '',
  componentVersion: '1.0.0',
  minPlatformVersion: '',
  maxPlatformVersion: '',
  description: '',
  icon: '',
  status: LC_COMPONENT_STATUS.DRAFT,
  deprecated: false,
  deprecatedMessage: '',
  sortNo: 0,
  remark: '',
})

const dialogModeIsEdit = computed(() => !!form.id)

/* 标签映射表 */
const statusLabelMap: Record<string, string> = LC_COMPONENT_STATUS_OPTIONS.reduce(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {} as Record<string, string>
)
const statusTagTypeMap: Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'> =
  LC_COMPONENT_STATUS_OPTIONS.reduce(
    (m, o) => ({ ...m, [o.value]: o.tagType }),
    {} as Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'>
  )

const categoryLabelMap: Record<string, string> = LC_COMPONENT_CATEGORY_OPTIONS.reduce(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {} as Record<string, string>
)

const platformLabelMap: Record<string, string> = LC_COMPONENT_PLATFORM_OPTIONS.reduce(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {} as Record<string, string>
)

const gradeLabelMap: Record<string, string> = LC_COMPONENT_GRADE_OPTIONS.reduce(
  (m, o) => ({ ...m, [o.value]: o.label }),
  {} as Record<string, string>
)

/** 平台对应的 tag 颜色 */
const platformTagTypeMap: Record<string, '' | 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  [LC_COMPONENT_PLATFORM.WEB]: 'primary',
  [LC_COMPONENT_PLATFORM.MOBILE]: 'success',
  [LC_COMPONENT_PLATFORM.BOTH]: 'warning',
}

async function loadData() {
  loading.value = true
  try {
    track('web.lowcode.component.list.view', {
      payload: { page: currentPage.value, size: pageSize.value },
    })
    const params: LcComponentRegistryPageQuery = {
      page: currentPage.value,
      size: pageSize.value,
      keyword: filters.keyword || undefined,
      category: filters.category || undefined,
      platform: filters.platform || undefined,
      status: filters.status || undefined,
    }
    const res: PageResult<LcComponentRegistry> = await getComponentRegistries(params)
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('lowcode.msg.loadComponentListFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filters.keyword = ''
  filters.category = ''
  filters.platform = ''
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
  form.componentCode = ''
  form.componentName = ''
  form.componentType = ''
  form.displayName = ''
  form.platform = LC_COMPONENT_PLATFORM.WEB
  form.category = LC_COMPONENT_CATEGORY.INPUT
  form.compatibilityGrade = LC_COMPONENT_GRADE.STABLE
  form.propsSchema = ''
  form.eventSchema = ''
  form.dataBinding = ''
  form.permissionSupport = false
  form.validationSupport = false
  form.permissionCode = ''
  form.componentVersion = '1.0.0'
  form.minPlatformVersion = ''
  form.maxPlatformVersion = ''
  form.description = ''
  form.icon = ''
  form.status = LC_COMPONENT_STATUS.DRAFT
  form.deprecated = false
  form.deprecatedMessage = ''
  form.sortNo = 0
  form.remark = ''
}

function handleAdd() {
  resetForm()
  dialogTitle.value = t('lowcode.component.dialog.create')
  dialogVisible.value = true
  track('web.lowcode.component.create.click', {})
}

function handleEdit(row: LcComponentRegistry) {
  resetForm()
  dialogTitle.value = t('lowcode.component.dialog.edit')
  dialogVisible.value = true
  track('web.lowcode.component.update.click', { bizId: row.id })
  // 直接用列表行数据回填 (避免再发一次详情请求)
  form.id = row.id
  form.version = row.version
  form.componentCode = row.componentCode
  form.componentName = row.componentName
  form.componentType = row.componentType
  form.displayName = row.displayName
  form.platform = row.platform
  form.category = row.category
  form.compatibilityGrade = row.compatibilityGrade
  form.propsSchema = row.propsSchema ?? ''
  form.eventSchema = row.eventSchema ?? ''
  form.dataBinding = row.dataBinding ?? ''
  form.permissionSupport = row.permissionSupport
  form.validationSupport = row.validationSupport
  form.permissionCode = row.permissionCode ?? ''
  form.componentVersion = row.componentVersion
  form.minPlatformVersion = row.minPlatformVersion ?? ''
  form.maxPlatformVersion = row.maxPlatformVersion ?? ''
  form.description = row.description ?? ''
  form.icon = row.icon ?? ''
  form.status = row.status
  form.deprecated = row.deprecated
  form.deprecatedMessage = row.deprecatedMessage ?? ''
  form.sortNo = row.sortNo
  form.remark = row.remark ?? ''
}

async function handleSave() {
  if (!form.componentCode.trim()) {
    ElMessage.warning(t('lowcode.msg.pleaseInputComponentCode'))
    return
  }
  if (!form.componentName.trim()) {
    ElMessage.warning(t('lowcode.msg.pleaseInputComponentName'))
    return
  }
  if (!form.componentType.trim()) {
    ElMessage.warning(t('lowcode.msg.pleaseInputComponentType'))
    return
  }
  if (!form.displayName.trim()) {
    ElMessage.warning(t('lowcode.msg.pleaseInputDisplayName'))
    return
  }
  saving.value = true
  try {
    if (dialogModeIsEdit.value && form.id) {
      await updateComponentRegistry(form.id, form)
      ElMessage.success(t('lowcode.msg.componentUpdateSuccess'))
      track('web.lowcode.component.update.success', { bizId: form.id })
    } else {
      await createComponentRegistry(form)
      ElMessage.success(t('lowcode.msg.componentCreateSuccess'))
      track('web.lowcode.component.create.success', {})
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error(t('lowcode.msg.saveComponentFailed'))
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: LcComponentRegistry) {
  try {
    await ElMessageBox.confirm(
      t('lowcode.msg.confirmDeleteComponent', { name: row.componentName }),
      t('lowcode.msg.deleteConfirmTitle'),
      { type: 'error', confirmButtonText: t('lowcode.msg.confirmDeleteButton'), cancelButtonText: t('lowcode.msg.cancel') }
    )
  } catch {
    return
  }
  track('web.lowcode.component.delete.click', { bizId: row.id })
  try {
    await deleteComponentRegistry(row.id)
    ElMessage.success(t('lowcode.msg.componentDeleted'))
    track('web.lowcode.component.delete.success', { bizId: row.id })
    await loadData()
  } catch (e) {
    ElMessage.error(t('lowcode.msg.deleteFailed'))
  }
}

async function handlePublish(row: LcComponentRegistry) {
  if (row.status === LC_COMPONENT_STATUS.PUBLISHED) {
    ElMessage.warning(t('lowcode.msg.componentAlreadyPublished'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('lowcode.msg.confirmPublishComponent', { name: row.componentName }),
      t('lowcode.msg.publishConfirmTitle'),
      { type: 'warning', confirmButtonText: t('lowcode.msg.confirmPublishButton'), cancelButtonText: t('lowcode.msg.cancel') }
    )
  } catch {
    return
  }
  publishing.value = true
  track('web.lowcode.component.publish.click', { bizId: row.id })
  try {
    await publishComponentRegistry(row.id)
    ElMessage.success(t('lowcode.msg.componentPublishSuccess'))
    track('web.lowcode.component.publish.success', { bizId: row.id })
    await loadData()
  } catch (e) {
    ElMessage.error(t('lowcode.msg.publishFailed'))
  } finally {
    publishing.value = false
  }
}

async function handleDisable(row: LcComponentRegistry) {
  if (row.status === LC_COMPONENT_STATUS.DISABLED) {
    ElMessage.warning(t('lowcode.msg.componentAlreadyDisabled'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('lowcode.msg.confirmDisableComponent', { name: row.componentName }),
      t('lowcode.msg.disableConfirmTitle'),
      { type: 'error', confirmButtonText: t('lowcode.msg.confirmDisableButton'), cancelButtonText: t('lowcode.msg.cancel') }
    )
  } catch {
    return
  }
  track('web.lowcode.component.disable.click', { bizId: row.id })
  try {
    await disableComponentRegistry(row.id)
    ElMessage.success(t('lowcode.msg.componentDisabledSuccess'))
    track('web.lowcode.component.disable.success', { bizId: row.id })
    await loadData()
  } catch (e) {
    ElMessage.error(t('lowcode.msg.disableFailed'))
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

onMounted(loadData)
</script>

<template>
  <div class="lc-component-page">
    <!-- 顶部过滤栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('lowcode.component.field.keyword')">
          <el-input
            v-model="filters.keyword"
            :placeholder="$t('lowcode.component.placeholder.keyword')"
            clearable
            style="width: 200px"
            :aria-label="$t('lowcode.component.aria.filterKeyword')"
          />
        </el-form-item>
        <el-form-item :label="$t('lowcode.component.field.category')">
          <el-select
            v-model="filters.category"
            :placeholder="$t('lowcode.component.placeholder.all')"
            clearable
            style="width: 140px"
            :aria-label="$t('lowcode.component.aria.filterCategory')"
          >
            <el-option
              v-for="o in LC_COMPONENT_CATEGORY_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('lowcode.component.field.platform')">
          <el-select
            v-model="filters.platform"
            :placeholder="$t('lowcode.component.placeholder.all')"
            clearable
            style="width: 120px"
            :aria-label="$t('lowcode.component.aria.filterPlatform')"
          >
            <el-option
              v-for="o in LC_COMPONENT_PLATFORM_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('lowcode.component.field.status')">
          <el-select
            v-model="filters.status"
            :placeholder="$t('lowcode.component.placeholder.all')"
            clearable
            style="width: 120px"
            :aria-label="$t('lowcode.component.aria.filterStatus')"
          >
            <el-option
              v-for="o in LC_COMPONENT_STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Refresh" @click="handleSearch">{{ $t('lowcode.component.action.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('lowcode.component.action.reset') }}</el-button>
          <el-button
            v-permission="'lc:component:edit'"
            type="success"
            :icon="Plus"
            @click="handleAdd"
            :aria-label="$t('lowcode.component.aria.create')"
          >
            {{ $t('lowcode.component.action.create') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="tableData" border stripe :aria-label="$t('lowcode.component.aria.list')">
        <el-table-column prop="componentCode" :label="$t('lowcode.component.field.componentCode')" width="180" fixed="left" />
        <el-table-column prop="componentName" :label="$t('lowcode.component.field.componentName')" min-width="160" />
        <el-table-column prop="displayName" :label="$t('lowcode.component.field.displayName')" width="140" />
        <el-table-column :label="$t('lowcode.component.field.category')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">
              {{ categoryLabelMap[row.category] || row.category }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.component.field.platform')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="platformTagTypeMap[row.platform] || 'info'">
              {{ platformLabelMap[row.platform] || row.platform }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.component.field.compatibilityGrade')" width="100" align="center">
          <template #default="{ row }">
            {{ gradeLabelMap[row.compatibilityGrade] || row.compatibilityGrade }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.component.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagTypeMap[row.status] || 'info'" size="small">
              {{ statusLabelMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.component.field.deprecated')" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.deprecated" type="danger" size="small">{{ $t('lowcode.component.status.deprecated') }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="componentVersion" :label="$t('lowcode.component.field.version')" width="100" align="center" />
        <el-table-column prop="sortNo" :label="$t('lowcode.component.field.sortNo')" width="80" align="center" />
        <el-table-column prop="updatedTime" :label="$t('lowcode.component.field.updatedTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('lowcode.component.field.operation')" width="300" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'lc:component:edit'"
              link
              type="primary"
              size="small"
              :icon="Edit"
              @click="handleEdit(row as LcComponentRegistry)"
              :aria-label="$t('lowcode.component.aria.edit')"
            >
              {{ $t('lowcode.component.action.edit') }}
            </el-button>
            <el-button
              v-permission="'lc:component:publish'"
              link
              type="warning"
              size="small"
              :icon="Check"
              :disabled="row.status === 'PUBLISHED'"
              :loading="publishing"
              @click="handlePublish(row as LcComponentRegistry)"
              :aria-label="$t('lowcode.component.aria.publish')"
            >
              {{ $t('lowcode.component.action.publish') }}
            </el-button>
            <el-button
              v-permission="'lc:component:edit'"
              link
              type="info"
              size="small"
              :icon="Close"
              :disabled="row.status === 'DISABLED'"
              @click="handleDisable(row as LcComponentRegistry)"
              :aria-label="$t('lowcode.component.aria.disable')"
            >
              {{ $t('lowcode.component.action.disable') }}
            </el-button>
            <el-button
              v-permission="'lc:component:delete'"
              link
              type="danger"
              size="small"
              :icon="Delete"
              @click="handleDelete(row as LcComponentRegistry)"
              :aria-label="$t('lowcode.component.aria.delete')"
            >
              {{ $t('lowcode.component.action.delete') }}
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

    <!-- 编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="960px"
      :close-on-click-modal="false"
      :aria-label="$t('lowcode.component.aria.editorDialog')"
    >
      <el-form :model="form" label-width="120px" size="default">
        <div class="section-title">{{ $t('lowcode.component.section.basic') }}</div>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.componentCode')" required>
              <el-input
                v-model="form.componentCode"
                :placeholder="$t('lowcode.component.placeholder.componentCode')"
                :disabled="dialogModeIsEdit"
                :aria-label="$t('lowcode.component.aria.componentCode')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.componentName')" required>
              <el-input v-model="form.componentName" :placeholder="$t('lowcode.component.placeholder.componentName')" :aria-label="$t('lowcode.component.aria.componentName')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.componentType')" required>
              <el-input v-model="form.componentType" :placeholder="$t('lowcode.component.placeholder.componentType')" :aria-label="$t('lowcode.component.aria.componentType')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.displayName')" required>
              <el-input v-model="form.displayName" :placeholder="$t('lowcode.component.placeholder.displayName')" :aria-label="$t('lowcode.component.aria.displayName')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.category')">
              <el-select v-model="form.category" :aria-label="$t('lowcode.component.aria.category')" style="width: 100%">
                <el-option
                  v-for="o in LC_COMPONENT_CATEGORY_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.platform')">
              <el-select v-model="form.platform" :aria-label="$t('lowcode.component.aria.platform')" style="width: 100%">
                <el-option
                  v-for="o in LC_COMPONENT_PLATFORM_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.compatibilityGrade')">
              <el-select v-model="form.compatibilityGrade" :aria-label="$t('lowcode.component.aria.compatibilityGrade')" style="width: 100%">
                <el-option
                  v-for="o in LC_COMPONENT_GRADE_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.componentVersion')">
              <el-input v-model="form.componentVersion" :placeholder="$t('lowcode.component.placeholder.componentVersion')" :aria-label="$t('lowcode.component.aria.componentVersion')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.sortNo')">
              <el-input-number
                v-model="form.sortNo"
                :controls="false"
                :aria-label="$t('lowcode.component.aria.sortNo')"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.status')">
              <el-select v-model="form.status" :aria-label="$t('lowcode.component.aria.status')" style="width: 100%">
                <el-option
                  v-for="o in LC_COMPONENT_STATUS_OPTIONS"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <div class="section-title">{{ $t('lowcode.component.section.schema') }}</div>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="propsSchema">
              <el-input
                v-model="form.propsSchema"
                type="textarea"
                :rows="4"
                :placeholder="$t('lowcode.component.placeholder.propsSchema')"
                aria-label="propsSchema"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="eventSchema">
              <el-input
                v-model="form.eventSchema"
                type="textarea"
                :rows="4"
                :placeholder="$t('lowcode.component.placeholder.eventSchema')"
                aria-label="eventSchema"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="dataBinding">
              <el-input
                v-model="form.dataBinding"
                type="textarea"
                :rows="3"
                :placeholder="$t('lowcode.component.placeholder.dataBinding')"
                aria-label="dataBinding"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$t('lowcode.component.field.permissionSupport')">
              <el-switch v-model="form.permissionSupport" :aria-label="$t('lowcode.component.aria.permissionSupport')" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$t('lowcode.component.field.validationSupport')">
              <el-switch v-model="form.validationSupport" :aria-label="$t('lowcode.component.aria.validationSupport')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.permissionCode')">
              <el-input v-model="form.permissionCode" :placeholder="$t('lowcode.component.placeholder.permissionCode')" :aria-label="$t('lowcode.component.aria.permissionCode')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.minPlatformVersion')">
              <el-input v-model="form.minPlatformVersion" :placeholder="$t('lowcode.component.placeholder.minPlatformVersion')" :aria-label="$t('lowcode.component.aria.minPlatformVersion')" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('lowcode.component.field.maxPlatformVersion')">
              <el-input v-model="form.maxPlatformVersion" :placeholder="$t('lowcode.component.placeholder.maxPlatformVersion')" :aria-label="$t('lowcode.component.aria.maxPlatformVersion')" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$t('lowcode.component.field.icon')">
              <el-input v-model="form.icon" :placeholder="$t('lowcode.component.placeholder.icon')" :aria-label="$t('lowcode.component.aria.icon')" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item :label="$t('lowcode.component.field.deprecated')">
              <el-switch v-model="form.deprecated" :aria-label="$t('lowcode.component.aria.deprecated')" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.deprecated">
            <el-form-item :label="$t('lowcode.component.field.deprecatedMessage')">
              <el-input v-model="form.deprecatedMessage" :placeholder="$t('lowcode.component.placeholder.deprecatedMessage')" :aria-label="$t('lowcode.component.aria.deprecatedMessage')" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="$t('lowcode.component.field.description')">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="2"
                :placeholder="$t('lowcode.component.placeholder.description')"
                :aria-label="$t('lowcode.component.aria.description')"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item :label="$t('lowcode.component.field.remark')">
              <el-input v-model="form.remark" :placeholder="$t('lowcode.component.placeholder.remark')" :aria-label="$t('lowcode.component.aria.remark')" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('lowcode.component.action.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('lowcode.component.action.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.lc-component-page {
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
  color: var(--el-text-color-primary);
  border-left: 3px solid var(--el-color-primary);
  padding-left: 8px;
}
</style>
