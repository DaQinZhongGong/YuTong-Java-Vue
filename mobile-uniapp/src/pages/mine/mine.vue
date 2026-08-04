<template>
  <view class="mine-page">
    <OfflineBanner />

    <view class="profile-card">
      <view class="avatar">
        <text class="avatar-text">{{ avatarText }}</text>
      </view>
      <view class="profile-info">
        <view class="profile-name">{{ user?.username || '未登录' }}</view>
        <view class="profile-meta">
          <text>ID: {{ user?.id ?? '-' }}</text>
          <text v-if="user?.tenantId"> | 租户: {{ user.tenantId }}</text>
        </view>
        <!-- P1-6: 角色摘要 -->
        <view v-if="roleSummary" class="profile-roles">角色: {{ roleSummary }}</view>
        <!-- P1-6: 版本号 -->
        <view class="profile-version">版本: v{{ appVersion }}</view>
      </view>
    </view>

    <view class="menu-group" role="menu" aria-label="个人设置">
      <view class="menu-item" role="menuitem" aria-label="个人资料" @click="onMenu('profile')">
        <text class="menu-label">个人资料</text>
        <text class="menu-arrow" aria-hidden="true">›</text>
      </view>
      <view class="menu-item" role="menuitem" aria-label="设置" @click="onMenu('settings')">
        <text class="menu-label">设置</text>
        <text class="menu-arrow" aria-hidden="true">›</text>
      </view>
      <view class="menu-item" role="menuitem" aria-label="关于" @click="onMenu('about')">
        <text class="menu-label">关于</text>
        <text class="menu-arrow" aria-hidden="true">›</text>
      </view>
      <view class="menu-item" role="menuitem" aria-label="清理缓存" @click="clearCache">
        <text class="menu-label">清理缓存</text>
        <text class="menu-arrow" aria-hidden="true">›</text>
      </view>
      <view class="menu-item" role="menuitem" aria-label="上传队列" @click="goUploadQueue">
        <text class="menu-label">上传队列</text>
        <text class="menu-arrow" aria-hidden="true">›</text>
      </view>
    </view>

    <view class="menu-group" role="menu" aria-label="账号操作">
      <view class="menu-item logout" role="menuitem" aria-label="退出登录" @click="handleLogout">
        <text class="menu-label danger">退出登录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { getUser, setUser, clearAuth } from '@/store/auth';
import { logout as logoutApi, getMe } from '@/api/auth';
import { clearUserCaches } from '@/utils/offlineCache';
import { clearAllDraftsForUser } from '@/utils/draft';
import { clearUploadQueueForUser, getQueue, removeTask } from '@/utils/uploadQueue';
import { trackPageView } from '@/utils/tracker';
import type { UserInfo } from '@/api/types';
import OfflineBanner from '@/components/OfflineBanner.vue';

const user = ref<UserInfo | null>(getUser());

const avatarText = computed<string>(() => {
  const name = user.value?.username || '?';
  return name.charAt(0).toUpperCase();
});

// P1-6: 角色摘要。后端 /auth/me 返回 roles 为字符串数组（如 ["ADMIN"]），
// 兼容字符串或 { name } 对象两种形态拼接展示。
const roleSummary = computed<string>(() => {
  const roles = user.value?.roles;
  if (!roles || roles.length === 0) return '';
  return (roles as unknown as Array<string | { name?: string }>)
    .map((r) => (typeof r === 'string' ? r : r?.name))
    .filter(Boolean)
    .join('、');
});

// P1-6: 版本号，构建期注入；缺省 1.0.0
const appVersion = (import.meta.env as unknown as Record<string, string | undefined>).VITE_APP_VERSION || '1.0.0';

// P1-6: onShow 拉取最新用户信息并刷新 store
async function refreshUser(): Promise<void> {
  try {
    const me = await getMe();
    // 合并：保留登录态已有的 id/username，以 /auth/me 的 roles/permissions 为准
    const merged: UserInfo = { ...(getUser() || {}), ...me };
    setUser(merged);
    user.value = merged;
  } catch (e) {
    console.error('Failed to load user info:', e);
  }
}

function onMenu(_key: string): void {
  uni.showToast({ title: '功能开发中', icon: 'none' });
}

function goUploadQueue(): void {
  uni.navigateTo({ url: '/pages/mine/uploadQueue' });
}

// P1-6: 清理缓存只清页面缓存和已完成上传，保留失败上传项（除非二次确认）
function clearCache(): void {
  uni.showModal({
    title: '提示',
    content: '将清理页面缓存和已完成的上传任务，失败的上传将保留。确认清理？',
    success: (res) => {
      if (!res.confirm) return;
      try {
        // 1. 清理页面缓存（列表离线缓存 cache:list:*），保留鉴权键与草稿
        clearUserCaches();
        // 2. 清理已完成的上传任务
        const queue = getQueue();
        const completed = queue.filter((t) => t.status === 'SUCCESS');
        completed.forEach((t) => removeTask(t.localId));
        // 3. 失败上传项：若存在，二次确认是否一并清理
        const failed = queue.filter((t) => t.status === 'FAILED' || t.status === 'BIND_FAILED');
        if (failed.length > 0) {
          uni.showModal({
            title: '发现失败上传',
            content: `检测到 ${failed.length} 个失败的上传任务，是否一并清理？`,
            confirmText: '清理',
            cancelText: '保留',
            success: (r2) => {
              if (r2.confirm) {
                failed.forEach((t) => removeTask(t.localId));
              }
              uni.showToast({ title: '缓存已清理', icon: 'success' });
            },
          });
        } else {
          uni.showToast({ title: '缓存已清理', icon: 'success' });
        }
      } catch (e) {
        console.error('Clear cache failed:', e);
        uni.showToast({ title: '清理失败', icon: 'none' });
      }
    },
  });
}

function handleLogout(): void {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: async (res) => {
      if (!res.confirm) return;
      try {
        await logoutApi();
      } catch {
        // Ignore network errors during logout; clear local state regardless.
      }
      // 退出登录清理敏感离线缓存（列表缓存 / 草稿 / 上传队列）
      clearUserCaches();
      clearAllDraftsForUser();
      clearUploadQueueForUser();
      clearAuth();
      uni.reLaunch({ url: '/pages/login/login' });
    },
  });
}

onShow(() => {
  // P1-5: 页面浏览埋点
  trackPageView('mobile.mine');
  // P1-6: 拉取最新用户信息
  refreshUser();
});
</script>

<style lang="scss" scoped>
.mine-page {
  min-height: 100vh;
  background-color: #f5f7fa;
  padding: 24rpx;
}

.profile-card {
  display: flex;
  align-items: center;
  padding: 32rpx;
  background-color: #ffffff;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
}

.avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background-color: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
}

.avatar-text {
  color: #ffffff;
  font-size: 44rpx;
  font-weight: 600;
}

.profile-info {
  flex: 1;
}

.profile-name {
  font-size: 36rpx;
  font-weight: 600;
  color: #303133;
}

.profile-meta {
  margin-top: 8rpx;
  font-size: 26rpx;
  color: #909399;
}

/* P1-6: 角色摘要与版本号 */
.profile-roles {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #606266;
}

.profile-version {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #c0c4cc;
}

.menu-group {
  background-color: #ffffff;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-label {
  font-size: 30rpx;
  color: #303133;
}

.menu-label.danger {
  color: #f56c6c;
}

.menu-arrow {
  font-size: 36rpx;
  color: #c0c4cc;
}

.logout {
  justify-content: center;
}
</style>
