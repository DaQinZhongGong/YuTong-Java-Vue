<template>
  <view class="contract-list-page">
    <OfflineBanner />
    <view class="page-header">
      <text class="page-title">合同档案</text>
    </view>
    <scroll-view scroll-y class="list-container" @scrolltolower="loadMore" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <view v-for="item in list" :key="item.id" class="contract-card" @click="goDetail(item.id)">
        <view class="card-header">
          <text class="contract-no">{{ item.contractNo }}</text>
          <text class="contract-status">{{ item.status }}</text>
        </view>
        <text class="contract-title">{{ item.title }}</text>
        <view class="card-meta">
          <text class="meta-text">甲方: {{ item.partyA }}</text>
          <text class="meta-text">乙方: {{ item.partyB }}</text>
        </view>
      </view>
      <EmptyState v-if="list.length === 0 && !loading" text="暂无合同" />
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

interface ContractItem {
  id: string;
  contractNo: string;
  title: string;
  partyA: string;
  partyB: string;
  status: string;
}

interface PageResult<T> {
  records: T[];
  total: number;
}

const list = ref<ContractItem[]>([]);
const loading = ref(false);
const refreshing = ref(false);
const noMore = ref(false);
const pageNo = ref(1);
const pageSize = 20;

async function loadData(reset = false) {
  if (loading.value) return;
  if (reset) {
    pageNo.value = 1;
    noMore.value = false;
  }
  if (noMore.value) return;
  loading.value = true;
  try {
    const res = await get<PageResult<ContractItem>>(`/contracts?page=${pageNo.value}&size=${pageSize}`);
    const items = res.records || [];
    if (reset) list.value = items;
    else list.value = [...list.value, ...items];
    if (items.length < pageSize) noMore.value = true;
    else pageNo.value++;
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

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/biz/detail?id=${id}` });
}

onMounted(() => loadData(true));

// P0-6: 进入合同档案页校验 biz:contract:list 权限，无权限提示并返回
onLoad(() => {
  if (!checkPermission('biz:contract:list')) {
    uni.showToast({ title: '无合同档案权限', icon: 'none' });
    setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/workbench/workbench' }) }), 800);
  }
});
</script>

<style scoped>
.contract-list-page {
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
.contract-card {
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
.contract-no {
  font-size: 24rpx;
  color: #909399;
}
.contract-status {
  font-size: 22rpx;
  color: #3c8772;
  background: #e6f7f2;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.contract-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12rpx;
  display: block;
}
.card-meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.meta-text {
  font-size: 24rpx;
  color: #606266;
}
.loading-text {
  display: block;
  text-align: center;
  padding: 24rpx;
  font-size: 26rpx;
  color: #c0c4cc;
}
</style>
