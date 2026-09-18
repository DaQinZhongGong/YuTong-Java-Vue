<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Document, View, ArrowLeft } from '@element-plus/icons-vue'
import { getPublishedBySlug, listAllPublished, listPublishedByCategory, CMS_CATEGORIES, type CmsContent } from '@/api/cms'

const route = useRoute()
const router = useRouter()

const list = ref<CmsContent[]>([])
const listLoading = ref(false)
const listError = ref('')
const activeCategory = ref<string>('all')
const detail = ref<CmsContent | null>(null)
const detailLoading = ref(false)
const detailError = ref('')

const slug = computed(() => (route.params.slug as string) || '')
const isDetail = computed(() => !!slug.value)

const categoryLabel = (c?: string) => {
  switch (c) {
    case 'announcement': return '公告'
    case 'help': return '帮助'
    case 'terms': return '条款'
    case 'blog': return '博客'
    default: return c || '—'
  }
}

const filtered = computed(() =>
  activeCategory.value === 'all' ? list.value : list.value.filter((i) => i.category === activeCategory.value),
)

/** 极简安全 Markdown 渲染: 先全量转义, 再还原受控子集 (标题/加粗/行内代码/链接/无序列表/段落)。
 *  内容作者为内部运营 (管理端登录态), 仍做转义 + 仅放行 http(s) 链接, 防存储型 XSS。 */
function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
}
function renderInline(s: string): string {
  let out = escapeHtml(s)
  out = out.replace(/`([^`\n]+)`/g, '<code class="yt-cms__code">$1</code>')
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  out = out.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  return out
}
function renderMarkdown(md: string): string {
  const lines = (md || '').split('\n')
  const html: string[] = []
  let inList = false
  const closeList = () => { if (inList) { html.push('</ul>'); inList = false } }
  for (const raw of lines) {
    const line = raw.trimEnd()
    if (/^#{1,3}\s+/.test(line)) {
      closeList()
      const level = line.match(/^#+/)![0].length
      html.push(`<h${level} class="yt-cms__h${level}">${renderInline(line.replace(/^#+\s+/, ''))}</h${level}>`)
    } else if (/^[-*]\s+/.test(line)) {
      if (!inList) { html.push('<ul class="yt-cms__ul">'); inList = true }
      html.push(`<li>${renderInline(line.replace(/^[-*]\s+/, ''))}</li>`)
    } else if (!line.trim()) {
      closeList()
    } else {
      closeList()
      html.push(`<p class="yt-cms__p">${renderInline(line)}</p>`)
    }
  }
  closeList()
  return html.join('')
}

const renderedDetail = computed(() => {
  if (!detail.value) return ''
  // 后端 contentHtml 当前为原文透传 (见 CmsContentService.renderMarkdown), 前端统一走安全渲染
  const src = detail.value.contentHtml || detail.value.contentMd || ''
  return renderMarkdown(src)
})

function fmtDate(s?: string): string {
  if (!s) return '—'
  return s.replace('T', ' ').slice(0, 16)
}

async function loadList(category: string) {
  listLoading.value = true
  listError.value = ''
  try {
    list.value = category === 'all'
      ? await listAllPublished()
      : await listPublishedByCategory(category)
  } catch (e: unknown) {
    const msg = (e as { message?: string })?.message || '内容加载失败'
    listError.value = msg
    list.value = []
  } finally {
    listLoading.value = false
  }
}

async function loadDetail(s: string) {
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    detail.value = await getPublishedBySlug(s)
  } catch (e: unknown) {
    const msg = (e as { message?: string })?.message || '内容加载失败'
    detailError.value = msg
    ElMessage.error(msg)
  } finally {
    detailLoading.value = false
  }
}

function openDetail(item: CmsContent) {
  void router.push(`/cms/${encodeURIComponent(item.slug)}`)
}
function backToList() {
  void router.push('/cms')
}

onMounted(() => {
  if (isDetail.value) {
    void loadDetail(slug.value)
    void loadList('all')
  } else {
    void loadList(activeCategory.value)
  }
})
watch(slug, (s) => { if (s) void loadDetail(s); else detail.value = null })
watch(activeCategory, (c) => { void loadList(c) })
</script>

<template>
  <div class="yt-cms">
    <!-- 详情 -->
    <div v-if="isDetail">
      <el-button :icon="ArrowLeft" text @click="backToList">返回内容列表</el-button>
      <el-card v-loading="detailLoading" shadow="never" class="yt-cms__detail">
        <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" />
        <template v-else-if="detail">
          <h1 class="yt-cms__title">{{ detail.title }}</h1>
          <div class="yt-cms__meta">
            <el-tag size="small" effect="plain">{{ categoryLabel(detail.category) }}</el-tag>
            <span><el-icon><View /></el-icon> {{ detail.viewCount ?? 0 }} 次浏览</span>
            <span>{{ fmtDate(detail.publishedAt) }}</span>
            <span v-if="detail.tags">{{ detail.tags }}</span>
          </div>
          <el-divider />
          <article class="yt-cms__body" v-html="renderedDetail" />
        </template>
      </el-card>
    </div>

    <!-- 列表 -->
    <div v-else>
      <div class="yt-cms__hero">
        <div class="yt-cms__hero-title"><el-icon style="color: var(--yt-color-primary)"><Document /></el-icon> 内容中心</div>
        <div class="yt-cms__hero-desc">公告 · 帮助文档 · 条款 · 博客（仅展示已发布内容）</div>
      </div>
      <el-tabs v-model="activeCategory" class="yt-cms__tabs">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane v-for="c in CMS_CATEGORIES" :key="c" :label="categoryLabel(c)" :name="c" />
      </el-tabs>
      <el-alert v-if="listError" :title="listError" type="error" show-icon :closable="false" class="yt-cms__alert" />
      <div v-else-if="listLoading" class="yt-cms__grid">
        <el-card v-for="i in 4" :key="i" shadow="never"><el-skeleton :rows="2" animated /></el-card>
      </div>
      <div v-else-if="filtered.length" class="yt-cms__grid">
        <el-card v-for="item in filtered" :key="item.id" shadow="never" class="yt-cms__card yt-anim-fade-up" @click="openDetail(item)">
          <div class="yt-cms__cardHead">
            <strong class="yt-cms__cardTitle">{{ item.title }}</strong>
            <el-tag size="small" effect="plain">{{ categoryLabel(item.category) }}</el-tag>
          </div>
          <div class="yt-cms__summary">{{ item.summary || (item.contentMd || '').slice(0, 120) || '—' }}</div>
          <div class="yt-cms__foot">
            <span><el-icon><View /></el-icon> {{ item.viewCount ?? 0 }}</span>
            <span>{{ fmtDate(item.publishedAt) }}</span>
          </div>
        </el-card>
      </div>
      <el-empty v-else description="暂无已发布内容" />
    </div>
  </div>
</template>

<style scoped>
.yt-cms { max-width: 1080px; margin: 0 auto; display: flex; flex-direction: column; gap: var(--yt-space-md); }
.yt-cms__hero { display: flex; flex-direction: column; gap: var(--yt-space-sm); padding: var(--yt-space-md) var(--yt-space-lg); background: linear-gradient(135deg, var(--yt-color-primary-light-9) 0%, var(--yt-bg-card) 65%); border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); box-shadow: var(--yt-shadow-card); }
.yt-cms__hero-title { display: flex; align-items: center; gap: var(--yt-space-sm); font-weight: var(--yt-font-weight-bold); font-size: 18px; color: var(--yt-text-primary); }
.yt-cms__hero-desc { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-cms__alert { margin-bottom: var(--yt-space-sm); }
.yt-cms__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: var(--yt-space-md); }
.yt-cms__card { border: 1px solid var(--yt-border-light); border-radius: var(--yt-radius-md); background: var(--yt-bg-card); cursor: pointer; transition: transform var(--yt-transition-hover, 120ms) ease, box-shadow var(--yt-transition-hover, 120ms) ease, border-color var(--yt-transition-hover, 120ms) ease; }
.yt-cms__card:hover { transform: translateY(-3px); box-shadow: var(--yt-shadow-popover); border-color: var(--yt-color-primary-light-7); }
.yt-cms__cardHead { display: flex; justify-content: space-between; align-items: center; gap: var(--yt-space-sm); }
.yt-cms__cardTitle { font-size: var(--yt-font-size-body); color: var(--yt-text-primary); }
.yt-cms__summary { font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); margin-top: var(--yt-space-sm); min-height: 36px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.yt-cms__foot { display: flex; gap: var(--yt-space-md); margin-top: var(--yt-space-sm); font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-cms__detail { margin-top: var(--yt-space-sm); }
.yt-cms__title { font-size: var(--yt-font-size-title); color: var(--yt-text-primary); margin: 0 0 var(--yt-space-sm); }
.yt-cms__meta { display: flex; gap: var(--yt-space-md); align-items: center; flex-wrap: wrap; font-size: var(--yt-font-size-caption); color: var(--yt-text-secondary); }
.yt-cms__body { font-size: var(--yt-font-size-body); line-height: var(--yt-font-line-height-body); color: var(--yt-text-primary); }
.yt-cms__body :deep(.yt-cms__p) { margin: 0 0 var(--yt-space-sm); }
.yt-cms__body :deep(.yt-cms__h1) { font-size: 20px; margin: var(--yt-space-md) 0 var(--yt-space-sm); }
.yt-cms__body :deep(.yt-cms__h2) { font-size: 17px; margin: var(--yt-space-md) 0 var(--yt-space-sm); }
.yt-cms__body :deep(.yt-cms__h3) { font-size: 15px; margin: var(--yt-space-sm) 0; }
.yt-cms__body :deep(.yt-cms__ul) { padding-left: 20px; margin: 0 0 var(--yt-space-sm); }
.yt-cms__body :deep(.yt-cms__code) { background: var(--yt-bg-page); border: 1px solid var(--yt-border-light); border-radius: 4px; padding: 0 4px; font-size: 12px; }
.yt-cms__body :deep(a) { color: var(--yt-color-primary); }
</style>
