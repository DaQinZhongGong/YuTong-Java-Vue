<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import {
  getMarketPlugins,
  installPlugin,
  uninstallPlugin,
  enablePlugin,
  disablePlugin,
  checkPluginDependencies,
} from '@/api/plugin'
import type { MarketPluginVO, PluginDependencyCheckResult } from '@/api/plugin'

/**
 * 插件市场页面（45 号文档「插件与模板生态设计」）
 * 功能：卡片式浏览插件市场、安装/卸载/启用/禁用、依赖校验提示
 */

const loading = ref(false)
const pluginList = ref<MarketPluginVO[]>([])
const dependencyVisible = ref(false)
const dependencyLoading = ref(false)
const dependencyResult = ref<PluginDependencyCheckResult | null>(null)
const currentPlugin = ref<MarketPluginVO | null>(null)
const actionLoading = ref(false)

const { t } = useI18n()

// 风险等级映射
const riskLevelMap: Record<string, string> = {
  LOW: t('plugin.market.riskLevel.low'),
  MEDIUM: t('plugin.market.riskLevel.medium'),
  HIGH: t('plugin.market.riskLevel.high'),
}

const riskLevelType = (level: string): 'success' | 'warning' | 'danger' => {
  if (level === 'LOW') return 'success'
  if (level === 'MEDIUM') return 'warning'
  return 'danger'
}

/** 加载插件市场列表 */
async function loadData() {
  loading.value = true
  try {
    const res = await getMarketPlugins()
    pluginList.value = res || []
  } catch (e) {
    ElMessage.error(t('plugin.market.msg.loadFailed'))
  } finally {
    loading.value = false
  }
}

/** 安装插件（先校验依赖） */
async function handleInstall(row: MarketPluginVO) {
  dependencyLoading.value = true
  dependencyVisible.value = true
  currentPlugin.value = row
  try {
    const result = await checkPluginDependencies(row.id)
    dependencyResult.value = result
  } catch (e) {
    dependencyVisible.value = false
    ElMessage.error(t('plugin.market.msg.dependencyCheckFailed'))
  } finally {
    dependencyLoading.value = false
  }
}

/** 确认安装 */
async function confirmInstall() {
  if (actionLoading.value) return
  if (!currentPlugin.value) return
  dependencyVisible.value = false
  actionLoading.value = true
  try {
    await installPlugin(currentPlugin.value.id)
    ElMessage.success(t('plugin.market.msg.installSuccess'))
    currentPlugin.value = null
    dependencyResult.value = null
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || t('plugin.market.msg.installFailed'))
  } finally {
    actionLoading.value = false
  }
}

/** 卸载插件 */
async function handleUninstall(row: MarketPluginVO) {
  try {
    await ElMessageBox.confirm(
      t('plugin.market.msg.uninstallConfirm', { name: row.pluginName }),
      t('plugin.market.msg.uninstallConfirmTitle'),
      { type: 'warning', confirmButtonText: t('plugin.market.msg.uninstallConfirmButton'), cancelButtonText: t('plugin.market.msg.cancelButton') }
    )
  } catch {
    return
  }
  if (actionLoading.value) return
  actionLoading.value = true
  try {
    await uninstallPlugin(row.id)
    ElMessage.success(t('plugin.market.msg.uninstallSuccess'))
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || t('plugin.market.msg.uninstallFailed'))
  } finally {
    actionLoading.value = false
  }
}

/** 启用插件 */
async function handleEnable(row: MarketPluginVO) {
  try {
    await ElMessageBox.confirm(
      t('plugin.market.msg.enableConfirm', { name: row.pluginName }),
      t('plugin.market.msg.enableConfirmTitle'),
      { type: 'info', confirmButtonText: t('plugin.market.msg.enableConfirmButton'), cancelButtonText: t('plugin.market.msg.cancelButton') }
    )
  } catch {
    return
  }
  if (actionLoading.value) return
  actionLoading.value = true
  try {
    await enablePlugin(row.id)
    ElMessage.success(t('plugin.market.msg.enableSuccess'))
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || t('plugin.market.msg.enableFailed'))
  } finally {
    actionLoading.value = false
  }
}

/** 禁用插件 */
async function handleDisable(row: MarketPluginVO) {
  try {
    await ElMessageBox.confirm(
      t('plugin.market.msg.disableConfirm', { name: row.pluginName }),
      t('plugin.market.msg.disableConfirmTitle'),
      { type: 'warning', confirmButtonText: t('plugin.market.msg.disableConfirmButton'), cancelButtonText: t('plugin.market.msg.cancelButton') }
    )
  } catch {
    return
  }
  if (actionLoading.value) return
  actionLoading.value = true
  try {
    await disablePlugin(row.id)
    ElMessage.success(t('plugin.market.msg.disableSuccess'))
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || t('plugin.market.msg.disableFailed'))
  } finally {
    actionLoading.value = false
  }
}

/** 解析依赖列表 */
function parseDependencies(json?: string): string[] {
  if (!json) return []
  try {
    return JSON.parse(json)
  } catch {
    return []
  }
}

onMounted(loadData)
</script>

<template>
  <div class="plugin-market-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">{{ $t('plugin.market.page.title') }}</h2>
      <el-button type="primary" @click="loadData" :icon="Refresh">{{ $t('plugin.market.action.refresh') }}</el-button>
    </div>

    <!-- 插件卡片网格 -->
    <el-row :gutter="16" v-loading="loading">
      <el-col
        v-for="item in pluginList"
        :key="item.id"
        :xs="24"
        :sm="12"
        :md="8"
        :lg="6"
        class="plugin-col"
      >
        <el-card class="plugin-card" shadow="hover">
          <!-- 顶部标签 -->
          <div class="card-header">
            <el-tag v-if="item.installed" type="success" size="small" effect="dark">
              {{ item.installationStatus === 'ACTIVE' ? $t('plugin.market.status.enabled') : $t('plugin.market.status.disabled') }}
            </el-tag>
            <el-tag v-else type="info" size="small">{{ $t('plugin.market.status.notInstalled') }}</el-tag>
            <el-tag :type="riskLevelType(item.riskLevel)" size="small">
              {{ riskLevelMap[item.riskLevel] || item.riskLevel }}
            </el-tag>
          </div>

          <!-- 插件信息 -->
          <div class="plugin-info">
            <h3 class="plugin-name" :title="item.pluginName">{{ item.pluginName }}</h3>
            <p class="plugin-version">{{ $t('plugin.market.field.version') }} {{ item.pluginVersion }}</p>
            <p class="plugin-desc" :title="item.description">
              {{ item.description || $t('plugin.market.tip.noDescription') }}
            </p>
          </div>

          <!-- 元数据 -->
          <div class="plugin-meta">
            <div class="meta-item">
              <span class="meta-label">{{ $t('plugin.market.field.author') }}</span>
              <span class="meta-value">{{ item.author || '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ $t('plugin.market.field.license') }}</span>
              <span class="meta-value">{{ item.licenseType || '-' }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ $t('plugin.market.field.installCount') }}</span>
              <span class="meta-value">{{ item.installCount }}</span>
            </div>
            <div v-if="parseDependencies(item.dependenciesJson).length > 0" class="meta-item">
              <span class="meta-label">{{ $t('plugin.market.field.dependencies') }}</span>
              <span class="meta-value">
                <el-tag
                  v-for="dep in parseDependencies(item.dependenciesJson)"
                  :key="dep"
                  size="small"
                  type="info"
                  class="dep-tag"
                >
                  {{ dep }}
                </el-tag>
              </span>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="plugin-actions">
            <template v-if="!item.installed">
              <el-button type="primary" size="small" @click="handleInstall(item)" :loading="actionLoading">
                {{ $t('plugin.market.action.install') }}
              </el-button>
            </template>
            <template v-else>
              <el-button
                v-if="item.installationStatus === 'ACTIVE'"
                type="warning"
                size="small"
                @click="handleDisable(item)"
                :loading="actionLoading"
              >
                {{ $t('plugin.market.action.disable') }}
              </el-button>
              <el-button
                v-else
                type="success"
                size="small"
                @click="handleEnable(item)"
                :loading="actionLoading"
              >
                {{ $t('plugin.market.action.enable') }}
              </el-button>
              <el-button type="danger" size="small" @click="handleUninstall(item)" :loading="actionLoading">
                {{ $t('plugin.market.action.uninstall') }}
              </el-button>
            </template>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 空状态 -->
    <el-empty v-if="!loading && pluginList.length === 0" :description="$t('plugin.market.tip.empty')" />

    <!-- 依赖校验对话框 -->
    <el-dialog
      v-model="dependencyVisible"
      :title="$t('plugin.market.dialog.installConfirmTitle', { name: currentPlugin?.pluginName || '' })"
      width="500px"
      :close-on-click-modal="false"
    >
      <div v-loading="dependencyLoading">
        <template v-if="dependencyResult">
          <!-- 版本兼容性 -->
          <div class="check-section">
            <div class="check-label">{{ $t('plugin.market.section.platformVersion') }}</div>
            <div class="check-result">
              <el-icon v-if="dependencyResult.versionCompatible" color="#67C23A" size="18">
                <CircleCheck />
              </el-icon>
              <el-icon v-else color="#F56C6C" size="18">
                <CircleClose />
              </el-icon>
              <span :class="dependencyResult.versionCompatible ? 'text-success' : 'text-danger'">
                {{ dependencyResult.versionCompatible ? $t('plugin.market.status.versionCompatible') : dependencyResult.versionConflictMessage }}
              </span>
            </div>
          </div>

          <!-- 依赖校验 -->
          <div class="check-section">
            <div class="check-label">{{ $t('plugin.market.section.dependencyCheck') }}</div>
            <div v-if="parseDependencies(currentPlugin?.dependenciesJson).length === 0" class="check-result">
              <el-icon color="#67C23A" size="18"><CircleCheck /></el-icon>
              <span class="text-success">{{ $t('plugin.market.tip.noExtraDeps') }}</span>
            </div>
            <div v-else-if="dependencyResult.passed" class="check-result">
              <el-icon color="#67C23A" size="18"><CircleCheck /></el-icon>
              <span class="text-success">{{ $t('plugin.market.tip.allDepsSatisfied') }}</span>
            </div>
            <div v-else>
              <div class="check-result">
                <el-icon color="#F56C6C" size="18"><CircleClose /></el-icon>
                <span class="text-danger">{{ $t('plugin.market.tip.missingDeps') }}</span>
              </div>
              <div class="missing-deps">
                <el-tag
                  v-for="dep in dependencyResult.missingDependencies"
                  :key="dep"
                  type="danger"
                  size="small"
                  class="dep-tag"
                >
                  {{ dep }}
                </el-tag>
              </div>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="dependencyVisible = false">{{ $t('plugin.market.action.cancel') }}</el-button>
        <el-button
          type="primary"
          :disabled="!dependencyResult?.passed"
          @click="confirmInstall"
        >
          {{ $t('plugin.market.action.confirmInstall') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.plugin-market-page {
  padding: 8px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--yutong-text-primary);
}

.plugin-col {
  margin-bottom: 16px;
}

.plugin-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  transition: transform 0.2s;
}

.plugin-card:hover {
  transform: translateY(-4px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.plugin-info {
  margin-bottom: 12px;
}

.plugin-name {
  margin: 0 0 4px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--yutong-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plugin-version {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: var(--yutong-text-secondary);
}

.plugin-desc {
  margin: 0;
  font-size: 13px;
  color: var(--yutong-text-regular);
  line-height: 1.5;
  height: 40px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.plugin-meta {
  margin-bottom: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--yutong-border-light);
}

.meta-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 13px;
}

.meta-item:last-child {
  margin-bottom: 0;
}

.meta-label {
  color: var(--yutong-text-secondary);
}

.meta-value {
  color: var(--yutong-text-primary);
  font-weight: 500;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.dep-tag {
  margin: 0;
}

.plugin-actions {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--yutong-border-light);
  display: flex;
  gap: 8px;
}

.plugin-actions .el-button {
  flex: 1;
}

.check-section {
  margin-bottom: 16px;
}

.check-section:last-child {
  margin-bottom: 0;
}

.check-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--yutong-text-primary);
  margin-bottom: 8px;
}

.check-result {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.text-success {
  color: #67C23A;
}

.text-danger {
  color: #F56C6C;
}

.missing-deps {
  margin-top: 8px;
  padding-left: 26px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
