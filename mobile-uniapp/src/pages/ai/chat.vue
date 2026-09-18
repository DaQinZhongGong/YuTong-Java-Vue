<template>
  <view class="ai-chat">
    <view class="header">
      <view class="header-left">
        <text class="title">AI 对话</text>
        <text v-if="appName" class="app-tag">{{ appName }}</text>
      </view>
      <view class="header-right">
        <text class="sub" @click="goHistory">历史</text>
        <text class="sub" @click="clearAll">清空</text>
      </view>
    </view>

    <scroll-view class="msg-list" scroll-y :scroll-top="scrollTop" :scroll-with-animation="true">
      <view v-for="(m, i) in messages" :key="m.id || i" class="bubble" :class="m.role">
        <view class="bubble-avatar" :class="m.role">
          <text class="avatar-text">{{ m.role === 'user' ? '我' : 'AI' }}</text>
        </view>
        <view class="bubble-box">
          <text class="msg-time">{{ formatRelative(m.createTime) }}</text>
          <view class="bubble-inner" :class="{ error: m.isError }">
            <view v-if="m.type === 'audio' && m.audioUrl" class="audio-row">
              <text class="audio-icon" @click="toggleAudio(i)">▶</text>
              <text class="audio-duration">{{ Math.ceil((m.duration || 0) / 1000) }}″</text>
              <text v-if="m.playing" class="audio-playing">播放中…</text>
            </view>
            <image v-if="m.imageUrl" :src="m.imageUrl" mode="widthFix" class="msg-image" @click="previewImage(m.imageUrl)" />
            <text class="msg-text">{{ m.content }}</text>
            <text v-if="m.streaming" class="cursor">▋</text>
          </view>
          <view v-if="m.role === 'assistant' && !m.streaming" class="feedback-row">
            <text class="fb-btn" :class="{ active: m.speaking }" @click="handleSpeak(m, i)">🔊</text>
            <text class="fb-btn" :class="{ active: m.liked }" @click="handleLike(m, i)">👍</text>
            <text class="fb-btn" :class="{ active: m.disliked }" @click="handleDislike(m, i)">👎</text>
            <text class="fb-hint">内容由大模型生成，仅供参考</text>
          </view>
        </view>
      </view>

      <view v-if="typingText" class="bubble assistant">
        <view class="bubble-avatar assistant"><text class="avatar-text">AI</text></view>
        <view class="bubble-box">
          <view class="bubble-inner typing">
            <text class="msg-text">{{ typingText }}</text><text class="cursor">▋</text>
          </view>
        </view>
      </view>

      <view v-if="!messages.length && !typingText" class="empty-state">
        <text class="empty-title">还没有对话</text>
        <text class="empty-desc">试试发送一条消息、上传图片或按住录音</text>
      </view>
    </scroll-view>

    <view v-if="selectedImage" class="preview-bar">
      <image :src="selectedImage" class="preview-thumb" mode="aspectFill" @click="previewImage(selectedImage)" />
      <text class="preview-remove" @click="removeImage">✕ 移除</text>
      <text v-if="uploadedUrl" class="preview-url">已上传</text>
    </view>

    <view v-if="showRecordModal" class="record-modal">
      <view class="record-dot"></view>
      <text class="record-text">录音中 {{ recordDuration }}s</text>
      <text class="record-hint">松开结束 · 将上传到文件服务</text>
    </view>

    <view class="input-bar">
      <view class="tool-btn" @click="chooseImage"><text class="tool-icon">🖼</text></view>
      <view
        class="tool-btn record"
        :class="{ recording: isRecording }"
        @touchstart.prevent="startRecord"
        @touchend.prevent="stopRecord"
        @mousedown.prevent="startRecord"
        @mouseup.prevent="stopRecord"
      >
        <text class="tool-icon">🎙</text>
      </view>
      <input v-model="input" class="input" placeholder="输入问题" confirm-type="send" :disabled="wsLoading" @confirm="handleSend" />
      <button v-if="!sending && !wsLoading" class="btn primary" size="mini" @click="handleSend">发送</button>
      <button v-else class="btn danger" size="mini" @click="stop">停止</button>
    </view>
    <view class="foot-hint">{{ connectionHint }}</view>
  </view>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { relativeTime } from '@/utils/relativeTime'
import { getToken, getUser } from '@/store/auth'
import { BASE_URL, get as apiGet } from '@/utils/request'
import { getDownloadUrl, uploadFile } from '@/api/file'

interface Msg {
  id?: string
  conversationId?: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
  createTime?: number
  isError?: boolean
  liked?: boolean
  disliked?: boolean
  speaking?: boolean
  type?: 'text' | 'audio'
  imageUrl?: string
  audioUrl?: string
  duration?: number
  playing?: boolean
}

const input = ref('')
const sending = ref(false)
const wsLoading = ref(false)
const typingText = ref('')
const messages = ref<Msg[]>([{
  role: 'assistant',
  content: '哈喽！我是 YuTong 智能助手，不管是生活里的小疑问，还是工作学习上想找点灵感，我都很乐意陪你一起琢磨。',
  createTime: Date.now(),
}])
const scrollTop = ref(0)
let abort: AbortController | null = null
const conversationId = ref('')
const lastAssistantMessageId = ref('')
// V052 P2-C 分支链: 最后一个服务端消息 id (SSE done / WS [DONE] 回填, 临时 id 不计入)
const lastServerMessageId = ref('')
const connectionHint = ref('正在连接 WS 流式通道…')

const appId = ref('')
const appName = ref('')
const agentId = ref('')
const systemPrompt = ref('')
const kbId = ref('')

const selectedImage = ref('')
const uploadedUrl = ref('')
const uploadedFileId = ref('')

const isRecording = ref(false)
const showRecordModal = ref(false)
const recordDuration = ref(0)
let recordTimer: ReturnType<typeof setInterval> | null = null
let recorderManager: UniApp.RecorderManager | null = null
let innerAudio: UniApp.InnerAudioContext | null = null
let wsConnected = false
let h5MediaRecorder: MediaRecorder | null = null
let h5Chunks: Blob[] = []
let h5RecordStart = 0

function formatRelative(ts?: number) {
  if (!ts) return '刚刚'
  return relativeTime(ts)
}

function scrollBottom() {
  nextTick(() => { scrollTop.value = 99999 })
}

function buildWsUrl(): string {
  const token = getToken() || (uni.getStorageSync('yutong_token') as string) || ''
  const user = getUser()
  const userId = user?.id || ''
  const httpBase = ((import.meta.env.VITE_API_BASE_URL as string) || BASE_URL || '/api/v1')
  let wsBase = (import.meta.env.VITE_WS_URL as string) || ''
  if (!wsBase) {
    if (httpBase.startsWith('/')) {
      const proto = typeof location !== 'undefined' && location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host = typeof location !== 'undefined' ? location.host : 'localhost:20060'
      wsBase = `${proto}//${host}/ws/chat`
    } else {
      wsBase = httpBase.replace(/^http/, 'ws').replace(/\/api\/v1\/?$/, '/ws/chat')
    }
  }
  const params: string[] = []
  if (token) params.push('Authorization=' + encodeURIComponent('Bearer ' + token))
  if (appId.value) params.push('appId=' + encodeURIComponent(appId.value))
  if (userId) params.push('userId=' + encodeURIComponent(userId))
  return wsBase + (params.length ? '?' + params.join('&') : '')
}

function connectWs(autoSendAfter?: boolean) {
  const url = buildWsUrl()
  try {
    uni.connectSocket({ url })
  } catch {
    connectionHint.value = 'WS 不可用，将使用 SSE'
    return
  }
  uni.onSocketOpen(() => {
    wsConnected = true
    wsLoading.value = false
    connectionHint.value = 'WS 流式已连接 · 失败自动回退 SSE'
    if (autoSendAfter && input.value.trim()) handleSend()
  })
  uni.onSocketError(() => {
    wsConnected = false
    connectionHint.value = 'WS 失败，将使用 SSE 流式'
  })
  uni.onSocketClose(() => {
    wsConnected = false
  })
  uni.onSocketMessage((res: { data: string }) => {
    const raw = res.data as string
    // V052 P2-C: JSON done 帧 (含落库 DB id) 优先; 裸 [DONE] 兼容旧服务端
    if (raw && raw.trimStart().startsWith('{')) {
      try {
        const frame = JSON.parse(raw) as { done?: boolean; messageId?: string; conversationId?: string }
        if (frame.done) {
          if (frame.conversationId) conversationId.value = frame.conversationId
          if (frame.messageId) {
            lastAssistantMessageId.value = frame.messageId
            lastServerMessageId.value = frame.messageId
          }
        }
      } catch { /* 非 JSON 帧走下行分支 */ }
    }
    if (raw === '[DONE]' || isJsonDone(raw)) {
      wsLoading.value = false
      sending.value = false
      if (typingText.value) {
        messages.value.push({
          role: 'assistant',
          content: typingText.value,
          createTime: Date.now(),
          conversationId: conversationId.value,
          id: lastAssistantMessageId.value || undefined,
        })
        if (lastAssistantMessageId.value) lastServerMessageId.value = lastAssistantMessageId.value
        typingText.value = ''
      }
      scrollBottom()
      return
    }
    if (!raw || raw.trim() === '') return
    try {
      const json = JSON.parse(raw) as { content?: string; data?: string; conversationId?: string; messageId?: string }
      if (json.conversationId) conversationId.value = json.conversationId
      if (json.messageId) lastAssistantMessageId.value = json.messageId
      if (typeof json.data === 'string' && json.data.includes('错误')) {
        wsLoading.value = false
        sending.value = false
        messages.value.push({ role: 'assistant', content: json.data, isError: true, createTime: Date.now() })
        scrollBottom()
        return
      }
      const chunk = json.content || ''
      if (chunk) {
        typingText.value += chunk
        scrollBottom()
      }
    } catch {
      if (raw.includes('错误')) {
        wsLoading.value = false
        sending.value = false
      }
    }
  })
}

interface WsAttachment {
  type?: string
  url: string
  name?: string
  format?: string
}

/** JSON done 帧判定 (V052 P2-C, 与裸 [DONE] 双轨兼容) */
function isJsonDone(raw: string): boolean {
  if (!raw || !raw.trimStart().startsWith('{')) return false
  try {
    return (JSON.parse(raw) as { done?: boolean }).done === true
  } catch {
    return false
  }
}

function sendViaWs(content: string, chatId: string, fileUrl?: string, attachments?: WsAttachment[]) {
  if (!wsConnected) return false
  const payload: Record<string, unknown> = {
    content,
    chatId,
    conversationId: conversationId.value || undefined,
    scenario: 'PLATFORM_QA',
  }
  if (agentId.value) payload.agentId = agentId.value
  // V052 P2-C 分支链: 挂到上条服务端消息 (无则链首, 后端同样规则校验)
  if (lastServerMessageId.value) payload.parentMessageId = lastServerMessageId.value
  if (systemPrompt.value) payload.systemPrompt = systemPrompt.value
  if (kbId.value) payload.kbId = kbId.value
  // 多模态 attachments 数组优先（P1-7）；回退到单 fileUrl 走文本拼接
  if (attachments && attachments.length) {
    payload.attachments = attachments
    payload.chatType = 1
  } else if (fileUrl) {
    payload.fileUrl = fileUrl
    payload.chatType = 1
  }
  try {
    uni.sendSocketMessage({ data: JSON.stringify(payload) })
    return true
  } catch {
    return false
  }
}

function chooseImage() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const path = res.tempFilePaths[0]
      selectedImage.value = path
      uploadImage(path)
    },
    fail: () => uni.showToast({ title: '选择图片失败', icon: 'none' }),
  })
}

async function uploadImage(filePath: string) {
  uni.showLoading({ title: '上传中...', mask: true })
  try {
    const file = await uploadFile(filePath)
    uploadedFileId.value = file.id
    uploadedUrl.value = await getDownloadUrl(file.id)
    selectedImage.value = uploadedUrl.value
    uni.showToast({ title: '上传成功', icon: 'success' })
  } catch {
    selectedImage.value = ''
    uploadedUrl.value = ''
    uploadedFileId.value = ''
    uni.showToast({ title: '图片上传失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function uploadBlobAudio(blobUrl: string): Promise<{ id: string; url: string }> {
  const blob = await fetch(blobUrl).then((r) => r.blob())
  const ext = blob.type.includes('webm') ? 'webm' : blob.type.includes('mp4') ? 'm4a' : 'mp3'
  const form = new FormData()
  form.append('file', blob, `voice-${Date.now()}.${ext}`)
  const token = getToken() || ''
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE_URL + '/files/upload', { method: 'POST', headers, body: form })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = await res.json() as { data?: { id?: string } }
  const id = body.data?.id
  if (!id) throw new Error('no file id')
  return { id, url: await getDownloadUrl(id) }
}

async function sendAudioFile(filePath: string, duration: number) {
  messages.value.push({
    role: 'user',
    content: '[语音消息]',
    type: 'audio',
    audioUrl: filePath,
    duration,
    createTime: Date.now(),
  })
  scrollBottom()
  try {
    let url: string
    if (filePath.startsWith('blob:')) {
      url = (await uploadBlobAudio(filePath)).url
    } else {
      const file = await uploadFile(filePath)
      url = await getDownloadUrl(file.id)
    }
    input.value = `请转写并回答这段语音（已上传）：${url}`
    await handleSend()
  } catch {
    uni.showToast({ title: '语音上传失败，仅本地可播放', icon: 'none' })
  }
}

function removeImage() {
  selectedImage.value = ''
  uploadedUrl.value = ''
  uploadedFileId.value = ''
}
function previewImage(url: string) {
  uni.previewImage({ urls: [url], current: url })
}

function initRecorder() {
  try { recorderManager = uni.getRecorderManager() } catch { recorderManager = null }
  try { innerAudio = uni.createInnerAudioContext() } catch { innerAudio = null }
  if (recorderManager) {
    recorderManager.onStart(() => {
      isRecording.value = true
      showRecordModal.value = true
      recordDuration.value = 0
      recordTimer = setInterval(() => recordDuration.value++, 1000)
    })
    recorderManager.onStop((res: { tempFilePath: string; duration: number }) => {
      isRecording.value = false
      showRecordModal.value = false
      if (recordTimer) { clearInterval(recordTimer); recordTimer = null }
      if (recordDuration.value < 1) {
        uni.showToast({ title: '录音时间太短', icon: 'none' })
        return
      }
      sendAudioFile(res.tempFilePath, res.duration)
    })
    recorderManager.onError(() => {
      isRecording.value = false
      showRecordModal.value = false
      if (recordTimer) { clearInterval(recordTimer); recordTimer = null }
      uni.showToast({ title: '录音失败', icon: 'none' })
    })
  }
}

async function startRecord() {
  if (typeof navigator !== 'undefined' && navigator.mediaDevices?.getUserMedia && typeof MediaRecorder !== 'undefined') {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      h5Chunks = []
      h5RecordStart = Date.now()
      const mime = MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/mp4'
      h5MediaRecorder = new MediaRecorder(stream, { mimeType: mime })
      h5MediaRecorder.ondataavailable = (ev) => { if (ev.data.size) h5Chunks.push(ev.data) }
      h5MediaRecorder.onstop = () => {
        stream.getTracks().forEach((t) => t.stop())
        const blob = new Blob(h5Chunks, { type: mime })
        const dur = Date.now() - h5RecordStart
        if (dur < 1000) {
          uni.showToast({ title: '录音时间太短', icon: 'none' })
          return
        }
        const url = URL.createObjectURL(blob)
        sendAudioFile(url, dur)
      }
      h5MediaRecorder.start()
      isRecording.value = true
      showRecordModal.value = true
      recordDuration.value = 0
      recordTimer = setInterval(() => recordDuration.value++, 1000)
      return
    } catch {
      /* fall through to uni recorder */
    }
  }
  if (!recorderManager) {
    uni.showToast({ title: '当前环境不支持录音', icon: 'none' })
    return
  }
  try { recorderManager.start({ format: 'mp3' } as UniApp.RecorderManagerStartOptions) } catch { /* ignore */ }
}

function stopRecord() {
  if (recordTimer) { clearInterval(recordTimer); recordTimer = null }
  isRecording.value = false
  showRecordModal.value = false
  if (h5MediaRecorder && h5MediaRecorder.state !== 'inactive') {
    h5MediaRecorder.stop()
    h5MediaRecorder = null
    return
  }
  if (!recorderManager) return
  try { recorderManager.stop() } catch { /* ignore */ }
}

function toggleAudio(idx: number) {
  const m = messages.value[idx]
  if (!m.audioUrl || !innerAudio) return
  if (m.playing) { innerAudio.pause(); m.playing = false; return }
  messages.value.forEach((x, i2) => { if (i2 !== idx) x.playing = false })
  innerAudio.stop()
  m.playing = true
  innerAudio.src = m.audioUrl
  innerAudio.play()
  innerAudio.onEnded(() => { m.playing = false })
  innerAudio.onError(() => { m.playing = false; uni.showToast({ title: '播放失败', icon: 'none' }) })
}

async function submitFeedback(item: Msg, index: number, type: 'HELPFUL' | 'NOT_HELPFUL') {
  if (!item.id) {
    uni.showToast({ title: '该消息暂不支持反馈', icon: 'none' })
    return
  }
  const token = getToken() || ''
  try {
    await new Promise<void>((resolve, reject) => {
      uni.request({
        url: BASE_URL + '/ai-governance/feedbacks',
        method: 'POST',
        header: { 'Content-Type': 'application/json', Authorization: token ? `Bearer ${token}` : '' },
        data: {
          feedbackType: type,
          targetType: 'ANSWER',
          messageId: item.id,
          conversationId: item.conversationId || conversationId.value,
          scenario: 'PLATFORM_QA',
        },
        success: (res) => {
          if (res.statusCode >= 200 && res.statusCode < 300) resolve()
          else reject(new Error(String(res.statusCode)))
        },
        fail: reject,
      })
    })
    messages.value[index].liked = type === 'HELPFUL'
    messages.value[index].disliked = type === 'NOT_HELPFUL'
    uni.showToast({ title: type === 'HELPFUL' ? '点赞成功' : '已反馈', icon: 'success' })
  } catch {
    uni.showToast({ title: '反馈失败', icon: 'none' })
  }
}
function handleLike(item: Msg, index: number) { return submitFeedback(item, index, 'HELPFUL') }
function handleDislike(item: Msg, index: number) { return submitFeedback(item, index, 'NOT_HELPFUL') }

/** TTS 语音播报: 调用后端音频合成, 成功后播放 */
let ttsAudio: UniApp.InnerAudioContext | null = null
async function handleSpeak(item: Msg, index: number) {
  if (!item.content || item.content.trim().length === 0) {
    uni.showToast({ title: '内容为空', icon: 'none' })
    return
  }
  // 正在播放 → 停止
  if (item.speaking) {
    if (ttsAudio) { ttsAudio.stop(); ttsAudio.destroy(); ttsAudio = null }
    messages.value[index].speaking = false
    return
  }
  // 停止其他播放
  if (ttsAudio) { ttsAudio.stop(); ttsAudio.destroy(); ttsAudio = null }
  messages.value.forEach((m, i) => { if (m.speaking) messages.value[i].speaking = false })

  uni.showLoading({ title: '合成中...' })
  try {
    const { synthesizeSpeech } = await import('@/api/tts')
    const result = await synthesizeSpeech(item.content)
    uni.hideLoading()
    if (result.status !== 'SUCCESS' || !result.outputUrl) {
      uni.showToast({ title: '语音合成失败', icon: 'none' })
      return
    }
    ttsAudio = uni.createInnerAudioContext()
    ttsAudio.src = result.outputUrl
    messages.value[index].speaking = true
    ttsAudio.play()
    ttsAudio.onEnded(() => { messages.value[index].speaking = false; ttsAudio?.destroy(); ttsAudio = null })
    ttsAudio.onError(() => { messages.value[index].speaking = false; uni.showToast({ title: '播放失败', icon: 'none' }); ttsAudio?.destroy(); ttsAudio = null })
  } catch {
    uni.hideLoading()
    uni.showToast({ title: '语音合成失败', icon: 'none' })
  }
}

function genUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

async function handleSend() {
  const q = input.value.trim()
  const hasImg = !!selectedImage.value
  if (!q && !hasImg) {
    uni.showToast({ title: '请输入内容或选择图片', icon: 'none' })
    return
  }
  if (sending.value || wsLoading.value) return
  let content = q
  if (hasImg) {
    const url = uploadedUrl.value || selectedImage.value
    content = q ? `${q}\n\n![image](${url})` : `![image](${url})`
  }
  const chatId = genUUID()
  messages.value.push({
    role: 'user',
    content: q || (hasImg ? '[图片]' : ''),
    imageUrl: hasImg ? (uploadedUrl.value || selectedImage.value) : undefined,
    createTime: Date.now(),
    id: chatId,
  })
  input.value = ''
  const imgPayload = hasImg ? (uploadedUrl.value || selectedImage.value) : undefined
  // 多模态 attachments 数组（P1-7）：WS 走 attachments 透传到后端，构造 OpenAI 多模态 content
  const attachmentsPayload = hasImg && imgPayload
    ? [{ type: 'image', url: imgPayload, name: `image-${Date.now()}.jpg` }]
    : undefined
  selectedImage.value = ''
  uploadedUrl.value = ''
  uploadedFileId.value = ''
  scrollBottom()
  if (wsConnected) {
    wsLoading.value = true
    typingText.value = ''
    const ok = sendViaWs(content, chatId, imgPayload, attachmentsPayload)
    if (ok) return
    wsLoading.value = false
  }
  const ai: Msg = { role: 'assistant', content: '', streaming: true, createTime: Date.now(), id: 'ai-' + Date.now() }
  messages.value.push(ai)
  sending.value = true
  abort = new AbortController()
  scrollBottom()
  try {
    const base = (import.meta.env.VITE_API_BASE_URL as string) || BASE_URL || '/api/v1'
    const token = getToken() || (uni.getStorageSync('yutong_token') as string) || ''
    const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
    if (token) headers.Authorization = `Bearer ${token}`
    const body: Record<string, unknown> = {
      message: content,
      scenario: 'PLATFORM_QA',
      idempotencyKey: `uni-${Date.now()}`,
      conversationId: conversationId.value || undefined,
    }
    if (appId.value) body.appId = appId.value
    if (agentId.value) body.agentId = agentId.value
    // V052 P2-C 分支链: 挂到上条服务端消息 (done 事件回填 ai.id 后同步)
    if (lastServerMessageId.value) body.parentMessageId = lastServerMessageId.value
    if (systemPrompt.value) body.systemPrompt = systemPrompt.value
    if (kbId.value) body.kbId = kbId.value
    const res = await fetch(`${base}/ai/chat`, { method: 'POST', headers, body: JSON.stringify(body), signal: abort.signal })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    if (!res.body) throw new Error('No body')
    const reader = res.body.getReader()
    const dec = new TextDecoder()
    let buf = ''
    const dispatch = (raw: string) => {
      const lines = raw.split('\n')
      const dl = lines.filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trimStart()).join('\n')
      if (!dl) return
      try {
        const ev = JSON.parse(dl) as { eventType?: string; conversationId?: string; messageId?: string; data?: { text?: string; code?: string; messageId?: string } }
        if (ev.conversationId) conversationId.value = ev.conversationId
        // V052 P2-C: 信封 messageId 为单次流临时 id; done 数据内 messageId 才是落库 DB id (反馈/分支挂靠用)
        if (ev.eventType === 'done' && ev.data?.messageId) {
          ai.id = ev.data.messageId
          lastAssistantMessageId.value = ev.data.messageId
          lastServerMessageId.value = ev.data.messageId
        } else if (ev.messageId) {
          ai.id = ev.messageId
        }
        ai.conversationId = conversationId.value
        if (ev.eventType === 'delta' && ev.data?.text) { ai.content += ev.data.text; scrollBottom() }
        else if (ev.eventType === 'done') { ai.streaming = false }
        else if (ev.eventType === 'error') { ai.streaming = false; ai.isError = true; ai.content += `\n[错误:${ev.data?.code || ''}]` }
      } catch { /* ignore incomplete json */ }
    }
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value as Uint8Array, { stream: true })
      let idx: number
      while ((idx = buf.indexOf('\n\n')) !== -1) {
        const raw = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        dispatch(raw)
      }
    }
    if (buf.trim()) dispatch(buf)
  } catch (e: unknown) {
    const msg = (e as { message?: string })?.message || ''
    const name = (e as { name?: string })?.name
    if (name !== 'AbortError' && !msg.includes('abort')) ai.content += `\n[发送失败:${msg}]`
    ai.streaming = false
  } finally {
    sending.value = false
    abort = null
    scrollBottom()
  }
}

function stop() {
  abort?.abort()
  wsLoading.value = false
}

// V052 P2-C: 跳历史会话列表 (选会话后带 conversationId 返回本页拉历史)
function goHistory() {
  uni.navigateTo({ url: '/pages/ai/conversations' })
}
function clearAll() {
  messages.value = [{ role: 'assistant', content: '哈喽！我是 YuTong 智能助手', createTime: Date.now() }]
  typingText.value = ''
  conversationId.value = ''
  lastAssistantMessageId.value = ''
  lastServerMessageId.value = ''
}

// V052 P2-C: 历史记录 (打开已有会话时加载, 点赞态随 feedback 回填)
async function loadHistory() {
  const cid = conversationId.value
  if (!cid) return
  try {
    const list = await apiGet<unknown>(`/ai/conversations/${cid}/messages`)
    const arr = Array.isArray(list) ? list : []
    if (!arr.length) return
    const mapped: Msg[] = []
    for (const raw of arr as Record<string, unknown>[]) {
      const role = raw.role === 'user' ? 'user' : 'assistant'
      const content =
        (raw.contentSummary as string) || (raw.content as string) || (raw.contentEncrypted as string) || ''
      if (!content && role === 'user') continue
      const m: Msg = {
        id: raw.id as string | undefined,
        role: role as 'user' | 'assistant',
        content,
        createTime: raw.createdTime ? Date.parse(raw.createdTime as string) || Date.now() : Date.now(),
      }
      if (role === 'assistant') {
        if (raw.feedback === 'LIKE') m.liked = true
        if (raw.feedback === 'DISLIKE') m.disliked = true
      }
      mapped.push(m)
    }
    if (!mapped.length) return
    messages.value = mapped
    const lastAi = [...mapped].reverse().find((m) => m.role === 'assistant' && m.id)
    if (lastAi?.id) {
      lastAssistantMessageId.value = lastAi.id
      lastServerMessageId.value = lastAi.id
    }
    scrollBottom()
  } catch {
    // 历史加载失败不阻断, 保留欢迎语
  }
}

onLoad((options: Record<string, string>) => {
  if (options?.appId) appId.value = options.appId
  if (options?.appName) appName.value = decodeURIComponent(options.appName)
  if (options?.agentId) agentId.value = options.agentId
  if (options?.systemPrompt) systemPrompt.value = decodeURIComponent(options.systemPrompt)
  if (options?.kbId) kbId.value = options.kbId
  if (options?.inputText) input.value = decodeURIComponent(options.inputText)
  // V052 P2-C: 支持带 conversationId 打开已有会话 (加载历史)
  if (options?.conversationId) {
    conversationId.value = options.conversationId
    loadHistory()
  }
  const autoSend = options?.autoSend && input.value.trim()
  connectWs(!!autoSend)
  if (autoSend && !wsConnected) setTimeout(() => handleSend(), 400)
  initRecorder()
  if (options?.appId || options?.agentId) uni.setNavigationBarTitle({ title: appName.value || '智能助手' })
})
onUnload(() => {
  try { uni.closeSocket() } catch { /* ignore */ }
  try { if (recordTimer) clearInterval(recordTimer) } catch { /* ignore */ }
  try { abort?.abort() } catch { /* ignore */ }
})
onMounted(() => { if (!(appId.value || agentId.value)) initRecorder() })
onUnmounted(() => { try { uni.closeSocket() } catch { /* ignore */ }; if (recordTimer) clearInterval(recordTimer) })
</script>

<style scoped>
.ai-chat { display:flex; flex-direction:column; height:100vh; background:var(--yt-bg-page, #f6f8fb); }
.header { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; background:var(--yt-bg-card, #fff); border-bottom:1px solid var(--yt-border-default, #e5e7eb); }
.header-left{ display:flex; gap:8px; align-items:center; }
.header-right{ display:flex; gap:12px; align-items:center; }
.title { font-weight:600; font-size:16px; color:var(--yt-text-primary, #111827); }
.app-tag{ font-size:11px; color:var(--yt-color-success, #16a34a); background:var(--yt-color-primary-light-9, #e2ecfe); border:1px solid var(--yt-border-default, #e5e7eb); border-radius:999px; padding:2px 8px; }
.sub { color:var(--yt-color-primary, #2563eb); font-size:13px; }
.msg-list { flex:1; padding:12px; display:flex; flex-direction:column; gap:12px; overflow-y:auto; }
.bubble { display:flex; gap:8px; align-items:flex-start; }
.bubble.user { flex-direction:row-reverse; }
.bubble-avatar{ width:32px; height:32px; border-radius:50%; display:flex; align-items:center; justify-content:center; flex-shrink:0; font-size:12px; }
.bubble-avatar.assistant{ background:var(--yt-color-primary-light-9, #e2ecfe); color:var(--yt-color-primary, #2563eb); border:1px solid var(--yt-border-default, #e5e7eb); }
.bubble-avatar.user{ background:var(--yt-color-primary, #2563eb); color:#fff; }
.avatar-text{ font-weight:600; }
.bubble-box{ flex:1; min-width:0; max-width:78%; display:flex; flex-direction:column; gap:4px; }
.bubble.user .bubble-box{ align-items:flex-end; }
.msg-time{ font-size:11px; color:var(--yt-text-disabled, #9ca3af); }
.bubble-inner { padding:10px 14px; border-radius:12px; font-size:14px; line-height:20px; word-break:break-word; background:var(--yt-bg-card, #fff); border:1px solid var(--yt-border-default, #e5e7eb); color:var(--yt-text-primary, #111827); }
.bubble.user .bubble-inner { background:var(--yt-color-primary, #2563eb); color:#fff; border-color:var(--yt-color-primary-dark-2, #1d50bd); }
.bubble-inner.error{ border-color:#fca5a5; background:#fef2f2; color:#991b1b; }
.bubble-inner.typing{ background:var(--yt-bg-card, #fff); }
.msg-image{ width:180px; border-radius:8px; margin-bottom:6px; }
.msg-text{ white-space:pre-wrap; }
.cursor { animation: blink 1s step-end infinite; margin-left:2px; }
@keyframes blink{50%{opacity:0}}
.feedback-row{ display:flex; gap:8px; align-items:center; margin-top:2px; }
.fb-btn{ font-size:13px; padding:2px 6px; border-radius:6px; border:1px solid var(--yt-border-default, #e5e7eb); background:var(--yt-bg-card, #fff); }
.fb-btn.active{ background:var(--yt-color-primary-light-9, #e2ecfe); border-color:var(--yt-color-primary, #2563eb); }
.fb-hint{ font-size:11px; color:var(--yt-text-disabled, #9ca3af); }
.audio-row{ display:flex; gap:8px; align-items:center; margin-bottom:6px; }
.audio-icon{ width:28px; height:28px; border-radius:50%; background:var(--yt-color-primary, #2563eb); color:#fff; display:flex; align-items:center; justify-content:center; font-size:12px; }
.audio-duration{ font-size:12px; color:var(--yt-text-secondary, #4b5563); }
.audio-playing{ font-size:11px; color:var(--yt-color-primary, #2563eb); }
.empty-state{ text-align:center; padding:40px 0; color:var(--yt-text-disabled, #9ca3af); }
.empty-title{ font-weight:600; color:var(--yt-text-secondary, #4b5563); }
.empty-desc{ font-size:12px; margin-top:4px; }
.preview-bar{ display:flex; gap:8px; align-items:center; padding:8px 12px; background:var(--yt-bg-card, #fff); border-top:1px solid var(--yt-border-default, #e5e7eb); }
.preview-thumb{ width:48px; height:48px; border-radius:8px; }
.preview-remove{ font-size:12px; color:var(--yt-color-danger, #dc2626); }
.preview-url{ font-size:11px; color:var(--yt-text-secondary, #4b5563); }
.record-modal{ display:flex; gap:8px; align-items:center; justify-content:center; padding:10px; background:var(--yt-text-primary, #111827); color:#fff; }
.record-dot{ width:10px; height:10px; border-radius:50%; background:var(--yt-color-danger, #dc2626); animation: blink 1s infinite; }
.record-text{ font-size:13px; font-weight:600; }
.record-hint{ font-size:11px; color:var(--yt-text-disabled, #9ca3af); }
.input-bar { display:flex; gap:8px; padding:10px 12px; background:var(--yt-bg-card, #fff); border-top:1px solid var(--yt-border-default, #e5e7eb); align-items:center; }
.tool-btn{ width:32px; height:32px; border-radius:50%; background:var(--yt-bg-page, #f6f8fb); display:flex; align-items:center; justify-content:center; border:1px solid var(--yt-border-default, #e5e7eb); }
.tool-btn.recording{ background:#fef2f2; border-color:#fecaca; }
.tool-icon{ font-size:14px; }
.input { flex:1; background:var(--yt-bg-page, #f6f8fb); border-radius:8px; padding:8px 12px; font-size:14px; }
.btn { border-radius:8px; }
.btn.primary { background:var(--yt-color-primary, #2563eb); color:#fff; }
.btn.danger { background:var(--yt-color-danger, #dc2626); color:#fff; }
.foot-hint{ font-size:11px; color:var(--yt-text-disabled, #9ca3af); text-align:center; padding:4px; background:var(--yt-bg-card, #fff); border-top:1px solid var(--yt-border-light, #f1f5f9); }
</style>
