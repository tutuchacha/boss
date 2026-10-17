# db-start.ps1 — 启动项目专用 MySQL 实例（端口 3307，后台运行）
. "$PSScriptRoot\_env.ps1"

$mysqld = Join-Path $MySqlHome 'bin\mysqld.exe'
if (-not (Test-Path (Join-Path $MySqlData 'mysql'))) { throw "尚未初始化，请先运行 scripts\db-init.ps1" }
if (Test-TcpPort '127.0.0.1' $MySqlPort) {
    Write-Host "[db-start] MySQL 已在运行（127.0.0.1:$MySqlPort）"
    exit 0
}

$argList = @(
    "--datadir=$MySqlData",
    "--port=$MySqlPort",
    "--bind-address=127.0.0.1",
    "--mysqlx=OFF",
    "--character-set-server=utf8mb4",
    "--collation-server=utf8mb4_0900_ai_ci",
    "--log-error=$LocalDir\mysql\mysqld.log",
    "--pid-file=$LocalDir\mysql\mysqld.pid"
)
Start-Process $mysqld -ArgumentList $argList -WindowStyle Hidden | Out-Null
if (Wait-TcpPort '127.0.0.1' $MySqlPort 60) {
    Write-Host "[db-start] MySQL 已启动（127.0.0.1:$MySqlPort）"
} else {
    throw "启动超时，详见 $LocalDir\mysql\mysqld.log"
}
