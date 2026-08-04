<script setup lang="ts">
import { onMounted, ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import {
  Refresh,
  Plus,
  Promotion,
  Edit,
  Delete,
  View,
  MagicStick,
  Check,
  ArrowDown,
} from '@element-plus/icons-vue'
import {
  pageSurveys,
  getSurveyDetail,
  createSurvey,
  updateSurvey,
  publishSurvey,
  startCollecting,
  closeSurvey,
  archiveSurvey,
  listQuestions,
  saveQuestion,
  updateQuestion,
  deleteQuestion,
  startResponse,
  submitResponse,
  listAnswers,
  pageResponses,
  getSurveyStats,
  generateAiDraft,
  type SurSurvey,
  type SurQuestion,
  type SurResponse,
  type SurAnswer,
  type SurveyStatsVO,
  type SaveSurveyRequest,
  type SaveQuestionRequest,
  type AnswerItem,
} from '@/api/survey'

const { t } = useI18n()

/**
 * 问卷表单运营页。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)
 *
 * 4 个 Tab:
 *  1. 运营监控: 6 项核心能力指标 (问卷/状态/收集/答卷/已提交/题目/评分/趋势)
 *  2. 问卷管理: 状态机 (DRAFT → PUBLISHED → COLLECTING → CLOSED → ARCHIVED) + 题目设计器
 *  3. 答卷管理: 答卷分页 + 答卷详情 (含答题列表)
 *  4. AI 草稿生成: 提示词输入 + 草稿预览 + 采纳入库 (mock-model)
 *
 * 核心能力验证 (6 项):
 *  - 动态表单渲染: 问卷管理 Tab 创建/编辑题目 + 详情聚合接口 (survey + questions)
 *  - 条件显隐: 题目设计器 logicJson 字段 (9 种 operator + 3 种 action), 提交答卷时服务端校验
 *  - 字段校验: 题目设计器 validationJson 字段 (minLength/maxLength/regex/minValue/maxValue/minSelect/maxSelect)
 *  - 移动端填写: 同一接口 (POST /surveys/responses/start + submit), source 字段标识来源
 *  - 统计报表: 运营监控 Tab 多维聚合 (问卷/状态/来源/分类/趋势)
 *  - AI 生成题目草稿: AI 草稿生成 Tab (mock-model, 不直接写库, 用户预览采纳)
 */

const activeTab = ref<'stats' | 'surveys' | 'responses' | 'ai-draft'>('stats')

// 题目类型选项
const QUESTION_TYPES = [
  { value: 'SINGLE_CHOICE', label: t('survey.questionType.singleChoice') },
  { value: 'MULTI_CHOICE', label: t('survey.questionType.multiChoice') },
  { value: 'TEXT', label: t('survey.questionType.text') },
  { value: 'TEXTAREA', label: t('survey.questionType.textarea') },
  { value: 'RATING', label: t('survey.questionType.rating') },
  { value: 'DATE', label: t('survey.questionType.date') },
  { value: 'MATRIX', label: t('survey.questionType.matrix') },
]

// 状态选项
const STATUS_OPTIONS = [
  { value: 'DRAFT', label: t('survey.status.draft') },
  { value: 'PUBLISHED', label: t('survey.status.published') },
  { value: 'COLLECTING', label: t('survey.status.collecting') },
  { value: 'CLOSED', label: t('survey.status.closed') },
  { value: 'ARCHIVED', label: t('survey.status.archived') },
]

// 逻辑操作符选项 (题目设计器参考, 实际 logicJson 由用户直接输入 JSON)
// const LOGIC_OPERATORS = ['EQ', 'NE', 'IN', 'NOT_IN', 'CONTAINS', 'GT', 'GTE', 'LT', 'LTE']
// const LOGIC_ACTIONS = ['SHOW', 'HIDE', 'REQUIRE']

// ==================== 运营监控 ====================
const stats = ref<SurveyStatsVO>({})
const statsLoading = ref(false)

async function loadStats() {
  statsLoading.value = true
  try {
    stats.value = await getSurveyStats()
  } catch (e) {
    console.error('loadStats failed', e)
  } finally {
    statsLoading.value = false
  }
}

function statusLabel(s: string): string {
  return STATUS_OPTIONS.find((o) => o.value === s)?.label || s
}

function formatDuration(ms?: number): string {
  if (!ms || ms <= 0) return '-'
  if (ms < 60_000) return t('survey.duration.seconds', { n: Math.round(ms / 1000) })
  if (ms < 3_600_000) return t('survey.duration.minutes', { n: Math.round(ms / 60_000) })
  return t('survey.duration.hours', { n: (ms / 3_600_000).toFixed(1) })
}

const recentTrendMax = computed(() => {
  const trend = stats.value.recentTrend || []
  if (trend.length === 0) return 1
  return Math.max(...trend.map((t) => t.count), 1)
})

// ==================== 问卷管理 ====================
const surveyList = ref<SurSurvey[]>([])
const surveyTotal = ref(0)
const surveyLoading = ref(false)
const surveyQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  surveyNo: '',
  title: '',
  status: '',
  category: '',
})

const surveyDialogVisible = ref(false)
const surveyDialogMode = ref<'create' | 'edit'>('create')
const surveyEditingId = ref('')
const surveyForm = reactive<SaveSurveyRequest>({
  title: '',
  description: '',
  category: '',
  anonymous: false,
  maxResponsesPerUser: 0,
  startTime: undefined,
  endTime: undefined,
  themeJson: '',
  remark: '',
})

// 题目设计器
const questionDesignerVisible = ref(false)
const questionDesignerSurvey = ref<SurSurvey | null>(null)
const questionList = ref<SurQuestion[]>([])
const questionLoading = ref(false)
const questionDialogVisible = ref(false)
const questionDialogMode = ref<'create' | 'edit'>('create')
const questionEditingId = ref('')
const questionForm = reactive<SaveQuestionRequest>({
  questionCode: '',
  questionType: 'SINGLE_CHOICE',
  title: '',
  description: '',
  required: true,
  sortNo: 1,
  optionsJson: '[{"code":"A","label":"选项 A"},{"code":"B","label":"选项 B"}]', // mock seed, 不参与 i18n
  validationJson: '',
  logicJson: '',
  matrixJson: '',
  remark: '',
})

// 模拟填写对话框 (验证服务端字段校验 + 条件显隐)
const fillDialogVisible = ref(false)
const fillSurvey = ref<SurSurvey | null>(null)
const fillQuestions = ref<SurQuestion[]>([])
const fillAnswers = reactive<Record<string, any>>({}) // questionId -> 答案值 (string 或 number, RATING 为 number)
const fillSource = ref<'WEB_ADMIN' | 'MOBILE_UNIAPP'>('WEB_ADMIN')

async function loadSurveys() {
  surveyLoading.value = true
  try {
    const params: any = { ...surveyQuery }
    if (!params.surveyNo) delete params.surveyNo
    if (!params.title) delete params.title
    if (!params.status) delete params.status
    if (!params.category) delete params.category
    const res = await pageSurveys(params)
    surveyList.value = res.records as SurSurvey[]
    surveyTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadSurveys failed', e)
  } finally {
    surveyLoading.value = false
  }
}

function handleSurveyPageChange(page: number) {
  surveyQuery.pageNo = page
  loadSurveys()
}

function openSurveyCreateDialog() {
  surveyDialogMode.value = 'create'
  surveyEditingId.value = ''
  surveyForm.title = ''
  surveyForm.description = ''
  surveyForm.category = ''
  surveyForm.anonymous = false
  surveyForm.maxResponsesPerUser = 0
  surveyForm.startTime = undefined
  surveyForm.endTime = undefined
  surveyForm.themeJson = ''
  surveyForm.remark = ''
  surveyDialogVisible.value = true
}

function openSurveyEditDialog(row: any) {
  surveyDialogMode.value = 'edit'
  surveyEditingId.value = row.id
  surveyForm.title = row.title
  surveyForm.description = row.description || ''
  surveyForm.category = row.category || ''
  surveyForm.anonymous = !!row.anonymous
  surveyForm.maxResponsesPerUser = row.maxResponsesPerUser || 0
  surveyForm.startTime = row.startTime || undefined
  surveyForm.endTime = row.endTime || undefined
  surveyForm.themeJson = row.themeJson || ''
  surveyForm.remark = row.remark || ''
  surveyDialogVisible.value = true
}

async function handleSaveSurvey() {
  if (!surveyForm.title.trim()) {
    ElMessage.warning(t('survey.msg.surveyTitleRequired'))
    return
  }
  try {
    if (surveyDialogMode.value === 'create') {
      const created = await createSurvey(surveyForm)
      ElMessage.success(t('survey.msg.surveyCreated', { surveyNo: created.surveyNo }))
    } else {
      await updateSurvey(surveyEditingId.value, surveyForm)
      ElMessage.success(t('survey.msg.surveyUpdated'))
    }
    surveyDialogVisible.value = false
    loadSurveys()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.saveFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

async function handlePublish(row: SurSurvey) {
  if (row.status !== 'DRAFT') {
    ElMessage.warning(t('survey.msg.cannotPublish', { status: row.status }))
    return
  }
  try {
    await ElMessageBox.confirm(t('survey.msg.confirmPublish', { surveyNo: row.surveyNo }), t('survey.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const updated = await publishSurvey(row.id)
    ElMessage.success(t('survey.msg.published', { status: updated.status }))
    loadSurveys()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.publishFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

async function handleStartCollecting(row: SurSurvey) {
  if (row.status !== 'PUBLISHED' && row.status !== 'CLOSED') {
    ElMessage.warning(t('survey.msg.cannotStartCollecting', { status: row.status }))
    return
  }
  try {
    await ElMessageBox.confirm(t('survey.msg.confirmStartCollecting', { surveyNo: row.surveyNo }), t('survey.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const updated = await startCollecting(row.id)
    ElMessage.success(t('survey.msg.startCollectingDone', { status: updated.status }))
    loadSurveys()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.startCollectingFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

async function handleClose(row: SurSurvey) {
  if (row.status !== 'COLLECTING') {
    ElMessage.warning(t('survey.msg.cannotClose', { status: row.status }))
    return
  }
  try {
    await ElMessageBox.confirm(t('survey.msg.confirmClose', { surveyNo: row.surveyNo }), t('survey.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const updated = await closeSurvey(row.id)
    ElMessage.success(t('survey.msg.closed', { status: updated.status }))
    loadSurveys()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.closeFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

async function handleArchive(row: SurSurvey) {
  if (row.status !== 'DRAFT' && row.status !== 'CLOSED') {
    ElMessage.warning(t('survey.msg.cannotArchive', { status: row.status }))
    return
  }
  try {
    await ElMessageBox.confirm(t('survey.msg.confirmArchive', { surveyNo: row.surveyNo }), t('survey.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  try {
    const updated = await archiveSurvey(row.id)
    ElMessage.success(t('survey.msg.archived', { status: updated.status }))
    loadSurveys()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.archiveFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

// 题目设计器
async function openQuestionDesigner(row: any) {
  questionDesignerSurvey.value = row
  questionDesignerVisible.value = true
  await loadQuestions(row.id)
}

async function loadQuestions(surveyId: string) {
  questionLoading.value = true
  try {
    questionList.value = await listQuestions(surveyId)
  } catch (e) {
    console.error('loadQuestions failed', e)
  } finally {
    questionLoading.value = false
  }
}

function openQuestionCreateDialog() {
  if (questionDesignerSurvey.value?.status !== 'DRAFT') {
    ElMessage.warning(t('survey.msg.onlyDraftCanAddQuestion'))
    return
  }
  questionDialogMode.value = 'create'
  questionEditingId.value = ''
  questionForm.questionCode = `Q${String(questionList.value.length + 1).padStart(3, '0')}`
  questionForm.questionType = 'SINGLE_CHOICE'
  questionForm.title = ''
  questionForm.description = ''
  questionForm.required = true
  questionForm.sortNo = questionList.value.length + 1
  questionForm.optionsJson = '[{"code":"A","label":"选项 A"},{"code":"B","label":"选项 B"}]' // mock seed, 不参与 i18n
  questionForm.validationJson = ''
  questionForm.logicJson = ''
  questionForm.matrixJson = ''
  questionForm.remark = ''
  questionDialogVisible.value = true
}

function openQuestionEditDialog(q: any) {
  if (questionDesignerSurvey.value?.status !== 'DRAFT') {
    ElMessage.warning(t('survey.msg.onlyDraftCanEditQuestion'))
    return
  }
  questionDialogMode.value = 'edit'
  questionEditingId.value = q.id
  questionForm.questionCode = q.questionCode
  questionForm.questionType = q.questionType
  questionForm.title = q.title
  questionForm.description = q.description || ''
  questionForm.required = !!q.required
  questionForm.sortNo = q.sortNo || 1
  questionForm.optionsJson = q.optionsJson || ''
  questionForm.validationJson = q.validationJson || ''
  questionForm.logicJson = q.logicJson || ''
  questionForm.matrixJson = q.matrixJson || ''
  questionForm.remark = q.remark || ''
  questionDialogVisible.value = true
}

async function handleSaveQuestion() {
  if (!questionForm.questionCode.trim()) {
    ElMessage.warning(t('survey.msg.questionCodeRequired'))
    return
  }
  if (!questionForm.title.trim()) {
    ElMessage.warning(t('survey.msg.questionTitleRequired'))
    return
  }
  const surveyId = questionDesignerSurvey.value?.id
  if (!surveyId) return
  try {
    if (questionDialogMode.value === 'create') {
      await saveQuestion(surveyId, questionForm)
      ElMessage.success(t('survey.msg.questionAdded'))
    } else {
      await updateQuestion(surveyId, questionEditingId.value, questionForm)
      ElMessage.success(t('survey.msg.questionUpdated'))
    }
    questionDialogVisible.value = false
    loadQuestions(surveyId)
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.saveFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

async function handleDeleteQuestion(q: any) {
  if (questionDesignerSurvey.value?.status !== 'DRAFT') {
    ElMessage.warning(t('survey.msg.onlyDraftCanDeleteQuestion'))
    return
  }
  try {
    await ElMessageBox.confirm(t('survey.msg.confirmDeleteQuestion', { questionCode: q.questionCode }), t('survey.msg.tip'), { type: 'warning' })
  } catch {
    return
  }
  const surveyId = questionDesignerSurvey.value?.id
  if (!surveyId) return
  try {
    await deleteQuestion(surveyId, q.id)
    ElMessage.success(t('survey.msg.deleted'))
    loadQuestions(surveyId)
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.deleteFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

function questionTypeLabel(t: string): string {
  return QUESTION_TYPES.find((o) => o.value === t)?.label || t
}

// ==================== 模拟填写 (验证服务端字段校验 + 条件显隐) ====================
async function openFillDialog(row: any) {
  if (row.status !== 'COLLECTING') {
    ElMessage.warning(t('survey.msg.onlyCollectingCanFill', { status: row.status }))
    return
  }
  try {
    const detail = await getSurveyDetail(row.id)
    fillSurvey.value = detail.survey
    fillQuestions.value = detail.questions
    // 清空 reactive 对象 (不能直接赋值 {}, 需逐个删除 key)
    Object.keys(fillAnswers).forEach((k) => delete fillAnswers[k])
    fillSource.value = 'WEB_ADMIN'
    fillDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(t('survey.msg.loadSurveyDetailFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

async function handleSubmitFill() {
  if (!fillSurvey.value) return
  const surveyId = fillSurvey.value.id
  const answers: AnswerItem[] = []
  for (const q of fillQuestions.value) {
    const value = fillAnswers[q.id] || ''
    // 跳过条件显隐隐藏的题目 (前端简单判断, 服务端会再次校验)
    if (q.logicJson && isQuestionHiddenByLogic(q, fillAnswers)) {
      continue
    }
    if (q.required && !value) {
      ElMessage.warning(t('survey.msg.questionRequired', { questionCode: q.questionCode, title: q.title }))
      return
    }
    if (!value) continue
    const item: AnswerItem = {
      questionId: q.id,
      questionCode: q.questionCode,
      answerValue: value,
      answerText: value,
    }
    if (q.questionType === 'RATING') {
      item.ratingScore = Number(value)
    } else if (q.questionType === 'SINGLE_CHOICE' || q.questionType === 'MULTI_CHOICE') {
      item.selectedOptions = JSON.stringify(value.split(','))
    }
    answers.push(item)
  }
  if (answers.length === 0) {
    ElMessage.warning(t('survey.msg.fillAtLeastOne'))
    return
  }
  try {
    // 启动答卷 (创建 IN_PROGRESS 记录)
    await startResponse(surveyId, fillSource.value)
    // 提交答卷 (服务端校验字段 + 条件显隐)
    const resp = await submitResponse({ surveyId, source: fillSource.value, answers })
    ElMessage.success(t('survey.msg.responseSubmitted', { responseNo: resp.responseNo, status: resp.status }))
    fillDialogVisible.value = false
    loadSurveys()
    loadStats()
    if (activeTab.value !== 'responses') {
      activeTab.value = 'responses'
    }
    loadResponseList()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.submitFailed', { message: e?.message || t('survey.msg.unknownError') }))
  }
}

/** 简单判断题目是否被条件显隐隐藏 (前端预判, 服务端会再次权威校验) */
function isQuestionHiddenByLogic(q: SurQuestion, answers: Record<string, any>): boolean {
  if (!q.logicJson) return false
  let rules: any[] = []
  try {
    rules = JSON.parse(q.logicJson)
  } catch {
    return false
  }
  if (!Array.isArray(rules)) return false
  for (const r of rules) {
    if (r.action !== 'HIDE') continue
    const targetQ = fillQuestions.value.find((x) => x.questionCode === r.questionCode)
    if (!targetQ) continue
    const targetVal = answers[targetQ.id]
    if (targetVal == null) continue
    if (compareValue(targetVal, r.operator, r.value)) {
      return true
    }
  }
  return false
}

function compareValue(actual: any, operator: string, expected: string): boolean {
  switch (operator) {
    case 'EQ': return actual === expected
    case 'NE': return actual !== expected
    case 'IN': return expected.split(',').map((s) => s.trim()).includes(actual)
    case 'NOT_IN': return !expected.split(',').map((s) => s.trim()).includes(actual)
    case 'CONTAINS': return actual.includes(expected)
    case 'GT': return Number(actual) > Number(expected)
    case 'GTE': return Number(actual) >= Number(expected)
    case 'LT': return Number(actual) < Number(expected)
    case 'LTE': return Number(actual) <= Number(expected)
    default: return false
  }
}

// ==================== 答卷管理 ====================
const responseList = ref<SurResponse[]>([])
const responseTotal = ref(0)
const responseLoading = ref(false)
const responseQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  surveyId: '',
  respondentId: '',
  status: '',
})

const responseDetailVisible = ref(false)
const responseDetail = ref<SurResponse | null>(null)
const answerList = ref<SurAnswer[]>([])
const answerLoading = ref(false)

async function loadResponseList() {
  responseLoading.value = true
  try {
    const params: any = { ...responseQuery }
    if (!params.surveyId) delete params.surveyId
    if (!params.respondentId) delete params.respondentId
    if (!params.status) delete params.status
    const res = await pageResponses(params)
    responseList.value = res.records as SurResponse[]
    responseTotal.value = Number(res.total) || 0
  } catch (e) {
    console.error('loadResponseList failed', e)
  } finally {
    responseLoading.value = false
  }
}

function handleResponsePageChange(page: number) {
  responseQuery.pageNo = page
  loadResponseList()
}

async function openResponseDetail(row: any) {
  responseDetail.value = row
  responseDetailVisible.value = true
  answerLoading.value = true
  try {
    answerList.value = await listAnswers(row.id)
  } catch (e) {
    console.error('listAnswers failed', e)
  } finally {
    answerLoading.value = false
  }
}

function responseStatusType(s: string): 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined {
  switch (s) {
    case 'SUBMITTED': return 'success'
    case 'IN_PROGRESS': return 'warning'
    case 'ABANDONED': return 'info'
    default: return undefined
  }
}

function surveyStatusType(s: string): 'success' | 'primary' | 'warning' | 'info' | 'danger' | undefined {
  switch (s) {
    case 'COLLECTING': return 'success'
    case 'PUBLISHED': return 'primary'
    case 'DRAFT': return 'warning'
    case 'CLOSED': return 'info'
    case 'ARCHIVED': return 'info'
    default: return undefined
  }
}

// ==================== AI 草稿生成 ====================
const aiPrompt = ref(t('survey.ai.defaultPrompt'))
const aiExpectedCount = ref(5)
const aiGenerating = ref(false)
const aiDraftResult = ref<SaveQuestionRequest[]>([])
const aiAdoptSurveyId = ref('')
const aiAdoptDialogVisible = ref(false)
const aiAdopting = ref(false)

async function handleGenerateAiDraft() {
  if (!aiPrompt.value.trim()) {
    ElMessage.warning(t('survey.msg.aiPromptRequired'))
    return
  }
  aiGenerating.value = true
  try {
    const result = await generateAiDraft({ prompt: aiPrompt.value, expectedCount: aiExpectedCount.value })
    // 后端返回 JSON 字符串, 解析为题目数组
    let parsed: any
    try {
      parsed = typeof result === 'string' ? JSON.parse(result) : result
    } catch {
      // 后端返回的是包装字符串, 需二次解析
      parsed = JSON.parse(JSON.parse(result))
    }
    aiDraftResult.value = Array.isArray(parsed) ? parsed : []
    ElMessage.success(t('survey.msg.aiDraftGenerated', { count: aiDraftResult.value.length }))
  } catch (e: any) {
    ElMessage.error(t('survey.msg.aiDraftGenerateFailed', { message: e?.message || t('survey.msg.unknownError') }))
  } finally {
    aiGenerating.value = false
  }
}

function openAiAdoptDialog() {
  if (aiDraftResult.value.length === 0) {
    ElMessage.warning(t('survey.msg.pleaseGenerateAiDraftFirst'))
    return
  }
  aiAdoptSurveyId.value = ''
  // 加载 DRAFT 状态问卷作为采纳入库目标
  loadSurveysForAdopt()
  aiAdoptDialogVisible.value = true
}

const adoptSurveyOptions = ref<SurSurvey[]>([])
async function loadSurveysForAdopt() {
  try {
    const res = await pageSurveys({ pageNo: 1, pageSize: 100, status: 'DRAFT' })
    adoptSurveyOptions.value = res.records as SurSurvey[]
  } catch (e) {
    console.error('loadSurveysForAdopt failed', e)
  }
}

async function handleAdoptAiDraft() {
  if (!aiAdoptSurveyId.value) {
    ElMessage.warning(t('survey.msg.pleaseSelectAdoptTarget'))
    return
  }
  aiAdopting.value = true
  let success = 0
  let failed = 0
  try {
    // 按 sortNo 顺序逐条采纳
    for (let i = 0; i < aiDraftResult.value.length; i++) {
      const q = aiDraftResult.value[i]
      try {
        await saveQuestion(aiAdoptSurveyId.value, { ...q, sortNo: q.sortNo || i + 1 })
        success++
      } catch {
        failed++
      }
    }
    ElMessage.success(t('survey.msg.adoptCompleted', { success, failed }))
    aiAdoptDialogVisible.value = false
    aiDraftResult.value = []
    loadSurveys()
    loadStats()
  } catch (e: any) {
    ElMessage.error(t('survey.msg.adoptFailed', { message: e?.message || t('survey.msg.unknownError') }))
  } finally {
    aiAdopting.value = false
  }
}

// ==================== 模拟填写辅助 (多选答案中转 + 选项解析 + 状态机指令分发) ====================
// 多选答案中转 (CheckboxGroup 需要 string[])
const fillMultiAnswers = reactive<Record<string, string[]>>({})
// 监听多选变化, 同步到 fillAnswers
watch(fillMultiAnswers, (val) => {
  for (const qid in val) {
    fillAnswers[qid] = val[qid].join(',')
  }
}, { deep: true })

// 解析 optionsJson
function parseOptions(json?: string): { code: string; label: string }[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    if (Array.isArray(arr)) return arr
  } catch {
    // ignore
  }
  return []
}

// 状态机指令分发
function handleStateCommand(cmd: string, row: any) {
  switch (cmd) {
    case 'publish': handlePublish(row); break
    case 'start': handleStartCollecting(row); break
    case 'close': handleClose(row); break
    case 'archive': handleArchive(row); break
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadStats()
  loadSurveys()
  loadResponseList()
})
</script>

<template>
  <div class="survey-ops">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- ==================== Tab 1: 运营监控 ==================== -->
      <el-tab-pane :label="$t('survey.stats.tabStats')" name="stats">
        <div class="toolbar">
          <el-button :icon="Refresh" @click="loadStats" :loading="statsLoading">{{ $t('survey.stats.refreshStats') }}</el-button>
        </div>

        <!-- 6 项核心能力指标卡 -->
        <el-row :gutter="16" class="stats-cards" v-loading="statsLoading">
          <el-col :span="4">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-label">{{ $t('survey.stats.totalSurveys') }}</div>
              <div class="stat-value">{{ stats.totalSurveys ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-label">{{ $t('survey.stats.collecting') }}</div>
              <div class="stat-value text-success">{{ stats.collectingCount ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-label">{{ $t('survey.stats.totalResponses') }}</div>
              <div class="stat-value">{{ stats.totalResponses ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-label">{{ $t('survey.stats.submitted') }}</div>
              <div class="stat-value text-success">{{ stats.submittedCount ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-label">{{ $t('survey.stats.questionCount') }}</div>
              <div class="stat-value">{{ stats.questionCount ?? 0 }}</div>
            </el-card>
          </el-col>
          <el-col :span="4">
            <el-card shadow="hover" class="stat-card">
              <div class="stat-label">{{ $t('survey.stats.avgScore') }}</div>
              <div class="stat-value">{{ (stats.avgScore ?? 0).toFixed(2) }}</div>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="stats-detail">
          <!-- 状态分布 -->
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><span>{{ $t('survey.stats.statusDistribution') }}</span></template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label">
                  <el-tag :type="surveyStatusType(opt.value)" size="small">
                    {{ stats.statusCounts?.[opt.value] ?? 0 }}
                  </el-tag>
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <!-- 来源渠道分布 -->
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><span>{{ $t('survey.stats.sourceChannel') }}</span></template>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="WEB_ADMIN">
                  <el-tag size="small">{{ stats.sourceCounts?.WEB_ADMIN ?? 0 }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="MOBILE_UNIAPP">
                  <el-tag type="success" size="small">{{ stats.sourceCounts?.MOBILE_UNIAPP ?? 0 }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="API">
                  <el-tag type="info" size="small">{{ stats.sourceCounts?.API ?? 0 }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item :label="$t('survey.stats.avgDuration')">
                  <span>{{ formatDuration(stats.avgDurationMs) }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
          <!-- 7 日提交趋势 (简易柱图) -->
          <el-col :span="8">
            <el-card shadow="never">
              <template #header><span>{{ $t('survey.stats.recentTrend') }}</span></template>
              <div class="trend-chart">
                <div v-for="(t, idx) in (stats.recentTrend || [])" :key="idx" class="trend-bar">
                  <div class="trend-bar-value">{{ t.count }}</div>
                  <div class="trend-bar-bar"
                       :style="{ height: `${(t.count / recentTrendMax) * 120}px` }"
                       :title="`${t.date}: ${t.count} ${$t('survey.stats.trendCountSuffix')}`"></div>
                  <div class="trend-bar-date">{{ t.date.slice(5) }}</div>
                </div>
                <div v-if="!stats.recentTrend || stats.recentTrend.length === 0" class="trend-empty">
                  {{ $t('survey.stats.noTrendData') }}
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <!-- 分类分布 -->
        <el-card shadow="never" class="stats-category">
          <template #header><span>{{ $t('survey.stats.categoryDistribution') }}</span></template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item
              v-for="(count, cat) in (stats.categoryCounts || {})"
              :key="cat"
              :label="cat || $t('survey.stats.uncategorized')"
            >
              <el-tag size="small">{{ count }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="!stats.categoryCounts || Object.keys(stats.categoryCounts).length === 0" :label="$t('survey.stats.none')">
              <span>-</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- ==================== Tab 2: 问卷管理 ==================== -->
      <el-tab-pane :label="$t('survey.list.tabSurveys')" name="surveys">
        <div class="toolbar">
          <el-form :inline="true" :model="surveyQuery" @submit.prevent>
            <el-form-item :label="$t('survey.list.surveyNo')">
              <el-input v-model="surveyQuery.surveyNo" :placeholder="$t('survey.list.exactMatch')" clearable style="width: 160px" />
            </el-form-item>
            <el-form-item :label="$t('survey.list.title')">
              <el-input v-model="surveyQuery.title" :placeholder="$t('survey.list.fuzzyMatch')" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item :label="$t('common.field.status')">
              <el-select v-model="surveyQuery.status" :placeholder="$t('survey.list.all')" clearable style="width: 120px">
                <el-option v-for="opt in STATUS_OPTIONS" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('survey.list.category')">
              <el-input v-model="surveyQuery.category" :placeholder="$t('survey.list.exactMatch')" clearable style="width: 140px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Refresh" @click="surveyQuery.pageNo = 1; loadSurveys()">{{ $t('common.action.list') }}</el-button>
            </el-form-item>
          </el-form>
          <div>
            <el-button type="primary" :icon="Plus" @click="openSurveyCreateDialog">{{ $t('survey.list.create') }}</el-button>
            <el-button :icon="Refresh" @click="loadSurveys">{{ $t('common.action.refresh') }}</el-button>
          </div>
        </div>

        <el-table :data="surveyList" v-loading="surveyLoading" border stripe>
          <el-table-column prop="surveyNo" :label="$t('survey.list.surveyNo')" width="180" />
          <el-table-column prop="title" :label="$t('survey.list.title')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="category" :label="$t('survey.list.category')" width="120" show-overflow-tooltip />
          <el-table-column :label="$t('common.field.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="surveyStatusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="responseCount" :label="$t('survey.list.responseCount')" width="80" align="right" />
          <el-table-column :label="$t('survey.list.anonymous')" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.anonymous ? 'success' : 'info'" size="small">
                {{ row.anonymous ? $t('survey.list.yes') : $t('survey.list.no') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="maxResponsesPerUser" :label="$t('survey.list.maxResponses')" width="90" align="right" />
          <el-table-column prop="createdTime" :label="$t('common.field.createdTime')" width="170" />
          <el-table-column :label="$t('survey.list.action')" width="380" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :icon="View" @click="openQuestionDesigner(row)">{{ $t('survey.list.questionDesign') }}</el-button>
              <el-button size="small" :icon="Edit" @click="openSurveyEditDialog(row)">{{ $t('common.action.edit') }}</el-button>
              <el-button size="small" type="success" :icon="Promotion" @click="openFillDialog(row)">{{ $t('survey.list.mockFill') }}</el-button>
              <el-dropdown trigger="click" @command="(cmd: string) => handleStateCommand(cmd, row)">
                <el-button size="small">{{ $t('survey.list.stateMachine') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="publish">{{ $t('survey.list.publish') }}</el-dropdown-item>
                    <el-dropdown-item command="start">{{ $t('survey.list.startCollect') }}</el-dropdown-item>
                    <el-dropdown-item command="close">{{ $t('survey.list.closeAction') }}</el-dropdown-item>
                    <el-dropdown-item command="archive">{{ $t('survey.list.archive') }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pagination"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="surveyTotal"
          :page-size="surveyQuery.pageSize"
          :current-page="surveyQuery.pageNo"
          :page-sizes="[10, 20, 50]"
          @current-change="handleSurveyPageChange"
          @size-change="(s: number) => { surveyQuery.pageSize = s; loadSurveys() }"
        />

        <!-- 问卷创建/编辑对话框 -->
        <el-dialog
          v-model="surveyDialogVisible"
          :title="surveyDialogMode === 'create' ? $t('survey.form.createTitle') : $t('survey.form.editTitle')"
          width="640px"
        >
          <el-form :model="surveyForm" label-width="120px">
            <el-form-item :label="$t('survey.form.surveyTitle')" required>
              <el-input v-model="surveyForm.title" maxlength="256" show-word-limit />
            </el-form-item>
            <el-form-item :label="$t('survey.form.surveyDescription')">
              <el-input v-model="surveyForm.description" type="textarea" :rows="2" maxlength="1024" show-word-limit />
            </el-form-item>
            <el-form-item :label="$t('survey.list.category')">
              <el-input v-model="surveyForm.category" maxlength="64" :placeholder="$t('survey.form.categoryPlaceholder')" />
            </el-form-item>
            <el-form-item :label="$t('survey.form.isAnonymous')">
              <el-switch v-model="surveyForm.anonymous" />
              <span class="form-hint">{{ $t('survey.form.anonymousHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('survey.form.maxResponses')">
              <el-input-number v-model="surveyForm.maxResponsesPerUser" :min="0" :max="999" />
              <span class="form-hint">{{ $t('survey.form.maxResponsesHint') }}</span>
            </el-form-item>
            <el-form-item :label="$t('survey.form.startTime')">
              <el-date-picker v-model="surveyForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" />
            </el-form-item>
            <el-form-item :label="$t('survey.form.endTime')">
              <el-date-picker v-model="surveyForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" />
            </el-form-item>
            <el-form-item :label="$t('survey.form.themeJson')">
              <el-input v-model="surveyForm.themeJson" type="textarea" :rows="2" placeholder='{"primaryColor":"#409eff"}' />
            </el-form-item>
            <el-form-item :label="$t('survey.form.remark')">
              <el-input v-model="surveyForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="surveyDialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
            <el-button type="primary" @click="handleSaveSurvey">{{ $t('common.action.save') }}</el-button>
          </template>
        </el-dialog>

        <!-- 题目设计器 -->
        <el-dialog
          v-model="questionDesignerVisible"
          :title="`${$t('survey.question.designerTitle')} - ${questionDesignerSurvey?.surveyNo || ''}`"
          width="1100px"
          top="5vh"
        >
          <div class="designer-header">
            <span>{{ $t('survey.question.surveyLabel') }} <strong>{{ questionDesignerSurvey?.title }}</strong></span>
            <el-tag :type="surveyStatusType(questionDesignerSurvey?.status || '')" size="small">
              {{ statusLabel(questionDesignerSurvey?.status || '') }}
            </el-tag>
            <el-button
              v-if="questionDesignerSurvey?.status === 'DRAFT'"
              type="primary"
              :icon="Plus"
              size="small"
              @click="openQuestionCreateDialog"
            >{{ $t('survey.question.add') }}</el-button>
            <el-tag v-else type="info" size="small">{{ $t('survey.question.draftOnlyHint') }}</el-tag>
          </div>
          <el-table :data="questionList" v-loading="questionLoading" border stripe size="small">
            <el-table-column prop="sortNo" :label="$t('survey.question.sortNo')" width="60" />
            <el-table-column prop="questionCode" :label="$t('survey.question.code')" width="100" />
            <el-table-column :label="$t('survey.question.type')" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ questionTypeLabel(row.questionType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" :label="$t('survey.question.title')" min-width="220" show-overflow-tooltip />
            <el-table-column :label="$t('survey.question.required')" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                  {{ row.required ? $t('survey.question.requiredYes') : $t('survey.question.requiredNo') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('survey.question.aiGenerated')" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.aiGenerated" type="warning" size="small">AI</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('survey.question.validation')" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.validationJson" class="text-warning">{{ row.validationJson }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('survey.question.logic')" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.logicJson" class="text-warning">{{ row.logicJson }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('survey.list.action')" width="160" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :icon="Edit" @click="openQuestionEditDialog(row)">{{ $t('common.action.edit') }}</el-button>
                <el-button size="small" type="danger" :icon="Delete" @click="handleDeleteQuestion(row)">{{ $t('common.action.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 题目新增/编辑对话框 -->
          <el-dialog
            v-model="questionDialogVisible"
            :title="questionDialogMode === 'create' ? $t('survey.question.createTitle') : $t('survey.question.editTitle')"
            width="780px"
            append-to-body
          >
            <el-form :model="questionForm" label-width="120px">
              <el-form-item :label="$t('survey.question.code')" required>
                <el-input v-model="questionForm.questionCode" maxlength="32" placeholder="Q001" />
              </el-form-item>
              <el-form-item :label="$t('survey.question.typeLabel')" required>
                <el-select v-model="questionForm.questionType" style="width: 200px">
                  <el-option v-for="t in QUESTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('survey.question.title')" required>
                <el-input v-model="questionForm.title" maxlength="512" show-word-limit />
              </el-form-item>
              <el-form-item :label="$t('survey.question.description')">
                <el-input v-model="questionForm.description" type="textarea" :rows="2" maxlength="1024" show-word-limit />
              </el-form-item>
              <el-form-item :label="$t('survey.question.isRequired')">
                <el-switch v-model="questionForm.required" />
              </el-form-item>
              <el-form-item :label="$t('survey.question.sortOrder')">
                <el-input-number v-model="questionForm.sortNo" :min="1" :max="999" />
              </el-form-item>
              <el-form-item :label="$t('survey.question.optionsJson')">
                <el-input
                  v-model="questionForm.optionsJson"
                  type="textarea"
                  :rows="3"
                  placeholder='[{"code":"A","label":"选项 A"}] (SINGLE_CHOICE/MULTI_CHOICE/RATING/MATRIX)'
                /> <!-- mock seed example, 不参与 i18n -->
              </el-form-item>
              <el-form-item :label="$t('survey.question.validationJson')">
                <el-input
                  v-model="questionForm.validationJson"
                  type="textarea"
                  :rows="3"
                  placeholder='{"minLength":10,"maxLength":500,"regex":"^1\\d{10}$"} 或 {"minValue":1,"maxValue":5} 或 {"minSelect":2,"maxSelect":4}'
                /> <!-- 含中文连接词"或"，但与 JSON 示例耦合，保持原样以避免破坏示例 -->
                <span class="form-hint">{{ $t('survey.question.validationHint') }}</span>
              </el-form-item>
              <el-form-item :label="$t('survey.question.logicJson')">
                <el-input
                  v-model="questionForm.logicJson"
                  type="textarea"
                  :rows="3"
                  placeholder='[{"questionCode":"Q001","operator":"EQ","value":"A","action":"SHOW"}] (operator: EQ/NE/IN/NOT_IN/CONTAINS/GT/GTE/LT/LTE, action: SHOW/HIDE/REQUIRE)'
                />
                <span class="form-hint">{{ $t('survey.question.logicHint') }}</span>
              </el-form-item>
              <el-form-item :label="$t('survey.question.matrixJson')">
                <el-input
                  v-model="questionForm.matrixJson"
                  type="textarea"
                  :rows="2"
                  :placeholder="$t('survey.question.placeholder.matrixJson')"
                />
              </el-form-item>
              <el-form-item :label="$t('survey.question.remark')">
                <el-input v-model="questionForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="questionDialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
              <el-button type="primary" @click="handleSaveQuestion">{{ $t('common.action.save') }}</el-button>
            </template>
          </el-dialog>
        </el-dialog>

        <!-- 模拟填写对话框 -->
        <el-dialog
          v-model="fillDialogVisible"
          :title="`${$t('survey.fill.title')} - ${fillSurvey?.surveyNo || ''}`"
          width="780px"
          top="5vh"
        >
          <el-alert
            :title="$t('survey.fill.alertTitle')"
            type="info"
            :closable="false"
            show-icon
            class="fill-tip"
          />
          <el-form label-width="160px">
            <el-form-item :label="$t('survey.fill.sourceChannel')">
              <el-radio-group v-model="fillSource">
                <el-radio value="WEB_ADMIN">{{ $t('survey.fill.webAdmin') }}</el-radio>
                <el-radio value="MOBILE_UNIAPP">{{ $t('survey.fill.mobileUniapp') }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-form>
          <el-divider />
          <el-form label-width="40" v-for="q in fillQuestions" :key="q.id" class="fill-question">
            <el-form-item :label="q.questionCode">
              <div class="fill-question-title">
                {{ q.title }}
                <el-tag v-if="q.required" type="danger" size="small">{{ $t('survey.fill.required') }}</el-tag>
                <el-tag size="small">{{ questionTypeLabel(q.questionType) }}</el-tag>
              </div>
              <div v-if="q.description" class="fill-question-desc">{{ q.description }}</div>
              <!-- 单选 -->
              <el-radio-group v-if="q.questionType === 'SINGLE_CHOICE'" v-model="fillAnswers[q.id]">
                <el-radio v-for="opt in parseOptions(q.optionsJson)" :key="opt.code" :value="opt.code">
                  {{ opt.label }}
                </el-radio>
              </el-radio-group>
              <!-- 多选 -->
              <el-checkbox-group v-else-if="q.questionType === 'MULTI_CHOICE'" v-model="fillMultiAnswers[q.id]">
                <el-checkbox v-for="opt in parseOptions(q.optionsJson)" :key="opt.code" :value="opt.code">
                  {{ opt.label }}
                </el-checkbox>
              </el-checkbox-group>
              <!-- 单行文本 -->
              <el-input v-else-if="q.questionType === 'TEXT'" v-model="fillAnswers[q.id]" />
              <!-- 多行文本 -->
              <el-input v-else-if="q.questionType === 'TEXTAREA'" v-model="fillAnswers[q.id]" type="textarea" :rows="3" />
              <!-- 评分 -->
              <el-rate v-else-if="q.questionType === 'RATING'" v-model="fillAnswers[q.id]" :max="5" />
              <!-- 日期 -->
              <el-date-picker v-else-if="q.questionType === 'DATE'" v-model="fillAnswers[q.id]" type="date" value-format="YYYY-MM-DD" />
              <!-- 矩阵 (简化为多行单选) -->
              <div v-else-if="q.questionType === 'MATRIX'" class="matrix-placeholder">
                <el-tag size="small">{{ $t('survey.fill.matrixHint') }}</el-tag>
                <el-input v-model="fillAnswers[q.id]" :placeholder="$t('survey.fill.matrixPlaceholder')" />
              </div>
              <!-- 校验规则提示 -->
              <div v-if="q.validationJson" class="fill-validation-hint">
                <el-tag type="warning" size="small">{{ $t('survey.fill.validationLabel') }} {{ q.validationJson }}</el-tag>
              </div>
              <!-- 条件显隐提示 -->
              <div v-if="q.logicJson" class="fill-logic-hint">
                <el-tag type="warning" size="small">{{ $t('survey.fill.logicLabel') }} {{ q.logicJson }}</el-tag>
              </div>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="fillDialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
            <el-button type="primary" @click="handleSubmitFill">{{ $t('survey.fill.submit') }}</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- ==================== Tab 3: 答卷管理 ==================== -->
      <el-tab-pane :label="$t('survey.response.tabResponses')" name="responses">
        <div class="toolbar">
          <el-form :inline="true" :model="responseQuery" @submit.prevent>
            <el-form-item :label="$t('survey.response.surveyId')">
              <el-input v-model="responseQuery.surveyId" :placeholder="$t('survey.list.exactMatch')" clearable style="width: 220px" />
            </el-form-item>
            <el-form-item :label="$t('survey.response.respondentId')">
              <el-input v-model="responseQuery.respondentId" :placeholder="$t('survey.list.exactMatch')" clearable style="width: 180px" />
            </el-form-item>
            <el-form-item :label="$t('common.field.status')">
              <el-select v-model="responseQuery.status" :placeholder="$t('survey.list.all')" clearable style="width: 140px">
                <el-option :label="$t('survey.response.inProgress')" value="IN_PROGRESS" />
                <el-option :label="$t('survey.response.submittedStatus')" value="SUBMITTED" />
                <el-option :label="$t('survey.response.abandoned')" value="ABANDONED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Refresh" @click="responseQuery.pageNo = 1; loadResponseList()">{{ $t('common.action.list') }}</el-button>
            </el-form-item>
          </el-form>
          <el-button :icon="Refresh" @click="loadResponseList">{{ $t('common.action.refresh') }}</el-button>
        </div>

        <el-table :data="responseList" v-loading="responseLoading" border stripe>
          <el-table-column prop="responseNo" :label="$t('survey.response.responseNo')" width="200" />
          <el-table-column prop="surveyNo" :label="$t('survey.list.surveyNo')" width="180" />
          <el-table-column prop="surveyId" :label="$t('survey.response.surveyId')" width="180" show-overflow-tooltip />
          <el-table-column :label="$t('common.field.status')" width="110">
            <template #default="{ row }">
              <el-tag :type="responseStatusType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="respondentId" :label="$t('survey.response.respondent')" width="180" show-overflow-tooltip />
          <el-table-column :label="$t('survey.response.source')" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ row.source || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('survey.response.duration')" width="120">
            <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column prop="totalScore" :label="$t('survey.response.totalScore')" width="80" align="right" />
          <el-table-column prop="submittedTime" :label="$t('survey.response.submittedTime')" width="170" />
          <el-table-column :label="$t('survey.list.action')" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :icon="View" @click="openResponseDetail(row)">{{ $t('common.action.view') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pagination"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="responseTotal"
          :page-size="responseQuery.pageSize"
          :current-page="responseQuery.pageNo"
          :page-sizes="[10, 20, 50]"
          @current-change="handleResponsePageChange"
          @size-change="(s: number) => { responseQuery.pageSize = s; loadResponseList() }"
        />

        <!-- 答卷详情对话框 -->
        <el-dialog v-model="responseDetailVisible" :title="$t('survey.response.detailTitle')" width="900px" top="5vh">
          <el-descriptions v-if="responseDetail" :column="2" border size="small">
            <el-descriptions-item :label="$t('survey.response.responseNo')">{{ responseDetail.responseNo }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.list.surveyNo')">{{ responseDetail.surveyNo }}</el-descriptions-item>
            <el-descriptions-item :label="$t('common.field.status')">
              <el-tag :type="responseStatusType(responseDetail.status)" size="small">{{ responseDetail.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('survey.response.source')">{{ responseDetail.source || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.response.respondent')">{{ responseDetail.respondentId || $t('survey.response.anonymous') }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.response.totalScore')">{{ responseDetail.totalScore ?? '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.response.duration')">{{ formatDuration(responseDetail.durationMs) }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.response.clientIp')">{{ responseDetail.clientIp || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.response.startedTime')">{{ responseDetail.startedTime || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.response.submittedTime')">{{ responseDetail.submittedTime || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.response.userAgent')" :span="2">{{ responseDetail.userAgent || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('survey.form.remark')" :span="2">{{ responseDetail.remark || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider />
          <h4>{{ $t('survey.response.answerList') }}</h4>
          <el-table :data="answerList" v-loading="answerLoading" border stripe size="small">
            <el-table-column prop="questionCode" :label="$t('survey.question.code')" width="100" />
            <el-table-column prop="answerText" :label="$t('survey.response.answerText')" min-width="220" show-overflow-tooltip />
            <el-table-column prop="selectedOptions" :label="$t('survey.response.selectedOptions')" width="180" show-overflow-tooltip />
            <el-table-column prop="ratingScore" :label="$t('survey.response.ratingScore')" width="80" align="right" />
            <el-table-column :label="$t('survey.response.answerDuration')" width="100">
              <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
            </el-table-column>
          </el-table>
        </el-dialog>
      </el-tab-pane>

      <!-- ==================== Tab 4: AI 草稿生成 ==================== -->
      <el-tab-pane :label="$t('survey.ai.tabAiDraft')" name="ai-draft">
        <el-alert
          :title="$t('survey.ai.alertTitle')"
          type="info"
          :closable="false"
          show-icon
          class="ai-tip"
        />

        <el-form label-width="120px" class="ai-form">
          <el-form-item :label="$t('survey.ai.prompt')" required>
            <el-input
              v-model="aiPrompt"
              type="textarea"
              :rows="4"
              maxlength="1024"
              show-word-limit
              :placeholder="$t('survey.ai.promptPlaceholder')"
            />
          </el-form-item>
          <el-form-item :label="$t('survey.ai.expectedCount')">
            <el-input-number v-model="aiExpectedCount" :min="1" :max="20" />
            <span class="form-hint">{{ $t('survey.ai.defaultCountHint') }}</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="MagicStick" :loading="aiGenerating" @click="handleGenerateAiDraft">
              {{ $t('survey.ai.generate') }}
            </el-button>
            <el-button
              v-if="aiDraftResult.length > 0"
              type="success"
              :icon="Check"
              @click="openAiAdoptDialog"
            >{{ $t('survey.ai.adopt') }} ({{ aiDraftResult.length }} {{ $t('survey.ai.items') }})</el-button>
          </el-form-item>
        </el-form>

        <el-divider />

        <h4 v-if="aiDraftResult.length > 0">{{ $t('survey.ai.draftPreview') }} ({{ aiDraftResult.length }} {{ $t('survey.ai.items') }})</h4>
        <el-table v-if="aiDraftResult.length > 0" :data="aiDraftResult" border stripe size="small">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="questionCode" :label="$t('survey.question.code')" width="100" />
          <el-table-column :label="$t('survey.question.type')" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ questionTypeLabel(row.questionType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" :label="$t('survey.question.title')" min-width="220" show-overflow-tooltip />
          <el-table-column :label="$t('survey.question.required')" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.required ? 'danger' : 'info'" size="small">
                {{ row.required ? $t('survey.question.requiredYes') : $t('survey.question.requiredNo') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="optionsJson" :label="$t('survey.question.optionsJson')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="validationJson" :label="$t('survey.question.validation')" width="150" show-overflow-tooltip />
          <el-table-column prop="logicJson" :label="$t('survey.question.logic')" width="150" show-overflow-tooltip />
        </el-table>

        <!-- 采纳入库对话框 -->
        <el-dialog v-model="aiAdoptDialogVisible" :title="$t('survey.ai.adoptTitle')" width="600px">
          <el-form label-width="160px">
            <el-form-item :label="$t('survey.ai.targetSurvey')" required>
              <el-select v-model="aiAdoptSurveyId" :placeholder="$t('survey.ai.targetSurveyPlaceholder')" filterable style="width: 380px">
                <el-option
                  v-for="s in adoptSurveyOptions"
                  :key="s.id"
                  :label="`${s.surveyNo} - ${s.title}`"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('survey.ai.pendingCount')">
              <el-tag type="warning">{{ aiDraftResult.length }} {{ $t('survey.ai.items') }}</el-tag>
            </el-form-item>
          </el-form>
          <el-alert
            :title="$t('survey.ai.adoptAlertTitle')"
            type="warning"
            :closable="false"
            show-icon
          />
          <template #footer>
            <el-button @click="aiAdoptDialogVisible = false">{{ $t('common.action.cancel') }}</el-button>
            <el-button type="primary" :loading="aiAdopting" @click="handleAdoptAiDraft">{{ $t('survey.ai.confirmAdopt') }}</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.survey-ops {
  padding: 0;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}
.toolbar .el-form--inline {
  margin: 0;
}
.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.stats-cards {
  margin-bottom: 16px;
}
.stat-card {
  text-align: center;
}
.stat-label {
  font-size: 13px;
  color: var(--yutong-text-regular, #606266);
  margin-bottom: 6px;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--yutong-primary, #409eff);
}
.text-success {
  color: var(--el-color-success, #67c23a);
}
.text-warning {
  color: var(--el-color-warning, #e6a23c);
}
.stats-detail {
  margin-bottom: 16px;
}
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  min-height: 160px;
  padding: 8px 0;
}
.trend-bar {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  gap: 4px;
}
.trend-bar-value {
  font-size: 12px;
  color: var(--yutong-text-regular, #606266);
}
.trend-bar-bar {
  width: 28px;
  background: linear-gradient(180deg, #409eff 0%, #67c23a 100%);
  border-radius: 3px 3px 0 0;
  min-height: 2px;
}
.trend-bar-date {
  font-size: 11px;
  color: var(--yutong-text-regular, #909399);
}
.trend-empty {
  width: 100%;
  text-align: center;
  color: var(--yutong-text-regular, #909399);
  padding: 40px 0;
}
.stats-category {
  margin-bottom: 16px;
}
.form-hint {
  margin-left: 8px;
  color: var(--yutong-text-regular, #909399);
  font-size: 12px;
}
.designer-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.fill-tip {
  margin-bottom: 12px;
}
.fill-question {
  margin-bottom: 16px;
  border-bottom: 1px dashed var(--yutong-border-light, #ebeef5);
  padding-bottom: 8px;
}
.fill-question-title {
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.fill-question-desc {
  color: var(--yutong-text-regular, #606266);
  font-size: 13px;
  margin-bottom: 8px;
}
.fill-validation-hint,
.fill-logic-hint {
  margin-top: 4px;
}
.matrix-placeholder {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ai-tip {
  margin-bottom: 16px;
}
.ai-form {
  max-width: 800px;
}
</style>
