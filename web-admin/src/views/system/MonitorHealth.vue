<script setup lang="ts">
/**
 * GA2-L177: 服务健康页。
 * 设计来源: 91-Web基础后台逐页交互详设「服务健康页」。
 *
 * 卡片展示 App/PostgreSQL/Redis/MinIO/Flyway/RabbitMQ/Nacos/AI Provider 状态。
 * boot profile 下 RabbitMQ/Nacos 显示 SKIPPED，cloud profile 必须 UP。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, CircleCheck, CircleClose, Remove } from '@element-plus/icons-vue'
import { getHealth, type HealthOverview, type HealthComponent } from '@/api/monitor'

const { t } = useI18n()

const loading = ref(false)
const health = ref<HealthOverview | null>(null)
const lastError = ref<string | null>(null)
let autoRefreshTimer: ReturnType<typeof setInterval> | null = null

async function fetchHealth() {
  loading.value = true
  lastError.value = null
  try {
    health.value = await getHealth()
  } catch (e: any) {
    lastError.value = e?.message || t('system.monitorHealth.message.loadFailed')
    health.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchHealth()
  autoRefreshTimer = setInterval(fetchHealth, 30_000)
})

onUnmounted(() => {
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
})

const overallStatusType = computed(() => {
  const s = health.value?.status
  if (s === 'UP') return 'success'
  if (s === 'DEGRADED') return 'warning'
  return 'danger'
})

const overallStatusText = computed(() => {
  const s = health.value?.status
  if (s === 'UP') return t('system.monitorHealth.status.up')
  if (s === 'DEGRADED') return t('system.monitorHealth.status.degraded')
  if (s === 'DOWN') return t('system.monitorHealth.status.down')
  return t('system.monitorHealth.status.unknown')
})

function componentStatusType(s: HealthComponent['status']) {
  if (s === 'UP') return 'success'
  if (s === 'SKIPPED') return 'info'
  if (s === 'DEGRADED') return 'warning'
  return 'danger'
}

function componentStatusText(s: HealthComponent['status']) {
  if (s === 'UP') return t('system.monitorHealth.componentStatus.up')
  if (s === 'SKIPPED') return t('system.monitorHealth.componentStatus.skipped')
  if (s === 'DEGRADED') return t('system.monitorHealth.componentStatus.degraded')
  if (s === 'DOWN') return t('system.monitorHealth.componentStatus.down')
  return s
}

function componentIcon(s: HealthComponent['status']) {
  if (s === 'UP') return CircleCheck
  if (s === 'SKIPPED') return Remove
  return CircleClose
}
</script>

<template>
  <div class="monitor-health-page">
    <!-- 顶部状态横幅 -->
    <el-card class="banner-card" shadow="never">
      <div class="banner">
        <div class="banner-left">
          <span class="banner-label">{{ $t('system.monitorHealth.field.overallStatus') }}</span>
          <el-tag :type="overallStatusType" size="large" effect="dark">
            {{ overallStatusText }}
          </el-tag>
        </div>
        <div class="banner-meta">
          <span><strong>{{ $t('system.monitorHealth.field.platform') }}:</strong> {{ health?.platform || 'YuTong' }}</span>
          <span><strong>{{ $t('common.field.version') }}:</strong> {{ health?.version || '-' }}</span>
          <span><strong>Profile:</strong> {{ health?.profile || '-' }}</span>
          <span><strong>{{ $t('system.monitorHealth.field.runMode') }}:</strong> {{ health?.runMode || '-' }}</span>
          <span><strong>{{ $t('system.monitorHealth.field.checkedTime') }}:</strong> {{ health?.checkedAt || '-' }}</span>
        </div>
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="loading"
          @click="fetchHealth"
        >{{ $t('common.action.refresh') }}</el-button>
      </div>
    </el-card>

    <!-- 错误提示 -->
    <el-alert
      v-if="lastError"
      :title="lastError"
      type="error"
      :closable="false"
      show-icon
      class="error-alert"
    />

    <!-- 组件卡片网格 -->
    <div v-loading="loading" class="components-grid">
      <el-card
        v-for="c in health?.components || []"
        :key="c.name"
        class="component-card"
        shadow="hover"
      >
        <div class="component-header">
          <el-icon class="component-icon" :class="`status-${c.status.toLowerCase()}`">
            <component :is="componentIcon(c.status)" />
          </el-icon>
          <span class="component-name">{{ c.name }}</span>
          <el-tag :type="componentStatusType(c.status)" size="small">
            {{ componentStatusText(c.status) }}
          </el-tag>
        </div>
        <div class="component-body">
          <div class="component-row">
            <span class="row-label">{{ $t('system.monitorHealth.field.duration') }}</span>
            <span class="row-value">{{ c.durationMs }} ms</span>
          </div>
          <div class="component-row">
            <span class="row-label">{{ $t('system.monitorHealth.field.checkedTime') }}</span>
            <span class="row-value">{{ c.checkedAt }}</span>
          </div>
          <div v-if="c.detail" class="component-row">
            <span class="row-label">{{ $t('system.monitorHealth.field.detail') }}</span>
            <span class="row-value detail">{{ c.detail }}</span>
          </div>
          <div v-if="c.errorSummary" class="component-row error-row">
            <span class="row-label">{{ $t('system.monitorHealth.field.errorSummary') }}</span>
            <span class="row-value error-text">{{ c.errorSummary }}</span>
          </div>
          <div v-if="c.traceId" class="component-row">
            <span class="row-label">TraceId</span>
            <span class="row-value trace-id">{{ c.traceId }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 空状态 -->
    <el-empty
      v-if="!loading && !health && !lastError"
      :description="$t('system.monitorHealth.empty.noData')"
    />
  </div>
</template>

<style scoped>
.monitor-health-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.banner-card {
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
}
.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
.banner-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.banner-label {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.banner-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.banner-meta strong {
  color: var(--el-text-color-primary);
}
.error-alert {
  margin-bottom: 0;
}
.components-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  min-height: 120px;
}
.component-card {
  border-radius: 8px;
}
.component-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.component-icon {
  font-size: 20px;
}
.component-icon.status-up {
  color: var(--el-color-success);
}
.component-icon.status-down {
  color: var(--el-color-danger);
}
.component-icon.status-skipped {
  color: var(--el-color-info);
}
.component-icon.status-degraded {
  color: var(--el-color-warning);
}
.component-name {
  font-size: 15px;
  font-weight: 600;
  flex: 1;
}
.component-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.component-row {
  display: flex;
  font-size: 12px;
  line-height: 1.6;
}
.row-label {
  width: 80px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}
.row-value {
  color: var(--el-text-color-primary);
  word-break: break-all;
}
.row-value.detail {
  font-family: 'Courier New', monospace;
  font-size: 11px;
  color: var(--el-text-color-regular);
}
.error-row .error-text {
  color: var(--el-color-danger);
}
.trace-id {
  font-family: 'Courier New', monospace;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
