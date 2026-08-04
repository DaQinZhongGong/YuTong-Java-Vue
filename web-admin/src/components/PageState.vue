<script setup lang="ts">
/**
 * PageState 空/错/加载/无权限状态展示
 * 设计来源: 66-前端组件API与状态管理详设 line 58-67 + 98-105 + base.css
 *
 * 使用 .yt-empty-state / .yt-error-state / .yt-no-permission CSS 类。
 * error/no-permission 展示 traceId/permissionCode, retry 按钮触发 retry 事件。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading, FolderOpened, CircleCloseFilled, Lock } from '@element-plus/icons-vue'

interface Props {
  type: 'loading' | 'empty' | 'error' | 'no-permission'
  traceId?: string
  message?: string
  permissionCode?: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'retry'): void
}>()

const { t } = useI18n()

const defaultMessage = computed(() => {
  switch (props.type) {
    case 'loading':
      return t('component.pageState.loading')
    case 'empty':
      return t('component.pageState.empty')
    case 'error':
      return t('component.pageState.error')
    case 'no-permission':
      return t('component.pageState.noPermission')
    default:
      return ''
  }
})

const message_ = computed(() => props.message || defaultMessage.value)

function handleRetry() {
  emit('retry')
}
</script>

<template>
  <!-- type=loading -->
  <div v-if="type === 'loading'" class="yt-empty-state">
    <el-icon class="yt-empty-state__icon" :size="48"><Loading /></el-icon>
    <div class="yt-empty-state__text">{{ message_ }}</div>
  </div>

  <!-- type=empty -->
  <div v-else-if="type === 'empty'" class="yt-empty-state">
    <el-icon class="yt-empty-state__icon" :size="48"><FolderOpened /></el-icon>
    <div class="yt-empty-state__text">{{ message_ }}</div>
  </div>

  <!-- type=error -->
  <div v-else-if="type === 'error'" class="yt-error-state">
    <el-icon class="yt-error-state__icon" :size="48"><CircleCloseFilled /></el-icon>
    <div class="yt-error-state__message">{{ message_ }}</div>
    <div v-if="traceId" class="yt-error-state__trace">{{ $t('component.pageState.label.traceId', { id: traceId }) }}</div>
    <div class="yt-error-state__retry">
      <el-button type="primary" @click="handleRetry">{{ $t('common.action.retry') }}</el-button>
    </div>
  </div>

  <!-- type=no-permission -->
  <div v-else-if="type === 'no-permission'" class="yt-no-permission">
    <el-icon class="yt-no-permission__icon" :size="48"><Lock /></el-icon>
    <div class="yt-empty-state__text">{{ message_ }}</div>
    <div v-if="permissionCode" class="yt-no-permission__code">{{ $t('component.pageState.label.permissionCode', { code: permissionCode }) }}</div>
  </div>
</template>
