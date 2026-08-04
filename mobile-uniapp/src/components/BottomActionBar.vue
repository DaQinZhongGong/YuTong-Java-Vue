<template>
  <view class="bottom-action-bar">
    <slot />
  </view>
</template>
<script setup lang="ts"></script>
<style scoped>
.bottom-action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: #fff;
  box-shadow: 0 -2rpx 8rpx rgba(0, 0, 0, 0.06);
  z-index: 100;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
}
</style>
