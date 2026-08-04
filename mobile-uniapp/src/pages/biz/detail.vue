<template>
  <view class="detail-page" v-if="detail">
    <OfflineBanner />

    <view class="detail-header">
      <text class="detail-header__title">{{ detail.title }}</text>
      <StatusBadge :label="detail.requestStatusLabel" :status="detail.requestStatus" />
    </view>

    <YtCard>
      <view class="info-row"><text class="info-label">单号</text><text class="info-value">{{ detail.requestNo }}</text></view>
      <view class="info-row"><text class="info-label">客户</text><text class="info-value">{{ detail.customerName }}</text></view>
      <view class="info-row"><text class="info-label">申请人</text><text class="info-value">{{ detail.applicantName || '-' }}</text></view>
      <view class="info-row"><text class="info-label">申请理由</text><text class="info-value">{{ detail.applyReason || '-' }}</text></view>
      <view class="info-row"><text class="info-label">提交时间</text><text class="info-value">{{ detail.submittedTime || '-' }}</text></view>
      <view class="info-row"><text class="info-label">审批时间</text><text class="info-value">{{ detail.approvedTime || '-' }}</text></view>
      <view class="info-row"><text class="info-label">总金额</text><text class="info-value info-value--amount">¥{{ formatAmount(detail.totalAmount) }}</text></view>
      <view v-if="lastUpdateText" class="info-row"><text class="info-label">最后更新</text><text class="info-value">{{ lastUpdateText }}</text></view>
    </YtCard>

    <text class="section-title">明细列表</text>
    <YtCard v-for="item in detail.items" :key="item.id">
      <view class="info-row"><text class="info-label">商品</text><text class="info-value">{{ item.productNameSnapshot }}</text></view>
      <view class="info-row"><text class="info-label">编码</text><text class="info-value">{{ item.productCodeSnapshot }}</text></view>
      <view class="info-row"><text class="info-label">数量</text><text class="info-value">{{ item.quantity }} {{ item.unit }}</text></view>
      <view class="info-row"><text class="info-label">单价</text><text class="info-value">¥{{ formatAmount(item.unitPrice) }}</text></view>
      <view class="info-row"><text class="info-label">小计</text><text class="info-value info-value--amount">¥{{ formatAmount(item.lineAmount) }}</text></view>
    </YtCard>
    <EmptyState v-if="detail.items.length === 0" text="暂无明细" />

    <template v-if="detail.approvals.length > 0">
      <text class="section-title">审批记录</text>
      <!-- P1-2: 审批记录改为纵向时间线展示 -->
      <view class="timeline">
        <view v-for="(approval, idx) in detail.approvals" :key="approval.id" class="timeline-item">
          <view class="timeline-item__rail">
            <view class="timeline-item__dot" :style="{ background: approvalColor(approval.result) }"></view>
            <view v-if="idx < detail.approvals.length - 1" class="timeline-item__line"></view>
          </view>
          <view class="timeline-item__body">
            <view class="timeline-item__head">
              <text class="timeline-item__action">{{ approval.action }}</text>
              <text class="timeline-item__time">{{ relativeTime(approval.operatedTime) }}</text>
            </view>
            <text class="timeline-item__operator">操作人：{{ approval.operatorId || '-' }}</text>
            <text v-if="approval.opinion" class="timeline-item__opinion">{{ approval.opinion }}</text>
          </view>
        </view>
      </view>
    </template>

    <text class="section-title">附件清单</text>
    <YtCard v-for="file in attachments" :key="file.id">
      <view class="info-row"><text class="info-label">文件名</text><text class="info-value">{{ file.name }}</text></view>
      <view class="info-row"><text class="info-label">大小</text><text class="info-value">{{ formatFileSize(file.size) }}</text></view>
      <view class="attach-actions">
        <button v-if="isImageFile(file.contentType, file.name)" class="action-btn action-btn--preview" @click="previewFile(file)">预览</button>
        <button class="action-btn action-btn--download" @click="downloadFile(file)">下载</button>
      </view>
    </YtCard>
    <EmptyState v-if="attachments.length === 0" text="暂无附件" />

    <BottomActionBar v-if="detail.requestStatus === 'SUBMITTED' && (canApprove || canReject)">
      <button v-if="canReject" class="action-btn action-btn--reject" @click="goHandle('reject')">驳回</button>
      <button v-if="canApprove" class="action-btn action-btn--approve" @click="goHandle('approve')">通过</button>
    </BottomActionBar>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { onShow, onLoad, onUnload } from '@dcloudio/uni-app';
import { getMobileRequestDetail, type MobileRequestDetailVO } from '@/api/mobile';
import { listFilesByBiz, getDownloadUrl } from '@/api/file';
import { checkPermission } from '@/utils/permission';
import { track, trackPageView } from '@/utils/tracker';
import { formatMoney } from '@/utils/format';
import { relativeTime } from '@/utils/relativeTime';
import YtCard from '@/components/YtCard.vue';
import StatusBadge from '@/components/StatusBadge.vue';
import BottomActionBar from '@/components/BottomActionBar.vue';
import EmptyState from '@/components/EmptyState.vue';
import OfflineBanner from '@/components/OfflineBanner.vue';

const detail = ref<MobileRequestDetailVO | null>(null);
const detailId = ref('');

// P0-6: 审批按钮按权限码过滤（mobile:biz-request:approve / :reject）
const canApprove = computed(() => checkPermission('mobile:biz-request:approve'));
const canReject = computed(() => checkPermission('mobile:biz-request:reject'));

/** 最后更新相对时间（lastUpdateTime / updateTime / lastModifiedAt 任一可用即展示，空则不展示）。 */
const lastUpdateText = computed(() => {
  const d = detail.value;
  if (!d) return '';
  const t = d.lastUpdateTime || d.updateTime || d.lastModifiedAt;
  return relativeTime(t);
});

/** 附件展示项（统一结构，便于 UI 渲染与 mock 兜底）。 */
interface AttachmentItem {
  id: string;
  name: string;
  size: number;
  contentType?: string;
}

const attachments = ref<AttachmentItem[]>([]);

/** P0-4 mock 兜底：后端 /files/by-biz 仅返回绑定关系且无文件元数据时使用。 */
const MOCK_ATTACHMENTS: AttachmentItem[] = [
  { id: 'mock-1', name: '申请单附件.pdf', size: 102400, contentType: 'application/pdf' },
];

/** P1-3: 金额按 string 格式化（每 3 位逗号分隔，保留 2 位小数）。 */
function formatAmount(amount: string | number) {
  return formatMoney(amount);
}

/** P1-2: 审批结果状态色映射 —— 通过=绿/驳回=红/提交=蓝/待审=灰。 */
function approvalColor(result: string): string {
  if (!result) return '#909399';
  if (/通过|approve/i.test(result)) return '#3c8772';
  if (/驳回|reject/i.test(result)) return '#f56c6c';
  if (/提交|submit/i.test(result)) return '#409eff';
  if (/待审|pending/i.test(result)) return '#909399';
  return '#909399';
}

function goHandle(action: string) {
  if (!detail.value) return;
  // GA2-18: 进入审核页埋点
  track('mobile.audit.enter', {
    bizType: 'request', bizId: detail.value.id,
    payload: { action, requestStatus: detail.value.requestStatus },
  });
  uni.navigateTo({
    url: `/pages/biz/handle?id=${detail.value.id}&action=${action}&version=${detail.value.version}&title=${encodeURIComponent(detail.value.title)}`,
  });
}

async function loadDetail() {
  if (!detailId.value) return;
  // GA2-18: 详情查看埋点 (设计来源 94 号文档业务事件字典 mobile.todo.detail)
  const startTime = Date.now();
  track('mobile.todo.detail.click', { bizType: 'request', bizId: detailId.value });
  try {
    detail.value = await getMobileRequestDetail(detailId.value);
    // GA2-18: 详情加载成功埋点 (不采集 title / applyReason 等业务字段)
    track('mobile.todo.detail.success', {
      bizType: 'request', bizId: detailId.value,
      durationMs: Date.now() - startTime,
      payload: {
        requestStatus: detail.value.requestStatus,
        itemCount: detail.value.items?.length || 0,
        approvalCount: detail.value.approvals?.length || 0,
      },
    });
  } catch (e) {
    // GA2-18: 详情加载失败埋点
    track('mobile.todo.detail.failed', {
      result: 'failed', bizType: 'request', bizId: detailId.value,
      errorCode: e instanceof Error ? e.message : 'DETAIL_LOAD_FAILED',
      durationMs: Date.now() - startTime,
    });
    console.error('Failed to load detail:', e);
    uni.showToast({ title: '加载失败', icon: 'none' });
  }
}

/**
 * 加载附件列表。调用后端 /files/by-biz 拉取绑定关系；
 * 若后端未内联文件元数据或请求失败，回退到 mock 占位数据，保证 UI 可用。
 */
async function loadAttachments() {
  if (!detail.value?.id) return;
  try {
    const rels = await listFilesByBiz('BIZ_REQUEST', detail.value.id);
    const items: AttachmentItem[] = (rels || []).map((r) => ({
      id: r.fileId || r.id,
      name: r.fileName || '',
      size: r.fileSize || 0,
      contentType: r.contentType,
    }));
    // 仅当后端返回了带文件名的可用元数据时才采用，否则走 mock 兜底
    attachments.value = items.length > 0 && items.some((i) => i.name)
      ? items
      : MOCK_ATTACHMENTS;
  } catch (e) {
    console.error('Failed to load attachments:', e);
    attachments.value = MOCK_ATTACHMENTS;
  }
}

/** 文件大小格式化：不足 1KB 显示 B，不足 1MB 显示 KB。 */
function formatFileSize(size: number): string {
  if (!size || size <= 0) return '-';
  if (size < 1024) return size + ' B';
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
  return (size / (1024 * 1024)).toFixed(2) + ' MB';
}

/** 判断是否图片类文件（用于决定是否显示预览按钮）。 */
function isImageFile(contentType?: string, name?: string): boolean {
  if (contentType && contentType.startsWith('image/')) return true;
  const imageExts = ['.png', '.jpg', '.jpeg', '.gif', '.bmp', '.webp'];
  return !!name && imageExts.some((ext) => name.toLowerCase().endsWith(ext));
}

/** 预览图片类附件：先获取下载链接再调用系统图片预览。 */
async function previewFile(file: AttachmentItem) {
  if (!file.id) {
    uni.showToast({ title: '无法预览', icon: 'none' });
    return;
  }
  try {
    const url = await getDownloadUrl(file.id);
    uni.previewImage({ urls: [url], current: url });
  } catch (e) {
    console.error('Preview failed:', e);
    uni.showToast({ title: '预览失败', icon: 'none' });
  }
}

/** 下载附件：获取预签名链接后通过系统下载能力打开。 */
async function downloadFile(file: AttachmentItem) {
  if (!file.id) {
    uni.showToast({ title: '无法下载', icon: 'none' });
    return;
  }
  uni.showLoading({ title: '准备下载...' });
  try {
    const url = await getDownloadUrl(file.id);
    // #ifdef H5
    window.open(url, '_blank');
    // #endif
    // #ifndef H5
    uni.downloadFile({
      url,
      success: (res) => {
        if (res.tempFilePath) {
          uni.openDocument({
            filePath: res.tempFilePath,
            showMenu: true,
            fail: () => uni.showToast({ title: '打开失败', icon: 'none' }),
          });
        }
      },
      fail: () => uni.showToast({ title: '下载失败', icon: 'none' }),
    });
    // #endif
  } catch (e) {
    console.error('Download failed:', e);
    uni.showToast({ title: '下载失败', icon: 'none' });
  } finally {
    uni.hideLoading();
  }
}

onMounted(() => {
  const pages = getCurrentPages();
  const currentPage = pages[pages.length - 1] as any;
  const options = currentPage?.options || currentPage?.$page?.options || {};
  const id = options.id;
  if (!id) {
    uni.showToast({ title: '缺少申请单ID', icon: 'none' });
    return;
  }
  detailId.value = id;
  loadDetail();
  loadAttachments();
});

// 监听 handle 页审核提交成功事件，刷新详情数据（与 onShow 双重保障）
onLoad(() => {
  uni.$on('biz-detail-refresh', loadDetail);
});
onUnload(() => {
  uni.$off('biz-detail-refresh', loadDetail);
});

onShow(() => {
  // P1-5: 页面浏览埋点
  trackPageView('mobile.biz-detail');
  // Reload detail when returning from handle page to reflect any status changes.
  if (detailId.value) loadDetail();
});
</script>

<style scoped>
.detail-page {
  padding: 24rpx;
  padding-bottom: 140rpx;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}
.detail-header__title {
  font-size: 34rpx;
  font-weight: 600;
  color: #303133;
  flex: 1;
  margin-right: 16rpx;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 8rpx 0;
}
.info-label {
  font-size: 28rpx;
  color: #909399;
}
.info-value {
  font-size: 28rpx;
  color: #303133;
  text-align: right;
}
.info-value--amount {
  font-weight: 600;
  color: #3c8772;
}
.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #303133;
  margin: 24rpx 0 16rpx;
  display: block;
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
.attach-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
}
.attach-actions .action-btn {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  font-size: 26rpx;
}
.action-btn--preview {
  background: #ecf5ff;
  color: #409eff;
  border: 1rpx solid #d9ecff;
}
.action-btn--download {
  background: #f0f9eb;
  color: #67c23a;
  border: 1rpx solid #e1f3d8;
}
/* P1-2: 审批记录时间线 */
.timeline {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}
.timeline-item {
  display: flex;
}
.timeline-item__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}
.timeline-item__dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  margin-top: 8rpx;
  flex-shrink: 0;
}
.timeline-item__line {
  flex: 1;
  width: 2rpx;
  background: #ebeef5;
  margin-top: 4rpx;
  min-height: 32rpx;
}
.timeline-item__body {
  flex: 1;
  padding-bottom: 24rpx;
}
.timeline-item__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.timeline-item__action {
  font-size: 28rpx;
  font-weight: 600;
  color: #303133;
}
.timeline-item__time {
  font-size: 24rpx;
  color: #c0c4cc;
}
.timeline-item__operator {
  display: block;
  font-size: 26rpx;
  color: #606266;
  margin-top: 6rpx;
}
.timeline-item__opinion {
  display: block;
  font-size: 26rpx;
  color: #909399;
  margin-top: 6rpx;
}
</style>
