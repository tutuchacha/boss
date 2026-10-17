# 聘聊 · 变更记录（CHANGELOG.md）

> 记录**重要代码与文档变更**，含未提交的工作区增量。
> 格式约定：版本（或「未提交」）→ Added / Changed / Fixed / Docs 分组；
> 新条目追加在顶部（最新在上）。提交历史另见 `git log`。

---

## [Step 3 规划] 2026-10-17 — 后端分块执行计划与上下文交接协议（纯文档）

> 提交 hash 见 `git log -1`。回答「Step 3 怎么分块做、上下文满了怎么接力」；
> 计划与任务板的唯一出处为 `docs/step3-plan.md`。

### Docs

- 新增 `docs/step3-plan.md` — T0~T9 分块任务板（含每块规格、演示语义对齐清单、
  上下文交接协议：开工/收工 checklist、应急流程、用户开场白模板、会话预算）；T10+ 增量另列。
- `PROGRESS.md` v1.5 — 快照新增「Step 3 计划」行、§1/§4 指向分块计划。
- `AGENTS.md` — 速览同步「Step 3 计划已就绪」。
- `worklog.md` — Task 16。

---

## [af6197e] 2026-10-16 — HR 人才筛选（演示版）：关键词匹配 + 技能标签 + 命中证据

> 已提交为 `af6197e`（基线 `a7c1fbd`）。方向确认：先做**无 AI 演示版**（关键词匹配）；范围限「站内投递候选人」；输入支持
> 自然语言描述 + 技能标签；语义检索（embedding）升级时保持契约与页面不变；HR 上传外部
> 简历库为后续增量。契约新增 §4.5 / §4.6（正文 68 → 70）；`npm run e2e` 扩展至
> **63/63** 通过（新增「HR 人才筛选」6 项断言）。

### Added

- `src/api/mock/resumes.ts` — 简历正文种子（`ResumeContent` 形状，7 份：6 位演示候选人 +
  求职者测试账号）；简历 id 沿用 `resume-{userId}` 约定（无简历的候选人不会出现在匹配结果）。
- `src/api/mock/talentMatch.ts` — 人才筛选演示引擎：标签词表 / 英文技术词 / 中文分词
  去停用词 → 关键词提取 → 检索简历 → 技能项加权排序 + 证据片段截取。
- `src/api/hrTalentMatch.ts` — service：§4.5 `POST /hr/candidates/match`、
  §4.6 `GET /hr/candidates/match/tags`（USE_MOCK 双分支；空筛选条件 42210）。
- `src/components/pinliao/hr/HrTalentMatchPage.tsx` — 筛选页：需求输入 + 标签 chips +
  结果卡片（命中关键词 / 证据片段原文高亮 / 进候选人详情）。

### Changed

- `src/types/api.ts` — 契约类型 `CandidateMatchInput` / `CandidateMatchEvidence` / `CandidateMatchView`。
- `src/store/hr.ts` / `src/components/pinliao/hr/HrShell.tsx` — 页面类型 `'talent-match'`
  与覆盖层接入；`HrCandidatesPage` 新增「按技能筛选」入口。
- `tests/e2e-verify.cjs` — 新增「HR 人才筛选」6 项；注册与企业认证段落由固定 sleep 改为
  `waitText` 轮询（消除偶发时序抖动）；新增 `matchOverlayText` 助手。

### Docs

- `docs/api-endpoints.md` v0.3.2 — 新增 §4.5 / §4.6；正文 68 → 70 条（GET 27 → 28 / POST 32 → 33）。
- `PROGRESS.md` v1.4 / `ARCHITECTURE.md` v1.2 / `PROJECT.md` v1.2 / `AGENTS.md` / `README.md` /
  `docs/AI-CONTINUE.md` v1.6（新坑：覆盖层「不出现」断言的取数范围；人才筛选依赖简历数据）。
- `worklog.md` — Task 15。

---

## [本轮文档整理] 2026-10-16 — 交接文档一致性刷新（纯文档，无代码变更）

> 目的：任何 AI / 人接手时第一时间进入状态。以 `8b69b87` 为基准，消除残留的
> 「五项功能未完成 / 工作区未提交」口径；提交 hash 见 `git log -1`。

### Docs

- `PROJECT.md` v1.1 — 阶段表与五项功能清单（✅）、验收说明（验证码 `123456`、HR 注册
  自动建公司、e2e 57/57 基线）、文档地图。
- `ARCHITECTURE.md` v1.1 — 运行时总览（HR 四 Tab、新页面）、目录树（移除 ★ 在建标记）、
  Mock 模块图（`companyOps` 快照同步）、认证流（三模式登录 + 落地页签重置）、
  §6.2 由「风险」改为「已修复勿回退」。
- `AGENTS.md` — 速览补验收现状；e2e 基线 27/27 → **57/57**；接口账目表述更新。
- `README.md` — 增补根目录五件套索引；路线表 Step 2 注明 57/57。
- `PROGRESS.md` v1.2 — §0 结算 `8b69b87` 实际 hash。
- `docs/AI-CONTINUE.md` — §0 状态一句话；目录树 / Mock 数据位置表 / §7.5 取数对照表同步新模块。
- `worklog.md` — Task 13。

---

## [8b69b87] 2026-10-16 — 验收五项功能：投递 / 候选人 / 面试、验证码登录注册、公司资料 / 企业认证

> 已提交为 `8b69b87`（基线 `592cc78`）。五项功能已全部接入 UI 并通过扩展后的
> 自动化验收（`npm run e2e` **57/57**）；同批含交接文档五件套与各处文档同步。
> 功能进度与下一步见 `PROGRESS.md`。

### Added（功能代码）

- `src/api/mock/applications.ts` — 投递 / 面试内存数据层：
  - 可变数组 `applications` / `interviews` + 懒加载种子（HR 侧 6 条覆盖全部状态枚举、
    含 1 条待确认面试与 1 条已完成面试；求职者侧 3 条，均投给非云杉公司）；
  - 状态中文映射（投递 / 面试形式 / 面试状态）；
  - 双端视图组装（`seekerApplicationViews` / `companyApplicationViews`）与会话、邀请消息辅助。
- `src/api/applications.ts` — 求职者投递 service：
  `listMyApplications` / `applyJob`（重复投递 40911）/ `withdrawApplication`（不可撤回 40913）
  / `acceptInterview` / `declineInterview`（面试归属校验）。
- `src/api/hrApplications.ts` — HR 投递 service：
  `listHrApplications`（status / jobId / q 筛选）、`getHrApplication`、
  `updateApplicationStatus`（VIEWED / OFFER / REJECTED）、
  `ensureConversationForApplication`、`inviteInterview`（404 / 40914 / 40915 / 42210 校验
  → 建面试 + 置 INTERVIEW + 发 `INTERVIEW_INVITE` 消息）、`HR_APPLICATIONS_QUERY_KEY`。
- `src/api/mock/candidates.ts` — 候选人种子（6 人画像）从 `hrConversations.ts` 抽出，
  供「沟通」与「候选人管理」两个页面共用，保证数据自洽。
- `src/api/mock/companyOps.ts` + `src/api/company.ts` — 公司资料编辑（`applyCompanyPatch`，
  改名同步 `jobs` / `hrJobRecords` 公司快照）与企业认证提交（§5.6 `POST /company/me/verify`；
  PENDING → 约 1.2s 自动通过；42210 / 40916）。
- `src/api/mock/auth.ts` / `src/api/auth.ts` / `src/store/auth.ts` — 验证码与注册：
  验证码 store（固定 `123456`、按手机号 60s 限流）、`registerMockAccount`
  （HR 注册自动建未认证新公司 `新注册企业-{手机尾4}`）、`sendSmsCode` / `loginWithSms` / `register`；
  auth store 新增 `loginSms` / `register` / `updateCompany`（身份校验先于写登录态）。
- 求职者 UI：`MyApplicationsPage.tsx`（我的投递：进度 / 撤回 / 接受与婉拒面试）。
- HR UI：`HrCandidatesPage` / `HrCandidateDetailPage` / `HrInterviewInvitePage` /
  `HrCompanyEditPage` / `HrCompanyVerifyPage`。
- `src/types/api.ts` — 契约类型 `CompanyVerifyInput`、`SmsCodeResult`。

### Changed（功能代码）

- `src/store/hr.ts` — 第 4 个 Tab `'candidates'` + 5 个覆盖页类型 + open actions + 根视图常量。
- `src/store/pinliao.ts` — `PageEntry` 增加 `'applications'`。
- `HrShell.tsx` — 四 Tab + 覆盖页渲染；`HrMePage` — 公司资料 / 企业认证入口与认证角标；
  `HrChatPage` — 面试邀请气泡卡片 + 空消息提示。
- `JobDetailPage.tsx` — 底部「发送简历」改为「投递简历」（ApplySheet + 已投递态）；
  `AppShell.tsx` / `MeView.tsx` — 我的投递入口与页面渲染。
- `LoginPage.tsx` — 重写为「密码 / 验证码 / 注册」三模式（60s 倒计时、注册身份复用顶部
  角色开关、登录成功统一重置双端落地页签）。
- `mock/hrJobs.ts` + `api/hrJobs.ts` + `HrJobEditorPage.tsx` — 新建职位归属从硬编码云杉
  改为按登录上下文注入（新公司 HR 发布的职位挂自己公司名下）。
- `mock/hrConversations.ts` — 引用共享候选人种子；`appendHrMessage` 支持 `type`
  （用于 `INTERVIEW_INVITE`）；新增 `ensureHrConversationForCandidate()`。
- `mock/companies.ts` — 新增 `registerCompany`；`lib/time.ts` — 新增 `formatDateTime`。

### Fixed

- e2e 聊天覆盖层未退出导致后续点击静默落空（新增 `clickAria('返回')`）。
- 重新登录落回上次停留页签（`LoginPage.afterAuthSuccess` 统一 `setTab('jobs')`）。
- 登录页三模式后「登录」文本歧义（e2e 改用 `form button[type=submit]` 提交助手）。

### 验收（本批改动）

- `tests/e2e-verify.cjs` 扩展：新增「候选人管理」「跨端投递闭环」「验证码登录」
  「注册 + 企业认证 + 公司资料」等段落；结果 **57/57 通过**（求职者端 11 / HR 端 15 /
  候选人管理 7 / 跨端投递闭环 14 / 验证码登录 1 / 注册与企业认证 7 / 质量 2），
  用户 Chrome 18 → 18 未受影响。
- `npx tsc --noEmit` 仍仅 3 个既有错误；本轮新增 / 修改文件 eslint 0 告警。

### Docs（交接与同步）

- 新增根目录五件套：`PROJECT.md`、`ARCHITECTURE.md`、`PROGRESS.md`（状态唯一定稿）、
  `CHANGELOG.md`（本文件）、`AGENTS.md` 追加项目规范（Next.js 自动区块保留）。
- `docs/api-endpoints.md` — 新增 §5.6 企业认证提交；公司模块行 5→6；账目说明更新为
  2026-10 复核口径（正文 68）；追加修订记录。
- `docs/AI-CONTINUE.md` v1.4 — 状态、验收记录（57/57）、坑清单（登录页签残留、
  e2e 文本匹配 / 覆盖层、HR 职位归属）同步。
- `README.md` — 功能现状、演示账号、未实现清单、目录结构更新。
- `worklog.md` — Task 11（交接）、Task 12（本轮功能）。

---

## 历史提交摘要（近 6 条，供上下文；详细 diff 用 `git show <hash>`）

| 提交 | 说明 |
| --- | --- |
| `af6197e` | feat: 新增 HR 人才筛选（演示版）——关键词匹配站内投递候选人（契约 §4.5/§4.6）；e2e 63/63 |
| `a7c1fbd` | docs: 固化「人工验收初步通过」状态并补远端仓库信息（推送 origin） |
| `bcac84a` | docs: 交接文档一致性刷新（对齐 `8b69b87`，任何 AI 接手即可第一时间进入状态） |
| `8b69b87` | feat: 完成前端验收五项功能（投递 / 候选人 / 面试、验证码登录注册、公司资料 / 企业认证）；e2e 57/57 |
| `592cc78` | fix: 修复 `npm run build` / `npm start` 在 Windows 上不可用（跨平台构建脚本）；新增可复用验收脚本 `tests/e2e-verify.cjs` + `npm run e2e` |
| `046586f` | docs: 补录自动化验收结果（24/24 通过） |

更早的 Step 0 / Step 1 决策与产出记录见 `worklog.md`（Task 0 ~ 10）。

---

## 用法约定

1. 完成一个增量后：把「未提交」段落的实际内容整理为正式条目（或标注提交 hash）。
2. 只写**发生了的事实**（含测试结果），不要把计划中的功能写成已完成。
3. 与 `PROGRESS.md` 分工：本文件讲「改了什么」，`PROGRESS.md` 讲「现在到哪、下一步」。

---

## 修订记录

| 版本 | 日期 | 变更 |
| --- | --- | --- |
| `8b69b87` | 2026-10-16 | 首版：记录五项验收功能（投递 / 候选人 / 面试、验证码登录注册、公司资料 / 企业认证）接入 UI 与 e2e 57/57，及交接五件套与文档同步 |
| （文档整理） | 2026-10-16 | 交接文档一致性刷新：以 `8b69b87` 为基准统一口径（状态 / 目录 / 基线 / hash 结算），无代码变更 |
| `af6197e` | 2026-10-16 | HR 人才筛选（演示版）：关键词匹配 + 技能标签 + 命中证据；契约 §4.5/§4.6（正文 70）；e2e 63/63 |
