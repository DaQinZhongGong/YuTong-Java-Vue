<script setup lang="ts">
/**
 * FileUploader 文件上传组件
 * 设计来源: 66-前端组件API与状态管理详设 line 81-89
 *
 * 5 状态机: idle / selected / uploading / success / failed
 *   idle       未选择文件      上传按钮                  选择文件
 *   selected   已选择未上传    文件名、大小              删除、开始上传
 *   uploading  上传中          进度条、禁用删除          取消预留
 *   success    上传成功        成功图标、fileId          预览、删除
 *   failed     上传失败        错误原因                  重试、删除
 */
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Document, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import type { UploadProgressEvent, UploadRawFile } from 'element-plus'

type UploadStatus = 'idle' | 'selected' | 'uploading' | 'success' | 'failed'

interface Props {
  /** 允许的文件类型, 透传 el-upload accept 属性 */
  accept?: string
  /** 最大文件大小(字节), 默认 10MB */
  maxSize?: number
  /** 上传 URL */
  action: string
  /** 上传请求头 (如 Authorization) */
  headers?: Record<string, string>
}

const props = withDefaults(defineProps<Props>(), {
  accept: '',
  maxSize: 10 * 1024 * 1024,
  headers: () => ({}),
})

const { t } = useI18n()

const emit = defineEmits<{
  (e: 'success', payload: { fileId: string; fileName: string; fileSize: number }): void
  (e: 'error', payload: { message: string }): void
  (e: 'remove'): void
}>()

const status = ref<UploadStatus>('idle')
const fileName = ref('')
const fileSize = ref(0)
const progress = ref(0)
const fileId = ref('')
const errorMessage = ref('')

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

function handleBeforeUpload(file: UploadRawFile): boolean {
  if (file.size > props.maxSize) {
    ElMessage.error(t('component.fileUploader.msg.sizeExceeded', { max: (props.maxSize / 1024 / 1024).toFixed(0) }))
    return false
  }
  fileName.value = file.name
  fileSize.value = file.size
  status.value = 'selected'
  return true
}

function handleProgress(evt: UploadProgressEvent) {
  status.value = 'uploading'
  progress.value = Math.floor(evt.percent || 0)
}

function handleSuccess(response: any) {
  status.value = 'success'
  progress.value = 100
  const data = response?.data ?? response
  fileId.value = data?.id || data?.fileId || ''
  emit('success', {
    fileId: fileId.value,
    fileName: fileName.value,
    fileSize: fileSize.value,
  })
}

function handleError(err: Error) {
  status.value = 'failed'
  errorMessage.value = err?.message || t('component.fileUploader.msg.uploadFailed')
  emit('error', { message: errorMessage.value })
}

function handleRemove() {
  status.value = 'idle'
  fileName.value = ''
  fileSize.value = 0
  progress.value = 0
  fileId.value = ''
  errorMessage.value = ''
  emit('remove')
}

function handleRetry() {
  // 重置为 idle, 由用户重新选择文件触发上传
  status.value = 'idle'
  errorMessage.value = ''
  fileName.value = ''
  fileSize.value = 0
  progress.value = 0
  fileId.value = ''
}

const currentStatus = computed(() => status.value)
</script>

<template>
  <div class="yt-file-uploader" :data-status="currentStatus">
    <!-- 状态: idle (上传按钮) -->
    <el-upload
      v-if="status === 'idle'"
      :action="action"
      :accept="accept"
      :headers="headers"
      :show-file-list="false"
      :before-upload="handleBeforeUpload"
      :on-progress="handleProgress"
      :on-success="handleSuccess"
      :on-error="handleError"
    >
      <el-button type="primary">{{ $t('component.fileUploader.button.selectFile') }}</el-button>
      <template #tip>
        <div v-if="accept" class="yt-file-uploader__tip">
          {{ $t('component.fileUploader.tip.allowedTypes', { accept, max: (maxSize / 1024 / 1024).toFixed(0) }) }}
        </div>
      </template>
    </el-upload>

    <!-- 状态: selected (文件名 + 大小 + 删除 + 开始上传) -->
    <div v-else-if="status === 'selected'" class="yt-file-uploader__info">
      <el-icon><Document /></el-icon>
      <span class="yt-file-uploader__name">{{ fileName }}</span>
      <span class="yt-file-uploader__size">{{ formatSize(fileSize) }}</span>
      <el-button size="small" type="danger" @click="handleRemove">{{ $t('common.action.delete') }}</el-button>
      <el-upload
        :action="action"
        :accept="accept"
        :headers="headers"
        :show-file-list="false"
        :before-upload="handleBeforeUpload"
        :on-progress="handleProgress"
        :on-success="handleSuccess"
        :on-error="handleError"
      >
        <el-button size="small" type="primary">{{ $t('component.fileUploader.button.startUpload') }}</el-button>
      </el-upload>
    </div>

    <!-- 状态: uploading (进度条 + 禁用删除) -->
    <div v-else-if="status === 'uploading'" class="yt-file-uploader__info">
      <span class="yt-file-uploader__name">{{ fileName }}</span>
      <el-progress
        :percentage="progress"
        :stroke-width="8"
        style="flex: 1; margin: 0 12px"
      />
      <span>{{ progress }}%</span>
    </div>

    <!-- 状态: success (成功图标 + fileId + 预览 + 删除) -->
    <div v-else-if="status === 'success'" class="yt-file-uploader__info">
      <el-icon color="var(--yt-color-success)"><CircleCheckFilled /></el-icon>
      <span class="yt-file-uploader__name">{{ fileName }}</span>
      <span class="yt-file-uploader__fileid">{{ $t('component.fileUploader.label.fileId', { id: fileId }) }}</span>
      <el-button size="small" @click="handleRemove">{{ $t('common.action.delete') }}</el-button>
    </div>

    <!-- 状态: failed (错误原因 + 重试 + 删除) -->
    <div v-else-if="status === 'failed'" class="yt-file-uploader__info">
      <el-icon color="var(--yt-color-danger)"><CircleCloseFilled /></el-icon>
      <span class="yt-file-uploader__name">{{ fileName }}</span>
      <span class="yt-file-uploader__error">{{ errorMessage }}</span>
      <el-button size="small" type="primary" @click="handleRetry">{{ $t('common.action.retry') }}</el-button>
      <el-button size="small" type="danger" @click="handleRemove">{{ $t('common.action.delete') }}</el-button>
    </div>
  </div>
</template>

<style scoped>
.yt-file-uploader__info {
  display: flex;
  align-items: center;
  gap: var(--yt-space-sm);
  padding: var(--yt-space-sm) var(--yt-space-md);
  border: 1px solid var(--yt-border-default);
  border-radius: var(--yt-radius-md);
  background: var(--yt-bg-card);
}
.yt-file-uploader__name {
  font-size: var(--yt-font-size-body);
  color: var(--yt-text-primary);
}
.yt-file-uploader__size,
.yt-file-uploader__fileid,
.yt-file-uploader__error {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
}
.yt-file-uploader__error {
  color: var(--yt-color-danger);
}
.yt-file-uploader__tip {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
  margin-top: 4px;
}
</style>
