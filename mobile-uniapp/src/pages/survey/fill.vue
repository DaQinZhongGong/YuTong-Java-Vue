<template>
  <view class="fill-page" v-if="survey">
    <view class="fill-header">
      <text class="fill-header__title">{{ survey.title }}</text>
      <text class="fill-header__desc" v-if="survey.description">{{ survey.description }}</text>
      <view class="fill-header__meta">
        <text class="meta-tag">编号: {{ survey.surveyNo }}</text>
        <text class="meta-tag" v-if="survey.anonymous">匿名</text>
        <text class="meta-tag" v-else>实名</text>
      </view>
    </view>

    <view v-if="loading" class="loading-hint">加载问卷中...</view>

    <template v-else>
      <view v-for="(q, idx) in visibleQuestions" :key="q.id" class="question-block">
        <view class="question-title">
          <text class="question-no">{{ idx + 1 }}.</text>
          <text class="question-text">{{ q.title }}</text>
          <text class="question-required" v-if="q.required">*</text>
        </view>
        <view class="question-desc" v-if="q.description">{{ q.description }}</view>

        <!-- 单选 SINGLE_CHOICE -->
        <radio-group v-if="q.questionType === 'SINGLE_CHOICE'" class="question-options" @change="onSingleChange(q, $event)">
          <label v-for="opt in parseOptions(q.optionsJson)" :key="opt.code" class="option-item">
            <radio :value="opt.code" :checked="answers[q.id] === opt.code" />
            <text class="option-label">{{ opt.label }}</text>
          </label>
        </radio-group>

        <!-- 多选 MULTI_CHOICE -->
        <checkbox-group v-else-if="q.questionType === 'MULTI_CHOICE'" class="question-options" @change="onMultiChange(q, $event)">
          <label v-for="opt in parseOptions(q.optionsJson)" :key="opt.code" class="option-item">
            <checkbox :value="opt.code" :checked="isMultiChecked(q, opt.code)" />
            <text class="option-label">{{ opt.label }}</text>
          </label>
        </checkbox-group>

        <!-- 单行文本 TEXT -->
        <input
          v-else-if="q.questionType === 'TEXT'"
          class="question-input"
          type="text"
          v-model="answers[q.id]"
          :placeholder="getPlaceholder(q)"
        />

        <!-- 多行文本 TEXTAREA -->
        <textarea
          v-else-if="q.questionType === 'TEXTAREA'"
          class="question-textarea"
          v-model="answers[q.id]"
          :placeholder="getPlaceholder(q)"
          :maxlength="500"
        />

        <!-- 评分 RATING -->
        <view v-else-if="q.questionType === 'RATING'" class="rating-row">
          <view
            v-for="n in 5"
            :key="n"
            class="rating-star"
            :class="{ 'rating-star--active': Number(answers[q.id]) >= n }"
            @click="setRating(q, n)"
          >
            <text>{{ Number(answers[q.id]) >= n ? '★' : '☆' }}</text>
          </view>
          <text class="rating-value" v-if="answers[q.id]">{{ answers[q.id] }} 分</text>
        </view>

        <!-- 日期 DATE -->
        <picker
          v-else-if="q.questionType === 'DATE'"
          mode="date"
          :value="answers[q.id] || ''"
          @change="onDateChange(q, $event)"
        >
          <view class="question-input question-input--picker">
            <text v-if="answers[q.id]">{{ answers[q.id] }}</text>
            <text v-else class="placeholder">请选择日期</text>
          </view>
        </picker>

        <!-- 矩阵 MATRIX (简化为行列单选) -->
        <view v-else-if="q.questionType === 'MATRIX'" class="matrix-block">
          <view class="matrix-header">
            <text class="matrix-cell"></text>
            <text v-for="col in matrixCols(q)" :key="col" class="matrix-cell matrix-cell--head">{{ col }}</text>
          </view>
          <view v-for="row in matrixRows(q)" :key="row" class="matrix-row">
            <text class="matrix-cell matrix-cell--row">{{ row }}</text>
            <view
              v-for="col in matrixCols(q)"
              :key="col"
              class="matrix-cell"
              @click="setMatrixValue(q, row, col)"
            >
              <view class="matrix-radio" :class="{ 'matrix-radio--active': getMatrixValue(q) === `${row}:${col}` }"></view>
            </view>
          </view>
        </view>

        <!-- 校验规则提示 -->
        <view class="validation-hint" v-if="q.validationJson">
          <text class="hint-text">校验: {{ formatValidationHint(q.validationJson) }}</text>
        </view>
      </view>

      <EmptyState v-if="questions.length === 0" text="问卷暂无题目" icon="📋" />

      <BottomActionBar v-if="questions.length > 0">
        <button class="action-btn action-btn--submit" :disabled="submitting" @click="handleSubmit">
          {{ submitting ? '提交中...' : '提交答卷' }}
        </button>
      </BottomActionBar>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue';
import { onMounted } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import {
  getSurveyDetail,
  startResponse,
  submitResponse,
  parseOptions,
  parseLogicRules,
  type SurSurvey,
  type SurQuestion,
  type AnswerItem,
} from '@/api/survey';
import EmptyState from '@/components/EmptyState.vue';
import BottomActionBar from '@/components/BottomActionBar.vue';

const survey = ref<SurSurvey | null>(null);
const questions = ref<SurQuestion[]>([]);
const loading = ref(false);
const submitting = ref(false);
const surveyId = ref('');
const answers = reactive<Record<string, string>>({});
const multiAnswers = reactive<Record<string, string[]>>({});
const startedResponseId = ref('');

const visibleQuestions = computed(() => {
  return questions.value.filter((q) => !isHidden(q));
});

function isHidden(q: SurQuestion): boolean {
  const rules = parseLogicRules(q.logicJson);
  for (const r of rules) {
    if (r.action !== 'HIDE') continue;
    const targetQ = questions.value.find((x) => x.questionCode === r.questionCode);
    if (!targetQ) continue;
    const targetVal = answers[targetQ.id];
    if (targetVal == null || targetVal === '') continue;
    if (compareValue(targetVal, r.operator, r.value)) {
      return true;
    }
  }
  return false;
}

function compareValue(actual: string, operator: string, expected: string): boolean {
  switch (operator) {
    case 'EQ': return actual === expected;
    case 'NE': return actual !== expected;
    case 'IN': return expected.split(',').map((s) => s.trim()).includes(actual);
    case 'NOT_IN': return !expected.split(',').map((s) => s.trim()).includes(actual);
    case 'CONTAINS': return actual.includes(expected);
    case 'GT': return Number(actual) > Number(expected);
    case 'GTE': return Number(actual) >= Number(expected);
    case 'LT': return Number(actual) < Number(expected);
    case 'LTE': return Number(actual) <= Number(expected);
    default: return false;
  }
}

function onSingleChange(q: SurQuestion, e: any) {
  answers[q.id] = e.detail.value;
}

function onMultiChange(q: SurQuestion, e: any) {
  const vals: string[] = e.detail.value || [];
  multiAnswers[q.id] = vals;
  answers[q.id] = vals.join(',');
}

function isMultiChecked(q: SurQuestion, code: string): boolean {
  return (multiAnswers[q.id] || []).includes(code);
}

function setRating(q: SurQuestion, n: number) {
  answers[q.id] = String(n);
}

function onDateChange(q: SurQuestion, e: any) {
  answers[q.id] = e.detail.value;
}

function getPlaceholder(q: SurQuestion): string {
  if (q.validationJson) {
    try {
      const v = JSON.parse(q.validationJson);
      if (v.minLength || v.maxLength) return `请输入 ${v.minLength || 0}-${v.maxLength || 500} 字`;
      if (v.regex) return '按格式要求输入';
    } catch {
      // ignore
    }
  }
  return '请输入';
}

function formatValidationHint(json: string): string {
  try {
    const v = JSON.parse(json);
    const parts: string[] = [];
    if (v.minLength != null) parts.push(`最少 ${v.minLength} 字`);
    if (v.maxLength != null) parts.push(`最多 ${v.maxLength} 字`);
    if (v.minSelect != null) parts.push(`最少选 ${v.minSelect} 项`);
    if (v.maxSelect != null) parts.push(`最多选 ${v.maxSelect} 项`);
    if (v.minValue != null) parts.push(`最小 ${v.minValue}`);
    if (v.maxValue != null) parts.push(`最大 ${v.maxValue}`);
    if (v.regex) parts.push(`正则 ${v.regex}`);
    return parts.join(', ');
  } catch {
    return json;
  }
}

function matrixRows(q: SurQuestion): string[] {
  try {
    const m = JSON.parse(q.matrixJson || '{}');
    return m.rows || [];
  } catch {
    return [];
  }
}

function matrixCols(q: SurQuestion): string[] {
  try {
    const m = JSON.parse(q.matrixJson || '{}');
    return m.cols || [];
  } catch {
    return [];
  }
}

function setMatrixValue(q: SurQuestion, row: string, col: string) {
  answers[q.id] = `${row}:${col}`;
}

function getMatrixValue(q: SurQuestion): string {
  return answers[q.id] || '';
}

async function loadDetail() {
  if (!surveyId.value) return;
  loading.value = true;
  try {
    const detail = await getSurveyDetail(surveyId.value);
    survey.value = detail.survey;
    questions.value = detail.questions || [];
    // 启动答卷 (创建 IN_PROGRESS 记录, 校验填写次数限制)
    try {
      const resp = await startResponse(surveyId.value, 'MOBILE_UNIAPP');
      startedResponseId.value = resp.id;
    } catch (e) {
      // 启动失败 (可能达到填写次数上限), 允许用户继续看问卷但提交时会被服务端拦截
      console.warn('Failed to start response:', e);
    }
  } catch (e) {
    console.error('Failed to load survey detail:', e);
    uni.showToast({ title: '问卷加载失败', icon: 'none' });
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  if (!survey.value) return;
  const sid = survey.value.id;
  const answerItems: AnswerItem[] = [];
  for (const q of visibleQuestions.value) {
    const value = answers[q.id] || '';
    if (q.required && !value) {
      uni.showToast({ title: `请填写: ${q.title}`, icon: 'none' });
      return;
    }
    if (!value) continue;
    const item: AnswerItem = {
      questionId: q.id,
      questionCode: q.questionCode,
      answerValue: value,
      answerText: value,
    };
    if (q.questionType === 'RATING') {
      item.ratingScore = Number(value);
    } else if (q.questionType === 'SINGLE_CHOICE' || q.questionType === 'MULTI_CHOICE') {
      item.selectedOptions = JSON.stringify(value.split(','));
    }
    answerItems.push(item);
  }
  if (answerItems.length === 0) {
    uni.showToast({ title: '请至少填写一道题目', icon: 'none' });
    return;
  }
  submitting.value = true;
  try {
    const resp = await submitResponse({
      surveyId: sid,
      source: 'MOBILE_UNIAPP',
      answers: answerItems,
    });
    uni.redirectTo({
      url: `/pages/survey/done?responseNo=${encodeURIComponent(resp.responseNo)}&surveyTitle=${encodeURIComponent(survey.value.title)}`,
    });
  } catch (e: any) {
    uni.showToast({ title: '提交失败: ' + (e?.message || '未知错误'), icon: 'none' });
  } finally {
    submitting.value = false;
  }
}

onLoad((options: any) => {
  surveyId.value = options?.id || '';
});
onMounted(() => loadDetail());
</script>

<style scoped>
.fill-page {
  padding: 24rpx;
  padding-bottom: 160rpx; /* 给底部按钮留空间 */
}
.fill-header {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.fill-header__title {
  display: block;
  font-size: 34rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8rpx;
}
.fill-header__desc {
  display: block;
  font-size: 26rpx;
  color: #606266;
  line-height: 1.5;
  margin-bottom: 12rpx;
}
.fill-header__meta {
  display: flex;
  gap: 16rpx;
}
.meta-tag {
  font-size: 22rpx;
  color: #909399;
  background: #f5f7fa;
  padding: 4rpx 12rpx;
  border-radius: 4rpx;
}
.loading-hint {
  text-align: center;
  color: #909399;
  padding: 80rpx 0;
}
.question-block {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.question-title {
  display: flex;
  align-items: flex-start;
  gap: 8rpx;
  margin-bottom: 8rpx;
}
.question-no {
  font-size: 28rpx;
  font-weight: 600;
  color: #3c8772;
}
.question-text {
  flex: 1;
  font-size: 28rpx;
  font-weight: 600;
  color: #303133;
  line-height: 1.5;
}
.question-required {
  color: #f56c6c;
  font-size: 28rpx;
}
.question-desc {
  font-size: 24rpx;
  color: #909399;
  margin-bottom: 16rpx;
  line-height: 1.5;
}
.question-options {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.option-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 8rpx 0;
}
.option-label {
  font-size: 28rpx;
  color: #303133;
}
.question-input {
  width: 100%;
  height: 72rpx;
  padding: 0 20rpx;
  background: #f5f7fa;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #303133;
  box-sizing: border-box;
}
.question-input--picker {
  display: flex;
  align-items: center;
  line-height: 72rpx;
}
.placeholder {
  color: #c0c4cc;
}
.question-textarea {
  width: 100%;
  min-height: 160rpx;
  padding: 16rpx 20rpx;
  background: #f5f7fa;
  border-radius: 8rpx;
  font-size: 28rpx;
  color: #303133;
  box-sizing: border-box;
}
.rating-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.rating-star {
  font-size: 56rpx;
  color: #c0c4cc;
  padding: 8rpx;
}
.rating-star--active {
  color: #f7ba2a;
}
.rating-value {
  margin-left: 16rpx;
  font-size: 26rpx;
  color: #f7ba2a;
}
.matrix-block {
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1rpx solid #ebeef5;
  border-radius: 8rpx;
  overflow: hidden;
}
.matrix-header,
.matrix-row {
  display: flex;
  border-bottom: 1rpx solid #ebeef5;
}
.matrix-header {
  background: #f5f7fa;
}
.matrix-cell {
  flex: 1;
  padding: 16rpx 8rpx;
  text-align: center;
  font-size: 24rpx;
  color: #606266;
  border-right: 1rpx solid #ebeef5;
}
.matrix-cell--head {
  font-weight: 600;
}
.matrix-cell--row {
  text-align: left;
  font-weight: 500;
}
.matrix-radio {
  width: 32rpx;
  height: 32rpx;
  border: 2rpx solid #dcdfe6;
  border-radius: 50%;
  margin: 0 auto;
}
.matrix-radio--active {
  border-color: #3c8772;
  background: #3c8772;
}
.validation-hint {
  margin-top: 12rpx;
  padding: 8rpx 12rpx;
  background: #fdf6ec;
  border-radius: 4rpx;
}
.hint-text {
  font-size: 22rpx;
  color: #e6a23c;
}
.action-btn {
  flex: 1;
  height: 80rpx;
  border: none;
  border-radius: 8rpx;
  font-size: 28rpx;
}
.action-btn--submit {
  background: #3c8772;
  color: #fff;
}
</style>
