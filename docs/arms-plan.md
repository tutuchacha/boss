# 聘聊 · 可观测性增量计划（阿里云 ARMS 应用监控接入）

> 状态唯一定稿在 [`PROGRESS.md`](../PROGRESS.md)；本文件是该增量的**方案与任务板**。
> 前置：Step 3 后端全量完成（真实后端 `npm run e2e` **63/63**）、Step 4 T-AI0 完成（`mvnw test` **176/176**）。
> 编号说明：本增量**独立于 Step 4（AI）**，可并行推进；任务号 **T-OBS0 ~ T-OBS3**。
> 最后更新：2026-10-19 · 状态：**T-OBS1 骨架已落地（可无 LicenseKey 验证）**；T-OBS2 待用户提供 LicenseKey 后收口。

---

## 0. 目标与验收口径（DoD）

- **业务目标**：把本机后端 `pinliao-backend`（Spring Boot 3.5 + JDK 21，`:8080`）的**请求链路**接入阿里云 ARMS，
  让用户能在控制台按「接口 / 时间 / 耗时 / 错误」筛出调用链，并且**用接口响应包络里的 `traceId`
  直接反查链路** —— 这样「验收前面的功能」时，慢接口、报错接口、SQL 耗时都能一眼定位。
- **DoD（T-OBS2 验收口径）**：
  1. ARMS 控制台出现应用 `pinliao-backend`，能看到真实调用链：HTTP → Controller →
     MyBatis/JDBC → MySQL（含 SQL 文本）；Redis；以及 `/api/v1/ai/*` 对百炼的上游调用；
  2. **任意接口响应包络里的 `traceId` == ARMS 链路详情中的 TraceId**（可复制粘贴直接搜）
     —— **待 LicenseKey 实测确认**，若取不到活跃 span 见 §6 备选；
  3. 触发一次业务错误（如未带 token 访问 `/api/v1/me` → `40101`）能在 ARMS 看到**错误链路**；
  4. **不接入时零影响**：默认开关关闭 → 启动方式、`npm run e2e` 63/63、`mvnw test` 182/182 全部不变
     （本轮已复跑验证 ✅）；
  5. LicenseKey 只进用户级环境变量，**仓库零命中**（与 `PINLIAO_AI_*` 同一套做法）。
- **本轮不做**：云上部署、日志采集到 SLS、告警规则、Grafana 大盘、压测、前端监控。

## 1. 选型（三条路径对比）

| 路径 | 做法 | 数据落在 ARMS 哪里 | 代价 | 结论 |
|---|---|---|---|---|
| **A. 开源 OpenTelemetry Java Agent** | 下载 `opentelemetry-javaagent.jar`，`-javaagent` + `OTEL_*` | **「可观测链路 OpenTelemetry 版」**：应用列表里语言列显示 `-`；有调用链、大盘、Trace Explorer、错/慢链路；**没有**慢 SQL / 异常分析 / JVM / 接口大盘 | 需配 endpoint + token + protocol | 不选（界面不是用户要的） |
| **B. 阿里云 ARMS Java 探针（应用监控）** | 下载 `AliyunJavaAgent.zip`，`-javaagent` + `-Darms.licenseKey` + `-Darms.appName` | **「应用监控」**：语言列显示 Java 图标；含慢 SQL、异常分析、JVM/线程、接口大盘、拓扑 | 探针为阿里云自研（**实为 OTel javaagent 改名版**，见 §4 实测）；与 A 是两套报表口径 | ✅ **采用**（T-OBS1/2） |
| C. Micrometer Tracing + OTLP（不用 agent） | 加 `micrometer-tracing-bridge-otel` + `management.otlp.tracing.*` | 同 A（更少数据） | 只有 HTTP 级 span，需手动埋点补 SQL/Redis | 不选 |

**为什么改选 B（结论修订）**：用户的目标是「引个 jar、一配就能在控制台看调用链 / 慢 SQL / 异常」——
官方文档明确区分：接入**应用监控**的应用在列表里显示 Java 图标，接入**链路 OpenTelemetry 版**的显示 `-`。
A 拿不到应用监控那套能力，且 B 的配置**更简单**（一个 LicenseKey，无需 endpoint/token/protocol）。
补充事实：官方文档写明「ARMS Java 探针从 4.x 起基于 opentelemetry-java-instrumentation 全面重构」，
本机实测探针 `Implementation-Version: apsara-java-agent-5.1.2-otel-2.23.0` —— 即 **OTel javaagent 2.23.0 的阿里云改名版**，
所以 A 与 B 在「OTel API 可用性」上是同源的，§3 的 traceId 打通方式无需改代码即可复用到两条路径。

> 版本事实（已核对本机 `spring-boot-dependencies-3.5.16.pom`）：Boot 3.5.16 已管理
> `opentelemetry-bom 1.49.0` 与 `micrometer-tracing 1.5.12`，故 §4 新增的
> `io.opentelemetry:opentelemetry-api` **无需自己定版本**。

## 2. 架构与数据流

```
浏览器 / 前端 dev(:3000)  --next.config rewrites-->  pinliao-backend(:8080, Tomcat)
                                                        │
                                                        ├─ MyBatis-Plus / JDBC ──> MySQL(127.0.0.1:3307)
                                                        ├─ Lettuce ─────────────> Redis(127.0.0.1:6379)
                                                        └─ Spring AI(HTTP) ─────> 百炼 DashScope
     ARMS Java 探针（-javaagent，仅挂应用 JVM；自动埋点 + 批量上报）
            └─ 上报到 LicenseKey 所属账号 / 地域（默认 cn-hangzhou）→ ARMS「应用监控」
```

要点：

- **只挂应用 JVM**：`spring-boot:run` 默认 fork（本轮实测确认），用
  `-Dspring-boot.run.jvmArguments=-javaagent:...` 即可让探针只进 `PinliaoBackendApplication` 那个进程；
  Maven 自身不挂（否则控制台会出现同名的第二个"应用"、数据双份）。
- **地域**由控制台决定（本轮探针按华东1-杭州 下载并上报 `cn-hangzhou`），可用 `-Region` 换。
- **探针工作目录**在用户目录：`C:\Users\<你>\.apsara-apm\java\<探针版本>\`，含 `status` 文件（可看是否已授权）。
- **traceId 打通（T-OBS2）**：`common/web/TraceIdFilter` 的取值优先级改为
  「活跃 OTel Span 的 traceId → 透传 `X-Trace-Id`（≤64）→ UUID 兜底」，使包络 `traceId` 与 ARMS 链路详情一致。
  OTel traceId 也是 32 位小写 hex，**契约（§4.1）不变**；实测 `tests/e2e-verify.cjs` **不校验 traceId**，不影响 e2e。

## 3. 配置与密钥（环境变量，不进仓库）

| 变量 | 示例值 | 说明 |
|---|---|---|
| `PINLIAO_ARMS_ENABLED` | `true` | 总开关；**默认 false** → 脚本不带 `-javaagent`，行为与现在完全一致 |
| `PINLIAO_ARMS_LICENSE_KEY` | `<控制台 LicenseKey>` | 应用监控接入凭据（**只进环境变量**，仓库只写占位文档） |
| `PINLIAO_ARMS_APP_NAME` | `pinliao-backend` | ARMS 里的应用名（默认即此值） |
| `PINLIAO_ARMS_AGENT_HOME` | `D:\boss\.local\arms\AliyunJavaAgent` | 探针目录（默认 `.local\arms\AliyunJavaAgent`） |

映射成探针认的启动参数（**只有应用 JVM 生效**）：

```text
-javaagent:D:\boss\.local\arms\AliyunJavaAgent\aliyun-java-agent.jar
-Darms.appName=pinliao-backend
-Darms.licenseKey=<PINLIAO_ARMS_LICENSE_KEY>
```

细节：

- 探针 jar 与 zip 都放 `D:\boss\.local\arms\`（`/.local/` 已 gitignore）。
- 下载地址随地域变化：`http://arms-apm-<region>.oss-<region>.aliyuncs.com/AliyunJavaAgent.zip`
  （本机实测华东1-杭州可达，51 MB）；也可在控制台下载后用 `-ZipPath` 指定。
- **SQL 文本默认脱敏**（探针自带 db-statement sanitizer）——本项目 SQL 会带手机号，
  **不要**为了看清 SQL 而关掉脱敏。
- 探针**未配 LicenseKey 时不会产生 span**（详见 §4 实测），故相关验收必须先完成 T-OBS0。

## 4. 改动清单与实测记录（不碰既有功能）

后端：

- **新增** `scripts/fetch-arms-agent.ps1`：下载/解压探针到 `.local\arms\AliyunJavaAgent\`
  （`-Region` / `-ZipPath` / `-Force` / `-KeepZip`），并打印后续控制台操作步骤。
- **新增** `scripts/dev-with-arms.ps1`：读环境变量 → 组 `-javaagent` + `-Darms.*` →
  `mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=..."`；`-DryRun` 配置自检（密钥打码）、
  `-Background` 后台起 + 等 health UP、`-AllowMissingLicenseKey` 仅验证探针加载；开关关闭或占用 8080 时给清晰提示。
- **改** `scripts/_env.ps1`：新增 `PINLIAO_ARMS_*` 读取 + 三个共享函数
  （`Wait-BackendUp` / `Get-JavaMajor` / `Resolve-PinliaoJavaHome`）+ `Format-Secret`（日志打码）。
- **改** `scripts/db-reset.ps1`：删掉与本文件重复的 JDK 探测与健康检查实现，改用 `_env.ps1` 共享函数
  （行为不变，已复跑核对基线 flyway=8 / companies=16 / jobs=51 / applications=9）。
- **改** `common/web/TraceIdFilter`：优先取活跃 OTel span 的 traceId（`Span.current()`；无 SDK/无 agent 时
  自动返回 invalid span → 回退透传 / UUID，**不抛异常**）；新增依赖 `io.opentelemetry:opentelemetry-api`（版本由 Boot BOM 管）。
- **新增单测** `common/web/TraceIdFilterTest`（6 项）：span 压过请求头 / 无 span 透传 / UUID 兜底 /
  超长丢弃 / 空白重生 / MDC 与响应头一致且请求后清理 MDC。
- **改** `backend/README.md`：新增「ARMS 链路（可选，T-OBS1）」一节。

不动：`pom.xml` 其余依赖、`dev-up.ps1` / `dev-down.ps1`、`db-reset.ps1` 的默认无探针行为、前端任何代码。

**本轮实测记录（2026-10-19，无 LicenseKey）**：

| 项 | 结果 |
|---|---|
| 探针下载与解压 | ✅ 华东1-杭州公网 51 MB → `aliyun-java-agent.jar` 57.9 MB，版本 `5.1.2_dc814b7_20260721082345` |
| 探针身份 | ✅ `Implementation-Version: apsara-java-agent-5.1.2-otel-2.23.0`（OTel javaagent 2.23.0 改名版；jar 内 `inst/` 33697 条、`io/opentelemetry/` 1627 条） |
| 挂载范围 | ✅ 实测 `-Dspring-boot.run.jvmArguments` 生效：fork 出的应用 JVM 命令行含 `-javaagent:...`；Maven JVM **不含** |
| 探针加载 | ✅ 启动日志 `Load Apsara Java Agent version: 5.1.2.`、`report to region: cn-hangzhou`、`Picked up [...\.apsara-apm\java\5.1.2_dc814b7\] as Agent Workspace.` |
| 应用可用性 | ✅ 带探针启动 `/actuator/health` 变 UP，接口正常返回（JDK 21 + Boot 3.5 兼容） |
| 零影响回归 | ✅ `mvnw test` **182/182**；`db-reset.ps1` + 真实后端 `npm run e2e` **63/63** |
| 未授权时的行为 | ⚠️ `status` 文件显示 `licenseKey=null / appId=null / applicationName=null`；**请求期间不产生 span**：日志 MDC 里只有本项目写的 `traceId`（无 `trace_id`/`EagleEye-TraceId`）；`traceparent` 与 `EagleEye-TraceId` 请求头均不被采纳 → 包络 traceId 落回透传/UUID 分支 |
| 结论 | DoD 第 2 条（包络 traceId == ARMS TraceId）**必须配好 LicenseKey 后实测**；风险与备选见 §6 |

## 5. 任务板

| 编号 | 任务 | 产出与验收 | 依赖 | 状态 |
|---|---|---|---|---|
| **T-OBS0** | 控制台前置 | 取到 LicenseKey（+ 应用名 `pinliao-backend`）并写入用户级环境变量；`dev-with-arms.ps1 -DryRun` 自检通过（密钥打码） | 用户操作 | ⏳ 待用户 |
| **T-OBS1** | 脚本 + 探针落地 | 探针就位；`fetch-arms-agent.ps1` / `dev-with-arms.ps1` / `_env.ps1` / README；无 LicenseKey 也能起后端且日志出现探针自述 | 可先做 | ✅ 完成 |
| **T-OBS2** | traceId 打通 + 链路验收 | 包络 `traceId` == ARMS TraceId；ARMS 能看到 HTTP/SQL/Redis span 与错误链路；`npm run e2e` 63/63、`mvnw test` 182/182 | T-OBS0 | ⏳ 待 LicenseKey（代码与单测已就绪） |
| **T-OBS3** | 可选深化 | ① metrics/JVM 开关 ② 慢 SQL / 异常分析 ③ 日志关联（SLS）④ 前端监控（Web SDK，注意 e2e「零控制台错误」门禁） | T-OBS2 | 待定 |

估算：T-OBS1 ≈ 0.5 天（已完成）；T-OBS2 ≈ 0.5 天（拿到 LicenseKey 后）。

## 6. 风险与未决（需用户拍板）

| 项 | 说明 |
|---|---|
| **费用** | ARMS **应用监控**按量计费（有免费额度），计费口径与「链路 OpenTelemetry 版」不同；**需用户确认已了解计费与额度** |
| **DoD 第 2 条的备选口径** | 若拿到 LicenseKey 后 `Span.current()` 仍取不到活跃 span（应用类路径的 OTel API 与探针上下文未连通），按优先级降级：<br>① 读探针注入的 MDC 键（如 `EagleEye-TraceId` / `trace_id`）作为一级来源（改动仅几行）→ 仍满足「包络 traceId 可反查」；<br>② 用探针的 OTel/ARMS 自定义埋点把包络 traceId 作为 **span attribute** 上报，DoD 改为「链路详情里能看到该属性」；<br>③ 放弃自动化对齐，改为「按接口 + 时间 + 错误码在控制台定位」（最弱，不推荐） |
| **地域** | 建议华东1-杭州（探针默认按此下载/上报）；换地域需 `-Region` + 控制台同地域 |
| 探针写入用户目录 | 探针会在 `C:\Users\<你>\.apsara-apm\` 建工作目录（含 `logs/`、`status`），非仓库产物 |
| **前端监控** | ARMS 前端监控会在浏览器发额外请求，**可能破坏 e2e「零控制台错误」门禁** → 若要做，最后做且加 env 开关 |
| 出网要求 | 需能访问 `*.aliyuncs.com`（下载探针；上报按地域域名） |

## 7. 用户侧操作清单（T-OBS0，路径 B）

1. 开通 **ARMS（应用实时监控服务）**：阿里云控制台 → 产品 → 应用实时监控服务（按量计费，见 §6）。
2. 进入 **应用监控 → 应用列表 → 创建应用**，接入方式选 **无侵入接入（Java 探针）**，
   应用名填 `pinliao-backend`（或自定义），**复制 LicenseKey**。
   > 注意：不要选「接入中心 → OpenTelemetry」那张卡片 —— 那是路径 A，会落到「可观测链路 OpenTelemetry 版」。
3. 把 LicenseKey 写入**用户级**环境变量（或直接把值发给我，我按同一套做法写入）：
   ```powershell
   [Environment]::SetEnvironmentVariable('PINLIAO_ARMS_ENABLED',     'true',                'User')
   [Environment]::SetEnvironmentVariable('PINLIAO_ARMS_LICENSE_KEY', '<LicenseKey>',        'User')
   [Environment]::SetEnvironmentVariable('PINLIAO_ARMS_APP_NAME',    'pinliao-backend',     'User')
   ```
4. 新开终端执行 `.\backend\scripts\fetch-arms-agent.ps1`（若尚未下载）→
   `.\backend\scripts\dev-with-arms.ps1 -DryRun` 自检 → `.\backend\scripts\dev-with-arms.ps1 -Background` 启动。
5. 约 1 分钟后在「应用列表」看到应用（语言列 Java 图标），点进去即可看调用链 / 慢 SQL / 异常。

> 安全：LicenseKey 与 `PINLIAO_AI_API_KEY` 同级对待——只进用户级环境变量，仓库只留文档占位；
> 落地后用 `git grep` / 未跟踪文件扫描 / `git log -S` 三连核查零命中（本轮已核查 `PINLIAO_ARMS` 相关零命中）。

## 8. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-10-19 | 首版。基于本机实况（无 Docker、Windows + mvnw、公网开发机、后端已有自研 traceId 与统一包络、e2e 63/63 门禁）制定 ARMS 链路接入方案：路径 A（OTel Java Agent，零代码）为起点，路径 B（ARMS 应用监控）为可选深化；traceId 打通作为验收硬指标；Token 走环境变量；默认开关关闭以保证既有基线不变。任务板 T-OBS0~T-OBS3 |
| v1.1 | 2026-10-19 | **路径改选 B（阿里云 ARMS Java 探针 / 应用监控）**：A 只能进「可观测链路 OpenTelemetry 版」（语言列 `-`，无慢 SQL/JVM/异常分析），B 才是「引 jar 一配就看调用链」的完整体验且配置更简单（一个 LicenseKey）。T-OBS1 骨架落地（`fetch-arms-agent.ps1` / `dev-with-arms.ps1` / `_env.ps1` 共享函数 / `db-reset.ps1` 去重 / `TraceIdFilter` + 6 项单测 / README 一节）；补本轮实测记录（探针 5.1.2 = OTel 2.23.0 改名版、只挂应用 JVM、零影响回归 182/182 与 63/63、未授权时不产生 span）；DoD 第 2 条标注「待 LicenseKey 实测」并列出三级备选口径；环境变量由 `PINLIAO_OTEL_*` 改为 `PINLIAO_ARMS_*` |
