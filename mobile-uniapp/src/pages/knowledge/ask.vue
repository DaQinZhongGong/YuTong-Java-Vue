<template>
  <view class="ask-page">
    <view class="search-bar">
      <input v-model="question" class="input" placeholder="输入问题，例如：如何配置数据源？" confirm-type="search" @confirm="ask" />
      <button class="btn primary" size="mini" :loading="loading" @click="ask">提问</button>
    </view>
    <view v-if="loading" class="loading">查询中...</view>
    <view v-else-if="answer" class="result">
      <view class="answer">{{ answer }}</view>
      <view v-if="hits.length" class="hits-title">引用来源</view>
      <view v-for="(h,i) in hits" :key="String(i)" class="hit">
        <text class="hit-title">{{ h.title || h.source || `片段 #${i+1}` }}</text>
        <text v-if="h.score!=null" class="hit-score">score {{ Number(h.score).toFixed(3) }}</text>
        <text class="hit-content">{{ h.content || '' }}</text>
      </view>
    </view>
    <view v-else class="empty">输入问题开始知识问答</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface Hit { title?: string; content?: string; score?: number; source?: string }

const question = ref('')
const loading = ref(false)
const answer = ref('')
const hits = ref<Hit[]>([])

async function ask(){
  const q=question.value.trim()
  if(!q) return
  loading.value=true
  answer.value=''
  hits.value=[]
  try{
    const base=(import.meta.env.VITE_API_BASE_URL as string)||'/api/v1'
    const token=uni.getStorageSync('yutong_token') as string||''
    const headers: Record<string,string>={'Content-Type':'application/json'}
    if(token) headers.Authorization=`Bearer ${token}`
    let data: unknown
    // 优先 /ai/knowledge/query，回退 /ai/chat
    let res=await fetch(`${base}/ai/knowledge/query`, { method:'POST', headers, body: JSON.stringify({ question:q, topK:5 }) })
    if(!res.ok){
      res=await fetch(`${base}/ai/chat`, { method:'POST', headers: { ...headers, Accept:'application/json' }, body: JSON.stringify({ message:q, scenario:'PLATFORM_QA', idempotencyKey:`kb-${Date.now()}` }) })
    }
    const json=await res.json() as { code?:string; data?: { answer?:string; content?:string; hits?:Hit[]; citations?:Hit[] }; answer?:string; content?:string; hits?:Hit[] }
    const d=(json as { data?: unknown }).data ?? json
    const obj=d as { answer?:string; content?:string; hits?:Hit[]; citations?:Hit[] }
    answer.value=obj.answer||obj.content||JSON.stringify(obj,null,2)
    hits.value=obj.hits||obj.citations||[]
  }catch(e: unknown){
    const msg=(e as {message?:string})?.message||'查询失败'
    answer.value=`查询失败: ${msg}`
  }finally{ loading.value=false }
}
</script>

<style scoped>
.ask-page { padding:12px; background:#f5f7fa; min-height:100vh; }
.search-bar { display:flex; gap:8px; align-items:center; background:#fff; padding:12px; border-radius:8px; border:1px solid #e5e7eb; }
.input { flex:1; background:#f6f8fb; border-radius:8px; padding:8px 12px; font-size:14px; }
.btn.primary { background:#2563eb; color:#fff; border-radius:8px; }
.loading { text-align:center; padding:24px; color:#909399; }
.result { margin-top:12px; background:#fff; border-radius:8px; padding:16px; border:1px solid #e5e7eb; }
.answer { white-space:pre-wrap; line-height:22px; font-size:14px; color:#111827; }
.hits-title { margin-top:12px; font-weight:600; font-size:13px; border-top:1px solid #f1f5f9; padding-top:12px; }
.hit { margin-top:8px; padding:10px; border:1px solid #f1f5f9; border-radius:8px; }
.hit-title { font-size:13px; font-weight:600; }
.hit-score { font-size:11px; color:#909399; margin-left:6px; }
.hit-content { display:block; margin-top:4px; font-size:12px; color:#4b5563; white-space:pre-wrap; }
.empty { text-align:center; padding:40px 0; color:#909399; }
</style>
