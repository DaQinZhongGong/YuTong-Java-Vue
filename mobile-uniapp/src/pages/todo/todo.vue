<template>
  <view class="todo-page">
    <OfflineBanner />

    <!-- P1-1: 关键词搜索 -->
    <view class="todo-search">
      <text class="todo-search__icon" aria-hidden="true">🔍</text>
      <input
        class="todo-search__input"
        type="text"
        v-model="keyword"
        placeholder="搜索单号/客户名称"
        aria-label="搜索待办"
        confirm-type="search"
        @confirm="onSearch"
      />
      <text v-if="keyword" class="todo-search__clear" @click="clearKeyword">×</text>
    </view>

    <!-- P1-1: 时间范围筛选 chips -->
    <view class="todo-chips" role="tablist" aria-label="时间范围筛选">
      <text
        v-for="opt in timeRangeOptions"
        :key="opt.value"
        class="todo-chip"
        :class="{ 'todo-chip--active': timeRange === opt.value }"
        role="tab"
        :aria-selected="timeRange === opt.value"
        @click="switchTimeRange(opt.value)"
      >{{ opt.label }}</text>
    </view>

    <!-- WCAG 1.3.1 Info and Relationships + 4.1.2: tabs 语义化 -->
    <view class="todo-tabs" role="tablist" aria-label="待办筛选">
      <text class="todo-tab" :class="{ 'todo-tab--active': activeTab === '' }" role="tab" :aria-selected="activeTab === ''" @click="switchTab('')">全部</text>
      <text class="todo-tab" :class="{ 'todo-tab--active': activeTab === 'PENDING' }" role="tab" :aria-selected="activeTab === 'PENDING'" @click="switchTab('PENDING')">待处理</text>
      <text class="todo-tab" :class="{ 'todo-tab--active': activeTab === 'DONE' }" role="tab" :aria-selected="activeTab === 'DONE'" @click="switchTab('DONE')">已处理</text>
    </view>

    <scroll-view scroll-y class="todo-list" @scrolltolower="loadMore" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <TodoCard v-for="todo in list" :key="todo.id" :todo="todo" @click="goDetail(todo.bizId)" />
      <!-- P1-1: 空态三态区分 —— 网络错误 / 无搜索结果 / 无数据（+ 无权限） -->
      <EmptyState v-if="loadState === 'noPermission'" icon="🔒" text="当前用户无审核权限" />
      <view v-else-if="loadState === 'error'" class="empty-block">
        <EmptyState icon="⚠️" :text="errorText" />
        <button class="empty-block__btn" @click="retryLoad">重新加载</button>
      </view>
      <view v-else-if="loadState === 'emptyFiltered'" class="empty-block">
        <EmptyState icon="🔍" :text="filteredEmptyText" />
        <button class="empty-block__btn empty-block__btn--ghost" @click="clearFilter">清空筛选条件</button>
      </view>
      <EmptyState v-else-if="loadState === 'empty'" text="当前没有待处理事项" />
      <!-- WCAG 4.1.3 Status Messages: 加载状态 aria-live -->
      <text v-if="loading" class="loading-text" role="status" aria-live="polite">加载中...</text>
      <text v-if="noMore && list.length > 0" class="loading-text" role="status">没有更多了</text>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getMobileTodos, type MobileTodoVO } from '@/api/mobile';
import { saveListCache, loadListCache } from '@/utils/offlineCache';
import { track, trackPageView } from '@/utils/tracker';
import type { RequestError } from '@/utils/request';
import TodoCard from '@/components/TodoCard.vue';
import EmptyState from '@/components/EmptyState.vue';
import OfflineBanner from '@/components/OfflineBanner.vue';

/** P1-1: 时间范围筛选维度（今日 / 本周 / 本月 / 全部）。 */
type TimeRange = 'today' | 'week' | 'month' | '';

const timeRangeOptions: { value: TimeRange; label: string }[] = [
  { value: 'today', label: '今日' },
  { value: 'week', label: '本周' },
  { value: 'month', label: '本月' },
  { value: '', label: '全部' },
];

const list = ref<MobileTodoVO[]>([]);
const activeTab = ref('');
const keyword = ref('');
const timeRange = ref<TimeRange>('');
const loading = ref(false);
const refreshing = ref(false);
const noMore = ref(false);
const pageNo = ref(1);
const pageSize = 20;
const noPermission = ref(false);
/** P1-1: 网络/服务端错误态文案，非空表示当前处于错误态。 */
const errorText = ref('');

/** 是否处于筛选态（关键词或时间范围任一生效）。 */
const hasFilter = computed(() => !!keyword.value || !!timeRange.value);

/** P1-1: 空态区分 —— 无权限 / 网络错误 / 筛选无结果 / 无数据 */
const loadState = computed<'noPermission' | 'error' | 'emptyFiltered' | 'empty' | 'hasData'>(() => {
  if (noPermission.value) return 'noPermission';
  if (list.value.length > 0) return 'hasData';
  if (errorText.value) return 'error';
  if (hasFilter.value) return 'emptyFiltered';
  return 'empty';
});

/** 筛选无结果文案：区分「搜不到关键词」与「该时间范围内无待办」。 */
const filteredEmptyText = computed(() => {
  if (keyword.value) return `没有找到与“${keyword.value}”相关的待办`;
  const label = timeRangeOptions.find((o) => o.value === timeRange.value)?.label || '';
  return `${label}没有待办`;
});

/**
 * 计算所选时间范围的起始时间戳（本地时区）。
 * today = 当天 00:00；week = 本周一 00:00；month = 本月 1 日 00:00。
 * 返回 null 表示不限制时间。
 */
function timeRangeStart(range: TimeRange): number | null {
  if (!range) return null;
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (range === 'today') return start.getTime();
  if (range === 'week') {
    // getDay(): 0=周日，按 ISO 周一为一周起点换算偏移天数
    const offset = (now.getDay() + 6) % 7;
    start.setDate(start.getDate() - offset);
    return start.getTime();
  }
  // month
  start.setDate(1);
  return start.getTime();
}

/**
 * P1-1: 后端 listMobileTodos 不支持 timeRange，
 * 前端按 submittedTime 对返回数据做时间过滤。
 */
function filterByTime(items: MobileTodoVO[]): MobileTodoVO[] {
  const threshold = timeRangeStart(timeRange.value);
  if (threshold == null) return items;
  return items.filter((it) => {
    if (!it.submittedTime) return false;
    const t = new Date(it.submittedTime).getTime();
    return !Number.isNaN(t) && t >= threshold;
  });
}

/** P1-1: 按错误类型给出可区分的网络错误空态文案。 */
function mapLoadError(err: RequestError | undefined): string {
  const status = err?.statusCode;
  if (status === 0 || !status) return '网络连接失败，请检查网络后重试';
  if (status === 408 || status === 504) return '请求超时，请稍后重试';
  if (status >= 500) return '服务暂时不可用，请稍后重试';
  return err?.message || '加载失败，请重试';
}

async function loadData(reset = false) {
  if (loading.value) return;
  if (reset) {
    pageNo.value = 1;
    noMore.value = false;
  }
  if (noMore.value) return;
  loading.value = true;
  // GA2-18: 待办列表打开埋点 (设计来源 94 号文档业务事件字典 mobile.todo.open)
  const startTime = Date.now();
  track('mobile.todo.open', {
    payload: { tab: activeTab.value || 'all', pageNo: pageNo.value, reset },
  });
  try {
    const result = await getMobileTodos({
      pageNo: pageNo.value,
      pageSize,
      todoStatus: activeTab.value || undefined,
      keyword: keyword.value || undefined,
    });
    const raw = result.records || [];
    // 前端按 timeRange 过滤（后端不支持该参数）
    const items = filterByTime(raw);
    if (reset) {
      list.value = items;
      // 弱网/离线策略: 首屏成功后写入列表缓存
      saveListCache('todo', items);
    } else {
      list.value = [...list.value, ...items];
    }
    // noMore 以原始返回数量为准，避免过滤后误判提前结束
    if (raw.length < pageSize) noMore.value = true;
    else pageNo.value++;
    noPermission.value = false;
    errorText.value = '';
    // GA2-18: 待办加载成功埋点 (不采集 title 等业务字段)
    track('mobile.todo.load.success', {
      durationMs: Date.now() - startTime,
      payload: { tab: activeTab.value || 'all', count: items.length },
    });
  } catch (e) {
    // GA2-18: 待办加载失败埋点
    track('mobile.todo.load.failed', {
      result: 'failed',
      errorCode: e instanceof Error ? e.message : 'TODO_LOAD_FAILED',
      durationMs: Date.now() - startTime,
    });
    console.error('Failed to load todos:', e);
    const err = e as RequestError;
    // P1-1: 403 视为无审核权限态
    if (err?.statusCode === 403) {
      noPermission.value = true;
      list.value = [];
      noMore.value = true;
      errorText.value = '';
    } else {
      // 弱网/离线策略: 首屏失败且本地无数据时读缓存展示
      if (reset && list.value.length === 0) {
        const cached = loadListCache<MobileTodoVO[]>('todo');
        if (cached) {
          list.value = cached.data;
          errorText.value = '';
          uni.showToast({ title: '网络异常，已展示离线缓存', icon: 'none' });
          return;
        }
      }
      // P1-1: 无缓存兜底时进入网络错误空态（区别于"无数据"与"无搜索结果"）
      errorText.value = mapLoadError(err);
      noMore.value = true;
      uni.showToast({ title: '加载失败', icon: 'none' });
    }
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

/**
 * P1-1: 时间范围为前端过滤，整页数据可能被过滤为空。
 * 首屏结果为空但后端仍有下一页时继续向后取页（最多 MAX_AUTO_PAGES 页），
 * 避免"实际有数据却展示空态"的误判。
 */
async function loadFirstScreen() {
  await loadData(true);
  let guard = 0;
  while (
    timeRange.value &&
    list.value.length === 0 &&
    !noMore.value &&
    !noPermission.value &&
    !errorText.value &&
    guard < MAX_AUTO_PAGES
  ) {
    guard++;
    await loadData();
  }
}

function onSearch() {
  loadFirstScreen();
}

function clearKeyword() {
  keyword.value = '';
  loadFirstScreen();
}

function switchTimeRange(val: TimeRange) {
  if (timeRange.value === val) return;
  timeRange.value = val;
  loadFirstScreen();
}

function clearFilter() {
  keyword.value = '';
  timeRange.value = '';
  loadFirstScreen();
}

/** P1-1: 网络错误空态重试。 */
function retryLoad() {
  errorText.value = '';
  noMore.value = false;
  loadFirstScreen();
}

function switchTab(tab: string) {
  activeTab.value = tab;
  loadFirstScreen();
}

function loadMore() {
  loadData();
}

function onRefresh() {
  refreshing.value = true;
  loadData(true);
}

function goDetail(id: string) {
  // GA2-18: 待办点击进入详情埋点
  track('mobile.todo.click', { bizType: 'request', bizId: id });
  uni.navigateTo({ url: `/pages/biz/detail?id=${id}` });
}

onMounted(() => loadData(true));

onShow(() => {
  // P1-5: 页面浏览埋点
  trackPageView('mobile.todo');
  // Refresh list when returning to this tab page.
  loadData(true);
});
</script>

<style scoped>
.todo-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.todo-search {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 16rpx 24rpx 0;
  padding: 0 24rpx;
  border-radius: 8rpx;
  border: 1rpx solid #ebeef5;
}
.todo-search__icon {
  font-size: 26rpx;
  color: #c0c4cc;
  margin-right: 12rpx;
}
.todo-search__input {
  flex: 1;
  height: 64rpx;
  font-size: 28rpx;
  color: #303133;
}
.todo-search__clear {
  font-size: 36rpx;
  color: #c0c4cc;
  padding: 0 8rpx;
  line-height: 64rpx;
}
.todo-chips {
  display: flex;
  background: #fff;
  padding: 16rpx 24rpx;
  gap: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
}
.todo-chip {
  padding: 8rpx 24rpx;
  font-size: 26rpx;
  color: #606266;
  border-radius: 32rpx;
  background: #f5f7fa;
}
.todo-chip--active {
  color: #fff;
  background: #3c8772;
}
.todo-tabs {
  display: flex;
  background: #fff;
  padding: 16rpx 24rpx;
  gap: 16rpx;
  border-bottom: 1rpx solid #f0f0f0;
}
.todo-tab {
  padding: 12rpx 32rpx;
  font-size: 28rpx;
  color: #606266;
  border-radius: 8rpx;
  background: #f5f7fa;
}
.todo-tab--active {
  color: #fff;
  background: #3c8772;
}
.todo-list {
  flex: 1;
  padding: 24rpx;
}
.loading-text {
  display: block;
  text-align: center;
  padding: 24rpx;
  font-size: 26rpx;
  color: #c0c4cc;
}
.empty-filtered {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.clear-filter-btn {
  margin-top: 8rpx;
  width: 240rpx;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 26rpx;
  border-radius: 32rpx;
  background: #3c8772;
  color: #fff;
  border: none;
}
</style>
