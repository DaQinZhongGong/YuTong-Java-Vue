<script setup lang="ts">
/**
 * 数据源管理列表。设计来源: 46-多数据源与数据集设计 (GA2-46 v1.5)
 *
 * 功能:
 *   1. 分页查询数据源 (脱敏)
 *   2. 新建/编辑数据源 (密钥外置: usernameRef/passwordRef 只存密钥引用)
 *   3. 连接测试 (只返回连通性和脱敏摘要)
 *   4. 删除数据源 (primary 不允许删除)
 *   5. 跳转元数据浏览页
 *
 * 安全约束:
 *   - jdbc_url 脱敏显示
 *   - 密钥引用不显示真实值
 *   - 连接测试失败不泄露数据库地址/用户名/密码/完整驱动异常
 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageDatasources,
  createDatasource,
  updateDatasource,
  deleteDatasource,
  testDatasourceConnection,
  type DatasourceVO,
  type SaveDatasourceRequest,
  type ConnectionTestResultVO,
} from '@/api/datasource'
import type { PageResult } from '@/api/types'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const tableData = ref<DatasourceVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

// 筛选条件
const filterKeyword = ref('')

const DB_TYPE_OPTIONS = [
  { label: 'PostgreSQL', value: 'POSTGRESQL' },
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'Oracle', value: 'ORACLE' },
  { label: 'SQL Server', value: 'SQLSERVER' },
]

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function healthTagType(health?: string): TagType {
  const map: Record<string, TagType> = {
    UP: 'success',
    DOWN: 'danger',
    UNKNOWN: 'info',
  }
  return map[health || 'UNKNOWN'] || 'info'
}

function healthLabel(health?: string): string {
  const map: Record<string, string> = {
    UP: t('datasource.msg.healthUp'),
    DOWN: t('datasource.msg.healthDown'),
    UNKNOWN: t('datasource.msg.healthUnknown'),
  }
  return map[health || 'UNKNOWN'] || t('datasource.msg.healthUnknown')
}

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<DatasourceVO> = await pageDatasources({
      page: currentPage.value,
      size: pageSize.value,
      keyword: filterKeyword.value || undefined,
    })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadData()
}

function handleReset() {
  filterKeyword.value = ''
  currentPage.value = 1
  loadData()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadData()
}

// ==================== 编辑弹窗 ====================

const editDialogVisible = ref(false)
const editForm = ref<SaveDatasourceRequest>(emptyForm())
const editing = ref(false)

function emptyForm(): SaveDatasourceRequest {
  return {
    datasourceCode: '',
    datasourceName: '',
    dbType: 'POSTGRESQL',
    jdbcUrl: '',
    usernameRef: '',
    passwordRef: '',
    poolConfig: '',
    readOnly: false,
    enabled: false,
    lagThresholdMs: 3000,
    description: '',
  }
}

function openCreateDialog() {
  editForm.value = emptyForm()
  editDialogVisible.value = true
}

function openEditDialog(row: DatasourceVO) {
  editForm.value = {
    datasourceCode: row.datasourceCode,
    datasourceName: row.datasourceName,
    dbType: row.dbType,
    jdbcUrl: '',
    usernameRef: row.usernameRef || '',
    passwordRef: row.passwordRef || '',
    poolConfig: row.poolConfig || '',
    readOnly: row.readOnly || false,
    enabled: row.enabled || false,
    lagThresholdMs: row.lagThresholdMs || 3000,
    description: row.description || '',
  }
  editDialogVisible.value = true
}

async function handleSave() {
  if (!editForm.value.datasourceCode.trim()) {
    ElMessage.warning(t('datasource.msg.codeRequired'))
    return
  }
  if (!editForm.value.datasourceName.trim()) {
    ElMessage.warning(t('datasource.msg.nameRequired'))
    return
  }
  if (!editForm.value.jdbcUrl.trim()) {
    ElMessage.warning(t('datasource.msg.jdbcUrlRequired'))
    return
  }
  editing.value = true
  try {
    // 判断是新建还是更新: 查找 tableData 中是否有相同 datasourceCode
    const existing = tableData.value.find((d) => d.datasourceCode === editForm.value.datasourceCode)
    if (existing) {
      await updateDatasource(editForm.value.datasourceCode, editForm.value)
      ElMessage.success(t('datasource.msg.updateSuccess'))
    } else {
      await createDatasource(editForm.value)
      ElMessage.success(t('datasource.msg.createSuccess'))
    }
    editDialogVisible.value = false
    loadData()
  } finally {
    editing.value = false
  }
}

// ==================== 连接测试 ====================

const testingCode = ref<string | null>(null)

async function handleTest(row: DatasourceVO) {
  testingCode.value = row.datasourceCode
  try {
    const result: ConnectionTestResultVO = await testDatasourceConnection(row.datasourceCode)
    if (result.connected) {
      ElMessage.success(
        t('datasource.msg.connectionSuccess', { info: result.databaseProductName || '', version: result.databaseProductVersion || '', latency: result.latencyMs || 0 }),
      )
    } else {
      ElMessage.error(t('datasource.msg.connectionFailed', { error: result.errorMessage || t('datasource.msg.unknownError') }))
    }
    loadData() // 刷新健康状态
  } finally {
    testingCode.value = null
  }
}

// ==================== 删除 ====================

async function handleDelete(row: DatasourceVO) {
  if (row.datasourceCode === 'primary') {
    ElMessage.warning(t('datasource.msg.primaryNotDeletable'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('datasource.msg.deleteConfirm', { name: row.datasourceName, code: row.datasourceCode }),
      t('datasource.msg.deleteConfirmTitle'),
      { type: 'warning' },
    )
    await deleteDatasource(row.datasourceCode)
    ElMessage.success(t('datasource.msg.deleteSuccess'))
    loadData()
  } catch {
    // 用户取消
  }
}

// ==================== 元数据浏览 ====================

function goMetadata(row: DatasourceVO) {
  router.push({
    name: 'DataSourceMetadata',
    params: { code: row.datasourceCode },
  })
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('datasource.page.list') }}</h2>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" size="small">
        <el-form-item :label="$t('datasource.filter.keyword')">
          <el-input
            v-model="filterKeyword"
            :placeholder="$t('datasource.placeholder.keyword')"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('datasource.action.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('datasource.action.reset') }}</el-button>
          <el-button type="success" @click="openCreateDialog">{{ $t('datasource.action.create') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="datasourceCode" :label="$t('datasource.field.code')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="datasourceName" :label="$t('datasource.field.name')" min-width="160" show-overflow-tooltip />
      <el-table-column prop="dbType" :label="$t('datasource.field.type')" width="120" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ (row as DatasourceVO).dbType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="jdbcUrlMasked" :label="$t('datasource.field.jdbcUrlMasked')" min-width="280" show-overflow-tooltip />
      <el-table-column prop="readOnly" :label="$t('datasource.field.readOnly')" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="(row as DatasourceVO).readOnly ? 'warning' : 'info'" size="small">
            {{ (row as DatasourceVO).readOnly ? $t('datasource.boolean.yes') : $t('datasource.boolean.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" :label="$t('datasource.field.enabled')" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="(row as DatasourceVO).enabled ? 'success' : 'info'" size="small">
            {{ (row as DatasourceVO).enabled ? $t('datasource.status.enabled') : $t('datasource.status.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="healthStatus" :label="$t('datasource.field.health')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="healthTagType((row as DatasourceVO).healthStatus)" size="small">
            {{ healthLabel((row as DatasourceVO).healthStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="configVersion" :label="$t('datasource.field.version')" width="70" align="center" />
      <el-table-column :label="$t('datasource.field.operation')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            size="small"
            :loading="testingCode === (row as DatasourceVO).datasourceCode"
            @click="handleTest(row as DatasourceVO)"
          >
            {{ $t('datasource.action.test') }}
          </el-button>
          <el-button type="primary" link size="small" @click="openEditDialog(row as DatasourceVO)">
            {{ $t('datasource.action.edit') }}
          </el-button>
          <el-button type="success" link size="small" @click="goMetadata(row as DatasourceVO)">
            {{ $t('datasource.action.metadata') }}
          </el-button>
          <el-button
            type="danger"
            link
            size="small"
            :disabled="(row as DatasourceVO).datasourceCode === 'primary'"
            @click="handleDelete(row as DatasourceVO)"
          >
            {{ $t('datasource.action.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="handlePageChange"
    />

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="tableData.find((d) => d.datasourceCode === editForm.datasourceCode) ? $t('datasource.dialog.titleEdit') : $t('datasource.dialog.titleCreate')"
      width="780px"
    >
      <el-form label-width="140px" size="small">
        <el-form-item :label="$t('datasource.field.datasourceCode')" required>
          <el-input
            v-model="editForm.datasourceCode"
            :disabled="!!tableData.find((d) => d.datasourceCode === editForm.datasourceCode)"
            :placeholder="$t('datasource.placeholder.datasourceCode')"
            maxlength="64"
            show-word-limit
          />
          <div class="form-tip">{{ $t('datasource.tip.datasourceCode') }}</div>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.datasourceName')" required>
          <el-input v-model="editForm.datasourceName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('datasource.field.dbType')" required>
          <el-select v-model="editForm.dbType" style="width: 100%">
            <el-option v-for="opt in DB_TYPE_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.jdbcUrl')" required>
          <el-input
            v-model="editForm.jdbcUrl"
            type="textarea"
            :rows="2"
            placeholder="jdbc:postgresql://host:5432/db"
            maxlength="512"
            show-word-limit
          />
          <div class="form-tip">{{ $t('datasource.tip.jdbcUrlEnv') }}</div>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.usernameRef')">
          <el-input
            v-model="editForm.usernameRef"
            :placeholder="$t('datasource.placeholder.usernameRef')"
            maxlength="128"
          />
          <div class="form-tip">{{ $t('datasource.tip.secretRef') }}</div>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.passwordRef')">
          <el-input
            v-model="editForm.passwordRef"
            :placeholder="$t('datasource.placeholder.passwordRef')"
            maxlength="128"
          />
          <div class="form-tip">{{ $t('datasource.tip.secretRef') }}</div>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.poolConfig')">
          <el-input
            v-model="editForm.poolConfig"
            type="textarea"
            :rows="2"
            placeholder='{"maximumPoolSize":10,"minimumIdle":2,"connectionTimeout":30000}'
          />
          <div class="form-tip">{{ $t('datasource.tip.poolConfig') }}</div>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.readOnly')">
          <el-switch v-model="editForm.readOnly" />
          <div class="form-tip">{{ $t('datasource.tip.readOnly') }}</div>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.enabled')">
          <el-switch v-model="editForm.enabled" />
          <div class="form-tip">{{ $t('datasource.tip.enabled') }}</div>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.lagThresholdMs')">
          <el-input-number v-model="editForm.lagThresholdMs" :min="0" :max="60000" :step="1000" />
          <div class="form-tip">{{ $t('datasource.tip.lagThresholdMs') }}</div>
        </el-form-item>
        <el-form-item :label="$t('datasource.field.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="512" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ $t('datasource.action.cancel') }}</el-button>
        <el-button type="primary" :loading="editing" @click="handleSave">{{ $t('datasource.action.save') }}</el-button>
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
.filter-card {
  margin-bottom: 16px;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
</style>
