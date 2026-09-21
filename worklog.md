# 聘聊项目 · 工作日志

本文件记录所有协作 Agent 的工作记录，按时间顺序追加。
每个新段落以 `---` 起始，遵循统一模板。

---
Task ID: 0
Agent: orchestrator (main)
Task: Step 0 — 产出框架无关的架构文档、数据模型、API 契约，沉淀决策

Work Log:
- 读取用户上传的原型（pinliao-app.html）与开发笔记（pinliao-dev-notes.md），归档至 docs/legacy/
- 与用户对齐关键决策：
  - 规模定位：首发百人千人级，但需为后期多客户/并发/高速预留平滑升级路径
  - 业务模式：Boss 直聘模式（C 端求职者 + 多 B 端企业客户）
  - 后端技术栈：Spring Boot（已确定）
  - 目标平台：首发只做 H5/Web（C 选项），App 后续再说
  - 前端框架：当前沙箱锁定 Next.js 16（H5 验收用）；最终上线前端框架待 Step 0 完成后由用户决定
  - 工作流：先做 Step 0（架构 + 数据模型 + API 契约），框架无关，避免后期返工
- 产出文档（docs/）：
  - architecture.md — 架构设计
  - data-model.md — 数据模型说明
  - api-contract.md — API 契约规范
  - api-endpoints.md — API 接口清单
  - dev-notes.md — 延续原开发笔记，追加本次问答关键决策
- 产出代码资产：
  - prisma/schema.prisma — 重写为聘聊领域 ERD（多租户字段、索引、关系）
  - src/types/api.d.ts — 与契约同步的 TypeScript 类型定义
  - src/api/mock/ — Mock 数据集（51 个职位、聊天、公司、分类树、城市）

Stage Summary:
- Step 0 完成。所有产出框架无关，无论前端最终选 Next.js / Vue / uni-app，后端选 Spring Boot / 其他，均可复用。
- 下一步等待用户确认进入 Step 1（前端开发）。
