# 聘聊 · Step 4 AI / RAG 增量计划（HR 简历问答 → 语义人才检索）

> 状态唯一定稿在 `PROGRESS.md`；本文件是 AI 增量的**方案与任务板**（进度以 §7 任务表为准）。
> 前置：Step 3 后端 + T9 全量联调已完成（`USE_MOCK=false` 下 `npm run e2e` **63/63**）。
> 编号说明：本增量编号为 **Step 4（AI / RAG）**；`PROGRESS.md` §1 中旧的「Step 4 联调」已并入 Step 3 T9。
> 最后更新：2026-10-20 · 状态：**T-AI0 ✅ + T-AI1 ✅ + T-AI2 ✅（单简历问答：检索 / 引用 / 无依据 / 配额 / HR 候选人详情 AI 抽屉全部落地并实测）**，
> 下一步 T-AI3 流式输出（决策见 §1，实测结论见 §7，待确认子项见 §10）

---

## 0. 目标与完成定义（DoD）

- **业务目标**：HR 能在面试准备 / 面试过程中，**基于候选人简历**提问并获得带原文引用的回答；
  进而支持「说人话」的跨候选人语义检索，替代现有纯关键词版人才筛选。
- **技术目标**：在**现有 Spring Boot 单体**中以新增 `ai/` 包的方式接入 RAG，不重构既有分层；
  业务代码与 AI 框架之间加一层自研 SPI，便于后续切换框架（Spring AI → LangChain4j）与升级（Boot 4 / Spring AI 2.0）。
- **DoD（第一个里程碑 T-AI2 验收口径）**：
  1. HR 在「候选人详情」打开 AI 助手，提问（如「他做过高并发吗？几年 Java 经验？」）能得到回答；
  2. 回答**必须带引用**（可点击定位到简历对应段落），无依据时必须明说「简历中没有相关信息」；
  3. 回答只能基于**该 HR 可见范围**内的简历（跨租户零泄漏，见 §5.3）；
  4. `pinliao.ai.enabled=false`（默认）时：接口返回 **42220**（AI 未启用）、前端入口隐藏，**`npm run e2e` 仍 63/63**；
  5. `mvnw test` 全绿且**不依赖网络 / 付费 key**（用 §8 的假模型）。
- **本轮不做**：模型微调、多模态（视频面试分析）、Agent 自主多步工具调用、线上部署与计费、
  PDF/DOCX 简历正文解析（属 T-AI6 之后，见 §10）。

## 1. 决策定案（2026-10-19 用户拍板）

| 事项 | 定案 | 备注 |
|---|---|---|
| 模型接入 | **通义千问一站式（百炼 DashScope）** | 同一个 API Key 同时提供对话与向量；OpenAI 兼容协议 |
| 对话模型 | **`qwen3.5-122b-a10b`**（2026-10-19 实测可用；**思考模式默认关闭**） | 用户指定；若要降本/提速可切 `qwen-plus`（仅改环境变量） |
| 向量模型 | `text-embedding-v4`，**1024 维**（可调 64~2048） | 单条 ≤ 8192 token、批量上限 **10 条/次**、约 ¥0.5/M token |
| 向量库 | **阶段 1：Spring AI `SimpleVectorStore`（内存 + JSON 落盘）→ 阶段 2：MySQL 自建 `resume_chunks` + 暴力余弦** | 本机无 Docker、原生 Redis 无 RediSearch，故先不上独立向量库 |
| 框架 | **Spring AI 1.1.x 为主线；LangChain4j 1.22.x 作为第二阶段对照实现** | 两者都要学，但不同时上（工作量翻倍） |
| 第一个能力 | **单简历问答（HR 候选人详情）** | 检索层可被后续语义检索复用 |
| 密钥与开关 | **全部走用户级环境变量**（`PINLIAO_AI_API_KEY` / `_BASE_URL` / `_CHAT_MODEL` / `_EMBEDDING_MODEL` / `_EMBEDDING_DIM` / `_ENABLED`），仓库只保留 `${占位默认值}` 引用 | 见 §6.2；key 不进仓库、不进日志 |
| 推进方式 | T-AI0 已落地（2026-10-19）；**T-AI1 起按任务板逐块推进**，每块单独验收 | 原「只落文档不改代码」限定针对方案轮，已被 T-AI0 落地取代 |

**版本对齐（2026-10-19 核对）**：

- 项目现用 Spring Boot **3.5.16**（Java 17 target，运行时 JBR 21）→ 对应 **Spring AI 1.1.x**（最新 1.1.8）。
- Spring AI **2.0.x**（2026-06 GA）要求 **Spring Boot 4.0/4.1 + Spring Framework 7 + Jackson 3**，
  **不要**在本增量里顺带升级；Boot 3.5 已于 **2026-06-30 EOL**，Boot 4 迁移**单独开一轮**（见 §10）。
- LangChain4j 的 `langchain4j-spring-boot-starter` 亦支持 Boot 3.5；社区模块提供 DashScope 集成
  （`langchain4j-community-dashscope`），第二阶段再引入。

## 2. 现状约束（读码得到的硬事实，方案据此设计）

| 事实 | 影响 |
|---|---|
| `backend/` 为单体 Spring Boot，按业务分包（`auth/ job/ application/ recruiter/ resume/ ...`），每包 entity/mapper/service/web 四件套 | AI 对应新增 `ai/` 包，**加房不拆墙** |
| `resumes.content` 是**结构化 JSON**：`basics / educations / experiences / projects / skills` | 可做**结构化分块**（一段经历 = 一个 chunk），远优于定长切分，是本项目天然的教学红利 |
| `applications` 带 `company_id`，`resumes` 只有 `user_id`（无租户列） | 向量检索**必须显式做可见性过滤**（§5.3），不能依赖 `PinliaoTenantLineHandler` |
| 已有 `TalentMatchService`（词典 + 权重 + 证据片段） | 可作混合检索的关键词侧与 A/B 对照组 |
| 已有 `usage_logs(company_id, type, day, count)` 与 `tenant_configs.features`（JSON） | 现成的 AI 用量统计位与租户级开关位 |
| 已有 STOMP WebSocket（`message/realtime`，4 主题） | 流式输出可走 STOMP；另有 SSE 备选（§6.3） |
| Flyway 已到 **V8** | AI 相关表从 **V9** 起 |
| 前端 `USE_MOCK` 全局开关 + service 双分支 + e2e「零控制台错误」门禁 | AI 新页面/接口必须沿用双分支 + 独立开关，否则 e2e 直接挂 |
| 本机无 Docker、无 Ollama、i5-1135G7 / 16G 内存 / 无独显；Redis 为原生 `redis-server.exe` | 不引入向量库服务；本地跑对话大模型体验不可接受，**对话走 API** |

## 3. 选型说明

### 3.1 为什么 Spring AI 主线 + LangChain4j 对照

- Spring AI 与现有 Spring Boot 工程同源（配置、依赖管理、Actuator、测试切面一致），落地摩擦最小；
   chat / embedding / vector store / chat memory / tool calling / MCP 能力齐全。
- LangChain4j 的 RAG 组件更「教科书」：`DocumentSplitter`、`EmbeddingStoreIngestor`、
   `ContentRetriever`、`RetrievalAugmentor`、`AiServices` 声明式接口、自带 Reranker 与查询改写，
   适合第二阶段做「同一功能换框架」的对比学习。
- **两者共存的关键**：业务只依赖自研 SPI（§6.1），框架实现藏在 adapter 里。这样框架升级/替换
   不会污染业务代码，也避免两套 API 在 service 层互相打架。

### 3.2 向量库分阶段（不新增基础设施）

| 阶段 | 方案 | 优点 | 代价 |
|---|---|---|---|
| 1 | Spring AI `SimpleVectorStore`（内存 + JSON 落盘 `.local/ai/`） | 0 依赖，当天跑通全链路 | 重启需重算（演示规模可接受）；单机 |
| 2 | MySQL `resume_chunks` 表 + Java 端暴力余弦 | 保持「一个库」的运维故事；几十万 chunk 内够用；**顺带吃透 `VectorStore` 接口** | 需要自己写过滤/排序（也是学习点） |
| 3 | Qdrant / pgvector / Memurai(RediSearch) | 演示升级、上云端 | 需要 Docker/WSL 或更换组件（本机暂无） |

> 注意：现有 Redis 是**原生 `redis-server.exe`，不含 RediSearch 模块**，不能直接当向量库。
> 若以后想用 Redis 路线，需换 Memurai Developer 或 Redis Stack（属于「动已验证依赖」，需单独评估）。

## 4. 能力清单与推荐顺序

| 顺序 | 能力 | 说明 |
|---|---|---|
| **A** | 单候选人简历问答（带引用） | 最小闭环：分块 → 向量化 → 检索 → 生成全串通；引用可溯源是治幻觉的关键设计 |
| **B** | 跨候选人**语义**人才检索 | 与现有关键词版 `TalentMatchService` 并行 A/B；最有演示冲击力 |
| C | 面试问题生成（简历 × 职位要求 → 问题清单 / 追问链） | HR 面试准备刚需，用结构化输出 |
| D | 多候选人对比摘要 / 匹配度打分 | 结构化输出 + 人工可改 |
| E | 求职者侧「简历体检 / 投递建议」 | 双端都用上 RAG |
| F | 工程化：混合检索、rerank、评测集、成本看板 | 持续投入 |

## 5. 数据流与关键设计

### 5.1 入库管道（索引）

```
触发（投递生成 / 简历更新 / HR 上传 PDF）
  → 取 resumes.content(JSON)
  → ResumeChunker：结构化分块 + 拼「上下文头」
  → 批量 embedding（按 content_hash + model 去重）
  → 写 resume_chunks（文本/元数据/向量）
```

**结构化分块规则（T-AI1 已定稿并落地，见 `ai/index/ResumeChunker`）**：

| section | 拆法 | 文本形态（示例） |
|---|---|---|
| `basics` | 1 块 | 「基本信息 · 林小满 · 上海\n3 年前端开发经验，熟悉 React…」（**手机号等联系方式不入库**） |
| `educations` | 每条 1 块（T-AI1 补充） | 「教育经历 · 某某大学 · 计算机科学与技术 · 本科 · 2018-09 - 2022-06」 |
| `experiences` | 每段经历 1 块 | 「工作经历 · 某电商平台 · 高级后端工程师 · 2019-07 - present\n负责订单与营销核心链路…」 |
| `projects` | 每个项目 1 块 | 「项目经历 · 秒杀系统高并发改造 · 后端负责人 · 2023-02 - 2024-05\n基于 Spring Boot + Redis…」 |
| `skills` | 1 块 | 「技能标签\nJava、Spring Boot、MySQL、Redis、微服务、高并发」 |

每块统一加**上下文头**（section + 主体 + 角色 + 时间），这是提升检索命中率最划算的一招。
`chunk_max_chars` 默认 800（超出按句号 / 分号 / 换行切分，续块标题追加「（续 n）」）；
每块记录 `content_hash`（sha256）用于增量与幂等。

### 5.2 查询管道（检索 + 生成）

```
问题 →（可选）查询改写/关键词抽取 → embedding
     → 过滤（companyId 可见范围 + 可选 resumeId）→ topK（默认 6）
     →（可选）rerank → PromptBuilder（编号引用）→ 流式生成 → 前端渲染引用
```

**Prompt 结构（草案）**：系统提示固定四条约束——① 只依据「资料」回答；② 资料没有就说没有，
不得推测；③ 每个结论后用 `[序号]` 标注来源；④ 输出先结论后证据。
「资料」段按 `[1] 工作经历 · 某电商平台 · 高级后端工程师\n…正文…` 编号拼装。

**引用结构**（前后端契约）：

```json
{ "index": 1, "resumeId": "resume-u-cand-2", "section": "experiences",
  "title": "某电商平台 · 高级后端工程师", "snippet": "QPS 从 3000 提升至 12000", "score": 0.82 }
```

### 5.3 多租户与隐私（**红线，必须一开始就做对**）

- `resumes` 是用户维度表（无 `company_id`），**不受** `PinliaoTenantLineHandler` 保护；
  `resume_chunks` 因此也不能当租户表糊弄。
- 检索过滤条件 = 「该 HR 所属公司可见的候选人集合」：由 §4.4 投递（`applications.company_id`）
  与人才库范围推导；**每一次向量查询都必须带上该条件**，不允许「先全局检索再过滤」。
- **定稿（T-AI1）**：索引**不冗余** `company_id` / `application_id`，一份简历只建一套块。
  理由：同一份简历可投多家公司，按公司复制会让存储与嵌入费用成倍放大；而 `applications.company_id`
  是现成且权威的可见性来源。T-AI2 检索时先 `SELECT DISTINCT resume_id FROM applications
  WHERE company_id = ? AND deleted_at IS NULL`，再 `resume_chunks.resume_id IN (...)` ——
  这是 SQL 层**前置过滤**（不是向量算完再丢弃），既安全又不重复计费。
- `resume_chunks` 的查询按 `model` 隔离：换 provider（`fake` ↔ 真实）或换嵌入模型后旧向量不复用，
  必须重建（`ResumeChunkStore` 的复用查询都带 `model` 条件）。
- 日志与埋点**不得**记录简历原文与完整 Prompt；只记录长度、token、chunkId、耗时。

### 5.4 开关、降级与成本

- 总开关 `pinliao.ai.enabled`（默认 **false**）；租户级可用 `tenant_configs.features.ai` 二次控制。
- 每小时/每日调用上限（Redis 计数，复用现有 limiter 风格），超限返回 **42902**。
- 每次调用写 `usage_logs`：`AI_CHAT`（次数）、`AI_EMBEDDING`（token 估算）；
  便于以后做「AI 成本看板」，也是演示时能讲的一环。
- 上游超时/失败 → **50020**，**不得**让 AI 故障影响既有功能（问答接口独立，不参与核心链路）。

## 6. 代码结构与契约

### 6.1 后端新增包（不动既有包）

`✅` = 已落地（T-AI0 / T-AI1）；`⬜` = 后续增量规划。

```
backend/src/main/java/com/pinliao/backend/ai/
├─ AiCodes.java        ✅ 模块错误码（42220 未启用 / 42221 索引未就绪 / 42902 超配额 / 50020 上游失败）
├─ config/             ✅ AiProperties（record，绑定 pinliao.ai.*：enabled / provider / chunkMaxChars / embeddingBatchSize）
│                         + AiConfig（@EnableConfigurationProperties）
├─ spi/                ✅ AiEmbeddingModel（业务唯一向量依赖）；⬜ AiChatClient / AiVectorStore
├─ provider/
│   ├─ SpringAiEmbeddingAdapter ✅ 真实上游（按 ≤10 条切批、异常转 50020、维度校验）
│   ├─ FakeEmbeddingModel       ✅ 确定性词袋哈希向量（零网络 / 零费用，单测与冒烟）
│   └─ langchain4j/    ⬜ LangChain4j adapter（阶段 2，对照）
├─ index/              ✅ ResumeChunker / ResumeChunk / ResumeChunkEntity / ResumeChunkMapper
│                         / ResumeChunkStore（按简历整体替换 + 按 model 复用向量）/ ResumeIndexService
├─ dto/                ✅ AiIndexRequests（rebuild 请求体）
├─ view/               ✅ AiViews（自检）/ AiIndexViews（重建结果 / 索引状态）
├─ rag/                ⬜ RetrievalService（过滤 + topK + rerank）、PromptBuilder、CitationAssembler
├─ qa/                 ⬜ HrResumeQaService（会话记忆 + 流式输出）
├─ search/             ⬜ SemanticTalentSearchService（阶段 T-AI4，与 TalentMatchService 并行）
├─ usage/              ⬜ AiUsageRecorder → usage_logs
├─ service/            ✅ AiConnectivityService（自检：chat + embedding）
└─ web/                ✅ AiController（/api/v1/ai/status、/ping）
                          + AiIndexController（/api/v1/ai/index/rebuild、/hr/applications/{id}/ai/index-status）
```

### 6.2 配置（**已落地**：key 只进环境变量，不进仓库）

**依赖（`backend/pom.xml`，已加）**：`spring-ai-bom:1.1.8`（import） + `spring-ai-starter-model-openai`。
1.1.x 对应 Boot 3.5.x；Spring AI 2.0.x 需 Boot 4 + Jackson 3，**勿混用**。

**环境变量（用户级，持久化；`环境变量` 对话框 → 用户变量）**：

| 变量 | 值（演示） | 说明 |
|---|---|---|
| `PINLIAO_AI_API_KEY` | `sk-…`（**只在环境变量里**） | 百炼 API Key；仓库只见 `${PINLIAO_AI_API_KEY:占位}` |
| `PINLIAO_AI_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode` | Spring AI 默认再拼 `/v1/chat/completions`、`/v1/embeddings` |
| `PINLIAO_AI_CHAT_MODEL` | `qwen3.5-122b-a10b` | 思考型模型，代码里显式 `enable_thinking=false` |
| `PINLIAO_AI_EMBEDDING_MODEL` | `text-embedding-v4` | 1024 维 |
| `PINLIAO_AI_EMBEDDING_DIM` | `1024` | 与 `text-embedding-v4` 默认一致 |
| `PINLIAO_AI_ENABLED` | `true` | 总开关；**默认 false**（无 key 的机器 / CI 不受影响） |
| `PINLIAO_AI_PROVIDER` | `springai` | T-AI1 新增：`fake` = 本地确定性假向量（单测 / 冒烟，零费用） |
| `PINLIAO_AI_CHUNK_MAX_CHARS` | `800` | T-AI1 新增：单块正文上限，超出按句切分并续写 |
| `PINLIAO_AI_EMBEDDING_BATCH_SIZE` | `10` | T-AI1 新增：单次嵌入请求条数上限（百炼 `text-embedding-v4` ≤10） |

`application.yml` 实际写法（provider 参数交给 Spring AI 原生前缀，不再自造一套）：

```yaml
spring:
  ai:
    openai:
      api-key: ${PINLIAO_AI_API_KEY:pinliao-ai-disabled-placeholder}   # 占位：无 key 也能启动，功能由总开关关掉
      base-url: ${PINLIAO_AI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode}
      chat:
        options:
          model: ${PINLIAO_AI_CHAT_MODEL:qwen3.5-122b-a10b}
          temperature: 0.3
      embedding:
        options:
          model: ${PINLIAO_AI_EMBEDDING_MODEL:text-embedding-v4}
          dimensions: ${PINLIAO_AI_EMBEDDING_DIM:1024}
pinliao:
  ai:
    enabled: ${PINLIAO_AI_ENABLED:false}
```

设置与自检（PowerShell，见 `backend/README.md`「AI 配置」）：

```powershell
[Environment]::SetEnvironmentVariable('PINLIAO_AI_API_KEY','sk-…','User')   # 其余变量同理
# 重启终端 / IDE 后（环境变量在进程启动时读取）：
$tk = (Invoke-RestMethod http://localhost:8080/api/v1/auth/login/password -Method Post `
  -ContentType 'application/json' -Body '{"account":"13800000002","password":"123456"}').data.accessToken
Invoke-RestMethod http://localhost:8080/api/v1/ai/status -Headers @{ Authorization = "Bearer $tk" }
Invoke-RestMethod http://localhost:8080/api/v1/ai/ping   -Headers @{ Authorization = "Bearer $tk" }
```

**实测结论（2026-10-19，curl/Invoke-RestMethod 实跑）**：

- `sk-ws-` 前缀的 key **可以**用于普通应用：标准「按量计费」兼容模式端点
  （`dashscope.aliyuncs.com/compatible-mode`）chat 与 embeddings **均 200**；
  `coding.dashscope.aliyuncs.com`（Coding Plan 专属，只服务编码类工具）**401** —— 故走兼容模式端点。
- `qwen3.5-122b-a10b` 是**思考型**模型：请求体加 `enable_thinking=false` 后 **~1.0s** 返回、无
  `reasoning_content`；不加则延迟 >10s 且多计 token（代码里已用 `extraBody` 固定关闭）。
- embedding `text-embedding-v4` 返回 **1024 维**，~0.5s。
- `/api/v1/ai/ping` 实测：chat 1005ms / 44 tokens、embedding 538ms / dim 1024、端到端 1.56s。

> 安全：key 只存于**用户级环境变量**；`git grep 'sk-ws-'`、未跟踪文件、全部历史提交均为空命中
> （接入时已核查）。`.env*` 与 `.local/` 已 gitignore。

### 6.3 接口草案（契约新增 §AI，账目并入 `docs/api-endpoints.md`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/ai/status` | **已落地**（契约外自检）：回显 enabled / provider（`springai` / `fake`）/ 模型名，不调用上游 |
| GET | `/api/v1/ai/ping` | **已落地**（契约外自检）：真实调用一次 chat + embedding，返回耗时 / token / 向量维度 |
| POST | `/api/v1/hr/applications/{applicationId}/ai/qa` | 简历问答（v1 非流式 JSON；v2 SSE `text/event-stream`：`delta` / `citation` / `done` / `error`） |
| GET | `/api/v1/hr/applications/{applicationId}/ai/index-status` | **已落地（T-AI1）**：该候选人简历索引状态（`indexed` / `chunkCount` / `model` / `dim` / `updatedAt`）；按投递记录校验可见性，跨公司 404 |
| POST | `/api/v1/ai/index/rebuild` | **已落地（T-AI1）**：全量或单份重建（body `{resumeId?}`）；需 HR / 管理员；返回 `resumes/skipped/chunks/embedded/reused/elapsedMs` |
| POST | `/api/v1/hr/talent/semantic-search` | 阶段 T-AI4：语义人才检索 |

- 两个自检端点同样受 `WebAuthConfig` 拦截器保护（`/api/v1/**` 默认需登录），避免未授权调用产生费用
  （单测 `AuthInterceptorPublicEndpointTest` 已断言非公开）。
- 流式方案：**优先 SSE**；若 Next `rewrites` 代理对 SSE 有阻断（T-AI3 实测），退回既有 STOMP
  主题（`/topic/hr/ai/{sessionId}`），复用现成的握手与鉴权拦截器。
- 错误码：**`42220` AI 未启用 / `42221` 索引未就绪 / `42902` 超出调用配额 / `50020` 上游调用失败或超时**
  （定义在 `ai/AiCodes.java`）。
  注意：原方案的 `43xxx` 段**不可用**——契约 §5 规定「5 位数字，前 3 位对应 HTTP 状态码」，
  而 `ApiCodes.httpStatus()` 只识别 400/401/403/404/409/422/429/500 前缀，`430xx` 会落到 500。故改用
  上表四个（`42220/42221` 属 422 业务规则族、`42902` 属 429 限流族、`50020` 属 500 上游故障族）。

### 6.4 数据表（**V9 已落地**，见 `db/migration/V9__ai_resume_chunks.sql`）

```sql
-- V9 实际结构（阶段 2 的 MySQL 向量方案：embedding 直接存 JSON 数组）
resume_chunks(
  id, resume_id, user_id,
  section, ordinal, title, content, content_hash,
  model, dim, embedding,                       -- embedding = JSON float 数组（1024 维约 12.7KB/行）
  created_at, updated_at,
  UNIQUE (resume_id, section, ordinal, model),
  KEY (content_hash, model), KEY (user_id),
  FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE CASCADE
)
-- 与草案的差异（T-AI1 定稿）：
--   1) 去掉 company_id / application_id（可见性在检索时用 applications 前置过滤，见 §5.3）；
--   2) 唯一键改为 (resume_id, section, ordinal, model)：同一段文字出现在两份简历里要能各建一块，
--      否则引用会指向别人的简历；跨简历复用向量靠 (content_hash, model) 查询，不靠唯一键；
--   3) 阶段 1「只存文本」与阶段 2「加 embedding」合并为一次建表（演示规模无需两段迁移）。

-- 尚未落地（T-AI2 / T-AI3）：
ai_chat_sessions(id, company_id, hr_id, application_id, title, created_at, updated_at)
ai_chat_messages(id, session_id, role, content, citations_json, tokens, latency_ms, created_at)
```

## 7. 任务板（进度以本表为准）

图例：⬜ 未开始 · 🔄 进行中 · ✅ 完成

| 编号 | 内容 | 产出与验收 | 预估 | 需要真实 key | 状态 |
|---|---|---|---|---|---|
| T-AI0 | 依赖与连通性：BOM + starter + `AiProperties` + `/ai/ping` | ✅ **完成（2026-10-19）**：`spring-ai-bom:1.1.8` + `spring-ai-starter-model-openai`；`AiCodes` / `AiProperties` / `AiConnectivityService` / `AiController`；`/api/v1/ai/status` + `/ping` 实测通过（chat 1005ms / 44 tokens、embedding 538ms / dim 1024）；`mvnw test` **176/176**；key 走用户级环境变量且已核查仓库零泄漏；`backend/README.md` 已加「AI 配置」 | 0.5 天 | 是 | ✅ |
| T-AI1 | 索引入库：`ResumeChunker` + `AiEmbeddingModel` + `resume_chunks`(V9) + 重建接口 | 单测（FakeEmbedding）断言分块规则；`smoke-ai.mjs`：12 份简历 → N 个 chunk；重复执行幂等 | 1~2 天 | 否 | ✅ **完成（2026-10-20）**：`ResumeChunker`（basics/educations/experiences/projects/skills 五段式 + 上下文头 + 800 字按句续写，手机号不入库）、`AiEmbeddingModel` SPI + `SpringAiEmbeddingAdapter`（≤10 条切批、失败转 50020）/ `FakeEmbeddingModel`（确定性词袋向量）、V9 `resume_chunks`、`ResumeIndexService`（全量 / 单份 / 状态，按 model 复用向量）、`AiIndexController`；单测新增 17 项，`mvnw test` **199/199**；`smoke-ai.mjs` **17/17**（fake：7 份简历 35 块、二次 `embedded=0`；真实：单份 5 块 950ms、`text-embedding-v4` 1024 维、二次 0 调用）；`verify-seed.ps1` 16/16（flyway=9） |
| T-AI2 | **单简历问答 MVP**（非流式）+ 引用 + HR 候选人详情「AI 助手」抽屉 | §0 的 5 条 DoD 全过；前端走 mock 分支；e2e 仅新增「入口存在 / 开关关闭时隐藏」断言 | 1~2 天 | 是（人工验收时） | ✅ **完成（2026-10-20）**：`AiChatClient` SPI + `SpringAiChatAdapter`（`enable_thinking=false`，失败转 50020）/ `FakeChatClient`；`RetrievalService` 余弦 topK（`resumeId` 先经 `applications.company_id` 过滤）、`PromptBuilder`（资料 / 指令分区 + 无依据话术）、`CitationAssembler`（`[n]` 解析 + 漏标兜底）、`HrResumeQaService`（开关 / 角色 / 归属 / 按需建索引 / 低分不调模型）、`AiQuotaService`（HR/日 100 次，42902；Redis 故障放行）；`POST /hr/applications/{id}/ai/qa`；前端 `src/api/ai.ts` + `mock/ai.ts` 双分支、候选人详情「AI 简历助手」抽屉（入口按 `/ai/status.enabled` 显隐）；`mvnw test` **230/230**、`smoke-ai.mjs` **31/31**、e2e **65/65**（真实后端 fake 向量 + Mock 分支各一轮）；AI 关闭下接口 42220、入口隐藏 |
| T-AI3 | 流式输出（SSE 或 STOMP）+ 打字机 + 引用点击定位 | 首字延迟、中断、错误态；e2e 不依赖流式内容 | 1 天 | 是 | ⬜ |
| T-AI4 | 语义人才检索（与关键词版并行 A/B） | 同一组查询两边对比记录（相关性、命中率）；人才筛选页加「语义模式」 | 2 天 | 是 | ⬜ |
| T-AI5 | 面试问题生成（结构化输出 + 人工可改） | HR 面试准备页；输出 schema 校验 | 1~2 天 | 是 | ⬜ |
| T-AI6 | 工程化：混合检索（复用 `TalentMatchService` 权重）/ rerank / 评测集 / 成本看板 | 评测报告（10~20 题小集）；`usage_logs` 看板 | 持续 | 是 | ⬜ |
| T-AI7 | （可选）LangChain4j 对照实现 | 同一问答换 adapter 跑通，写对比笔记 | 1~2 天 | 是 | ⬜ |

**T-AI1 实测记录（2026-10-20）**：

- 假向量（`PINLIAO_AI_PROVIDER=fake`）：7 份种子简历 → **35 块**（每份 5 块：基本信息 / 教育 / 工作 / 项目 / 技能），
  首次 `embedded=35`，重复重建 `embedded=0 / reused=35`，`smoke-ai.mjs` **17/17**。
- 真实上游（`text-embedding-v4`）：单份 `resume-u-cand-1` → 5 块 / `embedded=5` / **950ms**；
  全量 7 份 → 35 块 / `embedded=30`（复用 5）/ **2.1s**；再次重建 `embedded=0` / 185ms。
- 落库抽查：`model=text-embedding-v4`、`dim=1024`、单行 `embedding` 约 12.7KB；`13800001101` 在
  `resume_chunks.content` 中 **0 命中**（联系方式不入库）。
- 回归：`mvnw test` **199/199**；`db-reset.ps1` → `flyway=9 / companies=16 / jobs=51 / applications=9`；
  `verify-seed.ps1` **16/16**。

**T-AI2 实测记录（2026-10-20）**：

- `mvnw test` **230/230**（新增 31：`SpringAiChatAdapterTest` 3 / `PromptBuilderTest` 1 /
  `CitationAssemblerTest` 5 / `RetrievalServiceTest` 5 / `AiQuotaServiceTest` 3 / `HrResumeQaServiceTest` 14），
  全部使用假对话 / 假向量，**不联网、不花钱**。
- `smoke-ai.mjs` **31/31**（fake provider）：权限（未登录 40101 / 求职者 40301）、入参（空 / >500 字 42201）、
  跨公司与不存在投递 40401、带引用回答（`grounded=true`、`citations≥1`、`chatModel=fake-chat-v1`）、
  简历外问题固定「简历中没有相关信息。」且 `citations=[]`。
- 前端：候选人详情「AI 简历助手」入口仅在 `/ai/status.enabled=true` 时渲染；抽屉内置 3 条快捷问题、
  `[n]` 引用卡片（段落 / 相似度 / 原文片段）与「AI 生成仅供参考」提示；Mock 分支用段落意图 + 原文命中，
  未命中同样回答「简历中没有相关信息。」。
- e2e **65/65**：真实后端（`provider=fake`，避免真实对话费用）与 Mock 分支各跑一轮；新增
  「AI 入口与开关一致」「AI 问答返回带引用的回答」2 项。
- AI 关闭（`PINLIAO_AI_ENABLED=false`）：`POST .../ai/qa` → **422 + 42220**，前端入口隐藏，
  e2e 其余 64 项不受影响（实测见 `docs/AI-CONTINUE.md` §6.21）。

## 8. 测试策略（保证不破坏现有基线）

- **假模型优先**：`FakeEmbeddingModel`（确定性词袋哈希 → 归一化向量，保证同文本同向量）+
  `FakeChatClient`（模板回复，固定引用 `[1]`）。单测/集成测试**不联网、不花钱、可重复**，与现有 199 项测试风格一致。
  （T-AI0 已先行示范：`AiConnectivityServiceTest` 用 Mockito 假模型覆盖「关闭 → 42220 不调上游」与
  「开启 → 关闭思考模式 + token 上限」两条不变量；T-AI1 的 `ResumeIndexServiceTest` 用假向量覆盖
  「内容未变 → 0 次上游调用」「换模型 → 不复用旧向量」两条费用 / 正确性不变量。）
- **`smoke-ai.mjs`** 走 `provider=fake`（或本地 Ollama 向量），断言分块数、幂等、过滤边界
  （他人公司的简历检索不到）；`node scripts/smoke-ai.mjs` 纳入 `backend/README.md` 冒烟清单。
  **安全闸**：脚本检测到 `provider=springai` 会直接退出（避免误花额度），确认要测真实链路加 `--allow-real`。
- **e2e 只加不变量断言**：入口显隐、开关关闭时不可见、回答带 `[n]` 引用；**不**断言模型具体输出。
- **回归门禁**：每轮改动后仍须 `npm run e2e` **65/65**（真实后端 / Mock 各一轮）、`mvnw test` 全绿、
  `verify-seed.ps1` 16/16；AI 表新增不影响既有种子计数（V9 只建表不插演示数据）。
- **契约与文档义务**：`docs/api-endpoints.md` 新增 §AI 并更新账目数；
  `PROGRESS.md` / `CHANGELOG.md` / `worklog.md` / `AI-CONTINUE.md §6`（新增 AI 踩坑小节）同步。

## 9. 学习路线（本项目最大的复用价值）

把 RAG 六个环节**各替换一次实现**并量化差异（同一份 10~20 题评测集）：

| 环节 | 对比实验 | 观察指标 |
|---|---|---|
| 分块 | 结构化分块 vs 定长 500/50 | 检索命中率 |
| 向量模型 | `text-embedding-v4`(1024) vs 1536 维 vs 本地 bge-m3 | 命中率 / 成本 / 速度 |
| 存储 | `SimpleVectorStore` vs MySQL 暴力余弦 vs Qdrant | 延迟 / 运维复杂度 |
| 检索 | 纯向量 vs 关键词（现成 `TalentMatchService`）vs 混合 | 相关性、长尾查询 |
| 重排 | 无 rerank vs bge-reranker / LLM 重排 | 前 3 条相关性 |
| 生成 | Prompt 结构、引用约束、温度 | 幻觉率、可读性 |

**本项目独有的优势**：关键词侧不是玩具（`TalentMatchService` 已有词典 / 权重 / 证据片段实现），
混合检索在这里是真实可落地的改造，而不是示例代码。

## 10. 风险与未决事项

| 风险 | 应对 |
|---|---|
| DashScope 兼容协议的 path / 维度 / 批量（10 条）与 OpenAI 默认不同 | ✅ **已实测**（T-AI0/T-AI1）：`base-url` 用 `.../compatible-mode`，Spring AI 默认拼 `/v1/chat/completions`、`/v1/embeddings`，与 DashScope 路径一致；维度显式配 1024；**批量按 10 条切分已封装在 `SpringAiEmbeddingAdapter`**（真实单份 5 块 950ms、全量 30 块 2.1s） |
| DashScope「思考型」模型默认开启思考 → 延迟与费用不可控 | ✅ **已处理**（T-AI0）：请求体 `extraBody(enable_thinking=false)`（实测 10s+ → 1.0s）；单测断言该参数 |
| key 泄露（用户最担心「额度被用光」） | ✅ **已核查**（T-AI0）：key 仅存用户级环境变量；`git grep` / 未跟踪文件 / 全部历史提交均零命中；`.env*`、`.local/` 已 gitignore |
| 跨租户简历泄漏（最严重） | §5.3 过滤前置；`smoke-ai.mjs` 增加「A 公司查不到 B 公司简历」断言 |
| 简历文本被当作指令（Prompt 注入） | 「资料」与「指令」分区，并在系统提示里声明资料为纯数据 |
| e2e「零控制台错误」门禁 | AI 相关请求失败必须走统一包络处理，不在前端产生未捕获错误 |
| 成本不可控 | §5.4 配额 + `usage_logs`；演示额度上限写进配置 |
| 模型换代导致向量失效 | ✅ **已处理**（T-AI1）：`resume_chunks` 以 `model` 维度隔离，复用查询与状态统计都带 `model`；换代后重建即覆盖 |
| Spring Boot 3.5 已 EOL；Spring AI 2.0 需 Boot 4 + Jackson 3 | **单独开一轮** Boot 4 迁移，不与 AI 增量混做 |

**待确认（需用户后续拍板，不替用户决定）**：

1. ~~对话模型最终档位~~ → **用户已指定 `qwen3.5-122b-a10b`**（T-AI0 实测可用）；若后续在意成本可切
   `qwen-plus`（只改 `PINLIAO_AI_CHAT_MODEL`）。
2. 向量维度定 1024 还是 1536（精度 / 存储权衡）——**暂按 1024**（与 DashScope 默认一致）。
3. 是否同时接 Ollama 做「零成本模式」作为演示兜底（需装 Ollama，向量可，对话不建议）。
4. 配额策略（每 HR 每日问答次数上限、embedding 重建是否限流）。
5. 是否把求职者侧 AI 能力（简历体检 / 投递建议）纳入 Step 4 范围。
6. Boot 4 + Spring AI 2.0 迁移的时机（建议排在 Step 4 主线之后）。

## 11. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-10-19 | 首版。基于当前仓库实况（Boot 3.5.16 / MySQL 3307 / 无 Docker / 原生 Redis / 结构化简历 JSON / 多租户红线 / e2e 门禁）制定 Step 4 AI / RAG 增量方案；用户拍板：通义千问一站式、先内存后 MySQL、Spring AI 主线 + 事后补 LangChain4j、问答先行、**先落文档不动代码** |
| v1.1 | 2026-10-19 | **T-AI0 落地并实测**：新增 `spring-ai-bom:1.1.8` + `spring-ai-starter-model-openai`；`ai/` 包（`AiCodes` 42220/42221/42902/50020、`AiProperties`、`AiConnectivityService`、`AiController` 的 `/ai/status` 与 `/ai/ping`）；配置改为「Spring AI 原生前缀 `spring.ai.openai.*` + 总开关 `pinliao.ai.enabled`」，key 走 6 个用户级环境变量并以占位默认值保证无 key 也能启动。**修正原方案两处**：① 错误码 `43xxx` 不可用（契约规定前 3 位=HTTP 状态），改为 `42220/42221/42902/50020`；② 对话模型按用户指定定为 `qwen3.5-122b-a10b`（思考型，`enable_thinking=false`）。实测：chat 1005ms/44 tokens、embedding 1024 维/538ms；`mvnw test` 176/176；e2e 63/63 无回归 |
| v1.2 | 2026-10-20 | **T-AI1 索引入库落地并实测**：`ResumeChunker`（五段式 + 上下文头 + 800 字续写 + 手机号不入库）、`AiEmbeddingModel` SPI、`SpringAiEmbeddingAdapter`（≤10 切批）/ `FakeEmbeddingModel`、V9 `resume_chunks`、`ResumeIndexService`（全量 / 单份 / 状态）、`AiIndexController`（rebuild + index-status）、`smoke-ai.mjs`（17 项，带真实上游安全闸）。**定稿三处**：① 索引不冗余 `company_id`，检索用 `applications` 前置过滤（§5.3）；② 唯一键改 `(resume_id, section, ordinal, model)`，跨简历复用靠 `(content_hash, model)` 查询（§6.4）；③ 向量复用与状态统计都以 `model` 隔离，换 provider / 换模型不复用旧向量。实测：假向量 35 块 / 二次 0 调用；真实 `text-embedding-v4` 单份 950ms、全量 2.1s、1024 维；`mvnw test` 199/199、`verify-seed` 16/16（flyway=9） |
| v1.3 | 2026-10-20 | **T-AI2 单简历问答落地并实测**：自研 `AiChatClient` SPI（`SpringAiChatAdapter` 关闭思考 / `FakeChatClient`）+ `RetrievalService`（可见性前置过滤 + 余弦 topK）+ `PromptBuilder`（资料/指令分区）+ `CitationAssembler`（`[n]` 解析与漏标兜底）+ `HrResumeQaService`（低分不调模型、按需建索引）+ `AiQuotaService`（HR/日配额，Redis 故障放行）；`POST /api/v1/hr/applications/{id}/ai/qa`；前端候选人详情「AI 简历助手」抽屉 + `src/api/ai.ts`/`mock/ai.ts` 双分支，入口按 `/ai/status.enabled` 显隐。实测：`mvnw test` **230/230**、`smoke-ai.mjs` **31/31**、e2e **65/65**（真实 fake provider + Mock 各一轮）；AI 关闭 `42220` 且入口隐藏。**新增红线**：无依据不编造引用（固定「简历中没有相关信息。」且 `citations=[]`），审计不落简历原文 |
