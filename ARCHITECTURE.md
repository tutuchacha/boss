# 聘聊 · 架构说明（ARCHITECTURE.md）

> **本文件描述「当前代码实际长什么样」**：技术栈、目录结构、模块关系与关键机制。
> 与 [`docs/architecture.md`](./docs/architecture.md) 的分工：那份是 Step 0 产出的
> **框架无关目标架构**（未来 Spring Boot 后端 + 多租户方案），本文件是**验收版现状**。
> 两者冲突时，本文件描述现状、`docs/architecture.md` 描述目标。
>
> 最后更新：2026-10-16 · 对应基线 `592cc78` + 工作区未提交改动（见 `PROGRESS.md`）

---

## 1. 技术栈

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 框架 | Next.js 16（App Router + Turbopack） | 当前仅作沙箱验收；最终上线框架未定 |
| 语言 | TypeScript 5 | |
| UI | Tailwind CSS 4 + shadcn/ui | 青绿主题（`--primary: #00857F`）；`src/components/ui/` 为生成代码，勿改 |
| 客户端状态 | Zustand（含 `persist`） | `pinliao` / `auth` / `hr` 三个 store |
| 服务端状态 | TanStack Query | HR 端已使用；求职者端部分仍走同步 Mock |
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
        │        JobsView / JobDetailPage / ChatPage / MessagesView / MeView / …
        └─ <HrShell/>                HR 工作台（HR）
              └─ 根视图（tab：职位管理/沟通/我的）+ 页面栈覆盖层
                 HrJobsPage / HrJobEditorPage / HrMessagesPage / HrChatPage / HrMePage / …

状态层（Zustand）
   pinliao.ts   求职者端（持久化键 pinliao.v3；tab / pageStack / filters / favorites / 会话 / 资料 …）
   auth.ts      认证（持久化键 pinliao.auth.v1；user / profiles / company / member）
   hr.ts        HR 工作台（不持久化；tab / pageStack / 职位筛选 / 表单草稿 / dataVersion）

service 层（src/api/*.ts）—— 组件只依赖这一层，不直接 fetch
   client.ts      统一 fetch + 响应包络解包 + ApiError；USE_MOCK 分流
   auth.ts        登录 / 退出 /（规划中）验证码 / 注册
   jobs.ts        求职者视角职位；hrJobs.ts  HR 职位管理 + 表单校验
   conversations.ts / hrConversations.ts   两端会话
   applications.ts / hrApplications.ts     两端投递与面试（工作区新增，未接 UI）

Mock 数据层（src/api/mock/*.ts）—— USE_MOCK=true 时被 service 直读
   bootstrap.ts（装配）· jobs.ts（51 条基础职位）· hrJobs.ts（HR 可变职位）
   seekerJobs.ts（求职者可见职位合成）· companies.ts（公司字典）
   auth.ts（账号 / 画像 / 公司绑定）· dict / categories / filter（字典与筛选）
   hrConversations.ts（HR 会话）· candidates.ts（候选人种子，跨模块共享）
   applications.ts（投递 / 面试 可变数据，工作区新增）
```

数据流（以 HR 约面试为例，目标形态）：
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
│  │  ├─ LoginPage.tsx       登录页（身份选择 + 演示账号一键填入）
│  │  ├─ AppShell.tsx        求职者端外壳（tab + pageStack）
│  │  ├─ JobsView / JobDetailPage / ChatPage / MessagesView / MeView / FavoritesPage / ResumePage / …
│  │  ├─ BottomSheet / CitySheet / FilterSheet / WishSheet  弹层
│  │  └─ hr/                 HR 工作台（430px 居中）
│  │     ├─ HrShell.tsx      外壳（当前三 Tab：职位管理 / 沟通 / 我的）
│  │     ├─ HrJobsPage.tsx   / HrJobEditorPage.tsx
│  │     ├─ HrMessagesPage.tsx / HrChatPage.tsx
│  │     └─ HrMePage.tsx
│  └─ ui/                    shadcn/ui 基础组件（勿手改）
├─ api/                      service 层
│  ├─ client.ts              统一 fetch + 包络解包 + ApiError
│  ├─ auth.ts / jobs.ts / hrJobs.ts / hrConversations.ts / conversations.ts / categories.ts
│  ├─ applications.ts        ★ 求职者投递/面试 service（工作区新增，未接 UI）
│  ├─ hrApplications.ts      ★ HR 投递/面试 service（工作区新增，未接 UI）
│  ├─ index.ts               出口（当前无人使用，组件习惯直接按模块导入）
│  └─ mock/                  Mock 数据与装配
│     ├─ bootstrap.ts        打破 jobs ↔ hrJobs 循环依赖的装配点
│     ├─ jobs.ts / hrJobs.ts / seekerJobs.ts / companies.ts
│     ├─ auth.ts / dict.ts / categories.ts / filter.ts / conversations.ts / hrConversations.ts
│     ├─ candidates.ts       ★ 候选人种子（工作区新增；hrConversations 与 applications 共用）
│     └─ applications.ts     ★ 投递/面试可变数据（工作区新增）
├─ store/
│  ├─ pinliao.ts             求职者端（持久化 pinliao.v3）
│  ├─ auth.ts                认证（持久化 pinliao.auth.v1）
│  └─ hr.ts                  HR 工作台（不持久化）
├─ hooks/useStoreHydration.ts  水合守卫
├─ lib/                      工具（time.ts / utils.ts / db.ts=未使用）
├─ types/api.ts              与契约同步的类型定义（新增功能先查这里）
└─ config/env.ts             环境变量（USE_MOCK / API_BASE_URL / WS_BASE_URL / WS_PORT）
```

★ = 本次未提交的在建文件，详见 `PROGRESS.md` §3。

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
        └── applications.ts ──────────────┘（新增；还依赖 jobs / hrJobs / hrConversations）
```

- **循环依赖已解决**：`jobs.ts` 与 `hrJobs.ts` 不互相 import，由 `bootstrap.ts`
  按序装配（`src/api/jobs.ts` 顶部触发）。新增数据模块时沿用「纯数据 + 单向依赖」原则。
- **合成层**：求职者取数必须走 `listSeekerJobs()` / `jobById()`（含 HR 关闭/删除/新建的联动），
  不要直接读静态 `jobs` 数组。
- **候选人种子**：`mock/candidates.ts` 被 HR 会话（`hrConversations`）与投递（`applications`）
  共用，保证「沟通」与「候选人管理」两个页面的人物一致。

### 4.4 认证与角色分流

- 登录：`LoginPage` → `useAuthStore.login(account, password, expectRole)`
  → `src/api/auth.ts`（Mock 校验 `mock/auth.ts` 的账号表，写 token 到 localStorage）
  → store 写入 `user / profile / company / member` → `RootGate` 按角色渲染对应外壳。
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
4. **多租户**：业务表带 `companyId`；生产由 MyBatis-Plus 拦截器 + PostgreSQL RLS 落实。
5. **水合安全**：见 §4.6，禁止在模块初始化阶段读取浏览器私有状态。

---

## 6. 已知架构限制（当前验收版的现实，不是 Bug）

### 6.1 Mock 数据在内存

所有可变数据（职位 / 会话 / 投递 / 面试）存于模块级数组，**刷新即重置**；
跨端联动必须用应用内退出登录切换账号，不能刷新页面。

### 6.2 HR 新建职位固定挂在演示公司

`src/api/mock/hrJobs.ts` 的 `toRecord()` 目前写死 `MOCK_COMPANY`（云杉科技）与
`MOCK_HR_PROFILE`。实现「注册新公司 HR 发布职位」时必须一并改造为按当前登录
公司 / HR 注入，否则多租户演示会穿帮（登记在 `PROGRESS.md` §6.2 风险表）。

### 6.3 Prisma 只是设计文档

`prisma/schema.prisma`（19 模型 + 10 枚举）不参与运行：
无人 `import src/lib/db.ts`，`prisma generate` 非启动前置；生产由后端另维护建表。

### 6.4 求职者端取数方式混用

`JobsView` 走同步 `filterJobsSync()`（`mock/filter.ts`），`JobDetailPage` 走异步
`getJobDetail()`；HR 端统一 TanStack Query。新增求职者端页面时优先 Query，
不要扩大同步 Mock 的适用范围（参照 `docs/AI-CONTINUE.md` §7.5 对照表）。

---

## 7. 修订记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0 | 2026-10-16 | 首版：按工作区实况记录技术栈、运行时结构、目录、模块关系、约定与已知限制 |
