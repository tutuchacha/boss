# 聘聊 · 后端服务（pinliao-backend）

Step 3 后端：实现 `docs/api-endpoints.md` 正文契约（70 条）与 `docs/api-contract.md` 通用规范
（统一包络 / 错误码 / traceId / 时间格式）。技术栈：Spring Boot 3.5 + MySQL 8 + Redis + MyBatis-Plus。

Step 4 起增量接入 AI（Spring AI 1.1.8 + 百炼 DashScope OpenAI 兼容模式，见「AI 配置」一节）。

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

# 5g) 招聘者模块冒烟（HR 画像 / 我发布的职位 / 我收到的投递 / 人才筛选；可重复执行）
node scripts\smoke-recruiter.mjs

# 6) 种子行数抽查（16 项断言；需已启动过后端完成 Flyway 迁移 V1~V8）
powershell -ExecutionPolicy Bypass -File scripts\verify-seed.ps1
```

前端联调（Step 3 T9）：

```powershell
# 每轮前端 e2e 前先清库重迁（否则固定手机号 13900004321 已注册、验证码进 60s 冷却）
powershell -ExecutionPolicy Bypass -File scripts\db-reset.ps1

# 仓库根目录 .env.local（不提交）：NEXT_PUBLIC_USE_MOCK=false、NEXT_PUBLIC_API_BASE_URL=/api/v1
# next.config.ts 已把 /api/v1 与 /uploads 代理到 http://localhost:8080（可用 API_PROXY_TARGET 覆盖）
cd ..
npm run dev     # 改 .env.local 后必须重启
npm run e2e     # 真实后端下 63/63
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
- AI 自检（Step 4 T-AI0，登录 token）：`GET /api/v1/ai/status`（只回显配置）、
  `GET /api/v1/ai/ping`（真实调用一次 chat + embedding，返回耗时 / token / 向量维度）。
  两者**受鉴权拦截器保护**（非公开），避免未授权调用产生费用；配置见下节「AI 配置」

## AI 配置（Step 4 T-AI0）

对话与向量都走**阿里云百炼（DashScope）OpenAI 兼容模式**。**密钥只放本机环境变量，绝不进仓库**
（仓库内只有 `${PINLIAO_AI_API_KEY:占位}` 引用；已核查 `git grep` / 未跟踪文件 / 全部历史提交零命中）。

一次设置（**用户级**，持久化到注册表；重启终端 / IDE 后对新进程生效）：

```powershell
# 改成你自己的 key；其余为演示默认值，可只设 KEY 与 ENABLED
[Environment]::SetEnvironmentVariable('PINLIAO_AI_API_KEY',         'sk-……',                                            'User')
[Environment]::SetEnvironmentVariable('PINLIAO_AI_BASE_URL',        'https://dashscope.aliyuncs.com/compatible-mode',   'User')
[Environment]::SetEnvironmentVariable('PINLIAO_AI_CHAT_MODEL',      'qwen3.5-122b-a10b',                                'User')
[Environment]::SetEnvironmentVariable('PINLIAO_AI_EMBEDDING_MODEL', 'text-embedding-v4',                                'User')
[Environment]::SetEnvironmentVariable('PINLIAO_AI_EMBEDDING_DIM',   '1024',                                             'User')
[Environment]::SetEnvironmentVariable('PINLIAO_AI_ENABLED',         'true',                                             'User')   # 总开关，默认 false
```

改完**重启后端**（环境变量在进程启动时读取），然后自检：

```powershell
$tk = (Invoke-RestMethod http://localhost:8080/api/v1/auth/login/password -Method Post `
       -ContentType 'application/json' -Body '{"account":"13800000002","password":"123456"}').data.accessToken
Invoke-RestMethod http://localhost:8080/api/v1/ai/status -Headers @{ Authorization = "Bearer $tk" } | ConvertTo-Json -Depth 5
Invoke-RestMethod http://localhost:8080/api/v1/ai/ping   -Headers @{ Authorization = "Bearer $tk" } | ConvertTo-Json -Depth 5
```

实测参考（2026-10-19）：`ping` → chat `1005ms` / `44 tokens`、embedding `538ms` / `dim 1024`，端到端 1.56s。

已知要点：

- `qwen3.5-122b-a10b` 是**思考型**模型，代码里已固定 `enable_thinking=false`（否则 >10s 且多计 token）。
- key 前缀 `sk-ws-` 属百炼套餐类 key，但**按量计费的兼容模式端点可用**（实测 chat / embeddings 均 200）；
  若误配 `coding.dashscope.aliyuncs.com`（Coding Plan 专属，只服务编码类工具）会 401。
- `pinliao.ai.enabled=false` 时 `/ai/ping` 返回 **42220**，`/ai/status` 仍可读（enabled=false），
  不影响任何既有接口。

## ARMS 链路（可选，T-OBS1）

把本机后端的请求链路接入**阿里云 ARMS「应用监控」**（Java 探针，无侵入），用于按接口 / 耗时 / 错误筛调用链。
**默认关闭**：不设 `PINLIAO_ARMS_ENABLED=true` 时，启动方式、`mvnw test`、`npm run e2e` 与未接入时完全一致
（本增量实测：`mvnw test` **182/182**、真实后端 `npm run e2e` **63/63**）。

为什么用**阿里云自研探针**而不是开源 `opentelemetry-javaagent.jar`：两者都能被 ARMS 接收，但落在不同产品里 ——
开源 Agent 进「可观测链路 OpenTelemetry 版」（应用列表语言列显示 `-`，只有调用链 / 大盘 / Trace Explorer）；
阿里云探针进「应用监控」（Java 图标，含慢 SQL、异常分析、JVM / 线程、接口大盘、拓扑）。
选型对比与踩坑记录见 `docs/arms-plan.md`。

一次设置（**用户级**环境变量；LicenseKey 同 AI key 一样只进环境变量，绝不进仓库）：

```powershell
.\scripts\fetch-arms-agent.ps1                     # 下载探针到 .local\arms\AliyunJavaAgent\（≈51MB，已 gitignore）
.\scripts\fetch-arms-agent.ps1 -Region cn-beijing  # 换地域（默认 cn-hangzhou）

# LicenseKey 来自 ARMS 控制台 → 应用监控 → 应用列表 → 创建应用（无侵入接入 / Java）
[Environment]::SetEnvironmentVariable('PINLIAO_ARMS_ENABLED',     'true',            'User')
[Environment]::SetEnvironmentVariable('PINLIAO_ARMS_LICENSE_KEY', '<控制台 LicenseKey>', 'User')
[Environment]::SetEnvironmentVariable('PINLIAO_ARMS_APP_NAME',    'pinliao-backend', 'User')   # 可选，默认即此值
```

改完**新开终端**（`setx` / 用户级变量只对新进程生效），然后：

```powershell
.\scripts\dev-with-arms.ps1 -DryRun       # 只做配置自检（密钥打码），不启动
.\scripts\dev-with-arms.ps1               # 前台启动（Ctrl+C 停）
.\scripts\dev-with-arms.ps1 -Background   # 后台启动 + 日志 .local\backend-arms.log + 等 health UP
```

启动后日志应出现探针自述：`Load Apsara Java Agent version: 5.1.2.` /
`report to region: cn-hangzhou`。约 1 分钟后在控制台「应用监控 → 应用列表」能看到应用（`pinliao-backend`），
点进去即可看调用链 / 慢 SQL / 异常。

想验证「包络 traceId 就是链路 TraceId」：随便打一个接口，把响应里的 `traceId` 到控制台
「调用链查询 → TraceId」搜，应能搜到同一条链路（含 HTTP 入口 + MySQL/Redis 子调用）。

已知要点（本机实测 2026-10-19，链路**已打通**）：

- 探针版本 `apsara-java-agent-5.1.2-otel-2.23.0` —— 是 **opentelemetry-javaagent 的阿里云改名版**，
  所以后续想让链路带自定义埋点可以直接用 OTel API；jar 解压在 `.local/arms/AliyunJavaAgent/`。
- 探针**只挂在 `spring-boot:run` fork 出的应用 JVM** 上（脚本用 `-Dspring-boot.run.jvmArguments=-javaagent:……`）；
  **不要**用 `JAVA_TOOL_OPTIONS`，否则 Maven 自身也会挂探针，控制台里会出现同名的第二个应用、数据双份。
- **`-Background` 的引号坑（已修）**：`Start-Process -ArgumentList <数组>` 只按空格拼接、
  不给含空格的元素补引号，`-Dspring-boot.run.jvmArguments="…"` 会被拆开，应用 JVM 只拿到 `-javaagent`，
  表现为「探针加载了但控制台看不到应用、请求没有 span」。脚本现已改为单个带引号的参数字符串；
  排查同类问题时先 `Get-CimInstance Win32_Process` 回读应用 JVM 命令行，确认 `-Darms.*` 真的在。
- **判活三条判据**（本地自检，别再看 `status` 文件）：
  ① 应用 JVM 命令行含 `-javaagent` + `-Darms.appName` + `-Darms.licenseKey`；
  ② 送 `X-Trace-Id=mycustomtraceid123`，包络**不回显**该值而是 32 位 hex（= span 分支命中）；
  ③ 应用 JVM 有到阿里云 **80** 端口的 Established 连接（= 正在上报；`100.103.107.x:9990` 的 SynSent
  属 EagleEye 配置通道被阻断，不影响链路数据）。
- 探针工作目录：`C:\Users\<你>\.apsara-apm\java\<版本>\`。⚠️ 其中 `status` 文件**不刷新**，
  链路已通时它仍可能是 `licenseKey=null`，**不要**拿它作为授权判据。
- SQL 文本报警默认脱敏（含手机号），**不要**为看清 SQL 关掉探针的脱敏开关。
- 收工用 `.\scripts\db-reset.ps1`（无探针，自动恢复干净环境）。

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
| `db-reset.ps1` | **T9 起：清库重迁**——按 8080 端口止后端 → `DROP + CREATE pinliao` → Redis `FLUSHALL` → 起后端 → 等 `/actuator/health` UP → 回读基线（`-NoRestart` 只清库、`-KeepRedis` 保留缓存）。每轮 `npm run e2e` 前必跑。Win PowerShell 5.1 与 PowerShell 7 均可（T-AI0 修复：5.1 下 JDK 探测曾被 `$ErrorActionPreference='Stop'` 吞成 major=0） |
| `smoke-auth.mjs` | 鉴权接口冒烟（Node 18+；后端启动后运行，20 项断言含注册） |
| `smoke-jobs.mjs` | 职位与字典接口冒烟（T3a/T3b；公开读 + HR 写 发布 / 更新 / 关闭重开 / 软删 / 越权，49 项断言） |
| `smoke-applications.mjs` | 投递与面试接口冒烟（T4；状态机 / 校验码 / 约面试 / 接受拒绝 / 撤回重投，51 项断言，可重复执行） |
| `smoke-company.mjs` | 公司模块冒烟（T5；公开详情 / 公司职位分页 / 资料编辑 / 成员 / 企业认证 PENDING→自动通过 / 40916，26 项断言，可重复执行） |
| `smoke-messages.mjs` | 消息与 WebSocket 冒烟（T6；会话 / 消息 / 发起 / 发送 / 幂等 / 已读 / typing / 发送简历 / 关闭 + STOMP 推送 / 错误队列，40 项断言，可重复执行） |
| `smoke-user.mjs` | 用户 / 简历 / 收藏 / 上传冒烟（T7；画像 / 密码 / 手机号 / 偏好 / 注销 / 简历默认切换 / 收藏分页 / 预签名直传与静态读回，64 项断言，可重复执行） |
| `smoke-recruiter.mjs` | 招聘者模块冒烟（T8；HR 画像读改 / 我发布的职位（含 CLOSED 可见）/ 我收到的投递超集 / 人才筛选关键词与证据 / 词表，50 项断言，可重复执行） |
| `verify-seed.ps1` | 种子行数抽查（16 项断言，对齐 step3-plan §3 数字；须先完成迁移；已排除冒烟 / 注册产物前缀、T7 软删简历） |
| `fetch-arms-agent.ps1` | **T-OBS1**：下载阿里云 ARMS Java 探针到 `.local/arms/AliyunJavaAgent/`（默认华东1-杭州公网，`-Region` 换地域、`-ZipPath` 用控制台下载的包、`-Force` 重下） |
| `dev-with-arms.ps1` | **T-OBS1**：带 ARMS 探针启动后端（只挂应用 JVM）。`-DryRun` 配置自检（密钥打码）、`-Background` 后台起 + 等 health UP、`-AllowMissingLicenseKey` 仅验证探针能加载。**T-OBS2 修**：`-Background` 改为传单个带引号的参数字符串（原先 `Start-Process -ArgumentList` 数组会丢引号，`-Darms.*` 进不了应用 JVM）。默认关闭时请用 `db-reset.ps1` / `mvnw spring-boot:run` |

当前验收基线（2026-10-19）：`mvnw test` **182/182**（T-AI0 新增 3 项：AI 自检开关 / 调用参数 + AI 端点非公开；
T-OBS2 新增 6 项：traceId 三级优先级 `TraceIdFilterTest`）；
`smoke-auth` 20/20；`smoke-jobs` 49/49；
`smoke-applications` 51/51；`smoke-company` 26/26；`smoke-messages` 40/40（连续两次）；
`smoke-user` 64/64（连续两次）；`smoke-recruiter` 50/50（可重复执行）；`verify-seed` 16/16；
前端 `npm run e2e` 在**真实后端**下 **63/63**（T9，连续两轮，每轮前先 `db-reset.ps1`；
T-OBS1 后又复跑一轮 63/63；**T-OBS2 带 ARMS 探针再跑一轮仍是 63/63**，确认接入探针对既有功能零影响）。

另有 `scripts/seed-gen/`（开发期工具）：从仓库 `src/api/mock/*` 生成 `V4~V8` 种子迁移，
杜绝手抄漂移；**勿手改 `V4~V8` 迁移文件**，改演示数据请改 mock 后重跑生成脚本（用法见脚本头部注释）。

可用环境变量覆盖：`PINLIAO_MYSQL_HOME`（MySQL 二进制目录）、`PINLIAO_REDIS_HOME`（Redis 目录）、
`PINLIAO_UPLOAD_DIR`（上传目录，默认 `target/uploads`）、`PINLIAO_JAVA_HOME`（编译用 JDK，需 ≥17）、
`PINLIAO_ARMS_*`（ARMS 探针，见「ARMS 链路（可选）」一节）。

## 目录结构

```
backend/
├─ scripts/            本地环境脚本（见上表）
├─ src/main/java/com/pinliao/backend/
│  ├─ common/api/      统一包络 ApiResponse + 错误码表 ApiCodes
│  ├─ common/web/      traceId 过滤器（优先取活跃 OTel/ARMS span 的 traceId，T-OBS2）+ 包络装配 ResponseAdvice
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
│  ├─ ai/              AI 连通性自检 / 配置（Step 4 T-AI0；`/api/v1/ai/*`）
│  ├─ config/          MyBatis-Plus 分页 / 租户注入 / 时间填充 + OpenAPI 等配置（T1）
│  └─ demo/            骨架自检样例接口（非契约，T9 后可移除）
├─ src/main/resources/
│  ├─ application.yml          通用配置（含 `spring.ai.openai.*` + `pinliao.ai.enabled`，key 走环境变量）
│  ├─ application-local.yml    本地环境（MySQL 3307 / Redis 6379）
│  └─ db/migration/            Flyway 迁移（V1 鉴权建表 / V2 演示账号 / V3 业务建表 / V4~V8 演示种子）
└─ src/test/java/...           包络 / JWT / 种子密码 / 租户策略 / 注册 / 职位 / 投递面试 / 公司 / 消息 / 用户 / 简历 / 收藏 / 上传 / AI 自检 / traceId 优先级测试（不依赖数据库与网络）
```
