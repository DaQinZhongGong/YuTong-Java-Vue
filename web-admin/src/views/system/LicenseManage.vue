<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, CircleCheck, CircleClose, Upload } from '@element-plus/icons-vue'
import type { UploadRequestOptions } from 'element-plus'
import {
  getCurrentLicense,
  listModuleStatuses,
  getLicenseUsage,
  uploadLicense,
  refreshLicense,
  listAuditLogs,
  type LicenseInfo,
  type ModuleStatus,
  type QuotaUsage,
  type LicenseAuditLog,
  type PageResult,
} from '@/api/license'
import i18n from '@/locales'

const loading = ref(false)
const licenseInfo = ref<LicenseInfo | null>(null)
const moduleStatuses = ref<ModuleStatus[]>([])
const quotaUsages = ref<QuotaUsage[]>([])

// GA2-L174: License 上传相关状态
const uploadDialogVisible = ref(false)
const uploadContent = ref('')
const uploadLoading = ref(false)

// GA2-L174: 审计日志相关状态
const auditLogs = ref<LicenseAuditLog[]>([])
const auditTotal = ref(0)
const auditPage = ref(1)
const auditSize = ref(10)
const auditLoading = ref(false)
const auditFilterAction = ref('')
const auditFilterModuleCode = ref('')

// GA2-L174: 到期预警计算
const daysUntilExpiry = computed<number | null>(() => {
  if (!licenseInfo.value?.expireTime) return null
  const expiry = new Date(licenseInfo.value.expireTime).getTime()
  const now = Date.now()
  return Math.floor((expiry - now) / (1000 * 60 * 60 * 24))
})

const showExpiryWarning = computed(() => {
  return daysUntilExpiry.value !== null && daysUntilExpiry.value >= 0 && daysUntilExpiry.value < 30
})

const isExpired = computed(() => {
  return daysUntilExpiry.value !== null && daysUntilExpiry.value < 0
})

async function fetchAll() {
  loading.value = true
  try {
    const [info, modules, usage] = await Promise.all([
      getCurrentLicense(),
      listModuleStatuses(),
      getLicenseUsage(),
    ])
    licenseInfo.value = info
    moduleStatuses.value = modules
    quotaUsages.value = usage
  } catch {
    ElMessage.error(i18n.global.t('system.license.message.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function fetchAuditLogs() {
  auditLoading.value = true
  try {
    const result: PageResult<LicenseAuditLog> = await listAuditLogs({
      page: auditPage.value,
      size: auditSize.value,
      action: auditFilterAction.value || undefined,
      moduleCode: auditFilterModuleCode.value || undefined,
    })
    auditLogs.value = result.records || []
    auditTotal.value = result.total || 0
  } catch {
    ElMessage.error(i18n.global.t('system.license.message.auditLoadFailed'))
  } finally {
    auditLoading.value = false
  }
}

function handleAuditPageChange(page: number) {
  auditPage.value = page
  fetchAuditLogs()
}

function handleAuditFilter() {
  auditPage.value = 1
  fetchAuditLogs()
}

// GA2-L174: License 上传相关方法
function openUploadDialog() {
  uploadContent.value = ''
  uploadDialogVisible.value = true
}

function handleFileUpload(options: UploadRequestOptions): Promise<void> {
  return new Promise((resolve) => {
    const file = options.file as File
    const reader = new FileReader()
    reader.onload = (e) => {
      uploadContent.value = e.target?.result as string
      resolve()
    }
    reader.readAsText(file)
  })
}

function formatLicenseTemplate() {
  const template = {
    licenseId: 'LIC-2026-XXXX',
    subject: i18n.global.t('system.license.template.sampleSubject'),
    edition: 'Professional',
    deploymentId: 'deploy-xxx',
    licenseSchemaVersion: '1.0.0',
    limits: {
      'ai.monthly.tokens': 1000000,
      'lowcode.generate.count': 100,
      'report.export.count': 50,
    },
    modules: ['system', 'sample', 'lowcode', 'ai', 'report', 'workflow', 'datasource', 'plugin'],
    expireTime: '2027-12-31T23:59:59+08:00',
    signature: '...',
  }
  uploadContent.value = JSON.stringify(template, null, 2)
}

async function submitUpload() {
  if (!uploadContent.value.trim()) {
    ElMessage.warning(i18n.global.t('system.license.message.licenseJsonRequired'))
    return
  }
  uploadLoading.value = true
  try {
    const result = await uploadLicense(uploadContent.value)
    ElMessage.success(
      result.previousRevoked
        ? i18n.global.t('system.license.message.uploadSuccessWithRevoke', { id: result.licenseId, edition: result.edition })
        : i18n.global.t('system.license.message.uploadSuccess', { id: result.licenseId, edition: result.edition })
    )
    uploadDialogVisible.value = false
    await fetchAll()
    await fetchAuditLogs()
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : i18n.global.t('system.license.message.uploadFailed')
    ElMessage.error(msg)
  } finally {
    uploadLoading.value = false
  }
}

async function handleRefresh() {
  try {
    await refreshLicense()
    ElMessage.success(i18n.global.t('system.license.message.refreshSuccess'))
    await fetchAll()
    await fetchAuditLogs()
  } catch {
    ElMessage.error(i18n.global.t('system.license.message.refreshFailed'))
  }
}

function editionTagType(edition: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (edition) {
    case 'Enterprise':
      return 'danger'
    case 'Professional':
      return 'warning'
    case 'Industry':
      return 'success'
    default:
      return 'info'
  }
}

function editionLabel(edition: string): string {
  switch (edition) {
    case 'Community':
      return i18n.global.t('system.license.edition.community')
    case 'Professional':
      return i18n.global.t('system.license.edition.professional')
    case 'Enterprise':
      return i18n.global.t('system.license.edition.enterprise')
    case 'Industry':
      return i18n.global.t('system.license.edition.industry')
    default:
      return edition
  }
}

function moduleLabel(code: string): string {
  const labels: Record<string, string> = {
    system: i18n.global.t('system.license.option.moduleSystem'),
    sample: i18n.global.t('system.license.module.sample'),
    lowcode: i18n.global.t('system.license.option.moduleLowcode'),
    ai: i18n.global.t('system.license.option.moduleAi'),
    report: i18n.global.t('system.license.option.moduleReport'),
    workflow: i18n.global.t('system.license.option.moduleWorkflow'),
    datasource: i18n.global.t('system.license.option.moduleDatasource'),
    plugin: i18n.global.t('system.license.option.modulePlugin'),
  }
  return labels[code] || code
}

function quotaLabel(code: string): string {
  const labels: Record<string, string> = {
    'ai.monthly.tokens': i18n.global.t('system.license.quota.aiMonthlyTokens'),
    'lowcode.generate.count': i18n.global.t('system.license.quota.lowcodeGenerateCount'),
    'report.export.count': i18n.global.t('system.license.quota.reportExportCount'),
  }
  return labels[code] || code
}

function usagePercent(used: number, limit: number | null): number {
  if (!limit || limit <= 0) return 0
  return Math.min(100, Math.round((used / limit) * 100))
}

function actionTagType(action: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (action) {
    case 'LOAD':
      return 'info'
    case 'REFRESH':
      return 'success'
    case 'DENY':
      return 'danger'
    case 'QUOTA_EXCEEDED':
      return 'danger'
    case 'EXPIRE':
      return 'warning'
    case 'VERIFY':
      return 'info'
    default:
      return 'info'
  }
}

function actionLabel(action: string): string {
  const labels: Record<string, string> = {
    LOAD: i18n.global.t('system.license.option.actionLoad'),
    VERIFY: i18n.global.t('system.license.option.actionVerify'),
    REFRESH: i18n.global.t('system.license.option.actionRefresh'),
    DENY: i18n.global.t('system.license.option.actionDeny'),
    EXPIRE: i18n.global.t('system.license.option.actionExpire'),
    QUOTA_EXCEEDED: i18n.global.t('system.license.option.actionQuotaExceeded'),
  }
  return labels[action] || action
}

function resultTagType(result: string): 'success' | 'warning' | 'danger' | 'info' {
  switch (result) {
    case 'SUCCESS':
      return 'success'
    case 'WARN':
      return 'warning'
    case 'FAILURE':
    case 'DENIED':
      return 'danger'
    default:
      return 'info'
  }
}

onMounted(() => {
  fetchAll()
  fetchAuditLogs()
})
</script>

<template>
  <div class="license-view">
    <h2 class="page-title" id="page-title">{{ $t('system.license.page.list') }}</h2>

    <!-- GA2-L174: 到期预警横幅 -->
    <el-alert
      v-if="showExpiryWarning && licenseInfo"
      :title="$t('system.license.message.expiringSoon', { days: daysUntilExpiry, id: licenseInfo.licenseId })"
      type="warning"
      :closable="false"
      show-icon
      class="section-card"
    >
      <template #default>
        <span>{{ $t('system.license.field.expireTime') }}：{{ new Date(licenseInfo.expireTime!).toLocaleString('zh-CN') }}</span>
      </template>
    </el-alert>
    <el-alert
      v-if="isExpired && licenseInfo"
      :title="$t('system.license.message.expired')"
      type="error"
      :closable="false"
      show-icon
      class="section-card"
    />

    <!-- 授权信息卡片 -->
    <el-card shadow="never" class="section-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>{{ $t('system.license.title.currentInfo') }}</span>
          <div>
            <el-button :icon="Upload" size="small" type="primary" @click="openUploadDialog">{{ $t('system.license.action.uploadLicense') }}</el-button>
            <el-button :icon="Refresh" size="small" @click="handleRefresh">{{ $t('common.action.refresh') }}</el-button>
          </div>
        </div>
      </template>

      <el-descriptions v-if="licenseInfo" :column="3" border>
        <el-descriptions-item :label="$t('common.field.version')">
          <el-tag :type="editionTagType(licenseInfo.edition)" size="small">
            {{ editionLabel(licenseInfo.edition) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.license.field.subject')">{{ licenseInfo.subject }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.license.field.licenseId')">{{ licenseInfo.licenseId || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.license.field.deploymentId')">{{ licenseInfo.deploymentId || '—' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('system.license.field.schemaVersion')">{{ licenseInfo.licenseSchemaVersion }}</el-descriptions-item>
        <el-descriptions-item :label="$t('common.field.status')">
          <el-tag :type="licenseInfo.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ licenseInfo.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.license.field.expireTime')">
          <span v-if="licenseInfo.expireTime">
            {{ new Date(licenseInfo.expireTime).toLocaleString('zh-CN') }}
            <el-tag v-if="daysUntilExpiry !== null && daysUntilExpiry >= 0" size="small" :type="daysUntilExpiry < 30 ? 'warning' : 'success'" class="ml-2">
              {{ $t('system.license.message.daysRemaining', { days: daysUntilExpiry }) }}
            </el-tag>
          </span>
          <span v-else>{{ $t('system.license.tag.permanent') }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.license.field.modules')">
          <el-tag v-for="m in licenseInfo.modules" :key="m" size="small" class="module-tag">
            {{ moduleLabel(m) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('system.license.field.fallbackMode')">
          <el-tag :type="licenseInfo.fallbackCommunity ? 'warning' : 'success'" size="small">
            {{ licenseInfo.fallbackCommunity ? $t('system.license.tag.fallback') : $t('system.license.tag.formal') }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 模块状态矩阵 -->
    <el-card shadow="never" class="section-card" v-loading="loading">
      <template #header>
        <span>{{ $t('system.license.title.moduleMatrix') }}</span>
      </template>

      <el-table :data="moduleStatuses" border stripe :aria-label="$t('system.license.title.moduleMatrix')">
        <el-table-column prop="moduleCode" :label="$t('system.license.field.moduleCode')" width="140" />
        <el-table-column :label="$t('system.license.field.moduleName')" min-width="120">
          <template #default="{ row }">{{ moduleLabel(row.moduleCode) }}</template>
        </el-table-column>
        <el-table-column :label="$t('system.license.field.licensed')" width="120" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.licensed" :color="'#67c23a'"><CircleCheck /></el-icon>
            <el-icon v-else :color="'#f56c6c'"><CircleClose /></el-icon>
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.license.field.moduleSwitch')" width="120" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.switchOn" :color="'#67c23a'"><CircleCheck /></el-icon>
            <el-icon v-else :color="'#f56c6c'"><CircleClose /></el-icon>
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.license.field.active')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'" size="small">
              {{ row.active ? $t('system.license.status.active') : $t('system.license.status.inactive') }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 额度使用情况 -->
    <el-card shadow="never" class="section-card" v-loading="loading">
      <template #header>
        <span>{{ $t('system.license.title.quotaUsage') }}</span>
      </template>

      <el-empty v-if="quotaUsages.length === 0" :description="$t('system.license.empty.noQuota')" />
      <el-table v-else :data="quotaUsages" border stripe :aria-label="$t('system.license.aria.quotaUsage')">
        <el-table-column :label="$t('system.license.field.quotaItem')" min-width="180">
          <template #default="{ row }">{{ quotaLabel(row.quotaCode) }}</template>
        </el-table-column>
        <el-table-column prop="period" :label="$t('system.license.field.period')" width="120" align="center" />
        <el-table-column :label="$t('system.license.field.usedLimit')" min-width="200">
          <template #default="{ row }">
            <div class="usage-bar">
              <el-progress
                :percentage="usagePercent(row.used, row.limit)"
                :status="usagePercent(row.used, row.limit) >= 90 ? 'exception' : usagePercent(row.used, row.limit) >= 70 ? 'warning' : 'success'"
                :stroke-width="14"
                :text-inside="true"
              />
              <span class="usage-text">
                {{ row.used.toLocaleString() }} / {{ row.limit ? row.limit.toLocaleString() : $t('system.license.tag.unlimited') }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.license.field.lastUsedTime')" width="200">
          <template #default="{ row }">
            {{ row.lastUsedTime ? new Date(row.lastUsedTime).toLocaleString('zh-CN') : '—' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- GA2-L174: 授权审计日志 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <span>{{ $t('system.license.title.auditLog') }}</span>
      </template>

      <div class="audit-filters">
        <el-select v-model="auditFilterAction" :placeholder="$t('system.license.placeholder.actionFilter')" clearable size="small" style="width: 150px">
          <el-option :label="$t('system.license.option.actionLoad')" value="LOAD" />
          <el-option :label="$t('system.license.option.actionRefresh')" value="REFRESH" />
          <el-option :label="$t('system.license.option.actionDeny')" value="DENY" />
          <el-option :label="$t('system.license.option.actionQuotaExceeded')" value="QUOTA_EXCEEDED" />
          <el-option :label="$t('system.license.option.actionExpire')" value="EXPIRE" />
          <el-option :label="$t('system.license.option.actionVerify')" value="VERIFY" />
        </el-select>
        <el-select v-model="auditFilterModuleCode" :placeholder="$t('system.license.placeholder.moduleFilter')" clearable size="small" style="width: 150px">
          <el-option :label="$t('system.license.option.moduleSystem')" value="system" />
          <el-option :label="$t('system.license.option.moduleLowcode')" value="lowcode" />
          <el-option :label="$t('system.license.option.moduleAi')" value="ai" />
          <el-option :label="$t('system.license.option.moduleReport')" value="report" />
          <el-option :label="$t('system.license.option.moduleWorkflow')" value="workflow" />
          <el-option :label="$t('system.license.option.moduleDatasource')" value="datasource" />
          <el-option :label="$t('system.license.option.modulePlugin')" value="plugin" />
        </el-select>
        <el-button :icon="Refresh" size="small" @click="handleAuditFilter">{{ $t('common.action.list') }}</el-button>
      </div>

      <el-table :data="auditLogs" border stripe v-loading="auditLoading" :aria-label="$t('system.license.title.auditLog')" style="margin-top: 12px">
        <el-table-column :label="$t('system.license.field.action')" width="120">
          <template #default="{ row }">
            <el-tag :type="actionTagType(row.action)" size="small">{{ actionLabel(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.field.result')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="resultTagType(row.result)" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="licenseId" :label="$t('system.license.field.licenseId')" width="180" show-overflow-tooltip />
        <el-table-column :label="$t('system.license.field.module')" width="100">
          <template #default="{ row }">{{ row.moduleCode ? moduleLabel(row.moduleCode) : '—' }}</template>
        </el-table-column>
        <el-table-column :label="$t('system.license.field.quota')" width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.quotaCode ? quotaLabel(row.quotaCode) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="errorCode" :label="$t('system.license.field.errorCode')" width="120" />
        <el-table-column prop="traceId" :label="$t('common.field.traceId')" width="200" show-overflow-tooltip />
        <el-table-column :label="$t('system.license.field.operatedTime')" width="180">
          <template #default="{ row }">{{ new Date(row.operatedTime).toLocaleString('zh-CN') }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="auditTotal > 0"
        :current-page="auditPage"
        :page-size="auditSize"
        :total="auditTotal"
        layout="total, prev, pager, next"
        @current-change="handleAuditPageChange"
        style="margin-top: 12px; justify-content: flex-end"
      />
    </el-card>

    <!-- GA2-L174: License 上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" :title="$t('system.license.title.uploadDialog')" width="700px">
      <el-upload
        :auto-upload="false"
        :show-file-list="false"
        :http-request="handleFileUpload"
        accept=".json"
        drag
      >
        <el-icon class="el-icon--upload"><Upload /></el-icon>
        <div class="el-upload__text">{{ $t('system.license.upload.dragHint') }}<em>{{ $t('system.license.upload.clickSelect') }}</em></div>
        <template #tip>
          <div class="el-upload__tip">
            {{ $t('system.license.upload.tipFormat') }}
            <el-link type="primary" :underline="false" @click="formatLicenseTemplate">{{ $t('system.license.upload.viewTemplate') }}</el-link>
          </div>
        </template>
      </el-upload>

      <el-input
        v-model="uploadContent"
        type="textarea"
        :rows="10"
        :placeholder="$t('system.license.placeholder.pasteJson')"
        style="margin-top: 12px"
      />

      <template #footer>
        <el-button @click="uploadDialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="uploadLoading" @click="submitUpload">{{ $t('system.license.action.confirmUpload') }}</el-button>
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
.section-card {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.module-tag {
  margin-right: 4px;
}
.usage-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.usage-bar .el-progress {
  flex: 1;
  min-width: 120px;
}
.usage-text {
  white-space: nowrap;
  font-size: 13px;
  color: #606266;
}
.audit-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}
.ml-2 {
  margin-left: 8px;
}
</style>
