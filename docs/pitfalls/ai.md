# 踩坑 · AI 与可观测性（§6.19~§6.22）

> 摘自 `docs/AI-CONTINUE.md` §6（2026-10-20 拆分，编号未变）。索引见 [`../AI-CONTINUE.md`](../AI-CONTINUE.md) §6。
> 方案与任务板：[`../step4-ai-plan.md`](../step4-ai-plan.md)、[`../arms-plan.md`](../arms-plan.md)。

---
### 6.19 AI 接入（Step 4 T-AI0 起）

- **依赖坐标**：`org.springframework.ai:spring-ai-bom:1.1.8`（`import` scope） +
  `spring-ai-starter-model-openai`。**1.1.x ↔ Boot 3.5；2.0.x 需 Boot 4 + Jackson 3**，勿混用；
  `spring-ai-starter-model-openai` 已在 Maven Central（本地无 `settings.xml`，直连下载）。
- **DashScope 用 OpenAI 兼容模式，`base-url` 不要带路径后缀**：
  `https://dashscope.aliyuncs.com/compatible-mode`，Spring AI 会自己拼 `/v1/chat/completions` 与
  `/v1/embeddings`——正好等于 DashScope 的正确路径（curl 实测两个 path 均 200）。
- **`sk-ws-` 前缀的 key 可用于普通应用**：百炼 key 前缀有 `sk-`（按量）/`sk-sp-`（套餐）等，
  `sk-ws-` 实测在**按量计费兼容模式端点**上 chat + embeddings 均 200；而
  `coding.dashscope.aliyuncs.com`（Coding Plan 专属、只服务编码类工具）返回 401。
  **不要被前缀误导去配 coding 专属域名**（否则 401/404，或按量额外计费）。
- **思考型模型必须显式关思考**：`qwen3.5-122b-a10b` 默认返回 `reasoning_content`，实测 >10s 且多计
  token；用 `OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false))` 后 **~1.0s**、
  无 reasoning 输出。`enable_thinking` 是 DashScope 扩展字段（非 OpenAI 标准），**只能走 `extraBody`**。
- **`api-key` 要给占位默认值**：Spring AI 的 OpenAI 自动装配在 `spring.ai.openai.api-key` 为空时
  **应用启动失败**。写法 `${PINLIAO_AI_API_KEY:pinliao-ai-disabled-placeholder}` + 自研总开关
  `pinliao.ai.enabled`（默认 `false`）→ 无 key 的机器 / CI 照常启动，AI 接口返回 42220。
- **错误码不能用 `43xxx`**：契约 §5 规定「5 位数字，前 3 位对应 HTTP 状态」，
  `ApiCodes.httpStatus()` 只识别 400/401/403/404/409/422/429/500；`430xx` 会落到 500。
  AI 用 `42220`（未启用）/ `42221`（索引未就绪）/ `42902`（超配额）/ `50020`（上游失败或超时）。
- **AI 自检端点必须鉴权**：`/api/v1/**` 默认被 `WebAuthConfig` 拦截器保护，AI 调用会真实扣费，
  **不要**加白名单；`AuthInterceptorPublicEndpointTest` 已断言 `/ai/status` `/ai/ping` 非公开。
- **`java -version` 在 Windows PowerShell 5.1 + `$ErrorActionPreference='Stop'` 下是坑**：其输出走
  stderr，`2>&1` 会产生**终止性** `NativeCommandError`，被 `catch` 吞掉 → 版本解析恒为 0
  （`db-reset.ps1` 因此误报「未找到 JDK 17+」；PowerShell 7 不复现）。修法：探测函数内局部把
  `$ErrorActionPreference` 降为 `Continue`。同类「脚本内跑 native 命令并解析输出」的写法都要注意。
- **key 只放用户级环境变量**，仓库只留 `${引用}`；核查三连（都应为空命中）：
  `git grep -I -l 'sk-ws-'`、`git ls-files --others --exclude-standard` 后逐个 `Select-String`
  、`git log -p --all -S 'sk-ws-'`。`.env*` 与 `.local/` 已在 `.gitignore`。
- **单测不联网**：`AiConnectivityServiceTest` 用 Mockito 假模型断言「关闭 → 42220 且不调上游」
  「开启 → 关闭思考模式 + token 上限」「回显模型 / 向量维度」，符合 §8「假模型优先」。

### 6.20 可观测性 / 阿里云 ARMS 接入（T-OBS1 起）

- **两个产品入口别选错**：开源 `opentelemetry-javaagent.jar` 接进的是 ARMS「**可观测链路 OpenTelemetry 版**」
  （应用列表语言列显示 `-`，只有调用链 / 大盘 / Trace Explorer）；阿里云自研 **ARMS Java 探针**接进的是
  「**应用监控**」（Java 图标，含慢 SQL、异常分析、JVM/线程、接口大盘、拓扑）。用户要「引个 jar 一配就能看调用链」
  → 选**阿里云探针**，且它的配置更简单（一个 LicenseKey，无需 endpoint/token/protocol）。
- **探针下载地址随地域变化**：`http://arms-apm-<region>.oss-<region>.aliyuncs.com/AliyunJavaAgent.zip`
  （华东1-杭州 = `arms-apm-cn-hangzhou.oss-cn-hangzhou.aliyuncs.com`，约 51 MB），解压得
  `AliyunJavaAgent/aliyun-java-agent.jar`。**它其实是 OpenTelemetry javaagent 的阿里云改名版**
  （`META-INF/MANIFEST.MF` 里 `Implementation-Version: apsara-java-agent-5.1.2-otel-2.23.0`；
  jar 内 `inst/` 3.3 万条、`io/opentelemetry/` 1600+ 条），所以 OTel API 埋点方式可以复用。
- **`-javaagent` 只能挂到应用 JVM**：用 `-Dspring-boot.run.jvmArguments=-javaagent:...`（`spring-boot:run`
  默认 fork；实测 WMI 命令行确认 application JVM 有、Maven JVM 没有）。**千万不要**用 `JAVA_TOOL_OPTIONS`——
  Maven 自身也会挂上探针，控制台会出现同名的第二个「应用」且数据双份（还费钱）。
- **`jvmArguments` 是按空格切分的字符串**：探针路径与参数值不能含空格，否则参数会被切坏（脚本已加显式检查与提示）。
- **`-Dspring-boot.run.jvmArguments` 在 PowerShell 里有个致命坑（本增量实际踩中并修复）**：
  `Start-Process -ArgumentList <数组>` **只用空格拼接**、不会给含空格的元素补引号，于是
  `-Dspring-boot.run.jvmArguments="-javaagent:… -Darms.licenseKey=… -Darms.appName=…"` 被 Maven 拆成
  `jvmArguments` 只剩 `-javaagent`、其余 `-Darms.*` 变成 Maven 自己的 `-D` 参数。现象极具误导性：
  **探针横幅正常、健康检查正常、但控制台看不到应用、请求不产生 span**。修法是把整行拼成
  **单个带引号的字符串**（`Start-Process -ArgumentList "spring-boot:run `"$jvmArgLine`""`），
  并用 WMI 回读应用 JVM 命令行确认三个参数都在 —— 这是「挂了探针却没数据」的第一排查项。
- **探针工作目录在用户目录**：`%USERPROFILE%\.apsara-apm\java\<探针版本>\`（含 `logs/`、`status`）。
  ⚠️ **`status` 文件不可作为授权判据**：本机链路上报已通、控制台已能看到应用时，`status` 仍停在
  首次运行的内容（`licenseKey=null`、`lastWriteTime` 不变）。判活请看「判活三条判据」（下一条），
  不要再拿 `status` 下结论。
- **判活三条判据（不需要进控制台，本地就能定性）**：① 应用 JVM 命令行 `-Darms.*` 三个参数都在；
  ② 送 `X-Trace-Id=mycustomtraceid123`，包络**不回显**而是 32 位 hex（= span 分支命中）；
  ③ 应用 JVM 有到阿里云 **80** 端口的 Established 连接（= 正在上报）。三条全中即「探针在工作」。
  另外 `100.103.107.x:9990` 上出现 SynSent 是探针的 EagleEye 配置通道被网络阻断所致，**不影响链路数据**。
- **两种传参形式都可用**：控制台文档的 `-Darms.licenseKey` / `-Darms.appName`（脚本采用）与
  `ARMS_LICENSE_KEY` / `ARMS_APP_NAME` **环境变量**（实测同样能产生 span）。想避免密钥出现在进程命令行时用后者；
  但注意 `Start-Process` 的引号坑只影响前者。
- **未配 LicenseKey 时探针不产生 span**（本机实测，2026-10-19）：此时 `traceparent`（W3C）与
  `EagleEye-TraceId` 请求头**都不会被采纳**，请求期间日志 MDC 里也没有探针注入的键（只有本项目
  `TraceIdFilter` 写的 `traceId`）。→ 凡是要证明「响应包络 traceId == ARMS 链路 TraceId」的验证，
  **必须先有 LicenseKey**；无 key 时只能验证「探针能加载 + 应用不受影响」。
  **配好 LicenseKey 后已实测打通**：`Span.current()` 命中活跃 span，包络返回的就是探针的 32 位 traceId，
  应用 JVM 同时出现到阿里云 80 端口的 Established 上报连接，带探针 `npm run e2e` 仍 **63/63**。
- **别用 `OTEL_JAVAAGENT_LOGGING=debug` 开 debug**：该值非法，探针会打印
  `Unrecognized value of 'otel.javaagent.logging': 'debug'. The agent will use the no-op implementation.`，
  随后**连探针横幅都不打印**（日志实现变 no-op），很容易被误判成「探针没加载」。要开 debug 用
  `-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug`。
- **探测「探针到底注入了什么」的零代码手法**：临时用环境变量 `LOGGING_PATTERN_CONSOLE` 覆盖日志格式为
  `%mdc | %msg%n`（或加 `%X{trace_id:-} %X{span_id:-}`），打一个请求后看请求内的日志行——比读探针文档更快。
- **OTel API 读活跃 span 是安全降级写法**：`io.opentelemetry:opentelemetry-api`（版本由 Boot BOM 管，
  3.5.16 → 1.49.0，**不要自己定版本**）；`Span.current().getSpanContext().isValid()` 判断，无 agent / 无 SDK 时
  返回 invalid span **且不抛异常**，可放心作为「有则用、无则退回」的一级来源。
- **Windows + PowerShell 的 JDK 探测**：`Resolve-PinliaoJavaHome` / `Get-JavaMajor` 已提到 `scripts/_env.ps1`
  作为共享函数（坑见 §6.19 那条 `$ErrorActionPreference='Stop'` 与 `java -version` 走 stderr）。
  新增服务端脚本时**直接复用**，别再复制一份。
- **新增 `.ps1` 必须存成 UTF-8 带 BOM（本增量实际踩中）**：Windows PowerShell 5.1 对**无 BOM** 的脚本
  按系统 ANSI（本机 GBK）解码中文注释与全角标点，会把引号/括号配对搞乱，报
  `The string is missing the terminator` 或 `Missing closing ')' in subexpression`（PowerShell 7 不受影响，
  所以「pwsh 下能跑」不代表交付可用）。仓库内 `backend/scripts/*.ps1` 一律 **UTF-8 with BOM + LF**；
  用工具写文件后检查首三字节是否 `EF BB BF`，没有就补上。
- **费用与边界**：应用监控按量计费（口径与「链路 OpenTelemetry 版」不同，以控制台为准）。探针只在
  本机开发环境挂，且 SQL 文本默认脱敏（本项目 SQL 带手机号，**不要**关脱敏）；前端监控（Web SDK）
  会往浏览器注入请求，**可能破坏 e2e「零控制台错误」门禁**，要做必须最后做并加开关。

### 6.21 单简历问答 / 引用（Step 4 T-AI2 起）

- **检索必须先做可见性过滤**：`resume_chunks` 不冗余 `company_id`，`HrResumeQaService` 先用
  `applications`（租户表，`selectById` 已被插件追加 `company_id`）取 `resumeId`，再显式比对
  `companyId`（纯 Mockito 单测没有插件，必须靠显式比对兜住越权）。跨公司 / 不存在统一 `40401`，
  不泄露存在性。**不要**新增「按 resumeId 直接问答」的接口，否则等于绕过投递归属。
- **假向量只做管道验证，不具语义**：`FakeEmbeddingModel` 是「中文单字 + 拉丁词」词袋哈希，
  自然中文问句（如「候选人的核心技能是什么？」）与技能块的字面重合可能低于 `qa-min-score=0.2`，
  会走「无依据」分支。冒烟 / e2e 要带**强字面重合**的问题（如「候选人的技能包括 React 和 TypeScript 吗？」）；
  真实语义效果必须用 `provider=springai` 验，别拿 fake 结果判断召回质量。
- **低分不调对话模型**：`topScore < qa-min-score` 时直接返回
  `简历中没有相关信息。` + `grounded=false` + `citations=[]`，此时 `chatModel` 仍回显（排查用），
  `retrieved` 保留命中数。**不要**为了「显得智能」把低分资料硬塞给模型。
- **引用编号由后端解析，不信任模型**：`CitationAssembler` 解析回答中的 `[n]`，只保留合法编号并按首次
  出现顺序去重；模型完全漏标时在末尾补 `来源：[1]`。`citations[].index` 与正文 `[n]` 一一对应。
- **`resume_chunks.id` 是 `rc_` + 32 位 hash**（`resumeId|section|ordinal`），不是 `resumeId#1`；
  冒烟断言别写错格式。
- **首次提问按需建索引**：`countByResume(resumeId, model) == 0` 时自动 `rebuildOne`，仍为空才抛 `42221`。
  因此清库后无需先重建索引，用户第一问会多花一次嵌入时间（fake 毫秒级，真实约 1s）。
- **配额按 HR / 自然日**：`AiQuotaService` 用 Redis key（含 `userId` + 日期 + 模型维度），默认 100 次/日，
  超限 `42902`；**Redis 故障降级放行**（宁可少一道限流，也不要因缓存挂了导致问答全不可用）。
  `db-reset.ps1` 会 `FLUSHALL`，冒烟可重复执行不会撞配额。
- **上游失败统一 50020**：`SpringAiChatAdapter` / `SpringAiEmbeddingAdapter` 捕获异常转
  `BusinessException(AiCodes.UPSTREAM_FAILED)`，**不回显上游原始报文**（可能含 key / 内部信息）。
- **审计不落简历原文**：只记 `applicationId / userId / questionLength / retrieved / citations /
  answerLength / tokens / elapsedMs`。排查「答得不对」时用引用片段定位，不要往日志里打 Prompt。
- **前端入口按 `/ai/status.enabled` 显隐**：`getAiStatus` 查询 `staleTime=5min`、`retry=false`；
  查询失败也按「不显示」处理，避免用户点了才吃 `42220`。抽屉当前是**本地临时会话**，关闭即清空。
- **e2e 探针别用 CDP 的 `eval` 跑 async**：`tests/e2e-verify.cjs` 的 `eval` 外层是同步
  `JSON.stringify(...)`，传 `(async () => ...)()` 只会得到 `{}`（本次实测 `enabled=[object Object]`）。
  改在 **Node 侧**先密码登录、再带 token 查 `/api/v1/ai/status`，用返回值决定「入口应显示 / 应隐藏」。
- **Mock 模式跑 e2e 时后端最好别开着**：Mock 分支的入口固定显示，但 Node 探针仍会打到真实后端；
  若后端恰好 `AI 关闭`，探针得到 `false` 而页面入口存在 → 误报「入口与开关不一致」。
  Mock 回归请在**后端停止**（或后端 AI 开启）时跑。
- **`AiProperties` 是 8 参 record**：`(enabled, provider, chunkMaxChars, embeddingBatchSize,
  qaTopK, qaMinScore, qaMaxTokens, qaDailyLimit)`；新增配置项时所有单测构造器都要同步，别再写 4 参旧签名。

### 6.22 流式问答 / SSE（Step 4 T-AI3 起）

- **Next `rewrites` 会缓冲 SSE**：`next.config.ts` 的 `/api/v1/:path*` → Spring Boot 代理实测把逐条 delta
  攒到结束时一次性吐出（首字延迟 = 总耗时）。**并且 rewrites 的优先级高于同路径 Route Handler** ——
  在 `src/app/api/v1/...` 下放 route 文件不会被执行（响应头无自定义标记、`cache-control` 来自后端）。
  结论：流式接口单独走**站点根路径** `/api/ai/qa-stream/{applicationId}`（不在 `/api/v1` 下），
  用 Web Streams 透传 `upstream.body`。别再试图在 `/api/v1` 下覆盖 rewrites，也不必退回 STOMP。
- **SSE 的 `Accept` 必须同时包含 `application/json`**：`@PostMapping(produces = TEXT_EVENT_STREAM_VALUE)`
  下，校验类错误要写 JSON 包络；客户端只接受 `text/event-stream` 时 Spring 抛
  `HttpMediaTypeNotAcceptableException`，最终表现为 **500 空响应**（无包络、无 code）。
  前端、代理、冒烟统一发 `Accept: text/event-stream, application/json`；代理层顺带把 SSE-only 客户端
  也归一化掉了（冒烟实测 SSE-only 经代理仍能拿到 422 JSON）。
- **校验必须在返回 `SseEmitter` 之前完成**：`HrResumeQaService.prepare()` 同步做开关 / 角色 / 入参 /
  归属 / 配额 / 索引 / 检索；一旦开始推流，异常只能走 `error` 事件（HTTP 已是 200）。
  因此「42220 / 40301 / 42201 / 40401 / 42902」永远是普通 JSON 包络 —— 前端先看响应头与
  `content-type` 再决定读不读流。
- **`done` 事件才是最终正文**：`CitationAssembler` 在模型漏标时会补「来源：[1]」，这段只出现在
  `done.answer` 里；前端必须用返回值覆盖已累积的 delta 文本，否则会少一句。
  冒烟断言 `delta 拼接 == done.answer`（fake 模型保证一致；真实模型若漏标则不相等，属预期）。
- **异步请求的 ThreadLocal 要清**：`SseEmitter` 走异步派发，`preHandle` 塞进 `UserContext` 的值不会
  自动清；`AuthInterceptor` 实现 `AsyncHandlerInterceptor`，在
  `afterConcurrentHandlingStarted` 里 `UserContext.clear()`，否则线程复用时可能串用户。
- **`SseEmitter` 要用独立线程池**：`AiConfig.aiStreamExecutor`（4 线程、daemon、`@PreDestroy shutdown`），
  不要占用 Tomcat 请求线程 / 公共 `ForkJoinPool`；超时 120s，断连后 `sink` 停止写入（避免刷日志）。
- **前端务必做「无 done 兜底」**：代理缓冲、连接中断、网关超时都会让 SSE 提前结束但不报错；
  `askResumeQuestionStream` 在没收到 `done` 时自动再调一次非流式 `/ai/qa`（打字机不可用 ≠ 问答不可用）。
  错误态保留已输出文本，空占位气泡才移除。
- **Mock 流式要可复现**：`mockAskResumeQuestionStream` 用 `setTimeout` 分段吐字（4 字 / 18ms），
  并在返回前复用 `mockAskResumeQuestion` 的最终结果；不要用 `Date.now()` / `Math.random()` 生成 id。
- **e2e 不依赖流式内容**：只断言入口显隐与回答含 `技能` / `[1]`；流式失败会自动退回非流式，
  这样「打字机坏了」不会误报成「问答坏了」。SSE 细节全部放 `smoke-ai.mjs`。

### 6.23 语义人才检索（Step 4 T-AI4）

- **先取可见范围，再查分块**：`resume_chunks` **不带 `company_id`**，必须先用
  `applications.company_id = 当前公司 AND deleted_at IS NULL` 得到 `resume_id` 集合，再
  `resume_chunks.resume_id IN (...)`；不要写成「全局向量检索后再按候选人过滤」，那会多付检索成本且有越权风险。
- **同人多投递要按 user 去重**：一个求职者可能投多个职位 / 有多份简历，`applications` 行不唯一；
  以 `userId` 合并、保留最高分那条投递，否则列表会出现同名候选人。
- **分数是余弦（0~1），阈值别照抄问答**：语义检索默认 `search-min-score=0.15`，问答默认 `qa-min-score=0.2`；
  两者语义不同（问答低分直接拒答，检索低分只是不召回）。调阈值前先看真实 embedding 的分布。
- **`provider=fake` 只验管道**：假向量是词袋哈希，`React` 与「前端」不会天然接近；冒烟 / e2e 的查询词
  必须与简历有字面重合，否则会误判为「语义检索坏了」。Mock 分支（阈值 0.35）同样是词面近似，不代表语义能力。
- **语义检索也扣配额**：与问答共用 `AiQuotaService` 的按 HR / 自然日计数（默认 100，`42902`）；
  索引缺失时的 `rebuildOne` 不额外扣次数，但一次检索只允许一次查询向量化，不要在循环里反复 embed。
- **维度不一致返回 `42221`**：换过 embedding 模型（如 1024 → 1536）后旧索引不能混用；
  只要「有分块但全部维度不匹配」就整体报 `42221`，提示重建，而不是静默返回空列表。
- **前端入口按 `/ai/status.enabled` 显隐**：AI 关闭时后端返回 `42220`，但不应让用户先看到按钮再点出错误；
  Mock 模式没有 `/ai/status`，按 Mock 默认值展示，e2e 的语义断言随入口存在与否自动跳过。
