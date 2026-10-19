# 聘聊 · Step 3 后端开发计划（分块执行 + 上下文交接协议）

> 本文件回答两个问题：**Step 3 怎么分块做**、**每个新会话怎么低上下文接力**。
> 状态唯一定稿在 `PROGRESS.md`；本文件是执行计划与任务板（勾选进度以这里为准）。
> 最后更新：2026-10-18 · 状态：T0 ✅、**T2a ✅（登录/退出，先行于 T1）**、**T1 ✅（全量表 + 演示种子）**、**T2b ✅（注册）**、**T3a ✅（职位公开读 + 分类字典，8 条）**、**T3b ✅（HR 职位写，4 条）**、**T4 ✅（投递 + 面试闭环，10 条 + §8.7）**、**T5 ✅（公司模块 + 企业认证，6 条）**、**T6 ✅（消息 + WebSocket，8 条 + 4 主题）**、**T7 ✅（求职者 / 简历 / 收藏 / 上传，19 条）**；下一步：**T8 招聘者 / 候选人 / 人才筛选（§4，6 条）**

---

## 0. 总目标与完成定义（DoD）

- **目标**：Spring Boot 后端实现 `docs/api-endpoints.md` 正文 70 条契约（含 WebSocket 4 主题），
  多租户（`companyId`），对接现有前端（`NEXT_PUBLIC_USE_MOCK=false`）。
- **总验收（North Star）**：前端 `npm run e2e`（63 项）在**真实后端**下全绿；
  每个 chunk 另有接口级验收（见 §4）。
- **前提**：后端要复刻 mock 的**演示语义**（见 §3），这是 e2e 全绿的关键。
- **本轮不做**：语义检索升级（T10 增量）、LTR 排序模型、简历 PDF 解析、部署上线、
  前端新功能（前端只做联调修复）。

## 1. 技术基线

| 项 | 定 |
|---|---|
| 语言/框架 | Java 17+（**JBR 21**，IDEA 自带）· Spring Boot **3.5.16** · MyBatis-Plus 3.5.17 |
| 数据库 | **MySQL 8**（用户拍板，替代原 PostgreSQL；多租户 `companyId`；本机专用实例 3307） |
| 缓存/限流 | Redis |
| 实时 | WebSocket（4 主题，规范见 `docs/api-contract.md`） |
| 构建 | Maven Wrapper（`mvnw`，不依赖本机 Maven 版本） |
| 迁移 | Flyway（T2a 已落 `V1__auth_schema.sql` / `V2__seed_auth_demo.sql`；T1 从 **V3+** 继续） |
| 接口文档 | springdoc-openapi（/swagger-ui） |
| 目录 | **`backend/` 单仓已落地**（2026-10-17；与契约/文档同一 git 历史） |

**本机环境盘点（2026-10-17 实测）**：

- 仅 JDK 1.8（`JAVA_HOME=D:\JDK1.8`）→ **需安装 Temurin 17**（`winget install EclipseAdoptium.Temurin.17.JDK`，包已确认存在）
- Maven 3.6.3（用 `mvnw` 规避）；Git 2.20.1 可用；winget v1.29 可用
- 无 Docker；MySQL 服务存在但**已停止**（3306 空闲）；无 PostgreSQL/Redis（5432/6379 空闲）
- `C:\dev` 有 Tomcat/Nacos/MySQL/Navicat 等工具链（本机是 Java 开发机）

**T0 环境定案（2026-10-17，已实施）**：

- JDK：不装 Temurin 17，改用 IDEA 自带 **JBR 21**（免安装；构建/运行前设 `JAVA_HOME` 到 jbr）
- 数据库：不装 PostgreSQL，复用本机 MySQL 8.0.29 二进制另起**专用实例 3307**
  （数据 `.local/mysql`；3306 留给本机原服务）；Redis 用 tporadowski Windows 版 zip（`.local/redis`，6379）
- Docker 实测：`registry-1.docker.io` 不可达（`docker.m.daocloud.io` 可达）；Docker Desktop 需 WSL2 +
  重启 1~2 次（Win10），用户选**免 Docker 直装**；运行数据统一 `.local/`（已 gitignore）

**前端对接开关（已就绪，零代码改动）**：`src/config/env.ts` 读
`NEXT_PUBLIC_USE_MOCK=false` / `NEXT_PUBLIC_API_BASE_URL` / `NEXT_PUBLIC_WS_BASE_URL` / `NEXT_PUBLIC_WS_PORT`。
改环境变量后需重启 `npm run dev`。

## 2. 任务板（分块进度以本表为准）

图例：⬜ 未开始 · 🔄 进行中 · ✅ 完成

| 编号 | 内容 | 接口范围 | 预估会话 | 状态 |
|---|---|---|---|---|
| T0 | 环境安装与工程骨架（含统一包络/异常/健康检查） | — | 1 | ✅ 2026-10-17（`dd7a948`） |
| T1 | 数据库迁移与演示种子（对齐 mock 数字；鉴权表与演示账号已随 T2a 落地） | 剩余全部表（V3+） | 1–2 | ✅ 2026-10-18（`8ed73f8` T1a + `85518bf` T1b） |
| T2a | 鉴权：验证码 / 登录（密码+短信）/ 刷新 / 退出 / me | §1.1、§1.3~§1.7（6 条） | 1 | ✅ 2026-10-17（`6f286e3`；经用户指示先行于 T1） |
| T2b | 鉴权：注册（含注册自动建公司） | §1.2（1 条） | ≤1 | ✅ 2026-10-18（`030a5b2`） |
| T3a | 职位公开读 + 分类字典（列表/搜索/详情/相似/埋点 + 分类/城市/字典） | §6.1~§6.4、§6.9 + §7（8 条） | 1 | ✅ 2026-10-18（`9f509be`） |
| T3b | HR 职位管理（发布/更新/关闭重开/软删） | §6.5~§6.8（4 条） | ≤1 | ✅ 2026-10-18（`8102f0b`） |
| T4 | 投递 + 面试闭环（状态机/校验码） | §9 / §10（10 条）+ §8.7 约面试 | 1 | ✅ 2026-10-18（`d4ce22c`） |
| T5 | 公司模块 + 企业认证（含 1.2s 自动通过） | §5（6 条） | 1 | ✅ 2026-10-18（`2359625`） |
| T6 | 消息 + WebSocket（含自动回复演示语义） | §8（8 条）+ 4 主题 | 1–2 | ✅ 2026-10-18（`8d9513a`） |
| T7 | 求职者/简历/收藏/上传 | §2 / §3 / §11 / §12（19 条） | 1 | ✅ 2026-10-18（`c26dc44`） |
| T8 | 招聘者 + 候选人 + 人才筛选（移植关键词版） | §4（6 条，含 §4.5/§4.6） | 1 | ⬜ |
| T9 | 全量联调验收（前端 e2e 63/63） | 全部 | 1–2 | ⬜ |
| T10+ | 后续增量（不进本轮）：语义检索 / LLM 推荐理由 / 外部简历库 | — | — | — |

> `docs/api-endpoints.md` 账目已定稿（2026-10-18，用户拍板）：正文 70 条为准，总览表已校正
> 鉴权 7 / 职位 10 / 消息 9，分模块合计 70；分块只按功能域划分。
>
> **T8 / T9 顺序已定（2026-10-18，用户拍板）：T8 先、T9 后**——先补齐 §4 后端，再整体切
> `USE_MOCK=false` 做全量联调。理由：`USE_MOCK` 是全局开关，无模块级粒度，T9 时无法只让
> §4 页面留在 Mock；先 T8 可避免 T9 二次返工与一次验收范围打折。

**依赖关系**：T0 → T2a ✅（鉴权基础；只依赖 V1/V2 鉴权表）→ T1 ✅（剩余表 + 全量种子）→ T2b（注册建公司）→
之后各模块可按序推进（T6 最难、T8 依赖 T1 简历种子）；T9 必须最后；每个 chunk 一次会话（或按需拆小）。

## 3. 演示语义对齐清单（后端必须复刻；T9 全绿的前提）

> 行为参考实现全部在 `src/api/mock/`，写代码时直接对照抄语义，不要凭记忆。

- **验证码固定 `123456`**；同手机号 60s 限流（Redis）
- **HR 注册自动创建未认证公司**（`新注册企业-{手机尾4}`）
- **企业认证**：提交 → `PENDING` → 约 1200ms 自动通过（延时任务模拟）→ 前端轮询可见
- **聊天自动回复**：候选人关键词自动回复 + 「正在输入」延迟；`INTERVIEW_INVITE` 卡片消息类型
- **种子数据数字（会被 e2e 断言）**：求职者可见职位 **38** 条；HR 云杉 **7** 条职位；
  候选人 **6** 人；会话 **6** 个 / 未读 **4** 条；HR 投递 **6** 条（覆盖全部状态枚举）；
  求职者投递 **3** 条（均非云杉）；简历正文 **7** 份（人才筛选数据源）；
  人才筛选演示断言：`React`→林小满、标签`自动化测试`→沈禾、`COBOL`→空结果
- **错误码**：42210 空筛选条件 / 40911 重复投递 / 40913 不可撤回 / 40914·40915 邀请校验 /
  40910 手机号已注册 / 40110·40111 登录失败 / 40103 refreshToken 无效
- **响应包络**：`{ code, message, data, traceId }`（见 `docs/api-contract.md`；前端按此解包）
- **投递状态机**：APPLIED → VIEWED → INTERVIEW → OFFER / REJECTED；WITHDRAWN 独立；
  「已关闭职位对求职者不可见」等联动行为同 mock

## 4. 每 chunk 规格（概要）

- **T0 环境与骨架 ✅ 2026-10-17 实际**（与原案差异：JDK 用 IDEA 自带 JBR 21；数据库 MySQL 8 免 Docker 直装；
  start.spring.io 已下架 Boot 3.x → 手工建工程）
  - 环境：MySQL 专用实例 **3307**（`.local/mysql`）+ Redis **6379**（`.local/redis`），起停脚本
    `backend/scripts/`（`db-init` / `dev-up` / `dev-down` 等；`.ps1` 含中文须 UTF-8 BOM）
  - `backend/`：Spring Boot **3.5.16** + Maven Wrapper 3.3.2（→ Maven 3.9.16）；MyBatis-Plus 3.5.17、springdoc 2.9.1
  - 统一层：包络 `ApiResponse` / 错误码表 `ApiCodes`（code 前 3 位 → HTTP）/ `GlobalExceptionHandler` /
    `TraceIdFilter`（MDC + X-Trace-Id）/ `ResponseAdvice`；样例接口 `/api/v1/_demo/{ping,error,validate}`
  - 验收（实测）：`mvnw test` **3/3**；启动 3.5s 且 `/actuator/health` UP；ping / validate（422+42201+errors）/
    error（404+40401）包络与错误码符合契约（traceId 与响应头一致）；swagger / api-docs 200；
    Flyway 已连 MySQL（0 迁移，T1 补 V1/V2）
- **T1 数据迁移与种子 ✅ 2026-10-18 实际**（`V1`/`V2` 编号已被 T2a 的鉴权子集占用——V1 鉴权建表、
  V2 演示账号/云杉科技；T1 自 `V3__core_schema.sql` 起，种子迁移 `V4~V8`）
  - Flyway `V3` 按 `docs/data-model.md` 建 12 张剩余业务表（jobs / resumes / favorites / applications /
    interviews / conversations / messages / resume_view_logs + cities / categories / sys_configs /
    tenant_configs / usage_logs / audit_logs，`companyId` 租户列 + 必要索引；hr 展示字段冗余，演示无全量 HR 账号）
  - 种子迁移 `V4~V8` 数字对齐 §3：公司 16 / 城市 9 / 分类 84 / 职位 51（可见 38、云杉 7，关闭规则同
    mock `stableHash(id)%4`）/ 候选人 6 + 简历 7 / 投递 9（HR 6 覆盖全状态 + 求职者 3 均非云杉）/
    面试 3 / 会话 8（HR 6 未读 4 + 求职者 2）/ 消息 16
  - 种子由 `backend/scripts/seed-gen/generate.mjs` 直接从 `src/api/mock/*` 生成（Node 原生 TS + loader hook），
    迁移文件带「勿手改」头；改 mock 数据后重跑生成脚本
  - MyBatis-Plus：分页插件（MySQL，单页 ≤200）、租户拦截器（**HR 注入 company_id；求职者跨租户不注入**，
    见 `PinliaoTenantLineHandler`）、created/updated 自动填充（UTC）
  - 验收（实测）：启动自动 migrate **V1~V8 成功**（3.3s）；`mvnw test` **14/14**（新增租户策略 6 项）；
    `scripts/verify-seed.ps1` **16/16 命中**；鉴权冒烟 14/14 无回归
- **T2a 鉴权（登录/退出）✅ 2026-10-17 实际**：§1.1、§1.3~§1.7 六条；jjwt HS256（access 2h / refresh 30d，
  一次性轮换）+ Redis（refresh 白名单、access jti 吊销名单、验证码 5min、60s 限流）；`AuthInterceptor`
  默认保护 `/api/v1/**`（白名单：`auth/sms|login|refresh|register`、`_demo`）；错误码
  40101/40102/40103/40110/40111/42201/42901；Flyway V1（users / user_profiles / companies /
  company_members / hr_profiles）+ V2 种子（13800000001/2，密码 123456，id 与 mock 哈希一致）
  - 验收（实测）：`mvnw test` **8/8**（JwtService 4 + 种子 hash 1 + 包络 3）+ `scripts/smoke-auth.mjs`
    14 项全过 + 40111 手工验证；数据库抽查 5 表行数正确
- **T2b 注册 ✅ 2026-10-18 实际**（`030a5b2`）：§1.2；校验顺序对齐 mock（字段校验 42201 → 已注册 40910 →
  验证码 40110，失败不消耗验证码）；HR 自动建未认证公司「新注册企业-{尾4}」（未融资 / 1-19人 / 企业服务 /
  verified=false）+ ADMIN 成员，求职者建画像（求职者{尾4}）；id 派生自手机号（`u-reg-/up-reg-/co-reg-/
  hr-reg-/cm-reg-{phone}`——mock 用进程内计数刷新即复位，落库需跨重启稳定唯一）；`ApiCodes` 新增 40910
  - 验收（实测）：`mvnw test` **18/18**（新增 `AuthServiceRegisterTest` 4 项：校验顺序 / 不落库 / 双角色落库字段）；
    `scripts/smoke-auth.mjs` **20/20**（注册段 6 项：发码 / 坏码 40110 / HR 建公司 verified=false / 重复 40910 /
    me 带新 token / 求职者无 company）；字段校验三例 42201（phone / password / role）+ 未发码注册 40110 手工验证；
    `verify-seed.ps1` 16/16（注册产生的 `u-reg-*` / `co-reg-*` 已排除）
- **T3a 职位公开读 + 分类字典 ✅ 2026-10-18 实际**（`9f509be`；T3 按「公开读 / HR 写」拆两块）：
  §6.1~§6.4 列表·搜索·详情·相似 + §6.9 浏览埋点 + §7.1~§7.3 分类树·城市·字典；筛选对齐 mock `listJobs`
  （城市 / 分类联动 / 经验 / 薪资区间相交 / 关键词 hay = 标题·公司名·cat·sub·tags·area）；`recommend` 按
  期望二级岗位 +2 / 一级分类 +1 稳定排序；游标分页（base64 JSON `{s,k,id}`，默认 20 / 上限 100）；
  公开接口由 `AuthInterceptor` 按「方法 + 路径」可选鉴权（匿名放行、带 token 仍解析；写接口不放行）；
  公开浏览 SQL 显式 `@InterceptorIgnore(tenantLine)`；详情 ETag（`id-updatedAt`）→ 304；40410 / 40001
  - 验收（实测）：`mvnw test` **35/35**（新增 17：职位 12 + 公开路径 2 + 字典 3）；`scripts/smoke-jobs.mjs`
    **29/29**（筛选 / 搜索 / 游标翻页不重不漏 / 加权置顶 / 详情 / 相似 / ETag 304 / 埋点去重 / 分类 84 /
    城市 9 / 字典精确比对）；`smoke-auth.mjs` 20/20 无回归
- **T3b HR 职位写 ✅ 2026-10-18 实际**（`8102f0b`）：§6.5~§6.8 发布 / 更新 / 关闭·重开 / 软删；仅 HR /
  COMPANY_ADMIN 且归属本公司（companyId 取自登录上下文）；不存在 / 非本公司 / 已软删统一 40310
  「无权操作该职位」（对齐 mock，不泄露存在性）；配额 `tenant_configs.max_jobs`（缺省 50）→ 42211；
  发布限流 10 次/分钟/公司（Redis）→ 42901；新 id `job-new-{seq}` 递增派生（软删不复用）；
  PATCH 部分更新（null 保留原值，合并后校验 42201），保留发布者 / 发布时间 / 统计
  - 验收（实测）：`mvnw test` **50/50**（新增 `JobCommandServiceTest` 15 项）；`scripts/smoke-jobs.mjs`
    29 → **49/49**（写接口 20 项：401/40301/42201、发布归属本公司、可见 39、更新保留统计、关闭不可见、
    重开、越权 40310、软删幂等、收尾回到可见 38）；`smoke-auth` 20/20、`verify-seed` 16/16 无回归
- **T4 投递与面试 ✅ 2026-10-18 实际**（`d4ce22c`）：§9.1~§9.5 投递 / 我的投递 / 详情 / 撤回 / HR 状态；
  §10.1~§10.5 面试双端列表 / 详情 / 接受 / 拒绝；§8.7 约面试（创建 Interview + 投递置 INTERVIEW +
  INTERVIEW_INVITE 消息）。状态机对齐 mock：40911 重复投递、40913 状态冲突、40914 不可约面、
  40915 已有待确认、42210 简历 / 时间前置；归属隔离不存在 / 非本人 / 非本公司统一 40410。
  投递即按 `(jobId, userId)` 幂等占位建会话（`conv-new-{seq}`），`ApplicationDetail` 附契约外
  `conversationId` 供 HR 触达 / 约面；新 id 统一 `app-new-{seq}` / `int-new-{seq}` / `conv-new-{seq}` /
  `msg-new-{seq}` 递增派生
  - 验收（实测）：`mvnw test` **77/77**（新增投递面试写 22 + 读 5）；新增 `scripts/smoke-applications.mjs`
    **51/51**（连续两次可重复：投递 / 校验码 / 游标 / 归属 / 约面 / 接受拒绝 / 撤回重投）；`smoke-jobs`
    49/49、`smoke-auth` 20/20、`verify-seed` 16/16 无回归（排除 T4 四类前缀）
- **T5 公司模块 + 企业认证 ✅ 2026-10-18 实际**（`2359625`）：§5.1~§5.6 公司公开详情 / 公司职位（游标分页）/
  我的公司 / 资料编辑 / 成员列表 / 企业认证（6 条）。§5.1/§5.2 匿名公开读；§5.4~§5.6 的
  COMPANY_ADMIN 以 `company_members.role=ADMIN` 为准（演示 HR 用户角色是 HR、成员角色是 ADMIN）；
  改名后职位公司名实时装配，无需刷 jobs 快照；认证提交 → PENDING / verified=false → 约 1.2s
  后台调度自动 ACTIVE / verified=true，重复提交 40916；资料校验 42210、公司不存在 40410
  - 验收（实测）：`mvnw test` **90/90**（新增公司 12 + 公司职位分页 1）；新增
    `scripts/smoke-company.mjs` **26/26**（连续两次可重复：公开读 / 分页 / 权限 / 改名同步 / 成员 /
    认证异步通过 / 40916；临时职位 finally 软删保持可见 38 基线）；`smoke-jobs` 49/49、
    `smoke-applications` 51/51、`smoke-auth` 20/20、`verify-seed` 16/16 无回归
- **T6 消息与 WS ✅ 2026-10-18 实际**（`8d9513a`）：§8.1~§8.8 会话列表 / 消息 / 发起 / 发送 / 已读 /
  typing / 发送简历 / 关闭 + WebSocket STOMP（`/ws`）。未读计数以 `conversations.user_unread_count` /
  `hr_unread_count` 为持久化事实源，Redis 仅承担发送限流（30 次/分钟/用户）与 `idempotencyKey` 24h 去重；
  列表返回双端超集（conversation / job / hr / company / candidate / lastMessage），历史按 createdAt 降序游标；
  新增契约外 `GET /conversations/{id}` 供 HR 聊天顶栏读取。TEXT 双向自动回复（关键词 + typing true/false，
  默认 1.2s，`pinliao.message.auto-reply-delay-ms` 可调）；T4 面试邀请消息已接入实时推送。
  - 验收（实测）：`mvnw test` **114/114**（新增消息 24）；新增 `scripts/smoke-messages.mjs` **40/40**
    （连续两次可重复：列表 / 详情 / 历史 / 发起 / 40912 / 发送 / 幂等 / 已读 / typing /（42901 见单测）
    发送简历 / 关闭 / WS 推送与 STOMP 发送 / 错误队列 / 非法 token）；消息冒烟只读写 `conv-new-*`，
    种子未读保持 HR 4 + 求职者 2；`smoke-company` 26/26、`smoke-jobs` 49/49、
    `smoke-applications` 51/51（T6 后复跑）、`smoke-auth` 20/20、`verify-seed` 16/16 无回归
- **T7 用户 / 简历 / 收藏 / 上传 ✅ 2026-10-18 实际**（`c26dc44`）：§2.1~§2.8 画像读改 / 改密 / 重置 /
  改手机号 / 外观偏好 / 注销（8 条）+ §3.1~§3.6 简历列表 / 详情 / 创建 / 更新 / 软删 / 设默认（6 条）+
  §11.1~§11.3 收藏 / 取消 / 分页列表（3 条）+ §12.1~§12.2 预签名 / 确认（2 条）。外观偏好存 Redis
  （`pinliao:user:prefs:{userId}`，默认 auto）；注销软删 + 立即吊销当前 accessToken；简历权限按
  「本人 / 已投递到本公司的 HR」判定，越权统一 40410；首份自动默认、删除默认自动顺延；收藏
  `(userId, jobId)` 唯一、重复 40910、游标带 total；上传用本地目录 + HMAC 预签名 PUT + `/uploads/**`
  静态映射（未新增元数据表，业务落库由后续 PATCH 完成），后续可替换为 OSS
  - 验收（实测）：`mvnw test` **151/151**（新增用户 13 + 简历 10 + 收藏 5 + 上传 9）；新增
    `scripts/smoke-user.mjs` **64/64**（连续两次可重复：画像 / 密码 / 手机号 / 偏好 / 注销 / 简历默认切换 /
    HR 40410 / 收藏两页 / 直传 + 静态读回 / 篡改签名 / 越权确认）；`smoke-auth` 20/20、
    `smoke-jobs` 49/49、`smoke-applications` 51/51、`smoke-company` 26/26、`smoke-messages` 40/40
    （连续两次）、`verify-seed` 16/16 无回归；`verify-seed` 的 resumes 计数已排除 `u-reg-*` 与软删行
- **T8 招聘者与人才筛选**：§4；HR 画像读改 + 候选人列表/详情/标记 + §4.5/§4.6 **移植**
  `mock/talentMatch.ts`（提取词逻辑翻译 Java，出参与排序规则不变）；§4.3/§4.4 已在 T3b/T4 落地部分
  需先核对路径与出参；验收：JUnit +（可选）前端人才筛选半联调
  - **顺序（2026-10-18 已定）**：T8 先于 T9；T8 完成后才切 `USE_MOCK=false`，T9 验收范围含
    §4 全部页面（人才筛选不再暂留 Mock）。
- **T9 全量联调**：前端 `.env.local`（不提交）：`USE_MOCK=false`、`API_BASE_URL=http://localhost:8080/api/v1`、
  WS 指向后端；重启 dev server → `npm run e2e` 目标 **63/63**；逐项修差异（数字/文案/解包/时间格式）；
  收尾：`ARCHITECTURE.md` 增后端章节、`api-endpoints.md` 修订记录、CHANGELOG、本文件勾选；可选打 tag

## 5. 上下文与交接协议（核心）

### 5.1 总原则

**对话会丢，文件不会。** 每个会话的产出必须落进：代码提交 + PROGRESS + worklog（+ 本文件勾选）。
"下一个动作"要具体到**接口/文件/命令**，不写"继续完善"这种空话。

### 5.2 新会话开工 checklist（目标 ≤10 分钟）

1. 读 `PROGRESS.md` §0 与 Step 3 区块（看"当前 chunk"与"下一个动作"）
2. 读**本文件**该 chunk 的规格（§4 对应条目 + §3 相关清单）
3. 读 `AGENTS.md`（硬性约束）
4. 需要接口细节时查 `docs/api-endpoints.md` 对应小节；类型查 `src/types/api.ts` 对应段落
5. **只读当前 chunk 涉及的文件**；先 `grep` 定位再精读，禁止全库通读

### 5.3 每会话收工 checklist（chunk 完成或中途中止都执行）

1. **提交**：代码 + 文档一起（中文，`feat(backend): …（T?）`；半成品用 `wip(backend/T?): …`）
2. **`PROGRESS.md`**：§0 本轮提交、§1 状态行、"下一个具体动作"（越具体越好）
3. **`worklog.md`**：追加一条 Task（ID 递增，从 16 起）
4. **本文件**：T 表勾选状态（🔄/✅ + 日期 + commit hash）
5. 每 1–2 个 chunk 汇总一次 `CHANGELOG.md`

### 5.4 上下文将满的应急流程（触发前 5–10 分钟）

1. 停止开新功能；把当前代码改到能编译/启动（不行就回退到干净状态）
2. 把"现状 + 卡点（含报错原文）+ 下一步动作"写进 `PROGRESS.md` Step 3 区块
3. `wip` 提交（代码 + 文档一起）
4. 结束会话；用 §5.5 模板开新会话接力

### 5.5 用户开场白模板（复制即用）

```
继续开发「聘聊」Step 3 后端。
先读 PROGRESS.md §0 与 Step 3 区块 → docs/step3-plan.md 当前 chunk → AGENTS.md 约束。
只读当前 chunk 需要的文件；完成后按 step3-plan.md §5.3 收工 checklist 更新文档并提交。
```

### 5.6 每会话预算与省 token 技巧

- 目标：**一个会话一个 chunk**（净消耗控制在 10w token 内，留余量）；做不完就拆小、先收工
- 用 `grep` 定位 + `read` 带 offset/limit 精读；同一文件避免反复通读（读一次，把要点摘进对话或文档）
- 大输出重定向到文件再看片段；e2e 只在 chunk 收尾跑一次
- 探索陌生大范围时可用子代理（若客户端支持），保持主上下文干净

## 6. 关键决策（T0 会话已拍板，勿回退）

| # | 事项 | 结论（2026-10-17） |
|---|---|---|
| 1 | 仓库布局 | ✅ **单仓 `backend/`**（契约/文档/代码同一历史，AI 接力最省心） |
| 2 | 数据库/中间件 | ✅ **MySQL 8 专用实例 3307 + Redis 6379**（免 Docker 直装，数据 `.local/`；原 PostgreSQL 16 方案作废） |
| 3 | JDK | ✅ **IDEA 自带 JBR 21**（免安装；构建前设 `JAVA_HOME`；不装 Temurin） |
| 4 | 开工时机 | ✅ 2026-10-17 T0 已开工并完成；T1 ✅、T2b ✅、T3a ✅、T3b ✅、T4 ✅、T5 ✅、T6 ✅、T7 ✅（2026-10-18）；当前 T8 |

## 7. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-10-17 | 首版：T0~T9 分块任务板、演示语义对齐清单、上下文交接协议（开工/收工/应急）、待拍板事项 |
| v1.1 | 2026-10-17 | T0 完成：环境定案（JBR 21 / MySQL 3307 / Redis 6379 / 免 Docker 直装）与骨架落地（Boot 3.5.16、统一包络 / 异常 / traceId）；T0 行 ✅、§4 实测结果、§6 决策结项 |
| v1.2 | 2026-10-17 | T2a 鉴权（登录/退出，§1.1/§1.3~§1.7）先行完成并经用户指示先于 T1：T2 拆为 T2a ✅ / T2b ⬜；T1 迁移编号顺延 V3+；§4 补 T2a 实测 |
| v1.3 | 2026-10-18 | T1 完成：V3 建 12 张剩余业务表 + V4~V8 演示种子（数字对齐 §3）+ MyBatis-Plus 分页/租户拦截/时间填充；种子生成器 `backend/scripts/seed-gen` 与抽查脚本 `verify-seed.ps1` 就绪；T1 行 ✅（`8ed73f8`/`85518bf`）、§4 补实测、下一步 T2b |
| v1.4 | 2026-10-18 | T2b 完成：§1.2 注册（校验顺序 / HR 自动建未认证公司 / 40910）+ `AuthServiceRegisterTest`；`mvnw test` 18/18、冒烟 20/20、`verify-seed` 16/16（排除注册记录）；T2b 行 ✅（`030a5b2`）、下一步 T3 |
| v1.5 | 2026-10-18 | T3a 完成（T3 拆「公开读 / HR 写」）：§6.1~§6.4/§6.9 浏览·搜索·详情·相似·埋点 + §7 分类/城市/字典；公开接口可选鉴权、游标分页、详情 ETag；`mvnw test` 35/35、`smoke-jobs` 29/29、鉴权冒烟无回归；T3a 行 ✅（`9f509be`）/ T3b ⬜、§4 补实测、下一步 T3b |
| v1.6 | 2026-10-18 | T3b 完成：§6.5~§6.8 HR 职位写（发布/更新/关闭重开/软删；40310 / 42211 / 42901；job-new-{seq} 递增 id）；`mvnw test` 50/50、`smoke-jobs` 49/49、`verify-seed` 16/16；T3b 行 ✅（`8102f0b`）、§4 补实测、下一步 T4 |
| v1.7 | 2026-10-18 | T4 完成：§9/§10 + §8.7 投递与面试闭环（40911/40913/40914/40915/42210；投递占位建会话；app-new/int-new/conv-new/msg-new 递增 id）；`mvnw test` 77/77、`smoke-applications` 51/51（可重复）、其余冒烟无回归；T4 行 ✅（`d4ce22c`）、§4 补实测、下一步 T5 |
| v1.8 | 2026-10-18 | T5 完成：§5.1~§5.6 公司公开详情/职位、我的公司、资料编辑、成员列表、企业认证（40410/40916/42210；PENDING→约 1.2s 自动 ACTIVE）；管理员以成员角色 ADMIN 为准；`mvnw test` 90/90、`smoke-company` 26/26（可重复）、其余冒烟无回归；T5 行 ✅（`2359625`）、§4 补实测、下一步 T6 |
| v1.9 | 2026-10-18 | T6 完成：§8.1~§8.8 消息 REST + `/ws` STOMP（4 主题）；数据库未读事实源 + Redis 限流/幂等；双端会话超集与扩展详情；双向自动回复演示语义；`mvnw test` 114/114、`smoke-messages` 40/40（可重复）、其余冒烟与种子 16/16 无回归；T6 行 ✅（`8d9513a`）、§4 补实测、下一步 T7 |
| v1.10 | 2026-10-18 | T7 完成：§2 用户 / §3 简历 / §11 收藏 / §12 上传（19 条）；Redis 偏好、软删注销、HR 简历可见性、默认简历、收藏游标、本地目录 + HMAC 上传；`mvnw test` 151/151、`smoke-user` 64/64（可重复）、其余冒烟与种子 16/16 无回归；T7 行 ✅（`c26dc44`）、§4 补实测、下一步 T8 |
| v1.11 | 2026-10-18 | 接口账目定稿：正文 70 条为准，总览表校正为 70；T9 固定手机号重置策略定为每轮 e2e 前清库重迁；**T8/T9 顺序定为 T8 先、T9 后**（均用户拍板） |
