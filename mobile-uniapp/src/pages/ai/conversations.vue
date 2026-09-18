<template>
  <view class="conv-page">
    <view class="conv-head">
      <text class="conv-title">历史会话</text>
      <text class="conv-new" @click="newChat">新对话</text>
    </view>
    <scroll-view scroll-y class="conv-list" @scrolltolower="loadMore" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <view
        v-for="c in list"
        :key="c.id"
        class="conv-item"
        :aria-label="`会话: ${c.title || '未命名对话'}`"
        @click="openChat(c.id)"
      >
        <view class="conv-item__title">{{ c.title || '未命名对话' }}</view>
        <view class="conv-item__meta">
          <text v-if="c.pinned" class="conv-pinned">置顶</text>
          <text class="conv-time">{{ formatTime(c.lastMessageTime || c.createdTime) }}</text>
        </view>
      </view>
      <view v-if="!list.length && !loading" class="conv-empty">
        <text class="conv-empty__text">暂无会话</text>
        <text class="conv-empty__sub">发起一次对话后自动创建</text>
      </view>
      <text v-if="loading" class="loading-text">加载中...</text>
      <text v-if="noMore && list.length > 0" class="loading-text">没有更多了</text>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { get as apiGet } from '@/utils/request'
import { relativeTime } from '@/utils/relativeTime'

/**
 * AI 历史会话列表 (P2-I 移动历史入口)。
 * 点选进入 chat?conversationId= 拉历史；服务端置顶优先排序。
 */

interface AiConversation {
  id: string
  title?: string
  lastMessageTime?: string
  createdTime?: string
  pinned?: boolean
}

const list = ref<AiConversation[]>([])
const loading = ref(false)
const refreshing = ref(false)
const noMore = ref(false)
const pageNo = ref(1)
const pageSize = 20

function formatTime(v?: string): string {
  if (!v) return ''
  const t = Date.parse(v)
  if (Number.isNaN(t)) return ''
  return relativeTime(t)
}

async function loadData(reset = false) {
  if (loading.value) return
  if (reset) {
    pageNo.value = 1
    noMore.value = false
  }
  if (noMore.value) return
  loading.value = true
  try {
    const data = await apiGet<{ records?: AiConversation[] } | AiConversation[]>(
      `/ai/conversations?page=${pageNo.value}&size=${pageSize}`,
    )
    const items = Array.isArray(data) ? data : data.records || []
    if (reset) list.value = items
    else list.value = [...list.value, ...items]
    if (items.length < pageSize) noMore.value = true
    else pageNo.value++
  } catch {
    // 加载失败保留空态, 不阻断
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onRefresh() {
  refreshing.value = true
  loadData(true)
}

function loadMore() {
  loadData()
}

function openChat(id: string) {
  uni.navigateTo({ url: `/pages/ai/chat?conversationId=${id}` })
}

function newChat() {
  uni.navigateTo({ url: '/pages/ai/chat' })
}

onShow(() => {
  loadData(true)
})
</script>

<style scoped>
.conv-page { display: flex; flex-direction: column; height: 100vh; background: var(--yt-bg-page, #f6f8fb); }
.conv-head { display: flex; justify-content: space-between; align-items: center; padding: 12px 16px; background: var(--yt-bg-card, #fff); border-bottom: 1px solid var(--yt-border-default, #e5e7eb); }
.conv-title { font-weight: 600; font-size: 16px; color: var(--yt-text-primary, #111827); }
.conv-new { color: var(--yt-color-primary, #2563eb); font-size: 13px; }
.conv-list { flex: 1; padding: 12px; display: flex; flex-direction: column; gap: 8px; overflow-y: auto; }
.conv-item { background: var(--yt-bg-card, #fff); border: 1px solid var(--yt-border-default, #e5e7eb); border-radius: 8px; padding: 10px 12px; }
.conv-item__title { font-size: 14px; color: var(--yt-text-primary, #111827); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-item__meta { display: flex; gap: 6px; align-items: center; margin-top: 4px; }
.conv-time { font-size: 11px; color: var(--yt-text-disabled, #9ca3af); }
.conv-pinned { font-size: 11px; color: var(--yt-color-warning, #d97706); font-weight: 600; }
.conv-empty { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 48px 0; }
.conv-empty__text { font-size: 14px; color: var(--yt-text-primary, #111827); }
.conv-empty__sub { font-size: 12px; color: var(--yt-text-secondary, #4b5563); }
.loading-text { font-size: 12px; color: var(--yt-text-disabled, #9ca3af); text-align: center; }
</style>
