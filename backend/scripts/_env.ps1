# _env.ps1 — 本地运行环境路径与端口（供各脚本 dot-source 使用）
# 可用环境变量覆盖：PINLIAO_MYSQL_HOME / PINLIAO_REDIS_HOME
$ErrorActionPreference = 'Stop'

# 控制台中文输出兼容（Windows PowerShell 5.1 默认 GBK，避免脚本日志乱码）
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$BackendDir = Split-Path -Parent $PSScriptRoot          # backend\
$RepoDir    = Split-Path -Parent $BackendDir            # 仓库根
$LocalDir   = Join-Path $RepoDir '.local'               # 运行数据统一存放（已 gitignore）

# MySQL：复用本机 8.0.29 二进制，另起项目专用实例
$MySqlHome    = if ($env:PINLIAO_MYSQL_HOME) { $env:PINLIAO_MYSQL_HOME } else { 'C:\dev\mysql\mysql-8.0.29-winx64\mysql-8.0.29-winx64' }
$MySqlData    = Join-Path $LocalDir 'mysql\data'
$MySqlPort    = 3307
$MySqlRootPwd = '123456'

# Redis：Windows 版 zip 解压目录
$RedisHome = if ($env:PINLIAO_REDIS_HOME) { $env:PINLIAO_REDIS_HOME } else { Join-Path $LocalDir 'redis' }
$RedisData = Join-Path $LocalDir 'redis\data'
$RedisPort = 6379

function Test-TcpPort([string]$Target, [int]$Port) {
    # 快速探测端口是否可达（1.5s 超时）
    try {
        $client = New-Object Net.Sockets.TcpClient
        $client.ConnectAsync($Target, $Port).Wait(1500) | Out-Null
        return $client.Connected
    } catch {
        return $false
    } finally {
        if ($client) { $client.Close() }
    }
}

function Wait-TcpPort([string]$Target, [int]$Port, [int]$TimeoutSec = 30) {
    # 轮询等待端口就绪
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort $Target $Port) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-BackendUp([string]$Url, [int]$TimeoutSec = 180) {
    # 轮询 /actuator/health 直到包络里 status=UP
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            # PowerShell 7 在响应无 charset 时 Content 是 byte[]，需自行解码
            $body = if ($r.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($r.Content) } else { [string]$r.Content }
            if ($r.StatusCode -eq 200 -and $body -match '"status"\s*:\s*"UP"') { return $true }
        } catch { }
        Start-Sleep -Milliseconds 1500
    }
    return $false
}

function Get-JavaMajor([string]$JavaExe) {
    # 注意（Windows PowerShell 5.1）：`java -version` 输出走 stderr，在 $ErrorActionPreference='Stop'
    # 下 `2>&1` 会抛终止性 NativeCommandError 并被下面的 catch 吞掉 → 恒返回 0（PS 7 不触发）。
    # 此处局部降级为 Continue，保证 5.1 下也能正确识别 major。
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $JavaExe -version 2>&1 | Out-String
        if ($out -match 'version "(\d+)(\.(\d+))?') {
            $major = [int]$Matches[1]
            if ($major -eq 1 -and $Matches[3]) { return [int]$Matches[3] }   # 1.8 -> 8
            return $major
        }
    } catch { } finally { $ErrorActionPreference = $prevEap }
    return 0
}

function Resolve-PinliaoJavaHome([string]$LogPrefix = '[java]') {
    # 依次尝试 PINLIAO_JAVA_HOME → JetBrains 自带 JBR → 系统 JAVA_HOME，返回第一个 major≥17 的目录
    # （本机系统 JAVA_HOME 指向 JDK 1.8，不可用于编译后端）
    $candidates = @($env:PINLIAO_JAVA_HOME)
    $jbrDirs = Get-ChildItem 'C:\Program Files\JetBrains' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'jbr' } | Where-Object { Test-Path $_ }
    if ($jbrDirs) { $candidates += $jbrDirs }
    $candidates += @($env:JAVA_HOME)

    foreach ($cand in ($candidates | Where-Object { $_ })) {
        $javaExe = Join-Path $cand 'bin\java.exe'
        if (-not (Test-Path $javaExe)) { continue }
        $major = Get-JavaMajor $javaExe
        Write-Host "$LogPrefix 探测 JDK：$cand（major=$major）"
        if ($major -ge 17) { return $cand }
    }
    return $null
}

# ---- 可观测性（T-OBS）：阿里云 ARMS Java 探针（应用监控）----
# 设计见 docs/arms-plan.md；凭据只走用户级环境变量，仓库零命中。
# 默认关闭：不设 PINLIAO_ARMS_ENABLED=true 时，一切启动方式与未接入 ARMS 完全一致。
$ArmsEnabled    = ($env:PINLIAO_ARMS_ENABLED -eq 'true')
$ArmsLicenseKey = $env:PINLIAO_ARMS_LICENSE_KEY
$ArmsAppName    = if ($env:PINLIAO_ARMS_APP_NAME) { $env:PINLIAO_ARMS_APP_NAME } else { 'pinliao-backend' }
$ArmsAgentHome  = if ($env:PINLIAO_ARMS_AGENT_HOME) { $env:PINLIAO_ARMS_AGENT_HOME } else { Join-Path $LocalDir 'arms\AliyunJavaAgent' }
$ArmsAgentJar   = Join-Path $ArmsAgentHome 'aliyun-java-agent.jar'

function Format-Secret([string]$Value) {
    # 日志打码：只露前 4 位 + 长度，避免探针 LicenseKey / API Key 进日志或截图
    if (-not $Value) { return '(未设置)' }
    $head = if ($Value.Length -le 4) { '***' } else { $Value.Substring(0, 4) + '***' }
    return "$head($($Value.Length) 位)"
}
