<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, View } from '@element-plus/icons-vue'
import { track } from '@/utils/tracker'
import {
  getDictTypes,
  createDictType,
  updateDictType,
  deleteDictType,
} from '@/api/dict'
import type { DictType, PageRequest } from '@/api/types'

// GA2-25: 字典类型管理。字段对齐后端 DictType domain (dictType/dictName/status/sortNo/systemFlag)。
// 设计来源: 10-Vue3管理端设计、16-原型与交互体验设计、17-平台基础能力详细设计。

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const tableData = ref<DictType[]>([])
const total = ref(0)

const query = reactive<PageRequest & { keyword?: string }>({
  page: 1,
  size: 10,
  keyword: '',
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
// 表单初始值
const EMPTY_FORM: Partial<DictType> = {
  dictType: '',
  dictName: '',
  status: 'ENABLED',
  systemFlag: false,
  sortNo: 0,
}
const form = ref<Partial<DictType>>({ ...EMPTY_FORM })

async function fetchData() {
  loading.value = true
  try {
    const res = await getDictTypes(query)
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
  query.keyword = ''
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

function handleAdd() {
  isEdit.value = false
  form.value = { ...EMPTY_FORM }
  dialogVisible.value = true
  track('web.dict_type.create.click', {})
}

function handleEdit(row: DictType) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
  track('web.dict_type.update.click', { bizId: row.id })
}

async function handleDelete(row: DictType) {
  try {
    await ElMessageBox.confirm(
      t('dict.msg.typeDeleteConfirm', { name: row.dictName }),
      t('dict.msg.deleteConfirmTitle'),
      { type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  await deleteDictType(row.id)
  ElMessage.success(t('dict.msg.deleteSuccess'))
  track('web.dict_type.delete.success', { bizId: row.id })
  fetchData()
}

async function handleSave() {
  // 基础校验
  if (!form.value.dictType || !form.value.dictType.trim()) {
    ElMessage.warning(t('dict.msg.typeCodeRequired'))
    return
  }
  if (!form.value.dictName || !form.value.dictName.trim()) {
    ElMessage.warning(t('dict.msg.typeNameRequired'))
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateDictType(form.value.id!, form.value)
      ElMessage.success(t('dict.msg.updateSuccess'))
      track('web.dict_type.update.success', { bizId: form.value.id })
    } else {
      await createDictType(form.value)
      ElMessage.success(t('dict.msg.createSuccess'))
      track('web.dict_type.create.success', {})
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

function viewItems(row: DictType) {
  router.push({ path: '/system/dict-items', query: { type: row.dictType } })
}

function statusTagType(status: string): 'success' | 'info' {
  return status === 'ENABLED' ? 'success' : 'info'
}

function statusLabel(status: string): string {
  return status === 'ENABLED' ? t('dict.msg.statusEnabled') : t('dict.msg.statusDisabled')
}

onMounted(fetchData)
</script>

<template>
  <div class="dict-type-view">
    <h2 class="page-title">{{ $t('system.dict.page.typeView') }}</h2>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          :placeholder="$t('dict.type.placeholder.name')"
          clearable
          style="width: 240px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        <!-- GA2-25: 新增按钮按 system:dict:add 权限码隐藏 (96 号文档字典权限矩阵) -->
        <el-button v-permission="'system:dict:add'" type="success" :icon="Plus" @click="handleAdd">
          {{ $t('dict.type.action.create') }}
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('dict.type.empty.noData')"
      >
        <el-table-column prop="dictType" :label="$t('dict.type.field.code')" min-width="160" />
        <el-table-column prop="dictName" :label="$t('dict.type.field.name')" min-width="160" />
        <el-table-column prop="status" :label="$t('dict.type.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" :label="$t('dict.type.field.sort')" width="80" align="center" />
        <el-table-column prop="systemFlag" :label="$t('dict.type.field.system')" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.systemFlag" type="warning" size="small">{{ $t('dict.type.tag.system') }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('dict.type.field.createdTime')" width="180" />
        <el-table-column :label="$t('dict.type.field.operation')" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" size="small" @click="viewItems(row as DictType)">{{ $t('system.dict.page.items') }}</el-button>
            <el-button v-permission="'system:dict:edit'" type="primary" link :icon="Edit" size="small" @click="handleEdit(row as DictType)">{{ $t('common.action.edit') }}</el-button>
            <el-button
              v-permission="'system:dict:delete'"
              type="danger"
              link
              :icon="Delete"
              size="small"
              :disabled="(row as DictType).systemFlag"
              @click="handleDelete(row as DictType)"
            >{{ $t('common.action.delete') }}</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog
      :title="isEdit ? $t('dict.type.dialog.titleEdit') : $t('dict.type.dialog.titleCreate')"
      v-model="dialogVisible"
      width="520px"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item :label="$t('dict.type.field.code')" required>
          <el-input
            v-model="form.dictType"
            :placeholder="$t('dict.type.placeholder.codeExample')"
            :disabled="isEdit"
          />
        </el-form-item>
        <el-form-item :label="$t('dict.type.field.name')" required>
          <el-input v-model="form.dictName" :placeholder="$t('dict.type.placeholder.nameExample')" />
        </el-form-item>
        <el-form-item :label="$t('dict.type.field.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="$t('dict.type.option.enabled')" value="ENABLED" />
            <el-option :label="$t('dict.type.option.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('dict.type.field.sort')">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="$t('dict.type.field.systemFlag')">
          <el-switch v-model="form.systemFlag" :disabled="isEdit" />
          <span style="margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px">
            {{ $t('dict.type.tip.systemFlag') }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">{{ $t('common.action.confirm') }}</el-button>
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
</style>
