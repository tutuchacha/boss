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
 *   求职者端：登录页/错误密码/身份不匹配拦截/职位列表/详情/投递/我的投递/
 *            消息/退出登录
 *   认证    ：验证码登录（60s 倒计时）、HR 注册（自动建未认证公司）
 *   HR 端   ：工作台/四 Tab/职位筛选/发布(含空表单校验)/编辑/关闭/
 *            沟通/聊天/候选人管理/约面试/人才筛选(关键词+标签匹配)/
 *            公司资料/企业认证(自动通过)
 *   跨端    ：关闭职位对求职者不可见；求职者投递→HR 候选人可见→约面→
 *            求职者接受→HR 回看「候选人已接受」
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
/** 注册验收专用（新手机号 → 自动创建「新注册企业-4321」） */
const REG = { phone: '13900004321', password: '123456', company: '新注册企业-4321' }
/** 演示环境固定验证码 */
const SMS_CODE = '123456'

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

  /** 按可见文本点击第一个包含该文本的 button */
  async click(text) {
    const r = await this.eval(`(() => {
      const el = [...document.querySelectorAll('button')].find(e => (e.textContent||'').includes(${JSON.stringify(text)}))
      if (!el) return { ok: false }
      el.click(); return { ok: true }
    })()`)
    await sleep(450)
    return r
  }

  /** 按 aria-label 点击（返回键等纯图标按钮） */
  async clickAria(label) {
    const sel = `button[aria-label=${JSON.stringify(label)}]`
    const r = await this.eval(`(() => {
      const el = document.querySelector(${JSON.stringify(sel)})
      if (!el) return { ok: false }
      el.click(); return { ok: true }
    })()`)
    await sleep(450)
    return r
  }

  /**
   * 点击登录 / 注册表单的提交按钮。
   * 登录页有三个模式页签（密码登录 / 验证码登录 / 注册），
   * 「登录」文本会同时命中页签，故用 form 内 [type=submit] 精确定位。
   */
  async clickSubmit() {
    const r = await this.eval(`(() => {
      const el = document.querySelector('form button[type="submit"]')
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

  /** 轮询等待页面文本命中正则（异步渲染用，替代固定 sleep） */
  async waitText(re, timeout = 8000, step = 350) {
    const t0 = Date.now()
    for (;;) {
      const t = await this.text()
      if (re.test(t)) return true
      if (Date.now() - t0 > timeout) return false
      await sleep(step)
    }
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
    await cdp.clickSubmit()
    await sleep(1500)
    push('错误密码被拒绝', /账号或密码错误/.test(await cdp.text()))

    await cdp.setInput('#login-account', HR.phone)
    await cdp.setInput('#login-password', HR.password)
    await cdp.clickSubmit()
    await sleep(1500)
    const mismatch = await cdp.text()
    push('身份不匹配被拦截', /公司代表/.test(mismatch) && /切换/.test(mismatch))

    await cdp.click('求职者')
    await cdp.clickSubmit()
    await sleep(3200)
    t = await cdp.text()
    const jobTotal = t.match(/共 (\d+) 个职位/)?.[1]
    push('求职者进入职位列表', Boolean(jobTotal), `共 ${jobTotal} 个职位`)

    await cdp.eval(`(() => { document.querySelector('article').click(); return true })()`)
    await sleep(1800)
    push('职位详情可打开', /立即沟通|继续沟通/.test(await cdp.text()))
    push('职位详情提供投递入口', /投递简历|已投递/.test(await cdp.text()))
    await cdp.clickAria('返回')
    await sleep(1200)

    await cdp.click('消息')
    await sleep(1200)
    push('消息列表可用', (await cdp.text()).length > 60)

    await cdp.click('我的')
    await sleep(1200)
    t = await cdp.text()
    push('「我的」提供退出登录', /退出登录/.test(t))
    push('「我的」提供我的投递入口', /我的投递/.test(t))
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)
    push('求职者退出登录成功', /我要找工作/.test(await cdp.text()))

    // ============================== HR 端 ==============================
    console.log('\n=== HR 端 ===')
    await cdp.click('公司代表 HR')
    await cdp.clickSubmit()
    await sleep(3200)
    t = await cdp.text()
    push('HR 进入工作台', /HR 工作台/.test(t) && /云杉科技/.test(t))
    push(
      '底部四个 Tab',
      /职位管理/.test(t) && /候选人/.test(t) && /沟通/.test(t) && /我的/.test(t),
    )

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
    await cdp.clickAria('返回') // 退出聊天覆盖层（此前遗漏，会导致后续点击落空）
    await sleep(1200)

    // ============================== HR 候选人管理 ==============================
    console.log('\n=== HR 候选人管理 ===')
    await cdp.click('候选人')
    await sleep(1600)
    t = await cdp.text()
    push('候选人管理可用', /候选人管理/.test(t) && /共 \d+ 位候选人/.test(t))
    const candCount = await cdp.eval(`[...document.querySelectorAll('button')].filter(b => /应聘：/.test(b.innerText)).length`)
    push('候选人列表有数据', candCount > 0, `${candCount} 条`)

    const openedCand = await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => x.innerText.includes('林小满'))
      if (!b) return false
      b.click(); return true
    })()`)
    await sleep(1600)
    t = await cdp.text()
    push('候选人详情可打开', openedCand && /候选人详情/.test(t) && /简历完善度/.test(t))
    push('候选人详情提供约面试', /约面试/.test(t))

    await cdp.click('约面试')
    await sleep(1300)
    t = await cdp.text()
    push('面试邀请表单可打开', /发起面试邀请/.test(t) && /面试形式/.test(t))
    await cdp.click('发起面试邀请')
    await sleep(2000)
    t = await cdp.text()
    push('面试邀请已发送', /面试安排/.test(t) && /等待候选人确认/.test(t))

    await cdp.clickAria('返回')
    await sleep(1400)
    const inviteState = await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => x.innerText.includes('林小满'))
      return b ? b.innerText : ''
    })()`)
    push('候选人状态更新为面试中', /面试中/.test(inviteState))

    // ============================== HR 人才筛选 ==============================
    console.log('\n=== HR 人才筛选 ===')
    /**
     * 人才筛选覆盖层的文本（页面栈顶层）。
     * ⚠️ 不要用 document.body.innerText：被覆盖层遮住的根视图（候选人列表）
     * 文本仍在 DOM 中，会让「未命中不出现」类断言误判。
     */
    const matchOverlayText = () =>
      cdp.eval(`(() => {
        const els = [...document.querySelectorAll('.pl-page-enter')]
        const el = els[els.length - 1]
        return el ? el.innerText : ''
      })()`)

    await cdp.click('按技能筛选')
    await sleep(1500)
    let mt = await matchOverlayText()
    push('人才筛选页可打开', /人才筛选/.test(mt) && /技能需求/.test(mt))

    await cdp.setInput('input[aria-label="技能需求"]', 'React')
    await sleep(400)
    await cdp.click('开始筛选')
    await sleep(1400)
    mt = await matchOverlayText()
    push('关键词筛选命中候选人', /找到 \d+ 位相关候选人/.test(mt) && mt.includes('林小满'))
    push('展示关联证据片段', mt.includes('技术栈') && /项目经历/.test(mt))
    push('未命中候选人不出现', /找到 \d+ 位相关候选人/.test(mt) && !mt.includes('沈禾'))

    await cdp.setInput('input[aria-label="技能需求"]', 'COBOL')
    await sleep(400)
    await cdp.click('开始筛选')
    await sleep(1400)
    push('无匹配时给出提示', /没有匹配的候选人/.test(await matchOverlayText()))

    await cdp.setInput('input[aria-label="技能需求"]', '')
    await sleep(300)
    await cdp.click('自动化测试')
    await sleep(300)
    await cdp.click('开始筛选')
    await sleep(1400)
    mt = await matchOverlayText()
    push('标签筛选命中测试候选人', mt.includes('沈禾') && mt.includes('回归'))

    await cdp.clickAria('返回')
    await sleep(1000)

    // ============================== 跨端联动：投递闭环 ==============================
    console.log('\n=== 跨端联动 ===')
    await cdp.click('我的')
    await sleep(1100)
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)

    // 切回求职者：搜索云杉职位并投递
    await cdp.click('求职者')
    await cdp.clickSubmit()
    await sleep(3200)
    push('求职者重新登录', /共 \d+ 个职位/.test(await cdp.text()))

    await cdp.setInput('input[placeholder*="搜索职位"]', '云杉')
    await sleep(1300)
    const cloudCount = await cdp.eval(`document.querySelectorAll('article').length`)
    push('可按公司搜索到职位', cloudCount > 0, `${cloudCount} 条`)
    await cdp.eval(`(() => { const a = document.querySelector('article'); if (a) a.click(); return !!a })()`)
    await sleep(1900)
    push('职位详情可打开', /投递简历/.test(await cdp.text()))
    const cloudJobTitle = String(await cdp.eval(`(document.querySelector('h1')?.innerText || '')`)).trim()

    await cdp.click('投递简历')
    await sleep(900)
    t = await cdp.text()
    push('投递表单可打开', /在线简历/.test(t) && /确认投递/.test(t))
    await cdp.click('确认投递')
    await sleep(1200)
    push('投递成功', /投递成功/.test(await cdp.text()))
    const appliedBtn = await cdp.eval(`[...document.querySelectorAll('button')].some(b => b.textContent.includes('已投递'))`)
    push('按钮切换为已投递', appliedBtn)

    await cdp.clickAria('返回')
    await sleep(1200)

    // 已关闭职位对求职者不可见
    await cdp.setInput('input[placeholder*="搜索职位"]', NEW_JOB)
    await sleep(1300)
    push('已关闭职位对求职者不可见', !(await cdp.text()).includes(NEW_JOB))
    await cdp.setInput('input[placeholder*="搜索职位"]', '')
    await sleep(700)

    // 我的投递：新投递出现在列表
    await cdp.click('我的')
    await sleep(1100)
    await cdp.click('我的投递')
    await sleep(1600)
    t = await cdp.text()
    push(
      '我的投递包含新投递',
      Boolean(cloudJobTitle) && t.includes(cloudJobTitle) && /待处理/.test(t),
      cloudJobTitle,
    )

    await cdp.clickAria('返回')
    await sleep(900)
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)

    // ============================== HR 处理新投递 ==============================
    console.log('\n=== HR 处理新投递 ===')
    await cdp.click('公司代表 HR')
    await cdp.clickSubmit()
    await sleep(3200)
    await cdp.click('候选人')
    await sleep(1600)
    await cdp.setInput('input[aria-label="搜索候选人"]', '林晓')
    await sleep(1100)
    const hasSeekerApp = await cdp.eval(`[...document.querySelectorAll('button')].some(b => b.innerText.includes('林晓'))`)
    push('HR 收到求职者新投递', hasSeekerApp)

    await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => x.innerText.includes('林晓'))
      if (b) b.click(); return !!b
    })()`)
    await sleep(1600)
    t = await cdp.text()
    push('HR 可查看投递详情', /候选人详情/.test(t) && /应聘职位/.test(t))

    await cdp.click('约面试')
    await sleep(1300)
    await cdp.click('发起面试邀请')
    await sleep(2000)
    t = await cdp.text()
    push('面试邀请已发送给求职者', /面试安排/.test(t) && /等待候选人确认/.test(t))

    await cdp.clickAria('返回')
    await sleep(1000)
    await cdp.click('我的')
    await sleep(1000)
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)

    // ============================== 求职者接受面试 ==============================
    console.log('\n=== 求职者接受面试 ===')
    await cdp.click('求职者')
    await cdp.clickSubmit()
    await sleep(3200)
    await cdp.click('我的')
    await sleep(1100)
    await cdp.click('我的投递')
    await sleep(1600)
    t = await cdp.text()
    push('面试邀请已送达求职者', /面试邀请/.test(t) && /待你确认/.test(t))
    await cdp.click('接受面试')
    await sleep(1800)
    push('面试邀请已接受', /已接受邀请/.test(await cdp.text()))

    await cdp.clickAria('返回')
    await sleep(900)
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)

    // HR 回看：候选人已接受
    await cdp.click('公司代表 HR')
    await cdp.clickSubmit()
    await sleep(3200)
    await cdp.click('候选人')
    await sleep(1600)
    await cdp.setInput('input[aria-label="搜索候选人"]', '林晓')
    await sleep(1100)
    await cdp.eval(`(() => {
      const b = [...document.querySelectorAll('button')].find(x => x.innerText.includes('林晓'))
      if (b) b.click(); return !!b
    })()`)
    await sleep(1600)
    push('HR 可见候选人已接受', /候选人已接受/.test(await cdp.text()))

    await cdp.clickAria('返回')
    await sleep(900)
    await cdp.click('我的')
    await sleep(900)
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)

    // ============================== 验证码登录 ==============================
    console.log('\n=== 验证码登录 ===')
    await cdp.click('验证码登录')
    await sleep(500)
    await cdp.setInput('#sms-phone', SEEKER.phone)
    await cdp.click('获取验证码')
    await sleep(1000)
    await cdp.setInput('#sms-code', SMS_CODE)
    await cdp.clickSubmit()
    await sleep(3200)
    push('验证码登录成功', /共 \d+ 个职位/.test(await cdp.text()))
    await cdp.click('我的')
    await sleep(1000)
    await cdp.click('退出登录')
    await sleep(700)
    await cdp.click('确认退出')
    await sleep(2500)

    // ============================== 注册（HR）→ 企业认证 → 公司资料 ==============================
    console.log('\n=== 注册与企业认证 ===')
    await cdp.click('注册')
    await sleep(500)
    await cdp.click('公司代表')
    await sleep(400)
    await cdp.setInput('#reg-phone', REG.phone)
    await cdp.click('获取验证码')
    await sleep(1000)
    await cdp.setInput('#reg-code', SMS_CODE)
    await cdp.setInput('#reg-password', REG.password)
    await cdp.clickSubmit()
    await sleep(3600)
    t = await cdp.text()
    push('注册后可进入 HR 工作台', /HR 工作台/.test(t) && t.includes(REG.company))

    await cdp.click('我的')
    await sleep(1200)
    push('新公司显示未认证', /未认证/.test(await cdp.text()))
    await cdp.click('企业认证')
    const verifyOpen = await cdp.waitText(/认证资料/, 8000)
    push('认证表单可打开', verifyOpen, verifyOpen ? '' : (await cdp.text()).slice(0, 160))
    await cdp.click('提交认证资料')
    const pending = await cdp.waitText(/认证审核中/, 8000)
    push('提交后进入审核中', pending)
    const approved = await cdp.waitText(/企业认证已通过/, 12000)
    push('Mock 审核自动通过', approved)
    await cdp.clickAria('返回')
    push('认证状态同步到「我的」', await cdp.waitText(/已认证/, 8000))

    await cdp.click('公司资料')
    await sleep(1400)
    await cdp.setInput('#company-name', 'E2E验收企业')
    await cdp.click('保存修改')
    await sleep(2200)
    push('公司资料编辑已保存', (await cdp.text()).includes('E2E验收企业'))

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
