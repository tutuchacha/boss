# db-stop.ps1 — 停止项目专用 MySQL 实例
. "$PSScriptRoot\_env.ps1"

if (-not (Test-TcpPort '127.0.0.1' $MySqlPort)) {
    Write-Host "[db-stop] MySQL 未在运行"
    exit 0
}
$mysqladmin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
& $mysqladmin -h 127.0.0.1 -P $MySqlPort -u root "-p$MySqlRootPwd" shutdown
Write-Host "[db-stop] MySQL 已停止"
