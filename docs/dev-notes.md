# 聘聊 · 开发笔记

> 用途：保留开发现场。下次对话开始时，把本文件和相关文档发给 Claude，即可快速接续。
> 最后更新：2026-09-21，Step 0 完成（v0.3）

---

## 1. 项目概述与约束

- 目标：做一款参考 Boss 直聘模式的招聘平台，最终上线运营。C 端求职者 + 多 B 端企业客户共存。
- 当前阶段：Step 0 已完成（架构 + 数据模型 + API 契约 + Mock 数据），等待进入 Step 1（前端开发）。
- App 暂名「聘聊」（占位名，可改）。视觉参考 Boss 青绿色调，但配色、文案、头像均为自有设计。

## 2. 关键决策记录（截至 v0.3）

### 2.1 业务模式与规模
- **业务模式**：Boss 直聘模式。求职者跨企业共享（同一份简历可投多家公司），每个企业客户即一个"租户"。
- **首发规模**：百人千人级（日活几百、峰值并发几十到一两百）。
- **预留目标**：为多客户（多租户）、并发、高速预留**平滑升级**路径，避免推翻重做。

### 2.2 平台与技术栈
- **首发平台**：H5 / Web（求职者端 + 招聘者后台）。**只做 H5**，App、小程序为后续路线图（用户选项 C）。
- **后端**：Spring Boot（用户已确定）。Java 17 + Spring Boot 3.x + MyBatis-Plus + PostgreSQL + Redis。
- **前端**：当前沙箱锁定 Next.js 16 + TypeScript + Tailwind 4 + shadcn/ui（H5 验收用）。
  - 最终上线前端框架在 Step 0 完成后由用户决定。
  - 若将来要出 App / 小程序，再起 uni-app（Vue 系）工程，复用 API 契约与业务逻辑。
- **工作流**：先做 Step 0（框架无关契约），再做前端开发与验收，最后开发后端。

### 2.3 架构选型要点
- **多租户**：共享库 + `companyId` 列；MyBatis-Plus 多租户拦截器自动注入；PostgreSQL RLS 兜底。
- **后端无状态**：JWT + Redis 会话，可水平扩展。
- **WebSocket**：Spring STOMP + Redis broker，单机可跑，加机器即扩。
- **搜索**：首发 PG 全文索引；接口路径独立 `/api/v1/search/jobs`，后期切到 Elasticsearch 前端无感。
- **限流**：按 `userId` + `companyId` + `ip` 三维度，Bucket4j + Redis。
- **配置外部化**：Nacos。
- **部署演进**：Docker Compose（首发）→ 多实例 + PG 主从 + ES（万人级）→ K8s（十万级以上）。

### 2.4 API 契约要点
- 统一响应包络：`{ code, message, data, traceId }`。
- 字段统一 camelCase；时间统一 ISO 8601 UTC。
- 分页统一游标分页（cursor + limit），不用 offset。
- 鉴权用 Bearer Token（短 accessToken + 长 refreshToken），跨租户天然防 CSRF。
- 错误码采用 5 位数字（前 3 位对应 HTTP 状态码）。
- 文件上传走预签名直传对象存储。

## 3. 文档与代码资产索引

| 内容 | 位置 |
|---|---|
| 原型 HTML（v0.2） | `docs/legacy/pinliao-app-v0.2.html` |
| 原开发笔记（v0.2） | `docs/legacy/pinliao-dev-notes-v0.2.md` |
| **当前开发笔记（本文档）** | `docs/dev-notes.md` |
| 架构设计 | `docs/architecture.md` |
| 数据模型说明 | `docs/data-model.md` |
| 数据模型 ERD（Prisma） | `prisma/schema.prisma` |
| API 契约规范 | `docs/api-contract.md` |
| API 接口清单（68 个） | `docs/api-endpoints.md` |
| TypeScript 类型定义 | `src/types/api.d.ts` |
| Mock 数据 - 公司 | `src/api/mock/companies.ts` |
| Mock 数据 - 分类/职责模板 | `src/api/mock/categories.ts` |
| Mock 数据 - 字典 | `src/api/mock/dict.ts` |
| Mock 数据 - 职位（51 条） | `src/api/mock/jobs.ts` |
| Mock 数据 - 当前用户 + 会话 | `src/api/mock/conversations.ts` |
| Mock 数据出口 | `src/api/mock/index.ts` |

## 4. 功能清单（v0.2 原型，待 Step 1 重构）

- 职位 Tab：城市切换、实时搜索、一级分类 + 二级岗位、筛选面板、空状态。
- 职位详情页：薪资、福利、招聘者卡片、职责要求、公司信息、收藏、立即沟通/继续沟通。
- 消息 Tab：会话列表、未读角标、预置两条会话。
- 聊天页：消息气泡、快捷回复、关键词自动回复、"对方正在输入"。
- 我的 Tab：个人卡片、求职期望编辑、收藏列表、简历预览、外观切换、招聘者身份切换占位。

## 5. 已知限制（Step 0 阶段）

- 仍无真实账号与接口；Mock 数据基于 HTML 原型提取，前端验收用。
- WebSocket 主题、限流、缓存等仅在契约中预留，待后端实现。
- 简历暂以 JSON 字段存储；如需更强查询后期可拆表（已在升级路径中预留）。
- 招聘者端 Web 后台的具体页面布局尚未设计。

## 6. 后续路线（建议）

### Step 1 — 前端开发（按契约 + Mock，在沙箱用 Next.js）
1. 路由骨架（职位 / 消息 / 我的 + 详情页 / 聊天页 / 收藏 / 简历）。
2. 青绿色调设计系统（shadcn/ui 主题定制）。
3. service 层封装（所有后端调用走 `src/api/`，组件不直接 fetch）。
4. mock 切换开关（环境变量 `NEXT_PUBLIC_USE_MOCK`）。
5. 各页面交互与状态管理（Zustand + TanStack Query）。

### Step 2 — 前端验收（用户在预览面板逐页过）

### Step 3 — 后端开发（Spring Boot，按契约实现）
1. 鉴权 + 多租户拦截器 + RLS。
2. 职位 / 分类 / 城市等只读接口。
3. 聊天（WebSocket STOMP + Redis）。
4. 投递 / 面试 / 收藏。
5. 招聘者后台接口。

### Step 4 — 联调（前端切环境变量，Mock → 真实接口）

## 7. 接续开发的最快方式

1. 新对话开头发送：本文件 + `docs/architecture.md` + `docs/api-contract.md`。
2. 说明本次想做的事，例如「进入 Step 1，先做职位列表页」或「开始设计后端某模块」。
3. 关键决策已固化在 §2，若需调整请明确说明（如「改成多端方案」「改用 Spring Cloud Gateway」等）。
