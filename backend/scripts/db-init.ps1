# db-init.ps1 — 首次初始化「项目专用 MySQL 实例」
# 端口 3307，数据目录 <仓库>\.local\mysql\data；不影响本机原有 MySQL 服务
. "$PSScriptRoot\_env.ps1"

$mysqld     = Join-Path $MySqlHome 'bin\mysqld.exe'
$mysql      = Join-Path $MySqlHome 'bin\mysql.exe'
$mysqladmin = Join-Path $MySqlHome 'bin\mysqladmin.exe'
if (-not (Test-Path $mysqld)) { throw "未找到 MySQL：$mysqld（可用 PINLIAO_MYSQL_HOME 指定二进制目录）" }

if (Test-Path (Join-Path $MySqlData 'mysql')) {
    Write-Host "[db-init] 数据目录已存在，跳过初始化：$MySqlData"
    exit 0
}
if (Test-TcpPort '127.0.0.1' $MySqlPort) { throw "端口 $MySqlPort 已被占用，请先释放后再初始化" }

New-Item -ItemType Directory -Force -Path $MySqlData | Out-Null
Write-Host "[db-init] 初始化数据目录：$MySqlData"
& $mysqld --initialize-insecure "--datadir=$MySqlData" "--log-error=$LocalDir\mysql\init.log"
if ($LASTEXITCODE -ne 0) { throw "初始化失败，详见 $LocalDir\mysql\init.log" }

Write-Host "[db-init] 临时启动实例：设置 root 密码并创建库 pinliao ..."
$argList = @(
    "--datadir=$MySqlData",
    "--port=$MySqlPort",
    "--bind-address=127.0.0.1",
    "--mysqlx=OFF",
    "--log-error=$LocalDir\mysql\mysqld.log"
)
Start-Process $mysqld -ArgumentList $argList -WindowStyle Hidden | Out-Null
if (-not (Wait-TcpPort '127.0.0.1' $MySqlPort 60)) { throw "MySQL 启动超时，详见 $LocalDir\mysql\mysqld.log" }

& $mysql -h 127.0.0.1 -P $MySqlPort -u root --password= -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$MySqlRootPwd'; CREATE DATABASE IF NOT EXISTS pinliao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
if ($LASTEXITCODE -ne 0) { throw "设置 root 密码 / 建库失败" }

& $mysqladmin -h 127.0.0.1 -P $MySqlPort -u root "-p$MySqlRootPwd" shutdown
Write-Host "[db-init] 完成：库 pinliao 就绪（127.0.0.1:$MySqlPort，root / $MySqlRootPwd）"
