<template>
  <view class="message-page">
    <view class="message-actions">
      <!-- P1-4: 弱网读缓存时顶部标注"缓存"角标 -->
      <text v-if="usingCache" class="cache-badge" role="status" aria-label="当前展示为离线缓存数据">缓存</text>
      <text class="message-action" role="button" aria-label="全部标记为已读" @click="markAllRead">全部已读</text>
    </view>
    <scroll-view scroll-y class="message-list" @scrolltolower="loadMore" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <!-- WCAG 1.4.1 Use of Color: 未读状态不仅有色条，还有 aria-label 文字标识 -->
      <view class="message-item" v-for="msg in list" :key="msg.id" :class="{ 'message-item--unread': msg.readStatus === 'UNREAD' }" role="button" :aria-label="`${msg.readStatus === 'UNREAD' ? '未读消息' : '已读消息'}: ${msg.title}`" @click="readMessage(msg)">
        <view class="message-item__header">
          <text class="message-item__title">{{ msg.title }}</text>
          <text class="message-item__time">{{ formatTime(msg.createdTime) }}</text>
        </view>
        <text class="message-item__content">{{ msg.content }}</text>
      </view>
      <EmptyState v-if="list.length === 0 && !loading" text="暂无消息" icon="📨" />
      <text v-if="loading" class="loading-text" role="status" aria-live="polite">加载中...</text>
      <text v-if="noMore && list.length > 0" class="loading-text" role="status">没有更多了</text>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMessages, markAsRead, markAllAsRead, type SysMessage } from '@/api/message';
import { saveListCache, loadListCache } from '@/utils/offlineCache';
import { trackPageView } from '@/utils/tracker';
import EmptyState from '@/components/EmptyState.vue';

const list = ref<SysMessage[]>([]);
const loading = ref(false);
const refreshing = ref(false);
const noMore = ref(false);
const pageNo = ref(1);
const pageSize = 20;
// P1-4: 弱网读缓存展示时置 true，顶部显示"缓存"角标
const usingCache = ref(false);

async function loadData(reset = false) {
  if (loading.value) return;
  if (reset) {
    pageNo.value = 1;
    noMore.value = false;
  }
  if (noMore.value) return;
  loading.value = true;
  try {
    const result = await getMessages({ pageNo: pageNo.value, pageSize });
    const items = result.records || [];
    if (reset) list.value = items;
    else list.value = [...list.value, ...items];
    if (items.length < pageSize) noMore.value = true;
    else pageNo.value++;
    // P1-4: 加载成功写入列表缓存（仅首屏快照）
    usingCache.value = false;
    if (reset) saveListCache('message-list', list.value);
  } catch (e) {
    console.error('Failed to load messages:', e);
    // P1-4: 首屏失败时尝试读缓存展示，并顶部标注"缓存"角标
    if (reset && list.value.length === 0) {
      const cached = loadListCache<SysMessage[]>('message-list');
      if (cached) {
        list.value = cached.data;
        usingCache.value = true;
        noMore.value = true;
      }
    }
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function readMessage(msg: SysMessage) {
  if (msg.readStatus === 'UNREAD') {
    const prevStatus = msg.readStatus;
    try {
      await markAsRead(msg.id);
      msg.readStatus = 'READ';
    } catch (e) {
      // P1-4: 标记已读失败时回滚未读状态
      console.error(e);
      msg.readStatus = prevStatus;
    }
  }
  if (msg.targetRouteId) {
    uni.navigateTo({ url: `/pages/biz/detail?id=${msg.bizId}` });
  }
}

async function markAllRead() {
  try {
    await markAllAsRead();
    list.value.forEach((m) => (m.readStatus = 'READ'));
    uni.showToast({ title: '已全部标记为已读', icon: 'success' });
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' });
  }
}

function loadMore() {
  loadData();
}
function onRefresh() {
  refreshing.value = true;
  loadData(true);
}
function formatTime(time: string) {
  if (!time) return '';
  return time.substring(0, 16).replace('T', ' ');
}

onMounted(() => loadData(true));

onShow(() => {
  // P1-5: 页面浏览埋点
  trackPageView('mobile.message');
  // Refresh list when returning to this page.
  loadData(true);
});
</script>

<style scoped>
.message-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.message-actions {
  padding: 16rpx 24rpx;
  background: #fff;
  border-bottom: 1rpx solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16rpx;
}
/* P1-4: 缓存角标 */
.cache-badge {
  margin-right: auto;
  font-size: 22rpx;
  color: #e6a23c;
  background: #fdf6ec;
  border: 1rpx solid #f5dab1;
  border-radius: 8rpx;
  padding: 4rpx 14rpx;
}
.message-action {
  font-size: 28rpx;
  color: #3c8772;
}
.message-list {
  flex: 1;
  padding: 24rpx;
}
.message-item {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.message-item--unread {
  border-left: 6rpx solid #3c8772;
}
.message-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.message-item__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
}
.message-item__time {
  font-size: 24rpx;
  color: #c0c4cc;
}
.message-item__content {
  font-size: 26rpx;
  color: #606266;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.loading-text {
  display: block;
  text-align: center;
  padding: 24rpx;
  font-size: 26rpx;
  color: #c0c4cc;
}
</style>
