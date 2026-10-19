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
  - src/types/api.ts — 与契约同步的 TypeScript 类型定义
  - src/api/mock/ — Mock 数据集（51 个职位、聊天、公司、分类树、城市）

Stage Summary:
- Step 0 完成。所有产出框架无关，无论前端最终选 Next.js / Vue / uni-app，后端选 Spring Boot / 其他，均可复用。
- 下一步等待用户确认进入 Step 1（前端开发）。

---
Task ID: 1
Agent: orchestrator (main)
Task: Step 1 — 在沙箱用 Next.js 16 按契约 + Mock 把聘聊 H5 重构出来，供用户在预览面板验收

Work Log:
- 读取现有脚手架（layout/globals/page/lib/utils）与已有 shadcn/ui 组件清单
- 规划文件结构：
  - src/config/env.ts          环境变量
  - src/api/client.ts          统一 fetch 封装 + Mock 切换
  - src/api/{jobs,categories,conversations,dict}.ts   service 层
  - src/api/index.ts           出口
  - src/store/pinliao.ts       Zustand 全局状态 + localStorage 持久化
  - src/components/pinliao/    业务组件（AppShell, TabBar, 各视图/页面/Sheet）
  - src/app/page.tsx           唯一可见路由（SPA 入口）
- 准备按"基础设施 → 视图组件 → 整合验收"分批产出

Stage Summary:
- 进行中。

---
Task ID: 1
Agent: orchestrator (main)
Task: Step 1 — 在沙箱用 Next.js 16 按契约 + Mock 把聘聊 H5 重构出来，供用户在预览面板验收

Work Log:
- 读取现有脚手架（layout/globals/page/lib/utils）与已有 shadcn/ui 组件清单
- 规划文件结构（src/config/env.ts / src/api/ / src/store/ / src/components/pinliao/）
- 基础设施层产出：
  - src/config/env.ts — 环境变量集中读取（USE_MOCK / API_BASE_URL / WS_BASE_URL / WS_PORT）
  - src/api/client.ts — 统一 fetch 封装 + ApiError + 业务码解包
  - src/api/{categories,jobs,conversations}.ts — service 层，mock 同步返回
  - src/api/index.ts — 出口
  - src/api/mock/filter.ts — 同步过滤（mock 模式专用，避免 React IIFE 异步问题）
- 状态层产出：
  - src/store/pinliao.ts — Zustand 全局状态 + persist 中间件 + localStorage 键 pinliao.v3
    · tab/pageStack/sheet/filterDraft/wishDraft/typingConvIds
    · filters/favorites/conversations/messagesByConv/profile/toastText
    · 主题由 next-themes 统一管理，store 不持有 theme 字段
- 全局样式 + 布局：
  - src/app/globals.css — 青绿主题（--primary #00857F）+ 深色（#2BBDB5）+ 移动端 safe-area
  - src/app/layout.tsx — ThemeProvider + QueryProvider + 中文字体 + viewport
- 业务组件层产出（src/components/pinliao/）：
  - QueryProvider / TabBar / PinliaoToast / AppShell / AvatarBadge
  - JobsView / JobRow（51 个职位 + 17 一级分类 + 二级岗位联动 + 筛选 chips + 空状态）
  - JobDetailPage / PageShell（通用顶栏）
  - MessagesView / ChatPage（消息气泡 + 快捷回复 + 输入框 + 关键词自动回复 + "对方正在输入"动画）
  - MeView（个人卡片 + 期望 + 菜单 + 主题切换循环）
  - ResumePage / FavoritesPage
  - BottomSheet / CitySheet / FilterSheet / WishSheet
- 整合：
  - src/app/page.tsx — 唯一可见路由，渲染 AppShell
- 修复迭代中发现的问题：
  - .d.ts 文件 Turbopack 无法解析为模块 → 重命名为 api.ts
  - FilterSheet 内联 Section 组件触发 react-hooks/static-components → 提到组件外
  - JobDetailPage handleChat 连续点击重复 pushPage → 加 store 实时读取 + 栈顶判断
  - MeView 主题循环与 next-themes 的 'system'/'auto' 名称不一致 → 加 NEXT_THEME_MAP / FROM_NEXT_THEME 双向映射
  - ChatPage 新消息到达时不标记已读 → useEffect 监听 msgs.length 自动 markConversationRead
  - Mock 模式下 listJobs 异步返回在 useMemo 不可靠 → 拆出 filterJobsSync 同步函数
- 验证（Agent Browser 自检）：
  - 首页 51 个职位正常渲染，分类 chips 17 个 + 筛选按钮
  - 点击职位进入详情页（HR 卡片、职责要求、公司、CTA 三按钮）
  - 点击"立即沟通"发起会话 + HR 自动回复 + "对方正在输入"动画
  - 聊天页发送消息（输入框 + 快捷回复）触发关键词匹配自动回复
  - 消息 Tab 会话列表 + 未读角标（初始 2 条 seed 会话）
  - 我的 Tab 个人卡片 + 菜单 + 主题循环（auto → light → dark → auto）正常
  - 收藏 toggle + toast 反馈 + 列表角标实时更新
  - 城市选择 Sheet 切换城市后首页职位过滤
  - 筛选 Sheet（排序/经验/薪资）应用后列表过滤（上海+1-3年 = 2 个职位）
  - 求职期望 Sheet 一级→二级联动 + 保存后个人卡片刷新
  - 页面栈 push/pop + 浏览器后退键支持
  - 桌面端 430px 居中（left=425 right=855）+ 移动端 390px 全宽
  - 深浅色主题切换 OK，无 console error

Stage Summary:
- Step 1 完成。Next.js 16 + TypeScript + Tailwind 4 + shadcn/ui 重构聘聊 H5。
- 文件统计：18 个新文件（含 11 个业务组件）+ 修改 4 个（globals/layout/page/store 入口）
- TypeScript 0 error，ESLint 0 error 0 warning
- dev server 在端口 3000 运行，HTTP 200，无运行时错误
- 等待用户在预览面板验收；如需调整，可直接修改对应组件，store 与 API 层已解耦。

---
Task ID: 2
Agent: orchestrator (main)
Task: 优化职位列表与详情页布局，解决"列表内容太多 + 详情页底部 CTA 需要滑动一大段才能投简历"

Work Log:
- 问题诊断：
  1. 列表卡片信息过密（4 行：标题/薪资、城市·经验·学历、4 个标签、公司+stage/size+时间）→ 单卡 ~120px，51 条列表过长
  2. 详情页 CTA（收藏/发送简历/立即沟通）位于页面底部，使用 static 布局，长内容时被推到 4300px 处，必须滑到底才能投递
- JobRow 紧凑化（4 行 → 3 行，高度 ~82px）：
  · L1 标题 + 薪资
  · L2 城市·经验·学历 + 标签内联（最多 2 个 + "+N" 提示）
  · L3 公司名 + 时间（去掉 stage/size，进详情页才看）
- JobDetailPage 改造：
  · 顶部 Header 加收藏图标（star 按钮，首屏可见）
  · 标题区下方加"内联 HR + 立即沟通"小卡片，首屏可见即可发起沟通，无需滑到底部
  · 保留底部 CTA footer 作为长内容滚动后的二次入口
- 修复底部 CTA 不常驻问题：
  · 原因：pageStack 与 tab 视图共用 relative flex-1 overflow-hidden 容器，
    JobsView 用 static 布局把容器撑到 4223px，导致 absolute inset-0 的详情页
    也撑到 4300px，footer 的 sticky/absolute bottom-0 失效
  · 解决：AppShell 中给 tab 主视图也用 absolute inset-0 包裹，让所有兄弟元素
    脱离文档流，容器高度由 flex-1 计算决定（视口高度），不再被内容撑高
  · footer 改用 fixed + max-w-[430px] + mx-auto，常驻视口底部，桌面端居中
- 验证（Agent Browser）：
  · 列表卡片高度 82px（原 ~120px，节省 32%）
  · 详情页内联"立即沟通"按钮在 231px（首屏即可见）
  · 底部 CTA 常驻视口底部（移动端 781-844，桌面端居中宽 430px）
  · 滚动到底部时最后一个 section bottom=655，在 footer top=737 之上，不遮挡
  · 桌面端 430px 居中（left=425 right=855）
  · 移动端 390px 全宽
- 关键文件：
  · src/components/pinliao/JobRow.tsx — 紧凑卡片
  · src/components/pinliao/JobDetailPage.tsx — 顶部收藏 + 内联 HR+CTA + fixed footer
  · src/components/pinliao/AppShell.tsx — tab 视图改 absolute 包裹

Stage Summary:
- 优化完成。列表更紧凑，详情页首屏即可发起沟通/收藏，底部 CTA 滚动时常驻可见。
- 等待用户在预览面板验收。

---
Task ID: 3
Agent: orchestrator (main)
Task: 还原职位列表卡片原样；解决详情页"中间一大段空白"

Work Log:
- 用户反馈：列表卡片不需要精简（恢复 4 行原样），但详情页点进去有"中间一大段空白"
- JobRow.tsx 恢复为原始 4 行布局（标题/薪资、城市·经验·学历、4 个标签、公司头像+名+stage/size+时间），高度恢复 ~134px
- 详情页空白问题诊断（Agent Browser + Python/PIL 像素分析）：
  · 上一轮把 footer 改为 fixed + 内容区 pb-64，导致 footer 上方有 63px 的背景色空白
  · 截图分析（行方差 < 1000）确认：718..781 一段 63px 纯背景色空白
- 详情页 footer 改造为 sticky bottom-0：
  · scroll 容器内套一层 `flex min-h-full flex-col`
  · footer 用 `sticky bottom-0 + mt-auto`，成为 scroll 内的 sticky 元素
  · 内容短时 `mt-auto` 把 footer 推到底部（紧贴内容下方，无空隙）
  · 内容长时 `sticky bottom-0` 让 footer 常驻视口底部
- 验证（Agent Browser）：
  · 列表卡片高度 134px（恢复原样），4 行内容齐全
  · 详情页 blank zones = []（无任何大段空白）
  · 桌面端 430px 居中（left=426 right=854）
  · 长内容模拟（注入 600px 后总高 1333）：滚动到 300 时 footer 仍在视口底部（725..788）
  · 短内容（740px = 视口）：footer 紧贴最后一节，gap=7（即 mt-2 间距）
- 关键文件：
  · src/components/pinliao/JobRow.tsx — 恢复 4 行原样
  · src/components/pinliao/JobDetailPage.tsx — footer 改为 sticky bottom-0 + mt-auto，套 flex min-h-full flex-col 容器

Stage Summary:
- 完成。列表保持原样，详情页"中间空白"已消除。footer 在短内容时紧贴底部，长内容时常驻视口。

---
Task ID: 4
Agent: orchestrator (main)
Task: 用户询问如何保存项目以便下次用其他模型继续开发；产出接续指南 + 打包下载

Work Log:
- 检查项目 git 状态：所有产出已 commit（系统自动 commit，message 为 UUID），无远程仓库
- 创建 docs/HANDOFF.md（接续开发指南）：
  · 一句话项目概览
  · 当前状态（Step 0/1 完成，等验收）
  · 关键决策清单（业务/平台/架构/契约，下一个模型无需重新问）
  · 文件索引（文档 + 代码）
  · 启动命令 + 环境变量 + dev server 持久启动方式
  · 接续开发的"启动提示词"模板
  · 三种保存迁移方案（打包/Git/仅文档）
  · 已知限制与后续路线
- 打包项目到 /home/z/my-project/download/pinliao-20260921.zip（281KB，130 文件）
  排除：node_modules / .next / .git / db / upload / skills / examples / mini-services / 日志等
- 给出三种保存方案的具体命令：
  · 方案 A：打包下载（最直接，zip 已生成在 download/）
  · 方案 B：推送到 GitHub/Gitee（最持久）
  · 方案 C：仅保存关键文档（最小，任何框架可复用契约）

Stage Summary:
- 完成。HANDOFF.md + zip 包已就绪。用户可选择下载 zip 或推送到远程仓库。
- 下一个模型只需读 docs/HANDOFF.md 即可无缝接续。

---
Task ID: 5
Agent: orchestrator (main)
Task: Step 2 验收启动 — Windows 环境迁移修复 + 修复 TabBar 未读角标 hydration mismatch

Work Log:
- 环境迁移核对（项目从 Linux 沙箱搬到 D:\boss）：
  · .env 的 DATABASE_URL 原为 file:/home/z/my-project/db/custom.db（Windows 上无效）
    → 改为 file:D:/boss/db/custom.db，并补回被省略的联调注释块
  · docs/HANDOFF.md §5.1-5.4 启动章节重写为 Windows/PowerShell：
    实测本机无 bun（仅 Node v24.18.0），原 `bun run dev` 等命令全部不可用，
    改为 npm/npx；补充"src/lib/db.ts 无人 import，故 prisma generate 非启动前置条件"
  · docs/HANDOFF.md §7.1 打包方案补 PowerShell(tar) 版，Linux 版降级为历史参考
  · 修正 4 处文档笔误 src/types/api.d.ts → src/types/api.ts
    （architecture.md §10 / dev-notes.md §3 / api-endpoints.md 抬头 / worklog.md L28）
  · 保留 worklog 历史记录与 HANDOFF 中"Linux 沙箱(历史环境)"段落内的路径，不篡改历史

- 启动 dev server 并验证：Next.js 16.3.6 (Turbopack)，端口 3000，HTTP 200，
  title「聘聊 · 招聘平台」，关键词命中。局域网地址 172.20.10.4（WLAN）。
  注意：当前 shell 非管理员，无法添加防火墙入站规则；Private 配置默认拦入站，
  手机能否访问需用户实机验证（已提供 New-NetFirewallRule 命令供管理员执行）。

- 修复 hydration mismatch（TabBar.tsx:43 未读角标 服务端 2 / 客户端 1）：
  · 根因：zustand persist 在模块初始化时自动读 localStorage['pinliao.v3'] 恢复
    conversations.userUnreadCount，与 SSR 时的 seed 数据(1+1=2)不一致；
    persist 的 merge 只做默认值兜底，不阻止持久化值覆盖 → React 丢弃服务端 HTML 重建
  · 修法：store 增加 skipHydration: true（首屏客户端也先用 seed 渲染，与服务端一致），
    新增 src/hooks/useStoreHydration.ts（含 useRehydrateStore / useStoreHydrated），
    在 AppShell 挂载后手动 rehydrate() 切到真实持久化数据
  · 验证：无头 Chrome（预置 userUnreadCount:0 的 localStorage）加载 App
    → 零 hydration 错误，且角标正确变为 0，证明 rehydrate 确实生效
  · 诚实记录：对照实验（skipHydration:false）未能复现该报错，说明原缺陷为
    时序相关（persist 的 rehydrate 与 React hydration 竞态），非确定性复现；
    本修复并非"已复现→已修好"，而是从构造上消除该竞态（首帧恒为 seed 数据）

Stage Summary:
- Step 2 验收环境就绪：http://localhost:3000 可访问，控制台无 hydration 报错。
- 待用户确认：手机局域网访问是否需放行防火墙；随后逐页验收。
- 遗留（非本次范围）：next.config.ts 的 typescript.ignoreBuildErrors:true 会掩盖
  此类问题，建议 Step 3 前改为 false；TS 现有 3 个既有报错来自 examples/（缺
  socket.io 依赖）与 src/lib/db.ts（Prisma client 未生成），均不影响当前 Mock 运行。

---
Task ID: 6
Agent: orchestrator (main)
Task: 新增登录页（区分求职者 / 公司代表 HR）与 HR 端职位管理（发布 / 编辑 / 上下架 / 删除）

前置发现：契约早已完备，无需重新设计 —— src/types/api.ts 已有 RoleEnum、
LoginPasswordInput、AuthResult、HrProfile、CompanyMember、JobUpsertInput 等；
docs/api-endpoints.md 已定义 /auth/* 7 个接口与 /hr/* 4 个接口。本次是「照契约实现前端」。

用户决策（4 项，全部采用推荐方案）：
- 身份模型：两套独立账号，登录时选身份
- HR 端范围：登录 + 职位管理（发布/编辑/上下架/删除）
- 布局：延续现有移动端 H5（430px 居中），不做桌面后台
- 登录方式：密码登录 + 预置测试账号

新增文件：
- src/api/mock/auth.ts        测试账号（求职者 13800000001 / HR 13800000002，密码均 123456）、
                              公司绑定云杉科技、HR 画像（hrId 与 jobs.ts 的 hrIdOf 算法一致，
                              确保「我发布的职位」能命中既有 Mock 数据）
- src/api/mock/hrJobs.ts      HR 端可变职位数据（模块级数组），派生自 jobs.ts；
                              状态由 id 哈希决定（稳定，不用随机，避免 SSR/CSR 不一致）
- src/api/auth.ts             登录/退出/me service；token 存 pinliao.accessToken
- src/api/hrJobs.ts           HR 职位 CRUD service + 表单校验 + 草稿⇄入参转换
- src/store/auth.ts           认证 store（独立于 pinliao store，避免侵入已验收代码）
- src/store/hr.ts             HR 工作台 store（tab / pageStack / filters / draft / toast）
- src/components/pinliao/LoginPage.tsx        登录页（身份切换 + 表单校验 + 演示账号一键填入）
- src/components/pinliao/RootGate.tsx         根路由分发（未登录→登录页；按角色→求职者端/HR 端）
- src/components/pinliao/hr/HrShell.tsx       HR 工作台外壳（顶栏含常驻「发布职位」入口）
- src/components/pinliao/hr/HrJobsPage.tsx    职位管理列表（筛选/搜索/上下架/删除）
- src/components/pinliao/hr/HrJobEditorPage.tsx 发布/编辑表单（分类联动、职责模板、福利多选）
- src/components/pinliao/hr/HrMePage.tsx      我的（公司信息 + 退出登录）

修改文件：
- src/app/page.tsx                改为渲染 RootGate
- src/api/index.ts / mock/index.ts 导出新增模块
- src/components/pinliao/AppShell.tsx   移除 useRehydrateStore（水合上提到 RootGate，避免重复）
- src/components/pinliao/PinliaoToast.tsx 支持 text/onClear 覆盖，供 HR 端复用同一视觉组件
- src/hooks/useStoreHydration.ts  新增 useRehydrateAuth（认证 store 也需水合）

关键设计决策与踩坑记录：
1. 【重要缺陷修复】角色校验必须发生在写入登录态之前。
   最初实现是「先 login() 写入 store，再判断角色不符并 setMismatch」，结果
   RootGate 在 login 返回后立刻卸载 LoginPage 去渲染工作台，setMismatch 永远
   来不及渲染 —— 用户会「以 HR 身份登入求职者端」。E2E 测出后改为：期望身份
   作为 login(account, password, expectRole) 参数下沉到 auth service，不匹配时
   抛 RoleMismatchError 且不写任何登录态，问题解决。
2. 取数方式改用 TanStack Query。最初用 useEffect + setState 手写取数，
   触发 React Compiler 的 react-hooks/set-state-in-effect 报错。项目本就装了
   QueryClientProvider（此前从未使用），改用 useQuery + invalidateQueries 后
   告警消除，且 loading/缓存/去重由 Query 负责，无需手工维护。
3. 编辑器拆成 HrJobEditorPage（只取数）+ EditorForm（只渲染表单）两个组件，
   避免在同一个组件里既取数又同步 setState。
4. 「发布职位」入口最初只放在空状态里，导致列表非空时无任何发布入口
   （E2E 测出「not found: 发布职位」）。已在 HR 顶栏加常驻按钮。

验证（Chrome DevTools Protocol 驱动无头 Chrome，真实点击与输入，14 项断言）：
- 未登录停在登录页 / 演示账号可见                               PASS
- 错误密码提示「账号或密码错误」                                PASS
- 求职者账号以 HR 身份登录 → 提示身份不匹配且不放行              PASS
- HR 正确登录 → 进入 HR 工作台，显示公司「云杉科技」与统计        PASS
- 职位列表 7 条（云杉科技在 Mock 职位中的数量）                   PASS
- 打开发布表单 / 发布成功 / 关闭后状态变「已关闭」/ 删除后消失     PASS
- 退出登录回到登录页                                            PASS
- 全程零 hydration 报错                                        PASS
求职者端回归：51 个职位、详情页、消息、我的、收藏均正常，零控制台错误。
生产构建：npx next build 通过（Route / 静态产出）。
（构建需代理才能抓 Google Fonts，见「已知限制」）

Stage Summary:
- 登录页 + HR 职位管理全链路完成并通过 E2E 验证，类型检查与 ESLint 对新代码全绿。
- 待用户验收：http://localhost:3000 用 13800000002 / 123456 登录体验 HR 端。

已知限制与后续：
- 「候选人」Tab 只在 store 类型中预留，未实现（本次范围外）；投递、面试、
  公司资料编辑等 HR 能力留待后续。
- HR 新增/修改的职位存于内存，刷新页面即重置（Mock 阶段特性）。
- 生产构建需能访问 Google Fonts（layout.tsx 用 next/font/google 的 Geist）。
  国内直连会失败，需设 HTTP_PROXY/HTTPS_PROXY，或改为 next/font/local 自托管。

---
Task ID: 7
Agent: orchestrator (main)
Task: 打通「HR 发布 → 求职者可见」跨端链路；补齐求职者端退出登录

背景：Task 6 交付后复查发现一个**功能性缺口** —— HR 端的新增/编辑/上下架/删除
作用在 hrJobRecords（从 jobs.ts 派生的独立副本）上，而求职者端只读 jobs.ts 的
静态数组。结果是「HR 发布职位后，求职者在职位列表里看不到」，而发布岗位的
核心价值恰恰就是让求职者看到并投递。

改动：
- 新增 src/api/mock/seekerJobs.ts
  createSeekerJobFinder(baseJobs, hrRecords) 纯函数，合成「求职者可见职位」：
  软删除/已关闭 → 不出现；招聘中 → 用 HR 端最新内容覆盖既有条目（关键：即使
  仅被编辑过、状态未变，也必须覆盖，否则改标题/薪资后求职者端仍显示旧内容）；
  HR 新建（id 前缀 job-new-）→ 追加。
  采用「依赖注入」而非直接 import，以消除循环依赖。
- 新增 src/api/mock/bootstrap.ts
  装配模块。因为 jobs.ts 需要 hrJobRecords、hrJobs.ts 又需要 jobs，
  直接互相 import 会成环 —— 实测在 Next/Turbopack 下抛 500（hrJobRecords 在
  初始化期对 undefined 调 .map）。改为：hrJobs 不再 import jobs，导出
  initHrJobs(baseJobs) 惰性填充；由 bootstrap.ts 按序装配。
- src/api/mock/hrJobs.ts  hrJobRecords 初始为空数组 + initHrJobs()
- src/api/mock/jobs.ts    新增导出 listSeekerJobs / jobById（合成版）
- src/api/mock/filter.ts  求职者端列表改用 listSeekerJobs()
- src/api/jobs.ts         listJobs 与 getJobDetail 改用合成数据；顶部引入 bootstrap
- src/components/pinliao/MeView.tsx
  「我的」页原先只有「切换为招聘者身份 · 即将上线」的旧占位，**没有退出登录**，
  导致求职者一旦登录就无法切换账号。改为真实的退出登录（含二次确认），
  并在文案中指引可通过登录页切换为 HR 账号。

循环依赖排查记录（重要，避免以后重蹈）：
  尝试过 1) 在 jobs.ts 里用 require('./seekerJobs') 延迟加载 —— 被 ESLint 的
  @typescript-eslint/no-require-imports 拦下；2) 从 jobs.ts 顶部 import
  './bootstrap' —— 会造成 bootstrap → jobs 的再入，baseJobs 仍是 undefined。
  最终方案（依赖注入 + 独立装配模块）是唯一同时满足「无环、无 require、
  初始化顺序确定」的写法。

验证（CDP 驱动无头 Chrome，全程用应用内退出切换账号，不刷新页面）：
- 跨端联动 10/10 通过：
  HR 发布 → 求职者列表可见 → 详情页可打开且薪资 40-60K 正确
  → 求职者内退出 → HR 关闭（状态变已关闭）→ 求职者端确认消失
- 全量回归 24/25；唯一「失败」项经数值核对为测试阈值设置错误，非代码问题：
  基础职位 51，HR 记录 51（装配成功），招聘中 38 / 已关闭 13（恰为
  stableHash % 4 的期望分布），求职者可见 38 = 51 - 13 ✓；
  动态验证关闭一个 38→37、软删除一个 37→36、恢复 38，三种变更均正确传导。
- 回归中同时确认：登录分流、错误密码、身份不匹配、发布校验（空表单拦截）、
  编辑回填、保存修改、删除、状态筛选、求职者端消息/聊天/详情/收藏、
  两端退出登录、零控制台错误、零 hydration 报错。

Stage Summary:
- 目标功能已完整：登录页（求职者/HR 双身份）+ HR 职位全生命周期管理 +
  跨端数据一致 + 双向退出登录，全部通过真实浏览器交互验证。
- 待用户验收：http://localhost:3000（HR: 13800000002 / 求职者: 13800000001，密码 123456）

---
Task ID: 8
Agent: orchestrator (main)
Task: 修复误杀用户 Chrome 的测试清理逻辑；补齐 HR 端「与求职者联系」沟通板块

用户反馈 4 项，逐条处置：

【1｜严重｜已修复】测试脚本误杀用户浏览器
  事故：为清理无头测试浏览器，此前使用
    Get-Process chrome -ErrorAction SilentlyContinue | Stop-Process -Force
  这是**按进程名全杀**，会把用户正在使用的 Chrome 全部标签页一起杀掉，
  导致标签页状态丢失并报错（与用户反馈的第 4 点高度相关）。
  修复：新增 .zscripts/kill-chrome-safe.cjs
   - killChromeTree(pid)：taskkill /T /F，仅终止指定 PID 的进程树
   - listHeadlessChrome()：按命令行特征查询本工具的无头实例，仅供核对
  实测：spawn 无头实例后调用 killChromeTree，用户侧 20 个 Chrome 进程
  数量不变，仅目标实例被终止。后续所有测试脚本一律使用此工具。

【2｜功能缺口｜本次实现】HR 端缺少与求职者联系的板块
  原先 HR 只能管理职位，看不到也回复不了候选人消息。
  新增：
  - src/api/mock/hrConversations.ts  HR 侧会话数据（可变）
      · 独立于求职者侧会话：后者 companyId 是占位值（co-mock-mumianyun），
        与 companies.ts 的真实哈希 id 对不上，无法被 HR 检索
      · 会话基于「本公司真实在招职位」派生，6 名候选人固定画像，保证自洽
      · 前 3 个会话带未读（2/1/1），便于验收未读角标与「仅看未读」筛选
      · id 用递增计数（不用 Date.now/Math.random），避免 SSR/CSR 不一致
  - src/api/hrConversations.ts       service 层（列表/详情/发消息/已读/自动回复）
  - src/components/pinliao/hr/HrMessagesPage.tsx  沟通列表
      候选人画像 + 应聘职位 + 末条消息 + 未读角标 + 仅看未读 + 按职位筛选 + 搜索
  - src/components/pinliao/hr/HrChatPage.tsx      聊天页
      气泡、快捷回复（HR 常用话术）、候选人自动回复（先显示"正在输入"）、标记已读
  - src/store/hr.ts  HrTabKey 由 candidates 改为 messages；新增 hr-chat 页面；
      页面栈改为「空栈 + 根视图由 tab 决定」，使职位管理与沟通各自独立
  - src/components/pinliao/hr/HrShell.tsx  三 Tab（职位管理/沟通/我的）+ 沟通未读角标；
      「发布职位」按钮只在职位管理 Tab 显示

【3｜有明确答案，待用户决定是否开发】是否需要「标记/筛选条件」模块
  结论：契约里已经定义好，**不需要新设计**。
  ApplicationStatusEnum = SUBMITTED/VIEWED/INTERVIEW/OFFER/REJECTED/WITHDRAWN
  搭配 GET /api/v1/hr/applications 与 POST /applications/{id}/status，
  就是 HR 端的「候选人管理页」（按投递状态筛选 + 标记状态），
  即用户所设想的模块。本次未实现，等用户确认优先级。

【4｜已查证】打开新浏览器时当前页面报错
  结论：与第 1 点同源 —— 我全杀 Chrome 进程导致标签页被强杀，状态丢失后报错。
  另附：截图中 "Encountered a script tag while rendering React component"
  警告来自 next-themes 0.4.6 的 ThemeProvider（注入 script 防主题闪烁），
  layout.tsx 自提交 54296f4 起从未被本次任务改动过，属既有无害告警。
  说明：本会话无法查看图片内容，该报错文字来自用户消息正文。

本轮踩坑（重要）：
  HrShell 改用 hrCurrentPage 作为 zustand 选择器后页面崩溃（HTTP 500，
  "The result of getSnapshot should be cached" + Maximum update depth）。
  原因：hrCurrentPage 内 `?? { type: 'job-list' }` 每次调用新建对象，
  zustand 用 Object.is 比较快照 → 判定每次都变 → 无限重渲染。
  修复：新增 useHrCurrentPage() hook，选择器只读取稳定值，栈为空时返回
  模块级常量引用；hrCurrentPage 保留给非 hook 环境并加警示注释。

验证（CDP 驱动无头 Chrome，使用 killChromeTree 安全清理）：
  HR 沟通板块 22/22 通过，含：沟通 Tab 与未读角标(4条)、6 条会话渲染、
  候选人画像与应聘职位绑定、聊天页打开、快捷回复、发送消息、候选人自动回复
  (2→4 条气泡)、返回列表、「仅看未读」筛选(3条全带角标)、搜索命中、
  搜索无结果空状态、职位管理回归(7条)、求职者端回归、零控制台错误。
  全程用户浏览器进程数 20 → 20，未受影响。

Stage Summary:
- 用户反馈第 1、4 点已根治（安全清理工具），第 2 点已实现并通过 22 项验证，
  第 3 点已给出基于既有契约的结论，待用户确认是否开发。
- 待用户验收：HR 登录后底栏「沟通」Tab。

---
Task ID: 9
Agent: orchestrator (main)
Task: 重写 README（项目门面）并修正文档中的过期/错误数字

背景：README.md 仍是 GitHub 建仓时自动生成的模板，且内容是**错的** ——
写「访问 http://localhost:5173/」（Vite 默认端口），而本项目 dev 脚本为
`next dev -p 3000`。项目正在上传 Git，README 是门面，且目标明确包含
「HR 端职位发布功能」，README 未提及。

重写 README.md，覆盖：
- 项目定位（Boss 直聘模式，C 端 + 多 B 端租户，求职者跨企业共享）
- 快速开始（正确端口 3000）+ 演示账号表（13800000001 求职者 /
  13800000002 HR，密码均 123456）
- 技术栈表
- 功能现状：分别列出求职者端、HR 端（含职位管理、候选人沟通），
  并明确列出「尚未实现」清单（候选人管理、面试、公司资料、验证码登录、
  桌面后台、真实后端），避免过度承诺
- 项目结构树 + 架构约定（单页 SPA 不走路由、service 层隔离、
  多租户、水合安全策略）
- 常用命令 + Google Fonts 构建依赖说明 + 数据库说明
- 文档索引 + 给接续开发者/AI 的启动提示词 + 开发路线

核对中发现并修正的事实错误：
1. prisma 模型数：README 初稿与 docs/HANDOFF.md 均写「18 个」，
   实际为 **19 个**（User/UserProfile/Resume/Favorite/City/Category/SysConfig/
   Company/CompanyMember/HrProfile/Job/Conversation/Message/Application/
   Interview/ResumeViewLog/TenantConfig/UsageLog/AuditLog）+ 10 个枚举。
   已同时修正 HANDOFF.md 的表格。
2. zod 与 react-hook-form 装在 package.json 但**代码零引用**，
   README 初稿误写「校验用 Zod」。已改为如实说明「已装未用」。
3. docs/api-endpoints.md 存在账目不符：「接口总览」表格合计写 **68**，
   正文实际可数出 **67** 条（GET 27 / POST 31 / PATCH 6 / DELETE 3），
   且表格内各模块数字之和亦不等于 68。已在接口文档顶部加注说明，
   并以可逐条核验的正文为准；README 不再写重复数字（改为「按模块标注」）。
   表格合计的修订涉及既有设计资产，待用户确认后统一更正。

验证：
- README 中引用的 30 个文件路径逐一 Test-Path 校验，全部真实存在
- 敏感信息扫描：无绝对路径、无凭据、无 token 泄漏
- 生产构建通过、类型检查通过

Stage Summary:
- README 已从错误模板重写为准确的项目门面，并修正两处过期数字 + 一处误述。
- 遗留待用户确认：api-endpoints.md 总览表格合计（68）与正文（67）不一致，
  是否统一更正为正文口径。

---
Task ID: 10
Agent: orchestrator (main)
Task: 产出「与具体 AI/环境无关」的接续文档，并消除文档间的状态矛盾

背景：用户希望把有用信息固化下来，换到别处的 AI 也能直接接手。
核对发现仓库内文档互相矛盾，而矛盾比缺失更危险：
- docs/HANDOFF.md 称「招聘者端 Web 后台未实现」→ 实际 HR 端已完整（登录+职位管理+沟通）
- docs/HANDOFF.md 称「Step 1 完成，等待验收」→ 实际 Step 1 已完成且已进入 Step 2
- docs/HANDOFF.md 启动命令写 `bun run dev` → 目标机器未装 bun，无法执行
- docs/dev-notes.md 称「等待进入 Step 1」→ 严重过期
- 引用 `src/types/api.d.ts`、接口数 68（正文实为 67）等

新增 docs/AI-CONTINUE.md（约 20KB）—— 唯一「先读」入口，充要且自包含：
- §0 三分钟上手 + 换环境必查四条（不用 bun / 端口 3000 /
  prisma generate 非前置 / 构建需 Google Fonts）
- §1 项目定位（Boss 模式、多租户、只做 H5、当前为 Mock 验收版）
- §2 当前状态表 + 已实现功能 + **尚未实现清单**（避免过度承诺）
- §3 技术栈 + **五条必须遵守的架构约定**（单页 SPA 不引路由 / service 层隔离 /
  框架未定 / 多租户 / 水合安全）
- §4 目录结构（逐文件职责）
- §5 运维与验证手册：命令、Mock 数据位置、**已知既有报错**（免后来者误修）、
  验收清单、自动化验证方法（含安全清理 chrome 的强制要求）
- §6 **坑与教训（最重要）**：7 项实际踩过并已修复的坑，含现象/原因/已采用解法/
  已否决写法：
  1) jobs.ts ←→ hrJobs.ts 循环依赖（含被 ESLint 拦下的 require 方案）
  2) Zustand 选择器返回新对象 → 无限重渲染
  3) 误杀用户 Chrome（严禁 Get-Process chrome | Stop-Process -Force）
  4) 身份校验必须在写入登录态之前
  5) 手写 useEffect+setState 取数触发 React Compiler 告警 → 改用 TanStack Query
  6) 同组件既取数又 setState → 拆组件
  7) 其他小坑（Mock 刷新即重置、id 不用 Date.now、稳定哈希、跨端可见性联动）
- §7 数据与契约：契约是三份文档、关键实体、**求职者可见职位合成逻辑**、
  Prisma 的真实地位、各视图数据来源对照表（易踩）
- §8 协作规范 8 条（改完更新 worklog 与本文件、照契约、用 Query、稳定选择器、
  不提交私有配置、提交信息用中文、勿动 ui/）
- §9 待用户确认事项（不替用户决定）
- §10 修订记录

同步修正既有文档，消除矛盾：
- docs/HANDOFF.md：顶部加降级 banner（指向 AI-CONTINUE.md），
  并列出「已知差异对照表」；修正 `bun run dev`（2 处）、
  接口数（2 处）、模型数（1 处）、文件索引过期描述
- docs/dev-notes.md：顶部加 banner 声明状态描述已过期；修正「当前阶段」
- README.md：文档索引改指向 AI-CONTINUE.md；重写「给接续开发者/AI 的启动提示词」
- docs/api-endpoints.md：修订记录补一行账目说明

原则：**日志（worklog.md）作为历史记录不篡改**；对已过期文档采取
「加 banner + 修正误导性事实 + 声明降级」而非删除，保留决策历史价值。

验证：
- AI-CONTINUE.md 中引用的 46 个路径逐一 Test-Path，全部真实存在
- 全仓 markdown 复扫 5 类过期说法，剩余命中均为「故意说明」或「历史记录」
- 无绝对路径 / 凭据泄漏

Stage Summary:
- 项目现在有单一、充要、环境无关的接续入口 docs/AI-CONTINUE.md；
  换任何 AI 只需给它这一份文件即可开工。
- 文档间状态矛盾已消除（过期文档均已加 banner 并修正误导性事实）。

---
Task ID: 11
Agent: orchestrator (main)
Task: 开发交接 — 盘点工作区实际状态、复验构建与验收、产出交接文档，供新会话无缝接续

Work Log:
- 复核 Git：基线 592cc78（已提交且验收通过）；工作区 2 文件修改 + 4 源码文件新增（全部未提交）；
  无 stash；未动任何业务代码
- 实测复验（2026-10-16）：
  - npx tsc --noEmit：仅 3 个既有错误（examples/websocket/*、src/lib/db.ts），新文件零错误
  - npx eslint（新增/修改的 6 个文件）：0 告警
  - npm run e2e：27/27 通过（求职者端 9 / HR 端 15 / 跨端联动 1 / 质量 2）；
    用户 Chrome 18→18 未受影响
  - dev server：端口 3000 HTTP 200
- 盘点在建内容（未提交、未接 UI，全仓 grep 无 import 证实）：
  - mock/candidates.ts（候选人种子，自 hrConversations 抽出共享）
  - mock/applications.ts（投递/面试内存数据 + 双端视图 + 状态文案）
  - api/applications.ts / api/hrApplications.ts（求职者、HR 两端 service）
  - mock/hrConversations.ts（引用共享种子 + appendHrMessage 支持 type +
    ensureHrConversationForCandidate）
  - types/api.ts（CompanyVerifyInput / SmsCodeResult）
- 产出交接文档（根目录五件套）：PROJECT.md（目标与范围）、ARCHITECTURE.md（现状结构）、
  PROGRESS.md（状态唯一定稿：已完成/进行中/下一步/风险/命令与测试结果）、CHANGELOG.md
  （未提交变更清单 + 历史提交摘要）；AGENTS.md 追加项目约束与规范
  （保留 next dev 托管区块，已核对 upsert 实现不会被覆盖）
- 同步 docs/AI-CONTINUE.md（定位改为深读手册 + 工作区未提交快照 + v1.3 修订记录）

Stage Summary:
- 交接完成：新会话按 PROGRESS.md → ARCHITECTURE.md → PROJECT.md → AI-CONTINUE.md 顺序
  即可无损接续；五项功能任务的准确进度见 PROGRESS.md §5（投递/候选人/面试：数据与服务层
  完成未提交；验证码注册、公司资料/认证：未开始）。
- 本次交接任务本身未提交（与在建功能改动同处工作区）；功能完成后应作为下一条
  worklog（Task 12）记录，并把未提交改动整理进 CHANGELOG 正式条目。

---
Task ID: 12
Agent: orchestrator (main)
Task: 验收五项功能开发 — 投递 / 候选人 / 面试流程、手机验证码登录 / 注册、公司资料编辑 + 企业认证接入 UI；扩展 e2e 并同步文档

Work Log:
- 数据与 service（沿用上一轮已就绪的内存层，本轮接线 + 补齐）：
  - 公司资料 / 企业认证：mock/companyOps.ts + api/company.ts（PENDING → 约 1.2s 自动通过；
    42210 / 40916）
  - 验证码 / 注册：mock/auth.ts、api/auth.ts、store/auth.ts（固定 123456、按手机号 60s 限流；
    HR 注册自动建未认证公司「新注册企业-{手机尾4}」）
  - hrJobs owner 上下文：新建职位归属按登录注入（原硬编码云杉）
- Store 与 UI 接线：
  - hr store：第 4 个 Tab 'candidates' + 5 个覆盖页类型 + open actions；
    pinliao store：PageEntry 增加 'applications'
  - HR 新页：HrCandidatesPage / HrCandidateDetailPage / HrInterviewInvitePage /
    HrCompanyEditPage / HrCompanyVerifyPage；HrShell 四 Tab；HrMePage 入口与认证角标；
    HrChatPage 面试邀请气泡 + 空消息提示
  - 求职者：JobDetailPage 投递 Sheet（已投递态）、MyApplicationsPage（撤回 / 接受或婉拒面试）、
    MeView 入口、AppShell 渲染
  - LoginPage 重写为密码 / 验证码 / 注册三模式；登录成功统一重置双端落地页签
    （修：重新登录会落回上次停留页签）
- e2e 扩展（tests/e2e-verify.cjs）：
  - 新增助手 clickAria（按 aria-label 点返回）/ clickSubmit（form button[type=submit]，
    规避模式页签「登录」文本歧义）/ waitText（轮询文本）
  - 修复：聊天覆盖层未退出导致后续点击静默落空（历史断言靠容错侥幸通过）
  - 新增段落：HR 候选人管理、跨端投递闭环（投递 → 约面 → 接受 → 回看）、验证码登录、
    注册 + 企业认证 + 公司资料
- 实测（2026-10-16）：
  - npm run e2e：57/57 通过（求职者 11 / HR 15 / 候选人 7 / 跨端 14 / 验证码 1 /
    注册认证 7 / 质量 2）；用户 Chrome 18 → 18 未受影响
  - npx tsc --noEmit：仅 3 个既有错误；eslint（新增 / 修改文件）：0 告警
- 文档同步：docs/api-endpoints.md（§5.6 + 公司行 5→6 + 账目说明 + 修订记录）、
  README.md（功能现状 / 演示账号 / 未实现清单 / 目录）、docs/AI-CONTINUE.md v1.4
  （状态 / 验收记录 57/57 / 坑清单）、PROGRESS.md v1.1、CHANGELOG 正式条目
- 清理：api/company.ts 删除未被使用且未登记契约的 getCompanyVerifyRecord
  （其真实分支会调未登记的 GET /company/me/verify；审核进度实际走 §5.3 轮询）

Stage Summary:
- 五项功能全部完成并自动化验收（57/57）：投递 / 候选人 / 面试闭环、验证码登录 / 注册、
  公司资料 / 企业认证；本轮改动一条中文提交入库（含 AGENTS.md / CLAUDE.md）。
- 下一步：用户逐页验收（AI-CONTINUE §5）与待确认事项（§9）；随后进入 Step 3 后端。

---

Task ID: 13
Agent: orchestrator (main)
Task: 交接文档一致性刷新 — 让任何 AI / 人接手时第一时间看懂现状（纯文档，无代码变更）

Work Log:
- 背景：五项功能已提交（8b69b87），但部分交接文档仍停留在「功能未完成 / 工作区未提交」
  口径；以「任何 AI 拿到仓库即可开工」为目标做全量核对与刷新
- 刷新（与 8b69b87 对齐）：
  - PROJECT.md v1.1：阶段表、五项功能清单（✅）、验收说明（验证码 123456 / HR 注册建公司 /
    e2e 57/57）、文档地图
  - ARCHITECTURE.md v1.1：技术栈、运行时总览（HR 四 Tab、新页面）、目录树（移除 ★ 在建标记）、
    Mock 模块图（companyOps 快照同步）、认证流（三模式登录 + 落地页签重置）、
    §6.2 由「风险」改为「已修复勿回退」
  - AGENTS.md：速览补验收现状；e2e 基线 27/27 → 57/57；接口账目表述更新
  - README.md：增补根目录五件套索引表；路线表 Step 2 注明 57/57
  - PROGRESS.md：§0 结算 8b69b87 实际 hash；v1.2 修订记录
  - CHANGELOG.md：五项功能条目挂 8b69b87；新增本次文档整理条目；历史提交摘要补行
  - docs/AI-CONTINUE.md：§0 状态一句话；目录树 / Mock 数据位置表 / §7.5 取数对照表同步新模块
- 全仓 grep 复核：无残留「27/27 / 未接 UI / 工作区新增」等旧口径；历史记录
  （worklog 旧条目、各文件修订日志）保持原样不改写
- 未改任何业务代码；文档变更不影响运行结果，e2e 基线结论沿用本轮实测 57/57

Stage Summary:
- 交接链路已一致：新会话按 PROGRESS.md → ARCHITECTURE.md → PROJECT.md → docs/AI-CONTINUE.md
  → worklog.md 顺序阅读即可无损接续，无「多份真相」。
- 下一步：等待用户逐页验收与 AI-CONTINUE §9 待确认事项；之后进入 Step 3 后端。

---

Task ID: 14
Agent: orchestrator (main)
Task: 交付收尾 — 用户人工验收初步通过后固化状态；提交并推送远端（origin / GitHub）

Work Log:
- 人工验收：用户按 AI-CONTINUE §5 清单逐页过完，初步通过（2026-10-16）
- 交付前复跑（dev server 端口 3000）：npm run e2e 57/57 通过；无头 Chrome 清理检查通过
  （headless 残留 0，未影响用户 Chrome）
- 文档固化：
  - PROGRESS：快照补「远端仓库」行、本轮提交列 8b69b87 + bcac84a；§1/§4 状态改为
    「用户人工验收初步通过（2026-10-16）」；v1.3 修订记录
  - AGENTS / PROJECT / docs/AI-CONTINUE（v1.5）：同步该状态；AI-CONTINUE 补远端指向
- 提交推送：main → origin/main（SSH，GitHub tutuchacha/boss；推送前落后 0、领先 3：
  8b69b87 + bcac84a + 本条固化提交）

Stage Summary:
- 本地与远端一致，任意机器 git pull 后可无损接续；接续阅读顺序见上一 Task。
- 下一步：用户确认 AI-CONTINUE §9 待决事项；随后 Step 3 后端（按 api-endpoints 正文 68 条契约）。

---

Task ID: 15
Agent: orchestrator (main)
Task: HR 人才筛选（演示版）— 站内投递候选人关键词匹配：自然语言 + 技能标签输入、命中证据展示；e2e 扩展至 63/63

Work Log:
- 背景：用户选定先做**无 AI 演示版**（关键词匹配）；范围仅「站内投递简历」（HR 上传外部
  简历库为后续增量）；语义检索（embedding）升级需保持契约与页面不变
- 实现：
  - 数据层 `src/api/mock/resumes.ts`（7 份简历正文种子，id 沿用 `resume-{userId}` 约定）
    + 匹配引擎 `src/api/mock/talentMatch.ts`（标签词表 / 英文技术词 / 中文去停用词 →
    技能项加权命中 + 证据片段截取与高亮）
  - service `src/api/hrTalentMatch.ts`（§4.5 匹配 / §4.6 标签词表；USE_MOCK 双分支；空条件 42210）
  - UI `HrTalentMatchPage`（需求输入 + 标签 chips + 结果卡片）+ `HrCandidatesPage` 入口；
    `store/hr.ts` 页面类型 / `HrShell` 覆盖层接入
  - 契约 `docs/api-endpoints.md` v0.3.2（正文 68 → 70 条；GET 27 → 28 / POST 32 → 33）
- 验收：e2e 新增「HR 人才筛选」6 项（命中 / 证据 / 未命中 / 空结果 / 标签），全量 63/63 通过
  （修复断言取数后复跑连续通过）
  - 排障 1：首轮「未命中」断言误报 → 根因 `body.innerText` 含覆盖层下方的页面文本，
    改为取顶层 `.pl-page-enter`（新增 `matchOverlayText` 助手）
  - 排障 2：注册与企业认证段落偶发时序抖动 → 固定 sleep 改 `waitText` 轮询
- 文档：PROGRESS v1.4 / ARCHITECTURE v1.2 / PROJECT v1.2 / README / AGENTS /
  docs/AI-CONTINUE v1.6（新坑：覆盖层断言取数范围、人才筛选依赖简历数据）+ CHANGELOG 条目
- 提交：`af6197e`（feat: HR 人才筛选演示版）

Stage Summary:
- 演示路径：HR `13800000002` → 候选人 Tab →「按技能筛选」→ 输入「React」/「自动化测试」
  （命中；证据含原文高亮）；「COBOL」（无命中提示）；结果卡片可进候选人详情
- 下一步：HR 上传外部简历库、语义检索升级（替换 `mock/talentMatch.ts` + 服务端实现，
  接口 / 页面不变）；用户确认 AI-CONTINUE §9 待决事项后进入 Step 3 后端（正文 70 条契约）

---

Task ID: 16
Agent: orchestrator (main)
Task: Step 3 后端开发规划 — 分块执行计划（T0~T9 任务板）与上下文交接协议（纯文档）

Work Log:
- 背景：用户启动 Step 3（Spring Boot 后端）开发；会话上下文预算有限（用户侧约 20w/会话），
  要求「任务分块 + 上下文触发后可持续接力」
- 环境盘点（2026-10-17 实测）：本机仅 JDK 1.8（JAVA_HOME=D:\JDK1.8，需装 Temurin 17）；
  Maven 3.6.3（用 mvnw 规避）；无 Docker；MySQL 服务已停止、无 PostgreSQL/Redis（端口全空闲）；
  winget v1.29 可用（Temurin 17 包已确认）
- 产出 `docs/step3-plan.md`（计划与任务板的唯一出处）：
  - 总验收：后端复刻演示语义（种子数字 / 固定验证码 / 认证 1.2s 自动通过 / 聊天自动回复等），
    以「前端 e2e 63/63 切真后端跑通」为收官
  - T0~T9 分块（环境骨架 / 迁移与种子 / 鉴权 / 职位字典 / 投递面试 / 公司认证 / 消息 WS /
    求职者简历收藏上传 / 招聘者与人才筛选移植 / 全量联调）；T10+ 增量另行
  - 上下文与交接协议：开工/收工 checklist、上下文将满应急流程、用户开场白模板、预算与省 token 技巧
- 同步：PROGRESS v1.5（快照 + 下一步指向计划）、AGENTS 速览、CHANGELOG 条目

Stage Summary:
- 下一步（用户执行）：装 JDK 17（`winget install EclipseAdoptium.Temurin.17.JDK`）、
  选定 PostgreSQL/Redis 安装方式（待拍板见 step3-plan §6）；然后开新会话从 T0 开工
- 接力方式：新会话按 step3-plan §5.2 开工 checklist - T0/T1 两轮内应可看到后端健康检查与种子数据

---

Task ID: 17
Agent: orchestrator (main)
Task: Step 3 T0 — 环境与工程骨架（Spring Boot 3.5.16 + 统一包络/异常/traceId + MySQL/Redis 专用实例）

Work Log:
- 环境决策（用户拍板）：单仓 backend/；JDK 用 IDEA 自带 JBR 21（免安装，本机原仅 JDK 1.8）；
  数据库改用 MySQL 8（替代计划中 PostgreSQL）；Docker 评估可行但需 WSL2 + 重启 1~2 次 + 镜像加速，
  用户选「免 Docker 直装」：复用本机 MySQL 8.0.29 二进制另起专用实例（3307，数据 .local/mysql），
  Redis 用 Windows 版 zip（tporadowski 5.0.14.1，.local/redis）
- 工程骨架：start.spring.io 已下架 Boot 3.x → 手工建 backend/（Boot 3.5.16 + Maven Wrapper 3.3.2/Maven 3.9.16；
  MyBatis-Plus 3.5.17、springdoc 2.9.1；Web/Validation/MySQL/Flyway/Data Redis/WebSocket/Actuator/Lombok）
- 统一层：ApiResponse 包络 + ApiCodes 错误码表（code→HTTP 前 3 位推导）+ GlobalExceptionHandler +
  TraceIdFilter（MDC / X-Trace-Id 响应头）+ ResponseAdvice；样例接口 /api/v1/_demo/{ping,error,validate}
- 本地环境脚本（backend/scripts）：_env / db-init / db-start / db-stop / redis-start / redis-stop / dev-up / dev-down
- 验收（实测 2026-10-17）：mvnw test 3/3；spring-boot:run 3.5s 启动、Flyway 连 MySQL 8.0（0 迁移 + 建 history 表）；
  /actuator/health 200 UP；ping 包络/traceId 一致；validate 422+42201+errors；error 404+40401；swagger 200
- 文档：step3-plan v1.1（T0 勾选、基线 MySQL、§6 结项）、PROGRESS v1.6、CHANGELOG、AGENTS 速览、AI-CONTINUE §6.8
- 提交：`dd7a948`（feat(backend): Step 3 T0 后端工程骨架与本地环境）

Stage Summary:
- T0 完成。后端本地可启动（:8080），MySQL(3307)/Redis(6379) 一键起停（dev-up/dev-down）。
- 下一步：T1 数据库迁移与演示种子（Flyway V1 建表 MySQL 方言 + V2 种子，数字对齐 step3-plan §3）

---

Task ID: 18
Agent: orchestrator (main)
Task: Step 3 T2a — 鉴权：验证码 / 密码·短信登录 / 刷新 / 退出 / me（§1.1、§1.3~§1.7）+ V1/V2 鉴权表与演示账号

Work Log:
- 用户指示：后端先完成「登入和退出」→ 本次 chunk = §1 除注册外的 6 条（注册拆为 T2b 顺延）
- 建表：V1__auth_schema.sql（users / user_profiles / companies / company_members / hr_profiles，
  MySQL 8，companyId 租户列 + 外键 + 索引）；V2__seed_auth_demo.sql（13800000001 求职者 / 13800000002 HR，
  密码 123456；云杉科技 co-acgiw7、HR hr-x2270n —— id 与前端 mock 哈希一致）
- 依赖：pom + jjwt 0.12.6（api/impl/jackson）+ spring-security-crypto（仅 BCrypt）
- 代码（com.pinliao.backend.auth）：AuthProperties/AuthConfig；JwtService（HS256，jti/role/companyId 声明）；
  AuthTokenStore（Redis refresh 白名单 30d + access jti 吊销名单）；SmsCodeService（固定 123456 / 5min /
  60s 限流 / 一次性消费）；AuthService（登录、刷新轮换、退出、me、会话装配 user+profile+company+member）；
  AuthInterceptor + UserContext（默认保护 /api/v1/**，白名单 sms/login/refresh/register/_demo）；
  AuthController 6 接口；ApiCodes + 40110/40111
- 测试：JwtServiceTest 4 项 + PasswordHashTest 1 项（种子 hash 回归）；EnvelopeSmokeTest 排除鉴权拦截器
  （WebMvcTest 切片会扫 HandlerInterceptor）；mvnw test 3/3 → 8/8
- 冒烟（backend/scripts/smoke-auth.mjs，入库）：14 项全过 —— 登录 200（云杉 company/member）、
  40110/40101/40102/40103/42201/42901 全码位、refresh 一次性轮换、退出后 me=40102；另手工验 40111（暂停账号）
- 数据库复验：flyway V1/V2 success=1；5 表行数正确；登录推进 last_login_at；发现 TINYINT(1) 弃用告警
  → 改 BOOLEAN 重建本地库重跑（日志干净）
- 文档：PROGRESS v1.7、step3-plan v1.2（T2 拆 T2a/T2b；T1 顺延 V3+）、CHANGELOG、AI-CONTINUE §6.8+v1.8、
  backend/README（鉴权命令与冒烟）
- 提交：`6f286e3`（feat(backend): Step 3 T2a 鉴权）

Stage Summary:
- T2a 完成：鉴权 6 接口在真实 MySQL/Redis 上可用（access 2h / refresh 30d / 验证码 123456）；
  受保护接口统一 40101/40102；T9 联调前无需再动鉴权主干
- 下一步（按用户指示二选一）：T1 剩余迁移（V3 建剩余表 + 种子对齐 §3 数字）或 T2b 注册（§1.2 + 注册建公司）

---

Task ID: 19
Agent: orchestrator (main)
Task: 文档同步 —— AGENTS 速览 / 后端 README / 根 README 对齐 Step 3 T2a 现状

Work Log:
- 用户指示「readme 文件也同步更新一下」
- `837f534`：AGENTS.md 速览区块更新至 T2a
- backend/README.md 已在 `6f286e3` 内更新（鉴权命令、冒烟、目录结构），本次复核无残留旧口径
- 根 README.md：阶段口径改「Step 2 验收版 + Step 3 进行中（T0 ✅ / T2a ✅）」；技术栈表加后端行；
  多租户说明改 MySQL 8（RLS 随 T0 定案作废）；数据库说明改 Flyway 迁移；常用命令加
  「后端（Step 3，进行中）」段；文档索引补 `docs/step3-plan.md`；启动提示词与开发路线表同步
- 提交：`29c21c1`（docs: 根 README 同步 Step 3 进度）

Stage Summary:
- 根/后端 README、AGENTS 速览三处口径与 T2a 现状一致；其余文档（PROGRESS/CHANGELOG/AI-CONTINUE/step3-plan）此前已同步
- 下一步不变（待用户拍板）：T1 剩余迁移（V3+）或 T2b 注册（§1.2 + 注册建公司）

---

Task ID: 20
Agent: orchestrator (main)
Task: Step 3 T1 — 数据库迁移与演示种子（V3 全量表 + V4~V8 种子对齐 mock + MyBatis-Plus 分页/租户/时间填充）

Work Log:
- 用户指示「继续进行（分阶段推进、完成后及时更新文档）」→ 本 chunk = T1（用户此前二选一未拍板，按计划依赖选 T1），
  内部拆 T1a/T1b 两次 feat 提交防上下文丢失
- 数据源侦察：自写 Node 24 ESM loader（`@/` 别名 + 无扩展名补 .ts）+ `--experimental-transform-types`
  直接 import `src/api/mock/*`，实证关键数字：职位 51 / 初始关闭 13 / 可见 38 / 云杉 7；
  投递 9（HR 6 + 求职者 3 非云杉）；会话 8（HR 6 未读 4 + 求职者 2）；简历 7；候选人 6
- T1a（`8ed73f8`）：
  - `V3__core_schema.sql`：12 张剩余业务表（jobs / resumes / favorites / applications / interviews /
    conversations / messages / resume_view_logs + cities / categories / sys_configs / tenant_configs /
    usage_logs / audit_logs），companyId 租户列 + 索引；hr 展示字段冗余（演示无全量 HR 账号）
  - MyBatis-Plus：分页（MySQL，≤200/页）+ `PinliaoTenantLineHandler`（HR 注入 company_id；求职者跨租户
    与全局表不注入；缺企业上下文访问租户表 fail-fast 40301）+ `PinliaoMetaObjectHandler`（UTC 自动填充）
  - 测试：`PinliaoTenantLineHandlerTest` 6 项；`mvnw test` 8/8 → **14/14**
- T1b（`85518bf`）：
  - `backend/scripts/seed-gen/`（loader-hooks + register + generate.mjs）从 mock 生成 V4~V8（文件带「勿手改」头）：
    公司 15 / 城市 9 / 分类 84；职位 51（可见 38、云杉 7，关闭规则同 mock stableHash%4）；候选人 6 + 简历 7；
    投递 9 + 面试 3；会话 8（HR 6 未读 4 + 求职者 2）+ 消息 16
  - `backend/scripts/verify-seed.ps1` — 16 项行数断言（.ps1 已加 UTF-8 BOM）
- 验收（实测 2026-10-18）：启动自动 migrate **V1~V8 成功**（3.3s，日志无告警）；`verify-seed.ps1` **16/16 命中**；
  `node scripts/smoke-auth.mjs` **14/14 无回归**；后端已停、MySQL 3307 与 Redis 6379 保持运行
- 文档：PROGRESS v1.8、step3-plan v1.3（T1 ✅ + §4 实测）、CHANGELOG、AI-CONTINUE §6.9、backend/README 命令

Stage Summary:
- T1 完成：库表与演示种子就绪（对齐 step3-plan §3 全部数字），种子由 mock 一键再生成（改数据改 mock 后重跑）；
  租户注入策略已定并被测试覆盖（求职者跨租户是产品语义，勿改成按表一刀切）
- 下一步：T2b 注册（§1.2 + 注册自动建公司「新注册企业-{手机尾4}」、40910/40110，照抄 mock `registerMockAccount` 语义）；
  随后 T3 职位与字典模块起

---

Task ID: 21
Agent: orchestrator (main)
Task: Step 3 T2b — 注册（§1.2；HR 自动建未认证公司；40910/40110）

Work Log:
- 语义侦察：mock `registerMockAccount`（用户/画像/公司/成员字段与文案）+ 前端 service 校验顺序
  （字段校验 → 已注册 40910 → 验证码 40110，失败不消耗）+ e2e 注册段（固定手机号 13900004321）
- 实现：
  - `ApiCodes.PHONE_REGISTERED = 40910`；`AuthRequests.RegisterRequest`（phone 正则 / password ≥6 / role 枚举）
  - `AuthService.register`（@Transactional）：校验顺序对齐 mock；HR 自动建「新注册企业-{尾4}」
    （未融资 / 1-19人 / 企业服务 / verified=false）+ 招聘官{尾4} + ADMIN 成员；求职者建「求职者{尾4}」画像；
    id 派生自手机号（u-reg-/up-reg-/co-reg-/hr-reg-/cm-reg-{phone}）——mock 递增计数刷新复位，落库需跨重启唯一
  - `AuthController` 新增 `POST /api/v1/auth/register`（白名单已含 register，无需改拦截器）
- 测试：`AuthServiceRegisterTest` 4 项（Mockito 假体；踩坑：无 Spring 上下文时 LambdaWrapper 报
  `can not find lambda cache` → 静态块 `TableInfoHelper.initTableInfo` 手工注册 4 个实体）；
  `mvnw test` 14/14 → **18/18**
- 冒烟：`smoke-auth.mjs` 新增注册段 6 项（14 → **20 项**）全对——发码 200 / 坏码 40110 / HR 成功
  （新注册企业-0259、ADMIN、verified=false）/ 重复 40910 / me 带新 token / 求职者无 company；
  边界手工验证：42201 ×3（phone/password/role）+ 未发码 40110
- `verify-seed.ps1`：users / companies 行数按前缀排除 `u-reg-*` / `co-reg-*`（冒烟注册不破坏 16 项断言，
  实测 16/16 仍全中）；后端已停、MySQL 3307 与 Redis 6379 保持运行
- 文档：PROGRESS v1.9、step3-plan v1.4（T2b ✅）、CHANGELOG、AI-CONTINUE §6.10（注册落库 / e2e 固定手机号
  重跑风险 / TableInfo 坑）、backend/README + AGENTS 命令更新
- 提交：`030a5b2`（feat: Step 3 T2b —— 注册）+ 紧后 docs 结算 `899d201`

Stage Summary:
- T2b 完成：注册链路可用且与 mock 语义一致（已注册 40910、验证码 40110、HR 未认证公司联动企业认证）；
  注册记录持久保留（演示库现有 u-reg-*/co-reg-* 等测试记录）
- 下一步：T3 职位与分类字典（§6/§7：列表/搜索/筛选/排序 + HR CRUD/概览；验收基线 = 可见职位 38 条）；
  T9 前需定 e2e 固定手机号对真实库重跑的重置策略（AI-CONTINUE §6.10，待用户拍板）

---

Task ID: 22
Agent: orchestrator (main)
Task: Step 3 T3a — 职位公开读与分类字典（§6.1~§6.4/§6.9 + §7，8 条）

Work Log:
- 侦察：契约 §6/§7、api-contract 分页规范、mock 语义（jobs.ts / hrJobs.ts / filter.ts / seekerJobs.ts /
  categories.ts / dict.ts）、前端 service 与类型形状、V3 表结构、V4/V5 种子数字
- 实现（T3 拆 T3a 公开读 / T3b HR 写；本块 T3a）：
  - `job/`：JobEntity（tags/welfare JSON → JacksonTypeHandler + autoResultMap）、JobMapper（公开浏览 SQL
    显式 @InterceptorIgnore(tenantLine="true")——HR 浏览也全平台；关键词 hay 对齐 mock）、JobQueryService
    （筛选/相似/埋点对齐 mock；recommend 期望二级岗 +2 / 一级类 +1 稳定排序；游标 base64 JSON {s,k,id}；
    详情 40410；ETag=id+updatedAt）、JobController / JobSearchController（§6.2 独立路径）
  - `dict/`：DictController + DictService（分类树 17 根/84 节点、城市 9、字典常量对齐 mock dict.ts）
  - `AuthInterceptor`：公开接口「可选鉴权」（方法 + Ant 路径判定；匿名放行、带 token 解析、写接口不放行）
- 测试：`mvnw test` 18/18 → **35/35**（职位 12 / 公开路径 2 / 字典 3）
- 冒烟：新增 `smoke-jobs.mjs` **29/29**（列表/筛选/搜索/游标翻页/加权排序/详情/相似/ETag 304/埋点去重/分类/城市/字典）；
  `smoke-auth.mjs` 20/20 无回归
- 开发期两坑（已记入 AI-CONTINUE §6.11）：@Select 非 <script> 不解析 `&lt;&gt;` 实体（改用 `!=`）；
  Pagination 的 @JsonInclude(NON_NULL) 吞 nextCursor:null
- 文档：PROGRESS v1.10、step3-plan v1.5（T3a ✅ + T3b 行）、CHANGELOG、AI-CONTINUE §6.11（含 T9 需加 Next rewrites 坑）、
  backend/README、AGENTS
- 提交：`9f509be`（feat: Step 3 T3a）+ 紧后 docs 结算

Stage Summary:
- T3a 完成：职位公开读 8 条接口全绿（可见 38 / 云杉 7 / 分类 84 与 mock 基线一致）；公开接口契约语义（匿名可读、
  登录加权、ETag 缓存）已落地；T9 联调前列出的 Next rewrites 缺失问题已记录待处理
- 下一步：T3b HR 职位写（§6.5~§6.8：发布/更新/关闭重开/软删；本公司校验 40310、配额 42211；冒烟收尾清理
  保持可见 38 基线）

---

Task ID: 23
Agent: orchestrator (main)
Task: Step 3 T3b — HR 职位写接口（§6.5~§6.8，4 条）

Work Log:
- 侦察：契约 §6.5~§6.8、mock hrJobs.ts（40310 / 40410 语义、软删 / 更新保留统计）、前端 service 与
  JobUpsertInput 形状、ApiCodes / JobCodes、租户注入策略（INSERT 的 ignoreInsert）、登录 claims（role/companyId）、
  HrProfileEntity、validation 与包络约定
- 实现：
  - `JobCommandService`：发布 / 更新 / 关闭·重开 / 软删；仅 HR / COMPANY_ADMIN 且带 companyId；
    归属公司取自登录上下文（不信任请求体）；不存在 / 非本公司 / 已软删统一 40310（不泄露存在性，对齐 mock）；
    配额 tenant_configs.max_jobs（缺省 50）→ 42211；发布限流 10 次/分钟/公司（Redis 自然分钟）→ 42901；
    PATCH 部分更新（null 保留原值，合并后校验 42201）
  - 新 id `job-new-{seq}`：库内最大序号 +1（`SUBSTRING(id, 9)`，显式 @InterceptorIgnore——id 全局唯一；
    软删保留行、序号不复用；禁时间戳/随机）；`publishedAt` 截毫秒（DATETIME(3)）
  - `JobController`：POST /jobs、PATCH /jobs/{id}、POST /jobs/{id}/close|reopen、DELETE /jobs/{id}；
    `JobViews.HrRecord` 对齐 HrJobRecord；`JobRequests.Upsert`；JobCodes 40310 / 42211；JobMapper 配额与序号查询
- 测试：`JobCommandServiceTest` 15 项（创建成功/角色/上下文/配额/限流/校验/更新保留统计/越权/软删幂等/关闭重开）；
  `mvnw test` 35/35 → **50/50**
- 冒烟：`smoke-jobs.mjs` 29 → **49/49**（未登录 401 / 求职者 40301 / 42201 / 发布归属云杉 + OPEN /
  可见 39 / 更新保留发布时间统计 / 关闭后 404 且回到 38 / 重开 / 他公司 HR 与不存在 40310 / 软删 {} + 404 /
  收尾回到 38 基线 + 兜底幂等清理）；`smoke-auth.mjs` 20/20、`verify-seed.ps1` 16/16 无回归（排除 job-new-%）
- 踩坑记录（AI-CONTINUE §6.12）：租户插件 INSERT 的 ignoreInsert 行为；新 id 必须全局取最大；
  publishedAt 纳秒 vs DATETIME(3)；配额缺省 50；40310 统一语义；冒烟软删行与 verify-seed 排除
- 文档：PROGRESS v1.11、step3-plan v1.6（T3b ✅ + §4 实测 + 下一步 T4）、CHANGELOG、AI-CONTINUE §6.12、
  backend/README、AGENTS
- 提交：`8102f0b`（feat: Step 3 T3b）+ 紧后 docs 结算

Stage Summary:
- T3b 完成：HR 职位写 4 条接口全绿（发布 / 更新 / 关闭重开 / 软删；40310 / 42211 / 42901 语义落地）；
  冒烟新建职位收尾软删，可见 38 基线保持；三个冒烟 / 单测基线全部无回归
- 下一步：T4 投递与面试闭环（§9/§10，10 条；状态机 + 会话按需创建（先占位）+ 校验码
  40911 / 40913 / 40914 / 40915）；T9 前仍需用户拍板 e2e 固定手机号对真实库的重置策略

---

Task ID: 24
Agent: orchestrator (main)
Task: Step 3 T4 — 投递与面试闭环（§9/§10，10 条 + §8.7 约面试）

Work Log:
- 侦察：契约 §9/§10/§8.7 与 §4.4、mock applications.ts（状态机 / 409xx 语义 / ensureConversation）、
  双端 service 出参期望、types Application*/Interview*/Resume/Message、V3 表结构、V7/V8 种子、
  verify-seed、JobViews/JobQueryService 映射、UserContext/AuthInterceptor/租户策略
- 决策：投递成功即幂等占位建会话（conv-new-*），ApplicationDetail 增 conversationId 供 HR 触达 / 约面；
  新 id 统一 app-new/int-new/conv-new/msg-new 递增派生；§8.7 按契约返回 Message（前端声明 Interview 但未用返回值）
- 实现：
  - application/：ApplicationEntity / InterviewEntity / ConversationEntity / MessageEntity / ResumeEntity +
    Mapper（nextNewSeq 全局取最大，显式 @InterceptorIgnore）；ApplicationViews（Application / WithJob /
    Detail / Resume / Interview / InterviewWithJob / InterviewWithCandidate / Message）；ApplicationCodes
  - ApplicationCommandService：§9.1 投递（默认简历 / 42210 / 40911 / 撤回重投复用）、§9.4 撤回（40913）、
    §9.5 HR 状态（42201 / 40913）、§8.7 约面试（40914 / 40915 / 42201 / 42210 + 建面试 + INTERVIEW 状态 +
    INTERVIEW_INVITE 消息 + 未读 +1）、§10.4/§10.5 接受拒绝（40410 归属 / 幂等终态）
  - ApplicationQueryService：§9.2 我的投递、§9.3 详情（job/resume/interviews/conversationId）、§10.1/§10.2
    双端列表、§10.3 详情；游标 base64 JSON {k,id}，updatedAt/createdAt DESC + id ASC；状态筛选
  - ApplicationController（10 条 + §8.7）；Roles 角色守卫；PageViews 游标分页；JobQueryService 增
    summaryOf/detailOf 复用职位映射
- 测试：新增 ApplicationCommandServiceTest 22 项 + ApplicationQueryServiceTest 5 项；
  `mvnw test` 50/50 → **77/77**
- 冒烟：新增 smoke-applications.mjs **51/51**（投递 / 重复 40911 / 游标 / 详情 / 越权 40410 / 状态 /
  40914 / 40915 / 42201 / 42210 / 约面消息 / 双端面试 / 接受拒绝幂等 / 撤回重投；连续两次通过）；
  smoke-jobs 49/49、smoke-auth 20/20、verify-seed 16/16 无回归（排除 app-new/int-new/conv-new/msg-new）
- 踩坑记录（AI-CONTINUE §6.13）：投递建会话与 T6 复用；状态机判断顺序；接受/拒绝幂等；
  40410 归属隔离；四类 id 前缀与 DATETIME(3) 截毫秒；冒烟可重复；T9 分页/详情/§8.7 前端契约差异
- 文档：PROGRESS v1.12、step3-plan v1.7（T4 ✅ + §4 实测 + 下一步 T5）、CHANGELOG、AI-CONTINUE v1.13、
  backend/README、AGENTS
- 提交：`d4ce22c`（feat: Step 3 T4）+ 紧后 docs 结算

Stage Summary:
- T4 完成：投递与面试闭环 10 条 + §8.7 全绿；状态机 / 校验码 / 归属隔离 / 会话占位落地；单测 77/77、
  冒烟 51/51 可重复，其余基线无回归
- 下一步：T5 公司模块 + 企业认证（§5，6 条；资料编辑改名同步职位公司名 + 认证提交 / 1.2s 自动通过 / 轮询）；
  T9 前仍需用户拍板 e2e 固定手机号对真实库的重置策略

---

Task ID: 25
Agent: orchestrator (main)
Task: Step 3 T5 — 公司模块 + 企业认证（§5.1~§5.6，6 条）

Work Log:
- 侦察：契约 §5 六条、前端 company service / mock companyOps / 认证页轮询、CompanyEntity / CompanyMemberEntity、
  V1 companies / company_members 表、V2 演示 HR 成员、V4 公司种子、JobMapper / JobQueryService 装配、
  AuthInterceptor 公开路径与 Roles 守卫
- 决策：
  - §5.4~§5.6 的 COMPANY_ADMIN 以 `company_members.role=ADMIN` 为准；演示 HR user.role=HR、成员角色 ADMIN，
    不能直接判 token role。§5.3 仍允许 HR / COMPANY_ADMIN。
  - 认证提交只更新 companies.verified/status，不新增提交记录表（契约不暴露、数据模型无表）；四项资料仅校验。
  - 认证自动通过用 daemon ScheduledExecutorService（默认 1200ms，`pinliao.company.verify-delay-ms` 可覆盖）；
    后台再次确认 PENDING，避免覆盖人工禁用。
  - 公司改名不刷 jobs 行：职位公司名从 companies 表实时装配，§5.1/§5.2/§6.x 自动同步。
  - 公司职位仅 OPEN 且未软删；公司不存在统一 40410；游标沿用 §6.1 格式（score 固定 0）。
- 实现：
  - company/：CompanyCodes（40410 / 40916 / 42210）、CompanyRequests（Patch / Verify）、CompanyService
    （§5.1 详情、§5.2 职位、§5.3 我的公司、§5.4 部分更新、§5.5 成员 + user/hrProfile、§5.6 认证提交 /
    自动通过）、CompanyController
  - JobMapper.selectVisibleByCompany + JobQueryService.listByCompany（公司职位游标分页）
  - AuthInterceptor 公开路径新增 GET /companies/*、GET /companies/*/jobs；单测补充匿名放行 / 受保护断言
  - 单测：CompanyServiceTest 12 项（公开读 / 404 / 权限 / trim / 校验 / 成员 / PENDING→异步 ACTIVE / 40916）+
    JobQueryServiceTest 公司职位分页 1 项；异步用例延迟 30ms + Mockito timeout 2s
  - 新增 `backend/scripts/smoke-company.mjs` 26 项：公开详情 / 分页 / 40410、我的公司 / 40301、
    临时 co-reg-* HR 注册 ADMIN、名称校验 / 改名 / 公开详情同步、成员 user+hrProfile、
    临时 job-new-* 验证职位公司名同步（finally 软删）、认证 42210 / PENDING→轮询 ACTIVE / 40916
- 验收（实测）：
  - `mvnw.cmd test` **90/90**（T4 基线 77 + 公司 12 + 公司职位分页 1）
  - `smoke-company.mjs` **26/26**，连续两次执行均通过
  - `smoke-jobs` 49/49、`smoke-applications` 51/51、`smoke-auth` 20/20、`verify-seed` 16/16 无回归
- 文档：PROGRESS v1.13、step3-plan v1.8（T5 ✅ + §4 实测 + 下一步 T6）、CHANGELOG、
  api-endpoints v0.3.3（§5 权限 / 40410 / OPEN 口径）、AI-CONTINUE v1.14（§6.14）、backend/README、AGENTS
- 提交：`2359625`（feat: Step 3 T5）+ 紧后 docs 结算

Stage Summary:
- T5 完成：公司模块 6 条全绿；公开读 / 资料编辑 / 成员 / 企业认证异步自动通过落地；
  管理员语义按成员角色 ADMIN；单测 90/90、公司冒烟 26/26 可重复，其余基线无回归
- 下一步：T6 消息 + WebSocket（§8，8 条 + 4 主题；复用 T4 的 conv-new-* 占位会话）；
  T9 前仍需用户拍板 e2e 固定手机号对真实库的重置策略

---
Task ID: 26
Agent: orchestrator (main)
Task: Step 3 T6 — 消息 + WebSocket（§8.1~§8.8，8 条 + 4 主题）

Work Log:
- 侦察：契约 §8 / §10、前端 conversations / hrConversations mock 语义、ChatPage 取数形状、
  ConversationEntity / MessageEntity / CompanyMember / HrProfile、T4 的 conv-new-* 占位会话
- 决策：
  - 未读计数以 conversations.user_unread_count / hr_unread_count 为持久化事实源；Redis 只做
    发送限流（30 次/分钟/用户）与 idempotencyKey 24h 去重，重启不丢未读。
  - 会话列表返回双端超集（conversation / job / hr / company / candidate / lastMessage），
    同一端点按角色取用；补契约外 GET /conversations/{id} 给 HR 聊天顶栏（不计账）。
  - 历史按 createdAt DESC, id ASC 游标；列表按 lastMessageAt DESC, id ASC。
  - 发起会话 (jobId,userId) 唯一，重复 40912；职位不存在 / 下线 40410；firstMessage 写 SYSTEM + TEXT。
  - WebSocket 用 /ws STOMP；query token 为主，兼容 Authorization / STOMP header；新消息只推对方，
    避免发送方回显重复；typing 同时推用户队列与会话主题；错误推 /user/queue/errors。
  - 自动回复双向：USER TEXT → HR 关键词回复；HR TEXT → 候选人关键词回复；先 typing=true，
    默认 1.2s 后落库并推消息 + typing=false，延迟可配 pinliao.message.auto-reply-delay-ms。
- 实现：
  - message/：MessageCodes、MessageRequests、MessageViews、ConversationQueryService /
    ConversationCommandService / ConversationAutoReplyService、ConversationController、
    MessageRealtime + StompMessageRealtime、WebSocketConfig、WsHandshakeInterceptor /
    WsAuthChannelInterceptor / WsPrincipal、MessageWebSocketController
  - ApplicationCommandService 注入 MessageRealtime，T4 INTERVIEW_INVITE 消息同步推送求职者
  - 单测 24 项：命令 15（发起 / 越权 / 校验 / 幂等 / 限流 / 已读 / typing / 关闭 / 简历）+
    查询 6（双端列表 / 游标 / 历史 / 越权 / 非法游标）+ 自动回复 3（双向 / 关键词 / 默认）
  - 新增 backend/scripts/smoke-messages.mjs 40 项：REST 全链路 + WS 推送 + STOMP 发送 /
    typing + 错误队列 + 非法 token；只写 conv-new-* / msg-new-*，保护种子未读 4 / 2
- 验收（实测）：
  - `mvnw.cmd test` **114/114**（T5 基线 90 + 消息 24）
  - `smoke-messages.mjs` **40/40**，连续两次执行均通过；含真实 STOMP 帧往返
  - `smoke-company` 26/26、`smoke-jobs` 49/49、`smoke-applications` 51/51、`smoke-auth` 20/20、
    `verify-seed` 16/16（HR 会话 6 / 未读 4，求职者 2，消息 16）无回归
- 文档：PROGRESS v1.14、step3-plan v1.9、CHANGELOG、api-endpoints v0.3.4、api-contract v0.3.1、
  AI-CONTINUE v1.15（§6.15）、backend/README、AGENTS、README、PROJECT、worklog
- 提交：`8d9513a`（feat: Step 3 T6）+ 紧后 docs 结算

Stage Summary:
- T6 完成：§8 消息 8 条 + /ws STOMP 实时通道全绿；DB 未读事实源、双向自动回复、幂等 / 限流、
  双端超集列表与扩展详情落地；单测 114/114、消息冒烟 40/40 可重复，其余基线无回归
- 下一步：T7 求职者 / 简历 / 收藏 / 上传（§2/§3/§11/§12，19 条）；T9 前仍需用户拍板
  e2e 固定手机号对真实库的重置策略与 67/70 接口账目口径

---

Task ID: 27
Agent: orchestrator (main)
Task: Step 3 T7 — 用户 / 简历 / 收藏 / 上传（§2/§3/§11/§12，19 条）

Work Log:
- 侦察：契约 §2/§3/§11/§12、`docs/api-contract.md` §9 上传限制、前端 mock 的
  profile / resumes / favorites / upload 形状、T1 的 users / user_profiles / resumes / favorites 表。
- 决策：
  - 外观偏好不新增表：Redis `pinliao:user:prefs:{userId}` 存 JSON，默认 auto；接口保留多端同步能力。
  - 重置密码验证码场景 RESET；改手机号场景 LOGIN（新号可能未注册）；均在业务冲突校验之后消耗验证码。
  - 注销为软删：`users.status=DELETED` + `deleted_at`，并吊销当前 accessToken；暂不做恢复接口。
  - 简历权限：求职者仅本人；HR 仅当 `applications.resume_id` 指向该简历且 `company_id` 为当前公司，
    越权统一 40410。首份自动默认；删除默认时把最近更新的剩余简历设为默认并同步
    `user_profiles.default_resume_id`。
  - 收藏 id `fav-{userId}-{jobId}`，`(user_id, job_id)` 唯一；重复 40910，取消幂等；
    列表 `createdAt DESC, id ASC` 游标 + 全量 total。
  - 上传用本地目录替代 OSS：服务端生成 objectKey，HMAC 预签名 PUT，确认后 `/uploads/**` 静态访问；
    未新增上传元数据表，confirm 只校验归属 / 存在 / 大小，业务关联由后续 PATCH 完成。
- 实现：
  - user/：UserController / UserService / UserRequests / UserViews / UserCodes；8 条接口。
  - resume/：ResumeController / ResumeService / ResumeRequests / ResumeViews / ResumeCodes；6 条接口。
  - favorite/：FavoriteController / FavoriteService / FavoriteMapper / FavoriteEntity / 视图；3 条接口。
  - upload/：UploadController / UploadService / UploadConfig / UploadProperties；2 条接口 + 本地 PUT。
  - WebAuthConfig 放行密码重置与本地直传；application.yml 增 `pinliao.upload.dir` / `url-ttl`。
  - 单测 37 项：用户 13 + 简历 10 + 收藏 5 + 上传 9。
  - 新增 `backend/scripts/smoke-user.mjs` 64 项；临时 `u-reg-*` 账号隔离，RESP 清 Redis 验证码冷却，
    finally 清理简历 / 收藏；`verify-seed.ps1` 的 resumes 排除 `u-reg-*` 与软删行。
- 验收（实测）：
  - `mvnw.cmd test` **151/151**（T6 基线 114 + T7 37）
  - `smoke-user.mjs` **64/64**，连续两次执行均通过；19 条接口、错误分支、收藏两页、真实 PUT 直传与静态读回
  - `smoke-auth` 20/20、`smoke-jobs` 49/49、`smoke-applications` 51/51、`smoke-company` 26/26、
    `smoke-messages` 40/40（连续两次）、`verify-seed` 16/16 无回归
  - 修复 `smoke-messages` 上一轮 WebSocket 自动回复迟到导致的计数抖动：开头 / 结尾各等待 1.8s
- 文档：PROGRESS v1.15、step3-plan v1.10、CHANGELOG、api-endpoints v0.3.5、api-contract v0.3.2、
  AI-CONTINUE v1.16（§6.16）、
  backend/README、AGENTS、README、PROJECT、worklog
- 提交：`c26dc44`（feat: Step 3 T7）+ 紧后 docs 结算

Stage Summary:
- T7 完成：§2 用户 8 条 + §3 简历 6 条 + §11 收藏 3 条 + §12 上传 2 条全部落地；偏好 / 软删注销 /
  HR 简历可见性 / 默认顺延 / 收藏游标 / 本地 HMAC 上传；单测 151/151、T7 冒烟 64/64 可重复，
  其余冒烟与种子 16/16 无回归
- 下一步：T8 招聘者 / 候选人 / 人才筛选（§4，6 条；重点 §4.1/§4.2 HR 画像 + §4.5/§4.6 关键词匹配）；
  T9 前仍需用户拍板 e2e 固定手机号对真实库的重置策略与 67/70 接口账目口径

---

Task ID: 28
Agent: orchestrator (main)
Task: 文档结算 — 接口账目 / T9 重置策略 / T8→T9 顺序（用户拍板）

Work Log:
- 决策（用户拍板，2026-10-18）：
  - 接口账目以正文 70 条为准：总览表鉴权 6→7、职位 9→10（§6.7 close/reopen）、消息 8→9
    （§8.7 send-resume / invite-interview），分模块合计 70。
  - T9 e2e 固定手机号重置策略：每轮 e2e 前清库重迁（重建 + Flyway V1~V8），
    `13900004321` 可反复使用；重置脚本在 T9 落地。
  - T8/T9 顺序：**T8 先、T9 后**。`NEXT_PUBLIC_USE_MOCK` 为全局开关、无模块级粒度，
    先补齐 §4 再整体切真实接口，避免 T9 二次返工与验收范围打折。
- 文档：PROGRESS v1.16、step3-plan v1.11、api-endpoints v0.3.6、AI-CONTINUE v1.17/v1.18、
  CHANGELOG（新增本次结算段）、AGENTS（待确认清单移除已决项）、worklog。
- 顺带修复 CHANGELOG / worklog 中 T7 文档行遗留的字面 `\n` 换行错误。
- 提交：`docs: 决策结算（接口账目 70 条 / T9 重置策略 / T8→T9 顺序）`。

Stage Summary:
- 三项待决事项全部定稿并入库，无代码变更；工作区回到干净状态（除自动维护的 AGENTS/CLAUDE 区块）。
- 下一步：T8 开工——先核对 §4.3/§4.4 现状，再补 §4.1/§4.2 HR 画像与 §4.5/§4.6 人才筛选移植。
