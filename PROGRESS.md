# 聘聊 · 进度与交接（PROGRESS.md）

> **唯一「当前状态」定稿**：只写「现在是什么样」。与其他文档冲突时以本文件为准（并顺手修正对方）。
>
> **开工读这三样就够**：本文件 → [`docs/INDEX.md`](./docs/INDEX.md)（按需检索表）→ 相关代码。
> **不要整读 `docs/`**：先 grep 定位，再按行段读，单次 `read` ≤ 300 行。
> 历史在 `docs/archive/`；踩坑在 `docs/pitfalls/`；命令全集在 `docs/state/commands.md`。
>
> 预算：本文件 ≤ 12 KB（`scripts/doc-size.ps1` 校验）。
>
> 最后更新：2026-10-20（Step 4 T-AI4 语义人才检索）

## 0. 快照（开工前核对）

| 项 | 值 |
| --- | --- |
| 上一提交 | `032ef31`（docs 分层重构）← `8bf4661`（T-AI3）← `17a8e92`（T-AI1 + T-AI2）← `96c604e`（T-OBS2） |
| 本轮改动 | **Step 4 T-AI4 语义人才检索**（`POST /hr/talent/semantic-search` + 筛选页关键词 / 语义 A/B）；**尚未提交**，以 `git status` / `git log -1` 为准 |
| 运行态 | 前端 :3000（`.env.local` = `USE_MOCK=false`，经 `next.config.ts` rewrites 代理到 :8080）；后端 :8080（AI 开启、`provider=fake`）；MySQL 3307 + Redis 6379 |
| 已验基线 | `mvnw test` **253/253**；`smoke-ai` **54/54**；`verify-seed` 16/16；e2e **68/68**（真实 fake provider + Mock 各一轮），AI 关闭时语义 / 问答用例自动跳过 |
| 测试账号 | 求职者 `13800000001` / HR `13800000002`，密码 `123456`；短信码固定 `123456`；e2e 注册号 `13900004321`（须清库才能重注册） |
| 环境 | Windows + PowerShell；**统一 npm**（无 bun）；后端 JDK 用 IDEA 自带 JBR 21（跑 `mvnw` 必设 `JAVA_HOME`） |
| 远端 | `origin` → GitHub `tutuchacha/boss`（SSH）；接手先 `git pull` |
| AI / ARMS 配置 | 环境变量、接口清单、判活判据见 [`docs/state/ai-config.md`](./docs/state/ai-config.md) |

> 接手时先跑 `git status --porcelain` 与 `npx tsc --noEmit`，核对本表是否仍成立。

### 0.1 接续即用（本会话收尾状态，2026-10-20）

| 项 | 值 |
|---|---|
| 代码 | 工作区 = **Step 4 T-AI4（语义人才检索）**（基于 `032ef31`；提交后以 `git log -1` 为准）；**注意本轮改动尚未提交**（以 `git status` 为准） |
| 运行态 | 后端 :8080 **AI 开启且 `provider=fake`** 运行中（`PINLIAO_AI_ENABLED=true`，日志 `.local\backend-dev.log`）；MySQL 3307 + Redis 6379 运行中；前端 dev :3000 运行中（`.env.local` 为 `USE_MOCK=false`） |
| 已验基线 | `mvnw test` **253/253**；`db-reset.ps1` → `flyway=9 / companies=16 / jobs=51 / applications=9`；`verify-seed.ps1` **16/16**；`smoke-ai.mjs` **54/54**（fake，索引 + 问答 + SSE + 语义检索）；e2e **68/68**（真实后端 fake provider / Mock 各一轮） |
| 环境变量（用户级，换机器需重配） | `PINLIAO_AI_*`（15 个，新增 `SEARCH_TOP_K` / `SEARCH_MIN_SCORE`）与 `PINLIAO_ARMS_ENABLED=true` / `_LICENSE_KEY` / `_APP_NAME=pinliao-backend`；**仓库零命中** |
| 恢复干净环境 | `backend\scripts\db-reset.ps1`（无探针重启 + 清库重迁 + Redis FLUSHALL；AI 仍按环境变量开关） |
| 重建简历索引 | HR token 调 `POST /api/v1/ai/index/rebuild`（body `{}` 全量 / `{"resumeId":"..."}` 单份）；**问答与语义检索都会自动补建缺失索引**；假向量冒烟 `node scripts\smoke-ai.mjs` |
| 试语义检索 | HR「人才筛选」→ 切「语义检索」→ 输入技能需求 / 勾标签 → 按相似度展示 `score` + 证据片段；入口仅在 `/ai/status.enabled=true` 时显示；Mock 分支只是词面近似、不代表语义能力 |
| 快速自检（ARMS 探针） | 见 `docs/arms-plan.md` §4「判活三条判据」：命令行含 `-Darms.*` / 送 `X-Trace-Id` 不回显 / 有到阿里云 80 端口 Established 连接；**勿看** `.apsara-apm\...\status`（不刷新） |
| 下一步（按推荐序） | ① **T-AI5 面试问题生成**（Step 4 主线）② T-OBS3（控制台核对慢 SQL / 异常 / JVM）③ T10 剩余 mock 路径切真实 ④ 待用户拍板项见 §3 与 `docs/step4-ai-plan.md` §10 |

## 1. 总体进度

| 阶段 | 状态 | 证据 / 产物 |
| --- | --- | --- |
| Step 0 架构 + 契约 | ✅ 完成 | `docs/architecture.md`、`data-model.md`、`api-*.md`、`prisma/schema.prisma` |
| Step 1 双端前端开发 | ✅ 完成 | `src/components/pinliao/`（求职者端 + HR 端 + 登录） |
| Step 2 前端验收 | ⏳ 进行中 | 验收五项功能已全部完成，并新增**人才筛选（演示版）**增量；`npm run e2e` **68/68** 通过（见 §5）；**用户人工验收初步通过（2026-10-16）**；待确认 `docs/AI-CONTINUE.md` §9 待决事项 |
| Step 3 后端（Spring Boot） | ✅ 完成（T0~T9） | `backend/` Spring Boot 3.5.16 骨架 + MySQL 专用实例 3307 / Redis 6379；**T0 ✅、T2a 鉴权 ✅（2026-10-17）、T1 数据库迁移与演示种子 ✅、T2b 注册 ✅、T3a 职位公开读 + 分类字典 ✅、T3b HR 职位写 ✅、T4 投递与面试闭环 ✅、T5 公司模块 + 企业认证 ✅、T6 消息 + WebSocket ✅、T7 求职者 / 简历 / 收藏 / 上传 ✅（2026-10-18）、T8 招聘者 / 候选人 / 人才筛选 ✅、T9 全量联调 ✅（2026-10-19，实测见 `docs/state/commands.md`）** |
| Step 3-T9 联调 | ✅ 基本完成 | `NEXT_PUBLIC_USE_MOCK=false` 已切；`npm run e2e` 在真实后端上 **63/63**；**求职者职位列表 / 详情已切真实**（`listJobs` → `GET /jobs`，HR 发布 / 关闭对求职者立即生效）；遗留：求职者端「分类字典 / 消息会话」与 HR「发消息」入口仍为 mock 驱动（见 `docs/state/backend-facts.md`） |
| Step 4 AI / RAG 增量 | ✅ T-AI0~T-AI4 完成 | [`docs/step4-ai-plan.md`](./docs/step4-ai-plan.md)：HR 简历问答（带引用）→ 语义人才检索；**T-AI0 ✅**（Spring AI 1.1.8 + 环境变量 + `/ai/status|ping`）；**T-AI1 ✅**：V9 `resume_chunks` + 分块 + 真实 1024 维嵌入 + 重建 / 状态接口；**T-AI2 ✅**：检索 + 引用 + 无依据话术 + 配额 + HR 候选人详情 AI 抽屉；**T-AI3 ✅**：SSE 流式 + 打字机 + 引用双向定位；**T-AI4 ✅（2026-10-20）**：语义人才检索 `POST /hr/talent/semantic-search`（query+tags 向量化、公司可见范围前置过滤、topK + 证据片段）+ 筛选页关键词 / 语义 A/B，`mvnw test` 253/253、`smoke-ai` 54/54、e2e 68/68 |
| 可观测性 ARMS 增量（T-OBS） | ✅ T-OBS1 + T-OBS2 完成 | [`docs/arms-plan.md`](./docs/arms-plan.md)：路径 B（阿里云 ARMS **应用监控** Java 探针）已打通 —— 控制台可见 `pinliao-backend`、包络 traceId 即链路 TraceId、应用 JVM 有上报连接；带探针 `mvnw test` 182/182、真实后端 e2e **63/63** 无回归。**修脚本 bug**：`-Background` 的 `Start-Process -ArgumentList` 数组丢引号导致 `-Darms.*` 没进应用 JVM |

## 2. 当前增量：Step 4 T-AI4 语义人才检索（与关键词版 A/B，2026-10-20）

| 项 | 内容 |
|---|---|
| 后端检索 | `ai/search/SemanticTalentSearchService`：`query + tags` 只向量化一次 → 在**本公司可见投递的简历**分块内做余弦 → 按候选人最高分聚合（同人多投递去重）→ 倒序取 topK；相似度低于 `search-min-score` 不返回，每人最多 3 条证据 |
| 多租户红线 | 先查 `applications.company_id = 当前公司 AND deleted_at IS NULL` 得到 `resume_id` 集合，再 `resume_chunks.resume_id IN (...)` 前置过滤；**没有**「全局检索后丢弃」路径 |
| 端点 | `POST /api/v1/hr/talent/semantic-search`，入参 `{query?, tags?, limit?}`（至少一个非空，`query` ≤200 字、`tags` ≤20 个、`limit` ≤20），出参 `SemanticMatch[]`（candidate + `score` + `evidences[{chunkId, section, title, snippet, score}]`）；索引维度不一致 `42221` |
| 配置 / 配额 | `pinliao.ai.search-top-k`（默认 8）/ `search-min-score`（默认 0.15，环境变量 `PINLIAO_AI_SEARCH_TOP_K` / `_SEARCH_MIN_SCORE`）；与问答共用 `AiQuotaService` 的按 HR / 按日配额（`42902`，Redis 故障降级放行） |
| 索引 | 缺失索引按需补建（单份失败跳过）；`ResumeChunkStore.findByResumesAndModel` 批量取块，`RetrievalService.parseVector/cosine` 改为复用 |
| 前端 | `HrTalentMatchPage` 增「关键词 / 语义检索」模式切换（仅 `/ai/status.enabled=true` 显示）；两种模式各自 TanStack Query（`HR_TALENT_MATCH_KEY` / `HR_SEMANTIC_TALENT_MATCH_KEY`）；语义卡片展示 `score` 百分比 + 证据片段，含加载 / 空结果 / 配额与上游错误态 |
| Mock 分支 | `matchCandidatesSemantic` 用关键词命中 + 字符重叠近似打分（阈值 0.35），仅保证交互可演示；**不是 embedding 语义**，真实效果以 `provider=springai` 为准 |
| 诚实口径 | 语义模式不理解「必须 / 优先」等约束，只按向量相似度排序；`provider=fake` 的向量是词袋哈希，无语义能力 |

**最近一次复验（2026-10-20，本轮代码）**：

| 检查 | 结果 |
| --- | --- |
| `backend\mvnw.cmd test` | **253/253 通过**（新增 `SemanticTalentSearchServiceTest` 13 项：角色 / 空条件 / 超长 / limit / 公司范围 / 按需索引 / 分数阈值 / 同人去重 / 证据条数 / 维度不一致 / 配额顺序） |
| `node backend\scripts\smoke-ai.mjs` | **54/54 通过**（`provider=fake`；新增语义检索 11 项：鉴权、入参、公司范围、排序与证据、空结果、配额等） |
| `npm run e2e` | **68/68 通过**（真实后端 + fake AI provider；Mock 分支同为 68/68）；新增 3 项语义断言（入口 / 结果 / 无关候选），AI 关闭时随入口隐藏跳过 |
| `npx tsc --noEmit` | 仅 3 个**既有**错误（`examples/websocket/*`、`src/lib/db.ts`），本轮文件零错误 |
| `npx eslint`（本轮新增 / 修改文件） | 0 告警 |
| dev server | 端口 3000 HTTP 200；Chrome 进程数跑前跑后不变 |

## 3. 下一步

| 优先级 | 事项 | 参考 |
| --- | --- | --- |
| ① | **T-AI5 面试问题生成**（Step 4 主线） | `docs/step4-ai-plan.md` §7 |
| ② | T-OBS3：控制台核对慢 SQL / 异常 / JVM（前端监控最后做） | `docs/arms-plan.md` §4 |
| ③ | T10：求职者「分类字典 / 消息会话」+ HR「发消息」切真实（先补 e2e 断言再动代码） | AI-CONTINUE §9 / pitfalls 6.18 |
| ④ | 待用户拍板事项——**不要替用户决定** | AI-CONTINUE §9、`step4-ai-plan.md` §10 |

现状：Step 3 全部完成（T0~T9 + T9 追加）；Step 4 已完成 T-AI0~T-AI4。
每个 T 具体交付了什么 → `docs/archive/progress-history.md`（追溯时才读）。

## 4. 已锁定决策（沿用，勿回退）

- HR 端第 4 个 Tab「候选人」；详情 / 面试邀约 / 公司资料 / 企业认证走 `hr` store
  `pageStack` 覆盖页；求职者「我的投递」为 `pinliao` store 的 `applications` 页。
- 面试状态在求职者侧使用候选人视角文案（「待你确认 / 已接受邀请」），与 HR 端口径区分。
- 种子投递避开云杉公司；跨端闭环由 e2e 现场「求职者投递云杉职位」形成。
- 验证码固定 `123456`（60s 限流按手机号）；HR 注册自动建未认证新公司，与企业认证联动。
- 企业认证：提交 → `PENDING` → 约 1200ms 自动通过；契约 §5.6；审核进度复用 §5.3 轮询。
- 人才筛选（演示版）：范围限「站内投递候选人」；**T-AI4 已加语义模式**（`POST /hr/talent/semantic-search`，
  与关键词版并行 A/B，页面可切换）；HR 上传外部简历库为后续增量。
- 登录成功后必须 `setTab('jobs')` 重置双端落地页签（详见 `docs/pitfalls/frontend.md` §6.7）。
- 新建记录 id 一律递增计数（不用 `Date.now` / `Math.random`）。

## 5. 功能任务状态明细（验收五项 + 后续增量）

| # | 任务 | 数据层 | Service | UI | 验收 | 判定 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 投递流程（求职者投递 / 撤回） | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 2 | 候选人管理页（HR 按状态筛选 / 标记） | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 3 | 面试邀请闭环（邀约 / 接受 / 拒绝） | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 4 | 手机验证码登录 / 注册 | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 5 | 公司资料编辑 + 企业认证 | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 6 | 人才筛选（演示版增量） | ✅ | ✅ | ✅ | ✅ e2e 6 项 | 完成 |

## 6. 已知问题与风险

### 6.1 既有报错（不是你造成的，不要修）

- `npx tsc --noEmit` 报 3 个：`examples/websocket/frontend.tsx`（缺 socket.io-client）、
  `examples/websocket/server.ts`（缺 socket.io）、`src/lib/db.ts`（缺 Prisma client 生成）。
- `npx eslint src` 有少量既有告警（`src/components/ui/*`、`src/hooks/use-mobile.ts`）。
- 浏览器控制台：`next-themes` 注入 script 的告警（无害）、`favicon.ico` 404（未放置图标）。
- **AI（Step 4）需要 15 个用户级环境变量**（`PINLIAO_AI_*`，见 `docs/state/ai-config.md`）；
  未设时后端仍能正常启动（`api-key` 有占位默认值），仅 AI 接口不可用。
  另：`db-reset.ps1` 曾只在 PowerShell 7 可用（5.1 下 JDK 探测恒返回 0），T-AI0 已修。

### 6.2 需要留意的风险点

| 风险 | 说明 |
| --- | --- |
| 契约账目 | 已定稿（2026-10-18，用户拍板）：以正文 70 条为准；`docs/api-endpoints.md` v0.3.6 已将总览表鉴权 7 / 职位 10 / 消息 9 校正，分模块合计 70，不再有 67/70 差异 |
| e2e 文本匹配点击 | 脚本按文本找按钮（取首个含文本的 `button`）。新增 UI 文案（如「投递」「沟通」「我的」「注册」）可能干扰既有断言；登录提交已改用 `clickSubmit`，覆盖层退出用 `clickAria`；**覆盖层内取文本用顶层 `.pl-page-enter`**（`body.innerText` 含底层页面，已加 `matchOverlayText` 助手），扩展脚本时逐条核对 |
| 新公司 HR 的演示数据 | 新注册 HR 的公司为空（无职位 / 候选人）属预期；演示完整数据请用云杉演示账号 |

> **环境 / 兼容性 / 各 T 阶段接口事实**（租户注入、错误码、游标分页、ETag、上传、WebSocket、
> T9 联调差异、mock 遗留路径）已移到 [`docs/state/backend-facts.md`](./docs/state/backend-facts.md)。
> 改后端或排查接口时按需查，不要整读。

## 7. 命令速查

```powershell
npm run dev            # 前端 :3000（e2e 需要它常驻）
npm run e2e            # 端到端验收，基线 68/68
npx tsc --noEmit       # 类型检查（基线仅 3 个既有错误，见 §6.1）
npx eslint src         # 存量告警见 §6.1
```

```powershell
cd backend
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr'  # 必设
.\scripts\dev-up.ps1        # MySQL 3307 + Redis 6379
.\scripts\db-reset.ps1      # 每轮 e2e 前：清库重迁 + 清 Redis + 起后端 + 等 health
.\mvnw.cmd spring-boot:run  # 后端 :8080（收工 .\scripts\dev-down.ps1）
.\mvnw.cmd test             # 基线 253/253
```

冒烟脚本逐项、验证分级、验收清单、实测记录 → [`docs/state/commands.md`](./docs/state/commands.md)。

## 8. 文档地图

一页表（想知道什么 → 读哪个文件的哪一节）：[`docs/INDEX.md`](./docs/INDEX.md)。

维护铁律：**状态只写本文件**；**踩坑只写 `docs/pitfalls/`**；**历史只进 `docs/archive/`**。
