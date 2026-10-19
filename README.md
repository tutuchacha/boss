# 聘聊 · Pinliao

参考 Boss 直聘模式的招聘平台原型：**C 端求职者 + 多 B 端企业客户共存**。
求职者跨企业共享（同一份简历可投多家），每个注册企业即一个「租户」（Tenant）。

> **当前阶段**：Step 2 前端验收版（e2e 63/63）· Step 3 后端 **T0~T9 全部完成**（T0 ✅、T1 ✅、T2a ✅、T2b ✅、
> T3a ✅、T3b ✅、T4 ✅、T5 ✅、T6 ✅、T7 ✅、T8 ✅、T9 ✅，2026-10-19；任务板见 `docs/step3-plan.md`）。
> 前端可继续走本地 Mock（无需后端即可运行），也可切真实后端联调：`.env.local` 置
> `NEXT_PUBLIC_USE_MOCK=false`（`next.config.ts` 已有 `/api/v1` 代理），真实后端下 e2e 同为 63/63。

---

## 快速开始

```bash
npm install
npm run dev
```

访问 **<http://localhost:3000>**

### 演示账号

登录页有「演示账号（一键填入）」，点一下即可自动填好。

| 身份 | 账号 | 密码 | 进入 |
|---|---|---|---|
| 求职者 | `13800000001` | `123456` | 职位浏览 / 投递 / 收藏 / 简历 / 消息 |
| 公司代表 HR | `13800000002` | `123456` | 职位管理 / 候选人 / 沟通 / 我的 |

登录页支持**密码登录 / 验证码登录 / 注册**三种方式（演示环境短信验证码固定
`123456`）；新手机号注册「公司代表」会自动创建一个未认证公司，可体验企业认证流程。

登录状态保存在浏览器本地；两端均可在「我的」页退出登录后切换身份。

> 若账号与所选身份不符，登录页会提示并阻止进入，可一键切换到正确身份。

---

## 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 框架 | Next.js 16（App Router、Turbopack） | 当前为沙箱验收用 |
| 语言 | TypeScript 5 | |
| 样式 | Tailwind CSS 4 + shadcn/ui | 青绿主题（`--primary: #00857F`） |
| 状态 | Zustand（含 persist） | 客户端状态 |
| 数据 | TanStack Query | 服务端状态（两端新模块均走 Query，旧模块部分同步 Mock） |
| ORM | Prisma | **当前未接入业务**，见下文 |
| 后端 | Spring Boot 3.5 + MySQL 8 + Redis（`backend/`） | Step 3 **T0~T9 已完成**（鉴权 / 职位 / 投递 / 面试 / 公司 / 消息 + WebSocket / 用户简历收藏上传 / 招聘者与人才筛选 / 全量联调；`mvnw test` 173/173，真实后端下前端 e2e 63/63）；计划见 `docs/step3-plan.md` |

> `zod`、`react-hook-form` 等已随模板装好但**当前代码尚未使用**，故不列入上表。

> **前端框架最终选型未定**：`docs/HANDOFF.md` 中记录，当前 Next.js 仅用于 H5 验收；
> 若后续需要 App / 小程序，计划另起 uni-app 工程复用同一套 API 契约。

---

## 功能现状

### 求职者端（H5）

- 职位列表：城市切换、实时搜索、一级分类 + 二级岗位联动、筛选面板、推荐/最新排序
- 职位详情：薪资福利、招聘者卡片、收藏、立即沟通、**投递简历**（附打招呼语）
- 我的投递：投递进度（待处理 / 已查看 / 面试中 / 已录用 / 未通过 / 已撤回）、
  撤回投递、**面试邀请的接受 / 婉拒**
- 消息：会话列表、未读角标、聊天页、快捷回复、关键词自动回复
- 我的：个人卡片、求职期望编辑、收藏列表、简历预览、外观切换（跟随系统/浅色/深色）
- 登录 / 验证码登录 / 注册 / 退出登录

### HR 端（H5，与求职者端同一移动端布局）

- 登录（与求职者共用登录页，登录时选择身份）
- **职位管理**：状态筛选（全部 / 招聘中 / 已关闭）、关键字搜索、仅看我发布的
  - 发布、编辑、关闭 / 重新开放、删除（软删，二次确认）
  - 数据概览：招聘中数量、累计投递、累计浏览
- **候选人**：按投递状态筛选（待处理 / 已查看 / 面试中 / 已录用 / 未通过 / 已撤回）、
  搜索；候选人详情（简历完善度、标记已查看 / 录用 / 不合适）、**发起面试邀请**
- **人才筛选**：按技能需求匹配收到的候选人简历——自然语言描述 + 技能标签组合，
  展示命中关键词与关联证据片段（简历原文高亮）；演示版为关键词匹配，接口预留
  语义检索（embedding）升级
- **候选人沟通**：会话列表（候选人画像 + 应聘职位 + 未读角标）、仅看未读、按职位筛选、搜索
  - 聊天页：气泡、快捷回复、候选人自动回复（含「正在输入」）、进入即标记已读、
    面试邀请卡片
- **公司资料与企业认证**：公司资料编辑（名称 / 简介 / 行业 / 阶段 / 规模 / 官网）；
  企业认证提交（Mock 审核约 1.2 秒自动通过，状态同步「我的」角标）
- 我的：公司信息、公司资料、企业认证、退出登录

### 尚未实现

| 功能 | 状态 |
|---|---|
| 招聘者端桌面 Web 后台 | 未实现（当前仅移动端布局） |
| 真实后端（其余模块） | Step 3 **T0~T9 已完成**：鉴权（§1）/ 职位（§6/§7）/ 投递与面试（§9/§10）/ 公司（§5）/ 消息与 WebSocket（§8）/ 用户·简历·收藏·上传（§2/§3/§11/§12）/ 招聘者与人才筛选（§4）全部落地，前端已可切 `USE_MOCK=false`（真实后端下 e2e 63/63）；**遗留**：求职者端职位列表 / 分类字典 / 消息会话仍读 mock（待拍板，见 `PROGRESS.md` §6.3） |

---

## 项目结构

```
src/
├─ app/                      Next.js 入口（单页 SPA，仅一个可见路由）
│  ├─ page.tsx               渲染 RootGate
│  ├─ layout.tsx             根布局（主题、字体、Query Provider、Toast）
│  └─ globals.css            青绿主题 token（--pl-* / --primary）
├─ components/
│  ├─ pinliao/               业务组件
│  │  ├─ RootGate.tsx        按登录状态与角色分发
│  │  ├─ LoginPage.tsx       登录页（密码 / 验证码 / 注册三模式）
│  │  ├─ AppShell.tsx        求职者端外壳
│  │  └─ hr/                 HR 工作台（职位管理 / 候选人 / 沟通 / 聊天 / 公司资料与认证 / 我的）
│  └─ ui/                    shadcn/ui 基础组件
├─ api/                      service 层（组件不直接 fetch）
│  ├─ client.ts              统一 fetch + 响应包络解包 + ApiError
│  ├─ auth.ts / jobs.ts / hrJobs.ts / hrConversations.ts
│  ├─ applications.ts / hrApplications.ts / hrTalentMatch.ts / company.ts
│  └─ mock/                  Mock 数据（真实接口切换时整体替换）
├─ store/                    Zustand（pinliao 求职者 / auth 认证 / hr 工作台）
├─ hooks/                    通用 hooks（含水合守卫）
├─ lib/                      工具函数
└─ types/api.ts              与 API 契约同步的类型定义
```

### 架构约定

- **单页 SPA，不走路由**：所有视图通过各自 store 的 `tab` + `pageStack` 切换。
  引入 `next/navigation` 会改变架构，请先阅读 `docs/HANDOFF.md`。
- **service 层隔离**：组件只依赖 `src/api/*`，不直接 `fetch`。
  `src/config/env.ts` 的 `USE_MOCK` 控制 Mock / 真实接口切换。
- **多租户**：业务表带 `companyId`；后端（MySQL 8）由 MyBatis-Plus 拦截器注入租户条件，
  应用层校验兜底（原 PostgreSQL RLS 方案已随 T0 数据库定案作废，见 `docs/step3-plan.md` §6）。
- **水合安全**：persist store 使用 `skipHydration: true`，由 `RootGate` 挂载后统一
  `rehydrate()`，避免 SSR/CSR 不一致导致 hydration 报错。

---

## 常用命令

```bash
npm run dev        # 启动开发服务器（端口 3000）
npm run e2e        # 端到端验收（需先开着 dev server）
npm run build      # 生产构建
npm run lint       # ESLint
npx tsc --noEmit   # 类型检查
```

> `npm run build` 需要能访问 Google Fonts（`src/app/layout.tsx` 使用 `next/font/google`
> 加载 Geist 字体）。国内直连会构建失败，设置代理后可正常构建：
>
> ```powershell
> $env:HTTPS_PROXY='http://127.0.0.1:7897'
> $env:HTTP_PROXY='http://127.0.0.1:7897'
> npm run build
> ```
>
> 端口请按本机实际代理填写。彻底解决可改为自托管字体（`next/font/local`）。

### 后端（Step 3，T0~T9 已完成）

`backend/` 为 Spring Boot 3.5 工程（MySQL 专用实例 3307 + Redis 6379，运行数据在 `.local/`，
环境要求与完整说明见 `backend/README.md`）：

```powershell
cd backend
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr'  # 必设（本机仅 JDK 1.8）
.\scripts\dev-up.ps1         # 起 MySQL 3307 + Redis 6379
.\scripts\db-reset.ps1       # 清库重迁（每轮前端 e2e 前必跑）
.\mvnw.cmd spring-boot:run   # 后端服务 :8080
.\mvnw.cmd test              # 单元测试（基线 173/173）
node scripts\smoke-auth.mjs  # 鉴权接口冒烟（T2a/T2b，20 项含注册）
node scripts\smoke-jobs.mjs  # 职位与字典冒烟（T3a/T3b，49 项）
node scripts\smoke-applications.mjs  # 投递与面试冒烟（T4，51 项）
node scripts\smoke-company.mjs  # 公司模块冒烟（T5，26 项）
node scripts\smoke-messages.mjs  # 消息与 WebSocket 冒烟（T6，40 项）
node scripts\smoke-user.mjs  # 用户 / 简历 / 收藏 / 上传冒烟（T7，64 项）
node scripts\smoke-recruiter.mjs  # 招聘者模块冒烟（T8，50 项）
```

前端联调（Step 3 T9）：仓库根目录 `.env.local`（不提交）置 `NEXT_PUBLIC_USE_MOCK=false` +
`NEXT_PUBLIC_API_BASE_URL=/api/v1`，`next.config.ts` 已把 `/api/v1` 与 `/uploads` 代理到
`http://localhost:8080`；改后重启 dev server，`npm run e2e` 在真实后端下同为 63/63。

### 数据库说明

`prisma/schema.prisma` 是**领域模型设计文档**（19 个模型 + 10 个枚举 + 多租户字段标注），
并非当前运行时依赖：

- 当前无人 `import` `src/lib/db.ts`，因此 **`prisma generate` 不是启动前置条件**
- datasource 为 SQLite，路径取自 `.env` 的 `DATABASE_URL`
- 真实数据库为 **MySQL 8**（用户拍板，2026-10-17；专用实例 3307），由 `backend/` 的
  Flyway 迁移维护建表（`backend/src/main/resources/db/migration/`；V1 鉴权建表 / V2 演示账号 /
  V3 业务建表 / V4~V8 演示种子）

---

## 文档索引

**想让任何 AI / 新开发者接手这个项目？只需要给它看一份文件：**

> **`docs/AI-CONTINUE.md`** —— AI 接续开发指南。充要、自包含、与具体工具无关：
> 三分钟上手、当前状态、架构约定、运维手册、**已踩过的坑与教训**、协作规范。
> 换任何环境（换机器 / 换 AI / 隔几个月）都应先读它。

仓库根目录还有一组配套文档（在仓库内工作的 AI / 人按此顺序读，各司其职避免「多份真相」）：

| 文件 | 内容 |
|---|---|
| `PROGRESS.md` | **当前状态唯一定稿**：已完成 / 下一步 / 风险 / 测试结果 |
| `ARCHITECTURE.md` | 验收版现状架构（技术栈 / 目录 / 模块关系） |
| `PROJECT.md` | 项目定位、目标与业务范围 |
| `AGENTS.md` | 开发约束与规范（硬性约束、验收命令、文档维护义务） |
| `CHANGELOG.md` | 重要代码变更累积记录 |

`docs/` 下的其余文档是**框架无关**的设计资产，任何前端 / 后端实现都可复用：

| 文件 | 内容 |
|---|---|
| `AI-CONTINUE.md` | **AI/开发者接续入口（先读这个）** |
| `architecture.md` | 总体架构、多租户、并发/高可用预留、技术栈、部署演进 |
| `data-model.md` | 表清单、字段说明、索引、ER 图 |
| `api-contract.md` | API 通用规范（包络 / 错误码 / 分页 / 限流 / WebSocket / 安全） |
| `api-endpoints.md` | 全部业务接口清单（13 个模块，按模块标注方法与鉴权角色） |
| `step3-plan.md` | Step 3 后端分块开发计划与任务板（T0~T9 进度、每块规格、交接协议） |
| `dev-notes.md` | 决策记录与路线图 |
| `HANDOFF.md` | ⚠️ 已降级为「Step 0 决策历史」，当前状态以 `AI-CONTINUE.md` 为准 |
| `legacy/` | 原始 HTML 原型与 v0.2 开发笔记 |
| `../worklog.md` | 协作 Agent 工作日志 |

### 给接续开发者 / AI 的启动提示词

```
我有一个招聘平台项目「聘聊」，当前是前端验收版（Next.js 16 + Mock）+ Step 3 后端进行中（Spring Boot，鉴权已落地）。
请先读 docs/AI-CONTINUE.md —— 它包含项目全貌、当前状态、架构约定、
已踩过的坑与协作规范，读完即可开工，无需再问我背景问题。

本次我想做的是：<你的需求，例如"开发候选人管理页"或"开始 Step 3 后端">
```

---

## 开发路线

| 阶段 | 内容 | 状态 |
|---|---|---|
| Step 0 | 框架无关的架构、数据模型、API 契约 | ✅ |
| Step 1 | 前端开发（Next.js + Mock） | ✅ |
| Step 2 | 前端验收（五项验收功能 + 人才筛选演示增量已完成，`npm run e2e` 63/63） | ⏳ 进行中 |
| Step 3 | 后端开发（Spring Boot 3.5 + MySQL/Redis；分块计划 `docs/step3-plan.md`） | ✅ 完成（T0 ✅ / T1 ✅ / T2a ✅ / T2b ✅ / T3a ✅ / T3b ✅ / T4 ✅ / T5 ✅ / T6 ✅ / T7 ✅ / T8 ✅ / T9 ✅，2026-10-19；`mvnw test` 173/173） |
| Step 4 | 联调（`USE_MOCK=false`，Mock → 真实接口） | ✅ 基本完成（真实后端下 `npm run e2e` 63/63；遗留见 `PROGRESS.md` §6.3） |
| 未来 | App / 小程序（若决定多端，复用 API 契约） | — |

---

## 许可

私有项目，未经授权请勿分发。
