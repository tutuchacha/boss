# 聘聊 · 变更记录（CHANGELOG.md）

> 记录**重要代码与文档变更**，含未提交的工作区增量。
> 格式约定：版本（或「未提交」）→ Added / Changed / Fixed / Docs 分组；
> 新条目追加在顶部（最新在上）。提交历史另见 `git log`。

---

## [Step 4 · T-AI4] 2026-10-20 — 语义人才检索（与关键词版 A/B）

> 目标：在既有「人才筛选（关键词）」旁增加语义模式，用简历分块向量做候选人召回，
> 同一组筛选条件可切换两种模式对比效果；不改变关键词版行为，AI 关闭时入口自动隐藏。

### Added

- **语义检索服务** `ai/search/SemanticTalentSearchService`：`query + tags` 只向量化一次 →
  在本公司可见投递的简历分块内做余弦 → 按候选人聚合最高分（同人多投递去重）→ 倒序取 topK；
  低于 `search-min-score` 不返回，每名候选人最多 3 条证据（`chunkId / section / title / snippet / score`）。
- **接口** `POST /api/v1/hr/talent/semantic-search`（`AiSearchController` + `AiSearchRequests` / `AiSearchViews`）：
  `{query?, tags?, limit?}`（至少一个非空；`query` ≤200 字、`tags` ≤20 个、`limit` ≤20）；
  索引维度与当前模型不一致返回 `42221`。
- **配置**：`pinliao.ai.search-top-k`（默认 8，最大 20）/ `search-min-score`（默认 0.15），
  对应环境变量 `PINLIAO_AI_SEARCH_TOP_K` / `PINLIAO_AI_SEARCH_MIN_SCORE`。
- **数据访问**：`ResumeChunkStore.findByResumesAndModel` 批量取块；`RetrievalService.parseVector` / `cosine`
  改为可复用（问答与检索同口径）。
- **前端**：`HrTalentMatchPage` 新增「关键词 / 语义检索」模式切换（仅 `/ai/status.enabled=true` 显示）；
  两种模式各自 TanStack Query（`HR_TALENT_MATCH_KEY` / `HR_SEMANTIC_TALENT_MATCH_KEY`）；
  语义卡片展示 `score` 百分比与命中证据片段，含加载 / 空结果 / 配额与上游错误态。
- **Mock 分支**：`matchCandidatesSemantic` 以关键词命中 + 字符重叠近似打分（阈值 0.35），
  仅保证交互可演示；**不是 embedding 语义**，真实效果以 `provider=springai` 为准。
- **测试**：`SemanticTalentSearchServiceTest` 13 项（开关 / 角色 / 空条件 / 超长 / limit / 公司范围 /
  按需索引 / 分数阈值 / 同人去重 / 证据条数 / 维度不一致 / 配额顺序 / snippet 截断）。

### Changed

- `AiQuotaService` 由问答专用改为问答与语义检索共用（按 HR / 自然日，默认 100 次，`42902`），
  超限文案改为「今日 AI 调用次数已达上限（N 次），请明天再试」。
- `smoke-ai.mjs` 由 43 项扩到 **54 项**（新增语义检索 11 项：鉴权 / 入参 / 公司范围 / 排序与证据 /
  仅标签检索 / limit / 空结果等）。
- `tests/e2e-verify.cjs` 由 65 项扩到 **68 项**（新增 3 项语义断言：入口、带 score 的结果、无关候选不出现；
  AI 关闭时随入口隐藏跳过）。

### Docs

- `docs/api-endpoints.md` §14.7（语义检索契约）+ v0.3.11；§14 现为 7 条契约外接口，仍不计入 70 条账目。
- `docs/step4-ai-plan.md`：包结构 / §6.3 端点 / §7 T-AI4 ✅ 与实测指标。
- `PROGRESS.md` §0 / §0.1 / §1 / §2 / §3 / §4 / §7；`worklog.md` Task 41；
  `docs/pitfalls/ai.md` §6.23；`docs/AI-CONTINUE.md` §6 索引；
  `docs/state/{commands,ai-config,deliverables}.md`；`backend/README.md`。
- 归档：T-OBS1 / T-OBS2 / T-AI0 从本文件移至 `docs/archive/changelog-step4-early.md`（主文件保留最近 3 条）。

### Verified

- `mvnw test` **253/253**（新增 13 项，全部假模型，不联网 / 不花钱）。
- `smoke-ai.mjs` **54/54**（`provider=fake`，可重复执行）。
- `npm run e2e` **68/68**：真实后端 + fake AI provider 一轮；Mock 分支一轮；其余功能零回归。
- `npx tsc --noEmit` 仅 3 个既有错误；本轮改动文件 ESLint 0 告警；Chrome 进程数前后不变。

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

> **更早条目已归档**：Step 4 早期（T-OBS1 / T-OBS2 / T-AI0，2026-10-19）见
> [`docs/archive/changelog-step4-early.md`](./docs/archive/changelog-step4-early.md)，
> Step 3（2026-10-17~10-19）见 [`docs/archive/changelog-step3.md`](./docs/archive/changelog-step3.md)，
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
