<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# 聘聊 · 开发约束与规范（AGENTS.md）

> 本文件是给在本仓库工作的 AI Agent（以及人）的**硬性约束与操作规范**。
> **开工只读三样**：本文件（约束）→ `PROGRESS.md` §0~§3（当前状态 / 下一步）→
> `docs/INDEX.md`（按需检索表）。**不要整读 `docs/`**：其余文档先 `grep` 定位，再按行段读，单次 ≤ 300 行。
> 上方 `nextjs-agent-rules` 区块由 `next dev` 自动维护，勿删；允许在其后追加内容。

## 0. 项目速览

聘聊 = Boss 直聘模式招聘平台原型（求职者端 + HR 端双端 H5）：Next.js 16 + Tailwind 4 + Zustand +
TanStack Query；后端 Spring Boot 3.5.16（`backend/`）+ MySQL 3307 / Redis 6379 专用实例，前端默认联调真实后端。

- **Step 2 前端验收**：五项验收功能 + 人才筛选（演示版）完成，e2e 65/65；用户人工验收初步通过（2026-10-16）。
- **Step 3 后端**：T0~T9 全部完成，含全量联调（`USE_MOCK=false`，e2e 65/65）。
- **Step 4 AI**：T-AI0~T-AI3 完成（索引 / 单简历问答 / SSE 流式）；下一步 T-AI4 语义人才检索。
- **可观测性**：T-OBS1 + T-OBS2 完成（阿里云 ARMS 应用监控，链路已打通）。

状态明细见 `PROGRESS.md` §1；每个 T 交付了什么见 `docs/archive/progress-history.md`。
演示账号：`13800000001`（求职者）/ `13800000002`（HR），密码均 `123456`；短信验证码固定 `123456`。

## 1. 运行与验收（统一用 npm，不用 bun）

```powershell
npm run dev          # dev server，端口 3000（e2e 需要它常驻）；联调态读取 .env.local 的 USE_MOCK=false
npm run e2e          # 端到端验收（tests/e2e-verify.cjs）；基线 65/65（Mock 与真实后端相同）
npx tsc --noEmit     # 类型检查；基线仅 3 个既有错误（见 §3）
npm run lint         # ESLint
npm run build        # 生产构建（需能访问 Google Fonts）
./scripts/doc-size.ps1          # 文档体量体检（超软上限告警）
./scripts/doc-size.ps1 -Strict  # 超硬上限返回非 0；提交前跑一次
```

后端（`backend/`，Step 3；详见 `backend/README.md`）：

```powershell
cd backend
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr'  # 必设：本机系统仅 JDK 1.8
.\scripts\dev-up.ps1        # 起 MySQL 专用实例 3307 + Redis 6379（首次先 .\scripts\db-init.ps1）
.\scripts\db-reset.ps1      # 每轮 e2e 前：清库重迁（drop+create → Redis FLUSHALL → 起后端 → 等 health UP）
.\mvnw.cmd spring-boot:run  # 后端 :8080；收工用 .\scripts\dev-down.ps1
.\mvnw.cmd test             # 单元测试；基线 240/240
node scripts\smoke-ai.mjs   # AI 索引 + 问答冒烟（43 项，需 PINLIAO_AI_ENABLED=true，默认拒绝真实上游）
```

其余冒烟脚本（`smoke-auth` 20 / `smoke-jobs` 49 / `smoke-applications` 51 / `smoke-company` 26 /
`smoke-messages` 40 / `smoke-user` 64 / `smoke-recruiter` 50 / `verify-seed` 16）的逐项说明与验证分级
→ `docs/state/commands.md`，需要时再查，不要凭记忆跑。

## 2. 硬性约束（违反会破坏已验收功能）

1. **单页 SPA，不引入路由**：视图切换一律走各自 store 的 `tab` + `pageStack`
   （求职者 `store/pinliao.ts`、HR `store/hr.ts`）。禁止 `next/navigation`。
2. **service 层隔离**：组件不直接 `fetch`；新接口照 `src/api/hrJobs.ts` 写
   `if (USE_MOCK) {...} return request<T>(...)` 双分支；接口以 `docs/api-endpoints.md` +
   `src/types/api.ts` 为准，不要自造。
3. **身份校验先于写登录态**：登录相关校验必须在写入 auth store / token 之前完成
   （否则 RootGate 会先切换页面，提示永远看不到）。
4. **水合安全**：persist store 一律 `skipHydration: true`（由 RootGate 统一 rehydrate）；
   不在模块初始化阶段读 localStorage / 用 `Date.now()` 参与渲染；业务数据只在登录后的
   客户端路径渲染；**新建 id 用递增计数**，禁止 `Date.now()` / `Math.random()`。
5. **zustand 选择器只返回稳定引用或原始值**；需要派生对象时在组件内 `useMemo` 组装，
   否则无限重渲染。
6. **取数用 TanStack Query**：不要手写 `useEffect + setState`（React Compiler 规则为 error）；
   「取数 + 表单回填」拆成两个组件。
7. **Mock 数据在内存，刷新即重置**：跨端联动测试必须用应用内「退出登录」切换账号，
   不能刷新页面。
8. **不要动 `src/components/ui/`**（shadcn 生成代码）。
9. **清理无头 Chrome 只能用 `.zscripts/kill-chrome-safe.cjs` 的 `killChromeTree(pid)`**；
   严禁 `Get-Process chrome | Stop-Process -Force`（会杀掉用户全部标签页）。
10. **求职者可见职位必须走 `listSeekerJobs()` / `jobById()`**（含 HR 关闭/删除/新建联动），
    不要直接读静态 `jobs` 数组。
11. **文档分层不许打回原形**：状态只写 `PROGRESS.md`，坑只写 `docs/pitfalls/`，
    历史只进 `docs/archive/`；提交前 `./scripts/doc-size.ps1 -Strict` 必须通过。

## 3. 已知既有报错（不要试图修）

- `npx tsc --noEmit` 3 个：`examples/websocket/*` 缺 socket.io 依赖、`src/lib/db.ts` 缺 Prisma client。
- `npx eslint src` 有 `src/components/ui/*` 与 `src/hooks/use-mobile.ts` 的存量告警。
- `favicon.ico` 404、`next-themes` script 告警均无害。

## 4. 文档维护义务

### 4.1 写哪里（单一出处，别复制第二份）

| 内容 | 唯一去处 | 禁止 |
| --- | --- | --- |
| 当前状态 / 下一步 / 风险摘要 | `PROGRESS.md`（只写「现在」） | 在这里堆历史 |
| 某次做了什么 | `worklog.md` 追加一条（Task ID 递增，单条 ≤ 60 行） | 复述全量 diff |
| 版本变更 | `CHANGELOG.md` 当前阶段条目 | 保留全部历史 |
| 踩坑 | `docs/pitfalls/<主题>.md` + `AI-CONTINUE.md` §6 索引一行 | 堆进 AI-CONTINUE 正文 |
| 命令 / 验收 / 测试基线 | `docs/state/commands.md` | README、PROGRESS 各写一份 |
| 后端接口事实 / 环境坑 | `docs/state/backend-facts.md` | 分散多文件 |
| 历史（旧日志 / 旧版本 / 旧计划） | `docs/archive/`（原文切片） | 回填进主文件 |

### 4.2 归档触发（达到即做，不要等）

- `worklog.md` 超过 3 个阶段或 200 行 → 把上一阶段整体移到 `docs/archive/worklog-<stage>.md`。
- `CHANGELOG.md` 超过 5 个版本 → 更早的移到 `docs/archive/changelog-<stage>.md`。
- 任一非归档 `.md` 超 20 KB → 拆文件，并在 `docs/INDEX.md` 补/改一行。
- 每次提交前 `./scripts/doc-size.ps1 -Strict`。

### 4.3 其他

- 契约变化同步 `docs/api-endpoints.md`；方案变化同步对应 `docs/*-plan.md` 的任务板。
- 提交信息用中文，说明「改了什么 + 为什么」；提交时包含 `AGENTS.md` / `CLAUDE.md`
  以保持工作区干净（该区块由 `next dev` 自动重新生成）。
- 不要为整理文档而重构业务代码；不要提交 `.env` 与本机私有产物。
- 待用户确认的事项（前端最终框架、招聘者端桌面布局等）**不要替用户决定**，
  清单见 `docs/AI-CONTINUE.md` §9。
