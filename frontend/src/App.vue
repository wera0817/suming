<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { BookOpen, Compass, Hand, ScanFace, History, Settings, ArrowUpRight, ArrowRight, Shuffle, X, Trash2, RefreshCw, Upload, ChevronDown, Send, LoaderCircle, Plus, ShieldCheck } from '@lucide/vue'
import Admin from './Admin.vue'
import Hexagram from './Hexagram.vue'
import { api, streamReport, markdown, labels, date, type Chart, type Report } from './api'

const page = ref(location.hash === '#/admin' ? 'admin' : location.hash === '#/history' ? 'history' : 'consult')
const hashChange = () => { page.value = location.hash === '#/admin' ? 'admin' : location.hash === '#/history' ? 'history' : 'consult'; if (page.value === 'history') loadHistory() }
const mode = ref('meihua'), question = ref(''), background = ref(''), numbers = ref([17, 26, 9])
const session = ref({ configured: false, visionConfigured: false }), busy = ref(false), error = ref(''), errorCode = ref(''), notice = ref(false)
const report = ref<Report | null>(null), preview = ref<Chart | null>(null), partial = ref(''), followup = ref(''), records = ref<Report[]>([]), historyBusy = ref(false), deleting = ref('')
const photo = ref<File | null>(null), photoUrl = ref(''), consent = ref(false), accepted = ref(sessionStorage.getItem('gx-notice') === 'yes')
const chart = computed(() => report.value?.chart || preview.value)
const canGenerate = computed(() => report.value && !report.value.messages.some(m => m.role === 'assistant') && report.value.status !== 'running')
const progress = ref('')
function fail(e: unknown) { error.value = e instanceof Error ? e.message : '请求未完成，请稍后重试'; errorCode.value = (e as any)?.code || '' }
async function refreshSession() { try { session.value = await api('/session') } catch (e) { fail(e) } }
function accept() { accepted.value = true; sessionStorage.setItem('gx-notice', 'yes'); notice.value = false }
function switchMode(next: string) { if (busy.value) return; mode.value = next; report.value = null; preview.value = null; partial.value = ''; error.value = ''; clearPhoto() }
function randomize() { const values = new Uint32Array(3); crypto.getRandomValues(values); numbers.value = Array.from(values, n => n % 999 + 1) }
function clearPhoto() { if (photoUrl.value) URL.revokeObjectURL(photoUrl.value); photoUrl.value = ''; photo.value = null; consent.value = false }
function selectPhoto(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; input.value = ''; if (!file) return
  error.value = ''
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { error.value = '请选择 JPEG、PNG 或 WebP 图片'; return }
  if (file.size > 10 * 1024 * 1024) { error.value = '图片不能超过 10 MB'; return }
  clearPhoto(); photo.value = file; photoUrl.value = URL.createObjectURL(file)
}
async function onlyChart() {
  error.value = ''; busy.value = true; progress.value = '正在起卦'
  try { preview.value = await api('/chart', { method: 'POST', body: JSON.stringify({ numbers: numbers.value }) }); report.value = null; partial.value = '' } catch (e) { fail(e) } finally { busy.value = false }
}
async function generate(message = '') {
  if (!report.value) return
  busy.value = true; error.value = ''; errorCode.value = ''; partial.value = ''; progress.value = message ? '正在推敲追问' : '正在查阅与解读'
  const id = report.value.id
  try { await streamReport(id, message, text => { partial.value += text }); followup.value = '' }
  catch (e) { fail(e) }
  finally {
    try { report.value = await api(`/reports/${id}`) } catch (e) { fail(e) }
    busy.value = false; progress.value = ''; partial.value = ''
  }
}
async function submit() {
  if (!accepted.value) { notice.value = true; return }
  if (!question.value.trim()) { error.value = '请先写下所问之事'; return }
  if (mode.value !== 'meihua' && (!photo.value || !consent.value)) { error.value = '请选择照片并确认外部服务发送授权'; return }
  busy.value = true; error.value = ''; errorCode.value = ''; partial.value = ''; preview.value = null; report.value = null
  progress.value = mode.value === 'meihua' ? '正在起卦' : '正在检查照片'
  try {
    if (mode.value === 'meihua') report.value = await api('/reports', { method: 'POST', body: JSON.stringify({ question: question.value, background: background.value, chart: { numbers: numbers.value } }) })
    else {
      const form = new FormData(); form.append('kind', mode.value); form.append('question', question.value); form.append('background', background.value); form.append('consent', 'true'); form.append('image', photo.value!)
      report.value = await api('/reports/photo', { method: 'POST', body: form }); clearPhoto()
    }
    await generate()
  } catch (e) { fail(e) } finally { busy.value = false; progress.value = '' }
}
async function loadHistory() { historyBusy.value = true; error.value = ''; try { records.value = await api('/reports') } catch (e) { fail(e) } finally { historyBusy.value = false } }
async function openReport(id: string) { if (busy.value) return; try { report.value = await api(`/reports/${id}`); mode.value = report.value!.kind; preview.value = null; partial.value = ''; error.value = ''; location.hash = '#/'; page.value = 'consult' } catch (e) { fail(e) } }
async function remove(id: string) {
  if (!confirm('删除这份报告及全部追问？此操作不可撤销。')) return
  deleting.value = id
  try { await api(`/reports/${id}`, { method: 'DELETE' }); if (report.value?.id === id) report.value = null; await loadHistory() } catch (e) { fail(e) } finally { deleting.value = '' }
}
function newConsult() { if (busy.value) return; report.value = null; preview.value = null; question.value = ''; background.value = ''; followup.value = ''; partial.value = ''; error.value = ''; clearPhoto(); location.hash = '#/' }
onMounted(async () => { addEventListener('hashchange', hashChange); await refreshSession(); if (page.value === 'history') await loadHistory() })
onUnmounted(() => { removeEventListener('hashchange', hashChange); clearPhoto() })
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <a class="brand" href="#/" aria-label="观象首页"><img src="/seal.svg" alt="观象印记" width="38" height="38"><span>观象<small>数字术数书斋</small></span></a>
      <nav aria-label="主导航"><a href="#/" :class="{ active: page === 'consult' }"><Compass :size="17"/>问事</a><a href="#/history" :class="{ active: page === 'history' }"><History :size="17"/>往录</a></nav>
      <div class="header-actions"><button class="icon-button" title="顾问与隐私说明" aria-label="顾问与隐私说明" @click="notice = true"><BookOpen :size="19"/></button><a class="icon-button" href="#/admin" title="管理后台" aria-label="管理后台"><Settings :size="19"/></a></div>
    </header>

    <main v-if="page === 'admin'" class="main-wrap"><Admin @configured="refreshSession"/></main>
    <main v-else-if="page === 'history'" class="main-wrap history-page">
      <div class="page-heading"><div><p class="eyebrow">观象 · 往录</p><h1>问事有迹</h1></div><button class="primary" @click="newConsult"><Plus :size="17"/>新建咨询</button></div>
      <div v-if="error" class="alert error" role="alert">{{ error }}</div>
      <div v-if="historyBusy" class="empty-state compact"><LoaderCircle class="spin"/><p>正在取阅</p></div>
      <div v-else-if="!records.length" class="empty-state"><History :size="36"/><h2>尚无往录</h2><button class="text-button" @click="newConsult">起一问 <ArrowRight :size="16"/></button></div>
      <div v-else class="history-list"><article v-for="item in records" :key="item.id" class="history-row"><button class="history-open" @click="openReport(item.id)"><span class="history-kind">{{ labels[item.kind] }}</span><strong>{{ item.question }}</strong><span class="history-meta">{{ date(item.created_at) }} · {{ labels[item.status] }}</span><ArrowUpRight :size="18"/></button><button class="icon-button danger" :disabled="deleting === item.id" title="删除报告" aria-label="删除报告" @click="remove(item.id)"><Trash2 :size="17"/></button></article></div>
      <p class="footnote">往录不公开，仅凭当前浏览器凭证访问。共用浏览器也会共用往录；清除凭证后无法恢复访问。</p>
    </main>

    <main v-else class="main-wrap">
      <div class="page-heading"><div><p class="eyebrow">观象 · 问事</p><h1>一事一问，观象知止</h1></div><span class="chapter-mark" aria-hidden="true">观<br>象<br>明<br>意</span></div>
      <div class="mode-tabs" role="tablist" aria-label="咨询类型"><button v-for="tab in [{ id: 'meihua', name: '梅花问卦', icon: Compass }, { id: 'palm', name: '手相图解', icon: Hand }, { id: 'face', name: '面相文化', icon: ScanFace }]" :key="tab.id" role="tab" :aria-selected="mode === tab.id" :class="{ selected: mode === tab.id }" :disabled="busy" @click="switchMode(tab.id)"><component :is="tab.icon" :size="19"/>{{ tab.name }}</button></div>
      <div class="consult-layout">
        <section class="question-section" aria-labelledby="question-title">
          <div class="section-heading"><h2 id="question-title">{{ mode === 'meihua' ? '所问之事' : mode === 'palm' ? '掌中纹理' : '面部图解' }}</h2><span>壹 · 问</span></div>
          <form @submit.prevent="submit">
            <label for="question">问题 <span class="required">*</span></label><textarea id="question" v-model="question" rows="3" maxlength="2000" required :disabled="busy" :placeholder="mode === 'meihua' ? '此刻，有什么想问的事？' : '你想了解哪些传统术语或可见部位？'"></textarea>
            <label for="background">事情背景 <small>选填</small></label><textarea id="background" v-model="background" rows="3" maxlength="4000" :disabled="busy" placeholder="相关经过、当前处境……"></textarea>
            <template v-if="mode === 'meihua'">
              <div class="field-heading"><label>起卦三数</label><button type="button" class="icon-button" title="随机生成三数" aria-label="随机生成三数" :disabled="busy" @click="randomize"><Shuffle :size="17"/></button></div>
              <div class="number-grid"><label v-for="(name, i) in ['上卦数', '下卦数', '动爻数']" :key="name"><span>{{ name }}</span><input v-model.number="numbers[i]" :aria-label="name" :style="{ fontSize: String(numbers[i]).length > 6 ? '12px' : String(numbers[i]).length > 4 ? '15px' : undefined }" type="number" min="1" max="999999999" step="1" required :disabled="busy"></label></div>
              <details class="rules"><summary>本次起卦规则 <ChevronDown :size="14"/></summary><p>第一、二数除八取上、下卦，余零记八；第三数除六取动爻，余零记六。爻位自下而上。动卦为用、静卦为体。</p><small>规则版本：meihua-three-numbers-v1</small></details>
            </template>
            <template v-else>
              <label>咨询照片 <span class="required">*</span></label>
              <div v-if="photoUrl" class="photo-preview"><img :src="photoUrl" alt="本次待分析照片"><button type="button" class="icon-button" title="移除照片" aria-label="移除照片" :disabled="busy" @click="clearPhoto"><X :size="17"/></button></div>
              <label v-else class="upload-zone" for="photo"><Upload :size="27"/><strong>{{ mode === 'palm' ? '选择手掌照片' : '选择面部照片' }}</strong><span>JPEG / PNG / WebP · 最大 10 MB</span></label><input id="photo" class="visually-hidden" type="file" accept="image/jpeg,image/png,image/webp" :disabled="busy" @change="selectPhoto">
              <label class="checkbox-line"><input v-model="consent" type="checkbox" :disabled="busy"><span>同意将此照片发送给后台配置的外部模型服务。本站不留存照片；外部服务留存规则另行适用。</span></label>
            </template>
            <div class="form-actions"><button type="submit" class="primary full" :disabled="busy"><LoaderCircle v-if="busy" class="spin" :size="17"/><ArrowRight v-else :size="17"/>{{ busy ? progress : mode === 'meihua' ? '起卦并解读' : '查阅并解读' }}</button><button v-if="mode === 'meihua'" type="button" class="secondary full" :disabled="busy" @click="onlyChart">只看卦象</button></div>
          </form>
          <p class="privacy-note"><ShieldCheck :size="15"/>数字顾问 · AI 生成<br>传统文化参考，不作为专业决策依据</p>
        </section>

        <section class="reading-section" aria-labelledby="reading-title">
          <div class="section-heading"><h2 id="reading-title">{{ chart ? '此刻之象' : mode === 'meihua' ? '卦中有序' : '观其形，明其名' }}</h2><span>贰 · 观</span><button v-if="report || preview" class="icon-button" :disabled="busy" title="新建咨询" aria-label="新建咨询" @click="newConsult"><Plus :size="18"/></button></div>
          <div v-if="error" class="alert error" role="alert"><span>{{ error }}</span><a v-if="errorCode === 'API_NOT_CONFIGURED'" href="#/admin">前往配置 <ArrowUpRight :size="14"/></a></div>
          <div v-if="!session.configured && !error" class="alert quiet"><span>AI 服务尚未配置</span><a href="#/admin">管理后台 <ArrowUpRight :size="14"/></a></div>
          <template v-if="chart">
            <div class="hexagrams"><Hexagram :hex="chart.original" label="本卦" :moving="chart.movingLine"/><div class="hex-divider" aria-hidden="true">·</div><Hexagram :hex="chart.mutual" label="互卦"/><div class="hex-divider" aria-hidden="true">·</div><Hexagram :hex="chart.changed" label="变卦"/></div>
            <div class="chart-meta"><span>动爻 <strong>第{{ chart.movingLine }}爻</strong></span><span>体 <strong>{{ chart.body.name }} · {{ chart.body.element }}</strong></span><span>用 <strong>{{ chart.use.name }} · {{ chart.use.element }}</strong></span></div>
            <div v-if="report?.sources.find(d => d.id === `gua-${chart!.original.number}`)" class="classic-quote"><span>卦辞</span><p>{{ report.sources.find(d => d.id === `gua-${chart!.original.number}`)?.body }}</p><small>《周易》· {{ chart.original.name }}</small></div>
          </template>
          <template v-if="report">
            <div class="report-heading"><span class="ink-label">{{ labels[report.kind] }}</span><h3>{{ report.question }}</h3><small>{{ date(report.created_at) }} · {{ labels[busy ? 'running' : report.status] }}</small></div>
            <details v-if="report.observation" class="source-item"><summary>本次保存的可见观察 <ChevronDown :size="14"/></summary><p class="preserve">{{ report.observation }}</p><small>照片未保留；后续追问不会重新查看原图。</small></details>
            <div v-for="(message, i) in report.messages" :key="i" :class="message.role === 'user' ? 'user-message' : 'report-content'"><span v-if="message.role === 'user'" class="message-label">追问</span><div v-if="message.role === 'assistant'" v-html="markdown(message.content)"></div><p v-else>{{ message.content }}</p></div>
            <div v-if="partial" class="report-content live" aria-live="polite" v-html="markdown(partial)"></div>
            <div v-if="busy" class="generating" role="status"><LoaderCircle class="spin" :size="17"/>{{ progress }}</div>
            <div v-if="!busy && canGenerate" class="report-start"><p>{{ report.status === 'draft' ? '盘面已存，解读尚未完成。' : '上次解读未完成，可手动重试。' }}</p><button class="secondary" @click="generate()"><RefreshCw :size="16"/>{{ report.status === 'draft' ? '开始解读' : '重试解读' }}</button></div>
            <div v-if="report.sources.length" class="sources"><h4><BookOpen :size="16"/>本次参考资料 <span>{{ report.sources.length }}</span></h4><details v-for="doc in report.sources" :key="doc.id" class="source-item"><summary>{{ doc.title }}<small>[K:{{ doc.id }}]</small><ChevronDown :size="14"/></summary><p>{{ doc.body }}</p><dl><dt>出处</dt><dd>{{ doc.source }}</dd><dt>版本</dt><dd>{{ doc.edition }}</dd><dt>使用说明</dt><dd>{{ doc.rights }}</dd></dl></details></div>
            <p v-else class="footnote">本次没有匹配到已启用资料，解读不得补造文献依据。</p>
            <form v-if="report.messages.some(m => m.role === 'assistant')" class="followup" @submit.prevent="generate(followup)"><label for="followup">循此再问</label><div><textarea id="followup" v-model="followup" maxlength="2000" rows="2" placeholder="围绕本次盘面继续追问……" required :disabled="busy"></textarea><button class="icon-button send-button" aria-label="发送追问" title="发送追问" :disabled="busy || !followup.trim()"><Send :size="18"/></button></div></form>
          </template>
          <div v-else-if="!chart" class="empty-state reading-empty">
            <div v-if="mode === 'meihua'" class="empty-hex" aria-hidden="true"><span></span><span class="split"></span><span></span><span class="split"></span><span class="split"></span><span></span></div><Hand v-else-if="mode === 'palm'" :size="68" :stroke-width="1"/><ScanFace v-else :size="68" :stroke-width="1"/>
            <h3>{{ mode === 'meihua' ? '静心，问一事' : '一观其形，一解其名' }}</h3><span class="empty-caption">{{ mode === 'meihua' ? '待起卦' : '待查阅' }}</span><p class="empty-bottom">{{ mode === 'meihua' ? '易有太极，是生两仪' : '只述可见，不以相断人' }}</p>
          </div>
        </section>
      </div>
    </main>
    <footer class="site-footer"><span>观象 · 传统文化与自我梳理</span><button class="text-button" @click="notice = true">顾问与隐私说明 <ArrowUpRight :size="13"/></button></footer>

    <div v-if="notice" class="modal-backdrop" @click.self="notice = false"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="notice-title"><button class="icon-button modal-close" title="关闭说明" aria-label="关闭说明" @click="notice = false"><X :size="20"/></button><img src="/seal.svg" width="42" height="42" alt="观象印记"><h2 id="notice-title">关于这位数字顾问</h2><p>观象使用 AI API 生成解读，不是真人大师，也不提供真人批阅。排盘由程序计算，文献与 AI 演绎分开展示。</p><p>仅受理问卦解卦及手相面相文化咨询，不提供程序开发等无关服务。内容仅作传统文化参考和自我梳理，不替代医疗、法律、投资等专业服务。手相与面相只作文化图解，不据外貌判断人格、健康或命运。</p><p>问题、相关背景、文字观察与追问会发送给后台配置的外部模型服务。照片需另行确认后发送，本站不持久保存照片；外部服务的留存政策另行适用。</p><p>文字记录保存在本机 MySQL，不公开展示，使用浏览器凭证隔离；共用浏览器也会共用记录。可在往录中删除；清除浏览器凭证后不能恢复访问。设备管理员仍可能访问本机数据库。</p><button class="primary full" @click="accept">已知悉并继续 <ArrowRight :size="16"/></button></section></div>
  </div>
</template>
