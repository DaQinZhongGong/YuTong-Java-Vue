<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus, Film, View, VideoPlay, Setting, CopyDocument } from '@element-plus/icons-vue'
import client from '@/api/request'

interface DramaScript { id: string; title: string; synopsis?: string; status: string; createdTime?: string; updatedTime?: string }

const router = useRouter()
const loading = ref(false)
const list = ref<DramaScript[]>([])
const keyword = ref('')
const statusFilter = ref('all')
const currentPage = ref(1)
const pageSize = ref(9)

const drawer = ref(false)
const current = ref<DramaScript | null>(null)
const detail = ref<{ scenes: unknown[]; characters: unknown[]; locations: unknown[]; audios: unknown[]; storyboards: unknown[] }>({ scenes:[], characters:[], locations:[], audios:[], storyboards:[] })
const detailLoading = ref(false)
const createVisible = ref(false)
const createForm = ref({ title: '', synopsis: '', status: 'DRAFT' })
const saving = ref(false)

async function load(){
  loading.value=true
  try{
    const data = await client.get('/dramas', { params:{ page:1, size: 50, title: keyword.value || undefined, status: statusFilter.value!=='all'?statusFilter.value:undefined } }) as { records?: DramaScript[] }
    list.value = data?.records || []
  } catch (e: unknown) {
    list.value=[]
    ElMessage.error((e as { message?: string })?.message || '加载短剧失败')
  } finally { loading.value=false }
}

async function createDrama() {
  const title = createForm.value.title.trim()
  if (!title) { ElMessage.warning('请输入标题'); return }
  saving.value = true
  try {
    await client.post('/dramas', { title, synopsis: createForm.value.synopsis, status: createForm.value.status })
    ElMessage.success('已创建短剧')
    createVisible.value = false
    createForm.value = { title: '', synopsis: '', status: 'DRAFT' }
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '创建失败')
  } finally {
    saving.value = false
  }
}
const filtered = computed(()=>{
  const kw=keyword.value.trim().toLowerCase()
  return list.value.filter(d=>{
    if(kw && !(d.title.toLowerCase().includes(kw)||(d.synopsis||'').toLowerCase().includes(kw))) return false
    if(statusFilter.value!=='all' && d.status!==statusFilter.value) return false
    return true
  })
})
const paged = computed(()=>{ const s=(currentPage.value-1)*pageSize.value; return filtered.value.slice(s, s+pageSize.value) })
const stats = computed(()=>({ total:list.value.length, published:list.value.filter(d=>d.status==='PUBLISHED').length, draft:list.value.filter(d=>d.status==='DRAFT').length, archived:list.value.filter(d=>d.status==='ARCHIVED').length }))

function statusType(s:string): 'success'|'warning'|'info'|'danger'{
  if(s==='PUBLISHED') return 'success'
  if(s==='ARCHIVED') return 'info'
  if(s==='DRAFT') return 'warning'
  return 'info'
}

async function openDetail(item: DramaScript){
  current.value=item
  drawer.value=true
  detailLoading.value=true
  detail.value={ scenes:[], characters:[], locations:[], audios:[], storyboards:[] }
  const id=item.id
  const fet = async (path:string)=>{
    try{ const d= await client.get(`/dramas/${id}/${path}`) as unknown as unknown[]; return Array.isArray(d)? d : ((d as { records?: unknown[] })?.records||[]) }catch{ return [] }
  }
  try{
    const [scenes, characters, locations, audios, storyboards] = await Promise.all([
      fet('scenes'), fet('characters'), fet('locations'), fet('audios'), fet('storyboards')
    ])
    detail.value={ scenes: scenes as unknown[], characters: characters as unknown[], locations: locations as unknown[], audios: audios as unknown[], storyboards: storyboards as unknown[] }
  }finally{ detailLoading.value=false }
}
function goDrama(){ router.push('/drama') }

onMounted(load)
</script>

<template>
  <div class="drama-page">
    <div class="page-head">
      <div class="head-left">
        <h2 class="head-title"><el-icon><Film /></el-icon> 短剧管理</h2>
        <div class="head-sub">
          <span class="sub-stat"><strong>{{ stats.total }}</strong> 剧本</span><span class="dot">·</span>
          <span class="sub-stat"><strong>{{ stats.published }}</strong> 已发布</span><span class="dot">·</span>
          <span class="sub-stat">草稿 {{ stats.draft }} · 归档 {{ stats.archived }}</span>
        </div>
      </div>
      <div class="head-actions">
        <el-button size="small" :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button size="small" type="primary" :icon="Plus" @click="createVisible=true">新建短剧</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索剧本标题/梗概" clearable style="max-width:320px" @input="currentPage=1" />
        <el-select v-model="statusFilter" style="width:140px" @change="currentPage=1">
          <el-option label="全部状态" value="all" />
          <el-option label="DRAFT" value="DRAFT" />
          <el-option label="PUBLISHED" value="PUBLISHED" />
          <el-option label="ARCHIVED" value="ARCHIVED" />
        </el-select>
        <span class="filter-count">已筛选 {{ filtered.length }} / {{ list.length }}</span>
      </div>
    </el-card>

    <div v-loading="loading" class="drama-grid">
      <div v-for="d in paged" :key="d.id" class="drama-card">
        <div class="card-head">
          <div class="drama-icon"><el-icon><Film /></el-icon></div>
          <div class="drama-meta">
            <div class="drama-name" :title="d.title">{{ d.title }}</div>
            <div class="drama-synopsis">{{ d.synopsis || '—' }}</div>
          </div>
          <el-tag :type="statusType(d.status)" size="small" effect="plain">{{ d.status }}</el-tag>
        </div>
        <div class="drama-tags">
          <el-tag size="small" effect="plain" type="info"><el-icon><CopyDocument /></el-icon> drama_script</el-tag>
          <el-tag size="small" effect="plain" style="border-color: var(--yt-color-primary-light-7); color: var(--yt-color-primary)">V043 7表</el-tag>
          <span class="drama-time">{{ d.createdTime ? new Date(d.createdTime).toLocaleDateString() : '—' }}</span>
        </div>
        <div class="card-actions">
          <el-button size="small" type="primary" :icon="View" @click="openDetail(d)">详情</el-button>
          <el-button size="small" plain :icon="Setting" @click="openDetail(d)">资产</el-button>
          <el-button size="small" :icon="VideoPlay" @click="goDrama">进入工坊</el-button>
        </div>
      </div>
      <div v-if="!loading && !paged.length" class="empty-state">
        <div class="empty-illus">🎬</div>
        <div class="empty-title">暂无匹配的短剧</div>
        <div class="empty-desc">尝试调整关键字或状态筛选</div>
        <el-button size="small" @click="keyword=''; statusFilter='all'">清除筛选</el-button>
        <div class="empty-legend">
          <span class="legend-chip">DRAFT 草稿</span>
          <span class="legend-chip">PUBLISHED 已发布</span>
          <span class="legend-chip">ARCHIVED 归档</span>
        </div>
      </div>
    </div>

    <div v-if="filtered.length>pageSize" class="pager-wrap">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="filtered.length" :page-size="pageSize" :current-page="currentPage" :page-sizes="[9,12,24]" @size-change="(v:number)=>{pageSize=v; currentPage=1}" @current-change="(v:number)=>currentPage=v" />
    </div>
    <div class="foot-hint">后端 GET /api/v1/dramas · 列表+状态标签+进入详情 · 与 McpMarket/SkillMarket 同级视觉</div>

    <el-drawer v-model="drawer" :title="current ? `详情 · ${current.title}` : '详情'" size="640px" direction="rtl" destroy-on-close>
      <div v-loading="detailLoading" style="display:flex; flex-direction:column; gap:14px">
        <el-alert type="info" :closable="false" title="短剧详情：复用 P0 后端 drama 7表 · 角色/场景地/音频/分镜 资产概览" />
        <el-descriptions v-if="current" :column="1" border size="small">
          <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType(current.status)" size="small" effect="plain">{{ current.status }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="梗概">{{ current.synopsis || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ current.createdTime || '—' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-grid">
          <div class="detail-card"><strong>场景</strong><span class="detail-num">{{ detail.scenes.length }}</span><span class="detail-sub">drama_scene</span></div>
          <div class="detail-card"><strong>角色</strong><span class="detail-num">{{ detail.characters.length }}</span><span class="detail-sub">drama_character</span></div>
          <div class="detail-card"><strong>场景地</strong><span class="detail-num">{{ detail.locations.length }}</span><span class="detail-sub">drama_location</span></div>
          <div class="detail-card"><strong>音频</strong><span class="detail-num">{{ detail.audios.length }}</span><span class="detail-sub">drama_audio</span></div>
          <div class="detail-card"><strong>分镜</strong><span class="detail-num">{{ detail.storyboards.length }}</span><span class="detail-sub">drama_storyboard</span></div>
        </div>
        <el-button type="primary" :icon="VideoPlay" @click="goDrama">进入短剧工坊（/drama）</el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="createVisible" title="新建短剧" width="480px">
      <el-form label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="梗概">
          <el-input v-model="createForm.synopsis" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createDrama">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.drama-page{ display:flex; flex-direction:column; gap:14px; padding:4px 2px 20px; background: var(--yt-bg-page, #f6f8fb); min-height:100% }
.page-head{ display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:16px; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)) }
.head-title{ margin:0; font-size:18px; font-weight:600; display:flex; gap:8px; align-items:center }
.head-sub{ display:flex; align-items:center; gap:8px; font-size:12px; color: var(--yt-text-secondary); margin-top:6px }
.head-sub strong{ color: var(--yt-text-primary) }
.dot{ color: var(--yt-text-disabled) }
.head-actions{ display:flex; gap:8px; align-items:center }
.filter-card :deep(.el-card__body){ padding:12px 16px }
.filter-row{ display:flex; align-items:center; gap:12px; flex-wrap:wrap }
.filter-count{ font-size:12px; color: var(--yt-text-secondary) }
.drama-grid{ display:grid; grid-template-columns: repeat(auto-fill, minmax(320px,1fr)); gap:16px }
.drama-card{ background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:14px; display:flex; flex-direction:column; gap:10px; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)); transition: all 140ms ease }
.drama-card:hover{ border-color: var(--yt-color-primary-light-5); box-shadow: 0 4px 16px rgba(37,99,235,.08); transform: translateY(-1px) }
.card-head{ display:flex; gap:10px; align-items:center }
.drama-icon{ width:36px; height:36px; border-radius:10px; display:flex; align-items:center; justify-content:center; color:#fff; flex-shrink:0; background: var(--yt-color-primary) }
.drama-meta{ flex:1; min-width:0 }
.drama-name{ font-size:14px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.drama-synopsis{ font-size:12px; color: var(--yt-text-secondary); white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.drama-tags{ display:flex; gap:6px; flex-wrap:wrap; align-items:center }
.drama-time{ font-size:11px; color: var(--yt-text-disabled); margin-left:auto }
.card-actions{ display:flex; gap:8px; margin-top:2px }
.card-actions .el-button{ flex:1 }
.empty-state{ grid-column:1/-1; text-align:center; padding:36px 0; background: var(--yt-bg-card); border:1px dashed var(--yt-border-default); border-radius:12px }
.empty-illus{ font-size:32px; color: var(--yt-text-disabled) }
.empty-title{ font-weight:600; margin-top:8px }
.empty-desc{ font-size:12px; color: var(--yt-text-secondary); margin:6px 0 12px }
.empty-legend{ display:flex; gap:8px; justify-content:center; margin-top:12px; flex-wrap:wrap }
.legend-chip{ padding:4px 10px; border-radius:999px; font-size:11px; border:1px solid var(--yt-border-light); background:#f8fafc }
.pager-wrap{ display:flex; justify-content:center; margin-top:4px }
.foot-hint{ font-size:11px; color: var(--yt-text-secondary); text-align:center }
.detail-grid{ display:grid; grid-template-columns: repeat(3,1fr); gap:10px }
.detail-card{ border:1px solid var(--yt-border-light); border-radius:8px; padding:12px; display:flex; flex-direction:column; gap:4px; background:#f8fafc; text-align:center }
.detail-num{ font-size:22px; font-weight:700; color: var(--yt-color-primary) }
.detail-sub{ font-size:11px; color: var(--yt-text-secondary); font-family: ui-monospace, monospace }
</style>
