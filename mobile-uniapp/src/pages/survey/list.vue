<template>
  <view class="survey-list-page">
    <OfflineBanner />

    <view class="filter-bar">
      <input
        class="filter-input"
        type="text"
        v-model="titleFilter"
        placeholder="按标题搜索"
        confirm-type="search"
        @confirm="reload"
      />
      <button class="filter-btn" size="mini" @click="reload">搜索</button>
    </view>

    <view v-if="loading" class="loading-hint">加载中...</view>

    <YtCard v-for="survey in list" :key="survey.id" @click="goFill(survey)">
      <view class="survey-card">
        <view class="survey-card__title">{{ survey.title }}</view>
        <view class="survey-card__desc" v-if="survey.description">{{ survey.description }}</view>
        <view class="survey-card__meta">
          <text class="meta-item">编号: {{ survey.surveyNo }}</text>
          <text class="meta-item" v-if="survey.category">分类: {{ survey.category }}</text>
        </view>
        <view class="survey-card__meta">
          <text class="meta-item">已收集: {{ survey.responseCount || 0 }} 份</text>
          <text class="meta-item" v-if="survey.anonymous">匿名</text>
          <text class="meta-item" v-else>实名</text>
        </view>
      </view>
    </YtCard>

    <EmptyState v-if="!loading && list.length === 0" text="暂无收集中问卷" icon="📋" />

    <view class="load-more" v-if="list.length > 0">
      <button v-if="hasMore" size="mini" @click="loadMore" :disabled="loadingMore">
        {{ loadingMore ? '加载中...' : '加载更多' }}
      </button>
      <text v-else class="load-more__end">没有更多了</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app';
import { pageSurveys, type SurSurvey } from '@/api/survey';
import YtCard from '@/components/YtCard.vue';
import EmptyState from '@/components/EmptyState.vue';

const list = ref<SurSurvey[]>([]);
const loading = ref(false);
const loadingMore = ref(false);
const pageNo = ref(1);
const pageSize = 10;
const hasMore = ref(true);
const titleFilter = ref('');

async function loadList(reset = false) {
  if (reset) {
    pageNo.value = 1;
    hasMore.value = true;
  }
  if (pageNo.value === 1) {
    loading.value = true;
  } else {
    loadingMore.value = true;
  }
  try {
    const params: any = {
      pageNo: pageNo.value,
      pageSize,
      status: 'COLLECTING', // 移动端只看收集中问卷
    };
    if (titleFilter.value.trim()) params.title = titleFilter.value.trim();
    const result = await pageSurveys(params);
    const records = result.records || [];
    if (pageNo.value === 1) {
      list.value = records;
    } else {
      list.value = list.value.concat(records);
    }
    hasMore.value = records.length >= pageSize;
  } catch (e) {
    console.error('Failed to load survey list:', e);
    uni.showToast({ title: '加载失败', icon: 'none' });
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

function reload() {
  loadList(true);
}

function loadMore() {
  if (!hasMore.value || loadingMore.value) return;
  pageNo.value++;
  loadList(false);
}

function goFill(survey: SurSurvey) {
  uni.navigateTo({ url: `/pages/survey/fill?id=${survey.id}` });
}

onMounted(() => loadList(true));
onShow(() => loadList(true));
onPullDownRefresh(() => {
  loadList(true).finally(() => uni.stopPullDownRefresh());
});
onReachBottom(() => loadMore());
</script>

<style scoped>
.survey-list-page {
  padding: 24rpx;
}
.filter-bar {
  display: flex;
  gap: 12rpx;
  margin-bottom: 24rpx;
}
.filter-input {
  flex: 1;
  height: 64rpx;
  padding: 0 20rpx;
  background: #fff;
  border-radius: 8rpx;
  border: 1rpx solid #dcdfe6;
  font-size: 26rpx;
}
.filter-btn {
  flex-shrink: 0;
}
.loading-hint {
  text-align: center;
  color: #909399;
  padding: 32rpx;
}
.survey-card {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.survey-card__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
}
.survey-card__desc {
  font-size: 26rpx;
  color: #606266;
  line-height: 1.5;
}
.survey-card__meta {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
}
.meta-item {
  font-size: 24rpx;
  color: #909399;
}
.load-more {
  text-align: center;
  padding: 24rpx;
}
.load-more__end {
  font-size: 24rpx;
  color: #c0c4cc;
}
</style>
