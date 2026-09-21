# 聘聊 · API 契约规范

> 版本：v0.3 · 最后更新：2026-09-21
> 本文档定义所有 API 的通用规范。具体接口清单见 `docs/api-endpoints.md`。
> 框架无关：无论前端 Next.js / Vue / uni-app、后端 Spring Boot / 其他，均按本规范实现。

---

## 1. 基础约定

### 1.1 URL

- 基础路径：`/api/v1`
- 版本号在路径中，未来如有不兼容升级走 `/api/v2`
- 资源用复数名词：`/api/v1/jobs`、`/api/v1/conversations`
- 子资源层级表达：`/api/v1/jobs/{jobId}/applications`
- 操作动词放在路径末尾：`/api/v1/applications/{id}/withdraw`

### 1.2 HTTP 方法语义

| 方法 | 语义 | 幂等 | 安全 |
|---|---|---|---|
| GET | 查询 | 是 | 是 |
| POST | 创建 / 触发动作 | 否 | 否 |
| PUT | 全量替换 | 是 | 否 |
| PATCH | 部分更新 | 否 | 否 |
| DELETE | 删除（软删） | 是 | 否 |

> 注：触发类操作（如"继续沟通""接受面试"）用 POST + 动作路径，不勉强套 CRUD。

### 1.3 字段命名

- 统一 **camelCase**，前后端 JSON 字段一致，无转换层。
- 时间字段：`createdAt` / `updatedAt` / `publishedAt` / `lastMessageAt`。
- 布尔字段：`isXxx` 前缀（如 `isVerified` / `isActive`）。
- 计数字段：`xxxCount`（如 `viewCount` / `unreadCount`）。

### 1.4 时间格式

- 所有时间字段：**ISO 8601 UTC**，如 `2026-09-21T08:30:00Z`。
- 后端存 UTC，前端按本地时区展示。
- 日期字段（如生日）：`YYYY-MM-DD`。

### 1.5 字符编码

- UTF-8。

---

## 2. 鉴权

### 2.1 JWT

- 登录后返回 `accessToken`（短期，2h）和 `refreshToken`（长期，30d）。
- 请求头：`Authorization: Bearer <accessToken>`
- `accessToken` 自包含 `userId` / `role` / `companyId`（招聘者）/ `exp`。
- `refreshToken` 存 Redis，可服务端吊销。

### 2.2 角色枚举

| role | 说明 |
|---|---|
| `JOB_SEEKER` | 求职者 |
| `HR` | 招聘者（普通成员） |
| `COMPANY_ADMIN` | 企业管理员 |
| `PLATFORM_ADMIN` | 平台管理员 |

### 2.3 租户上下文

- 招聘者请求：网关从 JWT 解析 `companyId` 注入请求头 `X-Tenant-Id`。
- 求职者请求：不带 `companyId`（求职者跨租户）。
- 后端拦截器统一从 `X-Tenant-Id` 或 JWT 中取 `companyId`，注入 SQL。

### 2.4 接口分类

| 类别 | 是否需鉴权 | 示例 |
|---|---|---|
| 公开 | 否 | 登录、注册、职位列表（未登录浏览）、职位详情 |
| 求职者 | 是（JOB_SEEKER） | 收藏、投递、聊天 |
| 招聘者 | 是（HR / COMPANY_ADMIN） | 发布职位、查看简历、面试管理 |
| 平台 | 是（PLATFORM_ADMIN） | 租户管理、内容审核 |

---

## 3. 请求规范

### 3.1 Content-Type

- `application/json; charset=utf-8`
- 文件上传：`multipart/form-data`

### 3.2 查询参数

| 参数 | 含义 | 示例 |
|---|---|---|
| `cursor` | 游标分页标识 | `eyJpZCI6IjEyMyJ9`（base64 编码的 JSON） |
| `limit` | 每页条数，默认 20，最大 100 | `limit=20` |
| `sort` | 排序字段+方向 | `sort=-publishedAt`（前缀 - 表示降序） |
| `q` | 关键词搜索 | `q=前端` |
| `city` / `cat` / `sub` / `exp` / `sal` | 筛选 | `city=上海&cat=互联网/AI` |

### 3.3 路径参数

- 资源 ID 用路径参数：`/api/v1/jobs/{jobId}`

### 3.4 请求体

- JSON 对象，字段 camelCase。
- 必填字段在 OpenAPI schema 中标注。
- 字符串字段最大长度由后端校验，返回 422 + 字段级错误。

---

## 4. 响应规范

### 4.1 响应包络（Envelope）

**统一包络**：

```json
{
  "code": 0,
  "message": "ok",
  "data": { ... },
  "traceId": "abc-123"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | number | 业务码，0 表示成功，非 0 见错误码表 |
| message | string | 提示信息，前端可直接展示 |
| data | object / array / null | 业务数据，失败时为 null |
| traceId | string | 链路追踪 ID，便于排查 |

> 注意：HTTP 状态码与业务码分开。HTTP 反映传输层（200/400/401/403/404/429/500），业务码反映业务语义。前端先看 HTTP，再看 code。

### 4.2 分页响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "list": [ ... ],
    "pagination": {
      "nextCursor": "eyJpZCI6MTIzNH0=",
      "hasMore": true,
      "total": 0
    }
  }
}
```

| 字段 | 说明 |
|---|---|
| list | 当前页数据数组 |
| pagination.nextCursor | 下一页游标，无更多数据时为 null |
| pagination.hasMore | 是否有更多数据 |
| pagination.total | 总数（可选，仅在需要时返回，如收藏列表；高频列表如职位列表不返回 total 以节省 COUNT 开销） |

> 采用游标分页而非 offset 分页：大数据量下性能稳定，避免数据漂移。

### 4.3 错误响应

```json
{
  "code": 40001,
  "message": "手机号格式不正确",
  "data": null,
  "traceId": "abc-123",
  "errors": [
    { "field": "phone", "message": "手机号格式不正确" }
  ]
}
```

| HTTP | 含义 |
|---|---|
| 400 | 请求参数错误 |
| 401 | 未登录或 token 失效 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突（如已收藏、已投递） |
| 422 | 业务校验失败（字段级） |
| 429 | 限流 |
| 500 | 服务器内部错误 |

---

## 5. 错误码体系

> 业务码与 HTTP 状态码对应，但更细粒度。错误码采用 5 位数字，前 3 位对应 HTTP 状态码。

| 码段 | HTTP | 含义 |
|---|---|---|
| `0` | 200 | 成功 |
| `40000-40099` | 400 | 通用请求错误 |
| `40100-40199` | 401 | 鉴权失败 |
| `40300-40399` | 403 | 权限不足 |
| `40400-40499` | 404 | 资源不存在 |
| `40900-40999` | 409 | 资源冲突 |
| `42200-42299` | 422 | 业务校验失败 |
| `42900-42999` | 429 | 限流 |
| `50000-50099` | 500 | 服务器错误 |

### 5.1 通用错误码

| code | message | 说明 |
|---|---|---|
| 0 | ok | 成功 |
| 40001 | 请求参数错误 | 通用 |
| 40101 | 未登录 | 缺少 Authorization |
| 40102 | token 已过期 | |
| 40103 | refresh token 无效 | |
| 40301 | 无权限访问该资源 | 跨租户/角色不符 |
| 40401 | 资源不存在 | 通用 |
| 40901 | 资源已存在 | 通用 |
| 42201 | 字段校验失败 | 配合 errors 数组 |
| 42901 | 请求过于频繁 | 限流 |
| 50001 | 服务器内部错误 | 通用 |

### 5.2 业务错误码示例

| code | message | 说明 |
|---|---|---|
| 40910 | 已收藏过该职位 | |
| 40911 | 已投递过该职位 | |
| 40912 | 会话已存在 | |
| 42210 | 简历不完整，无法投递 | |
| 42211 | 企业配额已满，无法发布职位 | |
| 40310 | 简历查看次数已用尽 | |

> 完整业务错误码由后端在开发中持续补充，前端按 `code` 做精确分支处理，按 `message` 兜底展示。

---

## 6. 限流

### 6.1 限流维度

- `userId`：单用户限制
- `companyId`：单企业限制
- `ip`：防爬虫

### 6.2 限流响应

```
HTTP/1.1 429 Too Many Requests
Retry-After: 30
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1695283200

{
  "code": 42901,
  "message": "请求过于频繁，请稍后再试",
  "data": null,
  "traceId": "..."
}
```

### 6.3 限流策略（首发）

| 接口类型 | 限制 |
|---|---|
| 公开查询（职位列表/详情） | 60 次/分钟（按 ip） |
| 求职者操作（投递/聊天） | 30 次/分钟（按 userId） |
| 招聘者操作（发布职位） | 10 次/分钟（按 companyId） |
| 登录 | 5 次/分钟（按 ip + phone） |

---

## 7. 缓存控制

### 7.1 响应头

| 接口类型 | Cache-Control | ETag |
|---|---|---|
| 静态字典（城市/分类） | `public, max-age=86400` | 是 |
| 职位列表 | `public, max-age=30` | 是 |
| 职位详情 | `public, max-age=60` | 是 |
| 用户信息 | `private, no-cache` | 是 |
| 投递/聊天 | `no-store` | 否 |

### 7.2 客户端缓存

- 前端使用 TanStack Query（或等价物）做 stale-while-revalidate。
- ETag 匹配返回 304 时，前端复用本地缓存。

---

## 8. 幂等性

### 8.1 必须幂等的操作

- 投递职位（即使重复请求，也只产生一条投递记录）
- 收藏职位
- 发起会话

### 8.2 幂等键

- 客户端可在 POST 请求头携带 `Idempotency-Key: <uuid>`，服务端按此键去重。
- 24 小时内相同 key 返回首次结果。

---

## 9. 文件上传

### 9.1 直传对象存储（推荐）

- 前端先调 `POST /api/v1/uploads/sign` 获取预签名 URL。
- 前端直传 MinIO/S3。
- 上传完成后调 `POST /api/v1/uploads/confirm` 通知后端记录。

### 9.2 限制

| 类型 | 允许 MIME | 大小 |
|---|---|---|
| 头像/Logo | image/jpeg, image/png, image/webp | ≤ 2MB |
| 简历附件 | application/pdf, application/vnd.openxmlformats... | ≤ 10MB |
| 公司图片 | image/jpeg, image/png, image/webp | ≤ 5MB |

---

## 10. WebSocket（聊天实时通信）

### 10.1 连接

- 端点：`/ws?XTransformPort=<port>`（沙箱网关规则）/ 生产环境 `/ws`
- 协议：STOMP over WebSocket
- 鉴权：连接时携带 `Authorization: Bearer <accessToken>`（通过 header 或 query）

### 10.2 订阅主题

| 主题 | 订阅者 | 说明 |
|---|---|---|
| `/user/queue/messages` | 单用户 | 接收新消息推送 |
| `/user/queue/notifications` | 单用户 | 接收系统通知 |
| `/topic/conversation/{conversationId}/typing` | 会话双方 | "对方正在输入" |

### 10.3 发送主题

| 主题 | 说明 |
|---|---|
| `/app/conversation/{conversationId}/send` | 发送消息 |
| `/app/conversation/{conversationId}/typing` | 通知正在输入 |
| `/app/conversation/{conversationId}/read` | 标记已读 |

### 10.4 消息格式

STOMP frame body 仍为 JSON，遵循统一包络的 `data` 字段结构。

### 10.5 断线重连

- 客户端实现指数退避重连。
- 重连后通过 REST 拉取断线期间的未读消息。

> 沙箱约束：前端 WebSocket 连接必须走 `/?XTransformPort=<port>`，路径恒为 `/`。

---

## 11. 国际化（预留）

- 当前 `message` 字段返回中文。
- 预留：`Accept-Language: zh-CN` / `en-US` 请求头，后端按此返回对应语言的 message。
- 错误码与 message 分离，前端可按 `code` 自行翻译。

---

## 12. 安全

### 12.1 HTTPS

- 生产环境强制 HTTPS，HSTS。

### 12.2 输入校验

- 后端 Bean Validation 强制校验所有入参。
- 字符串过滤 XSS，防 SQL 注入用参数化查询。

### 12.3 CSRF

- 鉴权用 Bearer Token（非 Cookie），天然防 CSRF。

### 12.4 CORS

- 网关白名单配置允许的前端域名。
- 预检请求 OPTIONS 由网关直接响应。

---

## 13. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v0.3 | 2026-09-21 | Step 0 初版：URL、方法、包络、错误码、限流、缓存、WS、安全 |
