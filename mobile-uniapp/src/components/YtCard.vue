<template>
  <view class="yt-card" :class="{ 'yt-card--flat': flat }">
    <slot />
  </view>
</template>
<script setup lang="ts">
defineProps<{ flat?: boolean }>();
</script>
<style scoped>
.yt-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.yt-card--flat {
  box-shadow: none;
  border: 1rpx solid #ebeef5;
}
</style>
