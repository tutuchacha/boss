# 聘聊 · Step 4 AI / RAG 增量计划（HR 简历问答 → 语义人才检索）

> 状态唯一定稿在 `PROGRESS.md`；本文件是 AI 增量的**方案与任务板**（进度以 §7 任务表为准）。
> 前置：Step 3 后端 + T9 全量联调已完成（`USE_MOCK=false` 下 `npm run e2e` **63/63**）。
> 编号说明：本增量编号为 **Step 4（AI / RAG）**；`PROGRESS.md` §1 中旧的「Step 4 联调」已并入 Step 3 T9。
> 最后更新：2026-10-19 · 状态：**方案定稿，未开工**（决策见 §1，待确认子项见 §10）

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
  4. `pinliao.ai.enabled=false`（默认）时：接口返回 43001、前端入口隐藏，**`npm run e2e` 仍 63/63**；
  5. `mvnw test` 全绿且**不依赖网络 / 付费 key**（用 §8 的假模型）。
- **本轮不做**：模型微调、多模态（视频面试分析）、Agent 自主多步工具调用、线上部署与计费、
  PDF/DOCX 简历正文解析（属 T-AI6 之后，见 §10）。

## 1. 决策定案（2026-10-19 用户拍板）

| 事项 | 定案 | 备注 |
|---|---|---|
| 模型接入 | **通义千问一站式（百炼 DashScope）** | 同一个 API Key 同时提供对话与向量；OpenAI 兼容协议 |
| 对话模型 | `qwen3` 系列（演示默认 `qwen-plus` 档，可按效果升级） | 具体型号在 T-AI0 定稿并写入文档 |
| 向量模型 | `text-embedding-v4`，**1024 维**（可调 64~2048） | 单条 ≤ 8192 token、批量上限 **10 条/次**、约 ¥0.5/M token |
| 向量库 | **阶段 1：Spring AI `SimpleVectorStore`（内存 + JSON 落盘）→ 阶段 2：MySQL 自建 `resume_chunks` + 暴力余弦** | 本机无 Docker、原生 Redis 无 RediSearch，故先不上独立向量库 |
| 框架 | **Spring AI 1.1.x 为主线；LangChain4j 1.22.x 作为第二阶段对照实现** | 两者都要学，但不同时上（工作量翻倍） |
| 第一个能力 | **单简历问答（HR 候选人详情）** | 检索层可被后续语义检索复用 |
| 本轮范围 | **只落方案文档，不改代码** | 开工时机由用户另行指示 |

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

**结构化分块规则（草案，T-AI1 定稿）**：

| section | 拆法 | 文本形态（示例） |
|---|---|---|
| `basics` | 1 块 | 「基本信息 · 林小满 · 上海 · 3 年前端开发经验，熟悉 React…」 |
| `experiences` | 每段经历 1 块 | 「工作经历 · 某电商平台 · 高级后端工程师 2019.07-至今：负责订单与营销核心链路…」 |
| `projects` | 每个项目 1 块 | 「项目经历 · 秒杀系统高并发改造 · 后端负责人 2023.02-2024.05：基于 Spring Boot + Redis…」 |
| `skills` | 1 块（或按类别分） | 「技能标签：Java、Spring Boot、MySQL、Redis、微服务、高并发」 |

每块统一加**上下文头**（section + 主体 + 角色 + 时间），这是提升检索命中率最划算的一招。
`chunk_max_chars` 默认 800（超出按句号二次切分）；每块记录 `content_hash` 用于增量与幂等。

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
- 索引写入时冗余 `company_id` / `application_id` 便于过滤；跨公司共享同一份简历时按公司分别建块
  （向量相同、元数据不同）或建块一次 + 过滤时 join，二者在 T-AI1 定稿。
- 日志与埋点**不得**记录简历原文与完整 Prompt；只记录长度、token、chunkId、耗时。

### 5.4 开关、降级与成本

- 总开关 `pinliao.ai.enabled`（默认 **false**）；租户级可用 `tenant_configs.features.ai` 二次控制。
- 每小时/每日调用上限（Redis 计数，复用现有 limiter 风格），超限返回 43004。
- 每次调用写 `usage_logs`：`AI_CHAT`（次数）、`AI_EMBEDDING`（token 估算）；
  便于以后做「AI 成本看板」，也是演示时能讲的一环。
- 上游超时/失败 → 43002，**不得**让 AI 故障影响既有功能（问答接口独立，不参与核心链路）。

## 6. 代码结构与契约

### 6.1 后端新增包（不动既有包）

```
backend/src/main/java/com/pinliao/backend/ai/
├─ config/      AiProperties（enabled / provider / chat / embedding / retrieve / store）
│               @ConditionalOnProperty(pinliao.ai.enabled)
├─ spi/         AiChatClient / AiEmbeddingModel / AiVectorStore  ← 业务唯一依赖（框架隔离层）
├─ provider/
│   ├─ springai/        Spring AI adapter（阶段 1）
│   └─ langchain4j/     LangChain4j adapter（阶段 2，对照）
│   └─ fake/            FakeChatModel / FakeEmbeddingModel（测试与离线演示）
├─ chunk/       ResumeChunker、ChunkHash、ResumeTextExtractor
├─ index/       ResumeIndexService（增量索引 / 全量重建 / 状态查询）
├─ rag/         RetrievalService（过滤 + topK + rerank）、PromptBuilder、CitationAssembler
├─ qa/          HrResumeQaService（会话记忆 + 流式输出）
├─ search/      SemanticTalentSearchService（阶段 T-AI4，与 TalentMatchService 并行）
├─ usage/       AiUsageRecorder → usage_logs
└─ web/         AiController / HrAiController
```

### 6.2 配置（草案，key 不进仓库）

```yaml
pinliao:
  ai:
    enabled: false                 # 默认关闭；演示时用环境变量打开
    provider: springai             # springai | langchain4j | fake
    chat:
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${PINLIAO_AI_API_KEY:}
      model: qwen-plus
      timeout: 60s
    embedding:
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${PINLIAO_AI_API_KEY:}
      model: text-embedding-v4
      dimensions: 1024
      batch-size: 10               # DashScope 单次上限
      cache: true                  # content_hash → 向量本地缓存
    store:
      type: memory                 # memory | mysql
      file: .local/ai/vector-store.json
    retrieve:
      top-k: 6
      min-score: 0.35
```

- `PINLIAO_AI_API_KEY` 通过**系统环境变量**注入（`.env*` 与 `.local/` 均已 gitignore；
  后端不读 `.env`，起服务前在 PowerShell 里设一次即可，写入 `backend/README.md` 的联调步骤）。
- 客户端依赖坐标（T-AI0 落地时确认）：
  `org.springframework.ai:spring-ai-bom:1.1.x`（import）+ `spring-ai-starter-model-openai`
  （OpenAI 兼容 → DashScope）。**踩坑预警**：DashScope 的 `base-url` 与 `chat/embeddings` 路径
  与 OpenAI 默认拼接规则不同，T-AI0 先用 curl 验证两个 path 再写配置。

### 6.3 接口草案（契约新增 §AI，账目并入 `docs/api-endpoints.md`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/hr/applications/{applicationId}/ai/qa` | 简历问答（v1 非流式 JSON；v2 SSE `text/event-stream`：`delta` / `citation` / `done` / `error`） |
| GET | `/api/v1/hr/applications/{applicationId}/ai/index-status` | 该候选人简历索引状态（是否已索引、chunk 数、模型版本） |
| POST | `/api/v1/ai/index/rebuild` | 全量重建索引（演示/运维用，需鉴权） |
| POST | `/api/v1/hr/talent/semantic-search` | 阶段 T-AI4：语义人才检索 |

- 流式方案：**优先 SSE**；若 Next `rewrites` 代理对 SSE 有阻断（T-AI3 实测），退回既有 STOMP
  主题（`/topic/hr/ai/{sessionId}`），复用现成的握手与鉴权拦截器。
- 错误码（43xxx 段，属新增）：`43001` AI 未启用 / `43002` 上游调用失败或超时 /
  `43003` 索引未就绪 / `43004` 超出调用配额。

### 6.4 数据表（V9 起）

```sql
-- 阶段 1 只建表存文本与元数据（向量在内存 JSON）；阶段 2 再加 embedding 列
resume_chunks(
  id, resume_id, user_id, company_id, application_id,   -- company_id/application_id 用于可见性过滤
  section, ordinal, title, content, content_hash,
  model, dim, embedding,                                 -- 阶段 2 才有值
  created_at, updated_at,
  KEY (resume_id, section), KEY (company_id, resume_id), UNIQUE (content_hash, model)
)
ai_chat_sessions(id, company_id, hr_id, application_id, title, created_at, updated_at)
ai_chat_messages(id, session_id, role, content, citations_json, tokens, latency_ms, created_at)
```

## 7. 任务板（进度以本表为准）

图例：⬜ 未开始 · 🔄 进行中 · ✅ 完成

| 编号 | 内容 | 产出与验收 | 预估 | 需要真实 key | 状态 |
|---|---|---|---|---|---|
| T-AI0 | 依赖与连通性：BOM + starter + `AiProperties` + `/ai/ping` | curl 打通 chat 与 embedding；记录 token 与耗时；写 `backend/README.md` 的 key 设置步骤 | 0.5 天 | 是 | ⬜ |
| T-AI1 | 索引入库：`ResumeChunker` + `AiEmbeddingModel` + `resume_chunks`(V9) + 重建接口 | 单测（FakeEmbedding）断言分块规则；`smoke-ai.mjs`：12 份简历 → N 个 chunk；重复执行幂等 | 1~2 天 | 否 | ⬜ |
| T-AI2 | **单简历问答 MVP**（非流式）+ 引用 + HR 候选人详情「AI 助手」抽屉 | §0 的 5 条 DoD 全过；前端走 mock 分支；e2e 仅新增「入口存在 / 开关关闭时隐藏」断言 | 1~2 天 | 是（人工验收时） | ⬜ |
| T-AI3 | 流式输出（SSE 或 STOMP）+ 打字机 + 引用点击定位 | 首字延迟、中断、错误态；e2e 不依赖流式内容 | 1 天 | 是 | ⬜ |
| T-AI4 | 语义人才检索（与关键词版并行 A/B） | 同一组查询两边对比记录（相关性、命中率）；人才筛选页加「语义模式」 | 2 天 | 是 | ⬜ |
| T-AI5 | 面试问题生成（结构化输出 + 人工可改） | HR 面试准备页；输出 schema 校验 | 1~2 天 | 是 | ⬜ |
| T-AI6 | 工程化：混合检索（复用 `TalentMatchService` 权重）/ rerank / 评测集 / 成本看板 | 评测报告（10~20 题小集）；`usage_logs` 看板 | 持续 | 是 | ⬜ |
| T-AI7 | （可选）LangChain4j 对照实现 | 同一问答换 adapter 跑通，写对比笔记 | 1~2 天 | 是 | ⬜ |

## 8. 测试策略（保证不破坏现有基线）

- **假模型优先**：`FakeEmbeddingModel`（确定性哈希 → 归一化向量，保证同文本同向量）+
  `FakeChatModel`（模板回复）。单测/集成测试**不联网、不花钱、可重复**，与现有 173 项测试风格一致。
- **`smoke-ai.mjs`** 走 `provider=fake`（或本地 Ollama 向量），断言分块数、幂等、过滤边界
  （他人公司的简历检索不到）；`node scripts/smoke-ai.mjs` 纳入 `backend/README.md` 冒烟清单。
- **e2e 只加不变量断言**：入口显隐、开关关闭时不可见、无控制台错误；**不**断言模型具体输出。
- **回归门禁**：每轮改动后仍须 `npm run e2e` 63/63（真实后端）、`mvnw test` 全绿、
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
| DashScope 兼容协议的 path / 维度 / 批量（10 条）与 OpenAI 默认不同 | T-AI0 先 curl 验证；`dimensions` 显式配置；批量切分封装在 adapter 内 |
| 跨租户简历泄漏（最严重） | §5.3 过滤前置；`smoke-ai.mjs` 增加「A 公司查不到 B 公司简历」断言 |
| 简历文本被当作指令（Prompt 注入） | 「资料」与「指令」分区，并在系统提示里声明资料为纯数据 |
| e2e「零控制台错误」门禁 | AI 相关请求失败必须走统一包络处理，不在前端产生未捕获错误 |
| 成本不可控 | §5.4 配额 + `usage_logs`；演示额度上限写进配置 |
| 模型换代导致向量失效 | `resume_chunks` 以 `model` 维度隔离，换代即重建（索引重建接口已具备） |
| Spring Boot 3.5 已 EOL；Spring AI 2.0 需 Boot 4 + Jackson 3 | **单独开一轮** Boot 4 迁移，不与 AI 增量混做 |

**待确认（需用户后续拍板，不替用户决定）**：

1. 对话模型的最终档位（`qwen-plus` vs `qwen-max`：效果 / 成本权衡）。
2. 向量维度定 1024 还是 1536（精度 / 存储权衡）。
3. 是否同时接 Ollama 做「零成本模式」作为演示兜底（需装 Ollama，向量可，对话不建议）。
4. 配额策略（每 HR 每日问答次数上限、embedding 重建是否限流）。
5. 是否把求职者侧 AI 能力（简历体检 / 投递建议）纳入 Step 4 范围。
6. Boot 4 + Spring AI 2.0 迁移的时机（建议排在 Step 4 主线之后）。

## 11. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-10-19 | 首版。基于当前仓库实况（Boot 3.5.16 / MySQL 3307 / 无 Docker / 原生 Redis / 结构化简历 JSON / 多租户红线 / e2e 门禁）制定 Step 4 AI / RAG 增量方案；用户拍板：通义千问一站式、先内存后 MySQL、Spring AI 主线 + 事后补 LangChain4j、问答先行、**先落文档不动代码** |
