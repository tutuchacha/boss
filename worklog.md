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
