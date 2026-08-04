<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElNotification } from 'element-plus'
import { getMessages, markRead } from '@/api/message'
import { realtimeClient } from '@/api/realtime'
import type { RealtimeEnvelope } from '@/api/realtime'
import type { Message, PageResult } from '@/api/types'

/**
 * 站内信列表页。设计来源: 10-Vue3管理端设计、44-实时通信与消息推送设计
 *
 * GA2-32 实时推送集成:
 *  - 挂载时建立 WebSocket 连接，订阅 user:{userId} 频道
 *  - 收到 NOTIFICATION/TODO 类型信封时:
 *    1) ElNotification 弹出右上角通知（含 title/content/bizType）
 *    2) 自动刷新列表（无需用户手动点刷新）
 *    3) 自动发送 ACK_DELIVERED 投递去重
 *  - 卸载时断开 WebSocket 连接
 *  - realtime.push.enabled=false 时连接失败静默回退到 REST 拉取（GA 基线无损失）
 */
const { t } = useI18n()
const loading = ref(false)
const tableData = ref<Message[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
/** 实时推送连接状态徽标（仅在非 CONNECTED 时展示） */
const realtimeState = ref<string>('IDLE')

async function loadData() {
  loading.value = true
  try {
    const res: PageResult<Message> = await getMessages({ page: currentPage.value, size: pageSize.value })
    tableData.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function handleMarkRead(row: Message) {
  await markRead(row.id)
  ElMessage.success(t('message.msg.markRead'))
  loadData()
}

function handlePageChange(page: number) { currentPage.value = page; loadData() }

/** 实时推送回调: 收到信封时弹出通知并刷新列表 */
function onEnvelope(env: RealtimeEnvelope) {
  // 仅处理业务消息，过滤 PING 心跳
  if (env.type === 'PING') return
  // 弹出通知（对齐 44 号文档 line 60-69 type 枚举展示）
  const typeLabel = env.type === 'TODO' ? t('message.type.todo') : env.type === 'NOTIFICATION' ? t('message.type.notification') : env.type
  ElNotification({
    title: `[${typeLabel}] ${env.title || t('message.msg.newMessage')}`,
    message: env.content || '',
    type: env.type === 'TODO' ? 'warning' : 'info',
    duration: 5000,
    position: 'top-right',
  })
  // 自动刷新列表（确保新消息立即可见）
  loadData()
}

function onStateChange(state: string) {
  realtimeState.value = state
}

onMounted(() => {
  loadData()
  // 建立实时推送连接
  realtimeClient.connect({
    onEnvelope,
    onStateChange,
    onError: () => {
      // 静默处理错误，回退到 REST 拉取（GA 基线无损失）
      console.warn('[MessageList] ' + t('message.msg.realtimeConnectionError'))
    },
  })
})

onUnmounted(() => {
  // 离开页面时断开连接（避免多页面共享连接的泄漏）
  // 注意: 若多个页面共享 realtimeClient 单例，此处应改为引用计数而非直接断开
  // 第一版 MessageList 是唯一消费者，直接断开；后续若多页面共享需重构
  realtimeClient.disconnect()
})
</script>

<template>
  <div>
    <div v-if="realtimeState !== 'CONNECTED' && realtimeState !== 'IDLE'" class="realtime-banner">
      <el-tag :type="realtimeState === 'RECONNECTING' ? 'warning' : 'info'" size="small">
        {{ $t('message.realtime.label') }} {{ realtimeState }}
      </el-tag>
    </div>
    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column prop="messageTitle" :label="$t('message.field.title')" min-width="200" />
      <el-table-column prop="messageType" :label="$t('message.field.type')" width="120" />
      <el-table-column prop="readStatus" :label="$t('common.field.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.readStatus === 'UNREAD' ? 'danger' : 'info'">{{ row.readStatus === 'UNREAD' ? $t('message.status.unread') : $t('message.status.read') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" :label="$t('message.field.content')" min-width="300" show-overflow-tooltip />
      <el-table-column prop="createdAt" :label="$t('common.field.createdTime')" width="180" />
      <el-table-column :label="$t('message.field.operation')" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.readStatus === 'UNREAD'" size="small" @click="handleMarkRead(row as Message)">{{ $t('system.message.action.markRead') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top: 16px; justify-content: flex-end" v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="handlePageChange" />
  </div>
</template>

<style scoped>
.realtime-banner {
  margin-bottom: 8px;
  padding: 4px 8px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 4px;
  font-size: 12px;
}
</style>
