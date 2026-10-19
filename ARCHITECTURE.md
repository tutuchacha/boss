# 聘聊 · 架构说明（ARCHITECTURE.md）

> **本文件描述「当前代码实际长什么样」**：技术栈、目录结构、模块关系与关键机制。
> 与 [`docs/architecture.md`](./docs/architecture.md) 的分工：那份是 Step 0 产出的
> **框架无关目标架构**（未来 Spring Boot 后端 + 多租户方案），本文件是**验收版现状**。
> 两者冲突时，本文件描述现状、`docs/architecture.md` 描述目标。
>
> 最后更新：2026-10-19 · 前端基线 `npm run e2e` 63/63；Step 3 后端 T0~T9 已落地并完成全量联调（见 §7）

---

## 1. 技术栈

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 框架 | Next.js 16（App Router + Turbopack） | 当前仅作沙箱验收；最终上线框架未定 |
| 语言 | TypeScript 5 | |
| UI | Tailwind CSS 4 + shadcn/ui | 青绿主题（`--primary: #00857F`）；`src/components/ui/` 为生成代码，勿改 |
| 客户端状态 | Zustand（含 `persist`） | `pinliao` / `auth` / `hr` 三个 store |
| 服务端状态 | TanStack Query | 两端新模块均已使用；求职者端列表筛选仍走同步 Mock |
| 图标 / 工具 | lucide-react / clsx / tailwind-merge | |
| ORM | Prisma | **未接入业务**，仅作为领域模型设计文档（见 §6.3） |

> `zod`、`react-hook-form` 等随模板装好但**代码零引用**。

---

## 2. 运行时总览

```
src/app/page.tsx                    ← 唯一可见路由（单页 SPA）
   └─ <RootGate/>                   ← 水合守卫 + 按登录状态/角色分发
        ├─ <LoginPage/>              未登录
        ├─ <AppShell/>               求职者端（JOB_SEEKER）
        │     └─ 根视图（tab：职位/消息/我的）+ 页面栈覆盖层
        │        JobsView / JobDetailPage / MyApplicationsPage（我的投递）/ ChatPage /
        │        MessagesView / MeView / …
        └─ <HrShell/>                HR 工作台（HR）
              └─ 根视图（tab：职位管理/候选人/沟通/我的）+ 页面栈覆盖层
                 HrJobsPage / HrJobEditorPage / HrCandidatesPage / HrCandidateDetailPage /
                 HrInterviewInvitePage / HrTalentMatchPage（人才筛选）/ HrMessagesPage / HrChatPage /
                 HrCompanyEditPage / HrCompanyVerifyPage / HrMePage / …

状态层（Zustand）
   pinliao.ts   求职者端（持久化键 pinliao.v3；tab / pageStack / filters / favorites / 会话 / 资料 …）
   auth.ts      认证（持久化键 pinliao.auth.v1；user / profiles / company / member）
   hr.ts        HR 工作台（不持久化；tab / pageStack / 职位筛选 / 表单草稿 / dataVersion）

service 层（src/api/*.ts）—— 组件只依赖这一层，不直接 fetch
   client.ts      统一 fetch + 响应包络解包 + ApiError；USE_MOCK 分流
   auth.ts        密码 / 验证码登录、注册、退出
   jobs.ts        求职者视角职位；hrJobs.ts  HR 职位管理 + 表单校验
   conversations.ts / hrConversations.ts   两端会话
   applications.ts / hrApplications.ts     两端投递与面试（双端 UI 已接，e2e 覆盖）
   company.ts     公司资料 / 企业认证（Mock 提交后约 1.2s 自动通过）
   hrTalentMatch.ts  人才筛选（§4.5 / §4.6）：候选人匹配 + 技能标签词表

Mock 数据层（src/api/mock/*.ts）—— USE_MOCK=true 时被 service 直读
   bootstrap.ts（装配）· jobs.ts（51 条基础职位）· hrJobs.ts（HR 可变职位）
   seekerJobs.ts（求职者可见职位合成）· companies.ts（公司字典）· companyOps.ts（资料 / 认证）
   auth.ts（账号 / 画像 / 公司绑定）· dict / categories / filter（字典与筛选）
   hrConversations.ts（HR 会话）· candidates.ts（候选人种子，跨模块共享）
   resumes.ts（简历正文种子，人才筛选数据源）· talentMatch.ts（关键词匹配与证据提取）
   applications.ts（投递 / 面试 可变数据）
```

数据流（以 HR 约面试为例）：
页面事件 → `src/api/hrApplications.ts`（`if (USE_MOCK)` 读 mock / 否则 `request()`）
→ `src/api/mock/applications.ts` 改内存数组 → TanStack Query 失效重取 → 页面刷新。

---

## 3. 目录结构（当前实况）

```
src/
├─ app/                      Next.js 入口（单页 SPA）
│  ├─ page.tsx               渲染 RootGate
│  ├─ layout.tsx             根布局（主题、字体、Query Provider）
│  └─ globals.css            主题 token（--pl-* / --primary）
├─ components/
│  ├─ pinliao/               业务组件
│  │  ├─ RootGate.tsx        登录分发（顶部注释记录「不引入路由」决策）
│  │  ├─ LoginPage.tsx       登录页（密码 / 验证码 / 注册三模式 + 演示账号一键填入）
│  │  ├─ AppShell.tsx        求职者端外壳（tab + pageStack）
│  │  ├─ JobsView / JobDetailPage / MyApplicationsPage（我的投递）/ ChatPage /
│  │  │  MessagesView / MeView / FavoritesPage / ResumePage / …
│  │  ├─ BottomSheet / CitySheet / FilterSheet / WishSheet  弹层
│  │  └─ hr/                 HR 工作台（430px 居中）
│  │     ├─ HrShell.tsx      外壳（四 Tab：职位管理 / 候选人 / 沟通 / 我的）
│  │     ├─ HrJobsPage.tsx   / HrJobEditorPage.tsx
│  │     ├─ HrCandidatesPage.tsx / HrCandidateDetailPage.tsx / HrInterviewInvitePage.tsx
│  │     ├─ HrTalentMatchPage.tsx  人才筛选（关键词匹配 + 命中证据高亮）
│  │     ├─ HrMessagesPage.tsx / HrChatPage.tsx
│  │     ├─ HrCompanyEditPage.tsx / HrCompanyVerifyPage.tsx
│  │     └─ HrMePage.tsx
│  └─ ui/                    shadcn/ui 基础组件（勿手改）
├─ api/                      service 层
│  ├─ client.ts              统一 fetch + 包络解包 + ApiError
│  ├─ auth.ts / jobs.ts / hrJobs.ts / hrConversations.ts / conversations.ts / categories.ts
│  ├─ applications.ts        求职者投递 / 面试 service（投递闭环）
│  ├─ hrApplications.ts      HR 候选人 / 投递 / 面试 service
│  ├─ hrTalentMatch.ts       人才筛选 service（候选人匹配 / 标签词表）
│  ├─ company.ts             公司资料 / 企业认证 service
│  ├─ index.ts               出口（当前无人使用，组件习惯直接按模块导入）
│  └─ mock/                  Mock 数据与装配
│     ├─ bootstrap.ts        打破 jobs ↔ hrJobs 循环依赖的装配点
│     ├─ jobs.ts / hrJobs.ts / seekerJobs.ts / companies.ts / companyOps.ts
│     ├─ auth.ts / dict.ts / categories.ts / filter.ts / conversations.ts / hrConversations.ts
│     ├─ candidates.ts       候选人种子（hrConversations 与 applications 共用）
│     ├─ resumes.ts          简历正文种子（人才筛选检索的数据源）
│     ├─ applications.ts     投递 / 面试可变数据
│     └─ talentMatch.ts      人才筛选：关键词匹配与证据提取（演示版，无 AI）
├─ store/
│  ├─ pinliao.ts             求职者端（持久化 pinliao.v3）
│  ├─ auth.ts                认证（持久化 pinliao.auth.v1）
│  └─ hr.ts                  HR 工作台（不持久化）
├─ hooks/useStoreHydration.ts  水合守卫
├─ lib/                      工具（time.ts / utils.ts / db.ts=未使用）
├─ types/api.ts              与契约同步的类型定义（新增功能先查这里）
└─ config/env.ts             环境变量（USE_MOCK / API_BASE_URL / WS_BASE_URL / WS_PORT）
```

---

## 4. 模块关系与关键机制

### 4.1 单页 SPA：tab + pageStack 导航

- `src/app/page.tsx` 是唯一可见路由，只渲染 `RootGate`。
- 两端外壳（`AppShell` / `HrShell`）各自从 store 读取 `tab`（根视图）与 `pageStack`（覆盖层）。
- 覆盖层页面（详情 / 聊天 / 编辑器等）以绝对定位叠在根视图上，底部 Tab 在覆盖态隐藏。
- **禁止引入 `next/navigation` 做路由** —— 会改变已验收架构并造成白屏风险。

### 4.2 service 层与 Mock 切换

- 组件只 import `src/api/*` 模块（现有习惯是直接按模块导入，`src/api/index.ts` 桶当前无人使用）。
- 每个 service 函数形如：
  ```ts
  if (USE_MOCK) { /* 读写 src/api/mock 内存数据 */ }
  return request<T>(path, { method, body })   // 联调路径
  ```
- 新增功能一律照 `src/api/hrJobs.ts` 的写法；接口以 `docs/api-endpoints.md` 与
  `src/types/api.ts` 为准，不要自造。

### 4.3 Mock 数据模块图（关注依赖方向）

```
              bootstrap.ts
             ╱            ╲
      initHrJobs(jobs)      （仅此一处装配调用）
        ↑                    ↑
   hrJobs.ts ←─────────── jobs.ts ──► seekerJobs.ts（合成「求职者可见职位」）
        │                    │
        └── companies.ts ────┘          auth.ts（账号/画像/公司绑定）
                                          │
   candidates.ts（纯种子，被两处共用）      │
        ├── hrConversations.ts ───────────┤
        └── applications.ts ──────────────┘（还依赖 jobs / hrJobs / hrConversations）
```

- **循环依赖已解决**：`jobs.ts` 与 `hrJobs.ts` 不互相 import，由 `bootstrap.ts`
  按序装配（`src/api/jobs.ts` 顶部触发）。新增数据模块时沿用「纯数据 + 单向依赖」原则。
- **合成层**：求职者取数必须走 `listSeekerJobs()` / `jobById()`（含 HR 关闭/删除/新建的联动），
  不要直接读静态 `jobs` 数组。
- **候选人种子**：`mock/candidates.ts` 被 HR 会话（`hrConversations`）与投递（`applications`）
  共用，保证「沟通」与「候选人管理」两个页面的人物一致。
- **公司资料 / 认证**：`mock/companyOps.ts` 原地修改 `companies.ts` 的公司记录，并把
  名称 / 阶段 / 规模同步到 `jobs.ts` / `hrJobs.ts` 里的公司快照，否则双端仍显示旧公司名。

### 4.4 认证与角色分流

- 登录：`LoginPage`（密码 / 验证码 / 注册三模式）→ `useAuthStore` 的
  `login / loginSms / register` → `src/api/auth.ts`（Mock 校验 `mock/auth.ts` 的账号表与
  固定验证码 `123456`；注册写内存注册表，HR 注册自动创建未认证新公司；写 token 到 localStorage）
  → store 写入 `user / profile / company / member` → `RootGate` 按角色渲染对应外壳。
- 登录成功统一重置双端落地页签（`setTab('jobs')`），避免落回上次停留页签
  （`docs/AI-CONTINUE.md` §6.7）。
- **身份校验必须在写登录态之前**（不符时抛 `RoleMismatchError`，登录页可提示切换身份）——
  详见 `docs/AI-CONTINUE.md` §6.4。
- token 键：`pinliao.accessToken` / `pinliao.refreshToken`；auth store 只持久化身份信息。

### 4.5 状态归属（哪个 store 管什么）

| store | 内容 | 持久化 |
| --- | --- | --- |
| `pinliao` | 求职者端 tab / pageStack / 筛选 / 收藏 / 会话 / 资料 / toast | `pinliao.v3` |
| `auth` | 当前用户 / 两种画像 / 公司 / 成员 / pending | `pinliao.auth.v1` |
| `hr` | HR tab / pageStack / 职位筛选 / 表单草稿 / dataVersion / toast | 不持久化 |

约定：**选择器只返回稳定引用或原始值**（需要派生对象时在组件内 `useMemo` 组装），
否则会触发无限重渲染（`docs/AI-CONTINUE.md` §6.2）。

### 4.6 水合安全与数据渲染边界

- 所有 persist store `skipHydration: true`，`RootGate` 挂载后统一 `rehydrate()`。
- 不在模块初始化阶段读 localStorage / `Date.now()` 参与首屏渲染；Mock 时间种子用固定基准。
- 登录后的业务数据只在客户端路径渲染（首屏服务端与客户端 HTML 一致，避免 hydration 报错）。
- **新建 id 用模块内递增计数**，禁止 `Date.now()` / `Math.random()`（SSR/CSR 不一致）。

### 4.7 取数与刷新（React Compiler 约束）

- 本项目开启 React Compiler，`react-hooks/set-state-in-effect` 为 error：
  **不要手写 `useEffect + setState` 取数**，统一用 TanStack Query
  （`queryKey` + `queryFn`，变更后用 `invalidateQueries` 刷新）。
- 「取数 + 表单回填」拆成两个组件（取数壳 + 表单体），避免同组件取数与 setState 并存。

---

## 5. 五条必须遵守的架构约定（与 AI-CONTINUE §3 同步）

1. **单页 SPA，不引入路由**：一切视图切换走各自 store 的 `tab` + `pageStack`。
2. **service 层隔离**：组件不直接 `fetch`；`USE_MOCK` 控制 Mock / 真实接口切换。
3. **前端最终选型未定**：Next.js 当前只是验收沙箱；目标架构见 `docs/architecture.md`。
4. **多租户**：业务表带 `companyId`；生产由 MyBatis-Plus 租户拦截器落实（MySQL 8；
   求职者跨租户查询不注入，详情见 `docs/AI-CONTINUE.md` §6.9）。
5. **水合安全**：见 §4.6，禁止在模块初始化阶段读取浏览器私有状态。

---

## 6. 已知架构限制（当前验收版的现实，不是 Bug）

### 6.1 Mock 数据在内存

所有可变数据（职位 / 会话 / 投递 / 面试）存于模块级数组，**刷新即重置**；
跨端联动必须用应用内退出登录切换账号，不能刷新页面。

### 6.2 HR 新建职位归属（已于本轮修复，勿回退）

`src/api/mock/hrJobs.ts` 的 `toRecord()` 已改为按**当前登录上下文**注入公司 / HR ——
注册新公司的 HR 发布的职位挂在自己公司名下，多租户演示不穿帮。
改动前写死 `MOCK_COMPANY`（云杉科技）与 `MOCK_HR_PROFILE`，回归时以此为鉴。

### 6.3 Prisma 只是设计文档

`prisma/schema.prisma`（19 模型 + 10 枚举）不参与运行：
无人 `import src/lib/db.ts`，`prisma generate` 非启动前置；生产由后端另维护建表。

### 6.4 求职者端取数方式混用

`JobsView` 走同步 `filterJobsSync()`（`mock/filter.ts`），`JobDetailPage` 走异步
`getJobDetail()`，投递 / 面试相关页面（`MyApplicationsPage` 等）走 TanStack Query；
HR 端统一 Query。新增求职者端页面时优先 Query，不要扩大同步 Mock 的适用范围
（参照 `docs/AI-CONTINUE.md` §7.5 对照表）。

### 6.5 人才筛选为关键词匹配（演示版，非语义检索）

`hrTalentMatch` 演示版在「公司收到的投递候选人」范围内做**关键词匹配**：
标签 / 需求文本 → 词典与分词提取关键词 → 检索在线简历（`mock/resumes.ts`），
命中项返回证据片段。同义词不会被识别（如「React」与「前端框架」），词典命中
为包含式匹配（演示数据已规避短词误报，如无 JavaScript 技能项避免被「Java」命中）。
升级为语义检索（embedding + 向量库）时替换 `mock/talentMatch.ts` 与服务端实现即可，
接口（`docs/api-endpoints.md` §4.5 / §4.6）与页面不变。

---

## 7. 后端实现（Step 3，`backend/`）

> 2026-10-19（T9）起前端可切 `NEXT_PUBLIC_USE_MOCK=false` 直连该后端：
> `npm run e2e` 在**真实后端**下 **63/63**（连续两轮，每轮前先清库重迁）。
> 目标架构（多租户 / 分库演进）见 [`docs/architecture.md`](./docs/architecture.md)，本节只描述现状。

### 7.1 技术栈与本地运行

- Spring Boot **3.5.16**（Java **21**，用 IDEA 自带 JBR；本机系统 JDK 1.8 不可用）+ MyBatis-Plus
  （分页插件 / 租户插件 / created·updated 自动填充）+ MySQL 8.0.29（**项目专用实例 3307**）+
  Redis **6379** + Flyway（`V1`~`V8`）+ springdoc（`/v3/api-docs`）。
- 运行数据都在 `.local/`（gitignore）：`backend/scripts/dev-up.ps1` / `dev-down.ps1` 起停 MySQL + Redis；
  `db-reset.ps1` 清库重迁（drop + create → Redis FLUSHALL → 起后端 → 等 health）；后端 `:8080`。
- 统一响应包络 `{code, message, data, traceId}`；错误码 5 位、前 3 位对应 HTTP 状态
  （如 `40110` → 401、`40410` → 404、`42201` → 422）。

### 7.2 包结构（`com.pinliao.backend`）

```
auth/        登录（密码 / 短信）、注册、刷新、退出、me；JWT + Redis refresh 白名单
user/        §2 画像 / 密码 / 手机号 / 偏好（Redis）/ 注销
resume/      §3 简历 CRUD / 默认简历
job/         §6 职位公开读（列表 / 搜索 / 详情 / 相似 / 埋点）+ HR 职位写 + §4.3 我发布的职位
dict/        §7 分类树 / 城市 / 字典（由 mock 脚本生成种子）
application/ §9 投递 + §10 面试 + §8.7 约面试（状态机 / 归属隔离）
company/     §5 公司公开信息 / 我的公司 / 资料编辑 / 成员 / 企业认证（异步自动通过）
message/     §8 会话 / 消息 / 未读 + WebSocket（STOMP）+ 自动回复
recruiter/   §4.1/§4.2 HR 画像 + §4.4 我收到的投递 + §4.5/§4.6 人才筛选（关键词引擎）
favorite/    §11 收藏；upload/ §12 预签名上传
common/      api（包络 / 错误码 / 分页）/ 租户 / 安全 / 异常
config/      拦截器 / MyBatis-Plus / Redis / WebSocket / 上传目录等装配
demo/        `_demo` 调试接口（保留）
```

### 7.3 多租户与数据边界

- 表都带 `company_id` 租户列；MyBatis-Plus `TenantLineInnerInterceptor` 对 HR 端查询注入
  `company_id`，求职者侧（跨租户）不注入 → **查询必须显式过滤 `user_id`**。
- 少数表**不在**注入集合内（如 `hr_profiles` 按 `user_id` 唯一、`flyway_schema_history`）：
  写这类查询前先读 `docs/AI-CONTINUE.md` §6.9 / §6.17。
- 归属隔离统一 `40410`（不存在 / 非本人 / 非本公司，不泄露存在性）；角色不符才是 `40301`。
- 新记录 id 一律**递增派生**（`job-new-{seq}` / `app-new-{seq}` / `int-new-{seq}` / `conv-new-{seq}` /
  `msg-new-{seq}`），序号查询需 `@InterceptorIgnore(tenantLine="true")`。

### 7.4 鉴权与会话

- 登录返回 `accessToken`（JWT HS256，2h）+ `refreshToken`（Redis 白名单，30d，一次性轮换）。
- 拦截器默认保护 `/api/v1/**`；白名单：`auth/sms|login|refresh|register`、`_demo`、公开只读路径
  （职位 / 公司 / 分类字典，带 token 时仍解析身份用于推荐加权）。
- 演示语义：验证码固定 `123456`（同手机号 60s 限流）；HR 注册自动建未认证公司
  `新注册企业-{手机尾4}` + ADMIN 成员。

### 7.5 消息与 WebSocket

- 会话 / 消息落库（`conversations` / `messages`），未读计数以数据库为事实源。
- WebSocket 端点 `/ws`（STOMP，握手带 `token`）：`/user/queue/messages`、`/user/queue/typing`、
  `/topic/conversation/{id}/typing`、错误 `/user/queue/errors`。
- TEXT 双向自动回复延迟约 **1.2s**（`pinliao.message.auto-reply-delay-ms`）。
  ⚠️ **前端当前不建立 WS 连接**（T9 结论）：HR 聊天页用「定时重取」兜住自动回复。

### 7.6 上传

- 预签名：`POST /uploads/sign` → HMAC 签名 `PUT /api/v1/uploads/local/{objectKey}` → 确认后返回
  `/uploads/{objectKey}`；默认目录 `backend/target/uploads`（`PINLIAO_UPLOAD_DIR` 可覆盖）。
- 前端**不需要**拼绝对地址：`next.config.ts` 已把 `/uploads/**` 代理到后端。

### 7.7 前后端对接（T9 结论）

- `next.config.ts` rewrites：`/api/v1/:path*` 与 `/uploads/:path*` → `API_PROXY_TARGET`
  （默认 `http://localhost:8080`）。前端 `src/api/client.ts` 只发相对路径，故**不需要**绝对 URL / CORS。
- 开关在 `.env.local`（gitignore）：`NEXT_PUBLIC_USE_MOCK=false` + `NEXT_PUBLIC_API_BASE_URL=/api/v1`；
  改后必须重启 dev server（`NEXT_PUBLIC_*` 构建期内联）。
- 列表统一 `{list, pagination:{nextCursor, hasMore, total}}`；时间统一 ISO-8601 UTC；枚举用枚举名字符串。
- 验收：`mvnw.cmd test` **173/173**；冒烟 `smoke-auth` 20 / `smoke-jobs` 49 / `smoke-applications` 51 /
  `smoke-company` 26 / `smoke-messages` 40 / `smoke-user` 64 / `smoke-recruiter` 50；`verify-seed` 16/16；
  前端 `npm run e2e` **63/63**。
- 仍是 mock 驱动的前端路径（求职者职位列表 / 分类字典 / 消息会话）：见 `PROGRESS.md` §6.3。

---

## 8. 修订记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0 | 2026-10-16 | 首版：按工作区实况记录技术栈、运行时结构、目录、模块关系、约定与已知限制 |
| v1.1 | 2026-10-16 | 五项验收功能完成：补录候选人 / 投递 / 面试 / 验证码 / 公司资料认证相关页面与模块 |
| v1.2 | 2026-10-16 | 人才筛选（演示版）增量：运行时总览 / 目录 / Mock 模块图接入；§6.5 记录关键词匹配边界与语义升级路径 |
| v1.3 | 2026-10-19 | 新增 §7「后端实现（Step 3，`backend/`）」：技术栈与本地运行、包结构、多租户与数据边界、鉴权与会话、WebSocket、上传、T9 前后端对接结论（Next rewrites / 开关 / 出参口径 / 验收数字）；修订记录改为 §8 |
