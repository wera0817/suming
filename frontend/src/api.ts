import MarkdownIt from 'markdown-it'

export class ApiError extends Error {
  constructor(public code: string, message: string, public status: number) { super(message) }
}
export async function api<T = any>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(`/api${path}`, { ...options, headers, credentials: 'same-origin' })
  const value = await response.json().catch(() => ({}))
  if (!response.ok) throw new ApiError(value.code || 'NETWORK_ERROR', value.message || '请求未完成，请检查连接', response.status)
  return value as T
}
export async function streamReport(id: string, message: string, delta: (text: string) => void) {
  const response = await fetch(`/api/reports/${id}/generate`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message }), credentials: 'same-origin' })
  if (!response.ok) { const error = await response.json().catch(() => ({})); throw new ApiError(error.code, error.message || '解读请求未完成', response.status) }
  if (!response.body) throw new Error('浏览器不支持流式响应')
  const reader = response.body.getReader(), decoder = new TextDecoder()
  let buffer = '', done = false
  try {
    while (true) {
      const part = await reader.read()
      buffer += decoder.decode(part.value, { stream: !part.done }).replace(/\r\n/g, '\n')
      let end: number
      while ((end = buffer.indexOf('\n\n')) >= 0) {
        const event = buffer.slice(0, end); buffer = buffer.slice(end + 2)
        const type = event.split('\n').find(l => l.startsWith('event:'))?.slice(6).trim()
        const data = event.split('\n').filter(l => l.startsWith('data:')).map(l => l.slice(5)).join('\n')
        if (!data) continue
        const value = JSON.parse(data)
        if (type === 'delta') delta(value.text)
        if (type === 'error') throw new ApiError(value.code, value.message, 502)
        if (type === 'done') done = true
      }
      if (part.done) break
    }
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
  if (!done) throw new Error('连接已中断，报告未完成。请手动重试。')
}
const md = new MarkdownIt({ html: false, linkify: false, breaks: true })
md.disable(['image', 'link'])
export const markdown = (text: string) => md.render(text)
export const labels: Record<string, string> = { meihua: '梅花问卦', palm: '手相图解', face: '面相文化', draft: '待解读', running: '解读中', completed: '已完成', failed: '未完成', interrupted: '已中断' }
export const date = (value: string) => new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
export type Hex = { number: number; name: string; lines: number[]; upper: { name: string; nature: string }; lower: { name: string; nature: string } }
export type Chart = { version: string; numbers: number[]; movingLine: number; original: Hex; mutual: Hex; changed: Hex; body: { name: string; element: string }; use: { name: string; element: string } }
export type Doc = { id: string; title: string; body: string; category: string; school: string; source: string; edition: string; rights: string; verified: boolean; enabled: boolean }
export type Report = { id: string; kind: string; question: string; background: string; chart: Chart | null; observation: string | null; sources: Doc[]; status: string; error_code: string | null; created_at: string; messages: { role: string; content: string }[] }
