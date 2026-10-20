# 踩坑 · 后端核心（§6.8~§6.14）

> 摘自 `docs/AI-CONTINUE.md` §6（2026-10-20 拆分，编号未变）。索引见 [`../AI-CONTINUE.md`](../AI-CONTINUE.md) §6。
> 接口层面的「现在长什么样」（而非踩坑）见 [`../state/backend-facts.md`](../state/backend-facts.md)。

---
### 6.8 后端本地环境（Step 3 T0 起）

- **JDK 用 IDEA 自带 JBR 21**：本机系统仅 JDK 1.8，构建 / 运行 `mvnw` 前必须先设
  `$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr'`，
  否则报 JAVA_HOME 不合法。不装 Temurin（原计划作废）。
- **MySQL 专用实例 3307**：复用本机 MySQL 8.0.29 二进制 + 独立数据目录 `.local/mysql/data`
  （root/123456，库 `pinliao`）；**3306 是本机原服务（自动启动），不要连**。首次初始化
  `backend/scripts/db-init.ps1`（可重入），日常 `dev-up.ps1` / `dev-down.ps1` 一键起停。
- **Redis 6379**：tporadowski Windows 版（5.0.14.1）解压于 `.local/redis`，`redis-start/stop` 脚本管理。
- **`.ps1` 含中文必须 UTF-8 BOM**：Windows PowerShell 5.1 按 ANSI 读脚本，无 BOM 会乱码 / 报错。
- **Docker 结论（勿重复评估）**：`registry-1.docker.io` 本机不可达；Docker Desktop 需 WSL2 +
  重启 1~2 次，用户已选免 Docker 直装；运行数据统一 `.local/`（已 gitignore）。
- **后端 WARN 现状（T2a 起）**：原两条（Flyway「No migrations found」/ MyBatis-Plus「No MyBatis mapper」）
  已随 V1/V2 与 mapper 落地消失；仅剩 springdoc 的两条 INFO 级 WARN（无害）。
- **种子密码 hash 双处同步**：`V2__seed_auth_demo.sql` 里的 BCrypt hash 与
  `backend/src/test/java/.../PasswordHashTest.SEED_HASH` 必须一致（改演示密码时两处一起改；
  重新生成：临时加 `System.out.println(new BCryptPasswordEncoder().encode("123456"))` 跑一次测试）。
- **建表用 `BOOLEAN` 不要 `TINYINT(1)`**：MySQL 8 对整型显示宽度（如 `TINYINT(1)`）报弃用告警，
  `BOOLEAN` 落库等价 `tinyint(1)` 且无告警（T2a 已踩）。
- 后端进程结束按**端口 8080 定位 PID** 精确处理；勿按名杀 `java`（同机 IDEA 常驻）。

### 6.9 多租户注入与种子生成（Step 3 T1 起）

- **租户注入不是「按表一刀切」**：`PinliaoTenantLineHandler` 仅在「当前登录者带 companyId（HR / COMPANY_ADMIN）」
  且「表属租户私有集合（jobs / conversations / messages / applications / interviews / resume_view_logs /
  tenant_configs / usage_logs）」时注入 `company_id`；求职者是**跨租户**视角（浏览所有公司职位、查自己的
  投递 / 会话），一律不注入，service 层必须显式按 `user_id` / 可见职位过滤。改这段逻辑前先读
  `docs/step3-plan.md` §3（求职者跨租户是产品语义）。
- **`mvnw test` 的 WebMvcTest 切片**不加载 `MybatisPlusConfig`（`EnvelopeSmokeTest` 已排除鉴权拦零）；
  为业务写切片测试时注意：无登录上下文时租户表查询不会被注入条件（handler `ignoreTable` 直接放行），
  断言别依赖「自动注入」；`PinliaoTenantLineHandlerTest` 单测覆盖策略本身。
- **种子迁移不要手改**：`V4~V8` 由 `backend/scripts/seed-gen/generate.mjs` 从 `src/api/mock/*` 生成
  （文件头有「勿手改」警示）；改演示数据 → 改 mock 源文件 → 重跑生成脚本（Node ≥ 22.7；
  `--import` 参数必须传 `file:///` 绝对 URL；脚本用 Node 原生 `--experimental-transform-types`，零额外依赖）。
- **行数抽查**：`backend/scripts/verify-seed.ps1`（16 项断言，期望值写死在脚本）；改动种子数字时同步改脚本。
- **jsqlparser 版本差异**：本仓（MyBatis-Plus 3.5.17）`StringValue` 取值方法是 `getValue()`，
  不是 `getStringValue()`（写租户单测时已踩）。
- **求职者可见职位**：`status='OPEN' AND deleted_at IS NULL`（+ 公司存在），与 mock `listSeekerJobs()`
  语义一致；T3 起所有求职者侧职位查询都走这条定义（对应 AGENTS §2.10）。

### 6.10 注册落库与演示库重置（Step 3 T2b 起）

- **注册会真实落库且手机号唯一**：`u-reg-*` / `up-reg-*` / `co-reg-*` / `hr-reg-*` / `cm-reg-*`
  记录持久保留（同手机号重复注册 = 40910）；`verify-seed.ps1` 的 users / companies 断言已按前缀排除。
- **注册 id 派生自手机号**（`u-reg-{phone}` 等）：mock 用进程内递增计数（`u-reg-1`，刷新即复位），
  落库不能照抄（重启即冲突）；前端只把 id 当不透明字符串，无需对齐 mock 的 id 形态。
- **e2e 注册段用固定手机号 `13900004321`**：Mock 阶段刷新即可重跑；**T9 对真实后端重跑前必须定重置策略**
  （候选：跑 e2e 前重建演示库 / e2e 改用时间派生手机号并同步期望公司名）——**待用户拍板，勿擅自改 e2e**。
- **纯 Mockito 单测碰 MyBatis-Plus 的 LambdaWrapper 需先初始化 TableInfo**（无 Spring 上下文时报
  `can not find lambda cache for this entity`）：见 `AuthServiceRegisterTest` 静态块的
  `TableInfoHelper.initTableInfo`（对 UserEntity / UserProfileEntity / HrProfileEntity / CompanyMemberEntity）。

### 6.11 职位公开读与 T9 代理坑（Step 3 T3a 起）

- **`@Select` 字符串不解析 XML 实体（没包 `<script>` 时）**：`j.id &lt;&gt; #{jobId}` 会把 `&lt;&gt;` 原样
  发进 MySQL（报 `SQLSyntaxErrorException near '&gt;'`）；要么包 `<script>`（则 `<` 需转义），要么直接写
  `!=`（T3a 已踩，`JobMapper.selectSimilar`）。
- **统一包络的 traceId 逐请求变化 → 不能按响应体算 ETag**：职位详情 ETag 用「`jobId` + `updatedAt` 秒级
  版本号」手动比对 `If-None-Match` 返回 304（`JobQueryService.detail` + `JobController.detail`）；若日后想用
  `ShallowEtagHeaderFilter`，需先把 traceId 排除出响应体。
- **`@JsonInclude(NON_NULL)` 会把 `nextCursor: null` 吞掉**：游标分页响应（`JobViews.Pagination`）不能加
  NON_NULL——前端类型是 `nextCursor: string | null`，缺字段与显式 null 不等价。
- **公开接口的「可选鉴权」在 `AuthInterceptor` 内判定**（方法 + Ant 路径，包级可见便于单测），不要加进
  `WebAuthConfig` 的 exclude（exclude 后带 token 也不解析，且会把 `POST /api/v1/jobs` 一并放过）。
- **公开浏览不受租户注入**：`JobMapper` 的公开查询显式 `@InterceptorIgnore(tenantLine="true")`——HR 账号
  打开求职者端职位页也是全平台可见职位（契约「公开浏览」）；HR 管理端口径（§4.3 / §6.5~§6.8）才用租户注入。
- **游标格式**：`base64(JSON {"s":得分,"k":发布时间毫秒,"id":"job-1"})`——排序键三元组（得分 DESC / 时间
  DESC / id ASC），匿名与登录同格式；非法游标 40001；演示规模全量取回后内存切片，上量后把条件推 SQL
  （游标格式不变）。
- **T9 联调代理已落地（2026-10-19）**：`src/api/client.ts` 的 `buildUrl` **只返回相对路径**（避免跨域），
  故在 `next.config.ts` 加了 rewrites：`/api/v1/:path*` → `${API_PROXY_TARGET ?? 'http://localhost:8080'}/api/v1/:path*`，
  以及 `/uploads/:path*` → 后端（简历附件 / 上传文件回显靠它，**不需要**前端拼绝对地址）。
  `.env.local` 设 `NEXT_PUBLIC_USE_MOCK=false`、`NEXT_PUBLIC_API_BASE_URL=/api/v1`（`.env*` 已 gitignore，不提交）。
  改 `.env.local` 后必须重启 dev server（`NEXT_PUBLIC_*` 构建期内联）。

### 6.12 职位写与配额（Step 3 T3b 起）

- **租户插件对 INSERT 不会重复列**：MyBatis-Plus `TenantLineInnerInterceptor` 在 INSERT 前会检查租户列是否
  已在列清单里（`ignoreInsert`），因此 `BaseMapper.insert` + 实体显式 `companyId` 是安全的；UPDATE / DELETE /
  SELECT 会自动追加 `company_id` 条件。写路径仍在 service 里**显式比对 companyId**（纯 Mockito 单测没有插件，
  也能覆盖 40310）。
- **新职位 id = `job-new-{库内最大序号 + 1}`**：查询必须 `@InterceptorIgnore(tenantLine="true")`——id 是全局主键，
  要跨公司取最大值；软删保留行，序号不复用；**勿改成时间戳 / 随机**（T1 起既定约束）。
- **`publishedAt` 落库前截到毫秒**：Java 纳秒 vs MySQL `DATETIME(3)`，不截会导致「创建响应」与「更新 / 详情再读」
  的 ISO 字符串不相等（e2e 比对会假失败）。
- **配额缺省 50**：种子没有 `tenant_configs` 行，`max_jobs` 读为 null 时按 50；42211 在真实超限时才触发
  （JUnit 用 mock 覆盖）。**发布限流 10 次/分钟/公司**：Redis 自然分钟键
  `pinliao:job:create:{companyId}:{minute}`，超限 42901。
- **40310 语义**：不存在 / 非本公司 / 已软删统一「无权操作该职位」，不泄露职位是否存在（对齐 mock `hrJobs.ts`）；
  字段校验 42201（合并后校验：标题 2~30、薪资正数且下限 < 上限、标签 ≤ 8）。
- **冒烟会留下 `job-new-*` 软删行**：`verify-seed.ps1` 的 `jobs_total` 已排除该前缀；不要改成硬删（破坏
  「软删保留行」语义），也不要手动清库。

### 6.13 投递 / 面试与会话占位（Step 3 T4 起）

- **投递即建会话**：§9.1 成功（含撤回后重新投递）后按 `(jobId, userId)` 幂等创建 `conv-new-{seq}`，
  会话 `lastMessageAt = now`、未读 0；§9.3 详情返回 `conversationId`（契约外增量）。这样 HR 端
  「沟通 / 约面试」不再依赖 mock 的 `ensureConversationForApplication`（真实分支目前没有独立建会话接口）。
  T6 做会话列表 / 消息时**复用该行**，不要再按 job 建第二份。
- **状态机顺序要对齐 mock**：约面试先判 40914（REJECTED / WITHDRAWN）→ 40915（已有 PENDING）→
  42201（形式 / ONSITE 缺地点）→ 42210（时间早于当前）。顺序不同会让前端提示文案错位。
- **接受 / 拒绝是幂等终态**：已 ACCEPTED 再 accept 返回原记录；已 DECLINED 再 decline 同理；
  其余状态（如 CANCELLED / COMPLETED）→ 40913。不要改成 404（e2e 会重试）。
- **归属隔离统一 40410**：投递不存在 / 非本人 / 非本公司、面试不存在 / 跨公司统一 `40410`（不泄露存在性）；
  角色不符才是 40301。HR 侧租户注入 + service 显式 `companyId` 双保险。
- **新 id 前缀**：`app-new-` / `int-new-` / `conv-new-` / `msg-new-{seq}`，序号查询都要
  `@InterceptorIgnore(tenantLine="true")`（全局主键）；时间字段落库前截毫秒（同 T3b 的 DATETIME(3) 坑）。
- **冒烟可重复**：`smoke-applications.mjs` 每次选「云杉下未投递或已撤回」的职位，结尾撤回为 WITHDRAWN；
  中途失败可能留下非终态记录，重跑会换下一个职位，全部用完时需人工处理 `app-new-%`。
- **T9 已知契约差异（2026-10-19 已全部处理，实现细节见 §6.18）**：
  1. 列表分页对象 `{list,pagination}` → 前端真实分支统一解包 `.list`（§9.2 / §10.1 / §8.1 / §8.2）；
  2. `GET /hr/applications`（§4.4）T8 已实现，出参直接是 `HrApplicationView` 超集；
  3. §9.3 返回 `ApplicationDetail` 与 HR 详情页所需形状不同 → 详情页改走 §4.4 列表按 id 定位；
  4. §8.7 真实返回 `Message` → `inviteInterview` 返回类型改为 `Interview | Message`；
  5. `listMyApplications` 真实分支已按 `applicationId` 合并 §10.1 面试列表。

### 6.14 公司模块与企业认证（Step 3 T5 起）

- **管理员口径不要看 user.role**：§5.4~§5.6 的 COMPANY_ADMIN 以 `company_members.role=ADMIN` 为准。
  演示 HR `u-hr-1` 的 `users.role` 是 `HR`、成员角色是 `ADMIN`；若只判 token 里的 role，演示账号会 403。
  §5.3 仍然只要求 HR / COMPANY_ADMIN，不要求 ADMIN 成员。
- **改名无需刷职位行**：职位视图的公司信息从 `companies` 实时装配，`jobs` 不存公司名快照；
  §5.4 更新后 §5.1/§5.2/§6.x 自动同步。不要再写「UPDATE jobs SET company_name=...」。
- **认证没有独立记录表**：契约只暴露 `companies.verified/status`，数据模型也没有
  `company_verifications`；提交的四项资料只做校验，不落库。状态流转 PENDING → 约 1.2s
  后台调度 ACTIVE，延迟可用 `pinliao.company.verify-delay-ms` 覆盖（单测用 30ms）。
- **异步线程没有 UserContext**：自动通过只更新全局表 `companies`，不能用依赖租户注入的 Mapper；
  后台任务里 `selectById` 后必须再次确认 `status=PENDING`，避免覆盖人工禁用 / 删除。
- **公开路径**：只放行 `GET /api/v1/companies/*` 与 `GET /api/v1/companies/*/jobs`（匿名可读）；
  `/company/**`（单数）仍需登录。Ant 的 `*` 只匹配一段，新增子路径时要同步白名单与单测。
- **公司职位只看可见**：§5.2 仅 `OPEN` 且 `deleted_at IS NULL`，公司不存在 → 40410；
  游标与 §6.1 同格式（score 固定 0），不要复用带推荐加权的 cursor。
- **冒烟保持基线**：`smoke-company.mjs` 用临时 `co-reg-*` 公司验证写链路，发布的 `job-new-*`
  在 finally 软删；`verify-seed.ps1` 已排除这两类前缀，跑完仍应为可见 38 条。
