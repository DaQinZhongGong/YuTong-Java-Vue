<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Download, Delete, Search } from '@element-plus/icons-vue'
import { track } from '@/utils/tracker'
import {
  getFiles,
  uploadFile,
  downloadFile,
  deleteFile,
} from '@/api/file'
import type { FileInfo, PageRequest } from '@/api/types'

// GA2-25: 文件管理。字段对齐后端 SysFile domain (fileName/fileSize/contentType/storageType/uploadStatus/createdTime)。
// 支持: 上传 + 列表 + 文件名筛选 + 下载(预签名URL) + 删除 + 权限码。
// 设计来源: 10-Vue3管理端设计、12-中间件集成设计、17-平台基础能力详细设计。

const { t } = useI18n()
const loading = ref(false)
const uploading = ref(false)
const tableData = ref<FileInfo[]>([])
const total = ref(0)

const query = reactive<PageRequest & { fileName?: string }>({
  page: 1,
  size: 10,
  fileName: '',
})

async function loadData() {
  loading.value = true
  try {
    const res = await getFiles(query)
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
  loadData()
}

function handleReset() {
  query.fileName = ''
  query.page = 1
  loadData()
}

async function handleUpload(file: File) {
  uploading.value = true
  try {
    await uploadFile(file)
    ElMessage.success(t('file.msg.uploadSuccess'))
    track('web.file.upload.success', { payload: { name: file.name, size: file.size } })
    loadData()
  } finally {
    uploading.value = false
  }
  // 阻止 el-upload 默认上传行为
  return false
}

async function handleDownload(row: FileInfo) {
  try {
    const blob = await downloadFile(row.id)
    track('web.file.download.click', { bizId: row.id })
    // 后端 /files/{id}/download 返回文件流，前端生成临时 URL 触发浏览器下载
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.fileName || 'download'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch {
    // 错误已由拦截器统一提示
  }
}

async function handleDelete(row: FileInfo) {
  try {
    await ElMessageBox.confirm(
      t('file.msg.confirmDelete', { name: row.fileName }),
      t('file.msg.tip'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteFile(row.id)
  ElMessage.success(t('file.msg.deleteSuccess'))
  track('web.file.delete.success', { bizId: row.id })
  loadData()
}

function formatSize(size: number): string {
  if (!size && size !== 0) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  if (size < 1024 * 1024 * 1024) return (size / 1024 / 1024).toFixed(1) + ' MB'
  return (size / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function uploadStatusTag(status: string): 'success' | 'danger' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

function uploadStatusLabel(status: string): string {
  if (status === 'SUCCESS') return t('file.status.success')
  if (status === 'FAILED') return t('file.status.failed')
  return status || '-'
}

function handlePageChange(page: number) {
  query.page = page
  loadData()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div>
    <h2 class="page-title">{{ $t('system.file.page.list') }}</h2>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="query.fileName"
          :placeholder="$t('file.list.fileNamePlaceholder')"
          clearable
          style="width: 240px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        <!-- GA2-25: 上传按钮按 system:file:upload 权限码隐藏 (96 号文档文件管理权限) -->
        <el-upload
          v-permission="'system:file:upload'"
          :show-file-list="false"
          :before-upload="handleUpload"
          :disabled="uploading"
          style="display: inline-block"
        >
          <el-button type="success" :icon="Upload" :loading="uploading">{{ $t('file.list.uploadFile') }}</el-button>
        </el-upload>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe :empty-text="$t('file.list.emptyData')">
        <el-table-column prop="fileName" :label="$t('file.list.fileName')" min-width="220" show-overflow-tooltip />
        <el-table-column prop="contentType" :label="$t('file.list.type')" width="160" />
        <el-table-column prop="fileExt" :label="$t('file.list.ext')" width="90" align="center" />
        <el-table-column prop="fileSize" :label="$t('file.list.size')" width="110" align="right">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column prop="storageType" :label="$t('file.list.storageType')" width="100" align="center" />
        <el-table-column prop="uploadStatus" :label="$t('file.list.uploadStatus')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="uploadStatusTag(row.uploadStatus)" size="small">
              {{ uploadStatusLabel(row.uploadStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('file.list.uploadTime')" width="180" />
        <el-table-column :label="$t('file.list.action')" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="Download"
              size="small"
              :disabled="row.uploadStatus !== 'SUCCESS'"
              @click="handleDownload(row as FileInfo)"
            >{{ $t('common.action.download') }}</el-button>
            <el-button
              v-permission="'system:file:delete'"
              type="danger"
              link
              :icon="Delete"
              size="small"
              @click="handleDelete(row as FileInfo)"
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
</style>
