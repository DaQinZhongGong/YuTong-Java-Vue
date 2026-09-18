<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deleteMemory, pageMemories, saveMemory, type AiMemory } from '@/api/memory'

const loading = ref(false)
const records = ref<AiMemory[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const ownerType = ref('')
const memoryKind = ref('')
const content = ref('')
const saving = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await pageMemories({
      page: page.value,
      size: size.value,
      ownerType: ownerType.value || undefined,
      memoryKind: memoryKind.value || undefined,
    })
    records.value = data.records || []
    total.value = data.total || 0
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '加载记忆失败')
  } finally {
    loading.value = false
  }
}

async function create() {
  const text = content.value.trim()
  if (!text) {
    ElMessage.warning('请输入记忆内容')
    return
  }
  saving.value = true
  try {
    await saveMemory({
      ownerType: ownerType.value || 'USER',
      memoryKind: memoryKind.value || 'USER',
      content: text,
      source: 'MANUAL',
    })
    content.value = ''
    ElMessage.success('已写入记忆')
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '写入失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: AiMemory | Record<string, unknown>) {
  try {
    await deleteMemory(String((row as { id: string }).id))
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="memory-page">
    <div class="page-head">
      <div>
        <h2 class="head-title">记忆管理</h2>
        <p class="head-sub">短期记忆走会话窗口；本页管理长期 / 用户 / 全局记忆，写入后实时注入对话上下文。</p>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-select v-model="ownerType" clearable placeholder="归属" style="width:140px" @change="load">
          <el-option label="用户" value="USER" />
          <el-option label="租户" value="TENANT" />
          <el-option label="全局" value="GLOBAL" />
          <el-option label="智能体" value="AGENT" />
        </el-select>
        <el-select v-model="memoryKind" clearable placeholder="种类" style="width:140px" @change="load">
          <el-option label="长期" value="LONG_TERM" />
          <el-option label="用户" value="USER" />
          <el-option label="全局" value="GLOBAL" />
        </el-select>
        <el-input v-model="content" placeholder="写入一条可召回的事实或偏好（禁止密钥/密码）" @keyup.enter="create" />
        <el-button type="primary" :loading="saving" @click="create">写入</el-button>
      </div>
    </el-card>

    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="memoryKind" label="种类" width="120" />
      <el-table-column prop="ownerType" label="归属" width="110" />
      <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
      <el-table-column prop="source" label="来源" width="110" />
      <el-table-column prop="createdTime" label="时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(v:number)=>{ page=v; load() }"
      />
    </div>
  </div>
</template>

<style scoped>
.memory-page { display:flex; flex-direction:column; gap:14px; }
.page-head { display:flex; justify-content:space-between; align-items:flex-start; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:16px; }
.head-title { margin:0; font-size:18px; }
.head-sub { margin:6px 0 0; color: var(--yt-text-secondary); font-size:12px; }
.filter-row { display:flex; gap:8px; align-items:center; }
.pager { display:flex; justify-content:flex-end; }
</style>
