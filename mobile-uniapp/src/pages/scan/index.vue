<template>
  <view class="scan-page">
    <view class="scan-area">
      <text class="scan-icon" aria-hidden="true">📷</text>
      <text class="scan-tip">点击下方按钮扫描二维码</text>
      <button class="scan-btn" @click="startScan">开始扫码</button>
    </view>

    <view class="scan-history" v-if="lastResult">
      <text class="section-title">最近扫码结果</text>
      <YtCard>
        <text class="scan-result">编码：{{ lastResult.code }}</text>
        <text class="scan-result">路由：{{ lastResult.routeId }}</text>
      </YtCard>
    </view>

    <!-- P1-5: 扫码历史（本地保存最近 10 条非敏感业务编码） -->
    <view class="scan-history" v-if="history.length > 0">
      <view class="history-head">
        <text class="section-title">扫码历史</text>
        <text class="history-clear" @click="clearHistory">清空</text>
      </view>
      <YtCard v-for="item in history" :key="item.code + item.time">
        <text class="scan-result">编码：{{ item.code }}</text>
        <text class="scan-result scan-result--time">{{ formatHistoryTime(item.time) }}</text>
      </YtCard>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { resolveScan } from '@/api/mobile';
import { checkPermission } from '@/utils/permission';
import { relativeTime } from '@/utils/relativeTime';
import YtCard from '@/components/YtCard.vue';

interface ScanResolveResult {
  routeId: string;
  params?: Record<string, unknown>;
  targetId?: string;
}

interface ScanHistoryItem {
  code: string;
  time: string;
}

const HISTORY_KEY = 'scan_history';
const HISTORY_MAX = 10;

const lastResult = ref<{ code: string; routeId: string } | null>(null);
const history = ref<ScanHistoryItem[]>(loadHistory());

function loadHistory(): ScanHistoryItem[] {
  try {
    const raw = uni.getStorageSync(HISTORY_KEY);
    if (!raw) return [];
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return Array.isArray(arr) ? (arr as ScanHistoryItem[]) : [];
  } catch {
    return [];
  }
}

function saveHistory(code: string): void {
  if (!code) return;
  const item: ScanHistoryItem = { code, time: new Date().toISOString() };
  // 去重：相同编码只保留最新一条
  const filtered = history.value.filter((h) => h.code !== code);
  filtered.unshift(item);
  history.value = filtered.slice(0, HISTORY_MAX);
  try {
    uni.setStorageSync(HISTORY_KEY, JSON.stringify(history.value));
  } catch (e) {
    console.error('save scan history failed', e);
  }
}

function clearHistory(): void {
  history.value = [];
  try {
    uni.removeStorageSync(HISTORY_KEY);
  } catch (e) {
    console.error('clear scan history failed', e);
  }
}

function formatHistoryTime(time: string): string {
  return relativeTime(time);
}

/**
 * P1-5: 扫码错误分类展示。
 * 按 error.code / error.statusCode / errMsg 判断，返回面向用户的提示文案。
 * 返回空串表示用户取消，无需提示。
 */
function classifyScanError(e: any): string {
  const errMsg: string = e?.errMsg || '';
  // 用户取消扫码
  if (/cancel/i.test(errMsg)) return '';
  // 相机授权拒绝
  if (/(auth.?deny|permission|authorize|deny)/i.test(errMsg)) return '请开启相机权限';

  const status = e?.statusCode;
  const code: string = e?.code || '';
  // 无权限执行
  if (status === 403) return '您无权限执行此操作';
  // 扫码对象不存在
  if (status === 404) return '扫码对象不存在';
  // 无效编码（400 或业务码含 invalid/not_found 之类）
  if (status === 400 || /invalid|无效|bad_request|not_found/i.test(code)) {
    return '无效的编码，请重新扫码';
  }
  return '扫码失败，请重试';
}

// P0-6: 进入扫码页校验 mobile:scan:use 权限，无权限提示并返回
onLoad(() => {
  if (!checkPermission('mobile:scan:use')) {
    uni.showToast({ title: '无扫码权限', icon: 'none' });
    setTimeout(() => uni.navigateBack({ fail: () => uni.switchTab({ url: '/pages/workbench/workbench' }) }), 800);
  }
});

async function startScan() {
  try {
    const res = await uni.scanCode({ scanType: ['qrCode'] });
    const code = res.result;
    const result = await resolveScan(code) as ScanResolveResult;
    const routeId = result.routeId;
    // 兼容后端返回 params.targetId 或顶层 targetId
    const targetId =
      result.targetId ||
      (result.params && (result.params.targetId as string)) ||
      '';
    lastResult.value = { code, routeId };
    // P1-5: 记录扫码历史（非敏感业务编码）
    saveHistory(code);
    uni.showToast({ title: '扫描成功', icon: 'success' });
    // 保留扫码结果展示 800ms 后再跳转，让用户看到结果
    setTimeout(() => navigateByRouteId(routeId, targetId), 800);
  } catch (e: any) {
    if (e?.errMsg?.includes('cancel')) return;
    console.error('Scan failed:', e);
    // P1-5: 错误分类提示
    const tip = classifyScanError(e);
    if (tip) uni.showToast({ title: tip, icon: 'none' });
  }
}

/**
 * 根据扫码解析出的 routeId 跳转到对应页面。
 * 路径与 pages.json 实际路径保持一致（非 routes.yaml 规范路径，避免 404）。
 */
function navigateByRouteId(routeId: string, targetId: string): void {
  switch (routeId) {
    case 'mobile.workbench':
      uni.switchTab({ url: '/pages/workbench/workbench' });
      break;
    case 'mobile.todo':
      uni.navigateTo({ url: '/pages/todo/todo' });
      break;
    case 'mobile.biz-detail':
      uni.navigateTo({ url: '/pages/biz/detail?id=' + (targetId || '') });
      break;
    case 'mobile.message':
      uni.navigateTo({ url: '/pages/message/list' });
      break;
    default:
      uni.showToast({ title: '未知扫码结果', icon: 'none' });
      break;
  }
}
</script>

<style scoped>
.scan-page {
  padding: 24rpx;
}
.scan-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 24rpx;
  background: #fff;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}
.scan-icon {
  font-size: 100rpx;
  margin-bottom: 24rpx;
}
.scan-tip {
  font-size: 28rpx;
  color: #909399;
  margin-bottom: 32rpx;
}
.scan-btn {
  width: 60%;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  background: #3c8772;
  color: #fff;
  border-radius: 8rpx;
  font-size: 30rpx;
  border: none;
}
.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16rpx;
  display: block;
}
.history-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.history-clear {
  font-size: 26rpx;
  color: #f56c6c;
  padding: 4rpx 8rpx;
}
.scan-result {
  display: block;
  font-size: 28rpx;
  color: #606266;
  padding: 4rpx 0;
}
.scan-result--time {
  font-size: 24rpx;
  color: #c0c4cc;
}
</style>
