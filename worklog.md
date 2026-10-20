# 聘聊项目 · 工作日志

> **只保留最近 4 条**（本阶段进行中的那条 + 前 3 条）。更早的按阶段归档，**默认不要读**：
>
> - Step 0~2（Task 0~15）→ [`docs/archive/worklog-step0-2.md`](./docs/archive/worklog-step0-2.md)
> - Step 3（Task 16~31）→ [`docs/archive/worklog-step3.md`](./docs/archive/worklog-step3.md)
> - Step 4 / 可观测性（Task 32~38）→ [`docs/archive/worklog-step4.md`](./docs/archive/worklog-step4.md)
>
> 追溯某次决定时用 `grep`（如 `grep -n "Task ID: 24" docs/archive/worklog-step3.md`）。
> 新条目模板：`Task ID` 递增 + `Agent` + `Task` + `Work Log` + `验证` + `遗留`，单条建议 ≤ 60 行。

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

---

Task ID: 41
Agent: orchestrator (main)
Title: Step 4 T-AI4 —— 语义人才检索（与关键词版 A/B）
Status: 完成（2026-10-20）

Work:
- 后端新增 `ai/search/SemanticTalentSearchService`：`query + tags` 只向量化一次 → 在**本公司可见投递**
  的简历分块内做余弦 → 按候选人最高分聚合（同人多投递去重）→ 倒序取 topK；低于 `search-min-score`
  不返回，每人最多 3 条证据；缺失索引按需补建，维度不一致 `42221`。
- 多租户红线：先用 `applications.company_id = 当前公司 AND deleted_at IS NULL` 取 resumeId 集合，
  再 `resume_chunks.resume_id IN (...)` 前置过滤；不存在「先全局检索再丢弃」。
- 接口 `POST /api/v1/hr/talent/semantic-search`：`{query?, tags?, limit?}`（至少一个非空），
  出参 `SemanticMatch[]`（candidate + score + evidences）；`AiSearchController` / DTO / View 新建。
- 配置 `pinliao.ai.search-top-k`（8）/ `search-min-score`（0.15）；与问答共用 `AiQuotaService`
  配额（42902）；`ResumeChunkStore.findByResumesAndModel` 批量查询；`RetrievalService.parseVector/cosine` 复用。
- 前端：`HrTalentMatchPage` 增「关键词 / 语义检索」切换（仅 AI 开启时显示），两种模式各自 Query hook；
  语义卡片展示 score 百分比 + 证据片段，含加载 / 空结果 / 错误态；Mock 分支用词面近似（非 embedding）。
- 文档：`docs/api-endpoints.md` §14.7 + v0.3.11；`docs/step4-ai-plan.md` T-AI4 ✅；PROGRESS / CHANGELOG /
  worklog / pitfalls 6.23 / AI-CONTINUE 索引 / commands / ai-config / deliverables / backend README 同步。

验证：
- `mvnw test` **253/253**（新增 `SemanticTalentSearchServiceTest` 13 项）。
- `smoke-ai.mjs` **54/54**（新增 11 项语义检索，`provider=fake`，可重复执行）。
- `npm run e2e` **68/68**（真实后端 fake provider 一轮、Mock 分支一轮；新增 3 项语义断言）。
- `npx tsc --noEmit` 仅 3 个既有错误；本轮改动文件 ESLint 0 告警；Chrome 进程数前后不变。

Key findings：
- 语义模式不理解「必须 / 优先」等约束，只按余弦排序；`provider=fake` 是词袋哈希、无语义，冒烟只验管道。
- 语义检索也是「一次查询一次配额」，与问答共用日限；索引补建本身不再额外扣配额。
- Mock 语义分支阈值 0.35 与后端 0.15 不同口径（Mock 分数是近似值），文档已注明不可当作语义质量依据。

Stage Summary:
- 人才筛选从关键词单模式升级为可 A/B 的双模式；语义链路的租户隔离、配额、错误码与前端降级均有测试覆盖。
- 下一步 T-AI5 面试问题生成；T-OBS3 与 T10 仍待排期。
