# 变更记录 · Step 3 归档（2026-10-17 ~ 10-19）

> 归档于 2026-10-20。**默认不要读**；最新条目见 [`../../CHANGELOG.md`](../../CHANGELOG.md)。

---
## [T9 追加] 2026-10-19 — 求职者职位列表切真实（HR 发布 / 关闭对求职者生效）

> 用户选方案 A：只切「职位读路径」，分类字典与消息会话留 T10。

### Changed

- `src/components/pinliao/JobsView.tsx`：列表取数由 `filterJobsSync`（同步 Mock）改为
  `useQuery(['jobs','list',filters,expectCat,expectSub], () => listJobs({...}))` —— 走 `src/api/jobs.ts` 的双分支
  （真实模式 `GET /jobs`）。`limit=100` 单页取全（后端默认 20、上限 100），
  「共 N 个职位」改用后端 `pagination.total`；首帧加载显示「加载中…」，避免误显示空态。
- 删除 `src/api/mock/filter.ts`（其注释本就写着「Step 3 联调阶段此文件不再被调用」，且仅 `JobsView` 引用）。

### Why

- 后端 `GET /jobs` 的筛选口径与 mock 完全一致：`q` 匹配 `title / 公司名 / cat / sub / tags / area`；
  `city/cat/sub/exp/sal` 同名同义；`sort=recommend` 同样按「期望二级=2 / 期望一级=1」加权。
  因此切换后无行为回退，但**HR 端发布 / 编辑 / 关闭职位在真实模式下对求职者立即生效**
  （此前列表读 mock，跨端联动只是「看起来生效」）。

### Verification

- 真实后端（`USE_MOCK=false`）：`db-reset.ps1` 后 `npm run e2e` **63/63**（「可按公司搜索到职位 → 7 条」
  现在真的来自后端）；`npx tsc --noEmit` 仍仅 3 个既有错误；改动文件 `eslint` 零告警。
- Mock 基线（临时移开 `.env.local` 重启 dev server）：`npm run e2e` **63/63**（确认 mock 分支等价）。

### Docs

- `PROGRESS.md` v1.19（§0/§1/§4/§6.3/§8）、`docs/step3-plan.md` v1.14、`docs/AI-CONTINUE.md` v1.21
  （§2、§6.18、§9、§10）、`ARCHITECTURE.md` v1.4（§3 目录树、§6.4、§7.7）、`README.md`、`AGENTS.md` §0、
  `worklog.md` Task 31。

---

## [T9] 2026-10-19 — Step 3 T9：全量联调（`USE_MOCK=false`，e2e 63/63）

> 前端切真实后端并逐项修差异；新增清库重迁脚本。`npm run e2e` **63/63（连续两轮）**。
> 本次提交 hash 见 `git log` 最新一条（结算紧接其后）。

### Added

- **`backend/scripts/db-reset.ps1`**：清库重迁——按 8080 端口止后端 → `DROP + CREATE pinliao`
  → Redis `FLUSHALL`（清验证码冷却 / refresh token）→ 起后端 → 等 `/actuator/health` UP
  → 回读 `flyway=8 / companies=16 / jobs=51 / applications=9`；支持 `-NoRestart` / `-KeepRedis`。
  JDK 探测会校验 major ≥ 17（本机 `JAVA_HOME=D:\JDK1.8` 会让 `maven-shade-plugin` 直接失败）。
- `.env.local`（**不提交**，`.env*` 已 gitignore）：`NEXT_PUBLIC_USE_MOCK=false` +
  `NEXT_PUBLIC_API_BASE_URL=/api/v1`。
- `next.config.ts`：`/api/v1/:path*`、`/uploads/:path*` → `API_PROXY_TARGET`（默认 :8080）的 rewrites
  （前端只发相对路径，代理顺带避免跨域与上传 URL 拼接）。

### Changed

- `LoginPage.tsx`：**「演示账号（一键填入）」与固定验证码提示不再被 `USE_MOCK` 隐藏**（原型演示需要；
  隐藏曾让 e2e 连锁失败到 6/61）。
- `src/api/auth.ts`：真实登录分支补 `assertRoleMatches()`（在 `persistTokens` 之前），
  与 Mock 分支一致地拦截「身份不匹配」。
- `src/api/applications.ts`：`listMyApplications` 解包 `{list,pagination}` 并按 `applicationId`
  合并 §10.1 面试（此前 `interview` 恒为 `null`）。
- `src/api/hrConversations.ts`：`listHrConversations` / `listHrMessages` 解包 `.list`。
- `src/api/hrApplications.ts`：`getHrApplication` 改走 §4.4 列表按 id 定位（§9.3 返回
  `ApplicationDetail`，形状与 HR 详情页不符）；`inviteInterview` 返回类型改 `Interview | Message`。
- `HrInterviewInvitePage.tsx`：约面试透传 `conversationId`（§8.7 路径必需）。
- `HrChatPage.tsx`：真实模式不再调 Mock 专用的 `replyAsCandidate`，改为 1200ms / 2600ms
  定时重取（后端 `ConversationAutoReplyService` 约 1.2s 后写入自动回复）。
- `tests/e2e-verify.cjs`：质量门禁**只豁免 1 条**「错误密码」用例必然触发的 401
  （浏览器对失败响应必记录，属契约内预期行为）；Mock 模式下无此日志，行为不变。

### Verification

- 前端 `npm run e2e`：**63/63**（真实后端，连续两轮，每轮前 `db-reset.ps1`）；`npx tsc --noEmit`
  仍仅 3 个既有错误。
- 后端未改代码：`mvnw test` 173/173、七项冒烟（20/49/51/26/40/64/50）与 `verify-seed` 16/16 不变。

### Docs

- 新增 `ARCHITECTURE.md` §7「后端实现（Step 3）」（技术栈 / 包结构 / 多租户 / 鉴权 / WS / 上传 / T9 对接）；
  `PROGRESS.md` v1.18、`docs/step3-plan.md` v1.13、`docs/AI-CONTINUE.md` v1.20（新增 §6.18 十四条联调坑，
  更新 §6.11 / §6.13 / §9）、`backend/README.md`（脚本与基线）、`AGENTS.md` §0/§1、`worklog.md`。

---

## [9cf3ae0] 2026-10-19 — Step 3 T8：招聘者 / 候选人 / 人才筛选（§4）

> 契约 §4 共 6 条；补齐 HR 画像、我发布的职位、我收到的投递与人才筛选（关键词版）。
> 提交 hash：`9cf3ae0`（结算见紧后 docs 提交）。

### Added

- 招聘者模块（`recruiter/`）：`GET|PATCH /api/v1/hr/profile`（§4.1/§4.2，按 `user_id` 归属，
  缺失 40410，PATCH 部分更新、空姓名 42201）、`GET /api/v1/hr/applications`（§4.4，
  公司维度 + `status/jobId`/扩展 `q`、更新倒序、`HrApplicationView` 超集卡片）、
  `POST /api/v1/hr/candidates/match`（§4.5，关键词匹配，空条件 42210）、
  `GET /api/v1/hr/candidates/match/tags`（§4.6，20 条词表）。
- 职位模块新增 `HrJobQueryService` + `GET /api/v1/hr/jobs`（§4.3，HR 视角可见 CLOSED、`status` 筛选、
  扩展 `q`/`hrId`、`limit` 上限保护）与契约外 `GET /api/v1/hr/jobs/{jobId}`（编辑页需读已关闭职位，
  不存在 / 非本公司 / 已软删统一 40410）。
- `TalentMatchService`：逐条移植 `src/api/mock/talentMatch.ts`（词典长词优先并空格占位、
  英文技术词 `[A-Za-z][A-Za-z0-9+#.-]*` 去尾 `[.-]+`、中文先去停用词再取 `[\u4e00-\u9fa5]{2,}`、
  大小写不敏感去重、技能 3 / 文本 2、证据 ≤3/词与 ≤6/人、score 倒序后按完善度倒序）。
- 新增单元测试 22 项：`HrProfileServiceTest` 4、`HrJobQueryServiceTest` 3、
  `HrApplicationQueryServiceTest` 4、`TalentMatchServiceTest` 11（含关键词提取与片段纯函数用例）。
- 新增 `backend/scripts/smoke-recruiter.mjs` **50 项**：§4.1~§4.6 主链路 + 错误分支，
  只读为主；§4.2 改后还原、§4.3 临时 `job-new-*` 在 finally 软删。

### Changed

- `JobViews.HrRecord.of(job, company)` 抽出为唯一映射源，`JobCommandService.toRecord` 改为委托，
  删除重复的私有薪资 / 时间格式化方法；`JobMapper` 增加 `selectByCompanyAnyStatus` 与
  `selectForCompany(jobId, companyId)`（均排除软删、含 CLOSED/DRAFT、保留租户注入）。
- **出参口径（用户拍板）**：§4.3 返回 `HrJobRecord[]`、§4.4 返回 `HrApplicationView[]`（前端超集），
  而非契约文字的 `Job[]` / `ApplicationWithJob[]`；`cursor` 保留但未启用（演示规模全量返回）。
- 契约外扩展参数接受为可选：§4.3 的 `q` / `hrId`、§4.4 的 `q`（对齐前端 mock 行为）。
- `docs/api-endpoints.md` v0.3.7：§4.3/§4.4 出参与参数口径、新增 `GET /hr/jobs/{jobId}` 说明。

### Fixed

- 无（T8 为新增功能；既有六项冒烟与 `verify-seed` 16/16 复跑无回归）。

### Docs

- 同步 `PROGRESS.md` v1.17、`docs/step3-plan.md` v1.12、`docs/AI-CONTINUE.md` v1.19
  （新增 §6.17 招聘者模块踩坑：`hr_profiles` 非租户注入表、超集出参、扩展参数、CLOSED 口径差异、
  关键词引擎移植保真、e2e 证据文案、85 完善度基线、演示库历史残留）、`docs/api-endpoints.md` v0.3.7。

---

## [docs] 2026-10-18 — 文档结算：接口账目 70 条 / T9 重置策略 / T8→T9 顺序

> 三项决策均由用户拍板；本次提交只改文档，无代码变更。

### Docs

- **接口账目定稿**：以正文 **70** 条为准；`docs/api-endpoints.md` v0.3.6 将总览表校正为
  鉴权 7 / 职位 10 / 消息 9，分模块合计 70，消除此前的 67/70 差异。
- **T9 重置策略**：每轮 e2e 前清库重迁（重建 + Flyway V1~V8），e2e 注册段固定手机号
  `13900004321` 可反复使用；重置脚本在 T9 落地。
- **T8/T9 顺序**：定为 **T8 先、T9 后**。`NEXT_PUBLIC_USE_MOCK` 是全局开关、无模块级粒度，
  先补齐 §4 再整体切真实接口，避免 T9 二次返工与验收范围打折。
- 同步 `PROGRESS.md` v1.16、`docs/step3-plan.md` v1.11、`docs/AI-CONTINUE.md` v1.17/v1.18、
  `AGENTS.md`（待确认清单移除已决项）、`worklog.md`。

---

## [c26dc44] 2026-10-18 — Step 3 T7：用户 / 简历 / 收藏 / 上传（§2/§3/§11/§12）

> 契约 §2/§3/§11/§12 共 19 条；补齐求职者资料、简历、收藏与上传闭环。
> 提交 hash：`c26dc44`（结算见紧后 docs 提交）。

### Added

- 用户模块（`user/`）：`GET|PATCH /api/v1/user/profile`（§2.1/§2.2）、`POST /password/change`（§2.3）、
  `POST /password/reset`（§2.4，匿名 + RESET 验证码）、`POST /phone/change`（§2.5，LOGIN 验证码）、
  `GET|PATCH /user/preferences`（§2.6/§2.7）、`POST /user/deactivate`（§2.8，软删 + 吊销当前 token）。
- 简历模块（`resume/`）：`GET|POST /api/v1/resumes`（§3.1/§3.3）、`GET|PATCH|DELETE /resumes/{id}`
  （§3.2/§3.4/§3.5）、`POST /resumes/{id}/set-default`（§3.6）；首份自动默认、删除默认自动顺延，
  同步 `user_profiles.default_resume_id`；HR 仅可读「已投递到本公司」的简历，越权统一 40410。
- 收藏模块（`favorite/`）：`POST /api/v1/favorites`（§11.1，重复 40910）、`DELETE /favorites/{jobId}`
  （§11.2，幂等）、`GET /favorites`（§11.3，`createdAt DESC, id ASC` 游标 + `total`）。
- 上传模块（`upload/`）：`POST /uploads/sign` / `POST /uploads/confirm`（§12.1/§12.2），本地目录替代 OSS；
  服务端生成 objectKey，HMAC 预签名 `PUT /api/v1/uploads/local/{objectKey}`，确认后经 `/uploads/**`
  静态映射访问；头像/Logo ≤2MB、公司图片 ≤5MB、简历附件 ≤10MB，MIME 与扩展名双向校验。
- 新增 `backend/scripts/smoke-user.mjs` **64 项**：临时 `u-reg-*` 账号隔离，覆盖 19 条接口主链路与
  错误分支、收藏两页、简历默认切换、真实 PUT 直传与静态读回；finally 清理简历 / 收藏。

### Changed

- `WebAuthConfig` 白名单新增 `/api/v1/user/password/reset` 与 `/api/v1/uploads/local/**`；
  其余用户 / 简历 / 收藏 / 上传接口仍需登录。
- `application.yml` 新增 `pinliao.upload.dir`（默认 `target/uploads`）与 `pinliao.upload.url-ttl`
  （默认 10 分钟）。
- 外观偏好按 `pinliao:user:prefs:{userId}` 存 Redis，默认 `auto`；未新增数据库表。

### Fixed

- `smoke-messages.mjs` 在 WebSocket 用例结束前等待自动回复落库，并在下一轮开始前吸收迟到消息，
  修复连续执行时消息计数偶发 +2 的抖动；已复跑 **40/40 两次**。
- `verify-seed.ps1` 的 resumes 计数排除 `u-reg-*` 与 `deleted_at` 行，避免 T7 软删冒烟数据污染种子基线。

### Docs

- 同步 `PROGRESS.md` v1.15、`docs/step3-plan.md` v1.10、`docs/api-endpoints.md` v0.3.5、
  `docs/api-contract.md` v0.3.2、`docs/AI-CONTINUE.md` v1.16（§6.16）、
  `backend/README.md`、`AGENTS.md`、`README.md`、`PROJECT.md`、`worklog.md`；T7 实测
  `mvnw test` **151/151**（新增 37）、`smoke-user` **64/64**，其余冒烟与 `verify-seed` 16/16 无回归。

---

## [8d9513a] 2026-10-18 — Step 3 T6：消息与 WebSocket（§8.1~§8.8 + 4 主题）

> 消息 REST 八条 + `/ws` STOMP 实时通道；未读持久化事实源 + 双向自动回复演示语义。
> 提交 hash：`8d9513a`（结算见紧后 docs 提交）。

### Added

- 消息模块（`message/`）：`GET /api/v1/conversations`（§8.1，游标）、`GET .../{id}/messages`（§8.2）、
  `POST /api/v1/conversations`（§8.3，40912）、`POST .../{id}/messages`（§8.4，30 次/分钟/用户、
  `idempotencyKey` 24h）、`POST .../{id}/read`（§8.5）、`POST .../{id}/typing`（§8.6）、
  `POST .../{id}/actions/send-resume`（§8.7）、`POST .../{id}/close`（§8.8）；另提供契约外
  `GET /api/v1/conversations/{id}` 给 HR 聊天页读取顶栏（不计入 §8 条数）。
- WebSocket：`/ws`（STOMP over WebSocket），握手支持 `Authorization: Bearer` / query `token` /
  `access_token`，CONNECT 也接受原生 header；订阅 `/user/queue/messages`、`/user/queue/typing`、
  `/topic/conversation/{id}/typing`，错误推 `/user/queue/errors`；发送主题
  `/app/conversation/{id}/send|typing|read`，复用 REST 业务校验。
- 双向自动回复：求职者 TEXT → HR 关键词回复；HR TEXT → 候选人关键词回复；先推 typing=true，
  1.2s 后落库并推消息 + typing=false；延迟由 `pinliao.message.auto-reply-delay-ms` 覆盖。
- 新增 `backend/scripts/smoke-messages.mjs`（40 项）：REST 全链路 + WebSocket 推送 / STOMP 发送 /
  typing / 错误队列 / 非法 token；只读写 `conv-new-*` / `msg-new-*`，保护种子未读基线。

### Changed

- 会话列表返回双端超集（`conversation` + `job` + `hr` + `company` + `candidate` + `lastMessage`），
  同一端点按角色取用；消息历史统一按 `createdAt DESC, id ASC`。
- 未读计数以 `conversations.user_unread_count` / `hr_unread_count` 为事实源；Redis 仅用于发送限流与
  `idempotencyKey` 去重（不承担未读持久化）。
- T4 面试邀请写入 `INTERVIEW_INVITE` 消息后，现在同步通过实时通道推送给求职者。
- `/api/v1/conversations/{id}` 归属隔离统一 40410；`firstMessage` 创建 SYSTEM + TEXT 两条消息。

### Fixed

- 会话/消息内存排序前先复制查询结果，避免 MyBatis 返回不可变列表导致排序异常。
- 修正 T6 单测夹具的会话 userId 与 HR 发送断言不一致、跨公司详情夹具未设置 companyId 的问题。

### Docs

- 同步 `docs/api-endpoints.md` §8（角色视角、扩展详情、幂等、自动回复、typing 双通道）、
  `docs/api-contract.md` §10（4 主题 + 错误队列 + 认证方式）、`backend/README.md`、`AGENTS.md`、
  `PROGRESS.md`、`docs/step3-plan.md`、`docs/AI-CONTINUE.md`、`worklog.md`。

---

## [2359625] 2026-10-18 — Step 3 T5：公司模块与企业认证（§5.1~§5.6）

> 契约 §5.1~§5.6（6 条）；公开公司读 + HR 公司管理 + 企业认证异步自动通过。
> 提交 hash：`2359625`（结算见紧后 docs 提交）。

### Added

- `GET /api/v1/companies/{companyId}`（§5.1 公开详情）/ `GET /api/v1/companies/{companyId}/jobs`（§5.2
  公司职位，游标分页）/ `GET /api/v1/company/me`（§5.3 我的公司）/ `PATCH /api/v1/company/me`（§5.4
  部分更新）/ `GET /api/v1/company/members`（§5.5 成员列表）/ `POST /api/v1/company/me/verify`（§5.6
  企业认证）——公司不存在统一 **40410**；资料 / 认证校验 **42210**；已认证重复提交 **40916**。
- `CompanyService`：§5.4~§5.6 的 COMPANY_ADMIN 以 `company_members.role=ADMIN` 为准（演示 HR 的
  user.role 是 HR，成员角色是 ADMIN）；§5.3 仍允许 HR / COMPANY_ADMIN 读取本公司。
- 企业认证提交置 `verified=false / status=PENDING`，后台调度器约 **1200ms** 后自动通过为
  `verified=true / status=ACTIVE`；延迟可用 `pinliao.company.verify-delay-ms` 覆盖。
  提交的四项资料只做校验，不单列持久化表（契约未暴露提交记录，数据模型无对应表）。
- 公司改名后职位公司名实时装配（无 jobs 冗余快照），§5.2 / §6.x 立即同步。
- 测试：`CompanyServiceTest` 12 项 + `JobQueryServiceTest` 公司职位分页 1 项；
  新增 `backend/scripts/smoke-company.mjs` **26 项**（连续两次可重复）。

### Changed

- `AuthInterceptor` 公开路径新增 `GET /companies/*`、`GET /companies/*/jobs`（匿名放行，带 token 仍解析）。
- `JobMapper.selectVisibleByCompany` / `JobQueryService.listByCompany`：仅 OPEN 且未软删，游标语义与 §6.1 一致。
- 冒烟用临时 `co-reg-*` 公司 + `job-new-*` 职位验证写链路，职位 finally 软删，保持游客可见 38 条基线。

### Docs

- 实测：`mvnw test` **90/90**；`smoke-company.mjs` **26/26**；`smoke-jobs.mjs` 49/49、
  `smoke-applications.mjs` 51/51、`smoke-auth.mjs` 20/20、`verify-seed.ps1` 16/16 无回归。
- `docs/step3-plan.md` v1.8 — T5 ✅（`2359625`）+ §4 实测 + 下一步 T6；`PROGRESS.md` v1.13；`worklog.md` Task 25；
  `docs/api-endpoints.md` v0.3.3（§5 权限 / 40410 口径）；`docs/AI-CONTINUE.md` v1.14；
  `backend/README.md`、根 `README.md`、`ARCHITECTURE.md`（多租户口径纠正为 MySQL + 拦截器）、`AGENTS.md` 同步。

---

## [d4ce22c] 2026-10-18 — Step 3 T4：投递与面试闭环（§9/§10 + §8.7）

> 契约 §9.1~§9.5、§10.1~§10.5（10 条）+ §8.7 约面试；状态机与错误码对齐前端 mock。
> 提交 hash：`d4ce22c`（结算见紧后 docs 提交）。

### Added

- `POST /api/v1/applications`（§9.1 投递）/ `GET /api/v1/applications`（§9.2 我的投递）/
  `GET /api/v1/applications/{applicationId}`（§9.3 详情）/ `POST .../withdraw`（§9.4 撤回）/
  `POST .../status`（§9.5 HR 状态）——`SUBMITTED / VIEWED / INTERVIEW / OFFER / REJECTED / WITHDRAWN`
  状态机；**40911** 重复投递、**40913** 状态冲突、**42210** 简历不完整 / 简历越权；
  撤回后重新投递复用原记录并回到 `SUBMITTED`。
- `GET /api/v1/interviews`（§10.1 求职者列表）/ `GET /api/v1/hr/interviews`（§10.2 HR 列表）/
  `GET /api/v1/interviews/{interviewId}`（§10.3 详情）/ `POST .../accept`（§10.4）/
  `POST .../decline`（§10.5）——出参 `InterviewWithJob` / `InterviewWithCandidate`；接受 / 拒绝终态幂等；
  **40913** 非 PENDING 操作。
- `POST /api/v1/conversations/{conversationId}/actions/invite-interview`（§8.7）——创建 PENDING 面试、
  投递置 `INTERVIEW`、写 `INTERVIEW_INVITE` 消息（`userUnreadCount + 1`）；**40914** 已拒绝 / 已撤回不可约面、
  **40915** 已有待确认、**42210** 时间早于当前；现场面试缺地点 / 非法形式 **42201**。
- 投递成功（含重新激活）按 `(jobId, userId)` **幂等占位建会话** `conv-new-{seq}`；
  投递详情额外返回 `conversationId`（契约外增量字段），供 HR 端触达 / 约面试使用，T6 复用不重建。
- 新行 id 统一递增派生：`app-new-{seq}` / `int-new-{seq}` / `conv-new-{seq}` / `msg-new-{seq}`
  （库内同前缀最大序号 + 1，显式 `@InterceptorIgnore`；禁时间戳 / 随机）。
- 测试：`ApplicationCommandServiceTest` 22 项 + `ApplicationQueryServiceTest` 5 项；
  新增 `backend/scripts/smoke-applications.mjs` **51 项**（连续两次可重复）。

### Changed

- `JobQueryService` 增 `summaryOf(JobEntity)` / `detailOf(JobEntity)`，供投递 / 面试模块复用职位出参映射。
- 新增 `Roles`（JOB_SEEKER / HR·COMPANY_ADMIN 守卫）与通用 `PageViews`（游标分页）。
- `verify-seed.ps1` 的投递 / 面试 / 会话 / 消息断言排除 T4 冒烟产物前缀。

### Docs

- 实测：`mvnw test` **77/77**；`smoke-applications.mjs` **51/51**；`smoke-jobs.mjs` 49/49、
  `smoke-auth.mjs` 20/20、`verify-seed.ps1` 16/16 无回归。
- `docs/step3-plan.md` v1.7 — T4 ✅（`d4ce22c`）+ §4 实测 + 下一步 T5；`PROGRESS.md` v1.12；`worklog.md` Task 24；
  `docs/AI-CONTINUE.md` v1.13（§6.13 投递 / 面试与 T9 契约差异）；`backend/README.md`、`AGENTS.md` 同步。
- T9 待处理的前端契约差异：`GET /applications` / `/interviews` 后端为分页对象、前端 service 目前按数组解包；
  §9.3 后端返回 `ApplicationDetail`、前端 HR 详情用 `HrApplicationView`；§8.7 后端按契约返回 `Message`、
  前端 `inviteInterview` 类型写 `Interview`（返回值未使用）；`ensureConversationForApplication` 目前仅 mock 有。

---

## [8102f0b] 2026-10-18 — Step 3 T3b：HR 职位写接口（§6.5~§6.8）

> 契约 §6.5~§6.8（4 条，HR / COMPANY_ADMIN 本公司）；T3 拆分中的 **T3b**（T3a 公开读 + 字典已先行）。
> 提交 hash：`8102f0b`（结算见紧后 docs 提交）。

### Added

- `POST /api/v1/jobs`（§6.5 发布）/ `PATCH /api/v1/jobs/{jobId}`（§6.6 部分更新）/
  `POST /api/v1/jobs/{jobId}/close|reopen`（§6.7）/ `DELETE /api/v1/jobs/{jobId}`（§6.8 软删，幂等）——
  出参对齐前端 `HrJobRecord`（JobSummary + status / 职责 / 要求 / welfare / deleted / companyId）。
- `JobCommandService` —— 归属公司取自登录上下文（不信任请求体）；不存在 / 非本公司 / 已软删统一
  **40310**「无权操作该职位」（对齐 mock，不泄露存在性）；配额 `tenant_configs.max_jobs`（缺省 50）→
  **42211**；发布限流 10 次/分钟/公司（Redis 自然分钟计数）→ **42901**；PATCH 合并后校验 → 42201。
- 新职位 id `job-new-{seq}`：库内现有最大序号 + 1（递增派生，软删保留行、序号不复用；禁时间戳 / 随机）。
- `JobCommandServiceTest` 15 项；`smoke-jobs.mjs` 扩展写接口段 29 → **49 项**（越权 / 关闭不可见 / 软删 / 可见 38 基线）。

### Changed

- `JobMapper` —— `selectTenantMaxJobs`（配额）与 `nextNewJobSeq`（全局序号，显式 `@InterceptorIgnore`）。
- `JobViews.HrRecord`、`JobRequests.Upsert`（PATCH 允许只传部分字段，null 保留原值）；`JobCodes` 增 40310 / 42211。
- `verify-seed.ps1` —— `jobs_total` 排除冒烟产物 `job-new-%`（软删保留行导致行数增长）。

### Fixed

- `publishedAt` 创建时截到毫秒（MySQL `DATETIME(3)`）：避免「创建响应」与「更新 / 详情再读」的时间字符串不一致。

### Docs

- 实测：`mvnw test` **50/50**；`smoke-jobs.mjs` **49/49**；`smoke-auth.mjs` 20/20 无回归；`verify-seed.ps1` 16/16。
- `docs/step3-plan.md` v1.6 — T3b ✅（`8102f0b`）+ §4 实测 + 下一步 T4；`PROGRESS.md` v1.11；`worklog.md` Task 23；
  `docs/AI-CONTINUE.md` v1.12（§6.12 职位写与配额）；`backend/README.md`、`AGENTS.md` 同步。

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

