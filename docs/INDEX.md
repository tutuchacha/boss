# 聘聊 · 文档地图（INDEX.md）

> **这一页只做一件事：告诉你「想知道 X，去读哪个文件的哪一节」。**
> 不要从 `docs/` 逐个点开——大部分文件是归档，整读只会吃掉上下文。
> 状态与时序以 [`../PROGRESS.md`](../PROGRESS.md) 为准。

## 0. 启动必读（约 15k tokens）

| 顺序 | 文件 | 体量 | 读什么 |
| --- | --- | --- | --- |
| 1 | [`../AGENTS.md`](../AGENTS.md) | 7 KB | 硬性约束（每次开工都适用） |
| 2 | [`../PROGRESS.md`](../PROGRESS.md) | 15 KB | §0 快照 → §0.1 接续即用 → §3 下一步 |
| 3 | 本文件 | 4 KB | 检索入口 |

**到此为止。** 下面的文件一律「先 `grep`，再按行段读（单次 ≤ 300 行）」。

## 1. 按问题查

| 想知道 | 读 | 备注 |
| --- | --- | --- |
| 现在做到哪、下一步做什么 | `PROGRESS.md` §0~§3 | 状态唯一定稿 |
| 项目目标 / 范围 / 不做什么 | `PROJECT.md` | 100 行 |
| 系统结构、分层、数据流 | `ARCHITECTURE.md` | 282 行 |
| 命令、冒烟脚本、验收清单、测试基线 | `docs/state/commands.md` | 原 PROGRESS §7 + AI-CONTINUE §5 |
| 后端各 T 阶段接口事实（租户 / 错误码 / 分页 / WS / 上传） | `docs/state/backend-facts.md` | 原 PROGRESS §6.3 |
| AI / ARMS 环境变量、接口、判活判据 | `docs/state/ai-config.md` | 原 PROGRESS §0 两行 |
| 历次交付改过哪些文件、诚实边界 | `docs/state/deliverables.md` | 原 PROGRESS §3.1 / §3.3 |
| 踩坑：前端 | `docs/pitfalls/frontend.md` | §6.1~6.7 |
| 踩坑：后端核心 | `docs/pitfalls/backend-core.md` | §6.8~6.14 |
| 踩坑：消息 / 用户 / 招聘者 / 联调 | `docs/pitfalls/backend-collab.md` | §6.15~6.18 |
| 踩坑：AI / 可观测性 | `docs/pitfalls/ai.md` | §6.19~6.22 |
| 代码约定、协作规范、待用户确认 | `docs/AI-CONTINUE.md` §7~§9 | 其「导航」有分流 |
| API 契约（70 条） | `docs/api-endpoints.md` | 按模块 grep `§x.y` |
| 数据模型 | `docs/data-model.md` + `prisma/schema.prisma` | — |
| Step 3 任务板 | `docs/step3-plan.md` | 已完成，追溯用 |
| Step 4 AI 方案与任务板 | `docs/step4-ai-plan.md` | 进度看 §7 |
| 可观测性方案 | `docs/arms-plan.md` | 含判活三条判据 |
| 某天到底干了什么 | `docs/archive/worklog-*.md` | 默认不读 |
| 某版本改了什么 | `docs/archive/changelog-*.md` | 默认不读 |
| 旧交接（已过期） | `docs/HANDOFF.md`、`docs/dev-notes.md`、`docs/architecture.md` | 仅考古 |

## 2. 读取纪律（省上下文）

1. **不整读**：`worklog` / `CHANGELOG` / `archive/` 一律 `grep` 后按 `offset` / `limit` 读。
2. **不重复读**：同一会话读过的段落不重读；结论写进 `PROGRESS.md` 或回复里。
3. **只读相关坑**：按 `AI-CONTINUE.md` §6 索引表挑一节，不要整读 `pitfalls/`。
4. **不轻信旧文档**：状态以 `PROGRESS.md` 为准；发现过期就顺手修正，并在 worklog 记一句。
5. **大文件优先 `grep`**：`grep -n "关键词" 文件` → `read offset=行号-20 limit=120`。

## 3. 体积守卫

```powershell
./scripts/doc-size.ps1          # 体检：超软上限告警
./scripts/doc-size.ps1 -Strict  # CI / 提交前：超硬上限返回退出码 1
```

| 层级 | 文件 | 软上限 |
| --- | --- | --- |
| 启动必读 | `AGENTS.md` / `PROGRESS.md` / `docs/INDEX.md` | 9 / 16 / 6 KB |
| 按需 | `AI-CONTINUE.md` / `ARCHITECTURE.md` | 22 KB |
| 阶段日志 | `worklog.md` / `CHANGELOG.md` | 16 / 24 KB |
| 专题 | `docs/state/*` / `docs/pitfalls/*` | 24 / 20 KB |
| 归档 | `docs/archive/*` | 不限制（但不许回填进主文件） |

超限处理：按阶段切分 → 移入 `docs/archive/` → 在本文件与 `AI-CONTINUE.md` §6 补/改一行索引。
