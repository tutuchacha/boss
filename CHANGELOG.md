# 聘聊 · 变更记录（CHANGELOG.md）

> 记录**重要代码与文档变更**，含未提交的工作区增量。
> 格式约定：版本（或「未提交」）→ Added / Changed / Fixed / Docs 分组；
> 新条目追加在顶部（最新在上）。提交历史另见 `git log`。

---

## [Step 4 · T-AI3] 2026-10-20 — SSE 流式问答 + 打字机 + 引用双向定位

> 目标：把「等 1~2 秒一次性出结果」变成「边生成边读」，并让引用可点击定位；
> 流式不可用时自动退回非流式，保证「打字机不可用 ≠ 问答不可用」。

### Added

- **后端流式**：`AiChatClient.stream(system, user, Consumer<String>)`；`SpringAiChatAdapter.stream`
  （Spring AI `Flux` → `toIterable()`，跳过空块，空流 / 异常转 `50020`）/ `FakeChatClient.stream`
  （8 字一段 + 30ms，拼接结果与非流式一致）；`HrResumeQaService` 拆 `prepare()` / `answer()` /
  `stream(Prepared, AiStreamSink)`，校验顺序与非流式完全共用；`AiQaViews.StreamDelta` / `StreamError`；
  `AiStreamSink`；`AiConfig.aiStreamExecutor`（固定 4 线程 daemon）。
- 新接口 `POST /api/v1/hr/applications/{applicationId}/ai/qa/stream`（`SseEmitter` 120s，
  `Cache-Control: no-cache`、`X-Accel-Buffering: no`）：`delta` → `citation` → `done`；
  校验类错误（42220 / 40301 / 42201 / 40401 / 42902）仍在推流前返回普通 JSON 包络。
- **前端**：`client.requestStream` / `toApiError`（抽出 `send()`）；`api/ai.askResumeQuestionStream`
  + SSE 解析器（`\r\n` 归一、多行 `data:`、未收到 `done` 自动退回非流式）；`mock/ai.ts` 的
  `mockAskResumeQuestionStream`（4 字 / 18ms）；`HrAiAssistantSheet` 打字机 + 光标、引用逐条渲染、
  `[n]` ↔ 来源卡片双向 `scrollIntoView` + 1.2s 高亮、错误时保留已输出部分。
- **Next 流式代理** `src/app/api/ai/qa-stream/[applicationId]/route.ts`：用 Web Streams 透传
  `upstream.body`，绕过 `next.config.ts` rewrites 对 SSE 的缓冲。

### Changed

- `client.buildUrl`：以 `/api/` 开头的路径视为**站点根路径**，不再拼 `API_BASE_URL`（默认 `/api/v1`）。
- `AuthInterceptor` 改 `AsyncHandlerInterceptor`，在 `afterConcurrentHandlingStarted` 清 `UserContext`，
  避免 SSE 异步请求的 ThreadLocal 泄漏。
- SSE 客户端（前端 / 代理 / 冒烟）`Accept` 改为 `text/event-stream, application/json`；
  只接受 `text/event-stream` 时 Spring 写不出错误包络（`HttpMediaTypeNotAcceptableException` → 500 空响应）。
- `smoke-ai.mjs` 从 31 项扩到 **43 项**（新增 SSE 校验类错误 / 事件序列 / done 一致性 / 无依据）。

### Docs

- `docs/api-endpoints.md` 新增 §14.6（SSE 流式问答，契约外）；`docs/step4-ai-plan.md` v1.4（T-AI3 ✅ +
  实测记录 + 代理决策）；`docs/AI-CONTINUE.md` §6.22（流式 7 条坑）；`PROGRESS.md` §0 / §0.1 / §1 /
  §2.3 / §4 / §7.2；`README.md` / `backend/README.md` 基线数字；`worklog.md` Task 39。

### Verified

- `mvnw test` **240/240**（新增 10 项，全部假模型，不联网 / 不花钱）。
- `smoke-ai.mjs` **43/43**（`provider=fake`，可重复执行）。
- `npm run e2e` **65/65**：真实后端 + fake AI provider 一轮、Mock 分支一轮；e2e 不依赖流式内容。
- 链路：直连 :8080 ttfb 151ms / delta 逐条 152-279ms；经代理 ttfb 54ms / 逐条 55-189ms；
  浏览器打字机 109 / 167 / 219ms 递增；引用双向定位与 1.2s 高亮、断网错误态、流截断自动兜底均实测通过。

---

## [Step 4 · T-AI2] 2026-10-20 — 单简历问答（带引用）+ HR 候选人详情 AI 助手抽屉

> 本条目同时补记 **T-AI1 索引入库**（2026-10-20 落地，原 CHANGELOG 遗漏）与 **T-AI2 单简历问答**。
> 目标：HR 在候选人详情就能「就这份简历提问」，回答必须能溯源；没有依据时必须承认不知道。

### Added

- **T-AI1 索引入库**：V9 `resume_chunks`（`section/ordinal/title/content/content_hash/model/dim/embedding`，
  唯一键 `(resume_id, section, ordinal, model)`）；`ResumeChunker` 五段式结构化分块（上下文头、800 字按句续写、
  **手机号等联系方式不入库**）；`AiEmbeddingModel` SPI + `SpringAiEmbeddingAdapter`（`text-embedding-v4`，
  ≤10 条切批）/ `FakeEmbeddingModel`（确定性词袋向量）；`POST /api/v1/ai/index/rebuild`（全量 / 单份）与
  `GET /api/v1/hr/applications/{id}/ai/index-status`。
- **T-AI2 问答链路**：`AiChatClient` SPI + `SpringAiChatAdapter`（`enable_thinking=false`，失败转 50020）/
  `FakeChatClient`；`RetrievalService`（`resumeId` 先经 `applications.company_id` 前置过滤 + 内存余弦 topK）；
  `PromptBuilder`（资料 / 指令分区 + 无依据固定话术）；`CitationAssembler`（解析 `[n]`、去重、模型漏标兜底
  `来源：[1]`）；`HrResumeQaService`（开关 / 角色 / 归属 / 首次提问按需建索引 / 低分不调模型）；
  `AiQuotaService`（HR/自然日 100 次，`42902`，Redis 故障降级放行）。
- 新接口 `POST /api/v1/hr/applications/{applicationId}/ai/qa`（问题 1~500 字；无依据时
  `answer="简历中没有相关信息。"`、`grounded=false`、`citations=[]`）。
- 前端：`src/api/ai.ts`（`getAiStatus` / `askResumeQuestion`，`USE_MOCK` 双分支）、`src/api/mock/ai.ts`
  （段落意图 + 原文命中的规则回答）、`HrAiAssistantSheet`（快捷问题 / 对话 / `[n]` 引用卡片 / 错误态）、
  候选人详情「AI 简历助手」入口（按 `/ai/status.enabled` 显隐）。
- `docs/api-endpoints.md` 新增 §14「AI 模块（契约外扩展）」共 5 条接口（仍不计入 70 条账目）。
- `docs/AI-CONTINUE.md` §6.21（问答 / 引用 12 条坑，含 CDP `eval` 不能跑 async 的 e2e 探针坑）。

### Changed

- `AiProperties` 扩为 8 参 record：新增 `qaTopK` / `qaMinScore` / `qaMaxTokens` / `qaDailyLimit`，
  `application.yml` 对应 `pinliao.ai.qa-*` 环境变量（`PINLIAO_AI_QA_*`）。
- `smoke-ai.mjs` 从 17 项扩到 **31 项**：新增问答权限 / 入参 / 跨公司 / 带引用回答 / 无依据 / 问答后索引状态。
- `tests/e2e-verify.cjs` 从 63 项扩到 **65 项**：新增「AI 入口与开关一致」「AI 问答返回带引用的回答」。
- `backend/README.md`：AI 接口清单补 T-AI1/T-AI2 四条、配置补 `PROVIDER` / `QA_*`、脚本表补 `smoke-ai.mjs`、
  基线更新为 `mvnw test` 230/230、`smoke-ai` 31/31、e2e 65/65。

### Docs

- `docs/step4-ai-plan.md` v1.3：T-AI2 标 ✅、补实测记录、§8 假模型名更正为 `FakeChatClient`、e2e 门禁改 65。
- `PROGRESS.md` §0 / §0.1 / §1 / §2.2 / §7 同步；`worklog.md` 追加 Task 37 / Task 38。

### Verified

- `mvnw test` **230/230**（新增 31 项，全部假模型，不联网 / 不花钱）。
- `smoke-ai.mjs` **31/31**（`provider=fake`，可重复执行）。
- `npm run e2e` **65/65**：真实后端 + fake AI provider 一轮；Mock 分支一轮；AI 关闭时 **64/64**
  （入口隐藏、问答用例跳过），其余功能零回归。
- AI 关闭实测：`/ai/status` → `enabled=false`，`/ai/qa` → **422 + 42220**，前端无入口。

---

## [可观测性 · T-OBS2] 2026-10-19 — ARMS 链路打通（修 `-Background` 引号 bug，带探针 e2e 63/63）

> 用户已从控制台取得 LicenseKey 并反馈「当前已经能够在阿里云控制台查看本服务了」。
> 本轮目标从「骨架」转为**真实验收**：探针是否产生 span、上报是否连通、traceId 是否对齐、探针是否影响功能。

### Fixed

- **`backend/scripts/dev-with-arms.ps1 -Background` 的致命引号 bug**：原来用
  `Start-Process -ArgumentList 'spring-boot:run', "-Dspring-boot.run.jvmArguments=$jvmArgs"`，
  PowerShell 对数组只做**空格拼接**、不给含空格的元素补引号，命令行变成
  `spring-boot:run -Dspring-boot.run.jvmArguments=-javaagent:… -Darms.appName=… -Darms.licenseKey=…`，
  Maven 只把 `-javaagent:…` 收进 `jvmArguments`，其余 `-Darms.*` 成了 Maven 自己的系统属性。
  表现为**探针横幅正常、health 正常，但控制台看不到应用、请求不产生 span**（极具误导性）。
  改为先拼**单个带引号的参数字符串**再交给 `Start-Process`；已用 WMI 回读应用 JVM 命令行确认
  `-javaagent` / `-Darms.appName` / `-Darms.licenseKey` 三者都在。

### Added

- `docs/arms-plan.md` §4 新增「**判活三条判据**」（本地自检，不必进控制台）：
  ① 应用 JVM 命令行含 `-Darms.*`；② 送 `X-Trace-Id=mycustomtraceid123` 时包络**不回显**而是 32 位 hex；
  ③ 应用 JVM 有到阿里云 **80** 端口的 Established 连接。并记录 `100.103.107.x:9990` 的 SynSent 属
  EagleEye 配置通道被阻断，不影响链路数据。

### Docs

- `docs/arms-plan.md` v1.2：补「配好 LicenseKey 后」实测记录（控制台可见 / span 分支命中 / 上报连接 /
  带探针 e2e 63/63 / `status` 文件不刷新 / 引号 bug）；任务板 T-OBS0、T-OBS2 标 ✅；
  备选口径降级为「换环境时的兜底」；§7 增加第 6 步 traceId 对照自检。
- `docs/AI-CONTINUE.md` §6.20 补 5 条坑（`-Background` 引号、`status` 不可信、判活三条判据、
  两种传参形式、`OTEL_JAVAAGENT_LOGGING=debug` 非法值会让日志实现变 no-op 连横幅都不打）。
- `backend/README.md`：ARMS 一节改为「已打通」口径 + 脚本表注明修复。
- `README.md`（仓库根）：改为真实状态展示 —— Step 2/3 已完成、T-AI0 ✅、T-OBS1+T-OBS2 ✅；
  新增「进度速览」表、可观测性快捷用法小节、基线 `mvnw test` 182/182、下一步推进建议、接续提示词更新。
- `PROGRESS.md` v1.23/v1.24（新增 §0.1「接续即用」：HEAD / 运行态 / 基线 / 环境变量 / 恢复命令 / 下一步）。
- `CHANGELOG.md` 本条、`worklog.md` Task 35 / Task 36。

### Verified

- 带探针 `db-reset.ps1` → 基线不变（`flyway=8 / companies=16 / jobs=51 / applications=9`）；
  随后**带探针**跑真实后端 `npm run e2e` → **63/63**（零控制台错误、零 hydration 报错）。
- 包络 traceId 样例：`18c2a41966c9f7e920b455c6fa4d1aa6`（GET `/api/v1/cities`）、
  `dd907c79fe631ee168ddea44f8713311`（GET `/api/v1/jobs`）——可在控制台按 TraceId 反查同一条链路。

---

## [可观测性 · T-OBS1] 2026-10-19 — 接入阿里云 ARMS 应用监控（探针骨架 + traceId 三级优先级，默认关闭）

> 用户诉求：先把**链路检查**接上（比 AI 增量简单，也方便验收既有功能）；
> 并明确要「引个 jar、阿里云一配就能看调用链」的**应用监控**体验 —— 因此弃用开源 OTel Agent（只会落在
> 「可观测链路 OpenTelemetry 版」，语言列显示 `-`，没有慢 SQL / 异常分析 / JVM），改选**阿里云 ARMS Java 探针**。

### Added

- `backend/scripts/fetch-arms-agent.ps1`：下载/解压 ARMS 探针到 `.local/arms/AliyunJavaAgent/`
  （默认华东1-杭州公网 `http://arms-apm-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com/AliyunJavaAgent.zip`，
  约 51 MB → `aliyun-java-agent.jar` 57.9 MB；支持 `-Region` / `-ZipPath` / `-Force` / `-KeepZip`），
  并打印控制台取 LicenseKey 的后续步骤。
- `backend/scripts/dev-with-arms.ps1`：带探针启动后端。`-DryRun` 配置自检（LicenseKey 打码）、
  `-Background` 后台起 + 日志 `.local/backend-arms.log` + 等 `/actuator/health` UP、
  `-AllowMissingLicenseKey` 仅验证探针加载；校验开关 / 探针 jar / LicenseKey / 8080 占用，并提示下一步。
- `backend/src/test/java/.../common/web/TraceIdFilterTest.java`：6 项单测（span 压过请求头 / 无 span 透传 /
  UUID 兜底 / 超长丢弃 / 空白重生 / MDC 与响应头一致且请求后清理）——不依赖 Agent 与网络。
- `docs/arms-plan.md` v1.1：路径选型（A 开源 OTel Agent vs **B 阿里云 ARMS 探针** vs C Micrometer）、
  架构与数据流、环境变量、改动清单、T-OBS0~T-OBS3 任务板、**本轮本机实测记录**、风险与未决（含
  DoD 第 2 条的三级备选口径）、用户侧控制台操作清单。
- `backend/README.md`「ARMS 链路（可选，T-OBS1）」一节（探针下载 / 环境变量 / 启动命令 / 6 条已知要点）。

### Changed

- `backend/src/main/java/.../common/web/TraceIdFilter.java`：traceId 取值改为三级优先级
  **「活跃 OTel Span 的 traceId → 透传 `X-Trace-Id`（≤64）→ UUID 兜底」**，使响应包络 `traceId`
  与 ARMS 链路详情中的 TraceId 一致（OTel traceId 同为 32 位小写 hex，**契约 §4.1 不变**；
  `tests/e2e-verify.cjs` 不校验 traceId）。无 Agent 时 `Span.current()` 为 invalid span，**不抛异常**、行为同改造前。
- `backend/pom.xml`：新增 `io.opentelemetry:opentelemetry-api`（版本由 Spring Boot BOM 管理，
  3.5.16 → 1.49.0，**未自定版本**）。
- `backend/scripts/_env.ps1`：新增 `PINLIAO_ARMS_*` 读取（`ENABLED` / `LICENSE_KEY` / `APP_NAME` /
  `AGENT_HOME`）与共享函数 `Wait-BackendUp` / `Get-JavaMajor` / `Resolve-PinliaoJavaHome` / `Format-Secret`。
- `backend/scripts/db-reset.ps1`：删除与本文件重复的 JDK 探测与 health 轮询实现，改用 `_env.ps1` 共享函数
  （行为不变，已复跑核对基线 `flyway=8 / companies=16 / jobs=51 / applications=9`）。
- 文档基线同步：`mvnw test` **176/176 → 182/182**（新增 traceId 优先级 6 项）。

### Fixed

- 新增的两个脚本补上 **UTF-8 BOM**：Windows PowerShell 5.1 对无 BOM 的 `.ps1` 按 ANSI（GBK）解码中文，
  导致全角括号被拆坏、脚本直接解析失败（`The string is missing the terminator` /
  `Missing closing ')' in subexpression`，PowerShell 7 不复现）。仓库内脚本统一「UTF-8 with BOM + LF」，
  已在 §6.20 记为坑并双双复跑通过。

### Docs

- `PROGRESS.md` v1.22、`README.md`（阶段表 / 文档索引 / 后端行）、`docs/AI-CONTINUE.md` v1.24
  **新增 §6.20「可观测性 / 阿里云 ARMS 接入」12 条坑**（两产品入口区别、探针实为 OTel javaagent 2.23.0 改名版、
  只能挂应用 JVM、`jvmArguments` 空格坑、探针工作目录与 `status` 文件、未配 LicenseKey 不产生 span、
  `%mdc` 探针法、OTel API 安全降级、计费与前端监控门禁）、`worklog.md` Task 34。

### Verified

- `mvnw test` **182/182**（原 176 + 6）。
- 探针实测：`Load Apsara Java Agent version: 5.1.2.` / `report to region: cn-hangzhou`；
  `-javaagent` **只出现在 fork 出的应用 JVM**（WMI 核对 Maven JVM 无）；带探针启动 `/actuator/health` UP。
- 零影响：`db-reset.ps1`（无探针）→ `npm run e2e` **63/63**（真实后端）。
- 未决：未配 LicenseKey 时探针不产生 span（`status` 文件 `licenseKey=null`；`traceparent` / `EagleEye-TraceId`
  头均不被采纳），故「包络 traceId == ARMS TraceId」须待 T-OBS0 取到 LicenseKey 后实测。

---

## [Step 4 · T-AI0] 2026-10-19 — AI 接入：Spring AI + 百炼 DashScope 连通性打通（密钥走环境变量）

> 用户要求：API Key 配在本机**环境变量**里（方便统一修改），配置文件用引用方式读取，
> **绝不能进 git**（怕被人拿去用光额度）。

### Added

- `backend/pom.xml`：`spring-ai-bom:1.1.8`（`import`）+ `spring-ai-starter-model-openai`
  （OpenAI 兼容协议 → 阿里云百炼 DashScope）。1.1.x 对齐 Boot 3.5；2.0.x 需 Boot 4 + Jackson 3，勿混用。
- `backend/src/main/java/com/pinliao/backend/ai/`（新增包，不动既有包）：
  - `AiCodes.java`：AI 模块错误码 `42220`（未启用）/ `42221`（索引未就绪）/ `42902`（超配额）/
    `50020`（上游失败或超时）。**不能用原方案草案的 `43xxx`**——契约 §5 规定「前 3 位 = HTTP 状态」，
    `ApiCodes.httpStatus()` 只识别 400/401/403/404/409/422/429/500。
  - `config/AiProperties.java`（record，绑定 `pinliao.ai.enabled`）+ `config/AiConfig.java`。
  - `service/AiConnectivityService.java`：自检 = 一次 chat + 一次 embedding；chat 固定
    `extraBody(enable_thinking=false)`、`maxTokens=128`；未启用时直接抛 `42220`（不触碰上游）。
  - `web/AiController.java`：`GET /api/v1/ai/status`（只回显配置）、`GET /api/v1/ai/ping`
    （真实调用，返回模型 / 耗时 / token / 向量维度）。两者**受鉴权拦截器保护**，避免未授权调用扣费。
- `backend/src/test/java/.../ai/service/AiConnectivityServiceTest.java`：3 项不联网单测
  （关闭→42220 且不调上游 / 开启→回显模型与维度 / 开启→关思考模式 + token 上限）。

### Changed

- `backend/src/main/resources/application.yml`：新增 `spring.ai.openai.*`（`api-key` / `base-url` /
  `chat.options.model` / `embedding.options.model|dimensions`）与 `pinliao.ai.enabled`，
  **全部由环境变量注入**，`api-key` 给占位默认值 —— 未配密钥的机器 / CI 仍能正常启动。
  环境变量：`PINLIAO_AI_API_KEY` / `_BASE_URL` / `_CHAT_MODEL` / `_EMBEDDING_MODEL` /
  `_EMBEDDING_DIM` / `_ENABLED`。
- `backend/README.md`：新增「AI 配置（Step 4 T-AI0）」一节（设置命令 + 自检命令 + 已知要点）；
  基线更新为 `mvnw test` 176/176；目录结构补 `ai/`。

### Fixed

- `backend/scripts/db-reset.ps1`：`Get-JavaMajor` 在 **Windows PowerShell 5.1** 下会因
  `$ErrorActionPreference='Stop'`（`_env.ps1` 设置）把 `java -version`（走 stderr）抛出的
  `NativeCommandError` 当终止错误吞掉，导致版本解析恒为 0，脚本误报「未找到 JDK 17+」
  （PowerShell 7 不复现）。改为探测函数内局部降级为 `Continue`；5.1 与 7 均实跑通过。

### Docs

- `docs/step4-ai-plan.md` v1.1：T-AI0 标记完成并补实测结论；配置章节改写为「已落地」实际写法；
  错误码 `43xxx` → `42220/42221/42902/50020`；对话模型定为 `qwen3.5-122b-a10b`。
- `PROGRESS.md` v1.21、`docs/AI-CONTINUE.md` v1.23（新增 §6.19 AI 接入 10 条坑）、`worklog.md` Task 33。

### Why / Key findings（实测，影响后续所有 AI 工作）

- `sk-ws-` 前缀的 key **可以**用于普通应用：标准「按量计费」兼容模式端点
  （`dashscope.aliyuncs.com/compatible-mode`）chat 与 embeddings 均 200；
  `coding.dashscope.aliyuncs.com`（Coding Plan 专属、只服务编码类工具）401 —— 不要按前缀误配。
- `qwen3.5-122b-a10b` 是**思考型**模型：不加 `enable_thinking=false` 时 >10s 且多计 token，
  加上后 ~1.0s。
- `base-url` 只写到 `.../compatible-mode`（Spring AI 自拼 `/v1/chat/completions`、`/v1/embeddings`，
  与 DashScope 路径一致）。

### Verification

- `GET /api/v1/ai/status` → `enabled=true / openai-compatible / qwen3.5-122b-a10b / text-embedding-v4`。
- `GET /api/v1/ai/ping` → chat **1005ms / 44 tokens**、embedding **538ms / dim 1024**，端到端 1.56s。
- `mvnw test` **176/176**（+3）；真实后端 `db-reset.ps1` 后 `npm run e2e` **63/63**；
  `verify-seed.ps1` **16/16**；`/actuator/health` UP。
- 密钥泄露核查三连均为空命中：`git grep 'sk-ws-'`、未跟踪文件扫描、`git log -p --all -S 'sk-ws-'`。

---

> **更早条目已归档**：Step 3（2026-10-17~10-19）见 [`docs/archive/changelog-step3.md`](./docs/archive/changelog-step3.md)，
> Step 2 及更早见 [`docs/archive/changelog-legacy.md`](./docs/archive/changelog-legacy.md)。默认不要整读。

## 用法约定

1. 完成一个增量后：把「未提交」段落的实际内容整理为正式条目（或标注提交 hash）。
2. 只写**发生了的事实**（含测试结果），不要把计划中的功能写成已完成。
3. 与 `PROGRESS.md` 分工：本文件讲「改了什么」，`PROGRESS.md` 讲「现在到哪、下一步」。

---

## 修订记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| `8b69b87` | 2026-10-16 | 首版：记录五项验收功能（投递 / 候选人 / 面试、验证码登录注册、公司资料 / 企业认证）接入 UI 与 e2e 57/57，及交接五件套与文档同步 |
| （文档整理） | 2026-10-16 | 交接文档一致性刷新：以 `8b69b87` 为基准统一口径（状态 / 目录 / 基线 / hash 结算），无代码变更 |
| `af6197e` | 2026-10-16 | HR 人才筛选（演示版）：关键词匹配 + 技能标签 + 命中证据；契约 §4.5/§4.6（正文 70）；e2e 63/63 |
