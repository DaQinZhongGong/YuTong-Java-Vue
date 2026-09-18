<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ackCopilotOutbox, approveCopilotRun, generateCopilotDraft, listCopilotOutbox, pageCopilotRuns, rejectCopilotRun, type CopilotDraft, type CopilotRun } from '@/api/copilot'
import { saveEntityDraft } from '@/api/lowcode'
import type { SaveLcEntityRequest } from '@/api/types'

const prompt = ref('')
const targetType = ref('form')
const loading = ref(false)
const draft = ref<CopilotDraft | null>(null)
const runs = ref<CopilotRun[]>([])
const runsLoading = ref(false)
const outbox = ref<CopilotRun[]>([])

async function loadRuns() {
  runsLoading.value = true
  try {
    const data = await pageCopilotRuns({ page: 1, size: 20 })
    runs.value = data.records || []
    outbox.value = await listCopilotOutbox()
  } catch (e: unknown) {
    runs.value = []
    outbox.value = []
    ElMessage.error((e as { message?: string })?.message || '加载 Copilot 记录失败')
  } finally {
    runsLoading.value = false
  }
}

async function generate() {
  if (!prompt.value.trim()) {
    ElMessage.warning('请输入需求描述')
    return
  }
  loading.value = true
  try {
    draft.value = await generateCopilotDraft({ prompt: prompt.value.trim(), targetType: targetType.value })
    ElMessage.success(draft.value.status === 'DRAFT' ? '草稿已进入待审' : '已生成 LLM 草稿')
    await loadRuns()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '生成失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadRuns)

/**
 * P2-J Outbox 落库: approved 草稿 → 低代码实体草稿 (前端驱动, 零后端耦合)。
 * outputJson 结构 {targetType, code, name, fields[{fieldCode,fieldName,component,required}]}。
 * component→dataType: input/textarea→STRING, number→DECIMAL, date→DATE,
 * select→STRING (无字典类型信息, 落库后可在实体管理中改为 DICT 绑定)。
 */

interface MaterializeField {
  fieldCode: string
  fieldName: string
  component: string
  required: boolean
}

interface MaterializePayload {
  code: string
  name: string
  fields: MaterializeField[]
}

function toSnake(s: string): string {
  return s
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[\s-]+/g, '_')
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, '')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
}

function componentToDataType(component: string): string {
  switch ((component || '').toLowerCase()) {
    case 'number': return 'DECIMAL'
    case 'date': return 'DATE'
    default: return 'STRING'
  }
}

function parseMaterializePayload(run: CopilotRun): MaterializePayload | null {
  if (!run.outputJson) return null
  try {
    const o = JSON.parse(run.outputJson) as {
      code?: string
      name?: string
      fields?: MaterializeField[]
    }
    if (!o || !o.code || !Array.isArray(o.fields) || o.fields.length === 0) return null
    // 蛇形化后为空的编码 (如纯中文) 直接丢弃, 避免落库空 fieldCode/dbColumn 脏数据
    const fields = o.fields.filter(
      (f) => f && typeof f.fieldCode === 'string' && toSnake(f.fieldCode),
    )
    if (fields.length === 0) return null
    return { code: o.code.trim(), name: (o.name || o.code).trim(), fields }
  } catch {
    return null
  }
}

const matVisible = ref(false)
const matRunId = ref('')
const matSaving = ref(false)
const matForm = ref({ entityCode: '', entityName: '', tableName: '', moduleCode: 'copilot' })
const matFields = ref<MaterializeField[]>([])

function openMaterialize(row: CopilotRun) {
  const payload = parseMaterializePayload(row)
  if (!payload) {
    ElMessage.warning('该草稿无可用字段载荷，无法落库')
    return
  }
  matRunId.value = row.id
  matFields.value = payload.fields
  const code = toSnake(payload.code) || 'draft'
  matForm.value = {
    entityCode: code,
    entityName: payload.name,
    tableName: `t_${code}`,
    moduleCode: 'copilot',
  }
  matVisible.value = true
}

async function confirmMaterialize() {
  if (!matForm.value.entityCode.trim()) {
    ElMessage.warning('实体编码不能为空')
    return
  }
  if (!matForm.value.entityName.trim()) {
    ElMessage.warning('实体名称不能为空')
    return
  }
  matSaving.value = true
  try {
    const body: SaveLcEntityRequest = {
      entityCode: matForm.value.entityCode.trim(),
      entityName: matForm.value.entityName.trim(),
      tableName: matForm.value.tableName.trim() || `t_${toSnake(matForm.value.entityCode)}`,
      moduleCode: matForm.value.moduleCode.trim() || 'copilot',
      fields: matFields.value.map((f, i) => ({
        fieldCode: f.fieldCode,
        fieldName: f.fieldName,
        dbColumn: toSnake(f.fieldCode),
        dataType: componentToDataType(f.component),
        nullable: !f.required,
        sortNo: i + 1,
      })),
      relations: [],
    }
    const entity = await saveEntityDraft(body)
    ElMessage.success(`已落库为实体草稿: ${entity.entityCode} (${entity.id})`)
    matVisible.value = false
    await ackCopilotOutbox(matRunId.value)
    await loadRuns()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '落库失败')
  } finally {
    matSaving.value = false
  }
}

async function confirmAckOnly(row: CopilotRun) {
  try {
    await ElMessageBox.confirm('仅标记为已消费，不会落库任何实体，是否继续？', '确认消费', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await ackCopilotOutbox(row.id)
    await loadRuns()
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="copilot-page">
    <div class="page-head">
      <div>
        <h2 class="head-title">Copilot 草稿</h2>
        <p class="head-sub">调用已启用 LLM 生成表单/页面字段草稿，不直接写库，需人工确认后进入低代码发布。</p>
      </div>
    </div>
    <el-card shadow="never">
      <div class="row">
        <el-select v-model="targetType" style="width:140px">
          <el-option label="表单" value="form" />
          <el-option label="页面" value="page" />
          <el-option label="实体" value="entity" />
        </el-select>
        <el-input v-model="prompt" type="textarea" :rows="3" placeholder="例如：客户合同审批表，含金额、签订日期、审批意见" />
        <el-button type="primary" :loading="loading" @click="generate">生成草稿</el-button>
      </div>
    </el-card>
    <el-card v-if="draft" shadow="never">
      <div class="meta">{{ draft.suggestedName }} · {{ draft.suggestedCode }} · {{ draft.providerCode }}/{{ draft.modelCode }}</div>
      <p class="msg">{{ draft.message }}</p>
      <el-table :data="draft.fields" stripe>
        <el-table-column prop="fieldCode" label="编码" width="160" />
        <el-table-column prop="fieldName" label="名称" />
        <el-table-column prop="component" label="组件" width="120" />
        <el-table-column prop="required" label="必填" width="80" />
      </el-table>
      <pre class="json">{{ draft.layoutJson }}</pre>
    </el-card>
    <el-card shadow="never">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center">
          <span>待审 Run</span>
          <el-button size="small" @click="loadRuns">刷新</el-button>
        </div>
      </template>
      <el-table v-loading="runsLoading" :data="runs" stripe>
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="targetType" label="类型" width="90" />
        <el-table-column prop="prompt" label="需求" show-overflow-tooltip />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.status==='DRAFT'" link type="primary" @click="approveCopilotRun(row.id).then(loadRuns)">批准</el-button>
            <el-button v-if="row.status==='DRAFT'" link type="danger" @click="rejectCopilotRun(row.id).then(loadRuns)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card shadow="never">
      <template #header>Outbox 待消费</template>
      <el-table :data="outbox" stripe>
        <el-table-column prop="id" label="Run" width="220" />
        <el-table-column prop="targetType" label="类型" width="90" />
        <el-table-column prop="prompt" label="需求" show-overflow-tooltip />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button
              v-permission="'lc:entity:add'"
              link
              type="primary"
              :disabled="!parseMaterializePayload(row as CopilotRun)"
              :title="(parseMaterializePayload(row as CopilotRun) ? '落库为低代码实体草稿' : '草稿无可用字段载荷')"
              @click="openMaterialize(row as CopilotRun)"
            >落库实体</el-button>
            <el-button link type="info" @click="confirmAckOnly(row as CopilotRun)">仅确认</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="matVisible" title="落库为实体草稿" width="720px" aria-label="落库实体表单">
      <el-alert
        title="草稿字段映射: input/textarea→STRING, number→DECIMAL, date→DATE, select→STRING (无字典信息, 可在实体管理中改为 DICT)。落库后自动确认消费该 Outbox。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-form label-width="100px" :model="matForm">
        <el-form-item label="实体编码">
          <el-input v-model="matForm.entityCode" aria-label="实体编码" />
        </el-form-item>
        <el-form-item label="实体名称">
          <el-input v-model="matForm.entityName" aria-label="实体名称" />
        </el-form-item>
        <el-form-item label="表名">
          <el-input v-model="matForm.tableName" aria-label="表名" />
        </el-form-item>
        <el-form-item label="模块编码">
          <el-input v-model="matForm.moduleCode" aria-label="模块编码" />
        </el-form-item>
      </el-form>
      <el-table :data="matFields" stripe size="small">
        <el-table-column prop="fieldCode" label="编码" width="160" />
        <el-table-column prop="fieldName" label="名称" />
        <el-table-column prop="component" label="组件" width="110" />
        <el-table-column label="落库类型" width="110">
          <template #default="{ row }">
            {{ componentToDataType((row as MaterializeField).component) }}
          </template>
        </el-table-column>
        <el-table-column prop="required" label="必填" width="70">
          <template #default="{ row }">{{ (row as MaterializeField).required ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="matVisible = false">取消</el-button>
        <el-button type="primary" :loading="matSaving" @click="confirmMaterialize">确认落库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.copilot-page { display:flex; flex-direction:column; gap:14px; }
.page-head { background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:16px; }
.head-title { margin:0; font-size:18px; }
.head-sub { margin:6px 0 0; color: var(--yt-text-secondary); font-size:12px; }
.row { display:flex; flex-direction:column; gap:10px; }
.meta { font-weight:600; margin-bottom:8px; }
.msg { color: var(--yt-text-secondary); font-size:12px; }
.json { background: var(--yt-bg-page, #f8fafc); padding:12px; border-radius:8px; overflow:auto; font-size:12px; }
</style>
