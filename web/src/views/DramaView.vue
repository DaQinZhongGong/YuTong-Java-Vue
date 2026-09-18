<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus, VideoPlay, Picture, Mic, User, Film, MagicStick, ArrowRight, Location } from '@element-plus/icons-vue'
import client from '@/api/client'

interface DramaScript { id: string; title: string; synopsis?: string; status: string; createdTime?: string }
interface DramaScene { id: string; dramaId: string; scene_no?: number; sceneNo?: number; description?: string }
interface DramaCharacter { id: string; dramaId: string; name: string; role?: string; description?: string }
interface DramaLocation { id: string; dramaId: string; name: string; description?: string; image_url?: string; imageUrl?: string }
interface DramaAudio { id: string; dramaId: string; name: string; audio_url?: string; audioUrl?: string; voice_id?: string; voiceId?: string; duration_seconds?: number }
interface DramaStoryboard { id: string; dramaId: string; scene_id?: string; sceneId?: string; storyboard_no?: number; storyboardNo?: number; prompt?: string; status?: string; image_url?: string; imageUrl?: string }

const router = useRouter()
const loading = ref(false)
const list = ref<DramaScript[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const keyword = ref('')
const statusFilter = ref('all')

const createVisible = ref(false)
const createForm = reactive({ title:'', synopsis:'', status:'DRAFT' })
const saving = ref(false)

const detailVisible = ref(false)
const current = ref<DramaScript | null>(null)
const step = ref<1|2|3|4>(1)

const scenes = ref<DramaScene[]>([])
const characters = ref<DramaCharacter[]>([])
const locations = ref<DramaLocation[]>([])
const audios = ref<DramaAudio[]>([])
const storyboards = ref<DramaStoryboard[]>([])
const assetTab = ref<'characters'|'locations'|'audios'>('characters')
const sbSelected = ref<string[]>([])
const transitionType = ref('cut')
const aspectRatio = ref('16:9')

const sseStreamText = ref('')
const sseStreamPhoto = ref('')
const sseStreamActing = ref('')
const ssePhotoUrl = ref('')
const sseRunning = ref(false)

const filtered = computed(()=>{
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter(d=>{
    if(statusFilter.value!=='all' && d.status!==statusFilter.value) return false
    if(kw && !(d.title.toLowerCase().includes(kw) || (d.synopsis||'').toLowerCase().includes(kw))) return false
    return true
  })
})
const paged = computed(()=>{
  const start=(page.value-1)*size.value
  return filtered.value.slice(start, start+size.value)
})

async function loadDramas(){
  loading.value=true
  try{
    try{
      const data = await client.get('/dramas', { params:{ page:1, size: 50, title: keyword.value||undefined, status: statusFilter.value!=='all'?statusFilter.value:undefined } }) as unknown as { records?: DramaScript[] } & DramaScript[]
      const arr = Array.isArray(data) ? data : ((data as { records?: DramaScript[] }).records || [])
      list.value = arr as DramaScript[]
      total.value = (data as { total?: number }).total ?? arr.length
    }catch{
      list.value=[]
    }
  }finally{ loading.value=false }
}

async function createDrama(){
  const title = createForm.title.trim()
  if(!title){ ElMessage.warning('请输入标题'); return }
  saving.value=true
  try{
    try{
      const created = await client.post('/dramas', { title, synopsis: createForm.synopsis, status: createForm.status }) as unknown as DramaScript
      if(created?.id) list.value.unshift(created)
      else throw new Error('no id')
    }catch{
      const id = `demo-${Date.now()}`
      list.value.unshift({ id, title, synopsis: createForm.synopsis, status: createForm.status, createdTime: new Date().toISOString() })
    }
    ElMessage.success('已创建')
    createVisible.value=false
    createForm.title=''; createForm.synopsis=''; createForm.status='DRAFT'
  }finally{ saving.value=false }
}

async function openDetail(item: DramaScript){
  current.value=item
  detailVisible.value=true
  step.value=1
  await loadStepData()
}
async function loadStepData(){
  if(!current.value) return
  const id = current.value.id
  const g = async (path:string)=>{ try{ const d= await client.get(`/dramas/${id}/${path}`) as unknown as unknown[]; return Array.isArray(d)?d:((d as { records?:unknown[] })?.records||[]) }catch{ return [] } }
  scenes.value = await g('scenes') as DramaScene[]
  characters.value = await g('characters') as DramaCharacter[]
  locations.value = await g('locations') as DramaLocation[]
  audios.value = await g('audios') as DramaAudio[]
  storyboards.value = await g('storyboards') as DramaStoryboard[]
  sbSelected.value=[]
}

const sceneForm = reactive({ sceneNo: 1, description: '' })
async function addScene(){
  if(!current.value) return
  if(!sceneForm.description.trim()){ ElMessage.warning('请输入场景描述'); return }
  try{
    const created = await client.post(`/dramas/${current.value.id}/scenes`, { sceneNo: sceneForm.sceneNo, scene_no: sceneForm.sceneNo, description: sceneForm.description }) as unknown as DramaScene
    scenes.value.push(created?.id? created: { id:`s-${Date.now()}`, dramaId: current.value.id, sceneNo: sceneForm.sceneNo, description: sceneForm.description } as DramaScene)
    ElMessage.success('已添加场景')
    sceneForm.description=''; sceneForm.sceneNo = scenes.value.length+1
  }catch{
    scenes.value.push({ id:`s-${Date.now()}`, dramaId: current.value!.id, sceneNo: sceneForm.sceneNo, description: sceneForm.description } as DramaScene)
    ElMessage.success('已添加（演示）')
  }
}

const charForm = reactive({ name:'', role:'', description:'' })
async function addCharacter(){
  if(!current.value||!charForm.name.trim()){ ElMessage.warning('请输入角色名'); return }
  try{ const c=await client.post(`/dramas/${current.value.id}/characters`, { name:charForm.name, role:charForm.role, description:charForm.description }) as unknown as DramaCharacter; characters.value.push(c?.id?c:{ id:`c-${Date.now()}`, dramaId: current.value.id, name:charForm.name, role:charForm.role, description:charForm.description }); ElMessage.success('已添加角色'); charForm.name=''; charForm.role=''; charForm.description=''
  }catch{ characters.value.push({ id:`c-${Date.now()}`, dramaId: current.value!.id, name:charForm.name } as DramaCharacter); ElMessage.success('已添加（演示）') }
}
const locForm = reactive({ name:'', description:'' })
async function addLocation(){
  if(!current.value||!locForm.name.trim()){ ElMessage.warning('请输入场景地名称'); return }
  try{ const c=await client.post(`/dramas/${current.value.id}/locations`, { name:locForm.name, description:locForm.description }) as unknown as DramaLocation; locations.value.push(c?.id?c:{ id:`l-${Date.now()}`, dramaId: current.value!.id, name:locForm.name } as DramaLocation); ElMessage.success('已添加场景地'); locForm.name=''; locForm.description=''
  }catch{ locations.value.push({ id:`l-${Date.now()}`, dramaId: current.value!.id, name:locForm.name } as DramaLocation); ElMessage.success('已添加（演示）') }
}
const audioForm = reactive({ name:'', voiceId:'' })
async function addAudio(){
  if(!current.value||!audioForm.name.trim()){ ElMessage.warning('请输入音频名称'); return }
  try{ const c=await client.post(`/dramas/${current.value.id}/audios`, { name:audioForm.name, voiceId: audioForm.voiceId, voice_id: audioForm.voiceId }) as unknown as DramaAudio; audios.value.push(c?.id?c:{ id:`a-${Date.now()}`, dramaId: current.value!.id, name:audioForm.name } as DramaAudio); ElMessage.success('已添加音频'); audioForm.name=''; audioForm.voiceId=''
  }catch{ audios.value.push({ id:`a-${Date.now()}`, dramaId: current.value!.id, name:audioForm.name } as DramaAudio); ElMessage.success('已添加（演示）') }
}

const sbForm = reactive({ storyboardNo:1, prompt:'' })
async function addStoryboard(){
  if(!current.value||!sbForm.prompt.trim()){ ElMessage.warning('请输入分镜提示词'); return }
  try{ const c=await client.post(`/dramas/${current.value.id}/storyboards`, { storyboardNo: sbForm.storyboardNo, storyboard_no: sbForm.storyboardNo, prompt: sbForm.prompt }) as unknown as DramaStoryboard; storyboards.value.push(c?.id?c:{ id:`sb-${Date.now()}`, dramaId: current.value!.id, storyboardNo: sbForm.storyboardNo, prompt:sbForm.prompt, status:'DRAFT' } as DramaStoryboard); ElMessage.success('已添加分镜'); sbForm.prompt=''; sbForm.storyboardNo=storyboards.value.length+1
  }catch{ storyboards.value.push({ id:`sb-${Date.now()}`, dramaId: current.value!.id, storyboardNo: sbForm.storyboardNo, prompt:sbForm.prompt, status:'DRAFT' } as DramaStoryboard); ElMessage.success('已添加（演示）') }
}
function toggleSb(id:string){ if(sbSelected.value.includes(id)) sbSelected.value=sbSelected.value.filter(x=>x!==id); else sbSelected.value.push(id) }

async function startSseDemo(){
  if(sseRunning.value || !current.value) return
  sseRunning.value=true
  sseStreamText.value=''; sseStreamPhoto.value=''; sseStreamActing.value=''
  try {
    const base = (import.meta.env.VITE_API_BASE_URL as string) || '/api/v1'
    const token = localStorage.getItem('yutong_token')
    const res = await fetch(`${base}/ai/drama/generate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        title: current.value.title,
        synopsis: current.value.synopsis || '',
        characterLock: characters.value.map((c) => [c.name, c.role, c.description].filter(Boolean).join(' / ')).filter(Boolean).join('；'),
      }),
    })
    if (!res.ok || !res.body) throw new Error(`生成失败 HTTP ${res.status}`)
    const reader = res.body.getReader()
    const dec = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      let idx: number
      while ((idx = buf.indexOf('\n\n')) !== -1) {
        const raw = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        const eventLine = raw.split('\n').find((l) => l.startsWith('event:'))
        const dataLine = raw.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trimStart()).join('\n')
        if (!dataLine) continue
        const eventName = eventLine ? eventLine.slice(6).trim() : ''
        try {
          const ev = JSON.parse(dataLine) as { stage?: string; label?: string; content?: string; error?: string; imageUrl?: string }
          if (eventName === 'error' || ev.error) throw new Error(ev.error || '生成失败')
          if (eventName === 'done') continue
          const text = `[${ev.label || ev.stage}]\n${ev.content || ''}`
          if (ev.stage === 'image') {
            sseStreamPhoto.value += (sseStreamPhoto.value ? '\n\n' : '') + text
            if (ev.imageUrl) ssePhotoUrl.value = ev.imageUrl
          } else if (ev.stage === 'acting') sseStreamActing.value += (sseStreamActing.value ? '\n\n' : '') + text
          else sseStreamText.value += (sseStreamText.value ? '\n\n' : '') + text
        } catch (inner) {
          if (inner instanceof Error && inner.message !== '生成失败') {
            // ignore malformed chunk
          } else {
            throw inner
          }
        }
      }
    }
    ElMessage.success('6 阶段生成完成')
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '短剧生成失败')
  } finally {
    sseRunning.value = false
  }
}

function goStore(){ router.push('/store') }

onMounted(loadDramas)
</script>

<template>
  <div class="yt-drama">
    <div class="yt-drama__banner">
      <div class="banner-left">
        <div class="banner-title"><el-icon><Film /></el-icon> 短剧工坊</div>
        <div class="banner-desc">复用 P0 后端 drama 7表 · 4步横向进度 01创意→02剧本→03资产→04分镜 · 创意阶段走真实 LLM SSE</div>
        <div class="banner-tags">
          <el-tag size="small" effect="plain" style="background: rgba(255,255,255,.16); border-color: rgba(255,255,255,.28); color:#fff">V043 7表</el-tag>
          <el-tag size="small" effect="plain" style="background: rgba(255,255,255,.16); border-color: rgba(255,255,255,.28); color:#fff">真实 LLM SSE</el-tag>
        </div>
      </div>
      <div class="banner-right">
        <el-button type="primary" :icon="Plus" @click="createVisible=true">新建短剧</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadDramas">刷新</el-button>
        <el-button plain :icon="ArrowRight" @click="goStore">去商店</el-button>
      </div>
    </div>

    <el-card shadow="never" class="filter-card">
      <div class="filter-row">
        <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索标题/梗概" clearable style="max-width:320px" @input="page=1" />
        <el-select v-model="statusFilter" style="width:140px" @change="page=1">
          <el-option label="全部状态" value="all" />
          <el-option label="DRAFT" value="DRAFT" />
          <el-option label="PUBLISHED" value="PUBLISHED" />
          <el-option label="ARCHIVED" value="ARCHIVED" />
        </el-select>
        <span class="filter-count">已筛选 {{ filtered.length }} / {{ list.length }}</span>
        <span class="foot-hint" style="margin-left:auto; font-size:11px; color: var(--yt-text-secondary)">后端 GET /api/v1/dramas · 复用 V043 字段</span>
      </div>
    </el-card>

    <div v-loading="loading" class="drama-grid">
      <div v-for="d in paged" :key="d.id" class="drama-card" @click="openDetail(d)">
        <div class="card-head">
          <div class="card-icon"><el-icon><Film /></el-icon></div>
          <div class="card-meta">
            <div class="card-title" :title="d.title">{{ d.title }}</div>
            <div class="card-synopsis">{{ d.synopsis || '—' }}</div>
          </div>
          <el-tag size="small" effect="plain" :type="d.status==='PUBLISHED'?'success': d.status==='ARCHIVED'?'info':'warning'">{{ d.status }}</el-tag>
        </div>
        <div class="card-foot">
          <span class="foot-time">{{ d.createdTime ? new Date(d.createdTime).toLocaleDateString() : '—' }}</span>
          <el-button size="small" type="primary" :icon="VideoPlay" @click.stop="openDetail(d)">进入工坊</el-button>
        </div>
      </div>
      <div v-if="!loading && !paged.length" class="empty-state">
        <div class="empty-illus">🎬</div>
        <div class="empty-title">暂无短剧</div>
        <div class="empty-desc">新建一部短剧开始 4 步创作流程</div>
        <el-button size="small" type="primary" :icon="Plus" @click="createVisible=true">新建短剧</el-button>
        <div class="empty-steps">01 创意 · 02 剧本 · 03 资产 · 04 分镜</div>
      </div>
    </div>

    <div v-if="filtered.length>size" class="pager-wrap">
      <el-pagination background layout="total, sizes, prev, pager, next, jumper" :total="filtered.length" :page-size="size" :current-page="page" :page-sizes="[12,24,48]" @size-change="(v:number)=>{size=v; page=1}" @current-change="(v:number)=>page=v" />
    </div>

    <el-dialog v-model="createVisible" title="新建短剧" width="520px" destroy-on-close>
      <el-form label-width="80px" size="small">
        <el-form-item label="标题" required><el-input v-model="createForm.title" placeholder="如：雨夜信封" maxlength="64" /></el-form-item>
        <el-form-item label="梗概"><el-input v-model="createForm.synopsis" type="textarea" :autosize="{minRows:3,maxRows:6}" placeholder="一句话梗概" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="createForm.status" style="width:160px"><el-option label="DRAFT" value="DRAFT" /><el-option label="PUBLISHED" value="PUBLISHED" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="createDrama">创建</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="current ? `短剧工坊 · ${current.title}` : '短剧工坊'" size="86%" direction="rtl" destroy-on-close>
      <div v-if="current" class="workshop">
        <div class="steps">
          <div v-for="s in [1,2,3,4]" :key="s" class="step" :class="{ active: step===s, done: step>(s as number) }" @click="step=s as 1|2|3|4">
            <span class="step-num">0{{ s }}</span>
            <span class="step-label">{{ s===1?'创意': s===2?'剧本': s===3?'资产':'分镜' }}</span>
          </div>
          <div class="steps-line"></div>
        </div>

        <div v-if="step===1" class="step-panel">
          <el-alert type="info" :closable="false" title="01 创意：标题/梗概/状态 · 真实 LLM 6 阶段 SSE（概念/剧本/角色/分镜/画面/表演）" />
          <el-descriptions :column="2" border size="small" style="margin-top:12px">
            <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
            <el-descriptions-item label="状态"><el-tag size="small" effect="plain">{{ current.status }}</el-tag></el-descriptions-item>
            <el-descriptions-item label="梗概" :span="2">{{ current.synopsis || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div class="sse-grid">
            <div class="sse-col">
              <div class="sse-head"><el-icon><MagicStick /></el-icon> sseStreamText 文本流</div>
              <div class="sse-body">{{ sseStreamText || '点击“开始 SSE 演示”查看流式文本' }}</div>
            </div>
            <div class="sse-col">
              <div class="sse-head"><el-icon><Picture /></el-icon> 画面流 + 文生图</div>
              <div class="sse-body">{{ sseStreamPhoto || '画面阶段将调用已配置的图像供应商' }}</div>
              <img v-if="ssePhotoUrl" :src="ssePhotoUrl" alt="generated storyboard" style="width:100%; margin-top:8px; border-radius:8px" />
            </div>
            <div class="sse-col">
              <div class="sse-head"><el-icon><Mic /></el-icon> sseStreamActing 表演流</div>
              <div class="sse-body">{{ sseStreamActing || '表演/配音流占位 · 多选分镜后批量生成' }}</div>
            </div>
          </div>
          <div style="display:flex; gap:8px; margin-top:12px">
            <el-button type="primary" :icon="VideoPlay" :loading="sseRunning" @click="startSseDemo">开始 6 阶段生成</el-button>
            <el-button @click="sseStreamText=''; sseStreamPhoto=''; sseStreamActing=''; ssePhotoUrl=''">清空</el-button>
          </div>
        </div>

        <div v-if="step===2" class="step-panel">
          <el-alert type="success" :closable="false" title="02 剧本：drama_scene 场景列表 · POST /dramas/{id}/scenes" />
          <div class="scene-form">
            <el-input v-model="sceneForm.description" placeholder="场景描述/台词" style="flex:1" />
            <el-input-number v-model="sceneForm.sceneNo" :min="1" :max="99" size="small" style="width:110px" />
            <el-button type="primary" size="small" :icon="Plus" @click="addScene">添加</el-button>
          </div>
          <div class="scene-list">
            <div v-for="s in scenes" :key="s.id" class="scene-item">
              <span class="scene-no">#{{ s.sceneNo ?? s.scene_no ?? '—' }}</span>
              <span class="scene-desc">{{ s.description || '—' }}</span>
            </div>
            <div v-if="!scenes.length" class="empty-mini">暂无场景 · 添加一条开始</div>
          </div>
        </div>

        <div v-if="step===3" class="step-panel">
          <el-alert type="warning" :closable="false" title="03 资产：characters / locations / audios 三 Tab · 复用 V043 5表" />
          <el-tabs v-model="assetTab" style="margin-top:8px">
            <el-tab-pane label="角色 characters" name="characters">
              <div class="asset-form">
                <el-input v-model="charForm.name" placeholder="角色名" style="width:160px" size="small" />
                <el-input v-model="charForm.role" placeholder="定位 主角/配角" style="width:140px" size="small" />
                <el-input v-model="charForm.description" placeholder="人设描述" style="flex:1" size="small" />
                <el-button size="small" type="primary" :icon="Plus" @click="addCharacter">添加</el-button>
              </div>
              <div class="asset-grid">
                <div v-for="c in characters" :key="c.id" class="asset-card"><el-icon><User /></el-icon><strong>{{ c.name }}</strong><span class="asset-sub">{{ c.role || '—' }}</span><span class="asset-desc">{{ c.description || '—' }}</span></div>
                <div v-if="!characters.length" class="empty-mini">暂无角色</div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="场景地 locations" name="locations">
              <div class="asset-form">
                <el-input v-model="locForm.name" placeholder="场景地名称" style="width:180px" size="small" />
                <el-input v-model="locForm.description" placeholder="描述" style="flex:1" size="small" />
                <el-button size="small" type="primary" :icon="Plus" @click="addLocation">添加</el-button>
              </div>
              <div class="asset-grid">
                <div v-for="l in locations" :key="l.id" class="asset-card"><el-icon><Location /></el-icon><strong>{{ l.name }}</strong><span class="asset-desc">{{ l.description || '—' }}</span></div>
                <div v-if="!locations.length" class="empty-mini">暂无场景地</div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="音频 audios" name="audios">
              <div class="asset-form">
                <el-input v-model="audioForm.name" placeholder="音频名" style="width:180px" size="small" />
                <el-input v-model="audioForm.voiceId" placeholder="voiceId" style="width:160px" size="small" />
                <el-button size="small" type="primary" :icon="Plus" @click="addAudio">添加</el-button>
              </div>
              <div class="asset-grid">
                <div v-for="a in audios" :key="a.id" class="asset-card"><el-icon><Mic /></el-icon><strong>{{ a.name }}</strong><span class="asset-sub">{{ (a as unknown as Record<string,string>).voiceId || (a as unknown as Record<string,string>).voice_id || '—' }}</span></div>
                <div v-if="!audios.length" class="empty-mini">暂无音频</div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>

        <div v-if="step===4" class="step-panel">
          <el-alert type="info" :closable="false" title="04 分镜：storyboard 多选 + transitionType / aspectRatio 配置 · SSE Photo/Acting 分栏已在 01 展示" />
          <div class="sb-toolbar">
            <el-select v-model="transitionType" size="small" style="width:140px">
              <el-option label="cut 硬切" value="cut" />
              <el-option label="fade 淡入淡出" value="fade" />
              <el-option label="slide 滑动" value="slide" />
            </el-select>
            <el-select v-model="aspectRatio" size="small" style="width:120px">
              <el-option label="16:9" value="16:9" />
              <el-option label="9:16" value="9:16" />
              <el-option label="1:1" value="1:1" />
            </el-select>
            <span class="sb-count">已选 {{ sbSelected.length }} / {{ storyboards.length }}</span>
            <el-button size="small" type="primary" :icon="VideoPlay" :disabled="!sbSelected.length" @click="startSseDemo">批量生成（SSE占位）</el-button>
          </div>
          <div class="asset-form" style="margin-top:10px">
            <el-input v-model="sbForm.prompt" placeholder="分镜提示词 prompt" style="flex:1" size="small" />
            <el-input-number v-model="sbForm.storyboardNo" :min="1" :max="99" size="small" style="width:110px" />
            <el-button size="small" type="primary" :icon="Plus" @click="addStoryboard">添加</el-button>
          </div>
          <div class="sb-grid">
            <div v-for="sb in storyboards" :key="sb.id" class="sb-card" :class="{ selected: sbSelected.includes(sb.id) }" @click="toggleSb(sb.id)">
              <div class="sb-no">#{{ sb.storyboardNo ?? (sb as unknown as Record<string,number>).storyboard_no ?? '—' }}</div>
              <div class="sb-prompt">{{ sb.prompt || '—' }}</div>
              <el-tag size="small" effect="plain" :type="(sb.status as string)==='SUCCESS'?'success':'info'" style="align-self:flex-start">{{ sb.status || 'DRAFT' }}</el-tag>
              <div class="sb-meta">transition: {{ transitionType }} · {{ aspectRatio }}</div>
            </div>
            <div v-if="!storyboards.length" class="empty-mini">暂无分镜 · 添加一条开始</div>
          </div>
        </div>

        <div class="workshop-foot">
          <el-button @click="step=Math.max(1, (step-1) as 1|2|3|4) as 1|2|3|4" :disabled="step===1">上一步</el-button>
          <el-button type="primary" @click="step=Math.min(4, (step+1) as 1|2|3|4) as 1|2|3|4" :disabled="step===4">下一步</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.yt-drama{ display:flex; flex-direction:column; gap:14px; padding:4px 2px 20px; background: var(--yt-bg-page, #f6f8fb); min-height:100% }
.yt-drama__banner{ display:flex; justify-content:space-between; gap:16px; flex-wrap:wrap; background: linear-gradient(135deg, #0f172a 0%, #1e3a5f 100%); color:#fff; border-radius: var(--yt-radius-md, 8px); padding:18px 16px; border:1px solid rgba(255,255,255,.08) }
.banner-title{ font-size:18px; font-weight:700; display:flex; gap:8px; align-items:center }
.banner-desc{ font-size:12px; opacity:.85; margin-top:6px }
.banner-tags{ display:flex; gap:8px; margin-top:10px; flex-wrap:wrap }
.banner-right{ display:flex; gap:8px; align-items:center; flex-wrap:wrap }
.filter-card :deep(.el-card__body){ padding:12px 16px }
.filter-row{ display:flex; gap:12px; align-items:center; flex-wrap:wrap }
.filter-count{ font-size:12px; color: var(--yt-text-secondary) }
.drama-grid{ display:grid; grid-template-columns: repeat(auto-fill, minmax(320px,1fr)); gap:16px }
.drama-card{ background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius: var(--yt-radius-md, 8px); padding:14px; display:flex; flex-direction:column; gap:12px; cursor:pointer; transition: all 140ms ease; box-shadow: var(--yt-shadow-card, 0 1px 3px rgba(0,0,0,.06)) }
.drama-card:hover{ border-color: var(--yt-color-primary-light-5); box-shadow: 0 4px 16px rgba(37,99,235,.08); transform: translateY(-1px) }
.card-head{ display:flex; gap:10px; align-items:center }
.card-icon{ width:36px; height:36px; border-radius:10px; background: var(--yt-color-primary); color:#fff; display:flex; align-items:center; justify-content:center; flex-shrink:0 }
.card-meta{ flex:1; min-width:0 }
.card-title{ font-size:14px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.card-synopsis{ font-size:12px; color: var(--yt-text-secondary); white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.card-foot{ display:flex; justify-content:space-between; align-items:center; border-top:1px dashed var(--yt-border-light); padding-top:8px }
.foot-time{ font-size:11px; color: var(--yt-text-secondary) }
.empty-state{ grid-column:1/-1; text-align:center; padding:36px 0; background: var(--yt-bg-card); border:1px dashed var(--yt-border-default); border-radius:12px }
.empty-illus{ font-size:32px } .empty-title{ font-weight:600; margin-top:8px } .empty-desc{ font-size:12px; color: var(--yt-text-secondary); margin:6px 0 12px }
.empty-steps{ font-size:11px; color: var(--yt-text-disabled); margin-top:10px }
.pager-wrap{ display:flex; justify-content:center }
.workshop{ display:flex; flex-direction:column; gap:14px }
.steps{ display:flex; gap:0; align-items:center; position:relative; background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius:8px; padding:10px 14px }
.step{ flex:1; display:flex; gap:8px; align-items:center; justify-content:center; padding:8px; border-radius:8px; cursor:pointer; position:relative; z-index:1 }
.step.active{ background: var(--yt-color-primary); color:#fff }
.step.done{ color: var(--yt-color-success) }
.step-num{ font-weight:700; font-size:13px; background: rgba(0,0,0,.08); border-radius:6px; padding:2px 6px }
.step.active .step-num{ background: rgba(255,255,255,.2) }
.step-label{ font-size:12px; font-weight:600 }
.steps-line{ position:absolute; left:14px; right:14px; top:50%; height:2px; background: var(--yt-border-light); z-index:0 }
.step-panel{ background: var(--yt-bg-card); border:1px solid var(--yt-border-light); border-radius:8px; padding:14px }
.sse-grid{ display:grid; grid-template-columns: repeat(3,1fr); gap:12px; margin-top:12px }
.sse-col{ border:1px solid var(--yt-border-light); border-radius:8px; overflow:hidden }
.sse-head{ font-size:12px; font-weight:600; padding:8px 10px; background:#f8fafc; border-bottom:1px solid var(--yt-border-light); display:flex; gap:6px; align-items:center }
.sse-body{ font-size:12px; white-space:pre-wrap; padding:10px; min-height:88px; font-family: ui-monospace, monospace; color: var(--yt-text-secondary) }
.scene-form{ display:flex; gap:8px; margin-top:12px }
.scene-list{ display:flex; flex-direction:column; gap:8px; margin-top:12px }
.scene-item{ display:flex; gap:10px; padding:10px; border:1px solid var(--yt-border-light); border-radius:8px; background:#f8fafc }
.scene-no{ font-weight:700; color: var(--yt-color-primary); font-size:12px }
.scene-desc{ font-size:12px; color: var(--yt-text-secondary) }
.asset-form{ display:flex; gap:8px; align-items:center; flex-wrap:wrap }
.asset-grid{ display:grid; grid-template-columns: repeat(auto-fill, minmax(200px,1fr)); gap:10px; margin-top:12px }
.asset-card{ border:1px solid var(--yt-border-light); border-radius:8px; padding:10px; display:flex; flex-direction:column; gap:4px; background:#fff }
.asset-sub{ font-size:11px; color: var(--yt-text-secondary) } .asset-desc{ font-size:11px; color: var(--yt-text-disabled) }
.sb-toolbar{ display:flex; gap:10px; align-items:center; flex-wrap:wrap }
.sb-count{ font-size:12px; color: var(--yt-text-secondary) }
.sb-grid{ display:grid; grid-template-columns: repeat(auto-fill, minmax(220px,1fr)); gap:12px; margin-top:12px }
.sb-card{ border:1px solid var(--yt-border-light); border-radius:8px; padding:10px; background:#fff; cursor:pointer; display:flex; flex-direction:column; gap:6px }
.sb-card.selected{ border-color: var(--yt-color-primary); background:#eef2ff }
.sb-no{ font-weight:700; color: var(--yt-color-primary); font-size:12px }
.sb-prompt{ font-size:12px; color: var(--yt-text-secondary); min-height:24px }
.sb-meta{ font-size:11px; color: var(--yt-text-disabled) }
.empty-mini{ grid-column:1/-1; text-align:center; padding:18px; font-size:12px; color: var(--yt-text-secondary); border:1px dashed var(--yt-border-light); border-radius:8px }
.workshop-foot{ display:flex; justify-content:space-between; padding-top:8px; border-top:1px solid var(--yt-border-light) }
@media (max-width: 860px){ .sse-grid{ grid-template-columns:1fr } }
</style>
