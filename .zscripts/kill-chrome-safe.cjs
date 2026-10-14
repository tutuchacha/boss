/**
 * 安全清理无头 Chrome（供 DSH 后续写测试脚本时复用）
 * =====================================================================
 * 事故背景：
 *   曾用 `Get-Process chrome | Stop-Process -Force` 清理无头浏览器，
 *   这会把**用户自己正在使用的 Chrome 全部标签页一起杀掉**。
 *   标签页被强杀后状态丢失，已打开的页面报错（"Aw, Snap" / 白屏）。
 *
 * 正确原则：
 *   只终止「本脚本自己 spawn 出来的」chrome 进程及其子进程。
 *
 * 用法（在 Node 测试脚本里）：
 *   const { killChromeTree } = require('./kill-chrome-safe.cjs')
 *   const chrome = spawn(CHROME, [...args], { stdio: 'ignore' })
 *   ...
 *   killChromeTree(chrome.pid)   // 传 spawn 返回的 pid，不要按进程名匹配
 * =====================================================================
 */

const { execFileSync } = require('node:child_process')

/**
 * 终止指定 PID 的进程树（仅该进程及其后代）。
 * @param {number} rootPid spawn() 返回的 pid
 * @returns {{killed: number[], failed: number[]}}
 */
function killChromeTree(rootPid) {
  if (!rootPid) return { killed: [], failed: [] }

  const killed = []
  const failed = []

  // Windows: taskkill /T 递归终止子进程；/F 强制。只作用于给定 PID 树。
  try {
    execFileSync('taskkill', ['/PID', String(rootPid), '/T', '/F'], {
      stdio: 'ignore',
      windowsHide: true,
    })
    killed.push(rootPid)
  } catch {
    // 进程可能已自行退出，属正常情况
    failed.push(rootPid)
  }

  return { killed, failed }
}

/**
 * 按命令行特征精确筛选「本工具启动的无头实例」，供日志/断言使用。
 * 注意：仅用于**查询与核对**，不要拿它的结果去 Stop-Process 全部 chrome。
 * @returns {Array<{ProcessId:number, CommandLine:string}>}
 */
function listHeadlessChrome() {
  try {
    const out = execFileSync(
      'powershell',
      [
        '-NoProfile',
        '-Command',
        "Get-CimInstance Win32_Process -Filter \"Name='chrome.exe'\" | " +
          "Where-Object { $_.CommandLine -match 'headless' -and $_.CommandLine -match 'pinliao-' } | " +
          'Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress',
      ],
      { encoding: 'utf8', windowsHide: true },
    )
    const parsed = JSON.parse(out || '[]')
    return Array.isArray(parsed) ? parsed : [parsed]
  } catch {
    return []
  }
}

module.exports = { killChromeTree, listHeadlessChrome }
