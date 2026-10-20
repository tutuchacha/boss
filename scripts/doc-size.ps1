#Requires -Version 5.1
<#
  doc-size.ps1 —— 文档体量守卫

  背景：开发文档只增不减时，AI 的「启动必读」会吃掉大半上下文，导致反复压缩
  （2026-10-20 实测：改造前启动必读 197k 字符 ≈ 123k tokens = 200k 窗口的 62%）。

  用法：
    ./scripts/doc-size.ps1            # 体检；超软上限只告警
    ./scripts/doc-size.ps1 -Strict    # 超硬上限时退出码 1（提交前 / CI 用）

  规则见 docs/INDEX.md §3；超限处理：按阶段切分 → docs/archive/ → 补索引行。
#>
[CmdletBinding()]
param(
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$utf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $utf8

$rules = @(
    @{ Path = 'AGENTS.md';            Soft = 9KB;  Hard = 12KB;  Tier = '必读' }
    @{ Path = 'PROGRESS.md';          Soft = 16KB; Hard = 22KB;  Tier = '必读' }
    @{ Path = 'docs/INDEX.md';        Soft = 6KB;  Hard = 10KB;  Tier = '必读' }
    @{ Path = 'docs/AI-CONTINUE.md';  Soft = 22KB; Hard = 32KB;  Tier = '按需' }
    @{ Path = 'ARCHITECTURE.md';      Soft = 22KB; Hard = 32KB;  Tier = '按需' }
    @{ Path = 'worklog.md';           Soft = 16KB; Hard = 24KB;  Tier = '日志' }
    @{ Path = 'CHANGELOG.md';         Soft = 24KB; Hard = 32KB;  Tier = '日志' }
    @{ Path = 'docs/state/*.md';      Soft = 24KB; Hard = 36KB;  Tier = '专题' }
    @{ Path = 'docs/pitfalls/*.md';   Soft = 20KB; Hard = 32KB;  Tier = '专题' }
)

function Format-KB([long]$bytes) {
    if ($bytes -ge 1MB) { return ('{0:N1} MB' -f ($bytes / 1MB)) }
    return ('{0:N1} KB' -f ($bytes / 1KB))
}

$rows = New-Object System.Collections.Generic.List[object]
$hardFails = 0
$softWarns = 0

foreach ($rule in $rules) {
    $pattern = Join-Path $root $rule.Path
    $files = @(Get-ChildItem -Path $pattern -File -ErrorAction SilentlyContinue)
    if ($files.Count -eq 0) {
        $rows.Add([pscustomobject]@{ Tier = $rule.Tier; File = $rule.Path; Size = '-'; Soft = (Format-KB $rule.Soft); State = '缺失' })
        continue
    }
    foreach ($f in $files) {
        $len = $f.Length
        $state = 'OK'
        if ($len -gt $rule.Hard) { $state = '超硬上限'; $hardFails++ }
        elseif ($len -gt $rule.Soft) { $state = '超软上限'; $softWarns++ }
        $rows.Add([pscustomobject]@{
            Tier  = $rule.Tier
            File  = $f.FullName.Substring($root.Length).TrimStart('\', '/')
            Size  = Format-KB $len
            Soft  = Format-KB $rule.Soft
            State = $state
        })
    }
}

$rows | Format-Table -AutoSize | Out-String -Width 200 | Write-Host

# 归档：只统计总量，不设上限（但不能把归档内容回填主文件）
$arc = Join-Path $root 'docs/archive'
if (Test-Path $arc) {
    $arcBytes = (Get-ChildItem $arc -Filter *.md -File | Measure-Object -Property Length -Sum).Sum
    "归档 docs/archive/：{0} 个文件，共 {1}（默认不读，仅追溯）" -f (Get-ChildItem $arc -Filter *.md -File).Count, (Format-KB $arcBytes)
}

# 汇总
$main = @('AGENTS.md', 'PROGRESS.md', 'docs/INDEX.md') | ForEach-Object {
    $p = Join-Path $root $_
    if (Test-Path $p) { (Get-Item $p).Length } else { 0 }
}
$mainSum = ($main | Measure-Object -Sum).Sum
"启动必读合计：{0}（约 {1:N1}k tokens，占 200k 窗口 {2:N1}%）" -f (Format-KB $mainSum), ($mainSum / 1.6 / 1000), ($mainSum / 1.6 / 2000)

if ($hardFails -gt 0) {
    Write-Host ("`n[X] {0} 个文件超硬上限；{1} 个超软上限。" -f $hardFails, $softWarns) -ForegroundColor Red
    if ($Strict) { exit 1 }
}
elseif ($softWarns -gt 0) {
    Write-Host ("`n[!] {0} 个文件超软上限（可接受，但下次改动前建议归档）。" -f $softWarns) -ForegroundColor Yellow
}
else {
    Write-Host "`n[OK] 全部在软上限内。" -ForegroundColor Green
}
