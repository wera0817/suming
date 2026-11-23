<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Settings, BookOpen, ScrollText, LogOut, Save, Plug, Eye, EyeOff, Plus, Upload, Search, Pencil, X, LoaderCircle, ShieldCheck, ArrowRight } from '@lucide/vue'
import { api, date, type Doc } from './api'
const emit = defineEmits<{ configured: [] }>()
const authenticated = ref(false), checking = ref(true), tab = ref('settings'), busy = ref(false), error = ref(''), success = ref('')
const username = ref('admin'), password = ref(''), showPassword = ref(false)
const connection = () => ({ name: '', baseUrl: '', apiKey: '', model: '', hasKey: false })
const config = reactive({ text: { ...connection(), name: '文本服务' }, vision: { ...connection(), name: '视觉服务' }, separateVision: false, maxTokens: 3072, timeoutSeconds: 90, tokenParameter: 'max_tokens' })
const docs = ref<Doc[]>([]), logs = ref<any[]>([]), query = ref(''), category = ref('all'), editor = ref<Doc | null>(null), previewImport = ref(false)
const filtered = computed(() => docs.value.filter(d => (category.value === 'all' || d.category === category.value) && `${d.title} ${d.body} ${d.source}`.includes(query.value)))
const docCategories: Record<string, string> = { meihua: '梅花问卦', palm: '手相图解', face: '面相文化' }
const defaults = (): Doc => ({ id: '', title: '', body: '', category: 'meihua', school: '', source: '', edition: '', rights: '', verified: false, enabled: false })
function fail(e: unknown) { error.value = e instanceof Error ? e.message : '操作未完成'; if ((e as any)?.status === 401) authenticated.value = false }
function clear() { error.value = ''; success.value = '' }
async function load() {
  const data = await api('/admin/settings'); if (data.configured) { for (const key of ['text', 'vision'] as const) Object.assign(config[key], data[key]); Object.assign(config, { separateVision: data.separateVision, maxTokens: data.maxTokens, timeoutSeconds: data.timeoutSeconds, tokenParameter: data.tokenParameter }) }
  docs.value = await api('/admin/knowledge'); logs.value = await api('/admin/logs')
}
async function login() { busy.value = true; clear(); try { await api('/admin/login', { method: 'POST', body: JSON.stringify({ username: username.value, password: password.value }) }); password.value = ''; authenticated.value = true; await load() } catch (e) { fail(e) } finally { busy.value = false } }
async function logout() { clear(); try { await api('/admin/logout', { method: 'POST' }); authenticated.value = false; config.text.apiKey = ''; config.vision.apiKey = ''; docs.value = []; logs.value = [] } catch (e) { fail(e) } }
async function saveSettings() {
  busy.value = true; clear()
  try {
    const cleanConnection = (value: typeof config.text) => ({ name: value.name, baseUrl: value.baseUrl, apiKey: value.apiKey, model: value.model })
    const data = await api('/admin/settings', { method: 'PUT', body: JSON.stringify({ text: cleanConnection(config.text), vision: config.separateVision ? cleanConnection(config.vision) : { ...cleanConnection(config.text), model: config.vision.model }, separateVision: config.separateVision, maxTokens: config.maxTokens, timeoutSeconds: config.timeoutSeconds, tokenParameter: config.tokenParameter }) })
    Object.assign(config.text, data.text); Object.assign(config.vision, data.vision); success.value = '配置已加密保存，请分别测试文本和视觉连接'; emit('configured')
  } catch (e) { fail(e) } finally { busy.value = false }
}
async function test(kind: string) {
  busy.value = true; clear()
  try { const result = await api('/admin/test', { method: 'POST', body: JSON.stringify({ kind }) }); success.value = `${result.message} · ${result.durationMs} ms`; logs.value = await api('/admin/logs') } catch (e) { fail(e) } finally { busy.value = false }
}
function edit(doc?: Doc) { clear(); previewImport.value = false; editor.value = doc ? { ...doc } : defaults() }
async function importText(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; input.value = ''; if (!file) return; clear()
  try {
    if (!/\.(md|txt)$/i.test(file.name) || file.size > 100000) throw new Error('仅支持 100 KB 以内的 UTF-8 Markdown/TXT 文件')
    const body = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
    if (!body.trim() || body.length > 20000) throw new Error('正文应为 1 至 20000 字符')
    editor.value = { ...defaults(), title: (body.match(/^#\s+(.+)$/m)?.[1] || file.name.replace(/\.[^.]+$/, '')).slice(0, 200), body }; previewImport.value = true
  } catch (e) { fail(e) }
}
async function saveDoc() {
  if (!editor.value) return; busy.value = true; clear()
  try { await api('/admin/knowledge', { method: 'PUT', body: JSON.stringify(editor.value) }); docs.value = await api('/admin/knowledge'); editor.value = null; success.value = '资料已保存' } catch (e) { fail(e) } finally { busy.value = false }
}
async function toggle(doc: Doc) {
  busy.value = true; clear()
  try { await api('/admin/knowledge', { method: 'PUT', body: JSON.stringify({ ...doc, enabled: !doc.enabled }) }); docs.value = await api('/admin/knowledge'); success.value = doc.enabled ? '条目已停用，新咨询不再检索该资料' : '条目已启用' } catch (e) { fail(e) } finally { busy.value = false }
}
async function switchTab(value: string) { tab.value = value; clear(); if (value === 'logs') try { logs.value = await api('/admin/logs') } catch (e) { fail(e) } }
onMounted(async () => { try { await api('/admin/session'); authenticated.value = true; await load() } catch (e) { if ((e as any)?.status !== 401) fail(e) } finally { checking.value = false } })
</script>

<template>
  <div v-if="checking" class="empty-state"><LoaderCircle class="spin"/><p>正在验证会话</p></div>
  <section v-else-if="!authenticated" class="login-section">
    <img src="/seal.svg" width="44" height="44" alt="观象印记"><p class="eyebrow">观象 · 书斋管理</p><h1>入斋</h1><p class="muted">仅限设备管理员</p>
    <form @submit.prevent="login"><label for="username">管理员账号</label><input id="username" v-model="username" autocomplete="username" required maxlength="80"><label for="password">管理员密码</label><div class="password-field"><input id="password" v-model="password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" required maxlength="200"><button type="button" class="icon-button" :title="showPassword ? '隐藏密码' : '显示密码'" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17"/><Eye v-else :size="17"/></button></div><div v-if="error" class="alert error" role="alert">{{ error }}</div><button class="primary full" :disabled="busy"><LoaderCircle v-if="busy" class="spin" :size="17"/><ArrowRight v-else :size="17"/>登录</button></form>
  </section>
  <section v-else class="admin-page">
    <div class="page-heading"><div><p class="eyebrow">观象 · 管理</p><h1>书斋案牍</h1></div><button class="secondary" :disabled="busy" @click="logout"><LogOut :size="16"/>退出</button></div>
    <div class="mode-tabs admin-tabs" role="tablist"><button v-for="item in [{ id: 'settings', name: '模型连接', icon: Settings }, { id: 'knowledge', name: '典籍资料', icon: BookOpen }, { id: 'logs', name: '运行记录', icon: ScrollText }]" :key="item.id" role="tab" :aria-selected="tab === item.id" :class="{ selected: tab === item.id }" :disabled="busy" @click="switchTab(item.id)"><component :is="item.icon" :size="18"/>{{ item.name }}</button></div>
    <div v-if="error" class="alert error" role="alert">{{ error }}</div><div v-if="success" class="alert success" role="status">{{ success }}</div>

    <form v-if="tab === 'settings'" class="settings-form" @submit.prevent="saveSettings">
      <div class="section-heading"><h2>文本服务</h2><span>Chat Completions</span></div>
      <div class="settings-grid"><label>服务名称<input v-model="config.text.name" required maxlength="80" placeholder="例如：自选 API 服务"></label><label>文本模型<input v-model="config.text.model" required maxlength="150" placeholder="服务商提供的模型 ID"></label><label class="span-two">Base URL<input v-model="config.text.baseUrl" type="url" required maxlength="500" placeholder="https://服务商地址/v1"></label><label class="span-two">API Key <small>{{ config.text.hasKey ? '已保存，留空则保留原密钥' : '仅在服务端加密保存' }}</small><input v-model="config.text.apiKey" type="password" :required="!config.text.hasKey" maxlength="2000" autocomplete="new-password" :placeholder="config.text.hasKey ? '•••••••• 已保存' : '输入 API Key'"></label></div>
      <div class="section-heading"><h2>视觉服务</h2><label class="checkbox-line"><input v-model="config.separateVision" type="checkbox">使用独立连接</label></div>
      <div class="settings-grid"><label>视觉模型<input v-model="config.vision.model" required maxlength="150" placeholder="支持图片输入的模型 ID"></label><label v-if="config.separateVision">服务名称<input v-model="config.vision.name" required maxlength="80"></label><template v-if="config.separateVision"><label class="span-two">Base URL<input v-model="config.vision.baseUrl" type="url" required maxlength="500" placeholder="https://服务商地址/v1"></label><label class="span-two">API Key <small>{{ config.vision.hasKey ? '留空保留原密钥' : '独立视觉服务密钥' }}</small><input v-model="config.vision.apiKey" type="password" :required="!config.vision.hasKey" maxlength="2000" autocomplete="new-password"></label></template><p v-else class="field-note">沿用文本服务的地址与密钥。</p></div>
      <div class="section-heading"><h2>请求参数</h2><span>同时最多 3 个 API 请求</span></div>
      <div class="settings-grid"><label>输出 Token 上限<input v-model.number="config.maxTokens" type="number" min="256" max="8192" required></label><label>超时 / 秒<input v-model.number="config.timeoutSeconds" type="number" min="10" max="120" required></label><label>Token 参数名<select v-model="config.tokenParameter"><option value="max_tokens">max_tokens</option><option value="max_completion_tokens">max_completion_tokens</option></select></label></div>
      <div class="settings-actions"><button class="primary" :disabled="busy"><Save :size="16"/>保存配置</button><button type="button" class="secondary" :disabled="busy" @click="test('text')"><Plug :size="16"/>测试已保存的文本连接</button><button type="button" class="secondary" :disabled="busy" @click="test('vision')"><Eye :size="16"/>测试已保存的视觉连接</button><LoaderCircle v-if="busy" class="spin" :size="19"/></div>
      <p class="footnote"><ShieldCheck :size="14"/>不在浏览器调用模型。非本机 API 地址需 HTTPS；测试使用已保存的配置，并会产生少量 API 用量。</p>
    </form>

    <section v-else-if="tab === 'knowledge'" class="knowledge-admin">
      <div class="library-toolbar"><div class="search-input"><Search :size="17"/><input v-model="query" aria-label="搜索资料" placeholder="检索标题、正文或出处"></div><select v-model="category" aria-label="资料分类"><option value="all">全部术式</option><option v-for="(name, key) in docCategories" :key="key" :value="key">{{ name }}</option></select><label class="secondary import-button"><Upload :size="16"/>导入文本<input class="visually-hidden" type="file" accept=".md,.txt" :disabled="busy" @change="importText"></label><button class="primary" :disabled="busy" @click="edit()"><Plus :size="16"/>新增资料</button></div>
      <p class="footnote">{{ filtered.length }} 条资料 · 未核实来源的资料不可启用 · 历史报告保留当时的资料快照</p>
      <div class="knowledge-table-wrap"><table class="data-table"><thead><tr><th>条目</th><th>术式</th><th>来源状态</th><th>检索状态</th><th>编辑</th></tr></thead><tbody><tr v-for="doc in filtered" :key="doc.id"><td><strong>{{ doc.title }}</strong><small class="source-cell">{{ doc.source }}</small></td><td>{{ docCategories[doc.category] }}</td><td><span :class="doc.verified ? 'verified' : 'muted'">{{ doc.verified ? '已核对' : '待核对' }}</span></td><td><button class="toggle" role="switch" :aria-checked="doc.enabled" :aria-label="`${doc.title}的检索状态`" :class="{ on: doc.enabled }" :disabled="busy || (!doc.enabled && !doc.verified)" @click="toggle(doc)"><span></span></button></td><td><button class="icon-button" :aria-label="`编辑${doc.title}`" title="编辑资料" :disabled="busy" @click="edit(doc)"><Pencil :size="16"/></button></td></tr><tr v-if="!filtered.length"><td colspan="5" class="table-empty">没有匹配的资料</td></tr></tbody></table></div>
    </section>
    <section v-else><p class="footnote">最近 100 次请求。仅记录编号、状态、耗时及服务商返回的用量，不记录问题、照片和密钥。</p><div class="knowledge-table-wrap"><table class="data-table"><thead><tr><th>时间</th><th>操作 / 请求编号</th><th>状态</th><th>耗时</th><th>Tokens</th></tr></thead><tbody><tr v-for="log in logs" :key="log.id"><td>{{ date(log.created_at) }}</td><td>{{ log.operation }}<small class="source-cell mono">{{ log.id }}</small></td><td>{{ log.status }}</td><td>{{ log.duration_ms }} ms</td><td>{{ log.total_tokens ?? '—' }}</td></tr><tr v-if="!logs.length"><td colspan="5" class="table-empty">尚无调用记录</td></tr></tbody></table></div></section>

    <div v-if="editor" class="modal-backdrop" @click.self="!busy && (editor = null)"><section class="modal editor-modal" role="dialog" aria-modal="true" aria-labelledby="editor-title"><button class="icon-button modal-close" aria-label="关闭编辑" title="关闭编辑" :disabled="busy" @click="editor = null"><X :size="20"/></button><h2 id="editor-title">{{ previewImport ? '导入预览' : editor.id ? '修订资料' : '新增资料' }}</h2><form @submit.prevent="saveDoc"><label>标题<input v-model="editor.title" required maxlength="200"></label><div class="settings-grid"><label>术式<select v-model="editor.category"><option v-for="(name, key) in docCategories" :key="key" :value="key">{{ name }}</option></select></label><label>流派 / 类型<input v-model="editor.school" required maxlength="100"></label></div><label>正文<textarea v-model="editor.body" rows="9" required maxlength="20000"></textarea></label><label>出处<input v-model="editor.source" required maxlength="500" placeholder="书名、卷次及可核对来源；原创整理请如实注明"></label><label>版本<input v-model="editor.edition" required maxlength="200"></label><label>使用权限说明<input v-model="editor.rights" required maxlength="500"></label><div class="editor-checks"><label class="checkbox-line"><input v-model="editor.verified" type="checkbox" @change="!editor.verified && (editor.enabled = false)">已核对来源、版本和使用权限</label><label class="checkbox-line"><input v-model="editor.enabled" type="checkbox" :disabled="!editor.verified">启用检索</label></div><div v-if="error" class="alert error" role="alert">{{ error }}</div><button class="primary full" :disabled="busy"><Save :size="16"/>保存资料</button></form></section></div>
  </section>
</template>
