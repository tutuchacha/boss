# 交付明细（state/deliverables.md）

> 由 `PROGRESS.md` §3.1 与 §3.3 迁出（2026-10-20）。用于「某功能改过哪些文件 / 诚实边界」的排查，
> 不需要开工必读。关键决策仍在 [`../../PROGRESS.md`](../../PROGRESS.md) §4。

---
### 3.1 主要文件与改动

| 文件 | 类型 | 改了什么 / 为什么 |
| --- | --- | --- |
| `src/api/mock/applications.ts` | 新增 | 投递 / 面试内存数据层：可变数组 + 懒加载种子（HR 侧 6 条覆盖全部状态枚举；求职者侧 3 条均投非云杉）、状态文案映射、双端视图组装、面试邀请消息发送 |
| `src/api/applications.ts` / `src/api/hrApplications.ts` | 新增 | 求职者 / HR 两端投递 service（重复投递 40911、不可撤回 40913、邀请校验 40914/40915、状态标记、会话按需创建） |
| `src/api/mock/candidates.ts` | 新增 | 候选人种子（6 人画像），从 `hrConversations.ts` 抽出供「沟通」「候选人」共用 |
| `src/api/mock/companyOps.ts` / `src/api/company.ts` | 新增 | 公司资料编辑（改名同步职位卡片快照）与企业认证提交（§5.6；PENDING → 约 1.2s 自动通过） |
| `src/api/mock/auth.ts` / `src/api/auth.ts` / `src/store/auth.ts` | 修改 | 验证码 store（固定 `123456`、60s 限流）、`registerMockAccount`（HR 自动建未认证公司）、`sendSmsCode` / `loginWithSms` / `register`、store 的 `loginSms` / `register` / `updateCompany` |
| `src/store/hr.ts` | 修改 | 第 4 个 Tab `'candidates'` + 5 个页面类型 + open actions + 根视图常量 |
| `src/components/pinliao/hr/`（候选人 / 面试邀约 / 公司资料 / 认证 5 个新页） | 新增 | `HrCandidatesPage` / `HrCandidateDetailPage` / `HrInterviewInvitePage` / `HrCompanyEditPage` / `HrCompanyVerifyPage` |
| `src/components/pinliao/hr/HrShell.tsx` 等 | 修改 | 四 Tab + 覆盖页渲染；`HrMePage` 入口与认证角标；`HrChatPage` 面试邀请气泡；`HrJobEditorPage` 归属上下文 |
| `src/components/pinliao/MyApplicationsPage.tsx` | 新增 | 求职者「我的投递」（进度 / 撤回 / 接受与婉拒面试） |
| `src/components/pinliao/JobDetailPage.tsx` | 修改 | 底部「发送简历」改为「投递简历」（ApplySheet、已投递态） |
| `src/components/pinliao/LoginPage.tsx` | 重写 | 密码 / 验证码 / 注册三模式 + 60s 倒计时 + 登录落地页签重置 |
| `src/api/mock/hrJobs.ts` / `src/api/hrJobs.ts` | 修改 | 新建职位归属从硬编码云杉改为按登录上下文注入 |
| `tests/e2e-verify.cjs` | 重写扩展 | 68 项断言（含人才筛选 6 项 + T-AI4 语义检索 3 项）；新增 `clickAria` / `clickSubmit` / `waitText` / `matchOverlayText` 助手；注册认证段落改轮询等待；修复聊天覆盖层未退出问题 |
| `src/api/mock/resumes.ts` | 新增 | 简历正文字段种子（7 份：6 位演示候选人 + 求职者测试账号）；简历 id 沿用 `resume-{userId}` 约定 |
| `src/api/mock/talentMatch.ts` | 新增 | 人才筛选演示引擎：标签词表 / 英文技术词 / 中文去停用词 → 关键词提取 → 检索简历 → 加权排序 + 证据片段 |
| `src/api/hrTalentMatch.ts` | 新增 | §4.5 候选人匹配 / §4.6 标签词表 service（USE_MOCK 双分支；空条件 42210） |
| `src/components/pinliao/hr/HrTalentMatchPage.tsx` | 新增 | 筛选页：需求输入 + 标签 chips + 结果卡片（命中词 / 证据高亮 / 进候选人详情）；T-AI4 起增「关键词 / 语义检索」模式切换 |
| `backend/.../ai/search/SemanticTalentSearchService.java` | 新增（T-AI4） | 语义人才检索：query+tags 一次向量化 → 公司可见投递的简历分块余弦 → 按候选人聚合最高分 + 最多 3 条证据；缺失索引按需补建、维度不一致 42221、共用配额 |
| `backend/.../ai/web/AiSearchController.java` / `ai/dto/AiSearchRequests.java` / `ai/view/AiSearchViews.java` | 新增（T-AI4） | `POST /api/v1/hr/talent/semantic-search` 的接线与出入参 |
| `src/api/hrTalentMatch.ts` / `src/api/mock/talentMatch.ts` / `src/types/api.ts` | 修改（T-AI4） | 语义检索 service（USE_MOCK 双分支）+ 独立 Query key；Mock 分支词面近似打分（非 embedding）+ `SemanticTalentMatchView` 类型 |
| 文档 | 新增 / 修改 | 根目录五件套 + `docs/AI-CONTINUE.md`（v1.6）+ `docs/api-endpoints.md`（v0.3.2，§4.5/§4.6）+ `README.md` + `worklog.md` |

---

### 3.3 边界说明（诚实口径）

- e2e 已覆盖五项功能主链路（见 §2 表）；**未覆盖边界**：注册仅测 HR 角色、认证仅测
  通过态；「标记录用 / 不合适」「婉拒面试」「撤回投递」等已实现且有 UI，但未纳入
  e2e 断言，需要时先补脚本再采信。
- 人才筛选：关键词版为**无 AI 关键词匹配**（同义词不识别、包含式命中）；T-AI4 语义版按向量余弦召回，
  不理解「必须 / 优先」等约束，`provider=fake` 与 Mock 分支都只是词面近似、不代表语义能力；
  e2e 覆盖关键词 6 项 + 语义 3 项；简历种子仅 7 份，无简历的候选人不会出现在匹配结果（候选人列表不受影响）。
- Mock 全部为内存态，刷新即重置；以上通过均指本轮提交代码的自动化验收结果。
