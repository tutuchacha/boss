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
