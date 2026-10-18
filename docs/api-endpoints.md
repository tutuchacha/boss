# 聘聊 · API 接口清单

> 版本：v0.3.2 · 最后更新：2026-10-16
> 通用规范见 `docs/api-contract.md`。本文件列出全部业务接口。
> 类型定义见 `src/types/api.ts`。
>
> ⚠️ 账目说明（2026-10 复核，含 2026-10-16 人才筛选增量）：正文现可逐条数出
> **70** 条 HTTP 接口（GET 28 / POST 33 / PATCH 6 / DELETE 3；较 68 条新增 2 条：
> §4.5 候选人匹配、§4.6 技能标签词表）。「接口总览」合计 70 与正文现值一致，
> 但**表格内各模块数字之和（67）仍不等于合计**——分模块订正因涉及既有设计资产，
> 待确认后统一更正；逐条以正文为准（另 WebSocket 为 4 个主题，非 HTTP 接口，不计入）。

---

## 0. 接口总览

按模块分组，每条接口标注：方法、路径、鉴权角色、用途。

| 模块 | 接口数 |
|---|---|
| 鉴权 | 6 |
| 用户/求职者 | 8 |
| 简历 | 6 |
| 招聘者 | 6 |
| 公司 | 6 |
| 职位 | 9 |
| 分类/字典 | 3 |
| 消息 | 8 |
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
- 入参（部分）：`{ name?, avatarUrl?, expectCat?, expectSub?, expectCity?, expectSalaryMin?, expectSalaryMax?, seekingStatus?, workYears?, edu? }`
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
- 行为：软删账号，30 天内可恢复

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
- 查询：`cursor, limit, status`
- 出参：分页 `Job[]`

### 4.4 我收到的投递
`GET /api/v1/hr/applications`
- 鉴权：HR / COMPANY_ADMIN
- 查询：`cursor, limit, status, jobId`
- 出参：分页 `ApplicationWithJob[]`

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

### 5.1 公司详情
`GET /api/v1/companies/{companyId}`
- 鉴权：无（公开）
- 出参：`Company`

### 5.2 公司发布的职位
`GET /api/v1/companies/{companyId}/jobs`
- 鉴权：无（公开）
- 查询：`cursor, limit`
- 出参：分页 `Job[]`

### 5.3 我所在公司（招聘者视角）
`GET /api/v1/company/me`
- 鉴权：HR / COMPANY_ADMIN
- 出参：`Company`

### 5.4 更新公司信息
`PATCH /api/v1/company/me`
- 鉴权：COMPANY_ADMIN
- 入参（部分）：`{ name?, logoUrl?, intro?, industry?, stage?, size?, website? }`
- 出参：`Company`

### 5.5 公司成员列表
`GET /api/v1/company/members`
- 鉴权：COMPANY_ADMIN
- 出参：`CompanyMember[]`

### 5.6 企业认证提交
`POST /api/v1/company/me/verify`
- 鉴权：COMPANY_ADMIN
- 入参：`CompanyVerifyInput` — `{ companyName, licenseNo, contactName, contactPhone }`
- 出参：`Company`（`status` 置为 `PENDING`、`verified=false`）
- 错误：`42210` 资料校验失败（名称 / 18 位统一社会信用代码 / 联系人 / 联系电话）；
  `40916` 已认证，无需重复提交
- 说明：审核进度复用 §5.3 轮询（`verified` / `status`）；Mock 阶段提交后约 1.2 秒
  自动通过（`MOCK_VERIFY_REVIEW_DELAY_MS`）

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
- 查询：`cursor, limit`
- 出参：分页 `ConversationWithLastMessage[]`
- 说明：求职者看自己所有会话，招聘者看本公司所有会话（按角色返回不同视角）

### 8.2 会话详情（消息列表）
`GET /api/v1/conversations/{conversationId}/messages`
- 鉴权：是（会话参与方或本公司 HR）
- 查询：`cursor, limit`（游标按 createdAt 降序）
- 出参：分页 `Message[]`

### 8.3 发起会话（从职位详情页"立即沟通"）
`POST /api/v1/conversations`
- 鉴权：JOB_SEEKER
- 入参：`{ jobId, firstMessage?: string }`
- 出参：`Conversation`
- 错误：40912 会话已存在（前端转跳到已有会话）
- 幂等：通过 `(jobId, userId)` 唯一约束

### 8.4 发送消息
`POST /api/v1/conversations/{conversationId}/messages`
- 鉴权：是（会话参与方）
- 入参：`{ type: "TEXT" | "RESUME" | "INTERVIEW_INVITE" | "JOB_CARD", content: string | object, idempotencyKey?: string }`
- 出参：`Message`
- 限流：30 次/分钟（按 userId）

### 8.5 标记会话已读
`POST /api/v1/conversations/{conversationId}/read`
- 鉴权：是（会话参与方）
- 出参：`{ unreadCount: 0 }`

### 8.6 "对方正在输入"通知（可选，通过 WebSocket）
`POST /api/v1/conversations/{conversationId}/typing`
- 鉴权：是（会话参与方）
- 入参：`{ isTyping: boolean }`
- 出参：`{}`
- 行为：服务端转发到对方的 `/user/queue/typing`

### 8.7 快捷操作（发送简历/约面试）
`POST /api/v1/conversations/{conversationId}/actions/send-resume`
`POST /api/v1/conversations/{conversationId}/actions/invite-interview`
- 鉴权：HR（邀请面试）/ JOB_SEEKER（发送简历）
- 入参（邀请面试）：`{ scheduledAt, format, location?, onlineUrl?, note? }`
- 出参：`Message`
- 行为：发送对应类型的消息，并触发后续业务（创建 Interview 记录）
- 错误（邀请面试）：40914 投递已拒绝 / 已撤回；40915 已有待确认邀请；42201 形式 / 地点非法；42210 时间早于当前

### 8.8 关闭会话（HR 可关闭）
`POST /api/v1/conversations/{conversationId}/close`
- 鉴权：HR / COMPANY_ADMIN（本公司）
- 出参：`Conversation`

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
- 行为：后端校验对象已存在，记录到对应业务表

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

## 14. 修订记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v0.3 | 2026-09-21 | Step 0 初版：13 个模块共 68 个接口 |
| — | 2026-09-28 | 加注账目说明：总览表格合计 68 与正文实际 67 条不符，正文为准 |
| v0.3.1 | 2026-10-16 | 新增 §5.6 企业认证提交（POST /company/me/verify；42210 / 40916）；公司模块行 5→6；账目说明更新为 2026-10 复核（正文 67→68，合计仍 68，分模块数字 65 待订正） |
| v0.3.2 | 2026-10-16 | 新增 §4.5 候选人匹配、§4.6 技能标签词表（人才筛选演示版，关键词匹配；预留语义检索升级）；招聘者模块行 4→6；账目：正文 68→70（GET 27→28 / POST 32→33），合计 70，分模块之和 67 仍待订正 |
