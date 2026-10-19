# 聘聊 · Pinliao

参考 Boss 直聘模式的招聘平台原型：**C 端求职者 + 多 B 端企业客户共存**。
求职者跨企业共享（同一份简历可投多家），每个注册企业即一个「租户」（Tenant）。

> **当前阶段**：Step 2 前端验收版（e2e 63/63）· Step 3 后端 **T0~T9 全部完成**（鉴权 / 职位 / 投递 / 面试 /
> 公司 / 消息 + WebSocket / 用户·简历·收藏·上传 / 招聘者与人才筛选 / 全量联调，2026-10-19；任务板见 `docs/step3-plan.md`）。
> 前端可继续走本地 Mock（无需后端即可运行），也可切真实后端联调：`.env.local` 置
> `NEXT_PUBLIC_USE_MOCK=false`（`next.config.ts` 已有 `/api/v1` 代理），真实后端下 e2e 同为 63/63。
>
> **进行中（Step 4）**：接入 AI / RAG——HR 基于候选人简历问答（带原文引用）与语义人才检索；
> 方案与任务板见 `docs/step4-ai-plan.md`（**T-AI0 ✅**：Spring AI 1.1.8 + 百炼 DashScope 兼容模式
> 打通，密钥走本机环境变量；下一步 T-AI1 索引入库）。
>
> **可观测性（T-OBS）✅ 链路已打通**：接入阿里云 ARMS **应用监控**（Java 探针）——
> 控制台「应用监控 → 应用列表」可见 `pinliao-backend`，响应包络里的 `traceId` **就是**链路 TraceId
> （可到控制台按 TraceId 反查 HTTP + MySQL/Redis 子调用）；带探针 `mvnw test` 182/182、真实后端 e2e 63/63。
> 方案与踩坑见 `docs/arms-plan.md`、`docs/AI-CONTINUE.md` §6.20。

### 进度速览

| 阶段 | 状态 | 关键证据 |
|---|---|---|
| Step 0 架构 + 契约 | ✅ | `docs/architecture.md`、`data-model.md`、`api-*.md` |
| Step 1 前端开发 | ✅ | 求职者端 + HR 端 + 登录/注册（单页 SPA） |
| Step 2 前端验收 | ✅ 五项验收功能 + 人才筛选增量 | `npm run e2e` **63/63**（Mock 与真实后端一致） |
| Step 3 后端 + 联调 | ✅ T0~T9 | `backend/` Spring Boot 3.5 + MySQL 3307 + Redis 6379；`mvnw test` **182/182** |
| Step 4 AI / RAG | 🔄 T-AI0 ✅ | `GET /api/v1/ai/status\|ping` 实测 chat + embedding（1024 维） |
| 可观测性 T-OBS | ✅ T-OBS1 + T-OBS2 | ARMS 应用监控可见、traceId 对齐、带探针 e2e 63/63 |

> 每一步的「为什么 / 踩过的坑 / 下一步」以 [`PROGRESS.md`](./PROGRESS.md) 为唯一定稿；
> 换人/换 AI 接手先读 [`docs/AI-CONTINUE.md`](./docs/AI-CONTINUE.md)。

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
| 后端 | Spring Boot 3.5 + MySQL 8 + Redis（`backend/`） | Step 3 **T0~T9 已完成**（鉴权 / 职位 / 投递 / 面试 / 公司 / 消息 + WebSocket / 用户简历收藏上传 / 招聘者与人才筛选 / 全量联调；`mvnw test` **182/182**，真实后端下前端 e2e 63/63）；计划见 `docs/step3-plan.md`；**Step 4 AI：T-AI0 ✅**（Spring AI + 百炼 DashScope 打通）；**可观测性 T-OBS1 + T-OBS2 ✅**（阿里云 ARMS 应用监控，链路已打通，见 `docs/arms-plan.md`） |

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
| 真实后端（其余模块） | Step 3 **T0~T9 已完成**：鉴权（§1）/ 职位（§6/§7）/ 投递与面试（§9/§10）/ 公司（§5）/ 消息与 WebSocket（§8）/ 用户·简历·收藏·上传（§2/§3/§11/§12）/ 招聘者与人才筛选（§4）全部落地，前端已可切 `USE_MOCK=false`（真实后端下 e2e 63/63）；**求职者职位列表 / 详情已切真实**（HR 发布 / 关闭对求职者生效）；**遗留**：求职者端分类字典 / 消息会话仍读 mock（待拍板，见 `PROGRESS.md` §6.3） |

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

backend/                     Spring Boot 后端（Step 3 T0~T9 + Step 4 T-AI0 + 可观测性）
├─ src/main/java/com/pinliao/backend/
│  ├─ auth/ user/ job/ application/ company/ message/ recruiter/ ai/   业务模块
│  └─ common/                包络 / 异常 / 租户拦截 / traceId 过滤器 / WebSocket
├─ src/main/resources/db/migration/   Flyway V1~V8（建表 + 演示种子）
└─ scripts/                  起停、清库重迁、冒烟、ARMS 探针（fetch/dev-with-arms）
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
.\scripts\dev-up.ps1         # 起 MySQL 3307 + Redis 6379（首次先 .\scripts\db-init.ps1）
.\scripts\db-reset.ps1       # 清库重迁（每轮前端 e2e 前必跑）
.\mvnw.cmd spring-boot:run   # 后端服务 :8080（无探针）
.\mvnw.cmd test              # 单元测试（基线 182/182）
node scripts\smoke-auth.mjs  # 鉴权接口冒烟（T2a/T2b，20 项含注册）
node scripts\smoke-jobs.mjs  # 职位与字典冒烟（T3a/T3b，49 项）
node scripts\smoke-applications.mjs  # 投递与面试冒烟（T4，51 项）
node scripts\smoke-company.mjs  # 公司模块冒烟（T5，26 项）
node scripts\smoke-messages.mjs  # 消息与 WebSocket 冒烟（T6，40 项）
node scripts\smoke-user.mjs  # 用户 / 简历 / 收藏 / 上传冒烟（T7，64 项）
node scripts\smoke-recruiter.mjs  # 招聘者模块冒烟（T8，50 项）
```

### 可观测性（阿里云 ARMS，可选）

想在本机看到调用链（HTTP → MySQL / Redis → 百炼），用带探针的方式起后端：

```powershell
.\scripts\fetch-arms-agent.ps1      # 首次：下载 ARMS 探针到 .local\arms\（≈51MB，已 gitignore）
.\scripts\dev-with-arms.ps1 -DryRun # 配置自检（LicenseKey 打码）
.\scripts\dev-with-arms.ps1 -Background   # 后台启动 + 等 health UP，日志 .local\backend-arms.log
```

凭据只走**用户级环境变量**（`PINLIAO_ARMS_ENABLED` / `_LICENSE_KEY` / `_APP_NAME`），仓库零命中。
启动后控制台「应用监控 → 应用列表」可见 `pinliao-backend`，把任意接口响应里的 `traceId`
粘到「调用链查询」即可反查。**默认关闭**：不设开关时一切行为与未接入完全一致；收工跑
`db-reset.ps1` 自动恢复无探针实例。判活三条判据与踩坑见 `docs/arms-plan.md` §4。

> 小提示：探针只挂 `spring-boot:run` **fork 出的应用 JVM**，不要用 `JAVA_TOOL_OPTIONS`（否则 Maven 也挂，
> 控制台会出现两个同名应用）；`.apsara-apm\java\<版本>\status` 文件不刷新，不能当作是否授权的判据。

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
| `step4-ai-plan.md` | **Step 4 AI / RAG 增量计划**（HR 简历问答 → 语义人才检索；选型、数据流、多租户红线、T-AI0~T-AI7 任务板、测试策略；**T-AI0 已完成**，配置与实测见 §6.2/§7） |
| `arms-plan.md` | **可观测性增量计划（阿里云 ARMS 应用监控）**：路径选型（开源 OTel Agent vs 阿里云 ARMS 探针）、环境变量、脚本用法、**判活三条判据**、T-OBS0~T-OBS3 任务板与本机实测记录；**T-OBS1 + T-OBS2 已完成（链路已打通）** |
| `dev-notes.md` | 决策记录与路线图 |
| `HANDOFF.md` | ⚠️ 已降级为「Step 0 决策历史」，当前状态以 `AI-CONTINUE.md` 为准 |
| `legacy/` | 原始 HTML 原型与 v0.2 开发笔记 |
| `../worklog.md` | 协作 Agent 工作日志 |

### 给接续开发者 / AI 的启动提示词

```
我有一个招聘平台项目「聘聊」，当前状态是：
- 前端（Next.js 16 + Zustand + TanStack Query）已完成 Step 2 验收，e2e 63/63；
- 后端（backend/，Spring Boot 3.5 + MySQL 8 + Redis）已完成 Step 3 全部 T0~T9，`mvnw test` 182/182，
  真实后端下前端 e2e 63/63；
- Step 4 AI/RAG 已开工（T-AI0 打通 Spring AI + 百炼），可观测性已接阿里云 ARMS 应用监控（链路已打通）。

请先读 docs/AI-CONTINUE.md（全貌 + 约定 + 踩坑），再看 PROGRESS.md（当前状态唯一定稿），
读完即可开工，无需再问我背景问题。

本次我想做的是：<你的需求，例如"T-AI1 索引入库"或"核对 ARMS 慢 SQL 大盘">
```

---

## 开发路线

| 阶段 | 内容 | 状态 |
|---|---|---|
| Step 0 | 框架无关的架构、数据模型、API 契约 | ✅ |
| Step 1 | 前端开发（Next.js + Mock） | ✅ |
| Step 2 | 前端验收（五项验收功能 + 人才筛选演示增量） | ✅ 完成（`npm run e2e` 63/63；用户人工验收初步通过 2026-10-16，遗留待拍板项见 `PROGRESS.md` §6.3） |
| Step 3 | 后端开发 + 全量联调（Spring Boot 3.5 + MySQL/Redis；`USE_MOCK=false` 下真实接口；分块计划 `docs/step3-plan.md`） | ✅ 完成（T0 ✅ / T1 ✅ / T2a ✅ / T2b ✅ / T3a ✅ / T3b ✅ / T4 ✅ / T5 ✅ / T6 ✅ / T7 ✅ / T8 ✅ / T9 ✅，2026-10-19；`mvnw test` 182/182，真实后端下前端 e2e 63/63；遗留见 `PROGRESS.md` §6.3） |
| Step 4 | **AI / RAG 增量**（HR 简历问答带引用 → 语义人才检索；计划 `docs/step4-ai-plan.md`） | 🔄 T-AI0 ✅（2026-10-19：Spring AI 1.1.8 + 百炼 DashScope 打通、`/api/v1/ai/*` 自检、密钥走环境变量）；下一步 T-AI1 索引入库 |
| 可观测性 | **阿里云 ARMS 应用监控接入**（计划 `docs/arms-plan.md`；任务号 T-OBS0~T-OBS3） | ✅ T-OBS1 + T-OBS2 完成（2026-10-19：控制台可见 `pinliao-backend`、包络 traceId 即链路 TraceId、有上报连接；带探针 `mvnw test` 182/182、e2e 63/63）；T-OBS3（慢 SQL/异常分析/前端监控）可选 |
| 未来 | App / 小程序（若决定多端，复用 API 契约） | — |

### 接下来的推进建议

1. **Step 4 主线（推荐）**：T-AI1 索引入库（`ResumeChunker` 结构化分块 + V9 `resume_chunks` + 重建接口，
   用假向量模型做单测），之后 T-AI2 语义人才检索、T-AI3 HR 简历问答（带引用）。
2. **可观测性可选深化（T-OBS3）**：控制台核对慢 SQL / 异常分析 / JVM 大盘；前端监控（Web SDK）
   建议最后做且加开关（会碰 e2e「零控制台错误」门禁）。
3. **待用户拍板**（不替用户决定）：`docs/step4-ai-plan.md` §10 五项（向量维度 1024/1536、是否接 Ollama、
   配额策略、是否含求职者侧、Boot 4 + Spring AI 2.0 迁移时机）；求职者端分类字典 / 消息会话是否切真实；
   HR「发消息」入口是否补齐。

---

## 许可

私有项目，未经授权请勿分发。
