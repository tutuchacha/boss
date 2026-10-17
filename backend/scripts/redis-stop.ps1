# redis-stop.ps1 — 停止本地 Redis
. "$PSScriptRoot\_env.ps1"

if (-not (Test-TcpPort '127.0.0.1' $RedisPort)) {
    Write-Host "[redis-stop] Redis 未在运行"
    exit 0
}
$redisCli = Join-Path $RedisHome 'redis-cli.exe'
& $redisCli -h 127.0.0.1 -p $RedisPort shutdown
Write-Host "[redis-stop] Redis 已停止"
