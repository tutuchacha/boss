# 聘聊 · AI 接续开发指南

> **本文件的目的**：让任何环境中的 AI（或人）读完这一份文件，就能直接继续开发本项目，
> 无需访问历史会话、无需询问用户、不会踩已踩过的坑。
>
> **本文件的定位**：运维与经验「深读手册」（命令、Mock 数据位置、已踩坑、协作规范）。
> **当前进度与工作区状态**以根目录 [`PROGRESS.md`](../PROGRESS.md) 为准
> （2026-10-16 起另有交接四件套：`PROJECT.md` / `ARCHITECTURE.md` / `PROGRESS.md` / `CHANGELOG.md`）。
> 开工顺序：先 `PROGRESS.md`，再本文件；需要细节时再按下文索引深入 `docs/` 的专项文档。
>
> 最后更新：2026-10-19 · 对应本轮提交（**可观测性 T-OBS2：ARMS 链路打通** —— 控制台可见 `pinliao-backend`、包络 traceId 即链路 TraceId、修 `-Background` 引号 bug，带探针 `mvnw test` 182/182、前端 e2e 63/63 无回归）；Step 4 侧**下一步 T-AI1 索引入库**
> 维护要求：**每次改变项目状态（新增功能、改架构、修坑）后，必须回来更新本文件**，
> 否则它会像之前的 `HANDOFF.md` 一样过期并误导后来者。

---

## 0. 三分钟上手

```bash
npm install
npm run dev          # 端口 3000
```

打开 <http://localhost:3000>，用演示账号登录（登录页有「一键填入」按钮）：

| 身份 | 账号 | 密码 |
|---|---|---|
| 求职者 | `13800000001` | `123456` |
| 公司代表 HR | `13800000002` | `123456` |

**无需数据库、无需后端**：全部数据来自前端 Mock，开箱即跑。

> **当前状态一句话**：Step 3 后端 **T0~T9 全部完成**（含全量联调：`USE_MOCK=false` 下 `npm run e2e`
> 63/63 连续两轮）；Step 4 AI / RAG 增量 **T-AI0 已完成**（Spring AI 1.1.8 + 百炼 DashScope 兼容模式
> 打通，`/api/v1/ai/status|ping` 实测可用，密钥只进用户级环境变量；`mvnw test` 176/176）；遗留
> 求职者「分类字典 / 消息会话」与 HR「发消息」入口仍读 mock。方案与任务板见
> [`step4-ai-plan.md`](./step4-ai-plan.md)。当前任务与下一步先看
> [`../PROGRESS.md`](../PROGRESS.md)（状态唯一定稿）。

### 环境注意（换环境时最先检查这四条）

1. **不要用 `bun`**。`package.json` 里另有一份 `bun.lock`，但目标机器未安装 bun；
   请统一用 `npm` / `npx`。若你的环境有 bun，两种都能跑，但不要混用锁文件。
2. **端口固定 3000**（`next dev -p 3000`）。旧文档里写的 `5173` 是 Vite 默认端口，是错的。
3. **`prisma generate` 不是启动前置条件**。当前无人 `import src/lib/db.ts`，
   Prisma 未接入业务（详见 §7.4）。
4. **`npm run build` 需要能访问 Google Fonts**（`layout.tsx` 用 `next/font/google` 加载 Geist）。
   国内直连会构建失败，设代理即可：
   ```powershell
   $env:HTTPS_PROXY='http://127.0.0.1:7897'; $env:HTTP_PROXY='http://127.0.0.1:7897'; npm run build
   ```

---

## 1. 项目是什么

**聘聊**：参考 Boss 直聘模式的招聘平台原型。

- **C 端求职者 + 多 B 端企业客户共存**。求职者跨企业共享（一份简历可投多家）。
- **每个注册企业即一个「租户」**，主键 `companyId`；求职者账号不属于任何租户。
- **首发只做 H5/Web**（不含 App、小程序）。
- **前端已切真实后端**（Step 3 T9 起）：`.env.local` 置 `NEXT_PUBLIC_USE_MOCK=false` +
  `NEXT_PUBLIC_API_BASE_URL=/api/v1`，由 `next.config.ts` rewrites 代理到 `:8080`；改后须重启 dev server。
  仍读 mock 的只剩求职者「分类字典 / 消息会话」与 HR「发消息」入口（见 §6.18 末尾）。

---

## 2. 当前状态（务必按此判断，不要沿用旧文档）

| 阶段 | 状态 | 产物 |
|---|---|---|
| Step 0 架构 + 数据模型 + API 契约 | ✅ 完成 | `docs/architecture.md`、`data-model.md`、`api-contract.md`、`api-endpoints.md`、`prisma/schema.prisma`、`src/types/api.ts` |
| Step 1 前端开发（Next.js + Mock） | ✅ 完成 | `src/components/pinliao/`、`src/api/`、`src/store/` |
| Step 2 前端验收 | ⏳ 进行中 | 五项验收功能已完成，并新增人才筛选演示增量；e2e 63/63 通过；**用户人工验收初步通过（2026-10-16）**；待确认 §9 待决事项 |
| Step 3 后端（Spring Boot） | 🔄 进行中 | `backend/`（Spring Boot 3.5.16）：T0 骨架 ✅、T2a 鉴权 ✅（2026-10-17）、T1 全量表 + 演示种子 ✅、T2b 注册 ✅、T3a 职位公开读 + 分类字典 ✅、T3b HR 职位写 ✅、T4 投递与面试闭环 ✅、T5 公司模块 + 企业认证 ✅、T6 消息 + WebSocket ✅、T7 用户 / 简历 / 收藏 / 上传 ✅（2026-10-18）、T8 招聘者 / 候选人 / 人才筛选 ✅（2026-10-19）、**T9 全量联调 ✅（2026-10-19，`USE_MOCK=false` 下 `npm run e2e` 63/63，连续两轮通过）**；进度与实测以 `../PROGRESS.md` §7.2 为准 |
| Step 3-T9 联调 | ✅ 基本完成 | 前端已切真实后端（`.env.local`：`NEXT_PUBLIC_USE_MOCK=false`，`API_BASE_URL=/api/v1` 经 Next rewrites 代理到 :8080）；**求职者职位列表 / 详情已切真实**（`listJobs` → `GET /jobs`）；遗留：求职者端「分类字典 / 消息会话」仍读 mock，HR「发消息」入口仍为 mock 专用（详见 §6.18 末尾与 §9） |
| Step 4 AI / RAG 增量 | 🔄 进行中（T-AI0 ✅） | [`step4-ai-plan.md`](./step4-ai-plan.md)：HR 简历问答（带引用）→ 语义人才检索；**T-AI0 已落地**（`spring-ai-bom:1.1.8` + `spring-ai-starter-model-openai`、`ai/` 包、`/api/v1/ai/status|ping`、密钥走用户级环境变量、`mvnw test` 176/176）；下一步 T-AI1 索引入库（V9 `resume_chunks` + `ResumeChunker`） |

> ⚠️ **状态快照（2026-10-16）**：五项功能（投递 / 候选人 / 面试、验证码登录 /
> 注册、公司资料 / 企业认证）已全部接入 UI，并新增人才筛选（演示版）增量；自动化验收
> `npm run e2e` **63/63** 通过，改动随本轮提交入库（见 `git log` 最新提交）；若工作区
> 仍有未提交文件，以 [`../PROGRESS.md`](../PROGRESS.md) §0 的快照为准。

### 已实现的功能

**求职者端**：职位列表（城市/搜索/分类联动/筛选/排序）、职位详情（含**投递简历**）、
收藏、简历预览、求职期望编辑、**我的投递（撤回 / 接受或婉拒面试邀请）**、消息会话、
聊天页（快捷回复 + 关键词自动回复）、外观切换、**密码 / 验证码登录、注册、退出登录**。

**HR 端**（与求职者端同一移动端布局，430px 居中）：
- **职位管理**：状态筛选、关键字搜索、仅看我发布的；发布 / 编辑 / 关闭 / 重新开放 /
  删除（软删 + 二次确认）；数据概览（招聘中数量、累计投递、累计浏览）
- **候选人**：按投递状态筛选、搜索、候选人详情（简历完善度、标记已查看 / 录用 /
  不合适）、**发起面试邀请**（详情转「面试中」，候选人端可接受 / 婉拒）
- **候选人沟通**：会话列表（候选人画像 + 应聘职位 + 未读角标）、仅看未读、按职位筛选、
  搜索；聊天页（气泡、快捷回复、候选人自动回复含「正在输入」、进入即标记已读、
  面试邀请卡片）
- **公司资料与企业认证**：资料编辑（改名同步到职位卡片）、企业认证提交
  （Mock 约 1.2s 自动通过、页面轮询更新、状态同步「我的」）
- **人才筛选（演示版）**：按技能需求（自然语言 + 标签）在**站内投递候选人**中关键词匹配，
  展示命中关键词与简历证据片段（原文高亮）；接口预留语义检索升级（§4.5 / §4.6；HR 上传
  外部简历库为后续增量）
- **我的**：公司信息、公司资料、企业认证入口、退出登录

### 尚未实现（不要误以为已有）

| 功能 | 备注 |
|---|---|
| 招聘者端桌面 Web 后台 | 当前仅移动端布局 |
| 真实后端（其余模块） | Step 3 进行中：T0~T9 已落地（鉴权 / 注册 / 职位 / 投递面试 / 公司 / 消息 WebSocket / 用户简历收藏上传 / 招聘者与人才筛选 / **全量联调**）；§6.18 末尾列出仍需从 mock 切到真实的前端路径 |

---

## 3. 技术栈与架构约定

| 层 | 选型 |
|---|---|
| 框架 | Next.js 16（App Router + Turbopack） |
| 语言 | TypeScript 5 |
| 样式 | Tailwind CSS 4 + shadcn/ui，青绿主题（`--primary: #00857F`） |
| 客户端状态 | Zustand（含 `persist`） |
| 服务端状态 | TanStack Query（两端新模块均用；求职者端列表筛选仍走同步 Mock） |
| ORM | Prisma（**未接入业务**） |

> `zod`、`react-hook-form` 随模板装好但**代码零引用**，不要误以为在用。

### 五条必须遵守的架构约定

**1. 单页 SPA，不引入路由。**
所有视图通过各自 store 的 `tab` + `pageStack` 切换。`src/app/page.tsx` 是唯一可见路由，
只渲染 `RootGate`。**引入 `next/navigation` 会改变架构**，并让已验收的页面出现白屏风险。
（`src/components/pinliao/RootGate.tsx` 顶部注释记录了这个决策。）

**2. service 层隔离。**
组件只依赖 `src/api/*`，不直接 `fetch`。`src/config/env.ts` 的 `USE_MOCK` 控制
Mock / 真实接口切换，组件无感。新增接口请照 `src/api/hrJobs.ts` 的写法：
`if (USE_MOCK) { ...mock... } return request<T>(path, ...)`。

**3. 前端框架最终选型未定。**
当前 Next.js 只是**沙箱验收用**；`docs/HANDOFF.md` 记载最终上线框架待用户决定。
若将来出 App/小程序，计划另起 uni-app 工程复用同一套 API 契约。

**4. 多租户。**
业务表带 `companyId`；生产后端由 MyBatis-Plus 拦截器注入 `WHERE company_id = ?`。
（原 PostgreSQL RLS 兜底方案随数据库改 MySQL 作废，见 `docs/step3-plan.md` §1。）
求职者跨租户共享的表（`User`/`UserProfile`/`Resume`）**不带** `companyId`。

**5. 水合安全（hydration）。**
persist store 一律 `skipHydration: true`，由 `RootGate` 挂载后统一 `rehydrate()`。
**不要在模块初始化时读 localStorage** —— 否则 SSR/CSR 不一致会触发
`Hydration failed`，React 会丢弃服务端 HTML 整棵树重建。

---

## 4. 目录结构

```
src/
├─ app/                      Next.js 入口（单页 SPA）
│  ├─ page.tsx               渲染 RootGate
│  ├─ layout.tsx             根布局（主题、字体、Query Provider、Toast）
│  └─ globals.css            青绿主题 token（--pl-* / --primary）
├─ components/
│  ├─ pinliao/               业务组件
│  │  ├─ RootGate.tsx        按登录状态与角色分发（登录页 / 求职者端 / HR 端）
│  │  ├─ LoginPage.tsx       登录页（密码 / 验证码 / 注册三模式 + 演示账号）
│  │  ├─ AppShell.tsx        求职者端外壳
│  │  ├─ JobsView / JobDetailPage / ChatPage / MessagesView / MeView /
│  │  │  MyApplicationsPage（我的投递）/ …
│  │  └─ hr/                 HR 工作台
│  │     ├─ HrShell.tsx      外壳（四 Tab：职位管理 / 候选人 / 沟通 / 我的）
│  │     ├─ HrJobsPage.tsx   职位管理列表
│  │     ├─ HrJobEditorPage.tsx  发布/编辑表单
│  │     ├─ HrCandidatesPage.tsx / HrCandidateDetailPage.tsx / HrInterviewInvitePage.tsx  候选人
│  │     ├─ HrTalentMatchPage.tsx  人才筛选（关键词匹配 + 证据片段高亮）
│  │     ├─ HrMessagesPage.tsx   候选人会话列表
│  │     ├─ HrChatPage.tsx   聊天页
│  │     ├─ HrCompanyEditPage.tsx / HrCompanyVerifyPage.tsx  公司资料 / 企业认证
│  │     └─ HrMePage.tsx     我的
│  └─ ui/                    shadcn/ui 基础组件（勿手改）
├─ api/                      service 层
│  ├─ client.ts              统一 fetch + 包络解包 + ApiError
│  ├─ auth.ts                密码 / 验证码登录、注册、退出
│  ├─ jobs.ts                职位（求职者视角）
│  ├─ hrJobs.ts              HR 职位管理 + 表单校验
│  ├─ hrConversations.ts     HR 会话
│  ├─ applications.ts / hrApplications.ts  两端投递与面试
│  ├─ hrTalentMatch.ts       人才筛选（候选人匹配 / 技能标签词表）
│  ├─ company.ts             公司资料 / 企业认证
│  ├─ index.ts               出口（当前无人使用，按模块直接导入）
│  └─ mock/                  Mock 数据与装配
├─ store/                    Zustand
│  ├─ pinliao.ts             求职者端（持久化键 pinliao.v3）
│  ├─ auth.ts                认证（持久化键 pinliao.auth.v1）
│  └─ hr.ts                  HR 工作台（不持久化）
├─ hooks/useStoreHydration.ts  水合守卫
├─ lib/                      工具（time.ts / utils.ts / db.ts=未使用）
└─ types/api.ts              与契约同步的类型定义
```

---

## 5. 运维与验证手册

### 常用命令

```bash
npm run dev        # 开发服务器（3000）
npm run e2e        # 端到端验收（需先开着 dev server；见下）
npm run build      # 生产构建（需 Google Fonts，见 §0）
npm run lint       # ESLint
npx tsc --noEmit   # 类型检查
```

### 测试账号与 Mock 数据位置

| 内容 | 位置 |
|---|---|
| 测试账号、HR 画像、公司绑定 | `src/api/mock/auth.ts` |
| 职位基础数据（51 条） | `src/api/mock/jobs.ts` |
| HR 端可变职位数据 | `src/api/mock/hrJobs.ts` |
| HR 端可变会话数据 | `src/api/mock/hrConversations.ts` |
| 候选人种子（6 人，跨页共享） | `src/api/mock/candidates.ts` |
| 候选人简历正文种子（人才筛选数据源） | `src/api/mock/resumes.ts` |
| 人才筛选匹配逻辑（关键词 + 证据片段） | `src/api/mock/talentMatch.ts` |
| 投递 / 面试可变数据 | `src/api/mock/applications.ts` |
| 公司字典 / 资料与认证记录 | `src/api/mock/companies.ts` / `companyOps.ts` |
| 求职者可见职位的**合成层** | `src/api/mock/seekerJobs.ts` |
| Mock 数据装配（打破循环依赖） | `src/api/mock/bootstrap.ts` |

### 已知的既有报错（不是你造成的，不要试图修）

- `npx tsc --noEmit` 会报 3 个错误：`examples/websocket/*` 缺 `socket.io` 依赖、
  `src/lib/db.ts` 缺 Prisma client。均不影响运行。
- `npx eslint src` 会报 `src/components/ui/*`（shadcn 原有代码）与
  `src/hooks/use-mobile.ts` 的 `set-state-in-effect` 告警。属既有问题。
- 浏览器控制台会有 `Encountered a script tag while rendering React component`：
  来自 `next-themes` 注入 script 防主题闪烁，**无害**。
- `favicon.ico` 404：项目未放置图标。

### 构建与生产启动（这两个脚本曾经是坏的，已修）

`npm run build` 与 `npm start` 原先依赖 Unix 命令与 bun，在 Windows 上**完全不可用**：

| 旧写法 | 问题 |
|---|---|
| `build: next build && cp -r ...` | Windows 无 `cp` → **退出码 1**，静态资源从未拷入 standalone，产物不可用 |
| `start: NODE_ENV=production bun .next/standalone/server.js` | 依赖未安装的 bun；`NODE_ENV=x cmd` 是 Unix 语法 |

现改为跨平台、不依赖 bun：

```bash
npm run build      # 内部调用 .zscripts/build-portable.cjs（用 Node 拷贝 + 产物自检）
npm start          # 等价于 node .next/standalone/server.js
```

- 构建脚本会**自检** `server.js` 与 `.next/static` 是否就位，缺一即报错退出
- 指定端口：`$env:PORT=3001; npm start`（Windows PowerShell）
- 已实测：构建退出码 0、生产服务器 HTTP 200、静态资源 200

> 仍受 Google Fonts 限制：`npm run build` 需要能访问 `fonts.googleapis.com`（见 §0）。
> 这是唯一剩余的外部依赖；删掉它需自托管字体，但**无法可靠判定已缓存 woff2 的字重映射**
> （猜错会改变字形），故未擅自处理，留待确认。

### 验收清单（给用户逐页过）

1. 未登录 → 停在登录页；演示账号可一键填入；**密码 / 验证码 / 注册**三种方式均可进入
   （验证码固定 `123456`）
2. 错误密码 → 提示「账号或密码错误」
3. 用 HR 账号在「求职者」身份下登录 → 提示身份不匹配并可一键切换
4. HR 正确登录 → 工作台，底部四个 Tab（职位管理 / 候选人 / 沟通 / 我的）
5. **职位管理**：发布（空表单应被校验拦截）→ 列表出现 → 关闭（变已关闭）→ 重新开放 → 删除
6. **候选人**：按状态筛选、搜索；详情可标记已查看 / 录用 / 不合适、发起面试邀请
7. **沟通**：6 个候选人会话、未读角标、仅看未读、按职位筛选、搜索；进聊天页发消息、收到自动回复
8. **求职者投递闭环**：详情投递简历 → 「我的投递」可见进度 → 可撤回；收到的面试邀请可接受 / 婉拒
9. **跨端联动**：求职者投递 → HR 候选人里约面试 → 求职者接受 → HR 回看「候选人已接受」
10. **公司资料 / 企业认证**：改名后职位卡片同步；认证提交后约 1.2s 自动通过、状态同步
    「我的」角标；新注册 HR 默认「未认证」
11. 两端「我的」均可退出登录，退出后回到登录页

### 自动化验收记录

**最近一次结果：63/63 通过**，由 `npm run e2e` 产出（脚本 `tests/e2e-verify.cjs`）。

覆盖范围与结果：

| 分组 | 断言数 | 覆盖 |
|---|---|---|
| 求职者端 | 11 | 登录页、演示账号、错误密码、**身份不匹配被拦截**、职位列表(38 条)/详情/投递入口、消息、我的（含我的投递入口）、退出登录 |
| HR 端 | 15 | 四 Tab、职位列表(7 条)、状态筛选、发布（**空表单校验**）/编辑/关闭、沟通板块(6 会话)、会话、聊天页、发消息、自动回复 |
| 候选人管理 | 7 | 列表（6 位候选人）、详情、约面试、**面试邀请发送**、状态更新为面试中 |
| HR 人才筛选 | 6 | 页面打开、关键词命中（**含证据片段**）、未命中候选人不出现、无匹配提示、标签筛选 |
| 跨端投递闭环 | 14 | 求职者投递云杉职位 → HR 候选人可见 → 约面 → 求职者接受 → HR 回看「候选人已接受」；已关闭职位对求职者不可见 |
| 验证码登录 | 1 | `123456` 验证码登录全流程 |
| 注册与企业认证 | 7 | HR 注册（自动建「新注册企业-XXXX」未认证公司）、企业认证（**Mock 自动通过**）、公司资料改名 |
| 质量 | 2 | 零控制台错误、零 hydration 报错 |

脚本结束会自动打印「进程清理检查」，对比运行前后用户 Chrome 进程数（本轮 26 → 26，未受影响）。

复现：`npm run dev`（另一终端）→ `npm run e2e`。

### 自动化验证方法（无需人工点击）

**项目已内置验收脚本 —— 优先直接用它：**

```bash
npm run dev                # 另开一个终端保持运行
npm run e2e                # 跑完整验收，输出逐项 PASS/FAIL
```

- 脚本位置：`tests/e2e-verify.cjs`（纯 Node，无第三方依赖，用 CDP 驱动无头 Chrome 真实点击）
- 可用环境变量覆盖：`BASE_URL`、`CHROME_PATH`、`CDP_PORT`
- 结束时自动打印「进程清理检查」，证明未影响你正在使用的 Chrome
- 改完代码跑一遍，即可确认没有回归

**自己写测试脚本时**，清理无头实例必须使用
`node .zscripts/kill-chrome-safe.cjs` 的 `killChromeTree(pid)`，它只终止你 spawn 的进程树。

> ⚠️ **严禁使用 `Get-Process chrome | Stop-Process -Force`**。
> 这会把用户正在使用的 Chrome **全部标签页一起杀掉**，导致状态丢失与报错。
> （历史上犯过这个错，见 §6.3。）

---

## 6. 坑与教训（最重要的一节，务必读）

这些都是**实际踩过并已修复**的。重犯会浪费大量时间。

### 6.1 循环依赖：`jobs.ts` ←→ `hrJobs.ts`

**现象**：页面 HTTP 500，报 `Cannot access 'jobs' before initialization`
或 `baseJobs is not defined`。

**原因**：`jobs.ts` 需要 `hrJobRecords` 来合成「求职者可见职位」，
而 `hrJobs.ts` 又需要 `jobs.ts` 的基础职位做初始数据 —— 直接互相 import 成环，
模块初始化期取到 `undefined`。

**已采用的解法**（唯一同时满足「无环 + 无 require + 初始化顺序确定」）：
1. `hrJobs.ts` **不 import** `jobs.ts`，导出 `initHrJobs(baseJobs)` 惰性填充；
2. 新增 `src/api/mock/bootstrap.ts`，由它按序调用 `initHrJobs(jobs)`；
3. `api/jobs.ts` 顶部 `import './mock/bootstrap'` 触发装配（此处 `jobs.ts` 已求值完成，
   不会形成再入）。

**已否决的两种写法**（不要再试）：
- 在 `jobs.ts` 里 `require('./seekerJobs')` 延迟加载 → 被 ESLint
  `@typescript-eslint/no-require-imports` 拦下（本项目设为 error）。
- 从 `jobs.ts` 顶部 import bootstrap → 造成 bootstrap 再入 jobs，`baseJobs` 仍是 `undefined`。

### 6.2 Zustand 选择器返回新对象 → 无限重渲染

**现象**：页面崩成 500；控制台报
`The result of getSnapshot should be cached to avoid an infinite loop`
与 `Maximum update depth exceeded`。

**原因**：把返回**新建对象**的函数直接当选择器：
```ts
const current = useHrStore(s => s.pageStack.at(-1) ?? { type: 'job-list' })  // ❌
```
zustand 用 `Object.is` 比较快照，每次都是新对象 → 判定每次都变 → 无限重渲染。

**解法**：
```ts
// store 里导出「栈为空时返回同一模块级常量引用」的 hook
export function useHrCurrentPage(): HrPage {
  const top = useHrStore(s => s.pageStack[s.pageStack.length - 1])
  const tab = useHrStore(s => s.tab)
  if (top) return top
  return tab === 'messages' ? MESSAGES_ROOT : JOB_LIST_ROOT  // 常量
}
```
**规则**：选择器只允许返回**稳定引用或原始值**；需要派生对象时用 `useMemo` 或在组件里组装。

### 6.3 误杀用户浏览器（严重，已根治）

**曾经的做法**（❌ 严禁再用）：
```powershell
Get-Process chrome -ErrorAction SilentlyContinue | Stop-Process -Force
```
这是**按进程名全杀**，会把用户正在使用的 Chrome 全部标签页杀掉，
用户会看到标签页崩溃报错 —— 而且很容易被误判成「代码坏了」。

**正确做法**：只终止自己 spawn 的进程树。
```js
const { killChromeTree } = require('./.zscripts/kill-chrome-safe.cjs')
const chrome = spawn(CHROME, [...], { stdio: 'ignore' })
// ...
killChromeTree(chrome.pid)   // taskkill /T /F，仅该 PID 树
```

### 6.4 身份校验必须在写入登录态之前

**现象**：用户以「公司代表」身份登录求职者账号，结果**直接进了求职者端**，
预期的「身份不匹配」提示永远看不到。

**原因**：先 `login()` 写入 auth store，再由页面判断角色不符并 `setMismatch` ——
但 store 一变，`RootGate` 立刻卸载登录页去渲染工作台，提示来不及渲染。

**解法**：把期望身份下沉到 service 层，**不匹配就抛错、不写任何登录态**：
```ts
await login(account, password, expectRole)   // 不符时抛 RoleMismatchError
```
`RoleMismatchError` 定义在 `src/api/auth.ts`。**规则**：任何「登录后校验」都要前置到写入状态之前。

### 6.5 手写 `useEffect + setState` 取数会触发 React Compiler 告警

本项目开启 React Compiler，`react-hooks/set-state-in-effect` 是 **error** 级。
用 effect 手写取数（`useEffect(() => { reload() }, [...])`）会报错。

**解法**：用 **TanStack Query**（Provider 已就位）：
```ts
const { data, isPending } = useQuery({
  queryKey: ['hr', 'jobs', companyId, filters.status, dataVersion],
  queryFn: () => listMyJobs({ companyId, status: filters.status }),
  enabled: Boolean(companyId),
})
// 变更后：queryClient.invalidateQueries({ queryKey: ['hr', 'jobs'] })
```
需要「取数 + loading」时不要手工维护 `loading` 状态，交给 `isPending`。
`dataVersion`（store 里的自增计数）用于让 Mock 数据变更后触发重取。

### 6.6 同一组件既取数又 setState 会被告警 → 拆组件

编辑页需要「先取原职位、回填表单」。若在同一个组件里 `useEffect` 取数并
`setState` 回填，仍会触发 §6.5 的告警。
**解法**：拆成 `HrJobEditorPage`（只负责取数）+ `EditorForm`（只负责渲染表单）。

### 6.7 其他小坑

- **Mock 数据在内存**，页面刷新即重置。跨端联动测试**必须用应用内退出登录切换账号**，
  不能 `Page.navigate` 刷新，否则新建的职位会丢。
- **新建职位 id 用递增计数**，不要用 `Date.now()` / `Math.random()` 生成 ——
  会造成 SSR/CSR 渲染不一致。
- **Mock 职位状态用稳定哈希**（`stableHash(id) % 4`）而非随机，同理。
- **HR 关闭某职位后，该职位对求职者端应隐藏**（`listSeekerJobs` 已实现）；
  修改职位内容也要覆盖到求职者端（即使状态未变），否则改标题/薪资后求职者端仍是旧内容。
- **重新登录会落回上次停留的页签**：`pinliao` / `hr` 两个 store 不随登出重置。
  登录成功处必须 `setTab('jobs')`（`LoginPage` 的 `afterAuthSuccess` 已统一处理）；
  新增登录入口（如新登录方式）时不要漏掉。
- **登录页有三个模式页签**（密码 / 验证码 / 注册），按文本找「登录」按钮会命中页签。
  自动化脚本用 `form button[type=submit]` 定位提交按钮（`tests/e2e-verify.cjs` 的
  `clickSubmit`）；覆盖层页面先点 `aria-label="返回"` 退出再操作底部 Tab
  （`clickAria`），否则点击会静默落空（历史上就是这么丢过断言覆盖的）。
- **HR 新建职位的公司归属**：`mock/hrJobs.ts` 已从硬编码云杉改为按登录上下文注入
  （注册新公司的 HR 发布职位会挂到自己公司名下）；改动相关逻辑时保持这一点。
- **Mock 验证码是内存态**：固定 `123456`、按手机号 60s 限流；刷新页面即重置。
- **覆盖层内做「不出现」断言要限定取数范围**：`document.body.innerText` 会包含被覆盖层
  遮挡的**根视图**文本（如候选人列表仍在 DOM 中），「某某不出现」类断言会被背景文本
  误判。人才筛选段落改用顶层 `.pl-page-enter` 的 `innerText` 取数
  （`tests/e2e-verify.cjs` 的 `matchOverlayText`），写新覆盖层断言时沿用此方式。
- **人才筛选（候选人匹配）依赖简历正文数据**：数据源为 `src/api/mock/resumes.ts`
  （按 userId 关联，简历 id 约定 `resume-{userId}`）。新增候选人 / 求职者投递若没有
  对应简历正文，不会出现在匹配结果中——这是预期行为（先有简历数据才有可筛内容）。

### 6.8 后端本地环境（Step 3 T0 起）

- **JDK 用 IDEA 自带 JBR 21**：本机系统仅 JDK 1.8，构建 / 运行 `mvnw` 前必须先设
  `$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr'`，
  否则报 JAVA_HOME 不合法。不装 Temurin（原计划作废）。
- **MySQL 专用实例 3307**：复用本机 MySQL 8.0.29 二进制 + 独立数据目录 `.local/mysql/data`
  （root/123456，库 `pinliao`）；**3306 是本机原服务（自动启动），不要连**。首次初始化
  `backend/scripts/db-init.ps1`（可重入），日常 `dev-up.ps1` / `dev-down.ps1` 一键起停。
- **Redis 6379**：tporadowski Windows 版（5.0.14.1）解压于 `.local/redis`，`redis-start/stop` 脚本管理。
- **`.ps1` 含中文必须 UTF-8 BOM**：Windows PowerShell 5.1 按 ANSI 读脚本，无 BOM 会乱码 / 报错。
- **Docker 结论（勿重复评估）**：`registry-1.docker.io` 本机不可达；Docker Desktop 需 WSL2 +
  重启 1~2 次，用户已选免 Docker 直装；运行数据统一 `.local/`（已 gitignore）。
- **后端 WARN 现状（T2a 起）**：原两条（Flyway「No migrations found」/ MyBatis-Plus「No MyBatis mapper」）
  已随 V1/V2 与 mapper 落地消失；仅剩 springdoc 的两条 INFO 级 WARN（无害）。
- **种子密码 hash 双处同步**：`V2__seed_auth_demo.sql` 里的 BCrypt hash 与
  `backend/src/test/java/.../PasswordHashTest.SEED_HASH` 必须一致（改演示密码时两处一起改；
  重新生成：临时加 `System.out.println(new BCryptPasswordEncoder().encode("123456"))` 跑一次测试）。
- **建表用 `BOOLEAN` 不要 `TINYINT(1)`**：MySQL 8 对整型显示宽度（如 `TINYINT(1)`）报弃用告警，
  `BOOLEAN` 落库等价 `tinyint(1)` 且无告警（T2a 已踩）。
- 后端进程结束按**端口 8080 定位 PID** 精确处理；勿按名杀 `java`（同机 IDEA 常驻）。

### 6.9 多租户注入与种子生成（Step 3 T1 起）

- **租户注入不是「按表一刀切」**：`PinliaoTenantLineHandler` 仅在「当前登录者带 companyId（HR / COMPANY_ADMIN）」
  且「表属租户私有集合（jobs / conversations / messages / applications / interviews / resume_view_logs /
  tenant_configs / usage_logs）」时注入 `company_id`；求职者是**跨租户**视角（浏览所有公司职位、查自己的
  投递 / 会话），一律不注入，service 层必须显式按 `user_id` / 可见职位过滤。改这段逻辑前先读
  `docs/step3-plan.md` §3（求职者跨租户是产品语义）。
- **`mvnw test` 的 WebMvcTest 切片**不加载 `MybatisPlusConfig`（`EnvelopeSmokeTest` 已排除鉴权拦零）；
  为业务写切片测试时注意：无登录上下文时租户表查询不会被注入条件（handler `ignoreTable` 直接放行），
  断言别依赖「自动注入」；`PinliaoTenantLineHandlerTest` 单测覆盖策略本身。
- **种子迁移不要手改**：`V4~V8` 由 `backend/scripts/seed-gen/generate.mjs` 从 `src/api/mock/*` 生成
  （文件头有「勿手改」警示）；改演示数据 → 改 mock 源文件 → 重跑生成脚本（Node ≥ 22.7；
  `--import` 参数必须传 `file:///` 绝对 URL；脚本用 Node 原生 `--experimental-transform-types`，零额外依赖）。
- **行数抽查**：`backend/scripts/verify-seed.ps1`（16 项断言，期望值写死在脚本）；改动种子数字时同步改脚本。
- **jsqlparser 版本差异**：本仓（MyBatis-Plus 3.5.17）`StringValue` 取值方法是 `getValue()`，
  不是 `getStringValue()`（写租户单测时已踩）。
- **求职者可见职位**：`status='OPEN' AND deleted_at IS NULL`（+ 公司存在），与 mock `listSeekerJobs()`
  语义一致；T3 起所有求职者侧职位查询都走这条定义（对应 AGENTS §2.10）。

### 6.10 注册落库与演示库重置（Step 3 T2b 起）

- **注册会真实落库且手机号唯一**：`u-reg-*` / `up-reg-*` / `co-reg-*` / `hr-reg-*` / `cm-reg-*`
  记录持久保留（同手机号重复注册 = 40910）；`verify-seed.ps1` 的 users / companies 断言已按前缀排除。
- **注册 id 派生自手机号**（`u-reg-{phone}` 等）：mock 用进程内递增计数（`u-reg-1`，刷新即复位），
  落库不能照抄（重启即冲突）；前端只把 id 当不透明字符串，无需对齐 mock 的 id 形态。
- **e2e 注册段用固定手机号 `13900004321`**：Mock 阶段刷新即可重跑；**T9 对真实后端重跑前必须定重置策略**
  （候选：跑 e2e 前重建演示库 / e2e 改用时间派生手机号并同步期望公司名）——**待用户拍板，勿擅自改 e2e**。
- **纯 Mockito 单测碰 MyBatis-Plus 的 LambdaWrapper 需先初始化 TableInfo**（无 Spring 上下文时报
  `can not find lambda cache for this entity`）：见 `AuthServiceRegisterTest` 静态块的
  `TableInfoHelper.initTableInfo`（对 UserEntity / UserProfileEntity / HrProfileEntity / CompanyMemberEntity）。

### 6.11 职位公开读与 T9 代理坑（Step 3 T3a 起）

- **`@Select` 字符串不解析 XML 实体（没包 `<script>` 时）**：`j.id &lt;&gt; #{jobId}` 会把 `&lt;&gt;` 原样
  发进 MySQL（报 `SQLSyntaxErrorException near '&gt;'`）；要么包 `<script>`（则 `<` 需转义），要么直接写
  `!=`（T3a 已踩，`JobMapper.selectSimilar`）。
- **统一包络的 traceId 逐请求变化 → 不能按响应体算 ETag**：职位详情 ETag 用「`jobId` + `updatedAt` 秒级
  版本号」手动比对 `If-None-Match` 返回 304（`JobQueryService.detail` + `JobController.detail`）；若日后想用
  `ShallowEtagHeaderFilter`，需先把 traceId 排除出响应体。
- **`@JsonInclude(NON_NULL)` 会把 `nextCursor: null` 吞掉**：游标分页响应（`JobViews.Pagination`）不能加
  NON_NULL——前端类型是 `nextCursor: string | null`，缺字段与显式 null 不等价。
- **公开接口的「可选鉴权」在 `AuthInterceptor` 内判定**（方法 + Ant 路径，包级可见便于单测），不要加进
  `WebAuthConfig` 的 exclude（exclude 后带 token 也不解析，且会把 `POST /api/v1/jobs` 一并放过）。
- **公开浏览不受租户注入**：`JobMapper` 的公开查询显式 `@InterceptorIgnore(tenantLine="true")`——HR 账号
  打开求职者端职位页也是全平台可见职位（契约「公开浏览」）；HR 管理端口径（§4.3 / §6.5~§6.8）才用租户注入。
- **游标格式**：`base64(JSON {"s":得分,"k":发布时间毫秒,"id":"job-1"})`——排序键三元组（得分 DESC / 时间
  DESC / id ASC），匿名与登录同格式；非法游标 40001；演示规模全量取回后内存切片，上量后把条件推 SQL
  （游标格式不变）。
- **T9 联调代理已落地（2026-10-19）**：`src/api/client.ts` 的 `buildUrl` **只返回相对路径**（避免跨域），
  故在 `next.config.ts` 加了 rewrites：`/api/v1/:path*` → `${API_PROXY_TARGET ?? 'http://localhost:8080'}/api/v1/:path*`，
  以及 `/uploads/:path*` → 后端（简历附件 / 上传文件回显靠它，**不需要**前端拼绝对地址）。
  `.env.local` 设 `NEXT_PUBLIC_USE_MOCK=false`、`NEXT_PUBLIC_API_BASE_URL=/api/v1`（`.env*` 已 gitignore，不提交）。
  改 `.env.local` 后必须重启 dev server（`NEXT_PUBLIC_*` 构建期内联）。

### 6.12 职位写与配额（Step 3 T3b 起）

- **租户插件对 INSERT 不会重复列**：MyBatis-Plus `TenantLineInnerInterceptor` 在 INSERT 前会检查租户列是否
  已在列清单里（`ignoreInsert`），因此 `BaseMapper.insert` + 实体显式 `companyId` 是安全的；UPDATE / DELETE /
  SELECT 会自动追加 `company_id` 条件。写路径仍在 service 里**显式比对 companyId**（纯 Mockito 单测没有插件，
  也能覆盖 40310）。
- **新职位 id = `job-new-{库内最大序号 + 1}`**：查询必须 `@InterceptorIgnore(tenantLine="true")`——id 是全局主键，
  要跨公司取最大值；软删保留行，序号不复用；**勿改成时间戳 / 随机**（T1 起既定约束）。
- **`publishedAt` 落库前截到毫秒**：Java 纳秒 vs MySQL `DATETIME(3)`，不截会导致「创建响应」与「更新 / 详情再读」
  的 ISO 字符串不相等（e2e 比对会假失败）。
- **配额缺省 50**：种子没有 `tenant_configs` 行，`max_jobs` 读为 null 时按 50；42211 在真实超限时才触发
  （JUnit 用 mock 覆盖）。**发布限流 10 次/分钟/公司**：Redis 自然分钟键
  `pinliao:job:create:{companyId}:{minute}`，超限 42901。
- **40310 语义**：不存在 / 非本公司 / 已软删统一「无权操作该职位」，不泄露职位是否存在（对齐 mock `hrJobs.ts`）；
  字段校验 42201（合并后校验：标题 2~30、薪资正数且下限 < 上限、标签 ≤ 8）。
- **冒烟会留下 `job-new-*` 软删行**：`verify-seed.ps1` 的 `jobs_total` 已排除该前缀；不要改成硬删（破坏
  「软删保留行」语义），也不要手动清库。

### 6.13 投递 / 面试与会话占位（Step 3 T4 起）

- **投递即建会话**：§9.1 成功（含撤回后重新投递）后按 `(jobId, userId)` 幂等创建 `conv-new-{seq}`，
  会话 `lastMessageAt = now`、未读 0；§9.3 详情返回 `conversationId`（契约外增量）。这样 HR 端
  「沟通 / 约面试」不再依赖 mock 的 `ensureConversationForApplication`（真实分支目前没有独立建会话接口）。
  T6 做会话列表 / 消息时**复用该行**，不要再按 job 建第二份。
- **状态机顺序要对齐 mock**：约面试先判 40914（REJECTED / WITHDRAWN）→ 40915（已有 PENDING）→
  42201（形式 / ONSITE 缺地点）→ 42210（时间早于当前）。顺序不同会让前端提示文案错位。
- **接受 / 拒绝是幂等终态**：已 ACCEPTED 再 accept 返回原记录；已 DECLINED 再 decline 同理；
  其余状态（如 CANCELLED / COMPLETED）→ 40913。不要改成 404（e2e 会重试）。
- **归属隔离统一 40410**：投递不存在 / 非本人 / 非本公司、面试不存在 / 跨公司统一 `40410`（不泄露存在性）；
  角色不符才是 40301。HR 侧租户注入 + service 显式 `companyId` 双保险。
- **新 id 前缀**：`app-new-` / `int-new-` / `conv-new-` / `msg-new-{seq}`，序号查询都要
  `@InterceptorIgnore(tenantLine="true")`（全局主键）；时间字段落库前截毫秒（同 T3b 的 DATETIME(3) 坑）。
- **冒烟可重复**：`smoke-applications.mjs` 每次选「云杉下未投递或已撤回」的职位，结尾撤回为 WITHDRAWN；
  中途失败可能留下非终态记录，重跑会换下一个职位，全部用完时需人工处理 `app-new-%`。
- **T9 已知契约差异（2026-10-19 已全部处理，实现细节见 §6.18）**：
  1. 列表分页对象 `{list,pagination}` → 前端真实分支统一解包 `.list`（§9.2 / §10.1 / §8.1 / §8.2）；
  2. `GET /hr/applications`（§4.4）T8 已实现，出参直接是 `HrApplicationView` 超集；
  3. §9.3 返回 `ApplicationDetail` 与 HR 详情页所需形状不同 → 详情页改走 §4.4 列表按 id 定位；
  4. §8.7 真实返回 `Message` → `inviteInterview` 返回类型改为 `Interview | Message`；
  5. `listMyApplications` 真实分支已按 `applicationId` 合并 §10.1 面试列表。

### 6.14 公司模块与企业认证（Step 3 T5 起）

- **管理员口径不要看 user.role**：§5.4~§5.6 的 COMPANY_ADMIN 以 `company_members.role=ADMIN` 为准。
  演示 HR `u-hr-1` 的 `users.role` 是 `HR`、成员角色是 `ADMIN`；若只判 token 里的 role，演示账号会 403。
  §5.3 仍然只要求 HR / COMPANY_ADMIN，不要求 ADMIN 成员。
- **改名无需刷职位行**：职位视图的公司信息从 `companies` 实时装配，`jobs` 不存公司名快照；
  §5.4 更新后 §5.1/§5.2/§6.x 自动同步。不要再写「UPDATE jobs SET company_name=...」。
- **认证没有独立记录表**：契约只暴露 `companies.verified/status`，数据模型也没有
  `company_verifications`；提交的四项资料只做校验，不落库。状态流转 PENDING → 约 1.2s
  后台调度 ACTIVE，延迟可用 `pinliao.company.verify-delay-ms` 覆盖（单测用 30ms）。
- **异步线程没有 UserContext**：自动通过只更新全局表 `companies`，不能用依赖租户注入的 Mapper；
  后台任务里 `selectById` 后必须再次确认 `status=PENDING`，避免覆盖人工禁用 / 删除。
- **公开路径**：只放行 `GET /api/v1/companies/*` 与 `GET /api/v1/companies/*/jobs`（匿名可读）；
  `/company/**`（单数）仍需登录。Ant 的 `*` 只匹配一段，新增子路径时要同步白名单与单测。
- **公司职位只看可见**：§5.2 仅 `OPEN` 且 `deleted_at IS NULL`，公司不存在 → 40410；
  游标与 §6.1 同格式（score 固定 0），不要复用带推荐加权的 cursor。
- **冒烟保持基线**：`smoke-company.mjs` 用临时 `co-reg-*` 公司验证写链路，发布的 `job-new-*`
  在 finally 软删；`verify-seed.ps1` 已排除这两类前缀，跑完仍应为可见 38 条。

### 6.15 消息与 WebSocket（Step 3 T6 起）

- **未读事实源在数据库**：`conversations.user_unread_count` / `hr_unread_count` 才是持久化口径；
  Redis 只做发送限流（30 次/分钟/用户）与 `idempotencyKey` 24h 去重。不要把未读搬回 Redis，
  否则重启和 T9 的种子断言都会漂。
- **列表是双端超集**：`GET /conversations` 每项同时返回 `conversation / job / hr / company /
  candidate / lastMessage`。T9 接入时求职者取 job/hr/company，HR 取 candidate，不要另造接口。
- **历史顺序**：§8.2 明确 `createdAt DESC, id ASC`，出参是 `{ list, pagination }`；前端 mock
  的真实分支若按数组 / 升序处理，T9 必须适配（见下方差异清单）。
- **扩展读取**：`GET /conversations/{id}` 是 T6 为 HR 聊天顶栏加的契约外读取，与列表项同形；
  不计入 §8 的 8 条 HTTP 账目，`verify-seed` 也不受影响。
- **WebSocket 鉴权**：`/ws` 用 STOMP；浏览器 query `?token=<accessToken>`（也接受
  `access_token` / `Authorization` header）。握手无 token 会先升级、CONNECT 阶段拒绝；非法 /
  已吊销 token 握手即拒绝。订阅 `/user/queue/messages`、`/user/queue/typing`、
  `/topic/conversation/{id}/typing`，错误走 `/user/queue/errors`；发送用
  `/app/conversation/{id}/send|typing|read`。
- **发送不回显**：REST / STOMP 的 `send` 只向对方推送，避免发送方收两条；前端本地乐观消息
  需自行去重（T9 注意）。
- **自动回复线程没有 UserContext**：`ConversationAutoReplyService` 用显式 conversationId /
  companyId 查询，不能依赖租户注入；延迟默认 1200ms，可配 `pinliao.message.auto-reply-delay-ms`，
  单测直接调包级 `deliver` 避免异步等待。
- **幂等只在写成功后缓存**：同一 `idempotencyKey` 串行重试返回同一条；并发同时到达仍可能各写一条
  （演示可接受，真实支付/消息接入需加分布式锁或唯一索引）。
- **冒烟不能碰种子会话**：`smoke-messages.mjs` 只读写 `conv-new-*` / `msg-new-*`，绝不标记
  `conv-hr-*` / `conv-seed-*` 已读；否则 `verify-seed.ps1` 的 HR 未读 4 / 求职者未读 2 会失败。
- **T9 需适配的前端差异**：真实分支的会话/消息列表形状（`{list,pagination}` vs 数组）、时间字符串
  （ISO UTC 带 Z）、消息 `content` 结构化类型（后端返回 JSON 字符串，mock 可能是对象）、
  §8.7 邀请接口返回 `Message` 而前端类型声明为 `Interview`、`GET /hr/applications` 属 T8。

### 6.16 用户 / 简历 / 收藏 / 上传（Step 3 T7 起）

- **鉴权白名单只多两条**：`POST /api/v1/user/password/reset`（忘记密码）与
  `PUT /api/v1/uploads/local/**`（预签名直传）；其余 §2/§3/§11/§12 均需登录。
  `/uploads/**` 是静态资源，不在 `/api/v1/**` 下，不走 `AuthInterceptor`。
- **验证码场景**：重置密码用 `RESET`；改手机号用 `LOGIN`（新号可能尚未注册）。两者都先做业务冲突 /
  原密码校验，再消耗验证码；`smoke-user.mjs` 通过 RESP 直连 Redis 删除 60s 冷却键，保证可连续跑。
- **外观偏好不落库**：Redis key `pinliao:user:prefs:{userId}`，默认 `auto`；仅接受
  `auto|light|dark`。T9 前端仍可用 localStorage，接口是多端同步预留。
- **注销是软删**：`users.status=DELETED` + `deleted_at`，同时吊销当前 accessToken（再用返回 40102）；
  当前没有恢复接口，30 天恢复需运维 / 后续增量补。注销账号仍以 `u-reg-*` 留在库中，`verify-seed`
  的 users 计数已按前缀排除。
- **简历权限**：求职者只读写本人；HR 必须存在 `applications.resume_id` 指向该简历且
  `company_id` 为当前公司，否则统一 40410。列表按「默认优先、更新时间倒序」。
  首份自动默认；删除默认简历时自动把最近更新的剩余简历设为默认；全部删完清空
  `user_profiles.default_resume_id`。简历软删行保留，`verify-seed` 的 resumes 已排除
  `u-reg-*` 与 `deleted_at`，不要按全表行数断言。
- **收藏**：id 为 `fav-{userId}-{jobId}`，`(user_id, job_id)` 唯一，重复收藏 40910；
  取消幂等。列表返回 `{ list, pagination }`，`pagination.total` 是该用户全部收藏数，
  游标按 `createdAt DESC, id ASC`；职位摘要可能为 null（职位后续下线）。
- **上传是本地替代实现**：`POST /uploads/sign` 由服务端生成 objectKey 与 HMAC 签名，返回相对
  `uploadUrl`；客户端直接 `PUT` 到该 URL（无登录态），完成后 `POST /uploads/confirm` 返回
  `/uploads/{objectKey}`。确认**不会**写业务表（契约没有目标实体 id）；头像 / 附件落库由前端随后
  PATCH profile / resume 完成，T9 接入时注意。目录默认 `target/uploads`，URL 10 分钟过期；
  对象 key 含 userId，只能确认本人对象。
- **T9 前端差异**：真实 `UserProfile` 不含手机号（手机号在 `auth/me.user.phone`）；简历 / 收藏返回
  相对 URL 与 `{list,pagination}`，前端 `request` 需拼 `API_BASE_URL` 并归一出参；
  `GET /resumes/{id}` 的 HR 分支只有已投递关系才返回，mock 里可能更宽松。
- **T8/T9 顺序与边界**：§4.1~§4.6 已于 **T8（2026-10-19）全部落地**，`USE_MOCK=false` 尚未切换；
  T9 验收范围含 §4 全部页面（人才筛选不再暂留 Mock），不引入模块级 Mock 开关。详见 §6.17。

### 6.17 招聘者 / 候选人 / 人才筛选（Step 3 T8 起）

- **`hr_profiles` 不在租户注入表集合内**：`PinliaoTenantLineHandler.TENANT_TABLES` 不含该表
  （它按 `user_id` 唯一，见 V1 的 `uk_hr_profiles_user`）。§4.1/§4.2 必须显式按 `user_id` 归属查询；
  误用 `company_id` 会读到同公司其他 HR 或读不到。新增 HR 侧读取前，先确认目标表在不在注入集合里。
- **出参口径是「前端超集」**（2026-10-18 用户拍板）：§4.3 返回 `HrJobRecord[]`（`JobSummary` + `status`
  + `responsibilities` / `requirements` / `welfare` + `deleted` + `companyId`），§4.4 返回
  `HrApplicationView[]`（application + candidate 摘要 + 职位摘要 + `conversationId` + 最近面试）。
  契约文字写的是 `Job[]` / `ApplicationWithJob[]`，差异已在 `docs/api-endpoints.md` §4 标注，
  目的是让 T9 前端真实分支零适配。
- **契约外扩展**：`GET /api/v1/hr/jobs/{jobId}` 供 HR 编辑页打开已关闭职位（公开详情 §6.3 只认
  `OPEN`）；§4.3 额外接受 `q`（标题模糊）/ `hrId`（只看某招聘官），§4.4 额外接受 `q`
  （候选人姓名 + 职位名称，大小写不敏感）；`cursor` 为契约保留参数但未启用（演示规模全量返回，
  `limit` 作 1~100 上限保护），T9 不要依赖游标。
- **「HR 可见已关闭职位」是 §4.3 与 §6.3 的关键差异**，冒烟用「创建 `job-new-*` → close →
  HR 列表 / 详情可见且公开详情 40410 → 软删后 HR 详情 40410」验证；临时职位必须在 `finally`
  软删，否则「可见 38」基线与 `verify-seed`（`jobs_total` 排除 `job-new-%`）会漂。
- **人才筛选是关键词匹配（演示版）**：`backend/.../recruiter/service/TalentMatchService.java` 与
  `src/api/mock/talentMatch.ts` 逐条对齐——词典长词优先且命中后用空格占位（防「自动化测试」再命中
  「测试」）、英文技术词 `[A-Za-z][A-Za-z0-9+#.-]*` 并去掉结尾 `[.-]+`、中文先去停用词再取
  `[\u4e00-\u9fa5]{2,}`、大小写不敏感去重保留首次出现的大小写、技能 3 / 文本 2 取最大值、
  单关键词 ≤3 条证据 / 单候选人 ≤6 条、`score` 倒序后按简历完善度倒序。**改 mock 引擎必须同步
  Java 并跑 `TalentMatchServiceTest`**（该测试就是移植保真度护栏）。
- **e2e 依赖的证据文案与片段规则**：`技能 / 求职意向 / 个人简介 / 工作经历 · {公司} /
  项目经历 · {项目名}`；片段取命中处「前 10 / 后 46」字符并按需补 `…`。`技术栈` 必须落在个人简介
  片段里、`项目经历 · 招聘管理系统前端重构` 必须出现在证据里（两者都是 e2e 断言点）。
- **候选人口径复用 T6**：`CandidateSummaryService` 与 `ConversationQueryService.candidateOf` 同口径
  （缺画像给占位、缺字段给 `—` / `0`、存在可用简历 = 完善度 85）；单测按 85 断言，不要改成精确分。
- **本机演示库有历史冒烟残留**：早期 T4 冒烟留下一条可见的 `app-new-*` 投递
  （`app-new-1`：`u-seeker-1` 投云杉 `job-1`，状态 `WITHDRAWN`、未软删），会让 §4.4/§4.5 结果多一位
  候选人；冒烟断言按「种子齐备 + 容忍残留」编写，T9 e2e 前必须按 §6.10 策略清库重迁，
  否则候选人数与 mock 基线不一致。

---

### 6.18 T9 全量联调（`USE_MOCK=false`，Step 3 T9 起）

- **`USE_MOCK` 会隐藏「演示专用 UI」→ e2e 会成片失败**：`LoginPage` 的「演示账号（一键填入）」
  曾用 `USE_MOCK &&` 包住，切真实后端后整块消失，e2e 点不到账号 → 登录失败 → 61 项连锁 FAIL
  （试跑一度 6/61）。排查同类问题时**先看演示用的 UI 是否被开关隐藏**。结论：原型演示所需的
  一键填入 / 固定验证码提示在联调模式下**同样保留**（只改 `USE_MOCK` 判断，不删内容）。
- **真实分支必须自己补「身份校验先于写登录态」**：`loginWithPassword` / `loginWithSms` 的
  `expectRole` 校验原来只在 mock 分支里，真实分支直接 `persistTokens` → 用 HR 手机号选「求职者」
  能直接进 HR 工作台。现抽出 `assertRoleMatches(result, expectRole)`，在 `persistTokens` **之前**
  调用（角色取 `result.user.role === HR ? HR : JOB_SEEKER`，`COMPANY_ADMIN` 归 HR）。
- **分页包络统一解包 `.list`**：后端列表一律 `{list, pagination:{nextCursor,hasMore,total}}`
  （`PageViews.Page`）。T9 已解包：§9.2 我的投递、§10.1 面试、§8.1 会话、§8.2 消息
  （`listHrConversations` / `listHrMessages` / `listMyApplications`）。新增真实分支时**先确认
  返回是数组还是 Page**；`JobViews.Pagination` 未加 `NON_NULL`，`nextCursor: null` 会显式下发。
- **HR「候选人详情」不要用 §9.3**：§9.3 `GET /applications/{id}` 返回 `ApplicationDetail`
  （`job`/`resume`/`interviews[]`，**没有** `candidate`、没有单个 `interview`），HR 页需要的是
  §4.4 的 `HrApplicationView` 超集。现 `getHrApplication` 走 `GET /hr/applications` 后按
  `application.id` 定位（演示规模可接受；上量再补一条 §4.4 单条详情）。
- **§8.7 约面试必须带 `conversationId`**：真实端点是
  `POST /conversations/{conversationId}/actions/invite-interview`，缺会话直接 42210。
  `conversationId` 由 §4.4 投递卡片带出（`view.conversationId`），`HrInterviewInvitePage` 已透传；
  返回类型是 `Message`（不是 `Interview`），故 `inviteInterview` 标注 `Promise<Interview | Message>`。
- **真实模式不要调「Mock 专用」函数**：`replyAsCandidate` 在真实分支抛错，会让 HR 聊天页的
  `setCandidateTyping(false)` / `invalidateQueries` 被跳过（UI 卡在「正在输入」）。
  正确做法：真实模式不本地追加，改为定时重取消息（后端
  `ConversationAutoReplyService` 在 ~1.2s 后写入自动回复；前端**没有 WS 连接**，故
  1200ms + 2600ms 各 refetch 一次）。
- **e2e「零控制台错误」需要一条精确豁免**：真实后端下「错误密码被拒绝」用例必然触发 1 次
  HTTP 401（契约行为），浏览器必然记一条 `Failed to load resource: 401`。`tests/e2e-verify.cjs`
  的末段质量门禁**只豁免 1 条**该形态的 401（`exempt401`），其余 401 仍会失败；Mock 模式无此日志，
  行为不变（仍是 63 项）。
- **清库重迁脚本 `backend/scripts/db-reset.ps1`**（T9 落地，`npm run e2e` 前必跑）：
  按 **8080 端口**定位并结束后端 PID（勿按进程名杀 java）→ `DROP + CREATE pinliao` → Redis
  `FLUSHALL`（清验证码 60s 冷却 / refresh token）→ 起后端 → 等 `/actuator/health` UP → 回读
  `flyway=8 / companies=16 / jobs=51 / applications=9`。`-NoRestart` 只清库，`-KeepRedis` 保留缓存。
  **注册用的固定手机号 `13900004321` 只有清库后才能重复注册**，所以「每轮 e2e 前重迁」是硬前提。
- **脚本本身的三个坑**：① JDK 探测必须**校验 major ≥ 17**——本机 `JAVA_HOME=D:\JDK1.8` 存在但会让
  `maven-shade-plugin` 直接炸，故候选顺序为 `PINLIAO_JAVA_HOME` → IDEA `jbr` → `JAVA_HOME`，
  并用 `java -version` 解析版本；② PowerShell 7 的 `Invoke-WebRequest` 在响应无 charset 时
  `Content` 是 `byte[]`，`-match '"status":"UP"'` 会永远失败，需 `[Text.Encoding]::UTF8.GetString` 解码；
  ③ 脚本需 **UTF-8 with BOM**（仓库内 `.ps1` 均为 BOM，否则 PS 5.1 下中文输出乱码）。
- **求职者职位列表已切真实（T9 追加，2026-10-19）**：`JobsView` 原来用 `filterJobsSync`（`mock/filter.ts`，
  纯同步 mock 过滤）**绕过了 `listJobs` 的真实分支**，导致真实模式下 HR 发布 / 关闭职位对求职者无效。
  现改为 `useQuery(['jobs','list',filters,expectCat,expectSub], () => listJobs({..., limit:100}))`，\n  `mock/filter.ts` 已删除（`expectCat/expectSub` 入 key 是为了改动求职期望后自动重排：\n  Mock 分支读 localStorage 的 profile，真实分支由后端读库）。
  两个口径细节：① 演示规模要一次取全，必须显式 `limit=100`（后端默认 20、上限 100）；
  ② 「共 N 个职位」用后端 `pagination.total`，不要用 `list.length`（否则只显示当页条数）。
  后端 `q` 已同时匹配 `title / 公司名 / cat / sub / tags / area`，与 mock 的 haystack 一致，故
  「搜索公司名」在真实模式下同样可用。
- **仍是 Mock 驱动的真实分支（T9 剩余遗留，e2e 通过但不等于全链路真实）**：
  ① 分类字典（组件直接用 store 的 `CATEGORY_LIST` / `SUB_LIST_OF`，`categories.ts` 真实分支无人调用；
  后端 `/dict` 种子与 mock 同源，切了无可观测收益）；
  ② 求职者消息 / 会话（`store/pinliao.ts` 读 mock 播种，`conversations.ts` 真实分支不回写 store →
  真实模式「立即沟通」后的会话可能显示不存在）；
  ③ HR 侧 `ensureConversationForApplication`（真实分支直接抛 42210，后端在投递时已自动建会话，
  HR「发消息」入口改为复用 §4.4 卡片的 `conversationId` 即可）。
  ②③ 需要把 store 的会话模型改成 TanStack Query（未读 / 乐观发送 / 自动回复），且 e2e 对求职者消息页
  **零覆盖**——建议独立一轮（T10）并先补断言再动代码。

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

---

## 7. 数据与契约

### 7.1 API 契约（框架无关，是最重要的资产）

| 文档 | 内容 |
|---|---|
| `docs/api-contract.md` | 统一规范：响应包络 `{ code, message, data, traceId }`、错误码（5 位，前 3 位对应 HTTP 状态）、**游标分页**（不用 offset）、限流、WebSocket、安全 |
| `docs/api-endpoints.md` | 全部业务接口清单（13 模块，正文 70 条；v0.3.7 起总览表合计 70：鉴权 7 / 职位 10 / 消息 9，并标注 T8 的 §4.3 / §4.4 超集出参、扩展参数与契约外 `GET /hr/jobs/{jobId}`） |
| `src/types/api.ts` | 与契约同步的 TypeScript 类型定义。**新增功能前先看这里，很可能已有现成类型** |

> 已实现功能**全部照契约实现**，未自造接口。新增功能请沿用同一做法。

### 7.2 关键实体

- `RoleEnum`：`JOB_SEEKER` / `HR` / `COMPANY_ADMIN` / `PLATFORM_ADMIN`
- `ApplicationStatusEnum`：`SUBMITTED` / `VIEWED` / `INTERVIEW` / `OFFER` / `REJECTED` / `WITHDRAWN`
  —— 这就是「候选人管理」的标记与筛选维度，**不需要新设计**。
- `JobStatusEnum`：`OPEN` / `CLOSED` / `DRAFT`
- 带 `companyId`（租户私有）：`Company` `CompanyMember` `HrProfile` `Job`
  `Conversation` `Message` `Application` `Interview`
- 不带 `companyId`（求职者跨租户共享）：`User` `UserProfile` `Resume`

### 7.3 求职者可见职位的合成逻辑（易被忽略）

`src/api/mock/seekerJobs.ts` 把「基础职位」与「HR 端可变记录」合成一份
**求职者可见职位**：软删除/已关闭 → 不出现；招聘中 → 用 HR 端最新内容覆盖；
HR 新建 → 追加。

**这条链路曾经缺失**，导致「HR 发布的职位求职者看不到」。
求职者端取数必须走 `listSeekerJobs()` / `jobById()`（均从 `src/api/mock/jobs.ts` 导出），
**不要直接读 `jobs` 静态数组**。

### 7.4 Prisma 的真实地位

`prisma/schema.prisma` 是**领域模型设计文档**（19 个模型 + 10 个枚举），
**不是运行时依赖**：
- 当前无人 `import src/lib/db.ts`，故 `prisma generate` 非启动前置；
- datasource 为 SQLite（`.env` 的 `DATABASE_URL`），仅供本地原型；
- 生产为 **MySQL 8**（2026-10-17 T0 定案，原 PostgreSQL 计划作废），由 Spring Boot + MyBatis-Plus
  另维护建表 SQL；T1 起由 Flyway 管理（`backend/src/main/resources/db/migration/`）。

### 7.5 视图组件的数据来源（易踩）

| 组件 | 取数方式 |
|---|---|
| `JobsView`（求职者职位列表） | **同步** `filterJobsSync()`（`src/api/mock/filter.ts`） |
| `JobDetailPage` | 异步 `getJobDetail()`（`src/api/jobs.ts`）；投递态走 `src/api/applications.ts` + TanStack Query |
| `MyApplicationsPage`（我的投递） | **TanStack Query**（`listMyApplications` / 撤回 / 面试处理） |
| `MessagesView` / `ChatPage` / `FavoritesPage` | `jobById()`（已含合成逻辑） |
| HR 端各页（含候选人 / 公司资料 / 企业认证） | **TanStack Query** |

---

## 8. 协作规范（请遵循，以保持项目可维护）

1. **改完代码更新 `worklog.md`**：按现有模板追加条目
   （`Task ID` / `Agent` / `Task` / `Work Log` / `Stage Summary`），Task ID 递增。
2. **改变项目状态后更新本文件**（§2 状态表、§5 手册、§6 坑清单）。
3. **新增接口照契约**，先查 `src/types/api.ts` 与 `docs/api-endpoints.md`，不要自造。
4. **新增取数用 TanStack Query**，不要手写 `useEffect + setState`（§6.5）。
5. **选择器只返回稳定引用**（§6.2）。
6. **不要提交** `.env` 之外的本机私有配置；两个历史压缩包
   （`files.zip`、`workspace-*.tar`）是沙箱搬家产物，未纳入版本控制。
7. **提交信息用中文**，说明「改了什么 + 为什么」，保持与既有提交风格一致。
8. **不要动 `src/components/ui/`**（shadcn 生成代码）。

---

## 9. 待用户确认 / 已定事项（不要替用户决定）

| 事项 | 说明 |
|---|---|
| 最终上线前端框架 | Next.js / Vue / uni-app，当前 Next.js 仅沙箱验收 |
| 招聘者端桌面 Web 后台布局 | 当前仅移动端布局 |
| T8 / T9 先后（已定） | **先 T8 后 T9**（2026-10-18 用户拍板）：先补齐 §4 再整体切 `USE_MOCK=false`；`USE_MOCK` 无模块级粒度，先 T8 可避免二次返工 |
| T9 演示库重置策略（已定，已落地） | 每轮 e2e 前清库重迁（重建 + Flyway V1~V8），固定手机号 `13900004321` 可反复使用；脚本 **`backend/scripts/db-reset.ps1` 已落地并实测**（2026-10-19，含停后端 / 清 Redis / 等 health） |
| T9 遗留的真实分支（**部分已定 / 部分待确认**） | **已切**：求职者职位列表 / 详情（`JobsView` → `listJobs`，2026-10-19 用户选 A）。**仍待定**：求职者「分类字典 / 消息会话」，HR「发消息」入口——e2e 63/63 不依赖它们；建议独立一轮（T10），先补求职者消息页断言再切（详见 §6.18 末尾） |
| §4.3 / §4.4 出参口径（已定） | **给前端超集**（2026-10-18 用户拍板）：`HrJobRecord[]` / `HrApplicationView[]`，而非契约文字的 `Job[]` / `ApplicationWithJob[]`，换取 T9 前端零适配 |
| §4.3 / §4.4 契约外参数（已定） | **接受为可选扩展参数**（2026-10-18 用户拍板）：§4.3 的 `q` / `hrId`、§4.4 的 `q`；`cursor` 保留但未启用 |
| 是否自托管字体摆脱 Google Fonts 构建依赖 | `next/font/local` |
| 是否清除 `next-themes` 的 script 告警 | 无害，但刷屏 |
| 两个历史压缩包是否清理 | `files.zip`、`workspace-*.tar` |
| Step 4 AI / RAG 方案（**已定 / 待确认子项**） | **已定**（2026-10-19 用户拍板）：通义千问一站式（对话 `qwen3.5-122b-a10b` + `text-embedding-v4` 1024 维）、向量库先 `SimpleVectorStore` 后 MySQL 自建表、Spring AI 1.1.x 主线 + 事后补 LangChain4j、**单简历问答先行**；**T-AI0 已落地**（依赖 / 环境变量配置 / `/api/v1/ai/*` 自检）。**待确认**：见 [`step4-ai-plan.md`](./step4-ai-plan.md) §10 其余五项（向量维度 1024 vs 1536 / 是否接 Ollama / 配额策略 / 是否含求职者侧能力 / Boot 4 + Spring AI 2.0 迁移时机） |
| AI 密钥的存放位置（已定，已落地） | **用户级环境变量**（`环境变量` 对话框 → 用户变量），6 个 `PINLIAO_AI_*`；仓库只留 `${引用}`。理由：防泄露（用户明确要求「不要被上传 git 用光额度」）+ 一处修改全局生效；系统变量需管理员且扩大泄露面，不采用。已核查 `git grep` / 未跟踪文件 / 全部提交历史零命中 |

---

## 10. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-09-28 | 首版。取代已过期的 `HANDOFF.md` 作为唯一接续入口：修正端口/命令/状态/模型数等错误，补入 6 项已踩坑记录与协作规范 |
| v1.1 | 2026-09-28 | 补「自动化验收记录」：提交 `ca11abf` 上 24/24 断言通过，附覆盖范围表 |
| v1.2 | 2026-09-28 | 新增可复用验收脚本 `tests/e2e-verify.cjs` + `npm run e2e`；修复 `npm run build` / `npm start` 在 Windows 上不可用的问题（改用跨平台构建脚本）；补「构建与生产启动」说明 |
| v1.3 | 2026-10-16 | 开发交接：定位调整为「运维与深读手册」，状态指针指向根目录 `PROGRESS.md`；补工作区未提交在建改动（投递/面试数据层与 service，未接 UI）的快照说明 |
| v1.4 | 2026-10-16 | 五项验收功能全部完成并接入 UI（投递 / 候选人 / 面试闭环、验证码登录 / 注册、公司资料 / 企业认证）；e2e 扩展至 57/57；补登录页签残留、e2e 文本匹配 / 覆盖层、HR 职位归属等坑记录 |
| v1.5 | 2026-10-16 | 用户人工验收初步通过；补记远端仓库指向（`origin` → GitHub），状态与提交对齐 |
| v1.6 | 2026-10-16 | 人才筛选（演示版）增量：关键词匹配引擎 / 简历种子 / §4.5-§4.6 / 页面接入；e2e 扩展至 63/63；补覆盖层断言取数坑（§6.7） |
| v1.7 | 2026-10-17 | Step 3 T0：`backend/` Spring Boot 3.5.16 骨架（包络 / 错误码 / traceId）与本地环境定案（JBR 21 / MySQL 3307 / Redis 6379 / 免 Docker 直装）；补 §6.8 后端环境坑 |
| v1.8 | 2026-10-17 | Step 3 T2a：鉴权（验证码 / 登录 / 刷新 / 退出 / me）落地；§2 状态行更新；§6.8 补种子 hash 双处同步与 BOOLEAN 建表坑 |
| v1.9 | 2026-10-18 | Step 3 T1：全量表（V3）+ 演示种子（V4~V8）+ 种子生成器与抽查脚本落地；补 §6.9（租户注入策略 / 种子生成器用法 / jsqlparser 取值差异） |
| v1.10 | 2026-10-18 | Step 3 T2b：注册（§1.2 / 40910 / HR 自动建未认证公司）落地；补 §6.10（注册落库与演示库重置 / e2e 固定手机号重跑风险 / LambdaWrapper TableInfo 单测坑） |
| v1.11 | 2026-10-18 | Step 3 T3a：职位公开读（列表 / 搜索 / 详情 / 相似 / 埋点）+ 分类字典（分类树 / 城市 / 字典）落地；补 §6.11（`@Select` 实体转义 / ETag 与 traceId / `nextCursor: null` / 公开接口可选鉴权与租户豁免 / **T9 需加 Next rewrites**） |
| v1.12 | 2026-10-18 | Step 3 T3b：HR 职位写（发布 / 更新 / 关闭·重开 / 软删）落地；补 §6.12（租户 INSERT 不重复列 / `job-new-{seq}` 全局序号 / publishedAt 截毫秒 / 配额缺省 50 与限流 / 40310 语义 / 冒烟软删行与 verify-seed 排除） |
| v1.13 | 2026-10-18 | Step 3 T4：投递 / 面试闭环（§9/§10 + §8.7）落地；补 §6.13（投递占位建会话 / 状态机顺序 / 幂等终态 / 40410 归属隔离 / 四类新 id 前缀 / 冒烟可重复 / T9 契约差异清单）；§9 增演示库重置策略待拍板 |
| v1.14 | 2026-10-18 | Step 3 T5：公司模块 + 企业认证（§5.1~§5.6）落地；补 §6.14（管理员以成员角色 ADMIN 为准 / 改名实时装配 / 认证无独立记录表与异步自动通过 / 公开路径白名单 / 公司职位可见口径 / smoke-company 基线） |
| v1.15 | 2026-10-18 | Step 3 T6：消息 + WebSocket（§8.1~§8.8 + 4 主题）落地；补 §6.15（DB 未读事实源 / 双端超集 / 历史顺序 / 扩展详情 / WS 鉴权与主题 / 发送不回显 / 自动回复无线程上下文 / 幂等边界 / 种子冒烟保护 / T9 前端差异） |
| v1.16 | 2026-10-18 | Step 3 T7：用户 / 简历 / 收藏 / 上传（§2/§3/§11/§12）落地；补 §6.16（重置与改号验证码场景 / Redis 偏好 / 软删注销 / 简历 HR 可见性与默认顺延 / 收藏唯一与游标 / 本地 HMAC 上传与业务落库边界 / T9 前端差异 / T8 未完提醒） |
| v1.17 | 2026-10-18 | 决策结算：接口账目以正文 70 条定稿（总览表已校正）；T9 固定手机号重置策略定为每轮 e2e 前清库重迁；§9 拆为「待确认 / 已定」 |
| v1.18 | 2026-10-18 | 顺序拍板：**T8 先、T9 后**（§9 记为已定，§6.16 同步）；`USE_MOCK` 为全局开关，T9 前必须补齐 §4 |
| v1.19 | 2026-10-19 | Step 3 T8：招聘者 / 候选人 / 人才筛选（§4.1~§4.6）落地——HR 画像读改（`hr_profiles` 非租户注入表，按 `user_id` 归属）、我发布的职位（HR 可见 CLOSED + 契约外 `GET /hr/jobs/{jobId}`）、我收到的投递（超集卡片 + 最近面试）、人才筛选（移植 mock 关键词引擎）；补 §6.17，§9 补两条已定口径（超集出参 / 扩展参数），§2 状态推进到「下一步 T9」 |
| v1.20 | 2026-10-19 | Step 3 T9：全量联调打通（`USE_MOCK=false`，`npm run e2e` **63/63**，连续两轮通过）——`next.config.ts` 加 `/api/v1` `/uploads` rewrites、`.env.local` 切开关、演示账号一键填入不再被 `USE_MOCK` 隐藏、真实登录分支补身份校验、列表统一解包 `.list`、HR 详情改走 §4.4、约面试补 `conversationId`、自动回复改定时重取、e2e 精确豁免 1 条预期 401；新增 `backend/scripts/db-reset.ps1`（清库重迁 + 清 Redis + 重启后端 + 等 health）；补 §6.18（14 条联调坑）、§6.11/§6.13 更新、§9 增「T9 遗留真实分支」待确认项 |
| v1.21 | 2026-10-19 | T9 追加（用户选 A）：**求职者职位列表切真实**——`JobsView` 改 `useQuery + listJobs()`（`GET /jobs`，`limit=100` 单页取全、计数用 `pagination.total`），删除 `src/api/mock/filter.ts`；HR 发布 / 编辑 / 关闭职位在真实模式下对求职者立即生效。§6.18 新增该条坑（limit 默认 20 / 计数用 total / q 已含公司名），遗留清单更新为「分类字典 / 消息会话 / HR 发消息」；§9 改为「部分已定」 |
| v1.22 | 2026-10-19 | 新增 Step 4 AI / RAG 增量方案文档 [`step4-ai-plan.md`](./step4-ai-plan.md) v1.0（选型 / 数据流 / 多租户红线 §5.3 / 代码结构 / V9 表草案 / T-AI0~T-AI7 / 假模型测试策略 / 风险与未决 6 项）；§2 状态表新增该行（原「Step 4 联调」更名「Step 3-T9 联调」）；§9 新增「Step 4 方案（已定 / 待确认子项）」。**本轮只落文档，未改代码** |
| v1.23 | 2026-10-19 | **Step 4 T-AI0 落地**：Spring AI 1.1.8（BOM + OpenAI starter）接入百炼 DashScope 兼容模式；`ai/` 包（`AiCodes` / `AiProperties` / `AiConnectivityService` / `AiController` → `/api/v1/ai/status|ping`）；密钥与模型全部走用户级环境变量 `PINLIAO_AI_*`（`api-key` 占位，无 key 也能启动）。**新增 §6.19（AI 接入 10 条坑）**：`sk-ws-` key 可用性与 coding 域名 401、`enable_thinking=false`（思考型模型 10s+ → 1.0s）、空 `api-key` 会导致启动失败、错误码不能用 `43xxx`、AI 自检端点须鉴权、PowerShell 5.1 下 `java -version` NativeCommandError 吞掉 JDK 探测、key 泄露三连核查法。实测 ping chat 1005ms/44 tokens、embedding 1024 维/538ms；`mvnw test` 176/176、e2e 63/63 无回归；§2 状态行与 §9 同步 |
| v1.24 | 2026-10-19 | **可观测性 T-OBS1 落地（选型由开源 OTel Agent 改为阿里云 ARMS 应用监控探针）**：新增 `scripts/fetch-arms-agent.ps1`（下载探针到 `.local/arms/`）与 `scripts/dev-with-arms.ps1`（`-DryRun` 配置自检含密钥打码 / `-Background` / `-AllowMissingLicenseKey`；`-javaagent` 只挂 fork 出的应用 JVM）；`scripts/_env.ps1` 新增 `PINLIAO_ARMS_*` 与共享函数（`Wait-BackendUp` / `Get-JavaMajor` / `Resolve-PinliaoJavaHome` / `Format-Secret`），`db-reset.ps1` 去重改用它；`common/web/TraceIdFilter` 取值改为「活跃 OTel span 的 traceId → 透传 `X-Trace-Id` → UUID」（契约不变）+ 6 项 `TraceIdFilterTest`；新增 `docs/arms-plan.md` v1.1 与 `backend/README.md`「ARMS 链路」一节。**新增 §6.20（ARMS 接入 12 条坑）**：两产品入口区别（应用监控 vs 链路 OTel 版）、探针实为 OTel javaagent 2.23.0 改名版、只能挂应用 JVM、`jvmArguments` 空格坑、探针工作目录与 `status` 文件、**未配 LicenseKey 时不产生 span**、`%mdc` 探针法、OTel API 安全降级写法、计费与前端监控门禁。实测 `mvnw test` 182/182、真实后端 e2e 63/63（零影响） |
| v1.25 | 2026-10-19 | **可观测性 T-OBS2：ARMS 链路打通**（控制台可见 `pinliao-backend`、包络 traceId 即链路 TraceId、应用 JVM 有到阿里云 80 端口的 Established 上报连接、带探针 e2e 63/63）。**§6.20 补 5 条坑**：① `Start-Process -ArgumentList` 传数组会丢引号 → `-Darms.licenseKey/appName` 进不了应用 JVM（探针加载却不产生 span、控制台看不到应用），必须传单个带引号字符串并回读进程命令行；② `.apsara-apm\java\<版本>\status` **不刷新、不可作授权判据**；③ 判活三条判据（命令行含 `-Darms.*` / 送 `X-Trace-Id` 不回显 / 有到 80 端口连接）；④ `-Darms.*` 与环境变量 `ARMS_LICENSE_KEY`/`ARMS_APP_NAME` 两种形式都可用；⑤ `OTEL_JAVAAGENT_LOGGING=debug` 是非法值（日志变 no-op、连横幅都不打），要用 `-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug` |
