# 聘聊 · 进度与交接（PROGRESS.md）

> **本文件是「当前状态」的唯一定稿**：已完成 / 进行中 / 下一步 / 已知问题 / 命令与测试结果。
> 与其他文档冲突时，状态以本文件为准（并顺手修正对方）。
> 项目目标见 [`PROJECT.md`](./PROJECT.md)；技术结构见 [`ARCHITECTURE.md`](./ARCHITECTURE.md)；
> 运维与踩坑深读见 [`docs/AI-CONTINUE.md`](./docs/AI-CONTINUE.md)。
>
> 最后更新：2026-10-18

---

## 0. 快照信息（开工前先核对）

| 项 | 值 |
| --- | --- |
| 上一提交（历史基线） | `6295ee1`（docs: 结算 T3a 提交 hash（9f509be）并同步任务板与快照） |
| 本轮提交 | `8102f0b`（feat(backend): Step 3 T3b —— HR 职位写接口（§6.5~§6.8））+ 紧后 docs 结算提交；若 `git status` 仍有未提交文件，以工作区现状为准 |
| dev server（前端） | 端口 3000；未运行时用 `npm run dev` 启动（本会话未复测） |
| 后端（Step 3） | `backend/`（Spring Boot 3.5.16，:8080）；MySQL 专用实例 **3307** + Redis **6379** 运行中，**后端 :8080 本会话保持运行**（停止按端口 8080 定位 PID 精确处理，勿按名杀 java）；起停脚本 `backend/scripts/` |
| 测试账号 | 求职者 `13800000001` / HR `13800000002`，密码均 `123456`；短信验证码固定 `123456` |
| 环境 | Windows（`D:\boss`）+ PowerShell；Node 实测 v24 可跑；**无 bun，统一用 npm**；后端 JDK 用 IDEA 自带 **JBR 21**（跑 `mvnw` 必设 `JAVA_HOME`，见 §7.2） |
| 远端仓库 | `origin` → GitHub `tutuchacha/boss`（SSH）；接手前先 `git pull` 与远端对齐 |
| Step 3 计划 | [`docs/step3-plan.md`](./docs/step3-plan.md)（分块 T0~T9 + 上下文交接协议）；**T0 ✅、T2a ✅（2026-10-17 登录/退出先行）、T1 ✅（2026-10-18 全量表 + 演示种子）、T2b ✅（2026-10-18 注册 §1.2）、T3a ✅（2026-10-18 职位公开读 + 分类字典 §6.1~§6.4/§6.9 + §7）、T3b ✅（2026-10-18 HR 职位写 §6.5~§6.8）**，当前：**T4 投递与面试闭环（§9/§10）** |

> 接手时先跑一次 `git status --porcelain` 与 `npx tsc --noEmit` 核对本表是否仍然成立。

---

## 1. 总体进度

| 阶段 | 状态 | 证据 / 产物 |
| --- | --- | --- |
| Step 0 架构 + 契约 | ✅ 完成 | `docs/architecture.md`、`data-model.md`、`api-*.md`、`prisma/schema.prisma` |
| Step 1 双端前端开发 | ✅ 完成 | `src/components/pinliao/`（求职者端 + HR 端 + 登录） |
| Step 2 前端验收 | ⏳ 进行中 | 验收五项功能已全部完成，并新增**人才筛选（演示版）**增量；`npm run e2e` 63/63 通过（见 §2/§5）；**用户人工验收初步通过（2026-10-16）**；待确认 `docs/AI-CONTINUE.md` §9 待决事项 |
| Step 3 后端（Spring Boot） | 🔄 进行中（T3b ✅ → T4） | `backend/` Spring Boot 3.5.16 骨架 + MySQL 专用实例 3307 / Redis 6379；**T0 ✅、T2a 鉴权 ✅（2026-10-17）、T1 数据库迁移与演示种子 ✅、T2b 注册 ✅、T3a 职位公开读 + 分类字典 ✅、T3b HR 职位写 ✅（2026-10-18，实测见 §7.2）**；下一步 T4 投递与面试闭环（§9/§10） |
| Step 4 联调 | ⏸ 未开始 | `NEXT_PUBLIC_USE_MOCK=false` |

---

## 2. 已完成并验收（本轮提交）

**求职者端**：职位列表（城市 / 搜索 / 分类联动 / 筛选 / 排序）、职位详情（含**投递简历**）、
收藏、简历预览、求职期望编辑、**我的投递**（进度查看 / 两步撤回 / 面试邀请接受与婉拒）、
消息会话、聊天页（快捷回复 + 关键词自动回复）、外观切换、**密码 / 验证码登录、注册**、
退出登录（身份不匹配拦截）。

**HR 端**（同一移动端布局）：职位管理（发布 / 编辑 / 关闭 / 重开 / 删除、状态筛选、
搜索、数据概览）、**候选人管理**（状态筛选、详情、标记已查看 / 录用 / 不合适、
发起面试邀请）、候选人沟通（会话列表、未读角标、筛选、聊天页 + 自动回复 +
面试邀请卡片）、**公司资料编辑与企业认证**（Mock 审核约 1.2s 自动通过、状态同步
「我的」）、我的（含公司信息）、退出登录。HR 新建职位归属按登录上下文注入
（新注册公司 HR 发布的职位挂自己公司名下）。

**认证**：登录页「密码 / 验证码 / 注册」三模式；HR 注册自动创建未认证公司
（`新注册企业-{手机尾4}`）；登录成功统一重置双端落地页签。

**工程化**：`npm run e2e`（`tests/e2e-verify.cjs`）扩展至 **63 项断言**（含人才筛选 6 项），覆盖上述全部新链路；
`npm run build` / `npm start` 跨平台修复；`AGENTS.md` / `CLAUDE.md` 由 `next dev` 自动维护。

**最近一次复验（2026-10-16，本轮提交的代码）**：

| 检查 | 结果 |
| --- | --- |
| `npm run e2e` | **63/63 通过**（求职者端 11 / HR 端 15 / 候选人管理 7 / 人才筛选 6 / 跨端投递闭环 14 / 验证码登录 1 / 注册与企业认证 7 / 质量 2）；用户 Chrome 26 → 26 未受影响 |
| `npx tsc --noEmit` | 仅 3 个**既有**错误（`examples/websocket/*`、`src/lib/db.ts`），本轮文件零错误 |
| `npx eslint`（本轮新增 / 修改文件） | 0 告警 |
| dev server | 端口 3000 HTTP 200 |

---

## 3. 交付明细（累计，排查时可对照）

### 3.1 主要文件与改动

| 文件 | 类型 | 改了什么 / 为什么 |
| --- | --- | --- |
| `src/api/mock/applications.ts` | 新增 | 投递 / 面试内存数据层：可变数组 + 懒加载种子（HR 侧 6 条覆盖全部状态枚举；求职者侧 3 条均投非云杉）、状态文案映射、双端视图组装、面试邀请消息发送 |
| `src/api/applications.ts` / `src/api/hrApplications.ts` | 新增 | 求职者 / HR 两端投递 service（重复投递 40911、不可撤回 40913、邀请校验 40914/40915、状态标记、会话按需创建） |
| `src/api/mock/candidates.ts` | 新增 | 候选人种子（6 人画像），从 `hrConversations.ts` 抽出供「沟通」「候选人」共用 |
| `src/api/mock/companyOps.ts` / `src/api/company.ts` | 新增 | 公司资料编辑（改名同步职位卡片快照）与企业认证提交（§5.6；PENDING → 约 1.2s 自动通过） |
| `src/api/mock/auth.ts` / `src/api/auth.ts` / `src/store/auth.ts` | 修改 | 验证码 store（固定 `123456`、60s 限流）、`registerMockAccount`（HR 自动建未认证公司）、`sendSmsCode` / `loginWithSms` / `register`、store 的 `loginSms` / `register` / `updateCompany` |
| `src/store/hr.ts` | 修改 | 第 4 个 Tab `'candidates'` + 5 个页面类型 + open actions + 根视图常量 |
| `src/components/pinliao/hr/`（候选人 / 面试邀约 / 公司资料 / 认证 5 个新页） | 新增 | `HrCandidatesPage` / `HrCandidateDetailPage` / `HrInterviewInvitePage` / `HrCompanyEditPage` / `HrCompanyVerifyPage` |
| `src/components/pinliao/hr/HrShell.tsx` 等 | 修改 | 四 Tab + 覆盖页渲染；`HrMePage` 入口与认证角标；`HrChatPage` 面试邀请气泡；`HrJobEditorPage` 归属上下文 |
| `src/components/pinliao/MyApplicationsPage.tsx` | 新增 | 求职者「我的投递」（进度 / 撤回 / 接受与婉拒面试） |
| `src/components/pinliao/JobDetailPage.tsx` | 修改 | 底部「发送简历」改为「投递简历」（ApplySheet、已投递态） |
| `src/components/pinliao/LoginPage.tsx` | 重写 | 密码 / 验证码 / 注册三模式 + 60s 倒计时 + 登录落地页签重置 |
| `src/api/mock/hrJobs.ts` / `src/api/hrJobs.ts` | 修改 | 新建职位归属从硬编码云杉改为按登录上下文注入 |
| `tests/e2e-verify.cjs` | 重写扩展 | 63 项断言（含人才筛选 6 项）；新增 `clickAria` / `clickSubmit` / `waitText` / `matchOverlayText` 助手；注册认证段落改轮询等待；修复聊天覆盖层未退出问题 |
| `src/api/mock/resumes.ts` | 新增 | 简历正文字段种子（7 份：6 位演示候选人 + 求职者测试账号）；简历 id 沿用 `resume-{userId}` 约定 |
| `src/api/mock/talentMatch.ts` | 新增 | 人才筛选演示引擎：标签词表 / 英文技术词 / 中文去停用词 → 关键词提取 → 检索简历 → 加权排序 + 证据片段 |
| `src/api/hrTalentMatch.ts` | 新增 | §4.5 候选人匹配 / §4.6 标签词表 service（USE_MOCK 双分支；空条件 42210） |
| `src/components/pinliao/hr/HrTalentMatchPage.tsx` | 新增 | 筛选页：需求输入 + 标签 chips + 结果卡片（命中词 / 证据高亮 / 进候选人详情） |
| 文档 | 新增 / 修改 | 根目录五件套 + `docs/AI-CONTINUE.md`（v1.6）+ `docs/api-endpoints.md`（v0.3.2，§4.5/§4.6）+ `README.md` + `worklog.md` |

### 3.2 已锁定的关键决策（沿用，勿回退）

- HR 端第 4 个 Tab「候选人」；详情 / 面试邀约 / 公司资料 / 企业认证走 `hr` store
  `pageStack` 覆盖页；求职者「我的投递」为 `pinliao` store 的 `applications` 页。
- 面试状态在求职者侧使用候选人视角文案（「待你确认 / 已接受邀请」），与 HR 端口径区分。
- 种子投递避开云杉公司；跨端闭环由 e2e 现场「求职者投递云杉职位」形成。
- 验证码固定 `123456`（60s 限流按手机号）；HR 注册自动建未认证新公司，与企业认证联动。
- 企业认证：提交 → `PENDING` → 约 1200ms 自动通过；契约 §5.6；审核进度复用 §5.3 轮询。
- 人才筛选（演示版）：范围限「站内投递候选人」；引擎在 `mock/talentMatch.ts`，语义检索升级
  只替换该实现 + 服务端（契约 §4.5/§4.6 与页面不变）；HR 上传外部简历库为后续增量。
- 登录成功后必须 `setTab('jobs')` 重置双端落地页签（详见 `AI-CONTINUE.md` §6.7）。
- 新建记录 id 一律递增计数（不用 `Date.now` / `Math.random`）。

### 3.3 边界说明（诚实口径）

- e2e 已覆盖五项功能主链路（见 §2 表）；**未覆盖边界**：注册仅测 HR 角色、认证仅测
  通过态；「标记录用 / 不合适」「婉拒面试」「撤回投递」等已实现且有 UI，但未纳入
  e2e 断言，需要时先补脚本再采信。
- 人才筛选为**无 AI 关键词匹配**（同义词不识别、包含式命中）；e2e 覆盖 6 项主链路；简历种子仅
  7 份，无简历的候选人不会出现在匹配结果（候选人列表不受影响）。
- Mock 全部为内存态，刷新即重置；以上通过均指本轮提交代码的自动化验收结果。

---

## 4. 下一步

1. 用户人工验收**初步通过**（2026-10-16）；继续确认 `docs/AI-CONTINUE.md` §9 的待决事项
   （前端最终框架、接口账目口径等），并按用户后续反馈补齐。
2. **Step 3 后端（Spring Boot）**：按 [`docs/step3-plan.md`](./docs/step3-plan.md) 分块推进
   （T0 → T9，契约正文 70 条；每会话一块，按计划内「收工 checklist」交接）。
   **T0 ✅、T2a ✅（2026-10-17）、T1 ✅、T2b ✅、T3a ✅、T3b ✅（2026-10-18）**：
   - **T2a 已交付**：§1.1 验证码 / §1.3 短信登录 / §1.4 密码登录 / §1.5 刷新 / §1.6 退出 / §1.7 me；
     JWT（access 2h，HS256）+ Redis（refresh 30d 白名单一次性轮换、退出吊销 jti、验证码 60s 限流）；
     鉴权拦截器默认保护 `/api/v1/**`（白名单：`auth/sms|login|refresh|register`、`_demo`）；
     Flyway `V1__auth_schema.sql` + `V2__seed_auth_demo.sql`（5 张鉴权表 + 演示账号，id 与 mock 哈希一致）。
   - **T1 已交付**：`V3__core_schema.sql` 建 12 张剩余业务表（jobs / resumes / applications / interviews /
     conversations / messages / favorites + 字典 / 运营表，companyId 租户列 + 索引）；
     `V4~V8` 演示种子（公司 16 / 职位 51·可见 38·云杉 7 / 分类 84 / 城市 9 / 候选人 6 / 简历 7 /
     投递 9（HR 6 + 求职者 3）/ 面试 3 / 会话 8（HR 6 未读 4）/ 消息 16），由
     `backend/scripts/seed-gen` 从 `src/api/mock/*` 生成（勿手改迁移文件）；
     MyBatis-Plus 分页（MySQL，≤200）+ 租户拦截（HR 注入 company_id、求职者跨租户不注入）+ created/updated 自动填充。
   - **T2b 已交付**：§1.2 注册（校验顺序 42201 → 40910 → 40110；HR 自动建未认证公司
     `新注册企业-{尾4}` + ADMIN 成员；求职者建画像；id 派生自手机号 `u-reg-{phone}` 等）；
     `AuthServiceRegisterTest` 4 项（`mvnw test` 18/18）、冒烟 20/20（含注册段 6 项，见 §7.2）。
   - **T3a 已交付**：§6.1~§6.4 列表/搜索/详情/相似 + §6.9 浏览埋点 + §7.1~§7.3 分类/城市/字典（8 条，公开读）；
     筛选/排序/相似语义对齐 mock，游标分页 + 详情 ETag（If-None-Match → 304），公开接口可选鉴权（匿名可访问、
     带 token 用于推荐加权、写接口仍需鉴权）；实测 `mvnw test` 35/35、`smoke-jobs.mjs` 29/29、
     可见职位 38 / 云杉 7 / 分类 84 全命中。
   - **T3b 已交付**：§6.5~§6.8 发布 / 更新 / 关闭·重开 / 软删（4 条，HR / COMPANY_ADMIN 本公司）；
     `JobCommandService` 统一 40310（不存在 / 非本公司 / 已软删，不泄露存在性）、配额 42211
     （`tenant_configs.max_jobs`，缺省 50）、发布限流 42901（10 次/分钟/公司）；新 id `job-new-{seq}`
     递增派生（软删保留行、序号不复用）；PATCH 部分更新（null 保留原值，合并后校验 42201）。
     实测 `mvnw test` **50/50**、`smoke-jobs.mjs` **49/49**（写接口 20 项）、鉴权冒烟 20/20、种子抽查 16/16。
   - 下一个动作：**T4 投递与面试闭环**（§9/§10，10 条；状态机 + 会话按需创建（先占位）+
     校验码 40911 / 40913 / 40914 / 40915）；验收：JUnit + 冒烟。
   - T9 切换 `USE_MOCK=false` 跑通前端 e2e 63/63 后进入 Step 4 联调（注意：e2e 注册用固定手机号
     `13900004321`，对真实落库的演示库重跑会命中 40910——T9 前需定重置策略，见 §6.3 末条）。
3. 可选增量（未被本轮涵盖）：招聘者端桌面 Web 后台、HR 上传外部简历库、人才筛选语义检索升级（接口不变）等。

---

## 5. 功能任务状态明细（验收五项 + 后续增量）

| # | 任务 | 数据层 | Service | UI | 验收 | 判定 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 投递流程（求职者投递 / 撤回） | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 2 | 候选人管理页（HR 按状态筛选 / 标记） | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 3 | 面试邀请闭环（邀约 / 接受 / 拒绝） | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 4 | 手机验证码登录 / 注册 | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 5 | 公司资料编辑 + 企业认证 | ✅ | ✅ | ✅ | ✅ e2e | 完成 |
| 6 | 人才筛选（演示版增量） | ✅ | ✅ | ✅ | ✅ e2e 6 项 | 完成 |

---

## 6. 已知问题与风险

### 6.1 既有报错（不是你造成的，不要修）

- `npx tsc --noEmit` 报 3 个：`examples/websocket/frontend.tsx`（缺 socket.io-client）、
  `examples/websocket/server.ts`（缺 socket.io）、`src/lib/db.ts`（缺 Prisma client 生成）。
- `npx eslint src` 有少量既有告警（`src/components/ui/*`、`src/hooks/use-mobile.ts`）。
- 浏览器控制台：`next-themes` 注入 script 的告警（无害）、`favicon.ico` 404（未放置图标）。

### 6.2 需要留意的风险点

| 风险 | 说明 |
| --- | --- |
| 契约账目 | `docs/api-endpoints.md` 正文 70 条（含 §5.6、§4.5/§4.6）；表格分模块数字之和 67 与合计 70 不一致，口径待用户确认后统一订正（勿单方面改动合计） |
| e2e 文本匹配点击 | 脚本按文本找按钮（取首个含文本的 `button`）。新增 UI 文案（如「投递」「沟通」「我的」「注册」）可能干扰既有断言；登录提交已改用 `clickSubmit`，覆盖层退出用 `clickAria`；**覆盖层内取文本用顶层 `.pl-page-enter`**（`body.innerText` 含底层页面，已加 `matchOverlayText` 助手），扩展脚本时逐条核对 |
| 新公司 HR 的演示数据 | 新注册 HR 的公司为空（无职位 / 候选人）属预期；演示完整数据请用云杉演示账号 |

### 6.3 环境 / 兼容性注意事项

- **统一用 npm / npx，不用 bun**（目标机器未安装 bun；仓库里另有 `bun.lock` 属历史产物）。
- 端口固定 3000；`npm run e2e` 需要 dev server 先跑起来。
- `npm run build` 需要能访问 Google Fonts（`layout.tsx` 的 `next/font/google`）；国内直连需代理。
- 清理无头 Chrome 只能用 `.zscripts/kill-chrome-safe.cjs` 的 `killChromeTree(pid)`；
  **严禁** `Get-Process chrome | Stop-Process -Force`（会杀掉用户全部标签页）。
- Mock 数据刷新即重置：跨端联动测试必须用应用内退出登录切换账号。
- 水合安全：新页面 / 新数据只在登录后的客户端路径渲染；新建 id 用递增计数。
- **后端（`backend/`）**：JDK 用 IDEA 自带 **JBR 21**，跑 `mvnw` 前必设 `JAVA_HOME`
  （本机系统仅 JDK 1.8）；MySQL / Redis 是 `.local/` 下的**专用实例 3307 / 6379**
  （**不要连 3306 的本机原服务**），用 `backend/scripts/` 起停；`.ps1` 含中文必须 UTF-8 BOM
  （详见 `AI-CONTINUE.md` §6.8）。
- **T2a 起鉴权生效**：拦截器默认保护 `/api/v1/**`（白名单 `auth/sms|login|refresh|register`、`_demo`）；
  调用受保护接口需带 `Authorization: Bearer <accessToken>`（access 2h / refresh 30d）；
  演示账号密码均 `123456`，短信验证码固定 `123456`（60s 限流）。
- **T1 起种子与多租户**：`V4~V8` 已种演示数据（抽查用 `backend/scripts/verify-seed.ps1`，16 项行数断言）；
  种子由 `backend/scripts/seed-gen` 从 `src/api/mock/*` 生成，**勿手改迁移文件**（改 mock 后重跑生成脚本）；
  租户拦截策略：HR 查询注入 `company_id`、求职者跨租户（职位浏览/我的投递/我的会话）不注入——
  求职者侧查询必须显式过滤 `user_id`，改动前先读 `docs/step3-plan.md` §3 与 `AI-CONTINUE.md` §6.9。
- **T2b 起注册落库**：注册产生的 `u-reg-*` / `co-reg-*` / `hr-reg-*` / `cm-reg-*` 记录**持久保留**
  （`verify-seed.ps1` 已按前缀排除，16 项断言不受影响）；手机号注册后不可重复（40910）；
  前端 e2e 注册段用固定手机号，T9 联调对同库重跑前需定演示库重置策略（见 `AI-CONTINUE.md` §6.10）。
- **T3a 起职位公开读可用**：§6.1~§6.4/§6.9 + §7（分类/城市/字典）匿名可访问（带 token 时解析身份用于
  recommend 加权）；列表分页用游标（`cursor`，base64 JSON；默认 limit 20 / 上限 100）；详情支持 ETag
  （`If-None-Match` → 304）。
- **T3b 起 HR 职位写可用**：§6.5~§6.8 需 HR / COMPANY_ADMIN token（本公司）；40310 无权（不存在 / 非本公司 /
  已软删）、42211 配额（`tenant_configs.max_jobs`，缺省 50）、42901 发布限流（10 次/分钟/公司）；
  新职位 id `job-new-{seq}` 递增派生；软删只置 `deleted_at`（行保留），冒烟新建职位收尾软删以保持「可见 38」基线；
  `verify-seed.ps1` 的 jobs_total 已排除冒烟产物 `job-new-%`。
- localStorage 键：求职者 `pinliao.v3`、认证 `pinliao.auth.v1`、token `pinliao.accessToken` / `pinliao.refreshToken`。
- `AGENTS.md` / `CLAUDE.md` 由 `next dev` 自动维护（只管理 `<!-- BEGIN:nextjs-agent-rules -->` 区块，
  其余内容安全保留）；提交时一并带上可保持工作区干净。

---

## 7. 运行命令与测试结果

### 7.1 前端（2026-10-16 实测）

```powershell
npm run dev            # 端口 3000；另开终端保持运行
npm run e2e            # 端到端验收（需 dev server）；本轮：63/63 通过，Chrome 26 → 26 未受影响
npx tsc --noEmit       # 本轮：仅 3 个既有错误，新文件零错误
npm run lint           # ESLint（存量告警见 §6.1）
npm run build          # 生产构建（需 Google Fonts 可达；构建脚本自带产物自检）
npm start              # 生产启动（node .next/standalone/server.js；可用 $env:PORT 换端口）
```

可覆盖的环境变量：`npm run e2e` 支持 `BASE_URL` / `CHROME_PATH` / `CDP_PORT`。

### 7.2 后端 `backend/`（2026-10-18 实测，Step 3 T0 + T2a + T1 + T2b + T3a + T3b）

```powershell
cd backend
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr'  # 必设（本机仅 JDK 1.8）
.\scripts\dev-up.ps1         # 起 MySQL 专用实例 3307 + Redis 6379（首次先 .\scripts\db-init.ps1）
.\mvnw.cmd spring-boot:run   # 后端 :8080；Ctrl+C 停止（收尾用 .\scripts\dev-down.ps1）
.\mvnw.cmd test              # 单元测试（包络 / JWT / 种子密码 / 租户策略 / 注册 / 职位 / 字典）
node scripts\smoke-auth.mjs  # 鉴权接口冒烟（20 项，含注册；需后端已启动）
node scripts\smoke-jobs.mjs  # 职位与字典冒烟（49 项；需后端已启动，T3a/T3b）
.\scripts\verify-seed.ps1    # 种子行数抽查（16 项；需 Flyway 已迁移至 v8）
.\mvnw.cmd -DskipTests package  # 打包（target/pinliao-backend-*.jar）
```

| 检查 | 结果 |
| --- | --- |
| `mvnw.cmd test` | **50/50 通过**（包络 3 + JwtService 4 + 种子密码 hash 1 + 租户策略 6 + 注册 4 + 职位 12 + 职位写 15 + 公开路径 2 + 字典 3） |
| `spring-boot:run` | **3.5s 启动**（T2b 后，schema 已最新无需迁移；T1 首迁为 3.3s）；Flyway 校验 **V1~V8** 通过 |
| `/actuator/health` | `{"status":"UP"}`（200） |
| `/api/v1/_demo/*` 包络 | 与 T0 一致：`traceId`、`42201/40401` 等码位不变 |
| 鉴权冒烟（`backend/scripts/smoke-auth.mjs`） | **20/20 全对（T3a 后复跑无回归）**：登录 200（含云杉 company/member）；`40110/40101/40102/40103/42201/42901` 全码位；refresh 一次性轮换；退出后 me=40102；**注册段**：发码 200 → 坏码 40110 → HR 成功（`新注册企业-{尾4}` / ADMIN / verified=false）→ 重复 40910 → me 带新 token → 求职者无 company |
| 职位 & 字典冒烟（`backend/scripts/smoke-jobs.mjs`） | **49/49 全对**：公开读 29 项（列表匿名默认 20 / total=38 / 游标翻页；筛选上海 12、互联网·前端 2、1-3年 11、20-40K 18；搜索云杉 7 / 前端关键词 / §6.2 独立路径；recommend 期望岗位置顶；详情 HR / 福利 4 / 相似 6、ETag→304、40410；埋点去重；分类 17 根/84 节点；城市 9/热门 5；字典与 mock 完全一致）+ **写接口 20 项**（未登录 401 / 求职者 40301 / 字段校验 42201；发布 `job-new-*` OPEN 归属云杉、hr 陈女士；发布后可见 39；更新部分字段且保留发布时间与统计；关闭后详情 404 + 可见回到 38；重开再次可见；他公司 HR 与不存在职位 40310；软删 `{}` + 404 + 回到 38 基线；兜底幂等清理） |
| 注册边界（手工 4 例） | 字段校验 42201 ×3（phone / password / role）；未发码注册 40110；`co-reg-*` 落库 `verified=false` |
| 种子抽查（`backend/scripts/verify-seed.ps1`） | **16/16 命中**（注册产生的 `u-reg-*` / `co-reg-*` 与 T3b 冒烟产生的 `job-new-*` 已排除）：flyway v8；companies 16；jobs 51（可见 38 / 云杉 7）；categories 84；cities 9；users 8；resumes 7；投递 6+3；interviews 3；HR 会话 6（未读 4）；求职者会话 2；messages 16 |
| 数据库抽查（MySQL 3307） | V4~V8 success=1；种子内容与 mock 一致（职位关闭规则 stableHash(id)%4、简历 content JSON 原文、会话 id 序列 conv-hr-1/4/8/11/14/16 等）；T2b 后新增 `u-reg-*` / `co-reg-*` 等注册记录（持久保留） |

运行数据在 `.local/`（已 gitignore）；MySQL / Redis 起停详见 `backend/README.md` 与 `backend/scripts/`。

---

## 8. 修订记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| v1.0 | 2026-10-16 | 首版：开发交接产出。基于工作区实况盘点（未提交改动 6 个文件 + 文档），记录 27/27 复验、下一步任务与风险 |
| v1.1 | 2026-10-16 | 五项验收功能全部完成（投递 / 候选人 / 面试闭环、验证码登录 / 注册、公司资料 / 企业认证）；e2e 扩展至 57/57；文档同步 `api-endpoints` §5.6 / `README` / `AI-CONTINUE` v1.4 |
| v1.2 | 2026-10-16 | 交接文档一致性刷新：§0 结算 `8b69b87` 实际 hash；`PROJECT` / `ARCHITECTURE` / `AGENTS` / `README` / `AI-CONTINUE` 口径与提交对齐（无代码变更） |
| v1.3 | 2026-10-16 | 用户人工验收初步通过；快照补远端仓库（`origin` → GitHub）；提交推送远端 |
| v1.4 | 2026-10-16 | 人才筛选（演示版）增量：关键词匹配 + 技能标签 + 证据片段（契约 §4.5/§4.6，正文 70 条）；e2e 扩展至 63/63；同步覆盖层断言取数坑 |
| v1.5 | 2026-10-17 | Step 3 分块计划（`docs/step3-plan.md`）与上下文交接协议就绪；快照/下一步指向计划 |
| v1.6 | 2026-10-17 | Step 3 T0 完成：`backend/` Spring Boot 3.5.16 骨架（包络 / 错误码 / traceId / health）+ MySQL 3307 / Redis 6379 专用实例与起停脚本；mvnw test 3/3；基线调整 PG→MySQL、JDK→JBR 21 |
| v1.7 | 2026-10-17 | Step 3 T2a 完成（用户指示先做登入/退出）：鉴权 6 接口（§1.1/§1.3~§1.7）+ JWT/Redis 令牌 + V1/V2 鉴权表与演示账号；`mvnw test` 8/8、冒烟 14 项 + 40111 全码位；T1 顺延为 V3+ 剩余迁移 |
| v1.8 | 2026-10-18 | Step 3 T1 完成：V3 建 12 张剩余业务表 + V4~V8 演示种子（数字对齐 mock：职位 51·可见 38、简历 7、投递 9、会话 8 等）+ MyBatis-Plus 分页/租户拦截/时间填充；`mvnw test` 14/14、`verify-seed.ps1` 16/16、鉴权冒烟 14/14 无回归；种子生成器（`backend/scripts/seed-gen`）与 T1 双提交（`8ed73f8`/`85518bf`） |
| v1.9 | 2026-10-18 | Step 3 T2b 完成：§1.2 注册（校验顺序对齐 mock；HR 自动建未认证公司 `新注册企业-{尾4}` + ADMIN 成员；40910 / 40110；id 派生自手机号）；`mvnw test` 18/18（新增注册 4 项）、冒烟 20/20（注册段 6 项）、`verify-seed` 16/16（排除注册记录）；下一步 T3 职位与分类字典 |
| v1.10 | 2026-10-18 | Step 3 T3a 完成（T3 按「公开读 / HR 写」拆分）：职位公开读（列表 / 搜索 / 详情 / 相似 / 埋点）+ 分类字典（§6.1~§6.4/§6.9 + §7，8 条）；公开接口可选鉴权、游标分页、详情 ETag；`mvnw test` 35/35、`smoke-jobs` 29/29、鉴权冒烟 20/20 无回归；下一步 T3b HR 职位写（§6.5~§6.8） |
| v1.11 | 2026-10-18 | Step 3 T3b 完成：HR 职位写（§6.5~§6.8，4 条）——发布 / 更新 / 关闭·重开 / 软删；40310 无权（不存在 / 非本公司 / 已软删）、42211 配额、42901 限流；`job-new-{seq}` 递增 id；`mvnw test` 50/50、`smoke-jobs` 49/49（写接口 20 项）、鉴权 20/20、种子 16/16（排除 `job-new-%`）；下一步 T4 投递与面试（§9/§10） |
