<template>
  <text class="status-badge" :class="statusClass">{{ label }}</text>
</template>
<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ label: string; status?: string }>();
const statusClass = computed(() => {
  const s = props.status || '';
  if (s.includes('DRAFT') || s.includes('草稿')) return 'status-badge--draft';
  if (s.includes('SUBMITTED') || s.includes('待')) return 'status-badge--pending';
  if (s.includes('APPROVED') || s.includes('通过')) return 'status-badge--success';
  if (s.includes('REJECTED') || s.includes('驳回')) return 'status-badge--error';
  return 'status-badge--default';
});
</script>
<style scoped>
.status-badge {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: 6rpx;
  font-size: 22rpx;
  line-height: 1.5;
}
.status-badge--draft {
  background: #f0f0f0;
  color: #909399;
}
.status-badge--pending {
  background: #fdf6ec;
  color: #e6a23c;
}
.status-badge--success {
  background: #f0f9eb;
  color: #67c23a;
}
.status-badge--error {
  background: #fef0f0;
  color: #f56c6c;
}
.status-badge--default {
  background: #f0f0f0;
  color: #909399;
}
</style>
