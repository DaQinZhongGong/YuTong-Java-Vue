<script setup lang="ts">
/**
 * GA2-L177: 缓存概览页。
 * 设计来源: 91-Web基础后台逐页交互详设「缓存概览页」。
 *
 * 页面展示缓存名称、key 数估算、命中率、过期策略、最后刷新时间。
 * 清理缓存必须二次确认，仅允许清理白名单缓存，禁止页面提供任意 key 删除入口。
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete } from '@element-plus/icons-vue'
import { hasPermission } from '@/utils/permission'
import {
  getCacheOverview,
  clearCache,
  type CacheOverview,
  type CacheItem,
} from '@/api/monitor'

const { t } = useI18n()

const loading = ref(false)
const overview = ref<CacheOverview | null>(null)
const clearing = ref<string | null>(null)
const lastError = ref<string | null>(null)

/** 是否具备缓存清理权限（按钮可见性）。 */
const canClearCache = computed(() => hasPermission('monitor:cache:clear'))

async function fetchOverview() {
  loading.value = true
  lastError.value = null
  try {
    overview.value = await getCacheOverview()
  } catch (e: any) {
    lastError.value = e?.message || t('system.monitorCache.message.loadFailed')
    overview.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchOverview()
})

/** 全局命中率百分比格式化 */
function formatHitRate(rate: number): string {
  if (rate == null) return '-'
  return (rate * 100).toFixed(2) + '%'
}

/** 二次确认 + 清理缓存。 */
async function handleClear(cache: CacheItem) {
  if (!cache.clearable) {
    ElMessage.warning(t('system.monitorCache.message.cacheNotClearable', { cacheName: cache.cacheName }))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('system.monitorCache.message.clearConfirm', { cacheName: cache.cacheName, keyCount: cache.keyCount, keyPrefix: cache.keyPrefix }),
      t('system.monitorCache.message.clearConfirmTitle'),
      {
        confirmButtonText: t('system.monitorCache.message.confirmClear'),
        cancelButtonText: t('common.action.cancel'),
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      }
    )
  } catch {
    return // 用户取消
  }
  clearing.value = cache.cacheName
  try {
    const res = await clearCache(cache.cacheName)
    ElMessage.success(t('system.monitorCache.message.clearSuccess', { cacheName: res.cacheName, deletedKeys: res.deletedKeys }))
    await fetchOverview()
  } catch (e: any) {
    ElMessage.error(e?.message || t('system.monitorCache.message.clearFailed'))
  } finally {
    clearing.value = null
  }
}
</script>

<template>
  <div class="monitor-cache-page">
    <!-- 顶部概览 -->
    <el-card class="overview-card" shadow="never">
      <div class="overview-header">
        <div class="overview-meta">
          <span><strong>{{ $t('common.field.status') }}:</strong>
            <el-tag :type="overview?.status === 'UP' ? 'success' : 'danger'" size="small">
              {{ overview?.status || '-' }}
            </el-tag>
          </span>
          <span><strong>Provider:</strong> {{ overview?.provider || '-' }}</span>
          <span><strong>{{ $t('system.monitorCache.field.instanceId') }}:</strong> {{ overview?.endpoint || '-' }}</span>
          <span><strong>{{ $t('system.monitorCache.field.globalHitRate') }}:</strong>
            <el-tag type="success" size="small" effect="plain">
              {{ formatHitRate(overview?.globalHitRate ?? 0) }}
            </el-tag>
          </span>
          <span><strong>{{ $t('system.monitorCache.field.checkedTime') }}:</strong> {{ overview?.checkedAt || '-' }}</span>
        </div>
        <el-button
          type="primary"
          :icon="Refresh"
          :loading="loading"
          @click="fetchOverview"
        >
          {{ $t('common.action.refresh') }}
        </el-button>
      </div>
    </el-card>

    <el-alert
      v-if="lastError"
      :title="lastError"
      type="error"
      :closable="false"
      show-icon
    />

    <!-- 缓存列表表格 -->
    <el-table
      :data="overview?.caches || []"
      v-loading="loading"
      border
      stripe
      style="width: 100%"
      :empty-text="$t('system.monitorCache.empty.noData')"
    >
      <el-table-column prop="cacheName" :label="$t('system.monitorCache.field.cacheName')" min-width="140">
        <template #default="{ row }">
          <span class="cache-name">{{ row.cacheName }}</span>
          <el-tag v-if="!row.clearable" size="small" type="info" class="ml-2">{{ $t('system.monitorCache.tag.notClearable') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="keyPrefix" :label="$t('system.monitorCache.field.keyPrefix')" min-width="200">
        <template #default="{ row }">
          <code class="key-prefix">{{ row.keyPrefix }}*</code>
        </template>
      </el-table-column>
      <el-table-column prop="keyCount" :label="$t('system.monitorCache.field.keyCount')" width="120" align="right">
        <template #default="{ row }">
          <span :class="{ 'count-zero': row.keyCount === 0 }">{{ row.keyCount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="ttlPolicy" :label="$t('system.monitorCache.field.ttlPolicy')" width="140">
        <template #default="{ row }">
          <el-tag
            :type="row.ttlPolicy === 'empty' ? 'info' : (row.ttlPolicy === 'no-expiry' ? 'warning' : 'success')"
            size="small"
          >
            {{ row.ttlPolicy }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastRefreshTime" :label="$t('system.monitorCache.field.lastRefreshTime')" min-width="180">
        <template #default="{ row }">
          <span class="muted">{{ row.lastRefreshTime || $t('system.monitorCache.tag.notRecorded') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('system.monitorCache.field.operation')" width="140" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-if="canClearCache && row.clearable"
            type="danger"
            size="small"
            :icon="Delete"
            :loading="clearing === row.cacheName"
            @click="handleClear(row as CacheItem)"
          >
            {{ $t('system.monitorCache.action.clear') }}
          </el-button>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 说明 -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="$t('system.monitorCache.title.clearRules')"
      :description="$t('system.monitorCache.message.clearRulesDesc')"
    />
  </div>
</template>

<style scoped>
.monitor-cache-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.overview-card {
  background: linear-gradient(135deg, #f5f7fa 0%, #ffffff 100%);
}
.overview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.overview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.overview-meta strong {
  color: var(--el-text-color-primary);
}
.cache-name {
  font-weight: 600;
}
.ml-2 {
  margin-left: 8px;
}
.key-prefix {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 3px;
  color: var(--el-text-color-regular);
}
.count-zero {
  color: var(--el-text-color-secondary);
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
