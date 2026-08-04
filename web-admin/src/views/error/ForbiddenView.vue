<script setup lang="ts">
/**
 * GA2-16: 403 无权限页面。
 * 设计来源: 96-端侧权限可见性矩阵详设 (无权限、空数据、无数据范围的区分)
 *
 * 文案明确告知用户"你没有访问该页面的权限",提供返回首页和重新登录两个动作。
 * 不展示任何业务数据, 与"空数据"和"无数据范围"两类空态区分。
 */
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { MOCK_USER_LABELS } from '@/utils/auth'

const router = useRouter()
const authStore = useAuthStore()

function goHome() {
  router.push('/dashboard')
}

async function reLogin() {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="forbidden">
    <el-result
      icon="warning"
      title="403"
      :sub-title="authStore.mockUser ? $t('error.forbidden.noPermissionWithRole', { role: MOCK_USER_LABELS[authStore.mockUser] }) : $t('error.forbidden.noPermission')"
    >
      <template #extra>
        <el-button type="primary" @click="goHome">
          {{ $t('error.forbidden.backHome') }}
        </el-button>
        <el-button @click="reLogin">
          {{ $t('error.forbidden.reLogin') }}
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.forbidden {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--yutong-bg-page);
}
</style>
