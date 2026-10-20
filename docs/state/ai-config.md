# AI / ARMS 配置速查（state/ai-config.md）

> 由 `PROGRESS.md` §0 快照中两行迁出（2026-10-20），避免快照表被配置细节撑大。
> 密钥只存在**用户级环境变量**，仓库零命中；方案见 [`../step4-ai-plan.md`](../step4-ai-plan.md)、
> [`../arms-plan.md`](../arms-plan.md)，后端配置见 `backend/README.md`。

## 配置表

| 项 | 值 |
| --- | --- || AI（Step 4） | 依赖 `spring-ai-bom:1.1.8` + `spring-ai-starter-model-openai`；配置走 **用户级环境变量** `PINLIAO_AI_API_KEY` / `_BASE_URL` / `_CHAT_MODEL`（`qwen3.5-122b-a10b`）/ `_EMBEDDING_MODEL`（`text-embedding-v4`）/ `_EMBEDDING_DIM`（1024）/ `_ENABLED`（true）/ `_PROVIDER`（`springai` / `fake`）/ `_CHUNK_MAX_CHARS` / `_EMBEDDING_BATCH_SIZE` / `_QA_TOP_K` / `_QA_MIN_SCORE` / `_QA_MAX_TOKENS` / `_QA_DAILY_LIMIT`；接口 `GET /api/v1/ai/status|ping`、`POST /api/v1/ai/index/rebuild`、`GET /api/v1/hr/applications/{id}/ai/index-status`、`POST /api/v1/hr/applications/{id}/ai/qa`、`POST .../ai/qa/stream`（SSE；均需登录）；前端流式走站点根路径代理 `POST /api/ai/qa-stream/{id}`（绕过 rewrites 缓冲）；**key 只在环境变量、仓库零命中**（详见 `docs/step4-ai-plan.md` §6.2 与 `backend/README.md`「AI 配置」） |
| 可观测性（T-OBS） | **T-OBS1 ✅ + T-OBS2 ✅（2026-10-19）**：阿里云 ARMS **应用监控**探针（`AliyunJavaAgent` 5.1.2 = OTel 2.23.0 改名版）只挂应用 JVM；环境变量 `PINLIAO_ARMS_ENABLED` / `_LICENSE_KEY` / `_APP_NAME`；脚本 `fetch-arms-agent.ps1` / `dev-with-arms.ps1`；`TraceIdFilter` 为「活跃 OTel span → 透传头 → UUID」。**已打通**：LicenseKey 写入用户级环境变量后，控制台「应用监控 → 应用列表」可见 `pinliao-backend`，响应包络 traceId 即探针的 32 位 traceId（span 分支命中），应用 JVM 有到阿里云 80 端口的上报连接；带探针 e2e **63/63**。注意 `status` 文件**不刷新、不能作授权判据**（判活三条判据见 `docs/arms-plan.md` §4）。方案见 [`docs/arms-plan.md`](../arms-plan.md) |
