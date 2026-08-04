<template>
  <view class="login-page">
    <view class="login-card">
      <view class="login-title">YuTong</view>
      <view class="login-subtitle">欢迎登录</view>

      <!-- WCAG 3.3.2 Labels or Instructions + 4.1.2 Name, Role, Value: 表单字段必须有可访问 label -->
      <view class="form-item">
        <input
          v-model="form.username"
          class="form-input"
          placeholder="请输入用户名"
          placeholder-class="placeholder"
          maxlength="32"
          aria-label="用户名"
        />
      </view>

      <view class="form-item form-item--password">
        <input
          v-model="form.password"
          class="form-input form-input--password"
          :password="!showPassword"
          placeholder="请输入密码"
          placeholder-class="placeholder"
          maxlength="64"
          aria-label="密码"
        />
        <!-- 密码显示/隐藏切换（眼睛图标） -->
        <text
          class="pwd-toggle"
          role="button"
          :aria-label="showPassword ? '隐藏密码' : '显示密码'"
          @click="showPassword = !showPassword"
        >{{ showPassword ? '隐藏' : '显示' }}</text>
      </view>

      <button
        class="login-btn"
        aria-label="登录"
        :disabled="loading"
        :loading="loading"
        @click="handleLogin"
      >
        {{ loading ? '登录中...' : '登录' }}
      </button>

      <!-- 仅开发环境显示：一键填充后端 mock 可用账号 admin_demo/demo123 (GA2-L178 Demo 账号) -->
      <button v-if="isDev" class="dev-btn" @click="useDevAccount">
        使用开发账号
      </button>
      <!-- 多模式登录时展示当前认证模式（单模式隐藏切换 UI；仅 DEV 可见） -->
      <view v-if="isDev && !isSingleAuthMode" class="auth-mode-hint">
        认证模式：{{ authMode }}
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue';
import { login as loginApi } from '@/api/auth';
import { saveLogin } from '@/store/auth';
import { track } from '@/utils/tracker';
import { REDIRECT_STORAGE_KEY, type RequestError } from '@/utils/request';
import type { LoginRequest } from '@/api/types';

// P0-2: 不再预填弱口令，避免源码硬编码凭据泄露风险
const form = reactive<LoginRequest>({
  username: '',
  password: '',
});

// 开发环境标识（仅 DEV 构建为 true，生产构建会被压缩为 false）
// 直接访问 import.meta.env.DEV 以便 Vite 构建期静态替换
const isDev = (import.meta.env as unknown as { DEV?: boolean }).DEV ?? false;

// 密码显示/隐藏切换（眼睛图标）
const showPassword = ref(false);

// 检测后端登录模式：读 env VITE_AUTH_MODE，缺省 LOCAL_IAM。
// 多模式（逗号分隔，如 "LOCAL_IAM,OAUTH"）时此处可扩展模式切换 UI；
// 单模式时隐藏切换 UI（当前仅单模式，无切换 UI）。
const authMode =
  (import.meta.env as unknown as Record<string, string | undefined>).VITE_AUTH_MODE ||
  'LOCAL_IAM';
const isSingleAuthMode = computed(() => !authMode.includes(','));

// DEV 模式快捷填充后端 mock 实际可用账号 (GA2-L178: admin_demo/demo123, 密码由 YUTONG_DEMO_PASSWORD 初始化)
function useDevAccount(): void {
  form.username = 'admin_demo';
  form.password = 'demo123';
}

const loading = ref(false);

/**
 * 按后端错误码分类提示登录失败原因。
 * - 401 账号/密码错误；403 账号禁用；429 频繁尝试；其他回退后端 message 或通用文案。
 */
function mapLoginError(err: RequestError | undefined): string {
  const code = err?.statusCode;
  if (code === 401) return '账号或密码错误';
  if (code === 403) return '账号已被禁用，请联系管理员';
  if (code === 429) return '登录尝试过于频繁，请稍后再试';
  return err?.message || '登录失败，请重试';
}

async function handleLogin(): Promise<void> {
  if (!form.username || !form.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' });
    return;
  }

  loading.value = true;
  // GA2-18: 登录点击埋点 (设计来源 94 号文档业务事件字典 mobile.auth.login)
  // 不采集 password 等敏感字段 (脱敏管道兜底)
  const startTime = Date.now();
  track('mobile.auth.login.click', { payload: { username: form.username } });
  try {
    const resp = await loginApi(form);
    saveLogin(resp);
    // GA2-18: 登录成功埋点
    track('mobile.auth.login.success', { durationMs: Date.now() - startTime });
    uni.showToast({ title: '登录成功', icon: 'success' });
    setTimeout(() => {
      // Token 失效回跳：若存在中断前的目标路由则回跳，否则进入工作台
      const redirect = uni.getStorageSync(REDIRECT_STORAGE_KEY);
      if (redirect) {
        uni.removeStorageSync(REDIRECT_STORAGE_KEY);
        uni.reLaunch({ url: redirect });
      } else {
        uni.switchTab({ url: '/pages/workbench/workbench' });
      }
    }, 500);
  } catch (e) {
    // GA2-18: 登录失败埋点 (不采集密码等敏感信息)
    track('mobile.auth.login.failed', {
      result: 'failed',
      errorCode: e instanceof Error ? e.message : 'AUTH_LOGIN_FAILED',
      durationMs: Date.now() - startTime,
    });
    uni.showToast({ title: mapLoginError(e as RequestError | undefined), icon: 'none' });
  } finally {
    loading.value = false;
  }
}
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 0 48rpx;
  background-color: #f5f7fa;
}

.login-card {
  width: 100%;
  padding: 64rpx 48rpx;
  background-color: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.06);
}

.login-title {
  font-size: 56rpx;
  font-weight: 700;
  color: #303133;
  text-align: center;
}

.login-subtitle {
  margin-top: 12rpx;
  margin-bottom: 48rpx;
  font-size: 28rpx;
  color: #909399;
  text-align: center;
}

.form-item {
  margin-bottom: 32rpx;
}

.form-input {
  width: 100%;
  height: 88rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #303133;
  background-color: #f5f7fa;
  border-radius: 12rpx;
  box-sizing: border-box;
}

.placeholder {
  color: #c0c4cc;
}

.form-item--password {
  position: relative;
}

.form-input--password {
  padding-right: 96rpx;
}

.pwd-toggle {
  position: absolute;
  top: 50%;
  right: 16rpx;
  transform: translateY(-50%);
  padding: 8rpx 12rpx;
  font-size: 26rpx;
  color: #909399;
  line-height: 1;
}

.auth-mode-hint {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #c0c4cc;
  text-align: center;
}

.login-btn {
  width: 100%;
  height: 88rpx;
  margin-top: 16rpx;
  font-size: 32rpx;
  color: #ffffff;
  background-color: #409eff;
  border-radius: 12rpx;
  border: none;
  line-height: 88rpx;
}

.login-btn[disabled] {
  background-color: #a0cfff;
  color: #ffffff;
}

.dev-btn {
  width: 100%;
  height: 72rpx;
  margin-top: 24rpx;
  font-size: 26rpx;
  color: #909399;
  background-color: #f5f7fa;
  border-radius: 12rpx;
  border: 1rpx dashed #c0c4cc;
  line-height: 72rpx;
}
</style>
