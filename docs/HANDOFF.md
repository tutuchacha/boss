# 聘聊 · 接续开发指南（HANDOFF）

> 本文件是给"下一个 AI 模型"或"下一个开发者"的入口文档。
> 阅读本文件 + `docs/dev-notes.md` + `docs/architecture.md` 即可快速接续开发。
> 最后更新：2026-09-21，Step 1 完成，等待用户验收

---

## 1. 一句话项目概览

聘聊 = 参考 Boss 直聘模式的招聘平台（C 端求职者 + 多 B 端企业客户），首发 H5/Web，后端 Spring Boot，前端当前用 Next.js 16 沙箱验收，最终上线框架待用户决定。

---

## 2. 当前状态

| 阶段 | 状态 | 产物 |
|---|---|---|
| Step 0 架构 + 数据模型 + API 契约 | ✅ 完成 | `docs/` + `prisma/schema.prisma` + `src/types/api.ts` |
| Step 1 前端开发（Next.js + Mock） | ✅ 完成 | `src/components/pinliao/` + `src/api/` + `src/store/` |
| Step 2 前端验收 | ⏳ 进行中 | 用户在预览面板验收中 |
| Step 3 后端开发（Spring Boot） | ⏸ 待开始 | 等前端验收完成 |
| Step 4 联调 | ⏸ 待开始 | Mock → 真实接口切换 |

**当前可运行状态**：
- dev server 在端口 3000 运行（启动命令：`bun run dev`）
- H5 全部页面可交互（51 个职位、聊天、收藏、主题切换等）
- 所有数据来自本地 Mock（`src/api/mock/`），无需后端

---

## 3. 关键决策清单（下一个模型无需重新问）

### 3.1 业务与规模
- **业务模式**：Boss 直聘模式。求职者跨企业共享（同一份简历可投多家），每个企业客户即一个"租户"。
- **首发规模**：百人千人级（日活几百、峰值并发几十到一两百）。
- **预留目标**：为多客户（多租户）、并发、高速预留**平滑升级**路径，避免推翻重做。

### 3.2 平台与技术栈
- **首发平台**：H5 / Web（求职者端 + 招聘者后台）。**只做 H5**，App、小程序为后续路线图。
- **后端**：Spring Boot（已确定）。Java 17 + Spring Boot 3.x + MyBatis-Plus + PostgreSQL + Redis。
- **前端**：当前沙箱锁定 Next.js 16 + TypeScript + Tailwind 4 + shadcn/ui（H5 验收用）。
  - **最终上线前端框架待用户决定**。若将来要出 App/小程序，再起 uni-app（Vue 系）工程，复用 API 契约与业务逻辑。
- **工作流**：Step 0 契约 → Step 1 前端开发 → Step 2 验收 → Step 3 后端 → Step 4 联调。

### 3.3 架构选型要点
- **多租户**：共享库 + `companyId` 列；MyBatis-Plus 多租户拦截器自动注入；PostgreSQL RLS 兜底。
- **后端无状态**：JWT + Redis 会话，可水平扩展。
- **WebSocket**：Spring STOMP + Redis broker，单机可跑，加机器即扩。
- **搜索**：首发 PG 全文索引；接口路径独立 `/api/v1/search/jobs`，后期切到 Elasticsearch 前端无感。
- **限流**：按 `userId` + `companyId` + `ip` 三维度，Bucket4j + Redis。
- **部署演进**：Docker Compose（首发）→ 多实例 + PG 主从 + ES（万人级）→ K8s（十万级以上）。

### 3.4 API 契约要点
- 统一响应包络：`{ code, message, data, traceId }`。
- 字段统一 camelCase；时间统一 ISO 8601 UTC。
- 分页统一游标分页（cursor + limit），不用 offset。
- 鉴权用 Bearer Token（短 accessToken + 长 refreshToken）。
- 错误码采用 5 位数字（前 3 位对应 HTTP 状态码）。
- 文件上传走预签名直传对象存储。
- 完整规范见 `docs/api-contract.md`，68 个接口见 `docs/api-endpoints.md`。

---

## 4. 文件索引

### 4.1 文档（框架无关，任何模型都能用）

| 文件 | 内容 |
|---|---|
| `docs/HANDOFF.md`（本文件） | 接续入口 |
| `docs/dev-notes.md` | 开发笔记（决策记录、功能清单、路线图） |
| `docs/architecture.md` | 总体架构、多租户、并发/高速预留、技术栈、部署演进 |
| `docs/data-model.md` | 表清单、字段说明、索引、Mermaid ER 图 |
| `docs/api-contract.md` | API 通用规范（URL/方法/包络/错误码/分页/限流/WS/安全） |
| `docs/api-endpoints.md` | 13 个模块共 68 个接口完整清单 |
| `docs/legacy/pinliao-app-v0.2.html` | 原始 HTML 原型（601 行，无外部依赖） |
| `docs/legacy/pinliao-dev-notes-v0.2.md` | 原开发笔记（v0.2） |

### 4.2 代码资产

| 路径 | 内容 |
|---|---|
| `prisma/schema.prisma` | 18 个模型 + 枚举 + 索引 + 多租户字段标注 |
| `src/types/api.ts` | 与契约同步的 TypeScript 类型定义 |
| `src/config/env.ts` | 环境变量集中读取 |
| `src/api/client.ts` | 统一 fetch 封装 + ApiError + 业务码解包 |
| `src/api/{categories,jobs,conversations,index}.ts` | service 层 |
| `src/api/mock/` | Mock 数据（51 个职位、聊天、公司、分类树、字典） |
| `src/store/pinliao.ts` | Zustand 全局状态 + persist + localStorage 键 `pinliao.v3` |
| `src/components/pinliao/` | 业务组件（AppShell/TabBar/JobsView/JobDetailPage/ChatPage/MeView 等） |
| `src/app/{layout,page,globals.css}` | Next.js 入口 + 青绿主题 + 深色 + 移动端 safe-area |
| `worklog.md` | 所有协作 Agent 的工作记录（按 Task ID 追加） |

---

## 5. 如何启动开发

### 5.1 环境要求
- Node.js 18+ / Bun（推荐）
- Next.js 16 已安装（`package.json` 中所有依赖就绪）
- 数据库：当前用 SQLite（`prisma/schema.prisma` 配置），生产换 PostgreSQL

### 5.2 启动命令
```bash
cd /home/z/my-project
bun install          # 安装依赖（首次或依赖变更时）
bun run dev          # 启动 dev server，端口 3000
bun run lint         # ESLint 检查
bunx tsc --noEmit    # TypeScript 类型检查
bun run db:push      # 推送 Prisma schema 到数据库
```

### 5.3 环境变量（`.env`）
```
DATABASE_URL=file:/home/z/my-project/db/custom.db
# 联调阶段加：
# NEXT_PUBLIC_USE_MOCK=false
# NEXT_PUBLIC_API_BASE_URL=https://your-api.com/api/v1
# NEXT_PUBLIC_WS_BASE_URL=/
# NEXT_PUBLIC_WS_PORT=3003
```

### 5.4 重要：dev server 启动方式
沙箱环境中，`bun run dev` 直接启动可能会在 Bash session 结束后被杀。需要用 subshell + setsid 启动才能持久：
```bash
( setsid /home/z/my-project/node_modules/.bin/next dev -p 3000 > /home/z/my-project/dev.log 2>&1 < /dev/null & )
```

---

## 6. 接续开发的"启动提示词"

下一个对话开始时，把以下内容发给新模型即可无缝接续：

```
我有一个招聘平台项目"聘聊"，已完成前端 Step 1 阶段（Next.js 16 + Mock 数据），
现在要继续开发。请先阅读以下文件了解项目全貌：

1. docs/HANDOFF.md（接续入口，必读）
2. docs/dev-notes.md（决策记录）
3. docs/architecture.md（架构设计）
4. docs/api-contract.md（API 规范）
5. worklog.md（已完成的 Agent 工作记录）

本次我想做的是：<你的需求，比如"开始 Step 3 后端开发"或"调整某页面交互">
```

---

## 7. 保存与迁移方案

### 7.1 方案 A：打包下载（最直接）
```bash
cd /home/z/my-project
# 打包（排除 node_modules / .next / 数据库等）
zip -r pinliao-$(date +%Y%m%d).zip . \
  -x "node_modules/*" \
  -x ".next/*" \
  -x ".git/*" \
  -x "db/*" \
  -x "*.log" \
  -x "upload/*" \
  -x "download/*" \
  -x "skills/*" \
  -x "examples/*" \
  -x "mini-services/*"
# 然后从沙箱下载这个 zip
```

### 7.2 方案 B：推送到 GitHub/Gitee（最持久）
```bash
cd /home/z/my-project
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

### 7.3 方案 C：仅保存关键文档（最小）
如果只想带走"设计成果"而不带代码，打包 `docs/` + `prisma/schema.prisma` + `src/types/api.ts` + `worklog.md` 即可，任何框架的后端/前端都能基于这些契约重新实现。

---

## 8. 已知限制与后续路线

### 8.1 当前 Step 1 阶段的限制
- 数据、聊天、自动回复均为前端 Mock，无真实账号与接口
- localStorage 仅存于当前浏览器（键 `pinliao.v3`）
- 简历为静态预览，不可编辑
- 招聘者端 Web 后台未实现（仅求职者端）

### 8.2 后续路线
- **Step 2**：前端验收（用户在预览面板逐页过）
- **Step 3**：Spring Boot 后端开发（按契约实现 68 个接口）
- **Step 4**：联调（前端切 `NEXT_PUBLIC_USE_MOCK=false`，Mock → 真实接口）
- **未来**：App/小程序（若决定走多端，起 uni-app 工程复用契约）

### 8.3 待用户确认的事项
- 最终上线前端框架（Next.js / Vue / uni-app）
- 招聘者端 Web 后台布局
- 简历编辑功能细节
- 投递流程与面试邀请的交互

---

## 9. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-09-21 | Step 1 完成后创建，作为接续入口 |
