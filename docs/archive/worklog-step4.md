# 工作日志 · Step 4 / 可观测性归档（Task 32~35）

> 归档于 2026-10-20。**默认不要读**；现行日志 [`../../worklog.md`](../../worklog.md)。

---
Task ID: 32
Agent: orchestrator (main)
Task: Step 4 AI / RAG 增量方案定稿（只落文档，不改代码）

Work Log:
- 需求：把招聘平台与 AI 应用结合（RAG），让 HR 能基于候选人简历问答/查询，并借此学习 Spring AI / LangChain4j。
- 先做仓库侦察（结论直接决定方案形态）：
  - 依赖：Spring Boot **3.5.16**（Java 17 target / JBR 21 运行）、MyBatis-Plus、Flyway（已到 V8）、
    Redis、STOMP WebSocket；**无** spring-ai / langchain4j。
  - 基础设施：**无 Docker、无 Ollama**；Redis 为原生 `redis-server.exe`（**无 RediSearch/向量模块**）；
    i5-1135G7 / 16G / 无独显 → 本地跑对话模型不可行，向量可用 API 或小模型。
  - 数据：`resumes.content` 是**结构化 JSON**（basics/educations/experiences/projects/skills）→ 天然
    可做结构化分块；`applications` 带 `company_id` 而 `resumes` 只有 `user_id` → **向量检索必须显式
    做可见性过滤**（不能依赖 `PinliaoTenantLineHandler`）。
  - 复用点：`TalentMatchService`（关键词/权重/证据）= 混合检索的关键词侧与 A/B 基准；
    `usage_logs` = AI 用量埋点；`tenant_configs.features` = 租户级开关；STOMP = 流式备选通道。
  - 门禁：前端 `USE_MOCK` 全局开关 + e2e「零控制台错误」63/63 → AI 必须沿用双分支 + 独立开关。
- 版本核对（2026-10-19）：Spring AI **1.1.x**（1.1.8）对齐 Boot 3.5；Spring AI 2.0（2026-06 GA）需
  **Boot 4 + Jackson 3**，故 AI 增量不与 Boot 4 迁移混做；Boot 3.5 已于 **2026-06-30 EOL**，
  迁移单独开一轮。LangChain4j `spring-boot-starter` 亦支持 Boot 3.5（社区模块含 DashScope）。
- 用户拍板（五问）：① 模型 **通义千问一站式**（对话 + `text-embedding-v4` 1024 维，OpenAI 兼容）；
  ② 向量库 **先 `SimpleVectorStore` 后 MySQL 自建表**；③ **Spring AI 主线 + 事后补 LangChain4j 对照**；
  ④ 第一个能力 **单简历问答**（检索层可复用）；⑤ **先落文档，不动代码**。
- 产出：[`docs/step4-ai-plan.md`](../step4-ai-plan.md) v1.0 —— 目标与 DoD（5 条，含「关开关不影响
  e2e」）、现状约束表、选型（含 Redis 不能当向量库的说明）、能力清单 A~F、入库/查询两条管道与
  结构化分块规则、**多租户与隐私红线 §5.3**、开关/降级/成本、`ai/` 包结构与 SPI 隔离层、
  配置草案（key 走环境变量）、接口草案（43xxx 错误码 / SSE vs STOMP）、V9 表草案、
  T-AI0~T-AI7 任务板（含「是否需要真实 key」列）、**假模型测试策略**、学习路线的六组对比实验、
  风险表与 6 项待确认。
- 登记：`PROGRESS.md` v1.20（§0 快照 + §1 阶段表 + §4 下一步第 4 条 + §8；原「Step 4 联调」更名
  「Step 3-T9 联调」，AI 编号定为 Step 4）；`docs/AI-CONTINUE.md` v1.22（§1 状态一句话、§1 前端开关
  表述、§2 状态表、§9 待确认、§10）；`README.md`（阶段提示 + 文档索引 + 开发路线表）；worklog 本条。
- 未改任何业务代码 / 依赖 / 迁移：`git status` 仅文档变更，故 e2e 与 `mvnw test` 无需重跑。

Stage Summary:
- Step 4 AI / RAG 方案定稿：路线（通义一站式 + 先内存后 MySQL + Spring AI 主线 + 问答先行）与
  T-AI0~T-AI7 任务板、测试策略、租户红线均已书面化；**未开工**，等用户选 T-AI0 或先确认 §10 六项。

---

Task ID: 33
Agent: orchestrator (main)
Task: Step 4 T-AI0 —— 接入 Spring AI + 百炼 DashScope；密钥改走环境变量并打通连通性自检

背景/需求：用户给出 API Key（`sk-ws-…`）与模型名（`qwen3.5-122b-a10b`），要求：
① key 配在**本机系统环境变量**里（一处修改全局生效）；② 配置文件用**引用**方式读取；
③ 明确担心「上传 git 被人发现 key 把我额度用完」。

Work Log:
- 先探明 key 归属与可用性（不猜、实测）：
  - `sk-ws-` 前缀属百炼套餐类 key；查官方文档知 Coding Plan 专属域名为
    `coding.dashscope.aliyuncs.com`。
  - 用 key 实打三组请求：标准兼容模式端点 `dashscope.aliyuncs.com/compatible-mode` 上
    `qwen3.5-122b-a10b` **200**、`qwen-plus` **200**、`text-embedding-v4`（1024 维）**200**；
    `coding.dashscope.aliyuncs.com` **401**（`/compatible-mode` 变体 404）。
  - 结论：**按量计费兼容模式端点即正确入口**，chat 与 embedding 都能用；不要被 `sk-ws-` 前缀
    误导去配 coding 专属域名。
  - 另测思考模式：`qwen3.5-122b-a10b` 带 `reasoning_content`（思考型），加
    `enable_thinking=false` 后 1s 返回、无 reasoning 输出 → 演示默认关思考。
- 密钥落地方式（安全第一）：
  - 6 个变量写入**用户级**环境变量（`[Environment]::SetEnvironmentVariable(k, v, 'User')`）：
    `PINLIAO_AI_API_KEY` / `_BASE_URL` / `_CHAT_MODEL` / `_EMBEDDING_MODEL` / `_EMBEDDING_DIM`（1024）
    / `_ENABLED`（true）；当前会话同步注入以便立即验证。
  - 仓库侧只写引用：`application.yml` 用 `${PINLIAO_AI_API_KEY:pinliao-ai-disabled-placeholder}`
    等占位默认值（**空 api-key 会导致 Spring AI 启动失败**，故必须给占位）。
  - 泄露核查三连（全为空命中）：`git grep -I -l 'sk-ws-'`、`git ls-files --others --exclude-standard`
    + `Select-String`、`git log -p --all -S 'sk-ws-'`；并确认 `.gitignore` 覆盖 `.env*`。
- 代码（新增 `ai/` 包，不动既有包；风格对齐 `dict/` 与 `auth/config/`）：
  - `pom.xml`：`spring-ai-bom:1.1.8`（import）+ `spring-ai-starter-model-openai`（先 HEAD 校验
    Maven Central 有坐标再改，避免解析失败）。
  - `AiCodes`（42220 未启用 / 42221 索引未就绪 / 42902 超配额 / 50020 上游失败）——**修正原方案
    的 `43xxx`**：契约 §5 规定前 3 位=HTTP 状态，`ApiCodes.httpStatus()` 不认 43x，会落 500。
    选号前先 grep 确认 42220/42221/42902/50020 未被占用。
  - `AiProperties`（record, `pinliao.ai.enabled`）+ `AiConfig`（`@EnableConfigurationProperties`）。
  - `AiConnectivityService`（chat + embedding 自检；`extraBody(enable_thinking=false)`、`maxTokens=128`；
    未启用抛 42220 不碰上游）+ `AiController`（`/api/v1/ai/status|ping`，受拦截器保护）。
  - `application.yml`：`spring.ai.openai.*` + `pinliao.ai.enabled`，全部 `${ENV:默认}`。
- 验证：
  - `mvnw -B test-compile` 通过（Spring AI 依赖解析正常）；`mvnw test` **176/176**
    （新增 `AiConnectivityServiceTest` 3 项；`AuthInterceptorPublicEndpointTest` 加 2 条断言确认
    AI 端点非公开）。首跑 1 项失败：Mockito 假响应的 usage 是 0 而非 null → 修断言后全绿。
  - 重启后端（环境变量在进程启动时读取）→ HR 登录取 token → `GET /api/v1/ai/status` 返回
    `enabled=true / qwen3.5-122b-a10b / text-embedding-v4`；`GET /api/v1/ai/ping` 返回
    chat **1005ms / 44 tokens**、embedding **538ms / dim 1024**（端到端 1.56s）。
  - 回归门禁：`db-reset.ps1`（清库重迁）→ `verify-seed.ps1` **16/16** → 真实后端 `npm run e2e`
    **63/63**（含「零控制台错误」），AI 开启态下无回归。
- 顺手修一个真 bug（阻塞了上面的回归门禁）：
  - `db-reset.ps1` 在 **Windows PowerShell 5.1** 下报「未找到 JDK 17+」——`Get-JavaMajor` 里
    `& java -version 2>&1` 因 `_env.ps1` 设了 `$ErrorActionPreference='Stop'` 而产生**终止性**
    `NativeCommandError`，被 `catch` 吞掉 → 恒返回 0（PowerShell 7 不复现，故此前没暴露）。
    修法：探测函数内局部把 `$ErrorActionPreference` 降为 `Continue`（保持 UTF-8 BOM）。
    用文档原命令（`powershell -ExecutionPolicy Bypass -File`）复跑成功：`major=21`、exit=0、
    基线 `flyway=8 / companies=16 / jobs=51 / applications=9`。
- 文档：`docs/step4-ai-plan.md` v1.1（T-AI0 完成 + 实测结论 + 配置改为落地写法 + 错误码修正 +
  模型定案）、`backend/README.md`「AI 配置」一节 + 基线 176/176、`PROGRESS.md` v1.21、
  `docs/AI-CONTINUE.md` v1.23（新增 §6.19「AI 接入」10 条坑）、`CHANGELOG.md`（T-AI0 条目）、
  worklog 本条。

Stage Summary:
- Step 4 T-AI0 完成：Spring AI 1.1.8 + 百炼 DashScope 兼容模式打通（chat + embedding 均实测可用），
  总开关与密钥全部走用户级环境变量；`mvnw test` 176/176、`verify-seed` 16/16、真实后端 e2e 63/63。
- 三条会反复用到的实测结论已写入 `docs/AI-CONTINUE.md` §6.19：`sk-ws-` key 走兼容模式端点可用
  （coding 域名 401）、思考型模型必须 `enable_thinking=false`、空 `api-key` 会导致启动失败（须给占位）。
- 待用户拍板：`step4-ai-plan.md` §10 其余五项（向量维度 1024 vs 1536 / 是否接 Ollama / 配额策略 /
  是否含求职者侧能力 / Boot 4 + Spring AI 2.0 迁移时机）。下一步为 **T-AI1 索引入库**
  （`ResumeChunker` + V9 `resume_chunks` + 重建接口，用假向量模型做单测）。

---
Task ID: 34
Agent: orchestrator (main)
Task: 可观测性 T-OBS1 —— 接入阿里云 ARMS 应用监控探针（骨架落地 + traceId 三级优先级），默认关闭

背景/需求：用户希望先把「链路检查」接上（认为比 AI 增量简单，且方便验收既有功能），
并明确诉求是「像阿里云那样引个 jar、控制台一配就能看调用链」，因此追问「OpenTelemetry Java Agent
是不是阿里云提供的、会不会把后台界面弄得不好看」。

Work Log:
- 选型澄清（先查官方文档再定，避免想当然）：
  - 开源 OTel Java Agent **不是**阿里云提供，是 CNCF/OpenTelemetry 社区产物；接入 ARMS 后落在
    「**可观测链路 OpenTelemetry 版**」——应用列表语言列显示 `-`，只有调用链 / 大盘 / Trace Explorer，
    **没有**慢 SQL / 异常分析 / JVM / 接口大盘。
  - 阿里云自研 **ARMS Java 探针**落在「**应用监控**」——语言列 Java 图标，功能完整。
  - 结论：用户要的体验对应**阿里云探针**（路径 B），且配置更简单（一个 LicenseKey，无需 endpoint/token）。
    方案文档 `docs/arms-plan.md` 升 v1.1，把 v1.0 的「A 为主、B 备选」改为「**B 为主**」。
- 探针落地与事实核验（不猜、实测）：
  - 下载 `http://arms-apm-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/AliyunJavaAgent.zip`（51 MB）→
    `.local/arms/AliyunJavaAgent/aliyun-java-agent.jar`（57.9 MB），版本
    `5.1.2_dc814b7_20260721082345`；`META-INF/MANIFEST.MF` 显示
    `Implementation-Version: apsara-java-agent-5.1.2-otel-2.23.0` → **实为 opentelemetry-javaagent 2.23.0
    的阿里云改名版**（jar 内 `inst/` 33697 条、`io/opentelemetry/` 1627 条）。
  - 挂载方式核验：`spring-boot:run` 默认 fork，用
    `-Dspring-boot.run.jvmArguments=-javaagent:...` 后 WMI 命令行确认 application JVM 有 `-javaagent`、
    Maven JVM 没有；**不用** `JAVA_TOOL_OPTIONS`（否则 Maven 也挂，控制台出现同名重复应用、数据双份）。
- 代码与脚本：
  - 新增 `scripts/fetch-arms-agent.ps1`（`-Region` / `-ZipPath` / `-Force` / `-KeepZip`）、
    `scripts/dev-with-arms.ps1`（`-DryRun` 密钥打码自检 / `-Background` 等 health / `-AllowMissingLicenseKey`；
    校验开关、jar、LicenseKey、8080 占用、路径含空格）。
  - `scripts/_env.ps1` 新增 `PINLIAO_ARMS_*` 与共享函数 `Wait-BackendUp` / `Get-JavaMajor` /
    `Resolve-PinliaoJavaHome` / `Format-Secret`；`db-reset.ps1` 删掉重复实现改用它（行为不变）。
  - `TraceIdFilter` 取值改为三级优先级「活跃 OTel span → 透传 `X-Trace-Id` ≤64 → UUID」；
    新增依赖 `io.opentelemetry:opentelemetry-api`（版本由 Boot BOM 管，3.5.16 → 1.49.0）；
    新增 `TraceIdFilterTest` 6 项（用 `Span.wrap(...).makeCurrent()` 模拟挂了 agent，不联网）。
- 验证：
  - `mvnw test` **182/182**（176 + 6）；`db-reset.ps1`（重构后）基线不变
    `flyway=8 / companies=16 / jobs=51 / applications=9`；真实后端 `npm run e2e` **63/63**（零影响）。
  - 探针加载态启动：日志 `Load Apsara Java Agent version: 5.1.2.` / `report to region: cn-hangzhou` /
    `Picked up [...\.apsara-apm\java\5.1.2_dc814b7\] as Agent Workspace.`，`/actuator/health` UP、接口正常。
  - **重要负面结论（诚实记录）**：未配 LicenseKey 时探针**不产生 span** ——
    ① 探针 `status` 文件 `licenseKey=null / appId=null / applicationName=null`；
    ② 送 W3C `traceparent` 包络不回显（ARMS 用 EagleEye 协议）；
    ③ 送 `EagleEye-TraceId` 头也不被采纳；
    ④ 用 `%mdc` / `%X{trace_id:-}` 临时日志格式（环境变量 `LOGGING_PATTERN_CONSOLE` 覆盖，零代码改动）
    打请求，发现 MDC 里**只有本项目写的 `traceId`**，探针没注入任何键；
    ⑤ 送 `X-Trace-Id=mycustomtraceid123` 包络原样回显 → 证明 span 分支未命中（走的是透传分支）。
    → 故 DoD「包络 traceId == ARMS TraceId」**必须等 T-OBS0 拿到 LicenseKey 后实测**；已在方案 §6
    列出三级备选口径（读探针 MDC 键 / 用自定义埋点把包络 traceId 作 span attribute 上报 / 退化为按接口+时间定位）。
  - 泄露核查：探针静置在 `.local/`（gitignore）；`PINLIAO_ARMS` 相关在仓库零命中。
- 文档：`docs/arms-plan.md` v1.1、`backend/README.md`「ARMS 链路（可选）」+ 脚本表 2 行 + 基线 182/182、
  `PROGRESS.md` v1.22（快照 / 阶段表 / 下一步第 5 条 / §7.2 实测表 / 修订记录）、
  `docs/AI-CONTINUE.md` v1.24（新增 §6.20「可观测性 / ARMS 接入」12 条坑）、`CHANGELOG.md`、worklog 本条。

Stage Summary:
- 可观测性 T-OBS1 完成：ARMS 应用监控探针骨架（下载 + 启动脚本 + 环境变量 + traceId 打通代码 + 单测 + 文档），
  默认关闭且对既有基线零影响（`mvnw test` 182/182、真实后端 e2e 63/63）。
- 三条最有复用价值的结论：①「引 jar 看调用链」= ARMS 应用监控 = 阿里云探针（开源 OTel Agent 会落链路 OTel 版）；
  ② `-javaagent` 只能挂应用 JVM（`spring-boot.run.jvmArguments`，绝不用 `JAVA_TOOL_OPTIONS`）；
  ③ 无 LicenseKey 时探针不产生 span，`EagleEye-TraceId` / `traceparent` 都不认。
- 阻塞点（需用户操作 T-OBS0）：控制台「应用监控 → 应用列表 → 创建应用（无侵入 Java）」取 LicenseKey
  → 写入用户级环境变量 `PINLIAO_ARMS_LICENSE_KEY` → `dev-with-arms.ps1` 启动 → 核对链路与 traceId 对齐。
- 仍待用户拍板：ARMS 计费口径与额度；`step4-ai-plan.md` §10 五项；备份分支是否删除。

---

Task ID: 35
Agent: orchestrator (main)
Title: 可观测性 T-OBS2 —— ARMS 链路打通 + 修 `-Background` 引号 bug（带探针 e2e 63/63）
Status: 完成（2026-10-19）

Context:
- 用户从控制台取到 LicenseKey（形如 `a61t***(26 位)`，地域 cn-hangzhou），并提示了三行启动参数
  （`-javaagent` + `-Darms.licenseKey` + `-Darms.appName=my-service`），又贴了「手动安装」页说明。
- 选择：控制台**方法一**（`-javaagent` + `-Darms.*`）；方法二仅对 <4.0.0 有效（本机 5.1.2 不适用）。
  应用名按控制台原话「请将 my-service 替换成您自己的应用名」定为 `pinliao-backend`。

Root Cause（本轮最关键的发现）:
- 首轮 `dev-with-arms.ps1 -Background` 起来后：探针横幅正常、health UP，但控制台看不到应用、请求不产生 span。
  定位到 **`Start-Process -ArgumentList <数组>` 按空格拼接、丢掉元素内引号** →
  `-Dspring-boot.run.jvmArguments="-javaagent:… -Darms.appName=… -Darms.licenseKey=…"` 被 Maven 拆开，
  应用 JVM 只收到 `-javaagent`，`-Darms.*` 变成了 Maven 自己的系统属性 → 探针**无 LicenseKey = 不产生 span**。
  修法：先拼成**单个带引号的参数字符串**再传给 `Start-Process`。
- 另一处误导性证据：`.apsara-apm\java\<版本>\status` 文件**不刷新**——甚至链路已通、控制台已可见时它仍显示
  `licenseKey=null`；因此明确「status 不可作授权判据」，改立**判活三条判据**。

Work:
- 环境变量（用户级）写入：`PINLIAO_ARMS_ENABLED=true` / `PINLIAO_ARMS_LICENSE_KEY=<key>` /
  `PINLIAO_ARMS_APP_NAME=pinliao-backend`；`-DryRun` 自检通过（密钥打码 `a61t***(26 位)`）。
- 修 `backend/scripts/dev-with-arms.ps1`（Background 分支的引号 bug，附注释说明为何不能传数组）。
- 诊断过程中顺手排除两个假线索：
  ① `OTEL_JAVAAGENT_LOGGING=debug` 是**非法值** → 探针打印 `The agent will use the no-op implementation.`，
     之后连横幅都不打（容易被误判成「探针没加载」）；正确开关是
     `-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug`。
  ② 早前「应用 JVM 零对外连接」是抓错 pid（WMI 匹配 `javaagent` 会同时命中 Maven JVM）；按「监听 8080 的 pid」
     重查后能看到 MySQL/Redis 连接。
- 验证：带探针重新启动后
  ① 应用 JVM 命令行含 `-javaagent` + `-Darms.appName` + `-Darms.licenseKey`（WMI 回读）；
  ② 送 `X-Trace-Id=mycustomtraceid123` → 包络**不回显**，返回探针 traceId（`18c2a41966c9f7e920b455c6fa4d1aa6`）→ span 分支命中；
  ③ 应用 JVM 出现到阿里云 80 端口的 Established 连接（`121.43.177.209:80` / `47.98.97.63:80` / `120.55.220.55:80` / `47.97.242.7:80`），
     `100.103.107.x:9990` 为 EagleEye 配置通道（SynSent，本机网络阻断，不影响链路数据）；
  ④ 用户确认「当前已经能够在阿里云控制台查看本服务了」。
- 回归（**带探针**）：`db-reset.ps1` → `flyway=8 / companies=16 / jobs=51 / applications=9` → `npm run e2e` **63/63**。
- 出参加样：GET `/api/v1/cities` → `18c2a41966c9f7e920b455c6fa4d1aa6`；GET `/api/v1/jobs` → `dd907c79fe631ee168ddea44f8713311`。
- 文档：`docs/arms-plan.md` v1.2（记录 B + 判活三条判据 + 任务板 T-OBS0/T-OBS2 ✅ + 备选口径降级）、
  `docs/AI-CONTINUE.md` §6.20 补 5 条坑、`backend/README.md`（已打通口径 + 脚本表修复说明 + 带探针 e2e）、
  `PROGRESS.md` v1.23、`CHANGELOG.md`、worklog 本条。

Stage Summary:
- ARMS 链路打通：控制台可见 `pinliao-backend`，包络 traceId 即链路 TraceId（span 分支命中），有实际上报连接；
  带探针 e2e 63/63，确认对既有功能零影响。
- 最有复用价值的坑：**PowerShell `Start-Process -ArgumentList` 数组会丢引号** —— 这是「javaagent 挂了却没数据」的头号原因；
  排查入口永远是「回读应用 JVM 命令行」。次坑：`status` 文件不刷新，别拿它下结论。
- 下一步（可选 T-OBS3）：慢 SQL / 异常分析 / JVM 大盘核对；前端监控（Web SDK）最后做并加开关（会碰 e2e 零控制台错误门禁）。
- 仍待用户拍板：ARMS 计费与额度；`step4-ai-plan.md` §10 五项；备份分支是否删除；LicenseKey 已在本会话明文出现，
  如介意可在控制台重置后重新写入环境变量。

---

Task ID: 36
Agent: orchestrator (main)
Title: 进度存档 + README 展示更新（方便后续接续）
Status: 完成（2026-10-19）

Work:
- `README.md`（仓库根）：头部改为真实状态（Step 2/3 完成、T-AI0 ✅、T-OBS1+T-OBS2 ✅）；新增「进度速览」表；
  技术栈行补 ARMS 已打通；后端命令区基线 176/176 → **182/182**，并补 `dev-up.ps1` 首次初始化提示；
  新增「可观测性（阿里云 ARMS，可选）」小节（fetch / DryRun / Background 三步 + 控制台反查 + 默认关闭 + 判活提示）；
  项目结构补 `backend/` 目录树；文档索引与「开发路线」表更新到 T-OBS2 完成；新增「接下来的推进建议」；
  接续提示词改为当前真实状态。
- `PROGRESS.md`：新增 §0.1「接续即用」（HEAD/远端、运行态、已验基线、环境变量、恢复干净环境命令、
  下一步推荐序、判活自检入口）；修订记录 v1.24。
- `CHANGELOG.md`：T-OBS2 条目的 Docs 分组补 README / PROGRESS §0.1。
- 说明：本轮只改文档，无代码变更，不重跑测试（上一轮刚跑过 `mvnw test` 182/182、带探针 e2e 63/63）。

Stage Summary:
- 进度已落盘到「换个人/换个 AI 打开仓库就能续做」的程度：README 负责展示，PROGRESS §0.1 负责交接，
  arms-plan §4 与 AI-CONTINUE §6.20 负责防重复踩坑。
- 下一步仍是 Step 4 T-AI1 索引入库（主线）或 T-OBS3（可观测性深化，可选）。

---

Task ID: 37
Agent: orchestrator (main)
Title: Step 4 T-AI1 简历索引入库（V9 + 分块 + 嵌入 + 重建 / 状态接口）
Status: 完成（2026-10-20）

Work:
- **表**：`V9__ai_resume_chunks.sql` 新增 `resume_chunks`（`resume_id/section/ordinal/title/content/content_hash/model/dim/embedding`，
  唯一键 `(resume_id, section, ordinal, model)`，`resume_id` 外键级联删除；**不冗余 `company_id`**）。
- **分块**：`ResumeChunker` 结构化五段式（basics / educations / experiences / projects / skills），
  块首带上下文标题；单块 800 字上限按句续写「（续 n）」；**手机号等联系方式不入库**。
- **向量**：自研 SPI `AiEmbeddingModel`；`SpringAiEmbeddingAdapter`（`text-embedding-v4`，1024 维，DashScope ≤10 条切批，
  异常转 `50020`）+ `FakeEmbeddingModel`（中文单字 / 拉丁词的确定性词袋哈希 + L2 归一化，零费用）；
  按 `content_hash + model` 复用旧向量，换 provider / 模型不复用。
- **服务 / 接口**：`ResumeIndexService`（全量 / 单份 / 状态 + `rebuildOne` 供问答按需调用）、
  `POST /api/v1/ai/index/rebuild`（HR / 管理员）、`GET /api/v1/hr/applications/{id}/ai/index-status`
  （按投递校验可见性，跨公司 40401）。
- **冒烟**：`smoke-ai.mjs` 17 项（权限 / 全量 / 幂等 / 单份 / 状态 / 跨公司），带真实上游安全闸（`--allow-real` 才跑真实）。
- **实测**：fake 7 份简历 35 块、二次 `embedded=0`；真实 `text-embedding-v4` 单份 5 块 950ms、全量 30 块 2.1s、1024 维；
  `mvnw test` **199/199**（新增 16 项）；`verify-seed` 16/16（flyway=9）。

Stage Summary:
- 索引管道打通且可重复执行不重复计费；分块针对结构化简历 JSON，天然优于定长切分。
- 关键红线：索引表不带租户列，检索必须先经 `applications.company_id` 取可见 `resumeId`。
- 下一步 T-AI2：检索 topK → Prompt → 回答 + `[n]` 引用，并在 HR 候选人详情接 AI 抽屉。

---

Task ID: 38
Agent: orchestrator (main)
Title: Step 4 T-AI2 单简历问答 MVP（带引用）+ HR 候选人详情 AI 助手抽屉
Status: 完成（2026-10-20）

Work:
- **对话 SPI**：`AiChatClient`；`SpringAiChatAdapter`（`enable_thinking=false`、token 上限，失败转 `50020`）+
  `FakeChatClient`（固定模板 + 引用 `[1]`，零费用）。
- **RAG**：`RetrievalService`（可见 `resumeId` 范围 + 内存余弦 topK / `qaMinScore`）、
  `PromptBuilder`（资料与指令分区、资料声明为纯数据、无依据固定话术）、
  `CitationAssembler`（解析 `[n]`、合法性过滤、按首次出现去重、漏标兜底 `来源：[1]`）。
- **编排**：`HrResumeQaService`：开关 42220 → HR/管理员 40301 → 问题 1~500 字 42201 →
  投递归属 40401 → 配额 → 索引缺失自动 `rebuildOne` → 检索 → 低分直接「简历中没有相关信息。」不调模型 →
  生成 → 引用；审计只记长度 / 条数 / token / 耗时，不落原文。
- **配额**：`AiQuotaService` 按 HR / 自然日 Redis 计数（默认 100，`42902`；Redis 故障降级放行）。
- **接口**：`POST /api/v1/hr/applications/{applicationId}/ai/qa`（`AiQaController`）。
- **前端**：`src/types/api.ts` 新增 `AiStatus/AiCitation/AiQaAnswer`；`src/api/ai.ts` 双分支；
  `src/api/mock/ai.ts` 按段落意图 + 原文命中生成带引用回答（无命中同样「简历中没有相关信息。」）；
  `HrAiAssistantSheet`（BottomSheet：快捷问题 / 对话 / 引用卡片 / 错误态 / 免责声明）；
  候选人详情新增「AI 简历助手」入口，按 `/ai/status.enabled` 显隐（查询失败也不显示）。
- **冒烟 / e2e**：`smoke-ai.mjs` 扩到 **31/31**；`tests/e2e-verify.cjs` 新增 2 项（入口与开关一致、问答带引用），
  63 → **65**。
- **实测**：`mvnw test` **230/230**（新增 31）；e2e 真实后端（`provider=fake`）65/65、Mock 65/65、
  AI 关闭 64/64（入口隐藏、问答用例跳过）；关闭时 `/ai/qa` → `422/42220`。
- **坑**：CDP `eval` 外层同步 `JSON.stringify`，不能跑 async IIFE（探针曾得到 `[object Object]`），
  改为 Node 侧登录后探测；fake 向量无语义，e2e 问题需强字面重合。

Stage Summary:
- 单简历问答闭环完成：回答有引用、无依据会承认、跨租户零泄漏、关 AI 不影响任何既有功能。
- 费用保护三层：fake 单测 / 冒烟默认拒绝真实上游 / 低分不调模型 + 每日配额。
- 下一步 T-AI3 流式输出（SSE / STOMP + 打字机 + 引用点击定位）；T-AI4 语义人才检索。

---

