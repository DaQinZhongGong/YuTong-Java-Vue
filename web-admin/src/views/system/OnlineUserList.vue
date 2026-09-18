<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Delete } from '@element-plus/icons-vue'
import { getOnlineUsers, kickOnlineUser } from '@/api/onlineUser'
import type { OnlineUserSession } from '@/api/onlineUser'

const loading = ref(false)
const tableData = ref<OnlineUserSession[]>([])
const keyword = ref('')
const autoRefresh = ref(true)
let timer: ReturnType<typeof setInterval> | null = null

async function loadData() {
  loading.value = true
  try {
    tableData.value = await getOnlineUsers(keyword.value || undefined)
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadData()
}

function handleReset() {
  keyword.value = ''
  loadData()
}

async function handleKick(row: OnlineUserSession) {
  try {
    await ElMessageBox.confirm(
      `确定将用户「${row.username}」强制下线吗？`,
      '强制下线',
      { type: 'warning' }
    )
  } catch {
    return
  }
  const ok = await kickOnlineUser(row.tokenId)
  if (ok) {
    ElMessage.success(`已将 ${row.username} 强制下线`)
  } else {
    ElMessage.warning('会话不存在或已过期')
  }
  loadData()
}

function formatTime(time: string): string {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

function activeAgo(time: string): string {
  if (!time) return '-'
  const diff = Date.now() - new Date(time).getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  return Math.floor(diff / 86400000) + ' 天前'
}

function truncateUa(ua: string): string {
  if (!ua) return '-'
  return ua.length > 60 ? ua.substring(0, 60) + '...' : ua
}

onMounted(() => {
  loadData()
  timer = setInterval(() => {
    if (autoRefresh.value) loadData()
  }, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div>
    <h2 class="page-title">在线用户</h2>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="用户名/ID"
          clearable
          style="width: 220px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          inactive-text="暂停"
          style="margin-left: 12px"
        />
        <span class="online-count">
          当前在线: <strong>{{ tableData.length }}</strong> 人
        </span>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="userId" label="用户 ID" width="180">
          <template #default="{ row }">
            <span class="mono">{{ row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tenantId" label="租户" width="100" />
        <el-table-column prop="ip" label="IP 地址" width="140" />
        <el-table-column label="登录时间" width="170">
          <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
        </el-table-column>
        <el-table-column label="最后活跃" width="140">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ activeAgo(row.lastActiveTime) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="userAgent" label="User-Agent" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.userAgent" placement="top" :show-after="500">
              <span class="ua-text">{{ truncateUa(row.userAgent) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:monitor:online'"
              type="danger"
              link
              size="small"
              :icon="Delete"
              @click="handleKick(row as OnlineUserSession)"
            >下线</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 600;
}
.toolbar {
  margin-bottom: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.online-count {
  margin-left: auto;
  font-size: 14px;
  color: var(--yt-text-secondary, #606266);
}
.online-count strong {
  color: var(--yt-color-primary, #2563eb);
  font-size: 18px;
}
.mono {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}
.ua-text {
  font-size: 12px;
  color: var(--yt-text-secondary, #909399);
}
</style>
