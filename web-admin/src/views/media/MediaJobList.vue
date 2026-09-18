<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createMediaJob, getMediaJob, pageMediaJobs, type MediaJob } from '@/api/media'

const loading = ref(false)
const creating = ref(false)
const records = ref<MediaJob[]>([])
const total = ref(0)
const page = ref(1)
const mediaType = ref('image')
const prompt = ref('')

async function load() {
  loading.value = true
  try {
    const data = await pageMediaJobs({ page: page.value, size: 20, mediaType: mediaType.value })
    records.value = data.records || []
    total.value = data.total || 0
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '加载任务失败')
  } finally {
    loading.value = false
  }
}

async function create() {
  const text = prompt.value.trim()
  if (!text) {
    ElMessage.warning('请输入提示词')
    return
  }
  creating.value = true
  try {
    const job = await createMediaJob(mediaType.value, { prompt: text })
    ElMessage.success(`任务已提交：${job.id}`)
    prompt.value = ''
    await load()
    poll(job)
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '提交失败')
  } finally {
    creating.value = false
  }
}

async function poll(job: MediaJob) {
  let tries = 0
  while (tries < 20) {
    await new Promise((r) => setTimeout(r, 1500))
    const latest = await getMediaJob(job.mediaType, job.id)
    if (latest.status === 'SUCCESS' || latest.status === 'FAILED') {
      await load()
      if (latest.status === 'SUCCESS') ElMessage.success('生成完成')
      else ElMessage.error('生成失败')
      return
    }
    tries++
  }
}

onMounted(load)
</script>

<template>
  <div class="media-page">
    <div class="page-head">
      <div>
        <h2 class="head-title">多模态生成</h2>
        <p class="head-sub">图像/语音走已配置的 OpenAI 兼容供应商；视频/PPT 未配置时明确失败，不会返回假 URL。</p>
      </div>
    </div>
    <el-card shadow="never">
      <div class="filter-row">
        <el-select v-model="mediaType" style="width:140px" @change="load">
          <el-option label="图像" value="image" />
          <el-option label="语音" value="audio" />
          <el-option label="视频" value="video" />
          <el-option label="PPT" value="ppt" />
        </el-select>
        <el-input v-model="prompt" placeholder="提示词" @keyup.enter="create" />
        <el-button type="primary" :loading="creating" @click="create">生成</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </el-card>
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="mediaType" label="类型" width="90" />
      <el-table-column prop="status" label="状态" width="110" />
      <el-table-column prop="prompt" label="提示词" min-width="240" show-overflow-tooltip />
      <el-table-column label="产物" min-width="220">
        <template #default="{ row }">
          <a v-if="row.outputUrl" :href="row.outputUrl" target="_blank" rel="noopener">打开产物</a>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdTime" label="时间" width="180" />
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total" :current-page="page" @current-change="(v:number)=>{ page=v; load() }" />
    </div>
  </div>
</template>

<style scoped>
.media-page { display:flex; flex-direction:column; gap:14px; }
.page-head { background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:16px; }
.head-title { margin:0; font-size:18px; }
.head-sub { margin:6px 0 0; color: var(--yt-text-secondary); font-size:12px; }
.filter-row { display:flex; gap:8px; }
.pager { display:flex; justify-content:flex-end; }
</style>
