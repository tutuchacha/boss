# 聘聊 · API 接口清单

> 版本：v0.3.9 · 最后更新：2026-10-20
> 通用规范见 `docs/api-contract.md`。本文件列出全部业务接口。
> 类型定义见 `src/types/api.ts`。
>
> 账目说明（2026-10-18 定稿）：正文逐条 **70** 条 HTTP 接口（GET 28 / POST 33 / PATCH 6 /
> DELETE 3）。「接口总览」已与正文对齐：鉴权 7（§1.1~§1.7）、职位 10（§6.7 含
> close/reopen 两条）、消息 9（§8.7 含 send-resume / invite-interview 两条），其余模块
> 一节一条；分模块合计 70。WebSocket 4 主题不计入 HTTP 接口。

> T6 另实现契约外读取 `GET /api/v1/conversations/{conversationId}`（HR 聊天顶栏），
> 仅作扩展、不改变 8 条消息接口的计账。
>
> T8 另实现契约外读取 `GET /api/v1/hr/jobs/{jobId}`（HR 职位编辑页需打开已关闭职位，
> 公开详情 §6.3 只认 `OPEN`），同样仅作扩展、不改变 6 条招聘者接口的计账。
>
> T-AI0 另实现契约外自检 `GET /api/v1/ai/status`（只回显配置）与 `GET /api/v1/ai/ping`
> （真实调用一次 chat + embedding），**需登录**、不计账、不改变任何模块的接口条数。
> Step 4 正式 AI 接口（简历问答 / 索引状态 / 重建 / 语义检索）将在 T-AI1~T-AI4 落地时
> 新增独立的「§AI」模块并计入账目（方案见 `step4-ai-plan.md` §6.3）。
> **T-AI1 / T-AI2 已落地 4 条契约外接口**（重建、索引状态、单简历问答，连同既有 status/ping
> 共 5 条）：当前仍不计入 70 条账目，待用户确认是否并入正式契约（见 §14）。
>
> **T8 出参口径（2026-10-18 用户拍板）**：§4.3 / §4.4 直接返回前端 service 使用的
> **超集**结构（`HrJobRecord[]` / `HrApplicationView[]`），而非契约文字的 `Job[]` /
> `ApplicationWithJob[]`；目的是让 T9 切换真实后端时前端真实分支零改动。

---

## 0. 接口总览

按模块分组，每条接口标注：方法、路径、鉴权角色、用途。

| 模块 | 接口数 |
|---|---|
| 鉴权 | 7 |
| 用户/求职者 | 8 |
| 简历 | 6 |
| 招聘者 | 6 |
| 公司 | 6 |
| 职位 | 10 |
| 分类/字典 | 3 |
| 消息 | 9 |
| 投递 | 5 |
| 面试 | 5 |
| 收藏 | 3 |
| 文件上传 | 2 |
| WebSocket | 4（主题） |
| **合计** | 70 |

---

## 1. 鉴权模块

### 1.1 发送验证码
`POST /api/v1/auth/sms/code`
- 鉴权：无
- 入参：`{ phone: string, scene: "LOGIN" | "REGISTER" | "RESET" }`
- 出参：`{ sentAt: ISO8601, nextRetryIn: number }`
- 限流：60 秒内同手机号 1 次，10 分钟内 5 次

### 1.2 手机号注册
`POST /api/v1/auth/register`
- 鉴权：无
- 入参：`{ phone, code, password, role: "JOB_SEEKER" | "HR" }`
- 出参：`{ accessToken, refreshToken, user: UserProfile }`
- 错误：40910 手机号已注册；40110 验证码错误

### 1.3 手机号登录（验证码）
`POST /api/v1/auth/login/sms`
- 鉴权：无
- 入参：`{ phone, code }`
- 出参：`{ accessToken, refreshToken, user }`

### 1.4 密码登录
`POST /api/v1/auth/login/password`
- 鉴权：无
- 入参：`{ account: string, password: string }`（account 可为手机号或邮箱）
- 出参：`{ accessToken, refreshToken, user }`
- 错误：40110 账号或密码错误；40111 账号已禁用

### 1.5 刷新 token
`POST /api/v1/auth/refresh`
- 鉴权：无（用 refreshToken）
- 入参：`{ refreshToken }`
- 出参：`{ accessToken, refreshToken }`
- 错误：40103 refreshToken 无效

### 1.6 退出登录
`POST /api/v1/auth/logout`
- 鉴权：是
- 入参：`{ refreshToken }`
- 出参：`{}`

### 1.7 当前用户信息
`GET /api/v1/auth/me`
- 鉴权：是
- 出参：`{ user: User, profile: UserProfile | HrProfile | null }`

---

## 2. 用户/求职者模块

### 2.1 获取求职者画像
`GET /api/v1/user/profile`
- 鉴权：JOB_SEEKER
- 出参：`UserProfile`

### 2.2 更新求职者画像
`PATCH /api/v1/user/profile`
- 鉴权：JOB_SEEKER
- 入参（部分）：`{ name?, avatarUrl?, gender?, birthDate?, expectCat?, expectSub?, expectCity?, expectSalaryMin?, expectSalaryMax?, seekingStatus?, workYears?, edu? }`
- 出参：`UserProfile`

### 2.3 修改密码
`POST /api/v1/user/password/change`
- 鉴权：是
- 入参：`{ oldPassword, newPassword }`
- 出参：`{}`

### 2.4 重置密码
`POST /api/v1/user/password/reset`
- 鉴权：无
- 入参：`{ phone, code, newPassword }`
- 出参：`{}`

### 2.5 修改手机号（验证新号）
`POST /api/v1/user/phone/change`
- 鉴权：是
- 入参：`{ newPhone, code }`
- 出参：`{}`

### 2.6 获取外观偏好
`GET /api/v1/user/preferences`
- 鉴权：是
- 出参：`{ theme: "auto" | "light" | "dark", ... }`

### 2.7 更新外观偏好
`PATCH /api/v1/user/preferences`
- 鉴权：是
- 入参：`{ theme?, ... }`
- 出参：`{ theme, ... }`

> 外观偏好可仅前端 localStorage 存储，无需后端；此接口为多端同步预留。

### 2.8 注销账号
`POST /api/v1/user/deactivate`
- 鉴权：是
- 入参：`{ reason?: string }`
- 出参：`{}`
- 行为：软删账号并立即吊销当前 accessToken；T7 演示版未提供恢复接口（30 天恢复需运维 / 后续增量）

---

## 3. 简历模块

### 3.1 简历列表
`GET /api/v1/resumes`
- 鉴权：JOB_SEEKER
- 出参：`Resume[]`

### 3.2 简历详情
`GET /api/v1/resumes/{resumeId}`
- 鉴权：JOB_SEEKER（本人） / HR（仅在被投递的公司可见）
- 出参：`Resume`

### 3.3 创建简历
`POST /api/v1/resumes`
- 鉴权：JOB_SEEKER
- 入参：`{ name, content: ResumeContent, attachmentUrl? }`
- 出参：`Resume`

### 3.4 更新简历
`PATCH /api/v1/resumes/{resumeId}`
- 鉴权：JOB_SEEKER（本人）
- 入参（部分）：`{ name?, content?, attachmentUrl?, isDefault? }`
- 出参：`Resume`

### 3.5 删除简历
`DELETE /api/v1/resumes/{resumeId}`
- 鉴权：JOB_SEEKER（本人）
- 出参：`{}`
- 行为：软删；若删的是默认简历，自动把最近更新的剩余简历设为默认

### 3.6 设为默认简历
`POST /api/v1/resumes/{resumeId}/set-default`
- 鉴权：JOB_SEEKER（本人）
- 出参：`Resume`

---

## 4. 招聘者模块

### 4.1 获取招聘者画像
`GET /api/v1/hr/profile`
- 鉴权：HR / COMPANY_ADMIN
- 出参：`HrProfile`

### 4.2 更新招聘者画像
`PATCH /api/v1/hr/profile`
- 鉴权：HR / COMPANY_ADMIN
- 入参：`{ name?, title?, avatarUrl?, intro? }`
- 出参：`HrProfile`

### 4.3 我发布的职位
`GET /api/v1/hr/jobs`
- 鉴权：HR / COMPANY_ADMIN
- 查询：`status?, q?, hrId?, limit?, cursor?`
  （`q` 标题模糊、`hrId` 只看某招聘官，为契约外扩展参数；`cursor` 契约保留但演示规模未启用游标分页）
- 出参：`HrJobRecord[]`（契约超集：`JobSummary` + `status` + `responsibilities` /
  `requirements` / `welfare` + `deleted` + `companyId`；含已关闭职位，`limit` 作 1~100 上限保护）
- 错误：`42201` 状态筛选值非法

`GET /api/v1/hr/jobs/{jobId}`（契约外扩展，配合 HR 编辑页）
- 鉴权：HR / COMPANY_ADMIN（仅本公司，越权 / 不存在 / 已软删统一 `40410`）
- 出参：`HrJobRecord`（可读取已关闭职位；公开详情 §6.3 只认 `OPEN`）

### 4.4 我收到的投递
`GET /api/v1/hr/applications`
- 鉴权：HR / COMPANY_ADMIN（公司维度，`companyId` 取自登录上下文）
- 查询：`status?, jobId?, q?, limit?, cursor?`
  （`q` 按「候选人姓名 + 职位名称」模糊，为契约外扩展参数；`cursor` 同上保留未启用）
- 出参：`HrApplicationView[]`（契约超集：`application` + `candidate` 摘要 + 职位摘要
  + `conversationId` + 最近面试；更新倒序；`limit` 作 1~100 上限保护）
- 错误：`42201` 状态筛选值非法

### 4.5 候选人匹配（按技能需求筛选）
`POST /api/v1/hr/candidates/match`
- 鉴权：HR / COMPANY_ADMIN
- 入参：`CandidateMatchInput` — `{ query?: string, tags?: string[] }`
  （query 为自然语言技能需求描述；tags 为技能标签；两者至少一个非空）
- 出参：`CandidateMatchView[]`（按匹配度倒序；仅包含有命中的候选人，
  附命中关键词 `hitTokens` 与关联证据片段 `evidences`，供前端高亮展示）
- 错误：`42210` 筛选条件为空
- 说明：演示版为**关键词匹配**（标签 + 需求文本提取关键词后，在公司收到的
  投递候选人范围内检索其在线简历）；后续可升级为语义检索
  （embedding + 向量库），路径与出参结构不变

### 4.6 技能标签词表
`GET /api/v1/hr/candidates/match/tags`
- 鉴权：HR / COMPANY_ADMIN
- 出参：`string[]`（供筛选页标签 chips 展示）

---

## 5. 公司模块

> **权限口径**：§5.4~§5.6 的 `COMPANY_ADMIN` 指 `company_members.role=ADMIN`（用户角色为 HR 的
> 演示账号 `13800000002` 成员角色是 ADMIN，同样满足）；`companyId` 一律取自登录上下文，不信任请求体。
> §5.1 / §5.2 为公开读（匿名可访问，带 token 仍解析身份）。

### 5.1 公司详情
`GET /api/v1/companies/{companyId}`
- 鉴权：无（公开）
- 出参：`Company`
- 错误：`40410` 公司不存在

### 5.2 公司发布的职位
`GET /api/v1/companies/{companyId}/jobs`
- 鉴权：无（公开）
- 查询：`cursor, limit`
- 出参：分页 `JobSummary[]`
- 说明：仅返回 `OPEN` 且未软删的职位，游标语义同 §6.1
- 错误：`40410` 公司不存在

### 5.3 我所在公司（招聘者视角）
`GET /api/v1/company/me`
- 鉴权：HR / COMPANY_ADMIN
- 出参：`Company`
- 错误：`40301` 非招聘者 / 无企业上下文；`40410` 公司不存在

### 5.4 更新公司信息
`PATCH /api/v1/company/me`
- 鉴权：COMPANY_ADMIN（成员角色 ADMIN）
- 入参（部分）：`{ name?, logoUrl?, intro?, industry?, stage?, size?, website? }`
- 出参：`Company`
- 错误：`40301` 非公司管理员；`42210` 资料校验失败（名称 2-30 字 / 简介 ≤500 字符等）
- 说明：职位视图的公司名从 `companies` 实时装配，改名后 §5.1 / §5.2 / §6.x 立即同步

### 5.5 公司成员列表
`GET /api/v1/company/members`
- 鉴权：COMPANY_ADMIN（成员角色 ADMIN）
- 出参：`CompanyMember[]`
- 错误：`40301` 非公司管理员

### 5.6 企业认证提交
`POST /api/v1/company/me/verify`
- 鉴权：COMPANY_ADMIN（成员角色 ADMIN）
- 入参：`CompanyVerifyInput` — `{ companyName, licenseNo, contactName, contactPhone }`
- 出参：`Company`（`status` 置为 `PENDING`、`verified=false`）
- 错误：`42210` 资料校验失败（名称 / 18 位统一社会信用代码 / 联系人 / 联系电话）；
  `40916` 已认证，无需重复提交
- 说明：审核进度复用 §5.3 轮询（`verified` / `status`）；后端提交后约 1.2 秒
  自动通过（可配置 `pinliao.company.verify-delay-ms`；Mock 为 `MOCK_VERIFY_REVIEW_DELAY_MS`）

---

## 6. 职位模块

### 6.1 职位列表（搜索 + 筛选）
`GET /api/v1/jobs`
- 鉴权：无（公开浏览）
- 查询参数：
  - `q` 关键词
  - `city` 城市（"全国"或不传表示不限）
  - `cat` 一级分类
  - `sub` 二级岗位
  - `exp` 经验
  - `sal` 薪资区间（"不限" / "10K以下" / "10-20K" / "20-40K" / "40K以上"）
  - `sort` 排序（"recommend" 默认推荐 / "latest" 最新）
  - `cursor, limit`
- 出参：分页 `JobSummary[]`
- 缓存：`Cache-Control: public, max-age=30`

> 求职者登录后 `sort=recommend` 会结合用户期望岗位加权。

### 6.2 职位搜索（独立路径，便于后期切到 ES）
`GET /api/v1/search/jobs`
- 鉴权：无
- 查询：同 6.1
- 出参：分页 `JobSummary[]`
- 说明：首发与 6.1 共享实现；后期独立 ES 集群时此路径切走，前端无感

### 6.3 职位详情
`GET /api/v1/jobs/{jobId}`
- 鉴权：无（公开）
- 出参：`JobDetail`（含公司信息、HR 信息、相似职位）
- 缓存：`Cache-Control: public, max-age=60`，支持 ETag

### 6.4 相似职位
`GET /api/v1/jobs/{jobId}/similar`
- 鉴权：无
- 查询：`limit`
- 出参：`JobSummary[]`

### 6.5 发布职位
`POST /api/v1/jobs`
- 鉴权：HR / COMPANY_ADMIN
- 入参：`{ title, salaryMin, salaryMax, city, area, exp, edu, cat, sub, tags: string[], responsibilities, requirements, welfare: string[] }`
- 出参：`Job`
- 限流：10 次/分钟（按 companyId）
- 错误：42211 企业配额已满

### 6.6 更新职位
`PATCH /api/v1/jobs/{jobId}`
- 鉴权：HR / COMPANY_ADMIN（本公司）
- 入参：同 6.5 部分字段
- 出参：`Job`

### 6.7 关闭/重新开放职位
`POST /api/v1/jobs/{jobId}/close`
`POST /api/v1/jobs/{jobId}/reopen`
- 鉴权：HR / COMPANY_ADMIN（本公司）
- 出参：`Job`

### 6.8 删除职位（软删）
`DELETE /api/v1/jobs/{jobId}`
- 鉴权：HR / COMPANY_ADMIN（本公司）
- 出参：`{}`

### 6.9 浏览职位时记录浏览
`POST /api/v1/jobs/{jobId}/view`
- 鉴权：无
- 出参：`{}`
- 行为：累加 viewCount，用于热门排序（防重复由 ip + jobId + 时间窗口去重）

---

## 7. 分类/字典模块

### 7.1 分类树
`GET /api/v1/categories`
- 鉴权：无
- 出参：`CategoryNode[]`（树形结构）
- 缓存：`Cache-Control: public, max-age=86400`

### 7.2 城市列表
`GET /api/v1/cities`
- 鉴权：无
- 出参：`City[]`
- 缓存：`Cache-Control: public, max-age=86400`

### 7.3 字典聚合（经验/学历/薪资区间/福利等枚举）
`GET /api/v1/dict`
- 鉴权：无
- 出参：`{ exps: string[], edus: string[], salaryRanges: string[], welfare: string[] }`
- 缓存：`Cache-Control: public, max-age=86400`

---

## 8. 消息模块

### 8.1 会话列表
`GET /api/v1/conversations`
- 鉴权：是
- 查询：`cursor, limit`（默认 20 / 上限 100）
- 出参：分页 `ConversationWithLastMessage[]`
- 说明：求职者看自己所有会话，招聘者看本公司所有会话（按角色返回不同视角）。
  列表项为双端超集，含 `conversation / job / hr / company / candidate / lastMessage`；
  按 `lastMessageAt DESC, id ASC` 排序，未读字段区分 `userUnreadCount` / `hrUnreadCount`。

### 8.2 会话详情（消息列表）
`GET /api/v1/conversations/{conversationId}/messages`
- 鉴权：是（会话参与方或本公司 HR）
- 查询：`cursor, limit`（游标按 createdAt 降序）
- 出参：分页 `Message[]`（`{ list, pagination }`，`createdAt DESC, id ASC`）
- 说明：`readByUser` / `readByHr` 标识双端已读；不存在 / 非参与方 / 跨公司统一 40410。

### 8.2a 会话详情（扩展读取，不计入 8 条）
`GET /api/v1/conversations/{conversationId}`
- 鉴权：是（会话参与方或本公司 HR）
- 出参：单个 `ConversationWithLastMessage`（与 §8.1 列表项同形）
- 用途：HR 聊天页顶栏读取候选人 / 职位 / 公司；不存在或越权 40410。
- 说明：契约外增量，T9 前端可选用；不属于新增 HTTP 账目。

### 8.3 发起会话（从职位详情页"立即沟通"）
`POST /api/v1/conversations`
- 鉴权：JOB_SEEKER
- 入参：`{ jobId, firstMessage?: string }`
- 出参：`Conversation`
- 错误：40912 会话已存在（前端转跳到已有会话）；40410 职位不存在或下线；42201 字段非法
- 幂等：通过 `(jobId, userId)` 唯一约束
- 行为：`firstMessage` 存在时写入一条 SYSTEM 提示 + 一条 USER TEXT，HR 未读 +1，并触发演示自动回复

### 8.4 发送消息
`POST /api/v1/conversations/{conversationId}/messages`
- 鉴权：是（会话参与方）
- 入参：`{ type: "TEXT" | "RESUME" | "INTERVIEW_INVITE" | "JOB_CARD", content: string | object, idempotencyKey?: string }`
- 出参：`Message`
- 限流：30 次/分钟（按 userId），超限 42901
- 幂等：同一用户 / 会话 / `idempotencyKey` 24h 内返回首次 `Message`；重复内容不会被二次写入
- 校验：TEXT 内容必须为非空字符串且 ≤2000 字；结构化消息 ≤10000 字；非法类型 / 内容 42201
- 行为：TEXT 消息由服务端演示语义延迟自动回复（默认 1.2s，见 `pinliao.message.auto-reply-delay-ms`）

### 8.5 标记会话已读
`POST /api/v1/conversations/{conversationId}/read`
- 鉴权：是（会话参与方）
- 出参：`{ unreadCount: 0 }`
- 行为：清空当前角色未读计数，并回写对方消息的 `readByUser` / `readByHr`；不存在 / 越权 40410

### 8.6 "对方正在输入"通知（可选，通过 WebSocket）
`POST /api/v1/conversations/{conversationId}/typing`
- 鉴权：是（会话参与方）
- 入参：`{ isTyping: boolean }`
- 出参：`{}`
- 行为：服务端同时转发到对方的 `/user/queue/typing` 与会话主题
  `/topic/conversation/{conversationId}/typing`

### 8.7 快捷操作（发送简历/约面试）
`POST /api/v1/conversations/{conversationId}/actions/send-resume`
`POST /api/v1/conversations/{conversationId}/actions/invite-interview`
- 鉴权：HR（邀请面试）/ JOB_SEEKER（发送简历）
- 入参（发送简历）：`{ resumeId?: string }`（省略时使用默认简历 / 第一份可用简历）
- 入参（邀请面试）：`{ scheduledAt, format, location?, onlineUrl?, note? }`
- 出参：`Message`
- 行为：发送对应类型的消息，并触发后续业务（创建 Interview 记录）
- 错误（发送简历）：42210 无可用简历 / 简历不属于本人；40410 会话越权
- 错误（邀请面试）：40914 投递已拒绝 / 已撤回；40915 已有待确认邀请；42201 形式 / 地点非法；42210 时间早于当前

### 8.8 关闭会话（HR 可关闭）
`POST /api/v1/conversations/{conversationId}/close`
- 鉴权：HR / COMPANY_ADMIN（本公司）
- 出参：`Conversation`
- 行为：置 `closedByHr=true`（幂等）；求职者或跨公司 HR 40301 / 40410

> T6 实现说明：未读计数以 `conversations.user_unread_count` / `hr_unread_count` 为持久化事实源；
> Redis 只用于发送限流与 `idempotencyKey` 缓存。演示种子 HR 未读 4 / 求职者未读 2 不应被冒烟改写。

---

## 9. 投递模块

### 9.1 投递职位
`POST /api/v1/applications`
- 鉴权：JOB_SEEKER
- 入参：`{ jobId, resumeId, coverLetter?: string }`
- 出参：`Application`
- 错误：40911 已投递；42210 简历不完整
- 幂等：`(userId, jobId)` 唯一
- 实现备注（T4）：撤回后重新投递复用原记录并回到 `SUBMITTED`；投递成功即按 `(jobId, userId)`
  幂等占位创建会话（`conv-new-*`），供 §8 消息 / §8.7 约面试使用

### 9.2 我的投递列表
`GET /api/v1/applications`
- 鉴权：JOB_SEEKER
- 查询：`cursor, limit, status`
- 出参：分页 `ApplicationWithJob[]`

### 9.3 投递详情
`GET /api/v1/applications/{applicationId}`
- 鉴权：JOB_SEEKER（本人） / HR（本公司）
- 出参：`ApplicationDetail`
- 实现备注（T4）：详情额外返回 `conversationId`（投递时占位创建的会话 id，契约外增量字段），
  用于 HR 触达 / 约面试；不存在 / 非本人 / 非本公司统一 40410

### 9.4 撤回投递
`POST /api/v1/applications/{applicationId}/withdraw`
- 鉴权：JOB_SEEKER（本人）
- 出参：`Application`
- 错误：40913 当前状态不可撤回（仅 SUBMITTED / VIEWED / INTERVIEW 可撤回）

### 9.5 更新投递状态（HR 操作）
`POST /api/v1/applications/{applicationId}/status`
- 鉴权：HR / COMPANY_ADMIN（本公司）
- 入参：`{ status: "VIEWED" | "INTERVIEW" | "OFFER" | "REJECTED", note?: string }`
- 出参：`Application`
- 错误：42201 状态非法；40913 候选已撤回；40410 不存在 / 非本公司

---

## 10. 面试模块

### 10.1 面试邀请列表（求职者视角）
`GET /api/v1/interviews`
- 鉴权：JOB_SEEKER
- 查询：`cursor, limit, status`
- 出参：分页 `InterviewWithJob[]`

### 10.2 面试邀请列表（招聘者视角）
`GET /api/v1/hr/interviews`
- 鉴权：HR / COMPANY_ADMIN
- 查询：`cursor, limit, status`
- 出参：分页 `InterviewWithCandidate[]`

### 10.3 面试详情
`GET /api/v1/interviews/{interviewId}`
- 鉴权：参与方
- 出参：`Interview`

### 10.4 接受面试
`POST /api/v1/interviews/{interviewId}/accept`
- 鉴权：JOB_SEEKER
- 出参：`Interview`
- 错误：40913 非 PENDING（已接受幂等返回）；40410 不存在 / 非本人

### 10.5 拒绝面试
`POST /api/v1/interviews/{interviewId}/decline`
- 鉴权：JOB_SEEKER
- 入参：`{ reason?: string }`
- 出参：`Interview`
- 错误：40913 非 PENDING（已拒绝幂等返回）；40410 不存在 / 非本人

---

## 11. 收藏模块

### 11.1 收藏职位
`POST /api/v1/favorites`
- 鉴权：JOB_SEEKER
- 入参：`{ jobId }`
- 出参：`Favorite`
- 错误：40910 已收藏
- 幂等：`(userId, jobId)` 唯一

### 11.2 取消收藏
`DELETE /api/v1/favorites/{jobId}`
- 鉴权：JOB_SEEKER
- 出参：`{}`
- 幂等：未收藏也返回成功

### 11.3 我的收藏列表
`GET /api/v1/favorites`
- 鉴权：JOB_SEEKER
- 查询：`cursor, limit`
- 出参：分页 `FavoriteWithJob[]`（含 total）

---

## 12. 文件上传

### 12.1 获取预签名上传 URL
`POST /api/v1/uploads/sign`
- 鉴权：是
- 入参：`{ type: "avatar" | "logo" | "resume" | "company", contentType, ext }`
- 出参：`{ uploadUrl, objectKey, expiresAt }`

### 12.2 确认上传完成
`POST /api/v1/uploads/confirm`
- 鉴权：是
- 入参：`{ objectKey }`
- 出参：`{ url, contentType, size }`
- 行为：后端校验对象已存在 / 归属 / 大小；业务关联由客户端用返回的 URL 调后续 PATCH（T7 本地版）

> T7 演示实现（2026-10-18）：对象存储替换为本地目录，`uploadUrl` 指向
> `PUT /api/v1/uploads/local/{objectKey}?expires=&sig=`（HMAC + 默认 10 分钟）；确认只校验对象
> 存在 / 归属 / 大小并返回 `/uploads/{objectKey}`，不新增上传元数据表。因 confirm 入参没有业务
> 实体 id，头像 / 简历附件等关联由客户端随后调用 `PATCH /user/profile`、`PATCH /resumes/{id}` 等完成。

---

## 13. WebSocket 主题

> 详见 `docs/api-contract.md` §10

### 13.1 订阅主题

| 主题 | 订阅者 |
|---|---|
| `/user/queue/messages` | 单用户 |
| `/user/queue/notifications` | 单用户 |
| `/user/queue/typing` | 单用户 |
| `/topic/conversation/{conversationId}/presence` | 会话双方 |

### 13.2 发送主题

| 主题 | 说明 |
|---|---|
| `/app/conversation/{conversationId}/send` | 发送消息 |
| `/app/conversation/{conversationId}/typing` | 通知正在输入 |
| `/app/conversation/{conversationId}/read` | 标记已读 |
| `/app/conversation/{conversationId}/presence` | 在线状态 |

---

## 14. AI 模块（契约外扩展，不计入 70 条账目）

> 方案见 `docs/step4-ai-plan.md`；错误码 `42220`（AI 未启用）/ `42221`（索引未就绪）/
> `42902`（问答配额超限）/ `50020`（上游失败）。所有接口需登录；下标 `{applicationId}`
> 必须先通过 `applications.company_id` 校验，跨公司 / 不存在统一 `404 + 40401`。
> 密钥、模型与开关全部走用户级环境变量 `PINLIAO_AI_*`（仓库零命中）。

### 14.1 AI 状态
`GET /api/v1/ai/status`
- 鉴权：是
- 出参：`{ enabled, provider, chatModel, embeddingModel }`
- 行为：只回显配置，不调用上游；前端据此决定「AI 助手」入口显隐

### 14.2 连通性自检
`GET /api/v1/ai/ping`
- 鉴权：是
- 出参：`{ chatModel, answer, chatLatencyMs, totalTokens, embeddingModel, embeddingDim, embeddingLatencyMs }`
- 行为：真实调用一次 chat + embedding，会消耗少量额度

### 14.3 重建简历索引
`POST /api/v1/ai/index/rebuild`
- 鉴权：HR / 管理员
- 入参：`{}`（全量）或 `{ resumeId: string }`（单份）
- 出参：`{ scope: "all" | "resume", resumes, chunks, embedded, reused, skipped, elapsedMs }`
- 行为：按 `content_hash + model` 复用旧向量；同一内容二次重建 `embedded=0`

### 14.4 投递简历索引状态
`GET /api/v1/hr/applications/{applicationId}/ai/index-status`
- 鉴权：HR / 管理员
- 出参：`{ resumeId, indexed, chunkCount, model, dim, updatedAt }`
- 行为：按投递校验可见性；索引缺失时 `indexed=false`（不报错）

### 14.5 单简历问答（带引用）
`POST /api/v1/hr/applications/{applicationId}/ai/qa`
- 鉴权：HR / 管理员
- 入参：`{ question: string }`（去空格后 1~500 字，否则 `422 + 42201`）
- 出参：`{ answer, grounded, citations[{ index, chunkId, resumeId, section, title, snippet, score }],
  chatModel, elapsedMs, totalTokens, retrieved }`
- 行为：向量检索 topK → 低于 `qa-min-score` 直接回答「简历中没有相关信息。」且 `grounded=false`、
  `citations=[]`，不调用对话模型；回答中的 `[n]` 与 `citations[].index` 一一对应
- 配额：按 HR / 按日限制（默认 100 次，`42902`；Redis 故障降级放行）
- 审计：只记录问题长度 / 命中数 / 引用数 / 回答长度 / token / 耗时，不记录简历原文与完整 Prompt

---

## 15. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v0.3 | 2026-09-21 | Step 0 初版：13 个模块共 68 个接口 |
| — | 2026-09-28 | 加注账目说明：总览表格合计 68 与正文实际 67 条不符，正文为准 |
| v0.3.1 | 2026-10-16 | 新增 §5.6 企业认证提交（POST /company/me/verify；42210 / 40916）；公司模块行 5→6；账目说明更新为 2026-10 复核（正文 67→68，合计仍 68，分模块数字 65 待订正） |
| v0.3.2 | 2026-10-16 | 新增 §4.5 候选人匹配、§4.6 技能标签词表（人才筛选演示版，关键词匹配；预留语义检索升级）；招聘者模块行 4→6；账目：正文 68→70（GET 27→28 / POST 32→33），合计 70，分模块之和 67 仍待订正 |
| v0.3.3 | 2026-10-18 | Step 3 T5 落地补注：§5.4~§5.6 的 COMPANY_ADMIN 指 `company_members.role=ADMIN`；§5.1/§5.2 补 `40410`，§5.2 明确仅 OPEN 且出参 `JobSummary[]`；§5.4 补 42210 / 改名实时同步；§5.6 补后端 1.2s 自动通过配置项 |
| v0.3.4 | 2026-10-18 | Step 3 T6 落地补注：§8 八条消息接口的游标 / 双端超集 / 40912 / 42901 / 幂等 / 自动回复 / 已读 / typing 双通道 / 发送简历；新增 §8.2a 契约外 `GET /conversations/{id}`（不计账）；补未读持久化口径 |
| v0.3.5 | 2026-10-18 | Step 3 T7 落地补注：§2.2 补 `gender?` / `birthDate?`，§2.8 软删 + 吊销 token（暂无恢复接口），§3.5 删除默认自动顺延，§11.2 取消幂等；§12.2 本地预签名直传（HMAC / 10 分钟 / `/uploads/**`）、confirm 不写业务表、关联由后续 PATCH 完成 |
| v0.3.6 | 2026-10-18 | 接口账目定稿（用户拍板）：正文 70 条为准；总览表鉴权 6→7、职位 9→10（§6.7 close/reopen）、消息 8→9（§8.7 send-resume / invite-interview），分模块合计 70 |
| v0.3.7 | 2026-10-19 | T9 / T9 追加落地补注（不改账目）：实测真实后端下前端 `npm run e2e` 63/63；求职者职位列表 / 详情切真实（`GET /jobs`，`limit=100`） |
| v0.3.8 | 2026-10-19 | Step 4 T-AI0：登记两个**契约外** AI 自检接口 `GET /api/v1/ai/status` / `GET /api/v1/ai/ping`（需登录、不计账）；正式 AI 接口（问答 / 索引 / 语义检索）留待 T-AI1~T-AI4 新增 §AI 模块时计入账目；错误码 `42220/42221/42902/50020`（**原草案 `43xxx` 作废**，契约 §5 前 3 位须为 HTTP 状态） |
| v0.3.9 | 2026-10-20 | Step 4 T-AI1 / T-AI2：新增 §14「AI 模块（契约外扩展）」共 5 条（status / ping / rebuild / index-status / qa），仍不计入 70 条账目；qa 明确入参 1~500 字、无依据固定话术、`[n]` 与 citations 对应、按 HR/日配额 42902 |
