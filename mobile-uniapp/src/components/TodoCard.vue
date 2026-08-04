<template>
  <view class="todo-card" role="button" :aria-label="`待办: ${todo.title}, 单号 ${todo.requestNo}, 金额 ${formatAmount(todo.totalAmount)} 元`" @click="$emit('click')">
    <view class="todo-card__header">
      <text class="todo-card__title">{{ todo.title }}</text>
      <StatusBadge :label="todo.statusLabel" :status="todo.statusLabel" />
    </view>
    <view class="todo-card__body">
      <text class="todo-card__label">单号：{{ todo.requestNo }}</text>
      <text class="todo-card__label">客户：{{ todo.customerName || '-' }}</text>
      <text class="todo-card__label">金额：¥{{ formatAmount(todo.totalAmount) }}</text>
    </view>
    <view class="todo-card__footer">
      <text class="todo-card__time">{{ todo.submittedTime || '未提交' }}</text>
    </view>
  </view>
</template>
<script setup lang="ts">
import type { MobileTodoVO } from '@/api/mobile';
import StatusBadge from './StatusBadge.vue';
defineProps<{ todo: MobileTodoVO }>();
defineEmits<{ click: [] }>();
function formatAmount(amount: number) {
  if (amount == null) return '0.00';
  return Number(amount).toFixed(2);
}
</script>
<style scoped>
.todo-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.todo-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}
.todo-card__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
  flex: 1;
  margin-right: 16rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.todo-card__body {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.todo-card__label {
  font-size: 26rpx;
  color: #606266;
}
.todo-card__footer {
  margin-top: 16rpx;
  padding-top: 16rpx;
  border-top: 1rpx solid #f0f0f0;
}
.todo-card__time {
  font-size: 24rpx;
  color: #c0c4cc;
}
</style>
