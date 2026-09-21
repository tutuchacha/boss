# 聘聊 · 架构设计文档

> 版本：v0.3 · 最后更新：2026-09-21
> 状态：Step 0 产出，框架无关。前端框架与后端实现完成后可在此文档基础上修订。

---

## 1. 项目背景与目标

- **产品定位**：参考 Boss 直聘模式的招聘平台，C 端求职者 + 多 B 端企业客户共存。
- **业务模式**：求职者跨企业共享（同一份简历可投多个公司）；每个企业客户即一个"租户"（Tenant）。
- **首发规模**：百人千人级（日活几百、峰值并发几十到一两百）。
- **预留目标**：多客户（多租户）、并发、高速，均需为后期平滑升级预留，**避免推翻重做**。
- **首发平台**：H5 / Web（求职者端 + 招聘者后台）。App、小程序为后续路线图。

---

## 2. 规模定位与可升级路径

| 指标 | 首发阶段（百人千人） | 中期（万人级） | 远期（十万级以上） |
|---|---|---|---|
| 日活 | 几百 | 1万-10万 | 10万+ |
| 峰值并发 | 几十-两百 | 几千 | 数万 |
| 职位数据量 | 万级 | 十万-百万 | 百万-千万 |
| 搜索延迟容忍 | < 300ms | < 200ms | < 100ms |

### 2.1 首发可省略、但需预留接口位的组件

| 组件 | 首发方案 | 升级触发条件 | 预留方式 |
|---|---|---|---|
| Elasticsearch | PostgreSQL 全文索引（`tsvector` + GIN） | 用户过万 / 搜索 p95 > 200ms / 需要复杂相关度排序 | 搜索接口路径独立 `/api/v1/search/jobs`，前端不感知 |
| K8s | Docker Compose 单机 | 需要灰度发布、多环境隔离 | 后端无状态、配置外部化（Nacos） |
| Redis 集群 | Redis 单实例 | 内存 > 4G / QPS > 1万 | Spring Cache + Redisson 抽象，换集群只改配置 |
| DB 读写分离 | PostgreSQL 单主库 | 慢查询多 / 读 QPS 高 | ShardingSphere-JDBC 多数据源，配置切换 |
| 消息队列集群 | RabbitMQ 单实例 / Redis Stream | 消息积压 | 抽象 `MessageQueue` 接口 |
| CDN | Nginx 静态资源 + 浏览器缓存 | 静态资源带宽高 | 资源 URL 走统一 `assetUrl` 字段 |

### 2.2 Day 1 必须做（否则后期改不动）

1. **后端无状态**：JWT + Redis 存会话，任何实例可接管任何请求。
2. **数据表带 `companyId`（多租户字段）**：所有业务表加列，MyBatis-Plus 多租户拦截器自动注入。
3. **WebSocket 用 Redis 广播**：Spring STOMP + Redis broker，单机可跑，加机器即扩。
4. **接口幂等 + 限流**：按 `companyId + userId` 双维度限流（Bucket4j + Redis）。
5. **配置外部化**：动态配置走 Nacos，不写死代码。
6. **审计日志 + 操作埋点**：上线必备，出问题可追溯。
7. **业务错误码体系**：不依赖 HTTP 状态码，国际化与多端兼容。
8. **字段统一 camelCase**：前后端 JSON 字段命名统一，避免转换层。
9. **时间统一 ISO 8601 UTC**：后端存 UTC，前端按本地时区展示。

---

## 3. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│  客户端层                                                    │
│  ├─ 求职者 H5 (Next.js 16 / 待定)                           │
│  └─ 招聘者 Web 后台 (Next.js 16 / 待定)                     │
│  (后期可选) uni-app 小程序 / React Native / Flutter App     │
└───────────────────────┬─────────────────────────────────────┘
                        │ HTTPS (JWT in Header)
┌───────────────────────▼─────────────────────────────────────┐
│  API 网关 (Nginx / Spring Cloud Gateway)                    │
│  TLS 终结 · 限流 · 鉴权 · 租户路由 · 日志 · CORS            │
└───────────────────────┬─────────────────────────────────────┘
                        │
   ┌────────────────────┼────────────────────┬───────────────┐
   ▼                    ▼                    ▼               ▼
┌──────────┐    ┌──────────────┐    ┌──────────────┐  ┌──────────────┐
│ 主 API   │    │ 聊天服务     │    │ 搜索服务     │  │ 通知服务     │
│ Spring   │    │ Spring Boot  │    │ (首发复用主  │  │ Spring Boot  │
│ Boot     │    │ WebSocket    │    │  API 路径)   │  │ 邮件/短信/站 │
│ REST     │    │ STOMP        │    │ 后期独立 ES  │  │ 内信         │
└────┬─────┘    └──────┬───────┘    └──────────────┘  └──────┬───────┘
     │                 │                                    │
     │   ┌─────────────┴────────────────────────────────────┘
     │   │
     ▼   ▼
┌────────────────────────────────────────────────────────────┐
│  基础设施层                                                  │
│  ├─ PostgreSQL 15 (单主库)  ← 多租户: companyId + 索引       │
│  ├─ Redis 7 (缓存 + 会话 + 限流 + pub/sub)                  │
│  ├─ 对象存储 MinIO / S3 兼容 (简历附件、头像、公司Logo)      │
│  ├─ RabbitMQ (异步任务: 简历解析、索引、通知)                │
│  └─ XXL-Job (定时任务: 职位过期、活跃度统计)                 │
└────────────────────────────────────────────────────────────┘

监控: Spring Boot Actuator + Prometheus + Grafana
日志: ELK / Loki
链路: OpenTelemetry + Jaeger
错误: Sentry
```

---

## 4. 分层职责

### 4.1 客户端层
- 求职者 H5：浏览职位、搜索筛选、聊天、投递、简历管理、收藏、个人中心。
- 招聘者 Web 后台：发布/管理职位、查看牛人、聊天、处理投递、面试管理、公司主页。
- 所有客户端通过统一 API 网关访问后端，不直连数据库与基础设施。

### 4.2 API 网关层
- TLS 终结、CORS、限流（按 `companyId + userId`）、鉴权前置校验、租户路由。
- 首发可用 Nginx 直接承担；中期升级到 Spring Cloud Gateway。

### 4.3 业务服务层
- **主 API 服务**：账号、职位、简历、投递、收藏、面试、公司等核心业务。
- **聊天服务**：WebSocket 长连接，消息收发，Redis pub/sub 跨实例广播。
- **搜索服务**：首发复用主 API 的 `/api/v1/search/*` 路径（PG 全文索引）；后期独立部署 Elasticsearch 集群。
- **通知服务**：站内信、邮件、短信，由 RabbitMQ 异步触发。

### 4.4 基础设施层
- PostgreSQL、Redis、对象存储、消息队列、定时任务。

---

## 5. 多租户设计（Boss 模式）

### 5.1 租户定义
- **租户 = 企业客户**：每个注册的招聘企业即一个租户，主键 `companyId`。
- **求职者跨租户共享**：求职者账号不属于任何租户；简历、收藏、聊天、投递按 `companyId` 关联到具体租户。
- **招聘者归属租户**：招聘者账号属于一个 `companyId`，可被设为该租户管理员。

### 5.2 隔离策略（共享库 + companyId）

**首发采用：共享库 + `companyId` 列**
- 所有"租户私有数据"表加 `companyId` 列。
- MyBatis-Plus 多租户拦截器自动在 SQL 中注入 `WHERE company_id = ?`，避免漏写导致跨租户泄露。
- PostgreSQL **行级安全（RLS）**做兜底，即使应用层漏了，DB 层也挡得住。

**升级路径**：
- 中期：为头部企业客户分配独立 Schema（`tenant_xxx.*`）。
- 远期：为大客户提供独立库实例（"专属版"作为付费升级路径）。
- 升级时通过 ShardingSphere-JDBC 路由，应用代码改动极小。

### 5.3 跨租户数据（求职者侧）的特殊处理

下列表**不带 `companyId`**（属于求职者跨租户共享数据）：
- `User`（账号）
- `UserProfile`（求职者画像）
- `Resume`（简历，一份简历可投多家）

下列表**带 `companyId`**（租户私有数据）：
- `Company`、`CompanyMember`、`HrProfile`
- `Job`
- `Conversation`、`Message`（求职者和某公司 HR 的会话，归属该公司）
- `Application`（求职者投递某公司的某职位，归属该公司）
- `Interview`（面试邀请，归属该公司）

> 设计要点：`Application.companyId` 与 `Application.jobId` 冗余存储，避免 join 才能判定租户。

### 5.4 租户上下文传递

- HTTP 请求头：`X-Tenant-Id: <companyId>`（求职者请求时由网关从 JWT 中解析并注入，前端不感知）。
- WebSocket 连接：握手时携带 JWT，服务端解析 `companyId` 并绑定到 STOMP session。
- 后端拦截器统一从上下文取 `companyId`，注入 SQL。

### 5.5 租户级能力预留

| 能力 | 字段/实现 |
|---|---|
| 配额 | `TenantConfig.maxJobs` / `maxHrAccounts` / `maxMonthlyReach` |
| 限流 | Bucket4j 按 `companyId` 限流，Redis 存计数 |
| 功能开关 | `TenantConfig.features JSON`（发布职位/牛人推荐/视频面试 等） |
| 品牌定制 | `Company.logo / themeColor / customDomain`（白标预留） |
| 计费埋点 | `UsageLog` 表记录职位发布数、简历查看数、沟通数 |

---

## 6. 并发与高速预留

### 6.1 后端无状态化
- Session 完全基于 JWT + Redis，应用实例不保存会话状态。
- 任何实例可处理任何请求，水平扩展无障碍。

### 6.2 缓存策略
- **多级缓存**：
  - L1：本地内存缓存（Caffeine，毫秒级，TTL 30s-5min）
  - L2：Redis（分布式，TTL 5min-1h）
- **缓存对象**：
  - 职位详情（按 `jobId`，写时失效）
  - 分类树（全量缓存，更新时整体刷新）
  - 城市列表（静态缓存）
  - 用户基本信息（按 `userId`）
  - 公司基本信息（按 `companyId`）
- **缓存击穿/穿透防护**：布隆过滤器 + 空值缓存 + 互斥重建。

### 6.3 数据库
- 索引：所有筛选字段（city / cat / sub / exp / salary / days）建复合索引。
- 慢查询监控：pg_stat_statements + Prometheus 告警。
- 读写分离：ShardingSphere-JDBC 配置即可切换，应用无感。

### 6.4 搜索
- 首发：PG `tsvector` + GIN 索引覆盖关键词搜索。
- 升级：`/api/v1/search/*` 路径不变，后端切到 Elasticsearch 实例，前端无感。
- 索引同步：RabbitMQ 异步消费 `job.*` 事件，构建/更新 ES 文档。

### 6.5 实时聊天
- WebSocket：Spring STOMP + Redis broker。
- 消息投递：先落库（`Message` 表），再 pub 到 Redis，订阅者推送给客户端。
- 离线消息：客户端重连后通过 REST 拉取未读消息。
- 升级路径：Redis broker → 增加订阅实例 → 大规模时切 Centrifugo/NATS。

### 6.6 限流与降级
- 限流维度：`userId`（个人）+ `companyId`（企业）+ `ip`（防爬）。
- 限流算法：令牌桶（Bucket4j + Redis）。
- 降级：搜索高峰期返回缓存结果；聊天高峰期关闭"对方正在输入"等次要功能。

### 6.7 异步任务
- 简历解析（OCR / 结构化）：上传后异步处理，前端轮询状态。
- 职位索引同步：发布/修改后异步更新搜索索引。
- 通知：邮件/短信/站内信通过 RabbitMQ 异步发送。

---

## 7. 技术栈选型

### 7.1 后端（用户已确定 Spring Boot）

| 层 | 选型 | 备注 |
|---|---|---|
| 框架 | Spring Boot 3.x | Java 17 |
| Web | Spring MVC + Bean Validation | |
| ORM | MyBatis-Plus | 多租户拦截器原生支持 |
| DB | PostgreSQL 15 | RLS 兜底 |
| 缓存 | Spring Cache + Redisson | L1 Caffeine + L2 Redis |
| 实时 | Spring WebSocket + STOMP | Redis broker |
| 鉴权 | Spring Security + JWT | 无状态 |
| 限流 | Bucket4j + Redis | 双维度 |
| 任务 | XXL-Job + RabbitMQ | 定时 + 异步 |
| 文件 | MinIO / S3 兼容 | 简历、头像、Logo |
| API 文档 | SpringDoc OpenAPI | 自动生成契约 |
| 监控 | Actuator + Prometheus | |
| 部署 | Docker Compose → K8s | |

### 7.2 前端（待 Step 0 完成后用户最终确认）

- **沙箱当前环境**：Next.js 16 + TypeScript + Tailwind 4 + shadcn/ui（已锁定，H5 验收可用）。
- **状态管理**：Zustand（客户端状态）+ TanStack Query（服务端状态）。
- **数据层**：所有后端调用集中在 `src/api/`，组件不直接 fetch。
- **Mock 层**：`src/api/mock/` 提供本地数据，环境变量切换。

> 最终上线前端框架在 Step 0 完成后由用户决定；当前沙箱用作 H5 验收与契约验证场。

---

## 8. 部署演进路线

### 阶段一（首发，百人千人）
- Docker Compose 单机部署
- PG 单库 + Redis 单实例 + MinIO 单实例 + RabbitMQ 单实例
- 主 API 单实例 + 聊天服务单实例
- Nginx 反向代理 + TLS

### 阶段二（万人级）
- 主 API 多实例（2-4 个）+ 负载均衡
- 聊天服务多实例（Redis broker 跨实例广播）
- PG 主从读写分离
- 引入 Elasticsearch（搜索独立）
- Prometheus + Grafana 监控告警

### 阶段三（十万级以上）
- K8s 容器化部署
- PG 分库分表 / 独立库实例（大客户专属）
- Redis 集群
- ES 集群
- 灰度发布、蓝绿部署
- 多可用区容灾

---

## 9. 安全与合规

### 9.1 鉴权
- 求职者与招聘者统一 JWT，**角色字段**区分权限。
- JWT 短期（2h）+ Refresh Token 长期（30d），Refresh Token 存 Redis 可吊销。
- 关键操作（修改密码、提现、企业认证）二次校验。

### 9.2 跨租户数据安全
- 应用层：MyBatis-Plus 多租户拦截器强制注入 `companyId`。
- DB 层：PostgreSQL RLS 兜底。
- 接口层：网关校验 JWT 中 `companyId` 与请求路径中的 `companyId` 一致。

### 9.3 简历隐私
- 简历默认仅求职者本人可见。
- 求职者主动投递后，对应公司的 HR 可见。
- 招聘者主动查看简历需消耗"查看次数"（计费埋点），且记录到 `ResumeViewLog`。

### 9.4 内容安全
- 职位描述、聊天消息接入敏感词过滤。
- 图片上传接入内容审核（OSS 内容审核 / 第三方）。
- 反作弊：同 IP 高频注册、批量投递识别。

### 9.5 审计
- 关键操作（发布职位、修改公司信息、查看简历、关闭会话）记录到 `AuditLog`。
- 审计日志独立表，不可修改，只追加。

---

## 10. 文档与代码资产索引

| 内容 | 位置 |
|---|---|
| 原型 HTML | `docs/legacy/pinliao-app-v0.2.html` |
| 原开发笔记 | `docs/legacy/pinliao-dev-notes-v0.2.md` |
| 当前开发笔记 | `docs/dev-notes.md` |
| 架构设计（本文档） | `docs/architecture.md` |
| 数据模型说明 | `docs/data-model.md` |
| 数据模型 ERD | `prisma/schema.prisma` |
| API 契约规范 | `docs/api-contract.md` |
| API 接口清单 | `docs/api-endpoints.md` |
| TypeScript 类型定义 | `src/types/api.d.ts` |
| Mock 数据 | `src/api/mock/` |

---

## 11. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v0.3 | 2026-09-21 | Step 0 初版：架构、多租户、并发/高速预留、技术栈、部署演进 |
