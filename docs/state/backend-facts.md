# 后端接口事实与环境注意事项（state/backend-facts.md）

> 由 `PROGRESS.md` §6.3 原样迁出（2026-10-20）。这是「现在是什么样」，不是历史；
> 踩坑与成因见 [`../pitfalls/backend-core.md`](../pitfalls/backend-core.md)。
> 契约以 [`../api-endpoints.md`](../api-endpoints.md) 为准。

---
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
  （详见 `../pitfalls/backend-core.md` §6.8）。
- **T2a 起鉴权生效**：拦截器默认保护 `/api/v1/**`（白名单 `auth/sms|login|refresh|register`、`_demo`）；
  调用受保护接口需带 `Authorization: Bearer <accessToken>`（access 2h / refresh 30d）；
  演示账号密码均 `123456`，短信验证码固定 `123456`（60s 限流）。
- **T1 起种子与多租户**：`V4~V8` 已种演示数据（抽查用 `backend/scripts/verify-seed.ps1`，16 项行数断言）；
  种子由 `backend/scripts/seed-gen` 从 `src/api/mock/*` 生成，**勿手改迁移文件**（改 mock 后重跑生成脚本）；
  租户拦截策略：HR 查询注入 `company_id`、求职者跨租户（职位浏览/我的投递/我的会话）不注入——
  求职者侧查询必须显式过滤 `user_id`，改动前先读 `docs/step3-plan.md` §3 与 `../pitfalls/backend-core.md` §6.9。
- **T2b 起注册落库**：注册产生的 `u-reg-*` / `co-reg-*` / `hr-reg-*` / `cm-reg-*` 记录**持久保留**
  （`verify-seed.ps1` 已按前缀排除，16 项断言不受影响）；手机号注册后不可重复（40910）；
  前端 e2e 注册段用固定手机号，T9 联调对同库重跑前需定演示库重置策略（见 `../pitfalls/backend-core.md` §6.10）。
- **T3a 起职位公开读可用**：§6.1~§6.4/§6.9 + §7（分类/城市/字典）匿名可访问（带 token 时解析身份用于
  recommend 加权）；列表分页用游标（`cursor`，base64 JSON；默认 limit 20 / 上限 100）；详情支持 ETag
  （`If-None-Match` → 304）。
- **T3b 起 HR 职位写可用**：§6.5~§6.8 需 HR / COMPANY_ADMIN token（本公司）；40310 无权（不存在 / 非本公司 /
  已软删）、42211 配额（`tenant_configs.max_jobs`，缺省 50）、42901 发布限流（10 次/分钟/公司）；
  新职位 id `job-new-{seq}` 递增派生；软删只置 `deleted_at`（行保留），冒烟新建职位收尾软删以保持「可见 38」基线；
  `verify-seed.ps1` 的 jobs_total 已排除冒烟产物 `job-new-%`。
- **T4 起投递 / 面试可用**：§9 + §10（10 条）+ §8.7 约面试；求职者本人 / 本公司 HR 归属隔离，不存在 / 非本人 /
  非本公司统一 40410；40911 重复投递、40913 状态冲突、40914 不可约面、40915 已有待确认、42210 简历 / 时间前置。
  投递成功即按 `(jobId, userId)` 幂等占位建会话（`conv-new-{seq}`），HR 端「沟通 / 约面试」可直接使用
  `ApplicationDetail.conversationId`（契约外增量字段），T6 会话模块复用不重建。冒烟产物
  `app-new-%` / `int-new-%` / `conv-new-%` / `msg-new-%` 已从 `verify-seed.ps1` 的对应断言中排除。
- **T5 起公司模块 / 企业认证可用**：§5.1/§5.2 公开读匿名可访问（`/companies/{id}`、`/companies/{id}/jobs`）；
  §5.3 需 HR / COMPANY_ADMIN；§5.4~§5.6 的「公司管理员」以 `company_members.role=ADMIN` 为准
  （演示 HR 的 user.role 是 HR、成员角色是 ADMIN，不能只看 token role）。公司名实时从 companies 表装配，
  资料编辑后职位公司名立即同步；认证提交 → PENDING → 约 1.2s 自动 ACTIVE（可用
  `pinliao.company.verify-delay-ms` 覆盖，单测用 30ms）；40916 已认证 / 42210 资料校验 / 40410 公司不存在。
  `smoke-company.mjs` 用临时 `co-reg-*` 公司 + 临时 `job-new-*` 验证写链路，职位在 finally 软删以保持可见 38 基线。
- **T6 起消息 / WebSocket 可用**：§8.1~§8.8 需登录，归属隔离统一 40410（不存在 / 非参与方 / 跨公司）；
  40912 重复发起、42201 消息类型 / 内容校验、42901 发送限流（30 次/分钟/用户）；
  `conversations.user_unread_count` / `hr_unread_count` 是未读事实源，Redis 仅做限流与 `idempotencyKey` 24h 去重。
  WebSocket 端点 `/ws`（STOMP，query `?token=` 或 `Authorization` 握手）；`/user/queue/messages`、
  `/user/queue/typing`、`/topic/conversation/{id}/typing`，错误走 `/user/queue/errors`；TEXT 自动回复默认延迟
  1200ms（`pinliao.message.auto-reply-delay-ms`）。消息冒烟只动 `conv-new-*` / `msg-new-*`，不标记种子会话已读。
- **T7 起用户 / 简历 / 收藏 / 上传可用**：§2 需登录（§2.4 重置密码匿名）；简历仅 JOB_SEEKER 可写，
  HR 仅能读「已投递到本公司」的简历（`applications.resume_id + company_id`），越权统一 40410；
  首份简历自动默认，删除默认自动把最近更新的剩余简历设为默认并同步 `user_profiles.default_resume_id`。
  收藏 `(user_id, job_id)` 唯一，重复 40910；§11.3 返回 `{list,pagination}` 且 `total` 为全部收藏数。
  上传用本地目录替代 OSS：`POST /uploads/sign` 返回 HMAC 预签名 `PUT /api/v1/uploads/local/{objectKey}`，
  确认后返回 `/uploads/{objectKey}`；默认目录 `backend/target/uploads`（`PINLIAO_UPLOAD_DIR` 可覆盖），
  URL 有效期默认 10 分钟（`pinliao.upload.url-ttl`），未新增上传元数据表。
  外观偏好按 userId 存 Redis（`pinliao:user:prefs:{userId}`，默认 auto）；注销软删并立即吊销当前 accessToken。
  `smoke-user.mjs` 使用临时 `u-reg-*` 账号并自行清理简历 / 收藏；`verify-seed.ps1` 的 resumes 计数
  已排除 `u-reg-*` 与软删行（T7 软删简历会保留行）。
- **T8 起招聘者模块可用**：§4 全部要求 HR / COMPANY_ADMIN 且带企业上下文（`Roles.requireManager()`，
  越权 40301）；`hr_profiles` 按 `user_id` 显式归属（**该表不在租户注入表集合内**，详见
  `../pitfalls/backend-collab.md` §6.17）；§4.3/§4.4 出参为**契约超集**（`HrJobRecord[]` / `HrApplicationView[]`，
  T9 前端真实分支零适配），`cursor` 为契约保留参数（演示规模未启用），`q` / `hrId` 为契约外扩展参数；
  另新增契约外读取 `GET /api/v1/hr/jobs/{jobId}`（HR 编辑页需打开已关闭职位，公开详情只认 `OPEN`）。
- **T9 起全量联调（`USE_MOCK=false`）**：前端 `client.ts` 只发相对路径，故由 `next.config.ts` 的
  rewrites 把 `/api/v1/**` 与 `/uploads/**` 代理到 `API_PROXY_TARGET`（默认 `http://localhost:8080`）；
  开关在 `.env.local`（`NEXT_PUBLIC_USE_MOCK=false` + `NEXT_PUBLIC_API_BASE_URL=/api/v1`，`.env*` 不提交），
  **改后必须重启 dev server**（`NEXT_PUBLIC_*` 构建期内联）。
- **每轮 e2e / 冒烟前先清库重迁**：`backend/scripts/db-reset.ps1`（按 8080 端口止后端 → `DROP + CREATE pinliao`
  → Redis `FLUSHALL` → 起后端 → 等 `/actuator/health` UP → 回读 `flyway=8 / companies=16 / jobs=51 /
  applications=9`）。e2e 注册段用固定手机号 `13900004321`，**只有清库后才能重复注册**；Redis 也需清，
  否则验证码 60s 冷却会让「发送验证码」返回 42901。脚本参数：`-NoRestart` 只清库、`-KeepRedis` 保留缓存。
- **历史冒烟残留已被重置脚本覆盖**：早期 T4 冒烟留下一条可见的 `app-new-*` 投递（`app-new-1`：
  `u-seeker-1` 投云杉 `job-1`，`WITHDRAWN`、未软删），曾让 §4.4/§4.5 多一位候选人；冒烟断言写为
  「种子齐备 + 容忍残留」，而 e2e 走「先 `db-reset.ps1` 再跑」的流程，计数与 mock 基线一致。
- **求职者职位读路径已全部切真实（T9 追加，2026-10-19）**：`JobsView` 由 `filterJobsSync`（mock
  同步过滤）改为 `useQuery + listJobs()`（`GET /jobs`；`limit=100` 单页取全，计数用后端
  `pagination.total`），`src/api/mock/filter.ts` 已删除；详情页本就走 `getJobDetail()`（`GET /jobs/{id}`）。
  后端 `q` 与 mock 同口径（标题 / 公司名 / 分类 / 子类 / 标签 / 区域），`sort=recommend` 也按
  「期望二级=2 / 期望一级=1」加权 → **HR 发布 / 编辑 / 关闭职位对求职者立即生效**。
- **T9 仍遗留（mock 驱动，e2e 不依赖）**：① 分类字典（组件用 store 的 `CATEGORY_LIST` / `SUB_LIST_OF`，
  `categories.ts` 真实分支无调用方；后端 `/dict` 种子与 mock 同源，切了无可观测收益）；
  ② 求职者消息会话（`store/pinliao.ts` 由 mock 播种，`conversations.ts` 真实分支不回写 store →
  真实模式下「立即沟通」后的会话可能显示不存在）；③ HR「发消息」入口
  （`ensureConversationForApplication` 真实分支直接抛 42210，可改用 §4.4 投递卡片里的 `conversationId`）。
  ②③ 建议独立一轮（T10）处理：先把 store 的会话模型改成 TanStack Query（未读 / 乐观发送 / 自动回复），
  并先补求职者消息页的 e2e 断言（当前零覆盖），再动代码。
- localStorage 键：求职者 `pinliao.v3`、认证 `pinliao.auth.v1`、token `pinliao.accessToken` / `pinliao.refreshToken`。
- `AGENTS.md` / `CLAUDE.md` 由 `next dev` 自动维护（只管理 `<!-- BEGIN:nextjs-agent-rules -->` 区块，
  其余内容安全保留）；提交时一并带上可保持工作区干净。
