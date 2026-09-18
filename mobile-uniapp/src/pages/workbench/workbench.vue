<template>
  <view class="workbench">
    <OfflineBanner />

    <view class="workbench__header">
      <text class="workbench__greeting">{{ greeting }}，{{ userName }}</text>
      <text class="workbench__date">{{ today }}</text>
    </view>

    <view class="workbench__stats" role="region" aria-label="工作台统计">
      <view class="stat-card" role="button" aria-label="待办任务 {{ stats.todoCount }} 件，点击查看" @click="goTodo">
        <text class="stat-card__value">{{ stats.todoCount }}</text>
        <text class="stat-card__label">待办任务</text>
      </view>
      <view class="stat-card" role="button" aria-label="未读消息 {{ stats.messageCount }} 条，点击查看" @click="goMessage">
        <text class="stat-card__value">{{ stats.messageCount }}</text>
        <text class="stat-card__label">未读消息</text>
      </view>
      <view class="stat-card" aria-label="待审核 {{ stats.pendingRequestCount }} 件">
        <text class="stat-card__value">{{ stats.pendingRequestCount }}</text>
        <text class="stat-card__label">待审核</text>
      </view>
    </view>

    <view class="workbench__shortcuts" role="region" aria-label="快捷入口">
      <view v-if="checkPermission('mobile:todo:list')" class="shortcut" role="button" aria-label="待办" @click="goTodo">
        <view class="shortcut__icon-wrap">
          <text class="shortcut__icon" aria-hidden="true">📋</text>
          <text v-if="stats.todoCount > 0" class="shortcut__badge">{{ stats.todoCount > 99 ? '99+' : stats.todoCount }}</text>
        </view>
        <text class="shortcut__text">待办</text>
      </view>
      <view v-if="checkPermission('mobile:message:list')" class="shortcut" role="button" aria-label="消息" @click="goMessage"><text class="shortcut__icon" aria-hidden="true">📨</text><text class="shortcut__text">消息</text></view>
      <view v-if="checkPermission('mobile:scan:use')" class="shortcut" role="button" aria-label="扫码" @click="goScan"><text class="shortcut__icon" aria-hidden="true">📷</text><text class="shortcut__text">扫码</text></view>
      <view class="shortcut" role="button" aria-label="问卷" @click="goSurvey"><text class="shortcut__icon" aria-hidden="true">📝</text><text class="shortcut__text">问卷</text></view>
      <view v-if="checkPermission('biz:contract:list')" class="shortcut" role="button" aria-label="合同" @click="goContracts"><text class="shortcut__icon" aria-hidden="true">📄</text><text class="shortcut__text">合同</text></view>
      <view v-if="checkPermission('report:view')" class="shortcut" role="button" aria-label="报表" @click="goReports"><text class="shortcut__icon" aria-hidden="true">📊</text><text class="shortcut__text">报表</text></view>
      <view v-if="checkPermission('plugin:view')" class="shortcut" role="button" aria-label="插件市场" @click="goPlugins"><text class="shortcut__icon" aria-hidden="true">🔌</text><text class="shortcut__text">插件市场</text></view>
      <view class="shortcut" role="button" aria-label="创作大师" @click="goCreator"><text class="shortcut__icon" aria-hidden="true">✍️</text><text class="shortcut__text">创作大师</text></view>
      <view class="shortcut" role="button" aria-label="专业助理" @click="goAssistant"><text class="shortcut__icon" aria-hidden="true">🤖</text><text class="shortcut__text">专业助理</text></view>
    </view>

    <view class="workbench__section">
      <text class="section-title">最近待办</text>
      <TodoCard v-for="todo in recentTodos" :key="todo.id" :todo="todo" @click="goDetail(todo.bizId)" />
      <EmptyState v-if="recentTodos.length === 0" text="暂无待办" icon="✅" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getWorkbench, getWorkbenchStats, getMobileTodos, type MobileTodoVO, type WorkbenchStats } from '@/api/mobile';
import { saveListCache, loadListCache } from '@/utils/offlineCache';
import { checkPermission } from '@/utils/permission';
import { trackPageView } from '@/utils/tracker';
import TodoCard from '@/components/TodoCard.vue';
import EmptyState from '@/components/EmptyState.vue';
import OfflineBanner from '@/components/OfflineBanner.vue';

const stats = ref<WorkbenchStats>({ todoCount: 0, messageCount: 0, pendingRequestCount: 0 });
const recentTodos = ref<MobileTodoVO[]>([]);
const userName = ref('用户');

const greeting = (() => {
  const h = new Date().getHours();
  if (h < 6) return '凌晨好';
  if (h < 12) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
})();

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
});

async function loadStats() {
  try {
    stats.value = await getWorkbenchStats();
    // 弱网/离线策略: 成功后写入列表缓存
    saveListCache('workbench-stats', stats.value);
  } catch (e) {
    console.error('Failed to load workbench stats:', e);
    // 弱网/离线策略: 请求失败读缓存展示最近一次成功数据
    const cached = loadListCache<WorkbenchStats>('workbench-stats');
    if (cached) {
      stats.value = cached.data;
      uni.showToast({ title: '网络异常，已展示离线缓存', icon: 'none' });
    }
  }
}

async function loadRecentTodos() {
  try {
    const result = await getMobileTodos({ pageNo: 1, pageSize: 3 });
    recentTodos.value = result.records || [];
    // 弱网/离线策略: 成功后写入列表缓存
    saveListCache('workbench-recent', recentTodos.value);
  } catch (e) {
    console.error('Failed to load recent todos:', e);
    // 弱网/离线策略: 请求失败读缓存展示最近一次成功数据
    const cached = loadListCache<MobileTodoVO[]>('workbench-recent');
    if (cached) {
      recentTodos.value = cached.data;
    }
  }
}

/**
 * P1-1: 工作台首屏走聚合接口 GET /mobile/workbench，一次性获取 stats + recentTodos。
 * 失败时降级为原 Promise.all([loadStats(), loadRecentTodos()]) 并发逻辑。
 * 当聚合接口返回的 recentTodos 为空（后端未实现该字段）时，补充拉取最近待办。
 */
async function loadWorkbench() {
  try {
    const wb = await getWorkbench();
    stats.value = {
      todoCount: 0,
      messageCount: 0,
      pendingRequestCount: 0,
      ...(wb.stats as Partial<WorkbenchStats>),
    };
    recentTodos.value = wb.recentTodos || [];
    saveListCache('workbench-stats', stats.value);
    saveListCache('workbench-recent', recentTodos.value);
    // 聚合接口未返回 recentTodos 时，补充拉取（避免首屏待办区空白）
    if (recentTodos.value.length === 0) {
      await loadRecentTodos();
    }
  } catch (e) {
    console.error('Workbench aggregation failed, fallback to parallel load:', e);
    // 降级为原并发逻辑
    await Promise.all([loadStats(), loadRecentTodos()]);
  }
}

onMounted(async () => {
  try {
    const userStr = uni.getStorageSync('yutong_user');
    if (userStr) {
      const user = typeof userStr === 'string' ? JSON.parse(userStr) : userStr;
      userName.value = user?.username || user?.name || '用户';
    }
  } catch {
    // ignore storage parse errors
  }

  await loadWorkbench();
});

onShow(() => {
  // P1-5: 页面浏览埋点
  trackPageView('mobile.workbench');
  // P1-1: 刷新数据走聚合接口
  loadWorkbench();
});

function goTodo() {
  uni.switchTab({ url: '/pages/todo/todo' });
}
function goMessage() {
  uni.navigateTo({ url: '/pages/message/list' });
}
function goScan() {
  uni.navigateTo({ url: '/pages/scan/index' });
}
function goSurvey() {
  uni.navigateTo({ url: '/pages/survey/list' });
}
function goContracts() {
  uni.navigateTo({ url: '/pages/contracts/list' });
}
function goReports() {
  uni.navigateTo({ url: '/pages/reports/list' });
}
function goPlugins() {
  uni.navigateTo({ url: '/pages/plugins/index' });
}
function goCreator() {
  // 创作大师: 跳转聊天页并预填创作提示词
  const prompt = encodeURIComponent('你是一位专业的文案创作大师，擅长撰写各类文案、故事、诗歌、营销文案等。请告诉我你想创作什么类型的内容，我会帮你完成。')
  uni.navigateTo({ url: `/pages/ai/chat?appName=${encodeURIComponent('创作大师')}&systemPrompt=${prompt}` });
}
function goAssistant() {
  // 专业助理: 跳转聊天页并预填助理提示词
  const prompt = encodeURIComponent('你是一位全能的专业助理，擅长回答各类问题、提供建议、整理信息、翻译、编程辅助等。请告诉我你需要什么帮助。')
  uni.navigateTo({ url: `/pages/ai/chat?appName=${encodeURIComponent('专业助理')}&systemPrompt=${prompt}` });
}
function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/biz/detail?id=${id}` });
}
</script>

<style scoped>
.workbench {
  padding: 24rpx;
}
.workbench__header {
  margin-bottom: 32rpx;
}
.workbench__greeting {
  display: block;
  font-size: 36rpx;
  font-weight: 600;
  color: #303133;
}
.workbench__date {
  display: block;
  font-size: 26rpx;
  color: #909399;
  margin-top: 8rpx;
}
.workbench__stats {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
}
.stat-card {
  flex: 1;
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx 16rpx;
  text-align: center;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.stat-card__value {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #3c8772;
}
.stat-card__label {
  display: block;
  font-size: 24rpx;
  color: #909399;
  margin-top: 8rpx;
}
.workbench__shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 32rpx;
}
.shortcut {
  flex: 1;
  min-width: 120rpx;
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx 16rpx;
  text-align: center;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.shortcut__icon-wrap {
  position: relative;
  display: inline-block;
}
.shortcut__icon {
  display: block;
  font-size: 48rpx;
}
.shortcut__badge {
  position: absolute;
  top: -8rpx;
  right: -16rpx;
  background: #f56c6c;
  color: #fff;
  font-size: 20rpx;
  padding: 2rpx 10rpx;
  border-radius: 20rpx;
  line-height: 1;
}
.shortcut__text {
  display: block;
  font-size: 24rpx;
  color: #606266;
  margin-top: 8rpx;
}
.workbench__section {
  margin-top: 16rpx;
}
.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16rpx;
  display: block;
}
</style>
