<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCodeTemplates,
  deleteCodeTemplate,
} from '@/api/code-template'
import type { CodeTemplate } from '@/api/code-template'

const router = useRouter()

const { t } = useI18n()

/**
 * 代码生成模板管理页面（45 号文档「插件与模板生态设计」E0）
 * 功能：代码模板增删改查、分类筛选、跳转编辑
 */

const loading = ref(false)
const tableData = ref<CodeTemplate[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const filters = reactive({
  keyword: '',
  category: '',
  engineType: '',
  status: '',
})

const categoryOptions = [
  { value: 'ENTITY', label: 'Entity' },
  { value: 'CONTROLLER', label: 'Controller' },
  { value: 'SERVICE', label: 'Service' },
  { value: 'MAPPER', label: 'Mapper' },
  { value: 'VUE_LIST', label: 'Vue List' },
  { value: 'VUE_FORM', label: 'Vue Form' },
  { value: 'DDL', label: 'DDL' },
]

const engineOptions = [
  { value: 'FREEMARKER', label: 'FreeMarker' },
  { value: 'VELOCITY', label: 'Velocity' },
]

const statusOptions = [
  { value: 'ACTIVE', label: t('plugin.codeTemplate.status.active') },
  { value: 'INACTIVE', label: t('plugin.codeTemplate.status.inactive') },
]

const engineMap: Record<string, string> = {
  FREEMARKER: 'FreeMarker',
  VELOCITY: 'Velocity',
}

const statusMap: Record<string, string> = {
  ACTIVE: t('plugin.codeTemplate.status.active'),
  INACTIVE: t('plugin.codeTemplate.status.inactive'),
}

/** 加载列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getCodeTemplates({
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      category: filters.category || undefined,
      engineType: filters.engineType || undefined,
      status: filters.status || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('plugin.codeTemplate.msg.loadListFailed'))
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
  filters.engineType = ''
  filters.status = ''
  handleSearch()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/** 新建 */
function handleCreate() {
  router.push({ name: 'CodeTemplateEdit' })
}

/** 编辑 */
function handleEdit(row: CodeTemplate) {
  router.push({ name: 'CodeTemplateEdit', query: { id: row.id } })
}

/** 删除 */
async function handleDelete(row: CodeTemplate) {
  try {
    await ElMessageBox.confirm(
      t('plugin.codeTemplate.msg.deleteConfirm', { name: row.templateName }),
      t('plugin.codeTemplate.msg.deleteConfirmTitle'),
      { type: 'warning', confirmButtonText: t('plugin.codeTemplate.msg.deleteConfirmButton'), cancelButtonText: t('plugin.codeTemplate.msg.cancelButton') }
    )
  } catch {
    return
  }
  try {
    await deleteCodeTemplate(row.id)
    ElMessage.success(t('plugin.codeTemplate.msg.deleteSuccess'))
    await loadData()
  } catch (e) {
    ElMessage.error(t('plugin.codeTemplate.msg.deleteFailed'))
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
  <div class="code-template-page">
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('plugin.codeTemplate.field.keyword')">
          <el-input v-model="filters.keyword" :placeholder="$t('plugin.codeTemplate.placeholder.keyword')" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item :label="$t('plugin.codeTemplate.field.category')">
          <el-select v-model="filters.category" :placeholder="$t('plugin.codeTemplate.placeholder.all')" clearable style="width: 140px">
            <el-option v-for="o in categoryOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plugin.codeTemplate.field.engine')">
          <el-select v-model="filters.engineType" :placeholder="$t('plugin.codeTemplate.placeholder.all')" clearable style="width: 140px">
            <el-option v-for="o in engineOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plugin.codeTemplate.field.status')">
          <el-select v-model="filters.status" :placeholder="$t('plugin.codeTemplate.placeholder.all')" clearable style="width: 120px">
            <el-option v-for="o in statusOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('plugin.codeTemplate.action.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('plugin.codeTemplate.action.reset') }}</el-button>
          <el-button type="success" @click="handleCreate">{{ $t('plugin.codeTemplate.action.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <el-table :data="tableData" border stripe>
        <el-table-column prop="templateCode" :label="$t('plugin.codeTemplate.field.code')" width="160" />
        <el-table-column prop="templateName" :label="$t('plugin.codeTemplate.field.name')" min-width="160" />
        <el-table-column :label="$t('plugin.codeTemplate.field.category')" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('plugin.codeTemplate.field.engine')" width="120" align="center">
          <template #default="{ row }">
            {{ engineMap[row.engineType] || row.engineType }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('plugin.codeTemplate.field.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ statusMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tags" :label="$t('plugin.codeTemplate.field.tags')" width="120" show-overflow-tooltip />
        <el-table-column prop="updatedTime" :label="$t('plugin.codeTemplate.field.updatedTime')" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('plugin.codeTemplate.field.operation')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleEdit(row as CodeTemplate)">{{ $t('plugin.codeTemplate.action.edit') }}</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row as CodeTemplate)">{{ $t('plugin.codeTemplate.action.delete') }}</el-button>
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
  </div>
</template>

<style scoped>
.code-template-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.filter-card :deep(.el-card__body) {
  padding: 16px 20px 0 20px;
}
</style>
