# AI-CONTINUE 修订记录（归档）

> 归档于 2026-10-20。原 `docs/AI-CONTINUE.md` §10。**默认不要读**。

---
## 10. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-09-28 | 首版。取代已过期的 `HANDOFF.md` 作为唯一接续入口：修正端口/命令/状态/模型数等错误，补入 6 项已踩坑记录与协作规范 |
| v1.1 | 2026-09-28 | 补「自动化验收记录」：提交 `ca11abf` 上 24/24 断言通过，附覆盖范围表 |
| v1.2 | 2026-09-28 | 新增可复用验收脚本 `tests/e2e-verify.cjs` + `npm run e2e`；修复 `npm run build` / `npm start` 在 Windows 上不可用的问题（改用跨平台构建脚本）；补「构建与生产启动」说明 |
| v1.3 | 2026-10-16 | 开发交接：定位调整为「运维与深读手册」，状态指针指向根目录 `PROGRESS.md`；补工作区未提交在建改动（投递/面试数据层与 service，未接 UI）的快照说明 |
| v1.4 | 2026-10-16 | 五项验收功能全部完成并接入 UI（投递 / 候选人 / 面试闭环、验证码登录 / 注册、公司资料 / 企业认证）；e2e 扩展至 57/57；补登录页签残留、e2e 文本匹配 / 覆盖层、HR 职位归属等坑记录 |
| v1.5 | 2026-10-16 | 用户人工验收初步通过；补记远端仓库指向（`origin` → GitHub），状态与提交对齐 |
| v1.6 | 2026-10-16 | 人才筛选（演示版）增量：关键词匹配引擎 / 简历种子 / §4.5-§4.6 / 页面接入；e2e 扩展至 63/63；补覆盖层断言取数坑（§6.7） |
| v1.7 | 2026-10-17 | Step 3 T0：`backend/` Spring Boot 3.5.16 骨架（包络 / 错误码 / traceId）与本地环境定案（JBR 21 / MySQL 3307 / Redis 6379 / 免 Docker 直装）；补 §6.8 后端环境坑 |
| v1.8 | 2026-10-17 | Step 3 T2a：鉴权（验证码 / 登录 / 刷新 / 退出 / me）落地；§2 状态行更新；§6.8 补种子 hash 双处同步与 BOOLEAN 建表坑 |
| v1.9 | 2026-10-18 | Step 3 T1：全量表（V3）+ 演示种子（V4~V8）+ 种子生成器与抽查脚本落地；补 §6.9（租户注入策略 / 种子生成器用法 / jsqlparser 取值差异） |
| v1.10 | 2026-10-18 | Step 3 T2b：注册（§1.2 / 40910 / HR 自动建未认证公司）落地；补 §6.10（注册落库与演示库重置 / e2e 固定手机号重跑风险 / LambdaWrapper TableInfo 单测坑） |
| v1.11 | 2026-10-18 | Step 3 T3a：职位公开读（列表 / 搜索 / 详情 / 相似 / 埋点）+ 分类字典（分类树 / 城市 / 字典）落地；补 §6.11（`@Select` 实体转义 / ETag 与 traceId / `nextCursor: null` / 公开接口可选鉴权与租户豁免 / **T9 需加 Next rewrites**） |
| v1.12 | 2026-10-18 | Step 3 T3b：HR 职位写（发布 / 更新 / 关闭·重开 / 软删）落地；补 §6.12（租户 INSERT 不重复列 / `job-new-{seq}` 全局序号 / publishedAt 截毫秒 / 配额缺省 50 与限流 / 40310 语义 / 冒烟软删行与 verify-seed 排除） |
| v1.13 | 2026-10-18 | Step 3 T4：投递 / 面试闭环（§9/§10 + §8.7）落地；补 §6.13（投递占位建会话 / 状态机顺序 / 幂等终态 / 40410 归属隔离 / 四类新 id 前缀 / 冒烟可重复 / T9 契约差异清单）；§9 增演示库重置策略待拍板 |
| v1.14 | 2026-10-18 | Step 3 T5：公司模块 + 企业认证（§5.1~§5.6）落地；补 §6.14（管理员以成员角色 ADMIN 为准 / 改名实时装配 / 认证无独立记录表与异步自动通过 / 公开路径白名单 / 公司职位可见口径 / smoke-company 基线） |
| v1.15 | 2026-10-18 | Step 3 T6：消息 + WebSocket（§8.1~§8.8 + 4 主题）落地；补 §6.15（DB 未读事实源 / 双端超集 / 历史顺序 / 扩展详情 / WS 鉴权与主题 / 发送不回显 / 自动回复无线程上下文 / 幂等边界 / 种子冒烟保护 / T9 前端差异） |
| v1.16 | 2026-10-18 | Step 3 T7：用户 / 简历 / 收藏 / 上传（§2/§3/§11/§12）落地；补 §6.16（重置与改号验证码场景 / Redis 偏好 / 软删注销 / 简历 HR 可见性与默认顺延 / 收藏唯一与游标 / 本地 HMAC 上传与业务落库边界 / T9 前端差异 / T8 未完提醒） |
| v1.17 | 2026-10-18 | 决策结算：接口账目以正文 70 条定稿（总览表已校正）；T9 固定手机号重置策略定为每轮 e2e 前清库重迁；§9 拆为「待确认 / 已定」 |
| v1.18 | 2026-10-18 | 顺序拍板：**T8 先、T9 后**（§9 记为已定，§6.16 同步）；`USE_MOCK` 为全局开关，T9 前必须补齐 §4 |
| v1.19 | 2026-10-19 | Step 3 T8：招聘者 / 候选人 / 人才筛选（§4.1~§4.6）落地——HR 画像读改（`hr_profiles` 非租户注入表，按 `user_id` 归属）、我发布的职位（HR 可见 CLOSED + 契约外 `GET /hr/jobs/{jobId}`）、我收到的投递（超集卡片 + 最近面试）、人才筛选（移植 mock 关键词引擎）；补 §6.17，§9 补两条已定口径（超集出参 / 扩展参数），§2 状态推进到「下一步 T9」 |
| v1.20 | 2026-10-19 | Step 3 T9：全量联调打通（`USE_MOCK=false`，`npm run e2e` **63/63**，连续两轮通过）——`next.config.ts` 加 `/api/v1` `/uploads` rewrites、`.env.local` 切开关、演示账号一键填入不再被 `USE_MOCK` 隐藏、真实登录分支补身份校验、列表统一解包 `.list`、HR 详情改走 §4.4、约面试补 `conversationId`、自动回复改定时重取、e2e 精确豁免 1 条预期 401；新增 `backend/scripts/db-reset.ps1`（清库重迁 + 清 Redis + 重启后端 + 等 health）；补 §6.18（14 条联调坑）、§6.11/§6.13 更新、§9 增「T9 遗留真实分支」待确认项 |
| v1.21 | 2026-10-19 | T9 追加（用户选 A）：**求职者职位列表切真实**——`JobsView` 改 `useQuery + listJobs()`（`GET /jobs`，`limit=100` 单页取全、计数用 `pagination.total`），删除 `src/api/mock/filter.ts`；HR 发布 / 编辑 / 关闭职位在真实模式下对求职者立即生效。§6.18 新增该条坑（limit 默认 20 / 计数用 total / q 已含公司名），遗留清单更新为「分类字典 / 消息会话 / HR 发消息」；§9 改为「部分已定」 |
| v1.22 | 2026-10-19 | 新增 Step 4 AI / RAG 增量方案文档 [`step4-ai-plan.md`](../step4-ai-plan.md) v1.0（选型 / 数据流 / 多租户红线 §5.3 / 代码结构 / V9 表草案 / T-AI0~T-AI7 / 假模型测试策略 / 风险与未决 6 项）；§2 状态表新增该行（原「Step 4 联调」更名「Step 3-T9 联调」）；§9 新增「Step 4 方案（已定 / 待确认子项）」。**本轮只落文档，未改代码** |
| v1.23 | 2026-10-19 | **Step 4 T-AI0 落地**：Spring AI 1.1.8（BOM + OpenAI starter）接入百炼 DashScope 兼容模式；`ai/` 包（`AiCodes` / `AiProperties` / `AiConnectivityService` / `AiController` → `/api/v1/ai/status|ping`）；密钥与模型全部走用户级环境变量 `PINLIAO_AI_*`（`api-key` 占位，无 key 也能启动）。**新增 §6.19（AI 接入 10 条坑）**：`sk-ws-` key 可用性与 coding 域名 401、`enable_thinking=false`（思考型模型 10s+ → 1.0s）、空 `api-key` 会导致启动失败、错误码不能用 `43xxx`、AI 自检端点须鉴权、PowerShell 5.1 下 `java -version` NativeCommandError 吞掉 JDK 探测、key 泄露三连核查法。实测 ping chat 1005ms/44 tokens、embedding 1024 维/538ms；`mvnw test` 176/176、e2e 63/63 无回归；§2 状态行与 §9 同步 |
| v1.24 | 2026-10-19 | **可观测性 T-OBS1 落地（选型由开源 OTel Agent 改为阿里云 ARMS 应用监控探针）**：新增 `scripts/fetch-arms-agent.ps1`（下载探针到 `.local/arms/`）与 `scripts/dev-with-arms.ps1`（`-DryRun` 配置自检含密钥打码 / `-Background` / `-AllowMissingLicenseKey`；`-javaagent` 只挂 fork 出的应用 JVM）；`scripts/_env.ps1` 新增 `PINLIAO_ARMS_*` 与共享函数（`Wait-BackendUp` / `Get-JavaMajor` / `Resolve-PinliaoJavaHome` / `Format-Secret`），`db-reset.ps1` 去重改用它；`common/web/TraceIdFilter` 取值改为「活跃 OTel span 的 traceId → 透传 `X-Trace-Id` → UUID」（契约不变）+ 6 项 `TraceIdFilterTest`；新增 `docs/arms-plan.md` v1.1 与 `backend/README.md`「ARMS 链路」一节。**新增 §6.20（ARMS 接入 12 条坑）**：两产品入口区别（应用监控 vs 链路 OTel 版）、探针实为 OTel javaagent 2.23.0 改名版、只能挂应用 JVM、`jvmArguments` 空格坑、探针工作目录与 `status` 文件、**未配 LicenseKey 时不产生 span**、`%mdc` 探针法、OTel API 安全降级写法、计费与前端监控门禁。实测 `mvnw test` 182/182、真实后端 e2e 63/63（零影响） |
| v1.25 | 2026-10-19 | **可观测性 T-OBS2：ARMS 链路打通**（控制台可见 `pinliao-backend`、包络 traceId 即链路 TraceId、应用 JVM 有到阿里云 80 端口的 Established 上报连接、带探针 e2e 63/63）。**§6.20 补 5 条坑**：① `Start-Process -ArgumentList` 传数组会丢引号 → `-Darms.licenseKey/appName` 进不了应用 JVM（探针加载却不产生 span、控制台看不到应用），必须传单个带引号字符串并回读进程命令行；② `.apsara-apm\java\<版本>\status` **不刷新、不可作授权判据**；③ 判活三条判据（命令行含 `-Darms.*` / 送 `X-Trace-Id` 不回显 / 有到 80 端口连接）；④ `-Darms.*` 与环境变量 `ARMS_LICENSE_KEY`/`ARMS_APP_NAME` 两种形式都可用；⑤ `OTEL_JAVAAGENT_LOGGING=debug` 是非法值（日志变 no-op、连横幅都不打），要用 `-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=debug` |
| v1.26 | 2026-10-20 | **Step 4 T-AI1 + T-AI2**：索引入库（V9 / 分块 / 真实 1024 维嵌入 / rebuild + index-status）与单简历问答（检索 / 引用 / 无依据 / 配额 / 前端 AI 抽屉 / e2e 65 项）落地。**新增 §6.21（问答与引用 12 条坑）**：检索前置租户过滤、假向量无语义导致自然问句落「无依据」、低分不调模型、`[n]` 由后端解析与漏标兜底、chunkId 是 `rc_` hash、首次提问按需建索引、按 HR/日配额与 Redis 降级放行、上游失败统一 50020、审计不落原文、入口按 status 显隐、**CDP `eval` 不能跑 async**、Mock e2e 与后端开关的冲突。实测 `mvnw test` 230/230、`smoke-ai.mjs` 31/31、e2e 65/65（真实 fake provider + Mock 各一轮）、AI 关闭 42220 且入口隐藏 |
| v1.27 | 2026-10-20 | **Step 4 T-AI3 流式问答（SSE）**：`delta` / `citation` / `done` / `error`、`SseEmitter` + 独立线程池、`prepare/answer/stream` 复用校验链路、前端打字机 + 引用双向定位 + 截断自动兜底。**新增 §6.22（流式 9 条坑）**：Next rewrites 缓冲 SSE 且优先于同路径 Route Handler（→ 站点根路径代理 `/api/ai/qa-stream/{id}`）、SSE 客户端 `Accept` 须含 `application/json`（否则错误包络变 500 空响应）、校验必须在返回 emitter 前完成、`done` 才是最终正文、异步请求清 `UserContext`、独立线程池、无 done 兜底、Mock 流式可复现、e2e 不依赖流式内容。实测 `mvnw test` 240/240、`smoke-ai.mjs` 43/43、e2e 65/65（真实 fake provider + Mock 各一轮） |
