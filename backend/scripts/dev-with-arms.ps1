# dev-with-arms.ps1 —— T-OBS1：带阿里云 ARMS Java 探针启动后端（应用监控链路）
# ---------------------------------------------------------------------------
# 做的事情：只在 spring-boot:run fork 出来的【应用 JVM】上挂 -javaagent，
#   mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-javaagent:<jar> -Darms.appName=<name> -Darms.licenseKey=<key>"
# 为什么不用 JAVA_TOOL_OPTIONS：那样 Maven 自身也会挂上探针 → 同机出现第二个「同名应用」的数据，
#   控制台里分不清哪条是本项目后端，也会双份上报（费钱）。
#
# 开关与凭据全走用户级环境变量（见 _env.ps1 / docs/arms-plan.md §3）：
#   PINLIAO_ARMS_ENABLED       true 才挂探针（默认关闭 → 行为与未接 ARMS 完全一致）
#   PINLIAO_ARMS_LICENSE_KEY   控制台「应用监控 → 应用列表 → 创建应用」拿到的 LicenseKey（密钥，勿进仓库）
#   PINLIAO_ARMS_APP_NAME      默认 pinliao-backend
#   PINLIAO_ARMS_AGENT_HOME    默认 .local\arms\AliyunJavaAgent
#
# 用法：
#   .\scripts\fetch-arms-agent.ps1            # 先下载探针
#   .\scripts\dev-with-arms.ps1 -DryRun       # 只做配置自检（打码），不启动
#   .\scripts\dev-with-arms.ps1               # 前台启动（日志直接刷屏，Ctrl+C 停）
#   .\scripts\dev-with-arms.ps1 -Background   # 后台启动 + 日志写 .local\backend-arms.log + 等 health UP
#   .\scripts\dev-with-arms.ps1 -AllowMissingLicenseKey   # 仅验证探针能加载（不上报数据）
#
# 收工：.\scripts\db-reset.ps1（不带探针，恢复干净环境）或按端口 8080 定位 PID 后停止。
# ---------------------------------------------------------------------------
param(
    [switch]$DryRun,
    [switch]$Background,
    [switch]$AllowMissingLicenseKey
)

. "$PSScriptRoot\_env.ps1"

$backendPort = 8080
$healthUrl   = "http://127.0.0.1:$backendPort/actuator/health"
$mvnw        = Join-Path $BackendDir 'mvnw.cmd'
$logFile     = Join-Path $LocalDir 'backend-arms.log'
$errFile     = Join-Path $LocalDir 'backend-arms.err.log'

$problems = @()
if (-not $ArmsEnabled) {
    $problems += "PINLIAO_ARMS_ENABLED 不是 true（当前：$(if ($env:PINLIAO_ARMS_ENABLED) { $env:PINLIAO_ARMS_ENABLED } else { '未设置' })）"
}
if (-not (Test-Path $ArmsAgentJar)) {
    $problems += "探针 jar 不存在：$ArmsAgentJar（先跑 .\scripts\fetch-arms-agent.ps1）"
}
if (-not $ArmsLicenseKey -and -not $AllowMissingLicenseKey) {
    $problems += "缺 PINLIAO_ARMS_LICENSE_KEY（ARMS 控制台 → 应用监控 → 应用列表 → 创建应用；只想验证探针加载可加 -AllowMissingLicenseKey）"
}

Write-Host '[arms] 配置自检：'
Write-Host "  enabled     : $ArmsEnabled"
Write-Host "  appName     : $ArmsAppName"
Write-Host "  agent jar   : $ArmsAgentJar$(if (Test-Path $ArmsAgentJar) { "（$([math]::Round((Get-Item $ArmsAgentJar).Length / 1MB, 1)) MB）" })"
Write-Host "  licenseKey  : $(Format-Secret $ArmsLicenseKey)"
if (Test-Path $ArmsAgentJar) {
    $verTag = Join-Path $ArmsAgentHome 'version'
    if (Test-Path $verTag) { Write-Host "  agent 版本  : $((Get-Content $verTag -TotalCount 1).Trim())" }
}

if ($problems.Count -gt 0) {
    Write-Host ''
    foreach ($p in $problems) { Write-Host "  ✗ $p" -ForegroundColor Red }
    Write-Host ''
    Write-Host '详细步骤见 backend\README.md「ARMS 链路（可选）」与 docs\arms-plan.md §7' -ForegroundColor Yellow
    exit 1
}

# 组 jvmArguments：Spring Boot Maven 插件按空格切分该字符串，故路径含空格会歧义 —— 本仓库路径无空格，
# 但仍显式提示，避免换目录后出现「-javaagent 参数只被吃了一半」的怪问题。
if ($ArmsAgentJar -match '\s') {
    Write-Host "  ✗ 探针路径含空格，spring-boot:run 的 jvmArguments 无法解析：$ArmsAgentJar" -ForegroundColor Red
    Write-Host '    请把仓库放到无空格路径，或用 PINLIAO_ARMS_AGENT_HOME 指向无空格目录。' -ForegroundColor Yellow
    exit 1
}

$jvmArgs = @("-javaagent:$ArmsAgentJar", "-Darms.appName=$ArmsAppName")
if ($ArmsLicenseKey) {
    $jvmArgs += "-Darms.licenseKey=$ArmsLicenseKey"
} else {
    Write-Host ''
    Write-Host '  ⚠ 未提供 LicenseKey：探针会加载但不上报数据，仅用于验证 agent 能挂上（-AllowMissingLicenseKey）。' -ForegroundColor Yellow
}
$jvmArgs = $jvmArgs -join ' '

# 日志里给 LicenseKey 打码（注意：空串不能进 -replace 的正则位，否则会在每个字符间插入替换文本）
$maskedArgs = if ($ArmsLicenseKey) { $jvmArgs -replace [regex]::Escape($ArmsLicenseKey), (Format-Secret $ArmsLicenseKey) } else { $jvmArgs }
Write-Host "  启动命令    : mvnw.cmd spring-boot:run `"-Dspring-boot.run.jvmArguments=$maskedArgs`""

if ($DryRun) {
    Write-Host ''
    Write-Host '[arms] DryRun：配置自检通过，未启动后端。' -ForegroundColor Green
    exit 0
}

# 端口占用检查：db-reset.ps1 起的后端会占着 8080，直接再起会失败且报错难懂
$occupied = @(Get-NetTCPConnection -LocalPort $backendPort -State Listen -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty OwningProcess -Unique)
if ($occupied.Count -gt 0) {
    Write-Host ''
    Write-Host "  ✗ 端口 $backendPort 已被占用（PID $($occupied -join ', ')）。先停止它（或跑 .\scripts\db-reset.ps1 会自动重启为无探针版）。" -ForegroundColor Red
    exit 1
}

$javaHome = Resolve-PinliaoJavaHome -LogPrefix '[arms]'
if (-not $javaHome) {
    Write-Host '  ✗ 未找到 JDK 17+（可用 PINLIAO_JAVA_HOME 指定）' -ForegroundColor Red
    exit 1
}
$env:JAVA_HOME = $javaHome
Write-Host "[arms] JAVA_HOME=$javaHome"

# 关键坑：-ArgumentList 传数组时 Start-Process 只用空格拼接、不会给含空格的参数补引号，
#   "-Dspring-boot.run.jvmArguments=-javaagent:... -Darms.licenseKey=..." 会被 Maven 拆成
#   jvmArguments 只剩 -javaagent、其余 -Darms.* 变成 Maven 自己的 -D 参数。
#   结果：探针加载了但拿不到 LicenseKey/appName → 不产生 span、控制台看不到应用。
#   故这里显式拼成【单个带引号的参数字符串】再交给 Start-Process。
$jvmArgLine = "-Dspring-boot.run.jvmArguments=`"$jvmArgs`""

if ($Background) {
    Write-Host "[arms] 后台启动，日志：$logFile"
    Start-Process -FilePath $mvnw -ArgumentList "spring-boot:run $jvmArgLine" `
        -WorkingDirectory $BackendDir -WindowStyle Hidden `
        -RedirectStandardOutput $logFile -RedirectStandardError $errFile

    Write-Host "[arms] 等待 $healthUrl 变为 UP（最多 180s）..."
    if (-not (Wait-BackendUp $healthUrl 180)) {
        Write-Host "  ✗ 后端未在 180s 内就绪，请看 $logFile / $errFile" -ForegroundColor Red
        exit 1
    }
    Write-Host '[arms] 后端已就绪（探针已在应用 JVM 内）。' -ForegroundColor Green
    Write-Host "[arms] 验证探针：Select-String -Path $logFile -Pattern 'ARMS|agent' | Select-Object -First 10"
    Write-Host "[arms] 停止：停止端口 $backendPort 上的进程，或 .\scripts\db-reset.ps1"
} else {
    Write-Host '[arms] 前台启动（Ctrl+C 停止）...'
    & $mvnw spring-boot:run "-Dspring-boot.run.jvmArguments=$jvmArgs"
}
