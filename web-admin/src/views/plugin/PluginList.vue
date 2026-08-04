<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPlugins, installPlugin, uninstallPlugin, getPluginAuditLogs } from '@/api/plugin'
import type { PluginPackage, PluginAuditLog } from '@/api/plugin'

/**
 * 插件管理页面（45 号文档「插件与模板生态设计」）
 * 功能：插件列表、安装/卸载、审计日志查看
 */

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<PluginPackage[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const filters = reactive({
  keyword: '',
  status: '',
  riskLevel: '',
})

// 审计日志相关
const auditLogVisible = ref(false)
const auditLogLoading = ref(false)
const auditLogs = ref<PluginAuditLog[]>([])
const auditLogTotal = ref(0)
const auditLogPage = ref(1)
const auditLogPageSize = ref(10)
const currentPluginCode = ref('')

// 状态选项
const statusOptions = [
  { value: 'UPLOADED', label: t('plugin.plugin.status.uploaded') },
  { value: 'VERIFIED', label: t('plugin.plugin.status.verified') },
  { value: 'REJECTED', label: t('plugin.plugin.status.rejected') },
  { value: 'DEPRECATED', label: t('plugin.plugin.status.deprecated') },
]

// 风险等级选项
const riskLevelOptions = [
  { value: 'LOW', label: t('plugin.plugin.riskLevel.low') },
  { value: 'MEDIUM', label: t('plugin.plugin.riskLevel.medium') },
  { value: 'HIGH', label: t('plugin.plugin.riskLevel.high') },
]

// 签名状态映射
const signatureStatusMap: Record<string, string> = {
  UNSIGNED: t('plugin.plugin.signature.unsigned'),
  VALID: t('plugin.plugin.signature.valid'),
  INVALID: t('plugin.plugin.signature.invalid'),
}

// 状态映射
const statusMap: Record<string, string> = {
  UPLOADED: t('plugin.plugin.status.uploaded'),
  VERIFIED: t('plugin.plugin.status.verified'),
  REJECTED: t('plugin.plugin.status.rejected'),
  DEPRECATED: t('plugin.plugin.status.deprecated'),
}

// 风险等级映射
const riskLevelMap: Record<string, string> = {
  LOW: t('plugin.plugin.riskLevel.low'),
  MEDIUM: t('plugin.plugin.riskLevel.medium'),
  HIGH: t('plugin.plugin.riskLevel.high'),
}

/** 加载插件列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getPlugins({
      pageNo: currentPage.value,
      pageSize: pageSize.value,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      riskLevel: filters.riskLevel || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('plugin.plugin.msg.loadFailed'))
  } finally {
    loading.value = false
  }
}

/** 搜索 */
function handleSearch() {
  currentPage.value = 1
  loadData()
}

/** 重置筛选 */
function handleReset() {
  filters.keyword = ''
  filters.status = ''
  filters.riskLevel = ''
  handleSearch()
}

/** 分页变更 */
function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

/** 安装插件 */
async function handleInstall(row: PluginPackage) {
  try {
    await ElMessageBox.confirm(
      t('plugin.plugin.msg.installConfirm', { name: row.pluginName }),
      t('plugin.plugin.msg.installConfirmTitle'),
      { type: 'info', confirmButtonText: t('plugin.plugin.msg.installConfirmButton'), cancelButtonText: t('plugin.plugin.msg.cancelButton') }
    )
  } catch {
    return
  }
  try {
    await installPlugin(row.id)
    ElMessage.success(t('plugin.plugin.msg.installSuccess'))
    await loadData()
  } catch (e) {
    ElMessage.error(t('plugin.plugin.msg.installFailed'))
  }
}

/** 卸载插件 */
async function handleUninstall(row: PluginPackage) {
  try {
    await ElMessageBox.confirm(
      t('plugin.plugin.msg.uninstallConfirm', { name: row.pluginName }),
      t('plugin.plugin.msg.uninstallConfirmTitle'),
      { type: 'warning', confirmButtonText: t('plugin.plugin.msg.uninstallConfirmButton'), cancelButtonText: t('plugin.plugin.msg.cancelButton') }
    )
  } catch {
    return
  }
  try {
    await uninstallPlugin(row.id)
    ElMessage.success(t('plugin.plugin.msg.uninstallSuccess'))
    await loadData()
  } catch (e) {
    ElMessage.error(t('plugin.plugin.msg.uninstallFailed'))
  }
}

/** 查看审计日志 */
async function handleViewAuditLog(row: PluginPackage) {
  currentPluginCode.value = row.pluginCode
  auditLogVisible.value = true
  auditLogPage.value = 1
  await loadAuditLogs()
}

/** 加载审计日志 */
async function loadAuditLogs() {
  auditLogLoading.value = true
  try {
    const res = await getPluginAuditLogs(
      auditLogPage.value,
      auditLogPageSize.value,
      currentPluginCode.value
    )
    auditLogs.value = res.records || []
    auditLogTotal.value = res.total || 0
  } catch (e) {
    ElMessage.error(t('plugin.plugin.msg.auditLogFailed'))
  } finally {
    auditLogLoading.value = false
  }
}

/** 审计日志分页变更 */
function handleAuditLogPageChange(page: number) {
  auditLogPage.value = page
  loadAuditLogs()
}

/** 格式化时间 */
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
  <div class="plugin-page">
    <!-- 顶部筛选栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('plugin.plugin.field.keyword')">
          <el-input
            v-model="filters.keyword"
            :placeholder="$t('plugin.plugin.placeholder.keyword')"
            clearable
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="$t('plugin.plugin.field.status')">
          <el-select
            v-model="filters.status"
            :placeholder="$t('plugin.plugin.placeholder.all')"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="o in statusOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('plugin.plugin.field.riskLevel')">
          <el-select
            v-model="filters.riskLevel"
            :placeholder="$t('plugin.plugin.placeholder.all')"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="o in riskLevelOptions"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('plugin.plugin.action.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('plugin.plugin.action.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 插件列表 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="tableData" border stripe>
        <el-table-column prop="pluginCode" :label="$t('plugin.plugin.field.code')" width="180" />
        <el-table-column prop="pluginName" :label="$t('plugin.plugin.field.name')" min-width="160" />
        <el-table-column prop="pluginVersion" :label="$t('plugin.plugin.field.version')" width="100" align="center" />
        <el-table-column :label="$t('plugin.plugin.field.signatureStatus')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.signatureStatus === 'VALID' ? 'success' : row.signatureStatus === 'INVALID' ? 'danger' : 'info'"
              size="small"
            >
              {{ signatureStatusMap[row.signatureStatus] || row.signatureStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('plugin.plugin.field.riskLevel')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.riskLevel === 'LOW' ? 'success' : row.riskLevel === 'MEDIUM' ? 'warning' : 'danger'"
              size="small"
            >
              {{ riskLevelMap[row.riskLevel] || row.riskLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('plugin.plugin.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'VERIFIED' ? 'success' : row.status === 'REJECTED' ? 'danger' : row.status === 'DEPRECATED' ? 'info' : 'warning'"
              size="small"
            >
              {{ statusMap[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="installCount" :label="$t('plugin.plugin.field.installCount')" width="100" align="center" />
        <el-table-column prop="updatedTime" :label="$t('plugin.plugin.field.updatedTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column :label="$t('plugin.plugin.field.operation')" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="handleInstall(row as PluginPackage)"
            >
              {{ $t('plugin.plugin.action.install') }}
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              @click="handleUninstall(row as PluginPackage)"
            >
              {{ $t('plugin.plugin.action.uninstall') }}
            </el-button>
            <el-button
              link
              type="info"
              size="small"
              @click="handleViewAuditLog(row as PluginPackage)"
            >
              {{ $t('plugin.plugin.action.auditLog') }}
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

    <!-- 审计日志对话框 -->
    <el-dialog
      v-model="auditLogVisible"
      :title="$t('plugin.plugin.dialog.auditLogTitle', { code: currentPluginCode })"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-table :data="auditLogs" border stripe v-loading="auditLogLoading">
        <el-table-column prop="bizType" :label="$t('plugin.plugin.field.bizType')" width="120" />
        <el-table-column prop="pluginVersion" :label="$t('plugin.plugin.field.pluginVersion')" width="100" />
        <el-table-column prop="operatorId" :label="$t('plugin.plugin.field.operator')" width="120" />
        <el-table-column :label="$t('plugin.plugin.field.result')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.result === 'SUCCESS' ? $t('plugin.plugin.status.success') : $t('plugin.plugin.status.failed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="traceId" :label="$t('plugin.plugin.field.traceId')" width="180" show-overflow-tooltip />
        <el-table-column prop="createdTime" :label="$t('plugin.plugin.field.operationTime')" width="180">
          <template #default="{ row }">{{ formatTime(row.createdTime) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top: 16px; justify-content: flex-end"
        v-model:current-page="auditLogPage"
        :page-size="auditLogPageSize"
        :total="auditLogTotal"
        layout="total, prev, pager, next"
        @current-change="handleAuditLogPageChange"
      />
    </el-dialog>
  </div>
</template>

<style scoped>
.plugin-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.filter-card :deep(.el-card__body) {
  padding: 16px 20px 0 20px;
}
</style>
