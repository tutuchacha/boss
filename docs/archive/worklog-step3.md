# 工作日志 · Step 3 归档（Task 16~31）

> 归档于 2026-10-20。**默认不要读**；现行日志 [`../../worklog.md`](../../worklog.md)。

---
Task ID: 16
Agent: orchestrator (main)
Task: Step 3 后端开发规划 — 分块执行计划（T0~T9 任务板）与上下文交接协议（纯文档）

Work Log:
- 背景：用户启动 Step 3（Spring Boot 后端）开发；会话上下文预算有限（用户侧约 20w/会话），
  要求「任务分块 + 上下文触发后可持续接力」
- 环境盘点（2026-10-17 实测）：本机仅 JDK 1.8（JAVA_HOME=D:\JDK1.8，需装 Temurin 17）；
  Maven 3.6.3（用 mvnw 规避）；无 Docker；MySQL 服务已停止、无 PostgreSQL/Redis（端口全空闲）；
  winget v1.29 可用（Temurin 17 包已确认）
- 产出 `docs/step3-plan.md`（计划与任务板的唯一出处）：
  - 总验收：后端复刻演示语义（种子数字 / 固定验证码 / 认证 1.2s 自动通过 / 聊天自动回复等），
    以「前端 e2e 63/63 切真后端跑通」为收官
  - T0~T9 分块（环境骨架 / 迁移与种子 / 鉴权 / 职位字典 / 投递面试 / 公司认证 / 消息 WS /
    求职者简历收藏上传 / 招聘者与人才筛选移植 / 全量联调）；T10+ 增量另行
  - 上下文与交接协议：开工/收工 checklist、上下文将满应急流程、用户开场白模板、预算与省 token 技巧
- 同步：PROGRESS v1.5（快照 + 下一步指向计划）、AGENTS 速览、CHANGELOG 条目

Stage Summary:
- 下一步（用户执行）：装 JDK 17（`winget install EclipseAdoptium.Temurin.17.JDK`）、
  选定 PostgreSQL/Redis 安装方式（待拍板见 step3-plan §6）；然后开新会话从 T0 开工
- 接力方式：新会话按 step3-plan §5.2 开工 checklist - T0/T1 两轮内应可看到后端健康检查与种子数据

---

Task ID: 17
Agent: orchestrator (main)
Task: Step 3 T0 — 环境与工程骨架（Spring Boot 3.5.16 + 统一包络/异常/traceId + MySQL/Redis 专用实例）

Work Log:
- 环境决策（用户拍板）：单仓 backend/；JDK 用 IDEA 自带 JBR 21（免安装，本机原仅 JDK 1.8）；
  数据库改用 MySQL 8（替代计划中 PostgreSQL）；Docker 评估可行但需 WSL2 + 重启 1~2 次 + 镜像加速，
  用户选「免 Docker 直装」：复用本机 MySQL 8.0.29 二进制另起专用实例（3307，数据 .local/mysql），
  Redis 用 Windows 版 zip（tporadowski 5.0.14.1，.local/redis）
- 工程骨架：start.spring.io 已下架 Boot 3.x → 手工建 backend/（Boot 3.5.16 + Maven Wrapper 3.3.2/Maven 3.9.16；
  MyBatis-Plus 3.5.17、springdoc 2.9.1；Web/Validation/MySQL/Flyway/Data Redis/WebSocket/Actuator/Lombok）
- 统一层：ApiResponse 包络 + ApiCodes 错误码表（code→HTTP 前 3 位推导）+ GlobalExceptionHandler +
  TraceIdFilter（MDC / X-Trace-Id 响应头）+ ResponseAdvice；样例接口 /api/v1/_demo/{ping,error,validate}
- 本地环境脚本（backend/scripts）：_env / db-init / db-start / db-stop / redis-start / redis-stop / dev-up / dev-down
- 验收（实测 2026-10-17）：mvnw test 3/3；spring-boot:run 3.5s 启动、Flyway 连 MySQL 8.0（0 迁移 + 建 history 表）；
  /actuator/health 200 UP；ping 包络/traceId 一致；validate 422+42201+errors；error 404+40401；swagger 200
- 文档：step3-plan v1.1（T0 勾选、基线 MySQL、§6 结项）、PROGRESS v1.6、CHANGELOG、AGENTS 速览、AI-CONTINUE §6.8
- 提交：`dd7a948`（feat(backend): Step 3 T0 后端工程骨架与本地环境）

Stage Summary:
- T0 完成。后端本地可启动（:8080），MySQL(3307)/Redis(6379) 一键起停（dev-up/dev-down）。
- 下一步：T1 数据库迁移与演示种子（Flyway V1 建表 MySQL 方言 + V2 种子，数字对齐 step3-plan §3）

---

Task ID: 18
Agent: orchestrator (main)
Task: Step 3 T2a — 鉴权：验证码 / 密码·短信登录 / 刷新 / 退出 / me（§1.1、§1.3~§1.7）+ V1/V2 鉴权表与演示账号

Work Log:
- 用户指示：后端先完成「登入和退出」→ 本次 chunk = §1 除注册外的 6 条（注册拆为 T2b 顺延）
- 建表：V1__auth_schema.sql（users / user_profiles / companies / company_members / hr_profiles，
  MySQL 8，companyId 租户列 + 外键 + 索引）；V2__seed_auth_demo.sql（13800000001 求职者 / 13800000002 HR，
  密码 123456；云杉科技 co-acgiw7、HR hr-x2270n —— id 与前端 mock 哈希一致）
- 依赖：pom + jjwt 0.12.6（api/impl/jackson）+ spring-security-crypto（仅 BCrypt）
- 代码（com.pinliao.backend.auth）：AuthProperties/AuthConfig；JwtService（HS256，jti/role/companyId 声明）；
  AuthTokenStore（Redis refresh 白名单 30d + access jti 吊销名单）；SmsCodeService（固定 123456 / 5min /
  60s 限流 / 一次性消费）；AuthService（登录、刷新轮换、退出、me、会话装配 user+profile+company+member）；
  AuthInterceptor + UserContext（默认保护 /api/v1/**，白名单 sms/login/refresh/register/_demo）；
  AuthController 6 接口；ApiCodes + 40110/40111
- 测试：JwtServiceTest 4 项 + PasswordHashTest 1 项（种子 hash 回归）；EnvelopeSmokeTest 排除鉴权拦截器
  （WebMvcTest 切片会扫 HandlerInterceptor）；mvnw test 3/3 → 8/8
- 冒烟（backend/scripts/smoke-auth.mjs，入库）：14 项全过 —— 登录 200（云杉 company/member）、
  40110/40101/40102/40103/42201/42901 全码位、refresh 一次性轮换、退出后 me=40102；另手工验 40111（暂停账号）
- 数据库复验：flyway V1/V2 success=1；5 表行数正确；登录推进 last_login_at；发现 TINYINT(1) 弃用告警
  → 改 BOOLEAN 重建本地库重跑（日志干净）
- 文档：PROGRESS v1.7、step3-plan v1.2（T2 拆 T2a/T2b；T1 顺延 V3+）、CHANGELOG、AI-CONTINUE §6.8+v1.8、
  backend/README（鉴权命令与冒烟）
- 提交：`6f286e3`（feat(backend): Step 3 T2a 鉴权）

Stage Summary:
- T2a 完成：鉴权 6 接口在真实 MySQL/Redis 上可用（access 2h / refresh 30d / 验证码 123456）；
  受保护接口统一 40101/40102；T9 联调前无需再动鉴权主干
- 下一步（按用户指示二选一）：T1 剩余迁移（V3 建剩余表 + 种子对齐 §3 数字）或 T2b 注册（§1.2 + 注册建公司）

---

Task ID: 19
Agent: orchestrator (main)
Task: 文档同步 —— AGENTS 速览 / 后端 README / 根 README 对齐 Step 3 T2a 现状

Work Log:
- 用户指示「readme 文件也同步更新一下」
- `837f534`：AGENTS.md 速览区块更新至 T2a
- backend/README.md 已在 `6f286e3` 内更新（鉴权命令、冒烟、目录结构），本次复核无残留旧口径
- 根 README.md：阶段口径改「Step 2 验收版 + Step 3 进行中（T0 ✅ / T2a ✅）」；技术栈表加后端行；
  多租户说明改 MySQL 8（RLS 随 T0 定案作废）；数据库说明改 Flyway 迁移；常用命令加
  「后端（Step 3，进行中）」段；文档索引补 `docs/step3-plan.md`；启动提示词与开发路线表同步
- 提交：`29c21c1`（docs: 根 README 同步 Step 3 进度）

Stage Summary:
- 根/后端 README、AGENTS 速览三处口径与 T2a 现状一致；其余文档（PROGRESS/CHANGELOG/AI-CONTINUE/step3-plan）此前已同步
- 下一步不变（待用户拍板）：T1 剩余迁移（V3+）或 T2b 注册（§1.2 + 注册建公司）

---

Task ID: 20
Agent: orchestrator (main)
Task: Step 3 T1 — 数据库迁移与演示种子（V3 全量表 + V4~V8 种子对齐 mock + MyBatis-Plus 分页/租户/时间填充）

Work Log:
- 用户指示「继续进行（分阶段推进、完成后及时更新文档）」→ 本 chunk = T1（用户此前二选一未拍板，按计划依赖选 T1），
  内部拆 T1a/T1b 两次 feat 提交防上下文丢失
- 数据源侦察：自写 Node 24 ESM loader（`@/` 别名 + 无扩展名补 .ts）+ `--experimental-transform-types`
  直接 import `src/api/mock/*`，实证关键数字：职位 51 / 初始关闭 13 / 可见 38 / 云杉 7；
  投递 9（HR 6 + 求职者 3 非云杉）；会话 8（HR 6 未读 4 + 求职者 2）；简历 7；候选人 6
- T1a（`8ed73f8`）：
  - `V3__core_schema.sql`：12 张剩余业务表（jobs / resumes / favorites / applications / interviews /
    conversations / messages / resume_view_logs + cities / categories / sys_configs / tenant_configs /
    usage_logs / audit_logs），companyId 租户列 + 索引；hr 展示字段冗余（演示无全量 HR 账号）
  - MyBatis-Plus：分页（MySQL，≤200/页）+ `PinliaoTenantLineHandler`（HR 注入 company_id；求职者跨租户
    与全局表不注入；缺企业上下文访问租户表 fail-fast 40301）+ `PinliaoMetaObjectHandler`（UTC 自动填充）
  - 测试：`PinliaoTenantLineHandlerTest` 6 项；`mvnw test` 8/8 → **14/14**
- T1b（`85518bf`）：
  - `backend/scripts/seed-gen/`（loader-hooks + register + generate.mjs）从 mock 生成 V4~V8（文件带「勿手改」头）：
    公司 15 / 城市 9 / 分类 84；职位 51（可见 38、云杉 7，关闭规则同 mock stableHash%4）；候选人 6 + 简历 7；
    投递 9 + 面试 3；会话 8（HR 6 未读 4 + 求职者 2）+ 消息 16
  - `backend/scripts/verify-seed.ps1` — 16 项行数断言（.ps1 已加 UTF-8 BOM）
- 验收（实测 2026-10-18）：启动自动 migrate **V1~V8 成功**（3.3s，日志无告警）；`verify-seed.ps1` **16/16 命中**；
  `node scripts/smoke-auth.mjs` **14/14 无回归**；后端已停、MySQL 3307 与 Redis 6379 保持运行
- 文档：PROGRESS v1.8、step3-plan v1.3（T1 ✅ + §4 实测）、CHANGELOG、AI-CONTINUE §6.9、backend/README 命令

Stage Summary:
- T1 完成：库表与演示种子就绪（对齐 step3-plan §3 全部数字），种子由 mock 一键再生成（改数据改 mock 后重跑）；
  租户注入策略已定并被测试覆盖（求职者跨租户是产品语义，勿改成按表一刀切）
- 下一步：T2b 注册（§1.2 + 注册自动建公司「新注册企业-{手机尾4}」、40910/40110，照抄 mock `registerMockAccount` 语义）；
  随后 T3 职位与字典模块起

---

Task ID: 21
Agent: orchestrator (main)
Task: Step 3 T2b — 注册（§1.2；HR 自动建未认证公司；40910/40110）

Work Log:
- 语义侦察：mock `registerMockAccount`（用户/画像/公司/成员字段与文案）+ 前端 service 校验顺序
  （字段校验 → 已注册 40910 → 验证码 40110，失败不消耗）+ e2e 注册段（固定手机号 13900004321）
- 实现：
  - `ApiCodes.PHONE_REGISTERED = 40910`；`AuthRequests.RegisterRequest`（phone 正则 / password ≥6 / role 枚举）
  - `AuthService.register`（@Transactional）：校验顺序对齐 mock；HR 自动建「新注册企业-{尾4}」
    （未融资 / 1-19人 / 企业服务 / verified=false）+ 招聘官{尾4} + ADMIN 成员；求职者建「求职者{尾4}」画像；
    id 派生自手机号（u-reg-/up-reg-/co-reg-/hr-reg-/cm-reg-{phone}）——mock 递增计数刷新复位，落库需跨重启唯一
  - `AuthController` 新增 `POST /api/v1/auth/register`（白名单已含 register，无需改拦截器）
- 测试：`AuthServiceRegisterTest` 4 项（Mockito 假体；踩坑：无 Spring 上下文时 LambdaWrapper 报
  `can not find lambda cache` → 静态块 `TableInfoHelper.initTableInfo` 手工注册 4 个实体）；
  `mvnw test` 14/14 → **18/18**
- 冒烟：`smoke-auth.mjs` 新增注册段 6 项（14 → **20 项**）全对——发码 200 / 坏码 40110 / HR 成功
  （新注册企业-0259、ADMIN、verified=false）/ 重复 40910 / me 带新 token / 求职者无 company；
  边界手工验证：42201 ×3（phone/password/role）+ 未发码 40110
- `verify-seed.ps1`：users / companies 行数按前缀排除 `u-reg-*` / `co-reg-*`（冒烟注册不破坏 16 项断言，
  实测 16/16 仍全中）；后端已停、MySQL 3307 与 Redis 6379 保持运行
- 文档：PROGRESS v1.9、step3-plan v1.4（T2b ✅）、CHANGELOG、AI-CONTINUE §6.10（注册落库 / e2e 固定手机号
  重跑风险 / TableInfo 坑）、backend/README + AGENTS 命令更新
- 提交：`030a5b2`（feat: Step 3 T2b —— 注册）+ 紧后 docs 结算 `899d201`

Stage Summary:
- T2b 完成：注册链路可用且与 mock 语义一致（已注册 40910、验证码 40110、HR 未认证公司联动企业认证）；
  注册记录持久保留（演示库现有 u-reg-*/co-reg-* 等测试记录）
- 下一步：T3 职位与分类字典（§6/§7：列表/搜索/筛选/排序 + HR CRUD/概览；验收基线 = 可见职位 38 条）；
  T9 前需定 e2e 固定手机号对真实库重跑的重置策略（AI-CONTINUE §6.10，待用户拍板）

---

Task ID: 22
Agent: orchestrator (main)
Task: Step 3 T3a — 职位公开读与分类字典（§6.1~§6.4/§6.9 + §7，8 条）

Work Log:
- 侦察：契约 §6/§7、api-contract 分页规范、mock 语义（jobs.ts / hrJobs.ts / filter.ts / seekerJobs.ts /
  categories.ts / dict.ts）、前端 service 与类型形状、V3 表结构、V4/V5 种子数字
- 实现（T3 拆 T3a 公开读 / T3b HR 写；本块 T3a）：
  - `job/`：JobEntity（tags/welfare JSON → JacksonTypeHandler + autoResultMap）、JobMapper（公开浏览 SQL
    显式 @InterceptorIgnore(tenantLine="true")——HR 浏览也全平台；关键词 hay 对齐 mock）、JobQueryService
    （筛选/相似/埋点对齐 mock；recommend 期望二级岗 +2 / 一级类 +1 稳定排序；游标 base64 JSON {s,k,id}；
    详情 40410；ETag=id+updatedAt）、JobController / JobSearchController（§6.2 独立路径）
  - `dict/`：DictController + DictService（分类树 17 根/84 节点、城市 9、字典常量对齐 mock dict.ts）
  - `AuthInterceptor`：公开接口「可选鉴权」（方法 + Ant 路径判定；匿名放行、带 token 解析、写接口不放行）
- 测试：`mvnw test` 18/18 → **35/35**（职位 12 / 公开路径 2 / 字典 3）
- 冒烟：新增 `smoke-jobs.mjs` **29/29**（列表/筛选/搜索/游标翻页/加权排序/详情/相似/ETag 304/埋点去重/分类/城市/字典）；
  `smoke-auth.mjs` 20/20 无回归
- 开发期两坑（已记入 AI-CONTINUE §6.11）：@Select 非 <script> 不解析 `&lt;&gt;` 实体（改用 `!=`）；
  Pagination 的 @JsonInclude(NON_NULL) 吞 nextCursor:null
- 文档：PROGRESS v1.10、step3-plan v1.5（T3a ✅ + T3b 行）、CHANGELOG、AI-CONTINUE §6.11（含 T9 需加 Next rewrites 坑）、
  backend/README、AGENTS
- 提交：`9f509be`（feat: Step 3 T3a）+ 紧后 docs 结算

Stage Summary:
- T3a 完成：职位公开读 8 条接口全绿（可见 38 / 云杉 7 / 分类 84 与 mock 基线一致）；公开接口契约语义（匿名可读、
  登录加权、ETag 缓存）已落地；T9 联调前列出的 Next rewrites 缺失问题已记录待处理
- 下一步：T3b HR 职位写（§6.5~§6.8：发布/更新/关闭重开/软删；本公司校验 40310、配额 42211；冒烟收尾清理
  保持可见 38 基线）

---

Task ID: 23
Agent: orchestrator (main)
Task: Step 3 T3b — HR 职位写接口（§6.5~§6.8，4 条）

Work Log:
- 侦察：契约 §6.5~§6.8、mock hrJobs.ts（40310 / 40410 语义、软删 / 更新保留统计）、前端 service 与
  JobUpsertInput 形状、ApiCodes / JobCodes、租户注入策略（INSERT 的 ignoreInsert）、登录 claims（role/companyId）、
  HrProfileEntity、validation 与包络约定
- 实现：
  - `JobCommandService`：发布 / 更新 / 关闭·重开 / 软删；仅 HR / COMPANY_ADMIN 且带 companyId；
    归属公司取自登录上下文（不信任请求体）；不存在 / 非本公司 / 已软删统一 40310（不泄露存在性，对齐 mock）；
    配额 tenant_configs.max_jobs（缺省 50）→ 42211；发布限流 10 次/分钟/公司（Redis 自然分钟）→ 42901；
    PATCH 部分更新（null 保留原值，合并后校验 42201）
  - 新 id `job-new-{seq}`：库内最大序号 +1（`SUBSTRING(id, 9)`，显式 @InterceptorIgnore——id 全局唯一；
    软删保留行、序号不复用；禁时间戳/随机）；`publishedAt` 截毫秒（DATETIME(3)）
  - `JobController`：POST /jobs、PATCH /jobs/{id}、POST /jobs/{id}/close|reopen、DELETE /jobs/{id}；
    `JobViews.HrRecord` 对齐 HrJobRecord；`JobRequests.Upsert`；JobCodes 40310 / 42211；JobMapper 配额与序号查询
- 测试：`JobCommandServiceTest` 15 项（创建成功/角色/上下文/配额/限流/校验/更新保留统计/越权/软删幂等/关闭重开）；
  `mvnw test` 35/35 → **50/50**
- 冒烟：`smoke-jobs.mjs` 29 → **49/49**（未登录 401 / 求职者 40301 / 42201 / 发布归属云杉 + OPEN /
  可见 39 / 更新保留发布时间统计 / 关闭后 404 且回到 38 / 重开 / 他公司 HR 与不存在 40310 / 软删 {} + 404 /
  收尾回到 38 基线 + 兜底幂等清理）；`smoke-auth.mjs` 20/20、`verify-seed.ps1` 16/16 无回归（排除 job-new-%）
- 踩坑记录（AI-CONTINUE §6.12）：租户插件 INSERT 的 ignoreInsert 行为；新 id 必须全局取最大；
  publishedAt 纳秒 vs DATETIME(3)；配额缺省 50；40310 统一语义；冒烟软删行与 verify-seed 排除
- 文档：PROGRESS v1.11、step3-plan v1.6（T3b ✅ + §4 实测 + 下一步 T4）、CHANGELOG、AI-CONTINUE §6.12、
  backend/README、AGENTS
- 提交：`8102f0b`（feat: Step 3 T3b）+ 紧后 docs 结算

Stage Summary:
- T3b 完成：HR 职位写 4 条接口全绿（发布 / 更新 / 关闭重开 / 软删；40310 / 42211 / 42901 语义落地）；
  冒烟新建职位收尾软删，可见 38 基线保持；三个冒烟 / 单测基线全部无回归
- 下一步：T4 投递与面试闭环（§9/§10，10 条；状态机 + 会话按需创建（先占位）+ 校验码
  40911 / 40913 / 40914 / 40915）；T9 前仍需用户拍板 e2e 固定手机号对真实库的重置策略

---

Task ID: 24
Agent: orchestrator (main)
Task: Step 3 T4 — 投递与面试闭环（§9/§10，10 条 + §8.7 约面试）

Work Log:
- 侦察：契约 §9/§10/§8.7 与 §4.4、mock applications.ts（状态机 / 409xx 语义 / ensureConversation）、
  双端 service 出参期望、types Application*/Interview*/Resume/Message、V3 表结构、V7/V8 种子、
  verify-seed、JobViews/JobQueryService 映射、UserContext/AuthInterceptor/租户策略
- 决策：投递成功即幂等占位建会话（conv-new-*），ApplicationDetail 增 conversationId 供 HR 触达 / 约面；
  新 id 统一 app-new/int-new/conv-new/msg-new 递增派生；§8.7 按契约返回 Message（前端声明 Interview 但未用返回值）
- 实现：
  - application/：ApplicationEntity / InterviewEntity / ConversationEntity / MessageEntity / ResumeEntity +
    Mapper（nextNewSeq 全局取最大，显式 @InterceptorIgnore）；ApplicationViews（Application / WithJob /
    Detail / Resume / Interview / InterviewWithJob / InterviewWithCandidate / Message）；ApplicationCodes
  - ApplicationCommandService：§9.1 投递（默认简历 / 42210 / 40911 / 撤回重投复用）、§9.4 撤回（40913）、
    §9.5 HR 状态（42201 / 40913）、§8.7 约面试（40914 / 40915 / 42201 / 42210 + 建面试 + INTERVIEW 状态 +
    INTERVIEW_INVITE 消息 + 未读 +1）、§10.4/§10.5 接受拒绝（40410 归属 / 幂等终态）
  - ApplicationQueryService：§9.2 我的投递、§9.3 详情（job/resume/interviews/conversationId）、§10.1/§10.2
    双端列表、§10.3 详情；游标 base64 JSON {k,id}，updatedAt/createdAt DESC + id ASC；状态筛选
  - ApplicationController（10 条 + §8.7）；Roles 角色守卫；PageViews 游标分页；JobQueryService 增
    summaryOf/detailOf 复用职位映射
- 测试：新增 ApplicationCommandServiceTest 22 项 + ApplicationQueryServiceTest 5 项；
  `mvnw test` 50/50 → **77/77**
- 冒烟：新增 smoke-applications.mjs **51/51**（投递 / 重复 40911 / 游标 / 详情 / 越权 40410 / 状态 /
  40914 / 40915 / 42201 / 42210 / 约面消息 / 双端面试 / 接受拒绝幂等 / 撤回重投；连续两次通过）；
  smoke-jobs 49/49、smoke-auth 20/20、verify-seed 16/16 无回归（排除 app-new/int-new/conv-new/msg-new）
- 踩坑记录（AI-CONTINUE §6.13）：投递建会话与 T6 复用；状态机判断顺序；接受/拒绝幂等；
  40410 归属隔离；四类 id 前缀与 DATETIME(3) 截毫秒；冒烟可重复；T9 分页/详情/§8.7 前端契约差异
- 文档：PROGRESS v1.12、step3-plan v1.7（T4 ✅ + §4 实测 + 下一步 T5）、CHANGELOG、AI-CONTINUE v1.13、
  backend/README、AGENTS
- 提交：`d4ce22c`（feat: Step 3 T4）+ 紧后 docs 结算

Stage Summary:
- T4 完成：投递与面试闭环 10 条 + §8.7 全绿；状态机 / 校验码 / 归属隔离 / 会话占位落地；单测 77/77、
  冒烟 51/51 可重复，其余基线无回归
- 下一步：T5 公司模块 + 企业认证（§5，6 条；资料编辑改名同步职位公司名 + 认证提交 / 1.2s 自动通过 / 轮询）；
  T9 前仍需用户拍板 e2e 固定手机号对真实库的重置策略

---

Task ID: 25
Agent: orchestrator (main)
Task: Step 3 T5 — 公司模块 + 企业认证（§5.1~§5.6，6 条）

Work Log:
- 侦察：契约 §5 六条、前端 company service / mock companyOps / 认证页轮询、CompanyEntity / CompanyMemberEntity、
  V1 companies / company_members 表、V2 演示 HR 成员、V4 公司种子、JobMapper / JobQueryService 装配、
  AuthInterceptor 公开路径与 Roles 守卫
- 决策：
  - §5.4~§5.6 的 COMPANY_ADMIN 以 `company_members.role=ADMIN` 为准；演示 HR user.role=HR、成员角色 ADMIN，
    不能直接判 token role。§5.3 仍允许 HR / COMPANY_ADMIN。
  - 认证提交只更新 companies.verified/status，不新增提交记录表（契约不暴露、数据模型无表）；四项资料仅校验。
  - 认证自动通过用 daemon ScheduledExecutorService（默认 1200ms，`pinliao.company.verify-delay-ms` 可覆盖）；
    后台再次确认 PENDING，避免覆盖人工禁用。
  - 公司改名不刷 jobs 行：职位公司名从 companies 表实时装配，§5.1/§5.2/§6.x 自动同步。
  - 公司职位仅 OPEN 且未软删；公司不存在统一 40410；游标沿用 §6.1 格式（score 固定 0）。
- 实现：
  - company/：CompanyCodes（40410 / 40916 / 42210）、CompanyRequests（Patch / Verify）、CompanyService
    （§5.1 详情、§5.2 职位、§5.3 我的公司、§5.4 部分更新、§5.5 成员 + user/hrProfile、§5.6 认证提交 /
    自动通过）、CompanyController
  - JobMapper.selectVisibleByCompany + JobQueryService.listByCompany（公司职位游标分页）
  - AuthInterceptor 公开路径新增 GET /companies/*、GET /companies/*/jobs；单测补充匿名放行 / 受保护断言
  - 单测：CompanyServiceTest 12 项（公开读 / 404 / 权限 / trim / 校验 / 成员 / PENDING→异步 ACTIVE / 40916）+
    JobQueryServiceTest 公司职位分页 1 项；异步用例延迟 30ms + Mockito timeout 2s
  - 新增 `backend/scripts/smoke-company.mjs` 26 项：公开详情 / 分页 / 40410、我的公司 / 40301、
    临时 co-reg-* HR 注册 ADMIN、名称校验 / 改名 / 公开详情同步、成员 user+hrProfile、
    临时 job-new-* 验证职位公司名同步（finally 软删）、认证 42210 / PENDING→轮询 ACTIVE / 40916
- 验收（实测）：
  - `mvnw.cmd test` **90/90**（T4 基线 77 + 公司 12 + 公司职位分页 1）
  - `smoke-company.mjs` **26/26**，连续两次执行均通过
  - `smoke-jobs` 49/49、`smoke-applications` 51/51、`smoke-auth` 20/20、`verify-seed` 16/16 无回归
- 文档：PROGRESS v1.13、step3-plan v1.8（T5 ✅ + §4 实测 + 下一步 T6）、CHANGELOG、
  api-endpoints v0.3.3（§5 权限 / 40410 / OPEN 口径）、AI-CONTINUE v1.14（§6.14）、backend/README、AGENTS
- 提交：`2359625`（feat: Step 3 T5）+ 紧后 docs 结算

Stage Summary:
- T5 完成：公司模块 6 条全绿；公开读 / 资料编辑 / 成员 / 企业认证异步自动通过落地；
  管理员语义按成员角色 ADMIN；单测 90/90、公司冒烟 26/26 可重复，其余基线无回归
- 下一步：T6 消息 + WebSocket（§8，8 条 + 4 主题；复用 T4 的 conv-new-* 占位会话）；
  T9 前仍需用户拍板 e2e 固定手机号对真实库的重置策略

---
Task ID: 26
Agent: orchestrator (main)
Task: Step 3 T6 — 消息 + WebSocket（§8.1~§8.8，8 条 + 4 主题）

Work Log:
- 侦察：契约 §8 / §10、前端 conversations / hrConversations mock 语义、ChatPage 取数形状、
  ConversationEntity / MessageEntity / CompanyMember / HrProfile、T4 的 conv-new-* 占位会话
- 决策：
  - 未读计数以 conversations.user_unread_count / hr_unread_count 为持久化事实源；Redis 只做
    发送限流（30 次/分钟/用户）与 idempotencyKey 24h 去重，重启不丢未读。
  - 会话列表返回双端超集（conversation / job / hr / company / candidate / lastMessage），
    同一端点按角色取用；补契约外 GET /conversations/{id} 给 HR 聊天顶栏（不计账）。
  - 历史按 createdAt DESC, id ASC 游标；列表按 lastMessageAt DESC, id ASC。
  - 发起会话 (jobId,userId) 唯一，重复 40912；职位不存在 / 下线 40410；firstMessage 写 SYSTEM + TEXT。
  - WebSocket 用 /ws STOMP；query token 为主，兼容 Authorization / STOMP header；新消息只推对方，
    避免发送方回显重复；typing 同时推用户队列与会话主题；错误推 /user/queue/errors。
  - 自动回复双向：USER TEXT → HR 关键词回复；HR TEXT → 候选人关键词回复；先 typing=true，
    默认 1.2s 后落库并推消息 + typing=false，延迟可配 pinliao.message.auto-reply-delay-ms。
- 实现：
  - message/：MessageCodes、MessageRequests、MessageViews、ConversationQueryService /
    ConversationCommandService / ConversationAutoReplyService、ConversationController、
    MessageRealtime + StompMessageRealtime、WebSocketConfig、WsHandshakeInterceptor /
    WsAuthChannelInterceptor / WsPrincipal、MessageWebSocketController
  - ApplicationCommandService 注入 MessageRealtime，T4 INTERVIEW_INVITE 消息同步推送求职者
  - 单测 24 项：命令 15（发起 / 越权 / 校验 / 幂等 / 限流 / 已读 / typing / 关闭 / 简历）+
    查询 6（双端列表 / 游标 / 历史 / 越权 / 非法游标）+ 自动回复 3（双向 / 关键词 / 默认）
  - 新增 backend/scripts/smoke-messages.mjs 40 项：REST 全链路 + WS 推送 + STOMP 发送 /
    typing + 错误队列 + 非法 token；只写 conv-new-* / msg-new-*，保护种子未读 4 / 2
- 验收（实测）：
  - `mvnw.cmd test` **114/114**（T5 基线 90 + 消息 24）
  - `smoke-messages.mjs` **40/40**，连续两次执行均通过；含真实 STOMP 帧往返
  - `smoke-company` 26/26、`smoke-jobs` 49/49、`smoke-applications` 51/51、`smoke-auth` 20/20、
    `verify-seed` 16/16（HR 会话 6 / 未读 4，求职者 2，消息 16）无回归
- 文档：PROGRESS v1.14、step3-plan v1.9、CHANGELOG、api-endpoints v0.3.4、api-contract v0.3.1、
  AI-CONTINUE v1.15（§6.15）、backend/README、AGENTS、README、PROJECT、worklog
- 提交：`8d9513a`（feat: Step 3 T6）+ 紧后 docs 结算

Stage Summary:
- T6 完成：§8 消息 8 条 + /ws STOMP 实时通道全绿；DB 未读事实源、双向自动回复、幂等 / 限流、
  双端超集列表与扩展详情落地；单测 114/114、消息冒烟 40/40 可重复，其余基线无回归
- 下一步：T7 求职者 / 简历 / 收藏 / 上传（§2/§3/§11/§12，19 条）；T9 前仍需用户拍板
  e2e 固定手机号对真实库的重置策略与 67/70 接口账目口径

---

Task ID: 27
Agent: orchestrator (main)
Task: Step 3 T7 — 用户 / 简历 / 收藏 / 上传（§2/§3/§11/§12，19 条）

Work Log:
- 侦察：契约 §2/§3/§11/§12、`docs/api-contract.md` §9 上传限制、前端 mock 的
  profile / resumes / favorites / upload 形状、T1 的 users / user_profiles / resumes / favorites 表。
- 决策：
  - 外观偏好不新增表：Redis `pinliao:user:prefs:{userId}` 存 JSON，默认 auto；接口保留多端同步能力。
  - 重置密码验证码场景 RESET；改手机号场景 LOGIN（新号可能未注册）；均在业务冲突校验之后消耗验证码。
  - 注销为软删：`users.status=DELETED` + `deleted_at`，并吊销当前 accessToken；暂不做恢复接口。
  - 简历权限：求职者仅本人；HR 仅当 `applications.resume_id` 指向该简历且 `company_id` 为当前公司，
    越权统一 40410。首份自动默认；删除默认时把最近更新的剩余简历设为默认并同步
    `user_profiles.default_resume_id`。
  - 收藏 id `fav-{userId}-{jobId}`，`(user_id, job_id)` 唯一；重复 40910，取消幂等；
    列表 `createdAt DESC, id ASC` 游标 + 全量 total。
  - 上传用本地目录替代 OSS：服务端生成 objectKey，HMAC 预签名 PUT，确认后 `/uploads/**` 静态访问；
    未新增上传元数据表，confirm 只校验归属 / 存在 / 大小，业务关联由后续 PATCH 完成。
- 实现：
  - user/：UserController / UserService / UserRequests / UserViews / UserCodes；8 条接口。
  - resume/：ResumeController / ResumeService / ResumeRequests / ResumeViews / ResumeCodes；6 条接口。
  - favorite/：FavoriteController / FavoriteService / FavoriteMapper / FavoriteEntity / 视图；3 条接口。
  - upload/：UploadController / UploadService / UploadConfig / UploadProperties；2 条接口 + 本地 PUT。
  - WebAuthConfig 放行密码重置与本地直传；application.yml 增 `pinliao.upload.dir` / `url-ttl`。
  - 单测 37 项：用户 13 + 简历 10 + 收藏 5 + 上传 9。
  - 新增 `backend/scripts/smoke-user.mjs` 64 项；临时 `u-reg-*` 账号隔离，RESP 清 Redis 验证码冷却，
    finally 清理简历 / 收藏；`verify-seed.ps1` 的 resumes 排除 `u-reg-*` 与软删行。
- 验收（实测）：
  - `mvnw.cmd test` **151/151**（T6 基线 114 + T7 37）
  - `smoke-user.mjs` **64/64**，连续两次执行均通过；19 条接口、错误分支、收藏两页、真实 PUT 直传与静态读回
  - `smoke-auth` 20/20、`smoke-jobs` 49/49、`smoke-applications` 51/51、`smoke-company` 26/26、
    `smoke-messages` 40/40（连续两次）、`verify-seed` 16/16 无回归
  - 修复 `smoke-messages` 上一轮 WebSocket 自动回复迟到导致的计数抖动：开头 / 结尾各等待 1.8s
- 文档：PROGRESS v1.15、step3-plan v1.10、CHANGELOG、api-endpoints v0.3.5、api-contract v0.3.2、
  AI-CONTINUE v1.16（§6.16）、
  backend/README、AGENTS、README、PROJECT、worklog
- 提交：`c26dc44`（feat: Step 3 T7）+ 紧后 docs 结算

Stage Summary:
- T7 完成：§2 用户 8 条 + §3 简历 6 条 + §11 收藏 3 条 + §12 上传 2 条全部落地；偏好 / 软删注销 /
  HR 简历可见性 / 默认顺延 / 收藏游标 / 本地 HMAC 上传；单测 151/151、T7 冒烟 64/64 可重复，
  其余冒烟与种子 16/16 无回归
- 下一步：T8 招聘者 / 候选人 / 人才筛选（§4，6 条；重点 §4.1/§4.2 HR 画像 + §4.5/§4.6 关键词匹配）；
  T9 前仍需用户拍板 e2e 固定手机号对真实库的重置策略与 67/70 接口账目口径

---

Task ID: 28
Agent: orchestrator (main)
Task: 文档结算 — 接口账目 / T9 重置策略 / T8→T9 顺序（用户拍板）

Work Log:
- 决策（用户拍板，2026-10-18）：
  - 接口账目以正文 70 条为准：总览表鉴权 6→7、职位 9→10（§6.7 close/reopen）、消息 8→9
    （§8.7 send-resume / invite-interview），分模块合计 70。
  - T9 e2e 固定手机号重置策略：每轮 e2e 前清库重迁（重建 + Flyway V1~V8），
    `13900004321` 可反复使用；重置脚本在 T9 落地。
  - T8/T9 顺序：**T8 先、T9 后**。`NEXT_PUBLIC_USE_MOCK` 为全局开关、无模块级粒度，
    先补齐 §4 再整体切真实接口，避免 T9 二次返工与验收范围打折。
- 文档：PROGRESS v1.16、step3-plan v1.11、api-endpoints v0.3.6、AI-CONTINUE v1.17/v1.18、
  CHANGELOG（新增本次结算段）、AGENTS（待确认清单移除已决项）、worklog。
- 顺带修复 CHANGELOG / worklog 中 T7 文档行遗留的字面 `\n` 换行错误。
- 提交：`docs: 决策结算（接口账目 70 条 / T9 重置策略 / T8→T9 顺序）`。

Stage Summary:
- 三项待决事项全部定稿并入库，无代码变更；工作区回到干净状态（除自动维护的 AGENTS/CLAUDE 区块）。
- 下一步：T8 开工——先核对 §4.3/§4.4 现状，再补 §4.1/§4.2 HR 画像与 §4.5/§4.6 人才筛选移植。

---

Task ID: 29
Agent: orchestrator (main)
Task: Step 3 T8 — 招聘者 / 候选人 / 人才筛选（§4.1~§4.6）

Work Log:
- 侦察（先读后写）：
  - §4.1/§4.2/§4.5/§4.6 后端完全缺失；§4.3/§4.4 只有可复用能力（T3b 职位写、T4 面试、
    §9.5 状态、`/hr/interviews`），未按 §4 出参组装。
  - 关键发现：`hr_profiles` 带 `company_id` 但**不在** `PinliaoTenantLineHandler` 的租户注入表集合内
    （该表按 `user_id` 唯一），§4.1/§4.2 必须显式按 `user_id` 归属，否则会读错人 / 读不到。
  - `GET /hr/jobs/{jobId}` 为契约外但必需：公开详情 §6.3 只服务 `OPEN`，HR 编辑页要打开 CLOSED。
- 实现（遵用户拍板的两条口径）：
  - §4.3/§4.4 出参给前端**超集**（`HrJobRecord[]` / `HrApplicationView[]`），换取 T9 前端零适配；
    `cursor` 保留未启用，`limit` 作 1~100 上限保护。
  - 契约外参数接受为可选：§4.3 的 `q`/`hrId`、§4.4 的 `q`（对齐前端 mock 筛选行为）。
  - `recruiter/`：`RecruiterCodes`（40410 / 42210）、`RecruiterRequests`（`HrProfilePatch` /
    `CandidateMatchInput`）、`RecruiterViews`（`ApplicationCard` / `Candidate` / `JobBrief` /
    `CandidateMatch` / `Evidence`）、`HrProfileService`、`HrApplicationQueryService`、
    `CandidateSummaryService`（与 T6 `ConversationQueryService.candidateOf` 同口径、完善度 85 基线）、
    `TalentMatchService`、`RecruiterController`。
  - `HrJobQueryService`（§4.3 列表 + 详情）；`JobViews.HrRecord.of` 抽出唯一映射源并让
    `JobCommandService.toRecord` 委托（删掉重复的私有格式化方法）；`JobMapper` 增加
    `selectByCompanyAnyStatus` / `selectForCompany`（含 CLOSED、排除软删、保留租户注入）。
  - `TalentMatchService` 逐条移植 `src/api/mock/talentMatch.ts`：词典长词优先 + 空格占位防子词重复、
    英文技术词 `[A-Za-z][A-Za-z0-9+#.-]*` 去尾 `[.-]+`、中文先去停用词再取 `[\u4e00-\u9fa5]{2,}`、
    大小写不敏感去重保留首次大小写、技能 3 / 文本 2 取最大、证据 ≤3/词与 ≤6/人、score 倒序后按
    完善度倒序；空条件 42210，条件非空但提不出关键词则返回空数组（对齐 mock）。
- 测试：
  - 新增单测 22 项（招聘者 22 = 画像 4 + 我发布的职位 3 + 收到的投递 4 + 人才筛选 11）。
  - 新增 `smoke-recruiter.mjs` 50 项：§4.1~§4.6 主链路 + 错误分支；§4.2 改后按原值还原；
    §4.3 现场创建 `job-new-*` → close → 验证「HR 可见 CLOSED 而公开详情 40410」→ 软删并回到基线。
- 实测：`mvnw test` **173/173**（151→173）；`smoke-recruiter` **50/50**（重复执行通过）；
  `smoke-auth` 20/20、`smoke-jobs` 49/49、`smoke-applications` 51/51、`smoke-company` 26/26、
  `smoke-messages` 40/40、`smoke-user` 64/64、`verify-seed` 16/16 无回归。
- 踩坑（已写入 AI-CONTINUE §6.17 / PROGRESS §6.3）：
  - 本机演示库存在历史冒烟残留：早期 T4 留下一条**可见**的 `app-new-*` 投递（`u-seeker-1` 投云杉），
    使 §4.4/§4.5 结果多一位候选人。因此计数类断言改为「种子齐备 + 容忍残留」；T9 e2e 前必须按
    §6.10 清库重迁，否则候选人数与 mock 基线不一致。
  - `smoke-auth` 在 60 秒内连跑会因验证码冷却 429 而失败（脚本自身语义，非回归）。
- 文档：PROGRESS v1.17、step3-plan v1.12、api-endpoints v0.3.7、AI-CONTINUE v1.19（§6.17）、
  CHANGELOG、worklog。
- 提交：`9cf3ae0`（feat: Step 3 T8 招聘者模块）+ 紧后 docs 结算。

Stage Summary:
- T8 完成：§4.1~§4.6 六条全部落地，「HR 可见已关闭职位」「超集出参」「关键词匹配与证据片段」均已实证；
  单测 173/173、招聘者冒烟 50/50，其余冒烟与种子 16/16 无回归；前端 e2e 63/63 基线未动。
- 下一步：T9 全量联调——先落地「清库重迁」重置脚本，再切 `USE_MOCK=false`，逐项修差异至 e2e 63/63。

---

Task ID: 30
Agent: orchestrator (main)
Task: Step 3 T9 — 全量联调（切 `USE_MOCK=false`，目标 `npm run e2e` 63/63）

Work Log:
- 前置（用户拍板后开工）：
  - 顺序为「先 T8 后 T9」（2026-10-18 已定），本轮直接把前端整体切到真实后端。
  - 开始前先做差异侦察：`next.config.ts` 无 `/api/v1` 代理、7 个 service 真实分支与后端出参不符；
    另派 explore 子代理做「前端真实分支 × 后端 view/DTO」逐条核对（产出 M1~M7 必改 + N1~N9 可缓）。
- 联调底座：
  - `next.config.ts` 加 rewrites：`/api/v1/:path*` 与 `/uploads/:path*` → `API_PROXY_TARGET`（默认 :8080）。
    前端 `client.ts` 只发相对路径，代理同时解决跨域与上传 URL 拼接（不必拼绝对地址）。
  - `.env.local`（不提交）：`NEXT_PUBLIC_USE_MOCK=false`、`NEXT_PUBLIC_API_BASE_URL=/api/v1`。
  - 新增 `backend/scripts/db-reset.ps1`：按 8080 端口止后端 → `DROP + CREATE pinliao` → Redis `FLUSHALL`
    → 起后端 → 等 `/actuator/health` UP → 回读 `flyway=8 / companies=16 / jobs=51 / applications=9`；
    `-NoRestart` / `-KeepRedis` 可调。实测 3 轮可用（含首次踩的 JDK 与 health 解码两个坑）。
- 修差异（先跑一轮拿实测清单：**6/61**，根因是演示账号区被隐藏）：
  - 「演示账号（一键填入）」与固定验证码提示原先被 `USE_MOCK &&` 包住，切真实后端整块消失 → e2e 点不到账号，
    登录失败后 61 项连锁 FAIL。改为**始终展示**（原型演示需要），删掉 `USE_MOCK` 导入。
  - `auth.ts` 真实分支补 `assertRoleMatches()`（`persistTokens` 之前）：原实现只有 mock 分支校验身份，
    真实分支用 HR 手机号选「求职者」能直接进 HR 工作台（违反 AGENTS §2.3）。
  - 列表统一解包 `.list`：`listMyApplications` / `listHrConversations` / `listHrMessages`。
  - `listMyApplications` 额外按 `applicationId` 合并 §10.1 面试（原真实分支 `interview` 恒为 null）。
  - `getHrApplication` 改走 §4.4 列表按 id 定位（§9.3 返回 `ApplicationDetail`，与 HR 详情页形状不符）。
  - `inviteInterview` 返回类型改 `Interview | Message`；`HrInterviewInvitePage` 透传 `conversationId`
    （§8.7 路径必需，否则 42210）。
  - `HrChatPage` 真实模式不再调 mock 专用 `replyAsCandidate`（会抛错并跳过收尾逻辑），改为定时重取
    （1200ms / 2600ms 两次，覆盖后端 1.2s 自动回复；前端未建立 WS 连接）。
  - `tests/e2e-verify.cjs`：「错误密码被拒绝」用例在真实后端必然产生 1 条 HTTP 401 控制台日志
    （契约行为），质量门禁**只豁免 1 条**该形态的 401，其余仍失败；Mock 模式无此日志，63 项数不变。
- 实测：
  - 前端 `npm run e2e` 真实后端：先 62/63（仅差上述 401），加豁免后 **63/63**；随后「清库重迁 + e2e」
    再跑一轮仍 **63/63**（连续两轮可重复）。
  - `npx tsc --noEmit` 仍仅 3 个既有错误；后端代码本轮未改，故 `mvnw test` 173/173 与七项冒烟
    （20/49/51/26/40/64/50）、`verify-seed` 16/16 保持。
- 踩坑（已写入 AI-CONTINUE §6.18）：
  - **演示专用 UI 被全局开关隐藏**是本轮最大坑：排查同类问题先看 `USE_MOCK` 包了什么。
  - `db-reset.ps1` 三个坑：JDK 探测必须校验 major ≥ 17（本机 `JAVA_HOME=D:\JDK1.8` 会让 shade 插件炸）；
    PowerShell 7 的 `Invoke-WebRequest` 无 charset 时 `Content` 是 `byte[]`（health 判定需自行解码）；
    `.ps1` 必须 UTF-8 **with BOM**（仓库约定）。
  - e2e 可重复的前提是「先重迁再跑」：固定手机号 `13900004321` 已注册会 40910、验证码 60s 冷却会 42901。
- 文档：新建 `ARCHITECTURE.md` §7「后端实现（Step 3）」（原 §7 修订记录顺延为 §8）；
  PROGRESS v1.18（§0 快照 / §1 表 / §4 T9 实测 + 遗留 / §6.3 四条 / §7.1 §7.2 命令与结果 / §8）；
  step3-plan v1.13（T9 行 ✅、§4 T9 实测、§7）；AI-CONTINUE v1.20（§2 状态、§6.11、§6.13、新增 §6.18
  十四条、§9 补「T9 遗留真实分支」待确认、§10）；api-endpoints 本次无契约变化（T9 只改前端 / 脚本 / e2e）；
  `backend/README.md`（db-reset + smoke-recruiter + 基线 + 联调步骤）；AGENTS.md §0/§1；CHANGELOG；worklog。

Stage Summary:
- T9 完成：前端在**真实后端**下 `npm run e2e` **63/63**（连续两轮），联调开关与清库重迁流程均已脚本化；
  新增 `db-reset.ps1`、Next 代理、7 处前端差异修复与 e2e 的 1 条精确豁免。
- 遗留（**待用户拍板**）：求职者端「职位列表 / 分类字典 / 消息会话」仍读 mock（`jobs.ts#listJobs` /
  `categories.ts` / `conversations.ts` 真实分支无调用方），HR「发消息」入口仍为 mock 专用；切换前需评估
  e2e 跨端断言（依赖前端「关闭 / 新建职位联动」的 mock 合成逻辑）。

---

Task ID: 31
Agent: orchestrator (main)
Task: T9 追加 — 求职者职位列表切真实后端（用户选方案 A）

Work Log:
- 背景：T9 后求职者职位**列表**仍走 `filterJobsSync()`（`src/api/mock/filter.ts`，纯同步 mock 过滤），
  `jobs.ts#listJobs` 的真实分支无人调用 → 真实模式下 HR「发布 / 编辑 / 关闭职位」对求职者**不生效**
  （详情页已走 `getJobDetail()`，所以是「点进去 404 / 列表还在」的割裂）。用户拍板选 A：只切职位读路径。
- 改动（2 个文件）：
  - `JobsView.tsx`：`useMemo + filterJobsSync` → `useQuery(['jobs','list',filters], () => listJobs({...}))`；
    `limit: 100` 单页取全（后端 `DEFAULT_LIMIT=20 / MAX_LIMIT=100`，可见 38 条），
    「共 N 个职位」改用 `data.pagination.total`（Mock 分支同样返回 total，两模式等价）；
    `isPending` 时渲染「加载中…」，避免首帧误显示「没有符合条件的职位」。
  - 删除 `src/api/mock/filter.ts`（仅 `JobsView` 引用，grep 确认零残留）。
- 可行性依据（读码确认，未改后端）：`GET /jobs` 的 `q` 同时 LIKE `title / 公司名 / cat / sub / tags / area`，
  `city/cat/sub/exp/sal` 与 mock 同名同义，`sort=recommend` 同样「期望二级=2 / 期望一级=1」加权；
  service 层透传 `Pagination.total`，故前端无需任何口径适配。
- 实测：
  - 真实后端：`db-reset.ps1` 后 `npm run e2e` **63/63**（「可按公司搜索到职位 → 7 条」现为真实数据；
    「已关闭职位对求职者不可见」由弱断言变为真断言）。
  - Mock 基线：临时把 `.env.local` 改名并重启 dev server，`npm run e2e` 仍 **63/63**（证明 mock 分支等价），
    随后恢复 `.env.local` 并重启（**`NEXT_PUBLIC_*` 构建期内联，改完必须重启**）。
  - `npx tsc --noEmit` 仍仅 3 个既有错误；`eslint src/components/pinliao/JobsView.tsx` 零告警。
- 文档：PROGRESS v1.19（§0/§1/§4/§6.3/§8）、step3-plan v1.14（状态行 / §4 T9 追加 / §7）、
  AI-CONTINUE v1.21（§2、§6.18 改写末条 + 新增切真实条目、§9、§10）、ARCHITECTURE v1.4
  （§3 目录树去 `filter.ts`、§6.4 改为「取数方式已基本统一」、§7.7）、README、AGENTS §0、CHANGELOG（T9 追加）、worklog。

Stage Summary:
- 求职者「职位列表 / 详情」现全链路真实：HR 发布 / 编辑 / 关闭职位对求职者立即生效，Mock 与真实后端
  两套模式 `npm run e2e` 均 63/63。
- 仍待拍板（建议独立一轮 T10）：求职者分类字典、求职者消息会话、HR「发消息」入口；其中消息页
  目前 e2e **零覆盖**，应先补断言再切。

---

