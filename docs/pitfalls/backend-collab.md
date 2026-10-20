# 踩坑 · 消息 / 用户 / 招聘者 / 联调（§6.15~§6.18）

> 摘自 `docs/AI-CONTINUE.md` §6（2026-10-20 拆分，编号未变）。索引见 [`../AI-CONTINUE.md`](../AI-CONTINUE.md) §6。

---
### 6.15 消息与 WebSocket（Step 3 T6 起）

- **未读事实源在数据库**：`conversations.user_unread_count` / `hr_unread_count` 才是持久化口径；
  Redis 只做发送限流（30 次/分钟/用户）与 `idempotencyKey` 24h 去重。不要把未读搬回 Redis，
  否则重启和 T9 的种子断言都会漂。
- **列表是双端超集**：`GET /conversations` 每项同时返回 `conversation / job / hr / company /
  candidate / lastMessage`。T9 接入时求职者取 job/hr/company，HR 取 candidate，不要另造接口。
- **历史顺序**：§8.2 明确 `createdAt DESC, id ASC`，出参是 `{ list, pagination }`；前端 mock
  的真实分支若按数组 / 升序处理，T9 必须适配（见下方差异清单）。
- **扩展读取**：`GET /conversations/{id}` 是 T6 为 HR 聊天顶栏加的契约外读取，与列表项同形；
  不计入 §8 的 8 条 HTTP 账目，`verify-seed` 也不受影响。
- **WebSocket 鉴权**：`/ws` 用 STOMP；浏览器 query `?token=<accessToken>`（也接受
  `access_token` / `Authorization` header）。握手无 token 会先升级、CONNECT 阶段拒绝；非法 /
  已吊销 token 握手即拒绝。订阅 `/user/queue/messages`、`/user/queue/typing`、
  `/topic/conversation/{id}/typing`，错误走 `/user/queue/errors`；发送用
  `/app/conversation/{id}/send|typing|read`。
- **发送不回显**：REST / STOMP 的 `send` 只向对方推送，避免发送方收两条；前端本地乐观消息
  需自行去重（T9 注意）。
- **自动回复线程没有 UserContext**：`ConversationAutoReplyService` 用显式 conversationId /
  companyId 查询，不能依赖租户注入；延迟默认 1200ms，可配 `pinliao.message.auto-reply-delay-ms`，
  单测直接调包级 `deliver` 避免异步等待。
- **幂等只在写成功后缓存**：同一 `idempotencyKey` 串行重试返回同一条；并发同时到达仍可能各写一条
  （演示可接受，真实支付/消息接入需加分布式锁或唯一索引）。
- **冒烟不能碰种子会话**：`smoke-messages.mjs` 只读写 `conv-new-*` / `msg-new-*`，绝不标记
  `conv-hr-*` / `conv-seed-*` 已读；否则 `verify-seed.ps1` 的 HR 未读 4 / 求职者未读 2 会失败。
- **T9 需适配的前端差异**：真实分支的会话/消息列表形状（`{list,pagination}` vs 数组）、时间字符串
  （ISO UTC 带 Z）、消息 `content` 结构化类型（后端返回 JSON 字符串，mock 可能是对象）、
  §8.7 邀请接口返回 `Message` 而前端类型声明为 `Interview`、`GET /hr/applications` 属 T8。

### 6.16 用户 / 简历 / 收藏 / 上传（Step 3 T7 起）

- **鉴权白名单只多两条**：`POST /api/v1/user/password/reset`（忘记密码）与
  `PUT /api/v1/uploads/local/**`（预签名直传）；其余 §2/§3/§11/§12 均需登录。
  `/uploads/**` 是静态资源，不在 `/api/v1/**` 下，不走 `AuthInterceptor`。
- **验证码场景**：重置密码用 `RESET`；改手机号用 `LOGIN`（新号可能尚未注册）。两者都先做业务冲突 /
  原密码校验，再消耗验证码；`smoke-user.mjs` 通过 RESP 直连 Redis 删除 60s 冷却键，保证可连续跑。
- **外观偏好不落库**：Redis key `pinliao:user:prefs:{userId}`，默认 `auto`；仅接受
  `auto|light|dark`。T9 前端仍可用 localStorage，接口是多端同步预留。
- **注销是软删**：`users.status=DELETED` + `deleted_at`，同时吊销当前 accessToken（再用返回 40102）；
  当前没有恢复接口，30 天恢复需运维 / 后续增量补。注销账号仍以 `u-reg-*` 留在库中，`verify-seed`
  的 users 计数已按前缀排除。
- **简历权限**：求职者只读写本人；HR 必须存在 `applications.resume_id` 指向该简历且
  `company_id` 为当前公司，否则统一 40410。列表按「默认优先、更新时间倒序」。
  首份自动默认；删除默认简历时自动把最近更新的剩余简历设为默认；全部删完清空
  `user_profiles.default_resume_id`。简历软删行保留，`verify-seed` 的 resumes 已排除
  `u-reg-*` 与 `deleted_at`，不要按全表行数断言。
- **收藏**：id 为 `fav-{userId}-{jobId}`，`(user_id, job_id)` 唯一，重复收藏 40910；
  取消幂等。列表返回 `{ list, pagination }`，`pagination.total` 是该用户全部收藏数，
  游标按 `createdAt DESC, id ASC`；职位摘要可能为 null（职位后续下线）。
- **上传是本地替代实现**：`POST /uploads/sign` 由服务端生成 objectKey 与 HMAC 签名，返回相对
  `uploadUrl`；客户端直接 `PUT` 到该 URL（无登录态），完成后 `POST /uploads/confirm` 返回
  `/uploads/{objectKey}`。确认**不会**写业务表（契约没有目标实体 id）；头像 / 附件落库由前端随后
  PATCH profile / resume 完成，T9 接入时注意。目录默认 `target/uploads`，URL 10 分钟过期；
  对象 key 含 userId，只能确认本人对象。
- **T9 前端差异**：真实 `UserProfile` 不含手机号（手机号在 `auth/me.user.phone`）；简历 / 收藏返回
  相对 URL 与 `{list,pagination}`，前端 `request` 需拼 `API_BASE_URL` 并归一出参；
  `GET /resumes/{id}` 的 HR 分支只有已投递关系才返回，mock 里可能更宽松。
- **T8/T9 顺序与边界**：§4.1~§4.6 已于 **T8（2026-10-19）全部落地**，`USE_MOCK=false` 尚未切换；
  T9 验收范围含 §4 全部页面（人才筛选不再暂留 Mock），不引入模块级 Mock 开关。详见 §6.17。

### 6.17 招聘者 / 候选人 / 人才筛选（Step 3 T8 起）

- **`hr_profiles` 不在租户注入表集合内**：`PinliaoTenantLineHandler.TENANT_TABLES` 不含该表
  （它按 `user_id` 唯一，见 V1 的 `uk_hr_profiles_user`）。§4.1/§4.2 必须显式按 `user_id` 归属查询；
  误用 `company_id` 会读到同公司其他 HR 或读不到。新增 HR 侧读取前，先确认目标表在不在注入集合里。
- **出参口径是「前端超集」**（2026-10-18 用户拍板）：§4.3 返回 `HrJobRecord[]`（`JobSummary` + `status`
  + `responsibilities` / `requirements` / `welfare` + `deleted` + `companyId`），§4.4 返回
  `HrApplicationView[]`（application + candidate 摘要 + 职位摘要 + `conversationId` + 最近面试）。
  契约文字写的是 `Job[]` / `ApplicationWithJob[]`，差异已在 `docs/api-endpoints.md` §4 标注，
  目的是让 T9 前端真实分支零适配。
- **契约外扩展**：`GET /api/v1/hr/jobs/{jobId}` 供 HR 编辑页打开已关闭职位（公开详情 §6.3 只认
  `OPEN`）；§4.3 额外接受 `q`（标题模糊）/ `hrId`（只看某招聘官），§4.4 额外接受 `q`
  （候选人姓名 + 职位名称，大小写不敏感）；`cursor` 为契约保留参数但未启用（演示规模全量返回，
  `limit` 作 1~100 上限保护），T9 不要依赖游标。
- **「HR 可见已关闭职位」是 §4.3 与 §6.3 的关键差异**，冒烟用「创建 `job-new-*` → close →
  HR 列表 / 详情可见且公开详情 40410 → 软删后 HR 详情 40410」验证；临时职位必须在 `finally`
  软删，否则「可见 38」基线与 `verify-seed`（`jobs_total` 排除 `job-new-%`）会漂。
- **人才筛选是关键词匹配（演示版）**：`backend/.../recruiter/service/TalentMatchService.java` 与
  `src/api/mock/talentMatch.ts` 逐条对齐——词典长词优先且命中后用空格占位（防「自动化测试」再命中
  「测试」）、英文技术词 `[A-Za-z][A-Za-z0-9+#.-]*` 并去掉结尾 `[.-]+`、中文先去停用词再取
  `[\u4e00-\u9fa5]{2,}`、大小写不敏感去重保留首次出现的大小写、技能 3 / 文本 2 取最大值、
  单关键词 ≤3 条证据 / 单候选人 ≤6 条、`score` 倒序后按简历完善度倒序。**改 mock 引擎必须同步
  Java 并跑 `TalentMatchServiceTest`**（该测试就是移植保真度护栏）。
- **e2e 依赖的证据文案与片段规则**：`技能 / 求职意向 / 个人简介 / 工作经历 · {公司} /
  项目经历 · {项目名}`；片段取命中处「前 10 / 后 46」字符并按需补 `…`。`技术栈` 必须落在个人简介
  片段里、`项目经历 · 招聘管理系统前端重构` 必须出现在证据里（两者都是 e2e 断言点）。
- **候选人口径复用 T6**：`CandidateSummaryService` 与 `ConversationQueryService.candidateOf` 同口径
  （缺画像给占位、缺字段给 `—` / `0`、存在可用简历 = 完善度 85）；单测按 85 断言，不要改成精确分。
- **本机演示库有历史冒烟残留**：早期 T4 冒烟留下一条可见的 `app-new-*` 投递
  （`app-new-1`：`u-seeker-1` 投云杉 `job-1`，状态 `WITHDRAWN`、未软删），会让 §4.4/§4.5 结果多一位
  候选人；冒烟断言按「种子齐备 + 容忍残留」编写，T9 e2e 前必须按 §6.10 策略清库重迁，
  否则候选人数与 mock 基线不一致。

---

### 6.18 T9 全量联调（`USE_MOCK=false`，Step 3 T9 起）

- **`USE_MOCK` 会隐藏「演示专用 UI」→ e2e 会成片失败**：`LoginPage` 的「演示账号（一键填入）」
  曾用 `USE_MOCK &&` 包住，切真实后端后整块消失，e2e 点不到账号 → 登录失败 → 61 项连锁 FAIL
  （试跑一度 6/61）。排查同类问题时**先看演示用的 UI 是否被开关隐藏**。结论：原型演示所需的
  一键填入 / 固定验证码提示在联调模式下**同样保留**（只改 `USE_MOCK` 判断，不删内容）。
- **真实分支必须自己补「身份校验先于写登录态」**：`loginWithPassword` / `loginWithSms` 的
  `expectRole` 校验原来只在 mock 分支里，真实分支直接 `persistTokens` → 用 HR 手机号选「求职者」
  能直接进 HR 工作台。现抽出 `assertRoleMatches(result, expectRole)`，在 `persistTokens` **之前**
  调用（角色取 `result.user.role === HR ? HR : JOB_SEEKER`，`COMPANY_ADMIN` 归 HR）。
- **分页包络统一解包 `.list`**：后端列表一律 `{list, pagination:{nextCursor,hasMore,total}}`
  （`PageViews.Page`）。T9 已解包：§9.2 我的投递、§10.1 面试、§8.1 会话、§8.2 消息
  （`listHrConversations` / `listHrMessages` / `listMyApplications`）。新增真实分支时**先确认
  返回是数组还是 Page**；`JobViews.Pagination` 未加 `NON_NULL`，`nextCursor: null` 会显式下发。
- **HR「候选人详情」不要用 §9.3**：§9.3 `GET /applications/{id}` 返回 `ApplicationDetail`
  （`job`/`resume`/`interviews[]`，**没有** `candidate`、没有单个 `interview`），HR 页需要的是
  §4.4 的 `HrApplicationView` 超集。现 `getHrApplication` 走 `GET /hr/applications` 后按
  `application.id` 定位（演示规模可接受；上量再补一条 §4.4 单条详情）。
- **§8.7 约面试必须带 `conversationId`**：真实端点是
  `POST /conversations/{conversationId}/actions/invite-interview`，缺会话直接 42210。
  `conversationId` 由 §4.4 投递卡片带出（`view.conversationId`），`HrInterviewInvitePage` 已透传；
  返回类型是 `Message`（不是 `Interview`），故 `inviteInterview` 标注 `Promise<Interview | Message>`。
- **真实模式不要调「Mock 专用」函数**：`replyAsCandidate` 在真实分支抛错，会让 HR 聊天页的
  `setCandidateTyping(false)` / `invalidateQueries` 被跳过（UI 卡在「正在输入」）。
  正确做法：真实模式不本地追加，改为定时重取消息（后端
  `ConversationAutoReplyService` 在 ~1.2s 后写入自动回复；前端**没有 WS 连接**，故
  1200ms + 2600ms 各 refetch 一次）。
- **e2e「零控制台错误」需要一条精确豁免**：真实后端下「错误密码被拒绝」用例必然触发 1 次
  HTTP 401（契约行为），浏览器必然记一条 `Failed to load resource: 401`。`tests/e2e-verify.cjs`
  的末段质量门禁**只豁免 1 条**该形态的 401（`exempt401`），其余 401 仍会失败；Mock 模式无此日志，
  行为不变（仍是 63 项）。
- **清库重迁脚本 `backend/scripts/db-reset.ps1`**（T9 落地，`npm run e2e` 前必跑）：
  按 **8080 端口**定位并结束后端 PID（勿按进程名杀 java）→ `DROP + CREATE pinliao` → Redis
  `FLUSHALL`（清验证码 60s 冷却 / refresh token）→ 起后端 → 等 `/actuator/health` UP → 回读
  `flyway=8 / companies=16 / jobs=51 / applications=9`。`-NoRestart` 只清库，`-KeepRedis` 保留缓存。
  **注册用的固定手机号 `13900004321` 只有清库后才能重复注册**，所以「每轮 e2e 前重迁」是硬前提。
- **脚本本身的三个坑**：① JDK 探测必须**校验 major ≥ 17**——本机 `JAVA_HOME=D:\JDK1.8` 存在但会让
  `maven-shade-plugin` 直接炸，故候选顺序为 `PINLIAO_JAVA_HOME` → IDEA `jbr` → `JAVA_HOME`，
  并用 `java -version` 解析版本；② PowerShell 7 的 `Invoke-WebRequest` 在响应无 charset 时
  `Content` 是 `byte[]`，`-match '"status":"UP"'` 会永远失败，需 `[Text.Encoding]::UTF8.GetString` 解码；
  ③ 脚本需 **UTF-8 with BOM**（仓库内 `.ps1` 均为 BOM，否则 PS 5.1 下中文输出乱码）。
- **求职者职位列表已切真实（T9 追加，2026-10-19）**：`JobsView` 原来用 `filterJobsSync`（`mock/filter.ts`，
  纯同步 mock 过滤）**绕过了 `listJobs` 的真实分支**，导致真实模式下 HR 发布 / 关闭职位对求职者无效。
  现改为 `useQuery(['jobs','list',filters,expectCat,expectSub], () => listJobs({..., limit:100}))`，\n  `mock/filter.ts` 已删除（`expectCat/expectSub` 入 key 是为了改动求职期望后自动重排：\n  Mock 分支读 localStorage 的 profile，真实分支由后端读库）。
  两个口径细节：① 演示规模要一次取全，必须显式 `limit=100`（后端默认 20、上限 100）；
  ② 「共 N 个职位」用后端 `pagination.total`，不要用 `list.length`（否则只显示当页条数）。
  后端 `q` 已同时匹配 `title / 公司名 / cat / sub / tags / area`，与 mock 的 haystack 一致，故
  「搜索公司名」在真实模式下同样可用。
- **仍是 Mock 驱动的真实分支（T9 剩余遗留，e2e 通过但不等于全链路真实）**：
  ① 分类字典（组件直接用 store 的 `CATEGORY_LIST` / `SUB_LIST_OF`，`categories.ts` 真实分支无人调用；
  后端 `/dict` 种子与 mock 同源，切了无可观测收益）；
  ② 求职者消息 / 会话（`store/pinliao.ts` 读 mock 播种，`conversations.ts` 真实分支不回写 store →
  真实模式「立即沟通」后的会话可能显示不存在）；
  ③ HR 侧 `ensureConversationForApplication`（真实分支直接抛 42210，后端在投递时已自动建会话，
  HR「发消息」入口改为复用 §4.4 卡片的 `conversationId` 即可）。
  ②③ 需要把 store 的会话模型改成 TanStack Query（未读 / 乐观发送 / 自动回复），且 e2e 对求职者消息页
  **零覆盖**——建议独立一轮（T10）并先补断言再动代码。
