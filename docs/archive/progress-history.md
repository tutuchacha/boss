# PROGRESS 历史归档（Step 2~4 明细）

> 归档于 2026-10-20。内容 = 原 `PROGRESS.md` 的「§2 历史增量（不含当前 T-AI3）」「§4 下一步原始明细」
> 「§8 修订记录」，原样保留。**默认不要读**；现行状态见 [`../../PROGRESS.md`](../../PROGRESS.md)。

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

**工程化**：`npm run e2e`（`tests/e2e-verify.cjs`）扩展至 **65 项断言**（含人才筛选 6 项、AI 助手 2 项），覆盖上述全部新链路；
`npm run build` / `npm start` 跨平台修复；`AGENTS.md` / `CLAUDE.md` 由 `next dev` 自动维护。

### 2.1 Step 4 T-AI1 简历索引入库（2026-10-20）

| 项 | 内容 |
|---|---|
| 表 | **V9 `resume_chunks`**（`section/ordinal/title/content/content_hash/model/dim/embedding`；唯一键 `(resume_id, section, ordinal, model)`；`resume_id` 外键级联删除） |
| 分块 | `ResumeChunker`：basics / educations / experiences / projects / skills 五段式，块首带上下文标题；800 字上限按句续写（「（续 n）」）；**手机号等联系方式不入库** |
| 向量 | SPI `AiEmbeddingModel`；`SpringAiEmbeddingAdapter`（`text-embedding-v4`，1024 维，按 ≤10 条切批，失败转 50020）+ `FakeEmbeddingModel`（确定性词袋向量，零费用） |
| 复用 | 按 `content_hash + model` 复用旧向量；**换 provider / 换模型不复用**（查询与状态统计都带 `model`） |
| 接口 | `POST /api/v1/ai/index/rebuild`（HR / 管理员；全量或单份）、`GET /api/v1/hr/applications/{applicationId}/ai/index-status`（按投递校验可见性，跨公司 404） |
| 验证 | `mvnw test` **199/199**（新增分块 5 / 假向量 3 / 索引 9）；`smoke-ai.mjs` **17/17**；真实上游单份 5 块 950ms、全量 35 块 2.1s、二次 0 调用；`verify-seed` 16/16（flyway=9） |

### 2.2 本轮增量：Step 4 T-AI2 单简历问答 + AI 助手（2026-10-20）

| 项 | 内容 |
|---|---|
| 对话 | SPI `AiChatClient`；`SpringAiChatAdapter`（`enable_thinking=false`、token 上限、失败转 50020）+ `FakeChatClient`（固定模板 + `[1]`，零费用） |
| 检索 | `RetrievalService`：`resumeId` 先经 `applications.company_id` 前置过滤（跨租户零泄漏），内存余弦 topK / `qa-min-score` |
| 生成 | `PromptBuilder`（资料 / 指令分区，资料声明为纯数据）+ `CitationAssembler`（解析 `[n]`、去重、漏标兜底 `来源：[1]`） |
| 编排 | `HrResumeQaService`：42220 / 40301 / 42201 / 40401 → 配额 → 按需建索引 → 低分不调模型 → 生成；审计不落原文 |
| 配额 | `AiQuotaService`：按 HR / 自然日 Redis 计数（默认 100，`42902`；Redis 故障降级放行） |
| 接口 | `POST /api/v1/hr/applications/{applicationId}/ai/qa`（无依据固定「简历中没有相关信息。」且 `citations=[]`） |
| 前端 | `src/api/ai.ts` + `mock/ai.ts` 双分支；`HrAiAssistantSheet` 抽屉（快捷问题 / 引用卡片 / 错误态）；入口按 `/ai/status.enabled` 显隐 |
| 验证 | `mvnw test` **230/230**（新增 31）；`smoke-ai` **31/31**；e2e **65/65**（真实 fake provider + Mock 各一轮），AI 关闭 **64/64**、接口 42220、入口隐藏 |

---

## 4. 下一步

1. 用户人工验收**初步通过**（2026-10-16）；继续确认 `docs/AI-CONTINUE.md` §9 的待决事项
   （前端最终框架、接口账目口径等），并按用户后续反馈补齐。
2. **Step 3 后端（Spring Boot）**：按 [`docs/step3-plan.md`](../step3-plan.md) 分块推进
   （T0 → T9，契约正文 70 条；每会话一块，按计划内「收工 checklist」交接）。
   **T0 ✅、T2a ✅（2026-10-17）、T1 ✅、T2b ✅、T3a ✅、T3b ✅、T4 ✅、T5 ✅、T6 ✅、T7 ✅（2026-10-18）、
   T8 ✅、T9 ✅（2026-10-19）**：
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
   - **T4 已交付**：§9.1~§9.5 投递 / 我的投递 / 详情 / 撤回 / HR 状态机 + §10.1~§10.5 面试双端列表 /
     详情 / 接受 / 拒绝 + §8.7 约面试（创建 Interview、投递置 INTERVIEW、写 INTERVIEW_INVITE 消息）；
     状态机对齐 mock：40911 重复投递、40913 状态冲突、40914 不可约面、40915 已有待确认、42210 简历 / 时间前置；
     投递即幂等占位建会话（`conv-new-{seq}`，T6 复用）；新 id 统一 `app-new-` / `int-new-` / `conv-new-` /
     `msg-new-{seq}` 递增派生。实测 `mvnw test` **77/77**（新增 22 + 5）、`smoke-applications.mjs` **51/51**
     （连续两次可重复）、`smoke-jobs` 49/49、`smoke-auth` 20/20、`verify-seed` 16/16 无回归。
   - **T5 已交付**：§5.1~§5.6 公司公开详情 / 公司职位（游标分页）/ 我的公司 / 资料编辑 / 成员列表 /
     企业认证（6 条）；公开读匿名放行；§5.4~§5.6 的 COMPANY_ADMIN 以 `company_members.role=ADMIN`
     为准（演示 HR 账号 `u-hr-1` 满足）；改名无需刷 jobs 行（职位公司名实时装配）；
     认证提交置 PENDING / verified=false，约 1.2s 后后台调度自动通过为 ACTIVE / verified=true；
     40916 已认证、42210 资料校验、40410 公司不存在。实测 `mvnw test` **90/90**、新增
     `smoke-company.mjs` **26/26**（连续两次可重复）、`smoke-jobs` 49/49、`smoke-applications` 51/51、
     `smoke-auth` 20/20、`verify-seed` 16/16 无回归。
   - **T6 已交付**：§8.1~§8.8 消息模块（8 条）+ WebSocket `/ws` STOMP（4 主题）。会话列表 / 详情 /
     历史 / 发起 / 发送 / 已读 / typing / 发送简历 / 关闭；未读计数以数据库为事实源，Redis 只做发送
     限流（30 次/分钟/用户）与 `idempotencyKey` 24h 去重。列表返回双端超集，历史按 createdAt 降序游标；
     扩展 `GET /conversations/{id}` 供 HR 聊天顶栏。TEXT 双向自动回复（关键词 + typing，默认 1.2s），
     T4 面试邀请消息接入实时推送；40912 会话已存在、40410 归属隔离、42201 内容校验。
     实测 `mvnw test` **114/114**（新增消息 24）、新增 `smoke-messages.mjs` **40/40**（连续两次可重复，
     含 REST + STOMP 发送 / typing / 错误队列 / 非法 token）、`smoke-company` 26/26、`smoke-jobs` 49/49、
     `smoke-applications` 51/51、`smoke-auth` 20/20、`verify-seed` 16/16 无回归。
    - **T7 已交付**：§2.1~§2.8 画像 / 密码 / 手机号 / 偏好 / 注销（8 条）+ §3.1~§3.6 简历 CRUD /
      默认简历（6 条）+ §11.1~§11.3 收藏（3 条）+ §12.1~§12.2 预签名 / 确认（2 条），共 19 条。
      外观偏好存 Redis（`pinliao:user:prefs:{userId}`，默认 auto）；注销为软删 + 立即吊销当前
      accessToken；简历仅本人可写、HR 仅可看已投递到本公司的简历（40410 不泄露存在性），
      首份自动默认、删除默认自动顺延；收藏 `(userId, jobId)` 唯一、重复 40910、游标分页带 total；
      上传用本地目录替代 OSS（服务端 objectKey + HMAC 预签名 PUT + `/uploads/**` 静态映射，
      未新增上传元数据表，业务落库由后续 PATCH 完成）。实测 `mvnw test` **151/151**（新增 37）、
      `smoke-user.mjs` **64/64**（连续两次可重复）、既有五项冒烟与 `verify-seed` 16/16 无回归。
    - **T8 已交付**：§4.1~§4.6 招聘者模块（6 条）。§4.1/§4.2 HR 画像读改（`hr_profiles` 不在
      租户注入表集合内，改为显式按 `user_id` 归属；缺失 40410；PATCH 部分更新、空姓名 42201）；
      §4.3 我发布的职位（HR 视角可见 CLOSED，公开详情只认 OPEN；`status/q/hrId/limit`；新增契约外
      `GET /hr/jobs/{jobId}` 供编辑页打开已关闭职位；抽出 `JobViews.HrRecord.of` 统一映射）；
      §4.4 我收到的投递（公司维度 + `status/jobId/q`、更新倒序；出参直接给前端
      `HrApplicationView` 超集：候选人摘要 + 职位摘要 + `conversationId` + 待确认优先的面试）；
      §4.5/§4.6 人才筛选（逐条移植 `mock/talentMatch.ts`：词典长词优先 / 英文技术词 / 中文去停用词、
      技能 3 / 文本 2 权重、证据文案与上限、分值倒序；空条件 42210）。实测 `mvnw test` **173/173**、
      新增 `smoke-recruiter.mjs` **50/50**（可重复执行，临时职位自清理）、既有六项冒烟与
      `verify-seed` 16/16 无回归。
    - **T9 已交付（全量联调）**：前端切真实后端（`.env.local`：`NEXT_PUBLIC_USE_MOCK=false` +
      `API_BASE_URL=/api/v1`；`next.config.ts` 加 `/api/v1` `/uploads` rewrites 代理到 :8080，
      避免跨域、也不需前端拼绝对地址）。修通 7 类差异：①「演示账号一键填入」不再被 `USE_MOCK`
      隐藏（曾导致 e2e 连锁失败 6/61）；②真实登录分支补「身份校验先于写登录态」；③列表统一解包
      `.list`（我的投递 / 面试 / HR 会话 / HR 消息）；④「我的投递」按 `applicationId` 合并 §10.1
      面试；⑤HR 候选人详情改走 §4.4 列表按 id 定位（§9.3 出参形状不同）；⑥约面试透传
      `conversationId`（§8.7 路径必需）且返回类型改 `Interview | Message`；⑦HR 聊天真实模式改
      定时重取（后端 1.2s 自动回复，前端无 WS 连接）。新增 **`backend/scripts/db-reset.ps1`**
      （止后端 → 清库 → Redis FLUSHALL → 起后端 → 等 health → 回读基线）。e2e 质量门禁只豁免
      「错误密码」用例必然触发的 1 条 401。实测 **`npm run e2e` 63/63（连续两轮）**，
      `npx tsc --noEmit` 仍仅 3 个既有错误。
    - **T9 追加（用户选 A，2026-10-19）**：**求职者职位列表已切真实**——`JobsView` 从
      `filterJobsSync`（mock 同步过滤）改为 `useQuery + listJobs()`（真实分支 `GET /jobs`），
      `limit=100` 单页取全、展示条数用后端 `pagination.total`；`src/api/mock/filter.ts` 已删除。
      后端 `q` 同时匹配标题 / 公司名 / 分类 / 子类 / 标签 / 区域（与 mock 口径一致），
      `sort=recommend` 同样按「期望二级=2 / 期望一级=1」加权，故 **HR 发布 / 编辑 / 关闭职位在真实模式下
      对求职者立即生效**（此前只是「看起来生效」）。回归：真实后端 e2e 仍 63/63、Mock 基线仍 63/63。
    - 下一个动作：**待用户拍板**的 T9 剩余遗留——求职者端「分类字典 / 消息会话」仍读 mock，
      HR「发消息」入口仍为 mock 专用；建议独立一轮（T10）处理，且先补求职者消息页的 e2e / 人工验收清单
      （现在 e2e 对该页零覆盖）。另：接口账目口径、前端最终框架仍在 `docs/AI-CONTINUE.md` §9 待确认清单里。
3. 可选增量（未被本轮涵盖）：招聘者端桌面 Web 后台、HR 上传外部简历库、人才筛选语义检索升级（接口不变）等。
4. **Step 4 AI / RAG 增量（T-AI0 ✅ / T-AI1 ✅ / T-AI2 ✅ / T-AI3 ✅，下一步 T-AI4）**：完整方案与任务板见
   [`docs/step4-ai-plan.md`](../step4-ai-plan.md)（T-AI0 依赖连通 → T-AI1 索引 → T-AI2 单简历问答
   MVP → T-AI3 流式 → T-AI4 语义检索 → T-AI5 面试题生成 → T-AI6 工程化 → T-AI7 LangChain4j 对照）。
   - **T-AI0 已交付**：`spring-ai-bom:1.1.8` + `spring-ai-starter-model-openai`；`ai/` 包
     （`AiCodes` / `AiProperties` / `AiConnectivityService` / `AiController`）；`pinliao.ai.enabled`
     总开关 + `spring.ai.openai.*` 全部由**用户级环境变量**注入（`PINLIAO_AI_API_KEY` / `_BASE_URL` /
     `_CHAT_MODEL=qwen3.5-122b-a10b` / `_EMBEDDING_MODEL=text-embedding-v4` / `_EMBEDDING_DIM=1024` /
     `_ENABLED`）；`api-key` 给占位默认值，**无 key 的机器照样能启动**。实测 `/api/v1/ai/ping`：
     chat 1005ms / 44 tokens、embedding 538ms / dim 1024；`mvnw test` **176/176**、e2e **63/63** 无回归。
     两个实测排雷：① `sk-ws-` 前缀 key 走**按量计费兼容模式端点**可用（Coding Plan 专属域名 401）；
     ② `qwen3.5-122b-a10b` 是思考型，必须 `enable_thinking=false`（否则 >10s）。
   - 用户已拍板：通义千问一站式（对话 `qwen3.5-122b-a10b` + 向量 `text-embedding-v4` 1024 维）、
     向量库先内存后 MySQL、Spring AI 主线 + 事后补 LangChain4j、**问答先行**。
   - 下一步：**T-AI4 语义人才检索**（与关键词版并行 A/B；接口 `POST /api/v1/hr/talent/semantic-search`）；
     §10 仍有 5 项待拍板（向量维度 / Ollama 兜底 / 配额 / 是否含求职者侧 / Boot 4 时机）。
   - 顺手修复：`backend/scripts/db-reset.ps1` 在 **Windows PowerShell 5.1** 下 JDK 探测被
     `$ErrorActionPreference='Stop'` 吞成 `major=0`（PS 7 不复现），现已局部降级为 `Continue`。
5. **可观测性增量 ARMS（T-OBS1 ✅ + T-OBS2 ✅ 2026-10-19，链路已打通）**：方案与任务板见
   [`docs/arms-plan.md`](../arms-plan.md)（v1.2，路径 B 阿里云 ARMS **应用监控**）。
   - **T-OBS1 已交付**：`fetch-arms-agent.ps1`（下载探针 5.1.2 到 `.local/arms/AliyunJavaAgent/`）+
     `dev-with-arms.ps1`（`-DryRun` 自检 / `-Background` / `-AllowMissingLicenseKey`；只挂 fork 出的应用 JVM）+
     `_env.ps1` 新增 `PINLIAO_ARMS_*` 与 `Wait-BackendUp` / `Get-JavaMajor` / `Resolve-PinliaoJavaHome` / `Format-Secret`
     （`db-reset.ps1` 去掉重复实现改用共享函数，行为不变）+ `TraceIdFilter` 改为
     「活跃 OTel span 的 traceId → 透传 `X-Trace-Id` → UUID 兜底」+ 6 项 `TraceIdFilterTest` + README 一节。
   - **T-OBS2 已打通**（LicenseKey 写入用户级环境变量后）：
     ✅ 控制台「应用监控 → 应用列表」可见 `pinliao-backend`（用户确认）；
     ✅ 送 `X-Trace-Id=mycustomtraceid123` 时包络**不回显**、返回探针 32 位 traceId → `Span.current()` 命中活跃 span；
     ✅ 应用 JVM 出现到阿里云 **80** 端口的 Established 上报连接（`100.103.107.x:9990` 的 SynSent 属 EagleEye 配置通道，不影响链路）；
     ✅ `db-reset.ps1` 基线不变（`flyway=8 / companies=16 / jobs=51 / applications=9`）+ **带探针** e2e **63/63**。
   - **修掉一个真 bug**：`dev-with-arms.ps1 -Background` 用 `Start-Process -ArgumentList <数组>` 时 PowerShell
     只按空格拼接、丢参数内引号 → `-Dspring-boot.run.jvmArguments="… -Darms.licenseKey=… -Darms.appName=…"`
     被 Maven 拆开，应用 JVM **只拿到 `-javaagent`**（探针加载但不产生 span、控制台看不到应用）。
     改为单个带引号参数字符串后正常，并用 WMI 回读应用 JVM 命令行确认三个参数都在。
   - **坑**：`.apsara-apm\java\<版本>\status` **不刷新**，链路已通时仍显示 `licenseKey=null`；
     判活看「应用 JVM 命令行含 `-Darms.*` + 送 `X-Trace-Id` 不回显 + 有到阿里云 80 端口的 Established 连接」三条。
   - 下一步（可选，T-OBS3）：慢 SQL / 异常分析 / JVM 大盘核对；前端监控（Web SDK）需最后做且加开关（会碰 e2e 零控制台错误门禁）。

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
| v1.12 | 2026-10-18 | Step 3 T4 完成：投递与面试闭环（§9/§10 + §8.7，10 条）——投递 / 列表 / 详情 / 撤回 / HR 状态 + 双端面试列表 / 详情 / 接受 / 拒绝 + 约面试；40911/40913/40914/40915/42210 状态机语义；投递占位建会话（`conv-new-{seq}`，详情返回 conversationId）；`mvnw test` 77/77、`smoke-applications` 51/51（可重复）、`smoke-jobs` 49/49、鉴权 20/20、种子 16/16（排除 T4 四类前缀）；下一步 T5 公司模块 + 企业认证（§5） |
| v1.13 | 2026-10-18 | Step 3 T5 完成：公司模块 + 企业认证（§5.1~§5.6，6 条）——公开公司详情 / 公司职位（游标分页）/ 我的公司 / 资料编辑（改名职位公司名实时同步）/ 成员列表 / 企业认证（PENDING → 约 1.2s 自动 ACTIVE）；管理员以 `company_members.role=ADMIN` 为准；40410 / 40916 / 42210；`mvnw test` 90/90、`smoke-company` 26/26（连续两次可重复）、职位 49/49、投递 51/51、鉴权 20/20、种子 16/16；下一步 T6 消息 + WebSocket（§8） |
| v1.14 | 2026-10-18 | Step 3 T6 完成：消息 + WebSocket（§8.1~§8.8 + 4 主题）——会话列表 / 详情 / 历史 / 发起 / 发送 / 已读 / typing / 发送简历 / 关闭；DB 未读事实源 + Redis 限流 / 幂等；双端超集 + 扩展详情；双向自动回复；40912 / 40410 / 42201 / 42901；`mvnw test` 114/114、`smoke-messages` 40/40（可重复）、其余冒烟与种子 16/16 无回归；下一步 T7 求职者 / 简历 / 收藏 / 上传 |
| v1.15 | 2026-10-18 | Step 3 T7 完成：§2 用户画像 / 密码 / 手机号 / 偏好 / 注销（8 条）+ §3 简历 CRUD / 默认简历（6 条）+ §11 收藏（3 条）+ §12 预签名上传（2 条）；Redis 偏好、软删注销、HR 简历可见性、收藏唯一 / 游标、本地目录 + HMAC 上传；`mvnw test` 151/151、`smoke-user` 64/64（连续两次可重复）、其余冒烟与种子 16/16 无回归；修复 `smoke-messages` 迟到自动回复导致的可重复性抖动；下一步 T8 招聘者 / 候选人 / 人才筛选（§4） |
| v1.16 | 2026-10-18 | 决策结算：接口账目以正文 70 条为准，总览表已校正为鉴权 7 / 职位 10 / 消息 9（合计 70）；T9 e2e 固定手机号重置策略定为**每轮前清库重迁**；T8 与 T9 先后定为 **T8 先、T9 后**（不引入模块级 Mock 开关，避免二次返工） |
| v1.17 | 2026-10-19 | Step 3 T8 完成：§4 招聘者模块（§4.1~§4.6，6 条）——HR 画像读改（按 `user_id` 归属，非租户注入表）、我发布的职位（HR 可见 CLOSED + 契约外 `GET /hr/jobs/{jobId}`）、我收到的投递（超集卡片 + 最近面试）、人才筛选（移植 mock 关键词引擎）；出参取「前端超集」口径；`mvnw test` 173/173（151→173）、`smoke-recruiter` 50/50（新增，可重复）、既有六项冒烟与 `verify-seed` 16/16 无回归；下一步 T9 全量联调（切 `USE_MOCK=false`，先落实清库重迁重置脚本） |
| v1.18 | 2026-10-19 | Step 3 T9 完成：全量联调打通（`USE_MOCK=false`，`npm run e2e` **63/63**，连续两轮）——Next rewrites 代理 `/api/v1`+`/uploads`、`.env.local` 切开关、演示账号一键填入不再被 `USE_MOCK` 隐藏、真实登录补身份校验、列表统一解包 `.list`、HR 详情改走 §4.4、约面试补 `conversationId`、自动回复改定时重取、e2e 精确豁免 1 条预期 401；新增 `db-reset.ps1`（清库重迁 + 清 Redis + 起后端 + 等 health）；§6.3 更新（新增 T9 联调 / 重置脚本 / 遗留清单四条）；遗留：求职者端职位列表 / 分类字典 / 消息会话仍读 mock，待用户拍板是否本轮切换 |
| v1.19 | 2026-10-19 | T9 追加（用户选 A）：**求职者职位列表切真实**——`JobsView` 改 `useQuery + listJobs()`（`GET /jobs`，`limit=100` + 后端 `total`），删除 `src/api/mock/filter.ts`；HR 发布 / 编辑 / 关闭职位从此在真实模式下对求职者立即生效。回归：真实后端 e2e **63/63**、Mock 基线 **63/63**、`tsc` 仍 3 个既有错误。§6.3 遗留清单更新为「分类字典 / 消息会话 / HR 发消息」三项 |
| v1.20 | 2026-10-19 | 新增 Step 4 AI / RAG 增量方案：[`docs/step4-ai-plan.md`](../step4-ai-plan.md) v1.0（目标与 DoD、选型、数据流、代码结构、V9 表草案、T-AI0~T-AI7 任务板、测试策略、风险与未决 6 项）。§0 快照 / §1 阶段表新增该行（原「Step 4 联调」更名为「Step 3-T9 联调」）、§4 下一步新增第 4 条；用户拍板：通义千问一站式、先内存后 MySQL、Spring AI 主线 + 事后补 LangChain4j、问答先行、**本轮只落文档不改代码** |
| v1.21 | 2026-10-19 | **Step 4 T-AI0 完成**：`spring-ai-bom:1.1.8` + `spring-ai-starter-model-openai`；新增 `ai/` 包（`AiCodes` 42220/42221/42902/50020、`AiProperties`、`AiConnectivityService`、`AiController` 的 `/api/v1/ai/status|ping`）；模型 / 地址 / 密钥 / 开关全部由**用户级环境变量** `PINLIAO_AI_*` 注入（`api-key` 占位保证无 key 可启动，key 经核查仓库零命中）。实测 ping：chat 1005ms/44 tokens、embedding 1024 维/538ms；`mvnw test` **176/176**（+3）、e2e **63/63**、`verify-seed` 16/16 无回归。修正方案两处：错误码 `43xxx` 不可用（契约前 3 位=HTTP 状态）→ 改 `42220/42221/42902/50020`；对话模型定为 `qwen3.5-122b-a10b`（思考型，须 `enable_thinking=false`）。顺手修复 `db-reset.ps1` 在 PowerShell 5.1 下的 JDK 探测 bug |
| v1.22 | 2026-10-19 | **可观测性 T-OBS1 完成（选型由 OTel Agent 改为阿里云 ARMS 应用监控探针）**：新增 `fetch-arms-agent.ps1`（下载探针 5.1.2 到 `.local/arms/`）与 `dev-with-arms.ps1`（配置自检 / 后台起 / 等 health UP；`-javaagent` **只挂 fork 出的应用 JVM**）；`_env.ps1` 新增 `PINLIAO_ARMS_*` 与 `Wait-BackendUp` / `Get-JavaMajor` / `Resolve-PinliaoJavaHome` / `Format-Secret` 共享函数（`db-reset.ps1` 去重，行为不变）；`TraceIdFilter` 取值改为「活跃 OTel span → 透传 `X-Trace-Id` → UUID」（契约不变，e2e 不校验 traceId）+ 6 项单测；方案文档升 v1.1（含选型理由与实测记录）。实测：探针 `apsara-java-agent-5.1.2-otel-2.23.0`（OTel 2.23.0 改名版）加载正常、应用 health UP；`mvnw test` **182/182**、真实后端 e2e **63/63**（零影响）。**未配 LicenseKey 时探针不产生 span**（`status` 文件 `licenseKey=null`；W3C `traceparent` / `EagleEye-TraceId` 头均不被采纳；请求期 MDC 只有本项目 `traceId`）→ T-OBS2 的「包络 traceId == ARMS TraceId」待用户提供 LicenseKey 后实测，备选口径见 `docs/arms-plan.md` §6 |
| v1.23 | 2026-10-19 | **可观测性 T-OBS2 完成（ARMS 链路打通）**：LicenseKey 写入用户级环境变量后，控制台「应用监控 → 应用列表」可见 `pinliao-backend`；送 `X-Trace-Id=mycustomtraceid123` 时包络**不回显**而是探针 32 位 traceId（`Span.current()` 命中活跃 span，`TraceIdFilter` 走 span 分支）；应用 JVM 出现到阿里云 80 端口的 Established 上报连接；带探针 `db-reset.ps1`（基线不变）+ 真实后端 e2e **63/63**。**修 `dev-with-arms.ps1 -Background` 真 bug**：`Start-Process -ArgumentList` 数组按空格拼接丢引号 → `-Dspring-boot.run.jvmArguments="… -Darms.licenseKey=… -Darms.appName=…"` 被 Maven 拆开，应用 JVM 只拿到 `-javaagent`（探针加载却不产生 span、控制台看不到应用）；改为单个带引号参数字符串并用 WMI 回读确认。新增「判活三条判据」，并明确 `.apsara-apm\java\<版本>\status` **不刷新、不可作授权判据**。文档：`docs/arms-plan.md` v1.2、`docs/AI-CONTINUE.md` §6.20 补 5 条坑、`backend/README.md`、`CHANGELOG.md`、worklog Task 35 |
| v1.24 | 2026-10-19 | **进度存档 + README 展示更新**：`README.md` 改为真实状态（Step 2/3 已完成、T-AI0 ✅、T-OBS1+T-OBS2 ✅；新增「进度速览」表、ARMS 快捷用法小节、`mvnw test` 基线 182/182、下一步推进建议）；`PROGRESS.md` 新增 §0.1「接续即用」（HEAD/运行态/基线/环境变量/恢复命令/下一步 + 判活自检入口），便于换人或换 AI 直接续做 |
| v1.25 | 2026-10-20 | **Step 4 T-AI1 简历索引入库**：V9 `resume_chunks`（唯一键 `(resume_id, section, ordinal, model)`，不冗余 `company_id`）、`ResumeChunker` 五段式分块（标题上下文 / 800 字续写 / 手机号不入库）、`AiEmbeddingModel` SPI + 真实 `text-embedding-v4`（≤10 切批）/ `FakeEmbeddingModel`、`ResumeIndexService`（全量 / 单份 / 状态）、`AiIndexController`；`mvnw test` **199/199**（+17）、`smoke-ai.mjs` 17/17、真实单份 950ms / 全量 2.1s；§2.1 新增本轮增量表 |
| v1.26 | 2026-10-20 | **Step 4 T-AI2 单简历问答（带引用）+ HR 候选人详情 AI 抽屉**：`AiChatClient` SPI（`SpringAiChatAdapter` 关思考 / `FakeChatClient`）、`RetrievalService`（可见性前置过滤 + 余弦）、`PromptBuilder`、`CitationAssembler`（`[n]` 解析与兜底）、`HrResumeQaService`（低分不调模型 / 按需建索引）、`AiQuotaService`（HR/日配额，Redis 故障放行）、`POST /hr/applications/{id}/ai/qa`；前端 `src/api/ai.ts` + `mock/ai.ts` + `HrAiAssistantSheet`（入口按 `/ai/status.enabled` 显隐）。`mvnw test` **230/230**（+31）、`smoke-ai` **31/31**、e2e **65/65**（真实 fake provider + Mock 各一轮）、AI 关闭 64/64 且 42220；§0/§0.1/§1/§2.2/§7.2 同步 |
| v1.27 | 2026-10-20 | **Step 4 T-AI3 流式问答（SSE + 打字机 + 引用双向定位）**：后端 `AiChatClient.stream` / `SpringAiChatAdapter`（Flux → `toIterable`）/ `FakeChatClient`、`HrResumeQaService.prepare/answer/stream`、`SseEmitter` + `AiQaSseSink`（`delta→citation→done`，`error` 仅流内失败）、`AuthInterceptor` 改 `AsyncHandlerInterceptor`；前端 `requestStream` + SSE 解析与截断兜底、`HrAiAssistantSheet` 打字机 + `[n]`↔来源卡片双向定位/高亮。**关键坑**：Next `rewrites` 会缓冲 SSE 且优先于同路径 Route Handler → 新增站点根路径代理 `/api/ai/qa-stream/{id}`（Web Streams 透传），不退回 STOMP；SSE 客户端 `Accept` 须含 `application/json`（否则校验类错误变 500 空响应）。`mvnw test` **240/240**（+10）、`smoke-ai` **43/43**（+12）、e2e **65/65**（真实 fake provider + Mock 各一轮）；§0/§0.1/§1/§2.3/§4/§7.2 同步 |
