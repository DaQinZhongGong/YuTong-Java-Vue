<script setup lang="ts">
/**
 * 数据源元数据浏览器。设计来源: 46-多数据源与数据集设计 (GA2-46 v1.5)
 *
 * 功能:
 *   1. 左侧: 表列表 (按 ACL 过滤, 不返回未授权表)
 *   2. 右侧: 选中表的列列表 (按 ACL 过滤, 敏感列隐藏)
 *   3. 顶部: 数据源切换 + 刷新
 *
 * 安全约束:
 *   - 表/列元数据按 ACL 过滤
 *   - 敏感列 (HIGH/CRITICAL 且未授权) 不出现
 *   - masking_strategy HIDE 的列不出现
 *   - 敏感列的 default/comment 也不返回
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  listDatasourceTables,
  listDatasourceColumns,
  getDatasource,
  type DatasourceVO,
  type TableMetadataVO,
  type ColumnMetadataVO,
} from '@/api/datasource'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const datasourceCode = ref<string>((route.params.code as string) || 'primary')
const currentDatasource = ref<DatasourceVO | null>(null)

const tablesLoading = ref(false)
const tables = ref<TableMetadataVO[]>([])
const selectedTable = ref<string>('')

const columnsLoading = ref(false)
const columns = ref<ColumnMetadataVO[]>([])

async function loadDatasource() {
  try {
    currentDatasource.value = await getDatasource(datasourceCode.value)
  } catch {
    currentDatasource.value = null
  }
}

async function loadTables() {
  tablesLoading.value = true
  tables.value = []
  selectedTable.value = ''
  columns.value = []
  try {
    tables.value = await listDatasourceTables(datasourceCode.value)
  } catch (e: any) {
    ElMessage.error(t('datasource.msg.loadTablesFailed', { error: e?.message || t('datasource.msg.unknownError') }))
  } finally {
    tablesLoading.value = false
  }
}

async function loadColumns(tableName: string) {
  if (!tableName) {
    columns.value = []
    return
  }
  columnsLoading.value = true
  columns.value = []
  try {
    columns.value = await listDatasourceColumns(datasourceCode.value, tableName)
  } catch (e: any) {
    ElMessage.error(t('datasource.msg.loadColumnsFailed', { error: e?.message || t('datasource.msg.unknownError') }))
  } finally {
    columnsLoading.value = false
  }
}

function handleTableSelect(row: TableMetadataVO | undefined | null) {
  if (row) {
    selectedTable.value = row.tableName
    loadColumns(row.tableName)
  }
}

function handleRefresh() {
  loadTables()
}

watch(
  () => route.params.code,
  (newCode) => {
    if (newCode && newCode !== datasourceCode.value) {
      datasourceCode.value = newCode as string
      loadDatasource()
      loadTables()
    }
  },
)

type TagType = 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined

function sensitivityTagType(level?: string): TagType {
  const map: Record<string, TagType> = {
    LOW: 'info',
    MEDIUM: undefined,
    HIGH: 'warning',
    CRITICAL: 'danger',
  }
  return map[level || ''] || 'info'
}

function tableTypeTagType(t?: string): TagType {
  if (t === 'VIEW') return 'success'
  return 'primary'
}

function formatRows(rows?: number): string {
  if (rows == null) return '-'
  if (rows < 1000) return String(rows)
  if (rows < 1000000) return (rows / 1000).toFixed(1) + 'K'
  return (rows / 1000000).toFixed(1) + 'M'
}

function goBack() {
  router.push({ name: 'DataSourceList' })
}

onMounted(() => {
  loadDatasource()
  loadTables()
})
</script>

<template>
  <div>
    <div class="header">
      <h2 class="page-title">{{ $t('datasource.page.metadata') }}</h2>
      <el-button size="small" @click="goBack">{{ $t('datasource.action.back') }}</el-button>
    </div>

    <!-- 数据源信息 -->
    <el-card class="ds-info-card" shadow="never">
      <el-descriptions :column="4" size="small" border>
        <el-descriptions-item :label="$t('datasource.field.datasourceCode')">{{ datasourceCode }}</el-descriptions-item>
        <el-descriptions-item :label="$t('datasource.field.name')">{{ currentDatasource?.datasourceName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('datasource.field.type')">
          <el-tag size="small" type="info">{{ currentDatasource?.dbType || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('datasource.field.readOnly')">
          <el-tag :type="currentDatasource?.readOnly ? 'warning' : 'info'" size="small">
            {{ currentDatasource?.readOnly ? $t('datasource.boolean.yes') : $t('datasource.boolean.no') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('datasource.field.jdbcUrlMasked')">{{ currentDatasource?.jdbcUrlMasked || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('datasource.field.healthStatus')">
          <el-tag :type="currentDatasource?.healthStatus === 'UP' ? 'success' : currentDatasource?.healthStatus === 'DOWN' ? 'danger' : 'info'" size="small">
            {{ currentDatasource?.healthStatus || 'UNKNOWN' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('datasource.field.configVersion')">{{ currentDatasource?.configVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('datasource.field.operation')">
          <el-button size="small" type="primary" link @click="handleRefresh">{{ $t('datasource.action.refresh') }}</el-button>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 左右分栏 -->
    <el-row :gutter="16" class="metadata-row">
      <el-col :span="8">
        <el-card shadow="never" class="table-card">
          <template #header>
            <div class="card-header">
              <span>{{ $t('datasource.page.tableList') }} ({{ tables.length }})</span>
              <el-button size="small" type="primary" link @click="handleRefresh">{{ $t('datasource.action.refresh') }}</el-button>
            </div>
          </template>
          <el-table
            v-loading="tablesLoading"
            :data="tables"
            border
            stripe
            highlight-current-row
            size="small"
            @current-change="handleTableSelect"
            :row-class-name="() => 'clickable-row'"
          >
            <el-table-column prop="tableName" :label="$t('datasource.field.tableName')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="tableType" :label="$t('datasource.field.type')" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="tableTypeTagType((row as TableMetadataVO).tableType)" size="small">
                  {{ (row as TableMetadataVO).tableType === 'VIEW' ? 'VIEW' : 'TABLE' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="estimatedRows" :label="$t('datasource.field.rows')" width="80" align="right">
              <template #default="{ row }">
                {{ formatRows((row as TableMetadataVO).estimatedRows) }}
              </template>
            </el-table-column>
            <el-table-column prop="queryAllowed" :label="$t('datasource.field.queryAllowed')" width="60" align="center">
              <template #default="{ row }">
                <el-tag :type="(row as TableMetadataVO).queryAllowed === false ? 'danger' : 'success'" size="small">
                  {{ (row as TableMetadataVO).queryAllowed === false ? $t('datasource.boolean.no') : $t('datasource.boolean.yes') }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="never" class="column-card">
          <template #header>
            <div class="card-header">
              <span>
                {{ $t('datasource.page.columnList') }}
                <span v-if="selectedTable"> - {{ selectedTable }} ({{ columns.length }} {{ $t('datasource.text.columns') }})</span>
              </span>
            </div>
          </template>
          <el-table
            v-loading="columnsLoading"
            :data="columns"
            border
            stripe
            size="small"
            :empty-text="selectedTable ? $t('datasource.empty.noVisibleColumns') : $t('datasource.empty.selectTable')"
          >
            <el-table-column prop="ordinalPosition" label="#" width="50" align="center" />
            <el-table-column prop="columnName" :label="$t('datasource.field.columnName')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="dataType" :label="$t('datasource.field.dataType')" width="140" />
            <el-table-column prop="nullable" :label="$t('datasource.field.nullable')" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="(row as ColumnMetadataVO).nullable ? 'info' : 'warning'" size="small">
                  {{ (row as ColumnMetadataVO).nullable ? $t('datasource.boolean.yes') : $t('datasource.boolean.no') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="columnSize" :label="$t('datasource.field.length')" width="70" align="right">
              <template #default="{ row }">
                {{ (row as ColumnMetadataVO).columnSize || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="columnDefault" :label="$t('datasource.field.defaultValue')" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">
                {{ (row as ColumnMetadataVO).columnDefault || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="sensitivityLevel" :label="$t('datasource.field.sensitivityLevel')" width="100" align="center">
              <template #default="{ row }">
                <el-tag
                  v-if="(row as ColumnMetadataVO).sensitivityLevel"
                  :type="sensitivityTagType((row as ColumnMetadataVO).sensitivityLevel)"
                  size="small"
                >
                  {{ (row as ColumnMetadataVO).sensitivityLevel }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="maskingStrategy" :label="$t('datasource.field.maskingStrategy')" width="100" align="center">
              <template #default="{ row }">
                <el-tag v-if="(row as ColumnMetadataVO).maskingStrategy" size="small" type="info">
                  {{ (row as ColumnMetadataVO).maskingStrategy }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.ds-info-card {
  margin-bottom: 16px;
}
.metadata-row {
  margin-top: 0;
}
.table-card,
.column-card {
  height: 100%;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
:deep(.clickable-row) {
  cursor: pointer;
}
</style>
