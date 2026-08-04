<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <el-result
      icon="warning"
      title="404"
      :sub-title="$t('error.notFound.message')"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/dashboard')">
          {{ $t('error.notFound.backHome') }}
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.not-found {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--yutong-bg-page);
}
</style>
