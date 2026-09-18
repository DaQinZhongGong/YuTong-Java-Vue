<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Headset, VideoPlay, Refresh } from '@element-plus/icons-vue'
import { generateMedia, getMediaJobs } from '@/api/media'
import type { MediaJobInfo } from '@/api/media'

const activeTab = ref('image')
const generating = ref(false)
const prompt = ref('')
const jobs = ref<MediaJobInfo[]>([])
const loadingJobs = ref(false)

const mediaTypes = [
  { key: 'image', label: '图片生成', icon: Picture, placeholder: '描述你想生成的图片，如：一只可爱的橘猫坐在窗台上晒太阳' },
  { key: 'audio', label: '语音合成', icon: Headset, placeholder: '输入要合成语音的文本' },
  { key: 'video', label: '视频生成', icon: VideoPlay, placeholder: '描述你想生成的视频内容' },
]

async function handleGenerate() {
  if (!prompt.value.trim()) {
    ElMessage.warning('请输入提示词')
    return
  }
  generating.value = true
  try {
    await generateMedia({
      mediaType: activeTab.value,
      prompt: prompt.value.trim(),
    })
    ElMessage.success('生成任务已提交')
    prompt.value = ''
    loadJobs()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '生成失败'
    ElMessage.error(msg)
  } finally {
    generating.value = false
  }
}

async function loadJobs() {
  loadingJobs.value = true
  try {
    const res = await getMediaJobs({ mediaType: activeTab.value, size: 20 })
    jobs.value = res.records || []
  } catch {
    jobs.value = []
  } finally {
    loadingJobs.value = false
  }
}

function statusTag(status: string): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '排队中',
    RUNNING: '生成中',
    SUCCESS: '已完成',
    FAILED: '失败',
  }
  return map[status] || status
}

function switchTab(tab: string) {
  activeTab.value = tab
  loadJobs()
}

onMounted(loadJobs)
</script>

<template>
  <div class="media-page">
    <h2 class="page-title">媒体工作台</h2>

    <!-- 媒体类型 Tab -->
    <div class="type-tabs">
      <button
        v-for="t in mediaTypes"
        :key="t.key"
        class="type-tab"
        :class="{ active: activeTab === t.key }"
        @click="switchTab(t.key)"
      >
        <el-icon><component :is="t.icon" /></el-icon>
        <span>{{ t.label }}</span>
      </button>
    </div>

    <!-- 生成区域 -->
    <div class="generate-card">
      <textarea
        v-model="prompt"
        class="prompt-input"
        :placeholder="mediaTypes.find(t => t.key === activeTab)?.placeholder"
        rows="4"
      />
      <div class="generate-actions">
        <el-button
          type="primary"
          :loading="generating"
          @click="handleGenerate"
        >{{ generating ? '生成中...' : '开始生成' }}</el-button>
        <el-button :icon="Refresh" @click="loadJobs">刷新列表</el-button>
      </div>
    </div>

    <!-- 任务列表 -->
    <div class="jobs-section">
      <h3 class="section-title">生成记录</h3>
      <div v-loading="loadingJobs" class="jobs-grid">
        <div v-if="jobs.length === 0 && !loadingJobs" class="empty-state">
          暂无生成记录，输入提示词开始创作吧
        </div>
        <div v-for="job in jobs" :key="job.id" class="job-card">
          <div class="job-header">
            <el-tag :type="statusTag(job.status)" size="small">{{ statusLabel(job.status) }}</el-tag>
            <span class="job-time">{{ job.createdTime?.substring(0, 16) }}</span>
          </div>
          <div class="job-prompt">{{ job.prompt }}</div>
          <div v-if="job.outputUrl && job.status === 'SUCCESS'" class="job-result">
            <img
              v-if="job.mediaType === 'image'"
              :src="job.outputUrl"
              class="result-image"
              loading="lazy"
            />
            <audio
              v-else-if="job.mediaType === 'audio'"
              :src="job.outputUrl"
              controls
              class="result-audio"
            />
            <video
              v-else-if="job.mediaType === 'video'"
              :src="job.outputUrl"
              controls
              class="result-video"
            />
            <a :href="job.outputUrl" target="_blank" class="result-link">查看原图/下载</a>
          </div>
          <div v-else-if="job.status === 'FAILED'" class="job-error">
            生成失败，请重试
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.media-page {
  max-width: 960px;
  margin: 0 auto;
}
.page-title {
  margin: 0 0 20px;
  font-size: 22px;
  font-weight: 600;
  color: var(--yt-text-primary, #111827);
}
.type-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.type-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 8px;
  background: var(--yt-bg-card, #fff);
  cursor: pointer;
  font-size: 14px;
  color: var(--yt-text-secondary, #4b5563);
  transition: all 150ms ease-out;
}
.type-tab:hover {
  border-color: var(--yt-color-primary, #2563eb);
  color: var(--yt-color-primary, #2563eb);
}
.type-tab.active {
  border-color: var(--yt-color-primary, #2563eb);
  background: var(--yt-color-primary, #2563eb);
  color: #fff;
}
.generate-card {
  background: var(--yt-bg-card, #fff);
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
}
.prompt-input {
  width: 100%;
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 8px;
  padding: 12px;
  font-size: 14px;
  resize: vertical;
  font-family: inherit;
  box-sizing: border-box;
}
.prompt-input:focus {
  outline: none;
  border-color: var(--yt-color-primary, #2563eb);
}
.generate-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
.section-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--yt-text-primary, #111827);
}
.jobs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 100px;
}
.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px;
  color: var(--yt-text-secondary, #909399);
  font-size: 14px;
}
.job-card {
  background: var(--yt-bg-card, #fff);
  border: 1px solid var(--yt-border-default, #e5e7eb);
  border-radius: 10px;
  padding: 14px;
}
.job-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.job-time {
  font-size: 12px;
  color: var(--yt-text-secondary, #909399);
}
.job-prompt {
  font-size: 13px;
  color: var(--yt-text-primary, #111827);
  margin-bottom: 10px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.result-image {
  width: 100%;
  border-radius: 6px;
  margin-bottom: 6px;
}
.result-audio, .result-video {
  width: 100%;
  margin-bottom: 6px;
}
.result-link {
  font-size: 12px;
  color: var(--yt-color-primary, #2563eb);
  text-decoration: none;
}
.job-error {
  font-size: 13px;
  color: var(--yt-color-danger, #dc2626);
}
</style>
