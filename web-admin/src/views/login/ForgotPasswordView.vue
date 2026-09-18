<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Message, Lock } from '@element-plus/icons-vue'
import service from '@/api/request'

const router = useRouter()
const step = ref(1) // 1: 输入邮箱 2: 输入验证码+新密码
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: '',
})

const emailRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
  ],
}

const resetRules: FormRules = {
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleSendCode() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await service.post('/auth/forgot-password', { email: form.email })
      ElMessage.success('验证码已发送到您的邮箱')
      step.value = 2
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '发送失败'
      ElMessage.error(msg)
    } finally {
      loading.value = false
    }
  })
}

async function handleReset() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await service.post('/auth/reset-password', {
        email: form.email,
        code: form.code,
        newPassword: form.newPassword,
      })
      ElMessage.success('密码重置成功，请登录')
      router.push('/login')
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '重置失败'
      ElMessage.error(msg)
    } finally {
      loading.value = false
    }
  })
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="forgot-page">
    <div class="forgot-card">
      <h2 class="title">找回密码</h2>
      <p class="subtitle">{{ step === 1 ? '输入注册邮箱获取验证码' : '输入验证码并设置新密码' }}</p>

      <!-- Step 1: 输入邮箱 -->
      <el-form v-if="step === 1" ref="formRef" :model="form" :rules="emailRules" label-position="top" size="large">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" :prefix-icon="Message" placeholder="注册时填写的邮箱" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleSendCode">
          发送验证码
        </el-button>
      </el-form>

      <!-- Step 2: 验证码 + 新密码 -->
      <el-form v-else ref="formRef" :model="form" :rules="resetRules" label-position="top" size="large">
        <el-form-item label="验证码" prop="code">
          <el-input v-model="form.code" placeholder="邮箱收到的验证码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" :prefix-icon="Lock" show-password placeholder="6-64 字符" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" :prefix-icon="Lock" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleReset">
          重置密码
        </el-button>
      </el-form>

      <div class="footer-links">
        <span class="link" @click="goLogin">返回登录</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.forgot-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--yt-bg-page, #f6f8fb);
}
.forgot-card {
  width: 400px;
  background: var(--yt-bg-card, #fff);
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}
.title {
  margin: 0 0 4px;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
  color: var(--yt-text-primary, #111827);
}
.subtitle {
  margin: 0 0 24px;
  font-size: 14px;
  text-align: center;
  color: var(--yt-text-secondary, #6b7280);
}
.submit-btn {
  width: 100%;
  margin-top: 8px;
}
.footer-links {
  margin-top: 16px;
  text-align: center;
}
.link {
  font-size: 14px;
  color: var(--yt-color-primary, #2563eb);
  cursor: pointer;
}
.link:hover {
  text-decoration: underline;
}
</style>
