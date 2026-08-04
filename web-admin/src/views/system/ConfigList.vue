<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Edit, Delete, Refresh } from '@element-plus/icons-vue'
import {
  getConfigs,
  createConfig,
  updateConfig,
  deleteConfig,
  refreshConfigCache,
  type ConfigPageQuery,
} from '@/api/system'
import type { SysConfig } from '@/api/types'
import i18n from '@/locales'

const loading = ref(false)
const tableData = ref<SysConfig[]>([])
const total = ref(0)

const query = reactive<ConfigPageQuery>({
  page: 1,
  size: 10,
  keyword: '',
  configGroup: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await getConfigs(query)
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
  query.configGroup = ''
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
const form = reactive<Partial<SysConfig>>({
  id: '',
  configKey: '',
  configValue: '',
  valueType: 'STRING',
  configGroup: '',
  editable: true,
  sensitive: false,
  status: 'ENABLED',
  remark: '',
})
const isEdit = ref(false)

function openCreate() {
  isEdit.value = false
  dialogTitle.value = i18n.global.t('system.config.dialog.createTitle')
  Object.assign(form, {
    id: '',
    configKey: '',
    configValue: '',
    valueType: 'STRING',
    configGroup: '',
    editable: true,
    sensitive: false,
    status: 'ENABLED',
    remark: '',
  })
  dialogVisible.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  dialogTitle.value = i18n.global.t('system.config.dialog.editTitle')
  Object.assign(form, {
    id: row.id,
    configKey: row.configKey,
    configValue: row.configValue === '******' ? '' : row.configValue,
    valueType: row.valueType,
    configGroup: row.configGroup,
    editable: row.editable,
    sensitive: row.sensitive,
    status: row.status,
    remark: row.remark,
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.configKey) {
    ElMessage.warning(i18n.global.t('system.config.message.keyRequired'))
    return
  }
  submitting.value = true
  try {
    if (isEdit.value && form.id) {
      await updateConfig(form.id, form)
      ElMessage.success(i18n.global.t('system.config.message.updateSuccess'))
    } else {
      await createConfig(form)
      ElMessage.success(i18n.global.t('system.config.message.createSuccess'))
    }
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SysConfig) {
  try {
    await ElMessageBox.confirm(
      i18n.global.t('system.config.message.deleteConfirm', { key: row.configKey }),
      i18n.global.t('common.message.tip'),
      { confirmButtonText: i18n.global.t('common.action.confirm'), cancelButtonText: i18n.global.t('common.action.cancel'), type: 'warning' }
    )
    await deleteConfig(row.id)
    ElMessage.success(i18n.global.t('system.config.message.deleteSuccess'))
    fetchData()
  } catch {
    // 用户取消
  }
}

async function handleRefreshCache() {
  try {
    await ElMessageBox.confirm(i18n.global.t('system.config.message.refreshCacheConfirm'), i18n.global.t('common.message.tip'), {
      confirmButtonText: i18n.global.t('common.action.confirm'),
      cancelButtonText: i18n.global.t('common.action.cancel'),
      type: 'info',
    })
    await refreshConfigCache()
    ElMessage.success(i18n.global.t('system.config.message.cacheRefreshed'))
  } catch {
    // 用户取消
  }
}

onMounted(fetchData)
</script>

<template>
  <div class="config-view">
    <h2 class="page-title" id="page-title">{{ $t('system.config.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('system.config.aria.search')">
        <el-input
          v-model="query.keyword"
          :placeholder="$t('system.config.placeholder.keyword')"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          :aria-label="$t('system.config.placeholder.keyword')"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="query.configGroup"
          :placeholder="$t('system.config.placeholder.configGroup')"
          clearable
          style="width: 180px"
          :aria-label="$t('system.config.placeholder.configGroup')"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        <!-- GA2-16: 新建/刷新缓存按 system:config:add / system:config:refresh-cache 权限码隐藏 (96 号文档) -->
        <el-button v-permission="'system:config:add'" type="success" :icon="Plus" @click="openCreate">{{ $t('common.action.create') }}</el-button>
        <el-button v-permission="'system:config:refresh-cache'" :icon="Refresh" @click="handleRefreshCache">{{ $t('system.config.action.refreshCache') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('system.config.empty.noData')"
        :aria-label="$t('system.config.aria.list')"
      >
        <el-table-column prop="configKey" :label="$t('system.config.field.key')" min-width="180" />
        <el-table-column prop="configValue" :label="$t('system.config.field.value')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="valueType" :label="$t('system.config.field.valueType')" width="100" align="center" />
        <el-table-column prop="configGroup" :label="$t('system.config.field.group')" width="120" align="center" />
        <el-table-column :label="$t('system.config.field.sensitiveFlag')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sensitive ? 'danger' : 'info'" size="small">
              {{ row.sensitive ? $t('system.config.field.sensitiveFlag') : $t('system.config.tag.normal') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('system.config.field.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? $t('system.config.status.enabled') : $t('system.config.status.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('system.config.field.createdAt')" width="180" />
        <el-table-column :label="$t('system.config.field.operation')" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <!-- GA2-16: 编辑/删除按 system:config:edit / system:config:remove 权限码隐藏 -->
            <el-button
              v-permission="'system:config:edit'"
              type="primary"
              link
              :icon="Edit"
              size="small"
              :aria-label="$t('common.action.edit')"
              @click="openEdit(row as SysConfig)"
            >{{ $t('common.action.edit') }}</el-button>
            <el-button
              v-permission="'system:config:remove'"
              type="danger"
              link
              :icon="Delete"
              size="small"
              :aria-label="$t('common.action.delete')"
              @click="handleDelete(row as SysConfig)"
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
      width="560px"
      :aria-label="$t('system.config.aria.formDialog')"
    >
      <el-form label-width="100px" :model="form">
        <el-form-item :label="$t('system.config.field.key')">
          <el-input
            v-model="form.configKey"
            :placeholder="$t('system.config.placeholder.configKeyExample')"
            :disabled="isEdit"
            :aria-label="$t('system.config.field.key')"
          />
        </el-form-item>
        <el-form-item :label="$t('system.config.field.value')">
          <el-input
            v-model="form.configValue"
            type="textarea"
            :rows="2"
            :placeholder="form.sensitive ? $t('system.config.placeholder.sensitiveHint') : $t('system.config.placeholder.configValue')"
            :aria-label="$t('system.config.field.value')"
          />
        </el-form-item>
        <el-form-item :label="$t('system.config.field.valueType')">
          <el-select v-model="form.valueType" style="width: 100%" :aria-label="$t('system.config.field.valueType')">
            <el-option :label="$t('system.config.option.string')" value="STRING" />
            <el-option :label="$t('system.config.option.number')" value="NUMBER" />
            <el-option :label="$t('system.config.option.boolean')" value="BOOLEAN" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.config.field.group')">
          <el-input v-model="form.configGroup" :placeholder="$t('system.config.placeholder.groupExample')" :aria-label="$t('system.config.field.group')" />
        </el-form-item>
        <el-form-item :label="$t('system.config.field.editable')">
          <el-switch v-model="form.editable" :aria-label="$t('system.config.field.editable')" />
        </el-form-item>
        <el-form-item :label="$t('system.config.field.sensitiveConfig')">
          <el-switch v-model="form.sensitive" :aria-label="$t('system.config.field.sensitiveConfig')" />
        </el-form-item>
        <el-form-item :label="$t('system.config.field.status')">
          <el-select v-model="form.status" style="width: 100%" :aria-label="$t('system.config.field.status')">
            <el-option :label="$t('system.config.status.enabled')" value="ENABLED" />
            <el-option :label="$t('system.config.status.disabled')" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('system.config.field.remark')">
          <el-input v-model="form.remark" type="textarea" :rows="2" :aria-label="$t('system.config.field.remark')" />
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
