<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  VideoPlay,
  Reading,
  Document,
} from '@element-plus/icons-vue'
import {
  askQuestion,
  getKbStats,
  pageKbConversations,
  getKbConversation,
  reindexKbDocument,
  type AskQuestionVO,
  type KbStatsVO,
  type KbConversationLog,
  type AskQuestionRequest,
} from '@/api/knowledge'

/**
 * 知识库运营页。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 *
 * 4 个 Tab:
 *  1. 运营监控: 5 项指标统计看板 (文档数/分块数/今日问答/命中率/平均分)
 *  2. 问答测试: 输入问题 + 答案展示 + 引用来源 + 拒答提示
 *  3. 问答历史: 分页查询问答日志 + 详情弹窗
 *  4. 文档管理: 文档列表 + 重新索引
 *
 * 核心能力验证 (6 项):
 *  - 文档导入: 重新索引按钮
 *  - 分块和向量化: 文档导入后自动 (后端 ingest)
 *  - 权限过滤: 后端 RagAclService
 *  - 问答引用: 问答测试 Tab 展示 citations
 *  - 命中率统计: 运营监控 Tab todayHitRate
 *  - 低置信度拒答: 问答测试 Tab refused=true 红色提示
 */

// 种子知识库 ID (V015 迁移灌入的 kb-ops-mock)
const DEFAULT_KB_ID = '01K8KB0OPS0MOCK0000000000001'
const activeTab = ref<'stats' | 'ask' | 'history' | 'docs'>('stats')
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

// ==================== 问答测试 ====================
const question = ref('')
const askLoading = ref(false)
const askResult = ref<AskQuestionVO | null>(null)

async function handleAsk() {
  if (!question.value.trim()) {
    ElMessage.warning('请输入问题')
    return
  }
  askLoading.value = true
  askResult.value = null
  try {
    const req: AskQuestionRequest = {
      kbId: currentKbId.value,
      question: question.value.trim(),
    }
    askResult.value = await askQuestion(req)
    if (askResult.value.refused) {
      ElMessage.warning('已拒答: ' + (askResult.value.refuseReason || '未知原因'))
    } else {
      ElMessage.success('问答成功, 命中 ' + askResult.value.hitChunkCount + ' 个分块')
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

// ==================== 文档管理 ====================
const docReindexLoading = ref<Record<string, boolean>>({})
const docReindexId = ref('')

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

    <el-tabs v-model="activeTab" class="page-tabs">
      <!-- Tab 1: 运营监控 -->
      <el-tab-pane :label="$t('knowledge.ops.tabStats')" name="stats">
        <div v-loading="statsLoading" class="stats-grid">
          <el-card class="stat-card">
            <div class="stat-title">{{ $t('knowledge.ops.docTotal') }}</div>
            <div class="stat-value">{{ stats.documentCount ?? 0 }}</div>
            <div class="stat-sub">{{ $t('knowledge.ops.activeStatus') }}</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-title">{{ $t('knowledge.ops.chunkTotal') }}</div>
            <div class="stat-value">{{ stats.chunkCount ?? 0 }}</div>
            <div class="stat-sub">{{ $t('knowledge.ops.withEmbedding') }} {{ stats.embeddingCount ?? 0 }}</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-title">{{ $t('knowledge.ops.todayAsk') }}</div>
            <div class="stat-value">{{ stats.todayConversationCount ?? 0 }}</div>
            <div class="stat-sub">{{ $t('knowledge.ops.hit') }} {{ stats.todayHitCount ?? 0 }} / {{ $t('knowledge.ops.refused') }} {{ stats.todayRefusedCount ?? 0 }}</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-title">{{ $t('knowledge.ops.todayHitRate') }}</div>
            <div class="stat-value">{{ ((stats.todayHitRate ?? 0) * 100).toFixed(1) }}%</div>
            <div class="stat-sub">{{ $t('knowledge.ops.avgScore') }} {{ stats.todayAvgMaxScore ? (stats.todayAvgMaxScore * 100).toFixed(1) + '%' : '-' }}</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-title">{{ $t('knowledge.ops.historyTotal') }}</div>
            <div class="stat-value">{{ stats.totalConversationCount ?? 0 }}</div>
            <div class="stat-sub">{{ $t('knowledge.ops.hit') }} {{ stats.totalHitCount ?? 0 }} / {{ $t('knowledge.ops.refused') }} {{ stats.totalRefusedCount ?? 0 }}</div>
          </el-card>
        </div>
        <el-card class="kb-info-card">
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
              <el-button :icon="Refresh" @click="question = ''; askResult = null">{{ $t('knowledge.ops.clear') }}</el-button>
            </div>
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
            <div class="answer-area">
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
        <el-table :data="historyList" v-loading="historyLoading" border stripe>
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
        <div class="history-pagination">
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
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.kb-ops-page {
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kb-label {
  font-size: 13px;
  color: #606266;
}
.page-tabs {
  margin-top: 8px;
}

/* 运营监控 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
}
.stat-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}
.stat-sub {
  font-size: 12px;
  color: #909399;
}
.kb-info-card {
  margin-top: 16px;
}

/* 问答测试 */
.ask-container {
  max-width: 900px;
}
.ask-input-area {
  margin-bottom: 16px;
}
.ask-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
.ask-result-area {
  margin-top: 16px;
}
.refused-alert,
.success-alert {
  margin-bottom: 16px;
}
.answer-area {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  margin-bottom: 16px;
}
.answer-label {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}
.answer-text {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #606266;
}
.citations-area {
  margin-bottom: 16px;
}
.citations-label {
  font-weight: 600;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #303133;
}
.citations-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.citation-card {
  background: #fafafa;
}
.citation-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.citation-index {
  font-weight: 600;
  color: #409eff;
}
.citation-title {
  flex: 1;
  font-weight: 500;
  color: #303133;
}
.citation-section {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.citation-preview {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.ask-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

/* 问答历史 */
.history-filter {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.history-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.detail-question,
.detail-answer {
  white-space: pre-wrap;
  line-height: 1.6;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}
.detail-json {
  max-height: 240px;
  overflow: auto;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
}

/* 文档管理 */
.reindex-info {
  margin-bottom: 16px;
}
.reindex-form {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.docs-hint {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
}
.docs-hint code {
  background: #e4e7ed;
  padding: 2px 6px;
  border-radius: 2px;
  font-family: 'Courier New', monospace;
}
.docs-hint ul {
  margin: 8px 0 0 0;
  padding-left: 20px;
}
.docs-hint li {
  margin-bottom: 4px;
}
</style>
