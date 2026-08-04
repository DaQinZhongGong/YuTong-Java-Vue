<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { track } from '@/utils/tracker'
import {
  getDictTypes,
  getDictItems,
  createDictItem,
  updateDictItem,
  deleteDictItem,
} from '@/api/dict'
import type { DictType, DictItem, PageRequest } from '@/api/types'

// GA2-25: 字典项管理。字段对齐后端 DictItem domain (dictType/itemCode/itemLabel/itemValue/sortNo/status)。
// 支持: 按类型过滤 + CRUD + 状态标签 + 权限码。
// 设计来源: 10-Vue3管理端设计、16-原型与交互体验设计、17-平台基础能力详细设计。

const { t } = useI18n()
const route = useRoute()

const loading = ref(false)
const tableData = ref<DictItem[]>([])
const total = ref(0)
const dictTypes = ref<DictType[]>([])
// ADR-002: 优先读取契约 path 参数 route.params.dictType（/system/dict-types/:dictType/items），
// 回退旧路由查询参数 route.query.type（/system/dict-items?type=xxx），保持向后兼容
const currentDictType = ref<string>(
  (route.params.dictType as string) || (route.query.type as string) || ''
)

const query = reactive<PageRequest & { dictType?: string }>({
  page: 1,
  size: 10,
  dictType: currentDictType.value || undefined,
})

const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const EMPTY_FORM: Partial<DictItem> = {
  dictType: '',
  itemCode: '',
  itemLabel: '',
  itemValue: '',
  status: 'ENABLED',
  sortNo: 0,
  colorToken: '',
}
const form = ref<Partial<DictItem>>({ ...EMPTY_FORM })

async function fetchDictTypes() {
  try {
    const res = await getDictTypes({ page: 1, size: 200 })
    dictTypes.value = res.records || []
  } catch {
    dictTypes.value = []
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getDictItems(query)
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
  form.value = { ...EMPTY_FORM, dictType: currentDictType.value }
  dialogVisible.value = true
  track('web.dict_item.create.click', {})
}

function handleEdit(row: DictItem) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
  track('web.dict_item.update.click', { bizId: row.id })
}

async function handleDelete(row: DictItem) {
  try {
    await ElMessageBox.confirm(t('dict.msg.deleteConfirm', { label: row.itemLabel }), t('dict.msg.deleteConfirmTitle'), {
      type: 'warning',
    })
  } catch {
    return
  }
  await deleteDictItem(row.id)
  ElMessage.success(t('dict.msg.deleteSuccess'))
  track('web.dict_item.delete.success', { bizId: row.id })
  fetchData()
}

async function handleSave() {
  if (!form.value.dictType) {
    ElMessage.warning(t('dict.msg.dictTypeRequired'))
    return
  }
  if (!form.value.itemCode || !form.value.itemCode.trim()) {
    ElMessage.warning(t('dict.msg.codeRequired'))
    return
  }
  if (!form.value.itemLabel || !form.value.itemLabel.trim()) {
    ElMessage.warning(t('dict.msg.labelRequired'))
    return
  }
  if (!form.value.itemValue && form.value.itemValue !== '0') {
    ElMessage.warning(t('dict.msg.valueRequired'))
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateDictItem(form.value.id!, form.value)
      ElMessage.success(t('dict.msg.updateSuccess'))
      track('web.dict_item.update.success', { bizId: form.value.id })
    } else {
      await createDictItem(form.value)
      ElMessage.success(t('dict.msg.createSuccess'))
      track('web.dict_item.create.success', {})
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

function statusTagType(status: string): 'success' | 'info' {
  return status === 'ENABLED' ? 'success' : 'info'
}

function statusLabel(status: string): string {
  return status === 'ENABLED' ? t('dict.msg.statusEnabled') : t('dict.msg.statusDisabled')
}

watch(
  // ADR-002: 同时监听 path 参数 dictType 与查询参数 type，兼容新旧两种入口
  () => [route.params.dictType, route.query.type],
  ([pType, qType]) => {
    const next = (pType as string) || (qType as string) || ''
    currentDictType.value = next
    query.dictType = next || undefined
    query.page = 1
    fetchData()
  }
)

onMounted(() => {
  fetchDictTypes()
  fetchData()
})
</script>

<template>
  <div class="dict-item-view">
    <div class="page-header">
      <h2 class="page-title">{{ $t('dict.item.page.title') }}</h2>
      <el-button :icon="ArrowLeft" link @click="$router.push('/system/dict-types')">
        {{ $t('dict.item.action.backToType') }}
      </el-button>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select
          v-model="query.dictType"
          :placeholder="$t('dict.item.placeholder.typeFilter')"
          clearable
          style="width: 240px"
          @change="handleSearch"
        >
          <el-option
            v-for="t in dictTypes"
            :key="t.id"
            :label="t.dictName"
            :value="t.dictType"
          />
        </el-select>
        <el-button type="primary" @click="handleSearch">{{ $t('dict.item.action.query') }}</el-button>
        <el-button
          v-permission="'system:dict:add'"
          type="success"
          :icon="Plus"
          @click="handleAdd"
        >
          {{ $t('dict.item.action.create') }}
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('dict.item.empty.noData')"
      >
        <el-table-column prop="dictType" :label="$t('dict.item.field.belongType')" min-width="140" />
        <el-table-column prop="itemCode" :label="$t('dict.item.field.code')" min-width="140" />
        <el-table-column prop="itemLabel" :label="$t('dict.item.field.label')" min-width="140" />
        <el-table-column prop="itemValue" :label="$t('dict.item.field.value')" min-width="120" />
        <el-table-column prop="sortNo" :label="$t('dict.item.field.sort')" width="80" align="center" />
        <el-table-column prop="status" :label="$t('dict.item.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('dict.item.field.createdTime')" width="180" />
        <el-table-column :label="$t('dict.item.field.operation')" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:dict:edit'"
              type="primary"
              link
              :icon="Edit"
              size="small"
              @click="handleEdit(row as DictItem)"
            >
              {{ $t('dict.item.action.edit') }}
            </el-button>
            <el-button
              v-permission="'system:dict:delete'"
              type="danger"
              link
              :icon="Delete"
              size="small"
              @click="handleDelete(row as DictItem)"
            >
              {{ $t('dict.item.action.delete') }}
            </el-button>
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

    <el-dialog
      :title="isEdit ? $t('dict.item.dialog.titleEdit') : $t('dict.item.dialog.titleCreate')"
      v-model="dialogVisible"
      width="520px"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item :label="$t('dict.item.field.dictType')" required>
          <el-select v-model="form.dictType" :placeholder="$t('dict.item.placeholder.dictType')" style="width: 100%">
            <el-option
              v-for="t in dictTypes"
              :key="t.id"
              :label="t.dictName"
              :value="t.dictType"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('dict.item.field.code')" required>
          <el-input v-model="form.itemCode" :placeholder="$t('dict.item.placeholder.codeExample')" :disabled="isEdit" />
        </el-form-item>
        <el-form-item :label="$t('dict.item.field.label')" required>
          <el-input v-model="form.itemLabel" :placeholder="$t('dict.item.placeholder.labelExample')" />
        </el-form-item>
        <el-form-item :label="$t('dict.item.field.value')" required>
          <el-input v-model="form.itemValue" :placeholder="$t('dict.item.placeholder.valueExample')" />
        </el-form-item>
        <el-form-item :label="$t('dict.item.field.status')">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="$t('dict.item.option.enabled')" value="ENABLED" />
            <el-option :label="$t('dict.item.option.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('dict.item.field.sort')">
          <el-input-number v-model="form.sortNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="$t('dict.item.field.colorToken')">
          <el-input v-model="form.colorToken" :placeholder="$t('dict.item.placeholder.colorToken')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('dict.item.action.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSave">{{ $t('dict.item.action.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-title {
  margin: 0;
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
