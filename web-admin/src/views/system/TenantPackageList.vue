<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit } from '@element-plus/icons-vue'
import {
  getTenantPackages,
  saveTenantPackage,
  publishTenantPackage,
  archiveTenantPackage,
  type TenantPackagePageQuery,
} from '@/api/tenantPackage'
import type { SysTenantPackage, SaveTenantPackage } from '@/api/types'
import i18n from '@/locales'

/**
 * 租户套餐管理页。设计来源: 70-商业授权与版本能力裁剪、ADR 0004 P2-F 批次 6-B。
 * 状态机 DRAFT → ACTIVE → ARCHIVED, packageCode 全局唯一 (大写字母开头)。
 */

const loading = ref(false)
const tableData = ref<SysTenantPackage[]>([])
const total = ref(0)

const query = reactive<TenantPackagePageQuery>({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getTenantPackages(query)
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
  query.status = ''
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

function statusTagType(status?: string) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

function formatPrice(row: SysTenantPackage) {
  if (row.priceCnyPerPeriod === undefined || row.priceCnyPerPeriod === null) return '-'
  const period = row.periodMonths ? ` / ${row.periodMonths}${i18n.global.t('system.tenantPackage.unit.month')}` : ''
  return `¥${row.priceCnyPerPeriod}${period}`
}

// ===== 新建/编辑对话框 =====
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitting = ref(false)
const isEdit = ref(false)
const form = reactive<SaveTenantPackage>({
  id: '',
  packageCode: '',
  packageName: '',
  description: '',
  priceCnyPerPeriod: undefined,
  periodMonths: 12,
  menuIdsJson: '',
  quotaJson: '',
  sortNo: 0,
})

function openCreate() {
  isEdit.value = false
  dialogTitle.value = i18n.global.t('system.tenantPackage.dialog.createTitle') as string
  Object.assign(form, {
    id: '',
    packageCode: '',
    packageName: '',
    description: '',
    priceCnyPerPeriod: undefined,
    periodMonths: 12,
    menuIdsJson: '',
    quotaJson: '',
    sortNo: 0,
  })
  dialogVisible.value = true
}

function openEdit(row: SysTenantPackage) {
  isEdit.value = true
  dialogTitle.value = i18n.global.t('system.tenantPackage.dialog.editTitle') as string
  Object.assign(form, {
    id: row.id,
    packageCode: row.packageCode,
    packageName: row.packageName,
    description: row.description || '',
    priceCnyPerPeriod: row.priceCnyPerPeriod,
    periodMonths: row.periodMonths ?? 12,
    menuIdsJson: row.menuIdsJson || '',
    quotaJson: row.quotaJson || '',
    sortNo: row.sortNo ?? 0,
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.packageCode || !/^[A-Z][A-Z0-9_]{1,49}$/.test(form.packageCode)) {
    ElMessage.warning(i18n.global.t('system.tenantPackage.message.codeInvalid') as string)
    return
  }
  if (!form.packageName) {
    ElMessage.warning(i18n.global.t('system.tenantPackage.message.nameRequired') as string)
    return
  }
  submitting.value = true
  try {
    await saveTenantPackage({ ...form, id: isEdit.value ? form.id : '' })
    ElMessage.success(i18n.global.t('system.tenantPackage.message.saveSuccess') as string)
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handlePublish(row: SysTenantPackage) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.tenantPackage.message.publishConfirm', { code: row.packageCode }) as string,
      i18n.global.t('common.message.tip') as string,
      {
        confirmButtonText: i18n.global.t('common.action.confirm') as string,
        cancelButtonText: i18n.global.t('common.action.cancel') as string,
        type: 'info',
      }
    )
    await publishTenantPackage(row.id)
    ElMessage.success(i18n.global.t('system.tenantPackage.message.publishSuccess') as string)
    fetchData()
  } catch {
    // 用户取消
  }
}

async function handleArchive(row: SysTenantPackage) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.tenantPackage.message.archiveConfirm', { code: row.packageCode }) as string,
      i18n.global.t('common.message.tip') as string,
      {
        confirmButtonText: i18n.global.t('common.action.confirm') as string,
        cancelButtonText: i18n.global.t('common.action.cancel') as string,
        type: 'warning',
      }
    )
    await archiveTenantPackage(row.id)
    ElMessage.success(i18n.global.t('system.tenantPackage.message.archiveSuccess') as string)
    fetchData()
  } catch {
    // 用户取消
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="tenant-package-view">
    <h2 class="page-title" id="page-title">{{ $t('system.tenantPackage.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('system.tenantPackage.aria.search')">
        <el-select
          v-model="query.status"
          :placeholder="$t('system.tenantPackage.placeholder.status')"
          clearable
          style="width: 140px"
          :aria-label="$t('system.tenantPackage.placeholder.status')"
        >
          <el-option :label="$t('system.tenantPackage.status.draft')" value="DRAFT" />
          <el-option :label="$t('system.tenantPackage.status.active')" value="ACTIVE" />
          <el-option :label="$t('system.tenantPackage.status.archived')" value="ARCHIVED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        <el-button v-permission="'tenant:package:save'" type="success" :icon="Plus" @click="openCreate">{{ $t('common.action.create') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('system.tenantPackage.empty.noData')"
        :aria-label="$t('system.tenantPackage.aria.list')"
      >
        <el-table-column prop="packageCode" :label="$t('system.tenantPackage.field.code')" min-width="140" />
        <el-table-column prop="packageName" :label="$t('system.tenantPackage.field.name')" min-width="160" show-overflow-tooltip />
        <el-table-column :label="$t('system.tenantPackage.field.price')" min-width="150" align="center">
          <template #default="{ row }">
            {{ formatPrice(row as SysTenantPackage) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.tenantPackage.field.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.status === 'ACTIVE' ? $t('system.tenantPackage.status.active') : row.status === 'ARCHIVED' ? $t('system.tenantPackage.status.archived') : $t('system.tenantPackage.status.draft') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" :label="$t('system.tenantPackage.field.sortNo')" width="80" align="center" />
        <el-table-column prop="publishedAt" :label="$t('system.tenantPackage.field.publishedAt')" width="180" />
        <el-table-column :label="$t('system.tenantPackage.field.operation')" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'tenant:package:save'"
              type="primary"
              link
              :icon="Edit"
              size="small"
              :aria-label="$t('common.action.edit')"
              @click="openEdit(row as SysTenantPackage)"
            >{{ $t('common.action.edit') }}</el-button>
            <el-button
              v-if="(row as SysTenantPackage).status !== 'ACTIVE'"
              v-permission="'tenant:package:publish'"
              type="success"
              link
              size="small"
              @click="handlePublish(row as SysTenantPackage)"
            >{{ $t('system.tenantPackage.action.publish') }}</el-button>
            <el-button
              v-if="(row as SysTenantPackage).status !== 'ARCHIVED'"
              v-permission="'tenant:package:archive'"
              type="warning"
              link
              size="small"
              @click="handleArchive(row as SysTenantPackage)"
            >{{ $t('system.tenantPackage.action.archive') }}</el-button>
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
      v-model="dialogVisible"
      :title="dialogTitle"
      width="640px"
      :aria-label="$t('system.tenantPackage.aria.formDialog')"
    >
      <el-form label-width="110px" :model="form">
        <el-form-item :label="$t('system.tenantPackage.field.code')">
          <el-input v-model="form.packageCode" placeholder="PRO" :disabled="isEdit" :aria-label="$t('system.tenantPackage.field.code')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenantPackage.field.name')">
          <el-input v-model="form.packageName" :aria-label="$t('system.tenantPackage.field.name')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenantPackage.field.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" :aria-label="$t('system.tenantPackage.field.description')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenantPackage.field.price')">
          <el-input-number v-model="form.priceCnyPerPeriod" :min="0" :precision="2" style="width: 100%" :aria-label="$t('system.tenantPackage.field.price')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenantPackage.field.periodMonths')">
          <el-select v-model="form.periodMonths" style="width: 100%" :aria-label="$t('system.tenantPackage.field.periodMonths')">
            <el-option :label="`1 ${$t('system.tenantPackage.unit.month')}`" :value="1" />
            <el-option :label="`3 ${$t('system.tenantPackage.unit.month')}`" :value="3" />
            <el-option :label="`12 ${$t('system.tenantPackage.unit.month')}`" :value="12" />
            <el-option :label="`24 ${$t('system.tenantPackage.unit.month')}`" :value="24" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.tenantPackage.field.sortNo')">
          <el-input-number v-model="form.sortNo" :min="0" style="width: 100%" :aria-label="$t('system.tenantPackage.field.sortNo')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenantPackage.field.menuIdsJson')">
          <el-input v-model="form.menuIdsJson" type="textarea" :rows="2" placeholder='["menu1","menu2"]' :aria-label="$t('system.tenantPackage.field.menuIdsJson')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenantPackage.field.quotaJson')">
          <el-input v-model="form.quotaJson" type="textarea" :rows="2" placeholder='{"aiMonthlyTokens":100000}' :aria-label="$t('system.tenantPackage.field.quotaJson')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ $t('common.action.confirm') }}</el-button>
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
