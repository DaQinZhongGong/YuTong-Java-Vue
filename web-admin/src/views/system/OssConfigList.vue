<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Connection, Delete, Edit,
  Star, Lock, Unlock
} from '@element-plus/icons-vue'
import {
  getOssConfigs, saveOssConfig, deleteOssConfig,
  enableOssConfig, disableOssConfig, setDefaultOssConfig,
  testOssConfig, getOssConfig
} from '@/api/ossConfig'
import type { OssConfigInfo, SaveOssConfigRequest } from '@/api/ossConfig'

const loading = ref(false)
const tableData = ref<OssConfigInfo[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const dialogTitle = ref('新建配置')
const testing = ref('')

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  status: '',
  keyword: '',
})

const form = reactive<SaveOssConfigRequest>({
  id: undefined,
  configKey: '',
  configName: '',
  storageType: 'MINIO',
  endpoint: '',
  accessKey: '',
  secretKey: '',
  bucketName: '',
  domain: '',
  region: '',
  isHttps: false,
  isDefault: false,
  remark: '',
})

const formRules = {
  configKey: [
    { required: true, message: '请输入配置键', trigger: 'blur' },
    { pattern: /^[a-z0-9][a-z0-9_-]{1,62}[a-z0-9]$/, message: '仅允许小写字母/数字/下划线/中划线, 3-64位', trigger: 'blur' },
  ],
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  storageType: [{ required: true, message: '请选择存储类型', trigger: 'change' }],
  endpoint: [{ required: true, message: '请输入端点', trigger: 'blur' }],
  accessKey: [{ required: true, message: '请输入访问密钥', trigger: 'blur' }],
  secretKey: [{ required: true, message: '请输入密钥Secret', trigger: 'blur' }],
  bucketName: [{ required: true, message: '请输入桶名', trigger: 'blur' }],
}

const formRef = ref()

async function loadData() {
  loading.value = true
  try {
    const res = await getOssConfigs(query)
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
  query.pageNo = 1
  loadData()
}

function handleReset() {
  query.status = ''
  query.keyword = ''
  query.pageNo = 1
  loadData()
}

function resetForm() {
  form.id = undefined
  form.configKey = ''
  form.configName = ''
  form.storageType = 'MINIO'
  form.endpoint = ''
  form.accessKey = ''
  form.secretKey = ''
  form.bucketName = ''
  form.domain = ''
  form.region = ''
  form.isHttps = false
  form.isDefault = false
  form.remark = ''
}

function handleCreate() {
  resetForm()
  dialogTitle.value = '新建 OSS 配置'
  dialogVisible.value = true
}

async function handleEdit(row: OssConfigInfo) {
  try {
    const detail = await getOssConfig(row.id)
    form.id = detail.id
    form.configKey = detail.configKey
    form.configName = detail.configName
    form.storageType = detail.storageType
    form.endpoint = detail.endpoint
    form.accessKey = detail.accessKey
    form.secretKey = detail.secretKey
    form.bucketName = detail.bucketName
    form.domain = detail.domain || ''
    form.region = detail.region || ''
    form.isHttps = detail.isHttps
    form.isDefault = detail.isDefault
    form.remark = detail.remark || ''
    dialogTitle.value = '编辑 OSS 配置'
    dialogVisible.value = true
  } catch {
    // 错误已由拦截器处理
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  try {
    await saveOssConfig(form)
    ElMessage.success(form.id ? '更新成功' : '创建成功')
    dialogVisible.value = false
    loadData()
  } catch {
    // 错误已由拦截器处理
  }
}

async function handleDelete(row: OssConfigInfo) {
  try {
    await ElMessageBox.confirm(
      `确定删除配置「${row.configName}」吗？删除后不可恢复。`,
      '确认删除',
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteOssConfig(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleToggleStatus(row: OssConfigInfo) {
  if (row.status === 'ENABLED') {
    await disableOssConfig(row.id)
    ElMessage.success('已停用')
  } else {
    await enableOssConfig(row.id)
    ElMessage.success('已启用')
  }
  loadData()
}

async function handleSetDefault(row: OssConfigInfo) {
  try {
    await ElMessageBox.confirm(
      `确定将「${row.configName}」设为默认配置吗？当前默认配置将被取消。`,
      '设为默认',
      { type: 'info' }
    )
  } catch {
    return
  }
  await setDefaultOssConfig(row.id)
  ElMessage.success('已设为默认')
  loadData()
}

async function handleTest(row: OssConfigInfo) {
  testing.value = row.id
  try {
    const ok = await testOssConfig(row.id)
    if (ok) {
      ElMessage.success(`连接成功: ${row.bucketName}`)
    } else {
      ElMessage.error(`连接失败: 请检查端点/密钥/桶名`)
    }
  } finally {
    testing.value = ''
  }
}

function statusTag(status: string): 'success' | 'info' {
  return status === 'ENABLED' ? 'success' : 'info'
}

function storageTag(type: string): 'primary' | 'warning' | 'info' {
  if (type === 'MINIO') return 'primary'
  if (type === 'S3') return 'warning'
  return 'info'
}

onMounted(loadData)
</script>

<template>
  <div>
    <h2 class="page-title">OSS 配置管理</h2>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="配置键/名称/桶名"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button
          v-permission="'system:oss-config:save'"
          type="success"
          :icon="Plus"
          @click="handleCreate"
        >新建配置</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="configKey" label="配置键" width="140">
          <template #default="{ row }">
            <span class="config-key">{{ row.configKey }}</span>
            <el-tag v-if="row.isDefault" type="warning" size="small" style="margin-left: 6px">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="configName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="storageType" label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="storageTag(row.storageType)" size="small">{{ row.storageType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="endpoint" label="端点" min-width="180" show-overflow-tooltip />
        <el-table-column prop="bucketName" label="桶名" width="120" />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:oss-config:save'"
              type="primary" link size="small" :icon="Edit"
              @click="handleEdit(row as OssConfigInfo)"
            >编辑</el-button>
            <el-button
              v-permission="'system:oss-config:detail'"
              type="info" link size="small" :icon="Connection"
              :loading="testing === row.id"
              @click="handleTest(row as OssConfigInfo)"
            >测试</el-button>
            <el-button
              v-if="!row.isDefault"
              v-permission="'system:oss-config:save'"
              type="warning" link size="small" :icon="Star"
              @click="handleSetDefault(row as OssConfigInfo)"
            >设默认</el-button>
            <el-button
              v-permission="row.status === 'ENABLED' ? 'system:oss-config:disable' : 'system:oss-config:enable'"
              :type="row.status === 'ENABLED' ? 'warning' : 'success'"
              link size="small"
              :icon="row.status === 'ENABLED' ? Lock : Unlock"
              @click="handleToggleStatus(row as OssConfigInfo)"
            >{{ row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
            <el-button
              v-if="!row.isDefault"
              v-permission="'system:oss-config:delete'"
              type="danger" link size="small" :icon="Delete"
              @click="handleDelete(row as OssConfigInfo)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadData"
          @size-change="query.pageNo = 1; loadData()"
        />
      </div>
    </el-card>

    <!-- 新建/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="配置键" prop="configKey">
          <el-input
            v-model="form.configKey"
            placeholder="如 default / backup / cdn"
            :disabled="!!form.id"
          />
          <div class="form-tip">创建后不可修改, 用于 API 引用</div>
        </el-form-item>
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="如 生产环境 MinIO" />
        </el-form-item>
        <el-form-item label="存储类型" prop="storageType">
          <el-radio-group v-model="form.storageType">
            <el-radio-button value="MINIO">MinIO</el-radio-button>
            <el-radio-button value="S3">S3</el-radio-button>
            <el-radio-button value="LOCAL">Local</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="端点" prop="endpoint">
          <el-input v-model="form.endpoint" placeholder="如 http://minio:9000" />
        </el-form-item>
        <el-form-item label="访问密钥" prop="accessKey">
          <el-input v-model="form.accessKey" placeholder="Access Key ID" />
        </el-form-item>
        <el-form-item label="密钥 Secret" prop="secretKey">
          <el-input v-model="form.secretKey" type="password" show-password placeholder="Secret Access Key" />
        </el-form-item>
        <el-form-item label="桶名" prop="bucketName">
          <el-input v-model="form.bucketName" placeholder="如 yutong" />
        </el-form-item>
        <el-form-item label="自定义域名">
          <el-input v-model="form.domain" placeholder="CDN 域名 (可选)" />
        </el-form-item>
        <el-form-item label="区域">
          <el-input v-model="form.region" placeholder="如 us-east-1 (可选)" />
        </el-form-item>
        <el-form-item label="HTTPS">
          <el-switch v-model="form.isHttps" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
          <div class="form-tip">默认配置用于文件上传等未指定 configKey 的场景</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
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
  align-items: center;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.config-key {
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
}
.form-tip {
  font-size: 12px;
  color: var(--yt-text-secondary, #909399);
  line-height: 1.5;
}
</style>
