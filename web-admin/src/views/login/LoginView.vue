<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import type { LoginRequest } from '@/api/types'
import { MOCK_USER_LABELS, type MockUserType } from '@/utils/auth'
import { track } from '@/utils/tracker'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loginFormRef = ref<FormInstance>()
const loading = ref(false)

/**
 * GA2-16: dev 模式 Mock 用户类型选择。
 * 设计来源: 96-端侧权限可见性矩阵详设 (Mock 角色与账号章节)
 *
 * 生产构建不显示选择器, 后端 MockAuthGuardConfig 拒绝 dev 外 profile 启用 Mock。
 */
const isDev = import.meta.env.DEV
const mockUserType = ref<MockUserType>('admin')
const mockUserOptions: { value: MockUserType; label: string; desc: string }[] = [
  { value: 'admin', label: MOCK_USER_LABELS.admin, desc: t('login.desc.admin') },
  { value: 'biz', label: MOCK_USER_LABELS.biz, desc: t('login.desc.biz') },
  { value: 'approver', label: MOCK_USER_LABELS.approver, desc: t('login.desc.approver') },
  { value: 'viewer', label: MOCK_USER_LABELS.viewer, desc: t('login.desc.viewer') },
]

const loginForm = reactive<LoginRequest>({
  username: 'admin',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: t('login.rule.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.rule.passwordRequired'), trigger: 'blur' }],
}

async function handleLogin() {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    // GA2-18: 登录点击埋点 (设计来源 94 号文档业务事件字典 web.auth.login)
    const startTime = Date.now()
    track('web.auth.login.click', { payload: { mockUser: isDev ? mockUserType.value : undefined } })
    try {
      // GA2-16: dev 模式传 mockUserType, 生产模式不传 (后端使用真实 AuthAdapter)
      await authStore.login(loginForm, isDev ? mockUserType.value : undefined)
      ElMessage.success(t('login.msg.success'))
      // GA2-18: 登录成功埋点
      track('web.auth.login.success', { durationMs: Date.now() - startTime })
      const redirect = (route.query.redirect as string) || '/dashboard'
      router.push(redirect)
    } catch (err) {
      // GA2-18: 登录失败埋点 (不采集密码等敏感信息)
      track('web.auth.login.failed', {
        result: 'failed',
        errorCode: (err as Error)?.message || 'AUTH_LOGIN_FAILED',
        durationMs: Date.now() - startTime,
      })
      // 错误已由请求拦截器统一提示
    } finally {
      loading.value = false
    }
  })
}
</script>

<template>
  <div class="login-container">
    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <h1 class="login-title">{{ $t('login.title.app') }}</h1>
        <p class="login-subtitle">{{ $t('login.subtitle.welcome') }}</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <!-- WCAG 3.3.2 Labels or Instructions + 1.3.1 Info and Relationships: 表单字段必须有可访问的 label -->
        <el-form-item prop="username">
          <label for="login-username" class="sr-only">{{ $t('login.field.username') }}</label>
          <el-input
            id="login-username"
            v-model="loginForm.username"
            :placeholder="$t('login.placeholder.username')"
            :prefix-icon="User"
            clearable
            :aria-label="$t('login.aria.username')"
          />
        </el-form-item>
        <el-form-item prop="password">
          <label for="login-password" class="sr-only">{{ $t('login.field.password') }}</label>
          <el-input
            id="login-password"
            v-model="loginForm.password"
            type="password"
            :placeholder="$t('login.placeholder.password')"
            :prefix-icon="Lock"
            show-password
            :aria-label="$t('login.aria.password')"
          />
        </el-form-item>
        <!-- GA2-16: dev 模式 Mock 用户类型选择器 (96 号文档权限矩阵验证入口) -->
        <el-form-item v-if="isDev">
          <label for="login-mock-user" class="sr-only">{{ $t('login.field.mockUserType') }}</label>
          <el-select
            id="login-mock-user"
            v-model="mockUserType"
            :placeholder="$t('login.placeholder.mockUserType')"
            style="width: 100%"
            :aria-label="$t('login.aria.mockUserType')"
          >
            <el-option
              v-for="opt in mockUserOptions"
              :key="opt.value"
              :label="`${opt.label} — ${opt.desc}`"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            {{ $t('login.action.submit') }}
          </el-button>
        </el-form-item>
        <div class="login-footer-links">
          <router-link to="/register" class="footer-link">注册账号</router-link>
          <router-link to="/forgot-password" class="footer-link">忘记密码？</router-link>
        </div>
      </el-form>
      <div v-if="isDev" class="mock-tip">
        {{ $t('login.tip.devMode') }}
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 50%, #409eff 100%);
}
.login-card {
  width: 420px;
  max-width: 90vw;
  border-radius: 8px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
}
.login-header {
  text-align: center;
  margin-bottom: 24px;
}
.login-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
  color: var(--yutong-primary);
}
.login-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--yutong-text-secondary);
}
.login-btn {
  width: 100%;
}
.login-footer-links {
  display: flex;
  justify-content: space-between;
  margin-top: -8px;
  margin-bottom: 8px;
}
.footer-link {
  font-size: 13px;
  color: var(--yt-color-primary, #2563eb);
  text-decoration: none;
}
.footer-link:hover {
  text-decoration: underline;
}
.mock-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--yutong-text-secondary);
  line-height: 1.5;
  text-align: center;
}
</style>
