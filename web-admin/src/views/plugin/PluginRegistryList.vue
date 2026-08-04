<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPluginRegistries,
  createPluginRegistry,
  updatePluginRegistry,
  deletePluginRegistry,
} from '@/api/plugin-registry'
import type { PluginRegistry } from '@/api/plugin-registry'

/**
 * 插件注册表管理页面（45 号文档「插件与模板生态设计」E0）
 * 功能：内置插件元数据的增删改查、分类筛选
 */

const loading = ref(false)
const tableData = ref<PluginRegistry[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const filters = reactive({
  keyword: '',
  pluginType: '',
  status: '',
})

// 编辑对话框
const editVisible = ref(false)
const editForm = reactive<Partial<PluginRegistry>>({
  pluginCode: '',
  pluginName: '',
  pluginVersion: '1.0.0',
  pluginType: 'LOWCODE_COMPONENT',
  status: 'ACTIVE',
  description: '',
  entryClass: '',
  iconUrl: '',
  tags: '',
})
const isEdit = ref(false)
const editId = ref('')
const editLoading = ref(false)

const { t } = useI18n()

const typeOptions = [
  { value: 'TEMPLATE', label: t('plugin.registry.type.template') },
  { value: 'LOWCODE_COMPONENT', label: t('plugin.registry.type.lowcodeComponent') },
]

const statusOptions = [
  { value: 'ACTIVE', label: t('plugin.registry.status.active') },
  { value: 'INACTIVE', label: t('plugin.registry.status.inactive') },
  { value: 'DEPRECATED', label: t('plugin.registry.status.deprecated') },
]

const typeMap: Record<string, string> = {
  TEMPLATE: t('plugin.registry.type.template'),
  LOWCODE_COMPONENT: t('plugin.registry.type.lowcodeComponent'),
}

const statusMap: Record<string, string> = {
  ACTIVE: t('plugin.registry.status.active'),
  INACTIVE: t('plugin.registry.status.inactive'),
  DEPRECATED: t('plugin.registry.status.deprecated'),
}

/** 加载列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getPluginRegistries({
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      pluginType: filters.pluginType || undefined,
      status: filters.status || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('plugin.registry.msg.loadFailed'))
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
  filters.pluginType = ''
  filters.status = ''
  handleSearch()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/** 打开新建对话框 */
function handleCreate() {
  isEdit.value = false
  editId.value = ''
  Object.assign(editForm, {
    pluginCode: '',
    pluginName: '',
    pluginVersion: '1.0.0',
    pluginType: 'LOWCODE_COMPONENT',
    status: 'ACTIVE',
    description: '',
    entryClass: '',
    iconUrl: '',
    tags: '',
  })
  editVisible.value = true
}

/** 打开编辑对话框 */
function handleEdit(row: PluginRegistry) {
  isEdit.value = true
  editId.value = row.id
  Object.assign(editForm, {
    pluginCode: row.pluginCode,
    pluginName: row.pluginName,
    pluginVersion: row.pluginVersion,
    pluginType: row.pluginType,
    status: row.status,
    description: row.description,
    entryClass: row.entryClass,
    iconUrl: row.iconUrl,
    tags: row.tags,
  })
  editVisible.value = true
}

/** 提交保存 */
async function handleSubmit() {
  if (!editForm.pluginCode || !editForm.pluginName) {
    ElMessage.warning(t('plugin.registry.msg.formIncomplete'))
    return
  }
  editLoading.value = true
  try {
    const payload = {
      pluginCode: editForm.pluginCode!,
      pluginName: editForm.pluginName!,
      pluginVersion: editForm.pluginVersion || '1.0.0',
      pluginType: editForm.pluginType!,
      status: editForm.status!,
      description: editForm.description,
      entryClass: editForm.entryClass,
      iconUrl: editForm.iconUrl,
      tags: editForm.tags,
    } as any
    if (isEdit.value) {
      payload.version = tableData.value.find(r => r.id === editId.value)?.version
      await updatePluginRegistry(editId.value, payload)
      ElMessage.success(t('plugin.registry.msg.updateSuccess'))
    } else {
      await createPluginRegistry(payload)
      ElMessage.success(t('plugin.registry.msg.createSuccess'))
    }
    editVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error(isEdit.value ? t('plugin.registry.msg.updateFailed') : t('plugin.registry.msg.createFailed'))
  } finally {
    editLoading.value = false
  }
}

/** 删除 */
async function handleDelete(row: PluginRegistry) {
  try {
    await ElMessageBox.confirm(
      t('plugin.registry.msg.deleteConfirm', { name: row.pluginName }),
      t('plugin.registry.msg.deleteConfirmTitle'),
      { type: 'warning', confirmButtonText: t('plugin.registry.msg.deleteConfirmButton'), cancelButtonText: t('plugin.registry.msg.cancelButton') }
    )
  } catch {
    return
  }
  try {
    await deletePluginRegistry(row.id)
    ElMessage.success(t('plugin.registry.msg.deleteSuccess'))
    await loadData()
  } catch (e) {
    ElMessage.error(t('plugin.registry.msg.deleteFailed'))
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
  <div class="plugin-registry-page">
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('plugin.registry.field.keyword')">
          <el-input v-model="filters.keyword" :placeholder="$t('plugin.registry.placeholder.keyword')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.type')">
          <el-select v-model="filters.pluginType" :placeholder="$t('plugin.registry.placeholder.all')" clearable style="width: 140px">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.status')">
          <el-select v-model="filters.status" :placeholder="$t('plugin.registry.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('plugin.registry.action.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('plugin.registry.action.reset') }}</el-button>
          <el-button type="success" @click="handleCreate">{{ $t('plugin.registry.action.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-table :data="tableData" border stripe>
        <el-table-column prop="pluginCode" :label="$t('plugin.registry.field.code')" width="160" />
        <el-table-column prop="pluginName" :label="$t('plugin.registry.field.name')" min-width="160" />
        <el-table-column prop="pluginVersion" :label="$t('plugin.registry.field.version')" width="90" align="center" />
        <el-table-column :label="$t('plugin.registry.field.type')" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ typeMap[row.pluginType] || row.pluginType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('plugin.registry.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'ACTIVE' ? 'success' : row.status === 'DEPRECATED' ? 'info' : 'warning'"
              size="small"
            >
              {{ statusMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tags" :label="$t('plugin.registry.field.tags')" width="140" show-overflow-tooltip />
        <el-table-column prop="updatedTime" :label="$t('plugin.registry.field.updatedTime')" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('plugin.registry.field.operation')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row as PluginRegistry)">{{ $t('plugin.registry.action.edit') }}</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as PluginRegistry)">{{ $t('plugin.registry.action.delete') }}</el-button>
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
      v-model="editVisible"
      :title="isEdit ? $t('plugin.registry.dialog.editTitle') : $t('plugin.registry.dialog.createTitle')"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="editForm" label-width="100px">
        <el-form-item :label="$t('plugin.registry.field.code')" required>
          <el-input v-model="editForm.pluginCode" :placeholder="$t('plugin.registry.placeholder.code')" :disabled="isEdit" />
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.name')" required>
          <el-input v-model="editForm.pluginName" :placeholder="$t('plugin.registry.placeholder.name')" />
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.version')">
          <el-input v-model="editForm.pluginVersion" placeholder="1.0.0" />
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.type')" required>
          <el-select v-model="editForm.pluginType" :placeholder="$t('plugin.registry.placeholder.selectType')" style="width: 100%">
            <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.status')">
          <el-select v-model="editForm.status" :placeholder="$t('plugin.registry.placeholder.selectStatus')" style="width: 100%">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.entryClass')">
          <el-input v-model="editForm.entryClass" placeholder="com.example.Component" />
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.iconUrl')">
          <el-input v-model="editForm.iconUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.tags')">
          <el-input v-model="editForm.tags" :placeholder="$t('plugin.registry.placeholder.tags')" />
        </el-form-item>
        <el-form-item :label="$t('plugin.registry.field.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="3" :placeholder="$t('plugin.registry.placeholder.description')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ $t('plugin.registry.action.cancel') }}</el-button>
        <el-button type="primary" :loading="editLoading" @click="handleSubmit">{{ $t('plugin.registry.action.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.plugin-registry-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.filter-card :deep(.el-card__body) {
  padding: 16px 20px 0 20px;
}
</style>
