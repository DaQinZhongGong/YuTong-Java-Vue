<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app';
import { initNetworkMonitor } from '@/store/network';
import { initUploadQueue } from '@/utils/uploadQueue';

onLaunch(() => {
  // 弱网/离线策略: 初始化全局网络监听与上传队列（网络恢复自动重试）
  initNetworkMonitor();
  initUploadQueue();
  // eslint-disable-next-line no-console
  console.log('App Launch');
});
onShow(() => {
  // App-level show hook. Actual data refresh is handled by each page's own onShow.
  // eslint-disable-next-line no-console
  console.log('App Show - page refresh delegated to page onShow hooks');
});
onHide(() => {
  // eslint-disable-next-line no-console
  console.log('App Hide');
});
</script>

<style lang="scss">
/* Global styles */
page {
  background-color: #f5f7fa;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue',
    Arial, sans-serif;
  color: #303133;
  font-size: 28rpx;
}

/* ===== WCAG 2.2 AA 无障碍全局样式 (设计来源: 43-国际化与无障碍设计、50-设计系统与视觉规范详设) ===== */

/* 屏幕阅读器专用文本 (WCAG 1.3.1) */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* 全局焦点可见样式 (WCAG 2.4.7 Focus Visible)
   设计文档 50: 焦点态使用 color-primary 外描边 */
:focus-visible {
  outline: 2px solid #3c8772;
  outline-offset: 2px;
}

/* 减少动画 (WCAG 2.3.3 Animation from Interactions) */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
