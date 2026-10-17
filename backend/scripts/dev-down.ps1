# dev-down.ps1 — 一键停止本地开发依赖：MySQL + Redis
& "$PSScriptRoot\redis-stop.ps1"
& "$PSScriptRoot\db-stop.ps1"
