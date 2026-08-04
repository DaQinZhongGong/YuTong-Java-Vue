<template>
  <view class="done-page">
    <view class="done-icon">✅</view>
    <text class="done-title">答卷已提交</text>
    <text class="done-subtitle">感谢您的参与！</text>

    <view class="done-card">
      <view class="done-row">
        <text class="done-label">问卷</text>
        <text class="done-value">{{ surveyTitle || '-' }}</text>
      </view>
      <view class="done-row">
        <text class="done-label">答卷号</text>
        <text class="done-value">{{ responseNo || '-' }}</text>
      </view>
      <view class="done-row">
        <text class="done-label">提交时间</text>
        <text class="done-value">{{ submittedTime }}</text>
      </view>
    </view>

    <view class="done-actions">
      <button class="action-btn action-btn--primary" @click="goList">继续填写其他问卷</button>
      <button class="action-btn action-btn--default" @click="goWorkbench">返回工作台</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';

const responseNo = ref('');
const surveyTitle = ref('');
const submittedTime = ref('');

onLoad((options: any) => {
  responseNo.value = options?.responseNo ? decodeURIComponent(options.responseNo) : '';
  surveyTitle.value = options?.surveyTitle ? decodeURIComponent(options.surveyTitle) : '';
  submittedTime.value = new Date().toLocaleString('zh-CN');
});

function goList() {
  uni.redirectTo({ url: '/pages/survey/list' });
}

function goWorkbench() {
  uni.switchTab({ url: '/pages/workbench/workbench' });
}
</script>

<style scoped>
.done-page {
  padding: 80rpx 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.done-icon {
  font-size: 120rpx;
  margin-bottom: 24rpx;
}
.done-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8rpx;
}
.done-subtitle {
  font-size: 26rpx;
  color: #909399;
  margin-bottom: 48rpx;
}
.done-card {
  width: 100%;
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 48rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.done-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
  border-bottom: 1rpx solid #f5f7fa;
}
.done-row:last-child {
  border-bottom: none;
}
.done-label {
  font-size: 26rpx;
  color: #909399;
}
.done-value {
  font-size: 26rpx;
  color: #303133;
  font-weight: 500;
  text-align: right;
  max-width: 60%;
  word-break: break-all;
}
.done-actions {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.action-btn {
  width: 100%;
  height: 80rpx;
  border: none;
  border-radius: 8rpx;
  font-size: 28rpx;
}
.action-btn--primary {
  background: #3c8772;
  color: #fff;
}
.action-btn--default {
  background: #f5f7fa;
  color: #606266;
}
</style>
