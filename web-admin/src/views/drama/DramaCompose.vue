<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Plus, VideoPlay } from '@element-plus/icons-vue'
import {
  submitCompose,
  getComposeJob,
  getComposeJobs,
  getComposeCapabilities,
  type DramaComposeJob,
  type ComposeCapabilities,
  type ComposeJobPageQuery,
} from '@/api/drama'
import i18n from '@/locales'

/**
 * 短剧合成页。设计来源: ADR 0004 P2-D 批次 5-C。
 * 提交异步合成任务后轮询状态; ffmpeg 缺失时 capabilities 横幅 + 禁止提交 (失败关闭可视化)。
 */

const caps = ref<ComposeCapabilities | null>(null)
const capsLoading = ref(false)

const loading = ref(false)
const tableData = ref<DramaComposeJob[]>([])
const total = ref(0)
const query = reactive<ComposeJobPageQuery>({ page: 1, size: 10, keyword: '', status: '' })

async function loadCaps() {
  capsLoading.value = true
  try {
    caps.value = await getComposeCapabilities()
  } catch {
    caps.value = { ffmpegAvailable: false }
  } finally {
    capsLoading.value = false
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getComposeJobs(query)
    tableData.value = res.records || []
    total.value = res.total || 0
    armPolling()
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  fetchData()
}

function handleReset() {
  query.status = ''
  query.page = 1
  fetchData()
}

function handlePageChange(page: number) {
  query.page = page
  fetchData()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  fetchData()
}

function statusTagType(status?: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function statusText(status?: string) {
  if (status === 'SUCCESS') return i18n.global.t('drama.compose.status.success')
  if (status === 'FAILED') return i18n.global.t('drama.compose.status.failed')
  if (status === 'RUNNING') return i18n.global.t('drama.compose.status.running')
  return i18n.global.t('drama.compose.status.pending')
}

// ===== 轮询进行中任务 =====
let pollTimer: ReturnType<typeof setInterval> | null = null

function armPolling() {
  stopPolling()
  const active = tableData.value.some((j) => j.status === 'PENDING' || j.status === 'RUNNING')
  if (!active) return
  pollTimer = setInterval(async () => {
    try {
      const res = await getComposeJobs(query)
      tableData.value = res.records || []
      total.value = res.total || 0
      const stillActive = tableData.value.some((j) => j.status === 'PENDING' || j.status === 'RUNNING')
      if (!stillActive) stopPolling()
    } catch {
      stopPolling()
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// ===== 提交对话框 =====
const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({ title: '', shots: '', audios: '', subtitle: '', resolution: '720p' })

function openCreate() {
  if (caps.value && caps.value.ffmpegAvailable === false) {
    ElMessage.error(i18n.global.t('drama.compose.message.ffmpegMissing') as string)
    return
  }
  Object.assign(form, { title: '', shots: '', audios: '', subtitle: '', resolution: '720p' })
  dialogVisible.value = true
}

function splitLines(s: string) {
  return s.split(/\r?\n/).map((x) => x.trim()).filter((x) => x.length > 0)
}

async function handleSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning(i18n.global.t('drama.compose.message.titleRequired') as string)
    return
  }
  const shots = splitLines(form.shots)
  if (shots.length === 0) {
    ElMessage.warning(i18n.global.t('drama.compose.message.shotsRequired') as string)
    return
  }
  submitting.value = true
  try {
    await submitCompose({
      title: form.title.trim(),
      shotVideos: shots,
      audioTracks: splitLines(form.audios),
      subtitleFile: form.subtitle.trim() || undefined,
      resolution: form.resolution,
    })
    ElMessage.success(i18n.global.t('drama.compose.message.submitSuccess') as string)
    dialogVisible.value = false
    query.page = 1
    fetchData()
  } finally {
    submitting.value = false
  }
}

async function handleRefreshRow(row: DramaComposeJob) {
  try {
    const fresh = await getComposeJob(row.id)
    const idx = tableData.value.findIndex((j) => j.id === row.id)
    if (idx >= 0) tableData.value[idx] = fresh
  } catch {
    // 忽略单次刷新失败
  }
}

onMounted(() => {
  loadCaps()
  fetchData()
})
onUnmounted(stopPolling)
</script>

<template>
  <div class="compose-view">
    <h2 class="page-title" id="page-title">{{ $t('drama.compose.page.list') }}</h2>

    <el-alert
      v-if="caps && caps.ffmpegAvailable === false"
      type="error"
      show-icon
      :closable="false"
      class="caps-banner"
    >
      {{ $t('drama.compose.message.ffmpegMissing') }}
    </el-alert>
    <el-alert
      v-else-if="caps && caps.ffmpegAvailable"
      type="success"
      show-icon
      :closable="false"
      class="caps-banner"
    >
      {{ $t('drama.compose.caps.ready', { version: caps.ffmpegVersion || '', dir: caps.workDir || '' }) }}
    </el-alert>

    <el-card shadow="never">
      <div class="toolbar" role="search" :aria-label="$t('drama.compose.aria.search')">
        <el-select
          v-model="query.status"
          :placeholder="$t('drama.compose.placeholder.status')"
          clearable
          style="width: 140px"
          :aria-label="$t('drama.compose.placeholder.status')"
        >
          <el-option :label="$t('drama.compose.status.pending')" value="PENDING" />
          <el-option :label="$t('drama.compose.status.running')" value="RUNNING" />
          <el-option :label="$t('drama.compose.status.success')" value="SUCCESS" />
          <el-option :label="$t('drama.compose.status.failed')" value="FAILED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.action.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.action.reset') }}</el-button>
        <el-button
          v-permission="'ai:assistant:use'"
          type="success"
          :icon="Plus"
          :disabled="caps?.ffmpegAvailable === false"
          @click="openCreate"
        >{{ $t('drama.compose.action.submit') }}</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :empty-text="$t('drama.compose.empty.noData')"
        :aria-label="$t('drama.compose.aria.list')"
      >
        <el-table-column prop="title" :label="$t('drama.compose.field.title')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$t('drama.compose.field.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resolution" :label="$t('drama.compose.field.resolution')" width="90" align="center" />
        <el-table-column prop="durationSec" :label="$t('drama.compose.field.duration')" width="100" align="center" />
        <el-table-column prop="outputSize" :label="$t('drama.compose.field.size')" width="110" align="center" />
        <el-table-column :label="$t('drama.compose.field.error')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="error-text">{{ row.errorMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" :label="$t('drama.compose.field.createdAt')" width="180" />
        <el-table-column :label="$t('drama.compose.field.operation')" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              :icon="VideoPlay"
              size="small"
              @click="handleRefreshRow(row as DramaComposeJob)"
            >{{ $t('drama.compose.action.refresh') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="$t('drama.compose.dialog.submitTitle')"
      width="640px"
      :aria-label="$t('drama.compose.aria.formDialog')"
    >
      <el-form label-width="110px" :model="form">
        <el-form-item :label="$t('drama.compose.field.title')">
          <el-input v-model="form.title" :aria-label="$t('drama.compose.field.title')" />
        </el-form-item>
        <el-form-item :label="$t('drama.compose.field.shots')">
          <el-input
            v-model="form.shots"
            type="textarea"
            :rows="4"
            placeholder="shots/a.mp4&#10;shots/b.mp4"
            :aria-label="$t('drama.compose.field.shots')"
          />
          <div class="hint">{{ $t('drama.compose.hint.shots') }}</div>
        </el-form-item>
        <el-form-item :label="$t('drama.compose.field.audios')">
          <el-input
            v-model="form.audios"
            type="textarea"
            :rows="2"
            placeholder="audio/b.mp3"
            :aria-label="$t('drama.compose.field.audios')"
          />
        </el-form-item>
        <el-form-item :label="$t('drama.compose.field.subtitle')">
          <el-input v-model="form.subtitle" placeholder="subs.srt" :aria-label="$t('drama.compose.field.subtitle')" />
        </el-form-item>
        <el-form-item :label="$t('drama.compose.field.resolution')">
          <el-select v-model="form.resolution" style="width: 100%" :aria-label="$t('drama.compose.field.resolution')">
            <el-option label="480p" value="480p" />
            <el-option label="720p" value="720p" />
            <el-option label="1080p" value="1080p" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ $t('common.action.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 600;
}
.caps-banner {
  margin-bottom: 16px;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.error-text {
  color: var(--el-color-danger);
  font-size: 12px;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
