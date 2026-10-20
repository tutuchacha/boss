# 聘聊 · AI 接续开发指南（深读手册）

> **定位**：经验与规范手册——上手、目录、踩坑、协作约定。
> **状态只看** [`../PROGRESS.md`](../PROGRESS.md)；**找文件看** [`INDEX.md`](./INDEX.md)。
>
> **不必整读本文件**：先看下面的导航，再按需跳读，单次 `read` ≤ 300 行。
> §6 的坑已按主题拆到 `pitfalls/`，本文只留索引；命令 / 验收清单在 `state/commands.md`。
>
> 最后更新：2026-10-20（T-AI3 后按「分层文档」重组）

## 导航

| 想知道 | 去哪 |
| --- | --- |
| 现在做到哪 / 下一步 | [`../PROGRESS.md`](../PROGRESS.md) |
| 文件级地图 | [`INDEX.md`](./INDEX.md) |
| 命令 / 冒烟 / 验收清单 / 测试基线 | [`state/commands.md`](./state/commands.md) |
| 后端各 T 阶段接口事实与环境坑 | [`state/backend-facts.md`](./state/backend-facts.md) |
| AI / ARMS 环境变量与接口 | [`state/ai-config.md`](./state/ai-config.md) |
| 历次交付改了哪些文件 | [`state/deliverables.md`](./state/deliverables.md) |
| 踩坑 | [`pitfalls/`](./pitfalls/)（索引见 §6） |
| 历史（worklog / changelog / 旧计划） | [`archive/`](./archive/)（默认不读） |

> 环境开关现状：默认读 `.env.local`（`USE_MOCK=false`，连真实后端）。只想跑前端 Mock 时，
> 把 `NEXT_PUBLIC_USE_MOCK` 置回 `true` 并重启 dev server——Mock 数据在内存，刷新即重置。

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

**无需数据库即可看前端**：默认联调态读 `.env.local`（`USE_MOCK=false`，经 rewrites 连真实后端 :8080）；
只想跑纯前端 Mock 时，把 `NEXT_PUBLIC_USE_MOCK` 置回 `true` 并重启 dev server（Mock 数据在内存，刷新即重置）。

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

## 1. 项目是什么

**聘聊**：参考 Boss 直聘模式的招聘平台原型。

- **C 端求职者 + 多 B 端企业客户共存**。求职者跨企业共享（一份简历可投多家）。
- **每个注册企业即一个「租户」**，主键 `companyId`；求职者账号不属于任何租户。
- **首发只做 H5/Web**（不含 App、小程序）。
- **前端已切真实后端**（Step 3 T9 起）：`.env.local` 置 `NEXT_PUBLIC_USE_MOCK=false` +
  `NEXT_PUBLIC_API_BASE_URL=/api/v1`，由 `next.config.ts` rewrites 代理到 `:8080`；改后须重启 dev server。
  仍读 mock 的只剩求职者「分类字典 / 消息会话」与 HR「发消息」入口（见 `pitfalls/backend-collab.md` §6.18 末尾）。

## 2. 功能清单（状态见 PROGRESS §1）

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
| 真实后端（其余模块） | Step 3 进行中：T0~T9 已落地（鉴权 / 注册 / 职位 / 投递面试 / 公司 / 消息 WebSocket / 用户简历收藏上传 / 招聘者与人才筛选 / **全量联调**）；`pitfalls/backend-collab.md` §6.18 末尾列出仍需从 mock 切到真实的前端路径 |

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

## 5. 运维与验证

全部命令、冒烟脚本逐项说明、验证分级、验收清单与实测记录，统一在
[`state/commands.md`](./state/commands.md)——本文件不再重复，避免两处口径打架。
环境层面的坑（`JAVA_HOME` / 3307 / Redis / PowerShell 编码）见 §0「环境注意」与
[`state/backend-facts.md`](./state/backend-facts.md)；既有报错见 `../PROGRESS.md` §6.1。

## 6. 坑与教训（索引）

先按症状定位，再只读那一节；正文在 `pitfalls/`，编号 `6.x` 与旧文档一致（旧引用可直接对照）。

| # | 主题 | 一句话 | 文件 |
| --- | --- | --- | --- |
| 6.1 | 循环依赖 | `jobs.ts` ←→ `hrJobs.ts` | [`pitfalls/frontend.md`](./pitfalls/frontend.md) |
| 6.2 | Zustand 选择器 | 返回新对象 → 无限重渲染 | 同上 |
| 6.3 | 误杀用户浏览器 | 只能 `killChromeTree(pid)` | 同上 |
| 6.4 | 身份校验时机 | 必须早于写登录态 | 同上 |
| 6.5 | 取数方式 | 手写 `useEffect + setState` 触发 React Compiler 告警 | 同上 |
| 6.6 | 拆组件 | 同一组件既取数又 setState | 同上 |
| 6.7 | 其他前端小坑 | 文案歧义 / 页签重置 / id 生成 | 同上 |
| 6.8 | 后端本地环境 | `JAVA_HOME` / 3307 / PowerShell 编码 | [`pitfalls/backend-core.md`](./pitfalls/backend-core.md) |
| 6.9 | 多租户 + 种子 | 求职者不注入租户；种子由 mock 生成 | 同上 |
| 6.10 | 注册落库 | 注册记录持久 + 清库才能重复注册 | 同上 |
| 6.11 | 职位公开读 | 游标分页 / ETag / rewrites 代理 | 同上 |
| 6.12 | 职位写 | 40310 / 42211 配额 / 42901 限流 / 软删保留行 | 同上 |
| 6.13 | 投递 / 面试 | 状态机 40911~42210 + 会话占位 | 同上 |
| 6.14 | 公司 / 企业认证 | ADMIN 以成员表为准 / 约 1.2s 自动通过 | 同上 |
| 6.15 | 消息 / WebSocket | 未读以 DB 为事实源 / Redis 限流 / STOMP | [`pitfalls/backend-collab.md`](./pitfalls/backend-collab.md) |
| 6.16 | 用户 / 简历 / 收藏 / 上传 | HR 仅看已投递 / 本地目录预签名 | 同上 |
| 6.17 | 招聘者 / 人才筛选 | `hr_profiles` 非租户表 / 出参超集 | 同上 |
| 6.18 | T9 全量联调 | 7 类差异 + 清库重迁 + 遗留 mock 路径 | 同上 |
| 6.19 | AI 接入 | 兼容模式端点 / `enable_thinking=false` / 42220+ | [`pitfalls/ai.md`](./pitfalls/ai.md) |
| 6.20 | ARMS | `Start-Process` 丢引号 / `status` 不刷新 | 同上 |
| 6.21 | 单简历问答 / 引用 | 可见性前置过滤 / `[n]` 兜底 / 配额降级 | 同上 |
| 6.22 | 流式问答 / SSE | rewrites 缓冲 → 站点根路径代理 / `Accept` 头 | 同上 |

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

## 9. 待用户确认 / 已定事项（不要替用户决定）

| 事项 | 说明 |
|---|---|
| 最终上线前端框架 | Next.js / Vue / uni-app，当前 Next.js 仅沙箱验收 |
| 招聘者端桌面 Web 后台布局 | 当前仅移动端布局 |
| T8 / T9 先后（已定） | **先 T8 后 T9**（2026-10-18 用户拍板）：先补齐 §4 再整体切 `USE_MOCK=false`；`USE_MOCK` 无模块级粒度，先 T8 可避免二次返工 |
| T9 演示库重置策略（已定，已落地） | 每轮 e2e 前清库重迁（重建 + Flyway V1~V8），固定手机号 `13900004321` 可反复使用；脚本 **`backend/scripts/db-reset.ps1` 已落地并实测**（2026-10-19，含停后端 / 清 Redis / 等 health） |
| T9 遗留的真实分支（**部分已定 / 部分待确认**） | **已切**：求职者职位列表 / 详情（`JobsView` → `listJobs`，2026-10-19 用户选 A）。**仍待定**：求职者「分类字典 / 消息会话」，HR「发消息」入口——e2e 63/63 不依赖它们；建议独立一轮（T10），先补求职者消息页断言再切（详见 `pitfalls/backend-collab.md` §6.18 末尾） |
| §4.3 / §4.4 出参口径（已定） | **给前端超集**（2026-10-18 用户拍板）：`HrJobRecord[]` / `HrApplicationView[]`，而非契约文字的 `Job[]` / `ApplicationWithJob[]`，换取 T9 前端零适配 |
| §4.3 / §4.4 契约外参数（已定） | **接受为可选扩展参数**（2026-10-18 用户拍板）：§4.3 的 `q` / `hrId`、§4.4 的 `q`；`cursor` 保留但未启用 |
| 是否自托管字体摆脱 Google Fonts 构建依赖 | `next/font/local` |
| 是否清除 `next-themes` 的 script 告警 | 无害，但刷屏 |
| 两个历史压缩包是否清理 | `files.zip`、`workspace-*.tar` |
| Step 4 AI / RAG 方案（**已定 / 待确认子项**） | **已定**（2026-10-19 用户拍板）：通义千问一站式（对话 `qwen3.5-122b-a10b` + `text-embedding-v4` 1024 维）、向量库先 `SimpleVectorStore` 后 MySQL 自建表、Spring AI 1.1.x 主线 + 事后补 LangChain4j、**单简历问答先行**；**T-AI0 已落地**（依赖 / 环境变量配置 / `/api/v1/ai/*` 自检）。**待确认**：见 [`step4-ai-plan.md`](./step4-ai-plan.md) §10 其余五项（向量维度 1024 vs 1536 / 是否接 Ollama / 配额策略 / 是否含求职者侧能力 / Boot 4 + Spring AI 2.0 迁移时机） |
| AI 密钥的存放位置（已定，已落地） | **用户级环境变量**（`环境变量` 对话框 → 用户变量），6 个 `PINLIAO_AI_*`；仓库只留 `${引用}`。理由：防泄露（用户明确要求「不要被上传 git 用光额度」）+ 一处修改全局生效；系统变量需管理员且扩大泄露面，不采用。已核查 `git grep` / 未跟踪文件 / 全部提交历史零命中 |

## 10. 修订记录

2026-10-20 之前的全部修订条目 → [`archive/ai-continue-revisions.md`](./archive/ai-continue-revisions.md)。

本次重组：§6 拆分为 `pitfalls/*`（编号 6.1~6.22 保持不变，旧引用仍可对照）、
§5 归并到 `state/commands.md`、§2 状态表删除（改指向 `PROGRESS.md`，消除双份真相）。
