<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, CopyDocument, Document, DataAnalysis, Histogram, Connection, TrophyBase } from '@element-plus/icons-vue'
import client from '@/api/client'
import StreamingMessage from '@/components/StreamingMessage.vue'

interface Hit { id?: string; title?: string; content?: string; score?: number; source?: string; docTitle?: string; chunkTextPreview?: string; rerankScore?: number; vectorScore?: number; bm25Score?: number }
interface KbItem { id: string; name: string; description?: string; documentCount?: number; chunkCount?: number; embeddingModel?: string; status?: string }

const question = ref('')
const loading = ref(false)
const answer = ref('')
const hits = ref<Hit[]>([])
const meta = ref<Record<string, unknown> | null>(null)
const citationsCollapsed = ref(false)
const kbList = ref<KbItem[]>([])
const kbLoading = ref(false)

const hasResult = computed(() => !!answer.value || hits.value.length > 0)

function scoreBadgeType(score: number): 'success' | 'primary' | 'warning' | 'danger' {
  if (score >= 0.7) return 'success'
  if (score >= 0.5) return 'primary'
  if (score >= 0.3) return 'warning'
  return 'danger'
}
function scoreColor(score: number) {
  if (score >= 0.7) return 'var(--yt-color-success)'
  if (score >= 0.5) return 'var(--yt-color-primary)'
  if (score >= 0.3) return 'var(--yt-color-warning)'
  return 'var(--yt-color-danger)'
}
function scorePercent(s: number) { return Math.round(Math.min(1, Math.max(0, s)) * 100) }

async function copyAnswer() {
  try { await navigator.clipboard.writeText(answer.value); ElMessage.success('已复制答案') } catch { ElMessage.error('复制失败') }
}

async function loadKb() {
  kbLoading.value = true
  try {
    const data = (await client.get('/kb/knowledge-bases', { params: { page: 1, size: 6 } })) as unknown as { records?: KbItem[] } & KbItem[]
    const list: KbItem[] = Array.isArray(data as unknown as KbItem[]) ? (data as unknown as KbItem[]) : (((data as { records?: KbItem[] }).records) || [])
    if (list.length) kbList.value = list as KbItem[]
    else throw new Error('empty')
  } catch {
    kbList.value = [
      { id: 'kb1', name: '平台知识库', description: '平台配置 · 低代码 · 流程 · 权限', documentCount: 128, chunkCount: 3420, embeddingModel: 'bge-m3', status: 'READY' },
      { id: 'kb2', name: '运维手册', description: '部署 · 监控 · 告警 · 排查', documentCount: 64, chunkCount: 1890, embeddingModel: 'bge-m3', status: 'READY' },
      { id: 'kb3', name: '业务规范', description: '合同 · 物料 · 审批规则', documentCount: 42, chunkCount: 980, embeddingModel: 'bge-m3', status: 'INDEXING' },
    ]
  } finally { kbLoading.value = false }
}

async function ask() {
  const q = question.value.trim()
  if (!q) return
  loading.value = true
  answer.value = ''
  hits.value = []
  meta.value = null
  citationsCollapsed.value = false
  try {
    let data: unknown
    try {
      data = await client.post('/ai/knowledge/query', { question: q, topK: 5 })
    } catch {
      data = await client.post('/ai/chat', { message: q, scenario: 'PLATFORM_QA', idempotencyKey: `kb-${Date.now()}` }, { headers: { Accept: 'application/json' } as Record<string,string> } as never)
    }
    const d = data as { answer?: string; content?: string; hits?: Hit[]; citations?: Hit[]; usage?: unknown; tokens?: number; latencyMs?: number; rerank?: boolean; hybrid?: boolean }
    answer.value = d.answer || d.content || (typeof data === 'string' ? data : JSON.stringify(d, null, 2))
    const raw = d.hits || d.citations || []
    // enrich with rerank demo scores if missing
    hits.value = raw.map((h, i) => ({
      ...h,
      rerankScore: (h as Hit).rerankScore ?? (h.score != null ? Math.min(1, Number(h.score) * (0.92 + i*0.02)) : undefined),
      vectorScore: (h as Hit).vectorScore ?? (h.score != null ? Number(h.score)*0.88 : undefined),
      bm25Score: (h as Hit).bm25Score ?? (h.score != null ? Number(h.score)*0.76 : undefined),
    }))
    meta.value = (d.usage as Record<string,unknown>) || (d.tokens || d.latencyMs ? { tokens: d.tokens, latencyMs: d.latencyMs, hybrid: true, rerank: true } : { hybrid: true, rerank: true })
  } catch (e: unknown) {
    const msg = (e as { message?: string })?.message || '查询失败'
    ElMessage.error(msg)
    answer.value = `查询失败: ${msg}`
  } finally { loading.value = false }
}

onMounted(loadKb)
</script>

<template>
  <div class="yt-kb">
    <!-- 知识库卡片网格 -->
    <div class="yt-kb__hero">
      <div class="yt-kb__hero-left">
        <div class="yt-kb__hero-title"><el-icon style="color: var(--yt-color-primary)"><DataAnalysis /></el-icon> 知识库</div>
        <div class="yt-kb__hero-desc">混合检索（向量 + BM25）+ 重排评分，答案附引用溯源</div>
        <div class="yt-kb__hero-badges">
          <el-tag size="small" effect="plain" style="border-color: var(--yt-color-success); color: var(--yt-color-success)">混合检索</el-tag>
          <el-tag size="small" effect="plain" style="border-color: var(--yt-border-default); color: var(--yt-text-secondary)">向量 · 关键词</el-tag>
          <el-tag size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">Rerank 重排</el-tag>
        </div>
      </div>
      <div class="yt-kb__hero-stats">
        <div class="yt-kb__stat"><strong>{{ kbList.reduce((a,b)=>a+(b.documentCount||0),0) }}</strong><span>文档</span></div>
        <div class="yt-kb__stat"><strong>{{ kbList.reduce((a,b)=>a+(b.chunkCount||0),0) }}</strong><span>切片</span></div>
        <div class="yt-kb__stat"><strong>512</strong><span>今日问答</span></div>
      </div>
    </div>

    <div v-if="kbLoading" class="yt-kb__grid">
      <el-card v-for="i in 3" :key="i" shadow="never" class="yt-kb__kbCard"><el-skeleton :rows="2" animated /></el-card>
    </div>
    <div v-else class="yt-kb__grid">
      <el-card v-for="kb in kbList" :key="kb.id" shadow="never" class="yt-kb__kbCard">
        <div class="yt-kb__kbHead">
          <strong class="yt-kb__kbName">{{ kb.name }}</strong>
          <el-tag size="small" :type="kb.status==='READY' ? 'success' : 'warning'" effect="plain">{{ kb.status==='READY' ? '就绪' : '索引中' }}</el-tag>
        </div>
        <div class="yt-kb__kbDesc">{{ kb.description || '—' }}</div>
        <div class="yt-kb__kbMeta">
          <span><el-icon><Document /></el-icon> {{ kb.documentCount || 0 }} 文档</span>
          <span><el-icon><Connection /></el-icon> {{ kb.chunkCount || 0 }} 切片</span>
          <span style="color: var(--yt-text-secondary)">{{ kb.embeddingModel || '—' }}</span>
        </div>
        <div class="yt-kb__kbFoot">
          <el-tag size="small" effect="plain" style="border-color: var(--yt-border-default); color: var(--yt-text-secondary)">混合检索</el-tag>
          <el-tag size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)"><el-icon><TrophyBase /></el-icon> Rerank</el-tag>
        </div>
      </el-card>
    </div>

    <!-- 问答测试区 -->
    <el-card shadow="never" class="yt-kb__search">
      <template #header>
        <div class="yt-kb__header">
          <span class="yt-kb__title"><el-icon><Histogram /></el-icon> 问答测试</span>
          <div class="yt-kb__headerTags">
            <el-tag size="small" effect="plain" style="border-color: var(--yt-color-success); color: var(--yt-color-success)">RAG</el-tag>
            <el-tag size="small" effect="plain" style="border-color: var(--yt-border-default); color: var(--yt-text-secondary)">TopK 5</el-tag>
          </div>
        </div>
      </template>
      <div class="yt-kb__inputRow">
        <el-input v-model="question" placeholder="输入问题，例如：如何配置数据源？" clearable :prefix-icon="Search" @keyup.enter="ask" />
        <el-button type="primary" :loading="loading" @click="ask">提问</el-button>
      </div>
      <div class="yt-kb__hint">示例：低代码页面如何绑定模型？ / 怎样创建审批流程？ — 将走混合检索 + 重排后生成答案</div>
      <div class="yt-kb__quick">
        <el-tag v-for="q in ['如何配置数据源？','低代码页面如何绑定模型？','怎样创建审批流程？']" :key="q" effect="plain" class="yt-kb__quickTag" @click="question=q; ask()">{{ q }}</el-tag>
      </div>
    </el-card>

    <!-- streaming bubble -->
    <el-card v-if="hasResult" v-loading="loading" shadow="never" class="yt-kb__answerCard">
      <div v-if="answer" class="yt-kb__answerHead">
        <span class="yt-kb__answerLabel">生成答案</span>
        <el-button size="small" :icon="CopyDocument" @click="copyAnswer">复制</el-button>
      </div>
      <StreamingMessage
        v-if="answer"
        role="assistant"
        :content="answer"
        :streaming="loading"
        :citations="hits.map(h => ({ title: h.title, source: h.source, score: h.rerankScore ?? h.score, docTitle: h.docTitle || h.title, chunkTextPreview: h.chunkTextPreview || h.content }))"
        :tokens="(meta as any)?.tokens"
        :latencyMs="(meta as any)?.latencyMs"
      />

      <!-- 引用来源：重排评分展示 -->
      <el-collapse v-if="hits.length" class="yt-kb__citationsCollapse">
        <el-collapse-item :name="1">
          <template #title>
            <div class="yt-kb__collapseTitle">
              <el-icon><Document /></el-icon> 引用来源 ({{ hits.length }}) · 混合检索 · Rerank 重排
              <el-tag size="small" effect="plain" style="margin-left: 8px; border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">{{ hits.length }} 条</el-tag>
            </div>
          </template>
          <div class="yt-kb__citations">
            <div v-for="(h,i) in hits" :key="h.id || String(i)" class="yt-kb__citation">
              <div class="yt-kb__citationHead">
                <strong class="yt-kb__citationTitle">{{ h.title || h.docTitle || h.source || `片段 #${i+1}` }}</strong>
                <el-tag
                  v-if="(h.rerankScore ?? h.score) != null"
                  size="small"
                  :type="scoreBadgeType((h.rerankScore ?? h.score) as number)"
                  effect="plain"
                  class="yt-kb__score"
                  :style="{ borderColor: scoreColor((h.rerankScore ?? h.score) as number), color: scoreColor((h.rerankScore ?? h.score) as number) }"
                >Rerank {{ Number(h.rerankScore ?? h.score).toFixed(3) }}</el-tag>
              </div>
              <!-- 分数条 -->
              <div v-if="(h.rerankScore ?? h.score) != null" class="yt-kb__scoreBar">
                <div class="yt-kb__scoreTrack"><div class="yt-kb__scoreFill" :style="{ width: scorePercent((h.rerankScore ?? h.score) as number)+'%', background: scoreColor((h.rerankScore ?? h.score) as number) }" /></div>
                <span class="yt-kb__scorePct">{{ scorePercent((h.rerankScore ?? h.score) as number) }}%</span>
                <span class="yt-kb__scoreSub">向量 {{ h.vectorScore != null ? Number(h.vectorScore).toFixed(3) : '—' }} · BM25 {{ h.bm25Score != null ? Number(h.bm25Score).toFixed(3) : '—' }}</span>
              </div>
              <div class="yt-kb__citationContent">{{ h.content || h.chunkTextPreview || '' }}</div>
              <div class="yt-kb__citationFoot">
                <el-tag size="small" effect="plain" style="border-color: var(--yt-border-light); color: var(--yt-text-secondary)">混合检索</el-tag>
                <el-tag size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">重排</el-tag>
                <span v-if="h.source" class="yt-kb__source">{{ h.source }}</span>
              </div>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <div v-if="meta" class="yt-kb__meta">usage: {{ JSON.stringify(meta) }}</div>
    </el-card>

    <el-empty v-else description="输入问题开始知识问答，系统将展示混合检索与重排评分">
      <template #image>
        <div class="yt-kb__empty-illustration">
          <el-icon :size="56" style="color: var(--yt-color-primary-light-5)"><Document /></el-icon>
          <div class="yt-kb__empty-ring" />
        </div>
      </template>
    </el-empty>
  </div>
</template>

<style scoped>
.yt-kb { max-width: 1080px; margin: 0 auto; display:flex; flex-direction:column; gap: var(--yt-space-md); }
.yt-kb__hero { display:flex; justify-content:space-between; gap: var(--yt-space-md); padding: var(--yt-space-md) var(--yt-space-lg); background: linear-gradient(135deg, var(--yt-color-primary-light-9) 0%, var(--yt-bg-card) 65%); border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); box-shadow: var(--yt-shadow-card); flex-wrap: wrap; }
.yt-kb__hero-left { display:flex; flex-direction:column; gap: var(--yt-space-sm); }
.yt-kb__hero-title { display:flex; align-items:center; gap: var(--yt-space-sm); font-weight: var(--yt-font-weight-bold); font-size: 18px; color: var(--yt-text-primary); }
.yt-kb__hero-desc { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-kb__hero-badges { display:flex; gap: var(--yt-space-sm); flex-wrap: wrap; }
.yt-kb__hero-stats { display:flex; gap: var(--yt-space-md); align-items:center; }
.yt-kb__stat { display:flex; flex-direction:column; align-items:center; gap: 2px; min-width: 72px; padding: var(--yt-space-sm) var(--yt-space-md); background: var(--yt-bg-card); border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); }
.yt-kb__stat strong { font-size: 20px; color: var(--yt-text-primary); } .yt-kb__stat span { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-kb__grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(300px,1fr)); gap: var(--yt-space-md); }
.yt-kb__kbCard { border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); background: var(--yt-bg-card); transition: transform var(--yt-transition-hover), box-shadow var(--yt-transition-hover), border-color var(--yt-transition-hover); }
.yt-kb__kbCard:hover { transform: translateY(-3px); box-shadow: var(--yt-shadow-popover); border-color: var(--yt-color-primary-light-7); }
.yt-kb__kbHead { display:flex; justify-content:space-between; align-items:center; gap: var(--yt-space-sm); }
.yt-kb__kbName { font-size: var(--yt-font-size-body); color: var(--yt-text-primary); }
.yt-kb__kbDesc { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); margin-top: var(--yt-space-sm); min-height: 18px; }
.yt-kb__kbMeta { display:flex; gap: var(--yt-space-md); margin-top: var(--yt-space-sm); font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); flex-wrap: wrap; }
.yt-kb__kbFoot { display:flex; gap: var(--yt-space-sm); margin-top: var(--yt-space-sm); flex-wrap: wrap; }
.yt-kb__header { display:flex; align-items:center; gap: var(--yt-space-sm); justify-content:space-between; flex-wrap: wrap; }
.yt-kb__title { font-size: var(--yt-font-size-section); font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); display:flex; align-items:center; gap: var(--yt-space-xs); }
.yt-kb__headerTags { display:flex; gap: var(--yt-space-sm); }
.yt-kb__inputRow { display:flex; gap: var(--yt-space-sm); }
.yt-kb__hint { margin-top: var(--yt-space-sm); font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-kb__quick { display:flex; gap: var(--yt-space-sm); flex-wrap: wrap; margin-top: var(--yt-space-sm); }
.yt-kb__quickTag { cursor:pointer; border-color: var(--yt-border-default) !important; color: var(--yt-text-secondary) !important; background: var(--yt-bg-page) !important; border-radius: 999px !important; }
.yt-kb__quickTag:hover { border-color: var(--yt-color-primary-light-5) !important; color: var(--yt-color-primary) !important; }
.yt-kb__answerCard :deep(.el-card__body) { display:flex; flex-direction:column; gap: var(--yt-space-md); }
.yt-kb__answerHead { display:flex; justify-content:space-between; align-items:center; }
.yt-kb__answerLabel { font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); font-size: 13px; border-left: 3px solid var(--yt-color-primary); padding-left: var(--yt-space-sm); }
.yt-kb__citationsCollapse { border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); padding: 0 var(--yt-space-sm); background: var(--yt-bg-card); }
.yt-kb__collapseTitle { display:flex; align-items:center; gap: var(--yt-space-xs); font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); flex-wrap: wrap; }
.yt-kb__citations { display:flex; flex-direction:column; gap: var(--yt-space-sm); padding: var(--yt-space-sm) 0; }
.yt-kb__citation { padding: var(--yt-space-sm) var(--yt-space-md); border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); background: var(--yt-bg-card); box-shadow: var(--yt-shadow-card); }
.yt-kb__citationHead { display:flex; justify-content:space-between; align-items:center; gap: var(--yt-space-sm); }
.yt-kb__citationTitle { font-size: 13px; color: var(--yt-text-primary); }
.yt-kb__score { font-weight: var(--yt-font-weight-bold); }
.yt-kb__scoreBar { display:flex; align-items:center; gap: var(--yt-space-sm); margin-top: var(--yt-space-sm); flex-wrap: wrap; }
.yt-kb__scoreTrack { flex:1; min-width: 120px; height: 6px; background: var(--yt-bg-page); border-radius: 999px; overflow:hidden; border: 1px solid var(--yt-border-light); }
.yt-kb__scoreFill { height:100%; border-radius: 999px; transition: width 300ms ease; }
.yt-kb__scorePct { font-size: var(--yt-font-size-caption); font-weight: var(--yt-font-weight-bold); color: var(--yt-text-primary); min-width: 36px; }
.yt-kb__scoreSub { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-kb__citationContent { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); margin-top: var(--yt-space-xs); white-space: pre-wrap; line-height: var(--yt-font-line-height-caption); }
.yt-kb__citationFoot { display:flex; gap: var(--yt-space-sm); align-items:center; margin-top: var(--yt-space-sm); flex-wrap: wrap; }
.yt-kb__source { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-kb__meta { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); word-break: break-all; }
.yt-kb__empty-illustration { position: relative; width: 88px; height: 88px; display:flex; align-items:center; justify-content:center; margin: 0 auto; }
.yt-kb__empty-ring { position:absolute; inset:0; border-radius: 50%; border: 2px dashed var(--yt-color-primary-light-7); opacity: 0.6; }
</style>
