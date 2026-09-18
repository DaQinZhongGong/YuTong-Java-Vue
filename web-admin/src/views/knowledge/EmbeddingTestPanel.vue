<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import {
  EMBEDDING_MODALITY_OPTIONS,
  testMultimodalEmbedding,
  type EmbeddingModality,
  type MultimodalEmbeddingResult,
} from '@/api/embeddingTest'

/**
 * 多模态 Embedding 测试面板。设计来源: ai-depth-parity.md S2.3。
 * 可嵌入知识库运营页 Tab, 调用 POST /ai/rag/embeddings/multimodal。
 * 无 Key 时后端失败关闭 — 前端如实展示错误, 不伪造向量。
 */

const props = defineProps<{
  /** 当前知识库 ID, 可选 */
  kbId?: string
}>()

const modality = ref<EmbeddingModality>('text')
const payload = ref('')
const knowledgeBaseId = ref('')
const loading = ref(false)
const result = ref<MultimodalEmbeddingResult | null>(null)

const payloadPlaceholder = () => {
  if (modality.value === 'image') {
    return '图片 URL 或 data:image/png;base64,...'
  }
  if (modality.value === 'video') {
    return '视频 URL'
  }
  return '输入要向量化的文本…'
}

const previewVector = () => {
  if (!result.value) return ''
  const v = result.value.preview || result.value.vector || result.value.vectorPreview || []
  if (!v.length) return ''
  const head = v.slice(0, 12).map((n) => (typeof n === 'number' ? n.toFixed(4) : String(n)))
  return `[${head.join(', ')}${v.length > 12 ? ', …' : ''}]`
}

async function runTest() {
  if (!payload.value.trim()) {
    ElMessage.warning('请输入 payload')
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await testMultimodalEmbedding({
      modality: modality.value,
      payload: payload.value.trim(),
      knowledgeBaseId: knowledgeBaseId.value.trim() || props.kbId || undefined,
    })
    if (result.value && result.value.remoteSuccess === false) {
      ElMessage.warning(result.value.message || '多模态 Embedding 未远程成功（失败关闭/回退）')
    } else {
      ElMessage.success(`Embedding 成功 · 维度 ${result.value?.dimension ?? '-'}`)
    }
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || 'Embedding 测试失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  payload.value = ''
  result.value = null
}
</script>

<template>
  <div class="embedding-test-panel">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="多模态 Embedding 测试"
      description="POST /ai/rag/embeddings/multimodal · 支持 text / image / video。无 Provider Key 时后端失败关闭。"
      class="mb-12"
    />

    <el-form label-width="110px" @submit.prevent>
      <el-form-item label="模态">
        <el-radio-group v-model="modality" @change="reset">
          <el-radio-button
            v-for="opt in EMBEDDING_MODALITY_OPTIONS"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Payload" required>
        <el-input
          v-model="payload"
          type="textarea"
          :rows="modality === 'text' ? 4 : 3"
          :placeholder="payloadPlaceholder()"
        />
      </el-form-item>
      <el-form-item label="知识库 ID">
        <el-input
          v-model="knowledgeBaseId"
          :placeholder="props.kbId ? `默认: ${props.kbId}` : '可选，用于读取维度/模型配置'"
          clearable
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="MagicStick" :loading="loading" @click="runTest">
          执行测试
        </el-button>
        <el-button @click="reset">清空</el-button>
      </el-form-item>
    </el-form>

    <el-card v-if="result" shadow="never" class="result-card">
      <template #header>测试结果</template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="维度">{{ result.dimension ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="远程成功">
          <el-tag size="small" :type="result.remoteSuccess ? 'success' : 'warning'">
            {{ result.remoteSuccess ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ result.message || result.errorMessage || '—' }}</el-descriptions-item>
        <el-descriptions-item label="向量预览" :span="2">
          <code class="vector-preview">{{ previewVector() || '—' }}</code>
        </el-descriptions-item>
        <el-descriptions-item v-if="result.errorMessage" label="错误" :span="2">
          <span class="error-text">{{ result.errorMessage }}</span>
        </el-descriptions-item>
        <el-descriptions-item v-if="result.traceId" label="traceId" :span="2">
          <code>{{ result.traceId }}</code>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.embedding-test-panel {
  max-width: 860px;
}
.mb-12 {
  margin-bottom: 12px;
}
.result-card {
  margin-top: 4px;
}
.vector-preview {
  display: block;
  font-size: 12px;
  word-break: break-all;
  white-space: pre-wrap;
  background: var(--el-fill-color-light, #f5f7fa);
  padding: 6px 8px;
  border-radius: 4px;
}
.error-text {
  color: var(--el-color-danger);
  font-size: 12px;
}
</style>
