<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  VideoPlay,
  Reading,
  Document,
  FolderOpened,
  DataAnalysis,
  ChatDotRound,
  Clock,
} from '@element-plus/icons-vue'
import {
  askQuestion,
  getKbStats,
  pageKbConversations,
  getKbConversation,
  reindexKbDocument,
  getKnowledgeBase,
  updateRetrievalConfig,
  type AskQuestionVO,
  type KbStatsVO,
  type KbConversationLog,
  type AskQuestionRequest,
} from '@/api/knowledge'
import StreamingMessage from '@/components/StreamingMessage.vue'
import KnowledgeGraphTab from './KnowledgeGraphTab.vue'
import EmbeddingTestPanel from './EmbeddingTestPanel.vue'

/**
 * 知识库运营页。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 *
 * 4 个 Tab:
 *  1. 运营监控: 5 项指标统计看板 (文档数/分块数/今日问答/命中率/平均分)
 *  2. 问答测试: 输入问题 + 答案展示 + 引用来源 + 拒答提示 + 流式增量展示
 *  3. 问答历史: 分页查询问答日志 + 详情弹窗
 *  4. 文档管理: 文档列表 + 重新索引
 */

// 种子知识库 ID (V015 迁移灌入的 kb-ops-mock)
const DEFAULT_KB_ID = '01K8KB0OPS0MOCK0000000000001'
const activeTab = ref<'stats' | 'ask' | 'history' | 'docs' | 'graph' | 'tuning' | 'embedding'>('stats')
const currentKbId = ref(DEFAULT_KB_ID)

// ==================== 运营监控 ====================
const stats = ref<KbStatsVO>({})
const statsLoading = ref(false)

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await getKbStats(currentKbId.value)
  } catch (e) {
    console.error('loadStats failed', e)
  } finally {
    statsLoading.value = false
  }
}

// ==================== 问答测试（含流式增量） ====================
const question = ref('')
const askLoading = ref(false)
const askResult = ref<AskQuestionVO | null>(null)
const streamingActive = ref(false)
const streamingText = ref('')
let streamingTimer: ReturnType<typeof setInterval> | null = null

function clearStreaming() {
  if (streamingTimer) { clearInterval(streamingTimer); streamingTimer = null }
  streamingActive.value = false
  streamingText.value = ''
}

function playStreaming(full: string) {
  clearStreaming()
  streamingActive.value = true
  streamingText.value = ''
  let idx = 0
  const step = Math.max(1, Math.ceil(full.length / 48))
  streamingTimer = setInterval(() => {
    idx = Math.min(full.length, idx + step)
    streamingText.value = full.slice(0, idx)
    if (idx >= full.length) {
      clearStreaming()
    }
  }, 28)
}

async function handleAsk() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  askLoading.value = true
  askResult.value = null
  clearStreaming()
  try {
    const req: AskQuestionRequest = {
      kbId: currentKbId.value,
      question: question.value.trim(),
    }
    const res = await askQuestion(req)
    askResult.value = res
    if (res.refused) {
      ElMessage.warning('已拒答: ' + (res.refuseReason || '未知原因'))
    } else {
      ElMessage.success('问答成功, 命中 ' + res.hitChunkCount + ' 个分块')
      playStreaming(res.answer || '')
    }
  } catch (e) {
    console.error('ask failed', e)
    ElMessage.error('问答失败')
  } finally {
    askLoading.value = false
  }
}

function scoreTagType(score: number): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (score >= 0.7) return 'success'
  if (score >= 0.5) return 'primary'
  if (score >= 0.3) return 'warning'
  return 'danger'
}

function refuseReasonText(reason?: string): string {
  switch (reason) {
    case 'NO_HITS': return '无命中内容'
    case 'LOW_CONFIDENCE': return '低置信度'
    case 'KB_DISABLED': return '知识库已禁用'
    case 'ACL_DENIED': return '权限不足'
    default: return reason || '未知'
  }
}

// ==================== 问答历史 ====================
const historyLoading = ref(false)
const historyList = ref<KbConversationLog[]>([])
const historyTotal = ref(0)
const historyPageNo = ref(1)
const historyPageSize = ref(10)
const historyFilter = ref({ kbId: '', userId: '', refused: '' as string })
const detailDialogVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<KbConversationLog | null>(null)

async function loadHistory() {
  historyLoading.value = true
  try {
    const page = await pageKbConversations({
      page: historyPageNo.value,
      size: historyPageSize.value,
      kbId: historyFilter.value.kbId || currentKbId.value || undefined,
      userId: historyFilter.value.userId || undefined,
      refused: historyFilter.value.refused === '' ? undefined : historyFilter.value.refused === 'true',
    })
    historyList.value = page.records || []
    historyTotal.value = page.total || 0
  } catch (e) {
    console.error('loadHistory failed', e)
  } finally {
    historyLoading.value = false
  }
}

async function viewDetail(row: KbConversationLog) {
  detailDialogVisible.value = true
  detailLoading.value = true
  try {
    detailData.value = await getKbConversation(row.id)
  } catch (e) {
    console.error('viewDetail failed', e)
  } finally {
    detailLoading.value = false
  }
}

function historyRefusedTagType(refused?: boolean): 'primary' | 'success' | 'info' | 'warning' | 'danger' {
  if (refused === true) return 'danger'
  if (refused === false) return 'success'
  return 'info'
}

// ==================== 检索调参 (V050 P2-E 混合检索可配) ====================
const tuningLoading = ref(false)
const tuningSaving = ref(false)
const tuningForm = ref({
  hybridEnabled: false,
  hybridVectorWeight: 0.7,
  hybridTopK: 5,
  hybridMinScore: 0.01,
})

async function loadTuning() {
  if (!currentKbId.value) return
  tuningLoading.value = true
  try {
    const kb = await getKnowledgeBase(currentKbId.value)
    tuningForm.value = {
      hybridEnabled: kb.hybridEnabled ?? false,
      hybridVectorWeight: kb.hybridVectorWeight ?? 0.7,
      hybridTopK: kb.hybridTopK ?? 5,
      hybridMinScore: kb.hybridMinScore ?? 0.01,
    }
  } catch (e) {
    console.error('loadTuning failed', e)
    ElMessage.error('检索配置加载失败')
  } finally {
    tuningLoading.value = false
  }
}

async function saveTuning() {
  if (!currentKbId.value) return
  tuningSaving.value = true
  try {
    const kb = await updateRetrievalConfig(currentKbId.value, { ...tuningForm.value })
    tuningForm.value = {
      hybridEnabled: kb.hybridEnabled ?? false,
      hybridVectorWeight: kb.hybridVectorWeight ?? 0.7,
      hybridTopK: kb.hybridTopK ?? 5,
      hybridMinScore: kb.hybridMinScore ?? 0.01,
    }
    ElMessage.success('检索配置已保存，下次检索即生效')
  } catch (e) {
    console.error('saveTuning failed', e)
    ElMessage.error('检索配置保存失败')
  } finally {
    tuningSaving.value = false
  }
}

function onTabChange(name: unknown) {
  if ((name as string) === 'history') loadHistory()
  if ((name as string) === 'tuning') loadTuning()
}

// ==================== 文档管理 ====================
const docReindexLoading = ref<Record<string, boolean>>({})
const docReindexId = ref('')
const docSearch = ref('')
const docPage = ref(1)
const docPageSize = ref(5)
const docList = ref<{ id:string; title:string; status:string; chunks:number; health:string; updatedTime:string }[]>([
  { id:'01K8KB0DOC0MOCK0000000000001', title:'YuTong 平台白皮书', status:'ACTIVE', chunks: 42, health:'healthy', updatedTime:'2026-08-28 16:30' },
  { id:'01K8KB0DOC0MOCK0000000000002', title:'AI 能力设计 13 号文档', status:'ACTIVE', chunks: 28, health:'healthy', updatedTime:'2026-08-27 10:12' },
  { id:'01K8KB0DOC0MOCK0000000000003', title:'库存出入库 SOP', status:'INDEXING', chunks: 12, health:'degraded', updatedTime:'2026-08-29 09:00' },
])
const filteredDocs = computed(()=>{
  const kw = docSearch.value.trim().toLowerCase()
  if(!kw) return docList.value
  return docList.value.filter(d=> d.title.toLowerCase().includes(kw) || d.id.toLowerCase().includes(kw))
})
const pagedDocs = computed(()=>{
  const start=(docPage.value-1)*docPageSize.value
  return filteredDocs.value.slice(start, start+docPageSize.value)
})

async function handleReindex() {
  if (!docReindexId.value.trim()) {
    ElMessage.warning('请输入文档 ID')
    return
  }
  await ElMessageBox.confirm('确认重新索引该文档? 这将重新生成分块和向量', '确认', { type: 'warning' })
  docReindexLoading.value[docReindexId.value] = true
  try {
    await reindexKbDocument(docReindexId.value)
    ElMessage.success('重新索引请求已提交')
  } catch (e) {
    console.error('reindex failed', e)
    ElMessage.error('重新索引失败')
  } finally {
    docReindexLoading.value[docReindexId.value] = false
  }
}

// ==================== 通用 ====================
onMounted(() => {
  loadStats()
})
</script>

<template>
  <div class="kb-ops-page">
    <div class="page-header">
      <h2><el-icon><Reading /></el-icon> {{ $t('knowledge.ops.title') }}</h2>
      <div class="header-actions">
        <span class="kb-label">{{ $t('knowledge.ops.currentKb') }}</span>
        <el-input v-model="currentKbId" :placeholder="$t('knowledge.ops.kbIdPlaceholder')" style="width: 360px" size="small" />
        <el-button type="primary" size="small" :icon="Refresh" @click="loadStats">{{ $t('knowledge.ops.refreshStats') }}</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="page-tabs" @tab-change="onTabChange">
      <!-- Tab 1: 运营监控 -->
      <el-tab-pane :label="$t('knowledge.ops.tabStats')" name="stats">
        <div v-loading="statsLoading" class="stats-grid">
          <el-card class="stat-card stat-card--primary">
            <div class="stat-icon"><el-icon><Document /></el-icon></div>
            <div class="stat-title">{{ $t('knowledge.ops.docTotal') }}</div>
            <div class="stat-value">{{ stats.documentCount ?? 0 }}</div>
            <div class="stat-sub">{{ $t('knowledge.ops.activeStatus') }}</div>
          </el-card>
          <el-card class="stat-card stat-card--success">
            <div class="stat-icon"><el-icon><DataAnalysis /></el-icon></div>
            <div class="stat-title">{{ $t('knowledge.ops.chunkTotal') }}</div>
            <div class="stat-value">{{ stats.chunkCount ?? 0 }}</div>
            <div class="stat-sub">{{ $t('knowledge.ops.withEmbedding') }} {{ stats.embeddingCount ?? 0 }}</div>
          </el-card>
          <el-card class="stat-card stat-card--info">
            <div class="stat-icon"><el-icon><ChatDotRound /></el-icon></div>
            <div class="stat-title">{{ $t('knowledge.ops.todayAsk') }}</div>
            <div class="stat-value">{{ stats.todayConversationCount ?? 0 }}</div>
            <div class="stat-sub">{{ $t('knowledge.ops.hit') }} {{ stats.todayHitCount ?? 0 }} / {{ $t('knowledge.ops.refused') }} {{ stats.todayRefusedCount ?? 0 }}</div>
          </el-card>
          <el-card class="stat-card stat-card--warning">
            <div class="stat-icon"><el-icon><DataAnalysis /></el-icon></div>
            <div class="stat-title">{{ $t('knowledge.ops.todayHitRate') }}</div>
            <div class="stat-value">{{ ((stats.todayHitRate ?? 0) * 100).toFixed(1) }}%</div>
            <div class="stat-sub">{{ $t('knowledge.ops.avgScore') }} {{ stats.todayAvgMaxScore ? (stats.todayAvgMaxScore * 100).toFixed(1) + '%' : '-' }}</div>
          </el-card>
          <el-card class="stat-card stat-card--danger">
            <div class="stat-icon"><el-icon><Clock /></el-icon></div>
            <div class="stat-title">{{ $t('knowledge.ops.historyTotal') }}</div>
            <div class="stat-value">{{ stats.totalConversationCount ?? 0 }}</div>
            <div class="stat-sub">{{ $t('knowledge.ops.hit') }} {{ stats.totalHitCount ?? 0 }} / {{ $t('knowledge.ops.refused') }} {{ stats.totalRefusedCount ?? 0 }}</div>
          </el-card>
        </div>
        <el-card v-if="stats.kbId || stats.kbName" class="kb-info-card">
          <template #header>{{ $t('knowledge.ops.kbInfo') }}</template>
          <el-descriptions :column="3" border>
            <el-descriptions-item :label="$t('knowledge.ops.kbId')">{{ stats.kbId }}</el-descriptions-item>
            <el-descriptions-item :label="$t('knowledge.ops.name')">{{ stats.kbName }}</el-descriptions-item>
            <el-descriptions-item :label="$t('knowledge.ops.status')">
              <el-tag :type="stats.kbStatus === 'ACTIVE' ? 'success' : 'info'">{{ stats.kbStatus }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('knowledge.ops.todayAvgLatency')">{{ stats.todayAvgLatencyMs ? stats.todayAvgLatencyMs + ' ms' : '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('knowledge.ops.docCount')">{{ stats.documentCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item :label="$t('knowledge.ops.chunkCount')">{{ stats.chunkCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <div v-else class="yt-empty">
          <el-icon :size="48" style="color: var(--yt-color-primary-light-5)"><FolderOpened /></el-icon>
          <div class="yt-empty__text">暂无知识库统计，请检查 KB ID 或先导入文档</div>
          <el-button size="small" type="primary" plain @click="loadStats">重试加载</el-button>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 问答测试 -->
      <el-tab-pane :label="$t('knowledge.ops.tabAsk')" name="ask">
        <div class="ask-container">
          <div class="ask-input-area">
            <el-input
              v-model="question"
              type="textarea"
              :rows="3"
              :placeholder="$t('knowledge.ops.questionPlaceholder')"
              maxlength="4000"
              show-word-limit
            />
            <div class="ask-actions">
              <el-button type="primary" :icon="VideoPlay" :loading="askLoading" @click="handleAsk">
                {{ askLoading ? $t('knowledge.ops.asking') : $t('knowledge.ops.sendQuestion') }}
              </el-button>
              <el-button :icon="Refresh" @click="question = ''; askResult = null; clearStreaming()">{{ $t('knowledge.ops.clear') }}</el-button>
            </div>
          </div>

          <!-- 流式增量展示 -->
          <div v-if="streamingActive" class="yt-streaming-delta">
            <div class="yt-streaming-delta__label"><el-icon><ChatDotRound /></el-icon> 流式增量预览</div>
            <StreamingMessage role="assistant" :content="streamingText" :streaming="true" :citations="[]" />
          </div>

          <div v-if="askResult" class="ask-result-area">
            <el-divider content-position="left">{{ $t('knowledge.ops.askResult') }}</el-divider>
            <el-alert
              v-if="askResult.refused"
              :title="$t('knowledge.ops.refusedPrefix') + refuseReasonText(askResult.refuseReason)"
              type="warning"
              :description="askResult.answer"
              show-icon
              :closable="false"
              class="refused-alert"
            />
            <el-alert
              v-else
              :title="$t('knowledge.ops.successTitle', { count: askResult.hitChunkCount, score: (askResult.maxScore * 100).toFixed(1), latency: askResult.latencyMs })"
              type="success"
              show-icon
              :closable="false"
              class="success-alert"
            />
            <!-- 复用 StreamingMessage 展示最终答案 -->
            <StreamingMessage
              v-if="!askResult.refused"
              role="assistant"
              :content="streamingActive ? streamingText : askResult.answer"
              :streaming="streamingActive"
              :citations="(askResult.citations||[]).map(c=>({ title: c.docTitle, docTitle: c.docTitle, score: c.score, chunkTextPreview: c.chunkTextPreview, sectionPath: c.sectionPath }))"
              :latencyMs="askResult.latencyMs"
            />
            <div v-else class="answer-area">
              <div class="answer-label">{{ $t('knowledge.ops.systemAnswer') }}</div>
              <div class="answer-text">{{ askResult.answer }}</div>
            </div>
            <div v-if="askResult.citations && askResult.citations.length > 0" class="citations-area">
              <div class="citations-label">
                <el-icon><Document /></el-icon> {{ $t('knowledge.ops.citations') }} ({{ askResult.citations.length }})
              </div>
              <div class="citations-list">
                <el-card
                  v-for="(citation, idx) in askResult.citations"
                  :key="idx"
                  class="citation-card"
                  shadow="hover"
                >
                  <div class="citation-header">
                    <span class="citation-index">[{{ idx + 1 }}]</span>
                    <span class="citation-title">{{ citation.docTitle }}</span>
                    <el-tag :type="scoreTagType(citation.score)" size="small">
                      {{ (citation.score * 100).toFixed(1) }}%
                    </el-tag>
                  </div>
                  <div class="citation-section">{{ citation.sectionPath || $t('knowledge.ops.noSection') }}</div>
                  <div class="citation-preview">{{ citation.chunkTextPreview || $t('knowledge.ops.noPreview') }}</div>
                </el-card>
              </div>
            </div>
            <div class="ask-meta">
              <el-tag size="small">{{ $t('knowledge.ops.conversationNoLabel') }} {{ askResult.conversationNo }}</el-tag>
              <el-tag size="small" type="info">{{ $t('knowledge.ops.hitLabel') }} {{ askResult.hitChunkCount }}</el-tag>
              <el-tag size="small" type="info">{{ $t('knowledge.ops.maxScoreLabel') }} {{ (askResult.maxScore * 100).toFixed(1) }}%</el-tag>
              <el-tag size="small" type="info">{{ $t('knowledge.ops.minScoreLabel') }} {{ (askResult.minScore * 100).toFixed(1) }}%</el-tag>
              <el-tag size="small" type="info">{{ $t('knowledge.ops.avgScoreLabel') }} {{ (askResult.avgScore * 100).toFixed(1) }}%</el-tag>
              <el-tag size="small" type="info">{{ $t('knowledge.ops.latencyLabel') }} {{ askResult.latencyMs }}ms</el-tag>
            </div>
          </div>
          <div v-else class="yt-empty">
            <el-icon :size="48" style="color: var(--yt-color-primary-light-5)"><ChatDotRound /></el-icon>
            <div class="yt-empty__text">输入问题后，此处将展示流式答案、引用与命中分数</div>
            <div class="yt-empty__sub">支持实时增量预览与引用晶片</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 3: 问答历史 -->
      <el-tab-pane :label="$t('knowledge.ops.tabHistory')" name="history">
        <div class="history-filter">
          <el-input v-model="historyFilter.kbId" :placeholder="$t('knowledge.ops.kbIdDefaultPlaceholder')" size="small" style="width: 280px" clearable />
          <el-input v-model="historyFilter.userId" :placeholder="$t('knowledge.ops.userIdPlaceholder')" size="small" style="width: 200px" clearable />
          <el-select v-model="historyFilter.refused" :placeholder="$t('knowledge.ops.refusedStatusPlaceholder')" size="small" style="width: 140px" clearable>
            <el-option :label="$t('knowledge.ops.all')" value="" />
            <el-option :label="$t('knowledge.ops.answered')" value="false" />
            <el-option :label="$t('knowledge.ops.refusedOption')" value="true" />
          </el-select>
          <el-button type="primary" size="small" :icon="Refresh" @click="historyPageNo = 1; loadHistory()">{{ $t('knowledge.ops.query') }}</el-button>
        </div>
        <el-table v-if="historyList.length" :data="historyList" v-loading="historyLoading" border stripe>
          <el-table-column prop="conversationNo" :label="$t('knowledge.ops.conversationNoCol')" width="220" />
          <el-table-column prop="question" :label="$t('knowledge.ops.questionCol')" min-width="240" show-overflow-tooltip />
          <el-table-column prop="hitChunkCount" :label="$t('knowledge.ops.hitCountCol')" width="80" align="center" />
          <el-table-column :label="$t('knowledge.ops.maxScoreCol')" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.maxScore" :type="scoreTagType(row.maxScore as number)" size="small">
                {{ (row.maxScore * 100).toFixed(1) }}%
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('knowledge.ops.statusCol')" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="historyRefusedTagType(row.isRefused)" size="small">
                {{ row.isRefused ? $t('knowledge.ops.refusedOption') : $t('knowledge.ops.answered') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="latencyMs" :label="$t('knowledge.ops.latencyCol')" width="100" align="center" />
          <el-table-column prop="createdTime" :label="$t('knowledge.ops.askTimeCol')" width="180" />
          <el-table-column :label="$t('knowledge.ops.actionCol')" width="100" align="center" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :icon="Document" @click="viewDetail(row as KbConversationLog)">{{ $t('knowledge.ops.detail') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-else class="yt-empty">
          <el-icon :size="48" style="color: var(--yt-color-primary-light-5)"><FolderOpened /></el-icon>
          <div class="yt-empty__text">暂无问答历史</div>
          <div class="yt-empty__sub">发起一次问答后，历史将出现在这里</div>
          <el-button size="small" type="primary" plain :icon="Refresh" @click="loadHistory">刷新</el-button>
        </div>
        <div v-if="historyList.length" class="history-pagination">
          <el-pagination
            v-model:current-page="historyPageNo"
            v-model:page-size="historyPageSize"
            :total="historyTotal"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadHistory"
            @size-change="loadHistory"
          />
        </div>

        <!-- 详情弹窗 -->
        <el-dialog v-model="detailDialogVisible" :title="$t('knowledge.ops.detailTitle')" width="800px" destroy-on-close>
          <div v-loading="detailLoading">
            <el-descriptions v-if="detailData" :column="2" border>
              <el-descriptions-item :label="$t('knowledge.ops.conversationNoCol')">{{ detailData.conversationNo }}</el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.status')">
                <el-tag :type="historyRefusedTagType(detailData.isRefused)" size="small">
                  {{ detailData.isRefused ? $t('knowledge.ops.refusedOption') : $t('knowledge.ops.answered') }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.askUser')">{{ detailData.userId || '-' }}</el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.latency')">{{ detailData.latencyMs || 0 }} ms</el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.hitChunkCount')">{{ detailData.hitChunkCount || 0 }}</el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.maxScoreFull')">
                <span v-if="detailData.maxScore">{{ (detailData.maxScore * 100).toFixed(1) }}%</span>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.refuseReason')" v-if="detailData.isRefused">
                {{ refuseReasonText(detailData.refuseReason) }}
              </el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.askTime')">{{ detailData.createdTime }}</el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.question')" :span="2">
                <div class="detail-question">{{ detailData.question }}</div>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.answer')" :span="2">
                <div class="detail-answer">{{ detailData.answer }}</div>
              </el-descriptions-item>
              <el-descriptions-item :label="$t('knowledge.ops.citedDocsJson')" :span="2">
                <pre class="detail-json">{{ detailData.citedDocuments }}</pre>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-dialog>
      </el-tab-pane>

      <!-- Tab 5: 知识图谱 -->
      <el-tab-pane label="知识图谱" name="graph">
        <KnowledgeGraphTab :kb-id="currentKbId" />
      </el-tab-pane>

      <!-- Tab 6: 检索调参 (V050 P2-E 混合检索可配) -->
      <el-tab-pane :label="$t('knowledge.tuning.tab')" name="tuning">
        <el-card v-loading="tuningLoading" shadow="never">
          <template #header>
            <span>{{ $t('knowledge.tuning.title') }}</span>
          </template>
          <el-alert
            :title="$t('knowledge.tuning.hint')"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 16px"
          />
          <el-form label-width="140px" :model="tuningForm" style="max-width: 640px">
            <el-form-item :label="$t('knowledge.tuning.field.hybridEnabled')">
              <el-switch v-model="tuningForm.hybridEnabled" :aria-label="$t('knowledge.tuning.field.hybridEnabled')" />
            </el-form-item>
            <el-form-item :label="$t('knowledge.tuning.field.vectorWeight')">
              <el-slider
                v-model="tuningForm.hybridVectorWeight"
                :min="0"
                :max="1"
                :step="0.05"
                show-input
                :show-input-controls="false"
                style="width: 100%"
              />
              <div class="tuning-hint">{{ $t('knowledge.tuning.hintWeight', { text: (1 - tuningForm.hybridVectorWeight).toFixed(2) }) }}</div>
            </el-form-item>
            <el-form-item :label="$t('knowledge.tuning.field.topK')">
              <el-input-number v-model="tuningForm.hybridTopK" :min="1" :max="50" style="width: 100%" />
            </el-form-item>
            <el-form-item :label="$t('knowledge.tuning.field.minScore')">
              <el-input-number v-model="tuningForm.hybridMinScore" :min="0" :max="1" :step="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
            <el-form-item>
              <el-button v-permission="'ai:knowledge-base:edit'" type="primary" :loading="tuningSaving" @click="saveTuning">
                {{ $t('common.action.confirm') }}
              </el-button>
              <el-button @click="loadTuning">{{ $t('common.action.reset') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- Tab 7: 多模态 Embedding 测试 (platform-remaining-parity S2.3) -->
      <el-tab-pane label="Embedding 测试" name="embedding">
        <EmbeddingTestPanel :kb-id="currentKbId" />
      </el-tab-pane>

      <!-- Tab 4: 文档管理 -->
      <el-tab-pane :label="$t('knowledge.ops.tabDocs')" name="docs">
        <el-card>
          <template #header>
            <span><el-icon><Document /></el-icon> {{ $t('knowledge.ops.reindexDoc') }}</span>
          </template>
          <el-alert
            :title="$t('knowledge.ops.reindexAlertTitle')"
            type="info"
            :description="$t('knowledge.ops.reindexAlertDesc')"
            show-icon
            :closable="false"
            class="reindex-info"
          />
          <div class="reindex-form">
            <el-input v-model="docReindexId" :placeholder="$t('knowledge.ops.docIdPlaceholder')" style="width: 480px" />
            <el-button
              type="primary"
              :icon="Refresh"
              :loading="docReindexLoading[docReindexId] || false"
              @click="handleReindex"
            >
              {{ $t('knowledge.ops.reindex') }}
            </el-button>
          </div>
          <div class="docs-hint">
            <p>{{ $t('knowledge.ops.seedDocTitle') }}</p>
            <ul>
              <li><code>01K8KB0DOC0MOCK0000000000001</code> - {{ $t('knowledge.ops.seedDoc1') }}</li>
              <li><code>01K8KB0DOC0MOCK0000000000002</code> - {{ $t('knowledge.ops.seedDoc2') }}</li>
            </ul>
          </div>
        </el-card>

        <!-- 新增：文档列表 · 搜索/健康态/分页/空态 -->
        <el-card style="margin-top:12px" shadow="never">
          <template #header>
            <div style="display:flex; justify-content:space-between; align-items:center">
              <span style="font-weight:600"><el-icon><FolderOpened /></el-icon> 文档列表</span>
              <span style="font-size:12px; color: var(--yt-text-secondary)">共 {{ filteredDocs.length }} 篇 · 健康态分色</span>
            </div>
          </template>
          <div style="display:flex; gap:8px; margin-bottom:12px">
            <el-input v-model="docSearch" placeholder="搜索标题 / 文档 ID" clearable style="max-width:320px" size="small" @input="docPage=1" />
            <span style="font-size:12px; color: var(--yt-text-secondary); align-self:center">已筛选 {{ filteredDocs.length }} / {{ docList.length }}</span>
          </div>
          <el-table v-if="pagedDocs.length" :data="pagedDocs" size="small" border stripe>
            <el-table-column prop="id" label="文档 ID" width="300">
              <template #default="{row}"><span style="font-family: ui-monospace, monospace; font-size:11px">{{ row.id }}</span></template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="状态" width="110" align="center">
              <template #default="{row}">
                <el-tag :type="row.status==='ACTIVE'?'success': row.status==='INDEXING'?'warning':'info'" size="small" effect="plain">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunks" label="分块" width="80" align="center" />
            <el-table-column label="健康" width="100" align="center">
              <template #default="{row}">
                <el-tag :type="row.health==='healthy'?'success': row.health==='degraded'?'warning':'danger'" size="small" effect="plain">{{ row.health }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedTime" label="更新时间" width="160" />
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{row}">
                <el-button size="small" link :icon="Refresh" @click="docReindexId=row.id; handleReindex()">重索引</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="yt-empty" style="margin-top:0">
            <el-icon :size="36" style="color: var(--yt-text-disabled)"><Document /></el-icon>
            <div class="yt-empty__text">暂无匹配的文档</div>
            <div class="yt-empty__sub">尝试调整关键字或先导入新文档</div>
            <el-button size="small" style="margin-top:8px" @click="docSearch=''">清除搜索</el-button>
          </div>
          <div v-if="filteredDocs.length > docPageSize" style="display:flex; justify-content:flex-end; margin-top:12px">
            <el-pagination
              v-model:current-page="docPage"
              v-model:page-size="docPageSize"
              :total="filteredDocs.length"
              :page-sizes="[5,10,20]"
              layout="total, sizes, prev, pager, next"
              size="small"
              @current-change="()=>{}"
              @size-change="()=>{ docPage=1 }"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.tuning-hint {
  font-size: 12px;
  color: var(--yt-text-secondary);
  margin-top: 4px;
}
.kb-ops-page {
  padding: var(--yt-space-md);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--yt-space-md);
}
.page-header h2 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--yt-space-sm);
  font-size: var(--yt-font-size-title);
  line-height: var(--yt-font-line-height-title);
  color: var(--yt-text-primary);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--yt-space-sm);
}
.kb-label {
  font-size: 13px;
  color: var(--yt-text-secondary);
}
.page-tabs {
  margin-top: var(--yt-space-sm);
}

/* 运营监控 — 5 卡 yt-color 点缀 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--yt-space-md);
  margin-bottom: var(--yt-space-md);
}
.stat-card {
  text-align: center;
  position: relative;
  overflow: hidden;
  border: 1px solid var(--yt-border-light);
  border-radius: var(--yt-radius-md);
  transition: transform var(--yt-transition-hover), box-shadow var(--yt-transition-hover);
}
.stat-card:hover { transform: translateY(-2px); box-shadow: var(--yt-shadow-popover); }
.stat-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
}
.stat-card--primary::before { background: var(--yt-color-primary); }
.stat-card--success::before { background: var(--yt-color-success); }
.stat-card--info::before { background: var(--yt-color-info); }
.stat-card--warning::before { background: var(--yt-color-warning); }
.stat-card--danger::before { background: var(--yt-color-danger); }
.stat-card--primary .stat-icon { color: var(--yt-color-primary); }
.stat-card--success .stat-icon { color: var(--yt-color-success); }
.stat-card--info .stat-icon { color: var(--yt-color-info); }
.stat-card--warning .stat-icon { color: var(--yt-color-warning); }
.stat-card--danger .stat-icon { color: var(--yt-color-danger); }
.stat-icon { font-size: 22px; margin-bottom: var(--yt-space-xs); }
.stat-title {
  font-size: 13px;
  color: var(--yt-text-secondary);
  margin-bottom: var(--yt-space-sm);
}
.stat-value {
  font-size: 28px;
  font-weight: var(--yt-font-weight-bold);
  color: var(--yt-text-primary);
  margin-bottom: var(--yt-space-xs);
}
.stat-sub {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
}
.kb-info-card {
  margin-top: var(--yt-space-md);
  border: 1px solid var(--yt-border-light);
}

/* 问答测试 */
.ask-container {
  max-width: 900px;
}
.ask-input-area {
  margin-bottom: var(--yt-space-md);
}
.ask-actions {
  margin-top: var(--yt-space-md);
  display: flex;
  gap: var(--yt-space-sm);
}
.yt-streaming-delta {
  margin-bottom: var(--yt-space-md);
  padding: var(--yt-space-md);
  border: 1px solid var(--yt-color-primary-light-7);
  background: var(--yt-color-primary-light-9);
  border-radius: var(--yt-radius-md);
}
.yt-streaming-delta__label {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-color-primary-dark-2);
  font-weight: var(--yt-font-weight-bold);
  display:flex; align-items:center; gap: var(--yt-space-xs);
  margin-bottom: var(--yt-space-sm);
}
.ask-result-area {
  margin-top: var(--yt-space-md);
}
.refused-alert,
.success-alert {
  margin-bottom: var(--yt-space-md);
}
.answer-area {
  background: var(--yt-bg-page);
  padding: var(--yt-space-md);
  border-radius: var(--yt-radius-sm);
  margin-bottom: var(--yt-space-md);
  border: 1px solid var(--yt-border-light);
}
.answer-label {
  font-weight: var(--yt-font-weight-bold);
  margin-bottom: var(--yt-space-sm);
  color: var(--yt-text-primary);
}
.answer-text {
  white-space: pre-wrap;
  line-height: 1.8;
  color: var(--yt-text-regular);
}
.citations-area {
  margin-bottom: var(--yt-space-md);
}
.citations-label {
  font-weight: var(--yt-font-weight-bold);
  margin-bottom: var(--yt-space-md);
  display: flex;
  align-items: center;
  gap: var(--yt-space-xs);
  color: var(--yt-text-primary);
}
.citations-list {
  display: flex;
  flex-direction: column;
  gap: var(--yt-space-sm);
}
.citation-card {
  background: var(--yt-bg-card);
  border: 1px solid var(--yt-border-light);
}
.citation-header {
  display: flex;
  align-items: center;
  gap: var(--yt-space-sm);
  margin-bottom: var(--yt-space-xs);
}
.citation-index {
  font-weight: var(--yt-font-weight-bold);
  color: var(--yt-color-primary);
}
.citation-title {
  flex: 1;
  font-weight: 500;
  color: var(--yt-text-primary);
}
.citation-section {
  font-size: var(--yt-font-size-caption);
  color: var(--yt-text-secondary);
  margin-bottom: var(--yt-space-xs);
}
.citation-preview {
  font-size: 13px;
  color: var(--yt-text-regular);
  line-height: 1.6;
}
.ask-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yt-space-sm);
  margin-top: var(--yt-space-md);
}

/* 问答历史 */
.history-filter {
  display: flex;
  gap: var(--yt-space-sm);
  margin-bottom: var(--yt-space-md);
  flex-wrap: wrap;
}
.history-pagination {
  margin-top: var(--yt-space-md);
  display: flex;
  justify-content: flex-end;
}
.detail-question,
.detail-answer {
  white-space: pre-wrap;
  line-height: 1.6;
  padding: var(--yt-space-sm);
  background: var(--yt-bg-page);
  border-radius: var(--yt-radius-sm);
  border: 1px solid var(--yt-border-light);
}
.detail-json {
  max-height: 240px;
  overflow: auto;
  background: var(--yt-bg-page);
  padding: var(--yt-space-sm);
  border-radius: var(--yt-radius-sm);
  font-size: var(--yt-font-size-caption);
  border: 1px solid var(--yt-border-light);
}

/* 文档管理 */
.reindex-info {
  margin-bottom: var(--yt-space-md);
}
.reindex-form {
  display: flex;
  gap: var(--yt-space-sm);
  margin-bottom: var(--yt-space-md);
}
.docs-hint {
  background: var(--yt-bg-page);
  padding: var(--yt-space-md);
  border-radius: var(--yt-radius-sm);
  font-size: 13px;
  border: 1px solid var(--yt-border-light);
  color: var(--yt-text-regular);
}
.docs-hint code {
  background: var(--yt-border-light);
  padding: 2px 6px;
  border-radius: var(--yt-radius-sm);
  font-family: monospace;
  color: var(--yt-text-primary);
}
.docs-hint ul {
  margin: 8px 0 0 0;
  padding-left: 20px;
}
.docs-hint li {
  margin-bottom: 4px;
}

/* 通用空状态 — 4 tab 复用，yt 令牌 */
.yt-empty {
  display:flex; flex-direction:column; align-items:center; justify-content:center;
  padding: var(--yt-space-xl) var(--yt-space-md);
  background: var(--yt-bg-card);
  border: 1px dashed var(--yt-border-default);
  border-radius: var(--yt-radius-md);
  gap: var(--yt-space-sm);
  text-align:center;
}
.yt-empty__text { font-size: var(--yt-font-size-body); color: var(--yt-text-primary); font-weight: var(--yt-font-weight-bold); }
.yt-empty__sub { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
</style>
