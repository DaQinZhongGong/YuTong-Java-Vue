<script setup lang="ts">
import { ref, onMounted } from 'vue'
import client from '@/api/client'
import type { ChatAgentOption } from './types'

/**
 * Agent 选择器 — 下拉选择已发布 Agent, 透传 agentId 到 /ai/chat。
 * 设计来源: ADR 0004 P2-G (ChatView 假 "Agent" 按钮接真接口 GET /agents?status=PUBLISHED)。
 * 无权限/加载失败时降级为禁用态 (失败关闭可视化, 不填演示数据)。
 */

const STORAGE_KEY = 'yt_agentId'

const props = defineProps<{ modelValue: string }>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
}>()

const options = ref<ChatAgentOption[]>([])
const unavailable = ref(false)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = (await client.get('/agents', {
      params: { page: 1, size: 50, status: 'PUBLISHED' },
    })) as unknown as { records?: ChatAgentOption[] } | ChatAgentOption[]
    const arr = Array.isArray(data) ? data : data.records || []
    options.value = arr.filter((a) => a && a.id)
    // 恢复上次选择 (仍在列表中才恢复)
    try {
      const saved = localStorage.getItem(STORAGE_KEY) || ''
      if (saved && options.value.some((a) => a.id === saved) && saved !== props.modelValue) {
        emit('update:modelValue', saved)
      }
    } catch {
      // 忽略持久化失败
    }
  } catch {
    // 403/网络失败: 禁用选择器, 不伪造选项
    unavailable.value = true
  } finally {
    loading.value = false
  }
}

function onChange(v: string) {
  emit('update:modelValue', v)
  try {
    localStorage.setItem(STORAGE_KEY, v)
  } catch {
    // 忽略持久化失败
  }
}

function label(a: ChatAgentOption) {
  return a.agentName || a.agentCode || a.id
}

onMounted(load)
</script>

<template>
  <el-select
    :model-value="modelValue"
    size="small"
    style="width: 150px"
    placeholder="Agent"
    :loading="loading"
    :disabled="unavailable"
    :title="unavailable ? '无 Agent 列表权限或加载失败' : '选择执行本次对话的 Agent'"
    aria-label="Agent 选择"
    @change="onChange"
  >
    <el-option label="默认助手" value="" />
    <el-option v-for="a in options" :key="a.id" :label="label(a)" :value="a.id" />
  </el-select>
</template>
