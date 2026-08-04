<template>
  <view
    v-if="visible"
    class="offline-banner"
    :class="offline ? 'offline-banner--offline' : 'offline-banner--weak'"
    role="alert"
    aria-live="assertive"
  >
    <text class="offline-banner__icon" aria-hidden="true">{{ offline ? '⚠️' : '📶' }}</text>
    <text class="offline-banner__text">{{ bannerText }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { isOffline, isWeakNetwork, networkType } from '@/store/network';

const offline = computed(() => isOffline.value);
const visible = computed(() => isOffline.value || isWeakNetwork.value);

const bannerText = computed(() => {
  if (isOffline.value) {
    return '网络已断开，部分功能暂不可用，已为你保留最近一次成功数据';
  }
  return `当前为弱网环境（${networkType.value}），操作可能较慢或失败`;
});
</script>

<style scoped>
.offline-banner {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 16rpx 24rpx;
  font-size: 26rpx;
  color: #fff;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.12);
}
.offline-banner--offline {
  background: #f56c6c;
}
.offline-banner--weak {
  background: #e6a23c;
}
.offline-banner__icon {
  font-size: 30rpx;
  flex-shrink: 0;
}
.offline-banner__text {
  flex: 1;
  line-height: 1.4;
}
</style>
