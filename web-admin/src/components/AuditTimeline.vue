<script setup lang="ts">
/**
 * AuditTimeline 审核/日志时间线
 * 设计来源: 53-Web管理端页面级分工详设 line 62 (通用组件任务表: AuditTimeline records)
 *
 * 展示审批轨迹或操作日志:
 *   - 按 timestamp 倒序展示 (最新在顶部)
 *   - status 映射 el-timeline-item 的 type (success/warning/danger/info, 缺省 primary)
 *   - 每项展示: 操作名 (粗体) + 操作人 (带头像) + 相对时间 + 审批意见 (灰色斜体)
 *   - loading 时展示 el-skeleton 骨架屏
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

/** 审核项数据结构 */
export interface AuditItem {
  id: string
  /** 操作名称 (如 "提交申请"、"审批通过") */
  action: string
  /** 操作人 */
  operator: string
  /** 操作人头像 URL (可选) */
  operatorAvatar?: string
  /** 状态: success/warning/danger/info, 缺省按 primary 展示 */
  status?: string
  /** 审批意见 (可选) */
  comment?: string
  /** 操作时间, ISO 字符串 */
  timestamp: string
}

interface Props {
  /** 审核项列表 */
  items: AuditItem[]
  /** 是否加载中 */
  loading?: boolean
  /** 骨架屏占位行数 */
  skeletonRows?: number
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  skeletonRows: 3,
})

const { t } = useI18n()

/** el-timeline-item type 合法值 */
type TimelineItemType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

/** 将 AuditItem.status 映射为 el-timeline-item 的 type */
function mapType(status?: string): TimelineItemType {
  switch (status) {
    case 'success':
      return 'success'
    case 'warning':
      return 'warning'
    case 'danger':
      return 'danger'
    case 'info':
      return 'info'
    default:
      return 'primary'
  }
}

/** 将 ISO 时间格式化为相对时间 (如 "3 分钟前"), 无法解析时原样返回 */
function relativeTime(iso: string): string {
  if (!iso) return '-'
  const ts = Date.parse(iso)
  if (Number.isNaN(ts)) return iso
  const diff = Date.now() - ts
  if (diff < 0) {
    return iso
  }
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return t('component.auditTimeline.time.justNow')
  const min = Math.floor(sec / 60)
  if (min < 60) return t('component.auditTimeline.time.minutesAgo', { n: min })
  const hour = Math.floor(min / 60)
  if (hour < 24) return t('component.auditTimeline.time.hoursAgo', { n: hour })
  const day = Math.floor(hour / 24)
  if (day < 30) return t('component.auditTimeline.time.daysAgo', { n: day })
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 操作人头像缺省时展示的首字母 */
function avatarText(name: string): string {
  if (!name) return '?'
  return name.trim().charAt(0).toUpperCase()
}

/** 倒序后的展示列表 (最新在前) */
const displayItems = computed<AuditItem[]>(() => {
  return [...props.items].sort((a, b) => {
    const ta = Date.parse(a.timestamp) || 0
    const tb = Date.parse(b.timestamp) || 0
    return tb - ta
  })
})
</script>

<template>
  <div class="yt-audit-timeline">
    <!-- 加载骨架屏 -->
    <el-skeleton v-if="loading" :rows="skeletonRows" animated />

    <!-- 空状态 -->
    <el-empty
      v-else-if="displayItems.length === 0"
      :description="$t('component.auditTimeline.empty')"
      :image-size="80"
    />

    <!-- 时间线 -->
    <el-timeline v-else>
      <el-timeline-item
        v-for="item in displayItems"
        :key="item.id"
        :type="mapType(item.status)"
        :timestamp="relativeTime(item.timestamp)"
        placement="top"
      >
        <div class="yt-audit-timeline__head">
          <span class="yt-audit-timeline__action">{{ item.action }}</span>
        </div>
        <div class="yt-audit-timeline__operator">
          <el-avatar :size="22" :src="item.operatorAvatar">
            {{ avatarText(item.operator) }}
          </el-avatar>
          <span class="yt-audit-timeline__name">{{ item.operator || '-' }}</span>
        </div>
        <div v-if="item.comment" class="yt-audit-timeline__comment">
          "{{ item.comment }}"
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped>
.yt-audit-timeline {
  padding: 4px 0;
}

.yt-audit-timeline__head {
  margin-bottom: 4px;
}

.yt-audit-timeline__action {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.yt-audit-timeline__operator {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.yt-audit-timeline__name {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.yt-audit-timeline__comment {
  margin-top: 4px;
  font-size: 13px;
  font-style: italic;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>