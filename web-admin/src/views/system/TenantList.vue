<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit } from '@element-plus/icons-vue'
import {
  getTenants,
  saveTenant,
  enableTenant,
  disableTenant,
  assignTenantPackage,
  deleteTenant,
  type TenantPageQuery,
} from '@/api/tenant'
import { listActiveTenantPackages } from '@/api/tenantPackage'
import type { SysTenant, SaveTenant, SysTenantPackage } from '@/api/types'
import i18n from '@/locales'

/**
 * 租户列表管理页。设计来源: 业界同类实现 SysTenant + ADR 0004 P2-F 租户列表。
 * 平台级主数据 (后端豁免行级过滤, tenant:tenant:* 权限守卫):
 * 编码创建后不可改 / default 禁止停用删除 / 删除仅允许已停用。
 */

const t = (k: string, p?: Record<string, unknown>) => i18n.global.t(k, p ?? {}) as string

const loading = ref(false)
const tableData = ref<SysTenant[]>([])
const total = ref(0)

const query = reactive<TenantPageQuery>({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getTenants(query)
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
  return status === 'NORMAL' ? 'success' : 'info'
}

function formatAccount(row: SysTenant) {
  if (row.accountCount === undefined || row.accountCount === null) return '-'
  return row.accountCount < 0 ? t('system.tenant.unlimited') : String(row.accountCount)
}

function formatExpire(row: SysTenant) {
  if (!row.expireTime) return t('system.tenant.neverExpire')
  return String(row.expireTime).replace('T', ' ').slice(0, 16)
}

// ===== 新建/编辑对话框 =====
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitting = ref(false)
const isEdit = ref(false)
const form = reactive<SaveTenant>({
  id: '',
  tenantCode: '',
  companyName: '',
  contactUserName: '',
  contactPhone: '',
  licenseNumber: '',
  address: '',
  domain: '',
  intro: '',
  packageId: '',
  expireTime: '',
  accountCount: -1,
})

function openCreate() {
  isEdit.value = false
  dialogTitle.value = t('system.tenant.dialog.createTitle')
  Object.assign(form, {
    id: '',
    tenantCode: '',
    companyName: '',
    contactUserName: '',
    contactPhone: '',
    licenseNumber: '',
    address: '',
    domain: '',
    intro: '',
    packageId: '',
    expireTime: '',
    accountCount: -1,
  })
  dialogVisible.value = true
}

function openEdit(row: SysTenant) {
  isEdit.value = true
  dialogTitle.value = t('system.tenant.dialog.editTitle')
  Object.assign(form, {
    id: row.id,
    tenantCode: row.tenantCode,
    companyName: row.companyName,
    contactUserName: row.contactUserName || '',
    contactPhone: row.contactPhone || '',
    licenseNumber: row.licenseNumber || '',
    address: row.address || '',
    domain: row.domain || '',
    intro: row.intro || '',
    packageId: row.packageId || '',
    expireTime: row.expireTime || '',
    accountCount: row.accountCount ?? -1,
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.tenantCode || !/^[a-z0-9][a-z0-9-]{1,31}$/.test(form.tenantCode)) {
    ElMessage.warning(t('system.tenant.message.codeInvalid'))
    return
  }
  if (!form.companyName) {
    ElMessage.warning(t('system.tenant.message.nameRequired'))
    return
  }
  submitting.value = true
  try {
    await saveTenant({ ...form, id: isEdit.value ? form.id : '' })
    ElMessage.success(t('system.tenant.message.saveSuccess'))
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

// ===== 启停 / 删除 =====
async function confirmAction(
  message: string,
  type: 'success' | 'warning' | 'info' | 'error',
  action: () => Promise<unknown>,
  successMsg: string,
) {
  try {
    await ElMessageBox.confirm(message, t('common.message.tip'), {
      confirmButtonText: t('common.action.confirm'),
      cancelButtonText: t('common.action.cancel'),
      type,
    })
    await action()
    ElMessage.success(successMsg)
    fetchData()
  } catch {
    // 用户取消或接口报错 (报错由全局拦截器提示)
  }
}

function handleEnable(row: SysTenant) {
  confirmAction(
    t('system.tenant.message.enableConfirm', { code: row.tenantCode }),
    'success',
    () => enableTenant(row.id),
    t('system.tenant.message.enableSuccess'),
  )
}

function handleDisable(row: SysTenant) {
  confirmAction(
    t('system.tenant.message.disableConfirm', { code: row.tenantCode }),
    'warning',
    () => disableTenant(row.id),
    t('system.tenant.message.disableSuccess'),
  )
}

function handleDelete(row: SysTenant) {
  confirmAction(
    t('system.tenant.message.deleteConfirm', { code: row.tenantCode }),
    'error',
    () => deleteTenant(row.id),
    t('system.tenant.message.deleteSuccess'),
  )
}

// ===== 分配套餐对话框 =====
const assignVisible = ref(false)
const assignSubmitting = ref(false)
const assignTenant = ref<SysTenant | null>(null)
const activePackages = ref<SysTenantPackage[]>([])
const assignForm = reactive<{ packageId: string; expireTime: string }>({
  packageId: '',
  expireTime: '',
})

async function openAssign(row: SysTenant) {
  assignTenant.value = row
  assignForm.packageId = row.packageId || ''
  assignForm.expireTime = row.expireTime || ''
  assignVisible.value = true
  try {
    activePackages.value = await listActiveTenantPackages()
  } catch {
    activePackages.value = []
  }
}

async function handleAssign() {
  if (!assignTenant.value) return
  if (!assignForm.packageId) {
    ElMessage.warning(t('system.tenant.message.packageRequired'))
    return
  }
  assignSubmitting.value = true
  try {
    await assignTenantPackage(assignTenant.value.id, assignForm.packageId, assignForm.expireTime || undefined)
    ElMessage.success(t('system.tenant.message.assignSuccess'))
    assignVisible.value = false
    fetchData()
  } finally {
    assignSubmitting.value = false
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="tenant-view">
    <h2 class="page-title" id="page-title">{{ $t('system.tenant.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('system.tenant.aria.search')">
        <el-input
          v-model="query.keyword"
          :placeholder="$t('system.tenant.placeholder.keyword')"
          clearable
          style="width: 240px"
          :aria-label="$t('system.tenant.placeholder.keyword')"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.status"
          :placeholder="$t('system.tenant.placeholder.status')"
          clearable
          style="width: 140px"
          :aria-label="$t('system.tenant.placeholder.status')"
        >
          <el-option :label="$t('system.tenant.status.normal')" value="NORMAL" />
          <el-option :label="$t('system.tenant.status.disabled')" value="DISABLED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        <el-button v-permission="'tenant:tenant:save'" type="success" :icon="Plus" @click="openCreate">{{ $t('common.action.create') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('system.tenant.empty.noData')"
        :aria-label="$t('system.tenant.aria.list')"
      >
        <el-table-column prop="tenantCode" :label="$t('system.tenant.field.code')" min-width="120" />
        <el-table-column prop="companyName" :label="$t('system.tenant.field.company')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contactUserName" :label="$t('system.tenant.field.contact')" min-width="110" />
        <el-table-column prop="contactPhone" :label="$t('system.tenant.field.phone')" min-width="130" />
        <el-table-column :label="$t('system.tenant.field.account')" width="100" align="center">
          <template #default="{ row }">
            {{ formatAccount(row as SysTenant) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.tenant.field.expire')" width="130" align="center">
          <template #default="{ row }">
            {{ formatExpire(row as SysTenant) }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.tenant.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType((row as SysTenant).status)" size="small">
              {{ (row as SysTenant).status === 'NORMAL' ? $t('system.tenant.status.normal') : $t('system.tenant.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.tenant.field.operation')" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'tenant:tenant:save'"
              type="primary"
              link
              :icon="Edit"
              size="small"
              :aria-label="$t('common.action.edit')"
              @click="openEdit(row as SysTenant)"
            >{{ $t('common.action.edit') }}</el-button>
            <el-button
              v-permission="'tenant:tenant:assign'"
              type="primary"
              link
              size="small"
              @click="openAssign(row as SysTenant)"
            >{{ $t('system.tenant.action.assign') }}</el-button>
            <el-button
              v-if="(row as SysTenant).status !== 'NORMAL'"
              v-permission="'tenant:tenant:enable'"
              type="success"
              link
              size="small"
              @click="handleEnable(row as SysTenant)"
            >{{ $t('system.tenant.action.enable') }}</el-button>
            <el-button
              v-if="(row as SysTenant).status === 'NORMAL'"
              v-permission="'tenant:tenant:disable'"
              type="warning"
              link
              size="small"
              @click="handleDisable(row as SysTenant)"
            >{{ $t('system.tenant.action.disable') }}</el-button>
            <el-button
              v-if="(row as SysTenant).status !== 'NORMAL'"
              v-permission="'tenant:tenant:delete'"
              type="danger"
              link
              size="small"
              @click="handleDelete(row as SysTenant)"
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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="640px"
      :aria-label="$t('system.tenant.aria.formDialog')"
    >
      <el-form label-width="110px" :model="form">
        <el-form-item :label="$t('system.tenant.field.code')">
          <el-input v-model="form.tenantCode" placeholder="acme" :disabled="isEdit" :aria-label="$t('system.tenant.field.code')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.company')">
          <el-input v-model="form.companyName" :aria-label="$t('system.tenant.field.company')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.contact')">
          <el-input v-model="form.contactUserName" :aria-label="$t('system.tenant.field.contact')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.phone')">
          <el-input v-model="form.contactPhone" :aria-label="$t('system.tenant.field.phone')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.license')">
          <el-input v-model="form.licenseNumber" :aria-label="$t('system.tenant.field.license')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.address')">
          <el-input v-model="form.address" :aria-label="$t('system.tenant.field.address')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.domain')">
          <el-input v-model="form.domain" placeholder="acme.example.com" :aria-label="$t('system.tenant.field.domain')" />
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.account')">
          <el-input-number v-model="form.accountCount" :min="-1" style="width: 100%" :aria-label="$t('system.tenant.field.account')" />
          <div class="form-hint">{{ $t('system.tenant.hint.accountUnlimited') }}</div>
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.intro')">
          <el-input v-model="form.intro" type="textarea" :rows="2" :aria-label="$t('system.tenant.field.intro')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ $t('common.action.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="assignVisible"
      :title="$t('system.tenant.dialog.assignTitle')"
      width="520px"
      :aria-label="$t('system.tenant.dialog.assignTitle')"
    >
      <el-form label-width="110px" :model="assignForm">
        <el-form-item :label="$t('system.tenant.field.package')">
          <el-select v-model="assignForm.packageId" style="width: 100%" :aria-label="$t('system.tenant.field.package')">
            <el-option
              v-for="p in activePackages"
              :key="p.id"
              :label="`${p.packageName} (${p.packageCode})`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.tenant.field.expire')">
          <el-date-picker
            v-model="assignForm.expireTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
            :aria-label="$t('system.tenant.field.expire')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="handleAssign">{{ $t('common.action.confirm') }}</el-button>
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
.form-hint {
  font-size: 12px;
  color: var(--yt-text-secondary, #4b5563);
  margin-top: 4px;
}
</style>
