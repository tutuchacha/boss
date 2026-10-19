# 聘聊 · 可观测性增量计划（阿里云 ARMS 链路接入）

> 状态唯一定稿在 [`PROGRESS.md`](../PROGRESS.md)；本文件是该增量的**方案与任务板**。
> 前置：Step 3 后端全量完成（真实后端 `npm run e2e` **63/63**）、Step 4 T-AI0 完成（`mvnw test` **176/176**）。
> 编号说明：本增量**独立于 Step 4（AI）**，可并行推进；任务号 **T-OBS0 ~ T-OBS3**。
> 最后更新：2026-10-19 · 状态：**方案待拍板**；T-OBS1（脚本与骨架）可在无 Token 时先行落地。

---

## 0. 目标与验收口径（DoD）

- **业务目标**：把本机后端 `pinliao-backend`（Spring Boot 3.5，`:8080`）的**请求链路**接入阿里云 ARMS，
  让用户能在 ARMS 控制台按「接口 / 时间 / 耗时 / 错误」筛出调用链，并且**用接口响应包络里的 `traceId`
  直接反查链路** —— 这样「验收前面的功能」时，慢接口、报错接口、SQL 耗时都能一眼定位。
- **DoD（T-OBS2 验收口径）**：
  1. ARMS 控制台出现应用 `pinliao-backend`，能看到真实调用链：HTTP → Controller →
     MyBatis/JDBC → MySQL（含 SQL 文本）；Redis；以及 `/api/v1/ai/*` 对百炼的上游调用；
  2. **任意接口响应包络里的 `traceId` == ARMS 链路详情中的 TraceId**（可复制粘贴直接搜）；
  3. 触发一次业务错误（如错误密码登录 → `40110`）能在 ARMS 看到**错误链路**；
  4. **不接入时零影响**：默认开关关闭 → 启动方式、`npm run e2e` 63/63、`mvnw test` 176/176 全部不变；
  5. ARMS 的 Token 只进环境变量，**仓库零命中**（与 `PINLIAO_AI_*` 同一套做法）。
- **本轮不做**：云上部署、日志采集到 SLS、告警规则、Grafana 大盘、压测。

## 1. 选型（三条路径对比）

| 路径 | 做法 | 能得到什么 | 代价 | 结论 |
|---|---|---|---|---|
| **A. OpenTelemetry Java Agent（推荐起点）** | 下载 `opentelemetry-javaagent.jar`，启动加 `-javaagent`，其余全走 `OTEL_*` 环境变量 | ARMS「可观测链路 OpenTelemetry 版」：调用链、应用大盘、Trace Explorer、错/慢链路；**自动**埋点 Spring MVC / JDBC / HikariCP / Lettuce(Redis) / Logback / Executors | 启动命令多一个 `-javaagent`（用脚本封装即可）；**零业务代码改动** | ✅ 先做（T-OBS1/2） |
| B. 阿里云 ARMS Java Agent（深度应用监控） | 控制台「应用监控」新建应用 → 下载 `arms-agent.jar`，`-javaagent` + `arms.licenseKey` | 路径 A 的全部，外加 ARMS 原生应用监控：慢 SQL、异常分析、JVM/线程、接口大盘、拓扑、可配告警 | 依赖阿里云自研探针；与 A 是**两套报表口径** | 备选升级（T-OBS3） |
| C. Micrometer Tracing + OTLP（不用 agent） | 加 `micrometer-tracing-bridge-otel` + `management.otlp.tracing.*` | 只有 HTTP 级 span（需手动埋点补 SQL/Redis），无 JVM 深度 | 埋点最少、收益最低 | 不建议（除非明确不想用 agent） |

**为什么推荐 A**：官方文档主推（`opentelemetry-java-agent` 自动埋点）、**零代码**、与本地
Windows + `mvnw spring-boot:run` 集成成本低，风险最小；且同一份标准 OTel 数据以后可同时喂给
路径 B 或任意后端，不锁死。

> 版本事实（已核对本机 `spring-boot-dependencies-3.5.16.pom`）：Boot 3.5.16 已管理
> `micrometer-tracing 1.5.12` 与 `opentelemetry-bom 1.49.0`，故路径 C 或 T-OBS2 需要的
> `io.opentelemetry:opentelemetry-api` **无需自己定版本**。

## 2. 架构与数据流

```
浏览器 / 前端 dev(:3000)  --next.config rewrites-->  pinliao-backend(:8080, Tomcat)
                                                        │
                                                        ├─ MyBatis-Plus / JDBC ──> MySQL(127.0.0.1:3307)
                                                        ├─ Lettuce ─────────────> Redis(127.0.0.1:6379)
                                                        └─ Spring AI(HTTP) ─────> 百炼 DashScope
        OTel Java Agent（进程内，自动埋点，批量上报）
              └─ gRPC ──> tracing-analysis-dc-<region>.aliyuncs.com:8090   （ARMS 公网接入点）
```

要点：

- **本机在公网** → 必须用控制台的**公网**接入点；内网域名（`...-internal.aliyuncs.com`）本地不通。
- **地域**由控制台决定（华东1-杭州 `dc-hz` / 华北2-北京 `dc-bj` …），接入点随地域变 → 走环境变量。
- **上报协议**：默认 **gRPC**（`:8090` + `Authentication: <token>` 头，配置最简）；
  若本地对 8090 出网受限，改 **HTTP/protobuf**（Token 直接内嵌在接入点 URL 里，不需要 header）。
- **traceId 打通（T-OBS2）**：当前 `common/web/TraceIdFilter` 自己生成 UUID 写入 MDC + 响应头
  `X-Trace-Id` + 包络 `traceId`。改为「**有活跃 OTel Span 时取其 traceId，否则回退 UUID**」，
  这样**包络里的 traceId 就是 ARMS 里的 TraceId**。OTel traceId 同样是 32 位小写 hex，**契约（§4.1）不变**，
  且实测 `tests/e2e-verify.cjs` **不校验 traceId**，不影响 e2e。

## 3. 配置与密钥（环境变量，不进仓库）

| 变量 | 示例值 | 说明 |
|---|---|---|
| `PINLIAO_OTEL_ENABLED` | `true` | 总开关；**默认 false** → 脚本不带 `-javaagent`，行为与现在完全一致 |
| `PINLIAO_OTEL_SERVICE_NAME` | `pinliao-backend` | ARMS 里的应用名 |
| `PINLIAO_OTEL_ENDPOINT` | `http://tracing-analysis-dc-hz.aliyuncs.com:8090` | 控制台「公网 + gRPC」接入点 |
| `PINLIAO_OTEL_TOKEN` | `<控制台 Token>` | 鉴权 Token（**只进环境变量**，仓库只写 `${占位}`） |
| `PINLIAO_OTEL_ENV` | `local` | `deployment.environment` 资源属性，便于区分本机/云上 |
| `PINLIAO_OTEL_SIGNALS` | `traces` | `traces`（默认，省费用）或 `all`（含 metrics/JVM） |

脚本内把它们映射成 agent 认的原生变量（**只有应用 JVM 生效**）：

```text
-javaagent:D:\boss\.local\otel\opentelemetry-javaagent.jar
OTEL_SERVICE_NAME          = PINLIAO_OTEL_SERVICE_NAME
OTEL_EXPORTER_OTLP_ENDPOINT= PINLIAO_OTEL_ENDPOINT
OTEL_EXPORTER_OTLP_PROTOCOL= grpc
OTEL_EXPORTER_OTLP_HEADERS = Authentication=<PINLIAO_OTEL_TOKEN>
OTEL_TRACES_EXPORTER       = otlp
OTEL_METRICS_EXPORTER      = otlp | none      （由 PINLIAO_OTEL_SIGNALS 决定）
OTEL_LOGS_EXPORTER         = none             （日志暂不采集，避免费用与噪音）
OTEL_RESOURCE_ATTRIBUTES   = service.name=<...>,service.version=<...>,deployment.environment=<...>
```

细节：

- agent jar 放 `D:\boss\.local\otel\opentelemetry-javaagent.jar`（`/.local/` 已 gitignore）。
- **下载兜底**：GitHub Releases 若直连不通，走 Maven Central
  （`io.opentelemetry.javaagent:opentelemetry-javaagent:<ver>`，本机 Central 已验证可达）。
- SQL 文本默认**脱敏**（agent 的 db-statement sanitizer 默认开）——本项目 SQL 会带手机号，
  **不要**为了看清 SQL 而关掉脱敏。

## 4. 改动清单（很小，且不碰既有功能）

后端：

- **新增** `backend/scripts/dev-with-arms.ps1`：读环境变量 → 组 `-javaagent` 与 `OTEL_*` →
  执行 `mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-javaagent:<绝对路径>"`
  （只在**应用 JVM** 挂 agent，不给 Maven 自身挂）。开关关闭或未配 Token 时给出清晰提示。
- **改** `backend/scripts/_env.ps1`：补 `PINLIAO_OTEL_*` 读取与默认值（与 `PINLIAO_MYSQL_HOME` 同风格）。
- **改** `common/web/TraceIdFilter`：优先取活跃 OTel span 的 traceId（`Span.current()`；无 SDK/无 agent 时
  自动返回 invalid span → 回退 UUID，**不抛异常**）；新增依赖 `io.opentelemetry:opentelemetry-api`（版本由 Boot BOM 管）。
- **新增单测** `common/web/TraceIdFilterTest`：有 span → 用 span traceId；无 span → 32 位小写 hex；
  透传合法 `X-Trace-Id`、非法（超长）时重新生成 —— 与既有行为一致。
- **改** `backend/README.md`：新增「ARMS 链路（可选）」一节（环境变量、脚本用法、验收步骤）。

不动：`pom.xml` 依赖清单（除 `opentelemetry-api` 这一条）、`db-reset.ps1`（**默认不带 agent**，保证 e2e 基线不变）、
`dev-up.ps1`、前端任何代码。

## 5. 任务板

| 编号 | 任务 | 产出与验收 | 依赖 |
|---|---|---|---|
| **T-OBS0** | 控制台前置 | 按 §7 取到：地域 / 公网 gRPC 接入点 / Token / 应用名，写入 4 个用户级环境变量；脚本能打印「OTEL 已启用，endpoint=…，token=`***`」 | 用户操作 |
| **T-OBS1** | 脚本 + agent 落地 | `.local/otel/opentelemetry-javaagent.jar` 就位；`dev-with-arms.ps1` + `_env.ps1` + README；用脚本起后端，日志出现 agent 启动信息、接口正常 | 可先做（无 Token 也能起，只是不上报成功） |
| **T-OBS2** | traceId 打通 + 链路验收 | 包络 `traceId` == ARMS TraceId；ARMS 能看到 HTTP/SQL/Redis span 与错误链路；`npm run e2e` 63/63、`mvnw test` 全绿 | T-OBS0 / T-OBS1 |
| **T-OBS3** | 可选深化 | ① metrics/JVM 开关 ② 慢 SQL / 异常分析 ③ 日志关联（SLS） ④ 路径 B（ARMS 应用监控探针）⑤ 前端监控（Web SDK） | T-OBS2 |

估算：T-OBS1 ≈ 0.5 天；T-OBS2 ≈ 0.5 天；T-OBS3 按需。

## 6. 风险与未决（需用户拍板）

| 项 | 说明 |
|---|---|
| **费用** | ARMS 按量计费（有免费试用额度）。默认 `PINLIAO_OTEL_SIGNALS=traces` 只上报链路、不上报 metrics，可显著降本；**需用户确认已了解计费与额度** |
| **地域** | 选哪个地域（建议华东1-杭州）；本地必须选**公网**接入点 |
| **上报协议** | 默认 gRPC:8090；若本地出网受限，改 HTTP/protobuf（Token 在 URL 中） |
| **是否同时上路径 B** | B（ARMS 应用监控）能看慢 SQL / 异常分析 / JVM，但与 A 是两套数据；建议 A 跑通后再决定 |
| **前端监控** | ARMS 前端监控会在浏览器发额外请求，**可能破坏 e2e「零控制台错误」门禁** → 建议最后做且加 env 开关 |
| 出网要求 | 需能访问 `*.aliyuncs.com:8090`（下载 agent 时另需 GitHub 或 Maven Central） |

## 7. 用户侧操作清单（T-OBS0）

1. 开通 **ARMS（应用实时监控服务）**：阿里云控制台 → 产品 → 应用实时监控服务；
   首次接入有免费额度/按量计费（见 §6 费用项）。
2. ARMS 控制台 → 左侧 **接入中心** → 「服务端应用」区 → 点 **OpenTelemetry** 卡片。
3. 选择**数据上报地域**（初次接入会自动初始化该地域的资源）。
4. 连接方式选 **公网**；上报方式选 **gRPC**；复制**接入点（Endpoint）**与**鉴权 Token**。
5. 把下面 4 个值给我（或自行按 §3 写入用户级环境变量）：
   `地域/接入点`、`Token`、`应用名`（建议 `pinliao-backend`）、`环境`（建议 `local`）。

> 安全：Token 与 `PINLIAO_AI_API_KEY` 同级对待——只进用户级环境变量，仓库只留 `${占位}`；
> 落地后用 `git grep` / 未跟踪文件扫描 / `git log -S` 三连核查零命中。

## 8. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-10-19 | 首版。基于本机实况（无 Docker、Windows + mvnw、公网开发机、后端已有自研 traceId 与统一包络、e2e 63/63 门禁）制定 ARMS 链路接入方案：路径 A（OTel Java Agent，零代码）为起点，路径 B（ARMS 应用监控）为可选深化；traceId 打通作为验收硬指标；Token 走环境变量；默认开关关闭以保证既有基线不变。任务板 T-OBS0~T-OBS3 |
