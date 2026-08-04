<template>
  <view class="report-list-page">
    <OfflineBanner />
    <view class="page-header">
      <text class="page-title">报表分析</text>
    </view>
    <scroll-view scroll-y class="list-container" @scrolltolower="loadMore" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <view v-for="item in list" :key="item.id" class="report-card" @click="goDetail(item.reportCode)">
        <text class="report-name">{{ item.reportName }}</text>
        <view class="card-meta">
          <text class="meta-text">类型: {{ item.reportType }}</text>
          <text class="meta-text">状态: {{ item.status }}</text>
        </view>
      </view>
      <EmptyState v-if="list.length === 0 && !loading" text="暂无报表" />
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

interface ReportItem {
  id: string;
  reportCode: string;
  reportName: string;
  reportType: string;
  status: string;
}

interface PageResult<T> {
  records: T[];
  total: number;
}

const list = ref<ReportItem[]>([]);
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
    const res = await get<PageResult<ReportItem>>(`/report/reports?page=${pageNo.value}&size=${pageSize}`);
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

function goDetail(_code: string) {
  uni.showToast({ title: '报表详情开发中', icon: 'none' });
}

onMounted(() => loadData(true));

// P0-6: 进入报表分析页校验 report:view 权限，无权限提示并返回
onLoad(() => {
  if (!checkPermission('report:view')) {
    uni.showToast({ title: '无报表查看权限', icon: 'none' });
    setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/workbench/workbench' }) }), 800);
  }
});
</script>

<style scoped>
.report-list-page {
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
.report-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.report-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12rpx;
  display: block;
}
.card-meta {
  display: flex;
  gap: 16rpx;
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
