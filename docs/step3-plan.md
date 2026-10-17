# 聘聊 · Step 3 后端开发计划（分块执行 + 上下文交接协议）

> 本文件回答两个问题：**Step 3 怎么分块做**、**每个新会话怎么低上下文接力**。
> 状态唯一定稿在 `PROGRESS.md`；本文件是执行计划与任务板（勾选进度以这里为准）。
> 最后更新：2026-10-17 · 状态：计划就绪，待开工 T0

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
| 语言/框架 | Java 17+ · Spring Boot 3.x · MyBatis-Plus |
| 数据库 | PostgreSQL（生产基线；多租户 `companyId`，RLS 兜底） |
| 缓存/限流 | Redis |
| 实时 | WebSocket（4 主题，规范见 `docs/api-contract.md`） |
| 构建 | Maven Wrapper（`mvnw`，不依赖本机 Maven 版本） |
| 迁移 | Flyway（`V1__schema.sql` / `V2__seed_demo.sql`） |
| 接口文档 | springdoc-openapi（/swagger-ui） |
| 目录 | 建议 `backend/`（单仓，与契约/文档同一 git 历史；备选独立仓库） |

**本机环境盘点（2026-10-17 实测）**：

- 仅 JDK 1.8（`JAVA_HOME=D:\JDK1.8`）→ **需安装 Temurin 17**（`winget install EclipseAdoptium.Temurin.17.JDK`，包已确认存在）
- Maven 3.6.3（用 `mvnw` 规避）；Git 2.20.1 可用；winget v1.29 可用
- 无 Docker；MySQL 服务存在但**已停止**（3306 空闲）；无 PostgreSQL/Redis（5432/6379 空闲）
- `C:\dev` 有 Tomcat/Nacos/MySQL/Navicat 等工具链（本机是 Java 开发机）

**前端对接开关（已就绪，零代码改动）**：`src/config/env.ts` 读
`NEXT_PUBLIC_USE_MOCK=false` / `NEXT_PUBLIC_API_BASE_URL` / `NEXT_PUBLIC_WS_BASE_URL` / `NEXT_PUBLIC_WS_PORT`。
改环境变量后需重启 `npm run dev`。

## 2. 任务板（分块进度以本表为准）

图例：⬜ 未开始 · 🔄 进行中 · ✅ 完成

| 编号 | 内容 | 接口范围 | 预估会话 | 状态 |
|---|---|---|---|---|
| T0 | 环境安装与工程骨架（含统一包络/异常/健康检查） | — | 1 | ⬜ |
| T1 | 数据库迁移与演示种子（对齐 mock 数字） | 全部表 | 1–2 | ⬜ |
| T2 | 鉴权模块（JWT 双 token、验证码、注册） | §1（7 条） | 1 | ⬜ |
| T3 | 职位 + 分类字典（浏览/搜索/HR 管理） | §6 / §7（12 条） | 1 | ⬜ |
| T4 | 投递 + 面试闭环（状态机/校验码） | §9 / §10（10 条） | 1 | ⬜ |
| T5 | 公司模块 + 企业认证（含 1.2s 自动通过） | §5（6 条） | 1 | ⬜ |
| T6 | 消息 + WebSocket（含自动回复演示语义） | §8（8 条）+ 4 主题 | 1–2 | ⬜ |
| T7 | 求职者/简历/收藏/上传 | §2 / §3 / §11 / §12（19 条） | 1 | ⬜ |
| T8 | 招聘者 + 候选人 + 人才筛选（移植关键词版） | §4（6 条，含 §4.5/§4.6） | 1 | ⬜ |
| T9 | 全量联调验收（前端 e2e 63/63） | 全部 | 1–2 | ⬜ |
| T10+ | 后续增量（不进本轮）：语义检索 / LLM 推荐理由 / 外部简历库 | — | — | — |

> `docs/api-endpoints.md` 模块小计存在 67 vs 70 的既有账目差异（待用户确认），
> 接口条数以正文为准；分块只按功能域划分，不依赖该账目。

**依赖关系**：T0 → T1 → T2 → 之后各模块可按序推进（T6 最难、T8 依赖 T1 简历种子）；
T9 必须最后；每个 chunk 一次会话（或按需拆小）。

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

- **T0 环境与骨架**
  - 装 JDK 17（winget）；选装 PostgreSQL 16 + Redis（见 §6 决策）
  - 生成 `backend/`（start.spring.io：Web、Validation、PostgreSQL Driver、Flyway、
    Data Redis、WebSocket、Actuator、Lombok；MyBatis-Plus 与 springdoc 手动加依赖）
  - 统一包络（ResponseAdvice）+ 全局异常（@RestControllerAdvice）+ 错误码表 + traceId + `/actuator/health`
  - 多环境配置（local：本机 PG/Redis）；`.gitignore`（target/ 等）；代码注释与提交用中文
  - 验收：`mvnw spring-boot:run` 启动 + health 200 + 样例接口包络正确
- **T1 数据迁移与种子**
  - Flyway `V1` 按 `docs/data-model.md` 建表（`companyId` 多租户列 + 必要索引）；`V2` 演示种子
  - 种子来源：`src/api/mock/`（hrJobs / candidates / applications / hrConversations / resumes /
    companies / auth）——数字与文案对齐 §3 清单
  - MyBatis-Plus：分页插件、租户拦截器（companyId 注入）、创建/更新时间自动填充
  - 验收：启动自动 migrate 成功 + 抽查 SQL（各表行数 = §3 清单）
- **T2 鉴权**：§1 全部 + JWT（access/refresh，TTL 按 api-contract.md）+ Redis（refresh 白名单、
  验证码、限流）+ 角色权限（JOB_SEEKER/HR/COMPANY_ADMIN）+ 注册建公司；验收：JUnit 集成 + curl 冒烟
- **T3 职位与字典**：§6/§7；列表/搜索/筛选/排序（先用普通 SQL，全文索引后置）/详情 + HR CRUD/概览；
  验收：JUnit + 38 条可见职位
- **T4 投递与面试**：§9/§10；状态机 + 会话按需创建（先占位）+ 校验码 40911/40913/40914/40915；验收：JUnit
- **T5 公司模块**：§5；资料编辑（改名同步职位公司名）+ 认证提交/自动通过/轮询；验收：JUnit（含异步等待）
- **T6 消息与 WS**：§8 + 4 主题；未读计数（Redis）+ 自动回复演示语义；验收：JUnit + WS 手测
  （可选：与前端 ChatPage 半联调）
- **T7 求职者/简历/收藏/上传**：§2/§3/§11/§12；上传先落本地目录 + 静态映射（后续换 OSS）；验收：JUnit
- **T8 招聘者与人才筛选**：§4；候选人列表/详情/标记 + §4.5/§4.6 **移植** `mock/talentMatch.ts`
  （提取词逻辑翻译 Java，出参与排序规则不变）；验收：JUnit +（可选）前端人才筛选半联调
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

## 6. 待用户拍板

| # | 事项 | 建议 |
|---|---|---|
| 1 | 仓库布局 | **单仓 `backend/`**（契约/文档/代码同一历史，AI 接力最省心）；备选独立仓库 |
| 2 | 数据库/中间件 | **PostgreSQL 16 + Redis 本地安装**（基线已定；未来 pgvector/RLS 同源）。本机 MySQL 已停、无 PG/Redis，需安装；winget 可用 |
| 3 | JDK | 装 **Temurin 17**（`winget install EclipseAdoptium.Temurin.17.JDK`）；21 可用但非必须 |
| 4 | 开工时机 | 开新会话从 T0 开始（本文件与 PROGRESS 已就绪） |

## 7. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-10-17 | 首版：T0~T9 分块任务板、演示语义对齐清单、上下文交接协议（开工/收工/应急）、待拍板事项 |
