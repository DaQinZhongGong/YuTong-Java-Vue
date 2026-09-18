<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'
import service from '@/api/request'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 32, message: '用户名长度 3-32 字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  email: [
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
  ],
}

async function handleRegister() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      await service.post('/auth/register', {
        username: form.username,
        password: form.password,
        email: form.email || undefined,
      })
      ElMessage.success('注册成功，请登录')
      router.push('/login')
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '注册失败'
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
  <div class="register-page">
    <div class="register-card">
      <h2 class="title">注册账号</h2>
      <p class="subtitle">创建您的 YuTong 账号</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :prefix-icon="User" placeholder="3-32 字符" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" :prefix-icon="Lock" show-password placeholder="6-64 字符" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" :prefix-icon="Lock" show-password placeholder="再次输入密码" />
        </el-form-item>
        <el-form-item label="邮箱（可选）" prop="email">
          <el-input v-model="form.email" :prefix-icon="Message" placeholder="用于找回密码" />
        </el-form-item>

        <el-button type="primary" class="submit-btn" :loading="loading" @click="handleRegister">
          注册
        </el-button>
      </el-form>

      <div class="footer-links">
        <span class="link" @click="goLogin">已有账号？去登录</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--yt-bg-page, #f6f8fb);
}
.register-card {
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
