# fetch-arms-agent.ps1 —— T-OBS1：下载阿里云 ARMS Java 探针（应用监控）
# ---------------------------------------------------------------------------
# 为什么用阿里云自研探针（路径 B）而不是开源 OpenTelemetry Agent（路径 A）：
#   两者都能被 ARMS 接收，但落在控制台的不同产品里 —— A 进「可观测链路 OpenTelemetry 版」
#   （语言列显示 `-`，只有调用链/大盘/Trace Explorer），B 进「应用监控」
#   （语言列显示 Java 图标，含慢 SQL、异常分析、JVM/线程、接口大盘、拓扑）。
#   本项目的目标就是后者的完整体验，见 docs/arms-plan.md §1。
#   另：官方文档写明「ARMS Java 探针从 4.x 起基于 opentelemetry-java-instrumentation 全面重构」，
#   所以 B 与 common/web/TraceIdFilter 里的 OTel `Span.current()` 打通方式完全兼容。
#
# 产物（均已 gitignore）：.local\arms\AliyunJavaAgent\aliyun-java-agent.jar
#
# 用法：
#   .\scripts\fetch-arms-agent.ps1                       # 华东1-杭州公网（默认）
#   .\scripts\fetch-arms-agent.ps1 -Region cn-beijing    # 换地域
#   .\scripts\fetch-arms-agent.ps1 -ZipPath D:\tmp\AliyunJavaAgent.zip   # 用控制台下载的包，跳过网络
#   .\scripts\fetch-arms-agent.ps1 -Force                # 已存在也重新下载
#
# 注意：~51 MB，首次下载视网络 1~3 分钟。
# ---------------------------------------------------------------------------
param(
    [string]$Region = 'cn-hangzhou',
    [string]$ZipPath,
    [switch]$Force,
    [switch]$KeepZip
)

. "$PSScriptRoot\_env.ps1"

$dest   = Join-Path $LocalDir 'arms'
$zip    = Join-Path $dest 'AliyunJavaAgent.zip'
$jar    = $ArmsAgentJar
$verTag = Join-Path $ArmsAgentHome 'version'

if ((Test-Path $jar) -and -not $Force -and -not $ZipPath) {
    $ver = if (Test-Path $verTag) { (Get-Content $verTag -TotalCount 1).Trim() } else { '(版本未知)' }
    Write-Host "[arms] 探针已存在，跳过下载：$jar" -ForegroundColor Green
    Write-Host "[arms] 版本：$ver   大小：$([math]::Round((Get-Item $jar).Length / 1MB, 1)) MB"
    Write-Host "[arms] 需要重新下载请加 -Force"
    exit 0
}

New-Item -ItemType Directory -Force -Path $dest | Out-Null

try {
    if ($ZipPath) {
        if (-not (Test-Path $ZipPath)) { throw "指定的 zip 不存在：$ZipPath" }
        Write-Host "[arms] 使用本地探针包：$ZipPath"
        Copy-Item $ZipPath $zip -Force
    } else {
        # OSS 公网地址随地域变化；例：杭州 arms-apm-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com
        $hostPart = "arms-apm-$Region.oss-$Region.aliyuncs.com"
        $urls = @("https://$hostPart/AliyunJavaAgent.zip", "http://$hostPart/AliyunJavaAgent.zip")
        $ok = $false
        foreach ($url in $urls) {
            Write-Host "[arms] 下载探针包（地域 $Region）：$url"
            try {
                # 大文件下载时进度条会拖慢 PowerShell 5.1 数倍，临时关掉
                $prev = $ProgressPreference; $ProgressPreference = 'SilentlyContinue'
                Invoke-WebRequest -Uri $url -OutFile $zip -TimeoutSec 900
                $ProgressPreference = $prev
                $ok = $true
                break
            } catch {
                Write-Host "[arms]   失败：$($_.Exception.Message)" -ForegroundColor Yellow
            }
        }
        if (-not $ok) {
            throw "下载失败。请检查地域（当前 $Region）与出网；或在控制台「应用监控 → 接入中心」下载包后用 -ZipPath 指定"
        }
        Write-Host "[arms] 下载完成：$([math]::Round((Get-Item $zip).Length / 1MB, 1)) MB"
    }

    Write-Host "[arms] 解压到 $dest ..."
    Expand-Archive -Path $zip -DestinationPath $dest -Force

    if (-not (Test-Path $jar)) { throw "解压后未找到探针 jar：$jar" }
    $ver = if (Test-Path $verTag) { (Get-Content $verTag -TotalCount 1).Trim() } else { '(版本未知)' }
    Write-Host "[arms] 探针就绪：$jar" -ForegroundColor Green
    Write-Host "[arms] 版本：$ver   大小：$([math]::Round((Get-Item $jar).Length / 1MB, 1)) MB"
} finally {
    $isUserZip = $ZipPath -and ((Resolve-Path $ZipPath).Path -eq (Resolve-Path $zip -ErrorAction SilentlyContinue).Path)
    if (-not $KeepZip -and -not $isUserZip -and (Test-Path $zip) -and (Test-Path $jar)) {
        Remove-Item $zip -Force
        Write-Host "[arms] 已删除 zip（保留 zip 请加 -KeepZip）"
    }
}

Write-Host ''
Write-Host '下一步（T-OBS0，需在阿里云控制台操作）：' -ForegroundColor Cyan
Write-Host '  1) ARMS 控制台 → 应用监控 → 应用列表 → 创建应用（无侵入接入 / Java）→ 复制 LicenseKey'
Write-Host '  2) 写入用户级环境变量（绝不要提交到仓库）：'
Write-Host '       setx PINLIAO_ARMS_ENABLED true'
Write-Host '       setx PINLIAO_ARMS_LICENSE_KEY "<控制台 LicenseKey>"'
Write-Host '       setx PINLIAO_ARMS_APP_NAME    "pinliao-backend"'
Write-Host '  3) 新开一个终端（setx 只对新进程生效）→ .\scripts\dev-with-arms.ps1 -DryRun 自检'
Write-Host '  4) .\scripts\dev-with-arms.ps1 启动后端，约 1 分钟后在控制台「应用列表」看到应用'
