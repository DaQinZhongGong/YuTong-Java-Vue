<template>
  <view class="upload-page">
    <OfflineBanner />

    <view class="upload-toolbar">
      <button class="tool-btn tool-btn--pick" @click="pickFile">选择文件加入队列</button>
      <button class="tool-btn tool-btn--retry" :disabled="noRetryable" @click="retryAll">重试全部失败项</button>
      <button class="tool-btn tool-btn--clear" :disabled="noCompleted" @click="clearCompleted">清空已完成</button>
    </view>

    <view v-if="tasks.length === 0" class="empty">
      <text class="empty__icon" aria-hidden="true">📤</text>
      <text class="empty__text">队列为空，点击「选择文件」体验弱网重试</text>
    </view>

    <!-- P1-4: 列表按状态分组展示，可折叠 -->
    <view v-for="g in groups" :key="g.key" class="task-group">
      <view class="task-group__head" @click="toggleGroup(g.key)">
        <text class="task-group__title">{{ g.title }}（{{ g.count }}）</text>
        <text class="task-group__toggle">{{ collapsed[g.key] ? '展开' : '折叠' }}</text>
      </view>
      <view v-if="!collapsed[g.key]">
        <view v-for="t in g.tasks" :key="t.localId" class="task-card" role="listitem">
          <view class="task-card__head">
            <text class="task-card__name">{{ t.fileName }}</text>
            <text class="task-status" :class="statusClass(t.status)">{{ statusLabel(t.status) }}</text>
          </view>
          <view class="task-card__meta">
            <text>{{ formatSize(t.fileSize) }}</text>
            <text v-if="t.bizType"> · 业务: {{ t.bizType }}</text>
            <text v-if="t.retryCount > 0"> · 重试 {{ t.retryCount }}/{{ maxRetry }}</text>
          </view>
          <view v-if="t.errorMessage" class="task-card__error">{{ t.errorMessage }}</view>

          <view class="task-card__actions">
            <button
              v-if="t.status === 'BIND_FAILED'"
              class="act act--rebind"
              @click="onRetry(t.localId)"
            >重新绑定</button>
            <button
              v-else-if="t.status === 'FAILED' || t.status === 'PENDING'"
              class="act act--retry"
              @click="onRetry(t.localId)"
            >重试</button>
            <button
              v-if="t.status === 'UPLOADING'"
              class="act act--uploading"
              disabled
            >上传中…</button>
            <button class="act act--delete" @click="onDelete(t.localId)">删除</button>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  getQueue,
  enqueue,
  removeTask,
  retryTask,
  retryAll as retryAllTasks,
  onQueueChange,
  MAX_RETRY,
  type UploadTask,
  type UploadStatus,
} from '@/utils/uploadQueue';
import OfflineBanner from '@/components/OfflineBanner.vue';

interface TaskGroup {
  key: string;
  title: string;
  count: number;
  tasks: UploadTask[];
}

const tasks = ref<UploadTask[]>(getQueue());
const maxRetry = MAX_RETRY;
/** P1-4: 从详情页等带参跳转时，入队自动绑定业务维度。 */
const bizType = ref('');
const bizId = ref('');
/** 分组折叠状态。 */
const collapsed = ref<Record<string, boolean>>({});

let unsub: (() => void) | null = null;

onMounted(() => {
  unsub = onQueueChange(() => {
    tasks.value = getQueue();
  });
});

onBeforeUnmount(() => {
  if (unsub) unsub();
});

onLoad((options) => {
  bizType.value = options?.bizType || '';
  bizId.value = options?.bizId || '';
});

/** P1-4: 按状态分组 —— 上传中(UPLOADING+PENDING) / 失败(FAILED+BIND_FAILED) / 已完成(SUCCESS)。 */
const groups = computed<TaskGroup[]>(() => {
  const uploading = tasks.value.filter(
    (t) => t.status === 'UPLOADING' || t.status === 'PENDING',
  );
  const failed = tasks.value.filter(
    (t) => t.status === 'FAILED' || t.status === 'BIND_FAILED',
  );
  const done = tasks.value.filter((t) => t.status === 'SUCCESS');
  return [
    { key: 'uploading', title: '上传中', count: uploading.length, tasks: uploading },
    { key: 'failed', title: '失败', count: failed.length, tasks: failed },
    { key: 'done', title: '已完成', count: done.length, tasks: done },
  ].filter((g) => g.count > 0);
});

const noRetryable = computed(() =>
  !tasks.value.some(
    (t) => t.status === 'PENDING' || t.status === 'FAILED' || t.status === 'BIND_FAILED',
  ),
);

const noCompleted = computed(() => !tasks.value.some((t) => t.status === 'SUCCESS'));

function toggleGroup(key: string): void {
  collapsed.value[key] = !collapsed.value[key];
}

function statusLabel(status: UploadStatus): string {
  switch (status) {
    case 'PENDING':
      return '等待上传';
    case 'UPLOADING':
      return '上传中';
    case 'SUCCESS':
      return '上传成功';
    case 'FAILED':
      return '上传失败';
    case 'BIND_FAILED':
      return '绑定失败';
    default:
      return status;
  }
}

function statusClass(status: UploadStatus): string {
  return `task-status--${status.toLowerCase()}`;
}

function formatSize(size: number): string {
  if (!size && size !== 0) return '-';
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function pickFile(): void {
  uni.chooseMedia({
    count: 1,
    mediaType: ['image'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const file = res.tempFiles[0];
      if (!file) return;
      const path = file.tempFilePath;
      const fileName = (file as any).name || path.split('/').pop() || `file_${Date.now()}`;
      // P1-4: 支持从详情页带参入队，绑定 bizType/bizId
      enqueue({
        filePath: path,
        fileName,
        fileSize: file.size || 0,
        bizType: bizType.value || undefined,
        bizId: bizId.value || undefined,
      });
      // 加入队列后尝试立即上传（离线时会被 retryAll 内部拦截，待网络恢复自动重试）
      void retryAllTasks().then(() => {
        uni.showToast({ title: '已加入上传队列', icon: 'none' });
      });
    },
    fail: (err) => {
      console.error('chooseMedia failed', err);
    },
  });
}

function onRetry(localId: string): void {
  void retryTask(localId);
}

function onDelete(localId: string): void {
  removeTask(localId);
}

/** P1-4: 仅清空已完成的任务。 */
function clearCompleted(): void {
  const done = tasks.value.filter((t) => t.status === 'SUCCESS');
  if (done.length === 0) return;
  uni.showModal({
    title: '提示',
    content: `确定清空 ${done.length} 条已完成任务吗？`,
    success: (res) => {
      if (res.confirm) {
        done.forEach((t) => removeTask(t.localId));
      }
    },
  });
}

function retryAll(): void {
  void retryAllTasks();
}
</script>

<style scoped>
.upload-page {
  min-height: 100vh;
  background-color: #f5f7fa;
  padding: 24rpx;
  padding-top: 88rpx;
}
.upload-toolbar {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}
.tool-btn {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 26rpx;
  border-radius: 8rpx;
  border: none;
  background: #fff;
  color: #303133;
}
.tool-btn--pick {
  background: #3c8772;
  color: #fff;
}
.tool-btn--retry {
  background: #f5f7fa;
  color: #3c8772;
}
.tool-btn--clear {
  background: #f5f7fa;
  color: #f56c6c;
}
.tool-btn[disabled] {
  opacity: 0.5;
}
.empty {
  text-align: center;
  padding: 120rpx 0;
  color: #909399;
}
.empty__icon {
  display: block;
  font-size: 80rpx;
  margin-bottom: 16rpx;
}
.empty__text {
  font-size: 26rpx;
}
.task-group {
  margin-bottom: 16rpx;
}
.task-group__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 8rpx;
}
.task-group__title {
  font-size: 28rpx;
  font-weight: 600;
  color: #303133;
}
.task-group__toggle {
  font-size: 24rpx;
  color: #3c8772;
  padding: 4rpx 12rpx;
}
.task-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.task-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task-card__name {
  font-size: 28rpx;
  font-weight: 600;
  color: #303133;
  flex: 1;
  margin-right: 16rpx;
  word-break: break-all;
}
.task-card__meta {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #909399;
}
.task-card__error {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #f56c6c;
}
.task-card__actions {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}
.act {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 26rpx;
  border-radius: 8rpx;
  border: none;
}
.act--retry {
  background: #3c8772;
  color: #fff;
}
.act--rebind {
  background: #e6a23c;
  color: #fff;
}
.act--uploading {
  background: #f5f7fa;
  color: #909399;
}
.act--delete {
  background: #f5f7fa;
  color: #f56c6c;
}
.task-status {
  font-size: 24rpx;
  flex-shrink: 0;
}
.task-status--pending {
  color: #909399;
}
.task-status--uploading {
  color: #409eff;
}
.task-status--success {
  color: #3c8772;
}
.task-status--failed {
  color: #f56c6c;
}
.task-status--bind_failed {
  color: #e6a23c;
}
</style>
