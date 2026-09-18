<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Document, CopyDocument, Plus, CircleCheck, Tools } from '@element-plus/icons-vue'
import { executeSkill, pageSkills, publishSkill, type AiSkill } from '@/api/skills'

interface Skill {
  id: string
  name: string
  description?: string
  kind?: string
  version?: string
  tags?: string[]
  installed?: boolean
  downloads?: number
  rating?: number
  status?: string
  versionNo?: number
  optimisticVersion?: number
}

const loading = ref(false)
const filter = ref('')
const activeKind = ref('all')
const list = ref<Skill[]>([])
const currentPage = ref(1)
const pageSize = ref(9)
const installingId = ref<string>('')

function mapSkill(s: AiSkill): Skill {
  const md = (s.skillMd || '').replace(/\s+/g, ' ').trim()
  return {
    id: s.id,
    name: s.skillName,
    description: md.slice(0, 120) || s.skillCode,
    kind: s.skillType,
    version: String(s.versionNo ?? 1),
    tags: [s.skillType, s.status || 'DRAFT'],
    installed: s.status === 'PUBLISHED',
    downloads: 0,
    rating: 5,
    status: s.status,
    versionNo: s.versionNo,
    optimisticVersion: s.version,
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pageSkills({
      page: 1,
      size: 48,
      skillType: activeKind.value !== 'all' ? activeKind.value : undefined,
      skillCode: filter.value || undefined,
    })
    list.value = (data.records || []).map(mapSkill)
    currentPage.value = 1
  } catch (e: unknown) {
    list.value = []
    ElMessage.error((e as { message?: string })?.message || '加载技能失败')
  } finally {
    loading.value = false
  }
}

const filtered = computed(()=> list.value) // already filtered via load
const paged = computed(()=>{
  const start=(currentPage.value-1)*pageSize.value
  return filtered.value.slice(start, start+pageSize.value)
})
const stats = computed(()=> ({
  total: list.value.length,
  installed: list.value.filter(s=>s.installed).length,
  docx: list.value.filter(s=>s.kind==='docx').length,
  pdf: list.value.filter(s=>s.kind==='pdf').length,
  xlsx: list.value.filter(s=>s.kind==='xlsx').length,
}))

function kindColor(k?: string): 'success'|'danger'|'warning'|'primary'|'info' {
  if(k==='docx') return 'primary'
  if(k==='pdf') return 'danger'
  if(k==='xlsx') return 'success'
  return 'info'
}

async function install(s: Skill) {
  if (s.installed) {
    ElMessage.info(`${s.name} 已发布`)
    return
  }
  installingId.value = s.id
  try {
    await publishSkill(s.id, s.optimisticVersion ?? 0)
    ElMessage.success(`已发布技能：${s.name}`)
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '发布失败')
  } finally {
    installingId.value = ''
  }
}
async function runAnalyze(s: Skill) {
  try {
    const { value } = await ElMessageBox.prompt(`对「${s.name}」执行 analyze，请输入文本`, '执行技能', {
      confirmButtonText: '执行',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '粘贴待分析内容',
    })
    const content = String(value || '').trim()
    if (!content) {
      ElMessage.warning('内容不能为空')
      return
    }
    const res = await executeSkill(s.id, { action: 'analyze', content })
    ElMessage.success(res.summary || '执行完成')
  } catch (e: unknown) {
    if (e === 'cancel') return
    ElMessage.error((e as { message?: string })?.message || '执行失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="skill-page">
    <div class="page-head">
      <div class="head-left">
        <h2 class="head-title"><el-icon><CopyDocument /></el-icon> 技能市场</h2>
        <div class="head-sub">
          <span class="sub-stat"><strong>{{ stats.total }}</strong> 技能</span><span class="dot">·</span>
          <span class="sub-stat"><strong>{{ stats.installed }}</strong> 已安装</span><span class="dot">·</span>
          <span class="sub-stat">docx {{ stats.docx }} · pdf {{ stats.pdf }} · xlsx {{ stats.xlsx }}</span>
        </div>
      </div>
      <div class="head-actions">
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button size="small" type="primary" :icon="Plus" @click="load">同步内置技能</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-radio-group v-model="activeKind" size="small" @change="load">
          <el-radio-button value="all">全部 ({{ stats.total }})</el-radio-button>
          <el-radio-button value="docx">docx ({{ stats.docx }})</el-radio-button>
          <el-radio-button value="pdf">pdf ({{ stats.pdf }})</el-radio-button>
          <el-radio-button value="xlsx">xlsx ({{ stats.xlsx }})</el-radio-button>
        </el-radio-group>
        <el-input v-model="filter" :prefix-icon="Search" placeholder="搜索技能名 / 描述" clearable style="max-width:280px" @keyup.enter="load" />
        <el-button size="small" type="primary" @click="load">搜索</el-button>
        <span class="filter-count">已筛选 {{ filtered.length }} 项</span>
      </div>
    </el-card>

    <div v-loading="loading" class="skill-grid">
      <div v-for="s in paged" :key="s.id" class="skill-card" :class="{ installed: s.installed }">
        <div class="card-head">
          <div class="skill-icon" :class="s.kind"><el-icon><Document /></el-icon></div>
          <div class="skill-meta">
            <div class="skill-name" :title="s.name">{{ s.name }}</div>
            <div class="skill-kind"><el-tag size="small" :type="kindColor(s.kind)" effect="plain">{{ s.kind }}</el-tag><span class="ver">v{{ s.version }}</span><span v-if="s.installed" class="installed-badge"><el-icon><CircleCheck /></el-icon>已安装</span></div>
          </div>
        </div>
        <div class="skill-desc">{{ s.description || '—' }}</div>
        <div class="skill-tags">
          <el-tag v-for="t in (s.tags||[])" :key="t" size="small" effect="plain">{{ t }}</el-tag>
        </div>
        <div class="skill-foot">
          <span class="foot-item"><el-icon><Tools /></el-icon>{{ s.downloads ?? 0 }} 下载</span>
          <span class="foot-item">★ {{ s.rating ?? '-' }}</span>
        </div>
        <div class="card-actions">
          <el-button v-if="!s.installed" size="small" type="primary" :loading="installingId===s.id" :icon="Plus" style="flex:1" @click="install(s)">发布</el-button>
          <el-button v-else size="small" type="success" plain :icon="CircleCheck" style="flex:1" disabled>已发布</el-button>
          <el-button v-if="s.installed" size="small" @click="runAnalyze(s)">执行</el-button>
          <el-button size="small" :icon="Document" @click="ElMessage.info(s.description || s.name)">详情</el-button>
        </div>
      </div>

      <div v-if="!loading && !paged.length" class="empty-state">
        <div class="empty-illus">⬡</div>
        <div class="empty-title">暂无匹配的技能</div>
        <div class="empty-desc">尝试切换分类或调整关键字</div>
        <el-button size="small" @click="activeKind='all'; filter=''; load()">清除筛选</el-button>
        <div class="empty-kinds">
          <span class="k-chip docx">docx 文档</span>
          <span class="k-chip pdf">pdf 提取</span>
          <span class="k-chip xlsx">xlsx 分析</span>
        </div>
      </div>
    </div>

    <div v-if="filtered.length > pageSize" class="pager-wrap">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="filtered.length"
        :page-size="pageSize"
        :current-page="currentPage"
        :page-sizes="[9,12,24]"
        @size-change="(v:number)=>{ pageSize=v; currentPage=1 }"
        @current-change="(v:number)=> currentPage=v"
      />
    </div>
    <div class="foot-hint">GET /api/v1/skills · 内置 docx/pdf/xlsx 首次访问自动种子 · 执行走 POST /skills/{id}/execute</div>
  </div>
</template>

<style scoped>
.skill-page{ display:flex; flex-direction:column; gap:14px; padding:4px 2px 20px; background: var(--yt-bg-page, #f6f8fb); min-height:100% }
.page-head{ display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:16px; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)) }
.head-title{ margin:0; font-size:18px; font-weight:600; display:flex; gap:8px; align-items:center }
.head-sub{ display:flex; align-items:center; gap:8px; font-size:12px; color: var(--yt-text-secondary); margin-top:6px; flex-wrap:wrap }
.head-sub strong{ color: var(--yt-text-primary) }
.dot{ color: var(--yt-text-disabled) }
.head-actions{ display:flex; gap:8px; align-items:center }
.filter-card :deep(.el-card__body){ padding:12px 16px }
.filter-row{ display:flex; align-items:center; gap:12px; flex-wrap:wrap }
.filter-count{ font-size:12px; color: var(--yt-text-secondary) }
.skill-grid{ display:grid; grid-template-columns: repeat(auto-fill, minmax(300px,1fr)); gap:16px }
.skill-card{ background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:14px; display:flex; flex-direction:column; gap:10px; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)); transition: all 140ms ease }
.skill-card:hover{ border-color: var(--yt-color-primary-light-5); box-shadow: 0 4px 16px rgba(37,99,235,.08); transform: translateY(-1px) }
.skill-card.installed{ border-color: var(--yt-color-success); background: #f0fdf4 }
.card-head{ display:flex; gap:10px; align-items:center }
.skill-icon{ width:36px; height:36px; border-radius:10px; display:flex; align-items:center; justify-content:center; color:#fff; flex-shrink:0 }
.skill-icon.docx{ background: var(--yt-color-primary) } .skill-icon.pdf{ background: var(--yt-color-danger) } .skill-icon.xlsx{ background: var(--yt-color-success) }
.skill-meta{ flex:1; min-width:0 }
.skill-name{ font-size:14px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.skill-kind{ display:flex; gap:6px; align-items:center; margin-top:4px; font-size:11px; color: var(--yt-text-secondary) }
.ver{ font-family: ui-monospace, monospace; font-size:11px; color: var(--yt-text-disabled) }
.installed-badge{ display:flex; gap:3px; align-items:center; color: var(--yt-color-success); font-size:11px; font-weight:600 }
.skill-desc{ font-size:12px; color: var(--yt-text-secondary); min-height:28px; line-height:1.5 }
.skill-tags{ display:flex; gap:6px; flex-wrap:wrap }
.skill-foot{ display:flex; gap:12px; font-size:11px; color: var(--yt-text-secondary); border-top:1px dashed var(--yt-border-light); padding-top:8px }
.card-actions{ display:flex; gap:8px; margin-top:2px }
.empty-state{ grid-column: 1 / -1; text-align:center; padding:36px 0; background: var(--yt-bg-card); border:1px dashed var(--yt-border-default); border-radius:12px }
.empty-illus{ font-size:32px; color: var(--yt-text-disabled) }
.empty-title{ font-weight:600; margin-top:8px }
.empty-desc{ font-size:12px; color: var(--yt-text-secondary); margin:6px 0 12px }
.empty-kinds{ display:flex; gap:8px; justify-content:center; margin-top:12px }
.k-chip{ padding:4px 10px; border-radius:999px; font-size:11px; border:1px solid var(--yt-border-light); background:#f8fafc }
.k-chip.docx{ border-color: var(--yt-color-primary); color: var(--yt-color-primary) }
.k-chip.pdf{ border-color: var(--yt-color-danger); color: var(--yt-color-danger) }
.k-chip.xlsx{ border-color: var(--yt-color-success); color: var(--yt-color-success) }
.pager-wrap{ display:flex; justify-content:center; margin-top:4px }
.foot-hint{ font-size:11px; color: var(--yt-text-secondary); text-align:center }
</style>
