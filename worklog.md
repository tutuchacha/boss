# 聘聊项目 · 工作日志

> **只保留最近 4 条**（本阶段进行中的那条 + 前 3 条）。更早的按阶段归档，**默认不要读**：
>
> - Step 0~2（Task 0~15）→ [`docs/archive/worklog-step0-2.md`](./docs/archive/worklog-step0-2.md)
> - Step 3（Task 16~31）→ [`docs/archive/worklog-step3.md`](./docs/archive/worklog-step3.md)
> - Step 4 / 可观测性（Task 32~35）→ [`docs/archive/worklog-step4.md`](./docs/archive/worklog-step4.md)
>
> 追溯某次决定时用 `grep`（如 `grep -n "Task ID: 24" docs/archive/worklog-step3.md`）。
> 新条目模板：`Task ID` 递增 + `Agent` + `Task` + `Work Log` + `验证` + `遗留`，单条建议 ≤ 60 行。

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

Task ID: 39
Agent: orchestrator (main)
Title: Step 4 T-AI3 流式输出（SSE + 打字机 + 引用双向定位）
Status: 完成（2026-10-20）

Work:
- **后端流式**：`AiChatClient.stream`；`SpringAiChatAdapter.stream`（Spring AI `Flux` → `toIterable()`、
  跳过空 chunk、空流 / 异常转 `50020`）/ `FakeChatClient.stream`（8 字一段 + 30ms，拼接与非流式一致）。
- **编排复用**：`HrResumeQaService` 拆为 `prepare()` / `answer()` / `stream(Prepared, AiStreamSink)`；
  开关 / 角色 / 入参 / 归属 / 配额 / 索引 / 检索全部在返回 emitter 之前同步完成，错误仍是 JSON 包络。
- **SSE 端点**：`POST /api/v1/hr/applications/{id}/ai/qa/stream`，`SseEmitter` 120s + 4 线程 daemon executor
  （`AiConfig.aiStreamExecutor`）+ `AiQaSseSink`（JSON 单行化、断连即停）；事件 `delta` → `citation` → `done`，
  `error` 仅用于流开始后的失败；响应头 `Cache-Control: no-cache` / `X-Accel-Buffering: no`。
- **ThreadLocal 泄漏**：`AuthInterceptor` 改 `AsyncHandlerInterceptor`，`afterConcurrentHandlingStarted` 清 `UserContext`。
- **前端**：`client.requestStream` / `toApiError` + `buildUrl` 支持 `/api/` 站点根路径；`api/ai.ts` 增
  `askResumeQuestionStream` + SSE 解析（`\r\n` 归一 / 多行 data / 无 done 自动退回非流式）；`mock/ai.ts`
  增 `mockAskResumeQuestionStream`；`HrAiAssistantSheet` 打字机 + 光标、引用逐条渲染、`[n]` ↔ 来源卡片
  双向定位 + 1.2s 高亮、错误保留已输出部分。
- **关键坑 1（缓冲）**：Next `rewrites` 会把 SSE 缓冲成一次性吐出，且优先级高于同路径 Route Handler
  （`/api/v1/...` 下的 route 实测不执行）；改为站点根路径代理 `/api/ai/qa-stream/{id}`
  （`src/app/api/ai/qa-stream/[applicationId]/route.ts`，Web Streams 透传），实测 ttfb 54ms、逐条 55-189ms。
- **关键坑 2（Accept）**：SSE 客户端只发 `Accept: text/event-stream` 时，Spring 无法把校验类错误写成 JSON
  （`HttpMediaTypeNotAcceptableException` → 500 空响应）；统一改为 `text/event-stream, application/json`。
- **冒烟 / e2e**：`smoke-ai.mjs` 31 → **43**（新增 SSE 12 项）；e2e 保持 65 项且**不依赖流式内容**。
- **实测**：`mvnw test` **240/240**（+10）；直连 :8080 ttfb 151ms / delta 152-279ms；浏览器打字机
  109/167/219ms 递增、引用双向定位与高亮、断网错误态、流截断自动非流式兜底；控制台零错误；e2e
  真实后端（fake provider）65/65、Mock 65/65。

Stage Summary:
- 问答从「等结果」升级为「边生成边读」，引用可双向定位；模型 / 网络抖动不影响可用性（自动兜底）。
- 代理方案绕开 Next rewrites 的缓冲，不引入 STOMP / 新依赖；契约新增一条流式端点（契约外，不计账）。
- 下一步 T-AI4 语义人才检索（与关键词版 A/B）；T-AI5 面试问题生成。

---

Task ID: 40
Agent: orchestrator (main)
Task: 文档分层重构 —— 解决「读文档吃掉 60% 上下文 → 反复压缩」的问题

Work Log:
- 量化：AGENTS 原「启动必读」链路（AGENTS + PROGRESS + ARCHITECTURE + PROJECT + AI-CONTINUE + worklog）
  合计 197k 字符 ≈ 123k tokens，占 200k 窗口 62%；单文件 worklog 46k / AI-CONTINUE 32k / PROGRESS 30k
- 方案：三层文档 + 一张检索表，把「全读」改成「按需定位」
  - 必读：`AGENTS.md` + `PROGRESS.md` + `docs/INDEX.md`
  - 按需：`ARCHITECTURE.md`、`docs/AI-CONTINUE.md`、`docs/state/*`、`docs/pitfalls/*`
  - 归档：`docs/archive/*`（默认不读，追溯时 grep）
- 新增 `docs/INDEX.md`：想知道什么 → 读哪个文件哪一节 + 读取纪律 + 体积预算
- `PROGRESS.md` 瘦身：留 §0 快照 / §0.1 接续即用 / §1 总进度 / 当前增量（T-AI3）/ 下一步 /
  已锁定决策 / 功能状态 / 风险摘要 / 命令速查；历史增量、交付明细、环境与接口事实、全量命令、修订记录移出
- `docs/AI-CONTINUE.md` 拆分：§6（30k 字符 / 22 条坑）→ `docs/pitfalls/{frontend,backend-core,backend-collab,ai}.md`
  （编号 6.1~6.22 不变，本文 §6 只留索引表，旧引用可对照）；§5 归并到 `docs/state/commands.md`；
  §2 状态表删除（改指 `PROGRESS.md`——此前它已停留在 T-AI0，是双份真相的典型）；§10 归档
- `worklog.md` / `CHANGELOG.md` 按阶段归档，主文件只留当前阶段；新增 `docs/state/{commands,backend-facts,ai-config,deliverables}.md`
- 新增 `scripts/doc-size.ps1`：分层软/硬上限体检（`-Strict` 超限退出码 1）
- `AGENTS.md`：读取协议改为「必读 3 个 + 按索引检索」；§4 补「只写当前态 / 坑进 pitfalls / 历史进 archive / 提交前跑 doc-size」

验证：
- `./scripts/doc-size.ps1` 全绿（均在软上限内）
- 启动必读 197k → 20.5k 字符（≈12.8k tokens，约 6%）
- 归档为原文切片，未改写事实；相对链接逐条核对存在
- 本轮只动文档，未改代码，未跑 e2e

遗留：
- T-AI3 代码仍是未提交状态（以 `git status` 为准）；本轮文档重构建议与之一同提交
