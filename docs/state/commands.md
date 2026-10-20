# 命令 / 冒烟 / 验收（state/commands.md）

> 由 `PROGRESS.md` §7 与 `docs/AI-CONTINUE.md` §5 的独有部分合并（2026-10-20），是命令类内容的唯一出处。
> 状态与时序以 [`../../PROGRESS.md`](../../PROGRESS.md) 为准。

---
## 7. 运行命令与测试结果

### 7.1 前端（2026-10-19 实测：Mock 基线 + 真实后端联调）

```powershell
# —— Mock 基线（USE_MOCK=true，不依赖后端）——
npm run dev            # 端口 3000；另开终端保持运行
npm run e2e            # 端到端验收（需 dev server）；Mock 基线 68/68
npx tsc --noEmit       # 仅 3 个既有错误，新文件零错误
npm run lint           # ESLint（存量告警见 §6.1）
npm run build          # 生产构建（需 Google Fonts 可达；构建脚本自带产物自检）
npm start              # 生产启动（node .next/standalone/server.js；可用 $env:PORT 换端口）

# —— 真实后端联调（Step 3 T9 起）——
cd backend; .\scripts\db-reset.ps1   # 每轮 e2e 前：止后端 → 清库重迁 → Redis FLUSHALL → 起后端 → 等 health
cd ..
npm run dev            # .env.local 已置 NEXT_PUBLIC_USE_MOCK=false / API_BASE_URL=/api/v1；改后必须重启
npm run e2e            # 真实后端下 68/68（2026-10-20，fake AI provider；Mock 分支同 68/68）
```

可覆盖的环境变量：`npm run e2e` 支持 `BASE_URL` / `CHROME_PATH` / `CDP_PORT`；
`next.config.ts` 代理目标支持 `API_PROXY_TARGET`（默认 `http://localhost:8080`）。

### 7.2 后端 `backend/`（2026-10-20 实测，Step 3 T0~T9 + Step 4 T-AI0/T-AI1/T-AI2/T-AI3/T-AI4 + 可观测性 T-OBS1/T-OBS2）

```powershell
cd backend
$env:JAVA_HOME='C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\jbr'  # 必设（本机仅 JDK 1.8）
.\scripts\dev-up.ps1         # 起 MySQL 专用实例 3307 + Redis 6379（首次先 .\scripts\db-init.ps1）
.\scripts\db-reset.ps1       # 清库重迁（drop+create → Redis FLUSHALL → 起后端 → 等 health UP → 回读基线）
.\mvnw.cmd spring-boot:run   # 后端 :8080；Ctrl+C 停止（收尾用 .\scripts\dev-down.ps1）
.\mvnw.cmd test              # 单元测试（包络 / JWT / 种子密码 / 租户策略 / 注册 / 职位 / 职位写 / 字典 / 投递面试 / 公司 / 消息 / 用户 / 简历 / 收藏 / 上传 / 招聘者 / AI 自检 / AI 分块与索引 / AI 问答与流式）
node scripts\smoke-auth.mjs  # 鉴权接口冒烟（20 项，含注册；需后端已启动）
node scripts\smoke-jobs.mjs  # 职位与字典冒烟（49 项；需后端已启动，T3a/T3b）
node scripts\smoke-applications.mjs  # 投递与面试冒烟（51 项；需后端已启动，T4，可重复执行）
node scripts\smoke-company.mjs  # 公司模块冒烟（26 项；需后端已启动，T5，可重复执行）
node scripts\smoke-messages.mjs  # 消息与 WebSocket 冒烟（40 项；需后端已启动，T6，可重复执行）
node scripts\smoke-user.mjs  # 用户 / 简历 / 收藏 / 上传冒烟（64 项；需后端已启动，T7，可重复执行）
node scripts\smoke-recruiter.mjs  # 招聘者模块冒烟（50 项；需后端已启动，T8，可重复执行）
node scripts\smoke-ai.mjs    # AI 冒烟（54 项：索引 + 问答 + SSE 流式 + 语义人才检索；需 PINLIAO_AI_ENABLED=true + PROVIDER=fake；默认拒绝真实上游）
.\scripts\verify-seed.ps1    # 种子行数抽查（16 项；需 Flyway 已迁移至 v9）
.\scripts\dev-with-arms.ps1 -DryRun   # 可观测性：ARMS 探针配置自检（需 PINLIAO_ARMS_ENABLED=true；密钥打码）
.\scripts\dev-with-arms.ps1           # 带 ARMS 应用监控探针启动（收工用 .\scripts\db-reset.ps1 恢复无探针）
.\mvnw.cmd -DskipTests package  # 打包（target/pinliao-backend-*.jar）
```

| 检查 | 结果 |
| --- | --- |
| `mvnw.cmd test` | **253/253 通过**（包络 3 + JwtService 4 + 种子密码 hash 1 + 租户策略 6 + 注册 4 + 职位 13 + 职位写 15 + 公开路径 2 + 字典 3 + 投递面试写 22 + 投递面试读 5 + 公司 12 + 消息 24 + 用户 13 + 简历 10 + 收藏 5 + 上传 9 + 招聘者 22 + **AI 自检 3**（T-AI0）+ **traceId 优先级 6**（T-OBS2）+ **AI 分块 / 索引 17**（T-AI1）+ **AI 问答 31**（T-AI2：`SpringAiChatAdapterTest` 3 断言思考模式与 token 上限；`PromptBuilderTest` 1 断言资料/指令分区与无依据话术；`CitationAssemblerTest` 5 断言 `[n]` 解析 / 越界过滤 / 去重 / 漏标兜底；`RetrievalServiceTest` 5 断言余弦排序 / min-score / 空库 / 可见性参数；`AiQuotaServiceTest` 3 断言计数 / 超限 42902 / Redis 故障放行；`HrResumeQaServiceTest` 14 断言关闭 42220 / 角色 40301 / 空与超长 42201 / 跨公司 40401 / 低分不调模型 / 索引按需重建 / 引用与审计 / 配额顺序）+ **AI 流式 10**（T-AI3：`FakeChatClientTest` 2 断言分段回调与拼接一致；`SpringAiChatAdapterTest` 3 断言逐块 / 空块跳过 / 空流转 50020；`HrResumeQaServiceTest` 5 断言 `prepare` 校验顺序 / `stream` 的 done 与 error 事件 / 索引按需重建）+ **AI 语义检索 13**（T-AI4：`SemanticTalentSearchServiceTest` 断言开关 42220 / 角色 40301 / 空条件 42210 / 超长 42201 / 公司范围 / 按需索引 / 分数阈值 / 同人去重 / 证据条数 / 维度不一致 42221 / 配额顺序 / limit 上限 20 / snippet 截断）+ 公开路径加 2 项断言） |
| `spring-boot:run` | **约 3.5s 启动**（T7 重启实测，schema 已最新无需迁移；T1 首迁为 3.3s）；Flyway 校验 **V1~V9** 通过 |
| `/actuator/health` | `{"status":"UP"}`（200） |
| `/api/v1/_demo/*` 包络 | 与 T0 一致：`traceId`、`42201/40401` 等码位不变 |
| 鉴权冒烟（`backend/scripts/smoke-auth.mjs`） | **20/20 全对（T7 后复跑无回归）**：登录 200（含云杉 company/member）；`40110/40101/40102/40103/42201/42901` 全码位；refresh 一次性轮换；退出后 me=40102；**注册段**：发码 200 → 坏码 40110 → HR 成功（`新注册企业-{尾4}` / ADMIN / verified=false）→ 重复 40910 → me 带新 token → 求职者无 company |
| 职位 & 字典冒烟（`backend/scripts/smoke-jobs.mjs`） | **49/49 全对（T7 后复跑无回归）**：公开读 29 项（列表匿名默认 20 / total=38 / 游标翻页；筛选上海 12、互联网·前端 2、1-3年 11、20-40K 18；搜索云杉 7 / 前端关键词 / §6.2 独立路径；recommend 期望岗位置顶；详情 HR / 福利 4 / 相似 6、ETag→304、40410；埋点去重；分类 17 根/84 节点；城市 9/热门 5；字典与 mock 完全一致）+ **写接口 20 项**（未登录 401 / 求职者 40301 / 字段校验 42201；发布 `job-new-*` OPEN 归属云杉、hr 陈女士；发布后可见 39；更新部分字段且保留发布时间与统计；关闭后详情 404 + 可见回到 38；重开再次可见；他公司 HR 与不存在职位 40310；软删 `{}` + 404 + 回到 38 基线；兜底幂等清理） |
| 投递与面试冒烟（`backend/scripts/smoke-applications.mjs`） | **51/51 全对（T7 后复跑无回归），连续两次执行均通过（可重复）**：§9.1 未登录 401 / HR 40301 / 简历越权 42210 / 默认简历投递 `app-new-*` SUBMITTED / 重复 40911；§9.2~§9.3 total≥4、状态筛选、游标翻页不重不漏、非法游标 40001、详情（job/resume/`conv-new-*`）、越权 40410；§9.4~§9.5 VIEWED / 非法状态 42201 / 他公司 40410；§8.7 40914 / 40915 / 非法形式 42201 / ONSITE 缺地点 42201 / 时间早于当前 42210 / 越权 40410 / 成功生成 `msg-new-*` INTERVIEW_INVITE 未读消息；§10 双端列表 / 详情 / 越权 / 接受幂等 / 拒绝 / 再次约面；收尾撤回 40913 + 撤回后重新投递复用记录并收尾 WITHDRAWN |
| 公司模块冒烟（`backend/scripts/smoke-company.mjs`） | **26/26 全对（T7 后复跑无回归），连续两次执行均通过（可重复）**：§5.1/§5.2 匿名公司详情（云杉科技 / verified）与 40410、公司职位 7 条、限 3 游标翻两页 6 条不重不漏；§5.3 未登录 401 / HR 云杉 / 求职者 40301；§5.4/§5.5 临时 `co-reg-*` HR 注册自动 ADMIN、求职者越权 40301、名称过短 42210、改名 trim + 字段落库 + 公开详情即时同步、成员列表 user/hrProfile、发布 `job-new-*` 后公司职位显示新公司名、finally 软删回到 0；§5.6 非法资料 42210 / 求职者 40301 / 提交 PENDING→轮询约 1.2s 自动 ACTIVE / 重复 40916 / 公开详情同步 |
| 消息 & WebSocket 冒烟（`backend/scripts/smoke-messages.mjs`） | **40/40 全对，连续两次执行均通过（可重复）**：§8.1/§8.2 求职者 2 条 / HR 6 条种子会话与未读 2 / 4、双端列表超集、游标不重不漏、非法游标 40001、详情、历史降序、越权 40410；§8.3 复用 `conv-new-*`、重复 40912、HR 40301；§8.4~§8.6 发送字段 / HR 未读 +1 / 幂等同 id / 关键词自动回复 / 候选人自动回复 / typing 空对象 / 双端已读；§8.7 发送默认简历 `RESUME`；§8.8 求职者 40301 / HR 关闭；§10 WS token 握手、REST 发送推送 `/user/queue/messages`、typing topic、STOMP `/app/.../send|typing`、错误队列 42201、非法 / 匿名 token 拒绝 |
| 用户 / 简历 / 收藏 / 上传冒烟（`backend/scripts/smoke-user.mjs`） | **64/64 全对，连续两次执行均通过（可重复）**：§2 未登录 / HR 角色校验、画像读改、薪资联动 42201、改密原密码错误、重置密码坏码 40110 / 成功、手机号占用 40910 / 改号后新号可登录旧号 40110、偏好默认 auto / 改 dark / 非法 theme 42201、注销后 token 40102；§3 空列表、首份自动默认、非法 content 42201、第二份非默认、更新 / 详情 / set-default 排序、HR 未投递不可见 40410、软删后 404；§11 三职位收藏 / 重复 40910 / 不存在职位 40410 / HR 40301 / limit=2 两页不重不漏 total=3 / 取消幂等 / total=0；§12 未登录 401、签名 + PUT 直传 + confirm + `/uploads/**` 静态读回、非法 MIME / 扩展名 42201、篡改签名 40301、确认他人 objectKey 40410 |
| 注册边界（手工 4 例） | 字段校验 42201 ×3（phone / password / role）；未发码注册 40110；`co-reg-*` 落库 `verified=false` |
| 招聘者模块冒烟（`backend/scripts/smoke-recruiter.mjs`） | **50/50 全对，可重复执行**：§4.1 未登录 40101 / 求职者 40301 / HR 画像本人 + 本公司；§4.2 部分更新只改入参、空姓名 42201、空串清空可选字段、还原种子值、越权 40301；§4.3 本公司 7 条种子职位 + 超集字段、`status=OPEN` / `CLOSED` 过滤、非法状态 42201、`q` 标题 / `hrId` 归属筛选、不存在 40410、求职者 40301，并现场创建 `job-new-*` → 关闭 → **HR 可见 CLOSED 而公开详情 40410** → 软删后 HR 详情 40410 → 回到基线；§4.4 6 条种子投递 + 超集字段（候选人 / 职位 / `conversationId` / 面试）、更新倒序、`status=OFFER` / `jobId` / `q=林小满` 过滤、非法状态 42201、越权 40301、面试待确认优先（int-hr-01 PENDING）与次选最近（int-hr-02 COMPLETED）；§4.5 `React` 命中林小满（score 3、证据 `技能 / 个人简介 / 项目经历 · 招聘管理系统前端重构`、片段含「技术栈」）、`COBOL` 空数组、仅标签 `自动化测试` 命中沈禾、多关键词权重倒序、空条件 42210、越权 40301；§4.6 词表 20 条 + 未登录 40101 |
| 种子抽查（`backend/scripts/verify-seed.ps1`） | **16/16 命中**（注册 `u-reg-*` / `co-reg-*`、T3b `job-new-*`、T4/T6 `app-new-*` / `int-new-*` / `conv-new-*` / `msg-new-*` 均已排除；T7 起 resumes 另排除 `u-reg-*` 与软删行）：flyway v9；companies 16；jobs 51（可见 38 / 云杉 7）；categories 84；cities 9；users 8；resumes 7；投递 6+3；interviews 3；HR 会话 6（未读 4）；求职者会话 2；messages 16 |
| 数据库抽查（MySQL 3307） | V4~V8 success=1；种子内容与 mock 一致（职位关闭规则 stableHash(id)%4、简历 content JSON 原文、会话 id 序列 conv-hr-1/4/8/11/14/16 等）；T2b 后新增 `u-reg-*` / `co-reg-*` 等注册记录（持久保留） |
| 清库重迁脚本（`backend/scripts/db-reset.ps1`） | **实测可用**：止端口 8080 后端 → `DROP + CREATE pinliao` → Redis `FLUSHALL` → 起后端 → `/actuator/health` UP（约 8s 含 Maven）→ 回读 `flyway=9 / companies=16 / jobs=51 / applications=9`；随后 `verify-seed.ps1` 16/16。**T-AI0 修复**：`Get-JavaMajor` 在 Win PowerShell 5.1 + `$ErrorActionPreference='Stop'` 下会把 `java -version`（走 stderr）抛的 NativeCommandError 吞掉而恒返回 0（PS 7 不复现），现已局部降级为 `Continue`；5.1 与 7 均实跑通过。**T-OBS1 重构**：JDK 探测与 `/actuator/health` 轮询改用 `_env.ps1` 共享函数（`Get-JavaMajor` / `Resolve-PinliaoJavaHome` / `Wait-BackendUp`），脚本行为不变（复跑基线 `flyway=9 / companies=16 / jobs=51 / applications=9`） |
| **AI 自检（Step 4 T-AI0）** | **实测通过**：`GET /api/v1/ai/status` → `enabled=true / openai-compatible / qwen3.5-122b-a10b / text-embedding-v4`；`GET /api/v1/ai/ping` → chat `1005ms` `44 tokens`、embedding `538ms` `dim 1024`，端到端 1.56s（需 HR token；`enabled=false` 时 ping 返回 42220）；`spring-ai-bom:1.1.8` + `spring-ai-starter-model-openai` 启动无异常 |
| **ARMS 探针（可观测性 T-OBS1 ✅ / T-OBS2 ✅）** | **链路已打通**：`Load Apsara Java Agent version: 5.1.2.` / `report to region: cn-hangzhou`；探针身份 `apsara-java-agent-5.1.2-otel-2.23.0`；`-javaagent` + `-Darms.appName` + `-Darms.licenseKey` **只出现在 fork 出的应用 JVM**（Maven JVM 无，WMI 命令行核对）；带探针 `/actuator/health` UP。**配好 LicenseKey 后**：控制台「应用监控 → 应用列表」可见 `pinliao-backend`（用户确认）；送 `X-Trace-Id=mycustomtraceid123` 时包络不回显、返回探针 32 位 traceId（如 `18c2a41966c9f7e920b455c6fa4d1aa6`）→ span 分支命中；应用 JVM 有到阿里云 80 端口 Established 上报连接（`121.43.177.209:80` 等）。⚠️ `.apsara-apm\java\<版本>\status` **不可作判据**（链路已通仍显示 `licenseKey=null`）。**未配 LicenseKey 时的旧结论**：探针不产生 span、`traceparent`/`EagleEye-TraceId` 均不被采纳。**T-OBS2 修 bug**：`-Background` 的 `Start-Process -ArgumentList` 数组丢引号 → `-Darms.*` 没进应用 JVM（现改为单个带引号参数字符串） |
| **AI 简历索引（Step 4 T-AI1）** | **实测通过**：假向量 `provider=fake` → `smoke-ai.mjs` **17/17**（未登录 40101 / 求职者 40301 / 全量 7 份简历 **35 块** / 二次 `embedded=0` / 单份 `resume-u-cand-1` 5 块 / 跨公司投递 40401 / 不存在简历 40401）；真实 `text-embedding-v4` → 单份 5 块 **950ms**、全量 35 块 **2.1s**、二次 **0 调用** 185ms；库内抽查 `model=text-embedding-v4`、`dim=1024`、单行 `embedding` 约 12.7KB、**手机号 0 命中**；`GET /api/v1/ai/status` 的 `provider` 回显 `springai` / `fake` |
| **AI 单简历问答（Step 4 T-AI2）** | **实测通过**：fake 向量 + fake 对话 → `smoke-ai.mjs` **31/31**（未登录 40101 / 求职者 40301 / 空问题与超长 42201 / 跨公司与不存在投递 40401 / 有依据回答 `grounded=true` + `[1]` + 引用结构完整 / 简历外问题固定「简历中没有相关信息。」且 `citations=[]`）；AI 关闭实例（`PINLIAO_AI_ENABLED=false`）→ `/ai/status.enabled=false`、`/ai/qa` **422 + 42220**、前端无入口；`mvnw test` **230/230** |
| **AI 流式问答（Step 4 T-AI3）** | **实测通过**：`smoke-ai.mjs` **43/43**（新增 12 项：流式鉴权 / 入参 / 跨公司仍是 JSON 包络、200 + `text/event-stream`、`Cache-Control` / `X-Accel-Buffering`、`delta→citation→done` 且无 `error`、delta 拼接含 `[1]` 且与 `done.answer` 一致、无依据无 citation）；直连 :8080 ttfb **151ms** / delta 逐条 152-279ms；经 Next 代理 ttfb **54ms** / 逐条 55-189ms（直连 `rewrites` 会一次性缓冲，故改用站点根路径代理）；浏览器实测打字机在 109/167/219ms 递增、引用双向定位与高亮、断网提示与截断自动兜底；`mvnw test` **240/240** |
| **AI 语义人才检索（Step 4 T-AI4）** | **实测通过**：`smoke-ai.mjs` **54/54**（新增 11 项：未登录 40101 / 求职者 40301 / 空条件 42210 / 超长 42201 / 正常检索 200 且按 score 倒序 / 首位为林小满 `app-hr-01` / 证据结构含五段 section / 仅标签命中 / `limit=1` 生效 / 跨公司独有经历搜不到 / 检索后索引仍就绪）；`provider=fake` 下 `React` 命中林小满，分数 0~1；`mvnw test` **253/253**（新增 13 项） |
| **前端 e2e（真实后端，`USE_MOCK=false`）** | **68/68 通过（2026-10-20）**：真实后端 + `PINLIAO_AI_PROVIDER=fake`（零对话费用）一轮、Mock 分支一轮；新增「AI 入口与开关一致」「AI 问答返回带引用的回答」「语义检索入口 / score 结果 / 无关候选不出现」；AI 关闭时语义与问答用例随入口隐藏跳过；零控制台错误（仅豁免「错误密码」用例的 1 条预期 401）、零 hydration 报错；Chrome 进程数前后不变 |

运行数据在 `.local/`（已 gitignore）；MySQL / Redis 起停详见 `backend/README.md` 与 `backend/scripts/`。

---

### 构建与生产启动（这两个脚本曾经是坏的，已修）

`npm run build` 与 `npm start` 原先依赖 Unix 命令与 bun，在 Windows 上**完全不可用**：

| 旧写法 | 问题 |
|---|---|
| `build: next build && cp -r ...` | Windows 无 `cp` → **退出码 1**，静态资源从未拷入 standalone，产物不可用 |
| `start: NODE_ENV=production bun .next/standalone/server.js` | 依赖未安装的 bun；`NODE_ENV=x cmd` 是 Unix 语法 |

现改为跨平台、不依赖 bun：

```bash
npm run build      # 内部调用 .zscripts/build-portable.cjs（用 Node 拷贝 + 产物自检）
npm start          # 等价于 node .next/standalone/server.js
```

- 构建脚本会**自检** `server.js` 与 `.next/static` 是否就位，缺一即报错退出
- 指定端口：`$env:PORT=3001; npm start`（Windows PowerShell）
- 已实测：构建退出码 0、生产服务器 HTTP 200、静态资源 200

> 仍受 Google Fonts 限制：`npm run build` 需要能访问 `fonts.googleapis.com`（见 §0）。
> 这是唯一剩余的外部依赖；删掉它需自托管字体，但**无法可靠判定已缓存 woff2 的字重映射**
> （猜错会改变字形），故未擅自处理，留待确认。

---

### 验收清单（给用户逐页过）

1. 未登录 → 停在登录页；演示账号可一键填入；**密码 / 验证码 / 注册**三种方式均可进入
   （验证码固定 `123456`）
2. 错误密码 → 提示「账号或密码错误」
3. 用 HR 账号在「求职者」身份下登录 → 提示身份不匹配并可一键切换
4. HR 正确登录 → 工作台，底部四个 Tab（职位管理 / 候选人 / 沟通 / 我的）
5. **职位管理**：发布（空表单应被校验拦截）→ 列表出现 → 关闭（变已关闭）→ 重新开放 → 删除
6. **候选人**：按状态筛选、搜索；详情可标记已查看 / 录用 / 不合适、发起面试邀请
7. **沟通**：6 个候选人会话、未读角标、仅看未读、按职位筛选、搜索；进聊天页发消息、收到自动回复
8. **求职者投递闭环**：详情投递简历 → 「我的投递」可见进度 → 可撤回；收到的面试邀请可接受 / 婉拒
9. **跨端联动**：求职者投递 → HR 候选人里约面试 → 求职者接受 → HR 回看「候选人已接受」
10. **公司资料 / 企业认证**：改名后职位卡片同步；认证提交后约 1.2s 自动通过、状态同步
    「我的」角标；新注册 HR 默认「未认证」
11. 两端「我的」均可退出登录，退出后回到登录页

### 自动化验收记录

**最近一次结果：63/63 通过**，由 `npm run e2e` 产出（脚本 `tests/e2e-verify.cjs`）。

覆盖范围与结果：

| 分组 | 断言数 | 覆盖 |
|---|---|---|
| 求职者端 | 11 | 登录页、演示账号、错误密码、**身份不匹配被拦截**、职位列表(38 条)/详情/投递入口、消息、我的（含我的投递入口）、退出登录 |
| HR 端 | 15 | 四 Tab、职位列表(7 条)、状态筛选、发布（**空表单校验**）/编辑/关闭、沟通板块(6 会话)、会话、聊天页、发消息、自动回复 |
| 候选人管理 | 7 | 列表（6 位候选人）、详情、约面试、**面试邀请发送**、状态更新为面试中 |
| HR 人才筛选 | 6 | 页面打开、关键词命中（**含证据片段**）、未命中候选人不出现、无匹配提示、标签筛选 |
| 跨端投递闭环 | 14 | 求职者投递云杉职位 → HR 候选人可见 → 约面 → 求职者接受 → HR 回看「候选人已接受」；已关闭职位对求职者不可见 |
| 验证码登录 | 1 | `123456` 验证码登录全流程 |
| 注册与企业认证 | 7 | HR 注册（自动建「新注册企业-XXXX」未认证公司）、企业认证（**Mock 自动通过**）、公司资料改名 |
| 质量 | 2 | 零控制台错误、零 hydration 报错 |

脚本结束会自动打印「进程清理检查」，对比运行前后用户 Chrome 进程数（本轮 26 → 26，未受影响）。

复现：`npm run dev`（另一终端）→ `npm run e2e`。

### 自动化验证方法（无需人工点击）

**项目已内置验收脚本 —— 优先直接用它：**

```bash
npm run dev                # 另开一个终端保持运行
npm run e2e                # 跑完整验收，输出逐项 PASS/FAIL
```

- 脚本位置：`tests/e2e-verify.cjs`（纯 Node，无第三方依赖，用 CDP 驱动无头 Chrome 真实点击）
- 可用环境变量覆盖：`BASE_URL`、`CHROME_PATH`、`CDP_PORT`
- 结束时自动打印「进程清理检查」，证明未影响你正在使用的 Chrome
- 改完代码跑一遍，即可确认没有回归

**自己写测试脚本时**，清理无头实例必须使用
`node .zscripts/kill-chrome-safe.cjs` 的 `killChromeTree(pid)`，它只终止你 spawn 的进程树。

> ⚠️ **严禁使用 `Get-Process chrome | Stop-Process -Force`**。
> 这会把用户正在使用的 Chrome **全部标签页一起杀掉**，导致状态丢失与报错。
> （历史上犯过这个错，见 §6.3。）
