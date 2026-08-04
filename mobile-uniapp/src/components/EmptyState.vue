<template>
  <view class="empty-state" role="status" :aria-label="text">
    <text class="empty-state__icon" aria-hidden="true">{{ icon }}</text>
    <text class="empty-state__text">{{ text }}</text>
  </view>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ icon?: string; text?: string }>(), {
  icon: '📋',
  text: '暂无数据',
});
</script>
<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0;
}
.empty-state__icon {
  font-size: 80rpx;
  margin-bottom: 16rpx;
}
.empty-state__text {
  font-size: 28rpx;
  color: #909399;
}
</style>
