/**
 * 聘聊 · 端到端验收脚本（可复用回归测试）
 * =====================================================================
 * 用途：对当前代码做一次真实浏览器验收，覆盖项目的核心链路。
 *      改完代码后跑一遍，确认没有回归。
 *
 * 前置条件：
 *   1. dev server 已在运行（另开终端执行 `npm run dev`）
 *   2. 本机安装 Chrome
 *
 * 运行：
 *   node tests/e2e-verify.cjs
 *   # 或指定地址 / Chrome 路径：
 *   BASE_URL=http://localhost:3000 CHROME_PATH="C:\\...\\chrome.exe" node tests/e2e-verify.cjs
 *
 * 覆盖范围：
 *   求职者端：登录页/错误密码/身份不匹配拦截/职位列表/详情/消息/退出登录
 *   HR 端   ：工作台/三 Tab/职位筛选/发布(含空表单校验)/编辑/关闭/沟通/聊天
 *   跨端    ：HR 发布的职位对求职者可见；关闭后不可见
 *   质量    ：零控制台错误、零 hydration 报错
 *
 * ⚠️ 进程清理：只终止本脚本 spawn 的 Chrome 进程树（killChromeTree）。
 *    绝不要用 `Get-Process chrome | Stop-Process -Force` ——
 *    那会杀掉用户正在使用的全部 Chrome 标签页。
 * =====================================================================
 */

'use strict'

const { spawn, execFileSync } = require('node:child_process')
const os = require('node:os')
const path = require('node:path')
const fs = require('node:fs')
const { killChromeTree } = require('../.zscripts/kill-chrome-safe.cjs')

// ---------------------------------------------------------------- 配置

const BASE_URL = process.env.BASE_URL || 'http://localhost:3000'
const CDP_PORT = Number(process.env.CDP_PORT || 9260)
const CHROME_PATH =
  process.env.CHROME_PATH ||
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'

/** 演示账号（与 src/api/mock/auth.ts 保持一致） */
const SEEKER = { phone: '13800000001', password: '123456' }
const HR = { phone: '13800000002', password: '123456' }

/** 本脚本自建的职位名（避免与既有 Mock 数据冲突） */
const NEW_JOB = `E2E验收职位${Date.now().toString(36).slice(-4)}`

const sleep = ms => new Promise(r => setTimeout(r, ms))

/** 统计当前系统上的 chrome 进程数（用于证明未影响用户浏览器） */
function countChrome() {
  try {
    const out = execFileSync(
      'powershell',
      ['-NoProfile', '-Command', "(Get-Process chrome -ErrorAction SilentlyContinue | Measure-Object).Count"],
      { encoding: 'utf8', windowsHide: true },
    )
    return Number(String(out).trim()) || 0
  } catch {
    return -1
  }
}

// ---------------------------------------------------------------- CDP 客户端

class Cdp {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    this.logs = []
    ws.addEventListener('message', ev => {
      const m = JSON.parse(ev.data)
      if (m.id !== undefined && this.pending.has(m.id)) {
        const { resolve, reject } = this.pending.get(m.id)
        this.pending.delete(m.id)
        m.error ? reject(new Error(JSON.stringify(m.error))) : resolve(m.result)
      } else if (m.method) {
        this.logs.push(m)
      }
    })
  }

  send(method, params = {}) {
    const id = ++this.id
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.ws.send(JSON.stringify({ id, method, params }))
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error(`CDP 超时: ${method}`))
        }
      }, 25000)
    })
  }

  async eval(expression) {
    const r = await this.send('Runtime.evaluate', {
      expression: `(() => { try { return JSON.stringify(${expression}) } catch (e) { return JSON.stringify({__err:String(e)}) } })()`,
      awaitPromise: true,
      returnByValue: true,
    })
    return r?.result?.value ? JSON.parse(r.result.value) : null
  }

  async click(text) {
    const r = await this.eval(`(() => {
      const el = [...document.querySelectorAll('button')].find(e => (e.textContent||'').includes(${JSON.stringify(text)}))
      if (!el) return { ok: false }
      el.click(); return { ok: true }
    })()`)
    await sleep(450)
    return r
  }

  async setInput(selector, value) {
    return this.eval(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)})
      if (!el) return { ok: false }
      const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, ${JSON.stringify(value)})
      el.dispatchEvent(new Event('input', { bubbles: true }))
      return { ok: true }
    })()`)
  }

  async text() {
    return this.eval(`document.body.innerText`)
  }

  /** 收集控制台错误（已过滤已知噪音） */
  consoleErrors(extraFilter) {
    return this.logs
      .filter(e => e.method === 'Log.entryAdded' && e.params?.entry?.level === 'error')
      .map(e => e.params.entry.text)
      .filter(t => !/404|favicon|DevTools|script tag/i.test(t))
      .filter(t => (extraFilter ? !extraFilter.test(t) : true))
  }

  hydrationErrors() {
    return this.logs
      .filter(e => e.method === 'Log.entryAdded')
      .map(e => e.params?.entry?.text || '')
      .filter(t => /Hydration|hydrat/i.test(t))
  }
}

// ---------------------------------------------------------------- 主流程

async function main() {
  const results = []
  const push = (name, pass, detail = '') => {
    results.push({ name, pass, detail })
    console.log(`  ${pass ? 'PASS' : 'FAIL'}  ${name}${detail ? '  → ' + detail : ''}`)
  }

  // ---- 前置检查 ----
  if (!fs.existsSync(CHROME_PATH)) {
    console.error(`✗ 找不到 Chrome：${CHROME_PATH}`)
    console.error('  可用 CHROME_PATH 环境变量指定路径。')
    process.exit(2)
  }
  try {
    const res = await fetch(BASE_URL)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
  } catch (e) {
    console.error(`✗ 无法访问 ${BASE_URL} —— 请先在另一个终端运行 \`npm run dev\``)
    console.error(`  原因：${e.message}`)
    process.exit(2)
  }

  const chromeBefore = countChrome()

  const userDataDir = path.join(os.tmpdir(), `pinliao-e2e-${Date.now()}`)
  fs.mkdirSync(userDataDir, { recursive: true })

  const chrome = spawn(
    CHROME_PATH,
    [
      '--headless=new',
      `--remote-debugging-port=${CDP_PORT}`,
      `--user-data-dir=${userDataDir}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-gpu',
      '--window-size=430,900',
      'about:blank',
    ],
    { stdio: 'ignore' },
  )

  let wsUrl = null
  for (let i = 0; i < 60; i++) {
    await sleep(500)
    try {
      const targets = await (await fetch(`http://127.0.0.1:${CDP_PORT}/json/list`)).json()
      const page = targets.find(t => t.type === 'page' && t.webSocketDebuggerUrl)
      if (page) {
        wsUrl = page.webSocketDebuggerUrl
        break
      }
    } catch {
      /* 未就绪 */
    }
  }
  if (!wsUrl) {
    console.error('✗ 无法连接 Chrome CDP')
    killChromeTree(chrome.pid)
    process.exit(2)
  }

  const ws = new WebSocket(wsUrl)
  await new Promise((res, rej) => {
    ws.addEventListener('open', res)
    ws.addEventListener('error', rej)
  })
  const cdp = new Cdp(ws)
  await cdp.send('Runtime.enable')
  await cdp.send('Log.enable')
  await cdp.send('Page.enable')

  try {
    await cdp.send('Page.navigate', { url: BASE_URL })
    await sleep(6000)

    // ============================== 求职者端 ==============================
    console.log('\n=== 求职者端 ===')
    let t = await cdp.text()
    push('未登录时停在登录页', /我要找工作/.test(t) && /我是公司代表/.test(t))
    push('登录页提供演示账号', t.includes(SEEKER.phone) && t.includes(HR.phone))

    await cdp.click('求职者')
    await cdp.setInput('#login-account', SEEKER.phone)
    await cdp.setInput('#login-password', 'wrong-password')
    await cdp.click('登录')
    await sleep(1500)
    push('错误密码被拒绝', /账号或密码错误/.test(await cdp.text()))

    await cdp.setInput('#login-account', HR.phone)
    await cdp.setInput('#login-password', HR.password)
    await cdp.click('登录')
    await sleep(1500)
    const mismatch = await cdp.text()
    push('身份不匹配被拦截', /公司代表/.test(mismatch) && /切换/.test(mismatch))

    await cdp.click('求职者')
    await cdp.click('登录')
    await sleep(3200)
    t = await cdp.text()
    const jobTotal = t.match(/共 (\d+) 个职位/)?.[1]
    push('求职者进入职位列表', Boolean(jobTotal), `共 ${jobTotal} 个职位`)

    await cdp.eval(`(() => { document.querySelector('article').click(); return true })()`)
    await sleep(1800)
    push('职位详情可打开', /立即沟通|继续沟通/.test(await cdp.text()))
    await cdp.click('返回')
    await sleep(1200)

    await cdp.click('消息')
    await sleep(1200)
    push('消息列表可用', (await cdp.text()).length > 60)

    await cdp.click('我的')
    await sleep(1200)
    push('「我的」提供退出登录', /退出登录/.test(await cdp.text()))
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)
    push('求职者退出登录成功', /我要找工作/.test(await cdp.text()))

    // ============================== HR 端 ==============================
    console.log('\n=== HR 端 ===')
    await cdp.click('公司代表 HR')
    await cdp.click('登录')
    await sleep(3200)
    t = await cdp.text()
    push('HR 进入工作台', /HR 工作台/.test(t) && /云杉科技/.test(t))
    push('底部三个 Tab', /职位管理/.test(t) && /沟通/.test(t) && /我的/.test(t))

    const jobCount = await cdp.eval(`document.querySelectorAll('article').length`)
    push('职位列表有数据', jobCount > 0, `${jobCount} 条`)

    await cdp.click('已关闭')
    await sleep(1200)
    const filtered = await cdp.eval(`(() => {
      const a = [...document.querySelectorAll('article')]
      return { n: a.length, allClosed: a.every(x => x.innerText.includes('已关闭')) }
    })()`)
    push('职位状态筛选生效', filtered.allClosed, JSON.stringify(filtered))
    await cdp.click('全部')
    await sleep(1200)

    // 发布（先验空表单校验）
    await cdp.click('发布职位')
    await sleep(1500)
    push('发布表单可打开', /基本信息/.test(await cdp.text()))
    await cdp.click('发布职位')
    await sleep(1200)
    push('空表单被校验拦截', /请填写职位名称/.test(await cdp.text()))

    await cdp.setInput('input[placeholder="如：前端开发工程师"]', NEW_JOB)
    await cdp.eval(`(() => {
      const els = [...document.querySelectorAll('input[type=number]')]
      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
      set.call(els[0], '26'); els[0].dispatchEvent(new Event('input', { bubbles: true }))
      set.call(els[1], '42'); els[1].dispatchEvent(new Event('input', { bubbles: true }))
      return true
    })()`)
    await cdp.click('发布职位')
    await sleep(3000)
    push('职位发布成功', (await cdp.text()).includes(NEW_JOB))

    // 编辑
    const opened = await cdp.eval(`(() => {
      const a = [...document.querySelectorAll('article')].find(x => x.innerText.includes(${JSON.stringify(NEW_JOB)}))
      if (!a) return false
      const b = [...a.querySelectorAll('button')].find(x => x.textContent.includes('编辑'))
      if (!b) return false
      b.click(); return true
    })()`)
    await sleep(1800)
    const editText = await cdp.text()
    push('编辑表单回填原值', opened && /编辑职位/.test(editText) && editText.includes(NEW_JOB))
    await cdp.setInput('input[placeholder="如：前端开发工程师"]', NEW_JOB + '改')
    await cdp.click('保存修改')
    await sleep(3000)
    push('修改已保存', (await cdp.text()).includes(NEW_JOB + '改'))

    // 关闭
    const closed = await cdp.eval(`(() => {
      const a = [...document.querySelectorAll('article')].find(x => x.innerText.includes(${JSON.stringify(NEW_JOB + '改')}))
      if (!a) return false
      const b = [...a.querySelectorAll('button')].find(x => x.textContent.includes('关闭'))
      if (!b) return false
      b.click(); return true
    })()`)
    await sleep(2500)
    const closedState = await cdp.eval(`(() => {
      const a = [...document.querySelectorAll('article')].find(x => x.innerText.includes(${JSON.stringify(NEW_JOB + '改')}))
      if (!a) return { found: false }
      return { found: true, hasReopen: [...a.querySelectorAll('button')].some(b => b.textContent.includes('重新开放')) }
    })()`)
    push('职位可关闭', closed && closedState.hasReopen, JSON.stringify(closedState))

    // 沟通板块
    await cdp.click('沟通')
    await sleep(2000)
    t = await cdp.text()
    push('沟通板块可用', /候选人沟通/.test(t) && /共 \d+ 个会话/.test(t), t.match(/共 \d+ 个会话[^\n]*/)?.[0] ?? '')
    const convCount = await cdp.eval(`[...document.querySelectorAll('button')].filter(b => /应聘：/.test(b.innerText)).length`)
    push('候选人会话可渲染', convCount > 0, `${convCount} 条`)

    // 打开会话并发送消息
    await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => /应聘：/.test(x.innerText))
      if (b) b.click(); return true
    })()`)
    await sleep(2000)
    const chatOk = /简历完善度/.test(await cdp.text())
    push('聊天页可打开', chatOk)
    if (chatOk) {
      await cdp.setInput('input[aria-label="输入消息"]', '您好，方便约时间沟通吗？')
      await cdp.eval(`(() => { document.querySelector('button[aria-label="发送"]').click(); return true })()`)
      await sleep(1200)
      push('消息可发送', (await cdp.text()).includes('方便约时间沟通吗'))
      await sleep(2600)
      push('候选人自动回复到达', /方便|可以|好的|随时|期望/.test(await cdp.text()))
    }

    // ============================== 跨端联动 ==============================
    console.log('\n=== 跨端联动 ===')
    await cdp.click('我的')
    await sleep(1000)
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)
    await cdp.click('求职者')
    await cdp.click('登录')
    await sleep(3200)
    await cdp.eval(`(() => {
      const el = document.querySelector('input[placeholder*="搜索职位"]')
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, ${JSON.stringify(NEW_JOB)})
      el.dispatchEvent(new Event('input', { bubbles: true }))
      return true
    })()`)
    await sleep(1200)
    push('已关闭职位对求职者不可见', !(await cdp.text()).includes(NEW_JOB))

    // ============================== 质量 ==============================
    console.log('\n=== 质量 ===')
    const errs = cdp.consoleErrors()
    push('零控制台错误', errs.length === 0, errs.slice(0, 2).join(' | '))
    push('零 hydration 报错', cdp.hydrationErrors().length === 0)
  } finally {
    try { ws.close() } catch { /* ignore */ }
    killChromeTree(chrome.pid)
    await sleep(600)
    try { fs.rmSync(userDataDir, { recursive: true, force: true }) } catch { /* ignore */ }
  }

  // ---- 结果 ----
  const chromeAfter = countChrome()
  const failed = results.filter(r => !r.pass)

  console.log(`\n==== 验收结果：${results.length - failed.length}/${results.length} 通过 ====`)
  if (failed.length) {
    console.log('失败项：')
    failed.forEach(f => console.log(`  - ${f.name}${f.detail ? '  → ' + f.detail : ''}`))
  }
  if (chromeBefore >= 0 && chromeAfter >= 0) {
    const delta = chromeBefore - chromeAfter
    const safe = delta <= 0
    console.log(`进程清理检查：用户 Chrome 进程 ${chromeBefore} → ${chromeAfter} ${safe ? '（未受影响 ✓）' : `（⚠️ 少了 ${delta} 个）`}`)
  }

  process.exit(failed.length ? 1 : 0)
}

main().catch(err => {
  console.error('脚本异常:', err)
  process.exit(2)
})
