# db-reset.ps1 —— T9「清库重迁」：DROP + CREATE pinliao，重启后端让 Flyway 重跑 V1~V8
# ---------------------------------------------------------------------------
# 为什么需要：演示库里会留下冒烟 / e2e 的写入（app-new-*、job-new-*、u-reg-*、
# 固定手机号 13900004321 的注册账号 …），不重置就会让 e2e 的计数与「注册必成」断言
# 与 mock 基线不一致（见 docs/AI-CONTINUE.md §6.17 / PROGRESS §6.3）。
#
# 用法（前置：dev-up.ps1 已起 MySQL 3307 + Redis 6379）：
#   .\scripts\db-reset.ps1                 # 停后端(8080) → 清库 → FLUSHALL Redis → 起后端 → 等 health UP
#   .\scripts\db-reset.ps1 -NoRestart      # 只清库 + FLUSHALL Redis，不重启后端
#   .\scripts\db-reset.ps1 -KeepRedis      # 保留 Redis（默认 FLUSHALL，为了清掉验证码 60s 冷却）
#
# 注意：只按「8080 端口」定位并结束后端 PID，严禁按进程名杀 java（AGENTS §2.9）。
# ---------------------------------------------------------------------------
param(
    [switch]$NoRestart,
    [switch]$KeepRedis
)

. "$PSScriptRoot\_env.ps1"

$dbName    = 'pinliao'
$backendHost = '127.0.0.1'
$backendPort = 8080
$healthUrl   = "http://${backendHost}:${backendPort}/actuator/health"

$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
if (-not (Test-Path $mysql)) { throw "未找到 mysql.exe：$mysql（可用 PINLIAO_MYSQL_HOME 指定）" }

function Get-ListenPids([int]$Port) {
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) { return @() }
    return @($conns | Select-Object -ExpandProperty OwningProcess -Unique)
}

function Wait-BackendDown([int]$Port, [int]$TimeoutSec) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-TcpPort '127.0.0.1' $Port)) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-BackendUp([string]$Url, [int]$TimeoutSec) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            # PowerShell 7 在无 charset 时 Content 可能是 byte[]，需自行解码
            $body = if ($r.Content -is [byte[]]) { [Text.Encoding]::UTF8.GetString($r.Content) } else { [string]$r.Content }
            if ($r.StatusCode -eq 200 -and $body -match '"status"\s*:\s*"UP"') { return $true }
        } catch { }
        Start-Sleep -Milliseconds 1500
    }
    return $false
}

function Invoke-RedisFlushAll([string]$Target, [int]$Port) {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $client.Connect($Target, $Port)
        $stream = $client.GetStream()
        # RESP: *1\r\n$8\r\nFLUSHALL\r\n
        $bytes = [Text.Encoding]::ASCII.GetBytes("*1`r`n`$8`r`nFLUSHALL`r`n")
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush()
        Start-Sleep -Milliseconds 200
        $buf = New-Object byte[] 128
        $n = $stream.Read($buf, 0, $buf.Length)
        return [Text.Encoding]::ASCII.GetString($buf, 0, $n).Trim()
    } finally {
        $client.Close()
    }
}

# ---------- 1) 前置检查 ----------
if (-not (Test-TcpPort '127.0.0.1' $MySqlPort)) {
    throw "MySQL 未启动（127.0.0.1:$MySqlPort），先执行 .\scripts\dev-up.ps1"
}

# ---------- 2) 停后端（按端口 8080 精确定位） ----------
if (-not $NoRestart) {
    $backendPids = Get-ListenPids $backendPort
    if ($backendPids.Count -gt 0) {
        Write-Host "[db-reset] 停止端口 $backendPort 上的后端进程：$($backendPids -join ', ')"
        foreach ($procId in $backendPids) { Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue }
        if (-not (Wait-BackendDown $backendPort 30)) {
            throw "端口 $backendPort 在 30s 内未释放，请手工确认后重试"
        }
    } else {
        Write-Host "[db-reset] 端口 $backendPort 上没有后端在跑（跳过停止）"
    }
}

# ---------- 3) 清库重迁的「清库」 ----------
Write-Host "[db-reset] DROP + CREATE 数据库 $dbName ..."
& $mysql --host=127.0.0.1 --port=$MySqlPort --user=root "--password=$MySqlRootPwd" -e `
    "DROP DATABASE IF EXISTS $dbName; CREATE DATABASE $dbName DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
if ($LASTEXITCODE -ne 0) { throw "清库失败（mysql 退出码 $LASTEXITCODE）" }

# ---------- 4) 清 Redis（验证码冷却 / refresh token / 限流计数） ----------
if ($KeepRedis) {
    Write-Host "[db-reset] 按 -KeepRedis 保留 Redis 数据"
} else {
    if (Test-TcpPort '127.0.0.1' $RedisPort) {
        $reply = Invoke-RedisFlushAll '127.0.0.1' $RedisPort
        Write-Host "[db-reset] Redis FLUSHALL -> $reply"
    } else {
        Write-Host "[db-reset] Redis 未启动（127.0.0.1:$RedisPort），跳过 FLUSHALL"
    }
}

# ---------- 5) 重启后端（Flyway 会在启动时重跑 V1~V8） ----------
if ($NoRestart) {
    Write-Host "[db-reset] 库已清空。请自行重启后端（.\mvnw.cmd spring-boot:run）以让 Flyway 重新迁移。" -ForegroundColor Yellow
    exit 0
}

# 本机系统 PATH 上的 JAVA_HOME 是 1.8，必须显式挑一个 ≥17 的 JDK（否则 maven-shade-plugin 会炸）
function Get-JavaMajor([string]$JavaExe) {
    # Windows PowerShell 5.1 注意：`java -version` 输出走 stderr，在 $ErrorActionPreference='Stop'
    # （由 _env.ps1 设置）下 `2>&1` 会抛终止性 NativeCommandError 并被下面的 catch 吞掉 → 恒返回 0，
    # 导致脚本误报「未找到 JDK 17+」（PS 7 不触发）。此处局部降级为 Continue。
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

$javaCandidates = @($env:PINLIAO_JAVA_HOME)
$jbrDirs = Get-ChildItem 'C:\Program Files\JetBrains' -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Join-Path $_.FullName 'jbr' } | Where-Object { Test-Path $_ }
if ($jbrDirs) { $javaCandidates += $jbrDirs }
$javaCandidates += @($env:JAVA_HOME)

$javaHome = $null
foreach ($cand in ($javaCandidates | Where-Object { $_ })) {
    $javaExe = Join-Path $cand 'bin\java.exe'
    if (-not (Test-Path $javaExe)) { continue }
    $major = Get-JavaMajor $javaExe
    Write-Host "[db-reset] 探测 JDK：$cand（major=$major）"
    if ($major -ge 17) { $javaHome = $cand; break }
}
if (-not $javaHome) {
    throw "未找到 JDK 17+。请设 PINLIAO_JAVA_HOME 指向 JDK（本机系统 JAVA_HOME=D:\JDK1.8 不可用，需用 IDEA 自带 JBR）"
}
$env:JAVA_HOME = $javaHome
Write-Host "[db-reset] JAVA_HOME=$javaHome"

$logFile = Join-Path $LocalDir 'backend-dev.log'
$errFile = Join-Path $LocalDir 'backend-dev.err.log'
Write-Host "[db-reset] 启动后端：.\mvnw.cmd spring-boot:run（日志 $logFile）"
Start-Process -FilePath (Join-Path $BackendDir 'mvnw.cmd') -ArgumentList 'spring-boot:run' `
    -WorkingDirectory $BackendDir -WindowStyle Hidden `
    -RedirectStandardOutput $logFile -RedirectStandardError $errFile

Write-Host "[db-reset] 等待 $healthUrl 变为 UP（最多 180s，含 Maven 启动 + Flyway V1~V8）..."
if (-not (Wait-BackendUp $healthUrl 180)) {
    throw "后端未在 180s 内就绪，请看 $logFile / $errFile"
}

# ---------- 6) 回读 Flyway 版本与关键行数（应与 verify-seed 基线一致） ----------
$probe = @"
SELECT CONCAT('flyway=', MAX(version)) FROM ${dbName}.flyway_schema_history;
SELECT CONCAT('companies=', COUNT(*)) FROM ${dbName}.companies;
SELECT CONCAT('jobs=', COUNT(*)) FROM ${dbName}.jobs;
SELECT CONCAT('applications=', COUNT(*)) FROM ${dbName}.applications;
"@
Write-Host "[db-reset] 迁移后基线："
& $mysql --host=127.0.0.1 --port=$MySqlPort --user=root "--password=$MySqlRootPwd" -N -e $probe

Write-Host "[db-reset] 完成：库已重置、Flyway 已重迁、后端已就绪。" -ForegroundColor Green
