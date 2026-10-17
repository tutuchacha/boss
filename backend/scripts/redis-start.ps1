# redis-start.ps1 — 启动本地 Redis（端口 6379，数据在 .local\redis）
. "$PSScriptRoot\_env.ps1"

$redisServer = Join-Path $RedisHome 'redis-server.exe'
if (-not (Test-Path $redisServer)) { throw "未找到 Redis：$redisServer（可用 PINLIAO_REDIS_HOME 指定；下载见 backend\README.md）" }
if (Test-TcpPort '127.0.0.1' $RedisPort) {
    Write-Host "[redis-start] Redis 已在运行（127.0.0.1:$RedisPort）"
    exit 0
}

New-Item -ItemType Directory -Force -Path $RedisData | Out-Null
$argList = @(
    '--port', "$RedisPort",
    '--bind', '127.0.0.1',
    '--dir', $RedisData,
    '--appendonly', 'yes',
    '--logfile', "$LocalDir\redis\redis.log"
)
Start-Process $redisServer -ArgumentList $argList -WindowStyle Hidden | Out-Null
if (Wait-TcpPort '127.0.0.1' $RedisPort 20) {
    Write-Host "[redis-start] Redis 已启动（127.0.0.1:$RedisPort，数据目录 $RedisData）"
} else {
    throw "启动超时，详见 $LocalDir\redis\redis.log"
}
