<template>
  <view class="plugin-market-page">
    <OfflineBanner />
    <view class="page-header">
      <text class="page-title">插件市场</text>
    </view>
    <scroll-view scroll-y class="list-container" @scrolltolower="loadMore" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <view v-for="item in list" :key="item.id" class="plugin-card">
        <view class="card-header">
          <text class="plugin-name">{{ item.pluginName }}</text>
          <text class="plugin-version">v{{ item.pluginVersion }}</text>
        </view>
        <text class="plugin-desc">{{ item.description || '暂无描述' }}</text>
        <view class="card-actions">
          <text v-if="item.installed" class="status-text installed">已安装</text>
          <text v-else class="status-text not-installed">未安装</text>
        </view>
      </view>
      <EmptyState v-if="list.length === 0 && !loading" text="暂无插件" />
      <text v-if="loading" class="loading-text">加载中...</text>
      <text v-if="noMore && list.length > 0" class="loading-text">没有更多了</text>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { get } from '@/utils/request';
import { checkPermission } from '@/utils/permission';
import EmptyState from '@/components/EmptyState.vue';
import OfflineBanner from '@/components/OfflineBanner.vue';

interface MarketPluginVO {
  id: string;
  pluginCode: string;
  pluginName: string;
  pluginVersion: string;
  description?: string;
  installed: boolean;
}

const list = ref<MarketPluginVO[]>([]);
const loading = ref(false);
const refreshing = ref(false);
const noMore = ref(false);

async function loadData(reset = false) {
  if (loading.value) return;
  if (noMore.value && !reset) return;
  loading.value = true;
  try {
    // P1-6: 后端 GET /plugins/market 返回完整列表（非分页），
    // 对齐为一次性加载，不再传 pageNo/pageSize（后端会忽略且不返回 PageResult）
    const res = await get<MarketPluginVO[]>(`/plugins/market`);
    const items = Array.isArray(res) ? res : [];
    list.value = items;
    noMore.value = true;
  } catch (e) {
    uni.showToast({ title: '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

function loadMore() {
  loadData();
}

function onRefresh() {
  refreshing.value = true;
  loadData(true);
}

onMounted(() => loadData(true));

// P0-6: 进入插件市场页校验 plugin:view 权限，无权限提示并返回
onLoad(() => {
  if (!checkPermission('plugin:view')) {
    uni.showToast({ title: '无插件市场权限', icon: 'none' });
    setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/workbench/workbench' }) }), 800);
  }
});
</script>

<style scoped>
.plugin-market-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}
.page-header {
  padding: 24rpx;
  background: #fff;
}
.page-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #303133;
}
.list-container {
  flex: 1;
  padding: 24rpx;
}
.plugin-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.plugin-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
}
.plugin-version {
  font-size: 24rpx;
  color: #909399;
}
.plugin-desc {
  font-size: 26rpx;
  color: #606266;
  margin-bottom: 12rpx;
  display: block;
}
.card-actions {
  display: flex;
  justify-content: flex-end;
}
.status-text {
  font-size: 24rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.installed {
  color: #3c8772;
  background: #e6f7f2;
}
.not-installed {
  color: #909399;
  background: #f5f7fa;
}
.loading-text {
  display: block;
  text-align: center;
  padding: 24rpx;
  font-size: 26rpx;
  color: #c0c4cc;
}
</style>
