<script setup lang="ts">
import { ref, onMounted } from 'vue'
import client from '@/api/client'
import type { ChatModelSelection } from './types'

/**
 * 模型选择器 — 按供应商分组下拉, 透传 providerCode/modelCode 到 /ai/chat。
 * 设计来源: ADR 0004 P2-G (ChatView 假 "模型" 按钮接真接口 GET /ai/providers?enabled=true)。
 * 模型来自各供应商 modelListJson (JSON 数组, 元素为字符串或 {code/model/id/name} 对象);
 * 解析失败的供应商仅保留"默认模型"项; 无权限/加载失败时禁用 (不填演示数据)。
 */

const STORAGE_KEY = 'yt_modelSel'

const props = defineProps<{ modelValue: ChatModelSelection }>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: ChatModelSelection): void
}>()

interface ProviderRaw {
  providerCode?: string
  providerName?: string
  modelListJson?: string
  enabled?: boolean
}

interface Group {
  providerCode: string
  providerName: string
  models: { code: string; label: string }[]
}

const AUTO: ChatModelSelection = { providerCode: '', providerName: '', modelCode: '' }

const groups = ref<Group[]>([])
const unavailable = ref(false)
const loading = ref(false)

function parseModels(json?: string): string[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json) as unknown
    if (!Array.isArray(parsed)) return []
    const out: string[] = []
    for (const m of parsed) {
      if (typeof m === 'string' && m.trim()) out.push(m.trim())
      else if (m && typeof m === 'object') {
        const o = m as Record<string, unknown>
        const code = o.code || o.model || o.id || o.name
        if (typeof code === 'string' && code.trim()) out.push(code.trim())
      }
    }
    return out
  } catch {
    return []
  }
}

function currentKey() {
  return `${props.modelValue.providerCode}|||${props.modelValue.modelCode}`
}

async function load() {
  loading.value = true
  try {
    const data = (await client.get('/ai/providers', {
      params: { page: 1, size: 50, enabled: true },
    })) as unknown as { records?: ProviderRaw[] } | ProviderRaw[]
    const arr = (Array.isArray(data) ? data : data.records || []).filter((p) => p && p.providerCode)
    groups.value = arr.map((p) => {
      const code = String(p.providerCode)
      const name = String(p.providerName || code)
      const models = parseModels(p.modelListJson).map((m) => ({ code: m, label: m }))
      return { providerCode: code, providerName: name, models }
    })
    // 恢复上次选择 (仍在列表中才恢复)
    try {
      const saved = localStorage.getItem(STORAGE_KEY)
      if (saved) {
        const s = JSON.parse(saved) as ChatModelSelection
        const ok = groups.value.some(
          (g) => g.providerCode === s.providerCode && g.models.some((m) => m.code === s.modelCode),
        )
        if (ok) emit('update:modelValue', { providerCode: s.providerCode, providerName: s.providerName, modelCode: s.modelCode })
      }
    } catch {
      // 忽略持久化失败
    }
  } catch {
    unavailable.value = true
  } finally {
    loading.value = false
  }
}

function onChange(key: string) {
  if (!key) {
    emit('update:modelValue', { ...AUTO })
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // 忽略
    }
    return
  }
  const [providerCode, modelCode] = key.split('|||')
  const g = groups.value.find((x) => x.providerCode === providerCode)
  const sel: ChatModelSelection = { providerCode, providerName: g?.providerName || providerCode, modelCode }
  emit('update:modelValue', sel)
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sel))
  } catch {
    // 忽略
  }
}

onMounted(load)
</script>

<template>
  <el-select
    :model-value="currentKey() === '|||' ? '' : currentKey()"
    size="small"
    style="width: 170px"
    placeholder="模型"
    :loading="loading"
    :disabled="unavailable"
    :title="unavailable ? '无供应商列表权限或加载失败' : '选择本次对话的供应商与模型'"
    aria-label="模型选择"
    @change="onChange"
  >
    <el-option label="自动路由（默认）" value="" />
    <!-- 不用 el-optgroup: 其样式子路径在按需引入下无法解析 (element-plus/es/components/optgroup/style/css 不存在);
         改扁平选项, label 带供应商前缀 -->
    <template v-for="g in groups" :key="g.providerCode">
      <el-option
        v-if="!g.models.length"
        :label="`${g.providerName} · 默认模型`"
        :value="`${g.providerCode}|||`"
      />
      <el-option
        v-for="m in g.models"
        :key="`${g.providerCode}|||${m.code}`"
        :label="`${g.providerName} · ${m.label}`"
        :value="`${g.providerCode}|||${m.code}`"
      />
    </template>
  </el-select>
</template>
