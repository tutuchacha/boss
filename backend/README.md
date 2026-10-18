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

# 5) 另开终端跑接口冒烟（登录 / 注册 / 刷新 / 退出；需 Node 18+）
node scripts\smoke-auth.mjs

# 5b) 职位与字典冒烟（公开读 + HR 写：发布 / 更新 / 关闭重开 / 软删 / 越权）
node scripts\smoke-jobs.mjs

# 5c) 投递与面试冒烟（投递 / 状态机 / 约面试 / 接受拒绝 / 撤回重投；可重复执行）
node scripts\smoke-applications.mjs

# 5d) 公司模块冒烟（公开详情 / 公司职位 / 资料编辑 / 成员 / 企业认证自动通过；可重复执行）
node scripts\smoke-company.mjs

# 5e) 消息与 WebSocket 冒烟（REST + STOMP 推送 / typing / 错误队列；可重复执行）
node scripts\smoke-messages.mjs

# 5f) 用户 / 简历 / 收藏 / 上传冒烟（19 条接口 + 错误分支 + 真实 PUT 直传；可重复执行）
node scripts\smoke-user.mjs

# 6) 种子行数抽查（16 项断言；需已启动过后端完成 Flyway 迁移 V1~V8）
powershell -ExecutionPolicy Bypass -File scripts\verify-seed.ps1
```

自检地址：

- 健康检查 `http://localhost:8080/actuator/health` → `{"status":"UP"}`
- 包络样例 `http://localhost:8080/api/v1/_demo/ping` → `{"code":0,"message":"ok","data":{...},"traceId":"..."}`
- 参数校验样例 `http://localhost:8080/api/v1/_demo/validate?phone=123` → 422 + `errors` 数组
- 接口文档 `http://localhost:8080/swagger-ui.html`
- 鉴权接口 `POST /api/v1/auth/login/password`（演示账号 13800000001 / 13800000002，密码 123456；
  受保护接口需 `Authorization: Bearer <accessToken>`）
- 职位写接口需 HR token（13800000002）：`POST /api/v1/jobs`、`PATCH /api/v1/jobs/{jobId}`、
  `POST /api/v1/jobs/{jobId}/close|reopen`、`DELETE /api/v1/jobs/{jobId}`（40310 / 42211 / 42901）
- 投递与面试接口（求职者 / HR token）：`POST|GET /api/v1/applications`、`GET /api/v1/applications/{id}`、
  `POST .../withdraw|status`、`GET /api/v1/interviews`、`GET /api/v1/hr/interviews`、
  `POST .../accept|decline`、`POST /api/v1/conversations/{id}/actions/invite-interview`
  （40911 / 40913 / 40914 / 40915 / 42210）
- 公司接口：公开 `GET /api/v1/companies/{id}`、`GET /api/v1/companies/{id}/jobs`；HR token
  `GET|PATCH /api/v1/company/me`、`GET /api/v1/company/members`、`POST /api/v1/company/me/verify`
  （40410 / 40916 / 42210）；COMPANY_ADMIN 以 `company_members.role=ADMIN` 为准；
  认证自动通过延迟可用 `pinliao.company.verify-delay-ms` 覆盖（默认 1200ms）
- 消息接口（登录 token）：`GET|POST /api/v1/conversations`、`GET /api/v1/conversations/{id}`（扩展）、
  `GET|POST /api/v1/conversations/{id}/messages`、`POST .../read|typing|close`、
  `POST .../actions/send-resume`（40912 / 40410 / 42201 / 42901）；TEXT 自动回复延迟可用
  `pinliao.message.auto-reply-delay-ms` 覆盖（默认 1200ms）
- WebSocket：`ws://localhost:8080/ws?token=<accessToken>`（STOMP）；订阅 `/user/queue/messages`、
  `/user/queue/typing`、`/topic/conversation/{id}/typing`；发送 `/app/conversation/{id}/send|typing|read`；
  业务错误推 `/user/queue/errors`
- 用户 / 简历 / 收藏 / 上传（登录 token）：`GET|PATCH /api/v1/user/profile`、
  `POST /user/password/change|reset`、`POST /user/phone/change`、`GET|PATCH /user/preferences`、
  `POST /user/deactivate`；`GET|POST /api/v1/resumes`、`GET|PATCH|DELETE /resumes/{id}`、
  `POST /resumes/{id}/set-default`；`GET|POST /api/v1/favorites`、`DELETE /favorites/{jobId}`；
  `POST /uploads/sign|confirm`（上传直传为 `PUT /api/v1/uploads/local/{objectKey}?expires=&sig=`）。
  偏好存 Redis（`pinliao:user:prefs:{userId}`）；上传目录默认 `target/uploads`，可用
  `PINLIAO_UPLOAD_DIR` 覆盖，URL 有效期 `pinliao.upload.url-ttl`（默认 10m）；静态地址 `/uploads/**`

## 本地账号（仅演示用）

- MySQL：`127.0.0.1:3307`，用户 `root` / 密码 `123456`，库 `pinliao`（utf8mb4）
- Redis：`127.0.0.1:6379`（仅监听本机）
- 应用演示账号（V2 种子）：求职者 `13800000001` / HR `13800000002`，密码均 `123456`；
  短信验证码固定 `123456`（60s 限流）；access 2h / refresh 30d

## 脚本一览（`scripts/`）

| 脚本 | 说明 |
|---|---|
| `db-init.ps1` | 首次初始化项目专用 MySQL 实例（3307，数据目录 `.local/mysql/data`） |
| `db-start.ps1` / `db-stop.ps1` | 起 / 停 MySQL |
| `redis-start.ps1` / `redis-stop.ps1` | 起 / 停 Redis |
| `dev-up.ps1` / `dev-down.ps1` | 一键起 / 停两者 |
| `smoke-auth.mjs` | 鉴权接口冒烟（Node 18+；后端启动后运行，20 项断言含注册） |
| `smoke-jobs.mjs` | 职位与字典接口冒烟（T3a/T3b；公开读 + HR 写 发布 / 更新 / 关闭重开 / 软删 / 越权，49 项断言） |
| `smoke-applications.mjs` | 投递与面试接口冒烟（T4；状态机 / 校验码 / 约面试 / 接受拒绝 / 撤回重投，51 项断言，可重复执行） |
| `smoke-company.mjs` | 公司模块冒烟（T5；公开详情 / 公司职位分页 / 资料编辑 / 成员 / 企业认证 PENDING→自动通过 / 40916，26 项断言，可重复执行） |
| `smoke-messages.mjs` | 消息与 WebSocket 冒烟（T6；会话 / 消息 / 发起 / 发送 / 幂等 / 已读 / typing / 发送简历 / 关闭 + STOMP 推送 / 错误队列，40 项断言，可重复执行） |
| `smoke-user.mjs` | 用户 / 简历 / 收藏 / 上传冒烟（T7；画像 / 密码 / 手机号 / 偏好 / 注销 / 简历默认切换 / 收藏分页 / 预签名直传与静态读回，64 项断言，可重复执行） |
| `verify-seed.ps1` | 种子行数抽查（16 项断言，对齐 step3-plan §3 数字；须先完成迁移；已排除冒烟 / 注册产物前缀、T7 软删简历） |

当前验收基线（2026-10-18）：`mvnw test` **151/151**；`smoke-auth` 20/20；`smoke-jobs` 49/49；
`smoke-applications` 51/51；`smoke-company` 26/26；`smoke-messages` 40/40（连续两次）；
`smoke-user` 64/64（连续两次）；`verify-seed` 16/16。

另有 `scripts/seed-gen/`（开发期工具）：从仓库 `src/api/mock/*` 生成 `V4~V8` 种子迁移，
杜绝手抄漂移；**勿手改 `V4~V8` 迁移文件**，改演示数据请改 mock 后重跑生成脚本（用法见脚本头部注释）。

可用环境变量覆盖：`PINLIAO_MYSQL_HOME`（MySQL 二进制目录）、`PINLIAO_REDIS_HOME`（Redis 目录）、
`PINLIAO_UPLOAD_DIR`（上传目录，默认 `target/uploads`）。

## 目录结构

```
backend/
├─ scripts/            本地环境脚本（见上表）
├─ src/main/java/com/pinliao/backend/
│  ├─ common/api/      统一包络 ApiResponse + 错误码表 ApiCodes
│  ├─ common/web/      traceId 过滤器 + 包络装配 ResponseAdvice
│  ├─ common/exception/ 业务异常 + 全局异常处理
│  ├─ auth/            鉴权模块（JWT / Redis 令牌 / 验证码 / 登录接口，T2a）
│  ├─ job/             职位公开读 + 分类字典 + HR 职位写（T3a/T3b）
│  ├─ application/     投递 / 面试闭环 + §8.7 约面试（T4）
│  ├─ company/         公司详情 / 职位 / 资料 / 成员 / 企业认证（T5）
│  ├─ message/         会话 / 消息 / 自动回复 / STOMP WebSocket（T6）
│  ├─ user/            画像 / 密码 / 手机号 / 偏好 / 注销（T7）
│  ├─ resume/          简历 CRUD / 默认简历 / HR 可见性（T7）
│  ├─ favorite/        收藏 / 取消 / 游标列表（T7）
│  ├─ upload/          预签名 / 本地直传 / 确认 / 静态映射（T7）
│  ├─ config/          MyBatis-Plus 分页 / 租户注入 / 时间填充 + OpenAPI 等配置（T1）
│  └─ demo/            骨架自检样例接口（非契约，T9 后可移除）
├─ src/main/resources/
│  ├─ application.yml          通用配置
│  ├─ application-local.yml    本地环境（MySQL 3307 / Redis 6379）
│  └─ db/migration/            Flyway 迁移（V1 鉴权建表 / V2 演示账号 / V3 业务建表 / V4~V8 演示种子）
└─ src/test/java/...           包络 / JWT / 种子密码 / 租户策略 / 注册 / 职位 / 投递面试 / 公司 / 消息 / 用户 / 简历 / 收藏 / 上传测试（不依赖数据库）
```
