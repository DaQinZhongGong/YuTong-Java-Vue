<template>
  <view class="handle-page">
    <YtCard>
      <text class="handle-title">{{ title }}</text>
      <text class="handle-action">{{ action === 'approve' ? '审核通过' : '审核驳回' }}</text>
    </YtCard>

    <YtCard>
      <text class="field-label">审核意见</text>
      <!-- P1-3: 常用意见快捷短语，点击覆盖填入意见输入框 -->
      <view class="quick-phrases" role="group" aria-label="常用意见快捷短语">
        <text
          v-for="phrase in quickPhrases"
          :key="phrase"
          class="quick-phrase"
          role="button"
          :aria-label="`填入意见：${phrase}`"
          @click="applyPhrase(phrase)"
        >{{ phrase }}</text>
      </view>
      <!-- WCAG 3.3.2 Labels + 4.1.2: textarea 关联 aria-label -->
      <textarea class="opinion-input" v-model="opinion" placeholder="请输入审核意见" :maxlength="500" aria-label="审核意见" />
    </YtCard>

    <BottomActionBar>
      <button class="action-btn action-btn--cancel" @click="goBack">取消</button>
      <button class="action-btn" :class="action === 'approve' ? 'action-btn--approve' : 'action-btn--reject'" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中...' : '确认提交' }}
      </button>
    </BottomActionBar>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue';
import { onBackPress } from '@dcloudio/uni-app';
import { approveMobileRequest, rejectMobileRequest } from '@/api/mobile';
import { track } from '@/utils/tracker';
import { saveDraft, loadDraft, clearDraft, hasDraft } from '@/utils/draft';
import type { RequestError } from '@/utils/request';
import YtCard from '@/components/YtCard.vue';
import BottomActionBar from '@/components/BottomActionBar.vue';

const id = ref('');
const action = ref('approve');
const version = ref(0);
const title = ref('');
const opinion = ref('');
const submitting = ref(false);

// P1-3: 常用审核意见快捷短语
const quickPhrases = ['同意', '情况属实，同意', '已核实，通过', '驳回，资料不齐'];
function applyPhrase(phrase: string): void {
  opinion.value = phrase;
}

// 弱网/离线策略: 表单草稿
// 维度: 页面(biz_handle) + 业务类型(request:<id>) + 用户(userId)，详见 utils/draft.ts
const DRAFT_PAGE = 'biz_handle';
const draftBizType = computed(() => `request:${id.value || 'unknown'}`);
let saveTimer: ReturnType<typeof setTimeout> | null = null;

function scheduleSaveDraft(): void {
  if (!id.value || submitting.value) return;
  if (saveTimer) clearTimeout(saveTimer);
  // 防抖 600ms 自动存草稿
  saveTimer = setTimeout(() => {
    saveDraft(DRAFT_PAGE, draftBizType.value, { opinion: opinion.value });
  }, 600);
}

// 输入时防抖自动保存草稿
watch(opinion, scheduleSaveDraft);

onMounted(() => {
  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1] as any;
  const options = currentPage?.options || currentPage?.$page?.options || {};
  id.value = options.id || '';
  action.value = options.action || 'approve';
  version.value = parseInt(options.version) || 0;
  title.value = options.title ? decodeURIComponent(options.title) : '';

  // 弱网/离线策略: 进入页面检测未提交草稿，提示是否恢复
  if (id.value && hasDraft(DRAFT_PAGE, draftBizType.value)) {
    uni.showModal({
      title: '草稿恢复',
      content: '检测到未提交的审核意见草稿，是否恢复？',
      confirmText: '恢复',
      cancelText: '放弃',
      success: (res) => {
        if (res.confirm) {
          const d = loadDraft<{ opinion: string }>(DRAFT_PAGE, draftBizType.value);
          if (d && d.data.opinion) {
            opinion.value = d.data.opinion;
          }
        } else {
          // 用户选择放弃，清除草稿
          clearDraft(DRAFT_PAGE, draftBizType.value);
        }
      },
    });
  }
});

async function submit() {
  if (action.value === 'reject' && !opinion.value.trim()) {
    uni.showToast({ title: '驳回时必须填写意见', icon: 'none' });
    return;
  }
  submitting.value = true;
  // GA2-18: 审核提交点击埋点 (设计来源 94 号文档业务事件字典 mobile.audit.submit)
  // 隐私约束: 不采集 opinion 正文, 只上报 opinionLength (脱敏管道对 opinion 字段删除兜底)
  const startTime = Date.now();
  track('mobile.audit.submit.click', {
    bizType: 'request', bizId: id.value,
    payload: {
      action: action.value,
      opinionLength: opinion.value.length,
      hasOpinion: !!opinion.value.trim(),
    },
  });
  try {
    const params = {
      opinion: opinion.value || undefined,
      version: version.value || undefined,
      idempotencyKey: `mobile-${id.value}-${Date.now()}`,
    };
    if (action.value === 'approve') {
      await approveMobileRequest(id.value, params);
    } else {
      await rejectMobileRequest(id.value, { ...params, opinion: opinion.value });
    }
    // GA2-18: 审核提交成功埋点
    track('mobile.audit.submit.success', {
      bizType: 'request', bizId: id.value,
      durationMs: Date.now() - startTime,
      payload: { action: action.value, opinionLength: opinion.value.length },
    });
    uni.showToast({ title: '操作成功', icon: 'success' });
    // 弱网/离线策略: 提交成功后清除草稿
    clearDraft(DRAFT_PAGE, draftBizType.value);
    // 通知详情页刷新（详情页 onLoad 注册 uni.$on('biz-detail-refresh')）
    uni.$emit('biz-detail-refresh');
    setTimeout(() => {
      // 从 handle 返回 detail 为 1 层；若直接从外部进入 handle 无上一页，回退到待办列表
      uni.navigateBack({
        delta: 1,
        fail: () => {
          uni.switchTab({ url: '/pages/todo/todo' });
        },
      });
    }, 1500);
  } catch (e: any) {
    // GA2-18: 审核提交失败埋点 (不采集 opinion 正文)
    track('mobile.audit.submit.failed', {
      result: 'failed', bizType: 'request', bizId: id.value,
      errorCode: e?.message || 'AUDIT_SUBMIT_FAILED',
      durationMs: Date.now() - startTime,
      payload: { action: action.value, opinionLength: opinion.value.length },
    });
    console.error('Submit failed:', e);
    // P1-2: 409 单据状态冲突处理
    const reqErr = e as RequestError;
    if (reqErr?.statusCode === 409 || reqErr?.code === 'BIZ_REQUEST_STATUS_CHANGED') {
      uni.showModal({
        title: '单据状态已变化',
        content: '请刷新后再处理',
        confirmText: '刷新详情',
        showCancel: false,
        success: () => {
          // 返回详情页，详情页 onShow 会自动刷新
          uni.navigateBack();
        },
      });
    } else {
      uni.showToast({ title: e?.message || '操作失败', icon: 'none' });
    }
  } finally {
    submitting.value = false;
  }
}

// P1-2: 提交中禁用返回手势，避免重复提交
onBackPress(() => {
  if (submitting.value) {
    uni.showToast({ title: '提交中请稍候', icon: 'none' });
    return true; // 阻止返回
  }
  return false; // 允许返回
});

function goBack() {
  uni.navigateBack();
}
</script>

<style scoped>
.handle-page {
  padding: 24rpx;
  padding-bottom: 140rpx;
}
.handle-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8rpx;
}
.handle-action {
  display: block;
  font-size: 28rpx;
  color: #3c8772;
}
.field-label {
  display: block;
  font-size: 28rpx;
  color: #606266;
  margin-bottom: 16rpx;
}
/* P1-3: 快捷短语 chip */
.quick-phrases {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.quick-phrase {
  padding: 8rpx 24rpx;
  font-size: 26rpx;
  color: #3c8772;
  border: 1rpx solid #3c8772;
  border-radius: 32rpx;
  background: #fff;
  line-height: 1.4;
}
.opinion-input {
  width: 100%;
  min-height: 200rpx;
  padding: 16rpx;
  font-size: 28rpx;
  border: 1rpx solid #dcdfe6;
  border-radius: 8rpx;
  box-sizing: border-box;
}
.action-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  text-align: center;
  border-radius: 8rpx;
  font-size: 30rpx;
  border: none;
}
.action-btn--approve {
  background: #3c8772;
  color: #fff;
}
.action-btn--reject {
  background: #f56c6c;
  color: #fff;
}
.action-btn--cancel {
  background: #f5f7fa;
  color: #606266;
}
</style>
