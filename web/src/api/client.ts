import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('yutong_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  const tenantId = localStorage.getItem('yutong_tenant_id')
  if (tenantId) config.headers['X-Tenant-Id'] = tenantId
  return config
})

client.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data && typeof data === 'object' && 'code' in data) {
      const code = String((data as { code: unknown }).code)
      if (code !== '0' && code !== '200') return Promise.reject(new Error((data as { message?: string }).message || 'Error'))
      return (data as { data: unknown }).data ?? data
    }
    return data
  },
  (err) => Promise.reject(err),
)

export default client

/** SSE helper: POST /ai/chat text/event-stream -> callbacks */
export interface StreamCallbacks {
  onMeta?: (d: Record<string, unknown>, e: Record<string, unknown>) => void
  onDelta?: (d: { text: string }, e: Record<string, unknown>) => void
  onCitation?: (d: Record<string, unknown>, e: Record<string, unknown>) => void
  onTool?: (d: Record<string, unknown>, e: Record<string, unknown>) => void
  onDone?: (d: Record<string, unknown>, e: Record<string, unknown>) => void
  onError?: (d: Record<string, unknown>, e: Record<string, unknown>) => void
}

export async function streamChat(
  body: Record<string, unknown>,
  cbs: StreamCallbacks,
  signal?: AbortSignal,
): Promise<void> {
  const base = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const url = `${base}/ai/chat`
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  const token = localStorage.getItem('yutong_token')
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body), signal })
  if (!res.ok) {
    let msg = `HTTP ${res.status}`
    try {
      const j = await res.json()
      msg = (j as { message?: string }).message || msg
    } catch {}
    throw new Error(msg)
  }
  if (!res.body) throw new Error('No stream body')
  const reader = res.body.getReader()
  const dec = new TextDecoder('utf-8')
  let buf = ''
  const dispatch = (raw: string) => {
    const lines = raw.split('\n')
    const dataLines: string[] = []
    for (const l of lines) if (l.startsWith('data:')) dataLines.push(l.slice(5).trimStart())
    if (!dataLines.length) return
    let ev: { eventType?: string; data?: unknown } & Record<string, unknown>
    try { ev = JSON.parse(dataLines.join('\n')) } catch { return }
    const type = (ev.eventType as string) || ''
    if (type === 'meta') cbs.onMeta?.(ev.data as Record<string, unknown>, ev)
    else if (type === 'delta') cbs.onDelta?.(ev.data as { text: string }, ev)
    else if (type === 'citation') cbs.onCitation?.(ev.data as Record<string, unknown>, ev)
    else if (type === 'tool') cbs.onTool?.(ev.data as Record<string, unknown>, ev)
    else if (type === 'done') cbs.onDone?.(ev.data as Record<string, unknown>, ev)
    else if (type === 'error') cbs.onError?.(ev.data as Record<string, unknown>, ev)
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      let idx: number
      while ((idx = buf.indexOf('\n\n')) !== -1) {
        const raw = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        dispatch(raw)
      }
    }
    if (buf.trim()) dispatch(buf)
  } finally { reader.releaseLock() }
}
