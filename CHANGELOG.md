# 聘聊 · 变更记录（CHANGELOG.md）

> 记录**重要代码与文档变更**，含未提交的工作区增量。
> 格式约定：版本（或「未提交」）→ Added / Changed / Fixed / Docs 分组；
> 新条目追加在顶部（最新在上）。提交历史另见 `git log`。

---

## [9f509be] 2026-10-18 — Step 3 T3a：职位公开读 + 分类字典（§6.1~§6.4/§6.9 + §7）

> 契约 §6.1~§6.4 / §6.9 / §7.1~§7.3（8 条，全部公开）；T3 拆分为 **T3a**（本提交：公开读 + 字典）
> 与 **T3b**（HR 写 §6.5~§6.8，下一个 chunk）。提交 hash：`9f509be`（结算见紧后 docs 提交）。

### Added

- `GET /api/v1/jobs`（§6.1 列表）/ `GET /api/v1/search/jobs`（§6.2 独立搜索路径）/ `GET /api/v1/jobs/{jobId}`（§6.3 详情）/
  `GET /api/v1/jobs/{jobId}/similar`（§6.4 相似）/ `POST /api/v1/jobs/{jobId}/view`（§6.9 埋点）——
  筛选对齐 mock `listJobs`（城市 / 分类联动 / 经验 / 薪资区间相交 / 关键词 hay = 标题·公司名·cat·sub·tags·area）；
  recommend 按期望二级岗位 +2 / 一级分类 +1 加权（稳定排序）；游标分页（base64 JSON `{s,k,id}`，默认 20 / 上限 100）；
  详情 40410 / 非法游标 40001 / ETag（`id-updatedAt`，If-None-Match → 304）；缓存头 30s / 60s。
- `GET /api/v1/categories`（§7.1 分类树 17 根 / 84 节点）/ `GET /api/v1/cities`（§7.2 9 城 / 热门 5）/
  `GET /api/v1/dict`（§7.3 枚举与 `mock/dict.ts` 完全一致）——缓存头 24h。
- 测试：`JobQueryServiceTest` 12 + `AuthInterceptorPublicEndpointTest` 2 + `DictServiceTest` 3；
  `backend/scripts/smoke-jobs.mjs` 29 项接口冒烟。

### Changed

- `AuthInterceptor` —— 公开接口「可选鉴权」：按「方法 + 路径」判定（匿名放行、UserContext 为空；
  带 token 仍解析用于 recommend 加权）；`POST /api/v1/jobs` 等写接口不放过。
- 公开浏览 SQL 显式 `@InterceptorIgnore(tenantLine)`（`JobMapper`）——HR 账号浏览公开职位也全平台；
  租户注入只作用于 HR 管理端口径（§4.3 / §6.5~§6.8）。

### Fixed

- 开发期两坑（已修，详见 `docs/AI-CONTINUE.md` §6.11）：`@Select` 未包 `<script>` 时不解析 XML 实体
  （`&lt;&gt;` 原样进 SQL → 语法错误）；`@JsonInclude(NON_NULL)` 吞 `nextCursor: null`（契约要求显式 null）。

### Docs

- 实测：`mvnw test` **35/35**；`smoke-jobs.mjs` **29/29**；`smoke-auth.mjs` 20/20 无回归；可见职位 38 / 云杉 7 / 分类 84 / 城市 9 全命中。
- `docs/step3-plan.md` v1.5 — T3 拆分 T3a ✅（`9f509be`）/ T3b ⬜ + §4 实测；`PROGRESS.md` v1.10；`worklog.md` Task 22；
  `docs/AI-CONTINUE.md` v1.11（§6.11 职位公开读与 T9 代理坑）；`backend/README.md`、`AGENTS.md` 同步。

---

## [030a5b2] 2026-10-18 — Step 3 T2b：注册（§1.2，HR 自动建未认证公司）

> 契约 §1.2；注册成功即登录（AuthResult 与登录一致，带 company / member）；校验顺序对齐 mock
> （字段校验 42201 → 已注册 40910 → 验证码 40110，失败不消耗）。提交 hash：`030a5b2`（结算见紧后 docs 提交）。

### Added

- `POST /api/v1/auth/register` — `{phone, code, password, role}`；HR 自动建未认证公司
  「新注册企业-{尾4}」（未融资 / 1-19人 / 企业服务 / `verified=false`）+ 招聘官{尾4} + ADMIN 成员；
  求职者建「求职者{尾4}」画像；id 派生自手机号（`u-reg-/up-reg-/co-reg-/hr-reg-/cm-reg-{phone}` ——
  mock 为进程内递增计数、刷新即复位，落库改为手机号派生保证跨重启稳定唯一）。
- `ApiCodes.PHONE_REGISTERED = 40910`；`AuthRequests.RegisterRequest`（phone 正则 / password ≥ 6 / role 枚举）。
- `AuthServiceRegisterTest` 4 项（校验顺序且不消耗验证码 / 坏码不落库 / 求职者画像 / HR 建公司与成员；
  Mockito 假体，踩坑见 AI-CONTINUE §6.10）。

### Changed

- `backend/scripts/smoke-auth.mjs` — 新增注册段 6 项（14 → **20 项**）：发码 / 坏码 40110 /
  HR 注册（`verified=false`）/ 重复 40910 / me 带新 token / 求职者无 company。
- `backend/scripts/verify-seed.ps1` — users / companies 行数按前缀排除 `u-reg-*` / `co-reg-*`（16 项断言不受注册影响）。

### Docs

- 实测：`mvnw test` **18/18**；冒烟 **20/20**；`verify-seed` **16/16**；字段校验 42201 ×3 + 未发码 40110 手工验证。
- `docs/step3-plan.md` v1.4 — T2b 行 ✅（`030a5b2`）、§4 补实测、下一步 T3；`PROGRESS.md` v1.9；`worklog.md` Task 21；
  `docs/AI-CONTINUE.md` v1.10（§6.10 注册落库与演示库重置）；`backend/README.md` 冒烟数更新。

---

## [8ed73f8 + 85518bf] 2026-10-18 — Step 3 T1：数据库迁移与演示种子（对齐 mock 数字）

> 分两次 feat 落地：`8ed73f8`（T1a 建表 + MyBatis-Plus 配置）+ `85518bf`（T1b 种子 + 生成器/抽查脚本）。
> 种子由 `backend/scripts/seed-gen` 直接从 `src/api/mock/*` 生成，数字与语义对齐 `docs/step3-plan.md` §3。

### Added

- Flyway `V3__core_schema.sql` — 12 张剩余业务表：jobs（`companyId` + 冗余 hr 展示字段）/ resumes /
  favorites / applications / interviews / conversations / messages / resume_view_logs +
  cities / categories（含一级职责模板）/ sys_configs / tenant_configs / usage_logs / audit_logs。
- 种子迁移 `V4~V8` — 公司 16 / 城市 9 / 分类 84；职位 51（可见 38、云杉 7，关闭规则同 mock `stableHash(id)%4`）；
  候选人 6 账号 + 简历 7（content JSON 原文搬自 mock）；投递 9（HR 6 覆盖全状态 + 求职者 3 均非云杉）+
  面试 3；会话 8（HR 6 未读 4 + 求职者 2）+ 消息 16。
- `backend/scripts/seed-gen/`（Node 原生 TS + loader hook）— 从 `src/api/mock/*` 一键生成 V4~V8，
  迁移文件带「勿手改」头；`backend/scripts/verify-seed.ps1` — 16 项行数抽查。
- MyBatis-Plus：分页插件（MySQL，单页 ≤200）、`PinliaoTenantLineHandler`（HR 注入 `company_id`；
  求职者跨租户与全局表不注入）、`PinliaoMetaObjectHandler`（created/updated UTC 自动填充）。

### Changed

- `PinliaoTenantLineHandlerTest` — 新增 6 项（注入策略 / 大小写 / 列名 / 缺企业上下文 40301 兜底）；
  `mvnw test` 8/8 → **14/14**。

### Docs

- 实测：启动自动 migrate **V1~V8 成功**（3.3s）；`verify-seed.ps1` **16/16**；鉴权冒烟 **14/14 无回归**。
- `docs/step3-plan.md` v1.3 — T1 行 ✅、§4 补实测、下一步 T2b；`PROGRESS.md` v1.8；`worklog.md` Task 20；
  `docs/AI-CONTINUE.md` §6.9（租户注入策略 / 种子生成器用法）；`backend/README.md` 命令更新。

---

## [6f286e3] 2026-10-17 — Step 3 T2a：鉴权（验证码 / 密码·短信登录 / 刷新 / 退出 / me）

> 按用户指示「先完成登入和退出」，T2a 先行于 T1（T1 剩余迁移顺延为 V3+）。
> JWT（access 2h，HS256）+ Redis（refresh 30d 白名单一次性轮换、退出吊销 jti、验证码 60s 限流）；
> 鉴权拦截器默认保护 `/api/v1/**`。提交 hash：`6f286e3`（结算见紧后 docs 提交）。

### Added

- `backend/.../auth/` 鉴权模块：`JwtService`（jjwt HS256）、`AuthTokenStore`（Redis 白名单 / 吊销名单）、
  `SmsCodeService`（固定 123456 / 5min / 60s 限流 / 一次性）、`AuthService`、`AuthInterceptor` +
  `UserContext`、`AuthController`（§1.1、§1.3~§1.7 六接口）；`ApiCodes` 新增 40110 / 40111。
- Flyway `V1__auth_schema.sql`（users / user_profiles / companies / company_members / hr_profiles，MySQL 8，
  companyId 租户列 + 外键 + 索引）；`V2__seed_auth_demo.sql`（13800000001 求职者 / 13800000002 HR，密码 123456；
  云杉科技 `co-acgiw7`、HR `hr-x2270n` —— id 与前端 mock 哈希一致）。
- 登录 / me 响应在契约 `AuthResult` 之上带 `company` / `member`（对齐前端 `LoginResult`；HR 工作台渲染需要）。
- `backend/scripts/smoke-auth.mjs` — node 冒烟脚本（14 项：登录 / 限流 / 轮换 / 退出全链路）。
- 测试：`JwtServiceTest`（4 项）、`PasswordHashTest`（种子 hash 回归 1 项）。

### Changed

- `backend/pom.xml` — + jjwt 0.12.6（api/impl/jackson）+ spring-security-crypto（仅 BCrypt，不引入 Security 过滤链）。
- 鉴权拦截器默认保护全部 `/api/v1/**`（白名单：`auth/sms`、`auth/login`、`auth/refresh`、`auth/register`、`_demo`）。
- `EnvelopeSmokeTest` — WebMvcTest 切片排除 `WebAuthConfig` / `AuthInterceptor`（拦截器依赖 Redis / JWT）。

### Fixed

- `V1` 建表 `verified` 由 `TINYINT(1)` 改 `BOOLEAN`：消除 MySQL 8 整型显示宽度弃用告警
  （落库等价 `tinyint(1)`）；本地库重建重跑。

### Docs

- `docs/step3-plan.md` v1.2 — T2 拆分 T2a ✅ / T2b ⬜；T1 迁移编号顺延 V3+；§4 T2a 实测。
- `PROGRESS.md` v1.7 — §0/§1/§4/§6.3/§7.2 同步；`worklog.md` Task 18；`backend/README.md` 鉴权与冒烟说明；
  `docs/AI-CONTINUE.md` §6.8（种子 hash 双处同步、BOOLEAN 坑）+ 修订 v1.8。

---

## [dd7a948] 2026-10-17 — Step 3 T0：后端工程骨架与本地环境（MySQL 3307 / Redis 6379）

> 单仓 `backend/` 落地；技术基线调整：数据库 PostgreSQL → **MySQL 8**（用户拍板，免 Docker 直装）、
> JDK 用 IDEA 自带 JBR 21。提交 hash：`dd7a948`（结算见紧后 docs 提交）。

### Added

- `backend/` — Spring Boot **3.5.16** 工程（Maven Wrapper 3.3.2 → Maven 3.9.16；MyBatis-Plus 3.5.17、springdoc 2.9.1）。
- 统一层：`ApiResponse` 包络 / `ApiCodes` 错误码表（code→HTTP 前 3 位）/ `GlobalExceptionHandler` /
  `TraceIdFilter`（`X-Trace-Id` + MDC）/ `ResponseAdvice`；`/actuator/health` 暴露。
- 样例自检接口 `/api/v1/_demo/{ping,error,validate}`（非契约，T9 后可移除）+ MockMvc 冒烟测试 3 项全过。
- `backend/scripts/` — MySQL 8 专用实例（3307）与 Redis（6379）初始化 / 起停脚本（`db-init` / `dev-up` / `dev-down` 等）。

### Changed

- 技术基线：PostgreSQL → MySQL 8（用户拍板）；JDK「装 Temurin 17」→「IDEA 自带 JBR 21」；运行数据统一 `.local/`。
- 根 `.gitignore` — 新增 `/.local/`；裸规则 `test`/`prompt` 收窄为根级 `/test`、`/prompt`（避免误伤 `backend/src/test` 测试源码）。

### Docs

- `docs/step3-plan.md` v1.1 — T0 勾选；§1 环境定案；§4 T0 实际完成；§6 决策结项。
- `PROGRESS.md` v1.6 — §0 快照 / §1 状态 / §4 下一步（T1 具体动作）/ §6.3 后端注意 / §7 后端命令与实测结果。
- `worklog.md` — Task 17；`AGENTS.md` 速览与后端命令；`docs/AI-CONTINUE.md` §6.8 后端环境坑 + §7.4 口径修正（PG→MySQL）；`docs/architecture.md` 实现注记。

---

## [Step 3 规划] 2026-10-17 — 后端分块执行计划与上下文交接协议（纯文档）

> 提交 hash 见 `git log -1`。回答「Step 3 怎么分块做、上下文满了怎么接力」；
> 计划与任务板的唯一出处为 `docs/step3-plan.md`。

### Docs

- 新增 `docs/step3-plan.md` — T0~T9 分块任务板（含每块规格、演示语义对齐清单、
  上下文交接协议：开工/收工 checklist、应急流程、用户开场白模板、会话预算）；T10+ 增量另列。
- `PROGRESS.md` v1.5 — 快照新增「Step 3 计划」行、§1/§4 指向分块计划。
- `AGENTS.md` — 速览同步「Step 3 计划已就绪」。
- `worklog.md` — Task 16。

---

## [af6197e] 2026-10-16 — HR 人才筛选（演示版）：关键词匹配 + 技能标签 + 命中证据

> 已提交为 `af6197e`（基线 `a7c1fbd`）。方向确认：先做**无 AI 演示版**（关键词匹配）；范围限「站内投递候选人」；输入支持
> 自然语言描述 + 技能标签；语义检索（embedding）升级时保持契约与页面不变；HR 上传外部
> 简历库为后续增量。契约新增 §4.5 / §4.6（正文 68 → 70）；`npm run e2e` 扩展至
> **63/63** 通过（新增「HR 人才筛选」6 项断言）。

### Added

- `src/api/mock/resumes.ts` — 简历正文种子（`ResumeContent` 形状，7 份：6 位演示候选人 +
  求职者测试账号）；简历 id 沿用 `resume-{userId}` 约定（无简历的候选人不会出现在匹配结果）。
- `src/api/mock/talentMatch.ts` — 人才筛选演示引擎：标签词表 / 英文技术词 / 中文分词
  去停用词 → 关键词提取 → 检索简历 → 技能项加权排序 + 证据片段截取。
- `src/api/hrTalentMatch.ts` — service：§4.5 `POST /hr/candidates/match`、
  §4.6 `GET /hr/candidates/match/tags`（USE_MOCK 双分支；空筛选条件 42210）。
- `src/components/pinliao/hr/HrTalentMatchPage.tsx` — 筛选页：需求输入 + 标签 chips +
  结果卡片（命中关键词 / 证据片段原文高亮 / 进候选人详情）。

### Changed

- `src/types/api.ts` — 契约类型 `CandidateMatchInput` / `CandidateMatchEvidence` / `CandidateMatchView`。
- `src/store/hr.ts` / `src/components/pinliao/hr/HrShell.tsx` — 页面类型 `'talent-match'`
  与覆盖层接入；`HrCandidatesPage` 新增「按技能筛选」入口。
- `tests/e2e-verify.cjs` — 新增「HR 人才筛选」6 项；注册与企业认证段落由固定 sleep 改为
  `waitText` 轮询（消除偶发时序抖动）；新增 `matchOverlayText` 助手。

### Docs

- `docs/api-endpoints.md` v0.3.2 — 新增 §4.5 / §4.6；正文 68 → 70 条（GET 27 → 28 / POST 32 → 33）。
- `PROGRESS.md` v1.4 / `ARCHITECTURE.md` v1.2 / `PROJECT.md` v1.2 / `AGENTS.md` / `README.md` /
  `docs/AI-CONTINUE.md` v1.6（新坑：覆盖层「不出现」断言的取数范围；人才筛选依赖简历数据）。
- `worklog.md` — Task 15。

---

## [本轮文档整理] 2026-10-16 — 交接文档一致性刷新（纯文档，无代码变更）

> 目的：任何 AI / 人接手时第一时间进入状态。以 `8b69b87` 为基准，消除残留的
> 「五项功能未完成 / 工作区未提交」口径；提交 hash 见 `git log -1`。

### Docs

- `PROJECT.md` v1.1 — 阶段表与五项功能清单（✅）、验收说明（验证码 `123456`、HR 注册
  自动建公司、e2e 57/57 基线）、文档地图。
- `ARCHITECTURE.md` v1.1 — 运行时总览（HR 四 Tab、新页面）、目录树（移除 ★ 在建标记）、
  Mock 模块图（`companyOps` 快照同步）、认证流（三模式登录 + 落地页签重置）、
  §6.2 由「风险」改为「已修复勿回退」。
- `AGENTS.md` — 速览补验收现状；e2e 基线 27/27 → **57/57**；接口账目表述更新。
- `README.md` — 增补根目录五件套索引；路线表 Step 2 注明 57/57。
- `PROGRESS.md` v1.2 — §0 结算 `8b69b87` 实际 hash。
- `docs/AI-CONTINUE.md` — §0 状态一句话；目录树 / Mock 数据位置表 / §7.5 取数对照表同步新模块。
- `worklog.md` — Task 13。

---

## [8b69b87] 2026-10-16 — 验收五项功能：投递 / 候选人 / 面试、验证码登录注册、公司资料 / 企业认证

> 已提交为 `8b69b87`（基线 `592cc78`）。五项功能已全部接入 UI 并通过扩展后的
> 自动化验收（`npm run e2e` **57/57**）；同批含交接文档五件套与各处文档同步。
> 功能进度与下一步见 `PROGRESS.md`。

### Added（功能代码）

- `src/api/mock/applications.ts` — 投递 / 面试内存数据层：
  - 可变数组 `applications` / `interviews` + 懒加载种子（HR 侧 6 条覆盖全部状态枚举、
    含 1 条待确认面试与 1 条已完成面试；求职者侧 3 条，均投给非云杉公司）；
  - 状态中文映射（投递 / 面试形式 / 面试状态）；
  - 双端视图组装（`seekerApplicationViews` / `companyApplicationViews`）与会话、邀请消息辅助。
- `src/api/applications.ts` — 求职者投递 service：
  `listMyApplications` / `applyJob`（重复投递 40911）/ `withdrawApplication`（不可撤回 40913）
  / `acceptInterview` / `declineInterview`（面试归属校验）。
- `src/api/hrApplications.ts` — HR 投递 service：
  `listHrApplications`（status / jobId / q 筛选）、`getHrApplication`、
  `updateApplicationStatus`（VIEWED / OFFER / REJECTED）、
  `ensureConversationForApplication`、`inviteInterview`（404 / 40914 / 40915 / 42210 校验
  → 建面试 + 置 INTERVIEW + 发 `INTERVIEW_INVITE` 消息）、`HR_APPLICATIONS_QUERY_KEY`。
- `src/api/mock/candidates.ts` — 候选人种子（6 人画像）从 `hrConversations.ts` 抽出，
  供「沟通」与「候选人管理」两个页面共用，保证数据自洽。
- `src/api/mock/companyOps.ts` + `src/api/company.ts` — 公司资料编辑（`applyCompanyPatch`，
  改名同步 `jobs` / `hrJobRecords` 公司快照）与企业认证提交（§5.6 `POST /company/me/verify`；
  PENDING → 约 1.2s 自动通过；42210 / 40916）。
- `src/api/mock/auth.ts` / `src/api/auth.ts` / `src/store/auth.ts` — 验证码与注册：
  验证码 store（固定 `123456`、按手机号 60s 限流）、`registerMockAccount`
  （HR 注册自动建未认证新公司 `新注册企业-{手机尾4}`）、`sendSmsCode` / `loginWithSms` / `register`；
  auth store 新增 `loginSms` / `register` / `updateCompany`（身份校验先于写登录态）。
- 求职者 UI：`MyApplicationsPage.tsx`（我的投递：进度 / 撤回 / 接受与婉拒面试）。
- HR UI：`HrCandidatesPage` / `HrCandidateDetailPage` / `HrInterviewInvitePage` /
  `HrCompanyEditPage` / `HrCompanyVerifyPage`。
- `src/types/api.ts` — 契约类型 `CompanyVerifyInput`、`SmsCodeResult`。

### Changed（功能代码）

- `src/store/hr.ts` — 第 4 个 Tab `'candidates'` + 5 个覆盖页类型 + open actions + 根视图常量。
- `src/store/pinliao.ts` — `PageEntry` 增加 `'applications'`。
- `HrShell.tsx` — 四 Tab + 覆盖页渲染；`HrMePage` — 公司资料 / 企业认证入口与认证角标；
  `HrChatPage` — 面试邀请气泡卡片 + 空消息提示。
- `JobDetailPage.tsx` — 底部「发送简历」改为「投递简历」（ApplySheet + 已投递态）；
  `AppShell.tsx` / `MeView.tsx` — 我的投递入口与页面渲染。
- `LoginPage.tsx` — 重写为「密码 / 验证码 / 注册」三模式（60s 倒计时、注册身份复用顶部
  角色开关、登录成功统一重置双端落地页签）。
- `mock/hrJobs.ts` + `api/hrJobs.ts` + `HrJobEditorPage.tsx` — 新建职位归属从硬编码云杉
  改为按登录上下文注入（新公司 HR 发布的职位挂自己公司名下）。
- `mock/hrConversations.ts` — 引用共享候选人种子；`appendHrMessage` 支持 `type`
  （用于 `INTERVIEW_INVITE`）；新增 `ensureHrConversationForCandidate()`。
- `mock/companies.ts` — 新增 `registerCompany`；`lib/time.ts` — 新增 `formatDateTime`。

### Fixed

- e2e 聊天覆盖层未退出导致后续点击静默落空（新增 `clickAria('返回')`）。
- 重新登录落回上次停留页签（`LoginPage.afterAuthSuccess` 统一 `setTab('jobs')`）。
- 登录页三模式后「登录」文本歧义（e2e 改用 `form button[type=submit]` 提交助手）。

### 验收（本批改动）

- `tests/e2e-verify.cjs` 扩展：新增「候选人管理」「跨端投递闭环」「验证码登录」
  「注册 + 企业认证 + 公司资料」等段落；结果 **57/57 通过**（求职者端 11 / HR 端 15 /
  候选人管理 7 / 跨端投递闭环 14 / 验证码登录 1 / 注册与企业认证 7 / 质量 2），
  用户 Chrome 18 → 18 未受影响。
- `npx tsc --noEmit` 仍仅 3 个既有错误；本轮新增 / 修改文件 eslint 0 告警。

### Docs（交接与同步）

- 新增根目录五件套：`PROJECT.md`、`ARCHITECTURE.md`、`PROGRESS.md`（状态唯一定稿）、
  `CHANGELOG.md`（本文件）、`AGENTS.md` 追加项目规范（Next.js 自动区块保留）。
- `docs/api-endpoints.md` — 新增 §5.6 企业认证提交；公司模块行 5→6；账目说明更新为
  2026-10 复核口径（正文 68）；追加修订记录。
- `docs/AI-CONTINUE.md` v1.4 — 状态、验收记录（57/57）、坑清单（登录页签残留、
  e2e 文本匹配 / 覆盖层、HR 职位归属）同步。
- `README.md` — 功能现状、演示账号、未实现清单、目录结构更新。
- `worklog.md` — Task 11（交接）、Task 12（本轮功能）。

---

## 历史提交摘要（近 6 条，供上下文；详细 diff 用 `git show <hash>`）

| 提交 | 说明 |
| --- | --- |
| `af6197e` | feat: 新增 HR 人才筛选（演示版）——关键词匹配站内投递候选人（契约 §4.5/§4.6）；e2e 63/63 |
| `a7c1fbd` | docs: 固化「人工验收初步通过」状态并补远端仓库信息（推送 origin） |
| `bcac84a` | docs: 交接文档一致性刷新（对齐 `8b69b87`，任何 AI 接手即可第一时间进入状态） |
| `8b69b87` | feat: 完成前端验收五项功能（投递 / 候选人 / 面试、验证码登录注册、公司资料 / 企业认证）；e2e 57/57 |
| `592cc78` | fix: 修复 `npm run build` / `npm start` 在 Windows 上不可用（跨平台构建脚本）；新增可复用验收脚本 `tests/e2e-verify.cjs` + `npm run e2e` |
| `046586f` | docs: 补录自动化验收结果（24/24 通过） |

更早的 Step 0 / Step 1 决策与产出记录见 `worklog.md`（Task 0 ~ 10）。

---

## 用法约定

1. 完成一个增量后：把「未提交」段落的实际内容整理为正式条目（或标注提交 hash）。
2. 只写**发生了的事实**（含测试结果），不要把计划中的功能写成已完成。
3. 与 `PROGRESS.md` 分工：本文件讲「改了什么」，`PROGRESS.md` 讲「现在到哪、下一步」。

---

## 修订记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| `8b69b87` | 2026-10-16 | 首版：记录五项验收功能（投递 / 候选人 / 面试、验证码登录注册、公司资料 / 企业认证）接入 UI 与 e2e 57/57，及交接五件套与文档同步 |
| （文档整理） | 2026-10-16 | 交接文档一致性刷新：以 `8b69b87` 为基准统一口径（状态 / 目录 / 基线 / hash 结算），无代码变更 |
| `af6197e` | 2026-10-16 | HR 人才筛选（演示版）：关键词匹配 + 技能标签 + 命中证据；契约 §4.5/§4.6（正文 70）；e2e 63/63 |
