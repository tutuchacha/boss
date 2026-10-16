<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# 聘聊 · 开发约束与规范（AGENTS.md）

> 本文件是给在本仓库工作的 AI Agent（以及人）的**硬性约束与操作规范**。
> 开工前按此顺序读：`PROGRESS.md`（当前状态与下一步）→ `ARCHITECTURE.md`（结构）→
> `PROJECT.md`（范围）→ `docs/AI-CONTINUE.md`（命令、坑、规范深读）→ `worklog.md`（历史）。
> 上方 `nextjs-agent-rules` 区块由 `next dev` 自动维护，勿删；允许在其后追加内容。

## 0. 项目速览

聘聊 = Boss 直聘模式招聘平台原型（求职者端 + HR 端双端 H5），Next.js 16 + Tailwind 4 + Zustand +
TanStack Query，数据全部为前端 Mock。当前处于 **Step 2 前端验收**：五项验收功能（投递 / 候选人 /
面试、验证码登录 / 注册、公司资料 / 企业认证）已完成，并新增**人才筛选（演示版）**
增量，`npm run e2e` **63/63** 通过；用户人工验收初步通过（2026-10-16），Step 3 后端未开始。
演示账号：`13800000001`（求职者）/ `13800000002`（HR），密码均 `123456`；短信验证码固定 `123456`。

## 1. 运行与验收（统一用 npm，不用 bun）

```powershell
npm run dev          # dev server，端口 3000（e2e 需要它常驻）
npm run e2e          # 端到端验收（tests/e2e-verify.cjs）；基线 63/63
npx tsc --noEmit     # 类型检查；基线仅 3 个既有错误（见 §3）
npm run lint         # ESLint
npm run build        # 生产构建（需能访问 Google Fonts）
```

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

## 3. 已知既有报错（不要试图修）

- `npx tsc --noEmit` 3 个：`examples/websocket/*` 缺 socket.io 依赖、`src/lib/db.ts` 缺 Prisma client。
- `npx eslint src` 有 `src/components/ui/*` 与 `src/hooks/use-mobile.ts` 的存量告警。
- `favicon.ico` 404、`next-themes` script 告警均无害。

## 4. 文档维护义务

- 有实质进展后更新 `PROGRESS.md`（状态唯一定稿）并在 `worklog.md` 追加条目（Task ID 递增）。
- 完成增量后更新 `CHANGELOG.md`；契约变化同步 `docs/api-endpoints.md`；踩坑后同步
  `docs/AI-CONTINUE.md` §6。
- 提交信息用中文，说明「改了什么 + 为什么」；提交时包含 `AGENTS.md` / `CLAUDE.md`
  以保持工作区干净（该区块由 `next dev` 自动重新生成）。
- 不要为整理文档而重构业务代码；不要提交 `.env` 与本机私有产物。
- 待用户确认的事项（前端最终框架、接口账目口径等）**不要替用户决定**，
  清单见 `docs/AI-CONTINUE.md` §9。
