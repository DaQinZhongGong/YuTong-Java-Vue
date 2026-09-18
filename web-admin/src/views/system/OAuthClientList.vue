<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Refresh } from '@element-plus/icons-vue'
import {
  getOAuthClients,
  saveOAuthClient,
  enableOAuthClient,
  disableOAuthClient,
  resetOAuthClientSecret,
  type OAuthClientPageQuery,
} from '@/api/oauthClient'
import type { SysOAuthClient } from '@/api/types'
import i18n from '@/locales'

/**
 * OAuth2 客户端管理页。设计来源: 08-API 契约设计、ADR 0004 P2-F 批次 6-C。
 * 创建时自动生成 clientId/密钥; 更新不允许改密钥, 轮换走 reset-secret (新密钥仅本次可见)。
 */

const loading = ref(false)
const tableData = ref<SysOAuthClient[]>([])
const total = ref(0)

const query = reactive<OAuthClientPageQuery>({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getOAuthClients(query)
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

// ===== 新建/编辑对话框 =====
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitting = ref(false)
const isEdit = ref(false)
const form = reactive<Partial<SysOAuthClient>>({
  id: '',
  clientName: '',
  deviceType: 'pc',
  grantTypes: 'client_credentials,refresh_token',
  accessTokenTtl: 7200,
  refreshTokenTtl: 2592000,
  redirectUris: '',
})

function openCreate() {
  isEdit.value = false
  dialogTitle.value = i18n.global.t('system.oauthClient.dialog.createTitle') as string
  Object.assign(form, {
    id: '',
    clientName: '',
    deviceType: 'pc',
    grantTypes: 'client_credentials,refresh_token',
    accessTokenTtl: 7200,
    refreshTokenTtl: 2592000,
    redirectUris: '',
  })
  dialogVisible.value = true
}

function openEdit(row: SysOAuthClient) {
  isEdit.value = true
  dialogTitle.value = i18n.global.t('system.oauthClient.dialog.editTitle') as string
  Object.assign(form, {
    id: row.id,
    clientName: row.clientName || '',
    deviceType: row.deviceType || 'pc',
    grantTypes: row.grantTypes || 'client_credentials,refresh_token',
    accessTokenTtl: row.accessTokenTtl ?? 7200,
    refreshTokenTtl: row.refreshTokenTtl ?? 2592000,
    redirectUris: row.redirectUris || '',
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.clientName) {
    ElMessage.warning(i18n.global.t('system.oauthClient.message.nameRequired') as string)
    return
  }
  submitting.value = true
  try {
    await saveOAuthClient({ ...form, id: isEdit.value ? form.id : '' })
    ElMessage.success(i18n.global.t('system.oauthClient.message.saveSuccess') as string)
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleEnable(row: SysOAuthClient) {
  await enableOAuthClient(row.id)
  ElMessage.success(i18n.global.t('system.oauthClient.message.enableSuccess') as string)
  fetchData()
}

async function handleDisable(row: SysOAuthClient) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.oauthClient.message.disableConfirm', { name: row.clientName || row.clientId }) as string,
      i18n.global.t('common.message.tip') as string,
      {
        confirmButtonText: i18n.global.t('common.action.confirm') as string,
        cancelButtonText: i18n.global.t('common.action.cancel') as string,
        type: 'warning',
      }
    )
    await disableOAuthClient(row.id)
    ElMessage.success(i18n.global.t('system.oauthClient.message.disableSuccess') as string)
    fetchData()
  } catch {
    // 用户取消
  }
}

async function handleResetSecret(row: SysOAuthClient) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.oauthClient.message.resetConfirm', { name: row.clientName || row.clientId }) as string,
      i18n.global.t('common.message.tip') as string,
      {
        confirmButtonText: i18n.global.t('common.action.confirm') as string,
        cancelButtonText: i18n.global.t('common.action.cancel') as string,
        type: 'warning',
      }
    )
    const res = await resetOAuthClientSecret(row.id)
    await ElMessageBox.alert(
      `${i18n.global.t('system.oauthClient.message.newSecret') as string}: ${res.clientSecret}`,
      i18n.global.t('system.oauthClient.message.resetSuccess') as string,
      {
        confirmButtonText: i18n.global.t('common.action.confirm') as string,
        showClose: false,
      }
    )
    fetchData()
  } catch {
    // 用户取消
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="oauth-client-view">
    <h2 class="page-title" id="page-title">{{ $t('system.oauthClient.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('system.oauthClient.aria.search')">
        <el-select
          v-model="query.status"
          :placeholder="$t('system.oauthClient.placeholder.status')"
          clearable
          style="width: 140px"
          :aria-label="$t('system.oauthClient.placeholder.status')"
        >
          <el-option :label="$t('system.oauthClient.status.enabled')" value="ENABLE" />
          <el-option :label="$t('system.oauthClient.status.disabled')" value="DISABLE" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        <el-button v-permission="'auth:client:save'" type="success" :icon="Plus" @click="openCreate">{{ $t('common.action.create') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('system.oauthClient.empty.noData')"
        :aria-label="$t('system.oauthClient.aria.list')"
      >
        <el-table-column prop="clientId" :label="$t('system.oauthClient.field.clientId')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="clientName" :label="$t('system.oauthClient.field.clientName')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="deviceType" :label="$t('system.oauthClient.field.deviceType')" width="110" align="center" />
        <el-table-column prop="grantTypes" :label="$t('system.oauthClient.field.grantTypes')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$t('system.oauthClient.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLE' ? $t('system.oauthClient.status.enabled') : $t('system.oauthClient.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.oauthClient.field.operation')" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'auth:client:save'"
              type="primary"
              link
              :icon="Edit"
              size="small"
              :aria-label="$t('common.action.edit')"
              @click="openEdit(row as SysOAuthClient)"
            >{{ $t('common.action.edit') }}</el-button>
            <el-button
              v-if="(row as SysOAuthClient).status !== 'ENABLE'"
              v-permission="'auth:client:save'"
              type="success"
              link
              size="small"
              @click="handleEnable(row as SysOAuthClient)"
            >{{ $t('system.oauthClient.action.enable') }}</el-button>
            <el-button
              v-if="(row as SysOAuthClient).status === 'ENABLE'"
              v-permission="'auth:client:save'"
              type="warning"
              link
              size="small"
              @click="handleDisable(row as SysOAuthClient)"
            >{{ $t('system.oauthClient.action.disable') }}</el-button>
            <el-button
              v-permission="'auth:client:save'"
              type="danger"
              link
              :icon="Refresh"
              size="small"
              @click="handleResetSecret(row as SysOAuthClient)"
            >{{ $t('system.oauthClient.action.resetSecret') }}</el-button>
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
      width="600px"
      :aria-label="$t('system.oauthClient.aria.formDialog')"
    >
      <el-form label-width="130px" :model="form">
        <el-form-item :label="$t('system.oauthClient.field.clientName')">
          <el-input v-model="form.clientName" :aria-label="$t('system.oauthClient.field.clientName')" />
        </el-form-item>
        <el-form-item :label="$t('system.oauthClient.field.deviceType')">
          <el-select v-model="form.deviceType" style="width: 100%" :aria-label="$t('system.oauthClient.field.deviceType')">
            <el-option label="pc" value="pc" />
            <el-option label="mobile" value="mobile" />
            <el-option label="miniapp" value="miniapp" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.oauthClient.field.grantTypes')">
          <el-input v-model="form.grantTypes" placeholder="client_credentials,refresh_token" :aria-label="$t('system.oauthClient.field.grantTypes')" />
        </el-form-item>
        <el-form-item :label="$t('system.oauthClient.field.accessTokenTtl')">
          <el-input-number v-model="form.accessTokenTtl" :min="60" style="width: 100%" :aria-label="$t('system.oauthClient.field.accessTokenTtl')" />
        </el-form-item>
        <el-form-item :label="$t('system.oauthClient.field.refreshTokenTtl')">
          <el-input-number v-model="form.refreshTokenTtl" :min="60" style="width: 100%" :aria-label="$t('system.oauthClient.field.refreshTokenTtl')" />
        </el-form-item>
        <el-form-item :label="$t('system.oauthClient.field.redirectUris')">
          <el-input v-model="form.redirectUris" type="textarea" :rows="2" :aria-label="$t('system.oauthClient.field.redirectUris')" />
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
