<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, VideoPlay, Film, RefreshRight } from '@element-plus/icons-vue'
import {
  STORYBOARD_SHOT_TYPE_OPTIONS,
  batchGenerateProjectVideos,
  composeDramaProject,
  composeStatusTag,
  createDramaProject,
  generateStoryboardVideo,
  getDramaProject,
  getStoryboardVideo,
  listDramaProjects,
  storyboardVideoStatusTag,
  type DramaProject,
  type StoryboardInput,
} from '@/api/dramaStoryboard'

/**
 * 分镜视频工作室。设计来源: ai-depth-parity.md S2.2。
 * 左: 项目列表 + 新建 | 右: 详情(分镜表编辑 / 单镜生成 / 批量 / 合成 / 状态轮询)。
 */

const projects = ref<DramaProject[]>([])
const listLoading = ref(false)
const keyword = ref('')
const currentProjectId = ref('')
const currentProject = ref<DramaProject | null>(null)
const detailLoading = ref(false)

function normalizeList(data: DramaProject[] | { records?: DramaProject[] } | null): DramaProject[] {
  if (!data) return []
  if (Array.isArray(data)) return data
  return (data.records as DramaProject[]) || []
}

async function loadProjects() {
  listLoading.value = true
  try {
    const data = await listDramaProjects({
      page: 1,
      size: 100,
      keyword: keyword.value.trim() || undefined,
    })
    projects.value = normalizeList(data as DramaProject[] | { records?: DramaProject[] })
    if (currentProjectId.value && !projects.value.some((p) => p.id === currentProjectId.value)) {
      // keep detail if still selected even if not in first page
    }
  } catch {
    projects.value = []
  } finally {
    listLoading.value = false
  }
}

const filteredProjects = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return projects.value
  return projects.value.filter(
    (p) =>
      (p.title || '').toLowerCase().includes(kw) ||
      (p.synopsis || '').toLowerCase().includes(kw)
  )
})

async function openProject(id: string) {
  currentProjectId.value = id
  detailLoading.value = true
  try {
    currentProject.value = await getDramaProject(id)
  } catch (e: unknown) {
    currentProject.value = null
    ElMessage.error((e as { message?: string })?.message || '加载项目失败')
  } finally {
    detailLoading.value = false
  }
}

// ==================== 新建项目 ====================
const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  title: '',
  synopsis: '',
  artStyle: '写实电影感',
  aspectRatio: '16:9',
})
const createBoards = ref<StoryboardInput[]>([
  { sceneNo: 1, shotType: '全景', locationName: '', imagePrompt: '', videoPrompt: '', durationSeconds: 4 },
])

function addCreateBoard() {
  const next = createBoards.value.length + 1
  createBoards.value.push({
    sceneNo: next,
    shotType: '中景',
    locationName: '',
    imagePrompt: '',
    videoPrompt: '',
    durationSeconds: 4,
  })
}

function removeCreateBoard(idx: number) {
  if (createBoards.value.length <= 1) return
  createBoards.value.splice(idx, 1)
}

function openCreate() {
  createForm.title = ''
  createForm.synopsis = ''
  createForm.artStyle = '写实电影感'
  createForm.aspectRatio = '16:9'
  createBoards.value = [
    { sceneNo: 1, shotType: '全景', locationName: '', imagePrompt: '', videoPrompt: '', durationSeconds: 4 },
  ]
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.title.trim()) {
    ElMessage.warning('请输入项目标题')
    return
  }
  const boards = createBoards.value
    .map((b, i) => ({
      sceneNo: b.sceneNo || i + 1,
      shotType: b.shotType,
      locationName: b.locationName?.trim() || undefined,
      imagePrompt: b.imagePrompt?.trim() || undefined,
      // 后端 videoPrompt 必填：缺省时用 imagePrompt 兜底
      videoPrompt: b.videoPrompt?.trim() || b.imagePrompt?.trim() || undefined,
      durationSeconds: b.durationSeconds || undefined,
    }))
    .filter((b) => Boolean(b.videoPrompt))
  if (boards.length === 0) {
    ElMessage.warning('至少填写一镜的 videoPrompt 或 imagePrompt')
    return
  }
  creating.value = true
  try {
    const p = await createDramaProject({
      title: createForm.title.trim(),
      synopsis: createForm.synopsis?.trim() || undefined,
      artStyle: createForm.artStyle?.trim() || undefined,
      aspectRatio: createForm.aspectRatio?.trim() || undefined,
      storyboards: boards,
    })
    ElMessage.success('项目已创建')
    createVisible.value = false
    await loadProjects()
    if (p?.id) await openProject(p.id)
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '创建项目失败')
  } finally {
    creating.value = false
  }
}

// ==================== 单镜 / 批量 / 合成 ====================
const genBusy = ref<Record<string, boolean>>({})
const batchBusy = ref(false)
const composeBusy = ref(false)

async function generateOne(row: { id?: string; sceneNo?: number }) {
  const id = row.id
  if (!id) return
  genBusy.value = { ...genBusy.value, [id]: true }
  try {
    await generateStoryboardVideo(id, { idempotencyKey: `sb-${id}-${Date.now()}` })
    ElMessage.success(`场景 #${row.sceneNo ?? '?'} 已提交生成`)
    await openProject(currentProjectId.value)
    armPolling()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '单镜生成失败')
  } finally {
    genBusy.value = { ...genBusy.value, [id]: false }
  }
}

async function batchGenerate() {
  if (!currentProjectId.value) return
  try {
    await ElMessageBox.confirm(
      '将对项目内全部分镜提交批量视频生成（同地点串行 lastFrame，跨组并发）。是否继续？',
      '批量生成',
      { type: 'warning', confirmButtonText: '开始生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  batchBusy.value = true
  try {
    const res = await batchGenerateProjectVideos(currentProjectId.value, {
      idempotencyKey: `batch-${currentProjectId.value}-${Date.now()}`,
    })
    ElMessage.success(res?.message || `批量生成完成（成功 ${res?.succeeded ?? 0} / 共 ${res?.total ?? 0}）`)
    await openProject(currentProjectId.value)
    armPolling()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '批量生成失败')
  } finally {
    batchBusy.value = false
  }
}

async function composeProject() {
  if (!currentProjectId.value) return
  try {
    await ElMessageBox.confirm(
      '将按场景顺序串联已成功生成的视频进行合成。若存在未成功分镜将拒绝合成。是否继续？',
      '合成视频',
      { type: 'warning', confirmButtonText: '开始合成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  composeBusy.value = true
  try {
    const res = await composeDramaProject(currentProjectId.value, {
      idempotencyKey: `compose-${currentProjectId.value}-${Date.now()}`,
    })
    ElMessage.success(res?.message || '合成任务已提交')
    await openProject(currentProjectId.value)
    armPolling()
  } catch (e: unknown) {
    ElMessage.error((e as { message?: string })?.message || '合成失败')
  } finally {
    composeBusy.value = false
  }
}

// ==================== 状态轮询 ====================
let pollTimer: ReturnType<typeof setInterval> | null = null

function hasActiveBoard(): boolean {
  const boards = currentProject.value?.storyboards || []
  return boards.some((b) => b.videoStatus === 'PENDING' || b.videoStatus === 'GENERATING')
}

function armPolling() {
  stopPolling()
  if (!currentProjectId.value) return
  if (!hasActiveBoard()) return
  pollTimer = setInterval(async () => {
    if (!currentProjectId.value) {
      stopPolling()
      return
    }
    try {
      // 对 GENERATING 的镜头单独轮询，其余刷新项目详情
      const boards = currentProject.value?.storyboards || []
      const active = boards.filter((b) => b.videoStatus === 'GENERATING' || b.videoStatus === 'PENDING')
      for (const b of active.slice(0, 8)) {
        try {
          const r = await getStoryboardVideo(b.id)
          if (r) {
            b.videoStatus = r.videoStatus
            b.videoUrl = r.videoUrl
            b.lastFrameUrl = r.lastFrameUrl
            b.mediaJobId = r.mediaJobId
            b.errorMessage = r.errorMessage
          }
        } catch {
          // single poll fail ignored
        }
      }
      if (!hasActiveBoard()) {
        stopPolling()
        await openProject(currentProjectId.value)
      }
    } catch {
      stopPolling()
    }
  }, 4000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// ==================== 分镜行内编辑展示 ====================
const boards = computed(() => currentProject.value?.storyboards || [])

function statusText(s?: string) {
  if (s === 'SUCCEEDED') return '成功'
  if (s === 'FAILED') return '失败'
  if (s === 'GENERATING') return '生成中'
  if (s === 'PENDING') return '待生成'
  return s || '—'
}

onMounted(() => {
  void loadProjects()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <div class="storyboard-page">
    <div class="page-head">
      <div>
        <h2 class="head-title">分镜视频工作室</h2>
        <p class="head-sub">项目管理 · 分镜编辑 · 单镜/批量视频生成 · 串联合成。设计来源 platform-remaining-parity S2.2。</p>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadProjects">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建项目</el-button>
      </div>
    </div>

    <div class="layout">
      <!-- 左: 项目列表 -->
      <el-card shadow="never" class="project-panel">
        <template #header>
          <div class="panel-head">
            <span>项目</span>
            <el-input
              v-model="keyword"
              size="small"
              placeholder="搜索标题/简介"
              clearable
              style="width: 140px"
              @change="loadProjects"
            />
          </div>
        </template>
        <div v-loading="listLoading" class="project-list">
          <div
            v-for="p in filteredProjects"
            :key="p.id"
            class="project-item"
            :class="{ active: p.id === currentProjectId }"
            @click="openProject(p.id)"
          >
            <div class="p-title">{{ p.title }}</div>
            <div class="p-meta">
              <el-tag size="small" :type="composeStatusTag(p.composeStatus)" effect="plain">
                {{ p.composeStatus || 'NONE' }}
              </el-tag>
              <span>{{ p.aspectRatio || '—' }}</span>
              <span>{{ p.artStyle || '' }}</span>
            </div>
          </div>
          <el-empty v-if="!listLoading && filteredProjects.length === 0" description="暂无项目" :image-size="64" />
        </div>
      </el-card>

      <!-- 右: 详情 -->
      <div class="detail-panel">
        <el-empty v-if="!currentProjectId" description="选择左侧项目查看分镜，或新建项目" />

        <template v-else>
          <el-card v-loading="detailLoading" shadow="never" class="detail-card">
            <template #header>
              <div class="panel-head">
                <span>{{ currentProject?.title || '项目详情' }}</span>
                <el-tag size="small" :type="composeStatusTag(currentProject?.composeStatus)">
                  合成 {{ currentProject?.composeStatus || 'NONE' }}
                </el-tag>
                <div class="spacer" />
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :icon="VideoPlay"
                  :loading="batchBusy"
                  :disabled="boards.length === 0"
                  @click="batchGenerate"
                >
                  批量生成视频
                </el-button>
                <el-button
                  size="small"
                  type="success"
                  plain
                  :icon="Film"
                  :loading="composeBusy"
                  @click="composeProject"
                >
                  合成视频
                </el-button>
                <el-button size="small" :icon="RefreshRight" @click="openProject(currentProjectId)">
                  刷新详情
                </el-button>
              </div>
            </template>

            <el-descriptions v-if="currentProject" :column="3" border size="small" class="proj-desc">
              <el-descriptions-item label="标题">{{ currentProject.title }}</el-descriptions-item>
              <el-descriptions-item label="画幅">{{ currentProject.aspectRatio || '—' }}</el-descriptions-item>
              <el-descriptions-item label="画风">{{ currentProject.artStyle || '—' }}</el-descriptions-item>
              <el-descriptions-item label="简介" :span="3">
                {{ currentProject.synopsis || '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="合成产物" :span="3">
                {{ currentProject.composedPath || '—' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-table :data="boards" size="small" border class="board-table">
              <el-table-column prop="sceneNo" label="#" width="56" align="center" />
              <el-table-column prop="shotType" label="景别" width="90">
                <template #default="{ row }">{{ row.shotType || '—' }}</template>
              </el-table-column>
              <el-table-column prop="locationName" label="场景地" width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.locationName || '—' }}</template>
              </el-table-column>
              <el-table-column prop="imagePrompt" label="图像提示词" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">{{ row.imagePrompt || '—' }}</template>
              </el-table-column>
              <el-table-column prop="videoPrompt" label="视频提示词" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ row.videoPrompt || '—' }}</template>
              </el-table-column>
              <el-table-column label="时长" width="70" align="center">
                <template #default="{ row }">{{ row.durationSeconds ? row.durationSeconds + 's' : '—' }}</template>
              </el-table-column>
              <el-table-column label="视频状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="storyboardVideoStatusTag(row.videoStatus)">
                    {{ statusText(row.videoStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="视频" min-width="140">
                <template #default="{ row }">
                  <a
                    v-if="row.videoUrl"
                    :href="row.videoUrl"
                    target="_blank"
                    rel="noopener"
                    class="video-link"
                  >
                    {{ row.videoUrl.slice(0, 36) }}…
                  </a>
                  <span v-else class="muted">
                    {{ row.errorMessage || '—' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    :loading="genBusy[row.id]"
                    @click="generateOne(row)"
                  >
                    生成
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </template>
      </div>
    </div>

    <!-- 新建项目 -->
    <el-dialog v-model="createVisible" title="新建分镜项目" width="760px" destroy-on-close top="6vh">
      <el-form label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" placeholder="例如：城市夜景广告片" maxlength="120" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.synopsis" type="textarea" :rows="2" placeholder="可选剧情/风格简介" />
        </el-form-item>
        <el-form-item label="画风">
          <el-input v-model="createForm.artStyle" placeholder="例如：写实电影感 / 二次元" />
        </el-form-item>
        <el-form-item label="画幅">
          <el-select v-model="createForm.aspectRatio" style="width: 180px">
            <el-option label="16:9" value="16:9" />
            <el-option label="9:16" value="9:16" />
            <el-option label="1:1" value="1:1" />
            <el-option label="21:9" value="21:9" />
          </el-select>
        </el-form-item>
      </el-form>

      <div class="boards-edit-head">
        <strong>分镜脚本</strong>
        <el-button size="small" type="primary" plain :icon="Plus" @click="addCreateBoard">添加一镜</el-button>
      </div>
      <el-table :data="createBoards" size="small" border>
        <el-table-column label="#" width="70">
          <template #default="{ row }">
            <el-input-number v-model="row.sceneNo" :min="1" size="small" controls-position="right" style="width: 56px" />
          </template>
        </el-table-column>
        <el-table-column label="景别" width="110">
          <template #default="{ row }">
            <el-select v-model="row.shotType" size="small" style="width: 90px">
              <el-option v-for="t in STORYBOARD_SHOT_TYPE_OPTIONS" :key="t" :label="t" :value="t" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="场景地" width="120">
          <template #default="{ row }">
            <el-input v-model="row.locationName" size="small" placeholder="同地点串行" />
          </template>
        </el-table-column>
        <el-table-column label="图像提示词" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.imagePrompt" size="small" placeholder="可选" />
          </template>
        </el-table-column>
        <el-table-column label="视频提示词" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.videoPrompt" size="small" placeholder="镜头运动/主体/氛围" />
          </template>
        </el-table-column>
        <el-table-column label="秒" width="80">
          <template #default="{ row }">
            <el-input-number v-model="row.durationSeconds" :min="1" :max="30" size="small" controls-position="right" style="width: 64px" />
          </template>
        </el-table-column>
        <el-table-column label="" width="64" fixed="right">
          <template #default="{ $index }">
            <el-button size="small" text type="danger" :disabled="createBoards.length <= 1" @click="removeCreateBoard($index)">
              删
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.storyboard-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.head-title {
  margin: 0 0 4px;
  font-size: 18px;
}
.head-sub {
  margin: 0;
  color: var(--yt-text-secondary, #909399);
  font-size: 12px;
}
.head-actions {
  display: flex;
  gap: 8px;
}
.layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
  align-items: start;
}
.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}
.spacer {
  flex: 1;
}
.project-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 640px;
  overflow: auto;
}
.project-item {
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  padding: 10px;
  cursor: pointer;
}
.project-item:hover {
  border-color: var(--el-color-primary-light-5);
}
.project-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.p-title {
  font-weight: 600;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.p-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--yt-text-secondary, #909399);
}
.detail-panel {
  min-width: 0;
}
.proj-desc {
  margin-bottom: 12px;
}
.board-table {
  width: 100%;
}
.video-link {
  color: var(--el-color-primary);
  font-size: 12px;
  word-break: break-all;
}
.muted {
  color: var(--yt-text-secondary, #909399);
  font-size: 12px;
}
.boards-edit-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0 8px;
}
@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
