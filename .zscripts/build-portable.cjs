/**
 * 聘聊 · 跨平台生产构建脚本
 * =====================================================================
 * 解决的问题：
 *   原 `npm run build` 的 post-step 用了 Unix 的 `cp -r`：
 *     next build && cp -r .next/static .next/standalone/.next/ && cp -r public .next/standalone/
 *   在 **Windows 上 cp 不存在** → 构建以退出码 1 失败，且静态资源
 *   （JS/CSS/图片）根本没被拷进 standalone，产物不可用。
 *   同时原 `npm run start` 依赖 `bun` 与 Unix 的 `NODE_ENV=x` 语法，
 *   在未安装 bun 的机器上也无法启动。
 *
 * 本脚本用 Node 自身实现拷贝，Windows / macOS / Linux 通用，
 * 且不依赖 bun。
 *
 * 用法：node .zscripts/build-portable.cjs
 * =====================================================================
 */

'use strict'

const { execFileSync } = require('node:child_process')
const fs = require('node:fs')
const path = require('node:path')

const ROOT = path.resolve(__dirname, '..')
const STANDALONE = path.join(ROOT, '.next', 'standalone')

/** 递归拷贝目录（Node 16.7+ 的 cpSync 原生支持 recursive） */
function copyDir(from, to) {
  if (!fs.existsSync(from)) {
    console.warn(`  ! 跳过（源不存在）：${path.relative(ROOT, from)}`)
    return false
  }
  fs.mkdirSync(path.dirname(to), { recursive: true })
  fs.cpSync(from, to, { recursive: true, force: true })
  return true
}

function main() {
  // ---- 1. next build ----
  console.log('[1/3] next build ...')
  execFileSync(process.execPath, [require.resolve('next/dist/bin/next'), 'build'], {
    cwd: ROOT,
    stdio: 'inherit',
  })

  if (!fs.existsSync(STANDALONE)) {
    console.error('✗ 未生成 .next/standalone —— 请确认 next.config.ts 中 output: "standalone"')
    process.exit(1)
  }

  // ---- 2. 拷贝静态资源（原 cp -r 的等价实现）----
  console.log('[2/3] 拷贝静态资源到 standalone ...')
  const staticOk = copyDir(
    path.join(ROOT, '.next', 'static'),
    path.join(STANDALONE, '.next', 'static'),
  )
  const publicOk = copyDir(path.join(ROOT, 'public'), path.join(STANDALONE, 'public'))
  console.log(`      .next/static → ${staticOk ? '已完成' : '跳过'}`)
  console.log(`      public       → ${publicOk ? '已完成' : '跳过（项目可能无 public 目录）'}`)

  // ---- 3. 自检 ----
  console.log('[3/3] 产物自检 ...')
  const checks = [
    ['.next/standalone/server.js', path.join(STANDALONE, 'server.js')],
    ['.next/standalone/.next/static', path.join(STANDALONE, '.next', 'static')],
  ]
  let allOk = true
  for (const [label, p] of checks) {
    const ok = fs.existsSync(p)
    if (!ok) allOk = false
    console.log(`      ${ok ? '✓' : '✗'} ${label}`)
  }
  if (!allOk) {
    console.error('✗ 产物不完整，standalone 无法正常提供静态资源')
    process.exit(1)
  }

  console.log('\n✓ 构建完成。启动方式：')
  console.log('    npm start        （等价于 node .next/standalone/server.js）')
  console.log('    如需指定端口：$env:PORT=3001; npm start   （Windows PowerShell）')
}

main()
