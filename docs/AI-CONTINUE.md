# 聘聊 · AI 接续开发指南

> **本文件的目的**：让任何环境中的 AI（或人）读完这一份文件，就能直接继续开发本项目，
> 无需访问历史会话、无需询问用户、不会踩已踩过的坑。
>
> **本文件的定位**：运维与经验「深读手册」（命令、Mock 数据位置、已踩坑、协作规范）。
> **当前进度与工作区状态**以根目录 [`PROGRESS.md`](../PROGRESS.md) 为准
> （2026-10-16 起另有交接四件套：`PROJECT.md` / `ARCHITECTURE.md` / `PROGRESS.md` / `CHANGELOG.md`）。
> 开工顺序：先 `PROGRESS.md`，再本文件；需要细节时再按下文索引深入 `docs/` 的专项文档。
>
> 最后更新：2026-10-16 · 对应本轮提交（五项验收功能 + 人才筛选演示增量，e2e 63/63；提交历史见 `git log`）
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

> **当前状态一句话**：Step 2 验收中 —— 五项验收功能（投递 / 候选人 / 面试、验证码登录 /
> 注册、公司资料 / 企业认证）已全部完成，并新增人才筛选（演示版）增量，`npm run e2e`
> 63/63 通过；当前任务与下一步先看 [`../PROGRESS.md`](../PROGRESS.md)（状态唯一定稿）。

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
- **当前是前端验收版**：数据全为 Mock，Step 3（Spring Boot 后端）尚未开始。

---

## 2. 当前状态（务必按此判断，不要沿用旧文档）

| 阶段 | 状态 | 产物 |
|---|---|---|
| Step 0 架构 + 数据模型 + API 契约 | ✅ 完成 | `docs/architecture.md`、`data-model.md`、`api-contract.md`、`api-endpoints.md`、`prisma/schema.prisma`、`src/types/api.ts` |
| Step 1 前端开发（Next.js + Mock） | ✅ 完成 | `src/components/pinliao/`、`src/api/`、`src/store/` |
| Step 2 前端验收 | ⏳ 进行中 | 五项验收功能已完成，并新增人才筛选演示增量；e2e 63/63 通过；**用户人工验收初步通过（2026-10-16）**；待确认 §9 待决事项 |
| Step 3 后端（Spring Boot） | ⏸ 未开始 | 按契约实现接口 |
| Step 4 联调 | ⏸ 未开始 | `USE_MOCK=false` 切真实接口 |

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
| 真实后端 | Step 3，按 `docs/api-endpoints.md` 契约实现 |

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
- **后端两条 WARN 属预期**：Flyway「No migrations found」（T1 补 V1/V2 后消失）、
  MyBatis-Plus「No MyBatis mapper was found」（T2 起有 mapper 后消失）。
- 后端进程结束按**端口 8080 定位 PID** 精确处理；勿按名杀 `java`（同机 IDEA 常驻）。

---

## 7. 数据与契约

### 7.1 API 契约（框架无关，是最重要的资产）

| 文档 | 内容 |
|---|---|
| `docs/api-contract.md` | 统一规范：响应包络 `{ code, message, data, traceId }`、错误码（5 位，前 3 位对应 HTTP 状态）、**游标分页**（不用 offset）、限流、WebSocket、安全 |
| `docs/api-endpoints.md` | 全部业务接口清单（13 模块，正文 70 条）。**注意**：总览表格分模块数字（67）与合计 70 不一致，口径待用户确认后统一订正；逐条以正文为准 |
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

## 9. 待用户确认的事项（不要替用户决定）

| 事项 | 说明 |
|---|---|
| 最终上线前端框架 | Next.js / Vue / uni-app，当前 Next.js 仅沙箱验收 |
| 招聘者端桌面 Web 后台布局 | 当前仅移动端布局 |
| `docs/api-endpoints.md` 总览表格分模块数字（67）如何与合计（70）对齐 | 涉及既有设计资产；正文 70 条为准 |
| 是否自托管字体摆脱 Google Fonts 构建依赖 | `next/font/local` |
| 是否清除 `next-themes` 的 script 告警 | 无害，但刷屏 |
| 两个历史压缩包是否清理 | `files.zip`、`workspace-*.tar` |

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
