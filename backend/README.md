# 聘聊 · 后端服务（pinliao-backend）

Step 3 后端：实现 `docs/api-endpoints.md` 正文契约（70 条）与 `docs/api-contract.md` 通用规范
（统一包络 / 错误码 / traceId / 时间格式）。技术栈：Spring Boot 3.5 + MySQL 8 + Redis + MyBatis-Plus。

## 环境要求（本机现状，2026-10-17 定）

| 项 | 定 |
|---|---|
| JDK | 用 IDEA 自带 JBR 21（完整 JDK，含 javac）：`C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr` |
| Maven | 自带 wrapper（`.mvn/wrapper/maven-wrapper.properties` → Maven 3.9.16），无需本机 Maven |
| MySQL | 项目专用实例（复用本机 8.0.29 二进制 + 独立数据目录）：端口 **3307**，数据在仓库 `..\.local\mysql\data` |
| Redis | `..\.local\redis`（Windows 版 zip），端口 6379 |
| 运行数据 | 仓库根 `.local\`（已 gitignore；删掉该目录可从零重建环境） |

## 快速开始

```powershell
# 1) 首次初始化 MySQL（建独立实例 + root 密码 + 建库 pinliao）
powershell -ExecutionPolicy Bypass -File scripts\db-init.ps1

# 2) 一键启动 MySQL + Redis
powershell -ExecutionPolicy Bypass -File scripts\dev-up.ps1

# 3) 指定 JDK（JBR 21）
$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr'

# 4) 启动后端（首次会下载 Maven 3.9.16 与依赖，耗时较长）
.\mvnw.cmd spring-boot:run
```

自检地址：

- 健康检查 `http://localhost:8080/actuator/health` → `{"status":"UP"}`
- 包络样例 `http://localhost:8080/api/v1/_demo/ping` → `{"code":0,"message":"ok","data":{...},"traceId":"..."}`
- 参数校验样例 `http://localhost:8080/api/v1/_demo/validate?phone=123` → 422 + `errors` 数组
- 接口文档 `http://localhost:8080/swagger-ui.html`

## 本地账号（仅演示用）

- MySQL：`127.0.0.1:3307`，用户 `root` / 密码 `123456`，库 `pinliao`（utf8mb4）
- Redis：`127.0.0.1:6379`（仅监听本机）

## 脚本一览（`scripts/`）

| 脚本 | 说明 |
|---|---|
| `db-init.ps1` | 首次初始化项目专用 MySQL 实例（3307，数据目录 `.local/mysql/data`） |
| `db-start.ps1` / `db-stop.ps1` | 起 / 停 MySQL |
| `redis-start.ps1` / `redis-stop.ps1` | 起 / 停 Redis |
| `dev-up.ps1` / `dev-down.ps1` | 一键起 / 停两者 |

可用环境变量覆盖：`PINLIAO_MYSQL_HOME`（MySQL 二进制目录）、`PINLIAO_REDIS_HOME`（Redis 目录）。

## 目录结构

```
backend/
├─ scripts/            本地环境脚本（见上表）
├─ src/main/java/com/pinliao/backend/
│  ├─ common/api/      统一包络 ApiResponse + 错误码表 ApiCodes
│  ├─ common/web/      traceId 过滤器 + 包络装配 ResponseAdvice
│  ├─ common/exception/ 业务异常 + 全局异常处理
│  ├─ config/          OpenAPI 等配置
│  └─ demo/            骨架自检样例接口（非契约，T9 后可移除）
├─ src/main/resources/
│  ├─ application.yml          通用配置
│  ├─ application-local.yml    本地环境（MySQL 3307 / Redis 6379）
│  └─ db/migration/            Flyway 迁移脚本（T1 起）
└─ src/test/java/...           包络/异常冒烟测试（不依赖数据库）
```
