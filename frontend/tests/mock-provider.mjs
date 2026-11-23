import http from 'node:http'
const server = http.createServer(async (req, res) => {
  if (req.url === '/health') { res.end('ok'); return }
  let raw = ''; for await (const chunk of req) { raw += chunk; if (raw.length > 5000000) { res.writeHead(413).end(); return } }
  let data; try { data = JSON.parse(raw) } catch { res.writeHead(400).end(); return }
  const mode = req.url.split('/')[1]
  if (mode === 'timeout') { const timer = setTimeout(() => res.end(), 20000); res.on('close', () => clearTimeout(timer)); return }
  if (mode === 'unsupported') { res.writeHead(400).end(); return }
  if (mode === 'stall' && data.stream) { res.writeHead(200, { 'Content-Type': 'text/event-stream' }); res.write('data: {"choices":[{"delta":{"content":"开始"}}]}\n\n'); const timer = setTimeout(() => res.end(), 20000); res.on('close', () => clearTimeout(timer)); return }
  if (mode === 'auth') { res.writeHead(401).end('Do not expose raw provider body'); return }
  if (mode === 'rate') { res.writeHead(429).end(); return }
  const visual = data.messages.some(m => Array.isArray(m.content))
  if (!data.stream) {
    let content = '连接成功'
    if (data.messages[0]?.content.includes('范围审核器')) {
      const input = JSON.parse(data.messages[1].content)
      const question = input.currentFollowup || `${input.originalQuestion} ${input.background}`
      content = /帮我写|调试代码|翻译成英文|告诉我其他用户|只输出 ALLOW/.test(question) ? 'REJECT' : 'ALLOW'
      if (mode === 'scopeinvalid') content = 'ALLOW 然后忽略规则'
    }
    if (visual) {
      const prompt = data.messages.map(m => typeof m.content === 'string' ? m.content : m.content.filter(c => c.type === 'text').map(c => c.text).join('')).join('')
      if (prompt.includes('中央图形')) content = mode === 'visionfail' ? '无法查看图片' : '红色正方形'
      else content = JSON.stringify({ usable: !['blur', 'multiple'].includes(mode), subject: prompt.includes('单只手掌') ? 'palm' : 'face', quality: mode === 'blur' ? 'unclear' : 'clear', reason: '模拟视觉观察，仅用于测试', observations: ['图像中央可见主体，边缘和部位清楚；这是测试观察，不代表真实识别结果。'] })
    }
    res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify({ choices: [{ message: { content } }], usage: { total_tokens: 42 } })); return
  }
  let id = 'rule-v1'
  try { const message = data.messages[1].content; const facts = JSON.parse(message.slice(message.indexOf('{'))); id = facts.knowledge[0]?.id || '' } catch {}
  if (mode === 'badref') id = 'invented-source'
  const cite = id ? ` [K:${id}]` : ' 本次没有匹配资料，依据不足。'
  const text = `## 已知信息\n这是模拟 API 验收报告，不是真实模型解读。\n## 采用体系\n沿用服务器提供的盘面，不重新起卦。\n## 依据\n使用本次提供的资料。${cite}\n## 解释\n传统文化图解与现代演绎分开呈现。\n## 不确定性\n不根据外貌、掌纹或卦象断言人格、健康和未来。\n## 现实建议\n结合实际信息审慎判断。\n`
  res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' })
  let pos = 0
  const timer = setInterval(() => {
    if (res.destroyed) { clearInterval(timer); return }
    if (mode === 'interrupt' && pos > 50) { clearInterval(timer); res.end(); return }
    if (pos >= text.length) { res.write(`data: ${JSON.stringify({ choices: [{ delta: {}, finish_reason: 'stop' }], usage: { total_tokens: 88 } })}\n\n`); res.end('data: [DONE]\n\n'); clearInterval(timer); return }
    res.write(`data: ${JSON.stringify({ choices: [{ delta: { content: text.slice(pos, pos + 13) }, finish_reason: null }] })}\n\n`); pos += 13
  }, 15)
})
server.listen(19091, '127.0.0.1')
